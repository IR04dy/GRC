-- ============================================================================
-- TSS GRC Platform - Privacy & Personal Data Protection Module Tables
-- Schema: privacy
-- Compatible: SQL Server 2019 Express Edition
-- Compliance: Saudi PDPL, ISO 27701
-- ============================================================================

USE TSS_GRC;
GO

-- ============================================================================
-- privacy.data_inventory
-- ============================================================================
CREATE TABLE privacy.data_inventory (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    external_id             UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id                  BIGINT                  NOT NULL,
    bu_id                   BIGINT                  NULL,
    name_en                 NVARCHAR(300)           NOT NULL,
    name_ar                 NVARCHAR(300)           NULL,
    description_en          NVARCHAR(MAX)           NULL,
    data_type               NVARCHAR(100)           NOT NULL
        CONSTRAINT CK_di_type CHECK (data_type IN (
            'PERSONAL','SENSITIVE_PERSONAL','FINANCIAL','HEALTH',
            'BIOMETRIC','CHILDREN','CRIMINAL','OTHER')),
    classification          NVARCHAR(50)            NOT NULL DEFAULT 'CONFIDENTIAL'
        CONSTRAINT CK_di_class CHECK (classification IN (
            'TOP_SECRET','SECRET','CONFIDENTIAL','INTERNAL','PUBLIC')),
    storage_location        NVARCHAR(500)           NULL,
    storage_format          NVARCHAR(100)           NULL,
    retention_period        NVARCHAR(200)           NULL,
    legal_basis             NVARCHAR(100)           NULL
        CONSTRAINT CK_di_legal CHECK (legal_basis IN (
            'CONSENT','CONTRACT','LEGAL_OBLIGATION','VITAL_INTERESTS',
            'PUBLIC_TASK','LEGITIMATE_INTERESTS','NOT_DEFINED')),
    is_personal_data        BIT                     NOT NULL DEFAULT 0,
    is_sensitive_personal   BIT                     NOT NULL DEFAULT 0,
    is_cross_border         BIT                     NOT NULL DEFAULT 0,
    owner_user_id           BIGINT                  NULL,
    status                  NVARCHAR(50)            NOT NULL DEFAULT 'ACTIVE',
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                  NULL,
    updated_at              DATETIME2(7)            NULL,
    is_deleted              BIT                     NOT NULL DEFAULT 0,
    deleted_at              DATETIME2(7)            NULL,
    row_version             ROWVERSION              NOT NULL,
    CONSTRAINT PK_data_inventory PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_di_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_di_bu FOREIGN KEY (bu_id) REFERENCES platform.business_units(id)
);
GO

-- ============================================================================
-- privacy.processing_activities
-- ============================================================================
CREATE TABLE privacy.processing_activities (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    external_id             UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id                  BIGINT                  NOT NULL,
    data_inventory_id       BIGINT                  NULL,
    name_en                 NVARCHAR(300)           NOT NULL,
    name_ar                 NVARCHAR(300)           NULL,
    purpose_en              NVARCHAR(MAX)           NOT NULL,
    purpose_ar              NVARCHAR(MAX)           NULL,
    legal_basis             NVARCHAR(100)           NOT NULL,
    data_subjects_category  NVARCHAR(500)           NULL,
    data_categories         NVARCHAR(MAX)           NULL,
    recipients              NVARCHAR(MAX)           NULL,
    cross_border_transfer   BIT                     NOT NULL DEFAULT 0,
    transfer_countries      NVARCHAR(500)           NULL,
    transfer_safeguards     NVARCHAR(MAX)           NULL,
    retention_period        NVARCHAR(200)           NULL,
    security_measures       NVARCHAR(MAX)           NULL,
    automated_decision      BIT                     NOT NULL DEFAULT 0,
    profiling               BIT                     NOT NULL DEFAULT 0,
    status                  NVARCHAR(50)            NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT CK_pa_status CHECK (status IN ('ACTIVE','INACTIVE','UNDER_REVIEW')),
    owner_user_id           BIGINT                  NULL,
    last_reviewed_at        DATE                    NULL,
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                  NULL,
    updated_at              DATETIME2(7)            NULL,
    is_deleted              BIT                     NOT NULL DEFAULT 0,
    deleted_at              DATETIME2(7)            NULL,
    row_version             ROWVERSION              NOT NULL,
    CONSTRAINT PK_processing_activities PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_pa_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_pa_data_inv FOREIGN KEY (data_inventory_id) REFERENCES privacy.data_inventory(id)
);
GO

