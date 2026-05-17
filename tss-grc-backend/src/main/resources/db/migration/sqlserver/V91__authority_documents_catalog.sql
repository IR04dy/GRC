-- V91: Authority Documents catalog (Framework Intersection plan, slice F1).
--
-- This is the platform-level "what regulations does the org care about"
-- catalog. It replaces the previous plan's per-regulator modules
-- (DGA / SAMA / NRC) with a single pair of tables. Every NEW document
-- is seeded with is_placeholder = 1 — the existing NDI banner pattern
-- (Rule 0) lights up until the official text is loaded.
--
-- Naming follows ServiceNow GRC schema for transferability:
--   authority_documents  ↔ "Authority Document"
--   authority_document_citations ↔ "Citation"
--
-- See docs/grc/framework-intersection-plan.md §4-5 for the design.

-- ─── Authority Documents ──────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'authority_documents' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.authority_documents (
        id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id    BIGINT          NOT NULL,
        external_id        UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        -- e.g. 'NCA-ECC-V2', 'SAMA-BCM-FW', 'ISO-22301', 'PDPL-2023'.
        code               NVARCHAR(60)    NOT NULL,
        -- groups for filtering: 'NCA Cyber', 'SAMA', 'Privacy', 'BCM',
        -- 'Risk', 'Third-Party', 'Digital-Gov', 'Resilience', 'International'.
        family             NVARCHAR(60)    NOT NULL,
        jurisdiction       NVARCHAR(60)    NOT NULL,         -- 'KSA' / 'International' / 'EU' / 'US'
        issuer             NVARCHAR(120)   NOT NULL,         -- 'NCA' / 'SAMA' / 'SDAIA' / 'DGA' / 'NRC' / 'ISO' / 'AICPA'
        version            NVARCHAR(20)    NOT NULL,         -- '2.0', '1.0', '2023', '2022'…

        title_en           NVARCHAR(255)   NOT NULL,
        title_ar           NVARCHAR(255)   NULL,
        summary_en         NVARCHAR(MAX)   NULL,
        summary_ar         NVARCHAR(MAX)   NULL,

        -- JSON: { "org_types": ["BANK","INSURER"], "sectors": ["FINANCE"] }
        -- Empty / NULL means "applies to every org".
        applicability_json NVARCHAR(MAX)   NULL,

        effective_date_g   DATE            NULL,             -- Gregorian
        effective_date_h   NVARCHAR(20)    NULL,             -- Hijri (string per existing pattern)

        is_placeholder     BIT             NOT NULL DEFAULT 1,
        translation_status NVARCHAR(20)    NOT NULL DEFAULT 'EN_ONLY',
        -- EN_ONLY | AR_AUTO | AR_HUMAN_REVIEWED | AR_OFFICIAL
        status             NVARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
        -- DRAFT | ACTIVE | DEPRECATED | SUPERSEDED
        superseded_by_id   BIGINT          NULL,

        notes              NVARCHAR(MAX)   NULL,

        is_deleted         BIT             NOT NULL DEFAULT 0,
        created_at         DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by         NVARCHAR(100)   NULL,
        updated_at         DATETIME2(0)    NULL,
        updated_by         NVARCHAR(100)   NULL,

        CONSTRAINT uq_authdoc_org_code_version UNIQUE (organisation_id, code, version),
        CONSTRAINT ck_authdoc_translation CHECK (translation_status IN
            ('EN_ONLY','AR_AUTO','AR_HUMAN_REVIEWED','AR_OFFICIAL')),
        CONSTRAINT ck_authdoc_status CHECK (status IN
            ('DRAFT','ACTIVE','DEPRECATED','SUPERSEDED')),
        CONSTRAINT fk_authdoc_superseded FOREIGN KEY (superseded_by_id)
            REFERENCES platform.authority_documents(id)
    );

    CREATE INDEX ix_authdoc_org_family   ON platform.authority_documents(organisation_id, family);
    CREATE INDEX ix_authdoc_org_status   ON platform.authority_documents(organisation_id, status);
    CREATE INDEX ix_authdoc_issuer       ON platform.authority_documents(issuer);
END
GO

