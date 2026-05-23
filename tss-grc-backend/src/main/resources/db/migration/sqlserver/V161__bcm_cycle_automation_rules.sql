-- V161 — F8 automation rules consuming the new BCM annual-cycle triggers.
--
-- AR-160..AR-170 (11 rules) — wires the Slice 7-C BCM annual-cycle scheduler
-- triggers into the F8 engine so the cycle close-out reminder ladder fans
-- out automatically per Saudi BCM Manager playbook. No rule-engine code
-- changes — these reuse the existing NOTIFY / ESCALATE / CREATE_RECORD
-- action types.
--
-- Trigger events fired by BcmAnnualCycleScheduledSweep:
--   BCM_CYCLE_CLOSEOUT_DUE_90D     ISO 22301 §9 close-out 90-day lead
--   BCM_CYCLE_CLOSEOUT_DUE_60D     60-day lead — BCP/DRP refresh window
--   BCM_CYCLE_CLOSEOUT_DUE_30D     30-day lead — Board pack drafting kickoff
--   BCM_CYCLE_CLOSEOUT_DUE_7D      7-day lead — last call + executive escalation
--   BCM_CYCLE_OVERDUE              endDate past, status still open (daily re-arm)
--
-- Targets the demo tenant (org=1). Customers replicate via the V96 seed
-- pattern (organisation_licenses entitlement → rule clone).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-160 ── 90-day close-out → kick off cycle readiness review
-- The BCM Manager + executive sponsor are notified to start the annual
-- close-out workstream. 90 days gives space for BIA refresh, BCP/DRP
-- re-test, evidence collection, and gap-remediation before the Board
-- pack draft window opens.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-160')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-160',
         N'BCM cycle close-out 90-day lead → kickoff readiness review',
         N'إغلاق دورة BCM قبل 90 يوماً → بدء مراجعة الجاهزية',
         N'ISO 22301 §9 requires an annual management review. 90 days '
         + N'before the cycle end-date, the BCM Manager and executive '
         + N'sponsor are notified to kick off the close-out workstream '
         + N'(BIA refresh, BCP/DRP re-test, evidence assembly).',
         N'يتطلب ISO 22301 §9 مراجعة إدارية سنوية. قبل 90 يوماً من تاريخ '
         + N'انتهاء الدورة، يتم إخطار مدير BCM والراعي التنفيذي لبدء العمل '
         + N'على الإغلاق (تحديث BIA، اختبار BCP/DRP، جمع الأدلة).',
         N'BCM_CYCLE_CLOSEOUT_DUE_90D',
         N'{}',
         N'{"type":"NOTIFY","roles":["BCM_MANAGER","EXECUTIVE_SPONSOR"],"channels":["EMAIL","INBOX"],"template":"BCM_CYCLE_CLOSEOUT_KICKOFF","slaHours":168}',
         2, N'CUSTOM');
END

-- AR-161 ── 60-day close-out → BIA refresh + BCP/DRP re-test reminder
-- At 60 days, all critical-process BIAs should be in active review.
-- Process owners get reminded; the BCM team gets a dashboard tile.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-161')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-161',
         N'BCM cycle close-out 60-day lead → BIA refresh window',
         N'إغلاق دورة BCM قبل 60 يوماً → نافذة تحديث BIA',
         N'60 days before close-out, every critical-process BIA must be '
         + N'in active review. Process owners are notified to refresh '
         + N'their BIAs and BCP/DRP teams are reminded of the re-test SLA.',
         N'قبل 60 يوماً من الإغلاق، يجب أن يكون كل BIA لعملية حرجة قيد '
         + N'المراجعة الفعلية. يتم إخطار مالكي العمليات لتحديث BIAs '
         + N'وتذكير فرق BCP/DRP بـSLA إعادة الاختبار.',
         N'BCM_CYCLE_CLOSEOUT_DUE_60D',
         N'{}',
         N'{"type":"NOTIFY","roles":["BCM_MANAGER","PROCESS_OWNER"],"channels":["EMAIL","INBOX"],"template":"BCM_BIA_REFRESH_WINDOW","slaHours":168}',
         2, N'CUSTOM');
END

