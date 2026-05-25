-- V207: F8 automation rules for Sharia Governance & Audit events.
--
-- Sprint 20 PR #2. Pairs with PR #260 (backend module) by seeding 10
-- rules consuming SHARIA_* events emitted by ShariaService.
--
-- SAMA's Implementing Regulation on Sharia Governance (Aug 2020) plus
-- AAOIFI Governance Standards GSIFI 1-5 plus IFSB-10 set the operational
-- reality these rules harden into the platform:
--
--   * SSB independence: every scholar refreshes a conflict-of-interest
--     declaration annually (SAMA §3.5) and serves a renewable 3-year
--     term (SAMA §3.4). The platform stores both, and the SSB Chair
--     sees a refresh cadence on the dashboard.
--   * SSB cadence: at least 4 meetings/year (SAMA §3.6). The platform
--     tracks attended_ytd on every scholar.
--   * Internal Sharia Audit findings: HIGH severity must go to the
--     Board Audit Committee within 30 days (SAMA §5.7).
--   * Sharia non-compliance income (SNCI): per SAMA §6.4 + AAOIFI FAS 1,
--     SNCI must be purified (donated to charity) within 90 days of
--     detection. The platform arms the 90d countdown on DETECTED.
--   * Material SNCI (>500k SAR) goes to the Board Risk Committee
--     in the next regular meeting — SAMA §6.5.
--   * No Islamic-banking product may go live without APPROVED_FINAL
--     SSB resolution (SAMA §4.2 + AAOIFI GSIFI 3). Launch fires an
--     evidence record so the next external Sharia audit has a clean
--     audit trail.
--   * The Annual Sharia Compliance Report is due to the Board and SAMA
--     by end of Q1 each year (SAMA §7.2).
--
-- AR-251..AR-260 — service in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ─── AR-251 ─ Resolution submitted → notify Sharia Officer + SSB Secretary ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-251')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-251',
         N'SSB resolution submitted → notify Sharia Officer + SSB Secretary + Compliance',
         N'تقديم قرار للهيئة الشرعية → إشعار مسؤول الشريعة وأمين سرّ الهيئة والالتزام',
         N'A new draft fatwa is the start of the SSB review pipeline. The '
         + N'Sharia Officer queues it for the next SSB meeting, the SSB '
         + N'Secretary adds it to the meeting agenda, and Compliance '
         + N'cross-checks the subject area against currently active '
         + N'resolutions in case a supersession is intended.',
         N'تقديم مسوّدة فتوى جديدة هو بداية مسار مراجعة الهيئة. مسؤول الشريعة '
         + N'يدرجها في جدول أعمال الاجتماع القادم، وأمين السرّ يضيفها للأجندة، '
         + N'والالتزام يتحقّق من تقاطعها مع القرارات النشطة في حال نية '
         + N'استبدالها.',
         N'SHARIA_RESOLUTION_SUBMITTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["SHARIA_OFFICER","SHARIA_BOARD_SECRETARY","COMPLIANCE_OFFICER"],"template":"SHARIA_RES_QUEUED"}',
         2, N'CUSTOM');
END

-- ─── AR-252 ─ Resolution rejected → notify Sharia Officer + originator ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-252')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-252',
         N'SSB resolution rejected → notify Sharia Officer + originator + open revision CAPA',
         N'رفض قرار الهيئة الشرعية → إشعار مسؤول الشريعة وصاحب الطلب + فتح إجراء تصحيحي للمراجعة',
         N'A rejected fatwa needs structured re-work — not informal back-'
         + N'and-forth between business and the SSB. The CAPA captures the '
         + N'rejection reason, the revision plan, and the target re-'
         + N'submission date. If the underlying product was already in '
         + N'APPROVED_PROVISIONAL, the rejection blocks the path to '
         + N'APPROVED_FINAL and the product effectively goes back into '
         + N'SUBMITTED.',
         N'الفتوى المرفوضة تستلزم إعادة عمل منظّمة — لا تبادل غير رسمي بين '
         + N'العمل والهيئة. يلتقط الإجراء التصحيحي سبب الرفض وخطّة المراجعة '
         + N'وتاريخ إعادة التقديم. لو كان المنتج المرتبط في حالة موافقة '
         + N'مبدئية، فالرفض يعيقه عن الموافقة النهائية ويعود فعلياً لحالة '
         + N'التقديم.',
         N'SHARIA_RESOLUTION_REJECTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["SHARIA_OFFICER","COMPLIANCE_OFFICER"],"andCreate":{"entity":"CAPA","title":"SSB resolution rejected — revision plan","ownerRole":"SHARIA_OFFICER","priority":"MEDIUM","sourceModule":"SHARIA"}}',
         2, N'CUSTOM');
