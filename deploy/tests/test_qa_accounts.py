"""Dedicated QA accounts on UAT (qa_accounts.py) against a fake LawDesk API: provisioning through the public user
administration API only, a pipe-only credential handoff, owner-only files and no secret in any output."""

import http.server
import json
import os
import pty
import secrets
import stat
import subprocess
import sys
import tempfile
import threading
import unittest
import uuid
from http.cookies import SimpleCookie
from pathlib import Path
from urllib.parse import parse_qs, urlparse

SCRIPT = Path(__file__).resolve().parents[1] / "qa" / "qa_accounts.py"

ALL = ["USER_VIEW", "USER_MANAGE", "ROLE_VIEW", "ROLE_MANAGE", "AUDIT_VIEW", "CASE_VIEW", "CASE_CREATE", "CASE_EDIT",
       "CASE_DELETE", "CASE_STATUS_CHANGE", "PARTY_MANAGE", "COURT_VIEW", "COURT_MANAGE", "PROCEEDING_VIEW",
       "PROCEEDING_MANAGE", "JUDGMENT_VIEW", "JUDGMENT_MANAGE"]
MANAGER = ["CASE_VIEW", "CASE_CREATE", "CASE_EDIT", "CASE_DELETE", "CASE_STATUS_CHANGE", "PARTY_MANAGE", "COURT_VIEW",
           "COURT_MANAGE", "PROCEEDING_VIEW", "PROCEEDING_MANAGE", "JUDGMENT_VIEW", "JUDGMENT_MANAGE", "USER_VIEW",
           "AUDIT_VIEW"]
STAFF = ["CASE_VIEW", "CASE_CREATE", "CASE_EDIT", "PARTY_MANAGE", "COURT_VIEW", "PROCEEDING_VIEW",
         "PROCEEDING_MANAGE", "JUDGMENT_VIEW"]


