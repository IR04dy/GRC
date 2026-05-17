# 03 — Workflow Catalogue

Cross-module workflow inventory. Each workflow lists its source
service, the state machine, the triggers, evidence requirements, and
the automation rules from `platform.automation_rules` that fire on it.

## Conventions

- **State machine** locked in code as a Java `switch` inside the
  service's `transition()` method; backwards moves only where
  explicitly allowed.
- **SLA/timer rules** captured as DB columns (`due_date`,
  `expires_at`) or via the automation engine.
- **Audit** — every transition writes to `platform.audit_log` (F7)
  via `AuditLogService.recordEvent(...)`.
- **Notifications** — fan out through `module/platform/Notification`
  + automation rule actions.

## BCM workflows (§7.1)

| # | Workflow EN / AR | Source service | States | Evidence | Automation rule |
|---|---|---|---|---|---|
| 1 | BCM Governance — حوكمة الاستمرارية | `bcm.service.BcmService` | n/a — register | charter, policy | — |
| 2 | Critical Service ID — تحديد الخدمات الحساسة | `BcmService.classifyService` | LOW → MEDIUM → HIGH → CRITICAL | service inventory | AR-02 fires on `ASSET_CLASSIFIED` |
| 3 | BIA — تحليل أثر الأعمال | `BcmService.biaWorkflow` | DRAFT → REVIEW → APPROVED → PUBLISHED | impact analysis + RTO/RPO | AR-07 on `BIA_UPDATED` |
| 4 | BCM Risk Assessment — تقييم مخاطر الاستمرارية | `risk.RiskAssessmentService` (BCM-tagged) | inherits Risk states | likelihood + impact narrative | — |
| 5 | Continuity Strategy — استراتيجية الاستمرارية | `bcm.BcmStrategyService` | DRAFT → APPROVED | strategy doc | — |
| 6 | BCP Development — تطوير خطة الاستمرارية | `bcm.BcmService.planWorkflow` | DRAFT → REVIEW → APPROVED → PUBLISHED → ARCHIVED | full plan + tabletop | — |
| 7 | DRP — خطة التعافي من الكوارث | `bcm.DisasterRecoveryPlanService` | same as BCP | runbooks + RTO test results | — |
| 8 | Crisis Activation — تفعيل الأزمة | `bcm_lc.InvocationService` | NOT_INVOKED → DECLARED → CONTAINED → RECOVERED → REVIEWED | declaration memo + comms log | AR-18 on `INCIDENT_CLASSIFIED` (severity=CRITICAL) |
| 9 | Communication Mgmt — إدارة الاتصالات | `bcm.CrisisCommsService` | templated | call logs, press releases | — |
| 10 | Testing & Exercises — التمارين | `bcm.BcmExerciseService` | SCHEDULED → IN_PROGRESS → COMPLETED → REVIEWED | exercise report + findings | — |
| 11 | Lessons Learned — الدروس المستفادة | shared `module/capa` | OPEN → IN_PROGRESS → CLOSED | LL doc | — |
| 12 | Corrective Actions — الإجراءات التصحيحية | `capa.CapaTaskService` | OPEN → IN_PROGRESS → EVIDENCE_SUBMITTED → CLOSED | completion evidence | AR-12 on `CAPA_OVERDUE` |
| 13 | BCM Evidence — أدلة الاستمرارية | `evidence.EvidenceItemService` | PENDING → ACTIVE → EXPIRED | — | AR-09 on `EVIDENCE_EXPIRED` |
| 14 | Management Review — مراجعة الإدارة | `bcm.ManagementReviewService` | DRAFT → APPROVED | review minutes | — |

## Supply Chain workflows (§7.2)

Tabular view — same column structure. Highlights:

| Workflow | Service | Key states |
|---|---|---|
| Supplier Registration | `scm.SupplierService` | INTAKE → ACTIVE |
| Supplier Criticality | `scm.SupplierService.classify` | LOW → CRITICAL |
| Risk Assessment | `scm.SupplierRiskService` | DRAFT → REVIEW → APPROVED |
| Dependency Mapping | `scm.DependencyService` | implicit |
| BCM Evidence Review | `scm.SupplierBcmEvidenceService` | PENDING → ACCEPTED / REJECTED |
| 4th-Party Risk | `scm.FourthPartyService` | similar |
| SLA Monitoring | `scm.SlaService` | rolling — AR-14 watches |
| Incident Escalation | `incident.IncidentService` (supplier-tagged) | reuses incident states |
| Alternate Supplier | `scm.AlternateSupplierService` | IDENTIFIED → READY |
| Exit Plan | `scm.ExitPlanService` | DRAFT → APPROVED → ENACTED |
| Resilience Testing | `scm.SupplierResilienceTestService` | SCHEDULED → COMPLETED → REVIEWED |
| Contract Resilience | `scm.ContractClauseService` | REVIEWED → ATTESTED |
| Supplier CAPA | shared `module/capa` | as above |

