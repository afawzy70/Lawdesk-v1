package com.lawdesk.library;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/** LD-052, section 16.1: registering judgments in the library, with the source and its verification mandatory. */
class LibraryJudgmentApiTest extends LibrarySupport {

    private static final String BASE = "/api/v1/library/judgments";

    @Test
    void registersAJudgmentWithEverythingTheRequirementsListAndStampsTheEnteringMember() throws Exception {
        UUID topic = classification("LEGAL_TOPIC", "LIABILITY", "المسؤولية الإدارية");
        String body = judgmentJson("تعويض عن قرار إداري معيب", "courtLevel", "CASSATION", "chamber", "الدائرة الإدارية",
                "caseNumber", "1234", "caseYear", 2020, "judgmentDate", "2021-03-02", "caseType", "ADMINISTRATIVE",
                "partiesDescription", "جهة إدارية ضد مواطن", "summary", "ملخص الحكم", "operativeText", "حكمت المحكمة بالتعويض",
                "reasons", "لثبوت الخطأ والضرر وعلاقة السببية", "legalTexts", "المادة ٢٢٧ مدني",
                "requestsAddressed", "طلب التعويض", "requestResults", "قبول الطلب", "direction", "AGAINST_AUTHORITY",
                "keywords", List.of("تعويض", "قرار إداري"), "finality", "FINAL", "copyReference", "ملف رقم ١٢ بالأرشيف",
                "classificationIds", List.of(topic.toString()));
        mvc.perform(postJson(MANAGER, BASE, body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("تعويض عن قرار إداري معيب"))
                .andExpect(jsonPath("$.courtLevel").value("CASSATION"))
                .andExpect(jsonPath("$.caseYear").value(2020))
                .andExpect(jsonPath("$.keywords.length()").value(2))
                .andExpect(jsonPath("$.classifications[0].code").value("LIABILITY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.finality").value("FINAL"))
                .andExpect(jsonPath("$.createdBy").value(MANAGER_ID.toString()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.version").value(0));
        assertThat(audited("LIBRARY_JUDGMENT_CREATE")).isEqualTo(1);
    }

    @Test
    void courtSubjectSourceAndVerificationAreMandatoryButNothingElseIs() throws Exception {
        mvc.perform(postJson(MANAGER, BASE, json("subject", "x", "sourceReference", "s", "verificationNote", "v")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE, json("courtName", "c", "sourceReference", "s", "verificationNote", "v")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE, json("courtName", "c", "subject", "x", "verificationNote", "v")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE, json("courtName", "c", "subject", "x", "sourceReference", "s")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE, json("courtName", "c", "subject", "x", "sourceReference", "   ",
                "verificationNote", "v"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE, json("courtName", "c", "subject", "x", "sourceReference", "s",
                "verificationNote", "v"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseNumber").doesNotExist())
                .andExpect(jsonPath("$.finality").value("NOT_SPECIFIED"));
        assertThat(count("select count(*) from library_judgment")).isEqualTo(1);
    }

    @Test
    void rejectsUnknownCodesFutureDatesAndBadYears() throws Exception {
        mvc.perform(postJson(MANAGER, BASE, judgmentJson("x", "finality", "SETTLED"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE, judgmentJson("x", "direction", "WINNING"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE, judgmentJson("x", "caseYear", 1500))).andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE, judgmentJson("x", "judgmentDate", "2999-01-01")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("LIBRARY_DATE_IN_FUTURE"));
        mvc.perform(postJson(MANAGER, BASE, judgmentJson("x", "classificationIds", List.of(UUID.randomUUID().toString()))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LIBRARY_CLASSIFICATION_UNKNOWN"));
        assertThat(count("select count(*) from library_judgment")).isZero();
    }

    @Test
    void anInactiveClassificationCannotBeAssignedAnymore() throws Exception {
        UUID topic = classification("LEGAL_TOPIC", "OLD", "موضوع قديم");
        jdbc.update("update library_classification set active = false where id = ?", topic);
        mvc.perform(postJson(MANAGER, BASE, judgmentJson("x", "classificationIds", List.of(topic.toString()))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LIBRARY_CLASSIFICATION_UNKNOWN"));
    }

    @Test
    void aSecondEntryOfTheSameCitationIsRefusedUnlessTheMemberConfirmsItIsAnotherJudgment() throws Exception {
        String first = judgmentJson("تعويض", "caseNumber", "55", "caseYear", 2019, "judgmentDate", "2020-01-05");
        registerJson(first);
        mvc.perform(postJson(MANAGER, BASE, first)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LIBRARY_DUPLICATE_SUSPECTED"));
        // Nothing is merged: confirming keeps both rows.
        mvc.perform(postJson(MANAGER, BASE, judgmentJson("تعويض", "caseNumber", "55", "caseYear", 2019,
                "judgmentDate", "2020-01-05", "allowDuplicate", true))).andExpect(status().isCreated());
        assertThat(count("select count(*) from library_judgment")).isEqualTo(2);
        // A different number or an entry without a number is never flagged.
        register("تعويض", "caseNumber", "56", "caseYear", 2019, "judgmentDate", "2020-01-05");
        register("تعويض");
        register("تعويض");
        assertThat(count("select count(*) from library_judgment")).isEqualTo(5);
    }

    @Test
    void readingReturnsTheFullRecordAndAnUnknownIdIsNotFound() throws Exception {
        UUID id = register("إيجار", "operativeText", "منطوق", "reasons", "أسباب");
        mvc.perform(getAs(READER, BASE + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.operativeText").value("منطوق"))
                .andExpect(jsonPath("$.reasons").value("أسباب"))
                .andExpect(jsonPath("$.principles.length()").value(0))
                .andExpect(jsonPath("$.validity.legalValidityDecided").value(false))
                .andExpect(jsonPath("$.linkedCases.length()").value(0));
        mvc.perform(getAs(READER, BASE + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void updatingChangesTheRecordUnderOptimisticLockingAndIsAudited() throws Exception {
        UUID id = register("إيجار", "caseNumber", "1");
        mvc.perform(putJson(MANAGER, BASE + "/" + id, judgmentJson("إيجار تجاري", "caseNumber", "1", "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("إيجار تجاري"))
                .andExpect(jsonPath("$.version").value(1));
        // A stale version is a conflict, and the stored record is unchanged.
        mvc.perform(putJson(MANAGER, BASE + "/" + id, judgmentJson("آخر", "version", 0)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select subject from library_judgment where id = ?", String.class, id))
                .isEqualTo("إيجار تجاري");
        assertThat(audited("LIBRARY_JUDGMENT_UPDATE")).isEqualTo(1);
    }

    @Test
    void classificationsAreReplacedAsASetOnUpdate() throws Exception {
        UUID a = classification("LEGAL_TOPIC", "A", "موضوع أ");
        UUID b = classification("LEGAL_TOPIC", "B", "موضوع ب");
        UUID id = registerJson(judgmentJson("x", "classificationIds", List.of(a.toString())));
        mvc.perform(putJson(MANAGER, BASE + "/" + id, judgmentJson("x", "version", 0, "classificationIds",
                List.of(b.toString())))).andExpect(status().isOk())
                .andExpect(jsonPath("$.classifications.length()").value(1))
                .andExpect(jsonPath("$.classifications[0].code").value("B"));
    }

    @Test
    void withdrawingKeepsTheRecordWithItsReasonAndReinstatingBringsItBack() throws Exception {
        UUID id = register("إيجار");
        mvc.perform(postJson(MANAGER, BASE + "/" + id + "/withdraw", json("reason", "", "version", 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE + "/" + id + "/withdraw", json("reason", "أُدخل بالخطأ", "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.withdrawnReason").value("أُدخل بالخطأ"));
        // Withdrawn judgments are out of the default list but still readable and listed on request.
        assertThat(searchIds(READER, "")).doesNotContain(id.toString());
        assertThat(searchIds(READER, "includeWithdrawn=true")).contains(id.toString());
        mvc.perform(getAs(READER, BASE + "/" + id)).andExpect(status().isOk());
        mvc.perform(postJson(MANAGER, BASE + "/" + id + "/withdraw", json("reason", "مرة أخرى", "version", 1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LIBRARY_ALREADY_WITHDRAWN"));
        mvc.perform(postJson(MANAGER, BASE + "/" + id + "/reinstate", json("version", 1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.withdrawnReason").doesNotExist());
        assertThat(count("select count(*) from library_judgment")).isEqualTo(1);
        assertThat(audited("LIBRARY_JUDGMENT_WITHDRAW", "LIBRARY_JUDGMENT_REINSTATE")).isEqualTo(2);
    }

    @Test
    void theNamesOfPartiesInARestrictedJudgmentAreHiddenFromReaders() throws Exception {
        UUID id = register("سري", "partiesDescription", "فلان ضد علان", "confidentiality", "RESTRICTED");
        UUID open = register("علني", "partiesDescription", "وزارة ضد شركة");
        mvc.perform(getAs(READER, BASE + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partiesDescription").doesNotExist())
                .andExpect(jsonPath("$.partiesMasked").value(true))
                .andExpect(jsonPath("$.subject").value("سري"));
        mvc.perform(getAs(MANAGER, BASE + "/" + id)).andExpect(jsonPath("$.partiesDescription").value("فلان ضد علان"))
                .andExpect(jsonPath("$.partiesMasked").value(false));
        mvc.perform(getAs(READER, BASE + "/" + open)).andExpect(jsonPath("$.partiesDescription").value("وزارة ضد شركة"));
        // The parties text is never searched, so a reader cannot find a hidden name by guessing.
        assertThat(searchIds(READER, "q=فلان")).isEmpty();
    }

    @Test
    void readersCannotChangeTheLibraryAndAnonymousUsersCannotReadIt() throws Exception {
        UUID id = register("إيجار");
        mvc.perform(postJson(READER, BASE, judgmentJson("x"))).andExpect(status().isForbidden());
        mvc.perform(putJson(READER, BASE + "/" + id, judgmentJson("x", "version", 0))).andExpect(status().isForbidden());
        mvc.perform(postJson(READER, BASE + "/" + id + "/withdraw", json("reason", "r", "version", 0)))
                .andExpect(status().isForbidden());
        RequestPostProcessorHolder.noLibrary(mvc, BASE, id);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE))
                .andExpect(status().isUnauthorized());
    }

    /** A user with other permissions but not the library's gets 403 on every read. */
    static final class RequestPostProcessorHolder {
        static void noLibrary(org.springframework.test.web.servlet.MockMvc mvc, String base, UUID id) throws Exception {
            var other = as(OTHER_ID, "other", Permission.CASE_VIEW);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base).with(other))
                    .andExpect(status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base + "/" + id).with(other))
                    .andExpect(status().isForbidden());
        }
    }
}
