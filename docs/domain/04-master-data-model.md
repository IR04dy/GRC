# Master Data Model

## 1. Purpose

This document defines the master and reference data structures for the TSS GRC platform. Master data provides the shared vocabulary, lookup values, framework references, and geographic data that all modules depend on. Consistent master data ensures uniform reporting, bilingual display, and regulatory alignment.

---

## 2. Lookup System

### 2.1 Design Philosophy

All enumerated values in the platform (statuses, severity levels, categories, priorities) are stored in a centralized lookup system rather than hardcoded in application code. This enables:

- Runtime configurability without code changes
- Bilingual display values (English and Arabic)
- Custom ordering for UI dropdowns
- Tenant-level overrides (future capability)
- Consistent values across all modules

### 2.2 Lookup Categories Table

```sql
CREATE TABLE platform.lookup_categories (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    category_code   VARCHAR(100) NOT NULL,
    name_en         NVARCHAR(200) NOT NULL,
    name_ar         NVARCHAR(200) NOT NULL,
    description_en  NVARCHAR(500) NULL,
    description_ar  NVARCHAR(500) NULL,
    module_code     VARCHAR(50) NULL,           -- NULL = cross-module
    is_system       BIT NOT NULL DEFAULT 1,     -- System categories cannot be deleted
    is_active       BIT NOT NULL DEFAULT 1,
    created_by      BIGINT NOT NULL,
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT NULL,
    updated_at      DATETIME2 NULL,
    CONSTRAINT UQ_lookup_category_code UNIQUE (category_code)
);
```

### 2.3 Lookup Values Table

```sql
CREATE TABLE platform.lookup_values (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    category_id     BIGINT NOT NULL REFERENCES platform.lookup_categories(id),
    value_code      VARCHAR(100) NOT NULL,
    display_en      NVARCHAR(300) NOT NULL,
    display_ar      NVARCHAR(300) NOT NULL,
    description_en  NVARCHAR(500) NULL,
    description_ar  NVARCHAR(500) NULL,
    display_order   INT NOT NULL DEFAULT 0,
    color_code      VARCHAR(7) NULL,            -- Hex color for UI badges (e.g., #FF0000)
    icon            VARCHAR(100) NULL,           -- Icon identifier for UI rendering
    numeric_value   DECIMAL(10,2) NULL,         -- Numeric equivalent for scoring
    parent_value_id BIGINT NULL REFERENCES platform.lookup_values(id),  -- Hierarchical values
    is_default      BIT NOT NULL DEFAULT 0,
    is_system       BIT NOT NULL DEFAULT 1,     -- System values cannot be deleted
    is_active       BIT NOT NULL DEFAULT 1,
    created_by      BIGINT NOT NULL,
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT NULL,
    updated_at      DATETIME2 NULL,
    CONSTRAINT UQ_lookup_value_code UNIQUE (category_id, value_code)
);

CREATE INDEX IX_lookup_values_category
    ON platform.lookup_values(category_id, display_order);
```

---

## 3. Standard Lookup Categories

### 3.1 Cross-Module Categories

| category_code | name_en | Module | Values |
|---|---|---|---|
| `priority_level` | Priority Level | Cross-module | CRITICAL, HIGH, MEDIUM, LOW |
| `data_classification` | Data Classification | Cross-module | PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED, TOP_SECRET |
| `workflow_status` | Workflow Status | Cross-module | DRAFT, PENDING_REVIEW, IN_PROGRESS, PENDING_APPROVAL, APPROVED, REJECTED, COMPLETED, CANCELLED |
| `approval_status` | Approval Status | Cross-module | PENDING, APPROVED, REJECTED, RETURNED |
| `document_status` | Document Status | Cross-module | DRAFT, UNDER_REVIEW, APPROVED, PUBLISHED, ARCHIVED |

### 3.2 Risk Module Categories

