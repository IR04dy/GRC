# TSS GRC Platform — Full Project Chronicle & Application Reference

> **What this document is:** the complete, detailed record of everything done on this
> platform across all working sessions (2026-06-18 → 2026-07-11), plus a full reference
> of what the application is, how it is built, how it is secured, how it is verified,
> and how it is deployed. Written 2026-07-11 at master `d176dc90`.

---

## 1. The application

**TSS GRC Platform** is a bilingual (Arabic-primary / English), multi-tenant, enterprise
**Governance, Risk & Compliance** platform purpose-built for **Saudi Arabia and the MENA
region**. It is a modular-monolith Spring Boot REST backend with a standalone React
frontend — no server-side rendering.

It covers the full GRC surface in one product:

- **Enterprise Risk Management** — risk register, appetite, KRIs, treatment plans,
  acceptance workflow, multi-dimensional impact, typed risk linkage, committee wiring.
- **Compliance & Policy** — obligations, frameworks/controls, findings + CAPA lifecycle
  with reopen paths, policy documents with real file versioning and SoD, audit
  evidence packages.
- **Cybersecurity + SOC Operations** — NCA ECC frameworks, vulnerabilities with
  risk-based SLA clocks, incidents (9-step lifecycle), assets, threat intel with
  IOC→asset/vuln links, MITRE detection library, forensics custody ledger,
  SIEM/EDR/VM integrations (Splunk, CrowdStrike, Tenable, ServiceNow — stub + live modes).
- **Business Continuity (BCM)** — BIA, BCP/DR plans (generated from BIA), exercises with
  observation→gap→CAPA promotion, crisis events with regulatory deadline calculators,
  NCEMA/SAMA OpRes packets, evidence binders.
- **Privacy (PDPL)** — RoPA, DPIA, DSR cases with 30-day countdown, breach 72-hour clock,
  consent, data governance (retention/disposal/catalog/lineage/quality).
- **Third-Party Risk (TPRM/SCM)** — vendor register, assessments, outsourcing (SAMA),
  vendor BCP campaigns, vendor ESG surveillance.
- **Sustainability / ESG** — GHG inventory (versioned emission-factor library), CMA/TCFD
  disclosure registers with SoD approval + content-lock, IFRS S1/S2 readiness checklist,
  CSRD double materiality, climate scenarios with computed expected loss, financed
  emissions (PCAF), SGI/net-zero-2060 commitments, auto-CAPA on every gap type, and the
  full **green-finance layer** (below).
- **Green Finance** — Green Sukuk / bond / loan register with Shariah-structure gate,
  ICMA/CCE use-of-proceeds allocation with maker-checker and over-allocation hard block,
  impact attribution math, allocation reports with frozen figures, green portfolio
  (taxonomy tagging, Green Asset Ratio, weighted data quality), carbon credits
  (purchase/retire with certificate), ESG targets with trajectory auto-CAPA, NCEC permit
  expiry surveillance, EU CBAM exposure screen — designed around REG-1..11 (SOCPA IFRS
  S1/S2, CMA, Tadawul, SGI, NCEC, MWAN, SEEC, ICMA GBP, SAMA, EU-CBAM, RVCMC).
- **Access Governance (IAM)** — runtime role catalogue + permission-matrix editor,
  maker-checker access requests, time-bound assignments, JML hooks, SoD engine
  (preventive + detective), recertification campaigns (Hijri-stamped), break-glass
  emergency access, access analytics, SAMA/NCA access-evidence ZIP.
- **Platform services** — workflow request engine (14 modules adopted), notifications,
  audit ledger, brand theming (white-label), exports (PDF/XLSX/DOCX/PPTX/ZIP, bilingual
  RTL), executive dashboards, board pack, regulator packages, AI suggestions, committees,
  intake, NDI, audit lifecycle, automation rules (F8 events), workflow definitions.

**Regulatory anchoring (Saudi-first):** NCA ECC, SAMA CSF & Operational Resilience, PDPL
(SDAIA), CMA disclosure articles, CBB/UAE portability, NCEMA, SOCPA (IFRS S1/S2), Tadawul
ESG guidelines, Saudi Green Initiative, NCEC environmental permitting, MWAN, SEEC, ICMA
Green Bond Principles, EU CBAM. Hijri + Gregorian dual-dating on attestations and review
windows.

---

## 2. Technology stack & repository layout

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5.x, Java 17 (pinned — Lombok breaks on newer JDKs), Spring Security 6 |
| Auth | JJWT — stateless JWT (Bearer access token + HttpOnly refresh cookie) |
| Database | SQL Server 2019 (Express in dev), UTF-8 collation `Latin1_General_100_CI_AS_SC_UTF8` |
| ORM | Hibernate 6 / Spring Data JPA (`ddl-auto` none — schema owned by migrations) |
| Migrations | Flyway (V1→V292 packaged; **OFF in dev**, ON in prod with baseline support) |
| Cache | Caffeine L1 |
| Exports | Apache POI (XLSX/PPTX/DOCX), OpenPDF (Arabic RTL PDF, bundled Amiri OFL font) |
| API docs | SpringDoc OpenAPI (dev only) |
| Frontend | **tss-grc-ui-v2** — React + Vite + TypeScript, Recharts, STOMP/SockJS live panels, full AR/RTL i18n |
| Legacy frontend | `tss-grc-ui/` — original static HTML/Bootstrap build (superseded by v2) |
| Observability | Actuator (separate port 8090), Prometheus metrics, optional Sentry |

