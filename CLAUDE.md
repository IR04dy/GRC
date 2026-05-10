# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Code Path

Source of truth: `C:\AYMAN\Github\Claude-GRC-2`
Origin: extracted from `claude/quirky-borg` branch (commit `726cd1e`) of `C:\AYMAN\Github\Claude-GRC` on 2026-04-29.

Companion docs at the repo root carry context that is *not* in this file:
- `README.md` — public overview, full tech matrix, regulatory mapping
- `STATUS.md` — module-by-module completion state (last snapshot 2026-04-22)
- `SESSION_SUMMARY.md` — full V1 handoff (2026-04-07): bugs fixed, decisions, design palette
- `sprints/sprint-{1,2,3}.md` — phased delivery plan

## Project Overview

**TSS GRC Platform** — bilingual (Arabic primary / English), multi-tenant, enterprise Governance, Risk & Compliance platform for Saudi Arabia and MENA. Modular monolith Spring Boot REST backend + standalone static frontend. No SSR, no Thymeleaf.

---

## Commands

### Backend (Spring Boot, port 8084 in dev)

```bash
cd tss-grc-backend
mvn spring-boot:run -Dspring.profiles.active=dev
# API:        http://localhost:8084
# Swagger UI: http://localhost:8084/swagger-ui.html  (dev only)
# Health:     http://localhost:8084/actuator/health
```

```bash
mvn clean package -DskipTests
# Artifact: tss-grc-backend/target/tss-grc-platform-0.1.0-SNAPSHOT.jar
java -jar tss-grc-backend/target/tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev

mvn test                              # all tests
mvn test -Dtest=ClassName             # single class
mvn test -Dtest=ClassName#methodName  # single method
```

**Java pinned to 17.** Do not bump — Lombok is incompatible with Java 25 in this project (incident already happened, see memory `project_v2_lessons.md`).

### Frontend (no build step)

The UI is plain static HTML/CSS/JS. Three equivalent launchers (pick one):

```bash
cd tss-grc-ui
python server.py 8086        # canonical — see server.py default port
# or
python server.py 8087        # alternate (matches STATUS.md)
# or
npx live-server --port=3000  # node alternative
```

Open `http://localhost:<port>/login.html`. The dev profile's CORS allowlist already covers ports 3000, 5500, 8086, 8087 (see `application-dev.yml`).

### Database setup (dev — Flyway is OFF in dev)

Critical: in the **dev profile** `spring.flyway.enabled=false` and `jpa.hibernate.ddl-auto=none`. The database is bootstrapped by Python scripts at the repo root, not by Flyway:

```bash
# 1. Create DB on a local SQL Server with sa/tss.123
sqlcmd -S localhost -U sa -P tss.123 -Q "CREATE DATABASE TSS_GRC COLLATE Latin1_General_100_CI_AS_SC_UTF8;"

# 2. Run module bootstrap scripts (one per module)
python setup_cyber_db.py     # cyber schema + 41 seed rows
python setup_bcm_db.py       # bcm schema + seed
python fix_arabic_db.py      # Arabic collation/data repair

# Sprint-1 schemas (auth, iam, risk, incident, etc.) come from the SQL files
# under db/sqlserver/ and from Sprint-1's setup steps documented in STATUS.md.
```

Flyway migrations under `tss-grc-backend/src/main/resources/db/migration/sqlserver/` (`V3__compliance_module.sql` … `V10__evidence_permissions.sql`) are intended for **non-dev profiles** and apply on top of an already-bootstrapped DB. Treat the dev DB as Python-managed; treat staging/prod as Flyway-managed. `flyway.clean-disabled=true` always — never run `mvn flyway:clean`.

