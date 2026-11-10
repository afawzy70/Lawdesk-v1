# QA-RT-011 — developer re-check on UAT 18822a83bff8e2a38b83db4370674a9f69919d81

Developer evidence, not QA acceptance. Same account (qa.manager) and same synthetic SYNTH-DOC case as the reproduction on 03ff08e.

| Test | 03ff08e (before) | 18822a8 (after) |
|---|---|---|
| curl, 84 simultaneous GETs of the case-page endpoints, HTTP/2 | 58 × HTTP 500 | 0 × 5xx in 3 rounds (252 requests) |
| same, HTTP/1.1 (up to 100 connections) | 11 × HTTP 500 | 0 × 5xx in 3 rounds (252 requests) |
| Chromium 156 (headless shell), login then 4 case-page loads alternating en/ar, 1366 px | 76 × 5xx of 154 API responses (2 loads) | 0 × 5xx of 304 API responses (4 loads), two runs |

Limits: only 5xx were counted (no field-by-field check of the data shown); one account, one case, one browser engine and width; not the Codex 32-row matrix; no UAT application, proxy or database logs were available to the developer, so the cause rests on the isolated measurements in `docs/evidence/DOC-R3-RT011/`. The 14 developer journeys also pass on this SHA (`SUMMARY-18822a8.txt`).
