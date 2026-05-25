-- V208: F8 automation rules for Outsourcing & Cloud Governance events.
--
-- Sprint 30 PR #2. Pairs with PR #265 (backend) by seeding 10 rules
-- consuming OUTSOURCING_* and CLOUD_SERVICE_* events emitted by
-- OutsourcingService.
--
-- Regulatory anchors:
--   * SAMA Outsourcing Regulation §3 (pre-outsourcing risk assessment),
--     §4 (material-outsourcing notification within 60 days of Board
--     approval), §5 (non-objection request for core-activity outsourcing),
--     §6 (ongoing oversight cadence), §7 (exit and contingency planning),
--     §8 (cross-border outsourcing controls).
--   * SAMA Cloud Computing Regulatory Framework v1.0 — T3/T4 data must
--     reside IN_KINGDOM; T2 may be IN_KINGDOM or GCC; T1 may be GLOBAL.
--   * NCA ECC-2 (2023) §2-1-2 third-party security; §3-1-2 cloud
--     security; §4-1-2 data residency.
--
-- AR-261..AR-270 — service in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ─── AR-261 ─ Arrangement registered → notify Risk + Compliance + Procurement ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-261')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-261',
         N'Outsourcing arrangement registered → notify Risk + Compliance + Procurement',
         N'تسجيل ترتيب إسناد جديد → إشعار المخاطر والالتزام والمشتريات',
         N'A newly proposed outsourcing arrangement kicks off the SAMA §3 '
         + N'pre-outsourcing assessment pipeline. Risk drafts the risk '
         + N'assessment, Compliance scopes regulatory implications, and '
         + N'Procurement begins commercial diligence in parallel.',
         N'تسجيل ترتيب إسناد مقترح يطلق مسار التقييم قبل الإسناد وفق ساما §٣. '
         + N'تعدّ المخاطر تقييم المخاطر، ويحدّد الالتزام التبعات الرقابية، '
         + N'وتبدأ المشتريات العناية التجارية بالتوازي.',
         N'OUTSOURCING_ARRANGEMENT_REGISTERED',
         N'{}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","COMPLIANCE_OFFICER","PROCUREMENT_OFFICER"],"template":"OUTSOURCING_KICKOFF"}',
         2, N'CUSTOM');
END

-- ─── AR-262 ─ Material arrangement Board-approved → 60d SAMA notification timer ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-262')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-262',
         N'Material outsourcing Board-approved → SAMA notification within 60 days',
         N'موافقة المجلس على إسناد جوهري → إخطار ساما خلال 60 يوماً',
         N'SAMA Outsourcing Regulation §4 requires material outsourcing '
         + N'arrangements be notified to SAMA within 60 calendar days of '
         + N'Board approval. The platform fires a T-30 / T-7 / T-0 reminder '
         + N'ladder so the notification letter is not late.',
         N'تستوجب §٤ من تنظيم الإسناد لساما إخطار المؤسسة بترتيبات الإسناد '
         + N'الجوهرية خلال ٦٠ يوماً تقويمياً من موافقة المجلس. تُفعّل المنصّة '
         + N'سلّم تذكير T-30 / T-7 / T-0 لضمان عدم تأخّر خطاب الإخطار.',
         N'OUTSOURCING_BOARD_APPROVED',
         N'{"materiality":{"eq":"MATERIAL"}}',
         N'{"type":"ESCALATE","roles":["COMPLIANCE_OFFICER","RISK_MANAGER","CRO"],"template":"SAMA_60D_NOTIFY","reminderLadder":["T-30D","T-7D","T-0"]}',
         1, N'CUSTOM');
END

-- ─── AR-263 ─ Core-activity LIVE without non-objection letter → block + escalate ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-263')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-263',
         N'Core-activity outsourcing LIVE → enforce SAMA non-objection letter',
         N'انطلاق إسناد نشاط جوهري → التأكد من خطاب عدم ممانعة ساما',
         N'SAMA Outsourcing Regulation §5 requires written non-objection '
         + N'from SAMA before any core-banking activity is outsourced. The '
         + N'service layer already blocks the transition; this rule fires '
         + N'an evidence-vault record at the moment of LIVE for the audit '
         + N'trail, and notifies CRO + Compliance.',
         N'تستوجب §٥ من تنظيم الإسناد لساما الحصول على عدم ممانعة كتابي قبل '
         + N'إسناد أي نشاط مصرفي جوهري. خدمة الخادم تمنع التحول دون ذلك؛ '
         + N'وهذه القاعدة تنشئ سجلاً في خزانة الأدلة عند الانطلاق وتبلّغ '
         + N'مدير المخاطر والالتزام.',
         N'OUTSOURCING_LIVE',
         N'{"isCoreActivity":{"eq":true}}',
         N'{"type":"CREATE_RECORD","entity":"EVIDENCE","retentionYears":7,"context":"SAMA_NON_OBJECTION","alsoNotify":["CRO","COMPLIANCE_OFFICER","INTERNAL_AUDITOR"]}',
         1, N'CUSTOM');
