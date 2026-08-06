-- V159 — Demo expansion: instantiate 5 additional GRC committees from
-- V154 templates (Compliance, Cyber, Privacy, AML, AI-Ethics), 6 worked-
-- example meetings, 9 worked-example resolutions + 11 action items.
--
-- V157 covered Board / Risk / Audit / BCM. This migration fills the
-- remaining major GRC committees so a user opening the platform sees the
-- full Saudi-regulator catalogue active out-of-the-box, not just the
-- three traditional governance committees.
--
-- All content bilingual (EN + native Saudi Arabic). Regulator anchors:
--   * Compliance Committee — SAMA, AML Law, CMA, PDPL
--   * Cyber Security Committee — NCA ECC-2 § 1-2-1 mandate
--   * Privacy / DPO Committee — PDPL Article 30 (DPO designation)
--   * AML Committee — Anti-Money-Laundering Law Article 41
--   * AI Ethics Committee — SDAIA AI Ethics Principles + AI Governance
--
-- Resolutions carry vote breakdowns, dissents where realistic, and
-- regulatorNotify=1 for items that mandate CMA / SAMA / SDAIA / SAFIU
-- disclosure. Action items include cross-module links
-- (linkedModule + linkedEntityCode) so the cross-module-link chip on
-- the Action register tab renders meaningfully on every row.

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ═════════════════════════════════════════════════════════════════════
-- 1. Instantiate 5 new demo committees from V154 templates
-- ═════════════════════════════════════════════════════════════════════


-- ═════════════════════════════════════════════════════════════════════
-- 1b. The Executive Committee (F2-77 / V331, added 2026-08-06)
-- ═════════════════════════════════════════════════════════════════════
--
-- WHY THIS ARRIVED LATE, AND WHY IT IS IN *THIS* FILE.
--
-- V331 verifies that every board_pack.packs.audience matches an
-- admin.committees.committee_type, and THROWS otherwise. On a Flyway-built database it
-- threw: three demo packs carry audiences BOARD, RISK_COMMITTEE and EXEC_COMMITTEE, and
-- the first two are seeded here while EXEC_COMMITTEE was created only by the Python
-- bootstrap. Dev and prod both hold COM-EXEC "Executive Committee" (organisation 1),
-- so V331 passed everywhere anyone looked and failed on the one build no one ran: a
-- fresh install. Same shape as F2-146 — data that exists because a script made it, not
-- because a migration did.
--
-- It belongs in V159 rather than a new V361 for a mechanical reason: V331 runs FIRST, so
-- a later file cannot satisfy its guard. Editing V159 is checksum-safe because the
-- deployed server is baselined at 292, which never recorded a checksum for V1-V291.
--
-- Values are copied from the live dev row, not invented. EXEC_COMMITTEE is already in
-- CommitteeTypes.VALUES and in the CK_com_type CHECK, so the vocabulary always intended
-- this committee to exist; only the seed was missing.

IF NOT EXISTS (SELECT 1 FROM admin.committees WHERE code = N'COM-EXEC')
BEGIN
    INSERT INTO admin.committees
        (organisation_id, code, name_en, name_ar, description_en, description_ar,
         committee_type, chairperson_name, chairperson_role, secretary_name,
         members_json, charter_en, charter_ar,
         meeting_cadence, quorum_pct, status, external_id,
         charter_template_code, regulatory_domain, regulator_anchors)
    VALUES
        (@org, N'COM-EXEC',
         N'Executive Committee', N'اللجنة التنفيذية',
         N'Top management committee; drives execution of strategy and operational decisions.',
         N'لجنة الإدارة العليا؛ تقود تنفيذ الاستراتيجية والقرارات التشغيلية.',
         N'EXEC_COMMITTEE',
         N'President of the Authority', N'Chair',
         N'Board & Committees Secretariat',
         N'[{"name": "President of the Authority", "role": "Chair", "votingMember": true}, {"name": "Deputy for Shared Services", "role": "Member", "votingMember": true}, {"name": "Deputy for Digital Transformation & Data", "role": "Member", "votingMember": true}, {"name": "Deputy for Operations & Services", "role": "Member", "votingMember": true}, {"name": "Director of Strategy & Performance", "role": "Member", "votingMember": true}]',
         N'Coordinates execution of the approved strategy, budgets and cross-deputyship operational decisions.',
         N'تنسق تنفيذ الاستراتيجية المعتمدة والميزانيات والقرارات التشغيلية بين الوكالات.',
         N'MONTHLY', 60, N'ACTIVE', N'EXT-COM-EXEC',
         NULL, N'PUBLIC', N'Vision2030');
END

-- ⚠ No GO here, deliberately. @org is declared in an earlier batch of this file and T-SQL
-- variables do not survive a batch boundary; the first version of this block ended with GO
-- and every insert below it failed with "Must declare the scalar variable @org".

