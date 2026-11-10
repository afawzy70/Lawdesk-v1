package com.lawdesk.api;

import java.sql.SQLTransientConnectionException;

/**
 * Tells "no database connection could be obtained" (pool timeout, database away) from a programming error, so the
 * client is answered 503 with Retry-After instead of a blind 500 (QA-RT-011).
 */
public final class DatabaseUnavailable {

    public static final String RETRY_AFTER_SECONDS = "1";

    private DatabaseUnavailable() {
    }

    public static boolean causes(Throwable failure) {
        int depth = 0;
        for (Throwable t = failure; t != null && depth < 20; t = t.getCause(), depth++) {
            if (t instanceof SQLTransientConnectionException) {
                return true;
            }
        }
        return false;
    }
}
