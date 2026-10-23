package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * LD-004 / QA-0032 readiness under a database outage (owner decision 2026-10-23): with the application's own
 * PostgreSQL stopped, readiness must answer 503 within 5 seconds, liveness must stay 200, and readiness must return to
 * 200 once the database is back. A healthy database must never be reported as down.
 *
 * <p>Isolated test conditions: this class owns a separate embedded PostgreSQL server (stopping it affects no other
 * test) and talks to the application over real HTTP on loopback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class ReadinessDatabaseOutageTest {

    private static final Duration DETECTION_LIMIT = Duration.ofSeconds(5);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static Path dataDirectory;
    private static int databasePort;
    private static EmbeddedPostgres database;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void ownDatabase(DynamicPropertyRegistry registry) throws IOException {
        dataDirectory = Files.createTempDirectory("lawdesk-outage-db");
        databasePort = freePort();
        database = startDatabase();
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + databasePort + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void readinessReportsADatabaseOutageWithinFiveSecondsAndRecovers() throws Exception {
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);

        database.close();
        database = null;
        try {
            for (int probe = 1; probe <= 3; probe++) {
                long started = System.nanoTime();
                HttpResponse<String> readiness = get("/actuator/health/readiness");
                Duration took = Duration.ofNanos(System.nanoTime() - started);
                System.out.printf("outage probe %d: readiness %d in %d ms%n", probe, readiness.statusCode(),
                        took.toMillis());

                assertThat(readiness.statusCode()).as("probe %d during the outage", probe).isEqualTo(503);
                assertThat(readiness.body()).contains("\"status\":\"DOWN\"").doesNotContain("details", "components");
                assertThat(took).as("probe %d detection time", probe).isLessThan(DETECTION_LIMIT);
            }
            assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        } finally {
            database = startDatabase();
        }

        assertThat(readinessWithin(Duration.ofSeconds(30))).as("readiness after the database is back").isEqualTo(200);
    }

    @Test
    void aHealthyDatabaseIsNeverReportedDown() {
        for (int probe = 1; probe <= 30; probe++) {
            assertThat(get("/actuator/health/readiness").statusCode()).as("probe %d", probe).isEqualTo(200);
        }
    }

    private int readinessWithin(Duration limit) throws InterruptedException {
        long deadline = System.nanoTime() + limit.toNanos();
        int status = get("/actuator/health/readiness").statusCode();
        while (status != 200 && System.nanoTime() < deadline) {
            Thread.sleep(500);
            status = get("/actuator/health/readiness").statusCode();
        }
        return status;
    }

    private HttpResponse<String> get(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Starts (or restarts, keeping its data) this class's own PostgreSQL on the same port. */
    private static EmbeddedPostgres startDatabase() {
        try {
            return EmbeddedPostgres.builder()
                    .setDataDirectory(dataDirectory)
                    .setCleanDataDirectory(false)
                    .setPort(databasePort)
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start the outage test's PostgreSQL", e);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
