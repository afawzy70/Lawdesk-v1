#!/usr/bin/env bash
# Redeploys the release that ran before the current one. Database migrations are forward-only; an older release
# still starts against a newer schema because Flyway ignores future migrations (tested in LD-004).
set -euo pipefail

state_dir=${LAWDESK_STATE_DIR:-/srv/lawdesk-staging/state}
previous=$( [[ -f $state_dir/previous-release ]] && cat "$state_dir/previous-release" || true )
if [[ -z $previous ]]; then
    echo "rollback: no previous release recorded in $state_dir" >&2
    exit 2
fi
exec "$(dirname "${BASH_SOURCE[0]}")/deploy.sh" "$previous"
