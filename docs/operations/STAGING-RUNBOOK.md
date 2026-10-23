# LawDesk staging — operator runbook

Host: Contabo `vmi3673442` (167.86.90.174), shared with Matbakhy. Public URL once attached:
`https://lawdesk.167-86-90-174.sslip.io`. Everything LawDesk owns lives in `/srv/deployments/lawdesk`, the Docker
compose project `lawdesk-staging`, its volume `lawdesk-staging_db-data` and the network `lawdesk-edge`.

**Never** stop, restart, recreate or edit a Matbakhy container, volume, `.env` or compose file. The only change to
Matbakhy's proxy (`matbakhy-preview-proxy-1`) is the LawDesk site block plus joining `lawdesk-edge`, applied in place by
`deploy/proxy/attach.sh` and removed by `deploy/proxy/detach.sh` (owner authorisation 2026-10-23).

## How a release reaches staging

```
merge into develop ─► CI (6 gates) ─► deploy-staging job ─ssh─► lawdesk-deploy-gate "deploy <sha>" (forced command, as devops)
                                                                 ├─ git fetch develop (read-only deploy key); refuse if <sha> not on develop
                                                                 ├─ git archive <sha> → releases/<sha>/ ; docker build backend + web on the host
                                                                 └─ releases/<sha>/deploy/scripts/deploy.sh <sha>
                                                                      lock → pg_dump backup → compose up --wait → smoke
                                                                      failure → previous release back up (exit 1)
                      ◄── job: confirm current=<sha>, check https://… with a trusted certificate, roll back if that fails
```

* Unmerged pull-request code never deploys: the job runs only for `push` to `develop` after every gate, and the host
  itself refuses any commit that is not on `develop`.
* No images are published; GitHub Packages is not used (storage quota, B6). Images are `lawdesk-local/{backend,web}:<sha>`
  and carry the label `org.opencontainers.image.revision=<sha>`. The gate rebuilds a cached tag whose label names other
  code, and `deploy.sh` starts a release (or a rollback target) only when both labels match its commit (QAD-017).
* A rollback target is started with its own `compose.yaml` and verified with its own `smoke.sh` (QAD-021).
* The runner's key can only run the gate (`restrict,command=…`): no shell, no forwarding, three requests
  (`deploy <sha>`, `rollback`, `status`).
* Migrations are forward-only (Flyway). A rollback restarts the previous images against the newer schema, which works
  because Flyway ignores future migrations (tested in LD-004). Schema is never downgraded automatically; every
  deployment over a running release first writes a `pg_dump -Fc` to `backups/` (newest 10 kept) for manual recovery.

## Exit codes

| Gate | Meaning |
|------|---------|
| 0 | deployed (or status/rollback succeeded) |
| 1 | release failed health/smoke checks and the previous release was restored |
| 2 | malformed request / nothing to roll back |
| 3 | another deployment is running |
| 4 | release failed **and** the rollback failed — staging needs attention |
| 5 | commit is not on `develop` |
| 6 | fetch or image build failed, or a built image does not carry its commit's revision label; the running release is unchanged |

## One-time setup (devops)

Prerequisite: the CD pull request is merged into `develop` (it carries `deploy/host/*` and the host-mode `deploy.sh`).
Commands are exact; run them as `devops` unless marked **root**.

### 0. Record Matbakhy's state (proves later that nothing was restarted)

```bash
docker ps --filter label=com.docker.compose.project=matbakhy-preview \
  --format '{{.Names}} {{.ID}} {{.Status}}' | sort > ~/matbakhy-before.txt
for c in $(docker ps -q --filter label=com.docker.compose.project=matbakhy-preview); do
  docker inspect -f '{{.Name}} {{.State.StartedAt}} {{.RestartCount}}' "$c"; done | sort > ~/matbakhy-started-before.txt
```

### 1. Get the deploy files of the merged commit

The repository is private and the host has no GitHub credential yet. The agent exports `deploy/` of the merged commit
to `/srv/ai-workspaces/lawdesk/lawdesk-deploy-<sha>.tar.gz`; that directory is only readable through root:

```bash
SHA=<merged develop sha>          # full 40 characters, from GitHub
mkdir -p /srv/deployments/lawdesk/setup && cd /srv/deployments/lawdesk/setup
sudo cat /srv/ai-workspaces/lawdesk/lawdesk-deploy-$SHA.tar.gz | tar -xz
```

Step 3 creates the read-only fetch; once the deploy key is added (step 4) verify the files against GitHub itself:

```bash
git -C /srv/deployments/lawdesk/repo.git fetch -q origin +refs/heads/develop:refs/remotes/origin/develop
mkdir -p /tmp/lawdesk-verify && git -C /srv/deployments/lawdesk/repo.git archive "$SHA" deploy | tar -x -C /tmp/lawdesk-verify
diff -r /tmp/lawdesk-verify/deploy deploy && echo "deploy files match GitHub at $SHA"
```

