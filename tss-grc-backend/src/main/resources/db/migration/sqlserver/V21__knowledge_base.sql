-- V21 — Knowledge Base — Sprint 4 §2.9.
--
-- Bilingual KB articles + categories + per-org draft/published states.
-- Article search uses SQL Server full-text in a follow-up migration; this
-- file establishes the base schema.

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'kb')
    EXEC('CREATE SCHEMA kb');
GO

CREATE TABLE kb.categories (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id BIGINT        NOT NULL,
    code            NVARCHAR(64)  NOT NULL,
    name_en         NVARCHAR(200) NOT NULL,
    name_ar         NVARCHAR(200) NOT NULL,
    sort_order      INT           NOT NULL DEFAULT 100,
    is_deleted      SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_kb_cat UNIQUE (organisation_id, code)
);
GO

CREATE TABLE kb.articles (
    id               BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id      NVARCHAR(64)  NOT NULL,                    -- KB-2026-001
    organisation_id  BIGINT        NOT NULL,
    slug             NVARCHAR(120) NOT NULL,
    category_code    NVARCHAR(64),                              -- soft FK to kb.categories.code
    title_en         NVARCHAR(500) NOT NULL,
    title_ar         NVARCHAR(500) NOT NULL,
    summary_en       NVARCHAR(MAX),
    summary_ar       NVARCHAR(MAX),
    body_en_md       NVARCHAR(MAX),                              -- markdown
    body_ar_md       NVARCHAR(MAX),
    tags             NVARCHAR(500),                              -- comma-sep
    status           NVARCHAR(20)  NOT NULL DEFAULT 'DRAFT',    -- DRAFT/PUBLISHED/ARCHIVED
    visibility       NVARCHAR(20)  NOT NULL DEFAULT 'INTERNAL', -- INTERNAL/PUBLIC/RESTRICTED
    author           NVARCHAR(255),
    created_at       DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at       DATETIME2,
    published_at     DATETIME2,
    view_count       INT           NOT NULL DEFAULT 0,
    is_deleted       SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_kb_external UNIQUE (organisation_id, external_id),
    CONSTRAINT uk_kb_slug     UNIQUE (organisation_id, slug)
);
CREATE INDEX ix_kb_status ON kb.articles (organisation_id, status, is_deleted);
CREATE INDEX ix_kb_cat    ON kb.articles (organisation_id, category_code, is_deleted);
GO
