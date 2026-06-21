# TSS GRC Platform — Complete Build History

A detailed catalogue of **everything built** in this repository: every module, the
sprint-by-sprint feature arcs, the forms / evidence infrastructure, the design-system
and visualization work, the accessibility pass, the large "surface-every-orphaned-
capability" campaign, and finally this session's deployment / LAN-access work.

- **Repo:** `Ayman-Server/Claude-GRC-2`
- **History size:** 419 commits on `master`, PRs **#148 → #485**
- **Final `master`:** `3ca91bf`
- **Earlier history:** V1 (auth, IAM, risk, incident, core modules, sprints 1–6) was
  imported as the baseline from the `claude/quirky-borg` branch — the tracked git
  history begins ~2026-05-23 at PR #148.

---

## PART I — What the platform is

**TSS GRC Platform** — bilingual (Arabic-primary / English), multi-tenant, enterprise
Governance-Risk-Compliance application for Saudi Arabia / MENA banking & enterprise.

| Tier | Technology | Dev port |
|------|-----------|---------|
| Frontend | React 18 + Vite 5 + TypeScript (`tss-grc-ui-v2/`) — also a legacy static UI (`tss-grc-ui/`) | 5173 |
| Backend | Spring Boot 3.5, Java 17, Spring Security 6, JJWT, Hibernate 6, Flyway, Caffeine, Apache POI, OpenPDF | 8084 |
| Database | SQL Server 2019 Express, UTF-8 collation, DB `TSS_GRC` | 1433 |

**Cross-cutting patterns:** multi-tenancy via thread-local `TenantContext` (every query
org-scoped); stateless JWT (Bearer access + HttpOnly refresh cookie); method-level
`@PreAuthorize hasPermission(MODULE,ACTION)`; soft deletes (`is_deleted` + `@Where`);
bilingual data (`*En`/`*Ar` columns) and bilingual UI (`data-i18n` + `translations.js` /
`ar.ts`/`en.ts` + RTL); workflow state machines in the service layer.

**3 demo tenants:** TSS GRC (Saudi bank), Aramco Trading (energy), STC Bank (telco-banking).
**Seeded logins:** `admin/Admin@12345`, `riskmanager/Risk@12345`, `auditor/Audit@12345`.

---

## PART II — Functional modules created (by domain)

### Core GRC
- **Risk** — register, treatments, assessments, FAIR quantitative calculator (Monte Carlo),
  Key Risk Indicator (KRI) engine, Bow-Tie analysis (ISO 31010), Arabic parity columns,
  residual heat-map.
- **Compliance** — obligation register, periodic attestation, regulatory horizon scanner,
  obligation campaigns.
- **Continuous Control Monitoring (CCM)** — test engine, define/edit tests, live connector wiring.
- **Controls** — AI-assisted control-mapping suggestor, Control Library (assessments,
  risk links, citation mappings, objectives, review workflow).
- **Evidence** — full write lifecycle, segregation-of-duties (submitter ≠ approver),
  request-evidence-from-person with SLA reminders + escalation, evidence-readiness panels.
- **Incident / CAPA / Issues** — workflows, cross-module Issue tracker console.
- **IAM** — privileged-access recertification queue (JML), active-sessions security console.
- **Internal Audit** — annual plan + engagement workflow, audit universe (recompute residual,
  bulk import), create-engagement-from-universe, Audit Committee dashboard.
- **Governance** — Three Lines of Defense dashboard, Risk-Control-Finding pivot view,
  multi-step approval chains, platform-wide Audit Log console.

### Business Continuity Management (BCM) — the largest arc (Sprints 7, 9, 10)
- SAMA BCF clause-level mapping (47 clauses); NCA OTCC + ISO 22301 + NCEMA/NRC plan data.
- Board Pack BCM section auto-generator; annual-cycle automation scheduler; Saudi regulator
  countdown timers.
- Dependency-cascade simulator; DR-test trending (rolling quarters, pass-rate trend);
  quantitative Business Impact Analysis; continuity-strategy ROI analysis.
- Exercise scenario library + scenario→exercise wizard; inject-driven exercise runtime +
  facilitator console; Observer dashboard for live exercises.
- Mobile-optimised Crisis Room + Web-Push registry; Crisis Comms template expansion.
- Offline BCP/DRP binder PDF generator; vendor MTPD inheritance check.
- BCM readiness register, risk register, governance, call tree, BCP/DRP section builder,
  management-reviews register (ISO 22301 §9.3), SIEM→BCM crisis bridge.

