# 02 — Module Architecture
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Every module follows the same layered layout:

```
module/<name>/
├── controller/   @RestController — pure JSON
├── dto/          Request/Response DTOs (Jakarta validation)
├── entity/       JPA @Entity (extends BaseEntity; @Where soft delete)
├── repository/   Spring Data JPA — org-scoped finders
└── service/      @Service @Transactional — business logic + workflow
```

Cross-cutting: every entity has `organisation_id`, `is_deleted`,
`external_id (UUID)`, `created_at`. Every controller uses
`@PreAuthorize("hasPermission(...)")` and reads
`TenantContext.getCurrentTenant()` for multi-tenant scoping.

## Modules in code (35)

### Core domain modules

| Module | EN | AR | Status | Schema | Sidebar route |
|---|---|---|---|---|---|
| `risk` | Enterprise Risk Management | إدارة المخاطر المؤسسية | ✅ Built (Sprint 1-2) | `risk` | `/risk` |
| `bcm` | Business Continuity Management | استمرارية الأعمال | ✅ Built | `bcm` | `/bcm` |
| `scm` | Supply Chain Resilience | صمود سلاسل الإمداد | ✅ Built | `scm` | `/scm` |
| `cyber` | NCA Cybersecurity | الأمن السيبراني | ✅ Built | `cyber` | `/cyber` |
| `privacy` | PDPL Privacy | حماية البيانات الشخصية | ✅ Built | `privacy` | `/privacy` |
| `tprm` | Third-Party Risk Mgmt | إدارة مخاطر الأطراف الخارجية | ✅ Built (incl. Questionnaire Engine V81) | `tprm` | `/tprm` |
| `policy` | Policy Management | إدارة السياسات | ✅ Built (V79) | `policy` | `/policy-management` |
| `ndi` | SDAIA NDI / نضيء | نظام النضج للبيانات | ✅ Built (Sprint 3) | `ndi` | `/ndi` |
| `compliance` | Compliance Self-Assessment | الامتثال | ✅ Built | `compliance` | `/compliance` |
| `audit_lc` | Audit Lifecycle | دورة المراجعة الداخلية | ✅ Built | `audit_lc` | `/audit-lifecycle` |
| `incident` | Incident Management | إدارة الحوادث | ✅ Built | `incident` | `/incidents` |
| `evidence` | Evidence Repository | مستودع الأدلة | ✅ Built | `evidence` | `/evidence` |

### Lifecycle modules (per-domain workflow)

| Module | Purpose |
|---|---|
| `cyber_lc` | Vulnerability lifecycle + exceptions |
| `bcm_lc` | Crisis activation lifecycle |
| `privacy_lc` | DSR + breach lifecycle |
| `risk_lc` | Treatment plan lifecycle |
| `capa` | Corrective Actions across domains |
| `audit_eng` | Audit engagement workflow |
| `change_mgmt` | Change request workflow |
| `regchange` | Regulatory change tracking |

### Platform-spine modules (Framework Intersection F1-F9)

| Module | EN | Schema | Sub-package |
|---|---|---|---|
| Authority Documents | regulator catalog (F1) | `platform.authority_documents` | `module/platform/regulatory` |
| Control Library | unified controls + mappings (F2) | `platform.controls` etc. | `module/platform/controls` |
| Compliance Posture | coverage + heatmap (F5+F6) | (reads spine) | `module/platform/regulatory` |
| Audit Log | unified audit (F7) | `platform.audit_log` | `module/platform/auditlog` |
| Automation Rules | rules engine (F8) | `platform.automation_rules` | `module/platform/automation` |
| Module Licensing | entitlements (F9) | `platform.organisation_licenses` | `module/platform/licensing` |

### Shared services

| Module | Purpose |
|---|---|
| `auth` | JWT login/refresh, password reset |
| `iam` | Users, roles, permissions |
| `admin` | Tenant admin, system settings |
| `dashboard` | Executive dashboard aggregation |
| `reporting` | Report runs + schedules + definitions |
| `workflow` | Generic workflow engine (state machine framework) |
| `approvals` | Approval requests + matrix |
| `signature` | Digital signature transactions |
| `intake` | Promotion of intake records to domain entities |
| `knowledge` | Knowledge Base |
| `ethics` | Ethics case management |
| `sust` (sub-packages cma/sgi/ghg/capa) | ESG/sustainability disclosures |
| `board_pack` | Board pack generator |
| `nca_ecc` | NCA ECC filing module (annual self-assessment) |
| `datagov` | Data governance domains |
| `platform` | Notifications, Organisation, files, persistence helpers |

