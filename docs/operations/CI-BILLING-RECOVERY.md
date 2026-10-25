# GitHub Actions billing recovery on Contabo

Status: POC installer prepared on 2026-10-25. **No self-hosted job has executed yet.** PR #49 remains Draft. A queued check is NOT_RUN.

## Billing finding

Owner screenshots show GitHub Free, Actions minutes 2,000/2,000 and storage 0.5/0.5 GB used. Gross metered usage $350.29 is exactly offset by included discounts; current billable Actions usage is $0. This confirms exhausted hosted allowance and does not establish an unpaid $350.29 bill. Hosted CI jobs on Phase 5/6 PRs failed before obtaining runners. The exact Actions budget, payment-method restriction and run banner still require owner-side confirmation. Self-hosted scheduling remains unproven until the harmless job actually succeeds.

## Chosen POC

The owner selected a dedicated Linux account and an official GitHub Actions runner managed by `devops` on the existing Contabo host. [`install-systemd-poc.sh`](../../tools/ci-runner/install-systemd-poc.sh) creates `lawdesk-runner` with no sudo or Docker group, downloads the official runner v2.338.1 and verifies SHA-256, registers one ephemeral job under label `lawdesk-ci-poc-49`, and starts a non-restarting systemd unit. The job has no checkout, application secrets or deployment key.

The systemd unit makes the host filesystem read-only except the runner's own directory; hides home, deployment and AI workspace paths; masks the host Docker socket; drops capabilities; sets no-new-privileges and a seccomp filter; and limits the POC to 0.5 CPU, 1 GiB RAM, zero swap and 128 tasks. Its IP rules apply to **this service only**, not global firewall defaults: it denies the Contabo public IP, private/metadata ranges and loopback other than the system DNS stub. No Matbakhy container, deployment or Caddy setting is changed. The harmless workflow checks UID, readability of sensitive paths and socket, capability/seccomp state, and actual cgroup limits. These checks are necessary but do not turn a persistent runner into a safe environment for arbitrary PR code.

The current `aiagent` session cannot perform installation: `sudo -n` requires a password, Docker API access is denied and existing SSH authentication as `devops` was rejected. No registration token should be sent to this session.

## One-time operator action for the POC

As `devops` on Contabo, review PR #49's installer, then execute this single command. It copies the staged file to a root-owned path, verifies the exact reviewed bytes, and runs it. The installer prompts locally and without echo for the short-lived repository runner registration token from Lawdesk-v1 → Settings → Actions → Runners. Never paste the token into a command, chat, issue or log.

```bash
sudo install -o root -g root -m 0500 /tmp/lawdesk-ci-infra-20261024/tools/ci-runner/install-systemd-poc.sh /root/install-systemd-poc.sh && echo 'cd785fb53b1be5ab25046fecbaa612611805714c87147772e38fb755b1ccb0cd  /root/install-systemd-poc.sh' | sudo sha256sum --check && sudo bash /root/install-systemd-poc.sh
```

The installer adds only the native libraries needed for the official runner; it does not yet provision Java, Node or Docker for general CI. If registration or service startup fails, leave PR #49 Draft, record `sudo systemctl status lawdesk-runner-poc.service` and `sudo journalctl -u lawdesk-runner-poc.service -n 100 --no-pager`, and remove any stale runner in the GitHub Runners page. The service is deliberately **not enabled on reboot**; the POC is one job only.

## Required proof evidence

Record the PR #49 head SHA, GitHub run/job IDs, the assigned runner name, an executed `LAWDESK_CI_POC_EXECUTED` step, every isolation assertion, and a successful PR check conclusion. A queued/skipped/cancelled job or a service process alone is not a pass. After the job, confirm the ephemeral runner disappeared in GitHub and stop/disable the unit. Remove `lawdesk-runner` only after logs and result are preserved.

## Existing workflow dependency inventory

