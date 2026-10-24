package com.lawdesk.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** architecture/CONTRACTS.md §3 and §8: the seeded default roles, including the Phase 5 and 6 permissions. */
@SpringBootTest
class DefaultRolePermissionsTest extends PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private List<String> permissionsOf(String role) {
        return jdbc.queryForList("""
                select rp.permission from role_permission rp join app_role r on r.id = rp.role_id
                where r.code = ? order by rp.permission""", String.class, role);
    }

    @Test
    void theAdministratorHoldsEveryPermission() {
        assertThat(permissionsOf("ADMIN"))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(Permission.values()).map(Enum::name).toList());
    }

    @Test
    void legalManagersRunOperationsAndReports() {
        assertThat(permissionsOf("LEGAL_MANAGER")).contains("HEARING_VIEW", "HEARING_MANAGE", "EXPERT_VIEW",
                "EXPERT_MANAGE", "DOCUMENT_VIEW", "DOCUMENT_MANAGE", "TASK_VIEW", "TASK_MANAGE", "NOTE_MANAGE",
                "REPORT_VIEW", "REPORT_EXPORT");
    }

    @Test
    void legalStaffWorkOnOperationsButSeeNoReportsAndManageNoExperts() {
        assertThat(permissionsOf("LEGAL_STAFF")).contains("HEARING_VIEW", "HEARING_MANAGE", "EXPERT_VIEW",
                "DOCUMENT_VIEW", "DOCUMENT_MANAGE", "TASK_VIEW", "TASK_MANAGE", "NOTE_MANAGE")
                .doesNotContain("EXPERT_MANAGE", "REPORT_VIEW", "REPORT_EXPORT");
    }
}
