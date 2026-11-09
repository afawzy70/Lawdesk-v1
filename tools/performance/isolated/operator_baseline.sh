#!/usr/bin/env bash
# Direct lawdesk-ci operator run on the existing rootless daemon. Never invoke from CI.
set -euo pipefail

repo=$(git rev-parse --show-toplevel)
cd "$repo"
[[ $(id -un) == lawdesk-ci && -z ${CI:-} ]] || { echo 'refused: direct lawdesk-ci shell required' >&2; exit 2; }
[[ -z $(git status --porcelain) ]] || { echo 'refused: clean suite checkout required' >&2; exit 2; }
export LAWDESK_PERF_HOST_MODE=shared-verified
export LAWDESK_PERF_SHA=${LAWDESK_PERF_SHA:-1771bded862a49b9e3015eb76115a981944954c8}
export LAWDESK_PERF_SUITE_SHA=$(git rev-parse HEAD)
export LAWDESK_PERF_UID=$(id -u)
export LAWDESK_PERF_RATE_SCALE=0.20
export XDG_RUNTIME_DIR=/run/user/$LAWDESK_PERF_UID
export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/docker.sock
export LAWDESK_PERF_BACKEND_IMAGE=lawdesk-local/backend:$LAWDESK_PERF_SHA
export LAWDESK_PERF_WEB_IMAGE=lawdesk-local/web:$LAWDESK_PERF_SHA
run_id=$(date -u +%Y%m%dT%H%M%SZ)-$$
export LAWDESK_PERF_SECRETS_DIR=/tmp/lawdesk-perf-secrets-$run_id
export LAWDESK_PERF_EVIDENCE_DIR=/tmp/lawdesk-perf-evidence-$run_id
compose=(docker compose -f "$repo/tools/performance/isolated/compose.yaml" -f "$repo/tools/performance/isolated/compose.shared.yaml")

git diff --quiet "$LAWDESK_PERF_SHA" HEAD -- backend frontend || {
  echo 'refused: application trees differ from exact app SHA' >&2; exit 2;
}
python3 - <<'PY'
import importlib.util
from pathlib import Path
spec = importlib.util.spec_from_file_location('phase8_guard', 'tools/performance/isolated/guarded_run.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
guard.rootless_slice_guard()
state = guard.host_guard()
if state['load_1m'] >= 5.5 or state['available_gib'] < 8 or state['disk_used_pct'] >= 70:
    raise SystemExit('refused: shared host headroom is insufficient')
print('prebuild host headroom:', state)
PY
info=$(docker info --format '{{json .SecurityOptions}}|{{.CgroupVersion}}|{{.CgroupDriver}}')
[[ $info == *rootless*'|2|systemd' ]] || { echo 'refused: rootless cgroup-v2/systemd Docker required' >&2; exit 2; }
if pgrep -u "$LAWDESK_PERF_UID" -f 'Runner.Worker' >/dev/null; then
  echo 'refused: a CI worker job is active' >&2; exit 2
fi
[[ -z $(docker ps -aq --filter label=com.docker.compose.project=lawdesk-perf) ]] || {
  echo 'refused: existing lawdesk-perf containers need operator inspection' >&2; exit 2;
}
[[ -z $(docker volume ls -q --filter name='^lawdesk-perf_perf-db$') ]] || {
  echo 'refused: existing lawdesk-perf DB volume needs operator inspection' >&2; exit 2;
}

python3 tools/performance/isolated/accounts.py prepare "$LAWDESK_PERF_SECRETS_DIR"
install -d -m 0700 "$LAWDESK_PERF_EVIDENCE_DIR"
{
  printf 'app_sha=%s\nsuite_sha=%s\n' "$LAWDESK_PERF_SHA" "$LAWDESK_PERF_SUITE_SHA"
  printf 'docker=%s\n' "$info"
  cat /proc/loadavg
  grep MemAvailable /proc/meminfo
  df -h /
  cat "/sys/fs/cgroup/user.slice/user-$LAWDESK_PERF_UID.slice/cpu.max" \
      "/sys/fs/cgroup/user.slice/user-$LAWDESK_PERF_UID.slice/memory.max"
  docker ps --format '{{.Names}} {{.Status}} {{.Label "com.docker.compose.project"}}'
} > "$LAWDESK_PERF_EVIDENCE_DIR/host-before.txt"
"${compose[@]}" --profile load config > "$LAWDESK_PERF_EVIDENCE_DIR/compose-rendered.yaml"

cleanup_on_error() {
  echo 'baseline failed; stopping only the lawdesk-perf project' >&2
  "${compose[@]}" down --remove-orphans || true
}
trap cleanup_on_error ERR
backend_revision=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' \
  "$LAWDESK_PERF_BACKEND_IMAGE" 2>/dev/null || true)
web_revision=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' \
  "$LAWDESK_PERF_WEB_IMAGE" 2>/dev/null || true)
if [[ $backend_revision == "$LAWDESK_PERF_SHA" && $web_revision == "$LAWDESK_PERF_SHA" ]]; then
  echo 'reusing exact-SHA rootless LawDesk images'
else
  "${compose[@]}" build backend web
fi
"${compose[@]}" up --no-build -d db backend web
python3 tools/performance/isolated/accounts.py provision "$LAWDESK_PERF_SECRETS_DIR" http://127.0.0.1:18080
"${compose[@]}" exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 \
  -c 'create extension if not exists pg_stat_statements'
python3 tools/performance/isolated/guarded_run.py seed
"${compose[@]}" exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 \
  -c 'select pg_stat_statements_reset()'
python3 tools/performance/isolated/guarded_run.py api
"${compose[@]}" exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 \
  < tools/performance/db/observe-phase8.sql > "$LAWDESK_PERF_EVIDENCE_DIR/api-db-after.txt"
docker image inspect --format '{{.RepoTags}} {{.Id}} {{index .Config.Labels "org.opencontainers.image.revision"}}' \
  "$LAWDESK_PERF_BACKEND_IMAGE" "$LAWDESK_PERF_WEB_IMAGE" > "$LAWDESK_PERF_EVIDENCE_DIR/images.txt"
"${compose[@]}" ps > "$LAWDESK_PERF_EVIDENCE_DIR/stack-after.txt"
trap - ERR
printf 'baseline complete: app=%s suite=%s evidence=%s\n' \
  "$LAWDESK_PERF_SHA" "$LAWDESK_PERF_SUITE_SHA" "$LAWDESK_PERF_EVIDENCE_DIR"
