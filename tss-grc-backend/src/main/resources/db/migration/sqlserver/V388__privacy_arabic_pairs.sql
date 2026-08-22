/* ============================================================================================
   V388 — F2-272: four PDPL registers were made bilingual and each missed exactly one field.

   V387 closed `privacy.data_breaches`. Working down F2-271's EN_ONLY list, the `privacy` schema
   held five members. Reading each rather than batching them (F2-271 filed them with NO verdicts
   for this reason) gives four to close and one to exclude — and the shape the four share is the
   finding:

     privacy.processing_activities.purpose_en       ← table already has activity_name_ar
     privacy.disposal_certificates.method_details_en ← already has scope_description_ar
     privacy.privacy_notices.archive_reason_en      ← already has title_ar, purpose_ar,
                                                       retention_period_ar, body_ar
     privacy.data_sharing_agreements.termination_reason_en ← already has title_ar, purpose_ar

   🔑 Every one of these tables is ALREADY BILINGUAL for its other text columns and monolingual for
   exactly one. So this is not "the module has no Arabic": it is F2-215's two-of-N inside a single
   table, four times over. `privacy.privacy_notices` is the sharpest — four Arabic columns and no
   Arabic for the reason a notice was archived.

   The sharpest by statute is `purpose_en`: the PDPL Article 31 record of processing activities is
   required to state the PURPOSE of each activity, the column is populated on 11 of 11 rows in both
   databases, and the activity's NAME already has Arabic beside it.

   ⚠ `privacy.cross_border_transfers.ropa_title_en` is deliberately NOT here. It is a denormalised
   label stored from the caller's body (`t.setRopaTitleEn(body.ropaTitleEn())`) beside a `ropa_code`
   soft reference — F2-245's parent-identity-copy family, where the rule is to DERIVE the label from
   the resolved parent rather than store it. Deriving yields both languages for free, because
   `processing_activities` carries `activity_name_en` AND `activity_name_ar`; adding
   `ropa_title_ar` would instead create a SECOND copy to keep in sync, which is the defect F2-245
   measured at 11 of 11 divergent rows on `risk.scenarios`. Filed for that family, not fixed here.

   Same shape as V387, and the same reasons:
     • NULLABLE — a NOT NULL would refuse every row that predates the column.
     • WIDTH-MIRRORED against the English sibling, read from `sys.columns` rather than assumed. A
       narrower Arabic column truncates text the English one accepts, and Arabic is not shorter.
     • NO backfill — nothing can invent Arabic governance text, so absent is the honest state and a
       machine translation would be a fabricated record.
     • NO `@NotBlank` — bean validation fires on UPDATE too, so requiring Arabic would make every
       pre-existing row uneditable (F2-30's reason for its own ratchet).

   Live rows measured in BOTH databases and identical: processing_activities 11 (purpose_en 11),
   disposal_certificates 7 (5), privacy_notices 7 (2), data_sharing_agreements 8 (0).
   ============================================================================================ */

SET NOCOUNT ON;
GO

/* ── check 0: all four tables must exist ── */
IF OBJECT_ID('privacy.processing_activities') IS NULL
   OR OBJECT_ID('privacy.disposal_certificates') IS NULL
   OR OBJECT_ID('privacy.privacy_notices') IS NULL
   OR OBJECT_ID('privacy.data_sharing_agreements') IS NULL
BEGIN
    THROW 51388, 'V388: one of the four privacy tables does not exist. This migration belongs after the privacy module and its PDPL extensions; do not skip it.', 1;
END
GO

IF COL_LENGTH('privacy.processing_activities', 'purpose_ar') IS NULL
BEGIN
    ALTER TABLE privacy.processing_activities ADD purpose_ar NVARCHAR(MAX) NULL;
END
GO

IF COL_LENGTH('privacy.disposal_certificates', 'method_details_ar') IS NULL
BEGIN
    ALTER TABLE privacy.disposal_certificates ADD method_details_ar NVARCHAR(MAX) NULL;
END
GO

