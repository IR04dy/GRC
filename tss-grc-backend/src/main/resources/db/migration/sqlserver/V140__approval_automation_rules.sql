-- V140: F8 automation rules for ApprovalRequestService events.
--
-- Approvals fan out across every module — risk treatments, policy
-- exceptions, vendor onboarding, change requests, regulator
-- submissions. Wiring approval lifecycle into F8 lets domain rules
-- react automatically to PENDING/APPROVED/REJECTED/DELEGATED/EXPIRED
-- transitions without each consumer module re-inventing the wheel.
--
-- ApprovalRequestService publishes:
--
--   APPROVAL_CREATED     — new approval request opened
--   APPROVAL_DECIDED     — APPROVED or REJECTED
--                          (payload carries previousStatus, status,
--                           entityType, entityId so rules can route
--                           per entity domain)
--   APPROVAL_DELEGATED   — re-assigned to another approver
--   APPROVAL_EXPIRED     — SLA timed out without a decision
--   APPROVAL_TRANSITIONED — generic (DELEGATED → PENDING re-route)
--
-- AR-66..AR-70 — service #9 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-66 ── P1 approval requested → notify assignee
-- A P1 approval (CEO sign-off, board-level decisions, regulator
-- submissions) is high-stakes by definition. The assignee gets a
-- direct notification the moment the request hits their queue so
-- the SLA doesn't burn while it waits to be noticed.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-66')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-66',
         N'P1 approval requested → notify assignee immediately',
         N'طلب اعتماد بأولوية قصوى → إشعار المعتمد فوراً',
         N'A P1 approval (CEO sign-off, board-level decisions, '
         + N'regulator submissions) is high-stakes by definition. The '
         + N'assignee gets a direct notification the moment the '
         + N'request hits their queue so the SLA does not burn while '
         + N'the request waits to be noticed.',
         N'إشعار المعتمد فور وصول طلب اعتماد بأولوية قصوى لئلا تستهلك مدة الاستجابة في الانتظار.',
         N'APPROVAL_CREATED',
         N'{"priority":"P1"}',
         N'{"type":"NOTIFY","roles":["ASSIGNEE"],"template":"APPROVAL_P1_REQUESTED"}',
         1, N'CUSTOM');
END

-- AR-67 ── Approval REJECTED → notify requester + escalate to manager
-- A rejection is a learning loop for the requester (they need to
-- understand why) and a signal to their manager (they may need to
-- coach the team on the policy / criteria that triggered the
-- rejection). Routing rejection notifications via F8 keeps the
-- feedback loop tight without each module re-implementing it.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-67')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-67',
         N'Approval REJECTED → notify requester + their manager',
         N'رفض طلب الاعتماد → إشعار مقدم الطلب ومديره المباشر',
         N'A rejection is a learning loop: requester needs to know '
         + N'why so they can revise; manager needs visibility so they '
         + N'can coach the team on the policy or criteria that '
         + N'triggered the rejection.',
         N'يصل إشعار رفض الاعتماد لمقدم الطلب ومديره المباشر لمعرفة سبب الرفض وتوجيه الفريق وفق السياسة.',
         N'APPROVAL_DECIDED',
         N'{"status":"REJECTED"}',
         N'{"type":"NOTIFY","roles":["REQUESTER","REQUESTER_MANAGER"],"template":"APPROVAL_REJECTED"}',
         3, N'CUSTOM');
END

-- AR-68 ── Approval DELEGATED → notify new assignee
-- When an approval is re-assigned the new approver needs to know
-- they own it now — the delegation often happens mid-week (PTO,
-- workload balancing) and without a notification the request can
-- sit on someone's queue who isn't watching it.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-68')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-68',
         N'Approval delegated → notify new assignee',
         N'تفويض طلب الاعتماد → إشعار المعتمد الجديد',
         N'When an approval is re-assigned the new approver needs to '
         + N'know they own it now. Delegations often happen mid-week '
         + N'(PTO, workload balancing) — without a notification the '
         + N'request lingers on a queue nobody is watching.',
         N'يصل إشعار التفويض للمعتمد الجديد لئلا يبقى الطلب في طابور لا يراقبه أحد عند تبديل المعتمدين.',
         N'APPROVAL_DELEGATED',
         N'{}',
         N'{"type":"NOTIFY","roles":["NEW_ASSIGNEE"],"template":"APPROVAL_DELEGATED"}',
         3, N'CUSTOM');
END

-- AR-69 ── Approval EXPIRED → escalate to manager + flag SLA breach
-- An expired approval is by definition an SLA breach — it sat in a
-- queue past its deadline without a decision. This is a process
-- failure that needs both the assignee's manager (to triage the
-- backlog) and the compliance officer (to record the SLA miss for
-- the audit trail).
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-69')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-69',
         N'Approval EXPIRED → escalate to manager + flag SLA breach',
         N'انتهاء صلاحية طلب الاعتماد → تصعيد للمدير وتسجيل خرق اتفاقية مستوى الخدمة',
         N'An expired approval is by definition an SLA breach — sat '
         + N'in a queue past its deadline without a decision. The '
         + N'assignee''s manager triages the backlog; the compliance '
         + N'officer records the SLA miss in the audit trail.',
         N'انتهاء صلاحية الاعتماد دون قرار يُعد خرقاً لمستوى الخدمة — يصعَّد للمدير ويُسجل لدى مسؤول الالتزام.',
         N'APPROVAL_EXPIRED',
         N'{}',
         N'{"type":"ESCALATE","roles":["ASSIGNEE_MANAGER","COMPLIANCE_OFFICER"],"template":"APPROVAL_EXPIRED"}',
         2, N'CUSTOM');
END

-- AR-70 ── Risk treatment approved → CRO milestone for ERM register
-- Risk treatments specifically need a CRO milestone on approval —
-- it's the moment a treatment plan moves from "proposed" to
-- "committed", which is the milestone the ERM register tracks for
-- the board pack. Routing this via a dedicated entityType condition
-- keeps the CRO's queue clean (they don't see every approval, just
-- the risk-treatment ones).
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-70')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-70',
         N'Risk treatment APPROVED → CRO milestone for ERM register',
         N'اعتماد معالجة المخاطر → معلم لرئيس إدارة المخاطر في سجل المخاطر',
         N'Risk treatments need a CRO milestone on approval — the '
         + N'moment a plan moves from proposed to committed, which '
         + N'is the ERM-register milestone for the board pack. The '
         + N'entityType condition keeps the CRO''s queue clean — '
         + N'they only see the risk-treatment approvals, not every '
         + N'approval in the org.',
         N'يحتاج اعتماد خطط معالجة المخاطر لمعلم لدى رئيس إدارة المخاطر — '
         + N'يدوّن الانتقال من خطة مقترحة إلى خطة معتمدة في سجل المخاطر لاستعراضها أمام المجلس.',
         N'APPROVAL_DECIDED',
         N'{"status":"APPROVED","entityType":"risk.treatment"}',
         N'{"type":"NOTIFY","roles":["CHIEF_RISK_OFFICER"],"template":"RISK_TREATMENT_APPROVED"}',
         4, N'CUSTOM');
END
GO

PRINT 'V140 seeded 5 approval automation rules (AR-66..AR-70).';
GO
