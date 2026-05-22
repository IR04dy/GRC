-- V144: F8 automation rules for DsrCaseService events.
--
-- DSR (Data Subject Rights) is the most regulator-watched workflow
-- in the platform. PDPL Article 4 gives data subjects a 30-day SLA
-- on access/correction/deletion requests, extensible once to 60 days
-- with a documented reason. The Saudi Data Office spot-checks both
-- the SLA and the extension justification, and a missed deadline is
-- itself a reportable incident.
--
-- DsrCaseService publishes:
--
--   DSR_RECEIVED            — new case opened
--   DSR_IDENTITY_VERIFIED   — identity check passed
--   DSR_COLLECTING          — data extraction started
--   DSR_REDACTING           — redaction in progress
--   DSR_DELIVERED           — terminal success
--   DSR_EXTENDED            — Article 4 30-day extension invoked
--   DSR_REJECTED            — terminal denial
--   DSR_TRANSITIONED        — catch-all
--
-- AR-86..AR-90 — service #13 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-86 ── DSR received → notify DPO + privacy program lead
-- Every DSR starts a 30-day regulator clock. DPO routes the case
-- (verifies which RoPA entries are in scope) and the privacy program
-- lead tracks aggregate SLA performance. P1 because the clock starts
-- ticking the moment the row lands.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-86')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-86',
         N'DSR received → notify DPO + privacy program lead',
         N'استلام طلب حقوق صاحب البيانات → إشعار مسؤول حماية البيانات وقائد برنامج الخصوصية',
         N'Every DSR starts a 30-day regulator clock. DPO routes the '
         + N'case (which RoPA entries are in scope, identity-check '
         + N'method) and the privacy program lead tracks aggregate '
         + N'SLA performance for the regulator-facing dashboard.',
         N'يبدأ عداد 30 يوماً مع كل طلب حقوق — يصل الإشعار لمسؤول حماية البيانات لتوجيه الطلب '
         + N'وقائد برنامج الخصوصية لمتابعة الالتزام الإجمالي بمستوى الخدمة.',
         N'DSR_RECEIVED',
         N'{}',
         N'{"type":"NOTIFY","roles":["DPO","PRIVACY_PROGRAM_LEAD"],"template":"DSR_RECEIVED"}',
         1, N'CUSTOM');
END

-- AR-87 ── DSR extension invoked → DPO + GC review the justification
-- The Article 4 extension is the rare second-clock — extending past
-- 30 days requires a documented justification, and the Saudi Data
-- Office reviews these patterns. DPO + General Counsel see the
-- extension before delivery so they can cure any documentation gap.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-87')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-87',
         N'DSR extension invoked → DPO + GC review justification',
         N'تمديد طلب حقوق صاحب البيانات → مراجعة المبرر من مسؤول حماية البيانات والمستشار العام',
         N'PDPL Article 4 allows one 30-day extension with a '
         + N'documented justification. The Saudi Data Office reviews '
         + N'extension patterns — DPO + General Counsel see each one '
         + N'before delivery so the documentation gap (if any) can '
         + N'be cured before the regulator asks.',
         N'تجيز المادة 4 تمديد المهلة 30 يوماً بمبرر موثَّق — يراجع مسؤول حماية البيانات والمستشار العام '
         + N'المبرر قبل التسليم تحسباً لأي مراجعة من مكتب البيانات السعودي.',
         N'DSR_EXTENDED',
         N'{}',
         N'{"type":"ESCALATE","roles":["DPO","GENERAL_COUNSEL"],"template":"DSR_EXTENDED"}',
         2, N'CUSTOM');
END

