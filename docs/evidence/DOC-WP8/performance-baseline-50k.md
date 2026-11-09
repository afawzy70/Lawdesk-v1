# WP8 performance run

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 59457 ms
- each request: one warm-up (a request slower than 6000 ms is reported from that single run), then 3 timed runs; the table shows min / median / max in ms
- target: 500 ms (list and search)

| user | request | HTTP | min | median | max | target |
|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 2003 | 2038 | 2329 | MISSED |
| manager (department-wide) | case list, deep page | 200 | 1979 | 2237 | 2301 | MISSED |
| manager (department-wide) | case list, text filter | 200 | 13213 | 13213 | 13213 | MISSED |
| manager (department-wide) | case list, status + type filter | 200 | 267 | 273 | 275 | met |
| manager (department-wide) | unified search, reference | 200 | 39054 | 39054 | 39054 | MISSED |
| manager (department-wide) | unified search, words | 200 | 19153 | 19153 | 19153 | MISSED |
| manager (department-wide) | case inventory report | 200 | 6625 | 6625 | 6625 | MISSED |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 16344 | 16344 | 16344 | MISSED |
| manager (department-wide) | indicator figures | 200 | 12749 | 12749 | 12749 | MISSED |
| manager (department-wide) | indicator list TOTAL_CASES | 200 | 2069 | 2531 | 2775 | MISSED |
| manager (department-wide) | indicator list CASES_NEW | 200 | 561 | 595 | 771 | MISSED |
| manager (department-wide) | indicator list CASES_ONGOING | 200 | 1214 | 1284 | 1591 | MISSED |
| manager (department-wide) | indicator list CASES_CONCLUDED | 200 | 767 | 778 | 858 | MISSED |
| manager (department-wide) | indicator list RESERVED_FOR_JUDGMENT | 200 | 131 | 143 | 175 | met |
| manager (department-wide) | indicator list JUDGMENTS_ISSUED | 200 | 95 | 130 | 134 | met |
| manager (department-wide) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 41 | 58 | 79 | met |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 200 | 312 | 402 | 408 | met |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 200 | 139 | 209 | 275 | met |
| manager (department-wide) | indicator list APPEAL_DEADLINES | 200 | 48 | 58 | 63 | met |
| manager (department-wide) | indicator list OVERDUE_TASKS | 200 | 724 | 799 | 957 | MISSED |
| manager (department-wide) | indicator list CASES_BY_UNIT | 200 | 2424 | 2654 | 3729 | MISSED |
| manager (department-wide) | indicator list CASES_BY_MEMBER | 200 | 2272 | 2641 | 2960 | MISSED |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 925 | 962 | 1044 | MISSED |
| manager (department-wide) | indicator list OVERDUE_INFO_REQUESTS | 200 | 266 | 280 | 295 | met |
| manager (department-wide) | indicator list LINKED_CASES | 200 | 3545 | 4492 | 4596 | MISSED |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1135 | 1145 | 1160 | MISSED |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 200 | 1146 | 1221 | 1440 | MISSED |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 1549 | 1573 | 1851 | MISSED |
| manager (department-wide) | report family OPEN_CLOSED_CASES | 200 | 1219 | 1406 | 1574 | MISSED |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 200 | 1185 | 1338 | 1453 | MISSED |
| manager (department-wide) | report family CLAIM_RESULTS | 200 | 1226 | 1250 | 1293 | MISSED |
| manager (department-wide) | report family DEFENSE_POSITIONS | 200 | 1164 | 1338 | 1353 | MISSED |
| manager (department-wide) | report family LINKED_CASES | 200 | 1946 | 2232 | 2426 | MISSED |
| manager (department-wide) | report family ADJOURNMENTS | 200 | 66 | 67 | 67 | met |
| manager (department-wide) | report family EXPERT_SESSIONS | 200 | 203 | 207 | 223 | met |
| manager (department-wide) | report family DEADLINES_APPEALS | 200 | 16 | 17 | 50 | met |
| manager (department-wide) | report family ENFORCEMENT | 200 | 22 | 37 | 59 | met |
| manager (department-wide) | report family MISSING_EVIDENCE | 200 | 537 | 543 | 603 | MISSED |
| manager (department-wide) | report family INFO_REQUESTS | 200 | 613 | 650 | 671 | MISSED |
| manager (department-wide) | report family RISK_ASSESSMENT | 200 | 1459 | 1565 | 1616 | MISSED |
| manager (department-wide) | report family PERFORMANCE | 200 | 2501 | 2521 | 2662 | MISSED |
| manager (department-wide) | report family LIBRARY_PRECEDENTS | 200 | 24 | 26 | 30 | met |
| scoped to one case type | case list, first page | 200 | 4689 | 4732 | 4735 | MISSED |
| scoped to one case type | case list, deep page | 200 | 4682 | 4899 | 5007 | MISSED |
| scoped to one case type | case list, text filter | 200 | 6535 | 6535 | 6535 | MISSED |
| scoped to one case type | case list, status + type filter | 200 | 204 | 206 | 228 | met |
| scoped to one case type | unified search, reference | 200 | 4445 | 4484 | 4653 | MISSED |
| scoped to one case type | unified search, words | 200 | 4300 | 4492 | 4630 | MISSED |
| scoped to one case type | case inventory report | 200 | 7767 | 7767 | 7767 | MISSED |
| scoped to one case type | dashboard (with the 16 indicator figures) | 200 | 32304 | 32304 | 32304 | MISSED |
| scoped to one case type | indicator figures | 200 | 25069 | 25069 | 25069 | MISSED |
| scoped to one case type | indicator list TOTAL_CASES | 200 | 4416 | 4675 | 4748 | MISSED |
| scoped to one case type | indicator list CASES_NEW | 200 | 940 | 983 | 1096 | MISSED |
| scoped to one case type | indicator list CASES_ONGOING | 200 | 2282 | 2378 | 2410 | MISSED |
| scoped to one case type | indicator list CASES_CONCLUDED | 200 | 1527 | 1574 | 1580 | MISSED |
| scoped to one case type | indicator list RESERVED_FOR_JUDGMENT | 200 | 98 | 115 | 121 | met |
| scoped to one case type | indicator list JUDGMENTS_ISSUED | 200 | 72 | 73 | 82 | met |
| scoped to one case type | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 13 | 14 | 16 | met |
| scoped to one case type | indicator list UPCOMING_HEARINGS | 200 | 681 | 686 | 802 | MISSED |
| scoped to one case type | indicator list EXPERT_HEARINGS | 200 | 110 | 127 | 128 | met |
| scoped to one case type | indicator list APPEAL_DEADLINES | 200 | 10 | 13 | 16 | met |
| scoped to one case type | indicator list OVERDUE_TASKS | 200 | 1518 | 1525 | 1695 | MISSED |
| scoped to one case type | indicator list CASES_BY_UNIT | 200 | 4881 | 4883 | 4929 | MISSED |
| scoped to one case type | indicator list CASES_BY_MEMBER | 200 | 4548 | 4610 | 4747 | MISSED |
| scoped to one case type | indicator list HIGH_RISK_CASES | 200 | 1309 | 1315 | 1346 | MISSED |
| scoped to one case type | indicator list OVERDUE_INFO_REQUESTS | 200 | 419 | 422 | 438 | met |
| scoped to one case type | indicator list LINKED_CASES | 200 | 6541 | 6541 | 6541 | MISSED |
| scoped to one case type | report family CASES_BY_TYPE_COURT_STAGE | 200 | 2341 | 2563 | 2581 | MISSED |
| scoped to one case type | report family CASES_BY_ORG_UNIT | 200 | 2510 | 2623 | 3056 | MISSED |
| scoped to one case type | report family CASES_BY_MEMBER | 200 | 2548 | 2567 | 2779 | MISSED |
| scoped to one case type | report family OPEN_CLOSED_CASES | 200 | 2386 | 2513 | 2850 | MISSED |
| scoped to one case type | report family JUDGMENT_OUTCOMES | 200 | 2471 | 2519 | 2557 | MISSED |
| scoped to one case type | report family CLAIM_RESULTS | 200 | 2390 | 2503 | 2561 | MISSED |
| scoped to one case type | report family DEFENSE_POSITIONS | 200 | 2542 | 2618 | 2928 | MISSED |
| scoped to one case type | report family LINKED_CASES | 200 | 4714 | 4906 | 4942 | MISSED |
| scoped to one case type | report family ADJOURNMENTS | 200 | 46 | 50 | 65 | met |
| scoped to one case type | report family EXPERT_SESSIONS | 200 | 343 | 353 | 404 | met |
| scoped to one case type | report family DEADLINES_APPEALS | 200 | 13 | 16 | 16 | met |
| scoped to one case type | report family ENFORCEMENT | 200 | 22 | 28 | 40 | met |
| scoped to one case type | report family MISSING_EVIDENCE | 200 | 857 | 1071 | 1114 | MISSED |
| scoped to one case type | report family INFO_REQUESTS | 200 | 1086 | 1192 | 1298 | MISSED |
| scoped to one case type | report family RISK_ASSESSMENT | 200 | 3127 | 3133 | 3300 | MISSED |
| scoped to one case type | report family PERFORMANCE | 200 | 5439 | 5458 | 5592 | MISSED |
| scoped to one case type | report family LIBRARY_PRECEDENTS | 200 | 19 | 22 | 26 | met |
| member (own cases only) | case list, first page | 200 | 4965 | 4993 | 5232 | MISSED |
| member (own cases only) | case list, deep page | 200 | 4745 | 5145 | 5446 | MISSED |
| member (own cases only) | case list, text filter | 200 | 5743 | 5772 | 5985 | MISSED |
| member (own cases only) | case list, status + type filter | 200 | 470 | 473 | 481 | met |
| member (own cases only) | unified search, reference | 200 | 2859 | 3112 | 3208 | MISSED |
| member (own cases only) | unified search, words | 200 | 2910 | 2945 | 2990 | MISSED |
| member (own cases only) | case inventory report | 200 | 7537 | 7537 | 7537 | MISSED |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 35253 | 35253 | 35253 | MISSED |
| member (own cases only) | indicator figures | 200 | 26124 | 26124 | 26124 | MISSED |
| member (own cases only) | indicator list TOTAL_CASES | 200 | 5048 | 5051 | 5055 | MISSED |
| member (own cases only) | indicator list CASES_NEW | 200 | 1229 | 1252 | 1531 | MISSED |
| member (own cases only) | indicator list CASES_ONGOING | 200 | 1281 | 1732 | 1986 | MISSED |
| member (own cases only) | indicator list CASES_CONCLUDED | 200 | 755 | 845 | 1028 | MISSED |
| member (own cases only) | indicator list RESERVED_FOR_JUDGMENT | 200 | 121 | 134 | 134 | met |
| member (own cases only) | indicator list JUDGMENTS_ISSUED | 200 | 54 | 80 | 117 | met |
| member (own cases only) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 23 | 27 | 99 | met |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 200 | 656 | 733 | 733 | MISSED |
| member (own cases only) | indicator list EXPERT_HEARINGS | 200 | 131 | 146 | 147 | met |
| member (own cases only) | indicator list APPEAL_DEADLINES | 200 | 12 | 14 | 14 | met |
| member (own cases only) | indicator list OVERDUE_TASKS | 200 | 1633 | 1851 | 2219 | MISSED |
| member (own cases only) | indicator list CASES_BY_UNIT | 200 | 4729 | 4732 | 5471 | MISSED |
| member (own cases only) | indicator list CASES_BY_MEMBER | 200 | 4970 | 5028 | 5446 | MISSED |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 1723 | 1781 | 1784 | MISSED |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 200 | 491 | 503 | 515 | MISSED |
| member (own cases only) | indicator list LINKED_CASES | 200 | 4873 | 4983 | 5195 | MISSED |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 2364 | 2481 | 2652 | MISSED |
| member (own cases only) | report family CASES_BY_ORG_UNIT | 200 | 2340 | 2442 | 2495 | MISSED |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 2379 | 2458 | 2519 | MISSED |
| member (own cases only) | report family OPEN_CLOSED_CASES | 200 | 2338 | 2501 | 2561 | MISSED |
| member (own cases only) | report family JUDGMENT_OUTCOMES | 200 | 2440 | 2480 | 2506 | MISSED |
| member (own cases only) | report family CLAIM_RESULTS | 200 | 2347 | 2507 | 2527 | MISSED |
| member (own cases only) | report family DEFENSE_POSITIONS | 200 | 2435 | 2444 | 2604 | MISSED |
| member (own cases only) | report family LINKED_CASES | 200 | 5126 | 5168 | 5172 | MISSED |
| member (own cases only) | report family ADJOURNMENTS | 200 | 46 | 48 | 67 | met |
| member (own cases only) | report family EXPERT_SESSIONS | 200 | 335 | 412 | 460 | met |
| member (own cases only) | report family DEADLINES_APPEALS | 200 | 18 | 22 | 65 | met |
| member (own cases only) | report family ENFORCEMENT | 200 | 8 | 18 | 21 | met |
| member (own cases only) | report family MISSING_EVIDENCE | 200 | 907 | 920 | 951 | MISSED |
| member (own cases only) | report family INFO_REQUESTS | 200 | 1158 | 1171 | 1246 | MISSED |
| member (own cases only) | report family RISK_ASSESSMENT | 200 | 3079 | 3330 | 3559 | MISSED |
| member (own cases only) | report family PERFORMANCE | 200 | 5177 | 5384 | 5857 | MISSED |
| member (own cases only) | report family LIBRARY_PRECEDENTS | 200 | 13 | 19 | 46 | met |

Requests over the target (median): 90 of 126
