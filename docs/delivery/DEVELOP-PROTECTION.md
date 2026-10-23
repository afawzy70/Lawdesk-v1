# Protecting `develop` without branch protection (B4)

**Status:** APPROVED (owner, 2026-10-23) as an **additional** procedural and CI control. It does **not** replace
server-enforced GitHub branch protection, which remains the target if the plan ever allows it. The repository stays
private.

## Development/UAT policy (owner decision 2026-10-23)

For the development/UAT environment, merged pull requests deploy without waiting for a QA verdict, and Codex verifies
in parallel. Set the repository variable `LAWDESK_DEVELOP_POLICY=uat` to switch to it. The guard then keeps only its
structural checks: no force push, exactly one merge per push, and a real pull-request merge into develop. It no longer
requires `QA-VERDICT` or `RECOVER` lines. When the variable is unset, the strict `qa` policy below applies again. Every
test for the strict policy is kept.

## Why a procedure

- GitHub refuses branch protection and rulesets for this private repository on its current plan (`HTTP 403: Upgrade
  to GitHub Pro or make this repository public`).
- Every actor works through the same account (`afawzy70`): the owner, the developer agent (Claude) and the QA agent
  (Codex). GitHub therefore cannot tell who pushed, merged or commented.

So `develop` cannot be locked. Instead, every change to it must follow the procedure below, and a CI check verifies each
push automatically. A push that breaks the procedure is flagged and **never deploys**.

## The rules

1. **No direct pushes to `develop` or `main`, and no force pushes.** Every change arrives through a pull request.
   - The agents' local hooks already refuse commits on `develop` and `main` (`tools/repo_policy.py`).
2. **QA clears the exact head.**
   - QA (Codex) posts on the pull request a line that starts exactly with `QA-VERDICT: PASS <40-character head SHA>`.
   - To withdraw a pass, QA posts `QA-VERDICT: BLOCKED <sha>`. The latest verdict for a SHA counts.
   - Only verdicts written, and last edited, **before the merge** count. Lines inside code blocks or quotes do not.
   - A new push to the pull request creates a new head, which needs its own verdict.
   - Only QA posts `QA-VERDICT:` lines. The developer agent never does.
3. **Only the owner merges**, and only when all of these hold:
   - CI is green.
   - The head SHA shown on the pull request equals the SHA in QA's latest `PASS` verdict.
   - The merge uses **Create a merge commit** or **Squash and merge**. Rebase merging puts several commits on
     `develop` in one push, and the guard rejects that.
   - The agents never merge.
4. **One merge per push.** Never push merges or commits to `develop` from a local clone.

## What enforces it

The `develop-guard` CI job (`tools/develop_guard.py`) runs on every push to `develop`. It fails when any of these is
true:

| Check | Catches |
|-------|---------|
| `github.event.forced` is true | force push |
| the pushed commit's first parent ≠ the `develop` head before the push | several commits or merges in one push, rebase merges |
| the commit is not the merge commit of a pull request merged into `develop` | direct push |
| QA's latest verdict for that pull request's head, before the merge, is not `PASS` | merge without QA clearance, after QA withdrew it, or a verdict added afterwards |
| the previous `develop` head has no successful guard run | a direct push that skipped CI (`[skip ci]`) or that the guard flagged, carried along by the next merge |

- `deploy-staging` needs `develop-guard`, so a failing push is never deployed.
- A hand-dispatched redeploy also requires a successful `Develop guard (QA-cleared merge)` check run on its commit.
- The failure appears as a red CI run on `develop` and an `::error::` annotation that names the reason.

## When the guard fails

- Do not dispatch a deployment of that commit.
- Every later merge also fails, because its parent never passed the guard. To recover:
  - QA reviews the flagged commit.
  - QA posts `QA-VERDICT: RECOVER <flagged sha>` on the remediation pull request, before it is merged.
  - That pull request is usually a revert.
- **Direct push or merge without clearance:** revert it through a pull request that QA clears.
- **Force push:** restore `develop` to the last commit whose guard passed. Coordinate before pushing again.
- Record the incident in the pull request or the checkpoint.

## Bypass scenarios

Owner decision 2026-10-23: test the bypasses and document what remains. The tests are in
`tools/tests/test_develop_guard.py`, and the guard runs against a fake GitHub API in them.

| # | Bypass attempt | Result | Test |
|---|----------------|--------|------|
| 1 | Direct push to `develop` (no pull request, open PR, PR into `main`, PR commit pushed directly) | caught | `test_a_direct_push_fails` |
| 2 | Force push | caught | `test_a_force_push_fails` |
| 3 | Several commits or merges in one push, rebase merge | caught | `test_commits_pushed_along_with_the_merge_fail` |
| 4 | Merge without a verdict, a verdict for another SHA, `BLOCKED`, a quoted line, a short SHA | caught | `test_a_merge_without_a_qa_verdict_for_its_exact_head_fails` |
| 5 | A pass that QA later withdrew | caught | `test_a_later_verdict_that_withdraws_the_pass_fails` |
| 6 | Merge first, then post the verdict and re-run the guard | caught | `test_bypass_verdict_posted_after_the_merge_does_not_count` |
| 7 | Edit an old comment into a verdict after the merge | caught | `test_bypass_verdict_edited_after_the_merge_does_not_count` |
| 8 | Verdict text inside a code block (an example) | caught | `test_bypass_verdict_inside_a_code_block_does_not_count` |
| 9 | Direct push with `[skip ci]`, then a cleared merge on top | caught on the merge | `test_bypass_skip_ci_direct_push_before_a_merge_is_caught` |
| 10 | Cleared merge on top of a flagged push, or a fake guard result from another app or check | caught | `test_bypass_merging_on_top_of_a_flagged_push_is_caught` |
| 11 | A GitHub API error during the check | fails closed | `test_api_errors_fail_closed` |
| 12 | Dispatching a deploy of an unguarded commit | refused (the dispatch needs the guard's check run) | `tools/tests/test_cd_workflow.py` |

### What remains (accepted for staging)

| Remaining bypass | Why it cannot be closed here | Mitigation |
|------------------|------------------------------|------------|
| Writing the verdict line without being QA | One shared account; GitHub cannot tell authors apart | Procedure: only QA writes verdicts. Every verdict stays in the PR history for audit |
| A direct push that deletes or edits the guard job in `ci.yml`, together with the deploy job's dependency on it | The check lives in the repository it protects, so such a push is not flagged and can deploy itself to staging (the host gate only checks that the commit is on `develop`) | Procedure (no direct pushes) and owner review of `ci.yml` history. The CD switch `LAWDESK_STAGING_DEPLOY` turns deployment off. Staging holds sample data only |
| A `[skip ci]` direct push with no later merge | Nothing runs, so nothing flags it | It does not deploy either. The next merge catches it (#9) |
| Pushes land at all | Detection, not prevention | Server-side protection if the plan changes |
| Merges into `develop` before PR #7 | The guard did not exist yet (PR #2 and PR #5) | QA verifies those heads before they are merged |

## Owner settings

Rebase merging is disabled (owner decision 2026-10-23). The approved workflow uses merge commits, and the guard would
reject a rebase merge (#3):

```bash
gh api -X PATCH repos/afawzy70/Lawdesk-v1 -F allow_rebase_merge=false
```
