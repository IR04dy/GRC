-- ============================================================
-- V8 — Evidence Management Module
-- Schema: evidence
-- Hierarchy: Framework → Control → SubControl → EvidenceItem
-- ============================================================

-- ── Schema ───────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'evidence')
    EXEC('CREATE SCHEMA evidence');
GO

-- ── evidence.frameworks (global reference, not tenant-scoped) ─
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'evidence' AND TABLE_NAME = 'frameworks')
BEGIN
    CREATE TABLE evidence.frameworks (
        id              BIGINT IDENTITY(1,1)  NOT NULL,
        external_id     UNIQUEIDENTIFIER      NOT NULL DEFAULT NEWID(),
        code            NVARCHAR(50)          NOT NULL,
        name_en         NVARCHAR(255)         NOT NULL,
        name_ar         NVARCHAR(255)         NULL,
        version         NVARCHAR(50)          NULL,
        description_en  NVARCHAR(MAX)         NULL,
        issuer          NVARCHAR(100)         NULL,
        is_active       BIT                   NOT NULL DEFAULT 1,
        created_at      DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_ev_frameworks PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT UQ_ev_frameworks_code UNIQUE (code)
    );
    PRINT 'Created evidence.frameworks';
END
GO

-- ── evidence.controls (global reference) ──────────────────────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'evidence' AND TABLE_NAME = 'controls')
BEGIN
    CREATE TABLE evidence.controls (
        id              BIGINT IDENTITY(1,1)  NOT NULL,
        external_id     UNIQUEIDENTIFIER      NOT NULL DEFAULT NEWID(),
        framework_id    BIGINT                NOT NULL,
        control_code    NVARCHAR(50)          NOT NULL,
        name_en         NVARCHAR(500)         NOT NULL,
        name_ar         NVARCHAR(500)         NULL,
        description_en  NVARCHAR(MAX)         NULL,
        is_active       BIT                   NOT NULL DEFAULT 1,
        created_at      DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_ev_controls PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT FK_ev_controls_framework FOREIGN KEY (framework_id) REFERENCES evidence.frameworks(id),
        CONSTRAINT UQ_ev_controls_code UNIQUE (framework_id, control_code)
    );
    PRINT 'Created evidence.controls';
END
GO

-- ── evidence.sub_controls (global reference — main anchor) ────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'evidence' AND TABLE_NAME = 'sub_controls')
BEGIN
    CREATE TABLE evidence.sub_controls (
        id                  BIGINT IDENTITY(1,1)  NOT NULL,
        external_id         UNIQUEIDENTIFIER      NOT NULL DEFAULT NEWID(),
        control_id          BIGINT                NOT NULL,
        sub_control_code    NVARCHAR(50)          NOT NULL,
        name_en             NVARCHAR(500)         NOT NULL,
        name_ar             NVARCHAR(500)         NULL,
        description_en      NVARCHAR(MAX)         NULL,
        evidence_guidance   NVARCHAR(MAX)         NULL,
        is_active           BIT                   NOT NULL DEFAULT 1,
        created_at          DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_ev_sub_controls PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT FK_ev_sub_controls_control FOREIGN KEY (control_id) REFERENCES evidence.controls(id),
        CONSTRAINT UQ_ev_sub_controls_code UNIQUE (control_id, sub_control_code)
    );
    PRINT 'Created evidence.sub_controls';
END
GO

