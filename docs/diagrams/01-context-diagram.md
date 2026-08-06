# C4 Context Diagram - TSS GRC Platform
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This diagram provides a high-level view of the TSS GRC platform, showing the system boundary, external actors (users and personas), and external systems it integrates with. It is the starting point for understanding the platform's scope and ecosystem.

## Context Diagram

```mermaid
flowchart TB
    subgraph actors["External Actors"]
        RM["Risk Manager"]
        CISO["CISO / Security Officer"]
        CO["Compliance Officer"]
        DPO["Data Protection Officer"]
        BCMgr["BCM Manager"]
        EXEC["Executive / Board Member"]
        ADMIN["System Administrator"]
        AUDITOR["Internal / External Auditor"]
    end

    subgraph grc["TSS GRC Platform"]
        CORE["Modular GRC Application\n(Spring Boot 3.5)\n\n10 Modules:\nERM | Cybersecurity | BCM\nData Governance | Privacy\nCompliance & Audit | Workflow\nReporting | Licensing | Admin"]
    end

    subgraph external["External Systems"]
        LDAP["LDAP / Active Directory\n(Authentication & User Sync)"]
        EMAIL["Email Gateway\n(SMTP / Exchange)"]
        SMS["SMS Gateway\n(Notifications & Alerts)"]
        FILESTOR["File Storage\n(Document Repository)"]
        EXTFEED["External Threat Feeds\n(CVE, NVD)"]
        REGFEED["Regulatory Update Feeds\n(Compliance Libraries)"]
    end

    RM -- "Manage risks,\nassessments, treatments" --> CORE
    CISO -- "Cyber risk oversight,\nincident management" --> CORE
    CO -- "Framework assessments,\naudit findings" --> CORE
    DPO -- "Privacy assessments,\nbreach management" --> CORE
    BCMgr -- "BIA, recovery plans,\nexercise management" --> CORE
    EXEC -- "Dashboards,\nrisk appetite review" --> CORE
    ADMIN -- "User management,\nlicensing, configuration" --> CORE
    AUDITOR -- "Audit trails,\ncompliance evidence" --> CORE

    CORE -- "Authenticate users,\nsync directory" --> LDAP
    CORE -- "Send notifications,\nalerts, reports" --> EMAIL
    CORE -- "Send SMS alerts\nfor critical events" --> SMS
    CORE -- "Store/retrieve\ndocuments & evidence" --> FILESTOR
    CORE -- "Ingest vulnerability\n& threat data" --> EXTFEED
    CORE -- "Ingest regulatory\nupdates" --> REGFEED

    style grc fill:#1168bd,color:#ffffff,stroke:#0b4884
    style actors fill:#08427b,color:#ffffff,stroke:#052e56
    style external fill:#999999,color:#ffffff,stroke:#666666
```

## Key Interactions

- **User Actors** interact with the platform through a web browser over HTTPS.
- **LDAP/AD** provides centralized authentication and directory synchronization.
- **Email and SMS Gateways** handle all outbound notifications, escalations, and report delivery.
- **File Storage** serves as the document repository for attachments, evidence, and generated reports.
- **External Feeds** provide threat intelligence and regulatory update data to keep the platform current.