-- AR-162 ── 30-day close-out → Board pack drafting kickoff
-- 30 days out, the Board pack BCM section should be in first draft.
-- Slice 7-D's Board Pack auto-generator is the source — this rule
-- nudges the BCM Manager + Company Secretary to run it.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-162')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-162',
         N'BCM cycle close-out 30-day lead → Board pack drafting',
         N'إغلاق دورة BCM قبل 30 يوماً → إعداد حزمة المجلس',
         N'30 days before close-out, the Board pack BCM section must be '
         + N'in first draft. The BCM Manager and Company Secretary are '
         + N'paged to run the Board Pack auto-generator (GET '
         + N'/api/v1/bcm/board-pack-section) and start the executive review.',
         N'قبل 30 يوماً من الإغلاق، يجب أن يكون قسم BCM في حزمة المجلس '
         + N'في مسودة أولى. يتم تنبيه مدير BCM وأمين سر الشركة لتشغيل '
         + N'مولّد حزمة المجلس وبدء المراجعة التنفيذية.',
         N'BCM_CYCLE_CLOSEOUT_DUE_30D',
         N'{}',
         N'{"type":"NOTIFY","roles":["BCM_MANAGER","COMPANY_SECRETARY","EXECUTIVE_SPONSOR"],"channels":["EMAIL","INBOX"],"template":"BCM_BOARD_PACK_DRAFTING","slaHours":72}',
         1, N'CUSTOM');
END

-- AR-163 ── 30-day close-out with critical gaps > 0 → escalate to ExecutiveSponsor
-- If critical gaps are still open at T-30 days, the executive sponsor
-- gets pulled into the conversation directly — the cycle won't close
-- cleanly without remediation.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-163')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-163',
         N'BCM cycle T-30 with critical gaps open → escalate to sponsor',
         N'دورة BCM قبل 30 يوماً مع وجود فجوات حرجة → تصعيد للراعي',
         N'When T-30 fires and the cycle still has critical_gap_count > 0, '
         + N'escalate to the executive sponsor and CRO so a remediation '
         + N'sprint can be staffed before the Board pack lands. Cycles do '
         + N'not close cleanly with critical gaps open.',
         N'عندما يتم تشغيل T-30 ولا تزال الدورة تحتوي على '
         + N'critical_gap_count > 0، يتم التصعيد للراعي التنفيذي و CRO '
         + N'حتى يتم تشكيل سبرنت معالجة قبل وصول حزمة المجلس. لا تُغلق '
         + N'الدورات بشكل نظيف مع وجود فجوات حرجة مفتوحة.',
         N'BCM_CYCLE_CLOSEOUT_DUE_30D',
         N'{"criticalGapCountGt":0}',
         N'{"type":"ESCALATE","roles":["EXECUTIVE_SPONSOR","CHIEF_RISK_OFFICER"],"channels":["EMAIL","SMS","INBOX"],"template":"BCM_CRITICAL_GAPS_OPEN","slaHours":48}',
         1, N'CUSTOM');
END

-- AR-164 ── 7-day close-out → last call to BCM Manager + Audit Chair
-- 7 days out, this is the last reminder before the Board pack must
-- be locked. Audit Committee Chair gets copied so independent oversight
-- is visible.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-164')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-164',
         N'BCM cycle close-out 7-day lead → last call + audit chair',
         N'إغلاق دورة BCM قبل 7 أيام → إنذار أخير ورئيس لجنة التدقيق',
         N'7 days out is the last call before the Board pack locks. '
         + N'The BCM Manager, executive sponsor, and Audit Committee '
         + N'Chair are all notified. The Audit Chair copy makes the '
         + N'independent oversight trail visible.',
         N'7 أيام قبل الإغلاق هي الإنذار الأخير قبل تثبيت حزمة المجلس. '
         + N'يتم إخطار مدير BCM والراعي التنفيذي ورئيس لجنة التدقيق. '
         + N'تجعل نسخة رئيس لجنة التدقيق مسار الرقابة المستقلة مرئياً.',
         N'BCM_CYCLE_CLOSEOUT_DUE_7D',
         N'{}',
         N'{"type":"ESCALATE","roles":["BCM_MANAGER","EXECUTIVE_SPONSOR","AUDIT_COMMITTEE_CHAIR"],"channels":["EMAIL","SMS","INBOX"],"template":"BCM_CYCLE_LAST_CALL","slaHours":24}',
         1, N'CUSTOM');
END

