package com.lawdesk.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lawdesk.platform.PostgresTestSupport;

/** LD-DOC-04: everyone who works with cases reads the library; those who handle and supervise cases add to it. */
@SpringBootTest
class LibraryDefaultRolesTest extends PostgresTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    private List<String> libraryPermissionsOf(String role) {
        return jdbc.queryForList("""
                select rp.permission from role_permission rp join app_role r on r.id = rp.role_id
                where r.code = ? and rp.permission like 'LIBRARY%' order by rp.permission""", String.class, role);
    }

    @Test
    void managingRolesHoldBothPermissions() {
        for (String role : List.of("ADMIN", "LEGAL_MANAGER", "SECTION_HEAD", "LEGAL_STAFF")) {
            assertThat(libraryPermissionsOf(role)).as(role).containsExactly("LIBRARY_MANAGE", "LIBRARY_VIEW");
        }
    }

    @Test
    void theOtherRolesOnlyRead() {
        for (String role : List.of("DATA_ENTRY", "ARCHIVIST", "READ_ONLY")) {
            assertThat(libraryPermissionsOf(role)).as(role).containsExactly("LIBRARY_VIEW");
        }
    }
}
