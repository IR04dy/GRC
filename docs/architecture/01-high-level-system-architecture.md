# 01 - High-Level System Architecture
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document defines the overall system architecture for the TSS (Trusted Security Solutions) GRC platform. It establishes the technology stack, deployment topology, key architectural decisions, and the rationale behind choosing a modular monolith as the foundational architecture style. This is the root reference for all subsequent architecture documents.

---

## 1. Platform Vision

TSS is a comprehensive Governance, Risk, and Compliance (GRC) platform designed for regulated enterprises in Saudi Arabia and the broader MENA region. It provides integrated capabilities across Enterprise Risk Management, Cybersecurity Compliance, Business Continuity, Data Governance, Privacy, Audit, and Reporting within a single deployable unit.

### Key Drivers

- **Regulatory alignment**: NCA ECC, SAMA CSF, NDMO, PDPL, ISO 27001, ISO 22301, ISO 31000
- **Bilingual requirement**: Full Arabic (RTL) and English (LTR) support throughout the platform
- **Deployment flexibility**: On-premises for government entities, SaaS for commercial customers
- **Operational simplicity**: Small-to-medium DevOps teams must be able to deploy and operate the platform
- **Time to market**: Rapid delivery of a production-grade platform without premature distributed-system complexity

---

## 2. Architecture Style: Modular Monolith

### Decision

TSS adopts a **modular monolith** architecture for Phase 1. All ten functional modules are deployed as a single Spring Boot application with strict internal module boundaries enforced through package structure, access rules, and well-defined inter-module APIs.

### Why Modular Monolith Over Microservices

| Concern | Modular Monolith | Microservices |
|---|---|---|
| **Deployment complexity** | Single deployable artifact (JAR/WAR) | Requires container orchestration (K8s), service mesh, distributed tracing |
| **Team size** | Suitable for 5-15 developers | Requires dedicated platform/SRE team |
| **Data consistency** | Local transactions across modules | Saga patterns, eventual consistency, compensating transactions |
| **Development velocity** | Fast iteration, single IDE, simple debugging | Cross-service debugging, contract testing overhead |
| **Operational cost** | Single JVM, single database, straightforward monitoring | Multiple services, databases, message brokers, API gateways |
| **Refactoring ease** | IDE-supported refactoring across modules | Requires coordinated deployments across services |

### Why Not a Traditional Monolith

A modular monolith is not a "big ball of mud." It enforces:

1. **Package-level boundaries**: Each module owns its package namespace (`com.tss.module.erm`, `com.tss.module.bcm`, etc.)
2. **API-only access**: Modules communicate through defined service interfaces, never through direct repository or entity access across boundaries
3. **Independent domain models**: Each module defines its own domain entities, aggregates, and value objects
4. **Shared kernel minimization**: Only truly cross-cutting concerns (audit, notification, workflow) live in shared packages
5. **Database schema separation**: Each module owns its own database schema, accessed only through its own repositories

### Evolution Path to Microservices

The modular monolith is designed for a controlled migration to microservices when business scale demands it:

1. **Phase 1 (Current)**: Modular monolith, single deployment, schema-per-module
2. **Phase 2 (Scale trigger)**: Extract high-traffic modules (Reporting, Workflow) as independent services behind an API gateway
3. **Phase 3 (Full decomposition)**: Each module becomes an independent service with its own database, communicating via events and REST APIs

**Extraction readiness** is built in from day one:
- All inter-module communication goes through service interfaces (not direct method calls to internals)
- Domain events are published to an in-process event bus that can be swapped for a message broker (RabbitMQ, Kafka)
- Each module's schema is isolated, so database extraction is straightforward
- DTOs at module boundaries mean no shared entity leakage

---

## 3. Technology Stack

### Core Platform

| Layer | Technology | Version | Rationale |
|---|---|---|---|
| **Runtime** | Java | 21 (LTS) | Long-term support, virtual threads, modern language features |
| **Framework** | Spring Boot | 3.5.x | Mature ecosystem, extensive security features, production-ready actuator |
| **Web Layer** | Spring MVC (REST API) | 6.x | JSON REST API only; no Thymeleaf; all responses are `application/json` |
| **Security** | Spring Security | 6.x | Comprehensive authentication/authorization, SAML/OIDC/LDAP support |
| **Database** | SQL Server | 2019+ | Customer mandate, strong enterprise features, Arabic collation support |
| **ORM** | Hibernate / JPA | 6.x | Mature ORM, schema-per-module support, SQL Server dialect |
| **Migration** | Flyway | 10.x | Version-controlled schema migrations, per-module migration paths |
| **Build** | Maven | 3.9.x | Multi-module project support, dependency management |
| **Caching** | Spring Cache + Caffeine | Local L1 cache; Redis-ready for L2 |
| **Search** | SQL Server Full-Text Search | Built-in, no additional infrastructure |
| **Scheduling** | Spring Scheduler + Quartz | SLA tracking, report scheduling, notification dispatch |
| **Templating** | Thymeleaf | Server-side HTML rendering with layout dialect |

