# DOC-WP2 — claims, judgment results, defences, facts and evidence (LD-051)

Story LD-051, migrations V620–V623, requirements sections 5, 9, 17 and 20 (matrix rows `LD-DOC-05-*`, `LD-DOC-09-*`,
`LD-DOC-17-*`, `LD-DOC-20-*`), scenarios S1, S2, S3, S8, S13. Branch `feature/LD-DOC-wp2-claims-judgments`, PR to `develop`.

## Requirements covered

| Requirement IDs | What exists | Where |
|---|---|---|
| 05-001…05-011 | Claims: own sequence number per case, text, kind (6), submitter (a party of the case), date, stage (a proceeding of the case), source document (a stored document of the case), status (5), notes. Amending keeps a full snapshot per revision (text, kind, status, all fields, reason, user, time); revisions are append-only in the database; a claim is never deleted | `claim`, `claim_revision`; `/api/v1/cases/{id}/claims`, `/api/v1/claims/{id}[/revisions]`; `ClaimApiTest` |
| 05-012…05-024 | The court's result on every claim in a judgment: the 12 results of the source, operative text, reasons, awarded amount (KWD, 3 decimals, a string), notes. The judgment screen lists **all** registered claims with a selector each | `claim_result`; `/api/v1/judgments/{id}/claim-results`; `ClaimResultApiTest`; `JudgmentRecording.test.tsx` |
| 05-025 | Results are per judgment (unique `(judgment, claim)`), so a later judgment or stage never replaces an earlier one; the case view lists them side by side in stage order | `/api/v1/cases/{id}/claim-results`; `CaseClaims` matrix; S13 tests |
| 05-026 | Statistics from `claim_result` itself, by result and by stage, filters stage / case type / judgment dates, awarded amounts summed apart from every other amount; only over the cases the user may see | `/api/v1/reports/claim-results`; `StatisticsApiTest`; `ClaimStatisticsPage` |
| 09-001…09-013, 09-015, 09-016 | Extended judgment under `details` (chamber, type and nature from managed lists, operative text, reasons, costs, lawyer fees, appealable, notified on, status, copy = a stored document of the case). The legacy fields stay valid; a request without `details` leaves them untouched | `JudgmentDetailsApiTest`; `JudgmentForm` |
| 09-017 | Every judgment of every stage is kept; results and positions are per judgment | S13 tests, journey |
| 17-001…17-014 | Defences: number, title, kind (4), explanation, legal basis, facts, legal texts, memo (stored document), date raised, stage raised in, member who prepared it, notes | `defense`; `DefenseApiTest`; `CaseDefenses` |
| 17-015…17-022 | The court's position (the 7 positions of the source) with reasons and operative text, per defence per judgment; the judgment screen lists all defences | `defense_position`; `/api/v1/judgments/{id}/defense-positions` |
| 17-023…17-027 | Whether the defence was raised / re-asserted / not asserted in each proceeding, with its own date and memo, next to the court's position in each judgment | `defense_assertion`; `/api/v1/defenses/{id}/assertions/{proceedingId}` |
| 17-028, 17-029 | Library of wording with review state (draft / reviewed / approved); search; choosing an entry only fills the form for the member to review — the system copies nothing | `defense_template`; `/api/v1/defense-templates` |
| 17-030 | `/api/v1/reports/defense-positions`: positions by position, stage, kind, most used defences, filtered by case type / stage / dates | `StatisticsApiTest` |
| 20-001…20-021 | Facts and evidence items with all the listed fields, 6 roles, 4 importance levels, electronic copy = a stored document of the case (never moved or copied) | `case_fact`, `evidence_item`; `EvidenceApiTest`; `CaseEvidence` |
| 20-022, 20-023 | One document linked to any number of facts, defences and claims; a defence lists its supporting documents | `evidence_link`; S8 tests |
| 20-024 | Submitted to court, date, memo/hearing, opponent response, court position | `evidence_item` |
| 20-025 | Gap detection: needed and not obtained (`NOT_OBTAINED`), or obtained and not submitted (`NOT_SUBMITTED`); each gap links to follow-up tasks of the same case | `/api/v1/cases/{id}/evidence/gaps`, `gap_followup` |
| S8 "shown in the case file" | `/api/v1/cases/{id}/document-usage` tells for each stored document where it is used (evidence with its links, claim source, defence memo, judgment copy); the documents section of the case page shows it | `DocumentUsageList` |
| D10 | Managed lists with origin DOCUMENT / PROPOSED, approval, deactivation instead of deletion, full history; screen `/admin/lookups` | `lookup_value`, `lookup_value_history`; `LookupApiTest`; `LookupsPage` |