### Cyber / SOC
- Threat-Intelligence correlation service + IOC console; Attack Surface Management register
  (shadow-IT discovery, sanction/block); Risk-Based Vulnerability SLA engine (mark-remediated);
  NCA CSCC cloud-service inventory console; SOC Operations module + seed; forensics
  chain-of-custody (integrity verify).

### Privacy / PDPL / NDI / Data Officer
- PDPL data-flow visualization (graph) + cross-border residency.
- PDPL sub-processor tracking register (material-change); SDAIA breach-notification workflow
  (PDPL Art. 31); privacy notices (supersede), consent, DSR.
- **Sprint 33 — Data Officer (10 modules):** DSR, Consent, Breach, DPIA, Notice, Retention,
  NDR, Catalog, Quality, Ethics — each with backend state machines, register pages, a Data
  Officer dashboard, schedule-disposal + record-quality-run actions.
- **NDMO Data Classification + PDPL** (Sprint 32): assets, flows, classification.
- **NDI (National Data Index)** governance: domains, framework library, reports, operational
  excellence KPIs, cycles, compliance responses, settings, evidence (supersede) — 8 SDAIA-
  critical surfaces deep-linked.
- **Cyber → PDPL bridge** — manual trigger that links a cyber incident to a data-breach record.

### ESG
- TCFD Climate Scenarios + Scope-3 **Financed Emissions** (PCAF); GHG emission inventory
  (edit, archive); ESG metrics, materiality matrix, SGI commitments, CMA Article disclosures
  (approve), TCFD alignment (link climate risk), Annex reporting-year (advance), Climate
  Risks & Opportunities, Strategy ROI; bilingual ESG SME catalog.

### Third-Party / Outsourcing (TPRM)
- SAMA Outsourcing & Cloud Governance (arrangements, cloud-service SAMA classify),
  monitoring events (record/resolve), due diligence, exit plans + exit tests; vendor BCP
  attestation campaigns + record-submission; questionnaire builder MVP; contract clause library.

### Financial-crime & prudential
- **Fraud Risk Management** (cases, intake wizard, automation rules, seed).
- **AML / Sanctions Screening** engine + hit triage + pending-hits queue + watchlist
  entries/bulk import; KYC manual screening-hit capture.
- **Sharia Governance & Audit Workflow** (5 register pages).
- **SAMA Open Banking Compliance** (TPPs, consents/grant-consent, incidents).
- **Counterparty Credit Rating Registry** (ratings, limits).
- **Recovery & Resolution Plan (RRP)** (cycles, options, critical functions, indicators).
- **SAMA Operational Resilience 2024** self-assessment (dashboard, register, cell-editor wizard).
- **Customer Complaints Management**; **Conflict of Interest** register (recusal log).
- **Ethics / Whistleblower** hotline + retaliation-protection register.

### Regulatory
- **Regulatory Inspection Workspace** (inspections + document requests).
- **Mandatory Regulatory Reporting Calendar** (obligations, filings, generate-filing).
- **Regulatory Library** (authority documents register/edit/lifecycle, citations add/edit,
  control→citation mapping); NCA ECC annual filings register (ECC-2:2024).

### AI Governance
- AI/ML Model Registry — structured human oversight, PDPL data residency, NCA control
  mapping, AI Impact Assessment, evidence readiness, posture KPI tiles, regulator-ready
  XLSX + branded PDF export, scheduled AI pack, drift-observation, AI evidence-quality check,
  AI RTO/RPO recommendation; **AI/ML Model Lifecycle** (Sprint 31 — validations, retrainings,
  recertifications).

### Committees & board governance
- Committees, charter, schedule-meeting, resolutions, member registry (appoint, renew/end term,
  skills matrix), board-papers pre-read tracking (V154), meeting-paper circulation, action register.

### Integrations / connectors
- ServiceNow ITSM, Splunk SIEM, CrowdStrike Falcon EDR, Tenable VM bridge adapters; live
  connectors page wired to CCM; adapter action & audit console; outbound test-push.

### Platform
- Embedded contextual help; document-generation engines (PDF/DOCX/XLSX/PPTX); Reports Centre
  (all exports incl. orphaned Word); e-signature / sign-off facility; workspace customizer
  (font-size + quick-colors); JWT rotation, refresh-token revocation, DB-size monitor, MFA lib.

