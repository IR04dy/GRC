# 10 - Business Continuity Management (BCM) Domain Model

## Overview

The BCM domain model implements the ISO 22301:2019 Business Continuity Management System (BCMS) lifecycle: context → BIA → strategy → plan development → exercising → improvement. It tracks RTO/RPO commitments per critical process and integrates with the unified risk register.

---

## 1. Core Entities

### `bcm.critical_processes`

The business process inventory — the foundation of BIA.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | Internal PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `process_name_en` | NVARCHAR(300) | Process name (English) |
| `process_name_ar` | NVARCHAR(300) | Process name (Arabic) |
| `description_en` | NVARCHAR(MAX) | Process description |
| `owner_id` | BIGINT | FK → `iam.users` |
| `department_id` | BIGINT | FK → `platform.business_units` |
| `criticality` | NVARCHAR(20) | CRITICAL / HIGH / MEDIUM / LOW |
| `process_category` | NVARCHAR(100) | e.g. Financial, Operations, IT, HR |
| `dependencies_en` | NVARCHAR(MAX) | Key dependencies (systems, suppliers, people) |
| `is_deleted` | BIT | Soft delete |
| `row_version` | ROWVERSION | Optimistic concurrency |

### `bcm.bia_records`

Business Impact Analysis per critical process. Multiple BIA versions can exist per process (annual review cycle).

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `process_id` | BIGINT | FK → `bcm.critical_processes` |
| `assessment_date` | DATE | Date of BIA |
| `assessor_id` | BIGINT | FK → `iam.users` |
| `rto_hours` | INT | Recovery Time Objective (hours) |
| `rpo_hours` | INT | Recovery Point Objective (hours) |
| `mtpd_hours` | INT | Maximum Tolerable Period of Disruption (hours) |
| `mbco_percentage` | TINYINT | Minimum Business Continuity Objective (% of normal capacity) |
| `financial_impact_per_hour_sar` | DECIMAL(18,2) | Estimated financial impact per hour of disruption |
| `reputational_impact` | NVARCHAR(20) | CRITICAL / HIGH / MEDIUM / LOW |
| `regulatory_impact` | NVARCHAR(20) | CRITICAL / HIGH / MEDIUM / LOW |
| `overall_impact` | NVARCHAR(20) | Derived: max(financial, reputational, regulatory) |
| `linked_risk_id` | BIGINT | FK → `risk.risks` (created when overall_impact = HIGH/CRITICAL) |
| `status` | NVARCHAR(20) | DRAFT / APPROVED / SUPERSEDED |
| `approved_by` | BIGINT | FK → `iam.users` |
| `approved_at` | DATETIME2(7) | Approval timestamp |
| `created_by` / `updated_by` | BIGINT | Audit |

### `bcm.recovery_strategies`

Approved recovery strategies per critical process, linked to a BIA record.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `process_id` | BIGINT | FK → `bcm.critical_processes` |
| `bia_record_id` | BIGINT | FK → `bcm.bia_records` |
| `strategy_type` | NVARCHAR(50) | WARM_STANDBY / COLD_STANDBY / MANUAL_WORKAROUND / OUTSOURCE / MUTUAL_AID |
| `description_en` | NVARCHAR(MAX) | Strategy description |
| `estimated_cost_sar` | DECIMAL(18,2) | Implementation cost |
| `status` | NVARCHAR(20) | PROPOSED / APPROVED / IMPLEMENTED |

### `bcm.bcm_plans`

Business Continuity Plans and Disaster Recovery Plans. Versioned documents linked to one or more critical processes.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `plan_name_en` | NVARCHAR(300) | Plan title |
| `plan_name_ar` | NVARCHAR(300) | Plan title (Arabic) |
| `plan_type` | NVARCHAR(30) | BCP / DRP / COOP / ERP (Emergency Response Plan) |
| `version` | NVARCHAR(20) | e.g. v2.1 |
| `status` | NVARCHAR(20) | DRAFT / REVIEW / APPROVED / RETIRED |
| `owner_id` | BIGINT | FK → `iam.users` |
| `approved_by` | BIGINT | FK → `iam.users` |
| `approved_at` | DATETIME2(7) | Approval date |
| `next_review_date` | DATE | Annual review schedule |
| `document_attachment_id` | BIGINT | FK → `platform.attachments` (plan document) |

### `bcm.plan_process_links`

Many-to-many: which critical processes are covered by which plans.

| Column | Type |
|---|---|
| `plan_id` | BIGINT FK |
| `process_id` | BIGINT FK |

### `bcm.bcm_exercises`

Testing and exercising of BCM plans (tabletop, functional, full-scale drills).

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `plan_id` | BIGINT | FK → `bcm.bcm_plans` |
| `exercise_type` | NVARCHAR(30) | TABLETOP / FUNCTIONAL / FULL_SCALE / SIMULATION |
| `exercise_date` | DATE | Date conducted |
| `facilitator_id` | BIGINT | FK → `iam.users` |
| `outcome` | NVARCHAR(20) | PASS / PASS_WITH_FINDINGS / FAIL |
| `rto_achieved_hours` | INT | Actual RTO achieved during exercise |
| `rpo_achieved_hours` | INT | Actual RPO achieved |
| `findings_count` | INT | Number of findings/gaps identified |
| `lessons_learned_en` | NVARCHAR(MAX) | Lessons learned narrative |
| `next_exercise_date` | DATE | Scheduled next exercise |

### `bcm.exercise_findings`

Individual gaps discovered during a BCM exercise. Feeds the compliance evidence chain.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `exercise_id` | BIGINT | FK → `bcm.bcm_exercises` |
| `finding_description_en` | NVARCHAR(MAX) | Finding description |
| `severity` | NVARCHAR(20) | HIGH / MEDIUM / LOW |
| `corrective_action_en` | NVARCHAR(MAX) | Required corrective action |
| `due_date` | DATE | Remediation due date |
| `status` | NVARCHAR(20) | OPEN / IN_PROGRESS / CLOSED |
| `owner_id` | BIGINT | Responsible owner |

---

## 2. Cross-Module Integrations

### → Risk Module
When a BIA record is approved with `overall_impact = HIGH` or `CRITICAL`:
- `BiaHighImpactEvent` fires
- `RiskLinkageService` creates an entry in `risk.risks` with `source_module = 'BCM'`
- `bia_records.linked_risk_id` is populated

### → Compliance Module
Exercise results feed ISO 22301 compliance evidence:
- `bcm.bcm_exercises` records are linked as `compliance.evidence_items` against ISO 22301 control `A.8.4` (exercising and testing)
- Exercise findings become `compliance.compliance_findings`

### → Workflow Module
Plan approval workflows are routed through `workflow.workflow_instances` with `entity_type = 'BCM_PLAN'`.

---

## 3. ISO 22301:2019 Alignment

| ISO 22301 Clause | Entity |
|---|---|
| 8.2 — Business Impact Analysis | `bcm.bia_records` |
| 8.3 — Business Continuity Strategy | `bcm.recovery_strategies` |
| 8.4 — Business Continuity Plans | `bcm.bcm_plans` |
| 8.5 — Exercising and Testing | `bcm.bcm_exercises`, `bcm.exercise_findings` |
| 9.1 — Performance Monitoring | KRI: RTO achievement rate, exercise pass rate |
