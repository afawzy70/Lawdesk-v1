# Original acceptance scenarios S1–S16, repeated on the new deployed SHA

Every result below refers to UAT SHA `aea33fa3d8e1f92a5b8bde5ad6f34c99e4bf1bfa`; the individual `runs-aea33fa/*.json` files contain the checked steps and the release observations. PASS means only the stated scenario, not every field or authorization rule of its module. Row IDs are in `coverage.csv`.

| Scenario | Requirement ID | QA result | Evidence and remaining scope |
|---|---|---|---|
| S1: three claims, different judgment results | LD-DOC-35-001; LD-DOC-05-001, 05-012–025 | PASS | `runs-aea33fa/wp2.json`: three ordered claims; results REJECTED, ACCEPTED, PARTIAL. Other outcome values remain separate row checks. |
| S2: amend claim and retain text, actor, time | LD-DOC-35-002; LD-DOC-05-011 | PASS | `runs-aea33fa/wp2.json`: verbatim old text, change actor and date. |
| S3: defence, document, memo, judgment position | LD-DOC-35-003; LD-DOC-17, 20 | PASS | `runs-aea33fa/wp2.json`: memo link, document link, court positions. |
| S4: library judgment, separate principle, search | LD-DOC-35-004; LD-DOC-16-001, 16-022, 16-041 | PASS | `runs-aea33fa/wp7.json`: independent principle and search in its text. |
| S5: new-case suggestions, source, similarities and differences | LD-DOC-35-005; LD-DOC-16-043–044 | PARTIAL | `runs-aea33fa/wp7.json`: source and scored similarity; explicit difference presentation and memo drafting path not asserted. |
| S6: request, partial/incomplete/complete replies, delay | LD-DOC-35-006; LD-DOC-18 | PARTIAL | `runs-aea33fa/wp3_followup.json`: request, partial/document reply and overdue follow-up; all three completeness states in one history not asserted. |
| S7: risk update after new document, prior version | LD-DOC-35-007; LD-DOC-19 | PASS | `runs-aea33fa/wp3_followup.json`: version 2 triggered by new document, version 1 unchanged. |
| S8: same document linked to fact, defence, claim | LD-DOC-35-008; LD-DOC-13-009, 20 | PARTIAL | `runs-aea33fa/wp2.json`: fact and defence link plus claim source shown; identity of one document across all three not asserted. |
| S9: adjournment keeps previous hearing, no next date | LD-DOC-35-009; LD-DOC-07 | PASS | `runs-aea33fa/wp3_followup.json`: old hearing retained and undated adjournment follow-up. |
| S10: reciprocal case link, independent closure | LD-DOC-35-010; LD-DOC-08-012–013 | PASS | `runs-aea33fa/wp3_followup.json`: relation on both cases, other case stays OPEN. |
| S11: personal and general calendars, conflict | LD-DOC-35-011; LD-DOC-15 | PASS | `runs-aea33fa/wp6.json`: both views and conflict detection. Full UI matrix remains pending. |
| S12: closure, history, review open items | LD-DOC-35-012; LD-DOC-33 | PARTIAL | `runs-aea33fa/wp3_followup.json`: history remains after closure; full checklist and future open dates not independently asserted. |
| S13: independent court numbers, stable judicial reference, past results | LD-DOC-35-013; LD-DOC-04–05 | PARTIAL | `runs-aea33fa/wp2.json` and `runs-aea33fa/wp1d_stages.json`: stage results retained; stable automated reference and independent court numbers need a joined assertion. |
| S14: calculate, alert, review, approve | LD-DOC-35-014; LD-DOC-10-011–012 | PARTIAL | `runs-aea33fa/wp4.json`: synthetic rule and approval; operational legal rules and official holidays await D6. |
| S15: seven roles, scopes, grants, secrecy and revocation | LD-DOC-35-015; LD-DOC-30 | PARTIAL | `runs-aea33fa/wp1_access.json` and isolated `CaseBoundEndpointsAccessTest`: narrow checks pass. Admin UI and seven-role matrix are BLOCKED by `qa.admin` 401 or NOT_TESTED. |
| S16: assistant citation, refusal, access and human review | LD-DOC-35-016; LD-DOC-22 | PARTIAL | `runs-aea33fa/wp10.json`: deterministic test-provider checks only. Operational scenario BLOCKED until D8; no external AI was called. |

No whole-project QA acceptance is implied by the eight scenario PASS results.

The new provider-free case brief was probed separately (`brief-aea33fa-result.json`); it does not turn operational S16 into PASS.
