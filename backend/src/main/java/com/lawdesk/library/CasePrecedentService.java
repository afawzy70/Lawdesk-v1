package com.lawdesk.library;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.library.LibraryViews.JudgmentRef;
import com.lawdesk.library.LibraryViews.Precedent;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * Library judgments used in a case (requirements section 16.4): many to many, each link with the reason it helps and how
 * alike the facts and issues are. Every route goes through the case access policy: a case the user may not see is
 * reported as missing (404), a case seen but not changeable refuses the change (403). A link is the member's decision,
 * made on the case; suggestions only propose.
 */
@Service
class CasePrecedentService {

    static final String JUDGMENT_WITHDRAWN = "LIBRARY_JUDGMENT_WITHDRAWN";

    static final String REF_COLUMNS = "j.id as j_id, j.court_name, j.court_level, j.case_number, j.case_year,"
            + " j.judgment_date, j.case_type, j.subject, j.finality, j.status, j.source_reference, j.copy_reference";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final CaseAccessPolicy access;
    private final CaseLookup cases;

    CasePrecedentService(JdbcClient jdbc, CurrentActor currentActor, AuditLog audit, CaseAccessPolicy access,
            CaseLookup cases) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.audit = audit;
        this.access = access;
        this.cases = cases;
    }

    static JudgmentRef ref(ResultSet rs) throws SQLException {
        return new JudgmentRef(rs.getObject("j_id", UUID.class), rs.getString("court_name"), rs.getString("court_level"),
                rs.getString("case_number"), rs.getObject("case_year") == null ? null : rs.getInt("case_year"),
                rs.getObject("judgment_date", LocalDate.class), rs.getString("case_type"), rs.getString("subject"),
                rs.getString("finality"), rs.getString("status"), rs.getString("source_reference"),
                rs.getString("copy_reference") != null);
    }

    private Precedent row(ResultSet rs) throws SQLException {
        List<String> types = new ArrayList<>();
        for (Object type : (Object[]) rs.getArray("relation_types").getArray()) {
            types.add((String) type);
        }
        return new Precedent(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class), ref(rs),
                rs.getString("reason"), rs.getString("similarity_note"), rs.getString("origin"), types,
                rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class));
    }

    private static final String SELECT = "select l.id, l.case_id, l.reason, l.similarity_note, l.origin, l.created_at,"
            + " l.created_by, " + REF_COLUMNS + ", array(select distinct r.relation_type from library_judgment_relation r"
            + " where r.judgment_id = j.id and r.removed_at is null order by 1) as relation_types"
            + " from case_precedent_link l join library_judgment j on j.id = l.library_judgment_id";

    @Transactional(readOnly = true)
    List<Precedent> forCase(UUID caseId) {
        access.requireView(caseId);
        return jdbc.sql(SELECT + " where l.case_id = :caseId and l.removed_at is null order by l.created_at, l.id")
                .param("caseId", caseId).query((rs, n) -> row(rs)).list();
    }

    @Transactional
    Precedent link(UUID caseId, LibraryRequests.PrecedentLink request) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        String status = jdbc.sql("select status from library_judgment where id = :id").param("id", request.judgmentId())
                .query(String.class).optional().orElseThrow(() -> new NotFoundException("judgment"));
        if ("WITHDRAWN".equals(status)) {
            throw new BusinessRuleException(JUDGMENT_WITHDRAWN, "a withdrawn judgment cannot be linked to a case");
        }
        UUID id;
        try {
            id = jdbc.sql("insert into case_precedent_link (case_id, library_judgment_id, reason, similarity_note, origin,"
                            + " created_by) values (:caseId, :judgment, :reason, :note, :origin, :actor) returning id")
                    .param("caseId", caseId).param("judgment", request.judgmentId())
                    .param("reason", request.reason().strip()).param("note", LibraryText.trimToNull(request.similarityNote()))
                    .param("origin", request.origin() == null ? "MANUAL" : request.origin()).param("actor", actor.id())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("this judgment is already linked to the case");
        }
        audit.recordWithChange("CASE_PRECEDENT_LINK", actor.id(), actor.username(), "CASE", caseId.toString(),
                "judgment " + request.judgmentId());
        cases.recordEvent(caseId, "PRECEDENT_LINKED", "Library judgment linked: " + citation(request.judgmentId()));
        return jdbc.sql(SELECT + " where l.id = :id").param("id", id).query((rs, n) -> row(rs)).single();
    }

    @Transactional
    void unlink(UUID caseId, UUID linkId) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        UUID judgment = jdbc.sql("update case_precedent_link set removed_at = now(), removed_by = :actor where id = :id"
                        + " and case_id = :caseId and removed_at is null returning library_judgment_id")
                .param("id", linkId).param("caseId", caseId).param("actor", actor.id()).query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException("precedent link"));
        audit.recordWithChange("CASE_PRECEDENT_UNLINK", actor.id(), actor.username(), "CASE", caseId.toString(),
                "judgment " + judgment);
        cases.recordEvent(caseId, "PRECEDENT_UNLINKED", "Library judgment unlinked: " + citation(judgment));
    }

    private String citation(UUID judgmentId) {
        String text = jdbc.sql("select court_name || coalesce(' ' || case_number, '') || coalesce('/' || case_year, '')"
                        + " from library_judgment where id = :id").param("id", judgmentId).query(String.class).single();
        return text.length() > 400 ? text.substring(0, 400) : text;
    }
}
