package com.lawdesk.assistant;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether the assistant is operational (owner decision D8). It is operational only when the owner has approved a provider
 * (a row of {@code ai_provider_config} with status APPROVED) AND an adapter for it exists in this application. No row exists and
 * no adapter exists, so it is NOT operational: {@code reason NO_APPROVED_PROVIDER}, everywhere it is reported. The local test
 * provider is a stand-in that proves the components; it does not make the assistant operational.
 */
@Component
class AssistantStatus {

    static final String REASON = "NO_APPROVED_PROVIDER";
    static final String MODE = "NON_PRODUCTION_TEST";

    /** The answer of {@code GET /api/v1/assistant/status}. */
    record Status(boolean operational, String reason, String mode, AssistantProvider.Info provider, long approvedProviders,
            long configuredProviders, boolean externalCalls, boolean caseDataLeavesTheSystem, List<String> tasks) {
    }

    private final JdbcClient jdbc;
    private final AssistantProvider provider;

    AssistantStatus(JdbcClient jdbc, AssistantProvider provider) {
        this.jdbc = jdbc;
        this.provider = provider;
    }

    @Transactional(readOnly = true)
    Status current() {
        long configured = jdbc.sql("select count(*) from ai_provider_config").query(Long.class).single();
        long approved = jdbc.sql("select count(*) from ai_provider_config where status = 'APPROVED'").query(Long.class).single();
        // An approved row without an adapter in this build cannot make the assistant operational, and there is no adapter.
        boolean operational = false;
        return new Status(operational, REASON, MODE, provider.info(), approved, configured, false, false,
                java.util.Arrays.stream(AssistantTask.values()).map(Enum::name).toList());
    }
}
