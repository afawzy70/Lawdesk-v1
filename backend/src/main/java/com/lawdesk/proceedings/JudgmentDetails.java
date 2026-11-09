package com.lawdesk.proceedings;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.lawdesk.api.FieldErrorException;

/**
 * The extended judgment fields of LD-051 (requirements section 9): checks the values that point at other records (the
 * managed lists and the case's own documents) and turns a judgment into its answer with the list labels filled in.
 */
@Component
class JudgmentDetails {

    static final String TYPE_LIST = "JUDGMENT_TYPE";
    static final String NATURE_LIST = "JUDGMENT_NATURE";

    private record Label(String list, String ar, String en, boolean active) {
    }

    private final JdbcClient jdbc;

    JudgmentDetails(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Refuses a list value of another list or a deactivated one (unless the judgment already carries it) and a document of another case. */
    void check(JudgmentRequests.Details d, UUID caseId, Judgment current) {
        checkValue(d.judgmentTypeId(), TYPE_LIST, "details.judgmentTypeId", current == null ? null : current.getJudgmentTypeId());
        checkValue(d.natureId(), NATURE_LIST, "details.natureId", current == null ? null : current.getNatureId());
        if (d.copyDocumentId() != null && !jdbc.sql("select exists (select 1 from case_document where id = :id and case_id = :c)")
                .param("id", d.copyDocumentId()).param("c", caseId).query(Boolean.class).single()) {
            throw new FieldErrorException("details.copyDocumentId", "Exists");
        }
    }

    private void checkValue(UUID id, String list, String field, UUID kept) {
        if (id == null) {
            return;
        }
        Label label = labels(java.util.List.of(id)).get(id);
        if (label == null || !label.list().equals(list) || (!label.active() && !Objects.equals(id, kept))) {
            throw new FieldErrorException(field, "Exists");
        }
    }

    JudgmentRequests.Response response(Judgment j, UUID caseId) {
        return JudgmentRequests.Response.of(j, caseId, view(j, labels(idsOf(j))), appealDeadlines(java.util.List.of(j))
                .get(j.getId()));
    }

    /** Several judgments of one case with one label query. */
    java.util.List<JudgmentRequests.Response> responses(Collection<Judgment> judgments, UUID caseId) {
        Map<UUID, Label> labels = labels(judgments.stream().flatMap(j -> idsOf(j).stream()).toList());
        Map<UUID, JudgmentRequests.AppealDeadlineView> deadlines = appealDeadlines(judgments);
        return judgments.stream().map(j -> JudgmentRequests.Response.of(j, caseId, view(j, labels), deadlines.get(j.getId())))
                .toList();
    }

    /** The appeal deadline each judgment names (judgment id to the deadline), read in one query. */
    private Map<UUID, JudgmentRequests.AppealDeadlineView> appealDeadlines(Collection<Judgment> judgments) {
        Map<UUID, JudgmentRequests.AppealDeadlineView> found = new HashMap<>();
        java.util.List<UUID> ids = judgments.stream().map(Judgment::getAppealDeadlineId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return found;
        }
        Map<UUID, JudgmentRequests.AppealDeadlineView> byDeadline = new HashMap<>();
        jdbc.sql("select id, due_on, status, calc_status, action from deadline where id in (:ids)").param("ids", ids)
                .query(rs -> {
                    byDeadline.put(rs.getObject("id", UUID.class), new JudgmentRequests.AppealDeadlineView(
                            rs.getObject("id", UUID.class), rs.getObject("due_on", java.time.LocalDate.class),
                            rs.getString("status"), rs.getString("calc_status"), rs.getString("action")));
                });
        for (Judgment j : judgments) {
            if (j.getAppealDeadlineId() != null) {
                found.put(j.getId(), byDeadline.get(j.getAppealDeadlineId()));
            }
        }
        return found;
    }

    /** Refuses a deadline that does not run from this judgment (or is not in its case). */
    void checkAppealDeadline(UUID deadlineId, Judgment judgment) {
        if (deadlineId != null && !jdbc.sql("""
                select exists (select 1 from deadline where id = :d and source_type = 'JUDGMENT' and source_id = :j)""")
                .param("d", deadlineId).param("j", judgment.getId()).query(Boolean.class).single()) {
            throw new FieldErrorException("deadlineId", "NotFromJudgment");
        }
    }

    private static java.util.List<UUID> idsOf(Judgment j) {
        return java.util.stream.Stream.of(j.getJudgmentTypeId(), j.getNatureId()).filter(Objects::nonNull).toList();
    }

    private static JudgmentRequests.DetailsView view(Judgment j, Map<UUID, Label> labels) {
        Label type = j.getJudgmentTypeId() == null ? null : labels.get(j.getJudgmentTypeId());
        Label nature = j.getNatureId() == null ? null : labels.get(j.getNatureId());
        return new JudgmentRequests.DetailsView(j.getChamber(), j.getJudgmentTypeId(), type == null ? null : type.ar(),
                type == null ? null : type.en(), j.getNatureId(), nature == null ? null : nature.ar(),
                nature == null ? null : nature.en(), j.getOperativeText(), j.getReasons(), j.getCostsAmount(),
                j.getLawyerFeesAmount(), j.getAppealable(), j.getNotifiedOn(),
                j.getJudgmentStatus() == null ? null : JudgmentStatus.valueOf(j.getJudgmentStatus()),
                j.getCopyDocumentId());
    }

    private Map<UUID, Label> labels(Collection<UUID> ids) {
        Map<UUID, Label> found = new HashMap<>();
        if (ids.isEmpty()) {
            return found;
        }
        jdbc.sql("select id, list_code, label_ar, label_en, active from lookup_value where id in (:ids)")
                .param("ids", ids).query(rs -> {
                    found.put(rs.getObject("id", UUID.class), new Label(rs.getString("list_code"),
                            rs.getString("label_ar"), rs.getString("label_en"), rs.getBoolean("active")));
                });
        return found;
    }
}
