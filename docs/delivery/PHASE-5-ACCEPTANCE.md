# Phase 5 — refined acceptance criteria (PROPOSED, awaiting owner review)

Workstream D, legal operations (LD-024…LD-029, architecture/CONTRACTS.md §8). `product/stories.json` holds these
stories with templated criteria (`DRAFT_NEEDS_REFINEMENT`), and `qa/test-cases.json` holds the generic scenarios
QA-0185…QA-0232. This file proposes concrete, testable criteria in the style of `PHASE-3-ACCEPTANCE.md` and
`PHASE-4-ACCEPTANCE.md`, and maps each QA case to an executable check as the stories land. The baseline files are not
edited here.

Every rule marked **ASSUMPTION** is a product decision taken by the developer so that work can start; the owner
confirms or changes it, and changing one changes the tests named next to it. Status values: `AUTOMATED` (a test exists
and was run by the developer), `PLANNED` (criterion agreed, story not delivered yet), `BLOCKED` (reason given).
Developer runs are not independent QA: every case stays `NOT_RUN` in `qa/test-cases.json` until QA executes it.

The Egyptian legal-practice model is the one Phase 4 built: a case (B) holds proceedings (C) by stage —
first instance, appeal, cassation — each before a court, with a court number and year (رقم الدعوى / السنة) and a filing
date; a proceeding ends with a judgment. Phase 5 attaches the day-to-day work to that model: the hearings (الجلسات) of
a proceeding, the expert referrals (ندب الخبراء) a court orders, the PDF papers of the case, the tasks and deadlines of
the office, and its internal legal notes.

## Common rules (all Phase 5 endpoints)

1. Every endpoint lives under `/api/v1`, answers JSON in camelCase and follows CONTRACTS §4: 400 with
   `errors[{field, code}]` for a validation failure (no rejected values echoed), 400 without a field list for unreadable
   JSON (including an unknown enum value) and for a malformed id or date in the path or query, 404 for an unknown id,
   409 for a duplicate or a stale `version`, 422 with a stable `rule` code for a refused business rule, 500 without
   details.
2. Without a session the answer is 401; a signed-in user without the permission gets 403; both are
   `application/problem+json`, and the request changes nothing. Unsafe requests need the CSRF token. The permission is
   checked before the body is read (QAD-026), so a refused caller never learns field rules. Every 403 is recorded by
   the platform in the security audit log as `ACCESS_DENIED` (outcome `DENIED`, target `"<METHOD> <path>"`, LD-011);
   each story has one test that checks the audit row for one of its endpoints.
3. Permissions (CONTRACTS §8.2): `HEARING_VIEW` / `HEARING_MANAGE`, `EXPERT_VIEW` / `EXPERT_MANAGE`, `DOCUMENT_VIEW` /
   `DOCUMENT_MANAGE`, `TASK_VIEW` / `TASK_MANAGE`, `NOTE_MANAGE` (reading notes needs `CASE_VIEW`).
4. Writes record the acting user (`CurrentActor`) in `created_by` / `updated_by` and the time in `created_at` /
   `updated_at` (UTC). Editable records carry `version`; an update with another `version` gets 409 and changes nothing.
   Updates that compare stored state lock the row (`select ... for update`) first, so two racing writes are serialised.
5. Text input is trimmed; an optional text that is empty after trimming is stored as `null`, a required one counts as
   missing (`NotBlank`). Length limits count characters after trimming.
6. Lists are `PageResponse` (`page`, `size` default 20, capped at 100). **ASSUMPTION:** list order is fixed per
   endpoint (given below); `sort` is ignored. **OWNER DECISION 2026-10-25 (QAD-029):** an invalid `page` or `size` (not an integer, `page` below 0, `size` below 1) is
   refused with 400 and names the parameter (`errors[].field`, code `Min` or `Digits`); a missing or blank one takes the
   default (page 0, size 20); a `size` above 100 is capped. One rule for every list, including the case list and case
   timeline (PHASE-3-ACCEPTANCE LD-015): a shared interceptor applies it to every handler that takes a `Pageable`, after
   the authorization check, so a caller without the permission still gets 403. `PagingPolicyTest` pins it, including a
   test that discovers every paged handler (architecture/CONTRACTS.md §4).
7. Each recording write adds one case timeline entry through `CaseLookup#recordEvent(caseId, type, summary)` in the same
   transaction: if the event fails, the write is rolled back. Summaries name dates, numbers and statuses only, never
   the free text of notes, decisions or task descriptions (the timeline is shown to every `CASE_VIEW` holder).
8. **ASSUMPTION:** an `ARCHIVED` case (LD-017, read-only) accepts no new or changed operations records:
   `422 CASE_ARCHIVED`. Reading stays possible. `CLOSED` cases still accept them (for example a hearing on enforcement
   or a task after closure).
9. **ASSUMPTION:** "today" is the calendar date in Egypt (`Africa/Cairo`), for due-soon, overdue and default calendar
   windows.
10. Cases are read through `CaseLookup`, proceedings through `ProceedingLookup`, people through `UserDirectory` (§8.3).
    Phase 5 never writes the tables of Phases 2–4.

