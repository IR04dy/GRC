/* ============================================================================================
   V400 — F2-461: the only two UNTRUSTED constraints in the platform, verified against the rows
   they have never been checked against.

   `is_not_trusted = 1` is what a `WITH NOCHECK` add leaves behind. Such a constraint is ENABLED
   and enforces every new INSERT and UPDATE; what it has never done is verify the rows that were
   already there. And in any listing that checks only a constraint's NAME it is indistinguishable
   from a verified one — which is the property V395 asserts against for its own index, in as many
   words, and V399's check 2 above repeats: "a key that is present and not enforcing is worse than
   an absent one, because it answers the question a reader was about to ask."

   Measured across every constraint in both live databases: exactly TWO are untrusted, both on
   `cyber.cyber_evidence`, and both come from V265 —

       ALTER TABLE cyber.cyber_evidence WITH NOCHECK
           ADD CONSTRAINT FK_cyber_evidence_control  FOREIGN KEY (control_id) ...
       ALTER TABLE cyber.cyber_evidence WITH NOCHECK
           ADD CONSTRAINT ck_cyber_evidence_status   CHECK (status IN (...))

   V265 is the ONLY `WITH NOCHECK` in the whole 386-migration chain. Its own comment explains
   itself: "Both are added WITH NOCHECK: existing rows that predate CY-H are grandfathered."

   🔑 THE GRANDFATHERING IS NO LONGER TRUE, AND THAT IS WHY THIS FILE IS SAFE. Measured in BOTH
   databases immediately before writing it: 12 evidence rows each, `status` values CURRENT (8) and
   STALE (4) only, ZERO outside the six-token vocabulary, ZERO non-canonical casing (checked under
   a BINARY collation, because the column collates case-INSENSITIVELY so the CHECK cannot see case
   — F2-179), and ZERO `control_id` values pointing at a `cyber.cyber_controls` row that does not
   exist. So the re-verification below has nothing to refuse.

   ⚠ WHAT THIS FILE DOES NOT DO. It does not add, drop, disable or widen anything. `WITH CHECK
   CHECK CONSTRAINT <name>` asks SQL Server to validate the existing rows against a constraint that
   is already there and already enforcing, and to record that it did by clearing `is_not_trusted`.
   Verified on dev inside a rolled-back transaction before this file was written: both flags go
   1 → 0, `is_disabled` stays 0, and a rollback restores them — so the statement is transactional
   and its effect is exactly the one claimed.

   ⚠ AND IT CANNOT BE FIXED BY EDITING V265. An applied migration may never be edited: Flyway
   stores a checksum, so a changed V265 fails validation on the deployed server and blocks every
   later migration. That is also why the accompanying gate names V265 as a permanent exemption
   rather than demanding it be corrected.

   ⚠ Idempotent: re-verifying an already-trusted constraint is a no-op that succeeds, so this file
   is safe to apply twice (Flyway is OFF in dev, so it is applied by hand — house convention).
   On a fresh install V265 runs first over an empty table, so both constraints exist and are
   vacuously valid by the time this file reaches them.
   ============================================================================================ */

/* ── check 0: refuse outright if V265 has not run. ────────────────────────────────────────────
   ── `ALTER TABLE ... CHECK CONSTRAINT <name>` on a constraint that does not exist is an       ──
   ── error whose message names the constraint and not the reason, which sends the reader        ──
   ── looking for a typo rather than for a missing prerequisite (V381's check 0 precedent).      ── */
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                WHERE name = 'ck_cyber_evidence_status'
                  AND parent_object_id = OBJECT_ID('cyber.cyber_evidence'))
   OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys
                   WHERE name = 'FK_cyber_evidence_control'
                     AND parent_object_id = OBJECT_ID('cyber.cyber_evidence'))
BEGIN
    THROW 51400, 'V400: one of cyber.cyber_evidence''s two constraints is absent, so V265 has not been applied to this database. This file only re-verifies constraints that already exist; it does not create them.', 1;
END
GO

/* ── check 1 (pre-flight): NAME the rows that would fail, before asking SQL Server to. ────────
   ── A bare "The ALTER TABLE statement conflicted with the CHECK constraint" sends the reader   ──
   ── nowhere, and a failing migration blocks every later one on the deployed server. So this    ──
   ── reports the offending ids and values instead (V399/V395's precedent).                     ── */
DECLARE @badStatus NVARCHAR(MAX) = (
    SELECT STRING_AGG(CONVERT(NVARCHAR(MAX), CONCAT('id=', e.id, ' status=', ISNULL(e.status, '<NULL>'))), '; ')
      FROM cyber.cyber_evidence e
     WHERE e.status IS NULL
        OR e.status NOT IN ('CURRENT', 'PENDING', 'STALE', 'EXPIRED', 'ARCHIVED', 'SUPERSEDED'));

