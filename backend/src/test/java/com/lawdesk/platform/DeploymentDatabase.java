package com.lawdesk.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * A fresh database prepared the way the deployment prepares the real one (the real scripts of deploy/postgres and the real
 * migration job), so tests judge the credentials the deployed backend actually uses.
 */
final class DeploymentDatabase {

    static final String RUNTIME = "lawdesk_app";
    static final String PASSWORD = UUID.randomUUID().toString();

    final PGSimpleDataSource admin;

    private DeploymentDatabase(PGSimpleDataSource admin) {
        this.admin = admin;
        admin.setCurrentSchema("lawdesk");
    }

    /** A fresh installation, exactly as deploy.sh prepares it: roles and ownership, migrations as the owner, ownership again. */
    static DeploymentDatabase deploy() {
        DeploymentDatabase database = new DeploymentDatabase((PGSimpleDataSource) PostgresTestSupport.freshDatabase());
        database.split();
        database.migrateAsOwner();
        database.applyOwnership();
        return database;
    }

    /** An installation from before the split: deploy/postgres/app-role.sql, and migrations run by the runtime role itself. */
    static DeploymentDatabase legacy() {
        DeploymentDatabase database = new DeploymentDatabase((PGSimpleDataSource) PostgresTestSupport.freshDatabase());
        database.legacyRole();
        Flyway.configure().dataSource(database.as(RUNTIME)).locations("classpath:db/migration").outOfOrder(true)
                .target("845").load().migrate();
        return database;
    }

    /** deploy/postgres/roles.sql and ownership.sql, then the passwords (passwords.sql needs psql). */
    void split() {
        runScript("roles.sql");
        runScript("ownership.sql");
        execute(admin, "ALTER ROLE " + RUNTIME + " PASSWORD '" + PASSWORD + "'", "ALTER ROLE lawdesk_owner PASSWORD '" + PASSWORD + "'");
    }

    void applyOwnership() {
        runScript("ownership.sql");
    }

    /** The production migration job, as the owner. */
    void migrateAsOwner() {
        PGSimpleDataSource owner = as("lawdesk_owner");
        MigrateOnly.run("--spring.datasource.url=" + owner.getUrl(), "--spring.datasource.username=lawdesk_owner",
                "--spring.datasource.password=" + PASSWORD, "--spring.datasource.hikari.minimum-idle=0");
    }

    /** deploy/postgres/audit.sql; throws with its list of findings. */
    void audit() {
        runScript("audit.sql");
    }

    void runScript(String name) {
        execute(admin, read("deploy/postgres/" + name));
    }

    /** The statements of deploy/postgres/app-role.sql (that file needs psql: it reads a secret and switches database). */
    private void legacyRole() {
        String name = catalog();
        execute(admin,
                "DO $$ BEGIN CREATE ROLE " + RUNTIME + "; EXCEPTION WHEN duplicate_object THEN NULL; END $$",
                "ALTER ROLE " + RUNTIME + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '" + PASSWORD + "'",
                "REVOKE ALL ON DATABASE " + name + " FROM PUBLIC",
                "GRANT CONNECT, TEMPORARY ON DATABASE " + name + " TO " + RUNTIME,
                "REVOKE ALL ON SCHEMA public FROM PUBLIC",
                "CREATE SCHEMA IF NOT EXISTS lawdesk AUTHORIZATION " + RUNTIME,
                "ALTER ROLE " + RUNTIME + " SET search_path = lawdesk");
    }

    PGSimpleDataSource as(String role) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(admin.getUrl());
        dataSource.setUser(role);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    String catalog() {
        try (Connection connection = admin.getConnection()) {
            return connection.getCatalog();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    static void execute(PGSimpleDataSource dataSource, String... statements) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    static Path repository(String relative) {
        Path path = Path.of("..", relative);
        return Files.exists(path) ? path : Path.of(relative);
    }

    static String read(String relative) {
        try {
            return Files.readString(repository(relative));
        } catch (IOException e) {
            throw new IllegalStateException(relative + " is missing", e);
        }
    }
}
