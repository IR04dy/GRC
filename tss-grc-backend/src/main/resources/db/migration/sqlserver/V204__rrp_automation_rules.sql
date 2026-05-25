-- V204: F8 automation rules for Recovery & Resolution Plan events.
--
-- Sprint 17 PR #2. Pairs with PR #245 (backend module) by seeding 10
-- rules consuming RRP_* events emitted by RrpService.
--
-- SAMA D-SIB / Recovery Planning Rules (2022) reality:
--   * Indicator breaches are the operational early-warning signal.
--     The CRO must know within minutes; for CAPITAL/LIQUIDITY indicators
--     dropping to AMBER the Treasurer + CFO need same-hour notification.
--   * A move into RED band is board-reportable — the Board Risk
--     Committee Chair must see it in the next agenda, and a CRITICAL
--     CAPA captures the response plan.
--   * Worsening (GREEN→AMBER or AMBER→RED) escalates; recovery
--     (AMBER→GREEN) just records the timeline. AR-225..AR-227 filter
--     on the `worsening=true` payload field set by RrpService.
--   * Recovery option capacity drift is itself a watchable trend —
--     when the menu shrinks year-over-year SAMA notices.
--   * Board approval kicks off lodge prep; CRO + CFO + Compliance
--     need the artefact ready for SAMA RBS upload within 5 BD.
--
-- AR-221..AR-230 — service #25 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ─── AR-221 ─ Cycle opened → notify executive + Board team ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-221')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-221',
         N'RRP cycle opened → notify CRO + CFO + BCM Manager + Board Secretary',
         N'فتح دورة خطة الاستعادة → إشعار مدير المخاطر والمالي ومدير الاستمرارية وأمين السرّ',
         N'A new RRP cycle marks the start of a 4-6 month preparation cycle '
         + N'ending in Board approval and SAMA submission. CRO owns it, CFO '
         + N'co-owns for capital/liquidity content, BCM Manager owns critical '
         + N'functions inventory, Board Secretary schedules the Risk Committee '
         + N'review windows. All four need the workspace pinned from day one.',
         N'فتح دورة جديدة لخطة الاستعادة والحلّ يعني بداية مرحلة إعداد تمتدّ من 4 إلى 6 '
         + N'أشهر تنتهي باعتماد المجلس والتقديم لساما. مدير المخاطر يملكها، المدير المالي '
         + N'يشارك في محتوى رأس المال والسيولة، مدير الاستمرارية يملك جرد الوظائف الحيوية، '
         + N'أمين السرّ يحجز نوافذ مراجعة لجنة المخاطر. يحتاج الأربعة لتثبيت الورشة منذ اليوم الأول.',
         N'RRP_CYCLE_OPENED',
         N'{}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","CFO","BCM_MANAGER","BOARD_SECRETARY"],"template":"RRP_CYCLE_KICKOFF"}',
         1, N'CUSTOM');
END

-- ─── AR-222 ─ Cycle → BOARD_REVIEW → notify Board Risk + Audit Committees ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-222')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-222',
         N'RRP entering BOARD_REVIEW → notify Board Risk + Audit Committee members',
         N'انتقال الخطة إلى مراجعة المجلس → إشعار أعضاء لجنتَي المخاطر والمراجعة',
         N'BOARD_REVIEW is the formal gate before Board approval. The Risk '
         + N'Committee runs the deep-dive review; the Audit Committee gets the '
         + N'pack for independent challenge. Both committees need the artefact '
         + N'two weeks ahead of their scheduled meetings, so the notification '
         + N'fires the day BOARD_REVIEW is entered.',
         N'مراجعة المجلس هي البوّابة الرسمية قبل الاعتماد النهائي. لجنة المخاطر تقود المراجعة '
         + N'التفصيلية، ولجنة المراجعة تستلم الحزمة لتحدٍّ مستقلّ. تحتاج اللجنتان للوثيقة قبل '
         + N'أسبوعين من اجتماعهما، لذا يُطلق الإشعار فور دخول المرحلة.',
         N'RRP_CYCLE_TRANSITIONED',
         N'{"to":{"eq":"BOARD_REVIEW"}}',
         N'{"type":"NOTIFY","roles":["BOARD_RISK_COMMITTEE_MEMBER","AUDIT_COMMITTEE_MEMBER","BOARD_SECRETARY"],"template":"RRP_BOARD_REVIEW_PACK"}',
         1, N'CUSTOM');
