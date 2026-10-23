#!/usr/bin/env bash
# Removes the LawDesk site from the shared Caddy proxy and leaves lawdesk-edge, without restarting the proxy.
# Only the block between the LawDesk markers is removed from the *live* file, so Matbakhy changes made since
# attach.sh are kept. Exit codes: 0 detached (or nothing to do), 1 failed, 2 refused before any change.
set -euo pipefail
# shellcheck source=deploy/proxy/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

find_proxy
backup=$backup_root/$(date -u +%Y%m%dT%H%M%SZ)-detach
mkdir -p "$backup"
docker cp "$proxy:$LIVE" "$backup/Caddyfile"
echo "backup: $backup"

if grep -q "^$BEGIN_MARK" "$backup/Caddyfile"; then
    strip_block "$backup/Caddyfile" > "$backup/Caddyfile.candidate"
    stage "$backup/Caddyfile.candidate" || die "validation failed; nothing changed"
    activate "$backup/Caddyfile.candidate" || die "reload failed; the proxy keeps its previous configuration"
    echo "removed the LawDesk site"
else
    echo "no LawDesk site in the live Caddyfile"
fi
if on_edge; then
    docker network disconnect "$EDGE_NETWORK" "$proxy"
    echo "left $EDGE_NETWORK"
fi
