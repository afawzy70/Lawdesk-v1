# DOC-RESTORE: restore drill on an isolated PostgreSQL 18.6

Base: develop 1d6d2a8. Tool: `tools/restore-drill/` (reproducible; refuses anything but its own throwaway cluster). Data: synthetic, marked SYNTH-DOC.
Host: shared Contabo machine, load average 12-20 during the runs, so every duration below is pessimistic and not a capacity figure.
Not a host-level drill: nothing here touched UAT, its Docker volume, its backups or Production.

| Run | File | Result |
|-----|------|--------|
| 1 | run1-before-fix.log/.json | FAILED at step 8: `pg_restore` of the real dump aborts at `CREATE TABLE library_judgment`: `function library_normalize(text) does not exist` (library_trigrams, V640, SQL function calling a plain name; pg_restore empties the search path). |
| 1b | run1-pg_restore-without-exit-on-error.err | Same dump without `--exit-on-error`: 14 errors ignored, exit 1, library_judgment (table, data, indexes, FKs) missing, everything else restored. |
| 2 | run2-after-fix-tool-check-defects.* | With V800: restore clean (empty stderr); all data identical. Two defects in the drill's own checks (structure text comparison of ANY(ARRAY) deparse; anonymous /release answers 401) fixed in the tool. |
| 3 | run3-final.log/.json | PASSED, 15 of 15 checks, 653 s total. |

Fix: `V800__LD-052_restore_safe_trigram_function.sql` (`alter function library_trigrams(text) set search_path from current`). Other SQL-language functions were checked: library_normalize and library_similarity use only pg_catalog objects; search_snippet (V745) calls library_normalize by plain name but is not used by a generated column or index, so it is not executed during restore.

## Run 3 measurements (scale 1: 300 cases, 600 documents, 1200 versions, 71 MB of version files + 36 MB head copies, 15 467 rows in 43 non-empty tables of 88)

| Step | Seconds |
|------|---------|
| start cluster | 5.2 |
| app migrates empty DB (59 migrations, includes JVM start) | 78.2 |
| load synthetic data | 16.2 |
| `pg_dump -U postgres -d <db> -Fc` (database 131.6 MB, dump 123.6 MB, 638 TOC entries) | 40.3 |
| `pg_restore --exit-on-error` single job into an empty database | 16.2 |
| re-apply deploy/postgres/app-role.sql | 0.2 |
| ANALYZE | 0.9 |
| experiment `pg_restore -j 4` | 13.3 |
| application boot on the restored DB (Flyway: nothing to apply) | 130.1 |
| fingerprints (database level, each) | 32-39 |

Dump SHA-256 95cbd4eeb361252750d5627fea3645abbff7ec65887108b461ee62c328d64401. Verified identical after restore: rows and a content hash of every table (88), SHA-256 of all 1200 version files and 600 head files against their stored hash and size, 131 foreign keys (validated, no orphans), 848 document links (300 claims, 150 defenses, 78 hearings, 200 judgments, 120 tasks; none across cases), version history, grants/scopes/org units/roles, sequences, function/trigger/index/constraint definitions, case_visible/case_changeable answers for four personas (267, 173, 225, 187 visible cases), and the API answers (cases, documents, downloaded file hashes) of the same four personas against the restored database.
Index and constraint texts differ cosmetically after any restore (`ANY ((ARRAY[..])::text[])` becomes `ANY (ARRAY[(..)::text])`); they are compared after removing casts and brackets (names, counts and normalised text equal); the raw digests are kept in `structure_raw` of the JSON.

## Mutation checks of the new backend tests (every change reverted afterwards)
- `to_regclass('public.' || :t)` in IndicatorService: NoPublicSchemaReferenceTest fails naming file and line (separate run); RestrictedRoleApplicationTest fails on the 5xx answers of the indicator endpoints.
- `OPEN` parsed as IN_PROGRESS in TaskStatus.parse and the V720 map row `NEW` to `DONE` (same run): 4 of the 10 TaskStatusMigrationApiTest tests fail (filters, reverse map, old-word writers, follow-up).
