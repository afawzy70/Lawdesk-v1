# Round-5 retest request — LawDesk R6 M0 (to the independent Claude QA session)

**Target:** PR #115, branch `feature/LD-DOC-r6-m0-requirements`. Design and evidence content: commit `0bbd8e9bd317f0e0566bbdaf793de21619b58a2d` (the PR head adds only this request on top). **Previous rejected SHA:** `de23b53a1450e148bfb252a678814eda38f07d7d`. **Develop baseline:** `92822792dbb31c0246380d1ba3046bbd6b51d341` (merged in at `1f3424e`).

You are asked for an independent verdict on **QA-R6-M0-001, -002, -006, -009** only, plus the developer finding below. The author closes nothing. M0 documentation and a probe model only: no application code, migration or deployment change, nothing run against UAT.

## Read
`docs/design/r6/LD-DOC-R6-08-round5-closure.md` (governs over 07 and 05), then `docs/evidence/DOC-R6-M0/pg18-probe-r5/README.md`. Please attack the model with your own scripts; do not rely on the author's tests.

## Reproduce
    export PGBIN=<PostgreSQL 18 bin>   # disposable cluster, loopback only, created and removed by the runner
    cd docs/evidence/DOC-R6-M0/pg18-probe-r5/tools
    python3 run_round5.py tests --model r4 --pattern test_attacks.py   # author result: 18 tests, 16 fail, 1 error, 1 pass
    python3 run_round5.py tests --model r5                             # author result: 61/61
    python3 run_round5.py qa --model r5 --qa-scripts <your round4/scripts>

## Your scripts on the new model — what will look different
* Round-4 operator calls and `r6_complete_referral` no longer exist (`42883`). That is **not** evidence by itself: attack the new entry points.
* `hijack.py` ends in an error because its check reads `complaint` as the legal role. Verify the state as the owner.
* `conc.py` S-a/S-b cannot call the removed function: please write the concurrency test for `r6_open_referral_case`.

## What to try to break
| Finding | Claim to falsify |
|---|---|
| 001 | no single login or single person can cause any of the nine Tier B effects, including bootstrap and revocation; no replay, no change after confirmation, no stale confirmed request that undoes a revocation |
| 002 | no application role reads class C except through the five readers; hidden equals unknown. Judge whether the residual of 08 §2.1 (caller-supplied actor id) is stated completely; it is **not** accepted, D-09 is pending |
| 006 | no application write changes a legal column of a protected deadline or judgment, by any statement shape, referential action, trigger path or race |
| 009 | no application path links a pre-existing case; the case is RESTRICTED from its INSERT; exactly one case under retry and concurrency; rollback leaves nothing |

## Developer finding to verify (not in your report): DEV-R5-1
On the Round-4 model the runtime credential could erase `proof_document_id` of a protected deadline: set `lawdesk.archive_purge`, delete the proof document, and `ON DELETE SET NULL` ran as the table owner, passing the `current_user` guard. Fixed by also requiring `pg_trigger_depth() = 1`. Regression: `test_referential_action_cannot_erase_completion_proof`. Please assign severity and look for further paths of this kind.

## Known limits (please judge them, do not assume they are accepted)
08 §4 "Limits" and §8: reduced model, no Spring code; previous-release jar not run; UAT ownership not checked; the referral writer does not enforce that parties and assignment exist at COMMIT; case-grant and assignment guards never executed; declassification in the model lacks the second-person approval record of 05 §B.5; two logins are two persons only by provisioning.

## Documentation checks
55 requirements, 25 decisions, nine gates unchanged; test ids 273 → 280 (added T-DB-14, T-DB-15, T-CONF-19, T-CONF-20, T-DL-11, T-REF-11, T-REF-12; wording of T-DB-4, T-CONF-18, T-REF-1 corrected; none removed). Your tracker records 222 planned tests; the checker counted 273 at `de23b53`. `r6_m0_check.py` exits 0; `verify_source.py` on the handoff DOCX: IDENTICAL 209/209.

## Verdict requested
Per finding CLOSED / OPEN with severity; M0 acceptance YES/NO; M1-pre readiness YES/NO.
