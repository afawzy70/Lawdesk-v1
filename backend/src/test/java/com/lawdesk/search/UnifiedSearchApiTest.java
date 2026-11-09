package com.lawdesk.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.Permission;
import com.lawdesk.security.TestActors;
import com.lawdesk.wp9.Wp9TestSupport;

/**
 * LD-057, requirements section 28 (LD-DOC-28-001..013): one search across cases, parties, hearings, judgments, documents,
 * notes, tasks, library judgments, settlements and directives, in Arabic-normalised PostgreSQL full text, grouped by type
 * and ranked. Search is a way to read, so every result is restricted to the cases the user may see, the party's identity
 * numbers follow PARTY_ID_VIEW, and a total never counts what is hidden.
 */
class UnifiedSearchApiTest extends Wp9TestSupport {

    private static final Permission[] ALL_TYPES = {Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.HEARING_VIEW,
        Permission.JUDGMENT_VIEW, Permission.DOCUMENT_VIEW, Permission.TASK_VIEW, Permission.LIBRARY_VIEW};

    @org.springframework.beans.factory.annotation.Autowired
    CaseAccessPolicy access;

    private UUID owner;
    private UUID stranger;
    private UUID visible;
    private UUID hidden;
    private RequestPostProcessor searcher;

    @BeforeEach
    void twoCasesWithTheSameWordsInEveryRecordType() {
        owner = user("Owner");
        stranger = user("Stranger");
        visible = legalCase(owner, null);
        hidden = legalCase(stranger, null);
        jdbc.update("update legal_case set title = 'نزاع حول عقد التوريد', subject = 'مطالبة بالتعويض عن التأخير', facts = 'تأخر المورد في التسليم' where id = ?", visible);
        jdbc.update("update legal_case set title = 'نزاع حول عقد التوريد الثاني', subject = 'مطالبة بالتعويض عن التأخير', facts = 'تأخر المورد في التسليم' where id = ?", hidden);
        for (UUID c : List.of(visible, hidden)) {
            UUID proceeding = proceeding(c, "77" + (c.equals(visible) ? "1" : "2"));
            hearing(c, proceeding, "2026-03-02", "استمعت المحكمة إلى دفاع المورد حول التوريد", "أجلت للمستندات");
            judgment(proceeding, "قضت المحكمة بالتعويض عن التأخير في التوريد");
            document(c, "عقد التوريد الموقع", "contract-" + c.toString().substring(0, 4) + ".pdf");
            note(c, "ملاحظة: يلزم مراجعة شروط التوريد");
            task(c, "إعداد مذكرة التوريد", "مراجعة العقد", c.equals(visible) ? owner : stranger);
            onCase(c, party(c.equals(visible) ? "شركة النور للتوريد" : "شركة الظلال للتوريد", "ORGANIZATION", null, false, null), "OPPONENT");
            jdbc.update("""
                    insert into settlement (case_id, kind, legal_grounds, created_by, updated_by)
                    values (?, 'SETTLEMENT', 'تفادي نزاع التوريد الطويل', gen_random_uuid(), gen_random_uuid())""", c);
            jdbc.update("""
                    insert into directive (directive_no, case_id, title, directive_date, source, addressee_text, created_by, updated_by)
                    values (?, ?, 'متابعة توريد المستندات', current_date, 'مدير الإدارة', 'العضو المختص', gen_random_uuid(), gen_random_uuid())""",
                    "DIR-2026-9" + (c.equals(visible) ? "1" : "2"), c);
        }
        libraryJudgment("حكم في عقد التوريد وأثر التأخير", "قررت المحكمة التعويض");
        searcher = outsider(owner, ALL_TYPES);
    }

    private MvcResult search(RequestPostProcessor who, String query) throws Exception {
        return mvc.perform(getJson(who, "/api/v1/search?" + query)).andExpect(status().isOk()).andReturn();
    }

    // ---- LD-DOC-28-001..012: what is searched ---------------------------------------------------------------

