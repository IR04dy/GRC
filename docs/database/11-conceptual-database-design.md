# Conceptual Database Design

## Purpose
Provides the conceptual view of the TSS GRC Platform database — the major entity groups, their purpose, and relationships at a business abstraction level, independent of physical implementation details.

---

## Major Entity Groups

| Group | Schema | Core Entities | Description |
|-------|--------|---------------|-------------|
| **Organization Management** | platform | organizations, business_units | Hierarchical org structure; tenant boundary |
| **Identity & Access** | iam | users, roles, permissions, user_roles, role_permissions | Authentication and authorization |
| **Administration** | admin | modules, licenses, system_configurations | Platform setup and licensing |
| **Control Framework** | platform | controls, control_frameworks, control_framework_mappings | Shared control library across all GRC modules |
| **Workflow Engine** | workflow | workflow_definitions, workflow_steps, workflow_instances, workflow_instance_steps | Configurable approval and review processes |
| **File & Evidence** | platform | attachments | Document and evidence file metadata |
| **Notifications** | platform | notifications, notification_templates | In-app and email notification management |
| **Reference Data** | platform | lookup_categories, lookup_values, translations | Master data and multilingual content |
| **Enterprise Risk** | risk | risks, risk_assessments, risk_treatments, risk_categories, risk_appetite_statements, risk_indicators | Core ERM domain |
| **Cybersecurity** | cyber | assets, vulnerabilities, threats, incidents, cyber_assessments, cyber_controls | Cybersecurity risk and compliance |
| **Business Continuity** | bcm | critical_processes, process_dependencies, business_impact_analyses, bcm_plans, recovery_strategies, bcm_exercises | BCM domain |
| **Privacy** | privacy | data_inventory, processing_activities, privacy_assessments, consent_records, breach_records, ropa_entries | Privacy and PDPL compliance |
| **Compliance** | compliance | compliance_frameworks, assessments, assessment_controls, findings, gaps, remediation_plans, evidence_items | Multi-framework compliance |
| **Data Governance** | platform | data_assets, data_classifications, data_quality_rules, data_lineage, data_stewards | Data governance domain |
| **Reporting** | reporting | report_definitions, report_schedules, report_executions, dashboard_configurations, saved_filters | Reporting and analytics configuration |
| **Audit** | audit | audit_logs | Immutable action history |

---

## Conceptual Relationships

```
Organization
    ├── has many Business Units
    ├── has many Users (via IAM)
    ├── holds Licenses (per Module)
    ├── has Risks (ERM, Cyber, BCM, Privacy — all scoped to org)
    ├── has Controls (shared library)
    └── has Compliance Assessments

User
    ├── belongs to Organization
    ├── belongs to Business Unit (optional)
    ├── has Roles (scoped to org/BU)
    └── performs Actions (tracked in Audit Log)

Control (Shared Library)
    ├── mapped to Control Frameworks (NCA, ISO 27001, etc.)
    ├── linked to Risks (risk mitigation)
    ├── assessed in Compliance Assessments
    └── assessed in Cyber Assessments

Risk (Universal)
    ├── sourced from ERM, Cyber, BCM, or Privacy
    ├── linked to Controls (risk treatment)
    ├── has Risk Assessments (history)
    └── has Risk Treatments (action plans)

Workflow Instance
    ├── attached to any entity (polymorphic: entity_type + entity_id)
    ├── progresses through Workflow Steps
    └── generates Audit Events

Attachment
    ├── linked to any entity (polymorphic)
    └── stored as metadata; file on filesystem/storage

Audit Log
    └── linked to any entity (polymorphic; no FK — insert-only)
```

---

## Cross-Module Risk Linkage (Key Design Point)

All risk-generating events from different modules feed into the unified risk register:

```
cyber.incidents      → creates/links → risk.risks (source_module = 'CYBER')
privacy.breach_records → creates/links → risk.risks (source_module = 'PRIVACY')
bcm.business_impact_analyses → creates/links → risk.risks (source_module = 'BCM')
compliance.findings (HIGH/CRITICAL) → creates/links → risk.risks (source_module = 'COMPLIANCE')
```

This is the mechanism behind the **Unified Risk View** — the most strategically important feature of the platform.

---

## Compliance Evidence Chain

```
compliance.assessments
    └── compliance.assessment_controls (per-control compliance status)
            ├── compliance.findings (gaps/issues found)
            │       └── compliance.gaps (gap details)
            │               └── compliance.remediation_plans (action plans)
            └── compliance.evidence_items (uploaded evidence)
                    └── platform.attachments (actual files)
```

---

## Scalability Considerations at Conceptual Level

| Entity | Expected Volume | Growth Driver |
|--------|----------------|--------------|
| audit.audit_logs | Very High (millions/year) | All user actions |
| risk.risks | Medium (thousands) | Risk register |
| compliance.assessment_controls | Medium-High | Controls × assessments |
| platform.attachments | High (metadata) | Evidence uploads |
| workflow.workflow_instance_steps | Medium-High | All workflow activity |

The conceptual design separates high-volume from low-volume entities into different schemas, enabling targeted optimization.
