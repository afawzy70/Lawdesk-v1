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
 * A fresh database prepared the way the deployment prepares the real one, so tests judge the credentials the deployed backend
 * actually uses. Today that is deploy/postgres/app-role.sql followed by Flyway as the runtime role itself.
 */
final class DeploymentDatabase {

    static final String RUNTIME = "lawdesk_app";
    static final String PASSWORD = UUID.randomUUID().toString();

    final PGSimpleDataSource admin;

    private DeploymentDatabase(PGSimpleDataSource admin) {
        this.admin = admin;
        admin.setCurrentSchema("lawdesk");
    }

    /** An installation as it exists today: every object owned by the runtime role. */
    static DeploymentDatabase legacy() {
        return deploy();
    }

    /** Role provisioning and ownership transfer: today's deployment has none. */
    void split() {
    }

    void applyOwnership() {
    }

    void migrateAsOwner() {
        migrate();
    }

    /** The deploy-time privilege audit: today's deployment has none, so nothing is ever refused. */
    void audit() {
    }

    static DeploymentDatabase deploy() {
        DeploymentDatabase database = new DeploymentDatabase((PGSimpleDataSource) PostgresTestSupport.freshDatabase());
        database.prepare();
        database.migrate();
        return database;
    }

    /** The statements of deploy/postgres/app-role.sql (that file needs psql: it reads a secret and switches database). */
    private void prepare() {
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

    private void migrate() {
        Flyway.configure().dataSource(as(RUNTIME)).locations("classpath:db/migration").outOfOrder(true).load().migrate();
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
