# Checkpoint — independent QA

Application/UAT SHA: `352277571426bd4d135ea4acb0e12489aa09fc3c`. QA test source checkpoint: `708875d5e888fd23d25983a0e5b9770c208c403d`; final evidence commit follows. UAT journeys 14/14 script-pass (611 assertions) with release before/after each. Browser Chromium read-only smoke: 32/32 rows, same SHA; Firefox/WebKit blocked. Coverage generated: PASS 17, FAIL 0, BLOCKED 45, NOT_TESTED 541, PARTIAL 47. See `coverage-summary.json` and `SCENARIOS.md`.

Completed isolated checks: `CaseBoundEndpointsAccessTest` 2 passed with `bodyNotBuilt=[]`, 15 handlers unresolved; 50k performance harness completed and wrote `performance-50k.md`; QA unit tests 6 passed; tool tests 180 passed with local sockets. `npm run build` passed.

In progress at checkpoint: full frontend Vitest rerun (`/tmp/lawdesk-qa-frontend-tests-20261110.log`), current dump restore drill (`/tmp/lawdesk-qa-restore-20261110.log`), restricted-role/migration/time zone/export API tests. These are not promoted to acceptance until the result and its SHA are recorded. Prior export timeout and pre-V800 backup recovery remain open retest targets.

No credentials, cookies or real case content should enter the commit. The `runs/*.log` files are intentionally ignored; the JSON keeps redacted step assertions. `GET /api/v1/release` does not expose deployment time. Do not run the historical placeholder `receive` command or rotate QA accounts silently.
