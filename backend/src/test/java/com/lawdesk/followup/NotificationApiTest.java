package com.lawdesk.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/** LD-053, requirements section 32 and decision D11: in-app notifications of the signed-in user, never sent outside. */
class NotificationApiTest extends Wp3TestSupport {

    private UUID me;
    private UUID someoneElse;
    private UUID caseId;
    private RequestPostProcessor mine;

    @BeforeEach
    void setUp() {
        me = user("Me");
        someoneElse = user("Someone else");
        caseId = legalCase(me, null);
        mine = staff(me, Permission.CASE_VIEW);
    }

    private UUID notify(UUID user, String type, UUID forCase, String ref) {
        return jdbc.queryForObject("""
                insert into notification (user_id, type, case_id, record_ref, params, dedupe_key)
                values (?, ?, ?, ?, '{"requestNo":"IR-1"}'::jsonb, ?) returning id""", UUID.class, user, type, forCase, ref,
                type + ":" + ref + ":" + user);
    }

    @Test
    void listsOnlyTheSignedInUsersOwnNewestFirstWithTheValuesOfTheirText() throws Exception {
        UUID first = notify(me, "INFO_REQUEST_OVERDUE", caseId, "INFO_REQUEST:a");
        notify(someoneElse, "INFO_REQUEST_OVERDUE", caseId, "INFO_REQUEST:b");
        UUID second = notify(me, "HEARING_NO_NEXT_DATE", caseId, "HEARING:c");
        jdbc.update("update notification set created_at = now() - interval '1 hour' where id = ?", first);
        mvc.perform(getJson(mine, "/api/v1/notifications")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.items[0].id").value(second.toString()))
                .andExpect(jsonPath("$.items[1].type").value("INFO_REQUEST_OVERDUE"))
                .andExpect(jsonPath("$.items[1].params.requestNo").value("IR-1"))
                .andExpect(jsonPath("$.items[1].caseReferenceNo").isString())
                .andExpect(jsonPath("$.items[1].recordRef").value("INFO_REQUEST:a"))
                .andExpect(jsonPath("$.items[1].readAt").doesNotExist());
        mvc.perform(getJson(mine, "/api/v1/notifications/unread-count")).andExpect(jsonPath("$.unread").value(2));
    }

    @Test
    void markingOneReadAndAllReadWorksAndTheUnreadFilterFollows() throws Exception {
        UUID one = notify(me, "RISK_HIGH", caseId, "RISK:1");
        notify(me, "RISK_HIGH", caseId, "RISK:2");
        mvc.perform(postJson(mine, "/api/v1/notifications/" + one + "/read", "{}")).andExpect(status().isNoContent());
        mvc.perform(getJson(mine, "/api/v1/notifications?unreadOnly=true")).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(postJson(mine, "/api/v1/notifications/read-all", "{}")).andExpect(status().isNoContent());
        mvc.perform(getJson(mine, "/api/v1/notifications/unread-count")).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(getJson(mine, "/api/v1/notifications")).andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].readAt").isString());
    }

    @Test
    void aUserCannotReadOrMarkSomeoneElsesNotification() throws Exception {
        UUID theirs = notify(someoneElse, "RISK_HIGH", caseId, "RISK:3");
        mvc.perform(postJson(mine, "/api/v1/notifications/" + theirs + "/read", "{}")).andExpect(status().isNotFound());
        mvc.perform(postJson(mine, "/api/v1/notifications/read-all", "{}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select read_at from notification where id = ?", Object.class, theirs)).isNull();
    }

    @Test
    void aNotificationAboutACaseTheUserNoLongerSeesIsNotShown() throws Exception {
        UUID id = notify(me, "INFO_REQUEST_OVERDUE", caseId, "INFO_REQUEST:z");
        jdbc.update("update legal_case set primary_member_id = ?, created_by = ? where id = ?", someoneElse, someoneElse, caseId);
        RequestPostProcessor noScope = outsider(me, Permission.CASE_VIEW);
        mvc.perform(getJson(noScope, "/api/v1/notifications")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(getJson(noScope, "/api/v1/notifications/unread-count")).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(postJson(noScope, "/api/v1/notifications/" + id + "/read", "{}")).andExpect(status().isNotFound());
    }

    @Test
    void theSameNotificationCannotBeStoredTwice() {
        notify(me, "RISK_HIGH", caseId, "RISK:9");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> notify(me, "RISK_HIGH", caseId, "RISK:9"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void signingInIsRequiredAndThePermissionToSeeCases() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(getJson(staff(me), "/api/v1/notifications")).andExpect(status().isForbidden());
    }

    @Test
    void pagingIsValidatedLikeEveryOtherList() throws Exception {
        mvc.perform(getJson(mine, "/api/v1/notifications?size=0")).andExpect(status().isBadRequest());
        mvc.perform(getJson(mine, "/api/v1/notifications?size=101")).andExpect(status().isBadRequest());
    }
}