END

-- ─── AR-253 ─ HIGH-severity audit finding → 30d Board Audit Committee ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-253')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-253',
         N'HIGH Sharia audit finding → escalate Audit Committee Chair + CRO + 30-day SLA',
         N'نتيجة مراجعة شرعية عالية الخطورة → تصعيد لرئيس لجنة المراجعة ومدير المخاطر + مهلة 30 يوماً',
         N'SAMA §5.7 mandates HIGH-severity Internal Sharia Audit findings '
         + N'be escalated to the Board Audit Committee within 30 days of '
         + N'being raised. The platform pages the Chief Internal Auditor, '
         + N'the Audit Committee Chair, CRO, and Sharia Officer the moment '
         + N'a HIGH finding is raised, and arms a 30-day SLA reminder.',
         N'تستوجب §5.7 من تنظيم ساما تصعيد نتائج المراجعة الشرعية الداخلية '
         + N'عالية الخطورة إلى لجنة المراجعة المنبثقة عن المجلس خلال 30 يوماً '
         + N'من رفعها. تنبّه المنصّة فوراً المراجع الداخلي الأول ورئيس لجنة '
         + N'المراجعة ومدير المخاطر ومسؤول الشريعة، وتُفعّل عدّاد 30 يوماً.',
         N'SHARIA_AUDIT_FINDING_RAISED',
         N'{"severity":{"eq":"HIGH"}}',
         N'{"type":"ESCALATE","roles":["INTERNAL_AUDITOR","AUDIT_COMMITTEE_CHAIR","RISK_MANAGER","SHARIA_OFFICER"],"template":"SHARIA_HIGH_FINDING","reminderAt":"NOW_PLUS_30D"}',
         1, N'CUSTOM');
END

-- ─── AR-254 ─ NC event detected → DPO + CRO + Critical CAPA (90d purification) ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-254')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-254',
         N'Sharia non-compliance detected → escalate CRO + Sharia Officer + Compliance + CRITICAL CAPA (90-day SLA)',
         N'رصد مخالفة شرعية → تصعيد لمدير المخاطر ومسؤول الشريعة والالتزام + إجراء تصحيحي حرج (مهلة 90 يوماً)',
         N'SAMA §6.4 + AAOIFI FAS 1 require SNCI (Sharia non-compliance '
         + N'income) to be purified (donated to a charity approved by '
         + N'the SSB) within 90 days of detection. The CRITICAL CAPA '
         + N'arms the 90-day SLA and links to the NC event so the '
         + N'Compliance + Finance teams have a single record of the '
         + N'purification destination, the charity receipt, and the '
         + N'financial-statement footnote disclosure.',
         N'تستوجب §6.4 من ساما ومعيار AAOIFI FAS 1 تطهير الدخل غير الشرعي '
         + N'(تبرّعه لجهة خيرية تعتمدها الهيئة) خلال 90 يوماً من الرصد. '
         + N'الإجراء التصحيحي الحرج يفعّل عدّاد 90 يوماً ويربط بحادثة المخالفة '
         + N'ليتوفّر لفريقَي الالتزام والمالية سجل موحّد لجهة التطهير وإيصال '
         + N'التبرّع والإفصاح في الإيضاحات المرفقة بالقوائم المالية.',
         N'SHARIA_NC_EVENT_DETECTED',
         N'{}',
         N'{"type":"ESCALATE","roles":["RISK_MANAGER","SHARIA_OFFICER","COMPLIANCE_OFFICER","CFO"],"andCreate":{"entity":"CAPA","title":"SNCI purification — 90-day SAMA SLA","ownerRole":"SHARIA_OFFICER","priority":"CRITICAL","sourceModule":"SHARIA"},"reminderAt":"NOW_PLUS_90D"}',
         1, N'CUSTOM');
END

