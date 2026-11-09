# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 1374f54
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 281866 ms
- host load average at start / end of the measurement: 3.02 (1 min, 8 CPUs) / 6.22 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 8 parallel clients x 5 requests each; p50 and p95 over all 40 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 1 | 152 | 192 | 734 | 734 | 488 | 1190 | MISSED |
| manager (department-wide) | case list, deep page | 200 | 1 | 157 | 194 | 367 | 367 | 211 | 491 | met |
| manager (department-wide) | case list, text filter | 200 | 2 | 69 | 131 | 438 | 438 | 80 | 350 | met |
| manager (department-wide) | case list, status + type filter | 200 | 1 | 41 | 82 | 681 | 681 | 71 | 133 | MISSED |
| manager (department-wide) | unified search, reference | 200 | 4 | 592 | 764 | 946 | 946 | 995 | 1744 | MISSED |
| manager (department-wide) | unified search, words | 200 | 3 | 177 | 234 | 741 | 741 | 225 | 444 | MISSED |
| manager (department-wide) | case inventory report | 200 | 4 | 376 | 579 | 942 | 942 | 608 | 1191 | MISSED |
| manager (department-wide) | case inventory report, text filter | 200 | 4 | 107 | 172 | 320 | 320 | 186 | 780 | met |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 24 | 1566 | 2058 | 2761 | 2761 | 2503 | 3593 | MISSED |
| manager (department-wide) | indicator figures | 200 | 15 | 899 | 1085 | 1442 | 1442 | 1473 | 2437 | MISSED |
| manager (department-wide) | indicator list TOTAL_CASES | 200 | 2 | 144 | 172 | 244 | 244 | 239 | 357 | met |
| manager (department-wide) | indicator list CASES_NEW | 200 | 2 | 65 | 101 | 183 | 183 | 137 | 245 | met |
| manager (department-wide) | indicator list CASES_ONGOING | 200 | 2 | 102 | 256 | 384 | 384 | 731 | 2268 | met |
| manager (department-wide) | indicator list CASES_CONCLUDED | 200 | 2 | 83 | 101 | 288 | 288 | 216 | 521 | met |
| manager (department-wide) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 80 | 105 | 335 | 335 | 185 | 394 | met |
| manager (department-wide) | indicator list JUDGMENTS_ISSUED | 200 | 3 | 30 | 89 | 489 | 489 | 82 | 279 | met |
| manager (department-wide) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 11 | 18 | 62 | 62 | 12 | 216 | met |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 200 | 3 | 84 | 116 | 206 | 206 | 107 | 244 | met |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 200 | 3 | 59 | 75 | 218 | 218 | 80 | 173 | met |
| manager (department-wide) | indicator list APPEAL_DEADLINES | 200 | 2 | 7 | 24 | 151 | 151 | 10 | 107 | met |
| manager (department-wide) | indicator list OVERDUE_TASKS | 200 | 3 | 134 | 172 | 266 | 266 | 225 | 404 | met |
| manager (department-wide) | indicator list CASES_BY_UNIT | 200 | 2 | 132 | 241 | 563 | 563 | 234 | 551 | MISSED |
| manager (department-wide) | indicator list CASES_BY_MEMBER | 200 | 2 | 147 | 222 | 425 | 425 | 351 | 702 | met |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 2 | 286 | 372 | 829 | 829 | 527 | 1223 | MISSED |
| manager (department-wide) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 97 | 150 | 283 | 283 | 146 | 336 | met |
| manager (department-wide) | indicator list LINKED_CASES | 200 | 2 | 138 | 209 | 500 | 500 | 273 | 598 | met |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 73 | 99 | 275 | 275 | 124 | 463 | met |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 200 | 1 | 136 | 216 | 436 | 436 | 208 | 465 | met |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 197 | 138 | 322 | 619 | 619 | 289 | 561 | MISSED |
| manager (department-wide) | report family OPEN_CLOSED_CASES | 200 | 1 | 60 | 78 | 376 | 376 | 159 | 632 | met |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 200 | 1 | 135 | 225 | 370 | 370 | 217 | 518 | met |
| manager (department-wide) | report family CLAIM_RESULTS | 200 | 2 | 115 | 173 | 252 | 252 | 244 | 416 | met |
| manager (department-wide) | report family DEFENSE_POSITIONS | 200 | 1 | 101 | 127 | 286 | 286 | 271 | 785 | met |
| manager (department-wide) | report family LINKED_CASES | 200 | 1 | 92 | 129 | 486 | 486 | 261 | 403 | met |
| manager (department-wide) | report family ADJOURNMENTS | 200 | 1 | 46 | 52 | 99 | 99 | 110 | 251 | met |
| manager (department-wide) | report family EXPERT_SESSIONS | 200 | 1 | 92 | 109 | 502 | 502 | 261 | 582 | MISSED |
| manager (department-wide) | report family DEADLINES_APPEALS | 200 | 2 | 6 | 7 | 14 | 14 | 18 | 100 | met |
| manager (department-wide) | report family ENFORCEMENT | 200 | 2 | 5 | 6 | 126 | 126 | 12 | 202 | met |
| manager (department-wide) | report family MISSING_EVIDENCE | 200 | 1 | 102 | 146 | 565 | 565 | 232 | 431 | MISSED |
| manager (department-wide) | report family INFO_REQUESTS | 200 | 1 | 159 | 210 | 506 | 506 | 303 | 549 | MISSED |
| manager (department-wide) | report family RISK_ASSESSMENT | 200 | 2 | 229 | 317 | 1056 | 1056 | 529 | 1020 | MISSED |
| manager (department-wide) | report family PERFORMANCE | 200 | 201 | 227 | 322 | 581 | 581 | 575 | 826 | MISSED |
| manager (department-wide) | report family LIBRARY_PRECEDENTS | 200 | 8 | 6 | 10 | 44 | 44 | 15 | 159 | met |
| member (own cases only) | case list, first page | 200 | 1 | 37 | 47 | 324 | 324 | 86 | 365 | met |
| member (own cases only) | case list, deep page | 200 | 2 | 65 | 75 | 147 | 147 | 131 | 263 | met |
| member (own cases only) | case list, text filter | 200 | 2 | 38 | 69 | 160 | 160 | 64 | 161 | met |
| member (own cases only) | case list, status + type filter | 200 | 2 | 49 | 51 | 481 | 481 | 122 | 260 | met |
| member (own cases only) | unified search, reference | 200 | 3 | 84 | 112 | 206 | 206 | 166 | 544 | met |
| member (own cases only) | unified search, words | 200 | 3 | 49 | 53 | 180 | 180 | 89 | 243 | met |
| member (own cases only) | case inventory report | 200 | 4 | 112 | 124 | 695 | 695 | 280 | 506 | MISSED |
| member (own cases only) | case inventory report, text filter | 200 | 4 | 60 | 72 | 125 | 125 | 167 | 576 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 24 | 1033 | 1284 | 1657 | 1657 | 1291 | 2081 | MISSED |
| member (own cases only) | indicator figures | 200 | 15 | 720 | 915 | 1352 | 1352 | 919 | 1261 | MISSED |
| member (own cases only) | indicator list TOTAL_CASES | 200 | 2 | 82 | 98 | 249 | 249 | 86 | 224 | met |
| member (own cases only) | indicator list CASES_NEW | 200 | 2 | 58 | 84 | 107 | 107 | 81 | 285 | met |
| member (own cases only) | indicator list CASES_ONGOING | 200 | 1 | 34 | 54 | 894 | 894 | 40 | 231 | MISSED |
| member (own cases only) | indicator list CASES_CONCLUDED | 200 | 1 | 37 | 47 | 60 | 60 | 38 | 95 | met |
| member (own cases only) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 79 | 99 | 278 | 278 | 97 | 766 | met |
| member (own cases only) | indicator list JUDGMENTS_ISSUED | 200 | 1 | 18 | 24 | 62 | 62 | 19 | 41 | met |
| member (own cases only) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 5 | 10 | 40 | 40 | 6 | 28 | met |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 200 | 3 | 85 | 109 | 172 | 172 | 101 | 247 | met |
| member (own cases only) | indicator list EXPERT_HEARINGS | 200 | 3 | 51 | 75 | 128 | 128 | 53 | 94 | met |
| member (own cases only) | indicator list APPEAL_DEADLINES | 200 | 2 | 6 | 14 | 38 | 38 | 12 | 35 | met |
| member (own cases only) | indicator list OVERDUE_TASKS | 200 | 3 | 113 | 141 | 270 | 270 | 150 | 285 | met |
| member (own cases only) | indicator list CASES_BY_UNIT | 200 | 2 | 75 | 91 | 154 | 154 | 89 | 151 | met |
| member (own cases only) | indicator list CASES_BY_MEMBER | 200 | 2 | 81 | 100 | 126 | 126 | 91 | 263 | met |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 2 | 292 | 363 | 771 | 771 | 342 | 728 | MISSED |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 89 | 113 | 274 | 274 | 91 | 181 | met |
| member (own cases only) | indicator list LINKED_CASES | 200 | 2 | 106 | 135 | 212 | 212 | 115 | 202 | met |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 34 | 47 | 92 | 92 | 44 | 118 | met |
| member (own cases only) | report family CASES_BY_ORG_UNIT | 200 | 1 | 59 | 73 | 88 | 88 | 65 | 162 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 3 | 50 | 64 | 154 | 154 | 44 | 130 | met |
| member (own cases only) | report family OPEN_CLOSED_CASES | 200 | 1 | 41 | 52 | 108 | 108 | 51 | 209 | met |
| member (own cases only) | report family JUDGMENT_OUTCOMES | 200 | 1 | 62 | 87 | 215 | 215 | 89 | 281 | met |
| member (own cases only) | report family CLAIM_RESULTS | 200 | 2 | 51 | 76 | 158 | 158 | 58 | 138 | met |
| member (own cases only) | report family DEFENSE_POSITIONS | 200 | 1 | 50 | 60 | 104 | 104 | 60 | 121 | met |
| member (own cases only) | report family LINKED_CASES | 200 | 1 | 72 | 81 | 253 | 253 | 155 | 233 | met |
| member (own cases only) | report family ADJOURNMENTS | 200 | 1 | 41 | 53 | 75 | 75 | 76 | 177 | met |
| member (own cases only) | report family EXPERT_SESSIONS | 200 | 1 | 84 | 110 | 174 | 174 | 145 | 318 | met |
| member (own cases only) | report family DEADLINES_APPEALS | 200 | 2 | 4 | 5 | 13 | 13 | 12 | 114 | met |
| member (own cases only) | report family ENFORCEMENT | 200 | 2 | 7 | 14 | 183 | 183 | 10 | 28 | met |
| member (own cases only) | report family MISSING_EVIDENCE | 200 | 1 | 57 | 72 | 140 | 140 | 69 | 211 | met |
| member (own cases only) | report family INFO_REQUESTS | 200 | 1 | 44 | 55 | 72 | 72 | 54 | 105 | met |
| member (own cases only) | report family RISK_ASSESSMENT | 200 | 2 | 215 | 264 | 449 | 449 | 350 | 596 | met |
| member (own cases only) | report family PERFORMANCE | 200 | 7 | 162 | 191 | 246 | 246 | 269 | 356 | met |
| member (own cases only) | report family LIBRARY_PRECEDENTS | 200 | 8 | 5 | 10 | 156 | 156 | 12 | 38 | met |
| no scope | case list, first page | 200 | 2 | 63 | 70 | 127 | 127 | 72 | 169 | met |
| no scope | case list, deep page | 200 | 2 | 58 | 84 | 154 | 154 | 66 | 243 | met |
| no scope | case list, text filter | 200 | 3 | 61 | 89 | 160 | 160 | 140 | 322 | met |
| no scope | case list, status + type filter | 200 | 2 | 48 | 70 | 424 | 424 | 60 | 173 | met |
| no scope | unified search, reference | 200 | 3 | 54 | 104 | 303 | 303 | 65 | 155 | met |
| no scope | unified search, words | 200 | 3 | 49 | 62 | 344 | 344 | 70 | 168 | met |
| no scope | case inventory report | 200 | 3 | 88 | 128 | 289 | 289 | 217 | 717 | met |
| no scope | case inventory report, text filter | 200 | 3 | 60 | 72 | 390 | 390 | 79 | 234 | met |
| no scope | dashboard (with the 16 indicator figures) | 200 | 24 | 738 | 1007 | 1970 | 1970 | 944 | 1391 | MISSED |
| no scope | indicator figures | 200 | 15 | 530 | 585 | 956 | 956 | 687 | 1195 | MISSED |
| no scope | indicator list TOTAL_CASES | 200 | 1 | 56 | 76 | 138 | 138 | 47 | 176 | met |
| no scope | indicator list CASES_NEW | 200 | 1 | 26 | 35 | 50 | 50 | 27 | 52 | met |
| no scope | indicator list CASES_ONGOING | 200 | 1 | 29 | 55 | 179 | 179 | 50 | 188 | met |
| no scope | indicator list CASES_CONCLUDED | 200 | 1 | 31 | 48 | 154 | 154 | 46 | 206 | met |
| no scope | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 33 | 42 | 158 | 158 | 59 | 162 | met |
| no scope | indicator list JUDGMENTS_ISSUED | 200 | 1 | 16 | 25 | 55 | 55 | 18 | 203 | met |
| no scope | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 8 | 30 | 92 | 92 | 5 | 122 | met |
| no scope | indicator list UPCOMING_HEARINGS | 200 | 1 | 48 | 56 | 172 | 172 | 57 | 237 | met |
| no scope | indicator list EXPERT_HEARINGS | 200 | 1 | 26 | 46 | 218 | 218 | 22 | 56 | met |
| no scope | indicator list APPEAL_DEADLINES | 200 | 2 | 3 | 4 | 6 | 6 | 7 | 57 | met |
| no scope | indicator list OVERDUE_TASKS | 200 | 1 | 56 | 102 | 235 | 235 | 62 | 147 | met |
| no scope | indicator list CASES_BY_UNIT | 200 | 1 | 33 | 48 | 118 | 118 | 49 | 219 | met |
| no scope | indicator list CASES_BY_MEMBER | 200 | 1 | 34 | 50 | 380 | 380 | 41 | 149 | met |
| no scope | indicator list HIGH_RISK_CASES | 200 | 1 | 133 | 163 | 288 | 288 | 201 | 370 | met |
| no scope | indicator list OVERDUE_INFO_REQUESTS | 200 | 1 | 37 | 45 | 60 | 60 | 38 | 74 | met |
| no scope | indicator list LINKED_CASES | 200 | 1 | 32 | 44 | 66 | 66 | 37 | 274 | met |
| no scope | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 31 | 46 | 212 | 212 | 41 | 276 | met |
| no scope | report family CASES_BY_ORG_UNIT | 200 | 1 | 29 | 52 | 153 | 153 | 39 | 90 | met |
| no scope | report family CASES_BY_MEMBER | 200 | 1 | 34 | 58 | 167 | 167 | 51 | 154 | met |
| no scope | report family OPEN_CLOSED_CASES | 200 | 1 | 32 | 46 | 102 | 102 | 33 | 72 | met |
| no scope | report family JUDGMENT_OUTCOMES | 200 | 1 | 48 | 74 | 160 | 160 | 56 | 133 | met |
| no scope | report family CLAIM_RESULTS | 200 | 2 | 34 | 65 | 369 | 369 | 41 | 84 | met |
| no scope | report family DEFENSE_POSITIONS | 200 | 1 | 35 | 42 | 103 | 103 | 34 | 102 | met |
| no scope | report family LINKED_CASES | 200 | 1 | 97 | 119 | 287 | 287 | 94 | 302 | met |
| no scope | report family ADJOURNMENTS | 200 | 1 | 41 | 65 | 136 | 136 | 70 | 214 | met |
| no scope | report family EXPERT_SESSIONS | 200 | 1 | 34 | 51 | 87 | 87 | 52 | 140 | met |
| no scope | report family DEADLINES_APPEALS | 200 | 2 | 8 | 14 | 27 | 27 | 7 | 40 | met |
| no scope | report family ENFORCEMENT | 200 | 2 | 4 | 8 | 131 | 131 | 4 | 10 | met |
| no scope | report family MISSING_EVIDENCE | 200 | 1 | 34 | 45 | 66 | 66 | 47 | 149 | met |
| no scope | report family INFO_REQUESTS | 200 | 1 | 44 | 72 | 221 | 221 | 38 | 136 | met |
| no scope | report family RISK_ASSESSMENT | 200 | 2 | 195 | 289 | 404 | 404 | 359 | 710 | met |
| no scope | report family PERFORMANCE | 200 | 5 | 108 | 148 | 310 | 310 | 155 | 406 | met |
| no scope | report family LIBRARY_PRECEDENTS | 200 | 8 | 9 | 13 | 21 | 21 | 6 | 12 | met |
| manager with SEARCH_ALL | unified search, reference | 200 | 14 | 567 | 670 | 885 | 885 | 758 | 1194 | MISSED |
| manager with SEARCH_ALL | unified search, words | 200 | 13 | 166 | 214 | 427 | 427 | 221 | 553 | met |

Requests over the target (sequential p95): 23 of 131
