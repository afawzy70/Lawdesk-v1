package com.lawdesk.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** LD-052, section 16.7: counts by court, year and classification, and the legal questions named most often. */
class LibraryReportApiTest extends LibrarySupport {

    @Test
    void anEmptyLibraryReportsZeroes() throws Exception {
        mvc.perform(getAs(READER, "/api/v1/library/reports/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.byCourt.length()").value(0))
                .andExpect(jsonPath("$.frequentIssues.length()").value(0));
    }

    @Test
    void countsActiveJudgmentsByCourtYearDirectionFinalityAndClassification() throws Exception {
        UUID topic = classification("LEGAL_TOPIC", "LIAB", "المسؤولية");
        UUID outcome = seededClassification("OUTCOME", "REJECTED");
        register("أ", "courtName", "محكمة التمييز", "judgmentDate", "2020-01-01", "direction", "FOR_AUTHORITY",
                "finality", "FINAL", "classificationIds", List.of(topic.toString(), outcome.toString()));
        register("ب", "courtName", "محكمة  التمييز", "judgmentDate", "2020-06-01", "classificationIds", List.of(topic.toString()));
        register("ج", "courtName", "محكمة الاستئناف", "judgmentDate", "2021-01-01", "direction", "AGAINST_AUTHORITY");
        UUID gone = register("د", "courtName", "محكمة الاستئناف", "judgmentDate", "2021-02-01");
        mvc.perform(postJson(MANAGER, "/api/v1/library/judgments/" + gone + "/withdraw", json("reason", "خطأ", "version", 0)));

        mvc.perform(getAs(READER, "/api/v1/library/reports/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.active").value(3))
                .andExpect(jsonPath("$.withdrawn").value(1))
                .andExpect(jsonPath("$.byCourt[0].count").value(2))
                .andExpect(jsonPath("$.byCourt[1].count").value(1))
                .andExpect(jsonPath("$.byYear[?(@.key=='2020')].count").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.byYear[?(@.key=='2021')].count").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.byDirection[?(@.key=='NOT_SPECIFIED')].count").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.byFinality[?(@.key=='FINAL')].count").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.byClassification[?(@.code=='LIAB')].count").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$.byClassification[?(@.code=='REJECTED')].count").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void frequentIssuesGroupPrinciplesByTheirNormalisedTopicAndShowCountsSourcesAndDates() throws Exception {
        UUID a = register("أ", "judgmentDate", "2019-03-01");
        UUID b = register("ب", "judgmentDate", "2022-03-01");
        addPrinciple(a, "مبدأ ١", "legalTopic", "مسؤولية الإدارة");
        addPrinciple(b, "مبدأ ٢", "legalTopic", "مسئولية الاداره");
        addPrinciple(b, "مبدأ ٣", "legalTopic", "اختصاص المحكمة");
        addPrinciple(b, "مبدأ ٤");
        mvc.perform(getAs(READER, "/api/v1/library/reports/summary"))
                .andExpect(jsonPath("$.frequentIssues.length()").value(2))
                .andExpect(jsonPath("$.frequentIssues[0].principleCount").value(2))
                .andExpect(jsonPath("$.frequentIssues[0].judgmentCount").value(2))
                .andExpect(jsonPath("$.frequentIssues[0].firstJudgmentDate").value("2019-03-01"))
                .andExpect(jsonPath("$.frequentIssues[0].lastJudgmentDate").value("2022-03-01"))
                .andExpect(jsonPath("$.frequentIssues[1].principleCount").value(1));
    }

    @Test
    void theReportNeedsTheLibraryPermission() throws Exception {
        mvc.perform(getAs(com.lawdesk.security.TestActors.as(OTHER_ID, "x", com.lawdesk.security.Permission.CASE_VIEW),
                "/api/v1/library/reports/summary")).andExpect(status().isForbidden());
        assertThat(count("select count(*) from library_judgment")).isZero();
    }
}
