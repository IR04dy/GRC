-- V160 — Saved Views (Sprint-4 Track-2.5 Phase 2 backend).
--
-- Closes the Phase-1 frontend that's been writing to localStorage. The
-- shape here is 1-for-1 with the {@code SavedView} TypeScript interface
-- shipped in PR #105/#106/#107 (cyber/vulns, workflow/submissions,
-- risk/register), so the migration is purely additive — the existing
-- localStorage rows survive until the user signs in and hits "Sync";
-- the frontend adapter (lib/savedViews.ts) prefers the server when it
-- responds 2xx and falls back to localStorage when it doesn't (kiosk /
-- demo / offline).
--
-- Why a per-user table (not per-org with an owner column)?
-- Because saved views are personal preferences, not data. A risk
-- manager's "Critical risks this quarter" view is for them; the only
-- cross-user surface is the {@code is_shared} flag (Phase-3 follow-up).
--
-- Why JSON columns instead of relational filter/sort/columns?
-- The shape of "filter" varies by page (the workflow filter is just
-- a URL query string; the cyber filter is a FilterGroup tree). Coercing
-- everything into a single relational schema would force every list
-- page to learn the new shape; persisting opaque JSON lets each page
-- own its own format. The DB never reads inside these blobs.

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ════════════════════════════════════════════════════════════════════════
-- Table: platform.saved_views
-- ════════════════════════════════════════════════════════════════════════
IF NOT EXISTS (SELECT 1 FROM sys.tables t
                JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'platform' AND t.name = 'saved_views')
BEGIN
    CREATE TABLE platform.saved_views (
        id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id       BIGINT          NOT NULL,
        user_id               BIGINT          NOT NULL,
        page_key              NVARCHAR(120)   NOT NULL,                       -- e.g. "cyber/vulns", "risk/register"
        name                  NVARCHAR(160)   NOT NULL,
        is_default            BIT             NOT NULL DEFAULT 0,
        is_shared             BIT             NOT NULL DEFAULT 0,             -- Phase-3 (org-wide)
        filter_json           NVARCHAR(MAX),                                  -- opaque per-page FilterGroup blob
        sort_json             NVARCHAR(MAX),                                  -- opaque per-page sort blob
        visible_columns_json  NVARCHAR(MAX),                                  -- opaque per-page column list
        url_query             NVARCHAR(2000),                                 -- search-params snapshot (workflow/risk)
        is_deleted            BIT             NOT NULL DEFAULT 0,
        created_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at            DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by            BIGINT,
        updated_by            BIGINT
    );

    -- Fast lookup — "all my views for this page". Covers the hottest
    -- query the dropdown fires on every list-page mount.
    CREATE INDEX IX_saved_views_org_user_page
        ON platform.saved_views (organisation_id, user_id, page_key)
        WHERE is_deleted = 0;

    -- Default-view lookup — fires on mount when the URL is clean.
    -- Filtered index because at most one row per (user, page) is the
    -- default; SQL Server can use this as a unique lookup.
    CREATE UNIQUE INDEX UX_saved_views_default_per_user_page
        ON platform.saved_views (organisation_id, user_id, page_key)
        WHERE is_default = 1 AND is_deleted = 0;
END
GO

-- ════════════════════════════════════════════════════════════════════════
-- Audit hook — same generic audit log every other module writes through.
-- A saved view is not regulated data, but tenants run "who set what
-- default" forensics during access reviews. The hook is wired in the
-- service layer (SavedViewService), not via DB trigger.
-- ════════════════════════════════════════════════════════════════════════
-- No DB triggers — see SavedViewService.audit(...) for the call sites.

GO
