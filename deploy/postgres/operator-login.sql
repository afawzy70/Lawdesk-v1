-- lawdesk-sql: operator-login
-- Adds ONE person's operator login. NOT run by the deployment: who the operators are is an owner decision (D-09, pending), and the
-- group holds no privilege yet. As a superuser:
--   psql -U postgres -d lawdesk -c "set lawdesk.operator_login = 'op_<name>'" -f operator-login.sql   then  \password op_<name>
DO $$
DECLARE login text := current_setting('lawdesk.operator_login');
BEGIN
  IF login !~ '^op_[a-z][a-z0-9_]{1,30}$' THEN
    RAISE EXCEPTION 'operator login must look like op_<name>';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = login) THEN
    EXECUTE format('CREATE ROLE %I LOGIN', login);
  END IF;
  EXECUTE format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', login);
  EXECUTE format('GRANT lawdesk_operator TO %I', login);
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), login);
END $$;
