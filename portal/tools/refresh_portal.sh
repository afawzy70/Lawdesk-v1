#!/usr/bin/env bash
# Reproducible update: regenerates the Floorplan map and the Arabic portal for ONE develop commit, validates everything,
# and leaves a complete release directory. It never deploys and never publishes. Usage:
#   refresh_portal.sh <40-hex develop sha> [out dir]        (run from the root of a checkout of that sha)
# Steps: verify the sha is on origin/develop and is the checkout -> verify the Floorplan plugin against its checksums ->
# floorplan refresh (incremental) + build + static export -> content validation (every evidence reference at this sha) ->
# build the site -> automated gates -> release dir. Approved Arabic content (portal/content, portal/floorplan-src) is
# only read, never rewritten; new or changed code shows up as validator/describe warnings for a human to resolve.
set -euo pipefail
sha=${1:?full develop commit sha}
out=${2:-portal/dist/$sha}
[[ $sha =~ ^[0-9a-f]{40}$ ]] || { echo "sha must be 40 hex" >&2; exit 2; }
[[ $(git rev-parse HEAD) == "$sha" ]] || { echo "checkout is not $sha" >&2; exit 2; }
git fetch -q origin develop
git merge-base --is-ancestor "$sha" origin/develop || { echo "$sha is not on develop" >&2; exit 2; }
plugin=${FLOORPLAN_PLUGIN_DIR:-$HOME/.claude/plugins/marketplaces/floorplan}
(cd "$plugin" && sha256sum --check --quiet CHECKSUMS.txt) || { echo "Floorplan plugin does not match its CHECKSUMS.txt" >&2; exit 2; }
fp="$plugin/skills/floorplan/scripts"
mkdir -p floorplan/descriptions
cp portal/floorplan-src/architecture.json floorplan/architecture.json
cp portal/floorplan-src/descriptions/*.json floorplan/descriptions/
if [[ -f floorplan/graph.json ]]; then node "$fp/refresh.mjs" . ; else node "$fp/build.mjs" . ; fi
node "$fp/build.mjs" . | tee /tmp/floorplan-build.txt
grep -E '^DESCRIPTIONS' /tmp/floorplan-build.txt
if ! grep -qE 'DESCRIPTIONS +files ([0-9]+)/\1 · routes ([0-9]+)/\2' /tmp/floorplan-build.txt; then
    echo "NEW or CHANGED code lacks approved Arabic descriptions: run describe.mjs, write Arabic entries, review, then rerun" >&2
    exit 3
fi
mkdir -p "$(dirname "$out")"
node floorplan/serve.mjs --export "$out.map.en.html" --include flows,notes
python3 portal/tools/localize_map.py "$out.map.en.html" "$out.map.html"
python3 portal/tools/validate_content.py
python3 portal/tools/build_portal.py --sha "$sha" --out "$out" --map "$out.map.html"
python3 -I -m unittest discover -s portal/tests -p 'test_*.py'
echo "release directory: $out (not deployed). Next: portal/deploy/scripts/deploy.sh $out ${sha}-<n>"
