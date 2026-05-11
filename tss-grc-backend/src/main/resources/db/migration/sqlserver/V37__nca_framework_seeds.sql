-- V37 — NCA framework seeds: CSCC, CCC, OTCC (Saudi GRC §3.4 + gap #5).
--
-- The cyber.cyber_frameworks table previously held only ECC + ISO 27001
-- + PCI DSS. The Saudi GRC prompt's §3.4 explicitly requires all four
-- NCA frameworks so that the assessment UI and dashboards can offer
-- them side-by-side:
--
--   ECC   Essential Cybersecurity Controls           (existing, enriched)
--   CSCC  Critical Systems Cybersecurity Controls    (added)
--   CCC   Cloud Cybersecurity Controls               (added)
--   OTCC  Operational Technology Cybersecurity Controls (added)
--
-- We also enrich the table with bilingual + metadata columns so the
-- UI can render regulator chips, control counts, and mandatory-for
-- sector chips for each framework. This unblocks the §30 dashboards
-- listed in the implementation prompt and the §3.4 assessment surface.

SET QUOTED_IDENTIFIER ON;
GO

-- ─── 1. Extend cyber.cyber_frameworks with metadata columns ─────────
IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID(N'cyber.cyber_frameworks') AND name = N'name_ar')
BEGIN
    ALTER TABLE cyber.cyber_frameworks
        ADD name_ar         NVARCHAR(255) NULL,
            description_ar  NVARCHAR(MAX) NULL,
            regulator       NVARCHAR(20)  NULL,    -- NCA / SAMA / CITC / SDAIA / ISO / PCI
            framework_code  NVARCHAR(20)  NULL,    -- ECC / CSCC / CCC / OTCC / ISO27001 / PCIDSS
            domain_count    INT           NULL,
            control_count   INT           NULL,
            effective_from  DATE          NULL,
            mandatory_for   NVARCHAR(MAX) NULL,    -- CSV of sectors / CRITICAL_INFRA / GOVT etc.
            reference_url   NVARCHAR(500) NULL;
END
GO

-- ─── 2. Enrich the existing ECC row (idempotent) ────────────────────
UPDATE cyber.cyber_frameworks
   SET name_ar        = COALESCE(name_ar, N'الضوابط الأساسية للأمن السيبراني'),
       description_ar = COALESCE(description_ar,
            N'الضوابط الأساسية للأمن السيبراني الصادرة عن الهيئة الوطنية للأمن السيبراني - 5 مجالات و 114 ضابطاً. إلزامية لجميع الجهات الحكومية والقطاع الخاص ذات الأهمية في المملكة.'),
       regulator      = COALESCE(regulator, N'NCA'),
       framework_code = COALESCE(framework_code, N'ECC'),
       domain_count   = COALESCE(domain_count, 5),
       control_count  = COALESCE(control_count, 114),
       effective_from = COALESCE(effective_from, '2018-12-15'),
       mandatory_for  = COALESCE(mandatory_for, N'GOVT,REGULATED_PRIVATE,CRITICAL_INFRA'),
       description_en = COALESCE(description_en,
            N'NCA Essential Cybersecurity Controls — 5 main domains and 114 controls. Mandatory for all KSA government entities and important private-sector organisations.'),
       reference_url  = COALESCE(reference_url, N'https://nca.gov.sa/ecc')
 WHERE name = N'NCA ECC';
GO

-- Also enrich ISO 27001 + PCI DSS rows for completeness.
UPDATE cyber.cyber_frameworks
   SET name_ar        = COALESCE(name_ar, N'أيزو 27001 - أنظمة إدارة أمن المعلومات'),
       description_ar = COALESCE(description_ar,
            N'معيار أيزو الدولي لأنظمة إدارة أمن المعلومات - 4 فصول و 93 ضابطاً في الملحق أ. النسخة 2022 تستبدل النسخة 2013.'),
       regulator      = COALESCE(regulator, N'ISO'),
       framework_code = COALESCE(framework_code, N'ISO27001'),
       domain_count   = COALESCE(domain_count, 4),
       control_count  = COALESCE(control_count, 93),
       effective_from = COALESCE(effective_from, '2022-10-25'),
       mandatory_for  = COALESCE(mandatory_for, N'VOLUNTARY'),
       description_en = COALESCE(description_en,
            N'ISO 27001:2022 — international standard for information security management systems. 4 clauses + 93 Annex-A controls. Supersedes the 2013 edition.'),
       reference_url  = COALESCE(reference_url, N'https://www.iso.org/standard/27001')
 WHERE name = N'ISO 27001';
