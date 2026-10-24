package com.lawdesk.cases;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.api.PageResponse;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/** Case use cases (docs/delivery/PHASE-3-ACCEPTANCE.md) and the {@link CaseLookup} port for other modules. */
@Service
class CaseService implements CaseLookup {

    static final String REFERENCE_NO_RESERVED = "REFERENCE_NO_RESERVED";
    static final String CASE_READ_ONLY = "CASE_READ_ONLY";
    static final String DATE_RANGE_INVALID = "DATE_RANGE_INVALID";
    static final String STATUS_TRANSITION_NOT_ALLOWED = "STATUS_TRANSITION_NOT_ALLOWED";
    static final String REASON_REQUIRED = "REASON_REQUIRED";

    /** Supplied numbers may not look generated, so a generated number can never collide with one. */
    private static final Pattern RESERVED = Pattern.compile("(?i)LD-[0-9].*");

    private final CaseRepository cases;
    private final CaseTimeline timeline;
    private final CurrentActor currentActor;

    CaseService(CaseRepository cases, CaseTimeline timeline, CurrentActor currentActor) {
        this.cases = cases;
        this.timeline = timeline;
        this.currentActor = currentActor;
    }

    @Transactional
    LegalCase create(CreateCaseRequest request) {
        ActorRef actor = currentActor.require();
        String referenceNo = request.referenceNo() == null ? generateReferenceNo() : checkSupplied(request.referenceNo());
        LegalCase created;
        try {
            created = cases.insert(referenceNo, request.title(), CaseType.valueOf(request.caseType()),
                    request.subject(), request.openedOn(), actor.id());
        } catch (DuplicateKeyException e) {
            // A concurrent request took the number between the check and the insert.
            throw new ConflictException("reference number " + referenceNo + " is already used");
        }
        timeline.append(created.id(), CaseTimeline.CASE_CREATED, "Case " + referenceNo + " created", actor.id());
        return created;
    }

    @Transactional(readOnly = true)
    PageResponse<CaseListItem> search(CaseSearch search) {
        if (search.openedFrom() != null && search.openedTo() != null && search.openedFrom().isAfter(search.openedTo())) {
            throw new BusinessRuleException(DATE_RANGE_INVALID, "openedFrom is after openedTo");
        }
        return cases.search(search);
    }

    /**
     * LD-013: applies the edit when {@code version} is current. A request that changes nothing keeps the version and
     * records no event.
     */
    @Transactional
    LegalCase update(UUID id, UpdateCaseRequest request) {
        ActorRef actor = currentActor.require();
        LegalCase current = get(id);
        if (current.status().detailsReadOnly()) {
            throw new BusinessRuleException(CASE_READ_ONLY, "case " + id + " is " + current.status());
        }
        if (current.version() != request.version()) {
            throw new ConflictException("case " + id + " has changed since version " + request.version());
        }
        CaseType caseType = CaseType.valueOf(request.caseType());
        List<String> changed = new ArrayList<>();
        if (!current.title().equals(request.title())) changed.add("title");
        if (current.caseType() != caseType) changed.add("caseType");
        if (!Objects.equals(current.subject(), request.subject())) changed.add("subject");
        if (!current.openedOn().equals(request.openedOn())) changed.add("openedOn");
        if (changed.isEmpty()) {
            return current;
        }
        LegalCase updated = cases.update(id, request.version(), request.title(), caseType, request.subject(),
                        request.openedOn(), actor.id())
                // Another request changed the case between the read above and this update.
                .orElseThrow(() -> new ConflictException("case " + id + " has changed concurrently"));
        timeline.append(id, CaseTimeline.CASE_UPDATED, "Changed: " + String.join(", ", changed), actor.id());
        return updated;
    }

    /** LD-017: applies an allowed transition at the current version and records it in the timeline. */
    @Transactional
    LegalCase changeStatus(UUID id, StatusChangeRequest request) {
        ActorRef actor = currentActor.require();
        LegalCase current = get(id);
        CaseStatus target = CaseStatus.valueOf(request.status());
        if (current.version() != request.version()) {
            throw new ConflictException("case " + id + " has changed since version " + request.version());
        }
        if (!current.status().canChangeTo(target)) {
            throw new BusinessRuleException(STATUS_TRANSITION_NOT_ALLOWED, current.status() + " -> " + target);
        }
        if (current.status() == CaseStatus.CLOSED && target == CaseStatus.ACTIVE && request.reason() == null) {
            throw new BusinessRuleException(REASON_REQUIRED, "reopening a closed case needs a reason");
        }
        LegalCase changed = cases.changeStatus(id, request.version(), current.status(), target, actor.id())
                .orElseThrow(() -> new ConflictException("case " + id + " has changed concurrently"));
        String summary = current.status() + " -> " + target + (request.reason() == null ? "" : ": " + request.reason());
        timeline.append(id, CaseTimeline.CASE_STATUS_CHANGED, CaseTimeline.fit(summary), actor.id());
        return changed;
    }

    @Transactional(readOnly = true)
    PageResponse<CaseEvent> timeline(UUID id, Paging paging) {
        if (!cases.exists(id)) {
            throw new NotFoundException("case");
        }
        return timeline.page(id, paging);
    }

    @Transactional(readOnly = true)
    LegalCase get(UUID id) {
        return cases.find(id).orElseThrow(() -> new NotFoundException("case"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaseSummary> find(UUID id) {
        return cases.find(id).map(LegalCase::summary);
    }

    @Override
    @Transactional
    public void recordEvent(UUID caseId, String type, String summary) {
        String checked = CaseTimeline.checkedSummary(type, summary);
        ActorRef actor = currentActor.require();
        if (caseId == null || !cases.exists(caseId)) {
            throw new NotFoundException("case");
        }
        timeline.append(caseId, type, checked, actor.id());
    }

    private String checkSupplied(String referenceNo) {
        if (RESERVED.matcher(referenceNo).matches()) {
            throw new BusinessRuleException(REFERENCE_NO_RESERVED, "supplied reference numbers may not start LD-<digit>");
        }
        if (cases.referenceNoTaken(referenceNo)) {
            throw new ConflictException("reference number " + referenceNo + " is already used");
        }
        return referenceNo;
    }

    private String generateReferenceNo() {
        int year = Year.now(ZoneOffset.UTC).getValue();
        return "LD-%d-%05d".formatted(year, cases.nextReferenceNumber(year));
    }
}
