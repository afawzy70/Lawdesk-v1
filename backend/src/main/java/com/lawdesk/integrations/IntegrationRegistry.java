package com.lawdesk.integrations;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * The integrations the requirements name (section 34), each as a disabled adapter. Fixed in code on purpose: there is no
 * setting, table or endpoint that could switch one on.
 */
@Component
public class IntegrationRegistry {

    private final List<IntegrationAdapter> adapters = List.of(
            new DisabledIntegrationAdapter("CORRESPONDENCE", "نظام المراسلات", "Correspondence system", "INBOUND_AND_OUTBOUND",
                    List.of("registerIncomingLetter", "registerOutgoingLetter", "getLetterStatus")),
            new DisabledIntegrationAdapter("HUMAN_RESOURCES", "نظام الموارد البشرية", "Human resources system", "INBOUND",
                    List.of("getEmployee", "listOrganisationUnits")),
            new DisabledIntegrationAdapter("ARCHIVING", "نظام الأرشفة", "Archiving system", "OUTBOUND",
                    List.of("transferDocument", "getTransferStatus")),
            new DisabledIntegrationAdapter("EMAIL", "البريد الإلكتروني", "E-mail", "INBOUND",
                    List.of("listReceivedMessages", "getReceivedMessage")),
            new DisabledIntegrationAdapter("GOVERNMENT_SYSTEMS", "الأنظمة الحكومية ذات الصلة", "Related government systems",
                    "INBOUND_AND_OUTBOUND", List.of("lookupCaseStatus", "submitFiling")));

    public List<IntegrationAdapter> adapters() {
        return adapters;
    }

    public List<IntegrationAdapter.Descriptor> descriptors() {
        return adapters.stream().map(IntegrationAdapter::descriptor).toList();
    }
}
