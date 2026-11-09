# WP8 performance run

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- cases: 2000 (requested 2000)
- child rows (proceeding=3000, judgment=1200, hearing=6000, task=3000, claim=2000, defense=2000, evidence_item=2000, info_request=800, risk_assessment=1000, case_link=399, case_org_involvement=2000): 23399
- seeding took 3607 ms
- each request: one warm-up, then 3 timed runs; the table shows min / median / max in ms
- target: 500 ms (list and search)

| user | request | HTTP | min | median | max | target |
|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 113 | 125 | 216 | met |
| manager (department-wide) | case list, deep page | 200 | 120 | 163 | 173 | met |
| manager (department-wide) | case list, text filter | 200 | 485 | 517 | 553 | MISSED |
| manager (department-wide) | case list, status + type filter | 200 | 27 | 35 | 49 | met |
| manager (department-wide) | unified search, reference | 200 | 706 | 866 | 890 | MISSED |
| manager (department-wide) | unified search, words | 200 | 635 | 816 | 930 | MISSED |
| manager (department-wide) | case inventory report | 200 | 270 | 343 | 356 | met |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 787 | 928 | 1033 | MISSED |
| manager (department-wide) | indicator figures | 200 | 534 | 676 | 933 | MISSED |
| manager (department-wide) | indicator list TOTAL_CASES | 200 | 191 | 201 | 206 | met |
| manager (department-wide) | indicator list CASES_NEW | 200 | 31 | 42 | 49 | met |
| manager (department-wide) | indicator list CASES_ONGOING | 200 | 112 | 127 | 176 | met |
| manager (department-wide) | indicator list CASES_CONCLUDED | 200 | 63 | 66 | 110 | met |
| manager (department-wide) | indicator list RESERVED_FOR_JUDGMENT | 200 | 28 | 44 | 73 | met |
| manager (department-wide) | indicator list JUDGMENTS_ISSUED | 200 | 22 | 29 | 42 | met |
| manager (department-wide) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 21 | 23 | 56 | met |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 200 | 36 | 47 | 63 | met |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 200 | 32 | 37 | 42 | met |
| manager (department-wide) | indicator list APPEAL_DEADLINES | 200 | 18 | 26 | 26 | met |
| manager (department-wide) | indicator list OVERDUE_TASKS | 200 | 44 | 54 | 88 | met |
| manager (department-wide) | indicator list CASES_BY_UNIT | 200 | 115 | 143 | 156 | met |
| manager (department-wide) | indicator list CASES_BY_MEMBER | 200 | 84 | 92 | 111 | met |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 46 | 59 | 65 | met |
| manager (department-wide) | indicator list OVERDUE_INFO_REQUESTS | 200 | 42 | 44 | 76 | met |
| manager (department-wide) | indicator list LINKED_CASES | 200 | 236 | 283 | 291 | met |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 51 | 65 | 66 | met |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 200 | 51 | 55 | 63 | met |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 226 | 316 | 453 | met |
| manager (department-wide) | report family OPEN_CLOSED_CASES | 200 | 73 | 77 | 243 | met |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 200 | 54 | 58 | 93 | met |
| manager (department-wide) | report family CLAIM_RESULTS | 200 | 58 | 61 | 108 | met |
| manager (department-wide) | report family DEFENSE_POSITIONS | 200 | 63 | 77 | 120 | met |
| manager (department-wide) | report family LINKED_CASES | 200 | 88 | 100 | 110 | met |
| manager (department-wide) | report family ADJOURNMENTS | 200 | 16 | 21 | 45 | met |
| manager (department-wide) | report family EXPERT_SESSIONS | 200 | 39 | 43 | 49 | met |
| manager (department-wide) | report family DEADLINES_APPEALS | 200 | 20 | 28 | 57 | met |
| manager (department-wide) | report family ENFORCEMENT | 200 | 18 | 26 | 35 | met |
| manager (department-wide) | report family MISSING_EVIDENCE | 200 | 50 | 62 | 74 | met |
| manager (department-wide) | report family INFO_REQUESTS | 200 | 52 | 60 | 69 | met |
| manager (department-wide) | report family RISK_ASSESSMENT | 200 | 73 | 92 | 306 | met |
| manager (department-wide) | report family PERFORMANCE | 200 | 351 | 392 | 600 | met |
| manager (department-wide) | report family LIBRARY_PRECEDENTS | 200 | 33 | 51 | 85 | met |
| scoped to one case type | case list, first page | 200 | 167 | 303 | 336 | met |
| scoped to one case type | case list, deep page | 200 | 189 | 212 | 279 | met |
| scoped to one case type | case list, text filter | 200 | 251 | 273 | 294 | met |
| scoped to one case type | case list, status + type filter | 200 | 26 | 47 | 56 | met |
| scoped to one case type | unified search, reference | 200 | 206 | 226 | 250 | met |
| scoped to one case type | unified search, words | 200 | 197 | 291 | 325 | met |
| scoped to one case type | case inventory report | 200 | 331 | 401 | 546 | met |
| scoped to one case type | dashboard (with the 16 indicator figures) | 200 | 1359 | 1637 | 2022 | MISSED |
| scoped to one case type | indicator figures | 200 | 1139 | 1183 | 1291 | MISSED |
| scoped to one case type | indicator list TOTAL_CASES | 200 | 183 | 188 | 246 | met |
| scoped to one case type | indicator list CASES_NEW | 200 | 129 | 187 | 271 | met |
| scoped to one case type | indicator list CASES_ONGOING | 200 | 127 | 175 | 253 | met |
| scoped to one case type | indicator list CASES_CONCLUDED | 200 | 101 | 102 | 247 | met |
| scoped to one case type | indicator list RESERVED_FOR_JUDGMENT | 200 | 21 | 42 | 83 | met |
| scoped to one case type | indicator list JUDGMENTS_ISSUED | 200 | 23 | 36 | 41 | met |
| scoped to one case type | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 20 | 23 | 36 | met |
| scoped to one case type | indicator list UPCOMING_HEARINGS | 200 | 63 | 96 | 165 | met |
| scoped to one case type | indicator list EXPERT_HEARINGS | 200 | 30 | 36 | 49 | met |
| scoped to one case type | indicator list APPEAL_DEADLINES | 200 | 16 | 32 | 39 | met |
| scoped to one case type | indicator list OVERDUE_TASKS | 200 | 87 | 94 | 129 | met |
| scoped to one case type | indicator list CASES_BY_UNIT | 200 | 228 | 485 | 486 | met |
| scoped to one case type | indicator list CASES_BY_MEMBER | 200 | 230 | 236 | 382 | met |
| scoped to one case type | indicator list HIGH_RISK_CASES | 200 | 97 | 153 | 172 | met |
| scoped to one case type | indicator list OVERDUE_INFO_REQUESTS | 200 | 48 | 121 | 271 | met |
| scoped to one case type | indicator list LINKED_CASES | 200 | 199 | 231 | 252 | met |
| scoped to one case type | report family CASES_BY_TYPE_COURT_STAGE | 200 | 92 | 101 | 145 | met |
| scoped to one case type | report family CASES_BY_ORG_UNIT | 200 | 102 | 104 | 119 | met |
| scoped to one case type | report family CASES_BY_MEMBER | 200 | 107 | 115 | 194 | met |
| scoped to one case type | report family OPEN_CLOSED_CASES | 200 | 120 | 140 | 196 | met |
| scoped to one case type | report family JUDGMENT_OUTCOMES | 200 | 100 | 125 | 137 | met |
| scoped to one case type | report family CLAIM_RESULTS | 200 | 139 | 143 | 143 | met |
| scoped to one case type | report family DEFENSE_POSITIONS | 200 | 109 | 138 | 170 | met |
| scoped to one case type | report family LINKED_CASES | 200 | 174 | 204 | 215 | met |
| scoped to one case type | report family ADJOURNMENTS | 200 | 20 | 22 | 30 | met |
| scoped to one case type | report family EXPERT_SESSIONS | 200 | 26 | 43 | 55 | met |
| scoped to one case type | report family DEADLINES_APPEALS | 200 | 9 | 13 | 14 | met |
| scoped to one case type | report family ENFORCEMENT | 200 | 21 | 28 | 40 | met |
| scoped to one case type | report family MISSING_EVIDENCE | 200 | 53 | 103 | 137 | met |
| scoped to one case type | report family INFO_REQUESTS | 200 | 60 | 66 | 89 | met |
| scoped to one case type | report family RISK_ASSESSMENT | 200 | 130 | 140 | 218 | met |
| scoped to one case type | report family PERFORMANCE | 200 | 249 | 254 | 329 | met |
| scoped to one case type | report family LIBRARY_PRECEDENTS | 200 | 20 | 20 | 34 | met |
| member (own cases only) | case list, first page | 200 | 269 | 329 | 341 | met |
| member (own cases only) | case list, deep page | 200 | 185 | 199 | 203 | met |
| member (own cases only) | case list, text filter | 200 | 202 | 255 | 334 | met |
| member (own cases only) | case list, status + type filter | 200 | 30 | 35 | 35 | met |
| member (own cases only) | unified search, reference | 200 | 100 | 101 | 129 | met |
| member (own cases only) | unified search, words | 200 | 95 | 109 | 117 | met |
| member (own cases only) | case inventory report | 200 | 255 | 290 | 480 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 1489 | 1570 | 2435 | MISSED |
| member (own cases only) | indicator figures | 200 | 991 | 1040 | 1129 | MISSED |
| member (own cases only) | indicator list TOTAL_CASES | 200 | 220 | 221 | 393 | met |
| member (own cases only) | indicator list CASES_NEW | 200 | 51 | 94 | 129 | met |
| member (own cases only) | indicator list CASES_ONGOING | 200 | 72 | 211 | 452 | met |
| member (own cases only) | indicator list CASES_CONCLUDED | 200 | 46 | 47 | 54 | met |
| member (own cases only) | indicator list RESERVED_FOR_JUDGMENT | 200 | 16 | 20 | 38 | met |
| member (own cases only) | indicator list JUDGMENTS_ISSUED | 200 | 14 | 18 | 25 | met |
| member (own cases only) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 10 | 21 | 51 | met |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 200 | 68 | 69 | 120 | met |
| member (own cases only) | indicator list EXPERT_HEARINGS | 200 | 19 | 25 | 36 | met |
| member (own cases only) | indicator list APPEAL_DEADLINES | 200 | 26 | 28 | 108 | met |
| member (own cases only) | indicator list OVERDUE_TASKS | 200 | 119 | 123 | 125 | met |
| member (own cases only) | indicator list CASES_BY_UNIT | 200 | 220 | 231 | 234 | met |
| member (own cases only) | indicator list CASES_BY_MEMBER | 200 | 157 | 174 | 214 | met |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 69 | 134 | 182 | met |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 200 | 44 | 46 | 63 | met |
| member (own cases only) | indicator list LINKED_CASES | 200 | 195 | 206 | 214 | met |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 104 | 114 | 148 | met |
| member (own cases only) | report family CASES_BY_ORG_UNIT | 200 | 125 | 138 | 138 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 98 | 129 | 153 | met |
| member (own cases only) | report family OPEN_CLOSED_CASES | 200 | 111 | 135 | 169 | met |
| member (own cases only) | report family JUDGMENT_OUTCOMES | 200 | 131 | 136 | 173 | met |
| member (own cases only) | report family CLAIM_RESULTS | 200 | 156 | 170 | 173 | met |
| member (own cases only) | report family DEFENSE_POSITIONS | 200 | 115 | 133 | 145 | met |
| member (own cases only) | report family LINKED_CASES | 200 | 208 | 216 | 514 | met |
| member (own cases only) | report family ADJOURNMENTS | 200 | 12 | 13 | 14 | met |
| member (own cases only) | report family EXPERT_SESSIONS | 200 | 22 | 25 | 25 | met |
| member (own cases only) | report family DEADLINES_APPEALS | 200 | 9 | 11 | 14 | met |
| member (own cases only) | report family ENFORCEMENT | 200 | 10 | 12 | 13 | met |
| member (own cases only) | report family MISSING_EVIDENCE | 200 | 42 | 44 | 53 | met |
| member (own cases only) | report family INFO_REQUESTS | 200 | 50 | 53 | 60 | met |
| member (own cases only) | report family RISK_ASSESSMENT | 200 | 125 | 138 | 200 | met |
| member (own cases only) | report family PERFORMANCE | 200 | 184 | 227 | 280 | met |
| member (own cases only) | report family LIBRARY_PRECEDENTS | 200 | 21 | 39 | 68 | met |

Requests over the target (median): 9 of 126
