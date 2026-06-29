-- =====================================================================
-- V234 — F8 rule: EVIDENCE_REJECTED → CREATE_RECORD AuditFinding (review item 20)
-- ---------------------------------------------------------------------
-- The platform already escalates rejections (V142) and reopens an EXISTING control
-- gap (AR-17, V96, FLAG Gap status→OPEN). The one remaining sliver of review item 20
-- ("create a gap if missing/rejected") is opening a finding when none exists yet.
--
-- This adds a CREATE_RECORD rule handled by AuditFindingRecordCreator (entity
-- "AuditFinding"). The creator de-duplicates against AR-17 by storing the event's
-- source marker (correlation id, e.g. "evidence:55") in clause_ref and skipping if
-- an open finding for that marker already exists — so the two rules never double up.
--
-- Idempotent: inserted only if the rule_code is absent. Seeded for org 1 (the demo
-- tenant), matching the V96/V142 rule-seed convention.
-- =====================================================================

IF NOT EXISTS (
    SELECT 1 FROM platform.automation_rules
     WHERE organisation_id = 1 AND rule_code = 'AR-EVD-GAP')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (1, 'AR-EVD-GAP',
         N'Evidence rejected → open a control-gap finding',
         N'رفض الدليل ← فتح ملاحظة فجوة ضابط',
         N'When evidence is rejected and no open gap already covers it, create an audit '
         + N'finding (CAPA) so the gap is tracked to closure. Complements AR-17, which '
         + N'reopens an existing gap; the creator de-duplicates so they never double up.',
         N'عند رفض الدليل وعدم وجود فجوة مفتوحة تغطّيه، يتم إنشاء ملاحظة تدقيق (إجراء تصحيحي) '
         + N'لتتبّع المعالجة حتى الإغلاق. تُكمّل القاعدة AR-17 مع منع التكرار.',
         N'EVIDENCE_REJECTED',
         N'{}',
         N'{"type":"CREATE_RECORD","entity":"AuditFinding","fields":{"severity":"HIGH","title_template":"Evidence rejected — control gap (sub-control %subControlId%)","due_in_days":30}}',
         25, N'CUSTOM');
END
GO
