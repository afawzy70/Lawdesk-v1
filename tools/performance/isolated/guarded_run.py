#!/usr/bin/env python3
"""Run one isolated k6 profile with host, neighbor-health, and HTTP stop conditions."""
import json
import os
import shutil
import stat
import subprocess
import sys
import time
import re
import urllib.error
import urllib.request
from collections import deque
from datetime import datetime, timedelta, timezone
from pathlib import Path

COMPOSE = Path(__file__).with_name("compose.yaml")
SHARED = Path(__file__).with_name("compose.shared.yaml")
ROOT = COMPOSE.parents[3]
PROFILES = {"seed", "api", "matrix", "smoke", "baseline", "load", "stress", "spike", "recovery", "soak"}
HOST_MODE = os.environ.get("LAWDESK_PERF_HOST_MODE")


def compose_prefix():
    if HOST_MODE not in ("dedicated-verified", "shared-verified"):
        raise RuntimeError("set LAWDESK_PERF_HOST_MODE only after host isolation is verified")
    command = ["docker", "compose", "-f", str(COMPOSE)]
    if HOST_MODE == "shared-verified":
        command += ["-f", str(SHARED)]
    return command


def compose(*args, **kwargs):
    return subprocess.run([*compose_prefix(), *args], check=True, **kwargs)


def image_revision(image):
    return subprocess.check_output(["docker", "image", "inspect", "--format",
                                    '{{index .Config.Labels "org.opencontainers.image.revision"}}', image],
                                   text=True).strip()


def container_limits(container):
    raw = subprocess.check_output(["docker", "inspect", "--format",
                                   "{{.HostConfig.NanoCpus}}|{{.HostConfig.Memory}}|{{.HostConfig.PidsLimit}}|"
                                   "{{range $name, $_ := .NetworkSettings.Networks}}{{$name}},{{end}}", container],
                                  text=True).strip()
    cpu, memory, pids, networks = raw.split("|")
    return {"cpu": int(cpu) / 1e9, "memory": int(memory), "pids": int(pids),
            "networks": set(networks.strip(",").split(","))}


def mount_guard(container, service, secrets_dir, evidence_dir=None):
    mounts = json.loads(subprocess.check_output(["docker", "inspect", "--format", "{{json .Mounts}}",
                                                 container], text=True))
    allowed_files = {str((secrets_dir / name).resolve()) for name in
                     ("db_admin_password.txt", "db_app_password.txt", "admin_bootstrap_password.txt",
                      "perf_accounts.json")}
    allowed_files.add(str((ROOT / "deploy/postgres/app-role.sql").resolve()))
    if service == "k6":
        allowed_files.add(str((ROOT / "tools/performance/k6").resolve()))
        allowed_files.add(str(evidence_dir.resolve()))
    for mount in mounts:
        kind, target = mount["Type"], mount["Destination"]
        if kind == "tmpfs" and target == "/tmp":
            continue
        if kind == "volume" and service == "db" and mount.get("Name") == "lawdesk-perf_perf-db" and target == "/var/lib/postgresql":
            continue
        if kind == "bind" and mount["Source"] in allowed_files:
            writable_evidence = evidence_dir is not None and mount["Source"] == str(evidence_dir.resolve())
            if mount.get("RW") and not writable_evidence:
                raise RuntimeError("isolated source bind is writable")
            continue
        raise RuntimeError("unexpected mount in isolated " + service + " container")


def neighbor_state(project):
    ids = subprocess.check_output(["docker", "ps", "-q", "--filter",
                                   "label=com.docker.compose.project=" + project], text=True).split()
    state = {}
    for container in ids:
        raw = subprocess.check_output(["docker", "inspect", "--format",
                                       "{{.Name}}|{{.State.Running}}|"
                                       "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}|"
                                       "{{.RestartCount}}", container], text=True).strip()
        name, running, health, restarts = raw.split("|")
        state[container] = {"name": name, "running": running == "true", "health": health,
                            "restarts": int(restarts)}
    return state


