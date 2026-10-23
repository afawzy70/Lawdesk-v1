#!/usr/bin/env bash
# Deploys one LawDesk release to the staging stack and rolls back automatically if it does not become healthy.
#
#   deploy.sh <40-character commit sha>
#
# Environment: LAWDESK_REGISTRY (required), LAWDESK_STATE_DIR (default /srv/lawdesk-staging/state).
# Exit codes: 0 deployed, 1 failed (rolled back when possible), 2 usage, 3 another deployment running,
# 4 failed and the rollback failed too. Every docker call is scoped to the lawdesk-staging project.
set -euo pipefail

if [[ $# -ne 1 || ! $1 =~ ^[0-9a-f]{40}$ ]]; then
    echo "usage: deploy.sh <40-character lowercase commit sha>" >&2
    exit 2
fi
if [[ -z ${LAWDESK_REGISTRY:-} ]]; then
    echo "deploy: LAWDESK_REGISTRY is not set (see deploy/staging.env.example)" >&2
    exit 2
fi

release=$1
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
deploy_dir=$(dirname "$script_dir")
state_dir=${LAWDESK_STATE_DIR:-/srv/lawdesk-staging/state}
mkdir -p "$state_dir"

exec 9>"$state_dir/deploy.lock"
if ! flock -n 9; then
    echo "deploy: another deployment is in progress" >&2
    exit 3
fi

compose=(docker compose -p lawdesk-staging -f "$deploy_dir/compose.yaml")
if [[ -f $deploy_dir/staging.env ]]; then
    compose+=(--env-file "$deploy_dir/staging.env")
fi

log() {
    echo "$(date -u +%FT%TZ) $*" | tee -a "$state_dir/history.log" >&2
}

read_state() {
    [[ -f $state_dir/$1 ]] && cat "$state_dir/$1" || true
}

start_release() {
    LAWDESK_TAG=$1 "${compose[@]}" pull backend web &&
        LAWDESK_TAG=$1 "${compose[@]}" up -d --wait --wait-timeout 240 &&
        LAWDESK_TAG=$1 "$script_dir/smoke.sh"
}

current=$(read_state current-release)
log "deploying $release (current: ${current:-none})"

if start_release "$release"; then
    if [[ $release != "$current" ]]; then
        [[ -n $current ]] && echo "$current" > "$state_dir/previous-release"
        echo "$release" > "$state_dir/current-release"
    fi
    log "deployed $release"
    exit 0
fi

log "failed $release"
# Fall back to the running release; if that is the one that just failed, to the one before it (QAD-013).
target=$current
if [[ $target == "$release" ]]; then
    target=$(read_state previous-release)
fi
if [[ -z $target || $target == "$release" ]]; then
    echo "deploy: no previous release to roll back to; staging needs attention" >&2
    exit 1
fi
if start_release "$target"; then
    if [[ $target != "$current" ]]; then
        echo "$target" > "$state_dir/current-release"
        rm -f "$state_dir/previous-release"
    fi
    log "rolled back to $target"
    exit 1
fi
log "rollback to $target failed"
exit 4
