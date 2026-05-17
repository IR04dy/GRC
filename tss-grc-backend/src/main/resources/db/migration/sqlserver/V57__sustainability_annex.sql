-- V57 — Annual Sustainability Annex + Reporting-Year lifecycle
--
-- Slice 3 of the Sustainability module. Produces the canonical Saudi-
-- listed-company deliverable: a bilingual EN/AR annex covering CMA
-- Articles 91-94, Saudi Green Initiative commitments, TCFD alignment,
-- GHG inventory, and Climate risks/opportunities — all in one report.
--
-- Two new tables:
--   1. sust.reporting_year     — one row per (org, year)
--      Lifecycle:  ACTIVE → DATA_COLLECTION → REVIEW → APPROVED →
--                  PUBLISHED → ARCHIVED
--   2. sust.sustainability_report — generated report instances
--      Lifecycle:  DRAFT → SUBMITTED → APPROVED → PUBLISHED
--      Holds the generated content_markdown / content_html /
--      content_json snapshots so publishing is immutable.
--
-- Cross-module composition (no FK — service joins by reporting year):
--   The AnnexGeneratorService stitches together CMA disclosures (still
--   static in /esg lib for now), SGI commitments (static), TCFD items
--   (static), Climate Risks (V55), and GHG Inventory (V56). Future
--   slices that promote CMA/SGI/TCFD to real entities will plug into
--   the same generator.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

-- ─── reporting_year ─────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'reporting_year' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.reporting_year (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT        NOT NULL,
        external_id              NVARCHAR(64)  NULL,

        year                     SMALLINT      NOT NULL,
        period_start             DATE          NULL,        -- defaults to Jan 1 of `year`
        period_end               DATE          NULL,        -- defaults to Dec 31 of `year`

        reporting_entity_en      NVARCHAR(255) NOT NULL,
        reporting_entity_ar      NVARCHAR(255) NULL,

        -- ACTIVE | DATA_COLLECTION | REVIEW | APPROVED | PUBLISHED | ARCHIVED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'ACTIVE',

        prepared_by              NVARCHAR(255) NULL,
        reviewed_by              NVARCHAR(255) NULL,
        approved_by              NVARCHAR(255) NULL,
        approved_at              DATETIME2     NULL,
        published_at             DATETIME2     NULL,

        readiness_score_pct      DECIMAL(5,2)  NULL,        -- 0..100 — computed
        open_gaps_count          INT           NULL,         -- computed

        notes                    NVARCHAR(MAX) NULL,

        is_deleted               BIT           NOT NULL DEFAULT 0,
        created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               NVARCHAR(100) NULL,
        updated_at               DATETIME2     NULL,
        updated_by               NVARCHAR(100) NULL,

        CONSTRAINT ck_ry_status CHECK (status IN
            (N'ACTIVE', N'DATA_COLLECTION', N'REVIEW', N'APPROVED', N'PUBLISHED', N'ARCHIVED'))
    );

    -- Only one ACTIVE/draft year per (org, year) — enforce via UNIQUE
    CREATE UNIQUE INDEX uq_ry_org_year ON sust.reporting_year(organisation_id, year)
        WHERE is_deleted = 0;
END
GO

-- ─── sustainability_report ───────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'sustainability_report' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.sustainability_report (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT        NOT NULL,
        reporting_year_id        BIGINT        NOT NULL,
        external_id              NVARCHAR(64)  NULL,

        report_code              NVARCHAR(40)  NULL,

        -- ANNUAL_ANNEX | BOARD_ESG | CMA_MATRIX | TCFD_REPORT | GHG_REPORT |
        -- SGI_PROGRESS | EVIDENCE_PKG | CLIMATE_RISK | CAPA_STATUS | DASH_EXPORT
        report_type              NVARCHAR(30)  NOT NULL,

        title_en                 NVARCHAR(255) NOT NULL,
        title_ar                 NVARCHAR(255) NULL,

        version                  NVARCHAR(20)  NOT NULL DEFAULT N'v1.0',

        -- DRAFT | SUBMITTED | APPROVED | PUBLISHED | SUPERSEDED
        status                   NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',

        generated_at             DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        generated_by             NVARCHAR(255) NULL,
        reviewed_by              NVARCHAR(255) NULL,
        approved_by              NVARCHAR(255) NULL,
        approved_at              DATETIME2     NULL,
        published_at             DATETIME2     NULL,

        -- Generated content snapshots — locked once status = APPROVED.
        -- NVARCHAR(MAX) on SQL Server fits a multi-thousand-line annex
        -- comfortably (up to ~2GB).
        content_markdown         NVARCHAR(MAX) NULL,
        content_html             NVARCHAR(MAX) NULL,
        content_json             NVARCHAR(MAX) NULL,

        is_deleted               BIT           NOT NULL DEFAULT 0,
        created_at               DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               NVARCHAR(100) NULL,
        updated_at               DATETIME2     NULL,
        updated_by               NVARCHAR(100) NULL,

        CONSTRAINT ck_sr_type   CHECK (report_type IN (
            N'ANNUAL_ANNEX', N'BOARD_ESG', N'CMA_MATRIX', N'TCFD_REPORT',
            N'GHG_REPORT', N'SGI_PROGRESS', N'EVIDENCE_PKG',
            N'CLIMATE_RISK', N'CAPA_STATUS', N'DASH_EXPORT')),
        CONSTRAINT ck_sr_status CHECK (status IN
            (N'DRAFT', N'SUBMITTED', N'APPROVED', N'PUBLISHED', N'SUPERSEDED')),
        CONSTRAINT fk_sr_year   FOREIGN KEY (reporting_year_id)
            REFERENCES sust.reporting_year(id)
    );

    CREATE INDEX ix_sr_org_year   ON sust.sustainability_report(organisation_id, reporting_year_id);
    CREATE INDEX ix_sr_org_status ON sust.sustainability_report(organisation_id, status);
END
GO

-- ─── Seed — one 2025 reporting year in REVIEW status ─────────────────
IF NOT EXISTS (SELECT 1 FROM sust.reporting_year WHERE organisation_id = 1)
BEGIN
    INSERT INTO sust.reporting_year (
        organisation_id, year, period_start, period_end,
        reporting_entity_en, reporting_entity_ar,
        status, prepared_by, reviewed_by, readiness_score_pct, open_gaps_count, notes
    )
    VALUES (
        1, 2025, '2025-01-01', '2025-12-31',
        N'TSS GRC Platform',
        N'منصة الحوكمة والمخاطر والامتثال',
        N'REVIEW',
        N'Sara Al-Zahrani (Sustainability Manager)',
        N'Khalid Al-Otaibi (CSO)',
        84.0, 3,
        N'Q1 2026 prep cycle. Scope 3 coverage gap + 2 partial CMA disclosures still being closed.'
    );
END
GO
