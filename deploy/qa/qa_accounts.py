#!/usr/bin/env python3
"""Dedicated QA accounts on LawDesk UAT, through the public user administration API only.

  provision  run by the operator who holds the UAT administrator password (on the host: `devops`). Creates or rotates
             qa.admin (ADMIN), qa.manager (LEGAL_MANAGER), qa.staff (LEGAL_STAFF) and qa.viewer (QA_READ_ONLY, created
             if absent), completes each forced first password change, checks every role, then writes the credential
             bundle to stdout, which must be a (named) pipe into `receive`. The administrator's own account is never changed.
  receive    run by the QA execution identity; stores one owner-only JSON file per account and prints only the paths.
  verify     run by the QA execution identity; signs in with the stored files and prints only outcomes.

Passwords exist only in memory, the pipe and the 0600 files: never in arguments, logs or the terminal.
Exit codes: 0 done, 2 refused input or unsafe output, 3 administrator not usable, 4 a check failed or an
unexpected response.
"""

import argparse
import getpass
import http.cookiejar
import json
import os
import secrets
import stat
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

READ_ONLY_ROLE = {"code": "QA_READ_ONLY", "name": "QA read-only (synthetic test role)",
                  "permissions": ["CASE_VIEW", "COURT_VIEW", "JUDGMENT_VIEW", "PROCEEDING_VIEW"]}

# key, username, role, whether GET /api/v1/users is allowed (the administration boundary).
ACCOUNTS = (
    ("admin", "qa.admin", "ADMIN", True),
    ("manager", "qa.manager", "LEGAL_MANAGER", True),
    ("staff", "qa.staff", "LEGAL_STAFF", False),
    ("viewer", "qa.viewer", READ_ONLY_ROLE["code"], False),
)

VARIABLES = {"admin": "LAWDESK_QA_CREDENTIAL_FILE", "manager": "LAWDESK_QA_MANAGER_CREDENTIAL_FILE",
             "staff": "LAWDESK_QA_STAFF_CREDENTIAL_FILE", "viewer": "LAWDESK_QA_VIEWER_CREDENTIAL_FILE"}


class Refused(Exception):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


def say(message):
    print(message, file=sys.stderr, flush=True)


def require_https(base_url):
    url = urllib.parse.urlsplit(base_url)
    if not (url.scheme == "https" or (url.scheme == "http" and url.hostname in ("127.0.0.1", "localhost", "::1"))):
        raise Refused(2, "base URL must use https (plain http only to loopback); nothing sent or read")


def new_password():
    # 40 URL-safe characters (240 bits); well inside the 12-character / 72-byte policy.
    return secrets.token_urlsafe(30)


class Session:
    """One signed-in browser-like session: cookies plus the XSRF-TOKEN echo the API expects on writes."""

    def __init__(self, base_url):
        require_https(base_url)
        self.base = base_url.rstrip("/")
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.call("GET", "/api/v1/auth/csrf")

    def call(self, method, path, body=None):
        """Returns (status, parsed body). Response bodies never contain passwords; errors carry only the status."""
        headers = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        if method != "GET":
            token = self.csrf_token()
            if token is None and path != "/api/v1/auth/csrf":
                # Signing in rotates the token, as it does for the SPA; fetch a fresh one.
                self.call("GET", "/api/v1/auth/csrf")
                token = self.csrf_token()
            if token:
                headers["X-XSRF-TOKEN"] = token
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
        try:
            with self.opener.open(request, timeout=30) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as e:
            status, raw = e.code, e.read()
        except urllib.error.URLError as e:
            raise Refused(3, "cannot reach %s (%s)" % (self.base, e.reason)) from None
        try:
            return status, json.loads(raw) if raw else None
        except ValueError:
            return status, None

    def csrf_token(self):
        return next((c.value for c in self.jar if c.name == "XSRF-TOKEN" and c.value), None)

    def expect(self, method, path, body, *statuses):
        status, data = self.call(method, path, body)
        if status not in statuses:
            raise Refused(4, "%s %s returned %d" % (method, path, status))
        return data

    def login(self, username, password):
        status, me = self.call("POST", "/api/v1/auth/login", {"login": username, "password": password})
        return me if status == 200 else None

    def logout(self):
        self.call("POST", "/api/v1/auth/logout")