### Frontend Stack (Standalone Static UI — `tss-grc-ui/`)

| Component | Technology | Rationale |
|---|---|---|
| **HTML** | Plain HTML5 | No template engine; pages are self-contained static files served independently |
| **CSS Framework** | Bootstrap 5 (RTL) | Built-in RTL support, responsive grid, enterprise component library |
| **JavaScript** | Vanilla JS (ES2020) | No build step, no framework dependency; `api.js`, `auth.js`, `i18n.js` as thin helpers |
| **Charts** | Chart.js 4.x | Lightweight, RTL-compatible charting |
| **Icons** | Bootstrap Icons | Consistent iconography, RTL-aware |
| **Authentication** | JWT Bearer tokens | `sessionStorage` for access token; HttpOnly cookie for refresh token |
| **Translations** | JS translation bundle | `translations.js` static file; `i18n.js` applies `data-i18n` attributes at runtime |
| **Dev server** | `npx live-server` | Zero-install static file server with auto-reload for development |

### Why Decoupled Frontend

1. **Independent deployment**: UI and API versioned, deployed, and scaled independently
2. **API reusability**: Same REST API serves the web UI, future mobile apps, and third-party integrations
3. **No build overhead**: No Node.js, no bundler, no transpilation — open HTML files in any browser or editor
4. **CDN-ready**: All static assets served from edge nodes with long cache TTLs in production
5. **Team specialization**: Frontend (HTML/CSS/JS) and backend (Java/SQL) teams work in separate repositories without blocking each other
6. **No CSRF complexity**: JWT Bearer tokens replace server-side form CSRF tokens

See [20-frontend-architecture.md](20-frontend-architecture.md) for the complete frontend project specification.

### Infrastructure

| Component | Technology | Purpose |
|---|---|---|
| **Application Server** | Embedded Tomcat (Spring Boot) | Self-contained deployment |
| **Reverse Proxy** | Nginx / IIS ARR | TLS termination, static file serving, load balancing |
| **File Storage** | Local filesystem / S3-compatible | Evidence attachments, report exports, uploaded documents |
| **Email** | SMTP (configurable) | Notification delivery |
| **SMS** | Pluggable gateway (Unifonic, Twilio) | SMS notification delivery for Saudi market |
| **Monitoring** | Spring Actuator + Micrometer | Health checks, metrics export |
| **Logging** | SLF4J + Logback | Structured JSON logging, ELK-ready |

---

## 4. Deployment Topology

### On-Premises Deployment (Government / Regulated)

```
[Nginx / IIS ARR — Reverse Proxy & Static File Server]
        |
        ├── /            → serve tss-grc-ui/ static files (HTML/CSS/JS)
        └── /api/        → proxy_pass → Spring Boot REST API (port 8080)
                                |
                        [TSS Application Server(s)]  -- Active/Passive or Active/Active
                                |
                        [SQL Server 2019]            -- Always On Availability Group
                                |
                        [File Storage (NAS/SAN)]     -- Evidence and document storage
                                |
                        [SMTP Server]                -- Email notifications
                        [LDAP/AD Server]             -- Identity provider
```

**Characteristics:**
- Deployed on customer-managed infrastructure (VMs or bare metal)
- Single WAR/JAR deployed to one or more application servers
- SQL Server with Always On for high availability
- Integration with customer's Active Directory for authentication
- Network-isolated, no external internet dependency post-deployment

### SaaS Deployment (Commercial)

```
[CDN / WAF]
    |
[API Gateway / Load Balancer]
    |
[TSS Application Cluster]      -- Multiple instances behind LB
    |
[SQL Server (Managed)]         -- Azure SQL / AWS RDS
    |
[Object Storage (S3/Blob)]     -- Tenant-isolated file storage
    |
[Redis Cache]                  -- Shared session store, L2 cache
[SMTP Service]                 -- SES / SendGrid
[SMS Gateway]                  -- Unifonic / Twilio
```