IF NOT EXISTS (SELECT 1 FROM admin.committees WHERE code = N'COM-COMPLIANCE')
BEGIN
    INSERT INTO admin.committees
        (organisation_id, code, name_en, name_ar, description_en, description_ar,
         committee_type, chairperson_name, chairperson_role, secretary_name,
         members_json, charter_en, charter_ar,
         meeting_cadence, quorum_pct, status, external_id,
         charter_template_code, regulatory_domain, regulator_anchors)
    VALUES
        (@org, N'COM-COMPLIANCE',
         N'Compliance Committee', N'لجنة الالتزام',
         N'Reviews compliance posture across SAMA, CMA, NCA, PDPL, AML and FATF requirements. Approves remediation plans for control gaps and reviews regulatory-change impact.',
         N'تراجع وضع الالتزام عبر متطلبات ساما وهيئة السوق المالية والهيئة الوطنية للأمن السيبراني ونظام حماية البيانات ومكافحة غسل الأموال. تعتمد خطط معالجة فجوات الضوابط وتراجع أثر التغييرات التنظيمية.',
         N'COMPLIANCE_COMMITTEE',
         N'Chief Compliance Officer', N'CCO',
         N'Compliance Manager',
         N'[{"name":"CCO","role":"Chair","votingMember":true},{"name":"General Counsel","role":"Member","votingMember":true},{"name":"Head of AML","role":"Member","votingMember":true},{"name":"DPO","role":"Member","votingMember":true},{"name":"CRO","role":"Standing Invitee","votingMember":false},{"name":"Head of Internal Audit","role":"Standing Invitee","votingMember":false}]',
         N'Quarterly attestation packs to Board Audit Committee; monthly RegChange watch-list review; sign-off on AML / PDPL / NCA controls; sponsors compliance training plan.',
         N'حزم اعتماد ربع سنوية للجنة المراجعة بالمجلس؛ مراجعة شهرية لقائمة مراقبة التغييرات التنظيمية؛ اعتماد ضوابط مكافحة غسل الأموال وحماية البيانات والأمن السيبراني؛ ترعى خطة التدريب على الالتزام.',
         N'MONTHLY', 67, N'ACTIVE', N'COM-COMPLIANCE',
         N'CTPL-COMPLIANCE', N'GENERAL', N'SAMA,CMA,AML-Law,PDPL,NCA,FATF');

    INSERT INTO admin.committees
        (organisation_id, code, name_en, name_ar, description_en, description_ar,
         committee_type, chairperson_name, chairperson_role, secretary_name,
         members_json, charter_en, charter_ar,
         meeting_cadence, quorum_pct, status, external_id,
         charter_template_code, regulatory_domain, regulator_anchors)
    VALUES
        (@org, N'COM-CYBER',
         N'Cyber Security Committee', N'لجنة الأمن السيبراني',
         N'Cyber governance body mandated by NCA ECC-2 control 1-2-1. Oversees NCA ECC posture, SAMA Cyber Security Framework, threat intelligence, major incident reviews, and cyber risk appetite.',
         N'هيئة حوكمة الأمن السيبراني المنصوص عليها في الضابط 1-2-1 من الضوابط الأساسية للأمن السيبراني NCA ECC-2. تشرف على وضع ECC وإطار ساما للأمن السيبراني والاستخبارات السيبرانية ومراجعات الحوادث الكبرى وشهية المخاطر السيبرانية.',
         N'CYBER_SECURITY',
         N'CISO', N'CISO',
         N'Security Governance Lead',
         N'[{"name":"CISO","role":"Chair","votingMember":true},{"name":"CIO","role":"Vice-Chair","votingMember":true},{"name":"Head of SOC","role":"Member","votingMember":true},{"name":"Head of Risk","role":"Member","votingMember":true},{"name":"DPO","role":"Member","votingMember":true},{"name":"Internal Audit","role":"Standing Invitee","votingMember":false},{"name":"External Cyber Advisor","role":"Standing Invitee","votingMember":false}]',
         N'Monthly review of NCA ECC posture (114 controls); approves major-incident classifications; sponsors RT/PT programme; reviews threat-intel feed quality; approves cyber-control exceptions.',
         N'مراجعة شهرية لوضع ضوابط NCA ECC (114 ضابطاً)؛ اعتماد تصنيفات الحوادث الكبرى؛ ترعى برنامج الفريق الأحمر/اختبار الاختراق؛ مراجعة جودة تغذية التهديدات؛ اعتماد استثناءات الضوابط السيبرانية.',
         N'MONTHLY', 67, N'ACTIVE', N'COM-CYBER',
         N'CTPL-CYBER', N'GENERAL', N'NCA-ECC-2,SAMA-CSF,Saudi-CERT,ISO27001');

    INSERT INTO admin.committees
        (organisation_id, code, name_en, name_ar, description_en, description_ar,
         committee_type, chairperson_name, chairperson_role, secretary_name,
         members_json, charter_en, charter_ar,
         meeting_cadence, quorum_pct, status, external_id,
         charter_template_code, regulatory_domain, regulator_anchors)
    VALUES
        (@org, N'COM-PRIVACY',
         N'Privacy / DPO Committee', N'لجنة حماية البيانات الشخصية',
         N'Chaired by the Data Protection Officer mandated by PDPL Article 30. Reviews data-subject requests, cross-border transfer applications, DPIAs, consent posture and breach notifications to SDAIA.',
         N'يرأسها مسؤول حماية البيانات المعيّن بموجب المادة 30 من نظام حماية البيانات الشخصية. تراجع طلبات أصحاب البيانات وطلبات النقل عبر الحدود وتقييمات الأثر الخاص بحماية البيانات ووضع الموافقات وإخطارات الانتهاكات إلى الهيئة السعودية للبيانات والذكاء الاصطناعي.',
         N'PRIVACY_DPO',
         N'DPO (Data Protection Officer)', N'DPO',
         N'Privacy Programme Manager',
         N'[{"name":"DPO","role":"Chair","votingMember":true},{"name":"General Counsel","role":"Member","votingMember":true},{"name":"CISO","role":"Member","votingMember":true},{"name":"Chief Marketing Officer","role":"Member","votingMember":true},{"name":"Head of HR","role":"Member","votingMember":true},{"name":"Compliance Officer","role":"Standing Invitee","votingMember":false}]',
         N'Reviews DSRs against PDPL Article 4 (30-day SLA); approves cross-border transfer mechanisms (Article 29); reviews DPIAs before high-risk launches; signs off SDAIA breach notifications.',
         N'مراجعة طلبات أصحاب البيانات وفق المادة الرابعة (مهلة 30 يوماً)؛ اعتماد آليات النقل عبر الحدود وفق المادة 29؛ مراجعة تقييمات الأثر قبل إطلاق المشاريع عالية المخاطر؛ اعتماد إخطارات الانتهاكات إلى الهيئة.',
         N'MONTHLY', 67, N'ACTIVE', N'COM-PRIVACY',
         N'CTPL-PRIVACY', N'GENERAL', N'PDPL,SDAIA,NDMO');

    INSERT INTO admin.committees
        (organisation_id, code, name_en, name_ar, description_en, description_ar,
         committee_type, chairperson_name, chairperson_role, secretary_name,
         members_json, charter_en, charter_ar,
         meeting_cadence, quorum_pct, status, external_id,
         charter_template_code, regulatory_domain, regulator_anchors)
    VALUES
        (@org, N'COM-AML',
         N'AML Committee', N'لجنة مكافحة غسل الأموال',
         N'AML governance body mandated by Article 41 of the Saudi Anti-Money-Laundering Law. Reviews SAR escalations, sanctions-screening results, high-risk relationships, and AML programme effectiveness.',
         N'هيئة حوكمة مكافحة غسل الأموال المنصوص عليها في المادة 41 من نظام مكافحة غسل الأموال السعودي. تراجع تصعيدات تقارير المعاملات المشبوهة، ونتائج فحص العقوبات، والعلاقات عالية المخاطر، وفعالية برنامج مكافحة غسل الأموال.',
         N'AML',
         N'MLRO (Money Laundering Reporting Officer)', N'MLRO',
         N'AML Analyst',
         N'[{"name":"MLRO","role":"Chair","votingMember":true},{"name":"CCO","role":"Member","votingMember":true},{"name":"General Counsel","role":"Member","votingMember":true},{"name":"Head of Operations","role":"Member","votingMember":true},{"name":"Head of Financial Crime","role":"Member","votingMember":true},{"name":"Internal Audit","role":"Standing Invitee","votingMember":false}]',
         N'Bi-weekly review of SAR pipeline; approves SAFIU filings; reviews sanctions-screening false-positive rates; oversees CDD/EDD quality programme; sponsors AML training.',
         N'مراجعة نصف شهرية لخط تقارير المعاملات المشبوهة؛ اعتماد بلاغات وحدة التحريات المالية SAFIU؛ مراجعة معدلات الإيجابيات الكاذبة لفحص العقوبات؛ الإشراف على برنامج العناية الواجبة المعززة؛ ترعى تدريب مكافحة غسل الأموال.',
         N'BIWEEKLY', 67, N'ACTIVE', N'COM-AML',
         N'CTPL-AML', N'BANKING', N'SAMA-AML,AML-Law,FATF,SAFIU');

    INSERT INTO admin.committees
        (organisation_id, code, name_en, name_ar, description_en, description_ar,
         committee_type, chairperson_name, chairperson_role, secretary_name,
         members_json, charter_en, charter_ar,
         meeting_cadence, quorum_pct, status, external_id,
         charter_template_code, regulatory_domain, regulator_anchors)
    VALUES
        (@org, N'COM-AIETHICS',
         N'AI Ethics Committee', N'لجنة أخلاقيات الذكاء الاصطناعي',
         N'Governs AI use-cases against SDAIA AI Ethics Principles. Reviews AI risk assessments, approves high-risk AI deployments, monitors model drift / bias, and ensures human-in-the-loop on consequential decisions.',
         N'تحكم حالات استخدام الذكاء الاصطناعي مقابل مبادئ أخلاقيات الذكاء الاصطناعي للهيئة السعودية للبيانات والذكاء الاصطناعي. تراجع تقييمات مخاطر الذكاء الاصطناعي، تعتمد عمليات النشر عالية المخاطر، ترصد انجراف النموذج والتحيز، تضمن إشراف الإنسان على القرارات الجوهرية.',
         N'AI_ETHICS',
         N'Head of AI Governance', N'Head of AI Governance',
         N'AI Compliance Lead',
         N'[{"name":"Head of AI Governance","role":"Chair","votingMember":true},{"name":"CTO","role":"Member","votingMember":true},{"name":"DPO","role":"Member","votingMember":true},{"name":"Chief Data Officer","role":"Member","votingMember":true},{"name":"Independent Ethicist","role":"Member (External)","votingMember":true,"isIndependent":true},{"name":"Head of Customer","role":"Standing Invitee","votingMember":false},{"name":"Legal Counsel - AI","role":"Standing Invitee","votingMember":false}]',
         N'Quarterly review of all AI use-cases; approves high-risk AI deployments per SDAIA classification; reviews model drift / bias metrics; mandates human-in-the-loop on consequential decisions (credit, hiring, healthcare).',
         N'مراجعة ربع سنوية لجميع حالات استخدام الذكاء الاصطناعي؛ اعتماد عمليات النشر عالية المخاطر وفق تصنيف الهيئة السعودية للذكاء الاصطناعي؛ مراجعة مقاييس انجراف النموذج والتحيز؛ إلزامية إشراف الإنسان على القرارات الجوهرية (الائتمان، التوظيف، الصحة).',
         N'QUARTERLY', 67, N'ACTIVE', N'COM-AIETHICS',
         N'CTPL-AIETHICS', N'GENERAL', N'SDAIA-AI-Ethics,PDPL,NDMO');
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 2. Demo meetings — one MINUTES_APPROVED meeting per new committee
-- ═════════════════════════════════════════════════════════════════════

DECLARE @org BIGINT = 1;
DECLARE @compl  BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-COMPLIANCE');
DECLARE @cyber  BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-CYBER');
DECLARE @priv   BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-PRIVACY');
DECLARE @aml    BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-AML');
DECLARE @ai     BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-AIETHICS');
DECLARE @bcm    BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-BCM');

