# LawDesk R6 M0 — independent QA retest, Round 4

**Verdict: REJECT (narrowly). M0 acceptance: NO. M1-pre readiness: NO. M1 readiness: NO.**
Reviewer: Claude (independent QA lead, taking over from Codex). Target: PR #115 head `de23b53a1450e148bfb252a678814eda38f07d7d` (previous head `19f008a`, Round-3 REJECT) against develop `18822a83bff8e2a38b83db4370674a9f69919d81`, 2026-11-27. PR #115 is an open **draft**, docs/evidence/tools only; no R6 code or migration exists. Nothing was merged, deployed or run against UAT data.

**Result in one paragraph.** The Round-4 architecture fixes the root cause of the three P0s: I reproduced on my own disposable PostgreSQL 18.6 that the current runtime credential owns the schema and can disable/drop/replace guards, and that the proposed owner/runtime split closes it. Three of the six findings now close (004, 005 and the P0 root cause of 001/002/006). But my own attacks, run against the author's proof model, found **four P1 defects that the author's evidence does not show and in two cases contradicts**: single-person Tier B activation (001), undocumented direct complaint reads by the legal credential (002), a due-date rewrite of an approved R6 deadline by the ordinary runtime credential (006), and referral of a *pre-existing* case (009). Each has a small, concrete fix. Open: **P0 0, P1 4, P2 0, P3 0.** Closed finding IDs: **12 of 16.**

Evidence labels: **VERIFIED_EXECUTION** (I ran it, output in `round4/outputs/`), **VERIFIED_SOURCE** (read at the SHA), **INFERRED**, **NOT_VERIFIED**.

## 1. Review basis

