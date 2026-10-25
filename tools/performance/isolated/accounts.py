#!/usr/bin/env python3
"""Create isolated-only secrets and three synthetic k6 personas without printing passwords."""
import json
import os
import secrets
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "deploy" / "qa"))
import qa_accounts  # noqa: E402

PERSONAS = {"lawyer": ("perf.lawyer", "LEGAL_STAFF"),
            "staff": ("perf.staff", "LEGAL_STAFF"),
            "manager": ("perf.manager", "LEGAL_MANAGER")}


def write_private(path, content):
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    descriptor = os.open(path, flags, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        handle.write(content)


def prepare(directory):
    directory.mkdir(mode=0o700, parents=True, exist_ok=False)
    write_private(directory / ".lawdesk-perf-owned", "phase8-isolated\n")
    for name in ("db_admin_password", "db_app_password", "admin_bootstrap_password"):
        write_private(directory / (name + ".txt"), secrets.token_urlsafe(32) + "\n")
    print("isolated secret directory prepared: " + str(directory))


def provision(directory, base):
    qa_accounts.require_https(base)
    if not qa_accounts.owner_only(directory, 0o700):
        raise RuntimeError("secret directory must be owner-only (0700)")
    output = directory / "perf_accounts.json"
    if output.exists():
        raise RuntimeError("accounts already provisioned; refusing to rotate during a test")
    operational = directory / "admin_operational_password.txt"
    admin_password = (operational if operational.exists() else directory / "admin_bootstrap_password.txt").read_text().strip()
    admin = qa_accounts.Session(base)
    me = admin.login("perf.admin", admin_password)
    if me is None:
        raise RuntimeError("isolated bootstrap administrator sign-in failed")
    if me.get("mustChangePassword"):
        new_password = qa_accounts.new_password()
        admin.expect("POST", "/api/v1/me/password", {"currentPassword": admin_password,
                                                   "newPassword": new_password}, 204)
        write_private(operational, new_password + "\n")
    bundle = {}
    try:
        for persona, (username, role) in PERSONAS.items():
            temporary, final = qa_accounts.new_password(), qa_accounts.new_password()
            qa_accounts.set_temporary_password(admin, username, role, temporary)
            qa_accounts.complete_password_setup(base, username, temporary, final)
            bundle[persona] = {"username": username, "password": final}
    finally:
        admin.logout()
    for persona, (username, _) in PERSONAS.items():
        session = qa_accounts.Session(base)
        me = session.login(username, bundle[persona]["password"])
        if me is None or me.get("mustChangePassword"):
            raise RuntimeError("persona verification failed: " + persona)
        session.logout()
    write_private(output, json.dumps({"accounts": bundle}) + "\n")
    print("synthetic personas verified; private credentials saved: " + str(output))


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4) or sys.argv[1] not in ("prepare", "provision"):
        raise SystemExit("usage: accounts.py prepare DIR | provision DIR http://127.0.0.1:18080")
    directory = Path(sys.argv[2]).resolve()
    if sys.argv[1] == "prepare":
        if len(sys.argv) != 3:
            raise SystemExit(2)
        prepare(directory)
    else:
        if len(sys.argv) != 4:
            raise SystemExit(2)
        provision(directory, sys.argv[3])