**Characteristics:**
- Multi-tenant with tenant-discriminator column isolation
- Stateless application tier with external session store (Redis)
- Managed database with automated backups
- Object storage for evidence and documents
- WAF for DDoS protection and OWASP rule enforcement

---

## 5. Module Overview

TSS consists of ten functional modules, each with a well-defined domain boundary:

| # | Module | Package | Schema | Description |
|---|---|---|---|---|
| 1 | Enterprise Risk Management | `com.tss.module.erm` | `erm` | Risk registers, assessments, treatment plans, risk appetite |
| 2 | Cybersecurity Risk & Compliance | `com.tss.module.cyber` | `cyber` | NCA ECC, SAMA CSF, ISO 27001 compliance tracking |
| 3 | Business Continuity Management | `com.tss.module.bcm` | `bcm` | BIA, BCP, DR plans, testing schedules |
| 4 | Data Governance | `com.tss.module.datagov` | `datagov` | Data classification, data catalog, data quality, stewardship |
| 5 | Privacy & Personal Data Protection | `com.tss.module.privacy` | `privacy` | PDPL compliance, DPIA, consent management, data subject requests |
| 6 | Compliance & Audit | `com.tss.module.audit` | `audit` | Audit planning, findings, remediation, compliance obligations |
| 7 | Workflow & Case Management | `com.tss.module.workflow` | `workflow` | Approval workflows, case tracking, SLA management |
| 8 | Reporting & Analytics | `com.tss.module.reporting` | `reporting` | Dashboards, reports, heatmaps, export, scheduled reports |
| 9 | Licensing & Subscription | `com.tss.module.licensing` | `licensing` | License management, feature entitlements, usage metering |
| 10 | Administration & Configuration | `com.tss.module.admin` | `admin` | Users, roles, tenants, system configuration, branding |

---

## 6. Key Architectural Decisions

### ADR-001: Modular Monolith as Initial Architecture

- **Status**: Accepted
- **Context**: Team size is 5-12 developers; operational simplicity is critical; Saudi government clients require on-premises deployment
- **Decision**: Deploy as a single Spring Boot application with strict module boundaries
- **Consequences**: Faster development, simpler deployment, but requires discipline to maintain module boundaries
- **Reference**: Section 2 above

### ADR-002: SQL Server as Primary Database

- **Status**: Accepted
- **Context**: Customer organizations standardize on SQL Server; Arabic collation (`Arabic_CI_AS`) is well-supported
- **Decision**: Use SQL Server 2019+ as the sole RDBMS
- **Consequences**: No polyglot persistence complexity; some advanced features (JSON columns, temporal tables) available; limits NoSQL use cases
- **Trade-off**: Reduced flexibility vs. operational simplicity and customer alignment

### ADR-003: Decoupled Frontend — Standalone HTML/CSS/JS UI

- **Status**: Accepted *(supersedes earlier SSR/Thymeleaf decision)*
- **Context**: The UI layer must be independently deployable and maintainable. The backend REST API should be reusable by future mobile clients and third-party integrations without being tightly coupled to a server-side template engine.
- **Decision**: The frontend is a **standalone static project** (`tss-grc-ui/`) consisting of plain HTML5, CSS3, and vanilla JavaScript. It communicates with the Spring Boot backend exclusively via the REST API using JWT Bearer token authentication. Thymeleaf is removed from the backend entirely.
- **Consequences**:
  - UI and API can be deployed, versioned, and scaled independently
  - No CSRF token management required (JWT replaces form-based sessions)
  - i18n/L10n handled client-side via a JS translation bundle and `data-i18n` attributes
  - RTL switching done at runtime by `i18n.js` (swaps Bootstrap RTL CSS, applies `rtl-layout` class)
  - No build step needed; any static file server can host the frontend
  - Backend Thymeleaf, Thymeleaf Layout Dialect, and Thymeleaf Security Extras dependencies are removed from `pom.xml`
- **Reference**: [20-frontend-architecture.md](20-frontend-architecture.md)

### ADR-004: Schema-Per-Module Database Strategy

- **Status**: Accepted
- **Context**: Module independence requires data ownership; future microservice extraction requires schema portability
- **Decision**: Each module owns a dedicated SQL Server schema; cross-module data access goes through service APIs, not direct SQL joins
- **Consequences**: Clean data ownership, easier future extraction; some queries require application-level joins
- **Reference**: See [05-domain-driven-decomposition.md](05-domain-driven-decomposition.md)

### ADR-005: Arabic/English Bilingual as First-Class Requirement