| Item | Evidence | Label |
|---|---|---|
| PR #115 head, draft, docs-only delta | `gh pr view`, `git diff --stat` (71 files; `tools/docs`, `docs/`) | VERIFIED_SOURCE |
| Source DOCX | SHA-256 `777d4d36…ffa5` equals the sidecar file. My own ZIP/XML parser (not the author's script): **209 non-empty paragraphs, 209 extract lines, numbers 1–209 contiguous, 209/209 identical**; no media/comment/footnote parts. Not published. Page boundaries (metadata: 7) not verifiable | VERIFIED_EXECUTION |
| PostgreSQL 18.6 replay | 68 develop migrations applied as `lawdesk_app` on a fresh server (clean cluster, nothing shared); author probes `05`–`53` replayed verbatim. SQLSTATE sequence of the integrity probes `50`–`52` is **identical** to the author's (20 denials, same codes) | VERIFIED_EXECUTION |
| Baseline P0 reproduced | as `lawdesk_app` after migrations: `DISABLE TRIGGER`, `DROP TRIGGER`, `CREATE OR REPLACE FUNCTION`, `CREATE TABLE` all succeed; only `CREATE ROLE` is refused (probe `02` replay) | VERIFIED_EXECUTION |
| Previous-release jar, Spring code, UAT object ownership, browser E2E | no JVM in this sandbox; UAT database deliberately not touched | NOT_VERIFIED |

Caveat: the author's probe is a **reduced model** (names `x_*`, simplified writers). My conclusions on mechanism are exact; conclusions on the real Java adapter are not claimed.

## 2. Findings retest

| Finding | Prior | Result | Severity now |
|---|---|---|---|
| QA-R6-M0-001 sensitive legal authority | P0 | **OPEN** — root cause fixed, residual below | **P1** |
| QA-R6-M0-002 complaint confidentiality | P0 | **OPEN** — root cause fixed, residual below | **P1** |
| QA-R6-M0-004 legal deadline creation | P1 | **CLOSED** | — |
| QA-R6-M0-005 litigants, appeals, proceedings | P1 | **CLOSED** | — |
| QA-R6-M0-006 legal gates and legacy write paths | P0 | **OPEN** — guard bypass | **P1** |
| QA-R6-M0-009 referral atomicity | P1 | **OPEN** — pre-existing case linkable | **P1** |

### Root cause shared by 001/002/006 — fixed (design + model)
Probes `10`, `20`, `30`–`34`, `26a/b` replayed: after `REASSIGN OWNED` to `lawdesk_owner` the runtime and legal roles get `42501` on `ALTER/DROP TRIGGER`, function replacement, `CREATE TABLE`, `SET ROLE`, `session_replication_role`, direct DML on class-R tables, reading class-C tables (`lawdesk_app`), and every operator function; the guards fire for both runtime logins; protected-state digests unchanged by refused steps. `pg_temp` shadowing and `search_path` pinning behave as claimed once the database ACL revokes `TEMPORARY` (note: my first run showed TEMP allowed — my harness cloned the database and the ACL statements named the original DB; after correcting, `42501`; this is not a defect). **VERIFIED_EXECUTION.** Residual limits stated honestly by the author (superuser/owner/host root; RR-1) are accepted as stated.

### 001 — OPEN P1: Tier B "two-person" control is not what the proof model executes
- Design §2.7 (`LD-DOC-R6-07` lines 72–85) says every Tier B function requires two different `session_user`s. The probe implements two-person only for **revocation** (`80b`: `r6_op_request/confirm/revoke_authority`). The functions that *create* legal effect — `r6_op_record_legal_approval`, `r6_op_grant_authority`, `r6_op_set_policy` (`20-protected-objects-model.sql.txt:84-95`) — are single-call and are what probes `32`/`33` use.
- **Reproduced:** one login `op_alice`, alone, executed `r6_op_set_policy('P-DECLASS', true)`, `r6_op_grant_authority(<user>,'COMPLAINT_APPROVE')` and `r6_op_record_legal_approval('G-FINAL',1)`: all OK (`round4/outputs/qa-attack-suite.out.txt`, section B). One operator can therefore activate a legal gate, grant a sensitive authority and switch an owner policy ON, defeating D-09's separation.
- Also: `r6_op_list()` omits `payload`, so a confirmer cannot see what they confirm.
- Fix: every activating Tier B function must take a CONFIRMED `op_request` id, check kind/payload match, state and requester ≠ confirmer inside the function, mark EXECUTED; revoke direct EXECUTE of the single-call versions; expose payload (and its hash) in `r6_op_list`; add probes that a single operator is refused for each. Tests T-DB-14 (new).

### 002 — OPEN P1: legal credential reads all complaint data; the design omits it
- §2.4 (lines 51–58) and `25-link-fixture.sql.txt:11` grant `SELECT` on every class-C table to `lawdesk_legal`. **Reproduced:** as `lawdesk_legal`, `SELECT` on `complaint`, `complaint_case_link`, `referral_draft`, `legal_authority_grant`, `owner_policy` all succeed with no user, scope or predicate (section A). Case data has a DB-side visibility predicate (`case_visible`); complaint data has none, so complaint confidentiality (D-20) rests entirely on Java for every read.
- §2.10 row S2 (line 106) lists what a stolen legal credential "can" do as *calling Tier A functions* and omits **reading every complaint**; the table therefore understates the exposure. The legal credential lives in the backend container beside the app credential, so S2 ≈ S3 in practice.
- Fix: either (a) state the residual explicitly in S2/S3 and have the owner accept it under D-09, or better (b) move complaint reads behind owner-owned `SECURITY DEFINER` read functions taking the acting user id and enforcing complaint scope, with `lawdesk_legal` holding no table SELECT on class C (same RR-1 caveat: the id is caller-supplied, so (b) reduces accident/SQL-injection exposure, not a full backend compromise — say so). Add T-CONF probes for the chosen option.
- Declassification control itself (`LD010` for runtime and legal, `LD011` while P-DECLASS off) **VERIFIED_EXECUTION**.

### 004 — CLOSED
The Round-3 contradiction is resolved by the `current_user` discriminator: the legacy insert of a JUDGMENT-sourced deadline for a linked judgment is refused (`LD002`) while the owner-owned writer creates deadline + evidence + pointer in one transaction (probe `33`, deadline created, pointer set, partial writes absent on rejection). Invariant I-5's deferred-commit check is DESIGNED (not executed) — carried by T-DL-2 at M1. **VERIFIED_EXECUTION (mechanism) / VERIFIED_SOURCE (spec).**

### 005 — CLOSED
Composite keys on the real `proceeding`/`judgment`/`legal_case`: cross-case litigant, wrong party role, cross-judgment deadline/filing, child of another case, wrong stage, JOINED without joinder record, one own filing per child, one live filing per litigant/type — all refused with the author's SQLSTATEs; positive cases accepted (probes `50`–`52`, identical to author). Lock order: I reproduced the old order deadlocking (`40P01`: legacy child→parent vs the Round-3 parent→child) and the unified child→parent order running clean. The register no longer says "two filings" (`LD-DOC-R6-01` PJ-08). `ProceedingService.update` locks child then parent (read at `ProceedingService.java:91-120`) and V803's case_position triggers write `case_position` (FK to `legal_case`, key-share only) — compatible with the proposed `FOR SHARE`. Limits: tables are the reduced `x_*` model; trigger/lock interaction with the real R6 tables is for M1. **VERIFIED_EXECUTION + VERIFIED_SOURCE.**

### 006 — OPEN P1: the deadline guard is bypassable by the ordinary runtime credential
- `r6_guard_deadline` (`20-protected-objects-model.sql.txt:48-53`) decides "protected" from `coalesce(new.source_*, old.source_*)`, i.e. from the **new** row. §3.2 I-4 (line 141) does not say OLD must be tested, although the author cites this guard as VERIFIED.
- **Reproduced** as `lawdesk_app` (the legacy API / previous-release identity) on a linked judgment with an ACTIVE, approved R6 deadline due 2026-08-01: `UPDATE deadline SET source_type='TEXT', source_id=NULL, source_text='x', due_on='2031-01-01'` → **OK**. The judgment pointer still points at the row, the legal due date has been rewritten, evidence is untouched, and only the nightly I-7 check (detective, S5/S6 only) would notice (section D).
- Fix: evaluate the guard on `OLD` **and** `NEW` (`protected(old) OR protected(new)`), define "protected" also as *referenced by `judgment.appeal_deadline_id` or `judgment_party_deadline.deadline_id`* (the text of I-4 already lists the latter; the model must too), guard `source_*` columns, `DELETE`, and add negative tests for "change the source first, then edit". Same review for every other guard that keys off NEW. Judgment and confidentiality guards key off OLD and are fine.
- Legal gates themselves (finality, pointer, outcome/date edits: `LD001`; direct DML on approvals/policies: `42501`) **VERIFIED_EXECUTION**.

### 009 — OPEN P1: referral can link a pre-existing case
- `r6_complete_referral` proves "the case was created in this transaction" by `cs.created_at = transaction_timestamp()` (`40-referral-setup.sql.txt:53`; design §5 step 6, line 271, claims `44` (c),(d) prove pre-existing cases are refused). `created_at` is an ordinary writable column of a legacy table. **Reproduced** as `lawdesk_legal`: `UPDATE legal_case SET confidentiality='RESTRICTED', created_at=transaction_timestamp() WHERE id=<400-day-old case>` then `r6_complete_referral(...)` → **committed**; the complaint is REFERRED and linked to the unrelated pre-existing case (section C). A second complaint was then also linked to a forged case (S-b).
- Consequence: confidential complaint linkage attaches to a case with other members/documents (disclosure) and tampers with the case's `created_at`.
- Fix: do not trust a caller-writable column. Preferred: the owner-owned writer **inserts the restricted case itself** (case id never supplied), calling the existing intake validation boundary; or compare `xmin` of the case row with the current transaction id (not forgeable) and make `created_at` guarded. Add T-PC11 negative: forge `created_at`, set RESTRICTED, then refer → refused; a mutated pre-existing case remains unlinked.
- Verified good: invariant R-1 at COMMIT (both failure orders `LD032`), contradiction reproduction (`41`), concurrent double referral of one complaint → exactly one link, second `LD021` (S-a), unique link/case (`23505`).

## 3. Other requested inspections

| Topic | Result |
|---|---|
| Direct complaint reads via legal credential | **FAIL** — see 002 |
| Referral linking a pre-existing case | **FAIL** — see 009 |
| Identity impersonation via privileged functions | **By design, undocumented as residual only partly.** Tier A takes caller-supplied `p_preparer`/`p_approver`; any holder of the legal credential can name any user holding an active grant (the DB checks the grant, not the person) — executed in `33` and in my referral runs. The author lists this as RR-1; it must remain a named owner-accepted risk under D-09, and §2.10 must say it applies to a credential theft, not only RCE. Preparer need not hold any DB-visible authority. **INFERRED limit, VERIFIED_EXECUTION of mechanism** |
| Cross-role escalation | runtime/legal cannot call operator functions, operator cannot call Tier A, `SET ROLE`/`GRANT`/`ALTER ROLE`/`CREATE ROLE` refused — **PASS** (VERIFIED_EXECUTION). Operator single-person gap: see 001 |
| PostgreSQL ownership and permissions | 92 tables/6 sequences/293 indexes/59 functions reassigned and populated rows unchanged (probe `10` replay); no non-R6 `SECURITY DEFINER` function exists in the develop migrations (0, checked in catalog) — **PASS**. Actual UAT ownership: NOT_VERIFIED |
| Deadlock / concurrency | inverted order deadlocks (`40P01`), unified order passes; concurrent referral serializes — **PASS** except 009 forging |
| Existing API compatibility | previous-release jar not run; DB-level legacy statements for unlinked records behave as before (probe `53` replay) — **NOT_VERIFIED for HTTP behaviour**, gate for M1 (T-DB-9) |
| Ten previously CLOSED findings (003, 007, 008, 010–016) | no new evidence of regression; 003 re-verified 209/209 — **remain CLOSED** |

## 4. Verdicts

- **Security architecture: CONDITIONAL — one revision.** The ownership/credential split is sound and reproducible; four concrete defects (001, 002, 006, 009) remain in the legal-effect controls and must be fixed in design and model.
- **Data and transaction: PASS for keys, lock order, R-1 invariant; FAIL for referral case provenance (009) and deadline guard (006).**
- **M0 acceptance: NO.** Blocking P1 open (rule: no milestone accepted with open P0/P1).
- **M1-pre (roles/ownership scaffolding, no legal behaviour): NO** until the four items are re-reviewed (expected to be one short Round 5).
- **M1: NO.**

## 5. Decisions still pending (unchanged — nothing activated or inferred)
**CITRA Legal Affairs:** D-01–D-05, D-07, D-08, D-10, D-11, D-13, D-16, D-24. **Owner:** D-09 (now also: two operator identities, rotation, whether Tier A runs out of process, acceptance of RR-1), D-12, D-14, D-15, D-17–D-23, D-25. Nine legal gates and P-MANIFEST/P-DECLASS remain OFF.

## 6. Notes on other open PRs (metadata only, not reviewed)
PR #116 ("evidence-only consolidation") reports **2,863 files, 527k added lines** and includes two files under `backend/src/test`; GitHub's file API lists only 100. It is not trivially evidence-only and must be reviewed before it is merged; its `qa/independent` tree overlaps this QA path. PR #114 (R5) remains an open draft at `c583333` and is not an accepted baseline.

Reproduction: `round4/scripts/` and `round4/outputs/` (synthetic data only; scripts target a disposable server).
