# Independent Phase 5–6 execution

Use canonical `git archive <full SHA>` snapshots outside implementation workspaces. Build with Java 21 and
`-DskipTests`; a successful build is setup, not an independent product PASS. Install the candidate frontend's
locked dependencies with `npm ci`. Never run these scripts against customer databases.

The existing `phase234_review.py` lifecycle owns a disposable PostgreSQL 18.6 instance and real application jar
on loopback, creates synthetic accounts, and stops both in `finally`. It is reused, not replaced. The extension
suites deliberately inject database failures only into these disposable databases. Real-browser checks run Vite
against the same candidate API. The QA-only cumulative fixture uses `git merge-tree`/`commit-tree`, changes no
feature branch, and is explicitly identified as synthetic in its provenance manifest.

```bash
PYTHONPATH=/tmp/lawdesk-qa-python python3 qa/independent/phase56_review.py \
  --candidate /tmp/canonical-candidate --sha FULL_SHA --kind hearing-chronology --output /tmp/new-chronology-evidence
PYTHONPATH=/tmp/lawdesk-qa-python python3 qa/independent/phase56_operations.py \
  --candidate /tmp/canonical-candidate --sha FULL_SHA --kind experts --browser --output /tmp/new-expert-evidence
PYTHONPATH=/tmp/lawdesk-qa-python python3 qa/independent/phase56_formats.py \
  --candidate /tmp/canonical-candidate --sha FULL_SHA --browser --output /tmp/new-format-evidence
```

Existing `phase56_review.py` kinds: hearings, hearing-chronology, tasks, dashboard, inventory, judgments, exports.
`phase56_operations.py` kinds: documents, experts, notes. Format tests parse actual HTTP downloads with
openpyxl 3.1.5 and PyMuPDF 1.26.7. Playwright 1.64.1, Chromium build 1248 and axe-core 4.14.0 are the independent
browser tools used in this session. Inspect API **and** `browser/results.json`: the outer Python exit code alone
does not summarize the browser result. Never count developer tests or CI jobs as independent passes.

Evidence uses fresh directories and refuses overwrite. Every test result carries story trace, preconditions,
fixture/test data, steps, expected result and status. Some fixtures are shared across suites; counts are executed
cases, not distinct acceptance criteria or the entire 351-case baseline. All old evidence is retained. The corrected
CSV unsupported-format oracle permits PDF on the cumulative implementation; the old negative case is preserved.
The PDF row-limit parser joins wrapped reference digits within the known fixture title boundary; its earlier
false-positive missing-row result is preserved and classified as a QA oracle issue. The Arabic obsolete-banner
oracle matches the actual catalog phrase. The 73-character password regression expects the existing 72-byte limit.

For approved live UAT only, use existing `phase234-signedin.mjs`, `phase234-roles-integrity.mjs`, and
`phase234-forms.mjs` with `LAWDESK_QA_CREDENTIAL_FILE` pointing to the approved admin file; the role suite also
uses the existing secure directory. No secret may be passed as an argument or copied into evidence. The new
`phase6-uat-dashboard.mjs FULL_SHA NEW_OUTPUT` reconciles live aggregates read-only against case/proceeding/judgment
APIs, tests all four role permissions, and captures en/ar 320/375/1366 layouts. Its source-record contents remain
in memory. Run `release-attestation.mjs FULL_SHA NEW_OUTPUT` before and after the UAT batch. Attestation now
persists failures/operational blocks even when CSRF or a network precondition is unavailable.

Deployment-container PDF remains BLOCKED separately from local-jar success. Pending Phase 5 report ports,
undeployed UAT features and unexecuted desktop/cross-browser/performance checks are specified in
`phase56-remaining-specifications.json`. They do not become PASS because another test or CI job succeeded.
Phase 7 remains deferred. The owner delivery strategy merged in PR46 was read; disruptive load/stress on the shared
Contabo UAT is excluded. No Matbakhy, proxy, deployment, Docker-daemon or feature-worktree changes are authorized here.

The priority follow-up avoids replaying already completed same-SHA suites. To add the missing after-update rollback
and limited-SQL-writer COMMIT probes, use `phase56_priority.py` with the usual `--candidate`, `--sha`, `--output`
arguments and `--mode delta`. `--mode performance` instead collects the deliberately paced local baseline described
in `DELIVERY-STRATEGY-PLAN.md`; do not run it against shared UAT. The Java helper `ExpertReadPortProbe.java` is compiled
by `phase56_expert_port.py` against the exact candidate jar's classes/libraries, then queries the same disposable DB.
Its optional `--regression` runs the existing expert API suite first. If only the new helper fails, retain the completed
regression evidence and retry the helper in a fresh output directory without `--regression`. The helper uses the
candidate's Jackson3 API; the earlier compilation blocker remains in diagnostic evidence.