## LD-024 Schedule and record hearings

A hearing (جلسة) belongs to one proceeding (and so to its case and court).

| Field | Rule |
|-------|------|
| `hearingDate` | Required date. Not before the proceeding's filing date (`422 HEARING_BEFORE_FILING`). At most two years after today (`400`, field `hearingDate`, code `Range`) — **ASSUMPTION** (catches typing errors in the year). Past dates are allowed, to record hearings of files taken over from elsewhere. |
| `hearingTime` | Optional time of day (`HH:mm`). **ASSUMPTION**: Egyptian courts call a whole roll from the morning, so most hearings have no time. |
| `purpose` | Required: `FIRST_SESSION` (أول جلسة), `PLEADING` (مرافعة), `EVIDENCE` (إثبات / تحقيق / سماع شهود), `EXPERT_REPORT` (مناقشة تقرير الخبير), `JUDGMENT` (للنطق بالحكم), `OTHER`. **ASSUMPTION** (list of purposes). |
| `notes` | Optional, at most 2000 characters (preparation notes). |
| `status` | `SCHEDULED` when created; then exactly once `HELD` (انعقدت), `ADJOURNED` (تأجلت) or `CANCELLED` (ألغيت / شطبت). |
| `decision` | Optional, at most 2000 characters: what the court decided at the hearing (قرار الجلسة), set with the result. |

Acceptance criteria:

1. `POST /api/v1/proceedings/{proceedingId}/hearings` (`HEARING_MANAGE`) schedules a hearing: 201, `Location:
   /api/v1/hearings/{id}`, the stored hearing. An unknown proceeding is 404. The answer names the case (`caseId`,
   `caseReferenceNo`, `caseTitle`), the proceeding (`stage`, `courtCaseNumber`, `courtCaseYear`) and the court
   (`courtCode`, `courtNameAr`, `courtNameEn`), so lists need no further lookups. Timeline: `HEARING_SCHEDULED` with the
   date and the proceeding's number and year. The hearing's case is always its proceeding's case: it is taken from the
   proceeding, never from the request, and the database refuses a mismatch on insert and update (trigger
   `hearing_case_matches_proceeding`, V401; integration review of PR #33).
2. **ASSUMPTION:** one hearing per proceeding and day: a second hearing (not cancelled) on the same proceeding and date
   is 409 and stores nothing. The database enforces it with a partial unique index, so a repeated or concurrent request
   also gets 409.
3. `GET /api/v1/hearings/{id}` (`HEARING_VIEW`) reads one hearing. `GET /api/v1/cases/{caseId}/hearings`
   (`HEARING_VIEW`) lists the case's hearings oldest first (date, then time with untimed hearings first, then order of
   recording); an unknown case is 404. **ASSUMPTION:** `HEARING_VIEW` alone shows the case reference and title in
   hearing answers (a hearing calendar is unusable without them).
4. `PUT /api/v1/hearings/{id}` (`HEARING_MANAGE`) changes date, time, purpose and notes of a `SCHEDULED` hearing, with
   `version`. A hearing with a result is `422 HEARING_NOT_SCHEDULED`. The filing-date rule applies; a hearing created by
   an adjournment cannot move to or before the hearing it continues (`422 ADJOURNMENT_NOT_LATER`). Timeline:
   `HEARING_RESCHEDULED` when the date or time changes, nothing otherwise.
5. `POST /api/v1/hearings/{id}/result` (`HEARING_MANAGE`) records the outcome once, with `version`:
   - `{ status: HELD, decision }` — the hearing took place (for example "reserved for judgment").
   - `{ status: ADJOURNED, decision, nextHearingDate, nextHearingTime, nextHearingPurpose }` — the court adjourned the
     case (تأجيل). In the same transaction a new `SCHEDULED` hearing is created on the same proceeding with
     `adjournedFromId` pointing back; the adjourned hearing answers its `nextHearingId`. `nextHearingDate` is required
     (`400 NotNull`) and must be after the adjourned hearing's date (`422 ADJOURNMENT_NOT_LATER`); the purpose defaults
     to the adjourned hearing's purpose (**ASSUMPTION**); a next hearing already scheduled that day is 409.
   - `{ status: CANCELLED, decision }` — the hearing will not take place (for example struck off the roll).
   Rules: a hearing with a result is `422 HEARING_NOT_SCHEDULED`; `HELD` and `ADJOURNED` need the hearing day to have
   come (`422 HEARING_NOT_YET_HELD`), `CANCELLED` is allowed before it; `nextHearing*` fields with `HELD` or
   `CANCELLED` are `400` (code `Null`). Timeline: `HEARING_HELD`, `HEARING_ADJOURNED` (naming the next date) or
   `HEARING_CANCELLED`. **ASSUMPTION:** a recorded result is final in v1 — no correction endpoint; hearings are never
   deleted (cancel instead).
6. `GET /api/v1/hearings?from=&to=&status=&page=&size=` (`HEARING_VIEW`) is the hearing calendar across cases: hearings
   with `from ≤ hearingDate ≤ to`, optionally of one `status`, ordered by date, time (untimed first), order of
   recording. **ASSUMPTION:** `from` defaults to today and `to` to `from` + 30 days; `to` before `from`, or a range of
   more than 366 days, is `400` (field `to`, code `Range`).
