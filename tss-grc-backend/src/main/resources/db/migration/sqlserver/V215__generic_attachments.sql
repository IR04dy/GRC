-- Generic, typed file-attachment facility: any entity can own files of a given
-- doc_type (PLAN/REPORT/POLICY/CERTIFICATE/EVIDENCE/CONTRACT/...). Reused across
-- every register/form on the platform. Idempotent.
--
-- A bare platform.attachments table pre-existed in some environments; this creates
-- it on a fresh DB and otherwise adds the three typed columns (doc_type, title_en,
-- valid_to). Dev DB is Python-managed via setup_attachments_table.py.

IF OBJECT_ID('platform.attachments') IS NULL
BEGIN
    CREATE TABLE platform.attachments (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        organisation_id BIGINT NOT NULL,
        entity_type     NVARCHAR(100) NOT NULL,
        entity_id       BIGINT        NOT NULL,
        doc_type        NVARCHAR(30)  NOT NULL DEFAULT 'OTHER',
        title_en        NVARCHAR(300) NULL,
        file_name       NVARCHAR(300) NOT NULL,
        file_path       NVARCHAR(1000) NOT NULL,
        file_size_bytes BIGINT        NOT NULL,
        mime_type       NVARCHAR(200) NOT NULL,
        sha256_hash     NVARCHAR(64)  NOT NULL,
        valid_to        DATE          NULL,
        uploaded_by     BIGINT        NOT NULL,
        uploaded_at     DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        is_deleted      BIT           NOT NULL DEFAULT 0,
        deleted_by      BIGINT        NULL
    );
    CREATE INDEX ix_attachments_entity
        ON platform.attachments(organisation_id, entity_type, entity_id)
        WHERE is_deleted = 0;
END
ELSE
BEGIN
    IF COL_LENGTH('platform.attachments','doc_type') IS NULL
        ALTER TABLE platform.attachments ADD doc_type NVARCHAR(30) NOT NULL DEFAULT 'OTHER';
    IF COL_LENGTH('platform.attachments','title_en') IS NULL
        ALTER TABLE platform.attachments ADD title_en NVARCHAR(300) NULL;
    IF COL_LENGTH('platform.attachments','valid_to') IS NULL
        ALTER TABLE platform.attachments ADD valid_to DATE NULL;
END;
GO
