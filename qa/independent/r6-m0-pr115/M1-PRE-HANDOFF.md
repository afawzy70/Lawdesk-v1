# M1-pre handoff (for the development Claude) — start ONLY after the owner's explicit written go-ahead

Scope (08 §9): separate database ownership from runtime access. **No R6 table, no R6 behaviour, no legal effect.** Own branch from current develop, draft PR, failing tests committed first, no merge, no UAT change without the owner. Coordinate any change to `deploy/` before writing it.

Work: roles `lawdesk_owner`, `lawdesk_dml`, `lawdesk_app`, `lawdesk_legal`, `lawdesk_operator` (+ per-person operator logins, group NOLOGIN); idempotent `deploy/postgres/roles.sql`; one-off transfer for existing installs (`REASSIGN OWNED`, default privileges, `REVOKE TEMPORARY`, no write to `flyway_schema_history` for runtime); `MigrateOnly` job running Flyway as the owner with `SPRING_FLYWAY_ENABLED=false` in the backend; privilege audit run by deploy after migrations and restore.

Acceptance criteria (each with a test that fails first):
1. T-DB-1 catalog audit: schema, tables, sequences, functions owned by `lawdesk_owner`; runtime roles not superuser/CREATEROLE/CREATEDB/REPLICATION/BYPASSRLS, not members of the owner, no `CREATE`/`TEMPORARY`/`TRIGGER`/`TRUNCATE`/`REFERENCES`.
2. T-DB-2…8, 12: runtime attempts (ALTER, DROP/DISABLE TRIGGER, replace function, CREATE TABLE, SET ROLE, session_replication_role, grants) → `42501`; audit fails the deploy on any drift; restore re-runs the audit.
3. T-DB-13: compose mounts no owner or operator secret into the backend container.
4. T-DB-15: catalog test that fails when any foreign key with a non-restricting action writes into a guarded table; plus trigger-order test on guarded tables.
5. T-DB-9, T-MIG-3: **previous-release jar** started against the split schema, CRUD smoke and background jobs; result written down, including the documented DDL limit.
6. Existing-data compatibility: row counts and a data digest identical before/after the ownership transfer on a restored copy of a UAT backup (never on UAT); PG 18.x fresh and upgrade matrix; rollback and restore drill with owner approval.
7. Read-only inspection of actual UAT object ownership by the owner/DevOps, result attached (never by a write).
8. Introduces nothing named R6 and activates nothing in 05/07/08 (gates, policies, grants OFF); no frontend change.
9. QA gate: independent review on the real migrations and scripts, not the probe; address QA-R6-M0-021 items that touch roles/counter.
