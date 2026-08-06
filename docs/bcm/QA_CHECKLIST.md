# BCM Module — QA Checklist
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Manual-QA gate for the BCM module after the BCM-1, BCM-2, and BCM-3
sprints landed. Walkable without source access — every rule the
service layer enforces has a matching item below. New BCM sprints
add sections at the bottom rather than editing previous ones.

> **Placeholder guardrail (Rule 0).** Until the official ISO 22301,
> DGA, SAMA, and National Risk Council control libraries are
> imported into `platform.authority_documents`, every framework
> assessment surface keeps the placeholder banner lit. Do not claim
> regulatory completeness during QA dry-runs.

---

## BCM-1 — Foundations

### Assessment Cycles
- [ ] `POST /api/v1/bcm/cycles` requires `cycleNameEn` + `reportingYear`.
- [ ] Two cycles for the same `(org, reportingYear)` rejected with
      400 "already exists".
- [ ] Lifecycle: DRAFT → IN_PROGRESS → UNDER_REVIEW → APPROVED →
      ARCHIVED. UNDER_REVIEW → IN_PROGRESS is the only backward step.
- [ ] APPROVED stamps `approvedBy` + `approvedAt` from the JWT user.
- [ ] APPROVED / ARCHIVED cycles cannot be edited via PUT — must be
      reopened first.
- [ ] Every transition writes an audit row through F7 with
      `sourceModule='bcm'` and the workflow_status_before / _after.

### Governance Records
- [ ] `policyTitleEn` required.
- [ ] `reviewFrequency` accepts ANNUAL / SEMI_ANNUAL / QUARTERLY /
      ON_CHANGE only.
- [ ] Lifecycle: DRAFT → UNDER_REVIEW → APPROVED → ACTIVE. ACTIVE →
      REVIEW_DUE (manual or by scheduler when next_review_date passes).
      CHANGES_REQUIRED loops back to DRAFT. ARCHIVED is terminal.
- [ ] ACTIVE can't jump straight back to DRAFT — must go via
      CHANGES_REQUIRED.
- [ ] APPROVED / ACTIVE records refuse PUT edits.

### BCM Risks
- [ ] `titleEn`, `threatCategory` required; threatCategory restricted
      to the 18-value enum.
- [ ] `likelihood` and `impact` ∈ [1, 5]; out-of-range returns 400.
- [ ] `controlEffectiveness` accepts EFFECTIVE / NEEDS_IMPROVEMENT /
      INEFFECTIVE / NOT_TESTED only.
- [ ] **Inherent score = likelihood × impact.**
- [ ] **Residual score**: EFFECTIVE ÷ 4 · NEEDS_IMPROVEMENT ÷ 2 ·
      INEFFECTIVE / NOT_TESTED × 1 (no reduction).
- [ ] Lifecycle: DRAFT → ASSESSED → TREATMENT_PLANNED → (ACCEPTED |
      MITIGATED) → CLOSED.
- [ ] Moving to ASSESSED requires likelihood + impact populated.
- [ ] Moving to TREATMENT_PLANNED publishes
      `DomainEvent(triggerEvent="BCM_RISK_TREATMENT_PLANNED")` so
      automation rule `BCM-T2` can fire.
- [ ] Deleting a risk in ASSESSED or TREATMENT_PLANNED refused —
      move to CLOSED first.

### Continuity Strategies
- [ ] `selectedStrategy` restricted to the 15-value enum.
- [ ] `feasibility` ∈ {HIGH, MEDIUM, LOW}; `residualRisk` ∈ {LOW,
      MEDIUM, HIGH, CRITICAL}.
- [ ] `rtoRequirementHours` + `rpoRequirementHours` cannot be negative.
- [ ] Approval lifecycle: DRAFT → UNDER_REVIEW → APPROVED | REJECTED
      → ARCHIVED. REJECTED → DRAFT loop.