GO

UPDATE cyber.cyber_frameworks
   SET name_ar        = COALESCE(name_ar, N'PCI DSS - معيار أمن بيانات صناعة بطاقات الدفع'),
       description_ar = COALESCE(description_ar,
            N'معيار صناعة بطاقات الدفع لأمن البيانات الإصدار 4.0 - 12 متطلباً و 277 ضابطاً. إلزامي لجميع الجهات التي تخزن أو تعالج أو تنقل بيانات حاملي البطاقات.'),
       regulator      = COALESCE(regulator, N'PCI'),
       framework_code = COALESCE(framework_code, N'PCIDSS'),
       domain_count   = COALESCE(domain_count, 12),
       control_count  = COALESCE(control_count, 277),
       effective_from = COALESCE(effective_from, '2024-03-31'),
       mandatory_for  = COALESCE(mandatory_for, N'PAYMENTS_CARD_DATA'),
       description_en = COALESCE(description_en,
            N'Payment Card Industry Data Security Standard v4.0 — 12 requirements and 277 controls. Mandatory for entities that store, process, or transmit cardholder data.'),
       reference_url  = COALESCE(reference_url, N'https://www.pcisecuritystandards.org/document_library')
 WHERE name = N'PCI DSS';
GO

-- ─── 3. Insert CSCC (Critical Systems Cybersecurity Controls) ───────
IF NOT EXISTS (SELECT 1 FROM cyber.cyber_frameworks WHERE framework_code = N'CSCC')
BEGIN
    INSERT INTO cyber.cyber_frameworks
        (organisation_id, name, name_ar, version, description_en, description_ar,
         regulator, framework_code, domain_count, control_count,
         effective_from, mandatory_for, reference_url, is_deleted, created_at)
    VALUES
        (1, N'NCA CSCC',
         N'ضوابط الأمن السيبراني للأنظمة الحساسة',
         N'1.0',
         N'NCA Critical Systems Cybersecurity Controls — 4 main domains and 32 main controls + 73 subcontrols. Mandatory for any organisation operating a "critical national system" as designated by NCA (energy, water, telecom, finance, health).',
         N'ضوابط الأمن السيبراني للأنظمة الحساسة الصادرة عن الهيئة الوطنية للأمن السيبراني - 4 مجالات و 32 ضابطاً أساسياً و 73 ضابطاً فرعياً. إلزامي لأي جهة تشغل نظاماً وطنياً حساساً مصنفاً من الهيئة (الطاقة، المياه، الاتصالات، المالية، الصحة).',
         N'NCA', N'CSCC', 4, 105,
         '2019-12-31',
         N'CRITICAL_INFRA,ENERGY,WATER,TELECOM,FINANCE,HEALTH',
         N'https://nca.gov.sa/cscc',
         0, SYSUTCDATETIME());
END
GO

-- ─── 4. Insert CCC (Cloud Cybersecurity Controls) ───────────────────
IF NOT EXISTS (SELECT 1 FROM cyber.cyber_frameworks WHERE framework_code = N'CCC')
BEGIN
    INSERT INTO cyber.cyber_frameworks
        (organisation_id, name, name_ar, version, description_en, description_ar,
         regulator, framework_code, domain_count, control_count,
         effective_from, mandatory_for, reference_url, is_deleted, created_at)
    VALUES
        (1, N'NCA CCC',
         N'ضوابط الأمن السيبراني للحوسبة السحابية',
         N'1.0',
         N'NCA Cloud Cybersecurity Controls — 4 main domains and 37 main controls + 96 subcontrols. Mandatory for cloud service providers operating in KSA and for any KSA entity hosting government or critical data in cloud. Aligned with ECC and CSCC.',
         N'ضوابط الأمن السيبراني للحوسبة السحابية الصادرة عن الهيئة الوطنية للأمن السيبراني - 4 مجالات و 37 ضابطاً أساسياً و 96 ضابطاً فرعياً. إلزامي لمزودي الخدمات السحابية العاملين بالمملكة وأي جهة تستضيف بيانات حكومية أو حساسة في السحابة. متوافق مع ECC و CSCC.',
         N'NCA', N'CCC', 4, 133,
         '2020-12-31',
         N'CLOUD_PROVIDERS,CLOUD_CONSUMERS_GOVT,CRITICAL_INFRA',
         N'https://nca.gov.sa/ccc',
         0, SYSUTCDATETIME());
END
GO

