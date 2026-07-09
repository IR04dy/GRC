-- V281 (RBAC Access-Governance P1 — plan §6.2/§6.3): access requests (maker-checker).
-- A grant becomes effective ONLY after approval: PENDING -> APPROVED -> PROVISIONED
-- (row inserted into iam.user_roles with the validity window) or REJECTED.
-- Requester = created_by (auditing); approver must differ from requester AND from
-- the beneficiary (runtime SoD in the service). Expiry sweep hard-deletes the
-- user_roles join row when valid_to passes (the audit log + this table keep history).

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'access_requests' AND schema_id = SCHEMA_ID('iam'))
BEGIN
    CREATE TABLE iam.access_requests (
        id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id     BIGINT           NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        beneficiary_user_id BIGINT           NOT NULL,
        role_id             BIGINT           NOT NULL,
        valid_from          DATETIME2        NULL,
        valid_to            DATETIME2        NULL,     -- NULL = open-ended
        justification       NVARCHAR(1000)   NOT NULL,
        status              NVARCHAR(15)     NOT NULL DEFAULT 'PENDING',
        -- PENDING | APPROVED | PROVISIONED | REJECTED | REVOKED
        approved_by         BIGINT           NULL,
        decided_at          DATETIME2        NULL,
        rejection_reason    NVARCHAR(500)    NULL,
        provisioned_at      DATETIME2        NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NULL, created_by BIGINT NULL, updated_by BIGINT NULL,
        is_deleted BIT NOT NULL DEFAULT 0, deleted_at DATETIME2 NULL, deleted_by BIGINT NULL,
        row_version ROWVERSION
    );
    CREATE INDEX ix_accessreq_org_status ON iam.access_requests(organisation_id, status);
    CREATE INDEX ix_accessreq_beneficiary ON iam.access_requests(beneficiary_user_id);
END
