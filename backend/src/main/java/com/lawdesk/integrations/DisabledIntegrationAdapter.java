package com.lawdesk.integrations;

import java.util.List;
import java.util.Map;

import com.lawdesk.api.BusinessRuleException;

/**
 * An adapter that is not connected to anything: it holds no address and no credential (so there is nothing to configure by
 * mistake), and refuses every call with {@code INTEGRATION_DISABLED} before touching anything.
 */
final class DisabledIntegrationAdapter implements IntegrationAdapter {

    static final String DISABLED = "INTEGRATION_DISABLED";
    static final String NOT_APPROVED = "NOT_APPROVED";

    private final Descriptor descriptor;

    DisabledIntegrationAdapter(String code, String nameAr, String nameEn, String direction, List<String> operations) {
        this.descriptor = new Descriptor(code, nameAr, nameEn, direction, List.copyOf(operations), "DISABLED", NOT_APPROVED, false, false);
    }

    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public Map<String, Object> call(String operation, Map<String, Object> payload) {
        throw new BusinessRuleException(DISABLED, "the integration " + descriptor.code() + " is not approved and not connected");
    }
}
