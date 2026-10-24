#!/usr/bin/env python3
"""LD-040 UAT latency SMOKE SAMPLE (not a load test).

Hard limits, enforced in code: read endpoints only (GET), strictly sequential, at most 1 request per second, at most
60 requests in total including sign-in, CSRF and sign-out; the read-only QA account; the credential file is read
in-process through deploy/qa/qa_accounts.py's Session and never printed. Prints per-endpoint status counts and
latency percentiles only, never response bodies, cookies or tokens.

Usage: uat_smoke_sample.py <base-url> <viewer-credential-file> [request-budget, default and max 60]

Latency is timed around the HTTP exchange only, after the pacing wait (a first run on 2026-10-24 timed the wait too;
see docs/performance/PERFORMANCE-BASELINE.md §5).
"""

import json
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "deploy" / "qa"))

import qa_accounts  # noqa: E402

MAX_REQUESTS = 60
MIN_INTERVAL_S = 1.0


class Budget:
    """Spaces requests at least MIN_INTERVAL_S apart and refuses any beyond the limit (never more than 60)."""

    def __init__(self, limit=MAX_REQUESTS):
        self.limit = min(MAX_REQUESTS, limit)
        self.sent = 0
        self.last = None

    def take(self):
        if self.sent >= self.limit:
            raise RuntimeError("request budget exhausted")
        if self.last is not None:
            wait = MIN_INTERVAL_S - (time.monotonic() - self.last)
            if wait > 0:
                time.sleep(wait)
        self.last = time.monotonic()
        self.sent += 1


def percentile(values, p):
    ordered = sorted(values)
    if not ordered:
        return None
    k = max(0, min(len(ordered) - 1, round(p / 100 * len(ordered) + 0.5) - 1))
    return ordered[k]


def main(argv):
    if len(argv) not in (2, 3):
        print(__doc__, file=sys.stderr)
        return 2
    base, credential_file = argv[0], Path(argv[1])
    budget = Budget(int(argv[2]) if len(argv) == 3 else MAX_REQUESTS)
    results = {}

    class Metered(qa_accounts.Session):
        def call(self, method, path, body=None):
            if method != "GET" and path not in ("/api/v1/auth/login", "/api/v1/auth/logout"):
                raise RuntimeError("only reads, sign-in and sign-out are allowed")
            budget.take()
            started = time.perf_counter()
            answer = super().call(method, path, body)
            self.last_ms = (time.perf_counter() - started) * 1000
            return answer

    def measured(path, label):
        status, data = session.call("GET", path)
        results.setdefault(label, []).append((status, session.last_ms))
        return status, data

    account = json.loads(credential_file.read_text(encoding="utf-8"))
    session = Metered(base)
    if session.login(account["username"], account["password"]) is None:
        print("sign-in refused; nothing measured", file=sys.stderr)
        return 1
    del account

    reserve = 2  # sign-out needs a fresh CSRF token, then the sign-out itself
    status, page = measured("/api/v1/cases?size=20", "/api/v1/cases?size=20")
    items = (page or {}).get("items") or [] if status == 200 else []
    case_id = items[0].get("id") if items else None
    endpoints = ["/api/v1/me", "/api/v1/cases?size=20", "/api/v1/courts"]
    if case_id:
        endpoints += ["/api/v1/cases/%s" % case_id, "/api/v1/cases/%s/proceedings" % case_id]
    turn = 0
    while budget.sent + reserve < budget.limit:
        path = endpoints[turn % len(endpoints)]
        measured(path, path.replace(case_id, "{id}") if case_id else path)
        turn += 1
    session.logout()

    print("UAT smoke sample (sequential, <= 1 req/s): %d requests in total, base %s" % (budget.sent, base))
    print("| Endpoint | n | statuses | p50 ms | p95 ms | max ms | mean ms |")
    print("|---|---|---|---|---|---|---|")
    for label, rows in results.items():
        times = [t for _, t in rows]
        statuses = {}
        for s, _ in rows:
            statuses[s] = statuses.get(s, 0) + 1
        print("| `GET %s` | %d | %s | %.0f | %.0f | %.0f | %.0f |" % (
            label, len(rows), ", ".join("%d x%d" % kv for kv in sorted(statuses.items())), percentile(times, 50),
            percentile(times, 95), max(times), statistics.fmean(times)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
