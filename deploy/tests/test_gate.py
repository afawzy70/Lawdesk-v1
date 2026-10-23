"""Host deploy gate: the only command the CI deploy key may run (authorized_keys `restrict,command=...`).

A real local Git repository plays GitHub; a fake `docker` records calls; the release's own deploy.sh is a fixture
that records how it was invoked, so these tests cover the gate alone.
"""

import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

GATE = Path(__file__).resolve().parents[1] / "host" / "lawdesk-deploy-gate"

FAKE_DOCKER = textwrap.dedent("""\
    #!/bin/sh
    echo "$*" >> "$FAKE_DOCKER_LOG"
    case "$1 $2" in
      "image inspect") grep -qxF "$3" "$FAKE_IMAGES" ;;
      "build "*) [ -n "${FAKE_FAIL_BUILD:-}" ] && exit 1
                 for a in "$@"; do [ "$prev" = "-t" ] && echo "$a" >> "$FAKE_IMAGES"; prev=$a; done; exit 0 ;;
    esac
    """)

FIXTURE_DEPLOY = textwrap.dedent("""\
    #!/usr/bin/env bash
    { echo "deploy.sh $*"
      for v in LAWDESK_REGISTRY LAWDESK_IMAGE_SOURCE LAWDESK_STATE_DIR LAWDESK_RELEASES_DIR LAWDESK_ENV_FILE \\
               LAWDESK_BACKUP_DIR LAWDESK_SECRETS_DIR; do echo "$v=${!v:-}"; done
    } >> "$FAKE_DEPLOY_LOG"
    if [[ ${FAKE_DEPLOY_EXIT:-0} == 0 ]]; then echo "$1" > "$LAWDESK_STATE_DIR/current-release"; fi
    exit "${FAKE_DEPLOY_EXIT:-0}"
    """)

FIXTURE_ROLLBACK = textwrap.dedent("""\
    #!/usr/bin/env bash
    echo "rollback.sh" >> "$FAKE_DEPLOY_LOG"
    """)


def git(cwd, *args):
    return subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


class GateTestCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.origin = root / "origin.git"
        git(root, "init", "-q", "--bare", "-b", "develop", str(self.origin))
        self.work = root / "work"
        git(root, "clone", "-q", str(self.origin), str(self.work))
        git(self.work, "config", "user.email", "test@example.invalid")
        git(self.work, "config", "user.name", "test")
        scripts = self.work / "deploy" / "scripts"
        scripts.mkdir(parents=True)
        for name, body in [("deploy.sh", FIXTURE_DEPLOY), ("rollback.sh", FIXTURE_ROLLBACK)]:
            (scripts / name).write_text(body, encoding="utf-8")
            (scripts / name).chmod(0o755)
        (self.work / "backend").mkdir()
        (self.work / "frontend").mkdir()
        self.sha_a = self.commit("a")
        self.sha_b = self.commit("b")
        git(self.work, "push", "-q", "origin", "HEAD:develop")

        self.home = root / "home"
        self.home.mkdir()
        git(root, "clone", "-q", "--bare", str(self.origin), str(self.home / "repo.git"))
        bin_dir = root / "bin"
        bin_dir.mkdir()
        docker = bin_dir / "docker"
        docker.write_text(FAKE_DOCKER, encoding="utf-8")
        docker.chmod(docker.stat().st_mode | stat.S_IEXEC)
        self.docker_log = root / "docker.log"
        self.docker_log.touch()
        self.images = root / "images"
        self.images.touch()
        self.deploy_log = root / "deploy.log"
        self.deploy_log.touch()
        self.env = {"PATH": f"{bin_dir}:/usr/bin:/bin", "HOME": str(root), "LAWDESK_HOME": str(self.home),
                    "FAKE_DOCKER_LOG": str(self.docker_log), "FAKE_IMAGES": str(self.images),
                    "FAKE_DEPLOY_LOG": str(self.deploy_log)}

    def tearDown(self):
        self.tmp.cleanup()

    def commit(self, content):
        (self.work / "file.txt").write_text(content, encoding="utf-8")
        git(self.work, "add", "-A")
        git(self.work, "commit", "-q", "-m", content)
        return git(self.work, "rev-parse", "HEAD")

    def gate(self, request, **extra):
        env = {**self.env, "SSH_ORIGINAL_COMMAND": request, **extra}
        return subprocess.run(["bash", str(GATE)], env=env, capture_output=True, text=True, check=False, timeout=60)

    def docker_calls(self):
        return self.docker_log.read_text(encoding="utf-8").splitlines()

    def deploy_calls(self):
        return self.deploy_log.read_text(encoding="utf-8").splitlines()


