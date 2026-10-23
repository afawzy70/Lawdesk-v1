"""LD-001 repository policy tests (QA-0001..QA-0008 refinements, see docs/delivery/PHASE-1-ACCEPTANCE.md)."""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = TOOLS_DIR.parent
sys.path.insert(0, str(TOOLS_DIR))

import repo_policy  # noqa: E402


class BranchNameTest(unittest.TestCase):
    def test_accepts_long_lived_and_conventional_work_branches(self):
        for name in [
            "main",
            "develop",
            "feature/phase-1-foundation",
            "feature/LD-002-backend-bootstrap",
            "fix/LD-013-date-validation",
            "docs/product-qa-baseline",
            "qa/LD-004-migration-suite",
            "chore/bump-deps",
            "release/1.0.0",
            "hotfix/1.0.1",
        ]:
            with self.subTest(name=name):
                self.assertEqual([], repo_policy.check_branch_name(name))

    def test_rejects_unknown_prefix_with_actionable_message(self):
        errors = repo_policy.check_branch_name("my-work")
        self.assertEqual(1, len(errors))
        self.assertIn("feature/", errors[0])

    def test_rejects_names_git_cannot_create(self):
        # QAD-007: every name the policy accepts must also be a valid Git branch name.
        for name in ["feature/a.lock", "feature/a.", "feature/a\n", "feature/a\r", "feature/.a", "feature/a.lock\n"]:
            with self.subTest(name=name):
                self.assertNotEqual([], repo_policy.check_branch_name(name))

    def test_accepted_names_agree_with_git_check_ref_format(self):
        for name in ["feature/LD-002-backend-bootstrap", "release/1.0.0", "fix/a.b", "feature/a.lock",
                     "feature/a.", "feature/a\n", "feature/a..b"]:
            with self.subTest(name=name):
                git_ok = subprocess.run(["git", "check-ref-format", "--branch", name],
                                        capture_output=True, check=False).returncode == 0
                if not git_ok:
                    self.assertNotEqual([], repo_policy.check_branch_name(name))

    def test_rejects_malformed_names(self):
        for name in ["", "feature/", "feature/Has Space", "feature/..", "Feature/x", "feature/x/../../main"]:
            with self.subTest(name=name):
                self.assertNotEqual([], repo_policy.check_branch_name(name))


class CommitMessageTest(unittest.TestCase):
    def test_accepts_conventional_commits(self):
        for message in [
            "feat(LD-002): add health endpoint",
            "fix: handle empty input",
            "docs: add README-QA-BASELINE.md to LawDesk product QA baseline",
            "test(LD-004)!: replace fixture loader\n\nBody text.",
            "Merge pull request #1 from afawzy70/docs/product-qa-baseline",
            "Revert \"feat: something\"",
            "Initial commit",
        ]:
            with self.subTest(message=message):
                self.assertEqual([], repo_policy.check_commit_message(message))

    def test_rejects_free_form_subject(self):
        errors = repo_policy.check_commit_message("updated stuff")
        self.assertEqual(1, len(errors))
        self.assertIn("type(scope): summary", errors[0])

    def test_rejects_overlong_subject(self):
        errors = repo_policy.check_commit_message("feat: " + "x" * 100)
        self.assertTrue(any("72" in e for e in errors))

    def test_rejects_empty_message(self):
        self.assertNotEqual([], repo_policy.check_commit_message("   \n"))

    def test_ignores_git_comment_lines(self):
        self.assertEqual([], repo_policy.check_commit_message("# comment\nchore: tidy\n# more"))


class PullRequestFlowTest(unittest.TestCase):
    def test_work_branches_target_develop(self):
        self.assertEqual([], repo_policy.check_pull_request("feature/LD-002-x", "develop"))
        self.assertEqual([], repo_policy.check_pull_request("qa/LD-002-suite", "develop"))

    def test_only_release_and_hotfix_target_main(self):
        self.assertEqual([], repo_policy.check_pull_request("release/1.0.0", "main"))
        self.assertEqual([], repo_policy.check_pull_request("hotfix/1.0.1", "main"))

    def test_feature_branch_into_main_is_rejected(self):
        errors = repo_policy.check_pull_request("feature/LD-002-x", "main")
        self.assertEqual(1, len(errors))
        self.assertIn("develop", errors[0])

    def test_unknown_base_is_rejected(self):
        self.assertNotEqual([], repo_policy.check_pull_request("feature/x", "feature/y"))

    def test_invalid_head_name_is_reported(self):
        self.assertNotEqual([], repo_policy.check_pull_request("random", "develop"))


class RepositoryLayoutTest(unittest.TestCase):
    def test_this_repository_satisfies_required_layout(self):
        self.assertEqual([], repo_policy.check_layout(REPO_ROOT))

    def test_missing_files_are_listed(self):
        with tempfile.TemporaryDirectory() as tmp:
            errors = repo_policy.check_layout(Path(tmp))
        self.assertEqual(len(repo_policy.REQUIRED_PATHS), len(errors))
        self.assertTrue(any("CONTRIBUTING.md" in e for e in errors))


class CommandLineTest(unittest.TestCase):
    def run_cli(self, *args):
        return subprocess.run(
            [sys.executable, "-I", str(TOOLS_DIR / "repo_policy.py"), *args],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_valid_branch_exits_zero(self):
        self.assertEqual(0, self.run_cli("branch", "feature/LD-001-conventions").returncode)

    def test_invalid_branch_exits_nonzero_with_message_on_stderr(self):
        result = self.run_cli("branch", "wip")
        self.assertEqual(1, result.returncode)
        self.assertIn("wip", result.stderr)

    def test_commit_msg_reads_file(self):
        with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as f:
            f.write("nope\n")
        try:
            self.assertEqual(1, self.run_cli("commit-msg", f.name).returncode)
        finally:
            Path(f.name).unlink()

    def test_pr_flow(self):
        self.assertEqual(1, self.run_cli("pr", "feature/x", "main").returncode)
        self.assertEqual(0, self.run_cli("pr", "feature/x", "develop").returncode)

    def test_unknown_command_is_usage_error(self):
        self.assertEqual(2, self.run_cli("bogus").returncode)


if __name__ == "__main__":
    unittest.main()
