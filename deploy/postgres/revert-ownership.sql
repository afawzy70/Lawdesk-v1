-- lawdesk-sql: revert-ownership
-- EMERGENCY ONLY, by an operator's decision, never by the deployment: gives every object back to the runtime role, which removes
-- the protection of the separated model (the next deployment of a split-aware release transfers it again). Needed only for a
-- release that must run its own DDL. Take a backup first. As a superuser inside the lawdesk database.
BEGIN;
SET LOCAL lock_timeout = '20s';
REASSIGN OWNED BY lawdesk_owner TO lawdesk_app;
DO $$
BEGIN
  EXECUTE format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO lawdesk_app', current_database());
END $$;
COMMIT;
