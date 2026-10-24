package com.lawdesk.experts;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.proceedings.ProceedingLookup;
import com.lawdesk.proceedings.ProceedingLookup.ProceedingSummary;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * Expert referrals of a proceeding and their sessions (LD-025, docs/delivery/PHASE-5-ACCEPTANCE.md): a court refers
 * a question to an expert (ندب خبير), the expert holds sessions, then submits the report, or the referral is cancelled.
 * Every write locks the referral row and adds a case timeline entry in the same transaction.
 */
@Service
class ReferralService {

    private final ReferralStore referrals;
    private final ExpertRegistry experts;
    private final CaseLookup cases;
    private final ProceedingLookup proceedings;
    private final CurrentActor currentActor;

    ReferralService(ReferralStore referrals, ExpertRegistry experts, CaseLookup cases, ProceedingLookup proceedings,
            CurrentActor currentActor) {
        this.referrals = referrals;
        this.experts = experts;
        this.cases = cases;
        this.proceedings = proceedings;
        this.currentActor = currentActor;
    }

    @Transactional
    ExpertRequests.Referral refer(UUID proceedingId, ExpertRequests.ReferralCreate request) {
        ActorRef actor = currentActor.require();
        LocalDate today = Rules.today();
        requireNotFuture("referralDate", request.referralDate(), today);
        ProceedingSummary proceeding = proceedings.find(proceedingId)
                .orElseThrow(() -> new NotFoundException("proceeding"));
        writableCase(proceeding.caseId());
        ExpertRequests.Expert expert = experts.get(request.expertId());
        if (!expert.active()) {
            throw new BusinessRuleException(Rules.EXPERT_INACTIVE, "a deactivated expert takes no new referrals");
        }
        if (request.referralDate().isBefore(proceeding.filingDate())) {
            throw new BusinessRuleException(Rules.REFERRAL_BEFORE_FILING, "a referral cannot precede the filing");
        }
        requireDueAfter(request.reportDueDate(), request.referralDate());
        ReferralStore.Row stored = referrals.insert(proceeding.caseId(), proceedingId, expert.id(),
                request.referralDate(), request.mission(), request.reportDueDate(), actor.id());
        cases.recordEvent(proceeding.caseId(), "EXPERT_REFERRED", "Proceeding %s referred to expert %s on %s"
                .formatted(numberOf(proceeding), expert.name(), stored.referralDate()));
        return respond(stored, today);
    }

    @Transactional(readOnly = true)
    ExpertRequests.Referral get(UUID id) {
        return respond(referrals.find(id).orElseThrow(() -> new NotFoundException("expert referral")), Rules.today());
    }

    @Transactional(readOnly = true)
    List<ExpertRequests.Referral> forCase(UUID caseId) {
        if (cases.find(caseId).isEmpty()) {
            throw new NotFoundException("case");
        }
        return respond(referrals.forCase(caseId), Rules.today());
    }

    @Transactional
    ExpertRequests.Referral update(UUID id, ExpertRequests.ReferralUpdate request) {
        ActorRef actor = currentActor.require();
        ReferralStore.Row row = lockedOpen(id, request.version());
        requireDueAfter(request.reportDueDate(), row.referralDate());
        return respond(referrals.change(id, request.mission(), request.reportDueDate(), row.status(),
                row.reportSubmittedDate(), actor.id()), Rules.today());
    }

    @Transactional
    ExpertRequests.Referral addSession(UUID id, ExpertRequests.SessionCreate request) {
        ActorRef actor = currentActor.require();
        ReferralStore.Row row = lockedOpen(id, null);
        if (request.sessionDate().isBefore(row.referralDate())) {
            throw new BusinessRuleException(Rules.SESSION_BEFORE_REFERRAL, "a session cannot precede the referral");
        }
        referrals.addSession(id, request.sessionDate(), request.sessionTime(), request.location(), request.notes(),
                actor.id());
        ReferralStore.Row changed = referrals.change(id, row.mission(), row.reportDueDate(), row.status(),
                row.reportSubmittedDate(), actor.id());
        cases.recordEvent(row.caseId(), "EXPERT_SESSION_RECORDED", "Expert session on %s (%s)"
                .formatted(request.sessionDate(), experts.get(row.expertId()).name()));
        return respond(changed, Rules.today());
    }

