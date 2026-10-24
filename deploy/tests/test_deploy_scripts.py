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
SMOKE = DEPLOY_DIR / "scripts" / "smoke.sh"

SHA_A = "a" * 40
SHA_B = "b" * 40
SHA_C = "c" * 40

FAKE_DOCKER = textwrap.dedent("""\
    #!/bin/sh
    # Records each call with the release tag it saw; fails on demand.
    echo "LAWDESK_TAG=${LAWDESK_TAG:-} $*" | tr '\n' ' ' >> "$FAKE_DOCKER_LOG"
    echo >> "$FAKE_DOCKER_LOG"
    case "$*" in
      "image inspect "*)
        case "$*" in *":${FAKE_MISSING_IMAGE_TAG:-none}"*) exit 1 ;; esac
        # With a format, answer the revision label: the image's own tag unless told otherwise.
        for last in "$@"; do :; done
        case "$*" in *" -f "*|*" --format "*)
          if [ "${last##*:}" = "${FAKE_WRONG_REVISION_TAG:-none}" ]; then echo "${FAKE_OTHER_REVISION:-}"; else echo "${last##*:}"; fi ;;
        esac ;;
      *" up "*) [ "${LAWDESK_TAG:-}" = "${FAKE_FAIL_UP_TAG:-none}" ] && exit 1 ;;
      *" exec -T db pg_dump "*) [ -n "${FAKE_FAIL_DUMP:-}" ] && exit 1; echo "DUMP-OF-${LAWDESK_TAG:-}" ;;
      *" exec -T db psql "*) cat > /dev/null; [ "${LAWDESK_TAG:-}" = "${FAKE_FAIL_ROLE_TAG:-none}" ] && exit 1 ;;
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


class DatabaseRoleTest(DeployScriptTestCase):
    """Contabo first install: the init script that creates lawdesk_app was not readable by the postgres user (release
    unpacked under umask 027), so the database came up healthy without the role and the backend restarted forever.
    The role is now ensured on every start, before the backend, idempotently and without recreating the volume."""

    def starts(self, tag):
        return [c for c in self.calls() if c.startswith(f"LAWDESK_TAG={tag}") and (" up " in c or " exec -T db psql" in c)]

    def test_database_starts_first_then_the_role_is_ensured_then_the_stack(self):
        result = self.run_script(DEPLOY, SHA_A)

        self.assertEqual(0, result.returncode, result.stderr)
        steps = self.starts(SHA_A)
        self.assertEqual(3, len(steps), steps)
        self.assertIn(" up -d --wait --wait-timeout 240 db", steps[0])
        self.assertIn(" exec -T db psql -v ON_ERROR_STOP=1 -U postgres -d postgres", steps[1])
        self.assertRegex(steps[2], r" up -d --wait --wait-timeout 240\s*$")

    def test_failed_role_setup_fails_the_release_and_rolls_back(self):
        self.run_script(DEPLOY, SHA_A)

        result = self.run_script(DEPLOY, SHA_B, FAKE_FAIL_ROLE_TAG=SHA_B)

        self.assertEqual(1, result.returncode)
        self.assertEqual(SHA_A, self.current())
        self.assertFalse(any(" up -d --wait --wait-timeout 240" in c and not c.rstrip().endswith(" db")
                             for c in self.starts(SHA_B)), self.starts(SHA_B))

    def test_rollback_target_gets_its_role_ensured_too(self):
        self.run_script(DEPLOY, SHA_A)
        before = len(self.calls())

        self.run_script(DEPLOY, SHA_B, FAKE_FAIL_UP_TAG=SHA_B)

        self.assertTrue(any(c.startswith(f"LAWDESK_TAG={SHA_A}") and " exec -T db psql" in c
                            for c in self.calls()[before:]))

    def test_role_sql_is_idempotent_and_reads_the_password_inside_the_container(self):
        sql = (DEPLOY_DIR / "postgres" / "app-role.sql").read_text(encoding="utf-8")
        self.assertIn("/run/secrets/db_app_password", sql)
        self.assertIn("WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'lawdesk_app')", sql)
        self.assertIn("CREATE SCHEMA IF NOT EXISTS lawdesk AUTHORIZATION lawdesk_app", sql)
        self.assertRegex(sql, r"ALTER ROLE lawdesk_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE[^;]*PASSWORD :'app_password'")
        self.assertNotIn("DROP", sql.upper())


class AdminSecretTest(DeployScriptTestCase):
    """LD-008.4: the first administrator's password is generated on the host once, never committed or printed."""

    def setUp(self):
        super().setUp()
        self.secrets = Path(self.tmp.name) / "secrets"
        self.secrets.mkdir(mode=0o700)
        self.env["LAWDESK_SECRETS_DIR"] = str(self.secrets)

    def test_a_missing_password_file_is_generated_once_before_the_stack_starts(self):
        result = self.run_script(DEPLOY, SHA_A)

        self.assertEqual(0, result.returncode, result.stderr)
        secret = self.secrets / "admin_bootstrap_password.txt"
        password = secret.read_text(encoding="utf-8").strip()
        self.assertGreaterEqual(len(password), 20)
        self.assertRegex(password, r"^[A-Za-z0-9]+$")
        self.assertEqual(0o644, secret.stat().st_mode & 0o777, "readable by the backend user; the 0700 directory guards it")
        self.assertNotIn(password, result.stdout + result.stderr + self.history())

        self.run_script(DEPLOY, SHA_B)

        self.assertEqual(password, secret.read_text(encoding="utf-8").strip(), "never regenerated")

    def test_an_existing_password_file_is_kept(self):
        secret = self.secrets / "admin_bootstrap_password.txt"
        secret.write_text("chosen by the operator 123\n", encoding="utf-8")

        self.run_script(DEPLOY, SHA_A)

        self.assertEqual("chosen by the operator 123\n", secret.read_text(encoding="utf-8"))


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


class LocalImagesTest(DeployScriptTestCase):
    """Images built on the host (no registry): nothing is pulled, and a release without images is refused."""

    def test_local_images_are_checked_instead_of_pulled(self):
        result = self.run_script(DEPLOY, SHA_A, LAWDESK_IMAGE_SOURCE="local")

        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls()
        self.assertFalse(any(" pull" in c for c in calls), calls)
        inspects = [c for c in calls if " image inspect " in c]
        self.assertTrue(any(c.rstrip().endswith(f" ghcr.io/example/lawdesk/backend:{SHA_A}") for c in inspects), calls)
        self.assertTrue(any(c.rstrip().endswith(f" ghcr.io/example/lawdesk/web:{SHA_A}") for c in inspects), calls)

    def test_release_without_built_images_is_refused_before_touching_the_stack(self):
        self.run_script(DEPLOY, SHA_A, LAWDESK_IMAGE_SOURCE="local")
        before = len(self.calls())

        result = self.run_script(DEPLOY, SHA_B, LAWDESK_IMAGE_SOURCE="local", FAKE_MISSING_IMAGE_TAG=SHA_B)

        self.assertEqual(1, result.returncode)
        self.assertIn("not built", result.stderr)
        self.assertFalse(any(" up " in c for c in self.calls()[before:]), self.calls()[before:])
        self.assertEqual(SHA_A, self.current())

    def test_local_image_labelled_with_another_revision_is_refused(self):
        # QAD-017: the tag alone does not prove which code an image holds; its revision label must match.
        self.run_script(DEPLOY, SHA_A, LAWDESK_IMAGE_SOURCE="local")
        before = len(self.calls())

        for other in [SHA_C, ""]:
            with self.subTest(other=other):
                result = self.run_script(DEPLOY, SHA_B, LAWDESK_IMAGE_SOURCE="local", FAKE_WRONG_REVISION_TAG=SHA_B,
                                         FAKE_OTHER_REVISION=other)

                self.assertEqual(1, result.returncode)
                self.assertIn("revision", result.stderr)
                self.assertFalse(any(" up " in c for c in self.calls()[before:]), self.calls()[before:])
                self.assertEqual(SHA_A, self.current())

    def test_rollback_target_with_a_wrong_revision_label_is_not_started(self):
        self.run_script(DEPLOY, SHA_A, LAWDESK_IMAGE_SOURCE="local")
        before = len(self.calls())

        result = self.run_script(DEPLOY, SHA_B, LAWDESK_IMAGE_SOURCE="local", FAKE_FAIL_UP_TAG=SHA_B,
                                 FAKE_WRONG_REVISION_TAG=SHA_A, FAKE_OTHER_REVISION=SHA_C)

        self.assertEqual(4, result.returncode, result.stderr)
        self.assertFalse(any(c.startswith(f"LAWDESK_TAG={SHA_A}") and " up " in c for c in self.calls()[before:]))

    def test_unknown_image_source_is_a_usage_error(self):
        result = self.run_script(DEPLOY, SHA_A, LAWDESK_IMAGE_SOURCE="somewhere")

        self.assertEqual(2, result.returncode)
        self.assertEqual([], self.calls())


class BackupTest(DeployScriptTestCase):
    """The database is dumped before a new release starts, so a bad forward migration can be recovered by hand."""

    def setUp(self):
        super().setUp()
        self.backups = Path(self.tmp.name) / "backups"
        self.env["LAWDESK_BACKUP_DIR"] = str(self.backups)

    def test_first_deploy_has_nothing_to_back_up(self):
        result = self.run_script(DEPLOY, SHA_A)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(any("pg_dump" in c for c in self.calls()))

    def test_database_is_dumped_before_the_next_release_starts(self):
        self.run_script(DEPLOY, SHA_A)

        result = self.run_script(DEPLOY, SHA_B)

        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls()
        dump = next(i for i, c in enumerate(calls) if "exec -T db pg_dump" in c)
        first_up_of_b = next(i for i, c in enumerate(calls) if c.startswith(f"LAWDESK_TAG={SHA_B}") and " up " in c)
        self.assertLess(dump, first_up_of_b)
        dumps = list(self.backups.glob(f"*-{SHA_A}.dump"))
        self.assertEqual(1, len(dumps), list(self.backups.iterdir()))
        self.assertIn("DUMP-OF-", dumps[0].read_text(encoding="utf-8"))
        self.assertEqual(0o600, dumps[0].stat().st_mode & 0o777)

    def test_failed_backup_stops_the_deployment_and_keeps_the_running_release(self):
        self.run_script(DEPLOY, SHA_A)
        before = len(self.calls())

        result = self.run_script(DEPLOY, SHA_B, FAKE_FAIL_DUMP="1")

        self.assertEqual(1, result.returncode)
        self.assertIn("backup failed", result.stderr)
        self.assertFalse(any(" up " in c for c in self.calls()[before:]), self.calls()[before:])
        self.assertEqual(SHA_A, self.current())
        self.assertEqual([], list(self.backups.glob("*.partial")))

    def test_only_the_newest_backups_are_kept(self):
        self.backups.mkdir()
        for i in range(12):
            old = self.backups / f"20000101T0000{i:02d}Z-{SHA_C}.dump"
            old.write_text("old", encoding="utf-8")
            os.utime(old, (946684800 + i, 946684800 + i))
        self.run_script(DEPLOY, SHA_A)

        self.run_script(DEPLOY, SHA_B)

        dumps = sorted(p.name for p in self.backups.glob("*.dump"))
        self.assertEqual(10, len(dumps))
        self.assertTrue(any(SHA_A in name for name in dumps))


class ReleaseDirectoriesTest(DeployScriptTestCase):
    """On the host each release is unpacked into its own directory; a rollback uses that release's compose file."""

    def setUp(self):
        super().setUp()
        self.releases = Path(self.tmp.name) / "releases"
        for sha in [SHA_A, SHA_B]:
            (self.releases / sha / "deploy").mkdir(parents=True)
            (self.releases / sha / "deploy" / "compose.yaml").write_text("services: {}\n", encoding="utf-8")
        self.env["LAWDESK_RELEASES_DIR"] = str(self.releases)

    def test_each_release_starts_with_its_own_compose_file(self):
        self.run_script(DEPLOY, SHA_A)
        self.run_script(DEPLOY, SHA_B, FAKE_FAIL_UP_TAG=SHA_B)

        for call in self.calls():
            tag = call.split()[0].removeprefix("LAWDESK_TAG=")
            if " up " in call:
                with self.subTest(call=call):
                    self.assertIn(str(self.releases / tag / "deploy" / "compose.yaml"), call)

    def test_rollback_is_smoke_tested_with_the_target_release_own_contract(self):
        # QAD-021: after B fails, A is started with A's compose file and must be verified with A's, not B's.
        for sha in [SHA_A, SHA_B]:
            scripts = self.releases / sha / "deploy" / "scripts"
            scripts.mkdir()
            (scripts / "smoke.sh").write_text(SMOKE.read_text(encoding="utf-8"), encoding="utf-8")
            (scripts / "smoke.sh").chmod(0o755)
        self.run_script(DEPLOY, SHA_A)

        for failure in [{"FAKE_FAIL_UP_TAG": SHA_B}, {"FAKE_FAIL_SMOKE_TAG": SHA_B}]:
            with self.subTest(failure=failure):
                before = len(self.calls())
                result = self.run_script(DEPLOY, SHA_B, **failure)

                self.assertEqual(1, result.returncode, result.stderr)
                rollback = [c for c in self.calls()[before:] if c.startswith(f"LAWDESK_TAG={SHA_A}")]
                self.assertTrue(any(" exec -T web " in c for c in rollback), rollback)
                for call in rollback:
                    with self.subTest(call=call):
                        self.assertIn(str(self.releases / SHA_A / "deploy" / "compose.yaml"), call)

    def test_secrets_and_environment_file_come_from_the_host_configuration(self):
        env_file = Path(self.tmp.name) / "staging.env"
        env_file.write_text("LAWDESK_REGISTRY=x\n", encoding="utf-8")

        self.run_script(DEPLOY, SHA_A, LAWDESK_ENV_FILE=str(env_file))

        self.assertTrue(all(f"--env-file {env_file}" in c for c in self.calls()), self.calls())


class SafetyTest(DeployScriptTestCase):
    def test_every_docker_call_is_scoped_to_the_lawdesk_project_and_compose_file(self):
        self.run_script(DEPLOY, SHA_A)
        self.run_script(DEPLOY, SHA_B, FAKE_FAIL_UP_TAG=SHA_B)
        self.run_script(DEPLOY, SHA_C)
        self.run_script(ROLLBACK)

        self.run_script(DEPLOY, SHA_A, LAWDESK_IMAGE_SOURCE="local")

        calls = self.calls()
        self.assertTrue(calls)
        for call in calls:
            with self.subTest(call=call):
                if " image inspect " in call:
                    continue  # read-only lookup of this release's own images
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
