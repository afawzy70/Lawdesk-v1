# Phase 1 checkpoint (resume point)

Branch: `feature/phase-1-foundation` → PR into `develop`. Update this file at the end of every story.

## Story status

| Story | Scope (owner's 9 items) | Status | Evidence |
|-------|-------------------------|--------|----------|
| LD-001 | Repository structure and conventions (item 1) | GREEN + REFACTOR done, awaiting QA review | `docs/evidence/LD-001/` |
| LD-002 | Spring Boot backend, health checks, structured logging (items 2, 8) | In progress (Spring Boot 4.1.2) | `docs/evidence/LD-002/` |
| LD-003 | React + TypeScript shell (item 3) | In progress in parallel on `feature/LD-003-react-shell`, to be merged into the phase branch | `docs/evidence/LD-003/` |
| LD-004 | PostgreSQL migrations, test isolation (items 4, 5) | Not started (depends on LD-002) | — |
| LD-005 | GitHub Actions CI gates (items 5, 6) | Not started | — |
| LD-006 | Compose staging, observability, deploy + rollback, HTTPS proxy (items 7, 8, 9) | Not started; deploy blocked on B1, B2 | — |

## Open blockers / owner decisions

| ID | Blocker | Needed from owner |
|----|---------|-------------------|
| B1 | 443 is owned by Matbakhy's Caddy (compose project `matbakhy-preview`, Caddyfile baked into its image). LawDesk needs a site block there. | Approval of the isolated proxy plan below, executed by someone with `devops`/docker rights. |
| B2 | No mechanism for GitHub Actions to deploy to Contabo (no runner, no secrets, `aiagent` lacks docker). | Choose: (a) self-hosted runner under a dedicated `lawdesk-deploy` user in the `docker` group, or (b) SSH deploy key + restricted deploy user; plus GHCR pull access. |
| ~~B3~~ | **Resolved 2026-10-23**: owner approved Spring Boot 4.1.x (4.1.2) with Java 21; `architecture/DECISIONS.md` updated. | — |
| B4 | Branch protection/required checks unavailable (private repo, free plan). | Upgrade plan or make repo public, or accept process-only enforcement. |
| B5 | Phase 1 acceptance criteria are templates. | Product/QA approval of `PHASE-1-ACCEPTANCE.md`. |

## Proposed isolated proxy plan (B1) — NOT applied

1. LawDesk runs as its own compose project `lawdesk-staging` with private networks; no host ports.
2. One external network `lawdesk-edge` is created; only LawDesk's web container and the Matbakhy proxy join it.
3. Add one site block `lawdesk.167-86-90-174.sslip.io { reverse_proxy lawdesk-web:8080 }` (with Matbakhy's
   header/IP allow-list snippets) to the Caddyfile; recreate only the `proxy` service.
4. Rollback: restore the previous Caddyfile/image digest, recreate `proxy`, `docker network rm lawdesk-edge`.
   Matbakhy hosts are verified with `curl` before and after.

## Git identity

Commits in this worktree use the repo-local identity `LawDesk Claude Agent <noreply@anthropic.com>` (owner decision
2026-10-23); never the owner's personal identity.

## Local environment notes

- `export JAVA_HOME=~/tools/jdk-21.0.12.1+1` before running Maven.
- No Docker locally: integration tests must run without Docker here (embedded PostgreSQL binaries) and with
  real containers in CI.
