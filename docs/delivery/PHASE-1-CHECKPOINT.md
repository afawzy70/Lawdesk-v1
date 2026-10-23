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
| B1 | 443 is owned by Matbakhy's Caddy (compose project `matbakhy-preview`, Caddyfile baked into its image). LawDesk needs a site block there. | Approval of the isolated proxy plan below, executed by someone with `devops`/docker rights. |
| B2 | No mechanism for GitHub Actions to deploy to Contabo (no runner, no secrets, `aiagent` lacks docker). | Choose: (a) self-hosted runner under a dedicated `lawdesk-deploy` user in the `docker` group, or (b) SSH deploy key + restricted deploy user; plus GHCR pull access. |
| ~~B3~~ | **Resolved 2026-10-23**: owner approved Spring Boot 4.1.x (4.1.2) with Java 21; `architecture/DECISIONS.md` updated. | — |
| B4 | Branch protection/required checks unavailable (private repo, free plan). | Upgrade plan or make repo public, or accept process-only enforcement. |
| B5 | Phase 1 acceptance criteria are templates (also QA finding QAD-001). | Product/QA approval of `PHASE-1-ACCEPTANCE.md`. |
| B6 | GitHub Actions artifact storage quota is exhausted on the account (seen in CI 2026-10-23). | Free quota (e.g. delete old artifacts in other repos) or raise it; CI test-report uploads are best-effort until then. |

## Proposed isolated proxy plan (B1) — NOT applied

1. LawDesk runs as its own compose project `lawdesk-staging` with private networks; no host ports.
2. One external network `lawdesk-edge` is created; only LawDesk's web container and the Matbakhy proxy join it.
3. Add one site block `lawdesk.167-86-90-174.sslip.io { reverse_proxy lawdesk-web:8080 }` (with Matbakhy's
   header/IP allow-list snippets) to the Caddyfile; recreate only the `proxy` service.
4. Rollback: restore the previous Caddyfile/image digest, recreate `proxy`, `docker network rm lawdesk-edge`.
   Matbakhy hosts are verified with `curl` before and after.

## Resume here (2026-10-23)

- PR #2 (Phase 1) and PR #5 (preview, depends on #2) are drafts with all CI gates green; waiting for independent QA
  and owner decisions. Do not start Phase 2 until the Phase 1 exit gate is met.
- QA can execute `docs/delivery/phase-1-qa-probes.json` with `qa/independent/run.py` once B5 is approved.
- When B2 is resolved: add the publish-and-deploy workflow (GHCR images tagged with the develop SHA, then
  `deploy/scripts/deploy.sh <sha>` on the host after CI passes). When B1 is approved: apply `deploy/proxy/lawdesk.caddy`
  per `deploy/README.md`, verify Matbakhy hosts before/after, then run `smoke.sh` and report the URL only after health
  checks pass.

## Git identity

Commits in this worktree use the repo-local identity `LawDesk Claude Agent <noreply@anthropic.com>` (owner decision
2026-10-23); never the owner's personal identity.

## Local environment notes

- `export JAVA_HOME=~/tools/jdk-21.0.12.1+1` before running Maven.
- No Docker locally: integration tests must run without Docker here (embedded PostgreSQL binaries) and with
  real containers in CI.
