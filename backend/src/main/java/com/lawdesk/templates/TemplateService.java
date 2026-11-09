package com.lawdesk.templates;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.lists.ManagedLists;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;
import com.lawdesk.templates.TemplateRequests.Placeholder;
import com.lawdesk.templates.TemplateRequests.Summary;
import com.lawdesk.templates.TemplateRequests.Template;
import com.lawdesk.templates.TemplateRequests.Version;

/**
 * The library of ready legal templates (LD-057, requirements section 29, owner decision D10). A template has versions that
 * are all kept; a version is DRAFT, then REVIEWED, then APPROVED, and once reviewed its text is never edited (a change is a
 * new version). Managers see every version and its text; everyone else sees only the approved versions, without the text.
 */
@Service
class TemplateService {

    static final String KIND_UNKNOWN = "TEMPLATE_KIND_UNKNOWN";
    static final String VERSION_LOCKED = "TEMPLATE_VERSION_LOCKED";
    static final String VERSION_TRANSITION = "TEMPLATE_VERSION_TRANSITION";
    static final String UNKNOWN_PLACEHOLDER = "TEMPLATE_UNKNOWN_PLACEHOLDER";

    private static final String VERSION_COLUMNS = "id, template_id, version_no, status, body, change_note, reviewed_by,"
            + " reviewed_at, approved_by, approved_at, version, created_at, created_by";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final ManagedLists lists;

