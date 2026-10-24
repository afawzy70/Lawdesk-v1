# Phase 2: refined acceptance criteria (Authentication, Authorization & Audit)

`product/stories.json` carries templated criteria (`DRAFT_NEEDS_REFINEMENT`). This file states concrete, testable
behaviour for LD-007…LD-011, under the owner's direction of 2026-10-24 and `architecture/CONTRACTS.md` §3. Items
marked **ASSUMPTION** are developer choices that the owner can change without reworking the design. Every criterion
names the tests that cover it. Developer runs are not independent QA.

## LD-007 Database-backed login/logout

1. **Login.** `POST /api/v1/auth/login` with `{login, password}` authenticates against the `app_user` table.
   - `login` matches the username or the email, case-insensitively, after trimming.
   - On success the answer is 200 with the `GET /api/v1/me` body, and a new session id is issued (session fixation
     defence).
2. **Refusals are uniform.** A wrong password, an unknown user, a disabled account and a locked account all answer the
   same 401 problem, `{"title":"Unauthorized","status":401}`. Nothing in the answer tells them apart; only the audit
   record knows the reason.
3. **The session cookie.**
   - `JSESSIONID` is `HttpOnly`, `Secure` behind HTTPS and `SameSite=Lax`.
   - No token is returned in a body or stored by the SPA.
   - Idle timeout: **ASSUMPTION** 30 minutes.
4. **CSRF.**
   - `GET /api/v1/auth/csrf` (public) issues the `XSRF-TOKEN` cookie.
   - Every unsafe request, including login and logout, needs the `X-XSRF-TOKEN` header. Without it the answer is 403
     when signed in and 401 when anonymous.
5. **Logout.** `POST /api/v1/auth/logout` invalidates the session and expires the cookie, then answers 204. The old
   session id can no longer reach a protected endpoint (401).
6. **Who am I.** `GET /api/v1/me` returns `{id, username, displayName, email, locale, roles[], permissions[],
   mustChangePassword}` and needs a session (401 otherwise). Permissions are the union of the user's roles.
7. **Changes take effect at once.** A user who is disabled, or whose roles change, gets 401 or the new permissions on
   the next request: authorities are loaded from the database for every request. **ASSUMPTION**: one indexed lookup
   per request is acceptable at this scale.

## LD-008 Password storage and account policies

1. **Storage.** Passwords are stored as delegating `{bcrypt}` hashes, cost 12. Plain or reversible values never reach
   the database, logs, audit records or API answers.
2. **Policy.** **ASSUMPTION**: a password has 12 to 128 characters and is not equal to the username or the email. A
   violation answers 400 with `errors[].code` `PasswordPolicy`.
3. **Lockout.** **ASSUMPTION**: 5 consecutive failures lock the account for 15 minutes.
   - A successful login resets the counter.
   - A login during the lock is refused like any other refusal (LD-007.2), even with the right password.
4. **First administrator.** When the database has no users, the backend reads the bootstrap username, email and
   password file (`LAWDESK_BOOTSTRAP_ADMIN_*`), creates an enabled `ADMIN` with `mustChangePassword = true` and logs
   the username only.
   - Once any user exists, the bootstrap does nothing.
   - On staging, `deploy.sh` generates the password file once in the 0700 secrets directory. No credential is committed.
5. **Forced change.** A user with `mustChangePassword` may only call `/me`, `/auth/logout` and
   `POST /api/v1/me/password` (`{currentPassword, newPassword}`). Everything else answers 403 with
   `rule: "PASSWORD_CHANGE_REQUIRED"`. A successful change clears the flag.
6. **Administrative reset (LD-009).** `USER_MANAGE` sets a new temporary password and `mustChangePassword = true`, and
   the change is audited.

## LD-009 Role and permission administration

1. **Roles.** `ADMIN`, `LEGAL_MANAGER` and `LEGAL_STAFF` are seeded with the permissions of CONTRACTS.md §3.
   - Roles map to permission codes from `Permission`; an unknown code is a 400.
   - `GET /api/v1/roles` needs `ROLE_VIEW`. Creating a role, renaming it and changing its permissions need
     `ROLE_MANAGE`. Seeded roles can be edited but not deleted.
2. **Users.**
   - `GET /api/v1/users` (paged; search by name, username or email) and `GET /api/v1/users/{id}` need `USER_VIEW`.
   - Create, update, enable, disable, role assignment and password reset need `USER_MANAGE`.
   - Duplicate usernames or emails (case-insensitive) answer 409.
   - Field rules: username 3–64 characters `[a-z0-9._-]`, email up to 254 characters and valid, display name 1–128
     characters, locale `ar` or `en`.
3. **No escalation.**
   - A user cannot change their own roles or enabled state.
   - Only a holder of `ROLE_MANAGE` can grant a role that contains `ROLE_MANAGE` or `USER_MANAGE`.
   - The last enabled `ADMIN` cannot be disabled or lose `ADMIN`, and the `ADMIN` role keeps every permission. The rule
     violated is answered with 422 and `LAST_ADMIN`.
4. **Concurrency.** Updates carry `version`; a stale version answers 409.

## LD-010 Backend endpoint authorization

1. Every handler under `/api/**` either declares `@PreAuthorize`, or is one of the public auth endpoints (`csrf`,
   `login`). A test fails the build otherwise; it covers Phase 3 and Phase 4 endpoints too.
2. **Status codes.** Anonymous access answers 401; a missing permission answers 403. Both are problems without detail.
3. **Hidden identifiers.** Object identifiers that a user may not see answer 404, so resources cannot be enumerated
   (applied per module).
4. **Navigation is not access control.** The frontend hides what the user cannot do, and the backend enforces it
   regardless.

## LD-011 Security audit trail

1. **The table.** `audit_event` is append-only and holds `id, occurred_at, actor_id, actor_username, action, target_type,
   target_id, outcome (SUCCESS|FAILURE|DENIED), reason, request_id`. There is no update or delete path in the
   application.
2. **Events recorded:**
   - `LOGIN` (success; failure with reason `BAD_CREDENTIALS|DISABLED|LOCKED|UNKNOWN_USER`) and `LOGOUT`;
   - `PASSWORD_CHANGE`, `PASSWORD_RESET`;
   - `USER_CREATE`, `USER_UPDATE`, `USER_ENABLE`, `USER_DISABLE`, `USER_ROLES_CHANGE`;
   - `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_PERMISSIONS_CHANGE`;
   - `ACCESS_DENIED` (403 on `/api/**`).
3. **The attempted login.** For an unknown user it is stored truncated to 64 characters and never echoed back. No
   password, hash, session id or CSRF token is ever stored.
4. **Request id.** `request_id` is the correlation id of the request (`X-Request-Id`).
5. **Reading the trail.** `GET /api/v1/audit` (paged, filter by action, actor, outcome and time range) needs
   `AUDIT_VIEW`.
