-- V138: F8 automation rules for IncidentService events.
--
-- Wires the NIST SP 800-61r2 incident lifecycle into the F8 engine.
-- IncidentService now publishes four DomainEvents on every mutation:
--
--   INCIDENT_CREATED              — new incident logged
--   INCIDENT_PHASE_CHANGED        — NIST phase advance / set
--                                   (carries previousPhase in payload)
--   INCIDENT_CLOSED               — terminal close
--   INCIDENT_REGULATOR_NOTIFIED   — Saudi regulator notification recorded
--                                   (NCA_HEY / DATA_OFFICE / CITC / SAMA / HRSD)
--
-- AR-56..AR-60 continue the audit + automation parity arc that
-- closed at AR-50 (V132). The Incident service is the seventh
-- audit-logged service to gain canonical F8 events with seed rules
-- consuming them.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-56 ── CRITICAL incident raised → escalate to CISO + crisis coord
-- A CRITICAL incident demands executive eyes within minutes, not in
-- the next standup. The CISO owns cyber response; the crisis
-- coordinator owns the cross-functional war room. Both get a
-- top-priority escalation the moment INCIDENT_CREATED fires with
-- severity=CRITICAL. NIST SP 800-61r2 §3.2.7 "communicate severity
-- to authorised personnel before classification stabilises".
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-56')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-56',
         N'CRITICAL incident raised → escalate to CISO + crisis coord',
         N'حادثة حرجة → تصعيد لرئيس الأمن السيبراني ومنسق الأزمات',
         N'A CRITICAL incident demands executive eyes within minutes. '
         + N'CISO + crisis coordinator wake up the moment the incident '
         + N'is logged. NIST SP 800-61r2 §3.2.7 — communicate severity '
         + N'to authorised personnel before classification stabilises.',
         N'تصعيد فوري لرئيس الأمن السيبراني ومنسق الأزمات عند تسجيل أي حادثة حرجة — '
         + N'وفق NIST SP 800-61r2 لإبلاغ الجهات المعنية قبل تثبيت التصنيف.',
         N'INCIDENT_CREATED',
         N'{"severity":"CRITICAL"}',
         N'{"type":"ESCALATE","roles":["CISO","CRISIS_COORDINATOR"],"template":"INCIDENT_CRITICAL_RAISED"}',
         1, N'CUSTOM');
END

-- AR-57 ── Phase advance to CONTAINMENT → notify incident commander
-- The transition into CONTAINMENT is the operational pivot point — it
-- means the SOC has analysed the incident enough to start isolating
-- the affected systems. The incident commander needs to be in the
-- loop because containment decisions (e.g. isolating a domain
-- controller) have business-continuity implications.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-57')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-57',
         N'Phase advance to CONTAINMENT → notify incident commander',
         N'الانتقال إلى مرحلة الاحتواء → إشعار قائد الاستجابة',
         N'Containment decisions (e.g. isolating a domain controller) '
         + N'have business-continuity implications. The incident '
         + N'commander needs to be in the loop the moment SOC pivots '
         + N'from ANALYSIS to CONTAINMENT.',
         N'إشعار قائد الاستجابة عند انتقال الحادثة من التحليل إلى الاحتواء، '
         + N'لما لقرارات الاحتواء من تأثيرات على استمرارية الأعمال.',
         N'INCIDENT_PHASE_CHANGED',
         N'{"nistPhase":"CONTAINMENT"}',
         N'{"type":"NOTIFY","roles":["INCIDENT_COMMANDER"],"template":"INCIDENT_CONTAINMENT_START"}',
         2, N'CUSTOM');
END