-- ============================================================================
-- privacy.privacy_assessments (PIA / DPIA)
-- ============================================================================
CREATE TABLE privacy.privacy_assessments (
    id                      BIGINT IDENTITY(1,1)    NOT NULL,
    external_id             UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id                  BIGINT                  NOT NULL,
    assessment_type         NVARCHAR(50)            NOT NULL
        CONSTRAINT CK_prv_type CHECK (assessment_type IN ('PIA','DPIA')),
    title                   NVARCHAR(500)           NOT NULL,
    description             NVARCHAR(MAX)           NULL,
    processing_activity_id  BIGINT                  NULL,
    assessor_user_id        BIGINT                  NULL,
    assessment_date         DATE                    NULL,
    status                  NVARCHAR(50)            NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT CK_prv_status CHECK (status IN (
            'DRAFT','IN_PROGRESS','COMPLETED','APPROVED','ARCHIVED')),
    overall_risk_level      NVARCHAR(50)            NULL
        CONSTRAINT CK_prv_risk CHECK (overall_risk_level IN ('HIGH','MEDIUM','LOW','ACCEPTABLE')),
    findings                NVARCHAR(MAX)           NULL,
    recommendations         NVARCHAR(MAX)           NULL,
    dpa_consultation_needed BIT                     NOT NULL DEFAULT 0,
    approved_by             BIGINT                  NULL,
    approved_at             DATETIME2(7)            NULL,
    next_review_date        DATE                    NULL,
    created_by              BIGINT                  NULL,
    created_at              DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by              BIGINT                  NULL,
    updated_at              DATETIME2(7)            NULL,
    is_deleted              BIT                     NOT NULL DEFAULT 0,
    deleted_at              DATETIME2(7)            NULL,
    row_version             ROWVERSION              NOT NULL,
    CONSTRAINT PK_privacy_assessments PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_prv_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_prv_activity FOREIGN KEY (processing_activity_id)
        REFERENCES privacy.processing_activities(id)
);
GO

-- ============================================================================
-- privacy.consent_records
-- ============================================================================
CREATE TABLE privacy.consent_records (
    id                          BIGINT IDENTITY(1,1)    NOT NULL,
    external_id                 UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id                      BIGINT                  NOT NULL,
    data_subject_identifier     NVARCHAR(500)           NOT NULL,
    purpose                     NVARCHAR(500)           NOT NULL,
    processing_activity_id      BIGINT                  NULL,
    consent_given               BIT                     NOT NULL DEFAULT 0,
    consent_date                DATETIME2(7)            NULL,
    consent_method              NVARCHAR(100)           NULL,
    withdrawal_date             DATETIME2(7)            NULL,
    withdrawal_reason           NVARCHAR(500)           NULL,
    evidence_attachment_id      BIGINT                  NULL,
    is_active                   BIT                     NOT NULL DEFAULT 1,
    created_by                  BIGINT                  NULL,
    created_at                  DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at                  DATETIME2(7)            NULL,
    CONSTRAINT PK_consent_records PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_cr_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_cr_activity FOREIGN KEY (processing_activity_id)
        REFERENCES privacy.processing_activities(id),
    CONSTRAINT FK_cr_evidence FOREIGN KEY (evidence_attachment_id)
        REFERENCES platform.attachments(id)
);
GO

