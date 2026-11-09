package com.lawdesk.library;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.library.LibraryViews.ClassificationCount;
import com.lawdesk.library.LibraryViews.Count;
import com.lawdesk.library.LibraryViews.Issue;
import com.lawdesk.library.LibraryViews.Summary;

/**
 * Library reports (requirements section 16.7): active judgments by court, year, direction, finality and classification,
 * and the legal questions principles name most often. Counts only: how settled a line of cases is, is for the legal
 * member to assess from the judgments, never inferred here.
 */
@Service
class LibraryReportService {

    static final String NOTE = "Counts of what is recorded in the library. A count is not a finding that a line of cases is"
            + " settled: read the judgments and their sources.";

    private final JdbcClient jdbc;

    LibraryReportService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    Summary summary() {
        long total = jdbc.sql("select count(*) from library_judgment").query(Long.class).single();
        long active = jdbc.sql("select count(*) from library_judgment where status = 'ACTIVE'").query(Long.class).single();
        List<Count> byCourt = jdbc.sql("select library_normalize(court_name) as k, min(court_name) as label, count(*) as n"
                        + " from library_judgment where status = 'ACTIVE' group by 1 order by n desc, k")
                .query((rs, i) -> new Count(rs.getString("k"), rs.getString("label"), rs.getLong("n"))).list();
        List<Count> byYear = jdbc.sql("select extract(year from judgment_date)::int as y, count(*) as n from library_judgment"
                        + " where status = 'ACTIVE' group by 1 order by y desc nulls last")
                .query((rs, i) -> {
                    int y = rs.getInt("y");
                    String key = rs.wasNull() ? null : Integer.toString(y);
                    return new Count(key, key, rs.getLong("n"));
                }).list();
        List<Count> byDirection = jdbc.sql("select coalesce(direction, 'NOT_SPECIFIED') as k, count(*) as n"
                        + " from library_judgment where status = 'ACTIVE' group by 1 order by n desc, k")
                .query((rs, i) -> new Count(rs.getString("k"), rs.getString("k"), rs.getLong("n"))).list();
        List<Count> byFinality = jdbc.sql("select finality as k, count(*) as n from library_judgment"
                        + " where status = 'ACTIVE' group by 1 order by n desc, k")
                .query((rs, i) -> new Count(rs.getString("k"), rs.getString("k"), rs.getLong("n"))).list();
        List<ClassificationCount> byClassification = jdbc.sql("select c.dimension, c.id, c.code, c.label_ar, c.label_en,"
                        + " count(*) as n from library_judgment_class jc join library_classification c on c.id ="
                        + " jc.classification_id join library_judgment j on j.id = jc.judgment_id where j.status = 'ACTIVE'"
                        + " group by c.dimension, c.id, c.code, c.label_ar, c.label_en, c.sort_order"
                        + " order by c.dimension, n desc, c.sort_order, c.code")
                .query((rs, i) -> new ClassificationCount(rs.getString("dimension"), rs.getObject("id", UUID.class),
                        rs.getString("code"), rs.getString("label_ar"), rs.getString("label_en"), rs.getLong("n"))).list();
        List<Issue> issues = jdbc.sql("select min(p.legal_topic) as topic, count(*) as principles,"
                        + " count(distinct p.judgment_id) as judgments, min(j.judgment_date) as first_date,"
                        + " max(j.judgment_date) as last_date from library_principle p join library_judgment j on j.id ="
                        + " p.judgment_id where j.status = 'ACTIVE' and p.legal_topic is not null"
                        + " group by library_normalize(p.legal_topic) order by principles desc,"
                        + " library_normalize(p.legal_topic) limit 20")
                .query((rs, i) -> new Issue(rs.getString("topic"), rs.getLong("principles"), rs.getLong("judgments"),
                        rs.getObject("first_date", LocalDate.class), rs.getObject("last_date", LocalDate.class))).list();
        return new Summary(total, active, total - active, byCourt, byYear, byDirection, byFinality, byClassification,
                issues, NOTE);
    }
}