7. QAD-032: the chronology *filing date ≤ every hearing* holds whichever side is edited. Correcting a proceeding's
   filing date (`PUT /api/v1/proceedings/{id}`, LD-019) to after one of its hearings is `422
   FILING_AFTER_DEPENDENT_HEARING` and writes nothing; the same day as the earliest hearing is allowed. **ASSUMPTION:**
   cancelled hearings count too, so the record never shows a hearing before the filing. The proceedings package asks
   the modules that depend on its filing date through the port `com.lawdesk.proceedings.ProceedingDependentDates`
   (hearings implement it), while it holds the proceeding row lock it already takes (QAD-024). Scheduling,
   rescheduling and adjourning a hearing take the same lock first (`ProceedingLookup#lockForUpdate`), so racing writes
   are serialised and the second one sees the first one's committed date. Tests: `HearingFilingChronologyTest` (direct
   edit, cancelled hearing, and four races with a test-only pause trigger: filing edit first vs scheduling, scheduling
   first vs filing edit, reschedule first, adjournment first — each one success and one 422). Evidence:
   `LD-024-18-red-QAD-032-backend.log`, `LD-024-19-green-QAD-032-backend.log`, `LD-024-20/21` (frontend message).
   **ASSUMPTION:** the date of a hearing is still not checked against the proceeding's judgment date (hearings after
   the judgment, such as on enforcement, stay possible).
   QAD-033: the database enforces the same chronology, so no other writer (direct SQL, a future module) can break it.
   Migration `V406__LD-024_hearing_dates_database_integrity.sql` adds triggers that refuse, with a check violation
   (SQLSTATE 23514) naming the invariant and nothing changed:
   - `hearing_not_before_filing`: a hearing inserted or moved to before its proceeding's filing date (cancelled
     hearings count, as in the API);
   - `proceeding_filing_not_after_hearing`: a filing date moved to after any hearing of the proceeding (the same day is
     valid);
   - `proceeding_case_immutable`: a proceeding's `case_id` changes (Phase 4 never changes it, so this is simply
     immutable; the hearing-side mismatch was already refused by V401);
   - `hearing_follows_adjourned`: an adjourned hearing's next hearing not strictly after the hearing it continues,
     whichever of the two is edited.
   The hearing trigger reads the proceeding row `FOR SHARE`, the filing-date correction holds it exclusively, so even two
   writers that do not use the API lock are serialised. The API pre-check under the proceeding row lock still runs first
   and answers the 422 rules above, never a 500. The migration validates and rewrites no existing row (only new
   statements are checked), so it cannot fail on data the API accepted; `MigrationPhase5IntegrityTest` proves it with
   rows written through V405. **Authorized exception:** the trigger on the Phase 4 table `proceeding` is created from a
   Phase 5 migration (listed in architecture/CONTRACTS.md §8). Tests: `HearingDatabaseIntegrityTest` (the three QA
   probes as direct SQL, cancelled hearings, the adjournment chain, same-day edits, two SQL writers racing in both
   orders, the API still answering 422), and the six race tests of `HearingFilingChronologyTest` unchanged. Evidence:
   `LD-024-25-red-QAD-033-hearing-database.log`, `LD-024-26-red-QAD-033-share-lock-mutation.log` (the race test fails
   without `FOR SHARE`), `LD-024-27-green-QAD-033-hearing-database.log`.
8. **Report port for Phase 6:** `com.lawdesk.hearings.HearingReportQueries` (read-only, no security of its own: the
   reports module checks `REPORT_VIEW`): hearings in a date range with optional status, case and proceeding filters,
   paged, with their total, and counts by status. The rules "upcoming" (`SCHEDULED` and dated today or later) and
   "awaiting a result" (`SCHEDULED` and dated before today) are defined there.
9. Screens (Arabic and English, 375 px and 1366 px wide, keyboard only):
   - `/hearings`, the hearing calendar: an agenda list for a date range (default the next 30 days) and a status filter,
     paged; each row shows the day, time, case reference (linking to the case), court, court number, purpose and
     status. A `SCHEDULED` hearing offers "Record result" and "Reschedule"; adjourning asks for the next date.
     Hearings whose day has passed without a result are marked "awaiting result".
   - In the case's operations section (`CaseOperations`): the case's hearings and a "Schedule hearing" form that picks
     one of the case's proceedings.
   Field errors appear next to their field; 409 asks to reload; 422 explains the rule; 403 shows "not permitted".
   The navigation item `/hearings` is `ready` (shown to `HEARING_VIEW` holders) and its route lives in
   `operations/routes.tsx`; the case-level section is mounted on the case details page.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0185 | authorized happy path | `HearingApiTest`: schedule (201, `Location`, timeline), read, case list, calendar, reschedule, hold, adjourn (next hearing created), cancel | AUTOMATED |
