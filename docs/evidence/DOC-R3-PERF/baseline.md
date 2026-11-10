# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 3522775
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 15 ms
- host load average at start / end of the measurement: 18.13 (1 min, 8 CPUs) / 13.92 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 8 parallel clients x 5 requests each; p50 and p95 over all 40 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 1 | 126 | 257 | 516 | 516 | 350 | 618 | MISSED |
| manager (department-wide) | case list, deep page | 200 | 1 | 142 | 203 | 362 | 362 | 361 | 780 | met |
| manager (department-wide) | case list, text filter | 200 | 2 | 59 | 89 | 190 | 190 | 119 | 289 | met |
| manager (department-wide) | case list, status + type filter | 200 | 1 | 44 | 71 | 206 | 206 | 148 | 414 | met |
| manager (department-wide) | unified search, reference | 200 | 4 | 77 | 105 | 253 | 253 | 140 | 198 | met |
| manager (department-wide) | unified search, words | 200 | 3 | 23 | 35 | 65 | 65 | 54 | 175 | met |
| manager (department-wide) | case inventory report | 200 | 4 | 341 | 410 | 860 | 860 | 770 | 1129 | MISSED |
| manager (department-wide) | case inventory report, text filter | 200 | 7 | 200 | 310 | 412 | 412 | 650 | 1158 | met |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 24 | 1546 | 1898 | 3267 | 3267 | 3602 | 4264 | MISSED |
| manager (department-wide) | indicator figures | 200 | 15 | 1007 | 1284 | 2514 | 2514 | 2129 | 2571 | MISSED |
| manager (department-wide) | indicator list TOTAL_CASES | 200 | 2 | 140 | 182 | 450 | 450 | 345 | 664 | met |
| manager (department-wide) | indicator list CASES_NEW | 200 | 2 | 69 | 81 | 252 | 252 | 244 | 461 | met |
| manager (department-wide) | indicator list CASES_ONGOING | 200 | 2 | 113 | 149 | 255 | 255 | 281 | 509 | met |
| manager (department-wide) | indicator list CASES_CONCLUDED | 200 | 2 | 105 | 122 | 215 | 215 | 219 | 355 | met |
| manager (department-wide) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 89 | 102 | 292 | 292 | 192 | 366 | met |
| manager (department-wide) | indicator list JUDGMENTS_ISSUED | 200 | 3 | 125 | 188 | 363 | 363 | 210 | 428 | met |
| manager (department-wide) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 8 | 11 | 20 | 20 | 22 | 55 | met |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 200 | 3 | 175 | 197 | 250 | 250 | 382 | 644 | met |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 200 | 3 | 143 | 194 | 915 | 915 | 482 | 733 | MISSED |
| manager (department-wide) | indicator list APPEAL_DEADLINES | 200 | 2 | 7 | 22 | 175 | 175 | 17 | 116 | met |
| manager (department-wide) | indicator list OVERDUE_TASKS | 200 | 3 | 172 | 230 | 822 | 822 | 330 | 624 | MISSED |
| manager (department-wide) | indicator list CASES_BY_UNIT | 200 | 2 | 147 | 179 | 266 | 266 | 209 | 608 | met |
| manager (department-wide) | indicator list CASES_BY_MEMBER | 200 | 2 | 138 | 184 | 299 | 299 | 235 | 309 | met |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 2 | 287 | 329 | 445 | 445 | 459 | 854 | met |
| manager (department-wide) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 130 | 162 | 335 | 335 | 252 | 524 | met |
| manager (department-wide) | indicator list LINKED_CASES | 200 | 2 | 152 | 190 | 340 | 340 | 283 | 655 | met |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 62 | 82 | 606 | 606 | 186 | 337 | MISSED |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 200 | 1 | 135 | 178 | 423 | 423 | 376 | 677 | met |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 197 | 124 | 269 | 483 | 483 | 376 | 628 | met |
| manager (department-wide) | report family OPEN_CLOSED_CASES | 200 | 1 | 78 | 116 | 286 | 286 | 160 | 301 | met |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 200 | 1 | 142 | 182 | 671 | 671 | 449 | 1145 | MISSED |
| manager (department-wide) | report family CLAIM_RESULTS | 200 | 2 | 126 | 218 | 619 | 619 | 440 | 739 | MISSED |
| manager (department-wide) | report family DEFENSE_POSITIONS | 200 | 1 | 117 | 166 | 622 | 622 | 311 | 617 | MISSED |
| manager (department-wide) | report family LINKED_CASES | 200 | 1 | 115 | 146 | 244 | 244 | 439 | 654 | met |
| manager (department-wide) | report family ADJOURNMENTS | 200 | 1 | 49 | 77 | 506 | 506 | 222 | 477 | MISSED |
| manager (department-wide) | report family EXPERT_SESSIONS | 200 | 1 | 99 | 255 | 396 | 396 | 386 | 699 | met |
| manager (department-wide) | report family DEADLINES_APPEALS | 200 | 2 | 5 | 13 | 23 | 23 | 25 | 251 | met |
| manager (department-wide) | report family ENFORCEMENT | 200 | 2 | 9 | 13 | 26 | 26 | 19 | 26 | met |
| manager (department-wide) | report family MISSING_EVIDENCE | 200 | 1 | 114 | 140 | 374 | 374 | 390 | 630 | met |
| manager (department-wide) | report family INFO_REQUESTS | 200 | 1 | 154 | 204 | 742 | 742 | 543 | 927 | MISSED |
| manager (department-wide) | report family RISK_ASSESSMENT | 200 | 2 | 268 | 510 | 1129 | 1129 | 1087 | 1605 | MISSED |
| manager (department-wide) | report family PERFORMANCE | 200 | 201 | 345 | 548 | 1088 | 1088 | 1172 | 1546 | MISSED |
| manager (department-wide) | report family LIBRARY_PRECEDENTS | 200 | 8 | 11 | 26 | 85 | 85 | 64 | 97 | met |
| member (own cases only) | case list, first page | 200 | 1 | 50 | 208 | 373 | 373 | 286 | 555 | met |
| member (own cases only) | case list, deep page | 200 | 2 | 98 | 391 | 637 | 637 | 515 | 876 | MISSED |
| member (own cases only) | case list, text filter | 200 | 2 | 62 | 241 | 588 | 588 | 349 | 673 | MISSED |
| member (own cases only) | case list, status + type filter | 200 | 2 | 64 | 202 | 376 | 376 | 347 | 553 | met |
| member (own cases only) | unified search, reference | 200 | 3 | 56 | 123 | 213 | 213 | 252 | 404 | met |
| member (own cases only) | unified search, words | 200 | 3 | 30 | 50 | 151 | 151 | 81 | 153 | met |
| member (own cases only) | case inventory report | 200 | 4 | 267 | 399 | 575 | 575 | 663 | 1072 | MISSED |
| member (own cases only) | case inventory report, text filter | 200 | 7 | 164 | 240 | 626 | 626 | 627 | 872 | MISSED |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 24 | 1252 | 1655 | 3163 | 3163 | 3912 | 5173 | MISSED |
| member (own cases only) | indicator figures | 200 | 15 | 822 | 1167 | 1593 | 1593 | 2512 | 3364 | MISSED |
| member (own cases only) | indicator list TOTAL_CASES | 200 | 2 | 78 | 91 | 117 | 117 | 212 | 394 | met |
| member (own cases only) | indicator list CASES_NEW | 200 | 2 | 66 | 131 | 359 | 359 | 182 | 306 | met |
| member (own cases only) | indicator list CASES_ONGOING | 200 | 1 | 39 | 59 | 140 | 140 | 124 | 209 | met |
| member (own cases only) | indicator list CASES_CONCLUDED | 200 | 1 | 40 | 53 | 94 | 94 | 109 | 169 | met |
| member (own cases only) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 85 | 129 | 188 | 188 | 329 | 562 | met |
| member (own cases only) | indicator list JUDGMENTS_ISSUED | 200 | 1 | 56 | 92 | 239 | 239 | 209 | 344 | met |
| member (own cases only) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 5 | 9 | 49 | 49 | 27 | 95 | met |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 200 | 3 | 166 | 236 | 458 | 458 | 569 | 1117 | met |
| member (own cases only) | indicator list EXPERT_HEARINGS | 200 | 3 | 168 | 255 | 329 | 329 | 570 | 729 | met |
| member (own cases only) | indicator list APPEAL_DEADLINES | 200 | 2 | 5 | 13 | 20 | 20 | 16 | 23 | met |
| member (own cases only) | indicator list OVERDUE_TASKS | 200 | 3 | 149 | 164 | 218 | 218 | 417 | 637 | met |
| member (own cases only) | indicator list CASES_BY_UNIT | 200 | 2 | 72 | 85 | 120 | 120 | 256 | 491 | met |
| member (own cases only) | indicator list CASES_BY_MEMBER | 200 | 2 | 81 | 124 | 321 | 321 | 276 | 424 | met |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 2 | 206 | 399 | 645 | 645 | 1045 | 1376 | MISSED |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 120 | 303 | 544 | 544 | 664 | 1148 | MISSED |
| member (own cases only) | indicator list LINKED_CASES | 200 | 2 | 131 | 234 | 355 | 355 | 461 | 706 | met |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 51 | 67 | 143 | 143 | 170 | 264 | met |
| member (own cases only) | report family CASES_BY_ORG_UNIT | 200 | 1 | 65 | 95 | 198 | 198 | 308 | 536 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 3 | 42 | 61 | 95 | 95 | 123 | 344 | met |
| member (own cases only) | report family OPEN_CLOSED_CASES | 200 | 1 | 37 | 58 | 176 | 176 | 172 | 302 | met |
| member (own cases only) | report family JUDGMENT_OUTCOMES | 200 | 1 | 70 | 90 | 250 | 250 | 141 | 338 | met |
| member (own cases only) | report family CLAIM_RESULTS | 200 | 2 | 52 | 66 | 269 | 269 | 209 | 360 | met |
| member (own cases only) | report family DEFENSE_POSITIONS | 200 | 1 | 56 | 66 | 131 | 131 | 178 | 284 | met |
| member (own cases only) | report family LINKED_CASES | 200 | 1 | 74 | 98 | 236 | 236 | 167 | 330 | met |
| member (own cases only) | report family ADJOURNMENTS | 200 | 1 | 46 | 50 | 105 | 105 | 104 | 154 | met |
| member (own cases only) | report family EXPERT_SESSIONS | 200 | 1 | 98 | 137 | 290 | 290 | 192 | 329 | met |
| member (own cases only) | report family DEADLINES_APPEALS | 200 | 2 | 5 | 5 | 19 | 19 | 9 | 17 | met |
| member (own cases only) | report family ENFORCEMENT | 200 | 2 | 4 | 5 | 9 | 9 | 10 | 23 | met |
| member (own cases only) | report family MISSING_EVIDENCE | 200 | 1 | 59 | 75 | 109 | 109 | 79 | 148 | met |
| member (own cases only) | report family INFO_REQUESTS | 200 | 1 | 46 | 73 | 190 | 190 | 87 | 169 | met |
| member (own cases only) | report family RISK_ASSESSMENT | 200 | 2 | 186 | 210 | 461 | 461 | 564 | 902 | met |
| member (own cases only) | report family PERFORMANCE | 200 | 7 | 156 | 221 | 446 | 446 | 479 | 764 | met |
| member (own cases only) | report family LIBRARY_PRECEDENTS | 200 | 8 | 5 | 6 | 19 | 19 | 10 | 30 | met |
| no scope | case list, first page | 200 | 2 | 59 | 65 | 194 | 194 | 113 | 214 | met |
| no scope | case list, deep page | 200 | 2 | 61 | 95 | 150 | 150 | 115 | 284 | met |
| no scope | case list, text filter | 200 | 3 | 80 | 91 | 158 | 158 | 152 | 221 | met |
| no scope | case list, status + type filter | 200 | 2 | 54 | 66 | 89 | 89 | 63 | 166 | met |
| no scope | unified search, reference | 200 | 3 | 32 | 48 | 108 | 108 | 126 | 181 | met |
| no scope | unified search, words | 200 | 3 | 38 | 45 | 146 | 146 | 107 | 191 | met |
| no scope | case inventory report | 200 | 3 | 102 | 134 | 198 | 198 | 289 | 553 | met |
| no scope | case inventory report, text filter | 200 | 6 | 158 | 201 | 243 | 243 | 438 | 773 | met |
| no scope | dashboard (with the 16 indicator figures) | 200 | 24 | 722 | 945 | 1188 | 1188 | 2109 | 2766 | MISSED |
| no scope | indicator figures | 200 | 15 | 382 | 537 | 771 | 771 | 1198 | 1894 | MISSED |
| no scope | indicator list TOTAL_CASES | 200 | 1 | 35 | 38 | 78 | 78 | 124 | 209 | met |
| no scope | indicator list CASES_NEW | 200 | 1 | 29 | 51 | 107 | 107 | 70 | 114 | met |
| no scope | indicator list CASES_ONGOING | 200 | 1 | 38 | 46 | 72 | 72 | 90 | 140 | met |
| no scope | indicator list CASES_CONCLUDED | 200 | 1 | 33 | 40 | 85 | 85 | 60 | 102 | met |
| no scope | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 37 | 52 | 88 | 88 | 49 | 123 | met |
| no scope | indicator list JUDGMENTS_ISSUED | 200 | 1 | 45 | 56 | 146 | 146 | 131 | 242 | met |
| no scope | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 5 | 9 | 15 | 15 | 9 | 19 | met |
| no scope | indicator list UPCOMING_HEARINGS | 200 | 1 | 41 | 46 | 165 | 165 | 87 | 135 | met |
| no scope | indicator list EXPERT_HEARINGS | 200 | 1 | 45 | 62 | 216 | 216 | 79 | 187 | met |
| no scope | indicator list APPEAL_DEADLINES | 200 | 2 | 6 | 9 | 21 | 21 | 8 | 38 | met |
| no scope | indicator list OVERDUE_TASKS | 200 | 1 | 50 | 59 | 148 | 148 | 73 | 120 | met |
| no scope | indicator list CASES_BY_UNIT | 200 | 1 | 32 | 39 | 63 | 63 | 128 | 219 | met |
| no scope | indicator list CASES_BY_MEMBER | 200 | 1 | 30 | 47 | 123 | 123 | 76 | 215 | met |
| no scope | indicator list HIGH_RISK_CASES | 200 | 1 | 33 | 43 | 166 | 166 | 53 | 105 | met |
| no scope | indicator list OVERDUE_INFO_REQUESTS | 200 | 1 | 44 | 72 | 151 | 151 | 97 | 198 | met |
| no scope | indicator list LINKED_CASES | 200 | 1 | 39 | 49 | 137 | 137 | 45 | 96 | met |
| no scope | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 32 | 41 | 80 | 80 | 43 | 88 | met |
| no scope | report family CASES_BY_ORG_UNIT | 200 | 1 | 34 | 49 | 69 | 69 | 86 | 167 | met |
| no scope | report family CASES_BY_MEMBER | 200 | 1 | 31 | 41 | 73 | 73 | 120 | 227 | met |
| no scope | report family OPEN_CLOSED_CASES | 200 | 1 | 33 | 44 | 71 | 71 | 55 | 90 | met |
| no scope | report family JUDGMENT_OUTCOMES | 200 | 1 | 45 | 57 | 92 | 92 | 82 | 128 | met |
| no scope | report family CLAIM_RESULTS | 200 | 2 | 32 | 42 | 57 | 57 | 75 | 217 | met |
| no scope | report family DEFENSE_POSITIONS | 200 | 1 | 32 | 37 | 87 | 87 | 120 | 173 | met |
| no scope | report family LINKED_CASES | 200 | 1 | 110 | 142 | 268 | 268 | 247 | 366 | met |
| no scope | report family ADJOURNMENTS | 200 | 1 | 44 | 54 | 76 | 76 | 124 | 214 | met |
| no scope | report family EXPERT_SESSIONS | 200 | 1 | 31 | 42 | 131 | 131 | 88 | 125 | met |
| no scope | report family DEADLINES_APPEALS | 200 | 2 | 4 | 4 | 19 | 19 | 10 | 21 | met |
| no scope | report family ENFORCEMENT | 200 | 2 | 3 | 4 | 26 | 26 | 11 | 67 | met |
| no scope | report family MISSING_EVIDENCE | 200 | 1 | 33 | 39 | 45 | 45 | 77 | 112 | met |
| no scope | report family INFO_REQUESTS | 200 | 1 | 35 | 47 | 78 | 78 | 59 | 96 | met |
| no scope | report family RISK_ASSESSMENT | 200 | 2 | 116 | 137 | 255 | 255 | 308 | 434 | met |
| no scope | report family PERFORMANCE | 200 | 5 | 131 | 175 | 314 | 314 | 294 | 482 | met |
| no scope | report family LIBRARY_PRECEDENTS | 200 | 8 | 4 | 12 | 37 | 37 | 11 | 25 | met |
| manager with SEARCH_ALL | unified search, reference | 200 | 14 | 216 | 431 | 670 | 670 | 744 | 1015 | MISSED |
| manager with SEARCH_ALL | unified search, words | 200 | 13 | 120 | 138 | 238 | 238 | 183 | 383 | met |

Requests over the target (sequential p95): 25 of 131
