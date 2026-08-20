/* ============================================================================
 * V384 — F2-261: the board approver column, so a quantitative BIA's highest
 *                approval tier records WHO as well as WHEN
 * ============================================================================
 *
 * THE SERVER OWNS *WHEN* AND NOBODY OWNS *WHO* — at the TOP of a three-stage ladder whose two
 * lower stages both record the actor.
 *
 *     POST …/bia/quantitative/{externalId}/bcm-review     -> bcm_lead_reviewed_by  (the actor)
 *     POST …/bia/quantitative/{externalId}/cfo-approve    -> cfo_approver_name     (the actor, F2-223)
 *     POST …/bia/quantitative/{externalId}/board-approve  -> board_approved_at + board_minutes_ref
 *                                                            and NO ACTOR AT ALL
 *
 * `QuantitativeBiaService.boardApprove` stamps `board_approved_at` from `LocalDateTime.now()` and
 * writes a board-minutes reference, and there is no column for the person. 🔑 So the attribution
 * quality DECREASES as the authority increases: stage 1 records the actor, stage 2 records the
 * actor, and stage 3 — the board sign-off, the artefact an ISO 22301 or SAMA business-continuity
 * reviewer reads — records the moment and not the person. That is F2-256's shape (a clock with no
 * actor beside it) landing on the highest tier rather than a peripheral one.
 *
 * ── WHY THIS COLUMN AND NOT A USER ID ────────────────────────────────────────────────────
 * `bcm_lead_reviewed_by` and `cfo_approver_name` are both NVARCHAR(255) and both hold
 * `CurrentActor.username()`. Adding a BIGINT here would be a SECOND mechanism beside an existing
 * one (F2-190), and it would make the four-eyes comparison below impossible to write — the three
 * parties would not be comparable. So this mirrors its two siblings exactly, and check 2 asserts
 * that mirror so a future widening of one is noticed rather than silently diverging.
 *
 * ⚠ An id would be the better shape in the abstract — a username can be renamed and the history
 *   then lies. Converting all three together is a coherent slice and this is not it: doing one of
 *   the three is what produced the divergence being fixed. Filed.
 *
 * ── WHAT THIS FILE DOES NOT DO ───────────────────────────────────────────────────────────
 * It does not backfill. Measured in dev and prod: `bcm.bia_quantitative` holds ONE row, at
 * `CFO_APPROVED`, with `board_approved_at` NULL — so there is no completed board approval whose
 * approver would have to be invented, and inventing one would be F2-196's defect. There is also
 * deliberately no self-check refusing rows that DO have `board_approved_at` set with no approver:
 * on a customer's database such a row is a pre-fix board approval, and blocking their whole
 * migration chain over it is F2-183's hazard. Such a row is a disclosure (F2-209's third state),
 * not a migration failure.
 *
 * ⚠ And no tenant data is asserted anywhere here — F2-260's rule: a migration may assert its own
 *   writes and must not assert tenant data, because a fresh install has none.
 *
 * ⚠ No CHECK constraint is added. The whole table has **zero** today, and its status vocabulary is
 *   narrowed only by `QuantitativeBiaService.guardTransition` — F2-221's shape. Adding one is
 *   cheap only in the commit that closes the column's writers (F2-183), and this slice touches one
 *   writer of one column.
 *
 * Idempotent: re-running adds nothing and every check still passes.
 * ==========================================================================================*/

SET NOCOUNT ON;
GO

/* ── check 0 — the pre-flight: this is the right table, identified by the siblings the new
 * column has to mirror. A guessed table name is the recurring error in this programme, and an
 * ALTER against the wrong one succeeds. */
IF OBJECT_ID('bcm.bia_quantitative', 'U') IS NULL
    THROW 51384, 'V384 check 0: bcm.bia_quantitative does not exist.', 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
                WHERE object_id = OBJECT_ID('bcm.bia_quantitative') AND name = 'cfo_approver_name')
   OR NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE object_id = OBJECT_ID('bcm.bia_quantitative') AND name = 'bcm_lead_reviewed_by')
   OR NOT EXISTS (SELECT 1 FROM sys.columns
                   WHERE object_id = OBJECT_ID('bcm.bia_quantitative') AND name = 'board_approved_at')
    THROW 51384, 'V384 check 0b: bcm.bia_quantitative is missing one of the three columns this one mirrors (cfo_approver_name, bcm_lead_reviewed_by, board_approved_at). Either the table is not the one this file was written against, or the approval chain has changed shape.', 1;
GO

/* ── the column ─────────────────────────────────────────────────────────────────────────── */
IF NOT EXISTS (SELECT 1 FROM sys.columns
                WHERE object_id = OBJECT_ID('bcm.bia_quantitative') AND name = 'board_approved_by')
    ALTER TABLE bcm.bia_quantitative ADD board_approved_by NVARCHAR(255) NULL;
GO

/* ── check 1 — it exists ────────────────────────────────────────────────────────────────── */
IF NOT EXISTS (SELECT 1 FROM sys.columns
                WHERE object_id = OBJECT_ID('bcm.bia_quantitative') AND name = 'board_approved_by')
    THROW 51384, 'V384 check 1: board_approved_by was not created.', 1;
GO

/* ── check 2 — it MIRRORS its two siblings exactly, in type, width and nullability ────────
 * The four-eyes bar in QuantitativeBiaService compares all three as strings, so a divergence
 * in width could truncate one party's name and make two different people compare equal. */
IF EXISTS (
    SELECT 1
      FROM sys.columns c
      JOIN sys.types  t ON t.user_type_id = c.user_type_id
     WHERE c.object_id = OBJECT_ID('bcm.bia_quantitative')
       AND c.name IN ('board_approved_by', 'cfo_approver_name', 'bcm_lead_reviewed_by')
     GROUP BY t.name, c.max_length, c.is_nullable
    HAVING COUNT(*) <> 3)
    THROW 51384, 'V384 check 2: board_approved_by does not have the same type, width and nullability as cfo_approver_name and bcm_lead_reviewed_by. The four-eyes bar compares all three as strings, so a narrower column could truncate one party and make two different people compare equal.', 1;
GO

/* ── check 3 — anti-vacuity: the mirror group really is three columns, not one matched
 * three times by a pattern that collapsed. */
IF (SELECT COUNT(*)
      FROM sys.columns
     WHERE object_id = OBJECT_ID('bcm.bia_quantitative')
       AND name IN ('board_approved_by', 'cfo_approver_name', 'bcm_lead_reviewed_by')) <> 3
    THROW 51384, 'V384 check 3: the three-column mirror group does not have three members.', 1;
GO
