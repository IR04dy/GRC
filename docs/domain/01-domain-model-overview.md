# Domain Model Overview
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## 1. Purpose

This document defines the overall domain model for the TSS (Trusted Security Solutions) GRC platform. It identifies bounded contexts, aggregate roots, domain relationships, and the modular decomposition strategy that underpins the entire system architecture.

## 2. Platform Vision

TSS GRC is a multi-tenant, bilingual (Arabic/English) enterprise platform spanning Governance, Risk, and Compliance domains. The platform is designed as a set of cohesive, loosely-coupled modules that share a common identity, organization, and workflow infrastructure.

## 3. Bounded Contexts

Each bounded context maps to a logical module and a dedicated SQL Server schema.

| Bounded Context | Schema | Description |
|---|---|---|
| Platform Core | `platform` | Shared infrastructure: lookups, localization, attachments, notifications, tags |
| Identity & Access | `iam` | Users, roles, permissions, authentication, sessions, API keys |
| Administration | `admin` | Tenant management, organization hierarchy, licensing, system configuration |
| Workflow Engine | `workflow` | Workflow definitions, task assignments, approvals, escalations, SLA tracking |
| Risk Management | `risk` | Risk register, assessments, treatments, appetite, heat maps, KRIs |
| Cyber Security | `cyber` | Vulnerability management, threat intelligence, security incidents, asset inventory |
| Business Continuity | `bcm` | BIA, BCP, recovery plans, exercises, crisis management, dependencies |
| Privacy Management | `privacy` | PIA/DPIA, RoPA, data inventory, consent management, breach notification |
| Compliance Management | `compliance` | Framework mapping, assessments, gaps, findings, remediation, evidence |
| Reporting & Analytics | `reporting` | Dashboards, KPIs, metrics, aggregations, scheduled reports |
| Audit Trail | `audit` | Immutable audit logs, change tracking, evidence chain, data lineage |

## 4. Aggregate Roots

Aggregate roots are the primary consistency boundaries within each bounded context.

### 4.1 Platform Core
- **Lookup** -- Master reference data entry with localized values
- **Attachment** -- File metadata with external storage reference
- **Notification** -- Platform notification with delivery tracking

### 4.2 Identity & Access
- **User** -- Central identity with profile, credentials, and preferences
- **Role** -- Named collection of permissions with scope
- **Permission** -- Granular access right bound to a module and action

### 4.3 Administration
- **Tenant** -- Top-level isolation boundary for all data
- **Organization** -- Legal entity within a tenant
- **BusinessUnit** -- Organizational subdivision with hierarchy

### 4.4 Workflow Engine
- **WorkflowDefinition** -- Template for a process flow with steps and transitions
- **WorkflowInstance** -- Running instance of a workflow bound to an entity
- **Task** -- Assignable unit of work within a workflow instance

### 4.5 Risk Management
- **Risk** -- Individual risk entry in the register
- **RiskAssessment** -- Point-in-time evaluation of a risk
- **RiskTreatment** -- Planned response to mitigate or accept a risk
- **RiskAppetite** -- Organizational threshold for risk tolerance

### 4.6 Cyber Security
- **Asset** -- IT asset (hardware, software, service) under management
- **Vulnerability** -- Known weakness in an asset or configuration
- **ThreatScenario** -- Modeled threat with likelihood and impact
- **SecurityIncident** -- Detected or reported security event

### 4.7 Business Continuity
- **BusinessImpactAnalysis** -- BIA for a process or function
- **BusinessContinuityPlan** -- BCP document with recovery procedures
- **RecoveryPlan** -- Specific recovery steps for a resource or process
- **BCMExercise** -- Test or drill of a continuity plan

### 4.8 Privacy Management
- **DataInventoryItem** -- Record of personal data processing activity
- **PrivacyImpactAssessment** -- PIA/DPIA for a processing operation
- **ConsentRecord** -- Captured consent from a data subject
- **PrivacyBreach** -- Breach event with notification tracking

### 4.9 Compliance Management
- **ComplianceFramework** -- Regulatory or standards framework (e.g., NCA ECC, ISO 27001)
- **ComplianceAssessment** -- Assessment cycle against a framework
- **Finding** -- Identified gap or non-conformity
- **RemediationAction** -- Corrective action to close a finding

### 4.10 Reporting & Analytics
- **Dashboard** -- Named dashboard with widget configuration
- **ReportDefinition** -- Saved report template with parameters
- **Metric** -- Calculated KPI or measure with time-series data

### 4.11 Audit Trail
- **AuditEntry** -- Immutable record of a system action
- **ChangeSet** -- Group of field-level changes in a single transaction

## 5. Cross-Context Relationships

The following relationships span bounded context boundaries. They are implemented via foreign keys to shared identifier columns, never via direct object references across schemas.

```
[admin.Tenant] 1──* [All entities via TenantId]
[iam.User] 1──* [workflow.Task] (AssignedToUserId)
[iam.User] 1──* [audit.AuditEntry] (PerformedByUserId)
[risk.Risk] *──* [compliance.ComplianceFramework] (via mapping table)
[risk.Risk] 1──* [risk.RiskAssessment]
[risk.Risk] *──* [cyber.Asset] (via risk-asset mapping)
[compliance.Finding] 1──* [compliance.RemediationAction]
[compliance.Finding] *──1 [workflow.WorkflowInstance]
[bcm.BusinessContinuityPlan] *──* [risk.Risk] (via BCP-risk mapping)
[privacy.PrivacyBreach] *──1 [workflow.WorkflowInstance]
[platform.Lookup] 1──* [All status/category columns]
```

## 6. Domain Invariants

| Invariant | Enforcement |
|---|---|
| Every entity belongs to exactly one tenant | `TenantId` NOT NULL on every table; row-level security |
| Soft-deleted records are excluded from business queries | Default filtered indexes; application-level query filters |
| Audit trail is append-only | No UPDATE/DELETE permissions on audit schema tables |
| Workflow transitions follow defined rules | State machine validation in workflow engine |
| Risk scores are derived, not manually set | Computed from likelihood x impact via assessment |
| Users can only access data within their tenant and permitted scope | IAM + RLS policies |

## 7. Module Dependency Map

```
platform (foundation) <── iam <── admin
       ^                    ^        ^
       |                    |        |
   workflow ──────────────-─┘        |
       ^                             |
       |                             |
   risk ───── cyber                  |
       |         |                   |
       |         └──── compliance ───┘
       |                    |
   bcm ─────────────────────┘
       |
   privacy
       |
   reporting ──── audit
```

Dependencies flow upward. Every module depends on `platform` and `iam`. The `audit` schema is written to by all modules but owned independently.

## 8. Design Principles

1. **Schema-per-Module** -- Each bounded context owns a dedicated SQL Server schema for clear ownership and security boundaries.
2. **Tenant Isolation** -- Every business table includes `TenantId` as the first column in the clustered index for efficient multi-tenant queries.
3. **Bilingual by Default** -- All user-facing text columns use `nvarchar` with companion translation tables or JSON columns for Arabic/English content.
4. **Soft Delete** -- Business entities use `IsDeleted` flags rather than physical deletion, with periodic archival processes.
5. **Temporal Auditing** -- SQL Server 2019 system-versioned temporal tables provide automatic history for key entities.
6. **Workflow Integration** -- Any entity requiring approval or multi-step processing can be bound to a workflow instance.
7. **Framework Agnostic** -- The compliance module supports arbitrary frameworks through a metadata-driven control library.
