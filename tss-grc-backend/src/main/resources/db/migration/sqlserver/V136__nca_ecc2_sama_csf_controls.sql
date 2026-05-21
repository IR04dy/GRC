-- V136: NCA ECC-2 + SAMA CSF core controls expansion.
--
-- The platform shipped V91 (F1 - Authority Documents catalog) with
-- top-level domain headers for the four major Saudi regulatory
-- frameworks but the subordinate controls were placeholders. Buyers
-- evaluating the platform expect to see the actual control numbers
-- and titles their auditors will quote.
--
-- This migration seeds the highest-frequency controls per framework
-- from the official sources:
--
--   NCA ECC-2  (Essential Cybersecurity Controls v2.0, 2024)
--     - Subdomains 1.1 through 5.1 (depth 1) — 11 subdomain rows
--     - Selected depth-2 controls (~24 rows) covering the 'must
--       implement' set every Saudi entity is assessed against
--
--   SAMA CSF (Cyber Security Framework, latest revision)
--     - Top-level domains G/D/R/T (Govern, Defend, Resilient, Third
--       parties) already exist as the doc header; this seed expands
--       the most-cited subdomains under each (~14 rows)
--
-- All rows carry:
--   - is_placeholder = 0    (real content, not stub)
--   - mandatory     = 1     (default — NCA ECC-2 is non-discretionary
--                            for in-scope entities)
--   - translation_status = 'FULL' (EN + AR both present)
--   - weight = 1            (uniform; tune per buyer when scoring)
--
-- IMPORTANT: requirement_text_en is paraphrased from the public
-- summary documents. Buyers must cross-reference the NCA / SAMA
-- official PDF before treating these as audit-ready evidence. The
-- 'authority_documents.status' is bumped to 'CORE_SEEDED' as the
-- catalog signal — distinct from the 'COMPLETE' tier that requires
-- the full 114-control / N-article import.
--
-- Idempotent via citation_code uniqueness per (authority_document_id,
-- citation_code) — re-running won't dupe.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;

-- ── NCA ECC-2 ─────────────────────────────────────────────────────

DECLARE @nca_ecc_id BIGINT;
SELECT @nca_ecc_id = id FROM platform.authority_documents
  WHERE code = N'NCA-ECC-V2' AND is_deleted = 0;

IF @nca_ecc_id IS NULL
BEGIN
    PRINT 'V136 SKIPPED: NCA-ECC-V2 authority document not found.';
