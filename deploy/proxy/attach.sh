#!/usr/bin/env bash
# Publishes LawDesk staging through the shared (Matbakhy) Caddy proxy without restarting it:
#   1. back up the live Caddyfile and record how every Matbakhy host answers;
#   2. append only the LawDesk site between its markers (an earlier LawDesk block is replaced, nothing else changes);
#   3. validate the candidate inside the proxy, join the proxy to lawdesk-edge, reload gracefully, persist;
#   4. ask every Matbakhy host again; any difference restores the backup and leaves lawdesk-edge.
# Run by the operator (docker access) after the staging deployment has passed its checks. Undo: detach.sh.
#
# Environment: LAWDESK_HOSTNAME (default lawdesk.<the proxy's PREVIEW_DOMAIN>), LAWDESK_ALLOWED_IPS (optional
# space-separated CIDRs; when set, everyone else gets 403), LAWDESK_PROXY_BACKUP_DIR.
# Exit codes: 0 attached, 1 failed and nothing is left changed, 2 refused before any change.
set -euo pipefail
# shellcheck source=deploy/proxy/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

allowed=${LAWDESK_ALLOWED_IPS:-}
[[ -z $allowed || $allowed =~ ^[0-9a-fA-F.:/]+( [0-9a-fA-F.:/]+)*$ ]] || die "LAWDESK_ALLOWED_IPS must be CIDRs" 2
find_proxy
hostname=${LAWDESK_HOSTNAME:-lawdesk.$(docker exec "$proxy" printenv PREVIEW_DOMAIN)}
[[ $hostname =~ ^lawdesk(-[a-z0-9]+)?(\.[a-z0-9-]+)+$ ]] || die "hostname must be lawdesk.<domain>: $hostname" 2
docker network inspect "$EDGE_NETWORK" > /dev/null 2>&1 || die "network $EDGE_NETWORK does not exist; deploy LawDesk first" 2

backup=$backup_root/$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "$backup"
docker cp "$proxy:$LIVE" "$backup/Caddyfile"
host_statuses "$backup/Caddyfile" > "$backup/matbakhy-before.txt"
echo "backup: $backup"
sed 's/^/before: /' "$backup/matbakhy-before.txt"

{
    strip_block "$backup/Caddyfile"
    echo "$BEGIN_MARK (managed by LawDesk deploy/proxy/attach.sh; remove with detach.sh)"
    echo "$hostname {"
    echo "	route {"
    echo "		header {"
    echo "			Strict-Transport-Security \"max-age=31536000\""
    echo "			X-Robots-Tag \"noindex, nofollow\""
    echo "			-Server"
    echo "		}"
    if [[ -n $allowed ]]; then
        echo "		@lawdesk_outsider not remote_ip $allowed"
        echo "		respond @lawdesk_outsider \"Not available\" 403"
    fi
    echo "		# Only health probes of the actuator are public; the web container enforces the same."
    echo "		@lawdesk_internal {"
    echo "			path /actuator /actuator/*"
    echo "			not path /actuator/health /actuator/health/liveness /actuator/health/readiness"
    echo "		}"
    echo "		respond @lawdesk_internal 404"
    echo "		reverse_proxy lawdesk-web:8080"
    echo "	}"
    echo "}"
    echo "$END_MARK"
} > "$backup/Caddyfile.candidate"

# Every Matbakhy line must survive unchanged.
diff <(strip_block "$backup/Caddyfile") <(strip_block "$backup/Caddyfile.candidate") > /dev/null ||
    die "candidate would change Matbakhy configuration" 2

stage "$backup/Caddyfile.candidate" || die "validation failed; nothing changed"

connected=no
if ! on_edge; then
    docker network connect "$EDGE_NETWORK" "$proxy"
    connected=yes
fi

undo_network() {
    if [[ $connected == yes ]]; then
        docker network disconnect "$EDGE_NETWORK" "$proxy" || true
    fi
}

if ! activate "$backup/Caddyfile.candidate"; then
    undo_network
    die "reload failed; the proxy keeps its previous configuration"
fi

host_statuses "$backup/Caddyfile" > "$backup/matbakhy-after.txt"
sed 's/^/after:  /' "$backup/matbakhy-after.txt"
if ! changed=$(diff "$backup/matbakhy-before.txt" "$backup/matbakhy-after.txt"); then
    echo "proxy: Matbakhy hosts answer differently after the change; restoring:" >&2
    echo "$changed" >&2
    stage "$backup/Caddyfile" && activate "$backup/Caddyfile" || echo "proxy: RESTORE FAILED; see $backup" >&2
    undo_network
    exit 1
fi
echo "attached: https://$hostname -> lawdesk-web:8080 (backup $backup)"
