"""Continuous deployment to Contabo staging: contract for .github/workflows/deploy-staging.yml and its call from CI.

Policy (owner, 2026-10-23): every merge into develop that passes every CI gate deploys automatically; unmerged pull
request code is never deployed. The runner reaches the host only through the SSH forced command
deploy/host/lawdesk-deploy-gate, which accepts `deploy <sha>`, `rollback` and `status` and nothing else.
"""

import re
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
CI = REPO_ROOT / ".github" / "workflows" / "ci.yml"
CD = REPO_ROOT / ".github" / "workflows" / "deploy-staging.yml"
PINNED_ACTION = re.compile(r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$")
GATES = {"policy", "backend", "backend-postgres-image", "frontend", "stack", "secrets"}


def load(path):
    with path.open(encoding="utf-8") as f:
        workflow = yaml.safe_load(f)
    workflow["on"] = workflow.pop(True, workflow.get("on"))
    return workflow


def steps(workflow):
    return [step for job in workflow["jobs"].values() for step in job.get("steps", [])]


def scripts(workflow):
    return [step["run"] for step in steps(workflow) if "run" in step]


class CallFromCiTest(unittest.TestCase):
    def setUp(self):
        self.ci = load(CI)
        self.job = self.ci["jobs"].get("deploy-staging")

    def test_deploy_runs_only_after_every_gate_passes(self):
        self.assertIsNotNone(self.job, "ci.yml must have a deploy-staging job")
        self.assertEqual(GATES, set(self.job["needs"]))

    def test_deploy_runs_only_for_pushes_to_develop_in_this_repository(self):
        condition = self.job["if"]
        self.assertIn("github.event_name == 'push'", condition)
        self.assertIn("github.ref == 'refs/heads/develop'", condition)
        self.assertIn("github.repository == 'afawzy70/Lawdesk-v1'", condition)
        self.assertNotIn("pull_request", condition)

    def test_deploy_uses_the_staging_workflow_with_the_pushed_commit(self):
        self.assertEqual("./.github/workflows/deploy-staging.yml", self.job["uses"])
        self.assertEqual("${{ github.sha }}", self.job["with"]["sha"])
        self.assertEqual("inherit", self.job["secrets"])

    def test_a_newer_push_never_cancels_a_running_deployment(self):
        cancel = str(self.ci["concurrency"]["cancel-in-progress"])
        self.assertIn("github.event_name == 'pull_request'", cancel)


class DeployWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.cd = load(CD)
        self.job = self.cd["jobs"]["deploy"]

    def test_only_called_from_ci_or_dispatched_by_hand(self):
        self.assertEqual({"workflow_call", "workflow_dispatch"}, set(self.cd["on"]))
        for trigger in self.cd["on"].values():
            self.assertTrue(trigger["inputs"]["sha"]["required"])

    def test_token_is_read_only(self):
        self.assertEqual({"contents": "read"}, self.cd["permissions"])
        self.assertNotIn("permissions", self.job)

    def test_one_deployment_at_a_time_and_none_is_cancelled(self):
        self.assertEqual("lawdesk-staging-deploy", self.cd["concurrency"]["group"])
        self.assertIs(False, self.cd["concurrency"]["cancel-in-progress"])

    def test_runs_in_the_staging_environment_with_a_timeout(self):
        self.assertEqual("staging", self.job["environment"]["name"])
        self.assertLessEqual(self.job["timeout-minutes"], 30)

    def test_is_switched_on_by_a_repository_variable(self):
        self.assertIn("vars.LAWDESK_STAGING_DEPLOY == 'enabled'", self.job["if"])

    def test_actions_are_pinned(self):
        for step in steps(self.cd):
            if "uses" in step:
                with self.subTest(action=step["uses"]):
                    self.assertRegex(step["uses"], PINNED_ACTION)

    def test_expressions_never_reach_shell_code_directly(self):
        # Inputs and secrets go through env:, so a crafted value cannot become shell syntax.
        for script in scripts(self.cd):
            with self.subTest(script=script[:60]):
                self.assertNotIn("${{", script)

    def test_commit_is_validated_against_develop_before_anything_reaches_the_host(self):
        text = CD.read_text(encoding="utf-8")
        self.assertIn("^[0-9a-f]{40}$", text)
        self.assertIn("/compare/$SHA...develop", text)
        names = [step.get("name", "") for step in steps(self.cd)]
        validate = next(i for i, n in enumerate(names) if n.startswith("Validate the release commit"))
        deploy = next(i for i, n in enumerate(names) if n.startswith("Deploy"))
        self.assertLess(validate, deploy)

    def test_ssh_verifies_the_host_key_and_sends_only_gate_requests(self):
        # The only ssh invocation is the helper "$RUNNER_TEMP/gate" <request>; each call passes a fixed request.
        for script in scripts(self.cd):
            for line in script.splitlines():
                with self.subTest(line=line):
                    if re.search(r"(^|[\s;&|(])ssh\s", line):
                        self.assertTrue(line.strip().startswith("exec ssh "), line)
                        self.assertTrue(line.rstrip().endswith('"$1"'), line)
                    if '"$RUNNER_TEMP/gate" "' in line:
                        self.assertRegex(line, r'"\$RUNNER_TEMP/gate" "(deploy \$SHA|status|rollback)"')
        text = CD.read_text(encoding="utf-8")
        self.assertIn("StrictHostKeyChecking=yes", text)
        self.assertIn("BatchMode=yes", text)
        self.assertNotIn("StrictHostKeyChecking=no", text)

    def test_public_checks_require_a_trusted_certificate(self):
        text = CD.read_text(encoding="utf-8")
        for insecure in ["--insecure", " -k ", "-k\n", "curl -k"]:
            self.assertNotIn(insecure, text)
        self.assertIn("--proto =https", text)

    def test_failed_public_verification_rolls_back(self):
        names = [step.get("name", "") for step in steps(self.cd)]
        self.assertTrue(any(n.startswith("Roll back") for n in names), names)
        rollback = next(s for s in steps(self.cd) if s.get("name", "").startswith("Roll back"))
        self.assertIn("failure()", rollback["if"])

    def test_deployment_result_is_recorded_in_the_job_summary(self):
        self.assertIn("GITHUB_STEP_SUMMARY", CD.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
