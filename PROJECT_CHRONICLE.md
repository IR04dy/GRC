# TSS GRC Platform — Project Chronicle & Complete Application Handbook

> **What this document is:** the single self-contained reference for the TSS GRC
> Platform — what it is for (business), how it is built (technical), how to install
> and configure it, how to operate and troubleshoot it, and the complete development
> history with every decision and lesson learned. A reader who knows nothing about
> the project should be able to understand, run, deploy, and maintain the application
> from this document alone.
>
> Written 2026-07-13 at master `73fb9399` (tests 2,249/0, migrations V1–V295).

---

## Table of contents

| § | Section | For whom |
|---|---|---|
| 1 | The application — business purpose & value | everyone |
| 2 | Regulatory coverage map | compliance / product |
| 3 | Functional reference — every module, screen, API | product / QA / developers |
| 4 | Users, roles & demo tenants | admins / demo |
| 5 | Technology stack & repository layout | developers |
| 6 | Core architecture patterns | developers |
| 7 | Security & access-control model | security / developers |
| 8 | Database | DBAs / developers |
| 9 | Environments — dev machine & production server | operators / developers |
| 10 | Installation guide — fresh production server | operators |
| 11 | Configuration reference — external.properties & profiles | operators |
| 12 | Operations manual — run, update, backup, monitor | operators |
| 13 | The chronicle — everything done, in order | everyone |
| 14 | Verification discipline | developers / QA |
| 15 | Troubleshooting encyclopedia | operators / support |
| 16 | Developer landmines | developers |
| 17 | Design decisions & rationale | architects |
| 18 | Glossary | everyone |
| 19 | Current state & open items | everyone |

---

## 1. The application — business purpose & value

**TSS GRC Platform** is a bilingual (**Arabic-primary / English**), multi-tenant,
enterprise **Governance, Risk & Compliance** platform purpose-built for **Saudi
Arabia and the MENA region**. One product covers the full GRC surface that a Saudi
bank, telco, energy company, or government-adjacent enterprise must operate:
enterprise risk, regulatory compliance, cybersecurity + SOC operations, business
continuity, privacy (PDPL), third-party risk, sustainability/ESG with a complete
green-finance layer (Green Sukuk / green portfolio), and access governance — all on
one data model, one workflow engine, one evidence subsystem, and one export stack.

**The problem it solves.** Saudi enterprises answer to many regulators at once (SAMA,
NCA, SDAIA, CMA, NCEMA, NCEC, SOCPA, Tadawul…), each demanding registers,
assessments, evidence, attestations, and periodic packages — usually run today in
spreadsheets and e-mail. The platform replaces that with governed records: every
register is workflow-controlled (maker-checker / four-eyes), every figure traces to
evidence, every action lands in an audit ledger, and every regulator package is one
click (branded, bilingual, hash-manifested).

**What makes it Saudi-first, not localized-later:**

- Arabic is a first-class data language: every user-visible record carries `*_en`
  **and** `*_ar` columns (the backend rejects records missing the Arabic name);
  the UI is fully RTL; PDF/XLSX exports render real Arabic shaping (bundled Amiri
  font) with RTL run direction.
- Hijri + Gregorian dual dating on attestations and review windows.
- Saudi regulatory content is seeded, not left as an exercise: NCA ECC controls,
  SAMA CSF/OpRes mappings, PDPL article anchors, CMA disclosure articles, the
  SOCPA IFRS S1/S2 requirement checklist, Saudi Green Initiative commitments,
  RVCMC carbon-credit registry conventions, NCEC permit types.
- Green finance follows the instruments actually issued in the Kingdom: Green
  **Sukuk** with a Shariah-structure gate (Mudaraba/Wakala/Ijara/Hybrid + Shariah
  board approval reference required before approval), ICMA GBP use-of-proceeds
  discipline, and PIF/Aramco-style allocation & impact reporting.

**Who uses it (personas).** ~55 seeded roles per tenant, grouped as: platform/GRC
administrators; module officers (risk manager, compliance officer, CISO, BCM
manager, DPO, TPRM officer, sustainability officer, IAM officer); approvers and
committee members; executives (read-most dashboards, board pack); and read-only
assurance personas (internal audit, external assurance, regulator liaison).

**Deployment shape.** A modular-monolith Spring Boot REST backend + a standalone
React SPA. No server-side rendering. Runs on commodity Windows Server + SQL Server;
one jar, one static UI folder, one nginx, one config file.

---

## 2. Regulatory coverage map

| Regulator / framework | Where in the product |
|---|---|
| **NCA ECC** (Essential Cybersecurity Controls) | Cyber module frameworks, self-assessment + filing export (`/nca-ecc-export`, `/nca-ecc/filings`), access-control anchors (ECC 2-2) in IAM governance |
| **SAMA CSF** & Operational Resilience | Cyber mappings, BCM OpRes packet, outsourcing register w/ SAMA-pending queue (`/tprm/outsourcing`), access governance (CSF 3.3.5), audit evidence packages |
| **PDPL / SDAIA** | Privacy module: RoPA, DPIA, DSR cases (30-day countdown), breach 72-hour clock, consent, cross-border transfer; cyber-PDPL bridge (`/cyber-pdpl-bridge`) |
| **CMA** (Capital Market Authority) | ESG: CMA disclosure register (article-anchored, SoD approval + content lock) |
| **Tadawul ESG guidelines** | ESG disclosure register + report exports |
| **SOCPA — IFRS S1/S2** | ESG: 14-item S1/S2 readiness checklist + REG-1 obligation that mandates verifying SOCPA's phase dates at socpa.org.sa each reporting cycle |
| **GHG Protocol** | GHG inventory: Scopes 1/2/3 with the full 15 Scope-3 categories, emission-factor library, verification lifecycle |
| **ISAE 3410** | Assurance-lineage view: every disclosed object graded COMPLETE / PARTIAL / MISSING on data → evidence → verification, with a readiness % |
| **TCFD / IFRS S2** | TCFD item register, climate scenarios with computed expected loss, climate report |
| **Saudi Green Initiative / Net-zero 2060** | SGI commitment register with auto-CAPA on gaps |
| **ICMA Green Bond Principles / CCE taxonomy** | Green instrument register, use-of-proceeds ledger, allocation & impact reports, portfolio taxonomy tagging + Green Asset Ratio |
| **RVCMC** (Regional Voluntary Carbon Market Co.) | Carbon-credit purchase/retire ledger with certificate refs + tenant-configurable offset cap |
| **NCEC** (environmental compliance) | Environmental permit register with expiry surveillance → auto-CAPA |
| **EU CBAM** | CBAM exposure lines (CN groups, embedded tCO₂e) for exporters |
| **NCEMA** | BCM: crisis events with regulatory deadline calculators, NCEMA packet export |
| **PCAF** | Financed-emissions data quality scoring in the green portfolio |
| **MWAN / SEEC** | Waste / energy-efficiency obligations in the ESG regulatory register (REG-*) |

---

## 3. Functional reference — every module, screen, API

Backend modules live under `tss-grc-backend/src/main/java/com/tss/grc/module/<name>/`,
each with `controller / dto / entity / repository / service`. The React UI (v2) lives
in `tss-grc-ui-v2/src/pages/`. All APIs are JSON under `/api/**` and require
`Authorization: Bearer <JWT>` unless marked public.

### 3.1 Risk (ERM) — `module/risk` · schema `risk` · API `/api/v1/risks`, `/api/risk/**`
Risk register with multi-dimensional impact, appetite ↔ register wiring with breach
escalation, KRIs (single canonical engine), treatment plans with close gates,
acceptance workflow, typed risk linkage, scheduled reviews + gated archive,
committee/policy linkage, FAIR quantification (`/risk/fair`), risk scenarios and
emerging risk registers, reporting + audit package.
**Screens:** `/risk`, `/risk/dashboard`, `/risk-scenarios`, `/emerging-risk`,
`/risk/fair`, `/risk-lifecycle`.

### 3.2 Compliance & Policy — `module/compliance` · schema `compliance` · API `/api/v1/compliance/**`
Obligations register (bilingual, owner, review cycle), obligation attestation
campaigns, frameworks/controls libraries with crosswalk, findings + CAPA lifecycle
with reopen paths, policy documents with real file versioning + SoD, compliance
posture dashboard, audit evidence packages.
**Screens:** `/compliance`, `/compliance/obligations`, `/compliance/obligation-campaigns`,
`/compliance/posture`, `/regulatory/library`, `/controls/library`, `/framework-controls`,
`/control-crosswalk`, `/policy-management`, `/policy`, `/policy-templates`,
`/regulatory-change`, `/regulatory/horizon`.

### 3.3 Cyber + SOC Ops — `module/cyber`, `module/socops` · schemas `cyber`, `socops` · API `/api/v1/cyber/**`
NCA ECC frameworks and filings, assets, vulnerabilities with risk-based SLA clocks,
incidents (9-step lifecycle DETECTION→…→LESSONS_LEARNED), threat intel with IOC→
asset/vuln action links + MITRE detection library, forensics custody ledger
(append-only), SIEM/EDR/VM integrations (Splunk, CrowdStrike, Tenable, ServiceNow —
stub + live modes), cloud services register (CCC), awareness campaigns, JML events.
**Screens:** `/cyber`, `/cyber/dashboard`, `/cyber/vulns/v2`, `/cyber/threat-intel`,
`/cyber/cloud-services`, `/nca-ecc-export`, `/nca-ecc/filings`, `/third-party-cyber`,
`/cyber-lifecycle`.

### 3.4 BCM — `module/bcm` · schema `bcm` · API `/api/v1/bcm/**`
BIA (qualitative + quantitative), MTPD inheritance, BCP/DR plans generated from BIA,
DR validation checklists, exercises with facilitate/participate/observe run screens
and observation→gap→CAPA promotion, crisis events with regulatory deadline
calculators + mobile crisis room, communication templates + dispatch, dependency
mapping with cascade analysis, strategy ROI, NCEMA/SAMA OpRes packets, evidence
binders, management reviews, SIEM-event feed, readiness dashboard.
**Screens:** `/bcm`, `/bcm/dashboard`, `/bcm/bia`, `/bcm/bia-quantitative`,
`/bcm/plans`, `/dr-plans`, `/bcm/scenarios`, `/bcm/exercises/run/:id/*`,
`/bcm/crisis-room`, `/crisis/mobile`, `/bcm/comms/*`, `/bcm/dependency`,
`/bcm/dependency-cascade`, `/bcm/mtpd-inheritance`, `/bcm/strategy-roi`,
`/bcm/readiness`, `/bcm/remediation`, `/bcm/reports`, `/bcm/settings`,
`/bcm/compliance`, `/bcm/library`, `/bcm/management-reviews`, `/bcm/dr-test-trend`,
`/bcm/siem-events`, `/resilience-tests`, `/bcm-lifecycle`.

### 3.5 Privacy (PDPL) — `module/privacy`, `module/datagov` · schemas `privacy`, `datagov` · API `/api/v1/privacy/**`
RoPA, DPIA, DSR cases with 30-day countdown, breach register with 72-hour clock,
consent management, cross-border transfers, data-flow mapping, data governance
(retention/disposal/catalog/lineage/quality), processor assessments, data-sharing
agreements, PDPL extension registers.
**Screens:** `/privacy`, `/privacy/dashboard`, `/pdpl`, `/ropa`, `/data-lifecycle`,
`/pdpl-extensions`, `/privacy/data-flow`, `/data-sharing`, `/processor-assessments`,
`/privacy-lifecycle`.

