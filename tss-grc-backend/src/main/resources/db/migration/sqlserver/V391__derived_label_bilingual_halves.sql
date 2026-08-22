-- V391 — F2-277: a DERIVED label inherits its parent's bilingualism only if the derivation
-- copies BOTH halves, and four slices of this programme built the derivation and copied one.
--
-- ── PART A. A COPY NARROWER THAN THE COLUMN IT COPIES ────────────────────────────────────────
--
-- V390 (F2-276) widened `cyber.cyber_frameworks.name` from NVARCHAR(100) to NVARCHAR(255) so it
-- would mirror `name_ar`. `cyber.cyber_controls.framework_name` holds a DERIVED COPY of that
-- column and stayed NVARCHAR(100) — so a framework name of 101..255 characters became legal in
-- the parent and refused by the copy.
--
-- Measured, and the two databases DISAGREE, which is itself the evidence:
--     dev  (V390 applied)  framework_name 100  <-  name 255    COPY NARROWER
--     prod (V390 pending)  framework_name 100  <-  name 100    consistent
-- So V390 opened this, and it arrives in production with the same jar that carries V390.
--
-- Probed live as `so_officer` before this migration: POST /api/v1/cyber/frameworks with a
-- 150-character name answers 200, and POST /api/v1/cyber/controls linked to it answers 500,
-- while the identical call against a short-named framework answers 200.
--
-- 🔑 WHY NOTHING SAW IT. F2-276's capacity leg compares the two halves of a bilingual pair
-- WITHIN ONE ENTITY. A derived copy's source is in ANOTHER TABLE, so widening the source cannot
-- fail any width invariant that exists. Latent rather than live: 0 framework names are over 100
-- characters in either database today.
--
-- ── PART B. THE ARABIC HALF THE DERIVATION NEVER COPIED ──────────────────────────────────────
--
-- Four registers store a derived copy of a parent's label. Every one of their parents stores the
-- label in BOTH languages, and every derivation reads only the English half:
--
--   child column                                    parent column                    derivable
--   admin.committee_meetings.committee_name_en   <- admin.committees.name_ar            34/34
--   cyber.cyber_controls.framework_name          <- cyber.cyber_frameworks.name_ar     153/154
--   ethics.aml_alerts.subject_name               <- ethics.kyc_subjects.full_name_ar      2/2
--   platform.signatures.signer_name              <- iam.users.full_name_ar                1/4
--
-- Population resolved by VALUE AGREEMENT against every bilingual label pair in the database, not
-- by name (F2-252: a scan locates, THE PAIRING IS THE MEASUREMENT, and a guessed pairing
-- manufactures phantom findings). 32 monolingual identity copies exist; exactly these 4 agree
-- fully with a bilingual parent. The other 28 have a monolingual parent (`scm.vendors`), hold an
-- office rather than a name, or name a register nothing resolves — all filed, none of them this.
--
-- ⚠ THE BACKFILL IS DERIVED, NOT INVENTED, AND THAT IS WHY THIS FAMILY MAY BE BACKFILLED AT ALL.
-- F2-271's rule is that an Arabic governance value must never be manufactured — an empty column
-- is an honest "not recorded" and a machine translation is a fabricated record. That rule is
-- about INVENTING text. Here the text already exists, in the parent row this column is a copy of,
-- and the UPDATE performs exactly the operation the writer performs. Nothing is authored.
--
-- ⚠⚠ GUARDED BY AGREEMENT, because three of the four are point-in-time SNAPSHOTS (a signature's
-- signer name is documented as surviving later user renames). Copying today's Arabic value onto a
-- row whose English half no longer matches the parent would record a name the row never had. So
-- every backfill is conditioned on the English copy STILL EQUALLING the parent's English value —
-- i.e. on the parent demonstrably not having been renamed since. Measured: 0 rows disagree in
-- either database, so the guard is a no-op today and its branch is exercised by a deliberately
-- diverged [REVIEW-TEST] row in the verification script (F2-183: a branch that is a no-op
-- everywhere you can measure is untested code unless you build it something to do).
--
-- WIDTHS mirror the PARENT's Arabic half, never a round number: 255 / 255 / 500 / 200. A copy
-- narrower than its source is Part A of this very migration.
--
-- All columns NULLABLE: a NOT NULL would refuse every row whose parent has no Arabic value
-- (2 of 6 cyber frameworks, 3 of 7 users).
--
-- Idempotent: every step is guarded on the current schema, so applying twice via pyodbc is a
-- no-op (Flyway is off in dev).

SET NOCOUNT ON;
GO

