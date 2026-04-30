-- ============================================================================
-- TSS GRC Platform - Lookup Tables
-- Schema: platform
-- Purpose: Configurable lookup/reference data categories and values
-- ============================================================================

USE TSS_GRC;
GO

-- --------------------------------------------------------------------------
-- 1. Lookup Categories
-- --------------------------------------------------------------------------
IF OBJECT_ID('platform.lookup_values', 'U') IS NOT NULL
    DROP TABLE platform.lookup_values;
IF OBJECT_ID('platform.lookup_categories', 'U') IS NOT NULL
    DROP TABLE platform.lookup_categories;
GO

CREATE TABLE platform.lookup_categories
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    code            NVARCHAR(100)                   NOT NULL,
    name_en         NVARCHAR(200)                   NOT NULL,
    name_ar         NVARCHAR(200)                   NULL,
    description_en  NVARCHAR(500)                   NULL,
    is_system       BIT                             NOT NULL    DEFAULT 0,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_lookup_categories PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_lookup_categories_code UNIQUE (code)
);
GO

-- --------------------------------------------------------------------------
-- 2. Lookup Values
-- --------------------------------------------------------------------------
CREATE TABLE platform.lookup_values
(
    id              BIGINT          IDENTITY(1,1)   NOT NULL,
    category_id     BIGINT                          NOT NULL,
    code            NVARCHAR(100)                   NOT NULL,
    display_en      NVARCHAR(300)                   NOT NULL,
    display_ar      NVARCHAR(300)                   NULL,
    description_en  NVARCHAR(500)                   NULL,
    display_order   INT                             NULL,
    is_default      BIT                             NOT NULL    DEFAULT 0,
    is_active       BIT                             NOT NULL    DEFAULT 1,
    parent_value_id BIGINT                          NULL,
    created_by      BIGINT                          NULL,
    created_at      DATETIME2                       NOT NULL    DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT                          NULL,
    updated_at      DATETIME2                       NULL,

    CONSTRAINT PK_lookup_values PRIMARY KEY CLUSTERED (id),

    CONSTRAINT UQ_lookup_values_category_code UNIQUE (category_id, code),

    CONSTRAINT FK_lookup_values_category FOREIGN KEY (category_id)
        REFERENCES platform.lookup_categories (id),

    CONSTRAINT FK_lookup_values_parent FOREIGN KEY (parent_value_id)
        REFERENCES platform.lookup_values (id)
);
GO

PRINT 'Tables platform.lookup_categories and platform.lookup_values created successfully.';
GO
