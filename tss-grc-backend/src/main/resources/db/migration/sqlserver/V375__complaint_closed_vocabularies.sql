-- V375 — F2-183: the nine value-domain CHECK constraints `consumer` never had.
--
-- THE DEFECT. Measured 2026-08-11: the **entire `consumer` schema carried ZERO check constraints** —
-- not a subset like F2-153's 32, all of them. Every vocabulary column was a bare `NVARCHAR(n)` whose
-- allowed values lived only in prose, in three places at once with nothing keeping them in step:
-- `ComplaintService`'s javadoc, `Complaint`'s javadoc, and `lib/complaints.ts`'s TypeScript unions.
--
-- What that cost, in the order the harm gets worse:
--
--   `channel`  — written through a case-SENSITIVE `switch` whose default arm returned 'WEB', so
--                `channel: "email"` was stored as **WEB**. F2-179's collation trap: the column is
--                Latin1_General_100_CI_AS_SC_UTF8 and Java is not.
--   `severity` — the same shape with a 'MEDIUM' default, so `severity: "critical"` was stored as
--                **MEDIUM** on a SAMA-regulated complaint.
--   `customer_id_type` — no check of any kind; the request value was passed straight to the setter.
--   `category` — no check of any kind either, **on the anonymous public endpoint**, which is the only
--                path that creates a complaint. So arbitrary text was stored. And
--                `generateQuarterlyReport` groups this column into a case-SENSITIVE `HashMap` to pick
--                the `top_category` it reports to SAMA — so two spellings of one category became two
--                buckets, splitting the count and changing which category the regulator is told is
--                the most complained-about. That is the sharpest instance: the value is not merely
--                mis-stored, it changes a regulator-facing aggregate.
--
-- ⚠ MEASURED IN BOTH REAL DATABASES BEFORE WRITING THIS FILE (2026-08-11), because
-- `ADD CONSTRAINT` verifies the rows already present and a violation here would fail Flyway and block
-- the whole next jar. Unlike V364 — a no-op in both real databases because they already held its 32 —
-- every database lacks all nine of these, so this migration really does add them everywhere:
--
--     dev   TSS_GRC             12 complaints, 28 updates, 1 report — 0 violations, 0 bad casings
--     prod  TSS_GRC_Deployment  12 complaints, 28 updates, 1 report — 0 violations, 0 bad casings
--
-- So this is latent-defect closure, not a data-corruption repair: the defect is reachable only
-- through the API, which is exactly where machine callers live and nobody eyeballs the payload.
-- No `WITH NOCHECK` anywhere — SQL Server verifies the existing rows, which is the point.
--
-- ⚠ SCOPE IS TIED TO THE WRITERS, NOT TO THE COLUMNS. A CHECK on a column whose writer is still open
-- converts today's silently-stored junk into a constraint violation — on this table that means a
-- **lost complaint** on a SAMA-mandated intake channel, which is strictly worse than the defect. So
-- this file constrains exactly the columns whose writer the same commit closes:
--
--     closed by ClosedVocabulary.coerce      → channel, category, severity
--     closed by ClosedVocabulary.coerceOrNull → customer_id_type
--     already closed (literal + state machine) → status, update_type, from_status, to_status
--     derived from complaint.category          → report.top_category
--
-- `subcategory` and `root_cause_code` are deliberately NOT constrained: no vocabulary is declared for
-- them anywhere — not in the entity, not in V199's header, not in `lib/complaints.ts` — so there is
-- nothing to mirror, and inventing one here would be a product decision wearing a fix's clothes.
--
-- ⚠ THE CASING NORMALISATION IS NOT DECORATION AND THE CONSTRAINT CANNOT REPLACE IT. Because the
-- collation is case-INSENSITIVE, `CHECK (severity IN (N'CRITICAL', …))` **accepts** 'critical' and
-- stores it verbatim. So the constraint alone would leave the F2-179 defect intact while appearing to
-- guard it. The UPDATEs below are meaning-preserving by construction — they only touch values that
-- already match a vocabulary member case-insensitively — and they touch 0 rows in both measured
-- databases. A value that matches nothing is left alone and reported by the self-check below, with an
-- actionable message, rather than being coerced: silently rewriting somebody's regulatory record is
-- not this file's decision to make.
--
-- REVERSE RECIPE:
--   ALTER TABLE consumer.complaint                  DROP CONSTRAINT CK_complaint_channel;
--   ALTER TABLE consumer.complaint                  DROP CONSTRAINT CK_complaint_category;
--   ALTER TABLE consumer.complaint                  DROP CONSTRAINT CK_complaint_severity;
--   ALTER TABLE consumer.complaint                  DROP CONSTRAINT CK_complaint_status;
--   ALTER TABLE consumer.complaint                  DROP CONSTRAINT CK_complaint_id_type;
--   ALTER TABLE consumer.complaint_update           DROP CONSTRAINT CK_cmpl_update_type;
--   ALTER TABLE consumer.complaint_update           DROP CONSTRAINT CK_cmpl_update_from_status;
--   ALTER TABLE consumer.complaint_update           DROP CONSTRAINT CK_cmpl_update_to_status;
--   ALTER TABLE consumer.complaint_aggregate_report DROP CONSTRAINT CK_cmpl_report_top_category;
-- (The casing UPDATEs are not reversible and do not need to be: they replace a case-variant of a
--  legal token with the token itself.)

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. Normalise casing to the canonical form ─────────────────────────────────────────────────
-- `COLLATE Latin1_General_100_BIN2` is what makes the comparison see case at all; without it the
-- WHERE clause is always false and the statement is a silent no-op. Each UPDATE is scoped to values
-- that ARE a vocabulary member case-insensitively, so nothing un-interpretable is rewritten.

