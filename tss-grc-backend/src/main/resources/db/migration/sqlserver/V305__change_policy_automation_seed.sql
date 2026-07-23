-- V305 (CH-13): seed the Change + Policy F8 automation rules — per tenant.
--
-- Background: V141 (policy AR-71..75) and V146 (change AR-96..100) were
-- authored long ago but never actually reached any environment:
--   * dev is Python-bootstrapped (Flyway off) and its 175-rule seed skips
--     the AR-71..75 / AR-96..100 blocks, so CHANGE_* had 0 rule consumers
--     and POLICY_* had only the original AR-15 (POLICY_PUBLISHED);
--   * prod's Flyway baseline is 292, so V141/V146 sit below it and never run.
-- Net effect: every CHANGE_* trigger ChangeManagementService emits, and the
-- CHANGE_TASK_OVERDUE / POLICY_REVIEW_DUE scheduler events, fired into a void.
--
-- This migration re-seeds those rules idempotently and, unlike the originals
-- (which hard-coded @org = 1), seeds them for EVERY organisation — the dev/prod
-- deployments carry 3 Saudi tenants, so orgs 2/3 previously had no Change or
-- Policy automation at all. Also adds two rules that consume the scheduler
-- events (CH-5 CHANGE_TASK_OVERDUE, policy REVIEW_DUE) which had no consumers.
--
-- Idempotent on (organisation_id, rule_code); safe to run repeatedly and safe
-- to hand-apply to dev via pyodbc (Flyway off in dev).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

