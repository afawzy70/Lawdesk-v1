"""LD-006 staging stack contract (QA-0041..QA-0048, see docs/delivery/PHASE-1-ACCEPTANCE.md).

The stack shares a host with Matbakhy, so isolation rules are tested here rather than trusted.
"""

import os
import shutil
import subprocess
import unittest
from pathlib import Path

import yaml

DEPLOY_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = DEPLOY_DIR.parent
COMPOSE = DEPLOY_DIR / "compose.yaml"
# Runs to completion before the backend starts (deploy.sh), so it has no healthcheck and is never restarted.
ONE_SHOT = {"migrate"}


def load():
    with COMPOSE.open(encoding="utf-8") as f:
        return yaml.safe_load(f)


class IsolationTest(unittest.TestCase):
    def setUp(self):
        self.stack = load()
        self.services = self.stack["services"]

    def test_project_name_is_lawdesk_specific(self):
        self.assertEqual("lawdesk-staging", self.stack["name"])

    def test_expected_services(self):
        self.assertEqual({"db", "migrate", "backend", "web"}, set(self.services))

    def test_no_service_publishes_host_ports(self):
        for name, service in self.services.items():
            with self.subTest(service=name):
                self.assertNotIn("ports", service)
                self.assertNotIn("network_mode", service)

    def test_no_fixed_container_names(self):
        for name, service in self.services.items():
            with self.subTest(service=name):
                self.assertNotIn("container_name", service)

    def test_database_and_backend_are_on_an_internal_network_only(self):
        networks = self.stack["networks"]
        self.assertTrue(networks["internal"]["internal"])
        self.assertEqual(["internal"], self.services["db"]["networks"])
        self.assertEqual(["internal"], self.services["backend"]["networks"])

    def test_only_web_joins_the_external_edge_network_shared_with_the_proxy(self):
        edge = self.stack["networks"]["edge"]
        self.assertTrue(edge["external"])
        self.assertEqual("lawdesk-edge", edge["name"])
        self.assertEqual({"internal", "edge"}, set(self.services["web"]["networks"]))
        self.assertEqual(["lawdesk-web"], self.services["web"]["networks"]["edge"]["aliases"])

    def test_no_bind_mounts_outside_the_deploy_directory(self):
        for name, service in self.services.items():
            for volume in service.get("volumes", []):
                source = volume.split(":")[0] if isinstance(volume, str) else volume.get("source", "")
                with self.subTest(service=name, volume=source):
                    self.assertFalse(source.startswith("/"), "absolute host paths are not allowed")
                    self.assertNotIn("..", source)

    def test_named_volumes_are_namespaced_by_the_project(self):
        self.assertEqual({"db-data"}, set(self.stack["volumes"]))


class HardeningTest(unittest.TestCase):
    def setUp(self):
        self.services = load()["services"]

    def test_every_service_drops_privileges(self):
        for name, service in self.services.items():
            with self.subTest(service=name):
                self.assertIn("no-new-privileges:true", service["security_opt"])
                self.assertEqual(["ALL"], service["cap_drop"])

    def test_application_containers_have_read_only_root_filesystems(self):
        for name in ["migrate", "backend", "web"]:
            with self.subTest(service=name):
                self.assertTrue(self.services[name]["read_only"])

    def test_every_service_has_a_healthcheck_memory_limit_and_bounded_logs(self):
        for name, service in self.services.items():
            with self.subTest(service=name):
                if name not in ONE_SHOT:
                    self.assertIn("test", service["healthcheck"])
                self.assertIn("mem_limit", service)
                self.assertEqual("json-file", service["logging"]["driver"])
                self.assertIn("max-size", service["logging"]["options"])

    def test_restart_policy(self):
        for name, service in self.services.items():
            with self.subTest(service=name):
                self.assertEqual("no" if name in ONE_SHOT else "unless-stopped", service["restart"])

    def test_database_image_is_pinned_by_version_and_digest(self):
        self.assertRegex(self.services["db"]["image"], r"^postgres:18\.6-alpine[0-9.]*@sha256:[0-9a-f]{64}$")

    def test_application_images_require_an_explicit_release_tag(self):
        for name in ["backend", "web"]:
            with self.subTest(service=name):
                self.assertIn("${LAWDESK_TAG:?", self.services[name]["image"])

    def test_backend_waits_for_a_healthy_database_and_web_for_a_healthy_backend(self):
        self.assertEqual("service_healthy", self.services["backend"]["depends_on"]["db"]["condition"])
        self.assertEqual("service_healthy", self.services["web"]["depends_on"]["backend"]["condition"])

    def test_backend_records_the_deployed_commit(self):
        # app_release rows and the start log line identify the deployment by its commit (Codex review, 020154c).
        self.assertTrue(self.services["backend"]["environment"]["LAWDESK_RELEASE"].startswith("${LAWDESK_TAG:?"))

    def test_backend_health_uses_readiness(self):
        self.assertIn("/actuator/health/readiness", " ".join(self.services["backend"]["healthcheck"]["test"]))


