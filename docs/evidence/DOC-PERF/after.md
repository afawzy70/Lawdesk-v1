# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): e5e688e
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 329622 ms
- host load average at start / end of the measurement: 2.28 (1 min, 8 CPUs) / 5.05 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 8 parallel clients x 5 requests each; p50 and p95 over all 40 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 1 | 141 | 192 | 258 | 258 | 228 | 393 | met |
| manager (department-wide) | case list, deep page | 200 | 1 | 170 | 232 | 532 | 532 | 234 | 466 | MISSED |
| manager (department-wide) | case list, text filter | 200 | 2 | 71 | 96 | 223 | 223 | 98 | 184 | met |
| manager (department-wide) | case list, status + type filter | 200 | 1 | 43 | 66 | 238 | 238 | 65 | 189 | met |
| manager (department-wide) | unified search, reference | 200 | 4 | 70 | 112 | 211 | 211 | 171 | 448 | met |
| manager (department-wide) | unified search, words | 200 | 3 | 28 | 44 | 194 | 194 | 77 | 196 | met |
| manager (department-wide) | case inventory report | 200 | 4 | 292 | 426 | 875 | 875 | 436 | 807 | MISSED |
| manager (department-wide) | case inventory report, text filter | 200 | 7 | 119 | 167 | 316 | 316 | 179 | 510 | met |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 24 | 1496 | 1605 | 1921 | 1921 | 1637 | 2017 | MISSED |
| manager (department-wide) | indicator figures | 200 | 15 | 848 | 1006 | 1588 | 1588 | 967 | 1208 | MISSED |
| manager (department-wide) | indicator list TOTAL_CASES | 200 | 2 | 143 | 163 | 239 | 239 | 164 | 333 | met |
| manager (department-wide) | indicator list CASES_NEW | 200 | 2 | 72 | 81 | 143 | 143 | 82 | 120 | met |
| manager (department-wide) | indicator list CASES_ONGOING | 200 | 2 | 110 | 132 | 179 | 179 | 155 | 245 | met |
| manager (department-wide) | indicator list CASES_CONCLUDED | 200 | 2 | 91 | 113 | 354 | 354 | 110 | 177 | met |
| manager (department-wide) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 82 | 101 | 252 | 252 | 99 | 136 | met |
| manager (department-wide) | indicator list JUDGMENTS_ISSUED | 200 | 3 | 41 | 81 | 140 | 140 | 68 | 201 | met |
| manager (department-wide) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 8 | 18 | 92 | 92 | 9 | 18 | met |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 200 | 3 | 92 | 111 | 143 | 143 | 98 | 198 | met |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 200 | 3 | 52 | 66 | 170 | 170 | 79 | 240 | met |
| manager (department-wide) | indicator list APPEAL_DEADLINES | 200 | 2 | 8 | 12 | 20 | 20 | 8 | 13 | met |
| manager (department-wide) | indicator list OVERDUE_TASKS | 200 | 3 | 125 | 149 | 198 | 198 | 186 | 291 | met |
| manager (department-wide) | indicator list CASES_BY_UNIT | 200 | 2 | 148 | 162 | 555 | 555 | 167 | 219 | MISSED |
| manager (department-wide) | indicator list CASES_BY_MEMBER | 200 | 2 | 134 | 154 | 222 | 222 | 158 | 222 | met |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 2 | 270 | 325 | 674 | 674 | 385 | 754 | MISSED |
| manager (department-wide) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 94 | 115 | 244 | 244 | 101 | 258 | met |
| manager (department-wide) | indicator list LINKED_CASES | 200 | 2 | 155 | 178 | 254 | 254 | 172 | 285 | met |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 66 | 80 | 180 | 180 | 71 | 141 | met |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 200 | 1 | 136 | 190 | 711 | 711 | 291 | 847 | MISSED |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 197 | 147 | 327 | 653 | 653 | 133 | 272 | MISSED |
| manager (department-wide) | report family OPEN_CLOSED_CASES | 200 | 1 | 65 | 91 | 231 | 231 | 75 | 125 | met |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 200 | 1 | 129 | 159 | 267 | 267 | 224 | 841 | met |
| manager (department-wide) | report family CLAIM_RESULTS | 200 | 2 | 121 | 208 | 612 | 612 | 246 | 1304 | MISSED |
| manager (department-wide) | report family DEFENSE_POSITIONS | 200 | 1 | 108 | 126 | 177 | 177 | 120 | 190 | met |
| manager (department-wide) | report family LINKED_CASES | 200 | 1 | 119 | 143 | 203 | 203 | 125 | 270 | met |
| manager (department-wide) | report family ADJOURNMENTS | 200 | 1 | 45 | 52 | 191 | 191 | 51 | 113 | met |
| manager (department-wide) | report family EXPERT_SESSIONS | 200 | 1 | 93 | 113 | 232 | 232 | 109 | 180 | met |
| manager (department-wide) | report family DEADLINES_APPEALS | 200 | 2 | 9 | 17 | 45 | 45 | 9 | 33 | met |
| manager (department-wide) | report family ENFORCEMENT | 200 | 2 | 7 | 14 | 92 | 92 | 11 | 56 | met |
| manager (department-wide) | report family MISSING_EVIDENCE | 200 | 1 | 123 | 163 | 285 | 285 | 141 | 243 | met |
| manager (department-wide) | report family INFO_REQUESTS | 200 | 1 | 146 | 176 | 217 | 217 | 215 | 375 | met |
| manager (department-wide) | report family RISK_ASSESSMENT | 200 | 2 | 293 | 391 | 764 | 764 | 508 | 1024 | MISSED |
| manager (department-wide) | report family PERFORMANCE | 200 | 201 | 372 | 554 | 773 | 773 | 395 | 1066 | MISSED |
| manager (department-wide) | report family LIBRARY_PRECEDENTS | 200 | 8 | 6 | 10 | 43 | 43 | 16 | 50 | met |
| member (own cases only) | case list, first page | 200 | 1 | 38 | 44 | 163 | 163 | 61 | 209 | met |
| member (own cases only) | case list, deep page | 200 | 2 | 84 | 114 | 362 | 362 | 83 | 151 | met |
| member (own cases only) | case list, text filter | 200 | 2 | 39 | 61 | 151 | 151 | 51 | 137 | met |
| member (own cases only) | case list, status + type filter | 200 | 2 | 49 | 59 | 105 | 105 | 70 | 182 | met |
| member (own cases only) | unified search, reference | 200 | 3 | 44 | 59 | 119 | 119 | 56 | 91 | met |
| member (own cases only) | unified search, words | 200 | 3 | 12 | 17 | 42 | 42 | 18 | 267 | met |
| member (own cases only) | case inventory report | 200 | 4 | 120 | 210 | 318 | 318 | 148 | 319 | met |
| member (own cases only) | case inventory report, text filter | 200 | 7 | 109 | 156 | 309 | 309 | 203 | 687 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 24 | 977 | 1144 | 1760 | 1760 | 1643 | 2273 | MISSED |
| member (own cases only) | indicator figures | 200 | 15 | 630 | 757 | 971 | 971 | 777 | 1174 | MISSED |
| member (own cases only) | indicator list TOTAL_CASES | 200 | 2 | 70 | 90 | 145 | 145 | 80 | 140 | met |
| member (own cases only) | indicator list CASES_NEW | 200 | 2 | 50 | 69 | 138 | 138 | 57 | 96 | met |
| member (own cases only) | indicator list CASES_ONGOING | 200 | 1 | 37 | 46 | 63 | 63 | 56 | 156 | met |
| member (own cases only) | indicator list CASES_CONCLUDED | 200 | 1 | 36 | 53 | 107 | 107 | 43 | 108 | met |
| member (own cases only) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 84 | 94 | 232 | 232 | 100 | 341 | met |
| member (own cases only) | indicator list JUDGMENTS_ISSUED | 200 | 1 | 18 | 25 | 42 | 42 | 20 | 51 | met |
| member (own cases only) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 7 | 13 | 34 | 34 | 7 | 20 | met |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 200 | 3 | 93 | 102 | 132 | 132 | 109 | 381 | met |
| member (own cases only) | indicator list EXPERT_HEARINGS | 200 | 3 | 50 | 61 | 89 | 89 | 62 | 136 | met |
| member (own cases only) | indicator list APPEAL_DEADLINES | 200 | 2 | 5 | 11 | 25 | 25 | 11 | 71 | met |
| member (own cases only) | indicator list OVERDUE_TASKS | 200 | 3 | 125 | 214 | 393 | 393 | 123 | 322 | met |
| member (own cases only) | indicator list CASES_BY_UNIT | 200 | 2 | 96 | 108 | 151 | 151 | 103 | 269 | met |
| member (own cases only) | indicator list CASES_BY_MEMBER | 200 | 2 | 81 | 94 | 147 | 147 | 93 | 229 | met |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 2 | 293 | 334 | 413 | 413 | 395 | 550 | met |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 88 | 114 | 254 | 254 | 103 | 234 | met |
| member (own cases only) | indicator list LINKED_CASES | 200 | 2 | 114 | 177 | 344 | 344 | 142 | 340 | met |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 30 | 55 | 434 | 434 | 44 | 143 | met |
| member (own cases only) | report family CASES_BY_ORG_UNIT | 200 | 1 | 61 | 69 | 92 | 92 | 87 | 188 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 3 | 37 | 47 | 113 | 113 | 45 | 77 | met |
| member (own cases only) | report family OPEN_CLOSED_CASES | 200 | 1 | 40 | 44 | 96 | 96 | 40 | 62 | met |
| member (own cases only) | report family JUDGMENT_OUTCOMES | 200 | 1 | 61 | 83 | 143 | 143 | 88 | 147 | met |
| member (own cases only) | report family CLAIM_RESULTS | 200 | 2 | 50 | 58 | 76 | 76 | 61 | 131 | met |
| member (own cases only) | report family DEFENSE_POSITIONS | 200 | 1 | 49 | 60 | 118 | 118 | 75 | 119 | met |
| member (own cases only) | report family LINKED_CASES | 200 | 1 | 68 | 81 | 108 | 108 | 107 | 205 | met |
| member (own cases only) | report family ADJOURNMENTS | 200 | 1 | 39 | 49 | 118 | 118 | 47 | 147 | met |
| member (own cases only) | report family EXPERT_SESSIONS | 200 | 1 | 93 | 108 | 134 | 134 | 111 | 266 | met |
| member (own cases only) | report family DEADLINES_APPEALS | 200 | 2 | 7 | 12 | 19 | 19 | 6 | 16 | met |
| member (own cases only) | report family ENFORCEMENT | 200 | 2 | 6 | 12 | 19 | 19 | 8 | 37 | met |
| member (own cases only) | report family MISSING_EVIDENCE | 200 | 1 | 65 | 90 | 231 | 231 | 65 | 119 | met |
| member (own cases only) | report family INFO_REQUESTS | 200 | 1 | 45 | 52 | 62 | 62 | 49 | 82 | met |
| member (own cases only) | report family RISK_ASSESSMENT | 200 | 2 | 213 | 242 | 402 | 402 | 281 | 512 | met |
| member (own cases only) | report family PERFORMANCE | 200 | 7 | 165 | 191 | 422 | 422 | 157 | 518 | met |
| member (own cases only) | report family LIBRARY_PRECEDENTS | 200 | 8 | 7 | 18 | 38 | 38 | 15 | 63 | met |
| no scope | case list, first page | 200 | 2 | 62 | 77 | 189 | 189 | 92 | 509 | met |
| no scope | case list, deep page | 200 | 2 | 61 | 76 | 94 | 94 | 67 | 134 | met |
| no scope | case list, text filter | 200 | 3 | 66 | 72 | 106 | 106 | 83 | 174 | met |
| no scope | case list, status + type filter | 200 | 2 | 49 | 62 | 110 | 110 | 54 | 145 | met |
| no scope | unified search, reference | 200 | 3 | 39 | 50 | 103 | 103 | 37 | 175 | met |
| no scope | unified search, words | 200 | 3 | 38 | 44 | 61 | 61 | 34 | 104 | met |
| no scope | case inventory report | 200 | 3 | 88 | 104 | 131 | 131 | 128 | 344 | met |
| no scope | case inventory report, text filter | 200 | 6 | 112 | 151 | 221 | 221 | 120 | 212 | met |
| no scope | dashboard (with the 16 indicator figures) | 200 | 24 | 724 | 890 | 1594 | 1594 | 885 | 1803 | MISSED |
| no scope | indicator figures | 200 | 15 | 475 | 603 | 1013 | 1013 | 491 | 821 | MISSED |
| no scope | indicator list TOTAL_CASES | 200 | 1 | 36 | 53 | 138 | 138 | 41 | 161 | met |
| no scope | indicator list CASES_NEW | 200 | 1 | 26 | 39 | 61 | 61 | 39 | 184 | met |
| no scope | indicator list CASES_ONGOING | 200 | 1 | 33 | 44 | 87 | 87 | 40 | 180 | met |
| no scope | indicator list CASES_CONCLUDED | 200 | 1 | 33 | 56 | 117 | 117 | 38 | 220 | met |
| no scope | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 34 | 50 | 128 | 128 | 37 | 150 | met |
| no scope | indicator list JUDGMENTS_ISSUED | 200 | 1 | 18 | 33 | 101 | 101 | 17 | 105 | met |
| no scope | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 7 | 18 | 54 | 54 | 7 | 45 | met |
| no scope | indicator list UPCOMING_HEARINGS | 200 | 1 | 44 | 58 | 69 | 69 | 55 | 94 | met |
| no scope | indicator list EXPERT_HEARINGS | 200 | 1 | 21 | 31 | 70 | 70 | 36 | 153 | met |
| no scope | indicator list APPEAL_DEADLINES | 200 | 2 | 6 | 9 | 14 | 14 | 8 | 24 | met |
| no scope | indicator list OVERDUE_TASKS | 200 | 1 | 51 | 63 | 116 | 116 | 58 | 140 | met |
| no scope | indicator list CASES_BY_UNIT | 200 | 1 | 30 | 35 | 49 | 49 | 31 | 49 | met |
| no scope | indicator list CASES_BY_MEMBER | 200 | 1 | 31 | 40 | 113 | 113 | 33 | 66 | met |
| no scope | indicator list HIGH_RISK_CASES | 200 | 1 | 122 | 146 | 262 | 262 | 157 | 357 | met |
| no scope | indicator list OVERDUE_INFO_REQUESTS | 200 | 1 | 34 | 44 | 71 | 71 | 32 | 55 | met |
| no scope | indicator list LINKED_CASES | 200 | 1 | 30 | 39 | 105 | 105 | 33 | 56 | met |
| no scope | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 35 | 44 | 59 | 59 | 32 | 121 | met |
| no scope | report family CASES_BY_ORG_UNIT | 200 | 1 | 33 | 40 | 84 | 84 | 34 | 60 | met |
| no scope | report family CASES_BY_MEMBER | 200 | 1 | 35 | 40 | 283 | 283 | 43 | 209 | met |
| no scope | report family OPEN_CLOSED_CASES | 200 | 1 | 33 | 41 | 69 | 69 | 46 | 199 | met |
| no scope | report family JUDGMENT_OUTCOMES | 200 | 1 | 46 | 63 | 77 | 77 | 62 | 125 | met |
| no scope | report family CLAIM_RESULTS | 200 | 2 | 40 | 56 | 109 | 109 | 48 | 310 | met |
| no scope | report family DEFENSE_POSITIONS | 200 | 1 | 29 | 48 | 135 | 135 | 43 | 160 | met |
| no scope | report family LINKED_CASES | 200 | 1 | 94 | 115 | 295 | 295 | 263 | 1040 | met |
| no scope | report family ADJOURNMENTS | 200 | 1 | 50 | 70 | 323 | 323 | 58 | 440 | met |
| no scope | report family EXPERT_SESSIONS | 200 | 1 | 31 | 51 | 210 | 210 | 36 | 355 | met |
| no scope | report family DEADLINES_APPEALS | 200 | 2 | 7 | 12 | 40 | 40 | 7 | 23 | met |
| no scope | report family ENFORCEMENT | 200 | 2 | 8 | 18 | 46 | 46 | 5 | 26 | met |
| no scope | report family MISSING_EVIDENCE | 200 | 1 | 34 | 66 | 485 | 485 | 39 | 194 | met |
| no scope | report family INFO_REQUESTS | 200 | 1 | 34 | 55 | 96 | 96 | 48 | 310 | met |
| no scope | report family RISK_ASSESSMENT | 200 | 2 | 196 | 256 | 339 | 339 | 249 | 504 | met |
| no scope | report family PERFORMANCE | 200 | 5 | 105 | 128 | 157 | 157 | 111 | 236 | met |
| no scope | report family LIBRARY_PRECEDENTS | 200 | 8 | 8 | 13 | 74 | 74 | 9 | 21 | met |
| manager with SEARCH_ALL | unified search, reference | 200 | 14 | 61 | 111 | 233 | 233 | 89 | 178 | met |
| manager with SEARCH_ALL | unified search, words | 200 | 13 | 13 | 28 | 60 | 60 | 27 | 65 | met |

Requests over the target (sequential p95): 15 of 131