IF @compl IS NOT NULL AND NOT EXISTS (SELECT 1 FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-09')
BEGIN
    INSERT INTO admin.committee_meetings
        (organisation_id, committee_id, committee_code, committee_name_en,
         meeting_code, title_en, title_ar,
         scheduled_at, started_at, ended_at, location_or_link,
         agenda_json, attendees_json, quorum_met,
         minutes_en, minutes_ar,
         decisions_json, action_items_json,
         chair_name, secretary_name, status,
         minutes_approved_at, approver_name, external_id)
    VALUES
        (@org, @compl, N'COM-COMPLIANCE', N'Compliance Committee',
         N'MTG-DEMO-09',
         N'Compliance — April 2026 Monthly Review',
         N'لجنة الالتزام - المراجعة الشهرية أبريل 2026',
         DATEADD(DAY, -15, SYSUTCDATETIME()), DATEADD(DAY, -15, SYSUTCDATETIME()),
         DATEADD(MINUTE, 90, DATEADD(DAY, -15, SYSUTCDATETIME())),
         N'Riyadh HQ — Board Room B (Teams hybrid)',
         N'[{"seq":1,"topicEn":"Approval of prior minutes","topicAr":"اعتماد محضر الاجتماع السابق","presenter":"Secretary","minutesAllocated":5},{"seq":2,"topicEn":"PDPL DSR backlog — March position","topicAr":"تراكم طلبات أصحاب البيانات - وضع مارس","presenter":"DPO","minutesAllocated":15},{"seq":3,"topicEn":"SAMA Cyber Attestation 2026 — readiness","topicAr":"شهادة ساما للأمن السيبراني 2026 - الجاهزية","presenter":"CCO","minutesAllocated":25},{"seq":4,"topicEn":"AML SAR pipeline — Q1 metrics","topicAr":"خط تقارير المعاملات المشبوهة - مقاييس الربع الأول","presenter":"MLRO","minutesAllocated":20},{"seq":5,"topicEn":"Regulatory Change Watch-list","topicAr":"قائمة مراقبة التغييرات التنظيمية","presenter":"Compliance Manager","minutesAllocated":15},{"seq":6,"topicEn":"AOB","topicAr":"ما يستجد","presenter":"Chair","minutesAllocated":10}]',
         N'[{"name":"CCO","role":"Chair","present":true},{"name":"General Counsel","role":"Member","present":true},{"name":"Head of AML","role":"Member","present":true},{"name":"DPO","role":"Member","present":true},{"name":"CRO","role":"Standing Invitee","present":true}]',
         1,
         N'CCO opened with prior-minutes approval. DPO reported DSR backlog at 28 cases (vs 22 last month) — 4 are within 7 days of the 30-day Article 4 deadline. Committee approved an emergency ops sprint and the recommendation to escalate to Board Risk Committee. CCO presented SAMA Cyber Attestation 2026 readiness: 109/114 NCA ECC controls evidenced; 5 remaining tied to backup-verification gap previously raised by Audit Committee (RES-2026-Q1-AUD-002). MLRO reported Q1 SAR volume up 38% YoY — committee acknowledged that filtering quality (Acuity ML model) is acceptable. Compliance Manager presented RegChange watch-list: 7 active changes tracked, top item SAMA RBC v2 effective Sep 2026 with 4-month implementation runway.',
         N'افتتح المدير التنفيذي للالتزام بمصادقة المحضر السابق. أفاد مسؤول حماية البيانات بتراكم 28 طلباً (مقابل 22 الشهر الماضي) - 4 منها في غضون 7 أيام من مهلة المادة الرابعة (30 يوماً). اعتمدت اللجنة فريق طوارئ تشغيلي والتوصية بالتصعيد للجنة مخاطر المجلس. عرض المدير جاهزية شهادة ساما 2026: 109 من أصل 114 ضابطاً موثقة؛ 5 ضوابط متبقية مرتبطة بفجوة التحقق من النسخ الاحتياطية التي أثارتها لجنة المراجعة. أفاد مسؤول مكافحة غسل الأموال بزيادة 38% سنوياً في حجم تقارير المعاملات المشبوهة - أقرّت اللجنة بقبول جودة التصفية. عرض مدير الالتزام قائمة مراقبة التغييرات: 7 تغييرات نشطة، أبرزها RBC v2 الساري في سبتمبر 2026 مع مهلة تنفيذ 4 أشهر.',
         N'[]', N'[]',
         N'CCO', N'Compliance Manager', N'MINUTES_APPROVED',
         DATEADD(DAY, -10, SYSUTCDATETIME()), N'CCO', N'MTG-DEMO-09'),

        (@org, @cyber, N'COM-CYBER', N'Cyber Security Committee',
         N'MTG-DEMO-10',
         N'Cyber — April 2026 NCA ECC Review',
         N'لجنة الأمن السيبراني - مراجعة ضوابط NCA ECC أبريل 2026',
         DATEADD(DAY, -12, SYSUTCDATETIME()), DATEADD(DAY, -12, SYSUTCDATETIME()),
         DATEADD(MINUTE, 105, DATEADD(DAY, -12, SYSUTCDATETIME())),
         N'Riyadh HQ — Cyber War-Room',
         N'[{"seq":1,"topicEn":"Approval of prior minutes","topicAr":"اعتماد محضر الاجتماع السابق","presenter":"Secretary","minutesAllocated":5},{"seq":2,"topicEn":"NCA ECC Posture — 114 control review","topicAr":"وضع ضوابط NCA ECC - مراجعة 114 ضابطاً","presenter":"CISO","minutesAllocated":30},{"seq":3,"topicEn":"INC-2026-014 Crypto-Mining Detection (HIGH severity)","topicAr":"الحادث INC-2026-014 - رصد تعدين العملات (شدة عالية)","presenter":"Head of SOC","minutesAllocated":25},{"seq":4,"topicEn":"Vulnerability backlog — VLN-2026-* critical/high","topicAr":"تراكم الثغرات - VLN-2026-* حرجة وعالية","presenter":"Vuln Manager","minutesAllocated":15},{"seq":5,"topicEn":"Threat-Intel Feed Quality Q1","topicAr":"جودة تغذية استخبارات التهديدات - الربع الأول","presenter":"Threat-Intel Lead","minutesAllocated":15},{"seq":6,"topicEn":"AOB — RT exercise scheduling","topicAr":"ما يستجد - جدولة تمرين الفريق الأحمر","presenter":"Chair","minutesAllocated":15}]',
         N'[{"name":"CISO","role":"Chair","present":true},{"name":"CIO","role":"Vice-Chair","present":true},{"name":"Head of SOC","role":"Member","present":true},{"name":"Head of Risk","role":"Member","present":true},{"name":"DPO","role":"Member","present":true}]',
         1,
         N'CISO opened the meeting and noted INC-2026-014 (crypto-mining on bastion host, HIGH severity) was contained within 2h47m of detection — within the 4h target. Head of SOC presented full root-cause: developer SSH key compromised via phishing, lateral movement blocked by network segmentation. Committee resolved (RES-2026-Q2-CYB-001) to mandate FIDO2 for all bastion access by 2026-06-30 (linked to ECC control 2-3-1-1). Vuln Manager reported 14 critical + 47 high CVEs in backlog; AGE > 60d for 6 of them — committee approved escalation to CIO with weekly reporting until cleared. Threat-Intel Lead noted Acuity feed false-positive rate at 12% — acceptable but Saudi-CERT feed integration recommended.',
         N'افتتح رئيس أمن المعلومات الاجتماع وأشار إلى احتواء الحادث INC-2026-014 (تعدين عملات على خادم الإدارة، شدة عالية) خلال 2 ساعة و47 دقيقة من الاكتشاف - ضمن الهدف 4 ساعات. عرض رئيس مركز العمليات الأمنية السبب الجذري الكامل: اختراق مفتاح SSH لمطور عبر التصيد، حُجبت الحركة الجانبية بفضل تقسيم الشبكة. قررت اللجنة (RES-2026-Q2-CYB-001) إلزامية FIDO2 لجميع وصول خوادم الإدارة بحلول 2026-06-30 (مرتبط بضابط ECC رقم 2-3-1-1). أفاد مدير الثغرات بـ 14 ثغرة حرجة و47 عالية في التراكم؛ عمر 6 منها أكثر من 60 يوماً - وافقت اللجنة على التصعيد لرئيس تقنية المعلومات مع تقارير أسبوعية. أشار قائد الاستخبارات إلى معدل إيجابيات كاذبة 12% لتغذية Acuity - مقبول ولكن يُوصى بدمج تغذية CERT السعودي.',
         N'[]', N'[]',
         N'CISO', N'Security Governance Lead', N'MINUTES_APPROVED',
         DATEADD(DAY, -7, SYSUTCDATETIME()), N'CISO', N'MTG-DEMO-10'),

        (@org, @priv, N'COM-PRIVACY', N'Privacy / DPO Committee',
         N'MTG-DEMO-11',
         N'Privacy — April 2026 Monthly DSR + DPIA Review',
         N'لجنة حماية البيانات - مراجعة شهرية لطلبات أصحاب البيانات والتقييمات أبريل 2026',
         DATEADD(DAY, -10, SYSUTCDATETIME()), DATEADD(DAY, -10, SYSUTCDATETIME()),
         DATEADD(MINUTE, 75, DATEADD(DAY, -10, SYSUTCDATETIME())),
         N'Teams (DPO virtual)',
         N'[{"seq":1,"topicEn":"DSR Backlog — PDPL Article 4 30-day SLA","topicAr":"تراكم طلبات أصحاب البيانات - مهلة المادة الرابعة (30 يوماً)","presenter":"DPO","minutesAllocated":20},{"seq":2,"topicEn":"DPIA — New Marketing AI Personalisation","topicAr":"تقييم أثر - منصة التخصيص التسويقي الجديدة","presenter":"Privacy Programme Manager","minutesAllocated":20},{"seq":3,"topicEn":"Cross-Border Transfer to AWS Frankfurt (Article 29)","topicAr":"النقل عبر الحدود إلى AWS فرانكفورت (المادة 29)","presenter":"General Counsel","minutesAllocated":15},{"seq":4,"topicEn":"Breach BR-2026-003 — closure report","topicAr":"انتهاك BR-2026-003 - تقرير الإغلاق","presenter":"DPO","minutesAllocated":15},{"seq":5,"topicEn":"AOB","topicAr":"ما يستجد","presenter":"Chair","minutesAllocated":5}]',
         N'[{"name":"DPO","role":"Chair","present":true},{"name":"General Counsel","role":"Member","present":true},{"name":"CISO","role":"Member","present":true},{"name":"Chief Marketing Officer","role":"Member","present":true},{"name":"Head of HR","role":"Member","present":true}]',
         1,
         N'DPO opened with DSR backlog: 28 cases of which 4 are >23 days old (within 7 days of SLA breach). Privacy Programme Manager presented DPIA for new Marketing AI Personalisation — committee resolved (RES-2026-Q2-PRIV-001) to APPROVE with conditions: opt-out by default for under-18s, no sensitive-category inputs, quarterly bias review. General Counsel proposed Article 29 mechanism for AWS Frankfurt transfer (SCC + supplementary measures); committee resolved to APPROVE the binding-corporate-rules path, regulator notification to SDAIA required within 30 days. BR-2026-003 closure: 247 records affected, individuals notified within 72h, SDAIA filing complete — committee endorsed CLOSED status.',
         N'افتتحت المدير التنفيذي لحماية البيانات بتراكم الطلبات: 28 حالة منها 4 أعمارها أكثر من 23 يوماً (في غضون 7 أيام من تجاوز المهلة). عرض مدير برنامج الخصوصية تقييم الأثر لمنصة التخصيص التسويقي الجديدة - قررت اللجنة (RES-2026-Q2-PRIV-001) الموافقة بشروط: الانسحاب الافتراضي لمن دون 18 عاماً، عدم استخدام مدخلات حساسة، مراجعة ربع سنوية للتحيز. اقترح المستشار العام آلية المادة 29 للنقل إلى AWS فرانكفورت (الشروط التعاقدية القياسية مع تدابير تكميلية)؛ قررت اللجنة الموافقة على مسار قواعد الشركة الملزمة، مطلوب إخطار الهيئة السعودية للبيانات والذكاء الاصطناعي خلال 30 يوماً. إغلاق BR-2026-003: 247 سجلاً متأثراً، تم إخطار الأفراد خلال 72 ساعة، اكتمل إيداع SDAIA - أيدت اللجنة الحالة "مغلق".',
         N'[]', N'[]',
         N'DPO', N'Privacy Programme Manager', N'MINUTES_APPROVED',
         DATEADD(DAY, -5, SYSUTCDATETIME()), N'DPO', N'MTG-DEMO-11'),

        (@org, @aml, N'COM-AML', N'AML Committee',
         N'MTG-DEMO-12',
         N'AML — April 2026 Bi-Weekly SAR Review',
         N'لجنة مكافحة غسل الأموال - مراجعة نصف شهرية لتقارير المعاملات المشبوهة أبريل 2026',
         DATEADD(DAY, -8, SYSUTCDATETIME()), DATEADD(DAY, -8, SYSUTCDATETIME()),
         DATEADD(MINUTE, 60, DATEADD(DAY, -8, SYSUTCDATETIME())),
         N'Riyadh HQ — Compliance Suite 3',
         N'[{"seq":1,"topicEn":"SAR Pipeline Review (last 14d)","topicAr":"مراجعة خط تقارير المعاملات المشبوهة (آخر 14 يوماً)","presenter":"MLRO","minutesAllocated":20},{"seq":2,"topicEn":"Sanctions-Screening False-Positive Rate","topicAr":"معدل الإيجابيات الكاذبة لفحص العقوبات","presenter":"AML Analyst","minutesAllocated":15},{"seq":3,"topicEn":"PEP Relationship Review (3 quarterly)","topicAr":"مراجعة علاقات الأشخاص المعرّضين سياسياً (3 ربع سنوية)","presenter":"MLRO","minutesAllocated":15},{"seq":4,"topicEn":"AOB — SAFIU filing template update","topicAr":"ما يستجد - تحديث قالب إيداع SAFIU","presenter":"Chair","minutesAllocated":10}]',
         N'[{"name":"MLRO","role":"Chair","present":true},{"name":"CCO","role":"Member","present":true},{"name":"General Counsel","role":"Member","present":true},{"name":"Head of Operations","role":"Member","present":true},{"name":"Head of Financial Crime","role":"Member","present":true}]',
         1,
         N'MLRO opened with SAR pipeline: 11 filed in past 14 days (vs 8 prior cycle). 3 escalated to SAFIU (Saudi Arabia Financial Intelligence Unit) requiring committee endorsement. Committee resolved (RES-2026-Q2-AML-001) to ENDORSE the 3 SAFIU filings: (a) cross-border wire pattern indicating layering on Customer C-7821, (b) cash-deposit structuring on SME relationship M-1142, (c) trade-based ML red-flag on inter-Gulf invoice mismatch. False-positive rate on sanctions screening at 23% (target <15%) — MLRO commissioned vendor performance review with Acuity ML. PEP review: 3 PEP relationships flagged for enhanced annual review — all retained subject to additional monitoring.',
         N'افتتح مسؤول الإبلاغ خط تقارير المعاملات المشبوهة: تم إيداع 11 تقريراً خلال آخر 14 يوماً (مقابل 8 في الدورة السابقة). تم تصعيد 3 منها إلى وحدة التحريات المالية SAFIU بحاجة لاعتماد اللجنة. قررت اللجنة (RES-2026-Q2-AML-001) إقرار 3 إيداعات لـ SAFIU: (أ) نمط تحويلات عبر حدودية يشير لطبقات على العميل C-7821، (ب) هيكلة إيداعات نقدية على علاقة شركة صغيرة M-1142، (ج) علامة حمراء على غسل أموال مستند للتجارة بسبب عدم تطابق فواتير عبر الخليج. معدل الإيجابيات الكاذبة لفحص العقوبات 23% (الهدف أقل من 15%) - كلّف مسؤول الإبلاغ بمراجعة أداء المورد Acuity ML. مراجعة الأشخاص المعرّضين سياسياً: 3 علاقات مُعلّمة لمراجعة سنوية معززة - تم الاحتفاظ بها جميعاً مع مراقبة إضافية.',
         N'[]', N'[]',
         N'MLRO', N'AML Analyst', N'MINUTES_APPROVED',
         DATEADD(DAY, -3, SYSUTCDATETIME()), N'MLRO', N'MTG-DEMO-12'),

        (@org, @ai, N'COM-AIETHICS', N'AI Ethics Committee',
         N'MTG-DEMO-13',
         N'AI Ethics — Q1 2026 Quarterly Review',
         N'لجنة أخلاقيات الذكاء الاصطناعي - المراجعة الربع سنوية للربع الأول 2026',
         DATEADD(DAY, -20, SYSUTCDATETIME()), DATEADD(DAY, -20, SYSUTCDATETIME()),
         DATEADD(MINUTE, 120, DATEADD(DAY, -20, SYSUTCDATETIME())),
         N'Riyadh HQ — Innovation Lab',
         N'[{"seq":1,"topicEn":"AI Use-Case Register — Q1 inventory","topicAr":"سجل حالات استخدام الذكاء الاصطناعي - جرد الربع الأول","presenter":"Head of AI Governance","minutesAllocated":25},{"seq":2,"topicEn":"High-Risk Approval — Credit Scoring Model v3","topicAr":"اعتماد عالي المخاطر - نموذج التقييم الائتماني v3","presenter":"Chief Data Officer","minutesAllocated":30},{"seq":3,"topicEn":"Model Drift / Bias Quarterly Metrics","topicAr":"مقاييس انجراف النموذج والتحيز ربع سنوياً","presenter":"AI Compliance Lead","minutesAllocated":25},{"seq":4,"topicEn":"SDAIA AI Ethics Self-Assessment 2026","topicAr":"التقييم الذاتي لأخلاقيات الذكاء الاصطناعي للهيئة 2026","presenter":"Head of AI Governance","minutesAllocated":25},{"seq":5,"topicEn":"AOB","topicAr":"ما يستجد","presenter":"Chair","minutesAllocated":15}]',
         N'[{"name":"Head of AI Governance","role":"Chair","present":true},{"name":"CTO","role":"Member","present":true},{"name":"DPO","role":"Member","present":true},{"name":"Chief Data Officer","role":"Member","present":true},{"name":"Independent Ethicist","role":"Member (External)","present":true}]',
         1,
         N'Head of AI Governance opened with Q1 inventory: 17 AI use-cases active (8 production, 9 pilot). Chief Data Officer presented Credit Scoring Model v3 — uses XGBoost with SHAP explainability. Committee resolved (RES-2026-Q1-AI-001) to APPROVE production deployment with conditions: 5% canary rollout for 30 days, monthly bias audit on protected attributes (gender, region), human-in-the-loop for all rejections below threshold 580. Independent Ethicist registered support but flagged the model''s reliance on neighbourhood-level features as proxy-discrimination risk — added to risk register as RSK-2026-AI-003. Q1 drift metrics: 14/17 models within tolerance; 2 marketing models retrained; 1 fraud model paused pending root-cause review. SDAIA self-assessment: 89/100 — gap in "human oversight documentation" — action item raised.',
         N'افتتح رئيس حوكمة الذكاء الاصطناعي بجرد الربع الأول: 17 حالة استخدام نشطة (8 إنتاج، 9 تجريبية). عرض رئيس البيانات نموذج التقييم الائتماني v3 - يستخدم XGBoost مع قابلية تفسير SHAP. قررت اللجنة (RES-2026-Q1-AI-001) الموافقة على النشر الإنتاجي بشروط: نشر تجريبي 5% لمدة 30 يوماً، تدقيق شهري للتحيز على السمات المحمية (الجنس، المنطقة)، إشراف الإنسان على جميع الرفضات تحت الحد 580. سجّل المُختصّ المستقل دعمه لكن أشار إلى اعتماد النموذج على ميزات على مستوى الحي كخطر تمييز بالوكالة - أُضيف إلى سجل المخاطر كـ RSK-2026-AI-003. مقاييس انجراف الربع الأول: 14 من 17 نموذجاً ضمن التسامح؛ تم إعادة تدريب نموذجين تسويقيين؛ تم إيقاف نموذج احتيال انتظار مراجعة السبب الجذري. التقييم الذاتي للهيئة: 89/100 - فجوة في "توثيق الإشراف البشري" - تم رفع بند عمل.',
         N'[]', N'[]',
         N'Head of AI Governance', N'AI Compliance Lead', N'MINUTES_APPROVED',
         DATEADD(DAY, -15, SYSUTCDATETIME()), N'Head of AI Governance', N'MTG-DEMO-13'),

        (@org, @bcm, N'COM-BCM', N'BCM Steering Committee',
         N'MTG-DEMO-14',
         N'BCM — Q1 2026 DR Test + BIA Refresh',
         N'لجنة استمرارية الأعمال - اختبار التعافي من الكوارث وتحديث BIA الربع الأول 2026',
         DATEADD(DAY, -18, SYSUTCDATETIME()), DATEADD(DAY, -18, SYSUTCDATETIME()),
         DATEADD(MINUTE, 105, DATEADD(DAY, -18, SYSUTCDATETIME())),
         N'Riyadh HQ — Crisis Room',
         N'[{"seq":1,"topicEn":"DR Test Results — Q1 Core Banking failover","topicAr":"نتائج اختبار التعافي - تجاوز الفشل للنظام البنكي الأساسي","presenter":"BCM Officer","minutesAllocated":30},{"seq":2,"topicEn":"BIA refresh — Top 12 critical processes","topicAr":"تحديث BIA - أهم 12 عملية حرجة","presenter":"BCM Officer","minutesAllocated":25},{"seq":3,"topicEn":"Crisis-Activation Lessons-Learned (Cyber INC-2026-014)","topicAr":"الدروس المستفادة من تفعيل الأزمات (الحادث السيبراني INC-2026-014)","presenter":"COO","minutesAllocated":20},{"seq":4,"topicEn":"Tabletop exercise schedule 2026","topicAr":"جدول تمارين الطاولة 2026","presenter":"BCM Officer","minutesAllocated":15},{"seq":5,"topicEn":"AOB","topicAr":"ما يستجد","presenter":"Chair","minutesAllocated":15}]',
         N'[{"name":"COO","role":"Chair","present":true},{"name":"CIO","role":"Member","present":true},{"name":"CISO","role":"Member","present":true},{"name":"CFO","role":"Member","present":true},{"name":"Facilities Director","role":"Member","present":true}]',
         1,
         N'COO opened with the Q1 Core Banking DR test outcome — completed 2026-03-15. RTO target was 4h, actual 4h17m (GAP). RPO target 15 minutes, actual 12 minutes (PASS). Committee resolved (RES-2026-Q1-BCM-001) to ACCEPT the test outcome but raise CAPA-2026-074 to investigate the 17-minute overrun (DNS re-propagation longer than expected). BCM Officer presented refreshed BIA on top 12 critical processes — committee approved updated criticality tiers and noted that Treasury Settlement moved from Tier-2 to Tier-1 following new SAMA RBC requirements. Cyber INC-2026-014 crisis activation review: AAR-2026-007 completed, 4 lessons captured, all 4 actions in flight. Tabletop schedule 2026 approved: ransomware (May), payments outage (Aug), data-centre flood (Nov).',
         N'افتتح المدير التنفيذي للعمليات بنتيجة اختبار التعافي من الكوارث للنظام البنكي الأساسي للربع الأول - اكتمل 2026-03-15. هدف RTO 4 ساعات، الفعلي 4 ساعات و17 دقيقة (فجوة). هدف RPO 15 دقيقة، الفعلي 12 دقيقة (ناجح). قررت اللجنة (RES-2026-Q1-BCM-001) قبول نتيجة الاختبار مع رفع CAPA-2026-074 للتحقيق في تجاوز 17 دقيقة (إعادة نشر DNS أطول من المتوقع). عرض ضابط الاستمرارية BIA المحدث لأهم 12 عملية حرجة - اعتمدت اللجنة طبقات الحرجية المحدثة وأشارت إلى انتقال تسوية الخزينة من الطبقة 2 إلى الطبقة 1 إثر متطلبات RBC الجديدة من ساما. مراجعة تفعيل أزمة الحادث السيبراني: اكتمل AAR-2026-007، التُقطت 4 دروس، جميع 4 إجراءات قيد التنفيذ. تم اعتماد جدول التمارين 2026: فدية (مايو)، انقطاع المدفوعات (أغسطس)، فيضان مركز البيانات (نوفمبر).',
         N'[]', N'[]',
         N'COO', N'BCM Officer', N'MINUTES_APPROVED',
         DATEADD(DAY, -13, SYSUTCDATETIME()), N'COO', N'MTG-DEMO-14');
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 3. Worked-example resolutions — 9 across the 6 new meetings
-- ═════════════════════════════════════════════════════════════════════

DECLARE @org BIGINT = 1;
DECLARE @mtg09 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-09');
DECLARE @mtg10 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-10');
DECLARE @mtg11 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-11');
DECLARE @mtg12 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-12');
DECLARE @mtg13 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-13');
DECLARE @mtg14 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-14');

DECLARE @compl BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-COMPLIANCE');
DECLARE @cyber BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-CYBER');
DECLARE @priv  BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-PRIVACY');
DECLARE @aml   BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-AML');
DECLARE @ai    BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-AIETHICS');
DECLARE @bcm   BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-BCM');

IF @mtg09 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM admin.committee_resolutions WHERE resolution_code = N'RES-2026-Q2-COMPL-001')
BEGIN
    INSERT INTO admin.committee_resolutions
        (organisation_id, meeting_id, committee_id, resolution_code, seq, agenda_item_seq,
         title_en, title_ar, body_en, body_ar,
         proposed_by, seconded_by,
         vote_for, vote_against, vote_abstain, outcome,
         confidentiality, effective_from, review_required_by,
         regulator_notify, dissent_notes_en, dissent_notes_ar)
    VALUES
        (@org, @mtg09, @compl, N'RES-2026-Q2-COMPL-001', 1, 2,
         N'PDPL DSR Backlog — Emergency Ops Sprint',
         N'تراكم طلبات أصحاب البيانات - فريق طوارئ تشغيلي',
         N'RESOLVED that the Committee authorises an emergency 2-week operations sprint led by the DPO with 3 additional Operations analysts seconded from the Customer Success team, to clear the 28-case DSR backlog before any case breaches the PDPL Article 4 30-day SLA. CCO and DPO to report progress at the next bi-weekly stand-up.',
         N'قُرّر أن تخوّل اللجنة فريق طوارئ تشغيلي لمدة أسبوعين برئاسة مسؤول حماية البيانات وانتداب 3 محللي عمليات إضافيين من فريق نجاح العملاء، لتصفية تراكم 28 طلباً قبل تجاوز أي حالة لمهلة 30 يوماً المنصوص عليها في المادة الرابعة. يقدّم المدير ومسؤول حماية البيانات تقريراً عن التقدم في الاجتماع نصف الشهري التالي.',
         N'CCO', N'General Counsel',
         5, 0, 0, N'APPROVED',
         N'INTERNAL', CAST(DATEADD(DAY, -15, SYSUTCDATETIME()) AS DATE), CAST(DATEADD(DAY, +15, SYSUTCDATETIME()) AS DATE),
         0, NULL, NULL),

        (@org, @mtg10, @cyber, N'RES-2026-Q2-CYB-001', 1, 3,
         N'FIDO2 Mandate for All Bastion Host Access',
         N'إلزامية FIDO2 لجميع وصول خوادم الإدارة',
         N'RESOLVED that following the root-cause of INC-2026-014 (SSH key compromise via phishing leading to crypto-mining), all bastion host access (jump-servers, privileged-access workstations, and emergency-break-glass accounts) shall require hardware-backed FIDO2 authentication by 2026-06-30. CIO and CISO jointly accountable. Aligns to NCA ECC-2 control 2-3-1-1 (privileged-access multi-factor).',
         N'قُرّر إثر السبب الجذري للحادث INC-2026-014 (اختراق مفتاح SSH عبر التصيد وأدى إلى تعدين العملات)، أن جميع وصول خوادم الإدارة (خوادم القفز، محطات الوصول المتميز، حسابات الطوارئ) سيتطلب مصادقة FIDO2 مدعومة بأجهزة بحلول 2026-06-30. رئيس تقنية المعلومات ورئيس أمن المعلومات مسؤولان بالاشتراك. يتماشى مع الضابط 2-3-1-1 من NCA ECC-2.',
         N'CISO', N'CIO',
         5, 0, 0, N'APPROVED',
         N'INTERNAL', CAST(DATEADD(DAY, -12, SYSUTCDATETIME()) AS DATE), CAST(DATEADD(MONTH, +6, SYSUTCDATETIME()) AS DATE),
         1, NULL, NULL),

        (@org, @mtg10, @cyber, N'RES-2026-Q2-CYB-002', 2, 4,
         N'Vulnerability Backlog Escalation — Weekly Reporting to CIO',
         N'تصعيد تراكم الثغرات - تقارير أسبوعية لرئيس تقنية المعلومات',
         N'RESOLVED that the 6 vulnerabilities aged >60 days (4 critical + 2 high) be escalated to weekly CIO reporting until cleared, and that the IT Steering Committee be notified that infrastructure budget should accommodate accelerated remediation. Vuln Manager to publish a weekly burn-down chart.',
         N'قُرّر تصعيد الثغرات الـ 6 التي تجاوزت 60 يوماً (4 حرجة + 2 عالية) إلى تقارير أسبوعية لرئيس تقنية المعلومات حتى يتم إغلاقها، وإخطار لجنة توجيه تقنية المعلومات بأن ميزانية البنية التحتية يجب أن تستوعب المعالجة المعجّلة. ينشر مدير الثغرات مخطط احتراق أسبوعي.',
         N'CISO', N'Head of SOC',
         5, 0, 0, N'APPROVED',
         N'INTERNAL', CAST(DATEADD(DAY, -12, SYSUTCDATETIME()) AS DATE), CAST(DATEADD(MONTH, +3, SYSUTCDATETIME()) AS DATE),
         0, NULL, NULL),

        (@org, @mtg11, @priv, N'RES-2026-Q2-PRIV-001', 1, 2,
         N'Marketing AI Personalisation DPIA — Conditional Approval',
         N'تقييم أثر منصة التخصيص التسويقي - موافقة مشروطة',
         N'RESOLVED to APPROVE the deployment of the new Marketing AI Personalisation platform subject to the following PDPL-aligned conditions: (1) opt-out by default for all users under 18; (2) no sensitive-category inputs (health, religion, political opinion) under PDPL Article 6; (3) quarterly bias review on protected attributes; (4) consent renewal every 12 months. DPO to co-sign go-live readiness with CMO.',
         N'قُرّر الموافقة على نشر منصة التخصيص التسويقي الجديدة وفق الشروط التالية المتوافقة مع نظام حماية البيانات: (1) الانسحاب الافتراضي لجميع المستخدمين تحت 18 عاماً؛ (2) عدم استخدام مدخلات الفئات الحساسة (الصحة، الدين، الرأي السياسي) وفق المادة السادسة؛ (3) مراجعة ربع سنوية للتحيز على السمات المحمية؛ (4) تجديد الموافقة كل 12 شهراً. يوقّع مسؤول حماية البيانات مع المدير التسويقي على جاهزية التشغيل.',
         N'DPO', N'CMO',
         4, 0, 1, N'APPROVED',
         N'INTERNAL', CAST(DATEADD(DAY, -10, SYSUTCDATETIME()) AS DATE), CAST(DATEADD(MONTH, +3, SYSUTCDATETIME()) AS DATE),
         0,
         N'CMO abstained citing concern that quarterly bias review imposes operational drag on marketing velocity; agreed to revisit cadence in Q3 after first review cycle.',
         N'امتنع المدير التسويقي مشيراً إلى قلقه من أن المراجعة ربع السنوية للتحيز تفرض جهداً تشغيلياً على سرعة التسويق؛ وافق على إعادة النظر في الوتيرة في الربع الثالث بعد أول دورة مراجعة.'),

        (@org, @mtg11, @priv, N'RES-2026-Q2-PRIV-002', 2, 3,
         N'AWS Frankfurt Cross-Border Transfer — Binding Corporate Rules',
         N'النقل عبر الحدود إلى AWS فرانكفورت - قواعد الشركة الملزمة',
         N'RESOLVED that cross-border transfer of customer analytics data to AWS Frankfurt EU-region shall proceed via Binding Corporate Rules path under PDPL Article 29, with Standard Contractual Clauses + Supplementary Technical Measures (envelope encryption with KMS keys retained in KSA). SDAIA notification to be filed within 30 days. General Counsel to lead the notification.',
         N'قُرّر أن يتم النقل عبر الحدود لبيانات تحليلات العملاء إلى منطقة AWS فرانكفورت الأوروبية عبر مسار قواعد الشركة الملزمة وفق المادة 29 من نظام حماية البيانات، مع الشروط التعاقدية القياسية والتدابير التقنية التكميلية (تشفير المظروف مع مفاتيح KMS محتفظ بها في المملكة). يُودَع إخطار الهيئة السعودية للبيانات والذكاء الاصطناعي خلال 30 يوماً. يقود المستشار العام عملية الإخطار.',
         N'DPO', N'General Counsel',
         5, 0, 0, N'APPROVED',
         N'CONFIDENTIAL', CAST(DATEADD(DAY, -10, SYSUTCDATETIME()) AS DATE), CAST(DATEADD(YEAR, +1, SYSUTCDATETIME()) AS DATE),
         1, NULL, NULL),

        (@org, @mtg12, @aml, N'RES-2026-Q2-AML-001', 1, 1,
         N'Endorsement of 3 SAFIU Filings — April Cycle',
         N'اعتماد 3 إيداعات لـ SAFIU - دورة أبريل',
         N'RESOLVED to ENDORSE the 3 SAR escalations for filing with SAFIU (Saudi Arabia Financial Intelligence Unit): (a) Customer C-7821 cross-border wire layering pattern (SAR-2026-0142); (b) SME relationship M-1142 cash-deposit structuring (SAR-2026-0143); (c) Inter-Gulf trade-based ML red-flag on invoice mismatch (SAR-2026-0144). MLRO authorised to file under Article 70 of the AML Implementing Regulations.',
         N'قُرّر اعتماد 3 تصعيدات لتقارير المعاملات المشبوهة للإيداع لدى وحدة التحريات المالية السعودية SAFIU: (أ) العميل C-7821 نمط طبقات تحويلات عبر حدودية (SAR-2026-0142)؛ (ب) علاقة الشركة الصغيرة M-1142 هيكلة إيداعات نقدية (SAR-2026-0143)؛ (ج) علامة حمراء على غسل الأموال المستند للتجارة بين دول الخليج بسبب عدم تطابق فواتير (SAR-2026-0144). يخوّل مسؤول الإبلاغ الإيداع بموجب المادة 70 من اللائحة التنفيذية لمكافحة غسل الأموال.',
         N'MLRO', N'CCO',
         5, 0, 0, N'APPROVED',
         N'RESTRICTED', CAST(DATEADD(DAY, -8, SYSUTCDATETIME()) AS DATE), CAST(DATEADD(MONTH, +1, SYSUTCDATETIME()) AS DATE),
         1, NULL, NULL),

        (@org, @mtg13, @ai, N'RES-2026-Q1-AI-001', 1, 2,
         N'Credit Scoring Model v3 — Conditional Production Approval',
         N'نموذج التقييم الائتماني v3 - اعتماد إنتاجي مشروط',
         N'RESOLVED to APPROVE production deployment of Credit Scoring Model v3 (XGBoost with SHAP explainability) subject to: (1) 5% canary rollout for 30 days with bias-drift monitoring; (2) monthly bias audit on protected attributes (gender, region); (3) human-in-the-loop review for all rejections below score threshold 580; (4) quarterly Independent Ethicist review. Aligns with SDAIA AI Ethics Principles — Fairness + Transparency + Human Oversight.',
         N'قُرّر الموافقة على النشر الإنتاجي لنموذج التقييم الائتماني v3 (XGBoost مع قابلية تفسير SHAP) وفق الشروط: (1) نشر تجريبي 5% لمدة 30 يوماً مع مراقبة انجراف التحيز؛ (2) تدقيق شهري للتحيز على السمات المحمية (الجنس، المنطقة)؛ (3) مراجعة بإشراف بشري لجميع الرفضات تحت الحد 580؛ (4) مراجعة ربع سنوية من المُختص المستقل. يتماشى مع مبادئ أخلاقيات الذكاء الاصطناعي للهيئة - العدل والشفافية والإشراف البشري.',
         N'Chief Data Officer', N'CTO',
         4, 0, 1, N'APPROVED',
         N'CONFIDENTIAL', CAST(DATEADD(DAY, -20, SYSUTCDATETIME()) AS DATE), CAST(DATEADD(MONTH, +3, SYSUTCDATETIME()) AS DATE),
         0,
         N'Independent Ethicist registered support but flagged the model''s reliance on neighbourhood-level features as a potential proxy-discrimination vector; outcome captured as RSK-2026-AI-003 in risk register with quarterly review.',
         N'سجّل المُختصّ المستقل دعمه لكنه أشار إلى اعتماد النموذج على ميزات على مستوى الحي كاتجاه محتمل للتمييز بالوكالة؛ تم التقاط النتيجة كـ RSK-2026-AI-003 في سجل المخاطر مع مراجعة ربع سنوية.'),

        (@org, @mtg14, @bcm, N'RES-2026-Q1-BCM-001', 1, 1,
         N'Q1 Core Banking DR Test — Accept with CAPA',
         N'اختبار التعافي من الكوارث للنظام البنكي - قبول مع CAPA',
         N'RESOLVED to ACCEPT the Q1 Core Banking DR test outcome (RTO 4h17m vs target 4h — 7% overrun) and raise CAPA-2026-074 to investigate the 17-minute overrun root-cause (DNS re-propagation longer than expected). RPO performance of 12 minutes is within the 15-minute target. ISO 22301 § 8.5 compliance maintained. CIO accountable for CAPA closure within 60 days.',
         N'قُرّر قبول نتيجة اختبار التعافي من الكوارث للنظام البنكي للربع الأول (RTO 4 ساعات و17 دقيقة مقابل الهدف 4 ساعات - تجاوز 7%) ورفع CAPA-2026-074 للتحقيق في السبب الجذري لتجاوز 17 دقيقة (إعادة نشر DNS أطول من المتوقع). أداء RPO 12 دقيقة ضمن الهدف 15 دقيقة. الامتثال للمعيار ISO 22301 § 8.5 مستمر. رئيس تقنية المعلومات مسؤول عن إغلاق CAPA خلال 60 يوماً.',
         N'COO', N'CIO',
         5, 0, 0, N'APPROVED',
         N'INTERNAL', CAST(DATEADD(DAY, -18, SYSUTCDATETIME()) AS DATE), CAST(DATEADD(MONTH, +2, SYSUTCDATETIME()) AS DATE),
         0, NULL, NULL),

        (@org, @mtg14, @bcm, N'RES-2026-Q1-BCM-002', 2, 2,
         N'Treasury Settlement BIA Re-tier to Tier-1',
         N'إعادة تصنيف BIA لتسوية الخزينة إلى الطبقة 1',
         N'RESOLVED to re-tier the Treasury Settlement process from Tier-2 to Tier-1 (critical) in light of new SAMA RBC v2 requirements effective September 2026, which mandates RTO < 2h for treasury settlement systems. BCM Officer to refresh the BCP and DR runbook accordingly, and to schedule a focused DR test for Treasury Settlement in Q2 2026.',
         N'قُرّر إعادة تصنيف عملية تسوية الخزينة من الطبقة 2 إلى الطبقة 1 (حرجة) في ضوء متطلبات RBC v2 الجديدة من ساما السارية في سبتمبر 2026، والتي تستوجب RTO أقل من ساعتين لأنظمة تسوية الخزينة. يحدّث ضابط الاستمرارية خطة BCP ودليل تشغيل التعافي وفقاً لذلك، ويجدول اختبار تعافي مركّز لتسوية الخزينة في الربع الثاني 2026.',
         N'COO', N'CFO',
         5, 0, 0, N'APPROVED',
         N'INTERNAL', CAST(DATEADD(DAY, -18, SYSUTCDATETIME()) AS DATE), CAST(DATEADD(MONTH, +4, SYSUTCDATETIME()) AS DATE),
         1, NULL, NULL);
