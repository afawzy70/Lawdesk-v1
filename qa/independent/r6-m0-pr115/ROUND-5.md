# LawDesk R6 M0 — independent QA retest, Round 5 (final M0 verdict)

Reviewer: Claude (independent QA lead). Target: PR #115 head `9da22f0fd4afee40f808600d1971f4d95eb6a99d` (design content `0bbd8e9`; previous reviewed `de23b53`), develop `92822792dbb31c0246380d1ba3046bbd6b51d341` — both re-fetched and confirmed on 2026-11-27. PR #115 is still an open **draft**. No production source, migration, deploy file or frontend file differs from develop; the only non-doc delta is the three backend test files that arrived with PR #116. Nothing was merged, deployed or run against UAT.

## Verdicts

| # | Verdict |
|---|---|
| 1 | **M0: ACCEPT — as a design and proof-model acceptance only** |
| 2 | **M1-pre: READY from QA's side; NOT authorised to start** (needs the owner's explicit go-ahead and the deploy-file coordination below) |
| 3 | **M1: NOT READY** (entry = M1-pre independently accepted) |
| 4 | Owner decisions outstanding: §6 |
| 5 | CITRA Legal Affairs decisions outstanding: §6 |

*Acceptance of M0 means the design is internally consistent and its database mechanisms hold under attack on a reduced PostgreSQL model. It is not approval to implement legal behaviour, not approval to merge PR #115 into develop, not approval to deploy, and says nothing about Spring code, which does not exist.* Counts: closed finding IDs **16 of 16** (+ DEV-R5-1 closed); open **P0 0, P1 0, P2 4, P3 1** (new, all carried forward, §3).

Labels: **VERIFIED_EXECUTION** (I ran it; output in `round5/outputs/`), **VERIFIED_SOURCE**, **INFERRED**, **NOT_VERIFIED**.

## 1. What I ran

| Item | Result | Label |
|---|---|---|
| Author suite on the Round-5 model, my own fresh PostgreSQL 18.6, 68 develop migrations applied as `lawdesk_app` | **61/61 OK** — claim confirmed | VERIFIED_EXECUTION |
| Author's attack tests on the Round-4 model (`--model r4`) | 18 tests, 1 pass, the rest fail/error (author states 16 fail + 1 error + 1 pass; same shape, my count differs by one error row) — the attacks do succeed on the old model, so the tests discriminate | VERIFIED_EXECUTION |
| My own adversarial suite (`round5/scripts/r5_adversarial.py`) on the author's model, never reading the author's tests | **101 checks, 0 unexpected**; 8 of them are deliberate observations that confirm weaknesses (§3) | VERIFIED_EXECUTION |
| Race: legacy insert vs concurrent linking of a judgment | waits and is refused (`LD002`) **only if the registering transaction locks the judgment `FOR UPDATE`**; without that lock the legacy insert commits | VERIFIED_EXECUTION |
| Source DOCX | SHA-256 `777d4d36…ffa5` unchanged; author's `verify_source.py` IDENTICAL 209/209; my earlier independent parse stands; not published | VERIFIED_EXECUTION |
| Register | 55 requirements, 25 decisions, 9 gates; 280 test ids counted by me; the 7 new ids (T-DB-14, T-DB-15, T-CONF-19, T-CONF-20, T-DL-11, T-REF-11, T-REF-12) exist; none removed. My Round-4 tracker said 222 — that was the Round-3 count and is corrected below | VERIFIED_SOURCE |
| Spring code, previous-release jar, UAT object ownership, real migrations, browser | none exist / not run / not inspected | NOT_VERIFIED |

An error-returning removed function was never taken as proof: for 001/009 I attacked `r6_op_*(request)` and `r6_open_referral_case`.

## 2. Findings

### QA-R6-M0-001 — CLOSED
**Evidence.** Nine executors each take only a request id (catalog: no single-call form); every refusal path executed: unconfirmed (`LD052`), requester confirming (`LD050`, also after `SET ROLE lawdesk_operator`), `SET SESSION AUTHORIZATION`/`SET ROLE op_bob` (`42501`), wrong hash and hash of another request (`LD054`), private consumer unreachable (`42501`), no table access for operators/app/legal, immutability even for the owner (`LD055`), pre-confirmed insert refused, replay (`LD052`), **4 concurrent executions → exactly one effect**, stale confirmed grant cannot undo a revocation (`LD052`), stale activation after withdrawal (`LD052`), custodian cannot be granted through AUTHORITY_GRANT (`LD053`). **Verified tests:** author's `test_two_party.py` (all pass) + mine. **Remaining risk:** two logins are two persons only by provisioning; collusion of two operators; expiry (`LD056`) exercised by the author's test only. **Action:** carry to M1-pre (T-DB-14 on the real migration) and to D-09. Observations → QA-R6-M0-021.

