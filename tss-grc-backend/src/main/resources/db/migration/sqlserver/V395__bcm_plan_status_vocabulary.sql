/* ============================================================================
   V395 — bcm.bcm_plans.status: the register's first CHECK constraint
   ----------------------------------------------------------------------------
   F2-299. This table had ZERO CHECK constraints, so `status` was narrowed only
   by BcmService's five writers. F2-183's rule is that a constraint and its
   writers land together, and this is the second half of a sequence rather than
   a fresh claim: F2-296 gave the register an echo-tolerant / change-refusing
   guard on the entity-bound PUT, and F2-298 removed the `= "DRAFT"` initialiser
   that made that guard fire on every non-DRAFT plan. With those closed, every
   writer of this column is a literal from the machine or ServerOwnedState
   .requireInitial, so nothing reachable can store a value outside the four.

   ⚠ THE VOCABULARY IS FOUR TOKENS, NOT FIVE. BcmPlan#status's own javadoc said
   `DRAFT | UNDER_REVIEW | APPROVED | INVOKED | ARCHIVED (see BcmService)` and
   ARCHIVED appears nowhere in BcmService, its controller or BcmPage — it was
   copied from the CANONICAL register (bcm.bcp_plans, which really does have
   ARCHIVED / PUBLISHED / SUPERSEDED / CANCELLED). Writing this CHECK from that
   line would have blessed a state no route can reach, which is worse than
   blessing today's values because it makes the vocabulary look like five. The
   four below are derived from the writers, and
   BcmPlanStatusVocabularyTest asserts this file against them by reading the
   service's own literals — so the CHECK cannot drift from what can be written.

   ⚠ A CHECK cannot see DIRECTION or CASE (F2-296, F2-183). It is not the whole
   control here and is not meant to be: the ISO 22301 order is enforced by
   BcmService's transition guards, and the case-insensitive echo comparison by
   ServerOwnedState.sameState. This narrows the vocabulary and nothing more.

   MEASURED BEFORE WRITING, in BOTH databases, over ALL rows including
   soft-deleted ones (ADD CONSTRAINT verifies those too, and a live-only count
   would have missed two):

     TSS_GRC             10 rows = DRAFT 5 live + APPROVED 3 live + DRAFT 2 deleted
     TSS_GRC_Deployment  10 rows = DRAFT 5 live + APPROVED 3 live + DRAFT 2 deleted

     0 NULLs · 0 non-canonical casings · 0 values outside the four tokens

   So no WITH NOCHECK, and nothing to reconcile. Idempotent: safe to apply twice
   (dev has Flyway off and this is applied by hand via pyodbc).
   ============================================================================ */

SET NOCOUNT ON;

/* ---- 0. pre-flight: refuse rather than silently skip verification --------- */
IF EXISTS (
    SELECT 1 FROM bcm.bcm_plans
    WHERE status IS NULL
       OR status COLLATE Latin1_General_100_BIN2 NOT IN (
            N'DRAFT'         COLLATE Latin1_General_100_BIN2,
            N'UNDER_REVIEW'  COLLATE Latin1_General_100_BIN2,
            N'APPROVED'      COLLATE Latin1_General_100_BIN2,
            N'INVOKED'       COLLATE Latin1_General_100_BIN2))
BEGIN
    DECLARE @offenders NVARCHAR(MAX) = (
        SELECT STRING_AGG(CONCAT('id=', id, ' status=', ISNULL(status, '<NULL>')), '; ')
        FROM bcm.bcm_plans
        WHERE status IS NULL
           OR status COLLATE Latin1_General_100_BIN2 NOT IN (
                N'DRAFT'         COLLATE Latin1_General_100_BIN2,
                N'UNDER_REVIEW'  COLLATE Latin1_General_100_BIN2,
                N'APPROVED'      COLLATE Latin1_General_100_BIN2,
                N'INVOKED'       COLLATE Latin1_General_100_BIN2));
    /* Naming the rows matters: a bare "constraint violated" sends the reader
       nowhere, and this migration blocks every later one until it is resolved. */
    THROW 51395, @offenders, 1;
