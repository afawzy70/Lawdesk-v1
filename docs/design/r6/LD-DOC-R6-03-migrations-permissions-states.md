# LD-DOC-R6-03 — Migration and recovery plan, permissions, state matrices (M0, PROPOSED)

Nothing here is built. Authorization mechanics, the data model, gates, alerts and confidentiality are in `LD-DOC-R6-05`; this page is the migration/recovery plan, the permission matrix and the state tables.

## 1. Migrations, recovery and compatibility

### 1.1 Rules for every R6 migration
Additive only. No existing column or table altered destructively; no historical row rewritten; no backfill. New tables carry a primary key, `version` and audit columns; constraints are named; legal seeds are inserted DRAFT/inactive and never approved by a migration. **No existing table receives a new column** in the whole plan (capacity is per filing in `judgment_appeal`; the complaint↔case link is a separate table, 05 §B.2). Existing tables receive only **guard triggers** (on `judgment`, `deadline`, `role_permission`, `legal_case`, `case_grant`, the case-assignment columns) and five **implied-unique indexes** (`proceeding(id, case_id)`, `proceeding(id, case_id, parent_proceeding_id, stage)`, `judgment(id, proceeding_id)`, `case_party(id, case_id, role)`, `deadline(id, case_id)`; the primary key already makes them unique, so they cannot fail on existing data; `LD-DOC-R6-07` §4.2), none of which changes a stored row. **Ownership** of every object moves from `lawdesk_app` to `lawdesk_owner` and Flyway runs as the owner (`LD-DOC-R6-07` §2.3, §2.9): a deployment step with the owner's approval, not a migration. No migration is written in M0.

### 1.2 Migration numbers: a plan, not a reservation
Planning allocation only: V900–V909 M1, V910–V919 M2, V920–V929 M3, V930–V939 M4, V940–V949 M5. It binds nobody. **Immediately before creating each migration file** run `python3 tools/docs/r6_m0_check.py` (scans every remote branch), check open PRs and branch heads on GitHub, and take a number only if it is above the highest version found anywhere and unused; paste the output in the milestone PR. Snapshot at this revision: highest anywhere V881 (R5), develop V845; V900–V949 unused.

### 1.3 Compatibility with previously deployed releases
`application.yaml:17-24` sets `ddl-auto: validate`, `out-of-order: true`, `ignore-migration-patterns: "*:future"`. This is **necessary but not sufficient**: it shows that Flyway validation is configured to tolerate newer migrations, not that an older release runs correctly on an R6 schema, and it says nothing about coexistence with R5's migrations. Required proof (not yet done): **T-MIG-3** — start the previous release jar (the SHA UAT serves) on a database migrated by the milestone, log in, run case/judgment/deadline CRUD smoke and one `DeadlineReminderRun` cycle **with R6 tables populated**; **T-MIG-1/2/4** — fresh PostgreSQL 18 database, restored synthetic pre-milestone dump with unchanged digests of every pre-existing table, second start applies nothing; **T-MIG-5** — installation orders if R5 is later accepted. Compatibility is one-directional: new code on an old schema is unsupported.
Previous release and R6 triggers (revised in Round 3): the database triggers of `LD-DOC-R6-05` §I apply to **every** writer, including the previous release jar. A legacy write to an R6-linked judgment or deadline therefore fails closed with no side effect (the old release's HTTP status for that failure is not asserted); unlinked records work as before. This replaces the earlier hazard statement that an old release could silently edit an R6-linked deadline. Only the schema owner (the migrate job's credential) or a superuser can bypass the guards, **provided the roles of `LD-DOC-R6-07` §2.2 are in place**; under the present ownership (`lawdesk_app` owns the schema) the application's own credential could remove them, which is why the role split is a precondition of M1 (verified on PostgreSQL 18, 07 §2.1). Reconciliation (I-7) is detective.