| category_code | name_en | Values |
|---|---|---|
| `risk_level` | Risk Level | VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH, CRITICAL |
| `risk_likelihood` | Risk Likelihood | RARE, UNLIKELY, POSSIBLE, LIKELY, ALMOST_CERTAIN |
| `risk_impact` | Risk Impact | INSIGNIFICANT, MINOR, MODERATE, MAJOR, CATASTROPHIC |
| `risk_status` | Risk Status | IDENTIFIED, OPEN, UNDER_ASSESSMENT, MITIGATED, ACCEPTED, CLOSED, ESCALATED |
| `risk_type` | Risk Type | STRATEGIC, OPERATIONAL, FINANCIAL, COMPLIANCE, CYBER, REPUTATIONAL |
| `risk_treatment_strategy` | Treatment Strategy | ACCEPT, MITIGATE, TRANSFER, AVOID |
| `risk_treatment_status` | Treatment Status | PLANNED, IN_PROGRESS, COMPLETED, OVERDUE, CANCELLED |
| `risk_source` | Risk Source | ERM, CYBER, BCM, PRIVACY, COMPLIANCE |

### 3.3 Cyber Security Categories

| category_code | name_en | Values |
|---|---|---|
| `incident_severity` | Incident Severity | CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL |
| `incident_status` | Incident Status | REPORTED, TRIAGED, INVESTIGATING, CONTAINMENT, ERADICATION, RECOVERY, CLOSED, FALSE_POSITIVE |
| `incident_type` | Incident Type | DATA_BREACH, MALWARE, UNAUTHORIZED_ACCESS, DOS, PHISHING, INSIDER_THREAT, PHYSICAL, OTHER |
| `asset_type` | Asset Type | HARDWARE, SOFTWARE, NETWORK, DATA, PERSONNEL, FACILITY, SERVICE |
| `asset_criticality` | Asset Criticality | CRITICAL, HIGH, MEDIUM, LOW |
| `vulnerability_severity` | Vulnerability Severity | CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL |

### 3.4 BCM Categories

| category_code | name_en | Values |
|---|---|---|
| `bcm_plan_type` | BCM Plan Type | BCP, DRP, CRISIS_MGMT, EMERGENCY_RESPONSE |
| `bcm_plan_status` | BCM Plan Status | DRAFT, UNDER_REVIEW, APPROVED, ACTIVE, EXPIRED, ARCHIVED |
| `bcm_exercise_type` | Exercise Type | TABLETOP, WALKTHROUGH, SIMULATION, FULL, NOTIFICATION |
| `bcm_exercise_status` | Exercise Status | PLANNED, IN_PROGRESS, COMPLETED, CANCELLED, POSTPONED |
| `impact_level` | Impact Level | NONE, LOW, MEDIUM, HIGH, CRITICAL |
| `dependency_type` | Dependency Type | INTERNAL, EXTERNAL, IT_SYSTEM, VENDOR, FACILITY |
| `bcm_incident_status` | BCM Incident Status | DETECTED, ACTIVATED, RESPONDING, RECOVERING, RESOLVED, CLOSED |

### 3.5 Privacy Categories

| category_code | name_en | Values |
|---|---|---|
| `personal_data_type` | Personal Data Type | BASIC_IDENTITY, FINANCIAL, HEALTH, BIOMETRIC, GENETIC, LOCATION, ONLINE_IDENTIFIER, BEHAVIORAL |
| `legal_basis` | Legal Basis | CONSENT, CONTRACT, LEGAL_OBLIGATION, VITAL_INTEREST, PUBLIC_INTEREST, LEGITIMATE_INTEREST |
| `breach_type` | Breach Type | CONFIDENTIALITY, INTEGRITY, AVAILABILITY |
| `breach_status` | Breach Status | DETECTED, INVESTIGATING, NOTIFYING, REMEDIATION, CLOSED |
| `consent_status` | Consent Status | GIVEN, WITHDRAWN, EXPIRED, PENDING |
| `assessment_type` | Privacy Assessment Type | PIA, DPIA |
| `data_subject_category` | Data Subject Category | EMPLOYEE, CUSTOMER, VENDOR, VISITOR, MINOR, PUBLIC |

### 3.6 Compliance Categories

