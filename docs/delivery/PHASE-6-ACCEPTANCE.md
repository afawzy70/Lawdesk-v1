# Phase 6 — refined acceptance criteria (PROPOSED, awaiting owner review)

Workstream E, dashboard and reporting (LD-030…LD-034). `product/stories.json` holds these stories with templated
criteria (`DRAFT_NEEDS_REFINEMENT`), and `qa/test-cases.json` holds generic scenarios QA-0233…QA-0272. This file
proposes concrete, testable criteria in the style of `PHASE-3-ACCEPTANCE.md` and `PHASE-4-ACCEPTANCE.md`, and maps each
QA case to an executable check. The baseline files are not edited here.

Every rule marked **ASSUMPTION** is a product decision taken by the developer so that work can start; the owner
confirms or changes it. Status values: `AUTOMATED` (a test exists and was run by the developer), `PLANNED` (criterion
agreed, test not written yet), `PENDING` (waits for a Phase 5 read port of Workstream D, see "Dependencies"),
`NOT_DONE` (reason given). Developer runs are not independent QA: every case stays `NOT_RUN` in `qa/test-cases.json`
until QA executes it.

## Common rules (all Phase 6 endpoints and screens)

1. **Real data only.** Every figure is computed by the database from stored rows at request time. No sample, mock or
   estimated figure is ever shown in place of a real one: a figure whose data belongs to an unfinished Phase 5 module
   (hearings, experts, tasks) is reported as *not available yet*, never as a number (architecture/CONTRACTS.md §8.3).
2. **Read ports.** `com.lawdesk.reports` reads data only through read-only query ports of the owning modules:
   `com.lawdesk.cases.CaseReportQueries`, `com.lawdesk.proceedings.ProceedingReportQueries` and
   `com.lawdesk.proceedings.JudgmentReportQueries` (new files, delivered by E; existing Phase 3/4 files unchanged).
   Domain vocabulary comes from the owning module: case status groups from `CaseReportQueries.StatusGroup` (derived
   from `CaseStatus`), stages from `Stage`, outcomes from `Outcome`, finality from the judgment's `final` flag.
3. **Status groups (ASSUMPTION).** *Open* = `OPEN`, `ACTIVE`, `SUSPENDED`; *closed* = `CLOSED`, `ARCHIVED` — the two
   statuses whose details are read-only in Phase 3 (LD-013). A suspended case counts as open.
4. **Security** (CONTRACTS §3, §8.2). JSON reports need `REPORT_VIEW`, exports need `REPORT_EXPORT`. No session → 401,
   missing permission → 403, both `application/problem+json`; the refusal is recorded by the security layer as
   `ACCESS_DENIED` (`DENIED`, target `GET <path>` without the query string). **ASSUMPTION:** `REPORT_VIEW` alone
   opens reports that list case references and titles (it is granted only with `CASE_VIEW` in the default roles), and
   `REPORT_EXPORT` alone opens exports.
5. **Errors** (CONTRACTS §4): a malformed value (date that is not `yyyy-MM-dd`, unknown enum value, malformed UUID,
   non-numeric page) → 400 with `errors[{field, code}]` (`typeMismatch`) and no echoed values; `page < 0` → 400 `Min`;
   `size < 1` → 400 `Min`; `size > 100` is capped at 100; `q` longer than 100 characters → 400 `Size`; a `sort` outside
   the report's whitelist, or a direction other than `asc`/`desc` → 400 `Pattern`. **ASSUMPTION:** an inverted period
   (`from` after `to`) is `422` with `rule: DATE_RANGE_INVALID`, the same rule the case list uses (LD-015).
6. **Lists** are `PageResponse` (`items`, `page`, `size` default 20, `totalItems`, `totalPages`) plus a `summary`
   computed over **all** rows matching the filters (not only the page). Sorting is `sort=<field>,<asc|desc>` (default
   direction `asc`); ties are broken by a stable key, so paging never repeats or skips a row.
