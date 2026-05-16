-- V69 — NDI Compliance Assessment (slice 4)
--
-- Per-cycle responses to COMPLIANCE framework items. One row per
-- (cycle, framework_item). Captures the compliance status, evidence,
-- gap linkage, and the full control-owner → reviewer → approver chain.
--
-- Status set (6 values from the spec):
--   COMPLIANT           ملتزم
--   PARTIAL             ملتزم جزئياً
--   NON_COMPLIANT       غير ملتزم
--   NOT_APPLICABLE      غير منطبق  (requires justification)
--   EVIDENCE_REQUIRED   يتطلب دليلاً (Rule 3 auto-tag when COMPLIANT/PARTIAL with no evidence)
--   UNDER_REVIEW        تحت المراجعة
--
-- Service rules:
--   Rule 3 — COMPLIANT status requires evidence_ref unless an exception
--            is approved. Service refuses transition without it
--            (auto-flips to EVIDENCE_REQUIRED on save).
--   Rule 4 — PARTIAL or NON_COMPLIANT auto-sets needs_gap=1 so the
--            slice-8 gap workflow can create a corresponding gap.
--   Rule 5 — NOT_APPLICABLE requires na_justification text.
--
-- Lifecycle (separate from the status set above):
--   DRAFT → IN_PROGRESS → SUBMITTED → UNDER_REVIEW → APPROVED
--                                   → RETURNED → IN_PROGRESS (loop)

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = N'compliance_assessment' AND schema_id = SCHEMA_ID('ndi'))
BEGIN
    CREATE TABLE ndi.compliance_assessment (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT        NOT NULL,
        external_id                     NVARCHAR(64)  NULL,

        cycle_id                        BIGINT        NOT NULL,
        framework_item_id               BIGINT        NOT NULL,
        requirement_code                NVARCHAR(40)  NOT NULL,   -- denormalised from framework_item

        component                       NVARCHAR(30)  NOT NULL,   -- always COMPLIANCE
        domain                          NVARCHAR(80)  NOT NULL,
        subdomain                       NVARCHAR(80)  NULL,

        -- Compliance status (6 values from spec).
        compliance_status               NVARCHAR(30)  NOT NULL DEFAULT N'UNDER_REVIEW',
        compliance_score                DECIMAL(5,2)  NULL,        -- 0/50/100 from status × weight
        weight                          DECIMAL(5,2)  NOT NULL DEFAULT 1.00,

        response_text_en                NVARCHAR(MAX) NULL,
        response_text_ar                NVARCHAR(MAX) NULL,
        evidence_ref                    NVARCHAR(255) NULL,
        evidence_required               BIT           NOT NULL DEFAULT 0,
        evidence_status                 NVARCHAR(30)  NULL,        -- ATTACHED | EXPIRED | REJECTED

        -- Rule 4 — set by service when status flips to PARTIAL / NON_COMPLIANT.
        needs_gap                       BIT           NOT NULL DEFAULT 0,
        gap_id                          BIGINT        NULL,
        corrective_action_id            BIGINT        NULL,
        risk_id                         BIGINT        NULL,

        -- Rule 5 — required when status = NOT_APPLICABLE.
        na_justification                NVARCHAR(MAX) NULL,

        control_owner_name              NVARCHAR(255) NULL,
        control_owner_email             NVARCHAR(255) NULL,
        evidence_owner_name             NVARCHAR(255) NULL,
        evidence_owner_email            NVARCHAR(255) NULL,
        reviewer_name                   NVARCHAR(255) NULL,
        reviewer_email                  NVARCHAR(255) NULL,
        approver_name                   NVARCHAR(255) NULL,

        -- Lifecycle status — separate from compliance_status.
        lifecycle_status                NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
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

        CONSTRAINT ck_ndi_comp_status CHECK (compliance_status IN
            (N'COMPLIANT', N'PARTIAL', N'NON_COMPLIANT', N'NOT_APPLICABLE',
             N'EVIDENCE_REQUIRED', N'UNDER_REVIEW')),
        CONSTRAINT ck_ndi_comp_lifecycle CHECK (lifecycle_status IN
            (N'DRAFT', N'IN_PROGRESS', N'SUBMITTED', N'UNDER_REVIEW', N'RETURNED', N'APPROVED')),
        CONSTRAINT ck_ndi_comp_evidence_status CHECK (evidence_status IS NULL OR evidence_status IN
            (N'ATTACHED', N'EXPIRED', N'REJECTED')),
        CONSTRAINT ck_ndi_comp_weight CHECK (weight >= 0 AND weight <= 10)
    );

    CREATE UNIQUE INDEX uq_ndi_comp_cycle_item
        ON ndi.compliance_assessment(organisation_id, cycle_id, framework_item_id)
        WHERE is_deleted = 0;
    CREATE INDEX ix_ndi_comp_cycle_status ON ndi.compliance_assessment(cycle_id, compliance_status);
    CREATE INDEX ix_ndi_comp_cycle_domain ON ndi.compliance_assessment(cycle_id, domain);
END
GO
