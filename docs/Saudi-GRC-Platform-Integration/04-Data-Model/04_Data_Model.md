# 04 — Data Model
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Conventions (every entity)

```java
@Entity
@Table(schema = "<domain>", name = "<plural>")
@Where(clause = "is_deleted = 0")        // soft-delete filter
@Getter @Setter @NoArgsConstructor       // Lombok
class FooBar {
  @Id @GeneratedValue Long id;
  @Column(updatable = false) UUID externalId = UUID.randomUUID();
  @Column(nullable = false) Long organisationId;     // multi-tenant
  // … domain fields …
  @Column(nullable = false) Boolean isDeleted = false;
  @Column(updatable = false) LocalDateTime createdAt = LocalDateTime.now();
}
```

Bilingual fields: every user-visible string carries `*_en` and `*_ar`
columns (e.g. `title_en` + `title_ar`).

## Core entities (54 from prompt §8) — mapping table

| # | Entity (prompt name) | JPA class | Schema.table | Status |
|---|---|---|---|---|
| 1 | Organization | `Organisation` | `platform.organisations` | ✅ |
| 2 | Department | (rolled into Organisation today) | n/a | 🔜 task #39 |
| 3 | User | `User` | `iam.users` | ✅ |
| 4 | Role | `Role` | `iam.roles` | ✅ |
| 5 | Permission | `Permission` | `iam.permissions` | ✅ |
| 6 | Committee | `Committee` | `iam.committees` | ✅ |
| 7 | Service (business) | `BusinessService` (BCM) | `bcm.business_services` | ✅ |
| 8 | Process | `BusinessProcess` | `bcm.business_processes` | ✅ |
| 9 | Asset | `CyberAsset` | `cyber.assets` | ✅ |
| 10 | Application | folded into `CyberAsset` (type='APPLICATION') | — | ✅ |
| 11 | Data Asset | `ProcessingActivity` | `privacy.processing_activities` | ✅ |
| 12 | Personal Data Processing Activity | same | same | ✅ |
| 13 | Supplier | `Supplier` (alias Vendor in TPRM) | `scm.suppliers` / `tprm.vendors` | ✅ |
| 14 | Contract | `Contract` | `scm.contracts` | ✅ |
| 15 | Dependency | `Dependency` | `scm.dependencies` | ✅ |
| 16 | Risk | `Risk` | `risk.risks` | ✅ |
| 17 | Control | `Control` (unified F2) + legacy `CyberControl` / `ComplianceControl` | `platform.controls` + legacy | ✅ |
| 18 | Regulatory Requirement | `AuthorityDocumentCitation` (F1) | `platform.authority_document_citations` | ✅ |
| 19 | Evidence | `EvidenceItem` | `evidence.evidence_items` | ✅ |
| 20 | Assessment | `RiskAssessment` / `ComplianceAssessment` / `ControlAssessment` (F2) | per schema | ✅ |
| 21 | BIA | `BiaRecord` | `bcm.bia_records` | ✅ |
| 22 | BCP | `BcmPlan` | `bcm.bcm_plans` | ✅ |
| 23 | DRP | `DisasterRecoveryPlan` | `bcm.dr_plans` | ✅ |
| 24 | Incident | `Incident` | `incident.incidents` | ✅ |
| 25 | Crisis Event | `CrisisEvent` | `bcm.crisis_events` | ✅ |
| 26 | Data Breach | `DataBreach` | `privacy.data_breaches` | ✅ |
| 27 | Cyber Incident | `CyberIncident` (also reused `Incident`) | `cyber.incidents` | ✅ |
| 28 | Corrective Action | `CorrectiveAction` + `CapaTask` | `capa.capa_tasks` | ✅ |
| 29 | Issue | merged into CAPA today | n/a | 🔜 task #39 |
| 30 | KRI | `KriDefinition` + `KriReading` | `risk.kri_*` | ✅ |
| 31 | KPI | `NdiOperationalExcellenceKpi` | `ndi.opex_kpis` | ✅ |
| 32 | SLA | `Sla` | `scm.slas` | ✅ |
| 33 | Exercise | `BcmExercise` | `bcm.exercises` | ✅ |
| 34 | Test | `SupplierResilienceTest` | `scm.resilience_tests` | ✅ |
| 35 | Report | `ReportRun` + `ReportSchedule` + `ReportDefinition` | `reporting.*` | ✅ |
| 36 | Approval | `ApprovalRequest` | `approvals.*` | ✅ |
| 37 | Digital Signature Transaction | `DigitalSignatureTransaction` | `signature.*` | ✅ |
| 38 | Audit Log | `AuditLogEntry` (F7 generic) + module-specific (`NdiAuditEvent`, `WorkflowActionLog`) | `platform.audit_log` + others | ✅ |
| 39 | Notification | `Notification` | `platform.notifications` | ✅ |
| 40 | Workflow Instance | `WorkflowInstance` | `workflow.instances` | ✅ |
| 41 | Workflow Task | `WorkflowState` / `WorkflowTransition` | `workflow.*` | ✅ |
| 42 | Exception | `PolicyException` | `policy.exceptions` | ✅ |
| 43 | Risk Acceptance | `RiskTreatment` (type=ACCEPT) | `risk.risk_treatments` | ✅ |
| 44 | Data Subject Request | `DataSubjectRequest` | `privacy.dsr_cases` | ✅ |
| 45 | Consent Record | (entity 🔜 task #39) | n/a | 🔜 |
| 46 | Cross-Border Transfer | `CrossBorderTransfer` | `privacy.cross_border_transfers` | ✅ |
| 47 | Privacy Notice | `PrivacyNotice` | `privacy.notices` | ✅ |
| 48 | Vulnerability | `Vulnerability` | `cyber.vulnerabilities` | ✅ |
| 49 | Patch Record | `VulnException` (closest analogue) | `cyber.vuln_exceptions` | 🟡 |
| 50 | Cloud Service | (entity 🔜 task #39) | n/a | 🔜 |
| 51 | Critical System | `CriticalProcess` (BCM) + `CyberAsset` (with critical flag) | both | ✅ |
| 52 | OT Asset | `CyberAsset` (type=OT) + OTCC assessments | — | ✅ |
| 53 | Supplier Assessment | `VendorAssessment` | `tprm.vendor_assessments` | ✅ |
| 54 | Processor Assessment | `ProcessorAssessment` | `privacy.processor_assessments` | ✅ |

**Coverage:** 47 of 54 entities live and queryable. The 7 outstanding
(Department, Issue separate from CAPA, Consent Record, Cloud Service
as first-class, generic system Audit Log — fixed in F7, etc.) are
tracked in `STATUS.md` + task #39 for the remaining four real gaps.

## Cross-cutting structural patterns

### Multi-tenancy

- Every business table has `organisation_id BIGINT NOT NULL`.
- `TenantContext` (thread-local) is set by `JwtAuthenticationFilter`
  from the JWT `organisationId` claim.
- Repositories expose `findBy*OrganisationId(...)` finders — services
  never read without scoping by tenant.

### Module licensing

- `platform.organisation_licenses` (V97) — see `02_Module_Architecture`.

### Audit fields

Every entity carries `created_at`, optional `created_by`,
`updated_at`, `updated_by`. The generic audit ledger
(`platform.audit_log`, F7) captures every state transition with
old/new payload and ip/user_agent context.

### Soft delete

Every entity has `is_deleted BIT NOT NULL DEFAULT 0` + the
`@Where(clause = "is_deleted = 0")` annotation. JPA reads never
return deleted rows. Hard delete is supported via Flyway maintenance
scripts only.

### Bilingual content

Every user-visible string carries `*_en` and `*_ar`. The frontend
reads `I18n.current` and picks the right column. `translation_status`
flag (EN_ONLY / AR_AUTO / AR_HUMAN_REVIEWED / AR_OFFICIAL) on
authority documents + citations lets QA gate translation quality.

### Authority Documents spine (F1) — the regulatory catalog

```
authority_documents (1) ─── (N) authority_document_citations
```

### Unified Control Library spine (F2)

```
control_objectives (1) ─── (N) controls (N) ─── (N) authority_document_citations
                                                  via control_mappings

controls (1) ─── (N) control_assessments
controls (N) ─── (N) risks via risk_control_links
```

---

**See also:** `04_API_Object_Model.md`, `04_Entity_Relationships.md`,
Flyway migrations under `tss-grc-backend/src/main/resources/db/migration/sqlserver/`.