| category_code | name_en | Values |
|---|---|---|
| `compliance_status` | Compliance Status | COMPLIANT, PARTIALLY_COMPLIANT, NON_COMPLIANT, NOT_APPLICABLE, NOT_ASSESSED |
| `assessment_status` | Assessment Status | PLANNED, IN_PROGRESS, UNDER_REVIEW, COMPLETED, CANCELLED |
| `finding_severity` | Finding Severity | CRITICAL, HIGH, MEDIUM, LOW, OBSERVATION |
| `finding_status` | Finding Status | OPEN, IN_REMEDIATION, REMEDIATED, VERIFIED_CLOSED, ACCEPTED, OVERDUE |
| `evidence_status` | Evidence Status | PENDING, ACCEPTED, REJECTED, EXPIRED |
| `remediation_status` | Remediation Status | PLANNED, IN_PROGRESS, COMPLETED, OVERDUE, CANCELLED |

### 3.7 Control Categories

| category_code | name_en | Values |
|---|---|---|
| `control_type` | Control Type | PREVENTIVE, DETECTIVE, CORRECTIVE, DETERRENT, COMPENSATING |
| `control_effectiveness` | Control Effectiveness | EFFECTIVE, PARTIALLY_EFFECTIVE, INEFFECTIVE, NOT_TESTED |
| `control_implementation` | Implementation Status | IMPLEMENTED, PARTIALLY_IMPLEMENTED, PLANNED, NOT_IMPLEMENTED |
| `control_nature` | Control Nature | MANUAL, AUTOMATED, SEMI_AUTOMATED |
| `review_frequency` | Review Frequency | MONTHLY, QUARTERLY, SEMI_ANNUAL, ANNUAL, AD_HOC |

---

## 4. Lookup Value Definitions (Detailed Examples)

### 4.1 Risk Likelihood Scale

| value_code | display_en | display_ar | numeric_value | color_code | display_order |
|---|---|---|---|---|---|
| RARE | Rare | نادر | 1 | #22C55E | 1 |
| UNLIKELY | Unlikely | غير محتمل | 2 | #84CC16 | 2 |
| POSSIBLE | Possible | ممكن | 3 | #EAB308 | 3 |
| LIKELY | Likely | محتمل | 4 | #F97316 | 4 |
| ALMOST_CERTAIN | Almost Certain | شبه مؤكد | 5 | #EF4444 | 5 |

### 4.2 Risk Impact Scale

| value_code | display_en | display_ar | numeric_value | color_code | display_order |
|---|---|---|---|---|---|
| INSIGNIFICANT | Insignificant | ضئيل | 1 | #22C55E | 1 |
| MINOR | Minor | طفيف | 2 | #84CC16 | 2 |
| MODERATE | Moderate | متوسط | 3 | #EAB308 | 3 |
| MAJOR | Major | جسيم | 4 | #F97316 | 4 |
| CATASTROPHIC | Catastrophic | كارثي | 5 | #EF4444 | 5 |

### 4.3 Risk Level (Derived from Likelihood x Impact)

| value_code | display_en | display_ar | numeric_value | color_code | display_order |
|---|---|---|---|---|---|
| VERY_LOW | Very Low | منخفض جدا | 1 | #22C55E | 1 |
| LOW | Low | منخفض | 2 | #84CC16 | 2 |
| MEDIUM | Medium | متوسط | 3 | #EAB308 | 3 |
| HIGH | High | مرتفع | 4 | #F97316 | 4 |
| VERY_HIGH | Very High | مرتفع جدا | 5 | #EF4444 | 5 |
| CRITICAL | Critical | حرج | 6 | #DC2626 | 6 |

---

## 5. Framework Reference Data

### 5.1 Control Frameworks Table

```sql
CREATE TABLE platform.control_frameworks (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    code            VARCHAR(50) NOT NULL,
    name_en         NVARCHAR(300) NOT NULL,
    name_ar         NVARCHAR(300) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    issuing_body_en NVARCHAR(200) NOT NULL,
    issuing_body_ar NVARCHAR(200) NOT NULL,
    description_en  NVARCHAR(MAX) NULL,
    description_ar  NVARCHAR(MAX) NULL,
    effective_date  DATE NULL,
    is_active       BIT NOT NULL DEFAULT 1,
    created_by      BIGINT NOT NULL,
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT NULL,
    updated_at      DATETIME2 NULL,
    CONSTRAINT UQ_framework_code_version UNIQUE (code, version)
);
```

