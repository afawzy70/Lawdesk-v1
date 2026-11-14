# Test results — tested SHA `a2a76e49446e38db60137f2a16ff1fb35798d51b`

Taken from the process exit codes and reports of one run on that exact commit (the commit after it only adds this file and the README of this folder).
A green run is not QA acceptance.

| Check | Command | Exit code | Result |
|---|---|---|---|
| Backend, all tests | `./mvnw test` (backend) | 0 | 1456 run, 0 failures, 0 errors, 5 skipped, BUILD SUCCESS |
| Frontend, all tests | `npx vitest run` | 0 | 118 files, 1122 tests, all passed |
| Types | `npx tsc -p tsconfig.app.json --noEmit` | 0 | clean |
| Lint | `npx eslint --max-warnings=0 src` | 0 | clean |
| Register | `python3 docs/requirements/build_matrix.py --check` | 0 | in sync |

The 5 skipped backend tests are the OCR tests that need Tesseract (`TesseractOcrEngineTest` 1, `OcrQualityTest` 1, `OcrRealEngineApiTest` 3); the OCR change is not part of this branch (PR #107).
No timeout was raised. Earlier on `61b9a80`: backend 1455 run with 1 failure (`CaseVisibilityApiTest`, the isolation defect, fixed on this branch), frontend 1118/1120 (two list-wiring tests whose only dropdowns were on the removed /deadlines register; recorded as label-only displays in `catalog.test.tsx`).
Affected tests in both orders: `CaseBoundEndpointsAccessTest` + `CaseVisibilityApiTest`, `-Dsurefire.runOrder=reversealphabetical` and `alphabetical`: both rc=0.
Local journeys: 621 steps, 0 failures (README of this folder). Counsel drill: 12/12.
