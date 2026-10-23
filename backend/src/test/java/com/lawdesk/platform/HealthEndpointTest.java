package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** LD-002 health and info endpoints: QA-0009 (happy path), QA-0010 (unknown/required path), QA-0016 (API contract). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest extends HttpTestSupport {

    @Test
    void healthIsPublicAndUp() {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(299); // deliberate CI negative check
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).contains("json"));
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void healthDoesNotLeakComponentDetails() {
        String body = get("/actuator/health").body();

        assertThat(body).doesNotContain("components", "details", "diskSpace", "path");
    }

    @Test
    void livenessAndReadinessProbesAreExposedForContainerHealthChecks() {
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").body()).contains("\"status\":\"UP\"");
    }

    @Test
    void infoReportsApplicationNameAndVersionOnly() {
        HttpResponse<String> response = get("/actuator/info");

        assertThat(response.statusCode()).isEqualTo(299); // deliberate CI negative check
        assertThat(response.body()).contains("\"name\":\"lawdesk-backend\"").contains("\"version\"");
        assertThat(response.body()).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("secret");
    }

    @Test
    void unknownHealthGroupIsNotFoundWithoutInternals() {
        HttpResponse<String> response = get("/actuator/health/does-not-exist");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("trace", "exception", "org.springframework");
    }
}
