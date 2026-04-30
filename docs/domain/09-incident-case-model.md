# 09 - Incident & Case Management Domain Model

## Overview

The Incident & Case Management model covers the full lifecycle of security incidents and general GRC cases from detection through closure. Incidents originate primarily in the Cybersecurity module but the case management layer is shared across all modules (privacy breaches, BCM activations, compliance findings can all raise cases).

---

## 1. Core Entities

### `cyber.cyber_incidents`

The primary incident record. Every confirmed security event is recorded here.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | Internal PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed identifier |
| `organisation_id` | BIGINT | Tenant discriminator |
| `title_en` | NVARCHAR(300) | Incident title (English) |
| `title_ar` | NVARCHAR(300) | Incident title (Arabic) |
| `description_en` | NVARCHAR(MAX) | Detailed description |
| `incident_type` | NVARCHAR(50) | MALWARE / PHISHING / UNAUTHORIZED_ACCESS / DATA_BREACH / DDoS / INSIDER_THREAT / OTHER |
| `severity` | NVARCHAR(20) | CRITICAL / HIGH / MEDIUM / LOW |
| `status` | NVARCHAR(30) | DETECTED / TRIAGED / CONTAINED / ERADICATED / RECOVERED / CLOSED |
| `detected_at` | DATETIME2(7) | When the incident was first detected |
| `reported_at` | DATETIME2(7) | When it was formally reported |
| `contained_at` | DATETIME2(7) | When containment was achieved |
| `closed_at` | DATETIME2(7) | When the incident was fully closed |
| `affected_asset_count` | INT | Number of assets impacted |
| `estimated_impact_sar` | DECIMAL(18,2) | Estimated financial impact (SAR) |
| `linked_risk_id` | BIGINT | FK → `risk.risks.id` (auto-created when severity ≥ HIGH) |
| `workflow_instance_id` | BIGINT | FK → `workflow.workflow_instances.id` |
| `source_module` | NVARCHAR(30) | Always 'CYBER' for incidents |
| `created_by` / `updated_by` | BIGINT | Audit columns |
| `is_deleted` | BIT | Soft delete |
| `row_version` | ROWVERSION | Optimistic concurrency |

### `cyber.incident_affected_assets`

Many-to-many link between an incident and the affected asset inventory items.

| Column | Type | Description |
|---|---|---|
| `incident_id` | BIGINT | FK → `cyber.cyber_incidents` |
| `asset_id` | BIGINT | FK → `cyber.cyber_assets` |
| `impact_type` | NVARCHAR(50) | CONFIDENTIALITY / INTEGRITY / AVAILABILITY |

### `cyber.incident_timeline`

Immutable append-only log of incident response actions (the "case timeline").

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `incident_id` | BIGINT | FK → `cyber.cyber_incidents` |
| `event_type` | NVARCHAR(50) | STATUS_CHANGE / NOTE / EVIDENCE_ADDED / ESCALATION / CONTAINMENT_ACTION |
| `description_en` | NVARCHAR(MAX) | Event description |
| `performed_by` | BIGINT | FK → `iam.users` |
| `event_at` | DATETIME2(7) | When the event occurred |

### `cyber.cyber_assets`

Asset inventory — the target of vulnerabilities and incidents.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `external_id` | UNIQUEIDENTIFIER | API-exposed ID |
| `organisation_id` | BIGINT | Tenant |
| `asset_name` | NVARCHAR(200) | Asset name |
| `asset_type` | NVARCHAR(50) | SERVER / WORKSTATION / NETWORK_DEVICE / APPLICATION / DATABASE / CLOUD_SERVICE |
| `classification` | NVARCHAR(20) | PUBLIC / INTERNAL / CONFIDENTIAL / RESTRICTED (NCA ECC data classification) |
| `confidentiality_rating` | TINYINT | 1–5 CIA rating |
| `integrity_rating` | TINYINT | 1–5 CIA rating |
| `availability_rating` | TINYINT | 1–5 CIA rating |
| `ip_address` | NVARCHAR(45) | IPv4/IPv6 |
| `owner_id` | BIGINT | FK → `iam.users` |
| `department_id` | BIGINT | FK → `platform.business_units` |
| `is_critical` | BIT | Flagged as critical infrastructure |

### `cyber.vulnerabilities`

Vulnerability tracking per asset, aligned with CVE/CVSS.

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT IDENTITY | PK |
| `asset_id` | BIGINT | FK → `cyber.cyber_assets` |
| `cve_id` | NVARCHAR(30) | e.g. CVE-2024-12345 |
| `cvss_score` | DECIMAL(3,1) | 0.0–10.0 |
| `severity` | NVARCHAR(20) | CRITICAL / HIGH / MEDIUM / LOW / INFORMATIONAL |
| `status` | NVARCHAR(30) | OPEN / IN_REMEDIATION / PATCHED / ACCEPTED / FALSE_POSITIVE |
| `discovered_at` | DATETIME2(7) | Discovery date |
| `remediation_due_date` | DATE | SLA-driven due date |
| `remediation_notes` | NVARCHAR(MAX) | Remediation action notes |

---

## 2. Case Management Integration

All module-level events that require cross-functional coordination are routed through the Workflow module as `WorkflowInstance` records. The incident response workflow follows these states:

```
DETECTED → TRIAGED → IN_PROGRESS → CONTAINED → ERADICATED → RECOVERED → CLOSED
                                       ↓
                                  ESCALATED (if SLA breached)
```

Each state transition is recorded in `workflow.workflow_instances` and creates an entry in `cyber.incident_timeline`.

---

## 3. Cross-Module Risk Linkage

When an incident is classified with severity = HIGH or CRITICAL:

1. `IncidentEscalatedEvent` is published via Spring `ApplicationEventPublisher`
2. `RiskLinkageService` creates a new entry in `risk.risks` with:
   - `source_module = 'CYBER'`
   - `source_entity_id = incident.id`
   - `inherent_likelihood` and `inherent_impact` derived from incident severity
3. The `linked_risk_id` column on the incident is populated with the new risk ID

This linkage is visible on both the incident detail page and the unified risk register.

---

## 4. NCA ECC Alignment

The incident model maps directly to NCA ECC 2.0 domain **ECC-2-5 (Cybersecurity Incident and Threat Management)**:

| NCA ECC Control | Coverage |
|---|---|
| ECC-2-5-1 | Incident detection and reporting (`detected_at`, `reported_at`) |
| ECC-2-5-2 | Incident classification and severity (`incident_type`, `severity`) |
| ECC-2-5-3 | Incident response procedures (workflow state machine) |
| ECC-2-5-4 | Incident timeline and evidence (`incident_timeline`, attachments) |
| ECC-2-5-5 | Post-incident review (closure notes, linked risk treatment) |
