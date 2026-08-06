# Independent SME / GRC-Expert Audit — NCA · NDI · Data-Mgmt · PDPL · BCM · ERM · ESG (+ Workflow Engine)
> **HISTORIC — point-in-time snapshot (all files 2026-06-01). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


**Date:** 2026-06-01
**Mode:** Independent multi-agent workflow (`grc-sme-experts-esg-e2e`, run `wf_9ff229c6-f61`) — 21 agents, 4 phases, ~15 min, live against the running backend (`localhost:8084`) + direct SQL Server introspection.
**Scope:** Seven independent Saudi-GRC SME experts (one per family), day-to-day module review, live end-to-end journeys, **and** an explicit workflow-engine / state-machine verification pass ("all processes and workflows"). Also a regression sweep on the four fixes merged to `master` earlier today.

> Extends `sme-experts-erm-e2e.md` by adding **ESG** as a seventh family and a dedicated workflow-engine pass.

---

## Overall verdict: **WARN**

All five catalogued control families are complete + 100% bilingual; ERM and ESG are register/module-driven (WARN by design, not failures). All eight live E2E journeys PASS, including the workflow-engine guard pass. The four previously-merged fixes all HOLD. The verdict is WARN because the audit surfaced **one new HIGH runtime regression** (now fixed, see §4) plus several MEDIUM/LOW gaps.

| Dimension | Result |
|---|---|
| Catalog families (7) | 5 PASS, 2 WARN (ERM thin, ESG register-driven) |
| Module reviews (5) | 4 PASS, 1 WARN/HIGH → **fixed this pass** |
| Live E2E journeys (8, incl. workflow engine) | 8 PASS |
| Regression sweep (4 merged fixes) | 4/4 HOLD |

---

## 1. SME catalog coverage

| Family | Frameworks | Controls | Sub-controls | Bilingual | Verdict |
|---|---|---|---|---|---|
| NCA | 7 (TW-1 inactive) | 102 (99 active) | 362 (332 active) | 332/332 | PASS |
| NDI | NDMO-DMPDP-1 (embedded) | 15 | 77 | 77/77 | PASS |
| Data Management | NDMO-DMPDP-1 | 15 | 77 | 77/77 | PASS |
| PDPL | PDPL-1:2023 (+NDMO) | 29 | 136 | 136/136 | PASS |
| BCM | ISO22301-SAMA (+2 legacy) | 18 (13 active) | 75 (61 active) | 61/61 | PASS |
| **ERM** | ERM-1 (+RISK-1) | 5 | 13 | 13/13 | WARN |
| **ESG** | 0 (no catalog) | 0 | 0 | n/a | WARN |

- **ERM** — only ERM-1 + RISK-1 (13 bilingual sub-controls); the live ERM capability (70 risks, treatments, KRIs, appetite) lives in `module.risk`, not catalogued. WARN by design.
- **ESG** — **no** ESG framework in `evidence.frameworks`, but ESG is a **fully-wired backend module** `com.tss.grc.module.sust` (11 sub-packages: metrics, materiality, tcfd, climate, ghg, cma, sgi, annex, capa, evidence, dashboard) + `module.risk.emerging`, with 15 controllers under `/api/sustainability/*`, `/api/v1/climate/*`. Bilingual EN+AR (Saudi-native). WARN only because it is invisible to the unified GRC-Expert/evidence scorecard — a CMA/Tadawul/GRI/ISSB-aligned ESG framework would need adding to the catalog for cross-framework attestation.

## 2. Module reviews

| Module | Verdict | Sev | Note |
|---|---|---|---|
| Evidence-Management Lifecycle | WARN→**fixed** | HIGH | `NEEDS_REVISION` review path 500'd — DB CHECK constraint never migrated. **Fixed §4.** |
| GRC Expert (SME surface) | PASS | LOW | EXPIRED freshness fix verified; rollup math consistent; 542 sub-controls bilingual |
| Enterprise Risk Management | WARN | MEDIUM | Residual-rating fix landed (HIGH→MEDIUM); remaining: status-enum mismatch, null POST computed fields, mock links/KRI tabs |
| ESG (sustainability) | PASS | LOW | Real live module, bilingual, operable end-to-end |
| Cross-module day-to-day | PASS | LOW | Consistent register+filter+transition+dashboard; DSR 200 + Arabic-primary confirmed |

## 3. Live E2E journeys — all PASS

