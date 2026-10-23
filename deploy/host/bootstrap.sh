#!/usr/bin/env bash
# One-time, idempotent host preparation for LawDesk staging. Run as `devops` (owner of /srv/deployments/lawdesk and
# already the deployment operator); needs no root and touches nothing outside LawDesk's own paths, devops's
# ~/.ssh and the lawdesk-edge Docker network.
#
#   bootstrap.sh <ci-public-key.pub>
#
# The argument is the public half of the key GitHub Actions uses; it is installed in ~/.ssh/authorized_keys bound to
# the gate (restrict,command=...), so it can never open a shell, forward ports or run anything else.
# Existing files (database passwords, keys, authorized keys) are never overwritten.
set -euo pipefail
umask 027

home=${LAWDESK_HOME:-/srv/deployments/lawdesk}
gate=/usr/local/sbin/lawdesk-deploy-gate
repo_url=git@github.com:afawzy70/Lawdesk-v1.git
github_host_key="github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl"

ci_key=${1:-}
if [[ $# -ne 1 || ! -f $ci_key ]]; then
    echo "usage: bootstrap.sh <ci-public-key.pub>" >&2
    exit 2
fi
if [[ $(wc -l < "$ci_key") -ne 1 ]] || ! grep -Eq '^ssh-ed25519 [A-Za-z0-9+/]+=* ?[A-Za-z0-9@._-]*$' "$ci_key"; then
    echo "bootstrap: $ci_key must hold exactly one ssh-ed25519 public key and no options" >&2
    exit 2
fi

# Every line that already authorises the CI key, matched on the key field itself. Any entry other than exactly the
# gate-bound one (unrestricted, other options, a duplicate) would let the key do more than run the gate; it is never
# edited silently, so bootstrap refuses before changing anything (QAD-018).
entry="restrict,command=\"$gate\" $(cat "$ci_key")"
authorized=$HOME/.ssh/authorized_keys
key_body=$(cut -d ' ' -f 2 "$ci_key")
existing=$([[ -f $authorized ]] && awk -v key="$key_body" '
    { for (i = 1; i <= NF; i++) if ($i == key) { print; next } }' "$authorized" || true)
if [[ -n $existing && $existing != "$entry" ]]; then
    echo "bootstrap: $authorized already authorises this CI key with other options:" >&2
    printf '  %s\n' "$existing" >&2
    echo "remove those lines (or use a new CI key) and run bootstrap again" >&2
    exit 2
fi

echo "== directories ($home)"
mkdir -p "$home"/{state,releases,logs,proxy-backups}
install -d -m 700 "$home/secrets" "$home/backups"
[[ -f $home/staging.env ]] || echo "# LawDesk staging compose overrides (no secrets here; the gate sets the rest)." \
    > "$home/staging.env"

echo "== database passwords (generated once, never printed)"
for name in db_admin_password db_app_password; do
    file=$home/secrets/$name.txt
    if [[ ! -f $file ]]; then
        (umask 022 && openssl rand -hex 32 > "$file")
    fi
    # Compose bind-mounts the file into containers that run as other users; the 0700 directory keeps host users out.
    chmod 644 "$file"
done

echo "== read-only fetch of develop from GitHub"
install -d -m 700 "$HOME/.ssh"
[[ -f $HOME/.ssh/lawdesk_github_ro ]] ||
    ssh-keygen -q -t ed25519 -N "" -C "lawdesk-staging-fetch" -f "$HOME/.ssh/lawdesk_github_ro"
echo "$github_host_key" > "$home/github_known_hosts"
if [[ ! -d $home/repo.git ]]; then
    git init -q --bare "$home/repo.git"
    git -C "$home/repo.git" remote add origin "$repo_url"
fi
git -C "$home/repo.git" config core.sshCommand \
    "ssh -i $HOME/.ssh/lawdesk_github_ro -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$home/github_known_hosts"

echo "== CI key bound to the gate"
touch "$authorized"
chmod 600 "$authorized"
[[ -n $existing ]] || echo "$entry" >> "$authorized"

echo "== Docker network lawdesk-edge (internal: no route out, only the proxy and lawdesk web join it)"
docker network inspect lawdesk-edge > /dev/null 2>&1 || docker network create --internal lawdesk-edge

host_key=$(cut -d ' ' -f 1,2 "${LAWDESK_SSH_HOST_KEY:-/etc/ssh/ssh_host_ed25519_key.pub}" 2> /dev/null || true)
cat << EOF

Next steps for the owner:
  1. Add this public key to GitHub as a READ-ONLY deploy key (Settings > Deploy keys), title lawdesk-staging-fetch:
     $(cat "$HOME/.ssh/lawdesk_github_ro.pub")
  2. Repository secret LAWDESK_STAGING_KNOWN_HOSTS:
     167.86.90.174 ${host_key:-<ssh-ed25519 host key from /etc/ssh/ssh_host_ed25519_key.pub>}
  3. Install the gate root-owned (one root step): see docs/operations/STAGING-RUNBOOK.md.
EOF
