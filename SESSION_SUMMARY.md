# TSS GRC Platform — Full Session Summary

> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](CLAUDE.md) and [`PROJECT_CHRONICLE.md`](PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*

> **Generated**: 2026-04-07
> **Purpose**: Complete handoff document for continuation in a new session
> **Sprint Implemented**: Sprint 1 only (Sprints 2 & 3 not started)

---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Repository Structure](#2-repository-structure)
3. [Technology Stack](#3-technology-stack)
4. [Documentation Library](#4-documentation-library)
5. [Database Design](#5-database-design)
6. [Backend Implementation](#6-backend-implementation)
7. [Frontend Implementation](#7-frontend-implementation)
8. [Sprint Plans](#8-sprint-plans)
9. [Environment & Configuration](#9-environment--configuration)
10. [Bugs Fixed & Key Decisions](#10-bugs-fixed--key-decisions)
11. [Current System State](#11-current-system-state)
12. [Pending Items](#12-pending-items)
13. [How to Start the System](#13-how-to-start-the-system)

---

## 1. Project Overview

**TSS GRC Platform** — Trusted Security Solutions Enterprise Governance, Risk & Compliance Platform.

| Attribute | Value |
|-----------|-------|
| **Target Market** | Saudi Arabia & MENA region |
| **Language** | Bilingual — Arabic (primary) + English |
| **Architecture** | Modular Monolith (Spring Boot) + Standalone Frontend |
| **Backend Port** | 8084 |
| **Database** | SQL Server 2019 Express (`TSS_GRC`) |
| **Java** | 17 (NOT 25 — Lombok incompatibility) |
| **Build Tool** | Maven 3.9.x (from Apache NetBeans) |

### Design Colors
- **Primary (Navy)**: `#1a2744`
- **Accent (Blue)**: `#00a8e0`
- **Font**: Inter (Google Fonts)

### Regulatory Frameworks Supported
- NCA Essential Cybersecurity Controls (ECC) v2.0
- NCA Critical Systems Cybersecurity Controls (CSCC)
- ISO 27001:2022, ISO 27701:2019, ISO 22301:2019, ISO 31000:2018
- Saudi Personal Data Protection Law (PDPL) 2021
- SAMA Cyber Security Framework (CSF)

---

## 2. Repository Structure

```
C:\AYMAN\Github\Claude-GRC\
│
├── SESSION_SUMMARY.md                    ← THIS FILE
├── README.md
│
├── docs/
│   ├── analysis/       (16 .md files)   ← Business requirements & analysis
│   ├── architecture/   (20 .md files)   ← System & application architecture
│   ├── database/       (15 .md files)   ← DB design, schema, naming, indexing
│   ├── diagrams/       (18 .md files)   ← Mermaid ERDs, C4, flow diagrams
│   ├── domain/         (14 .md files)   ← Domain models per module
│   └── roadmap/        (5 .md files)    ← Delivery plan, ADRs, risks
│
├── sprints/
│   ├── sprint-1.md                      ← Foundation (IMPLEMENTED ✅)
│   ├── sprint-2.md                      ← Domain Modules (NOT STARTED ❌)
│   └── sprint-3.md                      ← Production Readiness (NOT STARTED ❌)
│
├── tss-grc-backend/                     ← Spring Boot REST API
│   ├── pom.xml
│   ├── src/main/java/com/tss/grc/
│   ├── src/main/resources/
│   └── db/sqlserver/                    ← DDL scripts (schema, tables, indexes, views)
│
└── tss-grc-ui/                          ← Standalone HTML/CSS/JS Frontend
    ├── index.html
    ├── login.html
    ├── dashboard.html
    ├── risk/
    ├── admin/
    ├── error/
    └── assets/
        ├── css/
        └── js/
```

---

## 3. Technology Stack

### Backend
| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Spring Boot | 3.5.0 |
| Language | Java | 17 |
| Build | Maven | 3.9.x |
| ORM | Hibernate / Spring Data JPA | 6.x |
| Database | Microsoft SQL Server | 2019 Express |
| Security | Spring Security + JWT | 6.x / JJWT 0.12.6 |
| Cache | Caffeine | 3.1.8 |
| Code Gen | Lombok | 1.18.30 |
| Mapping | MapStruct | 1.5.5 |
| API Docs | SpringDoc OpenAPI | 2.5.0 |
| Migrations | Flyway | 10.15.0 (disabled in dev) |
| Excel Export | Apache POI | 5.2.5 |
| PDF Export | OpenPDF | 1.3.34 |
| LDAP | Spring LDAP | (optional, for AD) |

### Frontend
| Component | Technology |
|-----------|-----------|
| HTML | HTML5 (no template engine) |
| CSS | Bootstrap 5 RTL |
| JavaScript | Vanilla ES2020 (no framework) |
| Charts | Chart.js 4.x |
| Auth Storage | sessionStorage (JWT access token) |
| Dev Server | npx live-server |

### Database
| Attribute | Value |
|-----------|-------|
| RDBMS | Microsoft SQL Server 2019 |
| Database Name | `TSS_GRC` |
| Schemas | 11 (platform, iam, admin, workflow, risk, cyber, bcm, privacy, compliance, reporting, audit) |
| Collation | `Latin1_General_100_CI_AS_SC_UTF8` |
| Auth | User: `sa`, Password: `tss.123`, Port: `1433` |

---

## 4. Documentation Library

### 4.1 Analysis Documents (`docs/analysis/`)
| File | Content |
|------|---------|
| `01-executive-summary.md` | Platform overview, market opportunity, strategic positioning |
| `02-product-vision.md` | Vision statement, mission, value proposition |
| `03-business-goals.md` | Revenue targets, market penetration, adoption goals |
| `04-problem-statement.md` | Pain points: fragmented GRC tools, Arabic gap, compliance burden |
| `05-scope-and-non-scope.md` | What's in/out of scope for v1 |
| `06-assumptions-and-constraints.md` | Deployment environment, connectivity, regulatory constraints |
| `07-success-criteria.md` | Measurable acceptance criteria per sprint |
| `08-stakeholder-analysis.md` | CISOs, DPOs, Auditors, IT, Compliance Officers |
| `09-user-roles-and-personas.md` | Platform Admin, Risk Manager, Auditor, Viewer, etc. |
| `10-module-definitions.md` | 7 modules: ERM, Cyber, BCM, Privacy, Compliance, DataGov, Reporting |
| `11-module-boundaries.md` | Inter-module interfaces and data contracts |
| `12-functional-requirements.md` | Full functional requirements per module |
| `13-non-functional-requirements.md` | Performance (P95<3s, 50 users), security, availability 99.5% |
| `14-multilingual-requirements.md` | Arabic-first, RTL, bidirectional support, Unicode |
| `15-licensing-model.md` | Per-module subscriptions, user-based pricing |
| `16-deployment-model.md` | On-premises (gov), SaaS (commercial), hybrid |

### 4.2 Architecture Documents (`docs/architecture/`)
| File | Content |
|------|---------|
| `01-high-level-system-architecture.md` | Modular monolith, layered design, API gateway |
| `02-logical-architecture.md` | Presentation → API → Service → Domain → Infrastructure layers |
| `03-module-interaction-architecture.md` | Spring ApplicationEvent for cross-module communication |
| `04-application-layer-architecture.md` | Controller/Service/Repository/Entity layering |
| `05-domain-driven-decomposition.md` | Bounded contexts per module, shared kernel |
| `06-shared-services-architecture.md` | Audit, notifications, workflow, attachments |
| `07-workflow-engine-architecture.md` | State machine engine, workflow instances |
| `08-reporting-architecture.md` | Report definitions, scheduled execution, bilingual PDF |
| `09-notification-architecture.md` | In-app + email + WebSocket push notifications |
| `10-audit-evidence-architecture.md` | Immutable audit trail, evidence attachments |
| `11-iam-architecture.md` | Users, roles, permissions, tenant isolation |
| `12-api-integration-architecture.md` | External system connectors, LDAP/AD, SIEM |
| `13-security-architecture.md` | JWT, RBAC, CSP headers, MFA, input validation |
| `14-tenant-organization-model.md` | Multi-tenant: org hierarchy, tenant_id discriminator |
| `15-configuration-branding-model.md` | Per-tenant branding, logos, color themes |
| `16-scalability-performance.md` | Horizontal scaling, Caffeine L1, query optimization |
| `17-availability-resilience.md` | HA setup, failover, health checks, circuit breakers |
| `18-observability-logging.md` | Logback, MDC, metrics, distributed tracing |
| `19-backup-disaster-recovery.md` | SQL Server backup, RTO/RPO targets |
| `20-frontend-architecture.md` | Standalone HTML/JS, api.js wrapper, auth guards |

### 4.3 Database Documents (`docs/database/`)
| File | Content |
|------|---------|
| `01-database-architecture.md` | Single SQL Server, schema-per-module isolation |
| `02-schema-design-strategy.md` | BaseEntity pattern, audit columns, soft deletes |
| `03-naming-conventions.md` | snake_case tables, camelCase Java, prefix by module |
| `04-indexing-strategy.md` | Clustered on PK, non-clustered on FK+filter columns |
| `05-partitioning-archive-strategy.md` | Date-based partitioning for large tables |
| `06-audit-history-versioning.md` | Temporal tables for change history |
| `07-soft-delete-retention.md` | `is_deleted`, `deleted_at`, retention policies |
| `08-localization-storage.md` | Dual-column (field_en, field_ar) + translations table |
| `09-attachment-metadata-model.md` | File metadata in DB, binary on filesystem |
| `10-reporting-export-strategy.md` | Materialized aggregations, scheduled jobs |
| `11-conceptual-database-design.md` | High-level entity overview |
| `12-logical-database-design.md` | Entity attributes and relationships |
| `13-physical-database-design.md` | SQL Server-specific types, indexes, ROWVERSION |
| `14-proposed-schemas.md` | DDL reference for all 11 schemas |
| `15-migration-versioning-strategy.md` | Flyway naming: `V{version}__{description}.sql` |

### 4.4 Diagram Documents (`docs/diagrams/`) — Mermaid Format
| File | Diagram Type |
|------|-------------|
| `01-context-diagram.md` | C4 Context: system in environment |
| `02-container-architecture.md` | C4 Container: backend, frontend, DB, cache |
| `03-module-interaction-diagram.md` | Module communication map |
| `04-erd-core-shared.md` | ERD: platform + iam + shared tables |
| `05-erd-risk-module.md` | ERD: risk schema tables |
| `06-erd-cyber-module.md` | ERD: cyber schema tables |
| `07-erd-bcm-module.md` | ERD: bcm schema tables |
| `08-erd-privacy-module.md` | ERD: privacy schema tables |
| `09-erd-compliance-module.md` | ERD: compliance schema tables |
| `10-schema-module-diagram.md` | All 11 schemas and their table counts |
| `11-workflow-state-diagram.md` | Workflow state machine transitions |
| `12-risk-assessment-flow.md` | Risk identification → assessment → treatment flow |
| `13-incident-management-flow.md` | Cyber incident → escalation → risk linkage flow |
| `14-compliance-assessment-flow.md` | Framework assessment → finding → remediation flow |
| `15-unified-risk-view.md` | Cross-module risk aggregation logic |
| `16-deployment-diagram.md` | On-prem and SaaS deployment topologies |
| `17-security-architecture-diagram.md` | JWT flow, RBAC enforcement, session management |
| `18-data-flow-diagram.md` | Data flows: user → API → DB → response |

### 4.5 Domain Documents (`docs/domain/`)
| File | Content |
|------|---------|
| `01-domain-model-overview.md` | Bounded contexts, aggregate roots |
| `02-core-entities-list.md` | All entities with fields summary |
| `03-entity-relationships.md` | Cross-entity relationships |
| `04-master-data-model.md` | Lookups, enumerations, reference data |
| `05-tenant-org-structure.md` | Organization hierarchy, business units |
| `06-user-role-permission-model.md` | RBAC model: users → roles → permissions |
| `07-control-library-model.md` | Controls, frameworks, mappings |
| `08-risk-model.md` | Risk register, categories, assessments, treatments, KRIs |
| `09-incident-case-model.md` | Cyber incidents, severity, SLA |
| `10-bcm-model.md` | BIA, BCP, exercises, recovery |
| `11-privacy-model.md` | ROPA, DPIA, breaches, DSR, consent |
| `12-compliance-assessment-model.md` | Assessments, findings, evidence, remediation |
| `13-reporting-data-model.md` | Report definitions, executions, schedules |
| `14-audit-trail-model.md` | Immutable logs, temporal tables |

### 4.6 Roadmap Documents (`docs/roadmap/`)
| File | Content |
|------|---------|
| `01-implementation-roadmap.md` | 3-sprint phased delivery plan |
| `02-delivery-summary.md` | Feature summary and release notes per sprint |
| `03-architecture-decision-log.md` | ADRs: why modular monolith, why SQL Server, why standalone frontend, etc. |
| `04-open-issues-register.md` | Unresolved design questions |
| `05-risk-register.md` | Project risks and mitigation |

---

## 5. Database Design

### 5.1 Schemas (11 total)
```sql
CREATE SCHEMA platform;     -- Orgs, users, roles, permissions, notifications
CREATE SCHEMA iam;          -- Identity & Access Management
CREATE SCHEMA admin;        -- Admin/system configuration
CREATE SCHEMA workflow;     -- Workflow engine
CREATE SCHEMA risk;         -- Enterprise Risk Management
CREATE SCHEMA cyber;        -- Cybersecurity
CREATE SCHEMA bcm;          -- Business Continuity Management
CREATE SCHEMA privacy;      -- Data Privacy / PDPL
CREATE SCHEMA compliance;   -- Compliance & Audit
CREATE SCHEMA reporting;    -- Reports & Analytics
CREATE SCHEMA audit;        -- Immutable audit trail
```

### 5.2 BaseEntity Pattern (all tables inherit)
```java
// com.tss.grc.common.model.BaseEntity
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate   private LocalDateTime createdAt;
    @CreatedBy     private String createdBy;
    @LastModifiedDate private LocalDateTime updatedAt;
    @LastModifiedBy   private String updatedBy;

    private Boolean isDeleted = false;
    private LocalDateTime deletedAt;
    private String deletedBy;

    @Version private byte[] rowVersion;   // SQL Server ROWVERSION for optimistic locking
}
```

### 5.3 Shared Tables (platform schema)
| Table | Purpose |
|-------|---------|
| `platform.organizations` | Tenant/org hierarchy (parent-child) |
| `platform.business_units` | Departments within organizations |
| `platform.users` | User accounts, password hash, MFA |
| `platform.roles` | Role definitions |
| `platform.permissions` | Permission definitions (module + action) |
| `platform.user_roles` | User ↔ Role many-to-many |
| `platform.role_permissions` | Role ↔ Permission many-to-many |
| `platform.modules` | Module catalogue |
| `platform.licenses` | Org ↔ Module subscription |
| `platform.controls` | Control library (NCA, ISO, PDPL) |
| `platform.control_frameworks` | Framework definitions |
| `platform.control_framework_mappings` | Control ↔ Framework |
| `platform.workflows` | Workflow definitions |
| `platform.workflow_instances` | Executed workflow instances |
| `platform.attachments` | File metadata (binary on filesystem) |
| `platform.audit_logs` | Immutable audit trail |
| `platform.notifications` | In-app notifications |
| `platform.lookup_tables` | Dynamic reference data |
| `platform.translations` | Bilingual text key-value store |

### 5.4 Risk Module Tables (risk schema)
| Table | Purpose |
|-------|---------|
| `risk.risk_categories` | Risk domains (Strategic, Operational, Financial, IT, etc.) |
| `risk.risks` | Risk register (computed `inherent_score`, `residual_score`, `risk_level`) |
| `risk.risk_assessments` | Assessment snapshots (likelihood 1-5, impact 1-5) |
| `risk.risk_treatments` | Treatment plans (Accept, Mitigate, Transfer, Avoid) |
| `risk.risk_indicators` | KRIs with threshold/current value |
| `risk.risk_appetite_statements` | Organizational risk appetite definitions |

### 5.5 Seeded Users
| Username | Password | Role | BCrypt Hash |
|----------|----------|------|-------------|
| `admin` | `Admin@12345` | PLATFORM_ADMIN | `$2a$10$DQZCHvnbmQclQpO50tajUO...` |
| `riskmanager` | `Risk@12345` | RISK_MANAGER | `$2a$10$VkuWC6XY6GZrUhsQoZOSNe...` |
| `auditor` | `Audit@12345` | AUDITOR | `$2a$10$18IjNU7Y1p7Yu7yFzCzGZ....` |

> **Note**: BCrypt hashes were generated using Maven exec:java (HashPrinter class with Spring Security BCryptPasswordEncoder). Direct shell generation failed due to `$` sign expansion in bash.

### 5.6 SQL DDL Files Location
```
tss-grc-backend/db/sqlserver/
├── schema/
│   ├── 00-create-database.sql
│   └── 01-create-schemas.sql
├── tables/
│   ├── shared/   (01 through 19 — platform tables)
│   └── modules/  (01-risk through 07-reporting)
├── indexes/
│   ├── 01-shared-indexes.sql
│   └── 02-module-indexes.sql
├── views/
│   ├── 01-unified-risk-view.sql
│   └── 02-user-permissions-view.sql
└── migrations/
    └── README.md
```

---

## 6. Backend Implementation

### 6.1 Java Package Structure
```
com.tss.grc/
├── GrcApplication.java
│
├── config/
│   ├── CacheConfig.java          ← Caffeine: 5-min TTL, 1000 entries
│   ├── CorsConfig.java           ← CORS: localhost:3000, 5500
│   ├── DataSourceConfig.java     ← HikariCP pool (5-20 connections)
│   ├── LocaleConfig.java         ← EN/AR locale resolution
│   ├── OpenApiConfig.java        ← Swagger with bearerAuth scheme
│   ├── SecurityConfig.java       ← JWT filter chain, BCrypt(12), permitAll list
│   └── WebMvcConfig.java         ← Static resources, view controllers
│
├── common/
│   ├── exception/
│   │   └── GlobalExceptionHandler.java  ← RFC 7807 ProblemDetail responses
│   ├── model/
│   │   ├── BaseEntity.java              ← JPA auditing + soft delete base
│   │   └── AuditorAwareImpl.java        ← SecurityContext → JPA auditor
│   └── tenant/
│       └── TenantContext.java           ← ThreadLocal tenant ID
│
├── security/
│   ├── JwtTokenProvider.java            ← Access (15m) + Refresh (7d) tokens
│   ├── JwtAuthenticationFilter.java     ← Bearer token → SecurityContext
│   ├── GrcUserDetails.java              ← userId, orgId, roles
│   └── ModulePermissionEvaluator.java   ← hasPermission('RISK','READ')
│
└── module/
    ├── auth/
    │   └── AuthController.java          ← POST /api/auth/{login,refresh,logout}
    │
    ├── dashboard/
    │   ├── controller/DashboardController.java
    │   ├── dto/{DashboardStats,HeatmapData,ChartData}.java
    │   └── service/DashboardService.java    ← @Cacheable on stats/heatmap
    │
    ├── iam/
    │   ├── controller/{UserController,RoleController}.java
    │   ├── dto/{UserRequest,UserResponse}.java
    │   ├── entity/{User,Role,Permission}.java
    │   ├── repository/{UserRepository,RoleRepository}.java
    │   └── service/{UserService,CustomUserDetailsService}.java
    │
    ├── platform/
    │   ├── controller/NotificationController.java
    │   ├── dto/NotificationResponse.java
    │   ├── entity/{Organisation,Notification}.java
    │   ├── repository/{OrganisationRepository,NotificationRepository}.java
    │   └── service/NotificationService.java
    │
    ├── risk/
    │   ├── controller/{RiskController,RiskCategoryController}.java
    │   ├── dto/{RiskRequest,RiskResponse}.java
    │   ├── entity/{Risk,RiskCategory,RiskAssessment,RiskTreatment}.java
    │   ├── repository/{RiskRepository,RiskCategoryRepository,...}.java
    │   └── service/RiskService.java
    │
    └── (bcm, compliance, cyber, datagov, privacy, reporting, workflow)
        └── package-info.java   ← Placeholder for Sprint 2/3
```

### 6.2 REST API Endpoints

#### Authentication
```
POST   /api/auth/login          → { accessToken, refreshToken, expiresIn }
POST   /api/auth/refresh        → { accessToken }
POST   /api/auth/logout         → 204
```

#### Dashboard (all require isAuthenticated())
```
GET    /api/v1/dashboard/stats              → DashboardStats
GET    /api/v1/dashboard/heatmap            → HeatmapData
GET    /api/v1/dashboard/risk-by-category   → ChartData
GET    /api/v1/dashboard/recent-risks       → List<RiskResponse>
```

#### Risk Management
```
GET    /api/v1/risk/risks           → Page<RiskResponse>  [?status=OPEN]
POST   /api/v1/risk/risks           → RiskResponse (201)
GET    /api/v1/risk/risks/{id}      → RiskResponse
PUT    /api/v1/risk/risks/{id}      → RiskResponse
DELETE /api/v1/risk/risks/{id}      → 204 (soft delete)
GET    /api/v1/risk/categories      → List<RiskCategory>
```

#### User Management (PLATFORM_ADMIN only)
```
GET    /api/v1/admin/users          → Page<UserResponse>
POST   /api/v1/admin/users          → UserResponse (201)
GET    /api/v1/admin/users/{id}     → UserResponse
PUT    /api/v1/admin/users/{id}     → UserResponse
DELETE /api/v1/admin/users/{id}     → 204 (soft delete)
GET    /api/v1/admin/roles          → List<Role>
```

#### Notifications
```
GET    /api/v1/notifications/unread-count   → { count: N }
GET    /api/v1/notifications/list           → List<NotificationResponse>
PUT    /api/v1/notifications/{id}/read      → 204
PUT    /api/v1/notifications/read-all       → 204
```

#### System
```
GET    /swagger-ui.html                     → Swagger UI (dev only)
GET    /v3/api-docs                         → OpenAPI JSON
GET    /actuator/health                     → Health status
```

### 6.3 Security Configuration
```java
// SecurityConfig.java — key decisions:
.requestMatchers("/api/auth/**").permitAll()
.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
.requestMatchers("/actuator/health").permitAll()
.anyRequest().authenticated()

// Password encoding
@Bean BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

### 6.4 Cache Configuration
```java
// CacheConfig.java
// Named caches with 5-minute TTL, max 1000 entries:
"dashboardStats"    // Keyed by organisationId
"heatmapData"       // Keyed by organisationId
"riskByCategory"    // Keyed by organisationId
"permissions"       // Keyed by userId
```

### 6.5 JWT Filter (Critical Fix)
```java
// JwtAuthenticationFilter.java
// CRITICAL: Must use loadUserById(Long), NOT loadUserByUsername(String)
// If you call loadUserByUsername("1"), it looks for username="1" and returns null → 403

private final CustomUserDetailsService userDetailsService;  // NOT generic UserDetailsService

Long userId = tokenProvider.getUserIdFromToken(token);
UserDetails userDetails = userDetailsService.loadUserById(userId);  // ← Correct
```

### 6.6 Resource Files
```
src/main/resources/
├── application.yml           ← Base config (all profiles)
├── application-dev.yml       ← Dev overrides (port 8084, sa/tss.123, Flyway off)
├── application-prod.yml      ← Prod overrides (SSL, env vars, Flyway on)
├── messages.properties       ← English i18n messages
├── messages_ar.properties    ← Arabic i18n messages
└── db/migration/sqlserver/   ← Flyway scripts (used in prod)
```

---

## 7. Frontend Implementation

### 7.1 Pages
| File | Description |
|------|-------------|
| `index.html` | Entry — redirect to login or dashboard |
| `login.html` | Split-panel login (left: navy brand, right: white form), EN/AR switcher, shake animation |
| `dashboard.html` | KPI cards, 5×5 risk heatmap, charts, recent risks table |
| `risk/index.html` | Risk register — filter bar, paginated table, color-coded severity badges |
| `risk/form.html` | Create/Edit risk — 1-5 visual rating buttons, live score calculator, mini heatmap preview |
| `risk/detail.html` | Risk detail — assessment history, treatment plans, linked controls |
| `admin/users.html` | User management — avatar initials, role badges, inline status toggle, create/edit modal |
| `error/403.html` | Access denied page |
| `error/404.html` | Not found page |
| `error/500.html` | Server error page |

### 7.2 JavaScript Modules
| File | Responsibility |
|------|---------------|
| `api.js` | Fetch wrapper — adds `Authorization: Bearer <token>`, handles 401 auto-refresh, throws structured errors |
| `auth.js` | `login()`, `logout()`, `requireAuth()` guard, token storage in `sessionStorage` |
| `app.js` | Application shell — sidebar toggle, notification badge polling, flash messages |
| `i18n.js` | EN ↔ AR switcher — swaps Bootstrap RTL CSS, updates `dir` attribute, replaces `data-i18n` attributes |
| `translations.js` | Bilingual string bundle — all UI strings in English + Arabic objects |
| `charts.js` | Chart.js renderers — heatmap (custom), bar charts (risk by category), doughnut (status) |

### 7.3 CSS Architecture
```
assets/css/
├── style.css   ← Design tokens (CSS variables), Bootstrap overrides, all components
│               Layout: sidebar 260px, top nav 64px, main content area
│               Components: KPI cards, risk badges, heatmap grid, skeleton shimmer, tables
│
└── rtl.css     ← RTL overrides for Arabic
                Sidebar flips to right, border-start/end swap, icon margin flip
                text-align: right, direction: rtl
```

### 7.4 Risk Badges (CSS classes)
```css
.badge-critical  { background: #dc2626 }   /* Score 20-25 */
.badge-high      { background: #f97316 }   /* Score 12-19 */
.badge-medium    { background: #eab308 }   /* Score 6-11 */
.badge-low       { background: #22c55e }   /* Score 1-5  */
```

### 7.5 API Base URL
> ⚠️ **Important**: `api.js` must use absolute URL when frontend is served from different port:
```javascript
const API_BASE = 'http://localhost:8084/api/v1';  // NOT relative '/api/v1'
```

---

## 8. Sprint Plans

### Sprint 1 — Foundation & Core Platform ✅ IMPLEMENTED
**Duration**: 6 weeks

**Deliverables Completed**:
- [x] JWT authentication (login/refresh/logout)
- [x] Enterprise Risk Management CRUD
- [x] Dashboard aggregations (stats, heatmap, category chart, recent risks)
- [x] User & Role management
- [x] In-app notifications
- [x] Global exception handler (RFC 7807 ProblemDetail)
- [x] Caffeine caching (5-min TTL)
- [x] OpenAPI/Swagger docs
- [x] `BaseEntity` with audit columns + soft delete
- [x] Frontend: login, dashboard, risk register, risk form, user admin
- [x] CSS design system + RTL support
- [x] Bilingual JS translations bundle

**Database Setup (manual, no Flyway)**:
- Schema created manually in SQL Server Management Studio
- Seed data inserted via `sqlcmd`

---

### Sprint 2 — Domain Modules Expansion ❌ NOT STARTED
**Duration**: 6 weeks

**Planned Deliverables**:
- [ ] Cyber Security module (assets, vulnerabilities, incidents)
- [ ] BCM module (BIA, BCP, exercises, recovery)
- [ ] Privacy module (ROPA, DPIA, breaches, DSR)
- [ ] Compliance module (frameworks, findings, evidence, remediation)
- [ ] Data Governance module
- [ ] Cross-module risk linkage (incident → risk, breach → risk)
- [ ] Excel/PDF export with bilingual support (Arabic RTL in PDF)
- [ ] Frontend pages for all 5 modules
- [ ] Reporting foundation

**Key Technical Work**:
- Module table migrations
- Spring ApplicationEvent for cross-module escalation
- Apache POI for Excel, OpenPDF for PDF with Arabic RTL
- `export.js` frontend module
- `risk-linkage.js` cross-module component

---

### Sprint 3 — Hardening & Production Readiness ❌ NOT STARTED
**Duration**: 6 weeks

**Planned Deliverables**:
- [ ] MFA (TOTP with QR code enrollment)
- [ ] Temporal tables for full change history
- [ ] Licensing & subscription module
- [ ] Advanced analytics (trends, drill-down, comparisons)
- [ ] Branding & system settings per tenant
- [ ] Executive PDF report generation (bilingual)
- [ ] Audit export with SHA-256 tamper-evident hashing
- [ ] Security hardening (rate limiting, CSP headers, OWASP ZAP)
- [ ] Account lockout (5 attempts → 30 min lockout)
- [ ] Full Arabic translation for all modules
- [ ] Operations documentation (runbook, deployment guide)

**Acceptance Criteria**:
- 50 concurrent users, P95 dashboard < 3 seconds
- OWASP ZAP: zero HIGH vulnerabilities
- MFA: QR enrollment + TOTP login
- All modules accessible in Arabic with RTL layout

---

## 9. Environment & Configuration

### 9.1 Development Environment
| Setting | Value |
|---------|-------|
| Java Home | `C:\Program Files\Java\jdk-17` |
| Maven | `C:\Program Files\Apache NetBeans\java\maven\bin` |
| Backend Port | `8084` |
| DB Host | `localhost:1433` |
| DB Name | `TSS_GRC` |
| DB User | `sa` |
| DB Password | `tss.123` |
| JWT Secret | `tss-dev-secret-key-at-least-32-chars-padding-ok` |
| CORS Allowed | `localhost:3000`, `localhost:5500`, `127.0.0.1:*` |
| Flyway | Disabled |
| DDL Auto | `none` |
| Show SQL | `true` |
| Swagger | Enabled at `/swagger-ui.html` |

### 9.2 Production Profile (`application-prod.yml`)
| Setting | Value |
|---------|-------|
| Port | `8443` (HTTPS) |
| DB Credentials | Via env vars (`${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`) |
| JWT Secret | Via env var (`${JWT_SECRET}`) |
| Flyway | Enabled |
| DDL Auto | `validate` |
| Swagger | Disabled |
| Logging | WARN to `/var/log/tss-grc/application.log` |
| Actuator | `8090` (separate port, not publicly exposed) |
| Max Login Attempts | 5 |
| Lockout Duration | 30 minutes |

---

## 10. Bugs Fixed & Key Decisions

### 10.1 Critical Bugs Fixed

#### Bug 1: Java 25 + Lombok Incompatibility
- **Symptom**: `com.sun.tools.javac.code.TypeTag :: UNKNOWN` compile error
- **Root Cause**: Lombok 1.18.30 uses `sun.misc.Unsafe` APIs removed in Java 25
- **Fix**: User installed Java 17; `pom.xml` reverted to `java.version=17`, `lombok.version=1.18.30`

#### Bug 2: Port 8080 Conflict
- **Symptom**: `Address already in use: bind`
- **Fix**: `powershell Stop-Process -Name java -Force`; port changed to 8084 per user request

#### Bug 3: Missing `NotificationResponse.java`
- **Symptom**: Compilation error — class not found
- **Fix**: File created manually at `module/platform/dto/NotificationResponse.java`

#### Bug 4: Missing `deleted_by` Column
- **Symptom**: `Invalid column name 'deleted_by'` SQL error
- **Root Cause**: Tables created manually, missing `BaseEntity` audit columns
- **Fix**: `add_deleted_by.sql` — `ALTER TABLE ... ADD deleted_by NVARCHAR(100) NULL`

#### Bug 5: Missing `created_by`, `updated_at`, `updated_by`, `deleted_at` on roles table
- **Symptom**: `Invalid column name 'created_by'` on `iam.roles`
- **Fix**: `fix_base_entity_columns.sql` with `IF NOT EXISTS` checks for all tables

#### Bug 6: BCrypt Hash Shell Escaping
- **Symptom**: Password stored as `\a\2\2IX...` instead of `$2a$12$...`
- **Root Cause**: Bash expanded `$` in BCrypt hashes when passed as `sqlcmd` argument
- **Fix**: Write SQL to a `.sql` file first, then run `sqlcmd -i C:\path\to\file.sql`

#### Bug 7: Wrong BCrypt Hash (wrong password)
- **Symptom**: Login failed for admin — hash was for "password", not "Admin@12345"
- **Fix**: Generated correct hashes via Maven exec:java:
  ```
  Admin@12345  → $2a$10$DQZCHvnbmQclQpO50tajUOccWDZJIW6kE4ftpD.wSYhknd/udVTr6
  Risk@12345   → $2a$10$VkuWC6XY6GZrUhsQoZOSNeXVL/OhRqlTwgQ38WqXnYfmv5O35s8W.
  Audit@12345  → $2a$10$18IjNU7Y1p7Yu7yFzCzGZ.GaymgAvJRnu9nNfb9.vY4fZ.RVu4F7m
  ```

#### Bug 8: 403 on All Authenticated Endpoints (Most Critical)
- **Symptom**: Every request after login returned 403, log: `"Cannot set user authentication from JWT: User not found: 1"`
- **Root Cause**: `JwtAuthenticationFilter` called `loadUserByUsername("1")` (userId as string) which found no user with `username = "1"`
- **Fix**: Inject `CustomUserDetailsService` directly, call `loadUserById(userId)` with the Long
  ```java
  // WRONG:
  UserDetailsService userDetailsService;
  userDetailsService.loadUserByUsername(String.valueOf(userId));

  // CORRECT:
  CustomUserDetailsService userDetailsService;
  userDetailsService.loadUserById(userId);
  ```

#### Bug 9: Deprecated Hibernate Dialect Warning
- **Symptom**: Two WARN lines at startup about `SQLServer2012Dialect`
- **Fix**: Removed `hibernate.dialect` from `application.yml` — Hibernate 6 auto-detects SQL Server

### 10.2 Key Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| Modular Monolith (not microservices) | Simpler for <100 user initial deployments; can extract modules later |
| Java 17 (not 25) | Lombok 1.18.30 incompatible with Java 25 |
| Flyway disabled in dev | Manual DB setup for faster iteration; enabled in prod |
| `ddl-auto: none` | Schema managed by Flyway; no accidental table drops |
| Standalone frontend (no Thymeleaf) | Better separation; can CDN-deploy statically |
| sessionStorage for JWT | Survives page refresh; isolated per browser tab |
| Caffeine L1 only (no Redis) | Sufficient for single-instance; Redis added in Sprint 3 scaling |
| Schema-per-module DB isolation | Clear boundaries; can extract to separate DB if needed |
| BCrypt(12) for passwords | Industry standard; balances security and login latency |
| `rowVersion` (SQL Server ROWVERSION) | Optimistic locking to prevent concurrent update conflicts |

---

## 11. Current System State

### 11.1 Confirmed Working (tested via curl/Postman)
```
POST /api/auth/login                    → 200 + { accessToken, refreshToken }  ✅
GET  /api/v1/dashboard/stats            → { totalRisks:0, totalUsers:1, ... }  ✅
POST /api/v1/risk/risks                 → 201 + created risk object             ✅
GET  /api/v1/notifications/unread-count → { count: 0 }                         ✅
GET  /api/v1/risk/categories            → []  (empty, no seed data yet)         ✅
```

### 11.2 Database State
- `TSS_GRC` database created ✅
- All 11 schemas created ✅
- Platform tables created (organizations, users, roles, permissions, etc.) ✅
- Risk tables created ✅
- Admin user seeded ✅
- Risk categories: **EMPTY** (no seed data inserted yet) ⚠️
- BCM, Cyber, Privacy, Compliance, DataGov tables: not yet created ⚠️

### 11.3 Known Warnings (non-blocking)
- Mail health indicator shows DOWN in actuator (SMTP not configured in dev)
- LDAP health indicator shows DOWN (AD not connected in dev)
- These can be disabled in `application-dev.yml`:
  ```yaml
  management:
    health:
      mail:
        enabled: false
      ldap:
        enabled: false
  ```

---

## 12. Pending Items

### High Priority
| Item | Details |
|------|---------|
| Verify `api.js` BASE_URL | Must be `http://localhost:8084/api/v1` (absolute) for cross-port serving |
| Seed risk categories | Insert rows into `risk.risk_categories` for the form dropdown to work |
| Test frontend end-to-end | Run `npx live-server --port=3000` from `tss-grc-ui/`, log in, navigate |

### Medium Priority
| Item | Details |
|------|---------|
| Disable Mail/LDAP health checks | Add to `application-dev.yml` to clean up actuator output |
| Add `riskmanager` & `auditor` seed users | Verify they can log in and see correct permissions |
| Seed sample risk data | For dashboard heatmap and charts to show meaningful data |
| Test RTL layout | Switch to Arabic, verify sidebar flips, text aligns right |

### Sprint 2 (next implementation phase)
| Item | Details |
|------|---------|
| Cyber module | Assets, vulnerabilities, incidents |
| BCM module | BIA, BCP, exercises |
| Privacy module | ROPA, DPIA, breaches, DSR |
| Compliance module | Assessments, findings, evidence |
| Data Governance module | Assets, classifications, lineage |
| Cross-module risk linkage | Incident → Risk, Breach → Risk (Spring Events) |
| Excel/PDF export | Apache POI + OpenPDF with Arabic RTL |

---

## 13. How to Start the System

### 13.1 Start the Backend
```bash
# From: C:\AYMAN\Github\Claude-GRC\tss-grc-backend\
JAVA_HOME="/c/Program Files/Java/jdk-17" \
PATH="/c/Program Files/Apache NetBeans/java/maven/bin:/c/Program Files/Java/jdk-17/bin:$PATH" \
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Expected startup output**:
```
Started GrcApplication in X.XXX seconds (JVM running for X.XXX)
```
**Verify**: `curl http://localhost:8084/actuator/health` → `{"status":"UP"}`

### 13.2 Start the Frontend (development)
```bash
# From: C:\AYMAN\Github\Claude-GRC\tss-grc-ui\
npx live-server --port=3000
```

### 13.3 Test Login
```bash
curl -X POST http://localhost:8084/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@12345"}'
```
Returns: `{ "accessToken": "eyJ...", "refreshToken": "eyJ...", "expiresIn": 900 }`

### 13.4 Test Authenticated Request
```bash
TOKEN="<paste accessToken here>"
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8084/api/v1/dashboard/stats
```

### 13.5 Access Swagger UI
Open browser: `http://localhost:8084/swagger-ui.html`
- Click **Authorize**, paste Bearer token
- Test all endpoints interactively

### 13.6 Generate BCrypt Hash (if needed)
```bash
# From tss-grc-backend/
JAVA_HOME="/c/Program Files/Java/jdk-17" \
PATH="/c/Program Files/Apache NetBeans/java/maven/bin:/c/Program Files/Java/jdk-17/bin:$PATH" \
mvn exec:java -Dexec.mainClass="com.tss.grc.HashPrinter" \
  -Dexec.args="MyPassword123"
```

### 13.7 Seed Risk Categories (SQL)
```sql
-- Write to file and run: sqlcmd -S localhost -U sa -P tss.123 -d TSS_GRC -i seed_categories.sql
INSERT INTO risk.risk_categories (name_en, name_ar, description_en, is_active, created_at, is_deleted)
VALUES
  (N'Strategic',    N'استراتيجي',   N'Strategic risks',    1, GETDATE(), 0),
  (N'Operational',  N'تشغيلي',      N'Operational risks',  1, GETDATE(), 0),
  (N'Financial',    N'مالي',        N'Financial risks',    1, GETDATE(), 0),
  (N'IT & Cyber',   N'تقنية المعلومات', N'IT and cybersecurity risks', 1, GETDATE(), 0),
  (N'Legal',        N'قانوني',      N'Legal and regulatory risks', 1, GETDATE(), 0),
  (N'Compliance',   N'امتثال',      N'Compliance risks',   1, GETDATE(), 0);
```

---

## Appendix A: File Count Summary

| Location | Files |
|----------|-------|
| `docs/analysis/` | 16 Markdown files |
| `docs/architecture/` | 20 Markdown files |
| `docs/database/` | 15 Markdown files |
| `docs/diagrams/` | 18 Markdown files |
| `docs/domain/` | 14 Markdown files |
| `docs/roadmap/` | 5 Markdown files |
| `sprints/` | 3 Markdown files |
| `tss-grc-backend/src/main/java/` | ~41 Java files |
| `tss-grc-backend/db/sqlserver/` | ~32 SQL files |
| `tss-grc-ui/` HTML pages | 10 HTML files |
| `tss-grc-ui/assets/js/` | 6 JavaScript files |
| `tss-grc-ui/assets/css/` | 2 CSS files |

---

## Appendix B: Important File Paths

```
Project Root:
  C:\AYMAN\Github\Claude-GRC\

Backend Source:
  C:\AYMAN\Github\Claude-GRC\tss-grc-backend\src\main\java\com\tss\grc\

Backend Resources:
  C:\AYMAN\Github\Claude-GRC\tss-grc-backend\src\main\resources\

Database DDL Scripts:
  C:\AYMAN\Github\Claude-GRC\tss-grc-backend\db\sqlserver\

Frontend:
  C:\AYMAN\Github\Claude-GRC\tss-grc-ui\

Documentation:
  C:\AYMAN\Github\Claude-GRC\docs\

Sprint Plans:
  C:\AYMAN\Github\Claude-GRC\sprints\

Maven (NetBeans):
  C:\Program Files\Apache NetBeans\java\maven\bin\mvn

Java 17:
  C:\Program Files\Java\jdk-17\
```

---

*End of Session Summary — Ready for handoff to next session*
