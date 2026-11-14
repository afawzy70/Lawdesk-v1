# LD-DOC-r5 — integrated validation of tracks A, B and C

Branch `feature/LD-DOC-r5-screens`. Nothing here is QA acceptance: it is the developer's evidence for the independent QA by Codex.
Not merged into `develop`, not deployed to UAT, no destructive migration run anywhere.

## What the branch contains

| Track | Content | Branch merged from |
|---|---|---|
| A | `/admin/lists` (`LIST_ADMIN`, ADMIN only): 58 catalogued lists, labels/order/deactivation applied on every screen; classes in `docs/design/LD-DOC-13-admin-lists.md`; migrations V860, V861 | `feature/LD-DOC-r5-a-lists` (5eb0af3) |
| B | hearings schedule table in `/calendar` and on the dashboard, `/hearings` reduced to results and follow-up, `/deadlines` register removed (old URL redirects to `/calendar`), optional next hearing at registration | `feature/LD-DOC-r5-b-schedule` (9cf699b) |
| C | registration: chamber under the stage, per-side party sections, «الطلبات»; case view: status field removed («متداولة» banner), lawyers/agents section removed, administrative bodies + information requests merged, grants and confidentiality moved to `/admin/case-access`; migrations V880 (rename only), V881 | `feature/LD-DOC-r5-c-case-screens` (9b6f414) |

Integration-only changes: conflict resolution in `IntakeRequest.java`, `api.ts`, `CaseCreatePage.tsx`, `IntakePartiesSection.tsx`, `HearingsPage.tsx`, `DeadlinesPage.tsx` (kept both sides; the deleted page stays deleted); the new hearings table reads the catalogue; V880 changed from a drop to a rename (owner decision); the audit assertion of `CaseVisibilityApiTest` scoped to the tested case; tests, journeys and documentation listed below.

## Test results

See `RESULTS.md` (tested SHA `a2a76e4`: backend 1456 run, 0 failures, 5 OCR tests skipped for lack of Tesseract; frontend 1122/1122; tsc, eslint and the register check clean).

## Baseline comparison — `CaseVisibilityApiTest.aGrantGivesViewOrContributeUntilItIsWithdrawn`

* Baseline `18822a8` (clean worktree, `CaseBoundEndpointsAccessTest` then `CaseVisibilityApiTest` in one JVM, `-Dsurefire.runOrder=reversealphabetical`): fails, expected 2 audit events, got 6. Same command on stream A's branch: the same failure with the same numbers. Opposite order (`alphabetical`) on the baseline: passes. So the defect pre-exists and is a test isolation defect: the test counted every `CASE_GRANT_*` audit event of the database, and `CaseBoundEndpointsAccessTest` leaves four.
* Fix: the assertion counts only the events of the tested case (`target_id`); a regression test (`grantEventsOfAnotherCaseDoNotChangeTheCountOfThisCase`) grants and withdraws on another case and proves the first case's count stays 0 while the events themselves are all still recorded.
* After the fix both orders pass (`reversealphabetical` and `alphabetical`, rc=0, `CaseVisibilityApiTest` 11/11, `CaseBoundEndpointsAccessTest` 3/3).

## Local end-to-end journeys

`tools/journeys/local_stack.py`: the real application jar on its own throw-away PostgreSQL 18 cluster (only its cluster, loopback), the real `deploy/qa/qa_accounts.py provision | receive | verify` creates and verifies `qa.admin` (ADMIN), `qa.manager` (LEGAL_MANAGER), `qa.staff` (LEGAL_STAFF), `qa.viewer` (QA_READ_ONLY); three synthetic courts are inserted (UAT has courts). Results (JSON per journey in this folder, `*-local.json`; `qa-accounts-verify.txt`):

| Journey | Steps passed |
|---|---|
| `r5_integrated_journey.py` (new: A, B, C with the four roles) | 52 / 52 |
| wp1 access 32, wp1c parties/requests 18, wp1d stages 8 | all pass |
| wp2 59, wp3 74, wp4 57, wp5 65, wp6 67, wp7 51, wp8 48, wp9 36, wp10 31, connected 23 | all pass |

Total 621 steps, 0 failures. Not run: the OCR journey (no Tesseract in the local stack). Not run against UAT (not deployed).
Journey changes needed by the new behaviour: `wp6_journey.py` now accepts a refused calendar (403) for a role without `CALENDAR_VIEW` (a freshly provisioned `qa.viewer` has no such permission; the calendar controller is unchanged from `develop`).

What `r5_integrated_journey.py` proves (with the roles): administrator-only list administration (403 for manager, staff, viewer), label edit of a system value keeping its code, no new option in a logic-bound list, a new option in a descriptive list, duplicate refused, history kept, the edited label served by the catalogue the screens read; a next hearing given at registration appears in the hearings schedule and calendar for the specialist and manager and not for an unrelated reader; requests: 20 per side accepted, the 21st refused with the stored items intact, edit and removal visible after a fresh read; a reader outside the case and an unknown id get identical answers (status and body) for the case, its requests, a change of its requests and its grants; a read-only user with a view grant reads but cannot change requests (403), cannot link an administrative body, cannot raise an information request; grants and confidentiality endpoints used by `/admin/case-access`; the lawyer/agent endpoints are gone.

Catalogue labels in the hearings table: `HearingsSchedule.test.tsx` (new tests): the edited status and purpose labels appear in the table rows and in the status filter; without a catalogue the built-in labels appear. The API side (the catalogue serves the edited label to every signed-in user) is checked in the journey above.

## Migration and historical data

* Ordering: no duplicate version numbers; V860, V861 (A), V880, V881 (C) apply after V845.
* `tools/restore-drill/counsel_retire_drill.py` on the final jar (`--target 861`): 12 / 12 checks pass (`docs/evidence/DOC-R5-COUNSEL-REMOVAL/drill-result.json`): 600 synthetic counsel rows kept by V880 (renamed table, same digest), backup verified by a restore into a side database, nothing else changed, second start-up applies no migration, recovery by rename and by backup.
* Historical codes: V860/V861 seed the catalogue from the existing codes and drop only CHECK constraints (replaced by format/catalogue checks); no stored code is rewritten (`ListAdminApiTest`, `CatalogListsApiTest`, `ImportanceListApiTest`).
* **V880 renames `case_party_counsel` to `case_party_counsel_retired`; nothing is deleted.** Final deletion is not authorised and not in this tree.
* **Before any UAT deployment (mandatory, not done):** the real number of counsel rows on UAT, a verified backup, a recovery check — `docs/operations/COUNSEL-RETIREMENT.md`.

## Dependencies on `case_party_counsel` (review)

Only `V602` (creates), `V880` (renames), two tests that assert the old name is gone and no function/view mentions it, the drill, and documentation. No query, report, scheduled job, SQL function, view, trigger or deployment script reads it. The timeline labels `COUNSEL_ADDED`/`COUNSEL_REMOVED` are kept so old history reads.

## Limitations

* Journeys ran on a local stack with synthetic data, not on UAT; no browser run (jsdom tests only for the screens); no OCR journey; Safari/iOS not covered.
* The label-only classification of most lists rests on code reading (see `LD-DOC-13`); only three lists accept new options.
* Courts are edited on `/courts` (linked from the lists page), not inline.
* The register (`docs/requirements`) was not changed for this round: the new behaviour comes from the owner's instructions, not from a requirement row.
* Dashboard timing (above 0.5 s for manager/member dashboards) is unchanged and open.
