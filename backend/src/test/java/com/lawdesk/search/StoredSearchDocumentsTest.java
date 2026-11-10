package com.lawdesk.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.platform.SqlStatementCounter;
import com.lawdesk.security.Permission;
import com.lawdesk.wp9.Wp9TestSupport;

/**
 * LD-058 round 2: the stored search documents (V801) and the guards that keep the search and the case-list text filter
 * fast without depending on the speed of the machine: documents kept equal to a full recomputation by the triggers, the
 * trigram prefilter never dropping a match, the number of statements per request, and the indexes being usable by the queries
 * the application sends (checked with sequential scans disabled, since a test database is too small for the planner to prefer an
 * index).
 */
@Import(SqlStatementCounter.Config.class)
class StoredSearchDocumentsTest extends Wp9TestSupport {

    private List<Map<String, Object>> documents() {
        return jdbc.queryForList("select case_id, vec::text as vec, trigrams::text as trigrams from search_case_doc order by case_id");
    }

    private List<Map<String, Object>> linkDocuments() {
        return jdbc.queryForList("select link_id, host_id, vec::text as vec from search_link_doc order by link_id, host_id");
    }

    @Test
    void theDocumentsKeptByTheTriggersEqualAFullRecomputationAfterEveryKindOfWrite() {
        UUID owner = user("Owner");
        UUID other = user("Other Member");
        UUID first = legalCase(owner, null);
        UUID second = legalCase(owner, other);
        UUID proceeding = proceeding(first, "4411");
        jdbc.update("update legal_case set title = 'نزاع حول عقد التوريد', subject = 'مطالبة', facts = 'وقائع', chamber = 'الدائرة 5' where id = ?", first);
        UUID party = party("شركة النور", "ORGANIZATION", null, false, null);
        onCase(first, party, "OPPONENT");
        UUID unit = authorityUnit("إدارة العقود");
        jdbc.update("insert into case_org_involvement (case_id, org_unit_id, is_primary, created_by, updated_by)"
                + " values (?, ?, true, gen_random_uuid(), gen_random_uuid())", first, unit);
        jdbc.update("""
                insert into case_link (case_id, other_case_id, kind, reason, status, created_by, updated_by)
                values (?, ?, 'SUBJECT_LINK', 'نفس الموضوع', 'ACTIVE', gen_random_uuid(), gen_random_uuid())""", first, second);
        // Every kind of write that changes what a document is made of.
        jdbc.update("update proceeding set court_case_number = '9922' where id = ?", proceeding);
        jdbc.update("update party set name = 'شركة الأنوار' where id = ?", party);
        jdbc.update("update org_unit set name_ar = 'إدارة العقود والمشتريات' where id = ?", unit);
        jdbc.update("update app_user set display_name = 'Renamed Owner' where id = ?", owner);
        jdbc.update("update legal_case set title = 'عنوان جديد للقضية الثانية' where id = ?", second);
        jdbc.update("update case_link set reason = 'سبب جديد' where case_id = ?", first);
        proceeding(second, "5533");
        jdbc.update("delete from proceeding where court_case_number = '5533'");
        // LD-DOC-R3 (V845): an update refreshes only when a searchable column changed. Each searchable column of the case, of
        // the proceeding and of the involvement is changed on its own, and a write that changes none of them is made too.
        jdbc.update("update legal_case set status = 'ACTIVE', claimed_amount = 5 where id = ?", first);
        jdbc.update("update legal_case set reference_no = 'LD-R3-REF-1' where id = ?", first);
        jdbc.update("update legal_case set automated_no = 'R3AUTO9' where id = ?", first);
        jdbc.update("update legal_case set alternate_member_id = ? where id = ?", other, first);
        jdbc.update("update legal_case set alternate_member_id = null where id = ?", second);
        jdbc.update("update legal_case set primary_member_id = ? where id = ?", other, second);
        jdbc.update("update proceeding set court_case_year = 2031, filing_date = filing_date + 1 where id = ?", proceeding);
        jdbc.update("update case_org_involvement set sector_name_snapshot = 'R3 sector', contact_name = 'R3 contact' where case_id = ?", first);
        jdbc.update("update case_org_involvement set removed_at = now(), removed_by = gen_random_uuid(), is_primary = false where case_id = ?", first);
        jdbc.update("update case_org_involvement set removed_at = null, removed_by = null, is_primary = true where case_id = ?", first);

        var before = documents();
        var linksBefore = linkDocuments();
        assertThat(before).hasSize(2);
        assertThat(linksBefore).hasSize(2);
        jdbc.queryForObject("select search_case_refresh(array(select id from legal_case))", Object.class);
        jdbc.queryForObject("select search_link_refresh(array(select id from case_link))", Object.class);
        assertThat(documents()).as("incremental maintenance equals a full recomputation").isEqualTo(before);
        assertThat(linkDocuments()).isEqualTo(linksBefore);
        // And the renamed link partner shows up in the text of the case it is linked to.
        assertThat(jdbc.queryForObject("select count(*) from search_link_doc where host_id = ? and vec @@ to_tsquery('simple', 'جديد:*')",
                Integer.class, first)).isEqualTo(1);
    }

