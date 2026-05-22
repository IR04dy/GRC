-- V142: F8 automation rules for EvidenceItemService events.
--
-- Evidence is the heartbeat of compliance — every framework control
-- ultimately resolves to one or more approved evidence items. Wiring
-- evidence lifecycle into F8 lets downstream attestation campaigns,
-- audit-pack assembly, and posture-refresh flows react automatically
-- to the four lifecycle moments that matter.
--
-- EvidenceItemService publishes:
--
--   EVIDENCE_CREATED        — new item logged (DRAFT default)
--   EVIDENCE_SUBMITTED      — DRAFT → PENDING_REVIEW
--   EVIDENCE_APPROVED       — PENDING_REVIEW → APPROVED
--   EVIDENCE_REJECTED       — PENDING_REVIEW → REJECTED
--   EVIDENCE_TRANSITIONED   — catch-all for other status moves
--   EVIDENCE_DELETED        — soft-delete
--
-- AR-76..AR-80 — service #11 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-76 ── Evidence submitted for review → notify reviewer queue
-- When a submitter moves a piece of evidence from DRAFT to
-- PENDING_REVIEW, the reviewer (compliance officer for general
-- evidence, control owner for control-specific evidence) needs to
-- know it's in their queue. Without a notification, evidence can sit
-- pending for days while the audit clock keeps ticking.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-76')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-76',
         N'Evidence submitted for review → notify reviewer queue',
         N'إرسال أدلة للمراجعة → إشعار قائمة المراجعين',
         N'Evidence moving from DRAFT to PENDING_REVIEW needs eyes '
         + N'on it the same day or the audit clock keeps ticking. '
         + N'Compliance officer reviews general evidence; control '
         + N'owner reviews control-specific evidence — both get the '
         + N'notification so whichever owns this item picks it up.',
         N'يحتاج كل دليل في قائمة المراجعة لمراجعة عاجلة قبل اقتراب موعد التدقيق — '
         + N'يصل الإشعار لمسؤول الالتزام ومالك الضابط لضمان المعالجة الفورية.',
         N'EVIDENCE_SUBMITTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_OFFICER","CONTROL_OWNER"],"template":"EVIDENCE_SUBMITTED"}',
         3, N'CUSTOM');
END

-- AR-77 ── Evidence APPROVED → posture refresh hint to compliance lead
-- Every approval potentially shifts the organisation's posture for
-- one or more framework controls. The compliance lead gets a soft
-- notification so they can decide whether the change is significant
-- enough to refresh the posture dashboard before the next reporting
-- cycle. (We don't auto-refresh here — recomputing posture is
-- expensive and shouldn't happen per-evidence; the lead batches it.)
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-77')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-77',
         N'Evidence APPROVED → posture refresh hint to compliance lead',
         N'اعتماد الأدلة → تنبيه لمسؤول الالتزام لتحديث المؤشرات',
         N'Every approval can shift the organisation''s posture for '
         + N'one or more framework controls. Compliance lead gets a '
         + N'soft notification so they decide whether to refresh the '
         + N'posture dashboard now or batch it for the next cycle — '
         + N'recomputing posture per-evidence would be too expensive.',
         N'كل اعتماد دليل قد يحدث مؤشرات الالتزام للضوابط ذات الصلة — '
         + N'يصل الإشعار لمسؤول الالتزام ليقرر التحديث الفوري أو ضمن الدورة القادمة.',
         N'EVIDENCE_APPROVED',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_LEAD"],"template":"EVIDENCE_APPROVED_POSTURE"}',
         5, N'CUSTOM');
END

