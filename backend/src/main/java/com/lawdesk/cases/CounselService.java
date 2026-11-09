package com.lawdesk.cases;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/** The lawyer or agent of a party on one case (requirements section 2); removal is a mark, the record stays. */
@Service
class CounselService {

    record Create(@Pattern(regexp = "LAWYER|AGENT") String kind, @NotBlank @Size(max = 200) String name,
            @Size(max = 30) @Pattern(regexp = "[0-9 +()-]+") String phone, @Size(max = 254) String email,
            @Size(max = 1000) String notes) {

        Create {
            name = Text.trimToNull(name);
            phone = Text.trimToNull(phone);
            email = Text.trimToNull(email);
            notes = Text.trimToNull(notes);
        }
    }

    record View(UUID id, UUID caseId, UUID partyId, String kind, String name, String phone, String email, String notes,
            Instant createdAt) {
    }

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CaseTimeline timeline;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    CounselService(JdbcClient jdbc, CaseAccessPolicy access, CaseTimeline timeline, CurrentActor currentActor,
            AuditLog audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.timeline = timeline;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    List<View> list(UUID caseId) {
        access.requireView(caseId);
        return jdbc.sql("""
                        select id, case_id, party_id, kind, name, phone, email, notes, created_at
                        from case_party_counsel where case_id = :case and removed_at is null
                        order by created_at, id""").param("case", caseId).query(CounselService::row).list();
    }

    @Transactional
    View add(UUID caseId, UUID partyId, Create request) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        if (!jdbc.sql("select exists (select 1 from case_party where case_id = :case and party_id = :party)")
                .param("case", caseId).param("party", partyId).query(Boolean.class).single()) {
            throw new NotFoundException("case party");
        }
        UUID id = jdbc.sql("""
                        insert into case_party_counsel (case_id, party_id, kind, name, phone, email, notes, created_by)
                        values (:case, :party, :kind, :name, :phone, :email, :notes, :actor) returning id""")
                .param("case", caseId).param("party", partyId).param("kind", request.kind())
                .param("name", request.name()).param("phone", request.phone()).param("email", request.email())
                .param("notes", request.notes()).param("actor", actor.id()).query(UUID.class).single();
        timeline.append(caseId, "COUNSEL_ADDED", CaseTimeline.fit(request.kind() + " recorded: " + request.name()),
                actor.id());
        return jdbc.sql("select id, case_id, party_id, kind, name, phone, email, notes, created_at"
                + " from case_party_counsel where id = :id").param("id", id).query(CounselService::row).single();
    }

    @Transactional
    void remove(UUID caseId, UUID id) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        int changed = jdbc.sql("""
                        update case_party_counsel set removed_at = now(), removed_by = :actor
                        where id = :id and case_id = :case and removed_at is null""")
                .param("actor", actor.id()).param("id", id).param("case", caseId).update();
        if (changed == 0) {
            throw new NotFoundException("counsel");
        }
        timeline.append(caseId, "COUNSEL_REMOVED", "Lawyer or agent removed", actor.id());
        audit.recordWithChange("CASE_COUNSEL_REMOVE", actor.id(), actor.username(), "CASE", caseId.toString(),
                "id=" + id);
    }

    private static View row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new View(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("party_id", UUID.class), rs.getString("kind"), rs.getString("name"), rs.getString("phone"),
                rs.getString("email"), rs.getString("notes"), rs.getTimestamp("created_at").toInstant());
    }
}
