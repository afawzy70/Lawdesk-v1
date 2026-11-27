package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Recognition must not hold a database connection while the engine runs (it can run for minutes per page) and must not make user requests
 * wait. The pool is the production one (HikariCP, maximum 10, connection timeout 3 s). The engine here is a reader that takes
 * {@value #ENGINE_MS} ms; while it runs the number of connections in use is sampled, and user requests are timed against the same requests
 * made with no recognition running. With -Docr.cost=<file> the numbers are appended to the file.
 */
class OcrConnectionHoldTest extends ArchiveTestSupport {

    static final int ENGINE_MS = 4000;

    @Autowired
    private DataSource dataSource;

    @Test
    void noConnectionIsHeldWhileTheEngineRunsAndRequestsAreNotDelayed() throws Exception {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        assertThat(pool.getConnectionTimeout()).isEqualTo(3000);
        UUID owner = user("Owner");
        UUID caseId = newCase(owner);
        UUID id = attach(member(owner), caseId, file("scan.png", "image/png", ArchiveFiles.png("scan")));
        ocr.reader = image -> {
            try {
                Thread.sleep(ENGINE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "مذكرة دفاع الشركة";
        };

        List<Long> idle = timeRequests(owner, id, caseId, 40, null);

        AtomicBoolean running = new AtomicBoolean(true);
        List<Integer> active = Collections.synchronizedList(new ArrayList<>());
        Thread sampler = new Thread(() -> {
            while (running.get()) {
                active.add(pool.getHikariPoolMXBean().getActiveConnections());
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        Thread worker = new Thread(() -> runRecognition());
        sampler.start();
        worker.start();
        Thread.sleep(800); // inside the engine call
        int duringEngine = pool.getHikariPoolMXBean().getActiveConnections();
        // user requests while the engine runs
        List<Long> busy = timeRequests(owner, id, caseId, 40, worker);
        worker.join();
        running.set(false);
        sampler.join();
        int maxActiveDuringEngine = active.stream().skip(10).mapToInt(Integer::intValue).max().orElse(0);

        System.out.println("OCRCONN engineMs=" + ENGINE_MS + " activeAt800ms=" + duringEngine + " maxActiveDuringRun(excluding API sampling)="
                + maxActiveDuringEngine + " idle p50/p95 ms=" + pct(idle, 50) + "/" + pct(idle, 95) + " busy p50/p95 ms=" + pct(busy, 50) + "/" + pct(busy, 95));
        String out = System.getProperty("ocr.cost");
        if (out != null) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(out), "engine_ms=" + ENGINE_MS + "\nactive_connections_while_engine_runs=" + duringEngine
                    + "\nidle_p50_ms=" + pct(idle, 50) + "\nidle_p95_ms=" + pct(idle, 95) + "\nbusy_p50_ms=" + pct(busy, 50) + "\nbusy_p95_ms=" + pct(busy, 95) + "\n");
        }
        // The only connection use during the run is the claim at the start, the single write at the end, and the API calls of this test.
        assertThat(duringEngine).as("connections in use while the engine runs").isZero();
        // no request waited for a connection (the pool's timeout is 3000 ms): the slowest request stays far below it
        assertThat(busy.stream().mapToLong(Long::longValue).max().orElse(0)).as("slowest request during recognition (ms)").isLessThan(2000);
    }

    private List<Long> timeRequests(UUID owner, UUID id, UUID caseId, int n, Thread whileAlive) throws Exception {
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            if (i % 2 == 0) {
                get(member(owner), "/api/v1/documents/" + id + "/text");
            } else {
                get(member(owner), "/api/v1/documents/search?q=مذكرة&caseId=" + caseId);
            }
            millis.add((System.nanoTime() - t0) / 1_000_000);
            if (whileAlive != null && !whileAlive.isAlive()) {
                break;
            }
        }
        return millis;
    }

    private static long pct(List<Long> values, int p) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.isEmpty() ? 0 : sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1));
    }
}
