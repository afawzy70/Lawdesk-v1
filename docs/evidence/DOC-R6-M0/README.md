# LD-DOC-R6 M0 evidence (revision after independent QA REJECT of 865ee1c)

* `source-extract.txt`: paragraph-numbered text of `البرنامج.docx` (the `¶n` of the register).
* `verify-source.txt`: output of `tools/docs/verify_source.py` run on the original DOCX (sha256 `777d4d36…2ffa5`): IDENTICAL, 209/209 paragraphs; page boundaries not verifiable. Independent QA verified the 209 paragraphs in its Round 4 review.
* `checks.txt`: output of `tools/docs/r6_m0_check.py` (consistency, traceability, test/decision/gate definitions, terminology, migration-number scan).
* `pg18-probe/` (Round 4): a disposable PostgreSQL 18.6 probe on the real develop schema with a reduced model of the proposed R6 objects; scripts, outputs and a README. Evidence for `LD-DOC-R6-07`; not a migration, not application code.
* `pg18-probe-r5/` (Round 5): corrected model, failing-before and passing-after regression tests, and the unmodified QA Round-4 suite on both. Evidence for `LD-DOC-R6-08`.
* Baseline: `origin/develop` = `92822792dbb31c0246380d1ba3046bbd6b51d341`. This branch changes no application code, migration or application test.
* Not QA-accepted; not merged; not deployed. Findings are closed only by the independent QA session.
