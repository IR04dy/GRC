-- File metadata — Sprint 6 production hardening.
--
-- Tracks every file uploaded through `lib/fileUpload.ts` adapter. The
-- bytes live in S3-compatible storage at `storage_ref`; this table
-- records ownership, residency, virus-scan outcome, retention.

CREATE TABLE file_metadata (
    id                 BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    organisation_id    BIGINT               NOT NULL,
    attached_to        NVARCHAR(200)        NOT NULL,
    filename           NVARCHAR(255)        NOT NULL,
    mime               NVARCHAR(100)        NOT NULL,
    size_bytes         BIGINT               NOT NULL,
    sha256             NVARCHAR(64)         NOT NULL,
    storage_ref        NVARCHAR(500)        NULL,
    scan_status        NVARCHAR(32)         NOT NULL,
    scan_reason        NVARCHAR(500)        NULL,
    uploaded_at        NVARCHAR(50)         NOT NULL,
    uploaded_by        NVARCHAR(200)        NOT NULL,
    cr_number          NVARCHAR(32)         NULL,
    residency          NVARCHAR(16)         NOT NULL,
    retention_days     INT                  NOT NULL,
    is_deleted         SMALLINT             NOT NULL DEFAULT 0,
    deleted_at         DATETIME2            NULL,
    created_at         DATETIME2            NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by         NVARCHAR(100)        NULL,
    updated_at         DATETIME2            NULL,
    updated_by         NVARCHAR(100)        NULL,
    version            BIGINT               NOT NULL DEFAULT 0
);

-- Tenant + record lookup (most common query pattern).
CREATE INDEX idx_file_org_attached
    ON file_metadata (organisation_id, attached_to)
    WHERE is_deleted = 0;

-- Hash-based dedupe + tamper-detection lookup.
CREATE INDEX idx_file_org_sha
    ON file_metadata (organisation_id, sha256)
    WHERE is_deleted = 0;

CREATE INDEX idx_file_uploaded
    ON file_metadata (uploaded_at);
