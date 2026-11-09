package com.lawdesk.cases;

import static com.lawdesk.security.TestActors.as;
import static com.lawdesk.security.TestActors.scoped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * Requirements sections 1 and 6 (LD-050): the rest of the case record, one set of rules for the intake, the edit and the
 * older create endpoint, the units a case concerns with the structure's names frozen at the time, and the wider search.
 */
class CaseDetailsApiTest extends GovernmentIntakeTestSupport {

    private static final String DETAILS = """
            {"chamber":"الدائرة الإدارية الثالثة","receivedOn":"2026-01-20","referredBy":"مكتب الأمين العام",
             "facts":"ملخص الوقائع","claimedAmount":"12500.500","importance":"HIGH","internalNotes":"ملاحظة داخلية"}""";

    private UUID specialist;
    private UUID court;
    private RequestPostProcessor clerk;

    @BeforeEach
    void actors() {
        specialist = member("Specialist");
        court = firstInstanceCourt();
        clerk = registrar(member("Clerk"));
    }

    private RequestPostProcessor specialistActor() {
        return as(specialist, "specialist", Permission.CASE_VIEW, Permission.CASE_EDIT, Permission.PARTY_MANAGE);
    }

    private UUID unit(String tree, String level, String name, UUID parent) {
        return jdbc.queryForObject("""
                insert into org_unit (tree, parent_id, level, name_ar, created_by, updated_by)
                values (?, ?, ?, ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class, tree, parent,
                level, name);
    }

    @Test
    void theIntakeKeepsTheDetailsAndTheDetailsEndpointReturnsThemAsTyped() throws Exception {
        UUID id = registered(clerk, intake(court, specialist, "\"details\":" + DETAILS));
        mvc.perform(getCase(as(Permission.CASE_VIEW), id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.chamber").value("الدائرة الإدارية الثالثة"))
                .andExpect(jsonPath("$.details.receivedOn").value("2026-01-20"))
                .andExpect(jsonPath("$.details.claimedAmount").value("12500.500"))
                .andExpect(jsonPath("$.details.importance").value("HIGH"))
                .andExpect(jsonPath("$.details.referredBy").value("مكتب الأمين العام"))
                .andExpect(jsonPath("$.financialImpact").value("NO"));
        assertThat(jdbc.queryForObject("select claimed_amount from legal_case where id = ?", java.math.BigDecimal.class,
                id)).isEqualByComparingTo("12500.500");
    }

    @Test
    void aCaseWithoutDetailsHasNoneAndNothingIsInvented() throws Exception {
        UUID id = registered(clerk, intake(court, specialist, null));
        mvc.perform(getCase(as(Permission.CASE_VIEW), id)).andExpect(jsonPath("$.details.chamber").doesNotExist())
                .andExpect(jsonPath("$.details.claimedAmount").doesNotExist())
                .andExpect(jsonPath("$.details.importance").doesNotExist())
                .andExpect(jsonPath("$.details.empty").doesNotExist());
    }

    @Test
    void theSameRulesApplyToTheIntakeTheEditAndTheOlderCreateEndpoint() throws Exception {
        String bad = "{\"importance\":\"URGENT\"}";
        String negative = "{\"claimedAmount\":\"-1\"}";
        String fourDecimals = "{\"claimedAmount\":\"1.2345\"}";
        for (String details : new String[] {bad, negative, fourDecimals}) {
            mvc.perform(register(clerk, intake(court, specialist, "\"details\":" + details)))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/v1/cases").with(clerk).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"T\",\"caseType\":\"CIVIL\",\"openedOn\":\"2026-01-01\",\"details\":"
                                    + details + "}"))
                    .andExpect(status().isBadRequest());
        }
        UUID id = registered(clerk, intake(court, specialist, null));
        for (String details : new String[] {bad, negative, fourDecimals}) {
            mvc.perform(putCase(specialistActor(), id, "{\"title\":\"x\",\"caseType\":\"COMMERCIAL\","
                            + "\"openedOn\":\"2026-01-10\",\"version\":0,\"details\":" + details + "}"))
                    .andExpect(status().isBadRequest());
        }
        // The older endpoint takes the details too, and its case is seen only by the creator until distributed.
        String created = mvc.perform(post("/api/v1/cases").with(clerk).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Legacy\",\"caseType\":\"OTHER\",\"openedOn\":\"2026-01-01\",\"details\":"
                                + DETAILS + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.details.importance").value("HIGH"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(created, "$.caseType")).isEqualTo("OTHER");
    }

    @Test
    void anEditWithDetailsReplacesThemAsAWholeAndWithoutThemLeavesThemAlone() throws Exception {
        UUID id = registered(clerk, intake(court, specialist, "\"details\":" + DETAILS));
        String base = "{\"title\":\"Renamed\",\"caseType\":\"COMMERCIAL\",\"openedOn\":\"2026-01-10\",\"version\":";
        mvc.perform(putCase(specialistActor(), id, base + "0}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.chamber").value("الدائرة الإدارية الثالثة"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(putCase(specialistActor(), id, base + "1,\"details\":{\"importance\":\"LOW\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.details.importance").value("LOW"))
                .andExpect(jsonPath("$.details.chamber").doesNotExist())
                .andExpect(jsonPath("$.details.claimedAmount").doesNotExist());
        assertThat(count("select count(*) from audit_event where action = 'CASE_CLAIMED_AMOUNT_CHANGE'"
                + " and target_id = ?", id.toString())).isEqualTo(1);
    }

    @Test
    void theReferringUnitMustBeAnActiveUnitOfTheAuthority() throws Exception {
        UUID legal = unit("LEGAL", "DEPARTMENT", "legal", null);
        UUID sector = unit("AUTHORITY", "SECTOR", "قطاع الخدمات المساندة", null);
        mvc.perform(register(clerk, intake(court, specialist, "\"details\":{\"referringUnitId\":\"" + legal + "\"}")))
                .andExpect(status().isBadRequest());
        UUID id = registered(clerk, intake(court, specialist, "\"details\":{\"referringUnitId\":\"" + sector + "\"}"));
        mvc.perform(getCase(as(Permission.CASE_VIEW), id))
                .andExpect(jsonPath("$.details.referringUnitId").value(sector.toString()));
    }

    @Test
    void unitsConcernedByTheCaseKeepTheStructureTheyWereRecordedUnder() throws Exception {
        UUID sector = unit("AUTHORITY", "SECTOR", "قطاع الخدمات المساندة", null);
        UUID department = unit("AUTHORITY", "DEPARTMENT", "إدارة الشؤون الإدارية", sector);
        UUID section = unit("AUTHORITY", "SECTION", "قسم شؤون الموظفين", department);
        UUID support = unit("AUTHORITY", "SECTION", "قسم الحسابات", department);
        UUID id = registered(clerk, intake(court, specialist, null));
        String path = "/api/v1/cases/" + id + "/involvements";
        RequestPostProcessor member = specialistActor();

        String primary = mvc.perform(post(path).with(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgUnitId\":\"" + section + "\",\"primary\":true,\"contactName\":\"سارة\","
                                + "\"referralDate\":\"2026-01-15\",\"requestedFromUnit\":\"صور الملف الوظيفي\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sectorName").value("قطاع الخدمات المساندة"))
                .andExpect(jsonPath("$.departmentName").value("إدارة الشؤون الإدارية"))
                .andExpect(jsonPath("$.sectionName").value("قسم شؤون الموظفين")).andReturn().getResponse()
                .getContentAsString();
        mvc.perform(post(path).with(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgUnitId\":\"" + support + "\",\"primary\":false}")).andExpect(status().isCreated());
        // One primary only; a unit once; an unnamed unit refused; the legal tree is not the authority's.
        mvc.perform(post(path).with(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionName\":\"قسم خارجي\",\"primary\":true}")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("INVOLVEMENT_PRIMARY_EXISTS"));
        mvc.perform(post(path).with(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgUnitId\":\"" + support + "\",\"primary\":false}")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("INVOLVEMENT_ALREADY_RECORDED"));
        mvc.perform(post(path).with(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"primary\":false}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).with(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgUnitId\":\"" + unit("LEGAL", "DEPARTMENT", "legal", null) + "\",\"primary\":false}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("INVOLVEMENT_UNIT_NOT_AUTHORITY"));

        // The structure changes afterwards: the case keeps the names it was registered under.
        jdbc.update("update org_unit set name_ar = 'قسم جديد', active = false where id in (?, ?, ?)", section, department,
                sector);
        mvc.perform(get(path).with(as(Permission.CASE_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[0].sectionName").value("قسم شؤون الموظفين"))
                .andExpect(jsonPath("$[0].departmentName").value("إدارة الشؤون الإدارية"));

        String involvementId = JsonPath.read(primary, "$.id");
        mvc.perform(put(path + "/" + involvementId).with(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"primary\":true,\"contactName\":\"هند\",\"version\":0}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contactName").value("هند")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put(path + "/" + involvementId).with(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"primary\":true,\"version\":0}")).andExpect(status().isConflict());
        mvc.perform(delete(path + "/" + involvementId).with(member).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(path).with(as(Permission.CASE_VIEW))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(path + "?includeRemoved=true").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.length()").value(2));
        // A removed unit does not count against the one-primary rule: a new primary can be recorded.
        mvc.perform(post(path).with(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionName\":\"قسم آخر\",\"primary\":true}")).andExpect(status().isCreated());
    }

    @Test
    void onlyThoseWhoMayChangeTheCaseRecordInvolvements() throws Exception {
        UUID id = registered(clerk, intake(court, specialist, null));
        String path = "/api/v1/cases/" + id + "/involvements";
        mvc.perform(post(path).with(scoped(member("Other"), "other", List.of("ALL"), Permission.CASE_VIEW, Permission.CASE_EDIT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"sectionName\":\"x\",\"primary\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).with(as(Permission.CASE_VIEW)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionName\":\"x\",\"primary\":false}")).andExpect(status().isForbidden());
    }

    @Test
    void theSearchFindsACaseByItsSubjectItsNumbersItsMembersAndTheUnitsItConcerns() throws Exception {
        UUID section = unit("AUTHORITY", "SECTION", "قسم شؤون الموظفين", null);
        UUID id = registered(clerk, intake(court, specialist, null));
        mvc.perform(post("/api/v1/cases/" + id + "/involvements").with(specialistActor()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgUnitId\":\"" + section + "\",\"primary\":true,\"contactName\":\"المهندس خالد\"}"))
                .andExpect(status().isCreated());
        for (String q : new String[] {"Breach of supply", "000123", "4471", "Specialist", "المهندس خالد", "شؤون الموظفين"}) {
            mvc.perform(get("/api/v1/cases").param("q", q).with(as(Permission.CASE_VIEW))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1));
        }
        mvc.perform(get("/api/v1/cases").param("q", "no such text").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }
}