| QA-0186 | required-field validation | `HearingApiTest.requiredFieldsAreValidated`, result `nextHearingDate` required when adjourned | AUTOMATED |
| QA-0187 | invalid input boundary | `HearingApiTest`: notes and decision 2000/2001 characters, date two years ahead, filing-date rule, calendar range | AUTOMATED |
| QA-0188 | unauthenticated access | `HearingApiTest.everyEndpointNeedsASession` | AUTOMATED |
| QA-0189 | insufficient permission | `HearingApiTest.viewersCannotWriteAndOthersCannotRead`, `aRefusalIsAuditedAsAccessDenied` | AUTOMATED |
| QA-0190 | record integrity and rollback | Unknown proceeding/case → 404, nothing stored; a failing timeline event rolls the hearing (and an adjournment's next hearing) back; database checks, including `aHearingAlwaysBelongsToItsProceedingsCase` | AUTOMATED |
| QA-0191 | concurrent or repeat request | Same proceeding and day → 409; second result → 422; stale version → 409; two racing results → one wins | AUTOMATED |
| QA-0192 | API-to-UI state and error handling | `HearingsPage.test.tsx`, `CaseHearings.test.tsx`, `OperationsLayout.test.tsx` (case page mount, narrow-screen CSS); real-browser check at 375 px and 1366 px in both languages (`docs/evidence/PHASE-5/LD-024-09-layout-375-1366.log`) | AUTOMATED |

## LD-027 Tasks and assignments

| Field | Rule |
|-------|------|
| `title` | Required, at most 200 characters. |
| `description` | Optional, at most 4000 characters. |
| `caseId` | Optional: a task may belong to a case (404 when unknown); fixed once created. |
| `assigneeId` | Required: an enabled user of `UserDirectory` (`GET /api/v1/users/directory`); an unknown or disabled user is `422 ASSIGNEE_NOT_ASSIGNABLE`. Stored without a foreign key (§8.3). |
| `dueDate` | Optional date. A new or changed due date cannot be before today (`400`, code `FutureOrPresent`); an unchanged past due date may stay. **ASSUMPTION**: tasks have dates, not times. |
| `priority` | `LOW`, `NORMAL` (default), `HIGH`, `URGENT`. **ASSUMPTION**. |
| `status` | `OPEN` → `IN_PROGRESS` → `DONE`; `OPEN` → `DONE` directly (QAD-031); `OPEN` / `IN_PROGRESS` → `CANCELLED`; `DONE` / `CANCELLED` → `OPEN` (reopen); `IN_PROGRESS` → `OPEN`. Other moves are `422 TASK_STATUS_TRANSITION`. **ASSUMPTION** for owner review (QAD-031): a task may be completed directly from `OPEN`, without first being marked `IN_PROGRESS`, because a small office often finishes a short task in one step; the owner may require the `IN_PROGRESS` step instead. Pinned by `TaskApiTest.aTaskCanBeCompletedDirectlyFromOpen`. |

1. `POST /api/v1/tasks` (`TASK_MANAGE`): 201, `Location`. `GET /api/v1/tasks/{id}` (`TASK_VIEW`). `PUT
   /api/v1/tasks/{id}` (`TASK_MANAGE`, with `version`) changes title, description, assignee, due date and priority.
   `POST /api/v1/tasks/{id}/status` (`TASK_MANAGE`, `{status, version}`) moves the status; `completedAt` is set when it
   becomes `DONE` and cleared when it is reopened.
2. `GET /api/v1/tasks?assignee=<id|me>&status=&caseId=&due=<overdue|soon>&page=&size=` (`TASK_VIEW`) lists tasks by due
   date (undated last), then priority (urgent first), then creation. Answers carry the assignee's display name and, for
   case tasks, the case reference and title, plus `overdue` and `dueSoon` flags. `status` may repeat; an unknown status,
   `due` value or an `assignee` that is neither `me` nor a user id is `400`. `due` keeps open tasks only.
   **ASSUMPTION:** `TASK_VIEW` shows every task of the office, and the case reference and title of case tasks.
3. Timeline (case tasks only): `TASK_CREATED`, `TASK_ASSIGNED` (assignee changed), `TASK_COMPLETED`, `TASK_CANCELLED`,
   `TASK_REOPENED`. Other edits add no entry.
4. **ASSUMPTION:** any `TASK_MANAGE` holder may change any task (a small office shares the work); tasks are never
   deleted (cancel instead); edits are allowed in every status. A disabled assignee may stay on a task until it is
   reassigned. Changes to a task of an `ARCHIVED` case are `422 CASE_ARCHIVED`. Writes lock the task row and check
   `version` (409).
5. **Report port:** `com.lawdesk.tasks.TaskReportQueries`: `find(TaskFilter, offset, limit)` (≤ 10 000 rows),
   `count`, `countByStatus` (every status, zero-filled), `overdueCount`, `dueSoonCount`, `workload()` (open, in
   progress and overdue per assignee) and `today()`. "Open" (`TaskStatus.OPEN_STATES`), "overdue" (open and due before
   today) and "due soon" (open and due today…today + `DUE_SOON_DAYS`) are defined there.
6. Screen `/tasks`: "My tasks" and "All tasks", filters by status and due state, create, edit, change status; the
   case's tasks in `CaseOperations`.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0209 | authorized happy path | `TaskApiTest`: create (case and office tasks), read, edit with reassignment, status lifecycle, list order and filters | AUTOMATED |
| QA-0210 | required-field validation | `TaskApiTest.requiredFieldsAreValidated` | AUTOMATED |
| QA-0211 | invalid input boundary | `TaskApiTest.boundariesOfTextDueDateAndAssignee`, `anUnchangedPastDueDateAndADisabledAssigneeMayStay`, `anArchivedCaseTakesNoTaskChanges` | AUTOMATED |
| QA-0212 | unauthenticated access | `TaskApiTest.everyEndpointNeedsASession` | AUTOMATED |
| QA-0213 | insufficient permission | `TaskApiTest.viewersCannotWriteAndOthersCannotRead`, `aRefusalIsAuditedAsAccessDenied` | AUTOMATED |
| QA-0214 | record integrity and rollback | `TaskApiTest.aFailingTimelineEventRollsTheWriteBack`, `unknownTasksAreNotFoundAndTheDatabaseKeepsStatusesConsistent` | AUTOMATED |
| QA-0215 | concurrent or repeat request | `TaskApiTest.aStaleVersionIsAConflictAndChangesNothing`; illegal repeated moves (`OPEN` → `OPEN`) → 422 | AUTOMATED |
| QA-0216 | API-to-UI state and error handling | `TasksPage.test.tsx`, `CaseTasks.test.tsx`, `routes.test.tsx`; real-browser layout `LD-027-07-layout-375-1366.log` | AUTOMATED |

## LD-028 Calendar and deadline reminders

1. Reminders are in-app only (§8.3, **ASSUMPTION**: no e-mail or SMS): due-soon and overdue lists and counts.
2. `GET /api/v1/tasks/reminders` (`TASK_VIEW`) answers, for the signed-in user's open tasks, `{ today, dueSoonDays,
   overdue, dueSoon }` counts. **ASSUMPTION:** "due soon" is due today or within the next 3 days.
3. The `/tasks` screen opens with a reminder banner (overdue and due-soon counts, each linking to its filtered list),
   overdue rows are marked, and the `/hearings` calendar is the calendar of court dates. **ASSUMPTION:** an agenda list
   for a date range stands in for a month grid in v1.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0217 | authorized happy path | `TaskReminderApiTest.countsTheSignedInUsersOverdueAndDueSoonTasks` (counts and the `due` lists) | AUTOMATED |
| QA-0218 | required-field validation | Reminders take no input; the `due` filter refuses unknown values (`TaskApiTest.listsTasksByDueDateThenPriorityWithFilters`) | AUTOMATED |
| QA-0219 | invalid input boundary | Boundaries of "due soon" (today and today + 3 counted, + 4 not) and "overdue" (yesterday counted, done tasks not) | AUTOMATED |
| QA-0220 | unauthenticated access | `TaskReminderApiTest.remindersNeedTaskView` | AUTOMATED |
| QA-0221 | insufficient permission | `TaskReminderApiTest.remindersNeedTaskView`; ACCESS_DENIED audited by the platform (LD-027 test) | AUTOMATED |
| QA-0222 | record integrity and rollback | Reminders only read; other users' tasks never counted (`countsTheSignedInUsersOverdueAndDueSoonTasks`) | AUTOMATED |
| QA-0223 | concurrent or repeat request | Repeated reads are idempotent; `aUserWithoutTasksHasNoReminders` | AUTOMATED |
| QA-0224 | API-to-UI state and error handling | `TasksPage.test.tsx`: banner counts, links to the filtered lists, "none" texts, unavailable reminders, Arabic | AUTOMATED |

## LD-026 Legal PDF attachments, up to ten per case

1. `POST /api/v1/cases/{caseId}/documents` (`DOCUMENT_MANAGE`, `multipart/form-data`: `file`, optional `title` ≤ 200
   characters, defaulting to the file name) stores one PDF in PostgreSQL (`bytea` in its own table, §8.3): 201,
   `Location`, the metadata (`id`, `caseId`, `caseReferenceNo`, `caseTitle`, `title`, `fileName`, `mediaType`,
   `sizeBytes`, `sha256`, `version`, `createdAt`, `createdBy`) and never the content. Timeline: `DOCUMENT_ATTACHED`.
   An unknown case is 404, an `ARCHIVED` case `422 CASE_ARCHIVED`; a body that is not multipart is 415.
2. A file is accepted only when its declared type is `application/pdf` and its content starts with `%PDF-`
   (`400`, field `file`, code `PdfOnly`); no file is `400 NotNull`, an empty file `400 NotEmpty`, a file over 10 MiB
   `400 MaxSize` (exactly 10 MiB is accepted). The container's multipart limit is the same 10 MiB
   (`spring.servlet.multipart.*`, the one `application.yaml` change §8.1 allows); parts are read lazily, so the
   permission is checked before the upload is parsed (QAD-026) and an oversized upload through the real container is
   a `400 MaxSize` problem, never a 500 or a broken connection. Uploads need the CSRF token like every unsafe request.
3. At most 10 documents per case: the 11th is `422 DOCUMENT_LIMIT_REACHED`. The count is checked while holding a lock on
   a per-case row (`case_document_quota`, `select ... for update`), so two concurrent uploads to a case holding nine
   store exactly one; removals take the same lock.
4. `GET /api/v1/cases/{caseId}/documents` (`DOCUMENT_VIEW`) lists the case's metadata, newest first;
   `GET /api/v1/documents` (`DOCUMENT_VIEW`, paged) the latest documents of every case for the documents screen;
   `GET /api/v1/documents/{id}` one document. `GET /api/v1/documents/{id}/content` (`DOCUMENT_VIEW`) answers the bytes
   with `Content-Type: application/pdf`, `Content-Disposition: attachment` (UTF-8 file name),
   `X-Content-Type-Options: nosniff` and `Cache-Control: no-store`. **ASSUMPTION:** every download is recorded in the
   security audit log as `DOCUMENT_DOWNLOAD` (actor, document id, no content).
5. `DELETE /api/v1/documents/{id}?version=` (`DOCUMENT_MANAGE`) removes the file: 204; a missing `version` is `400`, a
   stale one 409. Timeline: `DOCUMENT_REMOVED`. **ASSUMPTION:** removal deletes the bytes (the slot is freed); the
   timeline keeps that it existed. Documents are not edited (remove and attach again).
6. The file name is reduced to its last path segment, control characters removed, at most 255 characters
   (`document.pdf` when nothing is left).
7. Screens: `/documents` (ready, `DOCUMENT_VIEW`) lists the latest documents of every case with download links; the
   case's documents section in `CaseOperations` shows "n of 10", downloads, removal after confirmation and an upload
   form that checks type and size in the browser first (the backend checks again).
8. **ASSUMPTION:** no report port for documents in v1 (Phase 6 needs none per §8.3).

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0201 | authorized happy path | `DocumentApiTest`: attach (201, `Location`, sha256, timeline), read, list per case and across cases, download headers and audit, remove | AUTOMATED |
| QA-0202 | required-field validation | `DocumentApiTest.aFileIsRequiredAndMustNotBeEmpty` (no file, empty file, JSON body 415) | AUTOMATED |
| QA-0203 | invalid input boundary | `onlyPdfFilesUpToTenMebibytesAreAccepted` (type, magic bytes, 10 MiB ± 1, title 201), `fileNamesAreReducedToASafeLastSegment`, `aCaseHoldsAtMostTenDocuments`; `DocumentUploadOverHttpTest` (real container limit) | AUTOMATED |
| QA-0204 | unauthenticated access | `DocumentApiTest.everyEndpointNeedsASession` | AUTOMATED |
| QA-0205 | insufficient permission | `viewersCannotWriteAndOthersCannotRead` (with the `ACCESS_DENIED` audit row), `anUploadWithoutTheCsrfTokenIsRefused`, `DocumentUploadOverHttpTest.aUserWithoutTheManagePermissionIsRefusedBeforeTheBodyIsRead` | AUTOMATED |
| QA-0206 | record integrity and rollback | `aFailingTimelineEventStoresNothing`, `anArchivedOrUnknownCaseTakesNoDocuments`, database checks on type, size and hash | AUTOMATED |
| QA-0207 | concurrent or repeat request | `twoConcurrentUploadsToACaseHoldingNineStoreExactlyOne`; stale removal 409 | AUTOMATED |
| QA-0208 | API-to-UI state and error handling | `CaseDocuments.test.tsx`, `DocumentsPage.test.tsx`, `routes.test.tsx`; real-browser layout `LD-026-07-layout-375-1366.log` | AUTOMATED |

## LD-025 Expert referrals and sessions

1. Expert registry (reference data, like courts): `name` (required, ≤ 200), `specialty` (required, ≤ 100, for example
   engineering, accounting, agriculture), `office` (optional, ≤ 200, for example the Ministry of Justice experts office
   of a governorate — مكتب خبراء وزارة العدل), `phone` (optional, ≤ 32 digits, spaces and `+ ( ) -`), `active`.
   `GET /api/v1/experts?active=&q=` (`EXPERT_VIEW`, by name; `q` searches name and specialty), `GET
   /api/v1/experts/{id}`, `POST` / `PUT` (`EXPERT_MANAGE`, `PUT` with `version`, 409 when stale). **ASSUMPTION:** no
   deletion (deactivate instead), no experts are seeded, and names need not be unique.
2. A referral (ندب خبير) belongs to a proceeding: `POST /api/v1/proceedings/{id}/expert-referrals` (`EXPERT_MANAGE`)
   with `expertId` (an active expert, `422 EXPERT_INACTIVE`; unknown 404), `referralDate` (required; not in the future,
   `400 PastOrPresent`; not before the filing date, `422 REFERRAL_BEFORE_FILING`), `mission` (optional, ≤ 4000) and
   `reportDueDate` (optional, not before the referral date, `422 REPORT_DUE_BEFORE_REFERRAL`). The case is taken from
   the proceeding and the database refuses a mismatch (trigger, as for hearings). `status` is `OPEN` →
   `REPORT_SUBMITTED` (`POST …/report` with `reportSubmittedDate`: not in the future, not before the referral,
   `422 REPORT_BEFORE_REFERRAL`) or `CANCELLED` (`POST …/cancel`). `PUT /api/v1/expert-referrals/{id}` changes the
   mission and the due date of an open referral. Every change takes `version` (409 when stale) and locks the row.
3. Expert sessions (محاضر أعمال الخبير): `POST /api/v1/expert-referrals/{id}/sessions` with `sessionDate` (required, not
   before the referral date, `422 SESSION_BEFORE_REFERRAL`), optional `sessionTime` (`HH:mm`), `location` (≤ 200) and
   `notes` (≤ 2000); 201 with the referral, whose version it bumps. A referral that is no longer open accepts no
   sessions, report, cancellation or edit (`422 REFERRAL_CLOSED`); an `ARCHIVED` case none (`422 CASE_ARCHIVED`).
4. `GET /api/v1/cases/{caseId}/expert-referrals` (`EXPERT_VIEW`) lists the case's referrals by referral date, each with
   its sessions (oldest first), the expert's name and specialty, the proceeding's number and `reportOverdue` (open and
   due before today). `GET /api/v1/expert-referrals/{id}` reads one.
5. Timeline: `EXPERT_REFERRED`, `EXPERT_SESSION_RECORDED`, `EXPERT_REPORT_SUBMITTED`, `EXPERT_REFERRAL_CANCELLED`
   (names, dates and numbers only, never the mission or notes).
6. **Report port:** `com.lawdesk.experts.ExpertReportQueries`: `find(ReferralFilter, offset, limit)` (≤ 10 000 rows),
   `count`, `countByStatus` (zero-filled), `overdueReportCount()`, `workload()` (open and overdue referrals per expert
   of the registry) and `today()`; "overdue" is defined there.
7. QAD-032 applied here: correcting a proceeding's filing date to after one of its referrals (cancelled ones count,
   **ASSUMPTION**) is `422 FILING_AFTER_DEPENDENT_REFERRAL` with nothing written (port `ProceedingDependentDates`), and
   a referral takes the proceeding row lock (`ProceedingLookup#lockForUpdate`) before it compares its date with the
   filing date, so the two writes are serialised. Sessions and reports are bound by the referral date, so they follow.
   Tests: `ExpertFilingChronologyTest` (direct edit and two races, both orders). Evidence:
   `LD-025-10-red-QAD-032-referrals.log`, `LD-025-11-green-QAD-032-referrals.log`. Checked and not affected: tasks
   (due dates are bound by today only), documents and notes (no date bound to the proceeding or the case).
   QAD-033: the database enforces the same dates for any writer (migration
   `V407__LD-025_expert_dates_database_integrity.sql`, with `V406` for hearings and the case): a referral before the
   proceeding's filing date (`expert_referral_not_before_filing`), a filing date after a referral
   (`proceeding_filing_not_after_referral`, cancelled referrals count) and a session before its referral or a referral
   moved after its first session (`expert_session_not_before_referral`) are refused with a check violation (23514)
   and nothing changed. The referral and session triggers read the proceeding and referral rows `FOR SHARE`, so two
   SQL writers are serialised like the API. Tests: `ExpertDatabaseIntegrityTest` (including both race orders) and
   `MigrationPhase5IntegrityTest`, which migrates a database holding hearings, referrals and sessions written through
   V405 to the latest version and checks that no row is rewritten (same `xmin`) or rejected and that protection starts
   at once. Evidence: `LD-025-16-red-QAD-033-expert-database.log`, `LD-025-17-red-QAD-033-migration-on-existing-data.log`,
   `LD-025-18-green-QAD-033-expert-database.log`.