UPDATE consumer.complaint SET channel = UPPER(channel)
 WHERE channel IN (N'WEB', N'EMAIL', N'PHONE', N'BRANCH', N'SAMA_REFERRAL', N'SOCIAL_MEDIA')
   AND channel COLLATE Latin1_General_100_BIN2 <> UPPER(channel) COLLATE Latin1_General_100_BIN2;
GO

UPDATE consumer.complaint SET category = UPPER(category)
 WHERE category IN (N'ACCOUNT_ACCESS', N'CARDS_DISPUTES', N'ATM_ISSUES', N'TRANSFER_DELAYS',
                    N'FRAUD_UNAUTHORIZED_TXN', N'FEES_CHARGES', N'LOAN_FINANCING', N'INSURANCE',
                    N'INVESTMENT_PRODUCTS', N'DIGITAL_BANKING', N'BRANCH_SERVICE',
                    N'MISLEADING_INFORMATION', N'OTHER')
   AND category COLLATE Latin1_General_100_BIN2 <> UPPER(category) COLLATE Latin1_General_100_BIN2;
GO

UPDATE consumer.complaint SET severity = UPPER(severity)
 WHERE severity IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL')
   AND severity COLLATE Latin1_General_100_BIN2 <> UPPER(severity) COLLATE Latin1_General_100_BIN2;
GO

UPDATE consumer.complaint SET status = UPPER(status)
 WHERE status IN (N'RECEIVED', N'ACKNOWLEDGED', N'UNDER_INVESTIGATION', N'AWAITING_CUSTOMER',
                  N'RESOLVED', N'CLOSED', N'ESCALATED_TO_SAMA', N'REOPENED', N'REJECTED')
   AND status COLLATE Latin1_General_100_BIN2 <> UPPER(status) COLLATE Latin1_General_100_BIN2;
GO

UPDATE consumer.complaint SET customer_id_type = UPPER(customer_id_type)
 WHERE customer_id_type IN (N'NATIONAL_ID', N'IQAMA', N'PASSPORT', N'CR')
   AND customer_id_type COLLATE Latin1_General_100_BIN2
       <> UPPER(customer_id_type) COLLATE Latin1_General_100_BIN2;
GO

UPDATE consumer.complaint_update SET update_type = UPPER(update_type)
 WHERE update_type IN (N'STATUS_CHANGE', N'CUSTOMER_CONTACT', N'INTERNAL_NOTE',
                       N'EVIDENCE_REQUEST', N'SAMA_NOTIFICATION')
   AND update_type COLLATE Latin1_General_100_BIN2
       <> UPPER(update_type) COLLATE Latin1_General_100_BIN2;
GO

