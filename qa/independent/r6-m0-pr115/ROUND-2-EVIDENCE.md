# Round 2 independent evidence ledger

Review target `afawzy70/Lawdesk-v1` PR #115 at `48e39d903bb84a21c9b3e7e8a33f147fe0feff76`; previous rejected head `865ee1c67a819f1dd6863671cd0e67f07a14fab3`; baseline `develop` `18822a83bff8e2a38b83db4370674a9f69919d81`. Evidence collected 2026-11-26. The original DOCX remains solely in the private handoff directory; no source text or binary is stored here.

| Check | Independent result | Method / evidence |
|---|---|---|
| PR revision | Open draft, exact target SHA | GitHub PR metadata re-read at start and at report completion |
| Delta from rejected SHA | One commit, 14 changed files, docs/evidence/tools only; source extract unchanged | GitHub compare API `865ee1c..48e39d9` |
| Original file | Exists, 28,584 bytes | Private handoff path supplied by owner, file metadata |
| Original SHA-256 | `777d4d364e49a6c54c9214a3565e89f780af11924e0ac4b644f1183cc242ffa5`; equals recorded digest | `sha256sum` plus independent Python `hashlib.sha256` |
| Original/extract text | 209/209 paragraphs exactly equal, contiguous numbers 1–209, no first mismatch | Python `zipfile` + ElementTree parse of `word/document.xml` and numbered extract; separate from author's regex-based verifier |
| Side text | No header, footer, footnote, endnote or comments XML part; no media | ZIP member enumeration |
| Pages | Metadata says 7; printed page boundaries unverified | `docProps/app.xml` and `LD-DOC-R6-00:20` |
| Requirements | 55 unique IDs: PJ 20, ST 8, AL 6, PC 21 | Independent AST parsing of `r6_register_data.py` plus rendered register row scan |
| Planned tests | 129 row tests + 50 cross-cutting = 179 distinct IDs | Independent AST parsing; descriptions all nonempty |
| Decisions | D-01–D-25 = 25 | Register row scan of `LD-DOC-R6-04` |
| Gates | 9 distinct named gates | Scan of all R6 design pages |
| Source citations | 176 cited; 33 uncited structural; ¶203 cited twice | Independent source-reference expansion from 55 register rows |
| Register generation | Exact text equality | Import `r6_register_build.render()` and compare to `LD-DOC-R6-01` |
| Actual authority loading | ADMIN gets every enum; account permissions come from role_permission; role admin accepts known codes | `LawdeskUser.java:36-46`; `UserAccounts.java:125-133`; `RoleAdministration.java:61-109` at develop SHA |
| Existing legal write paths | Legacy judgment create/update accepts `final`; legacy judgment pointer and deadline endpoints remain | `JudgmentRequests.java:55-73`; `JudgmentService.java:91-124`; `JudgmentController.java:39-64`; `DeadlineController.java:47-96` |
| Existing declassification path | `CASE_GRANT_MANAGE` can set case confidentiality NORMAL without complaint check | `AccessController.java:55-59`; `AccessAdministration.java:225-253` |
| Runtime R6 tests | None claimed or run | M0 contains no R6 application code or migration; planned T-* IDs are future tests |

The full finding-by-finding assessment and required corrections are in [ROUND-2.md](ROUND-2.md). The private DOCX digest is included for provenance; its content is not part of the QA artifact.