def admin_session(base_url, username, password):
    session = Session(base_url)
    me = session.login(username, password)
    if me is None:
        raise Refused(3, "administrator sign-in refused for %s" % username)
    if me.get("mustChangePassword"):
        session.logout()
        raise Refused(3, "%s must change the bootstrap password first: sign in once in the browser, change it, "
                         "then rerun with the new password at the prompt" % username)
    if not {"USER_MANAGE", "ROLE_MANAGE", "ROLE_VIEW", "USER_VIEW"} <= set(me.get("permissions", [])):
        session.logout()
        raise Refused(3, "%s lacks user and role administration permissions" % username)
    return session


def ensure_read_only_role(admin):
    roles = {r["code"]: r for r in admin.expect("GET", "/api/v1/roles", None, 200)}
    if READ_ONLY_ROLE["code"] not in roles:
        admin.expect("POST", "/api/v1/roles", READ_ONLY_ROLE, 201)
        say("created role %s" % READ_ONLY_ROLE["code"])
        roles = {r["code"]: r for r in admin.expect("GET", "/api/v1/roles", None, 200)}
    return {code: sorted(r["permissions"]) for code, r in roles.items()}


def find_user(admin, username):
    page = admin.expect("GET", "/api/v1/users?" + urllib.parse.urlencode({"q": username, "size": 50}), None, 200)
    return next((u for u in page["items"] if u["username"] == username), None)


def set_temporary_password(admin, username, role, temporary):
    """Creates the QA user, or brings an existing one back to its role, enabled, with a new temporary password."""
    existing = find_user(admin, username)
    if existing is None:
        admin.expect("POST", "/api/v1/users", {
            "username": username, "email": username + "@example.invalid",
            "displayName": "QA %s (synthetic test account)" % username.split(".", 1)[1], "locale": "en",
            "roles": [role], "temporaryPassword": temporary}, 201)
        say("%s: created with role %s" % (username, role))
        return
    base = "/api/v1/users/" + existing["id"]
    admin.expect("PUT", base + "/roles", {"roles": [role]}, 200)
    admin.expect("PUT", base + "/enabled", {"enabled": True}, 200)
    admin.expect("POST", base + "/password-reset", {"temporaryPassword": temporary}, 204)
    say("%s: existing account reset to role %s and rotated" % (username, role))


def complete_password_setup(base_url, username, temporary, final):
    session = Session(base_url)
    me = session.login(username, temporary)
    if me is None or not me.get("mustChangePassword"):
        raise Refused(4, "%s: the temporary password did not start a forced password change" % username)
    session.expect("POST", "/api/v1/me/password", {"currentPassword": temporary, "newPassword": final}, 204)
    session.logout()


def check_account(base_url, username, password, role_permissions, admin_allowed):
    """Signs in, compares the effective permissions with the role and probes the administration boundary."""
    session = Session(base_url)
    me = session.login(username, password)
    if me is None:
        raise Refused(4, "%s: sign-in refused" % username)
    try:
        if me.get("mustChangePassword"):
            raise Refused(4, "%s: password setup is not complete" % username)
        if role_permissions is not None and sorted(me.get("permissions", [])) != role_permissions:
            raise Refused(4, "%s: effective permissions differ from its role" % username)
        users, _ = session.call("GET", "/api/v1/users?size=1")
        expected = 200 if admin_allowed else 403
        if users != expected:
            raise Refused(4, "%s: GET /api/v1/users expected %d, got %d" % (username, expected, users))
        say("%s: login OK, password setup complete, GET /api/v1/users %d" % (username, users))
        if "CASE_VIEW" in me.get("permissions", []):
            cases, _ = session.call("GET", "/api/v1/cases?size=1")
            if cases != 200:
                raise Refused(4, "%s: GET /api/v1/cases expected 200, got %d" % (username, cases))
            say("%s: GET /api/v1/cases 200" % username)
    finally:
        session.logout()


def read_admin_password(path):
    if path:
        return Path(path).read_text(encoding="utf-8").strip()
    return getpass.getpass("UAT administrator password (not echoed): ")


