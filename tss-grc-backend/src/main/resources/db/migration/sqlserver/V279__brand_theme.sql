-- V279: Export P2 — per-tenant brand theme.
--
-- Branding (organisation name, tagline, primary/accent colour, logo) previously
-- lived only in the browser (localStorage `whiteLabel.ts`) and never reached the
-- server-side export engines, so every tenant's PDF/XLSX rendered in the default
-- TSS palette. This table persists the theme per organisation so the PDF cover
-- colour + XLSX header fill can carry the tenant's identity.
--
-- One active row per organisation (filtered unique index on organisation_id).
-- Null fields fall back to the platform defaults at resolve time
-- (BrandThemeService: primary #094242, accent #C9A977).

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'brand_theme' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.brand_theme (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id BIGINT          NOT NULL,

        org_name_en     NVARCHAR(200)   NULL,
        org_name_ar     NVARCHAR(200)   NULL,
        tagline_en      NVARCHAR(300)   NULL,
        tagline_ar      NVARCHAR(300)   NULL,
        primary_hex     NVARCHAR(7)     NULL,   -- #RRGGBB
        accent_hex      NVARCHAR(7)     NULL,   -- #RRGGBB
        logo_data_url   NVARCHAR(MAX)   NULL,   -- data:image/...;base64,...

        -- BaseEntity audit / soft-delete / optimistic-lock columns
        created_at      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at      DATETIME2       NULL,
        created_by      BIGINT          NULL,
        updated_by      BIGINT          NULL,
        is_deleted      BIT             NOT NULL DEFAULT 0,
        deleted_at      DATETIME2       NULL,
        deleted_by      BIGINT          NULL,
        row_version     ROWVERSION
    );

    -- One active theme per organisation.
    CREATE UNIQUE INDEX ux_brand_theme_org
        ON platform.brand_theme(organisation_id)
        WHERE is_deleted = 0;
END
GO
