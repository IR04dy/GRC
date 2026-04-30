# Data Flow Diagram

## Purpose
Shows how data flows through the TSS GRC Platform layers — from user input through the application to the database and back to reporting.

---

## Core Data Flow

```mermaid
flowchart TD
    subgraph UI["Presentation Layer"]
        FORM[Thymeleaf HTML Form\nUser Data Input]
        TABLE[Data Table View\nPaginated list]
        DASH[Dashboard\nChart.js widgets]
    end

    subgraph Controller["Controller Layer (Spring MVC)"]
        WEB[Web Controller\n@Controller\nReturns HTML views]
        API[API Controller\n@RestController\nReturns JSON]
    end

    subgraph Service["Service Layer"]
        BIZ[Business Service\n@Service @Transactional\nBusiness logic + validation]
        AUDIT_SVC[Audit Service\nCaptures all changes]
        WFL_SVC[Workflow Service\nManages approvals]
        NOTIF_SVC[Notification Service\n@Async email + in-app]
        EXPORT_SVC[Export Service\nPDF / Excel / Word]
    end

    subgraph Repository["Data Access Layer"]
        REPO[Spring Data JPA Repository\nParameterized queries\norg_id filtered]
    end

    subgraph DB["SQL Server 2019 Express"]
        TABLES[Business Tables\nrisk / cyber / bcm / privacy / compliance]
        SHARED[Shared Tables\nplatform / iam / workflow / audit]
        VIEWS[Reporting Views\nvw_unified_risk_summary etc.]
    end

    subgraph External["External Services"]
        SMTP[SMTP Server\nEmail delivery]
        FS[File Storage\nAttachment files]
        LDAP[LDAP / AD\nOptional authentication]
    end

    FORM -->|HTTP POST + CSRF| WEB
    WEB -->|DTO| BIZ
    BIZ -->|Entity| REPO
    REPO -->|INSERT / UPDATE| TABLES
    BIZ --> AUDIT_SVC
    AUDIT_SVC -->|INSERT only| SHARED
    BIZ --> WFL_SVC
    WFL_SVC -->|State change| SHARED
    WFL_SVC --> NOTIF_SVC
    NOTIF_SVC -->|@Async| SMTP
    NOTIF_SVC -->|INSERT| SHARED

    REPO -->|SELECT| TABLES
    REPO -->|SELECT| VIEWS
    REPO -->|Return entities| BIZ
    BIZ -->|DTO| WEB
    WEB -->|Model + View| TABLE
    API -->|JSON| DASH

    EXPORT_SVC -->|Query data| REPO
    EXPORT_SVC -->|Stream file| FS

    LDAP -->|User authentication| BIZ
```

---

## Module-to-Module Data Flow

```mermaid
flowchart LR
    subgraph CYBER_MOD["Cybersecurity Module"]
        INC[Incident Created\nCRITICAL severity]
    end

    subgraph SHARED_SVC["Shared Services"]
        WFL[WorkflowService\nStart escalation workflow]
        NOTIF[NotificationService\nAlert CISO + Risk Manager]
        AUDIT[AuditService\nLog all actions]
    end

    subgraph ERM_MOD["ERM Module"]
        RISK[Risk Record Created\nsource_module=CYBER]
    end

    subgraph PRV_MOD["Privacy Module"]
        BREACH[Breach Record\nlinked_cyber_incident_id]
    end

    subgraph REPORTING["Reporting"]
        UNI[Unified Risk View\nAll sources visible]
    end

    INC -->|Event: IncidentCreatedEvent| WFL
    INC -->|Service call| RISK
    INC -->|If breach| BREACH
    BREACH -->|linked_risk_id| RISK
    WFL --> NOTIF
    WFL --> AUDIT
    RISK --> UNI
    BREACH --> UNI
```

---

## Reporting Data Flow

```mermaid
flowchart LR
    DB[SQL Server Views\nvw_unified_risk_summary\nvw_compliance_status\netc.] --> CACHE[Caffeine Cache\n5-minute TTL\nKey: orgId + filters]
    CACHE --> API2[Dashboard API\nGET /api/v1/dashboard/...]
    API2 --> CHARTS[Chart.js\nHeatmap + Bars + KPIs]

    DB --> REPO2[Report Repository\nPaginated queries\nFilter-aware]
    REPO2 --> EXPORT[ExportService\nApache POI + OpenPDF]
    EXPORT -->|Async| FILE[Temp File Storage]
    FILE -->|Stream| DOWNLOAD[User Download\nContent-Disposition header]
```
