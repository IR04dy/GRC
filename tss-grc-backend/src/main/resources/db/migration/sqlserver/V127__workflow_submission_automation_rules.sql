-- V127: F8 automation rules for workflow_submissions events.
--
-- The new WorkflowSubmissionService now publishes two DomainEvents:
--   WORKFLOW_SUBMISSION_CREATED   — wizard submitted
--   WORKFLOW_SUBMISSION_REVIEWED  — approve / reject / close / under-review
--
-- These five seed rules route the most regulator-sensitive wizards to
-- the right reviewer/role automatically. Conditions match against the
-- payload Map the service publishes (workflowId, workflowModule, status,
-- regulatoryAnchor, titleEn). Engine evaluates equality + contains.
--
-- Multi-tenant: every INSERT carries organisation_id = 1 (the canonical
-- demo tenant). source='CUSTOM' so the rules show as user-defined in the
-- admin view; PROMPT_14 is reserved for the original §14 seed set.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-21 ── PDPL personal-data breach → notify DPO immediately
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-21')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-21',
         N'PDPL breach submission → notify DPO',
         N'إشعار مسؤول حماية البيانات عند رفع بلاغ خرق PDPL',
         N'When a PDPL breach wizard is submitted, notify the DPO so '
         + N'they can start the 72-hour SDAIA notification clock.',
         N'إشعار فوري للـ DPO عند رفع بلاغ خرق PDPL لبدء عداد ٧٢ ساعة.',
         N'WORKFLOW_SUBMISSION_CREATED',
         N'{"workflowId":"pdpl-breach"}',
         N'{"type":"NOTIFY","role":"DPO","template":"PDPL_BREACH_FILED"}',
         5, N'CUSTOM');
END

-- AR-22 ── NCA cyber-incident submission → notify CISO + escalate to crisis room
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-22')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-22',
         N'NCA cyber-incident submission → notify CISO',
         N'إشعار CISO عند رفع حادث سيبراني للهيئة الوطنية',
         N'When the NCA cyber-incident wizard is submitted, notify the '
         + N'CISO and link the submission into the crisis room.',
         N'إشعار CISO وربط البلاغ بغرفة الأزمات.',
         N'WORKFLOW_SUBMISSION_CREATED',
         N'{"workflowId":"nca-cyber-incident"}',
         N'{"type":"NOTIFY","role":"CISO","template":"NCA_CYBER_INCIDENT_FILED"}',
         10, N'CUSTOM');
END

-- AR-23 ── Risk acceptance above appetite → escalate to Risk Committee
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-23')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-23',
         N'Risk acceptance submission → escalate to Risk Committee',
         N'تصعيد قبول الخطر إلى لجنة المخاطر',
         N'When a risk-acceptance wizard is submitted, escalate to the '
         + N'Risk Committee after 24h if not yet approved by CRO + Board.',
         N'تصعيد للجنة المخاطر إذا لم يكتمل اعتماد CRO والمجلس خلال ٢٤ ساعة.',
         N'WORKFLOW_SUBMISSION_CREATED',
         N'{"workflowId":"erm-risk-acceptance"}',
         N'{"type":"ESCALATE","to_role":"RISK_COMMITTEE","after_minutes":1440}',
         20, N'CUSTOM');
END

-- AR-24 ── Cross-border transfer submission → flag for DPO review
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-24')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-24',
         N'PDPL cross-border transfer → flag for DPO review',
         N'وضع علامة مراجعة DPO لنقل البيانات عبر الحدود',
         N'When a cross-border transfer is submitted, flag the record '
         + N'so it shows up in the DPO review queue alongside Saudi Data '
         + N'Office adequacy decisions.',
         N'وضع علامة لمراجعة DPO ومطابقتها مع قرارات مكتب البيانات.',
         N'WORKFLOW_SUBMISSION_CREATED',
         N'{"workflowId":"pdpl-cross-border"}',
         N'{"type":"FLAG","entity_field":"needs_dpo_review","to":true}',
         15, N'CUSTOM');
END

-- AR-25 ── Board pack APPROVED → audit-log the regulatory milestone
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-25')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-25',
         N'Board pack approved → audit the milestone',
         N'تسجيل إقرار ملف المجلس في سجل التدقيق',
         N'When a quarterly board pack is APPROVED, write a regulatory '
         + N'milestone audit entry that the board pack PDF generator '
         + N'reads for the next quarter cover letter.',
         N'تسجيل معلم تنظيمي في سجل التدقيق عند اعتماد ملف المجلس.',
         N'WORKFLOW_SUBMISSION_REVIEWED',
         N'{"workflowId":"governance-board-pack","status":"APPROVED"}',
         N'{"type":"AUDIT","severity":"INFO","message":"Quarterly board pack approved"}',
         30, N'CUSTOM');
END
GO

PRINT 'V127 workflow_submission automation rules (AR-21..AR-25) seeded.';
GO
