-- V300 — CH-2 / CM-H1: enum integrity for change_mgmt.change_requests.
-- The change module had ZERO CHECK constraints, so status/change_type/risk_level/
-- outcome accepted any free-text (a client could persist status='ZZZ' or
-- change_type='MADE_UP', which then vanished from every status-keyed view).
-- These constraints mirror the enums enforced in ChangeManagementService
-- (NORMAL_TRANSITIONS states, change types, risk tiers, outcomes). Nullable
-- columns (risk_level, outcome) permit NULL. Idempotent; applied to dev twice
-- via pyodbc (Flyway off in dev), prod via Flyway.

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_change_status')
BEGIN
    ALTER TABLE change_mgmt.change_requests WITH CHECK ADD CONSTRAINT ck_change_status
        CHECK (status IN (
            'DRAFT','SUBMITTED','IMPACT_ASSESSED','CAB_PENDING','CAB_APPROVED',
            'CAB_REJECTED','EMERGENCY_APPROVED','SCHEDULED','IMPLEMENTING',
            'IMPLEMENTED','ROLLED_BACK','POST_REVIEW','CLOSED','CANCELLED'));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_change_type')
BEGIN
    ALTER TABLE change_mgmt.change_requests WITH CHECK ADD CONSTRAINT ck_change_type
        CHECK (change_type IN ('STANDARD','NORMAL','EMERGENCY'));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_change_risk')
BEGIN
    ALTER TABLE change_mgmt.change_requests WITH CHECK ADD CONSTRAINT ck_change_risk
        CHECK (risk_level IS NULL OR risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL'));
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_change_outcome')
BEGIN
    ALTER TABLE change_mgmt.change_requests WITH CHECK ADD CONSTRAINT ck_change_outcome
        CHECK (outcome IS NULL OR outcome IN ('SUCCESS','PARTIAL_SUCCESS','FAILED','ROLLED_BACK'));
END
GO
