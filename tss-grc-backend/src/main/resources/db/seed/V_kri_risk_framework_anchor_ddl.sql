-- ============================================================================
-- TSS GRC — IA-5: KRIs + Risk Register framework anchor
-- ============================================================================
-- KRIs follow the same pattern as Controls (IA-3): typed FK +
-- backfill from existing `framework` string column.
--
-- Risks get TWO framework dimensions because Saudi regulated entities
-- (banks, insurers) need both lenses simultaneously:
--   * risk_framework_id      — ISO 31000, COSO ERM, NIST RMF (the risk
--                              taxonomy)
--   * compliance_framework_id — NCA ECC, SAMA CSF, PDPL, ZATCA … (the
--                              regulatory mandate this risk threatens)
-- Optionally with clause_ref per dimension.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

-- ── Add new RISK_MGMT family entries to the framework dictionary ───────────
-- Move COSO_ERM from SECURITY → RISK_MGMT (it was incorrectly classified)
-- and add ISO 31000 + NIST RMF.
UPDATE compliance.frameworks SET family = N'RISK_MGMT' WHERE code = 'COSO_ERM';

INSERT INTO compliance.frameworks (code, name_en, name_ar, version, issuer, family)
SELECT v.code, v.en, v.ar, v.ver, v.iss, v.fam
FROM (VALUES
  (N'ISO_31000',  N'ISO 31000 Risk Management',          N'إدارة المخاطر — ISO 31000',           N'2018',  N'ISO',  N'RISK_MGMT'),
  (N'NIST_RMF',   N'NIST Risk Management Framework',     N'إطار إدارة المخاطر — NIST',          N'SP 800-37 r2', N'NIST', N'RISK_MGMT'),
  (N'BASEL_III',  N'Basel III Operational Risk',          N'بازل 3 — مخاطر تشغيلية',              N'2023',  N'BCBS', N'RISK_MGMT')
) AS v(code, en, ar, ver, iss, fam)
WHERE NOT EXISTS (SELECT 1 FROM compliance.frameworks f WHERE f.code = v.code);

PRINT N'RISK_MGMT family expanded.';

-- ── KRIs framework FK (mirrors Controls IA-3) ──────────────────────────────
IF COL_LENGTH('risk.kri_definitions', 'framework_id') IS NULL
    ALTER TABLE risk.kri_definitions ADD framework_id BIGINT NULL;
IF COL_LENGTH('risk.kri_definitions', 'clause_ref')   IS NULL
    ALTER TABLE risk.kri_definitions ADD clause_ref NVARCHAR(100) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_kri_framework')
   AND OBJECT_ID('compliance.frameworks') IS NOT NULL
BEGIN
    ALTER TABLE risk.kri_definitions
        ADD CONSTRAINT FK_kri_framework FOREIGN KEY (framework_id) REFERENCES compliance.frameworks(id);
    PRINT N'Added FK_kri_framework';
END
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_kri_framework' AND object_id=OBJECT_ID('risk.kri_definitions'))
BEGIN
    CREATE INDEX IX_kri_framework ON risk.kri_definitions(framework_id) WHERE is_deleted = 0;
END
GO

-- Backfill KRI framework_id from existing string column. The seed data uses
-- partial / human-readable strings ("NCA ECC v2.0 §2-12-3", "ZATCA + SAMA
-- quarterly returns") so we use LIKE matching for best-effort tagging.
UPDATE risk.kri_definitions
   SET framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'NCA_ECC')
 WHERE framework_id IS NULL AND framework LIKE N'%NCA%';
UPDATE risk.kri_definitions
   SET framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'SAMA_CSF')
 WHERE framework_id IS NULL AND framework LIKE N'%SAMA%';
UPDATE risk.kri_definitions
   SET framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'ZATCA_EI')
 WHERE framework_id IS NULL AND framework LIKE N'%ZATCA%';
UPDATE risk.kri_definitions
   SET framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'PDPL')
 WHERE framework_id IS NULL AND framework LIKE N'%PDPL%';
UPDATE risk.kri_definitions
   SET framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'ISO_22301')
 WHERE framework_id IS NULL AND framework LIKE N'%ISO 22301%';

PRINT N'KRI framework_id backfilled.';

