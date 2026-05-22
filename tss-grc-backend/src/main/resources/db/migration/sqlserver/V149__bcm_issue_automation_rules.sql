-- V149: F8 automation rules for BcmCycleService + IssueService events.
--
-- Two services in one V-file because they share the "issue/cycle
-- intake" audience (BCM coordinator + audit committee) and route
-- through the same notification ladder.
--
-- BcmCycleService publishes (two aggregates, sharing this service):
--   BCM_CYCLE_CREATED              — new annual cycle row
--   BCM_CYCLE_STARTED              — DRAFT → IN_PROGRESS
--   BCM_CYCLE_UNDER_REVIEW         — IN_PROGRESS → UNDER_REVIEW
--   BCM_CYCLE_APPROVED             — UNDER_REVIEW → APPROVED
--   BCM_CYCLE_ARCHIVED             — APPROVED → ARCHIVED
--   BCM_GOVERNANCE_CREATED         — new governance record
--   BCM_GOVERNANCE_UNDER_REVIEW    — DRAFT → UNDER_REVIEW
--   BCM_GOVERNANCE_APPROVED        — UNDER_REVIEW → APPROVED
--   BCM_GOVERNANCE_ACTIVATED       — APPROVED → ACTIVE
--   BCM_GOVERNANCE_REVIEW_DUE      — ACTIVE → REVIEW_DUE
--   BCM_GOVERNANCE_ARCHIVED        — terminal
--   BCM_GOVERNANCE_CHANGES_REQUIRED — UNDER_REVIEW → CHANGES_REQUIRED
--
-- IssueService publishes:
--   ISSUE_CREATED       — new issue logged
--   ISSUE_STARTED       — OPEN → IN_PROGRESS
--   ISSUE_BLOCKED       — IN_PROGRESS → BLOCKED
--   ISSUE_RESOLVED      — → RESOLVED (resolution required)
--   ISSUE_CLOSED        — RESOLVED → CLOSED
--   ISSUE_CANCELLED     — OPEN → CANCELLED
--   ISSUE_TRANSITIONED  — catch-all
--
-- AR-111..AR-115 — services #19 + #20 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-111 ── New BCM cycle created → notify BCM coordinator + audit committee
-- The annual BCM cycle is the heartbeat — every BIA, BCP, DR test,
-- and AAR lands inside one. Coordinator owns the calendar; audit
-- committee tracks cycle completeness as a board-level KPI.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-111')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-111',
         N'New BCM cycle → notify coordinator + audit committee',
         N'دورة استمرارية أعمال جديدة → إشعار المنسق ولجنة التدقيق',
         N'The annual BCM cycle is the heartbeat — every BIA, BCP, '
         + N'DR test, and AAR lands inside one. Coordinator owns the '
         + N'calendar; audit committee tracks cycle completeness as '
         + N'a board-level KPI.',
         N'الدورة السنوية لاستمرارية الأعمال هي القلب — يبدأ المنسق التخطيط، '
         + N'وتتابع لجنة التدقيق اكتمالها كمؤشر للمجلس.',
         N'BCM_CYCLE_CREATED',
         N'{}',
         N'{"type":"NOTIFY","roles":["BCM_COORDINATOR","AUDIT_COMMITTEE"],"template":"BCM_CYCLE_CREATED"}',
         4, N'CUSTOM');
END

-- AR-112 ── BCM cycle APPROVED → executive sponsor + audit committee milestone
-- A cycle approval is the formal closure of the annual BCM exercise.
-- Executive sponsor confirms management commitment for next year's
-- budget; audit committee records the milestone for the annual
-- governance report.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-112')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-112',
         N'BCM cycle APPROVED → executive sponsor + audit committee',
         N'اعتماد دورة استمرارية الأعمال → الراعي التنفيذي ولجنة التدقيق',
         N'Cycle approval formally closes the annual BCM exercise. '
         + N'Executive sponsor confirms management commitment for '
         + N'next year''s budget; audit committee records the '
         + N'milestone for the annual governance report.',
         N'اعتماد الدورة يغلق التمرين السنوي رسمياً — يصل الراعي التنفيذي لتأكيد الاستمرار، '
         + N'ولجنة التدقيق لتسجيل المعلم في التقرير السنوي.',
         N'BCM_CYCLE_APPROVED',
         N'{}',
         N'{"type":"NOTIFY","roles":["EXECUTIVE_SPONSOR","AUDIT_COMMITTEE","BCM_COORDINATOR"],"template":"BCM_CYCLE_APPROVED"}',
         3, N'CUSTOM');
