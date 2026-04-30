-- ============================================================================
-- TSS GRC Platform - Attachments Table
-- Schema: platform
-- Purpose: Centralized file attachment storage metadata
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('platform.attachments', 'U') IS NOT NULL
    DROP TABLE platform.attachments;
GO

CREATE TABLE platform.attachments
(
    id                  BIGINT          IDENTITY(1,1)   NOT NULL,
    external_id         UNIQUEIDENTIFIER                NOT NULL    DEFAULT NEWID(),
    entity_type         NVARCHAR(100)                   NOT NULL,
    entity_id           BIGINT                          NOT NULL,
    file_name           NVARCHAR(500)                   NOT NULL,
    original_file_name  NVARCHAR(500)                   NOT NULL,
    file_extension      NVARCHAR(20)                    NULL,
    mime_type           NVARCHAR(200)                   NULL,
    file_size_bytes     BIGINT                          NULL,
    storage_type        NVARCHAR(50)                    NOT NULL    DEFAULT N'LOCAL',
    storage_path        NVARCHAR(1000)                  NOT NULL,
    checksum_sha256     NVARCHAR(128)                   NULL,
    description         NVARCHAR(500)                   NULL,
    uploaded_by         BIGINT                          NULL,
    uploaded_at         DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    virus_scan_status   NVARCHAR(50)                    NOT NULL    DEFAULT N'PENDING',
    virus_scanned_at    DATETIME2                       NULL,
    is_deleted          BIT                             NOT NULL    DEFAULT 0,
    deleted_at          DATETIME2                       NULL,
    deleted_by          BIGINT                          NULL,

    CONSTRAINT PK_attachments PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_attachments_external_id UNIQUE (external_id),

    CONSTRAINT CK_attachments_storage_type CHECK (storage_type IN (
        N'LOCAL', N'NAS', N'AZURE_BLOB', N'S3'
    )),

    CONSTRAINT CK_attachments_virus_scan CHECK (virus_scan_status IN (
        N'PENDING', N'CLEAN', N'INFECTED', N'ERROR'
    )),

    CONSTRAINT FK_attachments_uploaded_by FOREIGN KEY (uploaded_by)
        REFERENCES iam.users (id)
);
GO

PRINT 'Table platform.attachments created successfully.';
GO