Access (D3): every endpoint resolves the case of its record and applies `CaseAccessPolicy`: 404 for a user who may not see the
case, 403 for one who sees it but may not change it; statistics count only visible cases. Each controller has tests for the
member, a department-wide viewer, an outsider and missing permissions. New permissions: `CLAIM_VIEW/MANAGE`,
`DEFENSE_VIEW/MANAGE`, `EVIDENCE_VIEW/MANAGE`, `LOOKUP_MANAGE`, seeded as in LD-DOC-04 (data entry and read-only roles view only).

## Decisions and assumptions to confirm

* **Seven defence positions**, as in the source text and LD-DOC-03 (the work-package brief said 11). 12 claim results, as in the source.
* **Managed lists**: the source names no judgment types or natures, so nothing is marked DOCUMENT. Five values from the approved
  prototype are seeded as `PROPOSED` ("needs owner approval"): types substantive / preparatory / jurisdiction, natures
  first-instance / final. Approval is `LOOKUP_MANAGE` (admin and legal manager).
* **Judgment status** (`ISSUED / NOTIFIED / CHALLENGED / OTHER`) is a proposal: the source names the field but no values.
* A claim's PUT replaces all its fields (the UI always sends them all); an unchanged PUT adds no revision.
* Per-case lists (claims, defences, facts, evidence) are plain arrays, not pages; a case holds tens of them, not thousands.
* `judgment.outcome` / `summary` are untouched; "complete the judgment" offers to copy the summary into the operative text on request only.
* No result, position, type, deadline, holiday or amount is generated; every new table starts empty.

## What remains (not in this package)

* 09-014 appeal deadline (`appeal_deadline_id`) — needs the deadline model (WP4, decision D6); not added as a column now.
* 17-008 supporting precedents (`defense_precedent`) — needs the library of judgments (WP7).
* 20-023 / 20-025 requests to the departments: `gap_followup.info_request_id` is reserved without a foreign key until WP3 adds
  `info_request`; for now a gap links to a task.
* Search of the defence library is a simple text match (the unified Arabic search is WP9).

## Evidence in this folder

| File | |
|---|---|
| `01-red-…`, `02-red-…`, `03-red-…` | Failing runs before the code: lookups, claim results and judgment details; defences; claims, evidence and statistics (the last run with the three controllers set aside, 23 of 24 tests fail) |
| `04-green-backend-pleadings.log` | 50 tests of `com.lawdesk.pleadings` green |
| `05-backend-regression-neighbours.log` | 141 tests of the neighbouring modules (judgments, reports, visibility, hearings, documents, tasks, migrations, permissions) green |
| `06-frontend-suite-and-coverage.log` | 584 frontend tests green; branches 91.41 % (gate 90 %) |
| `journey-wp2-local.json` | The journey below run against the application with an embedded PostgreSQL on localhost (60 steps, all passed). **Not UAT.** |

Honest note on order: the lookup / claim-result / judgment-detail and defence tests were run red first as written; the claim,
evidence and statistics tests were written before their controllers but first run together with them, so their red run
(`03`) was produced afterwards by setting the three controllers aside.

## Running the journey on UAT (integration owner)

```
python3 tools/journeys/wp2_journey.py docs/evidence/DOC-WP2/journey-wp2-uat.json
```

Uses the QA accounts of `deploy/qa/qa_accounts.py` (manager = alternate member, staff = specialist, viewer = no scope). Creates one
synthetic case marked `SYNTH-DOC`, uploads four tiny synthetic PDFs to it, and one managed-list value that it deactivates again.
It never edits an existing case.
