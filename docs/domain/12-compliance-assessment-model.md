# 12 - Compliance Assessment Domain Model
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Overview

The Compliance Assessment model manages the full compliance lifecycle against multiple regulatory frameworks: gap analysis, evidence collection, finding management, remediation tracking, and audit engagement management. It consumes the shared Control Library (`platform.controls`) and feeds the unified risk register.

---

## 1. Core Entities

### `compliance.compliance_assessments`

An assessment is a point-in-time evaluation of an organisation's compliance against a specific control framework. Backed by a SQL Server temporal table for historical snapshots.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `framework_id` | BIGINT | FK → `platform.control_frameworks` |
| `assessment_name_en` | NVARCHAR(300) | Assessment name |
| `assessment_type` | NVARCHAR(30) | INTERNAL / EXTERNAL / SELF_ASSESSMENT / THIRD_PARTY |
| `scope_en` | NVARCHAR(MAX) | Scope description |
| `assessor_id` | BIGINT | FK → `iam.users` (lead assessor) |
| `start_date` | DATE | Assessment start date |
| `end_date` | DATE | Assessment end date |
| `status` | NVARCHAR(20) | PLANNED / IN_PROGRESS / COMPLETED / APPROVED |
| `overall_score` | DECIMAL(5,2) | Compliance % = (compliant / total assessed controls) × 100 |
| `total_controls` | INT | Total controls in scope |
| `compliant_count` | INT | Controls rated Compliant |
| `partial_count` | INT | Controls rated Partially Compliant |
| `non_compliant_count` | INT | Controls rated Non-Compliant |
| `not_applicable_count` | INT | Controls marked Not Applicable |
| `approved_by` | BIGINT | FK → `iam.users` |
| `approved_at` | DATETIME2(7) | Approval timestamp |
| `is_deleted` | BIT | Soft delete |
| `row_version` | ROWVERSION | Optimistic concurrency |
| *(temporal columns)* | | `SysStartTime`, `SysEndTime` — SQL Server temporal table |

### `compliance.assessment_control_results`

Individual control evaluation results within an assessment. One row per control assessed.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `assessment_id` | BIGINT | FK → `compliance.compliance_assessments` |
| `control_id` | BIGINT | FK → `platform.controls` |
| `result` | NVARCHAR(30) | COMPLIANT / PARTIALLY_COMPLIANT / NON_COMPLIANT / NOT_APPLICABLE |
| `maturity_level` | TINYINT | 1=Initial, 2=Developing, 3=Defined, 4=Managed, 5=Optimising |
| `assessor_notes_en` | NVARCHAR(MAX) | Assessor observations |
| `evidence_sufficient` | BIT | Whether evidence adequately supports the result |
| `assessed_by` | BIGINT | FK → `iam.users` |
| `assessed_at` | DATETIME2(7) | Assessment date for this control |

### `compliance.compliance_findings`

Non-conformities and gaps identified during an assessment. Each finding links to one or more controls.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `assessment_id` | BIGINT | FK → `compliance.compliance_assessments` |
| `control_result_id` | BIGINT | FK → `compliance.assessment_control_results` |
| `finding_type` | NVARCHAR(30) | NON_CONFORMITY / OBSERVATION / OPPORTUNITY_FOR_IMPROVEMENT |
| `severity` | NVARCHAR(20) | CRITICAL / HIGH / MEDIUM / LOW |
| `title_en` | NVARCHAR(300) | Finding title |
| `description_en` | NVARCHAR(MAX) | Detailed finding description |
| `root_cause_en` | NVARCHAR(MAX) | Root cause analysis |
| `status` | NVARCHAR(20) | OPEN / IN_REMEDIATION / RESOLVED / ACCEPTED |
| `owner_id` | BIGINT | FK → `iam.users` (responsible owner) |
| `due_date` | DATE | Remediation due date |
| `resolved_at` | DATETIME2(7) | When finding was resolved |
| `linked_risk_id` | BIGINT | FK → `risk.risks` (auto-created for CRITICAL/HIGH findings) |
| `workflow_instance_id` | BIGINT | FK → `workflow.workflow_instances` |

