# Sustainability / ESG Module Review and Remediation Plan

> **Module reviewed:** Sustainability / ESG (`module/sust`) + the standalone Climate stack (`/api/v1/climate/*`) + the ESG frontend (`tss-grc-ui-v2/src/pages/esg`, `lib/sustainability*.ts`, `lib/climate.ts`, `lib/esg.ts`)
> **Date:** 2026-07-02 · **Reviewer role:** Saudi GRC product architect · ESG/IFRS-S2/TCFD analyst · climate-risk & GHG-accounting specialist · CMA/SGI/SAMA reviewer · internal auditor · QA · full-stack reviewer · bilingual AR/EN UX reviewer
> **Repository:** `D:\AYMAN\GRC` — Spring Boot 3.5 (Java 17) backend + React 18/Vite/TS frontend, SQL Server, tenant `organisation_id = 1`, dev backend live on `http://localhost:8084`.
> **Method:** static code read (grounded in `file:line`), migration/DDL inspection, **four parallel review-only audit agents** (GHG+Annex, Climate+TCFD, SGI/CMA/Materiality/Metrics/Evidence/CAPA/Dashboard/Settings, Frontend+Integration+Tests), **live API smoke** against `:8084` (read-sweep of 29 endpoints + harsh input probes), and a **live RBAC DB check** (`iam.roles`, `iam.role_permissions`).
> **Scope discipline:** review + plan only. **No code was modified.** This document proposes what to keep, improve, add, remove, or defer — nothing is implemented yet.

---

## 0. Remediation Progress (to be filled during implementation — branch `feat/esg-remediation`)

| Epic | Item | Severity | Status | Commit |
|---|---|---|---|---|
| S-A | RBAC: SUST_READ/WRITE/DELETE perms + `SUSTAINABILITY_OFFICER` role + grant matrix (V248) + 3 climate controllers `hasAnyRole`→`hasPermission('SUST',…)` | 🔴 Critical | ✅ Shipped | V248 + climate ctrls |
| S-B | Wire `OrganisationSettings` to its consumers (dead config): GHG Scope-3 threshold, SGI at-risk factor, evidence expiry window + dynamic-cron sweep scheduler, dashboard bands, CAPA warning/escalation windows — all read from settings; changing a setting changes behaviour (unit + live-proven) | 🔴 Critical | ✅ Shipped | 5 consumer svcs + expiry scheduler |
| S-C | Real evidence pipeline: reuse `module/evidence` (upload real file + SHA-256 + review/approve/expire lifecycle) instead of the sust silo; ESG entities gain `evidence_item_id` FK (V250, 6 tables); `SUSTAINABILITY` added to evidence_links whitelist; bridge links real evidence + evidence-package endpoint bundles files+hashes (live-proven) | 🔴 Critical | ✅ Shipped | V250 + evidence bridge |
| S-D | Auto-CAPA / auto-gap closure loop: SGI behind-plan (DELAYED), CMA/TCFD PARTIAL·NOT_DISCLOSED, and rejected evidence each idempotently open a `sust.corrective_action` (deduped by source marker, mirrors compliance F8 `AR-EVD-GAP`); `CorrectiveActionService.autoOpen` + V251 adds `EVIDENCE` source_type; CMA/TCFD back-link `linkedCapaId` (unit + 13-check live smoke) | 🔴 Critical | ✅ Shipped | V251 + autoOpen in 4 svcs |
| S-E | Climate scenario loss engine: expected loss + portfolio-at-risk DERIVED from sector impacts (Σ exposure × PD-uplift/10000 × NGFS scenario factor [orderly 1.0 / disorderly 1.5 / hot-house 2.0]); free-typed loss fields removed from the request DTO (read-only, recomputed on create/update/add-sector); post-approval sector-add lock (409); config-driven probability-weight budget guard (`climateScenarioWeightsStrict`, V252) — strict rejects over-allocation (400), else soft warning (unit + 8-check live smoke) | 🔴 Critical | ✅ Shipped | V252 + TcfdScenarioService derive+guard |
| S-F | ClimateRisk ↔ ERM real, validated, bidirectional link: `linkErm` validates `ermRiskId` exists + same-tenant (reject 400, no more dangling id); creates a bidirectional typed `RiskLink` (new `CLIMATE` type, reuse E-G/E-I surface) on the ERM risk (idempotent, re-point cleanup, null unlinks); `TcfdItemService.linkClimateRisk`/`create` validate `relatedClimateRiskId` exists (reject 400) + reverse read `GET /tcfd/by-climate-risk/{id}` (unit + 10-check live smoke) | 🔴 Critical | ✅ Shipped | RiskLink CLIMATE + validated linkErm |
| S-G | Complete IFRS S2 / TCFD annual report (+ persist/approve/lock/export) | 🟠 High | ⬜ | — |
| S-H | Audit logging across the 11 unlogged sust services (GHG, SGI, CMA, TCFD-item, Materiality, Metrics, Evidence, CAPA, ClimateRisk, Report/Year, Settings) via `AuditLogService` — CREATE/UPDATE/TRANSITION/APPROVE/REJECT/ARCHIVE/DELETE; live-proven traceable history | 🟠 High | ✅ Shipped | 11 svcs + audit.log |
| S-I | Annex from live data + content lock/version + CSV/PDF | 🟠 High | ⬜ | — |
| S-J | GHG hardening: APPROVED edit-lock (update→409) + `reopen` forces re-verification (VERIFIED edit resets to SUBMITTED); versioned **emission-factor library** (`sust.emission_factor` V253, create-supersedes-prior + `resolveActive`) + **unit registry** (g/kg/t/kt/Mt) replacing the 2-branch heuristic; GHG records reference a library factor (`emission_factor_id`) → COMPUTED value via registry; `value_source` COMPUTED/ASSERTED flag; coverage relabeled emission-weighted (+basis key); **bulk CSV import** (T-06); evidence-item FK (S-C) satisfies the verify gate; real multi-year `trajectory` + YoY `priorYearHasData` (unit 26/26 + 11-check live smoke) | 🟠 High | ✅ Shipped | V253 + EF library/registry + GHG svc |
| S-K | CMA/TCFD approval SoD + item lock + official library import: `approve()` binds `@AuthenticationPrincipal` (non-forgeable `approvedBy`) + SoD (approver ≠ owner/reviewer); TCFD gains its first-ever `approve()` + `/{id}/approve`; APPROVED disclosures/items are content-locked (update/transition/attach/gap → 409); official CMA Art.91–94 (11) + TCFD 11-recommendation library auto-seeded per-tenant on reporting-year open (idempotent) | 🟠 High | ✅ Shipped | CMA/TCFD svc+ctrl + report-year seed |
| S-L | DTO + Jakarta validation layer (kill mass-assignment / 500-on-bad-enum) | 🟡 Medium | ⬜ | — |
| S-M | API namespace + auth-model unification | 🟡 Medium | ⬜ | — |
| S-N | Reporting-engine + notifications integration | 🟡 Medium | ⬜ | — |
| S-O | Vendor ESG surveillance — real backend + TPRM/SCM link | 🟠 High | ⬜ | — |
| S-P | Frontend: live KPIs (kill mock), routable tabs, PDF export, tests | 🟠 High | ⬜ | — |
| S-Q | Materiality: true double-materiality (impact vs financial axes) | 🟡 Medium | ⬜ | — |
| S-R | Data-model fixes: score `DECIMAL(4,2)`→`(5,2)`; unique `topic_code`/`metric_code`; `esg_metric_reading` history + endpoint; sector-impact FK `ON DELETE CASCADE` (V249) | 🟠 High | ✅ Shipped | V249 + metrics/climate |

---

## 1. Executive Summary

