# Phase 1 preflight — Contabo inspection (read-only)

Date: 2026-10-23 · Inspected as user `aiagent` · Nothing was stopped, restarted, modified or deleted.
`.env` files under `/srv/matbakhy-*` were deliberately **not** read.

## Host

| Item | Finding |
|------|---------|
| Host | `vmi3673442`, Ubuntu, kernel 6.8.0-147, x86_64 |
| CPU / RAM / swap | 8 vCPU / 23 GiB (≈19 GiB available) / no swap |
| Disk | `/` 290 GB, 21 GB used (8 %) |
| Public IP | `167.86.90.174` (also Tailscale `100.74.109.28`) |
| Docker | Engine 29.9.1, Compose v5.6.0. Socket `root:docker` 660; group `docker` = `devops` only |
| `aiagent` access | **Not** in `docker` group, no passwordless sudo → cannot run containers, compose or image builds locally |
| Toolchain for `aiagent` | JDK 21 at `~/tools/jdk-21.0.12.1+1` (not on PATH), Node 22.23 / npm 10.9, `gh` logged in (repo admin). Maven Central and npm reachable |

## Listening ports

| Port | Owner |
|------|-------|
| 0.0.0.0/[::]:80, 443 | `docker-proxy` → Matbakhy Caddy container (172.18.0.2) |
| 22 | sshd |
| 127.0.0.1:3000 | `docker-proxy` → Matbakhy Grafana |
| 127.0.0.1:3080 | `dsh web` (node, aiagent) |
| 127.0.0.1:18080, 38189 | a Keycloak 26.8.0 dev process started from `~/tools` by `aiagent` (not LawDesk; LawDesk must not use Keycloak — left untouched, flagged to owner) |
| 127.0.0.53/54:53 | systemd-resolved |

Ports reserved for LawDesk must avoid all of the above. LawDesk containers will publish **no** host ports; the only ingress is the shared proxy.

## Matbakhy (do not touch)

- Compose project `matbakhy-preview` in `/srv/matbakhy-preview` (owned by `devops`): Caddy proxy (image `FRONT_IMAGE`, Caddyfile baked in), PostgreSQL 16.15, Keycloak, SeaweedFS, API, Loki/Alloy/Prometheus/Alertmanager/Grafana/node-exporter.
- Docker bridge networks observed: 172.17 (docker0, down), 172.18 (edge — proxy), 172.19 (Grafana), 172.20 (internal — Postgres/Keycloak/API).
- Checkout `/srv/matbakhy-staging`; backups `/srv/matbakhy-backups`, `/srv/backups`, `/srv/deployments` (not readable).
- Actual domain: **`167-86-90-174.sslip.io`**; Caddy serves `app.` `ops.` `api.` `id.` `media.` subdomains with automatic ACME certificates (verified: certificate for `app.167-86-90-174.sslip.io`, valid to 2027-01-16). Access is IP allow-listed per host.

## Reverse proxy / TLS

Caddy (in the Matbakhy compose project) is the only 443 listener. Its Caddyfile is copied into Matbakhy's front image, so adding a LawDesk site means changing Matbakhy's image or mounting a new Caddyfile, then recreating the Matbakhy `proxy` container. **This requires owner approval** (see plan in `PHASE-1-CHECKPOINT.md`, blocker B1).

## GitHub Actions / deployment

- Matbakhy: CI on GitHub-hosted runners; `preview-images.yml` pushes images to GHCR on manual dispatch; deployment is a manual `deploy.sh` run on the host. No self-hosted runner found.
- LawDesk repo `afawzy70/Lawdesk-v1`: private, free plan → branch-protection API returns 403 (required checks cannot be enforced); 0 runners, 0 secrets, 0 environments.
- Deploy-on-merge therefore needs an owner-provided mechanism (blocker B2).

## Spring Boot version

The approved stack is "Spring Boot 3.x". The newest 3.x line, 3.5 (latest 3.5.16), reached end of open-source support on 2026-06-30; supported lines today are 4.0 (to 2026-12-31) and 4.1 (to 2027-07-31). Owner decision required before LD-002 (blocker B3). **Resolved 2026-10-23: Spring Boot 4.1.x approved.**
