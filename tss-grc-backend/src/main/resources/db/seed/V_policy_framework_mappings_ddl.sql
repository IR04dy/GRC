-- ============================================================================
-- TSS GRC — Policy ↔ Framework many-to-many mappings (IA-1 framework anchor)
-- ============================================================================
-- Mirrors the Evidence module pattern but as M2M: one Policy can satisfy
-- multiple frameworks (ServiceNow UCF / MetricStream "Unified Control"
-- model). Each row binds a policy to a specific framework + clause/control
-- reference, with optional satisfaction notes.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

-- ── Lightweight framework dictionary (compliance scope) ────────────────────
-- We DON'T re-use evidence.frameworks because that one is the cyber-control
-- anchor catalogue. Policies need a broader dictionary covering NCA ECC,
-- ISO 27001/22301/27701, SAMA CSF, PDPL, ZATCA, HRSD, CMA, CITC.
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='compliance' AND t.name='frameworks')
BEGIN
    CREATE TABLE compliance.frameworks (
        id            BIGINT IDENTITY(1,1) PRIMARY KEY,
        code          NVARCHAR(50)  NOT NULL UNIQUE,
        name_en       NVARCHAR(255) NOT NULL,
        name_ar       NVARCHAR(255) NULL,
        version       NVARCHAR(40)  NULL,
        issuer        NVARCHAR(255) NULL,
        family        NVARCHAR(60)  NULL,    -- SECURITY | PRIVACY | BCM | FINANCIAL | LABOUR | TAX | TELECOM
        is_active     BIT NOT NULL CONSTRAINT DF_cmpfw_active DEFAULT 1,
        created_at    DATETIME2 NOT NULL CONSTRAINT DF_cmpfw_created DEFAULT SYSUTCDATETIME()
    );
    PRINT N'Created compliance.frameworks';
END
ELSE PRINT N'compliance.frameworks already exists';

-- Seed canonical Saudi + international frameworks (idempotent on code)
INSERT INTO compliance.frameworks (code, name_en, name_ar, version, issuer, family)
SELECT v.code, v.en, v.ar, v.ver, v.iss, v.fam
FROM (VALUES
  (N'NCA_ECC',       N'NCA Essential Cybersecurity Controls', N'الضوابط الأساسية للأمن السيبراني', N'v2.0',  N'National Cybersecurity Authority',     N'SECURITY'),
  (N'NCA_CCC',       N'NCA Cloud Cybersecurity Controls',     N'ضوابط الأمن السيبراني السحابية',     N'v1.0',  N'National Cybersecurity Authority',     N'SECURITY'),
  (N'NCA_TPCS',      N'NCA Telework Cybersecurity Standards', N'معايير الأمن السيبراني للعمل عن بُعد', N'v1.0',  N'National Cybersecurity Authority',     N'SECURITY'),
  (N'SAMA_CSF',      N'SAMA Cybersecurity Framework',         N'إطار الأمن السيبراني',               N'v1.0',  N'Saudi Central Bank',                   N'SECURITY'),
  (N'ISO_27001',     N'ISO/IEC 27001 ISMS',                   N'نظام إدارة أمن المعلومات',           N'2022',  N'ISO',                                  N'SECURITY'),
  (N'ISO_27701',     N'ISO/IEC 27701 PIMS',                   N'نظام إدارة معلومات الخصوصية',        N'2019',  N'ISO',                                  N'PRIVACY'),
  (N'ISO_22301',     N'ISO 22301 BCMS',                       N'نظام إدارة استمرارية الأعمال',       N'2019',  N'ISO',                                  N'BCM'),
  (N'PDPL',          N'SDAIA Personal Data Protection Law',    N'نظام حماية البيانات الشخصية',         N'IR-2024',N'SDAIA',                               N'PRIVACY'),
  (N'ZATCA_EI',      N'ZATCA E-invoicing',                    N'الفوترة الإلكترونية',                 N'Phase-2',N'Zakat, Tax & Customs Authority',      N'TAX'),
  (N'CMA_CGR',       N'CMA Corporate Governance Regulations', N'لوائح حوكمة الشركات',                N'2023',  N'Capital Market Authority',             N'FINANCIAL'),
  (N'CITC_TCF',      N'CITC Telecom Cybersecurity Framework', N'إطار الأمن السيبراني للاتصالات',     N'v1.0',  N'CITC',                                 N'TELECOM'),
  (N'HRSD_LR',       N'HRSD Labour Regulations',              N'لائحة العمل',                        N'2025',  N'Ministry of Human Resources',          N'LABOUR'),
  (N'NIST_CSF',      N'NIST Cybersecurity Framework',         N'إطار الأمن السيبراني NIST',          N'2.0',   N'NIST',                                 N'SECURITY'),
  (N'COBIT',         N'COBIT 2019',                            N'إطار حوكمة وإدارة المعلومات',        N'2019',  N'ISACA',                                N'SECURITY'),
  (N'COSO_ERM',      N'COSO ERM',                              N'إدارة مخاطر المؤسسة',                N'2017',  N'COSO',                                 N'SECURITY')
) AS v(code, en, ar, ver, iss, fam)
WHERE NOT EXISTS (SELECT 1 FROM compliance.frameworks f WHERE f.code = v.code);

