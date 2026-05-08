-- Generic key/value persistence table — Sprint 6 production migration.
--
-- Backs the frontend `lib/persistence.ts` adapter. One row per logical
-- record from a frontend localStorage store; payload is JSON.
--
-- Tenant-scoped (organisation_id), soft-deleted (is_deleted), with
-- (organisation_id, store_key) as the primary lookup index.
--
-- Why generic instead of per-store: frontend stores evolve frequently
-- across sprints; locking each into its own table forces a Flyway
-- migration on every shape change. Audit / GDPR-style queries only
-- ever look up by storeKey + tenant, so a single table is sufficient.

CREATE TABLE persistence_row (
    id                 BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    organisation_id    BIGINT               NOT NULL,
    store_key          NVARCHAR(200)        NOT NULL,
    row_seq            BIGINT               NOT NULL,
    payload            NVARCHAR(MAX)        NOT NULL,
    source_origin      NVARCHAR(32)         NULL,
    is_deleted         SMALLINT             NOT NULL DEFAULT 0,
    deleted_at         DATETIME2            NULL,
    created_at         DATETIME2            NOT NULL DEFAULT SYSUTCDATETIME(),
    created_by         NVARCHAR(100)        NULL,
    updated_at         DATETIME2            NULL,
    updated_by         NVARCHAR(100)        NULL,
    version            BIGINT               NOT NULL DEFAULT 0
);

CREATE INDEX idx_persistence_org_key
    ON persistence_row (organisation_id, store_key)
    WHERE is_deleted = 0;

CREATE INDEX idx_persistence_created
    ON persistence_row (created_at);

-- Optional FK to organisations table; left commented because some seed
-- environments don't have organisations populated yet.
-- ALTER TABLE persistence_row
--     ADD CONSTRAINT fk_persistence_org FOREIGN KEY (organisation_id)
--     REFERENCES organisations (id);
