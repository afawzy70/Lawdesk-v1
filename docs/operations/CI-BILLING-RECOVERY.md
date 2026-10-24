# GitHub Actions billing recovery and runner decision

Status: investigation and proof preparation, 2026-10-24. No self-hosted job has run. Do not treat this document or the scheduling probe as a green CI result.

## Observed blockage

- CI run [#160](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40118354959) on `develop` at `19ad5386fa586e2469ecad36a677632273712f44` executed all six quality jobs and the develop guard successfully. It deployed staging.
- The next observed runs [#161](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40119990135) through [#167](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40122337834) failed before any quality job received a runner. In #167, the six quality jobs have `runner_name: ""`, `steps: []`, and completed within seven seconds. The same pattern appears on PRs [#43](https://github.com/afawzy70/Lawdesk-v1/pull/43), [#44](https://github.com/afawzy70/Lawdesk-v1/pull/44), [#47](https://github.com/afawzy70/Lawdesk-v1/pull/47), and [#48](https://github.com/afawzy70/Lawdesk-v1/pull/48).
- GitHub does not provide a job log for these jobs. The connected GitHub app cannot read account billing or branch protection. The exact billing reason remains unconfirmed until the account owner supplies the run banner and Actions usage/budget state. Do not equate a pre-run failure with a failed application test.

## Decision gate

GitHub documents that self-hosted runner execution is free of Actions minutes. This makes a runner a *possible* answer to a hosted-compute quota, but it does not prove that GitHub will schedule a self-hosted job while this account is blocked. A payment failure, spending stop, policy restriction, or account hold may have different effects. The only accepted GO evidence is the `Self-hosted scheduling probe` job executing on a temporary runner and appearing as a successful check on this PR. Until then, migration is NO-GO.

## Proof procedure

1. Confirm the exact account notice and whether self-hosted runners are allowed. The owner must give only the message and Actions usage/budget state, without payment details.
2. Use a fresh, disposable VM outside the shared LawDesk/Matbakhy host. Create an unprivileged identity with no sudo, Docker socket, deployment network access, or secrets. Limit it to one job, two vCPU, 4 GiB RAM, and a bounded disk. These are proposed caps and require operator verification.
3. On that VM only, the operator creates the `lawdesk-poc` user (without sudo or privileged groups) and a root-owned `/etc/lawdesk-ci-poc-vm` marker after verifying firewall and filesystem isolation. Obtain a repository runner registration token in GitHub Settings → Actions → Runners. Put it in a mode-0600 file owned by `lawdesk-poc`; do not paste it into a job, log, PR, or shell history. As `lawdesk-poc`, set `RUNNER_REGISTRATION_TOKEN_FILE` to that file and a unique `RUNNER_NAME=lawdesk-ci-poc-<id>`, then run `bash tools/ci-runner/bootstrap-poc.sh`. The script pins runner v2.338.1 by the release asset SHA-256, registers the one-job ephemeral runner with label `lawdesk-ci-poc`, removes the token file, and starts the runner. Its checks help catch accidental execution on the shared host, but the operator must verify VM isolation independently.
4. Record the GitHub job ID, runner identity, `LAWDESK_CI_POC_EXECUTED` line, nonroot and absent-Docker-socket assertions, and the PR check conclusion. If the job is rejected or remains unscheduled, cancel the proof and destroy the VM/runner registration. A queued job is not proof.
5. Remove any stale registration in GitHub Settings → Actions → Runners, then destroy the VM and its disk. Keep the workflow until the evidence is reviewed, then remove it in the migration PR or a follow-up. The proof must not use application secrets or the deployment environment.

## Production design after a successful proof

- Prefer a dedicated lightweight VM with ephemeral runners. The shared Contabo server houses both LawDesk and Matbakhy; a long-lived runner account on that host cannot establish the required trust boundary by Unix permissions alone. If separate VM isolation is unavailable, do not use this host for untrusted PR CI.
- Grant the CI VM no route to Contabo private services or deployment SSH, and no credentials for LawDesk or Matbakhy. Keep repository token permission at `contents: read` except the existing `develop-guard` read scopes. Avoid exposing the CI job to production/staging keys.
- Run one job per VM instance, wipe or destroy the instance afterward, bound CPU/memory/disk/concurrency, and apply image and runner updates. Avoid a host Docker socket. Docker-based PostgreSQL and stack smoke jobs require a separate isolated build VM with an ephemeral container daemon; defer them until that boundary is verified.
- Keep the existing job names and commands so historical check identity and dispatch guard stay valid. Migrate policy, frontend, backend, and secret scan one at a time only after the probe succeeds. Keep `develop-guard` and the restricted deployment workflow separate; never move deployment credentials to general CI.
- A failed or skipped check is never reported as PASS. Existing branch protection is unavailable on this private repository's plan; the `develop-guard` is detection after a push, not server-side prevention. Preserve the owner merge procedure in `docs/delivery/DEVELOP-PROTECTION.md`.

## Rollback

If scheduling, security, or test behavior regresses, stop assigning new jobs to the self-hosted label, remove the runner registration, and revert only the runner `runs-on` changes. Restore the prior `ubuntu-24.04` labels and require the original checks to execute successfully before merging or deploying. Do not weaken `deploy-staging` dependencies or manually manufacture success statuses.

## Operator handoff still required

- Exact run failure banner and Actions billing/usage/budget state.
- A disposable VM and repository runner registration permission for the proof, once approved.
- A separate decision on permanent VM capacity and network isolation after proof results.