### 2. CI deploy key (on the owner's workstation, not the server)

```bash
ssh-keygen -t ed25519 -N "" -C lawdesk-ci-deploy -f lawdesk_ci_deploy
gh secret set LAWDESK_STAGING_SSH_KEY --repo afawzy70/Lawdesk-v1 < lawdesk_ci_deploy
shred -u lawdesk_ci_deploy        # the private half now exists only in GitHub Secrets
# copy lawdesk_ci_deploy.pub to the server, e.g. /srv/deployments/lawdesk/setup/lawdesk_ci_deploy.pub
```

### 3. Bootstrap (devops, idempotent)

```bash
cd /srv/deployments/lawdesk/setup
deploy/host/bootstrap.sh lawdesk_ci_deploy.pub
```

If `~devops/.ssh/authorized_keys` already lists the CI key with any other options (or without restriction), bootstrap
refuses with exit 2 before changing anything; remove that line by hand (QAD-018). Otherwise it creates
`state/ releases/ logs/ backups/ secrets/ proxy-backups/`, generates the two database passwords once,
creates the bare repository with a read-only GitHub deploy key, binds the CI key to the gate in
`~devops/.ssh/authorized_keys`, and creates the `--internal` network `lawdesk-edge`. It prints the next two items.

### 4. GitHub configuration (owner, or the agent with repo-admin `gh`)

```bash
gh repo deploy-key add ~/lawdesk_github_ro.pub --repo afawzy70/Lawdesk-v1 --title lawdesk-staging-fetch   # read-only by default
gh secret   set LAWDESK_STAGING_KNOWN_HOSTS --repo afawzy70/Lawdesk-v1 \
  --body "167.86.90.174 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIuOMmfI7IkVf7qyBWoDOJZW6u4QNRMbBQY37jXpLI+s"
gh variable set LAWDESK_STAGING_SSH_TARGET --repo afawzy70/Lawdesk-v1 --body "devops@167.86.90.174"
# leave LAWDESK_STAGING_DEPLOY and LAWDESK_STAGING_URL unset until step 8
```

Host key fingerprint to compare: `SHA256:QDwnaK3VTY1Tk4ZX7/voXqImadGw2r7TeUqPmWR8PYc` (`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`).
SSH must be reachable from GitHub-hosted runners on port 22 (key-only auth is already enforced by `sshd`).

### 5. Install the gate (**root**, once per gate change)

```bash
sudo install -o root -g root -m 0755 /srv/deployments/lawdesk/setup/deploy/host/lawdesk-deploy-gate /usr/local/sbin/lawdesk-deploy-gate
sha256sum /usr/local/sbin/lawdesk-deploy-gate deploy/host/lawdesk-deploy-gate   # must match
```

Root-owned so neither `devops` nor any deployed release can change what the CI key runs.

### 6. First deployment, by hand, before anything is public

```bash
lawdesk-deploy-gate deploy $SHA          # builds both images (~5 min the first time), deploys, smoke-tests
lawdesk-deploy-gate status
lawdesk-deploy-gate deploy $SHA          # repeat: must reuse release and images and stay healthy (exit 0)
/srv/deployments/lawdesk/releases/$SHA/deploy/host/rollback-drill.sh   # must print DRILL PASSED
```

The drill derives a release whose backend refuses to start, deploys it, and passes only if `deploy.sh` restored the
running release, which then passes its smoke test; it removes only its two drill image tags.

### 7. Attach the HTTPS hostname (only after step 6 passed)

```bash
cd /srv/deployments/lawdesk/releases/$SHA
deploy/proxy/attach.sh            # backup → validate in the proxy → join lawdesk-edge → caddy reload → compare Matbakhy hosts
curl -sS -o /dev/null -w '%{http_code} %{ssl_verify_result}\n' https://lawdesk.167-86-90-174.sslip.io/healthz
```

