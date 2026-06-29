-- =====================================================================
-- V236 — F8 rule: EVIDENCE_REQUEST_REJECTED → CREATE_RECORD BcmGap (epic B-D)
-- ---------------------------------------------------------------------
-- When a BCM-anchored evidence request (B-B) is rejected, open a BCM gap so the
-- rejection is tracked to closure (review epic B-D: evidence lifecycle —
-- reject → gap). Handled by BcmGapRecordCreator (entity "BcmGap"), which only
-- acts when the request's anchorType is a BCM entity type and de-duplicates
-- against the source request, so the rule can stay unconditional and harmlessly
-- no-op for non-BCM rejections.
--
-- Idempotent: inserted only if the rule_code is absent. Seeded for org 1, matching
-- the V96/V142/V234 rule-seed convention.
-- =====================================================================

-- (a) Allow 'EVIDENCE' as a gap source. The ck_gap_source CHECK constraint
-- predates the evidence→gap link; widen it so BcmGapRecordCreator can persist
-- gaps with source='EVIDENCE' (the Java GAP_SOURCES set already allows it).
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_gap_source'
           AND parent_object_id = OBJECT_ID('bcm.gaps'))
    ALTER TABLE bcm.gaps DROP CONSTRAINT ck_gap_source;
GO
ALTER TABLE bcm.gaps ADD CONSTRAINT ck_gap_source CHECK
    (source IN ('BIA','BCP','DRP','EXERCISE','CALL_TREE','AUDIT','RISK','EVIDENCE','MANUAL'));
GO

-- (b) The rule itself.
IF NOT EXISTS (
    SELECT 1 FROM platform.automation_rules
     WHERE organisation_id = 1 AND rule_code = 'AR-BCM-EVD-GAP')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (1, 'AR-BCM-EVD-GAP',
         N'BCM evidence rejected → open a continuity gap',
         N'رفض دليل الاستمرارية ← فتح فجوة',
         N'When an evidence request anchored to a BCM record (plan, BIA, governance, '
         + N'crisis, CAPA) is rejected, open a BCM gap (source EVIDENCE) so it is tracked '
         + N'to closure. The creator de-duplicates against the source request.',
         N'عند رفض طلب دليل مرتبط بسجل استمرارية الأعمال، يتم فتح فجوة (المصدر: دليل) '
         + N'لتتبّعها حتى الإغلاق مع منع التكرار.',
         N'EVIDENCE_REQUEST_REJECTED',
         N'{}',
         N'{"type":"CREATE_RECORD","entity":"BcmGap","fields":{"severity":"HIGH","due_in_days":30}}',
         25, N'CUSTOM');
END
GO
