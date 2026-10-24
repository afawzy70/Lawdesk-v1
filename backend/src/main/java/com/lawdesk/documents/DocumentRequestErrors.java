package com.lawdesk.documents;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * Upload failures of the document endpoints as problems, never a 500: a file over the multipart limit is a 400 naming
 * the {@code file} field ({@code MaxSize}, like the service's own size check), a broken multipart body a 400, and a
 * body that is not multipart a 415. The shared handler does not know these exceptions.
 */
@RestControllerAdvice(assignableTypes = DocumentController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class DocumentRequestErrors {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST);
        problem.setProperty("errors", List.of(Map.of("field", "file", "code", "MaxSize")));
        return problem;
    }

    @ExceptionHandler(MultipartException.class)
    ProblemDetail unreadable(MultipartException e) {
        return problem(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail notMultipart(HttpMediaTypeNotSupportedException e) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    private static ProblemDetail problem(HttpStatus status) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
