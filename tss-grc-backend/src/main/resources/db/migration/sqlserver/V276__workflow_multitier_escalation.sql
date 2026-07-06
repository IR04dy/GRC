-- V276 (WF-10): multi-tier escalation for workflow requests (§2.3 #6 / §2.5).
--
-- Today a request has a single escalation target (escalation_role / escalation_user_id). WF-10
-- adds an ordered ESCALATION LADDER: escalation_tiers is a JSON array
--   [{"level":1,"role":"LINE_MANAGER","afterDays":0},
--    {"level":2,"role":"CISO","afterDays":2},
--    {"level":3,"userId":42,"afterDays":5}]
-- and escalation_level tracks how far up the ladder a request has climbed. The SLA sweep advances
-- to the highest tier whose afterDays threshold has elapsed since the due date, notifying each
-- newly-reached tier exactly once; manual /escalate steps up one tier. Requests with no tiers keep
-- the existing single-tier behaviour unchanged (escalation_tiers stays NULL).

IF COL_LENGTH('workflow.requests', 'escalation_level') IS NULL
    ALTER TABLE workflow.requests ADD escalation_level INT NOT NULL CONSTRAINT df_wfr_esc_level DEFAULT 0;
GO

IF COL_LENGTH('workflow.requests', 'escalation_tiers') IS NULL
    ALTER TABLE workflow.requests ADD escalation_tiers NVARCHAR(MAX) NULL;
GO