### `compliance.remediation_plans`

Action plans for resolving compliance findings. One finding can have multiple remediation tasks.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `finding_id` | BIGINT | FK → `compliance.compliance_findings` |
| `action_en` | NVARCHAR(MAX) | Remediation action description |
| `action_type` | NVARCHAR(30) | CORRECTIVE / PREVENTIVE / DETECTIVE |
| `owner_id` | BIGINT | FK → `iam.users` |
| `due_date` | DATE | Action due date |
| `status` | NVARCHAR(20) | PLANNED / IN_PROGRESS / COMPLETED / OVERDUE |
| `completed_at` | DATETIME2(7) | Completion date |
| `completion_evidence_en` | NVARCHAR(MAX) | Evidence of completion |

### `compliance.evidence_items`

Evidence artefacts linked to controls and findings. Provides the audit evidence chain.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `control_id` | BIGINT | FK → `platform.controls` |
| `assessment_id` | BIGINT | FK → `compliance.compliance_assessments` (nullable) |
| `finding_id` | BIGINT | FK → `compliance.compliance_findings` (nullable) |
| `evidence_type` | NVARCHAR(50) | POLICY / PROCEDURE / LOG / SCREENSHOT / CERTIFICATE / REPORT / INTERVIEW |
| `title_en` | NVARCHAR(300) | Evidence title |
| `description_en` | NVARCHAR(MAX) | Evidence description |
| `collection_date` | DATE | When the evidence was collected |
| `expiry_date` | DATE | Evidence validity expiry (e.g. annual policy review) |
| `attachment_id` | BIGINT | FK → `platform.attachments` (actual file) |
| `integrity_hash` | NVARCHAR(64) | SHA-256 hash of the file at upload time (tamper detection) |
| `uploaded_by` | BIGINT | FK → `iam.users` |
| `is_sufficient` | BIT | Assessor marks as sufficient evidence |

### `compliance.audit_engagements`

Internal and external audit management.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `audit_name_en` | NVARCHAR(300) | Audit name |
| `audit_type` | NVARCHAR(20) | INTERNAL / EXTERNAL / REGULATORY |
| `framework_id` | BIGINT | FK → `platform.control_frameworks` |
| `auditor_name_en` | NVARCHAR(200) | Lead auditor name |
| `audit_firm_en` | NVARCHAR(200) | Audit firm (for external audits) |
| `planned_start_date` | DATE | Planned start |
| `planned_end_date` | DATE | Planned end |
| `actual_start_date` | DATE | Actual start |
| `actual_end_date` | DATE | Actual end |
| `status` | NVARCHAR(20) | PLANNED / IN_PROGRESS / COMPLETED / REPORT_ISSUED |
| `linked_assessment_id` | BIGINT | FK → `compliance.compliance_assessments` |

---

## 2. Compliance Score Calculation

```
overall_score = (compliant_count / (total_controls - not_applicable_count)) × 100
```

Scores are cached per `(organisation_id, framework_id)` with 5-minute TTL via Caffeine. The cache is invalidated when any `assessment_control_results` record is updated.

---

## 3. Cross-Module Risk Linkage

Findings with `severity = CRITICAL` or `HIGH` automatically trigger `FindingHighSeverityEvent`:
- `RiskLinkageService` creates an entry in `risk.risks` with `source_module = 'COMPLIANCE'`
- `compliance_findings.linked_risk_id` is populated
- A workflow task is assigned to the finding owner for remediation

---

## 4. Supported Frameworks

| Framework | Standard | Controls Count |
|---|---|---|
| NCA ECC 2.0 | Saudi National Cybersecurity Authority | 149 controls |
| ISO 27001:2022 | Information Security Management | 93 controls (Annex A) |
| ISO 27701:2019 | Privacy Information Management | 31 additional controls |
| ISO 22301:2019 | Business Continuity Management | 42 controls |
| Saudi PDPL | Personal Data Protection Law | 38 obligations |
| SAMA CSF | Cyber Security Framework | 140 controls |

Controls seeded via repeatable Flyway migration `R__seed_controls.sql`.