class SecretsTest(unittest.TestCase):
    def setUp(self):
        self.stack = load()

    def test_passwords_come_from_secret_files_not_environment_values(self):
        for name, service in self.stack["services"].items():
            for key, value in (service.get("environment") or {}).items():
                with self.subTest(service=name, key=key):
                    if "PASSWORD" in key.upper():
                        self.assertTrue(key.endswith("_FILE"), f"{key} must be a *_FILE reference")
                        self.assertTrue(str(value).startswith("/run/secrets/"))

    def test_secret_files_live_in_an_ignored_directory(self):
        for name, secret in self.stack["secrets"].items():
            with self.subTest(secret=name):
                # On the host the directory is LAWDESK_SECRETS_DIR, outside every release checkout.
                self.assertTrue(secret["file"].startswith("${LAWDESK_SECRETS_DIR:-./secrets}/"), secret["file"])
        result = subprocess.run(["git", "check-ignore", "-q", "deploy/secrets/example.txt"], cwd=REPO_ROOT,
                                check=False)
        self.assertEqual(0, result.returncode, "deploy/secrets/ must be git-ignored")

    def test_backend_runs_as_a_least_privilege_database_role(self):
        backend = self.stack["services"]["backend"]
        self.assertEqual("lawdesk_app", backend["environment"]["SPRING_DATASOURCE_USERNAME"])
        self.assertNotEqual("postgres", backend["environment"]["SPRING_DATASOURCE_USERNAME"])
        role = (DEPLOY_DIR / "postgres" / "app-role.sql").read_text(encoding="utf-8")
        self.assertIn("NOSUPERUSER", role)
        self.assertIn("NOCREATEROLE", role)
        self.assertIn("NOCREATEDB", role)

    def test_the_first_administrator_password_is_a_secret_file_never_an_environment_value(self):
        # LD-008.4: the backend bootstraps the first administrator from this file only while no user exists.
        backend = self.stack["services"]["backend"]
        self.assertIn("admin_bootstrap_password", [s if isinstance(s, str) else s["source"] for s in backend["secrets"]])
        env = backend["environment"]
        self.assertEqual("/run/secrets/admin_bootstrap_password", env["LAWDESK_BOOTSTRAP_ADMIN_PASSWORD_FILE"])
        self.assertIn("LAWDESK_BOOTSTRAP_ADMIN_USERNAME", env)
        self.assertIn("LAWDESK_BOOTSTRAP_ADMIN_EMAIL", env)
        self.assertFalse(any("PASSWORD" in k and not k.endswith("_FILE") for k in env), env)
        self.assertEqual("${LAWDESK_SECRETS_DIR:-./secrets}/admin_bootstrap_password.txt",
                         self.stack["secrets"]["admin_bootstrap_password"]["file"])

    def test_database_does_not_depend_on_host_file_permissions_for_initialisation(self):
        # Contabo first install: a bind-mounted init directory unpacked under umask 027 was unreadable for the postgres
        # user, so the role was never created. deploy.sh ensures the role through `docker compose exec` instead.
        volumes = self.stack["services"]["db"]["volumes"]
        self.assertFalse(any("docker-entrypoint-initdb.d" in v for v in volumes), volumes)


def secret_sources(service):
    return {s if isinstance(s, str) else s["source"] for s in service.get("secrets", [])}