### 5.2 Pre-Loaded Frameworks

| code | name_en | version | issuing_body | Domain Count | Control Count |
|---|---|---|---|---|---|
| NCA_ECC | Essential Cybersecurity Controls | 1:2018 | National Cybersecurity Authority (NCA) | 5 | 114 |
| ISO_27001 | ISO/IEC 27001:2022 Information Security | 2022 | ISO/IEC | 4 | 93 |
| ISO_22301 | ISO 22301:2019 Business Continuity | 2019 | ISO | 10 | ~40 |
| ISO_31000 | ISO 31000:2018 Risk Management | 2018 | ISO | 6 | N/A (principles) |
| PDPL | Personal Data Protection Law | 2021 | Saudi Data & AI Authority (SDAIA) | 9 | ~45 |
| NCA_CSCC | Cloud Security Controls | 1:2020 | National Cybersecurity Authority (NCA) | 4 | 56 |
| NCA_DCC | Data Cybersecurity Controls | 1:2022 | National Cybersecurity Authority (NCA) | 4 | 60 |

### 5.3 NCA ECC Domain Structure

| domain_code | name_en | name_ar | display_order |
|---|---|---|---|
| ECC-1 | Cybersecurity Governance | حوكمة الأمن السيبراني | 1 |
| ECC-2 | Cybersecurity Defense | تعزيز الأمن السيبراني | 2 |
| ECC-3 | Cybersecurity Resilience | صمود الأمن السيبراني | 3 |
| ECC-4 | Third-Party Cybersecurity | الأمن السيبراني للأطراف الثالثة | 4 |
| ECC-5 | Industrial Control Systems | أنظمة التحكم الصناعي | 5 |

### 5.4 ISO 27001:2022 Annex A Structure

| domain_code | name_en | display_order |
|---|---|---|
| A.5 | Organizational Controls | 1 |
| A.6 | People Controls | 2 |
| A.7 | Physical Controls | 3 |
| A.8 | Technological Controls | 4 |

### 5.5 Framework Domain Table

```sql
CREATE TABLE platform.control_framework_domains (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    framework_id    BIGINT NOT NULL REFERENCES platform.control_frameworks(id),
    code            VARCHAR(50) NOT NULL,
    name_en         NVARCHAR(300) NOT NULL,
    name_ar         NVARCHAR(300) NOT NULL,
    description_en  NVARCHAR(MAX) NULL,
    description_ar  NVARCHAR(MAX) NULL,
    parent_domain_id BIGINT NULL REFERENCES platform.control_framework_domains(id),
    display_order   INT NOT NULL DEFAULT 0,
    is_active       BIT NOT NULL DEFAULT 1,
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_framework_domain_code UNIQUE (framework_id, code)
);
```

---

## 6. Geographic Reference Data

### 6.1 Countries Table

```sql
CREATE TABLE platform.countries (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    iso_code_2      CHAR(2) NOT NULL,           -- ISO 3166-1 alpha-2
    iso_code_3      CHAR(3) NOT NULL,           -- ISO 3166-1 alpha-3
    name_en         NVARCHAR(200) NOT NULL,
    name_ar         NVARCHAR(200) NOT NULL,
    dialing_code    VARCHAR(10) NULL,
    is_gcc          BIT NOT NULL DEFAULT 0,     -- Gulf Cooperation Council member
    is_active       BIT NOT NULL DEFAULT 1,
    display_order   INT NOT NULL DEFAULT 0,
    CONSTRAINT UQ_country_iso2 UNIQUE (iso_code_2),
    CONSTRAINT UQ_country_iso3 UNIQUE (iso_code_3)
);
```

### 6.2 Regions/Provinces Table (Saudi Focus)

