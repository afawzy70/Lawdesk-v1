# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 824ba5e (develop 1d6d2a8 application code + harness only)
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 79133 ms
- host load average at start / end of the measurement: 8.78 (1 min, 8 CPUs) / 13.06 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 8 parallel clients x 5 requests each; p50 and p95 over all 40 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 1 | 171 | 283 | 712 | 712 | 448 | 1068 | MISSED |
| manager (department-wide) | case list, deep page | 200 | 1 | 161 | 233 | 593 | 593 | 374 | 1277 | MISSED |
| manager (department-wide) | case list, text filter | 200 | 1 | 464 | 782 | 1201 | 1201 | 1173 | 2320 | MISSED |
| manager (department-wide) | case list, status + type filter | 200 | 1 | 44 | 67 | 115 | 115 | 148 | 322 | met |
| manager (department-wide) | unified search, reference | 200 | - | 21389 | 21389 | 21389 | 21389 | - | - | MISSED |
| manager (department-wide) | unified search, words | 200 | - | 22400 | 22400 | 22400 | 22400 | - | - | MISSED |
| manager (department-wide) | case inventory report | 200 | 5 | 1701 | 2235 | 3851 | 3851 | 3312 | 5123 | MISSED |
| manager (department-wide) | case inventory report, text filter | 200 | 5 | 665 | 970 | 1401 | 1401 | 1270 | 2227 | MISSED |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 26 | 1990 | 2425 | 3984 | 3984 | 6445 | 9551 | MISSED |
| manager (department-wide) | indicator figures | 200 | 17 | 1354 | 2652 | 4679 | 4679 | 3510 | 5694 | MISSED |
| manager (department-wide) | indicator list TOTAL_CASES | 200 | 2 | 164 | 230 | 1205 | 1205 | 453 | 1019 | MISSED |
| manager (department-wide) | indicator list CASES_NEW | 200 | 2 | 71 | 119 | 307 | 307 | 244 | 455 | met |
| manager (department-wide) | indicator list CASES_ONGOING | 200 | 2 | 111 | 167 | 530 | 530 | 263 | 620 | MISSED |
| manager (department-wide) | indicator list CASES_CONCLUDED | 200 | 2 | 92 | 145 | 288 | 288 | 226 | 509 | met |
| manager (department-wide) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 82 | 109 | 776 | 776 | 170 | 494 | MISSED |
| manager (department-wide) | indicator list JUDGMENTS_ISSUED | 200 | 3 | 32 | 65 | 650 | 650 | 106 | 214 | MISSED |
| manager (department-wide) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 6 | 9 | 185 | 185 | 25 | 88 | met |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 200 | 3 | 85 | 115 | 544 | 544 | 216 | 360 | MISSED |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 200 | 3 | 43 | 68 | 427 | 427 | 297 | 1897 | met |
| manager (department-wide) | indicator list APPEAL_DEADLINES | 200 | 2 | 6 | 19 | 52 | 52 | 14 | 104 | met |
| manager (department-wide) | indicator list OVERDUE_TASKS | 200 | 3 | 119 | 140 | 714 | 714 | 328 | 882 | MISSED |
| manager (department-wide) | indicator list CASES_BY_UNIT | 200 | 2 | 150 | 230 | 653 | 653 | 352 | 837 | MISSED |
| manager (department-wide) | indicator list CASES_BY_MEMBER | 200 | 2 | 126 | 215 | 697 | 697 | 430 | 979 | MISSED |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 2 | 365 | 496 | 970 | 970 | 768 | 1485 | MISSED |
| manager (department-wide) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 94 | 115 | 473 | 473 | 221 | 408 | met |
| manager (department-wide) | indicator list LINKED_CASES | 200 | 2 | 144 | 166 | 285 | 285 | 351 | 699 | met |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 61 | 72 | 569 | 569 | 102 | 389 | MISSED |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 200 | 1 | 133 | 194 | 966 | 966 | 439 | 831 | MISSED |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 197 | 112 | 161 | 848 | 848 | 300 | 544 | MISSED |
| manager (department-wide) | report family OPEN_CLOSED_CASES | 200 | 1 | 63 | 97 | 506 | 506 | 168 | 358 | MISSED |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 200 | 1 | 136 | 248 | 614 | 614 | 246 | 562 | MISSED |
| manager (department-wide) | report family CLAIM_RESULTS | 200 | 2 | 119 | 295 | 751 | 751 | 288 | 713 | MISSED |
| manager (department-wide) | report family DEFENSE_POSITIONS | 200 | 1 | 99 | 150 | 1333 | 1333 | 325 | 897 | MISSED |
| manager (department-wide) | report family LINKED_CASES | 200 | 1 | 101 | 186 | 1266 | 1266 | 225 | 336 | MISSED |
| manager (department-wide) | report family ADJOURNMENTS | 200 | 1 | 48 | 72 | 337 | 337 | 69 | 180 | met |
| manager (department-wide) | report family EXPERT_SESSIONS | 200 | 1 | 94 | 131 | 298 | 298 | 206 | 413 | met |
| manager (department-wide) | report family DEADLINES_APPEALS | 200 | 2 | 5 | 7 | 50 | 50 | 13 | 93 | met |
| manager (department-wide) | report family ENFORCEMENT | 200 | 2 | 6 | 13 | 92 | 92 | 8 | 56 | met |
| manager (department-wide) | report family MISSING_EVIDENCE | 200 | 1 | 100 | 155 | 314 | 314 | 186 | 466 | met |
| manager (department-wide) | report family INFO_REQUESTS | 200 | 1 | 162 | 261 | 559 | 559 | 268 | 658 | MISSED |
| manager (department-wide) | report family RISK_ASSESSMENT | 200 | 2 | 282 | 386 | 945 | 945 | 576 | 1011 | MISSED |
| manager (department-wide) | report family PERFORMANCE | 200 | 201 | 286 | 483 | 1014 | 1014 | 653 | 1127 | MISSED |
| manager (department-wide) | report family LIBRARY_PRECEDENTS | 200 | 8 | 9 | 30 | 126 | 126 | 20 | 79 | met |
| member (own cases only) | case list, first page | 200 | 1 | 39 | 68 | 369 | 369 | 137 | 311 | met |
| member (own cases only) | case list, deep page | 200 | 2 | 67 | 79 | 223 | 223 | 170 | 605 | met |
| member (own cases only) | case list, text filter | 200 | 1 | 452 | 543 | 658 | 658 | 987 | 1617 | MISSED |
| member (own cases only) | case list, status + type filter | 200 | 2 | 45 | 88 | 192 | 192 | 139 | 526 | met |
| member (own cases only) | unified search, reference | 200 | 3 | 221 | 291 | 794 | 794 | 479 | 1044 | MISSED |
| member (own cases only) | unified search, words | 200 | 3 | 213 | 298 | 862 | 862 | 566 | 1015 | MISSED |
| member (own cases only) | case inventory report | 200 | 5 | 180 | 242 | 1109 | 1109 | 395 | 1310 | MISSED |
| member (own cases only) | case inventory report, text filter | 200 | 5 | 715 | 984 | 1606 | 1606 | 1645 | 2581 | MISSED |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 26 | 1135 | 1834 | 3321 | 3321 | 2205 | 2906 | MISSED |
| member (own cases only) | indicator figures | 200 | 17 | 783 | 1184 | 2293 | 2293 | 2245 | 3352 | MISSED |
| member (own cases only) | indicator list TOTAL_CASES | 200 | 2 | 68 | 105 | 730 | 730 | 305 | 723 | MISSED |
| member (own cases only) | indicator list CASES_NEW | 200 | 2 | 52 | 99 | 769 | 769 | 180 | 434 | MISSED |
| member (own cases only) | indicator list CASES_ONGOING | 200 | 1 | 37 | 56 | 301 | 301 | 129 | 264 | met |
| member (own cases only) | indicator list CASES_CONCLUDED | 200 | 1 | 32 | 42 | 202 | 202 | 97 | 204 | met |
| member (own cases only) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 76 | 113 | 503 | 503 | 228 | 549 | MISSED |
| member (own cases only) | indicator list JUDGMENTS_ISSUED | 200 | 1 | 15 | 32 | 161 | 161 | 42 | 98 | met |
| member (own cases only) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 6 | 13 | 24 | 24 | 28 | 122 | met |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 200 | 3 | 126 | 180 | 461 | 461 | 395 | 828 | met |
| member (own cases only) | indicator list EXPERT_HEARINGS | 200 | 3 | 49 | 102 | 327 | 327 | 188 | 413 | met |
| member (own cases only) | indicator list APPEAL_DEADLINES | 200 | 2 | 4 | 6 | 54 | 54 | 18 | 196 | met |
| member (own cases only) | indicator list OVERDUE_TASKS | 200 | 3 | 100 | 134 | 840 | 840 | 350 | 726 | MISSED |
| member (own cases only) | indicator list CASES_BY_UNIT | 200 | 2 | 76 | 155 | 259 | 259 | 227 | 664 | met |
| member (own cases only) | indicator list CASES_BY_MEMBER | 200 | 2 | 80 | 151 | 372 | 372 | 164 | 293 | met |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 2 | 279 | 485 | 1241 | 1241 | 955 | 1301 | MISSED |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 84 | 106 | 829 | 829 | 279 | 769 | MISSED |
| member (own cases only) | indicator list LINKED_CASES | 200 | 2 | 111 | 128 | 430 | 430 | 197 | 536 | met |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 35 | 59 | 358 | 358 | 46 | 122 | met |
| member (own cases only) | report family CASES_BY_ORG_UNIT | 200 | 1 | 58 | 66 | 220 | 220 | 120 | 217 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 3 | 36 | 51 | 126 | 126 | 105 | 122 | met |
| member (own cases only) | report family OPEN_CLOSED_CASES | 200 | 1 | 39 | 43 | 72 | 72 | 70 | 164 | met |
| member (own cases only) | report family JUDGMENT_OUTCOMES | 200 | 1 | 71 | 107 | 336 | 336 | 121 | 474 | met |
| member (own cases only) | report family CLAIM_RESULTS | 200 | 2 | 45 | 56 | 98 | 98 | 81 | 226 | met |
| member (own cases only) | report family DEFENSE_POSITIONS | 200 | 1 | 49 | 75 | 290 | 290 | 106 | 367 | met |
| member (own cases only) | report family LINKED_CASES | 200 | 1 | 61 | 126 | 612 | 612 | 245 | 476 | MISSED |
| member (own cases only) | report family ADJOURNMENTS | 200 | 1 | 37 | 76 | 288 | 288 | 109 | 288 | met |
| member (own cases only) | report family EXPERT_SESSIONS | 200 | 1 | 86 | 132 | 542 | 542 | 235 | 469 | MISSED |
| member (own cases only) | report family DEADLINES_APPEALS | 200 | 2 | 4 | 5 | 7 | 7 | 12 | 253 | met |
| member (own cases only) | report family ENFORCEMENT | 200 | 2 | 4 | 4 | 4 | 4 | 9 | 61 | met |
| member (own cases only) | report family MISSING_EVIDENCE | 200 | 1 | 55 | 102 | 352 | 352 | 81 | 259 | met |
| member (own cases only) | report family INFO_REQUESTS | 200 | 1 | 44 | 53 | 136 | 136 | 94 | 297 | met |
| member (own cases only) | report family RISK_ASSESSMENT | 200 | 2 | 246 | 287 | 742 | 742 | 519 | 1047 | MISSED |
| member (own cases only) | report family PERFORMANCE | 200 | 7 | 151 | 175 | 617 | 617 | 332 | 566 | MISSED |
| member (own cases only) | report family LIBRARY_PRECEDENTS | 200 | 8 | 5 | 6 | 14 | 14 | 14 | 61 | met |
| no scope | case list, first page | 200 | 2 | 53 | 64 | 153 | 153 | 157 | 228 | met |
| no scope | case list, deep page | 200 | 2 | 53 | 64 | 233 | 233 | 179 | 814 | met |
| no scope | case list, text filter | 200 | 2 | 1107 | 1385 | 1721 | 1721 | 2113 | 2608 | MISSED |
| no scope | case list, status + type filter | 200 | 2 | 42 | 49 | 233 | 233 | 158 | 240 | met |
| no scope | unified search, reference | 200 | 3 | 34 | 63 | 245 | 245 | 86 | 290 | met |
| no scope | unified search, words | 200 | 3 | 33 | 36 | 116 | 116 | 72 | 125 | met |
| no scope | case inventory report | 200 | 4 | 107 | 125 | 184 | 184 | 256 | 465 | met |
| no scope | case inventory report, text filter | 200 | 4 | 713 | 940 | 3028 | 3028 | 1628 | 2513 | MISSED |
| no scope | dashboard (with the 16 indicator figures) | 200 | 26 | 877 | 1425 | 2817 | 2817 | 2361 | 4217 | MISSED |
| no scope | indicator figures | 200 | 17 | 554 | 793 | 1382 | 1382 | 1457 | 2051 | MISSED |
| no scope | indicator list TOTAL_CASES | 200 | 1 | 29 | 43 | 303 | 303 | 79 | 437 | met |
| no scope | indicator list CASES_NEW | 200 | 1 | 24 | 27 | 125 | 125 | 50 | 104 | met |
| no scope | indicator list CASES_ONGOING | 200 | 1 | 31 | 35 | 157 | 157 | 94 | 300 | met |
| no scope | indicator list CASES_CONCLUDED | 200 | 1 | 29 | 37 | 476 | 476 | 111 | 438 | met |
| no scope | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 32 | 47 | 127 | 127 | 107 | 531 | met |
| no scope | indicator list JUDGMENTS_ISSUED | 200 | 1 | 13 | 15 | 23 | 23 | 35 | 182 | met |
| no scope | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 4 | 6 | 28 | 28 | 14 | 218 | met |
| no scope | indicator list UPCOMING_HEARINGS | 200 | 1 | 43 | 54 | 108 | 108 | 129 | 427 | met |
| no scope | indicator list EXPERT_HEARINGS | 200 | 1 | 18 | 20 | 108 | 108 | 48 | 201 | met |
| no scope | indicator list APPEAL_DEADLINES | 200 | 2 | 4 | 4 | 16 | 16 | 12 | 40 | met |
| no scope | indicator list OVERDUE_TASKS | 200 | 1 | 47 | 91 | 159 | 159 | 135 | 263 | met |
| no scope | indicator list CASES_BY_UNIT | 200 | 1 | 29 | 35 | 330 | 330 | 96 | 258 | met |
| no scope | indicator list CASES_BY_MEMBER | 200 | 1 | 34 | 51 | 257 | 257 | 88 | 293 | met |
| no scope | indicator list HIGH_RISK_CASES | 200 | 1 | 253 | 361 | 789 | 789 | 349 | 977 | MISSED |
| no scope | indicator list OVERDUE_INFO_REQUESTS | 200 | 1 | 26 | 34 | 110 | 110 | 89 | 218 | met |
| no scope | indicator list LINKED_CASES | 200 | 1 | 32 | 38 | 250 | 250 | 89 | 324 | met |
| no scope | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 33 | 41 | 94 | 94 | 96 | 771 | met |
| no scope | report family CASES_BY_ORG_UNIT | 200 | 1 | 32 | 36 | 67 | 67 | 249 | 768 | met |
| no scope | report family CASES_BY_MEMBER | 200 | 1 | 31 | 57 | 135 | 135 | 144 | 488 | met |
| no scope | report family OPEN_CLOSED_CASES | 200 | 1 | 29 | 63 | 148 | 148 | 156 | 495 | met |
| no scope | report family JUDGMENT_OUTCOMES | 200 | 1 | 42 | 64 | 257 | 257 | 129 | 233 | met |
| no scope | report family CLAIM_RESULTS | 200 | 2 | 31 | 59 | 139 | 139 | 109 | 392 | met |
| no scope | report family DEFENSE_POSITIONS | 200 | 1 | 30 | 55 | 717 | 717 | 116 | 275 | MISSED |
| no scope | report family LINKED_CASES | 200 | 1 | 99 | 133 | 229 | 229 | 237 | 350 | met |
| no scope | report family ADJOURNMENTS | 200 | 1 | 46 | 112 | 390 | 390 | 156 | 357 | met |
| no scope | report family EXPERT_SESSIONS | 200 | 1 | 30 | 58 | 189 | 189 | 69 | 535 | met |
| no scope | report family DEADLINES_APPEALS | 200 | 2 | 4 | 11 | 143 | 143 | 12 | 378 | met |
| no scope | report family ENFORCEMENT | 200 | 2 | 3 | 3 | 7 | 7 | 12 | 36 | met |
| no scope | report family MISSING_EVIDENCE | 200 | 1 | 36 | 51 | 96 | 96 | 93 | 126 | met |
| no scope | report family INFO_REQUESTS | 200 | 1 | 29 | 44 | 116 | 116 | 115 | 357 | met |
| no scope | report family RISK_ASSESSMENT | 200 | 2 | 272 | 482 | 1862 | 1862 | 622 | 1263 | MISSED |
| no scope | report family PERFORMANCE | 200 | 5 | 109 | 150 | 687 | 687 | 240 | 755 | MISSED |
| no scope | report family LIBRARY_PRECEDENTS | 200 | 8 | 4 | 7 | 28 | 28 | 7 | 20 | met |
| manager with SEARCH_ALL | unified search, reference | 200 | - | 46706 | 46706 | 46706 | 46706 | - | - | MISSED |
| manager with SEARCH_ALL | unified search, words | 200 | - | 53834 | 53834 | 53834 | 53834 | - | - | MISSED |

Requests over the target (sequential p95): 56 of 131