- [ ] **APPROVED rejected** if both `rtoRequirementHours` and
      `rpoRequirementHours` are null — every approved strategy must
      commit to at least one recovery target.
- [ ] **High-cost rule:** when `costEstimate >= 1,000,000`, the
      audit message includes `HIGH_COST — executive approval
      required`. Verify the audit row's `message` field.
- [ ] Deleting an APPROVED strategy refused; archive first.
- [ ] Edit of APPROVED / ARCHIVED strategy refused.

---

## BCM-2 — Plans + crisis + comms tree

### Cycle awareness on legacy plan tables
- [ ] V99 migration added `cycle_id` (nullable) to `bcm.bcm_plans`,
      `bcm.dr_plans`, `bcm.crisis_events`. Existing rows still
      readable; new rows can be grouped by cycle for the
      forthcoming BCM-2b service work.

### Communication Contacts
- [ ] `POST /api/v1/bcm/contacts` requires `name` + at least one of
      `primaryPhone` or `email`. Missing both → 400.
- [ ] `escalationLevel` ∈ [1, 5]; 6+ rejected.
- [ ] `availabilityStatus` accepts AVAILABLE / ON_LEAVE / UNAVAILABLE
      / BACKUP only.
- [ ] `POST /api/v1/bcm/contacts/:id/test-result` with `result` other
      than PASSED / FAILED / NOT_REACHED returns 400.
- [ ] Recording FAILED publishes
      `DomainEvent(triggerEvent="BCM_CALL_TREE_FAILED")` so BCM-T1
      can fire (automation rule from BCM-3 seed).
- [ ] Recording PASSED does NOT publish the event.
- [ ] `GET /api/v1/bcm/contacts/test-stats` returns counts for
      PASSED / FAILED / NOT_REACHED buckets.

---

## BCM-3 — Lessons learned + gaps + corrective actions

### Lessons Learned
- [ ] `titleEn` + `sourceType` required.
- [ ] `sourceType` ∈ {INCIDENT, CRISIS, EXERCISE, AUDIT, MANUAL}.
- [ ] Lifecycle: DRAFT → UNDER_REVIEW → APPROVED → CLOSED.
      REJECTED loops back to DRAFT. Illegal jumps refused.
- [ ] APPROVED stamps `approvedBy` + `approvedAt`.

### Gaps
- [ ] `titleEn` + `source` required; `source` ∈ {BIA, BCP, DRP,
      EXERCISE, CALL_TREE, AUDIT, RISK, MANUAL}.
- [ ] `severity` ∈ {LOW, MEDIUM, HIGH, CRITICAL}; default MEDIUM.
- [ ] Lifecycle: OPEN → ASSIGNED → IN_PROGRESS → UNDER_REVIEW →
      CLOSED. REOPENED → IN_PROGRESS. ACCEPTED_RISK → OPEN allowed.
      ACCEPTED_RISK chosen directly from OPEN.
- [ ] **HIGH / CRITICAL gap cannot CLOSE** without at least one
      linked corrective action — UNDER_REVIEW → CLOSED returns 400
      "corrective action" if no CAPA links exist.

### Corrective Actions
- [ ] `titleEn` required; `priority` ∈ {LOW, MEDIUM, HIGH, CRITICAL};
      `progressPercent` ∈ [0, 100].
- [ ] Lifecycle: OPEN → ASSIGNED → IN_PROGRESS → EVIDENCE_SUBMITTED
      → UNDER_REVIEW → CLOSED. REOPENED → IN_PROGRESS. OVERDUE loops
      back to IN_PROGRESS / EVIDENCE_SUBMITTED.
- [ ] **CLOSE refused without evidence** — CAPA must have an
      `evidenceId` before CLOSED transition.
- [ ] Closing sets `progressPercent = 100` and stamps
      `closureDate = today`.
- [ ] Edit of CLOSED CAPA refused.
- [ ] `POST /api/v1/bcm/capas/flag-overdue` (scheduler endpoint)
      flips eligible CAPAs to OVERDUE and publishes
      `BCM_CAPA_OVERDUE` events for each.