## Module-by-module summary

The format below is the architectural contract per the implementation
prompt §6. Modules already documented in the codebase (NDI, Policy,
TPRM Questionnaire, Risk-Advanced) have full QA checklists at
`docs/<module>/QA_CHECKLIST.md`; this section is the cross-module
overview.

### Enterprise Risk Management (`module/risk`)

- **Business purpose:** identify, assess, treat, monitor enterprise
  risks against an explicit risk appetite.
- **User personas:** Risk Manager, Risk Owner, Risk Committee,
  Executive Reviewer.
- **Core workflows:** Risk lifecycle (Identified → Assessed →
  Treatment Planned → Treatment In Progress → Residual Risk Review →
  Accepted/Mitigated/Transferred/Avoided → Monitoring → Closed).
- **Registers:** Risk register, KRI register, Risk-Control matrix.
- **Data objects:** `Risk`, `RiskAssessment`, `RiskTreatment`,
  `RiskAppetite`, `KriDefinition`, `KriReading`, `RiskScenario`,
  `EmergingRisk`.
- **Screens:** `/risk` (register + heatmap + KRI + appetite +
  workflows + alignment tabs), `/risk/:id` detail.
- **Key fields:** likelihood, impact, residual_score (computed),
  category, owner, status, appetite_breach.
- **Approval points:** Risk acceptance requires committee sign-off
  via `module/approvals` → digital signature.
- **Required evidence:** assessment evidence (likelihood + impact
  rationale), treatment plan evidence, acceptance approval.
- **Reports:** Board Risk Report, Risk Committee Report, Top-N
  Risks, Appetite Breach Report, KRI Breach Report.
- **Integration:** controls via `platform.risk_control_links`;
  CAPA via `module/capa`; incidents via `module/incident` (a
  realised risk).
- **Licensing:** module code `RISK`.
- **QA:** `docs/risk-advanced/QA_CHECKLIST.md`.

### Business Continuity Management (`module/bcm`)

- **Business purpose:** identify critical services, run BIA, build
  BCPs/DRPs, exercise + improve.
- **User personas:** BCM Manager, BIA Owner, Service Owner, DR
  Owner, Crisis Manager.
- **Core workflows:** Critical Service Identification → BIA → BCP →
  DRP → Exercise → Lessons Learned → Management Review.
- **Data objects:** `CriticalService`, `BiaRecord`, `BcmPlan`,
  `DisasterRecoveryPlan`, `BcmExercise`, `CrisisEvent`,
  `CrisisCommsTemplate`.
- **Screens:** `/bcm` (overview/plans/processes/bia/graph/exercises/
  comms/workflows/alignment), `/dr-plans`.
- **Reports:** BCM Readiness, Critical Services Continuity, DR
  Readiness, Crisis Exercise.
- **Integration:** risks (BIA gap → risk), suppliers (BCP evidence),
  cyber (incidents activate crisis), evidence (BCP approvals).

### Supply Chain Resilience (`module/scm`)

- **Business purpose:** map dependencies, classify supplier
  criticality, monitor resilience, plan for exit.
- **Workflows:** Supplier Registration → Criticality → Risk
  Assessment → Dependency Mapping → BCM Evidence → 4th-Party Risk →
  SLA Monitoring → Incident Escalation → Alternate Planning → Exit.
- **Data objects:** `Supplier`, `SupplierResilienceTest`,
  `Dependency`, `Contract`, `AlternateSupplier`, `ExitPlan`.
- **Reports:** Supplier Resilience, Concentration Risk, 4th-Party
  Exposure.

### NCA Cybersecurity (`module/cyber`)

- **Business purpose:** assess + close gaps against NCA ECC v2 /
  CSCC / CCC / OTCC / DCC / TCC; manage vulnerabilities; respond
  to cyber incidents.
