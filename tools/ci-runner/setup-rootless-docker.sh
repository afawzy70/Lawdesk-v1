#!/usr/bin/env bash
# One-time, idempotent host setup for the LawDesk CI runner (LD-041, docs/operations/CI-RUNNER.md).
#
#   tools/ci-runner/setup-rootless-docker.sh --check              report only; needs no privileges
#   sudo tools/ci-runner/setup-rootless-docker.sh --dry-run       print every action, change nothing
#   sudo tools/ci-runner/setup-rootless-docker.sh --apply         do it
#   sudo tools/ci-runner/setup-rootless-docker.sh --apply --restart-runner   ... and restart the idle runner
#
# What it does, and nothing else:
#   1. gives lawdesk-ci a private range of sub-UIDs/sub-GIDs and a persistent user manager (lingering), both needed
#      for a rootless daemon;
#   2. installs a rootless Docker daemon as a user unit of lawdesk-ci (own socket /run/user/<uid>/docker.sock, own
#      images, networks and volumes, invisible to the host's Docker and so to Matbakhy);
#   3. bounds CPU, memory and tasks of that user (root-owned ceiling on user-<uid>.slice) and of the runner service;
#   4. points the runner at that daemon (DOCKER_HOST in the runner's .env);
#   5. installs a daily cleanup and health timer.
# It never adds lawdesk-ci to the docker group or to sudoers, never touches the host's Docker daemon, Caddy,
# Matbakhy or any LawDesk deployment file, and never restarts the runner unless asked and the runner is idle.
set -euo pipefail

CI_USER=lawdesk-ci
RUNNER_UNIT=actions.runner.afawzy70-Lawdesk-v1.contabo.service
RUNNER_ENV=/home/$CI_USER/actions-runner/.env
DOCKER_UNIT=lawdesk-ci-docker.service
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ETC=${LAWDESK_CI_ETC:-/etc}
SYSTEM_UNITS=$ETC/systemd/system
USER_UNITS=$ETC/systemd/user
SUB_COUNT=65536

# The numeric id of lawdesk-ci; LAWDESK_CI_UID lets the tests plan for a user this machine does not have.
uid=${LAWDESK_CI_UID:-$(id -u "$CI_USER" 2> /dev/null || echo '<uid>')}
SOCKET=/run/user/$uid/docker.sock

mode=check
restart_runner=no
for arg in "$@"; do
    case $arg in
        --check) mode=check ;;
        --dry-run) mode=dry-run ;;
        --apply) mode=apply ;;
        --restart-runner) restart_runner=yes ;;
        *) echo "unknown argument: $arg (see the header of this script)" >&2; exit 2 ;;
    esac
done

problems=0
ok() { printf 'ok    %s\n' "$*"; }
bad() { printf 'FAIL  %s\n' "$*"; problems=$((problems + 1)); }
act() {
    # One action: described and, only with --apply, executed.
    local description=$1
    shift
    case $mode in
        apply) printf 'do    %s\n' "$description"; "$@" ;;
        *) printf 'would %s\n' "$description" ;;
    esac
}

# The runner drop-in names the user manager by number.
render_runner_limits() { sed "s/@UID@/$uid/g" "$HERE/runner-limits.conf"; }

first_free_subid_start() {
    # The first multiple of 65536 above every range in /etc/subuid and /etc/subgid.
    local top=0 file
    for file in "$ETC/subuid" "$ETC/subgid"; do
        [ -r "$file" ] || continue
        while IFS=: read -r _ start count; do
            [ -n "${start:-}" ] || continue
            [ $((start + count)) -gt "$top" ] && top=$((start + count))
        done < "$file"
    done
    echo $(((top + SUB_COUNT - 1) / SUB_COUNT * SUB_COUNT))
}

has_subids() { [ -r "$ETC/$1" ] && grep -q "^$CI_USER:" "$ETC/$1"; }

