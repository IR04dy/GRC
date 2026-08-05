-- V358 — F2-113: the identity Track D's force-recusal has to compare, because today it compares a
-- team label to a person's name.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- THE DEFECT, MEASURED 2026-08-05 IN DEV
-- ════════════════════════════════════════════════════════════════════════════════════════
-- Track D bars a change's requester/owner from casting the deciding committee vote.
-- `ChangeManagementService.referToCommittee` builds a CSV from getRequestedByName() +
-- getChangeOwnerName() and passes it as `recusal_names`; `CommitteeResolutionService.recordVote`
-- matches that CSV with sameName(member, nm) against committee_member_terms.member_name.
--
-- A change's people are TEAM LABELS. A committee's members are PEOPLE.
--
--     change_requests.requested_by_id .... set on  2 of 17  (all 2 written by CE-15, from today)
--     change_requests.change_owner_id .... set on  0 of 17
--     requester labels resolving to a user ....  0 of 13   'AppSec', 'CIO Office', 'SRE' ...
--     owner labels resolving to a user ........  0 of 15   'IAM Team', 'CISO', 'CloudOps' ...
--     committee_member_terms member_name ...... 'Khalid Al-Rashidi', 'Noura Al-Zahrani' ...
--
-- The comparison cannot match, so the bar has never engaged for any change in either database.
-- This is the THIRD instance of F2-112's shape: a rule written correctly over data that cannot
-- satisfy it. The first two (the condition waiver, and the PIR/condition pair) closed at 7660b2f3.
--
-- ⚠ AND IT IS WORSE THAN A MISMATCH — THERE IS NOTHING IN THE COLUMN AT ALL:
--
--     live committee_referral rows .............. 4
--     ... carrying a non-empty recusal_names .... 0
--     CSV entries matching a seat on their own committee ... 0
--
-- Referrals #7 and #8 predate the recusal list. #86 and #87 were created from changes whose
-- requested_by_name/change_owner_name are both NULL, so the CSV builder produced blank -> NULL.
-- The gate is not weakly enforced; it is unarmed.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- WHY THE FIX IS POSSIBLE NOW AND WAS NOT YESTERDAY
-- ════════════════════════════════════════════════════════════════════════════════════════
-- Both sides of an id comparison exist for the first time:
--   (a) 7660b2f3 (CE-15) made ChangeManagementService.create capture requested_by_id, so a change
--       now knows who raised it -- 2 rows already, and every change raised from here on.
--   (b) b901faec seated the CAB and ECAB with committee_member_terms.member_user_id populated.
--       Dev now holds 42 active seats of which 13 are linked, including all 3 CAB seats
--       (committee 13) and all 3 ECAB seats (committee 12) -- the two the gate map resolves.
--
-- ════════════════════════════════════════════════════════════════════════════════════════
-- WHAT THIS MIGRATION DOES
-- ════════════════════════════════════════════════════════════════════════════════════════
-- One nullable column: admin.committee_referral.recusal_user_ids -- a CSV of platform user ids,
-- carried ALONGSIDE recusal_names rather than replacing it.
--
-- ⚠ recusal_names IS NOT DROPPED AND MUST NOT BE. 29 of dev's 42 active seats carry no
-- member_user_id, and committee_member_terms has a `no_platform_user` column precisely because an
-- external director legitimately never gets one. For those seats the name is the only comparable
-- thing there is, so the name comparison stays as the fallback and the id comparison is added on
-- top. A change that stops refusing something is not a fix -- the CE-15 lesson, from the side where
-- an existing test caught it (ChangeConditionLifecycleTest's legacy-fallback test).
--
-- ⚠ NO BACKFILL. The 4 live referrals carry no recusal data and there is no correct mapping from
-- 'CloudOps' to a user id; inventing one would fabricate the attribution this gate exists to check
-- (V357 took the same position, for the same reason). The uncovered population is reported instead:
-- GET /api/v1/changes/sod-coverage gains a COMMITTEE_VOTE_NOT_REQUESTER row counting both causes of
-- blindness -- referrals with no captured id, and committees with no linked seat.
--
-- NOTHING CHANGES ON DEPLOY. One nullable column, no default, no data written.
-- Idempotent: safe to run twice (verified by running it twice against dev via pyodbc).

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

-- ── the recused parties' platform identity ───────────────────────────────────────────────
-- nvarchar(200) against recusal_names' 500: this holds ids, not labels. Two bigints and a comma
-- is 41 characters at the theoretical maximum, so 200 is ~4x the widest realistic list while
-- still being small enough that a name accidentally written here would be visible as an anomaly
-- rather than absorbed silently.
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'admin' AND table_name = 'committee_referral'
                 AND column_name = 'recusal_user_ids')