    @Test
    void oneQueryFindsEveryTypeGroupedByTypeWithTheTotalOfWhatTheUserMaySee() throws Exception {
        MvcResult result = search(searcher, "q=" + "التوريد");
        // Visible: 1 case, 1 party, 1 hearing, 1 judgment, 1 document, 1 note, 1 task, 1 settlement, 1 directive, 1 library judgment.
        assertThat(this.<Integer>read(result, "$.total")).isEqualTo(10);
        assertThat(this.<List<String>>read(result, "$.groups[*].type")).containsExactly("CASE", "PARTY", "HEARING", "JUDGMENT",
                "DOCUMENT", "NOTE", "TASK", "LIBRARY", "SETTLEMENT", "DIRECTIVE");
        assertThat(this.<List<Integer>>read(result, "$.groups[*].total")).containsOnly(1);
        assertThat(this.<List<String>>read(result, "$.searchedTypes")).hasSize(10);
        assertThat(this.<String>read(result, "$.groups[0].items[0].caseId")).isEqualTo(visible.toString());
    }

    @Test
    void caseNumbersPartyNamesSubjectsAndRequestTextAreFound() throws Exception {
        String reference = jdbc.queryForObject("select reference_no from legal_case where id = ?", String.class, visible);
        MvcResult byNumber = search(searcher, "q=" + reference + "&types=CASE");
        assertThat(this.<List<String>>read(byNumber, "$.groups[0].items[*].caseId")).containsExactly(visible.toString());
        assertThat(this.<List<String>>read(byNumber, "$.groups[0].items[0].matchedIn")).contains("REFERENCE");
        MvcResult byCourtNumber = search(searcher, "q=771&types=CASE");
        assertThat(this.<List<String>>read(byCourtNumber, "$.groups[0].items[0].matchedIn")).contains("NUMBERS");
        MvcResult byParty = search(searcher, "q=" + "شركة النور");
        assertThat(this.<List<String>>read(byParty, "$.groups[?(@.type == 'PARTY')].items[0].title")).containsExactly("شركة النور للتوريد");
        MvcResult bySubject = search(searcher, "q=" + "التعويض عن التأخير&types=CASE");
        assertThat(this.<List<String>>read(bySubject, "$.groups[0].items[0].matchedIn")).contains("SUBJECT");
    }

    @Test
    void theAuthorityUnitsAndTheLegalMembersOfACaseAreSearchable() throws Exception {
        UUID unit = authorityUnit("إدارة العقود والمشتريات");
        jdbc.update("insert into case_org_involvement (case_id, org_unit_id, is_primary, created_by, updated_by) values (?, ?, true,"
                + " gen_random_uuid(), gen_random_uuid())", visible, unit);
        MvcResult byUnit = search(searcher, "q=" + "العقود والمشتريات&types=CASE");
        assertThat(this.<List<String>>read(byUnit, "$.groups[0].items[0].matchedIn")).contains("UNITS");
        MvcResult byMember = search(searcher, "q=Owner&types=CASE");
        assertThat(this.<List<String>>read(byMember, "$.groups[0].items[0].matchedIn")).contains("MEMBERS");
    }

    @Test
    void aLinkedCaseIsFoundThroughTheCaseItIsLinkedToOnlyWhenThatCaseIsVisible() throws Exception {
        UUID second = legalCase(owner, null);
        jdbc.update("update legal_case set title = 'قضية الاستئناف المرتبطة' where id = ?", second);
        jdbc.update("""
                insert into case_link (case_id, other_case_id, kind, reason, created_by, updated_by)
                values (?, ?, 'SUBJECT_LINK', 'نفس العقد', gen_random_uuid(), gen_random_uuid())""", visible, second);
        MvcResult found = search(searcher, "q=" + "الاستئناف المرتبطة&types=CASE");
        assertThat(this.<List<String>>read(found, "$.groups[*].items[*].caseId")).contains(visible.toString(), second.toString());
        // The same link to a case the user cannot see adds nothing: the hidden case's words do not find this one.
        jdbc.update("update legal_case set title = 'قضية سرية المرتبطة' where id = ?", hidden);
        jdbc.update("""
                insert into case_link (case_id, other_case_id, kind, reason, created_by, updated_by)
                values (?, ?, 'SUBJECT_LINK', 'عقد آخر', gen_random_uuid(), gen_random_uuid())""", visible, hidden);
        MvcResult none = search(searcher, "q=" + "سرية المرتبطة");
        assertThat(this.<Integer>read(none, "$.total")).isZero();
    }

