# Sprint 1 — Foundation & Core Platform
**Duration:** 6 weeks
**Theme:** Make the platform runnable end-to-end with JWT-authenticated REST API, standalone HTML/CSS/JS frontend, administration, and the Risk Management module fully functional.

> **Architecture note:** The UI (`tss-grc-ui/`) and backend (`src/`) are separate projects.
> The backend is a pure REST API (no Thymeleaf). The frontend is plain HTML + CSS + JS
> served by `npx live-server` in development and Nginx in production.
> See [docs/architecture/20-frontend-architecture.md](../docs/architecture/20-frontend-architecture.md).

---

## Goals

By the end of Sprint 1, a signed-in user can:
- Log in with username/password (Arabic or English UI)
- See a live dashboard with real data from the database
- Navigate the full sidebar (access guarded by permissions)
- Manage users, roles, and organisations (Admin module)
- Create, assess, treat, and close risks (ERM module)
- Receive in-app and email notifications for assigned tasks

---

## Deliverables by Layer

### Database (Flyway Migrations)
| Migration File | Description |
|---|---|
| `V1.0.0__create_schemas.sql` | All 11 schemas |
| `V1.1.0__shared_tables.sql` | organisations, business_units, lookup_tables, translations |
| `V1.2.0__iam_tables.sql` | users, roles, permissions, user_roles, role_permissions |
| `V1.3.0__platform_tables.sql` | modules, licenses, control_frameworks, controls |
| `V1.4.0__workflow_tables.sql` | workflow_definitions, workflow_instances, workflow_tasks |
| `V1.5.0__audit_tables.sql` | audit_logs, notifications |
| `V1.6.0__risk_tables.sql` | risk_categories, risks, risk_assessments, risk_treatments, risk_indicators, risk_appetite_statements |
| `V1.7.0__indexes.sql` | All Sprint 1 indexes (filtered + covering) |
| `V1.8.0__views.sql` | vw_unified_risk_summary, vw_user_permissions |
| `R__seed_data.sql` | Default roles, permissions, lookup values, NCA ECC controls seed |

### Backend — Shared/Common
- [ ] `BaseEntity.java` — JPA auditing, soft delete, ROWVERSION
- [ ] `TenantContext.java` — thread-local org ID holder
- [ ] `AuditorAwareImpl.java` — Spring Security → JPA auditor
- [ ] `GlobalExceptionHandler.java` — @ControllerAdvice (404, 403, 500, validation errors)
- [ ] `LocaleConfig.java` — SessionLocaleResolver, LocaleChangeInterceptor
- [ ] `SecurityConfig.java` — form login, CSRF, CSP, session management, BCrypt(12)
- [ ] `WebMvcConfig.java` — view controllers, static resources
- [ ] `DataSourceConfig.java` — HikariCP + SQL Server Unicode settings
- [ ] `CacheConfig.java` — Caffeine (5-min TTL)
- [ ] `AsyncConfig.java` — ThreadPoolTaskExecutor for @Async
- [ ] `SchedulingConfig.java` — TaskScheduler for @Scheduled jobs

### Backend — IAM & JWT Authentication
- [ ] `User.java`, `Role.java`, `Permission.java` entities
- [ ] `UserRepository.java`, `RoleRepository.java`
- [ ] `CustomUserDetailsService.java` — loads user + permissions for JWT validation
- [ ] `GrcUserDetails.java` — Spring Security UserDetails with userId + organisationId
- [ ] `ModulePermissionEvaluator.java` — `hasPermission('RISK','READ')` support
- [ ] `JwtTokenProvider.java` — access token (15 min) + refresh token (7 days) generation/validation
- [ ] `JwtAuthenticationFilter.java` — validates Bearer token per request, sets TenantContext
- [ ] `AuthController.java` — `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`
- [ ] `CorsConfig.java` — allow `localhost:3000` in dev; same-origin in prod

### Backend — Admin Module
- [ ] `Organisation.java`, `BusinessUnit.java` entities + repos
- [ ] `UserManagementController.java` — CRUD, role assignment, password reset
- [ ] `RoleManagementController.java` — CRUD, permission assignment
- [ ] `LookupTableController.java` — manage dynamic lookup values
- [ ] `ModuleActivationController.java` — enable/disable modules per org
- [ ] `AdminDashboardController.java`

### Backend — Risk Module (ERM)
- [ ] `Risk.java`, `RiskCategory.java`, `RiskAssessment.java`, `RiskTreatment.java`, `RiskIndicator.java`, `RiskAppetiteStatement.java` entities
- [ ] All repositories with soft-delete `@Where` filters
- [ ] `RiskService.java` — lifecycle, scoring (5×5 matrix), status transitions
- [ ] `RiskKpiJob.java` — @Scheduled KRI threshold check (every 1h)
- [ ] `RiskController.java` — register, detail, create, edit, assess, treat
- [ ] `RiskHeatmapController.java` — heatmap data API endpoint

