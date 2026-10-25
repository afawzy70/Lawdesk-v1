# CI runners: GitHub-hosted by default, the Contabo runner as the alternative (LD-041)

Owner decision 2026-10-25: use **both** GitHub-hosted runners and the runner `contabo` on the Contabo server. Hosted
runners are the default for speed and isolation; the Contabo runner stays operational as a switchable alternative for
billing recovery and cost control. This file says how jobs are allocated, what the Contabo runner can and cannot
reach, how it was set up once, and how to switch and roll back.

## Allocation and the switch

| Job (name unchanged) | Default | With `LAWDESK_CI_RUNNER=self-hosted` |
|----------------------|---------|--------------------------------------|
| Repository policy | GitHub-hosted `ubuntu-24.04` | Contabo |
| Backend (Java 21, embedded PostgreSQL 18.6) | hosted | Contabo |
| Backend against postgres:18.6 image | hosted | Contabo (rootless Docker) |
| Frontend (Node 22) | hosted | Contabo |
| Staging stack (images + compose smoke) | hosted | Contabo (rootless Docker) |
| Secret scan (gitleaks 8.30.1) | hosted | Contabo |
| Develop guard (QA-cleared merge) | hosted | Contabo |
| Deploy to staging (`deploy-staging.yml`) | hosted | **hosted always**: no switch moves it |

* **One switch, not two copies.** Every job of `ci.yml` reads the same expression:
  `runs-on: ${{ vars.LAWDESK_CI_RUNNER == 'self-hosted' && fromJSON('["self-hosted","Linux","X64"]') || 'ubuntu-24.04' }}`.
  The suite is never run on both kinds of runner at once. Job IDs, names (the check names), commands, scans and
  artifacts are identical on both paths.
* **Billing recovery or cost control:** `gh variable set LAWDESK_CI_RUNNER --body self-hosted --repo afawzy70/Lawdesk-v1`
  sends every following job to the Contabo runner, and `gh variable delete LAWDESK_CI_RUNNER` returns to hosted. No
  commit is needed. Deployment stays on hosted runners; while hosted jobs are refused, deploy by hand as the runbook's
  first deployment does (`lawdesk-deploy-gate deploy <sha>` as `devops`).
* **Queueing.** The Contabo runner executes one job at a time, so with the switch on a run takes the sum of its jobs.
  GitHub queues naturally; do not add runners without measured need and the owner's approval.
* **Draft pull requests** skip their gate jobs on either path; marking one ready for review starts its run.
  `concurrency` cancels a superseded run of the same pull request, and never a push to `develop` or a deployment
  (`deploy-staging.yml` has its own non-cancelling group).

### Measured (docs/evidence/LD-041/12-ci-duration-comparison.md)

| | GitHub-hosted | Contabo runner |
|---|---:|---:|
| Wall-clock of one CI run | 4.2 to 5.0 min (jobs in parallel) | 20 to 23 min (jobs one after another) |
| Slowest job | 3.0 to 3.6 min | 5.9 to 6.1 min |
| Sum of job minutes | 12 to 13 | 20 to 23 |
| Billed minutes (per job, rounded up) | 15 to 17 | none from GitHub today |

