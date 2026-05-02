-- ============================================================================
-- TSS GRC — PDPL DPIA workflow (assessment §5.4 critical recommendation #3)
-- ============================================================================
-- Data Protection Impact Assessment per PDPL Implementing Regulations.
-- High-risk processing activities require a documented DPIA with DPO sign-off.
-- State machine: DRAFT → IN_REVIEW → APPROVED → EXPIRED, with REJECTED branch.
--
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name='privacy' AND t.name='dpia')
BEGIN
    CREATE TABLE privacy.dpia (
        id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
        external_id             UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_dpia_extid DEFAULT NEWID(),
        organisation_id         BIGINT NOT NULL,
        processing_activity_id  BIGINT NULL,                 -- optional FK to privacy.processing_activities
        title_en                NVARCHAR(500) NOT NULL,
        title_ar                NVARCHAR(500) NULL,
        trigger_reason          NVARCHAR(50) NOT NULL,        -- NEW_HIGH_RISK | MAJOR_CHANGE | REGULATORY_REQUEST | PERIODIC | SPECIAL_CATEGORY | CROSS_BORDER | AUTO_DECISION | LARGE_SCALE_MONITORING
        status                  NVARCHAR(30) NOT NULL CONSTRAINT DF_dpia_status DEFAULT 'DRAFT',  -- DRAFT|IN_REVIEW|APPROVED|REJECTED|EXPIRED
        overall_residual_risk   NVARCHAR(20) NULL,            -- CRITICAL|HIGH|MEDIUM|LOW
        dpo_id                  BIGINT NULL,
        assessor_id             BIGINT NULL,
        assessment_date         DATE NULL,
        next_review_date        DATE NULL,
        approved_at             DATETIME2 NULL,
        rejected_at             DATETIME2 NULL,
        rejection_reason        NVARCHAR(MAX) NULL,
        conclusion              NVARCHAR(MAX) NULL,
        is_deleted              BIT NOT NULL CONSTRAINT DF_dpia_isdel DEFAULT 0,
        created_at              DATETIME2 NOT NULL CONSTRAINT DF_dpia_created DEFAULT SYSUTCDATETIME(),
        updated_at              DATETIME2 NULL
    );
    CREATE INDEX IX_dpia_org ON privacy.dpia(organisation_id) WHERE is_deleted = 0;
    PRINT N'Created privacy.dpia';
END
ELSE PRINT N'privacy.dpia already exists';

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name='privacy' AND t.name='dpia_question')
BEGIN
    CREATE TABLE privacy.dpia_question (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        dpia_id         BIGINT NOT NULL,
        question_code   NVARCHAR(50) NULL,                   -- e.g. PDPL-7-1, GDPR-35-7-a, etc.
        question_en     NVARCHAR(MAX) NOT NULL,
        question_ar     NVARCHAR(MAX) NULL,
        answer          NVARCHAR(MAX) NULL,
        risk_level      NVARCHAR(20) NULL,                    -- CRITICAL|HIGH|MEDIUM|LOW|NA
        sort_order      INT NOT NULL CONSTRAINT DF_dpiaq_sort DEFAULT 0,
        CONSTRAINT FK_dpiaq_dpia FOREIGN KEY (dpia_id) REFERENCES privacy.dpia(id)
    );
    CREATE INDEX IX_dpiaq_dpia ON privacy.dpia_question(dpia_id);
    PRINT N'Created privacy.dpia_question';
END
ELSE PRINT N'privacy.dpia_question already exists';

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name='privacy' AND t.name='dpia_mitigation')
BEGIN
    CREATE TABLE privacy.dpia_mitigation (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        dpia_id         BIGINT NOT NULL,
        mitigation_en   NVARCHAR(MAX) NOT NULL,
        mitigation_ar   NVARCHAR(MAX) NULL,
        status          NVARCHAR(30) NOT NULL CONSTRAINT DF_dpiam_status DEFAULT 'PLANNED', -- PLANNED|IN_PROGRESS|IMPLEMENTED|VERIFIED
        owner_id        BIGINT NULL,
        target_date     DATE NULL,
        verified_at     DATETIME2 NULL,
        sort_order      INT NOT NULL CONSTRAINT DF_dpiam_sort DEFAULT 0,
        CONSTRAINT FK_dpiam_dpia FOREIGN KEY (dpia_id) REFERENCES privacy.dpia(id)
    );
    CREATE INDEX IX_dpiam_dpia ON privacy.dpia_mitigation(dpia_id);
    PRINT N'Created privacy.dpia_mitigation';
END
ELSE PRINT N'privacy.dpia_mitigation already exists';

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE s.name='privacy' AND t.name='dpia_review')
BEGIN
    CREATE TABLE privacy.dpia_review (
        id              BIGINT IDENTITY(1,1) PRIMARY KEY,
        dpia_id         BIGINT NOT NULL,
        reviewer_id     BIGINT NULL,
        reviewer_name   NVARCHAR(255) NULL,
        decision        NVARCHAR(30) NOT NULL,                -- APPROVE|REJECT|REQUEST_CHANGES
        comments        NVARCHAR(MAX) NULL,
        decided_at      DATETIME2 NOT NULL CONSTRAINT DF_dpiar_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_dpiar_dpia FOREIGN KEY (dpia_id) REFERENCES privacy.dpia(id)
    );
    CREATE INDEX IX_dpiar_dpia ON privacy.dpia_review(dpia_id);
    PRINT N'Created privacy.dpia_review';
END
ELSE PRINT N'privacy.dpia_review already exists';

-- ── Seed: 2 sample DPIAs to demonstrate the lifecycle ──────────────────────
DECLARE @org BIGINT = 1;

INSERT INTO privacy.dpia (organisation_id, title_en, title_ar, trigger_reason, status, overall_residual_risk, assessment_date, next_review_date, conclusion)
SELECT v.org, v.en, v.ar, v.trig, v.st, v.risk, v.adate, v.ndate, v.concl
FROM (VALUES
  (1, N'Customer Onboarding KYC Pipeline (Sehhaty integration)',
      N'منظومة قبول العملاء — التكامل مع تطبيق صحتي',
      N'SPECIAL_CATEGORY', N'APPROVED', N'MEDIUM',
      '2025-01-15', '2026-01-15',
      N'DPIA approved with mitigations. Health-data fields tagged as special category; access restricted to DPO + compliance role; retention 7 years per SAMA.'),
  (1, N'Employee Performance Analytics — automated scoring',
      N'تحليلات أداء الموظفين — التقييم الآلي',
      N'AUTO_DECISION', N'IN_REVIEW', N'HIGH',
      '2025-03-10', NULL,
      NULL)
) AS v(org, en, ar, trig, st, risk, adate, ndate, concl)
WHERE NOT EXISTS (SELECT 1 FROM privacy.dpia d WHERE d.organisation_id=v.org AND d.title_en=v.en AND d.is_deleted=0);

PRINT N'Seeded 2 sample DPIAs.';

-- ── Result summary ────────────────────────────────────────────────────────
SELECT
    N'PDPL DPIA DDL complete' AS status,
    (SELECT COUNT(*) FROM privacy.dpia WHERE is_deleted=0) AS dpias,
    (SELECT COUNT(*) FROM privacy.dpia_question)           AS questions,
    (SELECT COUNT(*) FROM privacy.dpia_mitigation)         AS mitigations,
    (SELECT COUNT(*) FROM privacy.dpia_review)             AS reviews;

GO
