# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 18822a83bff8e2a38b83db4370674a9f69919d81
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 375060 ms
- host load average at start / end of the measurement: 3.31 (1 min, 8 CPUs) / 3.63 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 8 parallel clients x 5 requests each; p50 and p95 over all 40 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | case list, first page | 200 | 1 | 135 | 205 | 429 | 429 | 251 | 506 | met |
| manager (department-wide) | case list, deep page | 200 | 1 | 135 | 186 | 362 | 362 | 205 | 461 | met |
| manager (department-wide) | case list, text filter | 200 | 2 | 69 | 127 | 545 | 545 | 185 | 674 | MISSED |
| manager (department-wide) | dashboard (with the 16 indicator figures) | 200 | 22 | 1585 | 2152 | 3152 | 3152 | 2098 | 3132 | MISSED |
| manager (department-wide) | indicator figures | 200 | 15 | 1017 | 1267 | 1980 | 1980 | 1085 | 1655 | MISSED |
| member (own cases only) | case list, first page | 200 | 1 | 53 | 80 | 192 | 192 | 65 | 133 | met |
| member (own cases only) | case list, deep page | 200 | 2 | 81 | 103 | 172 | 172 | 100 | 332 | met |
| member (own cases only) | case list, text filter | 200 | 2 | 44 | 74 | 219 | 219 | 70 | 195 | met |
| member (own cases only) | dashboard (with the 16 indicator figures) | 200 | 22 | 968 | 1270 | 1919 | 1919 | 1260 | 2266 | MISSED |
| member (own cases only) | indicator figures | 200 | 15 | 757 | 933 | 1546 | 1546 | 693 | 1200 | MISSED |
| no scope | case list, first page | 200 | 2 | 66 | 82 | 362 | 362 | 106 | 241 | met |
| no scope | case list, deep page | 200 | 2 | 64 | 85 | 273 | 273 | 103 | 255 | met |
| no scope | case list, text filter | 200 | 3 | 70 | 98 | 322 | 322 | 95 | 682 | met |
| no scope | dashboard (with the 16 indicator figures) | 200 | 22 | 639 | 839 | 1229 | 1229 | 629 | 1025 | MISSED |
| no scope | indicator figures | 200 | 15 | 460 | 674 | 879 | 879 | 693 | 1829 | MISSED |

Requests over the target (sequential p95): 7 of 15
