#!/usr/bin/env bash
# Run once as lawdesk-poc on a disposable, dedicated VM after operator approval.
# The VM, registration, and this entire directory must be destroyed after the proof.
set -euo pipefail

version=2.338.1
archive=actions-runner-linux-x64-${version}.tar.gz
sha256=08e9ccb3604d2462c24b5e33604dc30810296ca15cc65f23cffd4317eb144c37
repo=https://github.com/afawzy70/Lawdesk-v1

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[[ $(id -un) == lawdesk-poc ]] || fail 'run as the dedicated lawdesk-poc identity'
[[ $(id -u) != 0 ]] || fail 'root execution is forbidden'
[[ ! -e /var/run/docker.sock ]] || fail 'Docker socket is present'
[[ ! -e /srv/deployments/lawdesk && ! -e /srv/ai-workspaces/secrets ]] || fail 'shared-server paths are present'
[[ -f /etc/lawdesk-ci-poc-vm ]] || fail 'operator VM isolation attestation is absent'
[[ -n ${RUNNER_REGISTRATION_TOKEN_FILE:-} && -r $RUNNER_REGISTRATION_TOKEN_FILE ]] || fail 'readable token file required'
[[ -n ${RUNNER_NAME:-} && $RUNNER_NAME =~ ^lawdesk-ci-poc-[a-z0-9-]+$ ]] || fail 'set a unique RUNNER_NAME'
[[ $(stat -c %a "$RUNNER_REGISTRATION_TOKEN_FILE") == 600 ]] || fail 'token file must be mode 0600'
for command in curl sha256sum tar; do command -v "$command" >/dev/null || fail "missing $command"; done

umask 077
workdir=${RUNNER_WORKDIR:-$HOME/actions-runner-poc}
[[ ! -e $workdir ]] || fail 'runner directory already exists'
mkdir -m 700 "$workdir"
cd "$workdir"

curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \
  --output "$archive" "https://github.com/actions/runner/releases/download/v${version}/${archive}"
printf '%s  %s\n' "$sha256" "$archive" | sha256sum --check --status || fail 'runner archive checksum mismatch'
tar -xzf "$archive"
rm -- "$archive"

# GitHub's config.sh accepts the short-lived registration token as an argument.
# The disposable VM must have no other local users who could read its process list.
./config.sh --unattended --url "$repo" \
  --token "$(cat "$RUNNER_REGISTRATION_TOKEN_FILE")" \
  --name "$RUNNER_NAME" --labels lawdesk-ci-poc-49 --ephemeral --work _work
rm -- "$RUNNER_REGISTRATION_TOKEN_FILE"

printf 'Runner registered. Starting one ephemeral job. Destroy VM and remove any stale registration afterward.\n'
exec ./run.sh
