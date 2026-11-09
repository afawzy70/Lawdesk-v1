package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * Owner decision D10: managed lists that tell the document's values from the proposed ones and keep their history. The
 * requirements text names no judgment types or natures, so every seeded value is PROPOSED and waits for the owner.
 */
class LookupApiTest extends PleadingsTestSupport {

    @Test
    void theSeededValuesAreProposedAndNeedTheOwnersApproval() throws Exception {
        get(with(Permission.CASE_VIEW), "/api/v1/lookups/JUDGMENT_TYPE").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].origin").value(Matchers.everyItem(Matchers.is("PROPOSED"))))
                .andExpect(jsonPath("$[*].needsApproval").value(Matchers.everyItem(Matchers.is(true))))
                .andExpect(jsonPath("$[0].labelAr").isString());
        get(with(Permission.CASE_VIEW), "/api/v1/lookups/JUDGMENT_NATURE").andExpect(jsonPath("$.length()").value(2));
        assertThat(count("select count(*) from lookup_value where origin = 'DOCUMENT' and list_code like 'JUDGMENT%'")).isZero();
        get(with(Permission.CASE_VIEW), "/api/v1/lookups/NO_SUCH_LIST").andExpect(status().isNotFound());
        get(with(Permission.TASK_VIEW), "/api/v1/lookups/JUDGMENT_TYPE").andExpect(status().isForbidden());
    }

    @Test
    void aNewValueIsProposedUntilApprovedAndKeepsItsHistory() throws Exception {
        String created = body(post(everything(), "/api/v1/lookups/JUDGMENT_TYPE",
                json("code", "IN_ABSENTIA", "labelAr", "حكم غيابي", "labelEn", "Judgment in absentia"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.origin").value("PROPOSED"))
                .andExpect(jsonPath("$.needsApproval").value(true)).andExpect(jsonPath("$.active").value(true)));
        UUID id = UUID.fromString(JsonPath.read(created, "$.id"));

        put(everything(), "/api/v1/lookups/values/" + id, json("labelAr", "حكم غيابي (معدل)", "labelEn", "In absentia",
                "active", true, "version", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.labelAr").value("حكم غيابي (معدل)")).andExpect(jsonPath("$.version").value(1));
        post(everything(), "/api/v1/lookups/values/" + id + "/approve", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.needsApproval").value(false)).andExpect(jsonPath("$.origin").value("PROPOSED"))
                .andExpect(jsonPath("$.approvedAt").isString());
        put(everything(), "/api/v1/lookups/values/" + id, json("labelAr", "حكم غيابي (معدل)", "active", false, "version", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        get(everything(), "/api/v1/lookups/values/" + id + "/history").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].change").value("DEACTIVATED"))
                .andExpect(jsonPath("$[1].change").value("APPROVED"))
                .andExpect(jsonPath("$[2].change").value("EDITED"))
                .andExpect(jsonPath("$[2].labelAr").value("حكم غيابي (معدل)"))
                .andExpect(jsonPath("$[3].change").value("CREATED"))
                .andExpect(jsonPath("$[3].labelAr").value("حكم غيابي"));
        // A deactivated value leaves the default list but is never deleted.
        get(everything(), "/api/v1/lookups/JUDGMENT_TYPE").andExpect(jsonPath("$.length()").value(3));
        get(everything(), "/api/v1/lookups/JUDGMENT_TYPE?includeInactive=true").andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void refusesDuplicatesBadCodesAndStaleVersions() throws Exception {
        post(everything(), "/api/v1/lookups/JUDGMENT_TYPE", json("code", "SUBSTANTIVE", "labelAr", "x"))
                .andExpect(status().isConflict());
        post(everything(), "/api/v1/lookups/JUDGMENT_TYPE", json("code", "lower case", "labelAr", "x"))
                .andExpect(status().isBadRequest());
        post(everything(), "/api/v1/lookups/JUDGMENT_TYPE", json("code", "OK_CODE", "labelAr", " "))
                .andExpect(status().isBadRequest());
        post(everything(), "/api/v1/lookups/NO_SUCH_LIST", json("code", "A", "labelAr", "x")).andExpect(status().isNotFound());
        UUID id = UUID.fromString(JsonPath.read(body(get(everything(), "/api/v1/lookups/JUDGMENT_NATURE")), "$[0].id"));
        put(everything(), "/api/v1/lookups/values/" + id, json("labelAr", "x", "active", true, "version", 9))
                .andExpect(status().isConflict());
        put(everything(), "/api/v1/lookups/values/" + UUID.randomUUID(), json("labelAr", "x", "active", true, "version", 0))
                .andExpect(status().isNotFound());
        post(everything(), "/api/v1/lookups/values/" + UUID.randomUUID() + "/approve", "{}").andExpect(status().isNotFound());
        get(everything(), "/api/v1/lookups/values/" + UUID.randomUUID() + "/history").andExpect(status().isNotFound());
    }

    @Test
    void managingListsNeedsLookupManage() throws Exception {
        post(with(Permission.CASE_VIEW), "/api/v1/lookups/JUDGMENT_TYPE", json("code", "A", "labelAr", "x"))
                .andExpect(status().isForbidden());
        UUID id = UUID.fromString(JsonPath.read(body(get(everything(), "/api/v1/lookups/JUDGMENT_NATURE")), "$[0].id"));
        put(with(Permission.CASE_VIEW), "/api/v1/lookups/values/" + id, json("labelAr", "x", "active", true, "version", 0))
                .andExpect(status().isForbidden());
        post(with(Permission.CASE_VIEW), "/api/v1/lookups/values/" + id + "/approve", "{}").andExpect(status().isForbidden());
    }
}
