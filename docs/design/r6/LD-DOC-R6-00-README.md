# LD-DOC-R6 — M0 design package (index, vocabulary, change log)

**M0 is documentation only.** No application code, migration or test is changed by it. Nothing described as "proposed" exists in the product; nothing here is QA-accepted, merged or deployed.

## Vocabulary used in every R6 document

| Label | Meaning | Where it appears |
|---|---|---|
| **EXISTING (develop 18822a8)** | Behaviour or schema read in the develop tree. Cited to a migration or class. | "Existing" columns, §1 of 02 |
| **PROPOSED** | Design offered for review. Not implemented, not approved. | "Gap → proposal", 02 §3, 03, 04 |
| **IMPLEMENTED (R6)** | Code merged for an R6 milestone. **None yet.** | evidence folders `docs/evidence/DOC-R6-Mx` (M0 has none) |
| **PROVISIONAL (owner direction)** | A direction the owner has given for review; not legal approval. | decisions D-16, D-19, D-23, D-24 |
| **BLOCKED (legal authority)** | Activation prohibited until the appropriate legal authority approves and the approval is recorded. | decisions D-01…D-05, D-07, D-08, D-10, D-11 |

## Source
`البرنامج.docx`: the document's own metadata reports **7 pages**; the text layer has no page markers, so rows cite the section heading and the paragraph number `¶n` of `docs/evidence/DOC-R6-M0/source-extract.txt` (209 paragraphs). The reader maps `¶` to the printed pages. Terminology is kept as written («الهيئة» = CITRA).

## Documents
01 register · 02 gap, architecture, R5 compatibility · 03 migration/recovery, permissions, states · 04 conflicts, decisions, tests, milestones.

## Consistency and traceability checks
`python3 tools/docs/r6_m0_check.py` (reads files only): every source paragraph is cited by a register row or is listed as structural; every requirement or decision id used is defined; every decision is referenced; prints the highest Flyway version on every remote branch. Result of the last run is in `docs/evidence/DOC-R6-M0/checks.txt`.

## Change log
* 2026-11-26 `6fc1efa` first M0 package (PR #115).
* Correction round after owner review (this revision): status vocabulary; D-14, D-09, D-20 redesigned; D-16/19/23/24 marked provisional; legal decisions gated; recovery redesigned (application rollback / restore / forward recovery); migration numbers are a plan, rechecked by script; R5 compatibility matrix rebuilt from the actual R5 diff; source coverage check added. The register has **55 rows** (the first report said 57; the correct count is 55).
