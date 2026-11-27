#!/usr/bin/env bash
# Post-deployment checks. Usage: verify.sh <hostname> [basic-auth-user:password-file]
# Prints PASS/FAIL per check; exit 1 on any failure. Never prints credentials.
set -uo pipefail
host=${1:?hostname, e.g. floorplan.167-86-90-174.sslip.io}
cred=${2:-}
fail=0
check() { # check <name> <command...>: PASS when the command succeeds
    local name=$1
    shift
    if "$@"; then echo "PASS $name"; else echo "FAIL $name"; fail=1; fi
}
# shellcheck disable=SC2329 # called through check()
is() { [[ $1 == "$2" ]]; }
code() { curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$@" 2> /dev/null || echo 000; }
# shellcheck disable=SC2329 # called through check()
has() { grep -q "$1" <<< "$2"; }
domain=${host#floorplan.}
check "DNS resolves" test -n "$(getent hosts "$host")"
check "HTTPS certificate valid" is "$(curl -sS -o /dev/null -w '%{ssl_verify_result}' --max-time 20 "https://$host/" 2> /dev/null)" 0
check "no credentials -> 401" is "$(code "https://$host/")" 401
probe=invalid-probe # a deliberately wrong login; not a credential
check "wrong credentials -> 401" is "$(code -u "$probe:$probe" "https://$host/")" 401
if [[ -n $cred ]]; then
    auth=(-u "${cred%%:*}:$(cat "${cred#*:}")")
    check "home 200 with credentials" is "$(code "${auth[@]}" "https://$host/")" 200
    check "Arabic RTL home" has 'lang="ar" dir="rtl"' "$(curl -sS "${auth[@]}" --max-time 20 "https://$host/" 2> /dev/null)"
    hdr=$(curl -sSI "${auth[@]}" --max-time 20 "https://$host/" 2> /dev/null | tr '[:upper:]' '[:lower:]')
    for h in content-security-policy x-content-type-options referrer-policy x-robots-tag strict-transport-security; do
        check "header $h" has "^$h:" "$hdr"
    done
    check "POST refused" is "$(code "${auth[@]}" -X POST "https://$host/")" 405
    check "dotfiles 404" is "$(code "${auth[@]}" "https://$host/.git/config")" 404
    check "no API surface" is "$(code "${auth[@]}" "https://$host/api/v1/release")" 404
    check "build.json carries the source sha" has '"source_sha"' "$(curl -sS "${auth[@]}" "https://$host/build.json" 2> /dev/null)"
fi
# Existing services must be unchanged.
check "LawDesk healthz 200" is "$(code "https://lawdesk.$domain/healthz")" 200
for h in app ops api id media; do echo "INFO matbakhy $h $(code "https://$h.$domain/")"; done
exit $fail
