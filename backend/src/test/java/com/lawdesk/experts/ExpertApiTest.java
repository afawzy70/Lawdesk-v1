package com.lawdesk.experts;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/** LD-025 the expert registry (docs/delivery/PHASE-5-ACCEPTANCE.md): part of QA-0193…QA-0199. */
class ExpertApiTest extends ExpertTestSupport {

    static String edit(String name, String specialty, boolean active, long version) {
        return """
                {"name":%s,"specialty":%s,"office":null,"phone":null,"active":%s,"version":%d}""".formatted(
                quote(name), quote(specialty), active, version);
    }

    @Test
    void registersListsAndEditsExperts() throws Exception {
        MvcResult created = mvc.perform(postExpert(expert("  Eng. Samir Fathy ", "Civil engineering",
                        "Ministry of Justice experts office, Giza", "+20 2 1234 5678")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Eng. Samir Fathy"))
                .andExpect(jsonPath("$.specialty").value("Civil engineering"))
                .andExpect(jsonPath("$.office").value("Ministry of Justice experts office, Giza"))
                .andExpect(jsonPath("$.phone").value("+20 2 1234 5678"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        assertThat(created.getResponse().getHeader("Location")).endsWith("/api/v1/experts/" + id);
        UUID other = createExpert("Accountant Hoda Adel");

        mvc.perform(get("/api/v1/experts").with(as(Permission.EXPERT_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(other.toString(), id)))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(put("/api/v1/experts/" + id).with(as(Permission.EXPERT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(edit("Eng. Samir Fathy", "Surveying", false, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialty").value("Surveying"))
                .andExpect(jsonPath("$.office").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/experts").param("active", "true").with(as(Permission.EXPERT_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(other.toString())));
        mvc.perform(get("/api/v1/experts").param("q", "samir").with(as(Permission.EXPERT_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(id)));
        mvc.perform(get("/api/v1/experts/" + id).with(as(Permission.EXPERT_VIEW)))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void validatesTheRegistryFields() throws Exception {
        mvc.perform(postExpert("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder("name", "specialty")));
        mvc.perform(postExpert(expert("n".repeat(201), "s", null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("name"));
        mvc.perform(postExpert(expert("Name", "s".repeat(101), null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("specialty"));
        mvc.perform(postExpert(expert("Name", "Medicine", null, "call me")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.errors[0].code").value("Pattern"));
        mvc.perform(postExpert(expert("n".repeat(200), "s".repeat(100), "  ", null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.office").value(Matchers.nullValue()));
        UUID id = createExpert("Versioned");
        mvc.perform(put("/api/v1/experts/" + id).with(as(Permission.EXPERT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(edit("Versioned", "X", true, 5)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/experts/" + UUID.randomUUID()).with(as(Permission.EXPERT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(edit("Nobody", "X", true, 0)))
                .andExpect(status().isNotFound());
        assertThat(rows("expert")).isEqualTo(2);
    }

    @Test
    void theRegistryNeedsItsPermissions() throws Exception {
        UUID actor = UUID.randomUUID();
        mvc.perform(post("/api/v1/experts").with(as(actor, "viewer-" + actor, Permission.EXPERT_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(expert("A", "B", null, null)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where action = 'ACCESS_DENIED' and actor_id = ?
                and target_id = 'POST /api/v1/experts'""", Integer.class, actor)).isEqualTo(1);
        mvc.perform(get("/api/v1/experts").with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/experts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/experts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/experts/" + UUID.randomUUID()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        assertThat(rows("expert")).isZero();
    }
}