```
D:\AYMAN\GRC                          ← repo root (github.com/Ayman-Server/Claude-GRC-2)
├── tss-grc-backend/                  ← Spring Boot REST API (module/{17+ modules})
│   └── src/main/resources/db/migration/sqlserver/   V1__ … V292__ (274 files)
├── tss-grc-ui-v2/                    ← React frontend (Vite, :5173 dev)
├── tss-grc-ui/                       ← legacy static frontend
├── setup_*.py / seed_real_data.py    ← dev-DB bootstrap + Saudi-native demo data
├── deploy/                           ← staged cloud artifacts (gitignored)
├── DEPLOYMENT.md                     ← Windows Server go-live runbook
├── reports/audit-report.html         ← the 7-phase platform audit (interactive)
├── audit/                            ← audit raw data, harnesses, generators
└── *_PLAN.md                         ← per-program review/remediation plan documents
```

Backend modules under `module/`: `admin`, `ai`, `auth`, `bcm`, `compliance`, `cyber`,
`dashboard`, `datagov`, `evidence`, `iam` (+ `iam/governance`), `incident`, `intake`,
`ndi`, `platform` (audit log, branding, notifications), `privacy`, `reporting`, `risk`,
`scm`, `socops`, `sust` (+ `sust/finance`, `sust/ghg`, `sust/capa`, `sust/vendor`),
`tprm`, `workflow` — each with `controller / dto / entity / repository / service`.

---

## 3. Core architecture patterns

- **Multi-tenancy** — `TenantContext` (thread-local) carries `organisationId`, set by
  `JwtAuthenticationFilter` from JWT claims. **Every repository query filters by
  `organisation_id`** (developer responsibility, no global filter). Defence-in-depth:
  `TenantWriteGuardInterceptor` (Hibernate flush interceptor, ON in prod) blocks
  cross-tenant writes unless explicitly allowed.
- **Authorization** — method-level `@PreAuthorize("hasPermission('MODULE','ACTION')")`
  resolved by `ModulePermissionEvaluator` into `MODULE_ACTION` authorities;
  `PLATFORM_ADMIN` bypasses in code. A **ratchet test**
  (`ControllerAuthorizationRatchetTest`) pins the count of unguarded write endpoints —
  it may only decrease (history: 422 → 61 → 62 with two documented public-endpoint
  exceptions).
- **Soft deletes** — entities carry `is_deleted` + `@Where(clause = "is_deleted = 0")`;
  native SQL must filter `is_deleted = 0`. (Exception: `iam.user_roles` join rows are
  **hard-deleted** on revocation — `@ManyToMany` ignores join-table soft-delete flags.)
- **Bilingual by construction** — `titleEn/titleAr` (etc.) columns everywhere; UI
  dictionaries per locale (lazy-loaded chunks); RTL run-direction threaded through every
  PDF/XLSX/PPTX generator; Hijri stamps via a shared calendar helper.
- **Workflow request engine** — generic `workflow.requests` engine (approval / review /
  acknowledgement / broadcast / report-distribution), actor selectors (user / role /
  committee with member fan-out), ALL/ANY/QUORUM/MAJORITY completion, SLA + multi-tier
  escalation ladders, conditional routing, distribution lists, tokenized EXTERNAL
  delivery with a public tracker, and record-anchored panels adopted by all 14 modules.
- **Evidence subsystem** — one generic evidence/attachment engine (upload, SHA-256,
  lifecycle DRAFT→PENDING_REVIEW→APPROVED/REJECTED, four-eyes) bridged into modules via
  typed links; evidence-request SLA scheduler; rejection auto-opens module gaps.
- **Auto-CAPA pattern** — `sust.corrective_action` with `(source_type, source_ref)`
  idempotency; sources: SGI / CMA / TCFD / EVIDENCE / VENDOR_ESG / GHG / CLIMATE_RISK /
  ANNEX_GAP / ESG_TARGET / ENV_PERMIT (whitelisted by the `ck_ca_source` CHECK — extend
  it for every new source or inserts 500).
- **Export stack** — shared `PdfTheme` + `PdfFonts` (Amiri bundled) + `XlsxStyler`
  (brand fill, freeze, auto-filter, RTL sheets, status conditional formatting) +
  `ExportNaming` (`[Module]_[Type]_[LANG]_[YYYYMMDD]_vN.ext`) + `BrandThemeService`
  (per-org white-label, `platform.brand_theme`) + `ExportAuditInterceptor` (every
  attachment download auto-logged as `action=EXPORT`). Evidence ZIPs carry
  `manifest.json` with SHA-256 per entry.