-- AR-78 ── Evidence REJECTED → escalate to owner + compliance officer
-- A rejection means the submitter has work to do — either the
-- evidence didn't meet the control criteria or quality bar. The
-- owner (submitter) needs to know why; the compliance officer joins
-- in case the rejection signals a deeper control gap rather than a
-- one-off evidence quality issue.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-78')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-78',
         N'Evidence REJECTED → escalate to owner + compliance officer',
         N'رفض الأدلة → تصعيد للمالك ومسؤول الالتزام',
         N'A rejection means rework — either the evidence missed the '
         + N'control criteria or the quality bar. Owner (submitter) '
         + N'needs to know why; compliance officer joins in case the '
         + N'rejection signals a deeper control gap rather than a '
         + N'one-off quality issue.',
         N'رفض الدليل يستوجب إعادة عمل — يصل المالك لمعرفة السبب، ومسؤول الالتزام لتقدير ما إذا كان مؤشراً على فجوة أعمق في الضابط.',
         N'EVIDENCE_REJECTED',
         N'{}',
         N'{"type":"ESCALATE","roles":["EVIDENCE_OWNER","COMPLIANCE_OFFICER"],"template":"EVIDENCE_REJECTED"}',
         3, N'CUSTOM');
END

-- AR-79 ── External-source evidence APPROVED → archive copy to vault
-- Evidence sourced externally (PEN_TEST reports, BUG_BOUNTY findings,
-- TENABLE/QUALYS scan exports, third-party audit reports) needs a
-- separate copy in the long-term audit vault — these are the rows
-- regulators most often demand during an examination years later.
-- We notify the audit-pack manager to mirror the file to cold
-- storage rather than auto-doing it (the vault may require manual
-- encryption / retention-tag selection per evidence type).
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-79')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-79',
         N'External-source evidence APPROVED → archive copy to vault',
         N'اعتماد دليل من مصدر خارجي → نسخة احتياطية في الأرشيف',
         N'External-source evidence (penetration tests, third-party '
         + N'audit reports, vendor scan exports) is what regulators '
         + N'demand during examinations years later. Audit-pack '
         + N'manager mirrors the file to the long-term vault — we '
         + N'notify rather than auto-copy because retention tagging '
         + N'and encryption may vary per evidence type.',
         N'الأدلة من مصادر خارجية (اختبارات الاختراق، تقارير المدققين، تقارير المخاطر من الموردين) '
         + N'هي ما تطلبه الجهات التنظيمية لاحقاً — يُبلَّغ مسؤول حزمة التدقيق لنسخها للأرشيف طويل الأمد.',
         N'EVIDENCE_APPROVED',
         N'{"sourceModule":"EXTERNAL"}',
         N'{"type":"NOTIFY","roles":["AUDIT_PACK_MANAGER"],"template":"EVIDENCE_EXTERNAL_VAULT"}',
         4, N'CUSTOM');
END

-- AR-80 ── Evidence DELETED (post-approval) → audit committee flag
-- Soft-deleting an APPROVED evidence row is unusual — it suggests
-- either the underlying control is being retired (legitimate) or
-- someone is trying to clean up failing posture (concerning). The
-- audit committee gets a FLAG so the change is captured in the
-- meeting pack regardless of which case it turns out to be. The
-- compliance officer joins because they need to revisit any audit
-- packs the deleted evidence was bound to.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-80')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-80',
         N'Evidence DELETED post-approval → flag audit committee',
         N'حذف دليل معتمد → تنبيه لجنة التدقيق',
         N'Soft-deleting an APPROVED evidence row is unusual — '
         + N'either control retirement (legitimate) or someone '
         + N'cleaning up failing posture (concerning). Audit '
         + N'committee gets a FLAG so the change lands in the '
         + N'meeting pack regardless of cause; compliance officer '
         + N'revisits any audit packs bound to the deleted row.',
         N'حذف دليل معتمد إجراء غير معتاد — قد يكون شطباً مشروعاً للضابط أو محاولة تجميل للمؤشرات. '
         + N'تُبلَّغ لجنة التدقيق للتسجيل في حزمة الاجتماع، ومسؤول الالتزام لمراجعة حزم التدقيق المرتبطة.',
         N'EVIDENCE_DELETED',
         N'{"previousStatus":"APPROVED"}',
         N'{"type":"FLAG","roles":["AUDIT_COMMITTEE","COMPLIANCE_OFFICER"],"template":"EVIDENCE_APPROVED_DELETED"}',
         2, N'CUSTOM');
END
GO

PRINT 'V142 seeded 5 evidence automation rules (AR-76..AR-80).';
GO