### F8 automation rules seeded
- [ ] `BCM-T1` — `BCM_CALL_TREE_FAILED` → CREATE_RECORD
      BcmCorrectiveAction (priority HIGH).
- [ ] `BCM-T2` — `BCM_RISK_TREATMENT_PLANNED` + `linked_erm=false`
      → AUDIT(WARN) "High/critical BCM risk lacks ERM linkage".
- [ ] `BCM-T3` — `BCM_EXERCISE_FAILED` → CREATE_RECORD BcmGap +
      BcmCorrectiveAction.
- [ ] `BCM-T4` — `BCM_CAPA_OVERDUE` → ESCALATE BCM_MANAGER.
- [ ] All four rows visible at `GET /api/v1/automation/rules?…`
      with `source='BCM_MODULE'`.

---

## Cross-cutting checks (every BCM sprint)

### Tenant isolation
- [ ] Every endpoint reads `TenantContext.getCurrentTenant()` —
      cross-tenant fetch returns 404 / 400.
- [ ] Every entity has `@Where(clause = "is_deleted = 0")` so soft-
      deleted rows never appear in normal reads.

### Bilingual content
- [ ] Every user-facing field has both `*_en` and `*_ar` columns;
      missing AR fallback uses EN.
- [ ] AR strings follow Saudi-native phrasing (MEMORY.md style guide).

### Audit + automation event publishing
- [ ] Every transition + create + delete writes a row to
      `platform.audit_log` with `sourceModule='bcm'` and the right
      `workflow_status_before` / `_after`.
- [ ] The five `BCM_*` domain events are visible in
      `GET /api/v1/automation/runs` after triggering them in a
      smoke test (BCM_RISK_TREATMENT_PLANNED, BCM_CALL_TREE_FAILED,
      BCM_EXERCISE_FAILED (BCM-3-future), BCM_CAPA_OVERDUE).

### Tests
- [ ] `mvn test -Dtest='BcmCycleServiceTest'` passes — locks
      cycle + governance lifecycle.
- [ ] `mvn test -Dtest='BcmRiskServiceTest'` passes — locks
      scoring math + ASSESSED rule + TREATMENT_PLANNED event.
- [ ] `mvn test -Dtest='ContinuityStrategyServiceTest'` passes —
      locks RTO/RPO-required-for-approve + high-cost path.
- [ ] `mvn test -Dtest='BcmCommunicationContactServiceTest'`
      passes — locks contact validation + FAILED-event-fires.
- [ ] `mvn test -Dtest='BcmRemediationServiceTest'` passes —
      locks lessons / gaps / CAPA lifecycle + close-without-evidence
      refusal + overdue sweep.

### Documentation
- [ ] `docs/bcm/BCM_IMPROVEMENT_PLAN.md` reflects what's actually in
      code (✓ for BCM-1, BCM-2, BCM-3; pending for BCM-4 frontend
      breakouts, BCM-5 advanced pages, BCM-6 reports).
- [ ] STATUS.md "BCM" section is current.
- [ ] CLAUDE.md "module/bcm" entry mentions the `cycle/` sub-package
      so future agents land in the right place.

---

## Future sprints (BCM-4 onwards)

Tracked separately in `BCM_IMPROVEMENT_PLAN.md` §15:

- BCM-4 — break out cycles / governance / risks / strategies /
  comms tabs into dedicated pages (the single-page
  `BcmReadinessPage` ships them as one workspace today).
- BCM-5 — exercises page + lessons page + gaps + CAPA page +
  RTL hardening sweep.
- BCM-6 — readiness report generator (Markdown / HTML / JSON /
  CSV); 15 named reports from `BCM_IMPROVEMENT_PLAN.md` §3.Q.

Add a QA section per sprint as it lands.

---

*Last updated: alongside the BCM-1 + BCM-2 + BCM-3 closeout.*
