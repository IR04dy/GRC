-- V104: BCM tenant-level settings (audit Tier-1 #6).
--
-- Spec §R lists 13 configurable settings: BCM scoring model, criticality scale,
-- impact scale, risk matrix, RTO/RPO thresholds, exercise frequency, evidence
-- expiry rules, SLA rules, approval matrix, notification rules, language labels,
-- framework version, regulatory import status. Today none of these are
-- persisted — every consumer hardcodes its own defaults.
--
-- Strategy: single per-tenant row in bcm.settings with a JSON blob for the
-- compound shapes (matrix, approval matrix, notification rules) and typed
-- columns for the scalar settings the service-layer actually reads on the
-- hot path (RTO breach thresholds, exercise frequency, evidence expiry days,
-- CAPA overdue grace period). One row per tenant = no fan-out, easy cache.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'settings' AND schema_id = SCHEMA_ID('bcm'))
BEGIN
    CREATE TABLE bcm.settings (
        id                              BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id                 BIGINT          NOT NULL,

        -- Recovery-target thresholds in hours. A BIA with RTO > rto_warning_hours
        -- shows an amber warning on the dashboard; RTO > rto_critical_hours shows
        -- red. Defaults match SAMA OPRES expectation for retail-bank operations.
        rto_warning_hours               INT             NOT NULL DEFAULT 12,
        rto_critical_hours              INT             NOT NULL DEFAULT 24,
        rpo_warning_hours               INT             NOT NULL DEFAULT 4,
        rpo_critical_hours              INT             NOT NULL DEFAULT 12,

        -- Exercise frequency in months. BCPs whose last exercise is older than
        -- this threshold appear on the readiness dashboard as "exercise overdue".
        exercise_frequency_months_default INT           NOT NULL DEFAULT 12,
        exercise_frequency_months_critical INT          NOT NULL DEFAULT 6,

        -- Evidence expiry — number of days before expiry to start nagging.
        evidence_expiry_warning_days    INT             NOT NULL DEFAULT 60,

        -- CAPA grace period (days past due) before the scheduler escalates to BCM_MANAGER.
        capa_overdue_grace_days         INT             NOT NULL DEFAULT 7,

        -- Risk matrix shape (likelihood × impact). 5x5 by default; configurable
        -- so a tenant can drop to 4x4 or expand to 6x6 without code change.
        risk_matrix_likelihood_max      INT             NOT NULL DEFAULT 5,
        risk_matrix_impact_max          INT             NOT NULL DEFAULT 5,

        -- Approval matrix: who can approve what. Free-form JSON because
        -- different sectors have wildly different sign-off chains.
        --   {"BIA": ["BCM_MANAGER", "EXECUTIVE_SPONSOR"],
        --    "BCP": ["BCM_MANAGER"],
        --    "DRP": ["BCM_MANAGER", "IT_RECOVERY_OWNER"],
        --    "STRATEGY_HIGH_COST": ["EXECUTIVE_SPONSOR", "CFO"]}
        approval_matrix_json            NVARCHAR(MAX)   NULL,

        -- Notification rules: which events go to which roles + channels.
        --   {"BCM_CALL_TREE_FAILED": [{"role":"BCM_MANAGER","channel":"EMAIL"}], ...}
        notification_rules_json         NVARCHAR(MAX)   NULL,

        -- Framework version tracking — what version of ISO 22301 / SAMA OPRES /
        -- DGA / NCEMA controls is loaded for this tenant.
        iso_22301_version               NVARCHAR(20)    NULL,
        sama_opres_version              NVARCHAR(20)    NULL,
        dga_framework_version           NVARCHAR(20)    NULL,
        ncema_framework_version         NVARCHAR(20)    NULL,

        -- Regulatory import status. PLACEHOLDER until official content is loaded.
        regulatory_import_status        NVARCHAR(30)    NOT NULL DEFAULT 'PLACEHOLDER',
        -- PLACEHOLDER | PARTIAL | COMPLETE
        regulatory_import_notes         NVARCHAR(1000)  NULL,

        created_at                      DATETIME2       NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                      DATETIME2       NULL,
        created_by                      BIGINT          NULL,
        updated_by                      BIGINT          NULL,
        row_version                     ROWVERSION
    );
    -- One settings row per tenant. UNIQUE constraint stops drift.
    CREATE UNIQUE INDEX UX_bcm_settings_org ON bcm.settings(organisation_id);
END
GO
