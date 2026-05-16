# Sustainability & Environmental Governance — Manual QA Checklist

Slice-13 deliverable for the Saudi-market Sustainability module. Covers
every surface, button, and workflow rule across slices V55–V64.

## How to use this checklist

1. Run dev DB bootstrap (V55–V64 applied automatically by sqlcmd in
   non-Flyway dev profile).
2. `mvn spring-boot:run -Dspring.profiles.active=dev` (backend).
3. `cd tss-grc-ui-v2 && npm run dev` (frontend on port 5173).
4. Log in as `admin / Admin@12345`.
5. Navigate to `/esg` — the Sustainability module.
6. Walk through each section below in order. Tick each item you verify.
7. Toggle language (EN ⇄ AR) at least once on every tab so RTL layout
   verification is covered.

Each section is timeboxed for a single QA pass (~5 min/tab).

---

## 1. Sustainability Dashboard (Overview tab)

**Path**: `/esg` → default Overview tab

- [ ] ESG Overall Score ring gauge renders with score band badge.
- [ ] Score breakdown details expand and show 7 sub-scores.
- [ ] Top Gaps panel shows bilingual gap labels (CMA_INCOMPLETE, etc.).
- [ ] GHG Total Emissions card shows total tCO2e + YoY arrow.
- [ ] If Scope 3 coverage <70%, the GHG card shows the Rule 1 warning.
- [ ] Scope Breakdown widget shows per-scope bars with YoY %.
- [ ] Verification ring shows verified % of GHG records.
- [ ] SGI widget shows On Track / At Risk / Delayed buckets.
- [ ] CMA widget shows readiness % + Partial / Evidence Required chips.
- [ ] TCFD widget shows per-pillar disclosed/total + Rule 7 alert if Strategy resilience gap.
- [ ] Climate Risk Heatmap shows 5×5 likelihood × impact grid.
- [ ] Corrective Action Tracker shows 4-state buckets; Overdue cell goes red when count > 0.
- [ ] Evidence Completeness shows approved % + 30-day expiry counter.
- [ ] Annex Readiness Gauge shows semi-circle SVG with composite %.
- [ ] Materiality preview shows top-4 topics with tier dot.
- [ ] Emissions trend stacked bar chart renders at the bottom.
- [ ] All numeric values are tabular-aligned in Arabic and English.

---

## 2. Annual Sustainability Annex tab

**Path**: `/esg` → Annex

- [ ] Reporting years list populates from V57 seed.
- [ ] Status badge per year renders correctly (ACTIVE / REVIEW / etc.).
- [ ] Select a reporting year → reports list refreshes.
- [ ] Report-type dropdown shows all 10 types with bilingual labels.
- [ ] Generate ANNUAL_ANNEX → new DRAFT row appears with version v(N+1).
- [ ] Generate BOARD_ESG → new DRAFT row, distinct report_code (BRD-).
- [ ] Generate CMA_MATRIX → row with prefix CMA-.
- [ ] Generate TCFD_REPORT → row with prefix TCFD-.
- [ ] Generate GHG_REPORT → row with prefix GHG-.
- [ ] Generate SGI_PROGRESS → row with prefix SGI-.
- [ ] Generate CLIMATE_RISK → row with prefix CLI-.
- [ ] Generate CAPA_STATUS → row with prefix CAPA-.
- [ ] Generate EVIDENCE_PKG → row with prefix EVP-.
- [ ] Generate DASH_EXPORT → row with prefix DASH-.
- [ ] Preview opens markdown content.
- [ ] Export MD downloads `.md` file with correct content.
- [ ] Export HTML downloads styled HTML file (tables, headings render).
- [ ] Export JSON downloads parseable JSON with `reportType` field.
- [ ] Submit moves DRAFT → SUBMITTED.
- [ ] Approve moves SUBMITTED → APPROVED.
- [ ] Publish moves APPROVED → PUBLISHED.
- [ ] Re-publishing an APPROVED report auto-supersedes the prior PUBLISHED of the same type+year.

---

## 3. Corrective Actions tab

**Path**: `/esg` → Corrective actions

- [ ] 9 seed actions render across CRITICAL/HIGH/MEDIUM/LOW priorities.
- [ ] Status filter chips work (Open / In progress / Completed / Verified / Closed / Cancelled).
- [ ] "Clear filter" CTA appears when filter eliminates all rows.
- [ ] Cost + effort columns render with SAR-compact format (e.g. "SAR 4.3M").
- [ ] Cost full value appears in `title=` hover.
- [ ] Evidence paperclip icon + ref renders inline when `evidenceRef` set.
- [ ] Start button transitions OPEN → IN_PROGRESS.
- [ ] **Rule B** — Complete button is disabled with tooltip when `evidenceRef` empty.
- [ ] **Rule A** — Verify modal opens; submitting with same name as owner shows danger toast.
- [ ] **Rule A** — Verify with different name flips status to VERIFIED.
- [ ] Close transitions VERIFIED → CLOSED.
- [ ] **Rule C** — Closed/Cancelled rows have no transition buttons.
- [ ] Cancel asks confirmation (bilingual modal), then transitions any state → CANCELLED.

