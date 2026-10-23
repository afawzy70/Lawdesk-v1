# shellcheck shell=bash disable=SC2034 # variables are used by the scripts that source this file
# Shared by attach.sh and detach.sh; sourced, not executed.
# The shared proxy belongs to Matbakhy (compose project matbakhy-preview, service proxy). LawDesk only ever edits the
# block between its own markers, through the container's filesystem and `caddy reload`; the container is never
# stopped, restarted or recreated. Besides the live Caddyfile, LawDesk writes only its own Caddyfile.lawdesk-* files.

BEGIN_MARK="# BEGIN lawdesk-staging"
END_MARK="# END lawdesk-staging"
EDGE_NETWORK=lawdesk-edge
LIVE=/etc/caddy/Caddyfile
CANDIDATE=/etc/caddy/Caddyfile.lawdesk-candidate
CHECK=/etc/caddy/Caddyfile.lawdesk-check
PREVIOUS=/etc/caddy/Caddyfile.lawdesk-previous
LAWDESK_HOST_RE='^lawdesk(-[a-z0-9]+)?(\.[a-z0-9-]+)+$'

project=${LAWDESK_PROXY_PROJECT:-matbakhy-preview}
service=${LAWDESK_PROXY_SERVICE:-proxy}
backup_root=${LAWDESK_PROXY_BACKUP_DIR:-/srv/deployments/lawdesk/proxy-backups}

die() {
    echo "proxy: $1" >&2
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

# Refuses (exit 2) unless the file holds no LawDesk block or exactly one well-formed one: both markers at the start of
# their own lines, BEGIN before END. strip_block on anything else could drop unrelated sites (QAD-020).
check_markers() {
    local sequence
    sequence=$(awk -v begin="$BEGIN_MARK" -v end="$END_MARK" '
        /(BEGIN|END) lawdesk-staging/ {
            if (index($0, begin) == 1) printf "B"; else if (index($0, end) == 1) printf "E"; else printf "?"
        }' "$1")
    [[ -z $sequence || $sequence == BE ]] ||
        die "the LawDesk markers in the live Caddyfile are malformed (found ${sequence//\?/x}); fix them by hand" 2
}

# Prints a Caddyfile without the LawDesk block (call check_markers first).
strip_block() {
    awk -v begin="$BEGIN_MARK" -v end="$END_MARK" '
        index($0, begin) == 1 { skip = 1; next }
        skip && index($0, end) == 1 { skip = 0; next }
        !skip' "$1"
}

# The host names Caddy itself serves for a Caddyfile inside the proxy (its placeholders expanded with the proxy's
# environment), sorted, one per line. Fails when Caddy cannot adapt the file.
served_hosts() {
    local json
    json=$(docker exec "$proxy" caddy adapt --config "$1" --adapter caddyfile 2> /dev/null) || return 1
    { grep -o '"host": *\[[^]]*\]' <<< "$json" || true; } | grep -o '"[^"]*"' | grep -vx '"host"' | tr -d '"' |
        sort -u || true
}

# "<host> <status>" for each host listed in a file, asked through the local 443 listener (000: no answer).
host_statuses() {
    local host
    while read -r host; do
        [[ $host == *'*'* ]] && continue
        echo "$host $(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 --resolve "$host:443:127.0.0.1" \
            "https://$host/" 2> /dev/null || true)"
    done < "$1"
}

# Copies a local Caddyfile into the proxy beside the live one and validates it there (nothing is running it yet).
stage() {
    docker cp "$1" "$proxy:$CANDIDATE" && docker exec "$proxy" caddy validate --config "$CANDIDATE" --adapter caddyfile
}

# activate <staged file> <file it replaces>: makes the staged file the running configuration (graceful, in place),
# then the persisted one. A failed reload leaves Caddy on its previous configuration; a failed persist reloads the
# replaced file again, so the proxy never keeps running what it would not restart with (QAD-019).
activate() {
    docker cp "$2" "$proxy:$PREVIOUS" || return 1
    docker exec "$proxy" caddy reload --config "$CANDIDATE" --adapter caddyfile || return 1
    docker cp "$1" "$proxy:$LIVE" && return 0
    echo "proxy: the new configuration could not be persisted; reloading the previous one" >&2
    docker exec "$proxy" caddy reload --config "$PREVIOUS" --adapter caddyfile ||
        echo "proxy: RESTORE FAILED: the proxy runs a configuration that is not its persisted one" >&2
    return 1
}
