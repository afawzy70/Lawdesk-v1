#!/usr/bin/env bash
# One-time, single-job LawDesk proof runner. Reviewed for PR #49 only.
# Run as: sudo bash /root/install-systemd-poc.sh
# Registration token is entered only at the hidden local TTY prompt.
set -euo pipefail
umask 077

fail() { printf 'NO-GO: %s\n' "$*" >&2; exit 1; }
[[ $(id -u) == 0 && ${SUDO_USER:-} == devops ]] ||
  fail 'run via devops sudo'
! id lawdesk-runner >/dev/null 2>&1 || fail 'lawdesk-runner already exists'
[[ ! -e /var/lib/lawdesk-runner ]] || fail 'runner directory already exists'
[[ ! -e /etc/systemd/system/lawdesk-runner-poc.service ]] ||
  fail 'POC unit already exists'

for cmd in apt-get curl sha256sum tar runuser useradd passwd systemctl systemd-analyze; do
  command -v "$cmd" >/dev/null || fail "missing $cmd"
done

version=2.338.1
archive=actions-runner-linux-x64-${version}.tar.gz
archive_sha=08e9ccb3604d2462c24b5e33604dc30810296ca15cc65f23cffd4317eb144c37
work=/var/lib/lawdesk-runner
runner=$work/runner
service=/etc/systemd/system/lawdesk-runner-poc.service

# Package installation is limited to the official runner's native dependencies.
# Java/Node/Docker tools are provisioned only after this scheduling proof passes.
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
  ca-certificates curl libicu74 libkrb5-3 liblttng-ust1t64 libssl3t64 zlib1g

useradd --system --create-home --home-dir "$work" --user-group \
  --shell /usr/sbin/nologin lawdesk-runner
passwd -l lawdesk-runner >/dev/null
[[ $(id -nG lawdesk-runner) == lawdesk-runner ]] ||
  fail 'runner belongs to additional groups'
install -d -o lawdesk-runner -g lawdesk-runner -m 0700 "$runner"
temp=$(mktemp -d)
trap 'rm -rf -- "$temp"' EXIT
curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \
  --output "$temp/$archive" \
  "https://github.com/actions/runner/releases/download/v${version}/${archive}"
printf '%s  %s\n' "$archive_sha" "$temp/$archive" | sha256sum --check
tar -xzf "$temp/$archive" -C "$runner"
chown -R lawdesk-runner:lawdesk-runner "$work"
chmod 0700 "$work" "$runner"

printf 'Get the short-lived registration token from Lawdesk-v1 → Settings → Actions → Runners.\n'
IFS= read -r -s -p 'Registration token: ' token </dev/tty
printf '\n'
[[ -n $token ]] || fail 'empty registration token'
(
  cd "$runner"
  runuser -u lawdesk-runner -- env HOME="$work" \
    ./config.sh --unattended \
    --url https://github.com/afawzy70/Lawdesk-v1 \
    --token "$token" \
    --name lawdesk-ci-poc-49-contabo \
    --labels lawdesk-ci-poc-49 \
    --ephemeral --work _work
)
unset token

cat > "$service" <<'UNIT'
[Unit]
Description=LawDesk GitHub Actions one-job proof runner
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=lawdesk-runner
Group=lawdesk-runner
WorkingDirectory=/var/lib/lawdesk-runner/runner
Environment=HOME=/var/lib/lawdesk-runner
ExecStart=/var/lib/lawdesk-runner/runner/run.sh
Restart=no
TimeoutStopSec=30
KillMode=control-group
NoNewPrivileges=yes
CapabilityBoundingSet=
AmbientCapabilities=
ProtectSystem=strict
ReadWritePaths=/var/lib/lawdesk-runner
ProtectHome=yes
InaccessiblePaths=/srv/deployments /srv/ai-workspaces /var/run/docker.sock
PrivateTmp=yes
PrivateDevices=yes
ProtectKernelTunables=yes
ProtectKernelModules=yes
ProtectControlGroups=yes
ProtectKernelLogs=yes
RestrictSUIDSGID=yes
LockPersonality=yes
RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6
SystemCallArchitectures=native
SystemCallFilter=~@mount @reboot @swap
MemoryMax=1G
MemorySwapMax=0
CPUQuota=50%
TasksMax=128
# Only the local DNS stub remains reachable on loopback. All Contabo app,
# private-network and metadata destinations are denied for CI jobs.
IPAddressAllow=127.0.0.53
IPAddressDeny=127.0.0.0/8 ::1
IPAddressDeny=167.86.90.174
IPAddressDeny=10.0.0.0/8 172.16.0.0/12 192.168.0.0/16
IPAddressDeny=169.254.0.0/16 100.64.0.0/10

[Install]
WantedBy=multi-user.target
UNIT
chmod 0644 "$service"
systemd-analyze verify "$service"
systemctl daemon-reload
systemctl start lawdesk-runner-poc.service
systemctl --no-pager --full status lawdesk-runner-poc.service || true
printf 'POC runner service started. Verify the real GitHub job conclusion.\n'
