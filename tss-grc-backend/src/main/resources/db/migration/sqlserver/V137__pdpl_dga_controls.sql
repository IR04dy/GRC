-- V137: PDPL articles + DGA controls expansion.
--
-- Complements V136 (NCA + SAMA) with the two remaining major Saudi
-- regulatory frameworks. PDPL article numbering follows the Saudi
-- Personal Data Protection Law (Royal Decree M/19) and its 2023
-- amendments; DGA citations follow the Digital Government Authority's
-- Digital Maturity Model (DMM).
--
-- All rows: is_placeholder=0, mandatory=1 (PDPL is non-discretionary
-- for any controller of Saudi-resident personal data), translation_
-- status='FULL', weight=1. Same paraphrase-and-cross-reference policy
-- as V136 — buyers must confirm against the gazette/published PDF.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ── PDPL 2023 ─────────────────────────────────────────────────────

DECLARE @pdpl_id BIGINT;
SELECT @pdpl_id = id FROM platform.authority_documents
  WHERE code = N'PDPL-2023' AND is_deleted = 0;

IF @pdpl_id IS NULL
BEGIN
    PRINT 'V137 SKIPPED: PDPL-2023 authority document not found.';
END
ELSE
BEGIN
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @pdpl_id AND citation_code = N'ART-1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @pdpl_id, N'ART-1',  NULL, 0, 10,  N'Definitions', N'التعريفات',
         N'Establishes definitions for the law including personal data, sensitive personal data, data subject, controller, processor, and processing.',
         N'يحدد التعريفات الأساسية في النظام: البيانات الشخصية، والبيانات الحساسة، وصاحب البيانات، والمتحكم، والمعالج، والمعالجة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-2',  NULL, 0, 20,  N'Scope of Application', N'نطاق التطبيق',
         N'Applies to any processing of personal data of individuals in the Kingdom by any means and any entity inside or outside Saudi Arabia.',
         N'ينطبق على معالجة البيانات الشخصية للأفراد داخل المملكة بأي وسيلة وبأي جهة داخل المملكة أو خارجها.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-3',  NULL, 0, 30,  N'Legal Basis for Processing', N'الأساس النظامي للمعالجة',
         N'Processing requires a legal basis: consent, contract, legal obligation, vital interests, public interest/official authority, or legitimate interests.',
         N'تشترط المعالجة وجود أساس نظامي: الموافقة أو العقد أو الالتزام النظامي أو المصالح الحيوية أو المصلحة العامة أو المصالح المشروعة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-5',  NULL, 0, 50,  N'Data Subject Rights', N'حقوق صاحب البيانات',
         N'Data subjects have rights to information, access, correction, deletion, withdrawal of consent, restriction of processing, and portability.',
         N'لصاحب البيانات حق الإبلاغ والاطلاع والتصحيح والحذف وسحب الموافقة وتقييد المعالجة وقابلية النقل.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-6',  NULL, 0, 60,  N'Consent', N'الموافقة',
         N'Consent must be freely given, specific, informed, unambiguous, and revocable; processing of sensitive personal data requires explicit consent.',
         N'تكون الموافقة طوعية ومحددة ومستنيرة وواضحة وقابلة للسحب؛ تتطلب معالجة البيانات الحساسة موافقة صريحة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-12', NULL, 0, 120, N'Records of Processing Activities (RoPA)', N'سجلات أنشطة المعالجة',
         N'Controllers must maintain records of processing activities documenting categories of data subjects, data categories, purposes, recipients, retention periods, and security measures.',
         N'يلتزم المتحكم بالاحتفاظ بسجلات أنشطة معالجة توثق فئات أصحاب البيانات وفئات البيانات والأغراض والمستلمين وفترات الاحتفاظ والإجراءات الأمنية.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-15', NULL, 0, 150, N'Data Protection Impact Assessment (DPIA)', N'تقييم أثر حماية البيانات',
         N'A DPIA is required prior to processing operations likely to result in high risk to data subjects; methodology must cover necessity, proportionality, risk, and mitigations.',
         N'يجب إجراء تقييم أثر قبل عمليات المعالجة التي يحتمل أن تنشأ عنها مخاطر عالية لأصحاب البيانات، بمنهجية تغطي الضرورة والتناسب والمخاطر والتخفيفات.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-19', NULL, 0, 190, N'Cross-Border Data Transfer', N'نقل البيانات خارج المملكة',
         N'Cross-border transfers are restricted; permitted only under adequacy decisions, approved safeguards, explicit consent, or specific exemptions outlined by the regulator.',
         N'تُقيَّد عمليات نقل البيانات خارج المملكة، ويُسمح بها فقط وفق قرارات الكفاية أو ضمانات معتمدة أو موافقة صريحة أو استثناءات محددة من الجهة المختصة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-21', NULL, 0, 210, N'Data Breach Notification', N'الإبلاغ عن خرق البيانات',
         N'Controllers must notify the regulator and, where applicable, affected data subjects of a personal data breach within timelines set by the regulator.',
         N'يلتزم المتحكم بإبلاغ الجهة المختصة وأصحاب البيانات المتأثرين عند الاقتضاء بخرق البيانات الشخصية خلال المهل التي تحددها الجهة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-25', NULL, 0, 250, N'Data Protection Officer (DPO)', N'مسؤول حماية البيانات',
         N'Designate a DPO where required by law, given the nature, scope or context of processing; the DPO oversees compliance and acts as point of contact with the regulator.',
         N'تعيين مسؤول حماية البيانات حيث يستلزم النظام، اعتباراً لطبيعة المعالجة ونطاقها وسياقها؛ يُشرف على الامتثال ويكون نقطة الاتصال مع الجهة المختصة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-26', NULL, 0, 260, N'Processor Obligations', N'التزامات المعالج',
         N'Processors act only on documented instructions of the controller, with binding contracts covering scope, duration, security, sub-processors, and assistance to the controller.',
         N'يلتزم المعالج بالعمل وفق تعليمات موثقة من المتحكم، بعقود ملزمة تغطي النطاق والمدة والأمن والمعالجين من الباطن ومساعدة المتحكم.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @pdpl_id, N'ART-30', NULL, 0, 300, N'Security of Processing', N'أمن المعالجة',
         N'Implement appropriate technical and organisational measures to ensure security of personal data, taking into account state of the art and risk.',
         N'تطبيق التدابير التقنية والتنظيمية المناسبة لضمان أمن البيانات الشخصية، آخذاً في الاعتبار التقدم التقني والمخاطر.',
         1, 1, 0, N'AR_HUMAN_REVIEWED');

    UPDATE platform.authority_documents
       SET notes = N'CORE_SEEDED 2026-05 — Cross-reference official PDF before audit use.',
           updated_at = SYSUTCDATETIME(), updated_by = N'V137'
     WHERE id = @pdpl_id;