-- ─── Citations ────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'authority_document_citations' AND schema_id = SCHEMA_ID('platform'))
BEGIN
    CREATE TABLE platform.authority_document_citations (
        id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
        organisation_id          BIGINT          NOT NULL,
        authority_document_id    BIGINT          NOT NULL,

        citation_code            NVARCHAR(80)    NOT NULL,   -- '2-1-1', 'A.5.7', 'Art-14', 'BCM-3.2'
        parent_citation_code     NVARCHAR(80)    NULL,
        depth                    INT             NOT NULL DEFAULT 0,
        sequence_order           INT             NOT NULL DEFAULT 0,

        title_en                 NVARCHAR(500)   NOT NULL,
        title_ar                 NVARCHAR(500)   NULL,
        requirement_text_en      NVARCHAR(MAX)   NULL,
        requirement_text_ar      NVARCHAR(MAX)   NULL,
        intent_en                NVARCHAR(MAX)   NULL,        -- what the citation is trying to achieve
        intent_ar                NVARCHAR(MAX)   NULL,
        testing_guidance_en      NVARCHAR(MAX)   NULL,
        testing_guidance_ar      NVARCHAR(MAX)   NULL,

        weight                   DECIMAL(5,2)    NOT NULL DEFAULT 1.00,
        mandatory                BIT             NOT NULL DEFAULT 1,
        is_placeholder           BIT             NOT NULL DEFAULT 1,
        translation_status       NVARCHAR(20)    NOT NULL DEFAULT 'EN_ONLY',

        is_deleted               BIT             NOT NULL DEFAULT 0,
        created_at               DATETIME2(0)    NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at               DATETIME2(0)    NULL,

        CONSTRAINT uq_citation_doc_code UNIQUE (authority_document_id, citation_code),
        CONSTRAINT fk_citation_doc FOREIGN KEY (authority_document_id)
            REFERENCES platform.authority_documents(id),
        CONSTRAINT ck_citation_translation CHECK (translation_status IN
            ('EN_ONLY','AR_AUTO','AR_HUMAN_REVIEWED','AR_OFFICIAL'))
    );

    CREATE INDEX ix_citation_doc        ON platform.authority_document_citations(authority_document_id);
    CREATE INDEX ix_citation_org_doc    ON platform.authority_document_citations(organisation_id, authority_document_id);
    CREATE INDEX ix_citation_parent     ON platform.authority_document_citations(authority_document_id, parent_citation_code);
END
GO

-- ─── Seed: 19 authority documents for org 1 (the demo tenant) ─────
-- Every row is is_placeholder = 1 + translation_status = 'EN_ONLY'.
-- Banner ("Placeholder — requires official text import") will fire
-- everywhere the catalog is read, per the NDI Rule-0 guardrail.