-- ── evidence.evidence_items (tenant-scoped) ───────────────────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'evidence' AND TABLE_NAME = 'evidence_items')
BEGIN
    CREATE TABLE evidence.evidence_items (
        id                  BIGINT IDENTITY(1,1)  NOT NULL,
        external_id         UNIQUEIDENTIFIER      NOT NULL DEFAULT NEWID(),
        organisation_id     BIGINT                NOT NULL,
        sub_control_id      BIGINT                NULL,
        title_en            NVARCHAR(300)         NOT NULL,
        title_ar            NVARCHAR(300)         NULL,
        description_en      NVARCHAR(MAX)         NULL,
        evidence_type       NVARCHAR(50)          NOT NULL DEFAULT 'DOCUMENT',
        source_module       NVARCHAR(50)          NULL,
        source_entity_id    BIGINT                NULL,
        owner_id            BIGINT                NULL,
        version             NVARCHAR(50)          NULL,
        valid_from          DATE                  NULL,
        valid_to            DATE                  NULL,
        status              NVARCHAR(30)          NOT NULL DEFAULT 'DRAFT',
        file_name           NVARCHAR(500)         NULL,
        file_path           NVARCHAR(1000)        NULL,
        file_size           BIGINT                NULL,
        mime_type           NVARCHAR(200)         NULL,
        -- Audit (BaseEntity)
        created_by          BIGINT                NULL,
        created_at          DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by          BIGINT                NULL,
        updated_at          DATETIME2(7)          NULL,
        is_deleted          BIT                   NOT NULL DEFAULT 0,
        deleted_at          DATETIME2(7)          NULL,
        deleted_by          BIGINT                NULL,
        row_version         ROWVERSION            NOT NULL,
        CONSTRAINT PK_ev_items PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_ev_items_type CHECK (evidence_type IN ('POLICY','PROCEDURE','LOG','ASSESSMENT','TEST','AUDIT','SCREENSHOT','CONFIGURATION','CERTIFICATE','REPORT','DOCUMENT','OTHER')),
        CONSTRAINT CK_ev_items_status CHECK (status IN ('DRAFT','PENDING_REVIEW','APPROVED','REJECTED','EXPIRED','SUPERSEDED','ARCHIVED')),
        CONSTRAINT FK_ev_items_sub_control FOREIGN KEY (sub_control_id) REFERENCES evidence.sub_controls(id)
    );
    CREATE INDEX IX_ev_items_org ON evidence.evidence_items(organisation_id) WHERE is_deleted = 0;
    CREATE INDEX IX_ev_items_status ON evidence.evidence_items(organisation_id, status) WHERE is_deleted = 0;
    CREATE INDEX IX_ev_items_subctl ON evidence.evidence_items(sub_control_id) WHERE is_deleted = 0;
    PRINT 'Created evidence.evidence_items';
END
GO

-- ── evidence.evidence_requests (tenant-scoped workflow) ───────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'evidence' AND TABLE_NAME = 'evidence_requests')
BEGIN
    CREATE TABLE evidence.evidence_requests (
        id                  BIGINT IDENTITY(1,1)  NOT NULL,
        external_id         UNIQUEIDENTIFIER      NOT NULL DEFAULT NEWID(),
        organisation_id     BIGINT                NOT NULL,
        sub_control_id      BIGINT                NULL,
        title_en            NVARCHAR(300)         NOT NULL,
        title_ar            NVARCHAR(300)         NULL,
        description_en      NVARCHAR(MAX)         NULL,
        requested_by_id     BIGINT                NULL,
        assigned_to_id      BIGINT                NULL,
        due_date            DATE                  NULL,
        priority            NVARCHAR(20)          NOT NULL DEFAULT 'MEDIUM',
        status              NVARCHAR(30)          NOT NULL DEFAULT 'OPEN',
        fulfilled_by_id     BIGINT                NULL,
        fulfilled_at        DATETIME2(7)          NULL,
        notes               NVARCHAR(MAX)         NULL,
        -- Audit
        created_by          BIGINT                NULL,
        created_at          DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by          BIGINT                NULL,
        updated_at          DATETIME2(7)          NULL,
        is_deleted          BIT                   NOT NULL DEFAULT 0,
        deleted_at          DATETIME2(7)          NULL,
        deleted_by          BIGINT                NULL,
        row_version         ROWVERSION            NOT NULL,
        CONSTRAINT PK_ev_requests PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_ev_req_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
        CONSTRAINT CK_ev_req_status CHECK (status IN ('OPEN','IN_PROGRESS','SUBMITTED','APPROVED','REJECTED','CANCELLED','CLOSED')),
        CONSTRAINT FK_ev_requests_sub_control FOREIGN KEY (sub_control_id) REFERENCES evidence.sub_controls(id)
    );
    CREATE INDEX IX_ev_requests_org ON evidence.evidence_requests(organisation_id) WHERE is_deleted = 0;
    CREATE INDEX IX_ev_requests_status ON evidence.evidence_requests(organisation_id, status) WHERE is_deleted = 0;
    PRINT 'Created evidence.evidence_requests';
