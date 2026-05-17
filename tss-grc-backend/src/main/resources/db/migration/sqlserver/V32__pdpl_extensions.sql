-- V32 — PDPL extensions (Saudi GRC prompt §3.5 + §7.4).
--
-- Three privacy capabilities the platform was missing per the gap
-- analysis (priority items 6, 7, 8):
--
--   1. Cross-border transfer workflow (PDPL Art. 29)
--      Tracks each transfer of personal data outside KSA, with the
--      transfer mechanism, justification, residency check, expiry
--      date, and approval state.
--
--   2. Disposal certificate workflow (PDPL Art. 18 retention)
--      Captures evidence that personal data was deleted / anonymised
--      / archived per the retention rule — with disposal method,
--      effective date, owner, and a sign-off chain (eligible for
--      digital signature via the §28 module shipped in commit 032f5ff).
--
--   3. Privacy notice templating (PDPL Art. 12)
--      Versioned bilingual privacy notices linked to ROPA processing
--      activities. Supports DRAFT → PUBLISHED → ARCHIVED lifecycle so
--      that a new version supersedes the previous one without
--      destroying the audit trail.

SET QUOTED_IDENTIFIER ON;
GO

-- ─── 1. Cross-border transfers ─────────────────────────────────────
IF OBJECT_ID(N'privacy.cross_border_transfers', N'U') IS NULL
BEGIN
    CREATE TABLE privacy.cross_border_transfers (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,

        -- Linked ROPA processing activity. Free-form code (e.g. "ROPA-2026-007")
        -- rather than FK to keep ROPA schema flexible during continued evolution.
        ropa_code                NVARCHAR(64)  NOT NULL,
        ropa_title_en            NVARCHAR(255) NULL,

        -- Source + destination.
        source_country           NVARCHAR(3)   NOT NULL DEFAULT N'SAU',
        destination_country      NVARCHAR(3)   NOT NULL,
        destination_org_name     NVARCHAR(255) NOT NULL,
        destination_org_role     NVARCHAR(50)  NULL, -- CONTROLLER | PROCESSOR | SUB_PROCESSOR

        -- Data scope (CSV of categories, e.g. "CONTACT,FINANCIAL").
        data_categories          NVARCHAR(MAX) NOT NULL,
        data_subjects_count_est  BIGINT        NULL,

        -- PDPL Art. 29 mechanism.
        -- ADEQUACY        — destination on the SDAIA adequacy list
        -- SCC             — standard contractual clauses signed
        -- BCR             — binding corporate rules
        -- EXPLICIT_CONSENT
        -- CONTRACT_NECESSITY
        -- VITAL_INTEREST
        -- PUBLIC_INTEREST
        transfer_mechanism       NVARCHAR(30)  NOT NULL,
        safeguards_en            NVARCHAR(MAX) NULL,
        safeguards_ar            NVARCHAR(MAX) NULL,
        justification_en         NVARCHAR(MAX) NOT NULL,
        justification_ar         NVARCHAR(MAX) NULL,

        residency_check_at       DATETIME2(3)  NULL,
        residency_check_by       NVARCHAR(255) NULL,
        residency_check_outcome  NVARCHAR(20)  NULL, -- PASS | PASS_WITH_CONDITIONS | FAIL

        -- Lifecycle: REQUESTED → ASSESSED → APPROVED | REJECTED → EXPIRED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'REQUESTED',
        rejection_reason         NVARCHAR(500) NULL,
        approved_at              DATETIME2(3)  NULL,
        approved_by              BIGINT        NULL,
        approver_name            NVARCHAR(255) NULL,
        effective_from           DATETIME2(3)  NULL,
        expiry_date              DATETIME2(3)  NULL,

        -- BaseEntity audit fields.
        external_id              NVARCHAR(64)  NULL,
        is_deleted               SMALLINT      NOT NULL DEFAULT 0,
        is_active                SMALLINT      NOT NULL DEFAULT 1,
        deleted_at               DATETIME2(3)  NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by               BIGINT        NULL,
        row_version              BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_cbt_status CHECK (status IN (N'REQUESTED', N'ASSESSED', N'APPROVED', N'REJECTED', N'EXPIRED')),
        CONSTRAINT CK_cbt_mechanism CHECK (transfer_mechanism IN
            (N'ADEQUACY', N'SCC', N'BCR', N'EXPLICIT_CONSENT',
             N'CONTRACT_NECESSITY', N'VITAL_INTEREST', N'PUBLIC_INTEREST')),
        CONSTRAINT CK_cbt_rejection CHECK (status <> N'REJECTED' OR rejection_reason IS NOT NULL)
    );

    CREATE INDEX IX_cbt_org_status ON privacy.cross_border_transfers (organisation_id, status) WHERE is_deleted = 0;
    CREATE INDEX IX_cbt_ropa       ON privacy.cross_border_transfers (organisation_id, ropa_code);
END;
GO

