package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** LD-DOC-04: the new permissions reach the roles as designed, and the read-only roles cannot change anything. */
class PleadingsPermissionsTest extends PleadingsTestSupport {

    private List<String> held(String role) {
        return jdbc.queryForList("""
                select p.permission from role_permission p join app_role r on r.id = p.role_id
                where r.code = ? and p.permission in ('CLAIM_VIEW', 'CLAIM_MANAGE', 'DEFENSE_VIEW', 'DEFENSE_MANAGE',
                    'EVIDENCE_VIEW', 'EVIDENCE_MANAGE', 'LOOKUP_MANAGE') order by 1""", String.class, role);
    }

    @Test
    void membersOfTheDepartmentCanViewAndManageTheirCases() {
        for (String role : new String[] {"ADMIN", "LEGAL_MANAGER"}) {
            assertThat(held(role)).as(role).containsExactly("CLAIM_MANAGE", "CLAIM_VIEW", "DEFENSE_MANAGE", "DEFENSE_VIEW",
                    "EVIDENCE_MANAGE", "EVIDENCE_VIEW", "LOOKUP_MANAGE");
        }
        for (String role : new String[] {"SECTION_HEAD", "LEGAL_STAFF"}) {
            assertThat(held(role)).as(role).containsExactly("CLAIM_MANAGE", "CLAIM_VIEW", "DEFENSE_MANAGE", "DEFENSE_VIEW",
                    "EVIDENCE_MANAGE", "EVIDENCE_VIEW");
        }
    }

    @Test
    void dataEntryAndReadOnlyRolesOnlyView() {
        for (String role : new String[] {"DATA_ENTRY", "READ_ONLY"}) {
            assertThat(held(role)).as(role).containsExactly("CLAIM_VIEW", "DEFENSE_VIEW", "EVIDENCE_VIEW");
        }
        assertThat(held("ARCHIVIST")).isEmpty();
    }

    @Test
    void theNewTablesHoldNoRowsUntilUsersAddThem() {
        for (String table : new String[] {"claim", "claim_revision", "claim_result", "defense", "defense_assertion",
            "defense_position", "defense_template", "case_fact", "evidence_item", "evidence_link", "gap_followup"}) {
            assertThat(count("select count(*) from " + table)).as(table).isZero();
        }
        // Nothing is generated from the document: every judgment list value is a proposal (the source names none). Later
        // work packages add lists whose values the source does name (LD-055: the kinds of memos, as DOCUMENT).
        assertThat(count("select count(*) from lookup_value where origin = 'DOCUMENT' and list_code like 'JUDGMENT%'")).isZero();
        assertThat(count("select count(*) from lookup_value where approved_at is not null and list_code like 'JUDGMENT%'")).isZero();
    }
}
