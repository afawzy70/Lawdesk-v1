package com.lawdesk.platform;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Clean-up of the LD-051 tables (claims, defences, facts, evidence). Test support classes call it before they delete
 * judgments, proceedings, documents, tasks or cases, which these tables reference. Claim revisions are append-only in the
 * database, so their trigger is switched off for the delete only.
 */
public final class PleadingsTestData {

    private PleadingsTestData() {
    }

    public static void deleteAll(JdbcTemplate jdbc) {
        ArchiveTestData.deleteAll(jdbc);
        jdbc.update("update judgment set copy_document_id = null where copy_document_id is not null");
        jdbc.update("delete from gap_followup");
        jdbc.update("delete from evidence_link");
        jdbc.update("delete from evidence_item");
        jdbc.update("delete from case_fact");
        jdbc.update("delete from defense_precedent");
        jdbc.update("delete from defense_position");
        jdbc.update("delete from defense_assertion");
        jdbc.update("delete from defense");
        jdbc.update("delete from defense_template");
        jdbc.update("delete from claim_result");
        jdbc.execute("alter table claim_revision disable trigger claim_revision_no_change");
        try {
            jdbc.update("delete from claim_revision");
        } finally {
            jdbc.execute("alter table claim_revision enable trigger claim_revision_no_change");
        }
        jdbc.update("delete from claim");
    }

    /** Removes the managed-list values tests added; call it after the judgments that use them are gone. */
    public static void deleteTestLookups(JdbcTemplate jdbc) {
        jdbc.update("""
                delete from lookup_value_history where value_id in
                (select id from lookup_value where created_by <> '00000000-0000-4000-8000-000000000000')""");
        jdbc.update("delete from lookup_value where created_by <> '00000000-0000-4000-8000-000000000000'");
    }
}