class FakeLawdesk:
    """The parts of the LawDesk API the tool uses, with sessions, CSRF and the forced first password change."""

    def __init__(self, admin_password, admin_must_change=False):
        self.roles = {code: {"id": str(uuid.uuid4()), "code": code, "name": code.title(), "system": True,
                             "permissions": sorted(perms), "version": 0}
                      for code, perms in (("ADMIN", ALL), ("LEGAL_MANAGER", MANAGER), ("LEGAL_STAFF", STAFF))}
        self.users = {}
        self.sessions = {}
        self.requests = []
        self.malformed_users = False
        self.add_user("admin", admin_password, ["ADMIN"], admin_must_change)
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), self.handler())
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.url = "http://127.0.0.1:%d" % self.server.server_port

    def close(self):
        self.server.shutdown()
        self.server.server_close()

    def add_user(self, username, password, roles, must_change):
        user = {"id": str(uuid.uuid4()), "username": username, "email": username + "@example.invalid",
                "displayName": username, "locale": "en", "enabled": True, "roles": roles,
                "password": password, "mustChangePassword": must_change}
        self.users[user["id"]] = user
        return user

    def by_name(self, username):
        return next((u for u in self.users.values() if u["username"] == username), None)

    def permissions(self, user):
        return sorted({p for r in user["roles"] for p in self.roles[r]["permissions"]})

    def me(self, user):
        return {"id": user["id"], "username": user["username"], "roles": sorted(user["roles"]),
                "permissions": self.permissions(user), "mustChangePassword": user["mustChangePassword"]}

    def view(self, user):
        return {k: user[k] for k in ("id", "username", "email", "displayName", "locale", "enabled", "roles")}

    def handler(self):
        fake = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                self.route("GET")

            def do_POST(self):
                self.route("POST")

            def do_PUT(self):
                self.route("PUT")

            def reply(self, status, body=None, cookies=()):
                self.send_response(status)
                for cookie in cookies:
                    self.send_header("Set-Cookie", cookie)
                data = b"" if body is None else json.dumps(body).encode()
                if body is not None:
                    self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def route(self, method):
                url = urlparse(self.path)
                path = url.path
                length = int(self.headers.get("Content-Length") or 0)
                body = json.loads(self.rfile.read(length)) if length else None
                fake.requests.append((method, path, body))
                jar = SimpleCookie(self.headers.get("Cookie", ""))
                if method == "GET" and path == "/api/v1/auth/csrf":
                    return self.reply(204, cookies=["XSRF-TOKEN=%s; Path=/" % secrets.token_hex(8)])
                if method != "GET" and ("XSRF-TOKEN" not in jar
                                        or self.headers.get("X-XSRF-TOKEN") != jar["XSRF-TOKEN"].value):
                    return self.reply(403, {"title": "Forbidden"})
                if method == "POST" and path == "/api/v1/auth/login":
                    user = fake.by_name(body["login"])
                    if user is None or not user["enabled"] or user["password"] != body["password"]:
                        return self.reply(401, {"title": "Unauthorized"})
                    sid = secrets.token_hex(8)
                    fake.sessions[sid] = user["id"]
                    # Like Spring Security, a sign-in invalidates the CSRF token; clients must fetch a new one.
                    return self.reply(200, fake.me(user), ["JSESSIONID=%s; Path=/; HttpOnly" % sid,
                                                           "XSRF-TOKEN=; Path=/; Max-Age=0"])
                sid = jar["JSESSIONID"].value if "JSESSIONID" in jar else None
                user = fake.users.get(fake.sessions.get(sid))
                if user is None:
                    return self.reply(401, {"title": "Unauthorized"})
                if method == "POST" and path == "/api/v1/auth/logout":
                    fake.sessions.pop(sid, None)
                    return self.reply(204)
                if path == "/api/v1/me" and method == "GET":
                    return self.reply(200, fake.me(user))
                if path == "/api/v1/me/password" and method == "POST":
                    if body.get("currentPassword") != user["password"]:
                        return self.reply(400, {"errors": [{"field": "currentPassword"}]})
                    new = body.get("newPassword") or ""
                    if len(new) < 12 or len(new.encode()) > 72 or new.lower() == user["username"]:
                        return self.reply(400, {"errors": [{"field": "newPassword", "code": "PasswordPolicy"}]})
                    user["password"], user["mustChangePassword"] = new, False
                    return self.reply(204)
                if user["mustChangePassword"]:
                    return self.reply(403, {"rule": "PASSWORD_CHANGE_REQUIRED"})
                perms = fake.permissions(user)

                def need(permission):
                    if permission not in perms:
                        self.reply(403, {"title": "Forbidden"})
                        return False
                    return True

                if path == "/api/v1/cases" and method == "GET":
                    return need("CASE_VIEW") and self.reply(200, {"items": [], "totalElements": 0})
                if path == "/api/v1/roles" and method == "GET":
                    return need("ROLE_VIEW") and self.reply(200, list(fake.roles.values()))
                if path == "/api/v1/roles" and method == "POST":
                    if not need("ROLE_MANAGE"):
                        return None
                    if body["code"] in fake.roles:
                        return self.reply(409, {"title": "Conflict"})
                    role = {"id": str(uuid.uuid4()), "code": body["code"], "name": body["name"], "system": False,
                            "permissions": sorted(body["permissions"]), "version": 0}
                    fake.roles[role["code"]] = role
                    return self.reply(201, role)
                if path == "/api/v1/users" and method == "GET":
                    if not need("USER_VIEW"):
                        return None
                    if fake.malformed_users:
                        return self.reply(200, {"unexpected": True})
                    q = parse_qs(url.query).get("q", [""])[0]
                    return self.reply(200, {"items": [fake.view(u) for u in fake.users.values()
                                                      if q in u["username"]]})
                if path == "/api/v1/users" and method == "POST":
                    if not need("USER_MANAGE"):
                        return None
                    if fake.by_name(body["username"]):
                        return self.reply(409, {"title": "Conflict"})
                    created = fake.add_user(body["username"], body["temporaryPassword"], body["roles"], True)
                    created["email"], created["displayName"] = body["email"], body["displayName"]
                    return self.reply(201, fake.view(created))
                parts = path.split("/")
                if len(parts) == 6 and parts[:4] == ["", "api", "v1", "users"] and parts[4] in fake.users:
                    if not need("USER_MANAGE"):
                        return None
                    target = fake.users[parts[4]]
                    if method == "PUT" and parts[5] == "roles":
                        target["roles"] = body["roles"]
                        return self.reply(200, fake.view(target))
                    if method == "PUT" and parts[5] == "enabled":
                        target["enabled"] = body["enabled"]
                        return self.reply(200, fake.view(target))
                    if method == "POST" and parts[5] == "password-reset":
                        target["password"], target["mustChangePassword"] = body["temporaryPassword"], True
                        return self.reply(204)
                return self.reply(404, {"title": "Not Found"})

        return Handler