---

## PART III — Forms & evidence infrastructure (2026-06-01 wave)

A platform-wide capability build:

- **Generic typed file-attachment facility** (`AttachmentsField`) — drag-and-drop, multi-file
  queue, client-side validation, per-file title + doc-type, "valid until" expiry tracking,
  edit-metadata PATCH, versioning + history, chain-of-custody upload dates, loading/delete states.
- **Cross-framework control mapping** (`ControlMappingPicker`) — optional manual mapping with
  per-link satisfaction levels (FULL/PARTIAL/PLANNED).
- **Reusable `EntityDocsModal` + "Docs & controls" row-action** rolled out to **51 surfaces**
  (30 detail pages + 21 registers) across every module.
- **Evidence readiness** — universal: every control-mapped register gets recommended-evidence
  coverage + raise-request via the shared picker; reusable `EvidenceReadinessPanel`.
- **GRC Expert** — bilingual SME catalogs for NCA / NDI / PDPL / BCM / ESG / ERM / AI
  Governance (deep sub-control + recommended-evidence, EN + AR), wired into the evidence lifecycle.
- **E-signature** — in-house sign-off mounted on DPIA, Finding, Vendor details.
- **Segregation of duties** — submitter cannot approve own evidence (BusinessRuleException 403).

---

## PART IV — Design system, visualization & accessibility (2026-06-02 sweep)

### Visualization (playbook Phases A–E)
Design-system foundation + hero charts; SDAIA 72-hour breach war-room timer; BCM readiness
rings; KSA data-residency map; vendor risk×criticality bubble quadrant; DSR-SLA countdown
rings; framework-coverage ring cluster; evidence-freshness calendar; AI model drift chart
with threshold band; SAMA materiality×criticality matrix; risk heat-map legend.
Primitives: `RadialGauge`, `RiskMatrix5x5`, `CountdownRing`, `ResidencyMap`, `BubbleQuadrant`,
`FreshnessCalendar`, control-crosswalk Sankey.

### UI consolidation & design tokens (~50 PRs, #287–#334)
One `PillTabs`, one `Field` renderer, one chip system, one gauge size system (GAUGE_MD/HERO),
one RAG breakpoint set, tabular-nums on numerics, `tlabel()` crash-proof enum-label lookups
(full sweep), repaired undefined card/ink/surface/spacing/shadow/semantic tokens across 27+
Sprint-30 pages, styled ~30 raw `.grc-table` pages, token-driven dark-safe chart colours,
typography tokens (headline numbers, clamp, tracking, line-heights, radii), form rhythm,
detail-header back-affordance + eyebrow, table toolbar/row/state polish, dashboard widget grids.

### Accessibility
Skip-to-content link + main landmark; breadcrumb `aria-current`; `aria-invalid` + inline
validation on Input/Select/Textarea and in FormModal.

---

## PART V — The "surface every orphaned capability" campaign (#346 → #482)

The dominant late arc. Many backend write-capabilities had no UI affordance ("orphans"). The
campaign added a bilingual (AR+EN) UI for each — buttons, FormModals, transition controls,
consoles — one verified, merged PR per capability, each E2E-tested in both languages with a
DB-confirmed mutation, a negative test, and a pristine demo-DB restore.

