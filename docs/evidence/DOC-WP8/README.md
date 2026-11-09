# DOC-WP8 — main dashboard, report families, performance (story LD-058)

Branch `feature/LD-DOC-wp8-dashboard-reports`. Requirements: sections 24, 26, 27 of the comprehensive requirements text;
performance target of LD-DOC-03 section 5. No table was added; migration `V760__LD-058_visibility_cost.sql` is plan-only
(function cost, one index).

## What was built

| Requirement | Where |
|---|---|
| LD-DOC-26-001…015 (the 14 indicators, clickable) | `IndicatorService` is the one definition; `GET /api/v1/reports/indicators` (figures), `/{code}` (the list a click opens), `/{code}/export`; dashboard carries the same figures (`indicators`). Screens: `IndicatorGrid` on the dashboard, `IndicatorListPage` at `/reports/indicators/:code` (group and period in the address). |
| LD-DOC-27-001…017 (17 families) | `ReportFamilies`, `GET /api/v1/reports/families[/{code}[/export]]`; screens `/reports/families`, `/reports/families/:code`, `/reports/performance`. Each is filterable (opening period, dispute type, open/closed, member, grouping) and exports to CSV, Excel and PDF in Arabic or English. |
| LD-DOC-24-001…003 (performance, objective) | `PERFORMANCE` family: members (cases as primary/alternate, tasks due/completed/open/overdue, hearings, defence memos, directives) and units (requests, replies, overdue, incomplete, average reply days). Alphabetical, no ranking, no judgment-outcome column; tested. |
| Performance harness | `Wp8PerformanceHarness` + `tools/performance/wp8/seed-wp8.sql`; command below. |
| UAT journey | `tools/journeys/wp8_journey.py`. |

The 14 indicators have 16 codes (indicator 2 has new, circulating, concluded). Ports added to the owning modules
(`RiskReportQueries`, `CaseLinkReportQueries`, `InfoRequestReportQueries`, `PleadingReportQueries`, `DirectiveReportQueries`,
`TaskPerformanceQueries`, `LibraryReportQueries`, additions to the case, hearing, judgment and proceeding ports): the rule
of each indicator lives with its module, `reports` only chooses the call.

Definitions that needed a decision (all stated, none invented): *reserved for judgment* = a proceeding with no judgment that has a
non-cancelled hearing for pronouncement of judgment; *new / circulating / concluded* = OPEN / ACTIVE+SUSPENDED / CLOSED+ARCHIVED;
*upcoming hearings* = scheduled, today and the next six days (as on the dashboard since LD-030); *expert sessions* = the same
window, hearing kind EXPERT; *high risk* = latest assessment HIGH or VERY_HIGH; *linked* = an ACTIVE link on either side;
*overdue requests* = the information-request module's own rule; *appeal deadlines* = OPEN deadlines whose source is a judgment
(WP4 table); *judgments being enforced* = enforcement file IN_PROGRESS or PARTIALLY_EXECUTED (WP5 table).

Money (D5): the five amounts are separate columns and are never added to each other; each case, claim or assessment counts once
(a claim decided at first instance and on appeal counts at its latest result, so its awarded amounts are not added; risk uses the latest
assessment); money is a string with three decimals in JSON, and a number with format `#,##0.000` in Excel.

## Equality of dashboard figure and list

`IndicatorApiTest.everyIndicatorFigureEqualsTheTotalOfItsListForManagerStaffAndAnOutsider`: for a department-scope user, a member
and a user with no scope, every available figure equals the total of its list, every group of the two distributions equals the list it
opens, and the groups add up to the figure. `definitionsCountExactlyWhatTheyShouldAndNeverTheHiddenCase` checks each definition against
hand-built data including a restricted case. The journey repeats the check on UAT for the manager, staff and viewer accounts.

## Modules that arrived in parallel

Indicators 5 (judgments being enforced) and 8 (appeal deadlines) and families 11 and 12 read `enforcement_file` and `deadline`. They
check that the table exists and report `available: false` (`MODULE_NOT_AVAILABLE`), never zero, when it does not. After rebasing onto develop
(WP4, WP5 and WP6 merged) both tables exist, so these are live; `IndicatorApiTest.appealDeadlinesAndEnforcementCountTheirRowsOnceTheirModulesAreInstalled`
counts them against real rows, hidden cases excluded. Their SQL lives in `reports` because those modules publish no report port yet; it should move
there. The overdue-task figure uses the tasks module's own port, so it follows WP6's status change; the member task counts use `completed_at`,
`CANCELLED` and the due date, which keep their meaning. WP6's `V720` redefines `case_visible` (open tasks are every status but the two final ones);
the fast predicate in `CaseAccessPolicy` only asks the function when the user has any task on the case, which includes all of those.

