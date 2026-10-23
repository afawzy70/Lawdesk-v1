# LawDesk v1 — product/QA baseline (draft)

Approved architecture: Spring Boot / Spring Security (database-backed identity), React + TypeScript, PostgreSQL. Eight delivery milestones. No external identity manager.

Scope: Legal case management, first instance/appeal/cassation, court hearings, judgments, expert work, legal PDF documents, users/permissions/audit, dashboards and filtered reports, Arabic/English UI.

- 42 draft stories in `product/stories.json`.
- 351 draft QA scenarios in `qa/test-cases.json` (8 per story + 15 security cross-cutting). **All NOT_RUN.**
- Acceptance criteria require refinement during story kickoff. No test has been executed yet.
- Refer to `product/DELIVERY-PLAN.md`, `architecture/DECISIONS.md`, `qa/QA-STRATEGY.md`, `operations/CONTABO-SESSIONS.md`.