PRINT N'Seeded compliance.frameworks (idempotent).';

-- ── Policy ↔ Framework mappings (M2M) ──────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='compliance' AND t.name='policy_framework_mappings')
BEGIN
    CREATE TABLE compliance.policy_framework_mappings (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        policy_id       BIGINT NOT NULL,
        framework_id    BIGINT NOT NULL,
        clause_ref      NVARCHAR(100) NULL,           -- e.g. "ECC §2-1-1", "A.5.1.1", "Art. 33"
        satisfaction    NVARCHAR(20) NOT NULL CONSTRAINT DF_pfm_sat DEFAULT N'PARTIAL',
        -- FULL | PARTIAL | OUT_OF_SCOPE | NOT_APPLICABLE
        notes           NVARCHAR(MAX) NULL,
        created_at      DATETIME2 NOT NULL CONSTRAINT DF_pfm_created DEFAULT SYSUTCDATETIME(),
        created_by      BIGINT NULL,
        is_deleted      BIT NOT NULL CONSTRAINT DF_pfm_isdel DEFAULT 0,
        CONSTRAINT FK_pfm_policy    FOREIGN KEY (policy_id)    REFERENCES compliance.policies(id),
        CONSTRAINT FK_pfm_framework FOREIGN KEY (framework_id) REFERENCES compliance.frameworks(id),
        CONSTRAINT UQ_pfm_combo UNIQUE (policy_id, framework_id, clause_ref)
    );
    CREATE INDEX IX_pfm_policy ON compliance.policy_framework_mappings(policy_id) WHERE is_deleted = 0;
    CREATE INDEX IX_pfm_framework ON compliance.policy_framework_mappings(framework_id) WHERE is_deleted = 0;
    PRINT N'Created compliance.policy_framework_mappings';
END
ELSE PRINT N'compliance.policy_framework_mappings already exists';

-- ── Seed: example mappings using whichever policies + frameworks are present
DECLARE @pISMS BIGINT = (SELECT TOP 1 id FROM compliance.policies WHERE code LIKE 'POL-INFO%' OR code LIKE 'POL-IS%' AND is_deleted = 0);
DECLARE @pAUP  BIGINT = (SELECT TOP 1 id FROM compliance.policies WHERE code LIKE '%ACC%' OR title_en LIKE '%Acceptable Use%' AND is_deleted = 0);
DECLARE @pBCM  BIGINT = (SELECT TOP 1 id FROM compliance.policies WHERE category = 'BUSINESS_CONTINUITY' AND is_deleted = 0);
DECLARE @pPRV  BIGINT = (SELECT TOP 1 id FROM compliance.policies WHERE category = 'PRIVACY' AND is_deleted = 0);

DECLARE @fNCA   BIGINT = (SELECT id FROM compliance.frameworks WHERE code='NCA_ECC');
DECLARE @fISO   BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ISO_27001');
DECLARE @fSAMA  BIGINT = (SELECT id FROM compliance.frameworks WHERE code='SAMA_CSF');
DECLARE @fPDPL  BIGINT = (SELECT id FROM compliance.frameworks WHERE code='PDPL');
DECLARE @f22301 BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ISO_22301');
DECLARE @f27701 BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ISO_27701');