## Performance — measured honestly

Run: `cd backend && ./mvnw -o test -Dtest=Wp8PerformanceHarness -Dlawdesk.perf=true -Dlawdesk.perf.cases=50000 -Dlawdesk.perf.runs=3 -Dlawdesk.perf.out=../docs/evidence/DOC-WP8/performance-50k-final.md`
(own throwaway embedded PostgreSQL, SYNTH data, real endpoints through MockMvc, `case_visible` in every path). Volumes: 50,000 cases and
584,999 child rows. The host was shared and loaded while measuring, so absolute times carry noise; the before/after gap is far larger.
Files: `performance-baseline-50k.md` (before), `performance-50k-after-predicates.md` (after the visibility change alone),
`performance-50k-final.md`, and the 2,000-case runs.

What was found and changed (all result-neutral):
1. **Visibility ran the database function once per row** (and once per child row). `CaseAccessPolicy` now tests, before the function, conditions
   the rule implies (member, alternate, creator, department or type scope on a non-restricted case); the function runs only for cases reached through
   a unit scope, a grant or a task, and for child tables the rule is evaluated once per case (hashed semi-join).
   `CaseVisibilityPredicateEquivalenceTest` proves the predicates select exactly what `case_visible` selects (32 user/scope combinations over
   members, creator, restricted, typed, unit subtree, active/revoked/expired grants, open/done tasks).
