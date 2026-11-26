# LD-DOC-R6 M0 evidence (revision after independent QA REJECT of 865ee1c)

* `source-extract.txt`: paragraph-numbered text of `البرنامج.docx` (the `¶n` of the register).
* `verify-source.txt`: output of `tools/docs/verify_source.py` run on the original DOCX (sha256 `777d4d36…2ffa5`): IDENTICAL, 209/209 paragraphs; page boundaries not verifiable. Independent verification pending.
* `checks.txt`: output of `tools/docs/r6_m0_check.py` (consistency, traceability, test/decision/gate definitions, terminology, migration-number scan).
* `pg18-probe/` (Round 4): a disposable PostgreSQL 18.6 probe on the real develop schema with a reduced model of the proposed R6 objects; scripts, outputs and a README. Evidence for `LD-DOC-R6-07`; not a migration, not application code.
* Baseline: `origin/develop` = `18822a83bff8e2a38b83db4370674a9f69919d81`. No application code, migration or test changed.
* Not QA-accepted; not merged; not deployed. Findings are closed only by Codex.
