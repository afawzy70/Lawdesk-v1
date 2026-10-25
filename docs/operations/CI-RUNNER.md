# Self-hosted CI runner (LD-041)

Owner decision 2026-10-25: every LawDesk GitHub Actions job runs on the runner `contabo` on the Contabo server instead
of on GitHub-hosted runners. This file says what that runner is, what it can and cannot reach, how it is set up once,
and how to roll back.

## Why, and what this does not fix

Since 2026-10-24 ~18:17Z GitHub refuses to start GitHub-hosted jobs for the account: "The job was not started because
recent account payments have failed or your spending limit needs to be increased". The hold concerns GitHub-hosted
compute.

* **Evidence that self-hosted jobs still run under the hold:** the proof-of-concept run
  [40168609293](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40168609293) executed on `contabo` while hosted jobs
  were refused, and so did every run of the migration (see Evidence below).
* **What is not claimed:** the account billing problem itself is not fixed by this migration. GitHub may bill or limit
  self-hosted usage in future (a per-minute platform charge was announced and postponed), and workflows that were
  not migrated would still be refused. Fixing Billing & plans remains the owner's decision.

## What runs where

| Part | Value |
|------|-------|
| Runner | `contabo`, labels `self-hosted`, `Linux`, `X64`; workflows use `runs-on: [self-hosted, Linux, X64]` |
| Service | `actions.runner.afawzy70-Lawdesk-v1.contabo.service` (systemd, enabled, restarts on boot) |
| Directory | `/home/lawdesk-ci/actions-runner` (work in `_work/`) |
| Identity | the unprivileged user `lawdesk-ci` (no sudo, not in the `docker` group, mode-0750 home) |
| Jobs | all seven jobs of `ci.yml` and the `deploy` job of `deploy-staging.yml`; job IDs, names, commands and checks are unchanged |
| Docker | the runner's **own rootless daemon**, the user unit `lawdesk-ci-docker.service` of `lawdesk-ci` (root-owned file in `/etc/systemd/user`), socket `/run/user/<uid>/docker.sock`, reached through `DOCKER_HOST` in the runner's `.env` |
| One job at a time | one runner process, so jobs queue. `concurrency` in `ci.yml` cancels superseded pull request runs; pushes to `develop` are never cancelled |

Tools: Java 21 and Node 22 come from `actions/setup-java` and `actions/setup-node`, Python 3.12 from
`actions/setup-python`; ShellCheck, actionlint and gitleaks are downloaded with pinned checksums, as before. Maven and
npm caches persist in the runner's home between jobs, which makes later runs faster.

## Security boundaries

| Boundary | How it holds |
|----------|--------------|
| Host Docker and Matbakhy | `lawdesk-ci` cannot open `/var/run/docker.sock` (verified: `permission denied` in a real job, run 40170308828). Docker-dependent steps use the rootless daemon, whose containers, images and networks are invisible to the host daemon. The CI stack is named `lawdesk-staging` and uses a network called `lawdesk-edge`, the names of the real staging stack, so it must never meet the host daemon |
| Root | no sudo, no setuid helpers beyond `newuidmap`/`newgidmap`, no capabilities. The daemon unit grants nothing to the user |
| Secrets | the seven CI gates read no secret (`tools/tests/test_ci_workflow.py` pins this). The deploy job reads the two `LAWDESK_STAGING_*` secrets only |
| Deployment | unchanged: the deploy job reaches the host through the SSH forced command `deploy/host/lawdesk-deploy-gate` (`deploy <sha>`, `status`, `rollback`, and only for commits on `develop`). The gate key never leaves `$RUNNER_TEMP` and is removed by a final `always()` step |
| Published ports | the database container of the PostgreSQL-image gate is published on `127.0.0.1` with a random port. `services:` is not used, because it would publish on every interface of this public host. The compose stack publishes no host ports |
| Resource use | root-owned: `runner-limits.conf` (runner ≤ 4 CPUs, 7 GiB) and `user-slice-limits.conf` on `user-<uid>.slice` (the daemon with all its containers ≤ 3 CPUs, 5 GiB), bounded tasks, low priority. The user could edit its own unit, so the slice is the ceiling that counts. Ceilings for a host with 23 GiB and 8 cores shared with Matbakhy; jobs run one at a time |
| Cleanup | every Docker job removes its containers, network and images; the daily maintenance timer prunes the daemon's old images, build cache and volumes and flags a disk above 80 % |

### Residual risks (read before relying on the boundary)

1. **The runner is persistent and runs pull request code as `lawdesk-ci`.** A malicious change to a build script or a
   dependency could leave something in that user's home, the tool cache, `~/.m2` or the runner directory, and a later
   job, **including the deploy job**, would run it. The gate key would be exposed to it for the length of one deploy,
   and the gate then only deploys commits that are already on `develop` (the host checks), so the damage is bounded to
   redeploying or rolling back merged code. Closing this properly needs a deploy identity separate from the PR
   identity: an ephemeral (just-in-time) runner, or a second runner user with its own label used only by `deploy`. Both
   were out of scope of this decision (no second runner); it is recorded for the owner.
2. **The deploy key is a repository secret, not an environment secret.** Any workflow on any branch of this repository
   can read repository secrets, hosted or self-hosted. The `staging` environment has no secrets and no branch
   restriction. The documented fix (STAGING-RUNBOOK §8) is to recreate both secrets in the `staging` environment,
   restrict its deployment branches to `develop`, then delete the repository-level secrets; it needs the key's value,
   so only the owner can do it. One-time commands are under Operator steps.