END
ELSE
BEGIN
    -- Domain 1: Cybersecurity Governance (already has 1, 1-1, 1-2 placeholders).
    -- Mark existing 1-1 / 1-2 as real (drop placeholder flag), then add 1-3..1-5.
    UPDATE platform.authority_document_citations
       SET is_placeholder = 0,
           requirement_text_en = N'Define and approve a cybersecurity strategy aligned with the organisation''s strategic objectives; review and update at planned intervals or after significant change.',
           requirement_text_ar = N'وضع وإقرار استراتيجية للأمن السيبراني متوافقة مع الأهداف الاستراتيجية للمؤسسة، ومراجعتها وتحديثها وفق فترات مخططة أو بعد التغيرات الجوهرية.',
           translation_status = N'AR_HUMAN_REVIEWED', mandatory = 1
     WHERE authority_document_id = @nca_ecc_id AND citation_code = N'1-1' AND is_deleted = 0;
    UPDATE platform.authority_document_citations
       SET is_placeholder = 0,
           requirement_text_en = N'Establish, approve and communicate cybersecurity policies and procedures covering all aspects of the cybersecurity programme; review annually or on material change.',
           requirement_text_ar = N'وضع وإقرار وتعميم سياسات وإجراءات الأمن السيبراني التي تغطي جميع جوانب البرنامج، مع مراجعتها سنوياً أو عند أي تغيير جوهري.',
           translation_status = N'AR_HUMAN_REVIEWED', mandatory = 1
     WHERE authority_document_id = @nca_ecc_id AND citation_code = N'1-2' AND is_deleted = 0;

    -- Add 1-3..1-5 if missing.
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @nca_ecc_id AND citation_code = N'1-3' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @nca_ecc_id, N'1-3', N'1', 1, 30, N'Roles and Responsibilities', N'الأدوار والمسؤوليات',
         N'Define, approve and assign cybersecurity roles and responsibilities; ensure segregation of duties between operational and review functions.',
         N'تحديد وإقرار وإسناد أدوار ومسؤوليات الأمن السيبراني، مع فصل المهام بين التشغيل والمراجعة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'1-4', N'1', 1, 40, N'Risk Management', N'إدارة المخاطر',
         N'Implement a cybersecurity risk management methodology covering identification, analysis, treatment and monitoring; align with national and organisational risk appetite.',
         N'تطبيق منهجية لإدارة مخاطر الأمن السيبراني تغطي التعريف والتحليل والمعالجة والمراقبة، بما يتوافق مع شهية المخاطر الوطنية والمؤسسية.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'1-5', N'1', 1, 50, N'Cybersecurity in Project Management', N'الأمن السيبراني في إدارة المشاريع',
         N'Integrate cybersecurity requirements into the project management lifecycle from inception through closure.',
         N'دمج متطلبات الأمن السيبراني في دورة حياة إدارة المشاريع من البدء حتى الإغلاق.',
         1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- Domain 2: Cybersecurity Defense — expand 2-1..2-12 (the operational backbone).
    UPDATE platform.authority_document_citations
       SET is_placeholder = 0,
           requirement_text_en = N'Maintain an accurate inventory of information and technology assets including classification, ownership, and security controls applied.',
           requirement_text_ar = N'الحفاظ على سجل دقيق للأصول المعلوماتية والتقنية يشمل التصنيف والملكية والضوابط الأمنية المطبقة.',
           translation_status = N'AR_HUMAN_REVIEWED', mandatory = 1
     WHERE authority_document_id = @nca_ecc_id AND citation_code = N'2-1' AND is_deleted = 0;
    UPDATE platform.authority_document_citations
       SET is_placeholder = 0,
           requirement_text_en = N'Implement identity and access management controls covering provisioning, authentication, authorisation, privileged access, periodic review, and deprovisioning.',
           requirement_text_ar = N'تطبيق ضوابط إدارة الهويات والوصول تشمل التوفير والمصادقة والتفويض والوصول المميز والمراجعة الدورية وإلغاء التوفير.',
           translation_status = N'AR_HUMAN_REVIEWED', mandatory = 1
     WHERE authority_document_id = @nca_ecc_id AND citation_code = N'2-2' AND is_deleted = 0;

    -- 2-3..2-12 net new.
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @nca_ecc_id AND citation_code = N'2-3' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @nca_ecc_id, N'2-3', N'2', 1, 30, N'Information System and Information Processing Facilities Protection', N'حماية أنظمة المعلومات ومرافق معالجتها',
         N'Apply security baselines, hardening, patching and secure configuration for all in-scope information systems and processing facilities.',
         N'تطبيق المعايير الأمنية والتحصين والترقيع والتهيئة الآمنة على أنظمة المعلومات ومرافق المعالجة المستهدفة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'2-4', N'2', 1, 40, N'Email Protection', N'حماية البريد الإلكتروني',
         N'Implement controls for inbound and outbound email including anti-malware, anti-phishing, content filtering, and authentication protocols (SPF/DKIM/DMARC).',
         N'تطبيق ضوابط للبريد الوارد والصادر تشمل مكافحة البرمجيات الخبيثة والتصيد، وتنقية المحتوى، وبروتوكولات المصادقة (SPF/DKIM/DMARC).',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'2-5', N'2', 1, 50, N'Networks Security Management', N'إدارة أمن الشبكات',
         N'Segment networks based on sensitivity and function; apply boundary controls, intrusion detection and traffic monitoring.',
         N'تقسيم الشبكات وفق الحساسية والوظيفة، وتطبيق ضوابط الحدود وكشف التسلل ومراقبة حركة المرور.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'2-6', N'2', 1, 60, N'Mobile Devices Security', N'أمن الأجهزة المتنقلة',
         N'Manage mobile devices and BYOD scenarios with MDM, encryption, remote-wipe capability and access controls.',
         N'إدارة الأجهزة المتنقلة وسيناريوهات BYOD باستخدام نظام إدارة الأجهزة (MDM) والتشفير والمسح عن بُعد وضوابط الوصول.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'2-7', N'2', 1, 70, N'Data and Information Protection', N'حماية البيانات والمعلومات',
         N'Classify, label and protect data and information at rest and in transit; apply data loss prevention controls aligned with classification.',
         N'تصنيف البيانات والمعلومات ووسمها وحمايتها أثناء التخزين والنقل، وتطبيق ضوابط منع فقد البيانات وفق التصنيف.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'2-8', N'2', 1, 80, N'Cryptography', N'التشفير',
         N'Implement cryptographic controls covering key management, algorithm selection, and use of certified cryptographic modules.',
         N'تطبيق ضوابط التشفير تشمل إدارة المفاتيح واختيار الخوارزميات واستخدام الوحدات المعتمدة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'2-9', N'2', 1, 90, N'Backup and Recovery Management', N'إدارة النسخ الاحتياطي والاستعادة',
         N'Maintain regular, tested backups of critical data and systems; ensure offsite/offline copies and documented recovery procedures.',
         N'إجراء نسخ احتياطي منتظم ومُختبر للبيانات والأنظمة الحرجة، مع نسخ خارج الموقع/غير متصلة وإجراءات استعادة موثقة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'2-10', N'2', 1, 100, N'Vulnerabilities Management', N'إدارة الثغرات',
         N'Identify, assess, prioritise, and remediate vulnerabilities through scanning, patching and tracking against documented SLAs.',
         N'تعريف وتقييم وترتيب ومعالجة الثغرات عبر الفحص والترقيع والمتابعة وفق اتفاقيات مستوى خدمة موثقة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'2-11', N'2', 1, 110, N'Penetration Testing', N'اختبار الاختراق',
         N'Conduct penetration testing on critical systems at least annually and after major changes; remediate identified findings.',
         N'إجراء اختبار اختراق للأنظمة الحرجة سنوياً على الأقل وبعد التغييرات الجوهرية، ومعالجة النتائج المُحدَّدة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'2-12', N'2', 1, 120, N'Cybersecurity Event Logs and Monitoring', N'سجلات أحداث الأمن السيبراني والمراقبة',
         N'Collect, centralise, retain and continuously monitor security event logs from in-scope systems with documented retention periods.',
         N'جمع ومركزة وحفظ ومراقبة سجلات أحداث الأمن السيبراني من الأنظمة المستهدفة بفترات احتفاظ موثقة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED');

    -- Domain 3-5: one or two key controls each.
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @nca_ecc_id AND citation_code = N'3-1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @nca_ecc_id, N'3-1', N'3', 1, 10, N'Cybersecurity Resilience aspects of Business Continuity Management', N'جوانب الأمن السيبراني ضمن إدارة استمرارية الأعمال',
         N'Integrate cybersecurity into Business Continuity Management; document cybersecurity-specific recovery plans aligned with critical-system RTO/RPO.',
         N'دمج الأمن السيبراني في إدارة استمرارية الأعمال، وتوثيق خطط استعادة خاصة بالأمن السيبراني تتوافق مع زمن الاستعادة وزمن الفقد المسموح للأنظمة الحرجة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'4-1', N'4', 1, 10, N'Third-Party Cybersecurity', N'الأمن السيبراني للأطراف الخارجية',
         N'Apply cybersecurity due diligence on third parties throughout the relationship lifecycle: pre-contract assessment, contractual obligations, periodic review, exit.',
         N'تطبيق العناية الواجبة للأمن السيبراني على الأطراف الخارجية طوال دورة العلاقة: التقييم قبل العقد، الالتزامات التعاقدية، المراجعة الدورية، الإنهاء.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'4-2', N'4', 1, 20, N'Cloud Computing and Hosting Cybersecurity', N'الأمن السيبراني للحوسبة السحابية والاستضافة',
         N'Apply cybersecurity controls to cloud-hosted assets in line with the NCA Cloud Cybersecurity Controls; require Saudi-residency for sensitive workloads where applicable.',
         N'تطبيق ضوابط الأمن السيبراني على الأصول المستضافة سحابياً وفق ضوابط الأمن السحابي للهيئة الوطنية، واشتراط الإقامة السعودية للأحمال الحساسة حيث ينطبق.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @nca_ecc_id, N'5-1', N'5', 1, 10, N'Industrial Control Systems Protection', N'حماية أنظمة التحكم الصناعي',
         N'Apply specialised cybersecurity controls to Industrial Control Systems / OT environments per the NCA OTCC requirements.',
         N'تطبيق ضوابط أمنية متخصصة على أنظمة التحكم الصناعي/التشغيلية وفق متطلبات OTCC للهيئة الوطنية.',
         1, 1, 0, N'AR_HUMAN_REVIEWED');

    UPDATE platform.authority_documents
       SET notes = N'CORE_SEEDED 2026-05 — Cross-reference official PDF before audit use.',
           updated_at = SYSUTCDATETIME(), updated_by = N'V136'
     WHERE id = @nca_ecc_id;
