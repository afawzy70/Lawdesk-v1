-- lawdesk-sql: ownership
-- Database-level part of the separated ownership model, run as a superuser inside the lawdesk database, after roles.sql. Idempotent
-- and atomic: one transaction, so the running backend never sees a moment without its privileges, and a lock that cannot be taken
-- in time leaves everything as it was. Run before migrating (grants default privileges) and after (protects the history table).
BEGIN;
SET LOCAL lock_timeout = '20s';

DO $$
BEGIN
  EXECUTE format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database());
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO lawdesk_owner, lawdesk_app, lawdesk_legal, lawdesk_operator', current_database());
  EXECUTE format('REVOKE CREATE, TEMPORARY ON DATABASE %I FROM lawdesk_app, lawdesk_dml, lawdesk_legal, lawdesk_operator', current_database());
END $$;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA IF NOT EXISTS lawdesk AUTHORIZATION lawdesk_owner;

-- Objects created by later migrations (any workstream) are usable by the runtime without knowing about this model.
ALTER DEFAULT PRIVILEGES FOR ROLE lawdesk_owner IN SCHEMA lawdesk GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO lawdesk_dml;
ALTER DEFAULT PRIVILEGES FOR ROLE lawdesk_owner IN SCHEMA lawdesk GRANT USAGE, SELECT ON SEQUENCES TO lawdesk_dml;
ALTER DEFAULT PRIVILEGES FOR ROLE lawdesk_owner IN SCHEMA lawdesk GRANT EXECUTE ON FUNCTIONS TO lawdesk_dml;
ALTER DEFAULT PRIVILEGES FOR ROLE lawdesk_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

-- The one-off transfer of an existing installation (objects still owned by the runtime role, also after restoring an old dump).
-- Grants on ALL objects happen only here: a later migration may withdraw rights on a table, and re-running this file must not
-- give them back.
DO $$
BEGIN
  IF EXISTS (SELECT FROM pg_shdepend d JOIN pg_roles r ON r.oid = d.refobjid
             WHERE r.rolname = 'lawdesk_app' AND d.deptype = 'o'
               AND d.dbid = (SELECT oid FROM pg_database WHERE datname = current_database())) THEN
    REASSIGN OWNED BY lawdesk_app TO lawdesk_owner;
    REVOKE ALL ON ALL TABLES IN SCHEMA lawdesk FROM lawdesk_app, lawdesk_dml;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA lawdesk TO lawdesk_dml;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA lawdesk TO lawdesk_dml;
    GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA lawdesk TO lawdesk_dml;
  END IF;
END $$;

REVOKE ALL ON SCHEMA lawdesk FROM PUBLIC, lawdesk_app, lawdesk_legal, lawdesk_operator;
GRANT USAGE ON SCHEMA lawdesk TO lawdesk_dml;

-- The migration history: the runtime reads it (an older release's Flyway needs that) and never writes it.
DO $$
BEGIN
  IF to_regclass('lawdesk.flyway_schema_history') IS NOT NULL THEN
    REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON lawdesk.flyway_schema_history FROM lawdesk_dml, lawdesk_app;
  END IF;
END $$;
COMMIT;
