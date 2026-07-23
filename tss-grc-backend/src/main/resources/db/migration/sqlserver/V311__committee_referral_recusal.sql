-- V311 (Track D, S2): recusal names on a referral. When a change/policy is
-- referred, its requester/author name(s) are captured here so the committee
-- vote can force-RECUSE them (SoD: the requester cannot cast the deciding vote).
-- Idempotent ALTER; no data change.

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('admin.committee_referral') AND name = 'recusal_names')
BEGIN
    ALTER TABLE admin.committee_referral ADD recusal_names NVARCHAR(500) NULL;
END
GO
