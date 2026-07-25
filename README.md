# TSS GRC Platform

**Trusted Security Solutions — Enterprise Governance, Risk & Compliance Platform**

A bilingual (Arabic primary / English) GRC platform for regulated enterprises in Saudi Arabia and the MENA region. Aligned with NCA ECC, SAMA Cyber Resilience, ISO 27001:2022, ISO 22301, ISO 31000, and Saudi PDPL.

---

## Architecture Overview

```
┌──────────────────────────────────────────────┐
│      tss-grc-ui-v2/  (canonical UI)          │  React 18 + Vite + TypeScript
│      Bilingual EN/AR with full RTL           │  Vite dev server :5173
│      54 page workspaces · TanStack-bound     │  No SSR
└──────────────────┬───────────────────────────┘
                   │ REST + JSON · Authorization: Bearer <JWT>
                   │ WebSocket /ws (Sprint 4 — STOMP topics for live updates)
┌──────────────────▼───────────────────────────┐
│       Spring Boot 3.5 REST API   :8084       │  Modular monolith
│       25 modules, ~538 endpoints             │  92% @PreAuthorize coverage
│       JWT stateless · Spring Security 6      │  Multi-tenant via TenantContext
└──────────────────┬───────────────────────────┘
                   │ JDBC + Flyway (prod) / Python bootstrap (dev)
┌──────────────────▼───────────────────────────┐
│       SQL Server 2019/2022 · UTF-8           │  Per-tenant isolation
│       Schemas: auth, iam, platform, risk,    │  Soft-delete (is_deleted=0)
│       compliance, cyber, bcm, privacy,       │  Bilingual *En/*Ar columns
│       evidence, incident, tprm, intake,      │  Multi-tenant org_id every table
│       reporting, change_mgmt, ethics, scm,   │
│       workflow, dashboard, *_lc lifecycle    │
└──────────────────────────────────────────────┘
```

---

## Modules (25)

**Domain modules** (full CRUD + state-machine workflows):

| # | Module | What it does |
|---|---|---|
| 1 | `auth` | Login, refresh, logout, JWT issuance |
| 2 | `iam` | Users, roles, permissions, RBAC |
| 3 | `platform` | Organisations, tenant switcher, files, persistence rows, SSO/Nafath, notifications |
| 4 | `dashboard` | Aggregate KPIs, heat-map, recent records |
| 5 | `risk` | Risk register, KRIs, appetite, scoring |
| 6 | `risk_lc` | **Risk lifecycle** — treatment plans, KRI breaches, acceptance requests |
| 7 | `compliance` | Controls, frameworks, assessments, regulatory change, audit engagements |
| 8 | `cyber` | NCA ECC, SAMA CSF assessments, vulnerabilities, assets, incidents |
| 9 | `cyber_lc` | **Cyber lifecycle** — vulnerability state machine (NEW → CLOSED + RISK_ACCEPTED + FALSE_POSITIVE) |
| 10 | `bcm` | BCM plans, BIA, recovery strategies, exercises |
| 11 | `bcm_lc` | **BCM lifecycle** — invocations (STANDBY → DECLARED → INVOKED → STAND_DOWN → POST_INCIDENT_REVIEW → CLOSED) |
| 12 | `privacy` | PDPL: ROPA, DPIA, breach console |
| 13 | `privacy_lc` | **Privacy lifecycle** — DSR cases (Article 4 30/60-day SLA + EXTENDED) |
| 14 | `audit_lc` | **Audit lifecycle** — findings (OPEN → MGMT_RESPONSE → IN_REMEDIATION → AWAITING_VERIFICATION → CLOSED) |
| 15 | `incident` | 9-step incident lifecycle (DETECTION → ... → LESSONS_LEARNED) |
| 16 | `tprm` | Third-party risk, vendor inventory, due diligence |
| 17 | `evidence` | Evidence vault, framework-control matrix, CCM checks |
| 18 | `change_mgmt` | ITIL change management (13-state machine: NORMAL / EMERGENCY / STANDARD paths) |
| 19 | `ethics` | Ethics cases, conflicts of interest, gifts register |
| 20 | `scm` | Supply-chain mapping, supplier register, scoring |
| 21 | `workflow` | Generic workflow engine, runtime instances |
| 22 | `intake` | Schema-driven intake forms, public submitter portal |
| 23 | `reporting` | PDF/XLSX exports, scheduled reports |
| 24 | `admin` | (reserved for future tenant-admin features) |
| 25 | `datagov` | (reserved for v2 — data lineage + classification) |

---

## Quick Start (development)

### Prerequisites