;WITH r (rule_code, name_en, name_ar, description_en, description_ar,
         trigger_event, condition_json, action_json, priority) AS (
    SELECT * FROM (VALUES
        -- ── Policy (AR-71..AR-75, ported from V141) ──────────────────────
        (N'AR-71',
         N'Policy PUBLISHED → notify owner + compliance officer',
         N'نشر سياسة → إشعار مالك السياسة ومسؤول الالتزام',
         N'Publication makes a policy binding: owner drives rollout, compliance officer fans out attestation tasks.',
         N'يصبح إنفاذ السياسة لازماً عند النشر — يصل الإشعار للمالك ومسؤول الالتزام.',
         N'POLICY_PUBLISHED', N'{}',
         N'{"type":"NOTIFY","roles":["POLICY_OWNER","COMPLIANCE_OFFICER"],"template":"POLICY_PUBLISHED"}', 2),
        (N'AR-72',
         N'Policy EXPIRED → escalate owner + compliance officer for renewal',
         N'انتهاء صلاحية السياسة → تصعيد للمالك ومسؤول الالتزام للتجديد',
         N'An expired policy is a live control gap; owner must re-publish or archive, compliance officer logs the gap.',
         N'انتهاء صلاحية السياسة فجوة في الضوابط — يصعَّد للمالك ومسؤول الالتزام.',
         N'POLICY_EXPIRED', N'{}',
         N'{"type":"ESCALATE","roles":["POLICY_OWNER","COMPLIANCE_OFFICER"],"template":"POLICY_EXPIRED"}', 2),
        (N'AR-73',
         N'HIGH-risk policy exception APPROVED → escalate CRO + GC',
         N'اعتماد استثناء سياسة عالي المخاطر → تصعيد لرئيس إدارة المخاطر والمستشار العام',
         N'A HIGH-risk exception is a deliberate residual-risk decision; NCA ECC demands executive sign-off (CRO + GC).',
         N'اعتماد استثناء عالي المخاطر قرار تنفيذي — يصل رئيس إدارة المخاطر والمستشار العام.',
         N'POLICY_EXCEPTION_APPROVED', N'{"riskLevel":"HIGH"}',
         N'{"type":"ESCALATE","roles":["CHIEF_RISK_OFFICER","GENERAL_COUNSEL"],"template":"POLICY_EXCEPTION_APPROVED_HIGH"}', 1),
        (N'AR-74',
         N'Policy attestation completed → audit committee KPI feed',
         N'إتمام إقرار سياسة → تغذية مؤشر لجنة التدقيق',
         N'Each attestation feeds the audit committee''s attestation-coverage KPI; milestone notification keeps it current.',
         N'يساهم كل إقرار في مؤشر تغطية الإقرارات لدى لجنة التدقيق.',
         N'POLICY_ATTESTED', N'{}',
         N'{"type":"NOTIFY","roles":["AUDIT_COMMITTEE"],"template":"POLICY_ATTESTED_KPI"}', 5),
        (N'AR-75',
         N'Policy attestation DECLINED → escalate manager + DPO',
         N'رفض إقرار السياسة → تصعيد للمدير المباشر ومسؤول حماية البيانات',
         N'A declined attestation signals a training gap or real disagreement; manager triages, DPO joins if PDPL-relevant.',
         N'رفض الإقرار مؤشر مبكر — يصعَّد للمدير المباشر ومسؤول حماية البيانات.',
         N'POLICY_ATTESTATION_DECLINED', N'{}',
         N'{"type":"ESCALATE","roles":["USER_MANAGER","DPO"],"template":"POLICY_ATTESTATION_DECLINED"}', 3),

        -- ── Change (AR-96..AR-100, ported from V146) ─────────────────────
        (N'AR-96',
         N'HIGH-risk change submitted → notify CAB members pre-meeting',
         N'تقديم تغيير عالي المخاطر → إشعار أعضاء لجنة استشارة التغيير قبل الاجتماع',
         N'HIGH-risk changes are material to availability/security; CAB members see them before the meeting.',
         N'يستحق التغيير عالي المخاطر مراجعة قبل اجتماع لجنة استشارة التغيير.',
         N'CHANGE_SUBMITTED', N'{"riskLevel":"HIGH"}',
         N'{"type":"NOTIFY","roles":["CAB_MEMBER","CHANGE_MANAGER"],"template":"CHANGE_HIGH_RISK_SUBMITTED"}', 2),
        (N'AR-97',
         N'Emergency change approved → escalate CTO + CISO + Change Manager',
         N'اعتماد تغيير طارئ → تصعيد لرئيس التقنية ورئيس الأمن السيبراني ومدير التغيير',
         N'Emergency changes bypass CAB by design; CTO + CISO + Change Manager see the bypass immediately.',
         N'تجاوز التغيير الطارئ للجنة إجراء عالي المخاطر — تصعيد فوري.',
         N'CHANGE_EMERGENCY_APPROVED', N'{}',
         N'{"type":"ESCALATE","roles":["CTO","CISO","CHANGE_MANAGER"],"template":"CHANGE_EMERGENCY_APPROVED"}', 1),
        (N'AR-98',
         N'Change CAB-rejected → notify requester + change manager',
         N'رفض لجنة استشارة التغيير → إشعار مقدم الطلب ومدير التغيير',
         N'Rejection sends the change back to DRAFT for rework; requester learns why, change manager tracks reject rates.',
         N'يعيد الرفض التغيير إلى المسودة — إشعار المقدم ومدير التغيير.',
         N'CHANGE_CAB_REJECTED', N'{}',
         N'{"type":"NOTIFY","roles":["CHANGE_REQUESTER","CHANGE_MANAGER"],"template":"CHANGE_CAB_REJECTED"}', 3),
        (N'AR-99',
         N'Change FAILED → flag incident mgmt + change manager + CTO',
         N'فشل التغيير → تنبيه إدارة الحوادث ومدير التغيير ورئيس التقنية',
         N'A rolled-back change is a textbook incident precursor; incident mgmt + change manager + CTO are flagged.',
         N'فشل التغيير من مؤشرات الحوادث الكبرى — تُبلَّغ إدارة الحوادث.',
         N'CHANGE_FAILED', N'{}',
         N'{"type":"FLAG","roles":["INCIDENT_MANAGER","CHANGE_MANAGER","CTO"],"template":"CHANGE_FAILED"}', 1),
        (N'AR-100',
         N'HIGH-risk change implemented → BCM coordinator stays in sync',
         N'تنفيذ تغيير عالي المخاطر → تزامن منسق استمرارية الأعمال',
         N'HIGH-risk changes in production are BCM''s watch-list for the next DR test / BIA refresh.',
         N'كل تغيير عالي المخاطر بالإنتاج مرشَّح لاختبار التعافي القادم.',
         N'CHANGE_IMPLEMENTED', N'{"riskLevel":"HIGH"}',
         N'{"type":"NOTIFY","roles":["BCM_COORDINATOR"],"template":"CHANGE_HIGH_RISK_IMPLEMENTED"}', 4),

        -- ── Scheduler events that previously had no consumers ────────────
        (N'CHG-TASK-OVERDUE',
         N'Change task overdue → notify change manager',
         N'مهمة تغيير متأخرة → إشعار مدير التغيير',
         N'The CH-5 SLA sweep emits CHANGE_TASK_OVERDUE for open tasks past due; the change manager is notified to act.',
         N'ترصد آلية مستوى الخدمة المهام المتأخرة — يُبلَّغ مدير التغيير لاتخاذ الإجراء.',
         N'CHANGE_TASK_OVERDUE', N'{}',
         N'{"type":"NOTIFY","roles":["CHANGE_MANAGER"],"template":"CHANGE_TASK_OVERDUE"}', 3),
        (N'POL-REVIEW-DUE',
         N'Policy review due → notify owner + compliance officer',
         N'مراجعة سياسة مستحقة → إشعار المالك ومسؤول الالتزام',
         N'The policy compliance sweep emits POLICY_REVIEW_DUE ahead of the review date; owner + compliance officer act.',
         N'ترصد آلية الالتزام مواعيد المراجعة — يُبلَّغ المالك ومسؤول الالتزام.',
         N'POLICY_REVIEW_DUE', N'{}',
         N'{"type":"NOTIFY","roles":["POLICY_OWNER","COMPLIANCE_OFFICER"],"template":"POLICY_REVIEW_DUE"}', 3)
    ) v (rule_code, name_en, name_ar, description_en, description_ar,
         trigger_event, condition_json, action_json, priority)
)
INSERT INTO platform.automation_rules
    (organisation_id, rule_code, name_en, name_ar, description_en, description_ar,
     trigger_event, condition_json, action_json, priority, source)
SELECT o.id, r.rule_code, r.name_en, r.name_ar, r.description_en, r.description_ar,
       r.trigger_event, r.condition_json, r.action_json, r.priority, N'CUSTOM'
FROM r
CROSS JOIN platform.organisations o
WHERE ISNULL(o.is_deleted, 0) = 0
  AND NOT EXISTS (SELECT 1 FROM platform.automation_rules ar
                  WHERE ar.organisation_id = o.id AND ar.rule_code = r.rule_code);
GO

PRINT 'V305: seeded Change + Policy F8 automation rules per tenant (AR-71..75, AR-96..100, CHG-TASK-OVERDUE, POL-REVIEW-DUE).';
GO
