# Independent SME / GRC-Expert Audit — NCA · NDI · Data Management · PDPL · BCM · ERM

**Date:** 2026-06-01
**Mode:** Independent multi-agent workflow (`grc-sme-experts-erm-e2e`, run `wf_991931a2-0c9`) — 17 agents, 4 phases, ~12 min, live against the running backend (`localhost:8084`) + direct SQL Server introspection.
**Scope:** Act as six independent Saudi-GRC subject-matter experts (one per framework family), review every module for day-to-day operational support, and run end-to-end user journeys against all processes.

> This run extends the earlier `sme-experts-independent-e2e.md` audit by adding **ERM** as a sixth framework family and re-testing every journey end-to-end.

---

## Overall verdict: **WARN**

All six catalog families are complete and bilingual on the dimension that matters to an officer, and **all six live end-to-end journeys passed**. The verdict is WARN (not PASS) because the module reviews surfaced four confirmed correctness defects an operator would actually hit — none of which is an absent family or a failed core journey (which would be FAIL).

| Dimension | Result |
|---|---|
| Catalog families (6) | 5 PASS, 1 WARN (ERM is register-driven, thin catalog) |
| Module reviews (4) | 2 PASS, 2 WARN |
| Live E2E journeys (6) | 6 PASS |

---

## 1. SME catalog coverage (recommended evidence per sub-control)

| Family | Frameworks | Controls | Sub-controls | EN+AR bilingual | Verdict |
|---|---|---|---|---|---|
| **NCA** | 7 (ECC-2:2024, CCC-1:2019, DCC-1:2022, OTCC-1:2022, CSCC-1:2020, TCC-1:2021; +TW-1 inactive) | 99 | 356 (332 active) | 332/332 = 100% | PASS |
| **NDI** | embedded in NDMO-DMPDP-1 (sub-controls 1-5, 2-4) | 15 | 77 | 77/77 = 100% | PASS |
| **Data Management** | NDMO-DMPDP-1 | 15 | 77 | 77/77 = 100% | PASS |
| **PDPL** | PDPL-1:2023 (+NDMO-DMPDP-1) | 29 (14 strict) | 136 (59 strict) | 136/136 = 100% | PASS |
| **BCM** | BCM-ISO22301-SAMA (+2 inactive legacy) | 18 (13 active) | 75 (61 active) | 61/61 = 100% | PASS |
| **ERM** | ERM-1 only | 3 | 8 | 8/8 = 100% | **WARN** |

**Key facts**
- Recommended evidence (`evidence_guidance` / `evidence_guidance_ar`) is **100% bilingual on every active sub-control** across all six families. The only EN/AR gaps are on `is_active = 0` placeholder rows, excluded from scoring.
- **NDI is not a separate framework** — it is embedded in NDMO-DMPDP-1 via sub-controls 1-5 (NDI Self-Assessment & Maturity) and 2-4 (NDI Data Asset Submission). A dedicated `/api/ndi/*` module also exists (20 requirement items, bilingual).
- **ERM is register-driven, not catalogued.** Only `ERM-1` (3 controls / 8 sub-controls) exists in `evidence.frameworks`; the live ERM capability (50 risks, treatments, KRIs, appetite, heatmap) lives in `module.risk`, **not cross-linked to `evidence.frameworks`**. No COSO / ISO 31000 / SAMA-ERM named catalogs; no sub-controls for risk culture, emerging-risk scanning, ICAAP/stress-testing, or three-lines assurance.

---

## 2. Module reviews (day-to-day operability)

| Module | Verdict | Severity | Headline |
|---|---|---|---|
| Evidence-Management Lifecycle | PASS | LOW | Two guarded state machines + unified review path; hardened file upload (SHA-256, allow-list, path-traversal guard); nightly auto-expiry; Request↔Item FK. Only gap: no reviewer-vs-submitter SoD. |
| GRC Expert (SME surface) | PASS | MEDIUM | 11 frameworks, rollup math consistent, 100% bilingual. **Defect:** coverage marks expired-only evidence as COVERED/green. |
| Enterprise Risk Management (`module.risk`) | **WARN** | **HIGH** | Backend operable end-to-end (create→assess→treat→approve→close all 2xx). **Defect:** `risk_level`/heatmap band on **inherent** L×I, ignoring residual. |
| Cross-module operator journey | **WARN** | MEDIUM | Consistent register+filter+transition+dashboard pattern, real KPIs, pervasive bilingual RTL. **Defects:** `privacy_lc.dsr_cases` 500; Arabic-primary not default. |

---

## 3. Live E2E journeys — all PASS

