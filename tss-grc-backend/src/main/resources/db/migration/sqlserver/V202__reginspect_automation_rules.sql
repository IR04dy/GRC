-- V202: F8 automation rules for Regulatory Inspection Workspace events.
--
-- Sprint 15 PR #2. Pairs with PR #234 (backend module) by seeding 10
-- rules that consume the REG_* events emitted by RegInspectService.
--
-- Saudi-banking on-site inspection reality:
--   * SAMA notifies typically 2-4 weeks ahead → the CRO + Compliance
--     Officer + Board Secretary need eyes on the workspace from day
--     one so the entry-meeting readiness pack can be drafted.
--   * SAMA SLA on document requests is 3-5 business days. Slippage
--     past dueDate is escalated immediately to the Compliance Officer
--     and (>3 overdue) to the CEO + Audit Committee.
--   * NCA OTCC inspections specifically engage CISO + cyber team; CMA
--     listing audits engage the Investor Relations + Legal team.
--   * The exit meeting marks the start of the response-letter window;
--     drafting must begin within 48h.
--   * On closure the formal letter + archive reference flows to
--     Internal Audit (for annual plan input) + Compliance Officer
--     (for SAMA on-site inspection history file).
--
-- AR-201..AR-210 — service #23 in the F7/F8 parity arc.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ─── AR-201 ─ Inspection opened → notify CRO + Compliance + Board Sec ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-201')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-201',
         N'Regulatory inspection opened → notify CRO + Compliance Officer + Board Secretary',
         N'فتح ورشة تفتيش رقابي → إشعار مدير المخاطر ومسؤول الالتزام وأمين سرّ المجلس',
         N'A new inspection workspace marks the start of a 4-6 week intensive sprint. '
         + N'The CRO owns it, the Compliance Officer coordinates day-to-day, and the '
         + N'Board Secretary schedules the risk-committee briefing. All three need '
         + N'eyes on the workspace from day one so the entry-meeting readiness pack '
         + N'is drafted before the inspector arrives.',
         N'ورشة تفتيش جديدة تعني بداية مرحلة مكثّفة من 4 إلى 6 أسابيع. مدير المخاطر '
         + N'يملكها، ومسؤول الالتزام ينسّق العمل اليومي، وأمين سرّ المجلس يحجز إحاطة '
         + N'لجنة المخاطر. يجب أن يطّلع الثلاثة على الورشة منذ اليوم الأوّل لإعداد حزمة '
         + N'الاستعداد لاجتماع الافتتاح قبل وصول المفتّش.',
         N'REG_INSPECTION_CREATED',
         N'{}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","COMPLIANCE_OFFICER","BOARD_SECRETARY"],"template":"REG_INSPECTION_OPENED"}',
         1, N'CUSTOM');
END

-- ─── AR-202 ─ NCA OTCC inspection → notify CISO + Cyber team ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-202')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-202',
         N'NCA inspection opened → notify CISO + Cyber Incident Responder',
         N'فتح تفتيش من هيئة الأمن السيبراني → إشعار مدير أمن المعلومات وفريق الاستجابة',
         N'NCA OTCC inspections target the cyber posture specifically — the CISO needs '
         + N'to lead the response, with the cyber-incident team on standby to pull '
         + N'logs, attestations, and red-team evidence. Compliance still coordinates '
         + N'but the technical content comes from cyber.',
         N'تستهدف عمليات تفتيش هيئة الأمن السيبراني الوضع السيبراني تحديداً — يجب '
         + N'أن يقود مدير أمن المعلومات الاستجابة، مع جاهزية فريق الاستجابة لسحب '
         + N'السجلّات والشهادات وأدلّة فرق الاختبار. مسؤول الالتزام يبقى المنسّق لكنّ '
         + N'المحتوى الفنّي مصدره الفريق السيبراني.',
         N'REG_INSPECTION_CREATED',
         N'{"regulator":{"eq":"NCA"}}',
         N'{"type":"NOTIFY","roles":["CISO","CYBER_INCIDENT_RESPONDER"],"template":"REG_NCA_INSPECTION"}',
         1, N'CUSTOM');
END

-- ─── AR-203 ─ CMA listing inspection → notify Investor Relations + Legal ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-203')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-203',
         N'CMA inspection opened → notify Investor Relations + Legal Counsel',
         N'فتح تفتيش هيئة السوق المالية → إشعار علاقات المستثمرين والمستشار القانوني',
         N'CMA inspections relate to listing obligations, prospectus continuing '
         + N'disclosure, and shareholder communications. Investor Relations drives '
         + N'the content; Legal Counsel reviews every external response before it '
         + N'goes back to CMA.',
         N'تتعلّق عمليات تفتيش هيئة السوق المالية بالتزامات الإدراج، والإفصاحات '
         + N'المستمرّة في النشرة، والاتصالات مع المساهمين. علاقات المستثمرين تقود '
         + N'المحتوى، والمستشار القانوني يراجع كل ردّ خارجي قبل إعادته للهيئة.',
         N'REG_INSPECTION_CREATED',
         N'{"regulator":{"eq":"CMA"}}',
         N'{"type":"NOTIFY","roles":["INVESTOR_RELATIONS","LEGAL_COUNSEL"],"template":"REG_CMA_INSPECTION"}',
         1, N'CUSTOM');
