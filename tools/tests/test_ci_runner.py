"""Contract tests for the self-hosted CI runner tooling (LD-041, docs/operations/CI-RUNNER.md).

The units and scripts in tools/ci-runner/ are installed once by the infrastructure administrator. These tests pin down
the security boundary (an unprivileged user, a private rootless Docker daemon, no docker group, no sudo) and the
resource limits that protect Matbakhy on the shared host. They never need root or systemd.
"""

import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DIR = REPO_ROOT / "tools" / "ci-runner"
SETUP = DIR / "setup-rootless-docker.sh"
GIB = 1024**3


def unit(name):
    """A systemd unit file as {section: {key: [values]}}; keys may repeat (Environment=)."""
    sections, current = {}, None
    for raw in (DIR / name).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("["):
            current = sections.setdefault(line.strip("[]"), {})
        else:
            key, _, value = line.partition("=")
            current.setdefault(key, []).append(value)
    return sections


def size(value):
    number, suffix = re.fullmatch(r"(\d+)([KMGT]?)", value).groups()
    return int(number) * {"": 1, "K": 1024, "M": 1024**2, "G": GIB, "T": 1024**4}[suffix]


class UnitSyntaxTest(unittest.TestCase):
    """systemd silently ignores a key it does not know, which turns a limit into no limit."""

    @staticmethod
    def findings(path):
        # System mode on purpose: `--user` needs a live user session and then prints nothing at all.
        result = subprocess.run(["systemd-analyze", "verify", str(path)], text=True, capture_output=True, check=False,
                                timeout=60)
        return [line for line in (result.stdout + result.stderr).splitlines()
                if re.search(r"Unknown (key|section|lvalue)|Invalid|Failed to parse|not a valid", line)]

    @unittest.skipUnless(shutil.which("systemd-analyze"), "systemd-analyze is not installed")
    def test_systemd_knows_every_key_of_every_unit_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            canary = Path(tmp, "canary.service")
            canary.write_text("[Service]\nExecStart=/bin/true\nNoSuchKeyAtAll=1\n", encoding="utf-8")
            if not self.findings(canary):
                self.skipTest("this systemd-analyze does not report unknown keys")
            for name in ["lawdesk-ci-docker.service", "lawdesk-ci-maintenance.service", "lawdesk-ci-maintenance.timer"]:
                with self.subTest(unit=name):
                    copy = Path(tmp, name)
                    shutil.copy(DIR / name, copy)
                    self.assertEqual([], self.findings(copy))


class DockerDaemonUnitTest(unittest.TestCase):
    """The daemon is a *user* unit of lawdesk-ci (a user manager gives Docker the systemd cgroup driver, the only one
    that enforces the compose memory limits in a rootless daemon). The unit file itself is root-owned in
    /etc/systemd/user."""

    def setUp(self):
        self.sections = unit("lawdesk-ci-docker.service")
        self.service = self.sections["Service"]

    def test_is_a_user_unit_started_with_the_user_manager(self):
        self.assertEqual(["default.target"], self.sections["Install"]["WantedBy"])
        for key in ["User", "Group", "SupplementaryGroups"]:
            self.assertNotIn(key, self.service, "a user unit runs as its user; it never names another one")

    def test_is_a_rootless_daemon_on_a_socket_private_to_the_user(self):
        (exec_start,) = self.service["ExecStart"]
        self.assertIn("/usr/bin/dockerd-rootless.sh", exec_start)
        self.assertIn("--host=unix://%t/docker.sock", exec_start, "%t is the user's private runtime directory")
        self.assertNotIn("/var/run/docker.sock", exec_start)
        self.assertEqual(["notify"], self.service["Type"])

    def test_grants_nothing_beyond_the_user(self):
        text = (DIR / "lawdesk-ci-docker.service").read_text(encoding="utf-8")
        for forbidden in ["AmbientCapabilities", "CapabilityBoundingSet", "--privileged", "User=root",
                          "NoNewPrivileges=yes", "NoNewPrivileges=true", "--exec-opt native.cgroupdriver=cgroupfs"]:
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, text)

    def test_containers_cannot_reach_services_on_the_host_loopback(self):
        self.assertIn("DOCKERD_ROOTLESS_ROOTLESSKIT_DISABLE_HOST_LOOPBACK=true", self.service["Environment"])

    def test_is_bounded_in_cpu_memory_and_tasks(self):
        for key in ["CPUQuota", "MemoryMax", "MemoryHigh", "TasksMax", "Delegate"]:
            with self.subTest(key=key):
                self.assertIn(key, self.service)
        self.assertLess(size(self.service["MemoryHigh"][0]), size(self.service["MemoryMax"][0]))


