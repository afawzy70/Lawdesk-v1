# Phase 1 — refined acceptance criteria (PROPOSED, awaiting product/QA approval)

`product/stories.json` holds Phase 1 stories with templated criteria (`DRAFT_NEEDS_REFINEMENT`), and the matching
`qa/test-cases.json` entries are generic scenarios. This file proposes concrete, testable criteria and maps each
QA case to an executable check. The baseline files are not edited here; product/QA owns them and can adopt this
mapping in a separate pull request. Status values: `AUTOMATED` (test exists and was run by the developer),
`BLOCKED` (cannot be executed yet, reason given), `N/A` (scenario does not apply; reviewer to confirm).
Developer runs are not independent QA: every case stays `NOT_RUN` in `qa/test-cases.json` until QA executes it.

## LD-001 Repository conventions and branch policy

Acceptance criteria:

1. The repository has a documented layout and contribution rules (`CONTRIBUTING.md`), editor/line-ending
   settings, ignore rules that exclude secrets and build output, code owners and a pull request template that
   asks for story IDs, QA case IDs, TDD evidence and test results.
2. Branch names are `main`, `develop` or `<prefix>/<slug>` with prefix in feature, fix, docs, qa, chore, release, hotfix.
   Anything else is rejected with a message naming the allowed prefixes and an example.
3. Commit subjects follow Conventional Commits (`type(scope): summary`, ≤ 72 chars); git/GitHub merge and revert
   subjects are accepted. Violations are rejected with an actionable message.
4. Pull requests from work branches may target only `develop`; only `release/*` and `hotfix/*` may target `main`.
5. The checks run locally through opt-in git hooks and in CI on every push and pull request (CI wiring in LD-005).
6. Direct pushes to `main`/`develop` and self-approval are prevented by GitHub branch protection — **blocked**
   (B4: unavailable for this private repository on the current GitHub plan).

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0001 | authorized happy path | Valid branch/commit/PR accepted — `BranchNameTest.test_accepts_*`, `CommitMessageTest.test_accepts_*`, `PullRequestFlowTest.test_work_branches_target_develop`, layout test | AUTOMATED |
| QA-0002 | required-field validation | Empty branch name, empty/comment-only commit message rejected | AUTOMATED |
| QA-0003 | invalid input boundary | 73+ char subject, malformed/traversal branch names, unknown prefix | AUTOMATED |
| QA-0004 | unauthenticated access | Direct push to protected branch must be refused by GitHub | BLOCKED (B4) |
| QA-0005 | insufficient permission | `feature/*` → `main` rejected; unknown base rejected | AUTOMATED |
| QA-0006 | record integrity and rollback | Checks are read-only; layout check on empty dir lists all missing files and creates nothing | AUTOMATED |
| QA-0007 | concurrent or repeat request | Checks are pure functions; repeated CLI invocations give the same exit code | AUTOMATED (CLI tests) |
| QA-0008 | API-to-UI state and error handling | No API/UI in this story; CLI exit codes 0/1/2 and stderr messages are the interface | AUTOMATED (CLI tests) |

## LD-002 … LD-006

Refined in each story's kickoff commit before its RED step.
