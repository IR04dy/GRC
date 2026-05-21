-- V130: F8 automation rules for FindingService (audit_lc) events.
--
-- PR #50 wired FindingService into the F8 engine. It now publishes two
-- DomainEvents on every mutation:
--
--   FINDING_CREATED        — audit finding raised on an engagement
--   FINDING_TRANSITIONED   — OPEN → MGMT_RESPONSE → IN_REMEDIATION
--                            → AWAITING_VERIFICATION → CLOSED
--
-- These five seed rules thread the IIA Standard 2330 cadence through
-- the F8 engine: at each state-machine step the right actor wakes up
-- without manual chase emails.
--
-- The payload (externalId, refNumber, engagementCode, severity, status,
-- ownerRole, previousStatus) is matched equality-only by the engine
-- today (AutomationRuleEngine.matches). All rules below use single-key
-- or AND-of-equality conditions.
--
-- AR-36..AR-40 picks up where V129 (FindingCapaWorkflow AR-31..AR-35)
-- left off — fourth service to close the audit + automation loop.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-36 ── CRITICAL finding raised → notify audit lead immediately
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-36')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-36',
         N'CRITICAL finding raised → notify audit lead',
         N'إثارة نتيجة حرجة → إشعار قائد التدقيق فوراً',
         N'When a finding with CRITICAL severity is raised, notify the '
         + N'audit lead immediately so the engagement partner can decide '
         + N'whether to escalate to the audit committee before the next '
         + N'scheduled close meeting. Matches the IIA Standard 2330 '
         + N'"significant findings communicated promptly" cadence.',
         N'إشعار فوري لقائد التدقيق عند إثارة نتيجة حرجة — وفق معيار IIA 2330 '
         + N'لإبلاغ النتائج الجوهرية بدون انتظار اجتماع الإغلاق.',
         N'FINDING_CREATED',
         N'{"severity":"CRITICAL"}',
         N'{"type":"NOTIFY","role":"AUDIT_LEAD","template":"FINDING_CRITICAL_RAISED"}',
         2, N'CUSTOM');
END

-- AR-37 ── Mgmt response received → notify engagement partner
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-37')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-37',
         N'Management response received → notify engagement partner',
         N'استلام رد الإدارة → إشعار شريك الارتباط',
         N'When a finding transitions to MGMT_RESPONSE, the engagement '
         + N'partner needs to review the response before the team can '
         + N'sign off on the implementation plan. Notify them so the '
         + N'review doesn''t sit waiting for someone to remember it.',
         N'إشعار شريك الارتباط بمراجعة رد الإدارة قبل اعتماد خطة التنفيذ.',
         N'FINDING_TRANSITIONED',
         N'{"status":"MGMT_RESPONSE"}',
         N'{"type":"NOTIFY","role":"ENGAGEMENT_PARTNER","template":"FINDING_MGMT_RESPONSE_RECEIVED"}',
         14, N'CUSTOM');
END

-- AR-38 ── In remediation → notify the owner role recorded on the finding
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-38')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-38',
         N'Finding in remediation → notify owner role',
         N'النتيجة قيد المعالجة → إشعار صاحب الدور',
         N'When a finding enters IN_REMEDIATION, send a kick-off note to '
         + N'the recorded owner role so accountability is recorded in '
         + N'the notifications outbox alongside the audit-log row. '
         + N'Owner role is carried in the event payload from the finding '
         + N'owner_role column.',
         N'إشعار صاحب الدور المُعيَّن في النتيجة عند بدء المعالجة — تسجيل المسؤولية '
         + N'في صندوق التنبيهات إلى جانب سجل التدقيق.',
         N'FINDING_TRANSITIONED',
         N'{"status":"IN_REMEDIATION"}',
         N'{"type":"NOTIFY","role":"FINDING_OWNER","template":"FINDING_REMEDIATION_STARTED"}',
         16, N'CUSTOM');
END

-- AR-39 ── Awaiting verification → notify independent reviewer
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-39')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-39',
         N'Awaiting verification → notify independent reviewer',
         N'بانتظار التحقق → إشعار المراجع المستقل',
         N'When a finding reaches AWAITING_VERIFICATION the owner has '
         + N'declared remediation complete — the independent reviewer '
         + N'must validate before the finding can close. Notify them '
         + N'so verification doesn''t sit in the queue.',
         N'إشعار المراجع المستقل عند انتقال النتيجة لـ AWAITING_VERIFICATION — '
         + N'بدء التحقق دون تأخير.',
         N'FINDING_TRANSITIONED',
         N'{"status":"AWAITING_VERIFICATION"}',
         N'{"type":"NOTIFY","role":"INDEPENDENT_REVIEWER","template":"FINDING_AWAITING_VERIFICATION"}',
         11, N'CUSTOM');
END

-- AR-40 ── Finding closed → write IIA Standard 2330 closure milestone
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-40')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-40',
         N'Finding closed → IIA Standard 2330 closure milestone',
         N'إغلاق النتيجة → معلَم IIA 2330',
         N'When a finding moves to CLOSED, write an IIA Standard 2330 '
         + N'milestone into platform.audit_log. Pairs with AR-29 (CapaTask '
         + N'CLOSED) and AR-31 (AuditFinding CAPA-stage CLOSED): the '
         + N'quarterly NCA-ECC filing template reads all three milestone '
         + N'types to build the "closed findings this quarter" section '
         + N'without separate report queries.',
         N'كتابة معلَم IIA 2330 عند إغلاق النتيجة — مع AR-29 و AR-31 '
         + N'تُكمل صورة "النتائج المغلقة هذا الربع" لتقرير NCA-ECC.',
         N'FINDING_TRANSITIONED',
         N'{"status":"CLOSED"}',
         N'{"type":"AUDIT","severity":"INFO","message":"Audit finding closed — IIA Standard 2330 milestone"}',
         28, N'CUSTOM');
END
GO

PRINT 'V130 finding_lifecycle automation rules (AR-36..AR-40) seeded.';
GO
