# TSS GRC Platform

**Trusted Security Solutions — Enterprise Governance, Risk & Compliance Platform**

A comprehensive, bilingual (Arabic/English) GRC platform for regulated enterprises in Saudi Arabia and the MENA region. Built for compliance with NCA ECC 2.0, ISO 27001:2022, Saudi PDPL, ISO 22301, and ISO 31000.

---

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│           tss-grc-ui/                        │  Standalone frontend
│   Plain HTML5 + CSS3 + Vanilla JS           │  No build step required
│   Bootstrap 5 RTL · Chart.js 4 · JWT auth  │  Served by Nginx / live-server
└──────────────────┬──────────────────────────┘
                   │ REST API (JSON)
                   │ Authorization: Bearer <JWT>
┌──────────────────▼──────────────────────────┐
│           Spring Boot 3.5 REST API           │  Pure @RestController
│   10 modules · Modular monolith              │  No Thymeleaf
│   JWT stateless · Spring Security 6         │
└──────────────────┬──────────────────────────┘
                   │ JDBC / Flyway
┌──────────────────▼──────────────────────────┐
│     SQL Server 2019 Express Edition          │  11 schemas
│     11 schemas · Temporal tables            │  Flyway migrations
│     BIGINT PKs · ROWVERSION concurrency     │
└─────────────────────────────────────────────┘
```

**10 Modules:** Enterprise Risk Management · Cybersecurity · Business Continuity · Data Governance · Privacy & PDPL · Compliance & Audit · Workflow & Case Management · Reporting & Analytics · Licensing & Subscription · Administration

---

## Repository Structure

```
/
├── tss-grc-ui/                  ← Frontend (standalone HTML/CSS/JS)
│   ├── index.html               ← Entry point (redirects to login/dashboard)
│   ├── login.html
│   ├── dashboard.html
│   ├── risk/  cyber/  bcm/  privacy/  compliance/  reports/  admin/
│   └── assets/
│       ├── css/style.css  rtl.css
│       └── js/auth.js  api.js  i18n.js  app.js  translations.js
│
├── src/                         ← Spring Boot REST API backend
│   └── main/
│       ├── java/com/tss/grc/
│       │   ├── GrcApplication.java
│       │   ├── config/          ← SecurityConfig, CorsConfig, DataSourceConfig, ...
│       │   ├── security/        ← JwtTokenProvider, JwtAuthenticationFilter, ...
│       │   ├── common/          ← BaseEntity, TenantContext, GlobalExceptionHandler
│       │   └── module/          ← risk/ cyber/ bcm/ privacy/ compliance/ ...
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── application-prod.yml
│           └── db/migration/sqlserver/   ← Flyway migrations
│
├── db/sqlserver/                ← DDL scripts (reference / manual setup)
│   ├── schema/                  ← 00-create-database.sql, 01-create-schemas.sql
│   ├── tables/shared/           ← 19 shared platform tables
│   ├── tables/modules/          ← Module-specific tables (risk, cyber, bcm, ...)
│   ├── indexes/                 ← Filtered + covering indexes
│   ├── views/                   ← Reporting views
│   └── migrations/              ← Flyway strategy README
│
├── docs/
│   ├── analysis/    (16 files)  ← Business analysis, requirements, personas
│   ├── architecture/(20 files)  ← System, security, API, frontend architecture
│   ├── domain/      (14 files)  ← Domain model per module
│   ├── database/    (15 files)  ← Physical DB design, Express constraints
│   ├── diagrams/    (18 files)  ← Mermaid ERDs, flow diagrams, deployment
│   └── roadmap/     ( 5 files)  ← Roadmap, delivery summary, ADL, issues, risks
│
├── sprints/
│   ├── sprint-1.md  ← Foundation + Core Platform (ERM + Admin + Auth)
│   ├── sprint-2.md  ← Domain modules (Cyber + BCM + Privacy + Compliance + DataGov)
│   └── sprint-3.md  ← Hardening + Advanced features + Production readiness
│
└── pom.xml                      ← Spring Boot 3.5.0, Java 17, JJWT, POI, OpenPDF
```

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend framework | Spring Boot 3.5.x, Java 17 |
| Security | Spring Security 6, JJWT 0.12.6 (JWT Bearer) |
| Database | SQL Server 2019 Express Edition |
| ORM | Hibernate 6 / Spring Data JPA |
| Migrations | Flyway 10.x |
| Caching | Caffeine (5-min TTL, L1) |
| Export | Apache POI 5.x (Excel), OpenPDF (PDF + Arabic RTL) |
| API docs | SpringDoc OpenAPI 3.0 / Swagger UI |
| Build | Maven 3.9.x |
| Frontend | Plain HTML5, Bootstrap 5 (RTL), Chart.js 4, Vanilla JS |
| Auth (frontend) | JWT in `sessionStorage`; refresh token in HttpOnly cookie |
| i18n | Client-side `translations.js`; `data-i18n` attributes; Bootstrap RTL swap |

---

## Quick Start (Development)

### Prerequisites

- Java 17+
- Maven 3.9+
- SQL Server 2019 Express (local or Docker)
- Node.js (optional — only needed for `npx live-server`)

### 1. Create the database

```sql
-- Run on your SQL Server instance:
CREATE DATABASE TSS_GRC COLLATE Latin1_General_100_CI_AS_SC_UTF8;
```

Or run the DDL script directly:

```bash
sqlcmd -S localhost -U sa -P yourpassword -i db/sqlserver/schema/00-create-database.sql
```

### 2. Configure environment variables

```bash
export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=TSS_GRC;encrypt=true;trustServerCertificate=true;sendStringParametersAsUnicode=true"
export DB_USERNAME=sa
export DB_PASSWORD=yourpassword
export JWT_SECRET="your-256-bit-secret-key-replace-this-in-production"
```

Or set them in `application-dev.yml` locally (do not commit credentials).

### 3. Start the backend API

```bash
mvn spring-boot:run -Dspring.profiles.active=dev
# API available at: http://localhost:8080
# Swagger UI at:    http://localhost:8080/swagger-ui.html
```

Flyway will automatically apply all migrations on first start.

### 4. Start the frontend

```bash
cd tss-grc-ui
npx live-server --port=3000
# UI available at: http://localhost:3000
```

CORS is pre-configured to allow `http://localhost:3000` in the dev profile.