-- AR-165 ── Cycle OVERDUE → daily escalation to CRO + Board liaison
-- Once a cycle is overdue, this rule fires daily until APPROVED.
-- CRO gets copied because audit committees treat a chronically open
-- cycle as a control failure.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-165')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-165',
         N'BCM cycle overdue → daily escalation to CRO',
         N'دورة BCM متأخرة → تصعيد يومي إلى CRO',
         N'When a BCM cycle is past its end-date and still open, this '
         + N'rule fires once per day to escalate to the CRO and Board '
         + N'liaison. SAMA / NCA examiners treat a chronically open '
         + N'cycle as evidence of a governance breakdown.',
         N'عندما تتجاوز دورة BCM تاريخ انتهائها وتظل مفتوحة، يتم '
         + N'تشغيل هذا القانون مرة يومياً للتصعيد إلى CRO والاتصال '
         + N'بالمجلس. يعتبر مفتشو ساما / NCA الدورة المفتوحة المزمنة '
         + N'دليلاً على انهيار الحوكمة.',
         N'BCM_CYCLE_OVERDUE',
         N'{}',
         N'{"type":"ESCALATE","roles":["CHIEF_RISK_OFFICER","EXECUTIVE_SPONSOR","BOARD_LIAISON"],"channels":["EMAIL","SMS","INBOX"],"template":"BCM_CYCLE_OVERDUE","slaHours":24}',
         1, N'CUSTOM');
END

-- AR-166 ── 90-day close-out with overall_score < 70 → readiness CAPA
-- If readiness is below 70% at the 90-day mark, a CAPA gets auto-opened
-- so remediation is tracked formally rather than relying on the close-out
-- workstream to surface it.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-166')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-166',
         N'BCM cycle T-90 with readiness < 70% → open readiness CAPA',
         N'دورة BCM قبل 90 يوماً مع جاهزية < 70٪ → فتح CAPA للجاهزية',
         N'When T-90 fires and the cycle overall_score is below 70%, '
         + N'a BCM CAPA is auto-created so remediation is tracked '
         + N'formally with an owner, due date, and evidence trail.',
         N'عندما يتم تشغيل T-90 وكان overall_score للدورة أقل من 70٪، '
         + N'يتم إنشاء CAPA لـBCM تلقائياً حتى يتم تتبع المعالجة بشكل '
         + N'رسمي مع مالك وتاريخ استحقاق ومسار الأدلة.',
         N'BCM_CYCLE_CLOSEOUT_DUE_90D',
         N'{"overallScoreLt":70}',
         N'{"type":"CREATE_RECORD","entityType":"BcmCorrectiveAction","fields":{"titleEn":"Readiness CAPA — cycle below threshold","titleAr":"إجراء معالجة الجاهزية — الدورة أقل من العتبة","priority":"HIGH","status":"OPEN","actionPlan":"Lift readiness score above 70% before cycle close-out: complete outstanding BIAs, refresh BCPs, re-test DRPs, gather evidence."}}',
         2, N'CUSTOM');
END

-- AR-167 ── 30-day close-out with bia_completion_percent < 90 → owner reminder
-- 90% BIA completion is the soft threshold for an honest close-out.
-- This rule pages process owners individually so the BCM Manager
-- doesn't have to chase them.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-167')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-167',
         N'BCM cycle T-30 with BIA completion < 90% → owner reminder',
         N'دورة BCM قبل 30 يوماً مع اكتمال BIA < 90٪ → تذكير المالكين',
         N'90% BIA completion is the soft threshold for an honest cycle '
         + N'close-out. This rule pages all process owners individually '
         + N'so the BCM Manager doesn''t have to chase them by hand.',
         N'90٪ اكتمال BIA هو الحد الناعم للإغلاق الصادق للدورة. يقوم '
         + N'هذا القانون بتنبيه جميع مالكي العمليات بشكل فردي حتى لا '
         + N'يحتاج مدير BCM إلى مطاردتهم يدوياً.',
         N'BCM_CYCLE_CLOSEOUT_DUE_30D',
         N'{"biaCompletionPercentLt":90}',
         N'{"type":"NOTIFY","roles":["PROCESS_OWNER","BCM_MANAGER"],"channels":["EMAIL","INBOX"],"template":"BCM_BIA_COMPLETION_LOW","slaHours":72}',
         2, N'CUSTOM');