2. **Planner chose a nested loop of 46 million comparisons** (12 s) because it priced the function at 100 per row: `V760` lowers the declared cost
   and adds `task (assignee_id, case_id)`. Plans only. If a later migration redefines `case_visible` or `case_in_scope` with `create or replace`
   it must restate the cost (WP6's V720 does this and is numbered lower, so V760 applies after it).
3. Correlated `EXISTS` over related tables became hashed sets (case text search, linked cases, high risk, reserved for judgment); the case list and
   unified search count and page in one pass; the inventory takes stage counts in one query instead of shipping 50,000 ids.

Result (median ms, 50,000 cases, department-wide manager unless stated; 90 of 126 measurements were over 500 ms before, 12 after):

| user | request | before (ms, median) | after (ms, median) | target 500 ms |
|---|---|---:|---:|---|
| manager (department-wide) | case list, first page | 2038 | 212 | met |
| manager (department-wide) | case list, deep page | 2237 | 359 | met |
| manager (department-wide) | case list, text filter | 13213 | 603 | NOT MET |
| manager (department-wide) | case list, status + type filter | 273 | 144 | met |
| manager (department-wide) | unified search, reference | 39054 | 19261 | NOT MET |
| manager (department-wide) | unified search, words | 19153 | 16828 | NOT MET |
| manager (department-wide) | case inventory report | 6625 | 900 | NOT MET |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 16344 | 2071 | NOT MET |
| manager (department-wide) | indicator figures | 12749 | 1413 | NOT MET |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 402 | 104 | met |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 962 | 325 | met |
| manager (department-wide) | indicator list LINKED_CASES | 4492 | 238 | met |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 1145 | 119 | met |
| manager (department-wide) | report family PERFORMANCE | 2521 | 471 | met |
| scoped to one case type | case list, first page | 4732 | 77 | met |
| scoped to one case type | case list, deep page | 4899 | 80 | met |
| scoped to one case type | case list, text filter | 6535 | 470 | met |
| scoped to one case type | case list, status + type filter | 206 | 52 | met |
| scoped to one case type | unified search, reference | 4484 | 1793 | NOT MET |
| scoped to one case type | unified search, words | 4492 | 1941 | NOT MET |
| scoped to one case type | case inventory report | 7767 | 431 | met |
| scoped to one case type | dashboard (with the 16 indicator figures) | 32304 | 1346 | NOT MET |
| scoped to one case type | indicator figures | 25069 | 989 | NOT MET |
| scoped to one case type | indicator list UPCOMING_HEARINGS | 686 | 307 | met |
| scoped to one case type | indicator list HIGH_RISK_CASES | 1315 | 350 | met |
| scoped to one case type | indicator list LINKED_CASES | 6541 | 212 | met |
| scoped to one case type | report family CASES_BY_TYPE_COURT_STAGE | 2563 | 72 | met |
| scoped to one case type | report family PERFORMANCE | 5458 | 231 | met |
| member (own cases only) | case list, first page | 4993 | 49 | met |
| member (own cases only) | case list, deep page | 5145 | 105 | met |
| member (own cases only) | case list, text filter | 5772 | 436 | met |
| member (own cases only) | case list, status + type filter | 473 | 65 | met |
| member (own cases only) | unified search, reference | 3112 | 242 | met |
| member (own cases only) | unified search, words | 2945 | 268 | met |
| member (own cases only) | case inventory report | 7537 | 329 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 35253 | 1339 | NOT MET |
| member (own cases only) | indicator figures | 26124 | 875 | NOT MET |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 733 | 117 | met |
| member (own cases only) | indicator list HIGH_RISK_CASES | 1781 | 357 | met |
| member (own cases only) | indicator list LINKED_CASES | 4983 | 117 | met |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 2481 | 52 | met |
| member (own cases only) | report family PERFORMANCE | 5384 | 192 | met |

**Not met, stated plainly:**
- *Unified search* (`/api/v1/search`) for a department-wide user: about 19 s (was 39 s); 1.8 s for a user scoped to one case type; under 300 ms for a member.
  The cause is the design of the search, not access control: each query normalises and tokenises the text of every visible record on the fly
  (no stored, indexed search document for cases). Fixing it needs a stored search document for cases kept up to date (generated column plus triggers for
  proceedings, units, members and links) with a GIN index; that is a change to the WP9 search module and is proposed as follow-up work, not done here.
- *Case list with a text filter*: 603 ms for the department-wide manager (target 500 ms); filtering by status, type, period or member is far below.
  `pg_trgm` is not installed on the test or target PostgreSQL, so unanchored ILIKE cannot use an index.
- *Dashboard* (about 2 s for the department-wide manager, 1.1–1.3 s for others, with the 16 indicator figures included) and *indicator figures* are
  computed on demand from about a dozen aggregate queries; no target of 500 ms was set for them in LD-DOC-03, but they are over it. Caching or
  a snapshot table is the next step if the owner wants them under 500 ms.
- *Case inventory report*: 900 ms for the department-wide manager.
Every single indicator list, every report family and the list/filter requests other than the text filter are under 500 ms at 50,000 cases for all three users.

## Tests added

Backend: `IndicatorApiTest` (11), `FamilyApiTest` (16), `CaseVisibilityPredicateEquivalenceTest` (1); `Wp8PerformanceHarness` (opt-in).
`ExportLimitApiTest.theCaseInventoryCountsStagesOfMoreThanTenThousandCasesInBoundedChunks` now asserts the new one-query behaviour.
Frontend: `Wp8Reports.test.tsx` (20) and the section list in `CaseInventoryPage.test.tsx`. Journey: `python3 tools/journeys/wp8_journey.py wp8_journey.json`.

## What remains

Run the journey on UAT after deployment; move the deadline and enforcement SQL into WP4/WP5 report ports; the search document (above);
snapshot or cache for the dashboard if wanted; the exports keep the existing 10,000-row limit (family tables are aggregates and far smaller).

## Defect after merge (UAT): indicators 5 and 8 answered 500

Cause: the check whether the deadline and enforcement tables exist used `to_regclass('public.<table>')`. The deployed application role has its own
schema as search path and no rights on `public` (`deploy/postgres/app-role.sql`), and resolving a name in a schema the role may not use fails with
"permission denied" instead of answering "no". CI connects as a superuser, so it never showed. The same lookup was in the report families 11 and 12.
Fix: the table is looked up by its plain name (search path). `IndicatorPendingModulesTest.theTableLookupWorksForTheRestrictedRoleDeploymentsUse`
reproduces it with a restricted role and fails on the old code (`permission denied`).
Hardening: each indicator is calculated in its own savepoint; if its query fails it is logged and reported `available: false` with
`unavailableReason: INDICATOR_FAILED` (list and figure), and the other indicators and the dashboard still answer (a failed SQL statement aborts a PostgreSQL
transaction, which a savepoint contains; tested with a real `1 / 0`). Malformed requests still answer 400/422.
