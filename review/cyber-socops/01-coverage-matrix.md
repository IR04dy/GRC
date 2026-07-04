# P1 — Coverage Matrix
### Every P0 reference process graded against the live system (code + DB + API), both directions

Method: parsed all controllers (89 cyber + 97 socops endpoints — `scratchpad/rev/catalogue.md`),
live-probed every parameterless GET (47/47 → HTTP 200 with real data), counted DB rows for all 38
tables, and read the key entities/controllers. Verdicts here are **matrix-level**; the test waves
(P2–P7) confirm REAL vs FAÇADE by exercising the full journey. Grades: REAL / PARTIAL / FAÇADE / MISSING.

## Headline architectural finding (drives the whole review)
The platform **already has** a complete, reusable evidence + attachment subsystem:
- `POST /api/v1/attachments/{entityType}/{entityId}` — real multipart upload, download, **versioning**, history, typed docs. Documented: *"Reusable across every register/form."*
- `/api/v1/evidence/requests` — cross-entity **evidence-request workflow** with comments (`EvidenceRequestController` + `EvidenceRequestService`).
- `/api/v1/evidence/reviews` — **review→complete(result,notes)** approve/reject loop.
- `/api/v1/evidence` matrix, versions, snapshot, connectors.

The **cyber** module ignores all of it and ships a parallel, gutted `CyberEvidence` (`/api/v1/cyber/evidence`) whose only file affordance is a **`file_name` string column** — no binary, no multipart endpoint (`POST /cyber/evidence` is JSON-only), no request, no review. **This is the user's exact complaint** (seed S1 + S2), and it is an *integration/island* problem, not a "capability missing" problem — the capability exists one module over. Same smell repeats across cyber's create forms.

Data reality: **not an empty demo** — cyber_sub_controls=529, cyber_controls=154, cyber_incidents=24 (+legacy `incidents`=20), assessments=21, vulnerabilities=20, action_plans=13, evidence=12; socops ir_playbook=37/steps=72, security_event=15, dlp=12, iam_jml=11, threat_intel=10, sbom=20, etc.

---

## A — Governance & Framework Controls

| ID | Process | Endpoints / tables | Verdict | Evidence & gap |
|---|---|---|---|---|
| A1 | Framework control library & applicability | `/cyber/frameworks` (6), `/controls` (154), `/sub-controls` (529) | **PARTIAL** | Rich library + real data. Applicability/SoA + per-control **owner dept** + review cadence need confirming in W1. No `is-applicable`/SoA endpoint seen. |
| A2 | Assessment → gap → CAPA | `/assessments` (21), `/assessments/{id}/convert-gap`, `/action-plans` (13), `/action-plans/{id}/complete` | **PARTIAL→test** | The convert-gap→action-plan wiring EXISTS (backend). Must verify in W1: does a failed control **auto**-raise the gap, does CAPA carry due date + **reminder + escalation** (the clock), and route to another dept. Likely PARTIAL (no clock). |
| A3 | Policy lifecycle | *(no `/cyber/policies*`; policy lives in compliance module)* | **MISSING (in cyber)** | Cyber has controls/evidence but no policy screen; policy mgmt is the compliance module. Cross-check: does the cyber workspace expose policy at all? If a "policy" popup exists with no upload → FAÇADE. Confirm in W1. |
| A4 | **Evidence lifecycle (request→collect→review→approve→retain)** — SEED S1/S2 | cyber: `/cyber/evidence` (12) CRUD only. platform: `/attachments/*`, `/evidence/requests`, `/evidence/reviews` | **FAÇADE (in cyber)** | `CyberEvidence` = CRUD island with a `file_name` **string** and no multipart, no request, no review. The real subsystem exists but is unwired. Confirmed at data-model + API level; W1 confirms the UI form has no upload. |

## B — Asset, Attack Surface & Vulnerability

