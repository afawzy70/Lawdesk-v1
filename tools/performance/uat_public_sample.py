#!/usr/bin/env python3
"""Sequential, anonymous UAT transfer sample. No login, writes, or load generation."""
import json
import re
import statistics
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

BASE = "https://lawdesk.167-86-90-174.sslip.io"
MAX_REQUESTS = 30
MIN_INTERVAL = 1.0


def percentile(values, fraction):
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = int(position)
    return round(ordered[lower] + (ordered[min(lower + 1, len(ordered) - 1)] - ordered[lower])
                 * (position - lower), 2)


def main():
    rows = []
    last = None
    run_started = time.perf_counter()

    def request(path, warmup=False):
        nonlocal last
        if len(rows) >= MAX_REQUESTS:
            raise RuntimeError("request budget exhausted")
        if last is not None:
            time.sleep(max(0, MIN_INTERVAL - (time.monotonic() - last)))
        last = time.monotonic()
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(urllib.request.Request(BASE + path, headers={"User-Agent": "LawDesk-Phase8-low-impact-sample"}), timeout=12) as reply:
                status, body = reply.status, reply.read()
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read()
        elapsed = (time.perf_counter() - started) * 1000
        rows.append({"path": path, "status": status, "duration_ms": round(elapsed, 2),
                     "response_bytes": len(body), "warmup": warmup})
        return body

    html = request("/", warmup=True).decode("utf-8", errors="replace")
    assets = re.findall(r'(?:src|href)="(/assets/[^\"]+)"', html)
    paths = ["/"] + list(dict.fromkeys(assets))[:2]
    for path in paths:
        request(path, warmup=True)
    for _ in range(8):
        for path in paths:
            request(path)
    wall_seconds = time.perf_counter() - run_started
    result = {"observed_at_utc": datetime.now(timezone.utc).isoformat(), "scope": "anonymous public transfer only",
              "request_budget": MAX_REQUESTS, "minimum_interval_s": MIN_INTERVAL, "rows": rows, "summary": {}}
    result["wall_seconds"] = round(wall_seconds, 3)
    result["paced_rps_including_warmup"] = round(len(rows) / wall_seconds, 3)
    for path in paths:
        samples = [r for r in rows if r["path"] == path and not r["warmup"]]
        times = [r["duration_ms"] for r in samples]
        result["summary"][path] = {"n": len(samples), "statuses": sorted(set(r["status"] for r in samples)),
                                   "p50_ms": percentile(times, .50), "p95_ms": percentile(times, .95),
                                   "p99_ms": percentile(times, .99), "mean_ms": round(statistics.mean(times), 2),
                                   "response_bytes": samples[0]["response_bytes"]}
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
