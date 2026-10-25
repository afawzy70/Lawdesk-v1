#!/usr/bin/env bash
# Smoke-tests a running stack from inside the web container, so it exercises web -> backend -> database
# without any published host port. Requires LAWDESK_REGISTRY and LAWDESK_TAG (the compose file needs them);
# LAWDESK_ENV_FILE as in deploy.sh.
set -euo pipefail

deploy_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose=(docker compose -p lawdesk-staging -f "$deploy_dir/compose.yaml")
env_file=${LAWDESK_ENV_FILE:-}
if [[ -z $env_file && -f $deploy_dir/staging.env ]]; then
    env_file=$deploy_dir/staging.env
fi
if [[ -n $env_file ]]; then
    compose+=(--env-file "$env_file")
fi

# Each check ends in "|| fail": under "sh -e" a failure on the left of "&&" does not stop the script (QAD-012).
# shellcheck disable=SC2016 # expanded inside the container
"${compose[@]}" exec -T web sh -euc '
    base=http://127.0.0.1:8080
    fail() { echo "FAIL $*" >&2; exit 1; }
    # busybox wget prints the status in a header line and, on errors, in "server returned error: HTTP/1.1 401 ...".
    status_of() { wget -q -S -O /dev/null "$base$1" 2>&1 | grep -o "HTTP/1\.[01] [0-9][0-9][0-9]" | tail -n 1 | cut -d " " -f 2; }
    expect_ok() { wget -q -O /dev/null "$base$1" || fail "$1 is not reachable with a 2xx status"; echo "ok   $1"; }
    expect_status() {
        actual=$(status_of "$1")
        [ "$actual" = "$2" ] || fail "$1 -> ${actual:-no response}, expected $2"
        echo "ok   $1 -> $2"
    }
    expect_ok /healthz
    expect_ok /
    readiness=$(wget -q -O - "$base/actuator/health/readiness") || fail "readiness is not reachable with a 2xx status"
    case "$readiness" in
        *"\"status\":\"UP\""*) echo "ok   readiness UP" ;;
        *) fail "readiness is not UP" ;;
    esac
    expect_status /api/v1/cases 401
    expect_status /actuator/env 404

    # QAD-037: the document upload route takes a PDF of at most 10 MiB (11 MiB of request). nginx'"'"'s default limit of
    # 1 MiB refused every larger one with 413. An anonymous request is turned away by the backend (never a 413 from
    # the proxy) and a body above the route'"'"'s allowance is refused by the proxy. Nothing is stored either way.
    upload=/api/v1/cases/00000000-0000-0000-0000-000000000000/documents
    probe=$(mktemp)
    trap "rm -f $probe" EXIT
    post_status() {
        # Not NUL bytes: busybox wget measures the file as a C string, so a body of zeros is sent as an empty one.
        head -c "$1" /dev/zero | tr "\\000" "a" > "$probe"
        wget -q -S -O /dev/null --header "Content-Type: application/octet-stream" --post-file="$probe" "$base$upload" 2>&1 \
            | grep -o "HTTP/1\.[01] [0-9][0-9][0-9]" | tail -n 1 | cut -d " " -f 2
    }
    actual=$(post_status 1572864)
    [ -n "$actual" ] || fail "$upload gave no answer to a 1.5 MiB body"
    [ "$actual" != 413 ] || fail "$upload: a 1.5 MiB body was refused with 413 by the proxy (client_max_body_size)"
    echo "ok   $upload takes a body above 1 MiB (answer $actual)"
    actual=$(post_status 12582912)
    [ "$actual" = 413 ] || fail "$upload: a 12 MiB body -> ${actual:-no answer}, expected the proxy to refuse it with 413"
    echo "ok   $upload still refuses a 12 MiB body -> 413"
'