8. Screens: `/experts` (ready, `EXPERT_VIEW`) is the registry with status filter, search, create, edit and deactivate;
   the case's expert referrals in `CaseOperations` refer a proceeding to an active expert, record sessions and the
   report, and cancel a referral after confirmation.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0193 | authorized happy path | `ExpertApiTest.registersListsAndEditsExperts`, `ExpertReferralApiTest.refersAProceedingToAnExpertWithSessionsAndAReport`, `aReferralCanBeCancelledAndThenTakesNoMoreWork` | AUTOMATED |
| QA-0194 | required-field validation | `ExpertApiTest.validatesTheRegistryFields`, `ExpertReferralApiTest.validatesReferralsSessionsAndReports` | AUTOMATED |
| QA-0195 | invalid input boundary | Name 200/201, specialty 100/101, phone pattern, mission 4001, future dates, chronology rules (same tests) | AUTOMATED |
| QA-0196 | unauthenticated access | `ExpertApiTest.theRegistryNeedsItsPermissions`, `ExpertReferralApiTest.referralEndpointsNeedASessionAndThePermission` | AUTOMATED |
| QA-0197 | insufficient permission | Same tests, with the `ACCESS_DENIED` audit row | AUTOMATED |
| QA-0198 | record integrity and rollback | `aFailingTimelineEventRollsTheWriteBackAndTheDatabaseKeepsReferralsConsistent`, `anInactiveExpertUnknownRecordsAndArchivedCasesAreRefused` | AUTOMATED |
| QA-0199 | concurrent or repeat request | `ExpertReferralApiTest.aStaleVersionIsAConflictAndChangesNothing`, stale expert edit 409; closed referrals refuse repeats | AUTOMATED |
| QA-0200 | API-to-UI state and error handling | `ExpertsPage.test.tsx`, `CaseExperts.test.tsx`, `routes.test.tsx`; real-browser layout `LD-025-07-layout-375-1366.log` | AUTOMATED |

