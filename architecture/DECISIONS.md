# LawDesk v1 architecture decisions

- Backend: Java 21, Spring Boot 4.1.x (owner decision 2026-10-23, replacing 3.x — see "Spring Boot line" below), Spring Security REST APIs.
- Frontend: React and TypeScript. Mobile-responsive Arabic and English user experience.
- Database: PostgreSQL; use versioned migrations and isolated test DB.
- Identity: NO Keycloak or other external identity-management provider. Spring Security + application database handle login, users, roles and permissions. Passwords must be securely hashed; enforce authorization server-side.
- Session transport and CSRF protection: design and document during Phase 2; prefer a secure HttpOnly cookie based approach for browser login with appropriate CSRF controls. Do not expose bearer tokens in localStorage.
- Deployment: Contabo Docker Compose, separate LawDesk app/database network and isolated secrets. Share host HTTPS reverse proxy safely with Matbakhy through distinct hostname and upstream; do not bind competing listeners to 443.
- Staging deploy on merge to develop AFTER passing CI and required QA gates. Never deploy an unverified change solely because CI infrastructure is missing.
- Branching: main stable, develop integration, feature/* and docs/* PRs, no direct release merges without gate evidence.
- Product scope separate from Agentic/Agile SDLC implementation.

## Spring Boot line (owner decision 2026-10-23)

Spring Boot 3.5, the last 3.x line, reached end of open-source support on 2026-06-30. The owner approved moving to
**Spring Boot 4.1.x** (OSS support until 2027-07-31) with Java 21. Use the latest 4.1.x patch; at adoption this is
**4.1.2**. All framework dependencies take their versions from the Spring Boot BOM — do not override them without a
recorded reason. Versions managed by 4.1.2:

| Component | Version |
|-----------|---------|
| Spring Framework | 7.0.10 |
| Spring Security | 7.1.2 |
| Spring Data (JPA) | 2026.0.2 (Hibernate ORM 7.4.13.Final) |
| Flyway | 12.4.0 (with `flyway-database-postgresql`) |
| PostgreSQL JDBC driver | 42.7.14 |
| Jackson | 3.1.7 |
| JUnit Jupiter | 6.0.3 |
| Testcontainers | 2.0.5 |
| Embedded Tomcat | 11.0.27 |

Compatibility is verified by building and running the test suite against PostgreSQL (LD-002 for Web/Security/Actuator,
LD-004 for Data JPA, Flyway and the driver); evidence lives under `docs/evidence/`. Upgrade to 4.2.x, or a later
supported line, before 4.1 support ends.
