-- V26 — Board Pack module — Sprint 4 §3.2 #5.
--
-- Quarterly board reporting packets. State machine:
--   DRAFT → IN_REVIEW → APPROVED → PUBLISHED
--   any non-terminal → CANCELLED

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'board_pack')
    EXEC('CREATE SCHEMA board_pack');
GO

CREATE TABLE board_pack.packs (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id       NVARCHAR(64)  NOT NULL,                   -- BP-2026-Q1
    organisation_id   BIGINT        NOT NULL,
    title_en          NVARCHAR(500) NOT NULL,
    title_ar          NVARCHAR(500) NOT NULL,
    period_label      NVARCHAR(40)  NOT NULL,                   -- 'Q1 2026', 'H1 2026'
    period_start      DATE          NOT NULL,
    period_end        DATE          NOT NULL,
    audience          NVARCHAR(40)  NOT NULL DEFAULT 'BOARD',  -- BOARD/AUDIT_COMMITTEE/RISK_COMMITTEE/EXEC
    -- Content snapshots — JSON for flexibility (each section is auto-built from
    -- live module data at "snapshot" time so the pack reflects a fixed point).
    risk_summary_json     NVARCHAR(MAX),
    appetite_breaches_json NVARCHAR(MAX),
    audit_engagements_json NVARCHAR(MAX),
    incidents_json        NVARCHAR(MAX),
    pdpl_summary_json     NVARCHAR(MAX),
    ccm_summary_json      NVARCHAR(MAX),
    sla_summary_json      NVARCHAR(MAX),
    custom_sections_json  NVARCHAR(MAX),
    -- Lifecycle
    status            NVARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    drafted_by        NVARCHAR(255) NOT NULL,
    drafted_at        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    submitted_for_review_at DATETIME2,
    reviewed_at       DATETIME2,
    reviewed_by       NVARCHAR(255),
    review_comments_en NVARCHAR(MAX),
    approved_at       DATETIME2,
    approved_by       NVARCHAR(255),
    published_at      DATETIME2,
    publication_url   NVARCHAR(500),                              -- where the PDF lives
    cancelled_at      DATETIME2,
    cancellation_reason_en NVARCHAR(MAX),
    is_deleted        SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_bp_external UNIQUE (organisation_id, external_id)
);
CREATE INDEX ix_bp_org_status ON board_pack.packs (organisation_id, status, is_deleted);
CREATE INDEX ix_bp_period     ON board_pack.packs (organisation_id, period_start, is_deleted);
GO