IF NOT EXISTS (SELECT 1 FROM platform.authority_documents WHERE organisation_id = 1 AND code = 'NCA-ECC-V2')
BEGIN
    INSERT INTO platform.authority_documents
      (organisation_id, code, family, jurisdiction, issuer, version,
       title_en, title_ar, summary_en, summary_ar, is_placeholder, status)
    VALUES
      (1, 'NCA-ECC-V2', 'NCA Cyber', 'KSA', 'NCA', '2.0',
       'Essential Cybersecurity Controls v2',
       N'الضوابط الأساسية للأمن السيبراني — الإصدار الثاني',
       'NCA mandatory baseline for every regulated entity.',
       N'الحد الأدنى للأمن السيبراني المُلزِم لكل جهة خاضعة.',
       1, 'ACTIVE'),
      (1, 'NCA-CSCC-V1', 'NCA Cyber', 'KSA', 'NCA', '1.0',
       'Critical Systems Cybersecurity Controls',
       N'ضوابط الأمن السيبراني للأنظمة الحساسة',
       'Controls applied on top of ECC for critical national systems.',
       N'ضوابط إضافية فوق الـECC للأنظمة الوطنية الحساسة.',
       1, 'ACTIVE'),
      (1, 'NCA-CCC-V1', 'NCA Cyber', 'KSA', 'NCA', '1.0',
       'Cloud Cybersecurity Controls',
       N'ضوابط الأمن السيبراني للحوسبة السحابية',
       'Cloud-service-provider and cloud-tenant cybersecurity controls.',
       N'ضوابط الأمن السيبراني لمزوّدي ومستفيدي الحوسبة السحابية.',
       1, 'ACTIVE'),
      (1, 'NCA-OTCC-V1', 'NCA Cyber', 'KSA', 'NCA', '1.0',
       'Operational Technology Cybersecurity Controls',
       N'ضوابط الأمن السيبراني للتقنيات التشغيلية',
       'OT / ICS cybersecurity for industrial environments.',
       N'الأمن السيبراني للأنظمة الصناعية والتقنيات التشغيلية.',
       1, 'ACTIVE'),
      (1, 'NCA-DCC-V1', 'NCA Cyber', 'KSA', 'NCA', '1.0',
       'Data Cybersecurity Controls',
       N'ضوابط أمن البيانات',
       'Cybersecurity controls tiered by data classification.',
       N'ضوابط حماية البيانات حسب مستوى التصنيف.',
       1, 'ACTIVE'),
      (1, 'NCA-TCC-V1', 'NCA Cyber', 'KSA', 'NCA', '1.0',
       'Telecom Cybersecurity Controls',
       N'ضوابط الأمن السيبراني للاتصالات',
       'Cybersecurity controls for licensed telecom operators.',
       N'ضوابط الأمن السيبراني لشركات الاتصالات المرخّصة.',
       1, 'ACTIVE'),
      (1, 'PDPL-2023', 'Privacy', 'KSA', 'SDAIA', '2023',
       'Personal Data Protection Law',
       N'نظام حماية البيانات الشخصية',
       'Saudi Arabia''s personal data protection regime issued by SDAIA.',
       N'النظام السعودي لحماية البيانات الشخصية الصادر من سدايا.',
       1, 'ACTIVE'),
      (1, 'SAMA-BCM-FW', 'SAMA', 'KSA', 'SAMA', '1.0',
       'SAMA Business Continuity Management Framework',
       N'إطار إدارة استمرارية الأعمال — مؤسسة النقد',
       'BCM expectations for SAMA-supervised entities.',
       N'متطلبات استمرارية الأعمال للجهات الخاضعة لمؤسسة النقد.',
       1, 'ACTIVE'),
      (1, 'SAMA-CYBER-FW', 'SAMA', 'KSA', 'SAMA', '1.0',
       'SAMA Cyber Security Framework',
       N'إطار الأمن السيبراني — مؤسسة النقد',
       'Cybersecurity expectations for SAMA-supervised entities.',
       N'متطلبات الأمن السيبراني للجهات الخاضعة لمؤسسة النقد.',
       1, 'ACTIVE'),
      (1, 'SAMA-OUTSOURCING', 'SAMA', 'KSA', 'SAMA', '1.0',
       'SAMA Outsourcing Regulation',
       N'لائحة التعهيد — مؤسسة النقد',
       'Third-party and outsourcing risk requirements for financial entities.',
       N'متطلبات إدارة مخاطر التعهيد والأطراف الخارجية للجهات المالية.',
       1, 'ACTIVE'),
      (1, 'SAMA-OP-RISK', 'SAMA', 'KSA', 'SAMA', '1.0',
       'SAMA Operational Risk Framework',
       N'إطار المخاطر التشغيلية — مؤسسة النقد',
       'Operational risk taxonomy + measurement for financial entities.',
       N'تصنيف وقياس المخاطر التشغيلية للجهات المالية.',
       1, 'ACTIVE'),
      (1, 'DGA-DIGITAL-MATURITY', 'Digital-Gov', 'KSA', 'DGA', '1.0',
       'DGA Digital Maturity Model',
       N'نموذج النضج الرقمي — هيئة الحكومة الرقمية',
       'Maturity model for government entities measured by DGA.',
       N'نموذج قياس النضج الرقمي للجهات الحكومية من هيئة الحكومة الرقمية.',
       1, 'ACTIVE'),
      (1, 'DGA-INTEROPERABILITY', 'Digital-Gov', 'KSA', 'DGA', '1.0',
       'DGA Interoperability Framework',
       N'إطار التشغيل البيني — هيئة الحكومة الرقمية',
       'Interoperability standards across government digital services.',
       N'معايير التشغيل البيني للخدمات الحكومية الرقمية.',
       1, 'ACTIVE'),
      (1, 'NRC-NATIONAL-RISKS', 'Resilience', 'KSA', 'NRC', '1.0',
       'National Risk Council — National Risk Catalog',
       N'الكتالوج الوطني للمخاطر — مجلس المخاطر الوطنية',
       'Cross-sector national risk register and resilience expectations.',
       N'سجل المخاطر الوطنية ومتطلبات الجاهزية عبر القطاعات.',
       1, 'ACTIVE'),
      (1, 'ISO-22301', 'BCM', 'International', 'ISO', '2019',
       'ISO 22301:2019 Business Continuity Management Systems',
       N'الأيزو 22301 — نظم إدارة استمرارية الأعمال',
       'International standard for BCMS — clauses 4 through 10.',
       N'المواصفة الدولية لنظم إدارة استمرارية الأعمال — البنود من 4 إلى 10.',
       1, 'ACTIVE'),
      (1, 'ISO-27001', 'Cyber', 'International', 'ISO', '2022',
       'ISO/IEC 27001:2022 Information Security Management Systems',
       N'الأيزو 27001 — نظم إدارة أمن المعلومات',
       'International standard for ISMS — Annex A controls.',
       N'المواصفة الدولية لنظم إدارة أمن المعلومات — ضوابط الملحق أ.',
       1, 'ACTIVE'),
      (1, 'ISO-27701', 'Privacy', 'International', 'ISO', '2019',
       'ISO/IEC 27701:2019 Privacy Information Management',
       N'الأيزو 27701 — إدارة معلومات الخصوصية',
       'Privacy extension on top of ISO 27001 for PIMS.',
       N'امتداد الخصوصية فوق الأيزو 27001 لنظام إدارة معلومات الخصوصية.',
       1, 'ACTIVE'),
      (1, 'ISO-31000', 'Risk', 'International', 'ISO', '2018',
       'ISO 31000:2018 Risk Management Guidelines',
       N'الأيزو 31000 — إرشادات إدارة المخاطر',
       'International guidance on enterprise risk management.',
       N'الإرشادات الدولية لإدارة المخاطر المؤسسية.',
       1, 'ACTIVE'),
      (1, 'SOC2-2017', 'Cyber', 'International', 'AICPA', '2017',
       'SOC 2 Trust Services Criteria',
       N'معايير الخدمات الموثوقة — SOC 2',
       'AICPA trust services criteria (Security, Availability, Processing Integrity, Confidentiality, Privacy).',
       N'معايير الخدمات الموثوقة من AICPA لـ SOC 2.',
       1, 'ACTIVE');