## LD-029 Legal notes and activity history

1. A note belongs to a case: `body` (required, ≤ 4000, trimmed). `POST /api/v1/cases/{caseId}/notes` (`NOTE_MANAGE`,
   201, `Location`), `PUT /api/v1/notes/{id}` (`NOTE_MANAGE`, with `version`, 409 when stale),
   `DELETE /api/v1/notes/{id}?version=` (`NOTE_MANAGE`, 204; a missing version is 400),
   `GET /api/v1/cases/{caseId}/notes` (`CASE_VIEW`, newest first, paged). Answers carry the author's id and display
   name, `editable` (the signed-in user wrote it) and `edited`. `NOTE_MANAGE` alone does not open notes (§8.2).
2. **ASSUMPTION:** only the author changes or deletes a note (`422 NOTE_AUTHOR_ONLY`); notes are internal to the office
   and their text never enters the timeline; an `ARCHIVED` case takes no note changes (`422 CASE_ARCHIVED`). Removal
   deletes the note.
3. Timeline: `NOTE_ADDED`, `NOTE_REMOVED`, naming the author only. The activity history of a case is its timeline
   (LD-014, `GET /api/v1/cases/{id}/timeline`), which every Phase 5 module feeds through `CaseLookup#recordEvent`
   (`ActivityHistoryTest`: one case with a hearing, a task, a document, an expert referral and a note shows all seven
   events newest first). The case details page shows that timeline already (Workstream B); the labels of the Phase 5
   event types come with the integration owner's PR #45 (see "Dependencies"); before it they read "other event", with
   their summary.