END

-- ─── AR-223 ─ Cycle APPROVED → lodge-prep handoff ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-223')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-223',
         N'RRP APPROVED → 5-BD lodge-prep handoff to CRO + Compliance + Treasury',
         N'اعتماد الخطة → تسليم الإعداد للتقديم خلال 5 أيام عمل لمدير المخاطر والالتزام والخزانة',
         N'After Board approval the lodge artefact must hit SAMA RBS within 5 '
         + N'business days. CRO + Compliance Officer assemble the SAMA upload '
         + N'package; Treasury validates the latest liquidity / capital numbers '
         + N'one more time so they match the day-of-submission position.',
         N'بعد اعتماد المجلس، يجب أن تصل وثيقة التقديم إلى بوّابة RBS لدى ساما خلال 5 أيام عمل. '
         + N'مدير المخاطر ومسؤول الالتزام يجمّعان حزمة الرفع، والخزانة تتحقّق من أحدث أرقام '
         + N'السيولة ورأس المال مرّة أخرى لضمان مطابقتها لوضع يوم التقديم.',
         N'RRP_CYCLE_TRANSITIONED',
         N'{"to":{"eq":"APPROVED"}}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","COMPLIANCE_OFFICER","TREASURY"],"template":"RRP_LODGE_PREP","reminderAt":"NOW_PLUS_5BD"}',
         1, N'CUSTOM');
END

-- ─── AR-224 ─ Cycle SUBMITTED → evidence vault cross-log ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-224')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-224',
         N'RRP submitted → cross-log to compliance evidence vault (10-year retention)',
         N'تقديم الخطة → إيداع في خزانة أدلّة الالتزام (احتفاظ 10 سنوات)',
         N'The lodged RRP artefact must be cross-logged to the compliance '
         + N'evidence vault with 10-year retention so future SAMA on-site '
         + N'reviews can pull the lodged copy + ack reference instantly. RRP is '
         + N'a deep document — re-creating it under inspector pressure is not '
         + N'an option.',
         N'يجب إيداع وثيقة الخطة المُقدَّمة في خزانة أدلّة الالتزام لمدّة احتفاظ 10 سنوات حتى تتمكّن '
         + N'مراجعات ساما الميدانية المستقبلية من سحب النسخة المُقدَّمة وإقرار الاستلام فوراً. '
         + N'خطة الاستعادة وثيقة عميقة — إعادة إعدادها تحت ضغط المفتّش ليست خياراً.',
         N'RRP_CYCLE_SUBMITTED',
         N'{}',
         N'{"type":"CREATE_RECORD","entity":"EVIDENCE_ITEM","title":"RRP lodged copy + ack ref","ownerRole":"COMPLIANCE_OFFICER","retentionYears":10,"sourceModule":"RRP"}',
         2, N'CUSTOM');
END

-- ─── AR-225 ─ Indicator WORSENING → escalate CRO ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-225')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-225',
         N'Recovery indicator worsening → escalate to CRO within the hour',
         N'تدهور مؤشّر استعادة → تصعيد لمدير المخاطر خلال ساعة',
         N'When a recovery indicator crosses GREEN→AMBER or AMBER→RED the CRO '
         + N'must know within the hour. The trigger only fires on worsening — '
         + N'the worsening=true payload field is set by RrpService.bandWorseThan, '
         + N'so recovery transitions (AMBER→GREEN) record on the timeline but '
         + N'do not page anyone.',
         N'عندما يتجاوز مؤشّر استعادة الحدّ من الأخضر إلى الكهرماني أو من الكهرماني إلى الأحمر '
         + N'يجب أن يعلم مدير المخاطر خلال ساعة. يُطلق التنبيه فقط عند التدهور — تحدّد خدمة '
         + N'RrpService.bandWorseThan قيمة worsening=true في الحمولة، أمّا حالات التحسّن '
         + N'(كهرماني → أخضر) فتُسجَّل على الجدول الزمني دون إشعار.',
         N'RRP_INDICATOR_THRESHOLD_BREACHED',
         N'{"worsening":{"eq":"true"}}',
         N'{"type":"ESCALATE","roles":["RISK_MANAGER"],"template":"RRP_INDICATOR_WORSENING","reminderAt":"NOW_PLUS_1H"}',
         1, N'CUSTOM');
