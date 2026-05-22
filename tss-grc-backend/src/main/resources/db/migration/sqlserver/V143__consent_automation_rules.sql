-- V143: F8 automation rules for ConsentRecordService events.
--
-- Consent is the cornerstone of PDPL — every personal-data
-- processing activity must trace back to a valid lawful basis.
-- Wiring the consent ledger into F8 lets downstream privacy flows
-- (DSR clocks, special-category scrutiny, audit committee reviews)
-- react automatically without each consumer re-implementing the
-- detection.
--
-- ConsentRecordService publishes:
--
--   CONSENT_GRANTED     — new record, ACTIVE (payload carries
--                         legalBasis, purposeCode, dataCategories)
--   CONSENT_WITHDRAWN   — ACTIVE → WITHDRAWN (carries
--                         withdrawnReason if provided)
--   CONSENT_SUPERSEDED  — ACTIVE → SUPERSEDED (carries
--                         supersededByConsentId)
--
-- AR-81..AR-85 — service #12 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-81 ── Consent withdrawn → DPO + downstream-system review
-- PDPL Article 6 makes the right to withdraw consent unconditional.
-- The moment a withdrawal lands, the DPO needs to (1) confirm the
-- downstream systems honour the withdrawal within the required
-- window, and (2) coordinate any data-deletion or processing-stop
-- workflows. We don't auto-create those tasks (each downstream
-- system has its own deletion flow) — the DPO routes them based on
-- which data categories were tied to the withdrawn consent.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-81')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-81',
         N'Consent WITHDRAWN → DPO coordinates downstream-system review',
         N'سحب الموافقة → مسؤول حماية البيانات يراجع الأنظمة المعتمدة',
         N'PDPL Article 6 makes the right to withdraw consent '
         + N'unconditional. DPO routes the downstream review — '
         + N'confirms each system that processed under this consent '
         + N'stops processing within the required window. We don''t '
         + N'auto-create deletion tasks here because each downstream '
         + N'system has its own flow.',
         N'تتطلب المادة 6 من نظام حماية البيانات احترام سحب الموافقة دون شروط — '
         + N'يُبلَّغ مسؤول حماية البيانات لتنسيق إيقاف المعالجة في كل نظام معني خلال المدة المقررة.',
         N'CONSENT_WITHDRAWN',
         N'{}',
         N'{"type":"ESCALATE","roles":["DPO"],"template":"CONSENT_WITHDRAWN"}',
         2, N'CUSTOM');
END

-- AR-82 ── Non-CONSENT legal basis → audit committee periodic review
-- When the lawful basis is anything OTHER than CONSENT (legitimate
-- interest, contract, legal obligation, etc.), PDPL still requires
-- periodic justification review. The audit committee gets a soft
-- notification at grant time so the basis lands in the next
-- legitimate-interest assessment cycle. This is a learning loop /
-- governance trail, not an exception.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-82')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-82',
         N'Legitimate-interest consent granted → audit committee log',
         N'اعتماد معالجة على أساس المصلحة المشروعة → تسجيل لدى لجنة التدقيق',
         N'When the lawful basis is LEGITIMATE_INTEREST (not '
         + N'consent), PDPL still requires periodic justification '
         + N'review. Soft notification to the audit committee so the '
         + N'basis lands in the next legitimate-interest assessment '
         + N'cycle. Governance trail, not an exception.',
         N'تتطلب الموافقة على أساس المصلحة المشروعة مراجعة دورية للمبرر — '
         + N'يُسجَّل لدى لجنة التدقيق ليُدرج في دورة تقييم المصالح المشروعة القادمة.',
         N'CONSENT_GRANTED',
         N'{"legalBasis":"LEGITIMATE_INTEREST"}',
         N'{"type":"NOTIFY","roles":["AUDIT_COMMITTEE","DPO"],"template":"CONSENT_LEGITIMATE_INTEREST"}',
         5, N'CUSTOM');
END

