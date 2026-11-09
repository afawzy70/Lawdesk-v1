# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 4b12329 (stage 1: search + trigram)
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 332929 ms
- host load average at start / end of the measurement: 13.00 (1 min, 8 CPUs) / 11.60 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 8 parallel clients x 5 requests each; p50 and p95 over all 40 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 1 | 152 | 266 | 464 | 464 | 397 | 775 | met |
| manager (department-wide) | case list, deep page | 200 | 1 | 141 | 208 | 527 | 527 | 470 | 1086 | MISSED |
| manager (department-wide) | case list, text filter | 200 | 1 | 544 | 764 | 1757 | 1757 | 1245 | 2271 | MISSED |
| manager (department-wide) | case list, status + type filter | 200 | 1 | 45 | 87 | 279 | 279 | 125 | 565 | met |
| manager (department-wide) | unified search, reference | 200 | 4 | 546 | 799 | 1926 | 1926 | 1383 | 1748 | MISSED |
| manager (department-wide) | unified search, words | 200 | 3 | 219 | 381 | 578 | 578 | 681 | 1216 | MISSED |
| manager (department-wide) | case inventory report | 200 | 5 | 1987 | 3019 | 4022 | 4022 | - | - | MISSED |
| manager (department-wide) | case inventory report, text filter | 200 | 5 | 91 | 134 | 316 | 316 | 358 | 636 | met |
| member (own cases only) | case list, first page | 200 | 1 | 44 | 114 | 368 | 368 | 147 | 523 | met |
| member (own cases only) | case list, deep page | 200 | 2 | 76 | 122 | 443 | 443 | 167 | 573 | met |
| member (own cases only) | case list, text filter | 200 | 1 | 461 | 680 | 1513 | 1513 | 1061 | 1638 | MISSED |
| member (own cases only) | case list, status + type filter | 200 | 2 | 52 | 58 | 213 | 213 | 140 | 232 | met |
| member (own cases only) | unified search, reference | 200 | 3 | 114 | 172 | 753 | 753 | 457 | 1058 | MISSED |
| member (own cases only) | unified search, words | 200 | 3 | 88 | 135 | 799 | 799 | 326 | 1023 | MISSED |
| member (own cases only) | case inventory report | 200 | 5 | 171 | 434 | 748 | 748 | 447 | 702 | MISSED |
| member (own cases only) | case inventory report, text filter | 200 | 5 | 79 | 108 | 294 | 294 | 182 | 409 | met |
| no scope | case list, first page | 200 | 2 | 64 | 93 | 224 | 224 | 122 | 816 | met |
| no scope | case list, deep page | 200 | 2 | 60 | 86 | 191 | 191 | 223 | 421 | met |
| no scope | case list, text filter | 200 | 2 | 896 | 1349 | 2627 | 2627 | 2268 | 3019 | MISSED |
| no scope | case list, status + type filter | 200 | 2 | 46 | 73 | 457 | 457 | 185 | 569 | met |
| no scope | unified search, reference | 200 | 3 | 37 | 42 | 193 | 193 | 85 | 507 | met |
| no scope | unified search, words | 200 | 3 | 35 | 59 | 217 | 217 | 130 | 311 | met |
| no scope | case inventory report | 200 | 4 | 116 | 201 | 671 | 671 | 280 | 858 | MISSED |
| no scope | case inventory report, text filter | 200 | 4 | 72 | 93 | 367 | 367 | 226 | 440 | met |
| manager with SEARCH_ALL | unified search, reference | 200 | 14 | 413 | 640 | 1586 | 1586 | 938 | 1419 | MISSED |
| manager with SEARCH_ALL | unified search, words | 200 | 13 | 205 | 376 | 1076 | 1076 | 514 | 855 | MISSED |

Requests over the target (sequential p95): 13 of 26