**This is not a fake module.** The Sustainability/ESG module is one of the more built-out areas of the platform: **~45 backend files across 12 sub-packages** (`ghg, sgi, cma, tcfd, climate, materiality, metrics, evidence, capa, annex, dashboard, settings`), **~140 REST endpoints**, **13 migrations** (V55–V65, V186), a **14-tab React dashboard** (`EsgPage.tsx`) plus three standalone climate pages, complete **EN/AR i18n with disciplined RTL**, and **88 backend unit tests**. Live smoke against `:8084` (org 1) confirmed genuinely computed data, not placeholders:

- **GHG aggregate is real** — Scope 1 = 14,220 / Scope 2 = 38,910 / Scope 3 = 142,480 / grand total = **195,610 tCO₂e**, Scope-3 coverage **68.82%** with the partial-flag correctly raised (below the 70% threshold).
- **CMA readiness 73%** (11 disclosures, 3 gaps), **TCFD alignment 73%** (strategy-resilience gap flagged), a real **materiality matrix** (1 critical / 3 high), a real **CAPA lifecycle** (9 open / 6 in-progress / 2 overdue), and a scenario **weighted-expected-loss of SAR 2,880.5 M** that even surfaced a data-integrity warning (probability weights sum to 2.0, not ~1.0).
- **Input validation works**: negative emissions → `400 "emission_value_tco2e must be >= 0"`, coverage 150 → `400 "must be 0..100"`, scope 9 → `400`, verify-without-evidence → `400 "requires evidence_ref (Rule 2)"`.

**But it is not market-ready for an audited Saudi ESG/climate buyer**, for a small set of *specific, high-impact* reasons:

1. **Nobody can actually use it.** The `SUST` permission is granted to **no role** (live-confirmed: no `SUST` authority anywhere in `iam.role_permissions`), and the climate pages are gated on roles that **don't exist** (`SUSTAINABILITY_OFFICER`, `CFO`, `BOARD_MEMBER` are absent from the 31 seeded roles). The entire module is **PLATFORM_ADMIN-only in practice**; the persona it was built for cannot log in with an appropriate role.
2. **The configurability is a facade.** `OrganisationSettings` exposes 8 tunable thresholds; **every one is hardcoded at its consumer** and the settings row is never read. Changing a setting changes nothing.
3. **The evidence backbone is a stub.** "Upload" stores client-supplied `fileUrl`/`fileName` strings — no `MultipartFile`, storage, or hashing; there is no request/SLA/reminder/escalation/resubmit flow, the expiry sweep isn't scheduled, and every `evidenceRef` is free text with no FK.
4. **The "close-the-loop" automation is narrative.** Behind-plan SGI commitments and PARTIAL/NOT_DISCLOSED CMA/TCFD items **never auto-create a gap or corrective action** — the linkage is seed prose.
5. **The headline analytics are partly theatre.** Portfolio climate exposure / expected loss is **free-typed, never calculated**; the ClimateRisk↔ERM link is a **dangling unvalidated id**; the IFRS S2 "annual report" **omits the entire TCFD checklist, GHG inventory, evidence index, and gaps** (live-confirmed); the annex generator **hardcodes** its SGI/CMA/TCFD sections; and the ESG landing-page KPIs are **hardcoded 2020–2025 demo constants**, not the live APIs the other tabs use.
6. **Audit-trail holes.** 11 of 14 services write **no audit log**; GHG records can be edited after approval; "immutable on approval" is unenforced.

**Verdict: Partially usable — needs targeted remediation.** The foundation (registers, calculators, lifecycles, aggregates, bilingual UI) is genuinely strong and largely correct; the blockers are a bounded set of integration, security, and "last-mile trust" gaps. With the S-A…S-R backlog below it becomes a credible Saudi ESG/climate-disclosure product.

---

## 2. Module Reviewed

| Item | Detail |
|---|---|
| Backend (Stack A — "Sustainability", V55–V65) | `module/sust/{ghg,sgi,cma,tcfd,materiality,metrics,evidence,capa,annex,dashboard,settings}` + `climate/ClimateRiskOpportunity`. Base path `/api/sustainability/*`, guard `hasPermission('SUST', …)`. |
| Backend (Stack B — "Climate", V186) | `module/sust/climate/{TcfdClimateScenario,TcfdSectorImpact,FinancedEmissionsRecord,FinancedEmissionsTarget}` + `TcfdScenarioService`, `FinancedEmissionsService`, `TcfdReportService`. Base path `/api/v1/climate/*`, guard `hasAnyRole('SUSTAINABILITY_OFFICER','CFO',…)`. |
| Frontend | `pages/esg/EsgPage.tsx` (14-tab dashboard), `pages/esg/{TcfdScenariosPage,FinancedEmissionsPage,ClimateAnnualReportPage}.tsx`; `lib/sustainability{Materiality,Tcfd,Cma,Sgi,Metrics,Evidence,Capa,Annex,Dashboard,Settings}.ts`, `lib/climateRisk.ts`, `lib/ghgInventory.ts`, `lib/climate.ts`, and the **mock** `lib/esg.ts`. |
| Migrations | V55 climate-risk · V56 GHG · V57 annex · V58 CAPA · V59 SGI · V60 CMA · V61 TCFD · V62 materiality · V63 metrics · V64 evidence · V65 settings · **V186** Scope-3/scenarios/financed-emissions. |
| Seed | `seed_gov_esg.py` (comprehensive — all sust tables, idempotent) is the real ESG seeder. `seed_real_data.py` seeds only `material_topic` + `climate_risk_opportunity`. No `setup_sust_db.py` exists. |
| Tests | 88 backend `@Test` methods (Mockito service-level) concentrated in the V186 climate stack + capa/sgi/cma/ghg/settings; **none** for materiality/metrics/evidence/annex/dashboard; **zero** frontend ESG tests. |

---

## 3. Full Real-World Sustainability Task List

Status legend: ✅ works · ◑ partial · ✗ missing/stub. (Detail in §6, §9–§17.)

