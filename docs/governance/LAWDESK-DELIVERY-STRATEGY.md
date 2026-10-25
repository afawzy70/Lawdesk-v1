# LawDesk v1 delivery strategy

**Status:** approved by the owner on 2026-10-24. It is mandatory for implementation, QA, security, performance and
release planning.

**Relationship to other documents.** This strategy adds to the approved scope and does not replace it:

- the backlog: `product/stories.json` and `product/DELIVERY-PLAN.md`;
- the architecture contracts: `architecture/CONTRACTS.md`;
- the acceptance files: `docs/delivery/PHASE-*-ACCEPTANCE.md`;
- the QA baseline: `qa/QA-STRATEGY.md` and `README-QA-BASELINE.md`.

Where this file and one of them seem to disagree, the more specific approved document wins and the owner is asked.

**Tooling.** No new governance infrastructure or orchestration software is introduced. Work runs through the existing
worktrees, agents, tools and GitHub workflows (pull requests, CI, the automatic deployment from `develop`, and QA on
PR #6).

## 1. Current priority A: complete Phases 5 and 6 concurrently

| Phase | Scope (stories in `product/DELIVERY-PLAN.md`) |
|-------|-----------------------------------------------|
| 5 Legal operations | hearings (LD-024), tasks and reminders (LD-027, LD-028), legal documents and attachments (LD-026), experts (LD-025), notes and activity history (LD-029) |
| 6 Dashboard and reporting | database-backed dashboard (LD-030); case, judgment and hearing reports (LD-031, LD-032); expert and workload reports (LD-033); CSV, Excel and PDF exports (LD-034), each with permissions, filtering and audit |

**Completion.** All approved stories and acceptance criteria are completed, including the integrations between Phase 5
and Phase 6: the Phase 6 reports read the Phase 5 read ports in `architecture/CONTRACTS.md` §8.

**How the work is delivered:**

- Pull requests stay small and dependency-aware.
- Every story carries TDD evidence (RED, then GREEN, then REFACTOR).
- Independent QA reviews each change.
- Completed work is neither restarted nor duplicated.

**Merge blockers.** Confirmed critical integrity or security defects, for example QAD-032, are fixed before the
affected functionality is merged.

## 2. Phase 7: complete UI/UX redesign (deferred)

Phase 7 starts only after the functionality and services of Phases 5 and 6 are complete, **and only with the owner's
explicit approval.** The owner intends a full visual and user-experience redesign, possibly based on the approved
Google Stitch designs.

**Scope:**

- a final design direction and design system;
- navigation and information architecture;
- responsive web and mobile layouts;
- Arabic RTL and English LTR;
- forms, tables, dashboards and case-management journeys;
- accessibility;
- visual consistency, integrated with every completed backend feature.

**Method:** incremental, on isolated branches with reviewable pull requests, never as one large rewrite. All business
logic, APIs, security controls and data-integrity behaviour are preserved.

**Until Phase 7:** only the usability fixes needed to keep functionality usable, such as a page that overflows a
phone screen (QAD-027). No large cosmetic work goes into the current UI.

## 3. Security testing

**Starting now and continuing through development:**

- static application security testing (SAST);
- software composition analysis (SCA) of backend and frontend dependencies;
- secret scanning (gitleaks on every branch already runs in CI);
- review of authentication and authorization;
- API security and access-control tests;
- safe, scoped dynamic testing (DAST).

**Scope limits:**

- DAST runs only against isolated or synthetic targets, never as a disruptive scan of shared UAT.
- Focused security reviews are done on the relevant pull requests: authentication, sessions, uploads, exports and
  database integrity.
- After Phase 7, Phase 8 adds the comprehensive security assessment and full DAST.

**Rule:** a successful automated scan never counts as proof of complete security.

## 4. Performance and stress testing

### Starting now

- API performance baselines.
- Measurable metrics: response-time percentiles (p50, p95, p99), throughput, error rate, and resource use (CPU,
  memory, database connections).
- Realistic, reproducible load scenarios with test data only.
- Identification of expensive database queries and other bottlenecks, for example query plans of the reports.

### After Phases 5 and 6

Controlled load tests against a safely isolated environment, covering cases, proceedings, hearings, attachments and
reporting.

### After Phase 7

- final frontend and API performance regression;
- load tests;
- stress tests;
- spike tests;
- soak (endurance) tests.

### Infrastructure constraint (mandatory)

LawDesk shares its Contabo server with Matbakhy.

- Disruptive stress or heavy load tests never run against the shared public UAT environment.
- They use isolated containers with bounded resources and test-only data. When enough isolation cannot be guaranteed
  on this host, heavy tests use separate infrastructure.
- Matbakhy's containers, the shared proxy, databases, network and availability are never disrupted.

## 5. Phase 8: final release verification

- full functional regression across all approved product stories;
- independent security validation;
- full DAST and vulnerability remediation;
- API and frontend performance;
- load, stress, spike and soak testing;
- cross-browser and responsive testing;
- accessibility;
- Arabic and English verification;
- data-integrity and migration verification;
- backup and recovery testing;
- a production-readiness assessment.

Production readiness is never declared without evidence.

## 6. Responsibilities

| Claude | Codex |
|--------|-------|
| implementation | independent QA |
| TDD | security assessment |
| defect remediation | performance test design and execution |
| integration and deployment | cross-phase regression |
| performance optimisation | verification of fixes and release-quality evidence |

- Codex never receives PASS credit automatically for Claude's tests.
- PASS, FAIL, BLOCKED and NOT_RUN are always kept separate. BLOCKED, SKIPPED and NOT_RUN are never reported as PASS.

## 7. Delivery states

Always state which of these a piece of work has reached, and never use one for another:

| State | Meaning | Evidence |
|-------|---------|----------|
| **IMPLEMENTED** | the functionality is coded | branch and PR head SHA, TDD evidence, green CI |
| **INTEGRATED** | merged into `develop` | merge SHA |
| **DEPLOYED** | verified on UAT at an exact SHA | deploy log and `GET /api/v1/release` reporting that SHA |
| **QA ACCEPTED** | independently tested against the intended scope | Codex's verdict and evidence for that SHA |
| **PRODUCTION READY** | passed every final release gate (§5) | Phase 8 evidence |

## 8. Autonomous execution

**Keep going:**

- Approved work continues without unnecessary pauses.
- Branch isolation, dependency order and the working functionality are preserved.
- Automatic deployment from `develop` stays operational.

**Never bypass:** failed security, authentication or data-integrity checks.

**Escalate only:**

- material business decisions;
- destructive operations;
- missing permissions;
- blockers that cannot be recovered.
