package com.lawdesk.lists;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;
import com.lawdesk.wp9.Wp9TestSupport;

/**
 * LD-057, owner decision D10: the kinds of settlement, directive and template are managed lists. Only the values the
 * requirements text names are seeded (origin DOCUMENT); a manager of the list adds the rest (origin PROPOSED), edits and
 * deactivates them, and nothing is deleted.
 */
class ManagedListApiTest extends Wp9TestSupport {

    private RequestPostProcessor reader() {
        return staff(user("Reader"), Permission.CASE_VIEW);
    }

    @Test
    void onlyTheValuesTheRequirementsTextNamesAreSeededAndMarkedAsTheDocumentsOwn() throws Exception {
        mvc.perform(getJson(reader(), "/api/v1/lists/SETTLEMENT_KIND")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].code").value("SETTLEMENT"))
                .andExpect(jsonPath("$[0].labelAr").value("تسوية")).andExpect(jsonPath("$[1].code").value("WAIVER"))
                .andExpect(jsonPath("$[2].code").value("CONCILIATION")).andExpect(jsonPath("$[0].origin").value("DOCUMENT"));
        mvc.perform(getJson(reader(), "/api/v1/lists/DIRECTIVE_KIND")).andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.origin != 'DOCUMENT')]").isEmpty());
        mvc.perform(getJson(reader(), "/api/v1/lists/TEMPLATE_KIND")).andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[?(@.code == 'LEGAL_POSITION_REPORT')].labelAr").value("تقرير الموقف القانوني"));
    }

    @Test
    void anUnknownListIsRefused() throws Exception {
        mvc.perform(getJson(reader(), "/api/v1/lists/NOT_A_LIST")).andExpect(status().isBadRequest());
    }

    @Test
    void aManagerOfTheListProposesAValueAndEditsAndDeactivatesItButNothingIsDeleted() throws Exception {
        RequestPostProcessor manager = staff(user("Manager"), Permission.CASE_VIEW, Permission.DIRECTIVE_MANAGE);
        MvcResult added = mvc.perform(postJson(manager, "/api/v1/lists/DIRECTIVE_KIND",
                        json("code", "ARCHIVE_FILE", "labelAr", "أرشفة الملف", "labelEn", "Archive the file")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.origin").value("PROPOSED"))
                .andExpect(jsonPath("$.active").value(true)).andReturn();
        UUID id = idOf(added);
        mvc.perform(postJson(manager, "/api/v1/lists/DIRECTIVE_KIND",
                json("code", "ARCHIVE_FILE", "labelAr", "اسم آخر"))).andExpect(status().isConflict());
        // The same label (spelled with another hamza form) is the same value.
        mvc.perform(postJson(manager, "/api/v1/lists/DIRECTIVE_KIND", json("code", "OTHER_ONE", "labelAr", "ارشفة الملف")))
                .andExpect(status().isConflict());
        mvc.perform(putJson(manager, "/api/v1/lists/DIRECTIVE_KIND/" + id,
                        json("labelAr", "أرشفة الملف وحفظه", "labelEn", "Archive", "active", false, "sortOrder", 9, "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(getJson(manager, "/api/v1/lists/DIRECTIVE_KIND")).andExpect(jsonPath("$.length()").value(6));
        mvc.perform(getJson(manager, "/api/v1/lists/DIRECTIVE_KIND?includeInactive=true"))
                .andExpect(jsonPath("$.length()").value(7));
        mvc.perform(putJson(manager, "/api/v1/lists/DIRECTIVE_KIND/" + id,
                json("labelAr", "x", "active", true, "sortOrder", 1, "version", 0))).andExpect(status().isConflict());
        assertThat(count("select count(*) from managed_list_value where list_code = 'DIRECTIVE_KIND'")).isEqualTo(7);
    }

    @Test
    void theValueTheDocumentNamesCanBeDeactivatedButKeepsItsOrigin() throws Exception {
        RequestPostProcessor manager = staff(user("Manager"), Permission.CASE_VIEW, Permission.SETTLEMENT_MANAGE);
        UUID waiver = jdbc.queryForObject("select id from managed_list_value where code = 'WAIVER'", UUID.class);
        mvc.perform(putJson(manager, "/api/v1/lists/SETTLEMENT_KIND/" + waiver,
                        json("labelAr", "تنازل", "active", false, "sortOrder", 2, "version", 0))).andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("DOCUMENT")).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void eachListIsManagedByTheHoldersOfItsOwnPermission() throws Exception {
        RequestPostProcessor settlementManager = staff(user("S"), Permission.CASE_VIEW, Permission.SETTLEMENT_MANAGE);
        mvc.perform(postJson(settlementManager, "/api/v1/lists/TEMPLATE_KIND", json("code", "X", "labelAr", "نوع")))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(settlementManager, "/api/v1/lists/DIRECTIVE_KIND", json("code", "X", "labelAr", "نوع")))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(reader(), "/api/v1/lists/SETTLEMENT_KIND", json("code", "X", "labelAr", "نوع")))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(settlementManager, "/api/v1/lists/SETTLEMENT_KIND", json("code", "partial", "labelAr", "نوع")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(settlementManager, "/api/v1/lists/SETTLEMENT_KIND", json("code", "PARTIAL", "labelAr", " ")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(settlementManager, "/api/v1/lists/SETTLEMENT_KIND", json("code", "PARTIAL", "labelAr", "تسوية جزئية")))
                .andExpect(status().isCreated());
        mvc.perform(getJson(staff(user("N")), "/api/v1/lists/SETTLEMENT_KIND")).andExpect(status().isForbidden());
    }
}