-- ─── 2. Disposal certificates ──────────────────────────────────────
IF OBJECT_ID(N'privacy.disposal_certificates', N'U') IS NULL
BEGIN
    CREATE TABLE privacy.disposal_certificates (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,

        -- What was disposed of. Either a ROPA activity (whole register entry
        -- retired) or a specific data asset / system. Free-form ref.
        related_entity_type      NVARCHAR(50)  NOT NULL, -- ropa | data_asset | system
        related_entity_id        NVARCHAR(120) NOT NULL, -- code or numeric id as string
        related_entity_label     NVARCHAR(255) NULL,

        retention_rule_ref       NVARCHAR(120) NULL, -- which retention rule triggered it
        scope_description_en     NVARCHAR(MAX) NOT NULL,
        scope_description_ar     NVARCHAR(MAX) NULL,
        records_count_est        BIGINT        NULL,

        -- DELETE       — irreversibly removed
        -- ANONYMIZE    — keyed identifiers stripped; data retained for analytics
        -- ARCHIVE      — moved to cold storage with restricted access
        -- DESTROY      — physical media destruction
        disposal_method          NVARCHAR(20)  NOT NULL,
        method_details_en        NVARCHAR(MAX) NULL,

        -- Lifecycle: SCHEDULED → IN_PROGRESS → COMPLETED → VERIFIED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'SCHEDULED',
        scheduled_date           DATETIME2(3)  NULL,
        executed_at              DATETIME2(3)  NULL,
        executed_by              BIGINT        NULL,
        executor_name            NVARCHAR(255) NULL,
        verified_at              DATETIME2(3)  NULL,
        verified_by              BIGINT        NULL,
        verifier_name            NVARCHAR(255) NULL,

        -- Evidence link — points at the evidence module item that holds
        -- the actual disposal certificate document.
        evidence_ref             NVARCHAR(120) NULL,

        -- Optional digital-signature transaction id (from the §28 module).
        signature_tx_id          BIGINT        NULL,

        owner_role               NVARCHAR(100) NULL,
        owner_name               NVARCHAR(255) NULL,

        external_id              NVARCHAR(64)  NULL,
        is_deleted               SMALLINT      NOT NULL DEFAULT 0,
        is_active                SMALLINT      NOT NULL DEFAULT 1,
        deleted_at               DATETIME2(3)  NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by               BIGINT        NULL,
        row_version              BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_dc_status CHECK (status IN (N'SCHEDULED', N'IN_PROGRESS', N'COMPLETED', N'VERIFIED', N'CANCELLED')),
        CONSTRAINT CK_dc_method CHECK (disposal_method IN (N'DELETE', N'ANONYMIZE', N'ARCHIVE', N'DESTROY'))
    );

    CREATE INDEX IX_dc_org_status ON privacy.disposal_certificates (organisation_id, status) WHERE is_deleted = 0;
END;
GO

-- ─── 3. Privacy notices (versioned, bilingual) ─────────────────────
IF OBJECT_ID(N'privacy.privacy_notices', N'U') IS NULL
BEGIN
    CREATE TABLE privacy.privacy_notices (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,

        notice_code              NVARCHAR(64)  NOT NULL, -- e.g. "PN-EMPLOYEE-V2"
        version                  INT           NOT NULL DEFAULT 1,

        title_en                 NVARCHAR(255) NOT NULL,
        title_ar                 NVARCHAR(255) NOT NULL,

        purpose_en               NVARCHAR(MAX) NOT NULL,
        purpose_ar               NVARCHAR(MAX) NULL,

        data_categories          NVARCHAR(MAX) NULL,
        recipients               NVARCHAR(MAX) NULL,
        retention_period_en      NVARCHAR(MAX) NULL,
        retention_period_ar      NVARCHAR(MAX) NULL,
        dpo_contact              NVARCHAR(255) NULL,
        lawful_basis             NVARCHAR(50)  NULL, -- CONSENT | CONTRACT | LEGAL_OBLIGATION | etc.

        body_en                  NVARCHAR(MAX) NOT NULL,
        body_ar                  NVARCHAR(MAX) NULL,

        -- CSV of ROPA codes this notice covers, e.g. "ROPA-001,ROPA-002".
        linked_ropa_codes        NVARCHAR(MAX) NULL,

        -- Lifecycle: DRAFT → PUBLISHED → ARCHIVED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
        effective_from           DATETIME2(3)  NULL,
        effective_to             DATETIME2(3)  NULL,
        published_at             DATETIME2(3)  NULL,
        published_by             BIGINT        NULL,
        archived_at              DATETIME2(3)  NULL,
        archived_by              BIGINT        NULL,
        archive_reason_en        NVARCHAR(500) NULL,

        -- For traceability — the previous notice this version supersedes.
        supersedes_id            BIGINT        NULL,

        external_id              NVARCHAR(64)  NULL,
        is_deleted               SMALLINT      NOT NULL DEFAULT 0,
        is_active                SMALLINT      NOT NULL DEFAULT 1,
        deleted_at               DATETIME2(3)  NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by               BIGINT        NULL,
        row_version              BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_pn_status CHECK (status IN (N'DRAFT', N'PUBLISHED', N'ARCHIVED'))
    );

    CREATE UNIQUE INDEX UX_pn_org_code_version ON privacy.privacy_notices (organisation_id, notice_code, version) WHERE is_deleted = 0;
    CREATE INDEX IX_pn_org_status ON privacy.privacy_notices (organisation_id, status) WHERE is_deleted = 0;
END;
GO