UPDATE consumer.complaint_update SET from_status = UPPER(from_status)
 WHERE from_status IN (N'RECEIVED', N'ACKNOWLEDGED', N'UNDER_INVESTIGATION', N'AWAITING_CUSTOMER',
                       N'RESOLVED', N'CLOSED', N'ESCALATED_TO_SAMA', N'REOPENED', N'REJECTED')
   AND from_status COLLATE Latin1_General_100_BIN2
       <> UPPER(from_status) COLLATE Latin1_General_100_BIN2;
GO

UPDATE consumer.complaint_update SET to_status = UPPER(to_status)
 WHERE to_status IN (N'RECEIVED', N'ACKNOWLEDGED', N'UNDER_INVESTIGATION', N'AWAITING_CUSTOMER',
                     N'RESOLVED', N'CLOSED', N'ESCALATED_TO_SAMA', N'REOPENED', N'REJECTED')
   AND to_status COLLATE Latin1_General_100_BIN2
       <> UPPER(to_status) COLLATE Latin1_General_100_BIN2;
GO

UPDATE consumer.complaint_aggregate_report SET top_category = UPPER(top_category)
 WHERE top_category IN (N'ACCOUNT_ACCESS', N'CARDS_DISPUTES', N'ATM_ISSUES', N'TRANSFER_DELAYS',
                        N'FRAUD_UNAUTHORIZED_TXN', N'FEES_CHARGES', N'LOAN_FINANCING', N'INSURANCE',
                        N'INVESTMENT_PRODUCTS', N'DIGITAL_BANKING', N'BRANCH_SERVICE',
                        N'MISLEADING_INFORMATION', N'OTHER')
   AND top_category COLLATE Latin1_General_100_BIN2
       <> UPPER(top_category) COLLATE Latin1_General_100_BIN2;
GO

-- ── 2. Pre-flight: name the un-interpretable values BEFORE ALTER TABLE does ────────────────────
-- ADD CONSTRAINT would fail on its own here, but with error 547 and no mention of which value in
-- which column — and F2-180 measured what that costs: 547 covers both FOREIGN KEY and CHECK, and the
-- handler's old "something it refers to does not exist" wording sent three investigations hunting a
-- foreign key. A wrong-but-specific diagnostic does not slow the reader down, it sends them
-- somewhere. So say it here, where the value is still in hand.
DECLARE @bad NVARCHAR(MAX);
SELECT @bad = STRING_AGG(CONVERT(NVARCHAR(MAX), src + N'=' + val), N', ')
  FROM (
    SELECT N'complaint.channel' src, channel val FROM consumer.complaint
     WHERE channel NOT IN (N'WEB', N'EMAIL', N'PHONE', N'BRANCH', N'SAMA_REFERRAL', N'SOCIAL_MEDIA')
    UNION ALL
    SELECT N'complaint.category', category FROM consumer.complaint
     WHERE category NOT IN (N'ACCOUNT_ACCESS', N'CARDS_DISPUTES', N'ATM_ISSUES', N'TRANSFER_DELAYS',
                            N'FRAUD_UNAUTHORIZED_TXN', N'FEES_CHARGES', N'LOAN_FINANCING',
                            N'INSURANCE', N'INVESTMENT_PRODUCTS', N'DIGITAL_BANKING',
                            N'BRANCH_SERVICE', N'MISLEADING_INFORMATION', N'OTHER')
    UNION ALL
    SELECT N'complaint.severity', severity FROM consumer.complaint
     WHERE severity NOT IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL')
    UNION ALL
    SELECT N'complaint.status', status FROM consumer.complaint
     WHERE status NOT IN (N'RECEIVED', N'ACKNOWLEDGED', N'UNDER_INVESTIGATION', N'AWAITING_CUSTOMER',
                          N'RESOLVED', N'CLOSED', N'ESCALATED_TO_SAMA', N'REOPENED', N'REJECTED')
    UNION ALL
    SELECT N'complaint.customer_id_type', customer_id_type FROM consumer.complaint
     WHERE customer_id_type IS NOT NULL
       AND customer_id_type NOT IN (N'NATIONAL_ID', N'IQAMA', N'PASSPORT', N'CR')
    UNION ALL
    SELECT N'complaint_update.update_type', update_type FROM consumer.complaint_update
     WHERE update_type NOT IN (N'STATUS_CHANGE', N'CUSTOMER_CONTACT', N'INTERNAL_NOTE',
                               N'EVIDENCE_REQUEST', N'SAMA_NOTIFICATION')
    UNION ALL
    SELECT N'complaint_update.from_status', from_status FROM consumer.complaint_update
     WHERE from_status IS NOT NULL
       AND from_status NOT IN (N'RECEIVED', N'ACKNOWLEDGED', N'UNDER_INVESTIGATION',
                               N'AWAITING_CUSTOMER', N'RESOLVED', N'CLOSED', N'ESCALATED_TO_SAMA',
                               N'REOPENED', N'REJECTED')
    UNION ALL
    SELECT N'complaint_update.to_status', to_status FROM consumer.complaint_update
     WHERE to_status IS NOT NULL
       AND to_status NOT IN (N'RECEIVED', N'ACKNOWLEDGED', N'UNDER_INVESTIGATION',
                             N'AWAITING_CUSTOMER', N'RESOLVED', N'CLOSED', N'ESCALATED_TO_SAMA',
                             N'REOPENED', N'REJECTED')
    UNION ALL
    SELECT N'report.top_category', top_category FROM consumer.complaint_aggregate_report
     WHERE top_category IS NOT NULL
       AND top_category NOT IN (N'ACCOUNT_ACCESS', N'CARDS_DISPUTES', N'ATM_ISSUES',
                                N'TRANSFER_DELAYS', N'FRAUD_UNAUTHORIZED_TXN', N'FEES_CHARGES',
                                N'LOAN_FINANCING', N'INSURANCE', N'INVESTMENT_PRODUCTS',
                                N'DIGITAL_BANKING', N'BRANCH_SERVICE', N'MISLEADING_INFORMATION',
                                N'OTHER')
  ) offenders;