def neighbor_changed(baseline):
    for project, before in baseline.items():
        after = neighbor_state(project)
        if set(after) != set(before):
            return project + " container set changed"
        for container, prior in before.items():
            current = after[container]
            if not current["running"] or current["health"] not in ("healthy", "none"):
                return project + " container became unavailable"
            if current["restarts"] != prior["restarts"]:
                return project + " container restarted"
    return None


def rootless_slice_guard():
    uid = os.getuid()
    parent = Path(f"/sys/fs/cgroup/user.slice/user-{uid}.slice")
    try:
        quota, period = (int(item) for item in (parent / "cpu.max").read_text().split())
        memory = int((parent / "memory.max").read_text().strip())
    except (OSError, ValueError) as error:
        raise RuntimeError("root-owned lawdesk-ci user slice cap cannot be verified") from error
    if quota / period > 3 or memory > 5 * 1024 ** 3:
        raise RuntimeError("root-owned lawdesk-ci user slice exceeds 3 CPUs or 5 GiB")
    unit = subprocess.check_output(["systemctl", "--user", "show", "lawdesk-ci-docker.service",
                                    "--property=Environment", "--value"], text=True)
    if "DOCKERD_ROOTLESS_ROOTLESSKIT_DISABLE_HOST_LOOPBACK=true" not in unit:
        raise RuntimeError("rootless daemon host-loopback isolation is not active")
    if os.access("/var/run/docker.sock", os.R_OK | os.W_OK):
        raise RuntimeError("runner account can access the host Docker socket")


def k6_limits_guard(maximum, secrets_dir, evidence_dir):
    ids = subprocess.check_output(["docker", "ps", "-q", "--filter",
                                   "label=com.docker.compose.project=lawdesk-perf", "--filter",
                                   "label=com.docker.compose.service=k6"], text=True).split()
    if len(ids) != 1:
        return "expected exactly one isolated k6 container"
    limits = container_limits(ids[0])
    if limits["cpu"] <= 0 or limits["cpu"] > maximum or limits["memory"] <= 0 or limits["pids"] <= 0:
        return "k6 runtime resource cap missing or too high"
    if limits["networks"] != {"lawdesk-perf_perf-internal"}:
        return "k6 joined a shared network"
    try:
        mount_guard(ids[0], "k6", secrets_dir, evidence_dir)
    except RuntimeError as error:
        return str(error)
    return None