    /** The stored documents must equal a full recomputation; refreshing them changes nothing when they are already right. */
    private void assertDocumentsEqualFullRecomputation(String after) {
        var kept = documents();
        var keptLinks = linkDocuments();
        jdbc.queryForObject("select search_case_refresh(array(select id from legal_case))", Object.class);
        jdbc.queryForObject("select search_link_refresh(array(select id from case_link))", Object.class);
        assertThat(documents()).as("documents after: " + after).isEqualTo(kept);
        assertThat(linkDocuments()).as("link documents after: " + after).isEqualTo(keptLinks);
    }

    /**
     * V845 refreshes a document only when a searchable column changed. Each searchable column is changed on its own, to and
     * from NULL, and by multi-row statements that touch some rows and not others (QA review of #101).
     */
    @Test
    void everySearchableColumnToAndFromNullAndMultiRowUpdatesKeepTheDocumentsEqualToARecomputation() {
        UUID owner = user("Owner");
        UUID other = user("Other Member");
        UUID unit = authorityUnit("إدارة العقود");
        UUID unit2 = authorityUnit("إدارة الشؤون");
        UUID[] cases = {legalCase(owner, null), legalCase(owner, other), legalCase(owner, null)};
        for (UUID c : cases) {
            proceeding(c, "70" + (cases[0].equals(c) ? "1" : cases[1].equals(c) ? "2" : "3"));
            jdbc.update("insert into case_org_involvement (case_id, org_unit_id, is_primary, created_by, updated_by)"
                    + " values (?, ?, true, gen_random_uuid(), gen_random_uuid())", c, unit);
        }
        assertDocumentsEqualFullRecomputation("setup");

        String[][] caseColumns = {{"reference_no", "'LD-V845-NEW'"}, {"automated_no", "'V845AUTO'"}, {"title", "'عنوان v845'"},
                {"subject", "'موضوع v845'"}, {"facts", "'وقائع v845'"}, {"chamber", "'دائرة v845'"}};
        for (String[] column : caseColumns) {
            jdbc.update("update legal_case set " + column[0] + " = " + column[1] + " where id = ?", cases[0]);
            assertDocumentsEqualFullRecomputation(column[0] + " set");
        }
        // The nullable ones go to NULL and come back.
        for (String column : new String[] {"automated_no", "subject", "facts", "chamber"}) {
            jdbc.update("update legal_case set " + column + " = null where id = ?", cases[0]);
            assertDocumentsEqualFullRecomputation(column + " to null");
            jdbc.update("update legal_case set " + column + " = 'back-' || ? where id = ?", column, cases[0]);
            assertDocumentsEqualFullRecomputation(column + " from null");
        }
        jdbc.update("update legal_case set alternate_member_id = ? where id = ?", other, cases[0]);
        assertDocumentsEqualFullRecomputation("alternate member set");
        jdbc.update("update legal_case set alternate_member_id = null where id = ?", cases[0]);
        assertDocumentsEqualFullRecomputation("alternate member to null");
        jdbc.update("update legal_case set primary_member_id = ? where id = ?", other, cases[0]);
        assertDocumentsEqualFullRecomputation("primary member changed");

        // The involvement: department, section, contact, the unit and its removal, each alone and to NULL.
        jdbc.update("update case_org_involvement set department_name_snapshot = 'V845 dept' where case_id = ?", cases[1]);
        assertDocumentsEqualFullRecomputation("department snapshot");
        jdbc.update("update case_org_involvement set section_name_snapshot = 'V845 section' where case_id = ?", cases[1]);
        assertDocumentsEqualFullRecomputation("section snapshot");
        jdbc.update("update case_org_involvement set contact_name = 'V845 contact' where case_id = ?", cases[1]);
        assertDocumentsEqualFullRecomputation("contact name");
        jdbc.update("update case_org_involvement set contact_name = null, section_name_snapshot = null,"
                + " department_name_snapshot = null where case_id = ?", cases[1]);
        assertDocumentsEqualFullRecomputation("involvement texts to null");
        jdbc.update("update case_org_involvement set org_unit_id = ? where case_id = ?", unit2, cases[1]);
        assertDocumentsEqualFullRecomputation("unit changed");
        jdbc.update("update proceeding set court_case_number = '8801' where case_id = ?", cases[2]);
        assertDocumentsEqualFullRecomputation("court case number");
        jdbc.update("update proceeding set court_case_year = 2032 where case_id = ?", cases[2]);
        assertDocumentsEqualFullRecomputation("court case year");

        // Multi-row statements: one UPDATE that changes a searchable column on some rows and only a non-searchable one on others.
        jdbc.update("update legal_case set title = case when id = ? then 'bulk-changed' else title end, claimed_amount = 9", cases[0]);
        assertDocumentsEqualFullRecomputation("bulk case update, one row changes text");
        jdbc.update("update legal_case set subject = 'bulk subject ' || reference_no");
        assertDocumentsEqualFullRecomputation("bulk case update, every row changes text");
        jdbc.update("update proceeding set court_case_number = court_case_number || 'x'");
        assertDocumentsEqualFullRecomputation("bulk proceeding update");
        jdbc.update("update case_org_involvement set contact_name = 'bulk contact' where case_id <> ?", cases[2]);
        assertDocumentsEqualFullRecomputation("bulk involvement update");
        // A statement that changes nothing searchable leaves every document as it was.
        var before = documents();
        jdbc.update("update legal_case set status = status, claimed_amount = 1");
        jdbc.update("update proceeding set filing_date = filing_date");
        assertThat(documents()).isEqualTo(before);
        assertDocumentsEqualFullRecomputation("updates with nothing searchable");
    }

