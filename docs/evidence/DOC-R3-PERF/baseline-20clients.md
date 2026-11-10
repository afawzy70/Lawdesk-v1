# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 3522775
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 7 ms
- host load average at start / end of the measurement: 11.99 (1 min, 8 CPUs) / 11.61 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 20 parallel clients x 5 requests each; p50 and p95 over all 100 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 1 | 136 | 194 | 258 | 258 | 725 | 1062 | met |
| manager (department-wide) | case inventory report | 200 | 4 | 317 | 343 | 499 | 499 | 1333 | 1870 | met |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 24 | 1320 | 1613 | 3075 | 3075 | 4613 | 5996 | MISSED |
| manager (department-wide) | indicator figures | 200 | 15 | 843 | 958 | 1628 | 1628 | 3304 | 4298 | MISSED |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 2 | 302 | 406 | 801 | 801 | 1166 | 1608 | MISSED |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 197 | 144 | 267 | 396 | 396 | 470 | 751 | met |
| manager (department-wide) | report family PERFORMANCE | 200 | 201 | 350 | 389 | 758 | 758 | 919 | 1332 | MISSED |
| member (own cases only) | case list, first page | 200 | 1 | 40 | 47 | 72 | 72 | 232 | 406 | met |
| member (own cases only) | case inventory report | 200 | 4 | 121 | 174 | 314 | 314 | 398 | 712 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 24 | 927 | 1053 | 1239 | 1239 | 3150 | 4042 | MISSED |
| member (own cases only) | indicator figures | 200 | 15 | 633 | 746 | 1003 | 1003 | 2541 | 4014 | MISSED |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 2 | 262 | 292 | 494 | 494 | 1488 | 2348 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 3 | 37 | 47 | 161 | 161 | 183 | 392 | met |
| member (own cases only) | report family PERFORMANCE | 200 | 7 | 161 | 197 | 292 | 292 | 928 | 1540 | met |
| no scope | case list, first page | 200 | 2 | 54 | 69 | 93 | 93 | 349 | 586 | met |
| no scope | case inventory report | 200 | 3 | 86 | 104 | 164 | 164 | 312 | 612 | met |
| no scope | dashboard (with the 16 indicator figures) | 200 | 24 | 710 | 814 | 1111 | 1111 | 2859 | 4176 | MISSED |
| no scope | indicator figures | 200 | 15 | 416 | 572 | 625 | 625 | 1714 | 2609 | MISSED |
| no scope | indicator list HIGH_RISK_CASES | 200 | 1 | 128 | 161 | 265 | 265 | 646 | 922 | met |
| no scope | report family CASES_BY_MEMBER | 200 | 1 | 28 | 36 | 264 | 264 | 105 | 227 | met |
| no scope | report family PERFORMANCE | 200 | 5 | 112 | 126 | 165 | 165 | 406 | 623 | met |

Requests over the target (sequential p95): 8 of 21