-- ── PART A: widen the copy to match its source ───────────────────────────────────────────────
IF COL_LENGTH('cyber.cyber_controls', 'framework_name') < 510
    ALTER TABLE cyber.cyber_controls ALTER COLUMN framework_name NVARCHAR(255) NULL;
GO

-- ── PART B: the Arabic half of each derived label ────────────────────────────────────────────
IF COL_LENGTH('admin.committee_meetings', 'committee_name_ar') IS NULL
    ALTER TABLE admin.committee_meetings ADD committee_name_ar NVARCHAR(255) NULL;
GO
IF COL_LENGTH('cyber.cyber_controls', 'framework_name_ar') IS NULL
    ALTER TABLE cyber.cyber_controls ADD framework_name_ar NVARCHAR(255) NULL;
GO
IF COL_LENGTH('ethics.aml_alerts', 'subject_name_ar') IS NULL
    ALTER TABLE ethics.aml_alerts ADD subject_name_ar NVARCHAR(500) NULL;
GO
IF COL_LENGTH('platform.signatures', 'signer_name_ar') IS NULL
    ALTER TABLE platform.signatures ADD signer_name_ar NVARCHAR(200) NULL;
GO

-- ── PART B2: derive the Arabic half from the parent, only where the English half still agrees ─
UPDATE m
   SET m.committee_name_ar = c.name_ar
  FROM admin.committee_meetings m
  JOIN admin.committees c ON c.id = m.committee_id
 WHERE m.committee_name_ar IS NULL
   AND c.name_ar IS NOT NULL
   AND ISNULL(m.committee_name_en, '') COLLATE Latin1_General_100_CI_AS_SC_UTF8
     = ISNULL(c.name_en, '')           COLLATE Latin1_General_100_CI_AS_SC_UTF8;
GO

UPDATE ct
   SET ct.framework_name_ar = f.name_ar
  FROM cyber.cyber_controls ct
  JOIN cyber.cyber_frameworks f ON f.id = ct.framework_id
 WHERE ct.framework_name_ar IS NULL
   AND f.name_ar IS NOT NULL
   AND ISNULL(ct.framework_name, '') COLLATE Latin1_General_100_CI_AS_SC_UTF8
     = ISNULL(f.name, '')            COLLATE Latin1_General_100_CI_AS_SC_UTF8;
GO

UPDATE a
   SET a.subject_name_ar = s.full_name_ar
  FROM ethics.aml_alerts a
  JOIN ethics.kyc_subjects s ON s.id = a.subject_id
 WHERE a.subject_name_ar IS NULL
   AND s.full_name_ar IS NOT NULL
   AND ISNULL(a.subject_name, '') COLLATE Latin1_General_100_CI_AS_SC_UTF8
     = ISNULL(s.full_name, '')    COLLATE Latin1_General_100_CI_AS_SC_UTF8;
GO

UPDATE sg
   SET sg.signer_name_ar = u.full_name_ar
  FROM platform.signatures sg
  JOIN iam.users u ON u.id = sg.signer_id
 WHERE sg.signer_name_ar IS NULL
   AND u.full_name_ar IS NOT NULL
   AND ISNULL(sg.signer_name, '') COLLATE Latin1_General_100_CI_AS_SC_UTF8
     = ISNULL(u.full_name_en, '') COLLATE Latin1_General_100_CI_AS_SC_UTF8;
GO

-- ── Self-checks. These THROW. A migration that reports success over an ALTER it did not perform
--    is worse than one that fails, because the asymmetry then reads as reviewed.

-- Check 1 (Part A): the copy is now as wide as the column it copies.
IF COL_LENGTH('cyber.cyber_controls', 'framework_name')
     <> COL_LENGTH('cyber.cyber_frameworks', 'name')
    THROW 53911, 'V391 check 1: cyber_controls.framework_name is not as wide as cyber_frameworks.name.', 1;

-- Check 2: each Arabic half exists and mirrors its PARENT's Arabic half exactly.
IF COL_LENGTH('admin.committee_meetings', 'committee_name_ar')
     <> COL_LENGTH('admin.committees', 'name_ar')
    THROW 53912, 'V391 check 2a: committee_name_ar does not mirror committees.name_ar.', 1;

IF COL_LENGTH('cyber.cyber_controls', 'framework_name_ar')
     <> COL_LENGTH('cyber.cyber_frameworks', 'name_ar')
    THROW 53913, 'V391 check 2b: framework_name_ar does not mirror cyber_frameworks.name_ar.', 1;

