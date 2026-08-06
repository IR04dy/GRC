# Logical Database Design
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose
Defines the logical database design — normalized table structures, cardinality rules, and entity relationships mapped to SQL Server 2019 schemas. This is the bridge between the conceptual model and the physical implementation.

---

## Normalization Standard

**Target: Third Normal Form (3NF)** for all transactional tables.
**Exceptions:** Reporting views and audit tables may be intentionally denormalized for query performance.

---

## Schema: platform

### platform.organizations
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT IDENTITY | PK |
| external_id | UNIQUEIDENTIFIER | UK, DEFAULT NEWID() |
| code | NVARCHAR(50) | UK, NOT NULL |
| name_en | NVARCHAR(200) | NOT NULL |
| name_ar | NVARCHAR(200) | NULL |
| type | NVARCHAR(50) | CHECK IN ('TENANT','SUBSIDIARY','BRANCH') |
| parent_org_id | BIGINT | FK → organizations(id), NULL |
| country | NVARCHAR(100) | DEFAULT 'Saudi Arabia' |
| city | NVARCHAR(100) | NULL |
| is_active | BIT | DEFAULT 1 |
| + audit columns | | created_by, created_at, updated_by, updated_at, is_deleted, deleted_at, deleted_by, row_version |

### platform.business_units
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT IDENTITY | PK |
| external_id | UNIQUEIDENTIFIER | UK |
| org_id | BIGINT | FK → organizations(id), NOT NULL |
| code | NVARCHAR(50) | NOT NULL |
| name_en | NVARCHAR(200) | NOT NULL |
| name_ar | NVARCHAR(200) | NULL |
| type | NVARCHAR(50) | CHECK IN ('DIVISION','DEPARTMENT','SECTION','TEAM') |
| parent_bu_id | BIGINT | FK → business_units(id), NULL (self-referencing) |
| head_user_id | BIGINT | FK → iam.users(id), NULL |
| level | INT | NOT NULL DEFAULT 1 |
| is_active | BIT | DEFAULT 1 |
| + audit columns | | |

### platform.controls
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT IDENTITY | PK |
| external_id | UNIQUEIDENTIFIER | UK |
| org_id | BIGINT | FK → organizations(id) |
| code | NVARCHAR(50) | NOT NULL |
| title_en | NVARCHAR(500) | NOT NULL |
| title_ar | NVARCHAR(500) | NULL |
| description_en | NVARCHAR(MAX) | NULL |
| description_ar | NVARCHAR(MAX) | NULL |
| control_type | NVARCHAR(50) | CHECK IN ('PREVENTIVE','DETECTIVE','CORRECTIVE','DIRECTIVE') |
| control_category | NVARCHAR(100) | NULL |
| implementation_status | NVARCHAR(50) | CHECK IN ('NOT_IMPLEMENTED','PARTIALLY_IMPLEMENTED','FULLY_IMPLEMENTED') |
| effectiveness | NVARCHAR(50) | CHECK IN ('INEFFECTIVE','PARTIALLY_EFFECTIVE','EFFECTIVE','HIGHLY_EFFECTIVE') |
| owner_user_id | BIGINT | FK → iam.users(id), NULL |
| review_frequency | NVARCHAR(50) | NULL |
| last_reviewed_at | DATETIME2 | NULL |
| next_review_at | DATETIME2 | NULL |
| is_active | BIT | DEFAULT 1 |
| + audit columns, soft delete, rowversion | | |

---

## Schema: iam

### iam.users
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT IDENTITY | PK |
| external_id | UNIQUEIDENTIFIER | UK, DEFAULT NEWID() |
| username | NVARCHAR(100) | UK, NOT NULL |
| email | NVARCHAR(200) | UK, NOT NULL |
| first_name_en | NVARCHAR(100) | NULL |
| last_name_en | NVARCHAR(100) | NULL |
| first_name_ar | NVARCHAR(100) | NULL |
| last_name_ar | NVARCHAR(100) | NULL |
| display_name_en | NVARCHAR(200) | NULL |
| display_name_ar | NVARCHAR(200) | NULL |
| password_hash | NVARCHAR(500) | NULL |
| org_id | BIGINT | FK → platform.organizations(id) |
| bu_id | BIGINT | FK → platform.business_units(id), NULL |
| job_title_en | NVARCHAR(200) | NULL |
| is_active | BIT | DEFAULT 1 |
| is_locked | BIT | DEFAULT 0 |
| failed_login_attempts | INT | DEFAULT 0 |
| last_login_at | DATETIME2 | NULL |
| preferred_locale | NVARCHAR(10) | DEFAULT 'en' |
| mfa_enabled | BIT | DEFAULT 0 |
| + audit columns, soft delete, rowversion | | |

