# LD-DOC-R6-03 — Migration plan, permission matrix, state-transition matrices (M0)

## 1. Migrations, recovery and compatibility (PROPOSED; nothing here exists yet)

### 1.1 Rules for every R6 migration
Additive only. No existing column or table altered destructively; no historical row rewritten; new tables carry a primary key, `version` and audit columns; constraints are named; legal seeds are inserted **inactive/DRAFT**. No migration is written in M0.

### 1.2 Migration numbers: a plan, not a reservation
Planning allocation only: V900–V909 M1, V910–V919 M2, V920–V929 M3, V930–V939 M4, V940–V949 M5. It binds nobody. **Immediately before creating each migration file** the developer runs `python3 tools/docs/r6_m0_check.py` (scans every remote branch) and checks open PRs; the number is taken only if it is above the highest version found anywhere and no other branch has the same one; the result is pasted into the milestone PR. State at this revision: highest anywhere V881 (R5), develop V845.

### 1.3 Compatibility with previously deployed releases (EXISTING configuration, verified)
`application.yaml` sets Flyway `out-of-order: true` and `ignore-migration-patterns: "*:future"` ("future versions stay ignored when an older release runs (rollback)"). Consequences:
* An older application release started against a database that already has R6 migrations does **not** fail Flyway validation; it ignores them.
* R6 tables are new and unknown to older code; older code never reads them. The only change to an existing table is a nullable `legal_case.origin_complaint_id` (M5), invisible to older code because Hibernate runs `ddl-auto: validate` against its own entities only (unmapped columns are not validated).
* Each milestone must **prove** this, not assume it: start the previous release jar (the SHA UAT serves) against a database migrated by the milestone, run its smoke journey, and record the result (T-M02).
* Compatibility does not work in the other direction: newer code against an older schema is not supported; migrations always run before the new code serves.

### 1.4 Three different recovery mechanisms (never conflated)
| Mechanism | When | What it does | What it loses |
|---|---|---|---|
| **Application rollback** | A defect is found after deployment, data are valid | Redeploy the previous release jar; R6 tables stay in place, untouched and unused (1.3). R6 behaviour is switched off by the activation gates (D-gates in 04) where applicable | R6 screens/jobs stop; R6 data written so far remain stored, not deleted |
| **Database restore** | Data corruption or a failed migration; last resort | Restore the verified pre-deployment backup into a clean database, switch over after reconciliation | **Everything written after the backup.** Needs explicit owner approval and a window agreed with Codex |
| **Forward recovery** | Normal way to fix an R6 data or schema defect | A corrective migration or hotfix release moves forward (new migration number, new code); populated tables are corrected, never dropped | Nothing |

**Never**: dropping or truncating a populated R6 table as a "routine rollback". Retiring a table, if ever wanted, follows the R5 counsel pattern: rename (`*_retired`), keep the rows, final deletion only with separate explicit owner approval, recorded counts and a verified backup.
Restore drills and reversal scripts that drop tables may exist **only** for empty or synthetic databases, are labelled as such and are not a production procedure.

### 1.5 Evidence required before any R6 milestone can reach UAT (owner approval and Codex window also required)
1. Verified backup of the target database (`pg_dump -Fc`, restored into a side database, row counts and digests equal).
2. Restore drill on PostgreSQL 18.x with a synthetic pre-milestone dump (`tools/restore-drill`, extended per milestone): migrate, start, second start applies nothing, previous release jar starts against the migrated database.
3. Data reconciliation report: row counts and digests of **every pre-existing table** before vs after (must be identical except documented additive columns), plus counts of new tables.
4. Real row counts on the target environment for any table a milestone reads for backfill (none planned in R6: no backfill).
5. Forward-recovery rehearsal: a deliberately failing step in a transaction leaves no partial R6 data.
Nothing in this list is performed in M0.

## 2. Permissions (PROPOSED; names confirmed in M1 review; final authority allocation PENDING owner approval — D-09)

