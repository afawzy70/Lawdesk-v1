"""Procedural protection of develop (B4): GitHub branch protection and rulesets are unavailable on this plan (HTTP 403),
and every actor uses the same account, so develop cannot be locked. tools/develop_guard.py runs on every push to
develop and fails, which also blocks the staging deployment, unless the push is exactly one merge of a pull request into
develop whose head QA cleared with a `QA-VERDICT: PASS <head sha>` comment. See docs/delivery/DEVELOP-PROTECTION.md.
"""

import json
import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

GUARD = Path(__file__).resolve().parents[1] / "develop_guard.py"
SHA = "1" * 40
BEFORE = "0" * 39 + "9"
HEAD = "2" * 40

# Plays the GitHub API from a JSON file mapping an API path to its response; records each path asked for.
FAKE_GH = textwrap.dedent("""\
    #!/usr/bin/env python3
    import json, os, sys
    args = sys.argv[1:]
    path = args[-1]
    with open(os.environ["FAKE_GH_LOG"], "a") as f:
        f.write(path + "\\n")
    responses = json.load(open(os.environ["FAKE_GH_RESPONSES"]))
    if path.split("?")[0] not in responses:
        sys.exit(1)
    body = responses[path.split("?")[0]]
    if "--jq" in args:  # only ".[]" is used: one compact item per line, as gh prints it
        assert args[args.index("--jq") + 1] == ".[]"
        for item in body:
            print(json.dumps(item, separators=(",", ":")))
    else:
        json.dump(body, sys.stdout)
    """)


def pull(number=7, base="develop", merged=True, merge_commit=SHA, head=HEAD):
    return {"number": number, "base": {"ref": base}, "merged_at": "2026-10-23T00:00:00Z" if merged else None,
            "merge_commit_sha": merge_commit, "head": {"sha": head}}


def comment(body):
    return {"body": body, "user": {"login": "afawzy70"}}


class DevelopGuardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        bin_dir = root / "bin"
        bin_dir.mkdir()
        (bin_dir / "gh").write_text(FAKE_GH, encoding="utf-8")
        (bin_dir / "gh").chmod(0o755)
        self.responses = root / "responses.json"
        self.log = root / "gh.log"
        self.log.touch()
        self.env = {"PATH": f"{bin_dir}:/usr/bin:/bin", "HOME": str(root), "FAKE_GH_LOG": str(self.log),
                    "FAKE_GH_RESPONSES": str(self.responses), "REPO": "afawzy70/Lawdesk-v1"}
        self.respond(pulls=[pull()], comments=[comment(f"QA-VERDICT: PASS {HEAD}")])

    def tearDown(self):
        self.tmp.cleanup()

    def respond(self, pulls, comments, parents=(BEFORE, HEAD)):
        base = "repos/afawzy70/Lawdesk-v1"
        self.responses.write_text(json.dumps({
            f"{base}/commits/{SHA}/pulls": pulls,
            f"{base}/commits/{SHA}": {"sha": SHA, "parents": [{"sha": p} for p in parents]},
            f"{base}/issues/7/comments": comments,
        }), encoding="utf-8")

    def guard(self, forced="false", before=BEFORE):
        return subprocess.run(["python3", str(GUARD), "--sha", SHA, "--before", before, "--forced", forced],
                              env=self.env, capture_output=True, text=True, check=False, timeout=30)

    def test_a_qa_cleared_pull_request_merge_passes(self):
        result = self.guard()

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("#7", result.stdout)

    def test_a_squash_merge_with_a_single_parent_passes(self):
        self.respond(pulls=[pull()], comments=[comment(f"QA-VERDICT: PASS {HEAD}")], parents=(BEFORE,))

        self.assertEqual(0, self.guard().returncode)

    def test_a_force_push_fails(self):
        result = self.guard(forced="true")

        self.assertEqual(1, result.returncode)
        self.assertIn("force", result.stdout + result.stderr)

    def test_a_direct_push_fails(self):
        for label, pulls in {"no pull request": [], "open pull request": [pull(merged=False)],
                             "merged into main": [pull(base="main")],
                             "commit is only part of the pull request": [pull(merge_commit="3" * 40)]}.items():
            with self.subTest(label):
                self.respond(pulls=pulls, comments=[comment(f"QA-VERDICT: PASS {HEAD}")])
                result = self.guard()
                self.assertEqual(1, result.returncode, result.stdout)
                self.assertIn("not the merge of a pull request into develop", result.stdout + result.stderr)

    def test_commits_pushed_along_with_the_merge_fail(self):
        # The merge's first parent must be the develop head before the push; otherwise more was pushed than the merge.
        result = self.guard(before="4" * 40)

        self.assertEqual(1, result.returncode)
        self.assertIn("more than one merge", result.stdout + result.stderr)

    def test_a_merge_without_a_qa_verdict_for_its_exact_head_fails(self):
        for label, comments in {"none": [], "other sha": [comment(f"QA-VERDICT: PASS {'5' * 40}")],
                                "blocked": [comment(f"QA-VERDICT: BLOCKED {HEAD}")],
                                "quoted, not at line start": [comment(f"> QA-VERDICT: PASS {HEAD}")],
                                "short sha": [comment(f"QA-VERDICT: PASS {HEAD[:12]}")]}.items():
            with self.subTest(label):
                self.respond(pulls=[pull()], comments=comments)
                result = self.guard()
                self.assertEqual(1, result.returncode, result.stdout)
                self.assertIn("QA-VERDICT: PASS", result.stdout + result.stderr)

    def test_a_later_verdict_that_withdraws_the_pass_fails(self):
        self.respond(pulls=[pull()], comments=[comment(f"QA-VERDICT: PASS {HEAD}"),
                                               comment(f"QA-VERDICT: BLOCKED {HEAD}")])

        self.assertEqual(1, self.guard().returncode)

    def test_the_first_push_of_a_new_develop_branch_skips_the_parent_check(self):
        self.assertEqual(0, self.guard(before="0" * 40).returncode)

    def test_malformed_arguments_are_refused(self):
        result = subprocess.run(["python3", str(GUARD), "--sha", "HEAD", "--before", BEFORE, "--forced", "false"],
                                env=self.env, capture_output=True, text=True, check=False)
        self.assertEqual(2, result.returncode)


if __name__ == "__main__":
    unittest.main()
