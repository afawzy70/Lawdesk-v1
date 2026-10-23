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

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump",
            "/actuator/loggers", "/actuator/mappings", "/actuator/threaddump"})
    void sensitiveActuatorEndpointsAreNotReachable(String path) {
        assertThat(get(path).statusCode()).isIn(401, 404);
    }

    @Test
    void writeWithoutAuthenticationIsRejected() {
        HttpResponse<String> response = post("/api/v1/cases", "{\"title\":\"x\"}");

        assertThat(response.statusCode()).isIn(401, 403);
        assertThat(response.body()).doesNotContain("trace", "exception");
    }

    @Test
    void writeToHealthEndpointIsRejected() {
        assertThat(post("/actuator/health", "{}").statusCode()).isBetween(400, 499);
    }

    @Test
    void responsesCarryBaselineSecurityHeaders() {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValue("default-src 'none'; frame-ancestors 'none'");
        assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(
                value -> assertThat(value).contains("no-store"));
    }

    @Test
    void rejectedResponsesAlsoCarrySecurityHeaders() {
        HttpResponse<String> response = get("/api/v1/cases");

        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("Content-Security-Policy")).isPresent();
    }
}