### 3.6 TPRM / SCM — `module/tprm`, `module/scm` · schemas `tprm`, `scm` · API `/api/v1/tprm/**`, `/api/v1/scm/**`
Vendor register, assessments, SAMA outsourcing register with admin approval queue,
vendor BCP campaigns with tokenized external attestation submission, fourth-party
mapping, vendor ESG surveillance (surveys + escalation), contract clause library.
**Screens:** `/tprm`, `/tprm/outsourcing`, `/tprm/bcp-campaigns`,
`/tprm/attestations/:token/submit` (public), `/scm/dashboard`, `/fourth-party`,
`/contract-clauses`, `/portal/vendor` (public portal).

### 3.7 Sustainability / ESG — `module/sust` (+`/finance`, `/ghg`, `/capa`, `/vendor`) · schema `sust` · API `/api/sustainability/**`, `/api/v1/sustainability/**`
- **GHG inventory** (`/api/sustainability/ghg`): Scopes 1/2/3, the GHG Protocol's
  15 Scope-3 categories (`scope3_category`, scope-3-only validation, per-category
  breakdown + coverage %), versioned emission-factor library, unit registry, CSV
  import, lifecycle DRAFT→SUBMITTED→VERIFIED→APPROVED→ARCHIVED with the Rule-2 gate
  (verification requires attached evidence).
- **Metrics & readings** (`esg_metric` + `esg_metric_reading`), ESG dashboards.
- **Disclosure registers**: CMA (article-anchored) and TCFD — SoD approval +
  content-lock after approval; official library seeds.
- **IFRS S1/S2**: 14-requirement readiness checklist with coverage %; persisted,
  locked, exportable S2 report.
- **Assurance lineage (ISAE 3410)**: one view over the 5 assurable object types
  (metrics, GHG records, CMA, TCFD, SGI) grading data→evidence→verification.
- **Climate**: scenario register with computed expected loss, portfolio overlay.
- **CSRD double materiality**, **financed emissions (PCAF)**, **SGI commitments**.
- **Auto-CAPA engine** (`sust.corrective_action`): idempotent CAPAs raised by every
  gap type (SGI, CMA, TCFD, evidence rejection, vendor ESG, GHG, climate,
  annex gap, ESG target off-track, permit expiry/violation).
- **Green finance** (`/api/v1/sustainability/finance`): instrument register
  (GREEN_SUKUK / GREEN_BOND / SUSTAINABILITY_SUKUK / GREEN_LOAN / SLL) with
  lifecycle DRAFT→SHARIAH_REVIEW→APPROVED→ISSUED→REPORTING→MATURED and the Shariah
  gate; eligible-project register; use-of-proceeds allocations (maker-checker,
  over-allocation hard block incl. pending, netProceeds = amount − costs); impact
  attribution math; allocation & impact reports with frozen figures, maker-checker
  publish, per-report XLSX/DOCX export; branded evidence ZIP.
- **Green portfolio** (`/api/v1/sustainability/portfolio`): ICMA/CCE taxonomy,
  asset tagging with SoD decide, **Green Asset Ratio**, weighted PCAF data quality,
  carbon credits purchase/retire (certificate + purpose mandatory, retirement
  capped at a **tenant-configurable %** of disclosed gross reduction across active
  targets — `organisation_settings.carbon_offset_cap_pct`, default 10).
- **Regulatory horizon** (`/api/v1/sustainability/horizon`): ESG targets +
  trajectory (piecewise milestones, 5% direction-aware tolerance, OFF_TRACK sweep →
  auto-CAPA), IFRS checklist, NCEC permits + violations (auto-CAPA) + expiry sweep,
  EU CBAM lines + summary, assurance lineage.
**Screens:** `/esg` (tabbed: dashboard, metrics, GHG w/ scope-3 category, disclosures,
materiality, climate, vendor ESG, targets & transition, IFRS S1/S2, env compliance
incl. CBAM, assurance lineage, settings incl. offset cap), `/green-finance`
(Instruments / Use of Proceeds / Reports / Portfolio / Credits), `/esg/tcfd-scenarios`,
`/esg/financed-emissions`, `/esg/climate-report`.

### 3.8 Access Governance (IAM) — `module/iam` (+`/governance`) · schema `iam` · API `/api/v1/iam/**`
Runtime role catalogue + permission-matrix editor (structural rules APPROVE⇒READ,
DELETE⇒WRITE), per-user effective access, maker-checker access requests (approver ≠
requester ≠ beneficiary; grants only on approval; engine-visible), time-bound
assignments + hourly expiry sweep, JML joiner/mover/leaver hooks (MOVER → targeted
mini-review campaign), SoD engine (8-rule pack, preventive 409 at approval +
GRC_ADMINISTRATOR override recorded, nightly detective scan, dispositions),
recertification campaigns (PRIVILEGED/MODULE/ALL scope, Hijri-stamped windows,
CERTIFY/REVOKE/MODIFY, self-review blocked, cadence auto-scheduler), break-glass
emergency access (EMERGENCY_RESPONDER, gated activation, ≤24 h, mandatory post-hoc
review by someone else), access analytics, SAMA/NCA access-evidence ZIP.
**Screens:** `/access-governance` (Roles & Matrix / Requests / Campaigns / SoD /
Break-glass / Analytics), `/sso`, `/sso/nafath`, `/account/sessions`.

### 3.9 Platform services — `module/platform`, `module/workflow`, `module/evidence`, `module/reporting`, `module/admin`, `module/dashboard`, `module/incident`, `module/intake`, `module/ndi`, `module/ai`, `module/auth`
- **Workflow engine** (`workflow.requests`): approval / review / acknowledgement /
  broadcast / report-distribution requests; actor selectors (user/role/committee
  with fan-out); ALL/ANY/QUORUM/MAJORITY completion; SLA + multi-tier escalation;
  conditional routing; distribution lists; tokenized EXTERNAL delivery + public
  tracker; adopted by all 14 record modules (record-anchored panels).
  Screens: `/approvals`, `/attestations`, `/signatures`, `/workflow-versions`.
- **Evidence subsystem**: generic upload w/ SHA-256, lifecycle
  DRAFT→PENDING_REVIEW→APPROVED/REJECTED (four-eyes), typed links into modules,
  evidence requests with SLA, rejection auto-opens module gaps. Screens:
  `/evidence`, `/evidence-chain`, `/file-upload`.
- **Exports/reporting**: branded PDF (Amiri, RTL), XLSX (styler), DOCX/PPTX
  packets, regulator/audit ZIPs with hash manifests, Export History
  (`/export-history`), board pack (`/board-pack`), executive dashboard.
- **Audit ledger**: `platform.audit_log`, before/after JSON, every export logged;
  screen `/audit-log`.
- **Admin/platform**: users, branding (white-label `platform.brand_theme`),
  licensing, committees (+catalog/forms), notifications (channels/outbox/dispatch),
  connectors (`/connectors`), intake inbox + public intake, NDI governance,
  knowledge base, onboarding, AI suggestions (`/controls/ai-mapper`), issues,
  incidents + RCA.

### 3.10 Change Enablement & CAB — `module/change_mgmt` (+ `module/change_adapters`) · schema `change_mgmt` · API `/api/v1/changes/**`
ITIL 4 change enablement with the CAB as a governed body rather than a status name.
**16-state lifecycle** — `DRAFT → SUBMITTED → IMPACT_ASSESSED → CAB_PENDING →
CAB_APPROVED | CAB_REJECTED | CAB_DEFERRED | MORE_INFO_REQUIRED → SCHEDULED →
IMPLEMENTING → IMPLEMENTED | ROLLED_BACK → POST_REVIEW → CLOSED`, plus
`EMERGENCY_APPROVED` and `CANCELLED`. `change_authorisation_matrix` maps *type × risk*
to a gate (`AUTO` / `CAB` / `ECAB`) and a minimum approval count; the gate resolves to a
real committee per tenant through `admin.committee_gate_map`, over the generic committee
engine (`module/admin/committee`) rather than a second one. Peer modules are reached
only through ports in `spi/` (`CommitteePort`, `EvidencePort`, `CapaPort`,
`IncidentPort`) whose adapters live outside both modules in `module/change_adapters`.

**What is server-enforced (all 409s — a refusal here is the feature working):**
- **No authority, no decision.** A `CAB`/`ECAB` gate whose committee is unmapped, or
  mapped to a body with no voting seats, refuses the transition and names what is
  missing. There is no default CAB (ADR-017 D-2).
- **A decision must be recorded by someone entitled to take it.** All five decision
  targets stamp author and timestamp. The requester/owner cannot cast a CAB vote on
  their own change, cannot ratify it, and cannot waive its conditions — resolved on user
  id, with name comparison only as a legacy fallback. ⚠ One residual: a raiser holding
  `CHANGE:APPROVE` can still take the `DRAFT → EMERGENCY_APPROVED` hop on their own
  change; mandatory ratification *by someone else* is what catches that, not the hop.
- **Conditions.** A CAB may approve subject to conditions (`change_condition`). Imposing
  and waiving need `CHANGE:APPROVE`; a waiver needs a stated reason and may not come
  from the requester or owner. `SCHEDULED → IMPLEMENTING` refuses while any condition
  is open.
- **Evidence.** Recording a condition satisfied requires an evidence item the evidence
  subsystem has **APPROVED** — not merely uploaded, and not expired. The gate fails
  closed if the evidence module is absent; reads fail open.
- **Emergency is not a bypass.** `EMERGENCY_APPROVED` stamps a ratification clock
  (`ck_change_ratification_clock` makes a pending state without a due date
  unrepresentable), swept daily at 01:55, escalating to the CISO with an audit-ledger
  event on breach.
- **Pre-approval is a record, not a label.** A `STANDARD` change is "pre-approved" only if it
  cites a **change model** — a named procedure in `change_mgmt.change_model` with an authorising
  person, a moment and an **expiry date** (`ck_cmodel_preapproval` makes any other combination
  unrepresentable). Authoring a model is `CHANGE:WRITE` and produces a `DRAFT`; granting the
  pre-approval is `CHANGE:APPROVE` and **may not come from the author**; an `APPROVED` model cannot
  be edited in place. An **expired** pre-approval authorises nothing, and a change that cites a
  model then deviates from it is not backed by it. Whether a cell *requires* a model is opt-in per
  tenant and per cell (`change_authorisation_matrix.requires_model`, default off — 5 STANDARD
  changes exist in each database and 3 are in flight, so a default-on flag would refuse work
  already under way).
- **Closure needs a retrospective.** A `NORMAL`/`EMERGENCY` change cannot reach `CLOSED`
  without a recorded PIR summary (`STANDARD` is exempt — it is pre-approved, low-risk
  work). And a PIR that records objectives *not* met cannot close without a follow-up
  action, which is raised as a tracked CAPA in the platform's universal tracker.
