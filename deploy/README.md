# LawDesk staging deployment (LD-006)

Status: **prepared and tested in CI, not deployed yet.** Host setup is the devops runbook
(`docs/operations/STAGING-RUNBOOK.md`). Nothing here restarts or recreates Matbakhy.

## What runs

Compose project `lawdesk-staging` (`compose.yaml`): `db` (PostgreSQL 18.6), `backend` (Spring Boot), `web` (nginx with
the React build, forwarding `/api/*` and the public health endpoints). No host ports are published. `db` and `backend`
are on an internal-only network; only `web` joins the external network `lawdesk-edge` (alias `lawdesk-web`).
Containers drop all capabilities, use `no-new-privileges`, read-only root file systems (backend, web), memory limits,
health checks and size-bounded logs. The backend connects as `lawdesk_app`, which owns only the `lawdesk` schema.

Observability baseline: structured JSON (ECS) logs with request ids (`docker compose logs`), container health checks
(readiness includes the database), `app_release` rows recording each started build, and `state/history.log` written by
the deploy script.

## Deploy, verify, roll back

On the host these run through the gate (`lawdesk-deploy-gate deploy <sha>` / `rollback` / `status`), which sets
`LAWDESK_IMAGE_SOURCE=local` and the host paths. Directly (e.g. with registry images):

```bash
LAWDESK_REGISTRY=<registry> scripts/deploy.sh <commit-sha>
scripts/rollback.sh               # redeploys the previous release
LAWDESK_TAG=<sha> LAWDESK_REGISTRY=<registry> scripts/smoke.sh
```

`deploy.sh` takes a lock (one deployment at a time), pulls the two images for that exact SHA (or, with
`LAWDESK_IMAGE_SOURCE=local`, refuses unless they were built on the host), dumps the database when
`LAWDESK_BACKUP_DIR` is set and a release is running, starts them with
`docker compose up --wait`, runs `smoke.sh`, and records the release. If health checks or the smoke test fail it
redeploys the running release and exits 1; if the release that failed *is* the running one (a redeploy), it falls
back to the release before it and forgets the failed one as a rollback target. `smoke.sh` fails on the first check
that does not hold: web and SPA reachable, readiness body `UP`, `/api/v1/cases` exactly 401, `/actuator/env` exactly
404. Migrations are forward-only; an older release still starts on a newer
schema (tested in LD-004). Data volumes are never removed by these scripts.

## Staging on Contabo

Automatic deployment after every validated merge into `develop`, the host gate, image builds on the host, the shared
proxy route and every operator command are in [`docs/operations/STAGING-RUNBOOK.md`](../docs/operations/STAGING-RUNBOOK.md).

| Path | Role |
|------|------|
| `host/lawdesk-deploy-gate` | SSH forced command for CI: `deploy <sha>` / `rollback` / `status` |
| `host/bootstrap.sh` | one-time, idempotent host preparation as `devops` |
| `host/rollback-drill.sh` | proves on the real stack that a broken release is rolled back |
| `proxy/attach.sh`, `proxy/detach.sh` | add/remove the LawDesk site on the shared Caddy in place (no restart) |
| `host/qa_accounts.py` | dedicated UAT QA accounts through the user administration API (see below) |

## Dedicated QA accounts on UAT

Independent QA signs in with its own accounts, never the bootstrap administrator. `host/qa_accounts.py` uses only the
public user administration API: it creates or rotates `qa.admin` (ADMIN), `qa.manager` (LEGAL_MANAGER), `qa.staff`
(LEGAL_STAFF) and `qa.viewer` (`QA_READ_ONLY`: view cases, courts, proceedings and judgments), all with
`@example.invalid` emails. It completes each forced first password change and checks every account's permissions and
the administration boundary before it releases anything. The administrator's own account is never changed.

Credentials pass only through a pipe from the operator who holds the administrator password to the QA identity. They
never go into arguments, files outside the 0700 directory, logs or a terminal (`provision` refuses a terminal):

```sh
sudo -u devops python3 -I qa_accounts.py provision --base-url https://lawdesk.167-86-90-174.sslip.io \
    --admin-password-file /srv/deployments/lawdesk/secrets/admin_bootstrap_password.txt \
  | sudo -u aiagent python3 -I qa_accounts.py receive --dir /srv/ai-workspaces/secrets/lawdesk-qa
```

Leave out `--admin-password-file` once the administrator has changed the bootstrap password. The script then prompts
on the terminal without echoing. `receive` writes `admin.json`, `manager.json`, `staff.json` and `viewer.json`
(`{"username","password"}`, mode 0600) and prints only the variable names and paths
(`LAWDESK_QA_CREDENTIAL_FILE`, `LAWDESK_QA_MANAGER_CREDENTIAL_FILE`, `LAWDESK_QA_STAFF_CREDENTIAL_FILE`,
`LAWDESK_QA_VIEWER_CREDENTIAL_FILE`). `qa_accounts.py verify --base-url … --dir …` re-checks them as the QA identity.
Rerunning `provision` rotates every QA password. To withdraw an account, disable it in Administration → Users.
