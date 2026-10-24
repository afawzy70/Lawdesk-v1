package com.lawdesk.audit;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.platform.RequestCorrelationFilter;

/**
 * Records security events (LD-011). Append-only: there is no update or delete path, and the database refuses one.
 * Each event runs in its own transaction, so a refused login or a failing request still leaves its record.
 * Callers pass identifiers and fixed reason codes only, never passwords, hashes, session ids or tokens.
 */
@Service
public class AuditLog {

    public enum Outcome {
        SUCCESS, FAILURE, DENIED
    }

    private static final int USERNAME_LENGTH = 64;
    private static final int TARGET_LENGTH = 256;

    private final JdbcTemplate jdbc;

    AuditLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, Outcome outcome, UUID actorId, String actorUsername, String targetType,
            String targetId, String reason) {
        jdbc.update("""
                insert into audit_event (actor_id, actor_username, action, target_type, target_id, outcome, reason, request_id)
                values (?, ?, ?, ?, ?, ?, ?, ?)""", actorId, truncate(actorUsername, USERNAME_LENGTH), action,
                targetType, truncate(targetId, TARGET_LENGTH), outcome.name(), reason,
                MDC.get(RequestCorrelationFilter.MDC_KEY));
    }

    private static String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