-- ─── AR-255 ─ Material SNCI (>500k SAR) → Board Risk Committee ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-255')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-255',
         N'Material SNCI (>500k SAR) → escalate Board Risk Committee Chair + external Sharia auditor pre-brief',
         N'دخل غير شرعي جوهري (>500 ألف ر.س) → تصعيد لرئيس لجنة المخاطر المنبثقة عن المجلس + إحاطة مسبقة للمراجع الشرعي الخارجي',
         N'A material SNCI event (over 500k SAR) is a Board-level matter '
         + N'per SAMA §6.5. The Board Risk Committee Chair gets a same-day '
         + N'brief, and the external Sharia auditor (the bank''s independent '
         + N'Sharia opinion provider) is pre-briefed so they can flag '
         + N'whether their annual opinion may now carry a qualification.',
         N'تعتبر مخالفة شرعية جوهرية (>500 ألف ر.س) شأناً مجلسيّاً وفق §6.5 '
         + N'من ساما. يستلم رئيس لجنة المخاطر إحاطة في نفس اليوم، ويُحاط '
         + N'المراجع الشرعي الخارجي مسبقاً ليُشير إلى احتمال أن يحمل رأيه '
         + N'السنوي تحفّظاً.',
         N'SHARIA_NC_EVENT_DETECTED',
         N'{"snciAmountSar":{"gt":500000}}',
         N'{"type":"ESCALATE","roles":["BOARD_RISK_CHAIR","RISK_MANAGER","SHARIA_BOARD_CHAIR","EXTERNAL_SHARIA_AUDITOR","CFO"],"template":"SHARIA_MATERIAL_SNCI","reminderAt":"NOW_PLUS_1D"}',
         1, N'CUSTOM');
END

-- ─── AR-256 ─ Product submitted → notify Sharia Officer + queue for SSB ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-256')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-256',
         N'Islamic product submitted → notify Sharia Officer + queue for SSB review',
         N'تقديم منتج إسلامي → إشعار مسؤول الشريعة وإدراجه ضمن جدول مراجعة الهيئة',
         N'A new Islamic-banking product entering the pipeline kicks off a '
         + N'multi-week SSB review cadence. The Sharia Officer prepares the '
         + N'product-structure paper, the SSB Secretary slots it into a '
         + N'meeting, the Compliance Officer prepares the SAMA new-product '
         + N'notification dossier. The product cannot move past '
         + N'APPROVED_FINAL without a linked SSB resolution.',
         N'دخول منتج مصرفي إسلامي جديد يُفعّل دورة مراجعة الهيئة لعدّة أسابيع. '
         + N'مسؤول الشريعة يجهّز ورقة هيكلة المنتج، أمين السرّ يدرجه في '
         + N'الاجتماع، الالتزام يجهّز ملف إبلاغ ساما بالمنتج الجديد. لا يمكن '
         + N'للمنتج تجاوز حالة الموافقة النهائية بدون قرار شرعي مرتبط.',
         N'SHARIA_PRODUCT_SUBMITTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["SHARIA_OFFICER","SHARIA_BOARD_SECRETARY","COMPLIANCE_OFFICER","PRODUCT_OWNER"],"template":"SHARIA_PRODUCT_QUEUED"}',
         2, N'CUSTOM');
END

-- ─── AR-257 ─ Product launched → evidence vault audit trail ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-257')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-257',
         N'Islamic product launched → cross-log to evidence vault (linked SSB resolution audit trail)',
         N'إطلاق منتج إسلامي → إيداع في خزانة الأدلّة (سجل القرار الشرعي المرتبط)',
         N'Every launched Islamic product needs a clean evidence trail: '
         + N'the SSB resolution that approved it, the date of approval, '
         + N'the scholars who signed, the linked product-structure paper. '
         + N'This record is the first thing the external Sharia auditor + '
         + N'the SAMA on-site inspection team request. The evidence-vault '
         + N'cross-log standardises retention (7 years) and access control.',
         N'كل منتج إسلامي مطلق يحتاج إلى سجل أدلّة نظيف: قرار الهيئة المعتمد '
         + N'وتاريخ الاعتماد والعلماء الموقّعين وورقة هيكلة المنتج المرتبطة. '
         + N'هذا السجل أوّل ما يطلبه المراجع الشرعي الخارجي وفريق التفتيش '
         + N'الميداني التابع لساما. الإيداع في الخزانة يوحّد الاحتفاظ '
         + N'(7 سنوات) وضوابط الوصول.',
         N'SHARIA_PRODUCT_LAUNCHED',
         N'{}',
         N'{"type":"CREATE_RECORD","entity":"EVIDENCE_ITEM","title":"Islamic product launch — SSB approval evidence","ownerRole":"SHARIA_OFFICER","retentionYears":7,"sourceModule":"SHARIA"}',
         3, N'CUSTOM');
END

