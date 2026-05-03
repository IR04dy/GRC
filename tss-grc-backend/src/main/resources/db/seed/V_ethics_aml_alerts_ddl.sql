-- ============================================================================
-- TSS GRC — Phase B-4: AML transaction alerts (case management)
-- ============================================================================
-- Transaction-monitoring alerts surface here from the bank's AML engine for
-- analyst disposition. Each alert is a case with evidence, disposition,
-- optional STR (Suspicious Transaction Report) filing reference for SAFIU.
--
-- State machine:
--   OPEN → INVESTIGATING → CLEARED|ESCALATED|STR_FILED → CLOSED
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='ethics' AND t.name='aml_alerts')
BEGIN
    CREATE TABLE ethics.aml_alerts (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id         UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_aml_extid DEFAULT NEWID(),
        organisation_id     BIGINT NOT NULL,
        alert_code          NVARCHAR(50) NULL,                -- e.g. AML-2025-0042
        alert_type          NVARCHAR(50) NOT NULL,
        -- STRUCTURING | UNUSUAL_VOLUME | UNUSUAL_VELOCITY | HIGH_RISK_GEO
        --   | SANCTIONS_HIT | PEP_TRANSACTION | RAPID_MOVEMENT | CASH_INTENSIVE
        --   | UNKNOWN_COUNTERPARTY | POLITICAL_EXPOSURE | OTHER
        severity            NVARCHAR(20) NOT NULL CONSTRAINT DF_aml_sev DEFAULT N'MEDIUM',
        -- LOW | MEDIUM | HIGH | CRITICAL
        rule_id             NVARCHAR(100) NULL,                -- ID of the monitoring rule that fired
        rule_name           NVARCHAR(255) NULL,
        title_en            NVARCHAR(500) NOT NULL,
        title_ar            NVARCHAR(500) NULL,
        description         NVARCHAR(MAX) NOT NULL,
        -- Subject / counter-party
        subject_id          BIGINT NULL,                       -- FK to ethics.kyc_subjects (optional)
        subject_name        NVARCHAR(500) NULL,                -- denormalised for visibility
        counterparty_name   NVARCHAR(500) NULL,
        counterparty_country NVARCHAR(3) NULL,
        -- Transaction details
        transaction_count   INT NULL,
        aggregate_amount    DECIMAL(18,2) NULL,
        currency            NVARCHAR(3) NULL CONSTRAINT DF_aml_ccy DEFAULT N'SAR',
        period_from         DATE NULL,
        period_to           DATE NULL,
        first_detected_at   DATETIME2 NOT NULL CONSTRAINT DF_aml_fdt DEFAULT SYSUTCDATETIME(),
        -- Workflow
        status              NVARCHAR(30) NOT NULL CONSTRAINT DF_aml_status DEFAULT N'OPEN',
        -- OPEN | INVESTIGATING | CLEARED | ESCALATED | STR_FILED | CLOSED
        analyst_name        NVARCHAR(255) NULL,
        analyst_id          BIGINT NULL,
        sla_due_at          DATETIME2 NULL,                   -- usually +30 days from detection
        disposition         NVARCHAR(40) NULL,
        -- TRUE_POSITIVE | FALSE_POSITIVE | NEEDS_MORE_INFO | DUPLICATE
        disposition_notes   NVARCHAR(MAX) NULL,
        decided_at          DATETIME2 NULL,
        decided_by_name     NVARCHAR(255) NULL,
        str_filed           BIT NOT NULL CONSTRAINT DF_aml_str DEFAULT 0,
        str_reference       NVARCHAR(100) NULL,
        str_filed_at        DATETIME2 NULL,
        framework_id        BIGINT NULL,
        clause_ref          NVARCHAR(100) NULL,
        is_deleted          BIT NOT NULL CONSTRAINT DF_aml_isdel DEFAULT 0,
        created_at          DATETIME2 NOT NULL CONSTRAINT DF_aml_created DEFAULT SYSUTCDATETIME(),
        updated_at          DATETIME2 NULL,
        created_by          BIGINT NULL,
        updated_by          BIGINT NULL,
        CONSTRAINT FK_aml_subject   FOREIGN KEY (subject_id)   REFERENCES ethics.kyc_subjects(id),
        CONSTRAINT FK_aml_framework FOREIGN KEY (framework_id) REFERENCES compliance.frameworks(id)
    );
    CREATE INDEX IX_aml_org_status ON ethics.aml_alerts(organisation_id, status) WHERE is_deleted = 0;
    CREATE INDEX IX_aml_org_type   ON ethics.aml_alerts(organisation_id, alert_type) WHERE is_deleted = 0;
    PRINT N'Created ethics.aml_alerts';
END
ELSE PRINT N'ethics.aml_alerts already exists';

-- ── Seed: 4 representative AML alerts (Saudi context) ──────────────────────
DECLARE @org BIGINT = 1;
DECLARE @fSAMA BIGINT = (SELECT id FROM compliance.frameworks WHERE code='SAMA_AML');
DECLARE @fFATF BIGINT = (SELECT id FROM compliance.frameworks WHERE code='FATF_40');
DECLARE @prohibitedSubj BIGINT = (SELECT TOP 1 id FROM ethics.kyc_subjects WHERE subject_code='KYC-LE-0002' AND is_deleted=0);
DECLARE @pepSubj BIGINT = (SELECT TOP 1 id FROM ethics.kyc_subjects WHERE subject_code='KYC-IND-0002' AND is_deleted=0);

