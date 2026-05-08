-- ============================================================================
-- Seed two additional demo organisations so the multi-tenant org switcher
-- has something to switch into.
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

-- Brand-aligned demo colours so each tenant is instantly recognisable
-- when the user switches organisations:
--   TSS   = #1a2744  navy   (column default — left alone)
--   ATC   = #00754A  deep green  — Aramco's signature green
--   STCB  = #4F008C  royal purple — STC's iconic brand colour
INSERT INTO platform.organisations (name_en, name_ar, short_code, industry_sector, country_code, subscription_tier, is_active, primary_color)
SELECT v.en, v.ar, v.code, v.sector, v.country, v.tier, 1, v.color
FROM (VALUES
  (N'Aramco Trading Company',      N'شركة أرامكو التجارية',  N'ATC',  N'Energy',         N'SAU', N'ENTERPRISE', N'#00754A'),
  (N'STC Bank — Demo Tenant',      N'بنك STC — منشأة تجريبية', N'STCB', N'Banking',        N'SAU', N'ENTERPRISE', N'#4F008C')
) AS v(en, ar, code, sector, country, tier, color)
WHERE NOT EXISTS (SELECT 1 FROM platform.organisations o WHERE o.short_code = v.code AND o.is_deleted = 0);

-- Bring existing rows in line with the new brand palette (idempotent —
-- only touches rows whose colour still matches the previous defaults so
-- a tenant that customised theirs via the WhiteLabel page is preserved).
UPDATE platform.organisations SET primary_color = N'#00754A'
  WHERE short_code = N'ATC'  AND primary_color IN (N'#0E5E5E', N'#1a2744');
UPDATE platform.organisations SET primary_color = N'#4F008C'
  WHERE short_code = N'STCB' AND primary_color IN (N'#3F8C5C', N'#1a2744');

PRINT N'Seeded extra demo orgs (idempotent).';

SELECT id, name_en, short_code, subscription_tier, primary_color, is_active
FROM platform.organisations
WHERE is_deleted = 0
ORDER BY id;
GO
