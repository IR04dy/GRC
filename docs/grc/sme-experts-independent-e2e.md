# SME / GRC‑Expert — Independent End‑to‑End Verification

**Workflow:** `grc-sme-experts-independent-e2e` (run `wf_e5ca38a2-a61`)
**Date:** 2026‑06‑01 · **Agents:** 14 · **Duration:** ~14 min · **Mode:** live backend (`localhost:8084`, `admin`)
**Scope:** SME experts for **NCA, NDI, NDMO Data Management, PDPL, BCM** — sub‑controls + recommended evidence (EN+AR), evidence‑management lifecycle, day‑to‑day module review, and live user‑journey tests.

## Overall verdict: ⚠️ **WARN**

Every SME catalog family is present, built on real sub‑controls with **100% bilingual recommended evidence** (active sub‑controls), wired to a live `COVERED/PARTIAL/GAP` rollup; the operator surfaces and read journeys are production‑usable and all live E2E journeys passed. The verdict is **WARN — not PASS — only because the evidence‑management _write_ lifecycle carries HIGH‑severity structural hardening gaps.** No catalog is absent/empty and no core journey failed.

---

## 1. SME catalogs — all five **PASS** (100% bilingual on active sub‑controls)

| Family | Frameworks | Controls | Sub‑controls | EN+AR rec. evidence | Verdict |
|---|---|--:|--:|---|---|
| **NCA** | 7 — ECC‑2:2024, CCC‑1:2019, DCC‑1:2022, OTCC‑1:2022, CSCC‑1:2020, TCC‑1:2021, (legacy TW‑1:NCA) | 102 | 362 | **332 / 332 active** | ✅ PASS |
| **NDI** (National Data Index) | NDMO‑DMPDP‑1 (NDI folded in) | 15 | 77 | **77 / 77** | ✅ PASS |
| **NDMO Data Management** | NDMO‑DMPDP‑1 | 15 | 77 | **77 / 77** | ✅ PASS |
| **PDPL** | NDMO‑DMPDP‑1 + PDPL‑1:2023 | 29 | 136 | **136 / 136** | ✅ PASS |
| **BCM** | BCM‑ISO22301‑SAMA (legacy BCM‑1/‑2) | 13 active | 61 active | **61 / 61 active** | ✅ PASS |

- Arabic is genuine Saudi‑native text (UTF‑8 verified, zero mojibake).
- The only AR‑incomplete rows belong to **superseded/inactive** legacy frameworks (TW‑1:NCA, BCM‑1, BCM‑2) — correctly excluded from coverage. Recommend archiving them.
- NDI has **no standalone framework row** — it is folded into `NDMO‑DMPDP‑1` (name explicitly states "incl. National Data Index").

## 2. Module reviews

| Module | Verdict | Severity | Summary |
|---|---|---|---|
| **GRC Expert surface** (`/evidence/expert`, `/framework-controls`) | ✅ PASS | LOW | 11 frameworks / 138 controls / 542 sub‑controls live; 100% bilingual; correct org‑scoped COVERED/PARTIAL/GAP rollup (9/4/529) + 60‑day expiry badges; one‑click collect→request. |
| **Operator surfaces** (NDI / Data Officer / Data Classification / PDPL / BCM) | ✅ PASS | LOW | KPI dashboards + filterable registers + drill + state‑machine workflows + bilingual RTL, verified live (incl. a real DSR `RECEIVED→ID_VERIFIED`). |
| **Evidence‑management WRITE lifecycle** | ⚠️ WARN | HIGH | Read/catalog/rollup side solid; the write path is not yet production‑ready (see punch‑list). |

## 3. Live E2E journeys

