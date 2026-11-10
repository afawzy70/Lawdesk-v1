# Operator runbook: restoring a backup taken BEFORE migration V800

Applies to `pg_dump -Fc` files (for example `backups/*.dump` made by `deploy/scripts/deploy.sh`) taken while the database was
still below Flyway version 800. Evidence: `docs/evidence/DOC-R3-RESTORE-OLD/README.md` (run with `tools/restore-drill/restore_old_backup.py`).
This runbook adds to, and does not replace, the restore procedure in `docs/handbook/06-operations-backup-restore-ar.md` section 5.

## Why a plain restore of such a file fails

V800 (`alter function library_trigrams(text) set search_path from current`) changes the LIVE database only. A dump file taken earlier
still contains the old definition of `library_trigrams` (V640): a SQL function whose body calls `library_normalize` by its plain
name. `pg_restore` empties the search path, PostgreSQL inlines that body while it creates the generated column of
`library_judgment`, and `CREATE TABLE library_judgment` fails with `function library_normalize(text) does not exist`.

- with `--exit-on-error`: the restore stops at that statement;
- without it: the restore continues, prints `errors ignored on restore: N`, exits with code 1, and the database is left WITHOUT
  `library_judgment` (its data, indexes and foreign keys). Never accept that outcome.

Restoring the old file and then letting Flyway apply V800 does not help: the table is already missing by then.
Setting `search_path` on the database or the role does not help either (tested): `pg_restore` sets it per session.

## Recognise an old dump

```bash
pg_restore -f - backups/<file>.dump | sed -n '/^CREATE FUNCTION lawdesk\.library_trigrams/,/^\$\$;/p' | grep -c search_path
```
`0` means the file has the old definition and needs the repair below. A dump taken at V800 or later should print `1` and uses the normal procedure
(that `1` case was not exercised in this drill; the repair was only run on a pre-V800 file).

## Procedure (on a SIDE database first; never over the live database; always as the PostgreSQL superuser)

Placeholders: `DUMP` the file, `NEWDB` a name that does not exist yet, connection options as in the handbook.

1. Make the plain script out of the dump (nothing is changed in the dump file):
   ```bash
   pg_restore -f /tmp/old.sql DUMP
   ```
2. Give `library_trigrams` its own search path (adds one line after the `CREATE FUNCTION` line; nothing else changes):
   ```bash
   sed "s/^CREATE FUNCTION lawdesk\.library_trigrams(p_text text) RETURNS text\[\]\$/&\n    SET search_path TO 'lawdesk'/" /tmp/old.sql > /tmp/old-repaired.sql
   diff /tmp/old.sql /tmp/old-repaired.sql      # must show exactly one added line: SET search_path TO 'lawdesk'
   ```
3. Create an EMPTY database (no schema, no `app-role.sql` before the restore; the role `lawdesk_app` must exist in the cluster) and load the repaired script, stopping at the first error:
   ```bash
   createdb NEWDB
   psql -X -q -v ON_ERROR_STOP=1 -d NEWDB -f /tmp/old-repaired.sql      # must print nothing
   ```
4. Afterwards, as in the handbook: re-apply `deploy/postgres/app-role.sql` for `NEWDB`, then `ANALYZE`.
5. Check before anything is switched: `select count(*) from lawdesk.library_judgment` equals the count you expect from the old database; row counts and file hashes as in the handbook (step 5 there).
6. Start the application on `NEWDB`. Flyway applies V800 and every later migration (V800 sets the search path on `library_trigrams` for good). A restore of a pre-V800 file therefore ends with the application healthy and `proconfig` of `library_trigrams` set.
7. Switching the application to `NEWDB` remains a separate decision with the owner (handbook step 6).

## What was verified, and what was not

See the evidence README for the numbers. Verified on a synthetic pre-V800 backup (Flyway 781, PostgreSQL 18.6 throwaway cluster): the as-is failures
above, the repair above, V800 and later applied on top, and the 15 DOC-RESTORE checks adapted to the old state. Not verified: a real production-size
dump, a dump taken by a PostgreSQL version other than 18, a dump with another schema name than `lawdesk` (the sed pattern names the schema), and the
`-j` parallel restore of the repaired script (the repaired file is a plain script and is loaded by one `psql`).
