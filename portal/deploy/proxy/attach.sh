#!/usr/bin/env bash
# Publishes the Floorplan portal through the shared (Matbakhy) Caddy proxy without restarting it, behind HTTP basic
# authentication (an independent boundary: LawDesk accounts are not used). Same safety pattern as deploy/proxy/attach.sh:
#   1. back up the live Caddyfile; refuse malformed markers; record how every other host answers;
#   2. replace only the block between the Floorplan markers; every other line must survive byte for byte;
#   3. validate the candidate inside the proxy and require it to serve exactly the other hosts plus the portal;
#   4. join the proxy to floorplan-edge, reload gracefully, persist;
#   5. ask every other host again; any difference restores the backup and leaves floorplan-edge.
# Run by the operator (docker access) after deploy.sh started the container. Undo: detach.sh. Exit: 0 attached,
# 1 failed and nothing is left changed, 2 refused before any change.
#
# Environment:
#   FLOORPLAN_BASIC_USER             required, a user name (letters, digits, . _ -)
#   FLOORPLAN_BASIC_HASH_FILE        required, a file holding ONE bcrypt hash (from `caddy hash-password`), mode 0600
#   FLOORPLAN_HOSTNAME               default floorplan.<the proxy's PREVIEW_DOMAIN>
#   FLOORPLAN_ALLOWED_IPS            optional space-separated CIDRs; everyone else gets 403 before authentication
#   FLOORPLAN_PUBLIC=yes             owner-approved public access: omits basic auth (default: private)
set -euo pipefail
# shellcheck source=portal/deploy/proxy/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
allowed=${FLOORPLAN_ALLOWED_IPS:-}
[[ -z $allowed || $allowed =~ ^[0-9a-fA-F.:/]+( [0-9a-fA-F.:/]+)*$ ]] || die "FLOORPLAN_ALLOWED_IPS must be CIDRs" 2
public=${FLOORPLAN_PUBLIC:-no}
user=${FLOORPLAN_BASIC_USER:-}
hash=
if [[ $public != yes ]]; then
    [[ $user =~ ^[A-Za-z0-9._-]{3,40}$ ]] || die "FLOORPLAN_BASIC_USER is required (private by default)" 2
    hashfile=${FLOORPLAN_BASIC_HASH_FILE:-}
    [[ -f $hashfile && $(stat -c %a "$hashfile") == 600 ]] || die "FLOORPLAN_BASIC_HASH_FILE must be a mode 0600 file" 2
    hash=$(tr -d '\n' < "$hashfile")
    [[ $hash =~ ^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$ ]] || die "the hash file must hold one bcrypt hash" 2
fi
find_proxy
hostname=${FLOORPLAN_HOSTNAME:-floorplan.$(docker exec "$proxy" printenv PREVIEW_DOMAIN)}
[[ $hostname =~ $FLOORPLAN_HOST_RE ]] || die "hostname must be floorplan.<domain>: $hostname" 2
docker network inspect "$EDGE_NETWORK" > /dev/null 2>&1 || die "network $EDGE_NETWORK does not exist; run deploy.sh first" 2
backup=$backup_root/$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "$backup"
docker cp "$proxy:$LIVE" "$backup/Caddyfile"
echo "backup: $backup"
check_markers "$backup/Caddyfile"
strip_block "$backup/Caddyfile" > "$backup/Caddyfile.kept"
docker cp "$backup/Caddyfile.kept" "$proxy:$CHECK"
served_hosts "$LIVE" > "$backup/hosts-live.txt" || die "Caddy cannot adapt the live Caddyfile" 2
served_hosts "$CHECK" > "$backup/hosts-kept.txt" || die "Caddy cannot adapt the live Caddyfile without the portal" 2
dropped=$(comm -23 "$backup/hosts-live.txt" "$backup/hosts-kept.txt" | grep -Ev "$FLOORPLAN_HOST_RE" || true)
[[ -z $dropped ]] || die "the Floorplan markers enclose other sites; fix by hand: ${dropped//$'\n'/ }" 2
host_statuses "$backup/hosts-kept.txt" > "$backup/others-before.txt"
sed 's/^/before: /' "$backup/others-before.txt"
[[ -s $backup/others-before.txt ]] || die "baseline incomplete: no other site found to compare before and after" 2
if grep -q ' 000$' "$backup/others-before.txt"; then
    die "baseline incomplete: $(grep ' 000$' "$backup/others-before.txt" | cut -d ' ' -f 1 | xargs) did not answer" 2
fi
{
    strip_block "$backup/Caddyfile"
    echo "$BEGIN_MARK (managed by portal/deploy/proxy/attach.sh; remove with detach.sh)"
    echo "$hostname {"
    echo "	route {"
    echo "		header {"
    echo "			Strict-Transport-Security \"max-age=31536000\""
    echo "			X-Robots-Tag \"noindex, nofollow, noarchive\""
    echo "			Cache-Control \"private, no-cache\""
    echo "			-Server"
    echo "		}"
    if [[ -n $allowed ]]; then
        echo "		@fp_outsider not remote_ip $allowed"
        echo "		respond @fp_outsider \"Not available\" 403"
    fi
    if [[ $public != yes ]]; then
        echo "		basic_auth {"
        echo "			$user $hash"
        echo "		}"
    fi
    echo "		reverse_proxy floorplan-web:8080"
    echo "	}"
    echo "}"
    echo "$END_MARK"
} > "$backup/Caddyfile.candidate"
diff <(strip_block "$backup/Caddyfile") <(strip_block "$backup/Caddyfile.candidate") > /dev/null ||
    die "candidate would change another site's configuration" 2
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
host_statuses "$backup/hosts-kept.txt" > "$backup/others-after.txt"
sed 's/^/after:  /' "$backup/others-after.txt"
if ! changed=$(diff "$backup/others-before.txt" "$backup/others-after.txt"); then
    echo "proxy: other hosts answer differently after the change; restoring:" >&2
    echo "$changed" >&2
    stage "$backup/Caddyfile" && activate "$backup/Caddyfile" "$backup/Caddyfile.candidate" ||
        echo "proxy: RESTORE FAILED; see $backup" >&2
    undo_network
    exit 1
fi
echo "attached: https://$hostname -> floorplan-web:8080 ($([[ $public == yes ]] && echo PUBLIC || echo "private, basic auth") ; backup $backup)"