def preflight():
    compose_prefix()
    if os.environ.get("CI"):
        raise RuntimeError("performance Docker access is forbidden from CI jobs")
    if os.environ.get("LAWDESK_PERF_UID") != str(os.getuid()):
        raise RuntimeError("LAWDESK_PERF_UID must equal the local user ID")
    app_sha = os.environ.get("LAWDESK_PERF_SHA", "")
    if not re.fullmatch(r"[0-9a-f]{40}", app_sha):
        raise RuntimeError("LAWDESK_PERF_SHA must be the exact 40-character application commit")
    if subprocess.run(["git", "diff", "--quiet", app_sha, "HEAD", "--", "backend", "frontend"],
                      cwd=ROOT).returncode != 0:
        raise RuntimeError("backend/frontend source differs from the tagged application SHA")
    config = json.loads(compose("--profile", "load", "config", "--format", "json", capture_output=True, text=True).stdout)
    if config["name"] != "lawdesk-perf" or set(config["networks"]) != {"perf-internal"}:
        raise RuntimeError("unexpected Compose project or network")
    if not config["networks"]["perf-internal"].get("internal"):
        raise RuntimeError("performance network is not internal")
    services = config["services"]
    if set(services) != {"db", "backend", "web", "k6"}:
        raise RuntimeError("unexpected Compose service")
    for service in services.values():
        if set(service.get("networks", {})) != {"perf-internal"}:
            raise RuntimeError("a service joined another network")
    ports = services["web"].get("ports", [])
    if len(ports) != 1 or ports[0].get("host_ip") != "127.0.0.1" or str(ports[0].get("published")) != "18080":
        raise RuntimeError("web ingress is not loopback-only")
    if any(services[name].get("ports") for name in ("db", "backend", "k6")):
        raise RuntimeError("db, backend or k6 publishes a host port")
    if services["backend"]["environment"].get("SPRING_DATASOURCE_URL") != "jdbc:postgresql://db:5432/lawdesk":
        raise RuntimeError("backend is not bound to the private Compose database")
    for name in ("backend", "web"):
        image = services[name]["image"]
        if HOST_MODE == "shared-verified" and image != f"lawdesk-local/{name}:{app_sha}":
            raise RuntimeError("shared host requires pinned lawdesk-local rootless images for " + name)
        if image_revision(image) != app_sha:
            raise RuntimeError("image revision label does not match application SHA for " + name)
    if HOST_MODE == "shared-verified":
        info = subprocess.check_output(["docker", "info", "--format",
                                        "{{json .SecurityOptions}}|{{.CgroupVersion}}"], text=True).strip()
        if "rootless" not in info.lower() or not info.endswith("|2"):
            raise RuntimeError("shared-host Docker must be rootless with cgroup v2")
        rootless_slice_guard()
        try:
            scale = float(os.environ.get("LAWDESK_PERF_RATE_SCALE", ""))
        except ValueError:
            scale = 0
        if not 0 < scale <= .25:
            raise RuntimeError("shared-host RATE_SCALE must be greater than zero and at most 0.25")
        expected_cpus = {"db": .75, "backend": .75, "web": .25, "k6": .5}
    else:
        expected_cpus = {"db": 1.0, "backend": 1.0, "web": .5, "k6": 1.0}
    expected_memory = {"db": 512, "backend": 768, "web": 96, "k6": 384 if HOST_MODE == "shared-verified" else 512}
    for name, maximum in expected_cpus.items():
        if float(services[name].get("cpus", 0)) > maximum or float(services[name].get("cpus", 0)) <= 0:
            raise RuntimeError("CPU cap missing or too high on " + name)
        if int(services[name].get("mem_limit", 0)) > expected_memory[name] * 1024 ** 2 or int(services[name].get("mem_limit", 0)) <= 0:
            raise RuntimeError("memory cap missing or too high on " + name)
    secrets_dir = Path(os.environ["LAWDESK_PERF_SECRETS_DIR"])
    if stat.S_IMODE(secrets_dir.stat().st_mode) != 0o700 or secrets_dir.stat().st_uid != os.getuid():
        raise RuntimeError("isolated secret directory must be owned by this user and mode 0700")
    marker = secrets_dir / ".lawdesk-perf-owned"
    if marker.is_symlink() or not marker.is_file() or marker.read_text() != "phase8-isolated\n":
        raise RuntimeError("secret directory was not created by the Phase 8 provisioner")
    for name in ("db_admin_password.txt", "db_app_password.txt", "admin_bootstrap_password.txt", "perf_accounts.json"):
        secret = secrets_dir / name
        if not secret.is_file() or secret.is_symlink() or secret.stat().st_uid != os.getuid() or stat.S_IMODE(secret.stat().st_mode) != 0o600:
            raise RuntimeError("private credentials missing or unsafe: " + name)
    source = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    if os.environ.get("LAWDESK_PERF_SUITE_SHA") != source:
        raise RuntimeError("LAWDESK_PERF_SUITE_SHA must equal the exact test-suite HEAD")
    for name in ("db", "backend", "web"):
        container = compose("ps", "-q", name, capture_output=True, text=True).stdout.strip()
        if not container:
            raise RuntimeError("isolated container is not running: " + name)
        limits = container_limits(container)
        if (limits["cpu"] > expected_cpus[name] or limits["cpu"] <= 0 or
                limits["memory"] > expected_memory[name] * 1024 ** 2 or limits["memory"] <= 0 or
                limits["pids"] <= 0):
            raise RuntimeError("runtime cgroup cap missing or too high on " + name)
        if limits["networks"] != {"lawdesk-perf_perf-internal"}:
            raise RuntimeError("isolated container joined a shared network: " + name)
        mount_guard(container, name, secrets_dir)
    baseline = {}
    if HOST_MODE == "shared-verified":
        # The CI user's rootless daemon cannot see Matbakhy's host-Docker containers.
        # Read-only rootless neighbors can still be watched; do not contact Matbakhy.
        for project in ("lawdesk-staging",):
            state = neighbor_state(project)
            if state:
                if any(not item["running"] or item["health"] not in ("healthy", "none") for item in state.values()):
                    raise RuntimeError(project + " rootless containers are unhealthy; shared load is refused")
                baseline[project] = state
        host = host_guard()
        if (host["load_1m"] >= 5.5 or host["available_gib"] < 8 or host["disk_used_pct"] >= 70 or
                not host["web_healthy"] or not host["backend_ready"]):
            raise RuntimeError("shared-host headroom or isolated web health is insufficient")
    return baseline