END

-- ─── AR-264 ─ Cloud service registered → notify CISO + DPO + CIO ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-264')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-264',
         N'Cloud service registered → notify CISO + DPO + CIO',
         N'تسجيل خدمة سحابية → إشعار مسؤول أمن المعلومات وحامي البيانات ورئيس تقنية المعلومات',
         N'Every new cloud subscription is a candidate for the SAMA Cloud '
         + N'Computing Framework classification gate. CISO checks security '
         + N'posture, DPO reviews data-processor obligations under PDPL '
         + N'Article 7, and CIO routes the service into the IT asset '
         + N'inventory.',
         N'كل اشتراك سحابي جديد مرشّح للمرور عبر بوابة تصنيف إطار الحوسبة '
         + N'السحابية ساما. يتحقّق مسؤول أمن المعلومات من الوضع الأمني، '
         + N'ويراجع حامي البيانات التزامات المعالج وفق المادة ٧ من نظام '
         + N'حماية البيانات، ويدرج رئيس تقنية المعلومات الخدمة في جرد '
         + N'الأصول التقنية.',
         N'CLOUD_SERVICE_REGISTERED',
         N'{}',
         N'{"type":"NOTIFY","roles":["CISO","DPO","CIO"],"template":"CLOUD_NEW"}',
         2, N'CUSTOM');
END

-- ─── AR-265 ─ Cloud residency violation → CRITICAL escalate + CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-265')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-265',
         N'Cloud data-residency violation → escalate CISO + DPO + CIO + CRITICAL CAPA',
         N'مخالفة مقرّ بيانات سحابية → تصعيد لمسؤول أمن المعلومات والحامي ورئيس التقنية + إجراء تصحيحي حرج',
         N'SAMA Cloud Framework §5 forbids T3/T4 data hosted outside the '
         + N'Kingdom and T2 outside the GCC. The platform auto-blocks '
         + N'classification-residency mismatches at the APPROVED gate, '
         + N'fires a CRITICAL CAPA to migrate the workload or reclassify, '
         + N'and notifies the CISO + DPO + CIO immediately.',
         N'تحظر §٥ من إطار الحوسبة السحابية ساما إيواء بيانات الفئة ٣/٤ '
         + N'خارج المملكة، والفئة ٢ خارج دول الخليج. تمنع المنصّة تلقائياً '
         + N'تعارض التصنيف مع مقرّ المعالجة عند بوابة الاعتماد، وتفتح '
         + N'إجراءً تصحيحياً حرجاً لترحيل الحمل أو إعادة تصنيفه، وتُخطر '
         + N'الأطراف الثلاثة فوراً.',
         N'CLOUD_SERVICE_RESIDENCY_VIOLATION',
         N'{}',
         N'{"type":"ESCALATE","roles":["CISO","DPO","CIO","COMPLIANCE_OFFICER"],"andCreate":{"entity":"CAPA","title":"Cloud residency violation — migrate or reclassify","priority":"CRITICAL","sourceModule":"OUTSOURCING"}}',
         1, N'CUSTOM');
END

-- ─── AR-266 ─ Ongoing review WITH_FINDINGS → NOTIFY + CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-266')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-266',
         N'Outsourcing review with findings → NOTIFY relationship owner + open CAPA',
         N'مراجعة إسناد بنتائج → إشعار مالك العلاقة + فتح إجراء تصحيحي',
         N'SAMA §6 ongoing-oversight reviews completed with findings need '
         + N'structured remediation. The CAPA captures the finding, owner, '
         + N'and target close date and feeds the next review cycle.',
         N'تتطلّب مراجعات الرقابة المستمرّة وفق §٦ من ساما والتي تنتهي بنتائج '
         + N'علاجاً منظّماً. يلتقط الإجراء التصحيحي النتيجة والمسؤول وموعد '
         + N'الإغلاق المستهدف، ويُغذّي دورة المراجعة التالية.',
         N'OUTSOURCING_REVIEW_WITH_FINDINGS',
         N'{}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","COMPLIANCE_OFFICER"],"andCreate":{"entity":"CAPA","title":"Outsourcing review findings — remediate","priority":"MEDIUM","sourceModule":"OUTSOURCING"}}',
         2, N'CUSTOM');
END

