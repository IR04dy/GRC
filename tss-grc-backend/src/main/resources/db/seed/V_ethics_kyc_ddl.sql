-- ============================================================================
-- TSS GRC — Phase B-3: Sanctions / PEP screening + CDD/EDD records
-- ============================================================================
-- Saudi banks + capital-markets entities are required by SAMA AML §3 and
-- CMA AML rules to perform CDD on every customer + EDD on high-risk +
-- continuous PEP/sanctions screening (OFAC, UN, SAMA local, EU).
--
-- Two tables:
--  * ethics.kyc_subjects   — the customer / counter-party / employee being
--    screened. Re-uses TPRM vendors via subject_link_type+subject_link_id
--    for vendor screening; otherwise stores the identity inline.
--  * ethics.screening_hits — match results from a screening run (sanctions
--    list, PEP list, adverse media). Each hit has a disposition (TRUE_MATCH,
--    FALSE_POSITIVE, NEEDS_REVIEW) and SAR (Suspicious Activity Report) flag.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

-- ── ethics.kyc_subjects ────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='ethics' AND t.name='kyc_subjects')
BEGIN
    CREATE TABLE ethics.kyc_subjects (
        id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_kyc_subj_extid DEFAULT NEWID(),
        organisation_id       BIGINT NOT NULL,
        subject_code          NVARCHAR(50) NULL,
        subject_type          NVARCHAR(30) NOT NULL,    -- INDIVIDUAL | LEGAL_ENTITY
        full_name             NVARCHAR(500) NOT NULL,
        full_name_ar          NVARCHAR(500) NULL,
        nationality           NVARCHAR(3) NULL,         -- ISO 3166-1 alpha-3
        country_of_residence  NVARCHAR(3) NULL,
        date_of_birth         DATE NULL,
        national_id           NVARCHAR(50) NULL,        -- masked / hashed in prod
        commercial_registration NVARCHAR(50) NULL,      -- for legal entities
        -- Optional link back to a TPRM vendor / employee record
        subject_link_type     NVARCHAR(30) NULL,        -- VENDOR | EMPLOYEE | CUSTOMER | OTHER
        subject_link_id       BIGINT NULL,
        -- Risk classification
        risk_rating           NVARCHAR(20) NOT NULL CONSTRAINT DF_kyc_subj_risk DEFAULT N'STANDARD',
        -- LOW | STANDARD | HIGH | PROHIBITED
        is_pep                BIT NOT NULL CONSTRAINT DF_kyc_subj_pep DEFAULT 0,
        is_sanctioned         BIT NOT NULL CONSTRAINT DF_kyc_subj_san DEFAULT 0,
        cdd_level             NVARCHAR(20) NOT NULL CONSTRAINT DF_kyc_subj_cdd DEFAULT N'STANDARD',
        -- SIMPLIFIED | STANDARD | ENHANCED
        cdd_completed_at      DATETIME2 NULL,
        edd_required          BIT NOT NULL CONSTRAINT DF_kyc_subj_eddr DEFAULT 0,
        edd_completed_at      DATETIME2 NULL,
        last_screened_at      DATETIME2 NULL,
        next_review_date      DATE NULL,
        status                NVARCHAR(30) NOT NULL CONSTRAINT DF_kyc_subj_status DEFAULT N'PENDING',
        -- PENDING | APPROVED | REJECTED | UNDER_REVIEW | OFFBOARDED
        framework_id          BIGINT NULL,
        notes                 NVARCHAR(MAX) NULL,
        is_deleted            BIT NOT NULL CONSTRAINT DF_kyc_subj_isdel DEFAULT 0,
        created_at            DATETIME2 NOT NULL CONSTRAINT DF_kyc_subj_created DEFAULT SYSUTCDATETIME(),
        updated_at            DATETIME2 NULL,
        created_by            BIGINT NULL,
        updated_by            BIGINT NULL,
        CONSTRAINT FK_kyc_subj_framework FOREIGN KEY (framework_id) REFERENCES compliance.frameworks(id)
    );
    CREATE INDEX IX_kyc_subj_org_status ON ethics.kyc_subjects(organisation_id, status) WHERE is_deleted = 0;
    CREATE INDEX IX_kyc_subj_org_risk   ON ethics.kyc_subjects(organisation_id, risk_rating) WHERE is_deleted = 0;
    PRINT N'Created ethics.kyc_subjects';
