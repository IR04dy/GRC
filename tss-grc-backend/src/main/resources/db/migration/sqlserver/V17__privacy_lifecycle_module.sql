-- V17 — Privacy Lifecycle module backend tables.
-- Mirrors lib/privacyLifecycle.ts. PDPL Articles 4/22/25/29.

IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'privacy_lc')
    EXEC('CREATE SCHEMA privacy_lc');
GO

-- ─── ROPA — Article 22 ─────────────────────────────────────────────────────
CREATE TABLE privacy_lc.ropa_activities (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id       NVARCHAR(64)  NOT NULL,                   -- ROPA-2026-001
    organisation_id   BIGINT        NOT NULL,
    name_en           NVARCHAR(500) NOT NULL,
    name_ar           NVARCHAR(500) NOT NULL,
    lawful_basis      NVARCHAR(30)  NOT NULL,                   -- CONSENT/CONTRACT/LEGAL_OBLIGATION/VITAL_INTEREST/PUBLIC_INTEREST/LEGITIMATE_INTEREST
    purposes_en       NVARCHAR(MAX) NOT NULL,
    purposes_ar       NVARCHAR(MAX) NOT NULL,
    data_categories   NVARCHAR(MAX),                              -- comma-sep enum values
    subject_categories NVARCHAR(MAX),
    recipients_en     NVARCHAR(MAX),
    recipients_ar     NVARCHAR(MAX),
    storage_location  NVARCHAR(255),
    retention_period_en NVARCHAR(MAX),
    retention_period_ar NVARCHAR(MAX),
    security_measures   NVARCHAR(MAX),                           -- comma-sep
    owner             NVARCHAR(255),
    status            NVARCHAR(20)  NOT NULL DEFAULT 'DRAFT',   -- DRAFT/IN_REVIEW/APPROVED/SUPERSEDED
    created_at        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    approved_by       NVARCHAR(255),
    approved_at       DATETIME2,
    dpia_id           NVARCHAR(64),
    cbt_id            NVARCHAR(64),
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ropa_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_ropa_status ON privacy_lc.ropa_activities (organisation_id, status, is_deleted);
GO

-- ─── DPIA — Article 25 ─────────────────────────────────────────────────────
CREATE TABLE privacy_lc.dpia_assessments (
    id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id              NVARCHAR(64)  NOT NULL,
    organisation_id          BIGINT        NOT NULL,
    ropa_id                  NVARCHAR(64)  NOT NULL,
    status                   NVARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    likelihood               INT           NOT NULL DEFAULT 3,
    severity                 INT           NOT NULL DEFAULT 3,
    risk_score               INT           NOT NULL DEFAULT 9,
    is_high_risk             BIT           NOT NULL DEFAULT 0,
    triggers_en              NVARCHAR(MAX),                       -- JSON array
    triggers_ar              NVARCHAR(MAX),
    mitigations_en           NVARCHAR(MAX),
    mitigations_ar           NVARCHAR(MAX),
    residual_score           INT           NOT NULL DEFAULT 9,
    needs_sdaia_consultation BIT           NOT NULL DEFAULT 0,
    consultation_outcome_en  NVARCHAR(MAX),
    consultation_outcome_ar  NVARCHAR(MAX),
    dpo_signed_by            NVARCHAR(255),
    dpo_signed_at            DATETIME2,
    created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    is_deleted               SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_dpia_external UNIQUE (organisation_id, external_id)
);
GO

-- ─── Cross-border transfer — Article 29 ───────────────────────────────────
CREATE TABLE privacy_lc.cross_border_transfers (
    id                     BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id            NVARCHAR(64)  NOT NULL,
    organisation_id        BIGINT        NOT NULL,
    ropa_id                NVARCHAR(64)  NOT NULL,
    destination_country    NVARCHAR(100) NOT NULL,
    purpose_en             NVARCHAR(MAX) NOT NULL,
    purpose_ar             NVARCHAR(MAX) NOT NULL,
    adequacy               NVARCHAR(20)  NOT NULL,                 -- ADEQUATE/CONDITIONAL/NOT_ADEQUATE/PROHIBITED
    safeguard              NVARCHAR(30)  NOT NULL,                 -- NONE_NEEDED/STANDARD_CLAUSES/...
    safeguard_date         DATETIME2,
    pre_risk_score         INT           NOT NULL,
    post_risk_score        INT           NOT NULL,
    review_interval_months INT           NOT NULL DEFAULT 12,
    last_reviewed_at       DATETIME2,
    next_review_at         DATE,
    status                 NVARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    approved_by            NVARCHAR(255),
    approved_at            DATETIME2,
    created_at             DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    is_deleted             SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_cbt_external UNIQUE (organisation_id, external_id)
);
GO

-- ─── DSR — Article 4 (PRIMARY state-machine entity) ───────────────────────
CREATE TABLE privacy_lc.dsr_cases (
    id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id              NVARCHAR(64)  NOT NULL,                -- DSR-2026-042
    organisation_id          BIGINT        NOT NULL,
    [type]                   NVARCHAR(30)  NOT NULL,                -- ACCESS/RECTIFICATION/ERASURE/...
    subject_name             NVARCHAR(255) NOT NULL,
    national_id_last4        NVARCHAR(10)  NOT NULL,
    contact_email            NVARCHAR(255) NOT NULL,
    status                   NVARCHAR(30)  NOT NULL DEFAULT 'INTAKE',
    filed_at                 DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    identity_verified_at     DATETIME2,
    verification_method      NVARCHAR(30),                            -- NAFATH/ID_DOCUMENT/IN_PERSON/VIDEO_KYC
    data_collected_at        DATETIME2,
    data_summary_en          NVARCHAR(MAX),
    data_summary_ar          NVARCHAR(MAX),
    redaction_completed_at   DATETIME2,
    delivery_method          NVARCHAR(20),
    delivered_at             DATETIME2,
    sla_deadline             DATE          NOT NULL,
    extended                 BIT           NOT NULL DEFAULT 0,
    extension_reason_en      NVARCHAR(MAX),
    extension_reason_ar      NVARCHAR(MAX),
    rejection_reason_en      NVARCHAR(MAX),
    rejection_reason_ar      NVARCHAR(MAX),
    ropa_ids                 NVARCHAR(MAX),                          -- comma-sep ROPA externalids
    is_deleted               SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_dsr_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_dsr_org_status ON privacy_lc.dsr_cases (organisation_id, status, is_deleted);
CREATE INDEX ix_dsr_sla        ON privacy_lc.dsr_cases (organisation_id, sla_deadline, is_deleted);
GO
