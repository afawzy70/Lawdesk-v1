package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Independent QAD-008 upgrade verification. Each test owns its isolated schema. */
@SpringBootTest
class IndependentMigrationUpgradeTest extends PostgresTestSupport {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void validV1DataSurvivesV2AndRerunDoesNotDuplicateHistory() {
        String schema="qa_upgrade_"+UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("1").load().migrate();
            jdbc.update("insert into "+schema+".app_release(version) values (?)", "QA-UPGRADE-VALID");
            Flyway latest=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            latest.migrate(); latest.migrate();
            assertThat(jdbc.queryForObject("select count(*) from "+schema+".flyway_schema_history where version='2' and success",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from "+schema+".app_release where version='QA-UPGRADE-VALID'",Integer.class)).isEqualTo(1);
        } finally {
            jdbc.execute("drop schema if exists "+schema+" cascade");
        }
    }

    @Test
    void invalidV1DataRefusesV2AtomicallyWithoutDeletingTheRow() {
        String schema="qa_upgrade_"+UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("1").load().migrate();
            jdbc.update("insert into "+schema+".app_release(version) values (?)", "\t");
            Flyway latest=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            assertThatThrownBy(latest::migrate).isInstanceOf(FlywayException.class);
            assertThat(jdbc.queryForObject("select count(*) from "+schema+".flyway_schema_history where version='2' and success",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from "+schema+".app_release where version=?",Integer.class,"\t")).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname=? and c.conname='app_release_version_check'",Integer.class,schema)).isEqualTo(1);
        } finally {
            jdbc.execute("drop schema if exists "+schema+" cascade");
        }
    }
}