| Job | Versions and tools | Required access |
| --- | --- | --- |
| Repository policy | Python 3.12, pip, actionlint 1.7.12, ShellCheck 0.11.0, checkout full history | Git read; no Docker or deployment key |
| Backend | Temurin Java 21, Maven wrapper, embedded PostgreSQL 18.6 | Git read; no host Docker |
| Backend against postgres image | Java 21, Maven wrapper, Docker service `postgres:18.6-alpine` | Isolated container engine; **never** shared host socket |
| Frontend | Node 22, npm, lint, typecheck, coverage, build, `npm audit --omit=dev --audit-level=high` | Git read; internet for packages |
| Staging stack | Docker build, Compose, disposable DB/admin secrets, smoke and rollback drill | Isolated build environment; **never** shared host socket or application volumes |
| Secret scan | gitleaks 8.30.1, full Git history | Git read |
| Develop guard | Python 3, GitHub CLI/API, read-only PR/check scopes | Runs on `develop` push after QA-cleared merge |
| Deploy staging | GitHub CLI, SSH, curl, forced-command LawDesk deployment gate | Separate trusted deploy identity and key; `develop` only |

The current CI has npm audit and gitleaks. Separate SAST/SCA coverage beyond those commands must be inventoried and added explicitly; this PR does not claim a scan passed when it did not run. Job names, commands, versions, reports, branch flow and `needs` dependencies remain unchanged until each migrated job executes successfully.

## Migration and deployment gates

1. Complete the POC first. Install the exact Java/Node/Python/Maven and CI lint/scanner dependencies needed by one job at a time. Migrate policy, frontend, backend and secret scan first, preserving existing job names and permissions. Run a real PR at every step and inspect the exact head SHA and test logs. Keep one runner/job at a time initially.
2. The PostgreSQL image job and stack job need Docker. Membership in the shared `docker` group or mounting `/var/run/docker.sock` would give PR code effective host-root control. Use a verified **runner-owned rootless engine** or another isolated build boundary; otherwise leave these checks blocked and do not merge. Do not silently substitute embedded PostgreSQL for the image test.
3. Automatic staging CD currently uses a hosted runner and a restricted forced-command SSH gate. A PR can request a repository runner label, so a label alone cannot protect a self-hosted deploy runner that holds the SSH key. Keep the key off the ordinary CI identity. Before moving CD, establish a trusted workflow admission boundary for a separate deployment identity; otherwise CD remains blocked. Preserve the current `develop-guard`, all `needs` gates and the release-SHA verification. No develop merge or UAT deployment is claimed in this POC.
4. On GitHub Free with this private personal repository, required deployment reviewers/environment protection are not available as a reliable gate: [GitHub environment documentation](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments). This is a concrete access-control constraint for self-hosted CD. Do not give the general CI runner a deployment key to bypass it.

## Operations and rollback

```bash
sudo systemctl status lawdesk-runner-poc.service
sudo journalctl -u lawdesk-runner-poc.service -n 100 --no-pager
sudo systemctl stop lawdesk-runner-poc.service
sudo systemctl disable lawdesk-runner-poc.service
```

If the proof fails, remove the stale registration in GitHub Settings → Actions → Runners, then on Contabo remove only the POC unit and account after preserving logs:

```bash
sudo systemctl stop lawdesk-runner-poc.service
sudo rm /etc/systemd/system/lawdesk-runner-poc.service
sudo systemctl daemon-reload
sudo userdel -r lawdesk-runner
```

Do not change `develop`, branch protection, Matbakhy, shared Docker daemon settings or Caddy during this rollback. A later permanent runner needs a separate review of service restart/update policy, workspace cleanup and the Docker/CD boundaries.

## Matbakhy usage note

Owner billing screenshot attributes $338.07 gross Actions use to Matbakhy-v1 and $12.00 to Lawdesk-v1, with the full gross amount discounted. Read-only history showed many native Android/iOS PR builds and duplicate push/PR CI. Review path filters, duplicate triggers, concurrency cancellation and artifact retention in Matbakhy's own process. No Matbakhy repository or server change is part of this PR.
