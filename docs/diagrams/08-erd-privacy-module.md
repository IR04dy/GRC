# ERD - Privacy & Data Governance Module
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This diagram defines the data model for the Privacy module covering data inventory, processing activity records, privacy impact assessments, consent management, breach management, and Records of Processing Activities (ROPA). It shows cross-module links to cyber incidents and enterprise risks.

## Entity-Relationship Diagram

```mermaid
erDiagram
    organizations ||--o{ data_inventory : catalogs
    organizations ||--o{ processing_activities : performs
    organizations ||--o{ consent_records : collects
    organizations ||--o{ breach_records : tracks
    data_inventory ||--o{ processing_activities : processed_in
    processing_activities ||--o{ privacy_assessments : assessed_by
    processing_activities ||--o{ ropa_entries : documented_in
    breach_records }o--o| incidents : originates_from
    breach_records }o--o| risks : escalates_to

    organizations {
        bigint id PK
        nvarchar code UK
        nvarchar name_en
    }
    incidents {
        bigint id PK
        nvarchar code
        nvarchar title
        nvarchar severity
    }
    risks {
        bigint id PK
        nvarchar code
        nvarchar title_en
        nvarchar status
    }
    data_inventory {
        bigint id PK
        bigint org_id FK
        nvarchar code UK
        nvarchar name_en
        nvarchar data_category
        nvarchar classification
        nvarchar storage_location
        nvarchar retention_period
        nvarchar legal_basis
        bit contains_pii
        bit contains_sensitive
        bigint owner_id FK
        nvarchar status
    }
    processing_activities {
        bigint id PK
        bigint org_id FK
        bigint data_inventory_id FK
        nvarchar code UK
        nvarchar name_en
        nvarchar purpose
        nvarchar legal_basis
        nvarchar data_subjects
        nvarchar recipients
        nvarchar cross_border_transfer
        nvarchar safeguards
        nvarchar status
    }
    privacy_assessments {
        bigint id PK
        bigint activity_id FK
        nvarchar assessment_type
        nvarchar risk_level
        nvarchar findings
        nvarchar recommendations
        datetime2 assessment_date
        bigint assessed_by FK
        nvarchar status
    }
    consent_records {
        bigint id PK
        bigint org_id FK
        nvarchar data_subject_ref
        nvarchar consent_type
        nvarchar purpose
        bit consent_given
        datetime2 consent_date
        datetime2 withdrawal_date
        nvarchar evidence_ref
    }
    breach_records {
        bigint id PK
        bigint org_id FK
        bigint incident_id FK
        bigint risk_id FK
        nvarchar code UK
        nvarchar title
        nvarchar severity
        nvarchar data_subjects_affected
        int records_affected_count
        datetime2 detected_date
        datetime2 notified_authority_date
        datetime2 notified_subjects_date
        nvarchar status
        nvarchar root_cause
        nvarchar remedial_actions
    }
    ropa_entries {
        bigint id PK
        bigint activity_id FK
        nvarchar controller_name
        nvarchar processor_name
        nvarchar purpose
        nvarchar data_categories
        nvarchar retention_period
        nvarchar technical_measures
        nvarchar organizational_measures
        datetime2 last_updated
    }
```

## Key Concepts

- **data_inventory** - Catalog of all personal and sensitive data assets held by the organization, with classification and retention policies.
- **processing_activities** - Records of how data is processed, for what purpose, under what legal basis (PDPL, GDPR compliance).
- **privacy_assessments** - Privacy Impact Assessments (PIA) and Data Protection Impact Assessments (DPIA) for processing activities.
- **consent_records** - Tracks consent given by data subjects, including purpose, date, and withdrawal status.
- **breach_records** - Personal data breach register with authority/subject notification tracking and regulatory timelines.
- **ropa_entries** - Regulatory-mandated Records of Processing Activities for controller and processor documentation.

## Cross-Module Relationships

- **breach_records -> cyber.incidents** - A privacy breach originates from a cybersecurity incident (incident_id links to the Cyber module).
- **breach_records -> risk.risks** - A significant breach escalates to an enterprise risk entry (risk_id links to the Risk module).
- Both linkages feed into the **Unified Risk View** for holistic risk reporting.
