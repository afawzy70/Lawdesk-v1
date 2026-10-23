#!/usr/bin/env bash
# Deploys one LawDesk release to the staging stack and rolls back automatically if it does not become healthy.
#
#   deploy.sh <40-character commit sha>
#
# Environment:
#   LAWDESK_REGISTRY      required; images are <registry>/backend:<sha> and <registry>/web:<sha>
#   LAWDESK_IMAGE_SOURCE  "registry" (default: pull the images) or "local" (images were built on this host)
#   LAWDESK_STATE_DIR     release state and history (default /srv/deployments/lawdesk/state)
#   LAWDESK_RELEASES_DIR  optional; <dir>/<sha>/deploy/compose.yaml is used for that release, so a rollback runs the
#                         previous release with its own compose file
#   LAWDESK_ENV_FILE      optional compose --env-file (default deploy/staging.env when present)
#   LAWDESK_BACKUP_DIR    optional; the database is dumped there before a new release starts (newest 10 kept)
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
image_source=${LAWDESK_IMAGE_SOURCE:-registry}
if [[ $image_source != registry && $image_source != local ]]; then
    echo "deploy: LAWDESK_IMAGE_SOURCE must be 'registry' or 'local'" >&2
    exit 2
fi

release=$1
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
deploy_dir=$(dirname "$script_dir")
state_dir=${LAWDESK_STATE_DIR:-/srv/deployments/lawdesk/state}
backup_dir=${LAWDESK_BACKUP_DIR:-}
mkdir -p "$state_dir"

exec 9>"$state_dir/deploy.lock"
if ! flock -n 9; then
    echo "deploy: another deployment is in progress" >&2
    exit 3
fi

env_file=${LAWDESK_ENV_FILE:-}
if [[ -z $env_file && -f $deploy_dir/staging.env ]]; then
    env_file=$deploy_dir/staging.env
fi

# The compose command for one release: its own compose file when the host keeps one directory per release.
compose_for() {
    local file=$deploy_dir/compose.yaml
    if [[ -n ${LAWDESK_RELEASES_DIR:-} && -f $LAWDESK_RELEASES_DIR/$1/deploy/compose.yaml ]]; then
        file=$LAWDESK_RELEASES_DIR/$1/deploy/compose.yaml
    fi
    compose_dir=$(dirname "$file")
    compose=(docker compose -p lawdesk-staging -f "$file")
    if [[ -n $env_file ]]; then
        compose+=(--env-file "$env_file")
    fi
}

log() {
    echo "$(date -u +%FT%TZ) $*" | tee -a "$state_dir/history.log" >&2
}

read_state() {
    [[ -f $state_dir/$1 ]] && cat "$state_dir/$1" || true
}

# Locally built images are accepted only when their revision label names the release they are tagged for (QAD-017).
local_images_match() {
    local name revision
    for name in backend web; do
        revision=$(docker image inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' \
            "$LAWDESK_REGISTRY/$name:$1" 2> /dev/null) || { echo "deploy: image $name:$1 is not built" >&2; return 1; }
        if [[ $revision != "$1" ]]; then
            echo "deploy: image $name:$1 carries revision '$revision', not $1" >&2
            return 1
        fi
    done
}

# Starts a release with its own compose file and verifies it with its own smoke test, so a rollback target is checked
# against its own contract and not the failed release's (QAD-021).
start_release() {
    local smoke=$script_dir/smoke.sh
    if [[ -n ${LAWDESK_RELEASES_DIR:-} && -x $LAWDESK_RELEASES_DIR/$1/deploy/scripts/smoke.sh ]]; then
        smoke=$LAWDESK_RELEASES_DIR/$1/deploy/scripts/smoke.sh
    fi
    compose_for "$1"
    if [[ $image_source == registry ]]; then
        LAWDESK_TAG=$1 "${compose[@]}" pull backend web || return 1
    else
        local_images_match "$1" || return 1
    fi
    # Database first, then its least-privilege role (idempotent; repairs a volume whose role is missing), then the rest.
    # A release older than this step has no app-role.sql of its own; the running release's copy is used for it.
    local role_sql=$compose_dir/postgres/app-role.sql
    [[ -f $role_sql ]] || role_sql=$deploy_dir/postgres/app-role.sql
    LAWDESK_TAG=$1 "${compose[@]}" up -d --wait --wait-timeout 240 db &&
        LAWDESK_TAG=$1 "${compose[@]}" exec -T db psql -v ON_ERROR_STOP=1 -U postgres -d postgres -q < "$role_sql" &&
        LAWDESK_TAG=$1 "${compose[@]}" up -d --wait --wait-timeout 240 &&
        LAWDESK_TAG=$1 LAWDESK_ENV_FILE=$env_file "$smoke"
}

# Dumps the running database before a new release can migrate it. Migrations are forward-only, so this dump is the
# way back from a bad schema change; it is restored by an operator, never automatically.
backup_database() {
    local name partial
    name=$backup_dir/$(date -u +%Y%m%dT%H%M%SZ)-$1.dump
    partial=$name.partial
    mkdir -p "$backup_dir"
    compose_for "$1"
    if ! (umask 077 && LAWDESK_TAG=$1 "${compose[@]}" exec -T db pg_dump -U postgres -d lawdesk -Fc > "$partial"); then
        rm -f -- "$partial"
        return 1
    fi
    mv -- "$partial" "$name"
    find "$backup_dir" -maxdepth 1 -name '*.dump' -printf '%T@ %p\n' | sort -rn | tail -n +11 | cut -d ' ' -f 2- |
        while read -r old; do rm -f -- "$old"; done
    log "backed up database to $name"
}

current=$(read_state current-release)
log "deploying $release (current: ${current:-none})"

if [[ $image_source == local ]] && ! local_images_match "$release"; then
    log "refused $release: its images are not built on this host with revision $release"
    exit 1
fi
if [[ -n $backup_dir && -n $current ]] && ! backup_database "$current"; then
    log "refused $release: database backup failed; $current keeps running"
    exit 1
fi

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
