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
            if "uses" in job:
                continue  # a reusable-workflow call; its jobs carry their own timeouts (test_cd_workflow.py)
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
        # The container is started by a step, not a `services:` block: a service port is published on every interface of
        # the shared host, and this database has a known throwaway password (see docs/operations/CI-RUNNER.md).
        job = self.jobs["backend-postgres-image"]
        self.assertNotIn("services", job)
        self.assertIn("LAWDESK_TEST_DB_URL", run_commands(job) + str(job.get("env", {})))
        start = next(s for s in job["steps"] if s.get("name", "").startswith("Start the official PostgreSQL"))
        self.assertRegex(start["run"], r"postgres:18\.6(-[a-z0-9.]+)?\b")
        self.assertIn("-p 127.0.0.1::5432", start["run"], "the database must listen on the loopback interface only")
        self.assertIn("LAWDESK_TEST_DB_URL=", start["run"])
        names = [step.get("name", "") for step in job["steps"]]
        test = next(i for i, n in enumerate(names) if n.startswith("Test against the official image"))
        self.assertLess(names.index(start["name"]), test)
        self.assertIn("./mvnw -B -ntp test", job["steps"][test]["run"])
        stop = job["steps"][-1]
        self.assertEqual("always()", stop.get("if"), "the container is removed whatever the test result")
        self.assertIn("docker rm -f", stop["run"])

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

    def test_stack_gate_runs_host_mode_deploys_and_the_rollback_drill_on_real_images(self):
        # Real-container evidence for the staging recovery procedure, on a disposable runner rather than the shared
        # host: images built like the gate builds them, the same SHA deployed twice, then the operator's drill.
        job = self.jobs["stack"]
        commands = run_commands(job)
        self.assertEqual("lawdesk-local", job["env"]["LAWDESK_REGISTRY"], "the drill and gate use lawdesk-local")
        self.assertEqual(2, commands.count('--label "org.opencontainers.image.revision=$LAWDESK_TAG"'))
        self.assertIn("LAWDESK_IMAGE_SOURCE=local", commands)
        self.assertEqual(2, commands.count('deploy/scripts/deploy.sh" "$LAWDESK_TAG"'), "first deploy and a repeat")
        self.assertIn("deploy/host/rollback-drill.sh", commands)
        # First install as on Contabo: fresh volume, release unpacked under the gate's restrictive umask.
        self.assertIn('(umask 027 && git archive HEAD deploy | tar -x -C "$release")', commands)
        self.assertLess(commands.index("down -v"), commands.index('deploy/scripts/deploy.sh" "$LAWDESK_TAG"'))
        self.assertIn("DRILL PASSED", commands)
        names = [step.get("name", "") for step in job["steps"]]
        drill = next(i for i, n in enumerate(names) if "rollback drill" in n)
        teardown = names.index("Tear down")
        self.assertLess(drill, teardown)

    def test_stack_gate_requires_readiness_to_detect_a_database_outage_within_five_seconds(self):
        # Owner decision 2026-10-23: the first readiness probe after the database stops answers 503 in under 5 s.
        commands = run_commands(self.jobs["stack"])
        self.assertIn('[ "$took" -lt 5000 ]', commands)

    def test_stack_gate_signs_the_bootstrap_administrator_in_through_nginx(self):
        # LD-007/LD-008.4 on real containers: the generated administrator can sign in via the web container.
        commands = run_commands(self.jobs["stack"])
        self.assertIn("deploy/secrets/admin_bootstrap_password.txt", commands)
        self.assertIn("/api/v1/auth/login", commands)
        self.assertRegex(commands, r'mustChangePassword\\?":true')

    def test_downloaded_tools_are_checksum_verified(self):
        for name in ["policy", "secrets"]:
            with self.subTest(job=name):
                self.assertIn("sha256sum --check", run_commands(self.jobs[name]))


if __name__ == "__main__":
    unittest.main()


RUNNER = ["self-hosted", "Linux", "X64"]


class SingleRunnerPolicyTest(unittest.TestCase):
    """One runner executes one job at a time (docs/operations/CI-RUNNER.md), so CI is spent only on work that is ready."""

    GATES = ["policy", "backend", "backend-postgres-image", "frontend", "stack", "secrets"]

    def test_a_draft_pull_request_waits_and_marking_it_ready_starts_the_run(self):
        types = load_workflow()["on"]["pull_request"]["types"]
        self.assertIn("ready_for_review", types)
        for needed in ["opened", "synchronize", "reopened"]:
            self.assertIn(needed, types)

    def test_every_gate_skips_drafts_but_always_runs_on_pushes_and_ready_pull_requests(self):
        for name in self.GATES:
            with self.subTest(job=name):
                # On a push there is no pull request, so `!null` is true: pushes to develop are never skipped.
                self.assertEqual("${{ !github.event.pull_request.draft }}", load_workflow()["jobs"][name].get("if"))

    def test_a_superseded_pull_request_run_is_cancelled_but_a_push_to_develop_never_is(self):
        concurrency = load_workflow()["concurrency"]
        self.assertEqual("ci-${{ github.ref }}", concurrency["group"])
        self.assertEqual("${{ github.event_name == 'pull_request' }}", concurrency["cancel-in-progress"])


class SelfHostedRunnerTest(unittest.TestCase):
    """Owner decision 2026-10-25: every job runs on the Contabo runner (docs/operations/CI-RUNNER.md)."""

    def test_every_job_runs_on_the_self_hosted_runner(self):
        for name, job in load_workflow()["jobs"].items():
            if "uses" in job:
                continue  # the reusable deployment workflow is checked in test_cd_workflow.py
            with self.subTest(job=name):
                self.assertEqual(RUNNER, job["runs-on"])

    def test_no_job_reaches_the_host_docker_daemon_or_root(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        for forbidden in ["/var/run/docker.sock", "/run/docker.sock", "sudo ", "--privileged", "docker.sock"]:
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, text)

    def test_the_stack_gate_starts_clean_and_leaves_nothing_behind_on_the_persistent_runner(self):
        job = load_workflow()["jobs"]["stack"]
        names = [step.get("name", "") for step in job["steps"]]
        clean = next(i for i, n in enumerate(names) if n.startswith("Remove leftovers"))
        build = next(i for i, n in enumerate(names) if n == "Build images")
        self.assertLess(clean, build)
        self.assertIn("down -v --remove-orphans", job["steps"][clean]["run"])
        self.assertIn("docker network rm lawdesk-edge", job["steps"][clean]["run"])
        # The network may survive an earlier failed run, so it is created only when missing.
        self.assertIn("docker network create lawdesk-edge", run_commands(job))
        self.assertIn("docker network inspect lawdesk-edge", run_commands(job))
        last = job["steps"][-1]
        self.assertEqual("always()", last.get("if"))
        self.assertIn("docker network rm lawdesk-edge", last["run"])
        self.assertIn('"$LAWDESK_REGISTRY/backend:$LAWDESK_TAG"', last["run"], "this run's images are removed")

    def test_gate_jobs_do_not_receive_secrets(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        # Only the call of the deployment workflow inherits secrets, and only on pushes to develop.
        self.assertEqual(1, text.count("secrets: inherit"))
        self.assertEqual(0, text.count("${{ secrets."))

