# Module Interaction Diagram - TSS GRC Platform
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This diagram shows all 10 platform modules, their inter-module dependencies, and how they leverage shared services. Understanding these interactions is critical for maintaining module boundaries and planning integration points.

## Module Interaction Diagram

```mermaid
flowchart TB
    subgraph shared_services["Shared Services Layer"]
        AUDIT["Audit Service\n(audit schema)"]
        WF_ENGINE["Workflow Engine\n(workflow schema)"]
        NOTIF["Notification Service\n(Email / SMS)"]
        FILE_SVC["File Storage Service"]
        CACHE["Cache Service"]
    end

    subgraph core_modules["Core Platform Modules"]
        ADM["Admin Module\n(admin schema)\n\nOrg setup, config,\nbusiness units"]
        LIC["Licensing Module\n(admin schema)\n\nModule activation,\nuser limits"]
        IAM["IAM / Admin Module\n(iam schema)\n\nUsers, roles,\npermissions"]
    end

    subgraph business_modules["Business Modules"]
        ERM["ERM Module\n(risk schema)\n\nRisks, assessments,\ntreatments"]
        CYBER["Cybersecurity Module\n(cyber schema)\n\nAssets, vulnerabilities,\nincidents"]
        BCM["BCM Module\n(bcm schema)\n\nBIA, plans,\nrecovery strategies"]
        PRIV["Privacy Module\n(privacy schema)\n\nPIA, data inventory,\nbreach management"]
        COMP["Compliance & Audit\n(compliance schema)\n\nFrameworks, assessments,\nfindings"]
        DG["Data Governance\n(privacy schema)\n\nData classification,\nretention policies"]
    end

    subgraph cross_cutting["Cross-Cutting Modules"]
        RPT["Reporting Module\n(reporting schema)\n\nDashboards, reports,\nunified risk view"]
        WFMOD["Workflow Module\n(workflow schema)\n\nApprovals, SLAs,\nescalations"]
    end

    %% Core module dependencies
    ADM -- "org context" --> ERM
    ADM -- "org context" --> CYBER
    ADM -- "org context" --> BCM
    ADM -- "org context" --> PRIV
    ADM -- "org context" --> COMP
    IAM -- "user & role context" --> ADM
    LIC -- "module activation" --> ADM

    %% Business module interactions
    ERM -- "risk-control\nlinkage" --> COMP
    ERM -- "cyber risks" --> CYBER
    ERM -- "operational\nrisks" --> BCM
    ERM -- "privacy risks" --> PRIV

    CYBER -- "incident\nescalation" --> BCM
    CYBER -- "breach\nnotification" --> PRIV
    CYBER -- "control\nevidence" --> COMP

    BCM -- "process\ndependencies" --> DG
    PRIV -- "data inventory" --> DG
    COMP -- "audit findings" --> ERM

    %% Cross-cutting integrations
    ERM --> WFMOD
    CYBER --> WFMOD
    BCM --> WFMOD
    PRIV --> WFMOD
    COMP --> WFMOD

    ERM -- "risk data" --> RPT
    CYBER -- "cyber metrics" --> RPT
    BCM -- "BCM status" --> RPT
    PRIV -- "privacy metrics" --> RPT
    COMP -- "compliance status" --> RPT

    %% Shared service usage
    ERM --> AUDIT
    CYBER --> AUDIT
    BCM --> AUDIT
    PRIV --> AUDIT
    COMP --> AUDIT
    ADM --> AUDIT

    WFMOD --> NOTIF
    WFMOD --> AUDIT
    RPT --> FILE_SVC

    ERM --> FILE_SVC
    CYBER --> FILE_SVC
    COMP --> FILE_SVC

    style shared_services fill:#6c757d,color:#ffffff,stroke:#495057
    style core_modules fill:#0d6efd,color:#ffffff,stroke:#0a58ca
    style business_modules fill:#198754,color:#ffffff,stroke:#146c43
    style cross_cutting fill:#6f42c1,color:#ffffff,stroke:#59359a
```

## Module Dependency Summary

| Module | Depends On | Depended On By |
|--------|-----------|----------------|
| Admin | IAM, Licensing | All business modules |
| ERM | Admin, Workflow, Audit | Cyber, BCM, Privacy, Compliance, Reporting |
| Cybersecurity | Admin, ERM, Workflow, Audit | BCM, Privacy, Compliance, Reporting |
| BCM | Admin, ERM, Cyber, Workflow, Audit | Reporting |
| Privacy | Admin, ERM, Cyber, Data Gov, Workflow, Audit | Reporting |
| Compliance | Admin, ERM, Workflow, Audit | Reporting |
| Data Governance | Admin, Audit | BCM, Privacy |
| Reporting | All business modules | None |
| Workflow | Notification, Audit | All business modules |
| Licensing | None | Admin |

## Design Principles

- Modules communicate through well-defined service interfaces, not direct database access across schemas.
- All state-changing operations pass through the Audit Service for traceability.
- The Workflow Engine is the single orchestrator for approval flows across all modules.
- The Reporting Module is a read-only consumer that aggregates data from all business modules.
