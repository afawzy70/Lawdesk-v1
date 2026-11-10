# DOC-R3-BRIEF: the case brief (records-only answers for section 22 rows)

Endpoint: `GET /api/v1/cases/{caseId}/brief[?similarLimit=1..25]` (permission `CASE_VIEW`; backend `com.lawdesk.casebrief`).
Screen: "Case brief" panel on the case page (`frontend/src/features/casebrief`), Arabic (RTL) and English.
Second task: "Judgments similar to a defence" in the defence area (`frontend/src/features/pleadings/DefenseSimilarJudgments.tsx`).

## What this is, and is not

- Deterministic queries over the system's own records. No model, no AI provider, no network call, no new dependency.
  Every response says `origin: RECORDS`, `modelGenerated: false` and carries the notice "derived from the case records by
  fixed rules, not model-generated". The legal assistant is unchanged: still a disabled, non-production test mode for real data.
- It does NOT satisfy the model-based assistant. Natural-language questions, abstractive summaries, extraction from document
  text, drafting, comparison of documents and every other model task of section 22 stay BLOCKED on D8 (provider decision).
- Every item carries its source (`source.type`, `id`, `date`, and `documentId`/`documentTitle` when the record cites a document,
  the title only for users with `DOCUMENT_VIEW`). `source.page` is always null: none of these records stores a page number, and
  the screen prints "page: not recorded".
- Where the records do not say, the answer is `NOT_RECORDED` (section or field). A part the user may not read is
  `NOT_PERMITTED`, not empty. No legal period is computed, no required-documents checklist is invented, no ruling is inferred.
- Access: `CaseAccessPolicy.requireView` runs first, before the `similarLimit` check (out of scope or RESTRICTED without
  membership/grant: 404 even for an invalid parameter). Each part then needs the permission of the screen that owns its records.

## Rows

| Row | Answered by (brief section) | Tests | Honest limits |
|---|---|---|---|
| LD-DOC-22-004 timeline of procedures | `timeline`: filings, hearings, judgments, special decisions, dated requests/defences, completed deadlines, ordered by date | `CaseBriefApiTest.theBriefAnswersFromTheRecords...`, `CaseBriefPanel.test.tsx` | Only dated records are placed; undated requests/defences appear in their own sections. Records, not a narrative. |
| LD-DOC-22-008 position of each request and each defence | `requests`, `defenses`: latest recorded ruling by judgment date (from `claim_result` / `defense_position`), all rulings as children | same | The position is what members recorded on the judgment; if none, `NOT_RECORDED`. Nothing is read from judgment text. |
| LD-DOC-22-009 last action and next required action | `lastAction` (latest dated act up to today; all records of that day), `nextHearing`, `nextRequiredActions` (required action recorded on the last held/adjourned hearing + open deadlines by stored due date) | same | "Required action" exists only where a member recorded it; otherwise `NOT_RECORDED`. |
| LD-DOC-22-039 last action | `lastAction` | same | As above. |
| LD-DOC-22-040 requests the court rejected | `rejectedRequests` (any ruling `REJECTED`, current position shown) | same | Depends on rulings having been recorded per request. |
| LD-DOC-22-041 defences not ruled on | `defensesNotRuledOn` (latest position `NOT_ADDRESSED` or `DEFERRED`); defences with no recorded position show `NOT_RECORDED` in `defenses` | same | A defence with no position recorded is not claimed to be unruled; it is reported as not recorded. |
| LD-DOC-22-042 missing documents | `missingFiles`: judgment with no copy attached, copy withdrawn, evidence item with no file or a withdrawn file | `...theBriefAnswers...`, `...aCopyThatWasWithdrawn...` | PARTIAL by design: only facts derivable from records. No checklist of required documents exists or is invented. Hearings/proceedings do not reference documents, so they cannot be reported. |
| LD-DOC-22-043 existing appeal deadlines | `appealDeadlines` (the deadline each judgment names via `appeal_deadline_id`), `openDeadlines` | `...theBriefAnswers...`, `...whatTheRecordsDoNotHold...` | Stored dates only; a judgment with no named deadline shows `NOT_RECORDED` with `appealable`/`notifiedOn` as recorded. No period is calculated. |
| LD-DOC-22-044 similar earlier judgments | `similarJudgments` (reuses `LibraryReadPort.similarTo` = `SimilarJudgmentService`), `linkedJudgments` | `SimilarJudgmentApiTest` (existing), brief test | Fixed word/classification comparison, not understanding of meaning; needs `LIBRARY_VIEW`. |
| LD-DOC-22-045 actions after the last hearing | `afterLastHearing`: the last held/adjourned hearing's recorded required action, plus deadlines that run from that hearing | `...theBriefAnswers...`, `...aHearingWithNoRecordedRequiredActionSaysSo` | Hearing with no recorded action shows an explicit `NOT_RECORDED` item. |
| LD-DOC-22-024..031 (case data, facts, opponents' requests, our position, defences, evidence, legal texts, related judgments) | Dossier sections `caseData`, `facts`, `requests`, `ourPosition`, `defenses`, `evidence`, `legalTexts`, `linkedJudgments`/`similarJudgments` | `CaseBriefApiTest`, `CaseBriefPanel.test.tsx` | PARTIAL: these rows are about drafting a first memo from those inputs. Only the input dossier is delivered; no draft is generated (blocked on D8). "Our position" is the defences as recorded. |
| LD-DOC-16-043 similar judgments at memo time | Pick a defence, then `GET /cases/{id}/similar-judgments?defenseId=` | `GapClosureApiTest.similarJudgmentsAreFoundFromADefenceAlone...` (backend, existing), `DefenseSimilarJudgments.test.tsx` | Shown on demand in the defence area; it is not pushed automatically when a new case is registered. |

Not covered and not claimed: 22-003, 22-005, 22-006, 22-007 and every row needing natural-language questions, summarising,
extraction from document text, drafting or document comparison.

## Backend tests (`CaseBriefApiTest`, package `com.lawdesk.pleadings`)

Full brief with source ids; NOT_RECORDED for silent records and for an empty case; hearing without a recorded action; withdrawn
copy and document title masking; NOT_PERMITTED parts and 403 without `CASE_VIEW`; out-of-scope user gets 404; RESTRICTED case hidden,
opened by a grant, closed again on revocation; access before validation (`similarLimit=0` is 404 for an outsider, 400 for a member).

## Migration

None. All data was already stored (V621, V622, V660, V681, V843).
