package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/** LD-004 migrations and test isolation: QA-0025 (happy path), QA-0032 (readiness reflects the database). */
@SpringBootTest
class DatabaseMigrationTest extends PostgresTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    @Autowired
    HealthEndpointGroups healthGroups;

    @Test
    void baselineMigrationIsApplied() {
        Boolean applied = jdbc.queryForObject(
                "select success from flyway_schema_history where version = '1'", Boolean.class);

        assertThat(applied).isTrue();
    }

    @Test
    void hibernateValidatesTheSchemaInsteadOfChangingIt() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }

    @Test
    void runsAgainstPostgresql18() {
        Integer version = jdbc.queryForObject("select current_setting('server_version_num')::int", Integer.class);

        assertThat(version).isBetween(180000, 189999);
    }

    @Test
    void eachTestContextUsesItsOwnDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).startsWith("lawdesk_test_");
    }

    @Test
    void readinessDependsOnTheDatabaseButLivenessDoesNot() {
        assertThat(healthGroups.get("readiness").isMember("db")).isTrue();
        assertThat(healthGroups.get("liveness").isMember("db")).isFalse();
    }
}