- **JDK 17** (pinned — Lombok is incompatible with Java 25 in this project)
- **Maven 3.9+** *(or use the bundled portable copy at `.tools/apache-maven-3.9.9/`)*
- **Node 20+** for the v2 UI
- **SQL Server 2019/2022** (Express edition is fine for dev) with TCP enabled on port 1433
- **Python 3.10+** with `pyodbc` for the dev DB bootstrap scripts

### 1. Database

```bash
# Create DB
sqlcmd -S localhost -U sa -P tss.123 -Q "CREATE DATABASE TSS_GRC COLLATE Latin1_General_100_CI_AS_SC_UTF8;"

# Bootstrap Sprint-1 schemas (auth, iam, platform, risk, compliance, evidence, etc.)
python setup_cyber_db.py
python setup_bcm_db.py
python fix_arabic_db.py            # collation/Arabic data repair

# Sprint 4 lifecycle schemas (risk_lc / audit_lc / privacy_lc / bcm_lc / cyber_lc)
python setup_lifecycle_db.py
python setup_lifecycle_seed.py
```

### 2. Backend

```bash
# Required env vars (production-fail-fast contract)
export JWT_SECRET="$(openssl rand -base64 48)"
export DB_USERNAME=sa
export DB_PASSWORD=tss.123

cd tss-grc-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# → http://localhost:8084
# Swagger UI: http://localhost:8084/swagger-ui.html
# Health:     http://localhost:8084/actuator/health
```

### 3. Frontend (v2 — canonical)

```bash
cd tss-grc-ui-v2
npm ci
npm run dev
# → http://localhost:5173
```

### 4. Default credentials

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@12345` | PLATFORM_ADMIN |
| `riskmanager` | `Risk@12345` | RISK_MANAGER |
| `auditor` | `Audit@12345` | AUDITOR |

---

## Quick Start (Docker)

```bash
docker build -t tss/grc-platform:latest .

docker run -d --name grc -p 8084:8084 \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  -e DB_URL="jdbc:sqlserver://host.docker.internal:1433;databaseName=TSS_GRC;encrypt=true;trustServerCertificate=true" \
  -e DB_USERNAME=sa \
  -e DB_PASSWORD=tss.123 \
  tss/grc-platform:latest

curl -fsS http://localhost:8084/actuator/health
```

The image refuses to boot if `JWT_SECRET` is missing, shorter than 32 chars, or matches a known placeholder string (defensive against the historic JWT-fallback bug).

---

## Repository Structure

```
/
├── tss-grc-backend/              ← Spring Boot REST API
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/tss/grc/
│       │   │   ├── GrcApplication.java
│       │   │   ├── config/        ← SecurityConfig, CorsConfig, etc.
│       │   │   ├── security/      ← JwtTokenProvider, JwtAuthenticationFilter
│       │   │   ├── common/        ← BaseEntity, TenantContext, exception
│       │   │   └── module/        ← 25 functional modules (see table above)
│       │   └── resources/
│       │       ├── application.yml      (base; JWT_SECRET no default)
│       │       ├── application-dev.yml  (Swagger on, Flyway off, port 8084)
│       │       ├── application-prod.yml (SSL, Swagger off, externalised secrets)
│       │       └── db/migration/sqlserver/  (Flyway V3 → V20)
│       └── test/                  ← JUnit 5 + Mockito + AssertJ
│
├── tss-grc-ui-v2/                ← React/Vite/TS frontend (canonical)
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.tsx               (78 routes)
│       ├── components/           (reusable UI: DataTable, Modal, Card, etc.)
│       ├── pages/                (54 page folders)
│       ├── lib/                  (91 lib files: api, i18n, persistence, ...)
│       └── styles/
│
├── tss-grc-ui/                   ← LEGACY frontend (vanilla HTML/JS, kept for reference)
│
├── setup_*_db.py                 ← Per-module Python DB bootstrap (dev only)
├── setup_lifecycle_db.py         ← Sprint 4 lifecycle schema bootstrap
├── setup_lifecycle_seed.py       ← Sprint 4 demo data seed
│
├── Dockerfile                    ← Multi-stage; non-root; healthcheck
├── .dockerignore
├── .github/workflows/            ← CI (backend + frontend + Docker build)
│
├── docs/                         ← Architecture, domain, diagrams, roadmap
├── sprints/                      ← sprint-1.md → sprint-4-convergence.md
│
├── README.md  STATUS.md  CLAUDE.md  SESSION_SUMMARY.md
```

---

## Key Architectural Patterns

| Pattern | How |
|---|---|
| **Multi-tenancy** | `TenantContext` (ThreadLocal) populated by `JwtAuthenticationFilter` from JWT `org` claim. Every repo query filters on `organisation_id`. |
| **Authentication** | Stateless JWT. Access (15 min prod / 1h dev) as `Bearer` header. Refresh (7d) as HttpOnly cookie. |
| **Authorization** | Method-level `@PreAuthorize("hasPermission('MODULE','ACTION')")` via `ModulePermissionEvaluator`. |
| **Soft delete** | `is_deleted SMALLINT` + `@Where(clause = "is_deleted = 0")` on every entity. |
| **Bilingual data** | Every user-visible entity carries paired `*En` / `*Ar` columns. |
| **State machines** | `Map<String, Set<String>> ALLOWED` in service layer. Illegal transitions throw `IllegalTransitionException` → HTTP 409 with `{from, to, allowed[]}` body. |
| **Workflow visualization** | Frontend kanban pipelines (`<LifecyclePipeline>`) + visual workflow designer (`<WorkflowDesigner>`). |
| **Notifications** | Multi-channel outbox (in-app, email, SMS, WhatsApp, Slack/Teams, Nafath, Tadawul portal) with priority-based routing. |

---

## Production Profile

The `prod` Spring profile is fail-fast on misconfiguration:

- `JWT_SECRET` env var is **required** — must be ≥32 chars, must not be a placeholder. App refuses to start otherwise.
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` env vars are required.
- Swagger UI is **disabled**.
- Flyway is **enabled** (dev uses Python bootstrap instead).
- HTTPS via PKCS12 keystore (configure via `SSL_KEYSTORE_PATH` + `SSL_KEYSTORE_PASSWORD`).
- `flyway.clean-disabled: true` always (prevents accidental data loss).

