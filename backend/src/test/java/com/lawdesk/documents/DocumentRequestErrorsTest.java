package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * Uploads refused by the servlet container's multipart limits never reach the service, and MockMvc does not apply
 * those limits; this pins how the document endpoints answer them (LD-026, PHASE-5-ACCEPTANCE criterion 2).
 */
class DocumentRequestErrorsTest {

    private final DocumentRequestErrors errors = new DocumentRequestErrors();

    @Test
    void anUploadOverTheMultipartLimitIsA400NamingTheFile() {
        ProblemDetail problem = errors.tooLarge(new MaxUploadSizeExceededException(10L * 1024 * 1024));
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry("errors", List.of(Map.of("field", "file", "code", "MaxSize")));
    }

    @Test
    void aBrokenMultipartBodyIsA400AndAnotherBodyA415() {
        assertThat(errors.unreadable(new MultipartException("truncated")).getStatus()).isEqualTo(400);
        assertThat(errors.notMultipart(new HttpMediaTypeNotSupportedException("json")).getStatus()).isEqualTo(415);
    }
}
