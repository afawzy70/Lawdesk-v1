#!/usr/bin/env python3
"""Read-only diagnosis of the handed-over qa.admin account; no secret values."""

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "tools" / "journeys"))
from uat import Session  # noqa: E402


def main():
    manager = Session("manager").login("manager")
    before = manager.call("GET", "/api/v1/release")[1]["release"]
    status, body = manager.call("GET", "/api/v1/users?size=10&q=qa.admin")
    users = [u for u in body.get("items", []) if u.get("username") == "qa.admin"] if status == 200 else []
    allowed = ("username", "enabled", "locked", "mustChangePassword", "roles", "version", "updatedAt")
    after = manager.call("GET", "/api/v1/release")[1]["release"]
    result = {"observedAtUtc": datetime.now(timezone.utc).isoformat(),
              "applicationShaBefore": before, "applicationShaAfter": after,
              "listStatus": status, "account": [{k: u.get(k) for k in allowed} for u in users]}
    (HERE / "admin-diagnostic.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False))
    return 0 if before == after else 2


if __name__ == "__main__":
    raise SystemExit(main())
