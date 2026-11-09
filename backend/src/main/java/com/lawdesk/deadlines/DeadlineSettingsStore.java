package com.lawdesk.deadlines;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL of the rules, the official holidays and the reminder lead times (V680__LD-054_deadline_rules_and_holidays.sql). */
@Repository
class DeadlineSettingsStore {

    private static final String RULE_SELECT = """
            select r.id, r.code, r.label, r.period_days, r.count_method, r.counts_start_day, r.roll_forward,
                   r.closed_weekdays, r.legal_source, r.effective_from, r.status, r.approved_by, a.display_name as approved_name,
                   r.approved_at, r.retired_by, r.retired_at, r.version, r.created_at
            from deadline_rule r left join app_user a on a.id = r.approved_by""";

    private static final String HOLIDAY_SELECT = """
            select h.id, h.holiday_on, h.label, h.legal_source, h.status, h.approved_by, a.display_name as approved_name,
                   h.approved_at, h.retired_by, h.retired_at, h.version, h.created_at
            from official_holiday h left join app_user a on a.id = h.approved_by""";

    private final JdbcClient jdbc;

    DeadlineSettingsStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static List<Integer> weekdays(String csv) {
        return csv.isEmpty() ? List.of() : Arrays.stream(csv.split(",")).map(Integer::valueOf).toList();
    }

    static String csv(List<Integer> values) {
        return String.join(",", values.stream().map(String::valueOf).toList());
    }

    // ---- rules ---------------------------------------------------------------------------------------------------

