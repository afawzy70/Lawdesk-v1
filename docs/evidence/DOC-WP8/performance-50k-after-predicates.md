# WP8 performance run

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 79702 ms
- each request: one warm-up (a request slower than 6000 ms is reported from that single run), then 3 timed runs; the table shows min / median / max in ms
- target: 500 ms (list and search)

| user | request | HTTP | min | median | max | target |
|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 195 | 230 | 288 | met |
| manager (department-wide) | case list, deep page | 200 | 283 | 299 | 302 | met |
| manager (department-wide) | case list, text filter | 200 | 13791 | 13791 | 13791 | MISSED |
| manager (department-wide) | case list, status + type filter | 200 | 114 | 132 | 218 | met |
| manager (department-wide) | unified search, reference | 200 | 37609 | 37609 | 37609 | MISSED |
| manager (department-wide) | unified search, words | 200 | 20034 | 20034 | 20034 | MISSED |
| manager (department-wide) | case inventory report | 200 | 1613 | 2026 | 2214 | MISSED |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 3193 | 3485 | 3977 | MISSED |
| manager (department-wide) | indicator figures | 200 | 2140 | 2154 | 2660 | MISSED |
| manager (department-wide) | indicator list TOTAL_CASES | 200 | 181 | 227 | 233 | met |
| manager (department-wide) | indicator list CASES_NEW | 200 | 120 | 123 | 133 | met |
| manager (department-wide) | indicator list CASES_ONGOING | 200 | 167 | 190 | 212 | met |
| manager (department-wide) | indicator list CASES_CONCLUDED | 200 | 102 | 121 | 138 | met |
| manager (department-wide) | indicator list RESERVED_FOR_JUDGMENT | 200 | 93 | 97 | 103 | met |
| manager (department-wide) | indicator list JUDGMENTS_ISSUED | 200 | 195 | 203 | 204 | met |
| manager (department-wide) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 11 | 12 | 12 | met |
| manager (department-wide) | indicator list UPCOMING_HEARINGS | 200 | 6053 | 6128 | 7107 | MISSED |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 200 | 2744 | 3336 | 4170 | MISSED |
| manager (department-wide) | indicator list APPEAL_DEADLINES | 200 | 21 | 22 | 38 | met |
| manager (department-wide) | indicator list OVERDUE_TASKS | 200 | 461 | 492 | 651 | met |
| manager (department-wide) | indicator list CASES_BY_UNIT | 200 | 370 | 431 | 457 | met |
| manager (department-wide) | indicator list CASES_BY_MEMBER | 200 | 273 | 391 | 567 | met |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 435 | 733 | 744 | MISSED |
| manager (department-wide) | indicator list OVERDUE_INFO_REQUESTS | 200 | 261 | 428 | 436 | met |
| manager (department-wide) | indicator list LINKED_CASES | 200 | 1530 | 2092 | 4265 | MISSED |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 340 | 372 | 430 | met |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 200 | 350 | 465 | 512 | met |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 179 | 694 | 1028 | MISSED |
| manager (department-wide) | report family OPEN_CLOSED_CASES | 200 | 144 | 194 | 286 | met |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 200 | 400 | 663 | 689 | MISSED |
| manager (department-wide) | report family CLAIM_RESULTS | 200 | 226 | 586 | 597 | MISSED |
| manager (department-wide) | report family DEFENSE_POSITIONS | 200 | 304 | 339 | 893 | met |
| manager (department-wide) | report family LINKED_CASES | 200 | 695 | 1048 | 1208 | MISSED |
| manager (department-wide) | report family ADJOURNMENTS | 200 | 165 | 219 | 234 | met |
| manager (department-wide) | report family EXPERT_SESSIONS | 200 | 183 | 210 | 272 | met |
| manager (department-wide) | report family DEADLINES_APPEALS | 200 | 33 | 34 | 53 | met |
| manager (department-wide) | report family ENFORCEMENT | 200 | 29 | 37 | 52 | met |
| manager (department-wide) | report family MISSING_EVIDENCE | 200 | 525 | 553 | 696 | MISSED |
| manager (department-wide) | report family INFO_REQUESTS | 200 | 658 | 818 | 1074 | MISSED |
| manager (department-wide) | report family RISK_ASSESSMENT | 200 | 611 | 1298 | 1516 | MISSED |
| manager (department-wide) | report family PERFORMANCE | 200 | 1396 | 1719 | 2461 | MISSED |
| manager (department-wide) | report family LIBRARY_PRECEDENTS | 200 | 56 | 84 | 192 | met |
| scoped to one case type | case list, first page | 200 | 488 | 566 | 1016 | MISSED |
| scoped to one case type | case list, deep page | 200 | 198 | 495 | 775 | met |
| scoped to one case type | case list, text filter | 200 | 6212 | 6212 | 6212 | MISSED |
| scoped to one case type | case list, status + type filter | 200 | 54 | 182 | 307 | met |
| scoped to one case type | unified search, reference | 200 | 5134 | 6939 | 8665 | MISSED |
| scoped to one case type | unified search, words | 200 | 6217 | 6217 | 6217 | MISSED |
| scoped to one case type | case inventory report | 200 | 607 | 1074 | 1693 | MISSED |
| scoped to one case type | dashboard (with the 16 indicator figures) | 200 | 6309 | 6309 | 6309 | MISSED |
| scoped to one case type | indicator figures | 200 | 3308 | 3376 | 4580 | MISSED |
| scoped to one case type | indicator list TOTAL_CASES | 200 | 248 | 271 | 621 | met |
| scoped to one case type | indicator list CASES_NEW | 200 | 230 | 249 | 308 | met |
| scoped to one case type | indicator list CASES_ONGOING | 200 | 317 | 339 | 476 | met |
| scoped to one case type | indicator list CASES_CONCLUDED | 200 | 170 | 174 | 321 | met |
| scoped to one case type | indicator list RESERVED_FOR_JUDGMENT | 200 | 137 | 277 | 325 | met |
| scoped to one case type | indicator list JUDGMENTS_ISSUED | 200 | 349 | 694 | 779 | MISSED |
| scoped to one case type | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 51 | 88 | 152 | met |
| scoped to one case type | indicator list UPCOMING_HEARINGS | 200 | 2980 | 3106 | 3286 | MISSED |
| scoped to one case type | indicator list EXPERT_HEARINGS | 200 | 477 | 607 | 937 | MISSED |
| scoped to one case type | indicator list APPEAL_DEADLINES | 200 | 20 | 22 | 41 | met |
| scoped to one case type | indicator list OVERDUE_TASKS | 200 | 292 | 619 | 636 | MISSED |
| scoped to one case type | indicator list CASES_BY_UNIT | 200 | 399 | 432 | 660 | met |
| scoped to one case type | indicator list CASES_BY_MEMBER | 200 | 230 | 263 | 680 | met |
| scoped to one case type | indicator list HIGH_RISK_CASES | 200 | 575 | 617 | 673 | MISSED |
| scoped to one case type | indicator list OVERDUE_INFO_REQUESTS | 200 | 495 | 765 | 816 | MISSED |
| scoped to one case type | indicator list LINKED_CASES | 200 | 459 | 513 | 709 | MISSED |
| scoped to one case type | report family CASES_BY_TYPE_COURT_STAGE | 200 | 154 | 188 | 251 | met |
| scoped to one case type | report family CASES_BY_ORG_UNIT | 200 | 469 | 690 | 873 | MISSED |
| scoped to one case type | report family CASES_BY_MEMBER | 200 | 180 | 187 | 208 | met |
| scoped to one case type | report family OPEN_CLOSED_CASES | 200 | 102 | 157 | 347 | met |
| scoped to one case type | report family JUDGMENT_OUTCOMES | 200 | 359 | 589 | 794 | MISSED |
| scoped to one case type | report family CLAIM_RESULTS | 200 | 206 | 377 | 532 | met |
| scoped to one case type | report family DEFENSE_POSITIONS | 200 | 85 | 113 | 255 | met |
| scoped to one case type | report family LINKED_CASES | 200 | 370 | 378 | 512 | met |
| scoped to one case type | report family ADJOURNMENTS | 200 | 50 | 125 | 136 | met |
| scoped to one case type | report family EXPERT_SESSIONS | 200 | 303 | 571 | 645 | MISSED |
| scoped to one case type | report family DEADLINES_APPEALS | 200 | 9 | 12 | 16 | met |
| scoped to one case type | report family ENFORCEMENT | 200 | 7 | 9 | 11 | met |
| scoped to one case type | report family MISSING_EVIDENCE | 200 | 262 | 301 | 347 | met |
| scoped to one case type | report family INFO_REQUESTS | 200 | 110 | 134 | 148 | met |
| scoped to one case type | report family RISK_ASSESSMENT | 200 | 506 | 637 | 829 | MISSED |
| scoped to one case type | report family PERFORMANCE | 200 | 309 | 814 | 1162 | MISSED |
| scoped to one case type | report family LIBRARY_PRECEDENTS | 200 | 38 | 66 | 109 | met |
| member (own cases only) | case list, first page | 200 | 300 | 489 | 664 | met |
| member (own cases only) | case list, deep page | 200 | 233 | 284 | 857 | met |
| member (own cases only) | case list, text filter | 200 | 2149 | 2243 | 2287 | MISSED |
| member (own cases only) | case list, status + type filter | 200 | 252 | 279 | 289 | met |
| member (own cases only) | unified search, reference | 200 | 388 | 774 | 926 | MISSED |
| member (own cases only) | unified search, words | 200 | 552 | 671 | 1270 | MISSED |
| member (own cases only) | case inventory report | 200 | 442 | 463 | 1101 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 3488 | 4390 | 5083 | MISSED |
| member (own cases only) | indicator figures | 200 | 2680 | 3475 | 3542 | MISSED |
| member (own cases only) | indicator list TOTAL_CASES | 200 | 264 | 302 | 332 | met |
| member (own cases only) | indicator list CASES_NEW | 200 | 215 | 283 | 482 | met |
| member (own cases only) | indicator list CASES_ONGOING | 200 | 125 | 157 | 327 | met |
| member (own cases only) | indicator list CASES_CONCLUDED | 200 | 54 | 63 | 128 | met |
| member (own cases only) | indicator list RESERVED_FOR_JUDGMENT | 200 | 157 | 264 | 282 | met |
| member (own cases only) | indicator list JUDGMENTS_ISSUED | 200 | 225 | 267 | 351 | met |
| member (own cases only) | indicator list JUDGMENTS_IN_ENFORCEMENT | 200 | 16 | 29 | 35 | met |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 200 | 814 | 1060 | 1176 | MISSED |
| member (own cases only) | indicator list EXPERT_HEARINGS | 200 | 283 | 293 | 602 | met |
| member (own cases only) | indicator list APPEAL_DEADLINES | 200 | 9 | 9 | 21 | met |
| member (own cases only) | indicator list OVERDUE_TASKS | 200 | 204 | 266 | 334 | met |
| member (own cases only) | indicator list CASES_BY_UNIT | 200 | 115 | 171 | 183 | met |
| member (own cases only) | indicator list CASES_BY_MEMBER | 200 | 189 | 232 | 300 | met |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 234 | 364 | 365 | met |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 200 | 218 | 267 | 290 | met |
| member (own cases only) | indicator list LINKED_CASES | 200 | 192 | 201 | 226 | met |
| member (own cases only) | report family CASES_BY_TYPE_COURT_STAGE | 200 | 64 | 81 | 111 | met |
| member (own cases only) | report family CASES_BY_ORG_UNIT | 200 | 76 | 130 | 207 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 83 | 94 | 165 | met |
| member (own cases only) | report family OPEN_CLOSED_CASES | 200 | 89 | 112 | 131 | met |
| member (own cases only) | report family JUDGMENT_OUTCOMES | 200 | 87 | 102 | 111 | met |
| member (own cases only) | report family CLAIM_RESULTS | 200 | 64 | 70 | 70 | met |
| member (own cases only) | report family DEFENSE_POSITIONS | 200 | 65 | 71 | 71 | met |
| member (own cases only) | report family LINKED_CASES | 200 | 128 | 133 | 211 | met |
| member (own cases only) | report family ADJOURNMENTS | 200 | 47 | 50 | 62 | met |
| member (own cases only) | report family EXPERT_SESSIONS | 200 | 131 | 141 | 169 | met |
| member (own cases only) | report family DEADLINES_APPEALS | 200 | 13 | 14 | 18 | met |
| member (own cases only) | report family ENFORCEMENT | 200 | 8 | 10 | 22 | met |
| member (own cases only) | report family MISSING_EVIDENCE | 200 | 82 | 88 | 108 | met |
| member (own cases only) | report family INFO_REQUESTS | 200 | 76 | 83 | 88 | met |
| member (own cases only) | report family RISK_ASSESSMENT | 200 | 226 | 261 | 339 | met |
| member (own cases only) | report family PERFORMANCE | 200 | 236 | 282 | 296 | met |
| member (own cases only) | report family LIBRARY_PRECEDENTS | 200 | 114 | 117 | 426 | met |

Requests over the target (median): 43 of 126
