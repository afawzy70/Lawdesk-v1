package com.lawdesk.casebrief;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.casebrief.CaseBriefViews.Brief;
import com.lawdesk.casebrief.CaseBriefViews.Entry;
import com.lawdesk.casebrief.CaseBriefViews.Section;
import com.lawdesk.casebrief.CaseBriefViews.Source;
import com.lawdesk.library.LibraryReadPort;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

import static com.lawdesk.casebrief.CaseBriefViews.NOT_PERMITTED;
import static com.lawdesk.casebrief.CaseBriefViews.NOT_RECORDED;
import static com.lawdesk.casebrief.CaseBriefViews.RECORDED;

/**
 * The case brief (requirements section 22, the rows that are queries over the system's own records). It uses no model and no
 * provider and makes no network call: it reads the case's proceedings, hearings, judgments and their rulings on requests and
 * defences, deadlines, evidence and documents, and sorts and filters them by fixed rules. It never computes a legal period, never
 * infers a ruling, and never invents a required document: where a record does not say, the answer is {@code NOT_RECORDED}.
 *
 * <p>Access: the case access policy runs first, before anything about the request is looked at (404 for a case the user may not
 * see, a RESTRICTED case included unless a grant or membership applies). Each part of the brief then needs the same permission as
 * the screen that owns the records; without it the part is NOT_PERMITTED, not empty.
 */
@Service
class CaseBriefService {

    static final String NOTICE = "Derived from the case records by fixed rules - not model-generated. Where the records do not say,"
            + " the brief says NOT_RECORDED. It is not legal advice and does not replace reading the records.";

    private static final int MAX_SIMILAR = 25;
    private static final List<String> ORDER = List.of("PROCEEDING", "CLAIM_SUBMITTED", "DEFENSE_RAISED", "HEARING",
            "DEADLINE_COMPLETED", "SPECIAL_DECISION", "JUDGMENT");

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CurrentActor actor;
    private final LibraryReadPort library;
    private final Clock clock;