class RequestValidationTest(GateTestCase):
    def test_only_the_three_known_requests_are_accepted(self):
        for bad in ["", "bash", "deploy", "deploy latest", f"deploy {'A' * 40}", f"deploy {'a' * 39}",
                    f"deploy {'a' * 40} extra", f"deploy {'a' * 40}; rm -rf /", f"deploy $(id)", "status now",
                    "rollback x", f"deploy\t{'a' * 40}", f"deploy {'a' * 40}\n"]:
            with self.subTest(request=bad):
                result = self.gate(bad)
                self.assertEqual(2, result.returncode, result.stderr)
                self.assertIn("usage", result.stderr)
        self.assertEqual([], self.docker_calls())
        self.assertEqual([], self.deploy_calls())

    def test_commit_not_on_develop_is_refused(self):
        git(self.work, "checkout", "-q", "-b", "feature/x")
        unmerged = self.commit("unmerged")
        git(self.work, "push", "-q", "origin", "feature/x")

        result = self.gate(f"deploy {unmerged}")

        self.assertEqual(5, result.returncode, result.stderr)
        self.assertIn("not on develop", result.stderr)
        self.assertEqual([], self.docker_calls())

    def test_commit_removed_from_develop_is_refused_even_if_fetched_before(self):
        git(self.work, "checkout", "-q", "-b", "later", "develop")
        dropped = self.commit("dropped")
        git(self.work, "push", "-q", "origin", "HEAD:develop")
        self.assertEqual(0, self.gate(f"deploy {dropped}").returncode)
        git(self.work, "push", "-q", "-f", "origin", f"{self.sha_b}:develop")

        result = self.gate(f"deploy {dropped}")

        self.assertEqual(5, result.returncode, result.stderr)


