# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 20bd23f
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 2 ms
- host load average at start / end of the measurement: 11.70 (1 min, 8 CPUs) / 14.87 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 8 parallel clients x 5 requests each; p50 and p95 over all 40 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 1 | 141 | 163 | 344 | 344 | 298 | 525 | met |
| manager (department-wide) | case list, deep page | 200 | 1 | 135 | 160 | 310 | 310 | 265 | 459 | met |
| manager (department-wide) | case list, text filter | 200 | 2 | 53 | 118 | 240 | 240 | 129 | 257 | met |
| manager (department-wide) | case list, status + type filter | 200 | 1 | 46 | 61 | 129 | 129 | 83 | 134 | met |
| manager (department-wide) | unified search, reference | 200 | 4 | 54 | 72 | 112 | 112 | 145 | 239 | met |
| manager (department-wide) | unified search, words | 200 | 3 | 15 | 23 | 81 | 81 | 39 | 126 | met |
| manager (department-wide) | case inventory report | 200 | 4 | 266 | 310 | 429 | 429 | 492 | 705 | met |
| manager (department-wide) | case inventory report, text filter | 200 | 7 | 121 | 151 | 250 | 250 | 232 | 342 | met |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 22 | 1088 | 1271 | 1888 | 1888 | 1706 | 2480 | MISSED |
| manager (department-wide) | indicator figures | 200 | 15 | 769 | 911 | 1206 | 1206 | 1296 | 1784 | MISSED |
| manager (department-wide) | indicator list TOTAL_CASES | 200 | 2 | 128 | 156 | 283 | 283 | 273 | 438 | met |
| manager (department-wide) | indicator list CASES_NEW | 200 | 2 | 74 | 91 | 127 | 127 | 127 | 205 | met |
| manager (department-wide) | indicator list CASES_ONGOING | 200 | 2 | 105 | 125 | 256 | 256 | 193 | 372 | met |
| manager (department-wide) | indicator list CASES_CONCLUDED | 200 | 2 | 84 | 110 | 217 | 217 | 145 | 319 | met |
| manager (department-wide) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 76 | 88 | 111 | 111 | 103 | 282 | met |
| manager (department-wide) | indicator list JUDGMENTS_ISSUED | 200 | 3 | 31 | 51 | 113 | 113 | 72 | 148 | met |
| manager (department-wide) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 7 | 12 | 45 | 45 | 12 | 46 | met |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 200 | 3 | 75 | 90 | 271 | 271 | 136 | 240 | met |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 200 | 3 | 44 | 53 | 245 | 245 | 60 | 150 | met |
| manager (department-wide) | indicator list APPEAL_DEADLINES | 200 | 2 | 5 | 6 | 13 | 13 | 11 | 57 | met |
| manager (department-wide) | indicator list OVERDUE_TASKS | 200 | 3 | 132 | 159 | 207 | 207 | 238 | 343 | met |
| manager (department-wide) | indicator list CASES_BY_UNIT | 200 | 2 | 120 | 155 | 219 | 219 | 283 | 445 | met |
| manager (department-wide) | indicator list CASES_BY_MEMBER | 200 | 2 | 132 | 163 | 204 | 204 | 220 | 350 | met |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 2 | 160 | 184 | 299 | 299 | 316 | 528 | met |
| manager (department-wide) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 89 | 103 | 205 | 205 | 200 | 284 | met |
| manager (department-wide) | indicator list LINKED_CASES | 200 | 2 | 125 | 155 | 269 | 269 | 304 | 446 | met |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 54 | 75 | 136 | 136 | 108 | 161 | met |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 200 | 1 | 130 | 145 | 256 | 256 | 290 | 379 | met |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 2 | 89 | 115 | 170 | 170 | 212 | 295 | met |
| manager (department-wide) | report family OPEN_CLOSED_CASES | 200 | 1 | 59 | 71 | 139 | 139 | 113 | 186 | met |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 200 | 1 | 116 | 141 | 186 | 186 | 339 | 729 | met |
| manager (department-wide) | report family CLAIM_RESULTS | 200 | 2 | 107 | 148 | 252 | 252 | 174 | 342 | met |
| manager (department-wide) | report family DEFENSE_POSITIONS | 200 | 1 | 93 | 110 | 199 | 199 | 213 | 325 | met |
| manager (department-wide) | report family LINKED_CASES | 200 | 1 | 96 | 117 | 166 | 166 | 250 | 366 | met |
| manager (department-wide) | report family ADJOURNMENTS | 200 | 1 | 45 | 53 | 88 | 88 | 89 | 124 | met |
| manager (department-wide) | report family EXPERT_SESSIONS | 200 | 1 | 91 | 108 | 202 | 202 | 132 | 253 | met |
| manager (department-wide) | report family DEADLINES_APPEALS | 200 | 2 | 5 | 8 | 17 | 17 | 11 | 19 | met |
| manager (department-wide) | report family ENFORCEMENT | 200 | 2 | 5 | 7 | 15 | 15 | 10 | 23 | met |
| manager (department-wide) | report family MISSING_EVIDENCE | 200 | 1 | 112 | 132 | 193 | 193 | 164 | 364 | met |
| manager (department-wide) | report family INFO_REQUESTS | 200 | 1 | 149 | 195 | 293 | 293 | 411 | 591 | met |
| manager (department-wide) | report family RISK_ASSESSMENT | 200 | 2 | 135 | 173 | 232 | 232 | 234 | 514 | met |
| manager (department-wide) | report family PERFORMANCE | 200 | 6 | 203 | 242 | 482 | 482 | 404 | 633 | met |
| manager (department-wide) | report family LIBRARY_PRECEDENTS | 200 | 8 | 6 | 11 | 29 | 29 | 17 | 35 | met |
| member (own cases only) | case list, first page | 200 | 1 | 37 | 52 | 80 | 80 | 79 | 114 | met |
| member (own cases only) | case list, deep page | 200 | 2 | 65 | 75 | 128 | 128 | 178 | 309 | met |
| member (own cases only) | case list, text filter | 200 | 2 | 41 | 58 | 85 | 85 | 104 | 164 | met |
| member (own cases only) | case list, status + type filter | 200 | 2 | 47 | 62 | 143 | 143 | 106 | 179 | met |
| member (own cases only) | unified search, reference | 200 | 3 | 42 | 52 | 149 | 149 | 89 | 248 | met |
| member (own cases only) | unified search, words | 200 | 3 | 9 | 10 | 15 | 15 | 20 | 33 | met |
| member (own cases only) | case inventory report | 200 | 4 | 119 | 135 | 193 | 193 | 198 | 280 | met |
| member (own cases only) | case inventory report, text filter | 200 | 7 | 117 | 132 | 182 | 182 | 137 | 239 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 22 | 682 | 754 | 900 | 900 | 940 | 1399 | MISSED |
| member (own cases only) | indicator figures | 200 | 15 | 504 | 550 | 880 | 880 | 735 | 1196 | MISSED |
| member (own cases only) | indicator list TOTAL_CASES | 200 | 2 | 70 | 98 | 343 | 343 | 106 | 182 | met |
| member (own cases only) | indicator list CASES_NEW | 200 | 2 | 52 | 66 | 139 | 139 | 109 | 234 | met |
| member (own cases only) | indicator list CASES_ONGOING | 200 | 1 | 36 | 59 | 159 | 159 | 41 | 110 | met |
| member (own cases only) | indicator list CASES_CONCLUDED | 200 | 1 | 37 | 50 | 142 | 142 | 44 | 86 | met |
| member (own cases only) | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 77 | 85 | 214 | 214 | 108 | 169 | met |
| member (own cases only) | indicator list JUDGMENTS_ISSUED | 200 | 1 | 14 | 33 | 75 | 75 | 17 | 80 | met |
| member (own cases only) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 7 | 12 | 32 | 32 | 6 | 12 | met |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 200 | 3 | 99 | 140 | 184 | 184 | 121 | 388 | met |
| member (own cases only) | indicator list EXPERT_HEARINGS | 200 | 3 | 45 | 54 | 113 | 113 | 51 | 123 | met |
| member (own cases only) | indicator list APPEAL_DEADLINES | 200 | 2 | 5 | 11 | 37 | 37 | 6 | 14 | met |
| member (own cases only) | indicator list OVERDUE_TASKS | 200 | 3 | 101 | 157 | 216 | 216 | 144 | 254 | met |
| member (own cases only) | indicator list CASES_BY_UNIT | 200 | 2 | 68 | 84 | 107 | 107 | 103 | 231 | met |
| member (own cases only) | indicator list CASES_BY_MEMBER | 200 | 2 | 70 | 96 | 195 | 195 | 108 | 309 | met |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 2 | 116 | 133 | 173 | 173 | 246 | 339 | met |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 200 | 3 | 75 | 87 | 132 | 132 | 164 | 283 | met |
| member (own cases only) | indicator list LINKED_CASES | 200 | 2 | 101 | 116 | 170 | 170 | 293 | 423 | met |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 33 | 53 | 123 | 123 | 91 | 166 | met |
| member (own cases only) | report family CASES_BY_ORG_UNIT | 200 | 1 | 57 | 75 | 142 | 142 | 111 | 289 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 2 | 40 | 45 | 114 | 114 | 54 | 98 | met |
| member (own cases only) | report family OPEN_CLOSED_CASES | 200 | 1 | 34 | 45 | 55 | 55 | 88 | 178 | met |
| member (own cases only) | report family JUDGMENT_OUTCOMES | 200 | 1 | 66 | 83 | 156 | 156 | 128 | 252 | met |
| member (own cases only) | report family CLAIM_RESULTS | 200 | 2 | 48 | 62 | 94 | 94 | 91 | 167 | met |
| member (own cases only) | report family DEFENSE_POSITIONS | 200 | 1 | 48 | 56 | 92 | 92 | 75 | 150 | met |
| member (own cases only) | report family LINKED_CASES | 200 | 1 | 72 | 105 | 155 | 155 | 113 | 389 | met |
| member (own cases only) | report family ADJOURNMENTS | 200 | 1 | 37 | 45 | 79 | 79 | 66 | 142 | met |
| member (own cases only) | report family EXPERT_SESSIONS | 200 | 1 | 91 | 122 | 221 | 221 | 129 | 262 | met |
| member (own cases only) | report family DEADLINES_APPEALS | 200 | 2 | 5 | 9 | 21 | 21 | 8 | 22 | met |
| member (own cases only) | report family ENFORCEMENT | 200 | 2 | 4 | 13 | 77 | 77 | 7 | 57 | met |
| member (own cases only) | report family MISSING_EVIDENCE | 200 | 1 | 60 | 79 | 115 | 115 | 80 | 163 | met |
| member (own cases only) | report family INFO_REQUESTS | 200 | 1 | 44 | 52 | 66 | 66 | 72 | 156 | met |
| member (own cases only) | report family RISK_ASSESSMENT | 200 | 2 | 84 | 112 | 188 | 188 | 228 | 356 | met |
| member (own cases only) | report family PERFORMANCE | 200 | 6 | 136 | 168 | 305 | 305 | 442 | 851 | met |
| member (own cases only) | report family LIBRARY_PRECEDENTS | 200 | 8 | 5 | 7 | 17 | 17 | 26 | 48 | met |
| no scope | case list, first page | 200 | 2 | 54 | 65 | 101 | 101 | 127 | 245 | met |
| no scope | case list, deep page | 200 | 2 | 49 | 76 | 105 | 105 | 96 | 192 | met |
| no scope | case list, text filter | 200 | 3 | 55 | 69 | 222 | 222 | 140 | 295 | met |
| no scope | case list, status + type filter | 200 | 2 | 45 | 58 | 140 | 140 | 110 | 219 | met |
| no scope | unified search, reference | 200 | 3 | 34 | 45 | 91 | 91 | 56 | 104 | met |
| no scope | unified search, words | 200 | 3 | 32 | 47 | 89 | 89 | 66 | 185 | met |
| no scope | case inventory report | 200 | 3 | 84 | 111 | 220 | 220 | 189 | 316 | met |
| no scope | case inventory report, text filter | 200 | 6 | 92 | 176 | 325 | 325 | 384 | 538 | met |
| no scope | dashboard (with the 16 indicator figures) | 200 | 22 | 517 | 662 | 764 | 764 | 997 | 1330 | MISSED |
| no scope | indicator figures | 200 | 15 | 320 | 368 | 575 | 575 | 706 | 1054 | MISSED |
| no scope | indicator list TOTAL_CASES | 200 | 1 | 39 | 93 | 431 | 431 | 70 | 267 | met |
| no scope | indicator list CASES_NEW | 200 | 1 | 22 | 24 | 155 | 155 | 148 | 243 | met |
| no scope | indicator list CASES_ONGOING | 200 | 1 | 35 | 55 | 643 | 643 | 220 | 430 | MISSED |
| no scope | indicator list CASES_CONCLUDED | 200 | 1 | 46 | 72 | 210 | 210 | 133 | 354 | met |
| no scope | indicator list RESERVED_FOR_JUDGMENT | 200 | 1 | 33 | 61 | 325 | 325 | 108 | 258 | met |
| no scope | indicator list JUDGMENTS_ISSUED | 200 | 1 | 13 | 36 | 137 | 137 | 36 | 89 | met |
| no scope | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 2 | 4 | 6 | 60 | 60 | 13 | 220 | met |
| no scope | indicator list UPCOMING_HEARINGS | 200 | 1 | 46 | 60 | 89 | 89 | 145 | 292 | met |
| no scope | indicator list EXPERT_HEARINGS | 200 | 1 | 19 | 29 | 86 | 86 | 81 | 290 | met |
| no scope | indicator list APPEAL_DEADLINES | 200 | 2 | 5 | 11 | 25 | 25 | 12 | 30 | met |
| no scope | indicator list OVERDUE_TASKS | 200 | 1 | 52 | 66 | 94 | 94 | 89 | 173 | met |
| no scope | indicator list CASES_BY_UNIT | 200 | 1 | 27 | 39 | 62 | 62 | 111 | 221 | met |
| no scope | indicator list CASES_BY_MEMBER | 200 | 1 | 27 | 55 | 99 | 99 | 68 | 242 | met |
| no scope | indicator list HIGH_RISK_CASES | 200 | 1 | 32 | 45 | 106 | 106 | 77 | 192 | met |
| no scope | indicator list OVERDUE_INFO_REQUESTS | 200 | 1 | 37 | 46 | 141 | 141 | 64 | 117 | met |
| no scope | indicator list LINKED_CASES | 200 | 1 | 34 | 43 | 75 | 75 | 66 | 113 | met |
| no scope | report family CASES_BY_TYPE_COURT_STAGE | 200 | 1 | 28 | 41 | 71 | 71 | 71 | 142 | met |
| no scope | report family CASES_BY_ORG_UNIT | 200 | 1 | 29 | 44 | 144 | 144 | 82 | 268 | met |
| no scope | report family CASES_BY_MEMBER | 200 | 1 | 30 | 37 | 286 | 286 | 73 | 199 | met |
| no scope | report family OPEN_CLOSED_CASES | 200 | 1 | 34 | 50 | 104 | 104 | 61 | 175 | met |
| no scope | report family JUDGMENT_OUTCOMES | 200 | 1 | 42 | 51 | 208 | 208 | 176 | 345 | met |
| no scope | report family CLAIM_RESULTS | 200 | 2 | 34 | 55 | 135 | 135 | 88 | 240 | met |
| no scope | report family DEFENSE_POSITIONS | 200 | 1 | 33 | 44 | 564 | 564 | 108 | 295 | MISSED |
| no scope | report family LINKED_CASES | 200 | 1 | 81 | 174 | 231 | 231 | 260 | 520 | met |
| no scope | report family ADJOURNMENTS | 200 | 1 | 34 | 52 | 156 | 156 | 136 | 323 | met |
| no scope | report family EXPERT_SESSIONS | 200 | 1 | 31 | 59 | 97 | 97 | 61 | 196 | met |
| no scope | report family DEADLINES_APPEALS | 200 | 2 | 4 | 5 | 14 | 14 | 37 | 127 | met |
| no scope | report family ENFORCEMENT | 200 | 2 | 3 | 4 | 7 | 7 | 12 | 129 | met |
| no scope | report family MISSING_EVIDENCE | 200 | 1 | 29 | 41 | 100 | 100 | 60 | 243 | met |
| no scope | report family INFO_REQUESTS | 200 | 1 | 30 | 48 | 171 | 171 | 47 | 193 | met |
| no scope | report family RISK_ASSESSMENT | 200 | 2 | 60 | 101 | 355 | 355 | 170 | 394 | met |
| no scope | report family PERFORMANCE | 200 | 5 | 105 | 149 | 243 | 243 | 225 | 423 | met |
| no scope | report family LIBRARY_PRECEDENTS | 200 | 8 | 5 | 6 | 51 | 51 | 7 | 82 | met |
| manager with SEARCH_ALL | unified search, reference | 200 | 14 | 75 | 99 | 171 | 171 | 112 | 256 | met |
| manager with SEARCH_ALL | unified search, words | 200 | 13 | 11 | 12 | 28 | 28 | 51 | 136 | met |

Requests over the target (sequential p95): 8 of 131
