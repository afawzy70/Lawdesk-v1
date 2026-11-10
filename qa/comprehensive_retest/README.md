# Independent retest, 2026-11-10

## Scope and version control

- Isolated worktree: `/tmp/lawdesk-qa-comprehensive-20261110`, branch `qa/comprehensive-retest-20261110`, based on `origin/develop` `352277571426bd4d135ea4acb0e12489aa09fc3c`. The existing `develop` worktree was not changed.
- Application source at the first UAT run: `352277571426bd4d135ea4acb0e12489aa09fc3c` (PR #98 is documentation only after application SHA `cac8d73`). UAT advanced during this QA task to `aea33fa3d8e1f92a5b8bde5ad6f34c99e4bf1bfa` (new case brief/UI), then to `514c481c02673f29a39601a6b22694d5e0427029` (test/docs only), then `03ff08ed053402744b5160409e5e851e0565fed6` (scope and 500 fixes). Each release has its own 14 journey files, 32 browser rows, OCR probe, coverage matrix, and release observations. See `release-before.json`, `runs/run-summary.json`, `release-after.json` for the first; `release-aea-before.json`, `runs-aea33fa/run-summary.json`, `release-aea-after.json` for the second; `runs-514c481/run-summary.json`, `ocr-targeted-result-514c481.json`, `browser-uat-result-514c481.json`, `release-514-after.json` for the third; `runs-03ff08e/run-summary.json`, `browser-uat-result-03ff08e.json`, `ocr-targeted-result-03ff08e.json`, `release-03ff-after.json` for the fourth. No cross-SHA PASS is counted.
- QA test source checkpoint: `708875d5e888fd23d25983a0e5b9770c208c403d` for the case-bound test and first scripts, and `1d6f95a0cd5716dec1196f4f4e7c5f203983afda` for the version-separated journey runner. The first UAT runs occurred while their scripts were still uncommitted; the evidence preserves that fact (`qaSourceSha: uncommitted`). The second run pins the runner SHA. The 03ff journey profile is pinned to QA source `786ec0814593bb1f4f9ebae2f870ed79208c155f`; the final QA evidence commit is a separate SHA.
- UAT's release endpoint gives a SHA but no build timestamp. Observation times are recorded in UTC. Do not infer deployment time from observation time.

## Source and independent method

`docs/requirements/SOURCE.md` records the original DOCX SHA-256 `edf402d18419a10e1a9012144f74e1b19c9b261a2d487a1ec84c32083bf72d3f`; the original DOCX is not committed. The checked-in extracted text is the available source for this audit. `build_coverage.py` independently parses 650 atomic rows, checks unique and contiguous IDs across sections 00–37, and checks that each row's quoted text occurs on its cited source line. `python3 -I docs/requirements/build_matrix.py --check` also passed. This proves register structure, not semantic completeness of the DOCX extraction.

`coverage.csv` (3522775), `coverage-aea33fa.csv`, `coverage-514c481.csv` and `coverage-03ff08e.csv` (current UAT) keep implementation claim, developer evidence claim, an independent row-specific test plan, QA result, QA evidence, and tested SHA separate. `open_rows_review*.csv` reviews each of the 40 PARTIAL and 45 BLOCKED developer claims individually. Developer PARTIAL claims are `NOT_TESTED` in QA unless a separate probe proves a slice; eight provider-blocked claims with testable local retrieval are `NOT_TESTED`. Only exact assertions listed in `build_coverage.py` are PASS. A journey-level pass does not promote every field in its module. `BLOCKED` and `NOT_TESTED` never become PASS by inheritance. Current deployed-03ff counts: PASS 17, FAIL 1, BLOCKED 37, NOT_TESTED 586, PARTIAL 9. The failed row is LD-DOC-34-016 due repeated case-page HTTP 500 (`browser-uat-result-03ff08e.json`); S15 is PARTIAL after the 514 party leak was fixed (`party-directory-scope-03ff08e-result.json`). On 514, S15 was FAIL and LD-DOC-34-016 PARTIAL. The 03ff interface also has eight axe failures; tablet and Firefox/WebKit coverage remain open.

## Reproduction

Run only with synthetic QA records and the handed-over 0600 credential files. The network helper maps the known UAT hostname to its IP in this QA executor because its DNS is unavailable; it preserves the original URL and TLS verification.

```bash
cd /tmp/lawdesk-qa-comprehensive-20261110
PYTHONPATH=qa/comprehensive_retest python3 qa/comprehensive_retest/uat_probe.py qa/comprehensive_retest/release-before.json
PYTHONPATH=qa/comprehensive_retest python3 qa/comprehensive_retest/run_journeys.py
PYTHONPATH=qa/comprehensive_retest python3 qa/comprehensive_retest/ocr_targeted.py
PYTHONPATH=qa/comprehensive_retest python3 qa/comprehensive_retest/uat_probe.py qa/comprehensive_retest/release-after.json
python3 qa/comprehensive_retest/build_coverage.py
```

For `aea33fa`, set `QA_RUN_DIR=$PWD/qa/comprehensive_retest/runs-aea33fa` and `QA_SOURCE_SHA=1d6f95a0cd5716dec1196f4f4e7c5f203983afda` for `run_journeys.py`; set `QA_OCR_OUT=$PWD/qa/comprehensive_retest/ocr-targeted-result-aea33fa.json` for `ocr_targeted.py`. Generate its separate matrix with `QA_APP_SHA=aea33fa3d8e1f92a5b8bde5ad6f34c99e4bf1bfa QA_OUTPUT_TAG=-aea33fa python3 qa/comprehensive_retest/build_coverage.py` while keeping `QA_RUN_DIR` set. `brief_audit.py` probes the new provider-free endpoint on the synthetic WP2 case. For the historical `514c481` SHA, use the same commands with `QA_RUN_DIR=$PWD/qa/comprehensive_retest/runs-514c481`, `QA_OCR_OUT=$PWD/qa/comprehensive_retest/ocr-targeted-result-514c481.json`, `QA_APP_SHA=514c481c02673f29a39601a6b22694d5e0427029`, and `QA_OUTPUT_TAG=-514c481` when building coverage. `party_unlink_counsel_probe.py` creates its own marked case to reproduce and retest QA-RT-009. On `03ff08e`, set `QA_JOURNEY_PROFILE=03ff`, `QA_RUN_DIR=$PWD/qa/comprehensive_retest/runs-03ff08e`, `QA_SOURCE_SHA=786ec0814593bb1f4f9ebae2f870ed79208c155f` for `run_journeys.py`; set matching `QA_APP_SHA`, `QA_OUTPUT_TAG=-03ff08e`, and `QA_OCR_OUT` for coverage/OCR. The QA-only WP1c profile expects 404 for a party whose sole linked case is hidden. `browser_uat_smoke.mjs` saves axe selectors, not sensitive page text.

`run_journeys.py` checks the release before and after each journey. `INVALIDATED_RELEASE_CHANGED` must be rerun on a single SHA. Each 14-journey batch created only `SYNTH-DOC` cases and files. Their rerun is independent execution, but their prewritten assertions were reviewed against the atomic requirements and accepted only where exact enough.

For the direct API authorization test, use a writable Maven cache and a socket-capable isolated executor:

```bash
cd /tmp/lawdesk-qa-comprehensive-20261110/backend
JAVA_HOME=/home/aiagent/tools/jdk-21.0.12.1+1 PATH=/home/aiagent/tools/jdk-21.0.12.1+1/bin:$PATH \
  ./mvnw -o -q -Dmaven.repo.local=/tmp/lawdesk-qa-m2-20261110 test -Dtest=CaseBoundEndpointsAccessTest
```

The test now builds valid JSON or multipart data for the ten historical BODY_NOT_BUILT handlers. It found an existing QA fixture problem: lowercase hex in a court code violated the uppercase DB constraint, leaving court and dependent records unseeded. The fixture is corrected. The final isolated run passed 2 tests, 152 handler checks per walk, and `bodyNotBuilt=[]`. On source `514c481`, QA reran `CaseBoundEndpointsAccessTest,CaseBoundSurfacesAccessTest`: 10/10 tests passed, three walks of 168 handler checks each with `unseeded=[]`, `unresolved=[]`, and `bodyNotBuilt=[]`; see `access-514c481-result.json`. The older 15-handler fixture gap is resolved for this suite.

## Limits and next tests

The UAT account set covers manager, staff, and viewer; `qa.admin` returns 401 on all four releases and is not reset here. Direct role administration and all seven roles remain unverified. `admin-diagnostic.json` contains only account metadata. Heavy performance, export and restore checks ran on throwaway PostgreSQL using the first application SHA; source-specific case brief tests for the new SHA are separate. Chromium UAT browser smoke ran 32 route/locale/viewport checks on each of the four releases, with release checks; a small interactive search/language check also passed on `aea33fa`. Firefox and WebKit remain blocked by local dependencies. OCR synthetic fixtures do not represent real archive quality. The live archive settings report `tesseract`, `ara+eng`, but no engine version (`detail: null`), so the historical version 5.5.2 is not independently confirmed. No provider is approved for operational S16. No real legal deadline rules or holidays were introduced. `Asia/Kuwait` is approved in the current owner instruction; two pinned-clock validation tests passed at 00:30 Kuwait, with other workflow boundaries still open.

See `SCENARIOS-03ff08e.md`, `DEFECTS.md`, `OWNER-REPORT-ar.md`, `CLAUDE-HANDOFF.md`, `isolated-test-results.json`, `restore-current/drill-result.json`, and `PRE-V800-RECOVERY.md` for decisions, findings and outstanding checks. The QA-only `Wp8ExportRetestQa` test replays the historical large-export risk on a throwaway 50,000-case database; its result is recorded separately.

UAT deployed `03ff08e` at the later observation. QA reran 15 direct-API isolated tests on that source (`source-03ff-access-result.json`) and live party-directory/unlink probes. The 514 S15 leak is resolved for the exact live path; full seven-role coverage remains open. The 03ff browser page repeatedly returned HTTP 500 under ordinary request fan-out, while nine API endpoints succeeded sequentially; see QA-RT-011 and `run-incidents-03ff08e.json`.