| Journey | Mode | Result |
|---|---|---|
| Evidence Collect→Fulfil loop (self-cleaning) | WRITE | PASS — GAP→request→item→approve→COVERED→cleanup |
| **Workflow engine + state-machine guards** | WORKFLOW | PASS — `/api/v1/workflow` definitions/instances/transitions; guards reject illegal→400/409 across ≥3 machines; 6 definitions seeded |
| NCA cyber | READ | PASS |
| NDI / NDMO data | READ | PASS |
| PDPL privacy (+DSR 200) | READ | PASS |
| BCM | READ | PASS |
| ERM / risk (residual) | READ | PASS |
| ESG / sustainability | READ | PASS |

**Workflow engine:** `WorkflowEngineController @ /api/v1/workflow` over `workflow_definition/state/transition/instance/action_log` tables; `executeTransition()` throws on bad action codes (→400), logs every transition, stamps `completedAt` on terminal states. 6 definitions seeded (RISK_LIFECYCLE_V1 reference). Note: modules still carry their own hard-coded state machines; live records not yet migrated onto the engine.

## 4. New HIGH regression — FIXED this pass

**`NEEDS_REVISION` evidence-review path returned HTTP 500.** The evidence-lifecycle-hardening change added `NEEDS_REVISION` to the Java state machine (`EvidenceItemService.ITEM_STATUSES`/`ITEM_TRANSITIONS` + `EvidenceReviewService`), but the DB `CHECK` constraint `evidence.evidence_items.CK_ev_items_status` was never migrated to allow it — so `POST /api/v1/evidence/reviews/{id}/complete?result=NEEDS_REVISION` violated the constraint and 500'd. It was the **only** path for an officer to return evidence for revision.

**Fix:** redefine the constraint to the full Java enum (`DRAFT, PENDING_REVIEW, APPROVED, REJECTED, NEEDS_REVISION, EXPIRED` + legacy `ARCHIVED, SUPERSEDED`). Dev DB via `setup_evidence_status_constraint.py`; staging/prod via Flyway `V213`.

**Verified live:** create item → submit (PENDING_REVIEW) → create review → complete `NEEDS_REVISION` → **HTTP 200**, item status = `NEEDS_REVISION` (was 500). Test artifact cleaned up.

## 5. Regression sweep — all 4 merged fixes HOLD

| Fix | Status | Evidence |
|---|---|---|
| ERM residual `riskLevel` | ✅ HOLDS | DB sweep of 70 risks: 54 have inherent≠residual band; 100% use residual; id=2 inherent 25→CRITICAL, API riskLevel=HIGH (residual 12) |
| GRC Expert `EXPIRED` freshness | ✅ HOLDS | 542 sub-controls: GAP 529 / EXPIRED 9 / PARTIAL 4; all 9 stale-approved report EXPIRED, never COVERED; invariant holds |
| privacy_lc DSR `200` | ✅ HOLDS | `/api/privacy-lc/dsr-cases` 200, 12 bilingual cases; `?overdueOnly=true` 200 (7) |
| Arabic-primary default | ✅ HOLDS | i18n defaults `ar`/RTL; 14/14 esgTab labels parity; Hijri + ar-SA formatting; no mojibake |

## 6. Remaining follow-ups (documented, not actioned)

- **MEDIUM** — Risk POST create returns null `inherentScore`/`residualScore`/`riskLevel` (entity not refreshed after `save()`); also emits an empty F8 `RISK_CREATED` payload, so `riskLevel=CRITICAL` escalation rules never match on creation. Fix: `entityManager.refresh()` / re-fetch after save.
- **MEDIUM** — Risk status-enum mismatch: `NewRiskModal` offers `OPEN/IN_PROGRESS/MITIGATE/TREATED/CLOSED` but backend produces `OPEN/UNDER_REVIEW/MITIGATING/ACCEPTED/CLOSED`; dead Rule-T1 guard checks a `TREATMENT` status the machine never sets.
- **MEDIUM (structural)** — ERM thin / ESG absent from the SME catalog: add COSO/ISO-31000/SAMA-ERM and CMA/Tadawul/GRI/ISSB/TCFD frameworks to `evidence.frameworks` with bilingual recommended evidence to make both attestable through the unified GRC-Expert scorecard.
- **LOW** — bilingual-data gaps: `risk_treatments`/`risk_assessments`/`risk_controls` have no `_ar` columns; `risks.description_ar` absent; cyber asset register EN-only.
- **LOW** — DTO omissions: `EvidenceRequestResponse.fulfilledAt` and `EvidenceItemResponse.requestId` null in response despite DB populated; risk list DTO omits `titleAr`.
- **LOW** — ERM **dashboard** heatmap widget still groups inherent (register heatmap is correct/residual); legacy inactive frameworks (TW-1:NCA, BCM-1/BCM-2) lack Arabic — formally deprecate.

---

*Generated from workflow `wf_9ff229c6-f61`. The HIGH regression in §4 was fixed and live-verified in the same session.*
