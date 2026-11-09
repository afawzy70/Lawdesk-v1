# DOC-PERF: round-2 performance (LD-058)

## 1. Where the 0.5 s target comes from and what it covers

- It is not a requirement of the comprehensive requirements text. Sections 34 and 36 contain no figure; LD-DOC-34-006 only says "ensure speed of search and query".
- Its only origin is `docs/design/LD-DOC-03-data-model.md` section 5 (decision D7): a **proposed limit**, "case list and search under 500 ms on 50,000 cases and 500,000 child records, measured by the phase-8 performance package".
- Scope as used by WP8 (`docs/evidence/DOC-WP8/README.md`): list and search requests; every other request was reported against the same bar, but LD-DOC-03 set no target for the dashboard, the indicator figures or the inventory report.
- No percentile, concurrency or user mix was ever defined. WP8 judged a median of 3 to 7 sequential runs, one client, through MockMvc, a department-wide manager first.
- This round states its own conditions: 50,000 cases and 584,999 child rows (`tools/performance/wp8/seed-wp8.sql`, synthetic, throwaway embedded PostgreSQL 18.6, never UAT); 15 sequential runs per request (p50, p95 as nearest rank) and 8 parallel clients x 5 requests (p50, p95); users: department-wide manager, a member (own cases), a user with no scope, and for the search a department-wide manager holding SEARCH_ALL; the target is judged on the sequential p95. With 15 runs the p95 is almost the maximum, so it is a strict reading.

## 2. Files

| file | what |
|---|---|
| `baseline.md`, `baseline.raw.csv` | develop 1d6d2a8 application code with the new harness (SHA 824ba5e). Host load average 8.8 at start and 13 at the end (other jobs ran on the shared host), so these numbers are pessimistic. |
| `after.md`, `after.raw.csv` | this branch (SHA e5e688e). Host load average 2.3 at start and 5 at the end. |

Reproduce: `cd backend && ./mvnw -o test -Dtest=Wp8PerformanceHarness -Dlawdesk.perf=true -Dlawdesk.perf.sha=<sha> -Dlawdesk.perf.out=../docs/evidence/DOC-PERF/run.md`.
Requests over the target (sequential p95, all users): baseline 56, after 15 of 131 (not every request is in both runs; the SEARCH_ALL and inventory-with-filter rows are new).

## 3. Diagnosis and changes

| request | cause (measured) | change | result neutral because |
|---|---|---|---|
| unified search, 50,000 cases | every query normalised and tokenised nine text parts of every visible case (21 s manager, 47 to 54 s with all twelve types) | V801/V802: weighted tsvectors stored when the record is written (statement/row triggers), GIN index; the access rule is applied at query time; matched-in flags and snippet are computed for the page only | `UnifiedSearchApiTest` (20 tests) unchanged and green; `StoredSearchDocumentsTest` proves incremental maintenance equals a full recomputation |
| case list / inventory text filter | `ILIKE '%x%'` over reference, title and six related tables, no index and no pg_trgm | V801: `search_case_doc.trigrams`, a hand-made trigram array (GIN, `@>`) per field of the case, used as a prefilter only when it leaves 3,000 cases or fewer; the same ILIKE then decides; related tables probed per candidate (correlated EXISTS) instead of hashed | the prefilter is a necessary condition of the ILIKE; test compares the API with an ILIKE oracle |
| case inventory report (stage distribution) | one index look-up per case (lateral) over 50,000 cases | V803: `case_position` (current proceeding of each case) kept by triggers; one hash join | the ladder in the trigger is checked against `Stage` by a test |
| dashboard and indicators | 24 and 15 statements, each re-evaluating the visibility rule over the register; three separate unit groupings | unit groups in one pass (`CaseReportQueries.unitGroups`), stage distribution as above | `IndicatorApiTest` figure == list equality for manager, member and outsider passes unchanged |

## 4. What still misses 0.5 s (sequential p95, 50,000 cases)

- Manager: dashboard 1.9 s (p50 1.6 s), indicator figures 1.6 s, case inventory report 0.9 s (p50 0.43 s), indicator list CASES_BY_UNIT and HIGH_RISK_CASES, report families CASES_BY_ORG_UNIT, CASES_BY_MEMBER, CLAIM_RESULTS, RISK_ASSESSMENT, PERFORMANCE (0.6 to 0.8 s), case list deep page 0.53 s (p50 0.23 s).
- Member and no-scope user: dashboard and indicator figures only (0.6 to 1.8 s).
- Why: the dashboard is 24 aggregate statements and the figures 15, each scanning a child table and the visible set (50 to 200 ms apiece at this size). The remaining lever is a stored snapshot of the figures. It was not built: a snapshot that must always equal the lists needs invalidation on every table the figures read, and a wrong invalidation would break that equality, which is the first rule. A safe design is a version counter bumped by deferred commit-time triggers; it needs the owner's decision. LD-DOC-03 set no target for these requests.
- Absolute times carry host noise (the host is shared); the before/after gap for search (21 s to 0.1 s) and the text filter (1.2 s to 0.2 s) is far larger.
