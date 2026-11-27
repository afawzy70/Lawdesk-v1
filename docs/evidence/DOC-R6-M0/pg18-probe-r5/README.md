# Round-5 PostgreSQL probe for `LD-DOC-R6-08` (documentation evidence — not a migration, not implementation evidence)

A **reduced model** of the proposed R6 objects on a disposable PostgreSQL 18.6 with the repository's 68 develop migrations and synthetic rows. It proves PostgreSQL privilege, trigger, constraint and lock behaviour. It proves **no Spring code**, and the previous-release jar was not run. `pg18-probe/` (Round 4) is unchanged. **Never point these tools at UAT or any shared server:** the runner creates its own cluster (trust authentication, `127.0.0.1` only) and deletes it.

## Run
Needs PostgreSQL 18 `initdb`/`pg_ctl` (`PGBIN`; the embedded binaries of the backend tests work), Python 3 with `psycopg2`; the `locks` mode also needs a JDK on `PATH` and the pgjdbc jar.

    export PGBIN=/path/to/postgres18/bin
    cd docs/evidence/DOC-R6-M0/pg18-probe-r5/tools
    python3 run_round5.py tests --model r4 --pattern test_attacks.py   # before: expected to FAIL
    python3 run_round5.py tests --model r5                             # after: expected to pass
    python3 run_round5.py chain --model r5                             # numbered files, output on stdout
    python3 run_round5.py qa --model r5 --qa-scripts DIR               # QA Round-4 scripts, unmodified
    python3 run_round5.py locks --model r5 --port 54329 --jdbc-jar JAR # Round-4 lock scenarios

`DIR` holds copies of `qa/independent/r6-m0-pr115/round4/scripts/` taken from the QA branch; they are not copied into this branch.

## Outputs (`outputs/`)
| File | Shows |
|---|---|
| `before-regressions-on-round4-model.out.txt` | attack tests on the reviewed model: 18 tests; 16 fail (the attack succeeds), 1 errors (no party-association table exists to protect), 1 passes (the runtime role already could not read class C) |
| `before-qa-suite-on-round4-model.out.txt` | the QA suite reproducing the four findings on the reviewed model |
| `after-regressions-on-round5-model.out.txt` | all tests on the corrected model |
| `after-qa-suite-on-round5-model.out.txt` | the same unmodified QA suite on the corrected model |
| `after-chain-round5.out.txt` | the numbered probe files, including the Round-4 attack files 30, 31, 33, 34, 50–53, 70, 71 |
| `after-lock-scenarios.out.txt` | old order still deadlocks (`40P01`), unified order, phantom child (`LD040`), closure both orders (`LD041`) |

## Reading the QA suite on the corrected model — important
* Sections A and D are direct evidence: reads answer `42501`, the three rewrites answer `LD002`.
* Sections B, C, S-a and S-b are refused with `42883` **because the old entry points no longer exist**. That alone proves little. The equivalent attacks on the new interfaces are in `test_two_party.py` and `test_referral.py`.
* `hijack.py` ends with a Python error: its verification query reads `complaint` as `lawdesk_legal`, which is now refused (that is finding 002 fixed). The QA reviewer must verify the case/link state with the owner.
* S-a's positive property ("exactly one link under concurrency") is therefore **not** re-shown by the QA script; see `test_concurrent_referrals`.
* In the chain, file 45's last statement is now refused by the case foreign key (its hard-coded case id no longer exists), not by uniqueness; file 71 reports that the old `r6_op_set_policy(text,boolean)` does not exist.

## Model files
`12` operator logins (group `NOLOGIN`) · `20` Tier B facts and two-person functions, Tier A writer, guards · `22` synthetic users and operator registry · `25` complaint tables, read functions, referral writer · `32` two-operator ceremony · `40` referral fixture · `42` referral sequence · `80`, `80b` names the QA suite expects. Files `05`, `10`, `26a`, `26b`, `30`, `31`, `33`, `34`, `45`, `50`–`53`, `70`, `71`, `90` are reused from `../pg18-probe/`.

## Reductions
No delegation or hash-chained security event; no case-grant or assignment guard; `judgment_party_deadline` is simplified (the keys are proved separately by files 50–52); intake validation, parties and documents of a referral are not modelled; fixtures write complaint rows as the owner; the declassification writer does **not** check the second-person approval record required by 05 §B.5.
