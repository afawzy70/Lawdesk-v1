package com.lawdesk.proceedings;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.courts.Court;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * Proceedings of a case: first instance (LD-019), appeal (LD-020) and cassation (LD-021), with the ladder rules of
 * docs/delivery/PHASE-4-ACCEPTANCE.md. Appeal and cassation deadlines are recorded, not enforced (ASSUMPTION).
 */
@Service
class ProceedingService {

    static final String PROCEEDING_RECORDED = "PROCEEDING_RECORDED";

    /** Stands for "no proceeding yet" in the duplicate check, so the query never binds a null id. */
    private static final UUID NONE = new UUID(0, 0);

    private final JudicialRecords records;
    private final ProceedingRepository proceedings;
    private final JudgmentRepository judgments;
    private final SpecialDecisionRepository decisions;
    private final CurrentActor currentActor;

    ProceedingService(JudicialRecords records, ProceedingRepository proceedings, JudgmentRepository judgments,
            SpecialDecisionRepository decisions, CurrentActor currentActor) {
        this.records = records;
        this.proceedings = proceedings;
        this.judgments = judgments;
        this.decisions = decisions;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    Page<ProceedingRequests.Response> list(UUID caseId, Pageable pageable) {
        records.requireCase(caseId);
        var ordered = records.proceedingsOf(caseId);
        Set<UUID> judged = judgments.findByProceedingIdIn(ordered.stream().map(Proceeding::getId).toList()).stream()
                .map(Judgment::getProceedingId).collect(Collectors.toSet());
        Map<UUID, Court> courts = new HashMap<>();
        return JudicialRecords.page(ordered, pageable).map(p -> ProceedingRequests.Response.of(p,
                courts.computeIfAbsent(p.getCourtId(), records::court), judged.contains(p.getId())));
    }

    @Transactional(readOnly = true)
    ProceedingRequests.Response get(UUID id) {
        return respond(records.proceeding(id));
    }

    @Transactional
    ProceedingRequests.Response create(UUID caseId, ProceedingRequests.Create request) {
        ActorRef actor = currentActor.require();
        records.requireCase(caseId);
        Court court = records.court(request.courtId());
        if (!court.isActive()) {
            throw new BusinessRuleException(Rules.COURT_INACTIVE, "court " + court.getCode() + " is deactivated");
        }
        requireLevel(request.stage(), court);
        Optional<Judgment> parentJudgment = checkParent(caseId, request.stage(), request.parentProceedingId());
        requireFiledAfter(parentJudgment, request.filingDate());

        Proceeding proceeding = new Proceeding(caseId, request.stage(), request.parentProceedingId(), actor,
                JudicialRecords.now());
        place(proceeding, request.courtId(), request.courtCaseNumber(), request.courtCaseYear(), request.filingDate());
        Proceeding stored = store(proceeding);
        records.recordEvent(caseId, PROCEEDING_RECORDED, "%s proceeding %s at court %s".formatted(
                label(stored.getStage()), stored.numberAndYear(), court.getCode()));
        return ProceedingRequests.Response.of(stored, court, false);
    }

    @Transactional
    ProceedingRequests.Response update(UUID id, ProceedingRequests.Update request) {
        ActorRef actor = currentActor.require();
        // Child before parent: this proceeding, then the one it attacks (QAD-024).
        Proceeding proceeding = records.lockedProceeding(id);
        if (proceeding.getVersion() != request.version()) {
            throw new ConflictException("proceeding was changed by someone else");
        }
        Court court = records.court(request.courtId());
        // A court deactivated after recording may stay on the proceeding; moving to a deactivated court is refused.
        if (!court.isActive() && !court.getId().equals(proceeding.getCourtId())) {
            throw new BusinessRuleException(Rules.COURT_INACTIVE, "court " + court.getCode() + " is deactivated");
        }
        requireLevel(proceeding.getStage(), court);
        Optional<Judgment> own = judgments.findByProceedingId(id);
        if (own.isPresent() && own.get().getJudgmentDate().isBefore(request.filingDate())) {
            throw new BusinessRuleException(Rules.JUDGMENT_BEFORE_FILING, "the judgment would precede the filing");
        }
        if (decisions.earliestDecisionDate(id).filter(d -> d.isBefore(request.filingDate())).isPresent()) {
            throw new BusinessRuleException(Rules.DECISION_BEFORE_FILING, "a special decision would precede the filing");
        }
        if (proceeding.getParentProceedingId() != null) {
            records.lockedProceeding(proceeding.getParentProceedingId());
            requireFiledAfter(judgments.findByProceedingId(proceeding.getParentProceedingId()), request.filingDate());
        }
        place(proceeding, request.courtId(), request.courtCaseNumber(), request.courtCaseYear(), request.filingDate());
        proceeding.touch(actor, JudicialRecords.now());
        return ProceedingRequests.Response.of(store(proceeding), court, own.isPresent());
    }

    private ProceedingRequests.Response respond(Proceeding p) {
        return ProceedingRequests.Response.of(p, records.court(p.getCourtId()),
                judgments.existsByProceedingId(p.getId()));
    }

    private static void requireLevel(Stage stage, Court court) {
        if (court.getLevel() != stage.courtLevel()) {
            throw new BusinessRuleException(Rules.COURT_LEVEL_MISMATCH,
                    "a " + stage + " proceeding cannot be heard by a " + court.getLevel() + " court");
        }
    }

    /**
     * The ladder: first instance has no parent; an appeal attacks a judged first-instance proceeding and a cassation a
     * judged appeal, both of the same case. Returns the parent's judgment, if there is a parent.
     */
    private Optional<Judgment> checkParent(UUID caseId, Stage stage, UUID parentId) {
        if (stage == Stage.FIRST_INSTANCE) {
            if (parentId != null) {
                throw new BusinessRuleException(Rules.FIRST_INSTANCE_HAS_NO_PARENT, "first instance has no parent");
            }
            return Optional.empty();
        }
        if (parentId == null) {
            throw new BusinessRuleException(Rules.PARENT_PROCEEDING_REQUIRED, stage + " needs a parent proceeding");
        }
        // Locked so the parent's judgment cannot move while this proceeding is checked against it (QAD-024).
        Proceeding parent = records.lockedProceeding(parentId);
        if (!parent.getCaseId().equals(caseId)) {
            throw new BusinessRuleException(Rules.PARENT_PROCEEDING_OTHER_CASE, "parent belongs to another case");
        }
        Stage expected = stage == Stage.APPEAL ? Stage.FIRST_INSTANCE : Stage.APPEAL;
        if (parent.getStage() != expected) {
            throw new BusinessRuleException(stage == Stage.APPEAL ? Rules.APPEAL_PARENT_NOT_FIRST_INSTANCE
                    : Rules.CASSATION_PARENT_NOT_APPEAL, stage + " must follow " + expected);
        }
        Optional<Judgment> judgment = judgments.findByProceedingId(parentId);
        if (judgment.isEmpty()) {
            throw new BusinessRuleException(stage == Stage.APPEAL ? Rules.APPEAL_REQUIRES_FIRST_INSTANCE_JUDGMENT
                    : Rules.CASSATION_REQUIRES_APPEAL_JUDGMENT, "the parent proceeding has no judgment yet");
        }
        return judgment;
    }

    private static void requireFiledAfter(Optional<Judgment> parentJudgment, LocalDate filingDate) {
        if (parentJudgment.isPresent() && filingDate.isBefore(parentJudgment.get().getJudgmentDate())) {
            throw new BusinessRuleException(Rules.FILED_BEFORE_PARENT_JUDGMENT,
                    "filed before the judgment it attacks");
        }
    }

    private void place(Proceeding proceeding, UUID courtId, String number, int year, LocalDate filingDate) {
        String trimmed = number.trim();
        UUID self = proceeding.getId() == null ? NONE : proceeding.getId();
        if (proceedings.numberTaken(courtId, year, trimmed, proceeding.getStage(), self)) {
            throw new ConflictException("number and year already recorded at this court");
        }
        proceeding.place(courtId, trimmed, year, filingDate);
    }

    /** Flushes now, so a concurrent duplicate becomes a 409 instead of a failure at commit. */
    private Proceeding store(Proceeding proceeding) {
        try {
            return proceedings.saveAndFlush(proceeding);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("number and year already recorded at this court");
        }
    }

    private static String label(Stage stage) {
        return switch (stage) {
            case FIRST_INSTANCE -> "First-instance";
            case APPEAL -> "Appeal";
            case CASSATION -> "Cassation";
        };
    }
}
