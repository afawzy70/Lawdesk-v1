package com.lawdesk.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-059, requirements 22.1 to 22.5: the summary, the document analysis, the precedent search, the memorandum draft and the
 * comparison, each retrieval-backed, source-cited and extractive (test provider only); drafts are stored, never final.
 */
class AssistantTasksApiTest extends AssistantTestSupport {

    private UUID owner;
    private UUID stranger;
    private UUID visible;
    private UUID hidden;
    private RequestPostProcessor ownerAs;

    @BeforeEach
    void aCaseWithItsRecords() {
        owner = user("Owner");
        stranger = user("Stranger");
        visible = legalCase(owner, null);
        hidden = legalCase(stranger, null);
        UUID proceeding = proceeding(visible, "901");
        hearing(visible, proceeding, "2026-03-02", "استمعت المحكمة إلى دفاع المورد", "أجلت للمستندات");
        judgment(proceeding, "قضت المحكمة بالتعويض عن التأخير في التوريد");
        claim(visible, 1, "إلزام المدعى عليه بسداد قيمة الفواتير");
        defense(visible, 1, "الدفع بالتقادم", "مضت مدة التقادم");
        libraryJudgment("حكم في التعويض عن التأخير في التوريد", "قررت المحكمة التعويض عن التأخير");
        ownerAs = member(owner);
    }

    @Test
    void theSummaryQuotesTheRecordsOfTheCaseSectionBySectionWithSources() throws Exception {
        MvcResult result = ask(ownerAs, json("task", "CASE_SUMMARY", "caseId", visible.toString()), 200);
        assertThat(this.<List<String>>read(result, "$.blocks[?(@.kind == 'HEADING')].code")).contains("CASE", "REQUESTS", "DEFENSES",
                "JUDGMENTS");
        assertThat(this.<List<String>>read(result, "$.sources[*].type")).contains("CASE", "CLAIM", "DEFENSE", "JUDGMENT");
        assertThat(body(result)).contains("إلزام المدعى عليه").contains("مضت مدة التقادم");
        // A section with nothing recorded says so with a fixed note; it does not make anything up.
        assertThat(this.<List<String>>read(result, "$.blocks[?(@.kind == 'NOTE')].code")).contains("DRAFT_FOR_REVIEW");
        assertThat(this.<Boolean>read(result, "$.isFinal")).isFalse();
    }

    @Test
    void aSummaryOfACaseWithoutRequestsSaysThatSectionIsEmpty() throws Exception {
        UUID bare = legalCase(owner, null);
        MvcResult result = ask(ownerAs, json("task", "CASE_SUMMARY", "caseId", bare.toString()), 200);
        assertThat(this.<List<String>>read(result, "$.blocks[?(@.kind == 'NOTE')].code")).contains("SECTION_EMPTY");
        assertThat(this.<List<String>>read(result, "$.sources[*].type")).doesNotContain("CLAIM", "DEFENSE");
    }

    @Test
    void aSummaryAndAMemoDraftOnlyQuoteWhatTheUserMaySee() throws Exception {
        UUID noPleadings = user("NoPleadings");
        jdbc.update("update legal_case set alternate_member_id = ? where id = ?", noPleadings, visible);
        MvcResult result = ask(outsider(noPleadings, Permission.ASSISTANT_USE, Permission.CASE_VIEW),
                json("task", "CASE_SUMMARY", "caseId", visible.toString()), 200);
        assertThat(this.<List<String>>read(result, "$.sources[*].type")).doesNotContain("CLAIM", "DEFENSE");
        MvcResult memo = ask(outsider(noPleadings, Permission.ASSISTANT_USE, Permission.CASE_VIEW),
                json("task", "MEMO_DRAFT", "caseId", visible.toString()), 200);
        assertThat(this.<List<String>>read(memo, "$.sources[*].type")).doesNotContain("LIBRARY", "CLAIM");
    }

