package com.lawdesk.cases;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-DOC-r5, stream A: a case's importance is an option of the administrator's list. A new option works on the screens
 * that use it, an unknown or deactivated one is refused for a new choice, and a case that already carries a later
 * deactivated option keeps it and can still be edited.
 */
class ImportanceListApiTest extends GovernmentIntakeTestSupport {

    private UUID specialist;
    private UUID court;
    private RequestPostProcessor clerk;

    @BeforeEach
    void actors() {
        specialist = member("Specialist");
        court = firstInstanceCourt();
        clerk = registrar(member("Clerk"));
    }

    @AfterEach
    void removeTestOptions() {
        jdbc.update("delete from lookup_value_history where value_id in (select id from lookup_value where code like 'IMP_%')");
        jdbc.update("delete from lookup_value where code like 'IMP_%'");
    }

    private void option(String code, boolean active) {
        jdbc.update("""
                insert into lookup_value (list_code, code, label_ar, origin, active, sort_order, created_by, updated_by)
                values ('CASE_IMPORTANCE', ?, 'اختبار', 'PROPOSED', ?, 9, gen_random_uuid(), gen_random_uuid())""", code, active);
    }

    private RequestPostProcessor specialistActor() {
        return as(specialist, "specialist", Permission.CASE_VIEW, Permission.CASE_EDIT, Permission.PARTY_MANAGE);
    }

    private static String details(String importance) {
        return "\"details\":{\"importance\":\"" + importance + "\"}";
    }

    @Test
    void anOptionTheAdministratorAddedIsAcceptedAndAnUnknownOrDeactivatedOneIsRefused() throws Exception {
        option("IMP_NEW", true);
        option("IMP_OFF", false);
        UUID id = registered(clerk, intake(court, specialist, details("IMP_NEW")));
        mvc.perform(getCase(as(Permission.CASE_VIEW), id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.importance").value("IMP_NEW"));
        mvc.perform(register(clerk, intake(firstInstanceCourt(), specialist, details("IMP_OFF")))).andExpect(status().isBadRequest());
        mvc.perform(register(clerk, intake(firstInstanceCourt(), specialist, details("IMP_NOPE")))).andExpect(status().isBadRequest());
        // The existing options keep working with the codes already stored.
        registered(clerk, intake(firstInstanceCourt(), specialist, details("CRITICAL")));
    }

    @Test
    void aCaseKeepsAnOptionThatWasDeactivatedAfterwardsAndStaysEditable() throws Exception {
        option("IMP_LATER", true);
        UUID id = registered(clerk, intake(court, specialist, details("IMP_LATER")));
        jdbc.update("update lookup_value set active = false where code = 'IMP_LATER'");
        String edit = "{\"title\":\"Edited\",\"caseType\":\"COMMERCIAL\",\"openedOn\":\"2026-01-10\",\"version\":0,"
                + "\"details\":{\"importance\":\"IMP_LATER\",\"chamber\":\"c\"}}";
        mvc.perform(putCase(specialistActor(), id, edit)).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.importance").value("IMP_LATER"));
        assertThat(count("select count(*) from legal_case where importance = 'IMP_LATER'")).isEqualTo(1);
        // Choosing it again for another case is a new choice and is refused.
        UUID other = registered(clerk, intake(firstInstanceCourt(), specialist, null));
        String choose = "{\"title\":\"Other\",\"caseType\":\"COMMERCIAL\",\"openedOn\":\"2026-01-10\",\"version\":0,"
                + "\"details\":{\"importance\":\"IMP_LATER\"}}";
        mvc.perform(putCase(specialistActor(), other, choose)).andExpect(status().isBadRequest());
    }
}
