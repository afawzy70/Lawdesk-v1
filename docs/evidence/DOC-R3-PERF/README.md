# DOC-R3-PERF: round-3 performance (LD-058)

## 1. The limits used and where they come from

- **500 ms** appears only in `docs/design/LD-DOC-03-data-model.md` section 5, decision D7 (status: *proposed*): "case list and search under 500 ms on 50,000 cases and 500,000 child records". Its scope is the case list and the search. It is **not** a requirement of the comprehensive requirements text and no percentile, concurrency or user mix was ever defined.
- This round does **not** generalise it. The harness still prints a "target" column of 500 ms for every row (inherited from earlier rounds) so that the numbers are comparable; for the dashboard, the indicator figures, the inventory and the report families it is only a yardstick, set by nobody. "Misses" below means "over 500 ms sequential p95 on this host", nothing more.
- No limit was set for writes or import; they are reported as measured, with and without the search columns.

## 2. Method (identical before and after)

- Synthetic data only (`tools/performance/wp8/seed-wp8.sql`, 50,000 cases, 584,999 child rows), loaded once into a throw-away database of my own PostgreSQL 18.6 cluster (port 55620, scratch under the session scratchpad, never UAT's database). Every measurement ran on a fresh `CREATE DATABASE ... TEMPLATE` copy of that seeded database, so before and after use the same corpus. Flyway runs on the copy, so new migrations are applied on top of the data as in an upgrade.
- Harness: `Wp8PerformanceHarness` through MockMvc with the real access functions (`-Dlawdesk.perf.db=<copy>`, added to `PostgresTestSupport`, skips seeding). 15 sequential runs after one warm-up (p50, p95 nearest-rank); concurrency 8 clients x 5 requests (all endpoints) and 20 clients x 5 requests (a subset of heavy endpoints). Users: department-wide manager, member (own cases), user with no scope, manager with SEARCH_ALL (search only).
- Writes: `tools/performance/r3/WriteCost.java`, SQL level, one transaction per operation (300 per operation, 20 warm-up), plus 5,000 single-case registrations (case, primary involvement, proceeding, claim) as the "bulk import": **the application has no bulk-import endpoint**, so this is the cost of 5,000 registrations, not an HTTP import. "Without" disables every `search_*` trigger and drops the 17 search-vector, trigram and party search indexes.
- **Host load.** The host is shared and was busy for the whole task (other agents' builds and test runs). Load average (1 min, 8 CPUs): baseline 18 -> 14; after 11.7 -> 14.9; 20-client baseline 12.0 -> 11.6; 20-client after 9.0 -> 12.8; write runs 9 to 16. Absolute numbers are therefore pessimistic and noisy, and runs were not interleaved. Where a difference is within that noise it is said so.
- Code versions: baseline = `develop` 3522775 (application code), after = branch head. The read results in `after.md` were taken at 20bd23f; later commits changed only V845 (write triggers) and tests, which do not touch read paths.

## 3. What changed

| change | why | evidence |
|---|---|---|
| Dashboard reads the case counts and the overdue-task count from the indicator figures it already computes instead of running those statements a second time | 2 statements fewer (24 -> 22). The hearing count is left on its own query: the report calendar and the hearing port use different clocks in `DashboardReportApiTest`, so reusing it would change behaviour under a pinned date | `DashboardReportApiTest`, `IndicatorApiTest` |
| Indicators answered by already-read counts skip their savepoint | 4 indicators x 2 round trips | same |
| Report families `CASES_BY_MEMBER` and `PERFORMANCE`: member names in one statement (`UserDirectory.displayNames`) instead of one per member | 197 and 201 statements -> 2 and 6 | `FamilyApiTest` |
| "Latest risk assessment" written as `not exists (a later version)` instead of `= (select max(version_no) ...)` in `RiskReportQueries` and `RiskService.high` | the correlated max cost 1.6 s as a plan in isolation, 70 ms as an anti-join; same rows (unique `case_id, version_no`) | `RiskAssessmentApiTest`, `IndicatorApiTest` |
| Department-wide users: `confidentiality <> 'RESTRICTED'` is tested first in the visibility predicate (`CaseAccessPolicy.fastVisible`) | the common row is decided by one comparison instead of three member comparisons; same set of cases, the original expression is still the fallback | `CaseVisibilityPredicateEquivalenceTest`, `CaseVisibilityApiTest`, new test below |
| **V845**: the stored search document of a case is refreshed on UPDATE only when a searchable column changed (case: reference, automated number, title, subject, facts, chamber, primary/alternate member; proceeding: number, year; involvement: unit names, unit, contact, removal), and the refresh functions are not called at all when nothing changed | every update, even of status or amounts, rebuilt the document (about 24 ms of a 27 ms update) | `StoredSearchDocumentsTest` (extended: each column on its own, plus a write that changes none, then compared with a full recomputation) |

Not done, on purpose: no stored snapshot or materialised figure of any kind (owner decision). A covering index on `legal_case` for the visibility columns was tried and dropped: no reliable gain under this load.

## 4. Correctness checks

- New `IndicatorApiTest.figuresAndDashboardEqualTheirListsForTypeUnitGrantTaskCreatorAndAlternateScopes`: for eight users (case-type scope, unit scope, explicit grant on a RESTRICTED case, task assignee, creator, alternate member, the RESTRICTED case's member, ALL scope) every indicator figure, every group, the dashboard's total/overdue-task figures and the case list total equal the count of the list under the same filter, and the RESTRICTED case reaches only its member and its grantee. The existing test does the same for manager, member and outsider.
- Targeted run, all green: `IndicatorApiTest` (13), `DashboardReportApiTest` (13), `FamilyApiTest` (16), `CaseVisibilityPredicateEquivalenceTest`, `CaseVisibilityApiTest` (10), `RiskAssessmentApiTest` (12), `CaseInventoryReportApiTest`, `StoredSearchDocumentsTest` (6), `UnifiedSearchApiTest` (21), `OcrTextSearchTest` (2), `CaseSearchApiTest` (27), `GovernmentIntakeApiTest` (19), `CaseEditApiTest` (12), `CaseApiTest` (21), `CaseDetailsApiTest` (8), `ExportLimitApiTest` (8, timeout unchanged). The full suite is left to CI.

## 5. Reads, 50,000 cases, before -> after

Files: `baseline.md`/`after.md` (+ raw CSV, every latency), `comparison-8-clients.md` (rows with a p95 over 400 ms or the dashboard/figures), `baseline-20clients.md`/`after-20clients.md`, `comparison-20-clients.md`.

Sequential p95 over 500 ms: **25 of 131 requests before, 8 of 131 after**.

| request (sequential, ms) | p50 before | p50 after | p95 before | p95 after | 8 clients p95 before -> after |
|---|---|---|---|---|---|
| manager: dashboard (22 statements, was 24) | 1898 | 1271 | 3267 | 1888 | 4264 -> 2480 |
| manager: indicator figures (15) | 1284 | 911 | 2514 | 1206 | 2571 -> 1784 |
| manager: case inventory report | 410 | 310 | 860 | 429 | 1129 -> 705 |
| manager: report family PERFORMANCE (201 -> 6 statements) | 548 | 242 | 1088 | 482 | 1546 -> 633 |
| manager: report family CASES_BY_MEMBER (197 -> 2) | 269 | 115 | 483 | 170 | 628 -> 295 |
| manager: report family RISK_ASSESSMENT | 510 | 173 | 1129 | 232 | 1605 -> 514 |
| manager: indicator list HIGH_RISK_CASES | 329 | 184 | 445 | 299 | 854 -> 528 |
| member: dashboard | 1655 | 754 | 3163 | 900 | 5173 -> 1399 |
| member: indicator figures | 1167 | 550 | 1593 | 880 | 3364 -> 1196 |
| no scope: dashboard | 945 | 662 | 1188 | 764 | 2766 -> 1330 |

(Columns are p50 and p95 of the sequential runs; the full table with all rows is in `comparison-8-clients.md`. A few "no scope" rows in `after.md` are slower than before (e.g. indicator list TOTAL_CASES p95 78 -> 431 ms): single outliers from the loaded host in a 15-run p95, not a code effect, since that path returns zero rows; not rerun.)

**20 parallel clients** (`comparison-20-clients.md`): no improvement can be claimed. The manager's dashboard is 4.6 s p50 before and 4.1 s after, the indicator figures 3.3 s and 3.1 s; several heavier rows are worse in the second run (inventory p95 1.9 s -> 2.5 s). With 20 clients on 8 CPUs that were already at load 9 to 12 the run is CPU-bound and run-to-run noise exceeds the effect; the 8-client run is the one to read.

## 6. Writes and import (SQL level, p50 ms; load 9 to 16)

| operation | with search columns (develop) | without | with V845 |
|---|---|---|---|
| case registered (case + involvement + proceeding + claim) | 35.5 | 13.3 | 38.7 |
| case update, title changed | 27.5 | 3.3 | 21.1 |
| case update, amount only (no searchable column) | 23.9 (own run, `perf_w4`) | not run | **4.3** |
| document upload + text job queued | 11.3 | 5.9 | 17.5 (noise, p95 144) |
| OCR text stored | 8.1 | 3.9 | 10.2 |
| hearing update | 9.0 | 4.7 | 5.2 |
| task update | 5.9 | 3.5 | 4.5 |
| audit row | 3.5 | 4.0 | 4.5 |
| import of 5,000 registrations, ms per case | 57.9 | 16.3 | 52.1 |

Reading: the search columns cost roughly 2.5x on registering a case and 8x on a case update (before V845); V845 removes the avoidable part of updates (24 -> 4 ms for a write that changes no searchable text). Registration is **not** improved: each of the three statements of a registration (case, involvement, proceeding) refreshes the case's document again (about 5 to 10 ms each). The honest fix is to refresh once per transaction (a deferred queue of case ids), which needs a design decision; not done. Differences of 2 to 5 ms between the "with" and "V845" columns for other rows are within the noise of runs taken at different loads. The audit row is unaffected by the search columns, as expected.

## 7. What still misses 500 ms (sequential p95, this host, 50,000 cases)

Manager: dashboard 1.9 s, indicator figures 1.2 s, case inventory 0.43 s p95 (p50 0.31) now under, PERFORMANCE report 0.48 s (just under), plus the 8 rows in `after.md` still above 500 ms: six real ones (the dashboard and the indicator figures for the three users below) and two single outliers of a loaded host that return no rows (no-scope `indicator list CASES_ONGOING` 643 ms with p50 55 ms, and `DEFENSE_POSITIONS` 564 ms with p50 44 ms). Member: dashboard 0.9 s, indicator figures 0.88 s. No scope: dashboard 0.76 s, indicator figures 0.58 s. The cause is unchanged: 22 and 15 statements each scanning the visible set of the register (50 to 150 ms each at this load). Without a stored snapshot (excluded by the owner) the remaining levers are fewer scans of `legal_case` per request (one shared visible-id set per request, e.g. a request-scoped temporary table, which would need the same visibility rule evaluated once and an explicit equivalence test) and less host load; neither was built here.

## 8. Not measured / limits of this evidence

- No HTTP-level write timings; no real bulk-import path (none exists); no OCR engine time; no UAT or production hardware; no cold-cache run; one host, one corpus shape (synthetic, evenly spread, so selectivities differ from real data).
- Runs were not interleaved and the host load differed between them (see section 2); differences under about 30 percent should not be read as effects.
- 20-client results are inconclusive, see section 5.
- The write test for the "without" variant dropped the search indexes and disabled the triggers, so it also removes their read benefit; it measures write cost only.
