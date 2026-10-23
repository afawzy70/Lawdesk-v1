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
        String database = "lawdesk_test_" + UUID.randomUUID().toString().replace("-", "");
        Server server = Server.INSTANCE;
        server.execute("CREATE DATABASE " + database);
        registry.add("spring.datasource.url", () -> server.jdbcUrl(database));
        registry.add("spring.datasource.username", () -> server.user);
        registry.add("spring.datasource.password", () -> server.password);
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

        void execute(String sql) {
            try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute(sql);
            } catch (SQLException e) {
                throw new IllegalStateException("Could not prepare test database: " + e.getMessage(), e);
            }
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