**Consoles that surfaced whole orphaned backends (#346–#357):** platform Audit Log, cross-module
Issue tracker, regulatory horizon scanner, NCA CSCC cloud inventory, FAIR risk calculator,
active-sessions security, threat-intel IOC correlation, dependency-cascade simulator, PDPL
data-flow graph, DR-test pass-rate trend, AI-assisted control mapper, whistleblower retaliation.

**Per-action affordances (#360–#482)** — surfaced create / edit / transition / action endpoints
across: Reports Centre, IAM recertification, SDAIA breach workflow, BCM management-reviews,
NCA ECC filings, SIEM→BCM bridge, sanctions list, connector adapters, IA universe & engagements,
outsourcing classify/events/DD/exit-plans/arrangements, forensics integrity, committee
papers/resolutions/meetings/members/charter/actions/skills, Data Officer workflow transitions
& operations, reg-reporting filings, vuln mark-remediated, sub-processor material-change,
CoI recusal, sanctions pending-hits, AI drift/quality/RTO-RPO, M&A phases, ASM sanction/block/
discovery/findings, Open Banking grant-consent, ERM appetite/incident-bump, CCM define/edit-test,
BCM submit-plan / risks / governance / call-tree / sections / DR-plans / strategy-ROI,
NDI domains / framework / reports / OpEx / cycles / compliance / settings / evidence,
Regulatory Library docs & citations, Control Library assessments / risk-links / mappings /
objectives, ESG metrics / materiality / SGI / CMA / TCFD / financed-emissions / GHG / annex /
climate-R&O, RCA edit, risk treatments & assessments, policy attestations, contract clauses,
privacy notices (supersede), Cyber→PDPL bridge manual trigger. *(See the 200-item task list
for the complete per-action enumeration.)*

**Deliberately deferred (validated as low-value / unsafe):** `archiveTemplate` (dev table
absent), `deleteClimateItem` (hard delete; app convention is no UI delete), `reorderItems`
(redundant with `sortOrder`), `startRun` (no clean launch surface). After the final orphan
(#482, the Cyber→PDPL bridge), the genuine-orphan backlog was declared **exhausted**.

**Data-quality fixes along the way:** repaired UTF-8 double-encoding (mojibake) in seeded cells
(117 + others); corrected TSS tenant name (الخدمات الموثوقة للأمن السيبراني / "Trusted Security
Solutions").

---

## PART VI — This session (2026-06-18): run, fix login, deploy on LAN, document

1. **Verified GitHub `master` was current** (was `fdc5f2b`, clean, zero open PRs).
2. **Started the stack** — frontend up; found backend down + SQL Server (SQLEXPRESS) Stopped
   (needs admin to start). User started SQL; started the backend (prebuilt jar + env vars).
3. **LAN binding fix** — Vite bound to localhost only. Set `server.host=true` / `allowedHosts`.
   Gotcha: `vite.config.js` (gitignored, compiled) shadows `vite.config.ts`, so the `.js` had
   to be edited too. Network URL `http://192.168.8.243:5173` then appeared. Firewall was off.
4. **"Server stopped"** — only Vite; it was a child of the Preview tool session. Relaunched it
   detached.
5. **Login bug (the headline fix)** — server-side `curl` login worked, browser didn't. Root
   cause: the dev CORS allowlist had only `localhost`/`127.0.0.1`; the browser's
   `Origin: http://192.168.8.243:5173` was rejected by Spring's CORS filter with **403
   "Invalid CORS request"** before login ran. Fixed immediately by restarting the backend with
   the LAN origin added via `--grc.cors.allowed-origins=…`. Verified login over the LAN IP → 200.
6. **"Restart the application"** — relaunched backend + Vite as **independent Windows processes**
   (`Start-Process`) so they survive tool-session cycles. Re-verified.
7. **Committed the fixes — [PR #484](https://github.com/Ayman-Server/Claude-GRC-2/pull/484)** (master `d611b56`):
   `vite.config.ts` host binding (verified live); `CorsConfig.java`
   `setAllowedOrigins`→`setAllowedOriginPatterns`; `application-dev.yml` private-LAN wildcard
   origins (`192.168.*` / `10.*` / `172.*`). Disclosed: backend not rebuilt in-session (no Maven
   on host) — backward-compatible, effective on next `mvn package`.
8. **Setup guide — [PR #485](https://github.com/Ayman-Server/Claude-GRC-2/pull/485)** (master `3ca91bf`):
   added [`LOCAL_SETUP.md`](LOCAL_SETUP.md) — full reproduce-on-another-machine guide.

### Key technical learnings
- `vite.config.js` shadows `vite.config.ts` if both exist.
- Spring's CORS filter rejects a disallowed `Origin` server-side (403) before the controller —
  `curl` without `Origin` won't reveal it.
- `allowedOriginPatterns` permits wildcards *with* credentials; plain entries still match exactly.
- Preview-tool / `nohup &` processes die on session cycles; `Start-Process` is independent.
- Spring property override (`--grc.cors.allowed-origins=…`) avoids a rebuild.
- Backend refuses to boot without `JWT_SECRET` (≥32 chars, no placeholder) and `DB_PASSWORD`.

---

## PART VII — Sprint / arc ledger (PR ranges)

| Arc | PRs | What shipped |
|-----|-----|--------------|
| Sprint 7 — BCM Manager | #148–#160 | SAMA BCF mapping, board pack, annual-cycle scheduler, regulator countdowns, ops doc library |
| Sprint 8 — P0 GRC block | #161–#169 | FAIR calc, horizon scanner, PDPL data-flow, threat-intel correlation, control-mapping suggestor |
| Sprint 9/10 — BCM advanced | #170–#192 | cascade simulator, DR trending, BIA, bow-tie, exercise runtime, crisis room, binder PDF, vendor MTPD |
| Sprint 11 — module wave | #195–#208 | AI Model Registry, Obligation Register, CCM, TCFD+Financed Emissions, Audit Committee, ASM, Vuln-SLA, sub-processors, pivot view |
| Sprint 12 — integrations | #177/#180/#184/#188, #206–#207 | ServiceNow/Splunk/CrowdStrike/Tenable, embedded help, i18n pass |
| Sprint 13 — governance | #211–#221 | approval chains, KRI, IA plan+engagement, ethics hotline, sanctions, outsourcing register, CoI, complaints |
| Sprint 14 — Fraud + Op-Res | #222–#233 | Fraud Risk Management, SAMA Op-Res 2024 self-assessment |
| Sprint 15 — Reg Inspection | #234–#238 | inspection workspace + DRs |
| Sprint 16 — Reg Reporting | #239–#243 | mandatory reporting calendar |
| Sprint 17 — RRP | #245–#249 | Recovery & Resolution Plan |
| Sprint 18 — Counterparty | #250–#254 | credit rating registry |
| Sprint 19 — Open Banking | #255–#259 | SAMA Open Banking compliance |
| Sprint 20 — Sharia | #260–#264 | Sharia governance & audit |
| Sprint 30 — Outsourcing/Cloud | #265–#272 | SAMA outsourcing & cloud governance |
| Sprint 31 — AI Lifecycle | #268–#273 | AI/ML model lifecycle |
| Sprint 32 — Data Classification | #270/#274 | NDMO classification + PDPL |
| Sprint 33 — Data Officer | (task-driven) | 10 data-officer modules + dashboard |
| GRC Expert + SME catalogs | #276 + SME PRs | bilingual sub-control & evidence catalogs |
| Forms & evidence infra | ~#45dbd3e–#cf3b8e6 | attachments, control mapping, evidence readiness, e-sign, SoD |
| Dashboards real KPIs | #277–#278 | 12-week trend charts |
| Visualization | #279–#286, #338–#340 | hero charts + primitives + playbook deploys |
| UI consolidation / tokens | #287–#334 | one-control consolidations, token repairs, tlabel sweep |
| Accessibility | #335–#337 | skip-link, aria-current, aria-invalid |
| Consoles (orphan backends) | #346–#357 | 12 read/console surfaces |
| Orphan-action campaign | #360–#482 | ~120 per-action affordances across all modules |
| Background | #480, #483 | BCM Arabic i18n; workspace customizer |
| **This session** | **#484, #485** | **LAN access (Vite host + CORS patterns); LOCAL_SETUP.md** |

---

## PART VIII — Final state

| Item | Value |
|------|-------|
| `master` (local + origin) | `3ca91bf` |
| Open / unmerged PRs | none |
| Orphan backlog | exhausted (4 items deliberately deferred) |
| Frontend | Vite `:5173`, bound to all interfaces (independent process) |
| Backend | Spring Boot `:8084`, `{"status":"UP"}`, LAN CORS via launch arg (independent process) |
| SQL Server | SQLEXPRESS running `:1433` |
| Verified LAN URL | `http://192.168.8.243:5173` → `admin` / `Admin@12345` (HTTP 200 end-to-end) |

### Open follow-ups
- Rebuild the backend with Maven on a host that has it, to validate the committed CORS patterns
  (this host has no Maven; the running backend uses the launch-arg workaround).
- For durable hosting, build the frontend (`npm run build`) behind a real web server and/or run
  the backend as a Windows service — the Vite dev server is not a production host.
- This file and `SESSION_SUMMARY_2026-06-18.md` are **uncommitted** by default — commit if wanted.

---

## Reference docs in the repo
`CLAUDE.md` (commands, env, architecture, landmines) · `README.md` (public overview) ·
`STATUS.md` (module completion state) · `SESSION_SUMMARY.md` (V1 handoff) ·
`LOCAL_SETUP.md` (reproduce the dev stack) · `sprints/sprint-{1,2,3}.md`.
