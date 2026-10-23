"""LD-005 CI quality-gate contract tests (QA-0033..QA-0040, see docs/delivery/PHASE-1-ACCEPTANCE.md).

These pin down what the CI workflow must enforce, so weakening a gate fails review in CI itself.
"""

import re
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "ci.yml"
PINNED_ACTION = re.compile(r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$")


def load_workflow():
    with WORKFLOW.open(encoding="utf-8") as f:
        workflow = yaml.safe_load(f)
    # PyYAML reads the bare key `on` as boolean True.
    workflow["on"] = workflow.pop(True, workflow.get("on"))
    return workflow


def run_commands(job):
    return "\n".join(step.get("run", "") for step in job.get("steps", []))


def uses(job):
    return [step["uses"] for step in job.get("steps", []) if "uses" in step]


class TriggerTest(unittest.TestCase):
    def test_runs_on_pull_requests_and_pushes_to_integration_branches(self):
        on = load_workflow()["on"]
        self.assertIn("pull_request", on)
        self.assertEqual({"develop", "main"}, set(on["pull_request"]["branches"]))
        self.assertTrue({"develop", "main"}.issubset(set(on["push"]["branches"])))


class SecurityPostureTest(unittest.TestCase):
    def test_token_is_read_only_by_default(self):
        self.assertEqual({"contents": "read"}, load_workflow()["permissions"])

    def test_every_third_party_action_is_pinned_to_a_commit_sha(self):
        for name, job in load_workflow()["jobs"].items():
            for action in uses(job):
                with self.subTest(job=name, action=action):
                    self.assertRegex(action.split(" ")[0], PINNED_ACTION)

    def test_jobs_have_timeouts(self):
        for name, job in load_workflow()["jobs"].items():
            with self.subTest(job=name):
                self.assertLessEqual(job.get("timeout-minutes", 999), 30)

    def test_pull_request_target_is_never_used(self):
        self.assertNotIn("pull_request_target", load_workflow()["on"])


class RequiredGatesTest(unittest.TestCase):
    def setUp(self):
        self.jobs = load_workflow()["jobs"]

    def test_repository_policy_gate(self):
        commands = run_commands(self.jobs["policy"])
        self.assertIn("unittest discover -s tools/tests", commands)
        self.assertIn("unittest discover -s deploy/tests", commands)
        self.assertIn("repo_policy.py pr", commands)
        self.assertIn("repo_policy.py commit-msg", commands)
        self.assertIn("repo_policy.py layout", commands)
        self.assertIn("actionlint", commands)
        self.assertIn("shellcheck", commands)

    def test_backend_gate_runs_full_maven_verify_on_java_21(self):
        job = self.jobs["backend"]
        self.assertIn("./mvnw -B -ntp verify", run_commands(job))
        java = next(s for s in job["steps"] if "setup-java" in s.get("uses", ""))
        self.assertEqual("21", str(java["with"]["java-version"]))

    def test_backend_suite_also_runs_against_the_official_postgres_image(self):
        job = self.jobs["backend-postgres-image"]
        self.assertRegex(job["services"]["postgres"]["image"], r"^postgres:18\.6(-[a-z0-9.]+)?$")
        self.assertIn("LAWDESK_TEST_DB_URL", job["env"])
        self.assertIn("./mvnw -B -ntp test", run_commands(job))

    def test_frontend_gate_runs_lint_typecheck_tests_with_coverage_and_build(self):
        commands = run_commands(self.jobs["frontend"])
        for command in ["npm ci", "npm run lint", "npm run typecheck", "npm run test:coverage", "npm run build",
                        "npm audit --omit=dev --audit-level=high"]:
            with self.subTest(command=command):
                self.assertIn(command, commands)

    def test_secret_scan_gate_covers_full_history(self):
        job = self.jobs["secrets"]
        checkout = next(s for s in job["steps"] if "actions/checkout" in s.get("uses", ""))
        self.assertEqual(0, checkout["with"]["fetch-depth"])
        self.assertRegex(run_commands(job), r'gitleaks"? git ')

    def test_stack_gate_builds_images_and_runs_the_compose_stack(self):
        commands = run_commands(self.jobs["stack"])
        self.assertIn("docker build -f backend/Dockerfile", commands)
        self.assertIn("docker build -f frontend/Dockerfile", commands)
        self.assertIn("docker network create lawdesk-edge", commands)
        self.assertIn("deploy/scripts/smoke.sh", commands)
        self.assertIn("stop db", commands, "readiness must be checked through a database outage and recovery")
        self.assertIn("app_release where version = '$LAWDESK_TAG'", commands,
                      "the stack must record the commit it runs")

    def test_downloaded_tools_are_checksum_verified(self):
        for name in ["policy", "secrets"]:
            with self.subTest(job=name):
                self.assertIn("sha256sum --check", run_commands(self.jobs[name]))


if __name__ == "__main__":
    unittest.main()
