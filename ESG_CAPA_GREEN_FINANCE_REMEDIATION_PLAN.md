# ESG CAPA Tab + Green-Finance Residual Gaps — Remediation Plan
# خطة معالجة تبويب الإجراءات التصحيحية وفجوات التمويل الأخضر المتبقية

> **Source review:** live + code review of `/esg?tab=capa` and the residual-gap audit of
> `SUSTAINABILITY_GREEN_FINANCE_REVIEW_AND_UPLIFT_PLAN.md`, performed **2026-07-13** at
> master `b8998a45` (stack live on :8084/:5173, dev DB inspected via pyodbc).
> **Review verdict:** CAPA backend **REAL** · CAPA UI **PARTIAL** (read-and-transition only —
> the lifecycle dead-ends at Complete); green-finance module complete except the seven
> residuals below (P-1…P-7).
>
> House rules per `CLAUDE.md`: verified slices, feature branch, **no merge without explicit
> "merge"**, ratchet ≤ 62, `[REVIEW-TEST]` + CHARINDEX cleanup, idempotent migrations applied
> twice to dev via pyodbc (Flyway OFF in dev), destructive DB ops need explicit OK.

---

## §0 Implementation status

| Epic | Content | Status | Commit | Migration |
|---|---|---|---|---|
| CT-1 | CAPA edit modal + evidence attach (unblocks Rule B — the loop can close) | **DONE** (tsc/vitest green, live probe 10/10) | `e8986724` | none |
| CT-2 | "New action" button — manual CAR creation from the tab | **DONE** (batched with CT-3/CT-4; suite 2262/0/0, vitest 48/48, live probe 11/11) | _(this branch)_ | none |
| CT-3 | CAR/PAR: `action_type` CORRECTIVE \| PREVENTIVE + FE select/filter/badge | **DONE** (V297 applied ×2 to dev; aggregate `byType`; reclassify via PUT) | _(this branch)_ | **V297** |
| CT-4 | Verify-as-principal modal fix (drop the ignored typed name) | **DONE** (confirm dialog; Rule-A mirror vs principal name+email) | _(this branch)_ | none |
| CT-5 | Source-type sync: service whitelist = `ck_ca_source`; FE union/labels; source+priority filters | **DONE** (suite 2257/0/0, live probe 3/3) | `fd8e23b2` | none |
| CT-6 | CAPA register export.xlsx + source deep-links | **NOT STARTED** | — | none |
| GF-1 | `portfolio_green_impact` rollup (Σ instruments, dedup by project) | **NOT STARTED** | — | none |
| GF-2 | Green-finance evidence linkage + ISAE assurance-lineage inclusion | **NOT STARTED** | — | **V298** |
| GF-3 | Dashboard KPI completion (allocation %, S1/S2 %, permits ≤90d, trajectory) | **NOT STARTED** | — | none |
| GF-4 | Hijri display on instrument issue/maturity dates | **NOT STARTED** | — | none |
| GF-5 | `prospectus_ref` field on instruments (REG-8 completion) | **NOT STARTED** | — | rides V298 |
| GF-6 | CBAM embedded-tCO₂e derivation helper from GHG records | **NOT STARTED** | — | none |

