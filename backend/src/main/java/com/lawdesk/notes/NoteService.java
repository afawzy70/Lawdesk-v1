package com.lawdesk.notes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.users.UserDirectory;
import com.lawdesk.users.UserDirectory.UserSummary;

/**
 * Legal notes of a case (LD-029, docs/delivery/PHASE-5-ACCEPTANCE.md): written with NOTE_MANAGE, read with CASE_VIEW,
 * changed or removed by their author only (ASSUMPTION). The timeline records that a note was added or removed and by
 * whom, never its text.
 */
@Service
class NoteService {

    static final String NOTE_AUTHOR_ONLY = "NOTE_AUTHOR_ONLY";
    static final String CASE_ARCHIVED = "CASE_ARCHIVED";

    record Write(@NotBlank @Size(max = 4000) String body) {

        Write {
            body = body == null ? null : body.strip();
        }
    }

    record Edit(@NotBlank @Size(max = 4000) String body, @NotNull Long version) {

        Edit {
            body = body == null ? null : body.strip();
        }
    }

    /** {@code editable}: the signed-in user wrote it; {@code edited}: it changed after it was written. */
    record Note(UUID id, UUID caseId, String body, UUID authorId, String authorName, boolean editable, boolean edited,
            long version, Instant createdAt, Instant updatedAt) {
    }

    private record Row(UUID id, UUID caseId, String body, UUID createdBy, long version, Instant createdAt,
            Instant updatedAt) {
    }

    private static final String COLUMNS = "id, case_id, body, created_by, version, created_at, updated_at";

    private final NamedParameterJdbcTemplate jdbc;
    private final CaseLookup cases;
    private final UserDirectory users;
    private final CurrentActor currentActor;

    NoteService(NamedParameterJdbcTemplate jdbc, CaseLookup cases, UserDirectory users, CurrentActor currentActor) {
        this.jdbc = jdbc;
        this.cases = cases;
        this.users = users;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    Page<Note> forCase(UUID caseId, Pageable pageable) {
        if (cases.find(caseId).isEmpty()) {
            throw new NotFoundException("case");
        }
        MapSqlParameterSource params = new MapSqlParameterSource("caseId", caseId)
                .addValue("offset", pageable.getOffset()).addValue("limit", pageable.getPageSize());
        List<Row> rows = jdbc.query("select " + COLUMNS + " from case_note where case_id = :caseId"
                + " order by created_at desc, id offset :offset limit :limit", params, (rs, n) -> row(rs));
        Long total = jdbc.queryForObject("select count(*) from case_note where case_id = :caseId", params, Long.class);
        UUID me = currentActor.require().id();
        Map<UUID, String> names = new HashMap<>();
        List<Note> notes = rows.stream().map(r -> respond(r, me, names)).toList();
        return new PageImpl<>(notes, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()),
                total == null ? 0 : total);
    }

    @Transactional
    Note add(UUID caseId, Write request) {
        ActorRef actor = currentActor.require();
        writableCase(caseId);
        Row stored = jdbc.queryForObject("""
                insert into case_note (case_id, body, created_by, updated_by) values (:caseId, :body, :actor, :actor)
                returning""" + " " + COLUMNS, new MapSqlParameterSource("caseId", caseId)
                .addValue("body", request.body()).addValue("actor", actor.id()), (rs, n) -> row(rs));
        Note note = respond(stored, actor.id(), new HashMap<>());
        cases.recordEvent(caseId, "NOTE_ADDED", "Legal note added by " + note.authorName());
        return note;
    }

    @Transactional
    Note edit(UUID id, Edit request) {
        ActorRef actor = currentActor.require();
        Row row = lockedOwn(id, request.version(), actor);
        Row stored = jdbc.queryForObject("""
                update case_note set body = :body, version = version + 1, updated_at = now(), updated_by = :actor
                where id = :id returning""" + " " + COLUMNS, new MapSqlParameterSource("id", row.id())
                .addValue("body", request.body()).addValue("actor", actor.id()), (rs, n) -> row(rs));
        return respond(stored, actor.id(), new HashMap<>());
    }

    @Transactional
    void remove(UUID id, Long version) {
        ActorRef actor = currentActor.require();
        if (version == null) {
            throw new FieldErrorException("version", "NotNull");
        }
        Row row = lockedOwn(id, version, actor);
        jdbc.update("delete from case_note where id = :id", new MapSqlParameterSource("id", row.id()));
        cases.recordEvent(row.caseId(), "NOTE_REMOVED", "Legal note removed by " + nameOf(actor.id(), new HashMap<>()));
    }

    /** The note, row-locked; its version must match, its case be writable and the signed-in user its author. */
    private Row lockedOwn(UUID id, long version, ActorRef actor) {
        Row row = jdbc.query("select " + COLUMNS + " from case_note where id = :id for update",
                new MapSqlParameterSource("id", id), (rs, n) -> row(rs)).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("note"));
        if (row.version() != version) {
            throw new ConflictException("note was changed by someone else");
        }
        writableCase(row.caseId());
        if (!row.createdBy().equals(actor.id())) {
            throw new BusinessRuleException(NOTE_AUTHOR_ONLY, "only the author changes a note");
        }
        return row;
    }

    private CaseSummary writableCase(UUID caseId) {
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(CASE_ARCHIVED, "an archived case is read-only");
        }
        return legalCase;
    }

    private Note respond(Row r, UUID me, Map<UUID, String> names) {
        return new Note(r.id(), r.caseId(), r.body(), r.createdBy(), nameOf(r.createdBy(), names),
                r.createdBy().equals(me), !r.updatedAt().equals(r.createdAt()), r.version(), r.createdAt(),
                r.updatedAt());
    }

    /** The author's display name; a user no longer in the directory is named by id. */
    private String nameOf(UUID userId, Map<UUID, String> names) {
        return names.computeIfAbsent(userId, key -> {
            Optional<UserSummary> user = users.find(key);
            return user.map(UserSummary::displayName).orElse(key.toString());
        });
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class), rs.getString("body"),
                rs.getObject("created_by", UUID.class), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
