# M1-pre — separated database ownership: what was built, what was shown, what was not

Authorised scope: M1-pre only. **Not QA-accepted, not merged, not deployed; no shared database touched.** Basis: independent QA `ROUND-5.md` and `M1-PRE-HANDOFF.md` (branch `qa/independent-r6-m0-pr115-r5-20261127`, `1ef4b2f`), design `LD-DOC-R6-08` and `-07` §2 (PR #115, `9da22f0`). Baseline `develop` = `92822792`, which GitHub records as the release deployed to staging on 2026-11-27 (the running process was not inspected).

## What changed
| Area | Change |
|---|---|
| `deploy/postgres/` | `database.sql`, `roles.sql`, `ownership.sql`, `passwords.sql`, `audit.sql` (run by deploy); `operator-login.sql`, `revert-ownership.sql`, `inspect-ownership.sql` (by hand). `app-role.sql` kept unchanged for rollback to releases from before the split |
| `deploy/compose.yaml` | one-shot `migrate` service (owner); backend runs with Flyway off; `db_owner_password` secret |
| `deploy/scripts`, `deploy/host/bootstrap.sh` | provision → migrate → audit → start; owner password generated when missing; audit findings do not block a rollback |
| backend | `MigrateOnly` (`java -jar app.jar migrate`); migration `V900__LD-050_case_reference_counter_guard.sql`. No other product code |
| CI | stack job follows the new order, checks ownership and the backend's secrets, and rehearses upgrade → rollback → redeploy from the deployed release |

## Roles
`lawdesk_owner` (login; migrate job only) · `lawdesk_dml` (group) · `lawdesk_app` (login; backend; owns nothing) · `lawdesk_legal` (**NOLOGIN, no password**) · `lawdesk_operator` (NOLOGIN group, no privilege). **No operator person login is created.**

Two choices differ from, or narrow, the handoff and are for QA and the owner to judge:
1. `lawdesk_legal` cannot log in: nothing uses it before M1, and a live unused credential in the backend would only add exposure.
2. "Per-person operator logins" are provided as a script and tested with a synthetic login; who the persons are is D-09 (pending).

## Test results
* Failing first (`before-*.txt`, commit `f05d32e`): against today's deployment the runtime role could disable and drop triggers, replace a guard function, drop and truncate tables, write the migration history, and owned the schema.
* After (`after-tests.txt`; final audit change re-run separately): backend `verify` 1439 tests, 0 failures, 5 skipped (Tesseract not installed on this machine); database tests 15/15; `deploy/tests` 170; `tools/tests` 182.
* GitHub CI, `workflow_dispatch`, run `46072699191` on `ad7de40`: all six gates green; develop guard and deploy **skipped**. One earlier run failed in the new rehearsal step because the step seeded data without `search_path = lawdesk`; fixed in `ad7de40`. 
* **CI on the last code commit `0e728e4` (run `46075225766`): one gate failed.** Backend (embedded and image), frontend, secret scan and the staging stack (including the upgrade rehearsal, with the new audit check) passed. **"Repository policy" failed: the subject of `0e728e4` is 73 characters, the limit is 72** (author's error). The job stopped there, so its workflow and shell lint steps did not run in that run; both were run locally and are clean. Because the policy checks every commit of a pull request, **the PR cannot pass it until that subject is reworded**, which needs a rewrite of the branch tip (force push) that was not performed: it awaits the owner's permission. The code does not change.
* One of the failing-first assertions was wrong and was corrected, not the product: PostgreSQL always lets a role set default privileges for objects it creates itself (it can create none). The test now attacks the owner's defaults.

## Previous-release compatibility (real jars)
`tools/db-split/compat_drill.py`, output `compat-drill-output.txt`, `compat-drill-report.json`: **62/62**. Previous jar = built from `92822792`. Own PostgreSQL 18.6 clusters, scram authentication, the real SQL files through `psql` 18.7, synthetic data created through the API.

| Phase | Result |
|---|---|
| today's installation, previous jar | seeded: ~390 rows in 91 tables |
| isolated restored copy (`pg_dump` → second cluster → transfer + migrate) | every table's row count and content digest unchanged; audit passes |
| in-place transfer with the previous jar serving | transfer ≈ 0.5 s; no failed request out of ~170 (one client, reads) |
| previous jar restarted on the split schema (Flyway on) | 28 API calls incl. login, permissions, writes, document purge, reminder and follow-up runs; no ERROR and no permission error in the log |
| this jar, Flyway off | same, passes |
| rollback with the old `app-role.sql`, then next deployment | works; `TEMPORARY` is re-granted by the old script and withdrawn by the next deployment |
| dump of the split database → empty cluster | 91 tables identical; audit passes; application works |
| **documented limit** | an old release with a pending DDL migration does **not** start on the split schema; nothing changes. `revert-ownership.sql` lets it run; the next deployment splits again |

## Deployment rehearsal on real images (CI run 46072699191)
Previous release deployed with its own scripts on a fresh volume → 500 synthetic cases → this release: schema owner `lawdesk_app → lawdesk_owner`, **91 tables unchanged**, one migration applied (v900), audit passed → `rollback.sh` to the previous release, healthy on the split schema → this release again.

**Interruption:** about **25 s** on the CI runner from recreating the `db` container (a new secret is mounted) to a passing smoke test. A deployment that does not change `db` has no database restart. Timing on the shared host was not measured.

## QA-R6-M0-021 and the other findings
* Counter item — **addressed, partly**: the runtime can no longer rewind, jump or delete the counter. It can still advance it step by step, or insert a case carrying a generated-format number with direct SQL (the API refuses such supplied numbers).
* Other 021 items (pending `BOOTSTRAP_OPEN`, one operator cancelling another's request, statistics row counts) concern objects that do not exist yet: **carried, not fixed**.
* **QA-R6-M0-017, 018, 019, 020: not fixed**; carried to M4 (017), before any completion path (018), M5 (019, 020).

## Not done — needed before UAT
1. Rehearsal on a **restored copy of a real UAT backup** (handoff criterion 6). Only synthetic data here.
2. **Read-only inspection of actual UAT ownership and role settings** (criterion 7): `inspect-ownership.sql`, by the owner or DevOps.
3. Owner approval of the window and of the limit for old releases that need DDL.
4. PostgreSQL 18.x matrix: only 18.6 servers were used.
5. `T-DB-12` after a **restore** is a documented manual step (`audit.sql`); `deploy.sh` has no restore path of its own.
6. `T-DB-5…7`, `T-DB-14` concern privileged R6 functions, which do not exist: the audit only forbids any `SECURITY DEFINER` function executable by the runtime.

## Residual risks
* A compromised runtime role can still change **its own password** (an outage until the next deployment resets it) and its own settings; the audit now reports stored settings but does not repair them. It can read and write every row, as before.
* The transfer takes locks on every object; under heavy write load it may hit the 20 s lock timeout, in which case nothing changes and the deployment fails. Load tested only lightly.
* `ALTER ROLE … PASSWORD` appears in the server log if statement logging is switched on (as for the existing script).
* If anyone has stored a setting on the `lawdesk` roles on the host, the audit will fail the first deployment: inspect first.
* A later milestone that withdraws rights on a table relies on `ownership.sql` granting on all objects only during the transfer; keep it so.
* `tools/restore-drill` still models the layout from before the split; it was not updated.
