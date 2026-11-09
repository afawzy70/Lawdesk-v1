# Retest package for Codex (independent QA)

Status of this package: **nothing is QA_ACCEPTED and the project is not complete.** The register
(`lawdesk-comprehensive-requirements-matrix.md`) carries three separate columns: IMPLEMENTED, VERIFIED_BY_DEVELOPER
(a named test or journey step on a stated SHA) and QA_ACCEPTED (always empty; only your independent review may fill it).
Counts are in `TESTED-STATUS-SUMMARY.md`. Do not take the developer evidence as acceptance: rerun it.

## 1. What is published

- UAT: https://lawdesk.167-86-90-174.sslip.io
- Intended release: `cac8d7305800e11ad90e1f715fc8051bbcc55347` (develop head after PR #97). It is **not** established
  by this document that it is deployed: the deploy job was still running when this package was written. The owner or the
  session confirms the deployed SHA with `GET /api/v1/release` after login, and the SHA found there is the one that counts.
- CI on `cac8d73`: Backend (embedded PostgreSQL 18.6), Backend against postgres:18.6 image, Frontend, Staging stack,
  Repository policy, Develop guard and Secret scan were green when checked.
- The integration session reports that the journeys last ran against release `113e06e`. **No evidence file for that run is
  committed.** The committed journey evidence in `docs/evidence/DOC-*` was recorded on older releases (the release is the
  first step of every file: WP1 `8d7c847`/`30fa134`/`c898321`, WP2 `4220ccf`, WP3 follow-up `3937a50`, WP4 `82c3285`, WP5
  `2bcd2dc`, WP6 `87bf0e6`, WP7 `60f5368`, WP8 `3937a50`, WP9 `f69bd76`, WP10 `64e5416`, connected `3937a50`, OCR `1b502c1`).
  Rerun them on the deployed SHA; treat the register's VERIFIED text as a claim to check.

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
6. **Restore drill** (`tools/restore-drill/restore_drill.py`, evidence `docs/evidence/DOC-RESTORE`). It starts its own
   PostgreSQL 18 cluster, migrates, loads synthetic data, dumps, restores into an empty database and compares row hashes,
   stored files, grants and API answers; it refuses anything that is not its own cluster. Needs `--workdir`, `--server-bin`,
   `--client-bin`, `--jar` (see `fetch_pg_tools.sh`). The passing run is `run3-final.log` (15 of 15 checks); runs 1 and 2
   are kept because they found a real restore defect (fixed by V800).
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

- **OCR** was measured only on a synthetic clean corpus (invented sentences, one typeface). On UAT release `1b502c1` the
  deployed engine **missed one word** on the clear 200 dpi page and **split a reference** (`LD-2026/045`) on the mixed page;
  the local run (Tesseract 5.3.4) had scored the clear page 1.000, so that figure does not carry over to the deployed image
  (5.5.2). Real documents, handwriting and photos are unmeasured. See `docs/evidence/DOC-OCR/UAT-RUN.md`.
- **Performance targets are still missed** at 50,000 cases for the manager (sequential p95): dashboard about 1.9 s,
  indicator figures about 1.6 s, case inventory report about 0.9 s, and a dozen other requests at 0.5-0.8 s. Search and list
  were fixed. The 0.5 s bar has no source in the requirements text; its only origin is `docs/design/LD-DOC-03-data-model.md`
  D7 (a proposed limit).
- **WebKit/Safari was not run** (needs a Docker image or render node the agent user cannot use). Tablet width was not in the
  matrix. Firefox ran without axe.
- **10 case-bound handlers are on the `BODY_NOT_BUILT` list** of `CaseBoundEndpointsAccessTest` (grants, confidentiality,
  case update, status change, precedent link, document link, reply with document, counsel, party link, proceeding create):
  the reflective access test cannot build a valid body for them, so it only proves they answer 400 before the service runs.
  Their refusal of a user without access is covered, if at all, by their own tests (for example `CaseMembershipAccessTest`,
  the wp1 journey for grants); please test each by hand with a valid body.
- **The AI assistant is disabled**: no provider is approved (D8). Only a deterministic non-production provider is tested.
- **No legal deadline period or holiday has been invented.** The calculator is tested on synthetic rules only; official
  rules and holidays must be entered and approved by the specialists (D6).
- **Asia/Kuwait as the day for due dates, tasks, hearings and reports is a new assumption awaiting owner approval**
  (`docs/design/LD-DOC-11-gap-decisions-addendum.md`, item 15), as are: closing a case that has open items only with an
  explicit acknowledgement (item 16) and the missing-evidence notification (item 17). The other new assumptions are in
  `LD-DOC-11-new-decisions-for-review.md` (items 1-14, among them archive limits, reminder lead times, settlement and
  enforcement states).
- Verification in the register is **feature level**: a named test or journey step creates and reads the record or exercises
  the rule the row belongs to; it does not claim each field was asserted separately.
- No backup-encryption, field-encryption or external penetration test exists; key management awaits D7.

## 6. What to deliver back

For each register row: confirm or reject the developer evidence, and fill QA_ACCEPTED yourself. Until you do, every row
stays at QA_ACCEPTED empty.
