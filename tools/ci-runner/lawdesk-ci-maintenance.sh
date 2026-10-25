#!/usr/bin/env bash
# Daily cleanup and health report for the LawDesk CI runner (LD-041, docs/operations/CI-RUNNER.md).
# Runs as the unprivileged user lawdesk-ci from lawdesk-ci-maintenance.timer; everything goes to the journal.
# Exit status 1 means "look at this" (disk nearly full, daemon down, runner down), never "something was deleted wrongly".
set -euo pipefail

# lawdesk-ci's rootless daemon lives in its user manager: /run/user/<uid>.
uid=$(id -u)
export XDG_RUNTIME_DIR=${XDG_RUNTIME_DIR:-/run/user/$uid}
export DOCKER_HOST=${DOCKER_HOST:-unix://$XDG_RUNTIME_DIR/docker.sock}
work=${LAWDESK_CI_WORK:-/home/lawdesk-ci/actions-runner/_work}
limit=${LAWDESK_CI_DISK_LIMIT_PERCENT:-80}
status=0

say() { printf '%s\n' "$*"; }

if docker info > /dev/null 2>&1; then
    say "docker: rootless daemon answers"
    # Everything below touches only this user's own daemon, never the host's.
    docker container prune -f --filter until=24h > /dev/null
    docker image prune -a -f --filter until=72h > /dev/null
    docker builder prune -f --filter until=168h > /dev/null
    docker volume prune -f > /dev/null
    docker system df
else
    say "docker: the rootless daemon does not answer" >&2
    status=1
fi

# Leftovers of finished jobs; the checkout of the next job cleans its own workspace.
if [ -d "$work/_temp" ]; then
    find "$work/_temp" -mindepth 1 -mtime +2 -delete 2> /dev/null || true
fi

used=$(df -P "$work" 2> /dev/null | awk 'NR == 2 { gsub("%", "", $5); print $5 }')
say "disk: ${used:-unknown}% used where the work directory lives (limit ${limit}%)"
if [ -n "${used:-}" ] && [ "$used" -ge "$limit" ]; then
    say "disk usage is at or above ${limit}%" >&2
    status=1
fi

if systemctl is-active --quiet actions.runner.afawzy70-Lawdesk-v1.contabo.service; then
    say "service: the runner is active"
else
    say "service: the runner is NOT active" >&2
    status=1
fi
if systemctl --user is-active --quiet lawdesk-ci-docker.service; then
    say "service: lawdesk-ci-docker.service (user unit) is active"
else
    say "service: lawdesk-ci-docker.service (user unit) is NOT active" >&2
    status=1
fi

exit "$status"