| ID | Task | Role | Trigger | Evidence/approval | Status transitions | SLA/reminders | Current status |
|---|---|---|---|---|---|---|---|
| T-01 | Create reporting year | Sustainability mgr | Annual cycle | — | ACTIVE→DATA_COLLECTION→REVIEW→APPROVED→PUBLISHED→ARCHIVED | period | ✅ Works |
| T-02 | Create annual sustainability annex | Sustainability mgr | Year open | approval | DRAFT→SUBMITTED→APPROVED→PUBLISHED | — | ◑ Generates, but 3/5 sections hardcoded |
| T-03 | Add GHG Scope 1 record | GHG owner | Data avail | evidence_ref | DRAFT→SUBMITTED→VERIFIED→APPROVED | — | ✅ Works |
| T-04 | Add GHG Scope 2 record | GHG owner | Data avail | evidence_ref | same | — | ✅ Works |
| T-05 | Add GHG Scope 3 record | GHG owner | Data avail | evidence_ref | same | — | ✅ Works (asserted value, no activity) |
| T-06 | Import emissions data (bulk) | GHG owner | Data file | file | — | — | ✗ Missing (no import endpoint) |
| T-07 | Calculate total GHG emissions | System | On read | — | — | — | ✅ Works (real aggregate) |
| T-08 | Calculate annual trajectory (YoY) | System | On read | — | — | — | ◑ Endpoint real but returns 0 (no prior-year baseline) |
| T-09 | Verify emissions record | Verifier | Submitted | evidence gate | →VERIFIED | — | ✅ Works (evidence-gated) |
| T-10 | Request evidence for emissions | GHG owner | Gap | owner+due | OPEN→SUBMITTED→APPROVED/REJECTED | due→remind→escalate | ✗ Missing (no request entity) |
| T-11 | Upload emissions evidence | Dept owner | Request | the file | — | — | ✗ Stub (client-string URL, no MultipartFile) |
| T-12 | Reject / resubmit evidence | Reviewer | Upload | reason | REJECTED→resubmit | — | ◑ Reject exists; **no resubmit** |
| T-13 | Track Scope 3 coverage | System | On read | — | — | — | ✅ Works (partial-flag at 70%) |
| T-14 | Create SGI commitment | Sustainability mgr | New pledge | — | ACTIVE→ACHIEVED/RETIRED | milestone | ✅ Works |
| T-15 | Update SGI progress | SGI owner | Data | — | — | — | ✅ Works (direction-aware) |
| T-16 | Detect behind-plan commitment | System | Time elapsed | — | →AT_RISK/DELAYED | — | ◑ Detected; **no CAPA raised** |
| T-17 | Create corrective action for behind-plan | System/owner | Behind plan | — | OPEN→…→CLOSED | due+escalate | ✗ Manual only (no auto-create) |
| T-18 | Create CMA disclosure item | Compliance | CMA reg | evidence | 6-state matrix | — | ✅ Works |
| T-19 | Attach CMA disclosure evidence | Compliance | Disclosure | evidence | — | — | ◑ Free-text ref, no FK |
| T-20 | Mark CMA partial / not-disclosed | Compliance | Assessment | gap required | — | — | ✅ Rule 5 enforced |
| T-21 | Create gap for missing disclosure | System | Partial | — | — | — | ✗ Gap *string* only; no CAPA/finding |
| T-22 | Create TCFD disclosure item | Compliance | TCFD | evidence | 6-state, 4-pillar | — | ✅ Works |
| T-23 | Create climate scenario | Risk/CFO | Scenario cycle | — | DRAFT→IN_REVIEW→BOARD_APPROVED→ARCHIVED | — | ✅ Works (as data) |
| T-24 | Add NGFS scenario assumptions | Risk/CFO | Scenario | — | — | — | ◑ Stored, not modelled |
| T-25 | Apply scenario to portfolio segment | Risk/CFO | Scenario | — | — | — | ◑ Sector rows stored |
| T-26 | Calculate portfolio climate exposure | System | Scenario | — | — | — | ✗ **Free-typed, never calculated** |
| T-27 | Record financed emissions (PCAF) | CFO/climate | Data | — | DRAFT→…→PUBLISHED | — | ✅ Real attribution (half-formula) |
| T-28 | Generate IFRS S2 / TCFD annual report | Sustainability mgr | Reporting | approval | — | — | ◑ Scenarios+FE only; **no checklist/GHG/evidence/gaps**; no export/lock |
| T-29 | Create materiality topic | ESG officer | Assessment | — | DRAFT→SCORED→APPROVED→ARCHIVED | — | ✅ Works |
| T-30 | Score materiality topic | ESG officer | Assessment | — | →SCORED | — | ◑ Single-axis average, not double-materiality |
| T-31 | Generate materiality matrix | System | On read | — | — | — | ✅ Works |
| T-32 | Create ESG metric | ESG officer | KPI | — | ACTIVE→AT_TARGET/OFF_TRACK | — | ✅ Works |
| T-33 | Link ESG metric to disclosure | ESG officer | Mapping | — | — | — | ✗ id stored, never dereferenced; no history |
| T-34 | Create climate risk | Risk owner | Identification | — | IDENTIFIED→ASSESSED→TREATED→CLOSED | — | ✅ Works |
| T-35 | Link climate risk to ERM | Risk owner | Mapping | — | — | — | ✗ **Dangling unvalidated id** |
| T-36 | Create climate opportunity | Risk owner | Identification | — | same | — | ✅ Works |
| T-37 | Add vendor ESG surveillance record | Procurement | Onboarding | — | — | — | ✗ **Frontend mock, no backend** |
| T-38 | Request supplier ESG data | Procurement | Survey | — | — | due→remind | ✗ Mock only |
| T-39 | Track supplier ESG non-response | System | Overdue | — | — | escalate | ✗ Missing |
| T-40 | Create vendor corrective action | Procurement | Gap | — | — | — | ✗ Missing |
| T-41 | Generate annual annex | Sustainability mgr | Reporting | approval | DRAFT→…→PUBLISHED | — | ◑ Hardcoded SGI/CMA/TCFD |
| T-42 | Submit annex for review | Sustainability mgr | Draft ready | — | →SUBMITTED | — | ✅ Works |
| T-43 | Approve annex | Executive | Review | approver | →APPROVED | — | ◑ Works; approver id not stamped |
| T-44 | Publish annex | Executive | Approved | — | →PUBLISHED | — | ✅ Works (supersedes prior) |
| T-45 | Export MD / HTML / JSON / CSV | Any | Report | — | — | — | ◑ MD/HTML/JSON only; **no CSV/PDF**; JSON empty for some |
| T-46 | Generate evidence package | Auditor | Audit | — | — | — | ◑ Evidence *list* in composer; no bundle/hash |
| T-47 | Switch AR / EN + RTL | Any | Locale | — | — | — | ✅ Works (full parity) |
| T-48 | Enforce role permissions | System | Any action | — | — | — | ✗ **Admin-only; SUST ungranted** |
| T-49 | Review audit trail | Auditor | Audit | — | — | — | ✗ 11/14 services unaudited |
| T-50 | Archive reporting year | Sustainability mgr | Cycle end | — | →ARCHIVED | — | ✅ Works (but reports still regenerate) |

---

## 4. Ideal Sustainability Workflow Design (target state)

Concise target lifecycles (weak "create→save" designs rejected per the brief). Each names status, required evidence, approvals, SLA/escalation, audit, integration.

