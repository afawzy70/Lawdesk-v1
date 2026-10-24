# Independent QA strategy

> The owner-approved delivery strategy, [docs/governance/LAWDESK-DELIVERY-STRATEGY.md](../docs/governance/LAWDESK-DELIVERY-STRATEGY.md), also governs QA: security and performance testing, the shared-host limits, Phase 8 gates and the delivery states.

Claude implements each story with test-driven development (RED failing test -> minimal GREEN implementation -> REFACTOR). Codex runs an independent suite and inspects failures; ChatGPT owns the acceptance criteria and QA baseline, reports findings, and reviews PR evidence. A single developer's tests cannot be sole release proof.

## Case status
All `qa/test-cases.json` entries are **DRAFT/NOT_RUN**, generated test design scenarios, not executable tests or verified passes. Each must be refined with exact API/UI test steps, fixtures and expected results before the corresponding story implementation is accepted. Maintain 1:1 linkage between each story and cases, plus cross-cutting tests.

## Required gates
- Unit/API integration tests with ephemeral PostgreSQL and real Spring Security configuration.
- Authentication, authorization, ownership/IDOR, file upload validation and sensitive-data tests.
- React component tests and Playwright end-to-end legal workflows against isolated staging.
- Database migrations, concurrency, idempotency, rollback, and backup/restore checks.
- No Critical/High security or functional blocker; re-run regression at current SHA.
- Produce evidence report with commit SHA, test environment, commands, passed/failed/skipped counts, screenshots/logs when appropriate; never mark skipped tests PASS.