    @Test
    void hearingMinutesJudgmentOperativeTextDocumentsAndNotesAreFoundByTheirWords() throws Exception {
        MvcResult result = search(searcher, "q=" + "دفاع المورد");
        assertThat(this.<List<String>>read(result, "$.groups[*].type")).containsExactly("HEARING");
        assertThat(this.<String>read(result, "$.groups[0].items[0].snippet")).contains("دفاع المورد");
        MvcResult judgment = search(searcher, "q=" + "قضت بالتعويض".replace("بالتعويض", "المحكمة"));
        assertThat(this.<List<String>>read(judgment, "$.groups[?(@.type == 'JUDGMENT')].type")).isNotEmpty();
        MvcResult document = search(searcher, "q=contract&types=DOCUMENT");
        assertThat(this.<List<String>>read(document, "$.groups[0].items[0].matchedIn")).contains("FILE_NAME");
        MvcResult note = search(searcher, "q=" + "شروط التوريد&types=NOTE");
        assertThat(this.<Integer>read(note, "$.total")).isEqualTo(1);
    }

    // ---- Arabic normalisation (WP7 functions, not a fork) ---------------------------------------------------

    @Test
    void spellingVariantsOfHamzaTaaMarbutaAndDiacriticsFindTheSameRecords() throws Exception {
        jdbc.update("update legal_case set subject = 'مسؤولية الإدارة عن الأضرار' where id = ?", visible);
        for (String variant : new String[] {"مسئولية", "مسولية", "مَسْؤُولِيَّةِ", "المسؤوليه", "ادارة الاضرار"}) {
            MvcResult result = search(searcher, "q=" + variant + "&types=CASE");
            assertThat(this.<Integer>read(result, "$.total")).as(variant).isEqualTo(1);
        }
    }

    @Test
    void everyWordMustMatchAndAWordBeginningMatchesItsLongerForms() throws Exception {
        assertThat(this.<Integer>read(search(searcher, "q=" + "عقد التوريد برمجيات&types=CASE"), "$.total")).isZero();
        assertThat(this.<Integer>read(search(searcher, "q=" + "التور&types=CASE"), "$.total")).isEqualTo(1);
    }

    @Test
    void theSnippetIsCutFromTheOriginalTextAroundTheMatch() throws Exception {
        jdbc.update("update legal_case set facts = ? where id = ?",
                "بدأت الوقائع في بداية العام وتسلسلت الأحداث حتى وصلت إلى لحظة الإخلال الجسيم بالتزامات التوريد من المورد وتبعه نزاع طويل ويستمر الخلاف في جوانب أخرى كثيرة لا حصر لها ولا نهاية لتعدادها بين الطرفين المتنازعين في هذه الدعوى", visible);
        MvcResult result = search(searcher, "q=" + "الإخلال&types=CASE");
        String snippet = read(result, "$.groups[0].items[0].snippet");
        assertThat(snippet).contains("الإخلال الجسيم").startsWith("…").endsWith("…").doesNotContain("بدأت الوقائع");
        assertThat(this.<List<String>>read(result, "$.groups[0].items[0].matchedIn")).containsExactly("FACTS");
    }

    @Test
    void aMatchInTheTitleOutranksAMatchInTheFacts() throws Exception {
        UUID facts = legalCase(owner, null);
        jdbc.update("update legal_case set title = 'قضية أخرى', facts = 'ورد ذكر الكفالة المصرفية هنا' where id = ?", facts);
        UUID title = legalCase(owner, null);
        jdbc.update("update legal_case set title = 'الكفالة المصرفية' where id = ?", title);
        MvcResult result = search(searcher, "q=" + "الكفالة المصرفية&types=CASE");
        assertThat(this.<List<String>>read(result, "$.groups[0].items[*].caseId")).containsExactly(title.toString(), facts.toString());
        assertThat(this.<Double>read(result, "$.groups[0].items[0].rank")).isGreaterThan(this.<Double>read(result, "$.groups[0].items[1].rank"));
    }

    // ---- search is a leak vector: D3 --------------------------------------------------------------------------

    @Test
    void aUserWhoSeesNothingFindsNothingAndTheTotalIsZeroInEveryType() throws Exception {
        UUID nobody = user("Nobody");
        // The library is department-wide reference data, so this user holds no library right; every case-bound type is empty.
        RequestPostProcessor outsider = outsider(nobody, Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.HEARING_VIEW,
                Permission.JUDGMENT_VIEW, Permission.DOCUMENT_VIEW, Permission.TASK_VIEW);
        for (String q : new String[] {"التوريد", "شركة", "دفاع المورد", "contract", "التعويض", "DIR-2026", "الإدارة"}) {
            MvcResult result = search(outsider, "q=" + q);
            assertThat(this.<Integer>read(result, "$.total")).as(q).isZero();
            assertThat(this.<List<Object>>read(result, "$.groups")).as(q).isEmpty();
            assertThat(body(result)).doesNotContain(visible.toString()).doesNotContain(hidden.toString());
        }
    }

