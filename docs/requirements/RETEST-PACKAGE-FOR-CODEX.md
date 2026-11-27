# Retest package for Codex (independent QA)

Status of this package: **nothing is QA_ACCEPTED and the project is not complete.** The register
(`lawdesk-comprehensive-requirements-matrix.md`) carries three separate columns: IMPLEMENTED, VERIFIED_BY_DEVELOPER
(a named test or journey step on a stated SHA) and QA_ACCEPTED (always empty; only your independent review may fill it).
Counts are in `TESTED-STATUS-SUMMARY.md`. Do not take the developer evidence as acceptance: rerun it.

## 1. What is published

- UAT: https://lawdesk.167-86-90-174.sslip.io
- **Application release this package describes: `18822a83bff8e2a38b83db4370674a9f69919d81`** (develop after PR #111). On
  2026-11-10 `GET /api/v1/release` (qa.manager session) returned exactly this SHA, and CI plus the staging deploy of it were
  green. Later merges that change only documentation or tests (this package, PR #112) move the SHA reported by the endpoint
  without changing application behaviour; record the release you observe before and after each run, and treat the SHA
  found there as the one that counts.
- Developer journeys: all 14 ran against `18822a8` and passed (access 32, parties 16, stages 8, WP2 59, WP3 follow-up 74,
  WP4 57, WP5 65, WP6 67, WP7 51, WP8 48, WP9 36, WP10 31, OCR 44, connected 23 steps; 0 failures). Evidence:
  `docs/evidence/DOC-RETEST/*-18822a8.json`, `SUMMARY-18822a8.txt`. One step of the parties journey (`wp1c`) had to be
  corrected after PR #105: a reader outside the case now gets 404 for `/parties/{id}/cases` instead of an empty list (that
  is the intended behaviour; the old assertion was stale). Earlier sweeps (`*-cac8d73.json`, `*-820c5c0.json`) are history,
  not evidence for this release.
- Case-page HTTP 500s (QA-RT-011): developer re-check on `18822a8`: 0 5xx in 3 rounds of 84 simultaneous requests over
  HTTP/2 and 3 over HTTP/1.1 with curl, and in 2 Chromium runs of 4 case-page loads each (before the fix on `03ff08e`: 58 and
  11 failures of 84, and 76 of 154 in Chromium). Details and limits: `docs/evidence/DOC-RETEST/CASE-PAGE-18822a8.md`. This is
  not your 32-row matrix and it counted only 5xx.
- **qa.admin is still not provisioned.** `qa_accounts.py verify` on 2026-11-10 passes manager, staff and viewer and refuses
  qa.admin. The fix needs the operator who holds the UAT administrator password (host user `devops`); the complete command,
  the effect (it rotates all four QA credentials) and the coordination note are in `docs/operations/QA-ACCOUNTS.md`. It has
  not been run, so the seven-role tests that need qa.admin remain blocked.
- The Kuwait-day boundary (21:00-24:00 UTC = 00:00-03:00 Kuwait) is covered by **pinned-clock** tests (backend
  `KuwaitBoundaryInstantsTest`, `KuwaitBoundaryDeadlinesTest`, `KuwaitBoundaryReportsTest`; frontend `kuwaitBoundary.test.ts`)
  that prove the logic at five instants. The **live** check (`tools/journeys/kuwait_live_check.py`) was run only outside the
  window (03:40 UTC), where UTC and Kuwait dates coincide, so it cannot distinguish them; the live window was NOT observed.

## 2. Pull requests merged this round (verified with `gh pr view N --json mergeCommit,title`)

| PR | Merge commit | Title |
|---|---|---|
| #83 | 867187c | docs(LD-DOC): final Arabic handbook for the requirements programme |
| #85 | 6b47e95 | docs(LD-DOC): new decisions for review; correct 650 count |
| #86 | 3937a50 | feat(gaps): close partial requirement rows (round 2) |
| #87 | fb4d922 | test(LD-DOC): restore drill, restricted-role and task-status tests |
| #88 | 4d83267 | LD-DOC r2: UI quality, axe fixes and cross-engine e2e tooling |
| #89 | 03dae13 | feat(DOC-OCR): real Arabic OCR in the app image, measured |
| #91 | 1b502c1 | LD-DOC r2: DOC-UX cross-engine evidence (312 rows, 0 findings) |
| #92 | c931e10 | perf(LD-058): stored search documents, trigram filter, report speed (round 2) |
| #93 | 11adb95 | test(LD-DOC): OCR journey on UAT with findings |
| #94 | 4743bd4 | fix(journeys): stale journeys match the product; UAT evidence |
| #95 | 113e06e | fix(LD-058): OCR and file text searchable in unified search again |
| #96 | 68c4b00 | fix(time): Kuwait day in validation; empty pending modules |
| #97 | cac8d73 | fix(security): case access before record version |
| #98 | 3522775 | docs(LD-DOC): register r3 and this package |
| #99 | 5d46087 | Kuwait boundary tests, tablet/WebKit(WPE) matrix, pre-V800 restore |
| #100 | f9dd8d9 | docs: open decisions r3 and exact QA-account procedure |
| #101 | 820c5c0 | performance round 3 (V845; no stored snapshots) |
| #102 | aea33fa | records-only case brief and the defence similar-judgments picker |
| #103 | 514c481 | access walk: the 10 BODY_NOT_BUILT handlers built, surfaces walk |
| #105 | 03ff08e | party directory scope, audit case id, assistant queries, two 500s |
| #106 | 210db26 | docs: Asia/Kuwait recorded as owner-approved (QA-RT-006) |
| #108 | 54a3f62 | bell accessible name (QA-RT-008) |
| #109 | 70d65c0 | Word source conformance record |
| #110 | 48499b2 | wp1c journey: 404 for a party outside the case |
| #111 | 18822a8 | case-page 500s: account loading held a pooled connection (QA-RT-011) |
| #112 | pending | V845 coverage test and evidence at 18822a8 |
| #107 | **not merged** | OCR alternate readings (QA-RT-001): under review, nothing here depends on it |

## 3. How to run the journeys

All journeys are real HTTP against the deployed application, use only synthetic records (marker `SYNTH-DOC`), and never edit
existing cases. They read QA credentials from the owner-only directory named by `LAWDESK_QA_DIR` (default
`/srv/ai-workspaces/secrets/lawdesk-qa`) and never print them. Base URL: `LAWDESK_BASE`.

```
python3 tools/journeys/<name>_journey.py [evidence.json]
```

Names: `wp1_access`, `wp1c_parties`, `wp1d_stages`, `wp2`, `wp3_followup`, `wp4`, `wp5`, `wp6`, `wp7`, `wp8`, `wp9`, `wp10`,
`connected`, `ocr` (files are `tools/journeys/<name>_journey.py`, for example `wp1_access_journey.py`). A run ends with a
pass/fail count and writes the evidence JSON (step list and the deployed release). Compare your release with the one in
the committed file.

QA account situation: `qa.manager`, `qa.staff` and `qa.viewer` sign in. **`qa.admin` is broken**: it was modified after
hand-over (roles changed, password change forced), so it answers 401. It stays broken until the owner runs the provisioning
command of `deploy/qa/qa_accounts.py` (`provision --base-url <UAT>` with the devops administrator, then `verify`). The
journeys therefore use `qa.manager` where an administrator is not strictly required. Any step that needs the admin screens
through the UI cannot be run by you until then (see `docs/evidence/DOC-WP0/qa-admin-401.md`). No credential is printed or
stored in this package.

## 4. Test steps per area

For each area: run the named journey, then try to break it by hand with the accounts above.

1. **Access, scopes, confidentiality, grants, revocation** (`wp1_access`, `wp1c_parties`; backend `CaseVisibilityApiTest`,
   `CaseMembershipAccessTest`, `CaseBoundEndpointsAccessTest`, `SessionRevocationTest`). Check: a viewer without scope does
   not see a case in lists, search or any case-bound endpoint (expect 404, not 403 with detail); restricting a case hides it
   from non-members; a grant needs a reason, gives read only, cannot be self-issued, and after withdrawal the case vanishes;
   the civil number of a confidential party shows only the last three characters to a reader without `PARTY_ID_VIEW`; the
   audit trail records each grant. Also try a wrong record version on a case you cannot see: the answer must not reveal
   that the record exists (PR #97).
2. **Roles screen and direct API denial** (frontend `RolesAudit.test.tsx`; backend `RoleAdministrationTest`,
   `RolePermissionsTakeEffectTest`, `EndpointAuthorizationTest`). Needs `qa.admin` after provisioning. Change a role's
   permissions in the screen, then call the matching API endpoint directly with that role's session: it must be refused even
   if the screen hides the button.
3. **Search** (`wp9` search steps, `wp5` archive search; `UnifiedSearchApiTest`, `StoredSearchDocumentsTest`). Spelling
   variants (ة/ه, أ/ا, Arabic-Indic digits), snippet shows matching words only, no hit for a case you cannot see, no mention
   of it in the answer.
4. **OCR** (`ocr` journey with the fixtures in `tools/journeys/ocr_fixtures`: `ocr-clear-ar.png`, `ocr-scan-skew.jpg`,
   `ocr-mixed.png`, ground truth in `manifest.json`). Upload each as a case document, wait for OCR_DONE, search by words of
   the text in the archive and in the unified search, and confirm a user outside the case cannot find or read the text.
5. **Performance** (`docs/evidence/DOC-PERF`). `README.md` states the conditions and where the 0.5 s bar comes from;
   `baseline.md` and `after.md` hold the tables; `*.raw.csv` the raw runs. Read the table column `p95` (sequential, 15 runs,
   nearest rank) against the target column. Reproduce: `cd backend && ./mvnw -o test -Dtest=Wp8PerformanceHarness
   -Dlawdesk.perf=true -Dlawdesk.perf.sha=<sha> -Dlawdesk.perf.out=../docs/evidence/DOC-PERF/run.md` (throwaway embedded
   PostgreSQL with 50,000 synthetic cases; never UAT). The harness is flag-gated, so it does not run in CI.
6. **Restore drill, and the old-backup case** (`tools/restore-drill/restore_drill.py`, evidence `docs/evidence/DOC-RESTORE`). It starts its own
   PostgreSQL 18 cluster, migrates, loads synthetic data, dumps, restores into an empty database and compares row hashes,
   stored files, grants and API answers; it refuses anything that is not its own cluster. Needs `--workdir`, `--server-bin`,
   `--client-bin`, `--jar` (see `fetch_pg_tools.sh`). The passing run is `run3-final.log` (15 of 15 checks); runs 1 and 2
   are kept because they found a real restore defect (fixed by V800). For backups taken before V800, which V800 does not repair
   retroactively: `tools/restore-drill/restore_old_backup.py`, evidence `docs/evidence/DOC-R3-RESTORE-OLD` (21 of 21 checks) and
   the operator procedure `docs/operations/RESTORE-OLD-BACKUPS.md` (`pg_restore -f`, one `sed` that adds `SET search_path` to the
   `library_trigrams` header, `psql -v ON_ERROR_STOP=1` into an empty database). **Limit: the source is a SIMULATED old backup
   (the app migrated to V781 in a throw-away cluster), not a historical archived backup.** Your own simulation reached the same
   repair independently. Not done: a real historical backup, a dump taken after V845, restore on UAT's real volume.
7. **Cross-browser** (`frontend/e2e-wp`, evidence `docs/evidence/DOC-UX`). `smoke.mjs` covers 39 screens x Chromium and
   Firefox x Arabic/English x 390/1366 px (312 rows) with axe on Chromium; `journeys.mjs` has 80 interaction steps. It runs
   only against a local throwaway instance (see `frontend/e2e-wp/README.md`); it refuses non-local URLs.
8. **Connected system** (`connected` journey; `ConnectedCaseApiTest`): one case with claim, defence, hearing, judgment,
   deadline, task, note and risk appears in summary, timeline, calendar, search, inventory and dashboard; a change shows in
   all; an outsider sees nothing.
9. **Other areas** have the journeys named in the register: parties and stages (`wp1c`, `wp1d`), claims, defences, evidence
   (`wp2`), hearings, links, information requests, risk, summary (`wp3_followup`), deadlines (`wp4`), enforcement, archive,
   memos (`wp5`), calendar, tasks, notifications (`wp6`), library (`wp7`), indicators and reports (`wp8`), settlements,
   directives, templates, unified search (`wp9`), assistant status and test-provider behaviour (`wp10`).

## 5. Known limits (stated, not hidden)

- **OCR (QA-RT-001) is open and in work.** The exact terms «الكويتي» and `LD-2026/045` still fail on the deployed engine; the
  fix under review (PR #107) is NOT merged and nothing in this package depends on it. Do not treat OCR as improved until a
  release containing it is published and you retest. Original note: OCR was measured only on a synthetic clean corpus (invented sentences, one typeface). On UAT release `1b502c1` the
  deployed engine **missed one word** on the clear 200 dpi page and **split a reference** (`LD-2026/045`) on the mixed page;
  the local run (Tesseract 5.3.4) had scored the clear page 1.000, so that figure does not carry over to the deployed image
  (5.5.2). Real documents, handwriting and photos are unmeasured. See `docs/evidence/DOC-OCR/UAT-RUN.md`.
- **Performance targets are still missed** at 50,000 cases (sequential p95, loaded host, `docs/evidence/DOC-R3-PERF`): after
  #101, 8 of 131 requests exceed 0.5 s (manager dashboard about 1.9 s, indicator figures about 1.2 s, member 0.90/0.88 s,
  no-scope 0.76/0.58 s); no gain can be claimed under 20 parallel clients; registering a case costs about 38 ms with the
  search columns. The manager case inventory (0.43 s) and the PERFORMANCE report (0.48 s) are now under 0.5 s. Search and list
  were fixed. The 0.5 s bar has no source in the requirements text; its only origin is `docs/design/LD-DOC-03-data-model.md`
  D7 (a proposed limit).
- **Browsers.** The final matrix (`docs/evidence/DOC-R3-TIME-BROWSERS/results-final-*.json`) has 936 rows: Chromium, Firefox
  157.0 and WebKit 27.2 (Playwright's Linux **WPE build 2370**, run rootless with a private library prefix and software EGL)
  x ar/en x 390/768/1024/1366 px x 39 screens, 0 findings, axe 0 on every row. WebKit here is **not Safari or iOS**; the
  official Playwright container command is in `frontend/e2e-wp/README.md` and has not been executed. The matrix covers the
  layout and accessibility of those screens with the local test data; it does not cover the 650 requirements and does not prove
  that every panel's data is complete. The tablet runs found and fixed real defects (mid-word breaks, side rail width, table
  columns), so earlier "0 findings" at 390/1366 did not include these checks.
- **Access walk.** After PR #103 `CaseBoundEndpointsAccessTest` has valid bodies for the 10 former `BODY_NOT_BUILT`
  handlers (and 3 more that became reachable: 13 held to "2xx for the member, 404/403 for an outsider with every permission"),
  seeds every table (the old seed swallowed insert errors and hid whole families of handlers), fails on any unseeded table,
  unresolved handler or 400, and compares an existing record in an invisible case with a random id. `CaseBoundSurfacesAccessTest`
  covers documents, text, search, reports, CSV/Excel/PDF exports, audit and the assistant (test provider) with one secret case.
  Not covered: a surface added later fails the route-inventory test until proven; the walk is MockMvc, not UAT.
- **Fixed after your retest findings** (`DEFECTS.md`): party directory and party cases hide parties that exist only on cases
  the reader cannot see (404); audit `targetId` is masked for invisible cases; `GET /assistant/queries` with an invisible case
  answers 404; unlinking a party with counsel answers 409; document text/reprocess without a stored version answer 404/422.
  Remaining: a party with no case at all is visible to every CASE_VIEW holder (needed for linking); a party whose only counsel
  rows are removed marks still cannot be unlinked (a migration would be needed).
- **The AI assistant is disabled**: no provider is approved (D8). Only a deterministic non-production provider is tested.
- **No legal deadline period or holiday has been invented.** The calculator is tested on synthetic rules only; official
  rules and holidays must be entered and approved by the specialists (D6).
- **Asia/Kuwait as the day** is recorded as owner-approved (item 15 of `LD-DOC-11-gap-decisions-addendum.md`; QA-RT-006).
  Still awaiting approval: closing a case that has open items only with an explicit acknowledgement (item 16) and the
  missing-evidence notification (item 17). The other new assumptions are in
  `LD-DOC-11-new-decisions-for-review.md` (items 1-14, among them archive limits, reminder lead times, settlement and
  enforcement states).
- Verification in the register is **feature level**: a named test or journey step creates and reads the record or exercises
  the rule the row belongs to; it does not claim each field was asserted separately.
- No backup-encryption, field-encryption or external penetration test exists; key management awaits D7.

## 6. What to deliver back

For each register row: confirm or reject the developer evidence, and fill QA_ACCEPTED yourself. Until you do, every row
stays at QA_ACCEPTED empty.

## 7. Handoff by QA defect (developer status; closure is yours)

| QA id | Cause (developer view) | Fix | Regression test | Developer verification | Ready to retest |
|---|---|---|---|---|---|
| RT-001 OCR exact terms | Otsu threshold removed the dots of «الكويتي»; the sparse reading of `LD-2026/045` was discarded by the line merge | PR #107, **not merged**, under review | in the PR | none yet | **No** |
| RT-002 qa.admin | account refused (401); needs the administrator | `docs/operations/QA-ACCOUNTS.md` (not run) | `verify` | verify still refuses qa.admin | **No: needs devops** |
| RT-003 pre-V800 restore | `library_trigrams` without `search_path`; restore stops at `library_judgment` | procedure + tool, PR #99 | `restore_old_backup.py` 21/21 on a simulated old dump | isolated only | Yes, with the stated limit |
| RT-004 unresolved handlers | seed swallowed errors | PR #103 | `CaseBoundEndpointsAccessTest` | CI green | Yes |
| RT-005 provider-blocked rows | retrieval was classed with generation | PR #102 (records-only brief), register r4 | `CaseBriefApiTest` | UAT 18822a8: brief 200/404 checked | Yes (rows stay PARTIAL) |
| RT-006 Kuwait record | doc said pending | PR #106 | n/a | doc | Yes |
| RT-007 case list 500 ms | query cost | PR #101 | `IndicatorApiTest`, equivalence tests | `DOC-R3-PERF`; dashboard still over | Partly (list under bar; dashboards not) |
| RT-008 bell name | badge `99+` vs name `123` | PR #108 | `Notifications.test.tsx` | axe on UAT: 0 violations on /cases (en, 1366 px) | Yes: rerun axe on all widths/locales |
| RT-009 unlink 500 | counsel FK | PR #105 | `PartyDirectoryAndDefectsTest` | CI | Yes |
| RT-010 party directory | scope | PR #105 | same | wp1c 16/16 on UAT | Yes |
| RT-011 case page 500 | account load held a connection while asking for a second; pool 10, timeout 3 s | PR #111 | `CasePageConcurrencyTest`, `...Http2Test`, `PoolExhaustionTest` | UAT 18822a8: 0 5xx (see section 1) | **Yes: rerun your 32-row matrix twice** |