1. **Reporting-Year** — ACTIVE→DATA_COLLECTION→REVIEW→APPROVED→PUBLISHED→ARCHIVED; opening a year initialises the CMA/TCFD disclosure matrices from the official library; closing locks child reports.
2. **Annual Annex** — assembled **from live registers** (GHG, SGI, CMA, TCFD, materiality, metrics, climate risk, financed emissions) → DRAFT→SUBMITTED→(review, SoD)→APPROVED (content **hashed + locked**)→PUBLISHED (supersede prior); export MD/HTML/JSON/CSV/**PDF (Arabic RTL via OpenPDF)**; every transition audited.
3. **GHG Inventory** — record DRAFT→SUBMITTED→VERIFIED (evidence FK required, 3rd-party provider)→APPROVED (**edit-locked**; re-open forces re-verification); calculator uses a **unit registry + emission-factor library**; totals + real YoY trajectory; Scope-3 coverage gate → auto-gap if < threshold.
4. **GHG Calculator** — scope→category→activity data→EF (from library, versioned)→unit conversion→method→notes→evidence link→verification→recalc/version→annual comparison.
5. **Emissions Verification** — independent verifier ≠ preparer (SoD); evidence FK; sign-off audited; verified numbers immutable.
6. **Evidence Request** (reuse `module/evidence`) — request from dept/user/vendor→assign owner→due date + countdown→reminders→escalation→upload (real file, hashed)→review→reject-with-reason→**resubmit**→approve→version→expire (scheduled)→link (FK) to disclosure/metric/risk/report→**evidence package**; all audited.
7. **SGI Commitment** — ACTIVE, progress direction-aware, behind-plan (time-elapsed vs `sgiAtRiskProgressFactor` **from settings**)→**auto-raise CAPA**→ACHIEVED/RETIRED; Vision-2030 target catalogue linkage.
8. **CMA Disclosure** — official Art 91–94 library seeded per tenant; status matrix; PARTIAL/NOT_DISCLOSED→gap **+ auto-CAPA**; evidence FK; principal-bound approval with SoD; annual-report linkage.
9. **TCFD Disclosure** — 4-pillar checklist; gap on PARTIAL/NOT_DISCLOSED→auto-CAPA; evidence FK; approval + **lock** on disclosed; feeds the annual climate report.
10. **TCFD Climate Scenario** — NGFS orderly/disorderly/hot-house; assumptions; horizon; sector segments; **exposure × PD-uplift × scenario → computed expected loss** rolled to portfolio; board approval; probability-weight validation (=1.0).
11. **NGFS Scenario Analysis** — scenario library + assumptions + sensitivity + link to climate risks + annual report.
12. **Loan-Portfolio Climate Exposure** — segment exposures → scenario-driven loss engine → aggregated portfolio-at-risk (computed, not typed).
13. **Financed Emissions (PCAF)** — asset-class attribution (exposure × attribution-factor × EF, with data-quality score); per-framework target progress; SBTi/NZBA alignment.
14. **IFRS S2 / Annual Climate Report** — Governance + Strategy + Risk-Mgmt + Metrics&Targets (**from the TCFD checklist**) + scenarios + financed emissions + **GHG inventory** + targets + **evidence index** + **gaps/CAPA**; approval→lock→PDF/Excel export; audited.
15. **ESG Materiality** — **double materiality** (impact axis vs financial axis) → matrix → APPROVED topics anchor CMA 92(a) + climate risks.
16. **ESG Metrics** — metric + time-series readings (history table) + link to disclosure/target; off-track→CAPA.
17. **Climate Risks & Opportunities** — register → **validated bidirectional ERM link** → treatment → reporting.
18. **Vendor ESG Surveillance** — backend register linked to the **TPRM vendor / SCM supplier**; ESG survey request (evidence workflow); non-response escalation; Scope-3 linkage; vendor CAPA.
19. **Corrective Action** — sourced (auto or manual) → owner → due → evidence FK → verify (SoD) → close; **scheduled overdue-warning + executive escalation from settings**.
20. **Report Approval & Publication** — SoD, content hash/lock, immutable published version, audit.
21. **Audit Evidence Package** — one-call bundle: report + linked evidence (files + sha256) + disclosure/GHG source rows + approvals; F7-audit-logged.

---

## 5. Repository & Module Map

- **Backend controllers (endpoint bases):** `/api/sustainability/{reporting-years,reports,ghg,sgi-commitments,cma-disclosures,tcfd,materiality,metrics,evidence,corrective-actions,climate-risks,dashboard,settings}` and `/api/v1/climate/{scenarios,financed-emissions,report}`. ~140 mapped methods total (full inventory captured during review).
- **Entities:** `GhgEmissionRecord, ReportingYear, SustainabilityReport, SgiCommitment, CmaDisclosure, TcfdItem, MaterialTopic, EsgMetric, SustainabilityEvidence, CorrectiveAction, ClimateRiskOpportunity, OrganisationSettings` (Stack A) + `TcfdClimateScenario, TcfdSectorImpact, FinancedEmissionsRecord, FinancedEmissionsTarget` (Stack B, extend `BaseEntity`).
- **Services:** one per sub-module; `SustainabilityDashboardService` composes 9 aggregates; `AnnexGeneratorService` + `ReportComposerService` (annex); `TcfdReportService` (climate report).
- **Frontend:** `EsgPage.tsx` (14 tabs: overview, annex, capa, calculator, cma, tcfd, materiality, metrics, evidence, sgi, ghg, climate, survey, settings) + 3 climate pages; libs as listed in §2; `Sidebar.tsx:361-370` group `esg`/`navEsgClimate` (4 routes).
- **Integration modules present:** `module/evidence` (generic, **unused by sust**), `module/risk` (ERM, **not linked**), `module/reporting` (**no ESG ref**), `module/tprm`/`module/scm` (**no ESG link**), `AuditLogService` (used only by V186 climate).

---

## 6. Current Implementation Findings (page/workflow by workflow)

Consolidated from four `file:line`-grounded audits + live smoke. (Severities in §8/§23.)

**GHG (`sust/ghg`)** — real calculator (`GhgEmissionRecordService.deriveTco2e:230-238`, `aggregate:152-184`, `yoy:190-200`); evidence-gated verification (`:120-123`); hand-rolled validation works (live-confirmed). Defects: `update()` has **no status guard** (`:82-106`) — approved records editable; **no audit log**; unit conversion is a 2-branch string heuristic; coverage metric mislabeled "spend-weighted" (`:26,164`); `evidenceRef` free text; YoY returns 0 (no prior year).

**Annex/Report (`sust/annex`)** — `ReportComposerService` reads live data for most types; **`AnnexGeneratorService` hardcodes SGI/CMA/TCFD sections** (`:149-239`); "immutable on approval" unenforced (no lock/version/hash); attribution columns dead; **no audit log**; MD/HTML/JSON export only (**no CSV/PDF**), and JSON export returned 7 bytes (empty) for report #5 live; V186 financed-emissions never reach the annex.

**Climate/TCFD (`sust/climate`, `sust/tcfd`)** — scenarios/sectors/financed-emissions modelled + real PCAF attribution (`FinancedEmissionsService.recomputeTotal:384-391`) + real probability-weighted loss; **but expected loss is free-typed, never computed** (`TcfdScenarioService:275-276`); **ERM link unvalidated/dangling** (`ClimateRiskOpportunityService.linkErm:204-210`); annual report **omits checklist/GHG/evidence/gaps** (live-confirmed); TcfdItem approval dead + no lock; TcfdItem/ClimateRisk have **no audit log**; **DECIMAL(4,2) can't store score 100** (`TcfdSectorImpact:50-54` vs `@DecimalMax("100.0")`).

**SGI/CMA/Materiality/Metrics/Evidence/CAPA/Dashboard/Settings** — progress + behind-plan detection real (SGI); disclosure matrices + readiness real (CMA/TCFD); materiality scoring real (but single-axis); CAPA lifecycle well-guarded with SoD; dashboard aggregates real. Defects: **`OrganisationSettings` is dead config** (all thresholds hardcoded); **no auto-CAPA** anywhere; **evidence upload is a stub** + no request/SLA/reminder/scheduler/resubmit; **CMA `approve()` trusts body `approvedBy`** (no SoD); no FK evidence links; **no audit log** in any of these 8; no double-materiality; no metric history; **CMA/TCFD library seed-only for org 1**.

**Frontend** — `EsgPage` 14 tabs mostly API-backed; **Overview KPIs + Calculator + Vendor Survey are mock** (`lib/esg.ts` constants + localStorage); climate report **PDF export stubbed**; tabs not deep-linkable; zero ESG tests; i18n/RTL strong.

---

## 7. Feature-Addition Gate Analysis

Every proposed addition passed the gate *"does it make the module more realistic/compliant/usable/integrated without weakening what works?"* Rejected ideas are listed so scope stays disciplined.

| Proposed | Gap it closes | Verdict |
|---|---|---|
| Seed `SUST` grants + `SUSTAINABILITY_OFFICER` role | Module unusable by non-admins | **Add** (S-A) |
| Wire `OrganisationSettings` to consumers | Config is a facade | **Improve** (S-B) |
| Real file upload + evidence-request/SLA (reuse `module/evidence`) | Stub upload, no request flow | **Improve/Add** (S-C) |
| Auto-CAPA/gap on behind-plan / partial-disclosure / evidence-reject | "Close the loop" is narrative | **Add** (S-D) — reuse the compliance F8 `AR-EVD-GAP` pattern already shipped |
| Scenario loss engine (exposure × PD-uplift × scenario) | Loss is free-typed | **Add** (S-E) |
| Validated bidirectional ClimateRisk↔ERM link | Dangling id | **Improve** (S-F) — reuse ERM `RiskLink` typed-link mechanism |
| Complete IFRS S2 report + persist/lock/export | Omits checklist/GHG/evidence/gaps | **Improve** (S-G) |
| Audit logging everywhere | 11/14 unaudited | **Add** (S-H) |
| Annex from live data + lock | Hardcoded sections | **Improve** (S-I) |
| Vendor ESG backend + TPRM link | Frontend mock | **Add** (S-O) |
| Double-materiality axes | Single-average | **Improve** (S-Q) |
| GHG bulk import (CSV) | No import | **Add** (in S-J) |
| Emission-factor library (versioned) | String heuristic | **Add** (in S-J) |
| **Rejected:** new sub-modules (biodiversity, water-stress engine), a bespoke ESG chart library, a second dashboard | No current gap; would complicate | **Do not add** |
| **Rejected:** replacing the 14-tab dashboard with 14 routes | Works today; churn | **Defer** (only make tabs deep-linkable) |

---

## 8. Critical Gaps (make the module unusable / untrustworthy / not market-ready)

1. **RBAC unseeded → admin-only.** `SUST` granted to no role (live-confirmed); climate roles `SUSTAINABILITY_OFFICER`/`CFO`/`BOARD_MEMBER` don't exist. `ModulePermissionEvaluator.java:38-42`; all 11 `sust` controllers; `climate/controller/*Controller.java:35-38`.
2. **`OrganisationSettings` is dead config.** Every threshold hardcoded at consumers (`GhgEmissionRecordService:181`, `SgiCommitmentService:240`, `SustainabilityEvidenceService:213`, `SustainabilityDashboardService:103-106`); settings never injected.
3. **Evidence upload is a stub** (`SustainabilityEvidence.java:50-51`, no `MultipartFile`) + no request/SLA/reminder/scheduler/resubmit + no FK links.
4. **No auto-CAPA / auto-gap** (SGI behind-plan, CMA/TCFD partial) — the loop-closure premise is seed narrative.
5. **Portfolio climate exposure/loss never calculated** — free-typed (`TcfdScenarioService:275-276`); sector exposure×PD-uplift never aggregated.
6. **ClimateRisk↔ERM link is a dangling, unvalidated, cross-tenant-capable id** (`ClimateRiskOpportunityService:204-210`).

---

## 9. GHG Inventory & Calculator Gaps

- [High] `update()` no status guard — approved/verified numbers editable, no re-verification (`GhgEmissionRecordService.java:82-106`).
- [High] No audit log on any GHG mutation.
- [Medium] YoY/trajectory returns 0 — no prior-year baseline data / logic yields 0 (live-confirmed); needs multi-year seed + baseline handling.
- [Medium] Unit conversion = 2-branch string heuristic (`kg*`→÷1000); no unit registry, **no emission-factor library** (factors are hand-typed per row).
- [Medium] Coverage metric mislabeled "spend-weighted" (it's emission-weighted avg of coverage%); no spend input exists (`:26,164,169-171`).
- [Medium] `emissionValueTco2e` recompute silently overrides caller for Scope 1/2 but Scope 3 rows carry asserted values with null activity → inconsistent "authoritative" value, no computed-vs-asserted flag.
- [Medium] Evidence gate satisfied by arbitrary free text; no FK to `SustainabilityEvidence`.
- [Medium] No bulk import (CSV/Excel) endpoint (T-06).
- [Low] `createdBy/updatedBy` never populated; `scope` is a raw `Short` (1/2/3) not a typed enum.

## 10. SGI Commitment Gaps

- [Critical] Behind-plan **does not auto-create a CAPA** (`SgiCommitmentService` has no `CorrectiveActionService` dep); V59 header claim is unimplemented.
- [Medium] At-risk factor `0.7` hardcoded (`:240`), ignores `settings.sgiAtRiskProgressFactor`.
- [Medium] Vision-2030 national-target tracking is seed text only; no structured target catalogue.
- [Low] No audit log; `evidenceRef` free text.

## 11. CMA Disclosure Gaps

- [High] `approve()` trusts request-body `approvedBy` — no principal binding, **no SoD**, forgeable (`CmaDisclosureService.java:162`).
- [Critical] PARTIAL/NOT_DISCLOSED enforces a gap *string* but **never creates a gap/CAPA record**.
- [High] Official Art 91–94 library is **hardcoded INSERTs for org 1 only** (`V60:118-262`); other tenants get an empty matrix, no bootstrap.
- [Medium] Evidence attach is a free-text ref, no FK; [Low] no audit log.

## 12. TCFD & Climate Scenario Gaps

- [Critical] Expected loss free-typed, never computed; sector impacts display-only (`TcfdScenarioService:275-276`, `TcfdSectorImpact:47-57`).
- [High] TcfdItem approval is dead metadata; disclosed items never locked (`TcfdItem.java:60-62`).
- [High] `DECIMAL(4,2)` cannot store max score 100 (`TcfdSectorImpact:50-54` vs `@DecimalMax("100.0")` vs CHECK `<=100`) → persist error at 100.00.
- [High] No audit log / no domain events on `TcfdItem`.
- [Medium] Scenario probability weights not enforced to sum to 1.0 (live: summed to 2.0, only a warning); NGFS categories modelled as data but not driving any calc.
- [Low] Sector-impact FK has no `ON DELETE`, no JPA relationship → orphan risk.

## 13. IFRS S2 / Annual Climate Report Gaps

- [High] Report **omits TCFD checklist (4 pillars), GHG inventory, evidence index, gaps** — scenarios + financed-emissions only (live-confirmed keys: `scenarios, sectorImpacts, weightedExpectedLoss, financedEmissionsTotal, emissionsRecords, targetProgress`).
- [High] No persistence, no approval, no lock, **no PDF/Excel export** (frontend button is a Sprint-12 TODO stub, `ClimateAnnualReportPage.tsx:66-72`).
- [Medium] Financed-emissions total shows 0 in the report because seed records aren't APPROVED (live-observed) — the year-filter is correct but the seed/state leaves the headline empty.

## 14. Materiality & ESG Metrics Gaps

- [Medium] Not true **double materiality** — impact + financial collapsed into one "business impact" axis (`MaterialTopicService:210-220`).
- [Medium] Metric `linkedSgiCommitmentId` never dereferenced; no link to CMA/report; `evidenceRef` free text.
- [Low] Metric `record-reading` overwrites `currentValue` — **no time-series history table**.
- [Low] No unique constraint on `topic_code`/`metric_code` (duplicate codes possible).

## 15. Climate Risks & Opportunities Gaps

- [Critical] ERM link dangling/unvalidated/cross-tenant-capable (`ClimateRiskOpportunityService:204-210`); no `module.risk` import; free-text `related_tcfd_recommendation`.
- [High] No audit log / domain events; [Medium] bound as raw entity, no DTO/`@Valid`.

## 16. Vendor ESG Surveillance Gaps

- [High] Exists **only as a frontend mock** (`lib/esg.ts:367-465` + localStorage); no backend entity, no API.
- [High] No link to `module/tprm` vendor register or `module/scm` supplier; vendor names are free text.
- [Missing] No supplier ESG-data request, no non-response escalation, no vendor CAPA, no Scope-3 linkage.

## 17. Evidence Workflow Gaps

| Capability | Status |
|---|---|
| Request from dept/user/vendor | ✗ missing |
| Assign owner / due date / SLA / countdown | ✗ missing (owner is free-text string) |
| Reminders / escalation | ✗ missing |
| Upload file | ✗ stub (client-supplied `fileUrl`, no `MultipartFile`/storage/hash) |
| Comments thread | ✗ (single `notes`) |
| Review / reject w/ reason | ✅ present (principal-bound) |
| Resubmit | ✗ missing |
| Approve (SoD) | ✅ present (approver ≠ owner) |
| Version | ◑ free-text `version` + `supersedesId` |
| Expire | ◑ `sweepExpired()` exists but **not scheduled** (manual endpoint only) |
| Link to disclosure/metric/risk/report | ✗ free-text `moduleArea` label, no FK |
| Evidence package / bundle | ✗ (composer lists rows only, no bundle/hash) |
| Audit log | ✗ none |

Contrast `module/evidence` (generic) which already has `EvidenceRequest` (owner/due/priority/lastRemindedAt), `EvidenceRequestSlaScheduler`, `EvidenceExpiryScheduler`, reviews, versions, storage — **reuse it (S-C)** rather than rebuild.

---

## 18. Button & Process Testing Results (live `:8084`, org 1)

| Page/API | Action | Expected | Actual | Pass/Fail | Sev |
|---|---|---|---|---|---|
| GHG | list/aggregate/yoy | data | 17 recs; aggregate real (195,610 tCO₂e, cov 68.82%); **yoy=0** | ◑ | Med |
| GHG | POST negative value | 400 | `400 "emission_value_tco2e must be >= 0"` | ✅ | — |
| GHG | POST coverage 150 | 400 | `400 "coverage_percent must be 0..100"` | ✅ | — |
| GHG | POST scope 9 | 400 | `400 "scope must be 1, 2 or 3"` | ✅ | — |
| GHG | verify w/o evidence | 400 | `400 "requires evidence_ref (Rule 2)"` | ✅ | — |
| CMA | readiness | real | 73%, 3 gaps | ✅ | — |
| TCFD | readiness | real | alignment 73%, strategy-resilience gap flagged | ✅ | — |
| Materiality | generate-matrix | real | 5 topics, 1 critical/3 high | ✅ | — |
| Scenarios | weighted-expected-loss | real | SAR 2,880.5M + **weights-sum-2.0 warning** | ◑ | Med |
| Financed-emissions | totals?year=2025 | totals | **all zeros** (no APPROVED rows) | ◑ | Med |
| Climate report | annual?year=2025 | full S2 pack | **missing checklist/GHG/evidence/gaps** | ✗ | High |
| Annex report #5 | GET + export | real content | `contentMarkdown`=190 chars stub; **export/json=7 bytes** | ✗ | High |
| Settings | GET | config | real row (thresholds) — but **never consumed** | ✗ | Crit |
| All `/api/sustainability/*` | non-admin access | role-based | **admin-only** (SUST ungranted) | ✗ | Crit |

Frontend button tests (per audit, to be re-walked on `:5173` during S-P): create/edit/transition flows on the 3 climate pages work; **Export PDF** (climate report) = TODO toast; Overview/Calculator/Survey tabs operate on mock data.

---

## 19. Data-Model Gaps

- **[High] `TcfdSectorImpact.transitionRiskScore/physicalRiskScore` `DECIMAL(4,2)` cannot store 100.00** (needs `DECIMAL(5,2)`) — `V186:118-119`, entity `:50-54`.
- Evidence has **no FK** to source entities (free-text `moduleArea`); disclosures/metrics/risks store free-text `evidenceRef` — needs a polymorphic evidence-link table or FK columns.
- `climate_risk_opportunity.related_erm_risk_id` / `related_tcfd_recommendation` are unconstrained ids/strings — need validated FKs.
- No `esg_metric_reading` history table (readings overwrite).
- No unique constraint on `material_topic.topic_code` / `esg_metric.metric_code`.
- No `emission_factor` library table; no `vendor_esg_*` tables.
- Inconsistent base: Stack B extends `BaseEntity` (rowversion/optimistic-lock); Stack A hand-rolls audit + no `@Version` → no optimistic locking on the report/disclosure rows that most need it.

## 20. API Gaps

- Missing: GHG bulk import; evidence-request lifecycle endpoints; scenario loss recompute; validated ERM-link; report PDF/Excel export; CSV export; vendor-ESG CRUD; metric-reading history.
- Inconsistent: `/api/sustainability/*` vs `/api/v1/climate/*` (two bases); no versioning on Stack A.
- No `@Valid`/DTOs on Stack A controllers (raw entity binding → mass-assignment surface + 500-on-bad-enum instead of 400).

## 21. UI/UX & Arabic/RTL Gaps

- [High] Landing KPIs + Overview chart are **hardcoded demo data** (`lib/esg.ts`), diverging from the live tabs.
- [Medium] Climate report **PDF export stubbed**; [Medium] Vendor Survey tab is mock.
- [Low] 14 EsgPage tabs not deep-linkable (in-page state only).
- [Low] Inline `isAr ? … : …` literals + `lib/esg.ts` embedded strings bypass the `translations.js`/`t()` convention (acceptable but inconsistent).
- ✅ Strengths: full EN/AR key parity, logical CSS props, `dir="rtl"` inputs, `overflow-x` table wrappers.

## 22. Integration Gaps

| Integration | State |
|---|---|
| ERM risk register | ✗ dangling id, no FK, no reverse link |
| Control library / compliance | ✗ none |
| Generic evidence repository (`module/evidence`) | ✗ siloed (own `sust.evidence`) |
| Corrective-action auto-raise | ✗ manual only |
| Vendor/supplier (TPRM/SCM) | ✗ frontend mock only |
| Reporting engine / board pack | ✗ no ESG reference |
| Notifications | ✗ none |
| Audit trail | ◑ only V186 climate (2 of 14 services) |
| Role permissions | ✗ ungranted / roles missing |
| AR/EN i18n | ✅ complete |

---

## 23. Required Remediation Backlog

Epic → user story → acceptance criteria → priority/severity → complexity → files → deps. (Priority P0=critical blocker, P1=high, P2=medium.)

- **S-A — RBAC enablement** · *P0/Critical · M.* As a Sustainability Officer I can use the module with a non-admin role. **AC:** migration seeds `SUST_READ/WRITE/DELETE` permissions + grants to `SUSTAINABILITY_OFFICER` (new), `RISK_MANAGER`, `COMPLIANCE_OFFICER`, `EXECUTIVE_REVIEWER`, `INTERNAL_AUDITOR` (read); climate controllers switched to `hasPermission('SUST',…)` (retire the `hasAnyRole` list referencing non-existent roles); a non-admin `SUSTAINABILITY_OFFICER` can CRUD per grants, 403 otherwise (test). *Files:* new `V187` migration + role seed; all `sust` + `climate` controllers. *Dep:* none. *(Ties to memory `rbac-permission-catalogue-gaps`.)*
- **S-B — Settings wiring** · *P0/Critical · S.* **AC:** GHG Scope-3 threshold, SGI at-risk factor, evidence expiry-window + sweep cron, dashboard bands, CAPA overdue/escalation days all read from `OrganisationSettings`; changing a setting changes behaviour (test each). *Files:* the 5 consumer services + `OrganisationSettingsService` injection.
- **S-C — Real evidence pipeline** · *P0/Critical · L.* **AC:** reuse `module/evidence` `EvidenceRequest` (request→owner→due→reminder→escalate→upload real `MultipartFile` w/ sha256→review→reject→**resubmit**→approve→version→expire via `@Scheduled`); ESG entities gain evidence **FK**; evidence-package endpoint bundles files + hashes. *Files:* `sust/evidence/*`, integration with `module/evidence`, FK columns migration. *Dep:* S-H.
- **S-D — Auto-CAPA/gap loop** · *P0/Critical · M.* **AC:** SGI behind-plan, CMA/TCFD PARTIAL/NOT_DISCLOSED, and evidence-rejected each auto-open a `CorrectiveAction`/gap (idempotent, deduped) — mirror the compliance F8 `AR-EVD-GAP` rule already shipped. *Files:* SGI/CMA/TCFD/evidence services + `CorrectiveActionService`. *Dep:* S-H.
- **S-E — Scenario loss engine** · *P0/Critical · M.* **AC:** expected loss computed from Σ(sector exposure × PD-uplift × scenario factor); portfolio-at-risk aggregated; probability weights validated to sum≈1.0 (reject/flip to warning per config); free-typed loss fields become derived/read-only. *Files:* `TcfdScenarioService`, `TcfdSectorImpact`.
- **S-F — ClimateRisk↔ERM link** · *P0/Critical · M.* **AC:** `ermRiskId` validated to exist + same-tenant (reject else); bidirectional visibility (reuse ERM `RiskLink` typed-link `CLIMATE`); TCFD↔climate-risk links validated both ways. *Files:* `ClimateRiskOpportunityService`, `module/risk` link surface.
- **S-G — Complete IFRS S2 report** · *P1/High · L.* **AC:** report includes the 4-pillar TCFD checklist + GHG inventory + evidence index + gap/CAPA list; persisted, approval-gated, content-locked, PDF (Arabic RTL/OpenPDF) + Excel (POI) export. *Files:* `TcfdReportService`, `ClimateReportController`, new persistence.
- **S-H — Audit logging everywhere** · *P1/High · M.* **AC:** all 14 services log create/update/transition/approve/reject/verify/delete via `AuditLogService`; auditor can trace every disclosure/number to who-did-what-when. *Files:* the 11 unlogged services.
- **S-I — Annex from live data + lock** · *P1/High · M.* **AC:** `AnnexGeneratorService` reads live SGI/CMA/TCFD entities (drop hardcoded arrays); content hashed + locked on approval; CSV + PDF export; RTL `dir` fixed. *Files:* `AnnexGeneratorService`, `SustainabilityReportService`.
- **S-J — GHG hardening** · *P1/High · M.* **AC:** approved records edit-locked (re-open→re-verify); real multi-year YoY; versioned emission-factor library + unit registry; coverage metric relabeled/corrected; bulk CSV import; evidence FK. *Files:* `GhgEmissionRecordService`, new EF library.
- **S-K — CMA/TCFD approval SoD + library** · *P1/High · M.* **AC:** principal-bound approval + SoD; disclosed/approved items locked; official CMA Art 91–94 + TCFD 11-rec library seeded per-tenant on year open. *Files:* `CmaDisclosureService`, `TcfdItemService`, library seed.
- **S-L — DTO + validation** · *P2/Medium · M.* **AC:** request DTOs + `@Valid` across Stack A; enum fields validated in Java (400 not 500); mass-assignment closed. *Files:* all Stack A controllers/dtos.
- **S-M — API/auth unification** · *P2/Medium · S.* **AC:** climate endpoints re-based under `/api/v1/sustainability/*` (alias old paths) with one authz scheme. *Files:* climate controllers, frontend `lib/climate.ts`.
- **S-N — Reporting + notifications** · *P2/Medium · M.* **AC:** ESG surfaces in board-pack/reports-centre; evidence/CAPA/SLA fire notifications. *Files:* `module/reporting`, notification wiring.
- **S-O — Vendor ESG surveillance** · *P1/High · L.* **AC:** backend `vendor_esg_survey` entity linked to TPRM vendor / SCM supplier; ESG-data request via evidence workflow; non-response escalation; vendor CAPA; frontend Survey tab hits the API (kills mock). *Files:* new sub-package + TPRM link + frontend.
- **S-P — Frontend truth + polish** · *P1/High · M.* **AC:** Overview KPIs/chart read live dashboard API (remove `lib/esg.ts` mock); climate report PDF export real; tabs deep-linkable; ESG component/E2E tests added. *Files:* `EsgPage.tsx`, `lib/esg.ts`, `esg/*Page.tsx`, tests.
- **S-Q — Double materiality** · *P2/Medium · S.* **AC:** separate impact-materiality and financial-materiality axes; matrix plots both. *Files:* `MaterialTopic`, `MaterialTopicService`.
- **S-R — Data-model fixes** · *P1/High · S.* **AC:** `DECIMAL(5,2)` score columns; unique `topic_code`/`metric_code`; `esg_metric_reading` history table; sector-impact `@ManyToOne`+`ON DELETE`. *Files:* migration V188 + entities.

## 24. Immediate Fixes (quick wins, < ½ day each)

1. Seed `SUST` permission grants + `SUSTAINABILITY_OFFICER` role (S-A core) — unblocks all usage.
2. `DECIMAL(4,2)→(5,2)` score-column fix (S-R) — removes a latent persist crash.
3. Wire the 8 `OrganisationSettings` values to consumers (S-B).
4. GHG `update()` status guard (block edits on APPROVED/VERIFIED).
5. CMA `approve()` → bind `@AuthenticationPrincipal` + SoD (match evidence/CAPA).
6. Fix annex HTML `dir="ltr"`→`rtl`; fix empty JSON export.

## 25. Structural Fixes (deeper)

- Reuse `module/evidence` instead of the `sust.evidence` silo (S-C).
- Auto-CAPA F8 rules (S-D). Scenario loss engine (S-E). Validated ERM link (S-F). Complete + persist the IFRS S2 report (S-G). Annex from live data + lock (S-I). Audit logging everywhere (S-H). DTO/validation layer (S-L). API/auth unification (S-M).

## 26. Data-Model Changes Required

`V187` (RBAC seed) · `V188`: widen score columns to `DECIMAL(5,2)`; add evidence-link FK columns / polymorphic `sust_evidence_link`; `esg_metric_reading` history; unique `topic_code`/`metric_code`; `emission_factor` library; `vendor_esg_survey` (+ TPRM FK); `@Version`/rowversion on `SustainabilityReport`, `CmaDisclosure`, `TcfdItem`; content-hash column on `SustainabilityReport`.

## 27. API Changes Required

Add: `POST /ghg/import` (CSV); evidence-request lifecycle (reuse generic); `POST /scenarios/{id}/recompute-loss`; validated `POST /climate-risks/{id}/link-erm`; `GET /reports/{id}/export/{pdf|xlsx|csv}`; `GET /climate/report/annual` → persisted + `POST …/approve|publish`; vendor-ESG CRUD; `GET /metrics/{id}/readings`. Re-base `/api/v1/climate/*` → `/api/v1/sustainability/*` (alias). Add DTOs + `@Valid` throughout.

## 28. Workflow-Engine Changes Required

Scheduled jobs: evidence expiry sweep (cron from settings), CAPA overdue-warning + executive escalation, SGI/metric behind-plan sweep → auto-CAPA. Status locks: GHG APPROVED edit-lock; report/disclosure content-lock on approval (hash). SoD: CMA approve. F8 domain events on all sust transitions + auto-CAPA rules (mirror `AR-EVD-GAP`).

## 29. Testing Plan

- **Unit (backend):** loss engine math; settings-driven thresholds; auto-CAPA idempotency; ERM-link validation; GHG edit-lock; report completeness; SoD. Fill the materiality/metrics/evidence/annex/dashboard test gap.
- **API:** RBAC ratchet (each role × endpoint); DTO validation (400 not 500); evidence lifecycle; export formats.
- **UI/E2E:** the 51 cases in §Test-Cases; live nav walk of all 14 tabs + 3 climate pages on `:5173` in EN + AR/RTL; deep-link + refresh persistence.
- **Regression:** full `mvn test`; frontend `tsc --noEmit` + vitest; live `:8084` smoke re-run (this plan's read-sweep + probes) after each epic.

## 30. Acceptance Criteria for Remediation (module is "market-ready" when)

A non-admin `SUSTAINABILITY_OFFICER` can, end-to-end: open a reporting year (matrices auto-seed from the official library) → enter/import GHG with real EF library, evidence-upload, and verification (SoD) → see real YoY → track SGI/metrics with behind-plan auto-CAPA → complete CMA/TCFD disclosures (approval + SoD + lock, gaps auto-raise CAPA) → run climate scenarios with **computed** portfolio loss → record financed emissions → generate a **complete, persisted, locked, PDF-exportable** IFRS S2 annual report + annex assembled **from live data** → link climate risks to ERM (validated) → run vendor ESG surveillance against the real vendor register → pull an audit evidence package — with **every action audit-logged**, **settings honoured**, and **AR/EN + RTL** intact.

## 31. Implementation Order

`S-A` (unblock access) → `S-R`+`S-B` (quick data/config fixes) → `S-H` (audit, prereq for the rest) → `S-C` (evidence) → `S-D` (auto-CAPA) → `S-K` (CMA/TCFD SoD+library) → `S-E` (loss engine) → `S-F` (ERM link) → `S-J` (GHG) → `S-G`+`S-I` (reports/annex) → `S-L`+`S-M` (DTO/API hygiene) → `S-O` (vendor ESG) → `S-P` (frontend truth) → `S-N` (reporting/notify) → `S-Q` (double materiality).

## 32. Risks if Not Fixed

- **Compliance/audit:** unaudited disclosure changes + editable approved GHG + unenforced "immutable" reports = **not defensible** to a CMA/IFRS-S2 assurer; hardcoded annex sections **misreport**.
- **Security:** admin-only access blocks real operation; forgeable CMA approval + cross-tenant ERM link = integrity/authz exposure.
- **Market:** a Saudi bank cannot rely on free-typed scenario loss or a report missing the TCFD checklist/GHG; vendor-ESG mock fails procurement due-diligence.
- **Adoption:** dead settings + stub upload + mock KPIs erode trust the moment a user probes them.

## 33. Final Recommendation

**Partially usable — needs targeted remediation.** Strong, largely-real foundation (calculators, lifecycles, aggregates, bilingual UI, 88 tests) held back by a bounded set of blockers: **RBAC (unusable by real personas), dead settings, stub evidence, no auto-CAPA, uncalculated scenario loss, dangling ERM link, incomplete/locked reports, and audit-trail holes.** None are foundational rewrites; all are addressable via the S-A…S-R backlog. Not market-ready today; credibly market-ready after S-A…S-K (P0/P1). **Do not ship to an audited Saudi ESG/climate buyer until at least the six §8 criticals + the audit-logging (S-H) and report-completeness (S-G) highs are closed.**

---

## Test Cases (LOOP 9)

Format: ID · scenario · role · preconditions · steps · expected · *(actual/pass placeholders for execution)* · severity-if-failed · workflow · surface.

| ID | Scenario | Role | Steps | Expected | Sev | Surface |
|---|---|---|---|---|---|---|
| TC-01 | Create reporting year | Sust.Officer | POST reporting-years | 201, ACTIVE, matrices seed from official library | High | annex |
| TC-02 | Create annual annex | Sust.Officer | generate-annex | 201, content from **live** SGI/CMA/TCFD (not hardcoded) | High | annex |
| TC-03 | Add Scope 1 | GHG owner | POST ghg scope=1 | 201, total recomputed | Med | ghg |
| TC-04 | Add Scope 2 | GHG owner | POST ghg scope=2 | 201 | Med | ghg |
| TC-05 | Add Scope 3 | GHG owner | POST ghg scope=3 | 201, coverage recomputed | Med | ghg |
| TC-06 | Negative emission | GHG owner | POST value=-5 | **400** (✅ verified) | High | ghg |
| TC-07 | Import GHG CSV | GHG owner | POST /ghg/import | 200, rows created | High | ghg |
| TC-08 | Calculate total | System | GET aggregate | real sums (✅ 195,610) | High | ghg |
| TC-09 | Calculate YoY | System | GET yoy (2 yrs) | non-zero deltas (**currently 0**) | Med | ghg |
| TC-10 | Verify w/ evidence | Verifier | attach FK → verify | VERIFIED | High | ghg |
| TC-11 | Verify w/o evidence | Verifier | verify | **400** (✅ verified) | High | ghg |
| TC-12 | Edit approved GHG | GHG owner | PUT on APPROVED | **rejected/locked** (currently allowed) | High | ghg |
| TC-13 | Evidence overdue | System | request past due | reminder+escalation fired | High | evidence |
| TC-14 | Evidence expiry | System | scheduled sweep | APPROVED→EXPIRED + linked flagged | High | evidence |
| TC-15 | Reject evidence | Reviewer | reject w/ reason | REJECTED (✅ present) | Med | evidence |
| TC-16 | Resubmit evidence | Owner | resubmit | new version PENDING (**missing**) | High | evidence |
| TC-17 | Approve evidence (SoD) | Approver≠owner | approve | APPROVED; self-approve→400 (✅) | High | evidence |
| TC-18 | Upload real file | Owner | multipart upload | stored + sha256 (**stub today**) | Crit | evidence |
| TC-19 | Add SGI commitment | Sust.Officer | POST | 201 | Med | sgi |
| TC-20 | Behind-plan→CAPA | System | progress < expected | AT_RISK + **auto CAPA** (missing) | Crit | sgi/capa |
| TC-21 | Add CMA item | Compliance | POST | 201 | Med | cma |
| TC-22 | CMA partial w/o gap | Compliance | mark-partial no gap | **400** (Rule 5 ✅) | High | cma |
| TC-23 | CMA partial→gap/CAPA | System | mark-partial | gap + **auto CAPA** (missing) | Crit | cma/capa |
| TC-24 | CMA approve SoD | Approver≠owner | approve | principal-bound + SoD (**body-trusted today**) | High | cma |
| TC-25 | Attach CMA evidence FK | Compliance | attach | FK link (**free-text today**) | Med | cma |
| TC-26 | Add TCFD item | Compliance | POST | 201 | Med | tcfd |
| TC-27 | TCFD disclosed→lock | Compliance | approve item | locked (**not enforced**) | High | tcfd |
| TC-28 | Add NGFS scenario | Risk/CFO | POST | 201 | Med | scenarios |
| TC-29 | Sector score = 100 | Risk/CFO | score=100 | stored OK (**DECIMAL(4,2) bug**) | High | scenarios |
| TC-30 | Apply scenario→portfolio | Risk/CFO | add sector impacts | rows stored | Med | scenarios |
| TC-31 | Calculate climate exposure | System | recompute-loss | **computed** loss (free-typed today) | Crit | scenarios |
| TC-32 | Probability weights ≠ 1 | Risk/CFO | weights sum 2.0 | reject/flag (**warning only**) | Med | scenarios |
| TC-33 | Add financed emissions | CFO | POST | 201, PCAF attribution | Med | financed |
| TC-34 | FE totals (approved) | System | totals?year | non-zero when APPROVED (**0 today**) | Med | financed |
| TC-35 | Generate IFRS S2 report | Sust.Officer | GET annual | includes checklist+GHG+evidence+gaps (**missing**) | High | climate-report |
| TC-36 | Export report PDF | Sust.Officer | export pdf | file (**stub TODO**) | High | climate-report |
| TC-37 | Edit published report | Sust.Officer | mutate published | **rejected/locked** (unenforced) | High | annex |
| TC-38 | Add materiality topic | ESG officer | POST | 201 | Med | materiality |
| TC-39 | Double-materiality score | ESG officer | score impact+financial | two axes (**single avg today**) | Med | materiality |
| TC-40 | Generate matrix | System | generate-matrix | real (✅) | Low | materiality |
| TC-41 | Add ESG metric + reading history | ESG officer | record-reading ×2 | history retained (**overwrites today**) | Low | metrics |
| TC-42 | Add climate risk | Risk owner | POST | 201 | Med | climate |
| TC-43 | Link climate risk→ERM (valid) | Risk owner | link-erm real id | linked + bidirectional (**dangling today**) | Crit | climate |
| TC-44 | Link climate risk→ERM (bogus id) | Risk owner | link-erm fake id | **400** (accepts today) | Crit | climate |
| TC-45 | Vendor ESG record | Procurement | POST vendor-esg | 201 backed by API + TPRM link (**mock today**) | High | survey |
| TC-46 | Supplier non-response | System | overdue survey | escalation (**missing**) | High | survey |
| TC-47 | Export CSV | Any | export csv | file (**absent today**) | Med | annex |
| TC-48 | Generate evidence package | Auditor | evidence-package | bundle + sha256 (**list-only today**) | High | evidence |
| TC-49 | Arabic / RTL walk | Any | toggle AR | all 14 tabs + 3 pages RTL, no clipping (✅ i18n parity) | Med | all |
| TC-50 | Unauthorized access | Sust.Officer (non-admin) | any endpoint | **200 per grant** (403 everywhere today) | Crit | RBAC |
| TC-51 | Audit-log generation | Auditor | any mutation | audit row written (**11/14 services silent**) | High | audit |

---

*Prepared 2026-07-02 · review-only · evidence: 4 audit agents + live `:8084` smoke + `iam` RBAC DB check. No code modified. Next command to begin implementation (after approval): start `S-A` (RBAC enablement) on branch `feat/esg-remediation`.*
