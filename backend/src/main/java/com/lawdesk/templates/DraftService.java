package com.lawdesk.templates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;
import com.lawdesk.templates.TemplateRequests.Draft;
import com.lawdesk.templates.TemplateRequests.FilledField;

/**
 * A draft document from a template and a case (LD-057, requirements section 29, LD-DOC-29-010). The case file fills the
 * placeholders; a placeholder the file cannot fill stays in the text and is listed as missing, and nothing is invented. The
 * result is a draft, never final: the competent member reviews and approves it. Generating changes nothing in the case file.
 */
@Service
class DraftService {

    static final String INACTIVE = "TEMPLATE_INACTIVE";
    static final String NO_APPROVED_VERSION = "TEMPLATE_NO_APPROVED_VERSION";
    static final String NOT_APPROVED = "TEMPLATE_NOT_APPROVED";

    /** Fixed text shown with every draft: it is a draft, to be reviewed and approved by the competent member. */
    static final String NOTICE = "مسودة غير نهائية: تُراجَع وتُعتمَد من العضو المختص قبل أي استعمال، والحقول الناقصة تُستكمل يدويًا.";

    private record Chosen(UUID id, int no, String status, String body) {
    }

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final TemplateFields fields;

    DraftService(JdbcClient jdbc, CaseAccessPolicy access, CurrentActor currentActor, AuditLog audit, TemplateFields fields) {
        this.jdbc = jdbc;
        this.access = access;
        this.currentActor = currentActor;
        this.audit = audit;
        this.fields = fields;
    }

    @Transactional
    Draft generate(UUID caseId, TemplateRequests.DraftRequest request) {
        ActorRef actor = currentActor.require();
        access.requireView(caseId);
        record Head(String title, String kind, boolean active) {
        }
        Head head = jdbc.sql("select title, kind, active from form_template where id = :id").param("id", request.templateId())
                .query((rs, n) -> new Head(rs.getString("title"), rs.getString("kind"), rs.getBoolean("active")))
                .optional().orElseThrow(() -> new NotFoundException("template"));
        if (!head.active()) {
            throw new BusinessRuleException(INACTIVE, "the template is not in use");
        }
        boolean manager = currentActor.has(Permission.TEMPLATE_MANAGE);
        Chosen version = choose(request, manager);
        if (!"APPROVED".equals(version.status()) && !manager) {
            throw new BusinessRuleException(NOT_APPROVED, "only an approved version of a template is used for a draft");
        }
        Map<String, String> values = fields.valuesOf(caseId);
        List<FilledField> filled = new ArrayList<>();
        List<FilledField> missing = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        Matcher matcher = TemplateFields.TOKEN.matcher(version.body());
        int last = 0;
        while (matcher.find()) {
            text.append(version.body(), last, matcher.start());
            last = matcher.end();
            String key = matcher.group(1);
            TemplateFields.Field field = TemplateFields.field(key);
            if (field == null) {
                text.append(matcher.group());
                if (!unknown.contains(key)) {
                    unknown.add(key);
                }
                continue;
            }
            FilledField entry = new FilledField(key, field.labelAr(), field.labelEn());
            String value = values.get(key);
            if (value == null) {
                text.append(matcher.group());
                if (missing.stream().noneMatch(m -> m.key().equals(key))) {
                    missing.add(entry);
                }
            } else {
                text.append(value);
                if (filled.stream().noneMatch(m -> m.key().equals(key))) {
                    filled.add(entry);
                }
            }
        }
        text.append(version.body(), last, version.body().length());
        audit.recordWithChange("TEMPLATE_DRAFT", actor.id(), actor.username(), "CASE", caseId.toString(),
                head.kind() + " v" + version.no());
        return new Draft(request.templateId(), head.title(), head.kind(), version.id(), version.no(), version.status(), caseId,
                true, false, text.toString(), filled, missing, unknown, NOTICE);
    }

    /** The requested version; otherwise the latest approved one, or — for a manager with none approved — the latest. */
    private Chosen choose(TemplateRequests.DraftRequest request, boolean manager) {
        String select = "select id, version_no, status, body from form_template_version where template_id = :t";
        if (request.versionId() != null) {
            return jdbc.sql(select + " and id = :v").param("t", request.templateId()).param("v", request.versionId())
                    .query((rs, n) -> new Chosen(rs.getObject("id", UUID.class), rs.getInt("version_no"),
                            rs.getString("status"), rs.getString("body")))
                    .optional().orElseThrow(() -> new NotFoundException("template version"));
        }
        List<Chosen> approved = jdbc.sql(select + " and status = 'APPROVED' order by version_no desc limit 1")
                .param("t", request.templateId()).query((rs, n) -> new Chosen(rs.getObject("id", UUID.class),
                        rs.getInt("version_no"), rs.getString("status"), rs.getString("body")))
                .list();
        if (!approved.isEmpty()) {
            return approved.get(0);
        }
        if (manager) {
            List<Chosen> any = jdbc.sql(select + " order by version_no desc limit 1").param("t", request.templateId())
                    .query((rs, n) -> new Chosen(rs.getObject("id", UUID.class), rs.getInt("version_no"),
                            rs.getString("status"), rs.getString("body")))
                    .list();
            if (!any.isEmpty()) {
                return any.get(0);
            }
        }
        throw new BusinessRuleException(NO_APPROVED_VERSION, "the template has no approved version");
    }
}