END

-- AR-168 ── 90-day close-out → auto-create Board management review meeting
-- The annual ISO 22301 §9.3 management review is a mandatory item.
-- This rule opens the calendar slot so it isn't missed.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-168')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-168',
         N'BCM cycle T-90 → schedule management review meeting',
         N'دورة BCM قبل 90 يوماً → جدولة اجتماع المراجعة الإدارية',
         N'ISO 22301 §9.3 mandates a management review per cycle. This '
         + N'rule notifies the BCM Manager + Company Secretary to lock '
         + N'a date and assemble inputs (audit results, exercise outcomes, '
         + N'AAR lessons, CAPA closure rate).',
         N'يفرض ISO 22301 §9.3 مراجعة إدارية لكل دورة. يقوم هذا القانون '
         + N'بإخطار مدير BCM وأمين سر الشركة لتحديد تاريخ وتجميع '
         + N'المدخلات (نتائج التدقيق، نتائج التمارين، دروس AAR، معدل '
         + N'إغلاق CAPA).',
         N'BCM_CYCLE_CLOSEOUT_DUE_90D',
         N'{}',
         N'{"type":"NOTIFY","roles":["BCM_MANAGER","COMPANY_SECRETARY"],"channels":["EMAIL","INBOX"],"template":"BCM_MGMT_REVIEW_SCHEDULE","slaHours":168}',
         3, N'CUSTOM');
END

-- AR-169 ── 7-day close-out → audit evidence package assembly
-- The audit evidence package (Slice from Audit-Fix 7) must be locked
-- before the cycle goes APPROVED. T-7 is the assembly deadline.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-169')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-169',
         N'BCM cycle T-7 → assemble audit evidence package',
         N'دورة BCM قبل 7 أيام → تجميع حزمة أدلة التدقيق',
         N'The audit evidence package consolidates every artefact a '
         + N'SAMA / NCA / internal-audit reviewer expects to see for the '
         + N'cycle. T-7 days is the assembly deadline — the package must '
         + N'be locked before the cycle moves to APPROVED.',
         N'تقوم حزمة أدلة التدقيق بتجميع كل قطعة عمل يتوقع مراجعو ساما '
         + N'/ NCA / التدقيق الداخلي رؤيتها للدورة. 7 أيام قبل الإغلاق '
         + N'هو الموعد النهائي للتجميع — يجب تثبيت الحزمة قبل انتقال '
         + N'الدورة إلى APPROVED.',
         N'BCM_CYCLE_CLOSEOUT_DUE_7D',
         N'{}',
         N'{"type":"NOTIFY","roles":["BCM_MANAGER","INTERNAL_AUDITOR"],"channels":["EMAIL","INBOX"],"template":"BCM_EVIDENCE_PACK_ASSEMBLY","slaHours":48}',
         2, N'CUSTOM');
END

-- AR-170 ── Cycle OVERDUE > 30 days → flag as control failure
-- If a cycle stays overdue for more than 30 days, it crosses from
-- "behind schedule" into "control failure" territory. This rule
-- creates a FLAG record so the next audit committee meeting agenda
-- includes it as a standing item.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-170')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-170',
         N'BCM cycle overdue > 30d → flag as control failure',
         N'دورة BCM متأخرة > 30 يوماً → علامة فشل في الرقابة',
         N'When a cycle stays overdue for more than 30 days, it crosses '
         + N'from "behind schedule" into "control failure" territory. '
         + N'This rule creates a FLAG record so the next audit committee '
         + N'meeting agenda includes it as a standing item.',
         N'عندما تظل الدورة متأخرة لأكثر من 30 يوماً، تنتقل من "خلف '
         + N'الجدول الزمني" إلى منطقة "فشل الرقابة". يقوم هذا القانون '
         + N'بإنشاء سجل FLAG حتى يتضمن جدول أعمال اجتماع لجنة التدقيق '
         + N'التالي هذا كبند دائم.',
         N'BCM_CYCLE_OVERDUE',
         N'{"daysOverdueGt":30}',
         N'{"type":"FLAG","entityType":"BcmAssessmentCycle","reason":"Cycle overdue > 30 days — control failure","escalateTo":"AUDIT_COMMITTEE","channels":["EMAIL","INBOX"]}',
         1, N'CUSTOM');
END

GO
