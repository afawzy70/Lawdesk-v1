#!/usr/bin/env bash
# Builds the release package an operator with Docker deploys (no Docker needed to run this). Usage:
#   package.sh <documented develop sha> <out dir>
# Produces <out>/floorplan-portal-<portal commit>.tar.gz (+ .sha256) containing dist/, deploy scripts and nginx files.
set -euo pipefail
src=${1:?documented develop sha}
out=${2:?output directory}
[[ $src =~ ^[0-9a-f]{40}$ ]] || { echo "sha must be 40 hex" >&2; exit 2; }
root=$(git rev-parse --show-toplevel)
commit=$(git -C "$root" rev-parse HEAD)
[[ -z $(git -C "$root" status --porcelain -- portal) ]] || { echo "portal/ has uncommitted changes; commit first" >&2; exit 2; }
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$out" "$work/pkg"
python3 "$root/portal/tools/build_portal.py" --sha "$src" --portal-commit "$commit" --out "$work/pkg/dist" --map "${FLOORPLAN_MAP:?path to the localized map html}" > /dev/null
cp -r "$root/portal/deploy" "$work/pkg/deploy"
echo "$commit" > "$work/pkg/PORTAL_COMMIT"
tar -C "$work/pkg" -czf "$out/floorplan-portal-$commit.tar.gz" .
(cd "$out" && sha256sum "floorplan-portal-$commit.tar.gz" > "floorplan-portal-$commit.tar.gz.sha256")
echo "package: $out/floorplan-portal-$commit.tar.gz"