3. **Jobs share the host network.** A job can connect to services bound to the host's loopback or tailnet addresses
   (at the time of writing: several local ports). CI containers cannot, because the daemon disables host loopback. A
   host firewall rule for the `lawdesk-ci` uid would close it, and would also block the tests that use loopback, so it
   needs care; it is not part of this change.
4. **Shared build caches** (`~/.m2`, npm) mean a change that poisons a cache can influence later jobs. The images that
   run in staging are built on the host by the gate from `git archive`, not by CI, so the cache cannot reach them.
5. **Throughput.** One runner serialises everything. A pull request takes the sum of its jobs (about half an hour at
   best) and a stack of pull requests waits in turn. Add runners only by owner decision.

## Operator steps (once, as the infrastructure administrator)

Why this is needed: both Docker jobs failed in run 40170308828 with `permission denied … /var/run/docker.sock`,
because `lawdesk-ci` rightly cannot use the host daemon. A rootless daemon of its own needs a sub-UID range and a
persistent user manager (`loginctl enable-linger`), which only root can add. Everything is in `tools/ci-runner/`, and
`setup-rootless-docker.sh` shows what it will do before doing it:

```bash
cd /path/to/Lawdesk-v1                                 # a checkout of the merged commit
tools/ci-runner/setup-rootless-docker.sh --check       # what is missing (no privileges needed)
sudo tools/ci-runner/setup-rootless-docker.sh --dry-run
sudo tools/ci-runner/setup-rootless-docker.sh --apply --restart-runner
```

`--restart-runner` restarts the runner only when no job is running; otherwise run
`sudo systemctl restart actions.runner.afawzy70-Lawdesk-v1.contabo.service` when it is idle. The script ends by
verifying its own result (daemon rootless, isolation from `/srv/deployments`, Matbakhy and the host socket).

Move the deploy secrets into the `staging` environment (needs the key; owner):

```bash
gh secret set LAWDESK_STAGING_SSH_KEY --env staging --repo afawzy70/Lawdesk-v1 < /path/to/the/ci/private/key
gh secret set LAWDESK_STAGING_KNOWN_HOSTS --env staging --repo afawzy70/Lawdesk-v1 < /path/to/known_hosts
gh api -X PUT repos/afawzy70/Lawdesk-v1/environments/staging \
  -F 'deployment_branch_policy[protected_branches]=false' -F 'deployment_branch_policy[custom_branch_policies]=true'
gh api -X POST repos/afawzy70/Lawdesk-v1/environments/staging/deployment-branch-policies -f name=develop
gh secret delete LAWDESK_STAGING_SSH_KEY --repo afawzy70/Lawdesk-v1        # only after one deploy worked
gh secret delete LAWDESK_STAGING_KNOWN_HOSTS --repo afawzy70/Lawdesk-v1
```

(Environment branch restrictions may not be offered on this plan for private repositories; if the second command is
refused, keep the secrets at repository level and rely on the residual-risk notes above.)

## Monitoring and maintenance

* State: `systemctl status actions.runner.afawzy70-Lawdesk-v1.contabo.service` (the daemon: `sudo tools/ci-runner/setup-rootless-docker.sh --check`); GitHub
  shows the runner under Settings → Actions → Runners (online/busy).
* Logs: `journalctl -u actions.runner.afawzy70-Lawdesk-v1.contabo.service -u lawdesk-ci-maintenance.service` and, for the
  daemon, `journalctl _UID=$(id -u lawdesk-ci) -u lawdesk-ci-docker.service`; job logs under `/home/lawdesk-ci/actions-runner/_diag` (readable by `lawdesk-ci`).
* Daily at ~04:30 `lawdesk-ci-maintenance.timer` prunes the daemon and writes a health report (disk, daemon, services);
  the service fails, and so shows in `systemctl --failed`, when the disk is at or above 80 % or a service is down.
* Usage right now: `systemd-cgtop`, `sudo -u lawdesk-ci DOCKER_HOST=unix:///run/user/$(id -u lawdesk-ci)/docker.sock
  docker system df`.
* The runner updates itself; a manual update is a normal GitHub runner upgrade done as `lawdesk-ci`.
* Heavy load, stress or soak tests must not run here (delivery strategy): the limits above are for CI, not for them.

## Rollback

* **Workflows:** revert the migration commit. Hosted jobs then queue again, and are refused while the billing hold
  lasts.
* **One job stuck:** cancel the run in GitHub; `sudo systemctl restart actions.runner.afawzy70-Lawdesk-v1.contabo.service`.
* **Docker daemon misbehaving:** `sudo runuser -u lawdesk-ci -- env XDG_RUNTIME_DIR=/run/user/$(id -u lawdesk-ci)
  systemctl --user restart lawdesk-ci-docker.service`; its data is under `/home/lawdesk-ci/.local/share/docker` and
  can be deleted by `lawdesk-ci` (nothing else uses it).
* **Remove the setup:** `sudo systemctl disable --now lawdesk-ci-maintenance.timer`, `sudo loginctl disable-linger
  lawdesk-ci`, delete `/etc/systemd/user/lawdesk-ci-docker.service`, the maintenance units in `/etc/systemd/system`, the
  drop-ins `actions.runner….service.d/lawdesk-limits.conf` and `user-<uid>.slice.d/lawdesk-limits.conf`, and
  `/usr/local/libexec/lawdesk-ci-maintenance.sh`, `systemctl daemon-reload`, and take `DOCKER_HOST` out of the runner's
  `.env`. The sub-ID range of `lawdesk-ci` is harmless to leave.

## Evidence

Filled in as the migration is validated (pull request #50 and the merge): see `docs/evidence/LD-041/`.