    @Test
    void theTotalNeverCountsWhatTheUserCannotSeeAndNoSnippetComesFromAHiddenRecord() throws Exception {
        MvcResult result = search(searcher, "q=" + "التوريد");
        assertThat(body(result)).doesNotContain(hidden.toString()).doesNotContain("الظلال").doesNotContain("الثاني");
        // Granting access to the second case adds exactly its records.
        jdbc.update("insert into case_grant (case_id, user_id, level, reason, granted_by) values (?, ?, 'VIEW', 'اختبار', ?)", hidden,
                owner, owner);
        MvcResult both = search(searcher, "q=" + "التوريد");
        assertThat(this.<Integer>read(both, "$.total")).isEqualTo(19);
        assertThat(body(both)).contains("الظلال");
    }

    @Test
    void aPartyOnlyOnAHiddenCaseIsNotFoundAndTheLibraryNeedsItsOwnPermission() throws Exception {
        MvcResult byHiddenParty = search(searcher, "q=" + "الظلال");
        assertThat(this.<Integer>read(byHiddenParty, "$.total")).isZero();
        RequestPostProcessor noLibrary = outsider(owner, Permission.CASE_VIEW, Permission.SEARCH_ALL);
        MvcResult result = search(noLibrary, "q=" + "التوريد");
        assertThat(this.<List<String>>read(result, "$.groups[*].type")).doesNotContain("LIBRARY", "HEARING", "DOCUMENT", "TASK", "JUDGMENT");
        assertThat(this.<List<String>>read(result, "$.searchedTypes")).containsExactlyInAnyOrder("CASE", "PARTY", "NOTE", "SETTLEMENT",
                "DIRECTIVE");
    }

    @Test
    void searchingEveryTypeNeedsSearchAllAndOtherwiseOnlyCasesAndPartiesAreSearched() throws Exception {
        RequestPostProcessor casesOnly = outsider(owner, Permission.CASE_VIEW, Permission.HEARING_VIEW, Permission.DOCUMENT_VIEW);
        MvcResult result = search(casesOnly, "q=" + "التوريد");
        assertThat(this.<List<String>>read(result, "$.searchedTypes")).containsExactlyInAnyOrder("CASE", "PARTY");
        assertThat(this.<Integer>read(result, "$.total")).isEqualTo(2);
        mvc.perform(getJson(staff(owner), "/api/v1/search?q=x")).andExpect(status().isForbidden());
    }

    @Test
    void aTaskWithoutACaseIsFoundByItsAssigneeOnly() throws Exception {
        jdbc.update("insert into task (title, assignee_id, created_by, updated_by) values ('تذكير شخصي بمراجعة الكشوف', ?, ?, ?)", owner,
                owner, owner);
        assertThat(this.<Integer>read(search(searcher, "q=" + "تذكير شخصي&types=TASK"), "$.total")).isEqualTo(1);
        RequestPostProcessor other = outsider(stranger, ALL_TYPES);
        assertThat(this.<Integer>read(search(other, "q=" + "تذكير شخصي&types=TASK"), "$.total")).isZero();
    }

    // ---- D4: party identity masking ---------------------------------------------------------------------------

    @Test
    void thePartysIdentityNumberAndConfidentialContactsAreNotSearchableWithoutPartyIdView() throws Exception {
        UUID p = party("خالد الاختبار", "PERSON", "290010100123", true, "99887766");
        onCase(visible, p, "CLIENT");
        RequestPostProcessor masked = outsider(owner, Permission.CASE_VIEW);
        RequestPostProcessor trusted = outsider(owner, Permission.CASE_VIEW, Permission.PARTY_ID_VIEW);
        assertThat(this.<Integer>read(search(masked, "q=290010100123&types=PARTY"), "$.total")).isZero();
        assertThat(this.<Integer>read(search(masked, "q=99887766&types=PARTY"), "$.total")).isZero();
        assertThat(this.<Integer>read(search(masked, "q=" + "خالد الاختبار&types=PARTY"), "$.total")).isEqualTo(1);
        MvcResult found = search(trusted, "q=290010100123&types=PARTY");
        assertThat(this.<Integer>read(found, "$.total")).isEqualTo(1);
        assertThat(this.<List<String>>read(found, "$.groups[0].items[0].matchedIn")).containsExactly("IDENTIFIER");
        assertThat(this.<Integer>read(search(trusted, "q=99887766&types=PARTY"), "$.total")).isEqualTo(1);
        // The masked user's results never carry the number, even for the name match.
        assertThat(body(search(masked, "q=" + "خالد الاختبار&types=PARTY"))).doesNotContain("290010100123").doesNotContain("99887766");
    }