7. **Dates.** Periods are inclusive calendar dates (`from` ≤ date ≤ `to`). **ASSUMPTION:** "today" and "this month" are
   evaluated in the `Africa/Cairo` time zone; the default period of a report is the current calendar month.
8. **Screens** (Arabic RTL and English LTR): labels come from `src/i18n/messages/reports.{en,ar}.ts` (status, type,
   stage and outcome names reuse the owning areas' keys); numbers and dates are formatted with `src/i18n/format`
   (`formatNumber`, Arabic-Indic digits in Arabic) and calendar dates without time-zone shift. Filter fields are
   labelled, tables sit in the focusable `.table-scroll` region, long references wrap (`overflow-wrap: anywhere`), and
   the pages have no horizontal page overflow at 375 px and 1366 px. A 403 shows the "not permitted" page, an
   unreachable API a retryable alert.

## LD-030 Legal operations dashboard

Acceptance criteria:

1. `GET /api/v1/reports/dashboard` (`REPORT_VIEW`) returns, from stored data:
   - `cases`: `total`, `open`, `closed` (common rule 3), `newThisMonth` (cases whose `openedOn` falls in the current
     calendar month, first and last day included), `byStatus` (every `CaseStatus`, zeros included) and `byType` (every
     `CaseType`, zeros included);
   - `proceedings`: per `Stage` (all three, zeros included) the number of proceedings (`total`) and of proceedings
     without a judgment (`awaitingJudgment`), and the overall `awaitingJudgment`;
   - `judgments`: for the period (`from`, `to`; default the current month), `recorded` (judgments whose
     `judgmentDate` is in the period), `final` (those marked final) and `byOutcome` (every `Outcome`, zeros included);
   - `pending`: the figures not built yet because their Phase 5 port is missing, as codes
     `HEARINGS_THIS_WEEK`, `EXPERT_REPORTS_DUE`, `OVERDUE_TASKS`. A code leaves the list when its figure is delivered.
2. Validation: `from`/`to` malformed → 400 (`typeMismatch`); inverted → 422 `DATE_RANGE_INVALID`.
3. The dashboard page (`/`) shows the figures of criterion 1 for users holding `REPORT_VIEW`; the figures that were
   mock KPIs before (open cases, pending judgments, cases by stage) now come from the API and the sample-data regions
   and `src/preview/mockData.ts` are removed. The three pending figures are shown as tiles reading "Not available yet",
   without a number. A user without `REPORT_VIEW` sees the dashboard page with a note that statistics need the report
   permission — no error page and no request to the reports API.
4. **ASSUMPTION:** "pending judgments" means proceedings that have no judgment yet (every stage), and the stage
   breakdown counts proceedings, not cases (a case with an appeal counts once at first instance and once at appeal).

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0233 | authorized happy path | `DashboardReportApiTest`: rows inserted, exact deltas against a baseline for every figure; month and period boundaries; empty period | AUTOMATED |
| QA-0234 | required-field validation | `DashboardReportApiTest`: no parameter is required, defaults to the current month | AUTOMATED |
| QA-0235 | invalid input boundary | `DashboardReportApiTest`: malformed dates → 400, inverted period → 422, one-day period counted | AUTOMATED |
| QA-0236 | unauthenticated access | `DashboardReportApiTest`: no session → 401 | AUTOMATED |
| QA-0237 | insufficient permission | `DashboardReportApiTest`: without `REPORT_VIEW` → 403 and one `ACCESS_DENIED` audit row | AUTOMATED |
| QA-0238 | record integrity and rollback | `DashboardReportApiTest`: reading changes no row (table counts unchanged) | AUTOMATED |
| QA-0239 | concurrent or repeat request | `DashboardReportApiTest`: repeated reads give identical figures | AUTOMATED |
| QA-0240 | API-to-UI state and error handling | `DashboardPage.test.tsx`: figures in English and Arabic digits, pending tiles, no permission, 403, failure + retry | AUTOMATED |

## LD-031 Case inventory report

Acceptance criteria:

1. `GET /api/v1/reports/cases` (`REPORT_VIEW`) lists cases with `id`, `referenceNo`, `title`, `caseType`, `status`,
   `statusGroup` (`OPEN`/`CLOSED`), `openedOn`, `currentStage`, `currentCourt` (`id`, `code`, `nameAr`, `nameEn`) and
   `proceedingCount`. Cases without proceedings have `currentStage` and `currentCourt` null.
2. **ASSUMPTION — current stage:** the highest stage among the case's proceedings (cassation > appeal > first
   instance); the current court is the court of the latest-filed proceeding at that stage.