def provision(args):
    require_https(args.base_url)
    if not stat.S_ISFIFO(os.fstat(sys.stdout.fileno()).st_mode):
        # A terminal, a file or a CI log would keep the passwords; only a pipe into `receive` is accepted.
        raise Refused(2, "stdout must be a pipe (or named pipe) into `qa_accounts.py receive`; nothing done")
    admin = admin_session(args.base_url, args.admin_user.strip().lower(), read_admin_password(args.admin_password_file))
    try:
        permissions = ensure_read_only_role(admin)
        bundle = {}
        for key, username, role, _ in ACCOUNTS:
            temporary, final = new_password(), new_password()
            set_temporary_password(admin, username, role, temporary)
            complete_password_setup(args.base_url, username, temporary, final)
            bundle[key] = {"username": username, "password": final}
    finally:
        admin.logout()
    for key, username, role, admin_allowed in ACCOUNTS:
        check_account(args.base_url, username, bundle[key]["password"], permissions.get(role), admin_allowed)
    json.dump({"version": 1, "baseUrl": args.base_url, "accounts": bundle}, sys.stdout)
    sys.stdout.flush()


def owner_only(path, mode):
    info = path.stat()
    return info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == mode


def receive(args):
    try:
        accounts = json.load(sys.stdin)["accounts"]
        valid = set(accounts) == set(VARIABLES) and all(
            set(a) == {"username", "password"} and all(isinstance(v, str) and v for v in a.values())
            for a in accounts.values())
    except (ValueError, KeyError, TypeError, AttributeError):
        valid = False
    if not valid:
        raise Refused(2, "stdin is not a credential bundle from `qa_accounts.py provision`; nothing stored")
    target = Path(args.dir)
    os.umask(0o077)
    target.mkdir(mode=0o700, parents=True, exist_ok=True)
    if not owner_only(target, 0o700):
        raise Refused(2, "%s must be owned by this user with mode 0700; nothing stored" % target)
    paths = {key: target / ("%s.json" % key) for key in VARIABLES}
    for path in paths.values():
        if path.is_symlink() or (path.exists() and not (path.is_file() and owner_only(path, 0o600))):
            raise Refused(2, "%s is not an owner-only regular file; nothing stored" % path)
    temporaries = {}
    try:
        for key in VARIABLES:
            handle, temporaries[key] = tempfile.mkstemp(dir=target, prefix=".%s." % key)
            with os.fdopen(handle, "w", encoding="utf-8") as file:
                json.dump(accounts[key], file)
        for key in VARIABLES:
            os.replace(temporaries.pop(key), paths[key])
    finally:
        for temporary in temporaries.values():
            os.unlink(temporary)
    for key, variable in VARIABLES.items():
        print("%s=%s" % (variable, paths[key]))


def verify(args):
    require_https(args.base_url)
    target = Path(args.dir)
    if not owner_only(target, 0o700):
        raise Refused(2, "%s must be owned by this user with mode 0700" % target)
    stored = {}
    for key in VARIABLES:
        path = target / ("%s.json" % key)
        if not owner_only(path, 0o600):
            raise Refused(2, "%s must be owned by this user with mode 0600" % path)
        stored[key] = json.loads(path.read_text(encoding="utf-8"))
    for key, username, _, admin_allowed in ACCOUNTS:
        if stored[key]["username"] != username:
            raise Refused(4, "%s.json names an unexpected account" % key)
        check_account(args.base_url, username, stored[key]["password"], None, admin_allowed)
    say("all QA accounts verified")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    p = commands.add_parser("provision")
    p.add_argument("--base-url", required=True)
    p.add_argument("--admin-user", default="admin")
    p.add_argument("--admin-password-file", help="file holding the administrator password; prompts if omitted")
    p.set_defaults(run=provision)
    for name, run in (("receive", receive), ("verify", verify)):
        p = commands.add_parser(name)
        p.add_argument("--dir", required=True)
        if name == "verify":
            p.add_argument("--base-url", required=True)
        p.set_defaults(run=run)
    args = parser.parse_args()
    try:
        args.run(args)
    except Refused as e:
        say("qa_accounts: " + str(e))
        return e.code
    except Exception as e:  # pylint: disable=broad-except
        # No traceback: it could quote request data. The type is enough to diagnose; nothing was released.
        say("qa_accounts: stopped on an unexpected %s; no credentials were released" % type(e).__name__)
        return 4
    return 0


if __name__ == "__main__":
    sys.exit(main())
