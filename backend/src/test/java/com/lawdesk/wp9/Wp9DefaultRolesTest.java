package com.lawdesk.wp9;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lawdesk.platform.PostgresTestSupport;

/**
 * LD-DOC-04: who holds the permissions of settlements, directives, templates and search by default. Specialists propose
 * settlements, supervisors approve them and issue directives; everyone who reads cases may search across every type.
 */
@SpringBootTest
class Wp9DefaultRolesTest extends PostgresTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    private List<String> wp9PermissionsOf(String role) {
        return jdbc.queryForList("""
                select rp.permission from role_permission rp join app_role r on r.id = rp.role_id
                where r.code = ? and rp.permission in ('SETTLEMENT_MANAGE', 'SETTLEMENT_APPROVE', 'DIRECTIVE_MANAGE',
                    'TEMPLATE_MANAGE', 'SEARCH_ALL') order by rp.permission""", String.class, role);
    }

    @Test
    void supervisorsHoldAllFive() {
        for (String role : List.of("ADMIN", "LEGAL_MANAGER", "SECTION_HEAD")) {
            assertThat(wp9PermissionsOf(role)).as(role).containsExactly("DIRECTIVE_MANAGE", "SEARCH_ALL", "SETTLEMENT_APPROVE",
                    "SETTLEMENT_MANAGE", "TEMPLATE_MANAGE");
        }
    }

    @Test
    void aSpecialistProposesAndSearchesButDoesNotApproveIssueOrManageTemplates() {
        assertThat(wp9PermissionsOf("LEGAL_STAFF")).containsExactly("SEARCH_ALL", "SETTLEMENT_MANAGE");
    }

    @Test
    void aReadOnlyUserSearchesAndTheRestHoldNothingOfThese() {
        assertThat(wp9PermissionsOf("READ_ONLY")).containsExactly("SEARCH_ALL");
        assertThat(wp9PermissionsOf("DATA_ENTRY")).isEmpty();
        assertThat(wp9PermissionsOf("ARCHIVIST")).isEmpty();
    }
}