IF COL_LENGTH('ethics.aml_alerts', 'subject_name_ar')
     <> COL_LENGTH('ethics.kyc_subjects', 'full_name_ar')
    THROW 53914, 'V391 check 2c: subject_name_ar does not mirror kyc_subjects.full_name_ar.', 1;

IF COL_LENGTH('platform.signatures', 'signer_name_ar')
     <> COL_LENGTH('iam.users', 'full_name_ar')
    THROW 53915, 'V391 check 2d: signer_name_ar does not mirror users.full_name_ar.', 1;

-- Check 3: every column is NULLABLE. A NOT NULL here refuses a row whose parent has no Arabic
-- value, which is 2 of 6 cyber frameworks and 3 of 7 users.
-- ⚠ The parentheses are load-bearing: AND binds tighter than OR in T-SQL, so writing this as
-- `(a) OR (b) OR (c) OR (d) AND is_nullable = 0` asks "does column A exist?" and THROWS on a
-- correct schema. The first draft of this check did exactly that.
IF EXISTS (SELECT 1 FROM sys.columns
            WHERE is_nullable = 0
              AND ((object_id = OBJECT_ID('admin.committee_meetings') AND name = 'committee_name_ar')
                OR (object_id = OBJECT_ID('cyber.cyber_controls')     AND name = 'framework_name_ar')
                OR (object_id = OBJECT_ID('ethics.aml_alerts')        AND name = 'subject_name_ar')
                OR (object_id = OBJECT_ID('platform.signatures')      AND name = 'signer_name_ar')))
    THROW 53916, 'V391 check 3: a derived Arabic label column is NOT NULL; it must be nullable.', 1;

-- Check 4: NOTHING WAS INVENTED. Every non-null Arabic value must equal its parent's own Arabic
-- value. This is the check that separates a derived backfill from a fabricated one, and it is the
-- reason this family may be backfilled where F2-271's could not.
DECLARE @invented INT = 0;

SELECT @invented = @invented + COUNT(*)
  FROM admin.committee_meetings m
  JOIN admin.committees c ON c.id = m.committee_id
 WHERE m.committee_name_ar IS NOT NULL
   AND ISNULL(m.committee_name_ar, '') COLLATE Latin1_General_100_CI_AS_SC_UTF8
    <> ISNULL(c.name_ar, '')           COLLATE Latin1_General_100_CI_AS_SC_UTF8;

SELECT @invented = @invented + COUNT(*)
  FROM cyber.cyber_controls ct
  JOIN cyber.cyber_frameworks f ON f.id = ct.framework_id
 WHERE ct.framework_name_ar IS NOT NULL
   AND ISNULL(ct.framework_name_ar, '') COLLATE Latin1_General_100_CI_AS_SC_UTF8
    <> ISNULL(f.name_ar, '')            COLLATE Latin1_General_100_CI_AS_SC_UTF8;

SELECT @invented = @invented + COUNT(*)
  FROM ethics.aml_alerts a
  JOIN ethics.kyc_subjects s ON s.id = a.subject_id
 WHERE a.subject_name_ar IS NOT NULL
   AND ISNULL(a.subject_name_ar, '') COLLATE Latin1_General_100_CI_AS_SC_UTF8
    <> ISNULL(s.full_name_ar, '')    COLLATE Latin1_General_100_CI_AS_SC_UTF8;

SELECT @invented = @invented + COUNT(*)
  FROM platform.signatures sg
  JOIN iam.users u ON u.id = sg.signer_id
 WHERE sg.signer_name_ar IS NOT NULL
   AND ISNULL(sg.signer_name_ar, '') COLLATE Latin1_General_100_CI_AS_SC_UTF8
    <> ISNULL(u.full_name_ar, '')    COLLATE Latin1_General_100_CI_AS_SC_UTF8;

IF @invented > 0
    THROW 53917, 'V391 check 4: a derived Arabic label does not equal its parent''s own Arabic value.', 1;

-- ⚠ There is DELIBERATELY no check that the English halves still agree with their parents.
-- It was written and removed: on a customer database a committee renamed after its meetings were
-- created legitimately leaves the older English snapshot in place, so that check would THROW and
-- block this migration and every one after it. A migration may assert ITS OWN WRITES and must
-- never assert tenant data (F2-260) — which is also why check 4 is safe: the four Arabic columns
-- are created by this file, so every non-null value it sees is one it just wrote. The claim that
-- this migration writes no English column is STRUCTURAL and is asserted hermetically by
-- DerivedLabelBilingualContractTest instead.

PRINT 'V391 OK: framework_name widened to its source, and four derived labels gained the Arabic half their parents already store.';
GO
