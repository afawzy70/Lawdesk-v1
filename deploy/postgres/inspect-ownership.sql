-- lawdesk-sql: inspect
-- Read-only inspection of an existing installation before the transfer (handoff criterion 7). For the owner or DevOps; it cannot
-- write: the transaction is READ ONLY and is rolled back.  psql -U postgres -d lawdesk -f inspect-ownership.sql
BEGIN TRANSACTION READ ONLY;
SELECT 'schema' AS kind, nspname AS name, pg_get_userbyid(nspowner) AS owner, 1 AS objects FROM pg_namespace
  WHERE nspname NOT LIKE 'pg\_%' AND nspname <> 'information_schema'
UNION ALL SELECT 'relation ' || relkind::text, n.nspname, pg_get_userbyid(c.relowner), count(*) FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname IN ('lawdesk', 'public') GROUP BY 1, 2, 3
UNION ALL SELECT 'function', n.nspname, pg_get_userbyid(p.proowner), count(*) FROM pg_proc p
  JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname IN ('lawdesk', 'public') GROUP BY 1, 2, 3
ORDER BY 1, 2, 3;
SELECT rolname, rolcanlogin, rolsuper, rolcreaterole, rolcreatedb, rolreplication, rolbypassrls FROM pg_roles WHERE rolname NOT LIKE 'pg\_%' ORDER BY 1;
SELECT datname, pg_get_userbyid(datdba) AS owner, datacl FROM pg_database WHERE datname = 'lawdesk';
SELECT extname, extversion FROM pg_extension ORDER BY 1;
SELECT proname AS security_definer_function FROM pg_proc WHERE pronamespace = to_regnamespace('lawdesk') AND prosecdef;
ROLLBACK;