| Journey | Mode | Result |
|---|---|---|
| Evidence Collect (GAP→request→item→approve→COVERED) | WRITE | PASS — full collect→fulfil loop; req 20 / item 65; coverage flipped GAP→COVERED |
| NCA cybersecurity browse→drill→evidence→registers | READ | PASS — ECC 107/107 bilingual; 75 controls, 16 incidents, 25 assets |
| NDI / NDMO data-management | READ | PASS — NDMO-DMPDP-1 drilled; dataclass dashboard reconciles |
| PDPL privacy + SDAIA Art.31 | READ | PASS — breaches/DSRs/ROPA populated; 72h deadline computed correctly |
| BCM browse→drill→registers | READ | PASS — 16 steps all 200; BIA/plans/processes/strategies bilingual |
| ERM / risk register | READ | PASS — 50 risks; inherent-vs-residual drill; heatmap sum=totalRisks; appetite bilingual |

---

## 4. Confirmed defects & fixes applied this pass

All four were independently verified against source / live DB before any change.

| # | Severity | Defect | Verified at | Fix |
|---|---|---|---|---|
| 1 | **HIGH** | `risk.risks.risk_level` computed column bands on `inherent_likelihood × inherent_impact` only — ignores residual. Headline rating over/under-reports post-control exposure (e.g. id=5 inherent 16→HIGH while residual 6). | `sys.computed_columns` (live) | Redefine computed column to band on `COALESCE(residual L×I, inherent L×I)` — residual when assessed, inherent baseline otherwise. Dev DB script + Flyway `V212`. Frontend heatmap + level filter re-keyed to residual-with-fallback. |
| 2 | MEDIUM | GRC Expert coverage marks a sub-control COVERED even when its only approved evidence is past `valid_to`. | `GrcExpertService.java:100` (expiry array computed line 98, never consulted) | New freshness-aware query (`aggregateFreshApprovedBySubControl`); coverage demotes to a new **EXPIRED** state when all approved items are expired. Frontend `EXPIRED` badge tone + bilingual label. |
| 3 | MEDIUM | `GET /api/privacy-lc/dsr-cases` → HTTP 500: entity maps `sla_breached_at` / `sla_breach_notified` absent from the dev DB. | `DsrCase.java:64,66`; live "Invalid column name" | Dev-DB hardening script adds both columns (Flyway `V115` already covers staging/prod — dev never got them since Flyway is off in dev). |
| 4 | MEDIUM | UI defaults to English LTR on first load, against the Arabic-primary spec. | `i18n.tsx:29` | Default first-load to `ar` unless the user has explicitly stored `en`. |

---

## 5. Remaining follow-ups (documented, not actioned this pass)

Lower-priority or larger-refactor items surfaced by the audit, intentionally left for a focused follow-up:

- **Risk frontend status-enum mismatch** — `RiskDetailPage` gates on non-existent `UNDER_ASSESSMENT`/`TREATMENT_IN_PROGRESS`; `NewRiskModal` offers `IN_PROGRESS`/`MITIGATE`/`TREATED` the service never transitions to. Collapse to the canonical `OPEN/UNDER_REVIEW/MITIGATING/ACCEPTED/CLOSED`.
- **Risk→control / risk→KRI linkage is mock-only in the risk UI** (`RISK_LINKS_BY_ID` / `RISK_KRIS_BY_ID`); the `RiskControl` bridge has no controller/service. Operator cannot attach a control/KRI from the UI; no residual recompute from `contribution_pct`.
- **Arabic-primary data gaps** — `Risk.descriptionAr`, `RiskTreatment.descriptionAr`, `RiskAssessment.assessmentNotesAr` are EN-only.
- **POST returns null computed fields** — refresh JPA entity after DB computes `inherentScore`/`residualScore`/`riskLevel`.
- **Reviewer-vs-submitter SoD** on evidence approve/reject and ERM assess→approve.
- **ERM catalog depth** — add ISO 31000 / SAMA-ERM sub-controls (risk culture, emerging-risk, ICAAP/stress, three-lines) and cross-link the live register to `evidence.frameworks`.
- **NCA cyber dashboard scorecard** reads 0 maturity despite 75 IMPLEMENTED controls — wire the rollup to aggregate implementation status.
- **Evidence file download** — `POST /items/{id}/file` stores bytes but no GET streaming endpoint was found.
- **Duplicate DSR registers** — consolidate `/api/v1/data-officer/dsr` (working) vs `/api/privacy-lc/dsr-cases` to one source of truth.

---

*Generated from workflow `wf_991931a2-0c9`. Full structured agent output retained in the run transcript.*
