-- V203: F8 automation rules for Mandatory Regulatory Reporting Calendar events.
--
-- Sprint 16 PR #2. Pairs with PR #239 (backend module) by seeding 10
-- rules consuming REG_REPORT_* events emitted by RegReportingService.
--
-- Saudi-banking statutory-returns reality:
--   * SAMA Daily Liquidity Return must be filed before 9:30 AM the next
--     business day; missing it is a Tier-1 supervisory observation that
--     surfaces in the next quarterly meeting with the bank.
--   * Monthly capital adequacy is the bank's most-watched return — CFO
--     personally signs off, Treasury prepares, Risk validates.
--   * CMA quarterly Directors' Dealings disclosure has zero tolerance for
--     late filing — every minute past the deadline appears on the Tadawul
--     news feed and triggers automatic CMA inquiry.
--   * ZATCA monthly VAT returns are externally hard-locked to the 15th of
--     the following month; auto-block payments past due date.
--   * Rejected filings (regulator pushed back) are operational red flags —
--     the same root cause repeats in 70% of cases unless a CAPA is opened.
--   * Formal exemptions are board-reportable events — Audit Committee must
--     see every one in their next pack.
--
-- AR-211..AR-220 — service #24 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ─── AR-211 ─ New obligation added → notify Compliance + owner + reviewer ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-211')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-211',
         N'Mandatory return added → notify Compliance Officer + owner role + reviewer role',
         N'إضافة تقرير مُلزم → إشعار مسؤول الالتزام والمالك والمراجِع',
         N'A new mandatory return on the catalog is operational debt the team has to '
         + N'absorb. The Compliance Officer needs visibility, the owner role (usually '
         + N'CFO / CRO / CISO / DPO) needs to confirm ownership, and the reviewer role '
         + N'needs to acknowledge the second-line responsibility before the first '
         + N'cycle is generated.',
         N'إضافة تقرير مُلزم جديد إلى الكتالوج يعني عبئاً تشغيليّاً جديداً يستوعبه '
         + N'الفريق. يحتاج مسؤول الالتزام للاطّلاع، والمالك (عادةً المدير المالي أو '
         + N'مدير المخاطر أو مدير أمن المعلومات أو مسؤول حماية البيانات) لتأكيد '
         + N'الملكية، والمراجِع لإقرار مسؤولية الخطّ الثاني قبل توليد أوّل دورة.',
         N'REG_REPORT_OBLIGATION_CREATED',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_OFFICER","OBLIGATION_OWNER","OBLIGATION_REVIEWER"],"template":"REG_OBLIGATION_CATALOGUED"}',
         2, N'CUSTOM');
END

-- ─── AR-212 ─ Filing scheduled → notify preparer + reminder ladder ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-212')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-212',
         N'Filing cycle scheduled → notify preparer + arm T-7/T-3/T-1 reminders',
         N'فتح دورة تقديم → إشعار المُعِدّ وتفعيل تذكيرات قبل 7 و3 ويوم واحد',
         N'Once a filing cycle opens, the preparer needs the workspace pinned and a '
         + N'reminder ladder armed at T-7, T-3, T-1 business days. The T-7 reminder '
         + N'is "start work"; T-3 is "review must begin"; T-1 is "approver must have '
         + N'signed off". Saudi workweek aware — Friday/Saturday are skipped.',
         N'بمجرّد فتح دورة التقديم، يحتاج المُعِدّ إلى تثبيت الورشة وتفعيل سلسلة '
         + N'تذكيرات قبل 7 و3 ويوم واحد من أيّام العمل. تذكير 7 أيّام = «ابدأ '
         + N'العمل»، تذكير 3 أيّام = «يجب أن تبدأ المراجعة»، تذكير يوم واحد = «يجب أن '
         + N'يكون المعتمِد قد وقّع». الأيّام السعودية فقط — تُتخطّى الجمعة والسبت.',
         N'REG_REPORT_FILING_SCHEDULED',
         N'{}',
         N'{"type":"NOTIFY","roles":["PREPARER"],"template":"REG_REPORT_CYCLE_OPENED","reminderLadder":"T-7,T-3,T-1"}',
         1, N'CUSTOM');