```sql
CREATE TABLE platform.regions (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    country_id      INT NOT NULL REFERENCES platform.countries(id),
    code            VARCHAR(20) NOT NULL,
    name_en         NVARCHAR(200) NOT NULL,
    name_ar         NVARCHAR(200) NOT NULL,
    region_type     VARCHAR(50) NOT NULL,       -- PROVINCE, EMIRATE, STATE, etc.
    is_active       BIT NOT NULL DEFAULT 1,
    display_order   INT NOT NULL DEFAULT 0,
    CONSTRAINT UQ_region_code UNIQUE (country_id, code)
);
```

### 6.3 Saudi Provinces Seed Data

| code | name_en | name_ar | display_order |
|---|---|---|---|
| SA-01 | Riyadh | الرياض | 1 |
| SA-02 | Makkah | مكة المكرمة | 2 |
| SA-03 | Madinah | المدينة المنورة | 3 |
| SA-04 | Eastern Province | المنطقة الشرقية | 4 |
| SA-05 | Al Qassim | القصيم | 5 |
| SA-06 | Asir | عسير | 6 |
| SA-07 | Tabuk | تبوك | 7 |
| SA-08 | Hail | حائل | 8 |
| SA-09 | Northern Borders | الحدود الشمالية | 9 |
| SA-10 | Jazan | جازان | 10 |
| SA-11 | Najran | نجران | 11 |
| SA-12 | Al Bahah | الباحة | 12 |
| SA-13 | Al Jawf | الجوف | 13 |

---

## 7. Industry Classifications

### 7.1 Industry Table

```sql
CREATE TABLE platform.industries (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    code            VARCHAR(20) NOT NULL,
    name_en         NVARCHAR(200) NOT NULL,
    name_ar         NVARCHAR(200) NOT NULL,
    sector_en       NVARCHAR(200) NULL,         -- Parent sector grouping
    sector_ar       NVARCHAR(200) NULL,
    is_regulated    BIT NOT NULL DEFAULT 0,     -- Subject to specific regulatory requirements
    is_active       BIT NOT NULL DEFAULT 1,
    display_order   INT NOT NULL DEFAULT 0,
    CONSTRAINT UQ_industry_code UNIQUE (code)
);
```

### 7.2 Saudi Key Industries Seed Data

| code | name_en | name_ar | is_regulated |
|---|---|---|---|
| GOV | Government | حكومي | 1 |
| FINANCE | Financial Services | الخدمات المالية | 1 |
| TELECOM | Telecommunications | الاتصالات | 1 |
| HEALTH | Healthcare | الرعاية الصحية | 1 |
| ENERGY | Energy & Utilities | الطاقة والمرافق | 1 |
| DEFENSE | Defense & Military | الدفاع والعسكرية | 1 |
| EDUCATION | Education | التعليم | 0 |
| RETAIL | Retail & Commerce | التجارة والتجزئة | 0 |
| MANUFACTURING | Manufacturing | التصنيع | 0 |
| TRANSPORT | Transportation & Logistics | النقل والخدمات اللوجستية | 1 |
| REAL_ESTATE | Real Estate & Construction | العقارات والإنشاءات | 0 |
| TECH | Technology & IT | التقنية وتقنية المعلومات | 0 |
| MEDIA | Media & Entertainment | الإعلام والترفيه | 0 |
| TOURISM | Tourism & Hospitality | السياحة والضيافة | 0 |

---

## 8. Currency Reference Data

```sql
CREATE TABLE platform.currencies (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    code            CHAR(3) NOT NULL,           -- ISO 4217
    name_en         NVARCHAR(100) NOT NULL,
    name_ar         NVARCHAR(100) NOT NULL,
    symbol          NVARCHAR(10) NOT NULL,
    decimal_places  INT NOT NULL DEFAULT 2,
    is_active       BIT NOT NULL DEFAULT 1,
    CONSTRAINT UQ_currency_code UNIQUE (code)
);
```

Key currencies: SAR (Saudi Riyal), USD, EUR, GBP, AED.

---

## 9. Seeding Strategy

### 9.1 Flyway Migration Scripts

