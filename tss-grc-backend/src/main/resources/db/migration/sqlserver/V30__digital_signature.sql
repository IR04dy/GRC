-- V30 — Digital Signature transactions (Saudi GRC prompt §28).
--
-- A digital signature transaction binds a signer to a specific record
-- (board pack approval, risk acceptance, BCP sign-off, supplier
-- attestation, NCA compliance attestation, etc.). Captured per-tenant
-- with full audit chain so the regulatory evidence package can prove
-- who approved what, when, and against which document hash.
--
-- Lifecycle: REQUESTED → SIGNED | REJECTED | EXPIRED
--   REQUESTED — created by initiator; waits for signer
--   SIGNED    — signer approved; document hash + signature certificate stored
--   REJECTED  — signer declined; rejection_reason mandatory
--   EXPIRED   — request not signed by expiry_date

-- Filtered indexes below need QUOTED_IDENTIFIER ON.
SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'signature')
    EXEC('CREATE SCHEMA signature');
GO

IF OBJECT_ID(N'signature.digital_signature_transactions', N'U') IS NULL
BEGIN
    CREATE TABLE signature.digital_signature_transactions (
        id                    BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id       BIGINT NOT NULL,

        -- What is being signed: free-form entity reference so any module
        -- (approvals, risk acceptance, board pack, supplier attestation,
        -- regulatory filing, evidence packet) can plug in without a
        -- foreign-key explosion.
        related_entity_type   NVARCHAR(50)  NOT NULL,
        related_entity_id     BIGINT        NOT NULL,
        related_entity_label  NVARCHAR(255) NULL,

        -- Signer (filled at REQUEST time so the inbox can route to them).
        signer_user_id        BIGINT        NULL,
        signer_name           NVARCHAR(255) NOT NULL,
        signer_email          NVARCHAR(255) NULL,
        signer_role           NVARCHAR(100) NULL,

        -- Initiator + provider metadata.
        initiator_user_id     BIGINT        NULL,
        initiator_name        NVARCHAR(255) NULL,
        signature_provider    NVARCHAR(50)  NOT NULL DEFAULT N'INTERNAL',
        purpose               NVARCHAR(500) NOT NULL,
        purpose_ar            NVARCHAR(500) NULL,

        -- Document binding — sha256 of the signed payload (the thing the
        -- signer attested to). Filled when state moves to SIGNED.
        signed_document_hash  NVARCHAR(64)  NULL,
        certificate_reference NVARCHAR(255) NULL,

        -- Lifecycle.
        status                NVARCHAR(20)  NOT NULL DEFAULT N'REQUESTED',
        rejection_reason      NVARCHAR(500) NULL,
        signature_timestamp   DATETIME2(3)  NULL,
        expiry_date           DATETIME2(3)  NULL,

        -- BaseEntity audit fields (matches other module tables).
        external_id           NVARCHAR(64)  NULL,
        is_deleted            SMALLINT      NOT NULL DEFAULT 0,
        is_active             SMALLINT      NOT NULL DEFAULT 1,
        deleted_at            DATETIME2(3)  NULL,
        deleted_by            BIGINT        NULL,
        created_at            DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by            BIGINT        NULL,
        updated_at            DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by            BIGINT        NULL,
        row_version           BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_dst_status CHECK (status IN (N'REQUESTED', N'SIGNED', N'REJECTED', N'EXPIRED')),
        CONSTRAINT CK_dst_rejection_reason CHECK (status <> N'REJECTED' OR rejection_reason IS NOT NULL),
        CONSTRAINT CK_dst_signed_hash CHECK (status <> N'SIGNED' OR signed_document_hash IS NOT NULL)
    );

    -- Indexes for the two common queries: "what's in my inbox" + "what's
    -- pending on this entity".
    CREATE INDEX IX_dst_org_status      ON signature.digital_signature_transactions (organisation_id, status) WHERE is_deleted = 0;
    CREATE INDEX IX_dst_signer_status   ON signature.digital_signature_transactions (signer_user_id, status)  WHERE is_deleted = 0;
    CREATE INDEX IX_dst_related_entity  ON signature.digital_signature_transactions (organisation_id, related_entity_type, related_entity_id);
END;
GO
