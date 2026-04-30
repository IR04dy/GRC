# 11 - Privacy & Personal Data Protection Domain Model

## Overview

The Privacy domain model supports compliance with the **Saudi Personal Data Protection Law (PDPL)** and **ISO 27701:2019** (Privacy Information Management). It covers the Record of Processing Activities (RoPA), Privacy/Data Protection Impact Assessments (PIA/DPIA), data breach management, data subject rights, and consent records.

---

## 1. Core Entities

### `privacy.data_categories`

Classification of personal data types processed by the organisation.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `organisation_id` | BIGINT | Tenant |
| `category_name_en` | NVARCHAR(200) | e.g. Identity Data, Health Data, Financial Data |
| `category_name_ar` | NVARCHAR(200) | Arabic name |
| `sensitivity_level` | NVARCHAR(20) | GENERAL / SENSITIVE / SPECIAL_CATEGORY |
| `pdpl_article` | NVARCHAR(50) | Relevant PDPL article reference |
| `examples_en` | NVARCHAR(MAX) | Example data elements |

### `privacy.processing_activities`

Record of Processing Activities (RoPA) per PDPL Article 12. Each row is one data processing activity.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `activity_name_en` | NVARCHAR(300) | Activity name |
| `activity_name_ar` | NVARCHAR(300) | Activity name (Arabic) |
| `purpose_en` | NVARCHAR(MAX) | Processing purpose |
| `legal_basis` | NVARCHAR(50) | CONSENT / CONTRACT / LEGAL_OBLIGATION / VITAL_INTEREST / PUBLIC_TASK / LEGITIMATE_INTEREST |
| `controller_id` | BIGINT | FK → `iam.users` (Data Controller contact) |
| `processor_name_en` | NVARCHAR(300) | Third-party processor name (if applicable) |
| `data_subject_categories` | NVARCHAR(MAX) | JSON array of subject types (employees, customers, etc.) |
| `retention_period_months` | INT | Data retention period |
| `retention_basis_en` | NVARCHAR(MAX) | Legal basis for retention period |
| `cross_border_transfer` | BIT | Whether data is transferred outside KSA |
| `transfer_countries` | NVARCHAR(500) | Comma-separated country codes |
| `transfer_safeguards_en` | NVARCHAR(MAX) | Safeguards for cross-border transfer (per PDPL Article 29) |
| `status` | NVARCHAR(20) | DRAFT / ACTIVE / RETIRED |
| `last_reviewed_at` | DATE | Last review date |
| `is_deleted` | BIT | Soft delete |
| `row_version` | ROWVERSION | Optimistic concurrency |

### `privacy.processing_activity_data_categories`

Many-to-many: which data categories are used in which processing activities.

| Column | Type |
|---|---|
| `activity_id` | BIGINT FK |
| `data_category_id` | BIGINT FK |

### `privacy.privacy_assessments`

PIA / DPIA records per processing activity or new project.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `assessment_type` | NVARCHAR(10) | PIA / DPIA |
| `subject_en` | NVARCHAR(300) | Assessment subject |
| `activity_id` | BIGINT | FK → `privacy.processing_activities` (nullable) |
| `assessor_id` | BIGINT | FK → `iam.users` |
| `assessment_date` | DATE | Date conducted |
| `risk_level` | NVARCHAR(20) | HIGH / MEDIUM / LOW |
| `recommendation_en` | NVARCHAR(MAX) | DPO recommendations |
| `status` | NVARCHAR(20) | DRAFT / COMPLETED / APPROVED |
| `approved_by` | BIGINT | FK → `iam.users` (DPO) |
| `approved_at` | DATETIME2(7) | Approval timestamp |
| `linked_risk_id` | BIGINT | FK → `risk.risks` (if risk level = HIGH) |
| `workflow_instance_id` | BIGINT | FK → `workflow.workflow_instances` |

### `privacy.data_breaches`