-- ISMS policy → NCA + ISO + SAMA
IF @pISMS IS NOT NULL
BEGIN
    INSERT INTO compliance.policy_framework_mappings (policy_id, framework_id, clause_ref, satisfaction, notes)
    SELECT @pISMS, v.fw, v.cls, v.sat, v.n FROM (VALUES
      (@fNCA,  N'§1-1',           N'FULL',    N'Information Security Policy & Procedures.'),
      (@fISO,  N'A.5.1',          N'FULL',    N'Policies for information security.'),
      (@fSAMA, N'1.2.1',          N'FULL',    N'Cybersecurity strategy and policies.')
    ) AS v(fw, cls, sat, n)
    WHERE v.fw IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM compliance.policy_framework_mappings m
                       WHERE m.policy_id=@pISMS AND m.framework_id=v.fw AND ISNULL(m.clause_ref,'')=v.cls AND m.is_deleted=0);
END

-- AUP → NCA + ISO + HRSD
IF @pAUP IS NOT NULL
BEGIN
    INSERT INTO compliance.policy_framework_mappings (policy_id, framework_id, clause_ref, satisfaction, notes)
    SELECT @pAUP, v.fw, v.cls, v.sat, v.n FROM (VALUES
      (@fNCA, N'§2-3-3',          N'FULL',    N'Acceptable use of assets — workstation & internet use.'),
      (@fISO, N'A.5.10',          N'FULL',    N'Acceptable use of information and other associated assets.')
    ) AS v(fw, cls, sat, n)
    WHERE v.fw IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM compliance.policy_framework_mappings m
                       WHERE m.policy_id=@pAUP AND m.framework_id=v.fw AND ISNULL(m.clause_ref,'')=v.cls AND m.is_deleted=0);
END

-- BCM policy → ISO 22301 + NCA
IF @pBCM IS NOT NULL
BEGIN
    INSERT INTO compliance.policy_framework_mappings (policy_id, framework_id, clause_ref, satisfaction, notes)
    SELECT @pBCM, v.fw, v.cls, v.sat, v.n FROM (VALUES
      (@f22301, N'§5.2',         N'FULL',    N'Business continuity policy.'),
      (@fNCA,   N'§5-1',         N'PARTIAL', N'Cybersecurity Resilience aspects.')
    ) AS v(fw, cls, sat, n)
    WHERE v.fw IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM compliance.policy_framework_mappings m
                       WHERE m.policy_id=@pBCM AND m.framework_id=v.fw AND ISNULL(m.clause_ref,'')=v.cls AND m.is_deleted=0);
END

-- Privacy policy → PDPL + ISO 27701
IF @pPRV IS NOT NULL
BEGIN
    INSERT INTO compliance.policy_framework_mappings (policy_id, framework_id, clause_ref, satisfaction, notes)
    SELECT @pPRV, v.fw, v.cls, v.sat, v.n FROM (VALUES
      (@fPDPL,  N'Art. 5',        N'FULL',    N'Lawful basis & data subject rights.'),
      (@f27701, N'A.7.2',         N'FULL',    N'Conditions for collection and processing.')
    ) AS v(fw, cls, sat, n)
    WHERE v.fw IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM compliance.policy_framework_mappings m
                       WHERE m.policy_id=@pPRV AND m.framework_id=v.fw AND ISNULL(m.clause_ref,'')=v.cls AND m.is_deleted=0);
END

PRINT N'Seeded sample policy↔framework mappings.';

-- ── Result summary ────────────────────────────────────────────────────────
SELECT
    N'Policy framework anchor DDL complete' AS status,
    (SELECT COUNT(*) FROM compliance.frameworks WHERE is_active=1)            AS frameworks,
    (SELECT COUNT(*) FROM compliance.policy_framework_mappings WHERE is_deleted=0) AS mappings,
    (SELECT COUNT(*) FROM compliance.policies WHERE is_deleted=0)              AS policies;

GO