check() {
    if ! id -u "$CI_USER" > /dev/null 2>&1; then
        bad "user $CI_USER does not exist"
        return
    fi
    ok "user $CI_USER exists (uid $uid)"
    local groups
    groups=" $(id -nG "$CI_USER") "
    for forbidden in docker sudo adm wheel root; do
        case $groups in
            *" $forbidden "*) bad "$CI_USER is in the group '$forbidden'; remove it" ;;
        esac
    done
    ok "$CI_USER is in no privileged group"
    for file in subuid subgid; do
        if has_subids "$file"; then ok "$ETC/$file has a range for $CI_USER"; else bad "$ETC/$file has no range for $CI_USER"; fi
    done
    for tool in newuidmap newgidmap rootlesskit dockerd-rootless.sh docker; do
        if command -v "$tool" > /dev/null 2>&1; then ok "$tool is installed"; else bad "$tool is not installed"; fi
    done
    if [ -e "$ETC/apparmor.d/rootlesskit" ]; then ok "AppArmor profile for rootlesskit present"; else bad "no AppArmor profile for rootlesskit"; fi
    if loginctl show-user "$CI_USER" -p Linger 2> /dev/null | grep -qx 'Linger=yes'; then ok "$CI_USER has a persistent user manager"; else bad "lingering is not enabled for $CI_USER"; fi
    if cmp -s "$HERE/$DOCKER_UNIT" "$USER_UNITS/$DOCKER_UNIT"; then ok "$DOCKER_UNIT installed (root-owned) and identical to the repository"; else bad "$DOCKER_UNIT is not installed as in the repository"; fi
    for unit in lawdesk-ci-maintenance.service lawdesk-ci-maintenance.timer; do
        if cmp -s "$HERE/$unit" "$SYSTEM_UNITS/$unit"; then ok "$unit installed and identical to the repository"; else bad "$unit is not installed as in the repository"; fi
    done
    if cmp -s "$HERE/user-slice-limits.conf" "$SYSTEM_UNITS/user-$uid.slice.d/lawdesk-limits.conf"; then ok "ceiling on user-$uid.slice installed"; else bad "ceiling on user-$uid.slice missing"; fi
    if render_runner_limits | cmp -s - "$SYSTEM_UNITS/$RUNNER_UNIT.d/lawdesk-limits.conf"; then ok "resource limits drop-in installed for the runner"; else bad "resource limits drop-in missing for the runner"; fi
    if [ -S "$SOCKET" ]; then ok "socket $SOCKET exists"; else bad "socket $SOCKET is missing"; fi
    if [ -r "$RUNNER_ENV" ]; then
        if grep -qx "DOCKER_HOST=unix://$SOCKET" "$RUNNER_ENV"; then ok "runner .env points at the rootless daemon"; else bad "runner .env lacks DOCKER_HOST=unix://$SOCKET"; fi
    else
        echo "skip  $RUNNER_ENV is not readable by this user (run the check as root to include it)"
    fi
    if [ "$(id -u)" = 0 ]; then
        for path in /srv/deployments /srv/ai-workspaces/secrets /srv/matbakhy-*; do
            [ -e "$path" ] || continue
            if runuser -u "$CI_USER" -- test -r "$path"; then bad "$CI_USER can read $path"; else ok "$CI_USER cannot read $path"; fi
        done
        if runuser -u "$CI_USER" -- test -r /var/run/docker.sock && runuser -u "$CI_USER" -- test -w /var/run/docker.sock; then
            bad "$CI_USER can use the host Docker socket"
        else
            ok "$CI_USER cannot use the host Docker socket"
        fi
        if runuser -u "$CI_USER" -- env "DOCKER_HOST=unix://$SOCKET" docker info --format '{{json .SecurityOptions}}' 2> /dev/null | grep -q 'name=rootless'; then
            ok "the runner's daemon is rootless"
        else
            bad "the runner's daemon did not answer as rootless"
        fi
        if runuser -u "$CI_USER" -- env "DOCKER_HOST=unix://$SOCKET" docker info --format '{{.CgroupDriver}}' 2> /dev/null | grep -qx systemd; then
            ok "the daemon uses the systemd cgroup driver (container limits are enforced)"
        else
            bad "the daemon does not use the systemd cgroup driver (compose memory limits would be ignored)"
        fi
    fi
    [ "$problems" = 0 ]
}