END
ELSE PRINT N'ethics.kyc_subjects already exists';

-- ── ethics.screening_hits ───────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
                WHERE s.name='ethics' AND t.name='screening_hits')
BEGIN
    CREATE TABLE ethics.screening_hits (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        subject_id      BIGINT NOT NULL,
        list_source     NVARCHAR(60) NOT NULL,        -- OFAC_SDN | UN_CONSOLIDATED | EU_CONSOLIDATED | SAMA_LOCAL | INTERPOL | PEP_GLOBAL | ADVERSE_MEDIA
        list_entry_ref  NVARCHAR(100) NULL,
        match_score     INT NOT NULL,                  -- 0–100
        match_summary   NVARCHAR(MAX) NULL,
        screened_at     DATETIME2 NOT NULL CONSTRAINT DF_kyc_hit_at DEFAULT SYSUTCDATETIME(),
        disposition     NVARCHAR(30) NOT NULL CONSTRAINT DF_kyc_hit_disp DEFAULT N'NEEDS_REVIEW',
        -- TRUE_MATCH | FALSE_POSITIVE | NEEDS_REVIEW | ESCALATED
        disposition_notes NVARCHAR(MAX) NULL,
        decided_by_name NVARCHAR(255) NULL,
        decided_at      DATETIME2 NULL,
        sar_filed       BIT NOT NULL CONSTRAINT DF_kyc_hit_sar DEFAULT 0,
        sar_reference   NVARCHAR(100) NULL,
        CONSTRAINT FK_kyc_hit_subj FOREIGN KEY (subject_id) REFERENCES ethics.kyc_subjects(id)
    );
    CREATE INDEX IX_kyc_hit_subj ON ethics.screening_hits(subject_id, disposition);
    PRINT N'Created ethics.screening_hits';
END
ELSE PRINT N'ethics.screening_hits already exists';

-- ── Seed: 4 representative subjects ─────────────────────────────────────────
DECLARE @org BIGINT = 1;
DECLARE @fSAMA BIGINT = (SELECT id FROM compliance.frameworks WHERE code='SAMA_AML');
DECLARE @fCMA  BIGINT = (SELECT id FROM compliance.frameworks WHERE code='CMA_AML');
DECLARE @fFATF BIGINT = (SELECT id FROM compliance.frameworks WHERE code='FATF_40');

INSERT INTO ethics.kyc_subjects
  (organisation_id, subject_code, subject_type, full_name, full_name_ar,
   nationality, country_of_residence, national_id, commercial_registration,
   subject_link_type, risk_rating, is_pep, is_sanctioned, cdd_level,
   cdd_completed_at, edd_required, edd_completed_at, last_screened_at,
   next_review_date, status, framework_id, notes)
SELECT @org, v.code, v.t, v.fn, v.fnAr, v.nat, v.cor, v.nid, v.cr,
       v.lt, v.risk, v.pep, v.san, v.cdd,
       v.cddDone, v.eddReq, v.eddDone, v.scr,
       v.nrev, v.st, v.fw, v.notes
