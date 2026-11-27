-- lawdesk-sql: audit
-- Read-only privilege audit (T-DB-1, T-DB-8, T-DB-12): run as a superuser inside the lawdesk database after migrations and after a
-- restore. Fails with the list of everything that is not as the separated ownership model requires. Changes nothing.
DO $$
DECLARE
  found text[] := '{}';
  runtime text[];
  r text;
BEGIN
  SELECT array_agg(x) INTO runtime FROM (
    SELECT unnest(ARRAY['lawdesk_app', 'lawdesk_dml', 'lawdesk_legal', 'lawdesk_operator']) AS x
    UNION SELECT m.rolname FROM pg_auth_members am JOIN pg_roles g ON g.oid = am.roleid AND g.rolname = 'lawdesk_operator'
                           JOIN pg_roles m ON m.oid = am.member) s;

  FOREACH r IN ARRAY ARRAY['lawdesk_owner', 'lawdesk_dml', 'lawdesk_app', 'lawdesk_legal', 'lawdesk_operator'] LOOP
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = r) THEN
      RAISE EXCEPTION 'privilege audit failed: role % is missing', r;
    END IF;
  END LOOP;

  -- 1. everything in the schema belongs to the owner
  SELECT found || coalesce(array_agg(what), '{}') INTO found FROM (
    SELECT 'schema lawdesk is owned by ' || pg_get_userbyid(nspowner) AS what FROM pg_namespace
      WHERE nspname = 'lawdesk' AND pg_get_userbyid(nspowner) <> 'lawdesk_owner'
    UNION ALL SELECT 'relation ' || relname || ' is owned by ' || pg_get_userbyid(relowner) FROM pg_class
      WHERE relnamespace = to_regnamespace('lawdesk') AND pg_get_userbyid(relowner) <> 'lawdesk_owner'
    UNION ALL SELECT 'function ' || proname || ' is owned by ' || pg_get_userbyid(proowner) FROM pg_proc
      WHERE pronamespace = to_regnamespace('lawdesk') AND pg_get_userbyid(proowner) <> 'lawdesk_owner'
    UNION ALL SELECT 'type ' || typname || ' is owned by ' || pg_get_userbyid(typowner) FROM pg_type
      WHERE typnamespace = to_regnamespace('lawdesk') AND pg_get_userbyid(typowner) <> 'lawdesk_owner'
    UNION ALL SELECT 'a runtime role owns an object (' || r2.rolname || ')' FROM pg_shdepend d JOIN pg_roles r2 ON r2.oid = d.refobjid
      WHERE d.deptype = 'o' AND r2.rolname = ANY (runtime)
        AND d.dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  ) v;

  -- 2. attributes, memberships, logins
  SELECT found || coalesce(array_agg(what), '{}') INTO found FROM (
    SELECT 'role ' || rolname || ' has a power attribute' AS what FROM pg_roles
      WHERE rolname = ANY (runtime || 'lawdesk_owner'::text) AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls)
    UNION ALL SELECT 'role ' || a.rolname || ' is a member of ' || b.rolname FROM pg_roles a, pg_roles b
      WHERE a.rolname = ANY (runtime || 'lawdesk_owner'::text) AND a.oid <> b.oid AND NOT a.rolsuper
        AND (b.rolsuper OR b.rolname LIKE 'pg\_%' OR (b.rolname = 'lawdesk_owner' AND a.rolname <> 'lawdesk_owner'))
        AND pg_has_role(a.oid, b.oid, 'MEMBER')
    UNION ALL SELECT 'role ' || rolname || ' must not log in' FROM pg_roles
      WHERE rolname IN ('lawdesk_dml', 'lawdesk_legal', 'lawdesk_operator') AND rolcanlogin
  ) v;

  -- 3. database, schema, object and parameter privileges of the runtime
  FOREACH r IN ARRAY runtime LOOP
    IF has_database_privilege(r, current_database(), 'CREATE') OR has_database_privilege(r, current_database(), 'TEMPORARY') THEN
      found := found || (r || ' may create schemas or temporary tables');
    END IF;
    IF has_schema_privilege(r, 'lawdesk', 'CREATE') OR has_schema_privilege(r, 'public', 'CREATE') THEN
      found := found || (r || ' may create objects in a schema');
    END IF;
    IF has_parameter_privilege(r, 'session_replication_role', 'SET') THEN
      found := found || (r || ' may set session_replication_role (triggers could be switched off)');
    END IF;
    SELECT found || coalesce(array_agg(r || ' holds more than row access on ' || c.relname), '{}') INTO found FROM pg_class c
      WHERE c.relnamespace = to_regnamespace('lawdesk') AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
        AND (has_table_privilege(r, c.oid, 'TRUNCATE, REFERENCES, TRIGGER, MAINTAIN')
             OR has_table_privilege(r, c.oid, 'SELECT WITH GRANT OPTION, INSERT WITH GRANT OPTION, UPDATE WITH GRANT OPTION, DELETE WITH GRANT OPTION'));
    IF to_regclass('lawdesk.flyway_schema_history') IS NOT NULL
       AND has_table_privilege(r, 'lawdesk.flyway_schema_history', 'INSERT, UPDATE, DELETE') THEN
      found := found || (r || ' may write the migration history');
    END IF;
  END LOOP;

  -- 4. no function runs with its owner's rights for the runtime (none exists yet; a later one must be listed here on purpose)
  SELECT found || coalesce(array_agg('SECURITY DEFINER function ' || p.proname || ' is executable by the runtime'), '{}') INTO found
    FROM pg_proc p WHERE p.pronamespace = to_regnamespace('lawdesk') AND p.prosecdef
      AND (has_function_privilege('lawdesk_app', p.oid, 'EXECUTE') OR has_function_privilege('lawdesk_dml', p.oid, 'EXECUTE'));

  IF cardinality(found) > 0 THEN
    RAISE EXCEPTION 'privilege audit failed: %', array_to_string(found, '; ');
  END IF;
  RAISE NOTICE 'privilege audit passed';
END $$;