class UserSliceLimitsTest(unittest.TestCase):
    """lawdesk-ci could edit its own user units, so the ceiling that counts is root-owned: on user-<uid>.slice."""

    def test_the_slice_caps_the_daemon_and_everything_in_it(self):
        slice_ = unit("user-slice-limits.conf")["Slice"]
        daemon = unit("lawdesk-ci-docker.service")["Service"]
        for key in ["CPUQuota", "MemoryMax", "TasksMax"]:
            self.assertIn(key, slice_)
        self.assertLessEqual(size(slice_["MemoryMax"][0]), size(daemon["MemoryMax"][0]))
        self.assertLessEqual(int(slice_["CPUQuota"][0].rstrip("%")), int(daemon["CPUQuota"][0].rstrip("%")))


class RunnerLimitsTest(unittest.TestCase):
    def setUp(self):
        sections = unit("runner-limits.conf")
        self.unit, self.service = sections["Unit"], sections["Service"]

    def test_starts_after_the_users_manager_that_runs_its_docker_daemon(self):
        self.assertEqual(["user@@UID@.service"], self.unit["Wants"])
        self.assertEqual(["user@@UID@.service"], self.unit["After"])

    def test_bounds_the_runner(self):
        for key in ["CPUQuota", "MemoryMax", "MemoryHigh", "TasksMax"]:
            with self.subTest(key=key):
                self.assertIn(key, self.service)

    def test_runner_and_daemon_together_leave_room_for_matbakhy(self):
        # The host has 23 GiB and 8 cores shared with Matbakhy, the monitoring stack and the developers' agents. Jobs run
        # one at a time, so these are ceilings, not reservations (ASSUMPTION, owner review).
        slice_ = unit("user-slice-limits.conf")["Slice"]
        memory = size(self.service["MemoryMax"][0]) + size(slice_["MemoryMax"][0])
        self.assertLessEqual(memory, 12 * GIB)
        for limits in (self.service, slice_):
            self.assertLessEqual(int(limits["CPUQuota"][0].rstrip("%")), 400)


class MaintenanceTest(unittest.TestCase):
    def test_runs_unprivileged_with_the_installed_script_and_a_timer(self):
        service = unit("lawdesk-ci-maintenance.service")["Service"]
        self.assertEqual(["lawdesk-ci"], service["User"])
        self.assertEqual(["/usr/local/libexec/lawdesk-ci-maintenance.sh"], service["ExecStart"])
        self.assertIn("OnCalendar", unit("lawdesk-ci-maintenance.timer")["Timer"])

    def test_script_cleans_only_the_ci_users_own_daemon_and_flags_a_full_disk(self):
        text = (DIR / "lawdesk-ci-maintenance.sh").read_text(encoding="utf-8")
        self.assertIn('DOCKER_HOST=${DOCKER_HOST:-unix://$XDG_RUNTIME_DIR/docker.sock}', text)
        self.assertIn("/run/user/$uid", text)
        self.assertNotIn("/var/run/docker.sock", text)
        for command in ["docker image prune -a -f --filter until=72h", "docker builder prune", "docker container prune"]:
            self.assertIn(command, text)
        self.assertRegex(text, r"-ge \"\$limit\"")