-- ─── 5. Insert OTCC (Operational Technology Cybersecurity Controls) ──
IF NOT EXISTS (SELECT 1 FROM cyber.cyber_frameworks WHERE framework_code = N'OTCC')
BEGIN
    INSERT INTO cyber.cyber_frameworks
        (organisation_id, name, name_ar, version, description_en, description_ar,
         regulator, framework_code, domain_count, control_count,
         effective_from, mandatory_for, reference_url, is_deleted, created_at)
    VALUES
        (1, N'NCA OTCC',
         N'ضوابط الأمن السيبراني للتقنيات التشغيلية',
         N'1.0',
         N'NCA Operational Technology Cybersecurity Controls — 5 main domains and 47 main controls. Mandatory for any KSA entity operating OT/ICS systems (industrial control, SCADA, DCS, PLC, RTU, IIoT). Aligned with IEC 62443 and ECC.',
         N'ضوابط الأمن السيبراني للتقنيات التشغيلية الصادرة عن الهيئة الوطنية للأمن السيبراني - 5 مجالات و 47 ضابطاً أساسياً. إلزامي لأي جهة تشغل أنظمة تقنيات تشغيلية أو أنظمة تحكم صناعية (SCADA، DCS، PLC، RTU، إنترنت الأشياء الصناعي). متوافق مع IEC 62443 و ECC.',
         N'NCA', N'OTCC', 5, 47,
         '2022-08-15',
         N'OT_ICS,ENERGY,UTILITIES,MANUFACTURING,CRITICAL_INFRA',
         N'https://nca.gov.sa/otcc',
         0, SYSUTCDATETIME());
END
GO

-- ─── 6. Two more for completeness — SAMA Cyber + ISO 22301 ───────────
IF NOT EXISTS (SELECT 1 FROM cyber.cyber_frameworks WHERE framework_code = N'SAMA_CYBER')
BEGIN
    INSERT INTO cyber.cyber_frameworks
        (organisation_id, name, name_ar, version, description_en, description_ar,
         regulator, framework_code, domain_count, control_count,
         effective_from, mandatory_for, reference_url, is_deleted, created_at)
    VALUES
        (1, N'SAMA Cyber Security Framework',
         N'إطار الأمن السيبراني - مؤسسة النقد العربي السعودي',
         N'1.0',
         N'SAMA Cyber Security Framework — 4 domains and 119 controls organised under cyber-security leadership & governance, risk management, operations & technology, and third-party. Mandatory for KSA banks, insurance, finance companies, and SAMA-regulated payment service providers.',
         N'إطار الأمن السيبراني الصادر عن مؤسسة النقد العربي السعودي - 4 مجالات و 119 ضابطاً. إلزامي للبنوك السعودية وشركات التأمين والتمويل ومزودي خدمات الدفع الخاضعين لمؤسسة النقد.',
         N'SAMA', N'SAMA_CYBER', 4, 119,
         '2017-05-31',
         N'BANKING,INSURANCE,FINANCE,PAYMENT_PROVIDERS',
         N'https://sama.gov.sa/cybersecurity-framework',
         0, SYSUTCDATETIME());
END
GO

IF NOT EXISTS (SELECT 1 FROM cyber.cyber_frameworks WHERE framework_code = N'ISO22301')
BEGIN
    INSERT INTO cyber.cyber_frameworks
        (organisation_id, name, name_ar, version, description_en, description_ar,
         regulator, framework_code, domain_count, control_count,
         effective_from, mandatory_for, reference_url, is_deleted, created_at)
    VALUES
        (1, N'ISO 22301',
         N'أيزو 22301 - أنظمة إدارة استمرارية الأعمال',
         N'2019',
         N'ISO 22301:2019 — international standard for business continuity management systems. 10 clauses providing requirements for planning, implementing, operating, monitoring, reviewing and continually improving a documented BCMS. Voluntary but widely adopted as a benchmark.',
         N'معيار أيزو 22301 الدولي لأنظمة إدارة استمرارية الأعمال - 10 فصول توفر متطلبات تخطيط وتنفيذ وتشغيل ومراقبة ومراجعة وتحسين نظام إدارة استمرارية الأعمال الموثق. طوعي ولكن معتمد على نطاق واسع كمرجع.',
         N'ISO', N'ISO22301', 10, 0,
         '2019-10-31',
         N'VOLUNTARY',
         N'https://www.iso.org/standard/75106.html',
         0, SYSUTCDATETIME());
END
GO

PRINT N'V37 — cyber.cyber_frameworks now seeded with ECC, CSCC, CCC, OTCC, SAMA Cyber, ISO 27001, ISO 22301, PCI DSS.';
GO