- **Freeze windows.** `→ SCHEDULED` refuses when the change's window overlaps an active
  freeze whose scope covers it (`ALL`, or a matching `CATEGORY`); an `EMERGENCY` change
  is exempt only where the window itself sets `allow_emergency`. Collisions with other
  scheduled changes are **detected and reported** (`GET /{id}/conflicts`), not refused —
  overlapping work is often legitimate, so the platform surfaces it for the CAB rather
  than deciding for it.

**Records for an auditor:** change register (`/api/v1/reporting/change-register.{pdf,xlsx,docx}`,
`CHANGE:EXPORT` **and** `CHANGE:READ`), per-change CAB pack
(`/api/v1/reporting/change/{id}/cab-pack.pdf`), the **CAB decision register**
(`/api/v1/reporting/change-decision-register.{pdf,xlsx}` — every change past a decision
point, and see below), and the committee's own agenda pack and minutes
(`/api/v1/reporting/committee/meeting/{id}/{agenda,minutes}.pdf`, guarded `ADMIN` because
the engine is generic). All bilingual with real Arabic RTL shaping (`?lang=ar`), and every
export is written to the audit ledger.

**The decision register states what it does not know.** Each row carries two independent
axes: whether the decision itself is `RECORDED` (the change carries a decision timestamp
or author) or `INFERRED_FROM_STATE` (a change in `IMPLEMENTING` must have been approved —
the state proves an act happened without saying when or by whom), and what evidences it —
a carried committee `RESOLUTION`, legacy `RECORDED_VOTES` with named approvers, or `NONE`
(correct for an `AUTO`-gated change: no committee was ever asked). The header publishes how
many rows are attributed, because *"12 decisions"* on its own invites the reader to assume
twelve recorded acts. Measured 2026-08-04: **11 changes past a decision point, 1 naming its
author** — so a register printing only stamped decisions would hide ten real governance
events, and one attributing all eleven would invent ten authors.

**Analytics** — `GET /api/v1/changes/analytics` carries `kpis`: 19 ITIL/ISO metrics, each
with its numerator, denominator, population, **basis** (how much of the population is
covered) and **provenance** (whether the covered rows were measured or asserted). Seven
report `NOT_MEASURABLE` with a named blocker rather than a figure — reading a `—` as a
gap and a `0%` as a measurement is the point, not a limitation.

**Screens:** `/changes` with tabs `register`, `kanban`, `workflow`, `analytics`,
`integrations`, `governance` (calendar + freeze windows), `comms`.

⚠ **Operational prerequisite.** Because the authority model fails closed, a fresh tenant
must constitute its CAB and map the gates before any `CAB`/`ECAB` change can be approved
— otherwise every such approval is refused, correctly, with a message naming the missing
configuration. Step-by-step: **`docs/change-manager/CAB_OPERATING_RUNBOOK.md`**.

**The unauthorised-change register** (`GET /api/v1/changes/unauthorised-register`,
`CHANGE:READ`) reconciles every change past its authorisation point against the authorisation
its `(type × risk)` cell required, and closes G-11. Findings are **derived on every read** from
the matrix, the approval trail and the status, so they cannot drift from the rows; only the
investigator's outcome is stored (`POST .../{changeId}/{reasonCode}/dispose`, `CHANGE:APPROVE`,
V355).

Two distinctions carry the feature:

- **An authority gap is not the same as an unattributed authorisation.** Six of dev's eleven
  findings satisfy their cell's `min_approvals` in full and lack only `cab_decision_at` (F2-103's
  legacy). Those are reported as `UNATTRIBUTED_AUTHORISATION` at MEDIUM and are excluded from the
  headline count — folding them in would report eight unauthorised changes where three is the truth.
- **Authorisation has two routes.** A referred change is authorised by the committee's carried
  resolution, which writes no `change_approvals` rows at all (Track D). The reconciliation asks the
  engine through `CommitteePort.decisionApproves`, because a check that read only the vote table
  would flag the platform's *preferred* route as the defect.

A disposition **annotates** a finding and never erases one: there is no `CLOSED` state, and the
`unauthorisedChangeCount` KPI counts disposed gaps too, so a clean dashboard cannot be reached by
signing findings off.

⚠ **What it cannot see is published in the payload and on screen** (`undetectable`): a production
change that was never ticketed here — that needs a configuration-drift feed and this deployment has
none (measured: no CMDB, no baseline table, no tables in the `integrations` schema) — and
implementation inside a freeze window, because `implementation_started` is NULL on every change, so
the predicate would compare against NULL and report every change compliant forever (A17-D3's
`ratification_due_at` defect, avoided by declaring it).

**The forward schedule of change** (`GET /api/v1/changes/forward-schedule`, `CHANGE:READ`)
closes G-8, and closes it in three parts because G-8 was three gaps:

- **Publication.** `POST /schedule-publications` (`CHANGE:APPROVE`) snapshots the schedule and
  digests it with SHA-256, so "what did we tell the business on the 4th?" has an answer today's
  rows cannot contradict. Following `board_pack.packs` (V26), whose own migration explains why a
  publication captures its content. The row is **immutable** — no `updated_at`, no `status`, and
  V356 THROWs if either appears: superseding a published schedule means publishing again.
  `GET /schedule-publications/{id}` returns the snapshot and re-verifies the digest.
- **The PSO** (`POST /{id}/outage`, `CHANGE:WRITE`). `ck_change_pso` refuses `outage_expected = 1`
  without a positive duration **and** named services — "there will be an outage" with neither is a
  rumour, not a declaration. Withdrawing clears both, so the FSC can never show a duration for a
  change that no longer claims an outage.
- **Schedule-wide collisions** (`GET /schedule-collisions`). `GET /{id}/conflicts` has existed since
  CH-17 but needs you to already suspect a specific change, and you cannot find a collision you have
  not guessed at — that is what "collision detection unpublished" meant. Three reasons:
  `WINDOW_OVERLAP`, `OUTAGE_OVERLAP` (two simultaneous declared outages — a business-visible incident
  being scheduled on purpose) and `INSIDE_FREEZE_WINDOW`, which honours `allow_emergency`.

**`scheduled_start`/`scheduled_end` were not re-modelled.** The uplift plan specifies a
`change_schedule_entry` table carrying the window, but `change_requests` already holds it on 15 of 17
dev rows — a second copy would give the platform two answers to "when is this change happening?", and
the FSC and the change register could each cite themselves.

**And a freeze violation is detectable here where S8f had to declare it undetectable.** The
unauthorised-change register cannot ask "was this *implemented* during a freeze" because
`implementation_started` is NULL on every change. This asks "is it *scheduled* inside one", from a
column populated on 15 of 17 — a different and more useful question, because it can still be acted
on. What stays blind is published as `unscheduledCount`: a forward-status change with no window
cannot collide with anything, so an empty collision list does not cover it.

**Not built yet** (tracked as CE-10, CE-13 in
`CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md`): workflow-engine adoption as the 15th
module; and the CAB workspace as its own frontend surface.

---

## 4. Users, roles & demo tenants

### 4.1 Seeded logins (dev/demo — **rotate all of these on any real deployment**)

| Username | Password | Role(s) | Use it for |
|---|---|---|---|
| `admin` | `Admin@12345` | PLATFORM_ADMIN | everything (bypasses module guards in code) |
| `rmanager` | `Risk@12345` | RISK_MANAGER | risk flows |
| `auditor` | `Admin@12345` | INTERNAL_AUDITOR | read-only assurance |
| `compliance` | `Admin@12345` | COMPLIANCE_OFFICER + GRC_ADMINISTRATOR | compliance + SoD overrides |
| `so_officer` | `Admin@12345` | CISO + SUSTAINABILITY_OFFICER | ESG/green-finance + cyber approvals; the standard second approver in 4-eyes tests |
| `ranalyst` | `Admin@12345` | analyst persona | limited writes |
| `viewer` | `Viewer@12345` | read-only | dashboards |

DB account (dev): `sa / tss.123`. Production uses its own SQL login (e.g. `grc_app`)
configured in `external.properties`. Login lockout: 5 attempts / 30 min in prod
(effectively disabled in dev).

**Four-eyes reality check:** many actions *cannot* be completed by the same account
that initiated them (allocation approve, tag decide, request decide, campaign
self-certify, break-glass self-review, GHG verify of own unevidenced record…). When
demoing alone, initiate as `admin` and decide as `so_officer` (or vice-versa) — a
409 with a clear message when using one account is the SoD design working, not a bug.

### 4.2 Demo tenants (`seed_real_data.py`, idempotent, ~450 rows)

| Tenant | org_id | Theme |
|---|---:|---|
| TSS GRC | 1 | Saudi bank — T24/SADAD/Exadata/Murex; PDPL articles 4/5/6/20/29; SAMA/NCA/SDAIA/CMA content |
| Aramco Trading | 2 | Energy + trading — Honeywell DCS, pipeline SCADA, Eka TRM |
| STC Bank (demo) | 3 | Telco-banking — USSD *900#, SS7/SIGTRAN, HSM cluster, voice biometrics |

All demo/testing rows created during verification are tagged `[REVIEW-TEST]` and are
cleaned with `CHARINDEX` deletes; the dev DB currently carries **zero** residue.

---
## 5. Technology stack & repository layout

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5.x, **Java 17 (pinned — Lombok breaks on newer JDKs)**, Spring Security 6 |
| Auth | JJWT — stateless JWT (Bearer access token + HttpOnly refresh cookie; transparent refresh-and-retry in the FE) |
| Database | SQL Server 2019+ (Express in dev), UTF-8 collation `Latin1_General_100_CI_AS_SC_UTF8` |
| ORM | Hibernate 6 / Spring Data JPA (`ddl-auto=none` — schema owned by migrations) |
| Migrations | Flyway (V1→V295 packaged, 285 files; **OFF in dev**, ON in prod with baseline support) |
| Cache | Caffeine L1 (`maximumSize=1000, expireAfterWrite=300s`) |
| Exports | Apache POI (XLSX/PPTX/DOCX), OpenPDF (Arabic RTL PDF, bundled Amiri OFL font) |
| API docs | SpringDoc OpenAPI — Swagger UI in dev only (`/swagger-ui.html`) |
| Frontend | **tss-grc-ui-v2** — React 18 + Vite + TypeScript, Recharts, STOMP/SockJS live panels, lazy-loaded AR/EN dictionaries, full RTL |
| Legacy frontend | `tss-grc-ui/` — original static HTML/Bootstrap build (superseded; kept for reference) |
| Observability | Spring Actuator on a separate management port, Prometheus metrics, optional Sentry |
| Scripting | Python 3 (`py` launcher) + pyodbc for DB bootstrap/seed/verification harnesses |

### 5.1 Repository layout (dev machine `D:\AYMAN\GRC`)