- **Audit ledger** — `platform.audit_log` with before/after JSON, severities
  INFO/WARN/ERROR, off-request-safe writes (guard `RequestContextHolder`, a documented
  landmine). Domain events (F8) drive automation rules and record-creators.
- **Schedulers** — off-request-safe (`TenantContext` set per-org or org-explicit
  methods): assignment expiry (hourly), SoD detective scan (nightly 02:30 UTC),
  break-glass expiry (30-min), evidence expiry, vuln/CAPA/workflow SLA clocks, vendor
  ESG escalation, DB-size monitor (SQL Express 10 GB cap).

---

## 4. Security & access-control model (as of V292)

- **Permission catalogue:** **94 permissions** = 24 modules × standard actions
  `READ · WRITE · APPROVE · DELETE` (+ `TRIAGE` where queues exist) **+ `EXPORT` as the
  6th action** (23 modules, rolled out with READ-parity grants so behaviour is unchanged
  until a tenant tightens) + the dedicated `IAM_BREAK_GLASS` activation permission.
- **Personas:** ~55 bilingual roles per tenant (V272 matrix, +292 grants), from
  GRC_ADMINISTRATOR through module officers to read-only ASSURANCE/REGULATOR personas;
  system roles are non-deletable templates; structural rules enforced in the matrix
  editor (APPROVE⇒READ, DELETE⇒WRITE).
- **Access lifecycle (the `iam/governance` module, P0–P3):**
  - Role CRUD + matrix editor + per-user effective-access view (revive pattern for
    soft-deleted role names).
  - **Maker-checker access requests** — a grant reaches `iam.user_roles` only on
    approval; approver ≠ requester ≠ beneficiary (409); time-bound validity windows;
    hourly expiry sweep; JML LEAVER processing from `socops.iam_jml_event`.
  - **SoD engine** — rule pack SOD-01..08 (RISK/EVIDENCE/COMPLIANCE/CAPA/TPRM
    maker-checker pairs, IAM×AUDIT admin≠auditor, read-only assurance cluster,
    single-purpose service accounts); preventive check 409s conflicting grants at
    approval (override = GRC_ADMINISTRATOR + documented justification, still recorded);
    nightly detective scan writes `iam.sod_violations` with dispositions
    OPEN → ACCEPTED_RISK (expiry mandatory) | REMEDIATED; violator cannot disposition
    their own violation; PLATFORM_ADMIN excluded by design.
  - **Recertification campaigns** — scope PRIVILEGED/MODULE/ALL, items snapshotted from
    live assignments, Hijri+Gregorian windows, decisions CERTIFY/REVOKE/MODIFY (REVOKE
    executes immediately), reviewers cannot certify their own access, closure blocked
    until 100% dispositioned or GRC_ADMINISTRATOR exception.
  - **Break-glass** — pre-provisioned `EMERGENCY_RESPONDER` (system, privileged,
    operational-response grant set), activation gated by `IAM/BREAK_GLASS` (CISO,
    IR_LEAD, GRC_ADMINISTRATOR), mandatory incident ref, ≤24 h validity, CRITICAL-level
    audit event, auto-expiry, mandatory post-hoc review by someone else.
  - **Analytics** — privileged holders, dormant grants (no login ≥90 d), grants expiring
    ≤30 d, open SoD violations, break-glass posture, campaign completion %, top grant
    holders.
  - **Access evidence ZIP** — branded README.pdf + Role_Catalogue / Permission_Matrix /
    User_Assignments / SoD_Violations / Recertification_Evidence XLSX + SHA-256
    manifest; guarded by `IAM/EXPORT` (the EXPORT-gating pilot).
- **Hardening history:** all 4 audit Criticals + 52 Highs fixed; mass-assignment closed
  via create-guards (`setId(null)` + org stamp); page-size clamps global + 65 manual
  endpoints; public intake tracker PII-free + rate-limited; Splunk HMAC fail-closed;
  Trivy fat-jar scan 0 HIGH/CRITICAL after dependency bumps.

**Seeded dev credentials** (rotate on any real deployment): `admin/Admin@12345`
(PLATFORM_ADMIN), `rmanager/Risk@12345`, `viewer/Viewer@12345`, and `auditor` /
`compliance` (GRC_ADMINISTRATOR) / `so_officer` (CISO + SUSTAINABILITY_OFFICER) /
`ranalyst` = `Admin@12345`. Dev DB `sa/tss.123`. Failed logins increment a lockout
counter (5 attempts / 30 min in prod).

---

## 5. Database

- SQL Server, one database `TSS_GRC`, **50+ schemas** (`platform`, `iam`, `risk`,
  `compliance`, `cyber`, `socops`, `bcm`, `privacy`, `sust`, `tprm`, `workflow`,
  `evidence`, lifecycle schemas `*_lc`, and more).
- **Migrations V1→V292** packaged in the jar (274 files). V1/V2 recreate the
  Python-bootstrapped baseline so a *fresh* DB can be built by Flyway alone.
