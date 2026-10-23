"""Continuous deployment to Contabo staging: contract for .github/workflows/deploy-staging.yml and its call from CI.

Policy (owner, 2026-10-23): every merge into develop that passes every CI gate deploys automatically; unmerged pull
request code is never deployed. The runner reaches the host only through the SSH forced command
deploy/host/lawdesk-deploy-gate, which accepts `deploy <sha>`, `rollback` and `status` and nothing else.
"""

import json
import os
import re
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
CI = REPO_ROOT / ".github" / "workflows" / "ci.yml"
CD = REPO_ROOT / ".github" / "workflows" / "deploy-staging.yml"
PINNED_ACTION = re.compile(r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$")
GATES = {"policy", "backend", "backend-postgres-image", "frontend", "stack", "secrets"}
# Push-only check that develop moved by exactly one QA-cleared pull request merge (tools/develop_guard.py, B4).
GUARD = "develop-guard"
SHA = "e5db1904666bdf45b470c30efa4a662c1cf4709b"

# Plays the GitHub API for the "Validate the release commit" step: answers compare and check-runs from fixtures, applies
# --jq with the real jq, and records each call.
FAKE_GH = textwrap.dedent("""\
    #!/usr/bin/env python3
    import json, os, subprocess, sys
    args = sys.argv[1:]
    with open(os.environ["FAKE_GH_LOG"], "a") as f:
        f.write(" ".join(args) + "\\n")
    path = args[1]
    if "/compare/" in path:
        body = {"status": os.environ["FAKE_COMPARE"]}
    elif "/check-runs" in path:
        body = json.loads(os.environ["FAKE_CHECK_RUNS"])
    else:
        sys.exit(1)
    text = json.dumps(body)
    if "--jq" in args:
        text = subprocess.run(["jq", "-r", args[args.index("--jq") + 1]], input=text, text=True, check=True,
                              capture_output=True).stdout
    sys.stdout.write(text)
    """)


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

    def test_deploy_runs_only_after_every_gate_and_the_develop_guard_pass(self):
        self.assertIsNotNone(self.job, "ci.yml must have a deploy-staging job")
        self.assertEqual(GATES | {GUARD}, set(self.job["needs"]))

    def test_develop_guard_checks_every_push_to_develop(self):
        guard = self.ci["jobs"][GUARD]
        self.assertIn("github.event_name == 'push'", guard["if"])
        self.assertIn("github.ref == 'refs/heads/develop'", guard["if"])
        # contents: the guard file at the parent; pull-requests: the merged PR and its comments; checks: the parent's
        # guard run (the check-runs endpoint needs Checks read on a private repository).
        self.assertEqual({"contents": "read", "pull-requests": "read", "checks": "read"}, guard["permissions"])
        script = "\n".join(s["run"] for s in guard["steps"] if "run" in s)
        self.assertIn("tools/develop_guard.py", script)
        self.assertNotIn("${{", script)
        env = {k: v for s in guard["steps"] for k, v in s.get("env", {}).items()}
        self.assertEqual("${{ github.event.before }}", env["BEFORE"])
        self.assertEqual("${{ github.event.forced }}", env["FORCED"])
        # The owner chooses the policy with a repository variable; unset means the strict QA policy.
        self.assertEqual("${{ vars.LAWDESK_DEVELOP_POLICY || 'qa' }}", env["POLICY"])
        self.assertIn('--policy "$POLICY"', script)

    def test_deploy_runs_only_for_pushes_to_develop_in_this_repository(self):
        condition = self.job["if"]
        self.assertIn("github.event_name == 'push'", condition)
        self.assertIn("github.ref == 'refs/heads/develop'", condition)
        self.assertIn("github.repository == 'afawzy70/Lawdesk-v1'", condition)
        self.assertNotIn("pull_request", condition)

    def test_deploy_job_may_read_check_results_and_nothing_more(self):
        # The called workflow reads the commit's check runs for hand-dispatched redeploys (QAD-016).
        self.assertEqual({"contents": "read", "checks": "read"}, self.job["permissions"])

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
        self.assertEqual({"contents": "read", "checks": "read"}, self.cd["permissions"])
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


def check_runs(*passed, failed=(), app="github-actions"):
    runs = [{"name": n, "conclusion": "success", "app": {"slug": app}} for n in passed]
    runs += [{"name": n, "conclusion": "failure", "app": {"slug": app}} for n in failed]
    return json.dumps({"total_count": len(runs), "check_runs": runs})


class ValidateReleaseStepTest(unittest.TestCase):
    """Runs the real "Validate the release commit" script against a fake GitHub API (QAD-016)."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        bin_dir = root / "bin"
        bin_dir.mkdir()
        (bin_dir / "gh").write_text(FAKE_GH, encoding="utf-8")
        (bin_dir / "gh").chmod(0o755)
        self.gh_log = root / "gh.log"
        self.gh_log.touch()
        self.output = root / "output"
        self.output.touch()
        step = next(s for s in steps(load(CD)) if s.get("name", "").startswith("Validate the release commit"))
        self.script = step["run"]
        ci = load(CI)
        self.gate_names = [ci["jobs"][job]["name"] for job in sorted(GATES | {GUARD})]
        self.env = {"PATH": f"{bin_dir}:/usr/bin:/bin", "HOME": str(root), "GITHUB_OUTPUT": str(self.output),
                    "SHA": SHA, "REPO": "afawzy70/Lawdesk-v1", "GH_TOKEN": "x", "EVENT": "workflow_dispatch",
                    "REF": "refs/heads/develop", "FAKE_GH_LOG": str(self.gh_log), "FAKE_COMPARE": "identical",
                    "FAKE_CHECK_RUNS": check_runs(*self.gate_names)}

    def tearDown(self):
        self.tmp.cleanup()

    def validate(self, **extra):
        return subprocess.run(["bash", "-e", "-c", self.script], env={**self.env, **extra}, capture_output=True,
                              text=True, check=False, timeout=30)

    def outputs(self):
        return self.output.read_text(encoding="utf-8")

    def test_dispatch_of_a_merged_commit_whose_gates_all_passed_deploys(self):
        result = self.validate()

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("skip=false", self.outputs())
        self.assertIn(f"repos/afawzy70/Lawdesk-v1/commits/{SHA}/check-runs", self.gh_log.read_text(encoding="utf-8"))

    def test_dispatch_is_refused_when_a_gate_failed_or_never_ran_for_that_commit(self):
        fixtures = {
            "failed": check_runs(*self.gate_names[1:], failed=self.gate_names[:1]),
            "missing": check_runs(*self.gate_names[1:]),
            "none": check_runs(),
            "other app": check_runs(*self.gate_names, app="impostor"),
        }
        for label, runs in fixtures.items():
            with self.subTest(label):
                self.output.write_text("", encoding="utf-8")
                result = self.validate(FAKE_CHECK_RUNS=runs)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertNotIn("skip=false", self.outputs())

    def test_a_gate_that_failed_once_and_passed_on_rerun_counts_as_passed(self):
        runs = check_runs(*self.gate_names, failed=self.gate_names[:1])

        self.assertEqual(0, self.validate(FAKE_CHECK_RUNS=runs).returncode)

    def test_dispatch_from_any_branch_but_develop_is_refused(self):
        for ref in ["refs/heads/feature/x", "refs/heads/main", "refs/tags/v1", ""]:
            with self.subTest(ref=ref):
                result = self.validate(REF=ref)
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("skip=false", self.outputs())

    def test_commit_not_on_develop_is_refused(self):
        for status in ["behind", "diverged", ""]:
            with self.subTest(status=status):
                self.assertNotEqual(0, self.validate(FAKE_COMPARE=status).returncode)

    def test_call_from_ci_relies_on_the_gates_it_needs(self):
        # The calling CI run is still in progress; its deploy job runs only after every gate passed (needs:).
        result = self.validate(EVENT="workflow_call", FAKE_CHECK_RUNS=check_runs())

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("skip=false", self.outputs())

    def test_call_for_a_superseded_commit_is_skipped(self):
        result = self.validate(EVENT="workflow_call", FAKE_COMPARE="ahead")

        self.assertEqual(0, result.returncode)
        self.assertIn("skip=true", self.outputs())

    def test_required_gates_are_exactly_the_ci_gate_jobs(self):
        for name in self.gate_names:
            self.assertIn(name, self.script)


if __name__ == "__main__":
    unittest.main()
