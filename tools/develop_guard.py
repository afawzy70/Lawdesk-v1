#!/usr/bin/env python3
"""Procedural protection of develop (B4; docs/delivery/DEVELOP-PROTECTION.md).

Usage (CI, on every push to develop; GH_TOKEN and REPO set):
  develop_guard.py --sha <pushed commit> --before <develop head before the push> --forced <true|false>

Passes only when the push is exactly one merge of a pull request into develop and the latest QA verdict comment on
that pull request for its exact head commit reads `QA-VERDICT: PASS <40-hex head sha>` at the start of a line.
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


def api(path):
    return json.loads(subprocess.run(["gh", "api", path], capture_output=True, text=True, check=True).stdout)


def api_list(path):
    """Every item of every page of a list endpoint, read as one compact JSON object per line."""
    output = subprocess.run(["gh", "api", "--paginate", "--jq", ".[]", path], capture_output=True, text=True,
                            check=True).stdout
    return [json.loads(line) for line in output.splitlines() if line.strip()]


def latest_verdict(comments, head):
    verdict = None
    for comment in comments:
        for state, sha in VERDICT_RE.findall(comment.get("body") or ""):
            if sha == head:
                verdict = state
    return verdict


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
    verdict = latest_verdict(api_list(f"repos/{repo}/issues/{pull['number']}/comments?per_page=100"), head)
    if verdict != "PASS":
        return [f"pull request #{pull['number']} was merged without 'QA-VERDICT: PASS {head}' as QA's latest verdict "
                f"for its head (found: {verdict or 'none'})"]
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
