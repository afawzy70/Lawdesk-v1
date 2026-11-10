# Checkpoint — independent QA

Application/UAT SHA: `352277571426bd4d135ea4acb0e12489aa09fc3c`. QA test source checkpoint: `708875d5e888fd23d25983a0e5b9770c208c403d`; final evidence commit follows. UAT journeys 14/14 script-pass (611 assertions) with release before/after each. Browser Chromium read-only smoke: 32/32 rows, same SHA; Firefox/WebKit blocked. Coverage generated: PASS 17, FAIL 0, BLOCKED 45, NOT_TESTED 541, PARTIAL 47. See `coverage-summary.json` and `SCENARIOS.md`.

Completed isolated checks: `CaseBoundEndpointsAccessTest` 2 passed with `bodyNotBuilt=[]`, 15 handlers unresolved; 50k performance harness completed and wrote `performance-50k.md`; QA unit tests 6 passed; tool tests 180 passed with local sockets. `npm run build` passed.

Completed after the first checkpoint: 47 focused Backend tests, two pinned Kuwait clock tests, and current-dump restore drill passed; see `isolated-test-results.json` and `restore-current/drill-result.json`. Full frontend Vitest ended 939/940 with one 5 s timeout during host load; targeted reruns 31/31 passed. `develop` advanced from the UAT SHA to `aea33fa3d8e1f92a5b8bde5ad6f34c99e4bf1bfa` after the UAT run; the release still reported `3522775` at 04:11 UTC. Prior 50k export timeout, other day-boundary workflows, and pre-V800 backup recovery remain open retest targets.

Second checkpoint: a simulated pre-V800 custom dump containing one synthetic library judgment failed restoration as predicted; a one-function SQL correction restored it into an empty throwaway database with zero data/permissions/file fingerprint differences. The real historical backup remains untested. A QA-only 50k export replay is running in another throwaway database (`/tmp/lawdesk-qa-export-50k-20261110.log`); it has no acceptance result yet. Draft PR #104 is open to `develop`, unmerged.

No credentials, cookies or real case content should enter the commit. The `runs/*.log` files are intentionally ignored; the JSON keeps redacted step assertions. `GET /api/v1/release` does not expose deployment time. Do not run the historical placeholder `receive` command or rotate QA accounts silently.