```
D:\AYMAN\GRC                          ← repo root (github.com/Ayman-Server/Claude-GRC-2)
├── tss-grc-backend/                  ← Spring Boot REST API
│   ├── pom.xml
│   ├── external.properties.template  ← the production config template (committed)
│   └── src/main/
│       ├── java/com/tss/grc/
│       │   ├── GrcApplication.java
│       │   ├── config/               ← SecurityConfig, CorsConfig, CacheConfig, …
│       │   ├── security/             ← JwtTokenProvider, JwtAuthenticationFilter,
│       │   │                            ModulePermissionEvaluator, GrcUserDetails
│       │   ├── common/               ← BaseEntity, TenantContext, exceptions
│       │   └── module/<name>/        ← controller / dto / entity / repository / service
│       └── resources/
│           ├── application.yml           ← base (imports optional:file:./external.properties)
│           ├── application-dev.yml       ← Swagger on, Flyway off, port 8084
│           ├── application-prod.yml      ← hardened, placeholder-driven
│           └── db/migration/sqlserver/   ← V1__ … V295__ (285 files)
├── tss-grc-ui-v2/                    ← React frontend
│   ├── vite.config.ts                ← dev :5173, proxies /api + /ws → :8084
│   └── src/ {App.tsx routes, pages/, components/, lib/ (api.ts, i18n, toast), translations/}
├── tss-grc-ui/                       ← legacy static frontend
├── setup_*.py, seed_real_data.py, fix_arabic_db.py   ← dev-DB bootstrap + demo data
├── deploy/                           ← staged cloud artifacts (gitignored): jar,
│   ui-dist/, external.properties, nginx.conf, DB .bak, run-backend.cmd
├── DEPLOYMENT.md                     ← Windows Server go-live runbook (WinSW-based)
├── PROJECT_CHRONICLE.md              ← this document
├── LOCAL_SETUP.md                    ← dev-machine bring-up notes
├── reports/audit-report.html         ← the 7-phase platform audit (interactive)
├── audit/                            ← audit raw data, harnesses
├── *_PLAN.md                         ← per-program review/remediation plans (each
│                                        carries a §0 implementation-status tracker)
└── CLAUDE.md                         ← working conventions for AI-assisted sessions
```

Backend modules under `module/`: `admin`, `ai`, `auth`, `bcm`, `compliance`, `cyber`,
`dashboard`, `datagov`, `evidence`, `iam` (+`iam/governance`), `incident`, `intake`,
`ndi`, `platform`, `privacy`, `reporting`, `risk`, `scm`, `socops`,
`sust` (+`sust/finance`, `sust/ghg`, `sust/capa`, `sust/vendor`), `tprm`, `workflow`.

### 5.2 Production server layout (as deployed, `D:\Deployment\tss-grc`)

```
D:\Deployment\tss-grc\
├── app\
│   ├── tss-grc-platform-0.1.0-SNAPSHOT.jar
│   ├── external.properties           ← ALL runtime config (secrets — ACL-restricted)
│   ├── WinSW.NET4.exe + WinSW.NET4.xml   ← Windows service wrapper for the jar
│   └── WinSW.NET4*.log               ← service wrapper logs
├── nginx\
│   ├── nginx.exe, conf\nginx.conf    ← serves the UI + proxies /api and /ws
│   ├── nginx-service.exe + nginx-service.xml  ← WinSW wrapper for nginx
│   └── logs\ (access.log, error.log)
├── ui\                               ← the built SPA (tss-grc-ui-v2/dist contents)
└── logs\                             ← application.log (path set in external.properties)
```

Note the port split on this server: the backend listens on **:8090** with Actuator
management on **:8091** (values in `external.properties`); nginx must proxy to the
same port (`proxy_pass http://127.0.0.1:8090;` in both the `/api/` and `/ws` blocks).
The repo's staged `deploy/nginx.conf` template says 8084 — always align it with
`SERVER_PORT`.

---

## 6. Core architecture patterns

- **Multi-tenancy** — `TenantContext` (thread-local) carries `organisationId`, set by
  `JwtAuthenticationFilter` from JWT claims. **Every repository query filters by
  `organisation_id`** (developer responsibility — no global Hibernate filter).
  Defence-in-depth: `TenantWriteGuardInterceptor` (Hibernate flush interceptor, ON in
  prod) blocks cross-tenant writes unless explicitly allowed.
- **Authorization** — method-level `@PreAuthorize("hasPermission('MODULE','ACTION')")`
  resolved by `ModulePermissionEvaluator` into `MODULE_ACTION` authorities;
  `PLATFORM_ADMIN` bypasses in code. A **ratchet test** pins the count of unguarded
  write endpoints — it may only decrease (history: 422 → 61 → **62** with two
  documented public-endpoint exceptions).
- **Soft deletes** — entities carry `is_deleted` + `@Where(clause = "is_deleted = 0")`;
  native SQL must filter `is_deleted = 0`. Exception: `iam.user_roles` join rows are
  hard-deleted on revocation (`@ManyToMany` ignores join-table soft-delete flags);
  history lives in `access_requests` + the audit ledger.
- **Bilingual by construction** — `*_en`/`*_ar` columns everywhere (Arabic mandatory
  on names); locale dictionaries lazy-loaded; RTL run-direction threaded through
  every PDF/XLSX/PPTX generator; Hijri stamps via a shared calendar helper.
- **Workflow request engine** — one generic engine for approvals/reviews/acks/
  broadcasts/report distribution, adopted by all 14 record modules with
  record-anchored panels (see §3.9).
- **Evidence subsystem** — one generic engine bridged into modules via typed links;
  SHA-256 on upload; four-eyes review; rejection auto-opens module gaps.
- **Auto-CAPA pattern** — `sust.corrective_action` with `(source_type, source_ref)`
  idempotency; the `ck_ca_source` CHECK whitelists source types — **extend it for
  every new source or inserts fail with 500**.
- **Export stack** — shared `PdfTheme` + `PdfFonts` (Amiri bundled) + `XlsxStyler`
  + `TabularExportService` (generic XLSX/DOCX from sheet defs) + `ExportNaming`
  (`[Module]_[Type]_[LANG]_[YYYYMMDD]_vN.ext`) + `BrandThemeService` (white-label)
  + `ExportAuditInterceptor` (every download logged as `action=EXPORT`). Evidence
  ZIPs carry `manifest.json` with SHA-256 per entry.
- **Audit ledger** — `platform.audit_log` with before/after JSON, severities
  INFO/WARN/ERROR, off-request-safe writes. Domain events (F8) drive automation
  rules and record creators.
- **Schedulers** (all off-request-safe, org-explicit): assignment expiry (hourly),
  SoD detective scan (nightly 02:30 UTC), break-glass expiry (30-min), campaign
  cadence scheduler, evidence expiry, vuln/CAPA/workflow SLA clocks, vendor-ESG
  escalation, permit/trajectory sweeps (also exposed as buttons), DB-size monitor
  (SQL Express 10 GB cap).
- **Error contract** — RFC-7807 ProblemDetail everywhere; bean-validation 400s carry
  a per-field `errors` map. The FE folds those fields into every toast
  (`problemMessage()` in `api.ts`), so a failed action always names the field or
  rule (e.g. *"Validation Failed — displayNameAr: Field must not be blank"*).

---

## 7. Security & access-control model (as of V295)

- **Permission catalogue:** **94 permissions** = 24 modules × actions
  `READ · WRITE · APPROVE · DELETE` (+`TRIAGE` where queues exist) **+ `EXPORT`**
  (rolled out READ-parity so behaviour is unchanged until a tenant tightens) + the
  dedicated `IAM_BREAK_GLASS` activation permission.
- **Personas:** ~55 bilingual roles per tenant (V272 matrix); system roles are
  non-deletable templates; structural rules enforced in the matrix editor.
- **Authentication:** JWT access token (1 h dev / 15 min prod) in `Authorization:
  Bearer`, refresh token (7 d) in an HttpOnly cookie; the FE transparently refreshes
  on 401 and retries once (single-flight, so parallel 401s don't rotate the cookie
  repeatedly); refresh failure bounces to `/login?next=…`. Sessions are server-side
  revocable (`/account/sessions`). Secrets: `JWT_SECRET` ≥32 chars, boot-fails on
  placeholder values; optional `JWT_SECRET_PREVIOUS` for 24 h rotation overlap.
- **Access lifecycle (`iam/governance`):** described functionally in §3.8. Key
  guarantees: a grant reaches `iam.user_roles` **only** through an approved request
  (or seeded matrix); every grant can be time-bound; every grant is recertifiable;
  every privileged path (break-glass) is post-hoc-reviewed by someone else; SoD is
  enforced both preventively (409 at approval, override = GRC_ADMINISTRATOR +
  documented justification, still recorded as an OVERRIDE violation) and
  detectively (nightly scan). PLATFORM_ADMIN is excluded from SoD scans by design.
- **Designed four-eyes / SoD gates** (the complete list — each returns 409 with a
  clear message; these are features, not bugs):

| Action | Rule |
|---|---|
| UoP allocation approve | proposer ≠ approver |
| Portfolio tag decide | proposer ≠ decider |
| Access request approve/reject | requester ≠ decider ≠ beneficiary |
| Campaign item decide | reviewer cannot certify their own access |
| Break-glass review | activator cannot review their own event |
| SoD violation disposition | violator cannot disposition their own violation |
| Allocation report publish | maker-checker |
| CMA/TCFD approval | SoD + content-lock after approval |
| GHG verify | requires attached evidence (Rule 2) |
| Sukuk SHARIAH_REVIEW → APPROVED | requires Shariah board approval reference |
| Credit retirement | ≤ tenant offset cap % of disclosed gross reduction; no active reduction target ⇒ no retirement |

- **Hardening history:** all 4 audit Criticals + 52 Highs fixed; mass-assignment
  closed via create-guards; page-size clamps global + 65 endpoints; public intake
  tracker PII-free + rate-limited; Splunk HMAC fail-closed; Trivy fat-jar scan
  0 HIGH/CRITICAL; CSV formula-injection guards; export DoS caps.

---

## 8. Database

- One SQL Server database **`TSS_GRC`**, UTF-8 collation, **50+ schemas**
  (`platform`, `iam`, `risk`, `compliance`, `cyber`, `socops`, `bcm`, `privacy`,
  `datagov`, `sust`, `tprm`, `scm`, `workflow`, `evidence`, `ndi`, `ethics`,
  lifecycle schemas `*_lc`, …).
- **Migrations V1→V295** packaged in the jar (285 files under
  `db/migration/sqlserver/`). V1/V2 recreate the baseline so a *fresh* DB can be
  built by Flyway alone. Next free number: **V296**.
- **Dev:** Flyway **OFF** (`application-dev.yml`) — the dev DB is Python-bootstrapped
  (`setup_*.py`), and every migration from V225 onward was applied **by hand via
  pyodbc** as authored (each written idempotent and applied twice to prove it).
- **Prod:** Flyway **ON** with `FLYWAY_DEFAULT_SCHEMA=platform` (the emptiness check
  must look at a populated schema — checking default `dbo` wrongly re-runs V1/V2 on a
  restored DB) and `FLYWAY_BASELINE_VERSION=292` for the restored database, so only
  future migrations (V293+) auto-apply at startup. `flyway.clean` permanently
  disabled — never run `mvn flyway:clean`.
- **Migration map by program:** V1/V2 baseline · V3–V10 compliance/incident/tprm/
  evidence · …sprint history… · V225–V230 performance indexes · V231–V234 compliance
  remediation · V235–V240 BCM · V241–V247 ERM (incl. KRI consolidation + legacy drop)
  · V248–V257 ESG remediation · V259–V268 Cyber+SOCOps · V269–V276 workflow engine
  (+V272 RBAC catalogue) · V277/V278 forms · V279 brand theme · V280–V284 RBAC
  governance P0–P3 · V285–V292 green finance G0–G4 · V293 green completion ·
  V294 RBAC completion · **V295 roadmap closure** (scope3_category + configurable
  offset cap + REG-1 SOCPA verification wording).
