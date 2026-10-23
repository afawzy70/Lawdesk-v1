"""Host bootstrap for LawDesk staging (run once by `devops`; idempotent). Uses a fake `docker`; real ssh-keygen/git."""

import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

BOOTSTRAP = Path(__file__).resolve().parents[1] / "host" / "bootstrap.sh"
GITHUB_KEY = "github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl"

FAKE_DOCKER = textwrap.dedent("""\
    #!/bin/sh
    echo "$*" >> "$FAKE_DOCKER_LOG"
    if [ "$1 $2" = "network inspect" ]; then [ -f "$FAKE_ROOT/edge" ]; exit; fi
    if [ "$1 $2" = "network create" ]; then touch "$FAKE_ROOT/edge"; fi
    """)


class BootstrapTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.root = root
        self.home = root / "deployments" / "lawdesk"
        self.home.mkdir(parents=True)
        self.user_home = root / "devops"
        self.user_home.mkdir()
        bin_dir = root / "bin"
        bin_dir.mkdir()
        (bin_dir / "docker").write_text(FAKE_DOCKER, encoding="utf-8")
        (bin_dir / "docker").chmod(0o755)
        self.docker_log = root / "docker.log"
        self.docker_log.touch()
        key = root / "ci"
        subprocess.run(["ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "lawdesk-ci-deploy", "-f", str(key)],
                       check=True)
        self.ci_pub = key.with_suffix(".pub")
        self.env = {"PATH": f"{bin_dir}:/usr/bin:/bin", "HOME": str(self.user_home), "LAWDESK_HOME": str(self.home),
                    "FAKE_DOCKER_LOG": str(self.docker_log), "FAKE_ROOT": str(root)}

    def tearDown(self):
        self.tmp.cleanup()

    def bootstrap(self, *args):
        return subprocess.run(["bash", str(BOOTSTRAP), *args], env=self.env, capture_output=True, text=True,
                              check=False, timeout=60)

    def mode(self, path):
        return stat.S_IMODE(path.stat().st_mode)

    def test_requires_the_ci_public_key(self):
        for args in [[], [str(self.root / "missing.pub")]]:
            with self.subTest(args=args):
                self.assertEqual(2, self.bootstrap(*args).returncode)

    def test_rejects_a_private_key_or_anything_but_one_ed25519_public_key(self):
        bad = self.root / "bad.pub"
        for content in ["-----BEGIN OPENSSH PRIVATE KEY-----\nx\n", "ssh-rsa AAAA x\n",
                        self.ci_pub.read_text() * 2, 'command="sh" ' + self.ci_pub.read_text()]:
            with self.subTest(content=content[:30]):
                bad.write_text(content, encoding="utf-8")
                self.assertEqual(2, self.bootstrap(str(bad)).returncode)
        self.assertFalse((self.user_home / ".ssh" / "authorized_keys").exists())

    def test_creates_the_isolated_layout(self):
        result = self.bootstrap(str(self.ci_pub))

        self.assertEqual(0, result.returncode, result.stderr)
        for name in ["state", "releases", "backups", "logs", "proxy-backups"]:
            self.assertTrue((self.home / name).is_dir(), name)
        self.assertEqual(0o700, self.mode(self.home / "secrets"))
        self.assertEqual(0o700, self.mode(self.home / "backups"))
        self.assertTrue((self.home / "staging.env").is_file())

    def test_generates_database_passwords_once_and_never_overwrites_them(self):
        self.bootstrap(str(self.ci_pub))
        admin = (self.home / "secrets" / "db_admin_password.txt").read_text(encoding="utf-8")
        app = (self.home / "secrets" / "db_app_password.txt").read_text(encoding="utf-8")

        self.bootstrap(str(self.ci_pub))

        self.assertRegex(admin, r"^[0-9a-f]{64}\n$")
        self.assertNotEqual(admin, app)
        self.assertEqual(admin, (self.home / "secrets" / "db_admin_password.txt").read_text(encoding="utf-8"))
        # Compose bind-mounts each file into containers running as other users; the 0700 directory protects them.
        self.assertEqual(0o644, self.mode(self.home / "secrets" / "db_app_password.txt"))

    def test_installs_the_ci_key_restricted_to_the_gate_once(self):
        self.bootstrap(str(self.ci_pub))
        self.bootstrap(str(self.ci_pub))

        lines = (self.user_home / ".ssh" / "authorized_keys").read_text(encoding="utf-8").splitlines()
        key = self.ci_pub.read_text(encoding="utf-8").split()[1]
        entries = [line for line in lines if key in line]
        self.assertEqual(1, len(entries))
        self.assertTrue(entries[0].startswith('restrict,command="/usr/local/sbin/lawdesk-deploy-gate" ssh-ed25519 '))
        self.assertEqual(0o600, self.mode(self.user_home / ".ssh" / "authorized_keys"))

    def test_keeps_existing_authorized_keys(self):
        ssh = self.user_home / ".ssh"
        ssh.mkdir(mode=0o700)
        (ssh / "authorized_keys").write_text("ssh-ed25519 AAAAexisting operator\n", encoding="utf-8")

        self.bootstrap(str(self.ci_pub))

        self.assertIn("ssh-ed25519 AAAAexisting operator", (ssh / "authorized_keys").read_text(encoding="utf-8"))

    def test_prepares_a_read_only_fetch_from_github_with_a_pinned_host_key(self):
        result = self.bootstrap(str(self.ci_pub))

        repo = self.home / "repo.git"
        config = subprocess.run(["git", "-C", str(repo), "config", "--list"], capture_output=True, text=True,
                                check=True).stdout
        self.assertIn("remote.origin.url=git@github.com:afawzy70/Lawdesk-v1.git", config)
        self.assertIn("StrictHostKeyChecking=yes", config)
        self.assertEqual(GITHUB_KEY + "\n", (self.home / "github_known_hosts").read_text(encoding="utf-8"))
        fetch_key = self.user_home / ".ssh" / "lawdesk_github_ro.pub"
        self.assertTrue(fetch_key.exists())
        self.assertIn(fetch_key.read_text(encoding="utf-8").strip(), result.stdout)

    def test_creates_the_internal_edge_network_only_when_missing(self):
        self.bootstrap(str(self.ci_pub))
        self.bootstrap(str(self.ci_pub))

        creates = [c for c in self.docker_log.read_text(encoding="utf-8").splitlines() if c.startswith("network create")]
        self.assertEqual(["network create --internal lawdesk-edge"], creates)

    def test_prints_the_known_hosts_line_for_the_ci_secret(self):
        result = self.bootstrap(str(self.ci_pub))

        self.assertIn("LAWDESK_STAGING_KNOWN_HOSTS", result.stdout)

    def test_never_touches_anything_outside_its_paths(self):
        text = BOOTSTRAP.read_text(encoding="utf-8")
        for forbidden in ["sudo", "matbakhy", "docker compose", "docker rm", "docker stop", "rm -rf", "chmod 777",
                          "usermod"]:
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, text.lower())
        # /etc is only read (the host's public SSH key), never written.
        self.assertNotRegex(text, r"(>>?|install[^\n]*|cp[^\n]*|tee[^\n]*)\s*/etc/")


if __name__ == "__main__":
    unittest.main()