    private static DeadlineRequests.RuleResponse rule(java.sql.ResultSet rs) throws java.sql.SQLException {
        OffsetDateTime approvedAt = rs.getObject("approved_at", OffsetDateTime.class);
        OffsetDateTime retiredAt = rs.getObject("retired_at", OffsetDateTime.class);
        return new DeadlineRequests.RuleResponse(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("label"),
                rs.getInt("period_days"), rs.getString("count_method"), rs.getBoolean("counts_start_day"),
                rs.getBoolean("roll_forward"), weekdays(rs.getString("closed_weekdays")), rs.getString("legal_source"),
                rs.getObject("effective_from", LocalDate.class), rs.getString("status"),
                rs.getObject("approved_by", UUID.class), rs.getString("approved_name"),
                approvedAt == null ? null : approvedAt.toInstant(), rs.getObject("retired_by", UUID.class),
                retiredAt == null ? null : retiredAt.toInstant(), rs.getLong("version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    Optional<DeadlineRequests.RuleResponse> rule(UUID id) {
        return jdbc.sql(RULE_SELECT + " where r.id = :id").param("id", id).query((rs, n) -> rule(rs)).optional();
    }

    List<DeadlineRequests.RuleResponse> rules(String status, int limit, long offset) {
        return jdbc.sql(RULE_SELECT + " where (cast(:status as varchar) is null or r.status = :status)"
                        + " order by r.code, r.effective_from desc, r.id limit :limit offset :offset")
                .param("status", status).param("limit", limit).param("offset", offset)
                .query((rs, n) -> rule(rs)).list();
    }

    long countRules(String status) {
        return jdbc.sql("select count(*) from deadline_rule where (cast(:status as varchar) is null or status = :status)")
                .param("status", status).query(Long.class).single();
    }

    boolean approvedRuleExists() {
        return jdbc.sql("select count(*) from deadline_rule where status = 'APPROVED'").query(Long.class).single() > 0;
    }

    boolean ruleKeyTaken(String code, LocalDate effectiveFrom, UUID except) {
        return jdbc.sql("select count(*) from deadline_rule where code = :code and effective_from = :from"
                        + " and (cast(:except as uuid) is null or id <> :except)")
                .param("code", code).param("from", effectiveFrom).param("except", except).query(Long.class).single() > 0;
    }

    UUID insertRule(DeadlineRequests.RuleInput r, List<Integer> closed, UUID actor) {
        return jdbc.sql("""
                        insert into deadline_rule (code, label, period_days, count_method, counts_start_day, roll_forward,
                            closed_weekdays, legal_source, effective_from, created_by, updated_by)
                        values (:code, :label, :period, :method, :countsStart, :roll, :closed, :source, :from, :actor, :actor)
                        returning id""")
                .param("code", r.code()).param("label", r.label()).param("period", r.periodDays())
                .param("method", r.countMethod().name()).param("countsStart", r.countsStartDay())
                .param("roll", r.rollForward()).param("closed", csv(closed)).param("source", r.legalSource())
                .param("from", r.effectiveFrom()).param("actor", actor).query(UUID.class).single();
    }

    boolean updateRule(UUID id, DeadlineRequests.RuleInput r, List<Integer> closed, UUID actor) {
        return jdbc.sql("""
                        update deadline_rule set code = :code, label = :label, period_days = :period,
                            count_method = :method, counts_start_day = :countsStart, roll_forward = :roll,
                            closed_weekdays = :closed, legal_source = :source, effective_from = :from,
                            version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version and status = 'DRAFT'""")
                .param("id", id).param("version", r.version()).param("code", r.code()).param("label", r.label())
                .param("period", r.periodDays()).param("method", r.countMethod().name())
                .param("countsStart", r.countsStartDay()).param("roll", r.rollForward()).param("closed", csv(closed))
                .param("source", r.legalSource()).param("from", r.effectiveFrom()).param("actor", actor).update() == 1;
    }

    boolean approveRule(UUID id, long version, UUID actor) {
        return jdbc.sql("""
                        update deadline_rule set status = 'APPROVED', approved_by = :actor, approved_at = now(),
                            version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version and status = 'DRAFT'""")
                .param("id", id).param("version", version).param("actor", actor).update() == 1;
    }

    boolean retireRule(UUID id, long version, UUID actor) {
        return jdbc.sql("""
                        update deadline_rule set status = 'RETIRED', retired_by = :actor, retired_at = now(),
                            version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version and status = 'APPROVED'""")
                .param("id", id).param("version", version).param("actor", actor).update() == 1;
    }

    // ---- holidays ------------------------------------------------------------------------------------------------

    private static DeadlineRequests.HolidayResponse holiday(java.sql.ResultSet rs) throws java.sql.SQLException {
        OffsetDateTime approvedAt = rs.getObject("approved_at", OffsetDateTime.class);
        OffsetDateTime retiredAt = rs.getObject("retired_at", OffsetDateTime.class);
        return new DeadlineRequests.HolidayResponse(rs.getObject("id", UUID.class), rs.getObject("holiday_on", LocalDate.class),
                rs.getString("label"), rs.getString("legal_source"), rs.getString("status"),
                rs.getObject("approved_by", UUID.class), rs.getString("approved_name"),
                approvedAt == null ? null : approvedAt.toInstant(), rs.getObject("retired_by", UUID.class),
                retiredAt == null ? null : retiredAt.toInstant(), rs.getLong("version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    Optional<DeadlineRequests.HolidayResponse> holiday(UUID id) {
        return jdbc.sql(HOLIDAY_SELECT + " where h.id = :id").param("id", id).query((rs, n) -> holiday(rs)).optional();
    }

    List<DeadlineRequests.HolidayResponse> holidays(String status, Integer year, int limit, long offset) {
        return jdbc.sql(HOLIDAY_SELECT + " where (cast(:status as varchar) is null or h.status = :status)"
                        + " and (cast(:year as integer) is null or extract(year from h.holiday_on) = :year)"
                        + " order by h.holiday_on, h.id limit :limit offset :offset")
                .param("status", status).param("year", year).param("limit", limit).param("offset", offset)
                .query((rs, n) -> holiday(rs)).list();
    }

    long countHolidays(String status, Integer year) {
        return jdbc.sql("select count(*) from official_holiday where (cast(:status as varchar) is null or status = :status)"
                        + " and (cast(:year as integer) is null or extract(year from holiday_on) = :year)")
                .param("status", status).param("year", year).query(Long.class).single();
    }

    boolean holidayDateTaken(LocalDate on, UUID except) {
        return jdbc.sql("select count(*) from official_holiday where holiday_on = :on and status <> 'RETIRED'"
                        + " and (cast(:except as uuid) is null or id <> :except)")
                .param("on", on).param("except", except).query(Long.class).single() > 0;
    }

    UUID insertHoliday(DeadlineRequests.HolidayInput h, UUID actor) {
        return jdbc.sql("""
                        insert into official_holiday (holiday_on, label, legal_source, created_by, updated_by)
                        values (:on, :label, :source, :actor, :actor) returning id""")
                .param("on", h.holidayOn()).param("label", h.label()).param("source", h.legalSource())
                .param("actor", actor).query(UUID.class).single();
    }

    boolean updateHoliday(UUID id, DeadlineRequests.HolidayInput h, UUID actor) {
        return jdbc.sql("""
                        update official_holiday set holiday_on = :on, label = :label, legal_source = :source,
                            version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version and status = 'DRAFT'""")
                .param("id", id).param("version", h.version()).param("on", h.holidayOn()).param("label", h.label())
                .param("source", h.legalSource()).param("actor", actor).update() == 1;
    }

    boolean approveHoliday(UUID id, long version, UUID actor) {
        return jdbc.sql("""
                        update official_holiday set status = 'APPROVED', approved_by = :actor, approved_at = now(),
                            version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version and status = 'DRAFT'""")
                .param("id", id).param("version", version).param("actor", actor).update() == 1;
    }

    boolean retireHoliday(UUID id, long version, UUID actor) {
        return jdbc.sql("""
                        update official_holiday set status = 'RETIRED', retired_by = :actor, retired_at = now(),
                            version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version and status = 'APPROVED'""")
                .param("id", id).param("version", version).param("actor", actor).update() == 1;
    }

    /** The approved holidays by date, for the arithmetic. */
    Map<LocalDate, String> approvedHolidays() {
        Map<LocalDate, String> out = new LinkedHashMap<>();
        jdbc.sql("select holiday_on, label from official_holiday where status = 'APPROVED' order by holiday_on")
                .query((rs, n) -> out.put(rs.getObject("holiday_on", LocalDate.class), rs.getString("label"))).list();
        return out;
    }

    // ---- reminder lead times -------------------------------------------------------------------------------------

    /** The configured lead days, or empty when nobody configured them. */
    Optional<List<Integer>> leadDays() {
        return jdbc.sql("select lead_days from deadline_reminder_setting").query(String.class).optional()
                .map(DeadlineSettingsStore::weekdays);
    }

    Optional<java.time.Instant> leadDaysUpdatedAt() {
        return jdbc.sql("select updated_at from deadline_reminder_setting")
                .query((rs, n) -> rs.getObject("updated_at", OffsetDateTime.class).toInstant()).optional();
    }

    void saveLeadDays(List<Integer> days, UUID actor) {
        jdbc.sql("""
                        insert into deadline_reminder_setting (id, lead_days, updated_by) values (true, :days, :actor)
                        on conflict (id) do update set lead_days = excluded.lead_days, updated_at = now(),
                            updated_by = excluded.updated_by""")
                .param("days", csv(days)).param("actor", actor).update();
    }

    static List<Integer> sorted(List<Integer> values) {
        List<Integer> out = new ArrayList<>(values.stream().distinct().sorted().toList());
        return out;
    }
}
