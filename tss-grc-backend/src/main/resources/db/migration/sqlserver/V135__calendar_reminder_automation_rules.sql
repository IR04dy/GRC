-- V135: F8 automation rules for REMINDER_DUE events from the
-- CalendarReminderScheduler (PR shipping with Track A4).
--
-- The scheduler ticks hourly. For every persisted calendar_event whose
-- reminder ladder ('7,3,1,0') has a stop landing on today (after Saudi
-- workday-shift), it publishes a DomainEvent with:
--
--   triggerEvent = REMINDER_DUE
--   payload      = { calendarEventId, eventType, titleEn, titleAr,
--                    startsAt, daysBefore, nominalFireDate,
--                    actualFireDate, workdayShifted, severity,
--                    entityType, entityId, ownerId }
--
-- Five seed rules below cover the buyer-facing reminder routes. They
-- use single-key equality on eventType where possible — equality match
-- is the only condition mode the engine supports today
-- (AutomationRuleEngine.matches).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-51 ── AUDIT_DAY reminder → notify audit team + auditee owner
-- Audit days run hot — the auditor needs prep materials, the auditee
-- needs to corral evidence. We fire on every reminder-stop for an
-- AUDIT_DAY so the 7-day, 3-day, and same-day pings all land in
-- audit + auditee inboxes without separate rules.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-51')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-51',
         N'Audit-day reminder → notify audit team + auditee owner',
         N'تذكير يوم التدقيق → إشعار فريق التدقيق ومالك الجهة',
         N'Fires for each REMINDER_DUE where eventType=AUDIT_DAY. The '
         + N'auditor needs prep materials and the auditee needs to '
         + N'corral evidence — both inboxes wake up.',
         N'إشعار فريق التدقيق ومالك الجهة في كل تذكير ليوم تدقيق قادم.',
         N'REMINDER_DUE',
         N'{"eventType":"AUDIT_DAY"}',
         N'{"type":"NOTIFY","roles":["AUDITOR","AUDITEE_OWNER"],"template":"AUDIT_DAY_REMINDER"}',
         3, N'CUSTOM');
END

-- AR-52 ── REPORT_DEADLINE reminder → notify compliance officer + filer
-- Quarterly SAMA / NDI / DGA filings sit on the calendar as
-- REPORT_DEADLINE rows. The compliance officer signs them off; the
-- filer collects the data. Both must know when the lead-time clock
-- starts ticking.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-52')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-52',
         N'Report-deadline reminder → notify compliance officer + filer',
         N'تذكير استحقاق التقرير → إشعار مسؤول الامتثال والمُقدِّم',
         N'Fires on REMINDER_DUE for REPORT_DEADLINE rows — SAMA, '
         + N'NDI, DGA, PDPL filings. Compliance officer + filer get '
         + N'a heads-up at every ladder stop.',
         N'إشعار مسؤول الامتثال والمُقدِّم لكل تذكير قبل موعد إيداع رسمي.',
         N'REMINDER_DUE',
         N'{"eventType":"REPORT_DEADLINE"}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_OFFICER","REPORT_FILER"],"template":"REPORT_DEADLINE_REMINDER"}',
         2, N'CUSTOM');
END

-- AR-53 ── CAPA_DUE reminder → escalate to action owner + program lead
-- A CAPA approaching its target date is the highest-signal reminder
-- on the calendar — a missed CAPA is a missed audit finding which
-- becomes an external audit finding. Escalate (not just notify) so the
-- action owner cannot reasonably claim they didn't see it; loop the
-- CAPA program lead in case the owner is on leave.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-53')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-53',
         N'CAPA-due reminder → escalate to action owner + program lead',
         N'تذكير استحقاق إجراء تصحيحي → تصعيد لصاحب الإجراء وقائد البرنامج',
         N'CAPA approaching target date is the highest-signal '
         + N'reminder — a missed CAPA becomes a repeat finding. '
         + N'Escalate (not just notify) so the owner cannot claim '
         + N'they didn''t see it; loop the program lead as backup.',
         N'تصعيد فوري لصاحب الإجراء وقائد البرنامج عند اقتراب موعد إجراء تصحيحي.',
         N'REMINDER_DUE',
         N'{"eventType":"CAPA_DUE"}',
         N'{"type":"ESCALATE","roles":["ACTION_OWNER","CAPA_PROGRAM_LEAD"],"template":"CAPA_DUE_REMINDER"}',
         1, N'CUSTOM');
END

-- AR-54 ── DR_TEST reminder → notify BCM coordinator + tech lead
-- ISO 22301 §8.5 mandates rehearsed disaster-recovery testing.
-- Reminders for DR_TEST go to the BCM coordinator (owns the schedule)
-- and the tech lead (owns the runbook). Both need lead time to confirm
-- the test window doesn't collide with a change-freeze.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-54')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-54',
         N'DR-test reminder → notify BCM coordinator + tech lead',
         N'تذكير اختبار تعافي → إشعار منسق استمرارية الأعمال والمسؤول التقني',
         N'ISO 22301 §8.5 mandates rehearsed DR testing. BCM '
         + N'coordinator owns the schedule, tech lead owns the '
         + N'runbook — both get lead time to confirm the window.',
         N'إشعار منسق استمرارية الأعمال والمسؤول التقني قبل أي اختبار تعافي مجدول.',
         N'REMINDER_DUE',
         N'{"eventType":"DR_TEST"}',
         N'{"type":"NOTIFY","roles":["BCM_COORDINATOR","TECH_LEAD"],"template":"DR_TEST_REMINDER"}',
         3, N'CUSTOM');
END

-- AR-55 ── Generic catch-all reminder → notify event owner
-- For event types not covered by AR-51..AR-54 (MEETING, TRAINING,
-- REMINDER, CUSTOM, RISK_ATTESTATION, FINDING_DEADLINE, AAR_REVIEW,
-- DSR_DEADLINE, WORKFLOW_DEADLINE), default route the reminder to
-- whoever set the event up. Priority 9 so the more-specific rules
-- above run first and this one is the fallback.
--
-- The condition is empty {} which means "match every REMINDER_DUE"
-- under the engine's equality logic — combined with the priority
-- ordering this acts as the safety net. If an org wants to silence
-- the catch-all for a specific event type, they disable AR-55 and
-- add a tighter custom rule.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-55')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-55',
         N'Generic reminder → notify event owner',
         N'تذكير عام → إشعار مالك الحدث',
         N'Catch-all REMINDER_DUE route — fires for any reminder '
         + N'whose eventType has no more specific rule. Priority 9 '
         + N'so AR-51..AR-54 run first when they match.',
         N'مسار افتراضي للتذكيرات — يعمل حين لا يطابق الحدث قاعدة أكثر تخصيصاً.',
         N'REMINDER_DUE',
         N'{}',
         N'{"type":"NOTIFY","roles":["EVENT_OWNER"],"template":"GENERIC_REMINDER"}',
         9, N'CUSTOM');
END
GO

PRINT 'V135 seeded 5 REMINDER_DUE automation rules (AR-51..AR-55).';
GO
