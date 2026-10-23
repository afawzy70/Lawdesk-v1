# Protecting `develop` without branch protection (B4)

**Status:** PROPOSED. The owner approves or amends it; until then it binds the agents only.

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
| QA's latest verdict for that pull request's head is not `PASS` | merge without QA clearance, or after QA withdrew it |

- `deploy-staging` needs `develop-guard`, so a failing push is never deployed.
- A hand-dispatched redeploy also requires a successful `Develop guard (QA-cleared merge)` check run on its commit.
- The failure appears as a red CI run on `develop` and an `::error::` annotation that names the reason.

## When the guard fails

- Do not dispatch a deployment of that commit.
- **Direct push or merge without clearance:** revert it through a pull request that QA clears.
- **Force push:** restore `develop` to the last commit whose guard passed. Coordinate before pushing again.
- Record the incident in the pull request or the checkpoint.

## Limits (accepted until the plan changes)

- **It detects, it does not prevent.** A push still lands on `develop`; it simply cannot deploy. If the
  repository moves to a plan with protection, add a ruleset for `develop` and `main` (require pull requests, block
  force pushes and deletions) and keep this check as a second line.
- **The QA verdict is not authenticated.** Because the account is shared, anyone with it could write the marker. The
  rule that only QA posts it is procedural, and every verdict stays auditable in the pull request history.
- **Older merges are not checked.** Merges before this check reached `develop` are not verified: PR #2 and PR #5 land
  before PR #7 brings the guard.

## Owner settings (optional, recommended)

Disable rebase merging so that the merge button cannot produce a push the guard rejects:

```bash
gh api -X PATCH repos/afawzy70/Lawdesk-v1 -F allow_rebase_merge=false
```