### Backend — Workflow Engine
- [ ] `WorkflowDefinition.java`, `WorkflowInstance.java`, `WorkflowTask.java` entities
- [ ] `WorkflowService.java` — state machine transitions, task assignment
- [ ] `SlaCheckJob.java` — @Scheduled every 15 min, auto-escalate overdue tasks
- [ ] `WorkflowController.java` — my tasks, approve, reject, comment

### Backend — Notifications
- [ ] `Notification.java` entity + `NotificationRepository.java`
- [ ] `NotificationService.java` — create in-app notifications
- [ ] `EmailService.java` — @Async SMTP with Thymeleaf bilingual templates
- [ ] `NotificationEventListener.java` — Spring ApplicationEvent handler

### Backend — Dashboard
- [ ] `DashboardController.java` — aggregates KPIs, heatmap data, recent risks, pending tasks
- [ ] `DashboardStatsDto.java` — KPI DTO
- [ ] `@Cacheable` on dashboard queries (Caffeine, 5-min TTL)

### Frontend — `tss-grc-ui/` Pages
- [ ] `login.html` — login page with JWT auth, bilingual (EN/AR), password visibility toggle
- [ ] `dashboard.html` — KPI cards, risk heatmap (Chart.js), bar chart, doughnut, recent items
- [ ] `risk/index.html` — risk register table with filters, pagination, export buttons
- [ ] `risk/form.html` — create/edit risk form (calls `POST/PUT /api/v1/risk/risks`)
- [ ] `risk/detail.html` — risk detail with assessment history, treatments, linked controls
- [ ] `admin/users.html`, `admin/roles.html`, `admin/organisations.html`
- [ ] `workflow/tasks.html` — task inbox with approve/reject actions (calls workflow API)
- [ ] `error/403.html`, `error/404.html`, `error/500.html` — static error pages

### Frontend — `tss-grc-ui/assets/`
- [ ] `css/style.css` — design tokens, layout, components (dark navy + accent blue)
- [ ] `css/rtl.css` — RTL overrides for Arabic layout
- [ ] `js/translations.js` — bilingual string bundle (EN + AR)
- [ ] `js/auth.js` — JWT storage (sessionStorage), login/logout, requireAuth() guard
- [ ] `js/api.js` — fetch wrapper with Bearer header, 401 refresh-retry, error handling
- [ ] `js/i18n.js` — language switcher, Bootstrap RTL CSS swap, `data-i18n` replacement
- [ ] `js/app.js` — shell renderer (sidebar + header), notification polling, flash messages
- [ ] `images/logo-white.svg`, `images/logo-dark.svg` — TSS branding assets

### Configuration & Bootstrapping
- [ ] `application.yml` — base config (datasource, JPA, Flyway, cache, mail, JWT, CORS)
- [ ] `application-dev.yml` — dev profile (debug logging, Swagger on, CORS for localhost:3000)
- [ ] `application-prod.yml` — prod profile (HTTPS, restricted actuator, CORS disabled)
- [ ] `tss-grc-ui/` — frontend project; started with `npx live-server --port=3000`

---

## Acceptance Criteria

1. `mvn spring-boot:run -Dspring.profiles.active=dev` starts without errors; `GET /actuator/health` returns UP
2. Flyway applies all migrations on a clean SQL Server Express 2019 database
3. `POST /api/auth/login` with `admin / Admin@12345` (seeded) returns `{ accessToken, refreshToken cookie, user }`
4. `login.html` in the frontend calls the login API and redirects to `dashboard.html` on success
5. Language toggle (EN ↔ عربي) on `login.html` switches layout direction without page reload; sidebar and all labels flip correctly
6. `GET /api/v1/risk/risks` with valid Bearer token returns paginated risk list
7. Creating a risk via the form calls `POST /api/v1/risk/risks`; response 201 with new risk
8. Admin can create a user via `admin/users.html`; new user can log in and sees only permitted modules
9. Expired access token triggers refresh flow; 401 after refresh failure redirects to login
10. Swagger UI at `/swagger-ui.html` lists all Sprint 1 endpoints (dev profile only)

---

## Definition of Done

- Unit tests for all Service classes (min 70% branch coverage via Jacoco)
- Integration test for each Controller (MockMvc, H2 in-memory)
- Flyway migrations run clean on both fresh install and upgrade from previous version
- No TODO comments in production code
- Arabic translation complete for all Sprint 1 UI strings
