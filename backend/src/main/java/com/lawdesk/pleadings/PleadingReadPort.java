package com.lawdesk.pleadings;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The read-only door other modules use to list the requests and the defences of a case (LD-059: the legal assistant). It is
 * {@link ClaimService} and {@link DefenseService}: a case the user may not see is reported as missing (404), and a list the
 * user holds no CLAIM_VIEW / DEFENSE_VIEW permission for comes back empty rather than refused.
 */
@Component
public class PleadingReadPort {

    /** A request ({@code CLAIM}) or a defence ({@code DEFENSE}) with the stored text and where it was raised. */
    public record Assertion(String type, UUID id, int seq, String title, String text, String basis, String status, LocalDate date,
            String party) {
    }

    private final ClaimService claims;
    private final DefenseService defenses;
    private final CurrentActor currentActor;

    PleadingReadPort(ClaimService claims, DefenseService defenses, CurrentActor currentActor) {
        this.claims = claims;
        this.defenses = defenses;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    public List<Assertion> claims(UUID caseId) {
        if (!currentActor.has(Permission.CLAIM_VIEW)) {
            return List.of();
        }
        List<Assertion> list = new ArrayList<>();
        for (ClaimRequests.View c : claims.list(caseId)) {
            list.add(new Assertion("CLAIM", c.id(), c.seq(), "#" + c.seq(), c.text(), null, c.status().name(), c.submittedOn(),
                    c.submittedByPartyName()));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<Assertion> defenses(UUID caseId) {
        if (!currentActor.has(Permission.DEFENSE_VIEW)) {
            return List.of();
        }
        List<Assertion> list = new ArrayList<>();
        for (DefenseRequests.View d : defenses.list(caseId)) {
            list.add(new Assertion("DEFENSE", d.id(), d.seq(), d.title(), d.argument(), d.legalBasis(), d.kind().name(),
                    d.raisedOn(), null));
        }
        return list;
    }
}
