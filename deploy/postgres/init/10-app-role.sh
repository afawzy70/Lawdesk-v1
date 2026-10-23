#!/bin/sh
# Runs once, when the data directory is first initialised. Creates the least-privilege role the backend uses:
# it owns only the `lawdesk` schema (where Flyway creates tables) and cannot create roles or databases.
set -eu
# The password is read inside psql (\set with backticks) so it never appears in a process argument list.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v app_password_file="$LAWDESK_APP_PASSWORD_FILE" <<'SQL'
\set app_password `cat :'app_password_file'`
CREATE ROLE lawdesk_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD :'app_password';
REVOKE ALL ON DATABASE lawdesk FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE lawdesk TO lawdesk_app;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA lawdesk AUTHORIZATION lawdesk_app;
ALTER ROLE lawdesk_app SET search_path = lawdesk;
SQL
