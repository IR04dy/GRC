-- V148: F8 automation rules for RegulatoryChangeService events.
--
-- Regulatory change is the compliance team's intake-to-implementation
-- pipeline — every new NCA ECC revision, SAMA circular, PDPL
-- clarification, or DGA framework update enters here, gets assessed
-- for applicability, gets sized for impact, and (if applicable) gets
-- an action plan that lands in CAPA / policy / control updates.
--
-- RegulatoryChangeService publishes:
--
--   REG_CHANGE_NEW                — new change logged
--   REG_CHANGE_ASSESSED           — applicability decided
--                                   (payload carries applicability)
--   REG_CHANGE_IMPACT_ANALYSED    — impact + scope quantified
--                                   (payload carries impactScore)
--   REG_CHANGE_IN_IMPLEMENTATION  — action plan launched
--   REG_CHANGE_IMPLEMENTED        — controls/policies updated
--   REG_CHANGE_CLOSED             — terminal close
--   REG_CHANGE_DEPRIORITISED      — not applicable / deferred
--
-- AR-106..AR-110 — service #18 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-106 ── New regulatory change logged → notify compliance lead + DPO
-- Every new regulatory change starts an applicability-assessment
-- clock. Compliance lead routes the assessment (which framework
-- owners to consult, what evidence to pull); DPO joins because
-- PDPL changes often arrive without explicit "this is PDPL"
-- labelling — early visibility avoids assessment delays.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-106')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-106',
         N'New regulatory change → notify compliance lead + DPO',
         N'تغيير تنظيمي جديد → إشعار قائد الالتزام ومسؤول حماية البيانات',
         N'Every new regulatory change starts an applicability '
         + N'clock. Compliance lead routes the assessment; DPO '
         + N'joins because PDPL changes often arrive without '
         + N'explicit "PDPL" labelling — early visibility avoids '
         + N'assessment delays.',
         N'يبدأ كل تغيير تنظيمي عدّاد تقييم التطبيق — '
         + N'يصل قائد الالتزام ومسؤول حماية البيانات لتوجيه التقييم وتجنب أي تأخير.',
         N'REG_CHANGE_NEW',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_LEAD","DPO"],"template":"REG_CHANGE_NEW"}',
         3, N'CUSTOM');
END

-- AR-107 ── Change assessed as APPLICABLE → CRO + audit committee aware
-- APPLICABLE means the new regulation hits the organisation. CRO
-- needs to plan for impact analysis; audit committee tracks pending
-- regulatory changes as a board-level risk.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-107')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-107',
         N'Reg change assessed APPLICABLE → CRO + audit committee notify',
         N'تقييم التغيير كقابل للتطبيق → إشعار رئيس المخاطر ولجنة التدقيق',
         N'APPLICABLE means the regulation hits the organisation. '
         + N'CRO plans for impact analysis; audit committee tracks '
         + N'pending regulatory changes as a board-level risk.',
         N'القابلية للتطبيق تعني أن التغيير يمس المؤسسة — '
         + N'يصل رئيس المخاطر ولجنة التدقيق للتخطيط للأثر.',
         N'REG_CHANGE_ASSESSED',
         N'{"applicability":"APPLICABLE"}',
         N'{"type":"NOTIFY","roles":["CHIEF_RISK_OFFICER","AUDIT_COMMITTEE","COMPLIANCE_LEAD"],"template":"REG_CHANGE_APPLICABLE"}',
         2, N'CUSTOM');
END

-- AR-108 ── HIGH-impact analysis complete → escalate executive committee
-- A HIGH impact score (typically 7+ on a 10-point scale) signals
-- the regulation requires material control / process / system
-- changes. Executive committee + CRO + GC need visibility before
-- implementation begins so the budget + timeline get executive
-- alignment.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-108')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-108',
         N'HIGH-impact regulatory change → escalate executive committee',
         N'تغيير تنظيمي عالي الأثر → تصعيد للجنة التنفيذية',
         N'A HIGH impact signals material control / process / '
         + N'system changes are required. Executive committee + '
         + N'CRO + GC need visibility before implementation begins '
         + N'so budget + timeline get executive alignment.',
         N'الأثر العالي يستوجب تغييرات جوهرية في الضوابط أو الأنظمة — '
         + N'يصل التصعيد للجنة التنفيذية ورئيس المخاطر والمستشار العام لمواءمة الميزانية والجدول الزمني.',
         N'REG_CHANGE_IMPACT_ANALYSED',
         N'{"impactScore":"HIGH"}',
         N'{"type":"ESCALATE","roles":["EXECUTIVE_COMMITTEE","CHIEF_RISK_OFFICER","GENERAL_COUNSEL"],"template":"REG_CHANGE_HIGH_IMPACT"}',
         1, N'CUSTOM');
END

-- AR-109 ── Implementation started → CAPA program lead links plan items
-- IN_IMPLEMENTATION means the action plan is live. CAPA program
-- lead links the CAPAs / policy updates / control changes that
-- came out of the impact analysis so progress is trackable in
-- one place rather than scattered across modules.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-109')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-109',
         N'Reg change implementation started → CAPA program lead links items',
         N'بدء تنفيذ التغيير التنظيمي → ربط الإجراءات التصحيحية من قبل القائد',
         N'IN_IMPLEMENTATION means the action plan is live. CAPA '
         + N'program lead links the CAPAs / policy updates / '
         + N'control changes so progress is trackable in one '
         + N'place rather than scattered across modules.',
         N'بدء تنفيذ التغيير يستوجب ربط الإجراءات والسياسات والضوابط بنقطة واحدة لتتبع التقدم — '
         + N'يصل قائد الإجراءات التصحيحية لإدارة الربط.',
         N'REG_CHANGE_IN_IMPLEMENTATION',
         N'{}',
         N'{"type":"NOTIFY","roles":["CAPA_PROGRAM_LEAD","COMPLIANCE_LEAD"],"template":"REG_CHANGE_IMPLEMENTATION"}',
         4, N'CUSTOM');
END

-- AR-110 ── Reg change IMPLEMENTED → framework mapping refresh trigger
-- Implementation typically means new policy versions, updated
-- controls, additional evidence types. Compliance lead refreshes
-- the framework→control mapping so coverage analytics reflect
-- the new state. Audit committee tracks as KPI (time-to-implement
-- by regulator).
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-110')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-110',
         N'Reg change IMPLEMENTED → framework mapping refresh hint',
         N'تنفيذ التغيير التنظيمي → تنبيه لتحديث ربط الأطر',
         N'Implementation typically means new policy versions, '
         + N'updated controls, additional evidence types. '
         + N'Compliance lead refreshes the framework→control '
         + N'mapping so coverage analytics reflect the new state. '
         + N'Audit committee tracks time-to-implement as KPI.',
         N'تنفيذ التغيير عادةً ما يستوجب تحديث ربط الأطر بالضوابط — '
         + N'يصل قائد الالتزام لتحديث الربط ولجنة التدقيق لمتابعة زمن التنفيذ.',
         N'REG_CHANGE_IMPLEMENTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_LEAD","AUDIT_COMMITTEE"],"template":"REG_CHANGE_IMPLEMENTED"}',
         5, N'CUSTOM');
END
GO

PRINT 'V148 seeded 5 regulatory-change automation rules (AR-106..AR-110).';
GO