- **Dev:** Flyway OFF — the dev DB was restored/bootstrapped and every migration from
  V225 onward has been applied **by hand via pyodbc** as it was authored (each one
  written idempotent and applied twice to prove it).
- **Prod:** Flyway ON with `FLYWAY_DEFAULT_SCHEMA=platform` (the emptiness check must
  look at a populated schema — checking default `dbo` wrongly re-runs V1/V2 on a
  restored DB) and `FLYWAY_BASELINE_VERSION=292` for restored databases, so only future
  migrations (V293+) auto-apply at startup. `flyway.clean` permanently disabled.
- **Demo data:** `seed_real_data.py` (idempotent) populates 3 Saudi-native tenants —
  TSS GRC (Saudi bank: T24/SADAD/Murex, PDPL articles, SAMA/NCA/SDAIA/CMA), Aramco
  Trading (energy/SCADA), STC Bank (telco-banking) — ~450 rows across 23 tables.

---

## 6. Environments & operations

**Local dev machine** (`D:\AYMAN\GRC`, Windows 11): SQL Server Express on `localhost:1433`
(`MSSQL$SQLEXPRESS`; the default instance is set Manual so it can't steal the port after
reboot), backend jar detached on **:8084** (dev profile, DevTools = 2 java PIDs — kill by
port), Vite dev server **:5173** (proxies `/api` + `/ws`), LAN reachable at
`192.168.8.249:5173` (firewall rule present). Toolchain: JDK 17 at
`C:\Program Files\Java\jdk-17` (**must** export `JAVA_HOME` — fresh shells default to a
JDK 11), Maven 3.9, Node 24, Python 3.14 via the `py` launcher (+ pyodbc, ODBC Driver 17).

**Configuration model (2026-07-11):** `application.yml` imports
`optional:file:./external.properties` — **one file next to the jar holds every runtime
setting** (JWT secret, DB, ports/SSL, Flyway, paths, SMTP, integrations). Edit + restart;
no environment variables, no rebuild. UPPER_CASE entries feed the `${PLACEHOLDER}`
references (always win); raw Spring keys also work unless the packaged prod profile pins
that key. Template committed at `tss-grc-backend/external.properties.template`; the
filled file is gitignored.

**Production deployment (Windows Server, IP-only HTTP)** — full runbook in
`DEPLOYMENT.md`; artifacts staged in `deploy/`:

```
Browser ──► nginx :80  (serves ui-dist + proxies /api and /ws)
              └──► Spring Boot :8084  (prod profile, HTTP, 127.0.0.1, external.properties)
                      └──► SQL Server :1433  (TSS_GRC restored from .bak, Flyway baseline 292)
              Actuator :8090 (loopback only)
```

Artifacts: backend jar (100 MB) · `external.properties` · `ui-dist/` (29 MB) ·
`TSS_GRC_2026-07-11.bak` (127 MB, RESTORE VERIFYONLY passed; logical files
`TSS_GRC`/`TSS_GRC_log`) · `nginx.conf` (SPA fallback, /api + /ws upgrade headers,
100 MB uploads) · thin `run-backend.cmd` · the runbook. Services run under NSSM;
firewall exposes port 80 only; nightly backup via Task Scheduler; post-go-live:
rotate every seeded password, optionally drop demo tenants 2–3, add TLS at nginx later.

---

## 7. The chronicle — everything done, in order

### 7.0 Pre-history (V1, before 2026-06-18)

The platform was built across three sprints (docs in `sprints/`, handoff in
`SESSION_SUMMARY.md`): core modules, auth/JWT, bilingual UI, the original static
frontend, then the React v2 frontend. Source of truth moved to
`github.com/Ayman-Server/Claude-GRC-2`.

### 7.1 Environment bring-up (2026-06-18)

Cloned and flattened into `D:\AYMAN\GRC`; SQL Server Express restored `TSS_GRC`;
backend + frontend launched detached with durable logs; CORS LAN patterns validated
with a real build; firewall rule for :5173. Recorded in `LOCAL_SETUP.md` + memory.

### 7.2 The platform audit (2026-06-18, commit `3ca91bf`)

A **7-phase audit** — Security, Quality, Performance, Functional, E2E, UX,
Accessibility — produced **287 findings (4 Critical / 52 High / 114 Medium / 93 Low /
24 Info)**, every Critical/High adversarially verified. Deliverable:
`reports/audit-report.html` + raw data under `audit/`.

The 4 Criticals (all authorization/tenancy): IDOR read+write across `CyberService`
(SEC-CYB-001/002), unauthenticated public intake tracker leaking submissions
(SEC-FIN-001), approval-chain actor identity taken from the request body (SEC-IAM-001).
Systemic theme: broken function-level authorization (`isAuthenticated()` on writes).

### 7.3 Audit remediation waves (2026-06-21 → 2026-06-22)

- **P0 Criticals** (`fix/audit-p0-criticals`): all 4 Criticals + the CCM SQL runner
  locked down; the **authorization ratchet test** introduced (baseline 422 unguarded
  writes). Suite 1886.
