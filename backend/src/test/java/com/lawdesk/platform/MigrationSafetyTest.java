package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * LD-004 migration safety: QA-0030 (a failing migration leaves no partial schema), the QA edge fixture for QA-0025
 * (run migrations twice; two migrators at once on an empty database) and rollback compatibility for
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

    @Test
    void migratingAnUpToDateDatabaseAgainChangesNothing() {
        Flyway flyway = applicationFlywayFor(freshDatabase());

        assertThat(flyway.migrate().migrationsExecuted).isPositive();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void concurrentMigratorsApplyEachMigrationExactlyOnce() {
        DataSource empty = freshDatabase();
        CyclicBarrier start = new CyclicBarrier(2);
        List<CompletableFuture<Integer>> migrators = List.of(1, 2).stream()
                .map(i -> CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return applicationFlywayFor(empty).migrate().migrationsExecuted;
                }))
                .toList();

        int executed = migrators.stream().mapToInt(CompletableFuture::join).sum();

        JdbcTemplate target = new JdbcTemplate(empty);
        int versions = applicationFlywayFor(empty).info().applied().length;
        assertThat(executed).isEqualTo(versions);
        assertThat(target.queryForObject(
                "select count(*) = count(distinct version) from flyway_schema_history where success", Boolean.class))
                .isTrue();
        assertThat(target.queryForObject("select to_regclass('app_release') is not null", Boolean.class)).isTrue();
    }

    @Test
    void upgradingADatabaseWithExistingReleasesKeepsThem() {
        DataSource v1 = freshDatabase();
        Flyway.configure().configuration(applicationFlyway.getConfiguration()).dataSource(v1).target("1").load()
                .migrate();
        new JdbcTemplate(v1).update("insert into app_release (version) values ('0.0.1')");

        applicationFlywayFor(v1).migrate();

        assertThat(new JdbcTemplate(v1).queryForList("select version from app_release", String.class))
                .containsExactly("0.0.1");
    }

    @Test
    void upgradeRefusesAndRollsBackWhenExistingRowsBreakTheTightenedVersionRule() {
        DataSource v1 = freshDatabase();
        Flyway.configure().configuration(applicationFlyway.getConfiguration()).dataSource(v1).target("1").load()
                .migrate();
        JdbcTemplate target = new JdbcTemplate(v1);
        target.update("insert into app_release (version) values (?)", "\t");

        assertThatThrownBy(applicationFlywayFor(v1)::migrate).isInstanceOf(FlywayException.class);

        assertThat(target.queryForObject("select count(*) from app_release", Integer.class)).isOne();
        assertThat(target.queryForObject(
                "select max(version::int) from flyway_schema_history where success", Integer.class)).isOne();
    }

    private Flyway applicationFlywayFor(DataSource target) {
        return Flyway.configure().configuration(applicationFlyway.getConfiguration()).dataSource(target).load();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
