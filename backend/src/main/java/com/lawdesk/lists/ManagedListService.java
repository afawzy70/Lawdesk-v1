package com.lawdesk.lists;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.lists.ManagedListRequests.Value;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/** Reading and managing the lists of settlement kinds, directive kinds and template kinds. Nothing is ever deleted. */
@Service
class ManagedListService {

    private static final String COLUMNS = "id, list_code, code, label_ar, label_en, origin, active, sort_order, version, updated_at";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    ManagedListService(JdbcClient jdbc, CurrentActor currentActor, AuditLog audit) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    private static Value value(ResultSet rs, int row) throws SQLException {
        return new Value(rs.getObject("id", UUID.class), rs.getString("list_code"), rs.getString("code"),
                rs.getString("label_ar"), rs.getString("label_en"), rs.getString("origin"), rs.getBoolean("active"),
                rs.getInt("sort_order"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());
    }

    /** The list must exist (400 otherwise); a caller without its permission is refused (403). */
    private void requireManager(String list) {
        var permission = ManagedLists.managedBy(list).orElseThrow(() -> new FieldErrorException("list", "Pattern"));
        if (!currentActor.has(permission)) {
            throw new AccessDeniedException("missing " + permission);
        }
    }

    @Transactional(readOnly = true)
    List<Value> list(String list, boolean includeInactive) {
        ManagedLists.managedBy(list).orElseThrow(() -> new FieldErrorException("list", "Pattern"));
        return jdbc.sql("select " + COLUMNS + " from managed_list_value where list_code = :list"
                        + (includeInactive ? "" : " and active") + " order by sort_order, code")
                .param("list", list).query(ManagedListService::value).list();
    }

    @Transactional
    Value create(String list, ManagedListRequests.Create request) {
        requireManager(list);
        ActorRef actor = currentActor.require();
        try {
            UUID id = jdbc.sql("""
                            insert into managed_list_value (list_code, code, label_ar, label_en, origin, sort_order, created_by,
                                                            updated_by)
                            values (:list, :code, :labelAr, :labelEn, 'PROPOSED',
                                    coalesce(:sortOrder, (select coalesce(max(sort_order), 0) + 1 from managed_list_value
                                                          where list_code = :list)), :actor, :actor)
                            returning id""")
                    .param("list", list).param("code", request.code()).param("labelAr", request.labelAr())
                    .param("labelEn", request.labelEn()).param("sortOrder", request.sortOrder())
                    .param("actor", actor.id()).query(UUID.class).single();
            audit.recordWithChange("LIST_VALUE_CREATE", actor.id(), actor.username(), "LIST_VALUE", id.toString(), list);
            return find(id);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("this code or label is already in the list");
        }
    }

    @Transactional
    Value update(String list, UUID id, ManagedListRequests.Update request) {
        requireManager(list);
        ActorRef actor = currentActor.require();
        Value current = jdbc.sql("select " + COLUMNS + " from managed_list_value where id = :id and list_code = :list")
                .param("id", id).param("list", list).query(ManagedListService::value).optional()
                .orElseThrow(() -> new NotFoundException("list value"));
        try {
            int changed = jdbc.sql("""
                            update managed_list_value set label_ar = :labelAr, label_en = :labelEn, active = :active,
                                sort_order = coalesce(:sortOrder, sort_order), version = version + 1, updated_at = now(),
                                updated_by = :actor
                            where id = :id and version = :version""")
                    .param("labelAr", request.labelAr()).param("labelEn", request.labelEn())
                    .param("active", request.active()).param("sortOrder", request.sortOrder()).param("actor", actor.id())
                    .param("id", id).param("version", request.version()).update();
            if (changed == 0) {
                throw new ConflictException("the value was changed by someone else");
            }
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("this label is already in the list");
        }
        audit.recordWithChange("LIST_VALUE_UPDATE", actor.id(), actor.username(), "LIST_VALUE", id.toString(),
                current.code() + (request.active() ? "" : " off"));
        return find(id);
    }

    private Value find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from managed_list_value where id = :id").param("id", id)
                .query(ManagedListService::value).single();
    }
}
