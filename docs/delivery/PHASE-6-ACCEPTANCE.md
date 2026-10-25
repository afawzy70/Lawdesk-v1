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
   - `operations` (from Workstream D's report ports, each rule evaluated by its owning module): `hearingsThisWeek`
     (scheduled hearings from today to today + 6 days, `HearingReportQueries.upcomingCount(6)`),
     `hearingsAwaitingResult` (still scheduled after their day), `expertReportsOverdue` (open referrals whose report
     was due before today), `overdueTasks` (open tasks due before today) and `tasksDueSoon` (open tasks due today or
     within `TaskReportQueries.DUE_SOON_DAYS` = 3 days);
   - `pending`: figures still waiting for a port, as codes; empty since the Phase 5 ports are integrated (LD-033 PR).
2. Validation: `from`/`to` malformed → 400 (`typeMismatch`); inverted → 422 `DATE_RANGE_INVALID`.
3. The dashboard page (`/`) shows the figures of criterion 1 for users holding `REPORT_VIEW`; the figures that were
   mock KPIs before (open cases, pending judgments, cases by stage) now come from the API and the sample-data regions
   and `src/preview/mockData.ts` are removed. The legal-operations figures are tiles with
   their numbers (before the Phase 5 ports were integrated they read "Not available yet", never a number). A user without `REPORT_VIEW` sees the dashboard page with a note that statistics need the report
   permission — no error page and no request to the reports API.
4. **ASSUMPTION:** "this week" on the dashboard is today and the six days after it; "expert reports due" is shown as
   the reports overdue (the expert port's rule). "Today" for these figures is each Phase 5 port's own clock, the
   calendar day in `Africa/Cairo`.
5. **ASSUMPTION:** "pending judgments" means proceedings that have no judgment yet (every stage), and the stage
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

Hearings part (through Workstream D's `HearingReportQueries`):

5. `GET /api/v1/reports/hearings` (`REPORT_VIEW`) lists the hearings dated in the period (`from`, `to`, default the
   current month) with `id`, `hearingDate`, `hearingTime`, `purpose`, `status`, `awaitingResult` (the hearing port's
   rule: still scheduled after its day), `stage`, `court`, `courtCaseNumber`, `courtCaseYear`, `caseId`,
   `caseReferenceNo`, `caseTitle`; filter `status` (one or more); `PageResponse` plus `from`, `to` and `summary`
   (`total`, `byStatus`, `awaitingResult`) over every matching hearing.
6. **ASSUMPTION:** the hearing port lists hearings in calendar order only (date, untimed first, time, order of
   recording), so the report offers that order alone: `sort=hearingDate[,asc]`; anything else is 400 `Pattern`.
7. The hearings section (`/reports/hearings`) shows the period, the filters, the summary, the table and paging.

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
| — | hearings report | `HearingReportApiTest` (13: six hearings in May 2013 with "today" pinned, calendar order, case/stage/court, status and period filters, summary incl. awaiting result, paging, 6 malformed parameters, 422, 401/403 + audit, no writes); `OperationsReports.test.tsx` | AUTOMATED |

## LD-033 Experts and workload reports

Read through Workstream D's `TaskReportQueries`, `ExpertReportQueries` and the user directory.

1. `GET /api/v1/reports/workload` (`REPORT_VIEW`): one row per assignee with open work (`assigneeId`, `username`,
   `displayName`, `enabled`, `open`, `inProgress`, `overdue`), by display name, assignees the directory does not know
   last (shown as "Unknown user"); `dueSoonDays`; `summary` (`byStatus` of every task, `overdue`, `dueSoon`).
2. `GET /api/v1/reports/experts` (`REPORT_VIEW`): every expert of the registry, deactivated ones included
   (`expertId`, `name`, `specialty`, `active`, `open` referrals, `overdue` reports), by name; `summary` (`byStatus` of
   every referral, `overdueReports`).
3. Screens `/reports/workload` and `/reports/experts`, with the summaries, tables and export links.
4. **ASSUMPTION:** both reports are short (one row per person) and have no filters or paging.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0257 | authorized happy path | `WorkloadReportApiTest`, `ExpertReportApiTest`: rows per assignee / expert with names and exact counts, totals as deltas from a baseline | AUTOMATED |
| QA-0258 | required-field validation | no parameter exists | N/A |
| QA-0259 | invalid input boundary | assignees without open tasks are not listed; unknown assignee; deactivated expert listed with zero | AUTOMATED |
| QA-0260 | unauthenticated access | `needsASessionAndTheReportPermission` (both) | AUTOMATED |
| QA-0261 | insufficient permission | `TASK_VIEW` / `EXPERT_VIEW` alone → 403 and one `ACCESS_DENIED` row | AUTOMATED |
| QA-0262 | record integrity and rollback | reports only read (repeatable-read snapshot) | AUTOMATED |
| QA-0263 | concurrent or repeat request | stable order (name, then id) | AUTOMATED |
| QA-0264 | API-to-UI state and error handling | `OperationsReports.test.tsx`: tables, summaries, empty, 403, failures, Arabic digits, export links | AUTOMATED |

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
6. Excel (`.xlsx`) and PDF follow the same columns and labels. Excel: text cells are never formulas, dates are date
   cells, counts numbers; an Arabic export is a right-to-left sheet. PDF: A4 landscape, header repeated on every page,
   DejaVu Sans embedded (subset); Arabic letters are shaped (contextual forms, lam-alef ligatures) and every line is
   laid out with the Unicode bidirectional algorithm, each cell taking the direction of its first strong letter; an
   Arabic export mirrors the table (first column on the right, text right-aligned). Libraries are pinned and
   permissively licensed: fastexcel 0.20.2 (Apache-2.0), Apache PDFBox 3.0.8 (Apache-2.0), ICU4J 78.3 (Unicode-3.0);
   the font under the Bitstream Vera licence with public-domain DejaVu changes.
7. The reports screen offers the export buttons only to users holding `REPORT_EXPORT`.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0265 | authorized happy path | `ReportExportApiTest`: exact CSV bytes of both reports (BOM, header, rows, CRLF), English and Arabic labels, filters and sort applied, headers `Content-Disposition`, `nosniff`, `no-store`; one `REPORT_EXPORT` audit row without data | AUTOMATED (CSV) |
| QA-0266 | required-field validation | `format` missing → 400 `NotNull` | AUTOMATED |
| QA-0267 | invalid input boundary | unknown `format`/`lang` → 400 `Pattern`, malformed filters → 400, inverted period → 422; exactly 10 000 rows exported, 10 001 → 422 `EXPORT_TOO_LARGE` and nothing audited; `CsvTest` quoting and formula neutralisation | AUTOMATED |
| QA-0268 | unauthenticated access | `ReportExportApiTest.needsASession` | AUTOMATED |
| QA-0269 | insufficient permission | `REPORT_VIEW` without `REPORT_EXPORT` → 403 and `ACCESS_DENIED` for both exports, no export audited | AUTOMATED |
| QA-0270 | record integrity and rollback | a refused export writes no `REPORT_EXPORT` row; exports read one snapshot | AUTOMATED |
| QA-0271 | concurrent or repeat request | each successful export is audited once | AUTOMATED |
| QA-0272 | API-to-UI state and error handling | `CaseInventoryPage.test.tsx`, `JudgmentReportPage.test.tsx`: "Download CSV" link with the active filters and language, only with `REPORT_EXPORT` | AUTOMATED (CSV) |
| — | Exports of the hearings, workload and experts reports | `OperationsExportApiTest`: exact CSV rows (English, Arabic), names in workload and experts exports, Arabic hearings PDF, experts workbook, 403/400/422, audit `HEARINGS:`, `WORKLOAD:`, `EXPERTS:` | AUTOMATED |
| — | Excel (`.xlsx`) and PDF | `ExportFormatsApiTest` (right-to-left Arabic sheet, text not formulas, shaped Arabic glyphs in visual order with an embedded font, English PDF text, multi-page, row limit, audit `:XLSX`/`:PDF`), `PdfTextTest` (hand-derived presentation forms, lam-alef, mixed Latin/Arabic, isolates, wrapping); rendered samples `docs/evidence/PHASE-6/27-pdf-sample-*.png` | AUTOMATED |

**ASSUMPTIONS (LD-034):** CSV dates are ISO `yyyy-MM-dd` and numbers plain digits (machine-readable in either
language); the court column holds the court name in the export language; a case without proceedings has empty stage
and court cells.

**QAD-035 (XLSX dimension), fixed.** The writer (fastexcel) declares `<dimension ref="A1"/>` whatever the sheet holds,
so streaming readers (openpyxl `read_only=True`) saw one row. Every `.xlsx` export now declares
`A1:<last column><last row>` (header row included, for example `A1:I10001` at the 10 000-row limit), in English and
Arabic; the sheet part is rewritten while the workbook is zipped again and every other part is copied unchanged.
Tests: `XlsxTest` (streaming XML read of `sheet1.xml`), `ExportFormatsApiTest` (English, Arabic, limit); extra evidence
`docs/evidence/PHASE-6/44-qad035-openpyxl-readonly-check.log` (openpyxl 3.1.5 in a throwaway virtual environment, not a
project dependency): normal and read-only readers agree at 7 and 10 001 rows.

**ASSUMPTION (QAD-036, owner-delegated decision): direction of PDF cells.** A PDF cell (and the title line) without any
strong right-to-left character keeps its logical order and is laid out left to right, in English and Arabic exports:
`=1+1`, `-1+1`, `+7`, `@a` are drawn as written. A cell with Arabic (or other right-to-left) letters stays right to
left and follows the Unicode bidirectional algorithm, so an operator next to a number inside such a cell may move
(`=1+1 تركة` reads `1+1=` beside the word, as in any Arabic text). Headers, the mirroring of the table and the
right alignment of Arabic exports are unchanged. CSV and Excel keep their formula-injection neutralisation exactly
as before (a `'` prefix in CSV, text cells in Excel). Tests: `PdfTextTest` (11 neutral cells, both languages; mixed
cells), `ExportFormatsApiTest` (extracted text order of `=1+1`, `-1+1`, `+7`, `@a` in Arabic and English PDFs);
rendered sample `docs/evidence/PHASE-6/47-pdf-neutral-cells-arabic.png`.

**QAD-039 (export row limit above 10 000 experts), fixed, and the export audit that followed.**
1. Every export is refused with 422 `EXPORT_TOO_LARGE` at 10 001 rows **before anything is looked up or serialised**:
   no name lookup, no attachment, no `REPORT_EXPORT` success audit, in CSV, Excel and PDF. Exactly 10 000 rows are
   exported. The experts and workload exports used to read their whole report first: with 10 001 experts the name lookup
   exceeded the expert port's own limit (10 000 ids per call) and answered 500; the workload export looked every
   assignee up before checking the size. Both now read the registry or the assignee list as ids and counts, check the
   size, and only then look names up.
2. The on-screen experts and workload reports have no row limit and work above 10 000 rows: names are looked up in
   chunks of 1 000 ids (experts, through `ExpertReportQueries.experts(ids)`) or from one `UserDirectory.active()` read
   plus a single lookup for each assignee it does not hold (disabled or unknown accounts).
3. The case inventory counts the current stage of every matching case through the proceedings port in chunks of at
   most `ProceedingReportQueries.MAX_IDS` (10 000) case ids per call; the chunk counts add up.
4. **Performance (found by the audit).** Lists of case, proceeding and expert ids reach the database as one array
   parameter. A filter written `id = any(cast(? as uuid[]))` re-evaluates the cast for every row of a sequential scan:
   10 000 ids took about 105 s per query, so a judgments or hearings export over 10 000 different cases, or the case
   inventory with a stage or court filter over 10 000 cases, needed minutes. The ids are now read once, as an
   uncorrelated subquery (`id in (select unnest(cast(? as uuid[])))`). Test: `ExportLimitApiTest` answers each of those
   requests within 30 s (they now take seconds).
5. Tests (`ExportLimitApiTest`, rows inserted by SQL): experts, workload, hearings, judgments and case-inventory
   exports at 10 000 (CSV counted row by row; Excel and PDF too for experts) and at 10 001 (422 in every format); every
   on-screen report at 10 001; chunk sizes of the name and stage lookups captured on the ports.
6. **Audit, what was checked** (every class in `com.lawdesk.reports` and the ports it calls): dashboard (aggregates
   only, no id lists); case inventory page and export (count before list, ids chunked, filter ids as a subquery);
   judgments page and export (count before list; case references of at most one page or 10 000 rows); hearings page and
   export (count before list; "awaiting result" is counted by reading the matching hearings 10 000 at a time, which is
   correct but proportional to the matching rows); workload and experts (see 1 and 2). Accepted as is: the stage and
   court filters pass the ids of every matching case to the cases port, bounded by the size of the registry.

## Dependencies

- Phase 5 read ports of Workstream D (architecture/CONTRACTS.md §8.3): `HearingReportQueries`, `TaskReportQueries`
  and `ExpertReportQueries` (with `experts(ids)` for names), delivered in PRs #33, #37, #40, #43, #44. The LD-033 PR
  builds on them and must not be merged before them.
- `docs/delivery/PREVIEW.md` and the preview banner: once the dashboard shows no sample figures, the banner sentence
  "The dashboard shows sample figures" is no longer true. The banner keys live in the shared catalogs (`en.ts`,
  `ar.ts`), so the change is requested from the integration owner.
