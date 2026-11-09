package com.lawdesk.proceedings;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.api.PageResponse;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.Permission;

/**
 * The judgments of every case the user may see, newest first (the judgments screen, requirements section 9). It lists and
 * never changes anything; the case access policy restricts the rows, so a judgment of a case the user may not see is not
 * listed (and is not counted).
 */
@RestController
@RequestMapping("/api/v1/judgments")
class JudgmentListController {

    record Row(UUID id, UUID caseId, String caseReferenceNo, String caseTitle, UUID proceedingId, String stage,
            String courtName, String courtCaseNumber, Integer courtCaseYear, LocalDate judgmentDate, String outcome,
            boolean finalJudgment, UUID appealDeadlineId, LocalDate appealDueOn) {
    }

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;

    JudgmentListController(JdbcClient jdbc, CaseAccessPolicy access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.JUDGMENT_VIEW)
    @Transactional(readOnly = true)
    PageResponse<Row> list(@RequestParam(required = false) String outcome, @RequestParam(required = false) String stage,
            @RequestParam(name = "final", required = false) Boolean finalOnly, Pageable page) {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("p.case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        String where = " where " + visible.sql();
        if (outcome != null && !outcome.isBlank()) {
            where += " and j.outcome = :outcome";
            params.put("outcome", outcome.strip());
        }
        if (stage != null && !stage.isBlank()) {
            where += " and p.stage = :stage";
            params.put("stage", stage.strip());
        }
        if (finalOnly != null) {
            where += " and j.final = :final";
            params.put("final", finalOnly);
        }
        String from = " from judgment j join proceeding p on p.id = j.proceeding_id join legal_case c on c.id = p.case_id"
                + " join court k on k.id = p.court_id left join deadline d on d.id = j.appeal_deadline_id";
        long total = jdbc.sql("select count(*)" + from + where).params(params).query(Long.class).single();
        Map<String, Object> paged = new HashMap<>(params);
        paged.put("limit", page.getPageSize());
        paged.put("offset", page.getOffset());
        List<Row> rows = jdbc.sql("""
                select j.id, p.case_id, c.reference_no, c.title, p.id as proceeding_id, p.stage, k.name_ar,
                       p.court_case_number, p.court_case_year, j.judgment_date, j.outcome, j.final, d.id as deadline_id,
                       d.due_on""" + from + where + " order by j.judgment_date desc, j.id limit :limit offset :offset")
                .params(paged).query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("reference_no"), rs.getString("title"), rs.getObject("proceeding_id", UUID.class),
                        rs.getString("stage"), rs.getString("name_ar"), rs.getString("court_case_number"),
                        rs.getObject("court_case_year") == null ? null : rs.getInt("court_case_year"),
                        rs.getObject("judgment_date", LocalDate.class), rs.getString("outcome"), rs.getBoolean("final"),
                        rs.getObject("deadline_id", UUID.class), rs.getObject("due_on", LocalDate.class))).list();
        return PageResponse.from(new PageImpl<>(rows, page, total));
    }
}