END
GO

-- ── evidence.evidence_reviews (tenant-scoped workflow) ────────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'evidence' AND TABLE_NAME = 'evidence_reviews')
BEGIN
    CREATE TABLE evidence.evidence_reviews (
        id                  BIGINT IDENTITY(1,1)  NOT NULL,
        external_id         UNIQUEIDENTIFIER      NOT NULL DEFAULT NEWID(),
        organisation_id     BIGINT                NOT NULL,
        evidence_id         BIGINT                NOT NULL,
        reviewer_id         BIGINT                NULL,
        review_date         DATE                  NULL,
        result              NVARCHAR(30)          NULL,
        notes_en            NVARCHAR(MAX)         NULL,
        status              NVARCHAR(20)          NOT NULL DEFAULT 'PENDING',
        -- Audit
        created_by          BIGINT                NULL,
        created_at          DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by          BIGINT                NULL,
        updated_at          DATETIME2(7)          NULL,
        is_deleted          BIT                   NOT NULL DEFAULT 0,
        deleted_at          DATETIME2(7)          NULL,
        deleted_by          BIGINT                NULL,
        row_version         ROWVERSION            NOT NULL,
        CONSTRAINT PK_ev_reviews PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_ev_review_result CHECK (result IN ('APPROVED','REJECTED','NEEDS_REVISION') OR result IS NULL),
        CONSTRAINT CK_ev_review_status CHECK (status IN ('PENDING','IN_REVIEW','COMPLETED','CANCELLED')),
        CONSTRAINT FK_ev_reviews_evidence FOREIGN KEY (evidence_id) REFERENCES evidence.evidence_items(id)
    );
    CREATE INDEX IX_ev_reviews_org ON evidence.evidence_reviews(organisation_id) WHERE is_deleted = 0;
    CREATE INDEX IX_ev_reviews_ev ON evidence.evidence_reviews(evidence_id) WHERE is_deleted = 0;
    PRINT 'Created evidence.evidence_reviews';
END
GO

-- ── evidence.evidence_links (cross-module linkage) ────────────
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES
               WHERE TABLE_SCHEMA = 'evidence' AND TABLE_NAME = 'evidence_links')
BEGIN
    CREATE TABLE evidence.evidence_links (
        id                  BIGINT IDENTITY(1,1)  NOT NULL,
        organisation_id     BIGINT                NOT NULL,
        evidence_id         BIGINT                NOT NULL,
        linked_module       NVARCHAR(50)          NOT NULL,
        linked_entity_type  NVARCHAR(100)         NULL,
        linked_entity_id    BIGINT                NULL,
        notes               NVARCHAR(500)         NULL,
        created_by          BIGINT                NULL,
        created_at          DATETIME2(7)          NOT NULL DEFAULT SYSUTCDATETIME(),
        is_deleted          BIT                   NOT NULL DEFAULT 0,
        deleted_at          DATETIME2(7)          NULL,
        CONSTRAINT PK_ev_links PRIMARY KEY CLUSTERED (id ASC),
        CONSTRAINT CK_ev_links_module CHECK (linked_module IN ('RISK','COMPLIANCE','CYBER','BCM','PRIVACY','TPRM','AUDIT','INCIDENT')),
        CONSTRAINT FK_ev_links_evidence FOREIGN KEY (evidence_id) REFERENCES evidence.evidence_items(id)
    );
    CREATE INDEX IX_ev_links_ev ON evidence.evidence_links(evidence_id) WHERE is_deleted = 0;
    PRINT 'Created evidence.evidence_links';
END
GO

PRINT 'V8 — Evidence module schema created successfully.';
GO
