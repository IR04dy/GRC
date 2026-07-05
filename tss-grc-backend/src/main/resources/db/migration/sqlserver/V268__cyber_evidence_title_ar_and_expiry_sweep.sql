-- V268 — CY-J: cyber evidence bilingual title + expiry-sweep bookkeeping
--
-- Two gaps this closes (last epic of the cyber/SOC remediation):
--   1. `cyber_evidence` had NO `title_ar` — an English-only island that broke the platform's
--      bilingual rule (every other cyber aggregate carries title_ar). Add it.
--   2. `expiry_date` was never swept. The CY-J scheduler flips expired evidence CURRENT/PENDING
--      → STALE and auto-raises a re-request; these columns record when it went stale and which
--      evidence-request was raised, so the sweep is idempotent + traceable.
--
-- Forward-only; idempotent. Hand-applied in dev via pyodbc (Flyway is off in dev).

IF COL_LENGTH('cyber.cyber_evidence', 'title_ar') IS NULL
    ALTER TABLE cyber.cyber_evidence ADD title_ar NVARCHAR(500) NULL;
GO

IF COL_LENGTH('cyber.cyber_evidence', 'stale_since') IS NULL
    ALTER TABLE cyber.cyber_evidence ADD stale_since DATE NULL;
GO

IF COL_LENGTH('cyber.cyber_evidence', 're_request_id') IS NULL
    ALTER TABLE cyber.cyber_evidence ADD re_request_id BIGINT NULL;
GO
