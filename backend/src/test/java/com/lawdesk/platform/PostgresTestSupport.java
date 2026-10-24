package com.lawdesk.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Gives every Spring test context its own freshly created PostgreSQL database on which the real Flyway migrations run
 * from scratch. Spring caches contexts, so test classes with identical configuration share one context and database:
 * tests that write data must remove it again, and tests that change the schema must use {@code @DirtiesContext}.
 *
 * <p>By default the databases live in one embedded PostgreSQL server (real binaries, no Docker) started once per JVM.
 * When {@code LAWDESK_TEST_DB_URL}, {@code LAWDESK_TEST_DB_USER} and {@code LAWDESK_TEST_DB_PASSWORD} point at an
 * existing server (for example the official image as a CI service), that server is used instead.
 */
public abstract class PostgresTestSupport {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        Server server = Server.INSTANCE;
        String url = server.jdbcUrl(server.createDatabase());
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> server.user);
        registry.add("spring.datasource.password", () -> server.password);
        // Cached Spring contexts each retain a pool on the same PostgreSQL server. Keep the complete suite below
        // its connection limit, while allowing the chronology and last-administrator races to use several writers.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 4);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 0);
    }

    /** A new, empty database (no migrations applied) on the same server, for tests that drive Flyway themselves. */
    protected static DataSource freshDatabase() {
        Server server = Server.INSTANCE;
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(server.jdbcUrl(server.createDatabase()));
        dataSource.setUser(server.user);
        dataSource.setPassword(server.password);
        return dataSource;
    }

    private static final class Server {

        static final Server INSTANCE = new Server();

        private final DataSource admin;
        private final String urlPrefix;
        private final String user;
        private final String password;

        private Server() {
            String url = System.getenv("LAWDESK_TEST_DB_URL");
            if (url != null && !url.isBlank()) {
                user = System.getenv("LAWDESK_TEST_DB_USER");
                password = System.getenv("LAWDESK_TEST_DB_PASSWORD");
                PGSimpleDataSource external = new PGSimpleDataSource();
                external.setUrl(url);
                external.setUser(user);
                external.setPassword(password);
                admin = external;
                urlPrefix = url.substring(0, url.lastIndexOf('/') + 1);
            } else {
                EmbeddedPostgres embedded = startEmbedded();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> close(embedded)));
                user = "postgres";
                password = "postgres";
                admin = embedded.getPostgresDatabase();
                urlPrefix = "jdbc:postgresql://localhost:" + embedded.getPort() + "/";
            }
        }

        String jdbcUrl(String database) {
            return urlPrefix + database;
        }

        String createDatabase() {
            String database = "lawdesk_test_" + UUID.randomUUID().toString().replace("-", "");
            try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + database);
            } catch (SQLException e) {
                throw new IllegalStateException("Could not create test database: " + e.getMessage(), e);
            }
            return database;
        }

        private static EmbeddedPostgres startEmbedded() {
            try {
                return EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start embedded PostgreSQL", e);
            }
        }

        private static void close(EmbeddedPostgres embedded) {
            try {
                embedded.close();
            } catch (IOException ignored) {
                // JVM is exiting; the data directory is temporary.
            }
        }
    }
}
