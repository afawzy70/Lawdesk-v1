package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two ordinary READ COMMITTED connections with explicit transactions, no MockMvc (QAD-038). The first writer runs its
 * statement and stays uncommitted; the second writer runs on another connection and must <em>wait</em> for the first
 * one, which is proved in {@code pg_stat_activity} (never with a sleep); then the first commits and the outcome of the
 * second is returned: {@code COMMITTED} or the SQLSTATE that refused it ({@code 23514} for a check violation).
 */
public final class TwoConnectionRace {

    /** One statement of a writer's transaction. */
    public record Write(String sql, Object... args) {
    }

    private TwoConnectionRace() {
    }

    /**
     * Runs {@code first} uncommitted, then {@code second} on another connection, asserts that {@code second} waits for
     * a lock, commits {@code first} and returns how {@code second} ended.
     */
    public static String run(DataSource dataSource, JdbcTemplate observer, Write first, Write second)
            throws Exception {
        try (Connection one = open(dataSource); Connection two = open(dataSource);
                ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            execute(one, first);
            int pid = pidOf(two);
            Future<String> outcome = pool.submit(() -> {
                try {
                    execute(two, second);
                    two.commit();
                    return "COMMITTED";
                } catch (SQLException e) {
                    two.rollback();
                    return e.getSQLState();
                }
            });
            try {
                awaitLockWait(observer, pid);
            } finally {
                one.commit();
            }
            return outcome.get(30, TimeUnit.SECONDS);
        }
    }

    /** The second writer must have been refused (check violation) after waiting; one winner, no half-write. */
    public static void assertRefusedAfterWaiting(DataSource dataSource, JdbcTemplate observer, Write first,
            Write second) throws Exception {
        assertThat(run(dataSource, observer, first, second)).as("the later writer").isEqualTo("23514");
    }

    private static Connection open(DataSource dataSource) throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    private static int pidOf(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("select pg_backend_pid()")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void execute(Connection connection, Write write) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(write.sql())) {
            for (int i = 0; i < write.args().length; i++) {
                statement.setObject(i + 1, write.args()[i]);
            }
            statement.executeUpdate();
        }
    }

    private static void awaitLockWait(JdbcTemplate observer, int pid) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            Integer waiting = observer.queryForObject(
                    "select count(*) from pg_stat_activity where pid = ? and wait_event_type = 'Lock'", Integer.class,
                    pid);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("the second writer never waited for the first one: the writes are not serialised");
    }
}
