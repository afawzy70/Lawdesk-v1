# shellcheck shell=bash disable=SC2034 # variables are used by the scripts that source this file
# Shared by attach.sh and detach.sh; sourced, not executed.
# The shared proxy belongs to Matbakhy (compose project matbakhy-preview, service proxy). LawDesk only ever edits the
# block between its own markers, through the container's filesystem and `caddy reload`; the container is never
# stopped, restarted or recreated.

BEGIN_MARK="# BEGIN lawdesk-staging"
END_MARK="# END lawdesk-staging"
EDGE_NETWORK=lawdesk-edge
LIVE=/etc/caddy/Caddyfile
CANDIDATE=/etc/caddy/Caddyfile.lawdesk-candidate

project=${LAWDESK_PROXY_PROJECT:-matbakhy-preview}
service=${LAWDESK_PROXY_SERVICE:-proxy}
backup_root=${LAWDESK_PROXY_BACKUP_DIR:-/srv/deployments/lawdesk/proxy-backups}

die() {
    echo "proxy: $*" >&2
    exit "${2:-1}"
}

# Exactly one running proxy container, found by its compose labels (read-only).
find_proxy() {
    local ids
    ids=$(docker ps --filter "label=com.docker.compose.project=$project" \
        --filter "label=com.docker.compose.service=$service" --format '{{.ID}}')
    [[ $(wc -w <<< "$ids") == 1 ]] || { echo "proxy: expected one running $project/$service container" >&2; exit 2; }
    proxy=$ids
}

on_edge() {
    docker inspect -f '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' "$proxy" |
        tr ' ' '\n' | grep -qx "$EDGE_NETWORK"
}

# Prints a Caddyfile without the LawDesk block.
strip_block() {
    awk -v begin="$BEGIN_MARK" -v end="$END_MARK" '
        index($0, begin) == 1 { skip = 1; next }
        skip && index($0, end) == 1 { skip = 0; next }
        !skip' "$1"
}

# The public hosts the proxy serves for Matbakhy, from its own Caddyfile and PREVIEW_DOMAIN.
matbakhy_hosts() {
    local domain
    domain=$(docker exec "$proxy" printenv PREVIEW_DOMAIN)
    # shellcheck disable=SC2016 # matches the literal {$PREVIEW_DOMAIN} placeholder
    sed -n 's/^\([a-z0-9-]*\)\.{\$PREVIEW_DOMAIN} {$/\1/p' "$1" | while read -r sub; do echo "$sub.$domain"; done
}

# "<host> <status>" for each Matbakhy host, asked through the local 443 listener.
host_statuses() {
    local host
    for host in $(matbakhy_hosts "$1"); do
        echo "$host $(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 --resolve "$host:443:127.0.0.1" \
            "https://$host/" 2> /dev/null || true)"
    done
}

# Copies a local Caddyfile into the proxy beside the live one and validates it there (nothing is running it yet).
stage() {
    docker cp "$1" "$proxy:$CANDIDATE" && docker exec "$proxy" caddy validate --config "$CANDIDATE" --adapter caddyfile
}

# Makes the staged file the running configuration (graceful, in place), then the persisted one. If the reload fails
# Caddy keeps serving the previous configuration.
activate() {
    docker exec "$proxy" caddy reload --config "$CANDIDATE" --adapter caddyfile && docker cp "$1" "$proxy:$LIVE"
}
