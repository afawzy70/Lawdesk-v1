# LD-DOC-R6-07 — Architecture closure appendix (Round 4, M0, PROPOSED)

Scope: the six findings still open after Round 3 — **QA-R6-M0-001, -002, -006 (P0)** and **-004, -005, -009 (P1)**. Nothing here is built; there is no application code, migration or merge. Where this appendix contradicts `LD-DOC-R6-05`, **this appendix governs** and the superseded passage of 05 carries a pointer. Findings are not closed by the author; the ten findings already CLOSED by Codex are not reopened (§7).

## 0. How the claims are labelled, and what was actually run

| Label | Meaning |
|---|---|
| **VERIFIED-CODE** | read in the develop tree (`18822a8`), cited to file and line |
| **VERIFIED-PG18** | executed against a disposable **PostgreSQL 18.6** (the embedded binaries the repository's own tests use), on the **real develop schema** (all 68 develop migrations applied as `lawdesk_app`, exactly as a fresh deployment does), with synthetic rows; evidence in `docs/evidence/DOC-R6-M0/pg18-probe/` |
| **DESIGNED** | specified here, not executed; the test id says how it will be proved |

The probe is **a reduced model** of the proposed R6 objects (names and shapes follow this appendix) on the real tables `judgment`, `deadline`, `proceeding`, `case_party`, `legal_case`, `role_permission`. It proves PostgreSQL privilege, trigger, locking and constraint semantics; it does **not** prove any Spring code (none exists) and it did not run the previous-release jar. The probe SQL files are `*.sql.txt` (documentation evidence, not migrations); the two small Java runners are in `pg18-probe/tools/`. Reproduce: start any PostgreSQL 18 with scratch roles, apply the develop migrations as `lawdesk_app`, then run the numbered files in order (`README.md` there lists the order and the expected outcomes).

## 1. Finding map

| Finding | Root cause (one line) | Section |
|---|---|---|
| 001 sensitive authorities | the runtime credential **owns** the schema, so every in-database protection (trigger, table, function) is removable by it | §2, §2.8 |
| 002 confidentiality bypass | same ownership flaw; the declassification trigger trusted a same-transaction row; complaint data readable by the runtime credential | §2, §5.5 |
| 006 legal gates / legacy writes | same ownership flaw; provenance by "a row exists in this transaction" is forgeable | §2, §3 |
| 004 deadline creation | invariant I-4 forbade the very insert R6 must make | §3 |
| 005 appeals and proceedings | no keys tie litigants/filings/deadlines/child proceedings to one judgment and one case; lock order contradicts existing code | §4 |
| 009 referral | the link trigger required a status that is set after the link; the restricted-case insert path was not specified | §5 |

## 2. Part A — the database security boundary (001, 002, 006)

### 2.1 Verified baseline
* **VERIFIED-CODE.** `deploy/postgres/app-role.sql:13` `CREATE SCHEMA IF NOT EXISTS lawdesk AUTHORIZATION lawdesk_app;`, `:8` creates the role without any other role, `:11` grants `CONNECT, TEMPORARY`; `deploy/compose.yaml:52-53` runs the backend as `SPRING_DATASOURCE_USERNAME: lawdesk_app`; `backend/src/main/resources/application.yaml:17-24` configures Flyway with no user of its own, so Flyway runs on the **primary datasource, i.e. as `lawdesk_app`**. `deploy/scripts/deploy.sh:99-103` runs `app-role.sql` as the PostgreSQL superuser on every deployment, then starts the backend (Flyway runs inside it).
* **VERIFIED-PG18 (`01`).** After the 68 migrations ran as `lawdesk_app`: schema owner `lawdesk_app`; **92 tables, 6 sequences, 293 indexes, 59 functions all owned by `lawdesk_app`**; 0 objects owned by anyone else. "Schema ownership" and "table ownership" are two different catalog facts (`pg_namespace.nspowner`, `pg_class.relowner`); both are `lawdesk_app`, and §2.3 verifies both.
* **VERIFIED-PG18 (`02`; the QA claim, reproduced).** As `lawdesk_app`: `ALTER TABLE judgment DISABLE TRIGGER …` succeeds; `DROP TRIGGER …` succeeds; `CREATE OR REPLACE FUNCTION` replacing a guard body with `return new` succeeds; `CREATE TABLE lawdesk.forged_policy` succeeds. The previous statement "only a superuser can bypass the triggers" (03 §1.3, 05 §I.2, §A.4(7)) is therefore **withdrawn**: under the present ownership every trigger-based control of 05 is removable by the application's own credential.

### 2.2 Identities (PostgreSQL 18; names are proposals)
| Role | Login | Owns | Privileges | Credential lives in | Used by |
|---|---|---|---|---|---|
| `postgres` | yes | database | superuser | host secrets only (`db_admin_password`) | deploy script, DBA, restore |
| `lawdesk_owner` | yes (migrate job only) | **schema, every table, sequence, function, trigger** | all on its own objects; no superuser/CREATEROLE/CREATEDB/REPLICATION/BYPASSRLS | `db_owner_password`, mounted **only in the migrate job** | Flyway; owner of all `SECURITY DEFINER` functions |
| `lawdesk_dml` | no (group) | nothing | `USAGE` on schema; `SELECT/INSERT/UPDATE/DELETE` on legacy tables; `USAGE/SELECT` on sequences; **no** `TRIGGER/TRUNCATE/REFERENCES/CREATE`; **no** `TEMPORARY` | — | membership group |
| `lawdesk_app` | yes | nothing | member of `lawdesk_dml`; no EXECUTE on any R6 function; no SELECT on class-C tables (§2.4) | `db_app_password` (backend container) | normal runtime pool; **the previous release** |
| `lawdesk_legal` | yes | nothing | member of `lawdesk_dml`; `EXECUTE` on **Tier A** functions; `SELECT` on class-C tables | `db_legal_password` (backend container) | the **legal pool**: every R6 write and every complaint read/write |
| `lawdesk_operator` | no (group) | nothing | `EXECUTE` on **Tier B** functions only; no table privilege | — | membership group |
| `op_<person>` | yes, one per person | nothing | member of `lawdesk_operator` | host operator store (mode 0600), **never in a container** | the two-person operator procedure (§2.7) |

Memberships and attributes are fixed by `deploy/postgres/roles.sql` (the successor of `app-role.sql`, idempotent, run by the deploy script as superuser). No runtime role is a member of `lawdesk_owner`; none has `CREATEROLE`, so none can grant itself anything (**VERIFIED-PG18**: `SET ROLE lawdesk_owner`, `GRANT lawdesk_owner TO lawdesk_app`, `ALTER ROLE … SUPERUSER`, `CREATE ROLE` all answer `42501`).

### 2.3 Exact PostgreSQL 18 setup and the existing-install transfer
`pg18-probe/10-ownership-split.sql.txt` is the script that was executed, as superuser, on the populated schema (rows existed). In order: create `lawdesk_owner`, `lawdesk_dml`, `lawdesk_legal`, `lawdesk_operator`; `GRANT lawdesk_dml TO lawdesk_app`; **`REASSIGN OWNED BY lawdesk_app TO lawdesk_owner`** (moves the schema and every table, sequence, index and function in the connected database in one statement; **VERIFIED-PG18**: afterwards schema owner = `lawdesk_owner`, tables 92/92, sequences 6/6, indexes 293/293, functions 59/59 owned by `lawdesk_owner`, **0 objects owned by `lawdesk_app`**, row counts unchanged); `REVOKE TEMPORARY ON DATABASE … FROM lawdesk_app`; `GRANT USAGE ON SCHEMA`; `GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES` and `USAGE, SELECT ON ALL SEQUENCES` to `lawdesk_dml`; `REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON flyway_schema_history FROM lawdesk_dml` (the runtime role may *read* the history, as an older release's Flyway needs, but never write it); **default privileges** `ALTER DEFAULT PRIVILEGES FOR ROLE lawdesk_owner IN SCHEMA lawdesk GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES / USAGE, SELECT ON SEQUENCES TO lawdesk_dml` (so migrations of other workstreams — R5's V860…V881 — that know nothing about this model keep working) and `ALTER DEFAULT PRIVILEGES FOR ROLE lawdesk_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC`; role-level `search_path = lawdesk`. A fresh install runs the same script before the first migration. `ALTER DEFAULT PRIVILEGES` only affects objects later created by `lawdesk_owner`, which is why Flyway **must** run as that role (§2.9).

**Verification (mandatory, run by the deploy script after migrations and after any restore; fails the deploy on any drift — T-DB-1, T-DB-8, T-DB-12).** Catalog assertions: (a) `pg_namespace.nspowner`, every `pg_class.relowner` and `pg_proc.proowner` in schema `lawdesk` equal `lawdesk_owner`; (b) no runtime/legal/operator role is superuser, `rolcreaterole`, `rolcreatedb`, `rolreplication`, `rolbypassrls`; none is a member (direct or indirect) of `lawdesk_owner`; (c) `has_schema_privilege(role,'lawdesk','CREATE')` and `has_database_privilege(role,'lawdesk','CREATE')` and `TEMPORARY` are false for them; (d) `has_table_privilege(r,t,'TRIGGER'|'TRUNCATE'|'REFERENCES')` false for every table; (e) for every table on the **protected list** (class R and C, §2.4) the runtime group holds no `INSERT/UPDATE/DELETE`, and no `SELECT` on class C; (f) for every function on the **function list** (§2.6) `proconfig` pins `search_path`, no `PUBLIC` EXECUTE, grantees equal the catalogue. The protected list is generated from the migrations of each milestone and committed; a protected table missing from it fails the build.

### 2.4 Table classes
| Class | Tables | Runtime group (`lawdesk_dml`) | `lawdesk_legal` | Written by |
|---|---|---|---|---|
| L legacy | every table that exists on develop and the R5/other-workstream tables | SELECT/INSERT/UPDATE/DELETE, **except** the guarded columns of §3.3 and §5.5 | same | legacy services |
| R R6-protected | `legal_approval`, `legal_authority_grant`, `legal_authority_delegation`, `owner_policy`, `sensitive_permission`, bootstrap tables, `security_event` chain, `deadline_calculation_evidence`, `judgment_procedural`, `judgment_litigant`, `judgment_party_deadline`, `judgment_appeal`, `proceeding_joinder`, `judgment_finality`, `appeal_decision`, history tables, `complaint_case_link` | **SELECT only** (not on class C) | SELECT only | owner-owned functions only (Tier A or B) |
| C confidential | `complaint*`, `complaint_case_link`, `referral_draft`, complaint documents/search rows | **nothing, not even SELECT** | SELECT | owner-owned functions only |

Reasoning: if *every* R6 write goes through a function, then a stolen or injected runtime credential can neither forge nor tamper with R6 data, and the **legal pool** is the only door. **VERIFIED-PG18 (`30`, `31`):** as `lawdesk_app` and as `lawdesk_legal`, `INSERT/UPDATE/DELETE` on `legal_approval`, `legal_authority_grant`, `owner_policy`, `deadline_calculation_evidence`, `complaint_case_link` all answer `42501`; `SELECT` on `complaint` and `complaint_case_link` answers `42501` for `lawdesk_app`.

### 2.5 Guards on existing tables: discriminate by `current_user`, not by a row
A guard on `judgment`, `deadline`, `legal_case`, `role_permission`, `case_grant`, and the case-assignment columns is an **invoker-rights** trigger function (no `SECURITY DEFINER`) that raises when `current_user <> 'lawdesk_owner'` and the row is protected. Inside an owner-owned `SECURITY DEFINER` writer function `current_user` is `lawdesk_owner`; in every other session it is the login role, and a runtime role **cannot become the owner** (no membership, no `SET ROLE`). **VERIFIED-PG18 (`70-72`):** the same trigger reported `current_user=lawdesk_app` for a direct write and `current_user=lawdesk_owner` when the write happened inside the definer function; a trigger function declared `SECURITY DEFINER` always reports the owner, which is why it must not be used as a guard. The runtime role cannot execute the guard functions (`has_function_privilege = false`) yet the guards fire on its DML (triggers do not check EXECUTE). "A row exists in this transaction" (the Round-3 mechanism: `xid = txid_current()`) is **withdrawn**: any role that could insert that row could forge it.

The guards are defence against **legacy paths and the previous release** and against a runtime credential used outside the writers; they are not the only barrier (the class-R tables have no DML at all).

### 2.6 `SECURITY DEFINER` rules (all functions of the catalogue below)
1. **Owner:** `lawdesk_owner`; created by a migration.
2. **EXECUTE:** `REVOKE ALL … FROM PUBLIC`, then a named grantee: `lawdesk_legal` (Tier A), `lawdesk_operator` (Tier B), `lawdesk_dml` only for pure read helpers that return one boolean (`r6_case_is_complaint_linked`). **VERIFIED-PG18:** `has_function_privilege` matrix — runtime `false/false`, legal `true` on Tier A, operator `true` on Tier B, PUBLIC `false`; a runtime call of a Tier A or B function answers `42501`.
3. **`search_path`:** `SET search_path = pg_catalog, lawdesk, pg_temp` on the function (`pg_temp` last), and every object name schema-qualified. **VERIFIED-PG18 (`72`):** with `TEMPORARY` deliberately re-granted, a caller created `pg_temp.legal_approval` containing a forged ACTIVE row; a definer function **without** a pinned path returned `true` for a gate that has no real approval; the pinned function returned `false`. The runtime roles also hold no `TEMPORARY` (`create temp table` answered `42501`).
4. **No dynamic SQL:** functions contain no `EXECUTE`/`format()` building statements; the catalog audit (T-DB-6) greps `prosrc` and fails the build otherwise. Inputs are typed parameters (uuid/date/int/text/jsonb), never SQL fragments.
5. **Custom SQLSTATEs** (class `LD`) carry the refusal reason; the application maps each to a stable problem code (§3.5, §5.6). Messages never include confidential values.

### 2.7 Who may do what: three trust tiers
The database cannot identify the human behind a `lawdesk_legal` connection; it can only prove *which credential* connected and *what facts exist*. The design therefore never lets a credential-level caller create the **facts** that legal effect depends on.

| Tier | Boundary | Writes | Authentication of the actor |
|---|---|---|---|
| **B — operator** | host-side procedure; per-person `op_<person>` logins that exist nowhere in the application; `lawdesk_operator` functions require **two different `session_user`s** (requester ≠ confirmer) and a document reference + SHA-256 | legal approvals and their activation (gates), grants/revocations/delegation rules of sensitive authority, owner policies `P-MANIFEST`/`P-DECLASS`, first-custodian bootstrap, credential containment | the operators' own database identities, recorded as `session_user` in the hash-chained `security_event`; the *legal* approver is a CITRA Legal Affairs signatory whose signed document hash is recorded |
| **A — application** | the backend's **legal pool** (`lawdesk_legal`) executing owner-owned writer functions inside one transaction | per-case legal acts: activate/recalculate a party deadline, record filings/joinder, confirm finality, approve non-appeal, referral, declassification, linked-case grants/assignment | the Spring session (principal reloaded on every request) + `AuthorityScope.require` + an **independent DB-side check** that the named approver holds an ACTIVE, in-scope grant (a Tier B fact), that preparer ≠ approver, and that the required gate/policy facts (Tier B) are ACTIVE |
| **O — owner** | `lawdesk_owner` (migrate job) and `postgres` | schema | not an application path |

**VERIFIED-PG18 (`31`–`33`, `80`–`84`):** the legal role calling an operator function → `42501`; the operator calling a Tier A function → `42501`; the legal role before any Tier B fact exists → `LD004 gate not active` / `LD011 policy off`, nothing written; after the operator recorded the approval and the grants, the same call succeeds once; preparer = approver → `LD003`; approver without grant → `LD005`; unknown judgment → `LD006`; revocation by two operators then makes the next activation `LD005`.

**Function catalogue (grantee in brackets).** Tier B [`lawdesk_operator`]: `r6_op_request`, `r6_op_confirm`, `r6_op_list`, `r6_op_record_legal_approval`, `r6_op_withdraw_legal_approval`, `r6_op_activate_gate`, `r6_op_grant_authority`, `r6_op_revoke_authority`, `r6_op_set_policy`, `r6_op_bootstrap_open/complete/revoke`, `r6_op_chain_head`. Tier A [`lawdesk_legal`]: `r6_register_judgment`, `r6_save_party_deadline_draft`, `r6_activate_party_deadline`, `r6_recalculate_party_deadline`, `r6_adopt_legacy_deadline`, `r6_record_filing`, `r6_void_filing`, `r6_withdraw_filing`, `r6_record_joinder`, `r6_record_citra_decision`, `r6_confirm_no_appeal`, `r6_confirm_finality`, `r6_save_referral_draft`, `r6_complete_referral`, `r6_declassify_linked_case`, `r6_grant_linked_case_access`, `r6_assign_linked_case`, the complaint-domain writers, `r6_bootstrap_confirm`. Guards (invoker, owner-only EXECUTE): `r6_guard_judgment`, `r6_guard_deadline`, `r6_guard_case_confidentiality`, `r6_guard_role_permission`, `r6_guard_case_grant`, `r6_guard_case_assignment`, the deferred `r6_check_referral_invariant`, the append-only guards. Read helper [`lawdesk_dml`]: `r6_case_is_complaint_linked(uuid) → boolean`. Of these, the probe **executed**: `r6_op_record_legal_approval`, `r6_op_grant_authority`, `r6_op_set_policy`, `r6_op_request/confirm/list/revoke_authority`, `r6_activate_party_deadline`, `r6_declassify_linked_case`, `r6_complete_referral`, `r6_case_is_complaint_linked`, `r6_guard_judgment`, `r6_guard_deadline`, `r6_guard_case_confidentiality`, `r6_guard_role_permission`, `r6_check_referral_invariant`; the remainder are DESIGNED with the same mechanism.

**Two-party authorization (VERIFIED-PG18, `80`–`83`).** Operator A opens a request (`session_user` recorded); the same identity trying to confirm it is refused (`LD050`); operator B confirms; the executor refuses an unconfirmed request (`LD052`) and runs only a confirmed one. The runtime and legal roles cannot call any of it. Requests carry the SHA-256 of the signed CITRA Legal Affairs / owner document; the function stores the hash and the reference, never the document.

### 2.8 First-custodian bootstrap (replaces 05 §A.4 steps 2–3 and 7)
1. **Owner authorization** outside the system (D-09): a signed document naming two nominees N1 ≠ N2 (enabled `app_user`s) and two operators; its SHA-256 and reference are entered.
2. **Operator A** opens `r6_op_bootstrap_open(owner_auth_ref, sha256, N1, N2)`; **operator B ≠ A** confirms it. The function refuses if any ACTIVE custodian grant exists, so it cannot add a custodian next to existing ones. It stores, per nominee, a token hash (`sha256(token)`), expiry 24 h, attempt counter; the operators hand the plaintext tokens to the nominees on a different channel.
3. **Nominee confirmation** is the single narrow Tier A function `r6_bootstrap_confirm(token, user_id)` (executable by the legal pool: this resolves the Round-3 defect that the confirmation endpoint had nowhere to persist). It checks token hash, expiry, ≤5 attempts, single use and that `user_id` is the nominee; it records the confirmation and grants nothing.
4. **Completion** is Tier B again: `r6_op_bootstrap_complete` (two operators) requires both confirmations and creates the two cross-approved `LEGAL_AUTHORITY_CUSTODIAN` grants, scope ALL, in one transaction.
5. **Evidence:** `security_event` (class R, insert-only through the writers, hash-chained `sha256(prev ‖ payload)`); `r6_op_chain_head()` prints the head, and the operators copy it to the host ledger after every ceremony. **Revocation:** `r6_op_bootstrap_revoke` (two operators, a second owner authorization) revokes every BOOTSTRAP grant; the system is then fail-closed (no custodian, every Tier A approval refused). **Lost custodians:** the same procedure with a fresh owner authorization; there is no "break-glass" that skips the ceremony.
6. Honest limit: a database **superuser** or the owner credential can still rewrite rows; those are detected, not prevented (§2.10).

### 2.9 Credentials, logging, rotation, revocation, recovery
* **Separation.** Secrets and where they live: `db_admin_password` (host only), `db_owner_password` (migrate job only), `db_app_password` + `db_legal_password` (backend container), operator passwords (host operator store). T-DB-13 asserts the compose file mounts no owner or operator secret into the backend container. The backend image must start without Flyway privileges: `SPRING_FLYWAY_ENABLED=false` for the backend service, and a one-shot **migrate job** (same image, `com.lawdesk.platform.MigrateOnly`, ~20 lines, run before `up`) performs Flyway as `lawdesk_owner`. Tests and local development keep the single-superuser setup. Changes to `deploy/compose.yaml`, `deploy/scripts/deploy.sh` and `deploy/postgres/` are **M1 work needing the owner's approval and a coordination window with Codex**; nothing in this repository's deployment is changed in M0.
* **Logging.** PostgreSQL `log_connections`, `log_disconnections`, `log_statement = 'ddl'`, `log_line_prefix` including user and database (owner-gated server setting); every Tier B action appends `security_event` (session_user, request id, document hash, chain hash); the application audits every Tier A act with the acting `app_user`.
* **Rotation.** `ALTER ROLE … PASSWORD` by the deploy script for `lawdesk_app`/`lawdesk_legal` at each deploy or on a schedule the owner sets (proposal: every 90 days and on any suspected exposure; this is an owner policy, not a legal gate); operator passwords by the operators; owner password only at a migration window.
* **Revocation / containment.** `ALTER ROLE lawdesk_legal NOLOGIN` + `pg_terminate_backend` (**VERIFIED-PG18 `85`**: needs neither the application nor the credential): every R6 write then fails closed while legacy operations through `lawdesk_app` continue; re-enabling restores. A compromised operator login is `NOLOGIN`-ed and its `security_event` entries reviewed.
* **Recovery.** Backup and restore keep ownership (`pg_dump -Fc` records owners and ACLs; restore as superuser into a database where the roles already exist), then **the privilege audit of §2.3 runs before the application starts** (T-DB-12). The old `app-role.sql` that an older release carries only does `CREATE SCHEMA IF NOT EXISTS` (a no-op on an existing schema), `ALTER ROLE lawdesk_app …` and `GRANT CONNECT, TEMPORARY`; it neither reverts ownership nor removes a grant, but it **re-grants `TEMPORARY`**, harmless to pinned functions (§2.6.3) and corrected by the next audit.

### 2.10 What is and is not protected if a credential is compromised
| Scenario | Attacker can | Attacker cannot | Residual / mitigation |
|---|---|---|---|
| **S1** runtime credential `lawdesk_app` stolen, or SQL injection under it (**VERIFIED-PG18**) | read and write legacy tables, subject to the guards of §3.3/§5.5 | alter/disable/replace any trigger, function, table or default privilege; write any class-R or class-C table; read class C; execute any Tier A/B function; declassify a linked case; change a protected judgment/deadline column; create a role | none beyond legacy data integrity; digests unchanged in `30`/`34` |
| **S2** `lawdesk_legal` stolen | call Tier A functions with any actor id | forge a gate, a grant, a policy, a bootstrap, an operator action, evidence outside a writer; skip a gate; bypass maker-checker; alter schema | RR-1: Tier A acts are attributable only to users who **really hold** an active grant, only inside active gates; detective: audit + chain; containment: `NOLOGIN` (§2.9) |
| **S3** backend RCE (both pool credentials) | everything in S2, at application speed | same as S2: Tier B facts, schema, evidence chain | RR-1; owner may choose D-09 sub-option **"Tier A out of process"** (a separate approval service with its own credential and step-up login; not designed here, needs ops approval) |
| **S4** one operator identity stolen | open requests | confirm its own request (`LD050`) | needs a second identity |
| **S5** `lawdesk_owner` credential stolen | everything on the schema | — | held only by the migrate job; used in a window; rotate after every migration; detective chain |
| **S6** superuser / host root | anything | — | **not preventable**; detective: independent chain head, reconciliation (I-7), PostgreSQL logs |
Owner decision D-09 is extended (still OWNER-PENDING): allocation of the sensitive authorities, the **two operator identities**, the rotation policy, and whether Tier A must run out of process. D-20 (`P-MANIFEST`, `P-DECLASS`) is unchanged and both stay OFF.

### 2.11 The three legal controls against this model
| Control | Facts only Tier B can create | Tier A check | DB barrier for everything else |
|---|---|---|---|
| Legal finality (G-FINAL, D-07 BLOCKED) | the ACTIVE `legal_approval` for G-FINAL and its activation | `r6_confirm_finality`: gate ACTIVE, approver grant, maker-checker, children/filings/litigants preconditions | `judgment.final` is a guarded column: any other writer → `LD001` |
| Sensitive authority grants | `legal_authority_grant` rows | `AuthorityScope` re-reads them in the transaction; a Tier A function re-checks in the DB | no DML on the table; `role_permission` guard `LD060` (**VERIFIED-PG18 `26`**) refuses sensitive codes for every role, the owner included |
| Complaint declassification | policy `P-DECLASS` ON, the grant `COMPLAINT_APPROVE` | `r6_declassify_linked_case`: policy ON, approver grant, approval record | confidentiality guard `LD010` for every non-owner write (**VERIFIED-PG18 `30`, `31`**: `LD010` for the runtime and the legal role; the writer refuses with `LD011` while the policy is OFF) |

### 2.12 Previous-release behaviour (T-DB-9, T-MIG-3; DESIGNED + partial VERIFIED-PG18)
The previous jar connects as `lawdesk_app` and runs Flyway: with nothing pending Flyway reads `flyway_schema_history`, takes an advisory lock and writes nothing — **VERIFIED-PG18 (`53`)**: the role can `SELECT` the history and take the advisory lock, and cannot insert into it or create a table. Unlinked judgments and deadlines behave exactly as before (`53`: final/outcome/date edits, a JUDGMENT-sourced deadline, the pointer and a completion all succeeded for an unlinked judgment). Writes to linked records fail closed (`LD001`/`LD002`; the HTTP status the old release maps them to is **not asserted**; digests are). **Not verified:** the actual jar (not run in M0). Because the old release runs Flyway as `lawdesk_app`, an old release whose own pending migrations need DDL cannot be installed on the split schema: that is a **supported-direction limit** (new code on an old schema was already unsupported, 03 §1.3) and is recorded for the owner.

### 2.13 Proof plan for Part A (all must be rejected with protected records unchanged)
| Required proof | Probe (VERIFIED-PG18) | Future test |
|---|---|---|
| runtime role attempts ALTER on protected tables | `30` §A | T-DB-2 |
| attempts to disable triggers | `30` §A, `31`, `session_replication_role` | T-DB-2, T-DB-3 |
| attempts to forge approvals and grants (direct DML, function calls, temp-table shadowing) | `30` §B, `31`, `32`, `72` | T-DB-4, T-DB-5, T-DB-7, T-SEC-17 |
| attempts to declassify a linked complaint case | `30` §C, `31` | T-CONF-14, T-CONF-16, T-CONF-18 |
| unauthorized legacy writes against R6-linked judgments/deadlines | `30` §D, `34` | T-LEG-1…10, T-DL-6 |
| previous release against the migrated schema | `53` (statements), jar run deferred | T-DB-9, T-MIG-3 |
`run-transcript.txt` shows the digest of the protected parts identical before and after every refused step (`30`, `31`, `34`) and changed only by the two authorised steps (`32` operator facts, `33` Tier A activation).

## 3. Part B — authorized creation of a party deadline (004, 006)

### 3.1 Root cause and baseline
**VERIFIED-CODE.** V843 requires the pointer's deadline to have `source_type='JUDGMENT'` and `source_id = judgment.id`; `deadline` (V681) has no column saying "R6"; 05 §C.2 stores due dates **only** in `deadline`; yet 05 §I I-4 refused every INSERT of a deadline whose source is a linked judgment. The two statements cannot both hold. The fix is not a broader exception ("same-xid evidence exists") but a **discriminator no unauthorised role can imitate** (§2.5): the only roles that may write a legal column of a linked deadline are the owner-owned writer functions.

### 3.2 Corrected invariants (replace 05 §I.2)
* **I-1** `judgment.final`, **I-2** `judgment.appeal_deadline_id`, **I-3** `judgment_date`/`outcome` (for a linked judgment): changed only when `current_user = lawdesk_owner`. A judgment is *linked* iff it has a `judgment_procedural` row. Other columns (summary, operative text, details, `version`, audit columns) stay writable by legacy services (**VERIFIED-PG18 `30` §E**).
* **I-4** `deadline`: INSERT, UPDATE of any legal column (`starts_on, period_days, due_on, rule_id, calculation, calc_status, approved_by/at, status, completed_on, proof_document_id, change_reason, source_*, action, case_id`) and DELETE of a row whose `source_type='JUDGMENT'` and whose `source_id` is a linked judgment, or that is referenced by a `judgment_party_deadline` row, require `current_user = lawdesk_owner`; `notes` and `owner_id` stay editable. This **resolves the contradiction**: the legacy API (lawdesk_app), the legal pool outside a writer, direct SQL and the previous release are refused; the writer inserts.
* **I-5** association: `judgment_party_deadline.deadline_id` and `deadline_calculation_evidence` are class R (no DML for any application role) — the Round-3 "approval row in this xid" check is replaced by privilege, plus a deferred constraint at commit: a party row is `EFFECTIVE` ⇔ it has a `deadline_id` ⇔ at least one evidence row exists for that deadline (T-DL-2).
* **I-6** finality preconditions are checked inside `r6_confirm_finality` after locking (§4.5), including *no child proceeding of the judged proceeding without a mapped non-VOID filing*.
* **I-7** reconciliation (nightly/startup, read-only) stays as a **detective** control for S5/S6 only.
**VERIFIED-PG18 (`30` §D, `34`):** direct `UPDATE judgment SET final/outcome/judgment_date/appeal_deadline_id`, `INSERT deadline … source JUDGMENT`, and `UPDATE/DELETE` of an R6 deadline by `lawdesk_app` and (for final/confirmation) by `lawdesk_legal` answered `LD001`/`LD002`; an unlinked judgment and a `TEXT` deadline remained writable.

### 3.3 Guarded columns — summary
`judgment`: `final, appeal_deadline_id, judgment_date, outcome` (linked only). `deadline`: legal columns above (linked only). `legal_case`: `confidentiality` (linked to a complaint; §5.5). `role_permission`: any sensitive code (always). `case_grant`, `case assignment`: linked-to-complaint cases (§5.5).

### 3.4 Transaction TX-DRAFT — no legal effect (works with every gate closed)
`r6_save_party_deadline_draft(litigant, appeal_type, start_on, start_basis, preparer)`, legal pool, one transaction: authenticate (session, `POSTJ_PREPARE`, `CaseAccessPolicy.requireChange` → 404/403 **before** validation) → lock tree (§4.5) → validate: judgment linked, not final, case writable, litigant belongs to the judgment (key-backed), no live row for the litigant → INSERT `judgment_party_deadline(status='DRAFT', deadline_id NULL)` + history + audit. **Creates no `deadline`, no evidence, no pointer change** (T-DL-1: digests of `deadline`, `judgment`, evidence unchanged). Refusals: `LD006` not found, `LD007` judgment final, `LD009` litigant mismatch, `LD008` live row exists.

### 3.5 Transaction TX-ACTIVATE — the exact successful sequence
User-visible order mapped to the nine requested steps; validation that reads mutable state is repeated **under the locks** (validate → lock → re-validate) so a race cannot slip through.

| # | Step | Where | Detail |
|---|---|---|---|
| 1 | Authenticate and authorize the actor | application, before the transaction | Spring session → `LawdeskUser` (reloaded each request); `@PreAuthorize('POSTJ_APPROVE')` (grant-based, 05 §A); `CaseAccessPolicy.requireChange(caseId)`; the approver is never the preparer. Failure: 401/403/404, nothing opened |
| 2 | Verify approved legal configuration and applicable version | application pre-check, **repeated in step 4** | the setting version applicable to the judgment's registration date (05 §E half-open rule) has an ACTIVE `legal_approval` for G-CALC/G-SETTING (and G-OPPONENT for an opposing litigant), gate activated by Tier B. Failure `LEGAL_GATE_CLOSED` (`LD004`) |
| 3 | Validate judgment and litigant | application pre-check (existence/visibility), **repeated in step 4** | judgment linked, litigant belongs to it (the key of §4.2 makes a wrong pair unrepresentable), draft exists, calculation inputs equal the approved setting |
| — | `BEGIN` on the **legal pool** | | one transaction for steps 4–9 |
| 4 | Acquire locks, then re-validate | `r6_activate_party_deadline` | order of §4.5: `legal_case` **FOR SHARE** → existing child proceedings of the judged proceeding (ascending id; only when mapping/finality needs them) → the judged proceeding → `judgment` → `judgment_procedural` → `judgment_litigant` rows (CITRA first, then id) → the party-deadline row. Then: case not read-only (`LD041`), judgment not final (`LD007`), party row still `DRAFT` (`LD008`), gate/setting/approver-grant/scope/maker-checker (`LD004`, `LD005`, `LD003`), approver is an enabled user |
| 5 | Create the authoritative deadline | same function | `INSERT deadline(source_type='JUDGMENT', source_id=judgment, calc_status='APPROVED', approved_by, approved_at …)` — I-4 passes because `current_user` is the owner |
| 6 | Record immutable calculation evidence | same | `INSERT deadline_calculation_evidence` (inputs incl. the holiday list used, setting id/version, result, approver, `sha256` chained to the previous row of that deadline) — class R, insert-only |
| 7 | Associate the deadline with the litigant | same | `UPDATE judgment_party_deadline SET status='EFFECTIVE', deadline_id=…` (composite keys: §4.2) |
| 8 | Maintain the CITRA `appeal_deadline_id` rule | same | **only if the litigant is the CITRA litigant:** `UPDATE judgment SET appeal_deadline_id = new deadline` (V843 trigger re-verifies source) ; opposing litigants never touch it; recalculation re-points it; NULL when none is effective |
| 9 | Commit atomically | | history row, `audit_event`; deferred constraints of I-5 run at COMMIT; any failure → rollback of steps 4–8 |
**VERIFIED-PG18 (`33`):** the modelled function (steps 4–8: gate, grant, maker-checker, lock, insert deadline, insert evidence, set pointer) produced exactly one deadline, one evidence row and the pointer; the four refusal paths (`LD003`, `LD005`, `LD006`, and gate/policy `LD004`/`LD011`) left the counts at 1/1/1; the second concurrent call is covered by T-DL-5 (DESIGNED).
**Recalculation** (`r6_recalculate_party_deadline`): same locks; the old deadline row is **kept**, its party row becomes `SUPERSEDED`, a new deadline + new evidence are created, the live-row partial unique index (§4.2) admits one live row per litigant. **Adoption** of a legacy deadline (`r6_adopt_legacy_deadline`): reason, evidence row of kind `ADOPTED`, audit; the legacy `deadline` row is not edited.

### 3.6 Rejection paths (stable application errors; the SQLSTATE class `LD` is mapped)
| SQLSTATE | Application error (HTTP) | Zero partial writes shown by |
|---|---|---|
| `LD001` | `JUDGMENT_R6_MANAGED` (409) | `34`, T-LEG-2..4 |
| `LD002` | `DEADLINE_R6_MANAGED` (409) | `34`, T-LEG-5..8 |
| `LD003` | `MAKER_CHECKER_VIOLATION` (403) | `33` |
| `LD004` | `LEGAL_GATE_CLOSED` (409) | `31`, T-GATE-1 |
| `LD005` | `AUTHORITY_REQUIRED` (403; 404 when the case is invisible) | `33` |
| `LD006` | `NOT_FOUND` (404) | `33` |
| `LD007` | `JUDGMENT_FINAL` (409) | T-DL-3 |
| `LD008` | `ALREADY_EFFECTIVE` / `DRAFT_EXISTS` (409) | T-DL-3, T-DL-5 |
| `LD009` | `LITIGANT_MISMATCH` (422) | T-DL-3 |
| `LD041` | `CASE_READ_ONLY` (409) | `60` closure, T-LOCK-6 |
Every refusal rolls back the whole transaction; the log line is the only trace (05 §D.4).

### 3.7 All write paths reconciled (replaces the "R6 guard" columns of 05 §I.1)
| Path (VERIFIED-CODE) | Unlinked judgment/deadline | Linked to R6 |
|---|---|---|
| `POST /proceedings/{id}/judgment` (`JudgmentService.create`, locks the proceeding) | unchanged | n/a (a new judgment is unlinked until `r6_register_judgment`; refused if `final=true`) |
| `PUT /judgments/{id}` (`update`) | unchanged | `final`, `judgment_date`, `outcome` → app `JUDGMENT_R6_MANAGED`, DB `LD001`; summary/operative text/details still work |
| `PUT /judgments/{id}/appeal-deadline` (`setAppealDeadline`) | unchanged | refused, app + `LD001` |
| `POST /cases/{id}/deadlines`, `…/propose` (`DeadlineService.create/propose`) | unchanged | `source_type='JUDGMENT'` with a linked judgment → `DEADLINE_R6_MANAGED` / `LD002` |
| `PUT /deadlines/{id}` (`update`) | unchanged | legal columns refused; `notes`, `owner_id` allowed |
| `POST /deadlines/{id}/approve` | unchanged | refused (`LD002`); the R6 approval is TX-ACTIVATE |
| `POST /deadlines/{id}/status` | unchanged | refused; resolution is written by `r6_record_filing`/`r6_confirm_no_appeal` |
| `POST /deadlines/reminders/run`, `DeadlineReminderSchedule` | reads | M3 excludes linked deadlines (05 §C.2) |
| `DeadlineSettingsController` rules/holidays/lead days | unchanged | an APPROVED legacy rule is **not** a legal approval; evidence snapshots the inputs |
| `POST /cases/{id}/proceedings`, `PUT /proceedings/{id}` | unchanged | a legacy child of a linked judgment's proceeding is allowed (a court fact) but **unmapped**: finality refuses until mapped; existing rules `JUDGMENT_AFTER_CHALLENGE_FILING`, `JUDGMENT_BEFORE_FILING` keep applying |
| direct SQL as `lawdesk_app` / `lawdesk_legal` outside a writer; previous jar | unchanged | refused `LD001`/`LD002` (`30`, `31`, `34`) |
| superuser / owner | — | not preventable; detective (I-7, chain head) |
Only `JudgmentRepository` and `DeadlineStore` write `judgment`/`deadline` today (searched); after R6 only the writers above write the legal columns of linked rows.

### 3.8 Finding-level summary (004, 006)
Root cause, baseline, design, sequence, invariants, failure behaviour: §3.1–§3.7 and §2. Positive/negative tests: T-DL-1…10, T-LEG-1…13, T-GATE-1…7, T-DB-2…6. Compatibility: unlinked data and the old API unchanged; linked data fails closed for old code; one-directional. Dependencies: **legal** D-01…D-07, D-13 (gates stay BLOCKED; nothing activates), **owner** D-09. **QA acceptance:** (i) the sequence of §3.5 has no operation that the guard of §3.2 refuses, (ii) every guarded write has exactly one authorised writer and that writer is owner-owned, (iii) the `30`/`34` outputs show refusals with unchanged digests under the **actual** runtime roles, (iv) the pointer rule of step 8 matches V843, (v) §3.7 lists every Controller/Service path.

## 4. Part C — appeal and proceeding integrity (005)

### 4.1 Root cause and baseline
**VERIFIED-CODE.** `proceeding.id` is the only key (V301); `judgment.proceeding_id` is unique (V302); `case_party` has `UNIQUE(case_id, party_id)` and `role ∈ {CLIENT, OPPONENT, OTHER}` (V201); 05 §C.2 named the relationships but declared no key that makes a mismatch unrepresentable. `JudicialRecords.lockedProceeding` documents "locks are always taken **child before parent**" (JudicialRecords:62) and `ProceedingService.update` follows it (lines 95, 119); `JudgmentService.create/update/setAppealDeadline` lock only the judged proceeding (`records.lockedProceeding(...)`), `ProceedingService.create` locks the parent (line 154).

### 4.2 Keys and constraints (VERIFIED-PG18 `50`–`52` on the real `proceeding`, `judgment`, `case_party`, `deadline`)
Existing tables receive **only implied-unique indexes** (their primary key already makes the wider key unique; no column, no data change): `proceeding(id, case_id)`, `proceeding(id, case_id, parent_proceeding_id, stage)`, `judgment(id, proceeding_id)`, `case_party(id, case_id, role)`, `deadline(id, case_id)`.

| Invariant | Declarative constraint | Probe result |
|---|---|---|
| judgment's procedural record is in the judgment's case | `judgment_procedural(judgment_id PK, proceeding_id, case_id)`, FK `(judgment_id, proceeding_id) → judgment(id, proceeding_id)`, FK `(proceeding_id, case_id) → proceeding(id, case_id)`, `UNIQUE(judgment_id, case_id)`, `UNIQUE(judgment_id, proceeding_id)` | enforced |
| every litigant belongs to the same case as its judgment | `judgment_litigant(id, judgment_id, case_id, kind, case_party_id, party_role)`, FK `(judgment_id, case_id) → judgment_procedural`, FK `(case_party_id, case_id, party_role) → case_party(id, case_id, role)` | T-DATA-1a/1b refused |
| kind matches role; one CITRA; no duplicates | `CHECK (kind='CITRA' ∧ role='CLIENT' ∨ kind='OPPOSING' ∧ role='OPPONENT')`, partial `UNIQUE(judgment_id) WHERE kind='CITRA'`, `UNIQUE(judgment_id, case_party_id)`, `UNIQUE(id, judgment_id)` | 1c, 1d, 1e refused |
| every party deadline belongs to the same judgment as its litigant | `judgment_party_deadline(…, judgment_id, litigant_id, case_id, status, deadline_id UNIQUE)`, FK `(litigant_id, judgment_id) → judgment_litigant(id, judgment_id)`, FK `(judgment_id, case_id) → judgment_procedural`, FK `(deadline_id, case_id) → deadline(id, case_id)`, `CHECK` status/deadline shape, partial `UNIQUE(litigant_id) WHERE status IN ('DRAFT','EFFECTIVE')` | 2a, 2b, 2d refused; 2c, 2e accepted |
| every appeal belongs to the same judgment and litigant | `judgment_appeal(…, judgment_id, litigant_id, case_id, parent_proceeding_id, appeal_type, status, proceeding_id, child_stage, link_kind, joinder_id, capacity)`, FK `(litigant_id, judgment_id) → judgment_litigant`, FK `(judgment_id, case_id) → judgment_procedural`, FK `(judgment_id, parent_proceeding_id) → judgment_procedural(judgment_id, proceeding_id)` | 3d refused |
| every linked child proceeding is in the right case, attacks the judged proceeding and has the right stage | FK `(proceeding_id, case_id, parent_proceeding_id, child_stage) → proceeding(id, case_id, parent_proceeding_id, stage)`, `CHECK child_stage = appeal_type`, `CHECK (proceeding_id IS NULL) = (child_stage IS NULL) = (link_kind IS NULL)` | 3a (other case), 3b (other first-instance), 3c (wrong stage) refused |
| each filing has one unique relation to its proceeding | `link_kind OWN`: partial `UNIQUE(proceeding_id) WHERE link_kind='OWN' AND status IN ('FILED','WITHDRAWN')`; `JOINED` only with `joinder_id`, FK `(joinder_id, proceeding_id) → proceeding_joinder(id, proceeding_id)`, `CHECK (link_kind='JOINED') = (joinder_id IS NOT NULL)`; a DRAFT filing has no proceeding | 4d, 3e, 5a refused; 5b accepted |
| one live filing per litigant and type | partial `UNIQUE(litigant_id, appeal_type) WHERE status IN ('DRAFT','FILED','WITHDRAWN')` | 4e refused |
| VOID and withdrawn rows keep history | no DELETE/TRUNCATE for any application role (class R); VOID frees the litigant slot and the proceeding slot; **WITHDRAWN keeps both** (no re-filing rule is invented) | 6a–6d; `52` |
| multiple opposing litigants | one `judgment_litigant` per opposing case party; independent deadline, filing, capacity | 4a, 4b, 4c accepted (three filings by three litigants on one judgment) |
`proceeding_joinder(id, proceeding_id, court_record_ref, gate approval)` exists only under gate **G-STAGE** (D-08, BLOCKED): without it every second filing needs its own child proceeding; same-number conflicts (the existing unique index of V301) keep the filing a DRAFT with the stable error `PROCEEDING_NUMBER_IN_USE`. **No court consolidation, numbering or re-filing rule is invented.**

### 4.3 Capacity
Per filing: `judgment_appeal.capacity`; `proceeding.citra_capacity` stays withdrawn (05 §C.2). A child's capacity view is the capacities of the filings mapped to it.

### 4.4 Probe observations that changed the design
Unique indexes are evaluated before foreign-key triggers, so a duplicate-key refusal can mask a missing key in a test; the probe orders each negative test so that exactly the intended constraint fires (`51` labels each expected constraint). WITHDRAWN-keeps-slot was chosen **because** the alternative would encode a legal re-filing rule.

### 4.5 One global locking discipline
**Rule (single, for every writer, existing and R6):** a transaction takes locks in this order and never goes back: **(1)** `legal_case` (R6 writers: `FOR SHARE`; existing closure/updates take the row lock by `UPDATE`); **(2)** child proceedings of the judged proceeding, **ascending id**, *only when it needs them* (finality, mapping/joinder, `ProceedingService.update`); **(3)** the parent (judged) proceeding; **(4)** `judgment`; **(5)** `judgment_procedural`; **(6)** `judgment_litigant` rows (CITRA first, then id); **(7)** `judgment_party_deadline` rows; **(8)** `deadline` rows; **(9)** filings, evidence, history inserts. It extends the existing "child before parent" convention (`JudicialRecords:62`) instead of reversing it; the Round-3 order (parent first, then children for finality) is **withdrawn** because it deadlocks with `ProceedingService.update`. Re-validation after the parent lock handles children created in between: `ProceedingService.create` locks the parent (line 154) before inserting a child, so a finality transaction that already holds the old children waits for the parent, then **re-queries** the children (§4.6 S3). All locks are taken **inside** the writer functions, so the order is written once, not per caller. A `FOR SHARE` on `legal_case` lets R6 writers run concurrently and conflicts with the closure `UPDATE` (`CaseRepository.changeStatus`, optimistic-version `UPDATE`, which takes the row lock).
**Verified against the code:** the `for update`/`PESSIMISTIC_WRITE`/`lockedProceeding` call sites (searched) are `ProceedingService.create/update` (parent; child→parent), `JudgmentService.create/update/setAppealDeadline` (judged proceeding only), `HearingService`, `SpecialDecisionService` (one proceeding), `DeadlineStore` (one deadline row), `RiskService`, `CaseRows` (a case row, then non-proceeding rows). None takes a case lock after a proceeding lock, none takes a parent before a child it will also lock, so every existing path is a prefix-compatible subset of the order above.

### 4.6 Concurrent scenarios (VERIFIED-PG18 `60`, real `proceeding` and `legal_case` rows)
| # | Scenario | Outcome shown | Test |
|---|---|---|---|
| S1 | **withdrawn order** (parent-first finality vs `ProceedingService.update` child-first) | **`40P01 deadlock detected`** — the Round-3 order is unsafe | — |
| S2 | the single discipline (children then parent) from both | both commit, no deadlock | T-LOCK-1 |
| S3 | filing creates a child while finality holds the old children and waits for the parent | filing commits; finality, after the parent lock, **sees 3 children** and refuses `LD040` (`APPEAL_FILED`) | T-LOCK-4 |
| S4 | two concurrent completions of one complaint (referral, §5) | one commits, the other `LD021`, one case survives | T-REF-6 |
| S5 | case closure vs R6 write, both orders | R6 first → closure waits, then commits; closure first → R6 sees `CLOSED`, `LD041` `CASE_READ_ONLY` | T-LOCK-6 |
The five scenarios requested: **(a) two opponents appealing simultaneously** — both take the judged proceeding (serialised), each creates its own child; both commit; procedural status set by the first and idempotently by the second (T-LOCK-2); with the same court number the second keeps a DRAFT (`PROCEEDING_NUMBER_IN_USE`). **(b) CITRA and an opponent** — identical (T-LOCK-3), the pointer is touched only by the CITRA activation. **(c) filing vs finality** — S3 plus the symmetric order: finality first → the filing is refused `LD007`/`JUDGMENT_FINAL` (T-LOCK-4). **(d) proceeding update vs filing** — `ProceedingService.update` of the judged proceeding and a filing both lock it and serialise; update of a child locks child then parent, a filing never locks an existing child unless it maps to one, then in ascending order (T-LOCK-5). **(e) case closure vs judgment updates** — S5. T-LOCK-7 runs 100 randomised mixed operations on 8 connections and expects zero `40P01`.

### 4.7 Register and tests corrected
PJ-05, PJ-06, PJ-07, PJ-08, PJ-09 and the cross-cutting T-DATA-5/6 no longer assume two parties (the register is regenerated; PJ-08 now reads "one `judgment_appeal` per filing litigant … applied per opposing litigant"; T-PJ08-9 added). Old tests are kept. New: T-DATA-15…20, T-LOCK-1…7.

### 4.8 Finding-level summary (005)
Dependencies: **legal** D-05 (opposing start rule), D-08 (stage/joinder; also whether co-litigants' periods differ), nothing activated. Compatibility: existing tables only gain implied-unique indexes (a metadata change under a short lock on small tables, rehearsed in T-MIG-2); no column. **QA acceptance:** every row of §4.2 has a probe line that fails exactly on that constraint; §4.5 is one rule with the code citations above; §4.6 shows S1's deadlock and S2/S3's resolution; no row of the register still says "two".

## 5. Part D — atomic complaint referral (009)

### 5.1 Root cause and baseline
**VERIFIED-CODE.** `IntakeService.register` (IntakeService:65-128, `@Transactional`): `PARTY_MANAGE` check for parties (:67), duplicate check with `confirmDistinct` (:90), `caseService.generateReferenceNo()` (:93) which upserts `case_reference_counter` — the row stays **locked until the transaction ends**, so numbers are gapless and a rolled-back referral burns none (V200 comment) — `cases.insertIntake` (:96) whose SQL (`CaseRepository.insertIntake`, :61-75) **omits `confidentiality`**, so V600 defaults it to `NORMAL`; then timeline, parties, first instance, assignment, audit (`CASE_CREATE`, :127). **VERIFIED-PG18 (`41`):** with the Round-3 pair of immediate checks — link INSERT requires status REFERRED, status change requires the link — **both orders fail** (`LD030`, `LD031`): the previous design was unsatisfiable.

### 5.2 Corrected invariant R-1 (replaces 05 §B.2(2))
`complaint_case_link(complaint_id PK, case_id UNIQUE)` is class C and written only by `r6_complete_referral`. R-1 — *status «محالة للمحكمة» ⇔ exactly one link* — is a **deferred constraint trigger** (`DEFERRABLE INITIALLY DEFERRED`, `AFTER INSERT OR UPDATE OF status` on `complaint`, `AFTER INSERT OR UPDATE OR DELETE` on the link) evaluated **at COMMIT**; it is `SECURITY DEFINER` (it reads class-C rows), pinned path, no dynamic SQL. Inside the writer the order of the two writes is irrelevant; a commit with a link and no status, or a status and no link, fails with `LD032` and rolls back. **VERIFIED-PG18 (`45`):** link without status → `LD032` at COMMIT; status without link → `LD032` at COMMIT; both leave the complaint `UNDER_INVESTIGATION` with 0 links; duplicate link/case rows → unique violations. The deferred trigger is the commit-time invariant; **no trigger requires a status at link-insert time** (the old B.2(2) text is withdrawn).

### 5.3 The sequence (one transaction on the legal pool)
| # | Step | Detail | Failure (nothing persists) |
|---|---|---|---|
| 0 | prerequisites outside the transaction | session, `COMPLAINT_APPROVE` (grant), `complaint_visible`; the referral **draft** exists and is evaluated; **P-MANIFEST ON** (owner policy, D-20) and D-25 approved; manifest approved by a second `COMPLAINT_APPROVE` holder | `REFERRAL_POLICY_OFF` (`LD023`, 409) / `NOT_FOUND` / 403 |
| 1 | validate complaint and approved transfer policy | inside `r6_complete_referral` after the lock | `LD020` not found, `LD021` already referred, `LD022` wrong status, `LD023` policy off |
| 2 | confirm mandatory case-intake data is complete | `referral_draft.missing = {}` and `manifest_approved` — the **application** evaluates the intake rules by running the *same validation* as `IntakeService` (§5.4) and stores the missing-fields list in the draft in its **own earlier transaction**; the DB re-checks the stored result | `LD024 REFERRAL_INCOMPLETE` (422) with the list; **status unchanged, no case, no link, counter untouched** |
| 3 | acquire locks | `complaint FOR UPDATE` first; then `case_reference_counter` (taken by `generateReferenceNo`, held to commit); the new case row is invisible to others | a concurrent completion of the same complaint blocks here, then `LD021` (`60` S4) |
| 4 | create the linked case **RESTRICTED from its first INSERT** | adapter calls `insertIntake(..., Confidentiality.RESTRICTED)`: one `INSERT`, never an `UPDATE` of confidentiality | any intake validation failure → rollback |
| 5 | transfer / link the approved minimum data | parties (all respondents + CITRA side), facts, documents as **new `case_document` rows** per the manifest (05 §B.2(4)); duplicates via the existing duplicate detection, `confirmDistinct` only by explicit user choice | rollback |
| 6 | create the unique link | `r6_complete_referral` verifies the case is `RESTRICTED` **and created in this very transaction** (`created_at = transaction_timestamp()`, **VERIFIED-PG18 `44` (c),(d)**: a NORMAL case or a pre-existing case → `LD025`) and that the actor holds `COMPLAINT_APPROVE`; `INSERT complaint_case_link` | `LD025`, `LD005` |
| 7 | mark the complaint REFERRED | `UPDATE complaint SET status`; status-history + audit rows | rollback |
| 8 | commit | deferred R-1 runs; counter increment commits with the case | `LD032` rolls back everything incl. the reference number |
**VERIFIED-PG18 (`42`, `44`):** P-MANIFEST OFF → `LD023`, case row count 0 after rollback; incomplete draft → `LD024`; NORMAL case → `LD025`; existing restricted case → `LD025`; actor without grant → `LD005`; the success sequence (case INSERT RESTRICTED → writer → COMMIT) → complaint `REFERRED`, link to the new case, case `RESTRICTED`; a retry after completion → `LD021`; orphan cases from the failed attempts: **0**; concurrent completions (`60` S4): exactly one link, one surviving case.

### 5.4 The dedicated restricted referral adapter (M5, VERIFIED-CODE baseline)
`IntakeService.register` is split, without changing its public behaviour, into `register(request)` → `registerInternal(request, Confidentiality.NORMAL, Origin.ORDINARY)`; `CaseRepository.insertIntake` gets an **overload with a `Confidentiality` argument** (the current signature stays and passes `NORMAL`, so every existing caller and test is untouched). `RestrictedReferralAdapter` (package `com.lawdesk.postjudgment.referral`, the only caller of `registerInternal(…, RESTRICTED, REFERRAL)`) runs inside **one `TransactionTemplate` bound to the legal pool**: the primary `DataSource` becomes a routing data source (`APP`/`LEGAL`) selected by `LegalSession.run(...)`; only classes under `com.lawdesk.legal.adapters` may use `LegalSession` (ArchUnit rule, T-DB-13). Because `lawdesk_legal` is in `lawdesk_dml`, the **same repositories** (`CaseRepository`, party service, first-instance, assignment, timeline, audit) execute unchanged on that connection — this is what preserves validation, numbering and audit instead of re-implementing them in PL/pgSQL. Preserved: actor, `PARTY_MANAGE` rule (the referring user needs it only if the manifest carries parties, as for ordinary intake), field validations, duplicate detection (**a duplicate suspicion keeps the referral a draft** — it never silently sets `confirmDistinct`), numbering, `CASE_CREATED` timeline, parties, first-instance record, assignment, audit `CASE_CREATE` (+ the referral audit event). Differences: confidentiality `RESTRICTED`; `primaryMemberId` = the responsible researcher; `owningUnit` per 05 §B.2.

**NORMAL-then-RESTRICTED is not used.** The case row never exists as NORMAL, not even uncommitted: T-REF-1 captures the SQL and asserts exactly one `INSERT` carrying `RESTRICTED` and no later `UPDATE legal_case SET confidentiality`; the existing statement-level search trigger (`search_case_insert`, V801) therefore sees the restricted row from the start.

### 5.5 Confidentiality guards (replace the DB trigger text of 05 §B.5)
* `legal_case.confidentiality`: `r6_guard_case_confidentiality` (invoker) refuses `RESTRICTED → NORMAL` when `r6_case_is_complaint_linked(case)` and `current_user <> lawdesk_owner` — for **every** non-owner, including `lawdesk_legal` outside the writer. The helper returns a boolean only (the runtime role has no privilege on the link table: **VERIFIED-PG18 `30` §B2**). `r6_declassify_linked_case` (Tier A) requires `P-DECLASS` ON (Tier B), the approver's `COMPLAINT_APPROVE` grant and an approval record. **VERIFIED-PG18:** direct update → `LD010` for `lawdesk_app` and `lawdesk_legal`; the writer → `LD011` while the policy is OFF.
* `case_grant` and the case-assignment columns on a linked case: `r6_guard_case_grant`/`r6_guard_case_assignment` (same mechanism, DESIGNED) refuse non-owner writes; the app routes them to `r6_grant_linked_case_access`/`r6_assign_linked_case`, which require the grantor to pass `complaint_visible` (grants give case visibility only). `AccessAdministration.setConfidentiality`, `POST /cases/{id}/grants` and the assignment update keep their service guards for a clean 4xx. The refusal reveals that the change is unavailable, not why (accepted oracle, recorded under D-20).
* Complaint data: class C (§2.4): the runtime credential cannot read it.

### 5.6 Failure behaviour, retries, concurrency, old APIs
| Case | Behaviour | Test |
|---|---|---|
| failure at any step 1–8 | whole transaction rolls back (case, parties, documents, link, status, **reference number**) | T-REF-4 (fault injection per step) |
| missing information | draft persists with the list; PC-11 stays unmet; never "completed" | T-REF-2 |
| owner policy / D-25 not approved | refused before any write | T-REF-3 |
| duplicate request/retry | the idempotency key (the draft id) returns the existing case and `200`; a different key for an already referred complaint → `LD021` | T-REF-5 |
| two concurrent completions, same complaint | the complaint row lock serialises; loser `LD021`; no orphan case | T-REF-6 (**`60` S4**) |
| two complaints concurrently | independent; numbers serialise on the counter and stay distinct and gapless | T-REF-7 |
| R-1 | deferred check at COMMIT | T-REF-8 (**`45`**) |
| old API after completion | confidentiality PUT, grants, assignment refused as in §5.5 | T-REF-9, T-CONF-11…17 |
| ordinary intake path | unchanged; never restricted by this path | T-REF-10 |

### 5.7 Finding-level summary (009)
Dependencies: **owner** D-20 (`P-MANIFEST`, `P-DECLASS`), D-25 (referral intake mapping) — **all remain unapproved; referral stays a draft in any build**, PC-10/11/12 unmet. Compatibility: `CaseRepository`/`IntakeService` gain overloads only; R5 adds optional intake fields only (T-R5-1). **QA acceptance:** the eight steps are consistent with every constraint (the contradiction of §5.1 is reproduced and gone), the case is never NORMAL, a missing field never marks a complaint referred, and each failure leaves counts, counter and links unchanged.

## 6. Independent QA acceptance criteria (per finding)
| Finding | Codex can mark CLOSED when |
|---|---|
| 001 | the runtime/legal roles own nothing and cannot alter or disable anything (T-DB-1…3, the probe reproduced by Codex); sensitive authorities come only from the grant table written by Tier B (T-SEC-1…19); the `role_permission` guard refuses sensitive codes for every role (`26`) |
| 002 | the runtime credential cannot read class C, cannot forge policy or approval, and cannot declassify (`30`, `31`); declassification needs P-DECLASS + grant + approval and stays OFF; linked-case grants/assignment guarded |
| 006 | every guarded write has a single owner-owned writer; provenance is privilege, not "a row exists"; the previous-release statement set behaves as §2.12 |
| 004 | the §3.5 sequence is consistent with §3.2 and V843; success and every rejection are in `33`/T-DL with zero partial writes |
| 005 | §4.2 keys, §4.5 one lock order with the code citations, §4.6 scenarios, register without two-party assumptions |
| 009 | §5.2–§5.4 consistent; no NORMAL window; draft semantics; failures at every step |

## 7. Regression and limits
**Closed findings (003, 007, 008, 010, 011, 012, 014, 015, 016, 013) are not reopened.** Touched by this revision and therefore **to re-check**: 011/014 (previous-release and authority statements now rest on §2), 015 (evidence immutability is now privilege-based), 010/016 (register regenerated: 55 rows, vocabulary unchanged), 013 (alert writers also run on the legal pool; the idempotency design is unchanged), 007/008 (unchanged text). The word "implemented" is not used for R6 behaviour.
Limits stated, not hidden: (1) no Spring code exists — nothing about the legal pool routing, the migrate job or the adapter is proven; (2) the probe is a reduced model; the previous-release **jar** was not run; (3) the arithmetic of a due date is verified by the approver on the evidence and by conformance tests, not recomputed in the database; (4) RR-1 (S2/S3) is a residual, not a closed risk; (5) the page boundaries of the source document remain unverified; (6) the owner credential and superuser are outside the model; (7) PostgreSQL 18.6 was used, UAT's minor version was not inspected.
