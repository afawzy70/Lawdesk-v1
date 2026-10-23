#!/usr/bin/env bash
# Rollback drill on the real staging stack, run by the operator (never by CI):
#   1. derive a broken release from the running one: its backend image refuses to start, its web image is unchanged;
#   2. deploy it with the running release's own deploy.sh, exactly as the gate would;
#   3. pass only if deploy.sh reported the failure, the running release is unchanged and passes its smoke test;
#   4. remove the two drill image tags (nothing else).
# The data volume is untouched and the database is backed up first by deploy.sh. Exit: 0 passed, 1 failed, 2 refused.
set -euo pipefail

home=${LAWDESK_HOME:-/srv/deployments/lawdesk}
registry=lawdesk-local
drill=deaddeaddeaddeaddeaddeaddeaddeaddeaddead
state=$home/state
current=$( [[ -f $state/current-release ]] && cat "$state/current-release" || true )
scripts=$home/releases/$current/deploy/scripts
if [[ -z $current || ! -x $scripts/deploy.sh ]]; then
    echo "drill: no running release to protect; deploy one first" >&2
    exit 2
fi
release_env=(
    "LAWDESK_REGISTRY=$registry"
    "LAWDESK_IMAGE_SOURCE=local"
    "LAWDESK_STATE_DIR=$state"
    "LAWDESK_RELEASES_DIR=$home/releases"
    "LAWDESK_ENV_FILE=$home/staging.env"
    "LAWDESK_BACKUP_DIR=$home/backups"
    "LAWDESK_SECRETS_DIR=$home/secrets"
)

echo "drill: running release $current; building the broken drill release $drill"
# Labelled with the drill revision: deploy.sh accepts only images whose label matches their tag.
docker build --label "org.opencontainers.image.revision=$drill" -t "$registry/backend:$drill" - << EOF
FROM $registry/backend:$current
ENTRYPOINT ["sh", "-c", "echo 'LawDesk rollback drill: this release refuses to start' >&2; exit 1"]
EOF
docker build --label "org.opencontainers.image.revision=$drill" -t "$registry/web:$drill" - << EOF
FROM $registry/web:$current
EOF

verdict=passed
deploy_status=0
history_before=$( [[ -f $state/history.log ]] && wc -l < "$state/history.log" || echo 0 )
env "${release_env[@]}" "$scripts/deploy.sh" "$drill" || deploy_status=$?
after=$(cat "$state/current-release")
if [[ $deploy_status != 1 ]]; then
    echo "drill: deploy.sh exited $deploy_status, expected 1 (failed and rolled back)"
    verdict=failed
fi
# Exit 1 also means "refused before starting"; only a release that started and failed exercises the rollback.
if ! tail -n "+$((history_before + 1))" "$state/history.log" | grep -q "failed $drill"; then
    echo "drill: deploy.sh did not start the drill release, so the rollback was not exercised"
    verdict=failed
fi
if [[ $after != "$current" ]]; then
    echo "drill: current release is now $after, expected $current"
    verdict=failed
fi
if ! env "${release_env[@]}" LAWDESK_TAG="$current" "$scripts/smoke.sh"; then
    echo "drill: the restored release does not pass its smoke test"
    verdict=failed
fi
docker image rm "$registry/backend:$drill" "$registry/web:$drill" > /dev/null || true
echo "drill: last history entries:"
tail -n 6 "$state/history.log" || true

if [[ $verdict == passed ]]; then
    echo "DRILL PASSED: broken release $drill was rolled back to $current"
    exit 0
fi
echo "DRILL FAILED"
exit 1