4. Screen: the case's legal notes in `CaseOperations`: write, edit and remove own notes (removal after confirmation),
   with a hint that notes stay internal.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0225 | authorized happy path | `NoteApiTest.writesReadsEditsAndRemovesALegalNote`, `ActivityHistoryTest` | AUTOMATED |
| QA-0226 | required-field validation | `NoteApiTest.theBodyIsRequiredAndBounded` | AUTOMATED |
| QA-0227 | invalid input boundary | Body 4000/4001, blank body, unknown case or note (same test) | AUTOMATED |
| QA-0228 | unauthenticated access | `NoteApiTest.notesNeedASessionAndTheirPermissions` | AUTOMATED |
| QA-0229 | insufficient permission | Same test, with the `ACCESS_DENIED` audit row; `onlyTheAuthorChangesANoteAndArchivedCasesTakeNone` | AUTOMATED |
| QA-0230 | record integrity and rollback | `aFailingTimelineEventRollsTheNoteBackAndStaleVersionsConflict`, database check on blank bodies | AUTOMATED |
| QA-0231 | concurrent or repeat request | Stale edit and removal → 409 (same test) | AUTOMATED |
| QA-0232 | API-to-UI state and error handling | `CaseNotes.test.tsx`; real-browser layout `LD-029-07-layout-375-1366.log` | AUTOMATED |

