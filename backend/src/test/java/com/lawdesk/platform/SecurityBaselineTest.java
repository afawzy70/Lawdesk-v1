package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * LD-002 deny-by-default baseline: QA-0012 (unauthenticated access), QA-0013 (sensitive endpoints), QA-0014 (no state
 * change through rejected writes), QA-0349 (security headers).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityBaselineTest extends HttpTestSupport {

    @Autowired
    ApplicationContext context;

    @Test
    void noDefaultInMemoryUserIsCreated() {
        // Spring Boot's fallback user has a generated password that is printed to the log.
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }

    @Test
    void unauthenticatedApiRequestIsRejectedWithProblemDetail() {
        HttpResponse<String> response = get("/api/v1/cases");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type)
                .startsWith("application/problem+json").doesNotContainIgnoringCase("ISO-8859-1"));
        assertThat(response.body()).contains("\"status\":401").contains("\"title\":\"Unauthorized\"");
        assertThat(response.body()).doesNotContain("trace", "exception");
    }

    // Codex review on 020154c: /error is only for the container's error dispatch, not a public URL.
    @Test
    void directRequestToTheErrorEndpointIsUnauthorized() {
        HttpResponse<String> response = get("/error");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(post("/error", "{}").statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump",
            "/actuator/loggers", "/actuator/mappings", "/actuator/threaddump"})
    void sensitiveActuatorEndpointsAreNotReachable(String path) {
        assertThat(get(path).statusCode()).isIn(401, 404);
    }

    // QAD-011: an anonymous caller is told to authenticate (401) for every method; a missing CSRF token only
    // matters once the caller is authenticated (403).
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/cases", "/actuator/health"})
    void writeWithoutAuthenticationIsUnauthorized(String path) {
        HttpResponse<String> response = post(path, "{\"title\":\"x\"}");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"status\":401").doesNotContain("trace", "exception");
    }

    @Test
    void responsesCarryBaselineSecurityHeaders() {
        assertBaselineHeaders(get("/actuator/health"));
    }

    private static void assertBaselineHeaders(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValue("default-src 'none'; frame-ancestors 'none'");
        assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(
                value -> assertThat(value).contains("no-store"));
    }

    // QAD-009: requests refused by the HTTP firewall, before the security filter chain, get the same headers.
    @ParameterizedTest
    @ValueSource(strings = {"//actuator/health", "/actuator/health;x=1", "/api/v1/%2e%2e/cases"})
    void firewallRejectionsAreProblemsWithBaselineHeaders(String path) {
        HttpResponse<String> response = get(path);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"status\":400").doesNotContain("trace", "exception", "Rejected");
        assertBaselineHeaders(response);
    }

    @Test
    void rejectedResponsesAlsoCarrySecurityHeaders() {
        HttpResponse<String> response = get("/api/v1/cases");

        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
    }
}
