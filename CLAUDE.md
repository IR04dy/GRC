# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Code path & companion docs

Working tree: `D:\AYMAN\GRC` — remote `github.com/Ayman-Server/Claude-GRC-2` (branch `master`).

- **`PROJECT_CHRONICLE.md`** — the master reference: business + functional reference for
  every module/screen/API, installation, configuration, operations, the full
  troubleshooting encyclopedia, all 28 developer landmines, and the complete
  development history. **Read it before deep work; this file stays lean on purpose.**
- `DEPLOYMENT.md` — Windows Server go-live runbook (WinSW services + nginx).
- `*_PLAN.md` at repo root — per-program review/remediation plans; each carries a
  §0 implementation-status tracker (what shipped, commits, migrations).
- `LOCAL_SETUP.md` — dev-machine bring-up. `reports/audit-report.html` — the platform audit.
- `README.md` / `STATUS.md` / `SESSION_SUMMARY.md` / `sprints/` — V1-era docs (historic; STATUS.md snapshot is 2026-04-22 and predates all remediation programs).

## Project overview

**TSS GRC Platform** — bilingual (Arabic primary / English), multi-tenant, enterprise
Governance, Risk & Compliance platform for Saudi Arabia / MENA. Modular-monolith
Spring Boot REST backend + **React SPA (`tss-grc-ui-v2`)**. No SSR.
Modules: risk (ERM/KRI/FAIR), compliance & policy, cyber + SOC ops (NCA ECC),
BCM (BIA/BCP/crisis/NCEMA), privacy (PDPL: RoPA/DPIA/DSR/breach), TPRM/SCM
(SAMA outsourcing), sustainability/ESG + green finance (GHG 15-category Scope 3,
CMA/TCFD/IFRS S1-S2, Green Sukuk/UoP/portfolio/GAR, carbon credits, ISAE 3410
assurance lineage), access governance (IAM: maker-checker requests, SoD engine,
recertification, break-glass), platform services (workflow engine adopted by 14
modules, evidence subsystem, exports, audit ledger, branding, committees).

---

## Commands

**JDK 17 is mandatory and NOT the shell default** (Git Bash picks a JDK 11 →
`mvn` fails "release 17 not supported"):

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17"
```

### Backend (Spring Boot, port 8084 in dev)

Required env vars — dev refuses to boot without real values:
- `JWT_SECRET` — ≥32 chars, must not contain `change-this`/`placeholder`
  (dev value in use: `tss-grc-dev-jwt-secret-0123456789-abcdefghij-KLMNOP`).
- `DB_PASSWORD` — dev SQL Server `sa` password: `tss.123`.

```bash
cd tss-grc-backend
SPRING_PROFILES_ACTIVE=dev JWT_SECRET=<secret> DB_PASSWORD=tss.123 mvn spring-boot:run
# profile MUST be the env var: -Dspring.profiles.active on the mvn CLI never reaches the
# forked app JVM → boots "default" (Flyway ON, user grc_app) and hangs in Hikari retries
# API http://localhost:8084 · Swagger /swagger-ui.html (dev only) · health /actuator/health

mvn -o clean package        # full build + all tests (~2,700 tests; ~1 min hot, 3–5 min cold)
mvn test -Dtest=ClassName#method
java -jar target/tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

Stop the running backend before `mvn clean` (jar lock) — kill by port
(`netstat -ano | findstr :8084`; DevTools = 2 java PIDs). When launching in a
background wrapper, **grep the log for `BUILD SUCCESS` / `Tests run:`** — wrapper
exit codes can lie.

### Frontend — `tss-grc-ui-v2` (React + Vite + TS; the primary UI)

```bash
cd tss-grc-ui-v2
npm run dev          # http://localhost:5173 — proxies /api and /ws → :8084
npx tsc --noEmit     # typecheck
npx vitest run       # unit tests
npx vite build       # production bundle → dist/
```

The FE calls same-origin `/api/*`; the Vite proxy forwards to :8084. **If the
backend is down, every button in the UI fails at once** — check :8084 first.
`tss-grc-ui/` is the legacy static frontend (superseded; `python server.py 8086`).

### Database (dev — Flyway is OFF in dev)

SQL Server Express `localhost:1433`, DB `TSS_GRC`, `sa/tss.123`. Dev schema is
**Python-bootstrapped** (`setup_cyber_db.py`, `setup_bcm_db.py`,
`setup_committees_db.py`, `fix_arabic_db.py`), demo data via `seed_real_data.py`
(idempotent, 3 Saudi tenants). Use the `py` launcher (not `python`), pyodbc +
ODBC Driver 17.

