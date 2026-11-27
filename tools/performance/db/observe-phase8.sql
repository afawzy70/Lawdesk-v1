-- Run only on the private Phase 8 database after pg_stat_statements is enabled.
-- Aggregates only; no document bytes, account secrets, or SQL bind values.
select current_database() as database, version() as postgres_version, now() as observed_at;
select state, count(*) as connections
from pg_stat_activity where datname = current_database()
group by state order by state;
select datname, numbackends, blks_hit, blks_read,
       round(100.0 * blks_hit / nullif(blks_hit + blks_read, 0), 2) as buffer_hit_pct
from pg_stat_database where datname = current_database();
select queryid, calls, round(total_exec_time::numeric, 2) as total_ms,
       round(mean_exec_time::numeric, 2) as mean_ms, rows,
       shared_blks_hit, shared_blks_read, temp_blks_written
from pg_stat_statements order by total_exec_time desc limit 25;
select relname, n_live_tup, n_dead_tup,
       pg_size_pretty(pg_total_relation_size(relid)) as total_size
from pg_stat_user_tables
where schemaname = 'lawdesk' and relname in
      ('legal_case', 'proceeding', 'judgment', 'hearing', 'task', 'case_document', 'expert_referral', 'audit_event')
order by relname;
