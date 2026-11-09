# Performance run (LD-058)

Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.

- code version (git SHA): 1740c0d (with V844)
- cases: 50000 (requested 50000)
- child rows (proceeding=75000, judgment=30000, hearing=150000, task=75000, claim=50000, defense=50000, evidence_item=50000, info_request=20000, risk_assessment=25000, case_link=9999, case_org_involvement=50000): 584999
- seeding took 340050 ms
- host load average at start / end of the measurement: 3.07 (1 min, 8 CPUs) / 3.07 (1 min, 8 CPUs)
- sequential: one warm-up (a request slower than 6000 ms is reported from that single run), then 15 timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request
- concurrent: 8 parallel clients x 5 requests each; p50 and p95 over all 40 requests
- target: 500 ms (list and search), judged on the sequential p95

| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |
|---|---|---|---|---|---|---|---|---|---|---|
| manager (department-wide) | unified search, reference | 200 | 4 | 95 | 115 | 229 | 229 | 152 | 300 | met |
| manager (department-wide) | unified search, words | 200 | 3 | 38 | 58 | 273 | 273 | 48 | 96 | met |
| member (own cases only) | unified search, reference | 200 | 3 | 70 | 138 | 478 | 478 | 151 | 529 | met |
| member (own cases only) | unified search, words | 200 | 3 | 31 | 71 | 161 | 161 | 62 | 256 | met |
| no scope | unified search, reference | 200 | 3 | 48 | 60 | 125 | 125 | 67 | 125 | met |
| no scope | unified search, words | 200 | 3 | 49 | 67 | 117 | 117 | 91 | 484 | met |
| manager with SEARCH_ALL | unified search, reference | 200 | 14 | 216 | 286 | 480 | 480 | 278 | 688 | met |
| manager with SEARCH_ALL | unified search, words | 200 | 13 | 139 | 186 | 462 | 462 | 216 | 434 | met |

Requests over the target (sequential p95): 0 of 8
