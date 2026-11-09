package com.lawdesk.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.Permission;

/**
 * What the unified search reads, per record type (LD-057, requirements section 28): where each type's rows come from, the
 * parts of a record that are searched with their weight (A highest), and the case-access restriction. Every type that
 * belongs to a case is restricted with {@code case_visible} (D3) before anything is matched, so a hit, a snippet and a total
 * can only come from a visible record. All text is matched through {@code library_normalize} (the WP7 Arabic normaliser).
 */
final class SearchSpecs {

    /** Canonical order of the groups in an answer. */
    static final List<String> ORDER = List.of("CASE", "PARTY", "CLAIM", "DEFENSE", "HEARING", "JUDGMENT", "DOCUMENT", "NOTE", "TASK", "LIBRARY",
            "SETTLEMENT", "DIRECTIVE");

    /** One searched part of a record: the code reported in {@code matchedIn}, its SQL text expression and its weight. */
    record Part(String key, String sql, char weight) {
    }

    /**
     * @param from      the FROM clause (with its joins; the case is alias {@code c} when the record belongs to one)
     * @param condition the restriction on rows: the case-access rule and anything fixed for the type
     * @param caseFilter how the case filters apply, as a SQL boolean using the named parameters, or null to exclude the type
     * @param date      the record's date, for the date filters
     */
    record Spec(String type, Permission permission, String from, String condition, String id, String caseId, String caseRef,
            String title, String detail, String date, String extra, List<Part> parts, String caseTypeSql, String caseIdSql,
            Stored stored) {

        Spec withStored(Stored stored) {
            return new Spec(type, permission, from, condition, id, caseId, caseRef, title, detail, date, extra, parts, caseTypeSql,
                    caseIdSql, stored);
        }

        Spec(String type, Permission permission, String from, String condition, String id, String caseId, String caseRef,
                String title, String detail, String date, String extra, List<Part> parts, String caseTypeSql, String caseIdSql) {
            this(type, permission, from, condition, id, caseId, caseRef, title, detail, date, extra, parts, caseTypeSql, caseIdSql,
                    null);
        }
    }

    /**
     * A type whose searchable text is stored, already normalised, in a vector that a GIN index covers (V800). The query asks the
     * index for the candidate ids, restricts them with the access rule, and only then ranks them; the parts of {@link Spec} are
     * evaluated for the rows of the page alone (matched-in flags and the snippet), never for the whole register.
     *
     * @param with     extra common table expressions (without the leading comma), or null; they may use the CTE {@code q}
     * @param from     joins appended to the type's FROM clause, giving access to what {@code vector} needs
     * @param vector   the full weighted tsvector of a record for the signed-in user
     * @param matchIds a query returning the ids that may match (a superset of the matches is allowed; the vector is rechecked)
     */
    record Stored(String with, String from, String vector, String matchIds) {
    }

    private SearchSpecs() {
    }

    /** Type to {table, alias in the spec} for the types with a {@code search_vec} column (V801). */
    private static final Map<String, String[]> OWN_VECTOR = Map.ofEntries(
            Map.entry("CLAIM", new String[] {"claim", "cl"}), Map.entry("DEFENSE", new String[] {"defense", "f"}),
            Map.entry("HEARING", new String[] {"hearing", "h"}), Map.entry("JUDGMENT", new String[] {"judgment", "j"}),
            Map.entry("DOCUMENT", new String[] {"case_document", "d"}), Map.entry("NOTE", new String[] {"case_note", "n"}),
            Map.entry("TASK", new String[] {"task", "t"}), Map.entry("LIBRARY", new String[] {"library_judgment", "j"}),
            Map.entry("SETTLEMENT", new String[] {"settlement", "s"}), Map.entry("DIRECTIVE", new String[] {"directive", "d"}));

    private static final String KUWAIT_DATE = "at time zone 'Asia/Kuwait')::date";

    private static Part p(String key, String sql, char weight) {
        return new Part(key, sql, weight);
    }

    static Map<String, Spec> all(CaseAccessPolicy access, boolean canId) {
        Map<String, Spec> specs = new java.util.LinkedHashMap<>();
        for (Spec spec : build(access, canId)) {
            String[] own = OWN_VECTOR.get(spec.type());
            // Types whose text is all in one row (or kept in that row by a trigger, V801) read their stored search_vec.
            specs.put(spec.type(), own == null ? spec : spec.withStored(new Stored(null, "", own[1] + ".search_vec",
                    "select x.id from " + own[0] + " x where x.search_vec @@ (select tq from q)")));
        }
        return specs;
    }