INSERT INTO ethics.aml_alerts
  (organisation_id, alert_code, alert_type, severity, rule_id, rule_name,
   title_en, title_ar, description,
   subject_id, subject_name, counterparty_name, counterparty_country,
   transaction_count, aggregate_amount, currency, period_from, period_to,
   first_detected_at, status, analyst_name, sla_due_at, disposition,
   disposition_notes, decided_at, decided_by_name,
   str_filed, str_reference, str_filed_at,
   framework_id, clause_ref)
SELECT @org, v.code, v.tp, v.sev, v.rid, v.rname,
       v.en, v.ar, v.dsc,
       v.subj, v.subjN, v.cpN, v.cpC,
       v.tc, v.amt, v.ccy, v.pf, v.pt,
       v.fdt, v.st, v.an, v.sla, v.disp,
       v.dn, v.dat, v.dby,
       v.str, v.strRef, v.strAt,
       v.fw, v.cls
FROM (VALUES
  (N'AML-2025-0001', N'STRUCTURING', N'CRITICAL', N'TM-007', N'Structuring under reporting threshold',
   N'Structuring pattern: 11 deposits SAR 49,500–49,900 in 8 days (BlueWave)',
   N'نمط هيكلة: 11 إيداع نقدي بين 49,500–49,900 ريال خلال 8 أيام',
   N'Transaction monitoring rule TM-007 fired — repeated cash deposits just under the SAR 50,000 reporting threshold across 11 branches in 8 business days. Pattern matches FATF structuring typology.',
   NULL, N'BlueWave Trading Co. (KSA)', N'Multiple cash sources', N'SAU',
   11, 545700.00, N'SAR', '2025-04-12', '2025-04-20',
   '2025-04-21 03:30', N'INVESTIGATING', N'AML Analyst — Layla Al-Anazi', '2025-05-21 03:30', NULL,
   NULL, NULL, NULL,
   0, NULL, NULL,
   @fSAMA, N'SAMA AML §3-2-7'),
  (N'AML-2025-0002', N'SANCTIONS_HIT', N'CRITICAL', N'TM-001', N'Sanctioned counter-party transaction',
   N'Outbound wire blocked: counter-party on UN consolidated list',
   N'تحويل صادر مرفوض: الطرف المقابل على قائمة الأمم المتحدة',
   N'Outbound USD 250,000 wire blocked at the screening gateway. Beneficial owner of the receiving entity matches UN consolidated sanctions list (98% confidence).',
   @prohibitedSubj, N'OffshoreCapital Holdings Ltd', N'OffshoreCapital Holdings Ltd', N'CYM',
   1, 937500.00, N'SAR', '2025-04-29', '2025-04-29',
   '2025-04-29 02:15', N'STR_FILED', N'Head of FCC', '2025-05-29 02:15', N'TRUE_POSITIVE',
   N'Confirmed match. Wire blocked. STR filed with SAFIU per SAMA AML §3-2-1.', '2025-04-29 04:30', N'Head of FCC',
   1, N'STR-2025-0042', '2025-04-29 04:45',
   @fSAMA, N'SAMA AML §3-2-1'),
  (N'AML-2025-0003', N'PEP_TRANSACTION', N'HIGH', N'TM-003', N'PEP unusual-volume transaction',
   N'Domestic PEP large inbound transfer (real-estate proceeds)',
   N'تحويل وارد كبير لشخصية سياسية محلية (عوائد عقارية)',
   N'Domestic PEP (KYC-IND-0002) received a SAR 12.5M inbound transfer from a notary-attested real-estate sale. Amount above customary activity. EDD on file documents source-of-funds.',
   @pepSubj, N'Khaled Bin Talal Al-Qassem', N'Notary office Riyadh', N'SAU',
   1, 12500000.00, N'SAR', '2025-04-15', '2025-04-15',
   '2025-04-16 03:00', N'CLEARED', N'AML Analyst — Layla Al-Anazi', '2025-05-16 03:00', N'TRUE_POSITIVE',
   N'Source of funds documented and verified against EDD file. No suspicion. Cleared but recorded for audit trail.', '2025-04-18 11:00', N'Head of FCC',
   0, NULL, NULL,
   @fSAMA, N'SAMA AML §3-3-2'),
  (N'AML-2025-0004', N'HIGH_RISK_GEO', N'MEDIUM', N'TM-014', N'High-risk geography exposure',
   N'Multiple wires to FATF grey-list jurisdiction',
   N'تحويلات متعددة إلى دولة على قائمة FATF الرمادية',
   N'Three outbound wires totaling SAR 850,000 to a jurisdiction on the FATF grey list within a 14-day window. Below STR threshold but flagged for analyst review.',
   NULL, N'BlueWave Trading Co. (KSA)', N'Trade-finance counter-party', N'PAK',
   3, 850000.00, N'SAR', '2025-04-08', '2025-04-22',
   '2025-04-23 03:00', N'OPEN', NULL, '2025-05-23 03:00', NULL,
   NULL, NULL, NULL,
   0, NULL, NULL,
   @fFATF, N'FATF Rec. 19')
) AS v(code, tp, sev, rid, rname, en, ar, dsc, subj, subjN, cpN, cpC, tc, amt, ccy, pf, pt, fdt, st, an, sla, disp, dn, dat, dby, str, strRef, strAt, fw, cls)
WHERE NOT EXISTS (SELECT 1 FROM ethics.aml_alerts a WHERE a.organisation_id=@org AND a.alert_code=v.code AND a.is_deleted=0);

PRINT N'Seeded 4 sample AML alerts.';

SELECT
    N'Phase B-4 AML alerts DDL complete' AS status,
    (SELECT COUNT(*) FROM ethics.aml_alerts WHERE is_deleted=0) AS alerts;
GO
