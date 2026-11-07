package com.lawdesk.cases;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.NotFoundException;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * Who may change the content of one case (LD-045). The role permissions say what a user can do to cases in general;
 * this says on which cases. A case with a primary member (العضو المختص) can be changed by its primary and alternate
 * members and by case supervisors (holders of {@code CASE_STATUS_CHANGE}: the administrator and the legal manager,
 * who keep today's rights to edit and reassign). Any other user, department member or not, is refused — in the
 * service layer, whatever the screen shows. A case without a primary member (it predates the assignment) keeps the old
 * rule: the permission alone decides. Access is read from the database on every call, so a reassignment applies at once.
 * Reading a case is not affected.
 */
@Component
public class CaseAccess {

    private final CaseRepository cases;
    private final CurrentActor currentActor;

    CaseAccess(CaseRepository cases, CurrentActor currentActor) {
        this.cases = cases;
        this.currentActor = currentActor;
    }

    /** True when the signed-in user may change the content of this case. */
    @Transactional(readOnly = true)
    public boolean mayChange(UUID caseId) {
        Optional<CaseRepository.Assignment> assignment = cases.assignment(caseId);
        if (assignment.isEmpty()) {
            throw new NotFoundException("case");
        }
        return mayChange(assignment.get());
    }

    boolean mayChange(CaseRepository.Assignment assignment) {
        if (assignment.primary() == null || isSupervisor()) {
            return true;
        }
        return assignment.isMember(currentActor.require().id());
    }

    /** @throws AccessDeniedException when the signed-in user may not change this case (403) */
    @Transactional(readOnly = true)
    public void requireChange(UUID caseId) {
        if (!mayChange(caseId)) {
            throw new AccessDeniedException("not a member of this case");
        }
    }

    /** The administrator and the legal manager: the holders of the case-wide management permission. */
    boolean isSupervisor() {
        return currentActor.has(Permission.CASE_STATUS_CHANGE);
    }
}
