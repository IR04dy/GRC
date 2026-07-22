-- V303 — CH-11: first-class change-plan + post-implementation-review (PIR) objects.
-- Change requests already carried rollback_plan + test_plan + impact_summary +
-- business_justification, but had NO first-class implementation plan and only a
-- bare post_review_date + one-word outcome for the retrospective. ITIL v4 Change
-- Enablement expects an implementation plan alongside the test/rollback plans, and
-- a structured PIR (what happened, were objectives met, lessons, follow-up, who
-- reviewed and when). These columns back the ChangeRequest entity fields and are
-- captured at POST_REVIEW / editable via PUT. All nullable — no backfill needed.
-- Idempotent; applied to dev twice via pyodbc (Flyway off in dev), prod via Flyway.

IF COL_LENGTH('change_mgmt.change_requests', 'implementation_plan') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD implementation_plan NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('change_mgmt.change_requests', 'pir_summary') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD pir_summary NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('change_mgmt.change_requests', 'pir_objectives_met') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD pir_objectives_met BIT NULL;
GO

IF COL_LENGTH('change_mgmt.change_requests', 'pir_lessons') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD pir_lessons NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('change_mgmt.change_requests', 'pir_follow_up') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD pir_follow_up NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('change_mgmt.change_requests', 'pir_reviewed_by') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD pir_reviewed_by NVARCHAR(255) NULL;
GO

IF COL_LENGTH('change_mgmt.change_requests', 'pir_reviewed_at') IS NULL
    ALTER TABLE change_mgmt.change_requests ADD pir_reviewed_at DATETIME2 NULL;
GO