- **Backups:** production restore verified from `TSS_GRC_2026-07-11.bak` (127 MB,
  logical files `TSS_GRC`/`TSS_GRC_log`); nightly `BACKUP DATABASE` via Task
  Scheduler recommended (see §12.4).

---
## 9. Environments — dev machine & production server

### 9.1 Local development (`D:\AYMAN\GRC`, Windows 11)

| Component | Where / how |
|---|---|
| SQL Server | Express instance `MSSQL$SQLEXPRESS` on `localhost:1433` (the default MSSQLSERVER instance is set **Manual** so it can't steal 1433 after a reboot); `sa/tss.123` |
| Backend | jar or `mvn spring-boot:run`, **:8084**, dev profile; Swagger at `/swagger-ui.html`; health `/actuator/health` |
| Frontend | Vite dev server **:5173** (`npm run dev` in `tss-grc-ui-v2`), proxies `/api` + `/ws` → :8084; LAN-reachable (host `0.0.0.0`, firewall rule present, e.g. `192.168.8.249:5173`) |
| Toolchain | **JDK 17 at `C:\Program Files\Java\jdk-17`** (must set `JAVA_HOME` — fresh shells default to a JDK 11), Maven 3.9, Node 24, Python 3 via `py` (+pyodbc, ODBC Driver 17) |

Required env vars for the backend (dev refuses to boot without real values):

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17"          # Git Bash
JWT_SECRET="tss-grc-dev-jwt-secret-0123456789-abcdefghij-KLMNOP"
DB_PASSWORD="tss.123"
```

Everyday commands:

```bash
# backend — run from source
cd tss-grc-backend
JWT_SECRET=... DB_PASSWORD=tss.123 mvn spring-boot:run -Dspring.profiles.active=dev

# backend — full build with tests (2,249 tests; ~10–15 min)
mvn -o clean package            # then: java -jar target/tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
mvn test -Dtest=ClassName#method

# frontend
cd tss-grc-ui-v2
npm run dev                     # :5173
npx tsc --noEmit                # typecheck
npx vitest run                  # unit tests (43)
npx vite build                  # production bundle → dist/

# dev DB bootstrap (fresh machine)
sqlcmd -S localhost -U sa -P tss.123 -Q "CREATE DATABASE TSS_GRC COLLATE Latin1_General_100_CI_AS_SC_UTF8;"
py setup_cyber_db.py && py setup_bcm_db.py && py setup_committees_db.py && py fix_arabic_db.py
py seed_real_data.py            # idempotent demo data
```

Gotcha: the backend and Vite hold ports across shells — kill by port
(`netstat -ano | findstr :8084`, then `taskkill /F /PID …`; DevTools spawns 2 java
PIDs). Stop the backend before `mvn clean` (jar file lock).

### 9.2 Production (Windows Server, IP-only HTTP for now)

Topology:

```
Browser ──► nginx :80          serves D:\Deployment\tss-grc\ui (SPA fallback)
              ├── /api/** ──► Spring Boot 127.0.0.1:8090   (prod profile, external.properties)
              └── /ws    ──► same (WebSocket upgrade for live panels)
Spring Boot ──► SQL Server :1433 (TSS_GRC restored from .bak, Flyway baseline 292)
Actuator    ──► 127.0.0.1:8091 (loopback only — health/metrics)
```

Both processes run as Windows services via **WinSW** (`TssGrcBackend`,
`TssGrcNginx`), auto-start, auto-restart on failure. Only port 80 is exposed by the
firewall. The SPA and the API share one origin (nginx), so no CORS configuration is
needed — never point the browser at :8090 directly.

---

## 10. Installation guide — fresh production server (Windows)

The authoritative step-by-step runbook is **`DEPLOYMENT.md`**; this section is the
condensed but complete sequence. Everything below runs in an **elevated PowerShell**.

**10.1 Prerequisites**
1. **Java 17** (Temurin): `winget install EclipseAdoptium.Temurin.17.JDK` — or
   install the MSI manually; verify `java -version` says 17.
2. **SQL Server 2019+** (Express OK): enable **TCP/IP** in Configuration Manager,
   restart the instance, enable **Mixed authentication**, create an app login
   (e.g. `grc_app`) with `db_owner` on `TSS_GRC`.
3. **nginx for Windows**: download the stable zip from nginx.org, extract so that
   `<nginx-dir>\nginx.exe` exists (watch for the doubled `nginx-1.2x.x` folder).
4. **WinSW**: one exe from github.com/winsw/winsw/releases (or reuse
   `WinSW.NET4.exe`); it gets copied+renamed per service — no installer, no PATH.

**10.2 Restore the database**

```powershell
sqlcmd -S localhost -U sa -P <pw> -Q "RESTORE DATABASE TSS_GRC FROM DISK='D:\Deployment\TSS_GRC_2026-07-11.bak' WITH MOVE 'TSS_GRC' TO 'D:\Data\TSS_GRC.mdf', MOVE 'TSS_GRC_log' TO 'D:\Data\TSS_GRC_log.ldf'"
```

**10.3 Lay out the app** — create `D:\Deployment\tss-grc\{app,nginx,ui,logs}`; copy
the jar + `external.properties` into `app\`; copy the built UI (`ui-dist` contents)
into `ui\`; extract nginx into `nginx\` and overwrite `conf\nginx.conf` with the
shipped one.

**10.4 Configure `external.properties`** (in `app\`, next to the jar — §11):
set `JWT_SECRET` (64 random chars), `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`,
`SERVER_PORT` (e.g. 8090) + `MANAGEMENT_SERVER_PORT` (e.g. 8091 — **must differ**),
`FLYWAY_BASELINE_VERSION=292`, `LOGGING_FILE_NAME`, `FILE_STORAGE_PATH`. Lock the
file down: `icacls ...\external.properties /inheritance:r /grant:r "Administrators:F" "SYSTEM:F"`.

**10.5 Fix `nginx.conf` for this server** — `root D:/Deployment/tss-grc/ui;`
(forward slashes) and **both** `proxy_pass` lines (`/api/` and `/ws`) to
`http://127.0.0.1:<SERVER_PORT>;`.

**10.6 Backend service (WinSW)** — `app\WinSW.NET4.xml` (or `backend-service.xml`):

```xml
<service>
  <id>TssGrcBackend</id>
  <name>TSS GRC Backend</name>
  <executable>java</executable>
  <arguments>-jar D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod</arguments>
  <workingdirectory>D:\Deployment\tss-grc\app</workingdirectory>
  <startmode>Automatic</startmode>
  <logpath>D:\Deployment\tss-grc\logs</logpath>
  <log mode="roll"></log>
  <onfailure action="restart" delay="10 sec"/>
</service>
```

`workingdirectory` is load-bearing: `external.properties` resolves relative to it.
The `--spring.profiles.active=prod` argument is mandatory. Then
`.\WinSW.NET4.exe install` + `start`. First boot takes 60–90 s (Flyway baseline +
JPA warm-up).

**10.7 nginx service (WinSW)** — copy the WinSW exe to
`nginx\nginx-service.exe`, create `nginx-service.xml` beside it:

```xml
<service>
  <id>TssGrcNginx</id>
  <name>TSS GRC UI (nginx)</name>
  <executable>D:\Deployment\tss-grc\nginx\nginx.exe</executable>
  <startarguments>-p D:\Deployment\tss-grc\nginx</startarguments>
  <stopexecutable>D:\Deployment\tss-grc\nginx\nginx.exe</stopexecutable>
  <stoparguments>-p D:\Deployment\tss-grc\nginx -s stop</stoparguments>
  <startmode>Automatic</startmode>
  <stoptimeout>15 sec</stoptimeout>
  <logpath>D:\Deployment\tss-grc\nginx\logs</logpath>
  <log mode="roll"></log>
  <onfailure action="restart" delay="10 sec"/>
</service>
```

Test the config first (`.\nginx.exe -t -p <dir>`), make sure nothing else owns :80
(IIS: `Stop-Service W3SVC; Set-Service W3SVC -StartupType Manual`), then
`.\nginx-service.exe install` + `start`.

**10.8 Firewall + verification**

```powershell
New-NetFirewallRule -DisplayName "TSS GRC HTTP 80" -Direction Inbound -Protocol TCP -LocalPort 80 -Action Allow
```

Verify: `http://<server-ip>/` shows the login page → log in as `admin` → open a
module → press **F5** on a deep link (SPA fallback proves nginx config) → check an
API screen loads data (proves the proxy + backend + DB chain).

**10.9 Post go-live (immediately)** — rotate every seeded password (§4.1), decide
whether to drop demo tenants 2–3, schedule nightly DB backups (§12.4), and plan TLS
at nginx when a domain/certificate exists.

---

## 11. Configuration reference

### 11.1 The model

`application.yml` (packaged) declares `spring.config.import:
optional:file:./external.properties` — so **one plain-text file next to the jar
holds every runtime setting**. Edit + restart the service; no rebuild, no
environment variables. Two kinds of entries work:

1. **UPPER_CASE names** (the supported surface) — they feed `${PLACEHOLDER}`
   references inside the packaged `application*.yml`. These **always win**.
2. **Raw Spring keys** (e.g. `grc.cors.allowed-origins=`) — full override, **except**
   where the packaged prod profile pins the key (profile files beat base-level
   imports in Spring's precedence). Prefer the UPPER_CASE names.

Template: `tss-grc-backend/external.properties.template` (committed); the filled
file is gitignored and ACL-restricted (contains secrets).

### 11.2 Key reference

| Key | Meaning | Default / note |
|---|---|---|
| `JWT_SECRET` | token-signing secret, ≥32 chars | **required**; boot fails on `change-this`/`placeholder` values |
| `JWT_SECRET_PREVIOUS` | previous secret during rotation | optional, 24 h overlap |
| `JWT_ACCESS_EXPIRY_MS` / `JWT_REFRESH_EXPIRY_MS` | token lifetimes | 900000 (15 min) / 604800000 (7 d) |
| `DB_URL` | JDBC URL | keep `sendStringParametersAsUnicode=true` (Arabic) + `trustServerCertificate=true` |
| `DB_USERNAME` / `DB_PASSWORD` | SQL login | **required** |
| `FLYWAY_BASELINE_ON_MIGRATE` / `FLYWAY_BASELINE_VERSION` | restored-DB baseline | `true` / `292` — only V293+ auto-apply |
| `SERVER_PORT` | backend HTTP port | 8084 template; **8090 on the current server** — nginx `proxy_pass` must match |
| `SERVER_ADDRESS` | bind address | `127.0.0.1` (only nginx talks to it) |
| `SERVER_SSL_ENABLED` (+`SSL_KEYSTORE_*`) | app-level TLS | `false` (TLS terminates at nginx later) |
| `MANAGEMENT_SERVER_PORT` / `MANAGEMENT_SERVER_ADDRESS` | Actuator | must **differ** from SERVER_PORT (same-port = boot conflict); loopback |
| `LOGGING_FILE_NAME` | application log file | e.g. `D:/Deployment/tss-grc/logs/application.log` |
| `FILE_STORAGE_PATH` | evidence/upload storage root | back this folder up with the DB |
| `SMTP_*` + `NOTIFY_FROM_EMAIL` | mail relay | placeholders until a relay exists; notifications are in-app meanwhile |
| `LDAP_HEALTH_ENABLED` / `MAIL_HEALTH_ENABLED` | optional-infra health probes | **off** by default (a probed-but-absent server flips `/actuator/health` DOWN) |
| `HIBERNATE_DDL_AUTO` | schema validation | unset (=none); `validate` only after the smallint/bit `is_deleted` reconciliation |
| `SENTRY_DSN`, `SERVICENOW_*`, `SPLUNK_HEC_TOKEN`, `CROWDSTRIKE_*`, `TENABLE_*` | integrations | off/stub until keyed |
| `CORS_ALLOWED_ORIGINS` | extra origins | leave unset behind nginx (same-origin) |

### 11.3 Profiles

| Profile | Use | Highlights |
|---|---|---|
| `dev` | local development | Swagger ON, Flyway OFF, port 8084, permissive CORS (localhost:3000/5173/5500/8086/8087 + LAN), lockout effectively off, 1 h access tokens |
| `prod` | server | Swagger OFF, Flyway ON (baseline-aware), no error details in responses, lockout 5/30 min, 15 min tokens, tenant write-guard ON, everything parameterized via external.properties |

---

## 12. Operations manual

### 12.1 Start / stop / status (production)

```powershell
Get-Service TssGrcBackend, TssGrcNginx
Restart-Service TssGrcBackend          # ~60–90 s to healthy
Restart-Service TssGrcNginx
# or the WinSW exes: .\WinSW.NET4.exe status|start|stop|restart
```

Health: `curl http://127.0.0.1:<MANAGEMENT_SERVER_PORT>/actuator/health` on the
server → `{"status":"UP"}`.

### 12.2 Logs

| What | Where |
|---|---|
| Application (prod) | `LOGGING_FILE_NAME` (e.g. `D:\Deployment\tss-grc\logs\application.log`) |
| Service wrapper | `app\WinSW.NET4*.out.log` / `.err.log` (or `logs\` per XML `logpath`) |
| nginx | `nginx\logs\access.log`, `nginx\logs\error.log` |
| Dev backend | console or the redirect file used at launch (e.g. `.tmp/backend*.log`) |
| In-app audit | `/audit-log` screen (platform.audit_log) — business actions, exports, security events |

### 12.3 Updating the application

```powershell
# build on the dev machine:
#   backend: mvn -o clean package          → target\tss-grc-platform-0.1.0-SNAPSHOT.jar
#   frontend: npx vite build               → tss-grc-ui-v2\dist
Stop-Service TssGrcBackend
Copy-Item <new>.jar D:\Deployment\tss-grc\app\tss-grc-platform-0.1.0-SNAPSHOT.jar -Force
Remove-Item D:\Deployment\tss-grc\ui\* -Recurse -Force
Copy-Item <dist>\* D:\Deployment\tss-grc\ui\ -Recurse
Start-Service TssGrcBackend      # Flyway auto-applies any migration > baseline
```

UI-only changes don't need the backend restart — replace `ui\` and hard-refresh.
Rollback = restore the previous jar (+ DB backup if a migration must be undone —
migrations are forward-only, so prefer roll-forward fixes).

### 12.4 Backups

```powershell
sqlcmd -S localhost -U sa -P <pw> -Q "BACKUP DATABASE TSS_GRC TO DISK='D:\Backups\TSS_GRC_daily.bak' WITH INIT, COMPRESSION"
```

Schedule nightly via Task Scheduler; also back up `FILE_STORAGE_PATH` (uploaded
evidence binaries) and `external.properties`. SQL **Express** caps a database at
10 GB — the platform's DB-size monitor warns as it approaches; plan a Standard
upgrade when needed.

### 12.5 Scheduled jobs inside the app (no action needed, listed for awareness)
Hourly assignment expiry · nightly SoD detective scan (02:30 UTC) · 30-min
break-glass expiry · recertification cadence scheduler · evidence expiry →
STALE + re-request · vulnerability/CAPA/workflow SLA clocks · vendor-ESG
escalation · permit-expiry + target-trajectory sweeps · DB-size monitor.

### 12.6 Security operations
Rotate `JWT_SECRET` with the `JWT_SECRET_PREVIOUS` overlap procedure; review
`/access-governance` analytics (dormant grants, expiring grants, open SoD
violations, break-glass posture) weekly; run recertification campaigns per cadence;
export the access-evidence ZIP for SAMA/NCA audits; keep seeded demo passwords
rotated; check `/audit-log` for WARN/ERROR events.

---
## 13. The chronicle — everything done, in order

### 13.0 Pre-history (V1, before 2026-06-18)
The platform was built across three sprints (docs in `sprints/`, handoff in
`SESSION_SUMMARY.md`): core modules, auth/JWT, bilingual UI, the original static
frontend, then the React v2 frontend. Source of truth moved to
`github.com/Ayman-Server/Claude-GRC-2`.

### 13.1 Environment bring-up (2026-06-18)
Cloned and flattened into `D:\AYMAN\GRC`; SQL Server Express restored `TSS_GRC`;
backend + frontend launched detached with durable logs; CORS LAN patterns validated
with a real build; firewall rule for :5173. Recorded in `LOCAL_SETUP.md`.

### 13.2 The platform audit (2026-06-18, commit `3ca91bf`)
A **7-phase audit** — Security, Quality, Performance, Functional, E2E, UX,
Accessibility — produced **287 findings (4 Critical / 52 High / 114 Medium / 93 Low /
24 Info)**, every Critical/High adversarially verified. Deliverable:
`reports/audit-report.html` + raw data under `audit/`. The 4 Criticals (all
authorization/tenancy): IDOR read+write across `CyberService`, unauthenticated
public intake tracker leaking submissions, approval-chain actor identity taken from
the request body. Systemic theme: broken function-level authorization.

### 13.3 Audit remediation waves (2026-06-21 → 06-22)
- **P0 Criticals**: all 4 fixed + the CCM SQL runner locked down; the
  **authorization ratchet test** introduced (baseline 422 unguarded writes).
- **P1 hardening**: dependency CVEs cleared (Trivy 0 HIGH/CRIT), account lockout +
  strong passwords, N+1 fixes, V225–V230 index programs (org-leading indexes on 67
  tenant tables), lazy i18n + route splitting, 32 SEC-Medium findings in one pass
  (BFLA guards, mass-assignment create-guards, SSRF constraints, portal throttle,
  Splunk HMAC fail-closed) — **ratchet 422 → 61**.
- **Waves C/D/E**: accessibility retokening (axe 68→13), UX copy, ALL performance
  findings (IO out of transactions, EAGER→LAZY, pagination clamps ×65, vendor chunk
  splitting), god-class splits (CyberService → 11 services), 3 latent board-pack
  crashes fixed. Residual structural tail explicitly dispositioned (§19).

### 13.4 Module remediation programs (each: review plan → epics → per-epic verified commits → explicit merge)

| Program | When | Scope shipped | Migrations | Merged |
|---|---|---|---|---|
| **Compliance/Policy** | 06-28 → 07-02 | C-1..C-12: real policy file upload/versioning, evidence attachments + SLA, scheduler suite (fixed the app-wide off-request audit bug), policy SoD, two-policy-systems merge, audit evidence-package, T-24 CAPA reopen | V231–V234 | `a21da1a5` |
| **BCM** | 06-29 → 06-30 | B-A..B-I: BCP generation from BIA, evidence-requests on 6 entities, rejected-evidence→gap, observation→gap promotion, DR checklists, BCM grant matrix | V235–V240 | `9df5c265` |
| **ERM/Risk** | 07-01 | E-A..E-K: acceptance workflow, control→residual math, treatment close gate, appetite breach escalation, KRI single-source consolidation (legacy engine deleted) | V241–V247 | `2c3877b5` |
| **Sustainability/ESG** | 07-02 → 07-04 | ALL 18 S-epics: SUST RBAC, real evidence pipeline, auto-CAPA loop, CMA/TCFD SoD + content-lock, climate loss engine, GHG hardening, IFRS S2 report, DTO validation layer, vendor ESG, CSRD double materiality | V248–V257 | `868eb6e6` |
| **Cyber + SOCOps** | 07-04 → 07-06 | 15/15 epics: façade evidence/policy forms rewired to the real subsystems; forensics custody ledger; threat-intel links + MITRE; workspace-incident backfill | V259–V268 | `0c244f65` |
| **Workflow engine** | 07-06 → 07-07 | WF-1..WF-10 (engine, SLA/escalation ladders, conditional routing, EXTERNAL tokenized delivery, QUORUM/MAJORITY) + 14-module adoption with live record-anchored panels; verdict MARKET-READY 20/20 | V269–V276 (V272 = RBAC catalogue) | `8421652e`, `67ba1ea4` |
| **Forms/screens** | 07-07 | GRCStructuredDrawer, submission↔engine reconciliation, §9 domain request forms, anchored buttons | V277/V278 | `e2227397` |
| **Export quality** | 07-08 → 07-09 | P0–P3 + phase 5: bundled Amiri font, real RTL everywhere, XlsxStyler/ExportNaming, export audit + Export History, BrandTheme white-label (V279), regulator/audit ZIPs with SHA-256 manifests + embedded evidence binaries, packet XLSX/DOCX, DoS caps | V279 | `3baceb8e` |

### 13.5 The two design plans (2026-07-09, merged `98d92de0`)
1. **`RBAC_ACCESS_GOVERNANCE_MODULE_PLAN.md`** — govern-don't-rebuild design over the
   existing evaluator: 24-module least-privilege matrix, maker-checker, SoD pack,
   recertification, break-glass, evidence ZIP, analytics; anchored to NCA ECC 2-2,
   SAMA CSF 3.3.5, PDPL. Phases P0–P3, V280–V284 reserved.
2. **`SUSTAINABILITY_GREEN_FINANCE_REVIEW_AND_UPLIFT_PLAN.md`** — dual-expert
   (sustainability + Saudi GRC) review incl. الصكوك الخضراء وحسابها والمحفظة الخضراء;
   uplift: REG-1..11 register, Green Sukuk/UoP math, portfolio/GAR, targets,
   IFRS S1/S2, NCEC, CBAM. Phases G0–G4, V285–V292 reserved.

### 13.6 Dual implementation loop (2026-07-09 → 07-11, alternating verified slices)
One self-paced loop alternating branches; house rules: commit per verified slice, no
merge without explicit "merge", ratchet ≤62, `[REVIEW-TEST]` tagging + CHARINDEX
cleanup, destructive DB ops need explicit OK.

**Green finance G0–G4 (V285–V292):** REG register + reading closure `3702c827` ·
Sukuk/UoP register with Shariah gate + over-allocation block `1f4b76a9` · impact
attribution + frozen reports + evidence ZIP `5344066e` · portfolio/taxonomy/GAR +
carbon credits `c8a30e13` · targets trajectory→auto-CAPA + IFRS checklist + NCEC
permits + CBAM `58343b97` (final probe 28/28).

**RBAC governance P0–P3 (V280–V284):** role CRUD/matrix + console `ce3c5925`/
`f092bc65` · maker-checker requests + time-bound + JML `38f33112` · recertification
+ SoD engine + evidence ZIP `eb162dea` (probe 33/33; the detective scan found 4 real
conflicts in dev org 1, left OPEN deliberately) · break-glass + analytics + EXPORT
6th action 70→94 perms `48b0971b` (probe 29/29).

### 13.7 Merge + cloud deployment preparation (2026-07-11)
User: "merge both, deploy master" → merged (`25006609`→`f1de27a6`), suite 2248/0/0,
ratchet 62, pushed. `deploy/` staged (jar, ui-dist, verified .bak, nginx.conf,
launcher); `DEPLOYMENT.md` runbook committed.

### 13.8 External configuration + first-ever prod-profile boot (2026-07-11, `d176dc90`)
`spring.config.import=optional:file:./external.properties` + every prod key
parameterized. Booting prod for the first time surfaced and fixed 4 real blockers:
Flyway checking empty `dbo` (→ `FLYWAY_DEFAULT_SCHEMA=platform` + baseline 292);
a prod-only bean cycle in the tenant write-guard (→ lazy `ObjectProvider`);
`ddl-auto=validate` false drift on `is_deleted` types (→ default none);
LDAP/SMTP health probes flipping health DOWN (→ disabled by default). Verified with
a completely clean environment: UP, login, APIs, baseline honored across restarts.

### 13.9 Completion program (2026-07-12, merged `0a07a35d`)
Gap analysis wrote a §0 implementation-status section into both plan MDs; user:
"do 1, 2 and 3" → all remaining items in both plans:
- **C1 green-finance backend gaps** `6fead01f` (V293): reading-required gate on
  report approval, `asset_ref` linkage + per-asset rollup, climate overlay endpoint,
  ESTIMATE impact basis + summary flag, offset cap w/ no-target block, IFRS sheet in
  the ZIP, target milestones + piecewise trajectory, permit violations → own CAPA
  (probe 18/18).
- **C2 RBAC backend gaps** `079bd448` (V294): workflow-engine visibility on access
  requests (three TX landmines solved: afterCommit + per-call REQUIRES_NEW + native
  stamp), tenant 4-eyes flag, in-app notifications, JML MOVER → targeted USER
  campaign, reviewer scoping, cadence auto-scheduler, platformAdminActions30d
  (probe 34/34).
- **UI completion** `b6f90d1a`: Access-Governance console +5 tabs (Requests /
  Campaigns / SoD / Break-glass / Analytics incl. override prompt + role diff);
  new `/green-finance` page (5 tabs); EsgPage +3 horizon tabs (Targets & Transition,
  IFRS S1/S2, Env Compliance incl. CBAM).

### 13.10 Roadmap closure (2026-07-12, merged `d33eaee0`, migration **V295**)
The plans' never-phased §10 items, closed (`fdbdbf50`, live probe 17/17):
- **ISAE 3410 assurance lineage** — 5-type UNION query → `GET /horizon/assurance-lineage`
  grading COMPLETE/PARTIAL/MISSING + readiness %; "Assurance Lineage" ESG tab.
- **GHG Scope-3 15-category split** — `scope3_category` CHECK 1..15, scope-3-only
  validation, `/ghg/scope3-breakdown` (per-category tCO₂e + coverage %), FE select.
- **Per-report packet exports** — `GET /finance/reports/{id}/export.xlsx|.docx`
  via the shared tabular engine + row buttons.
- **Offset cap made tenant-configurable** (plan decision #4) —
  `organisation_settings.carbon_offset_cap_pct` (default 10) read live by the
  retirement guard; Settings-tab field.
- **SOCPA IFRS S1/S2 dates** (decision #5) — a moving regulatory date, so encoded as
  a standing obligation: REG-1 wording now mandates verifying the current phase
  dates at socpa.org.sa before each reporting cycle.

### 13.11 Button audit + fixes (2026-07-12, merged `73fb9399`)
User report: "register button not working / all buttons in other tabs". Root cause
of the blanket failure: the backend was **down during a rebuild window** — the FE
proxies `/api` to it, so every button errored at once. Full replay of ~60 buttons'
API calls as `admin` through the Vite proxy: all wired. Two real defects found +
fixed: (1) validation-400 toasts didn't name the failing field (`problemMessage()`
now folds the RFC-7807 `errors` map into every toast); (2) the role-create form
didn't require the backend-mandatory Arabic display name. Everything else that
"fails" is a designed governance gate (§7 table) with a clear message. FE tests
43/43.

### 13.12 Real deployment execution (2026-07-11 → ongoing)
The user's actual server uses `D:\Deployment\tss-grc\` with **WinSW** (not NSSM):
- Fixed the backend service: missing `--spring.profiles.active=prod` in the WinSW
  XML, management port = server port conflict, placeholder JWT secret, junk
  `dbo.flyway_schema_history` from an aborted boot. Verified UP/login/APIs on
  **:8090/:8091**.
- nginx installed as a service the same WinSW way (`nginx-service.exe` + XML with a
  graceful `-s stop`), replacing the runbook's NSSM instructions (`winget` absent on
  the server). `DEPLOYMENT.md` rewritten accordingly (branch
  `docs/deploy-winsw-nginx`).

---

## 14. Verification discipline (how every slice was proven)

- **Full suite** on every slice: `mvn clean package` — grew 1,886 → … → 2,248 →
  **2,249 tests, 0 failures**. Always read the log for `BUILD SUCCESS` /
  `Tests run:` — a background wrapper's exit code can mask a Maven failure.
- **Authorization ratchet** — unguarded write endpoints pinned at **62**; any new
  write endpoint must carry a real `hasPermission` guard.
- **Live probes** — every feature verified against the running stack with Python
  urllib + pyodbc harnesses: happy paths, guard 401/403s, SoD 409s, idempotency
  (sweeps re-run to 0), math spot-checks, ZIP manifests re-hashed, **and via the
  Vite proxy (:5173) to prove the browser-identical path**. Probes run as the
  persona the user actually uses (`admin`) plus a second user for 4-eyes paths.
  Test rows tagged `[REVIEW-TEST]`, cleaned with `CHARINDEX`.
- **Migrations** applied to dev **twice** (idempotency proof) via pyodbc before
  commit; prod picks them up via Flyway baseline.
- **Frontend** — `tsc --noEmit` + `vitest` + `vite build`; Vite-transform 200 checks
  per edited module when the in-app browser is unavailable.

---

## 15. Troubleshooting encyclopedia

### 15.1 Backend won't start

| Symptom | Cause → fix |
|---|---|
| *"JWT_SECRET appears to be a placeholder value"* | real secret missing → set ≥32-char `JWT_SECRET` (env in dev, external.properties in prod) |
| *"Unable to determine Dialect without JDBC metadata"* | DB auth failed → `DB_PASSWORD` missing/wrong, SQL TCP/IP disabled, or wrong instance in `DB_URL` |
| *"release 17 not supported"* during build | shell picked JDK 11 → `JAVA_HOME=C:\Program Files\Java\jdk-17` |
| Port conflict at boot | `SERVER_PORT` == `MANAGEMENT_SERVER_PORT` (must differ), or an old java process still holds the port → kill by port |
| Flyway tries to run V1/V2 on a restored DB | emptiness check looked at `dbo` → `FLYWAY_DEFAULT_SCHEMA=platform` + `FLYWAY_BASELINE_VERSION=292`; also drop any junk `dbo.flyway_schema_history` from aborted boots |
| Circular-dependency error only in prod | prod-only bean graph (tenant write-guard) — already fixed via lazy `ObjectProvider`; pattern to remember for new prod-only beans |
| `/actuator/health` DOWN though app works | LDAP/SMTP health probing absent servers → keep `LDAP_HEALTH_ENABLED`/`MAIL_HEALTH_ENABLED` off until wired |
| Service starts then dies instantly (WinSW) | XML missing `--spring.profiles.active=prod`, or `workingdirectory` wrong so `external.properties` isn't found — check the WinSW `.err.log` |

### 15.2 UI problems

| Symptom | Cause → fix |
|---|---|
| **Every button "does nothing" / all tabs broken at once** | the backend is down/restarting — the UI forwards all `/api` calls to it (dev: Vite proxy → :8084; prod: nginx → :8090). Check the backend service/health, then hard-refresh |
| A create/save button shows *"Validation Failed — field: reason"* | a required field is missing/invalid — the toast names it (Arabic names are mandatory on bilingual records) |
| An approve/decide button returns 409 | a **designed** four-eyes/SoD gate (§7 table) — complete the action with a second user; the message states the rule |
| Login works via curl but browser gets CORS errors | you bypassed nginx/Vite and hit the backend port directly — always use :80 (prod) or :5173 (dev) |
| UI loads, everything under `/api` 404/502s | nginx `proxy_pass` port ≠ `SERVER_PORT` (both `/api/` and `/ws` blocks), or backend down |
| Deep-link F5 gives nginx 404 | SPA fallback missing — restore `try_files $uri $uri/ /index.html;` |
| Page loads but data empty + red toasts after idle | access token expired and refresh cookie dead (backend restarted with a **changed** JWT_SECRET) → log in again |
| Stale UI after an update | hard refresh (Ctrl+F5) — hashed assets cache aggressively; also confirm `ui\` was actually replaced |
| Vite dev page broken after many HMR edits | stale module graph → restart `npm run dev`, hard-refresh |

### 15.3 nginx / services

| Symptom | Cause → fix |
|---|---|
| `nssm not recognized` | the runbook now uses **WinSW** (§10.6–10.7) — no NSSM needed |
| nginx won't bind :80 (`bind() … 10013/10048`) | IIS or another app owns 80 → `Stop-Service W3SVC; Set-Service W3SVC -StartupType Manual`, or move nginx's `listen` |
| nginx service starts but config errors | run `nginx.exe -t -p <dir>` and read `logs\error.log`; remember `-p` prefix and forward slashes in paths |
| Service won't start, port busy | a manually-started nginx.exe is still running → `nginx.exe -s stop -p <dir>` first |
| 413 on evidence upload | body cap — `client_max_body_size 100m;` must be present |

### 15.4 Database

| Symptom | Cause → fix |
|---|---|
| Connection refused :1433 after reboot | the default MSSQLSERVER instance stole the port → keep it Manual/stopped, start `MSSQL$SQLEXPRESS` |
| Arabic text saved as `????` | connection missing `sendStringParametersAsUnicode=true` or a non-UTF8 collation — use the shipped `DB_URL` |
| Auto-CAPA insert 500s | new `source_type` not in the `ck_ca_source` CHECK whitelist → extend the constraint in a migration |
| Seed/test rows vanished after a LIKE cleanup | `[REVIEW-TEST]` inside `LIKE` is a character class → always match literals with `CHARINDEX` |
| DB approaching 10 GB on Express | the DB-size monitor warns → archive/purge or upgrade the SQL edition |

---

## 16. Developer landmines (read before touching the code)

1. **JAVA_HOME trap** — fresh shells default to JDK 11; export JDK 17 first.
2. **Jar lock** — stop the running backend (kill by port) before `mvn clean`.
3. **ROWVERSION double-flush** — never save an entity twice around a native
   `@Modifying` query on the same table; run the native first, mutate + save ONCE.
4. **Off-request audit** — `AuditLogService` guards `RequestContextHolder.getRequestAttributes()`
   (not `getIfAvailable()` — that returns a proxy); scheduled/async code must be
   org-explicit (`TenantContext` isn't set off-request).
5. **`ck_ca_source` CHECK** — every new auto-CAPA sourceType must be whitelisted.
6. **Soft-delete vs unfiltered unique index** — `iam.roles` unique name has no
   `is_deleted` filter while JPA can't see retired rows → native revive pattern.
7. **`@ManyToMany` join tables ignore soft-delete** — hard-delete `iam.user_roles`.
8. **`@Valid` fires before `@PreAuthorize`** — write-guard probes need a valid body
   or you measure 400, not 403.
9. **Nested Spring Data repositories are not scanned** — top-level interfaces only.
10. **Returning JPA entities with lazy collections** 500s outside the session — DTOs.
11. **`[REVIEW-TEST]` in `LIKE`** is a character class — use `CHARINDEX`.
12. **`seed_real_data.py` batches** must start `SET NOCOUNT ON;` (`run()` reads `@@ROWCOUNT`).
13. **Reboot port-steal** — keep default MSSQLSERVER Manual; Express owns 1433.
14. **Flyway on a restored DB** — populated default schema + baseline, or it re-runs history.
15. **Prod-only bean graphs differ** — a profile that has never booted hides cycles.
16. **Optional-infra health indicators** flip liveness DOWN when their servers don't exist.
17. **`workflow.requests` deletes** need children cleared first (`request_events`, `request_recipients`).
18. **Verify a table is wired** before "seeding an empty table" — row-count 0 can mean dead/legacy (`privacy_lc` is orphaned; live pages use `privacy`).
19. **Vite HMR ToastProvider error** after CSS edits is a stale-module artifact — full reload.
20. **External-config precedence** — UPPER_CASE placeholders always win; raw Spring keys lose to profile-pinned values.
21. **Engine-wiring TX rules** — a joined-TX engine failure poisons the commit (503);
    create+send in one TX trips ROWVERSION; `afterCommit` joins the completed TX
    (null-id writes). Pattern: `TransactionSynchronization.afterCommit` + per-call
    `REQUIRES_NEW` `TransactionTemplate` + native stamp updates.
22. **FE/BE required-field parity** — any backend `@NotBlank`/`@NotNull` field must be
    client-gated in the form (and `problemMessage()` names it if you forget).
23. **Testing 4-eyes flows solo** — the same account cannot propose *and* approve;
    use `admin` + `so_officer`. A 409 with an SoD message is the feature working.
24. **nginx as a service** — must stop via `-s stop` (graceful), never a raw kill;
    the WinSW `stopexecutable` pattern handles it.
25. **Background build wrappers** can report exit 0 on a failed Maven run — always
    grep the log for `BUILD SUCCESS` / `Tests run:`. Corollary hit twice on 2026-07-25:
    the same wrapper reports exit 0 when Maven never ran at all (`no POM in this
    directory` — a persisted `cd` to the repo root). Grep for the totals line, not the
    exit code, every time.
26. **Restoring a mutated source file with `shutil.copy2` preserves its mtime**, so
    Maven's incremental compiler sees source-older-than-class and leaves the **last
    mutation's class** in `target/classes`. Symptom: a mutation run reports every guard
    proven, then the next full suite fails on correct source (and a dev backend started
    from that tree serves mutated behaviour). Restore by **writing the text and touching
    the file** (`os.utime`), never `copy2`.
27. **A mutation-testing harness needs a control run.** An already-failing test reports
    `CAUGHT` for *every* mutation, because the harness only observes `BUILD FAILURE`.
    Prove each test green on unmutated source **first** and abort if it is not —
    otherwise a broken test manufactures a clean bill of health for guards nobody checked.
28. **A permission the code tests for must exist in `iam.permissions`.** Two separate
    defects came from this: `ADMIN:*` guarded 59 committee methods while held by **zero**
    roles (F2-23), and `mayRecordOnBehalf` matched the authority `ADMIN_APPROVE` when that
    permission was not catalogued at all, so no tenant could ever grant it. **A guard
    nobody holds is indistinguishable from a broken feature** — when adding or retargeting
    a guard, query the grant set in the same slice.

---

## 17. Design decisions & rationale

| Decision | Rationale |
|---|---|
| **Modular monolith**, not microservices | one DB, one deploy, cross-module joins (risk↔compliance↔evidence) are the product; scale ceiling of a Saudi enterprise tenant fits one box |
| **Arabic mandatory on names** | bilingual is a product guarantee, not a translation layer; enforced at the DTO layer so no record can be born monolingual |
| **Flyway OFF in dev / ON in prod** | dev DB long predates Flyway (Python-bootstrapped); rewriting history was riskier than a disciplined baseline (292) + forward-only migrations |
| **`external.properties` next to the jar** | operators edit one file and restart — no env vars, no rebuild; UPPER_CASE placeholder surface keeps the packaged profiles authoritative |
| **WinSW over NSSM** | no installer/winget dependency, XML-declarative, one exe per service; proven on the actual server |
| **nginx same-origin topology** | serving the SPA and proxying `/api`+`/ws` from :80 removes CORS entirely and hides the backend port |
| **Maker-checker everywhere** | SAMA/NCA expectations; enforced server-side (409), never just hidden buttons |
| **EXPORT as a 6th action, READ-parity rollout** | export gating without breaking anyone on day one; tenants tighten per-module when ready |
| **Offset cap tenant-configurable (default 10%)** | REG-11 integrity with policy freedom; fixed constant kept only as null-fallback |
| **SOCPA dates as a standing obligation** | a moving regulatory date must be re-verified per cycle, not hard-coded |
| **Ratchet test for authorization** | prevents regression of the audit's biggest finding class; the count can only go down |
| **`[REVIEW-TEST]` + CHARINDEX cleanup discipline** | live-probing production-shaped data without polluting it |
| **Evidence-first verification (GHG Rule 2, reading-required reports)** | ISAE 3410 assurance-readiness: no verified/approved figure without traceable evidence |

---

## 18. Glossary

| Term | Meaning |
|---|---|
| **GRC** | Governance, Risk & Compliance |
| **NCA ECC** | Saudi National Cybersecurity Authority — Essential Cybersecurity Controls |
| **SAMA CSF / OpRes** | Saudi Central Bank — Cyber Security Framework / Operational Resilience |
| **PDPL / SDAIA** | Personal Data Protection Law / Saudi Data & AI Authority |
| **CMA / Tadawul** | Capital Market Authority / the Saudi stock exchange |
| **SOCPA** | Saudi Organization for Chartered and Professional Accountants (adopts IFRS S1/S2) |
| **IFRS S1/S2** | ISSB sustainability & climate disclosure standards |
| **TCFD** | Task Force on Climate-related Financial Disclosures |
| **ISAE 3410** | assurance standard for GHG statements (external assurance readiness) |
| **GHG Protocol Scopes 1/2/3** | direct / purchased-energy / value-chain emissions; Scope 3 has 15 categories |
| **CAPA** | Corrective And Preventive Action |
| **SoD / four-eyes / maker-checker** | segregation of duties: initiator ≠ approver |
| **JML** | Joiner / Mover / Leaver identity lifecycle |
| **Break-glass** | audited emergency access with mandatory post-hoc review |
| **RoPA / DPIA / DSR** | Record of Processing Activities / Data Protection Impact Assessment / Data Subject Request |
| **BIA / BCP / MTPD** | Business Impact Analysis / Business Continuity Plan / Maximum Tolerable Period of Disruption |
| **NCEMA** | UAE/GCC National Emergency Crisis and Disasters Management Authority conventions used in BCM packets |
| **TPRM** | Third-Party Risk Management |
| **Green Sukuk / UoP** | Shariah-compliant green bond / Use of Proceeds |
| **ICMA GBP / CCE** | Green Bond Principles / Circular Carbon Economy taxonomy |
| **GAR** | Green Asset Ratio (eligible ÷ assessed exposure) |
| **PCAF** | Partnership for Carbon Accounting Financials (financed-emissions data quality 1–5) |
| **RVCMC** | Regional Voluntary Carbon Market Company (Saudi carbon-credit registry) |
| **NCEC / MWAN / SEEC** | Saudi environmental-compliance / waste-management / energy-efficiency bodies |
| **CBAM** | EU Carbon Border Adjustment Mechanism |
| **SGI** | Saudi Green Initiative (net-zero 2060) |
| **KRI** | Key Risk Indicator |
| **IDOR / BFLA** | Insecure Direct Object Reference / Broken Function-Level Authorization |
| **Ratchet (here)** | a test pinning the number of unguarded write endpoints so it can only decrease |
| **Hijri** | Islamic lunar calendar (dual-stamped on attestations) |

---

## 19. Current state & open items (2026-07-13)

**State:** master `73fb9399` == origin/master — both plans 100% implemented incl.
UI, roadmap closure (V295) and the button-audit fixes all merged. Suite
**2,249/0/0**, ratchet **62**, FE tests **43/43**. Migrations **V1–V295** packaged;
next free **V296**. Dev DB fully migrated, zero `[REVIEW-TEST]` residue. Dev stack:
backend :8084 + Vite :5173. Production: `D:\Deployment\tss-grc` — backend UP as a
WinSW service on :8090/:8091; nginx being installed as `TssGrcNginx` (WinSW method,
§10.7). Unmerged branch: `docs/deploy-winsw-nginx` (the runbook's WinSW rewrite +
this document's update).

**Open items / backlog:**
- **Finish the UI go-live**: nginx service running, firewall :80, smoke tests
  (§10.8), then rotate every seeded password and decide on demo tenants 2–3;
  TLS at nginx when a domain/certificate exists.
- **4 real SoD violations** OPEN in dev org 1 (deliberate detective findings) —
  disposition or remediate as a demo exercise.
- **`smallint`/`bit` `is_deleted` reconciliation** → re-enable
  `HIBERNATE_DDL_AUTO=validate` in prod.
- **No SMTP relay yet** — notifications are in-app; wire via `external.properties`
  (`SMTP_*`, then `MAIL_HEALTH_ENABLED=true`).
- **Deferred audit structural tail** (by explicit decision): TI-001 outsourcing-module
  merge, DUP-001 BaseEntity unification (~200-table migration), DUP-002 frontend
  clone dedup, FA-006 cross-module facades, per-endpoint `@Valid` DTO conversion.
- Minor: FE button for the regulator package endpoint; NDI ad-hoc export
  live-verify; optional C-12 legacy column drop; CMA Arabic term alignment.

---

*Companion documents: `DEPLOYMENT.md` (go-live runbook, WinSW-based) ·
`LOCAL_SETUP.md` (dev machine) · `reports/audit-report.html` (the platform audit) ·
per-program `*_PLAN.md` files (each carries its own §0 implementation tracker) ·
`CLAUDE.md` (working conventions) · `tss-grc-backend/external.properties.template`
(config template).*
