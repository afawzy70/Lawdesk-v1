package com.lawdesk.proceedings;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * Special decisions, LD-023: enforcement suspensions and other decisions taken within a proceeding. Informational in
 * this phase: a suspension changes neither the judgment it names nor what else may be recorded (ASSUMPTION).
 */
@Service
class SpecialDecisionService {

    static final String SPECIAL_DECISION_RECORDED = "SPECIAL_DECISION_RECORDED";

    private final JudicialRecords records;
    private final SpecialDecisionRepository decisions;
    private final JudgmentRepository judgments;
    private final CurrentActor currentActor;

    SpecialDecisionService(JudicialRecords records, SpecialDecisionRepository decisions, JudgmentRepository judgments,
            CurrentActor currentActor) {
        this.records = records;
        this.decisions = decisions;
        this.judgments = judgments;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    Page<SpecialDecisionRequests.Response> list(UUID caseId, Pageable pageable) {
        records.requireCase(caseId);
        List<UUID> ids = records.proceedingsOf(caseId).stream().map(Proceeding::getId).toList();
        List<SpecialDecisionRequests.Response> ordered = decisions.findByProceedingIdIn(ids).stream()
                .sorted(Comparator.comparing(SpecialDecision::getDecisionDate)
                        .thenComparing(SpecialDecision::getCreatedAt))
                .map(d -> SpecialDecisionRequests.Response.of(d, caseId)).toList();
        return JudicialRecords.page(ordered, pageable);
    }

    @Transactional(readOnly = true)
    SpecialDecisionRequests.Response get(UUID id) {
        SpecialDecision decision = decision(id);
        return SpecialDecisionRequests.Response.of(decision,
                records.proceeding(decision.getProceedingId()).getCaseId());
    }

    @Transactional
    SpecialDecisionRequests.Response create(UUID proceedingId, SpecialDecisionRequests.Create request) {
        ActorRef actor = currentActor.require();
        Proceeding proceeding = records.proceeding(proceedingId);
        SpecialDecision decision = new SpecialDecision(proceedingId, actor, JudicialRecords.now());
        decide(decision, proceeding, request);
        SpecialDecision stored = decisions.saveAndFlush(decision);
        records.recordEvent(proceeding.getCaseId(), SPECIAL_DECISION_RECORDED,
                "Special decision %s (%s) of %s in proceeding %s".formatted(stored.getDecisionType(),
                        stored.getEffect(), stored.getDecisionDate(), proceeding.numberAndYear()));
        return SpecialDecisionRequests.Response.of(stored, proceeding.getCaseId());
    }

    @Transactional
    SpecialDecisionRequests.Response update(UUID id, SpecialDecisionRequests.Update request) {
        ActorRef actor = currentActor.require();
        SpecialDecision decision = decision(id);
        if (decision.getVersion() != request.version()) {
            throw new ConflictException("special decision was changed by someone else");
        }
        Proceeding proceeding = records.proceeding(decision.getProceedingId());
        decide(decision, proceeding, request.decision());
        decision.touch(actor, JudicialRecords.now());
        return SpecialDecisionRequests.Response.of(decisions.saveAndFlush(decision), proceeding.getCaseId());
    }

    private SpecialDecision decision(UUID id) {
        return decisions.findById(id).orElseThrow(() -> new NotFoundException("special decision"));
    }

    private void decide(SpecialDecision decision, Proceeding proceeding, SpecialDecisionRequests.Create request) {
        if (request.decisionType() == DecisionType.ENFORCEMENT_SUSPENSION && request.judgmentId() == null) {
            throw new BusinessRuleException(Rules.SUSPENSION_REQUIRES_JUDGMENT,
                    "an enforcement suspension names the judgment it suspends");
        }
        if (request.judgmentId() != null) {
            Judgment judgment = judgments.findById(request.judgmentId())
                    .orElseThrow(() -> new NotFoundException("judgment"));
            UUID judgmentCase = records.proceeding(judgment.getProceedingId()).getCaseId();
            if (!judgmentCase.equals(proceeding.getCaseId())) {
                throw new BusinessRuleException(Rules.DECISION_JUDGMENT_OTHER_CASE,
                        "the judgment belongs to another case");
            }
        }
        if (request.decisionDate().isBefore(proceeding.getFilingDate())) {
            throw new BusinessRuleException(Rules.DECISION_BEFORE_FILING, "the decision precedes the filing");
        }
        String summary = request.summary() == null || request.summary().isBlank() ? null : request.summary().trim();
        decision.decide(request.decisionType(), request.decisionDate(), request.effect(), summary,
                request.judgmentId());
    }
}
