#!/usr/bin/env bash
# One repeatable operator command for LawDesk staging on Contabo: the first install and any later re-run. Run it as
# `devops` from a fresh checkout of develop, made with this account's existing GitHub access:
#
#   rm -rf /tmp/lawdesk-install && git clone -q -b develop git@github.com:afawzy70/Lawdesk-v1.git /tmp/lawdesk-install \
#     && /tmp/lawdesk-install/deploy/host/install.sh
#
# Steps; each is logged, and the first failure stops the run with diagnostics and the recovery commands:
#   1. the checkout must be the current develop head; Matbakhy's containers are recorded
#   2. bootstrap (idempotent: keeps the database secrets, the volume and existing keys) with a one-time CI key that
#      authorized_keys binds to the gate, so CI can never open a shell
#   3. install the root-owned gate when it changed (the only sudo step)
#   4. gate deploy <sha>: builds only missing or mislabelled images, ensures the database role (repairs the existing
#      volume, deletes nothing), smoke-tests, rolls back on failure
#   5. db, backend and web healthy; only then the hostname is attached to the shared Caddy (attach.sh backs up,
#      validates, compares every Matbakhy host and restores on any difference)
#   6. public HTTPS with a trusted certificate; no Matbakhy container restarted
#   7. CD: the CI key into GitHub Secrets (then deleted here), variables, and LAWDESK_STAGING_DEPLOY=enabled last
# Output goes to logs/<ts>-install.log and to a world-readable report (no secrets) for the agent.
# Exit codes: 0 installed, 2 refused before any change, other: failed at the step named in the report.
set -Eeuo pipefail
umask 027

src=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
home=${LAWDESK_HOME:-/srv/deployments/lawdesk}
gate=${LAWDESK_GATE:-/usr/local/sbin/lawdesk-deploy-gate}
report=${LAWDESK_REPORT:-/tmp/lawdesk-install-report.txt}
public_host=${LAWDESK_PUBLIC_HOST:-lawdesk.167-86-90-174.sslip.io}
ssh_target=${LAWDESK_SSH_TARGET:-devops@167.86.90.174}
github_repo=${LAWDESK_GITHUB_REPO:-afawzy70/Lawdesk-v1}
host_key_file=${LAWDESK_SSH_HOST_KEY:-/etc/ssh/ssh_host_ed25519_key.pub}
ci_key=$HOME/.ssh/lawdesk_ci_deploy
export LAWDESK_HOME=$home LAWDESK_SSH_HOST_KEY=$host_key_file

mkdir -p "$home/logs" "$home/state"
log=$home/logs/$(date -u +%Y%m%dT%H%M%SZ)-install.log
: > "$report"
chmod 644 "$report"

current="start"
sha=""

step() {
    current=$1
    echo "== $(date -u +%FT%TZ) $1"
}

matbakhy_containers() {
    local c
    for c in $(docker ps -q --filter label=com.docker.compose.project=matbakhy-preview); do
        docker inspect -f '{{.Name}} {{.State.StartedAt}} {{.RestartCount}}' "$c"
    done | sort
}

https() {
    curl -fsS --proto =https --tlsv1.2 --max-time 15 "https://$public_host$1"
}

github_cli() {
    command -v gh > /dev/null && gh auth status > /dev/null 2>&1
}

# shellcheck disable=SC2329 # invoked through the ERR trap
failed() {
    local status=$?
    trap - ERR
    # The failing command may have had its output redirected; the report goes to the log stream (fd 3) regardless.
    exec 1>&3 2>&3
    echo "=== FAILED during: $current (exit $status)"
    echo "--- diagnostics"
    docker ps -a --filter label=com.docker.compose.project=lawdesk-staging --format '{{.Names}} {{.Status}}' || true
    local s
    for s in db backend web; do
        echo "--- lawdesk-staging-$s-1 (last 60 lines)"
        docker logs --tail 60 "lawdesk-staging-$s-1" 2>&1 | grep -vi 'password=' || true
    done
    tail -n 15 "$home/state/history.log" 2>/dev/null || true
    cat << EOF
--- Recovery (Matbakhy is never stopped by this command; the database volume is never removed)
  state and history:      $gate status
  previous release:       $gate rollback
  remove the HTTPS route: $home/releases/${sha:-<sha>}/deploy/proxy/detach.sh   (LawDesk site only)
  after a fix:            run this command again; every step is idempotent
  full log:               $log
EOF
    exit "$status"
}

