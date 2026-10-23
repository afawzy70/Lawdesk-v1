package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * architecture/CONTRACTS.md §2: each workstream owns a version range, so a lower version can reach develop after a
 * higher one. The application runs Flyway out of order, and a rollback still ignores future versions.
 */
@SpringBootTest
class MigrationOrderingTest extends PostgresTestSupport {

    @Autowired
    private Flyway flyway;

    @Test
    void theApplicationAppliesMigrationsOutOfOrderAndIgnoresFutureOnes() {
        assertThat(flyway.getConfiguration().isOutOfOrder()).isTrue();
        assertThat(flyway.getConfiguration().getIgnoreMigrationPatterns())
                .extracting(Object::toString).contains("*:future");
    }

    @Test
    void aLowerVersionMergedAfterAHigherOneIsStillApplied() {
        DataSource database = freshDatabase();
        configured(database, "classpath:db/migration", "classpath:db/ordering/high").migrate();

        var result = configured(database, "classpath:db/migration", "classpath:db/ordering/high",
                "classpath:db/ordering/low").migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrations).extracting(m -> m.version).containsExactly("150");
    }

    private Flyway configured(DataSource database, String... locations) {
        return Flyway.configure().configuration(flyway.getConfiguration()).dataSource(database)
                .locations(locations).load();
    }
}
