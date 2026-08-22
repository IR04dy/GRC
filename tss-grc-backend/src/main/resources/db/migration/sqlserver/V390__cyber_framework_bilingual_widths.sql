-- V390 — F2-276: the two halves of a bilingual pair must have the SAME CAPACITY, and on
-- `cyber.cyber_frameworks` the ENGLISH half was the narrower one — twice.
--
-- THE SHAPE. V387, V388 and V389 each assert that a NEW Arabic column mirrors its English
-- sibling's width, and each states the reason: a narrower Arabic column silently truncates a value
-- the English one accepts, and Arabic is not shorter than English. That assertion has only ever
-- been applied to columns those three migrations created. Nothing has ever checked the pairs that
-- were already there.
--
-- Measured over BOTH databases, every En/Ar column pair in the platform (including the bare-stem
-- spelling F2-276 uncovered, where the English half carries no `_en` suffix):
--
--     pairs checked   643 dev / 626 prod
--     width mismatch  2, both on cyber.cyber_frameworks, both ENGLISH-narrower
--
--       name           NVARCHAR(100)   vs   name_ar         NVARCHAR(255)
--       description_en NVARCHAR(500)   vs   description_ar  NVARCHAR(MAX)
--
-- Two of 643 is the whole population, so this is a small, complete fix rather than a sweep.
--
-- ⚠⚠ WHY NOTHING SAW IT: THE ENTITY DECLARES THE CAPACITY IT WANTS AND `ddl-auto=none` MEANS
-- NOBODY EVER COMPARES THE CLAIM WITH THE COLUMN.
--   * `CyberFramework.descriptionEn` and `.descriptionAr` carry the IDENTICAL annotation —
--     `@Column(columnDefinition = "NVARCHAR(MAX)")` — and map to columns of 500 and MAX. So the
--     pair is symmetric at the bean and asymmetric at the schema, and the bean's symmetry is
--     precisely what stops a reader asking. `columnDefinition` is a CLAIM, not a constraint:
--     Hibernate only emits it when it generates DDL, which this platform never does.
--   * `CyberFramework.name` carries `@Column(nullable = false)` with NO length at all, so the bean
--     assumes Hibernate's 255 default against a 100-character column. An English framework name of
--     101 characters is accepted by every validator and refused by SQL Server as error 2628 — a
--     500, not a 400 naming the field (F2-185b's family, and error 2628 does not report the limit).
--
-- WHICH HALF IS WRONG. Widen the English, never narrow the Arabic:
--   * widening cannot refuse a row that exists today, and narrowing can;
--   * the Arabic widths are the ones the pair's own migration (V37) chose deliberately;
--   * widening makes the entity's OWN declared intent true for the first time.
-- Longest stored values are 9 and 175 characters, so nothing is near either limit — this closes a
-- latent asymmetry rather than repairing damage, and saying which is the point.
--
-- SAFETY, checked rather than assumed (sys.indexes / sys.check_constraints / sys.columns, both
-- databases): neither column is an index key, neither carries a CHECK, neither is computed and
-- neither has a default. NVARCHAR(MAX) cannot be an index key, so an index on `description_en`
-- would have made check 2 impossible — there is none. No data is read and none is written: a
-- migration may assert its own DDL and must never assert tenant data (F2-260).
--
-- Idempotent: each ALTER is guarded on the current width, so applying twice via pyodbc is a no-op
-- (Flyway is off in dev).

SET NOCOUNT ON;
GO

-- ── 1. cyber.cyber_frameworks.name : NVARCHAR(100) -> NVARCHAR(255), mirroring name_ar ──────
--    Stays NOT NULL. ALTER COLUMN drops nullability if it is not restated.
IF COL_LENGTH('cyber.cyber_frameworks', 'name') < 510
    ALTER TABLE cyber.cyber_frameworks ALTER COLUMN name NVARCHAR(255) NOT NULL;
GO

-- ── 2. cyber.cyber_frameworks.description_en : NVARCHAR(500) -> NVARCHAR(MAX) ────────────────
--    Mirrors description_ar AND makes the entity's own columnDefinition claim true. Stays NULL.
IF COL_LENGTH('cyber.cyber_frameworks', 'description_en') <> -1
    ALTER TABLE cyber.cyber_frameworks ALTER COLUMN description_en NVARCHAR(MAX) NULL;
GO

-- ── Self-checks. These THROW. A migration that reports success over an ALTER it did not perform
--    is worse than one that fails, because the asymmetry then reads as reviewed.

-- Check 1: the English name now mirrors name_ar, and is still NOT NULL.
IF COL_LENGTH('cyber.cyber_frameworks', 'name')
     <> COL_LENGTH('cyber.cyber_frameworks', 'name_ar')
    THROW 53901, 'V390 check 1: cyber_frameworks.name does not mirror name_ar.', 1;

IF EXISTS (SELECT 1 FROM sys.columns
            WHERE object_id = OBJECT_ID('cyber.cyber_frameworks')
              AND name = 'name' AND is_nullable = 1)
    THROW 53902, 'V390 check 1b: cyber_frameworks.name lost NOT NULL in the ALTER.', 1;

-- Check 2: the English description now mirrors description_ar (both MAX).
IF COL_LENGTH('cyber.cyber_frameworks', 'description_en')
     <> COL_LENGTH('cyber.cyber_frameworks', 'description_ar')
    THROW 53903, 'V390 check 2: cyber_frameworks.description_en does not mirror description_ar.', 1;

IF COL_LENGTH('cyber.cyber_frameworks', 'description_en') <> -1
    THROW 53904, 'V390 check 2b: description_en is not NVARCHAR(MAX); the entity claims MAX.', 1;

-- Check 3: the ARABIC halves were not touched. The mirror can be satisfied from either side, and
-- narrowing Arabic to meet a narrow English column is the wrong repair — it would refuse Arabic
-- values the register accepts today. Asserted so a later edit cannot "fix" it that way.
IF COL_LENGTH('cyber.cyber_frameworks', 'name_ar') <> 510
    THROW 53905, 'V390 check 3: name_ar is no longer NVARCHAR(255); Arabic must not be narrowed.', 1;

IF COL_LENGTH('cyber.cyber_frameworks', 'description_ar') <> -1
    THROW 53906, 'V390 check 3b: description_ar is no longer NVARCHAR(MAX).', 1;

-- Check 4: this table's pairs are now the whole story — no OTHER En/Ar pair on it disagrees.
-- Scoped to this table because that is what this migration changed (F2-183: a constraint's scope
-- is the writers you close, not the columns you can see).
DECLARE @mismatch INT = (
    SELECT COUNT(*)
      FROM sys.columns ar
      JOIN sys.columns en
        ON en.object_id = ar.object_id
       AND en.name IN (LEFT(ar.name, LEN(ar.name) - 3) + '_en',
                       LEFT(ar.name, LEN(ar.name) - 3))
     WHERE ar.object_id = OBJECT_ID('cyber.cyber_frameworks')
       AND ar.name LIKE '%[_]ar'
       AND ar.max_length <> en.max_length);
IF @mismatch > 0
    THROW 53907, 'V390 check 4: a bilingual pair on cyber_frameworks still has mismatched widths.', 1;

PRINT 'V390 OK: cyber_frameworks name + description_en widened to mirror their Arabic siblings.';
GO
