#!/usr/bin/env bash
# Smoke-tests a running stack from inside the web container, so it exercises web -> backend -> database
# without any published host port. Requires LAWDESK_REGISTRY and LAWDESK_TAG (the compose file needs them).
set -euo pipefail

deploy_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
compose=(docker compose -p lawdesk-staging -f "$deploy_dir/compose.yaml")
if [[ -f $deploy_dir/staging.env ]]; then
    compose+=(--env-file "$deploy_dir/staging.env")
fi

# shellcheck disable=SC2016 # expanded inside the container
"${compose[@]}" exec -T web sh -euc '
    check() { wget -q -O /dev/null "http://127.0.0.1:8080$1" && echo "ok   $1"; }
    status_of() { wget -q -S -O /dev/null "http://127.0.0.1:8080$1" 2>&1 | awk "/HTTP\\//{code=\$2} END{print code}"; }
    check /healthz
    check /
    wget -q -O - http://127.0.0.1:8080/actuator/health/readiness | grep -q "\"status\":\"UP\"" && echo "ok   readiness UP"
    [ "$(status_of /api/v1/cases)" = 401 ] && echo "ok   /api/v1/cases -> 401"
    [ "$(status_of /actuator/env)" = 404 ] && echo "ok   /actuator/env -> 404"
'
