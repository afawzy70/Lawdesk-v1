package com.lawdesk.courts;

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * A malformed path or query value (an id that is no UUID, an unknown level) is a 400 problem. The shared
 * {@code ApiExceptionHandler} does not map it yet and would answer 500; this advice covers the court controller
 * only, until the shared handler does (requested from the integration owner).
 */
@RestControllerAdvice(assignableTypes = CourtController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CourtRequestErrors {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail malformed(MethodArgumentTypeMismatchException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        return problem;
    }
}
