package com.lawdesk.integrations;

import java.util.List;
import java.util.Map;

/**
 * The port for a future integration with another system (requirements section 34, last item: correspondence, human resources,
 * archiving, e-mail and the related government systems, "subject to the necessary approvals"). Every adapter shipped today is a
 * {@link DisabledIntegrationAdapter}: no connection, no address, no credential. The contract each would have to meet is written
 * in docs/design/LD-DOC-10-integration-contracts.md and docs/design/integration-contracts.openapi.yaml; enabling one is a
 * separate owner decision, taken system by system, with the data-sharing terms and the credential handling agreed first.
 */
public interface IntegrationAdapter {

    /** What the adapter is for. {@code status} is always DISABLED for now; {@code reason} says what is missing. */
    record Descriptor(String code, String nameAr, String nameEn, String direction, List<String> operations, String status,
            String reason, boolean hasCredentials, boolean hasEndpoint) {
    }

    Descriptor descriptor();

    /** Performs an operation of the contract. A disabled adapter refuses every one of them. */
    Map<String, Object> call(String operation, Map<String, Object> payload);
}