    CaseBriefService(JdbcClient jdbc, CaseAccessPolicy access, CurrentActor actor, LibraryReadPort library, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.actor = actor;
        this.library = library;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    Brief brief(UUID caseId, int similarLimit) {
        access.requireView(caseId); // before any validation of the request
        if (similarLimit < 1) {
            throw new FieldErrorException("similarLimit", "Min");
        }
        if (similarLimit > MAX_SIMILAR) {
            throw new FieldErrorException("similarLimit", "Max");
        }
        LocalDate today = LocalDate.now(clock);
        boolean documents = actor.has(Permission.DOCUMENT_VIEW);

        List<Entry> timeline = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        List<Entry> hearings = hearings(caseId);
        List<Entry> judgments = judgments(caseId, documents);
        List<Entry> proceedings = proceedings(caseId);
        List<Entry> claimsSubmitted = new ArrayList<>();
        List<Entry> requests = null;
        List<Entry> defenses = null;
        if (actor.has(Permission.PROCEEDING_VIEW)) {
            timeline.addAll(proceedings);
        } else {
            omitted.add("PROCEEDING");
        }
        if (actor.has(Permission.HEARING_VIEW)) {
            timeline.addAll(hearings);
        } else {
            omitted.add("HEARING");
        }
        boolean judgmentView = actor.has(Permission.JUDGMENT_VIEW);
        if (judgmentView) {
            timeline.addAll(judgments);
            timeline.addAll(specialDecisions(caseId));
        } else {
            omitted.add("JUDGMENT");
            omitted.add("SPECIAL_DECISION");
        }
        if (actor.has(Permission.CLAIM_VIEW)) {
            requests = requests(caseId, judgmentView);
            for (Entry r : requests) {
                if (r.date() != null) {
                    claimsSubmitted.add(new Entry("CLAIM_SUBMITTED", r.title(), r.status(), r.date(), r.text(), r.source(),
                            r.fields(), List.of()));
                }
            }
            timeline.addAll(claimsSubmitted);
        } else {
            omitted.add("CLAIM_SUBMITTED");
        }
        if (actor.has(Permission.DEFENSE_VIEW)) {
            defenses = defenses(caseId, judgmentView);
            for (Entry d : defenses) {
                if (d.date() != null) {
                    timeline.add(new Entry("DEFENSE_RAISED", d.title(), d.status(), d.date(), null, d.source(), d.fields(),
                            List.of()));
                }
            }
        } else {
            omitted.add("DEFENSE_RAISED");
        }
        List<Entry> deadlines = null;
        if (actor.has(Permission.DEADLINE_VIEW)) {
            deadlines = deadlines(caseId, today);
            for (Entry d : deadlines) {
                if ("COMPLETED".equals(d.status()) && d.fields().get("completedOn") != null
                        && !NOT_RECORDED.equals(d.fields().get("completedOn"))) {
                    timeline.add(new Entry("DEADLINE_COMPLETED", d.title(), d.status(),
                            LocalDate.parse(d.fields().get("completedOn")), null, d.source(), d.fields(), List.of()));
                }
            }
        } else {
            omitted.add("DEADLINE_COMPLETED");
        }
        timeline.sort(Comparator.comparing(Entry::date).thenComparingInt(e -> ORDER.indexOf(e.kind())));

        // ---- the case itself, its facts, evidence, our position and legal texts -------------------------------------
        Entry caseData = jdbc.sql("select reference_no, title, subject, case_type, status, facts from legal_case where id = :id")
                .param("id", caseId).query((rs, n) -> {
                    Map<String, String> f = new LinkedHashMap<>();
                    put(f, "referenceNo", rs.getString("reference_no"));
                    put(f, "subject", rs.getString("subject"));
                    put(f, "caseType", rs.getString("case_type"));
                    put(f, "status", rs.getString("status"));
                    return new Entry("CASE", rs.getString("title"), rs.getString("status"), null, rs.getString("facts"),
                            new Source("CASE", caseId, null, null, null, null), f, List.of());
                }).single();

        return new Brief(caseId, "RECORDS", false, NOTICE, today, caseData,
                new Section(timeline.isEmpty() ? NOT_RECORDED : RECORDED, timeline, omitted),
                lastAction(timeline, today, omitted),
                nextHearing(hearings, today),
                nextRequiredActions(hearings, deadlines, today),
                afterLastHearing(caseId, hearings, today),
                requests == null ? denied() : recorded(requests),
                requests == null ? denied() : recorded(requests.stream().filter(CaseBriefService::rejectedAnywhere).toList()),
                defenses == null ? denied() : recorded(defenses),
                defenses == null ? denied() : recorded(defenses.stream().filter(d -> List.of("NOT_ADDRESSED", "DEFERRED")
                        .contains(d.status())).toList()),
                deadlines == null ? denied() : recorded(deadlines.stream().filter(d -> "OPEN".equals(d.status())).toList()),
                appealDeadlines(caseId, judgments, deadlines, judgmentView),
                missingFiles(caseId, documents, judgmentView),
                facts(caseId, caseData),
                evidence(caseId, documents),
                defenses == null ? denied() : recorded(defenses.stream().filter(d -> d.text() != null).toList()),
                defenses == null ? denied() : recorded(defenses.stream().filter(d -> value(d.fields(), "legalTexts") != null
                        || value(d.fields(), "legalBasis") != null).map(d -> new Entry("LEGAL_TEXT", d.title(), RECORDED,
                        d.date(), value(d.fields(), "legalTexts"), d.source(), d.fields(), List.of())).toList()),
                linked(caseId),
                similar(caseId, similarLimit));
    }

    // ---- proceedings, hearings, judgments --------------------------------------------------------------------------

    private List<Entry> proceedings(UUID caseId) {
        if (!actor.has(Permission.PROCEEDING_VIEW)) {
            return List.of();
        }
        return jdbc.sql("select p.id, p.stage, p.court_case_number, p.court_case_year, p.filing_date, c.name_ar, c.name_en"
                        + " from proceeding p join court c on c.id = p.court_id where p.case_id = :id order by p.filing_date, p.id")
                .param("id", caseId).query((rs, n) -> {
                    Map<String, String> f = new LinkedHashMap<>();
                    put(f, "courtAr", rs.getString("name_ar"));
                    put(f, "courtEn", rs.getString("name_en"));
                    LocalDate date = rs.getObject("filing_date", LocalDate.class);
                    return new Entry("PROCEEDING", rs.getString("court_case_number") + "/" + rs.getInt("court_case_year"),
                            rs.getString("stage"), date, null, new Source("PROCEEDING", rs.getObject("id", UUID.class), date,
                                    null, null, null), f, List.of());
                }).list();
    }

    private List<Entry> hearings(UUID caseId) {
        if (!actor.has(Permission.HEARING_VIEW)) {
            return List.of();
        }
        return jdbc.sql("select id, hearing_date, hearing_time, purpose, kind, status, decision, required_action,"
                        + " adjournment_reason, no_next_date, no_next_date_reason, follow_up_flag, opponents_requests"
                        + " from hearing where case_id = :id order by hearing_date, hearing_time nulls last, id")
                .param("id", caseId).query((rs, n) -> {
                    Map<String, String> f = new LinkedHashMap<>();
                    put(f, "time", rs.getString("hearing_time"));
                    put(f, "kind", rs.getString("kind"));
                    put(f, "decision", rs.getString("decision"));
                    put(f, "requiredAction", rs.getString("required_action"));
                    put(f, "adjournmentReason", rs.getString("adjournment_reason"));
                    put(f, "opponentsRequests", rs.getString("opponents_requests"));
                    put(f, "noNextDateReason", rs.getString("no_next_date_reason"));
                    f.put("followUpFlag", Boolean.toString(rs.getBoolean("follow_up_flag")));
                    LocalDate date = rs.getObject("hearing_date", LocalDate.class);
                    return new Entry("HEARING", rs.getString("purpose"), rs.getString("status"), date, rs.getString("decision"),
                            new Source("HEARING", rs.getObject("id", UUID.class), date, null, null, null), f, List.of());
                }).list();
    }

    private List<Entry> judgments(UUID caseId, boolean documents) {
        if (!actor.has(Permission.JUDGMENT_VIEW)) {
            return List.of();
        }
        return jdbc.sql("select j.id, j.judgment_date, j.outcome, j.summary, j.final, j.appealable, j.notified_on,"
                        + " j.appeal_deadline_id, j.copy_document_id, d.title as doc_title, d.withdrawn_at"
                        + " from judgment j join proceeding p on p.id = j.proceeding_id"
                        + " left join case_document d on d.id = j.copy_document_id where p.case_id = :id"
                        + " order by j.judgment_date, j.id")
                .param("id", caseId).query((rs, n) -> {
                    Map<String, String> f = new LinkedHashMap<>();
                    f.put("final", Boolean.toString(rs.getBoolean("final")));
                    put(f, "appealable", rs.getObject("appealable") == null ? null : Boolean.toString(rs.getBoolean("appealable")));
                    put(f, "notifiedOn", str(rs.getObject("notified_on", LocalDate.class)));
                    put(f, "appealDeadlineId", rs.getString("appeal_deadline_id"));
                    f.put("copyWithdrawn", Boolean.toString(rs.getTimestamp("withdrawn_at") != null));
                    LocalDate date = rs.getObject("judgment_date", LocalDate.class);
                    return new Entry("JUDGMENT", rs.getString("outcome"), rs.getString("outcome"), date, rs.getString("summary"),
                            source("JUDGMENT", rs.getObject("id", UUID.class), date, rs.getObject("copy_document_id", UUID.class),
                                    rs.getString("doc_title"), documents), f, List.of());
                }).list();
    }

    private List<Entry> specialDecisions(UUID caseId) {
        return jdbc.sql("select s.id, s.decision_type, s.decision_date, s.effect, s.summary from special_decision s"
                        + " join proceeding p on p.id = s.proceeding_id where p.case_id = :id order by s.decision_date, s.id")
                .param("id", caseId).query((rs, n) -> {
                    LocalDate date = rs.getObject("decision_date", LocalDate.class);
                    return new Entry("SPECIAL_DECISION", rs.getString("decision_type"), rs.getString("effect"), date,
                            rs.getString("summary"), new Source("SPECIAL_DECISION", rs.getObject("id", UUID.class), date, null,
                                    null, null), new LinkedHashMap<>(), List.of());
                }).list();
    }

    // ---- last action, next hearing, actions ------------------------------------------------------------------------

    /** The records of the latest date up to today that is an act (a hearing counts once it is no longer scheduled). */
    private Section lastAction(List<Entry> timeline, LocalDate today, List<String> omitted) {
        LocalDate latest = null;
        for (Entry e : timeline) {
            if (isAct(e, today) && (latest == null || e.date().isAfter(latest))) {
                latest = e.date();
            }
        }
        if (latest == null) {
            return new Section(NOT_RECORDED, List.of(), omitted);
        }
        LocalDate day = latest;
        return new Section(RECORDED, timeline.stream().filter(e -> isAct(e, today) && e.date().equals(day)).toList(), omitted);
    }

    private static boolean isAct(Entry e, LocalDate today) {
        return !e.date().isAfter(today) && !("HEARING".equals(e.kind()) && "SCHEDULED".equals(e.status()));
    }

    private Section nextHearing(List<Entry> hearings, LocalDate today) {
        if (!actor.has(Permission.HEARING_VIEW)) {
            return denied();
        }
        return hearings.stream().filter(h -> "SCHEDULED".equals(h.status()) && !h.date().isBefore(today)).findFirst()
                .map(h -> recorded(List.of(h))).orElse(new Section(NOT_RECORDED, List.of(), List.of()));
    }

    /** The latest hearing already held or adjourned (its result is recorded), or null. */
    private static Entry lastHearing(List<Entry> hearings, LocalDate today) {
        Entry last = null;
        for (Entry h : hearings) {
            if (("HELD".equals(h.status()) || "ADJOURNED".equals(h.status())) && !h.date().isAfter(today)) {
                last = h;
            }
        }
        return last;
    }

    private Section afterLastHearing(UUID caseId, List<Entry> hearings, LocalDate today) {
        if (!actor.has(Permission.HEARING_VIEW)) {
            return denied();
        }
        Entry last = lastHearing(hearings, today);
        if (last == null) {
            return new Section(NOT_RECORDED, List.of(), List.of());
        }
        List<Entry> items = new ArrayList<>();
        String action = value(last.fields(), "requiredAction");
        items.add(new Entry("REQUIRED_ACTION", last.title(), action == null ? NOT_RECORDED : RECORDED, last.date(), action,
                last.source(), last.fields(), List.of()));
        List<String> omitted = new ArrayList<>();
        if (actor.has(Permission.DEADLINE_VIEW)) {
            items.addAll(deadlines(caseId, LocalDate.now(clock)).stream()
                    .filter(d -> "HEARING".equals(d.fields().get("sourceType"))
                            && last.source().id().toString().equals(d.fields().get("sourceId")))
                    .map(d -> new Entry("DEADLINE_FROM_HEARING", d.title(), d.status(), d.date(), d.text(), d.source(),
                            d.fields(), List.of())).toList());
        } else {
            omitted.add("DEADLINE");
        }
        return new Section(RECORDED, items, omitted);
    }

    private Section nextRequiredActions(List<Entry> hearings, List<Entry> deadlines, LocalDate today) {
        List<Entry> items = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        if (actor.has(Permission.HEARING_VIEW)) {
            Entry last = lastHearing(hearings, today);
            if (last != null && value(last.fields(), "requiredAction") != null) {
                items.add(new Entry("REQUIRED_ACTION", last.title(), RECORDED, last.date(), value(last.fields(), "requiredAction"),
                        last.source(), last.fields(), List.of()));
            }
        } else {
            omitted.add("HEARING");
        }
        if (deadlines != null) {
            deadlines.stream().filter(d -> "OPEN".equals(d.status()))
                    .sorted(Comparator.comparing(Entry::date, Comparator.nullsLast(Comparator.naturalOrder())))
                    .forEach(items::add);
        } else {
            omitted.add("DEADLINE");
        }
        return new Section(items.isEmpty() ? NOT_RECORDED : RECORDED, items, omitted);
    }

    // ---- requests and defences -------------------------------------------------------------------------------------

    private record Ruling(UUID ownerId, Entry entry) {
    }

    private List<Entry> requests(UUID caseId, boolean rulingsVisible) {
        Map<UUID, List<Entry>> rulings = new LinkedHashMap<>();
        if (rulingsVisible) {
            jdbc.sql("select r.claim_id, r.result, r.operative, j.id as j_id, j.judgment_date from claim_result r"
                            + " join claim c on c.id = r.claim_id join judgment j on j.id = r.judgment_id"
                            + " where c.case_id = :id order by j.judgment_date, r.created_at, r.id")
                    .param("id", caseId).query((rs, n) -> new Ruling(rs.getObject("claim_id", UUID.class), ruling(rs, "result")))
                    .list().forEach(r -> rulings.computeIfAbsent(r.ownerId(), k -> new ArrayList<>()).add(r.entry()));
        }
        return jdbc.sql("select id, seq, text, kind, status, submitted_on from claim where case_id = :id order by seq")
                .param("id", caseId).query((rs, n) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    List<Entry> own = rulings.getOrDefault(id, List.of());
                    Map<String, String> f = new LinkedHashMap<>();
                    put(f, "claimKind", rs.getString("kind"));
                    put(f, "claimStatus", rs.getString("status"));
                    LocalDate date = rs.getObject("submitted_on", LocalDate.class);
                    return new Entry("CLAIM", "#" + rs.getInt("seq"), position(own, rulingsVisible), date, rs.getString("text"),
                            new Source("CLAIM", id, date, null, null, null), f, own);
                }).list();
    }

