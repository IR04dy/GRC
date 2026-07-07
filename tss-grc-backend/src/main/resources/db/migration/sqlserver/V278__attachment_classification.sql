-- Forms remediation (D) — data classification for generic attachments.
-- The shared platform.attachments facility already carries doc_type, valid_to (expiry) and
-- a version chain; it lacked a data-classification label, so ad-hoc uploads were versioned +
-- expiring but not *classified*. Add a nullable NDMO-aligned classification
-- (PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED); legacy rows stay NULL = unclassified.
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('platform.attachments') AND name = 'classification'
)
BEGIN
    ALTER TABLE platform.attachments ADD classification NVARCHAR(30) NULL;
END;
