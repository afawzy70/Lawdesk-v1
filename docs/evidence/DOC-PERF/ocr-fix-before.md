# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 3937a50 (develop, before V844)
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 370996 ms
- host load average at start / end of the measurement: 3.28 (1 min, 8 CPUs) / 4.95 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 8 parallel clients x 5 requests each; p50 and p95 over all 40 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | unified search, reference | 200 | 4 | 85 | 111 | 224 | 224 | 133 | 366 | met |
| manager (department-wide) | unified search, words | 200 | 3 | 33 | 41 | 60 | 60 | 83 | 395 | met |
| member (own cases only) | unified search, reference | 200 | 3 | 66 | 90 | 200 | 200 | 80 | 212 | met |
| member (own cases only) | unified search, words | 200 | 3 | 30 | 35 | 69 | 69 | 32 | 83 | met |
| no scope | unified search, reference | 200 | 3 | 49 | 58 | 90 | 90 | 90 | 183 | met |
| no scope | unified search, words | 200 | 3 | 45 | 51 | 108 | 108 | 54 | 108 | met |
| manager with SEARCH_ALL | unified search, reference | 200 | 14 | 203 | 221 | 365 | 365 | 233 | 303 | met |
| manager with SEARCH_ALL | unified search, words | 200 | 13 | 134 | 216 | 507 | 507 | 220 | 442 | MISSED |

Requests over the target (sequential p95): 1 of 8
