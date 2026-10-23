package com.lawdesk.cases;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-012 create and read a legal case through the real security filter chain (docs/delivery/PHASE-3-ACCEPTANCE.md,
 * LD-012; QA-0089…QA-0095).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CaseApiTest extends PostgresTestSupport {

    private static final String CASES = "/api/v1/cases";
    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;
    private static final UUID ACTOR = UUID.fromString("7c4f2e8a-1111-4abc-9def-000000000012");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void removeRows() {
        CaseTestData.deleteAll(jdbc);
    }

    private static String body(String referenceNo, String title, String caseType, String subject, String openedOn) {
        StringBuilder json = new StringBuilder("{");
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("referenceNo", referenceNo);
        fields.put("title", title);
        fields.put("caseType", caseType);
        fields.put("subject", subject);
        fields.put("openedOn", openedOn);
        fields.forEach((name, value) -> {
            if (value != null) {
                json.append(json.length() > 1 ? "," : "").append('"').append(name).append("\":\"").append(value)
                        .append('"');
            }
        });
        return json.append('}').toString();
    }

    private static String valid() {
        return body(null, "Ahmed v. Nile Trading Co.", "COMMERCIAL", "Unpaid invoices", "2026-03-01");
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post(CASES).with(as(ACTOR, "maha", Permission.CASE_CREATE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private static String generated(int number) {
        return "LD-%d-%05d".formatted(Year.now(ZoneOffset.UTC).getValue(), number);
    }

    @Test
    void createsACaseWithAGeneratedReferenceNumberAndAnswersWithTheStoredCase() throws Exception {
        String response = create(valid())
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.referenceNo").value(generated(1)))
                .andExpect(jsonPath("$.title").value("Ahmed v. Nile Trading Co."))
                .andExpect(jsonPath("$.caseType").value("COMMERCIAL"))
                .andExpect(jsonPath("$.subject").value("Unpaid invoices"))
                .andExpect(jsonPath("$.openedOn").value("2026-03-01"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdBy").value(ACTOR.toString()))
                .andExpect(jsonPath("$.updatedBy").value(ACTOR.toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(response, "$.id");
        assertThat(UUID.fromString(id)).isNotNull();
        create(valid()).andExpect(header().string("Location", containsString(CASES + "/")))
                .andExpect(jsonPath("$.referenceNo").value(generated(2)));
        assertThat(count("legal_case")).isEqualTo(2);
    }

    @Test
    void theLocationPointsAtTheCreatedCase() throws Exception {
        var response = create(valid()).andReturn().getResponse();
        String id = JsonPath.read(response.getContentAsString(), "$.id");

        assertThat(response.getHeader("Location")).endsWith(CASES + "/" + id);
        mvc.perform(get(CASES + "/" + id).with(as(Permission.CASE_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceNo").value(generated(1)));
    }

    @Test
    void acceptsASuppliedReferenceNumberAndTrimsTextInput() throws Exception {
        create(body("  123/2026-Cairo ", "  Title  ", "CIVIL", "   ", LocalDate.now().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceNo").value("123/2026-Cairo"))
                .andExpect(jsonPath("$.title").value("Title"))
                .andExpect(jsonPath("$.subject").doesNotExist());
    }

    @Test
    void aBlankReferenceNumberMeansGenerateOne() throws Exception {
        create(body("  ", "Title", "CIVIL", null, "2026-01-01")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceNo").value(generated(1)));
    }

    @Test
    void statusCannotBeChosenOnCreation() throws Exception {
        create("{\"title\":\"T\",\"caseType\":\"CIVIL\",\"openedOn\":\"2026-01-01\",\"status\":\"CLOSED\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void readsTheStoredCase() throws Exception {
        String created = create(valid()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(get(CASES + "/" + id).with(as(Permission.CASE_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.referenceNo").value(generated(1)))
                .andExpect(jsonPath("$.title").value("Ahmed v. Nile Trading Co."))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdBy").value(ACTOR.toString()));
    }

    @Test
    void recordsTheCreationInTheTimeline() throws Exception {
        String id = JsonPath.read(create(valid()).andReturn().getResponse().getContentAsString(), "$.id");

        List<Map<String, Object>> events = jdbc.queryForList("select * from case_event where case_id = ?::uuid", id);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).containsEntry("type", "CASE_CREATED").containsEntry("created_by", ACTOR);
        assertThat((String) events.getFirst().get("summary")).contains(generated(1));
        assertThat(events.getFirst().get("occurred_at")).isNotNull();
    }

    @Test
    void missingRequiredFieldsAreListedAndNothingIsStored() throws Exception {
        create("{}").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "caseType", "openedOn")))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].code").value("NotBlank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'caseType')].code").value("NotNull"))
                .andExpect(jsonPath("$.errors[?(@.field == 'openedOn')].code").value("NotNull"));
        create(body(null, "   ", "CIVIL", null, "2026-01-01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));

        assertThat(count("legal_case")).isZero();
        assertThat(count("case_event")).isZero();
    }

    @Test
    void lengthBoundariesAreEnforcedWithoutEchoingValues() throws Exception {
        create(body("r".repeat(40), "t".repeat(200), "CIVIL", "s".repeat(4000), "2026-01-01"))
                .andExpect(status().isCreated());
        create(body("q".repeat(41), "u".repeat(201), "CIVIL", "v".repeat(4001), "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("referenceNo", "title", "subject")))
                .andExpect(jsonPath("$.errors[*].code", containsInAnyOrder("Size", "Size", "Size")))
                .andExpect(content().string(not(containsString("uuuu"))));
        assertThat(count("legal_case")).isEqualTo(1);
    }

    @Test
    void referenceNumberFormatIsChecked() throws Exception {
        for (String bad : List.of("-abc", "a b", "قضية-1", "a*b")) {
            create(body(bad, "T", "CIVIL", null, "2026-01-01")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("referenceNo"))
                    .andExpect(jsonPath("$.errors[0].code").value("Pattern"));
        }
        assertThat(count("legal_case")).isZero();
    }

    @Test
    void referenceNumbersInTheGeneratedFormatAreReserved() throws Exception {
        for (String reserved : List.of(generated(7), "ld-1")) {
            create(body(reserved, "T", "CIVIL", null, "2026-01-01")).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.rule").value("REFERENCE_NO_RESERVED"));
        }
        create(body("LD-ABC", "T", "CIVIL", null, "2026-01-01")).andExpect(status().isCreated());
        assertThat(count("legal_case")).isEqualTo(1);
    }

    @Test
    void openedOnMayBeTodayButNotInTheFuture() throws Exception {
        create(body(null, "T", "CIVIL", null, LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("openedOn"))
                .andExpect(jsonPath("$.errors[0].code").value("PastOrPresent"));
        create(body(null, "T", "CIVIL", null, LocalDate.now().toString())).andExpect(status().isCreated());
    }

    @Test
    void unknownCaseTypeIsAFieldError() throws Exception {
        create(body(null, "T", "TAX", null, "2026-01-01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("caseType"))
                .andExpect(jsonPath("$.errors[0].code").value("Pattern"));
    }

    @Test
    void malformedJsonOrDateIsABadRequest() throws Exception {
        create("{").andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        create(body(null, "T", "CIVIL", null, "01/03/2026")).andExpect(status().isBadRequest());
        assertThat(count("legal_case")).isZero();
    }

    @Test
    void aDuplicateReferenceNumberIsAConflictIgnoringCase() throws Exception {
        create(body("Cairo-55", "T", "CIVIL", null, "2026-01-01")).andExpect(status().isCreated());

        create(body("cAIRO-55", "Other", "CIVIL", null, "2026-01-01")).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        assertThat(count("legal_case")).isEqualTo(1);
        assertThat(count("case_event")).isEqualTo(1);
    }

    @Test
    void concurrentCreationsWithTheSameReferenceNumberStoreOneCase() {
        List<Integer> statuses = concurrently(8, i -> body("SAME-1", "T" + i, "CIVIL", null, "2026-01-01"));

        assertThat(statuses).containsOnly(201, 409).filteredOn(s -> s == 201).hasSize(1);
        assertThat(count("legal_case")).isEqualTo(1);
    }

    @Test
    void concurrentCreationsGetDistinctConsecutiveReferenceNumbers() {
        List<Integer> statuses = concurrently(10, i -> body(null, "T" + i, "CIVIL", null, "2026-01-01"));

        assertThat(statuses).containsOnly(201);
        List<String> numbers = jdbc.queryForList("select reference_no from legal_case order by reference_no",
                String.class);
        assertThat(numbers).containsExactlyElementsOf(IntStream.rangeClosed(1, 10).mapToObj(CaseApiTest::generated)
                .toList());
    }

    private List<Integer> concurrently(int requests, java.util.function.IntFunction<String> bodies) {
        CyclicBarrier start = new CyclicBarrier(requests);
        List<CompletableFuture<Integer>> calls = IntStream.range(0, requests)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                        return create(bodies.apply(i)).andReturn().getResponse().getStatus();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()))
                .toList();
        return calls.stream().map(CompletableFuture::join).toList();
    }

    @Test
    void unknownOrMalformedIdIsNotFound() throws Exception {
        mvc.perform(get(CASES + "/" + UUID.randomUUID()).with(as(Permission.CASE_VIEW)))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(CASES + "/not-a-uuid").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void withoutASessionTheAnswerIs401AndNothingIsStored() throws Exception {
        mvc.perform(post(CASES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(CASES + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        assertThat(count("legal_case")).isZero();
    }

    @Test
    void withoutThePermissionTheAnswerIs403AndNothingIsStored() throws Exception {
        mvc.perform(post(CASES).with(as(Permission.CASE_VIEW, Permission.CASE_EDIT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        String id = JsonPath.read(create(valid()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get(CASES + "/" + id).with(as(Permission.CASE_CREATE))).andExpect(status().isForbidden());
        assertThat(count("legal_case")).isEqualTo(1);
    }

    @Test
    void creatingNeedsTheCsrfToken() throws Exception {
        mvc.perform(post(CASES).with(as(Permission.CASE_CREATE)).contentType(MediaType.APPLICATION_JSON)
                .content(valid())).andExpect(status().isForbidden());
        assertThat(count("legal_case")).isZero();
    }
}
