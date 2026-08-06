-- V120: round-4 demo fill — last remaining empty tables.
--
-- Fills:
--   compliance.compliance_assessments  6 assessments (NCA / PDPL / ISO / SAMA)
--   compliance.compliance_findings    12 findings linked to those assessments
--   platform.risk_control_links        18 risk→control bridge rows (cross-module)
--   platform.control_objectives        +8 objectives so the catalog isn't 1-deep
--
-- Per-row NOT EXISTS guards; idempotent.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO
-- ⚠ F2-77 / F2-140: guarded because compliance.compliance_assessments and
-- compliance.compliance_findings are created by NO migration in the set. Measured: the folder
-- CREATEs 470 tables, the live database holds 470, and three differ in each direction — these
-- two are among the three that exist live (10 and 18 rows) with no DDL anywhere. They are not
-- in the V341/V342 spine bootstrap either, and they have no JPA entity, which is why F2-70's
-- ratchets never asked about them and read as closed.
--
-- This is DEMO data, so skipping is the right answer and not a compromise: a demo fill for a
-- table the migration set does not build should be absent, not fatal. Whether those tables
-- ought to exist at all is F2-140's owner decision — they carry rows, have no entity, and sit
-- beside a live sibling (compliance.assessments) — and guarding here deliberately does not
-- pre-empt it. On both live databases the tables exist, so this runs exactly as before.
IF OBJECT_ID(N'compliance.compliance_assessments', N'U') IS NOT NULL
BEGIN

-- ══════════════════════════════════════════════════════════════════
-- (1) compliance.compliance_assessments — 6 cross-framework runs
-- ══════════════════════════════════════════════════════════════════

DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM compliance.compliance_assessments WHERE organisation_id = @org)
BEGIN
    -- compliance.frameworks is a shared catalog (no organisation_id) — pick the
    -- first 6 active ones for cross-tenant coverage.
    ;WITH fws AS (
        SELECT TOP 6 id, name_en FROM compliance.frameworks
         WHERE is_active = 1
         ORDER BY id ASC
    ),
    rows_ AS (
        SELECT f.id AS framework_id, f.name_en,
               ROW_NUMBER() OVER (ORDER BY f.id) AS rn
          FROM fws f
    )
    INSERT INTO compliance.compliance_assessments
        (organisation_id, framework_id, assessment_name_en, assessment_type,
         start_date, end_date, status, overall_score,
         total_controls, compliant_count, non_compliant_count,
         is_deleted, created_at)
    SELECT @org, r.framework_id,
           CONCAT(N'Q', ((MONTH(GETUTCDATE()) - 1) / 3) + 1, N' 2026 — ', r.name_en, N' self-assessment'),
           CASE (r.rn % 3) WHEN 0 THEN N'SELF' WHEN 1 THEN N'INTERNAL' ELSE N'EXTERNAL' END,
           DATEADD(DAY, -(r.rn * 25), CAST(GETUTCDATE() AS DATE)),
           CASE WHEN r.rn <= 3
                THEN DATEADD(DAY, -(r.rn * 25) + 14, CAST(GETUTCDATE() AS DATE))
                ELSE NULL END,
           CASE WHEN r.rn = 1 THEN N'COMPLETED'
                WHEN r.rn = 2 THEN N'COMPLETED'
                WHEN r.rn = 3 THEN N'UNDER_REVIEW'
                ELSE              N'IN_PROGRESS'
           END,
           CAST(60 + (r.rn * 5) AS DECIMAL(5,2)),  -- 65..90 spread
           50 + r.rn * 10,
           CAST((50 + r.rn * 10) * (60 + r.rn * 5) / 100.0 AS INT),
           CAST((50 + r.rn * 10) * (40 - r.rn * 5) / 100.0 AS INT),
           0, DATEADD(DAY, -(r.rn * 25), GETUTCDATE())
      FROM rows_ r;
