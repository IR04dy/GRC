# 02 - Phase 1 Delivery Summary

## What Phase 1 Delivers

Phase 1 (Analysis & Architecture) produces a complete, implementation-ready package for the TSS GRC Platform. No code has been written for the functional modules yet — this phase establishes the blueprint that the development team will execute across 3 implementation sprints.

---

## Deliverable Inventory

### Analysis Documents (`/docs/analysis/`) — 16 files

| File | Content |
|---|---|
| 01-executive-summary.md | Platform vision, value proposition, market context |
| 02-business-objectives.md | Business goals and success metrics |
| 03-problem-statement.md | Pain points in the Saudi GRC market |
| 04-platform-scope.md | In-scope / out-of-scope boundaries |
| 05-assumptions-constraints.md | Project assumptions and constraints |
| 06-success-criteria.md | Measurable success criteria |
| 07-stakeholder-analysis.md | Key stakeholders and their interests |
| 08-user-roles-personas.md | 13 user roles with personas |
| 09-module-definitions.md | Full definition of all 10 modules |
| 10-module-boundaries.md | Cross-module interaction rules |
| 11-functional-requirements.md | 120+ functional requirements (FR-PLT through FR-ADM) |
| 12-non-functional-requirements.md | Performance, security, availability NFRs |
| 13-multilingual-requirements.md | Arabic/English bilingual requirements |
| 14-licensing-model.md | Starter / Professional / Enterprise tier definitions |
| 15-deployment-model.md | On-premises vs SaaS deployment options |
| 16-success-criteria.md | KPIs and acceptance criteria |

### Architecture Documents (`/docs/architecture/`) — 20 files

| File | Content |
|---|---|
| 01–06 | High-level system, logical layers, module interactions, application layer (REST API), DDD decomposition, shared services |
| 07 | Workflow engine (state machine, SLA scheduling) |
| 08 | Reporting architecture (Chart.js, Apache POI, OpenPDF, async export) |
| 09 | Notification architecture (Spring Events, SMTP, in-app) |
| 10 | Audit & evidence architecture (INSERT-only logs, temporal tables, hash chain) |
| 11 | IAM architecture (JWT, RBAC, module permissions) |
| 12 | API & integration architecture (REST API design, OpenAPI 3.0) |
| 13 | Security architecture (OWASP, JWT, BCrypt, CSP headers) |
| 14 | Tenant organisation model (discriminator-column multi-tenancy) |
| 15 | Configuration & branding model |
| 16–19 | Scalability, availability, observability, backup/DR |
| **20** | **Frontend architecture (standalone tss-grc-ui/ HTML/CSS/JS project)** |

### Domain Model Documents (`/docs/domain/`) — 14 files

| File | Content |
|---|---|
| 01–06 | Domain overview, core entities, relationships, master data, tenant/org structure, RBAC model |
| 07 | Control library model (NCA ECC, ISO 27001, PDPL, ISO 22301) |
| 08 | Risk model (5×5 matrix, appetite, KRI) |
| 09 | Incident & case management model |
| 10 | BCM model (BIA, plans, exercises, RTO/RPO) |
| 11 | Privacy & PDPL model (RoPA, DPIA, breaches, DSR, consent) |
| 12 | Compliance assessment model (findings, evidence, remediation) |
| 13 | Reporting & analytics data model |
| 14 | Audit trail & evidence chain model |

### Database Design (`/docs/database/`) — 15 files

Complete physical database design for SQL Server 2019 Express Edition including Express-specific constraint handling (10 GB limit, no SQL Agent, no TDE, no partitioning).

### Database DDL (`/db/sqlserver/`) — Complete scripts

- Schema creation (11 schemas)
- Shared platform tables (19 tables)
- Module tables (7 files covering all 10 modules)
- Indexes (filtered + covering, Express-compatible)
- Views (unified risk, compliance status, user permissions, incident summary)
- Flyway migration strategy and naming conventions

### Backend Project Skeleton (`/src/`) — Key files

| File | Purpose |
|---|---|
| `GrcApplication.java` | Spring Boot entry point with all enable annotations |
| `BaseEntity.java` | JPA auditing, soft delete, ROWVERSION |
| `SecurityConfig.java` | Stateless JWT, Spring Security filter chain |
| `JwtTokenProvider.java` | Access (15 min) + refresh (7 days) token management |
| `JwtAuthenticationFilter.java` | Bearer token validation per request |
| `AuthController.java` | POST /api/auth/login, /refresh, /logout |
| `CorsConfig.java` | Dev CORS (localhost:3000); prod same-origin |
| `GrcUserDetails.java` | UserDetails with userId + organisationId |
| `ModulePermissionEvaluator.java` | hasPermission('RISK','READ') evaluator |
| `TenantContext.java` | Thread-local tenant discriminator |
| `LocaleConfig.java` | Message source configuration |
| `WebMvcConfig.java` | Static resource handlers, view controllers |
| `DataSourceConfig.java` | HikariCP + SQL Server Unicode settings |
| `application.yml` | Base configuration (JWT, CORS, cache, mail) |
| `application-dev.yml` | Dev overrides (Swagger on, CORS localhost:3000) |
| `application-prod.yml` | Prod overrides (HTTPS, restricted actuator) |
| `pom.xml` | Spring Boot 3.5, JJWT 0.12.6, POI 5.2.5, OpenPDF |
| Module `package-info.java` | All 8 module packages documented |

### Frontend Project (`/tss-grc-ui/`) — Standalone HTML/CSS/JS

| File | Purpose |
|---|---|
| `index.html` | Entry point — redirects to login or dashboard |
| `login.html` | JWT login page with RTL support |
| `dashboard.html` | Executive dashboard (KPIs, heatmap, charts) |
| `assets/js/auth.js` | JWT sessionStorage, requireAuth(), logout |
| `assets/js/api.js` | Fetch wrapper with Bearer header and 401 refresh |
| `assets/js/i18n.js` | Language switcher, Bootstrap RTL CSS swap |
| `assets/js/app.js` | Shell renderer (sidebar + header), notification polling |
| `assets/js/translations.js` | Full EN + AR translation bundle |
| `assets/css/style.css` | Design tokens, layout, KPI cards, heatmap |
| `assets/css/rtl.css` | Arabic RTL overrides |

### Sprint Plans (`/sprints/`) — 3 files

Detailed 6-week sprint plans covering all 10 modules across 3 sprints with backend deliverables, frontend pages, acceptance criteria, and definition of done.

---

## Key Architecture Decisions Made in Phase 1

| Decision | Choice | Rationale |
|---|---|---|
| Architecture style | Modular monolith | Team size, operational simplicity, on-prem requirement |
| Database | SQL Server 2019 Express | Customer mandate; Express edition constraints documented |
| Frontend/Backend separation | Decoupled (standalone UI) | Independent deployment, API reusability, no SSR lock-in |
| Authentication | JWT (stateless) | No sessions needed; same API serves future mobile clients |
| i18n/L10n | Client-side JS bundle | Frontend-owned; no server-side rendering required |
| Export | Apache POI + OpenPDF | Arabic RTL support; no external service dependency |
| Scheduling | Spring @Scheduled | Replaces SQL Server Agent (not available in Express) |
| Audit trail | INSERT-only + hash chain | Tamper-evident; satisfies NCA ECC audit evidence controls |