-- ── Risk Register: dual framework anchor ────────────────────────────────────
IF COL_LENGTH('risk.risks', 'risk_framework_id')        IS NULL
    ALTER TABLE risk.risks ADD risk_framework_id BIGINT NULL;
IF COL_LENGTH('risk.risks', 'risk_framework_clause')    IS NULL
    ALTER TABLE risk.risks ADD risk_framework_clause NVARCHAR(100) NULL;
IF COL_LENGTH('risk.risks', 'compliance_framework_id')  IS NULL
    ALTER TABLE risk.risks ADD compliance_framework_id BIGINT NULL;
IF COL_LENGTH('risk.risks', 'compliance_framework_clause') IS NULL
    ALTER TABLE risk.risks ADD compliance_framework_clause NVARCHAR(100) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_risk_riskfw')
BEGIN
    ALTER TABLE risk.risks
        ADD CONSTRAINT FK_risk_riskfw FOREIGN KEY (risk_framework_id) REFERENCES compliance.frameworks(id);
    PRINT N'Added FK_risk_riskfw';
END
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name='FK_risk_compfw')
BEGIN
    ALTER TABLE risk.risks
        ADD CONSTRAINT FK_risk_compfw FOREIGN KEY (compliance_framework_id) REFERENCES compliance.frameworks(id);
    PRINT N'Added FK_risk_compfw';
END
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_risk_riskfw' AND object_id=OBJECT_ID('risk.risks'))
BEGIN
    CREATE INDEX IX_risk_riskfw ON risk.risks(risk_framework_id) WHERE is_deleted = 0;
END
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_risk_compfw' AND object_id=OBJECT_ID('risk.risks'))
BEGIN
    CREATE INDEX IX_risk_compfw ON risk.risks(compliance_framework_id) WHERE is_deleted = 0;
END
GO

-- Backfill risks: assign ISO 31000 as the default risk_framework for all
-- existing risks (the canonical baseline most Saudi regulated entities use)
-- and best-effort-tag compliance_framework_id from title keywords.
UPDATE risk.risks
   SET risk_framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'ISO_31000')
 WHERE risk_framework_id IS NULL AND is_deleted = 0;

UPDATE risk.risks
   SET compliance_framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'NCA_ECC')
 WHERE compliance_framework_id IS NULL AND (title_en LIKE N'%NCA%' OR title_en LIKE N'%cybersecurity%' OR title_en LIKE N'%ECC%') AND is_deleted = 0;
UPDATE risk.risks
   SET compliance_framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'SAMA_CSF')
 WHERE compliance_framework_id IS NULL AND (title_en LIKE N'%SAMA%' OR title_en LIKE N'%banking%') AND is_deleted = 0;
UPDATE risk.risks
   SET compliance_framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'PDPL')
 WHERE compliance_framework_id IS NULL AND (title_en LIKE N'%PDPL%' OR title_en LIKE N'%personal data%' OR title_en LIKE N'%privacy%') AND is_deleted = 0;
UPDATE risk.risks
   SET compliance_framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'ISO_22301')
 WHERE compliance_framework_id IS NULL AND (title_en LIKE N'%continuity%' OR title_en LIKE N'%disaster%') AND is_deleted = 0;
UPDATE risk.risks
   SET compliance_framework_id = (SELECT id FROM compliance.frameworks WHERE code = 'SAMA_AML')
 WHERE compliance_framework_id IS NULL AND (title_en LIKE N'%money laundering%' OR title_en LIKE N'%AML%') AND is_deleted = 0;

PRINT N'Risk dual-framework backfilled.';

SELECT
    N'IA-5 KRI + Risk dual framework DDL complete' AS status,
    (SELECT COUNT(*) FROM risk.kri_definitions WHERE framework_id IS NOT NULL AND is_deleted=0) AS taggedKris,
    (SELECT COUNT(*) FROM risk.kri_definitions WHERE is_deleted=0) AS totalKris,
    (SELECT COUNT(*) FROM risk.risks WHERE risk_framework_id IS NOT NULL AND is_deleted=0) AS risksRiskFw,
    (SELECT COUNT(*) FROM risk.risks WHERE compliance_framework_id IS NOT NULL AND is_deleted=0) AS risksCompFw,
    (SELECT COUNT(*) FROM risk.risks WHERE is_deleted=0) AS totalRisks;
GO
