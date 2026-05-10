-- ============================================================================
-- TSS GRC — Phase A: Ethics & Financial-Crime framework dictionary entries
-- ============================================================================
-- Adds the regulatory anchors needed for anti-corruption / anti-bribery /
-- anti-money-laundering / code-of-conduct programs. Once these rows exist,
-- the framework-anchored sheet view (IA-1/2/3/4) lets users tag policies,
-- audit findings, controls and DPIAs to them with zero further code work.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

INSERT INTO compliance.frameworks (code, name_en, name_ar, version, issuer, family)
SELECT v.code, v.en, v.ar, v.ver, v.iss, v.fam
FROM (VALUES
  (N'NAZAHA',      N'Nazaha Anti-Corruption Regulations',           N'لوائح هيئة نزاهة لمكافحة الفساد',              N'2025',  N'National Anti-Corruption Commission (Nazaha)', N'ETHICS'),
  (N'ISO_37001',   N'ISO 37001 Anti-Bribery Management Systems',    N'ISO 37001 — أنظمة إدارة مكافحة الرشوة',         N'2016',  N'ISO',                                          N'ETHICS'),
  (N'SAMA_AML',    N'SAMA AML/CTF Rules for Banks',                  N'قواعد البنك المركزي لمكافحة غسل الأموال وتمويل الإرهاب', N'2024',  N'Saudi Central Bank',                           N'FINCRIME'),
  (N'CMA_AML',     N'CMA AML/CTF Rules for Capital Markets',         N'لوائح هيئة السوق المالية لمكافحة غسل الأموال', N'2023',  N'Capital Market Authority',                     N'FINCRIME'),
  (N'FATF_40',     N'FATF 40 Recommendations',                       N'توصيات مجموعة العمل المالي الـ 40',           N'2024',  N'Financial Action Task Force',                  N'FINCRIME'),
  (N'MOCI_COC',    N'MoCI Code of Commercial Conduct',               N'مدونة السلوك التجاري — وزارة التجارة',         N'2024',  N'Ministry of Commerce',                         N'ETHICS')
) AS v(code, en, ar, ver, iss, fam)
WHERE NOT EXISTS (SELECT 1 FROM compliance.frameworks f WHERE f.code = v.code);

PRINT N'Phase A — ethics/fincrime frameworks ensured.';

SELECT
    N'Phase A complete' AS status,
    (SELECT COUNT(*) FROM compliance.frameworks WHERE family = N'ETHICS')   AS ethicsFrameworks,
    (SELECT COUNT(*) FROM compliance.frameworks WHERE family = N'FINCRIME') AS fincrimeFrameworks,
    (SELECT COUNT(*) FROM compliance.frameworks WHERE is_active = 1)        AS totalActiveFrameworks;
GO
