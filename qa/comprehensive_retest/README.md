# Independent retest, 2026-11-10

## Scope and version control

- Isolated worktree: `/tmp/lawdesk-qa-comprehensive-20261110`, branch `qa/comprehensive-retest-20261110`, based on `origin/develop` `352277571426bd4d135ea4acb0e12489aa09fc3c`. The existing `develop` worktree was not changed.
- Application source SHA: `352277571426bd4d135ea4acb0e12489aa09fc3c` (PR #98 is documentation only after application SHA `cac8d73`). UAT `GET /api/v1/release`, after QA login, reported the same SHA before, around each journey, and after the batch. See `release-before.json`, `runs/run-summary.json`, `release-after.json`.
- QA test source checkpoint: `708875d5e888fd23d25983a0e5b9770c208c403d` for the case-bound test and scripts. The first UAT runs occurred while these scripts were still uncommitted; the evidence preserves that fact (`qaSourceSha: uncommitted`). The final QA evidence commit is a separate SHA.
- UAT's release endpoint gives a SHA but no build timestamp. Observation times are recorded in UTC. Do not infer deployment time from observation time.

## Source and independent method

`docs/requirements/SOURCE.md` records the original DOCX SHA-256 `edf402d18419a10e1a9012144f74e1b19c9b261a2d487a1ec84c32083bf72d3f`; the original DOCX is not committed. The checked-in extracted text is the available source for this audit. `build_coverage.py` independently parses 650 atomic rows, checks unique and contiguous IDs across sections 00–37, and checks that each row's quoted text occurs on its cited source line. `python3 -I docs/requirements/build_matrix.py --check` also passed. This proves register structure, not semantic completeness of the DOCX extraction.

`coverage.csv` keeps implementation claim, developer evidence claim, an independent row-specific test plan, QA result, QA evidence, and tested SHA separate. `open_rows_review.csv` reviews each of the 40 PARTIAL and 45 BLOCKED claims individually. Only exact assertions listed in `build_coverage.py` are PASS. A journey-level pass does not promote every field in its module. `BLOCKED` and `NOT_TESTED` never become PASS by inheritance.

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

`run_journeys.py` checks the release before and after each journey. `INVALIDATED_RELEASE_CHANGED` must be rerun on a single SHA. The 14 existing journeys created only `SYNTH-DOC` cases and files. Their rerun is independent execution, but their prewritten assertions were reviewed against the atomic requirements and accepted only where exact enough.

For the direct API authorization test, use a writable Maven cache and a socket-capable isolated executor:

```bash
cd /tmp/lawdesk-qa-comprehensive-20261110/backend
JAVA_HOME=/home/aiagent/tools/jdk-21.0.12.1+1 PATH=/home/aiagent/tools/jdk-21.0.12.1+1/bin:$PATH \
  ./mvnw -o -q -Dmaven.repo.local=/tmp/lawdesk-qa-m2-20261110 test -Dtest=CaseBoundEndpointsAccessTest
```

The test now builds valid JSON or multipart data for the ten historical BODY_NOT_BUILT handlers. It found an existing QA fixture problem: lowercase hex in a court code violated the uppercase DB constraint, leaving court and dependent records unseeded. The fixture is corrected. The final isolated run passed 2 tests, 152 handler checks per walk, and `bodyNotBuilt=[]`. Fifteen other handlers remain unresolved because related records are not seeded; their coverage is not claimed.

## Limits and next tests

The UAT account set covers manager, staff, and viewer; `qa.admin` returns 401 and is not reset here. Direct role administration and all seven roles remain unverified. `admin-diagnostic.json` contains only account metadata. Heavy performance and restore checks run on throwaway PostgreSQL. The read-only Chromium UAT browser smoke runs 32 route/locale/viewport checks, with release checks; Firefox and WebKit remain blocked by local dependencies. OCR synthetic fixtures do not represent real archive quality. The live archive settings report `tesseract`, `ara+eng`, but no engine version (`detail: null`), so the historical version 5.5.2 is not independently confirmed. No provider is approved for operational S16. No real legal deadline rules or holidays were introduced. `Asia/Kuwait` is approved in the current owner instruction; a pinned-clock boundary test is still required.

See `SCENARIOS.md`, `DEFECTS.md`, `OWNER-REPORT-ar.md`, `CLAUDE-HANDOFF.md`, `isolated-test-results.json`, `restore-current/drill-result.json`, and `PRE-V800-RECOVERY.md` for decisions, findings and outstanding checks. The QA-only `Wp8ExportRetestQa` test replays the historical large-export risk on a throwaway 50,000-case database; its result is recorded separately.