END

-- ─── AR-213 ─ State to IN_REVIEW → notify reviewer ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-213')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-213',
         N'Filing moved to IN_REVIEW → notify reviewer (second-line)',
         N'انتقال التقرير إلى قيد المراجعة → إشعار المراجِع (الخطّ الثاني)',
         N'Second-line review is the bottleneck on every regulatory return. The '
         + N'preparer hands off; the reviewer needs an immediate notification so the '
         + N'draft doesn''t sit in the queue overnight — especially for daily '
         + N'returns where the cutoff is 9:30 AM the next business day.',
         N'مراجعة الخطّ الثاني هي عنق الزجاجة في كل تقرير رقابي. يسلّم المُعِدّ المسوّدة، '
         + N'ويحتاج المراجِع إلى إشعار فوريّ حتى لا تبقى المسوّدة في الانتظار طوال الليل — '
         + N'خاصّةً للتقارير اليومية التي يكون موعد إغلاقها 9:30 صباح يوم العمل التالي.',
         N'REG_REPORT_FILING_STATE_CHANGED',
         N'{"to":{"eq":"IN_REVIEW"}}',
         N'{"type":"NOTIFY","roles":["REVIEWER"],"template":"REG_REPORT_READY_FOR_REVIEW"}',
         2, N'CUSTOM');
END

-- ─── AR-214 ─ State to APPROVED → notify approver + CFO ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-214')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-214',
         N'Filing APPROVED → notify accountable approver (final signoff)',
         N'اعتماد التقرير → إشعار المعتمِد المسؤول (التوقيع النهائي)',
         N'Approved means second-line review is complete; the named approver (CFO for '
         + N'capital/liquidity, CRO for risk returns, CISO for cyber returns, DPO for '
         + N'PDPL filings) must lock the signoff and the lodge will be triggered '
         + N'within their working window.',
         N'الاعتماد يعني اكتمال مراجعة الخطّ الثاني؛ يجب أن يقفل المعتمِد المُسمّى (المدير '
         + N'المالي لرأس المال والسيولة، مدير المخاطر لتقارير المخاطر، مدير أمن '
         + N'المعلومات للتقارير السيبرانية، مسؤول حماية البيانات لتقارير نظام حماية '
         + N'البيانات) التوقيعَ ضمن نافذة عمله.',
         N'REG_REPORT_FILING_STATE_CHANGED',
         N'{"to":{"eq":"APPROVED"}}',
         N'{"type":"NOTIFY","roles":["APPROVER","CFO"],"template":"REG_REPORT_APPROVED_PENDING_LODGE"}',
         1, N'CUSTOM');
END

-- ─── AR-215 ─ Late submission → CAPA on root cause + CRO escalation ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-215')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-215',
         N'Filing submitted LATE → open CAPA + escalate to CRO',
         N'تقديم متأخّر للتقرير → فتح إجراء تصحيحي وتصعيد لمدير المخاطر',
         N'Every late filing is a Tier-1 supervisory observation. The CRO must know '
         + N'within the hour and a CAPA must be open against the root cause before '
         + N'the regulator''s follow-up letter arrives. Common root causes: data '
         + N'extraction gap, ownership unclear, manual reconciliation bottleneck.',
         N'كل تقديم متأخّر هو ملاحظة رقابية من الفئة الأولى. يجب أن يعلم مدير المخاطر '
         + N'خلال ساعة وأن يُفتح إجراء تصحيحي على السبب الجذري قبل وصول خطاب المتابعة '
         + N'من الجهة الرقابية. الأسباب الجذرية الشائعة: فجوة في استخراج البيانات، '
         + N'غموض في الملكية، اختناق في المطابقة اليدوية.',
         N'REG_REPORT_FILING_SUBMITTED',
         N'{"late":{"eq":"true"}}',
         N'{"type":"CREATE_RECORD","entity":"CAPA","title":"Late regulatory filing — root cause","ownerRole":"COMPLIANCE_OFFICER","priority":"HIGH","sourceModule":"REGREPORTING","alsoNotify":["RISK_MANAGER","CFO"]}',
         1, N'CUSTOM');
