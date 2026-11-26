# R6 M0 requirement-by-requirement audit

All 55 IDs below were inspected in the register at PR #115 head `865ee1c`. All R6 implementation statuses are **Not started**. Source references are to the supplied extract only; fidelity to the unavailable original DOCX is **BLOCKED** for every row. “AC present” means a summary exists in the register, not that it is sufficient or legally approved.

| ID | Extract reference | Register test | Register AC | Decision | Independent QA note |
|---|---|---|---|---|---|
| PJ-01 | §1 ¶4-6 | T-A01 | Present | D-08 | No additional row-specific defect; source fidelity blocked. |
| PJ-02 | §1 ¶7-8 | T-A02 | Present | D-17 | Existing operative text misclassified (QA-012). |
| PJ-03 | §1 ¶9-11 | T-A03 | Present | D-01, D-02, D-03, D-04 | Existing deadline integration unresolved (QA-004); legal gate (QA-006). |
| PJ-04 | §1 ¶12 | T-A04 | Present | — | No additional row-specific defect; source fidelity blocked. |
| PJ-05 | §1 ¶13 | T-A05, T-A07 | Present | D-05, D-06 | Two party deadlines need legacy mapping (QA-004). |
| PJ-06 | §2أ ¶16-21 | T-A10, T-A11 | Present | D-07, D-12 | Finality and next stage blocked by D-07/D-08. |
| PJ-07 | §2ب ¶23-29 | T-A12-T-A14 | Present | D-07, D-09, D-21 | Finality and maker/checker blocked; appeal integration unresolved. |
| PJ-08 | §2ج ¶31-41 | T-A15 (×6) | Present | — | Simultaneous appeals lack proceeding mapping (QA-005). |
| PJ-09 | §3 ¶43 | T-A16 | Present | D-07 | Manual finality gate required. |
| PJ-10 | §3 ¶44 | T-A06 | Present | D-05 | No additional row-specific defect; source fidelity blocked. |
| PJ-11 | §3 ¶45 | T-X03 | Present | — | No additional row-specific defect; source fidelity blocked. |
| PJ-12 | §3 ¶46-50 | T-A12 | Present | D-09 | No additional row-specific defect; source fidelity blocked. |
| PJ-13 | §3 ¶51-55 | T-A17 | Present | — | No additional row-specific defect; source fidelity blocked. |
| PJ-14 | §3 ¶56-58 | T-A18 | Present | — | No additional row-specific defect; source fidelity blocked. |
| PJ-15 | §3 ¶59 | T-A14 | Present | — | No additional row-specific defect; source fidelity blocked. |
| PJ-16 | §3 ¶60 | T-A08, T-A09 | Present | — | Full recalculation snapshot needed (QA-015). |
| PJ-17 | §5 ¶99-106 | T-A19 | Present | D-21 | Simultaneous appeal status unresolved (QA-005). |
| PJ-18 | §6 ¶108-120 | T-A09 | Present | D-12 | Snapshot must integrate with existing proceeding/deadline. |
| PJ-19 | §6 ¶121-129 | T-A15 | Present | — | Appeal-to-proceeding cardinality unresolved (QA-005). |
| PJ-20 | §8 ¶149-165 | T-X01.. | Present | — | Grouped safeguards; open-ended T-X01.. (QA-010). |
| ST-01 | §4.1 ¶63 | T-B01 | Present | D-09 | No additional row-specific defect; source fidelity blocked. |
| ST-02 | §4.2 ¶65-73 | T-B02 | Present | D-01 | Legal activation gate and versioned approval. |
| ST-03 | §4.3 ¶75-91 | T-B03 | Present | D-01 | No additional row-specific defect; source fidelity blocked. |
| ST-04 | §4.4 ¶93 | T-B04 | Present | D-16 | ALL/specific overlap constraint unresolved (QA-008). |
| ST-05 | §4.4 ¶94 | T-B05 | Present | — | No additional row-specific defect; source fidelity blocked. |
| ST-06 | §4.4 ¶95 | T-B06 | Present | — | No additional row-specific defect; source fidelity blocked. |
| ST-07 | §4.4 ¶96 | T-B07 | Present | D-13 | Manual override authority D-13 unresolved (QA-006). |
| ST-08 | §4.4 ¶97 | T-B08 | Present | — | No additional row-specific defect; source fidelity blocked. |
| AL-01 | §7.1 ¶132-136 | T-C01 | Missing | D-14 | Manager mapping D-14 pending. |
| AL-02 | ¶137 | T-C02 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| AL-03 | §7.2 ¶139-140 | T-C03 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| AL-04 | §7.3 ¶142 | T-C04 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| AL-05 | §7.4 ¶144-145 | T-C05 | Missing | — | Retry/offset lifecycle unresolved (QA-013). |
| AL-06 | §7.5 ¶147 | T-C06 | Missing | D-15 | Idempotence/visibility lifecycle unresolved (QA-013). |
| PC-01 | ¶168 | — | Missing | — | No test or acceptance criteria (QA-010). |
| PC-02 | §ثانيًا-1 ¶171-180 | T-D01 | Missing | D-18, D-19 | No additional row-specific defect; source fidelity blocked. |
| PC-03 | ¶181 | T-D01 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| PC-04 | §2 ¶183 | T-D02 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| PC-05 | ¶184 | T-D03 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| PC-06 | ¶185-186 | T-D04 | Missing | D-20 | No additional row-specific defect; source fidelity blocked. |
| PC-07 | §3أ ¶189-190 | T-D05 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| PC-08 | ¶191, ¶203 | T-D06 | Missing | D-10 | D-10 legal basis blocked. |
| PC-09 | ¶192 | T-D07 | Missing | D-09, D-11 | Pending-grievance closure gap (QA-007). |
| PC-10 | §3ب ¶194-195 | T-D08 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| PC-11 | ¶196 | T-D09, T-D10 | Missing | D-12 | Intake adapter and confidentiality gap (QA-002/009). |
| PC-12 | ¶197 | T-D11 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| PC-13 | §3 ¶199 | T-D12 | Missing | D-22 | No additional row-specific defect; source fidelity blocked. |
| PC-14 | §4-1 ¶201 | T-D13 | Missing | D-11 | Pending-grievance guard missing (QA-007). |
| PC-15 | ¶202 | T-D14 | Missing | — | Dual writable link integrity unresolved. |
| PC-16 | ¶203 | T-X03 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| PC-17 | ¶204 | T-D12 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| PC-18 | §5 ¶206 | T-E01 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| PC-19 | ¶207 | T-E02 | Missing | — | No additional row-specific defect; source fidelity blocked. |
| PC-20 | ¶208 | T-E03 | Missing | D-23 | No additional row-specific defect; source fidelity blocked. |
| PC-21 | ¶209 | T-E04 | Missing | D-14 | Manager mapping D-14 pending. |

Register completeness result: 55 stable IDs and paragraph references; 54 rows have a named or approximate test entry, PC-01 has none. PJ-20 uses a non-specific `T-X01..` range. All 27 AL/PC rows lack an explicit acceptance column. Legal and owner decision references are present where the author has identified a decision, but the original source is unavailable for independent confirmation of whether more decisions are required.
