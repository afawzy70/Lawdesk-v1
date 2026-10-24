# Phase 4 — refined acceptance criteria (PROPOSED, awaiting owner review)

Workstream C, judicial lifecycle (LD-018…LD-023). `product/stories.json` holds these stories with templated criteria
(`DRAFT_NEEDS_REFINEMENT`), and `qa/test-cases.json` holds generic scenarios QA-0137…QA-0184. This file proposes
concrete, testable criteria, in the style of `PHASE-1-ACCEPTANCE.md`, and maps each QA case to an executable check.
The baseline files are not edited here.

Every rule marked **ASSUMPTION** is a product decision taken by the developer so that work can start; the owner
confirms or changes it. Status values: `AUTOMATED` (a test exists and was run by the developer), `PLANNED` (criterion
agreed, test not written yet), `BLOCKED` (cannot be executed yet, reason given). Developer runs are not independent QA:
every case stays `NOT_RUN` in `qa/test-cases.json` until QA executes it.

## Common rules (all Phase 4 endpoints)

1. Every endpoint lives under `/api/v1`, answers JSON in camelCase, and follows `architecture/CONTRACTS.md` §4:
   400 with `errors[{field, code}]` for bean validation (no rejected values echoed), 404 for an unknown id, 409 for a
   duplicate or a stale `version`, 422 with a stable `rule` code for a refused business rule, 500 without details.
2. Without a session the answer is 401; without the permission it is 403; both are `application/problem+json`. Unsafe
   requests need the CSRF token. Permissions: `COURT_VIEW` / `COURT_MANAGE` for courts, `PROCEEDING_VIEW` /
   `PROCEEDING_MANAGE` for proceedings, `JUDGMENT_VIEW` / `JUDGMENT_MANAGE` for judgments and special decisions.
3. Writes record the acting user (`CurrentActor`) in `created_by` / `updated_by` and the time in `created_at` /
   `updated_at` (UTC). Editable records carry `version`; an update with an older `version` gets 409 and changes
   nothing.
4. Lists are `PageResponse` (`page`, `size` default 20, capped at 100). **ASSUMPTION:** unknown `sort` fields are
   ignored and the default order applies, instead of a 400.
