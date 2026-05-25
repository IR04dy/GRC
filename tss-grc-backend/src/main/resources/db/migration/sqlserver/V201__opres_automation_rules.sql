-- V201: F8 automation rules for SAMA Operational Resilience self-assessment events.
--
-- Sprint 14 PR #10. Pairs with PR #229 (OpRes backend module) by
-- seeding 10 rules that consume the OPRES_* events emitted by
-- OpResService.
--
-- Saudi-bank operational-resilience reality:
--   * SAMA's Op-Res Framework (Sept 2024) requires an annual
--     self-assessment submitted to SAMA. The CRO and BCM Manager
--     run the workbench; the board signs it off; SAMA liaison
--     officer files the return. Each handoff is a rule.
--   * Maturity gaps ≥ 2 levels are not "monitoring" findings —
--     they are concrete plan items that must hit the CAPA register
--     with a named owner and a target close date.
--   * Governance + impact-tolerances pillar gaps are board-level
--     concerns; the others are typically executive-level.
--   * An overall avg current score < 3 ("Defined") is below the
--     SAMA expectation for a domestically systemic bank — that
--     submission needs executive review before it goes out the door.
--   * On approval the SAMA liaison team starts drafting the
--     regulator pack (the return is due within 30 days of board
--     sign-off per current SAMA guidance).
--
-- AR-191..AR-200 — service #22 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ─── AR-191 ─ Assessment opened → notify CRO + BCM Manager (kickoff) ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-191')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-191',
         N'Op-Res assessment opened → notify CRO + BCM Manager (annual cycle kickoff)',
         N'فتح تقييم الصمود التشغيلي → إشعار مدير المخاطر ومدير استمرارية الأعمال (انطلاق الدورة السنوية)',
         N'A new Op-Res self-assessment marks the start of the SAMA annual cycle. '
         + N'The CRO owns the submission; the BCM Manager runs the workbench day-to-day. '
         + N'Both need to be on alert from day one so the timeline does not slip.',
         N'يمثّل فتح تقييم جديد للصمود التشغيلي بداية الدورة السنوية لدى ساما. مدير المخاطر '
         + N'هو المسؤول عن التقديم، ومدير استمرارية الأعمال يدير ورشة العمل يومياً. '
         + N'يجب تنبيه الاثنين منذ اليوم الأول حتى لا يتأخر الجدول الزمني.',
         N'OPRES_ASSESSMENT_CREATED',
         N'{}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","BCM_MANAGER"],"template":"OPRES_CYCLE_KICKOFF"}',
         2, N'CUSTOM');
END

-- ─── AR-192 ─ Pillar maturity gap ≥ 2 levels → auto-CAPA task ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-192')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-192',
         N'Op-Res pillar maturity gap ≥ 2 levels → create CAPA task',
         N'فجوة نضج ركيزة الصمود التشغيلي بمستويين أو أكثر → إنشاء مهمة تصحيحية',
         N'A gap of 2 or more maturity levels between current and target is a concrete '
         + N'plan item, not a monitoring finding. It must land on the CAPA register with '
         + N'a named owner and a target close date so the next year''s assessment can '
         + N'demonstrate measurable improvement.',
         N'فجوة بمستويين أو أكثر بين النضج الحالي والمستهدف ليست مجرّد ملاحظة رصد، '
         + N'بل بند خطة محدّد. يجب إدراجها في سجل الإجراءات التصحيحية مع مسؤول '
         + N'مُسمّى وتاريخ إغلاق مستهدف ليتمكّن تقييم العام القادم من إثبات تحسّن قابل للقياس.',
         N'OPRES_PILLAR_GAP',
         N'{"gap":{"gte":2}}',
         N'{"type":"CREATE_RECORD","entity":"CAPA","title":"Op-Res pillar maturity gap","ownerRole":"BCM_MANAGER","priority":"HIGH","sourceModule":"OPRES"}',
         1, N'CUSTOM');
END

-- ─── AR-193 ─ Governance pillar gap → notify Board Secretary ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-193')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-193',
         N'Op-Res GOVERNANCE pillar gap → notify Board Secretary',
         N'فجوة في ركيزة الحوكمة للصمود التشغيلي → إشعار أمين سرّ المجلس',
         N'Governance pillar gaps are board-level concerns — they touch board ownership '
         + N'of operational resilience, accountable senior executive designation, and the '
         + N'three-lines model. The Board Secretary should see this before the annual '
         + N'risk committee paper is finalised.',
         N'فجوات ركيزة الحوكمة هي شؤون على مستوى المجلس — تمسّ مسؤولية المجلس عن '
         + N'الصمود التشغيلي، وتعيين تنفيذي أوّل مساءَل، ونموذج الخطوط الثلاثة. يجب '
         + N'أن يطّلع عليها أمين سرّ المجلس قبل إغلاق ورقة لجنة المخاطر السنوية.',
         N'OPRES_PILLAR_GAP',
         N'{"pillarCode":{"eq":"GOVERNANCE"}}',
         N'{"type":"NOTIFY","roles":["BOARD_SECRETARY","RISK_MANAGER"],"template":"OPRES_GOVERNANCE_GAP"}',
         2, N'CUSTOM');
