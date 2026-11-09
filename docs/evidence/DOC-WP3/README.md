# DOC-WP3 — hearings extended, linked cases, information requests, risk, summary, timeline, alerts (LD-053)

Story LD-053, migrations V660–V665. Requirement sections 7, 8, 18, 19, 21 and the in-app minimum of 32
(requirement IDs `LD-DOC-07-001…018`, `LD-DOC-08-001…013`, `LD-DOC-18-001…039`, `LD-DOC-19-001…028`,
`LD-DOC-21-001…041`, `LD-DOC-32-*` for the types listed below). Scenarios S6, S7, S9, S10, S12.

## What was built

| Area | Backend | Screens |
|---|---|---|
| 7 Hearings | `hearing` gets time/chamber/kind (COURT, EXPERT, INVESTIGATION, OTHER)/judge or expert/responsible member/attendees/proceedings summary/opponents' requests/required action/adjournment reason/`no_next_date`+reason/follow-up flag (V660). The adjournment chain and its rules are unchanged. `no_next_date` creates no hearing and puts the case on `GET /hearings/follow-up`; `POST /hearings/{id}/next-hearing` schedules the date later and ends the follow-up without rewriting what was recorded | Extended schedule/reschedule/result forms, "no next date" path, next-hearing form, follow-up list on the calendar |
| 8 Linked cases | `case_link` (V663): subject link, request to join, decision to join, referral; one row shown in both files; link to a case the user cannot see is refused (404) and only counted for readers; closing one case never touches the other | Linked-cases section |
| 18 Information requests | `info_request`, `info_reply`, `info_follow_up` (V661); system-generated `IR-<year>-<n>`; flow DRAFT → PENDING_APPROVAL → SENT → AWAITING, state follows the replies, OVERDUE set by the follow-up run, never closed automatically; several partial/incomplete replies; reply PDF archived through the document module; reminders, new letters, escalation (own permission); delay reason; report per unit | Information-requests section with replies, follow-up, close |
| 19 Risk | `risk_assessment` + `risk_assessment_element` (ten elements) + `risk_remediation` (V662); versions are never edited (database trigger refuses updates); high/very high needs a plan and raises an in-app alert; `potential_loss` its own `numeric(15,3)` (D5); report of high-risk cases; fixed "decision aid" notice | Risk section with versions and plan |
| 21 Summary and timeline | `GET /cases/{id}/summary` (assembled from stored data), `GET /cases/{id}/unified-timeline` (past and future, `isFuture`, `needsAction`, `refType/refId`, filter, paging) | Summary panel and timeline panel on the case page |
| 32 Alerts | `notification` (V664) with unique `dedupe_key`; list, unread count, mark read; follow-up run (hourly job and `POST /follow-up/run`) for overdue requests and hearings without a next date; in-app only | Bell in the application bar and `/notifications` |

Permissions added (V665 seeds them): `INFO_REQUEST_VIEW/MANAGE/ESCALATE`, `RISK_VIEW/MANAGE`, `CASE_LINK_MANAGE`.
Every endpoint that touches case data uses `CaseAccessPolicy`: 404 for a case the user may not see, 403 for one they see but may not change; every list, report and search is restricted to visible cases (tests below).

## Tests (strict TDD: the red runs are in `red-*.txt`)

Backend (MockMvc on embedded PostgreSQL): `InfoRequestApiTest` (25), `HearingExtensionApiTest` (12), `CaseLinkApiTest`, `RiskAssessmentApiTest` (12), `CaseFileApiTest`, `NotificationApiTest` (7). Each has outsider (404), view-only (403) and permission (403) cases.
`red-inforequests.txt` — 25 of 25 failed before the code; `red-hearing-extension.txt` — 11 of 12 failed before the code. The tests of links, risk, summary/timeline and notifications were written before their code in the same way but the red run was not kept.
Frontend (vitest, 90 % branch gate): `features/followup/*.test.tsx`, `features/operations/hearings/HearingExtension.test.tsx`; existing hearing tests were updated for the new request bodies.

## Journey

`python3 tools/journeys/wp3_followup_journey.py docs/evidence/DOC-WP3/journey-uat.json` (QA accounts, synthetic data `SYNTH-DOC`, no existing case edited). Run by the integration owner after the merge and deployment.

## What remains / assumptions to confirm

- Link states (REQUESTED, ACTIVE, REJECTED, ENDED) and the element rating scale (NOT_ASSESSED, LOW…VERY_HIGH) are not in the document: ASSUMPTIONS for the owner's approval.
- "Direct supervisor" has no model (no head of unit): escalation is manual, to a named user, with its own permission. The reminder lead time before a reply is due is not set (no value invented): only the overdue alert exists.
- `linked_fact_id` / `linked_defense_id` on requests and replies are stored but not validated until the WP2 tables exist.
- Legal deadlines (WP4), claims, defences, evidence and enforcement (WP2/WP5) are named as "not recorded yet" in the summary; the timeline reports `deadlinesAvailable: false`.
- The notification table is the minimum; types, preferences and the other ten kinds of section 32 belong to WP6. A hearing whose day passed without a result is not yet alerted.
- The responsiveness report (18.5) is an API (`GET /info-requests/report`) without a screen.
- Browser matrix, axe and screenshots were not captured here (run with the journey on UAT).
