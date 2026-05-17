# SDAIA NDI / نضيء Module — QA Checklist

This checklist locks the manual-QA gate for the NDI module before any
release. It is organised by slice and ends with cross-cutting checks
that must hold across the full module.

**Important guardrail (Rule 0)** — UI banners and stored
`is_placeholder` flags must remain visible on every domain, framework
item, and submission package until the official SDAIA/NDI control
library has been imported. Do not claim official completeness during
QA dry-runs.

---

## Slice 1 — Assessment Cycles
- [ ] Create a new cycle — name (EN/AR), reporting year, scope.
- [ ] Cycle status walks the 13-state machine forward only; ARCHIVED
      is the only escape.
- [ ] Cloning a cycle creates a new one with a different reporting
      year and copies the scope text.
- [ ] "One active cycle per year" rule rejects a second cycle in the
      same year that isn't ARCHIVED.

## Slice 2 — Framework Library
- [ ] List shows 20 placeholder items across COMPLIANCE / MATURITY /
      OPERATIONAL_EXCELLENCE.
- [ ] `Placeholder` badge appears on every seeded row.
- [ ] `Mark official` flips the flag; `Mark placeholder` restores it.
- [ ] Import JSON merges by `requirementCode` and returns
      `{inserted, updated, failed, errors[]}`.