END

-- ─── AR-194 ─ Impact-tolerances pillar gap → notify Risk Mgr + BCM ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-194')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-194',
         N'Op-Res TOLERANCES pillar gap → notify Risk Manager + BCM Manager',
         N'فجوة في ركيزة حدود التحمّل → إشعار مدير المخاطر ومدير استمرارية الأعمال',
         N'Impact-tolerances gaps are foundational — if tolerances are not quantified '
         + N'and approved by the board, no severe-but-plausible test has meaning. '
         + N'Both Risk and BCM need to coordinate on closing the gap before the next '
         + N'tolerance-validation exercise window.',
         N'فجوات حدود التحمّل أساسية — إذا لم تكن الحدود مُحدّدة بأرقام ومُعتمَدة '
         + N'من المجلس، فلا قيمة لأي اختبار سيناريو شديد محتمل. على مدير المخاطر '
         + N'ومدير استمرارية الأعمال التنسيق لإغلاق الفجوة قبل نافذة تمرين التحقّق التالية.',
         N'OPRES_PILLAR_GAP',
         N'{"pillarCode":{"eq":"TOLERANCES"}}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","BCM_MANAGER"],"template":"OPRES_TOLERANCES_GAP"}',
         2, N'CUSTOM');
END

-- ─── AR-195 ─ Mapping/testing pillar gap → schedule scenario exercise ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-195')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-195',
         N'Op-Res MAPPING_TESTING pillar gap → schedule severe-but-plausible scenario',
         N'فجوة في ركيزة الربط والاختبار → جدولة سيناريو شديد محتمل',
         N'A mapping/testing maturity gap closes only through actual exercise execution. '
         + N'Schedule a severe-but-plausible scenario covering the relevant Important '
         + N'Business Service so the next assessment can cite the exercise as evidence '
         + N'of practical capability — not just documented process.',
         N'لا تُغلَق فجوة نضج الربط والاختبار إلا بتنفيذ تمرين فعلي. اجدول سيناريو '
         + N'شديد محتمل يغطّي الخدمة التجارية المهمّة المعنية حتى يستشهد تقييم '
         + N'العام القادم بالتمرين كدليل على قدرة عملية حقيقية — لا مجرّد إجراء موثّق.',
         N'OPRES_PILLAR_GAP',
         N'{"pillarCode":{"eq":"MAPPING_TESTING"}}',
         N'{"type":"CREATE_RECORD","entity":"BCM_EXERCISE","title":"Op-Res mapping gap — severe-but-plausible scenario","ownerRole":"BCM_MANAGER","scenarioType":"SEVERE_BUT_PLAUSIBLE","sourceModule":"OPRES"}',
         2, N'CUSTOM');
END

-- ─── AR-196 ─ Assessment submitted → notify CRO + Board Secretary for sign-off ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-196')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-196',
         N'Op-Res assessment submitted → notify CRO + Board Secretary for sign-off',
         N'تقديم تقييم الصمود التشغيلي → إشعار مدير المخاطر وأمين سرّ المجلس للاعتماد',
         N'Submission means the BCM Manager has finished filling in all 5 pillar cells '
         + N'and is asking for board-level sign-off. The CRO reviews technical content; '
         + N'the Board Secretary schedules the risk committee slot to record formal '
         + N'approval against the SAMA filing deadline.',
         N'يعني التقديم أن مدير استمرارية الأعمال أكمل تعبئة جميع ركائز التقييم الخمس '
         + N'وأنه يطلب اعتماداً على مستوى المجلس. يراجع مدير المخاطر المحتوى الفنّي، '
         + N'بينما يحجز أمين سرّ المجلس جلسة لجنة المخاطر لتسجيل الموافقة الرسمية '
         + N'قبل الموعد التنظيمي لتقديم التقرير إلى ساما.',
         N'OPRES_ASSESSMENT_SUBMITTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","BOARD_SECRETARY"],"template":"OPRES_AWAITING_SIGNOFF"}',
         1, N'CUSTOM');
END