| ID | Process | Endpoints / tables | Verdict | Evidence & gap |
|---|---|---|---|---|
| B1 | Asset inventory & classification | `/cyber/assets` (61), `/asm/assets` (20) | **PARTIAL** | Two asset concepts (cyber_assets 61 vs attack_surface_asset 20) — possible island/duplication. Classification + recert cadence to confirm. |
| B2 | ASM findings & shadow-IT | `/asm/findings` (14), `/asm/shadow-it` (4), transition/sanction/block/link-capa | **PARTIAL→test** | State machines + link-capa exist. Confirm findings route to owner with a clock; shadow-IT sanction needs approval. |
| B3 | Vulnerability mgmt & **SLA engine** | `/vulnerabilities` (20), `/vuln-sla/{findings(11),policies(4),compute,recompute,escalation/run,dashboard}` | **PARTIAL→test (promising)** | A real SLA engine with policies + compute + escalation/run + dashboard (open=4). This is the strongest-looking flow. W3 verifies the formula, WARNING/BREACHED states, and that escalation actually notifies. |
| B3b| Vuln exceptions | `/cyber/exceptions` (2), `/exceptions/{id}/approve` | **PARTIAL** | REQUESTED→APPROVED exists. Confirm time-box + auto-expire re-opens vuln. |
| B4 | Threat intelligence | cyber `/threat-intel/correlate`; socops `/threat-intel` (10) + `/{id}/action` | **PARTIAL** | Data + action endpoint. Confirm IOC→asset/vuln linkage is real, not a status flip. |

## C — Incident Response

| ID | Process | Endpoints / tables | Verdict | Evidence & gap |
|---|---|---|---|---|
| C1 | Cyber incident 9-step lifecycle | `/cyber/incidents` (24), `/incidents/{id}/start-response`, `root_cause_analyses` (12) | **PARTIAL→test** | Real incidents + RCA + start-response. W2 verifies the 9 states, **regulatory notification timers** (NCA/SAMA/PDPL — the Saudi must), evidence attach, auto-CAPA from lessons. Two incident tables (cyber_incidents vs incidents) = smell. |
| C2 | IR playbooks | socops `/playbooks` (37, templates 6, steps 72), `/clone`, `/{id}/invoke` | **PARTIAL→test** | Templates + clone + invoke + steps exist (best-populated). W2: does invoke spawn **assigned, clocked tasks** or just clone rows (FAÇADE if latter)? |
| C3 | SOC event triage | socops `/events` (15), `/triage`, `/resolve`, `/siem-events` ingest | **PARTIAL→test** | Triage/resolve exist. W2/W4: does triage **promote to incident** (the SOC spine) or only set status? |

## D — SOC Operations

| ID | Process | Endpoints / tables | Verdict | Evidence & gap |
|---|---|---|---|---|
| D1 | Machine ingest pipelines | `/dlp/ingest`,`/appsec/ingest`,`/brand-monitoring/ingest`,`/siem-events`,`/scanner-runs`,`/sbom`,`/iam-jml`,`/threat-intel/{id}/action` | **PARTIAL→test** | Dedicated ingest roles present (SIEM_INGEST, DLP_INGEST, APPSEC_INGEST, BRAND_INGEST, JML_INGEST, TI_INGEST). W4: are these roles **seeded**? Idempotent/dedup? Or unreachable endpoints? |
| D2 | Forensics & chain-of-custody | `/forensics` (4), `/transfer-custody`, `/verify-integrity` | **PARTIAL→test** | Custody transfer + integrity verify endpoints — promising. W4: is the custody ledger append-only + does verify-integrity actually hash? |
| D3 | Brand protection & takedown | `/brand-monitoring` (8), `/request-takedown`, `/complete-takedown` | **PARTIAL→test** | Takedown lifecycle exists. W5: clock/reminders on takedown SLA? |
| D4a| Cyber-insurance renewal | `/cyber-insurance` (2), `/renewals-due` (live=0) | **PARTIAL→test** | renewals-due returns 0 — either none due or the sweep isn't computing. W5 + scheduler proof. |
| D4b| Pentest cadence | `/pentests` (4) | **PARTIAL** | CRUD present; overdue/cadence tracking to confirm. |
| D4c| Privileged-access review | `/iam-jml/privileged-reviews-due` (live=1) | **PARTIAL→test** | Returns 1 due — the "due" computation works. Confirm it drives a reminder/escalation. |
| D4d| M&A cyber due-diligence | `/ma-due-diligence` (1), `/{id}/transition` | **PARTIAL** | State machine present, 1 row. |
| D5 | Awareness & exercises | `/awareness` (6) | **PARTIAL** | Campaigns present. Confirm completion tracking → gap/CAPA. |
| D+ | Extra socops aggregates (not in P0) | `crypto-assets`(10), `ot-assets`(8), `zt-maturity`(7), `seasonal-overlays`(12), `regulator-exams`(4), `sbom`(20) | **note** | App exposes MORE than P0 (cert/secret expiry, OT/ICS, Zero-Trust maturity, seasonal risk, regulator exams). Good breadth; each still needs the clock+workflow test. |