class SetupScriptTest(unittest.TestCase):
    def run_setup(self, *args, subuid="", subgid=""):
        with tempfile.TemporaryDirectory() as etc:
            Path(etc, "subuid").write_text(subuid, encoding="utf-8")
            Path(etc, "subgid").write_text(subgid, encoding="utf-8")
            env = {**os.environ, "LAWDESK_CI_ETC": etc}
            return subprocess.run([str(SETUP), *args], env=env, text=True, capture_output=True, timeout=60, check=False)

    def test_never_grants_docker_group_membership_or_sudo(self):
        code = [line for line in SETUP.read_text(encoding="utf-8").splitlines() if not line.lstrip().startswith("#")]
        for line in code:
            with self.subTest(line=line):
                self.assertNotRegex(line, r"usermod\s+-[a-zA-Z]*G|gpasswd|adduser|visudo|/sudoers|chmod .*docker\.sock")

    def test_dry_run_plans_a_private_subid_range_above_every_existing_one(self):
        existing = "ubuntu:100000:65536\ndevops:165536:65536\naiagent:231072:65536\n"
        result = self.run_setup("--dry-run", subuid=existing, subgid=existing)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("give lawdesk-ci sub-UIDs 327680-393215", result.stdout)
        self.assertIn("give lawdesk-ci sub-GIDs 327680-393215", result.stdout)

    def test_dry_run_changes_nothing_and_every_line_is_a_plan(self):
        result = self.run_setup("--dry-run")
        self.assertEqual(0, result.returncode, result.stderr)
        for line in result.stdout.splitlines():
            with self.subTest(line=line):
                self.assertRegex(line, r"^(would|next) ")
        self.assertNotIn("do ", result.stdout)

    def test_a_user_that_already_has_sub_ids_is_left_alone(self):
        have = "lawdesk-ci:300000:65536\n"
        result = self.run_setup("--dry-run", subuid=have, subgid=have)
        self.assertNotIn("sub-UIDs", result.stdout)
        self.assertNotIn("sub-GIDs", result.stdout)

    def test_dry_run_installs_the_root_owned_user_unit_and_the_ceilings_for_the_ci_users_uid(self):
        with tempfile.TemporaryDirectory() as etc:
            Path(etc, "subuid").write_text("", encoding="utf-8")
            Path(etc, "subgid").write_text("", encoding="utf-8")
            env = {**os.environ, "LAWDESK_CI_ETC": etc, "LAWDESK_CI_UID": "999"}
            out = subprocess.run([str(SETUP), "--dry-run"], env=env, text=True, capture_output=True, check=True).stdout
        for expected in ["enable lingering for lawdesk-ci", "install the user unit lawdesk-ci-docker.service",
                         "cap the user slice user-999.slice", "install the runner's resource limits",
                         "start lawdesk-ci's rootless Docker daemon",
                         "point the runner at /run/user/999/docker.sock"]:
            with self.subTest(expected=expected):
                self.assertIn(expected, out)

    def test_the_runner_drop_in_is_rendered_with_the_users_uid(self):
        with tempfile.TemporaryDirectory() as root:
            env = {**os.environ, "LAWDESK_CI_ETC": root, "LAWDESK_CI_UID": "999", "LAWDESK_CI_PRINT_DROPIN": "1"}
            out = subprocess.run([str(SETUP), "--dry-run"], env=env, text=True, capture_output=True, check=True).stdout
        self.assertIn("Wants=user@999.service", out)
        self.assertNotIn("@UID@", out)

    def test_restarts_the_runner_only_when_asked(self):
        self.assertNotIn("restart the idle runner", self.run_setup("--dry-run").stdout)
        self.assertIn("restart the idle runner", self.run_setup("--dry-run", "--restart-runner").stdout)

    @unittest.skipIf(os.geteuid() == 0, "needs an unprivileged user")
    def test_apply_refuses_to_run_without_root(self):
        result = self.run_setup("--apply")
        self.assertEqual(2, result.returncode)
        self.assertIn("must run as root", result.stderr)

    def test_unknown_arguments_are_refused(self):
        self.assertEqual(2, self.run_setup("--everything").returncode)


if __name__ == "__main__":
    unittest.main()
