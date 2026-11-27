-- lawdesk-sql: passwords
-- Runs inside the db container; the passwords are read from the mounted secret files and never leave it.
\set app_password `cat /run/secrets/db_app_password`
\set owner_password `cat /run/secrets/db_owner_password`
ALTER ROLE lawdesk_app PASSWORD :'app_password';
ALTER ROLE lawdesk_owner PASSWORD :'owner_password';