- **P1 hardening** (`fix/audit-p1-hardening`): dependency CVEs cleared (Spring Boot
  3.5.14, Tomcat 10.1.55 → Trivy 0 HIGH/CRIT), account lockout + strong passwords,
  N+1 query fixes, `V225/227/228/229/230` index programs (org-leading indexes on 67
  tenant tables, login covering index), lazy i18n + route splitting, Vitest runner
  bootstrapped, **32 SEC-Medium findings** fixed in one workflow pass (BFLA guards,
  mass-assignment create-guards, SoD actor-from-principal, SSRF constraints, public
  portal throttle, Splunk HMAC fail-closed) — **ratchet 422 → 61**.
- **Waves C/D/E**: accessibility contrast retokening (axe 68→13 nodes), UX copy,
  Lighthouse items; ALL performance findings (IO out of transactions, EAGER→LAZY with
  entity graphs, pagination clamps global + per-endpoint ×65, vendor chunk splitting);
  QUAL structural work — god-class splits (CyberService → 11 services, BuyerGapService
  → 3, DataOfficerService 1069 LOC → 4 + support), dead code, long-method
  decompositions, N+1s, plus 3 latent board-pack crashes found and fixed.
- **Final disposition:** the safely-automatable backlog was exhausted; the residual tail
  was explicitly dispositioned — TI-001 outsourcing-module merge (needs a product
  decision + data migration), DUP-001 (BaseEntity unification = a ~200-table schema
  migration, not a refactor), DUP-002 (609 TSX visual clones need visual review),
  FA-006 (cross-module facades) — all deferred by decision, documented, not forgotten.
  Suite 1955.

### 7.4 Module remediation programs (each: review plan → epics → per-epic verified commits → explicit merge)

| Program | When | Scope shipped | Migrations | Merged at |
|---|---|---|---|---|
| **Compliance/Policy** | 2026-06-28 → 07-02 | C-1..C-12: real policy file upload/versioning, evidence attachments + SLA, scheduler suite (fixed the app-wide off-request audit bug), obligation forms, policy SoD, two-policy-systems merge (Option A, 17 rows migrated), audit evidence-package, FK control links, T-24 CAPA reopen | V231–V234 | `a21da1a5` |
| **BCM** | 2026-06-29 → 06-30 | B-A..B-I: BCP generation from BIA, generic evidence-requests + attachments on 6 entities, rejected-evidence→gap (F8), observation→gap promotion, DR validation checklists, exercise-run list, BCM grant matrix + `BCM_APPROVE`; residuals: approval-gated reopens, committee FK validation, request comment threads, board-pack evidence tile | V235–V240 | PR #487/#488, `9df5c265` |
| **ERM/Risk** | 2026-07-01 | E-A..E-K: acceptance workflow, control→residual math, evidence wiring, treatment consolidation + close gate, appetite↔register + breach escalation, typed linkage, multi-dim impact, scheduled review + gated archive, committee/policy linkage, reporting + audit package; **E-E**: legacy KRI engine deleted, canonical `risk/kri` single source (data-migrated, legacy tables dropped) | V241–V247 | `2c3877b5` |
| **Sustainability/ESG** | 2026-07-02 → 07-04 | ALL 18 S-epics: RBAC (`SUST_*`), data-model fixes, settings wiring, audit logging, real evidence pipeline, auto-CAPA loop, CMA/TCFD SoD + content-lock + official library seed, climate scenario loss engine, ERM link validation, GHG hardening (EF library, unit registry, CSV import, verify gate), persisted/locked/exportable IFRS S2 report, live annex + lock + exports, DTO validation layer (mass-assignment closed), API namespace unification, vendor ESG surveillance, frontend truth (live dashboards), board-pack ESG section + notifications, CSRD double materiality; re-verified live 24/24 on 2026-07-07 | V248–V257 | `868eb6e6` |
| **Cyber + SOCOps** | 2026-07-04 → 07-06 | 15/15 epics (CY-A..CY-J, SOC-A..SOC-E): core finding was cyber evidence/policy forms as façade islands → rewired to the real platform subsystems; forensics custody ledger (append-only), threat-intel action links + MITRE library, evidence expiry sweep → STALE + auto re-request, workspace-incident backfill (V261: 24 incidents linked) | V259–V268 | `0c244f65` |
| **Workflow engine** | 2026-07-06 → 07-07 | WF-1..WF-10: engine core, SLA/reminders/escalation, FE selector+pages, evidence two-way bridge, hardening (committee fan-out), conditional routing, EXTERNAL tokenized delivery + public tracker, distribution lists + suggestions, QUORUM/MAJORITY + approval gate + DRAFT edit, multi-tier escalation ladders; verdict **MARKET-READY**, §7 matrix 20/20; then the **adoption program**: all 14 modules raise AND see live engine requests (mock panels retired, record-scoped detail panels) | V269–V276 (V272 = RBAC catalogue 34→66 perms + 55-persona matrix) | `8421652e`, polish `67ba1ea4` |
| **Forms/screens** | 2026-07-07 | GRCStructuredDrawer + extracted selectors, submission↔engine reconciliation, §9 domain request forms (KRI breach / patch / containment / data-sharing / processor review), governance-pivot new-tab auth bug fixed, anchored buttons on Finding/CAPA | V277/V278 | `e2227397` |
| **Export quality** | 2026-07-08 → 07-09 | P0–P3 + phase 5: BCM ZIP guard, CSV formula-injection guards, configurable + **bundled Amiri** Arabic font, real RTL run-direction everywhere, shared XlsxStyler, ExportNaming, export audit trail + Export History page, export-options modal, **BrandTheme white-label** (V279), regulator/audit package ZIP with SHA-256 manifest + embedded evidence binaries, ReportMetadata blocks, `.txt`→branded-PDF conversions (8 producers), packet XLSX/DOCX endpoints, DoS caps on tabular export, executive dashboard | V279 | `3baceb8e` |