IF @bad IS NOT NULL
BEGIN
    -- THROW takes a literal or a variable, never a concatenation — hence the assembly above.
    DECLARE @badMsg NVARCHAR(2048) =
        N'V375 stopped before adding any constraint: consumer.complaint holds values outside the '
      + N'declared vocabularies, and they are not case-variants of a legal token so this migration '
      + N'will not guess at them. Fix or reclassify these rows, then re-run — do NOT add WITH '
      + N'NOCHECK, which would leave the constraint in place and these rows unchecked. Offending '
      + N'values: ' + LEFT(@bad, 1200);
    THROW 50000, @badMsg, 1;
END
GO

-- ── 3. The nine constraints ───────────────────────────────────────────────────────────────────
-- Written as explicit `IS NULL OR … IN (…)` on the nullable columns. A bare `col IN (…)` would also
-- admit NULL — three-valued logic makes the CHECK pass on UNKNOWN — but relying on that makes a
-- reader work out whether NULL was intended or overlooked.

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'consumer' AND t.name = 'complaint'
                  AND cc.name = 'CK_complaint_channel')
    ALTER TABLE consumer.complaint ADD CONSTRAINT CK_complaint_channel
        CHECK (channel IN (N'WEB', N'EMAIL', N'PHONE', N'BRANCH', N'SAMA_REFERRAL',
                           N'SOCIAL_MEDIA'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'consumer' AND t.name = 'complaint'
                  AND cc.name = 'CK_complaint_category')
    ALTER TABLE consumer.complaint ADD CONSTRAINT CK_complaint_category
        CHECK (category IN (N'ACCOUNT_ACCESS', N'CARDS_DISPUTES', N'ATM_ISSUES', N'TRANSFER_DELAYS',
                            N'FRAUD_UNAUTHORIZED_TXN', N'FEES_CHARGES', N'LOAN_FINANCING',
                            N'INSURANCE', N'INVESTMENT_PRODUCTS', N'DIGITAL_BANKING',
                            N'BRANCH_SERVICE', N'MISLEADING_INFORMATION', N'OTHER'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'consumer' AND t.name = 'complaint'
                  AND cc.name = 'CK_complaint_severity')
    ALTER TABLE consumer.complaint ADD CONSTRAINT CK_complaint_severity
        CHECK (severity IN (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'consumer' AND t.name = 'complaint'
                  AND cc.name = 'CK_complaint_status')
    ALTER TABLE consumer.complaint ADD CONSTRAINT CK_complaint_status
        CHECK (status IN (N'RECEIVED', N'ACKNOWLEDGED', N'UNDER_INVESTIGATION', N'AWAITING_CUSTOMER',
                          N'RESOLVED', N'CLOSED', N'ESCALATED_TO_SAMA', N'REOPENED', N'REJECTED'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'consumer' AND t.name = 'complaint'
                  AND cc.name = 'CK_complaint_id_type')
    ALTER TABLE consumer.complaint ADD CONSTRAINT CK_complaint_id_type
        CHECK (customer_id_type IS NULL
               OR customer_id_type IN (N'NATIONAL_ID', N'IQAMA', N'PASSPORT', N'CR'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'consumer' AND t.name = 'complaint_update'
                  AND cc.name = 'CK_cmpl_update_type')
    ALTER TABLE consumer.complaint_update ADD CONSTRAINT CK_cmpl_update_type
        CHECK (update_type IN (N'STATUS_CHANGE', N'CUSTOMER_CONTACT', N'INTERNAL_NOTE',
                               N'EVIDENCE_REQUEST', N'SAMA_NOTIFICATION'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'consumer' AND t.name = 'complaint_update'
                  AND cc.name = 'CK_cmpl_update_from_status')
    ALTER TABLE consumer.complaint_update ADD CONSTRAINT CK_cmpl_update_from_status
        CHECK (from_status IS NULL
               OR from_status IN (N'RECEIVED', N'ACKNOWLEDGED', N'UNDER_INVESTIGATION',
                                  N'AWAITING_CUSTOMER', N'RESOLVED', N'CLOSED',
                                  N'ESCALATED_TO_SAMA', N'REOPENED', N'REJECTED'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'consumer' AND t.name = 'complaint_update'
                  AND cc.name = 'CK_cmpl_update_to_status')
    ALTER TABLE consumer.complaint_update ADD CONSTRAINT CK_cmpl_update_to_status
        CHECK (to_status IS NULL
               OR to_status IN (N'RECEIVED', N'ACKNOWLEDGED', N'UNDER_INVESTIGATION',
                                N'AWAITING_CUSTOMER', N'RESOLVED', N'CLOSED',
                                N'ESCALATED_TO_SAMA', N'REOPENED', N'REJECTED'));
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'consumer' AND t.name = 'complaint_aggregate_report'
                  AND cc.name = 'CK_cmpl_report_top_category')
    ALTER TABLE consumer.complaint_aggregate_report ADD CONSTRAINT CK_cmpl_report_top_category
        CHECK (top_category IS NULL
               OR top_category IN (N'ACCOUNT_ACCESS', N'CARDS_DISPUTES', N'ATM_ISSUES',
                                   N'TRANSFER_DELAYS', N'FRAUD_UNAUTHORIZED_TXN', N'FEES_CHARGES',
                                   N'LOAN_FINANCING', N'INSURANCE', N'INVESTMENT_PRODUCTS',
                                   N'DIGITAL_BANKING', N'BRANCH_SERVICE',
                                   N'MISLEADING_INFORMATION', N'OTHER'));
GO

-- ── 4. Self-checks — the property, not the statement ──────────────────────────────────────────

DECLARE @present INT;
DECLARE @presentMsg NVARCHAR(400) =
    N'V375 failed: fewer than 9 of its CHECK constraints exist. consumer.complaint still accepts '
  + N'values no vocabulary in the platform contains, including on the anonymous public endpoint.';
SELECT @present = COUNT(*)
  FROM sys.check_constraints cc
  JOIN sys.tables t ON t.object_id = cc.parent_object_id
 WHERE t.schema_id = SCHEMA_ID('consumer')
   AND cc.is_system_named = 0
   AND cc.name IN ('CK_complaint_channel', 'CK_complaint_category', 'CK_complaint_severity',
                   'CK_complaint_status', 'CK_complaint_id_type', 'CK_cmpl_update_type',
                   'CK_cmpl_update_from_status', 'CK_cmpl_update_to_status',
                   'CK_cmpl_report_top_category');
IF @present < 9 THROW 50000, @presentMsg, 1;
GO

-- Every one must be TRUSTED. `ADD CONSTRAINT` verifies the rows already in the table and leaves
-- is_not_trusted = 0; `WITH NOCHECK` would leave a control that covers the next row and not the ones
-- already there. If a later edit adds NOCHECK to get past a violation, this fires rather than
-- letting the constraint look enforced.
DECLARE @untrusted INT;
DECLARE @untrustedMsg NVARCHAR(400) =
    N'V375 failed: one of its CHECK constraints is NOT TRUSTED, so the complaints already in the '
  + N'table were never verified against it. Fix the data, do not add WITH NOCHECK.';
SELECT @untrusted = COUNT(*)
  FROM sys.check_constraints cc
 WHERE cc.is_not_trusted = 1
   AND cc.name IN ('CK_complaint_channel', 'CK_complaint_category', 'CK_complaint_severity',
                   'CK_complaint_status', 'CK_complaint_id_type', 'CK_cmpl_update_type',
                   'CK_cmpl_update_from_status', 'CK_cmpl_update_to_status',
                   'CK_cmpl_report_top_category');
IF @untrusted > 0 THROW 50000, @untrustedMsg, 1;
GO

-- And none may be disabled — F2-62's dead-flag shape: in the catalogue, enforcing nothing.
DECLARE @disabled INT;
DECLARE @disabledMsg NVARCHAR(400) =
    N'V375 failed: one of its CHECK constraints is DISABLED. A disabled CHECK is in the catalogue '
  + N'and enforces nothing.';
SELECT @disabled = COUNT(*)
  FROM sys.check_constraints cc
 WHERE cc.is_disabled = 1
   AND cc.name IN ('CK_complaint_channel', 'CK_complaint_category', 'CK_complaint_severity',
                   'CK_complaint_status', 'CK_complaint_id_type', 'CK_cmpl_update_type',
                   'CK_cmpl_update_from_status', 'CK_cmpl_update_to_status',
                   'CK_cmpl_report_top_category');
IF @disabled > 0 THROW 50000, @disabledMsg, 1;
GO

-- And the casing must now be canonical everywhere. This is the assertion the CHECK constraints
-- above CANNOT make: the collation is case-insensitive, so they accept 'critical' and would leave
-- the F2-179 defect in place while appearing to guard it.
DECLARE @lower INT;
DECLARE @lowerMsg NVARCHAR(400) =
    N'V375 failed: a vocabulary column still holds a non-upper-case token. The CHECK constraints '
  + N'cannot see this — the collation is case-insensitive — so Java''s equals() and the frontend''s '
  + N'label maps would keep missing the row. Normalise it.';
SELECT @lower =
    (SELECT COUNT(*) FROM consumer.complaint
      WHERE channel  COLLATE Latin1_General_100_BIN2 <> UPPER(channel)  COLLATE Latin1_General_100_BIN2
         OR category COLLATE Latin1_General_100_BIN2 <> UPPER(category) COLLATE Latin1_General_100_BIN2
         OR severity COLLATE Latin1_General_100_BIN2 <> UPPER(severity) COLLATE Latin1_General_100_BIN2
         OR status   COLLATE Latin1_General_100_BIN2 <> UPPER(status)   COLLATE Latin1_General_100_BIN2
         OR (customer_id_type IS NOT NULL
             AND customer_id_type COLLATE Latin1_General_100_BIN2
                 <> UPPER(customer_id_type) COLLATE Latin1_General_100_BIN2))
  + (SELECT COUNT(*) FROM consumer.complaint_update
      WHERE update_type COLLATE Latin1_General_100_BIN2
                <> UPPER(update_type) COLLATE Latin1_General_100_BIN2
         OR (from_status IS NOT NULL AND from_status COLLATE Latin1_General_100_BIN2
                <> UPPER(from_status) COLLATE Latin1_General_100_BIN2)
         OR (to_status IS NOT NULL AND to_status COLLATE Latin1_General_100_BIN2
                <> UPPER(to_status) COLLATE Latin1_General_100_BIN2))
  + (SELECT COUNT(*) FROM consumer.complaint_aggregate_report
      WHERE top_category IS NOT NULL AND top_category COLLATE Latin1_General_100_BIN2
                <> UPPER(top_category) COLLATE Latin1_General_100_BIN2);
IF @lower > 0 THROW 50000, @lowerMsg, 1;
GO
