#!/usr/bin/env bash
# Restores the previous portal release (an image that is still on the host). Only the portal container changes.
# Full removal of the site: detach.sh (proxy block), then `docker compose -p lawdesk-floorplan down`, then
# `docker network rm floorplan-edge`. Usage: rollback.sh
set -euo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
state=${FLOORPLAN_STATE_DIR:-/srv/deployments/lawdesk-floorplan/state}
previous=$(cat "$state/previous" 2> /dev/null || true)
[[ -n $previous ]] || { echo "rollback: no previous release recorded" >&2; exit 2; }
docker image inspect "lawdesk-floorplan-web:$previous" > /dev/null 2>&1 || { echo "rollback: image for $previous is gone" >&2; exit 2; }
current=$(cat "$state/current")
FLOORPLAN_RELEASE=$previous docker compose -f "$here/compose.yaml" up -d --no-build
echo "$previous" > "$state/current"
echo "$current" > "$state/previous"
echo "$(date -u +%FT%TZ) rolled back $current -> $previous" >> "$state/history.log"
echo "rolled back to $previous"
