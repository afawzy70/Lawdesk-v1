package com.lawdesk.proceedings;

import java.net.URI;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * A malformed path or query value (an id that is no UUID) is a 400 problem. The shared {@code ApiExceptionHandler}
 * answers it with 500 today; this advice covers the Phase 4 proceeding controllers only, until the shared handler does
 * (requested from the integration owner).
 */
@RestControllerAdvice(assignableTypes = {ProceedingController.class, JudgmentController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProceedingRequestErrors {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail malformed(MethodArgumentTypeMismatchException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        return problem;
    }
}
