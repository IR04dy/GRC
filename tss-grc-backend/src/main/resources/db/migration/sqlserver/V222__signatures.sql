-- V222 — in-house electronic sign-off (attestation) records.
-- Polymorphic by (entity_type, entity_id). Tamper-evident via payload_hash
-- (SHA-256 over signer + entity + intent + timestamp). Idempotent.

IF OBJECT_ID('platform.signatures', 'U') IS NULL
CREATE TABLE platform.signatures (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id BIGINT        NOT NULL,
    entity_type     NVARCHAR(100) NOT NULL,
    entity_id       BIGINT        NOT NULL,
    intent          NVARCHAR(30)  NOT NULL,
    statement_en    NVARCHAR(MAX) NULL,
    signer_id       BIGINT        NOT NULL,
    signer_name     NVARCHAR(200) NULL,
    signer_role     NVARCHAR(100) NULL,
    payload_hash    NVARCHAR(64)  NOT NULL,
    signed_at       DATETIME2     NOT NULL CONSTRAINT DF_signatures_signed_at DEFAULT SYSUTCDATETIME(),
    is_deleted      BIT           NOT NULL CONSTRAINT DF_signatures_is_deleted DEFAULT 0
);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_signatures_entity')
    CREATE INDEX IX_signatures_entity ON platform.signatures(organisation_id, entity_type, entity_id);