IF @badStatus IS NOT NULL
BEGIN
    DECLARE @m1 NVARCHAR(MAX) = CONCAT(
        N'V400 pre-flight: cyber.cyber_evidence holds row(s) whose status is outside ',
        N'ck_cyber_evidence_status''s vocabulary, so re-verifying it would fail. ',
        N'These rows are what V265''s WITH NOCHECK grandfathered. Fix the data — do NOT ',
        N'restore WITH NOCHECK. Offenders: ', @badStatus);
    THROW 51400, @m1, 1;
END
GO

DECLARE @dangling NVARCHAR(MAX) = (
    SELECT STRING_AGG(CONVERT(NVARCHAR(MAX), CONCAT('id=', e.id, ' control_id=', e.control_id)), '; ')
      FROM cyber.cyber_evidence e
     WHERE e.control_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM cyber.cyber_controls c WHERE c.id = e.control_id));

IF @dangling IS NOT NULL
BEGIN
    DECLARE @m2 NVARCHAR(MAX) = CONCAT(
        N'V400 pre-flight: cyber.cyber_evidence holds row(s) whose control_id names no ',
        N'cyber.cyber_controls row, so re-verifying FK_cyber_evidence_control would fail. ',
        N'Fix the data — do NOT restore WITH NOCHECK. Offenders: ', @dangling);
    THROW 51400, @m2, 1;
END
GO

/* ── the whole change: validate the rows, and record that it happened. ─────────────────────── */
ALTER TABLE cyber.cyber_evidence WITH CHECK CHECK CONSTRAINT ck_cyber_evidence_status;
GO

ALTER TABLE cyber.cyber_evidence WITH CHECK CHECK CONSTRAINT FK_cyber_evidence_control;
GO

/* ── check 2: assert what this migration claims to have done rather than assuming it. ──────── */
IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
     WHERE name = 'ck_cyber_evidence_status'
       AND parent_object_id = OBJECT_ID('cyber.cyber_evidence')
       AND is_disabled = 0
       AND is_not_trusted = 0)
BEGIN
    THROW 51400, 'V400 self-check: ck_cyber_evidence_status is absent, disabled or still untrusted. An untrusted CHECK enforces new writes and says nothing about the rows already stored, while looking identical to a verified one in any listing that checks only the name.', 1;
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
     WHERE name = 'FK_cyber_evidence_control'
       AND parent_object_id = OBJECT_ID('cyber.cyber_evidence')
       AND referenced_object_id = OBJECT_ID('cyber.cyber_controls')
       AND is_disabled = 0
       AND is_not_trusted = 0)
BEGIN
    THROW 51400, 'V400 self-check: FK_cyber_evidence_control is absent, points at the wrong table, is disabled or is still untrusted.', 1;
END
GO

/* ── check 3: the platform-wide claim this file exists to make true. ───────────────────────────
   ── Scoped to the two constraints above would be a weaker assertion than the finding: what     ──
   ── was wrong is that NOTHING asked the question of the whole catalogue, so V265's two sat      ──
   ── untrusted while sixteen later migrations each wrote "No WITH NOCHECK anywhere" and gated    ──
   ── it over their own constraints only (F2-198's shape at a migration's own scope).            ──
   ── ⚠ This check is about the DATABASE. The companion gate is about the migration CORPUS, and  ──
   ── neither substitutes for the other: a `WITH NOCHECK` in a file nobody has applied yet is    ──
   ── invisible here, and a constraint some operator disabled by hand is invisible there.        ── */
DECLARE @untrusted NVARCHAR(MAX) = (
    SELECT STRING_AGG(CONVERT(NVARCHAR(MAX), CONCAT(s.name, '.', t.name, '.', x.name)), '; ')
      FROM (
            SELECT c.name, c.parent_object_id, c.is_disabled, c.is_not_trusted
              FROM sys.check_constraints c
            UNION ALL
            SELECT f.name, f.parent_object_id, f.is_disabled, f.is_not_trusted
              FROM sys.foreign_keys f
           ) x
      JOIN sys.tables  t ON t.object_id = x.parent_object_id
      JOIN sys.schemas s ON s.schema_id = t.schema_id
     WHERE x.is_not_trusted = 1 OR x.is_disabled = 1);

IF @untrusted IS NOT NULL
BEGIN
    DECLARE @m3 NVARCHAR(MAX) = CONCAT(
        N'V400 self-check: this database still holds a disabled or untrusted CHECK or FOREIGN KEY. ',
        N'Such a constraint is present in every listing and enforcing nothing about the rows ',
        N'already stored. Offenders: ', @untrusted);
    THROW 51400, @m3, 1;
END
GO