    private static List<Spec> build(CaseAccessPolicy access, boolean canId) {
        // The case is already joined as c in every type that has one, so the rule is tested on that row (cheap column tests
        // first, the database function last) instead of by a second pass over the register.
        String visC = access.viewable("c").sql();
        String caseType = "c.case_type = :caseType";
        String caseId = "c.id = :caseId";
        List<Spec> specs = new ArrayList<>();

        specs.add(new Spec("CASE", Permission.CASE_VIEW, "legal_case c", visC, "c.id", "c.id", "c.reference_no", "c.title",
                "c.status", "c.opened_on", "null::integer", List.of(
                        p("REFERENCE", "c.reference_no", 'A'),
                        p("NUMBERS", "concat_ws(' ', c.automated_no, (select string_agg(pr.court_case_number || ' ' || pr.court_case_year,"
                                + " ' ') from proceeding pr where pr.case_id = c.id))", 'A'),
                        p("TITLE", "c.title", 'A'),
                        p("SUBJECT", "c.subject", 'B'),
                        p("UNITS", "(select string_agg(concat_ws(' ', i.sector_name_snapshot, i.department_name_snapshot,"
                                + " i.section_name_snapshot, u.name_ar), ' ') from case_org_involvement i left join org_unit u"
                                + " on u.id = i.org_unit_id where i.case_id = c.id and i.removed_at is null)", 'B'),
                        p("MEMBERS", "(select string_agg(m.display_name, ' ') from app_user m where m.id in"
                                + " (c.primary_member_id, c.alternate_member_id))", 'B'),
                        p("FACTS", "c.facts", 'C'),
                        p("CHAMBER", "c.chamber", 'C'),
                        // Only cases the user may see contribute: a link to a hidden case adds nothing.
                        p("LINKED_CASES", "(select string_agg(concat_ws(' ', o.reference_no, o.title, l.reason), ' ') from"
                                + " case_link l join legal_case o on o.id = case when l.case_id = c.id then l.other_case_id"
                                + " else l.case_id end where (l.case_id = c.id or l.other_case_id = c.id) and "
                                + access.viewable("o").sql() + ")", 'C')),
                caseType, caseId,
                // The case document is stored (search_case_doc); what a case gains from the cases it is linked to is added per
                // request from the stored link texts, and only from linked cases the user may see.
                new Stored(null,
                        " join search_case_doc sd on sd.case_id = c.id left join lateral (select tsvector_agg(d.vec) as vec from"
                                + " search_link_doc d join legal_case o on o.id = d.other_id where sd.linked and d.host_id = c.id and "
                                + access.viewable("o").sql() + ") lk on true",
                        "(sd.vec || coalesce(lk.vec, cast('' as tsvector)))",
                        "select x.case_id from search_case_doc x where x.vec @@ (select tq from q) union select d.host_id"
                                + " from search_link_doc d where d.vec @@ (select tqa from q)")));

        // A party is found through the visible cases it is on; a party of hidden cases only does not exist for the user.
        // Identity numbers and a confidential party's contact details are searched only by holders of PARTY_ID_VIEW (D4).
        specs.add(new Spec("PARTY", Permission.CASE_VIEW,
                "party p cross join lateral (select c.id as case_id, c.reference_no, count(*) over () as n from case_party cp"
                        + " join legal_case c on c.id = cp.case_id where cp.party_id = p.id and " + visC
                        + " %CASE_FILTERS% order by c.opened_on desc, c.id limit 1) vc",
                "true", "p.id", "vc.case_id", "vc.reference_no", "p.name", "p.kind",
                "(p.created_at " + KUWAIT_DATE, "vc.n::integer", List.of(
                        p("NAME", "p.name", 'A'),
                        p("IDENTIFIER", "case when :canId then concat_ws(' ', p.national_id, p.registration_no) end", 'B'),
                        p("NOTES", "p.notes", 'C'),
                        p("CONTACT", "case when :canId or not p.confidential then concat_ws(' ', p.phone, p.email, p.address) end",
                                'D')),
                caseType, caseId,
                new Stored(null, "", canId ? "p.search_full" : "p.search_public",
                        "select x.id from party x where x." + (canId ? "search_full" : "search_public") + " @@ (select tq from q)")));

        // Requests and defences (WP2) belong to a case and follow it.
        specs.add(new Spec("CLAIM", Permission.CLAIM_VIEW, "claim cl join legal_case c on c.id = cl.case_id", visC, "cl.id", "c.id",
                "c.reference_no", "c.title", "cl.status", "cl.submitted_on", "null::integer",
                List.of(p("TEXT", "cl.text", 'A'), p("NOTES", "cl.notes", 'C')), caseType, caseId));

        specs.add(new Spec("DEFENSE", Permission.DEFENSE_VIEW, "defense f join legal_case c on c.id = f.case_id", visC, "f.id",
                "c.id", "c.reference_no", "f.title", "f.kind", "f.raised_on", "null::integer", List.of(
                        p("TITLE", "f.title", 'A'),
                        p("ARGUMENT", "f.argument", 'A'),
                        p("BASIS", "f.legal_basis", 'B'),
                        p("FACTS", "f.facts", 'B'),
                        p("TEXTS", "f.legal_texts", 'B'),
                        p("NOTES", "f.notes", 'C')),
                caseType, caseId));

        specs.add(new Spec("HEARING", Permission.HEARING_VIEW, "hearing h join legal_case c on c.id = h.case_id", visC, "h.id",
                "c.id", "c.reference_no", "c.title", "h.purpose", "h.hearing_date", "null::integer", List.of(
                        p("SUMMARY", "h.proceedings_summary", 'A'),
                        p("DECISION", "h.decision", 'A'),
                        p("NOTES", "h.notes", 'B'),
                        p("REQUESTS", "h.opponents_requests", 'B'),
                        p("ACTION", "h.required_action", 'B'),
                        p("ADJOURNMENT", "h.adjournment_reason", 'C'),
                        p("ATTENDEES", "h.attendees", 'C'),
                        p("JUDGE", "h.judge_or_expert", 'C'),
                        p("CHAMBER", "h.chamber", 'C')),
                caseType, caseId));

        specs.add(new Spec("JUDGMENT", Permission.JUDGMENT_VIEW,
                "judgment j join proceeding pr on pr.id = j.proceeding_id join legal_case c on c.id = pr.case_id", visC, "j.id",
                "c.id", "c.reference_no", "c.title", "j.outcome", "j.judgment_date", "null::integer",
                List.of(p("OPERATIVE", "j.operative_text", 'A'), p("SUMMARY", "j.summary", 'A'), p("REASONS", "j.reasons", 'C')),
                caseType, caseId));

        specs.add(new Spec("DOCUMENT", Permission.DOCUMENT_VIEW, "case_document d join legal_case c on c.id = d.case_id", visC,
                "d.id", "c.id", "c.reference_no", "d.title", "d.file_name", "(d.created_at " + KUWAIT_DATE, "null::integer",
                List.of(p("TITLE", "d.title", 'A'), p("FILE_NAME", "d.file_name", 'B')), caseType, caseId));

        specs.add(new Spec("NOTE", Permission.CASE_VIEW, "case_note n join legal_case c on c.id = n.case_id", visC, "n.id",
                "c.id", "c.reference_no", "c.title", "null::text", "(n.created_at " + KUWAIT_DATE, "null::integer",
                List.of(p("BODY", "n.body", 'A')), caseType, caseId));

        // A task of a case follows the case; a task of no case is found by its assignee and its creator only.
        specs.add(new Spec("TASK", Permission.TASK_VIEW, "task t left join legal_case c on c.id = t.case_id",
                "((t.case_id is not null and " + visC + ") or (t.case_id is null and"
                        + " (t.assignee_id = :av_me or t.created_by = :av_me)))",
                "t.id", "t.case_id", "c.reference_no", "t.title", "t.status", "coalesce(t.due_date, (t.created_at "
                        + KUWAIT_DATE + ")", "null::integer",
                List.of(p("TITLE", "t.title", 'A'), p("DESCRIPTION", "t.description", 'B')), caseType, "t.case_id = :caseId"));

        // The library is department-wide reference data (LIBRARY_VIEW), not case data; withdrawn judgments are not offered.
        specs.add(new Spec("LIBRARY", Permission.LIBRARY_VIEW, "library_judgment j", "j.status = 'ACTIVE'", "j.id", "null::uuid",
                "null::text", "j.subject", "j.court_name", "j.judgment_date", "null::integer", List.of(
                        p("SUBJECT", "j.subject", 'A'),
                        p("KEYWORDS", "j.keywords", 'A'),
                        p("SUMMARY", "j.summary", 'B'),
                        p("LEGAL_TEXTS", "j.legal_texts", 'B'),
                        p("PRINCIPLE", "(select string_agg(concat_ws(' ', lp.principle_text, lp.legal_topic, lp.legal_basis), ' ')"
                                + " from library_principle lp where lp.judgment_id = j.id)", 'B'),
                        p("OPERATIVE", "j.operative_text", 'C'),
                        p("REASONS", "j.reasons", 'D')),
                "j.case_type = :caseType", null));

        specs.add(new Spec("SETTLEMENT", Permission.CASE_VIEW, "settlement s join legal_case c on c.id = s.case_id", visC,
                "s.id", "c.id", "c.reference_no", "c.title", "s.status", "(s.created_at " + KUWAIT_DATE, "null::integer",
                List.of(
                        p("GROUNDS", "s.legal_grounds", 'A'),
                        p("OPINION", "s.legal_opinion", 'A'),
                        p("PARTY", "concat_ws(' ', s.offering_party_text, (select y.name from party y where y.id = s.offering_party_id))",
                                'B'),
                        p("IMPACT", "s.financial_impact_note", 'B'),
                        p("AUTHORITY", "s.approving_authority", 'C'),
                        p("DECISION", "s.decision_reason", 'C')),
                caseType, caseId));

        specs.add(new Spec("DIRECTIVE", Permission.CASE_VIEW, "directive d join legal_case c on c.id = d.case_id", visC, "d.id",
                "c.id", "c.reference_no", "d.title", "d.status", "d.directive_date", "null::integer", List.of(
                        p("TITLE", "d.title", 'A'),
                        p("NUMBER", "concat_ws(' ', d.directive_no, d.source_reference_no)", 'A'),
                        p("DETAILS", "d.details", 'B'),
                        p("SOURCE", "d.source", 'B'),
                        p("ADDRESSEE", "concat_ws(' ', d.addressee_text, (select z.display_name from app_user z"
                                + " where z.id = d.addressee_user_id))", 'C')),
                caseType, caseId));
        return specs;
    }
}
