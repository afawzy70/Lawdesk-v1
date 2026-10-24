package com.lawdesk.experts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** The referral and session tables of V404. */
@Repository
class ReferralStore {

    record Row(UUID id, UUID caseId, UUID proceedingId, UUID expertId, LocalDate referralDate, String mission,
            LocalDate reportDueDate, ReferralStatus status, LocalDate reportSubmittedDate, long version,
            Instant createdAt, Instant updatedAt) {
    }

    private static final String COLUMNS = """
            id, case_id, proceeding_id, expert_id, referral_date, mission, report_due_date, status,
            report_submitted_date, version, created_at, updated_at""";

    private final NamedParameterJdbcTemplate jdbc;

    ReferralStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Row insert(UUID caseId, UUID proceedingId, UUID expertId, LocalDate referralDate, String mission,
            LocalDate reportDueDate, UUID actor) {
        return jdbc.queryForObject("""
                insert into expert_referral (case_id, proceeding_id, expert_id, referral_date, mission, report_due_date,
                                             created_by, updated_by)
                values (:caseId, :proceedingId, :expertId, :referralDate, :mission, :reportDueDate, :actor, :actor)
                returning""" + " " + COLUMNS, new MapSqlParameterSource().addValue("caseId", caseId)
                .addValue("proceedingId", proceedingId).addValue("expertId", expertId)
                .addValue("referralDate", referralDate).addValue("mission", mission)
                .addValue("reportDueDate", reportDueDate).addValue("actor", actor), (rs, n) -> row(rs));
    }

    Optional<Row> find(UUID id) {
        return jdbc.query("select " + COLUMNS + " from expert_referral where id = :id",
                new MapSqlParameterSource("id", id), (rs, n) -> row(rs)).stream().findFirst();
    }

    /** The referral, row-locked until the transaction ends, so its writes are serialised. */
    Optional<Row> lock(UUID id) {
        return jdbc.query("select " + COLUMNS + " from expert_referral where id = :id for update",
                new MapSqlParameterSource("id", id), (rs, n) -> row(rs)).stream().findFirst();
    }

    List<Row> forCase(UUID caseId) {
        return jdbc.query("select " + COLUMNS + " from expert_referral where case_id = :caseId"
                + " order by referral_date, created_at, id", new MapSqlParameterSource("caseId", caseId),
                (rs, n) -> row(rs));
    }

    /** Applies one change and bumps the version; call it with the row locked and the version checked. */
    Row change(UUID id, String mission, LocalDate reportDueDate, ReferralStatus status, LocalDate reportSubmittedDate,
            UUID actor) {
        return jdbc.queryForObject("""
                update expert_referral set mission = :mission, report_due_date = :reportDueDate, status = :status,
                       report_submitted_date = :reportSubmittedDate, version = version + 1, updated_at = now(),
                       updated_by = :actor
                where id = :id returning""" + " " + COLUMNS, new MapSqlParameterSource().addValue("id", id)
                .addValue("mission", mission).addValue("reportDueDate", reportDueDate)
                .addValue("status", status.name()).addValue("reportSubmittedDate", reportSubmittedDate)
                .addValue("actor", actor), (rs, n) -> row(rs));
    }

    void addSession(UUID referralId, LocalDate date, LocalTime time, String location, String notes, UUID actor) {
        jdbc.update("""
                insert into expert_session (referral_id, session_date, session_time, location, notes, created_by)
                values (:referralId, :date, :time, :location, :notes, :actor)""", new MapSqlParameterSource()
                .addValue("referralId", referralId).addValue("date", date).addValue("time", time)
                .addValue("location", location).addValue("notes", notes).addValue("actor", actor));
    }

    /** The sessions of these referrals, oldest first. */
    List<SessionRow> sessions(Collection<UUID> referralIds) {
        if (referralIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                select id, referral_id, session_date, session_time, location, notes, created_at from expert_session
                where referral_id in (:ids) order by session_date, session_time nulls first, created_at, id""",
                new MapSqlParameterSource("ids", referralIds),
                (rs, n) -> new SessionRow(rs.getObject("referral_id", UUID.class), new ExpertRequests.Session(
                        rs.getObject("id", UUID.class), rs.getObject("session_date", LocalDate.class),
                        rs.getObject("session_time", LocalTime.class), rs.getString("location"),
                        rs.getString("notes"), rs.getTimestamp("created_at").toInstant())));
    }

    record SessionRow(UUID referralId, ExpertRequests.Session session) {
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("proceeding_id", UUID.class), rs.getObject("expert_id", UUID.class),
                rs.getObject("referral_date", LocalDate.class), rs.getString("mission"),
                rs.getObject("report_due_date", LocalDate.class), ReferralStatus.valueOf(rs.getString("status")),
                rs.getObject("report_submitted_date", LocalDate.class), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
