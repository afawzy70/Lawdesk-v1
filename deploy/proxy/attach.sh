#!/usr/bin/env bash
# Publishes LawDesk staging through the shared (Matbakhy) Caddy proxy without restarting it:
#   1. back up the live Caddyfile; refuse malformed LawDesk markers; record how every other host it serves answers
#      (hosts as Caddy itself adapts them, so literal and placeholder addresses both count);
#   2. append only the LawDesk site between its markers (an earlier LawDesk block is replaced, nothing else changes);
#   3. validate the candidate inside the proxy and require it to serve exactly the other hosts plus LawDesk;
#   4. join the proxy to lawdesk-edge, reload gracefully, persist;
#   5. ask every other host again; any difference restores the backup and leaves lawdesk-edge.
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
[[ $hostname =~ $LAWDESK_HOST_RE ]] || die "hostname must be lawdesk.<domain>: $hostname" 2
docker network inspect "$EDGE_NETWORK" > /dev/null 2>&1 || die "network $EDGE_NETWORK does not exist; deploy LawDesk first" 2

backup=$backup_root/$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "$backup"
docker cp "$proxy:$LIVE" "$backup/Caddyfile"
echo "backup: $backup"
check_markers "$backup/Caddyfile"

# The hosts that stay after the old LawDesk block is removed; only LawDesk hosts may go with it.
strip_block "$backup/Caddyfile" > "$backup/Caddyfile.kept"
docker cp "$backup/Caddyfile.kept" "$proxy:$CHECK"
served_hosts "$LIVE" > "$backup/hosts-live.txt" || die "Caddy cannot adapt the live Caddyfile" 2
served_hosts "$CHECK" > "$backup/hosts-kept.txt" || die "Caddy cannot adapt the live Caddyfile without LawDesk" 2
dropped=$(comm -23 "$backup/hosts-live.txt" "$backup/hosts-kept.txt" | grep -Ev "$LAWDESK_HOST_RE" || true)
[[ -z $dropped ]] || die "the LawDesk markers enclose other sites; fix by hand: ${dropped//$'\n'/ }" 2

host_statuses "$backup/hosts-kept.txt" > "$backup/matbakhy-before.txt"
sed 's/^/before: /' "$backup/matbakhy-before.txt"
[[ -s $backup/matbakhy-before.txt ]] || die "baseline incomplete: no other site found to compare before and after" 2
if grep -q ' 000$' "$backup/matbakhy-before.txt"; then
    die "baseline incomplete: $(grep ' 000$' "$backup/matbakhy-before.txt" | cut -d ' ' -f 1 | xargs) did not answer" 2
fi

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
served_hosts "$CANDIDATE" > "$backup/hosts-candidate.txt" || die "Caddy cannot adapt the candidate; nothing changed"
if ! sites=$(diff <(echo "$hostname" | sort -u - "$backup/hosts-kept.txt") "$backup/hosts-candidate.txt"); then
    die "the candidate would not serve exactly the existing sites plus $hostname; nothing changed: $sites" 2
fi

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

if ! activate "$backup/Caddyfile.candidate" "$backup/Caddyfile"; then
    undo_network
    die "activation failed; the proxy keeps its previous configuration"
fi

host_statuses "$backup/hosts-kept.txt" > "$backup/matbakhy-after.txt"
sed 's/^/after:  /' "$backup/matbakhy-after.txt"
if ! changed=$(diff "$backup/matbakhy-before.txt" "$backup/matbakhy-after.txt"); then
    echo "proxy: Matbakhy hosts answer differently after the change; restoring:" >&2
    echo "$changed" >&2
    stage "$backup/Caddyfile" && activate "$backup/Caddyfile" "$backup/Caddyfile.candidate" ||
        echo "proxy: RESTORE FAILED; see $backup" >&2
    undo_network
    exit 1
fi
echo "attached: https://$hostname -> lawdesk-web:8080 (backup $backup)"
