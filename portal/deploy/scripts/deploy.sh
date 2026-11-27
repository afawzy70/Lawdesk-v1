#!/usr/bin/env bash
# Builds the portal image for one release and starts it. Does NOT touch the shared proxy (attach.sh does that) and never
# touches LawDesk or Matbakhy containers. Needs docker. Usage: deploy.sh <dist dir> <release id>
# The release id is <40-hex source sha>-<n>; the previous release is recorded so rollback.sh can restore it.
set -euo pipefail
dist=${1:?dist directory (output of build_portal.py)}
release=${2:?release id}
[[ $release =~ ^[0-9a-f]{40}-[0-9]+$ ]] || { echo "release id must look like <sha40>-<n>" >&2; exit 2; }
[[ -f $dist/build.json && -f $dist/index.html ]] || { echo "not a built portal: $dist" >&2; exit 2; }
here=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
state=${FLOORPLAN_STATE_DIR:-/srv/deployments/lawdesk-floorplan/state}
mkdir -p "$state"
ctx=$(mktemp -d)
trap 'rm -rf "$ctx"' EXIT
cp -r "$dist" "$ctx/dist"
cp "$here/Dockerfile" "$here/nginx.conf" "$ctx/"
cp -r "$here/snippets" "$ctx/snippets"
docker build -q -t "lawdesk-floorplan-web:$release" "$ctx" > /dev/null
# --internal: the portal network has no route out and no host access; only the proxy joins it.
docker network inspect floorplan-edge > /dev/null 2>&1 || docker network create --internal floorplan-edge > /dev/null
previous=$(cat "$state/current" 2> /dev/null || true)
FLOORPLAN_RELEASE=$release docker compose -f "$here/compose.yaml" up -d --no-build --remove-orphans
for _ in $(seq 1 30); do
    [[ $(docker inspect -f '{{.State.Health.Status}}' floorplan-web 2> /dev/null) == healthy ]] && break
    sleep 2
done
if [[ $(docker inspect -f '{{.State.Health.Status}}' floorplan-web 2> /dev/null) != healthy ]]; then
    echo "deploy: not healthy; restoring ${previous:-nothing}" >&2
    [[ -n $previous ]] && FLOORPLAN_RELEASE=$previous docker compose -f "$here/compose.yaml" up -d --no-build
    exit 1
fi
[[ -n $previous && $previous != "$release" ]] && echo "$previous" > "$state/previous"
echo "$release" > "$state/current"
echo "$(date -u +%FT%TZ) deployed $release (previous: ${previous:-none})" >> "$state/history.log"
echo "deployed $release"