-- ─── AR-267 ─ Ongoing review ESCALATED → CRO + CISO + CRITICAL CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-267')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-267',
         N'Outsourcing review escalated → CRO + CISO + CRITICAL CAPA',
         N'تصعيد مراجعة إسناد → مدير المخاطر ومسؤول أمن المعلومات + إجراء حرج',
         N'When an ongoing review escalates beyond findings (e.g. data '
         + N'breach by provider, RTO violation, control-environment '
         + N'failure) the CRO and CISO must be paged within hours, not '
         + N'days. A CRITICAL CAPA tracks remediation alongside potential '
         + N'invocation of the exit plan.',
         N'حين تتجاوز المراجعة الدورية مجرّد نتائج (كخرق بيانات من المزوّد، '
         + N'مخالفة RTO، انهيار البيئة الرقابية) يجب إخطار مدير المخاطر ومسؤول '
         + N'أمن المعلومات خلال ساعات. ويتابع إجراء تصحيحي حرج العلاج مع '
         + N'احتمال تفعيل خطة الخروج.',
         N'OUTSOURCING_REVIEW_ESCALATED',
         N'{}',
         N'{"type":"ESCALATE","roles":["CRO","CISO","COMPLIANCE_OFFICER","INTERNAL_AUDITOR"],"andCreate":{"entity":"CAPA","title":"Outsourcing review escalated — assess exit","priority":"CRITICAL","sourceModule":"OUTSOURCING"}}',
         1, N'CUSTOM');
END

-- ─── AR-268 ─ Exit executed → CREATE_RECORD + notify Board ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-268')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-268',
         N'Outsourcing exit executed → evidence vault record (7yr) + Board notification',
         N'تنفيذ خروج من إسناد → سجل في خزانة الأدلة (٧ سنوات) + إخطار المجلس',
         N'SAMA §7 holds the bank responsible for clean and orderly exit '
         + N'from material outsourcing. The platform captures executed-on '
         + N'date, evidence of data return/destruction, and the substitute '
         + N'provider profile in the evidence vault with a 7-year retention '
         + N'tag for the next SAMA on-site visit.',
         N'تحمّل §٧ من تنظيم ساما البنك مسؤولية خروج نظيف ومنظّم من الإسناد '
         + N'الجوهري. تلتقط المنصّة تاريخ التنفيذ ودليل إعادة/إتلاف البيانات '
         + N'وملف المزوّد البديل في خزانة الأدلة بفترة احتفاظ ٧ سنوات تحضّر '
         + N'بها الزيارة الميدانية القادمة لساما.',
         N'OUTSOURCING_EXIT_EXECUTED',
         N'{}',
         N'{"type":"CREATE_RECORD","entity":"EVIDENCE","retentionYears":7,"context":"OUTSOURCING_EXIT","alsoNotify":["CRO","COMPLIANCE_OFFICER","BOARD_RISK_CHAIR"]}',
         1, N'CUSTOM');
END

-- ─── AR-269 ─ Review overdue → reminder ladder + escalate ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-269')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-269',
         N'Outsourcing review overdue → T+0 / T+30 / T+60 reminder ladder',
         N'تأخّر مراجعة إسناد → سلّم تذكير T+0 / T+30 / T+60',
         N'SAMA §6 ongoing-oversight reviews falling past their due date '
         + N'are a regulatory red flag. The platform reminds the '
         + N'relationship owner on the due day, escalates to the risk '
         + N'owner at +30 days, and pages the CRO at +60 days.',
         N'تأخّر مراجعات الرقابة المستمرة وفق §٦ من ساما عن موعدها يعدّ '
         + N'علامة حمراء رقابية. تذكّر المنصّة مالك العلاقة في اليوم '
         + N'المستحقّ، وتُصعّد لمالك المخاطر بعد ٣٠ يوماً، وتُخطر مدير '
         + N'المخاطر بعد ٦٠ يوماً.',
         N'OUTSOURCING_REVIEW_OVERDUE',
         N'{}',
         N'{"type":"ESCALATE","schedule":"DAILY_AT_08","reminderLadder":["T+0","T+30D","T+60D"],"roles":["RISK_MANAGER","CRO"]}',
         2, N'CUSTOM');
END

-- ─── AR-270 ─ Cross-border (jurisdiction != SAUDI) → DPO + PDPL Art.7 review ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-270')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-270',
         N'Cross-border outsourcing onboarded → DPO + PDPL Art.7 cross-border review',
         N'إسناد عبر الحدود → مراجعة الحامي والمادة ٧ من نظام حماية البيانات',
         N'SAMA §8 + PDPL Article 7 govern cross-border data flows under '
         + N'outsourcing. Any arrangement with provider_jurisdiction other '
         + N'than SAUDI triggers a DPO review for cross-border transfer '
         + N'authorisation, adequate-protection assessment, and (where '
         + N'applicable) Standard Contractual Clauses.',
         N'تنظّم §٨ من تنظيم ساما والمادة ٧ من نظام حماية البيانات تدفّقات '
         + N'البيانات عبر الحدود تحت الإسناد. أي ترتيب بمقرّ مزوّد خارج '
         + N'المملكة يُطلق مراجعة من الحامي لتفويض النقل العابر للحدود وتقييم '
         + N'مستوى الحماية وعند الاقتضاء بنود التعاقد المعيارية.',
         N'OUTSOURCING_ARRANGEMENT_REGISTERED',
         N'{"providerJurisdiction":{"ne":"SAUDI"}}',
         N'{"type":"NOTIFY","roles":["DPO","COMPLIANCE_OFFICER","CISO"],"template":"PDPL_ART7_CROSS_BORDER"}',
         2, N'CUSTOM');
END

GO