BEGIN
    ALTER TABLE admin.committee_referral ADD recusal_user_ids NVARCHAR(200) NULL;
    PRINT 'V358: added committee_referral.recusal_user_ids';
END
ELSE
    PRINT 'V358: committee_referral.recusal_user_ids already present — skipping';
GO

-- ════════════════════════════════════════════════════════════════════════════════════════
-- SELF-CHECKS — each THROWs, so a half-applied migration cannot pass as applied
-- ════════════════════════════════════════════════════════════════════════════════════════

-- 1. the column exists, nullable, and wide enough to be worth writing to.
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'admin' AND table_name = 'committee_referral'
                 AND column_name = 'recusal_user_ids'
                 AND data_type = 'nvarchar' AND is_nullable = 'YES'
                 AND character_maximum_length >= 200)
    THROW 51358, 'V358 self-check 1: committee_referral.recusal_user_ids is missing, not nullable nvarchar, or narrower than 200', 1;
GO

-- 2. NO DEFAULT. A default recusal list would bar somebody nobody named.
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
           WHERE table_schema = 'admin' AND table_name = 'committee_referral'
             AND column_name = 'recusal_user_ids' AND column_default IS NOT NULL)
    THROW 51358, 'V358 self-check 2: recusal_user_ids must have no DEFAULT — a default recusal list bars somebody nobody named', 1;
GO

-- 3. NO ROW WAS WRITTEN. This migration adds capacity, never attribution.
IF EXISTS (SELECT 1 FROM admin.committee_referral WHERE recusal_user_ids IS NOT NULL)
    THROW 51358, 'V358 self-check 3: a recusal_user_ids was populated — this migration must write no attribution', 1;
GO

-- 4. ⚠ THE FALLBACK SIDE MUST SURVIVE. recusal_names is what recuses the 29 of 42 active seats
--    that carry no member_user_id. If a later migration drops it as "superseded", the gate
--    silently stops refusing every unlinked seat -- which is the whole failure mode F2-113 is an
--    instance of, re-introduced by the fix for it.
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'admin' AND table_name = 'committee_referral'
                 AND column_name = 'recusal_names')
    THROW 51358, 'V358 self-check 4: committee_referral.recusal_names is gone — unlinked committee seats have nothing left to compare and the recusal silently stops firing for them', 1;
GO

-- 5. the OTHER side of the id comparison must exist, or only names can ever match.
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'admin' AND table_name = 'committee_member_terms'
                 AND column_name = 'member_user_id')
    THROW 51358, 'V358 self-check 5: committee_member_terms.member_user_id is gone — the recusal has no id to compare against', 1;
GO

-- 6. and the change's side, which CE-15/V357 established.
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'change_mgmt' AND table_name = 'change_requests'
                 AND column_name = 'requested_by_id')
    THROW 51358, 'V358 self-check 6: change_requests.requested_by_id is gone — a referral has no requester id to carry', 1;
GO

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
               WHERE table_schema = 'change_mgmt' AND table_name = 'change_requests'
                 AND column_name = 'change_owner_id')
    THROW 51358, 'V358 self-check 7: change_requests.change_owner_id is gone — a referral has no owner id to carry', 1;
GO

PRINT 'V358 OK: recusal_user_ids present, no attribution written, the name fallback and both id sides intact';