    private List<Entry> defenses(UUID caseId, boolean rulingsVisible) {
        Map<UUID, List<Entry>> rulings = new LinkedHashMap<>();
        if (rulingsVisible) {
            jdbc.sql("select p.defense_id, p.position, p.operative, j.id as j_id, j.judgment_date from defense_position p"
                            + " join defense d on d.id = p.defense_id join judgment j on j.id = p.judgment_id"
                            + " where d.case_id = :id order by j.judgment_date, p.created_at, p.id")
                    .param("id", caseId).query((rs, n) -> new Ruling(rs.getObject("defense_id", UUID.class),
                            ruling(rs, "position")))
                    .list().forEach(r -> rulings.computeIfAbsent(r.ownerId(), k -> new ArrayList<>()).add(r.entry()));
        }
        return jdbc.sql("select id, seq, title, kind, argument, legal_basis, legal_texts, raised_on from defense"
                        + " where case_id = :id order by seq")
                .param("id", caseId).query((rs, n) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    List<Entry> own = rulings.getOrDefault(id, List.of());
                    Map<String, String> f = new LinkedHashMap<>();
                    put(f, "defenseKind", rs.getString("kind"));
                    put(f, "legalBasis", rs.getString("legal_basis"));
                    put(f, "legalTexts", rs.getString("legal_texts"));
                    LocalDate date = rs.getObject("raised_on", LocalDate.class);
                    return new Entry("DEFENSE", rs.getString("title"), position(own, rulingsVisible), date,
                            rs.getString("argument"), new Source("DEFENSE", id, date, null, null, null), f, own);
                }).list();
    }

    /** One ruling of a judgment on a request or a defence, as the judgment's own record states it. */
    private static Entry ruling(ResultSet rs, String column) throws SQLException {
        LocalDate date = rs.getObject("judgment_date", LocalDate.class);
        return new Entry("RULING", null, rs.getString(column), date, rs.getString("operative"),
                new Source("JUDGMENT", rs.getObject("j_id", UUID.class), date, null, null, null), new LinkedHashMap<>(), List.of());
    }

    /** The latest recorded ruling (by judgment date); NOT_RECORDED when no judgment ruled, NOT_PERMITTED without JUDGMENT_VIEW. */
    private static String position(List<Entry> rulings, boolean visible) {
        if (!visible) {
            return NOT_PERMITTED;
        }
        return rulings.isEmpty() ? NOT_RECORDED : rulings.get(rulings.size() - 1).status();
    }

    private static boolean rejectedAnywhere(Entry request) {
        return request.children().stream().anyMatch(c -> "REJECTED".equals(c.status()));
    }

    // ---- deadlines -------------------------------------------------------------------------------------------------

    /** Every deadline of the case as recorded. A due date is shown only when one is stored; a period is never counted here. */
    private List<Entry> deadlines(UUID caseId, LocalDate today) {
        return jdbc.sql("select id, action, status, due_on, period_days, calc_status, source_type, source_id, source_label,"
                        + " completed_on from deadline where case_id = :id order by due_on nulls last, id")
                .param("id", caseId).query((rs, n) -> {
                    Map<String, String> f = new LinkedHashMap<>();
                    put(f, "calcStatus", rs.getString("calc_status"));
                    put(f, "sourceType", rs.getString("source_type"));
                    put(f, "sourceId", rs.getString("source_id"));
                    put(f, "sourceLabel", rs.getString("source_label"));
                    put(f, "periodDays", rs.getObject("period_days") == null ? null : Integer.toString(rs.getInt("period_days")));
                    put(f, "completedOn", str(rs.getObject("completed_on", LocalDate.class)));
                    LocalDate due = rs.getObject("due_on", LocalDate.class);
                    put(f, "dueOn", str(due));
                    f.put("overdue", Boolean.toString("OPEN".equals(rs.getString("status")) && due != null && due.isBefore(today)));
                    return new Entry("DEADLINE", rs.getString("action"), rs.getString("status"), due, null,
                            new Source("DEADLINE", rs.getObject("id", UUID.class), due, null, null, null), f, List.of());
                }).list();
    }

    /** Per judgment: the deadline it names as its appeal deadline, or an explicit NOT_RECORDED. Nothing is calculated. */
    private Section appealDeadlines(UUID caseId, List<Entry> judgments, List<Entry> deadlines, boolean judgmentView) {
        if (!judgmentView || deadlines == null) {
            return denied();
        }
        List<Entry> items = new ArrayList<>();
        for (Entry j : judgments) {
            String deadlineId = value(j.fields(), "appealDeadlineId");
            Map<String, String> f = new LinkedHashMap<>(j.fields());
            Entry deadline = deadlineId == null ? null : deadlines.stream()
                    .filter(d -> d.source().id().toString().equals(deadlineId)).findFirst().orElse(null);
            if (deadline == null) {
                put(f, "dueOn", null);
                items.add(new Entry("APPEAL_DEADLINE", j.title(), NOT_RECORDED, j.date(), null, j.source(), f, List.of()));
            } else {
                f.putAll(deadline.fields());
                items.add(new Entry("APPEAL_DEADLINE", deadline.title(), deadline.status(), deadline.date(), null,
                        deadline.source(), f, List.of()));
            }
        }
        return new Section(items.isEmpty() ? NOT_RECORDED : RECORDED, items, List.of());
    }

    // ---- files -----------------------------------------------------------------------------------------------------

    /**
     * Facts the records themselves show about files: a judgment with no copy attached, a copy that was withdrawn, an evidence
     * item recorded without a file. There is no checklist of required documents; nothing else is reported as missing.
     */
    private Section missingFiles(UUID caseId, boolean documents, boolean judgmentView) {
        List<Entry> items = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        if (judgmentView) {
            items.addAll(jdbc.sql("select j.id, j.judgment_date, j.copy_document_id, d.title, d.withdrawn_at from judgment j"
                            + " join proceeding p on p.id = j.proceeding_id left join case_document d on d.id = j.copy_document_id"
                            + " where p.case_id = :id and (j.copy_document_id is null or d.withdrawn_at is not null)"
                            + " order by j.judgment_date, j.id")
                    .param("id", caseId).query((rs, n) -> {
                        LocalDate date = rs.getObject("judgment_date", LocalDate.class);
                        String reason = rs.getObject("copy_document_id") == null ? "NO_FILE_ATTACHED" : "FILE_WITHDRAWN";
                        return new Entry("JUDGMENT", null, reason, date, null, source("JUDGMENT", rs.getObject("id", UUID.class),
                                date, rs.getObject("copy_document_id", UUID.class), rs.getString("title"), documents),
                                new LinkedHashMap<>(), List.of());
                    }).list());
        } else {
            omitted.add("JUDGMENT");
        }
        if (actor.has(Permission.EVIDENCE_VIEW)) {
            items.addAll(jdbc.sql("select e.id, e.name, e.role, e.received_on, e.document_id, d.title, d.withdrawn_at"
                            + " from evidence_item e left join case_document d on d.id = e.document_id"
                            + " where e.case_id = :id and (e.document_id is null or d.withdrawn_at is not null)"
                            + " order by e.name, e.id")
                    .param("id", caseId).query((rs, n) -> {
                        Map<String, String> f = new LinkedHashMap<>();
                        put(f, "evidenceRole", rs.getString("role"));
                        LocalDate date = rs.getObject("received_on", LocalDate.class);
                        String reason = rs.getObject("document_id") == null ? "NO_FILE_ATTACHED" : "FILE_WITHDRAWN";
                        return new Entry("EVIDENCE", rs.getString("name"), reason, date, null, source("EVIDENCE_ITEM",
                                rs.getObject("id", UUID.class), date, rs.getObject("document_id", UUID.class),
                                rs.getString("title"), documents), f, List.of());
                    }).list());
        } else {
            omitted.add("EVIDENCE");
        }
        return new Section(items.isEmpty() ? NOT_RECORDED : RECORDED, items, omitted);
    }

    // ---- dossier: facts, evidence ----------------------------------------------------------------------------------

    private Section facts(UUID caseId, Entry caseData) {
        if (!actor.has(Permission.EVIDENCE_VIEW)) {
            return denied();
        }
        List<Entry> items = new ArrayList<>();
        if (caseData.text() != null) {
            items.add(new Entry("CASE_FACTS", caseData.title(), RECORDED, null, caseData.text(), caseData.source(),
                    new LinkedHashMap<>(), List.of()));
        }
        items.addAll(jdbc.sql("select id, seq, text, fact_date from case_fact where case_id = :id order by seq")
                .param("id", caseId).query((rs, n) -> {
                    LocalDate date = rs.getObject("fact_date", LocalDate.class);
                    return new Entry("FACT", "#" + rs.getInt("seq"), RECORDED, date, rs.getString("text"),
                            new Source("CASE_FACT", rs.getObject("id", UUID.class), date, null, null, null),
                            new LinkedHashMap<>(), List.of());
                }).list());
        return recorded(items);
    }

    private Section evidence(UUID caseId, boolean documents) {
        if (!actor.has(Permission.EVIDENCE_VIEW)) {
            return denied();
        }
        return recorded(jdbc.sql("select e.id, e.name, e.role, e.importance, e.submitted_to_court, e.submitted_on,"
                        + " e.court_position, e.document_id, d.title from evidence_item e"
                        + " left join case_document d on d.id = e.document_id where e.case_id = :id order by e.name, e.id")
                .param("id", caseId).query((rs, n) -> {
                    Map<String, String> f = new LinkedHashMap<>();
                    put(f, "importance", rs.getString("importance"));
                    f.put("submittedToCourt", Boolean.toString(rs.getBoolean("submitted_to_court")));
                    put(f, "submittedOn", str(rs.getObject("submitted_on", LocalDate.class)));
                    put(f, "courtPosition", rs.getString("court_position"));
                    LocalDate date = rs.getObject("submitted_on", LocalDate.class);
                    return new Entry("EVIDENCE", rs.getString("name"), rs.getString("role"), date, null,
                            source("EVIDENCE_ITEM", rs.getObject("id", UUID.class), date, rs.getObject("document_id", UUID.class),
                                    rs.getString("title"), documents), f, List.of());
                }).list());
    }

    // ---- library -------------------------------------------------------------------------------------------------

    private Section linked(UUID caseId) {
        if (!library.available()) {
            return denied();
        }
        return recorded(library.linkedTo(caseId).stream().map(l -> new Entry("LIBRARY_JUDGMENT", citation(l.courtName(),
                l.caseNumber(), l.caseYear()), "LINKED", l.judgmentDate(), l.subject(), new Source("LIBRARY_JUDGMENT",
                        l.judgmentId(), l.judgmentDate(), null, null, null), mapOf("reason", l.reason()), List.of())).toList());
    }

    private Section similar(UUID caseId, int limit) {
        if (!library.available()) {
            return denied();
        }
        return recorded(library.similarTo(caseId, limit).stream().map(a -> new Entry("LIBRARY_JUDGMENT", citation(a.courtName(),
                a.caseNumber(), a.caseYear()), "SIMILAR", a.judgmentDate(), a.subject(), new Source("LIBRARY_JUDGMENT", a.id(),
                        a.judgmentDate(), null, null, null), mapOf("reasons", String.join("; ", a.reasons())), List.of()))
                .toList());
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private static String citation(String court, String number, Integer year) {
        return court + (number == null ? "" : " " + number) + (year == null ? "" : "/" + year);
    }

    private static Map<String, String> mapOf(String key, String value) {
        Map<String, String> map = new LinkedHashMap<>();
        put(map, key, value);
        return map;
    }

    /** Stores the value, or the explicit NOT_RECORDED marker when the record holds none. */
    private static void put(Map<String, String> fields, String key, String value) {
        fields.put(key, value == null || value.isBlank() ? NOT_RECORDED : value);
    }

    /** The recorded value, or null when the record holds none. */
    private static String value(Map<String, String> fields, String key) {
        String v = fields.get(key);
        return v == null || NOT_RECORDED.equals(v) ? null : v;
    }

    private static String str(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static Section recorded(List<Entry> items) {
        return new Section(items.isEmpty() ? NOT_RECORDED : RECORDED, items, List.of());
    }

    private static Section denied() {
        return new Section(NOT_PERMITTED, List.of(), List.of());
    }

    /** The source of a record; the cited document is named only for a user who may open documents. Pages are never stored. */
    private static Source source(String type, UUID id, LocalDate date, UUID documentId, String title, boolean documents) {
        return new Source(type, id, date, documents ? documentId : null, documents ? title : null, null);
    }
}