-- ─── AR-197 ─ Low overall maturity (< 3) on submission → exec review flag ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-197')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-197',
         N'Op-Res submission with avg current < 3 → executive review before filing',
         N'تقديم تقييم بمتوسط نضج حالي أقل من 3 → مراجعة تنفيذية قبل الإرسال',
         N'An average current score below "Defined" (level 3) is below SAMA expectation '
         + N'for a domestically systemic bank. The submission still goes — regulators '
         + N'expect honest self-assessment — but the executive committee should review '
         + N'the message and the management-action plan before the package leaves the '
         + N'building.',
         N'متوسط نضج حالي دون "محدّد" (المستوى 3) أدنى من توقّعات ساما لبنك ذي أهميّة '
         + N'محلّية. يبقى التقديم قائماً — تتوقّع الجهات الرقابية تقييماً ذاتياً صادقاً — '
         + N'لكن يجب أن تراجع اللجنة التنفيذية الرسالة وخطة الإجراءات الإدارية قبل '
         + N'مغادرة الحزمة المؤسسة.',
         N'OPRES_ASSESSMENT_SUBMITTED',
         N'{"avgCurrentScore":{"lt":3.0}}',
         N'{"type":"FLAG","reason":"Low overall maturity score — executive review required","priority":"HIGH","reviewerRoles":["CEO","RISK_MANAGER"]}',
         1, N'CUSTOM');
END

-- ─── AR-198 ─ Assessment approved → notify SAMA liaison for regulator filing ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-198')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-198',
         N'Op-Res assessment approved → notify SAMA Liaison Officer (regulator filing)',
         N'اعتماد تقييم الصمود التشغيلي → إشعار ضابط الارتباط مع ساما (تقديم لجهة الإشراف)',
         N'Board approval starts the regulator-filing clock. The SAMA Liaison Officer '
         + N'assembles the formal cover note, attaches the approved assessment, and '
         + N'transmits via SAMA''s designated channel within the cycle deadline.',
         N'يبدأ اعتماد المجلس الساعة لتقديم التقرير إلى الجهة الرقابية. يجمّع ضابط '
         + N'الارتباط مع ساما خطاب التغطية الرسمي، ويرفق التقييم المُعتمد، ويرسله عبر '
         + N'القناة المعتمدة لدى ساما قبل الموعد المحدّد للدورة.',
         N'OPRES_ASSESSMENT_APPROVED',
         N'{}',
         N'{"type":"NOTIFY","roles":["SAMA_LIAISON","COMPLIANCE_OFFICER"],"template":"OPRES_READY_TO_FILE"}',
         1, N'CUSTOM');
END

-- ─── AR-199 ─ Approved with high maturity (≥ 4 avg) → board pack success milestone ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-199')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-199',
         N'Op-Res approved with avg current ≥ 4 → board pack success milestone',
         N'اعتماد التقييم بمتوسط نضج ≥ 4 → إنجاز مرحلي لحزمة المجلس',
         N'An average current maturity at or above "Managed" (level 4) is a real '
         + N'organisational achievement worth surfacing to the board. Add a success '
         + N'milestone item to the next board pack so the resilience programme gets '
         + N'visible credit and the team is recognised.',
         N'متوسط نضج حالي عند "مُدار" (المستوى 4) أو أعلى يُعدّ إنجازاً مؤسسياً '
         + N'حقيقياً يستحقّ إبرازه للمجلس. أضِف بنداً للإنجاز ضمن حزمة المجلس القادمة '
         + N'حتى ينال برنامج الصمود التشغيلي اعترافاً ظاهراً والفريق تقديراً مستحقاً.',
         N'OPRES_ASSESSMENT_APPROVED',
         N'{"avgCurrentScore":{"gte":4.0}}',
         N'{"type":"CREATE_RECORD","entity":"BOARD_PACK_ITEM","title":"Op-Res maturity at Managed level — success milestone","section":"GRC_HIGHLIGHTS","sourceModule":"OPRES"}',
         3, N'CUSTOM');
END

-- ─── AR-200 ─ Assessment closed → archive evidence + lock cells ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-200')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-200',
         N'Op-Res assessment closed → archive evidence + notify Internal Audit',
         N'إغلاق تقييم الصمود التشغيلي → أرشفة الأدلّة وإشعار المراجعة الداخلية',
         N'Closure is terminal — the assessment record becomes the immutable annual '
         + N'submission. Internal Audit needs the archive reference for its own annual '
         + N'plan; the evidence trail must remain queryable for the SAMA on-site '
         + N'inspection cycle.',
         N'الإغلاق نهائي — يصبح سجل التقييم هو التقديم السنوي غير القابل للتعديل. '
         + N'تحتاج المراجعة الداخلية إلى مرجع الأرشيف لخطتها السنوية، ويجب أن يبقى '
         + N'مسار الأدلّة قابلاً للاستعلام لدورة التفتيش الميداني لدى ساما.',
         N'OPRES_ASSESSMENT_CLOSED',
         N'{}',
         N'{"type":"NOTIFY","roles":["INTERNAL_AUDITOR","COMPLIANCE_OFFICER"],"template":"OPRES_CYCLE_CLOSED"}',
         2, N'CUSTOM');
END
GO