-- AR-58 ── PDPL personal-data breach → notify DPO + start 72h clock
-- PDPL Article 21 mandates a 72-hour notification window from
-- discovery of a personal-data breach. When an incident is logged
-- with category=DATA_BREACH the DPO must be told immediately so they
-- can stage the regulator notification + affected-individual
-- communications inside the window. We use a single rule keyed on
-- category — severity is irrelevant; a confirmed personal-data
-- breach starts the clock regardless of NCA classification.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-58')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-58',
         N'PDPL personal-data breach → notify DPO + start 72h clock',
         N'خرق بيانات شخصية وفق نظام حماية البيانات → إشعار مسؤول حماية البيانات وبدء عداد 72 ساعة',
         N'PDPL Article 21 mandates a 72-hour notification window from '
         + N'discovery of a personal-data breach. The DPO needs to '
         + N'stage the regulator notification + affected-individual '
         + N'communications inside the window — severity is '
         + N'irrelevant once category=DATA_BREACH is confirmed.',
         N'يفرض نظام حماية البيانات (المادة 21) إشعار خرق البيانات خلال 72 ساعة من الاكتشاف — '
         + N'يصل الإشعار لمسؤول حماية البيانات فور التأكد من فئة الحادثة.',
         N'INCIDENT_CREATED',
         N'{"category":"DATA_BREACH"}',
         N'{"type":"ESCALATE","roles":["DPO"],"template":"INCIDENT_PDPL_BREACH"}',
         1, N'CUSTOM');
END

-- AR-59 ── Incident CLOSED → milestone + CAPA lookback
-- Closing an incident triggers two follow-up obligations: (1) write
-- the IIA / ISO 27035 milestone for the audit committee, and (2) the
-- CAPA program lead reviews any CAPAs that came out of this incident
-- to ensure they're tracked to closure. The milestone is a NOTIFY to
-- the audit committee role; the CAPA lookback is a notification to
-- the CAPA program lead — same rule, two recipients.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-59')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-59',
         N'Incident CLOSED → audit milestone + CAPA lookback',
         N'إغلاق الحادثة → معلم تدقيق ومراجعة الإجراءات التصحيحية',
         N'Incident closure triggers two follow-ups: (1) the IIA / '
         + N'ISO 27035 milestone for the audit committee, and (2) the '
         + N'CAPA program lead reviews CAPAs that came out of this '
         + N'incident to ensure they''re tracked to closure.',
         N'يستوجب إغلاق الحادثة تسجيل معلم تدقيق ومراجعة الإجراءات التصحيحية الناتجة عنها.',
         N'INCIDENT_CLOSED',
         N'{}',
         N'{"type":"NOTIFY","roles":["AUDIT_COMMITTEE","CAPA_PROGRAM_LEAD"],"template":"INCIDENT_CLOSED_MILESTONE"}',
         5, N'CUSTOM');
END

-- AR-60 ── Regulator notified → record in regulator log + notify GC
-- Every regulator notification is a legally significant act. The
-- General Counsel needs visibility (so external counsel can be looped
-- in if needed) and the regulator log needs an audit entry separate
-- from the incident's internal phase log. The notification regulator
-- is in payload.regulator (NCA_HEY/DATA_OFFICE/CITC/SAMA/HRSD) — a
-- single catch-all rule covers all five today; tighter per-regulator
-- routing can be added later without code changes.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-60')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-60',
         N'Regulator notified → audit log + general counsel',
         N'إخطار جهة تنظيمية → تسجيل تدقيق وإشعار المستشار العام',
         N'Every regulator notification is a legally significant act. '
         + N'GC needs visibility (external counsel may need looping '
         + N'in) and the regulator log needs an audit entry separate '
         + N'from the incident''s internal phase log.',
         N'كل إخطار لجهة تنظيمية هو إجراء ذو أثر قانوني — يصل المستشار العام لتقدير الحاجة لخبرة خارجية، '
         + N'ويُسجَّل في سجل تدقيق مستقل عن سجل أطوار الحادثة.',
         N'INCIDENT_REGULATOR_NOTIFIED',
         N'{}',
         N'{"type":"NOTIFY","roles":["GENERAL_COUNSEL","COMPLIANCE_OFFICER"],"template":"INCIDENT_REGULATOR_NOTIFIED"}',
         3, N'CUSTOM');
END
GO

PRINT 'V138 seeded 5 incident automation rules (AR-56..AR-60).';
GO
