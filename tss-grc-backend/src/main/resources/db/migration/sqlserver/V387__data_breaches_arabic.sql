/* ============================================================================================
   V387 — F2-271: a PDPL Article 20 breach record could not be described in Arabic.

   `privacy.data_breaches` carried `title_en` (NOT NULL) and `description_en` and **no `*_ar`
   column of any kind**, on a platform whose PRIMARY language is Arabic. House rule 7 — *"user-
   visible records carry `*_en` AND `*_ar`; Arabic names are backend-mandatory"* — and this is the
   statutory personal-data-breach register, so its title and description are exactly the
   user-visible governance text the rule is about.

   F2-209 filed this by name. F2-211 and F2-214 both worked on the same register and recorded the
   absence in a comment (`lib/api.ts`: *"There is no `severity` and no `titleAr` column on
   privacy.data_breaches"*) rather than closing it — the absence was documented three times.

   ⚠ The platform has TWO breach registers and the one that already supports Arabic is not this
   one: `pages/dataOfficer/BreachPage.tsx` collects `titleEn` AND `titleAr` for
   `data_officer.breach`. F2-196's disjoint-register shape, with the statutory half the poorer of
   the two. This migration does not reconcile them — picking a winner between two populated
   registers is an owner decision — it closes the language gap on the PDPL one.

   Both columns are NULLABLE and neither is backfilled:

     • NULLABLE, so this cannot break an existing row (F2-214). Every one of the 17 rows in dev and
       18 in prod (the two databases differ, which is why that is stated as a measurement) predates
       the column, and a NOT NULL would refuse all of them.
     • NOT backfilled, because nothing can invent Arabic governance text. F2-30's ratchet records
       the same reasoning for the 35 pairs already holding blank Arabic: *"nothing can invent
       Arabic governance text"*, so the honest state is absent rather than machine-translated.
     • And deliberately NOT annotated `@NotBlank`. Bean validation fires on UPDATE as well as
       insert, so requiring Arabic would make all 35 existing breaches uneditable until somebody
       supplies it — F2-30's exact reason for its own ratchet being a ratchet.

   Widths mirror the English siblings, checked against `sys.columns` rather than assumed:
   `title_en` is NVARCHAR(255) (max_length 510 bytes) and `description_en` is NVARCHAR(MAX).
   Mirroring matters — a narrower Arabic column would truncate a title the English one accepts,
   and Arabic text is not shorter than its English equivalent.
   ============================================================================================ */

SET NOCOUNT ON;
GO

/* ── check 0: the table must exist ── */
IF OBJECT_ID('privacy.data_breaches') IS NULL
BEGIN
    THROW 51387, 'V387: privacy.data_breaches does not exist. This migration belongs after the privacy module''s tables; do not skip it.', 1;
END
GO

/* ── the columns, idempotent so a re-run is a no-op ── */
IF COL_LENGTH('privacy.data_breaches', 'title_ar') IS NULL
BEGIN
    ALTER TABLE privacy.data_breaches ADD title_ar NVARCHAR(255) NULL;
END
GO

IF COL_LENGTH('privacy.data_breaches', 'description_ar') IS NULL
BEGIN
    ALTER TABLE privacy.data_breaches ADD description_ar NVARCHAR(MAX) NULL;
END
GO

/* ── check 1: assert what this migration claims to have done, rather than assuming it ── */
IF COL_LENGTH('privacy.data_breaches', 'title_ar') IS NULL
   OR COL_LENGTH('privacy.data_breaches', 'description_ar') IS NULL
BEGIN
    THROW 51387, 'V387: title_ar or description_ar is missing after the ALTER. The bilingual gate counts this table''s title_en as EN_ONLY until both exist.', 1;
END
GO

/* ── check 2: the Arabic columns must MIRROR their English siblings' widths. A narrower Arabic ──
   ── column would truncate a title the English one accepts, and the truncation would surface as ──
   ── an 8152 on the Arabic-primary path only.                                                  ── */
IF (SELECT c.max_length FROM sys.columns c
     WHERE c.object_id = OBJECT_ID('privacy.data_breaches') AND c.name = 'title_ar')
   <> (SELECT c.max_length FROM sys.columns c
        WHERE c.object_id = OBJECT_ID('privacy.data_breaches') AND c.name = 'title_en')
BEGIN
    THROW 51387, 'V387: title_ar and title_en have different widths. Mirror the English sibling: a narrower Arabic column truncates a title the English one accepts, and Arabic is the primary language here.', 1;
END
GO

IF (SELECT c.max_length FROM sys.columns c
     WHERE c.object_id = OBJECT_ID('privacy.data_breaches') AND c.name = 'description_ar')
   <> (SELECT c.max_length FROM sys.columns c
        WHERE c.object_id = OBJECT_ID('privacy.data_breaches') AND c.name = 'description_en')
BEGIN
    THROW 51387, 'V387: description_ar and description_en have different widths (both should be NVARCHAR(MAX), max_length -1).', 1;
END
GO

/* ── check 3: both columns must be NULLABLE. A NOT NULL here would refuse every existing row, ──
   ── and bean validation on the entity would make each of them uneditable (F2-30's reason).   ── */
IF EXISTS (SELECT 1 FROM sys.columns
            WHERE object_id = OBJECT_ID('privacy.data_breaches')
              AND name IN ('title_ar', 'description_ar')
              AND is_nullable = 0)
BEGIN
    THROW 51387, 'V387: title_ar / description_ar must be NULLABLE. Nothing can invent Arabic governance text for the rows that predate these columns, so the honest state is absent rather than machine-translated.', 1;
END
GO

/* ⚙ Deliberately NOT done, recorded rather than left as absences:

   (a) No backfill and no machine translation. An empty Arabic title is an honest "not recorded";
       a generated one is a fabricated governance record, which is the whole subject of F2-193
       through F2-269.

   (b) No @NotBlank. See F2-30's ratchet: the constraint fires on UPDATE too, so requiring Arabic
       would make every pre-existing breach uneditable. The column becomes visible to
       BilingualInvariantRatchetTest's PAIR scan the moment it exists, which is the mechanism that
       lets the annotation be added later when the rows have Arabic — and F2-30's javadoc already
       records that exact transition happening for Notification.messageAr.

   (c) The two disjoint breach registers are not reconciled. data_officer.breach already carries
       Arabic; choosing which register is authoritative for a PDPL breach changes what the platform
       reports to SDAIA and is an owner decision (F2-196).                                        */
