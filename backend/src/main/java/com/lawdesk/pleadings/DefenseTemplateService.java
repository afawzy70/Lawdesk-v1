package com.lawdesk.pleadings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/** The defence library (requirements section 17.4): reusable wording with its review state; never copied by the system. */
@Service
class DefenseTemplateService {

    private static final String SELECT = """
            select id, title, kind, text, legal_basis, status, reviewed_by, reviewed_at, version, updated_at
            from defense_template""";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;

    DefenseTemplateService(JdbcClient jdbc, CurrentActor currentActor) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    List<DefenseRequests.TemplateView> search(String q) {
        String text = q == null || q.isBlank() ? null : "%" + q.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        return jdbc.sql(SELECT + " where (cast(:q as text) is null or lower(title) like :q escape '\\'"
                + " or lower(coalesce(text, '')) like :q escape '\\'"
                + " or lower(coalesce(legal_basis, '')) like :q escape '\\') order by title, id limit 200")
                .param("q", text).query(DefenseTemplateService::view).list();
    }

    @Transactional
    DefenseRequests.TemplateView create(DefenseRequests.TemplateBody body) {
        ActorRef actor = currentActor.require();
        UUID id = jdbc.sql("""
                insert into defense_template (title, kind, text, legal_basis, created_by, updated_by)
                values (:title, :kind, :text, :basis, :actor, :actor) returning id""").param("title", body.title())
                .param("kind", body.kind().name()).param("text", body.text()).param("basis", body.legalBasis())
                .param("actor", actor.id()).query(UUID.class).single();
        return find(id);
    }

    @Transactional
    DefenseRequests.TemplateView update(UUID id, DefenseRequests.TemplateBody body) {
        ActorRef actor = currentActor.require();
        DefenseRequests.TemplateView current = find(id);
        if (body.version() == null || current.version() != body.version()) {
            throw new ConflictException("template " + id + " has changed since version " + body.version());
        }
        TemplateStatus status = body.status() == null ? current.status() : body.status();
        boolean reviewed = status != TemplateStatus.DRAFT && status != current.status();
        int updated = jdbc.sql("""
                update defense_template set title = :title, kind = :kind, text = :text, legal_basis = :basis,
                       status = :status, reviewed_by = case when :reviewed then cast(:actor as uuid) else reviewed_by end,
                       reviewed_at = case when :reviewed then now() else reviewed_at end, version = version + 1,
                       updated_at = now(), updated_by = :actor where id = :id and version = :version""")
                .param("title", body.title()).param("kind", body.kind().name()).param("text", body.text())
                .param("basis", body.legalBasis()).param("status", status.name()).param("reviewed", reviewed)
                .param("actor", actor.id()).param("id", id).param("version", body.version()).update();
        if (updated == 0) {
            throw new ConflictException("template " + id + " was changed by someone else");
        }
        return find(id);
    }

    private DefenseRequests.TemplateView find(UUID id) {
        return jdbc.sql(SELECT + " where id = :id").param("id", id).query(DefenseTemplateService::view).optional()
                .orElseThrow(() -> new NotFoundException("template"));
    }

    private static DefenseRequests.TemplateView view(ResultSet rs, int n) throws SQLException {
        java.sql.Timestamp reviewed = rs.getTimestamp("reviewed_at");
        return new DefenseRequests.TemplateView(rs.getObject("id", UUID.class), rs.getString("title"),
                DefenseKind.valueOf(rs.getString("kind")), rs.getString("text"), rs.getString("legal_basis"),
                TemplateStatus.valueOf(rs.getString("status")), rs.getObject("reviewed_by", UUID.class),
                reviewed == null ? null : reviewed.toInstant(), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());
    }
}