Data breach records with PDPL regulatory notification tracking.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `title_en` | NVARCHAR(300) | Breach title |
| `description_en` | NVARCHAR(MAX) | Breach description |
| `breach_type` | NVARCHAR(50) | UNAUTHORIZED_ACCESS / ACCIDENTAL_DISCLOSURE / THEFT / LOSS / OTHER |
| `discovered_at` | DATETIME2(7) | When breach was discovered (starts 72h clock) |
| `occurred_at` | DATETIME2(7) | When breach actually occurred (if known) |
| `data_subjects_affected_count` | INT | Estimated number of affected individuals |
| `data_categories_affected` | NVARCHAR(MAX) | JSON array of affected data category IDs |
| `cross_border` | BIT | Whether breach involved cross-border data |
| `pdpc_notification_required` | BIT | Whether PDPC notification is required |
| `pdpc_notified_at` | DATETIME2(7) | When PDPC was notified (must be within 72h of discovery) |
| `data_subjects_notified` | BIT | Whether affected individuals were notified |
| `subjects_notified_at` | DATETIME2(7) | Notification date |
| `status` | NVARCHAR(30) | DETECTED / INVESTIGATING / CONTAINED / NOTIFIED / CLOSED |
| `linked_risk_id` | BIGINT | FK → `risk.risks` |
| `linked_incident_id` | BIGINT | FK → `cyber.cyber_incidents` (if cyber-related) |
| `workflow_instance_id` | BIGINT | FK → `workflow.workflow_instances` |

### `privacy.data_subject_requests`

Data Subject Rights requests per PDPL Chapter 4 (access, correction, deletion, portability, objection).

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `request_type` | NVARCHAR(30) | ACCESS / CORRECTION / DELETION / PORTABILITY / OBJECTION / RESTRICTION |
| `requester_name` | NVARCHAR(200) | Data subject name |
| `requester_email` | NVARCHAR(200) | Contact email |
| `request_details_en` | NVARCHAR(MAX) | Request details |
| `received_at` | DATETIME2(7) | Request receipt date (starts 30-day SLA) |
| `due_date` | DATE | Response due date (received_at + 30 calendar days per PDPL) |
| `status` | NVARCHAR(30) | RECEIVED / VERIFYING / IN_PROGRESS / COMPLETED / REJECTED / EXTENDED |
| `response_en` | NVARCHAR(MAX) | Response to the data subject |
| `completed_at` | DATETIME2(7) | Completion date |
| `assigned_to` | BIGINT | FK → `iam.users` |
| `rejection_reason_en` | NVARCHAR(MAX) | Reason if rejected |

### `privacy.consent_records`

Consent collection and withdrawal audit trail.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `organisation_id` | BIGINT | Tenant |
| `activity_id` | BIGINT | FK → `privacy.processing_activities` |
| `data_subject_identifier` | NVARCHAR(200) | Hashed/anonymised subject reference |
| `consent_version` | NVARCHAR(20) | Version of the consent notice shown |
| `consented_at` | DATETIME2(7) | When consent was given |
| `withdrawn_at` | DATETIME2(7) | When consent was withdrawn (nullable) |
| `collection_method` | NVARCHAR(50) | WEB_FORM / EMAIL / PAPER / API |
| `ip_address_hash` | NVARCHAR(64) | SHA-256 hash of IP (not stored in clear) |

---

## 2. Cross-Module Integrations

### → Risk Module
- Confirmed data breach (`status = CONTAINED`) → `DataBreachConfirmedEvent` → creates `risk.risks` with `source_module = 'PRIVACY'`
- PIA with `risk_level = HIGH` → creates linked risk

### → Compliance Module
- Processing activities provide evidence for PDPL Article 12 (RoPA) controls
- DPIA approvals provide evidence for ISO 27701 controls

### → Workflow Module
- DPIA approval routed through `workflow.workflow_instances`
- Breach response and PDPC notification tracked as workflow tasks with SLA

---

## 3. PDPL Article Alignment

| PDPL Article | Entity |
|---|---|
| Art. 4 — Conditions for processing | `processing_activities.legal_basis` |
| Art. 12 — Record of Processing | `privacy.processing_activities` |
| Art. 18–22 — Data Subject Rights | `privacy.data_subject_requests` |
| Art. 24 — Consent | `privacy.consent_records` |
| Art. 29 — Cross-border transfer | `processing_activities.cross_border_transfer` |
| Art. 30 — Data breach notification | `privacy.data_breaches` |
