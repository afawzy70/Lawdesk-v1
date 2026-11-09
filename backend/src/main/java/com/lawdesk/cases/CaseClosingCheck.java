package com.lawdesk.cases;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * What is still open when a case is about to be closed (requirements section 33, and section 23 "the case is not over
 * merely because a settlement offer was recorded"). Closing never deletes anything and stays a manual decision, so these
 * are warnings, never blocks: the member sees them and closes with an explicit acknowledgement, which is recorded. Only
 * counts are given, so the check shows nothing of the records themselves to a user who cannot read them.
 *
 * <p>The findings: a settlement still proposed, under review or approved but not executed; deadlines still open; hearings
 * still scheduled for today or later; open tasks; information requests awaiting an answer; an enforcement file in progress.
 */
@Component
class CaseClosingCheck {

    /** One kind of open item and how many there are. */
    record Finding(String code, long count) {
    }

    record Result(UUID caseId, List<Finding> findings, boolean hasWarnings) {
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Kuwait");

    private final JdbcClient jdbc;

    CaseClosingCheck(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Result of(UUID caseId) {
        List<Finding> findings = new ArrayList<>();
        add(findings, "UNFINISHED_SETTLEMENT", "select count(*) from settlement where case_id = :id"
                + " and status in ('PROPOSED', 'UNDER_REVIEW', 'APPROVED')", caseId, null);
        add(findings, "OPEN_DEADLINE", "select count(*) from deadline where case_id = :id and status = 'OPEN'", caseId, null);
        add(findings, "SCHEDULED_HEARING", "select count(*) from hearing where case_id = :id and status = 'SCHEDULED'"
                + " and hearing_date >= :today", caseId, LocalDate.now(ZONE));
        add(findings, "OPEN_TASK", "select count(*) from task where case_id = :id and status in ('NEW', 'IN_PROGRESS',"
                + " 'WAITING_DOCUMENTS', 'WAITING_OTHER', 'LATE')", caseId, null);
        add(findings, "PENDING_INFO_REQUEST", "select count(*) from info_request where case_id = :id and status in ('SENT',"
                + " 'AWAITING', 'PARTIAL', 'INCOMPLETE', 'OVERDUE')", caseId, null);
        add(findings, "ENFORCEMENT_IN_PROGRESS", "select count(*) from enforcement_file where case_id = :id and status in"
                + " ('IN_PROGRESS', 'PARTIALLY_EXECUTED', 'OBSTRUCTED')", caseId, null);
        return new Result(caseId, List.copyOf(findings), !findings.isEmpty());
    }

    private void add(List<Finding> into, String code, String sql, UUID caseId, LocalDate today) {
        var query = jdbc.sql(sql).param("id", caseId);
        if (today != null) {
            query = query.param("today", today);
        }
        Long count = query.query(Long.class).single();
        if (count != null && count > 0) {
            into.add(new Finding(code, count));
        }
    }
}
