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
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.api.PageResponse;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;
import com.lawdesk.users.UserDirectory;

/** Case use cases (docs/delivery/PHASE-3-ACCEPTANCE.md) and the {@link CaseLookup} port for other modules. */
@Service
class CaseService implements CaseLookup {

    static final String REFERENCE_NO_RESERVED = "REFERENCE_NO_RESERVED";
    static final String CASE_READ_ONLY = "CASE_READ_ONLY";
    static final String DATE_RANGE_INVALID = "DATE_RANGE_INVALID";
    static final String STATUS_TRANSITION_NOT_ALLOWED = "STATUS_TRANSITION_NOT_ALLOWED";
    static final String REASON_REQUIRED = "REASON_REQUIRED";
    static final String MEMBER_NOT_ELIGIBLE = "MEMBER_NOT_ELIGIBLE";

    /** Supplied numbers may not look generated, so a generated number can never collide with one. */
    private static final Pattern RESERVED = Pattern.compile("(?i)LD-[0-9].*");

    private final CaseRepository cases;
    private final CaseTimeline timeline;
    private final CurrentActor currentActor;
    private final CaseAccessPolicy access;
    private final AuditLog audit;
    private final UserDirectory users;

    CaseService(CaseRepository cases, CaseTimeline timeline, CurrentActor currentActor, CaseAccessPolicy access,
            AuditLog audit, UserDirectory users) {
        this.cases = cases;
        this.timeline = timeline;
        this.currentActor = currentActor;
        this.access = access;
        this.audit = audit;
        this.users = users;
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
        checkDetails(request.details());
        cases.setDetails(created.id(), request.details());
        timeline.append(created.id(), CaseTimeline.CASE_CREATED, "Case " + referenceNo + " created", actor.id());
        return cases.find(created.id()).orElseThrow();
    }

    @Transactional(readOnly = true)
    PageResponse<CaseListItem> search(CaseSearch search) {
        if (search.openedFrom() != null && search.openedTo() != null && search.openedFrom().isAfter(search.openedTo())) {
            throw new BusinessRuleException(DATE_RANGE_INVALID, "openedFrom is after openedTo");
        }
        return cases.search(search, access.viewable("c"));
    }

    /**
     * LD-013: applies the edit when {@code version} is current. A request that changes nothing keeps the version and
     * records no event.
     */
    @Transactional
    LegalCase update(UUID id, UpdateCaseRequest request) {
        ActorRef actor = currentActor.require();
        LegalCase current = get(id);
        access.requireChange(id);
        if (current.status().detailsReadOnly()) {
            throw new BusinessRuleException(CASE_READ_ONLY, "case " + id + " is " + current.status());
        }
        if (current.version() != request.version()) {
            throw new ConflictException("case " + id + " has changed since version " + request.version());
        }
        FinancialImpact impact = financialImpactOf(request);
        CaseType caseType = CaseType.valueOf(request.caseType());
        List<String> changed = new ArrayList<>();
        if (!current.title().equals(request.title())) changed.add("title");
        if (current.caseType() != caseType) changed.add("caseType");
        if (!Objects.equals(current.subject(), request.subject())) changed.add("subject");
        if (!current.openedOn().equals(request.openedOn())) changed.add("openedOn");
        boolean automatedNoChanged = request.automatedNo() != null && !request.automatedNo().equals(current.automatedNo());
        if (automatedNoChanged) changed.add("automatedNo");
        boolean detailsChanged = request.details() != null && !request.details().equals(current.details());
        if (detailsChanged) {
            checkDetails(request.details());
            changed.add("details");
        }
        boolean claimedChanged = detailsChanged && !sameAmount(current.details().claimedAmount(),
                request.details().claimedAmount());
        boolean impactChanged = impact != null && (impact != current.financialImpact()
                || current.financialAmount() == null && request.financialAmount() != null
                || current.financialAmount() != null && request.financialAmount() != null
                        && current.financialAmount().compareTo(request.financialAmount()) != 0);
        if (impactChanged) changed.add("financialImpact");
        if (changed.isEmpty()) {
            return current;
        }
        LegalCase updated = cases.update(id, request.version(), request.title(), caseType, request.subject(),
                        request.openedOn(), new CaseRepository.DetailsChange(request.automatedNo(), impact,
                                request.financialAmount(), request.details()), actor.id())
                // Another request changed the case between the read above and this update.
                .orElseThrow(() -> new ConflictException("case " + id + " has changed concurrently"));
        timeline.append(id, CaseTimeline.CASE_UPDATED, "Changed: " + String.join(", ", changed), actor.id());
        if (automatedNoChanged) {
            audit.recordWithChange("CASE_AUTOMATED_NO_CHANGE", actor.id(), actor.username(), "CASE", id.toString(),
                    "changed");
        }
        if (impactChanged) {
            audit.recordWithChange("CASE_FINANCIAL_IMPACT_CHANGE", actor.id(), actor.username(), "CASE", id.toString(),
                    "impact=" + impact);
        }
        if (claimedChanged) {
            audit.recordWithChange("CASE_CLAIMED_AMOUNT_CHANGE", actor.id(), actor.username(), "CASE", id.toString(),
                    "changed");
        }
        return updated;
    }

