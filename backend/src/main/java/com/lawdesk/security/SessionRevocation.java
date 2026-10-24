package com.lawdesk.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import com.lawdesk.audit.AuditLog;

/**
 * LD-008.7 (QAD-028): ends a session whose credential version is older than the account's, that is a session signed in
 * before an administrative reset, a password change by another session, or a disablement. The refusal is audited as
 * {@code SESSION_REVOKED} with the user only: never the session id, a cookie or a token.
 */
@Component
public class SessionRevocation {

    /** Answered as 401 by the authentication entry point, like any request without a sign-in. */
    static final class SessionRevokedException extends AuthenticationException {

        SessionRevokedException() {
            super("session revoked");
        }
    }

    private final AuditLog audit;

    SessionRevocation(AuditLog audit) {
        this.audit = audit;
    }

    /** Whether the session's principal signed in with an older credential version than the account now has. */
    static boolean revoked(LawdeskUser session, long currentCredentialVersion) {
        return session.credentialVersion() != currentCredentialVersion;
    }

    /** Records the refusal and invalidates the session, so later requests with its cookie are simply anonymous. */
    public void refuse(HttpServletRequest request, LawdeskUser user) {
        audit.record("SESSION_REVOKED", AuditLog.Outcome.DENIED, user.id(), user.username(), "USER",
                user.id().toString(), null);
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                // A concurrent request of the same session got there first.
            }
        }
    }
}
