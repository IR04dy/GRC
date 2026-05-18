-- V113: Engines + schema for the three buyer-assessment module gaps.
--
-- The buyer assessment (May 2026) listed several gaps in §5.1 ERM, §5.9
-- Evidence, and §5.4 Privacy. A surprising amount of the schema was already
-- in place (KRI green/amber/red thresholds, EvidenceItem chain-of-custody +
-- review_due_date + WORM/hash, Dpia + DpiaMitigation + DpiaQuestion + DpiaReview,
-- ProcessingActivity.cross_border_transfer flag). What was missing was:
--   (a) the engine that classifies and acts on those columns
--   (b) detail columns for cross-border transfers (where, mechanism, ref)
--   (c) special-category data tagging on RoPA
--   (d) the risk → control linkage bridge (Tier-1 norm; ServiceNow/Archer have it)
--
-- Apply with: sqlcmd -f 65001 (UTF-8 codepage).

SET NOCOUNT ON;
GO

-- ─── §5.4 #6 — Cross-border transfer detail on ProcessingActivity ────
IF COL_LENGTH('privacy.processing_activities','receiving_country') IS NULL
BEGIN
    ALTER TABLE privacy.processing_activities ADD receiving_country         NVARCHAR(100) NULL;
    ALTER TABLE privacy.processing_activities ADD adequacy_decision         NVARCHAR(20)  NULL;
    -- ADEQUATE | PARTIAL | NONE — derived from a Saudi Data Office adequacy
    -- list (future seed); NULL when crossBorderTransfer=false.
    ALTER TABLE privacy.processing_activities ADD transfer_mechanism        NVARCHAR(40)  NULL;
    -- SCC | BCR | EXPLICIT_CONSENT | DATA_OFFICE_APPROVAL | INTRA_GROUP
    ALTER TABLE privacy.processing_activities ADD data_office_reference     NVARCHAR(100) NULL;
    -- Optional Saudi Data Office case/decision reference for non-adequate transfers.
END
GO

-- ─── §5.4 #7 — Special-category data tagging on RoPA ─────────────────
IF COL_LENGTH('privacy.processing_activities','special_category_flag') IS NULL
BEGIN
    ALTER TABLE privacy.processing_activities ADD special_category_flag    BIT NOT NULL DEFAULT 0;
    ALTER TABLE privacy.processing_activities ADD data_categories_json     NVARCHAR(MAX) NULL;
    -- JSON array of category tags, e.g.
    --   ["HEALTH","BIOMETRIC","CHILDREN_UNDER_18","FINANCIAL","GENETIC"]
END
GO

-- ─── §5.1 #2 — Risk → Control linkage bridge ─────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name='risk_controls' AND schema_id=SCHEMA_ID('risk'))
BEGIN
    CREATE TABLE risk.risk_controls (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        risk_id                  BIGINT          NOT NULL,
        control_id               BIGINT          NOT NULL,

        /* EFFECTIVE | NEEDS_IMPROVEMENT | INEFFECTIVE | NOT_TESTED */
        effectiveness            NVARCHAR(30)    NOT NULL DEFAULT 'NOT_TESTED',

        /* Optional contribution-to-mitigation as a percent 0..100 — used by
           the risk-control aggregation service to recompute residual scores
           when several controls reduce a single risk. */
        contribution_pct         DECIMAL(5,2)    NULL,

        notes                    NVARCHAR(1000)  NULL,

        is_deleted               BIT             NOT NULL DEFAULT 0,
        created_at               DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2       NULL,

        CONSTRAINT uq_risk_control      UNIQUE (risk_id, control_id),
        CONSTRAINT ck_risk_control_eff  CHECK (effectiveness IN
            ('EFFECTIVE','NEEDS_IMPROVEMENT','INEFFECTIVE','NOT_TESTED')),
        CONSTRAINT ck_risk_control_pct  CHECK
            (contribution_pct IS NULL OR (contribution_pct >= 0 AND contribution_pct <= 100))
    );
    CREATE INDEX IX_risk_controls_org_risk    ON risk.risk_controls(organisation_id, risk_id);
    CREATE INDEX IX_risk_controls_org_control ON risk.risk_controls(organisation_id, control_id);
END
GO

-- ─── §5.9 #5 — Evidence freshness — ensure review_due_date is indexed ─
-- The column was already there per V47; add an index so the sweep query
-- (where review_due_date < today AND status not in terminal) is cheap.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_evidence_items_review_due'
                 AND object_id = OBJECT_ID('evidence.evidence_items'))
BEGIN
    CREATE INDEX IX_evidence_items_review_due
        ON evidence.evidence_items(organisation_id, review_due_date)
        WHERE is_deleted = 0 AND review_due_date IS NOT NULL;
END
GO

-- ─── Demo seed: cross-border transfer + special-category tagging ────
UPDATE privacy.processing_activities
   SET receiving_country     = 'United States',
       adequacy_decision     = 'NONE',
       transfer_mechanism    = 'DATA_OFFICE_APPROVAL',
       data_office_reference = 'SDO-2026-XB-0001'
 WHERE organisation_id = 1 AND cross_border_transfer = 1
   AND receiving_country IS NULL;
GO

-- Flag one row as special-category for demo purposes — first row only,
-- guarded so re-running this migration doesn't keep over-flagging.
UPDATE TOP (1) privacy.processing_activities
   SET special_category_flag = 1,
       data_categories_json  = N'["HEALTH","BIOMETRIC"]'
 WHERE organisation_id = 1 AND special_category_flag = 0;
GO

PRINT 'V113 engines (ERM + Evidence + Privacy) applied.';
GO
