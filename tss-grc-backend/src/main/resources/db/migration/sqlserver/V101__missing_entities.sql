-- V101: missing entities from prompt §8 (#39).
--
-- Four entities the prior data model never materialised but several
-- features point at via soft FKs:
--   - platform.departments        — org chart unit, referenced by users.department_id
--   - privacy.consent_records     — PDPL Article 5 consent ledger
--   - cyber.cloud_services        — cloud service inventory (NCA CSCC §1.1)
--   - platform.issues             — generic issue tracker for findings / observations
--
-- All four follow the platform conventions:
--   * BIGINT IDENTITY id
--   * organisation_id NOT NULL  (tenant discriminator)
--   * is_deleted BIT NOT NULL DEFAULT 0 + @Where soft delete
--   * created_at / updated_at / created_by / updated_by / deleted_at / deleted_by
--   * row_version ROWVERSION for optimistic locking (matches BaseEntity)
--
-- Bilingual fields use the existing *_en / *_ar pattern.

-- ─── Departments (org chart) ────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'departments' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.departments (
        id                BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id   BIGINT          NOT NULL,
        external_id       UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        code              NVARCHAR(50)    NOT NULL,   -- short slug, unique per tenant
        name_en           NVARCHAR(255)   NOT NULL,
        name_ar           NVARCHAR(255)   NULL,
        parent_id         BIGINT          NULL,       -- self-FK; root departments have NULL
        head_user_id      BIGINT          NULL,       -- soft FK to iam.users
        cost_centre       NVARCHAR(50)    NULL,
        description       NVARCHAR(1000)  NULL,

        status            NVARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
        -- ACTIVE | INACTIVE | ARCHIVED

        created_at        DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at        DATETIME2       NULL,
        created_by        BIGINT          NULL,
        updated_by        BIGINT          NULL,
        is_deleted        BIT             NOT NULL DEFAULT 0,
        deleted_at        DATETIME2       NULL,
        deleted_by        BIGINT          NULL,
        row_version       ROWVERSION
    );
    CREATE UNIQUE INDEX UX_departments_org_code  ON platform.departments(organisation_id, code) WHERE is_deleted = 0;
    CREATE INDEX        IX_departments_parent    ON platform.departments(parent_id) WHERE is_deleted = 0;
    CREATE INDEX        IX_departments_head_user ON platform.departments(head_user_id) WHERE is_deleted = 0;
END
GO

-- ─── Consent records (PDPL Art. 5) ──────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'privacy')
    EXEC('CREATE SCHEMA privacy AUTHORIZATION dbo');
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'consent_records' AND schema_id = SCHEMA_ID('privacy'))
BEGIN
    CREATE TABLE privacy.consent_records (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        data_subject_ref    NVARCHAR(255)   NOT NULL,   -- hashed/pseudonymous subject id
        purpose_code        NVARCHAR(100)   NOT NULL,
        purpose_description NVARCHAR(1000)  NULL,
        legal_basis         NVARCHAR(50)    NOT NULL DEFAULT 'CONSENT',
        -- CONSENT | LEGITIMATE_INTEREST | CONTRACT | LEGAL_OBLIGATION | VITAL_INTEREST | PUBLIC_TASK

        granted_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        granted_via         NVARCHAR(50)    NOT NULL DEFAULT 'WEB_FORM',
        -- WEB_FORM | PAPER | IN_PERSON | API | EMAIL | OTHER
        valid_until         DATETIME2       NULL,       -- NULL = until withdrawn
        withdrawn_at        DATETIME2       NULL,
        withdrawn_reason    NVARCHAR(500)   NULL,

        evidence_id         BIGINT          NULL,       -- soft FK to evidence.evidence_items
        version             NVARCHAR(20)    NOT NULL DEFAULT 'v1',
        language            NVARCHAR(10)    NOT NULL DEFAULT 'ar',

        status              NVARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
        -- ACTIVE | EXPIRED | WITHDRAWN | SUPERSEDED

        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2       NULL,
        created_by          BIGINT          NULL,
        updated_by          BIGINT          NULL,
        is_deleted          BIT             NOT NULL DEFAULT 0,
        deleted_at          DATETIME2       NULL,
        deleted_by          BIGINT          NULL,
        row_version         ROWVERSION
    );
    CREATE INDEX IX_consent_records_org_subject  ON privacy.consent_records(organisation_id, data_subject_ref) WHERE is_deleted = 0;
    CREATE INDEX IX_consent_records_org_purpose  ON privacy.consent_records(organisation_id, purpose_code)     WHERE is_deleted = 0;
    CREATE INDEX IX_consent_records_org_status   ON privacy.consent_records(organisation_id, status)           WHERE is_deleted = 0;
END
GO

