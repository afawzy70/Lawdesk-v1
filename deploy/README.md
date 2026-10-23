# LawDesk staging deployment (LD-006)

Status: **prepared and tested in CI, not deployed.** Deploying needs owner decisions B1 and B2
(`docs/delivery/PHASE-1-CHECKPOINT.md`). Nothing here changes Matbakhy.

## What runs

Compose project `lawdesk-staging` (`compose.yaml`): `db` (PostgreSQL 18.6), `backend` (Spring Boot), `web` (nginx with
the React build, forwarding `/api/*` and the public health endpoints). No host ports are published. `db` and `backend`
are on an internal-only network; only `web` joins the external network `lawdesk-edge` (alias `lawdesk-web`).
Containers drop all capabilities, use `no-new-privileges`, read-only root file systems (backend, web), memory limits,
health checks and size-bounded logs. The backend connects as `lawdesk_app`, which owns only the `lawdesk` schema.

Observability baseline: structured JSON (ECS) logs with request ids (`docker compose logs`), container health checks
(readiness includes the database), `app_release` rows recording each started build, and `state/history.log` written by
the deploy script.

## One-time host setup (operator with docker rights, e.g. `devops`)

```bash
sudo install -d -o devops -g devops /srv/lawdesk-staging /srv/lawdesk-staging/state
git clone https://github.com/afawzy70/Lawdesk-v1 /srv/lawdesk-staging/repo   # deploy files only
cd /srv/lawdesk-staging/repo/deploy
cp staging.env.example staging.env
install -d -m 700 secrets
openssl rand -hex 24 > secrets/db_admin_password.txt
openssl rand -hex 24 > secrets/db_app_password.txt
chmod 600 secrets/*.txt
docker network create lawdesk-edge
```

## Deploy, verify, roll back

```bash
LAWDESK_STATE_DIR=/srv/lawdesk-staging/state LAWDESK_REGISTRY=ghcr.io/afawzy70/lawdesk scripts/deploy.sh <commit-sha>
scripts/rollback.sh               # redeploys the previous release
LAWDESK_TAG=<sha> LAWDESK_REGISTRY=... scripts/smoke.sh
```

`deploy.sh` takes a lock (one deployment at a time), pulls the two images for that exact SHA, starts them with
`docker compose up --wait`, runs `smoke.sh`, and records the release. If health checks or the smoke test fail it
redeploys the running release and exits 1; if the release that failed *is* the running one (a redeploy), it falls
back to the release before it and forgets the failed one as a rollback target. `smoke.sh` fails on the first check
that does not hold: web and SPA reachable, readiness body `UP`, `/api/v1/cases` exactly 401, `/actuator/env` exactly
404. Migrations are forward-only; an older release still starts on a newer
schema (tested in LD-004). Data volumes are never removed by these scripts.

## Owner decisions still needed

**B2 — how releases reach the host.** Images must be published (GHCR, tagged by commit SHA) and `deploy.sh` run on
the host after CI passes on `develop`. Options: (a) a self-hosted GitHub runner under a dedicated `lawdesk-deploy` user
in the `docker` group, labelled for this repository only; (b) an SSH deploy key for that user stored as an environment
secret, with a forced command limited to `deploy.sh`. Either needs someone with root on the host. Also note the
account's Actions/Packages storage quota is currently exhausted (B6), which blocks publishing images.

**B1 — HTTPS on 443.** Matbakhy's Caddy owns 80/443 and is the only way to get a publicly trusted certificate for an
sslip.io name (ACME needs those ports). Proposed change in `proxy/lawdesk.caddy`: one site block for
`lawdesk.167-86-90-174.sslip.io` plus joining `lawdesk-edge`; recreate only the proxy container. Rollback: restore the
previous Caddyfile/image and disconnect the network. Verify Matbakhy hosts with `curl` before and after.

Without B1 the preview can only be reached privately, e.g. over an SSH tunnel to a loopback-only port, or over
Tailscale; neither is a dedicated public HTTPS hostname.