END
GO

-- ─── Seed sample citations (ECC v2 placeholder skeleton) ──────────
-- Just enough to prove the schema + give the frontend something to
-- render. Real citations come with the official-text import.

DECLARE @eccId BIGINT = (SELECT id FROM platform.authority_documents
                          WHERE organisation_id = 1 AND code = 'NCA-ECC-V2');

IF @eccId IS NOT NULL AND NOT EXISTS
   (SELECT 1 FROM platform.authority_document_citations
    WHERE authority_document_id = @eccId AND citation_code = '1')
BEGIN
    INSERT INTO platform.authority_document_citations
      (organisation_id, authority_document_id, citation_code, parent_citation_code,
       depth, sequence_order, title_en, title_ar, is_placeholder)
    VALUES
      (1, @eccId, '1',   NULL, 0, 10, 'Cybersecurity Governance',
       N'حوكمة الأمن السيبراني', 1),
      (1, @eccId, '1-1', '1',  1, 11, 'Cybersecurity Strategy',
       N'استراتيجية الأمن السيبراني', 1),
      (1, @eccId, '1-2', '1',  1, 12, 'Cybersecurity Policies and Procedures',
       N'سياسات وإجراءات الأمن السيبراني', 1),
      (1, @eccId, '2',   NULL, 0, 20, 'Cybersecurity Defense',
       N'الدفاع السيبراني', 1),
      (1, @eccId, '2-1', '2',  1, 21, 'Asset Management',
       N'إدارة الأصول', 1),
      (1, @eccId, '2-2', '2',  1, 22, 'Identity and Access Management',
       N'إدارة الهويات والصلاحيات', 1),
      (1, @eccId, '3',   NULL, 0, 30, 'Cybersecurity Resilience',
       N'صمود الأمن السيبراني', 1),
      (1, @eccId, '4',   NULL, 0, 40, 'Third-Party and Cloud Cybersecurity',
       N'الأمن السيبراني للأطراف الخارجية والحوسبة السحابية', 1),
      (1, @eccId, '5',   NULL, 0, 50, 'Industrial Control Systems Cybersecurity',
       N'الأمن السيبراني لأنظمة التحكم الصناعي', 1);
END
GO