### QA-R6-M0-002 — CLOSED (P1 defect fixed; one P2 added)
**Evidence.** `lawdesk_app`, `lawdesk_legal`, an operator: `42501` on all four class-C tables (table and column privileges); readers execute only for the legal credential; the visibility predicate is not executable; hidden = unknown (`LD020` identical); disabled/ungranted users see nothing. **Residual stated in 08 §2.1** (caller-supplied actor id lets a stolen legal credential read everything one authorised user can read): I demonstrated it (`002-residual`) and judge the statement accurate; it is **not accepted** — D-09 decides, and M4 must not start before. It is incomplete in one respect: see QA-R6-M0-017 (existence oracle) and note that the same credential also reaches Tier A writers (stated) and that grants are readable by the application credential (stated). **Remaining risk:** D-09 residual; QA-R6-M0-017.

### QA-R6-M0-006 — CLOSED
**Evidence.** As `lawdesk_app` **and** `lawdesk_legal`, against a linked judgment whose deadline is referenced by party-deadline and evidence rows: update of every legal column, `id`, `case_id`, status/completion/approval, delete, `INSERT … ON CONFLICT DO UPDATE`, `MERGE`, `UPDATE … FROM`, source-type-then-due in one transaction, moving a free deadline onto the judgment, a second deadline from it, and every judgment column (`final`, pointer, `proceeding_id`, delete) → `LD002`/`LD001`; case/proceeding delete → `23503`; row unchanged afterwards; `notes` still editable (compatibility). Round-4 attack (change `source_type` first) is now refused. **Catalog (VERIFIED_EXECUTION):** the only referential action that writes into `deadline`, `judgment` or `legal_case` is `deadline.proof_document_id → case_document ON DELETE SET NULL`; no ON UPDATE actions; triggers on guarded tables are the guards plus `search_vec_judgment` (a BEFORE trigger that sorts after the guard — it only sets its search vector; **trigger-order catalog test remains mandatory**, author risk 4). **Race:** see §1 — correct only if M1 writers lock the judgment/deadline `FOR UPDATE` (stated in 08 §3). **Remaining risk:** reduced model; real migrations to be re-attacked; QA-R6-M0-018.

### DEV-R5-1 — CLOSED, severity P1 (as a finding)
Deleting a protected deadline's completion-proof document with the archive flag erased `proof_document_id` through `ON DELETE SET NULL` running as the table owner, defeating `current_user`. The author's fix (`pg_trigger_depth() = 1`) holds: purge by app and legal → `LD002`, link intact; purge without the flag → refused by the archive guard; unlinked deadlines unaffected. **Equivalent bypass search:** complete catalog scan of every foreign key with a non-restricting action (18 rows) — none writes into guarded or class-R tables other than the one above; ON UPDATE actions: none. T-DB-15 (catalog test) is the right permanent control. **Residual (P2, QA-R6-M0-018):** the *document row* itself (hash, title) stays editable by the legacy credential, so the proof can be altered even though the link cannot be erased.

### QA-R6-M0-009 — CLOSED
**Evidence.** `r6_open_referral_case(complaint, actor, key)` accepts no case id; the Round-4 forging attack no longer has an entry point; direct write of the link → `42501`. Four concurrent submissions with different keys → one case, three `LD021`; with the same key → one case id, replays identical; later retry with the same key returns the case, another key/actor is refused; failing intake date or numeric cast → case, link, status and reference counter unchanged; six parallel referrals → unique consecutive numbers `LD-2026-00004…00009`; all new cases `RESTRICTED`, one link each; policy OFF → `LD023`. **Remaining risk (stated by the author, accepted as P2):** the database does not require parties/documents/assignment at COMMIT (a legal-credential caller could commit a bare restricted case), the manifest-approval columns are set by no function in the model (INFERRED: approval is not bound to content), and the legacy-writable counter lets the legacy credential make referral/intake fail by squatting the next number (availability, clean rollback). → QA-R6-M0-019/-020/-021.

## 3. New findings (none blocks M0; all carried into the stated milestone)