FROM (VALUES
  (N'KYC-IND-0001', N'INDIVIDUAL',
   N'Mohammed Al-Otaibi', N'محمد العتيبي',
   N'SAU', N'SAU', N'1078123456', NULL,
   N'CUSTOMER', N'STANDARD', 0, 0, N'STANDARD',
   '2025-02-12 10:00', 0, NULL, '2025-04-28 03:00',
   '2026-02-12', N'APPROVED', @fSAMA,
   N'Standard retail customer. CDD passed; no sanctions/PEP hits in last screen.'),
  (N'KYC-IND-0002', N'INDIVIDUAL',
   N'Khaled Bin Talal Al-Qassem', N'خالد بن طلال القاسم',
   N'SAU', N'SAU', N'1099887766', NULL,
   N'CUSTOMER', N'HIGH', 1, 0, N'ENHANCED',
   '2025-03-04 09:00', 1, '2025-03-18 13:00', '2025-04-29 03:00',
   '2025-09-04', N'APPROVED', @fSAMA,
   N'Domestic PEP — sitting member of municipal council. EDD completed; source-of-funds documented.'),
  (N'KYC-LE-0001', N'LEGAL_ENTITY',
   N'BlueWave Trading Co. (KSA)', N'شركة بلو ويف للتجارة',
   N'SAU', N'SAU', NULL, N'1010234567',
   N'VENDOR', N'STANDARD', 0, 0, N'STANDARD',
   '2025-01-22 11:00', 0, NULL, '2025-04-28 03:00',
   '2026-01-22', N'APPROVED', @fCMA,
   N'Active supplier; UBOs verified; commercial registration valid.'),
  (N'KYC-LE-0002', N'LEGAL_ENTITY',
   N'OffshoreCapital Holdings Ltd', N'أوف شور كابيتال هولدنغز',
   N'GBR', N'CYM', NULL, NULL,
   N'CUSTOMER', N'PROHIBITED', 0, 1, N'ENHANCED',
   NULL, 1, NULL, '2025-04-29 02:00',
   NULL, N'REJECTED', @fFATF,
   N'Two adverse-media hits + one sanctions match (UN consolidated). Onboarding rejected; STR filed with SAFIU.')
) AS v(code, t, fn, fnAr, nat, cor, nid, cr, lt, risk, pep, san, cdd, cddDone, eddReq, eddDone, scr, nrev, st, fw, notes)
WHERE NOT EXISTS (SELECT 1 FROM ethics.kyc_subjects s WHERE s.organisation_id=@org AND s.subject_code=v.code AND s.is_deleted=0);

PRINT N'Seeded 4 sample KYC subjects.';

-- ── Seed: screening hits for the prohibited entity ─────────────────────────
DECLARE @prohibitedId BIGINT = (SELECT TOP 1 id FROM ethics.kyc_subjects
                                WHERE subject_code='KYC-LE-0002' AND is_deleted=0);
IF @prohibitedId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ethics.screening_hits WHERE subject_id=@prohibitedId)
BEGIN
    INSERT INTO ethics.screening_hits
      (subject_id, list_source, list_entry_ref, match_score, match_summary,
       disposition, disposition_notes, decided_by_name, decided_at,
       sar_filed, sar_reference)
    VALUES
      (@prohibitedId, N'UN_CONSOLIDATED', N'UN-1.234',  98,
       N'Strong name match against UN sanctions consolidated list — beneficial owner Y.X.',
       N'TRUE_MATCH', N'Confirmed by trade-finance compliance. Subject UBO is on UN list.',
       N'Head of FCC', '2025-04-29 04:00', 1, N'STR-2025-0042'),
      (@prohibitedId, N'ADVERSE_MEDIA', NULL, 72,
       N'Adverse-media articles linking entity to FATF grey-list jurisdiction.',
       N'TRUE_MATCH', N'Material adverse media corroborated.',
       N'Head of FCC', '2025-04-29 04:30', 0, NULL);
    PRINT N'Seeded sample screening hits.';
END

SELECT
    N'Phase B-3 KYC + screening DDL complete' AS status,
    (SELECT COUNT(*) FROM ethics.kyc_subjects WHERE is_deleted=0) AS subjects,
    (SELECT COUNT(*) FROM ethics.screening_hits)                  AS hits;
GO