---

## 4. SGI Commitments tab

**Path**: `/esg` → SGI

- [ ] 7 canonical commitments render grouped by sector.
- [ ] KPI strip shows tier counts + Achieved + Retired.
- [ ] Progress bar color reflects bucket (green ≥75% / blue ≥40% / amber <40%).
- [ ] **Rule A** — Achieve button is disabled until current_value meets target in direction.
- [ ] Achieve modal accepts approver name → status flips to ACHIEVED.
- [ ] Retire modal requires both name and reason (Rule B).
- [ ] Direction-aware progress: "renewable %" rises to target; "water intensity" falls to target — both show correct %.
- [ ] ACHIEVED row has success-bg styling.
- [ ] RETIRED row shows retired reason inline.

---

## 5. CMA Disclosures tab

**Path**: `/esg` → CMA

- [ ] 11 FY2025 disclosures render grouped by article (91, 92, 93, 94).
- [ ] Readiness KPI strip shows 6 metrics (Readiness%, Disclosed, Partial, Not Disclosed, Evidence Required, Under Review).
- [ ] 5 status transition buttons per row + Attach Evidence + Record Gap.
- [ ] Mark Disclosed on a row without evidence → Rule 6 auto-flips to EVIDENCE_REQUIRED.
- [ ] **Rule 5** — Mark Partial on a row without a gap → opens gap-record modal automatically.
- [ ] After saving gap, the Partial transition fires.
- [ ] Mark Not Disclosed without gap → gap modal opens.
- [ ] Attach Evidence on an EVIDENCE_REQUIRED row flips status back to UNDER_REVIEW.
- [ ] Bilingual gap text renders correctly in both EN and AR.

---

## 6. TCFD Alignment tab

**Path**: `/esg` → TCFD

- [ ] 11 recommendations render grouped by pillar (Governance, Strategy, Risk Mgmt, Metrics & Targets).
- [ ] Alignment KPI strip + Rule 7 banner appears when Strategy resilience (c) is PARTIAL.
- [ ] Same 5 transition buttons + Attach Evidence + Record Gap (mirrors CMA UX).
- [ ] Rule 7 banner disappears once Strategy.c becomes DISCLOSED.

---

## 7. ESG Materiality tab

**Path**: `/esg` → Materiality

- [ ] 5 seed topics render with stakeholder × business scores + computed score + tier badge.
- [ ] Tier bucketing: 4.6 → CRITICAL, 4.0 → HIGH, 3.2 → HIGH, 2.6 → MEDIUM.
- [ ] Category filter chips (ENV / SOC / GOV) work.
- [ ] Score modal opens with two 0-5 number inputs.
- [ ] Saving a score auto-computes materiality_score + tier (no manual entry).
- [ ] Auto-transition DRAFT → SCORED after first scoring.
- [ ] Approve modal requires approver name; SCORED → APPROVED.
- [ ] Approve is disabled on DRAFT rows (must score first).
- [ ] Matrix visualization grid renders dots at (business, stakeholder) coordinates.
- [ ] Dot colour matches tier (red CRITICAL / amber HIGH / blue MEDIUM / grey LOW).
- [ ] Hover title shows topic name + code + score.

---

## 8. ESG Metrics tab

**Path**: `/esg` → Metrics

- [ ] 15 seed metrics render (5 each E/S/G).
- [ ] Category filter chips work.
- [ ] Per-pillar mean-progress gauges show meaningful values.
- [ ] Progress bar color matches bucket (green ≥100% / blue ≥50% / amber <50%).
- [ ] Direction-aware: water intensity (lower-to-target) shows correct progress.
- [ ] Record button opens modal with current_value + as_of date inputs.
- [ ] Saving a reading updates the row; status snapshot may flip (e.g. AT_TARGET).
- [ ] Archive asks confirmation, then flips status to ARCHIVED.

---

## 9. Climate Risks tab

**Path**: `/esg` → Climate

- [ ] 5 seed climate risks/opportunities render.
- [ ] KPI strip shows risks vs opportunities + high-score count + Rule-8 needs-ERM-link count.
- [ ] Risk score badge color reflects band.
- [ ] **Rule 8** — Link ERM button only appears on rows with score ≥ 15 AND no `ermRiskId`.
- [ ] Link ERM modal opens — enter ERM risk ID — submits → button disappears.
- [ ] State transitions: IDENTIFIED → ASSESSED → TREATED → CLOSED.
- [ ] Retire button is hidden on CLOSED / RETIRED rows.

---

## 10. GHG Inventory tab

**Path**: `/esg` → GHG

