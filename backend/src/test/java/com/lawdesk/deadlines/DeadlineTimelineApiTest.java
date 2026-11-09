package com.lawdesk.deadlines;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/** LD-054, requirements section 21: the deadlines of a case in its unified timeline and its executive summary. */
class DeadlineTimelineApiTest extends DeadlineTestSupport {

    private UUID member;
    private UUID caseId;
    private RequestPostProcessor specialist;
    private RequestPostProcessor competent;
    private RequestPostProcessor withoutDeadlines;

    @BeforeEach
    void aCase() {
        member = user("Mona Specialist");
        caseId = legalCase(member, null);
        specialist = staff(member, SPECIALIST);
        competent = staff(user("Hala Supervisor"), COMPETENT);
        withoutDeadlines = staff(member, Permission.CASE_VIEW);
    }

    private UUID deadline(String action, int daysFromToday) throws Exception {
        return idOf(mvc.perform(postJson(specialist, "/api/v1/cases/" + caseId + "/deadlines",
                json("sourceType", "TEXT", "sourceText", "Notice", "startsOn", today().minusDays(90).toString(), "dueOn",
                        today().plusDays(daysFromToday).toString(), "action", action, "ownerId", member)))
                .andExpect(status().isCreated()).andReturn());
    }

    @Test
    void theTimelineShowsDeadlinesPastAndFutureWithTheirStateForWhoHoldsThePermission() throws Exception {
        UUID next = deadline("Next", 10);
        UUID late = deadline("Late", -4);
        UUID done = deadline("Done", -9);
        mvc.perform(postJson(competent, "/api/v1/deadlines/" + next + "/approve", json("version", 0))).andExpect(status().isOk());
        mvc.perform(postJson(specialist, "/api/v1/deadlines/" + done + "/status", json("status", "COMPLETED", "version", 0)))
                .andExpect(status().isOk());

        mvc.perform(getJson(specialist, "/api/v1/cases/" + caseId + "/unified-timeline?types=DEADLINE&size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadlinesAvailable").value(true))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[?(@.refId=='" + next + "')].isFuture").value(true))
                .andExpect(jsonPath("$.items[?(@.refId=='" + next + "')].status").value("APPROVED"))
                .andExpect(jsonPath("$.items[?(@.refId=='" + next + "')].refType").value("DEADLINE"))
                .andExpect(jsonPath("$.items[?(@.refId=='" + late + "')].needsAction").value(true))
                .andExpect(jsonPath("$.items[?(@.refId=='" + late + "')].status").value("MANUAL"))
                .andExpect(jsonPath("$.items[?(@.refId=='" + done + "')].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[?(@.refId=='" + done + "')].needsAction").value(false));
        // Mixed with the other kinds it is also found without a filter.
        mvc.perform(getJson(specialist, "/api/v1/cases/" + caseId + "/unified-timeline?size=50"))
                .andExpect(jsonPath("$.items[?(@.kind=='DEADLINE')]", hasSize(3)));
    }

    @Test
    void aDeadlineWithoutADueDateIsNotOnTheTimelineAndAnAreaWithoutPermissionIsLeftOut() throws Exception {
        mvc.perform(postJson(specialist, "/api/v1/cases/" + caseId + "/deadlines",
                json("sourceType", "TEXT", "sourceText", "Letter", "periodDays", 30, "action", "Reply"))).andExpect(status().isCreated());
        deadline("Dated", 3);
        mvc.perform(getJson(specialist, "/api/v1/cases/" + caseId + "/unified-timeline?types=DEADLINE"))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(getJson(withoutDeadlines, "/api/v1/cases/" + caseId + "/unified-timeline?size=50"))
                .andExpect(jsonPath("$.deadlinesAvailable").value(false))
                .andExpect(jsonPath("$.items[?(@.kind=='DEADLINE')]", hasSize(0)));
    }

    @Test
    void theSummaryCountsOpenLateAndUnapprovedDeadlinesOnlyForWhoMayOpenThem() throws Exception {
        UUID approved = deadline("Approved", 5);
        deadline("Late", -2);
        deadline("Later", 20);
        mvc.perform(postJson(competent, "/api/v1/deadlines/" + approved + "/approve", json("version", 0))).andExpect(status().isOk());
        mvc.perform(getJson(specialist, "/api/v1/cases/" + caseId + "/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.deadlines.open").value(3)).andExpect(jsonPath("$.deadlines.overdue").value(1))
                .andExpect(jsonPath("$.deadlines.unapproved").value(2))
                .andExpect(jsonPath("$.deadlines.items[0].action").value("Late"))
                .andExpect(jsonPath("$.deadlines.items[0].overdue").value(true))
                .andExpect(jsonPath("$.pendingModules[?(@=='DEADLINES')]", hasSize(0)));
        mvc.perform(getJson(withoutDeadlines, "/api/v1/cases/" + caseId + "/summary"))
                .andExpect(jsonPath("$.deadlines").doesNotExist());
        mvc.perform(getJson(outsider(UUID.randomUUID(), COMPETENT), "/api/v1/cases/" + caseId + "/summary"))
                .andExpect(status().isNotFound());
    }
}
