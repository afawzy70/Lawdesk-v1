package com.lawdesk.platform;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Database fault injection for the audit atomicity tests (QAD-040, QAD-042, QAD-043). Real PostgreSQL triggers, removed
 * again by {@link #clear}: one refuses the SUCCESS audit insert of an action, the other fails the business
 * transaction at commit time (a deferred constraint trigger) after every statement, the audit insert included, ran.
 */
public final class Faults {

    private static final List<String> BUSINESS_TABLES = List.of("app_user", "user_role", "app_role", "role_permission");

    private Faults() {
    }

    /** The SUCCESS audit event of {@code action} cannot be written. */
    public static void failAuditWrite(JdbcTemplate jdbc, String action) {
        jdbc.execute("""
                create or replace function test_refuse_audit() returns trigger language plpgsql as $$
                begin raise exception 'audit write refused' using errcode = '23514'; end $$""");
        jdbc.execute("create trigger test_refuse_audit before insert on audit_event for each row when (new.action = '"
                + action + "' and new.outcome = 'SUCCESS') execute function test_refuse_audit()");
    }

    /** Every transaction that writes a user or role row fails when it commits. */
    public static void failCommit(JdbcTemplate jdbc) {
        jdbc.execute("""
                create or replace function test_refuse_commit() returns trigger language plpgsql as $$
                begin raise exception 'refused at commit' using errcode = '23514'; end $$""");
        for (String table : BUSINESS_TABLES) {
            jdbc.execute("create constraint trigger test_refuse_commit after insert or update or delete on " + table
                    + " deferrable initially deferred for each row execute function test_refuse_commit()");
        }
    }

    public static void clear(JdbcTemplate jdbc) {
        jdbc.execute("drop trigger if exists test_refuse_audit on audit_event");
        for (String table : BUSINESS_TABLES) {
            jdbc.execute("drop trigger if exists test_refuse_commit on " + table);
        }
    }
}
