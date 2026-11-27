#!/usr/bin/env python3
"""Read-only release and QA account probe; never emits credentials or case data."""

import json
import os
import socket
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "journeys"))
from uat import Session  # noqa: E402

HOST = "lawdesk.167-86-90-174.sslip.io"
IP = "167.86.90.174"
original_getaddrinfo = socket.getaddrinfo


def getaddrinfo(host, port, *args, **kwargs):
    # Local DNS is unavailable in the QA executor. Preserve URL host and TLS SNI.
    if host == HOST:
        host = IP
    return original_getaddrinfo(host, port, *args, **kwargs)


socket.getaddrinfo = getaddrinfo


def main():
    result = {
        "observedAtUtc": datetime.now(timezone.utc).isoformat(),
        "base": f"https://{HOST}",
        "accounts": {},
    }
    for account in ("manager", "staff", "viewer", "admin"):
        try:
            session = Session(account).login(account)
            status, release = session.call("GET", "/api/v1/release")
            result["accounts"][account] = {
                "login": 200,
                "roles": session.me.get("roles", []),
                "mustChangePassword": session.me.get("mustChangePassword"),
                "releaseStatus": status,
                "release": release if status == 200 else None,
            }
        except SystemExit as exc:
            # uat.Session reports status only; no secret is present in the exception.
            result["accounts"][account] = {"login": str(exc).split("answered ")[-1]}
        except Exception as exc:
            result["accounts"][account] = {"error": type(exc).__name__}
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if output:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    else:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["accounts"].get("manager", {}).get("releaseStatus") == 200 else 2


if __name__ == "__main__":
    raise SystemExit(main())
