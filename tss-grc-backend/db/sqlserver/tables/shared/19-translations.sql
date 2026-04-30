-- ============================================================================
-- TSS GRC Platform - Translations Table
-- Schema: platform
-- Purpose: Dynamic field-level translations for any entity
-- ============================================================================

USE TSS_GRC;
GO

IF OBJECT_ID('platform.translations', 'U') IS NOT NULL
    DROP TABLE platform.translations;
GO

CREATE TABLE platform.translations
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    entity_type     NVARCHAR(100)                   NOT NULL,
    entity_id       BIGINT                          NOT NULL,
    field_name      NVARCHAR(100)                   NOT NULL,
    locale          NVARCHAR(10)                    NOT NULL,
    translated_text NVARCHAR(MAX)                   NULL,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_translations PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_translations UNIQUE (entity_type, entity_id, field_name, locale)
);
GO

PRINT 'Table platform.translations created successfully.';
GO
