"""LD-006 deploy and rollback behaviour, driven through a fake `docker` that records every call.

Covers QA-0042/QA-0043 (input validation), QA-0046 (failed deploy rolls back), QA-0047 (one deployment at a time).
"""

import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

DEPLOY_DIR = Path(__file__).resolve().parents[1]
DEPLOY = DEPLOY_DIR / "scripts" / "deploy.sh"
ROLLBACK = DEPLOY_DIR / "scripts" / "rollback.sh"

SHA_A = "a" * 40
SHA_B = "b" * 40
SHA_C = "c" * 40

FAKE_DOCKER = textwrap.dedent("""\
    #!/bin/sh
    # Records each call with the release tag it saw; fails on demand.
    echo "LAWDESK_TAG=${LAWDESK_TAG:-} $*" | tr '\n' ' ' >> "$FAKE_DOCKER_LOG"
    echo >> "$FAKE_DOCKER_LOG"
    case "$*" in
      *" up "*) [ "${LAWDESK_TAG:-}" = "${FAKE_FAIL_UP_TAG:-none}" ] && exit 1 ;;
      *" exec "*) [ "${LAWDESK_TAG:-}" = "${FAKE_FAIL_SMOKE_TAG:-none}" ] && exit 1 ;;
    esac
    exit 0
    """)


class DeployScriptTestCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.state = root / "state"
        self.state.mkdir()
        bin_dir = root / "bin"
        bin_dir.mkdir()
        docker = bin_dir / "docker"
        docker.write_text(FAKE_DOCKER, encoding="utf-8")
        docker.chmod(docker.stat().st_mode | stat.S_IEXEC)
        self.docker_log = root / "docker.log"
        self.docker_log.touch()
        self.env = {
            "PATH": f"{bin_dir}:/usr/bin:/bin",
            "HOME": str(root),
            "FAKE_DOCKER_LOG": str(self.docker_log),
            "LAWDESK_STATE_DIR": str(self.state),
            "LAWDESK_REGISTRY": "ghcr.io/example/lawdesk",
        }

    def tearDown(self):
        self.tmp.cleanup()

    def run_script(self, script, *args, **extra_env):
        return subprocess.run(["bash", str(script), *args], env={**self.env, **extra_env},
                              capture_output=True, text=True, check=False, timeout=60)

    def calls(self):
        return self.docker_log.read_text(encoding="utf-8").splitlines()

    def current(self):
        path = self.state / "current-release"
        return path.read_text(encoding="utf-8").strip() if path.exists() else None

    def previous(self):
        path = self.state / "previous-release"
        return path.read_text(encoding="utf-8").strip() if path.exists() else None

    def history(self):
        path = self.state / "history.log"
        return path.read_text(encoding="utf-8") if path.exists() else ""


class InputValidationTest(DeployScriptTestCase):
    def test_requires_a_full_commit_sha(self):
        for bad in [[], ["latest"], ["abc123"], ["A" * 40], [SHA_A + "0"], [SHA_A, SHA_B], ["../" + "a" * 37]]:
            with self.subTest(args=bad):
                result = self.run_script(DEPLOY, *bad)
                self.assertEqual(2, result.returncode)
                self.assertIn("usage", result.stderr.lower())
        self.assertEqual([], self.calls())

    def test_requires_the_registry_to_be_configured(self):
        env = dict(self.env)
        del env["LAWDESK_REGISTRY"]
        result = subprocess.run(["bash", str(DEPLOY), SHA_A], env=env, capture_output=True, text=True, check=False)
        self.assertEqual(2, result.returncode)
        self.assertIn("LAWDESK_REGISTRY", result.stderr)
        self.assertEqual([], self.calls())


class SuccessfulDeployTest(DeployScriptTestCase):
    def test_first_deploy_pulls_starts_waits_smokes_and_records_the_release(self):
        result = self.run_script(DEPLOY, SHA_A)

        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls()
        self.assertTrue(any(" pull" in c and c.startswith(f"LAWDESK_TAG={SHA_A}") for c in calls), calls)
        self.assertTrue(any(" up -d --wait" in c and c.startswith(f"LAWDESK_TAG={SHA_A}") for c in calls), calls)
        self.assertTrue(any(" exec -T web " in c for c in calls), calls)
        self.assertEqual(SHA_A, self.current())
        self.assertIsNone(self.previous())
        self.assertIn(f"deployed {SHA_A}", self.history())

    def test_next_deploy_keeps_the_previous_release_for_rollback(self):
        self.run_script(DEPLOY, SHA_A)
        result = self.run_script(DEPLOY, SHA_B)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(SHA_B, self.current())
        self.assertEqual(SHA_A, self.previous())

    def test_redeploying_the_current_release_is_idempotent(self):
        self.run_script(DEPLOY, SHA_A)
        self.run_script(DEPLOY, SHA_B)
        result = self.run_script(DEPLOY, SHA_B)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(SHA_B, self.current())
        self.assertEqual(SHA_A, self.previous())


