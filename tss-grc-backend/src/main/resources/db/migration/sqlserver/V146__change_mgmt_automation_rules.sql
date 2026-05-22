-- V146: F8 automation rules for ChangeManagementService events.
--
-- Change Management is ITIL v4 Change Enablement — the workflow
-- that decides which changes to production go through CAB approval
-- vs. emergency bypass vs. pre-approved standard paths. High
-- visibility for the audit committee because failed changes are a
-- common root-cause for incidents (40-60% by published industry
-- numbers).
--
-- ChangeManagementService publishes 9 canonical events at the
-- lifecycle milestones that matter to downstream consumers:
--
--   CHANGE_CREATED            — DRAFT row added
--   CHANGE_SUBMITTED          — → SUBMITTED
--   CHANGE_IMPACT_ASSESSED    — → IMPACT_ASSESSED
--   CHANGE_CAB_APPROVED       — CAB voted YES
--   CHANGE_CAB_REJECTED       — CAB voted NO (rework path)
--   CHANGE_EMERGENCY_APPROVED — Emergency bypass (CAB skipped)
--   CHANGE_IMPLEMENTED        — landed in production
--   CHANGE_FAILED             — ROLLED_BACK
--   CHANGE_CLOSED             — terminal close
--   CHANGE_TRANSITIONED       — catch-all (SCHEDULED, IMPLEMENTING,
--                               POST_REVIEW, CANCELLED)
--
-- AR-96..AR-100 — service #16 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-96 ── HIGH-risk change in CAB → notify all CAB members
-- A HIGH-risk change is by definition material to availability or
-- security posture. Every CAB member needs visibility BEFORE the
-- meeting so they can prepare questions / pull dependencies. P2
-- because CAB cadence (typically weekly) gives some time, but
-- pre-meeting visibility separates a productive vote from a
-- rubber-stamp.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-96')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-96',
         N'HIGH-risk change submitted → notify CAB members pre-meeting',
         N'تقديم تغيير عالي المخاطر → إشعار أعضاء لجنة استشارة التغيير قبل الاجتماع',
         N'HIGH-risk changes are by definition material to '
         + N'availability or security posture. Every CAB member sees '
         + N'them BEFORE the meeting so they prepare questions and '
         + N'pull dependencies. Pre-meeting visibility separates a '
         + N'productive vote from a rubber-stamp.',
         N'يستحق التغيير عالي المخاطر مراجعة جدية قبل اجتماع لجنة استشارة التغيير — '
         + N'يصل الإشعار للأعضاء قبل الاجتماع ليجهزوا الأسئلة والمتطلبات.',
         N'CHANGE_SUBMITTED',
         N'{"riskLevel":"HIGH"}',
         N'{"type":"NOTIFY","roles":["CAB_MEMBER","CHANGE_MANAGER"],"template":"CHANGE_HIGH_RISK_SUBMITTED"}',
         2, N'CUSTOM');
END

-- AR-97 ── Emergency change approved → escalate CTO + CISO + Change Mgr
-- Emergency-approved changes bypass CAB by design — they're either
-- security hot-fixes or availability-restoring patches that can't
-- wait. CTO + CISO + Change Manager get an immediate escalation so
-- they're aware the bypass was used and can spot-check the post-
-- implementation review. P1 because emergency bypass is the highest-
-- risk path in the lifecycle.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-97')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-97',
         N'Emergency change approved → escalate CTO + CISO + Change Manager',
         N'اعتماد تغيير طارئ → تصعيد لرئيس التقنية ورئيس الأمن السيبراني ومدير التغيير',
         N'Emergency-approved changes bypass CAB by design — '
         + N'security hot-fixes or availability-restoring patches. '
         + N'CTO + CISO + Change Manager see the bypass immediately '
         + N'and spot-check the post-implementation review. P1: '
         + N'emergency bypass is the highest-risk path.',
         N'تجاوز التغيير الطارئ للجنة استشارة التغيير إجراء عالي المخاطر — '
         + N'يصل التصعيد فوراً لرئيس التقنية ورئيس الأمن السيبراني ومدير التغيير لمراجعة الإجراء.',
         N'CHANGE_EMERGENCY_APPROVED',
         N'{}',
         N'{"type":"ESCALATE","roles":["CTO","CISO","CHANGE_MANAGER"],"template":"CHANGE_EMERGENCY_APPROVED"}',
         1, N'CUSTOM');
