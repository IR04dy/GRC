# C4 Container Architecture - TSS GRC Platform
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This diagram decomposes the TSS GRC platform into its primary containers: the web browser client, the Spring Boot application with its internal modules, the SQL Server database, and supporting infrastructure services.

## Container Diagram

```mermaid
flowchart TB
    BROWSER["Web Browser\n(SPA / Thymeleaf)\n\nHTML5, CSS3, JavaScript"]

    subgraph app["Spring Boot 3.5 Application"]
        direction TB
        subgraph api["API Layer"]
            REST["REST Controllers\n(Module APIs)"]
            SEC["Spring Security\n(JWT + RBAC)"]
        end

        subgraph modules["Business Modules"]
            ERM["ERM Module"]
            CYBER["Cybersecurity Module"]
            BCM["BCM Module"]
            DG["Data Governance Module"]
            PRIV["Privacy Module"]
            COMP["Compliance & Audit Module"]
            WF["Workflow Module"]
            RPT["Reporting Module"]
            LIC["Licensing Module"]
            ADM["Admin Module"]
        end

        subgraph shared["Shared Services"]
            AUDIT_SVC["Audit Service"]
            NOTIF_SVC["Notification Service"]
            FILE_SVC["File Service"]
            WF_ENGINE["Workflow Engine"]
            CACHE["Cache Layer"]
        end
    end

    subgraph data["Data Tier"]
        SQLDB[("SQL Server 2019\n\nSchemas: platform, iam, admin,\nworkflow, risk, cyber, bcm,\nprivacy, compliance,\nreporting, audit")]
    end

    subgraph infra["Infrastructure Services"]
        LDAP["LDAP / Active Directory"]
        EMAIL["Email Gateway\n(SMTP)"]
        SMS["SMS Gateway"]
        FILESTOR["File Storage\n(Shared Drive / S3)"]
    end

    BROWSER -- "HTTPS / REST API" --> REST
    REST --> SEC
    SEC --> modules
    modules --> shared

    ERM --> WF_ENGINE
    CYBER --> WF_ENGINE
    BCM --> WF_ENGINE
    PRIV --> WF_ENGINE
    COMP --> WF_ENGINE

    modules --> AUDIT_SVC
    shared --> SQLDB

    NOTIF_SVC --> EMAIL
    NOTIF_SVC --> SMS
    FILE_SVC --> FILESTOR
    SEC -- "LDAP Bind\n& User Sync" --> LDAP
    modules --> SQLDB

    style app fill:#1168bd,color:#ffffff,stroke:#0b4884
    style data fill:#2d882d,color:#ffffff,stroke:#1a5c1a
    style infra fill:#999999,color:#ffffff,stroke:#666666
    style BROWSER fill:#08427b,color:#ffffff,stroke:#052e56
```

## Container Descriptions

| Container | Technology | Responsibility |
|-----------|-----------|----------------|
| Web Browser | HTML5 / JS | User interface for all GRC functions |
| Spring Boot App | Java 21 + Spring Boot 3.5 | Core application hosting all 10 modules as a modular monolith |
| SQL Server | SQL Server 2019 | Persistent storage with schema-per-module isolation |
| File Storage | Shared Drive or S3 | Document and evidence file storage |
| LDAP/AD | Active Directory | Authentication provider and user directory |
| Email Gateway | SMTP / Exchange | Outbound email notifications |
| SMS Gateway | SMS API | Critical alert delivery via SMS |

## Design Decisions

- **Modular Monolith**: All modules are deployed within a single Spring Boot application but are logically isolated via package boundaries and separate database schemas.
- **Schema Isolation**: Each functional domain owns its database schema, enabling clear data ownership while sharing a single SQL Server instance.
- **Shared Services**: Audit logging, workflow engine, notifications, and file management are cross-cutting concerns shared by all modules.