    private static boolean sameAmount(java.math.BigDecimal a, java.math.BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** The referring unit, when given, must be an active unit of the authority's tree. */
    void checkDetails(CaseDetails details) {
        if (details != null && details.referringUnitId() != null && !cases.isActiveAuthorityUnit(details.referringUnitId())) {
            throw new FieldErrorException("details.referringUnitId", "Exists");
        }
    }

    /** The impact an edit asks for, or null when it leaves the impact alone; an amount always goes with "YES". */
    private static FinancialImpact financialImpactOf(UpdateCaseRequest request) {
        if (request.financialImpact() == null) {
            if (request.financialAmount() != null) {
                throw new FieldErrorException("financialImpact", "NotNull");
            }
            return null;
        }
        FinancialImpact impact = FinancialImpact.valueOf(request.financialImpact());
        if (impact == FinancialImpact.YES && request.financialAmount() == null) {
            throw new FieldErrorException("financialAmount", "NotNull");
        }
        if (impact == FinancialImpact.NO && request.financialAmount() != null) {
            throw new FieldErrorException("financialAmount", "Null");
        }
        return impact;
    }

    /** LD-045: replaces the members of a case. Only a case supervisor gets here (the controller checks it). */
    @Transactional
    LegalCase assign(UUID id, AssignmentRequest request) {
        ActorRef actor = currentActor.require();
        LegalCase current = get(id);
        access.requireDistribute(id, Permission.ASSIGN_CASE);
        if (current.version() != request.version()) {
            throw new ConflictException("case " + id + " has changed since version " + request.version());
        }
        if (request.primaryMemberId().equals(request.alternateMemberId())) {
            throw new FieldErrorException("alternateMemberId", "SameAsPrimary");
        }
        requireEligible(request.primaryMemberId(), "primaryMemberId");
        if (request.alternateMemberId() != null) {
            requireEligible(request.alternateMemberId(), "alternateMemberId");
        }
        MemberRef primary = current.primaryMember();
        MemberRef alternate = current.alternateMember();
        if (primary != null && primary.id().equals(request.primaryMemberId())
                && Objects.equals(alternate == null ? null : alternate.id(), request.alternateMemberId())) {
            return current;
        }
        LegalCase updated = cases.assign(id, request.version(), request.primaryMemberId(), request.alternateMemberId(),
                        actor.id())
                .orElseThrow(() -> new ConflictException("case " + id + " has changed concurrently"));
        recordAssignment(updated, actor);
        return updated;
    }

    /** The timeline entry and the audit event of a new assignment (same transaction as the change). */
    void recordAssignment(LegalCase updated, ActorRef actor) {
        timeline.append(updated.id(), CaseTimeline.CASE_ASSIGNED, CaseTimeline.fit("Assigned: "
                + updated.primaryMember().displayName() + (updated.alternateMember() == null ? ""
                        : ", alternate " + updated.alternateMember().displayName())), actor.id());
        // The audit reason holds 64 characters: one event per role, each with the member's id.
        audit.recordWithChange("CASE_ASSIGNMENT_CHANGE", actor.id(), actor.username(), "CASE",
                updated.id().toString(), "primary=" + updated.primaryMember().id());
        audit.recordWithChange("CASE_ASSIGNMENT_CHANGE", actor.id(), actor.username(), "CASE",
                updated.id().toString(), "alternate=" + (updated.alternateMember() == null ? "none"
                        : updated.alternateMember().id()));
    }

    private void requireEligible(UUID member, String field) {
        if (!users.isLegalDepartmentMember(member)) {
            throw new BusinessRuleException(MEMBER_NOT_ELIGIBLE, field + " is not an active legal department member");
        }
    }

    /** LD-017: applies an allowed transition at the current version and records it in the timeline. */
    @Transactional
    LegalCase changeStatus(UUID id, StatusChangeRequest request) {
        ActorRef actor = currentActor.require();
        LegalCase current = get(id);
        access.requireDistribute(id, Permission.CASE_STATUS_CHANGE);
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
        access.requireView(id);
        return timeline.page(id, paging);
    }

    @Transactional(readOnly = true)
    LegalCase get(UUID id) {
        access.requireView(id);
        return cases.find(id).orElseThrow(() -> new NotFoundException("case"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaseSummary> find(UUID id) {
        return access.canView(id) ? cases.find(id).map(LegalCase::summary) : Optional.empty();
    }

    @Override
    @Transactional
    public void recordEvent(UUID caseId, String type, String summary) {
        String checked = CaseTimeline.checkedSummary(type, summary);
        ActorRef actor = currentActor.require();
        if (caseId == null || !cases.exists(caseId)) {
            throw new NotFoundException("case");
        }
        // Every module writes to a case through this call, so it is where "only its members change a case" holds.
        access.requireChange(caseId);
        timeline.append(caseId, type, checked, actor.id());
    }

    @Override
    @Transactional
    public void recordTaskEvent(UUID caseId, String type, String summary) {
        String checked = CaseTimeline.checkedSummary(type, summary);
        ActorRef actor = currentActor.require();
        access.requireView(caseId);
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

    String generateReferenceNo() {
        int year = Year.now(ZoneOffset.UTC).getValue();
        return "LD-%d-%05d".formatted(year, cases.nextReferenceNumber(year));
    }
}
