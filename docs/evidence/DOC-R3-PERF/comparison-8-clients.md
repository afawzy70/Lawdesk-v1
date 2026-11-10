| user | request | SQL | p50 before | p50 after | p95 before | p95 after | 8 clients p95 before | 8 clients p95 after |
|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 1 -> 1 | 257 | 163 | 516 | 344 | 618 | 525 |
| manager (department-wide) | case inventory report | 4 -> 4 | 410 | 310 | 860 | 429 | 1129 | 705 |
| manager (department-wide) | case inventory report, text filter | 7 -> 7 | 310 | 151 | 412 | 250 | 1158 | 342 |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 24 -> 22 | 1898 | 1271 | 3267 | 1888 | 4264 | 2480 |
| manager (department-wide) | indicator figures | 15 -> 15 | 1284 | 911 | 2514 | 1206 | 2571 | 1784 |
| manager (department-wide) | indicator list TOTAL_CASES | 2 -> 2 | 182 | 156 | 450 | 283 | 664 | 438 |
| manager (department-wide) | indicator list EXPERT_HEARINGS | 3 -> 3 | 194 | 53 | 915 | 245 | 733 | 150 |
| manager (department-wide) | indicator list OVERDUE_TASKS | 3 -> 3 | 230 | 159 | 822 | 207 | 624 | 343 |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 2 -> 2 | 329 | 184 | 445 | 299 | 854 | 528 |
| manager (department-wide) | report family CASES_BY_TYPE_COURT_STAGE | 1 -> 1 | 82 | 75 | 606 | 136 | 337 | 161 |
| manager (department-wide) | report family CASES_BY_ORG_UNIT | 1 -> 1 | 178 | 145 | 423 | 256 | 677 | 379 |
| manager (department-wide) | report family CASES_BY_MEMBER | 197 -> 2 | 269 | 115 | 483 | 170 | 628 | 295 |
| manager (department-wide) | report family JUDGMENT_OUTCOMES | 1 -> 1 | 182 | 141 | 671 | 186 | 1145 | 729 |
| manager (department-wide) | report family CLAIM_RESULTS | 2 -> 2 | 218 | 148 | 619 | 252 | 739 | 342 |
| manager (department-wide) | report family DEFENSE_POSITIONS | 1 -> 1 | 166 | 110 | 622 | 199 | 617 | 325 |
| manager (department-wide) | report family ADJOURNMENTS | 1 -> 1 | 77 | 53 | 506 | 88 | 477 | 124 |
| manager (department-wide) | report family INFO_REQUESTS | 1 -> 1 | 204 | 195 | 742 | 293 | 927 | 591 |
| manager (department-wide) | report family RISK_ASSESSMENT | 2 -> 2 | 510 | 173 | 1129 | 232 | 1605 | 514 |
| manager (department-wide) | report family PERFORMANCE | 201 -> 6 | 548 | 242 | 1088 | 482 | 1546 | 633 |
| member (own cases only) | case list, deep page | 2 -> 2 | 391 | 75 | 637 | 128 | 876 | 309 |
| member (own cases only) | case list, text filter | 2 -> 2 | 241 | 58 | 588 | 85 | 673 | 164 |
| member (own cases only) | case inventory report | 4 -> 4 | 399 | 135 | 575 | 193 | 1072 | 280 |
| member (own cases only) | case inventory report, text filter | 7 -> 7 | 240 | 132 | 626 | 182 | 872 | 239 |
| member (own cases only) | dashboard (with the 16 indicator figures) | 24 -> 22 | 1655 | 754 | 3163 | 900 | 5173 | 1399 |
| member (own cases only) | indicator figures | 15 -> 15 | 1167 | 550 | 1593 | 880 | 3364 | 1196 |
| member (own cases only) | indicator list UPCOMING_HEARINGS | 3 -> 3 | 236 | 140 | 458 | 184 | 1117 | 388 |
| member (own cases only) | indicator list HIGH_RISK_CASES | 2 -> 2 | 399 | 133 | 645 | 173 | 1376 | 339 |
| member (own cases only) | indicator list OVERDUE_INFO_REQUESTS | 3 -> 3 | 303 | 87 | 544 | 132 | 1148 | 283 |
| member (own cases only) | report family RISK_ASSESSMENT | 2 -> 2 | 210 | 112 | 461 | 188 | 902 | 356 |
| member (own cases only) | report family PERFORMANCE | 7 -> 6 | 221 | 168 | 446 | 305 | 764 | 851 |
| no scope | dashboard (with the 16 indicator figures) | 24 -> 22 | 945 | 662 | 1188 | 764 | 2766 | 1330 |
| no scope | indicator figures | 15 -> 15 | 537 | 368 | 771 | 575 | 1894 | 1054 |
| no scope | indicator list TOTAL_CASES | 1 -> 1 | 38 | 93 | 78 | 431 | 209 | 267 |
| no scope | indicator list CASES_ONGOING | 1 -> 1 | 46 | 55 | 72 | 643 | 140 | 430 |
| no scope | report family DEFENSE_POSITIONS | 1 -> 1 | 37 | 44 | 87 | 564 | 173 | 295 |
| manager with SEARCH_ALL | unified search, reference | 14 -> 14 | 431 | 99 | 670 | 171 | 1015 | 256 |

Sequential p95 over 500 ms: before 25 of 131, after 8 of 131
