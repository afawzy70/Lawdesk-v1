package com.lawdesk.library;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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
import com.lawdesk.library.LibraryViews.Relation;
import com.lawdesk.library.LibraryViews.Relations;
import com.lawdesk.library.LibraryViews.Validity;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * What happened to a judgment afterwards (requirements section 16.6): appealed, upheld, modified, overturned, followed by a
 * later judgment, or departed from, each with its source. The library shows these and warns that they exist; it never
 * decides whether a judgment is still good law, and it never calls a line of cases settled.
 */
@Service
class LibraryRelationService {

    static final String RELATED_UNKNOWN = "LIBRARY_RELATED_JUDGMENT_UNKNOWN";
    static final String NOTE = "A summary of what is recorded, with its sources. Whether the judgment still stands is for the"
            + " legal member to decide from the sources.";

    private static final String OUTGOING = "select r.id, 'OUTGOING' as direction, r.relation_type, r.judgment_id,"
            + " r.related_judgment_id as other_id, o.subject as other_subject, o.court_name as other_court,"
            + " o.case_number as other_number, o.case_year as other_year, o.judgment_date as other_date,"
            + " r.related_citation, r.relation_date, r.source_reference, r.notes, r.created_at, r.created_by"
            + " from library_judgment_relation r left join library_judgment o on o.id = r.related_judgment_id"
            + " where r.judgment_id = :id and r.removed_at is null order by r.relation_date nulls last, r.created_at, r.id";
    private static final String INCOMING = "select r.id, 'INCOMING' as direction, r.relation_type, r.judgment_id,"
            + " r.judgment_id as other_id, o.subject as other_subject, o.court_name as other_court,"
            + " o.case_number as other_number, o.case_year as other_year, o.judgment_date as other_date,"
            + " r.related_citation, r.relation_date, r.source_reference, r.notes, r.created_at, r.created_by"
            + " from library_judgment_relation r join library_judgment o on o.id = r.judgment_id"
            + " where r.related_judgment_id = :id and r.removed_at is null order by r.relation_date nulls last,"
            + " r.created_at, r.id";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    LibraryRelationService(JdbcClient jdbc, CurrentActor currentActor, AuditLog audit) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    private static Relation row(ResultSet rs, int n) throws SQLException {
        return new Relation(rs.getObject("id", UUID.class), rs.getString("direction"), rs.getString("relation_type"),
                rs.getObject("judgment_id", UUID.class), rs.getObject("other_id", UUID.class),
                rs.getString("other_subject"), rs.getString("other_court"), rs.getString("other_number"),
                rs.getObject("other_year") == null ? null : rs.getInt("other_year"),
                rs.getObject("other_date", LocalDate.class), rs.getString("related_citation"),
                rs.getObject("relation_date", LocalDate.class), rs.getString("source_reference"), rs.getString("notes"),
                rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class));
    }

    Relations of(UUID judgmentId) {
        return new Relations(jdbc.sql(OUTGOING).param("id", judgmentId).query(LibraryRelationService::row).list(),
                jdbc.sql(INCOMING).param("id", judgmentId).query(LibraryRelationService::row).list());
    }

    /** The recorded standing of a judgment, with a warning for each reason to look at the sources before relying on it. */
    Validity validity(UUID judgmentId, String finality, String status, Relations relations) {
        List<String> types = relations.outgoing().stream().map(Relation::relationType).distinct().sorted().toList();
        List<String> warnings = new ArrayList<>();
        if (!relations.outgoing().isEmpty()) {
            warnings.add("LATER_RELATION_EXISTS");
        }
        if ("NOT_FINAL".equals(finality)) {
            warnings.add("NOT_FINAL");
        } else if ("NOT_SPECIFIED".equals(finality)) {
            warnings.add("FINALITY_NOT_SPECIFIED");
        }
        if ("WITHDRAWN".equals(status)) {
            warnings.add("WITHDRAWN");
        }
        return new Validity(judgmentId, finality, status, relations.outgoing().size(), types, warnings, false, NOTE);
    }

    @Transactional(readOnly = true)
    Validity validity(UUID judgmentId) {
        var row = jdbc.sql("select finality, status from library_judgment where id = :id").param("id", judgmentId)
                .query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)}).optional()
                .orElseThrow(() -> new NotFoundException("judgment"));
        return validity(judgmentId, row[0], row[1], of(judgmentId));
    }

    @Transactional
    Relations add(UUID judgmentId, LibraryRequests.RelationWrite request) {
        ActorRef actor = currentActor.require();
        if (!jdbc.sql("select exists (select 1 from library_judgment where id = :id)").param("id", judgmentId)
                .query(Boolean.class).single()) {
            throw new NotFoundException("judgment");
        }
        String citation = LibraryText.trimToNull(request.relatedCitation());
        if (request.relatedJudgmentId() == null && citation == null) {
            throw new com.lawdesk.api.FieldErrorException("relatedCitation", "NotBlank");
        }
        if (request.relatedJudgmentId() != null) {
            if (request.relatedJudgmentId().equals(judgmentId)) {
                throw new BusinessRuleException(RELATED_UNKNOWN, "a judgment cannot be related to itself");
            }
            if (!jdbc.sql("select exists (select 1 from library_judgment where id = :id)")
                    .param("id", request.relatedJudgmentId()).query(Boolean.class).single()) {
                throw new BusinessRuleException(RELATED_UNKNOWN, "the related judgment is not in the library");
            }
        }
        boolean twice = jdbc.sql("select exists (select 1 from library_judgment_relation where judgment_id = :id"
                        + " and relation_type = :type and removed_at is null and related_judgment_id is not distinct from"
                        + " :related and related_citation is not distinct from :citation)")
                .param("id", judgmentId).param("type", request.relationType()).param("related", request.relatedJudgmentId())
                .param("citation", citation).query(Boolean.class).single();
        if (twice) {
            throw new ConflictException("this relation is already recorded");
        }
        UUID id = jdbc.sql("insert into library_judgment_relation (judgment_id, relation_type, related_judgment_id,"
                        + " related_citation, relation_date, source_reference, notes, created_by) values (:id, :type,"
                        + " :related, :citation, :date, :source, :notes, :actor) returning id")
                .param("id", judgmentId).param("type", request.relationType()).param("related", request.relatedJudgmentId())
                .param("citation", citation).param("date", request.relationDate())
                .param("source", request.sourceReference().strip()).param("notes", LibraryText.trimToNull(request.notes()))
                .param("actor", actor.id()).query(UUID.class).single();
        audit.recordWithChange("LIBRARY_RELATION_ADD", actor.id(), actor.username(), "LIBRARY_RELATION", id.toString(),
                request.relationType() + " on " + judgmentId);
        return of(judgmentId);
    }

    @Transactional
    void remove(UUID relationId) {
        ActorRef actor = currentActor.require();
        UUID judgment = jdbc.sql("update library_judgment_relation set removed_at = now(), removed_by = :actor"
                        + " where id = :id and removed_at is null returning judgment_id")
                .param("id", relationId).param("actor", actor.id()).query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException("relation"));
        audit.recordWithChange("LIBRARY_RELATION_REMOVE", actor.id(), actor.username(), "LIBRARY_RELATION",
                relationId.toString(), "on " + judgment);
    }
}