### Default seeded credentials

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@12345` | PLATFORM_ADMIN |
| `riskmanager` | `Risk@12345` | RISK_MANAGER |
| `auditor` | `Audit@12345` | AUDITOR |

Dev DB account: `sa / tss.123` (hard-coded in `application-dev.yml`).

---

## Architecture

### Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5.0, Java 17 (pinned), Spring Security 6 |
| Auth | JJWT 0.12.6 — stateless JWT (Bearer access + HttpOnly refresh cookie) |
| Database | SQL Server 2019 Express, UTF-8 collation (`Latin1_General_100_CI_AS_SC_UTF8`) |
| ORM | Hibernate 6 / Spring Data JPA — `ddl-auto=validate` (base) / `none` (dev) |
| Migrations | Flyway 10.15 — non-dev profiles only |
| Caching | Caffeine L1 (`maximumSize=1000, expireAfterWrite=300s`) |
| Export | Apache POI 5.2 (Excel), OpenPDF 1.3 (Arabic RTL PDF) |
| API docs | SpringDoc OpenAPI 2.8 |
| Frontend | Plain HTML5 + Bootstrap 5 RTL + Chart.js 4 + Vanilla JS |

### Repository layout

```
/
├── tss-grc-backend/                          ← Spring Boot REST API
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/tss/grc/
│       │   ├── GrcApplication.java
│       │   ├── config/        ← SecurityConfig, CorsConfig, DataSourceConfig,
│       │   │                    CacheConfig, OpenApiConfig, LocaleConfig, WebMvcConfig
│       │   ├── security/      ← JwtTokenProvider, JwtAuthenticationFilter,
│       │   │                    GrcUserDetails, ModulePermissionEvaluator
│       │   ├── common/        ← BaseEntity, tenant/, exception/, model/
│       │   └── module/        ← 17 functional modules (see below)
│       └── resources/
│           ├── application.yml         ← base
│           ├── application-dev.yml     ← Swagger on, Flyway off, port 8084, sa/tss.123
│           ├── application-prod.yml    ← SSL, Swagger off, externalised secrets
│           ├── messages*.properties    ← server-side i18n (validation messages)
│           └── db/migration/sqlserver/ ← Flyway V3 → V10 (compliance, incident, tprm, evidence…)
│
├── tss-grc-ui/                               ← Standalone static frontend
│   ├── login.html, dashboard.html, index.html
│   ├── server.py                             ← simple HTTP server (default port 8086)
│   ├── start-ui.bat, serve.ps1               ← Windows launchers
│   ├── StaticFileServer.java                 ← optional Java-based static server
│   ├── assets/css/  style.css  rtl.css
│   ├── assets/js/   auth.js  api.js  i18n.js  app.js  translations.js  config.js
│   └── <module>/index.html                   ← admin, bcm, compliance, cyber, evidence,
│                                                incidents, privacy, risk, scm, tprm
│
├── setup_cyber_db.py, setup_bcm_db.py        ← module DB bootstrap (dev — replaces Flyway)
├── fix_arabic_db.py                          ← Arabic collation/data repair utility
├── db/sqlserver/                             ← reference DDL (schemas, tables, indexes, views)
├── docs/   {analysis,architecture,domain,database,diagrams,grc,roadmap}/
└── sprints/  sprint-1.md  sprint-2.md  sprint-3.md
```

### Backend module structure

The 17 modules under `module/` are: `admin`, `auth`, `bcm`, `compliance`, `cyber`, `dashboard`, `datagov`, `evidence`, `iam`, `incident`, `platform`, `privacy`, `reporting`, `risk`, `scm`, `tprm`, `workflow`.

Each module follows the same layered layout:

```
module/{name}/
├── controller/   @RestController — pure JSON
├── dto/          Request/Response DTOs with Jakarta validation
├── entity/       JPA @Entity (extends BaseEntity)
├── repository/   Spring Data JPA — soft-delete-filtered, org-scoped
└── service/      @Service @Transactional — business logic, workflow transitions
```

### Key architectural patterns

**Multi-tenancy.** `TenantContext` (thread-local) holds `organisationId`, populated by `JwtAuthenticationFilter` from the JWT claims. **Every repository query must filter by `organisation_id`** — typically via `findBy*OrganisationId(...)` finder methods. There is no global Hibernate filter for this; it is the developer's responsibility per query.

**JWT auth.** Access token = 1 hour in dev / 15 min in prod, sent as `Authorization: Bearer …` and stored in `sessionStorage`. Refresh token = 7 days, in an HttpOnly cookie. No HTTP sessions on the server.

**Access control.** Method-level `@PreAuthorize("hasPermission('MODULE','ACTION')")` resolves through `ModulePermissionEvaluator` (e.g. `RISK:READ`, `CYBER:WRITE`).

**Soft deletes.** Every entity has an `is_deleted` (smallint/bit) column and is annotated `@Where(clause = "is_deleted = 0")` — that filter is part of the entity, so reads via JPA never see deleted rows. Use `is_deleted = 0` (not `deleted_at IS NULL`) when writing native SQL.

**Bilingual data.** User-visible entities carry `titleEn` / `titleAr` (and similar) columns. The frontend reads `I18n.current` and selects the right column. Never store user-visible strings in just one language.

**Bilingual UI.** `data-i18n` attributes on HTML elements; `translations.js` holds all EN/AR strings; `rtl.css` + Bootstrap RTL are swapped dynamically when `ar` locale is active. The global `app.js` injects the sidebar/header shell into every page via `#app-shell`.

**Workflows.** State stored as a `workflow_status` string column; transitions validated and applied in the service layer (see `STATUS.md` for the per-module state machines — e.g. cyber incident's 9-step DETECTION→…→LESSONS_LEARNED).

### Configuration profiles

- `dev` — Swagger on, Flyway off, JPA `ddl-auto=none`, port 8084, dev JWT secret, lockout effectively disabled (99 attempts / 1 min), broad CORS allowlist. Hard-coded `sa / tss.123`.
- `prod` — Swagger off, SSL/TLS (PKCS12), externalised secrets, no error details in responses. Use Flyway here.

### Known landmines (already fixed once — do not regress)

Captured in `~/.claude/projects/.../memory/project_v2_lessons.md`:
- `JwtAuthenticationFilter` ordering and `TenantContext` setup
- Swagger CSP headers in `SecurityConfig`
- BCrypt password literal escaping in shell/Python seed scripts
- Java 17 pin (Lombok breaks on Java 25)
- `api.js` must use absolute URL to backend (frontend and backend on different ports in dev)

---

## Documentation

`docs/` carries the full design (analysis, architecture, domain, database, diagrams, roadmap). Three sprint plans live in `sprints/`. For day-to-day "what's done / what's next," `STATUS.md` is more current than the docs/ tree.
