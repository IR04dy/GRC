# 02 - Schema Design Strategy

> TSS GRC Platform | SQL Server 2019 | Document Version 1.0

## 1. Schema Inventory

| Schema | Purpose | Owner Role | Team |
|--------|---------|------------|------|
| `platform` | Shared business entities: organizations, business units, controls, attachments, lookups, notifications | `role_platform` | Platform team |
| `iam` | Identity and access management: users, roles, permissions, role assignments | `role_iam` | Security team |
| `admin` | Administration: modules, licenses, system and org-level configurations | `role_admin` | Platform team |
| `workflow` | Workflow engine: definitions, steps, instances, execution tracking | `role_workflow` | Platform team |
| `risk` | Enterprise Risk Management: risk registers, assessments, treatments, KRIs | `role_risk` | Risk module team |
| `cyber` | Cybersecurity Risk & Compliance: cyber assets, vulnerabilities, incidents, framework mappings | `role_cyber` | Cyber module team |
| `bcm` | Business Continuity Management: BIAs, BCP plans, exercises, incidents | `role_bcm` | BCM module team |
| `privacy` | Privacy & Personal Data Protection: DPIAs, processing activities, data subject requests, consent | `role_privacy` | Privacy module team |
| `compliance` | Compliance & Audit Management: assessments, findings, gaps, remediation plans, obligations | `role_compliance` | Compliance module team |
| `reporting` | Reporting & Analytics: report definitions, schedules, executions, dashboard configurations | `role_reporting` | Reporting team |
| `audit` | Audit trail: audit logs, system events | `role_audit` | Security team |

## 2. Schema Dependency Rules

### Allowed References (via Foreign Keys)

```
Module schemas ──FK──> platform schema (shared entities)
Module schemas ──FK──> iam schema (user references)
All schemas    ──FK──> platform.organizations (org_id)
workflow       ──FK──> any schema (polymorphic entity references)
audit          ──FK──> iam.users (actor reference)
reporting      ──FK──> platform/iam (metadata references)
```

### Prohibited References

```
risk ──X──> cyber       (NO direct FK between module schemas)
cyber ──X──> bcm        (NO direct FK between module schemas)
privacy ──X──> compliance (NO direct FK between module schemas)
```

### Cross-Module Data Access Pattern

When modules need to reference each other's data (e.g., a risk linked to a cyber incident), use **indirect references** through shared identifiers:

```sql
-- CORRECT: Store entity_type + entity_id for polymorphic references
-- risk.risks table
ALTER TABLE risk.risks ADD
    source_entity_type NVARCHAR(50) NULL,  -- e.g., 'CYBER_INCIDENT', 'COMPLIANCE_FINDING'
    source_entity_id   BIGINT NULL;

-- Application layer resolves the reference:
-- if source_entity_type = 'CYBER_INCIDENT' then query cyber.incidents WHERE id = source_entity_id
```

```sql
-- INCORRECT: Direct FK to another module schema
ALTER TABLE risk.risks ADD
    CONSTRAINT FK_risks_cyber_incidents
    FOREIGN KEY (cyber_incident_id) REFERENCES cyber.incidents(id);  -- PROHIBITED
```

### Rationale for Cross-Module Isolation

1. **Independent deployability** -- Module schemas can be versioned and migrated independently.
2. **Reduced coupling** -- Changes to one module's schema do not cascade FK constraint changes to other modules.
3. **Selective loading** -- Modules can be licensed/enabled independently without orphaned FK references.
4. **Team autonomy** -- Module teams manage their own schema without coordinating FK changes with other teams.

## 3. Schema Categories

### 3.1 Core Platform Schemas (Always Present)

These schemas exist in every TSS GRC installation regardless of licensed modules.

| Schema | Required By |
|--------|------------|
| `platform` | All modules depend on shared entities |
| `iam` | All modules depend on user/role references |
| `admin` | System configuration required for operation |
| `workflow` | Workflow engine shared across modules |
| `audit` | Audit logging mandatory for all operations |

### 3.2 Module Schemas (Licensed)

These schemas are created during installation but only populated/used when the corresponding module is licensed.

| Schema | Module License Key |
|--------|-------------------|
| `risk` | `MODULE_RISK` |
| `cyber` | `MODULE_CYBER` |
| `bcm` | `MODULE_BCM` |
| `privacy` | `MODULE_PRIVACY` |
| `compliance` | `MODULE_COMPLIANCE` |

### 3.3 Supporting Schemas

| Schema | Dependency |
|--------|-----------|
| `reporting` | Available for all licensed modules; aggregates data from active modules |

## 4. Shared Entity Design (platform Schema)

The `platform` schema holds entities referenced by multiple modules to avoid duplication.

### 4.1 Organizations and Structure

```
platform.organizations
  └── platform.business_units (hierarchical, self-referencing parent_id)
        └── linked to all module entities via org_id + business_unit_id
```

