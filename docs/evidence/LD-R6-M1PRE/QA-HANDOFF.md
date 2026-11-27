# M1-pre — handoff to the independent QA session

**Target:** PR #118 (draft), branch `feature/LD-R6-m1-pre-db-ownership`. Implementation commits: `f05d32e` (failing tests), `c3b52ca` (implementation), `ad7de40` (rehearsal fix), `0e728e4` (audit refuses stored settings), then this evidence (documents only). **Review the PR head**; its SHA is given with the request.

**Known open item:** the subject of `0e728e4` is one character over the repository limit, so the "Repository policy" gate fails for this history (evidence README). Rewording it will change the commit SHAs of `0e728e4` and the evidence commit but not any file content. Base: `develop` `92822792`.

**Ask:** an independent verdict on the nine acceptance criteria of your `M1-PRE-HANDOFF.md`, on the real migrations and scripts (not the M0 probe). The author approves nothing. Please do not merge: merging deploys to UAT.

| # | Criterion | Author's claim | Where to check |
|---|---|---|---|
| 1 | T-DB-1 catalog audit | met | `deploy/postgres/audit.sql`, `RuntimeRolePrivilegeTest` |
| 2 | T-DB-2…8, 12 | runtime attempts → `42501`; audit fails the deploy on drift. **Partial:** restore audit is a manual step; 5–7 have no R6 functions to test | `DatabaseOwnershipTransferTest`, `deploy/tests/test_deploy_scripts.py` |
| 3 | T-DB-13 | met | `test_compose.py`; CI "Backend runs as the least-privilege role" |
| 4 | T-DB-15 and trigger order | met | `GuardedTableCatalogTest` |
| 5 | T-DB-9, T-MIG-3 previous release | met with synthetic data; DDL limit demonstrated | `tools/db-split/compat_drill.py`; CI upgrade rehearsal |
| 6 | existing data, restore, rollback | **partial: no real UAT backup copy; only PG 18.6** | evidence README |
| 7 | inspect UAT ownership | **not done** (owner/DevOps) | `inspect-ownership.sql` |
| 8 | nothing R6, nothing activated, no frontend change | met | diff against `develop` |
| 9 | this review; QA-R6-M0-021 role/counter items | counter partly addressed | `V900`, evidence README |

## Please attack
* Any way for `lawdesk_app` to gain DDL, trigger control, the owner role, or to write `flyway_schema_history` — including default privileges, `SET`, role self-changes, functions it can execute, referential actions.
* `ownership.sql`: partial transfer, re-run, restore of an old dump, interaction with a later migration that withdraws rights.
* `deploy.sh`: failure at each step, rollback to a pre-split release, a hung migration, a release directory without the scripts, `LAWDESK_ROLLBACK`.
* `audit.sql`: drift it misses.
* The counter guard: legitimate writers it would break (restore tools, data repair), bypasses.
* The first deployment on the shared host: interruption, secrets, container recreation, the root-owned gate (unchanged).

## Reproduce
    cd backend && ./mvnw verify
    python3 -I -m unittest discover -s deploy/tests && python3 -I -m unittest discover -s tools/tests
    python3 -I tools/db-split/compat_drill.py --pg-bin <PG18 server bin> --client-bin <PG18 client bin> \
        --previous-jar <jar built from 92822792> --current-jar <jar built from the head> --java <java 21>
The drill creates and removes its own clusters. Never point anything at UAT.

## Owner decisions untouched
D-09 pending; all nine legal gates, `P-MANIFEST` and `P-DECLASS` do not exist in code and stay off.
