# LawDesk v1 architecture decisions

- Backend: Java 21, Spring Boot 3.x (exact supported patch version determined at implementation), Spring Security REST APIs.
- Frontend: React and TypeScript. Mobile-responsive Arabic and English user experience.
- Database: PostgreSQL; use versioned migrations and isolated test DB.
- Identity: NO Keycloak or other external identity-management provider. Spring Security + application database handle login, users, roles and permissions. Passwords must be securely hashed; enforce authorization server-side.
- Session transport and CSRF protection: design and document during Phase 2; prefer a secure HttpOnly cookie based approach for browser login with appropriate CSRF controls. Do not expose bearer tokens in localStorage.
- Deployment: Contabo Docker Compose, separate LawDesk app/database network and isolated secrets. Share host HTTPS reverse proxy safely with Matbakhy through distinct hostname and upstream; do not bind competing listeners to 443.
- Staging deploy on merge to develop AFTER passing CI and required QA gates. Never deploy an unverified change solely because CI infrastructure is missing.
- Branching: main stable, develop integration, feature/* and docs/* PRs, no direct release merges without gate evidence.
- Product scope separate from Agentic/Agile SDLC implementation.
