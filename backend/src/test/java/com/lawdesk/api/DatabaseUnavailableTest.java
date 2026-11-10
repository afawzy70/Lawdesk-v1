package com.lawdesk.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLTransientConnectionException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/** QA-RT-011: a pool timeout is a retryable 503; any other failure stays a 500. */
class DatabaseUnavailableTest {

    @Test
    void theMvcHandlerAnswers503ForAPoolTimeoutAnd500ForAnythingElse() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        Exception timeout = new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection",
                new SQLTransientConnectionException("HikariPool-1 - Connection is not available"));

        ResponseEntity<ProblemDetail> unavailable = handler.unexpected(timeout);
        ResponseEntity<ProblemDetail> bug = handler.unexpected(new IllegalStateException("bug"));

        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailable.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(bug.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