END

-- ─── AR-226 ─ Indicator → RED → Board notice + CRITICAL CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-226')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-226',
         N'Indicator entered RED → notify Board Risk Committee Chair + open CRITICAL CAPA',
         N'دخول المؤشّر النطاق الأحمر → إشعار رئيس لجنة المخاطر بالمجلس وفتح إجراء تصحيحي حرج',
         N'A RED band crossing is board-reportable. The Risk Committee Chair '
         + N'needs same-day notification with the indicator + the current value '
         + N'+ the recovery options that would be considered if the trend '
         + N'continues. A CRITICAL CAPA captures the response plan so the next '
         + N'Risk Committee meeting reviews specifics, not just the breach.',
         N'تجاوز النطاق الأحمر حدث قابل للتقرير للمجلس. يحتاج رئيس لجنة المخاطر إلى إشعار '
         + N'في اليوم نفسه مع اسم المؤشّر والقيمة الحالية وخيارات الاستعادة التي ستُعتبر إن '
         + N'استمرّ الاتجاه. يلتقط الإجراء التصحيحي الحرج خطّة الاستجابة حتى تراجع لجنة '
         + N'المخاطر القادمة التفاصيل، لا مجرّد التجاوز.',
         N'RRP_INDICATOR_THRESHOLD_BREACHED',
         N'{"bandAfter":{"eq":"RED"}}',
         N'{"type":"ESCALATE","roles":["BOARD_RISK_COMMITTEE_CHAIR","RISK_MANAGER","CFO"],"andCreate":{"entity":"CAPA","title":"Recovery indicator in RED band — response plan","ownerRole":"RISK_MANAGER","priority":"CRITICAL","sourceModule":"RRP"}}',
         1, N'CUSTOM');
END

-- ─── AR-227 ─ AMBER on CAPITAL or LIQUIDITY → CFO + Treasurer notify ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-227')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-227',
         N'Capital/Liquidity indicator hit AMBER → notify CFO + Treasurer same hour',
         N'دخول مؤشّر رأس المال أو السيولة النطاق الكهرماني → إشعار المدير المالي والخزانة في الساعة نفسها',
         N'CAPITAL and LIQUIDITY indicators are the most action-sensitive — a '
         + N'move to AMBER changes the daily Treasury playbook (different funding '
         + N'mix, different ALCO discussion, different rates committee posture). '
         + N'CFO + Treasurer get a same-hour ping so the next ALCO can react.',
         N'مؤشّرات رأس المال والسيولة أكثر المؤشّرات حساسيّة للإجراء — انتقالها إلى النطاق '
         + N'الكهرماني يغيّر دفتر عمل الخزانة اليومي (مزيج تمويل مختلف، نقاش مختلف في لجنة '
         + N'الأصول والخصوم، موقف مختلف للجنة الأسعار). يحصل المدير المالي ومدير الخزانة '
         + N'على تنبيه فوريّ ليتفاعل اجتماع ALCO القادم.',
         N'RRP_INDICATOR_THRESHOLD_BREACHED',
         N'{"bandAfter":{"eq":"AMBER"},"worsening":{"eq":"true"},"category":{"in":["CAPITAL","LIQUIDITY"]}}',
         N'{"type":"NOTIFY","roles":["CFO","TREASURY"],"template":"RRP_CAPLIQ_AMBER"}',
         2, N'CUSTOM');
END

