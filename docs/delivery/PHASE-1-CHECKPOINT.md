# Phase 1 checkpoint (resume point)

Branch: `feature/phase-1-foundation` → PR into `develop`. Update this file at the end of every story.

## Story status

| Story | Scope (owner's 9 items) | Status | Evidence |
|-------|-------------------------|--------|----------|
| LD-001 | Repository structure and conventions (item 1) | GREEN + REFACTOR done, awaiting QA review | `docs/evidence/LD-001/` |
| LD-002 | Spring Boot backend, health checks, structured logging (items 2, 8) | GREEN + REFACTOR done (23 tests), awaiting QA review | `docs/evidence/LD-002/` |
| LD-003 | React + TypeScript shell (item 3) | GREEN + REFACTOR done (50 tests), merged from `feature/LD-003-react-shell` and re-verified by lead; awaiting QA review | `docs/evidence/LD-003/` |
| LD-004 | PostgreSQL migrations, test isolation (items 4, 5) | GREEN + REFACTOR done (40 backend tests incl. QA edge cases), awaiting QA review | `docs/evidence/LD-004/` |
| LD-005 | GitHub Actions CI gates (items 5, 6) | Done: CI green on PR #2, negative check proven; merge enforcement BLOCKED (B4) | `docs/evidence/LD-005/` |
| LD-006 | Compose staging, observability, deploy + rollback, HTTPS proxy (items 7, 8, 9) | Stack, scripts and CI stack job GREEN; Contabo deploy BLOCKED (B2), HTTPS proxy BLOCKED (B1) | `docs/evidence/LD-006/` |

| PREVIEW | Owner-requested bilingual shell + mock dashboard (not a Phase 1 story) | Draft PR #5 (CI green, 136 tests, browser layout check + screenshots); deployment BLOCKED (B1, B2) | `docs/evidence/PREVIEW/` (on PR #5) |

## Open blockers / owner decisions

| ID | Blocker | Needed from owner |
|----|---------|-------------------|
| B1 | **Approved 2026-10-23** (narrow, reversible route after backup and validation). Implemented as `deploy/proxy/attach.sh` (in-place reload, no proxy restart); applied only after the host rollback drill passes. | devops runs runbook step 7. |
| B2 | **Decided 2026-10-23**: GitHub Actions → SSH forced command (`lawdesk-deploy-gate`) as `devops`, images built on the host (PR #7). | devops runs runbook steps 1–6 (one root step: install the gate); owner sets secrets/variables. |
| ~~B3~~ | **Resolved 2026-10-23**: owner approved Spring Boot 4.1.x (4.1.2) with Java 21; `architecture/DECISIONS.md` updated. | — |
| B4 | Branch protection/required checks unavailable (private repo, free plan). | Upgrade plan or make repo public, or accept process-only enforcement. |
| B5 | Phase 1 acceptance criteria are templates (also QA finding QAD-001). | Product/QA approval of `PHASE-1-ACCEPTANCE.md`. |
| B6 | GitHub Actions artifact storage quota is exhausted on the account (seen in CI 2026-10-23). | Free quota (e.g. delete old artifacts in other repos) or raise it; CI test-report uploads are best-effort until then. |

## Proxy plan (B1)

Superseded by `deploy/proxy/attach.sh` and `docs/operations/STAGING-RUNBOOK.md` (PR #7): the LawDesk block is appended
to the *live* Caddyfile, validated inside the proxy, activated with `caddy reload` (no restart or recreate), and every
Matbakhy host is compared before and after with automatic restore. `detach.sh` removes only the LawDesk block.

## Resume here (2026-10-23, after QA round 1)

- QA (PR #6) defects fixed test-first and pushed: QAD-007..013 on PR #2 (HEAD `020154c`), QAD-014/015 on PR #5
  (HEAD `a58dddc`); CI green on both; QA asked to re-verify those SHAs. QAD-001..006 wait for B5 (AC approval).
- Owner decisions since: CD on every validated develop merge; narrowly scoped Caddy route authorised after backup and
  validation; deployment home `/srv/deployments/lawdesk` (devops); do not expose the site until deployment safety and
  rollback are verified on the host.
- PR #7 (`feature/staging-cd`, on top of #2): CD workflow, SSH forced-command gate, host image builds, shared-proxy
  attach/detach, bootstrap, rollback drill, `docs/operations/STAGING-RUNBOOK.md`. Not deployed; nothing applied to Caddy.
- Merge order: #2 → #5 (update onto develop) → #7, each only after independent QA passes. Then export
  `deploy/` at the merged SHA to `/srv/ai-workspaces/lawdesk/lawdesk-deploy-<sha>.tar.gz` and hand devops the runbook.
- Report a URL only after `attach.sh` succeeded and an external HTTPS check with a trusted certificate passed.
- Do not start Phase 2 until the Phase 1 exit gate is met.

## Git identity

Commits in this worktree use the repo-local identity `LawDesk Claude Agent <noreply@anthropic.com>` (owner decision
2026-10-23); never the owner's personal identity.

## Local environment notes

- `export JAVA_HOME=~/tools/jdk-21.0.12.1+1` before running Maven.
- No Docker locally: integration tests must run without Docker here (embedded PostgreSQL binaries) and with
  real containers in CI.
