package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * LD-002 request correlation and structured request logging: QA-0011 (invalid header boundary), QA-0015 (concurrent
 * requests), QA-0348 (no sensitive request data in logs).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class RequestCorrelationTest extends HttpTestSupport {

    private static final String REQUEST_ID = "X-Request-Id";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Test
    void generatesRequestIdWhenClientSendsNone() {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.headers().firstValue(REQUEST_ID)).hasValueSatisfying(
                id -> assertThat(id).matches(UUID_PATTERN));
    }

    @Test
    void echoesWellFormedClientRequestId() {
        HttpResponse<String> response = get("/actuator/health", Map.of(REQUEST_ID, "proxy-abc.123_X"));

        assertThat(response.headers().firstValue(REQUEST_ID)).hasValue("proxy-abc.123_X");
    }

    @Test
    void replacesMalformedOrOversizedClientRequestId() {
        for (String bad : List.of("a".repeat(65), "has space", "<script>", "id;drop", "ümlaut")) {
            HttpResponse<String> response = get("/actuator/health", Map.of(REQUEST_ID, bad));

            assertThat(response.headers().firstValue(REQUEST_ID)).as(bad).hasValueSatisfying(
                    id -> assertThat(id).matches(UUID_PATTERN));
        }
    }

    @Test
    void unauthenticatedResponsesAreCorrelatedToo() {
        assertThat(get("/api/v1/cases").headers().firstValue(REQUEST_ID)).isPresent();
    }

    @Test
    void concurrentRequestsGetDistinctIds() {
        List<CompletableFuture<String>> calls = IntStream.range(0, 20)
                .mapToObj(i -> CompletableFuture.supplyAsync(
                        () -> get("/actuator/health").headers().firstValue(REQUEST_ID).orElseThrow()))
                .toList();

        Set<String> ids = calls.stream().map(CompletableFuture::join).collect(Collectors.toSet());

        assertThat(ids).hasSize(20);
    }

    @Test
    void completedRequestIsLoggedAsStructuredJsonWithoutSensitiveData(CapturedOutput output) {
        String id = get("/api/v1/cases?client=Secret-Client-Name",
                Map.of(REQUEST_ID, "trace-me-1", "Authorization", "Bearer top-secret-token"))
                .headers().firstValue(REQUEST_ID).orElseThrow();

        String line = output.getOut().lines()
                .filter(l -> l.contains("trace-me-1") && l.contains("request completed"))
                .findFirst().orElseThrow(() -> new AssertionError("no request log line for " + id + " in:\n" + output));
        assertThat(line).startsWith("{").endsWith("}");
        // ECS nests dotted field names.
        assertThat(line).contains("\"http\":{\"request\":{\"method\":\"GET\"}",
                "\"response\":{\"status_code\":401}", "\"url\":{\"path\":\"/api/v1/cases\"}",
                "\"requestId\":\"trace-me-1\"");
        assertThat(output.getOut()).doesNotContain("Secret-Client-Name", "top-secret-token");
    }
}
