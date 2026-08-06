# Schema-Module Diagram
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Shows the SQL Server database schema structure and which tables belong to which schema. Each schema corresponds to a logical module boundary.

---

```mermaid
flowchart TB
    subgraph DB["TSS_GRC Database — SQL Server 2019 Express"]

        subgraph platform["platform schema"]
            P1[organizations]
            P2[business_units]
            P3[controls]
            P4[control_frameworks]
            P5[control_framework_domains]
            P6[control_framework_mappings]
            P7[attachments]
            P8[notifications]
            P9[notification_templates]
            P10[lookup_categories]
            P11[lookup_values]
            P12[translations]
            P13[data_assets]
            P14[data_classifications]
            P15[data_quality_rules]
            P16[data_lineage]
            P17[data_stewards]
        end

        subgraph iam["iam schema"]
            I1[users]
            I2[roles]
            I3[permissions]
            I4[user_roles]
            I5[role_permissions]
        end

        subgraph admin["admin schema"]
            A1[modules]
            A2[licenses]
            A3[system_configurations]
            A4[org_configurations]
        end

        subgraph workflow["workflow schema"]
            W1[workflow_definitions]
            W2[workflow_steps]
            W3[workflow_instances]
            W4[workflow_instance_steps]
        end

        subgraph risk["risk schema"]
            R1[risk_categories]
            R2[risks]
            R3[risk_assessments]
            R4[risk_treatments]
            R5[risk_controls]
            R6[risk_appetite_statements]
            R7[risk_indicators]
        end

        subgraph cyber["cyber schema"]
            C1[assets]
            C2[vulnerabilities]
            C3[threats]
            C4[incidents]
            C5[incident_affected_assets]
            C6[cyber_assessments]
            C7[cyber_controls]
        end

        subgraph bcm["bcm schema"]
            B1[critical_processes]
            B2[process_dependencies]
            B3[business_impact_analyses]
            B4[bcm_plans]
            B5[recovery_strategies]
            B6[bcm_exercises]
            B7[bcm_incidents]
        end

        subgraph privacy["privacy schema"]
            PV1[data_inventory]
            PV2[processing_activities]
            PV3[privacy_assessments]
            PV4[consent_records]
            PV5[breach_records]
            PV6[ropa_entries]
        end

        subgraph compliance["compliance schema"]
            CO1[compliance_frameworks]
            CO2[assessments]
            CO3[assessment_controls]
            CO4[findings]
            CO5[gaps]
            CO6[remediation_plans]
            CO7[evidence_items]
        end

        subgraph reporting["reporting schema"]
            RP1[report_definitions]
            RP2[report_schedules]
            RP3[report_executions]
            RP4[dashboard_configurations]
            RP5[saved_filters]
            RP6[vw_unified_risk_summary]:::view
            RP7[vw_compliance_status]:::view
            RP8[vw_active_controls]:::view
            RP9[vw_incident_summary]:::view
        end

        subgraph audit["audit schema"]
            AU1[audit_logs]
            AU2[system_events]
        end
    end

    classDef view fill:#e8f5e9,stroke:#4caf50
```

---

## Cross-Schema Reference Rules

```mermaid
flowchart LR
    risk --> platform
    risk --> iam
    cyber --> platform
    cyber --> iam
    cyber --> risk
    bcm --> platform
    bcm --> iam
    bcm --> risk
    privacy --> platform
    privacy --> iam
    privacy --> risk
    privacy --> cyber
    compliance --> platform
    compliance --> iam
    reporting --> risk
    reporting --> cyber
    reporting --> compliance
    workflow --> platform
    workflow --> iam
    audit -.->|insert only| platform
    all_modules --> workflow
    all_modules --> audit
```

**Key rule:** Module schemas may reference `platform` and `iam` schemas via FK. Module schemas should NOT have direct FKs to each other — cross-module links use application-level joins on shared IDs. The exception is `cyber → risk` (linked_risk_id) and `privacy → cyber` (linked_cyber_incident_id) as they represent intentional cross-module data linkage.
