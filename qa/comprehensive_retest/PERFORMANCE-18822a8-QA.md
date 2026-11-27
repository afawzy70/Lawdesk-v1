# Independent 50,000-case timing checkpoint — source 18822a8

Run on 10 November 2026 in throwaway embedded PostgreSQL through MockMvc, using the app source at `18822a83bff8e2a38b83db4370674a9f69919d81`. Synthetic corpus: 50,000 cases and 584,999 child rows; seed time 375,060 ms. The report and all 225 sequential samples are `performance-50k-18822a8.md` and `performance-50k-18822a8.raw.csv`. Fifteen timed requests followed one warm-up for each row; p95 is nearest-rank. The concurrency column uses 8 clients × 5 requests. Host 1-minute load was 3.31 before and 3.63 after on 8 CPUs. Actor scopes: manager `ALL`, member own cases, and no scope. HTTP was 200 for all 15 measured rows; SQL statement counts are in the raw report.

| Actor | Endpoint | p50 ms | p95 ms | Concurrent p95 ms | Proposed 500 ms list/search target |
|---|---|---:|---:|---:|---|
| Manager, department-wide | case list first page | 205 | 429 | 506 | within sequential proposal |
| Manager, department-wide | case list deep page | 186 | 362 | 461 | within sequential proposal |
| Manager, department-wide | case list text filter | 127 | **545** | 674 | above sequential proposal |
| Member, own cases | case list first/deep/text | 80/103/74 | 192/172/219 | 133/332/195 | within sequential proposal |
| No scope | case list first/deep/text | 82/85/98 | 362/273/322 | 241/255/682 | within sequential proposal; text concurrent tail high |
| Manager | dashboard / indicator figures | 2152/1267 | 3152/1980 | 3132/1655 | **no approved 500 ms limit for these screens** |
| Member | dashboard / indicator figures | 1270/933 | 1919/1546 | 2266/1200 | no approved 500 ms limit |
| No scope | dashboard / indicator figures | 839/674 | 1229/879 | 1025/1829 | no approved 500 ms limit |

The harness marks 7/15 rows `MISSED` because it mechanically applies 500 ms to every endpoint. **Only one of those seven is within the proposed list/search scope:** manager text-filtered case list at p95 545 ms. The 500 ms threshold itself remains a proposal, not owner-approved acceptance for all UI. Unified search was not included in this focused rerun, and no export request is part of these 15 rows.

The prior QA run at source `3522775` reported manager first/deep list p95 699/623 ms, but its host load was 24.98/14.80. The new lower p95 values are observations, **not a causal attribution** to PR #101. Cache warmth was not captured; the harness did one warm-up but did not distinguish database cold cache or browser cache. CPU/RSS per process, exact index plans and write-path cost were not independently recorded in this run. It checks HTTP status and timing, not payload totals, sort/pagination or scope correctness on the 50,000-case corpus. A separate UAT WP8 journey on 511 cases checked 46 indicator group/list equalities for the manager, and isolated restricted-role/schema tests passed, but these do not substitute for large-corpus correctness or write overhead. QA-RT-007 remains open pending those checks and a more controlled same-host comparison. No UAT load test occurred.
