package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * LD-004 migration safety: QA-0030 (a failing migration leaves no partial schema) and rollback compatibility for
 * LD-006 (after a deployment rollback, the previous release must still start against a database that already has
 * the newer release's migrations). Changes the schema, so the context is discarded afterwards.
 */
@SpringBootTest
@DirtiesContext
class MigrationSafetyTest extends PostgresTestSupport {

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway applicationFlyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void failingMigrationIsRolledBackCompletely() {
        Flyway withBrokenMigration = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/broken-test").load();

        assertThatThrownBy(withBrokenMigration::migrate).isInstanceOf(FlywayException.class);

        assertThat(jdbc.queryForObject("select to_regclass('broken_half_applied') is null", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '9998' and success", Integer.class))
                .isZero();
    }

    @Test
    void previousReleaseStillValidatesAfterNewerMigrationsWereApplied() {
        Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/future-test").load().migrate();

        Flyway previousRelease = Flyway.configure()
                .configuration(applicationFlyway.getConfiguration())
                .locations("classpath:db/migration")
                .load();

        assertThatCode(previousRelease::validate).doesNotThrowAnyException();
    }
}