END;

/* ---- 1. the constraint ---------------------------------------------------- */
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_bcm_plans_status')
BEGIN
    ALTER TABLE bcm.bcm_plans WITH CHECK
        ADD CONSTRAINT CK_bcm_plans_status CHECK (status IN
            (N'DRAFT', N'UNDER_REVIEW', N'APPROVED', N'INVOKED'));
END;

/* ---- 2. self-check: it exists, is enabled, AND is trusted ----------------- */
/* `is_not_trusted = 1` is what a WITH NOCHECK add leaves behind, and it looks
   identical to a verified constraint in every listing that only checks the name. */
IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = 'CK_bcm_plans_status' AND is_disabled = 0 AND is_not_trusted = 0)
BEGIN
    THROW 51396, N'CK_bcm_plans_status is missing, disabled, or not trusted', 1;
END;

/* ---- 3. self-check: every state the machine writes is still admitted ------ */
/* A CHECK narrower than the writers turns a legal transition into a 547 at run
   time, which surfaces as a 409 naming no field (F2-230's observation). Proving
   the four are accepted is cheaper than discovering it from a broken approval.

   ⚠ Checks 3 and 4 UPDATE an existing row inside a rolled-back transaction, so
   on an EMPTY table they verify nothing and say nothing — which is F2-183's
   no-op-branch hazard. They are deliberately NOT made to fail on an empty
   table: a fresh install runs this before any plan exists, and requiring rows
   would mean a migration asserting TENANT DATA rather than its own writes
   (F2-260's rule, which cost V383 a check that would have blocked the chain).
   What covers the empty case is check 2 plus BcmPlanStatusVocabularyTest, which
   is hermetic and compares this file's token list against BcmService's own
   writers — so the vocabulary is asserted even where no row exists to probe. */
IF EXISTS (SELECT 1 FROM bcm.bcm_plans)
BEGIN
BEGIN TRAN;
    DECLARE @probe TABLE (s NVARCHAR(50));
    INSERT INTO @probe VALUES (N'DRAFT'), (N'UNDER_REVIEW'), (N'APPROVED'), (N'INVOKED');

    DECLARE @s NVARCHAR(50), @bad NVARCHAR(MAX) = N'';
    DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT s FROM @probe;
    OPEN c; FETCH NEXT FROM c INTO @s;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        BEGIN TRY
            UPDATE TOP (1) bcm.bcm_plans SET status = @s;
        END TRY
        BEGIN CATCH
            SET @bad = @bad + @s + N' ';
        END CATCH;
        FETCH NEXT FROM c INTO @s;
    END;
    CLOSE c; DEALLOCATE c;
ROLLBACK TRAN;   /* the probe never keeps its writes */

IF LEN(@bad) > 0
BEGIN
    THROW 51397, @bad, 1;
END;

/* ---- 4. self-check: the phantom token is NOT admitted --------------------- */
/* F2-299's finding was a javadoc naming ARCHIVED, which no route can produce.
   Asserting it is refused is what stops a later hand-edit widening the CHECK to
   match that prose (F2-197: a negative control that cannot fail proves nothing). */
BEGIN TRAN;
    DECLARE @archivedAccepted BIT = 0;
    BEGIN TRY
        UPDATE TOP (1) bcm.bcm_plans SET status = N'ARCHIVED';
        SET @archivedAccepted = 1;
    END TRY
    BEGIN CATCH
        SET @archivedAccepted = 0;
    END CATCH;
ROLLBACK TRAN;

IF @archivedAccepted = 1
BEGIN
    THROW 51398, N'CK_bcm_plans_status admits ARCHIVED, which BcmService cannot write', 1;
END;
END;   /* close the IF EXISTS guard around checks 3 and 4 */
