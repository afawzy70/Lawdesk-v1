package com.lawdesk.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.lawdesk.integrations.IntegrationRegistry;
import com.lawdesk.security.Permission;

/**
 * LD-059, owner decision D8: nothing leaves the system. No client of any kind exists in the assistant's package, no provider is
 * configured, the only provider is the local test one, and the integration adapters (section 34) are disabled and unconnected.
 */
class AssistantBoundariesTest extends AssistantTestSupport {

    private static final Set<String> CLIENTS = Set.of("java.net.http.HttpClient", "java.net.HttpURLConnection", "java.net.URLConnection",
            "java.net.Socket", "javax.net.ssl.SSLSocketFactory", "org.springframework.web.client.RestClient",
            "org.springframework.web.client.RestTemplate", "org.springframework.web.reactive.function.client.WebClient",
            "org.springframework.http.client.ClientHttpRequestFactory");

    @Autowired
    ApplicationContext context;

    @Autowired
    List<AssistantProvider> providers;

    @Autowired
    IntegrationRegistry integrations;

    @Test
    void noBeanOfTheAssistantPackageHoldsAnOutboundClient() throws Exception {
        List<String> offenders = new ArrayList<>();
        int beans = 0;
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null || !(type.getName().startsWith("com.lawdesk.assistant.") || type.getName().startsWith("com.lawdesk.integrations."))) {
                continue;
            }
            beans++;
            for (Field field : type.getDeclaredFields()) {
                if (CLIENTS.contains(field.getType().getName())) {
                    offenders.add(type.getSimpleName() + "." + field.getName());
                }
            }
            for (var constructor : type.getDeclaredConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    if (CLIENTS.contains(parameter.getName())) {
                        offenders.add(type.getSimpleName() + "(" + parameter.getSimpleName() + ")");
                    }
                }
            }
        }
        assertThat(beans).isGreaterThan(5);
        assertThat(offenders).isEmpty();
    }

    @Test
    void noSourceFileOfTheAssistantOrTheIntegrationsReferencesANetworkClient() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String directory : List.of("src/main/java/com/lawdesk/assistant", "src/main/java/com/lawdesk/integrations")) {
            try (Stream<Path> files = Files.walk(Path.of(directory))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String code = Files.readString(file);
                    for (String forbidden : List.of("java.net", "HttpClient", "RestClient", "RestTemplate", "WebClient", "okhttp", "Socket",
                            "openai", "anthropic", "api-key", "apiKey", "bearer ")) {
                        if (code.toLowerCase().contains(forbidden.toLowerCase())) {
                            offenders.add(file.getFileName() + " mentions " + forbidden);
                        }
                    }
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void noPropertyOrDependencyConfiguresAProviderOrAKey() throws IOException {
        String properties = Files.readString(Path.of("src/main/resources/application.yaml")).toLowerCase();
        assertThat(properties).doesNotContain("assistant").doesNotContain("openai").doesNotContain("anthropic").doesNotContain("api-key");
        String pom = Files.readString(Path.of("pom.xml")).toLowerCase();
        assertThat(pom).doesNotContain("openai").doesNotContain("anthropic").doesNotContain("langchain").doesNotContain("spring-ai");
    }

    @Test
    void theOnlyProviderIsTheLocalTestOneAndItIsNeitherProductionNorExternal() {
        assertThat(providers).hasSize(1);
        AssistantProvider.Info info = providers.get(0).info();
        assertThat(info.code()).isEqualTo("LOCAL_EXTRACTIVE_TEST");
        assertThat(info.nonProduction()).isTrue();
        assertThat(info.external()).isFalse();
    }

    @Test
    void theProviderTableStaysEmptyAndTheStatusSaysNotOperationalEverywhere() throws Exception {
        UUID owner = user("Owner");
        UUID caseId = legalCase(owner, null);
        ask(member(owner), question(caseId, "الكفالة البنكية"), 200);
        ask(member(owner), json("task", "CASE_SUMMARY", "caseId", caseId.toString()), 200);
        assertThat(count("select count(*) from ai_provider_config")).isZero();
        mvc.perform(getJson(outsider(user("Anyone")), "/api/v1/assistant/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.operational").value(false)).andExpect(jsonPath("$.reason").value("NO_APPROVED_PROVIDER"))
                .andExpect(jsonPath("$.mode").value("NON_PRODUCTION_TEST")).andExpect(jsonPath("$.approvedProviders").value(0))
                .andExpect(jsonPath("$.configuredProviders").value(0)).andExpect(jsonPath("$.externalCalls").value(false))
                .andExpect(jsonPath("$.caseDataLeavesTheSystem").value(false)).andExpect(jsonPath("$.provider.nonProduction").value(true))
                .andExpect(jsonPath("$.tasks.length()").value(6));
    }

    @Test
    void evenAnApprovedRowWithoutAnAdapterDoesNotMakeTheAssistantOperational() throws Exception {
        jdbc.update("insert into ai_provider_config (code, display_name, status, approved_by, approved_at, created_by)"
                + " values ('SOME_PROVIDER', 'x', 'APPROVED', gen_random_uuid(), now(), gen_random_uuid())");
        try {
            mvc.perform(getJson(outsider(user("Anyone")), "/api/v1/assistant/status")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.operational").value(false)).andExpect(jsonPath("$.approvedProviders").value(1))
                    .andExpect(jsonPath("$.reason").value("NO_APPROVED_PROVIDER"));
        } finally {
            jdbc.update("delete from ai_provider_config");
        }
    }

    @Test
    void statusNeedsASignedInUserButNoPermissionAndTheAssistantEndpointsNeedTheirOwn() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/assistant/status")).andExpect(status().isUnauthorized());
        UUID u = user("Plain");
        mvc.perform(getJson(outsider(u, Permission.CASE_VIEW), "/api/v1/assistant/outputs")).andExpect(status().isForbidden());
        mvc.perform(getJson(outsider(u, Permission.CASE_VIEW), "/api/v1/assistant/queries")).andExpect(status().isForbidden());
    }

    // ---- section 34: integration contracts ---------------------------------------------------------------------------------

    @Test
    void everyNamedIntegrationIsADisabledAdapterWithNoEndpointAndNoCredential() throws Exception {
        assertThat(integrations.descriptors()).extracting("code").containsExactly("CORRESPONDENCE", "HUMAN_RESOURCES", "ARCHIVING",
                "EMAIL", "GOVERNMENT_SYSTEMS");
        assertThat(integrations.descriptors()).allSatisfy(d -> {
            assertThat(d.status()).isEqualTo("DISABLED");
            assertThat(d.reason()).isEqualTo("NOT_APPROVED");
            assertThat(d.hasCredentials()).isFalse();
            assertThat(d.hasEndpoint()).isFalse();
            assertThat(d.operations()).isNotEmpty();
        });
        for (var adapter : integrations.adapters()) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.call("anything", Map.of()))
                    .isInstanceOf(com.lawdesk.api.BusinessRuleException.class)
                    .satisfies(e -> assertThat(((com.lawdesk.api.BusinessRuleException) e).rule()).isEqualTo("INTEGRATION_DISABLED"));
        }
        mvc.perform(getJson(outsider(user("Admin"), Permission.USER_VIEW), "/api/v1/integrations")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5)).andExpect(jsonPath("$[0].status").value("DISABLED"));
        mvc.perform(getJson(outsider(user("Plain2"), Permission.CASE_VIEW), "/api/v1/integrations")).andExpect(status().isForbidden());
    }

    @Test
    void theContractDocumentsNameEveryIntegrationAndStateThatNoneIsConnected() throws IOException {
        String markdown = Files.readString(Path.of("../docs/design/LD-DOC-10-integration-contracts.md"));
        String openapi = Files.readString(Path.of("../docs/design/integration-contracts.openapi.yaml"));
        for (String code : List.of("CORRESPONDENCE", "HUMAN_RESOURCES", "ARCHIVING", "EMAIL", "GOVERNMENT_SYSTEMS")) {
            assertThat(markdown).contains(code);
            assertThat(openapi).contains(code);
        }
        assertThat(openapi).contains("openapi:").contains("x-lawdesk-status: DISABLED");
        assertThat(Files.readString(Path.of("../docs/design/LD-DOC-09-assistant-provider-options.md"))).contains("NO_APPROVED_PROVIDER");
    }
}
