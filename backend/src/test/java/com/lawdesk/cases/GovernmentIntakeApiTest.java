package com.lawdesk.cases;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.lawdesk.security.Permission;

/**
 * LD-045 government case intake (docs/delivery/GOVERNMENT-INTAKE-ACCEPTANCE.md): the two case numbers, the authority as
 * a party, the specialist and alternate members, the financial impact and the compatibility of older cases, through the
 * real filter chain and PostgreSQL.
 */
class GovernmentIntakeApiTest extends GovernmentIntakeTestSupport {

    private static final UUID REGISTRAR_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    // --- the registration -------------------------------------------------------------------------------------

    @Test
    void registersTheWholeCaseInOneRequest() throws Exception {
        UUID specialist = member("Dr. Specialist");
        UUID alternate = member("Alternate Member", "LEGAL_MANAGER", true);
        UUID court = firstInstanceCourt();
        String json = intake(court, specialist, """
                "alternateMemberId":"%s","financialImpact":"YES","financialAmount":"15250.500",
                "parties":[{"newParty":{"kind":"PERSON","name":"Nile Trading Co.","nationalId":"00112233"},
                            "side":"DEFENDANT"}]""".formatted(alternate)).replace("\"financialImpact\":\"NO\",", "");

        mvc.perform(register(registrar(REGISTRAR_ID), json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.automatedNo").value("000123"))
                .andExpect(jsonPath("$.referenceNo").value(org.hamcrest.Matchers.startsWith("LD-")))
                .andExpect(jsonPath("$.authorityPosition").value("PLAINTIFF"))
                .andExpect(jsonPath("$.financialImpact").value("YES"))
                .andExpect(jsonPath("$.financialAmount").value("15250.500"))
                .andExpect(jsonPath("$.primaryMember.id").value(specialist.toString()))
                .andExpect(jsonPath("$.primaryMember.displayName").value("Dr. Specialist"))
                .andExpect(jsonPath("$.alternateMember.id").value(alternate.toString()));

        assertThat(count("select count(*) from legal_case")).isEqualTo(1);
        // The first stage carries its own number, separate from the automated case number.
        assertThat(jdbc.queryForMap("select stage, court_case_number, court_case_year from proceeding"))
                .containsEntry("stage", "FIRST_INSTANCE").containsEntry("court_case_number", "4471");
        // No legal-operations records are needed (or created) to register a case.
        assertThat(count("select count(*) from hearing") + count("select count(*) from task")
                + count("select count(*) from expert_referral") + count("select count(*) from case_note")).isZero();
        assertThat(jdbc.queryForObject("select financial_amount from legal_case", BigDecimal.class))
                .isEqualByComparingTo("15250.500");
    }

    @Test
    void keepsTheAutomatedNumberAsTextWithItsLeadingZeros() throws Exception {
        UUID id = registered(registrar(REGISTRAR_ID), intake(firstInstanceCourt(), member("S"), null));
        mvc.perform(getCase(registrar(REGISTRAR_ID), id)).andExpect(jsonPath("$.automatedNo").value("000123"));
        assertThat(jdbc.queryForObject("select automated_no from legal_case", String.class)).isEqualTo("000123");
    }

    @Test
    void requiresBothNumbersTheAuthorityPositionTheSpecialistAndTheFinancialChoice() throws Exception {
        UUID court = firstInstanceCourt();
        String body = """
                {"title":"T","caseType":"CIVIL","openedOn":"2026-01-10"}""";
        mvc.perform(register(registrar(REGISTRAR_ID), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("automatedNo", "firstInstance",
                        "authorityPosition", "primaryMemberId", "financialImpact")));
        String noStageNumber = intake(court, member("S"), null)
                .replace("\"courtCaseNumber\":\"4471\",", "\"courtCaseNumber\":\"  \",");
        mvc.perform(register(registrar(REGISTRAR_ID), noStageNumber)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("firstInstance.courtCaseNumber"));
        mvc.perform(register(registrar(REGISTRAR_ID),
                        intake(court, member("S"), null).replace("\"000123\"", "\"   \"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("automatedNo"));
        assertThat(count("select count(*) from legal_case")).isZero();
    }

    @Test
    void acceptsAnyVisibleAutomatedNumberWithoutAFormatRule() throws Exception {
        UUID court = firstInstanceCourt();
        for (String number : List.of("7", "AB/2026/(19)-x", "٠٠١٢٣", "0".repeat(100))) {
            mvc.perform(register(registrar(REGISTRAR_ID),
                            intake(court, member("S"), null).replace("\"000123\"", "\"" + number + "\"")
                                    .replace("\"4471\"", "\"" + number.substring(0, Math.min(number.length(), 30)) + "\"")))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.automatedNo").value(number));
        }
        mvc.perform(register(registrar(REGISTRAR_ID),
                        intake(court, member("S"), null).replace("\"000123\"", "\"" + "9".repeat(101) + "\"")))
                .andExpect(status().isBadRequest());
    }

    // --- the stages: same automated number, one number per stage ------------------------------------------------

    @Test
    void anotherStageKeepsTheCaseNumberAndTheEarlierStageNumber() throws Exception {
        UUID specialist = member("S");
        UUID id = registered(as(specialist, "specialist", REGISTRAR), intake(firstInstanceCourt(), specialist, null));
        UUID firstId = jdbc.queryForObject("select id from proceeding", UUID.class);
        jdbc.update("insert into judgment (proceeding_id, judgment_date, outcome, final, created_by, updated_by)"
                + " values (?, '2026-02-01', 'AGAINST', false, gen_random_uuid(), gen_random_uuid())", firstId);
        UUID appealCourt = court("AP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "APPEAL", true);

        mvc.perform(post("/api/v1/cases/" + id + "/proceedings").with(as(specialist, "specialist", REGISTRAR))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"APPEAL","courtId":"%s","courtCaseNumber":"918","courtCaseYear":2026,
                                 "filingDate":"2026-02-10","parentProceedingId":"%s"}""".formatted(appealCourt, firstId)))
                .andExpect(status().isCreated());

        // Both stage numbers are kept, each with its stage, and the case number is the one entered once.
        mvc.perform(get("/api/v1/cases/" + id + "/proceedings").with(registrar(specialist)))
                .andExpect(jsonPath("$.items[*].stage").value(org.hamcrest.Matchers.contains("FIRST_INSTANCE", "APPEAL")))
                .andExpect(jsonPath("$.items[*].courtCaseNumber").value(org.hamcrest.Matchers.contains("4471", "918")));
        mvc.perform(getCase(registrar(specialist), id)).andExpect(jsonPath("$.automatedNo").value("000123"));
    }

    // --- the authority and the parties -----------------------------------------------------------------------

    @Test
    void theAuthorityIsAPartyOnItsSideWithMorePartiesOnBothSides() throws Exception {
        UUID registrarId = REGISTRAR_ID;
        String parties = """
                "parties":[
                  {"newParty":{"kind":"ORGANIZATION","name":"Co-plaintiff Agency"},"side":"PLAINTIFF"},
                  {"newParty":{"kind":"PERSON","name":"First Defendant","nationalId":"123"},"side":"DEFENDANT"},
                  {"newParty":{"kind":"PERSON","name":"Second Defendant","nationalId":"456"},"side":"DEFENDANT"}]""";
        UUID id = registered(registrar(registrarId), intake(firstInstanceCourt(), member("S"), parties));

        mvc.perform(get("/api/v1/cases/" + id + "/parties").with(registrar(registrarId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.authority == true)].side").value(org.hamcrest.Matchers.contains("PLAINTIFF")))
                .andExpect(jsonPath("$[?(@.authority == true)].role").value(org.hamcrest.Matchers.contains("CLIENT")))
                .andExpect(jsonPath("$[?(@.side == 'PLAINTIFF')].name",
                        containsInAnyOrder("Co-plaintiff Agency", "الهيئة")))
                .andExpect(jsonPath("$[?(@.side == 'DEFENDANT')].role",
                        containsInAnyOrder("OPPONENT", "OPPONENT")));
    }

    @Test
    void theAuthorityCanBeTheDefendantWithOpposingPlaintiffs() throws Exception {
        String parties = """
                "parties":[{"newParty":{"kind":"PERSON","name":"Claimant A"},"side":"PLAINTIFF"},
                           {"newParty":{"kind":"PERSON","name":"Claimant B"},"side":"PLAINTIFF"},
                           {"newParty":{"kind":"ORGANIZATION","name":"Co-defendant Agency"},"side":"DEFENDANT"}]""";
        UUID id = registered(registrar(REGISTRAR_ID), intake(firstInstanceCourt(), member("S"), parties)
                .replace("\"PLAINTIFF\",\"primary", "\"DEFENDANT\",\"primary"));

        mvc.perform(get("/api/v1/cases/" + id + "/parties").with(registrar(REGISTRAR_ID)))
                .andExpect(jsonPath("$[?(@.authority == true)].side").value(org.hamcrest.Matchers.contains("DEFENDANT")))
                .andExpect(jsonPath("$[?(@.side == 'PLAINTIFF')].role", containsInAnyOrder("OPPONENT", "OPPONENT")))
                .andExpect(jsonPath("$[?(@.name == 'Co-defendant Agency')].role").value(org.hamcrest.Matchers.contains("OTHER")));
        mvc.perform(getCase(registrar(REGISTRAR_ID), id)).andExpect(jsonPath("$.authorityPosition").value("DEFENDANT"));
    }

    @Test
    void thePartyNumberIsIndependentOfTheCaseNumberAndKeepsWhatWasTyped() throws Exception {
        UUID id = registered(registrar(REGISTRAR_ID), intake(firstInstanceCourt(), member("S"), """
                "parties":[{"newParty":{"kind":"PERSON","name":"A Person","nationalId":"000123"},"side":"DEFENDANT"}]"""));
        assertThat(jdbc.queryForObject("select national_id from party where name = 'A Person'", String.class))
                .isEqualTo("000123");
        // The same party number on another case is the same party (numbers identify parties); the case number is its own.
        mvc.perform(post("/api/v1/parties").with(as(Permission.PARTY_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PERSON\",\"name\":\"Other\",\"nationalId\":\"000123\"}"))
                .andExpect(status().isConflict());
        assertThat(id).isNotNull();
    }

    @Test
    void aRegisteredPartyCanBeUsedOnceAndTheAuthorityNeverTwice() throws Exception {
        UUID first = registered(registrar(REGISTRAR_ID), intake(firstInstanceCourt(), member("S"), """
                "parties":[{"newParty":{"kind":"ORGANIZATION","name":"Shared Org"},"side":"DEFENDANT"}]"""));
        UUID shared = jdbc.queryForObject("select id from party where name = 'Shared Org'", UUID.class);
        UUID authority = jdbc.queryForObject("select id from party where authority", UUID.class);
        UUID court = firstInstanceCourt();
        UUID s = member("S2");
        mvc.perform(register(registrar(REGISTRAR_ID), intake(court, s, """
                        "parties":[{"partyId":"%s","side":"DEFENDANT"},{"partyId":"%s","side":"PLAINTIFF"}]"""
                        .formatted(shared, shared))))
                .andExpect(status().isBadRequest());
        mvc.perform(register(registrar(REGISTRAR_ID), intake(court, s, """
                        "parties":[{"partyId":"%s","side":"PLAINTIFF"}]""".formatted(authority))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("PARTY_IS_AUTHORITY"));
        mvc.perform(register(registrar(REGISTRAR_ID), intake(court, s, """
                        "parties":[{"partyId":"%s","side":"DEFENDANT"}]""".formatted(shared))))
                .andExpect(status().isCreated());
        assertThat(count("select count(*) from party where authority")).isEqualTo(1);
        assertThat(first).isNotNull();
    }

    @Test
    void partiesNeedThePartyPermissionAndEachEntryNeedsOneIdentity() throws Exception {
        String parties = """
                "parties":[{"newParty":{"kind":"PERSON","name":"X"},"side":"DEFENDANT"}]""";
        mvc.perform(register(as(REGISTRAR_ID, "r", Permission.CASE_CREATE, Permission.PROCEEDING_MANAGE),
                        intake(firstInstanceCourt(), member("S"), parties)))
                .andExpect(status().isForbidden());
        mvc.perform(register(registrar(REGISTRAR_ID), intake(firstInstanceCourt(), member("S"), """
                        "parties":[{"side":"DEFENDANT"}]""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parties[0].partyId"));
        assertThat(count("select count(*) from legal_case")).isZero();
    }

    // --- the specialist and the alternate member --------------------------------------------------------------

    @Test
    void membersMustBeActiveLegalDepartmentMembersAndDifferentPeople() throws Exception {
        UUID court = firstInstanceCourt();
        UUID active = member("Active");
        UUID disabled = member("Disabled", "LEGAL_STAFF", false);
        UUID administrator = member("Not Legal", "ADMIN", true);
        UUID noRole = member("No Role", null, true);
        for (UUID bad : List.of(disabled, administrator, noRole, UUID.randomUUID())) {
            mvc.perform(register(registrar(REGISTRAR_ID), intake(court, bad, null)))
                    .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("MEMBER_NOT_ELIGIBLE"));
            mvc.perform(register(registrar(REGISTRAR_ID), intake(court, active, "\"alternateMemberId\":\"" + bad + "\"")))
                    .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("MEMBER_NOT_ELIGIBLE"));
        }
        mvc.perform(register(registrar(REGISTRAR_ID), intake(court, active, "\"alternateMemberId\":\"" + active + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("alternateMemberId"))
                .andExpect(jsonPath("$.errors[0].code").value("SameAsPrimary"));
        assertThat(count("select count(*) from legal_case")).isZero();
        mvc.perform(register(registrar(REGISTRAR_ID), intake(court, active, null))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.alternateMember").doesNotExist());
    }

    @Test
    void listsOnlyActiveDepartmentMembersForTheSelectors() throws Exception {
        member("Zed Staff");
        member("Amal Manager", "LEGAL_MANAGER", true);
        member("Off Duty", "LEGAL_STAFF", false);
        member("Plain Admin", "ADMIN", true);
        mvc.perform(get("/api/v1/cases/members").with(registrar(REGISTRAR_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].displayName", org.hamcrest.Matchers.hasItems("Amal Manager", "Zed Staff")))
                .andExpect(jsonPath("$[*].displayName", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Off Duty"))))
                .andExpect(jsonPath("$[*].displayName", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Plain Admin"))))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
        mvc.perform(get("/api/v1/cases/members").with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cases/members")).andExpect(status().isUnauthorized());
    }

    // --- financial impact ---------------------------------------------------------------------------------------

    @Test
    void aFinancialImpactNeedsAPositiveAmountOfAtMostThreeDecimals() throws Exception {
        UUID s = member("S");
        // A fresh court per request: the same stage number at one court is refused as a duplicate.
        java.util.function.Function<String, String> yes = amount -> intake(firstInstanceCourt(), s,
                "\"financialAmount\":" + amount).replace("\"financialImpact\":\"NO\"", "\"financialImpact\":\"YES\"");
        for (String amount : List.of("\"0\"", "\"-5\"", "\"1.2345\"", "\"abc\"", "\"1234567890123\"", "null")) {
            mvc.perform(register(registrar(REGISTRAR_ID), yes.apply(amount))).andExpect(status().isBadRequest());
        }
        mvc.perform(register(registrar(REGISTRAR_ID),
                        intake(firstInstanceCourt(), s, null).replace("\"NO\"", "\"YES\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("financialAmount"));
        for (String ok : List.of("\"0.001\"", "\"1250.5\"", "12.345", "\"999999999999.999\"")) {
            mvc.perform(register(registrar(REGISTRAR_ID), yes.apply(ok))).andExpect(status().isCreated());
        }
        assertThat(jdbc.queryForList("select financial_amount from legal_case order by financial_amount", BigDecimal.class))
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("0.001"), new BigDecimal("12.345"), new BigDecimal("1250.500"),
                        new BigDecimal("999999999999.999"));
    }

    @Test
    void noFinancialImpactStoresNoAmountAndRefusesOne() throws Exception {
        UUID court = firstInstanceCourt();
        UUID s = member("S");
        mvc.perform(register(registrar(REGISTRAR_ID), intake(court, s, "\"financialAmount\":\"500.000\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("financialAmount"));
        mvc.perform(register(registrar(REGISTRAR_ID), intake(court, s, null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.financialImpact").value("NO"))
                .andExpect(jsonPath("$.financialAmount").doesNotExist());
        assertThat(jdbc.queryForObject("select financial_amount from legal_case", BigDecimal.class)).isNull();
    }

    @Test
    void switchingAnEditToNoImpactClearsTheStoredAmount() throws Exception {
        UUID s = member("S");
        UUID id = registered(as(s, "s", REGISTRAR), intake(firstInstanceCourt(), s, "\"financialAmount\":\"700.250\"")
                .replace("\"financialImpact\":\"NO\"", "\"financialImpact\":\"YES\""));
        String edit = """
                {"title":"Ministry v. Nile Trading","caseType":"COMMERCIAL","openedOn":"2026-01-10","version":%d,%s}""";
        mvc.perform(putCase(as(s, "s", REGISTRAR), id, edit.formatted(0, "\"financialImpact\":\"NO\",\"financialAmount\":\"1.000\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(putCase(as(s, "s", REGISTRAR), id, edit.formatted(0, "\"financialImpact\":\"NO\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.financialImpact").value("NO"))
                .andExpect(jsonPath("$.financialAmount").doesNotExist());
        assertThat(jdbc.queryForObject("select financial_amount from legal_case", BigDecimal.class)).isNull();
        // An edit that says nothing about the impact leaves it; an amount without an impact is refused.
        mvc.perform(putCase(as(s, "s", REGISTRAR), id, edit.formatted(1, "\"automatedNo\":\"A-9\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.financialImpact").value("NO"))
                .andExpect(jsonPath("$.automatedNo").value("A-9"));
        mvc.perform(putCase(as(s, "s", REGISTRAR), id, edit.formatted(2, "\"financialAmount\":\"5.000\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(putCase(as(s, "s", REGISTRAR), id, edit.formatted(2, "\"financialImpact\":\"YES\",\"financialAmount\":\"42.125\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.financialAmount").value("42.125"));
        assertThat(count("select count(*) from audit_event where action = 'CASE_FINANCIAL_IMPACT_CHANGE'")).isEqualTo(2);
        assertThat(count("select count(*) from audit_event where action = 'CASE_AUTOMATED_NO_CHANGE'")).isEqualTo(1);
    }

    // --- atomicity and audit ------------------------------------------------------------------------------------

    @Test
    void aRefusedRegistrationLeavesNothingBehind() throws Exception {
        int parties = count("select count(*) from party");
        int auditBefore = count("select count(*) from audit_event where action in ('CASE_CREATE', 'CASE_ASSIGNMENT_CHANGE')"
                + " and outcome = 'SUCCESS' and occurred_at > now() - interval '1 minute'");
        UUID appealCourt = court("AP-ONLY", "APPEAL", true);
        mvc.perform(register(registrar(REGISTRAR_ID), intake(appealCourt, member("S"), """
                        "parties":[{"newParty":{"kind":"PERSON","name":"Rolled Back"},"side":"DEFENDANT"}]""")))
                .andExpect(status().isUnprocessableContent());
        UUID inactive = court("FI-OFF", "FIRST_INSTANCE", false);
        mvc.perform(register(registrar(REGISTRAR_ID), intake(inactive, member("S2"), null)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(register(registrar(REGISTRAR_ID), intake(UUID.randomUUID(), member("S3"), null)))
                .andExpect(status().isNotFound());

        assertThat(count("select count(*) from legal_case")).isZero();
        assertThat(count("select count(*) from proceeding")).isZero();
        assertThat(count("select count(*) from case_party")).isZero();
        assertThat(count("select count(*) from party")).isEqualTo(parties);
        assertThat(count("select count(*) from party where name = 'Rolled Back' or authority")).isZero();
        assertThat(count("select count(*) from audit_event where action in ('CASE_CREATE', 'CASE_ASSIGNMENT_CHANGE')"
                + " and outcome = 'SUCCESS' and occurred_at > now() - interval '1 minute'")).isEqualTo(auditBefore);
    }

    @Test
    void recordsTheRegistrationAndTheAssignmentInTheAuditLogWithoutPersonalData() throws Exception {
        UUID s = member("S");
        UUID id = registered(registrar(REGISTRAR_ID), intake(firstInstanceCourt(), s, null));
        List<Map<String, Object>> events = jdbc.queryForList(
                "select action, target_id, outcome, reason from audit_event where target_id = ? order by id", id.toString());
        assertThat(events).extracting(e -> e.get("action")).contains("CASE_ASSIGNMENT_CHANGE", "CASE_CREATE");
        assertThat(events).allSatisfy(e -> assertThat(e.get("outcome")).isEqualTo("SUCCESS"));
        assertThat(events.toString()).doesNotContain("Ministry").doesNotContain("000123");
        mvc.perform(get("/api/v1/cases/" + id + "/timeline").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.items[*].type", org.hamcrest.Matchers.hasItems("CASE_CREATED", "CASE_ASSIGNED",
                        "PROCEEDING_RECORDED")));
    }

    // --- compatibility ----------------------------------------------------------------------------------------

    @Test
    void casesThatPredateTheIntakeStayReadableEditableAndUnspecified() throws Exception {
        UUID legacy = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by)
                values ('OLD-1', 'Legacy case', 'CIVIL', '2020-01-01', gen_random_uuid(), gen_random_uuid())
                returning id""", UUID.class);
        mvc.perform(getCase(as(Permission.CASE_VIEW), legacy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceNo").value("OLD-1"))
                .andExpect(jsonPath("$.automatedNo").doesNotExist())
                .andExpect(jsonPath("$.financialImpact").doesNotExist())
                .andExpect(jsonPath("$.primaryMember").doesNotExist());
        // Without a specialist, the permission alone decides, as before.
        mvc.perform(putCase(as(Permission.CASE_EDIT), legacy,
                        "{\"title\":\"Legacy case 2\",\"caseType\":\"CIVIL\",\"openedOn\":\"2020-01-01\",\"version\":0}"))
                .andExpect(status().isOk());
        // The unspecified impact is not "no impact"; an edit may specify it.
        mvc.perform(putCase(as(Permission.CASE_EDIT), legacy,
                        "{\"title\":\"Legacy case 2\",\"caseType\":\"CIVIL\",\"openedOn\":\"2020-01-01\",\"version\":1,"
                                + "\"automatedNo\":\"0042\",\"financialImpact\":\"YES\",\"financialAmount\":\"10\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.automatedNo").value("0042"))
                .andExpect(jsonPath("$.financialAmount").value("10.000"));
        // The original creation endpoint keeps working for integrations; its cases are unspecified, not invented.
        mvc.perform(post("/api/v1/cases").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Old way\",\"caseType\":\"CIVIL\",\"openedOn\":\"2024-01-10\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.automatedNo").doesNotExist())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void theDatabaseRefusesInconsistentFinancialAndMemberData() {
        String insert = "insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by,"
                + " financial_impact, financial_amount) values (?, 'T', 'CIVIL', '2024-01-01', gen_random_uuid(),"
                + " gen_random_uuid(), ?, ?)";
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update(insert, "DB-1", "NO", new BigDecimal("5")));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update(insert, "DB-2", "YES", null));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update(insert, "DB-3", "YES", BigDecimal.ZERO));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update(insert, "DB-4", null, new BigDecimal("5")));
        UUID m = member("M");
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by,
                                        primary_member_id, alternate_member_id)
                values ('DB-5', 'T', 'CIVIL', '2024-01-01', gen_random_uuid(), gen_random_uuid(), ?, ?)""", m, m));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by,
                                        alternate_member_id)
                values ('DB-6', 'T', 'CIVIL', '2024-01-01', gen_random_uuid(), gen_random_uuid(), ?)""", m));
    }
}
