package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The tables that later milestones protect with database guards (T-DB-15 and the trigger-order requirement). PostgreSQL runs
 * referential actions as the table owner, and a BEFORE trigger that sorts later can change a row after a guard checked it, so a new
 * foreign key action or trigger on these tables must be reviewed: this test fails until the expectation is changed on purpose.
 */
class GuardedTableCatalogTest {

    private static final String GUARDED = "('judgment'::regclass, 'deadline'::regclass, 'legal_case'::regclass,"
            + " 'role_permission'::regclass, 'case_grant'::regclass, 'case_document'::regclass, 'case_reference_counter'::regclass)";

    private final JdbcTemplate jdbc = new JdbcTemplate(DeploymentDatabase.deploy().admin);

    private List<String> rows(String sql) {
        return jdbc.queryForList(sql, String.class);
    }

    @Test
    void onlyTheReviewedReferentialActionsWriteIntoGuardedTables() {
        assertThat(rows("select conrelid::regclass::text || ' ' || conname || ' delete=' || confdeltype::text || ' update=' || confupdtype::text"
                + " from pg_constraint where contype = 'f' and (confdeltype in ('c','n','d') or confupdtype in ('c','n','d'))"
                + " and conrelid in " + GUARDED + " order by 1"))
                .extracting(line -> line.replace("lawdesk.", ""))
                .containsExactly("deadline deadline_proof_document_id_fkey delete=n update=a",
                        "role_permission role_permission_role_id_fkey delete=c update=a");
    }

    @Test
    void onlyTheReviewedTriggersRunOnGuardedTablesInThisOrder() {
        assertThat(rows("select tgrelid::regclass::text || ' ' || (case when tgtype & 2 <> 0 then 'BEFORE' else 'AFTER' end) || ' ' || tgname"
                + " || ' ' || tgenabled::text from pg_trigger where not tgisinternal and tgrelid in " + GUARDED + " order by tgrelid::regclass::text, tgname"))
                .extracting(line -> line.replace("lawdesk.", ""))
                .containsExactly("case_document BEFORE case_document_purge_guard O",
                        "case_document BEFORE search_vec_case_document O",
                        "case_reference_counter BEFORE case_reference_counter_guard O",
                        "judgment BEFORE judgment_appeal_deadline_runs_from_judgment O",
                        "judgment BEFORE search_vec_judgment O",
                        "legal_case AFTER search_case_insert O",
                        "legal_case AFTER search_case_update O");
    }

    @Test
    void noFunctionOfTheSchemaRunsWithItsOwnersRights() {
        assertThat(rows("select proname::text from pg_proc where pronamespace = 'lawdesk'::regnamespace and prosecdef")).isEmpty();
    }
}
