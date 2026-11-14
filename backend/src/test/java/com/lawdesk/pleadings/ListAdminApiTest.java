package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * LD-DOC-r5, stream A: the administrator's page of dropdown lists. Adding and editing options, the rules that protect
 * the values system logic depends on, the refusal of everyone who is not the administrator, and the guarantee that the
 * migration changed no stored code.
 */
class ListAdminApiTest extends PleadingsTestSupport {

    private static final String ADMIN_LISTS = "/api/v1/admin/lists";

    private static RequestPostProcessor admin() {
        return with(Permission.CASE_VIEW, Permission.LIST_ADMIN, Permission.LOOKUP_MANAGE);
    }

    @AfterEach
    void restoreSeededLabels() {
        jdbc.update("""
                update lookup_value set label_ar = 'مدني', label_en = 'Civil', sort_order = 1, version = 0
                where list_code = 'CASE_TYPE' and code = 'CIVIL' and version > 0""");
    }

    @Test
    void theCatalogueNamesEveryListAndSaysWhichOnesTakeNewOptions() throws Exception {
        get(admin(), ADMIN_LISTS).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(14))
                .andExpect(jsonPath("$[?(@.code=='CASE_IMPORTANCE')].addable").value(true))
                .andExpect(jsonPath("$[?(@.code=='COURTS')].source").value("COURT"))
                .andExpect(jsonPath("$[?(@.code=='SETTLEMENT_KIND')].source").value("MANAGED"))
                .andExpect(jsonPath("$[?(@.code=='CASE_TYPE')].addable").value(false))
                .andExpect(jsonPath("$[?(@.code=='CASE_STATUS')].addable").value(false))
                .andExpect(jsonPath("$[?(@.code=='AUTHORITY_POSITION')].addable").value(false))
                .andExpect(jsonPath("$[?(@.code=='CASE_IMPORTANCE')].options").value(4));
    }

    @Test
    void anAdministratorAddsEditsAndDeactivatesAnOptionAndEveryChangeIsAuditedAndKept() throws Exception {
        String created = body(post(admin(), ADMIN_LISTS + "/CASE_IMPORTANCE/options",
                json("code", "URGENT", "labelAr", "عاجلة", "labelEn", "Urgent")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(5)).andExpect(jsonPath("$.system").value(false)));
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));

        put(admin(), ADMIN_LISTS + "/options/" + id, json("labelAr", "عاجلة جدًا", "labelEn", "Very urgent",
                "active", true, "version", 0, "sortOrder", 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.labelAr").value("عاجلة جدًا")).andExpect(jsonPath("$.sortOrder").value(2))
                .andExpect(jsonPath("$.version").value(1));
        put(admin(), ADMIN_LISTS + "/options/" + id, json("labelAr", "عاجلة جدًا", "active", false, "version", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.sortOrder").value(2));

        get(admin(), ADMIN_LISTS + "/options/" + id + "/history").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].change").value(Matchers.contains("DEACTIVATED", "EDITED", "CREATED")));
        assertThat(count("select count(*) from audit_event where target_id = ? and action in "
                + "('LOOKUP_CREATE', 'LOOKUP_EDITED', 'LOOKUP_DEACTIVATED')", id.toString())).isEqualTo(3);
        // A deactivated option leaves the screens' list but stays in the administrator's.
        get(with(Permission.CASE_VIEW), "/api/v1/lookups/CASE_IMPORTANCE")
                .andExpect(jsonPath("$[*].code").value(Matchers.not(Matchers.hasItem("URGENT"))));
        get(admin(), ADMIN_LISTS + "/CASE_IMPORTANCE/options").andExpect(jsonPath("$[*].code").value(Matchers.hasItem("URGENT")));
    }

    @Test
    void aNewOptionShowsInTheListTheScreensRead() throws Exception {
        post(admin(), ADMIN_LISTS + "/CASE_IMPORTANCE/options", json("code", "TOP", "labelAr", "قصوى")).andExpect(status().isCreated());
        get(with(Permission.CASE_VIEW), "/api/v1/lookups/CASE_IMPORTANCE").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(Matchers.contains("LOW", "NORMAL", "HIGH", "CRITICAL", "TOP")))
                .andExpect(jsonPath("$[4].labelAr").value("قصوى"));
    }

    @Test
    void aDuplicateCodeAndAStaleVersionAreRefused() throws Exception {
        String created = body(post(admin(), ADMIN_LISTS + "/CASE_IMPORTANCE/options", json("code", "DUP", "labelAr", "مكرر"))
                .andExpect(status().isCreated()));
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));
        post(admin(), ADMIN_LISTS + "/CASE_IMPORTANCE/options", json("code", "DUP", "labelAr", "مكرر 2"))
                .andExpect(status().isConflict());
        post(admin(), ADMIN_LISTS + "/CASE_IMPORTANCE/options", json("code", "bad code", "labelAr", "x"))
                .andExpect(status().isBadRequest());
        put(admin(), ADMIN_LISTS + "/options/" + id, json("labelAr", "أ", "active", true, "version", 0)).andExpect(status().isOk());
        put(admin(), ADMIN_LISTS + "/options/" + id, json("labelAr", "ب", "active", true, "version", 0)).andExpect(status().isConflict());
        post(admin(), ADMIN_LISTS + "/NO_SUCH_LIST/options", json("code", "X", "labelAr", "x")).andExpect(status().isNotFound());
    }

    @Test
    void aListThatSystemLogicDependsOnTakesNoNewOptionAndItsSystemValuesStayActive() throws Exception {
        post(admin(), ADMIN_LISTS + "/CASE_TYPE/options", json("code", "TAX", "labelAr", "ضريبي"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("LIST_LABEL_ONLY"));
        post(admin(), ADMIN_LISTS + "/CASE_STATUS/options", json("code", "PAUSED", "labelAr", "x"))
                .andExpect(status().isUnprocessableContent());
        assertThat(count("select count(*) from lookup_value where list_code = 'CASE_TYPE'")).isEqualTo(8);

        var civil = jdbc.queryForMap("select id, version from lookup_value where list_code = 'CASE_TYPE' and code = 'CIVIL'");
        UUID id = (UUID) civil.get("id");
        long version = (Long) civil.get("version");
        put(admin(), ADMIN_LISTS + "/options/" + id, json("labelAr", "مدني", "active", false, "version", version))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("SYSTEM_VALUE_FIXED"));
        // Labels and order are editable; the code is not part of the body and never changes.
        put(admin(), ADMIN_LISTS + "/options/" + id, json("labelAr", "قضايا مدنية", "labelEn", "Civil cases", "active", true,
                "version", version, "sortOrder", 7)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("CIVIL"))
                .andExpect(jsonPath("$.labelAr").value("قضايا مدنية")).andExpect(jsonPath("$.system").value(true));
        assertThat(count("select count(*) from lookup_value where list_code = 'CASE_TYPE' and code = 'CIVIL'")).isEqualTo(1);
    }

    @Test
    void onlyTheAdministratorManagesTheCatalogue() throws Exception {
        // The legal manager's rights (LOOKUP_MANAGE) do not open the administrator's page or the new lists.
        RequestPostProcessor manager = with(Permission.CASE_VIEW, Permission.LOOKUP_MANAGE);
        get(manager, ADMIN_LISTS).andExpect(status().isForbidden());
        get(manager, ADMIN_LISTS + "/CASE_IMPORTANCE/options").andExpect(status().isForbidden());
        post(manager, ADMIN_LISTS + "/CASE_IMPORTANCE/options", json("code", "NOPE", "labelAr", "x")).andExpect(status().isForbidden());
        post(manager, "/api/v1/lookups/CASE_IMPORTANCE", json("code", "NOPE", "labelAr", "x")).andExpect(status().isForbidden());
        var civil = jdbc.queryForMap("select id, version from lookup_value where list_code = 'CASE_TYPE' and code = 'CIVIL'");
        put(manager, "/api/v1/lookups/values/" + civil.get("id"),
                json("labelAr", "x", "active", true, "version", civil.get("version"))).andExpect(status().isForbidden());
        get(with(Permission.TASK_VIEW), ADMIN_LISTS).andExpect(status().isForbidden());
        assertThat(count("select count(*) from lookup_value where code = 'NOPE'")).isZero();
        // The three original lists stay with LOOKUP_MANAGE.
        post(manager, "/api/v1/lookups/JUDGMENT_TYPE", json("code", "MANAGER_ADDED", "labelAr", "x")).andExpect(status().isCreated());
    }

    @Test
    void theAdministratorRoleHoldsTheListRightAndNoOtherRoleDoes() {
        assertThat(jdbc.queryForList("""
                select r.code from role_permission rp join app_role r on r.id = rp.role_id
                where rp.permission = 'LIST_ADMIN'""", String.class)).containsExactly("ADMIN");
    }

    @Test
    void theMigrationKeptEveryExistingCodeAndSeededTheCurrentValues() {
        assertThat(jdbc.queryForList("select code from lookup_value where list_code = 'CASE_IMPORTANCE' order by sort_order",
                String.class)).containsExactly("LOW", "NORMAL", "HIGH", "CRITICAL");
        assertThat(jdbc.queryForList("select code from lookup_value where list_code = 'CASE_STATUS' order by sort_order",
                String.class)).containsExactly("OPEN", "ACTIVE", "SUSPENDED", "CLOSED", "ARCHIVED");
        assertThat(jdbc.queryForList("select code from lookup_value where list_code = 'AUTHORITY_POSITION' order by sort_order",
                String.class)).containsExactly("PLAINTIFF", "DEFENDANT");
        assertThat(count("select count(*) from lookup_value where system and not active")).isZero();
        // Every seeded value has its history entry, so the audit trail starts complete.
        assertThat(count("""
                select count(*) from lookup_value v where not exists (select 1 from lookup_value_history h where h.value_id = v.id)"""))
                .isZero();
    }
}
