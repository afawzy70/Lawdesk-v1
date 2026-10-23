#!/usr/bin/env python3
"""Procedural protection of develop (B4; docs/delivery/DEVELOP-PROTECTION.md).

Usage (CI, on every push to develop; GH_TOKEN and REPO set):
  develop_guard.py --sha <pushed commit> --before <develop head before the push> --forced <true|false>

Passes only when the push is exactly one merge of a pull request into develop, the develop head it builds on passed
this guard too (or predates it, or QA posted `QA-VERDICT: RECOVER <that sha>` on the pull request), and the latest QA
verdict for the pull request's exact head reads `QA-VERDICT: PASS <40-hex head sha>` at the start of a line, outside
code blocks, in a comment written and last edited before the merge.
Branch protection is unavailable on this plan and every actor shares one account, so this cannot prevent a push; it
makes one visible (a failed CI run on develop) and keeps it from deploying (the staging job needs this check).
Exit codes: 0 compliant, 1 violation (details on stdout), 2 usage error.
"""

import argparse
import json
import os
import re
import subprocess
import sys

SHA_RE = re.compile(r"^[0-9a-f]{40}$")
VERDICT_RE = re.compile(r"^QA-VERDICT: ([A-Z_]+) ([0-9a-f]{40})\s*$", re.MULTILINE)
ZERO = "0" * 40
GUARD_CHECK = "Develop guard (QA-cleared merge)"  # the name of the develop-guard job in ci.yml
FENCE_RE = re.compile(r"^(```|~~~).*?^\1[^\n]*$", re.MULTILINE | re.DOTALL)


def api(path):
    return json.loads(subprocess.run(["gh", "api", path], capture_output=True, text=True, check=True).stdout)


def api_list(path, items=".[]"):
    """Every item of every page of a list endpoint, read as one compact JSON object per line."""
    output = subprocess.run(["gh", "api", "--paginate", "--jq", items, path], capture_output=True, text=True,
                            check=True).stdout
    return [json.loads(line) for line in output.splitlines() if line.strip()]


def exists(path):
    """True if the API path exists, False on 404; any other failure is raised (the guard fails closed)."""
    result = subprocess.run(["gh", "api", path], capture_output=True, text=True, check=False)
    if result.returncode == 0:
        return True
    if "HTTP 404" in result.stderr:
        return False
    raise RuntimeError(f"gh api {path}: {result.stderr.strip()}")


def latest_verdict(comments, sha, merged_at):
    """QA's latest verdict for a commit, counting only comments written and last edited before the merge."""
    verdict = None
    for comment in comments:
        if comment["created_at"] > merged_at or comment.get("updated_at", comment["created_at"]) > merged_at:
            continue
        for state, verdict_sha in VERDICT_RE.findall(FENCE_RE.sub("", comment.get("body") or "")):
            if verdict_sha == sha:
                verdict = state
    return verdict


def guarded(repo, sha):
    runs = api_list(f"repos/{repo}/commits/{sha}/check-runs?per_page=100", ".check_runs[]")
    return any(r["name"] == GUARD_CHECK and r["conclusion"] == "success" and r["app"]["slug"] == "github-actions"
               for r in runs)


def check(repo, sha, before, forced):
    if forced:
        return ["develop was force-pushed; restore it from the last guarded commit (see DEVELOP-PROTECTION.md)"]
    if before != ZERO:
        parents = [p["sha"] for p in api(f"repos/{repo}/commits/{sha}")["parents"]]
        if not parents or parents[0] != before:
            return [f"the push to develop contains more than one merge ({before[:12]}..{sha[:12]})"]
    merged = [p for p in api_list(f"repos/{repo}/commits/{sha}/pulls")
              if p["base"]["ref"] == "develop" and p.get("merged_at") and p.get("merge_commit_sha") == sha]
    if not merged:
        return [f"{sha} is not the merge of a pull request into develop (direct push?)"]
    pull = merged[0]
    head = pull["head"]["sha"]
    comments = api_list(f"repos/{repo}/issues/{pull['number']}/comments?per_page=100")
    verdict = latest_verdict(comments, head, pull["merged_at"])
    if verdict != "PASS":
        return [f"pull request #{pull['number']} was merged without 'QA-VERDICT: PASS {head}' as QA's latest verdict "
                f"for its head before the merge (found: {verdict or 'none'})"]
    # A push that skipped CI ("[skip ci]") or failed this guard must not ride along with a later, cleared merge.
    if before != ZERO and not guarded(repo, before):
        if not exists(f"repos/{repo}/contents/tools/develop_guard.py?ref={before}"):
            print(f"develop guard: {before[:12]} predates the guard")
        elif latest_verdict(comments, before, pull["merged_at"]) == "RECOVER":
            print(f"develop guard: QA reviewed the unguarded {before[:12]} (QA-VERDICT: RECOVER) for #{pull['number']}")
        else:
            return [f"the develop head {before} this merge builds on has not passed the develop guard (skipped or "
                    f"failed CI); QA must review it and post 'QA-VERDICT: RECOVER {before}' on the pull request"]
    print(f"develop guard: {sha} is the merge of #{pull['number']}, QA-cleared at head {head}")
    return []


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--sha", required=True)
    parser.add_argument("--before", required=True)
    parser.add_argument("--forced", required=True, choices=["true", "false"])
    args = parser.parse_args(argv)
    if not SHA_RE.match(args.sha) or not SHA_RE.match(args.before):
        print("develop guard: --sha and --before must be full lowercase commit SHAs", file=sys.stderr)
        return 2
    errors = check(os.environ["REPO"], args.sha, args.before, args.forced == "true")
    for error in errors:
        print(f"::error::develop guard: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
