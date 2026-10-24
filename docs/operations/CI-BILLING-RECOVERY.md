# GitHub Actions billing recovery and runner decision

Status: investigation and proof preparation, 2026-10-24. No self-hosted job has run. Do not treat this document or the scheduling probe as a green CI result.

## Execution preflight and current blocker

On 2026-10-24 the available agent host ran as `aiagent`, exposed `/srv/ai-workspaces/secrets` to that identity, and rejected an unprivileged user/mount namespace with `unshare: Operation not permitted`. It therefore fails the required execution boundary for this proof. The local `gh` credential reports invalid, and the connected GitHub app rejects the repository runner-administration endpoint. No separate VM or runner registration capability is attached to this session. The current-head PR #49 probe is queued with no runner or steps; its outcome is **NOT_RUN**, not PASS or a proven billing rejection. The minimal external action is for the operator to provide the isolated VM and enter a short-lived registration token there using the procedure below. No token should be sent to this session.

## Observed blockage

- CI run [#160](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40118354959) on `develop` at `19ad5386fa586e2469ecad36a677632273712f44` executed all six quality jobs and the develop guard successfully. It deployed staging.
- The next observed runs [#161](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40119990135) through [#167](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40122337834) failed before any quality job received a runner. In #167, the six quality jobs have `runner_name: ""`, `steps: []`, and completed within seven seconds. The same pattern appears on PRs [#43](https://github.com/afawzy70/Lawdesk-v1/pull/43), [#44](https://github.com/afawzy70/Lawdesk-v1/pull/44), [#47](https://github.com/afawzy70/Lawdesk-v1/pull/47), and [#48](https://github.com/afawzy70/Lawdesk-v1/pull/48).
- Owner-provided Billing screenshots on 2026-10-24 show GitHub Free, **2,000 / 2,000 Actions minutes** and **0.5 / 0.5 GB Actions storage** used. Gross Actions usage is **$350.29**, the included-usage discount is **$350.29**, and current billable Actions usage is **$0**. The gross figure is not an outstanding payment. Of that gross figure, Matbakhy-v1 is $338.07 and Lawdesk-v1 is $12.00. The exhausted hosted-runner allowance is confirmed; the screenshots provided so far do not show the Actions budget, payment-method state, or exact run banner. An additional spending stop or account payment restriction is still possible. The connected GitHub app cannot read those account settings. Do not equate a pre-run failure with a failed application test.

## Decision gate

GitHub documents that self-hosted runner execution is free of Actions minutes. This makes a runner a *possible* answer to the confirmed hosted-compute quota, but it does not prove that GitHub will execute a self-hosted job while this account is blocked. The [PR #49 probe checks](https://github.com/afawzy70/Lawdesk-v1/pull/49/checks) show the current-head job pending under label `lawdesk-ci-poc-49`, while its hosted CI jobs failed before a step ran. A queued job is admission evidence, not execution evidence. A payment failure, spending stop, policy restriction, or account hold may have different effects. The only accepted GO evidence is the `Self-hosted scheduling probe` job executing on a temporary runner and appearing as a successful check on this PR. Until then, migration is NO-GO.

## Proof procedure

1. Confirm the exact account notice and whether self-hosted runners are allowed. The owner must give only the message and Actions usage/budget state, without payment details.
2. Use a fresh, disposable VM outside the shared LawDesk/Matbakhy host. Create an unprivileged identity with no sudo, Docker socket, deployment network access, or secrets. One vCPU, 1 GiB RAM and 25 GiB disk suffice for this no-checkout probe only. Full Java, frontend and Docker CI need separate sizing after proof.
3. On that VM only, the operator creates the `lawdesk-poc` user (without sudo or privileged groups) and a root-owned `/etc/lawdesk-ci-poc-vm` marker after verifying firewall and filesystem isolation. Obtain a repository runner registration token in GitHub Settings → Actions → Runners. Put it in a mode-0600 file owned by `lawdesk-poc`; do not paste it into a job, log, PR, or shell history. As `lawdesk-poc`, set `RUNNER_REGISTRATION_TOKEN_FILE` to that file and a unique `RUNNER_NAME=lawdesk-ci-poc-<id>`, then run `bash tools/ci-runner/bootstrap-poc.sh`. The script pins runner v2.338.1 by the release asset SHA-256, registers the one-job ephemeral runner with label `lawdesk-ci-poc-49`, removes the token file, and starts the runner. Older queued proof attempts have the retired `lawdesk-ci-poc` label and cannot take this runner. Its checks help catch accidental execution on the shared host, but the operator must verify VM isolation independently.
4. Record the GitHub job ID, runner identity, `LAWDESK_CI_POC_EXECUTED` line, nonroot and absent-Docker-socket assertions, and the PR check conclusion. If the job is rejected or remains unscheduled, cancel the proof and destroy the VM/runner registration. A queued job is not proof.
5. Remove any stale registration in GitHub Settings → Actions → Runners, then destroy the VM and its disk. Keep the workflow until the evidence is reviewed, then remove it in the migration PR or a follow-up. The proof must not use application secrets or the deployment environment.

### Cost and exact one-time operator setup (proposal; do not purchase without approval)

A [DigitalOcean Basic 1-vCPU / 1-GiB / 25-GiB Droplet](https://www.digitalocean.com/pricing/droplets) is a concrete disposable x64 example: **$0.00893/hour**, capped at **$6/month** before tax. A 24-hour proof is approximately **$0.22 before tax** if the Droplet is deleted. The [Cloud Firewall](https://docs.digitalocean.com/products/networking/firewalls/details/) has no extra charge. This is a POC estimate, not a permanent CI capacity quote. An already available isolated VM would avoid a new VM purchase if it meets the same boundaries.

After owner approval, the one-time procedure is:

1. In DigitalOcean, create **one** Ubuntu 24.04 x64 Basic Droplet, 1 vCPU / 1 GiB / 25 GiB, in a region the operator can access. Disable backups and paid add-ons. Do not connect it to Contabo, Tailscale, any production VPC, or shared storage. Attach a Cloud Firewall with SSH inbound only from the operator's current public IPv4 `/32` for bootstrap; allow no other inbound traffic. Do not add deployment credentials. [Droplet creation instructions](https://docs.digitalocean.com/products/droplets/how-to/create/).
2. On the operator's trusted workstation, in a LawDesk clone, fetch `infra/actions-billing-runner` and copy **only** `tools/ci-runner/bootstrap-poc.sh` to the VM through that temporary SSH rule. The operator reviews the exact PR head SHA before using it; do not run application code on this VM. Replace `VM_IP` and `SSH_KEY` with the new VM's details:

   ```bash
   git fetch origin infra/actions-billing-runner
   git show FETCH_HEAD:tools/ci-runner/bootstrap-poc.sh > /tmp/bootstrap-poc.sh
   echo '5c4a6ce19e121e7cd6f7594e0db16be68781a7fe1c8888b73d8308878aebb45a  /tmp/bootstrap-poc.sh' | sha256sum --check
   scp -i SSH_KEY /tmp/bootstrap-poc.sh root@VM_IP:/tmp/bootstrap-poc.sh
   ssh -i SSH_KEY root@VM_IP
   ```
3. On the **new VM as root**, verify its provider identity and that it has no Contabo/private route or mounted host volumes. Then run:

   ```bash
   apt-get update && apt-get install -y ca-certificates curl ufw liblttng-ust1t64 libkrb5-3 zlib1g libssl3t64 libicu74
   useradd --create-home --shell /bin/bash lawdesk-poc
   passwd -l lawdesk-poc
   install -o root -g root -m 0444 /dev/null /etc/lawdesk-ci-poc-vm
   install -o lawdesk-poc -g lawdesk-poc -m 0500 /tmp/bootstrap-poc.sh /home/lawdesk-poc/bootstrap-poc.sh
   rm /tmp/bootstrap-poc.sh
   ufw default deny incoming
   ufw default allow outgoing
   ufw deny out to 167.86.90.174
   ufw allow from OPERATOR_PUBLIC_IP to any port 22 proto tcp
   ufw --force enable
   id lawdesk-poc
   test ! -e /var/run/docker.sock
   ufw status numbered
   ```

   Replace `OPERATOR_PUBLIC_IP` with the operator's actual address. The Contabo IP denial covers both LawDesk and Matbakhy on that shared host. The Cloud Firewall also prevents non-operator inbound access during setup.
4. On GitHub, open Lawdesk-v1 → Settings → Actions → Runners → New self-hosted runner and copy the **short-lived registration token**. In the existing VM session run `su - lawdesk-poc`, then enter the token through a non-echoing prompt and start the runner:

   ```bash
   umask 077
   read -rsp 'Registration token: ' POC_TOKEN; printf '\n'
   printf '%s' "$POC_TOKEN" > "$HOME/registration-token"
   unset POC_TOKEN
   RUNNER_REGISTRATION_TOKEN_FILE="$HOME/registration-token" \
     RUNNER_NAME=lawdesk-ci-poc-49-1 \
     nohup bash "$HOME/bootstrap-poc.sh" > "$HOME/runner.log" 2>&1 < /dev/null &
   ```

   Do not paste the token into a command, PR, log, or chat. The script deletes the token file after registration. The runner receives at most one job and has no checkout, Docker socket, sudo or secrets.
5. Once the runner is connected, remove the SSH allow rule from both the Cloud Firewall and UFW. Check the latest PR #49 probe job ID and exact head SHA. Record whether it executed and passed; `queued`, `cancelled`, and `skipped` are not PASS. Remove any stale runner registration in GitHub and **delete** the Droplet afterward; powering it off does not complete cleanup or stop all charges.

The bootstrap alone cannot prove the VM's firewall, cloud isolation, or absence of other credentials. The operator must inspect those settings and record the result before marking the security proof complete.

## Matbakhy gross usage investigation (read-only)

- GitHub currently reports [Matbakhy-v1](https://github.com/afawzy70/Matbakhy-v1) as public, with 2,123 historical Actions runs. In the latest 100 runs (2026-10-22, about five hours), 12 were native Android/iOS PR builds. [One native run](https://github.com/afawzy70/Matbakhy-v1/actions/runs/39786216264) used about 38 minutes for Android and 40 minutes for iOS; its [workflow](https://github.com/afawzy70/Matbakhy-v1/blob/develop/.github/workflows/native-build.yml) runs both on every PR to `develop`, regardless of changed paths. The [Product CI workflow](https://github.com/afawzy70/Matbakhy-v1/blob/develop/.github/workflows/ci.yml) runs on every push and PR, often duplicating work for the same change.
- These observations explain high gross metered activity, but do **not** prove that Matbakhy consumed the private-repository 2,000-minute allowance. GitHub says public standard hosted jobs have no billable minutes. The owner's screenshot reports the entire $350.29 gross amount discounted and $0 currently billable; inspect the Billing usage breakdown by runner SKU and repository before attributing quota depletion to Matbakhy.
- Safe recommendations for Matbakhy's owner to review, without modifying that repository here: add paths filtering to native Android/iOS PR builds for mobile/toolchain changes while retaining required status behavior; avoid duplicate push and PR Product CI runs for the same head where branch policy permits; keep concurrency cancellation for superseded PR heads; limit artifact retention and delete expired artifacts through the normal UI after checking evidence retention. Do not disable required native or security checks just to reduce gross usage.

## Production design after a successful proof

- Prefer a dedicated lightweight VM with ephemeral runners. The shared Contabo server houses both LawDesk and Matbakhy; a long-lived runner account on that host cannot establish the required trust boundary by Unix permissions alone. If separate VM isolation is unavailable, do not use this host for untrusted PR CI.
- Grant the CI VM no route to Contabo private services or deployment SSH, and no credentials for LawDesk or Matbakhy. Keep repository token permission at `contents: read` except the existing `develop-guard` read scopes. Avoid exposing the CI job to production/staging keys.
- Run one job per VM instance, wipe or destroy the instance afterward, bound CPU/memory/disk/concurrency, and apply image and runner updates. Avoid a host Docker socket. Docker-based PostgreSQL and stack smoke jobs require a separate isolated build VM with an ephemeral container daemon; defer them until that boundary is verified.
- Keep the existing job names and commands so historical check identity and dispatch guard stay valid. Migrate policy, frontend, backend, secret scan, and then `develop-guard` one at a time only after the probe succeeds. The guard needs its current read-only GitHub scopes and must still gate every push to `develop`.
- Automatic deployment also uses a GitHub-hosted runner today. Moving only CI would leave it blocked. The immediate safe path is to restore hosted billing for the existing deployment job. A self-hosted alternative would need a **separate, disposable** deployment VM with workflow-level runner admission enforced outside an editable PR workflow, and the current forced-command SSH gate key delivered only to the approved `develop` deploy job. A repository label alone is not an access control: a PR can request that label. Unless that admission boundary is proven, deployment migration is NO-GO. Never put the deployment key on the general CI VM.
- A failed or skipped check is never reported as PASS. Existing branch protection is unavailable on this private repository's plan; the `develop-guard` is detection after a push, not server-side prevention. Preserve the owner merge procedure in `docs/delivery/DEVELOP-PROTECTION.md`.

### Incremental CI and CD migration sequence (conditional on a PASS proof)

1. Recheck the account budget/payment state and confirm the proof ran on an isolated VM at the exact PR SHA. Destroy the POC VM before general CI work. Produce a new, reviewable migration commit on this infrastructure branch; keep the PR draft until its own checks really pass.
2. Provision a dedicated ephemeral **PR CI** VM per job or a verified clean-image reset process. Size it from actual test measurements, starting with a bounded 2-vCPU / 8-GiB class for Java and frontend. No Docker daemon, host socket, Contabo route, SSH deploy key, or persistent workspace. Limit concurrency to one while observing resource use and credential cleanup. Move `Repository policy`, `Frontend (Node 22)`, `Backend (Java 21, embedded PostgreSQL 18.6)`, and `Secret scan (gitleaks 8.30.1)` one at a time, preserving names, commands, versions, timeouts, permissions and failure behavior. Run a real PR after each move and retain the previous workflow definition for rollback.
3. Use a **separate disposable build VM** for `Backend against postgres:18.6 image` and `Staging stack (images + compose smoke)`. Only a rootless, job-local container engine inside that disposable VM may serve those jobs; no shared-host Docker socket. Keep the exact PostgreSQL image and stack smoke/rollback commands. Prove network and storage isolation, resource caps, volume cleanup and VM destruction on real PR runs before these jobs count as restored.
4. Migrate `Develop guard (QA-cleared merge)` only after its GitHub API reads and historical-check logic pass on the new runner. Retain its exact name and scopes. Do not move or bypass the `deploy-staging` dependency list. Keep repository policy tests, ShellCheck/actionlint, gitleaks, npm audit and any later SAST/SCA gates real and required; a skipped job does not satisfy the gate.
5. **CD path A (preferred):** keep `.github/workflows/deploy-staging.yml` on a GitHub-hosted runner and restore hosted availability through an owner-approved billing/budget remedy or allowance reset. This preserves the existing staging environment secrets, forced-command SSH gate, branch/SHA checks, and automatic push-to-`develop` behavior. Verify one approved merge's CI and deployment, exact release SHA, and public health checks before declaring CD restored.
6. **CD path B (only if path A is unavailable):** design a separate deployment VM and an admission control that GitHub enforces for the *specific trusted workflow/ref*, not merely a runner label. A personal-repository runner label can be requested by an editable PR workflow and is therefore insufficient for an SSH-capable runner. Do not implement path B without owner approval of the security architecture and a **keyless** admission test in which GitHub rejects an untrusted PR job. Only then may the deployment key be delivered to the approved job. If such admission cannot be enforced on the current account/repository, CD migration remains NO-GO and hosted billing must be fixed.
7. After a reviewed infrastructure merge, trigger **new** PR check suites for #43, #44, #47 and #48 at their exact heads without editing their feature code. Coordinate with their owners to update against `develop` or reopen the PRs as appropriate. Old pre-run failures and reruns of old workflow definitions do not count as a restored check. Merge application PRs only under the owner procedure and after their actual required checks pass.

## Rollback

If scheduling, security, or test behavior regresses, stop assigning new jobs to the self-hosted label, remove the runner registration, and revert only the runner `runs-on` changes. Restore the prior `ubuntu-24.04` labels and require the original checks to execute successfully before merging or deploying. Do not weaken `deploy-staging` dependencies or manually manufacture success statuses.

## Operator handoff still required

- Exact run failure banner and Actions billing/usage/budget state.
- A disposable VM and repository runner registration permission for the proof, once approved.
- A separate decision on permanent VM capacity and network isolation after proof results.