Reserved migrations: **V297** (CAPA `action_type` — **APPLIED to dev ×2**) · **V298**
(green-finance evidence FKs + `prospectus_ref`). Next free after V298: V299.
Open decisions: **§5** (4 items — #1 resolved as recommended: preventive is manual-only).

---

## §1 Findings register (receipts from the 2026-07-13 review)

### Wave CT — the CAPA tab (`/esg?tab=capa`)

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| C-1 | **CAPA loop cannot close in-product.** Rule B requires `evidence_ref` before COMPLETED, but the tab has no edit modal and no evidence control; auto-CAPAs are born without evidence, so Complete is permanently disabled. `updateCapa()` (`lib/sustainabilityCapa.ts:121`) is called nowhere. Dev data: 18 OPEN / 8 IN_PROGRESS; the single COMPLETED and VERIFIED rows came from API probes, not the UI. | **Critical (workflow)** | `EsgPage.tsx:823-830` (disabled button), grep: zero `updateCapa` call sites |
| C-2 | **No manual creation.** `POST /api/sustainability/corrective-actions` (WRITE, `MANUAL` whitelisted) is fully built; `createCapa()` is never called from the ESG page — officers cannot raise a CAR from the register. | **High** | `CorrectiveActionController.java:78`; grep: zero call sites |
| C-3 | **Verify modal collects a name the server ignores.** FE posts `{verifiedBy}`; the endpoint binds no body and stamps the authenticated principal (SEC-ESG-002). Typed attribution is misleading and the client-side Rule-A mirror checks the typed name while the server checks the principal. | Medium (integrity/UX) | `EsgPage.tsx:662-693`, controller `:104-108` |
| C-4 | **Blank source badges live today.** FE `CapaSourceType` union + label dicts miss `VENDOR_ESG`, `EVIDENCE` (rows exist in dev: 3+1), `ESG_TARGET`, `ENV_PERMIT` (raised by G4 sweeps) → empty Badge render. | Medium | `sustainabilityCapa.ts:23-25` vs `SELECT source_type, COUNT(*)…` |
| C-5 | **Java whitelist ≠ DB CHECK.** `ck_ca_source` allows 11 types; service `SOURCE_TYPES` (`CorrectiveActionService.java:51`) knows 9 — manual create for `ESG_TARGET`/`ENV_PERMIT` 400s though the DB allows them (G4 wrote those CAPAs via the repository, bypassing the service). | Medium | service `:51-53` vs `sys.check_constraints` |
| C-6 | No source/priority filters (backend supports both), no register export (house pattern), no deep-link from CAPA row to source record, no owner reassignment. | Low | tab renders status chips only |
| C-7 | **No preventive actions.** No `action_type` column — every row is implicitly corrective; the platform's preventive triggers (AT_RISK tiers, due-soon permits) have nowhere to land. | Medium (capability) | entity has no such column |

### Wave GF — green-finance residuals (vs the uplift plan)

| ID | Plan ref | Finding | Severity |
|---|---|---|---|
| P-1 | §4.2 | `portfolio_green_impact = Σ_instruments impact(i)` (dedup by project) — never built; only per-instrument impact + intensity exist. | Medium |
| P-2 | §4.1/§4.2 | **Green finance is outside the evidence/assurance fabric**: `green_projects` has no evidence field; none of GreenProject / GreenInstrument / InstrumentAllocation / CarbonCredit are in `SustEvidenceLinkService.ENTITY_TYPES` (7 types, all pre-green); the ISAE 3410 lineage view grades 5 object types — green finance absent. Credit "certificate" is free text. | **High (assurance)** |
| P-3 | §6 | Dashboard KPIs delivered 4/7, relocated to `/green-finance` (instruments, GAR, weighted DQ, credits retired). Missing as KPIs: allocation %, S1/S2 coverage %, permits ≤90 d, trajectory status. | Low-Medium |
| P-4 | §4.1 | Hijri display on instrument issue/maturity dates absent (shared Hijri helper unused there). | Low |
| P-5 | REG-8 | `prospectus_ref` absent on instruments (`isin`/`listing` exist). | Low |
| P-6 | §5.7 | CBAM embedded emissions manual-only; "from GHG records" derivation never wired. | Low |
| P-7 | drift | = C-5 (fixed in CT-5). | — |

---

## §2 Remediation epics

### CT-1 — CAPA edit modal + evidence attach *(fixes C-1)* — **M**
- **Target design.** Every non-terminal CAPA is editable by SUST WRITE: owner name/email,
  assignee, target date, priority, root cause AR/EN, remediation plan AR/EN, cost, effort,
  **evidence_ref**, notes. Editing a CLOSED/CANCELLED row stays 409 (already server-enforced).
- **BE.** None — `PUT /{id}` exists and maps exactly these fields.
- **FE.** Edit (pencil) button per row → `FormModal` (pattern: the SGI edit modal) →
  `updateCapa()`; `evidenceRef` field carries helper text "required before Complete (Rule B)".
  Complete button tooltip unchanged.
- **Accept.** Live: auto-CAPA (born evidence-less) → edit → set evidenceRef → Complete 200 →
  Verify (second user) 200 → Close 200 — the full loop closes through the UI for the first
  time; edit of a CLOSED row → 409 surfaced in toast.

### CT-2 — "New action" button (manual CAR) *(fixes C-2)* — **S**
- **Target design.** Header button "New action / إجراء جديد" → create modal: type (see CT-3),
  titleEn+**titleAr (mandatory — bilingual rule)**, description AR/EN, priority, owner,
  assignee, target date, source = `MANUAL` (fixed), optional evidenceRef/cost/effort.
- **BE.** None — `POST /` + `SgiCreateRequest`-style DTO already validate.
- **FE.** Modal + `createCapa()`; KPI strip refresh on success.
- **Accept.** Create via UI → row appears OPEN with `MANUAL` badge; missing titleAr blocked
  client-side and named by `problemMessage()` if forced.

### CT-3 — CAR/PAR: `action_type` *(fixes C-7)* — **S** · **V297**
- **Decision taken (review §2):** one register, one lifecycle — a **type field**, not a
  second table (ISO 14001 §10.2 practice; market-leader shape).
- **DB (V297, idempotent ×2):** `sust.corrective_action.action_type VARCHAR(20) NOT NULL
  DEFAULT 'CORRECTIVE'` + `ck_ca_action_type CHECK (action_type IN ('CORRECTIVE','PREVENTIVE'))`.
- **BE.** Entity + create DTO + `update()` mapping + `validate()`; `autoOpen()` always
  CORRECTIVE. Aggregate gains `byType` counts.
- **FE.** Type select in the CT-2 modal (default CORRECTIVE), badge column, filter chip pair,
  KPI "Preventive open" if byType present. Bilingual labels (إجراء تصحيحي / إجراء وقائي).
- **Accept.** Create PREVENTIVE via UI → persisted + badged; V297 applied twice to dev; suite
  green; corrective:preventive ratio visible in aggregate.

### CT-4 — Verify-as-principal *(fixes C-3)* — **S**
- **Target design.** The verifier IS the logged-in user — the modal must say so, not ask for
  a name. Replace the text field with a confirm dialog: "You (`{username}`) will be recorded
  as verifier. The verifier must differ from the owner (`{ownerName}`) — Rule A."
- **FE.** Drop `verifiedBy` field + dead body param from `verifyCapa()`; client Rule-A mirror
  compares `getUser().username` to owner (case/trim-insensitive, same as server `sameActor`).
- **Accept.** Verify as owner-principal → client blocks; force via API → server 400 Rule A;
  verify as second user → `verified_by` = principal username in DB.

### CT-5 — Source-type sync *(fixes C-4, C-5, P-7)* — **S**
- **BE.** `SOURCE_TYPES` += `ESG_TARGET`, `ENV_PERMIT` (now equal to `ck_ca_source`); add a
  unit test asserting the service set matches the 11-type DB constraint list (drift guard).
- **FE.** Union + AR/EN labels += 4 types; label helper gets a safe fallback (render the raw
  code, never blank); source filter dropdown + priority filter chips.
- **Accept.** Dev rows with `VENDOR_ESG`/`EVIDENCE` render labelled badges; manual create with
  `ESG_TARGET` → 201; unit drift-test locks the lists together.

### CT-6 — Register export + deep links *(fixes C-6)* — **S**
- **BE.** `GET /corrective-actions/export.xlsx` (SUST READ) via `TabularExportService` +
  `ExportNaming` ("Sustainability", "CorrectiveActions") — house pattern, audit-logged.
- **FE.** Export button; source chip becomes a deep link where a screen exists
  (SGI→`/esg?tab=sgi`, CMA→`?tab=cma`, TCFD→`?tab=tcfd`, GHG→`?tab=ghg`, targets/permits→
  horizon tabs) — resolve by `sourceType`+`sourceRef` (SGI already has `by-code` lookup).
- **Accept.** XLSX PK-magic + row count == register; export audit row `action=EXPORT`;
  chip navigation lands on the owning tab.

### GF-1 — Portfolio green-impact rollup *(fixes P-1)* — **M**
- **Target design (plan §4.2 last formula).** Org-level attributed impact across ALL
  instruments with double-count protection: per (project, metric), attributed impact is
  `Σ_i project_share(i,p) × actual_impact(p,metric)` where `Σ_i project_share(i,p)` is
  capped at 1 per project (a project financed by two sukuk cannot deliver its impact twice).
- **BE.** `GET /api/v1/sustainability/finance/portfolio-impact` (READ): per-metric totals
  (tCO₂e avoided, MWh, m³), per-instrument contribution, `dedupApplied` flag per project.
  Unit test with a 2-instrument shared project proving the cap.
- **FE.** "Portfolio impact" card on `/green-finance` → Reports tab (or Portfolio tab)
  showing the three metric totals + a share-capped note.
- **Accept.** Seed two instruments funding one project at 60 %/60 % → rollup counts 100 %
  (not 120 %); math spot-check vs pyodbc; probe green.

### GF-2 — Evidence linkage + assurance lineage *(fixes P-2)* — **L** · **V298**
- **Target design.** Green-finance figures become assurance-grade: the four object types
  carry real evidence FKs, are linkable via the existing bridge, and appear in the ISAE
  3410 lineage view.
- **DB (V298, idempotent ×2):** `evidence_item_id BIGINT NULL` on `sust.green_projects`,
  `sust.green_instruments`, `sust.instrument_allocations`, `sust.carbon_credits`
  (+`prospectus_ref` — GF-5 rides here).
- **BE.** `SustEvidenceLinkService.ENTITY_TYPES` += `GreenProject`, `GreenInstrument`,
  `InstrumentAllocation`, `CarbonCredit` (+ repo wiring, same V250 pattern);
  `AssuranceLineageRepository` UNION += 2 rows minimum: **GreenProject** (`has_data` =
  actual impact metrics present, `has_evidence` = FK/ref set, verified = n/a) and
  **CarbonCredit** (`has_evidence` = certificate evidence item or ref; retirement rows only).
  Decision #2 (§5) gates whether instruments/allocations also join the view.
  Credit retirement keeps accepting the legacy text ref (compat) but prefers the FK.
- **FE.** Attach-evidence chip on project/credit rows (existing evidence-link flow);
  lineage tab shows the new object types.
- **Accept.** Link evidence to a project via UI → `evidence_item_id` set + link row;
  lineage response contains GREEN_PROJECT/CARBON_CREDIT rows graded COMPLETE/PARTIAL/
  MISSING; readiness % recomputes; V298 applied twice.

### GF-3 — Dashboard KPI completion *(fixes P-3)* — **S**
- **BE.** Extend the sustainability dashboard aggregate (or reuse existing endpoints) with:
  `allocationRatioPct` (portfolio-level: Σ allocated / Σ net proceeds across REPORTING+
  instruments), `ifrsCoveragePct` (exists — surface it), `permitsExpiring90d` (exists in
  permit views — count it), `trajectoryStatus` (targets ON/OFF-track counts).
- **FE.** Four KPI tiles: ESG page Dashboard tab strip + mirrored on `/green-finance`
  (allocation %). Each tile deep-links to its owning tab.
- **Accept.** KPI values cross-checked vs pyodbc; tiles navigate correctly; AR/EN labels.

### GF-4 — Hijri instrument dates *(fixes P-4)* — **S**
- **FE only.** Issue/maturity render "Gregorian (Hijri)" via the shared Hijri helper (the
  attestation pattern); list + detail + create-modal preview.
- **Accept.** A seeded instrument shows the dual date in AR and EN locales.

### GF-5 — `prospectus_ref` *(fixes P-5)* — **S** (rides V298)
- **DB.** Column on `green_instruments` (V298). **BE.** DTO/entity/update. **FE.** Field in
  instrument form + detail row. **Accept.** Round-trips via UI; visible in export.

### GF-6 — CBAM derive-from-GHG helper *(fixes P-6)* — **M**
- **Target design.** Optional assist, not a mandate: when creating a CBAM line, the user can
  pick "derive from GHG inventory" → `embedded tCO₂e/t = (Σ VERIFIED/APPROVED S1+S2 of the
  latest complete year × allocation share) / production tonnes` with the share + tonnes
  entered by the user; the derivation inputs are stored in a `data_basis_note` for audit.
  Manual entry stays default (`dataBasis=MEASURED|DEFAULT_VALUES` unchanged).
- **BE.** `GET /horizon/cbam/derive?year=&sharePct=&productionTonnes=` (READ) returning the
  suggested factor + inputs echo. **FE.** "Derive…" button in the CBAM form filling the field.
- **Accept.** Derived factor equals hand-computed pyodbc value; note persisted; probe green.

---

## §3 Implementation order (dependency-ordered, one epic per fire)

| # | Epic | Why this position |
|---|---|---|
| 1 | **CT-5** | Smallest risk; unblocks correct rendering/creation for everything after; drift-guard test first |
| 2 | **CT-1** | The Critical — closes the CAPA loop; needs nothing else |
| 3 | **CT-3** (V297) | Schema first among FE-visible additions; CT-2's modal wants the type select |
| 4 | **CT-2** | Create modal lands with type field already available |
| 5 | **CT-4** | Isolated modal swap |
| 6 | **CT-6** | Export + links polish over a now-complete tab |
| 7 | **GF-2** (V298) | The High — assurance fabric; V298 also carries GF-5 |
| 8 | **GF-5** | Field exposure after V298 applied |
| 9 | **GF-1** | Pure computation + card |
| 10 | **GF-3** | KPIs read from now-complete data |
| 11 | **GF-4** | FE cosmetic |
| 12 | **GF-6** | Optional assist, last |

Suggested batching: **CT-5+CT-1** one session · **CT-3+CT-2+CT-4** one session ·
**CT-6** half · **GF-2+GF-5** one session · **GF-1+GF-3** one session · **GF-4+GF-6** half.

---

## §4 Verification discipline (house standard)

- Full suite per slice (`mvn clean package`, grep `BUILD SUCCESS` + `Tests run:` from the
  log — background wrappers can mask failures); ratchet stays ≤ **62** (new endpoints:
  export.xlsx READ-guarded, portfolio-impact READ, cbam/derive READ — all guarded).
- Live probes through the Vite proxy (:5173) as `admin` + `so_officer` (4-eyes paths:
  verify ≠ owner); `[REVIEW-TEST]` rows cleaned via CHARINDEX; sweep-created CAPAs on seed
  rows deleted by id after probing.
- **V297/V298** written idempotent, applied twice to dev via pyodbc before commit; prod
  picks them up via baseline-aware Flyway on the next jar update.
- FE: `tsc --noEmit` + `vitest` + `vite build` per slice.
- New unit tests: Rule-A principal compare (CT-4), whitelist drift guard (CT-5),
  `action_type` validation (CT-3), project-share dedup cap (GF-1), lineage grading for
  green objects (GF-2), CBAM derivation math (GF-6).

## §5 Open decisions (before the affected epic starts)

1. **(CT-3)** Should AT_RISK tiers (SGI/targets) auto-suggest **PREVENTIVE** actions
   (banner/one-click, never auto-insert), or stay manual-only? *Recommended: manual-only
   now; revisit after adoption.*
2. **(GF-2)** Lineage scope: projects + credits only (recommended — they carry the figures),
   or also instruments + allocations (noisier view, stronger completeness)?
3. **(GF-2)** Verified dimension for green rows: n/a like SGI (recommended — no verify step
   exists on these objects) or add a verify step later?
4. **(GF-3)** KPI placement: ESG Dashboard tab + mirror on `/green-finance` (recommended),
   or green-finance page only?

## §6 Out of scope

Preventive-action auto-generation from analytics; carbon-credit registry API integration
(RVCMC has no public API yet); SMTP delivery of CAPA reminders (in-app only until a relay
exists); reopening CLOSED CAPAs (compliance-module pattern exists — separate decision);
workflow-engine routing of CAPA approvals (the direct lifecycle + Rule A/B is the accepted
design per the CMA/TCFD/SGI convention).