    // ---- requests, defences and the operative part (WP2 records) ---------------------------------------------------

    @Test
    void requestsDefencesAndTheOperativePartAreSearchedThroughVisibleCasesOnly() throws Exception {
        for (UUID c : List.of(visible, hidden)) {
            jdbc.update("""
                    insert into claim (case_id, seq, text, kind, created_by, updated_by)
                    values (?, 1, 'إلزام المورد بسداد غرامة التأخير المستحقة', 'PRIMARY', gen_random_uuid(), gen_random_uuid())""", c);
            jdbc.update("""
                    insert into defense (case_id, seq, title, kind, argument, created_by, updated_by)
                    values (?, 1, 'الدفع: عدم قبول الدعوى', 'FORMAL', 'لرفعها بغير الطريق القانوني', gen_random_uuid(), gen_random_uuid())""", c);
        }
        jdbc.update("update judgment set operative_text = 'ألزمت المحكمة المدعى عليه بالمصروفات' where proceeding_id in"
                + " (select id from proceeding where case_id = ?)", visible);
        RequestPostProcessor reader = outsider(owner, Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.CLAIM_VIEW,
                Permission.DEFENSE_VIEW, Permission.JUDGMENT_VIEW);
        MvcResult claims = search(reader, "q=" + "غرامة التأخير");
        assertThat(this.<List<String>>read(claims, "$.groups[?(@.type == 'CLAIM')].items[*].caseId")).containsExactly(visible.toString());
        assertThat(this.<List<Integer>>read(claims, "$.groups[?(@.type == 'CLAIM')].total")).containsExactly(1);
        MvcResult defences = search(reader, "q=" + "عدم قبول الدعوى&types=DEFENSE");
        assertThat(this.<Integer>read(defences, "$.total")).isEqualTo(1);
        assertThat(this.<List<String>>read(defences, "$.groups[0].items[0].matchedIn")).contains("TITLE");
        MvcResult operative = search(reader, "q=" + "بالمصروفات&types=JUDGMENT");
        assertThat(this.<List<String>>read(operative, "$.groups[0].items[0].matchedIn")).containsExactly("OPERATIVE");
        // Without the view permission of the type the group is not searched at all.
        MvcResult none = search(outsider(owner, Permission.CASE_VIEW, Permission.SEARCH_ALL), "q=" + "غرامة التأخير");
        assertThat(this.<List<String>>read(none, "$.searchedTypes")).doesNotContain("CLAIM", "DEFENSE");
        // A user who sees no case finds none of them.
        RequestPostProcessor nobody = outsider(user("Nobody"), Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.CLAIM_VIEW,
                Permission.DEFENSE_VIEW);
        assertThat(this.<Integer>read(search(nobody, "q=" + "غرامة التأخير"), "$.total")).isZero();
    }

    // ---- filters and paging -------------------------------------------------------------------------------------

    @Test
    void advancedFiltersNarrowByTypeCaseTypeCaseAndDate() throws Exception {
        jdbc.update("update legal_case set case_type = 'LABOR' where id = ?", visible);
        assertThat(this.<Integer>read(search(searcher, "q=" + "التوريد&caseType=LABOR"), "$.total")).isGreaterThan(1);
        assertThat(this.<Integer>read(search(searcher, "q=" + "التوريد&caseType=ADMINISTRATIVE"), "$.total")).isZero();
        MvcResult one = search(searcher, "q=" + "التوريد&caseId=" + visible);
        assertThat(this.<List<String>>read(one, "$.groups[*].type")).doesNotContain("LIBRARY");
        MvcResult dates = search(searcher, "q=" + "التوريد&types=HEARING&from=2026-03-01&to=2026-03-31");
        assertThat(this.<Integer>read(dates, "$.total")).isEqualTo(1);
        assertThat(this.<Integer>read(search(searcher, "q=" + "التوريد&types=HEARING&from=2026-04-01"), "$.total")).isZero();
        assertThat(this.<List<String>>read(search(searcher, "q=" + "التوريد&types=HEARING,NOTE"), "$.searchedTypes")).containsExactly("HEARING", "NOTE");
    }