    @Transactional
    ExpertRequests.Referral submitReport(UUID id, ExpertRequests.ReportSubmission request) {
        ActorRef actor = currentActor.require();
        ReferralStore.Row row = lockedOpen(id, request.version());
        LocalDate today = Rules.today();
        requireNotFuture("reportSubmittedDate", request.reportSubmittedDate(), today);
        if (request.reportSubmittedDate().isBefore(row.referralDate())) {
            throw new BusinessRuleException(Rules.REPORT_BEFORE_REFERRAL, "a report cannot precede the referral");
        }
        ReferralStore.Row changed = referrals.change(id, row.mission(), row.reportDueDate(),
                ReferralStatus.REPORT_SUBMITTED, request.reportSubmittedDate(), actor.id());
        cases.recordEvent(row.caseId(), "EXPERT_REPORT_SUBMITTED", "Expert report of %s submitted on %s"
                .formatted(experts.get(row.expertId()).name(), request.reportSubmittedDate()));
        return respond(changed, today);
    }

    @Transactional
    ExpertRequests.Referral cancel(UUID id, ExpertRequests.Cancellation request) {
        ActorRef actor = currentActor.require();
        ReferralStore.Row row = lockedOpen(id, request.version());
        ReferralStore.Row changed = referrals.change(id, row.mission(), row.reportDueDate(), ReferralStatus.CANCELLED,
                null, actor.id());
        cases.recordEvent(row.caseId(), "EXPERT_REFERRAL_CANCELLED", "Expert referral to %s cancelled"
                .formatted(experts.get(row.expertId()).name()));
        return respond(changed, Rules.today());
    }

    /** The referral, row-locked; its version (when given) must match, its case be writable and it still open. */
    private ReferralStore.Row lockedOpen(UUID id, Long version) {
        ReferralStore.Row row = referrals.lock(id).orElseThrow(() -> new NotFoundException("expert referral"));
        if (version != null && row.version() != version) {
            throw new ConflictException("expert referral was changed by someone else");
        }
        writableCase(row.caseId());
        if (row.status() != ReferralStatus.OPEN) {
            throw new BusinessRuleException(Rules.REFERRAL_CLOSED, "the referral is no longer open");
        }
        return row;
    }

    private CaseSummary writableCase(UUID caseId) {
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(Rules.CASE_ARCHIVED, "an archived case is read-only");
        }
        return legalCase;
    }

    private static void requireNotFuture(String field, LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw new FieldErrorException(field, "PastOrPresent");
        }
    }

    private static void requireDueAfter(LocalDate due, LocalDate referralDate) {
        if (due != null && due.isBefore(referralDate)) {
            throw new BusinessRuleException(Rules.REPORT_DUE_BEFORE_REFERRAL, "the report cannot be due before the referral");
        }
    }

    private static String numberOf(ProceedingSummary proceeding) {
        return proceeding.courtCaseNumber() + "/" + proceeding.courtCaseYear();
    }

    private ExpertRequests.Referral respond(ReferralStore.Row row, LocalDate today) {
        return respond(List.of(row), today).getFirst();
    }

    /** Reads the cases, proceedings, experts and sessions of these referrals once each. */
    private List<ExpertRequests.Referral> respond(List<ReferralStore.Row> rows, LocalDate today) {
        Map<UUID, List<ExpertRequests.Session>> sessions = referrals.sessions(rows.stream().map(ReferralStore.Row::id)
                .toList()).stream().collect(Collectors.groupingBy(ReferralStore.SessionRow::referralId,
                Collectors.mapping(ReferralStore.SessionRow::session, Collectors.toList())));
        Map<UUID, CaseSummary> legalCases = new HashMap<>();
        Map<UUID, ProceedingSummary> proceedingSummaries = new HashMap<>();
        Map<UUID, ExpertRequests.Expert> expertRecords = new HashMap<>();
        return rows.stream().map(r -> {
            CaseSummary legalCase = legalCases.computeIfAbsent(r.caseId(),
                    key -> cases.find(key).orElseThrow(() -> new NotFoundException("case")));
            ProceedingSummary proceeding = proceedingSummaries.computeIfAbsent(r.proceedingId(),
                    key -> proceedings.find(key).orElseThrow(() -> new NotFoundException("proceeding")));
            ExpertRequests.Expert expert = expertRecords.computeIfAbsent(r.expertId(), experts::get);
            boolean overdue = r.status() == ReferralStatus.OPEN && r.reportDueDate() != null
                    && r.reportDueDate().isBefore(today);
            return new ExpertRequests.Referral(r.id(), r.caseId(), legalCase.referenceNo(), legalCase.title(),
                    r.proceedingId(), proceeding.stage(), proceeding.courtCaseNumber(), proceeding.courtCaseYear(),
                    expert.id(), expert.name(), expert.specialty(), r.referralDate(), r.mission(), r.reportDueDate(),
                    r.status(), r.reportSubmittedDate(), overdue, sessions.getOrDefault(r.id(), List.of()),
                    r.version(), r.createdAt(), r.updatedAt());
        }).toList();
    }
}
