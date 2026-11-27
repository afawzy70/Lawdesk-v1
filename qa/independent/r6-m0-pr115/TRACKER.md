# LawDesk delivery tracker (evidence-backed; updated 2026-11-27, after R6 M0 Round 4)

| Metric | Value | Basis |
|---|---|---|
| Source requirements (original Word document) | **55** register IDs over **209** source paragraphs (209/209 text-identical to the extract) | independent parse |
| Implemented in code (R6) | **0** | PR #115 docs only; no R6 code/migration |
| Independently verified (R6, executed) | **0** requirements; design mechanisms executed in PG18: ownership split, guards, composite keys, lock order, referral invariant | Round 4 |
| Deployed to UAT (R6) | **0** | develop has no R6 |
| Planned tests / executed R6 implementation tests | 222 / **0** | register |
| Legal gates / decisions unapproved | **9 / 25 (all 25 open or provisional; none approved)** | register |
| Blocked requirements | not computed — requires per-row gate mapping by the register owner | — |
| Open R6 design defects | **P0 0, P1 4 (001, 002, 006, 009), P2 0, P3 0** | Round 4 |
| Closed finding IDs | 12 of 16 | Round 4 |
| Other open defects (R1–R5, PR #104 retest, PR #114 rejection) | **NOT re-inventoried by me yet** | pending takeover audit |
| Current source SHA (develop) | `18822a83bff8e2a38b83db4370674a9f69919d81` | git |
| Latest R6 SHA | `de23b53a1450e148bfb252a678814eda38f07d7d` (PR #115, draft) | git |
| Current UAT SHA | `18822a83…` per GitHub deployment record (2026-11-10); `/actuator/health` UP on 2026-11-27. Running SHA **NOT_VERIFIED** (no public version endpoint) | `gh api deployments` |
| Next implementation milestone | none authorised; M1-pre after Round 5 acceptance | — |
| Owner actions required | decide D-09 (operators, RR-1, out-of-process Tier A); authorise merge/deploy only after QA PASS; direct review of PR #116 scope | — |