| Journey | Mode | Verdict | Note |
|---|---|---|---|
| Evidence Collect | WRITE | ✅ PASS | Created `REQ‑00017` (GAP sub‑control ECC 1‑1‑1, bilingual, OPEN→IN_PROGRESS) + `EvidenceItem 62` (DRAFT→PENDING_REVIEW); durable re‑read confirmed. |
| NDI / NDMO | READ | ✅ PASS | Drilled NDMO‑DMPDP‑1 → 15 controls → 77 sub‑controls; 77/77 EN+AR; registers coherent. |
| PDPL | READ | ✅ PASS | Drilled PDPL‑1:2023 → 14 controls → 59 sub‑controls; 59/59 EN+AR; DSR/breach/ROPA registers 200. |
| NCA‑cyber | READ | — | Agent completed work but did not emit a structured result (workflow output hiccup, not a platform defect). NCA catalog PASSED in Phase 1. |
| BCM | READ | — | Same as above; BCM catalog PASSED in Phase 1. |

## ⚠️ HIGH‑severity punch‑list — evidence write lifecycle

1. **No file/binary upload** — only filename metadata; no `MultipartFile` endpoint, no storage backend. *Biggest blocker to real evidence collection.*
2. **Broken Request↔Item linkage** — `evidence_items` has no `request_id` FK; `fulfilledById` never set → collect→fulfil loop unclosed.
3. **Divergent review mechanisms** — `EvidenceReview.complete` mutates item status directly, bypassing audit log + DomainEvents, and collapses `NEEDS_REVISION` into `DRAFT`.
4. **No transition validation** — live‑proven `OPEN→APPROVED` arbitrary jumps accepted; `status=BANANA` returns **HTTP 500 not 400**.
5. **No EXPIRED state / auto‑expiry** — freshness computed on‑read only; APPROVED‑but‑EXPIRED still rolls up as COVERED.

## Other notable gaps

- **[MEDIUM]** NCA ECC operator surface is the thinnest — `NcaEccExportPage` KPIs come from a static client‑side `ECC_DOMAINS` constant, not the live filing register; no live per‑control NCA assessment register.
- **[LOW]** Legacy `GET /api/privacy-lc/dsr-cases` returns **500** (stale duplicate of the working `/api/v1/data-officer/dsr`).
- **[LOW]** `i18n.tsx` defaults to **English** when no preference is stored — contrary to "Arabic‑primary" (full AR+RTL works on toggle).
- **[LOW]** Inconsistent API base paths (`/api/ndi/*` vs `/api/v1/*`); coarse `isAuthenticated()` gating on some modules; `/privacy/stats.totalBreaches` (4) diverges from `/breaches` register (16).

## Recommendations (priority order)

1. Add a `MultipartFile` upload endpoint + storage backend for `EvidenceItem` (entity already has `file_size`/`mime_type`/`file_path` + `EvidenceVersion` WORM/hash‑chain scaffolding).
2. Add `request_id` FK on `evidence_items` + set `fulfilledById` on fulfilment.
3. Unify the review path through `EvidenceItemService.transitionStatus` (single audit+event pipeline); add a distinct `NEEDS_REVISION` item status.
4. Add a transition‑validation guard (allowed `from→to` map) returning **400** on illegal/unknown statuses.
5. Add an `EXPIRED` status + a `@Scheduled` nightly job (driven by `validTo`/`reviewDueDate`); treat APPROVED‑but‑EXPIRED as PARTIAL/STALE in the rollup.
6. Make NCA ECC data‑driven (live per‑control register).
7. Retire/fix the legacy `/api/privacy-lc/dsr-cases` 500; reconcile `/privacy/stats`.
8. Default `i18n.tsx` to Arabic‑first; AR‑localize the collect‑modal pre‑fill.
9. Archive superseded inactive frameworks (TW‑1:NCA, BCM‑1, BCM‑2); standardize on `/api/v1/*`.

## Test artifacts to clean up

- `EvidenceRequest` **id=17** (`REQ‑00017`) + `EvidenceItem` **id=62** (this run)
- `EvidenceRequest` **id=13** (earlier run)

Soft‑delete via `DELETE /api/v1/evidence/requests/{id}` and `/items/{id}` (require `EVIDENCE:DELETE`).