-- AR-83 ── In-person consent capture → DPO records auditability flag
-- Consent captured IN_PERSON (paper sign-up, branch interaction)
-- doesn't have an automated audit trail by default — there's no
-- form-submission log, no email receipt, just the paper artefact.
-- DPO gets a notification so the source artefact (signed form,
-- recorded video, etc.) lands in the evidence vault. Without this
-- the consent record is unfalsifiable but also un-defensible at
-- audit time.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-83')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-83',
         N'In-person consent captured → DPO archives source artefact',
         N'موافقة شخصية → مسؤول حماية البيانات يحفظ المصدر',
         N'IN_PERSON consent (branch sign-up, paper form, '
         + N'recorded conversation) lacks an automated audit trail. '
         + N'DPO archives the source artefact (signed form, '
         + N'recording) in the evidence vault — without it the '
         + N'record is un-defensible at audit time.',
         N'الموافقة الشخصية لا توثَّق آلياً — يحفظ مسؤول حماية البيانات المصدر (نموذج موقَّع، تسجيل) في الأرشيف لضمان التثبت لاحقاً.',
         N'CONSENT_GRANTED',
         N'{"grantedVia":"IN_PERSON"}',
         N'{"type":"NOTIFY","roles":["DPO"],"template":"CONSENT_IN_PERSON_ARCHIVE"}',
         3, N'CUSTOM');
END

-- AR-84 ── Consent SUPERSEDED → marketing + analytics teams notified
-- When a consent text is reissued (new terms, version bump), every
-- system that consumed the prior version needs to start serving the
-- new version's preferences. Marketing (drives campaign-eligibility
-- queries) and analytics (drives tracking flags) are the two
-- dominant consumers — they get the heads-up so they can flush
-- caches / re-resolve the subject's current state.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-84')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-84',
         N'Consent SUPERSEDED → notify marketing + analytics teams',
         N'استبدال الموافقة → إشعار فرق التسويق والتحليلات',
         N'When consent text is reissued (new terms, version bump), '
         + N'every consumer must serve the new version. Marketing '
         + N'(drives campaign-eligibility queries) and analytics '
         + N'(drives tracking flags) are dominant consumers — they '
         + N'flush caches / re-resolve subject state on notification.',
         N'عند إعادة إصدار نص الموافقة بنسخة جديدة، يحتاج كل نظام مستهلك للتحديث — '
         + N'يُبلَّغ التسويق والتحليلات لإعادة قراءة حالة الموافقة من السجل.',
         N'CONSENT_SUPERSEDED',
         N'{}',
         N'{"type":"NOTIFY","roles":["MARKETING_LEAD","ANALYTICS_LEAD","DPO"],"template":"CONSENT_SUPERSEDED"}',
         4, N'CUSTOM');
END

-- AR-85 ── Withdrawal-with-reason → record reason in privacy register
-- When a data subject provides a reason for withdrawal (e.g.
-- "Service no longer meets my needs", "Concerns about how my data
-- is used"), the reason is a high-value signal for the privacy
-- program — patterns in withdrawal reasons drive policy updates
-- and trust-rebuilding work. DPO logs the reason in the privacy
-- register's withdrawal-reason analysis bucket.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-85')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-85',
         N'Withdrawal-with-reason → privacy program records signal',
         N'سحب موافقة مع سبب → برنامج الخصوصية يسجل المؤشر',
         N'A reason-with-withdrawal is high-value signal — patterns '
         + N'in withdrawal reasons drive policy updates and '
         + N'trust-rebuilding work. DPO + privacy program lead see '
         + N'each reason so they can group + analyse trends in the '
         + N'privacy register''s withdrawal-reason bucket.',
         N'سبب سحب الموافقة مؤشر مهم لتحسين السياسة وإعادة بناء الثقة — '
         + N'يصل مسؤول حماية البيانات وقائد برنامج الخصوصية لتحليل النمط ضمن سجل أسباب السحب.',
         N'CONSENT_WITHDRAWN',
         N'{}',
         N'{"type":"NOTIFY","roles":["DPO","PRIVACY_PROGRAM_LEAD"],"template":"CONSENT_WITHDRAWAL_REASON"}',
         5, N'CUSTOM');
END
GO

PRINT 'V143 seeded 5 consent automation rules (AR-81..AR-85).';
GO