3. Filters combine with AND: `q` (reference or title contains the text, ignoring case, `%` and `_` literal), `status`
   (one or more), `statusGroup` (`OPEN` or `CLOSED`), `caseType` (one or more), `openedFrom` / `openedTo` (inclusive),
   `stage` (the current stage, one or more) and `courtId` (**ASSUMPTION:** a case matches when any of its proceedings
   is before that court). An unknown court id matches nothing (200, empty).
4. Sort whitelist: `referenceNo`, `title`, `caseType`, `status`, `openedOn`; default `openedOn,desc`; ties by
   `referenceNo`, then id.
5. `summary` over all matching cases: `total`, `open`, `closed`, `byStatus`, `byType`, `byStage` (current stage, with
   `NONE` for cases without proceedings).
6. Screen `/reports` (navigation item "Reports" becomes `ready`, shown with `REPORT_VIEW`): a tab per report, the
   filter form (labelled fields, values kept in the address), the summary counts, the sortable table and paging.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0241 | authorized happy path | `CaseInventoryReportApiTest`: five cases with proceedings, rows with current stage/court, summary over all matches, empty result | AUTOMATED |
| QA-0242 | required-field validation | no parameter is required; `CaseInventoryReportApiTest` lists everything without filters | AUTOMATED |
| QA-0243 | invalid input boundary | `CaseInventoryReportApiTest`: 13 malformed parameters → 400 with field and code, `q` of 101 characters → 400, inverted period → 422, one-day period, size capped at 100, page past the end | AUTOMATED |
| QA-0244 | unauthenticated access | `CaseInventoryReportApiTest.needsASession` | AUTOMATED |
| QA-0245 | insufficient permission | `CaseInventoryReportApiTest`: `CASE_VIEW` alone → 403 before validation, one `ACCESS_DENIED` row | AUTOMATED |
| QA-0246 | record integrity and rollback | reading changes no row | AUTOMATED |
| QA-0247 | concurrent or repeat request | repeated reads return identical bodies; stable tie-break for every sort | AUTOMATED |
| QA-0248 | API-to-UI state and error handling | `CaseInventoryPage.test.tsx` (filters in the address, sort, paging, empty, 400/422/500/403, courts only with `COURT_VIEW`, Arabic), `reportsLayout.test.ts`; Chromium check at 320/375/1366 px in both languages: no page overflow | AUTOMATED |

## LD-032 Hearings and judgments reports

Acceptance criteria (judgments part):

1. `GET /api/v1/reports/judgments` (`REPORT_VIEW`) lists judgments whose `judgmentDate` is in the period (`from`, `to`,
   default the current month) with `id`, `judgmentDate`, `outcome`, `final`, `stage`, `court` (`id`, `code`, `nameAr`,
   `nameEn`), `courtCaseNumber`, `courtCaseYear`, `caseId`, `caseReferenceNo`, `caseTitle`.
2. Filters: `outcome` (one or more), `stage` (one or more), `courtId`, `final` (`true`/`false`). Sort whitelist:
   `judgmentDate`, `outcome`, `stage`; default `judgmentDate,desc`; ties by the newest judgment date, then id. The response also echoes the resolved period (`from`, `to`).
3. `summary`: `total`, `final`, `byOutcome`, `byStage` over all matching judgments.
4. The judgments section (`/reports/judgments`) shows the period, the filters, summary and table.

