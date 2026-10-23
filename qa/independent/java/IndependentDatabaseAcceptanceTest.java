package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** QA-owned tests copied ONLY into the disposable candidate snapshot. */
@SpringBootTest
class IndependentDatabaseAcceptanceTest extends PostgresTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @AfterEach
    void removeSyntheticRows() {
        jdbc.update("delete from app_release where version like 'QA-INDEPENDENT-%' or version in (?, ?, ?, ?)",
                "\t", "\n", "\r\n", "\u2003");
    }

    @Test
    void migrationIsAppliedOnceAndNoFailedHistoryRemains() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='1' and success", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where not success", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select current_setting('server_version_num')::int", Integer.class)).isBetween(180000,189999);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\t", "\n", "\r\n", "\u2003"})
    void databaseRejectsWhitespaceOnlyVersionLikeBeanValidation(String blank) {
        // LD-004 AC3: database must reject the SAME blank inputs as @NotBlank.
        assertThatThrownBy(() -> jdbc.update("insert into app_release(version) values (?)", blank))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sixtyFourCharactersAcceptedAndSixtyFiveRejected() {
        String prefix = "QA-INDEPENDENT-";
        String atLimit = prefix + "x".repeat(64-prefix.length());
        jdbc.update("insert into app_release(version) values (?)", atLimit);
        assertThat(jdbc.queryForObject("select count(*) from app_release where version=?", Integer.class, atLimit)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("insert into app_release(version) values (?)",atLimit+"x"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectedSecondWriteRollsBackFirstWrite() {
        String version="QA-INDEPENDENT-ATOMIC";
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("insert into app_release(version) values (?)",version);
            jdbc.update("insert into app_release(version) values (null)");
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from app_release where version=?",Integer.class,version)).isZero();
    }
}
