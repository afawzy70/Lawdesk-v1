-- lawdesk-sql: database
-- Run by deploy.sh through `psql -U postgres -d postgres`, BEFORE roles.sql, ownership.sql and passwords.sql, in one session.
\set ON_ERROR_STOP 1
SELECT 'CREATE DATABASE lawdesk' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'lawdesk') \gexec
\connect lawdesk