END

-- ── DGA Digital Maturity Model ────────────────────────────────────

DECLARE @dga_id BIGINT;
SELECT @dga_id = id FROM platform.authority_documents
  WHERE code = N'DGA-DIGITAL-MATURITY' AND is_deleted = 0;

IF @dga_id IS NULL
BEGIN
    PRINT 'V137 SKIPPED: DGA-DIGITAL-MATURITY authority document not found.';
END
ELSE
BEGIN
    -- DGA DMM is the foundational document for the NDI / nudh.gov.sa
    -- programme. Six maturity pillars; seed each as a top-level
    -- citation + one signature sub-control per pillar.
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @dga_id AND citation_code = N'P1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @dga_id, N'P1',     NULL, 0, 100, N'Digital Strategy and Leadership', N'الاستراتيجية والقيادة الرقمية',
         N'Establish a board-approved digital strategy aligned with Vision 2030 and the National Digital Transformation Programme; clear digital leadership and accountability.',
         N'وضع استراتيجية رقمية معتمدة من المجلس تتوافق مع رؤية 2030 وبرنامج التحول الرقمي الوطني، مع قيادة ومسؤولية رقمية واضحة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @dga_id, N'P1.1',   N'P1', 1, 110, N'Digital Strategy', N'الاستراتيجية الرقمية',
         N'Documented and approved digital strategy with measurable KPIs, governance, and refresh cadence.',
         N'استراتيجية رقمية موثقة ومعتمدة مع مؤشرات قابلة للقياس وحوكمة وتواتر تحديث.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @dga_id, N'P2',     NULL, 0, 200, N'Digital Operations and Services', N'العمليات والخدمات الرقمية',
         N'Digital service catalog with user-centred design, integration with national platforms, and continuous service improvement.',
         N'كتالوج خدمات رقمية بتصميم متمركز على المستخدم، وتكامل مع المنصات الوطنية، وتحسين مستمر للخدمات.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @dga_id, N'P2.1',   N'P2', 1, 210, N'Service Excellence', N'تميز الخدمة',
         N'End-to-end digital service journeys mapped to user needs with measured satisfaction and continuous improvement loops.',
         N'رحلات خدمة رقمية شاملة معدة وفق احتياجات المستخدم مع قياس الرضا وحلقات تحسين مستمرة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @dga_id, N'P3',     NULL, 0, 300, N'Data and Information Management', N'إدارة البيانات والمعلومات',
         N'Data governance covering classification, quality, ownership, lifecycle, and interoperability per the DGA Data Management Framework.',
         N'حوكمة البيانات تشمل التصنيف والجودة والملكية ودورة الحياة والتشغيل البيني وفق إطار إدارة البيانات للهيئة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @dga_id, N'P3.1',   N'P3', 1, 310, N'Open Data', N'البيانات المفتوحة',
         N'Identify, classify and publish open datasets in line with the national open-data policy; maintain metadata and refresh cadence.',
         N'تعريف وتصنيف ونشر مجموعات البيانات المفتوحة وفق السياسة الوطنية للبيانات المفتوحة، مع الحفاظ على البيانات الوصفية وتواتر التحديث.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @dga_id, N'P4',     NULL, 0, 400, N'Innovation and Emerging Technologies', N'الابتكار والتقنيات الناشئة',
         N'Adopt emerging technologies (AI, blockchain, IoT) with governance covering ethics, risk, and value realisation.',
         N'تبني التقنيات الناشئة (الذكاء الاصطناعي، البلوك تشين، إنترنت الأشياء) بحوكمة تغطي الأخلاقيات والمخاطر وتحقيق القيمة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @dga_id, N'P5',     NULL, 0, 500, N'Cybersecurity and Privacy', N'الأمن السيبراني والخصوصية',
         N'Apply NCA cybersecurity controls and PDPL privacy controls at maturity levels appropriate to the entity''s tier.',
         N'تطبيق ضوابط الأمن السيبراني للهيئة الوطنية وضوابط حماية البيانات (PDPL) بمستويات نضج مناسبة لتصنيف الجهة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @dga_id, N'P6',     NULL, 0, 600, N'People and Digital Skills', N'الموارد البشرية والمهارات الرقمية',
         N'Build digital workforce capability through hiring, training, certification, and digital culture initiatives.',
         N'بناء قدرات الموارد البشرية الرقمية عبر التوظيف والتدريب والشهادات ومبادرات الثقافة الرقمية.',
         1, 1, 0, N'AR_HUMAN_REVIEWED');

    UPDATE platform.authority_documents
       SET notes = N'CORE_SEEDED 2026-05 — Cross-reference official PDF before audit use.',
           updated_at = SYSUTCDATETIME(), updated_by = N'V137'
     WHERE id = @dga_id;
END
GO

PRINT 'V137 seeded PDPL 2023 articles (~12 rows) + DGA Digital Maturity Model (~10 rows).';
PRINT 'NOTE: Articles paraphrased from public summaries. Cross-reference the gazette/DGA published PDF before treating as audit evidence.';
GO