### 7.5 The two design plans (2026-07-09, merged `98d92de0`)

1. **`RBAC_ACCESS_GOVERNANCE_MODULE_PLAN.md`** — requested as: *"design based on each
   module RBAC and make a module to manage these roles and permissions and access
   controls and rights for every module and every role… primary target Saudi markets…
   as GRC subject matter expert"*. A govern-don't-rebuild design over the existing
   evaluator: 24-module × persona-cluster least-privilege matrix, maker-checker,
   SoD pack, recertification, break-glass, evidence ZIP, analytics; anchored to NCA
   ECC 2-2, SAMA CSF 3.3.5, PDPL; phases P0–P3, migrations V280–V284 reserved.
2. **`SUSTAINABILITY_GREEN_FINANCE_REVIEW_AND_UPLIFT_PLAN.md`** — requested in Arabic as
   a dual-expert (sustainability + Saudi GRC) review including *"الصكوك الخضراء
   وحسابها والمحفظة الخضراء"* (Green Sukuk, their calculations, and the green
   portfolio). Review found the as-built stronger than assumed (PCAF financed emissions
   + materiality existed); the uplift: REG-1..11 register, Green Sukuk/UoP math,
   portfolio/GAR, targets/trajectory, IFRS S1/S2, NCEC, CBAM; phases G0–G4,
   migrations V285–V292 reserved.

### 7.6 Dual implementation loop (2026-07-09 → 07-11, alternating verified slices)

Run as one self-paced `/loop` alternating between two branches; house rules: commit per
verified slice, no merge without an explicit "merge", ratchet ≤62, `[REVIEW-TEST]`
tagging with CHARINDEX cleanup, destructive DB ops need explicit OK.

**Green finance — `feat/esg-green-finance-impl` (G0–G4, V285–V292):**
- **G0** `3702c827` — REG-1..11 as 12 bilingual compliance obligations (V285) +
  `esg_metric_reading` seed closure (26 defs / 0 readings defect fixed).
- **G1** `1f4b76a9` — Green Sukuk/bond/loan register + projects + use-of-proceeds
  allocations (V286): netProceeds = amount − costs; over-allocation hard block including
  PENDING proposals; proposer ≠ approver 409; Shariah gate (sukuk needs
  `shariahApprovalRef` to pass SHARIAH_REVIEW); lifecycle
  DRAFT→SHARIAH_REVIEW→APPROVED→ISSUED→REPORTING→MATURED; `SUST_APPROVE` catalogued
  (perms 69→70). Pure-math unit tests (`GreenFinanceMathTest`).
- **G2** `5344066e` — impact attribution (projectShare capped at 1, attributedImpact,
  intensity), allocation reports with frozen figures + maker-checker publish, and the
  branded Green-Finance evidence ZIP (README.pdf AR/EN + 3 XLSX + SHA-256 manifest).
- **G3** `c8a30e13` — green portfolio: taxonomy entries (ICMA/CCE 10-row global seed),
  asset tagging with SoD, **GAR** = eligible/assessed exposure, portfolio mix, weighted
  data-quality over latest-year financed emissions (V287); carbon credits
  purchase/retire with certificate + purpose (V288).
- **G4** `58343b97` — ESG targets & trajectory (V289: linear baseline→target
  interpolation, 5% direction-aware tolerance band, OFF_TRACK sweep → idempotent HIGH
  auto-CAPA; `ck_ca_source` extended with ESG_TARGET + ENV_PERMIT after a live 500);
  IFRS S1/S2 checklist (V290: 14 seeded bilingual requirements, coveragePct with
  PARTIAL = half); NCEC permits (V291: expiry sweep flips EXPIRED, CRITICAL CAPA when
  past due); EU CBAM lines (V292: total = volume × tCO₂e/tonne, byGroup summary).
  **Final probe 28/28.**

**RBAC governance — `feat/rbac-governance-impl` (P0–P3, V280–V284):**
- **P0** `ce3c5925` + `f092bc65` — V280 (user_roles lifecycle columns + IAM catalogue),
  role CRUD with revive pattern (soft-deleted name vs unfiltered unique index),
  permission-matrix API with structural rules + before/after audit, effective-access
  view; React **Access-Governance console** (`/access-governance`).