-- AR-88 ── DSR rejected → DPO + GC verify lawful basis for refusal
-- Refusing a DSR is legally significant — the data subject can
-- escalate to the regulator, who will examine the refusal basis.
-- DPO + GC verify the rejection cites a valid PDPL exemption
-- (e.g. legal-obligation conflict, manifestly unfounded request)
-- before the refusal letter goes out.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-88')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-88',
         N'DSR rejected → DPO + GC verify lawful basis',
         N'رفض طلب حقوق صاحب البيانات → التحقق من السبب المشروع لرفض الطلب',
         N'Refusing a DSR is legally significant — the data subject '
         + N'can escalate to the regulator. DPO + GC verify the '
         + N'rejection cites a valid PDPL exemption (legal-obligation '
         + N'conflict, manifestly unfounded request, etc.) before the '
         + N'refusal letter goes out.',
         N'رفض طلب حقوق صاحب البيانات قرار قانوني — يمكن لصاحب البيانات اللجوء للجهة التنظيمية. '
         + N'يتحقق مسؤول حماية البيانات والمستشار العام من استناد الرفض لاستثناء صحيح من النظام قبل إرسال الرد.',
         N'DSR_REJECTED',
         N'{}',
         N'{"type":"ESCALATE","roles":["DPO","GENERAL_COUNSEL"],"template":"DSR_REJECTED"}',
         1, N'CUSTOM');
END

-- AR-89 ── DSR delivered → audit committee SLA-performance feed
-- Each delivery feeds the audit committee's DSR-SLA KPI (% of
-- requests delivered within the 30-day or 60-day window).
-- Milestone-style notification keeps the dashboard current without
-- batch reprocessing.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-89')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-89',
         N'DSR delivered → audit committee SLA-performance feed',
         N'تسليم طلب حقوق صاحب البيانات → تغذية مؤشر الأداء لدى لجنة التدقيق',
         N'Each delivery feeds the audit committee''s DSR-SLA KPI — '
         + N'percentage of requests delivered within the 30-day or '
         + N'60-day extended window. Milestone-style notification '
         + N'keeps the dashboard current without batch reprocessing.',
         N'تساهم كل عملية تسليم في احتساب مؤشر الالتزام بمستوى الخدمة لدى لجنة التدقيق — '
         + N'يُسجَّل معلم فور التسليم لتحديث المؤشر بشكل آني.',
         N'DSR_DELIVERED',
         N'{}',
         N'{"type":"NOTIFY","roles":["AUDIT_COMMITTEE","PRIVACY_PROGRAM_LEAD"],"template":"DSR_DELIVERED_KPI"}',
         5, N'CUSTOM');
END

-- AR-90 ── DSR ERASURE type received → IT data-deletion task created
-- ERASURE (right-to-be-forgotten) requests trigger a deeper
-- downstream workflow than ACCESS/CORRECTION — every system holding
-- the subject's data must be told to delete. IT operations get a
-- task-creation notification so they can spin up the per-system
-- deletion checklist immediately rather than at the redaction
-- phase. We don't auto-create the deletion tasks (the per-system
-- list varies by RoPA scope) — IT operations owns that routing.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-90')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-90',
         N'DSR ERASURE received → IT operations prepare deletion checklist',
         N'طلب حق المحو → فريق العمليات يجهز قائمة الحذف من الأنظمة',
         N'ERASURE (right-to-be-forgotten) requests trigger a deeper '
         + N'workflow than ACCESS — every system holding the '
         + N'subject''s data must be told to delete. IT operations '
         + N'gets the heads-up at receipt so they spin up the '
         + N'per-system deletion checklist rather than starting at '
         + N'the redaction phase.',
         N'يستوجب حق المحو إجراءات أعمق من حق الوصول — '
         + N'يُبلَّغ فريق العمليات فور الاستلام لإعداد قائمة الحذف من الأنظمة قبل وصول الطلب لمرحلة التهيئة.',
         N'DSR_RECEIVED',
         N'{"type":"ERASURE"}',
         N'{"type":"NOTIFY","roles":["IT_OPERATIONS","DPO"],"template":"DSR_ERASURE_PREP"}',
         3, N'CUSTOM');
END
GO

PRINT 'V144 seeded 5 DSR automation rules (AR-86..AR-90).';
GO