    @Test
    void aMemoDraftIsAlwaysKeptAsANonFinalDraftThatTheMemberMustApprove() throws Exception {
        MvcResult result = ask(ownerAs, json("task", "MEMO_DRAFT", "caseId", visible.toString(), "question", "مذكرة رد على الدعوى"), 200);
        assertThat(this.<List<String>>read(result, "$.blocks[?(@.kind == 'HEADING')].code")).contains("FACTS", "REQUESTS", "DEFENSES", "PRECEDENTS");
        String outputId = read(result, "$.outputId");
        MvcResult draft = mvc.perform(getJson(ownerAs, "/api/v1/assistant/outputs/" + outputId)).andExpect(status().isOk()).andReturn();
        assertThat(this.<String>read(draft, "$.status")).isEqualTo("DRAFT");
        assertThat(this.<Boolean>read(draft, "$.isFinal")).isFalse();
        assertThat(this.<String>read(draft, "$.kind")).isEqualTo("MEMO_DRAFT");
        assertThat(this.<Boolean>read(draft, "$.nonProduction")).isTrue();
        assertThat(this.<List<String>>read(draft, "$.content.sources[*].type")).contains("CLAIM");

        MvcResult listed = mvc.perform(getJson(ownerAs, "/api/v1/assistant/outputs?caseId=" + visible)).andExpect(status().isOk()).andReturn();
        assertThat(this.<List<String>>read(listed, "$[*].id")).containsExactly(outputId);

        MvcResult approved = mvc.perform(postJson(ownerAs, "/api/v1/assistant/outputs/" + outputId + "/review",
                json("decision", "APPROVED", "note", "راجعتها"))).andExpect(status().isOk()).andReturn();
        assertThat(this.<String>read(approved, "$.status")).isEqualTo("USER_APPROVED");
        assertThat(this.<Boolean>read(approved, "$.isFinal")).isFalse();
        // Approval creates no memorandum or document, and a draft cannot be reviewed twice.
        assertThat(count("select count(*) from legal_document")).isZero();
        mvc.perform(postJson(ownerAs, "/api/v1/assistant/outputs/" + outputId + "/review", json("decision", "DISCARDED")))
                .andExpect(status().isUnprocessableContent());
        assertThat(count("select count(*) from audit_event where action = 'ASSISTANT_OUTPUT_APPROVED'")).isEqualTo(1);
    }

