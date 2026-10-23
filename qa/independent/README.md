# Independent QA foundation

This directory contains QA tooling, not application code. The product baseline is
`8ff061cf59c1bdd2e2ef41c2cc6fbc53acc0af4d`. No application exists at that SHA.
All 351 product cases are BLOCKED; tooling checks are reported separately.

Run from repository root:

```sh
python3 -m unittest discover -s qa/independent/tests -v
python3 qa/independent/catalog.py
python3 qa/independent/run.py --output /tmp/lawdesk-product-results.json
```

The last command intentionally exits **2** when verification is incomplete (1 for
failed product probes, 0 only when no blocked/not-run/failed product cases remain).
A successful probe alone never promotes a whole scenario to PASS. Full-case
execution evidence must be reviewed and recorded independently. Raw legal data,
cookies and passwords must not enter committed evidence.

## Design and traceability

`qa/specifications/refined-cases.json` retains all 351 baseline IDs, maps the 15
cross-cutting cases to real stories, and supplies prerequisites, synthetic data,
steps, expected results, priority, AC ordinal and blockers. All designs are
PROPOSED, not new approved acceptance criteria. AC ordinals refer to the original
three generic criteria; they do not imply those criteria are sufficient.

`story-profiles.txt` records a story-specific action, fixture, oracle, missing
contract decisions and boundary/concurrency probe for every story. Regenerate
with `python3 qa/independent/build_specs.py`; review the resulting diff.
Contract-dependent names, limits, statuses, dates, routes and roles MUST be bound
to an approved story contract before a case becomes reproducible for execution.
The current designs are conditional specifications, not 351 executable tests.
Infrastructure/release stories use repository/operator evidence rather than
invented application APIs. Generic mandatory-field/UI scenarios may need
replacement or justified N/A after the contract is agreed; never silently delete
an original ID or call N/A PASS.

## Spring Boot / Spring Security

Use a real running Spring Boot application and its real security chain, with an
ephemeral QA PostgreSQL instance. Do not substitute controller mocks or disable
filters. `run.py` consumes an explicit `--contract <local-json>` with:

```json
{
  "tested_sha": "<current git HEAD>",
  "approval_reference": "<approved story contract / PR reference>",
  "probes": [{
    "id": "endpoint-anonymous-denied",
    "case_ids": ["QA-0076"],
    "kind": "http",
    "method": "GET",
    "path": "<approved protected path beginning with />",
    "expected_status": 401,
    "forbidden_body_strings": ["QA-HIDDEN-CASE-B"],
    "header_env": {}
  }]
}
```

401 above is a contract example, not an approved route/status. Supply the actual
approved values. Set `LAWDESK_QA_BASE_URL` to the explicit loopback QA instance.
For logged-in probes, `header_env` maps header names to environment-variable names
holding QA credentials (never put credential values in JSON). Redirects are not
followed: a login redirect cannot masquerade as a correct API denial.
`expected_json_fields` supports exact top-level JSON assertions;
`expected_headers` supports exact response headers.

Expand each registered route/method across anonymous, allowed, denied, disabled,
revoked and out-of-scope users. Compare state before/after denied writes. Stateful
CSRF, fixation, logout replay, password policies and concurrency remain design
cases until the session/seed/reset contract exists; the simple probes do not
claim that coverage. Capture startup artifact digest and real seed evidence.

## PostgreSQL migrations / integrity

The SQL probe uses the installed `psql`, inherited PostgreSQL credentials, explicit
loopback `PGHOST` and a database name beginning `lawdesk_qa_`. Every probe runs in
a read-only transaction; expected output is one exact scalar. Use real schema
names from migration DDL, not invented tables. Example probe shape:

```json
{"id":"no-orphans","case_ids":["QA-0030"],"kind":"sql",
 "sql":"SELECT <approved orphan count query>","expected_scalar":0}
```

Migration execution is deliberately separate: provision disposable QA database,
apply versioned migrations with the product's pinned migration command, collect
history/checksums, run schema/constraint probes, rerun migrations and upgrade a
previous-version fixture. Add write/rollback, FK/unique/check and two-connection
race tests after DDL/seed contracts exist. Current read-only probes do not prove
constraint enforcement, migration upgrade or restore safety. No shared DB,
Contabo deployment, proxy configuration or Matbakhy resources are touched.

## React / Playwright

`browser/components.spec.tsx` is prepared for the product's pinned Vitest,
jsdom, React Testing Library and user-event toolchain. Configure jsdom and expose
an absolute `LAWDESK_QA_COMPONENT_ADAPTER` module exporting
`renderRequiredFieldForm()`. It must render the real component and return
`submitLabel`, `requiredLabel`, `requiredError`, `successfulMutationCount()`.
No adapter exists yet; this suite is NOT_RUN/BLOCKED, not verified automation.

`browser/journeys.spec.ts` runs with the product's pinned Playwright runner. Set
`LAWDESK_QA_UI_CONTRACT` to an approved local JSON file supplying the keys used in
the spec and environment-only QA username/password. Each workflow stage supplies
`url`, `inputs[{label,value}]`, `submit`, `expectedText`. Use isolated seeded QA
users. The workflow contract must include all legal stages, scoped negative
checks and report reconciliation before it verifies LD-038. Existing journey
assertions alone are partial coverage. Missing contract causes explicit skips;
record them as BLOCKED. Screenshots/traces may contain legal content: use only
synthetic data and redact before committing. Do not persist authentication state.

No JS dependencies or browser binaries were installed because the product
workspace/toolchain is absent. Runtime compilation and browser execution remain
unverified. When implementation arrives, add a QA-local pinned toolchain or use
the verified candidate's existing dependencies without altering its source.

## PR review workflow

Read-only discovery:
`gh pr list --repo afawzy70/Lawdesk-v1 --base develop --state open`.
For each implementation PR capture base/head SHAs and diff; use an isolated QA
checkout, not Claude's worktree. Read its story contract and architecture delta.
Require actual failing RED output, GREEN output at the exact implementation SHA
and REFACTOR regression evidence. Commit names or developer claims alone are
insufficient. Run applicable independent cases and save a per-PR report based on
`qa/reports/PR-REVIEW-TEMPLATE.md`. Recheck the head SHA before recommending.

Recommend REQUEST_CHANGES for verified defects/gate violations, BLOCKED for
missing contract/runtime/evidence, APPROVE only after applicable verification
and risk review. Do not call GitHub approve, merge or deploy. No daemon/schedule
is installed; repeat discovery on subsequent review runs. A currently empty PR
queue is not evidence about future implementations.
