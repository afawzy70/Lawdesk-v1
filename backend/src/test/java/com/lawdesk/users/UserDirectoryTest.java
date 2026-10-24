package com.lawdesk.users;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * architecture/CONTRACTS.md §8: Phase 5 assigns tasks to users and Phase 6 reports workload per user, but neither may
 * read the users table. {@link UserDirectory} and {@code GET /api/v1/users/directory} give them names only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserDirectoryTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserDirectory directory;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID insertUser(String username, boolean enabled) {
        return jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                username, username + "@example.invalid", "Name " + username, enabled);
    }

    private static String unique() {
        return "dir" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void findsAUserByIdWithoutSecrets() {
        String username = unique();
        UUID id = insertUser(username, true);

        UserDirectory.UserSummary found = directory.find(id).orElseThrow();

        assertThat(found).isEqualTo(new UserDirectory.UserSummary(id, username, "Name " + username, true));
        assertThat(directory.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listsOnlyEnabledUsersForAssignment() throws Exception {
        String active = unique();
        String disabled = unique();
        insertUser(active, true);
        insertUser(disabled, false);

        assertThat(directory.active()).extracting(UserDirectory.UserSummary::username).contains(active)
                .doesNotContain(disabled);
        mvc.perform(get("/api/v1/users/directory").with(as(Permission.TASK_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", Matchers.hasItem(active)))
                .andExpect(jsonPath("$[*].username", Matchers.not(Matchers.hasItem(disabled))))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void theDirectoryNeedsTaskOrReportPermission() throws Exception {
        mvc.perform(get("/api/v1/users/directory").with(as(Permission.REPORT_VIEW))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/directory").with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/directory")).andExpect(status().isUnauthorized());
    }
}