ADMIN_PASSWORD = "owner-admin-" + secrets.token_hex(12)


class QaAccountsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.fake = FakeLawdesk(ADMIN_PASSWORD)
        self.admin_file = self.root / "admin_password.txt"
        self.admin_file.write_text(ADMIN_PASSWORD + "\n", encoding="utf-8")
        self.admin_file.chmod(0o600)
        self.target = self.root / "qa" / "lawdesk"

    def tearDown(self):
        self.fake.close()
        self.tmp.cleanup()

    def run_tool(self, *args, stdin=None, stdout=subprocess.PIPE):
        return subprocess.run([sys.executable, "-I", str(SCRIPT), *args], input=stdin, stdout=stdout,
                              stderr=subprocess.PIPE, timeout=60, check=False)

    def provision(self, *extra):
        return self.run_tool("provision", "--base-url", self.fake.url, "--admin-password-file", str(self.admin_file),
                             *extra)

    def receive(self, bundle):
        return self.run_tool("receive", "--dir", str(self.target), stdin=bundle)

    def assert_no_secret(self, text, passwords):
        for password in passwords:
            self.assertNotIn(password, text)

    def test_provisions_four_dedicated_accounts_with_completed_password_setup(self):
        result = self.provision()

        self.assertEqual(result.returncode, 0, result.stderr.decode())
        bundle = json.loads(result.stdout)
        accounts = bundle["accounts"]
        self.assertEqual(set(accounts), {"admin", "manager", "staff", "viewer"})
        self.assertEqual({a["username"] for a in accounts.values()}, {"qa.admin", "qa.manager", "qa.staff", "qa.viewer"})
        passwords = [a["password"] for a in accounts.values()]
        self.assertEqual(len(set(passwords)), 4)
        for password in passwords:
            self.assertGreaterEqual(len(password), 32)
            self.assertLessEqual(len(password.encode()), 72)
            self.assertNotEqual(password, ADMIN_PASSWORD)
        roles = {"qa.admin": ["ADMIN"], "qa.manager": ["LEGAL_MANAGER"], "qa.staff": ["LEGAL_STAFF"],
                 "qa.viewer": ["QA_READ_ONLY"]}
        for account in accounts.values():
            user = self.fake.by_name(account["username"])
            self.assertEqual(user["password"], account["password"])
            self.assertFalse(user["mustChangePassword"])
            self.assertEqual(user["roles"], roles[account["username"]])
            self.assertTrue(user["email"].endswith("@example.invalid"))
            self.assertIn("QA", user["displayName"])
        self.assertEqual(self.fake.roles["QA_READ_ONLY"]["permissions"],
                         ["CASE_VIEW", "COURT_VIEW", "JUDGMENT_VIEW", "PROCEEDING_VIEW"])
        self.assertFalse(self.fake.roles["QA_READ_ONLY"]["system"])
        self.assertEqual(self.fake.by_name("admin")["password"], ADMIN_PASSWORD)
        self.assert_no_secret(result.stderr.decode(), passwords + [ADMIN_PASSWORD])

    def test_validates_each_role_including_denials_and_reports_only_outcomes(self):
        result = self.provision()

        report = result.stderr.decode()
        self.assertEqual(result.returncode, 0, report)
        for line in ("qa.admin: login OK, password setup complete, GET /api/v1/users 200",
                     "qa.staff: login OK, password setup complete, GET /api/v1/users 403",
                     "qa.viewer: login OK, password setup complete, GET /api/v1/users 403",
                     "qa.viewer: GET /api/v1/cases 200"):
            self.assertIn(line, report)

    def test_only_the_public_api_is_used_and_no_customer_records_are_touched(self):
        self.provision()

        paths = {path for _, path, _ in self.fake.requests}
        self.assertTrue(all(p.startswith(("/api/v1/auth/", "/api/v1/me", "/api/v1/users", "/api/v1/roles"))
                            or p == "/api/v1/cases" for p in paths), paths)
        writes = [(m, p) for m, p, _ in self.fake.requests if m != "GET" and p.startswith("/api/v1/cases")]
        self.assertEqual(writes, [])

    def test_never_writes_credentials_to_a_terminal(self):
        controller, terminal = pty.openpty()
        try:
            result = self.run_tool("provision", "--base-url", self.fake.url, "--admin-password-file",
                                   str(self.admin_file), stdout=terminal)
        finally:
            os.close(terminal)
            os.close(controller)

        self.assertEqual(result.returncode, 2)
        self.assertIn("pipe", result.stderr.decode())
        self.assertIsNone(self.fake.by_name("qa.admin"))

    def test_never_writes_credentials_to_a_regular_file(self):
        captured = self.root / "captured.json"
        with captured.open("wb") as file:
            result = self.run_tool("provision", "--base-url", self.fake.url, "--admin-password-file",
                                   str(self.admin_file), stdout=file)

        self.assertEqual(result.returncode, 2)
        self.assertIn("pipe", result.stderr.decode())
        self.assertEqual(captured.read_bytes(), b"")
        self.assertIsNone(self.fake.by_name("qa.admin"))

    def test_the_operator_hands_over_through_a_named_pipe_owned_by_the_qa_identity(self):
        fifo = self.root / "handoff.fifo"
        os.mkfifo(fifo, 0o620)
        receiver = subprocess.Popen(["sh", "-c", 'exec "$1" -I "$2" receive --dir "$3" < "$4"', "sh", sys.executable,
                                     str(SCRIPT), str(self.target), str(fifo)],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        with fifo.open("wb") as pipe:
            provision = self.run_tool("provision", "--base-url", self.fake.url, "--admin-password-file",
                                      str(self.admin_file), stdout=pipe)
        out, err = receiver.communicate(timeout=60)

        self.assertEqual(provision.returncode, 0, provision.stderr.decode())
        self.assertEqual(receiver.returncode, 0, err.decode())
        self.assertIn("LAWDESK_QA_CREDENTIAL_FILE=%s" % (self.target / "admin.json"), out.decode())
        self.assertEqual(stat.S_IMODE((self.target / "admin.json").stat().st_mode), 0o600)

    def test_passwords_are_only_sent_over_https_except_to_loopback(self):
        for url in ("http://lawdesk.example.invalid", "ftp://127.0.0.1"):
            for args in (("provision", "--base-url", url, "--admin-password-file", str(self.admin_file)),
                         ("verify", "--base-url", url, "--dir", str(self.target))):
                result = self.run_tool(*args)
                self.assertEqual(result.returncode, 2, args)
                self.assertIn("https", result.stderr.decode())
        self.assertEqual(self.fake.requests, [])

    def test_an_unexpected_response_fails_without_a_traceback(self):
        self.fake.malformed_users = True

        result = self.provision()

        self.assertEqual(result.returncode, 4)
        self.assertNotIn("Traceback", result.stderr.decode())
        self.assertIn("unexpected", result.stderr.decode())
        self.assertEqual(result.stdout, b"")

    def test_an_administrator_still_on_the_bootstrap_password_is_left_to_the_owner(self):
        self.fake.close()
        self.fake = FakeLawdesk(ADMIN_PASSWORD, admin_must_change=True)

        result = self.provision()

        self.assertEqual(result.returncode, 3)
        self.assertIn("must change", result.stderr.decode())
        self.assertEqual(result.stdout, b"")
        self.assertEqual(self.fake.by_name("admin")["password"], ADMIN_PASSWORD)
        self.assertEqual([m for m, p, _ in self.fake.requests if m != "GET" and p not in
                          ("/api/v1/auth/login", "/api/v1/auth/logout")], [])

    def test_a_wrong_administrator_password_fails_without_echoing_it(self):
        self.admin_file.write_text("not-the-password-" + secrets.token_hex(6), encoding="utf-8")

        result = self.provision()

        self.assertEqual(result.returncode, 3)
        self.assertIn("administrator sign-in refused", result.stderr.decode())
        self.assert_no_secret(result.stderr.decode(), [self.admin_file.read_text(encoding="utf-8")])
        self.assertEqual(result.stdout, b"")

    def test_rerunning_rotates_existing_qa_accounts_instead_of_duplicating_them(self):
        first = json.loads(self.provision().stdout)["accounts"]
        staff = self.fake.by_name("qa.staff")
        staff["roles"], staff["enabled"] = ["ADMIN"], False

        second = self.provision()

        self.assertEqual(second.returncode, 0, second.stderr.decode())
        accounts = json.loads(second.stdout)["accounts"]
        self.assertEqual(sum(1 for u in self.fake.users.values() if u["username"].startswith("qa.")), 4)
        self.assertEqual(self.fake.by_name("qa.staff")["roles"], ["LEGAL_STAFF"])
        self.assertTrue(self.fake.by_name("qa.staff")["enabled"])
        for key, account in accounts.items():
            self.assertNotEqual(account["password"], first[key]["password"])
        self.assertEqual(sum(1 for code in self.fake.roles if code == "QA_READ_ONLY"), 1)

    def test_a_role_with_unexpected_permissions_fails_validation(self):
        self.fake.roles["LEGAL_STAFF"]["permissions"] = sorted(STAFF + ["USER_VIEW"])

        result = self.provision()

        self.assertEqual(result.returncode, 4)
        self.assertIn("qa.staff: GET /api/v1/users expected 403, got 200", result.stderr.decode())
        self.assertEqual(result.stdout, b"")

    def test_receive_stores_owner_only_files_and_prints_only_paths(self):
        bundle = self.provision().stdout
        passwords = [a["password"] for a in json.loads(bundle)["accounts"].values()]

        result = self.receive(bundle)

        self.assertEqual(result.returncode, 0, result.stderr.decode())
        out = result.stdout.decode()
        self.assert_no_secret(out + result.stderr.decode(), passwords)
        self.assertEqual(stat.S_IMODE(self.target.stat().st_mode), 0o700)
        names = {"admin": "LAWDESK_QA_CREDENTIAL_FILE", "manager": "LAWDESK_QA_MANAGER_CREDENTIAL_FILE",
                 "staff": "LAWDESK_QA_STAFF_CREDENTIAL_FILE", "viewer": "LAWDESK_QA_VIEWER_CREDENTIAL_FILE"}
        for key, variable in names.items():
            path = self.target / ("%s.json" % key)
            self.assertIn("%s=%s" % (variable, path), out)
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertEqual(path.stat().st_uid, os.getuid())
            self.assertEqual(set(json.loads(path.read_text(encoding="utf-8"))), {"username", "password"})
        self.assertEqual(sorted(p.name for p in self.target.iterdir()),
                         ["admin.json", "manager.json", "staff.json", "viewer.json"])

    def test_receive_refuses_a_directory_others_can_read(self):
        self.target.mkdir(parents=True)
        self.target.chmod(0o755)

        result = self.receive(self.provision().stdout)

        self.assertEqual(result.returncode, 2)
        self.assertIn("0700", result.stderr.decode())
        self.assertEqual(list(self.target.iterdir()), [])

    def test_receive_stores_nothing_and_leaves_no_temporary_file_when_a_write_fails(self):
        bundle = self.provision().stdout
        self.target.mkdir(parents=True, mode=0o700)
        (self.target / "staff.json").mkdir()

        result = self.receive(bundle)

        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("Traceback", result.stderr.decode())
        self.assertEqual(sorted(p.name for p in self.target.iterdir()), ["staff.json"])
        self.assertEqual(result.stdout, b"")

    def test_receive_refuses_input_that_is_not_a_bundle(self):
        result = self.receive(b'{"accounts": {"admin": {"username": "x"}}}')

        self.assertEqual(result.returncode, 2)
        self.assertFalse(self.target.exists() and any(self.target.iterdir()))

    def test_verify_signs_in_with_the_stored_files_and_reports_without_secrets(self):
        bundle = self.provision().stdout
        self.receive(bundle)
        passwords = [a["password"] for a in json.loads(bundle)["accounts"].values()]

        result = self.run_tool("verify", "--base-url", self.fake.url, "--dir", str(self.target))

        report = result.stdout.decode() + result.stderr.decode()
        self.assertEqual(result.returncode, 0, report)
        self.assertIn("qa.viewer: login OK, password setup complete, GET /api/v1/users 403", report)
        self.assert_no_secret(report, passwords)

    def test_verify_fails_when_a_file_is_readable_by_others(self):
        self.receive(self.provision().stdout)
        (self.target / "staff.json").chmod(0o644)

        result = self.run_tool("verify", "--base-url", self.fake.url, "--dir", str(self.target))

        self.assertEqual(result.returncode, 2)
        self.assertIn("staff.json", result.stderr.decode())


if __name__ == "__main__":
    unittest.main()
