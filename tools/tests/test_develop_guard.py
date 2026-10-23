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
        sys.stderr.write("gh: Not Found (HTTP 404)\\n")
        sys.exit(1)
    body = responses[path.split("?")[0]]
    if isinstance(body, dict) and "fake_http_status" in body:
        sys.stderr.write(f"gh: error (HTTP {body['fake_http_status']})\\n")
        sys.exit(1)
    if "--jq" in args:  # ".[]" or ".check_runs[]": one compact item per line, as gh prints it
        expression = args[args.index("--jq") + 1]
        items = body["check_runs"] if expression == ".check_runs[]" else body
        assert expression in (".[]", ".check_runs[]"), expression
        for item in items:
            print(json.dumps(item, separators=(",", ":")))
    else:
        json.dump(body, sys.stdout)
    """)


def pull(number=7, base="develop", merged=True, merge_commit=SHA, head=HEAD):
    return {"number": number, "base": {"ref": base}, "merged_at": MERGED_AT if merged else None,
            "merge_commit_sha": merge_commit, "head": {"sha": head}}


GUARD_CHECK = "Develop guard (QA-cleared merge)"
MERGED_AT = "2026-10-23T12:00:00Z"
BEFORE_MERGE = "2026-10-23T11:00:00Z"


def comment(body, created=BEFORE_MERGE, updated=None):
    return {"body": body, "user": {"login": "afawzy70"}, "created_at": created, "updated_at": updated or created}


def check_run(name=GUARD_CHECK, conclusion="success", app="github-actions"):
    return {"name": name, "conclusion": conclusion, "app": {"slug": app}}


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

    def respond(self, pulls, comments, parents=(BEFORE, HEAD), before_checks=(check_run(),), before_has_guard=True):
        base = "repos/afawzy70/Lawdesk-v1"
        responses = {
            f"{base}/commits/{SHA}/pulls": pulls,
            f"{base}/commits/{SHA}": {"sha": SHA, "parents": [{"sha": p} for p in parents]},
            f"{base}/issues/7/comments": comments,
            f"{base}/commits/{BEFORE}/check-runs": {"check_runs": list(before_checks)},
        }
        if before_has_guard is True:
            responses[f"{base}/contents/tools/develop_guard.py"] = {"type": "file"}
        elif before_has_guard is not False:
            responses[f"{base}/contents/tools/develop_guard.py"] = {"fake_http_status": before_has_guard}
        self.responses.write_text(json.dumps(responses), encoding="utf-8")

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

    # Bypass scenarios (owner decision 2026-10-23: test them, document what remains; DEVELOP-PROTECTION.md).

    def test_bypass_verdict_posted_after_the_merge_does_not_count(self):
        # Merge first, post the verdict, re-run the failed guard: must still fail.
        self.respond(pulls=[pull()], comments=[comment(f"QA-VERDICT: PASS {HEAD}", created="2026-10-23T12:00:01Z")])

        result = self.guard()

        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn("QA-VERDICT: PASS", result.stdout)

    def test_bypass_verdict_edited_after_the_merge_does_not_count(self):
        self.respond(pulls=[pull()], comments=[comment(f"QA-VERDICT: PASS {HEAD}", updated="2026-10-23T13:00:00Z")])

        self.assertEqual(1, self.guard().returncode)

    def test_bypass_verdict_inside_a_code_block_does_not_count(self):
        body = f"Post this when you are done:\n```\nQA-VERDICT: PASS {HEAD}\n```\n"
        self.respond(pulls=[pull()], comments=[comment(body)])

        self.assertEqual(1, self.guard().returncode)

    def test_bypass_verdict_inside_any_code_fence_does_not_count(self):
        # Review finding on f492e0c: unclosed and indented fences, tildes, and a shorter closing fence.
        verdict = f"QA-VERDICT: PASS {HEAD}"
        for label, body in {
            "unclosed fence": f"Example:\n```\n{verdict}\n",
            "fence indented two spaces": f"Example:\n  ```\n{verdict}\n  ```\n",
            "tilde fence": f"~~~text\n{verdict}\n~~~\n",
            "longer fence not closed by a shorter one": f"````\n```\n{verdict}\n````\n",
            "closing fence of the other kind": f"```\n~~~\n{verdict}\n```\n",
        }.items():
            with self.subTest(label):
                self.respond(pulls=[pull()], comments=[comment(body)])
                self.assertEqual(1, self.guard().returncode, body)

    def test_verdict_after_a_closed_fence_counts(self):
        body = f"```\nexample\n```\nQA-VERDICT: PASS {HEAD}\n"
        self.respond(pulls=[pull()], comments=[comment(body)])

        self.assertEqual(0, self.guard().returncode)

    def test_recover_verdict_inside_a_code_fence_does_not_count(self):
        comments = [comment(f"QA-VERDICT: PASS {HEAD}\n```\nQA-VERDICT: RECOVER {BEFORE}\n")]
        self.respond(pulls=[pull()], comments=comments, before_checks=())

        self.assertEqual(1, self.guard().returncode)

    def test_bypass_skip_ci_direct_push_before_a_merge_is_caught(self):
        # "[skip ci]" on a direct push means no guard run for it; the next merge would carry it along.
        self.respond(pulls=[pull()], comments=[comment(f"QA-VERDICT: PASS {HEAD}")], before_checks=())

        result = self.guard()

        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn("has not passed the develop guard", result.stdout)

    def test_bypass_merging_on_top_of_a_flagged_push_is_caught(self):
        for checks in [(check_run(conclusion="failure"),), (check_run(app="impostor"),),
                       (check_run(name="Repository policy"),)]:
            with self.subTest(checks=checks):
                self.respond(pulls=[pull()], comments=[comment(f"QA-VERDICT: PASS {HEAD}")], before_checks=checks)
                self.assertEqual(1, self.guard().returncode)

    def test_qa_recovery_verdict_for_the_exact_unguarded_commit_accepts_the_merge(self):
        comments = [comment(f"QA-VERDICT: PASS {HEAD}\nQA-VERDICT: RECOVER {BEFORE}")]
        self.respond(pulls=[pull()], comments=comments, before_checks=())

        result = self.guard()

        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("recover", result.stdout.lower())

    def test_recovery_verdict_for_another_commit_does_not_count(self):
        comments = [comment(f"QA-VERDICT: PASS {HEAD}\nQA-VERDICT: RECOVER {'6' * 40}")]
        self.respond(pulls=[pull()], comments=comments, before_checks=())

        self.assertEqual(1, self.guard().returncode)

    def test_the_first_guarded_push_accepts_a_develop_head_from_before_the_guard(self):
        self.respond(pulls=[pull()], comments=[comment(f"QA-VERDICT: PASS {HEAD}")], before_checks=(),
                     before_has_guard=False)

        self.assertEqual(0, self.guard().returncode)

    def test_api_errors_fail_closed(self):
        self.respond(pulls=[pull()], comments=[comment(f"QA-VERDICT: PASS {HEAD}")], before_checks=(),
                     before_has_guard=500)

        result = self.guard()

        self.assertNotEqual(0, result.returncode)

    def test_the_check_name_matches_the_ci_job(self):
        import yaml
        ci = yaml.safe_load((GUARD.parents[1] / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8"))
        self.assertEqual(GUARD_CHECK, ci["jobs"]["develop-guard"]["name"])

    def test_malformed_arguments_are_refused(self):
        result = subprocess.run(["python3", str(GUARD), "--sha", "HEAD", "--before", BEFORE, "--forced", "false"],
                                env=self.env, capture_output=True, text=True, check=False)
        self.assertEqual(2, result.returncode)


if __name__ == "__main__":
    unittest.main()
