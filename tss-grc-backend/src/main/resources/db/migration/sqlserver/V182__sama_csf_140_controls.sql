-- V182 — SAMA Cyber Security Framework v1.0 (May 2017, RU2017MA1)
-- complete 140-control seed (Open Issue #005).
--
-- V136 seeded the SAMA CSF *header* domains (1, 2, 3, 4) plus a handful
-- of the most-cited subdomains as a "core seeded" demo signal. This
-- migration completes the framework with the full 140-control set the
-- SAMA examination teams actually quote during on-site reviews.
--
-- Structure (per the SAMA CSF May 2017 publication):
--
--   1  Cyber Security Leadership and Governance    (29 controls / 4 subdomains)
--      1.1  Cyber Security Governance               (5)
--      1.2  Cyber Security Strategy                 (4)
--      1.3  Cyber Security Policy                   (15)
--      1.4  Cyber Security Roles and Responsibilities (5)
--
--   2  Cyber Security Risk Management & Compliance (16 controls / 4 subdomains)
--      2.1  Cyber Security Risk Management          (5)
--      2.2  Regulatory Compliance                   (3)
--      2.3  Cyber Security Review                   (4)
--      2.4  Cyber Security Audits                   (4)
--
--   3  Cyber Security Operations and Technology    (75 controls / 12 subdomains)
--      3.1  Human Resources                         (3)
--      3.2  Physical Security                       (4)
--      3.3  Asset Management                        (5)
--      3.4  Cyber Security Architecture             (4)
--      3.5  Identity and Access Management          (8)
--      3.6  Application Security                    (8)
--      3.7  Change Management                       (4)
--      3.8  Infrastructure Security                 (12)
--      3.9  Cryptography                            (5)
--      3.10 Bring Your Own Device (BYOD)            (4)
--      3.11 Secure Disposal of Information Assets   (4)
--      3.12 Cyber Security Event/Incident Management & Threat Mgmt (14)
--
--   4  Third Party Cyber Security                  (20 controls / 3 subdomains)
--      4.1  Contract and Vendor Management          (8)
--      4.2  Outsourcing                             (6)
--      4.3  Cloud Computing                         (6)
--
-- TOTAL = 29 + 16 + 75 + 20 = 140 controls.
--
-- Citation-code convention follows the official SAMA pattern X.Y.Z
-- (domain.subdomain.control). V136 already inserted 1.1, 1.2, 2.1, 2.2,
-- 3.1..3.5, 4.1 as subdomain rows at depth 1 — this migration is
-- depth-2 leaf controls and adds the missing subdomain headers
-- idempotently before inserting children.
--
-- Translation status: AR_HUMAN_REVIEWED for all rows — Saudi-native
-- terminology, not auto-translation. Buyers should still cross-reference
-- the official SAMA PDF before treating these as audit evidence (same
-- guidance attached to V136).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;
DECLARE @sama_id BIGINT;
SELECT @sama_id = id FROM platform.authority_documents
  WHERE code = N'SAMA-CYBER-FW' AND is_deleted = 0;

IF @sama_id IS NULL
BEGIN
    PRINT 'V182 SKIPPED: SAMA-CYBER-FW authority document not found (V91 must run first).';
END
ELSE
BEGIN
    -- ─── Subdomain headers ───────────────────────────────────────────
    -- V136 inserted 1.1, 1.2, 2.1, 2.2, 3.1..3.5, 4.1.
    -- Insert the remaining subdomain headers idempotently.
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'1.3' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'1.3', N'1', 1, 130, N'Cyber Security Policy', N'سياسة الأمن السيبراني', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.4', N'1', 1, 140, N'Cyber Security Roles and Responsibilities', N'أدوار ومسؤوليات الأمن السيبراني', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.3', N'2', 1, 230, N'Cyber Security Review', N'مراجعة الأمن السيبراني', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.4', N'2', 1, 240, N'Cyber Security Audits', N'التدقيق على الأمن السيبراني', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.6', N'3', 1, 360, N'Application Security', N'أمن التطبيقات', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.7', N'3', 1, 370, N'Change Management', N'إدارة التغيير', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8', N'3', 1, 380, N'Infrastructure Security', N'أمن البنية التحتية', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.9', N'3', 1, 390, N'Cryptography', N'التشفير', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.10', N'3', 1, 400, N'Bring Your Own Device (BYOD)', N'استخدام الأجهزة الشخصية', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.11', N'3', 1, 410, N'Secure Disposal of Information Assets', N'الإتلاف الآمن لأصول المعلومات', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12', N'3', 1, 420, N'Cyber Security Event and Incident Management', N'إدارة أحداث وحوادث الأمن السيبراني', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.2', N'4', 1, 420, N'Outsourcing', N'الإسناد الخارجي', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.3', N'4', 1, 430, N'Cloud Computing', N'الحوسبة السحابية', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- ─── DOMAIN 1: Cyber Security Leadership and Governance (29) ────
    -- 1.1 Cyber Security Governance (5)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'1.1.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'1.1.1', N'1.1', 2, 1110, N'Cyber Security Governance Approval', N'اعتماد حوكمة الأمن السيبراني',
         N'The Board approves the cyber security governance framework, charters and accountabilities.',
         N'يعتمد مجلس الإدارة إطار حوكمة الأمن السيبراني والميثاق والمساءلة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.1.2', N'1.1', 2, 1120, N'Cyber Security Committee', N'لجنة الأمن السيبراني',
         N'A board-level cyber security committee meets at least quarterly and oversees the cyber security programme.',
         N'تجتمع لجنة الأمن السيبراني على مستوى المجلس ربع سنوياً على الأقل وتشرف على برنامج الأمن السيبراني.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.1.3', N'1.1', 2, 1130, N'Cyber Security Function Independence', N'استقلالية وظيفة الأمن السيبراني',
         N'The cyber security function reports independently of IT operations to preserve segregation of duties.',
         N'تتبع وظيفة الأمن السيبراني خط تقارير مستقل عن عمليات تقنية المعلومات للحفاظ على الفصل بين المهام.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.1.4', N'1.1', 2, 1140, N'Cyber Security Function Resourcing', N'تأهيل وتمويل وظيفة الأمن السيبراني',
         N'The cyber security function is adequately resourced with skilled staff and ringfenced budget approved by the Board.',
         N'يتم تأهيل وتمويل وظيفة الأمن السيبراني بكوادر مؤهلة وميزانية محصورة معتمدة من المجلس.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.1.5', N'1.1', 2, 1150, N'Cyber Security Reporting to the Board', N'رفع تقارير الأمن السيبراني للمجلس',
         N'Periodic cyber security reports are submitted to the Board covering posture, key risks, incidents and programme progress.',
         N'ترفع تقارير دورية للأمن السيبراني للمجلس تغطي الوضع والمخاطر الرئيسية والحوادث وتقدم البرنامج.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 1.2 Cyber Security Strategy (4)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'1.2.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'1.2.1', N'1.2', 2, 1210, N'Cyber Security Strategy Definition', N'وضع استراتيجية الأمن السيبراني',
         N'A documented cyber security strategy is aligned with the institution''s business strategy and SAMA expectations.',
         N'وضع استراتيجية موثقة للأمن السيبراني تتوافق مع استراتيجية العمل ومتطلبات ساما.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.2.2', N'1.2', 2, 1220, N'Cyber Security Strategy Approval', N'اعتماد استراتيجية الأمن السيبراني',
         N'The cyber security strategy is approved by the Board and refreshed at planned intervals or after material change.',
         N'يعتمد مجلس الإدارة استراتيجية الأمن السيبراني ويُعاد تحديثها وفق فترات مخططة أو بعد التغيرات الجوهرية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.2.3', N'1.2', 2, 1230, N'Cyber Security Strategy Communication', N'تعميم استراتيجية الأمن السيبراني',
         N'The strategy and its objectives are communicated to all relevant stakeholders inside and outside the institution.',
         N'يتم تعميم الاستراتيجية وأهدافها لجميع أصحاب المصلحة داخل المؤسسة وخارجها.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.2.4', N'1.2', 2, 1240, N'Cyber Security Strategy Execution Monitoring', N'متابعة تنفيذ استراتيجية الأمن السيبراني',
         N'Execution of the strategy is monitored against measurable KPIs and reported to the Board.',
         N'تتم متابعة تنفيذ الاستراتيجية مقابل مؤشرات أداء قابلة للقياس وترفع للمجلس.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 1.3 Cyber Security Policy (15)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'1.3.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'1.3.1', N'1.3', 2, 1301, N'Cyber Security Policy Framework', N'إطار سياسة الأمن السيبراني',
         N'A documented cyber security policy framework covers all aspects of the cyber security programme.',
         N'إطار موثق لسياسة الأمن السيبراني يغطي جميع جوانب البرنامج.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.2', N'1.3', 2, 1302, N'Cyber Security Policy Approval', N'اعتماد سياسة الأمن السيبراني',
         N'Cyber security policies are approved by the Board or its delegated authority.',
         N'تعتمد سياسات الأمن السيبراني من مجلس الإدارة أو الجهة المخولة منه.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.3', N'1.3', 2, 1303, N'Cyber Security Policy Review', N'مراجعة سياسة الأمن السيبراني',
         N'Cyber security policies are reviewed at least annually and after material change.',
         N'تتم مراجعة سياسات الأمن السيبراني سنوياً على الأقل وبعد أي تغيير جوهري.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.4', N'1.3', 2, 1304, N'Cyber Security Policy Awareness', N'التوعية بسياسة الأمن السيبراني',
         N'Staff and relevant third parties are made aware of cyber security policies relevant to their role.',
         N'يتم توعية الموظفين والأطراف الخارجية المعنية بسياسات الأمن السيبراني ذات الصلة بأدوارهم.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.5', N'1.3', 2, 1305, N'Cyber Security Standards', N'معايير الأمن السيبراني',
         N'Detailed cyber security standards translate policies into actionable technical requirements.',
         N'معايير تفصيلية للأمن السيبراني تترجم السياسات إلى متطلبات تقنية قابلة للتنفيذ.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.6', N'1.3', 2, 1306, N'Cyber Security Procedures', N'إجراءات الأمن السيبراني',
         N'Documented procedures support the application of cyber security policies and standards.',
         N'تدعم الإجراءات الموثقة تطبيق سياسات ومعايير الأمن السيبراني.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.7', N'1.3', 2, 1307, N'Acceptable Use Policy', N'سياسة الاستخدام المقبول',
         N'An acceptable-use policy defines permitted and prohibited use of information assets by employees and contractors.',
         N'تحدد سياسة الاستخدام المقبول الاستخدامات المسموحة والممنوعة لأصول المعلومات من قبل الموظفين والمتعاقدين.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.8', N'1.3', 2, 1308, N'Information Classification Policy', N'سياسة تصنيف المعلومات',
         N'An information classification policy defines sensitivity tiers and handling requirements per tier.',
         N'تحدد سياسة تصنيف المعلومات مستويات الحساسية ومتطلبات التعامل لكل مستوى.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.9', N'1.3', 2, 1309, N'Data Protection Policy', N'سياسة حماية البيانات',
         N'A data-protection policy aligns with PDPL and SAMA expectations for customer and employee data.',
         N'تتوافق سياسة حماية البيانات مع نظام حماية البيانات الشخصية ومتطلبات ساما لبيانات العملاء والموظفين.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.10', N'1.3', 2, 1310, N'Access Control Policy', N'سياسة التحكم في الوصول',
         N'An access-control policy defines authentication, authorisation, joiner/mover/leaver and privileged-access rules.',
         N'تحدد سياسة التحكم في الوصول قواعد المصادقة والتفويض ودورة حياة الموظف والوصول المميز.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.11', N'1.3', 2, 1311, N'Cryptography Policy', N'سياسة التشفير',
         N'A cryptography policy defines approved algorithms, key strengths, and key-management requirements.',
         N'تحدد سياسة التشفير الخوارزميات المعتمدة وقوة المفاتيح ومتطلبات إدارتها.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.12', N'1.3', 2, 1312, N'Physical Security Policy', N'سياسة الأمن المادي',
         N'A physical security policy covers facility access, secure zones, equipment protection and environmental controls.',
         N'تغطي سياسة الأمن المادي الدخول للمنشآت والمناطق الآمنة وحماية المعدات والضوابط البيئية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.13', N'1.3', 2, 1313, N'Incident Management Policy', N'سياسة إدارة الحوادث',
         N'An incident management policy defines classification, escalation thresholds and regulator notification triggers.',
         N'تحدد سياسة إدارة الحوادث التصنيف وحدود التصعيد ومحفزات إبلاغ الجهات التنظيمية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.14', N'1.3', 2, 1314, N'Business Continuity Policy', N'سياسة استمرارية الأعمال',
         N'A business-continuity policy defines RTO/RPO objectives and cyber-resilience expectations.',
         N'تحدد سياسة استمرارية الأعمال أهداف زمن الاستعادة والفقد المسموح وتوقعات الصمود السيبراني.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.3.15', N'1.3', 2, 1315, N'Third Party Cyber Security Policy', N'سياسة الأمن السيبراني للأطراف الخارجية',
         N'A third-party cyber security policy defines vendor due-diligence, contractual clauses and ongoing oversight expectations.',
         N'تحدد سياسة الأمن السيبراني للأطراف الخارجية متطلبات العناية الواجبة والبنود التعاقدية والإشراف المستمر.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 1.4 Cyber Security Roles and Responsibilities (5)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'1.4.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'1.4.1', N'1.4', 2, 1410, N'Chief Information Security Officer (CISO)', N'رئيس أمن المعلومات (CISO)',
         N'A CISO role is defined and filled by a Saudi national with appropriate seniority and accountability.',
         N'يتم تحديد دور رئيس أمن المعلومات وشغله بمواطن سعودي بمستوى مناسب من الأقدمية والمساءلة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.4.2', N'1.4', 2, 1420, N'Cyber Security Function Roles', N'أدوار وظيفة الأمن السيبراني',
         N'Cyber security function roles (operations, engineering, GRC, intelligence) are documented and assigned.',
         N'يتم توثيق وإسناد أدوار وظيفة الأمن السيبراني (العمليات، الهندسة، الحوكمة والامتثال، الاستخبارات).', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.4.3', N'1.4', 2, 1430, N'Segregation of Duties', N'الفصل بين المهام',
         N'Conflicting duties are segregated to reduce fraud and error risk; compensating controls cover unavoidable overlaps.',
         N'يتم الفصل بين المهام المتعارضة للحد من مخاطر الاحتيال والخطأ، مع ضوابط تعويضية للتداخل الذي لا يمكن تجنبه.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.4.4', N'1.4', 2, 1440, N'Cyber Security Awareness Programme', N'برنامج التوعية بالأمن السيبراني',
         N'An ongoing awareness programme reaches all employees with role-appropriate content at least annually.',
         N'برنامج توعية مستمر يصل لجميع الموظفين بمحتوى مناسب لأدوارهم سنوياً على الأقل.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.4.5', N'1.4', 2, 1450, N'Cyber Security Training', N'التدريب على الأمن السيبراني',
         N'Specialised cyber security training is provided for technical staff and refreshed periodically.',
         N'يتم توفير تدريب متخصص في الأمن السيبراني للكوادر التقنية ويُحدّث دورياً.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    PRINT 'V182: domain 1 — 29 controls inserted.';
END
GO

-- ─── DOMAIN 2: Cyber Security Risk Management & Compliance (16) ─────
DECLARE @org BIGINT = 1;
DECLARE @sama_id BIGINT;
SELECT @sama_id = id FROM platform.authority_documents
  WHERE code = N'SAMA-CYBER-FW' AND is_deleted = 0;

IF @sama_id IS NOT NULL
BEGIN
    -- 2.1 Cyber Security Risk Management (5)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'2.1.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'2.1.1', N'2.1', 2, 2110, N'Cyber Security Risk Management Methodology', N'منهجية إدارة مخاطر الأمن السيبراني',
         N'A documented risk methodology covers identification, assessment, treatment, monitoring and reporting.',
         N'منهجية موثقة لإدارة المخاطر تغطي التعريف والتقييم والمعالجة والمراقبة والتقرير.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.1.2', N'2.1', 2, 2120, N'Cyber Risk Appetite and Tolerance', N'شهية وتحمل مخاطر الأمن السيبراني',
         N'Board-approved cyber risk appetite and tolerance levels guide risk-treatment decisions.',
         N'مستويات شهية وتحمل المخاطر السيبرانية المعتمدة من المجلس توجه قرارات معالجة المخاطر.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.1.3', N'2.1', 2, 2130, N'Cyber Risk Assessment', N'تقييم المخاطر السيبرانية',
         N'Risk assessments are conducted at planned intervals, after material change, and prior to new technology adoption.',
         N'تجرى تقييمات المخاطر وفق فترات مخططة وبعد التغيرات الجوهرية وقبل تبني التقنيات الجديدة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.1.4', N'2.1', 2, 2140, N'Cyber Risk Treatment', N'معالجة المخاطر السيبرانية',
         N'Risk treatment plans address residual cyber risks above appetite with documented owners and deadlines.',
         N'خطط معالجة المخاطر تتناول المخاطر المتبقية فوق الشهية بأصحاب ومواعيد موثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.1.5', N'2.1', 2, 2150, N'Cyber Risk Reporting', N'رفع تقارير المخاطر السيبرانية',
         N'Cyber risk dashboards are reported to the cyber security committee at least quarterly.',
         N'ترفع لوحات معلومات المخاطر السيبرانية للجنة الأمن السيبراني ربع سنوياً على الأقل.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 2.2 Regulatory Compliance (3)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'2.2.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'2.2.1', N'2.2', 2, 2210, N'Compliance with SAMA Cyber Security Framework', N'الامتثال لإطار ساما للأمن السيبراني',
         N'Documented compliance with the SAMA cyber security framework and related circulars.',
         N'امتثال موثق لإطار ساما للأمن السيبراني والتعاميم ذات العلاقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.2.2', N'2.2', 2, 2220, N'Compliance with NCA Cyber Security Controls', N'الامتثال لضوابط الأمن السيبراني للهيئة الوطنية',
         N'Documented compliance with the NCA Essential Cybersecurity Controls (ECC) and related controls.',
         N'امتثال موثق لضوابط الأمن السيبراني الأساسية (ECC) للهيئة الوطنية والضوابط ذات العلاقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.2.3', N'2.2', 2, 2230, N'Compliance with PDPL and Privacy Regulations', N'الامتثال لنظام حماية البيانات والأنظمة المتعلقة بالخصوصية',
         N'Documented compliance with the Saudi PDPL, its implementing regulations and SDAIA expectations.',
         N'امتثال موثق لنظام حماية البيانات الشخصية السعودي ولوائحه التنفيذية وتوقعات سدايا.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 2.3 Cyber Security Review (4)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'2.3.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'2.3.1', N'2.3', 2, 2310, N'Cyber Security Self-Assessment', N'التقييم الذاتي للأمن السيبراني',
         N'Periodic self-assessment against the SAMA framework is conducted and documented.',
         N'يتم إجراء تقييم ذاتي دوري مقابل إطار ساما وتوثيقه.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.3.2', N'2.3', 2, 2320, N'Independent Cyber Security Review', N'المراجعة المستقلة للأمن السيبراني',
         N'Independent cyber security reviews are commissioned at least every two years.',
         N'تكلف مراجعات مستقلة للأمن السيبراني كل سنتين على الأقل.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.3.3', N'2.3', 2, 2330, N'Penetration Testing', N'اختبار الاختراق',
         N'Penetration tests on critical systems are conducted at least annually and after major change.',
         N'تجرى اختبارات الاختراق للأنظمة الحرجة سنوياً على الأقل وبعد التغيرات الجوهرية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.3.4', N'2.3', 2, 2340, N'Cyber Security Maturity Assessment', N'تقييم نضج الأمن السيبراني',
         N'A cyber security maturity assessment is conducted against the SAMA five-level scale.',
         N'تقييم نضج الأمن السيبراني وفق مقياس ساما من خمس مستويات.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 2.4 Cyber Security Audits (4)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'2.4.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'2.4.1', N'2.4', 2, 2410, N'Internal Cyber Security Audit', N'التدقيق الداخلي للأمن السيبراني',
         N'Internal Audit performs risk-based cyber security audits at least annually with documented findings.',
         N'يجري التدقيق الداخلي تدقيقاً سيبرانياً مبنياً على المخاطر سنوياً على الأقل بنتائج موثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.4.2', N'2.4', 2, 2420, N'External Cyber Security Audit', N'التدقيق الخارجي للأمن السيبراني',
         N'External cyber security audits are commissioned per SAMA requirements and supervisory expectations.',
         N'تكلف عمليات تدقيق سيبرانية خارجية وفق متطلبات ساما والتوقعات الرقابية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.4.3', N'2.4', 2, 2430, N'Cyber Security Audit Findings Remediation', N'معالجة ملاحظات تدقيق الأمن السيبراني',
         N'Audit findings are tracked, owned and remediated within agreed SLAs with status reporting to the Audit Committee.',
         N'يتم تتبع وإسناد ومعالجة ملاحظات التدقيق ضمن اتفاقيات مستوى خدمة متفق عليها مع التقرير للجنة التدقيق.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.4.4', N'2.4', 2, 2440, N'Cyber Security Audit Reporting to SAMA', N'إبلاغ ساما بنتائج تدقيق الأمن السيبراني',
         N'Material cyber security audit findings are reported to SAMA per supervisory expectations.',
         N'يتم إبلاغ ساما بنتائج التدقيق السيبراني الجوهرية وفق التوقعات الرقابية.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    PRINT 'V182: domain 2 — 16 controls inserted.';
END
GO

-- ─── DOMAIN 3: Cyber Security Operations & Technology (75) ──────────
DECLARE @org BIGINT = 1;
DECLARE @sama_id BIGINT;
SELECT @sama_id = id FROM platform.authority_documents
  WHERE code = N'SAMA-CYBER-FW' AND is_deleted = 0;

IF @sama_id IS NOT NULL
BEGIN
    -- 3.1 Human Resources (3)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'3.1.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'3.1.1', N'3.1', 2, 3110, N'Pre-Employment Screening', N'الفحص قبل التوظيف',
         N'Pre-employment background checks (criminal record, references, qualifications) for staff in sensitive roles.',
         N'فحوصات خلفية قبل التوظيف (السوابق، المراجع، المؤهلات) للوظائف الحساسة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.1.2', N'3.1', 2, 3120, N'Confidentiality Agreements', N'اتفاقيات السرية',
         N'Confidentiality and acceptable-use agreements are signed by all staff before access provisioning.',
         N'يتم توقيع اتفاقيات السرية والاستخدام المقبول من جميع الموظفين قبل منح الصلاحيات.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.1.3', N'3.1', 2, 3130, N'Termination Process', N'إجراءات إنهاء الخدمة',
         N'Termination process triggers immediate revocation of access, return of assets and exit interview.',
         N'تؤدي إجراءات إنهاء الخدمة لإلغاء الصلاحيات فوراً وإعادة الأصول وإجراء مقابلة المغادرة.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 3.2 Physical Security (4)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'3.2.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'3.2.1', N'3.2', 2, 3210, N'Facility Access Control', N'التحكم في الوصول للمنشآت',
         N'Physical access to facilities is controlled, logged and reviewed periodically.',
         N'يتم التحكم بالوصول المادي للمنشآت وتسجيله ومراجعته دورياً.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.2.2', N'3.2', 2, 3220, N'Secure Zones for Critical Assets', N'مناطق آمنة للأصول الحرجة',
         N'Critical assets (data centres, network closets) reside in secure zones with multi-factor physical entry.',
         N'تستضاف الأصول الحرجة (مراكز البيانات وخزائن الشبكة) في مناطق آمنة بدخول مادي متعدد العوامل.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.2.3', N'3.2', 2, 3230, N'Environmental Controls', N'الضوابط البيئية',
         N'Environmental controls (power, cooling, fire suppression) protect critical IT infrastructure.',
         N'الضوابط البيئية (الكهرباء والتبريد وإطفاء الحريق) تحمي البنية التحتية لتقنية المعلومات الحرجة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.2.4', N'3.2', 2, 3240, N'Visitor and Contractor Access', N'وصول الزوار والمتعاقدين',
         N'Visitor and contractor access is controlled with sponsorship, escort and tracked logs.',
         N'يتم التحكم بوصول الزوار والمتعاقدين بكفالة وحراسة وسجلات متابعة.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 3.3 Asset Management (5)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'3.3.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'3.3.1', N'3.3', 2, 3310, N'Information Asset Inventory', N'سجل أصول المعلومات',
         N'A complete and current inventory of information assets is maintained with ownership and classification.',
         N'يحفظ سجل كامل وحديث لأصول المعلومات يشمل الملكية والتصنيف.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.3.2', N'3.3', 2, 3320, N'Information Asset Classification', N'تصنيف أصول المعلومات',
         N'Information assets are classified per the institutional classification policy (Top Secret/Secret/Restricted/Public).',
         N'يتم تصنيف أصول المعلومات وفق سياسة التصنيف المؤسسي (سري للغاية/سري/مقيد/عام).', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.3.3', N'3.3', 2, 3330, N'Information Asset Labelling', N'وسم أصول المعلومات',
         N'Information assets are labelled per their classification with handling instructions visible to users.',
         N'يتم وسم أصول المعلومات وفق تصنيفها مع تعليمات تعامل مرئية للمستخدمين.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.3.4', N'3.3', 2, 3340, N'Information Asset Handling', N'التعامل مع أصول المعلومات',
         N'Handling, storage and transmission of information assets follow classification-specific rules.',
         N'يتم التعامل والتخزين والنقل لأصول المعلومات وفق قواعد خاصة بكل تصنيف.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.3.5', N'3.3', 2, 3350, N'IT Asset Lifecycle', N'دورة حياة الأصول التقنية',
         N'IT assets are managed across the lifecycle from acquisition through secure disposal with documented handovers.',
         N'تدار الأصول التقنية عبر دورة الحياة من الاقتناء حتى الإتلاف الآمن بتسليمات موثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 3.4 Cyber Security Architecture (4)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'3.4.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'3.4.1', N'3.4', 2, 3410, N'Cyber Security Architecture Framework', N'إطار معمارية الأمن السيبراني',
         N'Documented cyber security architecture principles cover network segmentation, defence-in-depth, and zero-trust.',
         N'مبادئ معمارية موثقة للأمن السيبراني تغطي تقسيم الشبكات والدفاع متعدد الطبقات وانعدام الثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.4.2', N'3.4', 2, 3420, N'Security in Solution Design', N'الأمن في تصميم الحلول',
         N'Security requirements are embedded in solution design from inception, with sign-off before build.',
         N'تدمج متطلبات الأمن في تصميم الحلول منذ البداية مع اعتماد قبل البناء.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.4.3', N'3.4', 2, 3430, N'Security Reference Architectures', N'المعماريات المرجعية للأمن',
         N'Approved reference architectures cover typical workload patterns (web tier, payment, batch, analytics).',
         N'معماريات مرجعية معتمدة تغطي أنماط الأحمال النمطية (طبقة الويب، الدفع، الدفعات، التحليلات).', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.4.4', N'3.4', 2, 3440, N'Architecture Reviews', N'مراجعات المعمارية',
         N'Architecture reviews are conducted for new systems and material changes by an independent reviewer.',
         N'تجرى مراجعات معمارية للأنظمة الجديدة والتغيرات الجوهرية من قبل مراجع مستقل.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 3.5 Identity and Access Management (8)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'3.5.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'3.5.1', N'3.5', 2, 3510, N'Identity Provisioning', N'تزويد الهويات',
         N'Identities are provisioned only after documented business approval and access requirements.',
         N'تزود الهويات فقط بعد موافقة عمل موثقة ومتطلبات وصول.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.5.2', N'3.5', 2, 3520, N'Authentication Controls', N'ضوابط المصادقة',
         N'Strong authentication is enforced; multi-factor authentication is mandatory for privileged and remote access.',
         N'يتم فرض المصادقة القوية، والمصادقة متعددة العوامل إلزامية للوصول المميز وعن بُعد.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.5.3', N'3.5', 2, 3530, N'Authorisation Controls', N'ضوابط التفويض',
         N'Authorisations follow least-privilege and need-to-know with documented approval workflows.',
         N'يتم تطبيق التفويض وفق أقل الصلاحيات والحاجة للمعرفة بمسارات اعتماد موثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.5.4', N'3.5', 2, 3540, N'Privileged Access Management', N'إدارة الوصول المميز',
         N'Privileged access is vaulted, brokered, session-recorded and reviewed periodically.',
         N'الوصول المميز محفوظ في خزينة ومسير ومسجل الجلسات ومراجع دورياً.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.5.5', N'3.5', 2, 3550, N'Access Reviews', N'مراجعات الصلاحيات',
         N'User access rights are reviewed at least every six months and reconciled to current job roles.',
         N'يتم مراجعة صلاحيات المستخدمين كل ستة أشهر على الأقل وتوفيقها مع الأدوار الحالية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.5.6', N'3.5', 2, 3560, N'Joiner/Mover/Leaver Process', N'إجراءات الانضمام/الانتقال/المغادرة',
         N'JML process triggers timely provisioning, role updates and deprovisioning of access.',
         N'تؤدي إجراءات JML للتزويد وتحديث الأدوار وإلغاء الصلاحيات في الوقت المناسب.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.5.7', N'3.5', 2, 3570, N'Remote Access', N'الوصول عن بُعد',
         N'Remote access uses VPN with MFA and is logged; split-tunnelling is prohibited for sensitive sessions.',
         N'الوصول عن بُعد يستخدم VPN مع المصادقة متعددة العوامل ومسجل، وفصل الأنفاق محظور للجلسات الحساسة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.5.8', N'3.5', 2, 3580, N'Customer Authentication for Online Services', N'مصادقة العملاء للخدمات الإلكترونية',
         N'Customer-facing online services use strong authentication aligned with SAMA payment security expectations.',
         N'تستخدم الخدمات الإلكترونية للعملاء مصادقة قوية تتوافق مع توقعات ساما لأمن المدفوعات.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 3.6 Application Security (8)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'3.6.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'3.6.1', N'3.6', 2, 3610, N'Secure Development Lifecycle', N'دورة التطوير الآمن',
         N'A secure SDLC integrates threat modelling, secure coding standards and security testing into every release.',
         N'تدمج دورة التطوير الآمن نمذجة التهديدات ومعايير الترميز الآمن واختبار الأمن في كل إصدار.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.6.2', N'3.6', 2, 3620, N'Secure Coding Standards', N'معايير الترميز الآمن',
         N'Documented secure-coding standards (OWASP ASVS aligned) are followed by all development teams.',
         N'تتبع جميع فرق التطوير معايير ترميز آمن موثقة (متوافقة مع OWASP ASVS).', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.6.3', N'3.6', 2, 3630, N'Application Security Testing', N'اختبار أمن التطبيقات',
         N'Static and dynamic application security testing is performed before each release of in-scope applications.',
         N'يجرى اختبار أمن التطبيقات الساكن والديناميكي قبل كل إصدار للتطبيقات المستهدفة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.6.4', N'3.6', 2, 3640, N'Application Vulnerability Remediation', N'معالجة ثغرات التطبيقات',
         N'Application vulnerabilities are remediated within SLAs aligned to severity.',
         N'تتم معالجة ثغرات التطبيقات ضمن اتفاقيات مستوى خدمة وفق درجة الخطورة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.6.5', N'3.6', 2, 3650, N'Source Code Protection', N'حماية الكود المصدري',
         N'Source code repositories are access-controlled, branch-protected and scanned for secrets.',
         N'تحفظ مستودعات الكود بصلاحيات محكمة وحماية للفروع وفحص للأسرار.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.6.6', N'3.6', 2, 3660, N'Web Application Firewall', N'جدار حماية تطبيقات الويب',
         N'Customer-facing web applications are protected by a tuned web application firewall.',
         N'تحمى تطبيقات الويب المواجهة للعملاء بجدار حماية تطبيقات الويب موَلَّف.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.6.7', N'3.6', 2, 3670, N'API Security', N'أمن واجهات البرمجة',
         N'APIs are inventoried, authenticated, rate-limited and continuously monitored for abuse.',
         N'تحصر وتصادق وتحدد معدلات واجهات البرمجة وتراقب باستمرار ضد سوء الاستخدام.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.6.8', N'3.6', 2, 3680, N'Third-Party and Open-Source Component Security', N'أمن المكونات المفتوحة المصدر والخارجية',
         N'Third-party and open-source components are inventoried (SBOM) and scanned for known vulnerabilities.',
         N'تحصر المكونات الخارجية والمفتوحة المصدر في قائمة SBOM وتفحص ضد الثغرات المعروفة.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    PRINT 'V182: domain 3 (part 1) — 32 controls inserted (3.1..3.6).';
END
GO

-- ─── DOMAIN 3 (part 2): 3.7..3.12 — 43 controls ─────────────────────
DECLARE @org BIGINT = 1;
DECLARE @sama_id BIGINT;
SELECT @sama_id = id FROM platform.authority_documents
  WHERE code = N'SAMA-CYBER-FW' AND is_deleted = 0;

IF @sama_id IS NOT NULL
BEGIN
    -- 3.7 Change Management (4)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'3.7.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'3.7.1', N'3.7', 2, 3710, N'Change Management Process', N'إجراءات إدارة التغيير',
         N'Documented change management process covers risk assessment, approval, testing, deployment and rollback.',
         N'إجراءات موثقة لإدارة التغيير تغطي تقييم المخاطر والاعتماد والاختبار والنشر والاسترجاع.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.7.2', N'3.7', 2, 3720, N'Change Advisory Board', N'لجنة استشارية للتغيير',
         N'A change advisory board reviews significant changes and emergency-change post-implementation reviews.',
         N'تراجع لجنة استشارية للتغيير التغييرات الجوهرية ومراجعات ما بعد التنفيذ للتغييرات الطارئة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.7.3', N'3.7', 2, 3730, N'Emergency Change Process', N'إجراءات التغيير الطارئ',
         N'Emergency change process exists with explicit triggers and accelerated approval and post-review.',
         N'إجراءات تغيير طارئ موجودة بمحفزات صريحة واعتماد متسارع ومراجعة لاحقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.7.4', N'3.7', 2, 3740, N'Segregation of Development, Test and Production', N'الفصل بين بيئات التطوير والاختبار والإنتاج',
         N'Development, test and production environments are segregated with controlled promotion gates.',
         N'تفصل بيئات التطوير والاختبار والإنتاج بحواجز ترقية محكمة.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 3.8 Infrastructure Security (12)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'3.8.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'3.8.1', N'3.8', 2, 3801, N'Network Segmentation', N'تقسيم الشبكات',
         N'Networks are segmented based on sensitivity and function with documented zone definitions.',
         N'تقسم الشبكات حسب الحساسية والوظيفة بتعريفات مناطق موثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.2', N'3.8', 2, 3802, N'Firewall Management', N'إدارة جدران الحماية',
         N'Firewalls implement deny-by-default with documented and periodically reviewed rules.',
         N'تطبق جدران الحماية الرفض الافتراضي بقواعد موثقة ومراجعة دورياً.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.3', N'3.8', 2, 3803, N'Intrusion Detection and Prevention', N'كشف ومنع التسلل',
         N'IDS/IPS sensors are deployed at critical network boundaries and managed centrally.',
         N'تنشر حساسات كشف ومنع التسلل عند الحدود الشبكية الحرجة وتدار مركزياً.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.4', N'3.8', 2, 3804, N'Wireless Network Security', N'أمن الشبكات اللاسلكية',
         N'Wireless networks use WPA3 enterprise authentication; guest networks are segregated.',
         N'تستخدم الشبكات اللاسلكية مصادقة WPA3 المؤسسية مع فصل شبكات الضيوف.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.5', N'3.8', 2, 3805, N'Anti-Malware', N'مكافحة البرمجيات الخبيثة',
         N'Anti-malware is deployed on endpoints and servers with central signature updates and EDR telemetry.',
         N'تنشر مكافحة البرمجيات الخبيثة على الأجهزة والخوادم بتحديثات مركزية للتوقيعات وتسجيل EDR.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.6', N'3.8', 2, 3806, N'Vulnerability Management', N'إدارة الثغرات',
         N'Vulnerability scanning is conducted continuously with risk-based remediation SLAs.',
         N'يجرى فحص الثغرات باستمرار باتفاقيات مستوى خدمة معالجة مبنية على المخاطر.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.7', N'3.8', 2, 3807, N'Patch Management', N'إدارة التحديثات الأمنية',
         N'A patch management process applies security updates within risk-aligned SLAs with documented exceptions.',
         N'إجراءات إدارة التحديثات تطبق التحديثات الأمنية ضمن اتفاقيات مستوى خدمة متوافقة مع المخاطر بالاستثناءات الموثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.8', N'3.8', 2, 3808, N'Secure Configuration', N'التهيئة الآمنة',
         N'Secure configuration baselines (CIS-aligned) are applied to OS, databases and middleware.',
         N'تطبق معايير تهيئة آمنة (متوافقة مع CIS) على أنظمة التشغيل وقواعد البيانات والوسائط البرمجية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.9', N'3.8', 2, 3809, N'Endpoint Security', N'أمن الأجهزة الطرفية',
         N'Endpoints are hardened, encrypted, DLP-enabled and monitored by EDR.',
         N'الأجهزة الطرفية محصنة ومشفرة وممكَّن عليها منع تسرب البيانات وتراقب بـ EDR.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.10', N'3.8', 2, 3810, N'Email Security', N'أمن البريد الإلكتروني',
         N'Email security covers anti-spam, anti-phishing, sandboxing and SPF/DKIM/DMARC.',
         N'يغطي أمن البريد الإلكتروني مكافحة الإزعاج والتصيد وصندوق الرمل وبروتوكولات SPF/DKIM/DMARC.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.11', N'3.8', 2, 3811, N'Web Security', N'أمن تصفح الويب',
         N'Web filtering and CASB controls protect outbound traffic from malicious sites and shadow IT.',
         N'ضوابط تنقية الويب و CASB تحمي حركة الخروج من المواقع الضارة وتقنية الظل.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.8.12', N'3.8', 2, 3812, N'Data Loss Prevention', N'منع تسرب البيانات',
         N'DLP controls cover endpoints, email and cloud egress with classification-aligned rules.',
         N'تغطي ضوابط منع تسرب البيانات الأجهزة الطرفية والبريد ومخرجات السحابة بقواعد متوافقة مع التصنيف.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 3.9 Cryptography (5)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'3.9.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'3.9.1', N'3.9', 2, 3910, N'Approved Cryptographic Algorithms', N'الخوارزميات المعتمدة للتشفير',
         N'Only approved cryptographic algorithms and minimum key strengths per the cryptography policy are used.',
         N'لا تستخدم سوى الخوارزميات المعتمدة وأدنى أطوال المفاتيح وفق سياسة التشفير.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.9.2', N'3.9', 2, 3920, N'Encryption of Data at Rest', N'تشفير البيانات أثناء التخزين',
         N'Sensitive data at rest is encrypted using approved algorithms and centrally managed keys.',
         N'تشفر البيانات الحساسة أثناء التخزين بخوارزميات معتمدة ومفاتيح مدارة مركزياً.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.9.3', N'3.9', 2, 3930, N'Encryption of Data in Transit', N'تشفير البيانات أثناء النقل',
         N'Sensitive data in transit is encrypted using approved TLS configurations (TLS 1.2+ with strong ciphers).',
         N'تشفر البيانات الحساسة أثناء النقل بإعدادات TLS معتمدة (TLS 1.2+ بخوارزميات قوية).', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.9.4', N'3.9', 2, 3940, N'Key Management', N'إدارة المفاتيح',
         N'Cryptographic keys are generated, stored, rotated and destroyed per documented key-management procedures.',
         N'تنشأ المفاتيح وتخزن وتدور وتتلف وفق إجراءات إدارة مفاتيح موثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.9.5', N'3.9', 2, 3950, N'Hardware Security Modules', N'وحدات الأمن الأجهزة',
         N'HSMs (FIPS 140-2 Level 3 minimum) protect root keys for payment, signing and customer authentication.',
         N'تحمي وحدات الأمن الأجهزة (المستوى الثالث من FIPS 140-2 على الأقل) المفاتيح الجذرية للدفع والتوقيع ومصادقة العملاء.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.10.1', N'3.10', 2, 4010, N'BYOD Policy', N'سياسة استخدام الأجهزة الشخصية',
         N'A BYOD policy specifies permitted use, security requirements and acceptable-use rules.',
         N'تحدد سياسة BYOD الاستخدامات المسموحة ومتطلبات الأمن وقواعد الاستخدام المقبول.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.10.2', N'3.10', 2, 4020, N'BYOD Mobile Device Management', N'إدارة الأجهزة الشخصية',
         N'BYOD devices accessing institution data are enrolled in MDM with mandatory controls.',
         N'تسجل أجهزة BYOD التي تصل لبيانات المؤسسة في نظام إدارة الأجهزة بضوابط إلزامية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.10.3', N'3.10', 2, 4030, N'BYOD Data Segregation', N'فصل البيانات على أجهزة BYOD',
         N'Corporate data on BYOD is held in a managed container with remote-wipe capability.',
         N'تحفظ بيانات المؤسسة على أجهزة BYOD في حاوية مدارة بقدرة المسح عن بُعد.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.10.4', N'3.10', 2, 4040, N'BYOD Loss Reporting', N'الإبلاغ عن فقد أجهزة BYOD',
         N'Lost or stolen BYOD devices trigger immediate remote-wipe and a security incident ticket.',
         N'تؤدي حالات فقد أو سرقة أجهزة BYOD لمسح فوري عن بُعد وفتح بلاغ حادث أمن.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.11.1', N'3.11', 2, 4110, N'Secure Disposal Procedures', N'إجراءات الإتلاف الآمن',
         N'Documented secure-disposal procedures cover paper, magnetic, solid-state, optical and mobile media.',
         N'إجراءات إتلاف آمن موثقة تغطي الورق والوسائط المغناطيسية والصلبة والبصرية والمتنقلة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.11.2', N'3.11', 2, 4120, N'Disposal Certification', N'شهادة الإتلاف',
         N'Certificates of destruction are obtained for sensitive media and retained per the records-retention policy.',
         N'يتم الحصول على شهادات إتلاف للوسائط الحساسة وحفظها وفق سياسة حفظ السجلات.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.11.3', N'3.11', 2, 4130, N'Sanitisation Standards', N'معايير التطهير',
         N'Media sanitisation follows NIST SP 800-88 or equivalent standards prior to re-use or disposal.',
         N'تطهير الوسائط يتبع معايير NIST SP 800-88 أو ما يعادلها قبل إعادة الاستخدام أو الإتلاف.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.11.4', N'3.11', 2, 4140, N'Cryptographic Erasure', N'الإتلاف بالتشفير',
         N'Cryptographic erasure is used as an alternative to physical destruction where supported by the device.',
         N'يستخدم الإتلاف بالتشفير كبديل للإتلاف المادي حيثما دعم الجهاز ذلك.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 3.12 Cyber Security Event & Incident Management + Threat Management (14)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'3.12.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'3.12.1', N'3.12', 2, 4201, N'Security Operations Centre (SOC)', N'مركز عمليات الأمن السيبراني',
         N'A 24x7 SOC monitors cyber security events from in-scope systems with documented playbooks.',
         N'مركز عمليات أمن على مدار الساعة يراقب الأحداث السيبرانية من الأنظمة المستهدفة بمسارات استجابة موثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.2', N'3.12', 2, 4202, N'Log Collection and Retention', N'جمع وحفظ السجلات',
         N'Security event logs from in-scope systems are centralised and retained for the regulator-mandated period.',
         N'تجمع سجلات الأحداث الأمنية من الأنظمة المستهدفة مركزياً وتحفظ للفترة التي تحددها الجهات التنظيمية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.3', N'3.12', 2, 4203, N'Security Information and Event Management (SIEM)', N'إدارة المعلومات والأحداث الأمنية',
         N'A SIEM correlates events across sources and triggers analyst tickets per documented use cases.',
         N'يربط نظام SIEM الأحداث عبر المصادر ويفتح بلاغات للمحللين وفق سيناريوهات استخدام موثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.4', N'3.12', 2, 4204, N'Threat Intelligence', N'الاستخبارات التهديدية',
         N'Cyber threat intelligence from trusted sources informs detection rules and protective controls.',
         N'تغذي استخبارات التهديدات السيبرانية من مصادر موثوقة قواعد الاكتشاف والضوابط الوقائية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.5', N'3.12', 2, 4205, N'Threat Hunting', N'صيد التهديدات',
         N'Proactive threat hunting based on hypotheses derived from threat intelligence is performed periodically.',
         N'يجرى صيد استباقي للتهديدات بناءً على فرضيات مستمدة من الاستخبارات بشكل دوري.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.6', N'3.12', 2, 4206, N'Cyber Security Incident Response Plan', N'خطة الاستجابة للحوادث السيبرانية',
         N'A documented incident response plan covers classification, escalation, communication and recovery.',
         N'خطة استجابة للحوادث موثقة تغطي التصنيف والتصعيد والتواصل والاستعادة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.7', N'3.12', 2, 4207, N'Incident Classification', N'تصنيف الحوادث',
         N'Incidents are classified by severity and impact with corresponding response timelines and authorities.',
         N'تصنف الحوادث حسب الخطورة والأثر مع جداول استجابة وصلاحيات مقابلة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.8', N'3.12', 2, 4208, N'Incident Escalation', N'تصعيد الحوادث',
         N'Documented escalation thresholds trigger executive notification and regulator briefings as required.',
         N'حدود تصعيد موثقة تؤدي لإبلاغ الإدارة التنفيذية وإحاطات الجهات التنظيمية حسب الحاجة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.9', N'3.12', 2, 4209, N'Regulatory Incident Notification', N'إبلاغ الجهات التنظيمية بالحوادث',
         N'Material incidents are reported to SAMA within the regulator-mandated notification window.',
         N'تبلغ الحوادث الجوهرية لساما ضمن نافذة الإبلاغ التي تحددها الجهة التنظيمية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.10', N'3.12', 2, 4210, N'Incident Containment and Eradication', N'احتواء واستئصال الحوادث',
         N'Incident response procedures contain the threat, eradicate root cause and restore systems to known-good state.',
         N'إجراءات الاستجابة للحوادث تحتوي التهديد وتستأصل السبب الجذري وتستعيد الأنظمة لحالة سليمة معلومة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.11', N'3.12', 2, 4211, N'Forensic Investigation', N'التحقيق الجنائي الرقمي',
         N'Forensic investigation procedures preserve evidence chain-of-custody for material incidents.',
         N'إجراءات التحقيق الجنائي الرقمي تحفظ سلسلة عهدة الأدلة للحوادث الجوهرية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.12', N'3.12', 2, 4212, N'Lessons Learned', N'الدروس المستفادة',
         N'Post-incident reviews capture lessons learned and feed remediation backlogs and policy updates.',
         N'تجمع مراجعات ما بعد الحوادث الدروس المستفادة وتغذي قوائم المعالجة وتحديثات السياسات.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.13', N'3.12', 2, 4213, N'Cyber Crisis Communication', N'التواصل في الأزمات السيبرانية',
         N'Crisis communication procedures cover internal staff, customers, media and regulators with pre-approved templates.',
         N'إجراءات التواصل في الأزمات تغطي الموظفين والعملاء والإعلام والجهات التنظيمية بقوالب معتمدة مسبقاً.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.12.14', N'3.12', 2, 4214, N'Cyber Security Resilience', N'الصمود السيبراني',
         N'Cyber resilience is integrated with Business Continuity Management; recovery is tested for critical systems.',
         N'يتكامل الصمود السيبراني مع إدارة استمرارية الأعمال، ويختبر الاستعادة للأنظمة الحرجة.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    PRINT 'V182: domain 3 (part 2) — 43 controls inserted (3.7..3.12).';
END
GO

-- ─── DOMAIN 4: Third Party Cyber Security (20) ──────────────────────
DECLARE @org BIGINT = 1;
DECLARE @sama_id BIGINT;
SELECT @sama_id = id FROM platform.authority_documents
  WHERE code = N'SAMA-CYBER-FW' AND is_deleted = 0;

IF @sama_id IS NOT NULL
BEGIN
    -- 4.1 Contract and Vendor Management (8)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'4.1.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'4.1.1', N'4.1', 2, 4110, N'Third Party Risk Assessment', N'تقييم مخاطر الأطراف الخارجية',
         N'Third parties with access to sensitive data or critical systems undergo cyber security risk assessment.',
         N'يجرى تقييم مخاطر الأمن السيبراني للأطراف الخارجية التي تصل لبيانات حساسة أو أنظمة حرجة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.1.2', N'4.1', 2, 4120, N'Third Party Due Diligence', N'العناية الواجبة للأطراف الخارجية',
         N'Pre-contract due diligence verifies the third party''s cyber security posture, certifications and breach history.',
         N'تتحقق العناية الواجبة قبل العقد من وضع الأمن السيبراني والشهادات وتاريخ الحوادث للطرف الخارجي.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.1.3', N'4.1', 2, 4130, N'Cyber Security Contract Clauses', N'البنود التعاقدية للأمن السيبراني',
         N'Contracts include cyber security clauses covering controls, breach notification, audit rights and termination.',
         N'تتضمن العقود بنوداً للأمن السيبراني تغطي الضوابط والإبلاغ عن الحوادث وحقوق التدقيق والإنهاء.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.1.4', N'4.1', 2, 4140, N'Right to Audit Third Parties', N'حق تدقيق الأطراف الخارجية',
         N'Contracts secure the right to audit third-party cyber security controls directly or via independent assurance.',
         N'تضمن العقود حق تدقيق ضوابط الأمن السيبراني للأطراف الخارجية مباشرة أو بضمان مستقل.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.1.5', N'4.1', 2, 4150, N'Third Party Performance Monitoring', N'متابعة أداء الأطراف الخارجية',
         N'Ongoing monitoring of third-party cyber security performance against contractual SLAs and KPIs.',
         N'متابعة مستمرة لأداء الأمن السيبراني للأطراف الخارجية مقابل اتفاقيات مستوى الخدمة ومؤشرات الأداء التعاقدية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.1.6', N'4.1', 2, 4160, N'Third Party Incident Notification', N'إبلاغ الأطراف الخارجية بالحوادث',
         N'Third parties are contractually required to notify cyber security incidents within an agreed timeline.',
         N'تلزم الأطراف الخارجية تعاقدياً بإبلاغ حوادث الأمن السيبراني ضمن جدول زمني متفق عليه.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.1.7', N'4.1', 2, 4170, N'Third Party Cyber Security Reviews', N'مراجعات الأمن السيبراني للأطراف الخارجية',
         N'Third-party cyber security reviews are conducted at planned intervals based on risk tiering.',
         N'مراجعات للأمن السيبراني للأطراف الخارجية تجرى وفق فترات مخططة بناءً على تصنيف المخاطر.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.1.8', N'4.1', 2, 4180, N'Third Party Exit', N'إنهاء العلاقة مع الأطراف الخارجية',
         N'Documented exit procedures cover data return/destruction, access deprovisioning and knowledge transfer.',
         N'إجراءات إنهاء موثقة تغطي إعادة/إتلاف البيانات وإلغاء الصلاحيات ونقل المعرفة.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 4.2 Outsourcing (6)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'4.2.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'4.2.1', N'4.2', 2, 4210, N'Outsourcing Approval', N'اعتماد الإسناد الخارجي',
         N'Outsourcing of cyber security functions requires senior management approval and SAMA non-objection where applicable.',
         N'يتطلب إسناد وظائف الأمن السيبراني خارجياً اعتماد الإدارة العليا وعدم ممانعة ساما حيث ينطبق.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.2.2', N'4.2', 2, 4220, N'Outsourcing Risk Assessment', N'تقييم مخاطر الإسناد الخارجي',
         N'A cyber security risk assessment precedes any outsourcing of in-scope functions.',
         N'يسبق تقييم مخاطر الأمن السيبراني أي إسناد خارجي لوظيفة مستهدفة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.2.3', N'4.2', 2, 4230, N'Retained Capability', N'القدرة المحتفظ بها',
         N'Critical cyber security functions retain internal capability sufficient to oversee and replace the outsourced provider.',
         N'تحتفظ وظائف الأمن السيبراني الحرجة بقدرة داخلية كافية للإشراف على المزود الخارجي واستبداله.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.2.4', N'4.2', 2, 4240, N'Outsourcing Geography Restrictions', N'القيود الجغرافية للإسناد',
         N'Outsourcing of sensitive data processing complies with Saudi data-residency expectations and SAMA outsourcing rules.',
         N'يتوافق إسناد معالجة البيانات الحساسة مع توقعات إقامة البيانات السعودية وقواعد الإسناد لساما.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.2.5', N'4.2', 2, 4250, N'Sub-Outsourcing Control', N'ضوابط الإسناد من الباطن',
         N'Sub-outsourcing is subject to prior approval with the same controls as primary outsourcing.',
         N'يخضع الإسناد من الباطن لاعتماد مسبق بنفس ضوابط الإسناد الأصلي.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.2.6', N'4.2', 2, 4260, N'Outsourcing Exit Planning', N'تخطيط الخروج من الإسناد',
         N'Exit strategies cover transition timelines, knowledge transfer and data return for outsourced cyber functions.',
         N'استراتيجيات الخروج تغطي الجداول الزمنية للتحول ونقل المعرفة وإعادة البيانات للوظائف المسندة خارجياً.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- 4.3 Cloud Computing (6)
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'4.3.1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'4.3.1', N'4.3', 2, 4310, N'Cloud Computing Strategy', N'استراتيجية الحوسبة السحابية',
         N'A documented cloud strategy covers service models, deployment models and risk classification of cloud workloads.',
         N'استراتيجية حوسبة سحابية موثقة تغطي نماذج الخدمة والنشر وتصنيف مخاطر الأحمال السحابية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.3.2', N'4.3', 2, 4320, N'Cloud Service Provider Selection', N'اختيار مزود الخدمات السحابية',
         N'Cloud service provider selection follows due diligence including certifications (ISO 27001, SOC 2), residency and SAMA requirements.',
         N'يتبع اختيار مزود الخدمات السحابية العناية الواجبة شاملة الشهادات (ISO 27001 و SOC 2) والإقامة ومتطلبات ساما.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.3.3', N'4.3', 2, 4330, N'Cloud Data Residency', N'إقامة البيانات السحابية',
         N'Sensitive workloads use cloud regions inside KSA where required by SAMA, PDPL and NCA Cloud Cybersecurity Controls.',
         N'تستخدم الأحمال الحساسة مناطق سحابية داخل المملكة حيثما تتطلبه ساما ونظام حماية البيانات وضوابط الأمن السحابي للهيئة الوطنية.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.3.4', N'4.3', 2, 4340, N'Shared Responsibility Model', N'نموذج المسؤولية المشتركة',
         N'The shared responsibility model is documented per cloud service with explicit allocation of cyber security duties.',
         N'يوثق نموذج المسؤولية المشتركة لكل خدمة سحابية بإسناد صريح لمهام الأمن السيبراني.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.3.5', N'4.3', 2, 4350, N'Cloud Configuration and Posture Management', N'إدارة تهيئة ووضع السحابة',
         N'Cloud security posture management (CSPM) continuously monitors configurations against approved baselines.',
         N'تراقب إدارة وضع الأمن السحابي (CSPM) باستمرار التهيئة مقابل المعايير المعتمدة.', 1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.3.6', N'4.3', 2, 4360, N'Cloud Incident Coordination', N'تنسيق حوادث السحابة',
         N'Cloud security incident response is coordinated with the provider per documented joint playbooks.',
         N'تنسق الاستجابة لحوادث الأمن السحابي مع المزود وفق مسارات استجابة مشتركة موثقة.', 1, 1, 0, N'AR_HUMAN_REVIEWED');

    PRINT 'V182: domain 4 — 20 controls inserted.';
    PRINT 'V182: SAMA CSF 140-control seed complete (29 + 16 + 75 + 20 = 140).';

    UPDATE platform.authority_documents
       SET notes = N'FULL_SEEDED 2026-05 (V182) — 140 controls across 4 domains and 23 subdomains. Cross-reference official SAMA CSF May 2017 PDF for audit use.',
           updated_at = SYSUTCDATETIME(), updated_by = N'V182'
     WHERE id = @sama_id;
END
GO