apply() {
    if [ "$mode" = apply ]; then
        if [ "$(id -u)" != 0 ]; then
            echo "--apply must run as root (use sudo)" >&2
            exit 2
        fi
        id -u "$CI_USER" > /dev/null 2>&1 || { echo "user $CI_USER does not exist; the runner must be installed first" >&2; exit 2; }
    fi
    local start
    start=$(first_free_subid_start)
    has_subids subuid || act "give $CI_USER sub-UIDs $start-$((start + SUB_COUNT - 1))" usermod --add-subuids "$start-$((start + SUB_COUNT - 1))" "$CI_USER"
    has_subids subgid || act "give $CI_USER sub-GIDs $start-$((start + SUB_COUNT - 1))" usermod --add-subgids "$start-$((start + SUB_COUNT - 1))" "$CI_USER"
    act "install the user unit $DOCKER_UNIT (root-owned)" install -D -m 0644 -o root -g root "$HERE/$DOCKER_UNIT" "$USER_UNITS/$DOCKER_UNIT"
    act "cap the user slice user-$uid.slice" install -D -m 0644 -o root -g root "$HERE/user-slice-limits.conf" "$SYSTEM_UNITS/user-$uid.slice.d/lawdesk-limits.conf"
    act "install the runner's resource limits" bash -c "install -d -m 0755 '$SYSTEM_UNITS/$RUNNER_UNIT.d' && sed 's/@UID@/$uid/g' '$HERE/runner-limits.conf' > '$SYSTEM_UNITS/$RUNNER_UNIT.d/lawdesk-limits.conf' && chmod 0644 '$SYSTEM_UNITS/$RUNNER_UNIT.d/lawdesk-limits.conf'"
    for unit in lawdesk-ci-maintenance.service lawdesk-ci-maintenance.timer; do
        act "install $unit" install -m 0644 -o root -g root "$HERE/$unit" "$SYSTEM_UNITS/$unit"
    done
    act "install the maintenance script" install -D -m 0755 -o root -g root "$HERE/lawdesk-ci-maintenance.sh" /usr/local/libexec/lawdesk-ci-maintenance.sh
    act "reload systemd" systemctl daemon-reload
    act "enable lingering for $CI_USER (a user manager that survives logout and reboots)" loginctl enable-linger "$CI_USER"
    act "wait for the user manager of $CI_USER" bash -c "for _ in \$(seq 1 60); do [ -S /run/user/$uid/bus ] && exit 0; sleep 1; done; exit 1"
    act "start $CI_USER's rootless Docker daemon" runuser -u "$CI_USER" -- env "XDG_RUNTIME_DIR=/run/user/$uid" systemctl --user enable --now "$DOCKER_UNIT"
    act "wait for the daemon socket" bash -c "for _ in \$(seq 1 60); do [ -S $SOCKET ] && exit 0; sleep 1; done; exit 1"
    act "point the runner at $SOCKET" runuser -u "$CI_USER" -- bash -c "touch '$RUNNER_ENV' && { grep -qx 'DOCKER_HOST=unix://$SOCKET' '$RUNNER_ENV' || echo 'DOCKER_HOST=unix://$SOCKET' >> '$RUNNER_ENV'; }"
    act "enable the daily maintenance timer" systemctl enable --now lawdesk-ci-maintenance.timer
    if [ "$restart_runner" = yes ]; then
        if [ "$mode" = apply ] && pgrep -u "$CI_USER" -f Runner.Worker > /dev/null; then
            echo "a job is running on the runner; not restarting. Restart it when idle: systemctl restart $RUNNER_UNIT" >&2
        else
            act "restart the idle runner so it reads .env and the new limits" systemctl restart "$RUNNER_UNIT"
        fi
    else
        echo "next  restart the runner when it is idle so it reads .env: systemctl restart $RUNNER_UNIT"
    fi
}

if [ "${LAWDESK_CI_PRINT_DROPIN:-}" = 1 ]; then
    # Test hook: show the runner drop-in as it would be installed.
    render_runner_limits
    exit 0
fi

case $mode in
    check) check || { echo "$problems problem(s)"; exit 1; } ;;
    dry-run | apply)
        apply
        if [ "$mode" = apply ]; then
            echo "--- verifying"
            check || { echo "$problems problem(s) after applying"; exit 1; }
        fi
        ;;
esac