END

-- ─── AR-216 ─ SAMA filing submitted → cross-log to evidence vault ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-216')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-216',
         N'SAMA filing submitted → cross-log to compliance evidence vault',
         N'تقديم تقرير ساما → إيداع في خزانة أدلّة الالتزام',
         N'Every SAMA submission must be cross-logged to the compliance evidence vault '
         + N'with the regulator ack reference, so future on-site inspections '
         + N'referencing the same return can pull the lodged copy instantly. Without '
         + N'this discipline the team re-creates filings from scratch under '
         + N'inspector pressure.',
         N'كل تقديم لساما يجب أن يُودَع في خزانة أدلّة الالتزام مع رقم إقرار الاستلام، '
         + N'حتى تتمكّن عمليات التفتيش الميداني المستقبلية التي تشير إلى نفس التقرير '
         + N'من سحب النسخة المُقدَّمة فوراً. بدون هذا الانضباط، يعيد الفريق إعداد '
         + N'التقارير من الصفر تحت ضغط المفتّش.',
         N'REG_REPORT_FILING_SUBMITTED',
         N'{"regulator":{"eq":"SAMA"}}',
         N'{"type":"CREATE_RECORD","entity":"EVIDENCE_ITEM","title":"SAMA filing — lodged copy","ownerRole":"COMPLIANCE_OFFICER","retentionYears":7,"sourceModule":"REGREPORTING"}',
         3, N'CUSTOM');
END

-- ─── AR-217 ─ Filing REJECTED → escalate to CRO + Board Secretary + CAPA ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-217')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-217',
         N'Filing REJECTED by regulator → escalate CRO + Board Sec + open CAPA',
         N'رفض التقرير من الجهة الرقابية → تصعيد لمدير المخاطر وأمين السرّ وإجراء تصحيحي',
         N'A regulator-rejected filing is a controls failure on multiple lines — '
         + N'preparation, review, and approval all let the error through. CRO needs '
         + N'to know within the hour, Board Secretary schedules a risk-committee '
         + N'discussion, and the CAPA captures the specific defect so the next cycle '
         + N'doesn''t replay it.',
         N'رفض التقرير من الجهة الرقابية هو إخفاق في الضوابط على خطوط متعدّدة — '
         + N'الإعداد والمراجعة والاعتماد سمحت بمرور الخطأ. يجب أن يعلم مدير المخاطر '
         + N'خلال ساعة، ويحجز أمين السرّ نقاشاً في لجنة المخاطر، ويلتقط الإجراء '
         + N'التصحيحي العيب المحدّد حتى لا تتكرّر الدورة القادمة بنفس الإخفاق.',
         N'REG_REPORT_FILING_REJECTED',
         N'{}',
         N'{"type":"ESCALATE","roles":["RISK_MANAGER","BOARD_SECRETARY","COMPLIANCE_OFFICER"],"andCreate":{"entity":"CAPA","title":"Regulator rejection — root cause + control fix","ownerRole":"COMPLIANCE_OFFICER","priority":"CRITICAL","sourceModule":"REGREPORTING"}}',
         1, N'CUSTOM');
END