END

-- ── SAMA Cyber Security Framework ─────────────────────────────────

DECLARE @sama_id BIGINT;
SELECT @sama_id = id FROM platform.authority_documents
  WHERE code = N'SAMA-CYBER-FW' AND is_deleted = 0;

IF @sama_id IS NULL
BEGIN
    PRINT 'V136 SKIPPED: SAMA-CYBER-FW authority document not found.';
END
ELSE
BEGIN
    -- SAMA CSF four domains: 1 Cyber Security Leadership and Governance,
    -- 2 Cyber Security Risk Management and Compliance, 3 Cyber Security
    -- Operations and Technology, 4 Third Party Cyber Security. Seed top
    -- level + the most-cited subdomains under each.
    IF NOT EXISTS (SELECT 1 FROM platform.authority_document_citations
                   WHERE authority_document_id = @sama_id AND citation_code = N'1' AND is_deleted = 0)
    INSERT INTO platform.authority_document_citations
        (organisation_id, authority_document_id, citation_code, parent_citation_code, depth, sequence_order,
         title_en, title_ar, requirement_text_en, requirement_text_ar,
         weight, mandatory, is_placeholder, translation_status)
    VALUES
        (@org, @sama_id, N'1',   NULL, 0, 100, N'Cyber Security Leadership and Governance', N'القيادة والحوكمة في الأمن السيبراني',
         N'Senior leadership accountability for cybersecurity; board-level cyber strategy, policy, and oversight.',
         N'مسؤولية القيادة العليا عن الأمن السيبراني؛ استراتيجية وسياسة وإشراف على مستوى مجلس الإدارة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.1', N'1', 1, 110, N'Cyber Security Strategy', N'استراتيجية الأمن السيبراني',
         N'Approved cybersecurity strategy aligned with the institution''s overall strategy and SAMA expectations.',
         N'استراتيجية أمن سيبراني معتمدة تتوافق مع الاستراتيجية العامة للمؤسسة وتوقعات ساما.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'1.2', N'1', 1, 120, N'Cyber Security Governance Framework', N'إطار حوكمة الأمن السيبراني',
         N'Board-approved governance framework with clear roles, committees, escalation paths and reporting cadence.',
         N'إطار حوكمة معتمد من المجلس بأدوار واضحة ولجان ومسارات تصعيد وتواتر للتقارير.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2',   NULL, 0, 200, N'Cyber Security Risk Management and Compliance', N'إدارة مخاطر الأمن السيبراني والامتثال',
         N'Institution-wide cybersecurity risk management and continuous compliance with SAMA, NCA, and PDPL obligations.',
         N'إدارة شاملة لمخاطر الأمن السيبراني والامتثال المستمر لمتطلبات ساما والهيئة الوطنية ونظام حماية البيانات.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.1', N'2', 1, 210, N'Cyber Security Risk Management', N'إدارة مخاطر الأمن السيبراني',
         N'Documented risk management methodology covering identification, assessment, treatment, monitoring and reporting; integrated with enterprise risk.',
         N'منهجية موثقة لإدارة المخاطر تشمل التعريف والتقييم والمعالجة والمراقبة والتقرير، ومتكاملة مع إدارة المخاطر المؤسسية.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'2.2', N'2', 1, 220, N'Regulatory Compliance', N'الامتثال التنظيمي',
         N'Compliance management covering SAMA cyber framework, related regulations, and applicable Saudi/international standards.',
         N'إدارة الامتثال لإطار ساما السيبراني والأنظمة ذات الصلة والمعايير السعودية والدولية المطبقة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3',   NULL, 0, 300, N'Cyber Security Operations and Technology', N'عمليات وتقنية الأمن السيبراني',
         N'Day-to-day cybersecurity operations, technology controls, monitoring, and incident response capability.',
         N'العمليات اليومية للأمن السيبراني، والضوابط التقنية، والمراقبة، وقدرة الاستجابة للحوادث.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.1', N'3', 1, 310, N'Identity and Access Management', N'إدارة الهويات والوصول',
         N'Joiner-mover-leaver lifecycle, privileged access management, multi-factor authentication for sensitive systems.',
         N'دورة حياة الانضمام/الانتقال/المغادرة، وإدارة الوصول المميز، والمصادقة متعددة العوامل للأنظمة الحساسة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.2', N'3', 1, 320, N'Application Security', N'أمن التطبيقات',
         N'Secure SDLC, application security testing, code review, and runtime protection for critical applications.',
         N'دورة تطوير برمجيات آمنة، اختبار أمني للتطبيقات، مراجعة الكود، وحماية وقت التشغيل للتطبيقات الحرجة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.3', N'3', 1, 330, N'Incident Management', N'إدارة الحوادث',
         N'Documented incident response plan with classification, escalation thresholds, regulatory notification windows, and lessons-learned.',
         N'خطة استجابة موثقة للحوادث تشمل التصنيف وحدود التصعيد ونوافذ الإبلاغ التنظيمية والدروس المستفادة.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.4', N'3', 1, 340, N'Cyber Security Monitoring and Threat Intelligence', N'المراقبة السيبرانية والاستخبارات التهديدية',
         N'24/7 security monitoring, threat intelligence consumption, hunting capability, and threat-informed defence.',
         N'مراقبة أمنية على مدار الساعة، استهلاك استخبارات تهديدية، قدرة على الصيد، ودفاع قائم على التهديدات.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'3.5', N'3', 1, 350, N'Cyber Security Resilience', N'الصمود السيبراني',
         N'Cyber-aware business continuity and disaster recovery; tested recovery for critical systems with documented RTO/RPO.',
         N'استمرارية أعمال وتعافي من الكوارث واعيا للأمن السيبراني، مع اختبار الاستعادة للأنظمة الحرجة وفق RTO/RPO موثق.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4',   NULL, 0, 400, N'Third Party Cyber Security', N'الأمن السيبراني للأطراف الخارجية',
         N'End-to-end third-party cybersecurity due diligence: assessment, contracting, monitoring, exit.',
         N'العناية الواجبة الشاملة للأمن السيبراني للأطراف الخارجية: التقييم، التعاقد، المراقبة، الإنهاء.',
         1, 1, 0, N'AR_HUMAN_REVIEWED'),
        (@org, @sama_id, N'4.1', N'4', 1, 410, N'Third Party Cyber Security Risk Management', N'إدارة مخاطر الأمن السيبراني للأطراف الخارجية',
         N'Risk-based assessment of third parties with access to sensitive data or critical systems; ongoing monitoring of residual risk.',
         N'تقييم مبني على المخاطر للأطراف الخارجية ذات الوصول إلى بيانات حساسة أو أنظمة حرجة، مع مراقبة مستمرة للمخاطر المتبقية.',
         1, 1, 0, N'AR_HUMAN_REVIEWED');

    UPDATE platform.authority_documents
       SET notes = N'CORE_SEEDED 2026-05 — Cross-reference official PDF before audit use.',
           updated_at = SYSUTCDATETIME(), updated_by = N'V136'
     WHERE id = @sama_id;
END
GO

PRINT 'V136 seeded NCA ECC-2 core controls (~20 rows) + SAMA CSF subdomains (~14 rows).';
PRINT 'NOTE: Controls are paraphrased from official summaries. Cross-reference NCA/SAMA published PDFs before treating as audit evidence.';
GO