5. Recording security events for refused access (the stories' third criterion) belongs to the audit log of Phase 2
   (`com.lawdesk.audit.AuditLog`, workstream A). Phase 4 checks the refusal itself; the audit entry is **BLOCKED**
   until A delivers the audit log and the security layer that writes it.

## LD-018 Court and judicial reference data

Acceptance criteria:

1. A court has a `code` (2–32 letters, digits or hyphens, stored upper-case, unique ignoring case), `nameAr` and
   `nameEn` (required, 1–200 characters, not only whitespace), a `level` of `FIRST_INSTANCE` (المحاكم الابتدائية
   والجزئية), `APPEAL` (محاكم الاستئناف) or `CASSATION` (محكمة النقض), an optional `governorate` (≤ 100 characters)
   and an `active` flag (new courts are active).
2. `GET /api/v1/courts` (`COURT_VIEW`) lists courts ordered by code, filterable by `level` and `active`.
   `GET /api/v1/courts/{id}` returns one court or 404.
3. `POST /api/v1/courts` (`COURT_MANAGE`) creates a court: 201, `Location`, the stored representation. A code already
   used, in any letter case, is 409 and stores nothing.
4. `PUT /api/v1/courts/{id}` (`COURT_MANAGE`) replaces code, names, level, governorate and `active`, with `version`.
   Courts are never deleted: they are deactivated (`active: false`), so proceedings that reference them keep their
   history. **ASSUMPTION:** there is no `DELETE` endpoint; reactivation is allowed.
5. **ASSUMPTION:** no courts are seeded. The real list of Egyptian courts is reference data the owner supplies or
   enters; the system ships no invented courts presented as real ones.
6. **ASSUMPTION:** the governorate is free text in this phase (no governorate reference list).
7. The administration screen (`/courts`, Arabic and English) lists courts with level and status, filters by level and
   status, and creates and edits courts; validation errors appear next to their field, a duplicate code and a stale
   version show an actionable message, and a 403 shows a "not permitted" message instead of the form.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0137 | authorized happy path | `CourtApiTest`: create → 201 + `Location`, read, list with filters, update, deactivate | AUTOMATED |
| QA-0138 | required-field validation | `CourtApiTest`: missing code, names, level → 400 with field codes | AUTOMATED |
| QA-0139 | invalid input boundary | `CourtApiTest`: 201-character name, malformed code, unknown level, whitespace-only name → 400; 200 characters accepted | AUTOMATED |
| QA-0140 | unauthenticated access | `CourtApiTest`: every endpoint without a session → 401 | AUTOMATED |
| QA-0141 | insufficient permission | `CourtApiTest`: `COURT_VIEW` cannot create or update (403); without `COURT_VIEW` reads → 403. Audit entry BLOCKED (A) | AUTOMATED (refusal); audit BLOCKED |
| QA-0142 | record integrity and rollback | `CourtApiTest`: duplicate code → 409, nothing stored; database unique constraint on the stored upper-case code (format check refuses lower case); refused update leaves the row unchanged | AUTOMATED |
| QA-0143 | concurrent or repeat request | `CourtApiTest`: stale `version` → 409; repeated create with the same code → 409 | AUTOMATED |
| QA-0144 | API-to-UI state and error handling | `CourtsPage.test.tsx`: list, empty state, filters, create, edit, field errors, 409, 403, unreachable API, Arabic | AUTOMATED |

## LD-019 First-instance proceedings

Acceptance criteria:

1. A proceeding belongs to one case (`caseId`, read through `com.lawdesk.cases.CaseLookup`, owned by B) and has a
   `stage` (`FIRST_INSTANCE`, `APPEAL`, `CASSATION`), a `courtId`, the case number and year at the court
   (`courtCaseNumber`, 1–32 characters; `courtCaseYear`, 1900–2100 — رقم الدعوى / السنة), a `filingDate` (not in the
   future) and, for appeals and cassations, a `parentProceedingId`.
2. `POST /api/v1/cases/{caseId}/proceedings` (`PROCEEDING_MANAGE`) records a proceeding: 201, `Location`. An unknown
   case or court is 404. `GET /api/v1/cases/{caseId}/proceedings` (`PROCEEDING_VIEW`) lists the case's proceedings in
   stage order, then filing date. `GET` / `PUT /api/v1/proceedings/{id}` reads or updates court, number, year and
   filing date (stage and parent are fixed once recorded).
3. Business rules, answered with 422 and `rule`:
   - `COURT_LEVEL_MISMATCH`: the court's level must equal the proceeding's stage. **ASSUMPTION** (first-instance
     cases are heard by partial or primary courts, appeals by an appeal court or an appellate circuit of a primary
     court, which the owner may want to model as a `FIRST_INSTANCE` court hearing appeals — confirm).
   - `COURT_INACTIVE`: a deactivated court cannot receive a new proceeding.
   - `FIRST_INSTANCE_HAS_NO_PARENT`: a first-instance proceeding has no parent.
   - `FILING_AFTER_DEPENDENT_HEARING` (QAD-032, Phase 5): a filing date cannot be corrected to after a hearing of the
     proceeding (LD-024); records of other modules that depend on the filing date are asked through the port
     `ProceedingDependentDates` while the proceeding row is locked (docs/delivery/PHASE-5-ACCEPTANCE.md, LD-024.7).
4. **ASSUMPTION:** the court number and year are unique per court and stage (`courtId`, `courtCaseYear`,
   `courtCaseNumber`, `stage`); a second record is 409.
5. **ASSUMPTION:** a case may hold more than one first-instance proceeding (for example a counterclaim filed as its
   own suit).
6. **ASSUMPTION:** the case's status (B, LD-017) does not restrict recording proceedings in this phase.
7. Recording a proceeding adds a case timeline entry through `CaseLookup#recordEvent(caseId, "PROCEEDING_RECORDED",
   summary)`, in the same transaction: if recording the event fails, the proceeding is not stored.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0145 | authorized happy path | `ProceedingApiTest`: create, list, read, update; timeline event recorded | AUTOMATED |
| QA-0146 | required-field validation | `ProceedingApiTest.requiredFieldsAreValidated` | AUTOMATED |
| QA-0147 | invalid input boundary | `ProceedingApiTest.boundariesOfNumberYearAndFilingDate`, court level and active rules | AUTOMATED |
| QA-0148 | unauthenticated access | `ProceedingApiTest.everyEndpointNeedsASession` | AUTOMATED |
| QA-0149 | insufficient permission | `ProceedingApiTest.viewersCannotWriteAndOthersCannotRead`. Audit BLOCKED (A) | AUTOMATED (refusal); audit BLOCKED |
| QA-0150 | record integrity and rollback | Unknown case/court → 404, nothing stored; a failing timeline event rolls the proceeding back; database stage/parent check | AUTOMATED |
| QA-0151 | concurrent or repeat request | Duplicate number/year → 409; stale version → 409 | AUTOMATED |
| QA-0152 | API-to-UI state and error handling | `CaseProceedings.test.tsx`: ladder display, empty state, 401/403/404, retry, record first instance, field errors, 409, 422 | AUTOMATED |

## LD-020 Appeal proceedings

Acceptance criteria:

1. An appeal is a proceeding with `stage: APPEAL` and a `parentProceedingId`.
2. Rules (422): `PARENT_PROCEEDING_REQUIRED` (no parent given); `PARENT_PROCEEDING_OTHER_CASE` (the parent belongs to
   another case); `APPEAL_PARENT_NOT_FIRST_INSTANCE` (the parent is not a first-instance proceeding);
   `APPEAL_REQUIRES_FIRST_INSTANCE_JUDGMENT` (the parent has no judgment yet); `COURT_LEVEL_MISMATCH` (court not of
   level `APPEAL`); `FILED_BEFORE_PARENT_JUDGMENT` (filed before the judgment it attacks; the same day is allowed).
   An unknown parent is 404.
3. **ASSUMPTION:** the statutory appeal deadline (ميعاد الاستئناف, for example 40 days under the Code of Civil and
   Commercial Procedure) is **not enforced** in this phase: the filing date is recorded only, and a late appeal is
   accepted.
4. **ASSUMPTION:** one first-instance judgment may be appealed by more than one appeal (for example by each party).
5. **ASSUMPTION:** a judgment marked final (`final: true`) can still be the parent of an appeal record; finality is
   informational in this phase.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0153…QA-0160 | the eight standard scenarios | Backend: `StageRulesTest` (appeal cases) and the shared `ProceedingApiTest` checks; UI: `CaseProceedings.test.tsx` | Backend and UI AUTOMATED |

## LD-021 Cassation proceedings

Acceptance criteria:

1. A cassation (طعن بالنقض) is a proceeding with `stage: CASSATION` whose parent is an appeal proceeding of the same
   case that has a judgment; the court is of level `CASSATION`.
2. Rules (422): `PARENT_PROCEEDING_REQUIRED`, `PARENT_PROCEEDING_OTHER_CASE`, `CASSATION_PARENT_NOT_APPEAL`,
   `CASSATION_REQUIRES_APPEAL_JUDGMENT`, `COURT_LEVEL_MISMATCH`, `FILED_BEFORE_PARENT_JUDGMENT`.
3. **ASSUMPTION:** the cassation deadline (ميعاد الطعن بالنقض, 60 days) is not enforced; the filing date is recorded.
4. **ASSUMPTION:** direct cassation of a first-instance judgment (where the law allows it) is not supported in this
   phase.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0161…QA-0168 | the eight standard scenarios | Backend: `StageRulesTest` (cassation cases); UI: `CaseProceedings.test.tsx` | Backend and UI AUTOMATED |

## LD-022 Judgments and outcomes

Acceptance criteria:

1. A judgment belongs to one proceeding and has a `judgmentDate` (not in the future), an `outcome` (`IN_FAVOUR`,
   `AGAINST`, `PARTIAL`, `DISMISSED`, `OTHER`, from the client's point of view), an optional `summary`
   (≤ 4000 characters) and `final` (حكم نهائي/بات, default false).
2. `POST /api/v1/proceedings/{id}/judgment` (`JUDGMENT_MANAGE`) records it: 201, `Location`.
   `GET /api/v1/judgments/{id}`, `GET /api/v1/cases/{caseId}/judgments` (`JUDGMENT_VIEW`), `PUT /api/v1/judgments/{id}`
   (`JUDGMENT_MANAGE`, with `version`).
3. **ASSUMPTION:** one judgment ends a proceeding (the judgment that closes the instance, منهي للخصومة); a second
   judgment for the same proceeding is 409. Interim rulings are special decisions (LD-023).
4. Rule (422) `JUDGMENT_BEFORE_FILING`: the judgment date cannot be before the proceeding's filing date, when the
   judgment is recorded or corrected, and a proceeding's filing date cannot be moved after its judgment.
   Rule (422) `JUDGMENT_AFTER_CHALLENGE_FILING` (QAD-024): a judgment date cannot be corrected to a day after the
   filing date of any appeal or cassation that attacks it (the earliest one bounds it; the same day is allowed). The
   correction is refused as a whole: date, outcome, summary, finality and `version` stay unchanged. Dates are never
   rewritten and proceedings are never deleted to make the chronology fit. Together with `FILED_BEFORE_PARENT_JUDGMENT`
   (on create and on correcting a child's filing date) and `JUDGMENT_BEFORE_FILING`, every write keeps the chronology
   *filing ≤ judgment ≤ filing of each challenge*.
   Concurrency: every write that compares these dates first locks the proceeding row(s) involved with
   `select ... for update` (child before parent): recording or correcting a judgment locks its proceeding; recording
   an appeal or cassation locks its parent; correcting a proceeding locks it and then its parent. Two racing writes are
   therefore serialised and the second one sees the first one's committed dates. No migration is needed.
   Tests: `JudicialChronologyTest` — direct updates `aJudgmentDateCannotMovePastAnAppealFiledAgainstIt`,
   `anAppealJudgmentDateCannotMovePastACassationFiledAgainstIt`, `theEarliestOfSeveralAppealsBoundsTheJudgmentDate`
   (rollback: the stored row is compared before and after); valid corrections with existing appeals and cassations
   `aCorrectionThatKeepsTheChronologySucceeds` (200, version bumped); the other edit paths
   `anAppealsFilingDateCannotMoveBeforeTheJudgmentItAttacks`, `aParentProceedingsFilingDateStaysBoundByItsOwnJudgment`;
   races (one request paused inside its transaction by a test-only trigger, final database state checked)
   `anAppealFiledWhileTheJudgmentDateMovesLaterIsRefused`, `aJudgmentDateMovedWhileAnAppealIsFiledIsRefused`,
   `anAppealMovedEarlierWhileTheJudgmentDateMovesLaterIsRefused`, `aJudgmentDateMovedWhileTheAppealMovesEarlierIsRefused`,
   `aJudgmentRecordedWhileTheFilingDateMovesLaterIsRefused`. Evidence: `docs/evidence/PHASE-4/17-red-QAD-024-backend.log`,
   `18-green-QAD-024-backend.log` (numbers 11–16 are used by the stacked LD-023 branch).
5. Recording a judgment adds a timeline entry `JUDGMENT_RECORDED` through `CaseLookup#recordEvent`, in the same
   transaction. **ASSUMPTION:** corrections (updates) of proceedings and judgments add no timeline entry, and
   proceedings and judgments cannot be deleted in this phase.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0169…QA-0175 | backend scenarios | `JudgmentApiTest` | AUTOMATED (audit part of QA-0173 BLOCKED, A) |
| QA-0176 | API-to-UI state and error handling | `CaseProceedings.test.tsx`: judgment display, hidden without JUDGMENT_VIEW, record, 409, 422 | AUTOMATED |

## LD-023 Special judicial decisions and enforcement suspension

Acceptance criteria:

1. A special decision belongs to one proceeding and has a `decisionType` (`ENFORCEMENT_SUSPENSION` — وقف التنفيذ,
   `URGENT_MEASURE` — إجراء وقتي/مستعجل, `OTHER`), a `decisionDate` (not in the future), an `effect` (`GRANTED`,
   `PARTIALLY_GRANTED`, `REFUSED`), an optional `summary` (≤ 4000 characters) and, optionally, the `judgmentId` it
   affects.
2. `POST /api/v1/proceedings/{id}/special-decisions` (`JUDGMENT_MANAGE`), `GET /api/v1/cases/{caseId}/special-decisions`
   (`JUDGMENT_VIEW`), `PUT /api/v1/special-decisions/{id}` (`JUDGMENT_MANAGE`, with `version`).
3. Rules (422): `SUSPENSION_REQUIRES_JUDGMENT` (an enforcement suspension names the judgment whose enforcement it
   suspends); `DECISION_JUDGMENT_OTHER_CASE` (a named judgment belongs to the same case); `DECISION_BEFORE_FILING`
   (the decision date is not before the proceeding's filing date). An unknown judgment is 404. **ASSUMPTION:** a
   proceeding may hold several decisions, including repeated suspension requests.
   QAD-024 applied here: `DECISION_BEFORE_FILING` is also answered when a proceeding's filing date is corrected to a
   day after one of its decisions (the earliest one bounds it; nothing is written). Recording or correcting a decision
   locks its proceeding row (`select ... for update`) like judgments do, so a racing filing-date correction is
   serialised with it. Tests: `SpecialDecisionChronologyTest` (`theFilingDateCannotMoveAfterASpecialDecision` and three
   races). Evidence: `docs/evidence/PHASE-4/19-red-QAD-024-special-decisions.log`,
   `20-green-QAD-024-special-decisions.log`. **ASSUMPTION:** no date rule ties a decision to the judgment it names
   (a suspension may be dated before or after that judgment), so correcting a judgment date never touches decisions.
4. **ASSUMPTION:** an enforcement suspension is informational: it does not change the judgment record or block other
   entries. Its effect is shown next to the judgment it names.
5. Recording a decision adds a timeline entry `SPECIAL_DECISION_RECORDED`.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0177…QA-0183 | backend scenarios | `SpecialDecisionApiTest` | AUTOMATED (audit part of QA-0181 BLOCKED, A) |
| QA-0184 | API-to-UI state and error handling | `CaseProceedings.test.tsx`: decision display, record suspension, 400, 401, 422 | AUTOMATED |

## Dependencies

- `com.lawdesk.cases.CaseLookup` and the `legal_case` table (B, LD-012) are needed by LD-019…LD-023. The foreign key
  `proceeding.case_id → legal_case.id` is added in the first Phase 4 migration created after B's `V200` is on
  `develop`.
- Signing in on UAT (A, LD-007) is needed before the screens can be used there; tests sign in with `TestActors`.
- Audit entries for refused access (A, LD-011) — see common rule 5.

## Frontend embedding (PR C3)

`src/features/proceedings/CaseProceedings.tsx` exports `<CaseProceedings caseId="…" />`, the judicial stages of one
case, for the case details page of Workstream B. It is not routed on its own and no navigation entry is flipped:
`/hearings` and `/judgments` stay `planned` because their cross-case screens are not built in this phase.
