package com.lawdesk.library;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.library.LibraryViews.Classification;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * The managed classification lists of the library (owner decision D10): values the requirements text lists are
 * {@code DOCUMENT}, values members add are {@code PROPOSED}. Values are edited and deactivated, never deleted, so a
 * judgment keeps the value it was classified with; every change is audited with the previous label.
 */
@Service
class ClassificationService {

    static final String COLUMNS = "id, dimension, code, label_ar, label_en, origin, active, sort_order, version, updated_at";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    ClassificationService(JdbcClient jdbc, CurrentActor currentActor, AuditLog audit) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    static Classification row(ResultSet rs, int n) throws SQLException {
        return new Classification(rs.getObject("id", UUID.class), rs.getString("dimension"), rs.getString("code"),
                rs.getString("label_ar"), rs.getString("label_en"), rs.getString("origin"), rs.getBoolean("active"),
                rs.getInt("sort_order"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());
    }

    @Transactional(readOnly = true)
    List<Classification> list(String dimension, boolean includeInactive) {
        return jdbc.sql("select " + COLUMNS + " from library_classification where (cast(:dimension as varchar) is null"
                        + " or dimension = :dimension) and (:all or active) order by dimension, sort_order,"
                        + " library_normalize(label_ar), id")
                .param("dimension", dimension).param("all", includeInactive).query(ClassificationService::row).list();
    }

    @Transactional
    Classification create(LibraryRequests.ClassificationCreate request) {
        ActorRef actor = currentActor.require();
        try {
            Classification created = jdbc.sql("insert into library_classification (dimension, code, label_ar, label_en,"
                            + " origin, sort_order, created_by, updated_by) values (:dimension, :code, :labelAr, :labelEn,"
                            + " 'PROPOSED', :sort, :actor, :actor) returning " + COLUMNS)
                    .param("dimension", request.dimension()).param("code", request.code())
                    .param("labelAr", request.labelAr().strip()).param("labelEn", LibraryText.trimToNull(request.labelEn()))
                    .param("sort", request.sortOrder() == null ? 100 : request.sortOrder()).param("actor", actor.id())
                    .query(ClassificationService::row).single();
            audit.recordWithChange("LIBRARY_CLASSIFICATION_CREATE", actor.id(), actor.username(),
                    "LIBRARY_CLASSIFICATION", created.id().toString(), created.dimension() + "/" + created.code());
            return created;
        } catch (DuplicateKeyException e) {
            throw new ConflictException("this code or label already exists in the list");
        }
    }

    @Transactional
    Classification update(UUID id, LibraryRequests.ClassificationUpdate request) {
        ActorRef actor = currentActor.require();
        Classification current = jdbc.sql("select " + COLUMNS + " from library_classification where id = :id for update")
                .param("id", id).query(ClassificationService::row).optional()
                .orElseThrow(() -> new NotFoundException("classification"));
        if (current.version() != request.version()) {
            throw new ConflictException("classification " + id + " has changed since version " + request.version());
        }
        try {
            Classification updated = jdbc.sql("update library_classification set label_ar = :labelAr, label_en = :labelEn,"
                            + " active = :active, sort_order = :sort, version = version + 1, updated_at = now(),"
                            + " updated_by = :actor where id = :id returning " + COLUMNS)
                    .param("id", id).param("labelAr", request.labelAr().strip())
                    .param("labelEn", LibraryText.trimToNull(request.labelEn())).param("active", request.active())
                    .param("sort", request.sortOrder() == null ? current.sortOrder() : request.sortOrder())
                    .param("actor", actor.id()).query(ClassificationService::row).single();
            audit.recordWithChange("LIBRARY_CLASSIFICATION_UPDATE", actor.id(), actor.username(),
                    "LIBRARY_CLASSIFICATION", id.toString(), LibraryText.shortReason("was [" + current.labelAr() + "]"
                            + (current.active() == updated.active() ? "" : updated.active() ? " reactivated" : " deactivated")));
            return updated;
        } catch (DuplicateKeyException e) {
            throw new ConflictException("an active value with this label already exists in the list");
        }
    }
}