class DeployTest(GateTestCase):
    def test_deploy_builds_both_images_and_runs_the_release_own_deploy_script(self):
        result = self.gate(f"deploy {self.sha_b}")

        self.assertEqual(0, result.returncode, result.stderr)
        release = self.home / "releases" / self.sha_b
        self.assertEqual("b", (release / "file.txt").read_text(encoding="utf-8"))
        builds = [c for c in self.docker_calls() if c.startswith("build ")]
        self.assertEqual(2, len(builds), builds)
        self.assertTrue(any(f"-t lawdesk-local/backend:{self.sha_b}" in c and str(release / "backend") in c
                            for c in builds), builds)
        self.assertTrue(any(f"-t lawdesk-local/web:{self.sha_b}" in c and str(release / "frontend") in c
                            for c in builds), builds)
        self.assertTrue(all(f"org.opencontainers.image.revision={self.sha_b}" in c for c in builds), builds)
        calls = self.deploy_calls()
        self.assertEqual(f"deploy.sh {self.sha_b}", calls[0])
        self.assertIn("LAWDESK_REGISTRY=lawdesk-local", calls)
        self.assertIn("LAWDESK_IMAGE_SOURCE=local", calls)
        self.assertIn(f"LAWDESK_STATE_DIR={self.home / 'state'}", calls)
        self.assertIn(f"LAWDESK_RELEASES_DIR={self.home / 'releases'}", calls)
        self.assertIn(f"LAWDESK_ENV_FILE={self.home / 'staging.env'}", calls)
        self.assertIn(f"LAWDESK_BACKUP_DIR={self.home / 'backups'}", calls)
        self.assertIn(f"LAWDESK_SECRETS_DIR={self.home / 'secrets'}", calls)
        self.assertIn(f"RESULT deployed {self.sha_b}", result.stdout)

    def test_an_older_commit_on_develop_can_be_deployed(self):
        result = self.gate(f"deploy {self.sha_a}")

        self.assertEqual(0, result.returncode, result.stderr)

    def test_repeating_a_deployment_reuses_the_release_and_its_images(self):
        self.gate(f"deploy {self.sha_b}")
        builds_before = sum(c.startswith("build ") for c in self.docker_calls())

        result = self.gate(f"deploy {self.sha_b}")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(builds_before, sum(c.startswith("build ") for c in self.docker_calls()))
        self.assertEqual(2, sum(c.startswith("deploy.sh ") for c in self.deploy_calls()))

    def test_failed_build_changes_nothing(self):
        result = self.gate(f"deploy {self.sha_b}", FAKE_FAIL_BUILD="1")

        self.assertEqual(6, result.returncode)
        self.assertIn("build failed", result.stderr)
        self.assertEqual([], self.deploy_calls())

    def test_deploy_script_failure_is_reported_with_its_exit_code(self):
        result = self.gate(f"deploy {self.sha_b}", FAKE_DEPLOY_EXIT="1")

        self.assertEqual(1, result.returncode)
        self.assertIn(f"RESULT failed {self.sha_b} exit=1", result.stdout)

    def test_every_run_leaves_a_log_file(self):
        self.gate(f"deploy {self.sha_b}")

        logs = list((self.home / "logs").glob(f"*-deploy-{self.sha_b}.log"))
        self.assertEqual(1, len(logs))
        self.assertIn(f"RESULT deployed {self.sha_b}", logs[0].read_text(encoding="utf-8"))

    def test_a_second_gate_run_is_refused_while_one_is_running(self):
        (self.home / "state").mkdir(exist_ok=True)
        holder = subprocess.Popen(["flock", str(self.home / "state" / "gate.lock"), "sleep", "5"])
        try:
            subprocess.run(["sleep", "0.3"], check=True)
            result = self.gate(f"deploy {self.sha_b}")
        finally:
            holder.terminate()
            holder.wait()

        self.assertEqual(3, result.returncode)
        self.assertEqual([], self.docker_calls())


class StatusAndRollbackTest(GateTestCase):
    def test_status_reports_current_and_previous_release_and_history(self):
        state = self.home / "state"
        state.mkdir()
        (state / "current-release").write_text(self.sha_b + "\n", encoding="utf-8")
        (state / "previous-release").write_text(self.sha_a + "\n", encoding="utf-8")
        (state / "history.log").write_text("2026-10-23T00:00:00Z deployed x\n", encoding="utf-8")

        result = self.gate("status")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(f"current={self.sha_b}", result.stdout)
        self.assertIn(f"previous={self.sha_a}", result.stdout)
        self.assertIn("deployed x", result.stdout)

    def test_rollback_runs_the_current_release_rollback_script(self):
        self.gate(f"deploy {self.sha_b}")

        result = self.gate("rollback")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("rollback.sh", self.deploy_calls())

    def test_rollback_without_a_current_release_is_refused(self):
        result = self.gate("rollback")

        self.assertEqual(2, result.returncode)
        self.assertIn("no current release", result.stderr)


class GateSafetyTest(unittest.TestCase):
    def test_gate_never_removes_volumes_or_prunes(self):
        text = GATE.read_text(encoding="utf-8")
        for forbidden in ["down -v", "--volumes", "volume rm", "network rm", "system prune", "rm -rf"]:
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, text)


if __name__ == "__main__":
    unittest.main()
