# DOC-R3-RESTORE-OLD: restoring a backup taken before migration V800

Base: develop `3522775` (PR #87 introduced V800 and the DOC-RESTORE drill). Tool: `tools/restore-drill/restore_old_backup.py` (reuses the
guards, cluster handling, fingerprints and API reads of `restore_drill.py`: only its own throwaway PostgreSQL 18.6 cluster, loopback,
free ports, databases `lawdesk_drill_*`). Runbook produced: `docs/operations/RESTORE-OLD-BACKUPS.md`. Synthetic data only (SYNTH-DOC).
Status: VERIFIED_BY_DEVELOPER, not QA_ACCEPTED. Nothing touched UAT, its Docker volumes or any `/srv` path; the work directory was under the
session scratchpad and was deleted afterwards; the cluster was stopped (no process left).

Result: **PASSED, 21 of 21 checks, 293 s** (`drill-result.json`, `drill.log`). The shared host was loaded (load average 11-17), so times are pessimistic.

## What was built: a real old-state backup
The real application (the jar of this commit) migrated an empty database with `--spring.flyway.target=781` (781 is the last migration below
V800): 58 migrations applied, `library_trigrams` has no `search_path` setting (`proconfig` is NULL). The synthetic data of the DOC-RESTORE drill
was loaded (300 cases, 600 documents, 1200 versions with files, 848 links, grants, scopes, org units, tasks, audit rows: 15 169 rows in 43
non-empty tables of 88) plus 40 synthetic `library_judgment` rows (the table that cannot be restored). `pg_dump -U postgres -d <db> -Fc`:
123 612 449 bytes, 638 TOC entries, SHA-256 `d814d169...09ae2e66`. The application cannot finish booting on a V781 schema (Hibernate validation
needs later columns), which is irrelevant: Flyway finishes first and the application is stopped.

## (a) Restoring that dump as it is fails in the documented way
- `pg_restore --exit-on-error`: exit 1 at `CREATE TABLE library_judgment`: `ERROR: function library_normalize(text) does not exist ... CONTEXT: SQL function "library_trigrams" during inlining`; 63 of 88 tables had been created, `library_judgment` absent.
- `pg_restore` without `--exit-on-error`: exit 1, `errors ignored on restore: 14`, 87 of 88 tables, `library_judgment` (table, data, indexes, foreign keys) missing, silently.
- EXPERIMENT: `alter database ... set search_path = lawdesk` before `pg_restore --exit-on-error` does not help (exit 1): `pg_restore` sets the path per session. Not an option.
- The dump text of the function has no `search_path` (`library_trigrams_in_dump` in the JSON).

## (b) The repair that works: a `sed` over `pg_restore -f -`, loaded with `psql`
`pg_restore -f old.sql dump`, a one-line `sed` that appends `SET search_path TO 'lawdesk'` to the `CREATE FUNCTION lawdesk.library_trigrams` header (the
diff against the plain script is exactly one added line), `psql -v ON_ERROR_STOP=1 -d <empty db> -f old-repaired.sql`: exit 0, empty stderr (12 s).
The runbook's quoting variant of the sed was run separately and gives a byte-identical file. Then: `app-role.sql` re-applied, `ANALYZE`, application
boot: **Flyway applied 9 migrations (V800, V801-V803, V840-V844)**, the schema validated, the application is healthy, and `library_trigrams` carries
`search_path=lawdesk` (V800 on top of the repair). The reference (the old source migrated forward the same way) applied the same 9.

Not tried (and why): the pre-created function with a `pg_restore -L` list (needs the full function bodies copied by hand and a hand-edited TOC; the sed is shorter and
passed, so it was not pursued); `pg_restore -j` on the repaired file (it is a plain script, loaded by one `psql`).

## The 15 DOC-RESTORE checks, adapted, plus the new ones (all PASS)
Same checks as `docs/evidence/DOC-RESTORE/run3-final.json`, with the source as the reference. Two adaptations are needed because the source is the OLD
schema: the database-level checks (1, 4-10) compare the restored database with the old source BEFORE Flyway; the API checks (13-15) and the final
structure check compare the restored database with the old source after both were migrated forward by the application.

| DOC-RESTORE check | Here |
|---|---|
| 1 source files match SHA-256 | PASS (1200 version files 70 971 384 bytes, 600 head files) |
| 2 experiment: wrong order refused | not repeated (unchanged by this work); replaced by the new failure experiments (a) |
| 3 pg_restore printed nothing | PASS for the repaired path (empty `psql` stderr); the as-is path is the failure being shown |
| 4 restored identical to source | PASS: rows, hashes, files, FKs, links, versions, grants, scopes, sequences, visibility equal; the only difference is `structure.0.digest`, the repaired function text |
| 5-10 files, orphans/FKs (131), cross-case links, visibility of four personas (267/173/225/187), predicate vs `case_visible()`, role `search_path` | PASS |
| 11 `-j 4` experiment | not run (the repaired file is a plain script) |
| 12 Flyway applied nothing new | replaced: Flyway applied exactly the 9 migrations the reference applied |
| 13-15 API answers identical, smoke reads, downloaded files hashed | PASS |
New checks: source really pre-V800; old dump has no search_path; (a) strict failure message; (a) lenient 14 ignored errors and missing table; database search_path does not help; repaired restore exit 0; `library_judgment` back with 40 of 40 rows; after Flyway `library_trigrams` has the setting; after Flyway the restored database equals the migrated reference in every fingerprinted table, file, FK, link, version, grant, structure and function (the digests of `app_release`, `audit_event` and `flyway_schema_history` are excluded because every boot rewrites them; their row counts are compared and the Flyway history compares equal by version, description and checksum).

## Tool defects found and fixed during this work
1. `--target 799` stopped the first attempt: Flyway needs an existing version ("No migration with a target version 799 could be found"). Default is now 781.
2. The first complete run failed two of its own checks: the as-is error check looked at the last 700 characters of pg_restore's stderr (the message is earlier) and the final equality
   check compared per-boot rows. Both fixed in the tool; the run above is the rerun (no product code changed).

## Not run / limits
- A real production-size backup, a dump from another PostgreSQL version, another schema name than `lawdesk` (the sed names it).
- The repaired restore was run once (no repetition for variance); times come from a loaded shared host.
- No change to V800 or any migration; `docs/handbook/06-operations-backup-restore-ar.md` only gained a pointer to the new runbook.
