# ERD - Cybersecurity Module

## Purpose

This diagram defines the data model for the Cybersecurity module covering asset management, vulnerability tracking, threat identification, incident management, cyber assessments, and cyber-specific controls. It shows the critical linkage from incidents to the Risk module for enterprise risk escalation.

## Entity-Relationship Diagram

```mermaid
erDiagram
    organizations ||--o{ assets : owns
    organizations ||--o{ threats : identifies
    organizations ||--o{ incidents : reports
    assets ||--o{ vulnerabilities : has
    assets ||--o{ cyber_controls : protected_by
    threats ||--o{ incidents : triggers
    vulnerabilities ||--o{ incidents : exploited_in
    incidents ||--o{ cyber_assessments : assessed_by
    incidents }o--o| risks : escalates_to
    controls ||--o{ cyber_controls : implements

    organizations {
        bigint id PK
        nvarchar code UK
        nvarchar name_en
    }
    controls {
        bigint id PK
        bigint org_id FK
        nvarchar code
        nvarchar title_en
    }
    risks {
        bigint id PK
        bigint org_id FK
        nvarchar code
        nvarchar title_en
    }
    assets {
        bigint id PK
        bigint org_id FK
        nvarchar code UK
        nvarchar name_en
        nvarchar asset_type
        nvarchar classification
        nvarchar criticality
        nvarchar owner
        nvarchar location
        nvarchar status
    }
    vulnerabilities {
        bigint id PK
        bigint asset_id FK
        nvarchar code UK
        nvarchar title
        nvarchar severity
        nvarchar cve_id
        nvarchar status
        datetime2 discovered_date
        datetime2 remediated_date
    }
    threats {
        bigint id PK
        bigint org_id FK
        nvarchar code UK
        nvarchar name_en
        nvarchar threat_type
        nvarchar source
        nvarchar likelihood
        nvarchar description
    }
    incidents {
        bigint id PK
        bigint org_id FK
        nvarchar code UK
        nvarchar title
        nvarchar severity
        nvarchar status
        nvarchar incident_type
        bigint threat_id FK
        bigint vulnerability_id FK
        bigint risk_id FK
        datetime2 detected_date
        datetime2 reported_date
        datetime2 resolved_date
        bigint assigned_to FK
    }
    cyber_assessments {
        bigint id PK
        bigint incident_id FK
        nvarchar assessment_type
        nvarchar impact_analysis
        nvarchar root_cause
        nvarchar lessons_learned
        datetime2 assessment_date
        bigint assessed_by FK
    }
    cyber_controls {
        bigint id PK
        bigint asset_id FK
        bigint control_id FK
        nvarchar implementation_status
        nvarchar effectiveness
        datetime2 last_review_date
    }
```

## Key Concepts

- **assets** - IT and information assets (servers, databases, applications, endpoints) classified by type, criticality, and data classification.
- **vulnerabilities** - Known weaknesses in assets, optionally linked to CVE identifiers, tracked through discovery to remediation.
- **threats** - Identified threat actors and threat types (malware, phishing, insider, APT) relevant to the organization.
- **incidents** - Security incidents from detection through resolution. Can link to the triggering threat and exploited vulnerability.
- **cyber_assessments** - Post-incident analysis including impact, root cause, and lessons learned.
- **cyber_controls** - Maps platform controls to specific assets, tracking implementation status and effectiveness.

## Cross-Module Relationships

- **incidents -> risks** - A cyber incident can escalate to an enterprise risk entry in the Risk module via risk_id. This enables the unified risk view.
- **cyber_controls -> platform.controls** - Reuses the shared controls library for consistent control management across modules.
- **incidents -> privacy.breach_records** - Incidents involving personal data trigger privacy breach workflows (linked via the Privacy module).