### 1.4 Three different recovery mechanisms (never conflated)
| Mechanism | When | What it does | What it loses |
|---|---|---|---|
| **Application rollback** | A defect appears after deployment and the data are valid | Redeploy the previous release jar; R6 tables stay in place, untouched and unused (after T-MIG-3 has proved it works). Gated behaviour stays off. | R6 screens/jobs stop; data written meanwhile remain stored, not deleted |
| **Database restore** | Data corruption or a failed migration; last resort | Restore the verified pre-deployment backup into a clean database, reconcile, switch over | **Everything written after the backup**; needs explicit owner approval and a window agreed with Codex |
| **Forward recovery** | Normal fix of an R6 data or schema defect | A corrective migration or hotfix release with a new number; populated tables are corrected, never dropped | Nothing |

**Never** drop or truncate a populated R6 table as a "routine rollback". Retiring a table follows the R5 pattern (rename to `*_retired`, rows kept, final deletion only with separate explicit owner approval, recorded counts and a verified backup). Scripts that drop tables may exist only for empty or synthetic databases, labelled as such.

Restore (revised in Round 4): `pg_restore` as superuser into a database where the roles already exist keeps ownership and ACLs; the **privilege audit** (07 §2.3) runs before the application starts; an older release's `app-role.sql` re-grants `TEMPORARY` and nothing else (07 §2.9).

### 1.5 Evidence required before any R6 milestone can reach UAT (owner approval and a Codex window are also required)
1. Verified backup of the target database (`pg_dump -Fc`, restored into a side database, row counts and digests equal).
2. Restore drill on PostgreSQL 18.x with a synthetic pre-milestone dump (`tools/restore-drill`, extended per milestone): T-MIG-1…T-MIG-4, T-MIG-6.
3. Data reconciliation report: counts and digests of **every pre-existing table** before vs after (identical except the documented additive column), plus counts of new tables.
4. Real row counts on the target for any table a milestone reads (none planned: no backfill).
5. Forward-recovery rehearsal: a deliberately failing step leaves no partial R6 data (T-MIG-6).
Nothing in this list is performed in M0.

## 2. Permissions (PROPOSED; final allocation PENDING owner approval — D-09)

Principle: **technical administration is not legal authority.** ADMIN administers configuration, users and roles; it does not hold legal approval or complaint content by role. Mechanism (sensitive authorities only in `legal_authority_grant`; never from the enum, role rows, role creation, role assignment, direct assignment or delegation; first-custodian bootstrap; seven enforcement layers): `LD-DOC-R6-05` §A. The "no" entries below mean exactly that.

| Authority | Meaning | Inherited by ADMIN? |
|---|---|---|
| `POSTJ_VIEW` | read party deadlines, appeals, decisions, alert history (case-scoped) | yes (existing pattern; case scope still applies) |
| `POSTJ_PREPARE` | register/edit litigants, deadlines and filings, confirm each opposing litigant's position, prepare decision/finality/recalculation | yes (preparing is not approving) |
| `POSTJ_APPROVE` | approve/reject prepared decisions, finality, recalculations | **no** |
| `APPEAL_SETTINGS_VIEW`, `APPEAL_SETTINGS_MANAGE` | setting screen; source ST-01: administrator or legal affairs manager | yes |
| `APPEAL_SETTINGS_APPROVE` | records the legal authority's approval that makes a setting usable | **no** |
| `LEGAL_GATE_APPROVE` | records versioned legal approvals (`legal_approval`) | **no** |
| `LEGAL_AUTHORITY_CUSTODIAN` | approves grants/delegations of sensitive authorities | **no**; first designation is an owner decision |
| `COMPLAINT_VIEW`, `COMPLAINT_CREATE`, `COMPLAINT_EDIT` | complaint access within scope | **no** |
| `COMPLAINT_PREPARE_DECISION`, `COMPLAINT_APPROVE` | prepare / approve prosecution decision, grievance decision, closure, referral manifest | **no** |
| `AUDIT_VIEW` (existing) | audit rows for records the viewer may see | unchanged |

Maker-checker for every approval: preparer ≠ approver; approver holds the authority (or a valid delegation), the scope covers the target and the approver passes the target's visibility predicate; identity, date and `grant_id`/`delegation_id` stored. Proposed default holders (not final): LEGAL_MANAGER for approvals within the manager's scope; LEGAL_STAFF VIEW/PREPARE/COMPLAINT_CREATE/EDIT within scope; QA_READ_ONLY none of the sensitive ones. Post-judgment records are reached only through the case (`CaseAccessPolicy.requireView` → 404 identical to an unknown id; `requireChange` → 403; access before validation). Complaint visibility: 05 §B.