-- ─── Cloud services (NCA CSCC §1.1 inventory) ──────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'cyber')
    EXEC('CREATE SCHEMA cyber AUTHORIZATION dbo');
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'cloud_services' AND schema_id = SCHEMA_ID('cyber'))
BEGIN
    CREATE TABLE cyber.cloud_services (
        id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id       BIGINT          NOT NULL,
        external_id           UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        service_name          NVARCHAR(255)   NOT NULL,
        provider              NVARCHAR(255)   NOT NULL,
        -- e.g. AWS, Azure, GCP, Oracle Cloud, Tabby, STC Cloud, etc.
        service_model         NVARCHAR(20)    NOT NULL DEFAULT 'SAAS',
        -- IAAS | PAAS | SAAS | FAAS | DBAAS
        deployment_model      NVARCHAR(20)    NOT NULL DEFAULT 'PUBLIC',
        -- PUBLIC | PRIVATE | HYBRID | COMMUNITY

        data_classification   NVARCHAR(30)    NULL,
        -- PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED | TOP_SECRET (NCA / NDMO scale)
        residency_region      NVARCHAR(100)   NULL,     -- KSA | GCC | EU | US | GLOBAL
        kingdom_data_resident BIT             NOT NULL DEFAULT 0,

        cscc_tier             NVARCHAR(10)    NULL,     -- NCA CSCC tier (1..3)
        contract_id           BIGINT          NULL,     -- soft FK to vendor contract
        owner_user_id         BIGINT          NULL,     -- soft FK to iam.users
        criticality           NVARCHAR(20)    NOT NULL DEFAULT 'MEDIUM',
        -- LOW | MEDIUM | HIGH | CRITICAL

        sso_enabled           BIT             NOT NULL DEFAULT 0,
        mfa_enforced          BIT             NOT NULL DEFAULT 0,
        encryption_at_rest    BIT             NOT NULL DEFAULT 0,
        encryption_in_transit BIT             NOT NULL DEFAULT 1,

        status                NVARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
        -- ACTIVE | UNDER_REVIEW | DEPROVISIONED | ARCHIVED
        last_review_date      DATE            NULL,
        next_review_date      DATE            NULL,

        notes                 NVARCHAR(1000)  NULL,

        created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at            DATETIME2       NULL,
        created_by            BIGINT          NULL,
        updated_by            BIGINT          NULL,
        is_deleted            BIT             NOT NULL DEFAULT 0,
        deleted_at            DATETIME2       NULL,
        deleted_by            BIGINT          NULL,
        row_version           ROWVERSION
    );
    CREATE UNIQUE INDEX UX_cloud_services_org_name   ON cyber.cloud_services(organisation_id, service_name, provider) WHERE is_deleted = 0;
    CREATE INDEX        IX_cloud_services_org_status ON cyber.cloud_services(organisation_id, status) WHERE is_deleted = 0;
    CREATE INDEX        IX_cloud_services_org_crit   ON cyber.cloud_services(organisation_id, criticality) WHERE is_deleted = 0;
END
GO

-- ─── Issues (generic finding tracker) ──────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'issues' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.issues (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT          NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        issue_code          NVARCHAR(50)    NOT NULL,   -- e.g. ISS-2026-000123

        title_en            NVARCHAR(500)   NOT NULL,
        title_ar            NVARCHAR(500)   NULL,
        description         NVARCHAR(MAX)   NULL,

        source_module       NVARCHAR(50)    NOT NULL,
        -- audit | cyber | bcm | risk | compliance | scm | tprm | privacy | manual
        source_entity_type  NVARCHAR(100)   NULL,
        source_entity_id    BIGINT          NULL,

        severity            NVARCHAR(20)    NOT NULL DEFAULT 'MEDIUM',
        -- LOW | MEDIUM | HIGH | CRITICAL
        priority            NVARCHAR(20)    NOT NULL DEFAULT 'NORMAL',
        -- LOW | NORMAL | HIGH | URGENT

        assignee_user_id    BIGINT          NULL,
        assignee_role       NVARCHAR(100)   NULL,
        due_date            DATE            NULL,

        status              NVARCHAR(20)    NOT NULL DEFAULT 'OPEN',
        -- OPEN | IN_PROGRESS | BLOCKED | RESOLVED | CLOSED | CANCELLED
        resolution          NVARCHAR(20)    NULL,
        -- FIXED | WONT_FIX | DUPLICATE | NOT_REPRODUCIBLE | DEFERRED
        resolution_notes    NVARCHAR(2000)  NULL,
        resolved_at         DATETIME2       NULL,
        resolved_by         BIGINT          NULL,
        closed_at           DATETIME2       NULL,

        capa_id             BIGINT          NULL,   -- soft FK to capa.capa_tasks
        evidence_id         BIGINT          NULL,   -- soft FK to evidence.evidence_items

        created_at          DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2       NULL,
        created_by          BIGINT          NULL,
        updated_by          BIGINT          NULL,
        is_deleted          BIT             NOT NULL DEFAULT 0,
        deleted_at          DATETIME2       NULL,
        deleted_by          BIGINT          NULL,
        row_version         ROWVERSION
    );
    CREATE UNIQUE INDEX UX_issues_org_code    ON platform.issues(organisation_id, issue_code) WHERE is_deleted = 0;
    CREATE INDEX        IX_issues_org_status  ON platform.issues(organisation_id, status)     WHERE is_deleted = 0;
    CREATE INDEX        IX_issues_org_assignee ON platform.issues(organisation_id, assignee_user_id) WHERE is_deleted = 0;
    CREATE INDEX        IX_issues_org_source  ON platform.issues(organisation_id, source_module, source_entity_type, source_entity_id) WHERE is_deleted = 0;
END
GO