    TemplateService(JdbcClient jdbc, CurrentActor currentActor, AuditLog audit, ManagedLists lists) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.audit = audit;
        this.lists = lists;
    }

    private boolean manager() {
        return currentActor.has(Permission.TEMPLATE_MANAGE);
    }

    // ---- reads ----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<Summary> list(String kind, boolean includeInactive) {
        boolean inactiveToo = includeInactive && manager();
        return jdbc.sql("""
                        select t.id, t.kind, t.title, t.description, t.origin, t.active, t.version, t.updated_at,
                               (select count(*) from form_template_version v where v.template_id = t.id
                                   and (:manager or v.status = 'APPROVED')) as version_count,
                               (select max(v.version_no) from form_template_version v where v.template_id = t.id
                                   and v.status = 'APPROVED') as approved_no
                        from form_template t where (:inactive or t.active) and (cast(:kind as text) is null or t.kind = :kind)
                        order by t.kind, lower(t.title), t.id""")
                .param("manager", manager()).param("inactive", inactiveToo).param("kind", kind)
                .query((rs, n) -> new Summary(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("title"),
                        rs.getString("description"), rs.getString("origin"), rs.getBoolean("active"),
                        rs.getInt("version_count"), approvedNo(rs), rs.getLong("version"),
                        rs.getTimestamp("updated_at").toInstant()))
                .list();
    }

    @Transactional(readOnly = true)
    Template get(UUID id) {
        return load(id);
    }

    private static Integer approvedNo(ResultSet rs) throws SQLException {
        return rs.getObject("approved_no") == null ? null : rs.getInt("approved_no");
    }

    private Template load(UUID id) {
        return jdbc.sql("""
                        select t.id, t.kind, t.title, t.description, t.origin, t.active, t.version, t.created_at, t.updated_at,
                               (select max(v.version_no) from form_template_version v where v.template_id = t.id
                                   and v.status = 'APPROVED') as approved_no
                        from form_template t where t.id = :id""")
                .param("id", id).query((rs, n) -> new Template(id, rs.getString("kind"), rs.getString("title"),
                        rs.getString("description"), rs.getString("origin"), rs.getBoolean("active"), approvedNo(rs),
                        versionsOf(id), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()))
                .optional().orElseThrow(() -> new NotFoundException("template"));
    }

    private List<Version> versionsOf(UUID templateId) {
        boolean manager = manager();
        return jdbc.sql("select " + VERSION_COLUMNS + " from form_template_version where template_id = :id"
                        + (manager ? "" : " and status = 'APPROVED'") + " order by version_no desc")
                .param("id", templateId).query((rs, n) -> version(rs, manager)).list();
    }

    private Version loadVersion(UUID id) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from form_template_version where id = :id").param("id", id)
                .query((rs, n) -> version(rs, true)).optional().orElseThrow(() -> new NotFoundException("template version"));
    }

    private static Version version(ResultSet rs, boolean withBody) throws SQLException {
        String body = rs.getString("body");
        List<Placeholder> placeholders = new ArrayList<>();
        for (String key : TemplateFields.keysIn(body)) {
            TemplateFields.Field field = TemplateFields.field(key);
            placeholders.add(new Placeholder(key, field != null, field == null ? null : field.labelAr(),
                    field == null ? null : field.labelEn()));
        }
        return new Version(rs.getObject("id", UUID.class), rs.getObject("template_id", UUID.class), rs.getInt("version_no"),
                rs.getString("status"), withBody ? body : null, rs.getString("change_note"), placeholders,
                rs.getObject("reviewed_by", UUID.class),
                rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
                rs.getObject("approved_by", UUID.class),
                rs.getTimestamp("approved_at") == null ? null : rs.getTimestamp("approved_at").toInstant(),
                rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class));
    }

    // ---- writes ---------------------------------------------------------------------------------------------------

    @Transactional
    Template create(TemplateRequests.Create request) {
        ActorRef actor = currentActor.require();
        lists.requireActive(ManagedLists.TEMPLATE_KIND, request.kind(), KIND_UNKNOWN);
        UUID id = jdbc.sql("""
                        insert into form_template (kind, title, description, created_by, updated_by)
                        values (:kind, :title, :description, :actor, :actor) returning id""")
                .param("kind", request.kind()).param("title", request.title()).param("description", request.description())
                .param("actor", actor.id()).query(UUID.class).single();
        if (request.body() != null) {
            insertVersion(id, request.body(), null, actor);
        }
        audit.recordWithChange("TEMPLATE_CREATE", actor.id(), actor.username(), "TEMPLATE", id.toString(), request.kind());
        return load(id);
    }

    @Transactional
    Template update(UUID id, TemplateRequests.Update request) {
        ActorRef actor = currentActor.require();
        Template current = load(id);
        if (!current.kind().equals(request.kind())) {
            lists.requireActive(ManagedLists.TEMPLATE_KIND, request.kind(), KIND_UNKNOWN);
        }
        int updated = jdbc.sql("""
                        update form_template set kind = :kind, title = :title, description = :description, active = :active,
                            version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("kind", request.kind()).param("title", request.title()).param("description", request.description())
                .param("active", request.active()).param("actor", actor.id()).param("id", id)
                .param("version", request.version()).update();
        if (updated != 1) {
            throw new ConflictException("the template was changed by someone else");
        }
        audit.recordWithChange("TEMPLATE_UPDATE", actor.id(), actor.username(), "TEMPLATE", id.toString(),
                request.active() ? "edit" : "deactivate");
        return load(id);
    }

    @Transactional
    Version addVersion(UUID templateId, TemplateRequests.NewVersion request) {
        ActorRef actor = currentActor.require();
        // Serialises the numbering of versions of one template.
        jdbc.sql("select id from form_template where id = :id for update").param("id", templateId).query(UUID.class)
                .optional().orElseThrow(() -> new NotFoundException("template"));
        UUID id = insertVersion(templateId, request.body(), request.changeNote(), actor);
        audit.recordWithChange("TEMPLATE_VERSION", actor.id(), actor.username(), "TEMPLATE", templateId.toString(), "new");
        return loadVersion(id);
    }

    private UUID insertVersion(UUID templateId, String body, String changeNote, ActorRef actor) {
        return jdbc.sql("""
                        insert into form_template_version (template_id, version_no, body, change_note, created_by, updated_by)
                        values (:t, (select coalesce(max(version_no), 0) + 1 from form_template_version where template_id = :t),
                                :body, :note, :actor, :actor) returning id""")
                .param("t", templateId).param("body", body).param("note", changeNote).param("actor", actor.id())
                .query(UUID.class).single();
    }

    @Transactional
    Version editVersion(UUID id, TemplateRequests.EditVersion request) {
        ActorRef actor = currentActor.require();
        Version current = loadVersion(id);
        if (!"DRAFT".equals(current.status())) {
            throw new BusinessRuleException(VERSION_LOCKED, "a reviewed or approved version is never edited: add a new version");
        }
        int updated = jdbc.sql("""
                        update form_template_version set body = :body, change_note = :note, version = version + 1,
                            updated_at = now(), updated_by = :actor where id = :id and version = :version""")
                .param("body", request.body()).param("note", request.changeNote()).param("actor", actor.id())
                .param("id", id).param("version", request.version()).update();
        if (updated != 1) {
            throw new ConflictException("the version was changed by someone else");
        }
        audit.recordWithChange("TEMPLATE_VERSION", actor.id(), actor.username(), "TEMPLATE", current.templateId().toString(),
                "edit v" + current.versionNo());
        return loadVersion(id);
    }

    @Transactional
    Version review(UUID id, long version) {
        ActorRef actor = currentActor.require();
        Version current = loadVersion(id);
        if (!"DRAFT".equals(current.status())) {
            throw new BusinessRuleException(VERSION_TRANSITION, "only a draft version is reviewed");
        }
        step(id, version, "REVIEWED", "reviewed_by = :actor, reviewed_at = now(),", actor);
        audit.recordWithChange("TEMPLATE_VERSION", actor.id(), actor.username(), "TEMPLATE", current.templateId().toString(),
                "review v" + current.versionNo());
        return loadVersion(id);
    }

    @Transactional
    Version approve(UUID id, long version) {
        ActorRef actor = currentActor.require();
        Version current = loadVersion(id);
        if (!"REVIEWED".equals(current.status())) {
            throw new BusinessRuleException(VERSION_TRANSITION, "only a reviewed version is approved");
        }
        if (current.placeholders().stream().anyMatch(p -> !p.known())) {
            throw new BusinessRuleException(UNKNOWN_PLACEHOLDER, "the text uses a field the case file does not have");
        }
        step(id, version, "APPROVED", "approved_by = :actor, approved_at = now(),", actor);
        audit.recordWithChange("TEMPLATE_VERSION", actor.id(), actor.username(), "TEMPLATE", current.templateId().toString(),
                "approve v" + current.versionNo());
        return loadVersion(id);
    }

    private void step(UUID id, long version, String to, String stamp, ActorRef actor) {
        int updated = jdbc.sql("update form_template_version set status = :to, " + stamp
                        + " version = version + 1, updated_at = now(), updated_by = :actor where id = :id and version = :version")
                .param("to", to).param("actor", actor.id()).param("id", id).param("version", version).update();
        if (updated != 1) {
            throw new ConflictException("the version was changed by someone else");
        }
    }
}
