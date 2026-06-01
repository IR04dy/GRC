-- Generic, polymorphic control mapping: any entity (entity_type + entity_id) can
-- map to many sub-controls across frameworks. Optional, manual, user-maintained
-- (never auto-derived); replace-set semantics. Idempotent.
-- Dev DB is Python-managed via setup_control_links_table.py.

IF OBJECT_ID('evidence.control_links') IS NULL
BEGIN
    CREATE TABLE evidence.control_links (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT NOT NULL,
        entity_type     NVARCHAR(60) NOT NULL,
        entity_id       BIGINT NOT NULL,
        sub_control_id  BIGINT NOT NULL,
        created_by      BIGINT NULL,
        created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE UNIQUE INDEX ux_control_links
        ON evidence.control_links(organisation_id, entity_type, entity_id, sub_control_id);
END;
GO