Hearings part: **PENDING** on Workstream D's `HearingReportQueries` (LD-024). The reports screen lists it as "not
available yet"; no figure or row is shown.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0249 | authorized happy path | `JudgmentReportApiTest`: five judgments around May 2012, default month, rows with case/court/finality, summary over all matches | AUTOMATED |
| QA-0250 | required-field validation | no parameter is required; the period defaults to the current month (`Africa/Cairo`) | AUTOMATED |
| QA-0251 | invalid input boundary | `JudgmentReportApiTest`: period boundaries (first/last day, one day, empty), 10 malformed parameters → 400, inverted period → 422, size cap | AUTOMATED |
| QA-0252 | unauthenticated access | `JudgmentReportApiTest.needsASession` | AUTOMATED |
| QA-0253 | insufficient permission | `JUDGMENT_VIEW` alone → 403 and one `ACCESS_DENIED` row | AUTOMATED |
| QA-0254 | record integrity and rollback | reading changes no row | AUTOMATED |
| QA-0255 | concurrent or repeat request | identical repeated reads; stable tie-break (newest date, then id) for every sort | AUTOMATED |
| QA-0256 | API-to-UI state and error handling | `JudgmentReportPage.test.tsx` (filters in the address, `final`, sort, paging, empty, 400/422/403, Arabic) and the Chromium overflow check | AUTOMATED |
| — | hearings report | waits for `HearingReportQueries` | PENDING |

## LD-033 Experts and workload reports

**PENDING** on Workstream D's expert referral (LD-025) and task (LD-027) report ports. Nothing is built or shown as a
figure until they are on `develop`.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0257…QA-0264 | the eight standard scenarios | waits for D's ports | PENDING |

## LD-034 Parameterized filters and CSV / Excel / PDF exports

Acceptance criteria:

1. Every report's JSON filters (LD-031, LD-032) are also the parameters of its export:
   `GET /api/v1/reports/cases/export` and `GET /api/v1/reports/judgments/export` (`REPORT_EXPORT`) with
   `format=csv|xlsx|pdf` (required; missing or unknown → 400) and `lang=en|ar` (labels; default `en`). The export holds
   every matching row in the report's sort order, without paging.
2. CSV: `text/csv; charset=UTF-8`, a UTF-8 byte order mark, CRLF line ends, RFC 4180 quoting (a field containing a
   comma, a double quote, CR or LF is quoted and its quotes doubled), a header row of localized column names, and
   localized enum labels. **Formula injection:** a cell starting with `=`, `+`, `-`, `@`, a tab or a carriage return
   is prefixed with `'`.
3. Every export answers `Content-Disposition: attachment; filename="<report>-<yyyy-MM-dd>.<ext>"` and
   `X-Content-Type-Options: nosniff`.
4. **ASSUMPTION:** at most 10 000 rows per export; more matching rows → 422 `rule: EXPORT_TOO_LARGE`, nothing is
   produced, and the user narrows the filters.
5. A successful export is audited once as `REPORT_EXPORT` (`SUCCESS`, target type `REPORT`, target id
   `<report>:<format>`, e.g. `CASE_INVENTORY:CSV`) — never the exported data or the filter values.
6. Excel (`.xlsx`) and PDF follow the same columns; the Arabic PDF renders shaped, right-to-left text with an embedded
   font. Libraries are pinned and permissively licensed.
7. The reports screen offers the export buttons only to users holding `REPORT_EXPORT`.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0265…QA-0271 | backend scenarios | `ReportExportApiTest`, `CsvTest` | PLANNED |
| QA-0272 | API-to-UI state and error handling | `ReportsPage.test.tsx` (export links) | PLANNED |

## Dependencies

- Phase 5 read ports of Workstream D (architecture/CONTRACTS.md §8.3): `HearingReportQueries` (hearings this week, the
  hearings report), the expert referral port (expert reports due, the experts report) and the task port (overdue tasks,
  the workload report). Until each is on `develop`, the matching figure is listed in `pending` and the report is
  PENDING here.
- `docs/delivery/PREVIEW.md` and the preview banner: once the dashboard shows no sample figures, the banner sentence
  "The dashboard shows sample figures" is no longer true. The banner keys live in the shared catalogs (`en.ts`,
  `ar.ts`), so the change is requested from the integration owner.