Caddy obtains the certificate on the first request (ACME through the proxy's existing 80/443). The script refuses
before any change if the LawDesk markers are malformed, if they enclose any non-LawDesk site, if no other site is found
or one of them does not answer, or if the adapted candidate would not serve exactly the existing hosts plus LawDesk
(hosts as Caddy itself adapts them, literal or `{$PREVIEW_DOMAIN}`; QAD-020). It restores automatically if reload,
persisting the file (QAD-019) or any Matbakhy host check fails. Besides the live Caddyfile it writes only
`/etc/caddy/Caddyfile.lawdesk-{candidate,check,previous}` inside the proxy. Undo at any time:
`deploy/proxy/detach.sh` (removes only the LawDesk block from the *live* file, reloads, leaves `lawdesk-edge`).

**Persistence:** the change lives in the running proxy container. A Matbakhy redeploy that *recreates* the proxy
(new `FRONT_IMAGE`) drops it; rerun `attach.sh` afterwards (the CD job's public check fails until then). A permanent
`import` in Matbakhy's Caddyfile would remove this step but changes Matbakhy's repository and needs separate approval.

### 8. Enable automatic deployment

```bash
gh variable set LAWDESK_STAGING_URL    --repo afawzy70/Lawdesk-v1 --body "https://lawdesk.167-86-90-174.sslip.io"
gh variable set LAWDESK_STAGING_DEPLOY --repo afawzy70/Lawdesk-v1 --body enabled
```

From now on every merge into `develop` that passes CI deploys. Manual redeploy of a merged commit:
`gh workflow run deploy-staging.yml --ref develop -f sha=<sha>`. A dispatched run is refused unless it runs develop's
copy of the workflow and all six CI gates succeeded for that exact commit, as GitHub Actions reported them (QAD-016).
Switch off: `gh variable set LAWDESK_STAGING_DEPLOY --body disabled`.

Who may deploy: anyone with write access can dispatch the workflow, so the guards above plus the host's own
"is it on develop" check are what limit it. If the repository plan offers environment protection, put the two
`LAWDESK_STAGING_*` secrets in the `staging` environment and restrict its deployment branches to `develop`; then no
workflow from another branch can read the key at all.

### 9. Prove Matbakhy was not disturbed

```bash
for c in $(docker ps -q --filter label=com.docker.compose.project=matbakhy-preview); do
  docker inspect -f '{{.Name}} {{.State.StartedAt}} {{.RestartCount}}' "$c"; done | sort > ~/matbakhy-started-after.txt
diff ~/matbakhy-started-before.txt ~/matbakhy-started-after.txt && echo "Matbakhy: no container restarted"
for h in app ops api id media; do curl -sS -o /dev/null -w "$h %{http_code}\n" https://$h.167-86-90-174.sslip.io/; done
```

## Day-to-day

| Task | Command (devops) |
|------|------------------|
| State and recent history | `lawdesk-deploy-gate status` |
| Full log of a deployment | `ls -t /srv/deployments/lawdesk/logs/ \| head` |
| Release history | `/srv/deployments/lawdesk/state/history.log` |
| Roll back to the previous release | `lawdesk-deploy-gate rollback` |
| Database backups | `/srv/deployments/lawdesk/backups/*.dump` (restore by hand with `pg_restore`; never automatic) |
| Remove the public route | `releases/<current>/deploy/proxy/detach.sh` |

Disk: release directories and `lawdesk-local/*` images accumulate (≈ 400 MB per release). Prune old ones by hand,
never the current or previous release: `docker image rm lawdesk-local/backend:<old> lawdesk-local/web:<old>`.

## Isolation summary

| Boundary | How it is enforced | Checked by |
|----------|-------------------|------------|
| No host ports | services publish nothing | `deploy/tests/test_compose.py` |
| DB and API unreachable from the proxy | `internal` network; only `web` joins `lawdesk-edge` | `test_compose.py` |
| `lawdesk-edge` has no route out | created `--internal` | `test_bootstrap.py` |
| Least-privilege DB role | backend connects as `lawdesk_app` (no superuser/createdb/createrole) | CI stack job |
| Hardened containers | `cap_drop: ALL`, `no-new-privileges`, read-only roots, memory limits | `test_compose.py` |
| CI cannot run arbitrary commands | forced command + exact request regex | `test_gate.py`, `test_cd_workflow.py` |
| Only merged code deploys | CI condition + host-side `merge-base --is-ancestor` | `test_cd_workflow.py`, `test_gate.py` |
| Proxy change is additive and reversible | backup, validate, reload in place, Matbakhy host comparison, auto-restore | `test_proxy_scripts.py`, real Caddy 2.10.2 validation (`docs/evidence/CD/10-*`) |
| Matbakhy containers, volumes and compose project never stopped, restarted or recreated | no compose/restart/stop/rm calls anywhere in LawDesk scripts | `test_proxy_scripts.py`, step 0/9 |
| **Matbakhy's proxy is modified** (owner-authorised, operator-run only) | `attach.sh`/`detach.sh` copy files into it, connect it to `lawdesk-edge` and reload Caddy; deployments and CI never run them; the live run is not yet validated | `test_proxy_scripts.py`, `docs/evidence/CD/21-*`; live run NOT_RUN |

Residual risk, accepted for staging: `devops` is in the `docker` group, so code merged into `develop` runs with Docker
rights on this host during deployment. Merges are gated by review, CI and independent QA.
