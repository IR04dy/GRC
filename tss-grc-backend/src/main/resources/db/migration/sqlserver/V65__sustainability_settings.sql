-- V65 — Sustainability Module Settings (slice 14-A)
--
-- One row per organisation carrying configurable thresholds + cron
-- schedules that the rest of the sust/* slices read at runtime.
--
-- Unlocks workflow rules that previously had hard-coded constants:
--   • Rule 1 — scope_3 partial coverage threshold (was hard-coded 70%)
--   • Rule 3 — SGI at-risk progress factor (was hard-coded 0.7)
--   • Rule 9 — evidence sweep cron + expiry warning window
--   • Rule 13 — CAPA overdue escalation windows (warning + executive)
--   • Rule 14 — ESG overall-score warning/critical bands for the
--               dashboard "low readiness" exec banner
--
-- One-row-per-org keeps the schema flat and avoids EAV pain. New
-- thresholds get added as new columns with defaults, not as new rows.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'organisation_settings' AND schema_id = SCHEMA_ID('sust'))
BEGIN
    CREATE TABLE sust.organisation_settings (
        id                                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                    BIGINT        NOT NULL,

        -- ── Rule 14 — ESG Overall Score bands (dashboard banner) ──
        -- Banner shows warning when score < warning_threshold,
        -- critical when score < critical_threshold.
        esg_readiness_warning_threshold    INT           NOT NULL DEFAULT 60,
        esg_readiness_critical_threshold   INT           NOT NULL DEFAULT 40,

        -- ── Rule 9 — Evidence expiry sweep ─────────────────────
        evidence_expiry_warning_days       INT           NOT NULL DEFAULT 30,
        evidence_sweep_cron                NVARCHAR(50)  NOT NULL DEFAULT N'0 0 6 * * *',

        -- ── Rule 1 — GHG Scope 3 partial-coverage threshold ────
        scope3_partial_coverage_threshold  DECIMAL(5,2)  NOT NULL DEFAULT 70.00,

        -- ── Rule 13 — Corrective-action escalation windows ─────
        capa_overdue_warning_days          INT           NOT NULL DEFAULT 7,
        capa_executive_escalation_days     INT           NOT NULL DEFAULT 30,

        -- ── Rule 3 — SGI at-risk progress factor ───────────────
        -- A commitment is "at risk" when actual_pct < expected_pct × factor.
        -- Default 0.70 means a commitment progressing at 70% of the
        -- linear expected rate is still on-track; below that is at risk.
        sgi_at_risk_progress_factor        DECIMAL(3,2)  NOT NULL DEFAULT 0.70,

        notes                              NVARCHAR(MAX) NULL,

        is_deleted                         BIT           NOT NULL DEFAULT 0,
        created_at                         DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                         NVARCHAR(100) NULL,
        updated_at                         DATETIME2     NULL,
        updated_by                         NVARCHAR(100) NULL,

        CONSTRAINT ck_orgsettings_score_bands CHECK (
            esg_readiness_warning_threshold BETWEEN 0 AND 100
            AND esg_readiness_critical_threshold BETWEEN 0 AND 100
            AND esg_readiness_critical_threshold <= esg_readiness_warning_threshold
        ),
        CONSTRAINT ck_orgsettings_scope3 CHECK (scope3_partial_coverage_threshold BETWEEN 0 AND 100),
        CONSTRAINT ck_orgsettings_sgi_factor CHECK (sgi_at_risk_progress_factor BETWEEN 0 AND 1),
        CONSTRAINT ck_orgsettings_capa_days CHECK (
            capa_overdue_warning_days >= 0 AND capa_executive_escalation_days >= capa_overdue_warning_days
        )
    );

    CREATE UNIQUE INDEX uq_orgsettings_org ON sust.organisation_settings(organisation_id)
        WHERE is_deleted = 0;
END
GO

-- Seed default settings for org 1.
IF NOT EXISTS (SELECT 1 FROM sust.organisation_settings WHERE organisation_id = 1)
BEGIN
    INSERT INTO sust.organisation_settings (organisation_id, notes)
    VALUES (1, N'Default thresholds — tune via /esg → Settings before promoting to staging.');
END
GO