    @Test
    void theSubstringFilterOfTheCaseListFindsEverythingItDidBeforeTheTrigramPrefilter() throws Exception {
        UUID owner = user("Owner");
        UUID c = legalCase(owner, null);
        jdbc.update("update legal_case set title = 'Contract Dispute_100% about التوريد', subject = 'Delivery Delay', automated_no = 'AUTO-77' where id = ?", c);
        UUID party = party("Al-Noor Trading", "ORGANIZATION", null, false, null);
        onCase(c, party, "OPPONENT");
        proceeding(c, "88123");
        RequestPostProcessor staff = outsider(owner, Permission.CASE_VIEW);
        String reference = jdbc.queryForObject("select reference_no from legal_case where id = ?", String.class, c);
        for (String q : List.of("contract", "CONTRACT DISP", "ispute_1", "100%", "ونيد", "التور", "Delay", "livery", "AUTO-7", "noor tra",
                "88123", "8812", reference, reference.substring(2, 7), "wner")) {
            var result = mvc.perform(getJson(staff, "/api/v1/cases").param("q", q)).andReturn();
            boolean expected = jdbc.queryForObject("""
                    select exists (select 1 from legal_case c where c.id = ? and (c.reference_no ilike ? escape '\\' or c.title ilike ?
                    escape '\\' or c.subject ilike ? escape '\\' or c.automated_no ilike ? escape '\\'
                    or c.id in (select cp.case_id from case_party cp join party p on p.id = cp.party_id where p.name ilike ? escape '\\')
                    or c.id in (select pr.case_id from proceeding pr where pr.court_case_number ilike ? escape '\\')
                    or c.primary_member_id in (select m.id from app_user m where m.display_name ilike ? escape '\\')))""",
                    Boolean.class, c, like(q), like(q), like(q), like(q), like(q), like(q), like(q));
            int found = com.jayway.jsonpath.JsonPath.<List<String>>read(result.getResponse().getContentAsString(), "$.items[*].id").size();
            assertThat(found == 1).as("q=%s", q).isEqualTo(expected);
        }
    }