class FailedDeployTest(DeployScriptTestCase):
    def test_unhealthy_release_is_rolled_back_to_the_current_one(self):
        self.run_script(DEPLOY, SHA_A)

        result = self.run_script(DEPLOY, SHA_B, FAKE_FAIL_UP_TAG=SHA_B)

        self.assertEqual(1, result.returncode)
        self.assertEqual(SHA_A, self.current())
        calls = self.calls()
        failed_at = max(i for i, c in enumerate(calls) if c.startswith(f"LAWDESK_TAG={SHA_B}") and " up " in c)
        self.assertTrue(any(c.startswith(f"LAWDESK_TAG={SHA_A}") and " up -d --wait" in c
                            for c in calls[failed_at + 1:]), calls)
        self.assertIn(f"failed {SHA_B}", self.history())
        self.assertIn(f"rolled back to {SHA_A}", self.history())

    def test_failed_smoke_test_also_rolls_back(self):
        self.run_script(DEPLOY, SHA_A)

        result = self.run_script(DEPLOY, SHA_B, FAKE_FAIL_SMOKE_TAG=SHA_B)

        self.assertEqual(1, result.returncode)
        self.assertEqual(SHA_A, self.current())
        self.assertIn(f"rolled back to {SHA_A}", self.history())

    def test_failed_redeploy_of_the_current_release_rolls_back_to_the_previous_one(self):
        # QAD-013: current B, previous A; B no longer starts, so A must be brought back.
        self.run_script(DEPLOY, SHA_A)
        self.run_script(DEPLOY, SHA_B)
        before = len(self.calls())

        result = self.run_script(DEPLOY, SHA_B, FAKE_FAIL_UP_TAG=SHA_B)

        self.assertEqual(1, result.returncode, result.stderr)
        self.assertTrue(any(c.startswith(f"LAWDESK_TAG={SHA_A}") and " up -d --wait" in c
                            for c in self.calls()[before:]), self.calls()[before:])
        self.assertEqual(SHA_A, self.current())
        self.assertIsNone(self.previous(), "the failed release must not stay a rollback target")
        self.assertIn(f"rolled back to {SHA_A}", self.history())

    def test_failed_redeploy_without_a_previous_release_reports_it(self):
        self.run_script(DEPLOY, SHA_A)

        result = self.run_script(DEPLOY, SHA_A, FAKE_FAIL_UP_TAG=SHA_A)

        self.assertEqual(1, result.returncode)
        self.assertIn("no previous release", result.stderr)
        self.assertEqual(SHA_A, self.current())

    def test_failed_first_deploy_records_nothing_as_current(self):
        result = self.run_script(DEPLOY, SHA_A, FAKE_FAIL_UP_TAG=SHA_A)

        self.assertEqual(1, result.returncode)
        self.assertIsNone(self.current())
        self.assertIn("no previous release", result.stderr)


class ConcurrencyTest(DeployScriptTestCase):
    def test_a_second_deployment_is_refused_while_one_is_running(self):
        lock = self.state / "deploy.lock"
        holder = subprocess.Popen(["flock", str(lock), "sleep", "5"])
        try:
            subprocess.run(["sleep", "0.3"], check=True)
            result = self.run_script(DEPLOY, SHA_A)
        finally:
            holder.terminate()
            holder.wait()

        self.assertEqual(3, result.returncode)
        self.assertIn("another deployment", result.stderr)
        self.assertEqual([], self.calls())


class RollbackTest(DeployScriptTestCase):
    def test_rollback_redeploys_the_previous_release(self):
        self.run_script(DEPLOY, SHA_A)
        self.run_script(DEPLOY, SHA_B)

        result = self.run_script(ROLLBACK)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(SHA_A, self.current())
        self.assertEqual(SHA_B, self.previous())

    def test_rollback_without_a_previous_release_is_refused(self):
        self.run_script(DEPLOY, SHA_A)
        before = len(self.calls())

        result = self.run_script(ROLLBACK)

        self.assertEqual(2, result.returncode)
        self.assertIn("no previous release", result.stderr)
        self.assertEqual(before, len(self.calls()))


class SafetyTest(DeployScriptTestCase):
    def test_every_docker_call_is_scoped_to_the_lawdesk_project_and_compose_file(self):
        self.run_script(DEPLOY, SHA_A)
        self.run_script(DEPLOY, SHA_B, FAKE_FAIL_UP_TAG=SHA_B)
        self.run_script(DEPLOY, SHA_C)
        self.run_script(ROLLBACK)

        calls = self.calls()
        self.assertTrue(calls)
        for call in calls:
            with self.subTest(call=call):
                self.assertIn(" compose -p lawdesk-staging -f ", call)
                self.assertIn(str(DEPLOY_DIR / "compose.yaml"), call)

    def test_scripts_never_remove_volumes_networks_or_prune(self):
        for script in [DEPLOY, ROLLBACK]:
            text = script.read_text(encoding="utf-8")
            for forbidden in ["down -v", "--volumes", "volume rm", "network rm", "system prune", "rm -rf"]:
                with self.subTest(script=script.name, forbidden=forbidden):
                    self.assertNotIn(forbidden, text)


if __name__ == "__main__":
    unittest.main()