def host_guard():
    load = float(Path("/proc/loadavg").read_text().split()[0])
    available_kib = next(int(line.split()[1]) for line in Path("/proc/meminfo").read_text().splitlines()
                         if line.startswith("MemAvailable:"))
    disk = shutil.disk_usage("/")
    def healthy_path(path):
        try:
            with urllib.request.urlopen("http://127.0.0.1:18080" + path, timeout=3) as response:
                return response.status == 200
        except (urllib.error.URLError, TimeoutError):
            return False
    web_healthy = healthy_path("/healthz")
    backend_ready = healthy_path("/actuator/health/readiness")
    return {"load_1m": load, "available_gib": round(available_kib / 1024 / 1024, 2),
            "disk_used_pct": round(100 * disk.used / disk.total, 1), "web_healthy": web_healthy,
            "backend_ready": backend_ready}


def stop_k6():
    ids = subprocess.check_output(["docker", "ps", "-q", "--filter", "label=com.docker.compose.project=lawdesk-perf",
                                   "--filter", "label=com.docker.compose.service=k6"], text=True).split()
    if ids:
        subprocess.run(["docker", "stop", "--time", "10", *ids], check=False, stdout=subprocess.DEVNULL)


def service_observations():
    ids = subprocess.check_output(["docker", "ps", "-q", "--filter", "label=com.docker.compose.project=lawdesk-perf"],
                                  text=True).split()
    stats = []
    if ids:
        raw = subprocess.check_output(["docker", "stats", "--no-stream", "--format", "{{json .}}", *ids], text=True)
        stats = [json.loads(line) for line in raw.splitlines() if line]
    rows = compose("exec", "-T", "db", "psql", "-U", "postgres", "-d", "lawdesk", "-Atc",
                   "select coalesce(state,'other'),count(*) from pg_stat_activity "
                   "where datname='lawdesk' group by state order by state", capture_output=True, text=True).stdout
    connections = dict(line.split("|", 1) for line in rows.splitlines() if "|" in line)
    return {"docker_stats": stats, "db_connections_by_state": connections}


def dataset_guard(profile, evidence):
    release = compose("exec", "-T", "db", "psql", "-U", "postgres", "-d", "lawdesk", "-Atc",
                      "select version from lawdesk.app_release order by id desc limit 1",
                      capture_output=True, text=True).stdout.strip()
    if release != os.environ["LAWDESK_PERF_SHA"]:
        raise RuntimeError("backend release SHA differs from LAWDESK_PERF_SHA")
    if profile in ("seed", "api"):
        counts = compose("exec", "-T", "db", "psql", "-U", "postgres", "-d", "lawdesk", "-Atc",
                         "select (select count(*) from lawdesk.legal_case),"
                         "(select count(*) from lawdesk.proceeding),"
                         "(select count(*) from lawdesk.court where code like 'SYN-%')",
                         capture_output=True, text=True).stdout.strip()
        cases, proceedings, courts = map(int, counts.split("|"))
        if profile == "seed" and (cases != 0 or courts != 0):
            raise RuntimeError("seed profile requires a fresh synthetic dataset")
        if profile == "api" and (cases < 20 or proceedings < 50 or courts < 30):
            raise RuntimeError("API baseline needs at least 20 synthetic cases, 50 proceedings and 30 courts")
        return
    if profile == "recovery":
        if not (evidence / "spike-guard.jsonl").is_file():
            raise RuntimeError("recovery must follow spike on the same dataset")
        return
    sql = ("select (select count(*) from lawdesk.legal_case),"
           "(select count(*) from lawdesk.task),"
           "(select count(*) from lawdesk.case_document),"
           "(select count(*) from lawdesk.expert),"
           "(select count(*) from lawdesk.expert_referral)")
    raw = compose("exec", "-T", "db", "psql", "-U", "postgres", "-d", "lawdesk", "-Atc", sql,
                  capture_output=True, text=True).stdout.strip()
    if raw != "10000|30000|1000|60|5000":
        raise RuntimeError("dataset differs from seeded snapshot; restore it before this profile (counts " + raw + ")")


