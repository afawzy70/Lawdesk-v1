# LD-045 — Government case intake (acceptance criteria, assumptions, QA scenarios)

Owner directive of 2026-11-07. LawDesk serves a government authority in Kuwait: "الهيئة" is the authority, "الإدارة
القانونية" its legal department, "العضو المختص" the member responsible for a case. Spring Boot + React with
database-backed users; no external identity provider. Status: **IMPLEMENTED** — it is not QA ACCEPTED until independent
QA retests it.

## What changed

| Area | Behaviour |
|------|-----------|
| Registration | `POST /api/v1/cases/intake` registers the case, its first-instance proceeding, the authority and the other parties, the members and the financial impact in **one transaction**. A refused registration leaves nothing behind. The screen is `/cases/new`. |
| الرقم الآلي للقضية | `legal_case.automated_no`, typed by the user, kept as text (leading zeros), required on registration, the same on every stage, shown on the case and carried into the stage forms without asking again. No format rule; 100 characters is only the column's technical bound. |
| رقم القضية في درجة التقاضي | The existing `proceeding.court_case_number` + year, one per stage (first instance, appeal, cassation). Earlier stages keep their numbers; the case page lists each with its stage. Required for the first stage on registration. |
| موقف الهيئة | `legal_case.authority_position` (`PLAINTIFF`/`DEFENDANT`), required. The authority is a real party (one marked `party` row, name from `lawdesk.authority.name`, default «الهيئة») linked on its side. |
| الأطراف | Any number of other parties on either side, new or registered. A party's capacity is its `case_party.side`; the legacy `role` is derived (`CLIENT` authority, `OTHER` same side, `OPPONENT` other side). Changing the position on the screen never flips or drops parties: the screen asks for a review (or an explicit swap) before saving. |
| الرقم الآلي للطرف | Replaces the label "الرقم القومي" of the party form. The column and API field keep the name `nationalId`; values are untouched. The Egyptian 14-digit rule is **removed** (any visible text up to 50 characters) — see assumption A3. Independent of the case's automated number. |
| العضو المختص / البديل | `legal_case.primary_member_id` (required) and `alternate_member_id` (optional), different people, both **active members of the legal department** (enabled holders of `LEGAL_MANAGER` or `LEGAL_STAFF`), checked on the server. `GET /api/v1/cases/members` lists them. |
| Who may change a case | `CaseAccess`: with a specialist assigned, only the specialist, the alternate and **case supervisors** (holders of `CASE_STATUS_CHANGE`: administrator and legal manager) may change its content. Enforced in the services (case edit, parties, and every module that writes through `CaseLookup#recordEvent`: proceedings, judgments, special decisions, hearings, expert referrals, tasks, documents, notes), read from the database on every request. Viewing is not narrowed. A case without a specialist keeps the old rule. |
| Reassignment | `PUT /api/v1/cases/{id}/assignment` for supervisors (`CASE_EDIT` + `CASE_STATUS_CHANGE`). Applies at once to old and new members. Timeline `CASE_ASSIGNED`; audit `CASE_ASSIGNMENT_CHANGE` (one event for the primary, one for the alternate), `CASE_CREATE`, `CASE_AUTOMATED_NO_CHANGE`, `CASE_FINANCIAL_IMPACT_CHANGE` — all in the business transaction (QAD-040/042 rule), ids and fixed codes only. |
| الأثر المالي | `financial_impact` `YES`/`NO` and `financial_amount numeric(15,3)` (KWD, exact). Required choice on registration; `YES` needs an amount > 0 with at most three decimals; `NO` hides and stores no amount and the API refuses one. A database check makes the pair consistent. The screen asks before dropping a typed amount. One value for the whole case. |
| Older cases | Every new column is nullable. `null` means **not specified** and is shown as such: no number, position, member or financial impact is invented, and an older case is never labelled "no financial impact". They stay editable under the old rule until a supervisor assigns a specialist. |
| Legal operations | The registration screen has no such section and none of its data is required. (The screen as found did not contain one; the section lives on the case page next to the stages, which is unchanged.) |

## Assumptions to evaluate (owner)

- **A1 — case supervisors.** "Existing administration" = holders of `CASE_STATUS_CHANGE` (ADMIN, LEGAL_MANAGER). They keep editing and reassigning every case.
- **A2 — legal department.** Active users with role `LEGAL_MANAGER` or `LEGAL_STAFF`; a custom role does not count; ADMIN alone does not count.
- **A3 — party number.** Only the label changed to «الرقم الآلي للطرف». It is not the civil number and has no civil-number validation. The old 14-digit pattern was dropped because it described an Egyptian national number; **the field label and rules need your review**. Existing values are unchanged.
- **A4 — alternate = specialist** from the moment of assignment (same permissions).
- **A5 — first stage.** A case is registered at first instance (court, number, year, filing date are required); appeal and cassation are added later from the case page with their own numbers.
- **A6 — one financial impact** per case, not per stage.
- **A7 — authority name.** No system-settings screen exists; the name comes from the property `lawdesk.authority.name` (env `LAWDESK_AUTHORITY_NAME`), default «الهيئة».
- **A8 — a task assignee** who is not a member of the case cannot complete a task on it (all case writes go through the same gate). Say if assignees should be exempt.
- **A9 — position after registration.** The authority's position is fixed at registration in this version; parties are added/removed afterwards by side.

## Known limits

- `POST /api/v1/cases` (the original endpoint) still exists for integrations and QA fixtures: cases created through it are "not specified". The screen and the intake endpoint enforce the new required fields; closing the old endpoint is a decision for the owner.
- The internal reference (`LD-YYYY-NNNNN`) is now labelled "المرجع الداخلي" / "Internal reference" so it is not mistaken for the automated number.

## QA scenarios

1. Register as plaintiff with two co-plaintiffs and two defendants; as defendant with plaintiffs; the authority appears once, on its side.
2. Automated number `000123` is stored and shown as typed; add an appeal with a different number: the automated number is shown, not asked again, and the first-instance number is still listed.
3. Party number `000123` on a party and case number `000123`: independent.
4. Specialist and alternate: cannot be the same person; disabled, non-department and unknown users are refused by the API (422).
5. As an unassigned department member: view works; edit/proceeding/party/task/note writes answer 403 by direct API; as specialist and alternate they succeed; as a supervisor they succeed. Reassign and repeat at once.
6. Financial: `YES` + 15250.500; reject 0, negative, 4 decimals, missing; `NO` stores nothing; edit `YES` → `NO` clears the amount.
7. Older case: opens, edits, shows "not specified".
8. Arabic RTL and a 375 px viewport: no horizontal overflow, labels as above, keyboard-only registration.

UAT browser evidence (27/27, deployed f9aa4aa, Chromium, en/ar, 1366 and 375 px): `docs/evidence/GOV-INTAKE/uat/` (`e2e.mjs`, `results.json`, screenshots).

Tests: `GovernmentIntakeApiTest`, `CaseMembershipAccessTest` (backend); `CaseCreatePage.test.tsx`, `CaseIntakeDetails.test.tsx`, `PagedSelect.test.tsx` (frontend). Evidence: `docs/evidence/GOV-INTAKE/`.
