package com.lawdesk.casefile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only door other modules use to reach the executive summary and the unified timeline of a case (LD-059: the legal
 * assistant). It flattens {@link CaseFileService} - which already requires that the user may see the case (404 otherwise) and
 * leaves out every area the user holds no permission for - into labelled facts. Nothing is generated: each field is a stored
 * value, named by a stable code.
 */
@Component
public class CaseFileReadPort {

    /** One stored value of a fact, named by a stable code the screens translate. */
    public record Field(String code, String value) {
    }

    /** A stored record or group of records of the case: its kind, the record it comes from (may be null) and its fields. */
    public record Fact(String type, UUID id, String title, LocalDate date, List<Field> fields) {
    }

    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    private final CaseFileService service;

    CaseFileReadPort(CaseFileService service) {
        this.service = service;
    }

    /** The facts of the case summary, in a fixed order. */
    @Transactional(readOnly = true)
    public List<Fact> facts(UUID caseId) {
        CaseFileViews.Summary s = service.summary(caseId);
        List<Fact> facts = new ArrayList<>();
        List<Field> head = new ArrayList<>();
        add(head, "referenceNo", s.referenceNo());
        add(head, "automatedNo", s.automatedNo());
        add(head, "title", s.title());
        add(head, "subject", s.subject());
        add(head, "caseType", s.caseType());
        add(head, "status", s.status());
        add(head, "authorityPosition", s.authorityPosition());
        if (s.claimedAmount() != null) {
            add(head, "claimedAmount", s.claimedAmount().toPlainString());
        }
        if (s.primaryMember() != null) {
            add(head, "primaryMember", s.primaryMember().displayName());
        }
        if (s.parties() != null) {
            s.parties().forEach(p -> add(head, "party", p.name() + " (" + p.role() + ")"));
        }
        if (s.involvedUnits() != null) {
            s.involvedUnits().forEach(u -> add(head, "unit", u.name()));
        }
        facts.add(new Fact("CASE", caseId, s.referenceNo(), null, head));
        if (s.lastProcedure() != null) {
            List<Field> fields = new ArrayList<>();
            add(fields, "procedureType", s.lastProcedure().type());
            add(fields, "summary", s.lastProcedure().summary());
            facts.add(new Fact("PROCEDURE", null, s.lastProcedure().type(), s.lastProcedure().occurredAt() == null ? null
                    : s.lastProcedure().occurredAt().atZone(ZONE).toLocalDate(), fields));
        }
        if (s.hearings() != null) {
            if (s.hearings().lastHearing() != null) {
                facts.add(hearing("lastHearing", s.hearings().lastHearing()));
            }
            if (s.hearings().nextHearing() != null) {
                facts.add(hearing("nextHearing", s.hearings().nextHearing()));
            }
        }
        if (s.lastJudgment() != null) {
            CaseFileViews.JudgmentBrief j = s.lastJudgment();
            List<Field> fields = new ArrayList<>();
            add(fields, "outcome", j.outcome());
            add(fields, "summary", j.summary());
            add(fields, "finalJudgment", Boolean.toString(j.finalJudgment()));
            facts.add(new Fact("JUDGMENT", j.id(), j.outcome(), j.judgmentDate(), fields));
        }
        if (s.deadlines() != null) {
            for (CaseFileViews.DeadlineBrief d : s.deadlines().items()) {
                List<Field> fields = new ArrayList<>();
                add(fields, "action", d.action());
                add(fields, "calcStatus", d.calcStatus());
                add(fields, "overdue", Boolean.toString(d.overdue()));
                facts.add(new Fact("DEADLINE", d.id(), d.action(), d.dueOn(), fields));
            }
        }
        if (s.tasks() != null) {
            for (CaseFileViews.TaskBrief t : s.tasks().items()) {
                List<Field> fields = new ArrayList<>();
                add(fields, "title", t.title());
                add(fields, "status", t.status());
                add(fields, "overdue", Boolean.toString(t.overdue()));
                facts.add(new Fact("TASK", t.id(), t.title(), t.dueDate(), fields));
            }
        }
        return facts;
    }

    /** The latest {@code limit} entries of the unified timeline, oldest first, as facts. */
    @Transactional(readOnly = true)
    public List<Fact> chronology(UUID caseId, int limit) {
        CaseFileViews.Timeline timeline = service.timeline(caseId, List.of(), false, PageRequest.of(0, limit));
        List<Fact> facts = new ArrayList<>();
        for (CaseFileViews.Entry e : timeline.items()) {
            List<Field> fields = new ArrayList<>();
            add(fields, "kind", e.kind());
            add(fields, "status", e.status());
            add(fields, "summary", e.summary());
            add(fields, "future", Boolean.toString(e.isFuture()));
            facts.add(new Fact("TIMELINE", e.refId(), e.kind(), e.at() == null ? null : e.at().atZone(ZONE).toLocalDate(), fields));
        }
        java.util.Collections.reverse(facts);
        return facts;
    }

    private static Fact hearing(String code, CaseFileViews.HearingBrief h) {
        List<Field> fields = new ArrayList<>();
        add(fields, "role", code);
        add(fields, "status", h.status());
        add(fields, "purpose", h.purpose());
        add(fields, "kind", h.kind());
        add(fields, "time", h.hearingTime() == null ? null : h.hearingTime().toString());
        add(fields, "decision", h.decision());
        add(fields, "adjournmentReason", h.adjournmentReason());
        return new Fact("HEARING", h.id(), h.purpose(), h.hearingDate(), fields);
    }

    private static void add(List<Field> fields, String code, String value) {
        if (value != null && !value.isBlank()) {
            fields.add(new Field(code, value));
        }
    }
}