- [ ] 9 seed records render (3 Scope 1, 2 Scope 2, 4 Scope 3).
- [ ] Aggregate totals: Scope 1 ≈ 14,220 / Scope 2 ≈ 38,910 / Scope 3 ≈ 142,480.
- [ ] Scope 3 coverage shows 67% (weighted) + Rule-1 partial flag visible.
- [ ] **Rule 2** — Verify button is disabled for rows without evidenceRef.
- [ ] State transitions: DRAFT → SUBMITTED → VERIFIED → APPROVED.
- [ ] YoY values render with direction colour (red for increase, green for decrease).

---

## 11. Evidence Repository tab

**Path**: `/esg` → Evidence

- [ ] 10 seed evidence rows render across modules.
- [ ] KPI strip shows 6 metrics (Total / Approved / Pending / Rejected / Expiring 30d / Expired).
- [ ] Status filter chips work.
- [ ] PENDING_REVIEW row has Approve + Reject buttons.
- [ ] Approve modal requires approver name; flips to APPROVED.
- [ ] Reject modal requires reviewer name + reason; flips to REJECTED.
- [ ] **Rule 9** — "Sweep expired" button asks confirmation, then flips APPROVED rows with `valid_to` in the past to EXPIRED.
- [ ] Toast shows count of expired rows after sweep.
- [ ] Expiry chip on rows with `valid_to` within 30 days shows warning color.
- [ ] External-verification badge appears below title when provider set.

---

## 12. Cross-cutting checks

### Language toggle

- [ ] EN → AR toggle works on every tab without page reload.
- [ ] Layout direction flips (RTL applies; icons mirror where appropriate).
- [ ] Modals carry over correctly to new direction.
- [ ] KPI label letter-spacing/uppercase drops in RTL (Arabic labels read naturally).

### Audit fields

- [ ] Every create row has `created_at` populated.
- [ ] Every state transition updates `updated_at`.
- [ ] Approvals stamp `approved_at` + `approved_by`.

### Multi-tenancy

- [ ] All list endpoints scoped to `organisation_id = 1` (admin's org).
- [ ] Logging in as a different org returns empty lists for slices not seeded in that org.

### Soft delete

- [ ] DELETE on any row flips `is_deleted=1`; row disappears from list.
- [ ] Soft-deleted rows don't break aggregate computations.

---

## 13. Backend unit tests

Run:

```bash
cd tss-grc-backend
mvn -q test
```

Expected passes for the slice-13 additions:

- `GhgEmissionRecordServiceTest` — 5 tests (Rule 1 + Rule 2 + state machine)
- `SgiCommitmentServiceTest` — 9 tests (direction-aware progress + Rule A/B)
- `CmaDisclosureServiceTest` — 9 tests (Rule 5 + Rule 6 + readiness math)
- `CorrectiveActionServiceTest` — 10 tests (Rule A + Rule B + Rule C)

Total new tests in slice 13: **33 unit tests**.

---

## 14. Known limitations / follow-ups

These are documented gaps — not bugs:

- **Rule 4** (auto-suggest CAPA on Behind-Plan SGI) is **not active** — the CapaTab can be manually linked via `source_type=SGI_COMMITMENT` but no auto-create job exists.
- **Rule 10** (annex critical-gap blocks approval) is **not active** — the workflow exists but doesn't pre-check open gaps before approval.
- **Rule 12** (immutable published report audit log) is **partial** — content_markdown/html/json are frozen at PUBLISHED, but no separate audit log table for the publish event.
- **Rule 13** (overdue CAPA escalation) is **not active** — overdue flag is visible on UI; no escalation pipeline.
- **Rule 14** (low ESG readiness exec warning) is **partial** — score appears on dashboard, no banner if below threshold.
- **Climate-risk treated rate** in dashboard score uses a placeholder value pending ClimateRiskOpportunityService.aggregate() endpoint.
- Frontend Vitest/Playwright test setup is not part of this slice — recommend adding `npm install -D vitest @testing-library/react` and porting at least one component (e.g. KpiTile) as a demonstration.
- Backend not compiled locally during slice development — Spring DI errors would surface on first `mvn spring-boot:run`. Recommend running a full compile after applying V55–V64 + this slice.

---

## 15. Verification of Saudi-context requirements

- [ ] Arabic translations exist for every label, badge, status, tier, sector.
- [ ] Saudi-specific entities: Nitaqat, HRSD, NCEC, SDAIA, CMA, SGI all mentioned in seed data.
- [ ] Article numbering follows CMA Corporate Governance Regulations 2024 amendment.
- [ ] Hijri date conversion is preserved on existing surfaces (PDPL breach console, etc.).
- [ ] No regulator-name uses non-Saudi terminology where Saudi equivalent exists.

---

## Sign-off

| Tester | Date | Pass/Fail | Notes |
|---|---|---|---|
| | | | |
