package com.lawdesk.proceedings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The read-only port other modules use to reach proceedings (architecture/CONTRACTS.md §8): Phase 5 attaches hearings
 * and expert referrals to them and Phase 6 reports on them. Neither writes Workstream C's tables.
 */
@Component
public class ProceedingLookup {

    public record ProceedingSummary(UUID id, UUID caseId, Stage stage, UUID courtId, String courtCaseNumber,
            int courtCaseYear, LocalDate filingDate, UUID parentProceedingId) {
    }

    private static final String SELECT = """
            select id, case_id, stage, court_id, court_case_number, court_case_year, filing_date, parent_proceeding_id
            from proceeding""";

    private final JdbcTemplate jdbc;

    ProceedingLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The proceeding with this id, or empty when there is none. */
    public Optional<ProceedingSummary> find(UUID id) {
        return jdbc.query(SELECT + " where id = ?", (rs, n) -> summary(rs), id).stream().findFirst();
    }

    /** The proceedings of a case, first instance first, then by filing date. */
    public List<ProceedingSummary> forCase(UUID caseId) {
        return jdbc.query(SELECT + """
                 where case_id = ?
                order by case stage when 'FIRST_INSTANCE' then 0 when 'APPEAL' then 1 else 2 end, filing_date, id""",
                (rs, n) -> summary(rs), caseId);
    }

    private static ProceedingSummary summary(ResultSet rs) throws SQLException {
        return new ProceedingSummary(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                Stage.valueOf(rs.getString("stage")), rs.getObject("court_id", UUID.class),
                rs.getString("court_case_number"), rs.getInt("court_case_year"),
                rs.getObject("filing_date", LocalDate.class), rs.getObject("parent_proceeding_id", UUID.class));
    }
}
