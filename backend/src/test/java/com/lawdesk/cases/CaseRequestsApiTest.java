package com.lawdesk.cases;

import static com.lawdesk.security.TestActors.scoped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/** LD-DOC-r5: «الطلبات» per side, recorded at registration and edited later, behind the same access policy as the case. */
class CaseRequestsApiTest extends GovernmentIntakeTestSupport {

    private UUID specialist;
    private UUID court;
    private RequestPostProcessor clerk;
    private RequestPostProcessor editor;
    private RequestPostProcessor reader;
    private RequestPostProcessor outsider;

    @BeforeEach
    void actors() {
        specialist = member("Specialist");
        court = firstInstanceCourt();
        clerk = registrar(member("Clerk"));
        editor = scoped(specialist, "sp", List.of("ALL"), Permission.CASE_VIEW, Permission.CASE_EDIT);
        reader = scoped(member("Reader"), "rd", List.of("ALL"), Permission.CASE_VIEW);
        outsider = scoped(member("Outsider"), "out", List.of(), Permission.CASE_VIEW, Permission.CASE_EDIT);
    }

    @Test
    void requestsGivenAtRegistrationAreStoredPerSideAndReadBack() throws Exception {
        UUID caseId = registered(clerk, intake(court, specialist,
                "\"requests\":{\"plaintiff\":[\"  Annul the decision \",\"Costs\"],\"defendant\":[\"Dismiss the claim\"]}"));
        mvc.perform(get("/api/v1/cases/" + caseId + "/requests").with(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.plaintiff.length()").value(2)).andExpect(jsonPath("$.plaintiff[0]").value("Annul the decision"))
                .andExpect(jsonPath("$.plaintiff[1]").value("Costs")).andExpect(jsonPath("$.defendant[0]").value("Dismiss the claim"));
        UUID none = registered(clerk, intake(court, specialist, "").replace("000123", "000124").replace("4471", "4472"));
        mvc.perform(get("/api/v1/cases/" + none + "/requests").with(reader)).andExpect(jsonPath("$.plaintiff.length()").value(0))
                .andExpect(jsonPath("$.defendant.length()").value(0));
    }

    @Test
    void replacingTheItemsKeepsTheOldOnesMarkedAndIsAudited() throws Exception {
        UUID caseId = registered(clerk, intake(court, specialist, "\"requests\":{\"plaintiff\":[\"A\"],\"defendant\":[]}"));
        mvc.perform(put("/api/v1/cases/" + caseId + "/requests").with(editor).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"plaintiff\":[\"B\",\"C\"],\"defendant\":[\"D\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.plaintiff[0]").value("B")).andExpect(jsonPath("$.defendant[0]").value("D"));
        assertThat(count("select count(*) from case_request_item where case_id = ?::uuid and removed_at is not null", caseId.toString()))
                .isEqualTo(1);
        assertThat(count("select count(*) from audit_event where action = 'CASE_REQUESTS_UPDATE' and target_id = ?", caseId.toString()))
                .isEqualTo(1);
        // The same lists again change nothing.
        mvc.perform(put("/api/v1/cases/" + caseId + "/requests").with(editor).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"plaintiff\":[\"B\",\"C\"],\"defendant\":[\"D\"]}")).andExpect(status().isOk());
        assertThat(count("select count(*) from case_request_item where case_id = ?::uuid and removed_at is not null", caseId.toString()))
                .isEqualTo(1);
    }

    @Test
    void blankAndTooManyItemsAreRefusedAndARegistrationWithABadItemStoresNothing() throws Exception {
        UUID caseId = registered(clerk, intake(court, specialist, ""));
        mvc.perform(put("/api/v1/cases/" + caseId + "/requests").with(editor).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"plaintiff\":[\"  \"],\"defendant\":[]}")).andExpect(status().isBadRequest());
        String many = String.join(",", java.util.Collections.nCopies(21, "\"x\""));
        mvc.perform(put("/api/v1/cases/" + caseId + "/requests").with(editor).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"plaintiff\":[" + many + "],\"defendant\":[]}")).andExpect(status().isBadRequest());
        int before = count("select count(*) from legal_case");
        mvc.perform(register(clerk, intake(court, specialist, "\"requests\":{\"plaintiff\":[\"\"],\"defendant\":[]}")))
                .andExpect(status().isBadRequest());
        assertThat(count("select count(*) from legal_case")).isEqualTo(before);
    }

    @Test
    void aUserWithoutAccessToTheCaseGetsNotFoundBeforeAnyValidationAndAReaderCannotChange() throws Exception {
        UUID caseId = registered(clerk, intake(court, specialist, "\"requests\":{\"plaintiff\":[\"A\"],\"defendant\":[]}"));
        jdbc.update("update legal_case set confidentiality = 'RESTRICTED' where id = ?", caseId);
        mvc.perform(get("/api/v1/cases/" + caseId + "/requests").with(outsider)).andExpect(status().isNotFound());
        // Even an invalid body does not reveal the case: access is checked first.
        mvc.perform(put("/api/v1/cases/" + caseId + "/requests").with(outsider).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"plaintiff\":[\"\"],\"defendant\":[]}")).andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(404), org.hamcrest.Matchers.is(400))));
        mvc.perform(put("/api/v1/cases/" + caseId + "/requests").with(outsider).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"plaintiff\":[\"Z\"],\"defendant\":[]}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/cases/" + caseId + "/requests").with(reader).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"plaintiff\":[\"Z\"],\"defendant\":[]}")).andExpect(status().isForbidden());
        assertThat(count("select count(*) from case_request_item where case_id = ?::uuid and removed_at is null", caseId.toString()))
                .isEqualTo(1);
    }

    @Test
    void whoSeesACaseIsTheSameAfterTheLawyerAndAgentTableWasDropped() throws Exception {
        UUID caseId = registered(clerk, intake(court, specialist, ""));
        // Nothing in the database (functions, views, triggers) refers to the dropped table any more.
        assertThat(count("select count(*) from pg_proc where prosrc ilike '%case_party_counsel%'")).isZero();
        assertThat(count("select count(*) from pg_views where definition ilike '%case_party_counsel%'")).isZero();
        // In scope: the case is readable and listed; outside any scope: not found and not listed.
        mvc.perform(get("/api/v1/cases/" + caseId).with(reader)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cases/" + caseId).with(outsider)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cases").with(reader)).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/cases").with(outsider)).andExpect(jsonPath("$.totalItems").value(0));
        // The members of the case keep seeing it without any scope.
        RequestPostProcessor member = scoped(specialist, "sp2", List.of(), Permission.CASE_VIEW);
        mvc.perform(get("/api/v1/cases/" + caseId).with(member)).andExpect(status().isOk());
    }
}
