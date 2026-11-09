# WP8 performance run

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 69308 ms
- each request: one warm-up (a request slower than 6000 ms is reported from that single run), then 3 timed runs; the table shows min / median / max in ms
- target: 500 ms (list and search)

| user | request | HTTP | min | median | max | target |
|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 158 | 212 | 358 | met |
| manager (department-wide) | case list, deep page | 200 | 186 | 359 | 508 | met |
| manager (department-wide) | case list, text filter | 200 | 527 | 603 | 676 | MISSED |
| manager (department-wide) | case list, status + type filter | 200 | 143 | 144 | 272 | met |
| manager (department-wide) | unified search, reference | 200 | 19261 | 19261 | 19261 | MISSED |
| manager (department-wide) | unified search, words | 200 | 16828 | 16828 | 16828 | MISSED |
| manager (department-wide) | case inventory report | 200 | 720 | 900 | 1105 | MISSED |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 1990 | 2071 | 2236 | MISSED |
| manager (department-wide) | indicator figures | 200 | 1275 | 1413 | 1478 | MISSED |
| manager (department-wide) | indicator list TOTAL_CASES | 200 | 204 | 206 | 224 | met |
| manager (department-wide) | indicator list CASES_NEW | 200 | 94 | 160 | 246 | met |
| manager (department-wide) | indicator list CASES_ONGOING | 200 | 132 | 201 | 260 | met |
| manager (department-wide) | indicator list CASES_CONCLUDED | 200 | 114 | 143 | 281 | met |
| manager (department-wide) | indicator list RESERVED_FOR_JUDGMENT | 200 | 109 | 112 | 134 | met |
| manager (department-wide) | indicator list JUDGMENTS_ISSUED | 200 | 81 | 89 | 118 | met |
| manager (department-wide) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 17 | 18 | 18 | met |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 200 | 104 | 104 | 107 | met |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 200 | 82 | 90 | 99 | met |
| manager (department-wide) | indicator list APPEAL_DEADLINES | 200 | 24 | 72 | 140 | met |
| manager (department-wide) | indicator list OVERDUE_TASKS | 200 | 149 | 192 | 250 | met |
| manager (department-wide) | indicator list CASES_BY_UNIT | 200 | 172 | 232 | 266 | met |
| manager (department-wide) | indicator list CASES_BY_MEMBER | 200 | 232 | 258 | 298 | met |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 305 | 325 | 455 | met |
| manager (department-wide) | indicator list OVERDUE_INFO_REQUESTS | 200 | 153 | 157 | 158 | met |
| manager (department-wide) | indicator list LINKED_CASES | 200 | 163 | 238 | 290 | met |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 74 | 119 | 125 | met |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 200 | 158 | 179 | 195 | met |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 402 | 493 | 592 | met |
| manager (department-wide) | report family OPEN_CLOSED_CASES | 200 | 85 | 99 | 103 | met |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 200 | 144 | 213 | 356 | met |
| manager (department-wide) | report family CLAIM_RESULTS | 200 | 177 | 355 | 355 | met |
| manager (department-wide) | report family DEFENSE_POSITIONS | 200 | 189 | 230 | 240 | met |
| manager (department-wide) | report family LINKED_CASES | 200 | 134 | 198 | 234 | met |
| manager (department-wide) | report family ADJOURNMENTS | 200 | 70 | 73 | 100 | met |
| manager (department-wide) | report family EXPERT_SESSIONS | 200 | 134 | 374 | 584 | met |
| manager (department-wide) | report family DEADLINES_APPEALS | 200 | 23 | 40 | 45 | met |
| manager (department-wide) | report family ENFORCEMENT | 200 | 13 | 18 | 20 | met |
| manager (department-wide) | report family MISSING_EVIDENCE | 200 | 163 | 173 | 279 | met |
| manager (department-wide) | report family INFO_REQUESTS | 200 | 164 | 165 | 203 | met |
| manager (department-wide) | report family RISK_ASSESSMENT | 200 | 269 | 317 | 362 | met |
| manager (department-wide) | report family PERFORMANCE | 200 | 438 | 471 | 709 | met |
| manager (department-wide) | report family LIBRARY_PRECEDENTS | 200 | 22 | 32 | 50 | met |
| scoped to one case type | case list, first page | 200 | 74 | 77 | 93 | met |
| scoped to one case type | case list, deep page | 200 | 72 | 80 | 116 | met |
| scoped to one case type | case list, text filter | 200 | 454 | 470 | 470 | met |
| scoped to one case type | case list, status + type filter | 200 | 47 | 52 | 63 | met |
| scoped to one case type | unified search, reference | 200 | 1790 | 1793 | 1888 | MISSED |
| scoped to one case type | unified search, words | 200 | 1719 | 1941 | 1968 | MISSED |
| scoped to one case type | case inventory report | 200 | 404 | 431 | 578 | met |
| scoped to one case type | dashboard (with the 16 indicator figures) | 200 | 1206 | 1346 | 1432 | MISSED |
| scoped to one case type | indicator figures | 200 | 865 | 989 | 1081 | MISSED |
| scoped to one case type | indicator list TOTAL_CASES | 200 | 102 | 146 | 157 | met |
| scoped to one case type | indicator list CASES_NEW | 200 | 67 | 69 | 89 | met |
| scoped to one case type | indicator list CASES_ONGOING | 200 | 89 | 93 | 119 | met |
| scoped to one case type | indicator list CASES_CONCLUDED | 200 | 88 | 89 | 131 | met |
| scoped to one case type | indicator list RESERVED_FOR_JUDGMENT | 200 | 95 | 112 | 187 | met |
| scoped to one case type | indicator list JUDGMENTS_ISSUED | 200 | 82 | 100 | 108 | met |
| scoped to one case type | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 10 | 14 | 14 | met |
| scoped to one case type | indicator list UPCOMING_HEARINGS | 200 | 116 | 307 | 323 | met |
| scoped to one case type | indicator list EXPERT_HEARINGS | 200 | 72 | 83 | 154 | met |
| scoped to one case type | indicator list APPEAL_DEADLINES | 200 | 20 | 36 | 43 | met |
| scoped to one case type | indicator list OVERDUE_TASKS | 200 | 152 | 162 | 204 | met |
| scoped to one case type | indicator list CASES_BY_UNIT | 200 | 99 | 109 | 114 | met |
| scoped to one case type | indicator list CASES_BY_MEMBER | 200 | 127 | 158 | 187 | met |
| scoped to one case type | indicator list HIGH_RISK_CASES | 200 | 325 | 350 | 516 | met |
| scoped to one case type | indicator list OVERDUE_INFO_REQUESTS | 200 | 144 | 144 | 147 | met |
| scoped to one case type | indicator list LINKED_CASES | 200 | 144 | 212 | 215 | met |
| scoped to one case type | report family CASES_BY_TYPE_COURT_STAGE | 200 | 57 | 72 | 76 | met |
| scoped to one case type | report family CASES_BY_ORG_UNIT | 200 | 70 | 84 | 96 | met |
| scoped to one case type | report family CASES_BY_MEMBER | 200 | 116 | 143 | 220 | met |
| scoped to one case type | report family OPEN_CLOSED_CASES | 200 | 59 | 59 | 61 | met |
| scoped to one case type | report family JUDGMENT_OUTCOMES | 200 | 92 | 107 | 139 | met |
| scoped to one case type | report family CLAIM_RESULTS | 200 | 67 | 74 | 122 | met |
| scoped to one case type | report family DEFENSE_POSITIONS | 200 | 82 | 85 | 121 | met |
| scoped to one case type | report family LINKED_CASES | 200 | 100 | 120 | 159 | met |
| scoped to one case type | report family ADJOURNMENTS | 200 | 64 | 72 | 79 | met |
| scoped to one case type | report family EXPERT_SESSIONS | 200 | 109 | 120 | 161 | met |
| scoped to one case type | report family DEADLINES_APPEALS | 200 | 19 | 19 | 33 | met |
| scoped to one case type | report family ENFORCEMENT | 200 | 23 | 25 | 66 | met |
| scoped to one case type | report family MISSING_EVIDENCE | 200 | 100 | 110 | 111 | met |
| scoped to one case type | report family INFO_REQUESTS | 200 | 71 | 93 | 117 | met |
| scoped to one case type | report family RISK_ASSESSMENT | 200 | 277 | 278 | 294 | met |
| scoped to one case type | report family PERFORMANCE | 200 | 224 | 231 | 270 | met |
| scoped to one case type | report family LIBRARY_PRECEDENTS | 200 | 31 | 68 | 81 | met |
| member (own cases only) | case list, first page | 200 | 44 | 49 | 56 | met |
| member (own cases only) | case list, deep page | 200 | 78 | 105 | 118 | met |
| member (own cases only) | case list, text filter | 200 | 425 | 436 | 598 | met |
| member (own cases only) | case list, status + type filter | 200 | 57 | 65 | 65 | met |
| member (own cases only) | unified search, reference | 200 | 214 | 242 | 249 | met |
| member (own cases only) | unified search, words | 200 | 250 | 268 | 281 | met |
| member (own cases only) | case inventory report | 200 | 301 | 329 | 411 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 1131 | 1339 | 1714 | MISSED |
| member (own cases only) | indicator figures | 200 | 786 | 875 | 927 | MISSED |
| member (own cases only) | indicator list TOTAL_CASES | 200 | 80 | 85 | 203 | met |
| member (own cases only) | indicator list CASES_NEW | 200 | 58 | 62 | 65 | met |
| member (own cases only) | indicator list CASES_ONGOING | 200 | 51 | 55 | 76 | met |
| member (own cases only) | indicator list CASES_CONCLUDED | 200 | 43 | 59 | 83 | met |
| member (own cases only) | indicator list RESERVED_FOR_JUDGMENT | 200 | 81 | 93 | 121 | met |
| member (own cases only) | indicator list JUDGMENTS_ISSUED | 200 | 21 | 32 | 47 | met |
| member (own cases only) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 7 | 8 | 87 | met |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 200 | 107 | 117 | 140 | met |
| member (own cases only) | indicator list EXPERT_HEARINGS | 200 | 58 | 61 | 66 | met |
| member (own cases only) | indicator list APPEAL_DEADLINES | 200 | 8 | 14 | 20 | met |
| member (own cases only) | indicator list OVERDUE_TASKS | 200 | 144 | 148 | 149 | met |
| member (own cases only) | indicator list CASES_BY_UNIT | 200 | 91 | 93 | 94 | met |
| member (own cases only) | indicator list CASES_BY_MEMBER | 200 | 151 | 167 | 280 | met |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 322 | 357 | 480 | met |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 200 | 89 | 115 | 121 | met |
| member (own cases only) | indicator list LINKED_CASES | 200 | 113 | 117 | 120 | met |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 47 | 52 | 66 | met |
| member (own cases only) | report family CASES_BY_ORG_UNIT | 200 | 64 | 78 | 95 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 40 | 46 | 54 | met |
| member (own cases only) | report family OPEN_CLOSED_CASES | 200 | 55 | 57 | 66 | met |
| member (own cases only) | report family JUDGMENT_OUTCOMES | 200 | 77 | 79 | 80 | met |
| member (own cases only) | report family CLAIM_RESULTS | 200 | 72 | 94 | 113 | met |
| member (own cases only) | report family DEFENSE_POSITIONS | 200 | 55 | 61 | 72 | met |
| member (own cases only) | report family LINKED_CASES | 200 | 81 | 87 | 111 | met |
| member (own cases only) | report family ADJOURNMENTS | 200 | 59 | 69 | 81 | met |
| member (own cases only) | report family EXPERT_SESSIONS | 200 | 98 | 113 | 114 | met |
| member (own cases only) | report family DEADLINES_APPEALS | 200 | 10 | 18 | 25 | met |
| member (own cases only) | report family ENFORCEMENT | 200 | 11 | 11 | 12 | met |
| member (own cases only) | report family MISSING_EVIDENCE | 200 | 88 | 92 | 117 | met |
| member (own cases only) | report family INFO_REQUESTS | 200 | 55 | 55 | 69 | met |
| member (own cases only) | report family RISK_ASSESSMENT | 200 | 207 | 217 | 285 | met |
| member (own cases only) | report family PERFORMANCE | 200 | 171 | 192 | 199 | met |
| member (own cases only) | report family LIBRARY_PRECEDENTS | 200 | 20 | 32 | 123 | met |

Requests over the target (median): 12 of 126
