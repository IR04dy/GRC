/* ============================================================================
   V331 — a board pack's `audience` must name a committee that actually exists.

   THE PREREQUISITE FOR ADR-017 D-11, and a defect in its own right.

   D-11 decided that board-pack reads must be scoped by `audience` bound to
   committee membership, defaulting to deny for non-members. Measuring it before
   building it stopped it from shipping, for two independent reasons.

   ── REASON 1: THREE VOCABULARIES, TWO OF THEM WRONG ────────────────────────
   `board_pack.packs.audience` is a free string. Measured:

     BoardPackDto @Pattern accepts : BOARD | AUDIT_COMMITTEE | RISK_COMMITTEE | EXEC   (4)
     actually stored in the table  : BOARD, AUDIT_COMMITTEE, RISK_COMMITTEE,
                                     CYBER_COMMITTEE, EXEC                            (5)
     admin.committees.committee_type                                                  (17)

   So one pack holds audience='CYBER_COMMITTEE', which the API can no longer
   create — the validation pattern and the data disagree. And joining audience to
   committee_type by string equality resolves only THREE of the five:

     BOARD            -> BOARD             OK
     AUDIT_COMMITTEE  -> AUDIT_COMMITTEE   OK
     RISK_COMMITTEE   -> RISK_COMMITTEE    OK
     CYBER_COMMITTEE  -> (nothing)         the real type is CYBER_SECURITY
     EXEC             -> (nothing)         the real type is EXEC_COMMITTEE

   An audience-scoped read filter written against that join would silently
   mis-scope every CYBER_COMMITTEE and EXEC pack — 5 of the 16 — and "silently"
   is the whole problem: no error, no log line, just a pack visible to the wrong
   set or to nobody. This migration removes that trap by making the two
   vocabularies the same vocabulary.

   ── REASON 2: WHY D-11'S ENFORCEMENT IS NOT IN THIS MIGRATION ──────────────
   Measured what D-11-as-written would actually do, before writing it:

     audience         packs  committee_type      linked platform users
     AUDIT_COMMITTEE      1  AUDIT_COMMITTEE          0
     BOARD                7  BOARD                    0
     CYBER_COMMITTEE      1  CYBER_SECURITY           0
     EXEC                 4  EXEC_COMMITTEE           0
     RISK_COMMITTEE       3  RISK_COMMITTEE           2

   **13 of 16 packs would have become unreadable by any non-admin principal, all
   7 BOARD packs among them.** The board would have been locked out of board
   packs. Only 5 of 31 committee member terms are linked to a platform user, and
   ZERO on the Board and the Audit Committee — because F2-26 deliberately supports
   directors who will never have a login (`no_platform_user`). Membership-by-login
   is therefore structurally incapable of being the whole control, and default-deny
   on today's data is an outage dressed as a security fix. That is the F2-27
   lesson repeating: the impact query falsified the design the plan had
   pre-committed to.

   So this migration ships the prerequisite and NOT the enforcement. ADR-017 D-11
   is amended with the measurement, and enforcement is gated on a stated
   readiness condition rather than a hope.

   ── WHAT THIS MIGRATION DOES ──────────────────────────────────────────────
   Re-points the two drifted values onto the committee vocabulary. Nothing else:
   no read behaviour changes, no grants, no schema change. 5 rows at most.

   Safe to run twice: the UPDATEs are value-scoped, so the second pass matches
   nothing (that is how dev receives it — Flyway is off in dev).
   ============================================================================ */

SET NOCOUNT ON;
GO

/* ── 1. CYBER_COMMITTEE -> CYBER_SECURITY ─────────────────────────────────── */
UPDATE board_pack.packs
   SET audience = 'CYBER_SECURITY'
 WHERE audience COLLATE DATABASE_DEFAULT = 'CYBER_COMMITTEE' COLLATE DATABASE_DEFAULT;
GO

/* ── 2. EXEC -> EXEC_COMMITTEE ────────────────────────────────────────────── */
UPDATE board_pack.packs
   SET audience = 'EXEC_COMMITTEE'
 WHERE audience COLLATE DATABASE_DEFAULT = 'EXEC' COLLATE DATABASE_DEFAULT;
GO

/* ── 3. Verify by throwing ────────────────────────────────────────────────── */

/* 3a. The whole point: every stored audience must now resolve to a real
       committee_type. If this throws, a new drifted value has appeared and an
       audience-scoped filter would mis-scope it. */
IF EXISTS (
    SELECT 1 FROM board_pack.packs p
    WHERE p.is_deleted = 0
      AND NOT EXISTS (
        SELECT 1 FROM admin.committees cc
        WHERE cc.committee_type COLLATE DATABASE_DEFAULT = p.audience COLLATE DATABASE_DEFAULT))
    THROW 50331, 'V331 FAILED: a board pack audience does not match any admin.committees.committee_type. An audience-scoped read filter would silently mis-scope it. Align the value or add the committee.', 1;
GO

/* 3b. The two legacy spellings must be gone, not merely outnumbered. */
IF EXISTS (
    SELECT 1 FROM board_pack.packs
    WHERE audience COLLATE DATABASE_DEFAULT IN ('CYBER_COMMITTEE' COLLATE DATABASE_DEFAULT,
                                                'EXEC'            COLLATE DATABASE_DEFAULT))
    THROW 50331, 'V331 FAILED: a pre-alignment audience value survived the update.', 1;
GO

/* 3c. Anti-vacuity. If the table were empty, 3a passes trivially and this
       migration would look successful having verified nothing. */
IF (SELECT COUNT(*) FROM board_pack.packs WHERE is_deleted = 0) = 0
    THROW 50331, 'V331 FAILED: no board packs found, so the audience check verified nothing. Investigate before treating this as applied.', 1;
GO

/* 3d. No pack may lose its audience to the alignment. `audience` is NOT NULL in
       the entity; a blank would break the future filter in the most confusing
       possible way (a pack belonging to no committee). */
IF EXISTS (
    SELECT 1 FROM board_pack.packs
    WHERE is_deleted = 0 AND (audience IS NULL OR LEN(LTRIM(RTRIM(audience))) = 0))
    THROW 50331, 'V331 FAILED: a board pack has an empty audience.', 1;
GO

PRINT N'V331 — board-pack audience aligned to the admin.committees vocabulary; every stored audience now resolves to a real committee_type. Read enforcement (ADR-017 D-11) deliberately NOT included: it would have hidden 13 of 16 packs.';
GO
