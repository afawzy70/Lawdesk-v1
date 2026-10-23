"""Smoke-test gate (QAD-012, QA-0046/QA-0048): every failed check must fail the script.

The fake `docker` runs the real inner shell of smoke.sh locally; a fake busybox-style `wget` answers from a scenario.
"""

import json
import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

SMOKE = Path(__file__).resolve().parents[1] / "scripts" / "smoke.sh"

FAKE_DOCKER = textwrap.dedent("""\
    #!/usr/bin/env python3
    import os, subprocess, sys
    args = sys.argv[1:]
    if "exec" in args and "-euc" in args:
        sys.exit(subprocess.run(["sh", "-euc", args[-1]], env=os.environ).returncode)
    sys.exit(0)
    """)

# Mimics busybox wget: -S prints response headers on stderr; a non-2xx status prints
# "wget: server returned error: HTTP/1.1 <code> ..." and exits 1; "-O -" writes the body to stdout.
FAKE_WGET = textwrap.dedent("""\
    #!/usr/bin/env python3
    import json, os, sys
    args = sys.argv[1:]
    path = "/" + args[-1].split("/", 3)[3]
    status, body = json.loads(os.environ["FAKE_RESPONSES"]).get(path, [None, ""])
    if status is None:
        print("wget: can't connect to remote host", file=sys.stderr)
        sys.exit(1)
    if "-S" in args:
        print(f"  HTTP/1.1 {status} X", file=sys.stderr)
    if not 200 <= status < 300:
        print(f"wget: server returned error: HTTP/1.1 {status} X", file=sys.stderr)
        sys.exit(1)
    if args[args.index("-O") + 1] == "-":
        sys.stdout.write(body)
    sys.exit(0)
    """)

HEALTHY = {
    "/healthz": [200, "ok"],
    "/": [200, "<!doctype html>"],
    "/actuator/health/readiness": [200, '{"status":"UP"}'],
    "/api/v1/cases": [401, '{"status":401}'],
    "/actuator/env": [404, ""],
}


class SmokeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        bin_dir = Path(self.tmp.name)
        for name, code in [("docker", FAKE_DOCKER), ("wget", FAKE_WGET)]:
            path = bin_dir / name
            path.write_text(code, encoding="utf-8")
            path.chmod(path.stat().st_mode | stat.S_IEXEC)
        self.env = {"PATH": f"{bin_dir}:/usr/bin:/bin", "HOME": self.tmp.name,
                    "LAWDESK_REGISTRY": "lawdesk-test", "LAWDESK_TAG": "a" * 40}

    def tearDown(self):
        self.tmp.cleanup()

    def smoke(self, **changes):
        responses = {**HEALTHY, **changes}
        return subprocess.run(["bash", str(SMOKE)], env={**self.env, "FAKE_RESPONSES": json.dumps(responses)},
                              capture_output=True, text=True, check=False, timeout=30)

    def test_healthy_stack_passes_every_check(self):
        result = self.smoke()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(5, result.stdout.count("ok   "), result.stdout)

    def test_each_failed_check_fails_the_smoke_test(self):
        scenarios = {
            "web health down": {"/healthz": [503, ""]},
            "spa missing": {"/": [404, ""]},
            "readiness DOWN with 200": {"/actuator/health/readiness": [200, '{"status":"DOWN"}']},
            "readiness 503": {"/actuator/health/readiness": [503, '{"status":"DOWN"}']},
            "readiness unreachable": {"/actuator/health/readiness": [None, ""]},
            "api open to anonymous": {"/api/v1/cases": [200, "[]"]},
            "api forbidden instead of 401": {"/api/v1/cases": [403, ""]},
            "api unreachable": {"/api/v1/cases": [None, ""]},
            "actuator env exposed": {"/actuator/env": [200, "{}"]},
            "actuator env unreachable": {"/actuator/env": [None, ""]},
        }
        for name, change in scenarios.items():
            with self.subTest(scenario=name):
                result = self.smoke(**change)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertIn("FAIL", result.stderr)

    def test_failure_names_the_check_and_the_actual_status(self):
        result = self.smoke(**{"/api/v1/cases": [200, "[]"]})

        self.assertIn("/api/v1/cases -> 200, expected 401", result.stderr)


if __name__ == "__main__":
    unittest.main()
