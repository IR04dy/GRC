-- V128: F8 automation rules for CapaTaskService events.
--
-- PR #46 wired CapaTaskService into the F8 engine. It now publishes two
-- DomainEvents on every mutation:
--
--   CAPA_TASK_CREATED   — CAPA opened (from audit.finding / processor_assessment
--                          / supplier_resilience_test / rca / fourth_party_exposure)
--   CAPA_TASK_REVIEWED  — IN_PROGRESS, AWAITING_VERIFICATION, CLOSED, CANCELLED
--
-- These five seed rules turn those events into the day-to-day routing
-- buyers expect: the right reviewer wakes up, the right ticket lands,
-- the right regulator gets a filing reminder.
--
-- Conditions match against the payload Map the service publishes:
-- (externalId, originType, originId, status, severity, capaType, owner)
-- plus previousStatus on REVIEWED. Engine evaluates equality + contains.
--
-- Origin types come from the actual seed/automation surfaces in the
-- codebase, not invented examples:
--   "audit.finding"            — V23 schema comment, FindingService
--   "processor_assessment"     — TprmAutomationService PDPL Art. 28 path
--   "supplier_resilience_test" — TprmAutomationService SRP fail path
--   "fourth_party_exposure"    — TprmAutomationService concentration risk
--   "rca"                      — RootCauseAnalysisService
--
-- Multi-tenant: every INSERT carries organisation_id = 1 (the canonical
-- demo tenant). source='CUSTOM' so the rules show as user-defined in the
-- admin view; the bracket AR-26..AR-30 picks up where V127 left off.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-26 ── HIGH-severity audit-finding CAPA opened → notify the audit lead
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-26')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-26',
         N'High-severity audit-finding CAPA → notify auditor',
         N'إشعار المدقق عند فتح CAPA عالي الخطورة من تدقيق',
         N'When a CAPA is opened from an audit finding with HIGH severity, '
         + N'notify the audit lead so the remediation window has a senior owner '
         + N'from day one. Matches the IIA Standard 2500 follow-up cadence.',
         N'إشعار قائد التدقيق عند فتح CAPA عالي الخطورة من نتيجة تدقيق — '
         + N'يتماشى مع معيار IIA 2500 لمتابعة الإجراءات التصحيحية.',
         N'CAPA_TASK_CREATED',
         N'{"originType":"audit.finding","severity":"HIGH"}',
         N'{"type":"NOTIFY","role":"AUDITOR","template":"CAPA_HIGH_SEVERITY_OPENED"}',
         8, N'CUSTOM');
END

-- AR-27 ── Processor-assessment CAPA opened → notify DPO (PDPL Art. 28)
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-27')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-27',
         N'Processor-assessment CAPA → notify DPO',
         N'إشعار DPO عند CAPA من فحص معالج بيانات',
         N'When TprmAutomationService creates a CAPA from a failed processor '
         + N'assessment, notify the DPO. PDPL Art. 28 holds the controller '
         + N'accountable for processor remediation — the DPO needs visibility '
         + N'from the moment the CAPA opens.',
         N'إشعار DPO عند فتح CAPA من فشل تقييم معالج — وفق المادة ٢٨ من '
         + N'نظام حماية البيانات الشخصية، المراقب مسؤول عن متابعة المعالج.',
         N'CAPA_TASK_CREATED',
         N'{"originType":"processor_assessment"}',
         N'{"type":"NOTIFY","role":"DPO","template":"PDPL_PROCESSOR_REMEDIATION_OPENED"}',
         12, N'CUSTOM');
END

-- AR-28 ── Supplier-resilience-test CAPA → escalate to procurement after 7d
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-28')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-28',
         N'Supplier-resilience CAPA → escalate to procurement after 7d',
         N'تصعيد CAPA من اختبار صمود المورد بعد ٧ أيام',
         N'When a supplier-resilience-test CAPA is opened, give the supplier '
         + N'manager a week to acknowledge; after 168h escalate to procurement '
         + N'leadership. Matches SAMA OPRES §6 third-party resilience cadence.',
         N'تصعيد CAPA لمسؤول المشتريات إذا لم يُؤكّد خلال ١٦٨ ساعة — '
         + N'يتماشى مع متطلبات صمود الأطراف الثالثة في SAMA OPRES §٦.',
         N'CAPA_TASK_CREATED',
         N'{"originType":"supplier_resilience_test"}',
         N'{"type":"ESCALATE","to_role":"PROCUREMENT_LEAD","after_minutes":10080}',
         18, N'CUSTOM');
END

-- AR-29 ── Audit-finding CAPA closed → audit milestone for NCA filing reminder
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-29')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-29',
         N'Audit-finding CAPA closed → record NCA filing milestone',
         N'تسجيل إغلاق CAPA من تدقيق كمعلَم للهيئة الوطنية',
         N'When an audit-finding CAPA closes, write a regulatory milestone '
         + N'into platform.audit_log. The quarterly NCA-ECC filing template '
         + N'reads these milestones to build the "closed findings this '
         + N'quarter" section without needing a separate report query.',
         N'كتابة معلَم تنظيمي في سجل التدقيق عند إغلاق CAPA — تستخدمه قوالب '
         + N'تقارير NCA-ECC ربع السنوية لقائمة "نتائج مغلقة هذا الربع".',
         N'CAPA_TASK_REVIEWED',
         N'{"originType":"audit.finding","status":"CLOSED"}',
         N'{"type":"AUDIT","severity":"INFO","message":"Audit-finding CAPA closed — NCA quarterly filing milestone"}',
         25, N'CUSTOM');
END

-- AR-30 ── CRITICAL CAPA cancelled (not closed) → escalate to board
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-30')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-30',
         N'Critical CAPA cancelled → escalate to board',
         N'تصعيد CAPA حرج تم إلغاؤه إلى المجلس',
         N'A CRITICAL CAPA being cancelled (not closed) is an exception '
         + N'pattern: the org chose to accept the residual risk rather than '
         + N'remediate. Escalate to board so the acceptance is on the formal '
         + N'risk-tolerance record, not buried in a CAPA register.',
         N'إلغاء CAPA حرج (بدلاً من إغلاقه) قرارٌ بقبول الخطر المتبقي — '
         + N'يُصعَّد للمجلس ليُسجَّل ضمن سجلات تحمل المخاطر الرسمية.',
         N'CAPA_TASK_REVIEWED',
         N'{"severity":"CRITICAL","status":"CANCELLED"}',
         N'{"type":"ESCALATE","to_role":"BOARD","template":"CRITICAL_CAPA_CANCELLED"}',
         3, N'CUSTOM');
END
GO

PRINT 'V128 capa_task automation rules (AR-26..AR-30) seeded.';
GO
