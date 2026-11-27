# LD-DOC-R6-08 — Round 5 closure of the four P1 findings (M0, PROPOSED)

Scope: the four findings left open by the independent QA Round 4 review of `de23b53` (`qa/independent/r6-m0-pr115/ROUND-4.md` on branch `qa/independent-r6-m0-pr115-r4-20261127`): **QA-R6-M0-001, -002, -006, -009**. Nothing here is built in the product: no application code, migration or deployment change. **Where this document differs from `LD-DOC-R6-07` or `-05`, this document governs.** The author closes no finding.

Baseline: `origin/develop` = `92822792` (PR #116 merged into this branch by a merge commit). Between `18822a8` and `92822792` no migration, production source, frontend or deployment file changed (three backend **test** files did), so the code citations of 05 and 07 still hold.

## 0. Evidence and how to read it

`docs/evidence/DOC-R6-M0/pg18-probe-r5/` holds a **reduced model** on a disposable PostgreSQL 18.6 with the real develop schema (68 migrations), synthetic rows, and regression tests that assert SQLSTATEs and unchanged rows. The Round-4 probe (`pg18-probe/`) is kept unedited. **VERIFIED-PG18** = executed on that model; **DESIGNED** = specified, not executed. The model proves PostgreSQL privilege, trigger, constraint and lock behaviour; it proves **no Spring code**.

## 1. QA-R6-M0-001 — two people for every legal-effect operator operation

Replaces the Tier B function list of 07 §2.7 and the operator parts of 07 §2.8.

**Rule.** Apart from request handling (`r6_op_request`, `r6_op_confirm`, `r6_op_cancel`) and two read functions (`r6_op_list`, `r6_op_facts`), **every function executable by an operator takes exactly one argument, a request id**, and obtains its payload only from the private `r6_op_consume(request, kind)`. There is no single-call form. A future operator function must follow the same shape; the catalog test T-DB-14 fails otherwise. **VERIFIED-PG18.**

| Check (inside the privileged function) | Refusal |
|---|---|
| the request exists | `LD051` |
| its kind is the executor's kind | `LD052` |
| its state is `CONFIRMED` (not open, executed or cancelled: this also refuses replay) | `LD052` |
| not expired (24 hours, a PROPOSED parameter, D-09) | `LD056` |
| the executing login is the requester or the confirmer | `LD057` |
| requester and confirmer are registered, active operators and **different persons** | `LD057` / `LD050` |
| the stored content still matches `payload_sha256` = SHA-256 over kind, payload, document reference and document hash | `LD054` |
| the payload has exactly the keys and value types of its kind | `LD053` |

* **Identities.** The group `lawdesk_operator` is `NOLOGIN`; each person has an own login; `operator_identity(login, person_ref unique, active)` is written only at provisioning. `session_user` (not `current_user`) identifies the login.
* **Confirmation.** The confirmer passes the hash of what they reviewed (`r6_op_confirm(id, sha256)`); `r6_op_list()` shows payload, hash, document reference and document hash.
* **Immutability.** A trigger that applies to every role, the owner included, refuses any change of content, any backward or skipped state transition, any change of a recorded confirmation, deletion, and insertion of a pre-confirmed row (`LD055`). Application and operator roles have no DML at all (`42501`).
* **Single use.** The request row is locked and marked `EXECUTED` in the same transaction as its effect; a concurrent second execution waits and is refused. If the effect is refused, the mark rolls back with it.
* **Stale decisions.** Executing a revocation, a gate-approval withdrawal, a policy change or a bootstrap revocation **cancels** pending requests that could undo it.

| Kind | Effect | Extra rule |
|---|---|---|
| `LEGAL_APPROVAL_RECORD` | records an approval version (state `RECORDED`, no effect yet) | one row per gate and version |
| `GATE_ACTIVATE` | the recorded approval becomes `ACTIVE` | payload names id, gate and version; one active version per gate |
| `LEGAL_APPROVAL_WITHDRAW` | `WITHDRAWN` | cancels pending activations |
| `AUTHORITY_GRANT` | one active grant with explicit scope | never `LEGAL_AUTHORITY_CUSTODIAN` |
| `AUTHORITY_REVOKE` | revokes | cancels pending grants of the same pair |
| `POLICY_SET` | `P-MANIFEST` / `P-DECLASS` on or off | cancels other pending changes of that policy |
| `BOOTSTRAP_OPEN` | two nominees, token hashes, 24 h | refused while a custodian is active or a bootstrap is open |
| `BOOTSTRAP_COMPLETE` | two cross-approved custodian grants | refused until both nominees confirmed (`LD058`) |
| `BOOTSTRAP_REVOKE` | revokes the bootstrap grants | fail-closed afterwards |

Nominee confirmation (`r6_bootstrap_confirm(token, user)`, Tier A) returns `false` for a wrong, expired or reused token and counts each wrong token. All nine kinds are **VERIFIED-PG18**. Delegation-rule operations and the hash-chained `security_event` (07 §2.8–2.9) are **DESIGNED** and bound to the same request mechanism.

**What this does not prove.** Two logins are two persons only because provisioning says so: a superuser or the owner credential can register false identities or disable the trigger. Two colluding operators can still act. These are D-09 matters.

## 2. QA-R6-M0-002 — complaint reads

Replaces the `lawdesk_legal` rows of 07 §2.2 and §2.4 and row S2 of 07 §2.10.

**Change (option b of the QA report).** No application credential (`lawdesk_app`, `lawdesk_legal`, group `lawdesk_dml`, operators) holds **any** privilege, `SELECT` included, on class-C tables (`complaint`, `complaint_access`, `complaint_case_link`, `referral_draft`; the same rule for complaint documents and search rows when they exist). The legal credential reads only through owner-owned functions that apply the visibility rule of 05 §B.2(1) — active `COMPLAINT_VIEW` grant **and** (responsible researcher, or access list, or grant scope covering the owning unit), user enabled:

| Function (`lawdesk_legal` only) | Returns | Hidden record |
|---|---|---|
| `r6_complaint_list(actor)` | visible complaints | absent |
| `r6_complaint_get(actor, complaint)` | one complaint | `LD020`, identical to an unknown id |
| `r6_complaint_case(actor, complaint)` | linked case id | `LD020` |
| `r6_case_complaint(actor, case)` | complaint id | `NULL`, same as "none" |
| `r6_referral_draft_get(actor, complaint)` | draft | `LD020` |

The visibility predicate itself is not executable by any application role (it would be a membership oracle). **VERIFIED-PG18.**

**Application rules (DESIGNED, M4; T-CONF-20).** The actor argument is taken only from the authenticated server-side principal (reloaded per request), never from a request parameter, header or body; only the complaint adapter package may call these functions (ArchUnit); the application applies `case_visible` before showing a case identifier, and its own check before every call.

### 2.1 The remaining trust boundary — stated, not accepted
**The database cannot tell who the human is. The actor id is supplied by the caller.** Therefore:
* a holder of the `lawdesk_legal` password, a backend with remote code execution, or SQL injection that can run chosen statements on the legal pool **can name any authorised user and read everything that user can read**;
* such an authorised user is easy to find, because grants are readable (the principal loader needs them);
* the same applies to Tier A **writes** (07 RR-1): the database checks that the named person holds the grant, not that the person is present.

What the change does achieve: no complaint data is reachable by a plain `SELECT` (a query that forgot its predicate, a reporting or search query, an injection limited to reading tables); every read path has one visibility rule. It does **not** protect against theft of the legal credential. The test `test_RESIDUAL_…` demonstrates this on purpose.

**Owner decision D-09 — PENDING (nothing is accepted on the owner's behalf).** Options: (A) accept the residual for reads and writes, with credential separation, logging and containment; (B) a separate complaint/approval service outside the backend that holds the legal credential and verifies the user's session itself; (C) a per-user database identity bound to the login (largest operational change). M4 must not start until the owner has chosen.

## 3. QA-R6-M0-006 — the guards

Replaces the guard description of 07 §2.5 and invariant I-4 of 07 §3.2.

**Protected deadline** (evaluated on the **OLD** row for update and delete, and on the **NEW** row for insert and update): its source is a linked judgment; **or** it is referenced by `judgment_party_deadline`; **or** by `deadline_calculation_evidence`; **or** by the `appeal_deadline_id` of a linked judgment. Association protects even if the source columns no longer say so.

**On a protected row** only `notes`, `owner_id`, `version`, `updated_at`, `updated_by` may change. This is an allow-list: `source_*`, `case_id`, dates, period, rule, calculation, calculation status, approval, completion, proof, action and **any column added later** are refused (`LD002`), as are delete, insert onto a linked judgment, moving a free deadline onto one, and upsert. **VERIFIED-PG18**, column by column.

**Linked judgment:** `id`, `proceeding_id` (new), `judgment_date`, `outcome`, `final`, `appeal_deadline_id` and delete are refused (`LD001`).

**Writer discriminator, corrected.** A write counts as a writer's only if `current_user = lawdesk_owner` **and** `pg_trigger_depth() = 1`.

> **Found during this remediation, not in the QA report (developer finding DEV-R5-1).** PostgreSQL runs referential actions as the table owner. On the Round-4 model the **ordinary runtime credential** could set `lawdesk.archive_purge`, delete the completion-proof document of a protected deadline, and `ON DELETE SET NULL` erased `proof_document_id` under `current_user = lawdesk_owner`. Reproduced; refused now. Such nested writes have trigger depth above 1 and are judged like any application write. `deadline.proof_document_id` is today the only referential **write** into `deadline`, `judgment` or `legal_case`; T-DB-15 fails when another appears.

**Behaviour change to carry into M1:** the existing archive purge of a document that is the completion proof of a protected deadline is now refused with `LD002` (the whole purge rolls back); the application needs a stable error for it. Unlinked deadlines are unaffected (**VERIFIED-PG18**).

**Races.** A legacy insert from a judgment takes `FOR SHARE` on that judgment, so it waits for a concurrent registration and is then refused. A legacy update waits on the deadline row lock held by a writer that adopts it, then sees the association. Both **VERIFIED-PG18**, and both require M1 writers to lock the judgment (registration) and the deadline (adoption) `FOR UPDATE`.

| Guard | Decides from | Result of the review |
|---|---|---|
| `r6_guard_deadline` | OLD and NEW + associations | corrected |
| `r6_guard_judgment` | OLD | `proceeding_id`, `id` and delete added; depth rule |
| `r6_guard_case_confidentiality` | OLD | depth rule |
| `r6_guard_role_permission` | NEW (it forbids creating a value) | correct as is |
| `r6_guard_case_grant`, `r6_guard_case_assignment` | — | **DESIGNED only, never executed**; M5 must prove them with OLD and NEW |

## 4. QA-R6-M0-009 — the referral writer creates the case

Replaces 07 §5.3 steps 4–6, §5.4 "one INSERT by `insertIntake`", and the retry row of §5.6. `r6_complete_referral(complaint, case, actor)` is **removed**; `created_at` is not used as proof of anything.

`r6_open_referral_case(complaint, actor, idempotency_key) → (case_id, reference_no, replayed)` — legal credential only; **no case identifier is accepted**. In the caller's transaction:
1. lock the complaint; invisible or unknown → `LD020`; actor without `COMPLAINT_APPROVE` in scope → `LD005`;
2. existing link: same key and same actor → return the same case, `replayed = true`; otherwise `LD021`;
3. status (`LD022`), owner policy `P-MANIFEST` (`LD023`), draft complete, manifest approved by a holder of `COMPLAINT_APPROVE` other than its preparer, mandatory intake values present, responsible researcher enabled (`LD024`);
4. allocate the reference number from the same `case_reference_counter` row as ordinary intake (gapless; rolls back with the transaction);
5. **insert the case itself, `RESTRICTED` in its first and only INSERT**, id generated inside the function, from the approved draft;
6. insert the one link, set the status; invariant R-1 is checked at COMMIT.

**The adapter (M5, DESIGNED)** continues in the **same transaction** on the legal pool with the existing services for everything after the case row: intake validation (run **before** the writer too), duplicate check (a suspicion keeps the draft), parties, first instance, documents per manifest, assignment, timeline, audit `CASE_CREATE` and the referral audit event. A failure rolls back the case, link, status and number. Access to the new case follows the existing restricted-case rules.

**VERIFIED-PG18:** one new case, same transaction id, invisible before commit, `RESTRICTED` at every write; refusals and rollback leave case count, link count, statuses and counter unchanged; duplicate submission returns one case; two concurrent referrals of one complaint give one case; two complaints give consecutive numbers; the link cannot be written, moved or deleted by any application role.

**Still blocked:** D-20, D-25 and `P-MANIFEST`. The policy is OFF; referral remains a draft in any build. It was switched on only inside the disposable database.

**Limits.** (1) The database does not require parties, first instance or assignment to exist at COMMIT: a direct caller holding the legal credential could commit a bare restricted case. Completeness is the adapter's (T-REF-10, T-REF-4); a deferred completeness check is an M5 option. (2) The numbering format now exists in SQL and in `CaseService`: T-REF-11 pins parity. (3) Legacy tables stay writable by the application credentials, so a credential holder can still move **legacy child rows** (documents, proceedings) between cases; that is not linkage of a case, and is not prevented here.

## 5. Stolen credential, restated (replaces 07 §2.10 rows S1–S4)

| Scenario | Can | Cannot |
|---|---|---|
| S1 `lawdesk_app` | read and write legacy tables within the guards; read grants and policies | change a protected deadline or judgment column; read or write class C; call any R6 writer or reader; alter schema |
| S2 `lawdesk_legal` | **name any authorised user: read every complaint that user may read**, and call Tier A writers as that user (referral and declassification still need their policies ON) | `SELECT` class C directly; create a gate, grant, policy or custodian; link a pre-existing case; alter schema |
| S3 backend remote code execution | S1 + S2 at application speed | Tier B facts, schema |
| S4 one operator login | open and cancel requests, read the request list and facts | confirm its own request; execute an unconfirmed one |
| S4b two operator logins of two persons (collusion or theft of both) | every Tier B operation | schema, Tier A case acts |

## 6. Test changes
Seven test ids added: T-DB-14, T-DB-15, T-CONF-19, T-CONF-20, T-DL-11, T-REF-11, T-REF-12 (register 273 → 280). Wording corrected, none deleted: T-DB-4, T-CONF-18, T-REF-1.

## 7. Decisions pending (none made, nothing activated)
* **CITRA Legal Affairs:** D-01–D-05, D-07, D-08, D-10, D-11, D-13, D-16, D-24; all nine gates OFF.
* **Owner:** D-09 — operator persons and provisioning; the residual of §2.1 (option A, B or C); the 24-hour request lifetime; whether a normal grant also needs a custodian's approval in addition to two operators (07 §2.7 puts grants in Tier B; 05 §A.3 mentions custodian approval); how further custodians are added after bootstrap. Also D-12, D-14, D-15, D-17–D-23, D-25. `P-MANIFEST` and `P-DECLASS` OFF.

## 8. Remaining implementation risks
1. No Spring code exists: legal-pool routing, principal-only actor ids, error mapping and the adapter are unproven.
2. The previous-release jar has not been run on the split schema (T-DB-9); the actual ownership of UAT objects is not verified.
3. The probe is a reduced model; the real migrations must be re-attacked, not assumed equal.
4. A `BEFORE` trigger sorting after a guard could change a guarded column after the check; M1 needs a catalog test of trigger order on guarded tables.
5. The depth rule also refuses a future owner trigger that writes a guarded table (fail-closed; it would need an explicit design).
6. Case grant and assignment guards (§3) are not yet executed.
7. A credential holder can roll back a failed bootstrap-token attempt, so the attempt limit protects only the application path; tokens must be long random values.
8. The owner credential and superuser remain outside the model.
9. **Declassification in the model is weaker than the design.** `r6_declassify_linked_case` checks `P-DECLASS` and the approver's grant, but **not** the approval record by a second `COMPLAINT_APPROVE` holder that 05 §B.5 requires (the same reduction as the Round-4 model). The design requirement stands; M5 must prove it (T-CONF-16). `P-DECLASS` is OFF.

## 9. Roadmap after M0 (nothing starts without an independent M0 acceptance **and** the owner's go-ahead)
Each milestone: own branch and draft PR, failing tests committed first, evidence, independent QA acceptance before merge, no UAT change without the owner. Scope and exclusions are those of 04 §D.

| Order | Content | Entry condition | Never in it |
|---|---|---|---|
| M1-pre | database ownership separated from runtime access: roles, ownership transfer, privilege audit, migrate job (T-DB-1…13, T-DB-15) | M0 accepted; owner approval; coordination of deploy files | any R6 table or behaviour |
| M1 | R6 domain and legal **draft** workflows, judgment facts, party-specific deadline drafts, guards on existing tables | M1-pre accepted | due dates, finality, activation |
| M2 | legal settings, two-person approved activation, calculation and evidence, appeals, cassation, finality, decisions | M1 accepted; each effect behind its gate | activating a limit or finality without an approval record |
| M3 | notifications and escalations | M2 accepted | manager recipients before D-14 |
| M4 | public-prosecution complaint management | M3 accepted; **D-09 residual chosen** | referral, grievance deadlines |
| M5 | atomic restricted case referral, decisions, grievances, confidentiality guards | M4 accepted; D-20, D-25 for completion | completion while `P-MANIFEST` is OFF |
| M6 | integration, full regression, security, browser QA, UAT verification | M5 accepted; owner approval for UAT | — |
