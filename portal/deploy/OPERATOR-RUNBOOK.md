# Operator runbook — publish the Arabic Floorplan portal (owner-approved)

Who: a user in the `docker` group (on this host: `devops`). `aiagent` has no Docker access, so it cannot run these steps.
Scope: only the container `floorplan-web`, compose project `lawdesk-floorplan`, network `floorplan-edge`, and one marked block in the shared Caddyfile. LawDesk and Matbakhy containers, volumes and databases are never touched; the proxy is reloaded gracefully, never restarted.

## 0. Receive the package (root copies it; the hand-off directory is private)
```
sudo install -d -o devops -g devops -m 700 /srv/deployments/lawdesk-floorplan
sudo cp /srv/ai-workspaces/lawdesk/floorplan-handoff/floorplan-portal-<PORTAL_COMMIT>.tar.gz{,.sha256} /srv/deployments/lawdesk-floorplan/
sudo chown devops:devops /srv/deployments/lawdesk-floorplan/floorplan-portal-*
sudo install -m 600 -o devops -g devops /srv/ai-workspaces/secrets/lawdesk-floorplan/admin.bcrypt /srv/deployments/lawdesk-floorplan/admin.bcrypt
```
Then as `devops`:
```
cd /srv/deployments/lawdesk-floorplan && sha256sum --check floorplan-portal-<PORTAL_COMMIT>.tar.gz.sha256
mkdir -p release && tar -xzf floorplan-portal-<PORTAL_COMMIT>.tar.gz -C release
```
(`admin.bcrypt` holds only the bcrypt hash of the generated password; the plaintext stays in `/srv/ai-workspaces/secrets/lawdesk-floorplan/admin.password`, mode 600, owner `aiagent`.)

## 1. Deploy the container (portal only)
```
cd /srv/deployments/lawdesk-floorplan/release
deploy/scripts/deploy.sh dist <PORTAL_COMMIT>-1
```
Expect `deployed <PORTAL_COMMIT>-1`; on an unhealthy start it restores the previous release itself.

## 2. Attach to the shared Caddy (backup, validate, graceful reload, other hosts compared before/after)
```
FLOORPLAN_BASIC_USER=admin FLOORPLAN_BASIC_HASH_FILE=/srv/deployments/lawdesk-floorplan/admin.bcrypt \
  deploy/proxy/attach.sh
```
Expect `attached: https://floorplan.167-86-90-174.sslip.io -> floorplan-web:8080 (private, basic auth …)`. It restores the old Caddyfile by itself if any other host answers differently.

## 3. Verify (external, through the real proxy)
```
deploy/scripts/verify.sh floorplan.167-86-90-174.sslip.io admin:/srv/ai-workspaces/secrets/lawdesk-floorplan/admin.password
```
(`aiagent` can run the same verify.sh, it only needs HTTP access; ask it to, or run it yourself.) All lines must be `PASS`.

## Owner: choosing your own password (never typed on a command line)
```
deploy/scripts/set_password.sh /srv/deployments/lawdesk-floorplan/admin.bcrypt     # silent prompt
FLOORPLAN_BASIC_USER=admin FLOORPLAN_BASIC_HASH_FILE=/srv/deployments/lawdesk-floorplan/admin.bcrypt deploy/proxy/attach.sh
```
To read the generated one: `sudo cat /srv/ai-workspaces/secrets/lawdesk-floorplan/admin.password` in your own terminal (not in a shared log).

## Rollback / removal
- Previous portal release: `deploy/scripts/rollback.sh`
- Remove the site: `deploy/proxy/detach.sh`, then `docker compose -p lawdesk-floorplan down`, then `docker network rm floorplan-edge`.

## Refreshing after R5/R6 are accepted
Only after the change is independently QA-accepted, merged to develop and verified on UAT: check out that develop SHA, add or update the feature JSON files (and `planned/*.json`), run `portal/tools/refresh_portal.sh <sha>`, review, package with `deploy/scripts/package.sh`, deploy as above. Never refresh from an unfinished R5/R6 branch.