## E — Cross-cutting

| ID | Process | Endpoints | Verdict | Evidence & gap |
|---|---|---|---|---|
| E1 | Dashboards & KPIs | `/cyber/dashboard`, `/asm/dashboard`, `/vuln-sla/dashboard`, `/socops/dashboard` | **PARTIAL→test** | All return computed numbers off real data (not hardcoded — e.g. totalControls=75 matches). Confirm drill-down + org-scope. |
| E2 | Reporting / Excel / Arabic PDF | *(none in cyber/socops catalogue)* | **MISSING→test** | No `/export` or `/report` endpoint in either module's catalogue. Likely no Excel/Arabic-PDF export for cyber/socops registers — a gap vs market + vs other modules (BCM/ESG have exports). Confirm in W6. |
| E3 | i18n / RTL / Saudi-formal | titleEn/titleAr on entities | **PARTIAL→test** | CyberEvidence has titleEn but **no titleAr** (island entity). W6: audit titleAr coverage; a title-EN-only entity breaks OS-8. |
| E4 | RBAC & least privilege | cyber=`hasPermission('CYBER',…)`, socops=`hasAnyRole(…)` | **PARTIAL / FINDING** | (a) cyber **list** GETs require **WRITE** not READ (e.g. `/controls`, `/assessments`, `/incidents`) while `/all` variants require READ — inconsistent, a read-only user can't page lists. (b) socops list GETs have **no** method `@PreAuthorize` (any authenticated user lists). (c) ingest/CISO/analyst roles may be unseeded. W6 confirms. |
| E5 | Tenant isolation | `findByIdAndOrganisationId` (SEC-CYB-001) | **PARTIAL→test** | Prior hardening exists. W6 runs live IDOR probes (org-spoofed ids → must 404/403). |
| E6 | Integration bridges (no islands) | F8 events; convert-gap; **evidence subsystem unwired** | **PARTIAL / core finding** | convert-gap bridges to action-plan. But cyber evidence↔platform evidence/attachments = **unwired island** (A4). incident→risk, incident→BCM, SOC events consumed (not just published) to verify in W2/W6. |

---

## Reverse sweep — every catalogued surface is accounted for
- **89 cyber endpoints** → all fall under A1–A4, B1–B4, C1, E1, E4/E5 rows above. No orphan.
- **97 socops endpoints** → all fall under C2/C3, D1–D5, D+ rows. No orphan.
- Surfaces the app exposes **beyond** P0 (added as rows): crypto-assets/cert-expiry, ot-assets (ICS/OT), zt-maturity, seasonal-overlays, regulator-exams, sbom, seasonal-overlay. These are *market-plus* — good.
- Surfaces P0 expects but the app **lacks** (MISSING/weak): A3 policy in cyber, E2 exports (Excel/Arabic PDF) for cyber+socops, and the real evidence lifecycle inside cyber (A4).

## Provisional tallies (matrix-level, pre-wave)
- **FAÇADE:** 1 confirmed (A4 cyber evidence) + candidates pending wave proof (C2 invoke, E1 dashboards if hardcoded — ruled out, D4a renewals).
- **MISSING:** A3 (policy in cyber), E2 (cyber/socops exports). 
- **PARTIAL:** the large majority — real data + endpoints, but the *clock / cross-dept routing / upload / approval* primitives (OS-2..OS-6) are the recurring gap.
- **REAL (provisional):** none yet fully proven end-to-end; W-waves will elevate the strongest (B3 SLA engine, C2 playbooks, D2 forensics) to REAL if the full journey holds.

## Top hypotheses the waves must settle
1. **A4** cyber evidence is a façade duplicating the real subsystem → **confirmed at API/model level**; UI confirm in W1. *(highest user value)*
2. **OS-2 "the clock"** (due+countdown+reminder+escalation) is the pervasive missing primitive across CAPA, evidence, takedown, reviews → test each wave.
3. **C2 invoke** and **D1 ingest roles** are the FAÇADE risks (endpoint exists, no real effect / role unseeded).
4. **E2 exports** absent for cyber/socops.
5. **E4 RBAC** inconsistencies (WRITE-gated reads; unauthenticated socops list).