## Dependencies

- `CaseOperations.tsx` is mounted on the case details page by one line in `CaseDetailsPage.tsx` (§8.1), added in the
  LD-024 pull request after the mobile fix QAD-027 (PR #29) reached `develop`.
- The shell tests see every navigation area because the shared test administrator (`src/test/api.ts`) holds every
  permission (integration owner, contracts branch `c11bd48`); each Phase 5 item is flipped with its working screen.
- QAD-032 integration exception (authorized by the integration owner): `com.lawdesk.proceedings` gained the port
  `ProceedingDependentDates`, its call in the filing-date correction and `ProceedingLookup#lockForUpdate`; the
  proceedings messages gained the rule's text. Nothing else of Workstream C changed.
- Timeline labels in `features/cases` (Workstream B files): added by the integration owner in PR #45
  (`eventLabel(type)`, en/ar) for
  the Phase 5 timeline types `HEARING_SCHEDULED`, `HEARING_RESCHEDULED`, `HEARING_HELD`,
  `HEARING_ADJOURNED`, `HEARING_CANCELLED`, `TASK_CREATED`, `TASK_ASSIGNED`, `TASK_COMPLETED`, `TASK_CANCELLED`,
  `TASK_REOPENED`, `DOCUMENT_ATTACHED`, `DOCUMENT_REMOVED`, `EXPERT_REFERRED`, `EXPERT_SESSION_RECORDED`,
  `EXPERT_REPORT_SUBMITTED`, `EXPERT_REFERRAL_CANCELLED`, `NOTE_ADDED`, `NOTE_REMOVED`.
- Uploading files from the browser uses `src/api/http.ts` with `FormData` bodies, added by the integration owner
  (branch `feature/LD-026-http-multipart`, PR #38), which the LD-026 branch merges.