IF COL_LENGTH('privacy.privacy_notices', 'archive_reason_ar') IS NULL
BEGIN
    ALTER TABLE privacy.privacy_notices ADD archive_reason_ar NVARCHAR(500) NULL;
END
GO

IF COL_LENGTH('privacy.data_sharing_agreements', 'termination_reason_ar') IS NULL
BEGIN
    ALTER TABLE privacy.data_sharing_agreements ADD termination_reason_ar NVARCHAR(MAX) NULL;
END
GO

/* ── check 1: all four exist after the ALTERs ── */
IF COL_LENGTH('privacy.processing_activities', 'purpose_ar') IS NULL
   OR COL_LENGTH('privacy.disposal_certificates', 'method_details_ar') IS NULL
   OR COL_LENGTH('privacy.privacy_notices', 'archive_reason_ar') IS NULL
   OR COL_LENGTH('privacy.data_sharing_agreements', 'termination_reason_ar') IS NULL
BEGIN
    THROW 51388, 'V388: at least one Arabic column is missing after the ALTERs. BilingualInvariantRatchetTest counts the English sibling as EN_ONLY until both exist.', 1;
END
GO

/* ── check 2: each Arabic column must MIRROR its English sibling's width. Written as one ──
   ── set-based comparison so a fifth pair added later is covered by the same check.       ── */
IF EXISTS (
    SELECT 1
      FROM (VALUES
              ('privacy.processing_activities',   'purpose_en',            'purpose_ar'),
              ('privacy.disposal_certificates',   'method_details_en',     'method_details_ar'),
              ('privacy.privacy_notices',         'archive_reason_en',     'archive_reason_ar'),
              ('privacy.data_sharing_agreements', 'termination_reason_en', 'termination_reason_ar')
           ) AS p(tbl, en, ar)
     WHERE (SELECT c.max_length FROM sys.columns c
             WHERE c.object_id = OBJECT_ID(p.tbl) AND c.name = p.en)
        <> (SELECT c.max_length FROM sys.columns c
             WHERE c.object_id = OBJECT_ID(p.tbl) AND c.name = p.ar))
BEGIN
    THROW 51388, 'V388: an Arabic column does not mirror its English sibling''s width. A narrower Arabic column truncates text the English one accepts, and the failure would surface as an 8152 on the Arabic-primary path only.', 1;
END
GO

/* ── check 3: every new column must be NULLABLE ── */
IF EXISTS (
    SELECT 1 FROM sys.columns c
     WHERE (c.object_id = OBJECT_ID('privacy.processing_activities')   AND c.name = 'purpose_ar'
        OR  c.object_id = OBJECT_ID('privacy.disposal_certificates')   AND c.name = 'method_details_ar'
        OR  c.object_id = OBJECT_ID('privacy.privacy_notices')         AND c.name = 'archive_reason_ar'
        OR  c.object_id = OBJECT_ID('privacy.data_sharing_agreements') AND c.name = 'termination_reason_ar')
       AND c.is_nullable = 0)
BEGIN
    THROW 51388, 'V388: the Arabic columns must be NULLABLE. Nothing can invent Arabic governance text for the rows that predate them, so the honest state is absent rather than machine-translated.', 1;
END
GO

/* ── check 4: nothing may have been backfilled. An empty Arabic field is an honest "not ──
   ── recorded"; a generated one is a fabricated governance record.                       ── */
IF (SELECT COUNT(purpose_ar) FROM privacy.processing_activities) > 0
   OR (SELECT COUNT(method_details_ar) FROM privacy.disposal_certificates) > 0
   OR (SELECT COUNT(archive_reason_ar) FROM privacy.privacy_notices) > 0
   OR (SELECT COUNT(termination_reason_ar) FROM privacy.data_sharing_agreements) > 0
BEGIN
    THROW 51388, 'V388: an Arabic column holds data immediately after being created, so something backfilled it. This migration must never do that - a machine-translated PDPL record is a fabricated governance record.', 1;
END
GO
