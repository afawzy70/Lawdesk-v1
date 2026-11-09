package com.lawdesk.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lawdesk.platform.PostgresTestSupport;

/** LD-DOC-04 section 3: the legal assistant is for administrators, managers, section heads and specialists, and for no one else. */
@SpringBootTest
class AssistantDefaultRolesTest extends PostgresTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void onlyTheCaseWorkingRolesHoldTheAssistantPermission() {
        List<String> roles = jdbc.queryForList("""
                select r.code from role_permission rp join app_role r on r.id = rp.role_id
                where rp.permission = 'ASSISTANT_USE' order by r.code""", String.class);
        assertThat(roles).containsExactly("ADMIN", "LEGAL_MANAGER", "LEGAL_STAFF", "SECTION_HEAD");
    }
}