END

-- AR-98 ── CAB rejected → notify requester + change manager for rework
-- A rejection means the change goes back to DRAFT for rework.
-- Requester needs to know why (so they can revise impact, risk,
-- rollback plan); change manager tracks reject rates as a KPI
-- (high reject rates can signal a too-restrictive CAB or too-
-- ambitious change requests). P3 — this is process improvement,
-- not crisis.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-98')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-98',
         N'Change CAB-rejected → notify requester + change manager',
         N'رفض لجنة استشارة التغيير → إشعار مقدم الطلب ومدير التغيير',
         N'CAB rejection sends the change back to DRAFT for rework. '
         + N'Requester knows why; change manager tracks reject rates '
         + N'as a KPI (high rates signal too-restrictive CAB or '
         + N'too-ambitious requests). Process improvement, not crisis.',
         N'يعيد الرفض التغيير إلى المسودة للمعالجة — '
         + N'يصل الإشعار لمقدم الطلب لمعرفة السبب، ولمدير التغيير لمتابعة معدلات الرفض كمؤشر للعملية.',
         N'CHANGE_CAB_REJECTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["CHANGE_REQUESTER","CHANGE_MANAGER"],"template":"CHANGE_CAB_REJECTED"}',
         3, N'CUSTOM');
END

-- AR-99 ── Change FAILED (rolled back) → incident management triage
-- A rolled-back change is the textbook precursor to a major
-- incident. Incident management + change manager + CTO get a FLAG
-- so the post-implementation review captures root cause (e.g.
-- inadequate test plan, missing rollback step) and the issue
-- doesn't recur on the next change in the same area.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-99')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-99',
         N'Change FAILED → flag incident mgmt + change manager + CTO',
         N'فشل التغيير → تنبيه إدارة الحوادث ومدير التغيير ورئيس التقنية',
         N'A rolled-back change is the textbook precursor to a '
         + N'major incident. Incident management + change manager + '
         + N'CTO get a FLAG so the post-implementation review '
         + N'captures root cause (inadequate test plan, missing '
         + N'rollback step) and the issue doesn''t recur.',
         N'فشل التغيير من المؤشرات الكلاسيكية للحوادث الكبرى — '
         + N'تُبلَّغ إدارة الحوادث ومدير التغيير ورئيس التقنية لتسجيل السبب الجذري وتجنب التكرار.',
         N'CHANGE_FAILED',
         N'{}',
         N'{"type":"FLAG","roles":["INCIDENT_MANAGER","CHANGE_MANAGER","CTO"],"template":"CHANGE_FAILED"}',
         1, N'CUSTOM');
END

-- AR-100 ── HIGH-risk change implemented → BCM coordinator alerts
-- HIGH-risk changes that landed in production are the rows BCM
-- watches — they're the candidates for the next DR test, the next
-- BIA refresh, the next continuity-strategy re-evaluation. BCM
-- coordinator gets a soft notification so the BCM cycle stays in
-- sync with what's actually running in production.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-100')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-100',
         N'HIGH-risk change implemented → BCM coordinator stays in sync',
         N'تنفيذ تغيير عالي المخاطر → تزامن منسق استمرارية الأعمال',
         N'HIGH-risk changes landed in production are BCM''s '
         + N'watch-list — candidates for the next DR test, BIA '
         + N'refresh, continuity-strategy re-evaluation. BCM '
         + N'coordinator stays in sync with what''s actually '
         + N'running in production so the cycle doesn''t lag '
         + N'reality.',
         N'كل تغيير عالي المخاطر يدخل الإنتاج مرشَّح لاختبار التعافي القادم أو تحديث تحليل الأثر — '
         + N'يصل منسق استمرارية الأعمال ليبقى دوراته متزامنة مع الواقع التشغيلي.',
         N'CHANGE_IMPLEMENTED',
         N'{"riskLevel":"HIGH"}',
         N'{"type":"NOTIFY","roles":["BCM_COORDINATOR"],"template":"CHANGE_HIGH_RISK_IMPLEMENTED"}',
         4, N'CUSTOM');
END
GO

PRINT 'V146 seeded 5 change-management automation rules (AR-96..AR-100).';
GO
