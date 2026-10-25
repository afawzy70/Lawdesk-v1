#!/usr/bin/env python3
"""Run one k6 profile with host and HTTP stop conditions on a verified dedicated worker."""
import json
import os
import shutil
import stat
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections import deque
from datetime import datetime, timedelta, timezone
from pathlib import Path

COMPOSE = Path(__file__).with_name("compose.yaml")
ROOT = COMPOSE.parents[3]
PROFILES = {"matrix", "smoke", "baseline", "load", "stress", "spike", "recovery", "soak"}


def compose(*args, **kwargs):
    return subprocess.run(["docker", "compose", "-f", str(COMPOSE), *args], check=True, **kwargs)


def preflight():
    if os.environ.get("LAWDESK_PERF_DEDICATED_HOST") != "verified":
        raise RuntimeError("dedicated host isolation must be verified; shared Contabo is forbidden")
    if os.environ.get("LAWDESK_PERF_UID") != str(os.getuid()):
        raise RuntimeError("LAWDESK_PERF_UID must equal the local user ID")
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
    if os.environ.get("LAWDESK_PERF_SHA") != source:
        raise RuntimeError("LAWDESK_PERF_SHA must equal the exact source HEAD")


def host_guard():
    load = float(Path("/proc/loadavg").read_text().split()[0])
    available_kib = next(int(line.split()[1]) for line in Path("/proc/meminfo").read_text().splitlines()
                         if line.startswith("MemAvailable:"))
    disk = shutil.disk_usage("/")
    try:
        with urllib.request.urlopen("http://127.0.0.1:18080/healthz", timeout=3) as response:
            healthy = response.status == 200
    except (urllib.error.URLError, TimeoutError):
        healthy = False
    return {"load_1m": load, "available_gib": round(available_kib / 1024 / 1024, 2),
            "disk_used_pct": round(100 * disk.used / disk.total, 1), "web_healthy": healthy}


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
    os.environ["LAWDESK_PERF_SCRIPT"] = "export-matrix.js" if profile == "matrix" else "lawdesk-mix.js"
    os.environ["LAWDESK_PERF_PROFILE"] = profile
    preflight()
    evidence = Path(os.environ["LAWDESK_PERF_EVIDENCE_DIR"])
    evidence.mkdir(mode=0o700, parents=True, exist_ok=True)
    dataset_guard(profile, evidence)
    points = evidence / f"{profile}-metrics.jsonl"
    observations = evidence / f"{profile}-guard.jsonl"
    if points.exists() or observations.exists():
        raise RuntimeError("evidence already exists for this profile; use a fresh directory")
    process = subprocess.Popen(["docker", "compose", "-f", str(COMPOSE), "--profile", "load", "run", "--rm", "--no-deps", "k6"])
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
                if state["load_1m"] > 6 or state["available_gib"] < 3 or state["disk_used_pct"] > 85:
                    reason = "host resource stop condition"
                elif not state["web_healthy"]:
                    reason = "web health failed"
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
        raise SystemExit("usage: guarded_run.py matrix|smoke|baseline|load|stress|spike|recovery|soak")
    try:
        raise SystemExit(main(sys.argv[1]))
    except (RuntimeError, KeyError, FileNotFoundError, subprocess.CalledProcessError) as error:
        print("refused: " + str(error), file=sys.stderr)
        raise SystemExit(2) from None