- **P1** `38f33112` — V281 access requests: maker-checker (approver ≠ requester ≠
  beneficiary), provision-on-approve **native-insert-first then single entity save**
  (the ROWVERSION double-flush landmine), time-bound assignments, hourly expiry sweep,
  JML LEAVER revoke-all.
- **P2** `eb162dea` — V282 recertification campaigns (Hijri-stamped windows, native
  snapshot generation, REVOKE auto-executes, self-review 409, exception closure) +
  V283 SoD engine (8-rule pack, PAIR + STRUCTURAL detection SQL, PLATFORM_ADMIN
  excluded, nightly 02:30 scan, preventive 409 + GRC_ADMINISTRATOR override recorded as
  OVERRIDE violations, dispositions with mandatory acceptance expiry) + the access
  evidence ZIP. Also made `displayNameAr` mandatory (bilingual platform policy; was a
  500, now a clean 400). **Probe 33/33** — the detective scan found **4 real conflicts
  in the dev org** (e.g. `rmanager` and `compliance` hold RISK WRITE+APPROVE), left
  OPEN deliberately as genuine findings.
- **P3** `48b0971b` — V284 break-glass (EMERGENCY_RESPONDER global template +
  `IAM_BREAK_GLASS` gate + `is_privileged` flag seed), access analytics endpoint,
  **EXPORT 6th action** (70→94 perms, READ-parity; gating proven live by revoking
  `IAM_EXPORT` → ZIP 403 → restore → 200). **Probe 29/29.**

### 7.7 Merge + cloud deployment preparation (2026-07-11)

User chose **"merge both, deploy master"** (Windows Server / restore dev DB / IP-only
HTTP):
- Both branches merged to master (`25006609`), combined suite **2248/0/0**, ratchet 62;
  pushed with both branch refs.
- `deploy/` staged: jar + `ui-dist/` (Vite production build) + verified DB backup +
  nginx.conf + launcher; **`DEPLOYMENT.md`** runbook committed (`f1de27a6`).

### 7.8 External configuration + first-ever prod-profile boot (2026-07-11, `d176dc90`)

User asked for a fully external config file. Implemented
`spring.config.import=optional:file:./external.properties` + parameterized every
prod-pinned key — then **actually booted the prod profile for the first time**, which
surfaced and fixed four real go-live blockers:

1. **Flyway checked the empty `dbo` schema** → tried to re-run V1/V2 on a restored DB;
   fixed with `FLYWAY_DEFAULT_SCHEMA=platform` (baseline at 292 then verified
   "No migration necessary" on restart).
2. **Prod-only circular dependency** — `TenantWriteGuardInterceptor` (EMF-wired, prod
   flag) field-injected `AuditLogService` → JPA repo → EMF cycle; fixed with a lazy
   `ObjectProvider` resolved at block-time.
3. **`ddl-auto=validate` false drift** — legacy tables use `smallint` for `is_deleted`
   where entities map `bit`; prod now defaults `none` like dev (re-enable via
   `HIBERNATE_DDL_AUTO` after type reconciliation).
4. **Health stuck DOWN** — auto-configured LDAP (`localhost:389`) and SMTP health
   indicators; disabled by default (`LDAP_HEALTH_ENABLED`/`MAIL_HEALTH_ENABLED`).

Final verification with a **completely clean environment** (file-only config): health
`UP`, admin login OK, governance + analytics APIs 200, Flyway baseline honored across
restarts. Suite 2248/0/0. Deploy folder refreshed; runbook rewritten around the
file-based model; dev DB test artifacts removed.

---

## 8. Verification discipline (how every slice was proven)

- **Full suite** on every slice: `mvn clean package` — grew 1886 → 1904 → 1954/55 →
  1970 → 2037 → 2099 → 2164 → 2193 → 2216 → 2242 → **2248 tests, 0 failures** — always
  read the log for `BUILD SUCCESS` / `Tests run:` (a background wrapper's exit code can
  mask a Maven failure).
- **Authorization ratchet** — unguarded write endpoints pinned at **62**; any new write
  endpoint must carry a real `hasPermission` guard.
- **Live probes** — every feature slice verified against the running stack with Python
  urllib + pyodbc harnesses: happy paths, guard 401/403s, SoD 409s, idempotency
  (sweeps re-run to 0), math spot-checks (e.g. expected 70,000 @ 2026 for a
  100k→50k 2020→2030 target), ZIP manifests re-hashed. Test rows tagged
  `[REVIEW-TEST]` and cleaned with `CHARINDEX` (never `LIKE '[...]%'` — bracket = SQL
  character class).
- **Migrations** applied to dev **twice** (idempotency proof) via pyodbc before commit.
- **Frontend** — `tsc --noEmit` clean + Vite build + Vitest; browser verification via
  programmatic auth when headless form-login is flaky.

---

## 9. Landmines & lessons learned (accumulated — read before touching)

1. **JAVA_HOME trap** — fresh shells default to JDK 11; always
   `export JAVA_HOME="/c/Program Files/Java/jdk-17"` or Maven fails with
   "release 17 not supported".
