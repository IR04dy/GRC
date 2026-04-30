# ERD - Risk Management Module

## Purpose

This diagram defines the data model for the Enterprise Risk Management (ERM) module. It covers risk identification, categorization, assessment (inherent and residual), treatment planning, control linkage, risk appetite, and key risk indicators. Relationships to the shared platform entities (organizations, controls) are shown.

## Entity-Relationship Diagram

```mermaid
erDiagram
    organizations ||--o{ risks : owns
    organizations ||--o{ risk_categories : defines
    organizations ||--o{ risk_appetite_statements : sets
    risk_categories ||--o{ risks : categorizes
    risks ||--o{ risk_assessments : assessed_by
    risks ||--o{ risk_treatments : treated_by
    risks ||--o{ risk_controls : mitigated_by
    controls ||--o{ risk_controls : applied_to
    risks ||--o{ risk_indicators : monitored_by

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
    risk_categories {
        bigint id PK
        bigint org_id FK
        nvarchar code UK
        nvarchar name_en
        nvarchar name_ar
        nvarchar description
        bigint parent_category_id FK
        bit is_active
    }
    risks {
        bigint id PK
        bigint org_id FK
        bigint category_id FK
        nvarchar code UK
        nvarchar title_en
        nvarchar title_ar
        nvarchar description
        nvarchar risk_source
        nvarchar status
        bigint owner_id FK
        datetime2 identified_date
    }
    risk_assessments {
        bigint id PK
        bigint risk_id FK
        nvarchar assessment_type
        int likelihood_score
        int impact_score
        int risk_score
        nvarchar risk_level
        nvarchar assessment_method
        datetime2 assessment_date
        bigint assessed_by FK
    }
    risk_treatments {
        bigint id PK
        bigint risk_id FK
        nvarchar treatment_type
        nvarchar description
        nvarchar status
        datetime2 target_date
        bigint responsible_id FK
        decimal estimated_cost
    }
    risk_controls {
        bigint id PK
        bigint risk_id FK
        bigint control_id FK
        nvarchar effectiveness
        datetime2 last_tested_date
    }
    risk_appetite_statements {
        bigint id PK
        bigint org_id FK
        nvarchar category
        nvarchar appetite_level
        int threshold_value
        nvarchar description
        datetime2 effective_date
        bigint approved_by FK
    }
    risk_indicators {
        bigint id PK
        bigint risk_id FK
        nvarchar code UK
        nvarchar name_en
        nvarchar indicator_type
        decimal threshold_green
        decimal threshold_amber
        decimal threshold_red
        decimal current_value
        datetime2 last_measured_date
    }
```

## Key Concepts

- **risk_categories** - Hierarchical taxonomy for classifying risks (strategic, operational, financial, compliance, cyber). Supports parent-child nesting.
- **risks** - Core risk register entries owned by an organization and assigned to a risk owner.
- **risk_assessments** - Records both inherent (before controls) and residual (after controls) assessments using likelihood x impact scoring.
- **risk_treatments** - Treatment plans with one of four strategies: Accept, Mitigate, Transfer, or Avoid.
- **risk_controls** - Links risks to platform controls and tracks control effectiveness.
- **risk_appetite_statements** - Organization-level risk appetite and tolerance thresholds per category.
- **risk_indicators** - Key Risk Indicators (KRIs) with traffic-light thresholds for ongoing monitoring.

## Cross-Module Relationships

- Risks link to **platform.controls** for shared control mitigation tracking.
- Risks are owned by **platform.organizations** for multi-tenant isolation.
- Cyber incidents and privacy breaches can create linked risk entries via the risk_source field.
