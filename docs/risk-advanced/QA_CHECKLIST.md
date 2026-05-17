# Risk-Advanced Module — QA Checklist

Manual-QA gate for the Risk-Advanced extensions on top of the base ERM
module — risk appetite, periodic assessments, and ISO 31000 treatment
plans (`module.risk.service.RiskAssessmentService`,
`RiskTreatmentService`, `RiskAppetiteService`, migrations V80 +
companions).

This document is walkable without source access — every server-side
rule has a checklist item.

---

## Risk Assessments

ISO heatmap math: `likelihood ∈ [1,5]`, `impact ∈ [1,5]`,
`score = likelihood × impact ∈ [1,25]`.

### Rule A1 — scale validation
- [ ] `POST /api/v1/risk/risks/:id/assessments` requires both
      `likelihood` and `impact`; missing either returns 400.
- [ ] Values outside `[1,5]` return 400 with
      `likelihood must be 1..5, got N` or
      `impact must be 1..5, got N`.

### Rule A2 — residual snapshot promotion
- [ ] Recording a new assessment for risk #X promotes its
      `likelihood` / `impact` into the parent risk's
      `residual_likelihood` / `residual_impact`. (Refresh the risk
      detail page — the heatmap dot moves.)
- [ ] `residual_score` is a DB-computed column (`likelihood × impact`)
      and updates automatically.
- [ ] Recording assessments dated *today* on a risk that already has
      an assessment from yesterday makes today's row the latest, and
      the residual mirrors today's values.

### Correction
- [ ] `PATCH /assessments/:id` with new likelihood/impact updates the
      record and re-validates the 1..5 scale.
- [ ] Correcting the *most-recent* assessment also mirrors the change
      back to the parent risk's residual columns.
- [ ] Correcting an *older* assessment does **not** mirror back — the
      parent risk's residual stays pinned to the newest assessment.
- [ ] `assessmentNotesEn` accepts free text and persists unchanged.

### Tenant isolation
- [ ] Recording or listing assessments for a risk owned by another
      tenant returns 400 with "belongs to another organisation".
- [ ] Lookup of a risk-assessment id from a different tenant returns
      404 / 400.

## Risk Treatments (ISO 31000)

Treatment types: AVOID / MITIGATE / TRANSFER / ACCEPT.
Statuses: PLANNED → IN_PROGRESS → COMPLETED, with CANCELLED as escape
from any non-terminal state.

### Creation
- [ ] `POST /api/v1/risk/risks/:id/treatments` requires `riskId`,
      `treatmentType ∈ {AVOID, MITIGATE, TRANSFER, ACCEPT}`, and a
      non-blank `descriptionEn`.
- [ ] Any other `treatmentType` (e.g. "DELEGATE") returns 400.
- [ ] `status` defaults to PLANNED when omitted.

### State machine
- [ ] PLANNED → IN_PROGRESS and PLANNED → CANCELLED both succeed.
- [ ] IN_PROGRESS → COMPLETED, IN_PROGRESS → PLANNED, and
      IN_PROGRESS → CANCELLED all succeed.
- [ ] COMPLETED and CANCELLED are terminal — every transition out of
      them returns 400 with `IllegalTransition`.
- [ ] COMPLETED stamps `completed_at` to "now" (the V80 migration
      added this column; ALTER+backfill verified).

### Editing
- [ ] PATCH on a treatment updates `descriptionEn`, `ownerId`,
      `dueDate`; `treatmentType` cannot change once recorded (audit
      history).

### Rule T1 — risk-in-treatment integrity
- [ ] Deleting the only live (non-COMPLETED, non-CANCELLED) treatment
      from a risk in TREATMENT status returns 400:
      "Risk N is in TREATMENT — cannot delete the last live treatment.
      Move the Risk to ACCEPTED or MONITORING first, or add another
      treatment."
- [ ] Deleting one of two live treatments on the same risk is
      allowed.
- [ ] Deleting the only live treatment is allowed when the parent
      risk is in a status other than TREATMENT (OPEN, ACCEPTED,
      MONITORING, …).

## Risk Appetite (companion service)

- [ ] Appetite statements are tenant-scoped and bilingual
      (`statementEn` / `statementAr`).
- [ ] Numeric thresholds (low/medium/high) are positive integers; the
      UI heatmap re-tints when they change.
- [ ] Breach detection runs across all open risks and flags any whose
      residual score exceeds the configured tolerance.

---

## Cross-cutting checks

### Tenant isolation
- [ ] Every assessment / treatment lookup validates the parent risk's
      `organisation_id` against `TenantContext.getCurrentTenant()`
      before proceeding.
- [ ] Cross-tenant fetch returns a clean 400 rather than leaking
      "not found".

### UI integration
- [ ] Risk detail page (`/risk/:id`) shows Assessments and Treatments
      tabs.
- [ ] "Record assessment" button posts to the API and refreshes the
      live table — no mock fallback when data is present.
- [ ] Treatment transition buttons (Start, Complete, Cancel) honour
      the state machine — disabled or hidden for terminal rows.

### Bilingual content
- [ ] All user-facing labels exist in EN + AR.
- [ ] AR phrasing follows Saudi-native conventions (no literal
      translations from English UX patterns).

### Tests
- [ ] `mvn test -Dtest='com.tss.grc.module.risk.service.RiskAssessmentServiceTest'`
      passes — locks Rules A1 and A2.
- [ ] `mvn test -Dtest='com.tss.grc.module.risk.service.RiskTreatmentServiceTest'`
      passes — locks the ISO 31000 state machine and Rule T1.

### Documentation
- [ ] STATUS.md "Risk-Advanced" section lists the three services and
      this checklist.
- [ ] CLAUDE.md captures `risk.risk_assessments` and
      `risk.risk_treatments` under "Backend module structure" + the
      V80 `completed_at` ALTER.

---

Last updated: alongside Sprint 2 close-out + Risk-Advanced unit tests.