Migrations live in `tss-grc-backend/src/main/resources/db/migration/sqlserver/`
— **V1→V295 packaged; next free V296**. Convention for new migrations: write them
**idempotent**, apply to the dev DB **twice via pyodbc** (Flyway off in dev), and
let prod pick them up via Flyway (baseline 292 on the deployed server).
`flyway.clean` is disabled — never run `mvn flyway:clean`.

### Default dev credentials

`admin/Admin@12345` (PLATFORM_ADMIN) · `so_officer/Admin@12345` (CISO +
SUSTAINABILITY_OFFICER — the standard second approver for 4-eyes flows) ·
`compliance/Admin@12345` (GRC_ADMINISTRATOR) · `auditor/Admin@12345` ·
**`ranalyst/Analyst@12345`** (corrected 2026-07-29 — this file said `Admin@12345`;
that password 401s, and a login attempt costs a lockout counter) ·
`rmanager/Risk@12345` · `viewer/Viewer@12345`.
Full persona list: PROJECT_CHRONICLE §4.

Grants matter as much as passwords when probing authorization. Read them from
`iam.role_permissions` rather than inferring from the username — the useful shapes
are `so_officer` (EVIDENCE_READ+WRITE, RISK_READ, CYBER_READ, **no** COMPLIANCE/BCM;
ROLE_CISO), `ranalyst` (EVIDENCE+RISK+COMPLIANCE reads, **no** BCM/CYBER/PRIVACY/TPRM)
and `auditor` (EVIDENCE_WRITE + COMPLIANCE_WRITE + BCM_READ) — between them they cover
"holds the module permission", "holds the role but not the permission", and neither.

---

## House rules (established working conventions — follow them)

1. **Commit per verified slice**; work on a feature/fix branch.
2. **Never merge to master without the user's explicit "merge"** command.
3. **Authorization ratchet ≤ 62** — the suite enforces the count of unguarded
   write endpoints; every new write endpoint needs a real
   `hasPermission('MODULE','ACTION')` guard.
4. **Live-probe every slice** against the running stack (Python urllib + pyodbc),
   including guard 403s and SoD 409s; probe through the Vite proxy (:5173) when
   verifying what the browser does; use `admin` + `so_officer` for 4-eyes paths.
5. Test rows are tagged **`[REVIEW-TEST]`** and cleaned with **`CHARINDEX`**
   deletes (never `LIKE '[...]%'` — brackets are a SQL character class).
6. **Destructive DB operations need the user's explicit OK.**
7. Bilingual rule: user-visible records carry `*_en` **and** `*_ar`; Arabic names
   are backend-mandatory — client-gate them in forms.

## Architecture (condensed — details in PROJECT_CHRONICLE §5–§8)

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5.x, **Java 17 (pinned — Lombok breaks on newer)**, Spring Security 6 |
| Auth | JJWT — Bearer access token + HttpOnly refresh cookie; FE auto-refreshes on 401 |
| DB | SQL Server 2019+, UTF-8 collation, 50+ schemas, Flyway V1–V295 (OFF in dev) |
| ORM | Hibernate 6 / Spring Data JPA, `ddl-auto=none` |
| Exports | Apache POI (XLSX/DOCX/PPTX) + OpenPDF (RTL Arabic, bundled Amiri font) |
| Frontend | React 18 + Vite + TypeScript (`tss-grc-ui-v2`), Recharts, STOMP/SockJS |

Backend modules under `module/` — **58 packages** (57 with code; `datagov` is an empty
placeholder), 186 kLoC, 417 entities, 2,160 endpoints. Each follows
`controller / dto / entity / repository / service`.

- **Largest:** `bcm` 25k · `sust` 16.7k (+`finance`/`ghg`/`capa`/`vendor`) ·
  `platform` 10k · `risk` 9k · `ndi` 7.7k · `integrations` 7.6k · `cyber` 7.3k ·
  `socops` 6.8k · `privacy` · `compliance` · `tprm` · `workflow` · `reporting` ·
  `ethics` · `admin` · `iam` (+`iam/governance`) · `scm` · `ai` · `evidence` · `audit_lc`
- **Saudi / financial-sector:** `sharia`, `openbanking`, `outsourcing`, `rrp`, `cpty`,
  `regreporting`, `reginspect`, `aml`, `fraud`, `consumer`, `opres`, `nca_ecc`,
  `dataclass`, `data_officer`, `regchange`
