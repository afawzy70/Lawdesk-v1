# LD-DOC-R6 — M0 design package (index, vocabulary, provenance, change log)

**M0 is documentation only.** No application code, migration or test is changed. Nothing described as PROPOSED exists in the product; nothing here is QA-accepted, merged or deployed. The first package (`865ee1c`) was **rejected** by independent QA (16 findings); this revision answers each in `LD-DOC-R6-06`. Findings are not closed by the author.

## Vocabulary (used in every R6 document)
| Label | Meaning |
|---|---|
| **EXISTING (develop 18822a8)** | Read in the develop tree and cited to a file. A mechanism that exists is not a legal approval. |
| **PROPOSED** | Design offered for review. Not built, not approved. Every R6 behaviour in M0 is PROPOSED. |
| **BUILT IN R6** | Code merged for an R6 milestone. **None.** (The word "implemented" is not used for R6 in M0.) |
| **SPECIFIED** | Stated by the source document; still only a proposal until built and tested. |
| **PROVISIONAL** | Owner direction given for review; not legal approval; legal effect stays inactive. |
| **BLOCKED** | Activation prohibited until CITRA Legal Affairs approves and a versioned approval record exists. |
| **OWNER-PENDING** | An owner design decision is outstanding. |

## Source provenance (QA-R6-M0-003)
* File: `البرنامج.docx`, 28,584 bytes, received in the session upload 2026-11-26.
* SHA-256 of the original DOCX: `777d4d364e49a6c54c9214a3565e89f780af11924e0ac4b644f1183cc242ffa5`
* Author verification (this session, with the original in hand): `python3 tools/docs/verify_source.py <docx>` → digest equals the line above; the 209 non-empty paragraphs of `word/document.xml` (body and table cells, in order) equal `docs/evidence/DOC-R6-M0/source-extract.txt` line by line; no header, footer, footnote, endnote or comment part adds text. Output: `docs/evidence/DOC-R6-M0/verify-source.txt`.
* Page count: the file's own metadata (`docProps/app.xml`) says 7 pages; page boundaries cannot be verified without rendering the document in a word processor, so rows cite section and paragraph `¶n`. **Page mapping: not verified.**
* **Independent verification: PENDING (Codex).** The DOCX is not committed (the repository is public and the document is internal to CITRA Legal Affairs). Handoff: a copy is at `/srv/ai-workspaces/lawdesk/handoff-r6-m0/program-source.docx` (+ `.sha256`) on the host where both agents run; Codex runs `verify_source.py` on it and compares the digest. If that path is not readable for Codex, the owner must supply the same file (digest above) by the approved private channel; until then source fidelity stays **BLOCKED for independent QA** even though the author's check passes.

## Documents
01 register (generated; 55 requirements, acceptance criteria, test ids, verification) · 02 gaps, R5 compatibility · 03 migration/recovery, permissions, states · 04 conflicts, 25 decisions, test plan, milestones · 05 authoritative design (authorization, confidentiality, data model, gates, alerts) · 06 QA finding-to-correction matrix · **07 architecture closure appendix (Round 4: database security boundary, authorised deadline creation, appeal integrity and locking, atomic referral; governs where it differs from 05)**. Evidence of the PostgreSQL 18 probe: `docs/evidence/DOC-R6-M0/pg18-probe/`.

## Checks
`python3 tools/docs/r6_register_build.py` regenerates 01; `python3 tools/docs/r6_m0_check.py` (reads files only) checks: every source paragraph is cited or structural; every row has source, expected, baseline, proposed, acceptance, ≥1 test and dependency; every test id used anywhere is defined; every `D-nn`/gate referenced is defined; the word "implemented" is not used for R6 behaviour; prints the highest Flyway version on every remote branch. Output: `docs/evidence/DOC-R6-M0/checks.txt`.

## Change log
* `6fc1efa` first package. `865ee1c` first correction (owner review). **QA REJECT** of `865ee1c` (16 findings).
* `48e39d9`: **QA REJECT (Round 2)** — nine findings closed, seven open (3 P0, 3 P1, 1 P2).
* `19f008a`: **QA REJECT (Round 3)** — 001, 002, 006 (P0) and 004, 005, 009 (P1) open; 013 closed. Ten of sixteen closed.
* Round 4 revision (this): `LD-DOC-R6-07` — three database identities with exact PostgreSQL 18 setup and an existing-install ownership transfer, three trust tiers (operator / application / owner), `SECURITY DEFINER` rules, bootstrap and compromise analysis; the I-4 contradiction resolved by a `current_user` discriminator and an exact activation transaction; composite keys and one global lock order; the referral sequence with a deferred invariant and a restricted intake adapter. Evidence: a probe on PostgreSQL 18.6 with the real develop schema. Register now 273 test ids.
* Round 3 revision: sensitive-authority model with seven layers and a first-custodian bootstrap; case-confidentiality guards and owner policies; legacy write-path inventory with database invariants; litigant/filing/capacity model; mandatory atomic referral; one-transaction alert publication. Register now 222 test ids.
* `48e39d9` revision (previous): authorization redesign from the real `LawdeskUser`; referral confidentiality model; one authoritative judgment/deadline/appeal/stage model; legal gates with DB-level enforcement; M1 reduced to schema/history/draft; register rebuilt with acceptance criteria and executable test ids for all 55 rows; PJ-02 corrected; D-25 added; recovery and compatibility claims corrected; R5 matrix corrected.
