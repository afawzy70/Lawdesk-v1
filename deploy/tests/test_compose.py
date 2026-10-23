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
        self.assertEqual({"db", "backend", "web"}, set(self.services))

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
        for name in ["backend", "web"]:
            with self.subTest(service=name):
                self.assertTrue(self.services[name]["read_only"])

    def test_every_service_has_a_healthcheck_memory_limit_and_bounded_logs(self):
        for name, service in self.services.items():
            with self.subTest(service=name):
                self.assertIn("test", service["healthcheck"])
                self.assertIn("mem_limit", service)
                self.assertEqual("json-file", service["logging"]["driver"])
                self.assertIn("max-size", service["logging"]["options"])

    def test_restart_policy(self):
        for name, service in self.services.items():
            with self.subTest(service=name):
                self.assertEqual("unless-stopped", service["restart"])

    def test_database_image_is_pinned_by_version_and_digest(self):
        self.assertRegex(self.services["db"]["image"], r"^postgres:18\.6-alpine[0-9.]*@sha256:[0-9a-f]{64}$")

    def test_application_images_require_an_explicit_release_tag(self):
        for name in ["backend", "web"]:
            with self.subTest(service=name):
                self.assertIn("${LAWDESK_TAG:?", self.services[name]["image"])

    def test_backend_waits_for_a_healthy_database_and_web_for_a_healthy_backend(self):
        self.assertEqual("service_healthy", self.services["backend"]["depends_on"]["db"]["condition"])
        self.assertEqual("service_healthy", self.services["web"]["depends_on"]["backend"]["condition"])

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
        init = (DEPLOY_DIR / "postgres" / "init" / "10-app-role.sh").read_text(encoding="utf-8")
        self.assertIn("NOSUPERUSER", init)
        self.assertIn("NOCREATEROLE", init)
        self.assertIn("NOCREATEDB", init)


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
