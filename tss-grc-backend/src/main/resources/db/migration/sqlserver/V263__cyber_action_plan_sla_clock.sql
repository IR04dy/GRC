-- V263 — CAPA/action-plan escalation clock (epic CY-E, finding "no CAPA reminder/escalation")
--
-- FINDING: cyber gap→CAPA conversion produced a cyber.cyber_action_plans row with
-- NO owner and NO due date, and nothing ever chased it — an overdue remediation
-- sat silent forever. CY-E routes owner+due at convert time (service layer) and
-- adds a reminder→overdue-escalation sweep. This migration adds the columns that
-- sweep needs to stay idempotent (escalate/remind once, not every tick).
--
-- PURELY ADDITIVE — three nullable/defaulted columns. Forward-only + idempotent
-- (guarded by COL_LENGTH). Dev is Flyway-off; hand-applied via pyodbc.

IF COL_LENGTH('cyber.cyber_action_plans', 'last_reminder_at') IS NULL
    ALTER TABLE cyber.cyber_action_plans ADD last_reminder_at DATETIME2 NULL;
GO

IF COL_LENGTH('cyber.cyber_action_plans', 'escalated_at') IS NULL
    ALTER TABLE cyber.cyber_action_plans ADD escalated_at DATETIME2 NULL;
GO

IF COL_LENGTH('cyber.cyber_action_plans', 'reminder_count') IS NULL
    ALTER TABLE cyber.cyber_action_plans ADD reminder_count INT NOT NULL CONSTRAINT DF_cap_reminder_count DEFAULT 0;
GO

-- Sweep predicate is (workflow_status not terminal AND due_date < today); a filtered
-- index keeps that scan cheap as the register grows.
IF OBJECT_ID('cyber.cyber_action_plans') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cap_due_open' AND object_id = OBJECT_ID('cyber.cyber_action_plans'))
    CREATE INDEX ix_cap_due_open
        ON cyber.cyber_action_plans (organisation_id, due_date)
        WHERE is_deleted = 0 AND due_date IS NOT NULL;
GO
