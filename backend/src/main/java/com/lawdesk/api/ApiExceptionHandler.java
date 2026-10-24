package com.lawdesk.api;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps failures to RFC 9457 problems (architecture/CONTRACTS.md §4). Nothing from the request or the exception
 * message reaches the client: validation errors name the field and the constraint only.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "code", String.valueOf(error.getCode())))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST);
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(FieldErrorException.class)
    ProblemDetail field(FieldErrorException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST);
        problem.setProperty("errors", List.of(Map.of("field", e.field(), "code", e.code())));
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST);
    }

    /** A path or query parameter of the wrong type, such as a malformed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail mismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail noResource(NoResourceFoundException e) {
        return problem(HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail method(HttpRequestMethodNotSupportedException e) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({ConflictException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail rule(BusinessRuleException e) {
        log.info("business rule {} refused the request: {}", e.rule(), e.getMessage());
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT);
        problem.setProperty("rule", e.rule());
        return problem;
    }

    /** Left to Spring Security, which answers 401/403 as problems (SecurityConfig). */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void security(RuntimeException e) {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static ProblemDetail problem(HttpStatus status) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
