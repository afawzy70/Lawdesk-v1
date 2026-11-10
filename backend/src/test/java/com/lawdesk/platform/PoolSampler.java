package com.lawdesk.platform;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

/**
 * Samples the Hikari pool (HikariPoolMXBean) and, over a separate connection that is not part of the pool,
 * pg_stat_activity and pg_locks every 25 ms while a burst runs (QA-RT-011 measurements).
 */
final class PoolSampler {

    private final HikariPoolMXBean pool;
    private final String url;
    private final String user;
    private final String password;
    private volatile boolean running;
    private Thread thread;
    private int samples;
    private int maxActive;
    private int maxAwaiting;
    private int maxIdleInTransaction;
    private int maxLockWaiters;
    private final Map<String, Integer> maxByState = new TreeMap<>();
    private final Map<String, Integer> waitEvents = new TreeMap<>();

    PoolSampler(DataSource dataSource, String url, String user, String password) {
        this.pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();
        this.url = url;
        this.user = user;
        this.password = password;
    }

    void start() {
        running = true;
        thread = new Thread(this::run, "pool-sampler");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        running = false;
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        try (Connection admin = DriverManager.getConnection(url, user, password); Statement statement = admin.createStatement()) {
            while (running) {
                maxActive = Math.max(maxActive, pool.getActiveConnections());
                maxAwaiting = Math.max(maxAwaiting, pool.getThreadsAwaitingConnection());
                Map<String, Integer> states = new TreeMap<>();
                try (ResultSet rs = statement.executeQuery("""
                        select coalesce(state, 'none') s, coalesce(wait_event_type || ':' || wait_event, '-') w, count(*)
                        from pg_stat_activity where datname = current_database() and pid <> pg_backend_pid()
                          and backend_type = 'client backend' group by 1, 2""")) {
                    while (rs.next()) {
                        states.merge(rs.getString(1), rs.getInt(3), Integer::sum);
                        waitEvents.merge(rs.getString(2), rs.getInt(3), Math::max);
                    }
                }
                states.forEach((k, v) -> maxByState.merge(k, v, Math::max));
                maxIdleInTransaction = Math.max(maxIdleInTransaction, states.getOrDefault("idle in transaction", 0));
                try (ResultSet rs = statement.executeQuery("select count(*) from pg_locks where not granted")) {
                    rs.next();
                    maxLockWaiters = Math.max(maxLockWaiters, rs.getInt(1));
                }
                samples++;
                Thread.sleep(25);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.out.println("POOL-SAMPLER failed: " + e);
        }
    }

    String summary() {
        return "samples=" + samples + " hikari.maxActive=" + maxActive + "/" + pool.getTotalConnections()
                + " hikari.maxThreadsAwaiting=" + maxAwaiting + " pg.maxByState=" + maxByState
                + " pg.maxIdleInTransaction=" + maxIdleInTransaction + " pg.maxUngrantedLocks=" + maxLockWaiters
                + " pg.waitEventsMax=" + waitEvents;
    }
}