## ERM workflows (§7.3)

Already detailed in `docs/risk-advanced/QA_CHECKLIST.md`. Key state
machines:

- Risk lifecycle: `Identified → Assessed → Treatment Planned →
  Treatment In Progress → Residual Review → Accepted | Mitigated |
  Transferred | Avoided → Monitoring → Closed`
- Treatment lifecycle (ISO 31000): `PLANNED → IN_PROGRESS →
  COMPLETED` with CANCELLED escape from non-terminal states.
- KRI monitoring: KRI breach → AR-06 fires if appetite is breached.

## PDPL / Privacy workflows (§7.4)

| Workflow | Service | States |
|---|---|---|
| ROPA | `privacy.ProcessingActivityService` | DRAFT → APPROVED |
| Privacy Notices | `privacy.PrivacyNoticeService` | DRAFT → PUBLISHED → REVOKED |
| Consent | `privacy.ConsentService` (entity 🔜) | GRANTED → WITHDRAWN → EXPIRED |
| DSR | `privacy_lc.DsrCaseService` | RECEIVED → TRIAGED → IN_PROGRESS → RESPONDED → CLOSED with AR-10 SLA watch |
| PIA | `privacy.DpiaService` | DRAFT → REVIEW → APPROVED |
| Data Sharing | `privacy.DataSharingService` | REQUESTED → APPROVED → ACTIVE |
| Cross-Border Transfer | `privacy.CrossBorderTransferService` | AR-19 on `CROSS_BORDER_REQUEST` |
| Breach Mgmt | `privacy.DataBreachService` | DETECTED → CONTAINED → INVESTIGATED → NOTIFIED → CLOSED (72h timer via AR-04) |
| Retention & Disposal | `privacy_lc.DisposalCertificateService` | SCHEDULED → DISPOSED → CERTIFIED |
| Processor Assessment | `tprm.ProcessorAssessmentService` | DRAFT → REVIEW → APPROVED |

## NCA workflows (§7.5)

| Workflow | Service | Anchor |
|---|---|---|
| Framework Applicability | `cyber.CyberApplicabilityService` | ECC/CSCC/CCC/OTCC/DCC/TCC |
| Control Library | `cyber.CyberControlService` (also `platform.controls` for unified) | F2 spine |
| Self-Assessment | `cyber.CyberAssessmentService` | per cycle |
| Cyber Risk Assessment | `cyber.CyberRiskService` | per asset |
| Asset/CSCC Classification | `cyber.CyberAssetService` | criticality bucket |
| IAM Compliance | mapped to `module/iam` + Privileged Access reviews |
| Vulnerability Mgmt | `cyber_lc.VulnerabilityService` | DETECTED → TRIAGED → REMEDIATED |
| Cyber Incident | `incident.IncidentService` (cyber-tagged) | NIST 800-61 lifecycle |
| Cloud Cybersecurity | `cyber.CccAssessmentService` | per cloud service |
| OT/ICS | `cyber.OtccAssessmentService` | per OT zone |
| Awareness & Training | `iam.TrainingService` | campaigns |
| NCA Corrective Action | shared `module/capa` |

## Cross-module workflows (§7.6)

| Workflow | Implementation |
|---|---|
| Regulatory Obligation Mapping | F1 catalog + F2 control_mappings |
| Evidence Mgmt | `module/evidence` + per-domain anchors |
| Control Testing | `platform.control_assessments` (F2) |
| Exception Mgmt | `policy.PolicyException` |
| CAPA | `module/capa` |
| Executive Reporting | `module/reporting` + `module/dashboard` |
| Board Reporting | `module/board_pack` |
| Regulatory Evidence Package | `module/ndi.submissionPackage` (NDI pattern) |
| Digital Signature Approval | `module/signature` |
| Audit Trail Review | F7 unified log + `/admin/audit-log` |

---

**Workflow engine** — see
`12-Developer-Handoff/12_Workflow_Engine_Specification.md` for the
generic state-machine framework (`module/workflow`) used by modules
that want declarative configuration instead of switch statements.