-- ─── AR-218 ─ Filing EXEMPTED → notify Audit Committee + Compliance ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-218')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-218',
         N'Filing exempted (formal waiver) → notify Audit Committee + Compliance',
         N'إعفاء رسمي من التقرير → إشعار لجنة المراجعة ومسؤول الالتزام',
         N'Formal exemptions are board-reportable events. The Audit Committee must '
         + N'see every one in their next pack with the exemption reference and the '
         + N'underlying justification — exemptions tend to cluster (e.g. dormant '
         + N'subsidiary, decommissioned product line) and the committee needs the '
         + N'pattern visible.',
         N'الإعفاءات الرسمية هي أحداث قابلة للتقرير للمجلس. يجب أن ترى لجنة المراجعة '
         + N'كل إعفاء في حزمتها التالية مع مرجع الإعفاء والمبرّر الأساسي — تميل '
         + N'الإعفاءات إلى التجمّع (مثلاً شركة تابعة خاملة، خطّ منتج مُتقاعد) ولجنة '
         + N'المراجعة بحاجة إلى رؤية النمط.',
         N'REG_REPORT_FILING_EXEMPTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["AUDIT_COMMITTEE_MEMBER","COMPLIANCE_OFFICER","INTERNAL_AUDITOR"],"template":"REG_REPORT_EXEMPTION_GRANTED"}',
         2, N'CUSTOM');
END

-- ─── AR-219 ─ ZATCA filing scheduled → notify Tax team + Treasury ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-219')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-219',
         N'ZATCA filing scheduled → notify Tax team + Treasury (cash impact)',
         N'فتح دورة تقديم لزاتكا → إشعار فريق الضرائب والخزانة (أثر السيولة)',
         N'ZATCA monthly VAT + corporate income tax returns are externally hard-locked '
         + N'to the 15th of the following month. The Tax team prepares, but Treasury '
         + N'needs the heads-up for cash positioning — VAT payments alone can run '
         + N'into 9-figures and starve same-day liquidity if scheduled blind.',
         N'تقارير ضريبة القيمة المضافة وضريبة الدخل الشهرية لدى زاتكا مُقفلة بحدّ '
         + N'خارجي على 15 من الشهر التالي. يُعِدّها فريق الضرائب، لكن الخزانة تحتاج '
         + N'تنبيهاً مسبقاً لتمركز السيولة — مدفوعات ضريبة القيمة المضافة وحدها قد '
         + N'تصل لأرقام بتسعة خانات وتُجوّع سيولة اليوم نفسه إن جُدوِلت دون تخطيط.',
         N'REG_REPORT_FILING_SCHEDULED',
         N'{"regulator":{"eq":"ZATCA"}}',
         N'{"type":"NOTIFY","roles":["TAX_MANAGER","TREASURY","CFO"],"template":"REG_ZATCA_CYCLE_OPENED"}',
         2, N'CUSTOM');
END

-- ─── AR-220 ─ Filing CLOSED → archive to Internal Audit + compliance history ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-220')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-220',
         N'Filing CLOSED → log to Internal Audit + compliance history archive',
         N'إغلاق التقرير → تسجيل في المراجعة الداخلية وأرشيف تاريخ الالتزام',
         N'On closure the filing reference + lodged artefact reference + regulator '
         + N'ack flow into the Internal Audit annual-plan input (returns with '
         + N'recurring rejection patterns drive audit scope) and into the Compliance '
         + N'Officer''s history archive (so cross-cycle comparison is one click).',
         N'عند الإغلاق يتدفّق مرجع التقرير ومرجع الوثيقة المُقدَّمة وإقرار الاستلام إلى '
         + N'مدخلات الخطّة السنوية للمراجعة الداخلية (التقارير ذات أنماط الرفض المتكرّرة '
         + N'تحدّد نطاق التدقيق) وإلى أرشيف تاريخ مسؤول الالتزام (لتسهيل المقارنة بين '
         + N'الدورات بنقرة واحدة).',
         N'REG_REPORT_FILING_CLOSED',
         N'{}',
         N'{"type":"NOTIFY","roles":["INTERNAL_AUDITOR","COMPLIANCE_OFFICER"],"template":"REG_REPORT_FILING_CLOSED"}',
         3, N'CUSTOM');
END
GO