## Slice 3 — Maturity Assessment
- [ ] `Start for cycle` bulk-creates rows from MATURITY framework
      items (idempotent — calling twice doesn't duplicate).
- [ ] PUT a row with `currentLevel` and `targetLevel`; Rule 1 sets
      `improvementActionSuggested = true` when `target > current`.
- [ ] `currentLevel >= 4` requires `evidenceRef`.
- [ ] D.Validation — `targetLevel < currentLevel` requires `justification`.
- [ ] Workflow: DRAFT → IN_PROGRESS → SUBMITTED → UNDER_REVIEW →
      APPROVED. RETURNED loops back to IN_PROGRESS.

## Slice 4 — Compliance Assessment
- [ ] `Start for cycle` bulk-creates rows from COMPLIANCE framework
      items.
- [ ] Rule 3: setting status COMPLIANT/PARTIAL without
      `evidenceRef` auto-flips to EVIDENCE_REQUIRED.
- [ ] Rule 4: PARTIAL / NON_COMPLIANT sets `needsGap = true`.
- [ ] Rule 5: NOT_APPLICABLE without `naJustification` is rejected.
- [ ] Dual axis: substantive `complianceStatus` (6 values) ×
      workflow `lifecycleStatus` (6 values, RETURNED loops back).

## Slice 5 — Operational Excellence KPIs (Rule 13)
- [ ] Higher-is-better KPI with `actual >= target` → ACHIEVED.
- [ ] Progress fraction ≥ 0.90 → ON_TRACK; ≥ 0.70 → AT_RISK;
      below → BEHIND_PLAN.
- [ ] Lower-is-better KPI (target < baseline) inverts the comparison.
- [ ] `actual` null + `dataSource` null → DATA_MISSING.
- [ ] `actual` null + `dataSource` set → NOT_STARTED.
- [ ] `update-actual` recomputes status server-side; clients never
      set `status` directly.
- [ ] Aggregate returns `opexScorePct` = mean direction-aware
      progress fraction × 100.

## Slice 6 — Data Governance Domains
- [ ] 8 placeholder domains seeded (GOV/CAT/QUA/SHA/OPEN/PDP/SEC/CLS),
      weights sum to 100.
- [ ] Component blend weights (maturity/compliance/opex) sum to
      1.00 ±0.005 per domain (DB CHECK enforces).
- [ ] `Mark official` flips the placeholder flag; banner disappears
      for that row.
- [ ] Rollup reallocates component weight pro-rata when a component
      has no data.
- [ ] Unmatched-strings card shows any upstream domain/category that
      doesn't match an alias.
- [ ] PUT with only `{weight: ...}` does **not** clobber
      `sortOrder` (entity defaults moved into @PrePersist).

## Slice 7 — Evidence Repository (Rule 6)
- [ ] 8 evidence items seeded across all 9 types, classifications and
      one SUPERSEDED chain.
- [ ] `Approve` flips PENDING → ACTIVE and stamps reviewer.
- [ ] `Reject` requires a reason.
- [ ] `Supersede(oldId, newId)` archives the old, links the new.
- [ ] `/check-expiry` mass-flips past-expiry ACTIVE rows to EXPIRED.
- [ ] Dashboard warning card lists items expiring within 60 days.

## Slice 8 — Gaps + Corrective Actions (Rules 7 / 8 / 9)
- [ ] Rule 7: completing all CAPAs auto-flips the parent gap to
      RESOLVED with `actualCloseDate` stamped today.
- [ ] Rule 7: partial CAPA progress drives `gap.rollupProgress` =
      mean of CAPA `progressPct`.
- [ ] Rule 8: CRITICAL gap without any CAPA with target ≤ 90 days
      from open_date gets an audit-style marker appended to notes.
- [ ] Rule 9: progress outside [0,100] is rejected (DB CHECK + service).
- [ ] Auto-derive: setting CAPA status COMPLETED forces
      `progressPct=100` and stamps `completionDate`.

## Slice 9 — Improvement Plan (Rules 10 / 11 / 12)
- [ ] Rule 10: plan totals recompute on every CAPA-status / item
      change via `/recompute-all`.
- [ ] Rule 11: `/reorder` renumbers `sequenceOrder` in steps of 10.
- [ ] Rule 12: `targetQuarter` rejected unless matching
      `^Y\d{4}-Q[1-4]$` (e.g. `Y2026-Q3`).
- [ ] Status machine: DRAFT → APPROVED → IN_FLIGHT → COMPLETED |
      SUSPENDED → ARCHIVED. SUSPENDED can resume to IN_FLIGHT.
- [ ] All CAPAs COMPLETED while plan is IN_FLIGHT auto-flips plan to
      COMPLETED.

## Slice 10 — Submission Package (Rules 15 / 16)
- [ ] Rule 15 (pre-flight): `criticalGapsWithoutCapa > 0` blocks
      finalize. Other counts surface as warnings only.
- [ ] Workflow: DRAFT → READY → SUBMITTED → ACKNOWLEDGED | REJECTED.
      REJECTED `/reopen` returns to DRAFT, clearing reviewer/
      approver/submitter stamps.
- [ ] `/submit` requires English attestation text.
- [ ] Submitting auto-ARCHIVEs prior versions of the same cycle's
      package.
- [ ] Rule 16: `/regenerate` returns 409 with
      "Snapshot is immutable in status …" once past READY.
- [ ] Manifest checksum is `sha256(snapshotJson + "|" + manifestJson)`
      and changes whenever the snapshot changes.
- [ ] `isPlaceholder` on the package inherits from the domain catalog
      (any placeholder domain → placeholder package).

## Slice 11 — Reports
- [ ] 12 canonical report definitions seeded.
- [ ] `Run` on a saved report stamps `lastRunAt` + `lastRunStatus`
      and `lastRunMessage`.
- [ ] `/run-adhoc?type=…` returns `{reportType, summary, rows[]}`
      without persisting.
- [ ] Failed run sets `lastRunStatus = FAILED` and records the error
      message; subsequent successful run flips back to SUCCESS.

## Slice 12 — Dashboard
- [ ] Single GET returns all 14 widgets with a single round trip.
- [ ] `topUnderperforming` returns the 3 lowest-scoring active
      domains.
- [ ] `upcoming` mixes evidence expirations and CAPA due dates,
      sorted by `dueDate`, capped at 10.
- [ ] Days-left badges color: red if < 14 days, yellow < 30, info
      otherwise.

## Slice 13 — Settings
- [ ] 12 settings seeded across 5 categories (GENERAL, THRESHOLDS,
      WORKFLOW, ATTESTATION, GUARDRAIL).
- [ ] Type validation: INTEGER / DECIMAL / BOOLEAN reject invalid
      strings; STRING / JSON pass through.
- [ ] `is_locked = true` rejects writes to that row.
- [ ] `/reset` restores the row to its `defaultValue`.
- [ ] Setting `official_library_imported = true` should suppress
      every placeholder banner sitewide (UI-level check; confirm by
      re-reading the framework, domains, and submission pages).

## Slice 14 — Audit Trail
- [ ] 10 seed events visible covering cycle / evidence / gap / capa /
      submission lifecycles.
- [ ] Filters by entity type, action, severity drive the search.
- [ ] Severity colour: INFO blue, WARN yellow, ERROR red.
- [ ] `correlation_id` lets a single action (e.g. a submit) be traced
      through multiple downstream audit rows.
- [ ] Posting an ad-hoc event via `/api/ndi/audit` (e.g. report
      export) appears immediately in the Audit tab on refresh.

---

## Cross-cutting checks

### Tenant isolation
- [ ] Every NDI endpoint filters by `organisation_id` (no query
      returns rows owned by a different tenant).
- [ ] Every entity has `is_deleted` and a `@Where(clause =
      "is_deleted = 0")` annotation so JPA reads never return
      soft-deleted rows.

### Bilingual content
- [ ] Every user-facing string has an EN and (where the spec calls
      for it) AR variant.
- [ ] AR strings use Saudi-native phrasing per the user feedback
      note in MEMORY.md.
- [ ] RTL layout flips correctly when language is switched on every
      tab.

### Placeholder guardrail
- [ ] Every domain row, framework item, and submission package
      flagged `is_placeholder = 1` shows the bilingual banner:
        EN: "Placeholder — requires official SDAIA/NDI control library import"
        AR: "عنصر مؤقت — يتطلب استيراد مكتبة الضوابط الرسمية من سدايا/نضيء"
- [ ] Submission package inherits `is_placeholder` from the domain
      catalog.

### Workflow integrity
- [ ] Every state machine refuses backward transitions except where
      explicitly documented (REJECTED → DRAFT for submissions,
      RETURNED → IN_PROGRESS for compliance, SUSPENDED → IN_FLIGHT
      for plans).
- [ ] Soft-deletes cascade where documented (gap → its CAPAs;
      plan → its items).

### Performance
- [ ] Dashboard endpoint returns within 1 second for a cycle with
      10× the demo seed volume.
- [ ] Submission `/regenerate` returns within 2 seconds for the same
      scale.

### Tests
- [ ] `mvn test -Dtest='NdiOperationalExcellenceKpiServiceTest'`
      passes — locks Rule 13 direction-aware status math.
- [ ] `mvn test -Dtest='NdiGapServiceTest'` passes — locks Rule 7
      rollup auto-resolve and Rule 9 progress validation.
- [ ] `npx tsc --noEmit` from `tss-grc-ui-v2/` is clean.

### Documentation
- [ ] README.md "Modules" section mentions NDI / نضيء.
- [ ] STATUS.md records the slice completion state and links to this
      checklist.
- [ ] CLAUDE.md captures the NDI schema (`ndi.*`) under the
      "Backend module structure" section so future agents pick it up.

---

Last updated: alongside slice 15 (test pyramid + this checklist).
