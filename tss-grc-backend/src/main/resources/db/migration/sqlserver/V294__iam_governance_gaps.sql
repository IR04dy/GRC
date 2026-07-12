-- V294 (RBAC governance completion — plan §0 remaining items 2–7):
--  a) access_review_campaigns.reviewer_user_id — optional reviewer scoping: when set,
--     only that reviewer (or a governance admin) may decide the campaign's items.
--  b) access_review_campaigns.scope_user_id — USER-scoped campaigns: the JML MOVER
--     hook opens a targeted mini-review of one user's grants (SAMA CSF 3.3.5 mover).
--  c) access_requests.engine_request_id — workflow-engine visibility wiring: every
--     access request also raises an anchored engine request (inbox/SLA/escalation),
--     responded/closed when the governance decision lands.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('iam.access_review_campaigns') AND name = 'reviewer_user_id')
    ALTER TABLE iam.access_review_campaigns ADD reviewer_user_id BIGINT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('iam.access_review_campaigns') AND name = 'scope_user_id')
    ALTER TABLE iam.access_review_campaigns ADD scope_user_id BIGINT NULL;

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('iam.access_requests') AND name = 'engine_request_id')
    ALTER TABLE iam.access_requests ADD engine_request_id BIGINT NULL;
