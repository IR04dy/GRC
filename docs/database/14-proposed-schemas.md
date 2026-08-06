# Proposed Database Schemas and Table Inventory
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Complete schema list with table inventory, descriptions, and estimated data volumes for the TSS GRC Platform SQL Server 2019 database.

---

## Schema Overview

| Schema | Purpose | Est. Tables | Owner |
|--------|---------|-------------|-------|
| `platform` | Shared business entities | 17 | Platform Team |
| `iam` | Identity and access management | 5 | Security Team |
| `admin` | Administration and licensing | 4 | Platform Team |
| `workflow` | Workflow engine | 4 | Platform Team |
| `risk` | Enterprise Risk Management | 7 | Risk Module Team |
| `cyber` | Cybersecurity Risk & Compliance | 7 | Cyber Module Team |
| `bcm` | Business Continuity Management | 7 | BCM Module Team |
| `privacy` | Privacy & Personal Data Protection | 6 | Privacy Module Team |
| `compliance` | Compliance & Audit Management | 7 | Compliance Module Team |
| `reporting` | Reporting & Analytics | 5 | Reporting Team |
| `audit` | Audit trail | 2 | Security Team |

---

## Schema: platform (Shared Business Entities)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| organizations | Organization hierarchy; multi-tenant root | S |
| business_units | BU hierarchy within organizations | S |
| controls | Shared control library | M |
| control_frameworks | GRC frameworks (NCA, ISO 27001, etc.) | S |
| control_framework_domains | Framework sections/domains | S |
| control_framework_mappings | Control-to-framework reference mappings | M |
| attachments | File upload metadata (polymorphic) | M |
| notifications | In-app notification records | L |
| notification_templates | Notification email/in-app templates | S |
| lookup_categories | Reference data category definitions | S |
| lookup_values | Reference data values | S |
| translations | Dynamic multilingual content | M |
| data_assets | Data governance: data asset registry | M |
| data_classifications | Data classification levels | S |
| data_quality_rules | Data quality rule definitions | S |
| data_lineage | Data lineage relationships | M |
| data_stewards | Data steward assignments | S |

---

## Schema: iam (Identity and Access Management)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| users | Platform users | M |
| roles | Access roles (system + custom) | S |
| permissions | Granular permissions | S |
| user_roles | User-to-role assignments (scoped) | M |
| role_permissions | Role-to-permission assignments | S |

---

## Schema: admin (Administration)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| modules | Platform modules registry | S |
| licenses | Module licenses per organization | S |
| system_configurations | Platform-wide key-value configuration | S |
| org_configurations | Organization-specific configuration | S |

---

## Schema: workflow (Workflow Engine)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| workflow_definitions | Workflow template definitions | S |
| workflow_steps | Steps within workflow definitions | S |
| workflow_instances | Active/completed workflow executions | L |
| workflow_instance_steps | Per-step execution records | L |

---

## Schema: risk (Enterprise Risk Management)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| risk_categories | Risk categorization hierarchy | S |
| risks | Master risk register | M |
| risk_assessments | Historical risk assessment records | M |
| risk_treatments | Risk treatment plans | M |
| risk_controls | Risk-to-control linkage | M |
| risk_appetite_statements | Organizational risk appetite | S |
| risk_indicators | Key Risk Indicators (KRIs) | M |

---

## Schema: cyber (Cybersecurity Risk & Compliance)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| assets | IT/OT asset inventory | M |
| vulnerabilities | Vulnerabilities linked to assets | L |
| threats | Threat intelligence entries | M |
| incidents | Cybersecurity incidents | M |
| cyber_assessments | Cybersecurity assessments | M |
| cyber_controls | Control assessments per asset/assessment | M |
| incident_affected_assets | Incident-to-asset linkage | M |

---

## Schema: bcm (Business Continuity Management)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| critical_processes | Critical business processes | S |
| process_dependencies | Process dependency relationships | S |
| business_impact_analyses | BIA records | S |
| bcm_plans | BCP/DRP plan documents | S |
| recovery_strategies | Recovery strategies per process | S |
| bcm_exercises | BCM exercise records | S |
| bcm_incidents | BCM-specific incidents | S |

---

## Schema: privacy (Privacy & Personal Data Protection)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| data_inventory | Personal data inventory | M |
| processing_activities | Personal data processing activities | M |
| privacy_assessments | PIA/DPIA assessments | S |
| consent_records | Data subject consent records | M |
| breach_records | Personal data breach records | S |
| ropa_entries | Record of Processing Activities | M |

---

## Schema: compliance (Compliance & Audit Management)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| compliance_frameworks | Compliance framework registry | S |
| assessments | Compliance assessments | M |
| assessment_controls | Per-control compliance status | L |
| findings | Compliance findings | M |
| gaps | Gap analysis records | M |
| remediation_plans | Remediation action plans | M |
| evidence_items | Formal evidence records | M |

---

## Schema: reporting (Reporting & Analytics)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| report_definitions | Report template definitions | S |
| report_schedules | Scheduled report configurations | S |
| report_executions | Report generation history | M |
| dashboard_configurations | User/org dashboard layouts | S |
| saved_filters | User-saved filter presets | S |

---

## Schema: audit (Audit Trail)

| Table | Description | Est. Volume |
|-------|-------------|-------------|
| audit_logs | Immutable action audit trail | **XL** |
| system_events | System-level events (startup, errors) | L |

---

## Volume Key

| Code | Description | Approx. Rows |
|------|-------------|-------------|
| S | Small | < 1,000 |
| M | Medium | 1,000 – 100,000 |
| L | Large | 100,000 – 1,000,000 |
| XL | Very Large | > 1,000,000 |

> **Express Edition Note:** With the 10 GB limit, XL tables (audit_logs) are the primary constraint. Archival strategy must be operational from day one of production use.
