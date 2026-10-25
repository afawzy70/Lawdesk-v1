#!/usr/bin/env bash
# Read-only host gate. Run as: sudo bash contabo-rootless-preflight.sh
# This does not create a user, daemon, firewall rule, container or runner.
set -euo pipefail

fail() { printf 'NO-GO: %s\n' "$*" >&2; exit 1; }
[[ $(id -u) == 0 ]] || fail 'run via devops sudo'
[[ ${SUDO_USER:-} == devops ]] || fail 'SUDO_USER must be devops'

for cmd in docker rootlesskit dockerd-rootless.sh newuidmap newgidmap unshare nft runuser; do
  command -v "$cmd" >/dev/null || fail "missing $cmd"
done
[[ $(cat /proc/sys/kernel/unprivileged_userns_clone) == 1 ]] ||
  fail 'unprivileged user namespaces disabled'
[[ -f /sys/fs/cgroup/cgroup.controllers ]] || fail 'cgroup v2 unavailable'
[[ -e /sys/module/apparmor/parameters/enabled ]] ||
  fail 'AppArmor unavailable'
[[ $(cat /sys/module/apparmor/parameters/enabled) == Y ]] ||
  fail 'AppArmor disabled'

for map in /etc/subuid /etc/subgid; do
  count=$(awk -F: '$1 == "devops" {print $3; exit}' "$map")
  [[ ${count:-0} -ge 65536 ]] || fail "devops lacks 65536 entries in $map"
done

# The kernel toggle alone is not proof: AppArmor or user-session policy can
# still reject mapping. Test with the actual infrastructure operator identity.
runuser -u devops -- unshare --user --map-root-user --mount --pid --fork \
  sh -ec 'test "$(id -u)" = 0; grep -Eq "^[[:space:]]*0[[:space:]]+$(id -u devops)[[:space:]]" /proc/self/uid_map' ||
  fail 'devops cannot create a mapped user/mount/PID namespace'

# Verify the rootful daemon is available to the operator, but do not change it.
docker info --format '{{.ServerVersion}}' >/dev/null ||
  fail 'devops sudo cannot inspect Docker'
printf 'GO: devops user namespaces, AppArmor, cgroup v2, nftables and rootless tooling are available.\n'
printf 'NEXT: inspect a dedicated rootless daemon and scoped network policy before runner registration.\n'
