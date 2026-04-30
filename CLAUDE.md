# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---
## Code Path
Source of truth: `C:\AYMAN\Github\Claude-GRC-2`
Origin: extracted from `claude/quirky-borg` branch (commit `726cd1e`) of `C:\AYMAN\Github\Claude-GRC` on 2026-04-29.

## Project Overview

**TSS GRC Platform** — a bilingual (Arabic/English), multi-tenant, enterprise Governance, Risk & Compliance platform targeting Saudi Arabia and the MENA region. Modular monolith backend + standalone frontend. No server-side rendering.

---

## Commands

### Backend (Spring Boot)

```bash
# Run in development (Flyway migrations run automatically on startup)
cd tss-grc-backend
mvn spring-boot:run -Dspring.profiles.active=dev

# API:        http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
# Health:     http://localhost:8080/actuator/health
```

```bash
# Build JAR
mvn clean package -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName
```

### Frontend (standalone HTML/CSS/JS — no build step)

```bash
cd tss-grc-ui
npx live-server --port=3000
# UI: http://localhost:3000
```

CORS is pre-configured for `http://localhost:3000` in the dev profile.

### Database setup (one-time)

```sql
CREATE DATABASE TSS_GRC COLLATE Latin1_General_100_CI_AS_SC_UTF8;
```

Required environment variables (or override in `application-dev.yml` locally — do not commit):

```bash
DB_URL="jdbc:sqlserver://localhost:1433;databaseName=TSS_GRC;encrypt=true;trustServerCertificate=true;sendStringParametersAsUnicode=true"
DB_USERNAME=sa
DB_PASSWORD=yourpassword
JWT_SECRET="your-256-bit-secret-key-replace-this-in-production"
```

### Default seeded credentials

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@12345` | Platform Admin |
| `riskmanager` | `Risk@12345` | Risk Manager |
| `auditor` | `Audit@12345` | Internal Auditor |

---

## Architecture

### Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5, Java 17, Spring Security 6 |
| Auth | JJWT 0.12.6 — stateless JWT (Bearer token) |
| Database | SQL Server 2019 Express, 11 schemas |
| ORM | Hibernate 6 / Spring Data JPA — `ddl-auto: validate` |
| Migrations | Flyway 10 — `db/migration/sqlserver/` |
| Caching | Caffeine L1 (5-min TTL, 1000 entries) |
| Export | Apache POI (Excel), OpenPDF (Arabic RTL PDF) |
| API docs | SpringDoc OpenAPI 3 |
| Frontend | Plain HTML5 + Bootstrap 5 RTL + Chart.js 4 + Vanilla JS |

### Repository layout

```
/
├── tss-grc-backend/          ← Spring Boot backend (source of truth)
│   ├── src/main/java/com/tss/grc/
│   │   ├── config/           ← SecurityConfig, CorsConfig, DataSourceConfig, cache
│   │   ├── security/         ← JwtTokenProvider, JwtAuthenticationFilter
│   │   ├── common/           ← BaseEntity, TenantContext, GlobalExceptionHandler
│   │   └── module/           ← 13 functional modules (see below)
│   └── src/main/resources/
│       ├── application.yml           ← Base config (all profiles)
│       ├── application-dev.yml       ← Dev overrides (Swagger enabled)
│       ├── application-prod.yml      ← Prod overrides (SSL, no Swagger)
│       └── db/migration/sqlserver/   ← Flyway versioned (V) and repeatable (R) migrations
│
├── tss-grc-ui/               ← Standalone frontend (no build step)
│   ├── assets/css/style.css  rtl.css
│   └── assets/js/auth.js  api.js  i18n.js  app.js  translations.js
│
├── docs/                     ← 80+ markdown files: analysis, architecture, domain, DB, diagrams, roadmap
├── sprints/                  ← sprint-1.md, sprint-2.md, sprint-3.md (delivery phases)
└── db/sqlserver/             ← Reference DDL scripts (schema, tables, indexes, views)
```

### Backend module structure

Each module under `module/` follows the same layered layout:

```
module/{name}/
├── controller/   ← @RestController — pure JSON, no Thymeleaf
├── dto/          ← Request/Response DTOs with Jakarta validation
├── entity/       ← JPA @Entity, @Embeddable
├── repository/   ← Spring Data JPA (soft-delete filtered via @Where)
└── service/      ← @Service @Transactional — business logic, event publishing
```

The 13 modules are: `auth`, `iam`, `admin`, `platform`, `dashboard`, `risk`, `cyber`, `bcm`, `privacy`, `compliance`, `reporting`, `datagov`, `workflow`.

### Key architectural patterns

**Multi-tenancy**: `TenantContext` (thread-local) stores `organisationId` extracted from the JWT by `JwtAuthenticationFilter`. All repository queries must filter by tenant.

**JWT auth flow**: Access token (15 min) in `sessionStorage` → sent as `Authorization: Bearer <token>`. Refresh token (7 days) in HttpOnly cookie. No HTTP sessions.

**Module isolation**: Modules must communicate through service interfaces only — no direct cross-module entity/repository access. Each module owns a dedicated SQL Server schema (`risk`, `cyber`, `bcm`, etc.).

**Access control**: Method-level `@PreAuthorize("hasPermission('MODULE', 'ACTION')")` — e.g., `RISK:READ`, `RISK:CREATE`.

**Flyway conventions**: Versioned migrations use `V{major}.{minor}.{patch}__{description}.sql`; repeatable migrations use `R__{description}.sql` (re-applied on checksum change, idempotent MERGE/upsert). `flyway.clean-disabled=true` — never run `flyway:clean`.

**Soft deletes**: All entities have `deleted_at`; filtered via Hibernate `@Where(clause = "deleted_at IS NULL")`.

**Bilingual UI**: `data-i18n` attributes on HTML elements; `translations.js` holds all English/Arabic strings; `rtl.css` + Bootstrap RTL are swapped dynamically when `ar` locale is active.

### Configuration profiles

- `dev` — Swagger enabled, local DB defaults, CORS allows `localhost:3000`
- `prod` — Swagger disabled, SSL/TLS (PKCS12), logs to `/var/log/tss-grc/`, no error details in responses

### Documentation

Comprehensive docs in `docs/` cover the full design:
- `docs/architecture/` — system design, security model, API patterns, frontend architecture, scalability
- `docs/domain/` — entity models per module
- `docs/database/` — physical DB design, indexing, migrations strategy
- `docs/diagrams/` — Mermaid ERDs and flow diagrams
- `sprints/` — phased delivery plan (Sprint 1 = Auth + Admin + ERM)