class SeparatedCredentialsTest(unittest.TestCase):
    """M1-pre (T-DB-13): schema ownership and the runtime are different credentials, and the backend never holds the owner's."""

    def setUp(self):
        self.stack = load()
        self.services = self.stack["services"]

    def test_the_backend_holds_no_owner_admin_or_operator_secret(self):
        self.assertEqual({"db_app_password", "admin_bootstrap_password"}, secret_sources(self.services["backend"]))
        self.assertEqual(set(), secret_sources(self.services["web"]))
        self.assertFalse(any("operator" in name for name in self.stack["secrets"]), "operator credentials never enter compose")

    def test_only_the_migrate_job_connects_as_the_owner(self):
        migrate = self.services["migrate"]
        self.assertEqual("lawdesk_owner", migrate["environment"]["SPRING_DATASOURCE_USERNAME"])
        self.assertEqual({"db_owner_password"}, secret_sources(migrate))
        self.assertEqual([{"source": "db_owner_password", "target": "spring.datasource.password"}], migrate["secrets"])
        self.assertEqual(["migrate"], migrate["command"])
        self.assertEqual(["migrate"], migrate["profiles"], "never started by `up`: deploy.sh runs it explicitly")
        self.assertEqual(self.services["backend"]["image"], migrate["image"], "migrations of the release being started")
        self.assertEqual(["internal"], migrate["networks"])
        for name, service in self.services.items():
            if name != "migrate":
                with self.subTest(service=name):
                    self.assertNotEqual("lawdesk_owner", (service.get("environment") or {}).get("SPRING_DATASOURCE_USERNAME"))

    def test_the_backend_never_runs_migrations(self):
        self.assertEqual("false", self.services["backend"]["environment"]["SPRING_FLYWAY_ENABLED"])

    def test_the_database_receives_the_owner_password_only_to_set_it(self):
        self.assertIn("db_owner_password", secret_sources(self.services["db"]))
        self.assertEqual("${LAWDESK_SECRETS_DIR:-./secrets}/db_owner_password.txt", self.stack["secrets"]["db_owner_password"]["file"])


class ProvisioningScriptsTest(unittest.TestCase):
    def read(self, name):
        return (DEPLOY_DIR / "postgres" / name).read_text(encoding="utf-8")

    def test_no_script_carries_a_password_or_drops_anything(self):
        for name in ["database.sql", "roles.sql", "ownership.sql", "passwords.sql", "audit.sql"]:
            with self.subTest(script=name):
                sql = "\n".join(line for line in self.read(name).splitlines() if not line.lstrip().startswith("--"))
                self.assertNotRegex(sql, r"(?i)password\s+'")
                self.assertNotRegex(sql, r"(?i)\bdrop\s+(table|schema|database|owned|column)\b")

    def test_passwords_are_read_inside_the_database_container(self):
        sql = self.read("passwords.sql")
        self.assertIn("/run/secrets/db_app_password", sql)
        self.assertIn("/run/secrets/db_owner_password", sql)

    def test_the_runtime_roles_get_no_power_attributes(self):
        roles = self.read("roles.sql")
        for role in ["lawdesk_owner", "lawdesk_dml", "lawdesk_app", "lawdesk_legal", "lawdesk_operator"]:
            with self.subTest(role=role):
                self.assertRegex(roles, rf"ALTER ROLE {role} [^;]*NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS")


@unittest.skipUnless(shutil.which("docker"), "docker CLI not available")
class RenderTest(unittest.TestCase):
    def test_compose_file_renders_with_example_environment(self):
        env = dict(os.environ, LAWDESK_REGISTRY="ghcr.io/example/lawdesk", LAWDESK_TAG="0" * 40)
        result = subprocess.run(
            ["docker", "compose", "-f", str(COMPOSE), "--env-file", str(DEPLOY_DIR / "staging.env.example"),
             "config", "--quiet"],
            cwd=DEPLOY_DIR, env=env, capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_rendering_without_a_release_tag_fails(self):
        env = {k: v for k, v in os.environ.items() if k != "LAWDESK_TAG"}
        env["LAWDESK_REGISTRY"] = "ghcr.io/example/lawdesk"
        result = subprocess.run(["docker", "compose", "-f", str(COMPOSE), "config", "--quiet"],
                                cwd=DEPLOY_DIR, env=env, capture_output=True, text=True, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("LAWDESK_TAG", result.stderr)


if __name__ == "__main__":
    unittest.main()
