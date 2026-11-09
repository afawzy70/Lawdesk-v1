package com.lawdesk.platform;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Clean-up of the LD-055 tables (document links, memos and letters, enforcement) and of the archive itself. The database
 * refuses to delete archived documents outside the deletion procedure (V701), so tests delete them the way the procedure does:
 * inside one statement that first allows it for that transaction.
 */
public final class ArchiveTestData {

    private ArchiveTestData() {
    }

    /** Records that reference claims, judgments, hearings, tasks, cases or documents; call before deleting those. */
    public static void deleteAll(JdbcTemplate jdbc) {
        jdbc.update("delete from document_link");
        jdbc.update("delete from enforcement_action");
        jdbc.update("delete from enforcement_file");
        jdbc.update("delete from legal_document_event");
        jdbc.update("delete from legal_document");
    }

    /** Deletes every archived document, its versions and text, and the deletion log. */
    public static void deleteDocuments(JdbcTemplate jdbc) {
        jdbc.execute("""
                do $$ begin
                    perform set_config('lawdesk.archive_purge', 'on', true);
                    delete from document_deletion_log;
                    delete from case_document;
                end $$""");
        jdbc.update("delete from case_document_quota");
    }
}