-- ─── AR-258 ─ Product rejected → product owner + de-list marketing ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-258')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-258',
         N'Islamic product rejected → notify product owner + Marketing + remove from launch calendar',
         N'رفض منتج إسلامي → إشعار صاحب المنتج والتسويق + شطبه من تقويم الإطلاق',
         N'A rejected product needs an immediate co-ordination across '
         + N'Product (re-design the structure), Marketing (pull launch '
         + N'campaign assets), and Operations (prevent any premature '
         + N'enablement in core banking). The Sharia Officer documents '
         + N'the rejection rationale for the annual Sharia compliance '
         + N'report.',
         N'المنتج المرفوض يستلزم تنسيقاً فورياً بين قسم المنتج (إعادة هيكلة) '
         + N'والتسويق (سحب أصول حملة الإطلاق) والعمليات (منع أي تفعيل مبكّر في '
         + N'النظام الجوهري). مسؤول الشريعة يوثّق مسوّغات الرفض في تقرير '
         + N'الالتزام الشرعي السنوي.',
         N'SHARIA_PRODUCT_REJECTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["SHARIA_OFFICER","PRODUCT_OWNER","MARKETING_OFFICER","OPERATIONS_OFFICER"],"template":"SHARIA_PRODUCT_REJECTED"}',
         2, N'CUSTOM');
END

-- ─── AR-259 ─ Scholar COI declared → evidence vault (annual independence proof) ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-259')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-259',
         N'SSB scholar COI declaration → cross-log to evidence vault (annual independence proof)',
         N'إقرار تعارض المصالح لعالم الهيئة → إيداع في خزانة الأدلّة (دليل الاستقلالية السنوي)',
         N'SAMA §3.5 + AAOIFI GSIFI 1 require annual COI declarations for '
         + N'every SSB scholar. The evidence vault keeps a 7-year audit '
         + N'trail of every declaration. The external Sharia auditor uses '
         + N'this to opine on SSB independence in the annual Sharia '
         + N'compliance report.',
         N'تستوجب §3.5 من ساما ومعيار AAOIFI GSIFI 1 إقراراً سنوياً لتعارض '
         + N'المصالح لكل عالم في الهيئة. تحتفظ خزانة الأدلّة بسجل سبع سنوات '
         + N'لكل إقرار. يعتمد المراجع الشرعي الخارجي على هذا السجل في إصدار '
         + N'رأيه السنوي حول استقلالية الهيئة.',
         N'SHARIA_SCHOLAR_COI_DECLARED',
         N'{}',
         N'{"type":"CREATE_RECORD","entity":"EVIDENCE_ITEM","title":"SSB scholar COI declaration — annual independence proof","ownerRole":"SHARIA_OFFICER","retentionYears":7,"sourceModule":"SHARIA"}',
         3, N'CUSTOM');
END

-- ─── AR-260 ─ Scholar term expiring → T-90/T-30 Board renewal ladder ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-260')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-260',
         N'SSB scholar term expiring → T-90/T-30 reminder ladder to Board Secretary + SSB Chair',
         N'انتهاء فترة عضوية عالم الهيئة → سلسلة تذكيرات 90 و30 يوماً لأمين سرّ المجلس ورئيس الهيئة',
         N'SAMA §3.4 sets SSB scholar terms at 3 years, renewable. The '
         + N'Board approves renewal/replacement. A T-90 reminder triggers '
         + N'the Nominations Committee to start searching alternates if '
         + N'needed; T-30 prepares the Board paper. The platform also '
         + N'monitors the scholar''s YTD meeting attendance — if it falls '
         + N'below the SAMA §3.6 minimum (4/year), the rule fires '
         + N'BOARD_MEETING_SHORTFALL on the same ladder.',
         N'تحدّد §3.4 من ساما فترة عضوية الهيئة بـ3 سنوات قابلة للتجديد، ويُجدّد '
         + N'المجلس. يبدأ تذكير 90 يوماً عمل لجنة الترشيحات إن لزم البحث عن '
         + N'بدلاء؛ تذكير 30 يوماً يُجهّز ورقة المجلس. تراقب المنصّة أيضاً عدد '
         + N'الاجتماعات المحضورة لهذه السنة — لو انخفض دون الحد الأدنى لـ§3.6 '
         + N'(4/سنة)، يُطلق تنبيه قصور الحضور على السلسلة نفسها.',
         N'SHARIA_SCHOLAR_TERM_EXPIRING',
         N'{}',
         N'{"type":"NOTIFY","roles":["BOARD_SECRETARY","SHARIA_BOARD_CHAIR","SHARIA_OFFICER","NOMINATIONS_CHAIR"],"template":"SHARIA_TERM_EXPIRING","reminderLadder":"T-90,T-30"}',
         2, N'CUSTOM');
END
GO
