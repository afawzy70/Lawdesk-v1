-- Ensures the least-privilege role the backend uses, on every start, before the backend (deploy/scripts/deploy.sh).
-- Idempotent: creates what is missing and resynchronises the password with the secret file; never removes anything.
-- Runs inside the db container (psql -U postgres -d postgres); the password is read there and never leaves it.
\set ON_ERROR_STOP 1
\set app_password `cat /run/secrets/db_app_password`
SELECT 'CREATE DATABASE lawdesk' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'lawdesk') \gexec
SELECT 'CREATE ROLE lawdesk_app' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'lawdesk_app') \gexec
ALTER ROLE lawdesk_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'app_password';
\connect lawdesk
REVOKE ALL ON DATABASE lawdesk FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE lawdesk TO lawdesk_app;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA IF NOT EXISTS lawdesk AUTHORIZATION lawdesk_app;
ALTER ROLE lawdesk_app SET search_path = lawdesk;
