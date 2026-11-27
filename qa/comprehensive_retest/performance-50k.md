# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 352277571426bd4d135ea4acb0e12489aa09fc3c
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 371497 ms
- host load average at start / end of the measurement: 24.98 (1 min, 8 CPUs) / 14.80 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 8 parallel clients x 5 requests each; p50 and p95 over all 40 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 1 | 248 | 452 | 699 | 699 | 815 | 1221 | MISSED |
| manager (department-wide) | case list, deep page | 200 | 1 | 286 | 461 | 623 | 623 | 861 | 1175 | MISSED |
| manager (department-wide) | case list, text filter | 200 | 2 | 69 | 209 | 317 | 317 | 394 | 582 | met |
| manager (department-wide) | case list, status + type filter | 200 | 1 | 66 | 140 | 247 | 247 | 184 | 388 | met |
| manager (department-wide) | unified search, reference | 200 | 4 | 76 | 240 | 288 | 288 | 300 | 672 | met |
| manager (department-wide) | unified search, words | 200 | 3 | 35 | 106 | 150 | 150 | 128 | 218 | met |
| manager (department-wide) | case inventory report | 200 | 4 | 382 | 814 | 1739 | 1739 | 1848 | 2560 | MISSED |
| manager (department-wide) | case inventory report, text filter | 200 | 7 | 247 | 361 | 534 | 534 | 547 | 1016 | MISSED |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 24 | 2438 | 3240 | 5364 | 5364 | - | - | MISSED |
| manager (department-wide) | indicator figures | 200 | 15 | 1073 | 2139 | 2749 | 2749 | 4344 | 5388 | MISSED |
| manager (department-wide) | indicator list TOTAL_CASES | 200 | 2 | 151 | 182 | 255 | 255 | 334 | 551 | met |
| manager (department-wide) | indicator list CASES_NEW | 200 | 2 | 67 | 82 | 181 | 181 | 185 | 250 | met |
| manager (department-wide) | indicator list CASES_ONGOING | 200 | 2 | 114 | 150 | 211 | 211 | 248 | 502 | met |
| manager (department-wide) | indicator list CASES_CONCLUDED | 200 | 2 | 86 | 112 | 321 | 321 | 198 | 276 | met |
| manager (department-wide) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 74 | 82 | 101 | 101 | 185 | 260 | met |
| manager (department-wide) | indicator list JUDGMENTS_ISSUED | 200 | 3 | 57 | 79 | 188 | 188 | 156 | 322 | met |
| manager (department-wide) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 5 | 7 | 18 | 18 | 26 | 50 | met |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 200 | 3 | 78 | 92 | 246 | 246 | 132 | 266 | met |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 200 | 3 | 54 | 67 | 176 | 176 | 161 | 295 | met |
| manager (department-wide) | indicator list APPEAL_DEADLINES | 200 | 2 | 5 | 6 | 18 | 18 | 17 | 30 | met |
| manager (department-wide) | indicator list OVERDUE_TASKS | 200 | 3 | 125 | 145 | 167 | 167 | 281 | 414 | met |
| manager (department-wide) | indicator list CASES_BY_UNIT | 200 | 2 | 127 | 151 | 376 | 376 | 358 | 472 | met |
| manager (department-wide) | indicator list CASES_BY_MEMBER | 200 | 2 | 127 | 154 | 362 | 362 | 276 | 410 | met |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 2 | 289 | 305 | 409 | 409 | 677 | 837 | met |
| manager (department-wide) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 92 | 118 | 203 | 203 | 291 | 604 | met |
| manager (department-wide) | indicator list LINKED_CASES | 200 | 2 | 141 | 170 | 343 | 343 | 384 | 608 | met |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 70 | 87 | 164 | 164 | 128 | 419 | met |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 200 | 1 | 131 | 160 | 364 | 364 | 242 | 478 | met |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 197 | 126 | 158 | 426 | 426 | 262 | 355 | met |
| manager (department-wide) | report family OPEN_CLOSED_CASES | 200 | 1 | 54 | 67 | 300 | 300 | 145 | 220 | met |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 200 | 1 | 118 | 133 | 321 | 321 | 301 | 425 | met |
| manager (department-wide) | report family CLAIM_RESULTS | 200 | 2 | 113 | 139 | 264 | 264 | 369 | 509 | met |
| manager (department-wide) | report family DEFENSE_POSITIONS | 200 | 1 | 93 | 108 | 151 | 151 | 234 | 361 | met |
| manager (department-wide) | report family LINKED_CASES | 200 | 1 | 101 | 124 | 181 | 181 | 245 | 366 | met |
| manager (department-wide) | report family ADJOURNMENTS | 200 | 1 | 38 | 46 | 58 | 58 | 90 | 142 | met |
| manager (department-wide) | report family EXPERT_SESSIONS | 200 | 1 | 96 | 106 | 145 | 145 | 158 | 576 | met |
| manager (department-wide) | report family DEADLINES_APPEALS | 200 | 2 | 4 | 6 | 44 | 44 | 14 | 45 | met |
| manager (department-wide) | report family ENFORCEMENT | 200 | 2 | 5 | 7 | 12 | 12 | 15 | 39 | met |
| manager (department-wide) | report family MISSING_EVIDENCE | 200 | 1 | 107 | 115 | 304 | 304 | 312 | 524 | met |
| manager (department-wide) | report family INFO_REQUESTS | 200 | 1 | 138 | 153 | 226 | 226 | 316 | 481 | met |
| manager (department-wide) | report family RISK_ASSESSMENT | 200 | 2 | 227 | 257 | 364 | 364 | 465 | 711 | met |
| manager (department-wide) | report family PERFORMANCE | 200 | 201 | 219 | 293 | 522 | 522 | 518 | 950 | MISSED |
| manager (department-wide) | report family LIBRARY_PRECEDENTS | 200 | 8 | 5 | 6 | 14 | 14 | 17 | 59 | met |
| member (own cases only) | case list, first page | 200 | 1 | 39 | 47 | 191 | 191 | 84 | 160 | met |
| member (own cases only) | case list, deep page | 200 | 2 | 66 | 83 | 105 | 105 | 101 | 186 | met |
| member (own cases only) | case list, text filter | 200 | 2 | 36 | 45 | 69 | 69 | 107 | 199 | met |
| member (own cases only) | case list, status + type filter | 200 | 2 | 48 | 52 | 73 | 73 | 111 | 224 | met |
| member (own cases only) | unified search, reference | 200 | 3 | 44 | 50 | 77 | 77 | 77 | 148 | met |
| member (own cases only) | unified search, words | 200 | 3 | 11 | 18 | 30 | 30 | 19 | 29 | met |
| member (own cases only) | case inventory report | 200 | 4 | 108 | 120 | 182 | 182 | 268 | 394 | met |
| member (own cases only) | case inventory report, text filter | 200 | 7 | 105 | 111 | 173 | 173 | 244 | 361 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 24 | 912 | 1026 | 1277 | 1277 | 1941 | 2393 | MISSED |
| member (own cases only) | indicator figures | 200 | 15 | 595 | 628 | 869 | 869 | 1099 | 1502 | MISSED |
| member (own cases only) | indicator list TOTAL_CASES | 200 | 2 | 66 | 78 | 111 | 111 | 127 | 200 | met |
| member (own cases only) | indicator list CASES_NEW | 200 | 2 | 51 | 67 | 109 | 109 | 77 | 124 | met |
| member (own cases only) | indicator list CASES_ONGOING | 200 | 1 | 38 | 53 | 70 | 70 | 49 | 97 | met |
| member (own cases only) | indicator list CASES_CONCLUDED | 200 | 1 | 32 | 40 | 51 | 51 | 50 | 86 | met |
| member (own cases only) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 76 | 81 | 96 | 96 | 149 | 221 | met |
| member (own cases only) | indicator list JUDGMENTS_ISSUED | 200 | 1 | 17 | 21 | 48 | 48 | 32 | 120 | met |
| member (own cases only) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 8 | 11 | 42 | 42 | 12 | 42 | met |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 200 | 3 | 73 | 87 | 121 | 121 | 133 | 214 | met |
| member (own cases only) | indicator list EXPERT_HEARINGS | 200 | 3 | 38 | 56 | 121 | 121 | 93 | 136 | met |
| member (own cases only) | indicator list APPEAL_DEADLINES | 200 | 2 | 5 | 7 | 12 | 12 | 8 | 18 | met |
| member (own cases only) | indicator list OVERDUE_TASKS | 200 | 3 | 109 | 123 | 146 | 146 | 223 | 315 | met |
| member (own cases only) | indicator list CASES_BY_UNIT | 200 | 2 | 67 | 69 | 82 | 82 | 95 | 159 | met |
| member (own cases only) | indicator list CASES_BY_MEMBER | 200 | 2 | 68 | 79 | 137 | 137 | 130 | 275 | met |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 2 | 254 | 293 | 420 | 420 | 426 | 732 | met |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 77 | 89 | 122 | 122 | 109 | 385 | met |
| member (own cases only) | indicator list LINKED_CASES | 200 | 2 | 93 | 109 | 173 | 173 | 237 | 382 | met |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 33 | 40 | 69 | 69 | 99 | 190 | met |
| member (own cases only) | report family CASES_BY_ORG_UNIT | 200 | 1 | 48 | 58 | 82 | 82 | 180 | 313 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 3 | 35 | 38 | 49 | 49 | 93 | 129 | met |
| member (own cases only) | report family OPEN_CLOSED_CASES | 200 | 1 | 34 | 37 | 50 | 50 | 98 | 171 | met |
| member (own cases only) | report family JUDGMENT_OUTCOMES | 200 | 1 | 64 | 69 | 170 | 170 | 121 | 309 | met |
| member (own cases only) | report family CLAIM_RESULTS | 200 | 2 | 43 | 48 | 77 | 77 | 77 | 153 | met |
| member (own cases only) | report family DEFENSE_POSITIONS | 200 | 1 | 47 | 56 | 86 | 86 | 160 | 279 | met |
| member (own cases only) | report family LINKED_CASES | 200 | 1 | 78 | 95 | 114 | 114 | 186 | 281 | met |
| member (own cases only) | report family ADJOURNMENTS | 200 | 1 | 40 | 45 | 95 | 95 | 97 | 202 | met |
| member (own cases only) | report family EXPERT_SESSIONS | 200 | 1 | 82 | 106 | 194 | 194 | 165 | 437 | met |
| member (own cases only) | report family DEADLINES_APPEALS | 200 | 2 | 4 | 5 | 27 | 27 | 5 | 31 | met |
| member (own cases only) | report family ENFORCEMENT | 200 | 2 | 4 | 5 | 15 | 15 | 11 | 111 | met |
| member (own cases only) | report family MISSING_EVIDENCE | 200 | 1 | 58 | 80 | 160 | 160 | 153 | 356 | met |
| member (own cases only) | report family INFO_REQUESTS | 200 | 1 | 42 | 56 | 124 | 124 | 148 | 429 | met |
| member (own cases only) | report family RISK_ASSESSMENT | 200 | 2 | 167 | 202 | 398 | 398 | 566 | 1122 | met |
| member (own cases only) | report family PERFORMANCE | 200 | 7 | 161 | 224 | 375 | 375 | 369 | 668 | met |
| member (own cases only) | report family LIBRARY_PRECEDENTS | 200 | 8 | 5 | 22 | 145 | 145 | 20 | 39 | met |
| no scope | case list, first page | 200 | 2 | 62 | 74 | 137 | 137 | 142 | 300 | met |
| no scope | case list, deep page | 200 | 2 | 65 | 101 | 424 | 424 | 174 | 534 | met |
| no scope | case list, text filter | 200 | 3 | 53 | 64 | 275 | 275 | 142 | 189 | met |
| no scope | case list, status + type filter | 200 | 2 | 47 | 51 | 124 | 124 | 84 | 162 | met |
| no scope | unified search, reference | 200 | 3 | 34 | 39 | 70 | 70 | 105 | 282 | met |
| no scope | unified search, words | 200 | 3 | 34 | 46 | 489 | 489 | 181 | 302 | met |
| no scope | case inventory report | 200 | 3 | 88 | 131 | 242 | 242 | 238 | 376 | met |
| no scope | case inventory report, text filter | 200 | 6 | 105 | 139 | 394 | 394 | 332 | 488 | met |
| no scope | dashboard (with the 16 indicator figures) | 200 | 24 | 659 | 908 | 1912 | 1912 | 1648 | 2403 | MISSED |
| no scope | indicator figures | 200 | 15 | 409 | 535 | 716 | 716 | 916 | 1416 | MISSED |
| no scope | indicator list TOTAL_CASES | 200 | 1 | 31 | 40 | 149 | 149 | 55 | 134 | met |
| no scope | indicator list CASES_NEW | 200 | 1 | 23 | 27 | 67 | 67 | 56 | 126 | met |
| no scope | indicator list CASES_ONGOING | 200 | 1 | 36 | 55 | 157 | 157 | 91 | 172 | met |
| no scope | indicator list CASES_CONCLUDED | 200 | 1 | 30 | 33 | 44 | 44 | 58 | 150 | met |
| no scope | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 30 | 41 | 107 | 107 | 55 | 82 | met |
| no scope | indicator list JUDGMENTS_ISSUED | 200 | 1 | 13 | 15 | 23 | 23 | 29 | 67 | met |
| no scope | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 4 | 6 | 45 | 45 | 10 | 49 | met |
| no scope | indicator list UPCOMING_HEARINGS | 200 | 1 | 39 | 44 | 127 | 127 | 119 | 174 | met |
| no scope | indicator list EXPERT_HEARINGS | 200 | 1 | 20 | 22 | 68 | 68 | 33 | 74 | met |
| no scope | indicator list APPEAL_DEADLINES | 200 | 2 | 4 | 9 | 27 | 27 | 8 | 15 | met |
| no scope | indicator list OVERDUE_TASKS | 200 | 1 | 47 | 67 | 92 | 92 | 88 | 150 | met |
| no scope | indicator list CASES_BY_UNIT | 200 | 1 | 30 | 39 | 86 | 86 | 59 | 180 | met |
| no scope | indicator list CASES_BY_MEMBER | 200 | 1 | 28 | 34 | 73 | 73 | 40 | 95 | met |
| no scope | indicator list HIGH_RISK_CASES | 200 | 1 | 124 | 149 | 192 | 192 | 311 | 571 | met |
| no scope | indicator list OVERDUE_INFO_REQUESTS | 200 | 1 | 30 | 33 | 94 | 94 | 76 | 102 | met |
| no scope | indicator list LINKED_CASES | 200 | 1 | 31 | 42 | 110 | 110 | 106 | 213 | met |
| no scope | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 29 | 33 | 48 | 48 | 56 | 121 | met |
| no scope | report family CASES_BY_ORG_UNIT | 200 | 1 | 29 | 34 | 133 | 133 | 54 | 77 | met |
| no scope | report family CASES_BY_MEMBER | 200 | 1 | 27 | 39 | 48 | 48 | 70 | 107 | met |
| no scope | report family OPEN_CLOSED_CASES | 200 | 1 | 30 | 32 | 114 | 114 | 67 | 107 | met |
| no scope | report family JUDGMENT_OUTCOMES | 200 | 1 | 45 | 50 | 114 | 114 | 77 | 217 | met |
| no scope | report family CLAIM_RESULTS | 200 | 2 | 31 | 36 | 47 | 47 | 66 | 135 | met |
| no scope | report family DEFENSE_POSITIONS | 200 | 1 | 30 | 36 | 56 | 56 | 52 | 118 | met |
| no scope | report family LINKED_CASES | 200 | 1 | 77 | 83 | 125 | 125 | 189 | 320 | met |
| no scope | report family ADJOURNMENTS | 200 | 1 | 38 | 45 | 82 | 82 | 88 | 168 | met |
| no scope | report family EXPERT_SESSIONS | 200 | 1 | 27 | 31 | 38 | 38 | 90 | 246 | met |
| no scope | report family DEADLINES_APPEALS | 200 | 2 | 3 | 9 | 46 | 46 | 14 | 40 | met |
| no scope | report family ENFORCEMENT | 200 | 2 | 2 | 7 | 15 | 15 | 18 | 70 | met |
| no scope | report family MISSING_EVIDENCE | 200 | 1 | 35 | 53 | 106 | 106 | 65 | 355 | met |
| no scope | report family INFO_REQUESTS | 200 | 1 | 31 | 38 | 76 | 76 | 67 | 146 | met |
| no scope | report family RISK_ASSESSMENT | 200 | 2 | 193 | 256 | 358 | 358 | 581 | 1073 | met |
| no scope | report family PERFORMANCE | 200 | 5 | 103 | 117 | 135 | 135 | 248 | 376 | met |
| no scope | report family LIBRARY_PRECEDENTS | 200 | 8 | 4 | 5 | 9 | 9 | 12 | 27 | met |
| manager with SEARCH_ALL | unified search, reference | 200 | 14 | 56 | 67 | 191 | 191 | 165 | 500 | met |
| manager with SEARCH_ALL | unified search, words | 200 | 13 | 12 | 13 | 76 | 76 | 47 | 83 | met |

Requests over the target (sequential p95): 11 of 131