main() {
    exec 3>&1
    trap failed ERR

    step "1/7 checkout is the develop head"
    git -C "$src" fetch -q origin develop
    sha=$(git -C "$src" rev-parse HEAD)
    local head url
    head=$(git -C "$src" rev-parse FETCH_HEAD)
    url=$(git -C "$src" remote get-url origin)
    if [[ $sha != "$head" ]]; then
        echo "install: this checkout ($sha) is not the develop head ($head); clone develop again" >&2
        exit 2
    fi
    echo "deploying $sha (develop head, fetched from $url)"
    matbakhy_containers > "$home/state/matbakhy-before.txt"

    step "2/7 bootstrap with a one-time CI key bound to the gate"
    install -d -m 700 "$HOME/.ssh"
    [[ -f $ci_key.pub ]] || ssh-keygen -q -t ed25519 -N "" -C lawdesk-ci-deploy -f "$ci_key"
    LAWDESK_FETCH_WITH=existing "$src/deploy/host/bootstrap.sh" "$ci_key.pub" | grep -v '^     ' || true
    git -C "$home/repo.git" remote set-url origin "$url"
    case $url in
        https://*) git -C "$home/repo.git" config --unset core.sshCommand || true ;;
    esac

    step "3/7 root-owned gate"
    if cmp -s "$src/deploy/host/lawdesk-deploy-gate" "$gate"; then
        echo "gate unchanged: $gate"
    else
        sudo install -o root -g root -m 0755 "$src/deploy/host/lawdesk-deploy-gate" "$gate"
        cmp -s "$src/deploy/host/lawdesk-deploy-gate" "$gate"
        echo "gate installed: $gate"
    fi

    step "4/7 deploy $sha through the gate"
    "$gate" deploy "$sha"
    "$gate" status | head -n 2

    step "5/7 containers healthy, then the HTTPS route"
    local s status
    for s in db backend web; do
        status=$(docker inspect -f '{{.State.Health.Status}}' "lawdesk-staging-$s-1")
        echo "lawdesk-staging-$s-1 $status"
        [[ $status == healthy ]]
    done
    LAWDESK_PROXY_BACKUP_DIR=$home/proxy-backups "$src/deploy/proxy/attach.sh"

    step "6/7 public HTTPS and Matbakhy"
    for _ in $(seq 1 "${LAWDESK_HTTPS_TRIES:-24}"); do
        https /healthz > /dev/null && break
        sleep "${LAWDESK_HTTPS_WAIT:-5}"
    done
    https /healthz > /dev/null
    https / | grep -q '<title>LawDesk'
    https /actuator/health/readiness | grep -q '"status":"UP"'
    echo "https://$public_host/ answers with a trusted certificate"
    matbakhy_containers > "$home/state/matbakhy-after.txt"
    if ! diff "$home/state/matbakhy-before.txt" "$home/state/matbakhy-after.txt"; then
        echo "Matbakhy containers restarted during the install (see the diff above)"
        false
    fi
    echo "Matbakhy: no container restarted"

    step "7/7 automatic deployment"
    if ! github_cli; then
        cat << EOF
CD PENDING: this account has no authenticated GitHub CLI. From the owner's workstation, once:
  ssh $ssh_target 'cat ~/.ssh/lawdesk_ci_deploy' | gh secret set LAWDESK_STAGING_SSH_KEY --repo $github_repo \\
    && ssh $ssh_target 'shred -u ~/.ssh/lawdesk_ci_deploy'
  gh secret set LAWDESK_STAGING_KNOWN_HOSTS --repo $github_repo --body "${ssh_target#*@} $(cut -d ' ' -f 1,2 "$host_key_file")"
  for v in LAWDESK_STAGING_SSH_TARGET=$ssh_target LAWDESK_STAGING_URL=https://$public_host LAWDESK_DEVELOP_POLICY=uat \\
           LAWDESK_STAGING_DEPLOY=enabled; do gh variable set "\${v%%=*}" --repo $github_repo --body "\${v#*=}"; done
EOF
        echo "LAWDESK INSTALLED $sha: https://$public_host/ (CD pending)"
        return 0
    fi
    if [[ -f $ci_key ]]; then
        gh secret set LAWDESK_STAGING_SSH_KEY --repo "$github_repo" < "$ci_key"
        shred -u "$ci_key"
        echo "CI key stored in GitHub Secrets and deleted here; it can only run the gate"
    fi
    gh secret set LAWDESK_STAGING_KNOWN_HOSTS --repo "$github_repo" \
        --body "${ssh_target#*@} $(cut -d ' ' -f 1,2 "$host_key_file")" < /dev/null
    gh variable set LAWDESK_STAGING_SSH_TARGET --repo "$github_repo" --body "$ssh_target"
    gh variable set LAWDESK_STAGING_URL --repo "$github_repo" --body "https://$public_host"
    gh variable set LAWDESK_DEVELOP_POLICY --repo "$github_repo" --body uat
    gh variable set LAWDESK_STAGING_DEPLOY --repo "$github_repo" --body enabled
    echo "LAWDESK INSTALLED $sha: https://$public_host/ (CD enabled)"
}

main "$@" 2>&1 | tee -a "$log" "$report"
exit "${PIPESTATUS[0]}"
