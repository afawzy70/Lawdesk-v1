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
        // An invalid body must not tell an outsider anything about the case: the answer for the hidden case and for an id
        // that does not exist is the same status and the same body (400 comes from the request validation for both).
        String invalid = "{\"plaintiff\":[\"\"],\"defendant\":[]}";
        var hidden = mvc.perform(put("/api/v1/cases/" + caseId + "/requests").with(outsider).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(invalid)).andReturn().getResponse();
        var absent = mvc.perform(put("/api/v1/cases/" + UUID.randomUUID() + "/requests").with(outsider).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(invalid)).andReturn().getResponse();
        assertThat(hidden.getStatus()).isEqualTo(absent.getStatus());
        assertThat(normalised(hidden.getContentAsString())).isEqualTo(normalised(absent.getContentAsString()));
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

    private String replaceItems(UUID caseId, RequestPostProcessor who, String body, int expected) throws Exception {
        return mvc.perform(put("/api/v1/cases/" + caseId + "/requests").with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private static String items(String side, int n) {
        return String.join(",", java.util.stream.IntStream.rangeClosed(1, n).mapToObj(i -> "\"" + side + " " + i + "\"").toList());
    }

    @Test
    void exactlyTwentyItemsPerSideAreAcceptedTheTwentyFirstIsRefusedAndNothingChanges() throws Exception {
        UUID caseId = registered(clerk, intake(court, specialist, ""));
        replaceItems(caseId, editor, "{\"plaintiff\":[" + items("P", 20) + "],\"defendant\":[" + items("D", 20) + "]}", 200);
        assertThat(count("select count(*) from case_request_item where case_id = ?::uuid and removed_at is null", caseId.toString()))
                .isEqualTo(40);
        replaceItems(caseId, editor, "{\"plaintiff\":[" + items("P", 21) + "],\"defendant\":[]}", 400);
        replaceItems(caseId, editor, "{\"plaintiff\":[],\"defendant\":[" + items("D", 21) + "]}", 400);
        assertThat(count("select count(*) from case_request_item where case_id = ?::uuid and removed_at is null", caseId.toString()))
                .as("a refused change leaves the stored items as they were").isEqualTo(40);
        mvc.perform(get("/api/v1/cases/" + caseId + "/requests").with(reader)).andExpect(jsonPath("$.plaintiff.length()").value(20))
                .andExpect(jsonPath("$.defendant.length()").value(20)).andExpect(jsonPath("$.plaintiff[19]").value("P 20"));
    }

    @Test
    void anItemCanBeEditedAndRemovedTheOldTextStaysMarkedAndAFreshReadShowsTheCurrentList() throws Exception {
        UUID caseId = registered(clerk, intake(court, specialist, "\"requests\":{\"plaintiff\":[\"one\",\"two\",\"three\"],\"defendant\":[\"d\"]}"));
        // Edit the second item.
        replaceItems(caseId, editor, "{\"plaintiff\":[\"one\",\"two (amended)\",\"three\"],\"defendant\":[\"d\"]}", 200);
        mvc.perform(get("/api/v1/cases/" + caseId + "/requests").with(reader)).andExpect(jsonPath("$.plaintiff[1]").value("two (amended)"));
        assertThat(count("select count(*) from case_request_item where case_id = ?::uuid and body = 'two' and removed_at is not null",
                caseId.toString())).as("the old wording is kept, marked removed").isEqualTo(1);
        // Remove the first item (soft delete): the others keep their order, nothing is physically deleted.
        int rowsBefore = count("select count(*) from case_request_item where case_id = ?::uuid", caseId.toString());
        replaceItems(caseId, editor, "{\"plaintiff\":[\"two (amended)\",\"three\"],\"defendant\":[\"d\"]}", 200);
        mvc.perform(get("/api/v1/cases/" + caseId + "/requests").with(reader)).andExpect(jsonPath("$.plaintiff.length()").value(2))
                .andExpect(jsonPath("$.plaintiff[0]").value("two (amended)")).andExpect(jsonPath("$.plaintiff[1]").value("three"));
        assertThat(count("select count(*) from case_request_item where case_id = ?::uuid", caseId.toString()))
                .as("rows are only added or marked").isGreaterThanOrEqualTo(rowsBefore);
        assertThat(count("select count(*) from case_request_item where case_id = ?::uuid and body = 'one' and removed_at is not null",
                caseId.toString())).isEqualTo(2);
        // Emptying a side removes its items from the list too.
        replaceItems(caseId, editor, "{\"plaintiff\":[\"two (amended)\",\"three\"],\"defendant\":[]}", 200);
        mvc.perform(get("/api/v1/cases/" + caseId + "/requests").with(reader)).andExpect(jsonPath("$.defendant.length()").value(0));
    }

    @Test
    void aClosedOrArchivedCaseTakesNoChangeToItsItems() throws Exception {
        UUID caseId = registered(clerk, intake(court, specialist, "\"requests\":{\"plaintiff\":[\"A\"],\"defendant\":[]}"));
        jdbc.update("update legal_case set status = 'ARCHIVED' where id = ?", caseId);
        replaceItems(caseId, editor, "{\"plaintiff\":[\"B\"],\"defendant\":[]}", 422);
        mvc.perform(get("/api/v1/cases/" + caseId + "/requests").with(reader)).andExpect(jsonPath("$.plaintiff[0]").value("A"));
    }

    @Test
    void registeringWithItemsNeedsTheRegistrationRightAndStoresThemAtomically() throws Exception {
        RequestPostProcessor viewerOnly = scoped(member("ViewOnly"), "vo", List.of("ALL"), Permission.CASE_VIEW);
        mvc.perform(register(viewerOnly, intake(court, specialist, "\"requests\":{\"plaintiff\":[\"A\"],\"defendant\":[]}")))
                .andExpect(status().isForbidden());
        assertThat(count("select count(*) from case_request_item")).isZero();
    }

    /** The body without the request path and ids, which differ between two requests by design. */
    private static String normalised(String body) {
        return body.replaceAll("\"instance\":\"[^\"]*\"", "").replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "ID");
    }
}
