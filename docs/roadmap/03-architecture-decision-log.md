# 03 - Architecture Decision Log (ADL)

All significant architectural decisions made during Phase 1 are recorded here. Each ADR (Architecture Decision Record) follows the format: Context → Decision → Consequences → Status.

---

## ADR-001: Modular Monolith as Initial Architecture

- **Status:** Accepted
- **Date:** 2025-Q1
- **Context:** Team size is 5–12 developers. Saudi government clients require on-premises deployment with minimal operational overhead. Microservices require container orchestration (K8s), service mesh, and distributed tracing expertise that the initial team does not have.
- **Decision:** Deploy as a single Spring Boot application with strict module boundaries enforced by package structure, inter-module service interfaces, and schema-per-module database design.
- **Consequences:**
  - Faster development iteration, single-JAR deployment, simple debugging
  - Module boundaries must be enforced by discipline and code review (no compiler enforcement)
  - Evolution path to microservices preserved: all cross-module communication via service interfaces, not direct repository access
- **Review trigger:** When any single module handles > 500 concurrent users or when team grows beyond 20 developers

---

## ADR-002: SQL Server 2019 Express Edition as Target Database

- **Status:** Accepted
- **Date:** 2025-Q1
- **Context:** Customer organisations standardise on SQL Server. The initial deployment target is Express Edition to minimise licensing costs for small deployments.
- **Decision:** Design all database objects to be compatible with SQL Server 2019 Express Edition (10 GB limit, no SQL Agent, no TDE, no table partitioning, no Full-Text Search).
- **Consequences:**
  - No Full-Text Search → use `LIKE '%term%'` for search (sufficient for initial scale)
  - No SQL Agent → use Spring `@Scheduled` for background jobs
  - No TDE → document BitLocker/OS-level encryption as mitigation
  - No table partitioning → use application-level archival (move rows to archive table monthly)
  - 10 GB limit → storage monitoring job warns at 8 GB (80%); documented upgrade path to SQL Server Standard

---

## ADR-003: Decoupled Frontend — Standalone HTML/CSS/JS Project

- **Status:** Accepted (supersedes earlier SSR/Thymeleaf consideration)
- **Date:** 2025-Q1
- **Context:** The UI layer must be independently deployable and maintainable. The backend REST API must be reusable by future mobile apps and third-party integrations without coupling to a server-side template engine.
- **Decision:** The frontend is a standalone static project (`tss-grc-ui/`) consisting of plain HTML5, CSS3, and vanilla JavaScript. The Spring Boot backend is a pure REST API with no Thymeleaf dependency. Communication uses JWT Bearer tokens.
- **Consequences:**
  - Thymeleaf, Thymeleaf Layout Dialect, and Thymeleaf Security Extras removed from `pom.xml`
  - CSRF protection not needed (JWT replaces session-based auth)
  - i18n handled client-side via `translations.js` and `data-i18n` attributes
  - RTL switching done at runtime by `i18n.js` without page reload
  - No build step needed for frontend; served by any static file server
  - In production: Nginx serves both frontend and proxies `/api/` to backend

---

## ADR-004: JWT for Authentication (Stateless)

- **Status:** Accepted
- **Date:** 2025-Q1
- **Context:** With a decoupled frontend, session-based authentication (JSESSIONID cookie) is not practical across origins in development and is overly stateful for an API-first backend.
- **Decision:** Use JJWT 0.12.6 for JWT generation/validation. Access tokens (15-minute TTL) sent as Bearer header; refresh tokens (7-day TTL) sent as HttpOnly, Secure, SameSite=Strict cookie.
- **Consequences:**
  - Backend is fully stateless — scales horizontally without shared session store
  - Access token stored in `sessionStorage` (cleared on tab close, not accessible to other tabs)
  - Refresh token rotation on every refresh (new refresh token replaces old)
  - Token revocation not instantaneous (access token valid until expiry — mitigated by 15-min TTL)

---

## ADR-005: Schema-Per-Module Database Strategy

- **Status:** Accepted
- **Date:** 2025-Q1
- **Context:** Module independence requires each module to own its data. Future microservice extraction requires schema portability without cross-module SQL joins in the data layer.
- **Decision:** 11 SQL Server schemas: `platform`, `iam`, `admin`, `workflow`, `risk`, `cyber`, `bcm`, `privacy`, `compliance`, `reporting`, `audit`. Each module's JPA repositories access only their own schema.
- **Consequences:**
  - Cross-module data access goes through service interfaces, not SQL joins
  - Some queries require application-level joins (acceptable at current scale)
  - Future: each schema can be extracted to its own database when a module becomes a microservice

---

## ADR-006: Tenant Isolation via Discriminator Column

- **Status:** Accepted
- **Date:** 2025-Q1
- **Context:** SaaS deployment requires multi-tenant data isolation. Database-per-tenant is too costly at initial scale. Schema-per-tenant conflicts with the module schema strategy.
- **Decision:** `organisation_id` column on all tenant-scoped tables. Set via `TenantContext` thread-local from the JWT `org` claim. JPA `@Where` or explicit `WHERE organisation_id = :orgId` in all queries.
- **Consequences:**
  - All repositories must include `organisation_id` in queries — enforced by code review
  - Evolution path: if a customer requires data isolation stronger than discriminator-column, migrate them to schema-per-tenant or database-per-tenant

---

## ADR-007: Flyway for Database Migrations

- **Status:** Accepted
- **Date:** 2025-Q1
- **Context:** Database schema evolves across sprints and must be reproducible and auditable.
- **Decision:** Flyway 10.x with versioned migrations (`V{major}.{minor}.{patch}__{desc}.sql`) and repeatable migrations (`R__{desc}.sql`) for seed data. `flyway.clean-disabled=true` in all profiles.
- **Consequences:**
  - Every schema change is version-controlled alongside application code
  - Rollback strategy: forward-only (write a new migration to undo, never delete existing migrations)

---

## ADR-008: Caffeine for L1 Caching

- **Status:** Accepted
- **Date:** 2025-Q1
- **Context:** Dashboard aggregations are expensive (cross-schema queries). A local in-process cache avoids repeated DB hits on the most-viewed page.
- **Decision:** Caffeine with 5-minute TTL for dashboard aggregations, permission lookups, and compliance scores. Cache keys always include `organisation_id` for tenant isolation.
- **Consequences:**
  - Data can be up to 5 minutes stale — acceptable for dashboard KPIs
  - No distributed cache needed for single-node Express deployment; Redis can be added when scaling to multiple nodes

---

## ADR-009: Arabic RTL as First-Class Requirement

- **Status:** Accepted
- **Date:** 2025-Q1
- **Context:** Saudi Arabian market requires full Arabic UI with RTL layout across all 10 modules.
- **Decision:** Bootstrap 5 RTL CSS swapped at runtime by `i18n.js`. All database user-facing text fields have `_en` / `_ar` column pairs. Translation bundle (`translations.js`) covers all UI strings in both languages.
- **Consequences:**
  - All new UI strings must be added to `translations.js` in both `en` and `ar` sections before a feature is complete
  - All new DB columns for user-facing text must follow the `_en` / `_ar` pattern
  - RTL layout must be QA-tested as part of the definition of done for every sprint
