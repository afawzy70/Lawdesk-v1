# Isolated pre-V800 backup experiment

This experiment used only the marked `tools/restore-drill` cluster in `/tmp`. Source application SHA: `352277571426bd4d135ea4acb0e12489aa09fc3c`. PostgreSQL 18.6; 300 synthetic cases, 600 documents, 1200 versions and one synthetic library judgment in the final run. The UAT database and its backups were never opened.

1. A complete current-schema restore passed first; see `restore-current/drill-result.json`.
2. In the synthetic source only, `ALTER FUNCTION lawdesk.library_trigrams(text) RESET search_path` reproduced the function attribute before V800. A synthetic library judgment was inserted with the schema in the session search path. `pg_dump -Fc` then created a **simulated old** backup. `pg_restore --exit-on-error` into a new empty `lawdesk_drill_old_judgment` database exited 1 while creating `lawdesk.library_judgment`, with `ERROR: function library_normalize(text) does not exist`. This directly confirms why a pre-V800 dump cannot be assumed restored by installing V800 in source code.
3. For a recovery experiment, `pg_restore --file` rendered that custom dump to plain SQL. Exactly one `CREATE FUNCTION lawdesk.library_trigrams` declaration was changed from `LANGUAGE sql IMMUTABLE PARALLEL SAFE` to that declaration plus `SET search_path TO 'lawdesk'`. No other SQL was edited. `psql -X -q -v ON_ERROR_STOP=1 -f` restored the resulting SQL into another empty `lawdesk_drill_old_recovered_judgment` database with empty stderr. `compare_pre_v800_recovery.py` found **zero differences** in table content digests including the library judgment, 1200 file and 600 head hashes, links, grants/scopes, foreign keys, sequences, Flyway history and visibility answers; see `pre-v800-with-judgment-result.json`. An earlier run without the library row also matched (`pre-v800-simulated-result.json`).

The above is **a QA reproduction and candidate recovery mechanism**, not an approved production operator procedure. The starting dump was simulated from a current schema by resetting one function attribute; it was not an actual archived pre-V800 backup. Claude should package a guarded procedure for real old dumps, verify the exact function declaration and its checksum before changing anything, preserve the original dump, restore into an empty isolated database, run complete fingerprint/API checks, and document how to stop on any mismatch. The patch must never be applied directly to a shared database or to an unreviewed dump.

Reproduction commands, with the marked cluster already running on a high local port, were:

```bash
# Only in the throwaway source, after verifying the two LAWDESK_DRILL_THROWAWAY markers.
psql -h /tmp/lawdesk-qa-restore-20261110/socket -p 55491 -U postgres -d lawdesk_drill_src \
  -v ON_ERROR_STOP=1 -c 'ALTER FUNCTION lawdesk.library_trigrams(text) RESET search_path'
pg_dump -h /tmp/lawdesk-qa-restore-20261110/socket -p 55491 -U postgres -d lawdesk_drill_src -Fc \
  -f /tmp/lawdesk-qa-restore-20261110/pre-v800-simulated.dump
pg_restore --file=/tmp/lawdesk-qa-restore-20261110/pre-v800-simulated.sql \
  /tmp/lawdesk-qa-restore-20261110/pre-v800-simulated.dump
```

The exact one-declaration patch is stated in step 3 above. The result JSON includes the dump SHA-256, patched function config, row counts and comparison fields. All large dump/SQL files are kept under `/tmp`, not committed. The comparison script accepts only a marked drill directory and a loopback port.