- **Status**: Accepted
- **Context**: Saudi Arabian market requires full Arabic UI with RTL layout; English must also be fully supported
- **Decision**: All UI text externalized to message bundles; all database text fields that are user-facing support bilingual storage; Bootstrap RTL theme applied conditionally
- **Consequences**: Additional development effort for i18n; all UI components must be RTL-tested
- **Reference**: See [06-shared-services-architecture.md](06-shared-services-architecture.md)

### ADR-006: Multi-Tenancy via Discriminator Column

- **Status**: Accepted
- **Context**: SaaS deployment requires multi-tenant data isolation without database-per-tenant overhead for early scale
- **Decision**: `tenant_id` column on all tenant-scoped tables with Hibernate filters for automatic tenant isolation
- **Consequences**: Simpler infrastructure, but requires rigorous tenant filter enforcement; evolution path to schema-per-tenant or database-per-tenant exists
- **Reference**: See [14-tenant-organization-model.md](14-tenant-organization-model.md)

---

## 7. Cross-Cutting Concerns

These concerns span all modules and are addressed in dedicated architecture documents:

| Concern | Document Reference |
|---|---|
| Authentication & Authorization | [11-iam-architecture.md](11-iam-architecture.md), [13-security-architecture.md](13-security-architecture.md) |
| Audit Trail | [10-audit-evidence-architecture.md](10-audit-evidence-architecture.md) |
| Workflow & Approvals | [07-workflow-engine-architecture.md](07-workflow-engine-architecture.md) |
| Notifications | [09-notification-architecture.md](09-notification-architecture.md) |
| Reporting | [08-reporting-architecture.md](08-reporting-architecture.md) |
| Localization (i18n/L10n) | [06-shared-services-architecture.md](06-shared-services-architecture.md) |
| Multi-Tenancy | [14-tenant-organization-model.md](14-tenant-organization-model.md) |
| Security | [13-security-architecture.md](13-security-architecture.md) |
| Observability | [18-observability-logging.md](18-observability-logging.md) |
| Disaster Recovery | [19-backup-disaster-recovery.md](19-backup-disaster-recovery.md) |

---

## 8. Constraints and Assumptions

### Constraints

1. SQL Server 2019+ is mandated by target customer environments
2. On-premises deployment must work without internet connectivity
3. Arabic language and RTL layout are non-negotiable requirements
4. Saudi regulatory frameworks (NCA, SAMA, PDPL) define compliance requirements
5. Java 21 LTS is the runtime target for long-term support

### Assumptions

1. Development team size will be 5-15 engineers for Phase 1
2. Initial deployment targets fewer than 50 concurrent users per tenant
3. Evidence and document storage will not exceed 500 GB per tenant in Year 1
4. Email SMTP infrastructure is provided by the customer or SaaS provider
5. LDAP/AD infrastructure exists in on-premises customer environments

---

## 9. Document Roadmap

| Document | Scope |
|---|---|
| [02-logical-architecture.md](02-logical-architecture.md) | Logical layers, component organization |
| [03-module-interaction-architecture.md](03-module-interaction-architecture.md) | Inter-module communication |
| [04-application-layer-architecture.md](04-application-layer-architecture.md) | Controller/Service/Repository patterns |
| [05-domain-driven-decomposition.md](05-domain-driven-decomposition.md) | DDD, bounded contexts, aggregates |
| [06-shared-services-architecture.md](06-shared-services-architecture.md) | Common services |
| [07-workflow-engine-architecture.md](07-workflow-engine-architecture.md) | Workflow engine design |
| [08-reporting-architecture.md](08-reporting-architecture.md) | Reporting and dashboards |
| [09-notification-architecture.md](09-notification-architecture.md) | Notification system |
| [10-audit-evidence-architecture.md](10-audit-evidence-architecture.md) | Audit trail and evidence |
| [11-iam-architecture.md](11-iam-architecture.md) | Identity and access management |
| [12-api-integration-architecture.md](12-api-integration-architecture.md) | API design and integrations |
| [13-security-architecture.md](13-security-architecture.md) | Security architecture |
| [14-tenant-organization-model.md](14-tenant-organization-model.md) | Multi-tenancy model |
| [15-configuration-branding-model.md](15-configuration-branding-model.md) | Configuration and branding |
| [16-scalability-performance.md](16-scalability-performance.md) | Scalability and performance |
| [17-availability-resilience.md](17-availability-resilience.md) | Availability and resilience |
| [18-observability-logging.md](18-observability-logging.md) | Observability and logging |
| [19-backup-disaster-recovery.md](19-backup-disaster-recovery.md) | Backup and disaster recovery |