END

-- ─── AR-204 ─ Entry meeting started → readiness pack notification ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-204')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-204',
         N'Inspection moved to ENTRY_MEETING → publish readiness pack notification',
         N'انتقال التفتيش إلى اجتماع الافتتاح → نشر إشعار حزمة الاستعداد',
         N'The entry meeting is when the engagement letter is formally received and '
         + N'scope is locked. The internal team needs the readiness pack visible on '
         + N'the workspace dashboard so document-request fulfilment starts cleanly.',
         N'اجتماع الافتتاح هو اللحظة التي يُستلم فيها خطاب التكليف الرسمي ويُغلق '
         + N'فيها النطاق. يحتاج الفريق الداخلي إلى ظهور حزمة الاستعداد على لوحة الورشة '
         + N'لينطلق تنفيذ طلبات الوثائق بسلاسة.',
         N'REG_INSPECTION_TRANSITIONED',
         N'{"to":{"eq":"ENTRY_MEETING"}}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_OFFICER","RISK_MANAGER"],"template":"REG_ENTRY_MEETING_READY"}',
         2, N'CUSTOM');
END

-- ─── AR-205 ─ Document request opened → notify assignee ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-205')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-205',
         N'Document request opened → notify assignee + 24h-reminder',
         N'فتح طلب وثائق → إشعار المُسنَد إليه مع تذكير قبل 24 ساعة',
         N'Document requests are the daily currency of an on-site inspection. The '
         + N'assignee needs an immediate notification and a 24h-before-due reminder; '
         + N'the regulator''s SLA is typically 3-5 business days and slippage is the '
         + N'first thing inspectors note in the exit-meeting summary.',
         N'طلبات الوثائق هي العملة اليومية للتفتيش الميداني. يحتاج المُسنَد إليه إلى '
         + N'إشعار فوري وتذكير قبل 24 ساعة من الاستحقاق؛ مدّة ساما المعتمدة عادةً '
         + N'3-5 أيام عمل، والتأخّر هو أوّل ما يسجّله المفتّشون في ملخّص اجتماع الإغلاق.',
         N'REG_DOCUMENT_REQUEST_OPENED',
         N'{}',
         N'{"type":"NOTIFY","roles":["ASSIGNEE"],"template":"REG_DR_OPENED","reminderAt":"DUE_MINUS_24H"}',
         1, N'CUSTOM');
END

-- ─── AR-206 ─ Document request submitted → confirm + log evidence ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-206')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-206',
         N'Document request submitted → confirm to coordinator + log evidence',
         N'تقديم طلب وثائق → تأكيد للمنسّق وتسجيل الدليل',
         N'Every submitted DR must be confirmed to the Compliance Officer (so the '
         + N'workspace dashboard stays accurate) and the evidence reference must '
         + N'be cross-logged into the evidence module so it''s recoverable for '
         + N'future SAMA on-site reviews referencing the same artefact.',
         N'يجب تأكيد كل طلب مُقدَّم لمسؤول الالتزام (للحفاظ على دقّة لوحة الورشة) '
         + N'وتسجيل مرجع الدليل في وحدة الأدلّة عبر الإشارة لتسهيل استرجاعه في '
         + N'مراجعات ساما المستقبلية التي قد تشير إلى نفس الوثيقة.',
         N'REG_DOCUMENT_REQUEST_SUBMITTED',
         N'{}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_OFFICER"],"template":"REG_DR_SUBMITTED"}',
         3, N'CUSTOM');
END

-- ─── AR-207 ─ Late DR submission (was overdue) → CAPA root cause ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-207')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-207',
         N'Late DR submission (was overdue) → open CAPA on root cause',
         N'تقديم متأخّر لطلب وثائق (كان متجاوزاً) → فتح إجراء تصحيحي على السبب الجذري',
         N'A document request that was already overdue when submitted is a signal of '
         + N'a real operational gap — usually data availability, ownership clarity, '
         + N'or evidence retention. Open a CAPA so the next inspection cycle doesn''t '
         + N'repeat the same slip.',
         N'الطلب الذي كان قد تجاوز موعد استحقاقه قبل تقديمه يشير إلى فجوة تشغيلية '
         + N'حقيقية — عادةً توافر البيانات، أو وضوح الملكية، أو الاحتفاظ بالأدلّة. '
         + N'افتح إجراءً تصحيحياً حتى لا تتكرّر الفجوة في دورة التفتيش القادمة.',
         N'REG_DOCUMENT_REQUEST_SUBMITTED',
         N'{"overdue":{"eq":"true"}}',
         N'{"type":"CREATE_RECORD","entity":"CAPA","title":"Late regulatory DR submission — root cause","ownerRole":"COMPLIANCE_OFFICER","priority":"MEDIUM","sourceModule":"REGINSPECT"}',
         2, N'CUSTOM');
