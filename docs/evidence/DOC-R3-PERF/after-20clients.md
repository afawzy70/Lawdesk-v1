# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 735ff56
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 3 ms
- host load average at start / end of the measurement: 9.03 (1 min, 8 CPUs) / 12.79 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 20 parallel clients x 5 requests each; p50 and p95 over all 100 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 1 | 132 | 161 | 292 | 292 | 701 | 1077 | met |
| manager (department-wide) | case inventory report | 200 | 4 | 302 | 377 | 926 | 926 | 1558 | 2466 | MISSED |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 22 | 1252 | 1622 | 2199 | 2199 | 4068 | 5645 | MISSED |
| manager (department-wide) | indicator figures | 200 | 15 | 823 | 1029 | 1466 | 1466 | 3118 | 3986 | MISSED |
| manager (department-wide) | indicator list HIGH_RISK_CASES | 200 | 2 | 148 | 313 | 1150 | 1150 | 1253 | 2594 | MISSED |
| manager (department-wide) | report family CASES_BY_MEMBER | 200 | 2 | 111 | 170 | 522 | 522 | 476 | 984 | MISSED |
| manager (department-wide) | report family PERFORMANCE | 200 | 6 | 202 | 280 | 411 | 411 | 957 | 1698 | met |
| member (own cases only) | case list, first page | 200 | 1 | 40 | 59 | 93 | 93 | 242 | 523 | met |
| member (own cases only) | case inventory report | 200 | 4 | 116 | 144 | 221 | 221 | 642 | 1181 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 22 | 851 | 979 | 1290 | 1290 | 3015 | 4076 | MISSED |
| member (own cases only) | indicator figures | 200 | 15 | 549 | 750 | 949 | 949 | 1783 | 2989 | MISSED |
| member (own cases only) | indicator list HIGH_RISK_CASES | 200 | 2 | 139 | 180 | 261 | 261 | 509 | 916 | met |
| member (own cases only) | report family CASES_BY_MEMBER | 200 | 2 | 34 | 53 | 112 | 112 | 137 | 473 | met |
| member (own cases only) | report family PERFORMANCE | 200 | 6 | 145 | 181 | 233 | 233 | 533 | 886 | met |
| no scope | case list, first page | 200 | 2 | 58 | 71 | 118 | 118 | 220 | 441 | met |
| no scope | case inventory report | 200 | 3 | 88 | 113 | 233 | 233 | 337 | 523 | met |
| no scope | dashboard (with the 16 indicator figures) | 200 | 22 | 525 | 649 | 871 | 871 | 1803 | 2970 | MISSED |
| no scope | indicator figures | 200 | 15 | 352 | 409 | 519 | 519 | 997 | 1701 | MISSED |
| no scope | indicator list HIGH_RISK_CASES | 200 | 1 | 34 | 42 | 77 | 77 | 124 | 234 | met |
| no scope | report family CASES_BY_MEMBER | 200 | 1 | 31 | 36 | 61 | 61 | 104 | 177 | met |
| no scope | report family PERFORMANCE | 200 | 5 | 100 | 116 | 139 | 139 | 319 | 506 | met |

Requests over the target (sequential p95): 9 of 21