## 3. State-transition matrices

### 3.1 Judgment procedural status (separate from the case status and from the old `judgment.status`)
| From | Event | To | Condition |
|---|---|---|---|
| — | register, IN_FAVOUR | في انتظار انقضاء ميعاد الطعن | — |
| — | register, AGAINST or PARTIAL | جاري إعداد الطعن | — |
| في انتظار انقضاء ميعاد الطعن | an opposing litigant's filing recorded | منظورة استئناف / منظورة تمييز | child proceeding mapped (05 §C.2; own child unless an approved joinder); capacity of that filing مستأنف ضدها / مطعون ضدها |
| جاري إعداد الطعن | CITRA filing recorded before expiry | منظورة استئناف / منظورة تمييز | capacity مستأنفة / طاعنة |
| any pending status | **another filing** by any other litigant (CITRA or another opposing litigant) | (unchanged: منظورة …) | independent row; every litigant's deadline resolves independently |
| في انتظار انقضاء ميعاد الطعن | finality approved | حكم نهائي | G-FINAL satisfied (D-07), every litigant resolved, all party deadlines resolved/elapsed, each opposing litigant's position confirmed «no appeal», no non-VOID filing, no unmapped child proceeding (I-6) |
| جاري إعداد الطعن | finality approved | حكم نهائي (قبول الحكم) | as above and CITRA decision NOT_APPEAL approved |
| any non-final | deadline expiry | **no change** (alert only) | PJ-09 |

Forbidden (stable rule codes, no side effect): final → any other status (reopening, D-24); filing on a final judgment (`JUDGMENT_FINAL`); finality while a party deadline is running, a non-VOID filing exists (`APPEAL_FILED`) or any opposing litigant's position is UNRESOLVED; non-appeal after a CITRA filing; a second non-VOID filing by the same litigant and appeal type; any transition not listed.
Held open: label while an approved non-appeal waits for the opponent's period (D-21).

### 3.2 Parallel positions (separate columns, never overwritten)
CITRA decision `UNDECIDED → APPEAL | NOT_APPEAL` (reason, approver, date). Position of **each** opposing litigant `UNRESOLVED → APPEALED | NO_APPEAL_CONFIRMED` (on `judgment_litigant`). Judicial status `DEADLINE_RUNNING → APPEAL_FILED → FINAL`. Changing one leaves the other two untouched (T-PJ14-1).

### 3.3 Complaint status
| From | To | Condition |
|---|---|---|
| (new) | مقدمة للنيابة | on submission |
| مقدمة للنيابة | قيد التحقيق بالنيابة | first session/request or explicit action (D-22) |
| قيد التحقيق بالنيابة | محفوظة | preservation decision recorded |
| قيد التحقيق بالنيابة | محالة للمحكمة | **only by the atomic completion of a referral** (05 §B.4): all mandatory data present, manifest approved, owner policy P-MANIFEST and D-25 approved; the linked case is created in the same transaction. While any value is missing the complaint **stays «قيد التحقيق بالنيابة»** with a referral draft and a list of missing fields. Invariant R-1: this status ⇔ exactly one linked case |
| مقدمة للنيابة | محفوظة / محالة للمحكمة | only if D-22 approves skipping investigation |
| محفوظة | مغلقة | explicit action by an authorised user, **only if no grievance is PENDING** and (a non-grievance decision is approved **or** the grievance deadline elapsed with no grievance filed) |
| محفوظة | (stays) | a grievance is filed → recorded PENDING; path after disposition is D-11 (BLOCKED) |

Forbidden: any move out of مغلقة; closure before the deadline without a non-grievance decision; **closure while a grievance is PENDING**; closure by a timer; second referral; referral of a محفوظة or مغلقة complaint (unless D-11 says otherwise); deleting a complaint, session or request (soft removal with history only).
Every transition writes `complaint_status_history` and an audit event.
