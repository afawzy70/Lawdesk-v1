# PostgreSQL 18 probe for `LD-DOC-R6-07` (documentation evidence, not a migration)

What this is: a disposable PostgreSQL **18.6** (the embedded binaries the repository's own tests use), the **real develop schema** (all 68 develop migrations applied as `lawdesk_app`, i.e. as a fresh deployment does today), synthetic rows only, and a **reduced model** of the proposed R6 objects. It proves PostgreSQL privilege, trigger, constraint and lock semantics. It does **not** prove any Spring code (none exists in M0) and it did **not** run the previous-release jar. Nothing here touches the repository's deployment, CI or a migration directory; the `*.sql.txt` files are scripts for a scratch server.

## Reproduce
1. Start any PostgreSQL 18 (loopback only, scratch data directory); create database `lawdesk`, role `lawdesk_app`, schema `lawdesk AUTHORIZATION lawdesk_app`, `search_path = lawdesk` (as `deploy/postgres/app-role.sql` does).
2. `tools/Migrate.java <user> <db> backend/src/main/resources/db/migration` applies the develop migrations in version order as `lawdesk_app`.
3. `tools/Run.java <user> <db> <file>` runs a file whose statements are separated by a line containing only `;;`, printing `OK`/`DENY` with the SQLSTATE (classpath: the pgjdbc 42.7.x jar). `tools/Lock.java <scenario>` runs the concurrency scenarios (`inverted`, `unified`, `phantom`, `referral`, `closure`).
4. Run the numbered files in order as the user named in the file's first comment line (`postgres` = superuser).

## Files and what they show
| File | Role | Shows |
|---|---|---|
| `05-synthetic-fixture.sql.txt` | lawdesk_app | synthetic case/court/proceeding/judgment; a stand-in for Flyway's history table |
| `01`, `02` | postgres, lawdesk_app | **current model**: all 92 tables / 6 sequences / 293 indexes / 59 functions owned by `lawdesk_app`; the runtime role can `DISABLE TRIGGER`, `DROP TRIGGER`, replace a guard function and create tables |
| `10-ownership-split.sql.txt` | postgres | roles, `REASSIGN OWNED`, grants, default privileges (the existing-install transfer) |
| `20`, `25` | lawdesk_owner | reduced model: class-R/C tables, guards (invoker, `current_user`), Tier A/B functions, declassification guard |
| `26a`, `26b` | owner, app | `role_permission` guard refuses sensitive codes for every role (`LD060`) |
| `30`–`34` | app, legal, operator | the six required proofs; `run-transcript.txt` digests of the protected state before/after each step |
| `40`–`45` | owner, legal, operator | referral: contradiction of the old design (`41`), the eight-step sequence and refusals (`42`, `44`), deferred invariant at COMMIT (`45`) |
| `50`–`52` | owner, app | composite keys on the real tables; one line per constraint that must fire |
| `53` | app | legacy behaviour for an unlinked judgment; what a previous release's Flyway can and cannot do |
| `60` (`tools/Lock.java`) | owner, legal | deadlock of the withdrawn lock order; the single order; phantom child; concurrent referrals; closure vs R6 write |
| `70`–`72` | owner, app, legal | invoker vs definer trigger `current_user`; pinned `search_path` vs temp-schema shadowing |
| `80`–`85` | postgres, operators, legal | two-party operator requests, revocation, credential containment |
| `*.out.txt` | | outputs of the run kept in the repository |

Known reductions: the probe's Tier A functions model the checks of `07` (gate, grant, maker-checker, lock, writes) but not the whole business logic; the litigant/filing tables are named `x_*`; `flyway_schema_history` is a stand-in table; one local PostgreSQL minor version (18.6) was used, UAT's was not inspected.