### 5. Default credentials (seeded)

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@12345` | Platform Admin |
| `riskmanager` | `Risk@12345` | Risk Manager |
| `auditor` | `Audit@12345` | Internal Auditor |

---

## Production Deployment

See [`docs/architecture/20-frontend-architecture.md`](docs/architecture/20-frontend-architecture.md) for the Nginx reverse proxy configuration that serves both frontend and API from the same origin.

See [`src/main/resources/application-prod.yml`](src/main/resources/application-prod.yml) for all production configuration options.

---

## Regulatory Compliance Awareness

| Framework | Version | Coverage |
|---|---|---|
| NCA ECC | 2.0 | Cybersecurity controls, asset classification, incident management |
| ISO 27001 | 2022 | Information security management (93 Annex A controls) |
| ISO 27701 | 2019 | Privacy information management (PIMS extension) |
| ISO 22301 | 2019 | Business continuity management |
| ISO 31000 | 2018 | Risk management principles |
| Saudi PDPL | 2021 | Personal data protection (RoPA, DSR, breach notification) |
| SAMA CSF | 1.0 | Cyber Security Framework for financial sector |

---

## Documentation Index

| Section | Location |
|---|---|
| Analysis & Requirements | [`docs/analysis/`](docs/analysis/) |
| System Architecture | [`docs/architecture/`](docs/architecture/) |
| Frontend Architecture | [`docs/architecture/20-frontend-architecture.md`](docs/architecture/20-frontend-architecture.md) |
| Domain Models | [`docs/domain/`](docs/domain/) |
| Database Design | [`docs/database/`](docs/database/) |
| Diagrams (Mermaid) | [`docs/diagrams/`](docs/diagrams/) |
| Sprint Plans | [`sprints/`](sprints/) |
| Roadmap & ADL | [`docs/roadmap/`](docs/roadmap/) |