### 4.2 Control Framework (Shared)

Controls are a shared concept across Risk, Cyber, and Compliance modules.

```
platform.control_frameworks          -- e.g., ISO 27001, NIST CSF, NCA ECC
  └── platform.control_framework_domains  -- framework sections/domains
        └── platform.controls             -- individual controls
              └── mapped to risks, cyber assets, compliance findings
```

### 4.3 Lookup Values (Reference Data)

```
platform.lookup_categories    -- e.g., RISK_LIKELIHOOD, RISK_IMPACT, INCIDENT_SEVERITY
  └── platform.lookup_values  -- e.g., (HIGH, 4), (MEDIUM, 3), (LOW, 2)
```

All drop-down lists, status values, and enumerated types are stored as lookup values. Module schemas reference `lookup_values.id` via FK.

### 4.4 Notifications

```
platform.notification_templates  -- templates with placeholders
platform.notifications           -- generated notification instances
```

## 5. Schema Permission Model

Each schema role receives specific permissions. The application database user (`tss_grc_app`) is granted membership in the appropriate roles.

```sql
-- Platform schema: full CRUD
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::platform TO role_platform;

-- Audit schema: INSERT-only for application (no UPDATE/DELETE on audit_logs)
GRANT SELECT, INSERT ON SCHEMA::audit TO role_audit;
DENY UPDATE, DELETE ON audit.audit_logs TO role_audit;
DENY UPDATE, DELETE ON audit.system_events TO role_audit;

-- Cross-schema read access (modules can SELECT from platform and iam)
GRANT SELECT ON SCHEMA::platform TO role_risk;
GRANT SELECT ON SCHEMA::platform TO role_cyber;
GRANT SELECT ON SCHEMA::platform TO role_bcm;
GRANT SELECT ON SCHEMA::platform TO role_privacy;
GRANT SELECT ON SCHEMA::platform TO role_compliance;
GRANT SELECT ON SCHEMA::platform TO role_reporting;

GRANT SELECT ON SCHEMA::iam TO role_risk;
GRANT SELECT ON SCHEMA::iam TO role_cyber;
GRANT SELECT ON SCHEMA::iam TO role_bcm;
GRANT SELECT ON SCHEMA::iam TO role_privacy;
GRANT SELECT ON SCHEMA::iam TO role_compliance;
GRANT SELECT ON SCHEMA::iam TO role_reporting;

-- Application user role membership
ALTER ROLE role_platform ADD MEMBER tss_grc_app;
ALTER ROLE role_iam ADD MEMBER tss_grc_app;
ALTER ROLE role_admin ADD MEMBER tss_grc_app;
ALTER ROLE role_workflow ADD MEMBER tss_grc_app;
ALTER ROLE role_risk ADD MEMBER tss_grc_app;
ALTER ROLE role_cyber ADD MEMBER tss_grc_app;
ALTER ROLE role_bcm ADD MEMBER tss_grc_app;
ALTER ROLE role_privacy ADD MEMBER tss_grc_app;
ALTER ROLE role_compliance ADD MEMBER tss_grc_app;
ALTER ROLE role_reporting ADD MEMBER tss_grc_app;
ALTER ROLE role_audit ADD MEMBER tss_grc_app;
```

## 6. Schema Versioning

Each schema tracks its own version in the Flyway migration history. Migration files are prefixed by schema context:

```
V1.0.0__create_schemas.sql                 -- creates all schemas
V1.0.1__platform_create_organizations.sql  -- platform tables
V1.0.2__iam_create_users_roles.sql         -- iam tables
V1.0.3__risk_create_tables.sql             -- risk tables
V1.1.0__risk_add_kri_thresholds.sql        -- risk schema update
```

## 7. Entity Relationship Boundaries

```
┌─────────────────────────────────────────────────────────┐
│                    platform + iam                         │
│  organizations, business_units, users, roles, controls   │
│  lookup_values, attachments, notifications               │
└──────────┬──────────┬──────────┬──────────┬─────────────┘
           │ FK       │ FK       │ FK       │ FK
     ┌─────▼──┐ ┌────▼───┐ ┌───▼────┐ ┌───▼──────┐
     │  risk  │ │ cyber  │ │  bcm   │ │ privacy  │
     │ module │ │ module │ │ module │ │  module  │
     └────────┘ └────────┘ └────────┘ └──────────┘
           │          │          │          │
           └──────────┴──────────┴──────────┘
                      │ (no direct FKs between modules)
                      │ polymorphic entity_type + entity_id
                ┌─────▼──────┐
                │ compliance │
                │   module   │
                └────────────┘
                      │ FK
              ┌───────▼───────┐
              │   workflow    │ (polymorphic references to any entity)
              └───────────────┘
                      │
              ┌───────▼───────┐
              │    audit      │ (captures changes from all schemas)
              └───────────────┘
```