END

-- ─── AR-208 ─ Exit meeting reached → 48h response-drafting kickoff ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-208')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-208',
         N'Inspection moved to EXIT_MEETING → 48h response-drafting kickoff',
         N'انتقال التفتيش إلى اجتماع الإغلاق → انطلاق صياغة الردّ خلال 48 ساعة',
         N'The exit meeting locks the provisional findings list. Management responses '
         + N'must start drafting within 48 hours — letting the response window open '
         + N'is the most common ops failure. Notify CRO + Compliance Officer with '
         + N'a 48h timer attached.',
         N'يُغلق اجتماع الإغلاق قائمة الملاحظات المبدئية. يجب أن تبدأ صياغة الردود '
         + N'الإدارية خلال 48 ساعة — ترك نافذة الردّ مفتوحة هو أكثر إخفاقات التشغيل '
         + N'شيوعاً. تنبيه مدير المخاطر ومسؤول الالتزام بمؤقّت 48 ساعة.',
         N'REG_INSPECTION_TRANSITIONED',
         N'{"to":{"eq":"EXIT_MEETING"}}',
         N'{"type":"NOTIFY","roles":["RISK_MANAGER","COMPLIANCE_OFFICER"],"template":"REG_EXIT_MEETING","reminderAt":"NOW_PLUS_48H"}',
         1, N'CUSTOM');
END

-- ─── AR-209 ─ Inspection moved to RESPONSE_DRAFTING → coordinator assignment ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-209')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-209',
         N'Inspection moved to RESPONSE_DRAFTING → assign response coordinator',
         N'انتقال التفتيش إلى صياغة الردود → تعيين منسّق الردود',
         N'Response drafting needs a single named coordinator routing each draft '
         + N'response through Legal review and Risk Committee sign-off before it '
         + N'goes back to the regulator. Without one named owner the responses '
         + N'fragment across the team.',
         N'تتطلّب صياغة الردود منسّقاً واحداً مُسمّى يوجّه كل مسوّدة عبر المراجعة '
         + N'القانونية واعتماد لجنة المخاطر قبل إرسالها للجهة الرقابية. بدون مالك '
         + N'واحد، تتشتّت الردود عبر الفريق.',
         N'REG_INSPECTION_TRANSITIONED',
         N'{"to":{"eq":"RESPONSE_DRAFTING"}}',
         N'{"type":"NOTIFY","roles":["COMPLIANCE_OFFICER","RISK_MANAGER","LEGAL_COUNSEL"],"template":"REG_RESPONSE_DRAFTING"}',
         2, N'CUSTOM');
END

-- ─── AR-210 ─ Inspection closed → archive + notify Internal Audit ───
IF NOT EXISTS (SELECT 1 FROM platform.automation_rules
               WHERE organisation_id = @org AND rule_code = N'AR-210')
BEGIN
    INSERT INTO platform.automation_rules
        (organisation_id, rule_code, name_en, name_ar,
         description_en, description_ar,
         trigger_event, condition_json, action_json,
         priority, source)
    VALUES
        (@org, N'AR-210',
         N'Inspection closed → archive + notify Internal Audit',
         N'إغلاق التفتيش → الأرشفة وإشعار المراجعة الداخلية',
         N'On closure the formal letter + archive reference must flow to Internal '
         + N'Audit (for annual-plan input — closed-finding patterns drive next '
         + N'year''s audit universe) and to the Compliance Officer''s SAMA on-site '
         + N'inspection history file (for cross-cycle continuity).',
         N'عند الإغلاق يجب أن يتدفّق الخطاب الرسمي ومرجع الأرشيف إلى المراجعة الداخلية '
         + N'(كمدخل للخطة السنوية — أنماط الملاحظات المغلقة تحدّد عالم التدقيق العام '
         + N'القادم) وإلى ملف تاريخ تفتيش ساما الميداني لدى مسؤول الالتزام (لاستمرارية '
         + N'الدورات).',
         N'REG_INSPECTION_CLOSED',
         N'{}',
         N'{"type":"NOTIFY","roles":["INTERNAL_AUDITOR","COMPLIANCE_OFFICER","BOARD_SECRETARY"],"template":"REG_INSPECTION_CLOSED"}',
         2, N'CUSTOM');
END
GO
