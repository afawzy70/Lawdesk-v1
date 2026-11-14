package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-DOC-13: the whole catalogue. Every list of the screens is seeded with its current codes; class-b lists are
 * label/order only and class-a lists take new options that the services accept (claim kind, evidence importance), keep
 * for existing rows once deactivated, and refuse when unknown.
 */
class CatalogListsApiTest extends PleadingsTestSupport {

    private static RequestPostProcessor admin() {
        return with(Permission.CASE_VIEW, Permission.LIST_ADMIN);
    }

    @AfterEach
    void removeNewOptions() {
        jdbc.update("delete from lookup_value_history where value_id in (select id from lookup_value where code like 'CK\\_%' or code like 'EI\\_%')");
        jdbc.update("delete from lookup_value where code like 'CK\\_%' or code like 'EI\\_%'");
    }

    @Test
    void everyListOfTheCatalogueIsSeededAndClassBListsAreSystemOnly() {
        for (ListCatalog.Def def : ListCatalog.ALL) {
            if (def.source() != ListCatalog.Source.LOOKUP || ListCatalog.ORIGINAL.contains(def.code())) {
                continue;
            }
            int options = count("select count(*) from lookup_value where list_code = ?", def.code());
            assertThat(options).as(def.code() + " has seeded options").isPositive();
            int systemOptions = count("select count(*) from lookup_value where list_code = ? and system", def.code());
            assertThat(systemOptions).as(def.code() + (def.addable() ? " (class a) has no system option" : " (class b) is all system"))
                    .isEqualTo(def.addable() ? 0 : options);
        }
        assertThat(ListCatalog.ALL.stream().filter(ListCatalog.Def::addable).map(ListCatalog.Def::code))
                .contains("CASE_IMPORTANCE", "CLAIM_KIND", "EVIDENCE_IMPORTANCE", "JUDGMENT_TYPE", "COURTS");
    }

    @Test
    void anyoneSignedInReadsTheWholeCatalogueInOneCallWithTheActiveFlagAndOrder() throws Exception {
        get(with(Permission.TASK_VIEW), "/api/v1/lookups/all").andExpect(status().isOk())
                .andExpect(jsonPath("$.CLAIM_KIND[0].code").value("PRIMARY"))
                .andExpect(jsonPath("$.CLAIM_KIND[0].active").value(true))
                .andExpect(jsonPath("$.CASE_STATUS.length()").value(5))
                .andExpect(jsonPath("$.HEARING_PURPOSE[4].labelEn").value("Pronouncement of judgment"))
                .andExpect(jsonPath("$.TASK_STATUS[0].code").value("NEW"));
    }

    @Test
    void aClaimKindTheAdministratorAddedWorksAndAnUnknownOrDeactivatedOneIsRefusedForNewChoices() throws Exception {
        UUID caseId = newCase();
        String path = "/api/v1/cases/" + caseId + "/claims";
        post(everything(), path, json("text", "t", "kind", "CK_NEW")).andExpect(status().isBadRequest());

        String created = body(post(admin(), "/api/v1/admin/lists/CLAIM_KIND/options", json("code", "CK_NEW", "labelAr", "نوع جديد"))
                .andExpect(status().isCreated()));
        UUID optionId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));
        UUID claim = created(everything(), path, json("text", "claim one", "kind", "CK_NEW"));
        get(everything(), "/api/v1/claims/" + claim).andExpect(jsonPath("$.kind").value("CK_NEW"));

        // Deactivated: the claim keeps it and stays editable; a new claim cannot choose it.
        put(admin(), "/api/v1/admin/lists/options/" + optionId, json("labelAr", "نوع جديد", "active", false, "version", 0))
                .andExpect(status().isOk());
        post(everything(), path, json("text", "claim two", "kind", "CK_NEW")).andExpect(status().isBadRequest());
        put(everything(), "/api/v1/claims/" + claim, json("text", "claim one edited", "kind", "CK_NEW", "status", "STANDING",
                "version", 0)).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("CK_NEW"));
        // The seeded kinds keep working with their stored codes.
        post(everything(), path, json("text", "claim three", "kind", "ALTERNATIVE")).andExpect(status().isCreated());
    }

    @Test
    void anEvidenceImportanceOptionIsAcceptedWhenActiveAndRefusedWhenUnknown() throws Exception {
        UUID caseId = newCase();
        String path = "/api/v1/cases/" + caseId + "/evidence";
        post(everything(), path, json("name", "Item", "role", "SUPPORTING", "importance", "EI_TOP")).andExpect(status().isBadRequest());
        post(admin(), "/api/v1/admin/lists/EVIDENCE_IMPORTANCE/options", json("code", "EI_TOP", "labelAr", "قصوى")).andExpect(status().isCreated());
        post(everything(), path, json("name", "Item", "role", "SUPPORTING", "importance", "EI_TOP")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.importance").value("EI_TOP"));
        post(everything(), path, json("name", "Item two", "role", "SUPPORTING", "importance", "DECISIVE")).andExpect(status().isCreated());
    }

    @Test
    void aClassBListTakesLabelEditsButNoNewOptionAndNoDeactivation() throws Exception {
        post(admin(), "/api/v1/admin/lists/TASK_STATUS/options", json("code", "PAUSED", "labelAr", "x"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("LIST_LABEL_ONLY"));
        var late = jdbc.queryForMap("select id, version from lookup_value where list_code = 'TASK_STATUS' and code = 'LATE'");
        put(admin(), "/api/v1/admin/lists/options/" + late.get("id"), json("labelAr", "متأخرة", "active", false, "version", late.get("version")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("SYSTEM_VALUE_FIXED"));
        put(admin(), "/api/v1/admin/lists/options/" + late.get("id"), json("labelAr", "متأخرة جدًا", "labelEn", "Overdue", "active", true,
                "version", late.get("version"), "sortOrder", 3)).andExpect(status().isOk());
        get(with(Permission.TASK_VIEW), "/api/v1/lookups/all").andExpect(jsonPath("$.TASK_STATUS[?(@.code=='LATE')].labelEn").value("Overdue"));
        jdbc.update("update lookup_value set label_ar = 'متأخرة', label_en = 'Late', sort_order = 6, version = 0 where list_code = 'TASK_STATUS' and code = 'LATE'");
        // Storing a task status the old way still works: the codes did not change.
        assertThat(count("select count(*) from lookup_value where list_code = 'TASK_STATUS'")).isEqualTo(7);
    }
}
