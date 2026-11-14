# Retiring «المحامون والوكلاء» — count, backup, restore, approval

Migration `V880` renames `case_party_counsel` to `case_party_counsel_retired`. It deletes nothing. The application no longer reads or writes
the table. Evidence: `docs/evidence/DOC-R5-COUNSEL-REMOVAL/` (the drill is `tools/restore-drill/counsel_retire_drill.py`, synthetic data only).

## 1. Count the real rows (read-only; run by the operator `devops`, not by the developer)

`deploy/scripts/deploy.sh` takes a `pg_dump -Fc` of the running database before each deployment over a running release; the files are in
`/srv/deployments/lawdesk/backups/` (newest 10). To count the rows on the live UAT database, from a shell that can reach it (container name per
`docs/operations/STAGING-RUNBOOK.md`):

```bash
psql -X -v ON_ERROR_STOP=1 -U postgres -d <database> -c \
 "set search_path to lawdesk; select count(*) as total, count(*) filter (where removed_at is null) as not_removed from case_party_counsel;"
```
Report the two numbers to the owner. The statement only reads.

## 2. Verified backup before the deployment that runs V880

1. `pg_dump -Fc -U postgres -d <database> -f counsel-pre-v880.dump` (the deployment gate writes an equivalent file; this one is a named extra).
2. Verify it: `pg_restore --list counsel-pre-v880.dump | grep case_party_counsel` must show the table and `TABLE DATA`; then restore it into a side
   database that does not exist yet (`createdb side; pg_restore --exit-on-error --no-owner -d side counsel-pre-v880.dump`) and compare
   `select count(*) from case_party_counsel` in both databases. A dump taken below V800 needs the repair in `RESTORE-OLD-BACKUPS.md`.
3. Keep the file outside the deployment's rotation until the owner has decided on the deletion.

## 3. Recovery if the retirement has to be undone

* While the table is only renamed: `alter table case_party_counsel_retired rename to case_party_counsel;` (every row returns; both
  steps are proven by the drill).
* From the backup: restore the table from the verified dump into the live database in a maintenance window
  (`pg_restore --data-only --table=case_party_counsel` into a recreated table, or a full restore per the handbook).

## 4. Irreversible deletion (not part of this tree)

Needs, in this order: the count reported (section 1), the verified backup (section 2), and the owner's explicit written approval of the
deletion for the reported number of rows. Only then a separate migration `DROP TABLE case_party_counsel_retired` is prepared, reviewed and
deployed in a window agreed with Codex.