### iam.roles
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT IDENTITY | PK |
| code | NVARCHAR(100) | UK, NOT NULL |
| name_en | NVARCHAR(200) | NOT NULL |
| name_ar | NVARCHAR(200) | NULL |
| module_code | NVARCHAR(50) | NULL (NULL = platform-wide role) |
| is_system | BIT | DEFAULT 0 (system roles cannot be deleted) |
| is_active | BIT | DEFAULT 1 |
| + audit columns, soft delete | | |

### iam.permissions
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT IDENTITY | PK |
| code | NVARCHAR(200) | UK, NOT NULL |
| name_en | NVARCHAR(200) | NOT NULL |
| module_code | NVARCHAR(50) | NOT NULL |
| resource_type | NVARCHAR(100) | NOT NULL |
| action | NVARCHAR(50) | CHECK IN ('CREATE','READ','UPDATE','DELETE','APPROVE','REJECT','EXPORT','IMPORT','CONFIGURE') |
| is_active | BIT | DEFAULT 1 |
| + audit columns | | |

---

## Schema: risk

### risk.risks
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT IDENTITY | PK |
| external_id | UNIQUEIDENTIFIER | UK |
| org_id | BIGINT | FK → platform.organizations(id) |
| bu_id | BIGINT | FK → platform.business_units(id), NULL |
| code | NVARCHAR(50) | NOT NULL |
| title_en | NVARCHAR(500) | NOT NULL |
| title_ar | NVARCHAR(500) | NULL |
| description_en | NVARCHAR(MAX) | NULL |
| category_id | BIGINT | FK → risk.risk_categories(id), NULL |
| risk_type | NVARCHAR(50) | NOT NULL |
| source_module | NVARCHAR(50) | CHECK IN ('ERM','CYBER','BCM','PRIVACY','COMPLIANCE') DEFAULT 'ERM' |
| source_entity_id | BIGINT | NULL (ID of source record in source module) |
| likelihood | INT | CHECK 1-5 |
| impact | INT | CHECK 1-5 |
| inherent_score | DECIMAL(5,2) | NULL |
| residual_likelihood | INT | CHECK 1-5, NULL |
| residual_impact | INT | CHECK 1-5, NULL |
| residual_score | DECIMAL(5,2) | NULL |
| risk_level | NVARCHAR(50) | CHECK IN ('CRITICAL','HIGH','MEDIUM','LOW') |
| status | NVARCHAR(50) | CHECK IN ('DRAFT','OPEN','UNDER_TREATMENT','CLOSED','ACCEPTED') |
| owner_user_id | BIGINT | FK → iam.users(id), NULL |
| treatment_strategy | NVARCHAR(50) | CHECK IN ('ACCEPT','MITIGATE','TRANSFER','AVOID','SHARE'), NULL |
| review_date | DATE | NULL |
| + audit columns, soft delete, rowversion | | |

---

## Cardinality Rules

| Relationship | Cardinality |
|-------------|-------------|
| Organization → Business Units | 1 : N |
| Organization → Users | 1 : N |
| Business Unit → Users | 1 : N |
| User → User Roles | 1 : N |
| Role → User Roles | 1 : N |
| Role → Role Permissions | 1 : N |
| Permission → Role Permissions | 1 : N |
| Organization → Risks | 1 : N |
| Risk → Risk Assessments | 1 : N |
| Risk → Risk Treatments | 1 : N |
| Risk ↔ Controls | M : N (via risk.risk_controls) |
| Compliance Assessment → Assessment Controls | 1 : N |
| Assessment Control → Findings | 1 : N |
| Finding → Gaps | 1 : N |
| Gap → Remediation Plans | 1 : N |
| Compliance Assessment → Evidence Items | 1 : N |
| Evidence Item → Attachments | 1 : 1 |
| Any Entity → Attachments | 1 : N (polymorphic) |
| Any Entity → Audit Logs | 1 : N (polymorphic; no FK) |
| Workflow Definition → Workflow Steps | 1 : N |
| Workflow Definition → Workflow Instances | 1 : N |
| Workflow Instance → Workflow Instance Steps | 1 : N |