| ID | Sev | Finding | Evidence | Fix / gate |
|---|---|---|---|---|
| QA-R6-M0-017 | P2 | **Existence oracle for the complaint↔case link.** `r6_case_is_complaint_linked(uuid)` is executable by the application credential for *any* case id (true/false); a failed delete of a linked case names `complaint_case_link` (`23503`); the declassification refusal text says "linked to a complaint" (`LD010`). 08 claims no application role reads class C except through the readers and hidden = unknown. | `r5_adversarial.py` section 002, `outputs-r5-adversarial.txt` | Replace the helper by a guarded boolean column on the case (owner-writable only) or a non-granted check; map `LD010`/`23503` to a generic refusal; add to T-CONF-19. **Before M4.** |
| QA-R6-M0-018 | P2 | Proof-document content not protected: the legacy credential can change `sha256`/title of a document referenced as a protected deadline's proof. | `DEV-R5-1-obs` | Extend the allow-list guard to `case_document` rows referenced as proof (new T-DL id). **Before M1 accepts any completion path.** |
| QA-R6-M0-019 | P2 | **Declassification is single-actor in the model.** With `P-DECLASS` ON one legal-credential call declassified the case; 05 §B.5 requires a second `COMPLAINT_APPROVE` holder's approval record. `P-DECLASS` is OFF and can be turned ON only by two operators. | `outputs-r5-declass.txt` | Execute T-CONF-16 with the approval record; **before M5**; never ON before D-20. |
| QA-R6-M0-020 | P2 | Referral completeness and approval provenance are adapter-only: no DB check that parties/documents/assignment exist at COMMIT; manifest approval is not bound to the draft content (no hash). | 08 §4 limit 1, model 25 | Deferred completeness check or hash-bound approval; prove with T-REF-4/10. **Before M5.** |
| QA-R6-M0-021 | P3 | Operator-ceremony hygiene: a confirmed `BOOTSTRAP_OPEN` made before a bootstrap revocation still executes afterwards (bounded by 24 h, still needs nominee tokens and a two-person COMPLETE); any one operator can cancel others' confirmed requests; the legacy-writable reference counter can be squatted; statistics views reveal complaint row counts. | `001-obs`, `009-obs`, `002-obs` | Cancel pending `BOOTSTRAP_OPEN` on revoke; restrict cancel to requester or two persons; guard the counter; document the stats leak. M1-pre/M5. |

## 4. Answers to the specific questions
- **Caller-supplied identity / D-09:** stated accurately in 08 §2.1 and §5, not accepted, still pending; treat as the largest residual risk of the whole programme.
- **Real migration vs proof model:** every claim above is about the reduced model; names, grants and trigger order must be re-attacked on the real migrations (M1-pre gate).
- **Existing-version compatibility:** DB-level behaviour for unlinked records unchanged (author probes `53`, `34` replayed); the previous-release jar was not run — **NOT_VERIFIED**; plus the author's documented limit: an old release that needs its own DDL cannot install on the split schema.
- **Unexecuted guards:** `r6_guard_case_grant` / `r6_guard_case_assignment` were never executed by the author or me — NOT_VERIFIED, M5 gate.
- 61/61 is reproducible; it proves PostgreSQL behaviour of the model, **not** application behaviour.

## 5. Security / data verdicts
Security architecture: **ACCEPTED as design** conditional on the D-09 choice and the P2 list. Data and transactions: **ACCEPTED** (keys, lock order, R-1 at COMMIT, idempotency, rollback, concurrency verified). Application level: **NOT VERIFIED**.

## 6. Pending decisions (nothing assumed or activated)
**Owner:** D-09 (two operator persons and provisioning; choose A/B/C for the caller-supplied-identity residual; 24-hour request lifetime; custodian approval in addition to two operators for normal grants; adding custodians later; Tier A out of process); **approval to change `deploy/compose.yaml`, `deploy/scripts/deploy.sh`, `deploy/postgres/` and secrets for M1-pre**; acceptance that an old release cannot run its own DDL on the split schema; D-12, D-14, D-15, D-17–D-23, D-25; `P-MANIFEST` and `P-DECLASS` stay OFF. **CITRA Legal Affairs:** D-01–D-05, D-07, D-08, D-10, D-11, D-13, D-16, D-24; all nine legal gates remain OFF.

See `M1-PRE-HANDOFF.md` for the implementation handoff and `TRACKER.md`.
