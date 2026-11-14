package com.lawdesk.cases;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * «الطلبات» of a case: text items for each side. Replacing the lists marks the old items removed and keeps them.
 * Viewing needs the right to view the case (404 otherwise), changing it the right to change it (403), and the check
 * comes before any validation so a refusal never reveals that the case exists.
 */
@Service
class CaseRequestService {

    static final int MAX_ITEMS = 20;

    record Items(@Size(max = MAX_ITEMS) List<@NotBlank @Size(max = 2000) String> plaintiff,
            @Size(max = MAX_ITEMS) List<@NotBlank @Size(max = 2000) String> defendant) {

        static final Items EMPTY = new Items(List.of(), List.of());

        Items {
            plaintiff = clean(plaintiff);
            defendant = clean(defendant);
        }

        private static List<String> clean(List<String> values) {
            List<String> out = new ArrayList<>();
            if (values != null) {
                for (String value : values) {
                    String trimmed = Text.trimToNull(value);
                    out.add(trimmed == null ? "" : trimmed);
                }
            }
            return out;
        }

        boolean isEmpty() {
            return plaintiff.isEmpty() && defendant.isEmpty();
        }
    }

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CaseRepository cases;
    private final CaseTimeline timeline;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    CaseRequestService(JdbcClient jdbc, CaseAccessPolicy access, CaseRepository cases, CaseTimeline timeline,
            CurrentActor currentActor, AuditLog audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.cases = cases;
        this.timeline = timeline;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    Items list(UUID caseId) {
        access.requireView(caseId);
        return read(caseId);
    }

    @Transactional
    Items replace(UUID caseId, Items items) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        LegalCase legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        if (legalCase.status().detailsReadOnly()) {
            throw new BusinessRuleException(CaseService.CASE_READ_ONLY, "case " + caseId + " is " + legalCase.status());
        }
        Items current = read(caseId);
        if (current.equals(items)) {
            return current;
        }
        store(caseId, items, actor.id());
        timeline.append(caseId, "REQUESTS_CHANGED", CaseTimeline.fit("Requests updated: plaintiff "
                + items.plaintiff().size() + ", defendant " + items.defendant().size()), actor.id());
        audit.recordWithChange("CASE_REQUESTS_UPDATE", actor.id(), actor.username(), "CASE", caseId.toString(),
                "plaintiff=" + items.plaintiff().size() + ";defendant=" + items.defendant().size());
        return read(caseId);
    }

    /** Registration: the items of a case just created, inside the intake transaction (no access check needed). */
    void recordNew(UUID caseId, Items items, UUID actor) {
        if (!items.isEmpty()) {
            store(caseId, items, actor);
        }
    }

    private void store(UUID caseId, Items items, UUID actor) {
        jdbc.sql("update case_request_item set removed_at = now(), removed_by = :actor where case_id = :case and removed_at is null")
                .param("actor", actor).param("case", caseId).update();
        insert(caseId, Side.PLAINTIFF, items.plaintiff(), actor);
        insert(caseId, Side.DEFENDANT, items.defendant(), actor);
    }

    private void insert(UUID caseId, Side side, List<String> bodies, UUID actor) {
        for (int i = 0; i < bodies.size(); i++) {
            jdbc.sql("insert into case_request_item (case_id, side, sort_no, body, created_by) values (:case, :side, :n, :body, :actor)")
                    .param("case", caseId).param("side", side.name()).param("n", i + 1).param("body", bodies.get(i))
                    .param("actor", actor).update();
        }
    }

    private Items read(UUID caseId) {
        return new Items(side(caseId, Side.PLAINTIFF), side(caseId, Side.DEFENDANT));
    }

    private List<String> side(UUID caseId, Side side) {
        return jdbc.sql("select body from case_request_item where case_id = :case and side = :side and removed_at is null order by sort_no")
                .param("case", caseId).param("side", side.name()).query(String.class).list();
    }
}