-- ─── AR-228 ─ Option capacity erosion → CRO + CFO notice ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-228')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-228',
         N'Recovery option capacity changed → notify CRO + CFO (capacity drift watch)',
         N'تعديل طاقة خيار الاستعادة → إشعار مدير المخاطر والمالي (مراقبة انجراف الطاقة)',
         N'When a recovery option''s capacity is re-assessed downward (e.g. a '
         + N'subsidiary divestiture is no longer commercially viable, or an '
         + N'asset-sale market has thinned out), the total recovery capacity '
         + N'on the menu shrinks. SAMA tracks this drift across cycles; CRO + '
         + N'CFO need to know in real time so they can either reactivate the '
         + N'option or document a substitute.',
         N'عند إعادة تقييم طاقة خيار استعادة إلى الانخفاض (مثلاً تخارج شركة تابعة لم يعد '
         + N'تجارياً جدياً، أو ضحالة سوق بيع الأصول)، تتقلّص الطاقة الإجمالية على القائمة. تتابع '
         + N'ساما هذا الانجراف عبر الدورات؛ يحتاج مدير المخاطر والمالي للعلم فوراً ليُعيدا '
         + N'تنشيط الخيار أو يوثّقا بديلاً.',
         N'RRP_OPTION_CAPACITY_CHANGED',
         N'{}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","CFO"],"template":"RRP_OPTION_CAPACITY_DRIFT"}',
         3, N'CUSTOM');
END

-- ─── AR-229 ─ Critical function added → BCM Manager continuity assessment ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-229')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-229',
         N'Critical function added → notify BCM Manager for continuity-strategy review',
         N'إضافة وظيفة حيوية → إشعار مدير الاستمرارية لمراجعة استراتيجية الاستمرارية',
         N'A new critical function on the RRP inventory must be cross-linked '
         + N'to BCM''s BIA + continuity strategy. BCM Manager assesses whether '
         + N'the existing BCP/DRP already covers it with adequate MTPD and '
         + N'RTO, or whether a new continuity track is needed before the next '
         + N'exercise cycle.',
         N'يجب ربط أي وظيفة حيوية جديدة على جرد الخطة بتحليل تأثير الأعمال واستراتيجية '
         + N'الاستمرارية لدى مدير الاستمرارية. يقيّم المدير ما إذا كانت خطّة الاستمرارية '
         + N'الحالية تغطّيها بمدّة تعطّل قصوى مقبولة وزمن استرداد كافٍ، أم أنّ هناك حاجة '
         + N'لمسار استمرارية جديد قبل دورة التمرين القادمة.',
         N'RRP_CRITICAL_FUNCTION_ADDED',
         N'{}',
         N'{"type":"NOTIFY","roles":["BCM_MANAGER"],"template":"RRP_CRITICAL_FUNCTION_REVIEW"}',
         3, N'CUSTOM');
END

-- ─── AR-230 ─ Cycle CLOSED → IA + cross-cycle archive ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-230')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-230',
         N'RRP cycle closed → notify Internal Audit + cross-cycle archive',
         N'إغلاق دورة الخطة → إشعار المراجعة الداخلية وأرشيف المقارنة بين الدورات',
         N'On cycle closure the artefact + SAMA ack + Board resolution flow to '
         + N'Internal Audit (input for next year''s audit plan — recurring '
         + N'control gaps drive scope) and into the cross-cycle archive so '
         + N'capacity / indicator / critical-function drift can be compared '
         + N'year-over-year at next Board Risk Committee review.',
         N'عند إغلاق الدورة، تتدفّق الوثيقة وإقرار ساما وقرار المجلس إلى المراجعة الداخلية '
         + N'(كمدخل لخطة التدقيق العام القادم — فجوات الضوابط المتكرّرة تحدّد النطاق) '
         + N'وإلى الأرشيف المقارن بين الدورات حتى يمكن مقارنة انجراف الطاقة والمؤشرات '
         + N'والوظائف الحيوية سنوياً في مراجعة لجنة المخاطر القادمة.',
         N'RRP_CYCLE_CLOSED',
         N'{}',
         N'{"type":"NOTIFY","roles":["INTERNAL_AUDITOR","RISK_MANAGER","BOARD_SECRETARY"],"template":"RRP_CYCLE_CLOSED"}',
         3, N'CUSTOM');
END
GO