    @Test
    void aBroadFilterUsesTheHashedFormAndFindsTheSameCasesAsTheNarrowOne() throws Exception {
        UUID owner = user("Owner");
        // More candidates than Text.FEW_CANDIDATES, so the related tables are read once into hashed sets.
        jdbc.update("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, primary_member_id, created_by, updated_by)
                select 'BULK-' || g, 'bulkword case ' || g, 'CIVIL', '2026-01-10', 'OPEN', ?, ?, ?
                from generate_series(1, 3100) g""", owner, owner, owner);
        UUID viaParty = legalCase(owner, null);
        onCase(viaParty, party("Bulkword Trading Co", "ORGANIZATION", null, false, null), "OPPONENT");
        RequestPostProcessor staff = outsider(owner, Permission.CASE_VIEW);
        var result = mvc.perform(getJson(staff, "/api/v1/cases").param("q", "bulkword").param("size", "1")).andExpect(status().isOk())
                .andReturn();
        assertThat(this.<Integer>read(result, "$.totalItems")).as("3100 by title and one through its party").isEqualTo(3101);
    }

    @Test
    void theStoredCasePositionFollowsEveryWriteToProceedingsAndRanksStagesLikeTheLadder() throws Exception {
        UUID owner = user("Owner");
        UUID a = legalCase(owner, null);
        UUID b = legalCase(owner, null);
        legalCase(owner, null);
        UUID p1 = proceeding(a, "1");
        proceeding(a, "2");
        proceeding(a, "3");
        proceeding(b, "9");
        assertThat(jdbc.queryForList("select case_id from case_position")).hasSize(2);
        assertThat(jdbc.queryForObject("select proceeding_count from case_position where case_id = ?", Integer.class, a)).isEqualTo(3);
        jdbc.update("delete from proceeding where id = ?", p1);
        assertThat(jdbc.queryForObject("select proceeding_count from case_position where case_id = ?", Integer.class, a)).isEqualTo(2);
        jdbc.update("delete from proceeding where case_id = ?", b);
        assertThat(jdbc.queryForList("select case_id from case_position")).as("a case with no proceeding has no position").hasSize(1);
        // The ladder written into the trigger is the order of the Stage enum, which the reports use.
        String sql = new String(getClass().getResourceAsStream("/db/migration/V803__LD-058_case_position.sql").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder ladder = new StringBuilder("case p.stage");
        for (com.lawdesk.proceedings.Stage stage : com.lawdesk.proceedings.Stage.values()) {
            ladder.append(" when '").append(stage.name()).append("' then ").append(stage.ordinal());
        }
        assertThat(sql.replaceAll("\\s+", " ")).contains(ladder.toString() + " end desc");
    }

    private static String like(String q) {
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    @Test
    void aSearchNeedsAFixedNumberOfStatementsWhateverTheRegisterHolds() throws Exception {
        UUID owner = user("Owner");
        for (int i = 0; i < 12; i++) {
            UUID c = legalCase(owner, null);
            jdbc.update("update legal_case set title = ? where id = ?", "نزاع التوريد رقم " + i, c);
        }
        RequestPostProcessor me = outsider(owner, Permission.CASE_VIEW);
        SqlStatementCounter.reset();
        mvc.perform(getJson(me, "/api/v1/search").param("q", "نزاع التوريد")).andExpect(status().isOk());
        // normalise + (page, rows) for cases + (page) for parties: not one statement per record.
        assertThat(SqlStatementCounter.count()).isLessThanOrEqualTo(8);
        SqlStatementCounter.reset();
        mvc.perform(getJson(me, "/api/v1/cases").param("q", "التوريد")).andExpect(status().isOk());
        assertThat(SqlStatementCounter.count()).isLessThanOrEqualTo(3);
    }

    @Test
    void theIndexesAreUsableByTheQueriesTheApplicationSends() {
        List<String> plans = plans(
                "select x.case_id from search_case_doc x where x.vec_all @@ to_tsquery('simple', 'نزاع:*')",
                "select d.case_id from search_case_doc d where d.trigrams @> lawdesk_trigrams('contract')",
                "select x.id from party x where x.search_full @@ to_tsquery('simple', 'نور:*')",
                "select x.id from party x where x.search_public @@ to_tsquery('simple', 'نور:*')");
        assertThat(plans.get(0)).contains("search_case_doc_vec_all");
        assertThat(plans.get(1)).contains("search_case_doc_trigrams");
        assertThat(plans.get(2)).contains("party_search_full");
        assertThat(plans.get(3)).contains("party_search_public");
    }

    /** The plans of the statements with sequential scans disabled, on one connection (the setting is per session). */
    private List<String> plans(String... statements) {
        return jdbc.execute((java.sql.Connection connection) -> {
            List<String> out = new java.util.ArrayList<>();
            try (java.sql.Statement st = connection.createStatement()) {
                st.execute("set enable_seqscan = off");
                for (String sql : statements) {
                    StringBuilder plan = new StringBuilder();
                    try (java.sql.ResultSet rs = st.executeQuery("explain " + sql)) {
                        while (rs.next()) {
                            plan.append(rs.getString(1)).append('\n');
                        }
                    }
                    out.add(plan.toString());
                }
                st.execute("reset enable_seqscan");
            }
            return out;
        });
    }
}