---

## Documentation

| Document | What's in it |
|---|---|
| `STATUS.md` | Module-by-module completion state |
| `SESSION_SUMMARY.md` | Historical V1 handoff (2026-04-07) |
| `sprints/sprint-1.md` → `sprint-3.md` | Original three-sprint delivery plan |
| `sprints/sprint-4-convergence.md` | **Current sprint** — production blockers, frontend convergence with ServiceNow IRM, backend completion, quality |
| `docs/` | Detailed architecture, domain model, database design, diagrams, regulatory mapping |
| `CLAUDE.md` | Codebase conventions for AI-assisted development |

---

## Status (Sprint 4 in progress)

✅ **Completed in Sprint 4 so far:**
- 5 lifecycle backend modules (V15-V19) with state-machine REST endpoints
- 30 Mockito tests covering state-machine contracts
- JWT secret hardening (no fallback, fail-fast on placeholder)
- 409 + 404 structured error contract
- Authorization gaps closed (DashboardController, OrganisationController)
- 13 real TypeScript bugs eliminated (Card/Badge/icon-map issues)
- ReportSchedule bilingual columns (V20 migration)
- Dockerfile + GitHub Actions CI

🚧 **In flight:** Flyway V1/V2 baseline · TanStack Table v8 list engine · Recharts dashboards

📋 **See `sprints/sprint-4-convergence.md` for the full 12-week plan.**

---

## License

Proprietary — Trusted Security Solutions. All rights reserved.

---

## Appendix — standalone single-page prototype (`index.html`)

> Scope note: the sections above describe **this platform** — the Spring Boot
> backend (`tss-grc-backend`) and React SPA (`tss-grc-ui-v2`). The material below
> describes a separate, self-contained demo artefact that also lives in this
> repository root. It is not part of the deployed platform and shares no code
> with it.

The standalone `index.html` at the repository root is a single-page prototype for a **National GRC (Governance, Risk, and Compliance) Platform** with Arabic (RTL) and English (LTR) support.

## What is included

- Full front-end application in `index.html`
- Login and MFA flow (client-side mock)
- Multi-module navigation (risk, compliance, audit, policies, board reporting, evidence, etc.)
- Charts and analytics powered by Chart.js
- In-browser PDF export with classification watermarking
- CSV export utilities
- Role-based page-access logic in the client layer

## Run locally

Because this is a static app, you can run it directly with any local web server.

### Option 1 (Python)

```bash
python3 -m http.server 8080
```

Then open:

- http://localhost:8080/index.html

### Option 2 (VS Code Live Server)

Open the folder and launch `index.html` with Live Server.

## Notes

- This project is currently front-end only.
- Data persistence, real authentication, and backend APIs should be added in a production implementation.

## Test

Run a basic smoke test:

```bash
./scripts/smoke_test.sh
```
