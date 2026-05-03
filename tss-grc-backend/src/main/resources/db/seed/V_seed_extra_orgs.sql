-- ============================================================================
-- Seed two additional demo organisations so the multi-tenant org switcher
-- has something to switch into.
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

INSERT INTO platform.organisations (name_en, name_ar, short_code, industry_sector, country_code, subscription_tier, is_active, primary_color)
SELECT v.en, v.ar, v.code, v.sector, v.country, v.tier, 1, v.color
FROM (VALUES
  (N'Aramco Trading Company',      N'شركة أرامكو التجارية',  N'ATC',  N'Energy',         N'SAU', N'ENTERPRISE', N'#0E5E5E'),
  (N'STC Bank — Demo Tenant',      N'بنك STC — منشأة تجريبية', N'STCB', N'Banking',        N'SAU', N'ENTERPRISE', N'#3F8C5C')
) AS v(en, ar, code, sector, country, tier, color)
WHERE NOT EXISTS (SELECT 1 FROM platform.organisations o WHERE o.short_code = v.code AND o.is_deleted = 0);

PRINT N'Seeded extra demo orgs (idempotent).';

SELECT id, name_en, short_code, subscription_tier, is_active
FROM platform.organisations
WHERE is_deleted = 0
ORDER BY id;
GO