END
GO

-- ═════════════════════════════════════════════════════════════════════
-- 4. Worked-example action items — 11 across the 6 new meetings
-- ═════════════════════════════════════════════════════════════════════

DECLARE @org BIGINT = 1;
DECLARE @mtg09 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-09');
DECLARE @mtg10 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-10');
DECLARE @mtg11 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-11');
DECLARE @mtg12 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-12');
DECLARE @mtg13 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-13');
DECLARE @mtg14 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-14');

DECLARE @compl BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-COMPLIANCE');
DECLARE @cyber BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-CYBER');
DECLARE @priv  BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-PRIVACY');
DECLARE @aml   BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-AML');
DECLARE @ai    BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-AIETHICS');
DECLARE @bcm   BIGINT = (SELECT id FROM admin.committees WHERE code = N'COM-BCM');

IF @mtg09 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM admin.committee_action_items WHERE action_code = N'ACT-2026-Q2-COMPL-001')
BEGIN
    INSERT INTO admin.committee_action_items
        (organisation_id, meeting_id, committee_id, action_code, seq, agenda_item_seq,
         title_en, title_ar, description_en, description_ar,
         owner_name, owner_role, due_date, priority, status,
         linked_module, linked_entity_type, linked_entity_code)
    VALUES
        (@org, @mtg09, @compl, N'ACT-2026-Q2-COMPL-001', 1, 2,
         N'Clear PDPL DSR backlog within emergency sprint',
         N'تصفية تراكم طلبات أصحاب البيانات ضمن فريق الطوارئ',
         N'Close all 28 backlog DSR cases before any reaches the PDPL Article 4 30-day deadline. 3 Ops analysts seconded. Weekly burn-down report to CCO.',
         N'إغلاق جميع طلبات تراكم 28 حالة قبل أن تصل أي منها إلى مهلة المادة الرابعة (30 يوماً). انتداب 3 محللي عمليات. تقرير احتراق أسبوعي للمدير.',
         N'DPO', N'DPO', CAST(DATEADD(DAY, +12, SYSUTCDATETIME()) AS DATE), N'CRITICAL', N'IN_PROGRESS',
         N'PRIVACY', N'DsrCase', N'DSR-BATCH-Q2'),

        (@org, @mtg09, @compl, N'ACT-2026-Q2-COMPL-002', 2, 3,
         N'Close remaining 5 NCA ECC backup-verification gap controls',
         N'إغلاق الـ 5 ضوابط المتبقية لفجوة التحقق من النسخ الاحتياطية في NCA ECC',
         N'Complete evidence packs for the 5 remaining NCA ECC controls (2-9-3-1 through 2-9-3-5) tied to the backup-verification gap raised in RES-2026-Q1-AUD-002. SAMA Cyber Attestation deadline 2026-06-30.',
         N'إكمال حزم الأدلة للضوابط الخمسة المتبقية في NCA ECC (2-9-3-1 إلى 2-9-3-5) المرتبطة بفجوة التحقق من النسخ الاحتياطية. مهلة شهادة ساما للأمن السيبراني 2026-06-30.',
         N'CISO', N'CISO', CAST(DATEADD(MONTH, +2, SYSUTCDATETIME()) AS DATE), N'HIGH', N'OPEN',
         N'CYBER', N'CyberControl', N'NCA-ECC-2-9-3'),

        (@org, @mtg10, @cyber, N'ACT-2026-Q2-CYB-001', 1, 3,
         N'Deploy FIDO2 to all bastion-host access paths',
         N'نشر FIDO2 على جميع مسارات وصول خوادم الإدارة',
         N'Roll out hardware-backed FIDO2 (Yubikey 5C NFC) to all jump servers, privileged-access workstations, and emergency-break-glass accounts. Per RES-2026-Q2-CYB-001. Aligns to NCA ECC-2 § 2-3-1-1.',
         N'نشر FIDO2 المدعوم بأجهزة (Yubikey 5C NFC) على جميع خوادم القفز ومحطات الوصول المتميز وحسابات الطوارئ. وفق RES-2026-Q2-CYB-001. يتماشى مع الضابط 2-3-1-1 من NCA ECC-2.',
         N'CIO', N'CIO', CAST(DATEADD(MONTH, +2, SYSUTCDATETIME()) AS DATE), N'HIGH', N'IN_PROGRESS',
         N'CYBER', N'CyberControl', N'NCA-ECC-2-3-1-1'),

        (@org, @mtg10, @cyber, N'ACT-2026-Q2-CYB-002', 2, 4,
         N'Weekly CIO burn-down on 6 aged vulnerabilities',
         N'تقرير احتراق أسبوعي لرئيس تقنية المعلومات على الـ 6 ثغرات المتقادمة',
         N'Publish weekly burn-down chart on the 6 vulnerabilities aged >60 days (4 critical CVEs + 2 high CVEs) until cleared. Per RES-2026-Q2-CYB-002.',
         N'نشر مخطط احتراق أسبوعي على الـ 6 ثغرات التي تجاوزت 60 يوماً (4 ثغرات حرجة + 2 عالية) حتى الإغلاق. وفق RES-2026-Q2-CYB-002.',
         N'Vuln Manager', N'Vulnerability Manager', CAST(DATEADD(DAY, +7, SYSUTCDATETIME()) AS DATE), N'HIGH', N'IN_PROGRESS',
         N'CYBER', N'Vulnerability', N'VLN-2026-BATCH'),

        (@org, @mtg11, @priv, N'ACT-2026-Q2-PRIV-001', 1, 2,
         N'Implement DPIA conditions before Marketing AI go-live',
         N'تنفيذ شروط تقييم الأثر قبل تشغيل منصة التخصيص',
         N'Implement: (1) under-18 opt-out default; (2) sensitive-category input blocking; (3) quarterly bias-review cadence; (4) 12-month consent renewal. DPO + CMO co-sign before go-live.',
         N'تنفيذ: (1) الانسحاب الافتراضي لمن دون 18؛ (2) حجب مدخلات الفئات الحساسة؛ (3) وتيرة مراجعة التحيز ربع سنوياً؛ (4) تجديد الموافقة كل 12 شهراً. توقيع مشترك للمسؤول والمدير التسويقي قبل التشغيل.',
         N'Privacy Programme Manager', N'Privacy Programme Manager',
         CAST(DATEADD(MONTH, +1, SYSUTCDATETIME()) AS DATE), N'HIGH', N'IN_PROGRESS',
         N'PRIVACY', N'Dpia', N'DPIA-2026-014'),

        (@org, @mtg11, @priv, N'ACT-2026-Q2-PRIV-002', 2, 3,
         N'File AWS Frankfurt cross-border notification with SDAIA',
         N'إيداع إخطار النقل عبر الحدود إلى AWS فرانكفورت لدى SDAIA',
         N'Prepare and file the cross-border transfer notification with SDAIA for the AWS Frankfurt route within 30 days per PDPL Article 29. Include the BCR text, SCC mapping, and supplementary technical measures evidence.',
         N'إعداد وإيداع إخطار النقل عبر الحدود لدى الهيئة السعودية للبيانات والذكاء الاصطناعي لمسار AWS فرانكفورت خلال 30 يوماً وفق المادة 29 من نظام حماية البيانات. تشمل قواعد الشركة الملزمة وخريطة الشروط التعاقدية القياسية وأدلة التدابير التقنية التكميلية.',
         N'General Counsel', N'General Counsel',
         CAST(DATEADD(DAY, +25, SYSUTCDATETIME()) AS DATE), N'CRITICAL', N'OPEN',
         N'PRIVACY', N'CrossBorderTransfer', N'CBT-2026-007'),

        (@org, @mtg12, @aml, N'ACT-2026-Q2-AML-001', 1, 1,
         N'File 3 SAFIU reports per RES-2026-Q2-AML-001',
         N'إيداع 3 تقارير SAR لدى SAFIU وفق RES-2026-Q2-AML-001',
         N'File SAR-2026-0142 (C-7821 wire layering), SAR-2026-0143 (M-1142 cash structuring), SAR-2026-0144 (trade-based ML) with SAFIU within 5 working days per AML Implementing Regulations Article 70.',
         N'إيداع SAR-2026-0142 (طبقات تحويلات C-7821)، SAR-2026-0143 (هيكلة نقدية M-1142)، SAR-2026-0144 (غسل أموال مستند للتجارة) لدى وحدة التحريات المالية SAFIU خلال 5 أيام عمل وفق المادة 70 من اللائحة التنفيذية لمكافحة غسل الأموال.',
         N'MLRO', N'MLRO', CAST(DATEADD(DAY, +5, SYSUTCDATETIME()) AS DATE), N'CRITICAL', N'IN_PROGRESS',
         N'COMPLIANCE', N'SarFiling', N'SAR-2026-BATCH'),

        (@org, @mtg12, @aml, N'ACT-2026-Q2-AML-002', 2, 2,
         N'Acuity ML vendor performance review — sanctions screening',
         N'مراجعة أداء مورد Acuity ML - فحص العقوبات',
         N'Commission performance review of Acuity ML sanctions-screening engine — current false-positive rate 23% (target <15%). Cover tuning options, alternative vendors, or hybrid approach. Report at next AML Committee.',
         N'تكليف مراجعة أداء محرك فحص العقوبات من Acuity ML - معدل الإيجابيات الكاذبة الحالي 23% (الهدف أقل من 15%). تغطي خيارات الضبط، الموردين البدلاء، أو النهج الهجين. تقرير في اللجنة التالية.',
         N'AML Analyst', N'AML Analyst', CAST(DATEADD(DAY, +14, SYSUTCDATETIME()) AS DATE), N'HIGH', N'OPEN',
         N'TPRM', N'Vendor', N'VEN-ACUITY-ML'),

        (@org, @mtg13, @ai, N'ACT-2026-Q1-AI-001', 1, 2,
         N'Credit Scoring v3 — 5% canary monitoring',
         N'نموذج التقييم الائتماني v3 - مراقبة 5% تجريبي',
         N'Monitor 5% canary rollout for 30 days. Track bias-drift on protected attributes daily. Auto-rollback trigger if drift > 5σ from training distribution. Per RES-2026-Q1-AI-001.',
         N'مراقبة نشر 5% التجريبي لمدة 30 يوماً. تتبع انجراف التحيز على السمات المحمية يومياً. تفعيل إرجاع تلقائي إذا تجاوز الانجراف 5 انحرافات معيارية من توزيع التدريب. وفق RES-2026-Q1-AI-001.',
         N'Chief Data Officer', N'CDO', CAST(DATEADD(DAY, +10, SYSUTCDATETIME()) AS DATE), N'HIGH', N'IN_PROGRESS',
         N'RISK', N'Risk', N'RSK-2026-AI-003'),

        (@org, @mtg13, @ai, N'ACT-2026-Q1-AI-002', 2, 4,
         N'Close SDAIA self-assessment "Human Oversight Documentation" gap',
         N'إغلاق فجوة "توثيق الإشراف البشري" في التقييم الذاتي للهيئة',
         N'Document the human-in-the-loop processes for all consequential AI decisions (credit, hiring, healthcare), with clear escalation paths, override-rate tracking, and review cadence. Lift SDAIA self-assessment from 89/100 to 100/100.',
         N'توثيق عمليات إشراف الإنسان لجميع قرارات الذكاء الاصطناعي الجوهرية (الائتمان، التوظيف، الصحة)، مع مسارات تصعيد واضحة، تتبع معدل التجاوز، ووتيرة المراجعة. رفع التقييم الذاتي للهيئة من 89/100 إلى 100/100.',
         N'AI Compliance Lead', N'AI Compliance Lead',
         CAST(DATEADD(MONTH, +2, SYSUTCDATETIME()) AS DATE), N'NORMAL', N'OPEN',
         N'POLICY', N'Policy', N'POL-AI-OVERSIGHT-001'),

        (@org, @mtg14, @bcm, N'ACT-2026-Q1-BCM-001', 1, 1,
         N'CAPA-2026-074 — investigate DR test 17-minute overrun',
         N'CAPA-2026-074 - التحقيق في تجاوز 17 دقيقة لاختبار التعافي',
         N'Root-cause the 17-minute overrun on Q1 Core Banking DR test (RTO 4h17m vs target 4h). Initial hypothesis: DNS re-propagation. Deliverables: RCA report, control gap (if any), test-plan update for Q2.',
         N'تحديد السبب الجذري لتجاوز 17 دقيقة في اختبار التعافي للنظام البنكي للربع الأول (RTO 4 ساعات و17 دقيقة مقابل الهدف 4 ساعات). الفرضية الأولية: إعادة نشر DNS. المخرجات: تقرير التحليل، فجوة الضبط (إن وُجدت)، تحديث خطة الاختبار للربع الثاني.',
         N'CIO', N'CIO', CAST(DATEADD(MONTH, +2, SYSUTCDATETIME()) AS DATE), N'HIGH', N'IN_PROGRESS',
         N'BCM', N'CapaTask', N'CAPA-2026-074');
END
GO