- **Kernel-ish:** `auth`, `attachment`, `signature`, `approvals`, `capa`, `intake`,
  `dashboard`, `knowledge`, `controlmap`, `workflow_submission`
- **Other:** `change_mgmt`, `policy`, `incident`, `governance`, `ccm`, `audit_committee`,
  `audit_eng`, `board_pack`, and the `*_lc` lifecycle packages (`risk_lc`, `cyber_lc`,
  `bcm_lc`, `privacy_lc`)

⚠ **This file previously listed 24 modules** — a 40% understatement that silently scoped
34 packages out of the RBAC catalogue, the test bar and every remediation plan. Full census
with per-module LoC/tests/coupling: `TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md`
(regenerate with `Appendix/scripts/coupling.py`).

**Key patterns:**
- **Multi-tenancy** — `TenantContext` thread-local from JWT; **every repository
  query filters `organisation_id`** (developer responsibility); prod adds a
  tenant write-guard interceptor. Schedulers/async must be org-explicit.
- **Authorization** — `@PreAuthorize("hasPermission('MODULE','ACTION')")` via
  `ModulePermissionEvaluator`; **114-permission catalogue across 31 modules**
  (READ/WRITE/APPROVE/DELETE/TRIAGE/EXPORT), measured 2026-08-01 in both dev and
  prod — this line said "94 / 24" for months; **PLATFORM_ADMIN bypasses in code**,
  so `admin` proves nothing about authorization: probe with a real persona.
  ⚠ **`hasAnyRole(...)` is a second, independent gate that gets NO admin bypass**
  and validates nothing — **537 endpoints still authorise by role name, reciting
  role names with a ~50% miss rate against `iam.roles`** (F2-05/F2-52). Before
  trusting or writing one, check the names exist.
- **Soft deletes** — `is_deleted` + `@Where`; native SQL must filter it.
  Exception: `iam.user_roles` rows are hard-deleted.
- **Four-eyes/SoD gates are server-enforced 409s** (allocation approve, tag decide,
  request decide, campaign self-certify, break-glass self-review, GHG verify needs
  evidence…). A 409 with an SoD message is the feature working — test with two users.
- **Workflow engine** (`workflow.requests`) + **evidence subsystem** (SHA-256,
  four-eyes) + **auto-CAPA** (`sust.corrective_action`; every new `source_type`
  must be added to the `ck_ca_source` CHECK or inserts 500).
- **Errors** — RFC-7807 ProblemDetail; FE `problemMessage()` folds the
  bean-validation `errors` map into toasts, so failures name the field.

## Configuration profiles

- `dev` — Swagger on, Flyway off, port 8084, permissive CORS (localhost:3000/5173/
  5500/8086/8087 + LAN), lockout effectively off, 1 h tokens.
- `prod` — Swagger off, Flyway on (baseline-aware), hardened; **all runtime config
  in `external.properties` next to the jar** (template:
  `tss-grc-backend/external.properties.template`; reference: PROJECT_CHRONICLE §11).
  Deployed server: `D:\Deployment\tss-grc` — WinSW services, backend :8090 /
  management :8091, nginx :80 serves the SPA + proxies `/api` and `/ws`.

## Top landmines (full list of 28: PROJECT_CHRONICLE §16)

- `JAVA_HOME` → JDK 17, always (fresh shells default to 11).
- ROWVERSION double-flush: never save an entity twice around a native
  `@Modifying` on the same table — native first, mutate + save once.
- Engine-wiring TX rules: `afterCommit` + per-call `REQUIRES_NEW`
  `TransactionTemplate` + native stamp updates (joined-TX failures poison commits).
- `@Valid` fires **before** `@PreAuthorize` — permission probes need a valid body.
- Off-request audit: guard `RequestContextHolder.getRequestAttributes()`;
  `TenantContext` isn't set off-request.
- `[REVIEW-TEST]` in `LIKE` is a character class — `CHARINDEX` for literals.
- Reboot port-steal: keep default MSSQLSERVER **Manual**; Express owns 1433.
- Nested Spring Data repositories aren't scanned; lazy-collection entities 500
  outside the session — return DTOs.
- `seed_real_data.py` batches must start `SET NOCOUNT ON;`.
- Don't "seed" an empty table before checking it's wired (`privacy_lc` is orphaned).
