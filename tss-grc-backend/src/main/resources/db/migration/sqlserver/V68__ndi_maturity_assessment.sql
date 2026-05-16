-- V68 — NDI Maturity Assessment (slice 3)
--
-- Per-cycle responses to MATURITY framework items. One row per
-- (cycle, framework_item). Captures the assessor's current_level and
-- target_level (0..5 CMMI-style) plus the reviewer + approver chain.
--
-- Status machine (forward-only except RETURNED, which loops back):
--   DRAFT → IN_PROGRESS → SUBMITTED → UNDER_REVIEW
--                                   → RETURNED → IN_PROGRESS  (re-entrant)
--                                   → APPROVED   (terminal until reopened)
--
-- Rules (enforced in the service):
--   Rule 1 — target_level > current_level marks improvement_action_
--            suggested = 1 so the dashboard / improvement-plan tab
--            (slice 9) can surface candidates for the roadmap.
--   Rule 2 — current_level >= 4 requires evidence_ref. Service refuses
--            submit/approve without it.
--   D.Validation — target_level cannot be lower than current_level
--                  unless a justification is captured in justification.
--
-- Score = current_level * weight (weight comes from framework_item).
-- The aggregate endpoint normalises to 0..100 across the cycle so the
-- dashboard composite can consume it directly.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'maturity_assessment' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.maturity_assessment (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT        NOT NULL,
        external_id                     NVARCHAR(64)  NULL,

        cycle_id                        BIGINT        NOT NULL,
        framework_item_id               BIGINT        NOT NULL,

        -- Denormalised copies of (component, domain) from framework_item
        -- so the per-domain rollup query stays single-table.
        component                       NVARCHAR(30)  NOT NULL,    -- always MATURITY for now; reserved for future cross-component analytics
        domain                          NVARCHAR(80)  NOT NULL,
        subdomain                       NVARCHAR(80)  NULL,

        current_level                   TINYINT       NULL,        -- 0..5
        target_level                    TINYINT       NULL,        -- 0..5
        score                           DECIMAL(5,2)  NULL,        -- current_level * weight (recomputed on save)
        weight                          DECIMAL(5,2)  NOT NULL DEFAULT 1.00,

        response_text_en                NVARCHAR(MAX) NULL,
        response_text_ar                NVARCHAR(MAX) NULL,
        evidence_ref                    NVARCHAR(255) NULL,
        evidence_required               BIT           NOT NULL DEFAULT 0,

        -- Rule 1 flag — set by service when target_level > current_level.
        improvement_action_suggested    BIT           NOT NULL DEFAULT 0,
        -- D.Validation justification — required only when target < current.
        justification                   NVARCHAR(MAX) NULL,

        assessor_name                   NVARCHAR(255) NULL,
        assessor_email                  NVARCHAR(255) NULL,
        domain_owner_name               NVARCHAR(255) NULL,
        domain_owner_email              NVARCHAR(255) NULL,
        reviewer_name                   NVARCHAR(255) NULL,
        reviewer_email                  NVARCHAR(255) NULL,
        approver_name                   NVARCHAR(255) NULL,

        -- Cross-module pointers — no FK so slice 8 (gaps) + slice 9
        -- (improvement plan) can attach later without a migration here.
        gap_id                          BIGINT        NULL,
        improvement_action_id           BIGINT        NULL,

        status                          NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
        submitted_at                    DATETIME2     NULL,
        reviewed_at                     DATETIME2     NULL,
        approved_at                     DATETIME2     NULL,
        returned_at                     DATETIME2     NULL,
        return_reason                   NVARCHAR(MAX) NULL,

        notes                           NVARCHAR(MAX) NULL,

        is_deleted                      BIT           NOT NULL DEFAULT 0,
        created_at                      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                      NVARCHAR(100) NULL,
        updated_at                      DATETIME2     NULL,
        updated_by                      NVARCHAR(100) NULL,

        CONSTRAINT ck_ndi_mat_status CHECK (status IN
            (N'DRAFT', N'IN_PROGRESS', N'SUBMITTED', N'UNDER_REVIEW',
             N'RETURNED', N'APPROVED')),
        CONSTRAINT ck_ndi_mat_level_current CHECK (current_level IS NULL OR (current_level BETWEEN 0 AND 5)),
        CONSTRAINT ck_ndi_mat_level_target  CHECK (target_level  IS NULL OR (target_level  BETWEEN 0 AND 5)),
        CONSTRAINT ck_ndi_mat_weight        CHECK (weight >= 0 AND weight <= 10)
    );

    -- One assessment row per (cycle, framework_item).
    CREATE UNIQUE INDEX uq_ndi_mat_cycle_item
        ON ndi.maturity_assessment(organisation_id, cycle_id, framework_item_id)
        WHERE is_deleted = 0;
    CREATE INDEX ix_ndi_mat_cycle_status ON ndi.maturity_assessment(cycle_id, status);
    CREATE INDEX ix_ndi_mat_cycle_domain ON ndi.maturity_assessment(cycle_id, domain);
END
GO
