# LD-DOC-R6-03 — Migration plan, permission matrix, state-transition matrices (M0)

## 1. Migration and rollback plan

Rules: additive only; no existing column or table altered destructively; no historical row rewritten; every new table has a primary key, a `version` column and audit columns; CHECK/EXCLUDE constraints carry names; seeds are inserted **inactive/DRAFT**.

| Range | Milestone | Content |
|---|---|---|
| V900–V909 | M1 | `judgment_party_deadline`, `judgment_appeal`, `judgment_appeal_document`, procedural status + history, `citra_capacity` on stages, applied-duration snapshot columns |
| V910–V919 | M2 | `deadline_setting` + history + overlap exclusion constraint, `appeal_decision`, `deadline_recalculation`, finality record, three DRAFT seed rows |
| V920–V929 | M3 | `case_alert`, alert offsets setting, notification types |
| V930–V939 | M4 | complaint aggregate tables |
| V940–V949 | M5 | decision/grievance/referral tables, `legal_case.origin_complaint_id` (nullable, unique) |

Rollback: each milestone ships a documented reverse (drop of the new tables in reverse dependency order), proven on a restored copy; because nothing existing is altered, rolling back application code alone leaves the database valid (new tables simply unused). For `legal_case.origin_complaint_id` (the only change to an existing table) the reverse is `drop column`, safe while it is null for all pre-existing rows.
Verification per milestone: (a) empty database migrates; (b) a PostgreSQL 18 database restored from a pre-milestone synthetic dump migrates, row counts and digests of all pre-existing tables unchanged; (c) second start applies nothing; (d) the restore drill (`tools/restore-drill`) is extended with the new tables. Production-like numbers are never used; UAT is not touched.
Backfill: none. Existing judgments get no party deadline unless a user creates one; the existing `deadline` and `appeal_deadline_id` stay valid and are shown as "previous record".

## 2. Permissions (proposed; names to be confirmed in M1 review)

New authorities (existing `CASE_*`, `JUDGMENT_*`, `DEADLINE_*`, `AUDIT_VIEW` unchanged):

| Authority | Meaning |
|---|---|
| `POSTJ_VIEW` | read party deadlines, appeals, decisions, alert history |
| `POSTJ_MANAGE` | register/edit party deadlines and appeals, confirm opponent position |
| `POSTJ_DECIDE` | record and approve an appeal decision (incl. non-appeal) |
| `POSTJ_FINALITY` | confirm finality |
| `POSTJ_RECALC` | recalculate a deadline with a reason |
| `APPEAL_SETTINGS_MANAGE` | appeal/grievance setting screen (create, update, deactivate); ADMIN and LEGAL_MANAGER only (ST-01) |
| `APPEAL_SETTINGS_VIEW` | read settings |
| `COMPLAINT_VIEW` / `COMPLAINT_CREATE` / `COMPLAINT_EDIT` | complaint read / submit / edit sessions, requests, documents |
| `COMPLAINT_DECIDE` | record prosecution decision, referral |
| `COMPLAINT_APPROVE` | approve non-grievance decision, closure |
| audit | existing `AUDIT_VIEW` serves both workflows |

Role mapping (proposal, subject to D-09): ADMIN all; LEGAL_MANAGER all; LEGAL_STAFF: VIEW/MANAGE/CREATE/EDIT on cases they can access, no DECIDE/FINALITY/APPROVE/RECALC/SETTINGS; QA_READ_ONLY: VIEW only.

Visibility: every post-judgment record is reached only through its case; `CaseAccessPolicy.requireView` → 404 identical to an unknown id; `requireChange` → 403. Access is checked before validation. A complaint with no case yet: visible to its responsible researcher, LEGAL_MANAGER and ADMIN only (D-20); after referral it follows the linked case's rules. Worklists and alert history are filtered with the same predicate (a user never sees an alert row for a hidden case).

## 3. State-transition matrices

### 3.1 Judgment procedural status (the document's §5 statuses; separate from the case status and from the old `judgment.status`)

| From | Event | To | Condition |
|---|---|---|---|
| — | register, outcome IN_FAVOUR | في انتظار انقضاء ميعاد الطعن | — |
| — | register, outcome AGAINST or PARTIAL | جاري إعداد الطعن | — |
| في انتظار انقضاء ميعاد الطعن | opponent appeal recorded | منظورة استئناف / منظورة تمييز (by stage) | next stage opened, CITRA capacity مستأنف ضدها / مطعون ضدها |
| جاري إعداد الطعن | CITRA appeal filed before expiry | منظورة استئناف / منظورة تمييز | capacity مستأنفة / طاعنة |
| في انتظار انقضاء ميعاد الطعن | finality confirmed | حكم نهائي | all party deadlines elapsed, opponent position confirmed "no appeal", no recorded appeal, approved conditions met (D-07) |
| جاري إعداد الطعن | finality confirmed | حكم نهائي (قبول الحكم) | CITRA decision = NOT_APPEAL approved, same finality conditions |
| any non-final | expiry of a deadline | **no change** (alert only) | forbidden to finalise (PJ-09) |

Forbidden: final → any other status (reopening is not in the document; D-24); appeal registration on a final judgment; finality while any party deadline is running, an appeal is recorded, or the opponent position is UNRESOLVED; non-appeal decision after CITRA's appeal exists; second CITRA appeal for the same judgment and type; any transition not in the table (422 with a stable rule code).
Not specified by the document and held open: the label of a judgment whose CITRA decision is NOT_APPEAL while the opponent's deadline still runs (D-21); transition when both parties appeal (assumed: منظورة of the next stage, one status, two appeal rows).

### 3.2 Parallel positions (stored separately, never overwritten)

* CITRA decision: `UNDECIDED` → `APPEAL` | `NOT_APPEAL` (reason, approver, date mandatory).
* Opponent position: `UNRESOLVED` → `APPEALED` | `NO_APPEAL_CONFIRMED` (researcher confirmation, date, user).
* Judicial status: `DEADLINE_RUNNING` → `APPEAL_FILED` → `FINAL`.
Changing one of these must leave the other two untouched (T-A18).

### 3.3 Complaint status

| From | To | Condition |
|---|---|---|
| (new) | مقدمة للنيابة | on submission |
| مقدمة للنيابة | قيد التحقيق بالنيابة | first session or request recorded, or explicit action (D-22) |
| قيد التحقيق بالنيابة | محفوظة | preservation decision recorded (reason, date, awareness date, document) |
| قيد التحقيق بالنيابة | محالة للمحكمة | referral recorded; exactly one linked case created (idempotent) |
| مقدمة للنيابة | محفوظة / محالة للمحكمة | allowed only if D-22 approves skipping investigation |
| محفوظة | مغلقة | grievance deadline elapsed **or** a non-grievance decision recorded (reason + approver); explicit action by an authorised user, never by a timer (PC-14) |
| محفوظة | (stays) | grievance filed: recorded as a grievance row; the next status is not specified (D-11) |

Forbidden: any move out of مغلقة; closure of a محفوظة complaint before the deadline without a non-grievance decision; second referral; referral of a محفوظة or مغلقة complaint (unless D-11 says otherwise); deleting a complaint or any of its sessions/requests (soft removal with history only).
Every transition writes `complaint_status_history` and an audit event (who, when, from, to).