-- ============================================================================
-- privacy.breach_records (Personal Data Breaches)
-- ============================================================================
CREATE TABLE privacy.breach_records (
    id                          BIGINT IDENTITY(1,1)    NOT NULL,
    external_id                 UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id                      BIGINT                  NOT NULL,
    code                        NVARCHAR(50)            NOT NULL,
    title                       NVARCHAR(500)           NOT NULL,
    description                 NVARCHAR(MAX)           NULL,
    breach_type                 NVARCHAR(100)           NOT NULL
        CONSTRAINT CK_br_type CHECK (breach_type IN (
            'CONFIDENTIALITY','INTEGRITY','AVAILABILITY','ALL_THREE')),
    personal_data_types         NVARCHAR(MAX)           NULL,
    data_subjects_affected      INT                     NULL,
    data_subjects_category      NVARCHAR(500)           NULL,
    detected_at                 DATETIME2(7)            NOT NULL,
    reported_internally_at      DATETIME2(7)            NULL,
    dpo_notified_at             DATETIME2(7)            NULL,
    authority_notification_req  BIT                     NOT NULL DEFAULT 0,
    notified_authority_at       DATETIME2(7)            NULL,
    authority_notified          NVARCHAR(200)           NULL,
    subject_notification_req    BIT                     NOT NULL DEFAULT 0,
    notified_subjects_at        DATETIME2(7)            NULL,
    status                      NVARCHAR(50)            NOT NULL DEFAULT 'OPEN'
        CONSTRAINT CK_br_status CHECK (status IN (
            'OPEN','UNDER_INVESTIGATION','RESOLVED','CLOSED')),
    containment_measures        NVARCHAR(MAX)           NULL,
    root_cause                  NVARCHAR(MAX)           NULL,
    corrective_actions          NVARCHAR(MAX)           NULL,
    severity                    NVARCHAR(50)            NULL
        CONSTRAINT CK_br_severity CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    -- Cross-module links
    linked_risk_id              BIGINT                  NULL,
    linked_cyber_incident_id    BIGINT                  NULL,
    -- Audit
    created_by                  BIGINT                  NULL,
    created_at                  DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by                  BIGINT                  NULL,
    updated_at                  DATETIME2(7)            NULL,
    is_deleted                  BIT                     NOT NULL DEFAULT 0,
    deleted_at                  DATETIME2(7)            NULL,
    row_version                 ROWVERSION              NOT NULL,
    CONSTRAINT PK_breach_records PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT UQ_br_code UNIQUE (org_id, code),
    CONSTRAINT FK_br_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_br_risk FOREIGN KEY (linked_risk_id) REFERENCES risk.risks(id),
    CONSTRAINT FK_br_incident FOREIGN KEY (linked_cyber_incident_id) REFERENCES cyber.incidents(id)
);
GO

-- ============================================================================
-- privacy.ropa_entries (Record of Processing Activities - PDPL requirement)
-- ============================================================================
CREATE TABLE privacy.ropa_entries (
    id                          BIGINT IDENTITY(1,1)    NOT NULL,
    external_id                 UNIQUEIDENTIFIER        NOT NULL DEFAULT NEWID(),
    org_id                      BIGINT                  NOT NULL,
    processing_activity_id      BIGINT                  NULL,
    controller_name             NVARCHAR(300)           NULL,
    controller_contact          NVARCHAR(500)           NULL,
    processor_name              NVARCHAR(300)           NULL,
    dpo_contact                 NVARCHAR(500)           NULL,
    purposes                    NVARCHAR(MAX)           NOT NULL,
    legal_basis                 NVARCHAR(500)           NULL,
    data_subject_categories     NVARCHAR(MAX)           NULL,
    data_categories             NVARCHAR(MAX)           NULL,
    recipients                  NVARCHAR(MAX)           NULL,
    third_country_transfers     NVARCHAR(MAX)           NULL,
    transfer_safeguards         NVARCHAR(MAX)           NULL,
    retention_timeframes        NVARCHAR(MAX)           NULL,
    security_measures           NVARCHAR(MAX)           NULL,
    last_reviewed_date          DATE                    NULL,
    reviewed_by                 BIGINT                  NULL,
    status                      NVARCHAR(50)            NOT NULL DEFAULT 'CURRENT'
        CONSTRAINT CK_ropa_status CHECK (status IN ('CURRENT','DRAFT','ARCHIVED')),
    created_by                  BIGINT                  NULL,
    created_at                  DATETIME2(7)            NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by                  BIGINT                  NULL,
    updated_at                  DATETIME2(7)            NULL,
    is_deleted                  BIT                     NOT NULL DEFAULT 0,
    deleted_at                  DATETIME2(7)            NULL,
    row_version                 ROWVERSION              NOT NULL,
    CONSTRAINT PK_ropa_entries PRIMARY KEY CLUSTERED (id ASC),
    CONSTRAINT FK_ropa_org FOREIGN KEY (org_id) REFERENCES platform.organizations(id),
    CONSTRAINT FK_ropa_activity FOREIGN KEY (processing_activity_id)
        REFERENCES privacy.processing_activities(id)
);
GO

PRINT 'Privacy module tables created successfully.';
GO