Hosted is about five times faster per run. Cost impact: a run bills about 16 hosted minutes; at the published
GitHub-hosted Linux 2-core rate of about $0.006 per minute that is roughly **$0.10 per run**, so 100 runs a month are
about 1,600 minutes (about $10 beyond the plan's included minutes, if any are used up). The rate and the included
minutes depend on the owner's plan and should be checked in Billing & plans. Draft skipping, cancellation of
superseded runs and cohesive pull requests are what keep the number of runs down. The Contabo runner costs no GitHub
minutes today (GitHub announced and postponed a per-minute charge for self-hosted runners) but spends the shared
server's CPU and memory.

## What the Contabo runner is

| Part | Value |
|------|-------|
| Runner | `contabo`, labels `self-hosted`, `Linux`, `X64` |
| Service | `actions.runner.afawzy70-Lawdesk-v1.contabo.service` (systemd, enabled, restarts on boot) |
| Directory | `/home/lawdesk-ci/actions-runner` (work in `_work/`) |
| Identity | the unprivileged user `lawdesk-ci` (no sudo, not in the `docker` group, mode-0750 home) |
| Docker | the runner's **own rootless daemon**, the user unit `lawdesk-ci-docker.service` of `lawdesk-ci` (root-owned file in `/etc/systemd/user`), socket `/run/user/<uid>/docker.sock`, reached through `DOCKER_HOST` in the runner's `.env` |

Evidence that it runs jobs while hosted jobs are refused: proof-of-concept run
[40168609293](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40168609293); both Docker jobs failed for exactly one
reason on the first full run (`permission denied … /var/run/docker.sock`, run 40170308828), and all six jobs passed on
runs [40172127852](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40172127852) and
[40174327139](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40174327139). The account billing problem itself is a
GitHub matter; the first hosted job executed after the plan upgrade is recorded in the evidence file above.

Tools there: Java 21 and Node 22 from `actions/setup-java` and `actions/setup-node`, Python 3.12 from
`actions/setup-python`; ShellCheck, actionlint and gitleaks are downloaded with pinned checksums on both paths.
Maven and npm caches persist in the runner's home between jobs.

## Security boundaries

| Boundary | How it holds |
|----------|--------------|
| Host Docker and Matbakhy | `lawdesk-ci` cannot open `/var/run/docker.sock` (verified: `permission denied` in a real job, run 40170308828). Docker-dependent steps use the rootless daemon, whose containers, images and networks are invisible to the host daemon. The CI stack is named `lawdesk-staging` and uses a network called `lawdesk-edge`, the names of the real staging stack, so it must never meet the host daemon |
| Root | no sudo, no setuid helpers beyond `newuidmap`/`newgidmap`, no capabilities. The daemon unit grants nothing to the user |
| Secrets | the CI jobs read no secret (`tools/tests/test_ci_workflow.py` pins this). Only the deploy job reads the two `LAWDESK_STAGING_*` secrets, and it runs on GitHub-hosted runners only |
| Deployment | unchanged: the deploy job (hosted, ephemeral) reaches the host through the SSH forced command `deploy/host/lawdesk-deploy-gate` (`deploy <sha>`, `status`, `rollback`, and only for commits on `develop`). The gate key stays in `$RUNNER_TEMP` and is removed by a final `always()` step; no deployment privilege or host Docker access exists on the Contabo runner |
| Published ports | the database container of the PostgreSQL-image gate is published on `127.0.0.1` with a random port. `services:` is not used, because it would publish on every interface of this public host. The compose stack publishes no host ports |
| Resource use | root-owned: `runner-limits.conf` (runner ≤ 4 CPUs, 7 GiB) and `user-slice-limits.conf` on `user-<uid>.slice` (the daemon with all its containers ≤ 3 CPUs, 5 GiB), bounded tasks, low priority. The user could edit its own unit, so the slice is the ceiling that counts. Ceilings for a host with 23 GiB and 8 cores shared with Matbakhy; jobs run one at a time |
| Cleanup | every Docker job removes its containers, network and images; the daily maintenance timer prunes the daemon's old images, build cache and volumes and flags a disk above 80 % |

### Residual risks (read before relying on the boundary)

1. **When the switch is on, the Contabo runner is persistent and runs pull request code as `lawdesk-ci`.** A malicious
   change to a build script or a dependency could leave something in that user's home, the tool cache, `~/.m2` or the
   runner directory for later jobs of the same runner. With the default (hosted) this does not arise, and because the
   deploy job never runs on the Contabo runner, a foothold there cannot reach the deployment key. Running PR code
   there is a billing-recovery measure, not the normal path. A stronger form (ephemeral just-in-time runner) needs the
   owner's decision.
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
5. **Throughput.** With the switch on, one runner serialises everything: a run takes about 20 minutes and a stack of
   pull requests waits in turn. Add runners only by owner decision and measured need.

## Operator steps for the Contabo runner (done once by the infrastructure administrator)

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

* **Back to hosted only:** `gh variable delete LAWDESK_CI_RUNNER`. **Contabo runner out of service:** leave the variable
  unset; nothing else depends on it.
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