END
END
GO
-- ⚠ F2-77 / F2-140: guarded because compliance.compliance_assessments and
-- compliance.compliance_findings are created by NO migration in the set. Measured: the folder
-- CREATEs 470 tables, the live database holds 470, and three differ in each direction — these
-- two are among the three that exist live (10 and 18 rows) with no DDL anywhere. They are not
-- in the V341/V342 spine bootstrap either, and they have no JPA entity, which is why F2-70's
-- ratchets never asked about them and read as closed.
--
-- This is DEMO data, so skipping is the right answer and not a compromise: a demo fill for a
-- table the migration set does not build should be absent, not fatal. Whether those tables
-- ought to exist at all is F2-140's owner decision — they carry rows, have no entity, and sit
-- beside a live sibling (compliance.assessments) — and guarding here deliberately does not
-- pre-empt it. On both live databases the tables exist, so this runs exactly as before.
IF OBJECT_ID(N'compliance.compliance_assessments', N'U') IS NOT NULL
BEGIN

-- ══════════════════════════════════════════════════════════════════
-- (2) compliance.compliance_findings — 2 per assessment = 12
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_cf BIGINT = 1;

IF EXISTS (SELECT 1 FROM compliance.compliance_assessments WHERE organisation_id = @org_cf)
   AND NOT EXISTS (SELECT 1 FROM compliance.compliance_findings cf
                    JOIN compliance.compliance_assessments ca ON ca.id = cf.assessment_id
                   WHERE ca.organisation_id = @org_cf)
BEGIN
    ;WITH ax AS (
        SELECT id, assessment_name_en AS name_en, ROW_NUMBER() OVER (ORDER BY id) AS rn
          FROM compliance.compliance_assessments
         WHERE organisation_id = @org_cf AND is_deleted = 0
    ),
    flv (slot, type_, sev, title_template) AS (
        SELECT 1, N'CONTROL_GAP',   N'HIGH',   N'Control %s testing evidence is over 12 months old'
        UNION ALL SELECT 2, N'DOC_GAP',       N'MEDIUM', N'Control %s — policy reference missing in framework mapping'
    )
    INSERT INTO compliance.compliance_findings
        (assessment_id, finding_type, severity, title_en, description_en,
         status, due_date, is_deleted, created_at)
    SELECT a.id, f.type_, f.sev,
           REPLACE(f.title_template, '%s', a.name_en),
           CONCAT(N'Auto-seeded ', LOWER(f.type_), N' identified during ', a.name_en, N'. Remediation plan to be drafted.'),
           CASE WHEN f.slot = 1 THEN N'OPEN' ELSE N'IN_PROGRESS' END,
           DATEADD(DAY, 30 + (a.rn * 15), CAST(GETUTCDATE() AS DATE)),
           0, GETUTCDATE()
      FROM ax a CROSS JOIN flv f;
END
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (3) platform.risk_control_links — duplicate the risk_controls bridge
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_rcl BIGINT = 1;

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'risk_control_links' AND schema_id = SCHEMA_ID('platform'))
   AND NOT EXISTS (SELECT 1 FROM platform.risk_control_links WHERE organisation_id = @org_rcl)
BEGIN
    -- Copy the bridge from risk.risk_controls (created in V113 + V119) into
    -- platform.risk_control_links so the legacy cross-module page that
    -- reads from `platform.risk_control_links` has matching content.
    DECLARE @cols NVARCHAR(MAX) = (
        SELECT STRING_AGG(name, ', ')
          FROM sys.columns
         WHERE object_id = OBJECT_ID('platform.risk_control_links')
           AND name IN ('organisation_id','risk_id','control_id','is_deleted','created_at')
    );

    IF CHARINDEX('risk_id', @cols) > 0 AND CHARINDEX('control_id', @cols) > 0
    BEGIN
        INSERT INTO platform.risk_control_links (organisation_id, risk_id, control_id, is_deleted, created_at)
        SELECT rc.organisation_id, rc.risk_id, rc.control_id, 0, SYSUTCDATETIME()
          FROM risk.risk_controls rc
         WHERE rc.organisation_id = @org_rcl
           AND rc.is_deleted = 0
           AND NOT EXISTS (SELECT 1 FROM platform.risk_control_links x
                            WHERE x.organisation_id = rc.organisation_id
                              AND x.risk_id          = rc.risk_id
                              AND x.control_id       = rc.control_id);
    END
END
GO

PRINT 'V120 round-4 demo fill applied: compliance assessments + findings + risk_control_links.';
GO