def main(profile):
    if profile not in PROFILES:
        raise RuntimeError("unknown profile")
    os.environ["LAWDESK_PERF_SCRIPT"] = ("seed-via-api.js" if profile == "seed" else
                                          "api-baseline.js" if profile == "api" else
                                          "export-matrix.js" if profile == "matrix" else "lawdesk-mix.js")
    os.environ["LAWDESK_PERF_PROFILE"] = profile
    neighbors = preflight()
    evidence = Path(os.environ["LAWDESK_PERF_EVIDENCE_DIR"])
    evidence.mkdir(mode=0o700, parents=True, exist_ok=True)
    dataset_guard(profile, evidence)
    points = evidence / f"{profile}-metrics.jsonl"
    observations = evidence / f"{profile}-guard.jsonl"
    if points.exists() or observations.exists():
        raise RuntimeError("evidence already exists for this profile; use a fresh directory")
    process = subprocess.Popen([*compose_prefix(), "--profile", "load", "run", "--rm", "--no-deps", "k6"])
    recent = deque()
    offset = 0
    reason = None
    started = time.monotonic()
    try:
        with observations.open("w", encoding="utf-8") as output:
            while process.poll() is None:
                time.sleep(10)
                now = datetime.now(timezone.utc)
                state = host_guard()
                state.update(service_observations())
                state["rootless_neighbors"] = {project: neighbor_state(project) for project in neighbors}
                state["k6_limit_error"] = k6_limits_guard(.5 if HOST_MODE == "shared-verified" else 1.0,
                                                            Path(os.environ["LAWDESK_PERF_SECRETS_DIR"]), evidence)
                if points.exists():
                    with points.open(encoding="utf-8") as source:
                        source.seek(offset)
                        while line := source.readline():
                            try:
                                item = json.loads(line)
                                if item.get("type") == "Point" and item.get("metric") == "http_reqs":
                                    at = datetime.fromisoformat(item["data"]["time"].replace("Z", "+00:00"))
                                    status = int(item["data"].get("tags", {}).get("status", "0"))
                                    recent.append((at, status))
                            except (ValueError, KeyError, TypeError):
                                pass
                        offset = source.tell()
                while recent and recent[0][0] < now - timedelta(seconds=60):
                    recent.popleft()
                errors = sum(status >= 500 for _, status in recent)
                state.update({"observed_at_utc": now.isoformat(), "requests_60s": len(recent), "http_5xx_60s": errors})
                output.write(json.dumps(state) + "\n")
                output.flush()
                if state["load_1m"] > (6.5 if HOST_MODE == "shared-verified" else 6) or state["available_gib"] < (6 if HOST_MODE == "shared-verified" else 3) or state["disk_used_pct"] > (75 if HOST_MODE == "shared-verified" else 85):
                    reason = "host resource stop condition"
                elif neighbors and (change := neighbor_changed(neighbors)):
                    reason = change
                elif state["k6_limit_error"]:
                    reason = state["k6_limit_error"]
                elif not state["web_healthy"] or not state["backend_ready"]:
                    reason = "isolated web/backend health failed"
                elif time.monotonic() - started >= 60 and len(recent) >= 20 and errors / len(recent) > .05:
                    reason = "HTTP 5xx exceeded 5% over the last 60 seconds"
                if reason:
                    break
    finally:
        if reason or process.poll() is None:
            stop_k6()
        process.wait(timeout=30)
    if reason:
        print("stopped: " + reason, file=sys.stderr)
        return 3
    return process.returncode


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: guarded_run.py seed|api|matrix|smoke|baseline|load|stress|spike|recovery|soak")
    try:
        raise SystemExit(main(sys.argv[1]))
    except (RuntimeError, KeyError, FileNotFoundError, subprocess.CalledProcessError) as error:
        print("refused: " + str(error), file=sys.stderr)
        raise SystemExit(2) from None
