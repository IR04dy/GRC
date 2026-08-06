-- ERM remediation epic E-J — Risk review / reassessment cycle (beyond attestation).
--
-- Adds a scheduled-review cadence + stale flag to risk.risks, and a per-event
-- review log (risk.risk_reviews) capturing what each reassessment changed.
-- Attestation (risk.risk_attestations, §5.1 #6) is a separate, lighter owner
-- re-confirmation cycle and is intentionally left untouched.

-- ── Review-schedule fields on risk.risks ────────────────────────────────────
IF COL_LENGTH('risk.risks', 'review_cadence_days') IS NULL
    ALTER TABLE risk.risks
        ADD review_cadence_days INT NOT NULL
            CONSTRAINT DF_risks_review_cadence DEFAULT 365;
GO

IF COL_LENGTH('risk.risks', 'last_reviewed_at') IS NULL
    ALTER TABLE risk.risks ADD last_reviewed_at DATETIME2 NULL;
GO

-- CURRENT | OVERDUE — set OVERDUE by the daily stale sweep, reset to CURRENT on reassess.
IF COL_LENGTH('risk.risks', 'review_status') IS NULL
    ALTER TABLE risk.risks
        ADD review_status NVARCHAR(20) NOT NULL
            CONSTRAINT DF_risks_review_status DEFAULT 'CURRENT';
GO

-- ── Per-event review log ─────────────────────────────────────────────────────
IF NOT EXISTS (
    SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
    WHERE s.name = 'risk' AND t.name = 'risk_reviews')
CREATE TABLE risk.risk_reviews (
    id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
    organisation_id             BIGINT        NOT NULL,
    risk_id                     BIGINT        NOT NULL,
    reviewed_at                 DATETIME2     NOT NULL CONSTRAINT DF_risk_reviews_at DEFAULT SYSUTCDATETIME(),
    reviewed_by                 BIGINT        NULL,
    reviewed_by_name            NVARCHAR(200) NULL,
    -- CONFIRMED | RESCORED | ESCALATE | RETIRE_SUGGESTED
    decision                    NVARCHAR(30)  NOT NULL CONSTRAINT DF_risk_reviews_decision DEFAULT 'CONFIRMED',
    residual_likelihood_before  INT           NULL,
    residual_impact_before      INT           NULL,
    residual_likelihood_after   INT           NULL,
    residual_impact_after       INT           NULL,
    residual_score_snapshot     INT           NULL,
    risk_level_snapshot         NVARCHAR(20)  NULL,
    next_review_date            DATE          NULL,
    note_en                     NVARCHAR(MAX) NULL,
    note_ar                     NVARCHAR(MAX) NULL,
    is_deleted                  BIT           NOT NULL CONSTRAINT DF_risk_reviews_deleted DEFAULT 0,
    created_at                  DATETIME2     NOT NULL CONSTRAINT DF_risk_reviews_created DEFAULT SYSUTCDATETIME()
);
GO

IF OBJECT_ID('risk.risk_reviews') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_risk_reviews_org_risk' AND object_id = OBJECT_ID('risk.risk_reviews'))
    CREATE INDEX IX_risk_reviews_org_risk ON risk.risk_reviews (organisation_id, risk_id, reviewed_at DESC);
GO

-- ── Allow the new ARCHIVED terminal status (E-J approval-gated archive) ──────
IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'CK_risks_status' AND parent_object_id = OBJECT_ID('risk.risks'))
    ALTER TABLE risk.risks DROP CONSTRAINT CK_risks_status;
GO

ALTER TABLE risk.risks ADD CONSTRAINT CK_risks_status
    CHECK (status IN ('OPEN','UNDER_REVIEW','MITIGATING','ACCEPTED','CLOSED','ARCHIVED','TRANSFERRED'));
GO
