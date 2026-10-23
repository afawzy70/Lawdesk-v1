"""Rollback drill on the real staging stack (operator-run): start a release that cannot become healthy and prove
deploy.sh brings the running release back. The drill images are derived from the running release, and only they are
removed afterwards."""

import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

DRILL = Path(__file__).resolve().parents[1] / "host" / "rollback-drill.sh"
CURRENT = "c" * 40
DRILL_SHA = "dead" * 10

FAKE_DOCKER = textwrap.dedent("""\
    #!/bin/sh
    if [ "$1" = build ]; then
      echo "build $* STDIN:$(cat | tr '\\n' '|')" >> "$FAKE_DOCKER_LOG"
    else
      echo "$*" >> "$FAKE_DOCKER_LOG"
    fi
    """)

FIXTURE_DEPLOY = textwrap.dedent("""\
    #!/usr/bin/env bash
    echo "deploy.sh $* registry=$LAWDESK_REGISTRY source=$LAWDESK_IMAGE_SOURCE" >> "$FAKE_DEPLOY_LOG"
    [[ -n ${FAKE_DRILL_DEPLOYED:-} ]] && { echo "$1" > "$LAWDESK_STATE_DIR/current-release"; exit 0; }
    echo "x rolled back to $(cat "$LAWDESK_STATE_DIR/current-release")" >> "$LAWDESK_STATE_DIR/history.log"
    exit 1
    """)

FIXTURE_SMOKE = textwrap.dedent("""\
    #!/usr/bin/env bash
    echo "smoke.sh tag=$LAWDESK_TAG" >> "$FAKE_DEPLOY_LOG"
    exit "${FAKE_SMOKE_EXIT:-0}"
    """)


class RollbackDrillTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.home = root / "home"
        self.state = self.home / "state"
        self.state.mkdir(parents=True)
        scripts = self.home / "releases" / CURRENT / "deploy" / "scripts"
        scripts.mkdir(parents=True)
        for name, body in [("deploy.sh", FIXTURE_DEPLOY), ("smoke.sh", FIXTURE_SMOKE)]:
            (scripts / name).write_text(body, encoding="utf-8")
            (scripts / name).chmod(0o755)
        bin_dir = root / "bin"
        bin_dir.mkdir()
        (bin_dir / "docker").write_text(FAKE_DOCKER, encoding="utf-8")
        (bin_dir / "docker").chmod(0o755)
        self.docker_log = root / "docker.log"
        self.deploy_log = root / "deploy.log"
        self.docker_log.touch()
        self.deploy_log.touch()
        self.env = {"PATH": f"{bin_dir}:/usr/bin:/bin", "HOME": str(root), "LAWDESK_HOME": str(self.home),
                    "FAKE_DOCKER_LOG": str(self.docker_log), "FAKE_DEPLOY_LOG": str(self.deploy_log)}

    def tearDown(self):
        self.tmp.cleanup()

    def drill(self, **extra):
        return subprocess.run(["bash", str(DRILL)], env={**self.env, **extra}, capture_output=True, text=True,
                              check=False, timeout=60)

    def set_current(self):
        (self.state / "current-release").write_text(CURRENT + "\n", encoding="utf-8")

    def test_refuses_without_a_running_release(self):
        result = self.drill()

        self.assertEqual(2, result.returncode)
        self.assertEqual("", self.docker_log.read_text(encoding="utf-8"))

    def test_broken_release_is_rolled_back_and_the_running_release_still_passes_smoke(self):
        self.set_current()

        result = self.drill()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("DRILL PASSED", result.stdout)
        docker = self.docker_log.read_text(encoding="utf-8")
        self.assertIn(f"-t lawdesk-local/backend:{DRILL_SHA}", docker)
        self.assertIn(f"FROM lawdesk-local/backend:{CURRENT}", docker)
        self.assertIn(f"tag lawdesk-local/web:{CURRENT} lawdesk-local/web:{DRILL_SHA}", docker)
        deploy = self.deploy_log.read_text(encoding="utf-8").splitlines()
        self.assertEqual(f"deploy.sh {DRILL_SHA} registry=lawdesk-local source=local", deploy[0])
        self.assertIn(f"smoke.sh tag={CURRENT}", deploy)
        self.assertEqual(CURRENT, (self.state / "current-release").read_text(encoding="utf-8").strip())

    def test_drill_images_and_only_they_are_removed(self):
        self.set_current()

        self.drill()

        removals = [c for c in self.docker_log.read_text(encoding="utf-8").splitlines() if c.startswith("image rm")]
        self.assertEqual([f"image rm lawdesk-local/backend:{DRILL_SHA} lawdesk-local/web:{DRILL_SHA}"], removals)

    def test_drill_fails_if_the_broken_release_was_accepted(self):
        self.set_current()

        result = self.drill(FAKE_DRILL_DEPLOYED="1")

        self.assertEqual(1, result.returncode)
        self.assertIn("DRILL FAILED", result.stdout)

    def test_drill_fails_if_the_restored_release_does_not_pass_smoke(self):
        self.set_current()

        result = self.drill(FAKE_SMOKE_EXIT="1")

        self.assertEqual(1, result.returncode)
        self.assertIn("DRILL FAILED", result.stdout)


if __name__ == "__main__":
    unittest.main()
