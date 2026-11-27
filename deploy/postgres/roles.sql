-- lawdesk-sql: roles
-- Cluster roles of the separated ownership model. Idempotent; run as a superuser. Creates and constrains, never removes, and sets no
-- password (passwords.sql does). Also the first step of a restore into an empty cluster, because a dump names these owners.
--   lawdesk_owner     owns the schema and every object; used only by the one-shot migrate job (Flyway)
--   lawdesk_dml       group: read and write rows, nothing else
--   lawdesk_app       the backend's runtime login; member of lawdesk_dml; owns nothing
--   lawdesk_legal     reserved for a later milestone; cannot log in
--   lawdesk_operator  reserved group of per-person operator logins; the group itself cannot log in and holds no privilege yet
DO $$
DECLARE r text;
BEGIN
  FOREACH r IN ARRAY ARRAY['lawdesk_owner', 'lawdesk_dml', 'lawdesk_app', 'lawdesk_legal', 'lawdesk_operator'] LOOP
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = r) THEN
      EXECUTE format('CREATE ROLE %I NOLOGIN', r);
    END IF;
  END LOOP;
END $$;
ALTER ROLE lawdesk_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE lawdesk_dml NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE lawdesk_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE lawdesk_legal NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE lawdesk_operator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
GRANT lawdesk_dml TO lawdesk_app, lawdesk_legal WITH INHERIT TRUE, SET FALSE;
ALTER ROLE lawdesk_owner SET search_path = lawdesk;
ALTER ROLE lawdesk_app SET search_path = lawdesk;
ALTER ROLE lawdesk_legal SET search_path = lawdesk;
