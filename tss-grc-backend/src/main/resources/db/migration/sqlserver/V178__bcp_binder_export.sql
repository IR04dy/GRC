-- V178 — Slice 10-F: Offline BCP/DRP plan PDF binder generator.
--
-- SAMA BCF Annex D mandates that every critical-business-service BCP be
-- available in printable, off-line, RTL Arabic-supporting PDF so the
-- Crisis Room team can fall back to paper when the GRC platform itself
-- is offline (datacenter outage, network split). This migration adds
-- the bookkeeping table that tracks each generated binder export.
--
-- One table:
--
--   bcm.bcp_binder_export — one row per generated PDF binder, with the
--                           plan it was generated from, the language
--                           variant (EN | AR | BILINGUAL), the captured
--                           plan version_label, the storage file_path
--                           (under ${grc.storage.base-path}/binders/),
--                           generation timing + status + audit fields.
--
-- The PDF itself is NOT stored in the DB — it lives on disk under the
-- platform's configured storage base path. This row is the metadata
-- index over those files and the audit trail of who generated / down-
-- loaded / archived each one.
--
-- Status lifecycle (enforced in BcpBinderService):
--   GENERATING → READY → ARCHIVED
--   GENERATING → FAILED  (terminal — error_message captured)
--
-- All inserts in this migration are guarded by IF NOT EXISTS — the
-- migration is idempotent and safe to run on a DB that already has
-- the BCP plan baseline (V107+V109+V110+V111).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF SCHEMA_ID('bcm') IS NULL EXEC('CREATE SCHEMA bcm');
GO

-- ─── Binder export table ────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'bcp_binder_export' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.bcp_binder_export (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id             BIGINT          NOT NULL,
        external_id                 UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        bcp_plan_id                 BIGINT          NOT NULL,
        export_code                 NVARCHAR(64)    NOT NULL,        -- e.g. BCP-EXP-2026-001

        format                      VARCHAR(20)     NOT NULL DEFAULT 'PDF',  -- future: DOCX, ZIP
        language                    VARCHAR(10)     NOT NULL DEFAULT 'BILINGUAL', -- EN | AR | BILINGUAL

        version_label               NVARCHAR(64)    NULL,            -- captured from plan at generation time (e.g. "v3")
        watermark_text              NVARCHAR(255)   NOT NULL
            DEFAULT N'Controlled — Not for Distribution / محكوم — غير قابل للتوزيع',

        status                      VARCHAR(20)     NOT NULL DEFAULT 'GENERATING', -- GENERATING|READY|FAILED|ARCHIVED
        error_message               NVARCHAR(MAX)   NULL,

        generated_at                DATETIME2(3)    NOT NULL DEFAULT SYSUTCDATETIME(),
        generated_by_user_id        BIGINT          NULL,
        generated_by_name           NVARCHAR(255)   NULL,
        generation_duration_ms      INT             NULL,

        page_count                  INT             NULL,
        byte_size                   INT             NULL,
        file_path                   NVARCHAR(500)   NULL,             -- relative to grc.storage.base-path

        download_count              INT             NOT NULL DEFAULT 0,
        last_downloaded_at          DATETIME2(3)    NULL,
        last_downloaded_by_user_id  BIGINT          NULL,

        -- Audit + soft-delete + ROWVERSION
        created_at                  DATETIME2(3)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2(3)    NULL,
        created_by                  BIGINT          NULL,
        updated_by                  BIGINT          NULL,
        is_deleted                  BIT             NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2(3)    NULL,
        deleted_by                  BIGINT          NULL,
        row_version                 ROWVERSION      NOT NULL,

        CONSTRAINT UQ_bcp_binder_export_code UNIQUE (organisation_id, export_code),
        CONSTRAINT CK_bcp_binder_export_status
            CHECK (status IN ('GENERATING','READY','FAILED','ARCHIVED')),
        CONSTRAINT CK_bcp_binder_export_language
            CHECK (language IN ('EN','AR','BILINGUAL')),
        CONSTRAINT CK_bcp_binder_export_format
            CHECK (format IN ('PDF','DOCX','ZIP'))
    );
END
GO

-- Filtered index on (org_id, bcp_plan_id, generated_at) — supports the
-- "list binder versions for a plan, newest first" query that the UI
-- versions-tab fires on every plan open. Filtered to is_deleted=0 so the
-- index is smaller and matches the entity's @Where clause.
IF OBJECT_ID('bcm.bcp_binder_export') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_bcp_binder_export_plan_generated' AND object_id = OBJECT_ID('bcm.bcp_binder_export'))
    CREATE INDEX IX_bcp_binder_export_plan_generated
        ON bcm.bcp_binder_export(organisation_id, bcp_plan_id, generated_at DESC)
        WHERE is_deleted = 0;
GO

-- Secondary index on (org_id, status) for status-filtered admin views
-- (e.g. "list all FAILED generations to triage").
IF OBJECT_ID('bcm.bcp_binder_export') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_bcp_binder_export_status' AND object_id = OBJECT_ID('bcm.bcp_binder_export'))
    CREATE INDEX IX_bcp_binder_export_status
        ON bcm.bcp_binder_export(organisation_id, status)
        WHERE is_deleted = 0;
GO