Master data is populated via Flyway versioned migration scripts. The naming convention ensures deterministic ordering:

```
V1.0.0__create_schemas.sql
V1.1.0__create_platform_tables.sql
V1.2.0__create_iam_tables.sql
...
V2.0.0__seed_lookup_categories.sql
V2.0.1__seed_risk_lookups.sql
V2.0.2__seed_cyber_lookups.sql
V2.0.3__seed_bcm_lookups.sql
V2.0.4__seed_privacy_lookups.sql
V2.0.5__seed_compliance_lookups.sql
V2.0.6__seed_control_lookups.sql
V2.1.0__seed_countries.sql
V2.1.1__seed_saudi_regions.sql
V2.1.2__seed_gcc_countries.sql
V2.2.0__seed_industries.sql
V2.2.1__seed_currencies.sql
V2.3.0__seed_framework_nca_ecc.sql
V2.3.1__seed_framework_iso27001.sql
V2.3.2__seed_framework_iso22301.sql
V2.3.3__seed_framework_iso31000.sql
V2.3.4__seed_framework_pdpl.sql
V2.3.5__seed_framework_nca_cscc.sql
V2.3.6__seed_framework_nca_dcc.sql
V2.4.0__seed_system_roles.sql
V2.4.1__seed_permissions.sql
V2.4.2__seed_role_permissions.sql
```

### 9.2 Seeding Script Pattern

```sql
-- V2.0.1__seed_risk_lookups.sql
-- Idempotent: uses MERGE to allow re-running

-- Risk Likelihood category
MERGE INTO platform.lookup_categories AS target
USING (VALUES ('risk_likelihood', N'Risk Likelihood', N'احتمالية المخاطر', N'Probability scale for risk assessment', N'مقياس الاحتمالية لتقييم المخاطر', 'risk', 1))
    AS source (category_code, name_en, name_ar, description_en, description_ar, module_code, is_system)
ON target.category_code = source.category_code
WHEN NOT MATCHED THEN
    INSERT (category_code, name_en, name_ar, description_en, description_ar, module_code, is_system, created_by)
    VALUES (source.category_code, source.name_en, source.name_ar, source.description_en, source.description_ar, source.module_code, source.is_system, 1);

-- Risk Likelihood values
DECLARE @cat_id BIGINT = (SELECT id FROM platform.lookup_categories WHERE category_code = 'risk_likelihood');

MERGE INTO platform.lookup_values AS target
USING (VALUES
    (@cat_id, 'RARE',           N'Rare',            N'نادر',        1, '#22C55E', 1.0, 1),
    (@cat_id, 'UNLIKELY',       N'Unlikely',        N'غير محتمل',   2, '#84CC16', 2.0, 2),
    (@cat_id, 'POSSIBLE',       N'Possible',        N'ممكن',        3, '#EAB308', 3.0, 3),
    (@cat_id, 'LIKELY',         N'Likely',          N'محتمل',       4, '#F97316', 4.0, 4),
    (@cat_id, 'ALMOST_CERTAIN', N'Almost Certain',  N'شبه مؤكد',    5, '#EF4444', 5.0, 5)
) AS source (category_id, value_code, display_en, display_ar, display_order, color_code, numeric_value, sort_order)
ON target.category_id = source.category_id AND target.value_code = source.value_code
WHEN NOT MATCHED THEN
    INSERT (category_id, value_code, display_en, display_ar, display_order, color_code, numeric_value, is_system, created_by)
    VALUES (source.category_id, source.value_code, source.display_en, source.display_ar, source.display_order, source.color_code, source.numeric_value, 1, 1);
```

### 9.3 Seeding Rules

1. All seed scripts use MERGE for idempotency (safe to re-run).
2. System-created records use `created_by = 1` (system user).
3. `is_system = 1` for all seed data to prevent accidental deletion.
4. Framework control data is loaded from structured CSV/JSON files via bulk insert scripts.
5. Each framework gets its own migration script for independent versioning.
6. Seed data includes both English and Arabic translations.
7. Numeric values on lookup items enable scoring and calculation in risk/compliance engines.
