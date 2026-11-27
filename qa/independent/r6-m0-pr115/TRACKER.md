# LawDesk delivery tracker (evidence-backed; updated 2026-11-27, after R6 M0 Round 5)

| Metric | Value | Basis |
|---|---|---|
| Source requirements (original Word document) | **55** register IDs over **209** source paragraphs (209/209 text-identical to the extract) | independent parse |
| Implemented in code (R6) | **0** | PR #115 docs only; no R6 code/migration |
| Independently verified (R6, executed) | **0** requirements; design mechanisms executed in PG18: ownership split, guards, composite keys, lock order, referral invariant | Round 4 |
| Deployed to UAT (R6) | **0** | develop has no R6 |
| Planned tests / executed R6 implementation tests | **280** (corrected from 222, a Round-3 figure) / **0** | register, counted at `9da22f0` |
| Legal gates / decisions unapproved | **9 / 25 (all 25 open or provisional; none approved)** | register |
| Blocked requirements | not computed — requires per-row gate mapping by the register owner | — |
| Open R6 design defects | **P0 0, P1 0, P2 4 (017–020), P3 1 (021)** — none blocks M0 | Round 5 |
| Closed finding IDs | **16 of 16** (+ DEV-R5-1) | Round 5 |
| Other open defects (R1–R5, PR #104 retest, PR #114 rejection) | **NOT re-inventoried by me yet** | pending takeover audit |
| Current source SHA (develop) | `92822792dbb31c0246380d1ba3046bbd6b51d341` (PR #116 merged; test-only delta vs `18822a8`) | git |
| Latest R6 SHA | `9da22f0fd4afee40f808600d1971f4d95eb6a99d` (PR #115, draft); develop now `92822792` | git |
| Current UAT SHA | `18822a83…` per GitHub deployment record (2026-11-10); newer develop SHA not re-checked; `/actuator/health` UP on 2026-11-27. Running SHA **NOT_VERIFIED** (no public version endpoint) | `gh api deployments` |
| Next implementation milestone | **M1-pre** (QA-ready; awaiting the owner's go-ahead) | — |
| Owner actions required | decide D-09 (operators, RR-1, out-of-process Tier A); authorise merge/deploy only after QA PASS; direct review of PR #116 scope | — |
| M0 verdict | **ACCEPT (design/proof-model only)**; M1-pre READY not authorised; M1 NOT READY | Round 5 |
