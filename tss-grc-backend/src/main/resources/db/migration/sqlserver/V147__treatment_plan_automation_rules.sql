-- V147: F8 automation rules for TreatmentPlanService events.
--
-- Treatment plans are the commitment side of the risk register —
-- the moment a plan is APPROVED the residual-risk number on the
-- parent risk becomes a board-level commitment. Wiring the
-- treatment lifecycle into F8 lets downstream consumers (board
-- pack assembly, CRO dashboards, CAPA fan-out) react automatically.
--
-- TreatmentPlanService publishes:
--
--   TREATMENT_DRAFTED    — new plan opened
--   TREATMENT_SUBMITTED  — DRAFT → SUBMITTED
--   TREATMENT_APPROVED   — SUBMITTED → APPROVED (board commitment)
--   TREATMENT_REJECTED   — SUBMITTED → REJECTED (rework path)
--   TREATMENT_STARTED    — APPROVED → IN_PROGRESS
--   TREATMENT_COMPLETED  — IN_PROGRESS → COMPLETED (residual realised)
--   TREATMENT_REOPENED   — REJECTED → DRAFT (rework loop)
--   TREATMENT_TRANSITIONED — catch-all
--
-- AR-101..AR-105 — service #17 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- AR-101 ── ACCEPT-strategy treatment submitted → CRO + GC pre-review
-- An ACCEPT strategy is the strategy of last resort — the
-- organisation deliberately chooses to live with the residual risk
-- rather than mitigate. CRO + GC see it BEFORE the formal approval
-- so they can ask the "have we exhausted mitigation options" and
-- "what's the documented compensating control" questions.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-101')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-101',
         N'ACCEPT-strategy treatment submitted → CRO + GC pre-review',
         N'تقديم خطة قبول المخاطر → مراجعة مسبقة من رئيس المخاطر والمستشار العام',
         N'ACCEPT is the strategy of last resort — the organisation '
         + N'chooses to live with the residual risk rather than '
         + N'mitigate. CRO + GC see it BEFORE formal approval so '
         + N'they can ask the "have we exhausted mitigation" and '
         + N'"what''s the compensating control" questions.',
         N'استراتيجية قبول المخاطر آخر الخيارات — يراجع رئيس المخاطر والمستشار العام الخطة قبل الاعتماد '
         + N'للتحقق من استنفاد بدائل المعالجة وتوثيق الضوابط البديلة.',
         N'TREATMENT_SUBMITTED',
         N'{"strategy":"ACCEPT"}',
         N'{"type":"ESCALATE","roles":["CHIEF_RISK_OFFICER","GENERAL_COUNSEL"],"template":"TREATMENT_ACCEPT_SUBMITTED"}',
         2, N'CUSTOM');
END

-- AR-102 ── Treatment APPROVED → board pack feed + risk register update
-- An approved treatment is a board-level commitment — the residual
-- risk number on the parent risk just became reportable. Board pack
-- assembly + risk register need the heads-up so the next monthly
-- ERM report shows the new commitment.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-102')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-102',
         N'Treatment APPROVED → board pack feed + CRO milestone',
         N'اعتماد خطة المعالجة → تغذية ملف المجلس ومعلم لرئيس المخاطر',
         N'An approved treatment is a board-level commitment — the '
         + N'residual-risk number on the parent risk just became '
         + N'reportable. Board pack assembly + CRO get the heads-up '
         + N'so the next monthly ERM report shows the commitment.',
         N'الموافقة على خطة المعالجة التزام أمام المجلس — يصل ملف المجلس ورئيس المخاطر لتحديث التقرير الشهري بالتزامن.',
         N'TREATMENT_APPROVED',
         N'{}',
         N'{"type":"NOTIFY","roles":["CHIEF_RISK_OFFICER","BOARD_PACK_OWNER"],"template":"TREATMENT_APPROVED"}',
         3, N'CUSTOM');
END

-- AR-103 ── Treatment REJECTED → notify submitter + risk owner for rework
-- A rejection means the plan needs revision (often the cost-benefit
-- didn't hold up, the strategy was wrong, or the controls weren't
-- specific enough). Submitter knows why; risk owner tracks the
-- rework cycle so the parent risk doesn't sit untreated.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-103')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-103',
         N'Treatment REJECTED → notify submitter + risk owner',
         N'رفض خطة المعالجة → إشعار مقدم الخطة ومالك المخاطرة',
         N'A rejection means revision — cost-benefit didn''t hold '
         + N'up, strategy was wrong, or controls weren''t specific '
         + N'enough. Submitter knows why; risk owner tracks the '
         + N'rework cycle so the parent risk doesn''t sit untreated.',
         N'رفض الخطة يستوجب المراجعة — يصل المقدم لمعرفة السبب ومالك المخاطرة لمتابعة دورة المعالجة.',
         N'TREATMENT_REJECTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["TREATMENT_SUBMITTER","RISK_OWNER"],"template":"TREATMENT_REJECTED"}',
         3, N'CUSTOM');
END

-- AR-104 ── Treatment STARTED → CAPA program lead links the controls
-- IN_PROGRESS means the treatment is being implemented. CAPA program
-- lead links any CAPAs that came out of the parent risk to this
-- treatment so progress is visible in both places.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-104')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-104',
         N'Treatment STARTED → CAPA program lead links controls',
         N'بدء تنفيذ المعالجة → ربط الإجراءات التصحيحية من قبل قائد البرنامج',
         N'IN_PROGRESS means implementation is live. CAPA program '
         + N'lead links any CAPAs that came out of the parent risk '
         + N'to this treatment so progress is visible in both '
         + N'places (treatment dashboard + CAPA register).',
         N'بدء تنفيذ المعالجة يستدعي ربط الإجراءات التصحيحية الناتجة عن المخاطرة الأم بالخطة — '
         + N'يصل قائد برنامج الإجراءات التصحيحية ليجعل التقدم ظاهراً في كلا اللوحتين.',
         N'TREATMENT_STARTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["CAPA_PROGRAM_LEAD","RISK_OWNER"],"template":"TREATMENT_STARTED"}',
         5, N'CUSTOM');
END

-- AR-105 ── Treatment COMPLETED → audit committee milestone + posture refresh
-- Completion realises the residual-risk reduction the plan
-- committed to. Audit committee tracks treatment completion as an
-- ERM KPI (% of approved treatments completed within plan
-- window); compliance posture refresh is hinted because the
-- residual on the parent risk likely just dropped.
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-105')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-105',
         N'Treatment COMPLETED → audit committee milestone + posture hint',
         N'إتمام المعالجة → معلم للجنة التدقيق وتنبيه لتحديث المؤشرات',
         N'Completion realises the residual-risk reduction the '
         + N'plan committed to. Audit committee tracks treatment-'
         + N'completion as an ERM KPI; compliance posture is hinted '
         + N'because the residual on the parent risk likely dropped.',
         N'إتمام المعالجة يحقق خفض المخاطرة المتبقية الموعود به — '
         + N'يصل معلم للجنة التدقيق ومسؤول الالتزام لمراجعة المؤشرات.',
         N'TREATMENT_COMPLETED',
         N'{}',
         N'{"type":"NOTIFY","roles":["AUDIT_COMMITTEE","COMPLIANCE_LEAD","RISK_OWNER"],"template":"TREATMENT_COMPLETED"}',
         5, N'CUSTOM');
END
GO

PRINT 'V147 seeded 5 treatment-plan automation rules (AR-101..AR-105).';
GO
