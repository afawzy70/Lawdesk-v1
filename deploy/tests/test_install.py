"""One repeatable operator command for the first Contabo install and later re-runs (deploy/host/install.sh).

The real install.sh and bootstrap.sh run against a local Git origin that plays GitHub; docker, sudo, gh, curl, the gate
and attach.sh are recording fakes, so these tests cover the orchestration: order, idempotence, failure handling,
recovery output and that the one-time CI key never leaks.
"""

import os
import shutil
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

HOST_DIR = Path(__file__).resolve().parents[1] / "host"
INSTALL = HOST_DIR / "install.sh"
BOOTSTRAP = HOST_DIR / "bootstrap.sh"

FAKES = {
    "docker": """\
        #!/bin/bash
        echo "docker $*" >> "$FAKE_EVENTS"
        case "$1 $2" in
          "network inspect") [ -f "$FAKE_ROOT/edge" ]; exit ;;
          "network create") touch "$FAKE_ROOT/edge"; exit 0 ;;
        esac
        if [ "$1" = ps ]; then echo m1; exit 0; fi
        if [ "$1" = inspect ]; then
          case "$*" in
            *Health.Status*) name=${!#}; [ "$name" = "lawdesk-staging-${FAKE_UNHEALTHY:-none}-1" ] && echo unhealthy || echo healthy ;;
            *StartedAt*) [ -n "${FAKE_MATBAKHY_RESTARTS:-}" ] && [ -f "$FAKE_ROOT/attached" ] && echo "/m1 later 0" || echo "/m1 early 0" ;;
          esac
          exit 0
        fi
        """,
    "sudo": """\
        #!/bin/bash
        echo "sudo $*" >> "$FAKE_EVENTS"
        [ "$1" = install ] || exit 1
        cp "${@: -2:1}" "${@: -1}" && chmod 755 "${@: -1}"
        """,
    "gh": """\
        #!/bin/bash
        if [ "$1 $2" = "auth status" ]; then [ -z "${FAKE_NO_GH:-}" ]; exit; fi
        if [ "$1 $2" = "secret set" ]; then
          body=$(cat); grep -q 'BEGIN OPENSSH PRIVATE KEY' <<< "$body" && private=yes || private=no
          echo "gh secret set $3 private=$private" >> "$FAKE_EVENTS"; exit 0
        fi
        echo "gh $*" >> "$FAKE_EVENTS"
        """,
    "curl": """\
        #!/bin/bash
        url=${!#}; echo "curl $url" >> "$FAKE_EVENTS"
        [ -n "${FAKE_HTTPS_FAIL:-}" ] && exit 22
        case "$url" in
          */actuator/health/readiness) echo '{"status":"UP"}' ;;
          */healthz) echo ok ;;
          *) echo '<title>LawDesk</title>' ;;
        esac
        """,
}

FAKE_GATE = """\
    #!/bin/bash
    echo "gate $*" >> "$FAKE_EVENTS"
    if [ "$1" = deploy ]; then
      [ "${FAKE_DEPLOY_EXIT:-0}" = 0 ] || { echo "RESULT failed $2 exit=$FAKE_DEPLOY_EXIT"; exit "$FAKE_DEPLOY_EXIT"; }
      mkdir -p "$LAWDESK_HOME/state"; echo "$2" > "$LAWDESK_HOME/state/current-release"; echo "RESULT deployed $2"
    fi
    [ "$1" = status ] && echo "current=$(cat "$LAWDESK_HOME/state/current-release" 2>/dev/null)"
    exit 0
    """

FAKE_ATTACH = """\
    #!/bin/bash
    echo "attach" >> "$FAKE_EVENTS"; touch "$FAKE_ROOT/attached"
    """


def git(cwd, *args):
    return subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def write(path, body, mode=0o755):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(body), encoding="utf-8")
    path.chmod(mode)


class InstallTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.root = root
        # GitHub: a develop branch carrying the real install/bootstrap scripts and a fake gate and attach.
        self.origin = root / "origin.git"
        git(root, "init", "-q", "--bare", "-b", "develop", str(self.origin))
        work = root / "work"
        git(root, "clone", "-q", str(self.origin), str(work))
        git(work, "config", "user.email", "t@example.invalid")
        git(work, "config", "user.name", "t")
        shutil.copy(INSTALL, work / "install.sh")
        write(work / "deploy" / "host" / "install.sh", INSTALL.read_text(encoding="utf-8"))
        write(work / "deploy" / "host" / "bootstrap.sh", BOOTSTRAP.read_text(encoding="utf-8"))
        write(work / "deploy" / "host" / "lawdesk-deploy-gate", FAKE_GATE)
        write(work / "deploy" / "proxy" / "attach.sh", FAKE_ATTACH)
        (work / "install.sh").unlink()
        git(work, "add", "-A")
        git(work, "commit", "-q", "-m", "release")
        git(work, "push", "-q", "origin", "HEAD:develop")
        self.work = work
        self.sha = git(work, "rev-parse", "HEAD")
        # The operator's checkout of develop, as the documented one-liner makes it.
        self.checkout = root / "checkout"
        git(root, "clone", "-q", "-b", "develop", str(self.origin), str(self.checkout))

        bin_dir = root / "bin"
        for name, body in FAKES.items():
            write(bin_dir / name, body)
        self.home = root / "lawdesk"
        self.home.mkdir()
        self.user_home = root / "devops"
        self.user_home.mkdir()
        host_key = root / "ssh_host_ed25519_key.pub"
        host_key.write_text("ssh-ed25519 AAAAhostkey root@vmi\n", encoding="utf-8")
        self.events = root / "events.log"
        self.events.touch()
        self.report = root / "report.txt"
        self.gate = root / "sbin" / "lawdesk-deploy-gate"
        self.gate.parent.mkdir()
        self.env = {"PATH": f"{bin_dir}:/usr/bin:/bin", "HOME": str(self.user_home), "LAWDESK_HOME": str(self.home),
                    "LAWDESK_GATE": str(self.gate), "LAWDESK_REPORT": str(self.report),
                    "LAWDESK_SSH_HOST_KEY": str(host_key), "FAKE_EVENTS": str(self.events), "FAKE_ROOT": str(root),
                    "LAWDESK_HTTPS_TRIES": "2", "LAWDESK_HTTPS_WAIT": "0"}

    def tearDown(self):
        self.tmp.cleanup()

    def install(self, **extra):
        return subprocess.run(["bash", str(self.checkout / "deploy" / "host" / "install.sh")],
                              env={**self.env, **extra}, capture_output=True, text=True, check=False, timeout=120)

    def events_list(self):
        return self.events.read_text(encoding="utf-8").splitlines()

    def index(self, prefix):
        return next(i for i, e in enumerate(self.events_list()) if e.startswith(prefix))

    def has(self, prefix):
        return any(e.startswith(prefix) for e in self.events_list())

    def test_full_run_deploys_attaches_verifies_https_and_then_enables_cd(self):
        result = self.install()

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertLess(self.index("sudo install"), self.index(f"gate deploy {self.sha}"))
        self.assertLess(self.index(f"gate deploy {self.sha}"), self.index("attach"))
        self.assertLess(self.index("attach"), self.index("curl https://lawdesk.167-86-90-174.sslip.io/healthz"))
        self.assertIn("gh secret set LAWDESK_STAGING_SSH_KEY private=yes", self.events_list())
        self.assertTrue(self.has("gh secret set LAWDESK_STAGING_KNOWN_HOSTS"))
        for variable in ["LAWDESK_STAGING_SSH_TARGET", "LAWDESK_STAGING_URL", "LAWDESK_DEVELOP_POLICY"]:
            self.assertTrue(self.has(f"gh variable set {variable}"), variable)
        enable = self.index("gh variable set LAWDESK_STAGING_DEPLOY")
        self.assertEqual(len(self.events_list()) - 1, enable, "CD is switched on last, after HTTPS was verified")
        self.assertIn(f"LAWDESK INSTALLED {self.sha}", result.stdout)

    def test_the_ci_key_is_bound_to_the_gate_and_never_leaks(self):
        result = self.install()

        entries = (self.user_home / ".ssh" / "authorized_keys").read_text(encoding="utf-8").splitlines()
        self.assertEqual(1, len(entries))
        self.assertTrue(entries[0].startswith(f'restrict,command="/usr/local/sbin/lawdesk-deploy-gate" ssh-ed25519 '))
        for text in [result.stdout, result.stderr, self.report.read_text(encoding="utf-8")]:
            self.assertNotIn("PRIVATE KEY", text)
        leftovers = [p for p in self.user_home.rglob("*") if p.is_file() and "PRIVATE KEY" in p.read_text(errors="ignore")
                     and p.name != "lawdesk_github_ro"]
        self.assertEqual([], leftovers, "the private half exists only in GitHub Secrets afterwards")

    def test_report_is_readable_for_the_agent_and_logged_on_the_host(self):
        self.install()

        self.assertEqual(0o644, stat.S_IMODE(self.report.stat().st_mode))
        logs = list((self.home / "logs").glob("*-install.log"))
        self.assertEqual(1, len(logs))
        self.assertIn(self.sha, logs[0].read_text(encoding="utf-8"))

    def test_refuses_a_checkout_that_is_not_the_develop_head(self):
        (self.work / "later.txt").write_text("later", encoding="utf-8")
        git(self.work, "add", "-A")
        git(self.work, "commit", "-q", "-m", "later")
        git(self.work, "push", "-q", "origin", "HEAD:develop")

        result = self.install()

        self.assertEqual(2, result.returncode, result.stdout)
        self.assertIn("not the develop head", result.stdout + result.stderr)
        self.assertFalse(self.has("gate") or self.has("sudo"))

    def test_failed_deploy_stops_before_the_proxy_and_prints_the_recovery(self):
        result = self.install(FAKE_DEPLOY_EXIT="1")

        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.has("attach"))
        self.assertFalse(self.has("gh variable set LAWDESK_STAGING_DEPLOY"))
        report = self.report.read_text(encoding="utf-8")
        self.assertIn("FAILED", report)
        self.assertIn("Recovery", report)

    def test_unhealthy_container_stops_before_the_proxy(self):
        result = self.install(FAKE_UNHEALTHY="backend")

        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.has("attach"))
        self.assertIn("lawdesk-staging-backend-1", result.stdout)

    def test_https_failure_leaves_cd_off_and_names_the_detach_command(self):
        result = self.install(FAKE_HTTPS_FAIL="1")

        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.has("gh variable set LAWDESK_STAGING_DEPLOY"))
        self.assertIn("detach.sh", self.report.read_text(encoding="utf-8"))

    def test_a_matbakhy_restart_is_reported_as_a_failure(self):
        result = self.install(FAKE_MATBAKHY_RESTARTS="1")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("Matbakhy", result.stdout)
        self.assertFalse(self.has("gh variable set LAWDESK_STAGING_DEPLOY"))

    def test_a_rerun_reuses_the_key_the_gate_and_the_images(self):
        self.install()
        before = len(self.events_list())

        result = self.install()

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        later = self.events_list()[before:]
        self.assertFalse(any(e.startswith("sudo") for e in later), "an unchanged gate is not reinstalled")
        self.assertFalse(any(e.startswith("gh secret set LAWDESK_STAGING_SSH_KEY") for e in later))
        self.assertEqual(1, len((self.user_home / ".ssh" / "authorized_keys").read_text(encoding="utf-8").splitlines()))

    def test_without_github_cli_the_site_is_deployed_and_cd_needs_one_owner_command(self):
        result = self.install(FAKE_NO_GH="1")

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue(self.has("attach"))
        key = self.user_home / ".ssh" / "lawdesk_ci_deploy"
        self.assertEqual(0o600, stat.S_IMODE(key.stat().st_mode))
        self.assertIn("gh secret set LAWDESK_STAGING_SSH_KEY", result.stdout)
        self.assertFalse(self.has("gh variable set"))


if __name__ == "__main__":
    unittest.main()