Principle: **technical administration is not legal approval authority.** ADMIN (system administrator) administers configuration, users and roles; it does **not** automatically hold the right to approve a legal decision or to read complaint content. Legal approval is a separate set of authorities granted to roles/users the owner designates (proposal: LEGAL_MANAGER).

Maker-checker workflow (PROPOSED): the responsible researcher **prepares** (a draft record: non-appeal decision, finality confirmation request, recalculation, non-grievance decision, complaint closure, referral) → an authorised legal manager **approves or rejects** with a reason → only the approval changes the status. The approver must differ from the preparer, must hold the approval authority **and** have case access (case-scoped), and their identity and date are stored as the document requires («صاحب الموافقة»).

| Authority | Meaning |
|---|---|
| `POSTJ_VIEW` | read party deadlines, appeals, decisions, alert history (case-scoped) |
| `POSTJ_PREPARE` | register/edit deadlines and appeals, confirm opponent position, prepare decision/finality/recalculation requests |
| `POSTJ_APPROVE` | approve or reject prepared decisions, finality and recalculations (legal approval; ≠ ADMIN) |
| `APPEAL_SETTINGS_VIEW` / `APPEAL_SETTINGS_MANAGE` | setting screen. **Source-specified (ST-01):** system administrator or legal affairs manager only. Managing a setting is not approving its legal content (see activation gates, 04) |
| `APPEAL_SETTINGS_APPROVE` | records the legal authority's approval that makes a setting usable (legal approval) |
| `COMPLAINT_VIEW` | read complaints **within scope** (below) |
| `COMPLAINT_CREATE` / `COMPLAINT_EDIT` | submit; edit sessions, requests, documents |
| `COMPLAINT_PREPARE_DECISION` | prepare prosecution decision, grievance decision, closure, referral |
| `COMPLAINT_APPROVE` | approve those (legal approval) |
| `AUDIT_VIEW` (existing) | audit rows for workflows the viewer may see; hidden records excluded |

Proposed default mapping, **not final**: ADMIN: `APPEAL_SETTINGS_*` (except APPROVE), no POSTJ/COMPLAINT content authorities. LEGAL_MANAGER: all POSTJ, COMPLAINT, APPEAL_SETTINGS authorities within scope. LEGAL_STAFF: VIEW/PREPARE/CREATE/EDIT within scope; no APPROVE. QA_READ_ONLY: VIEW within scope.

### 2.1 Visibility of post-judgment records
Reached only through the case: `CaseAccessPolicy.requireView` → 404 identical (status and normalised body) to an unknown id; `requireChange` → 403; access is checked before validation. Worklists, alert history, counts, search results and dashboards use the same predicate.

### 2.2 Complaint confidentiality (D-20; PROPOSED)
A complaint is visible only to a user who has `COMPLAINT_VIEW` **and** is in scope:
* the responsible researcher and users explicitly added to the complaint (access list, like case grants);
* users whose organisational scope (existing LEGAL org-unit tree and access scopes) covers the complaint's owning unit and who hold `COMPLAINT_VIEW` (a manager sees their unit's complaints, not all of them);
* after referral, the linked case's rules also apply to the linked case; the complaint keeps its own rule.
ADMIN does **not** see complaints merely by role. If the owner wants administrative oversight it is granted explicitly (for example metadata-only audit) and decided separately.
Hidden-record isolation is required on **every** surface; each is a named test (T-S02): complaint read/list/search endpoints, sessions, requests, respondents, documents and download/preview/OCR text endpoints (404 identical to unknown id), worklists and dashboard counts (excluded, never "N hidden"), alerts and notifications (created only for users in scope; the text carries no respondent name or incident detail beyond what the recipient may read; alert history filtered), audit viewer, exports and reports, timeline of a linked case (shows the link only if the viewer may see the complaint), and recipient changes (removing a user's scope removes their pending alerts).

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