2. **Jar lock** — stop the running backend (kill by port 8084) before `mvn clean`.
3. **ROWVERSION double-flush** — never save an entity twice around a native
   `@Modifying` query on the same table; run the native first, mutate + save ONCE.
4. **Off-request audit** — `AuditLogService` guards `RequestContextHolder.
   getRequestAttributes()`; scheduled/async code must be org-explicit
   (`TenantContext` isn't set off-request).
5. **`ck_ca_source` CHECK** — every new auto-CAPA `sourceType` must be added to the
   whitelist or inserts 500.
6. **Soft-delete vs unfiltered unique index** — `iam.roles` unique name has no
   `is_deleted` filter while JPA can't see retired rows → use the native revive pattern.
7. **`@ManyToMany` join tables ignore soft-delete** — revocation must hard-delete
   `iam.user_roles` rows; history lives in `access_requests` + audit log.
8. **`@Valid` fires before `@PreAuthorize`** — write-guard probes need a valid body or
   you measure 400, not 403.
9. **Nested Spring Data repositories are not scanned** — always top-level interfaces.
10. **Returning JPA entities with lazy collections** 500s outside the session — use DTOs
    (RoleSummary pattern).
11. **`[REVIEW-TEST]` in `LIKE`** is a character class — match literals with `CHARINDEX`.
12. **`seed_real_data.py` `run()`** fetches `@@ROWCOUNT` — every `IF NOT EXISTS…INSERT`
    batch must start `SET NOCOUNT ON;`.
13. **Reboot port-steal** — default MSSQLSERVER grabs 1433; keep it Manual, start
    `MSSQL$SQLEXPRESS`.
14. **Flyway on a restored DB** — default schema must be a populated one
    (`FLYWAY_DEFAULT_SCHEMA=platform`) + baseline at the current head, or it re-runs
    history.
15. **Prod-only bean graphs differ** — flags like the tenant-write guard change wiring;
    a profile that has never booted hides cycles (lazy `ObjectProvider` broke this one).
16. **Optional-infrastructure health indicators** (LDAP/SMTP) flip liveness DOWN when
    their servers don't exist — disable until wired.
17. **`workflow.requests` deletes** need children cleared first (`request_events`,
    `request_recipients`).
18. **Verify a table is wired to an entity/endpoint before "seeding an empty table"** —
    row-count 0 can mean dead/legacy (`privacy_lc` ropa/dpia are orphaned; the live
    pages use the `privacy` schema).
19. **Vite HMR ToastProvider error** after CSS edits is a stale-module artifact — full
    reload, not a bug.
20. **External-config precedence** — UPPER_CASE placeholder names always win; raw Spring
    keys lose to profile-pinned values (the import is declared at base level).

---

## 10. Current state & open items (2026-07-11)

**State:** master `d176dc90` == origin/master; local branches `feat/esg-green-finance-impl`
+ `feat/rbac-governance-impl` merged and pushed (refs kept). Suite **2248/0/0**, ratchet
**62**. Migrations **V1–V292** packaged; next free **V293**. Dev DB has everything through
V292 applied. Local stack: backend :8084 (merged jar, dev profile) + Vite :5173.
`deploy/` holds the verified cloud artifact set.

**Open items / backlog:**
- **Cloud go-live execution** — the server-side steps of `DEPLOYMENT.md` (restore,
  services, firewall, smoke tests) + immediately rotate all seeded passwords and decide
  on demo tenants 2–3; TLS at nginx when a domain/cert exists.
- **Access-Governance frontend tail** — the console covers P0 scope (roles/matrix/
  effective access); SoD, campaigns, break-glass and analytics screens are natural
  follow-ups (APIs complete).
- **4 real SoD violations** are OPEN in dev org 1 (deliberate detective findings) —
  disposition or remediate as a demo exercise.
- **`smallint`/`bit` is_deleted reconciliation** — would allow re-enabling
  `HIBERNATE_DDL_AUTO=validate` in prod.
- **No platform SMTP/outbox** — notifications are in-app; wire a relay via
  `external.properties` when available (then flip `MAIL_HEALTH_ENABLED`).
- **Deferred audit structural tail** (by explicit decision): TI-001 outsourcing-module
  merge (product decision + data migration), DUP-001 BaseEntity unification (~200-table
  schema migration), DUP-002 frontend clone dedup (visual review), FA-006 cross-module
  facades; plus per-endpoint `@Valid` DTO conversion (exploit vector already closed by
  create-guards).
- Minor: FE button for the regulator package endpoint; NDI ad-hoc export live-verify
  (module data not bootstrapped in dev); optional C-12 legacy column drop; CMA Arabic
  term alignment (`الأهمية النسبية المزدوجة`) inside the regulatory-quote text.

---

*Companion documents: `DEPLOYMENT.md` (go-live runbook) · `LOCAL_SETUP.md` (dev machine)
· `reports/audit-report.html` (the audit) · per-program `*_PLAN.md` files (each carries
its own implementation-status tracker) · `CLAUDE.md` (working conventions).*