    @Test
    void eachGroupIsPagedAndTheGroupTotalIsTheCountOfEverythingVisible() throws Exception {
        for (int i = 0; i < 7; i++) {
            UUID c = legalCase(owner, null);
            jdbc.update("update legal_case set title = ? where id = ?", "توريد رقم " + i, c);
        }
        MvcResult first = search(searcher, "q=" + "توريد&types=CASE&size=5");
        assertThat(this.<Integer>read(first, "$.groups[0].total")).isEqualTo(8);
        assertThat(this.<List<Object>>read(first, "$.groups[0].items")).hasSize(5);
        MvcResult second = search(searcher, "q=" + "توريد&types=CASE&size=5&page=1");
        assertThat(this.<List<Object>>read(second, "$.groups[0].items")).hasSize(3);
        assertThat(this.<Integer>read(second, "$.groups[0].page")).isEqualTo(1);
        assertThat(this.<Integer>read(second, "$.total")).isEqualTo(8);
    }

    @Test
    void theRequestIsValidated() throws Exception {
        mvc.perform(getJson(searcher, "/api/v1/search")).andExpect(status().isBadRequest());
        mvc.perform(getJson(searcher, "/api/v1/search").param("q", "   ")).andExpect(status().isBadRequest());
        mvc.perform(getJson(searcher, "/api/v1/search?q=" + "x".repeat(201))).andExpect(status().isBadRequest());
        mvc.perform(getJson(searcher, "/api/v1/search?q=a&types=NOPE")).andExpect(status().isBadRequest());
        mvc.perform(getJson(searcher, "/api/v1/search?q=a&size=101")).andExpect(status().isBadRequest());
        mvc.perform(getJson(searcher, "/api/v1/search?q=a&page=-1")).andExpect(status().isBadRequest());
        mvc.perform(getJson(searcher, "/api/v1/search?q=a&from=bad")).andExpect(status().isBadRequest());
        // Words that normalise to nothing find nothing, they do not fail.
        mvc.perform(getJson(searcher, "/api/v1/search?q=***")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void queryTextCannotInjectSqlOrQueryOperators() throws Exception {
        for (String q : new String[] {"'; drop table legal_case; --", "a & b | !c", "(التوريد", "التوريد:*", "\\", "%27"}) {
            mvc.perform(getJson(searcher, "/api/v1/search").param("q", q)).andExpect(status().isOk());
        }
        assertThat(count("select count(*) from legal_case")).isEqualTo(2);
    }

    /**
     * Guard against the regression of UAT 3937a50: a part added to a searched type that its stored vector does not hold makes a record
     * found only through that part invisible to the search. For every type read from a stored vector (cases and parties are covered by
     * StoredSearchDocumentsTest), the vector built from the spec's own parts must equal the stored one for every row.
     */
    @Test
    void everyPartOfASearchedTypeIsInItsStoredVector() {
        UUID doc = document(visible, "وثيقة", "scan.png");
        jdbc.update("""
                insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256, uploaded_by)
                values (?, ?, 1, 'scan.png', 'image/png', 10, repeat('a', 64), gen_random_uuid())""", doc, doc);
        jdbc.update("insert into document_text (version_id, document_id, status, method, text) values (?, ?, 'OCR_DONE', 'OCR', ?)",
                doc, doc, "نص مقروء بالتعرف الضوئي");
        TestActors.signIn(owner);
        for (SearchSpecs.Spec spec : SearchSpecs.all(access, true).values()) {
            if (spec.stored() == null || spec.stored().with() != null || "PARTY".equals(spec.type()) || "CASE".equals(spec.type())) {
                continue;
            }
            StringBuilder built = new StringBuilder();
            for (SearchSpecs.Part part : spec.parts()) {
                built.append(built.length() == 0 ? "" : " || ").append("setweight(to_tsvector('simple', library_normalize(")
                        .append(part.sql()).append(")), '").append(part.weight()).append("')");
            }
            String from = spec.from().replace("%CASE_FILTERS%", "");
            int rows = count("select count(*) from " + from);
            int wrong = count("select count(*) from " + from + " where (" + built + ")::text is distinct from "
                    + spec.stored().vector() + "::text");
            if ("DOCUMENT".equals(spec.type())) {
                assertThat(rows).as("fixture rows of DOCUMENT").isPositive();
            }
            assertThat(wrong).as("rows of %s whose stored vector differs from its parts", spec.type()).isZero();
        }
    }
}
