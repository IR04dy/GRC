/* ============================================================================================
   V374 — bind an intake submission to the person who made it (F2-172, ADR-010 condition 2)
   ============================================================================================

   WHY THIS COLUMN EXISTS

   `intake.intake_records` records WHO submitted only as caller-supplied strings —
   submitter_name, submitter_email, submitter_phone, submitter_role, submitter_is_anonymous — every
   one of them read straight out of the request body by IntakeService.submit(). There is no column
   naming a platform user, so nothing binds a submission to the account that made it. That is RC-E
   ("server truth recorded from the caller") in the platform's front door.

   It has been latent for the same reason F2-144's was: POST /api/v1/intake is guarded by
   hasPermission('INTAKE','WRITE'), and measured 2026-08-09 that permission is held by
   3 of 69 roles -- COMPLIANCE_OFFICER, GRC_ADMINISTRATOR, ORG_ADMIN, all administrators. So the
   only callers were trusted, and the unbound write never mattered.

   ⚠ THE ORDER OF THIS COLUMN AND THE GUARD IS THE WHOLE POINT, and it is F2-144's lesson applied
   rather than re-learned. F2-172 opens submit() to isAuthenticated(), because reporting a security
   event (NCA ECC 6.1), a privacy breach (PDPL Art. 27), a workplace injury (GOSI, 24 hours) or
   wrongdoing (Nazaha / ISO 37001 §8.3) is a duty every employee is under and a grant list can only
   ever wrongly exclude someone. But a wider guard over a body-chosen submitter is an impersonation
   path: any authenticated user could file a report in a colleague's name. So the binding lands
   FIRST, in this migration, and the guard opens on top of it.

   ⚠ NULL IS A MEANINGFUL VALUE HERE, not "not yet backfilled". Three cases produce it and all
   three are correct:

     1. An ANONYMOUS submission of an anonymity-allowing kind (SPEAKUP, BREACH_REPORT,
        VRP_DISCLOSURE). ISO 37001 §8.3 and Nazaha expect real anonymity, so recording the user id
        of a whistleblower who ticked "anonymous" would defeat the channel while appearing to
        improve the audit trail. IntakeService stores no identity at all for these -- not the id,
        and not the name/email/phone the body may still carry.
     2. A submission through a genuinely unauthenticated public portal. None exists yet: F2-138
        records that no anonymous intake endpoint has ever been written, which is why /portal/dsr
        cannot reach a server at all today.
     3. The 0 rows that predate this column. There are none -- intake.intake_records was measured
        empty in dev on 2026-08-09 (F2-171: the substrate has never been called by product code) --
        so this migration has no backfill to do and deliberately writes none.

   No FK to iam.users. A submission is an immutable record of an event; if the account is later
   hard-deleted the record must survive naming an id that no longer resolves, exactly as
   platform.audit_log does. An FK would make deleting a user delete or block the report they filed.

   Idempotent: the ALTER is guarded on sys.columns and the file re-verifies at the end, THROWing if
   the column is absent. Applied twice to the dev database by hand (Flyway is OFF in dev).
   ============================================================================================ */
SET NOCOUNT ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('intake.intake_records') AND name = 'submitter_user_id')
    ALTER TABLE intake.intake_records ADD submitter_user_id BIGINT NULL;
GO

/* The triager inbox filters "submitted by me" and the SLA dashboard groups by submitter, so the
   column is read as a predicate rather than only projected. Filtered to exclude the anonymous rows
   -- they are the majority on a whistleblowing channel and never match this predicate. */
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID('intake.intake_records') AND name = 'IX_intake_records_submitter')
    CREATE INDEX IX_intake_records_submitter
        ON intake.intake_records (organisation_id, submitter_user_id)
        WHERE submitter_user_id IS NOT NULL;
GO

-- ── self-check: the column this migration exists to add must be present ────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('intake.intake_records') AND name = 'submitter_user_id')
BEGIN
    THROW 50374, 'V374 self-check failed: intake.intake_records.submitter_user_id was not created. IntakeService cannot bind a submitter, so POST /api/v1/intake must NOT be opened to isAuthenticated().', 1;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE object_id = OBJECT_ID('intake.intake_records') AND name = 'IX_intake_records_submitter')
BEGIN
    THROW 50374, 'V374 self-check failed: IX_intake_records_submitter was not created.', 1;
END
GO

-- ── self-check: the column must be NULLable, because anonymity depends on it ────────────────
-- A later "tidy-up" making this NOT NULL would force every whistleblower submission to name its
-- author or fail to insert. That is the one change to this column that must never happen quietly.
IF EXISTS (SELECT 1 FROM sys.columns
           WHERE object_id = OBJECT_ID('intake.intake_records')
             AND name = 'submitter_user_id' AND is_nullable = 0)
BEGIN
    THROW 50374, 'V374 self-check failed: submitter_user_id is NOT NULL. Anonymous submissions (SPEAKUP / BREACH_REPORT / VRP_DISCLOSURE) store no identity by design -- NULL is a value here, not a gap.', 1;
END
GO
