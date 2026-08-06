# 11 — User Stories
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Representative stories per persona. Each story maps to one or more
existing services/UIs and to the QA checklist that locks its rules.

## Risk Manager

- **US-RM-01.** As a Risk Manager, I want to register a new risk so
  that I can track it through assessment and treatment.
  *Implementation:* `POST /api/v1/risk/risks` →
  `/risk` Register tab. **QA:** `docs/risk-advanced/QA_CHECKLIST.md`.
- **US-RM-02.** As a Risk Manager, I want to record a periodic
  assessment so that the residual score reflects the latest view.
  *Implementation:* `POST /risks/:id/assessments` (Rule A1 + A2).
- **US-RM-03.** As a Risk Manager, I want a treatment plan with
  ISO 31000 type so that owners can act.
- **US-RM-04.** As a Risk Manager, I want to see appetite breaches
  on the dashboard so I can escalate.
- **US-RM-05.** As a Risk Manager, I want my risks linked to the
  controls that mitigate them so that I have a Risk-Control Matrix
  for the auditor. *Implementation:* `platform.risk_control_links`
  (F2).

## BCM Manager

- **US-BCM-01.** As a BCM Manager, I want a BIA per critical service
  with RTO/RPO so I can plan recovery.
- **US-BCM-02.** As a BCM Manager, I want a published BCP per
  critical service, signed by the service owner.
- **US-BCM-03.** As a BCM Manager, I want to see a Regulatory
  Alignment tab on `/bcm` showing ISO 22301 + SAMA BCM coverage.
  *Implementation:* F5 component.
- **US-BCM-04.** As a BCM Manager, I want an exercise calendar so
  that every BCP gets tested annually.

## DPO

- **US-DPO-01.** As a DPO, I want every personal-data processing
  registered with a legal basis (PDPL Article 6).
- **US-DPO-02.** As a DPO, I want any cyber incident that touches
  personal data to auto-open a breach case with a 72-hour timer.
  *Implementation:* AR-04 (F8 automation rule).
- **US-DPO-03.** As a DPO, I want to track every DSR through the
  30-day SLA + 60-day extension. *QA:* `docs/privacy/QA_CHECKLIST.md`.
- **US-DPO-04.** As a DPO, I want a PIA mandatory before any high-
  risk activity goes live.

## Cybersecurity Manager

- **US-CYB-01.** As a Cybersecurity Manager, I want to assess every
  NCA ECC v2 control once per cycle.
- **US-CYB-02.** As a Cybersecurity Manager, I want a single control
  to satisfy multiple frameworks (ECC + ISO 27001 + SAMA Cyber).
  *Implementation:* `platform.control_mappings` (F2).
- **US-CYB-03.** As a Cybersecurity Manager, I want vulnerability
  SLAs by severity (Critical 15d / High 30d / Medium 60d) with
  auto-escalation. *Implementation:* AR-13.
- **US-CYB-04.** As a Cybersecurity Manager, I want a Compliance
  Posture Hub I can show to the CISO before an NCA visit.
  *Implementation:* `/compliance/posture` (F6).

## Supplier Manager

- **US-SM-01.** As a Supplier Manager, I want a vendor
  questionnaire engine that scores vendors deterministically.
  *Implementation:* TPRM Questionnaire V81.
- **US-SM-02.** As a Supplier Manager, I want suppliers without
  valid BCP evidence to auto-trigger a CAPA. *Implementation:*
  AR-08.
- **US-SM-03.** As a Supplier Manager, I want concentration risk
  reported so I can plan alternates.

## Internal Auditor

- **US-IA-01.** As an Internal Auditor, I want every record's full
  history (who/when/before/after) so I can sample without asking
  the engineering team. *Implementation:* `platform.audit_log` (F7).
- **US-IA-02.** As an Internal Auditor, I want to trace a single
  business action across modules via correlation_id.
- **US-IA-03.** As an Internal Auditor, I want to filter the
  audit log by entity, user, action type, or severity.

## Compliance Officer

- **US-CO-01.** As a Compliance Officer, I want a single posture
  view across NCA + PDPL + SAMA + ISO so I can brief the board.
  *Implementation:* F6.
- **US-CO-02.** As a Compliance Officer, I want to import the
  official NCA ECC v2 control text and flip the placeholder flags
  to ACTIVE. *Implementation:* `07_Control_Library_Import_Template.md`.
- **US-CO-03.** As a Compliance Officer, I want every CAPA closure
  audited, signed, and traceable.

## Board Member

- **US-BM-01.** As a Board Member, I want a quarterly Board Risk
  Report digitally signed and traceable. *Implementation:* report
  #1 + `module/signature`.
- **US-BM-02.** As a Board Member, I want to approve risk
  acceptances with a digital signature. *Implementation:*
  approval matrix `08_Approval_Matrix.md` row 2.

## Platform Admin

- **US-PA-01.** As a Platform Admin, I want to entitle each tenant
  to specific modules (FREE / PRO / ENTERPRISE). *Implementation:*
  F9 (V97).
- **US-PA-02.** As a Platform Admin, I want to enable/disable
  automation rules without a deployment. *Implementation:* F8
  (`PATCH /api/v1/automation/rules/:id`).

---

**See also:** `11_Acceptance_Criteria.md` for the testable rules
behind each story.
