package com.lawdesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.lawdesk.platform.PostgresTestSupport;

/**
 * LD-010.1: every API handler, in every workstream, declares its authorization; only the endpoints needed to sign in
 * are public. Runs against the real handler mappings, so new controllers are covered automatically.
 */
@SpringBootTest
class EndpointAuthorizationTest extends PostgresTestSupport {

    private static final Set<String> PUBLIC = Set.of("AuthController#csrf", "AuthController#login");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void everyApiHandlerDeclaresItsAuthorization() {
        List<String> unprotected = new ArrayList<>();
        int api = 0;
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            boolean isApi = entry.getKey().getPathPatternsCondition() != null && entry.getKey()
                    .getPathPatternsCondition().getPatternValues().stream().anyMatch(p -> p.startsWith("/api/"));
            if (!isApi) {
                continue;
            }
            api++;
            var method = entry.getValue();
            String name = method.getBeanType().getSimpleName() + "#" + method.getMethod().getName();
            boolean declared = AnnotatedElementUtils.hasAnnotation(method.getMethod(), PreAuthorize.class)
                    || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), PreAuthorize.class);
            if (!declared && !PUBLIC.contains(name)) {
                unprotected.add(name + " " + entry.getKey());
            }
        }

        assertThat(api).isPositive();
        assertThat(unprotected).isEmpty();
    }
}
