package com.lawdesk.cases;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/** Case use cases (docs/delivery/PHASE-3-ACCEPTANCE.md) and the {@link CaseLookup} port for other modules. */
@Service
class CaseService implements CaseLookup {

    static final String REFERENCE_NO_RESERVED = "REFERENCE_NO_RESERVED";

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