- **Data objects:** `CyberAsset`, `CyberControl`, `CyberSubControl`,
  `CyberAssessment`, `Vulnerability`, `VulnException`, `CloudService`
  (🔜 missing entity #39), `CyberIncident`, `CyberEvidence`.
- **Screens:** `/cyber` (overview/controls/subcontrols/assessments/
  vuln/exceptions/incidents/assets/evidence/actions/workflows/comms/
  alignment).
- **Integration:** unified controls via `platform.control_mappings`
  (F2 spine); incidents → crisis workflow (BCM); incidents
  affecting personal data → PDPL breach (automation rule AR-04).

### PDPL Privacy (`module/privacy`)

- **Business purpose:** PDPL Article-by-Article compliance —
  processing activities (ROPA), consent, DSRs, PIAs, cross-border
  transfers, breaches.
- **Data objects:** `ProcessingActivity`, `PrivacyNotice`,
  `DataSubjectRequest`, `DpiaRecord`, `CrossBorderTransfer`,
  `DataBreach`. (Consent Record entity 🔜 — task #39.)
- **Screens:** `/privacy` (overview/dsr/breaches/ropa/dpia/consent/
  workflows/comms/alignment), `/data-lifecycle`.
- **Integration:** vendors processing personal data → processor
  due diligence (automation rule AR-03); breach 72h timer via
  AR-04.

### TPRM (`module/tprm`)

- **Business purpose:** vendor lifecycle from intake through
  reassessment + exit; questionnaire scoring drives risk rating.
- **Data objects:** `Vendor`, `VendorAssessment`,
  `QuestionnaireTemplate`, `QuestionnaireQuestion`,
  `QuestionnaireResponse`, `ProcessorAssessment`,
  `ThirdPartyCyberAssessment`.
- **Screens:** `/tprm` (overview/vendors/onboarding/reassessment/
  assessments/questionnaires/contracts/workflows/comms/alignment).
- **Integration:** SAMA Outsourcing for financial institutions
  (via Regulatory Alignment tab); NCA TPCA for cyber vendors.
- **QA:** `docs/tprm/QA_CHECKLIST.md`.

### Policy Management (`module/policy`)

- **Business purpose:** policy lifecycle DRAFT → REVIEW → APPROVED
  → PUBLISHED → EXPIRED/ARCHIVED; exceptions; attestations.
- **Data objects:** `PolicyDocument`, `PolicyException`,
  `PolicyAttestation`.
- **QA:** `docs/policy/QA_CHECKLIST.md`.

### Incident + Crisis (`module/incident` + `module/bcm`)

- **Business purpose:** unified incident intake (cyber / privacy /
  service disruption / supplier) → triage → containment →
  investigation → recovery → RCA → CAPA → lessons learned.
- **Data objects:** `Incident`, `RootCauseAnalysis`, `CrisisEvent`,
  `CommunicationLog`.
- **Integration:** every incident can trigger:
  cyber-incident-workflow, PDPL breach workflow, BCM/crisis
  workflow, supplier incident workflow, regulatory notification.

### Evidence Management (`module/evidence`)

- **Business purpose:** evidence-as-data — every control,
  attestation, audit finding linked to evidence with versioning +
  expiry + chain-of-custody.
- **Data objects:** `EvidenceItem`, `EvidenceLink`, `EvidenceRequest`,
  `ChainOfCustodyRecord`.

### Platform spine (F1-F9)

| Module | What it does | Schema | Tests |
|---|---|---|---|
| `module/platform/regulatory` | Authority Documents + Citations + Posture endpoints | `platform.authority_documents`, `_citations` | `AuthorityDocumentServiceTest`, `CompliancePostureServiceTest` |
| `module/platform/controls` | Unified Control Library + Mappings + Risk-Control Links + Assessments | `platform.controls`, `_objectives`, `_mappings`, `risk_control_links`, `_assessments` | `ControlLibraryServiceTest` |
| `module/platform/auditlog` | Generic audit ledger | `platform.audit_log` | `AuditLogServiceTest` |
| `module/platform/automation` | Rules engine + event dispatch | `platform.automation_rules`, `_runs` | `AutomationRuleEngineTest` |
| `module/platform/licensing` | Per-tenant module entitlements | `platform.organisation_licenses` | `LicensingServiceTest` |

---

**See also:** `docs/grc/framework-intersection-plan.md`,
`docs/architecture/`.
