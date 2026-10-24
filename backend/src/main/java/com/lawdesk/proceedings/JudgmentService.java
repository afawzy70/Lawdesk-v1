package com.lawdesk.proceedings;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/** Judgments and outcomes, LD-022: one judgment ends a proceeding (ASSUMPTION in the acceptance file). */
@Service
class JudgmentService {

    static final String JUDGMENT_RECORDED = "JUDGMENT_RECORDED";

    private final JudicialRecords records;
    private final JudgmentRepository judgments;
    private final CurrentActor currentActor;

    JudgmentService(JudicialRecords records, JudgmentRepository judgments, CurrentActor currentActor) {
        this.records = records;
        this.judgments = judgments;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    Page<JudgmentRequests.Response> list(UUID caseId, Pageable pageable) {
        records.requireCase(caseId);
        List<UUID> ids = records.proceedingsOf(caseId).stream().map(Proceeding::getId).toList();
        List<JudgmentRequests.Response> ordered = judgments.findByProceedingIdIn(ids).stream()
                .sorted(Comparator.comparing(Judgment::getJudgmentDate).thenComparing(Judgment::getCreatedAt))
                .map(j -> JudgmentRequests.Response.of(j, caseId)).toList();
        return JudicialRecords.page(ordered, pageable);
    }

    @Transactional(readOnly = true)
    JudgmentRequests.Response get(UUID id) {
        Judgment judgment = judgment(id);
        return JudgmentRequests.Response.of(judgment, records.proceeding(judgment.getProceedingId()).getCaseId());
    }

    @Transactional
    JudgmentRequests.Response create(UUID proceedingId, JudgmentRequests.Create request) {
        ActorRef actor = currentActor.require();
        Proceeding proceeding = records.proceeding(proceedingId);
        if (judgments.existsByProceedingId(proceedingId)) {
            throw new ConflictException("the proceeding already has a judgment");
        }
        Judgment judgment = new Judgment(proceedingId, actor, JudicialRecords.now());
        decide(judgment, proceeding, request);
        Judgment stored = store(judgment);
        records.recordEvent(proceeding.getCaseId(), JUDGMENT_RECORDED, "Judgment of %s (%s) in proceeding %s"
                .formatted(stored.getJudgmentDate(), stored.getOutcome(), proceeding.numberAndYear()));
        return JudgmentRequests.Response.of(stored, proceeding.getCaseId());
    }

    @Transactional
    JudgmentRequests.Response update(UUID id, JudgmentRequests.Update request) {
        ActorRef actor = currentActor.require();
        Judgment judgment = judgment(id);
        if (judgment.getVersion() != request.version()) {
            throw new ConflictException("judgment was changed by someone else");
        }
        Proceeding proceeding = records.proceeding(judgment.getProceedingId());
        decide(judgment, proceeding, request.decision());
        judgment.touch(actor, JudicialRecords.now());
        return JudgmentRequests.Response.of(store(judgment), proceeding.getCaseId());
    }

    private Judgment judgment(UUID id) {
        return judgments.findById(id).orElseThrow(() -> new NotFoundException("judgment"));
    }

    private static void decide(Judgment judgment, Proceeding proceeding, JudgmentRequests.Create request) {
        if (request.judgmentDate().isBefore(proceeding.getFilingDate())) {
            throw new BusinessRuleException(Rules.JUDGMENT_BEFORE_FILING, "the judgment precedes the filing");
        }
        String summary = request.summary() == null || request.summary().isBlank() ? null : request.summary().trim();
        judgment.decide(request.judgmentDate(), request.outcome(), summary, Boolean.TRUE.equals(request.finalJudgment()));
    }

    /** Flushes now, so a concurrent second judgment becomes a 409 instead of a failure at commit. */
    private Judgment store(Judgment judgment) {
        try {
            return judgments.saveAndFlush(judgment);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("the proceeding already has a judgment");
        }
    }
}
