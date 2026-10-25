"""Moving the staging deployment secrets to the `staging` GitHub Environment (tools/github/migrate-staging-secrets.sh).

The owner runs the script once with the key files; the agent never sees a secret. The tests play GitHub with a fake `gh`
and check the properties that matter: nothing changes without --apply, a secret only travels on stdin from a file, no
output ever contains a secret, the environment is restricted to develop BEFORE a secret is stored in it, and the
repository-level copies are deleted only after a deployment succeeded with the environment's copies.
"""

import json
import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "github" / "migrate-staging-secrets.sh"
REPO = "afawzy70/Lawdesk-v1"

FAKE_GH = textwrap.dedent("""\
    #!/usr/bin/env python3
    import hashlib, json, os, sys
    args = sys.argv[1:]
    state = json.load(open(os.environ["FAKE_STATE"]))
    stdin = "" if sys.stdin.isatty() else sys.stdin.read()
    entry = {"args": args, "stdin_sha256": hashlib.sha256(stdin.encode()).hexdigest() if stdin else None,
             "stdin_len": len(stdin)}
    with open(os.environ["FAKE_LOG"], "a") as log:
        log.write(json.dumps(entry) + "\\n")
    cmd = " ".join(args)
    if args[:2] == ["auth", "status"]:
        sys.exit(0)
    if args[:2] == ["variable", "get"]:
        print(state["target"]); sys.exit(0)
    if "deployment-branch-policies" in cmd and "-X" not in args:
        print("\\n".join(state["policies"])); sys.exit(0)
    if args[0] == "api" and "/environments/staging/secrets/" in cmd:
        print(state["secret_updated_at"]); sys.exit(0)
    if args[:2] == ["run", "list"]:
        before = state.get("runs_before")
        dispatched = any(json.loads(line)["args"][:2] == ["workflow", "run"] for line in open(os.environ["FAKE_LOG"]))
        print(json.dumps(state["runs"] if before is None or dispatched else before)); sys.exit(0)
    if args[:2] == ["run", "watch"]:
        sys.exit(0 if state["watch_ok"] else 1)
    if args[0] == "api" and "commits/develop" in cmd:
        print("a" * 40); sys.exit(0)
    sys.exit(0)
    """)

KEY_MARKER = "BEGIN OPENSSH PRIVATE KEY"


class MigrateStagingSecretsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        bin_dir = root / "bin"
        bin_dir.mkdir()
        gh = bin_dir / "gh"
        gh.write_text(FAKE_GH, encoding="utf-8")
        gh.chmod(gh.stat().st_mode | stat.S_IEXEC)
        self.log = root / "gh.log"
        self.log.touch()
        self.state_file = root / "state.json"
        self.set_state()
        self.key = root / "ci_key"
        subprocess.run(["ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "test-only", "-f", str(self.key)],
                       check=True)
        self.key.chmod(0o600)
        self.known_hosts = root / "known_hosts"
        host_key = (root / "ci_key.pub").read_text(encoding="utf-8").split()[:2]
        self.known_hosts.write_text(f"167.86.90.174 {host_key[0]} {host_key[1]}\n", encoding="utf-8")
        self.known_hosts.chmod(0o600)
        self.env = {"PATH": f"{bin_dir}:/usr/bin:/bin", "HOME": str(root), "FAKE_STATE": str(self.state_file),
                    "FAKE_LOG": str(self.log)}

    def tearDown(self):
        self.tmp.cleanup()

    def set_state(self, **changes):
        state = {"target": "devops@167.86.90.174", "policies": [], "secret_updated_at": "2026-10-25T12:00:00Z",
                 "runs": [], "watch_ok": True}
        state.update(changes)
        self.state_file.write_text(json.dumps(state), encoding="utf-8")

    def run_script(self, *args, key=None, known_hosts=None):
        command = [str(SCRIPT), "--ssh-key-file", str(key or self.key), "--known-hosts-file",
                   str(known_hosts or self.known_hosts), *args]
        return subprocess.run(command, env=self.env, capture_output=True, text=True, check=False, timeout=60,
                              stdin=subprocess.DEVNULL)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text(encoding="utf-8").splitlines()]

    def mutating(self):
        out = []
        for call in self.calls():
            a = call["args"]
            if a[:2] in (["secret", "set"], ["secret", "delete"], ["workflow", "run"]) or "-X" in a:
                out.append(call)
        return out

    def key_text(self):
        return self.key.read_text(encoding="utf-8")

    # ---- safety of the plan -------------------------------------------------------------------------------------
    def test_without_apply_nothing_is_changed_and_the_plan_names_every_step(self):
        result = self.run_script()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], self.mutating())
        for step in ["restrict the staging environment to develop", "store LAWDESK_STAGING_SSH_KEY",
                     "store LAWDESK_STAGING_KNOWN_HOSTS"]:
            self.assertIn(step, result.stdout)

    def test_no_output_ever_contains_a_secret(self):
        for args in [[], ["--apply"]]:
            with self.subTest(args=args):
                result = self.run_script(*args)
                everything = result.stdout + result.stderr
                body = self.key_text().splitlines()[1]
                self.assertNotIn(body, everything)
                self.assertNotIn(KEY_MARKER, everything)

    # ---- the files ----------------------------------------------------------------------------------------------
    def test_a_key_file_other_users_can_read_is_refused(self):
        self.key.chmod(0o644)

        result = self.run_script("--apply")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("mode", result.stderr)
        self.assertEqual([], self.mutating())

    def test_a_file_that_is_not_a_private_key_is_refused(self):
        notkey = Path(self.tmp.name, "notkey")
        notkey.write_text("hello\n", encoding="utf-8")
        notkey.chmod(0o600)

        result = self.run_script("--apply", key=notkey)

        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], self.mutating())

    def test_known_hosts_must_contain_the_deployment_host(self):
        other = Path(self.tmp.name, "other_hosts")
        other.write_text(f"example.invalid {self.known_hosts.read_text().split()[1]} "
                         f"{self.known_hosts.read_text().split()[2]}\n", encoding="utf-8")
        other.chmod(0o600)

        result = self.run_script("--apply", known_hosts=other)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("167.86.90.174", result.stderr)
        self.assertEqual([], self.mutating())

    # ---- apply --------------------------------------------------------------------------------------------------
    def test_apply_restricts_the_environment_before_it_stores_any_secret(self):
        result = self.run_script("--apply")

        self.assertEqual(0, result.returncode, result.stderr)
        kinds = []
        for call in self.mutating():
            a = call["args"]
            if "-X" in a and "deployment-branch-policies" not in " ".join(a):
                kinds.append("environment")
            elif "-X" in a:
                kinds.append("policy")
            else:
                kinds.append("secret")
        self.assertEqual(["environment", "policy", "secret", "secret"], kinds)
        put = next(c["args"] for c in self.mutating() if "-X" in c["args"] and "PUT" in c["args"])
        self.assertIn("deployment_branch_policy[custom_branch_policies]=true", put)
        self.assertIn("deployment_branch_policy[protected_branches]=false", put)
        policy = next(c["args"] for c in self.mutating() if "deployment-branch-policies" in " ".join(c["args"]))
        self.assertIn("name=develop", policy)

    def test_a_secret_travels_on_stdin_from_its_file_and_never_in_an_argument(self):
        self.run_script("--apply")

        sets = [c for c in self.calls() if c["args"][:2] == ["secret", "set"]]
        self.assertEqual({"LAWDESK_STAGING_SSH_KEY", "LAWDESK_STAGING_KNOWN_HOSTS"}, {c["args"][2] for c in sets})
        import hashlib
        expected = {"LAWDESK_STAGING_SSH_KEY": hashlib.sha256(self.key_text().encode()).hexdigest(),
                    "LAWDESK_STAGING_KNOWN_HOSTS": hashlib.sha256(self.known_hosts.read_text().encode()).hexdigest()}
        for call in sets:
            self.assertEqual(expected[call["args"][2]], call["stdin_sha256"])
            self.assertEqual(["--env", "staging", "--repo", REPO], call["args"][3:])
            self.assertNotIn("--body", call["args"])
            self.assertNotIn("-b", call["args"])

    def test_apply_is_idempotent_when_develop_is_already_the_only_allowed_branch(self):
        self.set_state(policies=["develop"])

        result = self.run_script("--apply")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(any("deployment-branch-policies" in " ".join(c["args"]) and "-X" in c["args"]
                             for c in self.calls()), "the develop policy already exists")

    def test_apply_refuses_to_leave_another_branch_allowed(self):
        self.set_state(policies=["develop", "feature/x"])

        result = self.run_script("--apply")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("feature/x", result.stderr)
        self.assertEqual([], [c for c in self.mutating() if c["args"][:2] == ["secret", "set"]])

    # ---- verify and the removal of the repository-level copies --------------------------------------------------
    def test_verify_redeploys_the_current_develop_head_and_waits_for_the_run(self):
        self.set_state(runs_before=[{"databaseId": 90, "createdAt": "2026-10-25T09:00:00Z", "conclusion": "success"}],
                       runs=[{"databaseId": 99, "createdAt": "2026-10-25T12:05:00Z", "conclusion": None}])

        result = self.run_script("--verify")

        self.assertEqual(0, result.returncode, result.stderr)
        run = next(c["args"] for c in self.calls() if c["args"][:2] == ["workflow", "run"])
        self.assertIn("deploy-staging.yml", run)
        self.assertIn("sha=" + "a" * 40, " ".join(run))
        self.assertIn("develop", run)

    def test_the_repository_copies_are_removed_only_after_a_deployment_that_followed_the_environment_secrets(self):
        self.set_state(runs=[{"databaseId": 7, "createdAt": "2026-10-25T11:00:00Z", "conclusion": "success"}])

        before = self.run_script("--remove-repo-secrets")

        self.assertNotEqual(0, before.returncode)
        self.assertEqual([], [c for c in self.calls() if c["args"][:2] == ["secret", "delete"]])

        self.set_state(runs=[{"databaseId": 8, "createdAt": "2026-10-25T12:30:00Z", "conclusion": "success"}])
        after = self.run_script("--remove-repo-secrets")

        self.assertEqual(0, after.returncode, after.stderr)
        deleted = [c["args"] for c in self.calls() if c["args"][:2] == ["secret", "delete"]]
        self.assertEqual({"LAWDESK_STAGING_SSH_KEY", "LAWDESK_STAGING_KNOWN_HOSTS"}, {a[2] for a in deleted})
        for args in deleted:
            self.assertNotIn("--env", args, "only the repository-level copies are deleted")

    def test_a_failed_deployment_never_authorises_the_removal(self):
        self.set_state(runs=[{"databaseId": 8, "createdAt": "2026-10-25T12:30:00Z", "conclusion": "failure"}])

        result = self.run_script("--remove-repo-secrets")

        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], [c for c in self.calls() if c["args"][:2] == ["secret", "delete"]])

    def test_unknown_arguments_are_refused(self):
        self.assertNotEqual(0, self.run_script("--everything").returncode)


if __name__ == "__main__":
    unittest.main()