END

-- AR-113 ── BCM governance REVIEW_DUE → coordinator schedules refresh
-- ACTIVE governance records flip to REVIEW_DUE when their
-- next_review_date passes (annual / semi-annual / quarterly /
-- on-change). Coordinator needs to schedule the refresh before it
-- becomes a control gap.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-113')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-113',
         N'BCM governance review due → coordinator schedules refresh',
         N'استحقاق مراجعة حوكمة استمرارية الأعمال → جدولة التحديث',
         N'ACTIVE governance records flip to REVIEW_DUE when their '
         + N'next_review_date passes. Coordinator schedules the '
         + N'refresh before it becomes a control gap.',
         N'تنتقل سجلات الحوكمة النشطة إلى حالة مستحقة المراجعة عند انقضاء الموعد — '
         + N'يجدول المنسق المراجعة قبل أن تتحول لفجوة.',
         N'BCM_GOVERNANCE_REVIEW_DUE',
         N'{}',
         N'{"type":"NOTIFY","roles":["BCM_COORDINATOR","POLICY_OWNER"],"template":"BCM_GOVERNANCE_REVIEW_DUE"}',
         3, N'CUSTOM');
END

-- AR-114 ── CRITICAL or HIGH-severity issue created → assignee + manager
-- Cross-module issues flow through this service. CRITICAL/HIGH
-- severity gets immediate assignee notification + manager visibility
-- so triage starts the same day. P1 because Critical doesn't wait.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-114')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-114',
         N'CRITICAL issue created → notify assignee + manager',
         N'تسجيل قضية حرجة → إشعار المُسند له والمدير',
         N'Cross-module issues flow through this service. CRITICAL '
         + N'severity gets immediate assignee notification + manager '
         + N'visibility so triage starts the same day.',
         N'القضايا الحرجة لا تنتظر — يصل الإشعار للمُسند له والمدير فوراً لبدء المعالجة في يومها.',
         N'ISSUE_CREATED',
         N'{"severity":"CRITICAL"}',
         N'{"type":"NOTIFY","roles":["ASSIGNEE","ASSIGNEE_MANAGER"],"template":"ISSUE_CRITICAL"}',
         1, N'CUSTOM');
END

-- AR-115 ── Issue BLOCKED → escalate to manager + assignee
-- A BLOCKED transition means the assignee can't make progress
-- without external help. Manager intervention unblocks faster than
-- waiting for the assignee to surface the obstacle organically.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-115')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-115',
         N'Issue BLOCKED → escalate to manager',
         N'قضية معلَّقة → تصعيد للمدير',
         N'A BLOCKED transition means the assignee can''t make '
         + N'progress without external help. Manager intervention '
         + N'unblocks faster than waiting for the assignee to '
         + N'surface the obstacle organically.',
         N'حالة التعليق تعني أن المُسند له لا يستطيع التقدم — '
         + N'تدخل المدير يسرّع رفع العائق بدل انتظار طرحه طبيعياً.',
         N'ISSUE_BLOCKED',
         N'{}',
         N'{"type":"ESCALATE","roles":["ASSIGNEE_MANAGER","COMPLIANCE_OFFICER"],"template":"ISSUE_BLOCKED"}',
         2, N'CUSTOM');
END
GO

PRINT 'V149 seeded 5 BCM cycle + issue automation rules (AR-111..AR-115).';
GO
