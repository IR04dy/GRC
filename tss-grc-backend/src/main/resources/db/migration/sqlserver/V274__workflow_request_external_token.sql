-- V274 (WF-7): tokenized public access for EXTERNAL workflow-request recipients (§7 #18).
--
-- An EXTERNAL recipient (recipientType='EXTERNAL', recipientKey = contact email) has no
-- platform login, so on send it is minted a crypto-random opaque access token. That token is
-- the ONLY credential for a minimal public tracker (mirrors the intake / whistleblower / TPRM
-- BCP-attestation by-token pattern already in SecurityConfig + PublicEndpointRateLimitFilter):
--   GET  /api/public/workflow-requests/{token}          -> minimal projection (no internal ids)
--   POST /api/public/workflow-requests/{token}/respond  -> external acknowledgement
--
-- Token is unique; expiry defaults to the request due date + a grace window (set in the service).

IF COL_LENGTH('workflow.request_recipients', 'access_token') IS NULL
    ALTER TABLE workflow.request_recipients ADD access_token NVARCHAR(64) NULL;

IF COL_LENGTH('workflow.request_recipients', 'token_expires_at') IS NULL
    ALTER TABLE workflow.request_recipients ADD token_expires_at DATETIME2 NULL;
GO

-- Unique filtered index: at most one row per token, only where a token exists.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_wrr_access_token')
    CREATE UNIQUE INDEX uq_wrr_access_token
        ON workflow.request_recipients (access_token)
        WHERE access_token IS NOT NULL;
GO
