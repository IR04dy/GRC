-- V129: F8 automation rules for compliance CapaWorkflowService events.
--
-- PR #48 wired CapaWorkflowService into the F8 engine. It now publishes
-- a single DomainEvent type from all five transition methods:
--
--   FINDING_CAPA_STAGE_CHANGED — every stage transition on AuditFinding's
--                                 CAPA lifecycle (IDENTIFIED → IMPLEMENTING
--                                 → OWNER_TESTED → INDEPENDENT_TESTED
--                                 → AUDIT_SIGNED_OFF → CLOSED)
--
-- The payload carries (previousStage, newStage, status, severity,
-- clauseRef, result, byUserId, notes) — F8 rules match on the keys
-- that matter without needing five separate event types.
--
-- These five seed rules turn those transitions into the day-to-day
-- routing buyers expect for an audit-finding remediation lifecycle:
-- the right reviewer wakes up on FAIL, the auditor wakes up on
-- independent PASS, the board secretary gets the sign-off, etc.
--
-- The matcher today is equality-only (AutomationRuleEngine.matches:
-- numbers as doubles, booleans coerced, otherwise textual ==). So
-- condition JSON keys carry exact values. Richer operators (>, <,
-- contains) are a future engine change; until then we lean on the
-- payload's already-coarse fields (newStage, result).
--
-- AR-31..AR-35 picks up where V128 (CapaTask rules AR-26..AR-30) and
-- V127 (workflow-submission rules AR-21..AR-25) left off.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-31 ── Finding CAPA closed → audit milestone for NCA quarterly filing
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-31')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-31',
         N'Finding CAPA closed → NCA quarterly filing milestone',
         N'إغلاق CAPA من تدقيق → معلَم تقرير الهيئة الوطنية',
         N'When an audit finding reaches the CLOSED CAPA stage, write a '
         + N'regulatory milestone into platform.audit_log. The quarterly '
         + N'NCA-ECC filing template reads these to populate the "closed '
         + N'findings this quarter" section without a separate report '
         + N'query — the same role the CapaTask AR-29 rule plays for the '
         + N'remediation-task side of the same lifecycle.',
         N'تسجيل معلَم تنظيمي عند إغلاق CAPA من تدقيق — يستخدمه قالب '
         + N'تقرير NCA-ECC ربع السنوي لقائمة "نتائج مغلقة هذا الربع".',
         N'FINDING_CAPA_STAGE_CHANGED',
         N'{"newStage":"CLOSED"}',
         N'{"type":"AUDIT","severity":"INFO","message":"Audit finding CAPA closed — NCA quarterly filing milestone"}',
         25, N'CUSTOM');
END

-- AR-32 ── Owner test FAIL → notify the remediation owner (rework)
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-32')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-32',
         N'Owner test FAIL → notify remediation owner',
         N'فشل اختبار المالك → إشعار المعالج',
         N'When an owner-test result is FAIL, the CAPA stage stays at '
         + N'IMPLEMENTING (rework loop) — notify the remediation owner '
         + N'immediately so the next attempt starts without the manual '
         + N'"hey, your fix didn''t pass" email. Matches the CapaWorkflow '
         + N'§5.3 #6 rework semantics.',
         N'إشعار المعالج فور فشل اختبار المالك ليبدأ التصحيح بدون انتظار.',
         N'FINDING_CAPA_STAGE_CHANGED',
         N'{"result":"OWNER_TEST_FAIL"}',
         N'{"type":"NOTIFY","role":"REMEDIATION_OWNER","template":"CAPA_OWNER_TEST_FAIL"}',
         7, N'CUSTOM');
END

-- AR-33 ── Independent test FAIL → escalate to audit lead
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-33')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-33',
         N'Independent test FAIL → escalate to audit lead',
         N'فشل اختبار مستقل → تصعيد لقائد التدقيق',
         N'Independent FAIL means the owner''s reported success didn''t '
         + N'survive a second look — the auditor needs visibility before '
         + N'this drifts into another rework cycle. Escalate to the audit '
         + N'lead with a 24h SLA so the auditor either approves the rework '
         + N'plan or pulls the finding back to IMPLEMENTING formally.',
         N'تصعيد لقائد التدقيق خلال ٢٤ ساعة عند فشل الاختبار المستقل — '
         + N'يقرر إعادة العمل أو متابعة رسمية.',
         N'FINDING_CAPA_STAGE_CHANGED',
         N'{"result":"INDEPENDENT_TEST_FAIL"}',
         N'{"type":"ESCALATE","to_role":"AUDIT_LEAD","after_minutes":1440}',
         4, N'CUSTOM');
END

-- AR-34 ── Audit signoff → notify board secretary (finding ready for board pack)
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-34')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-34',
         N'Audit signoff → notify board secretary',
         N'توقيع المدقق → إشعار أمانة المجلس',
         N'AUDIT_SIGNED_OFF means the auditor has accepted the remediation '
         + N'evidence — the finding is ready to roll up to the next board '
         + N'pack. Notify the board secretary so the next quarterly pack '
         + N'includes it under "closed findings" without a manual chase.',
         N'إشعار أمانة المجلس عند توقيع المدقق — يدرج في حزمة المجلس القادمة '
         + N'ضمن النتائج المغلقة دون متابعة يدوية.',
         N'FINDING_CAPA_STAGE_CHANGED',
         N'{"newStage":"AUDIT_SIGNED_OFF"}',
         N'{"type":"NOTIFY","role":"BOARD_SECRETARY","template":"FINDING_AUDIT_SIGNED_OFF"}',
         15, N'CUSTOM');
END

-- AR-35 ── Independent test PASS → notify audit lead (green light for signoff)
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-35')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-35',
         N'Independent test PASS → notify audit lead',
         N'اجتياز الاختبار المستقل → إشعار قائد التدقيق',
         N'INDEPENDENT_TEST_PASS advances the finding to INDEPENDENT_TESTED — '
         + N'the next move is the auditor''s sign-off. Notify the audit '
         + N'lead so the sign-off doesn''t sit idle. Closes the loop with '
         + N'AR-33 (FAIL escalation) — same role notified, opposite signal.',
         N'إشعار قائد التدقيق عند اجتياز الاختبار المستقل ليبدأ التوقيع '
         + N'دون تأخير. مقابل AR-33: نفس الدور، نتيجة عكسية.',
         N'FINDING_CAPA_STAGE_CHANGED',
         N'{"result":"INDEPENDENT_TEST_PASS"}',
         N'{"type":"NOTIFY","role":"AUDIT_LEAD","template":"CAPA_INDEPENDENT_TEST_PASS"}',
         20, N'CUSTOM');
END
GO

PRINT 'V129 finding_capa_stage automation rules (AR-31..AR-35) seeded.';
GO