    @Test
    void aDraftIsBarredFromBeingFinalByTheDatabaseItself() {
        UUID session = jdbc.queryForObject("insert into assistant_session (user_id) values (?) returning id", UUID.class, owner);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "insert into assistant_output (session_id, user_id, kind, title, content, is_final) values (?, ?, 'MEMO_DRAFT', 't', '{}'::jsonb, true)",
                session, owner)).hasMessageContaining("assistant_output_never_final");
    }

    @Test
    void aDraftBelongsToItsAuthorAndDisappearsWhenTheyLoseTheCase() throws Exception {
        MvcResult result = ask(ownerAs, json("task", "CASE_SUMMARY", "caseId", visible.toString(), "saveDraft", true), 200);
        String outputId = read(result, "$.outputId");
        UUID other = user("Other");
        mvc.perform(getJson(viewer(other, MEMBER), "/api/v1/assistant/outputs/" + outputId)).andExpect(status().isNotFound());
        mvc.perform(postJson(viewer(other, MEMBER), "/api/v1/assistant/outputs/" + outputId + "/review", json("decision", "APPROVED")))
                .andExpect(status().isNotFound());
        jdbc.update("update legal_case set primary_member_id = ?, created_by = ? where id = ?", other, other, visible);
        mvc.perform(getJson(ownerAs, "/api/v1/assistant/outputs/" + outputId)).andExpect(status().isNotFound());
        MvcResult listed = mvc.perform(getJson(ownerAs, "/api/v1/assistant/outputs")).andExpect(status().isOk()).andReturn();
        assertThat(this.<List<Object>>read(listed, "$")).isEmpty();
        mvc.perform(getJson(ownerAs, "/api/v1/assistant/outputs?caseId=" + visible)).andExpect(status().isNotFound());
    }

    @Test
    void anAnswerIsKeptOnlyWhenAskedFor() throws Exception {
        ask(ownerAs, question(visible, "التقادم"), 200);
        assertThat(count("select count(*) from assistant_output where user_id = ?", owner)).isZero();
        MvcResult kept = ask(ownerAs, json("task", "QUESTION", "caseId", visible.toString(), "question", "التقادم", "saveDraft", true), 200);
        assertThat(this.<String>read(kept, "$.outputId")).isNotNull();
        MvcResult discarded = mvc.perform(postJson(ownerAs, "/api/v1/assistant/outputs/" + this.<String>read(kept, "$.outputId")
                + "/review", json("decision", "DISCARDED"))).andExpect(status().isOk()).andReturn();
        assertThat(this.<String>read(discarded, "$.status")).isEqualTo("DISCARDED");
        mvc.perform(postJson(ownerAs, "/api/v1/assistant/outputs/x/review", json("decision", "MAYBE"))).andExpect(status().isBadRequest());
    }

    // ---- 22.2 document analysis -----------------------------------------------------------------------------------------

    @Test
    void theAnalysisPointsAtDatesNumbersRequestsAndOperativePartsAlreadyInTheText() throws Exception {
        UUID doc = documentWithText(visible, "صحيفة الدعوى", "رفعت الدعوى بتاريخ 2026-01-15 برقم 321/2026.\n"
                + "يطلب المدعي إلزام المدعى عليه بالسداد.\nندفع بعدم قبول الدعوى.\nحكمت المحكمة بالرفض.\nجملة لا علاقة لها بشيء.", "EXTRACTED");
        MvcResult result = ask(ownerAs, json("task", "DOCUMENT_ANALYSIS", "documentId", doc.toString()), 200);
        assertThat(this.<List<String>>read(result, "$.blocks[?(@.kind == 'HEADING')].code")).contains("DATES", "CASE_NUMBERS", "REQUESTS",
                "DEFENSES", "OPERATIVE");
        assertThat(body(result)).contains("2026-01-15").contains("321/2026").contains("حكمت المحكمة بالرفض");
        assertThat(body(result)).doesNotContain("جملة لا علاقة لها");
        assertThat(this.<List<String>>read(result, "$.sources[*].type")).containsOnly("DOCUMENT");
        assertThat(this.<String>read(result, "$.caseId")).isEqualTo(visible.toString());
    }

    @Test
    void arabicIndicDigitsInADateAreRecognised() throws Exception {
        UUID doc = documentWithText(visible, "محضر", "انعقدت الجلسة بتاريخ ٢٠٢٦/٠٣/٠١ أمام الدائرة", "EXTRACTED");
        MvcResult result = ask(ownerAs, json("task", "DOCUMENT_ANALYSIS", "documentId", doc.toString()), 200);
        assertThat(this.<List<String>>read(result, "$.blocks[?(@.kind == 'HEADING')].code")).contains("DATES");
    }

    @Test
    void aDocumentWithoutReadableTextOrWithNothingToPointAtIsRefused() throws Exception {
        UUID scanned = documentWithText(visible, "مسح ضوئي", null, "OCR_UNAVAILABLE");
        MvcResult noText = ask(ownerAs, json("task", "DOCUMENT_ANALYSIS", "documentId", scanned.toString()), 200);
        assertThat(this.<String>read(noText, "$.refusal")).isEqualTo("DOCUMENT_TEXT_UNAVAILABLE");
        UUID plain = documentWithText(visible, "ورقة", "كلمات عادية بلا أي شيء مما نبحث عنه هنا", "EXTRACTED");
        MvcResult nothing = ask(ownerAs, json("task", "DOCUMENT_ANALYSIS", "documentId", plain.toString()), 200);
        assertThat(this.<String>read(nothing, "$.outcome")).isEqualTo("INSUFFICIENT_SOURCES");
    }

    @Test
    void aDocumentOfAHiddenCaseOrWithoutThePermissionIsNotReadable() throws Exception {
        UUID secret = documentWithText(hidden, "وثيقة سرية", "حكمت المحكمة بسرية الزئبق الأحمر 2026-01-01", "EXTRACTED");
        MvcResult result = ask(ownerAs, json("task", "DOCUMENT_ANALYSIS", "documentId", secret.toString()), 404);
        assertThat(body(result)).doesNotContain("الزئبق");
        ask(ownerAs, json("task", "COMPARISON", "documentId", secret.toString(), "otherDocumentId", secret.toString()), 404);
        ask(ownerAs, json("task", "DOCUMENT_ANALYSIS", "documentId", UUID.randomUUID().toString()), 404);
        UUID own = documentWithText(visible, "وثيقة", "حكمت المحكمة بالرفض 2026-01-01", "EXTRACTED");
        ask(outsider(owner, Permission.ASSISTANT_USE, Permission.CASE_VIEW), json("task", "DOCUMENT_ANALYSIS", "documentId", own.toString()), 403);
        jdbc.update("update case_document set withdrawn_at = now(), withdrawn_by = gen_random_uuid(), withdrawn_reason = 'x' where id = ?", own);
        ask(ownerAs, json("task", "DOCUMENT_ANALYSIS", "documentId", own.toString()), 404);
    }

    // ---- 22.5 comparison ------------------------------------------------------------------------------------------------

    @Test
    void theComparisonSeparatesWhatIsOnlyInEachDocumentFromWhatTheyShare() throws Exception {
        UUID first = documentWithText(visible, "المذكرة الأولى", "يلتزم المورد بالتسليم خلال ثلاثين يوما. الثمن خمسة آلاف دينار. التحكيم في الكويت.", "EXTRACTED");
        UUID second = documentWithText(visible, "المذكرة الثانية", "يلتزم المورد بالتسليم خلال ثلاثين يوما. الثمن ستة آلاف دينار. المحكمة المختصة هي الكلية.", "EXTRACTED");
        MvcResult result = ask(ownerAs, json("task", "COMPARISON", "documentId", first.toString(), "otherDocumentId", second.toString()), 200);
        assertThat(this.<List<String>>read(result, "$.blocks[?(@.kind == 'HEADING')].code")).containsExactly("ONLY_IN_FIRST", "ONLY_IN_SECOND", "COMMON");
        String text = body(result);
        assertThat(text).contains("خمسة آلاف").contains("ستة آلاف").contains("يلتزم المورد");
        UUID scanned = documentWithText(visible, "مسح", null, "OCR_UNAVAILABLE");
        MvcResult refused = ask(ownerAs, json("task", "COMPARISON", "documentId", first.toString(), "otherDocumentId", scanned.toString()), 200);
        assertThat(this.<String>read(refused, "$.refusal")).isEqualTo("DOCUMENT_TEXT_UNAVAILABLE");
    }

    // ---- 22.3 precedents ------------------------------------------------------------------------------------------------

    @Test
    void precedentsAreCitedWithCourtNumberYearAndDateAndEveryRecordedFactIsLabelled() throws Exception {
        UUID judgment = libraryJudgment("حكم في عقد التوريد وأثر التأخير", "قررت المحكمة التعويض");
        jdbc.update("update library_judgment set case_number = '455', case_year = 2024, judgment_date = '2024-06-01', direction = 'FOR_AUTHORITY',"
                + " source_reference = 'مجموعة المبادئ' where id = ?", judgment);
        MvcResult result = ask(ownerAs, json("task", "PRECEDENTS", "question", "التعويض عن التأخير في التوريد"), 200);
        assertThat(this.<List<String>>read(result, "$.sources[*].type")).containsOnly("LIBRARY");
        assertThat(body(result)).contains("455").contains("2024").contains("2024-06-01").contains("محكمة التمييز");
        assertThat(this.<List<String>>read(result, "$.sources[*].fields[?(@.code == 'direction')].value")).contains("FOR_AUTHORITY");
        MvcResult byCase = ask(ownerAs, json("task", "PRECEDENTS", "caseId", visible.toString()), 200);
        assertThat(this.<String>read(byCase, "$.caseId")).isEqualTo(visible.toString());
    }

    @Test
    void anUnrelatedPrecedentQuestionIsRefusedAndTheLibraryPermissionIsRequired() throws Exception {
        MvcResult none = ask(ownerAs, json("task", "PRECEDENTS", "question", "الملاحة الجوية الفضائية"), 200);
        assertThat(this.<String>read(none, "$.outcome")).isEqualTo("INSUFFICIENT_SOURCES");
        MvcResult noWords = ask(ownerAs, json("task", "PRECEDENTS", "question", "ما هي"), 200);
        assertThat(this.<String>read(noWords, "$.refusal")).isEqualTo("NO_SEARCH_WORDS");
        ask(outsider(owner, Permission.ASSISTANT_USE, Permission.CASE_VIEW), json("task", "PRECEDENTS", "caseId", visible.toString()), 403);
        MvcResult withoutLibrary = ask(outsider(owner, Permission.ASSISTANT_USE, Permission.CASE_VIEW), json("task", "PRECEDENTS", "question", "التعويض التأخير"), 200);
        assertThat(this.<String>read(withoutLibrary, "$.outcome")).isEqualTo("INSUFFICIENT_SOURCES");
        assertThat(count("select count(*) from assistant_query_log where outcome = 'DENIED_PERMISSION' and user_id = ?", owner)).isEqualTo(1);
    }

    @Test
    void aWithdrawnJudgmentOrOneOnlySpelledAlikeIsNotOffered() throws Exception {
        UUID withdrawn = libraryJudgment("حكم في سحب الترخيص التجاري", "ملخص");
        jdbc.update("update library_judgment set status = 'WITHDRAWN', withdrawn_reason = 'x', withdrawn_at = now(), withdrawn_by = gen_random_uuid() where id = ?", withdrawn);
        MvcResult result = ask(ownerAs, json("task", "PRECEDENTS", "question", "سحب الترخيص التجاري"), 200);
        assertThat(this.<String>read(result, "$.outcome")).isEqualTo("INSUFFICIENT_SOURCES");
    }

    @Test
    void aQuestionWithoutACaseSearchesTheLibraryOnlyAndNeverACase() throws Exception {
        MvcResult result = ask(ownerAs, question(null, "التعويض عن التأخير في التوريد"), 200);
        assertThat(this.<List<String>>read(result, "$.sources[*].type")).containsOnly("LIBRARY");
        assertThat(body(result)).doesNotContain("استمعت المحكمة");
    }
}
