#!/usr/bin/env bash
# Move the manually registered LawDesk runner to an unprivileged identity.
# Run only after stopping its foreground run.sh process.
set -euo pipefail
umask 077

die() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
[[ $(id -u) == 0 && ${SUDO_USER:-} == devops ]] || die 'run with devops sudo'
[[ $# == 1 ]] || die 'usage: sudo bash move-existing-runner.sh /home/devops/path-to-runner'
src=$(realpath -- "$1")
[[ $src == /home/devops/* && -d $src && ! -L $src ]] || die 'runner must be a real directory beneath /home/devops'
[[ -f $src/.runner && -f $src/.credentials && -x $src/svc.sh && -x $src/runsvc.sh ]] ||
  die 'the directory is not a configured official GitHub runner'
[[ $(stat -c %U "$src") == devops ]] || die 'source runner is not owned by devops'
! pgrep -u devops -f 'Runner.Listener' >/dev/null || die 'stop the foreground runner first (Ctrl-C)'
! id lawdesk-ci >/dev/null 2>&1 || die 'lawdesk-ci already exists; inspect it before reusing'
[[ ! -e /var/lib/lawdesk-ci ]] || die 'target path already exists'

useradd --system --create-home --home-dir /var/lib/lawdesk-ci --user-group \
  --shell /usr/sbin/nologin lawdesk-ci
passwd -l lawdesk-ci >/dev/null
[[ $(id -nG lawdesk-ci) == lawdesk-ci ]] || die 'CI user has additional groups'
mv -- "$src" /var/lib/lawdesk-ci/runner
rm -rf -- /var/lib/lawdesk-ci/runner/_work /var/lib/lawdesk-ci/runner/_diag
chown -R lawdesk-ci:lawdesk-ci /var/lib/lawdesk-ci
chmod 0700 /var/lib/lawdesk-ci /var/lib/lawdesk-ci/runner

cd /var/lib/lawdesk-ci/runner
./svc.sh install lawdesk-ci
[[ -f .service ]] || die 'official svc.sh did not record a unit'
unit=$(cat .service)
[[ $unit == actions.runner.*.service && $unit != */* ]] || die 'unexpected service name'
override=/etc/systemd/system/$unit.d
install -d -o root -g root -m 0755 "$override"
cat > "$override/10-lawdesk-ci.conf" <<'UNIT'
[Service]
User=lawdesk-ci
Group=lawdesk-ci
Environment=HOME=/var/lib/lawdesk-ci
NoNewPrivileges=yes
CapabilityBoundingSet=
AmbientCapabilities=
ProtectSystem=strict
ReadWritePaths=/var/lib/lawdesk-ci
ProtectHome=yes
InaccessiblePaths=/srv/deployments /srv/ai-workspaces /home/devops /home/aiagent /var/run/docker.sock
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
IPAddressDeny=127.0.0.0/8 ::1 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 100.64.0.0/10 169.254.0.0/16 167.86.90.174
MemoryMax=3G
MemorySwapMax=0
CPUQuota=100%
TasksMax=384
KillMode=control-group
UNIT
chmod 0644 "$override/10-lawdesk-ci.conf"
systemd-analyze verify /etc/systemd/system/"$unit"
systemctl daemon-reload
systemctl enable --now "$unit"
systemctl is-active --quiet "$unit" || die 'service did not start; inspect journalctl'
printf 'unit=%s user=%s active=%s\n' "$unit" \
  "$(systemctl show "$unit" -p User --value)" "$(systemctl is-active "$unit")"
printf 'Next: verify GitHub shows runner contabo online and run a no-secrets identity probe.\n'
