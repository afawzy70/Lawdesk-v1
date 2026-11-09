"""A small client for the UAT journeys (docs/design/LD-DOC-06): real HTTP against the deployed application, signed in
with the QA accounts handed over through deploy/qa/qa_accounts.py. Credentials are read from the owner-only files
named by LAWDESK_QA_DIR (default /srv/ai-workspaces/secrets/lawdesk-qa) and are never printed or stored. Every record a
journey creates carries the marker SYNTH so it is plainly test data; journeys never edit existing cases."""

import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("LAWDESK_BASE", "https://lawdesk.167-86-90-174.sslip.io")
QA_DIR = os.environ.get("LAWDESK_QA_DIR", "/srv/ai-workspaces/secrets/lawdesk-qa")
MARK = "SYNTH-DOC"


class Session:
    def __init__(self, name):
        self.name = name
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.me = None

    def _xsrf(self):
        return next((c.value for c in self.jar if c.name == "XSRF-TOKEN"), "")

    def call(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode()
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if method != "GET":
            headers["X-XSRF-TOKEN"] = self._xsrf()
        path = urllib.parse.quote(path, safe="/?&=%:,+")
        request = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with self.opener.open(request, timeout=60) as response:
                raw = response.read()
                try:
                    return response.status, (json.loads(raw) if raw else None)
                except ValueError:
                    return response.status, raw  # a file download
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                return error.code, json.loads(raw)
            except ValueError:
                return error.code, None

    def login(self, account):
        credential = json.load(open(os.path.join(QA_DIR, account + ".json")))
        self.opener.open(BASE + "/api/v1/auth/csrf")
        status, body = self.call("POST", "/api/v1/auth/login",
                                 {"login": credential["username"], "password": credential["password"]})
        if status != 200:
            raise SystemExit("%s: sign-in answered %d" % (account, status))
        self.me = body
        return self


class Journey:
    """Records each step with what was asked and what the application answered; fails the run on a wrong answer."""

    def __init__(self, title):
        self.title = title
        self.steps = []
        self.failed = False

    def check(self, label, actual, expected):
        ok = actual == expected
        self.steps.append({"step": label, "expected": expected, "actual": actual, "ok": ok})
        print(("PASS " if ok else "FAIL ") + label + ("" if ok else "  expected %r got %r" % (expected, actual)))
        self.failed = self.failed or not ok
        return ok

    def note(self, label, value):
        self.steps.append({"step": label, "value": value})
        print("NOTE " + label + ": " + str(value))

    def finish(self, path):
        with open(path, "w", encoding="utf-8") as out:
            json.dump({"journey": self.title, "base": BASE, "steps": self.steps, "passed": not self.failed}, out,
                      ensure_ascii=False, indent=2, default=str)
        print("%s: %s" % (self.title, "FAILED" if self.failed else "passed"))
        if self.failed:
            sys.exit(1)
