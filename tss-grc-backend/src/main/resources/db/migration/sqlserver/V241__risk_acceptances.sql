-- =====================================================================
-- V241 — Risk Acceptance workflow (ERM remediation epic E-A).
-- ---------------------------------------------------------------------
-- Until now "risk acceptance" was a bare status flip (RiskService.accept) +
-- a demo wizard that wrote to the generic workflow-submission inbox. No
-- structured record, no enforceable expiry, no approval matrix, no
-- revalidation. This adds:
--   (a) risk.risk_acceptances — the structured, time-boxed acceptance record
--       (justification, compensating controls, residual snapshot, expiry,
--        CRO/Board sign-off, status). Standard BaseEntity audit columns.
--   (b) the RISK_APPROVE permission + a role grant matrix (the approve
--       endpoint gates on hasPermission('RISK','APPROVE'); no such permission
--       existed, so it was admin-only). Mirrors V239 (BCM_APPROVE).
-- Idempotent: guarded by NOT EXISTS / IF NOT EXISTS.
-- =====================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = 'risk' AND t.name = 'risk_acceptances')
BEGIN
    CREATE TABLE risk.risk_acceptances (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        external_id                   UNIQUEIDENTIFIER NOT NULL CONSTRAINT df_risk_acc_extid DEFAULT NEWID(),
        organisation_id               BIGINT        NOT NULL,
        risk_id                       BIGINT        NOT NULL,
        justification_en              NVARCHAR(MAX) NOT NULL,
        justification_ar              NVARCHAR(MAX) NULL,
        compensating_controls_en      NVARCHAR(MAX) NULL,
        compensating_controls_ar      NVARCHAR(MAX) NULL,
        residual_score_at_acceptance  INT           NULL,
        appetite_ceiling              INT           NULL,
        requires_board                BIT           NOT NULL CONSTRAINT df_risk_acc_board   DEFAULT 0,
        expires_at                    DATE          NOT NULL,
        status                        NVARCHAR(20)  NOT NULL CONSTRAINT df_risk_acc_status  DEFAULT 'SUBMITTED',
        cro_signed_by                 NVARCHAR(200) NULL,
        board_signed_by               NVARCHAR(200) NULL,
        requested_by_id               BIGINT        NULL,
        approved_by                   NVARCHAR(120) NULL,
        approved_at                   DATETIME2     NULL,
        rejection_reason              NVARCHAR(MAX) NULL,
        created_at                    DATETIME2     NOT NULL CONSTRAINT df_risk_acc_created DEFAULT SYSUTCDATETIME(),
        updated_at                    DATETIME2     NULL,
        created_by                    BIGINT        NULL,
        updated_by                    BIGINT        NULL,
        is_deleted                    BIT           NOT NULL CONSTRAINT df_risk_acc_deleted DEFAULT 0,
        deleted_at                    DATETIME2     NULL,
        deleted_by                    BIGINT        NULL,
        row_version                   ROWVERSION
    );

    CREATE INDEX ix_risk_acc_risk
        ON risk.risk_acceptances (organisation_id, risk_id)
        WHERE is_deleted = 0;

    -- Expiry-sweep lookup: APPROVED acceptances past their expiry.
    CREATE INDEX ix_risk_acc_expiry
        ON risk.risk_acceptances (status, expires_at)
        WHERE is_deleted = 0;
END
GO

-- RISK_APPROVE permission (acceptance approval gate) — mirrors V239 (BCM_APPROVE).
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = 'RISK' AND action = 'APPROVE')
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('RISK', 'APPROVE', 'RISK_APPROVE', 'Approve risk acceptances / governance sign-off');
GO

;WITH grants(role_name, action) AS (
    SELECT v.role_name, v.action FROM (VALUES
        ('PLATFORM_ADMIN','APPROVE'),
        ('ORG_ADMIN','APPROVE'),
        ('GRC_ADMINISTRATOR','APPROVE'),
        ('RISK_MANAGER','APPROVE'),
        ('EXECUTIVE_REVIEWER','APPROVE')
    ) v(role_name, action)
)
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM grants g
JOIN iam.roles r       ON r.role_name = g.role_name
JOIN iam.permissions p ON p.module = 'RISK' AND p.action = g.action
WHERE NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
     WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO
