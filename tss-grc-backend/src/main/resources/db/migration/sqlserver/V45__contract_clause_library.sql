-- V45 — Contractual resilience clause library (gap-analysis item G).
-- ===========================================================================
-- A standalone library of standard clause templates that legal + procurement
-- + vendor-risk teams can reference when drafting or reviewing contracts.
-- Decoupled from any particular Contract row — clauses are templates that
-- get *cited* by contracts via clause_code, not embedded.
--
-- Categories cover the resilience-relevant clause families called out by
-- the Saudi GRC prompt §3.3 + NCA CSCC §4 + PDPL Article 25:
--   DATA_PROTECTION    PDPL alignment, residency, breach notice
--   SLA                Uptime, response, restoration
--   AUDIT_RIGHTS       Right to audit, evidence, sample size
--   EXIT_RIGHTS        Disengagement, data return, transition support
--   SECURITY           NCA control coverage, certifications
--   COMPLIANCE         Regulatory alignment, change notification
--   LIABILITY          Caps, carve-outs, indirect damages
--   SUBPROCESSORS      Chain of custody, prior consent
--   FORCE_MAJEURE      Trigger definitions, notice, mitigation
--   OTHER
--
-- Lifecycle: DRAFT → APPROVED → DEPRECATED.
-- ===========================================================================

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'scm.contract_clauses', N'U') IS NULL
BEGIN
    CREATE TABLE scm.contract_clauses (
        id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id     BIGINT NOT NULL,

        clause_code         NVARCHAR(64)  NOT NULL,
        title_en            NVARCHAR(255) NOT NULL,
        title_ar            NVARCHAR(255) NULL,

        category            NVARCHAR(20)  NOT NULL,
        applicable_to       NVARCHAR(MAX) NULL,         -- CSV: MSA,SOW,DPA,etc.

        body_en             NVARCHAR(MAX) NOT NULL,
        body_ar             NVARCHAR(MAX) NULL,

        guidance_en         NVARCHAR(MAX) NULL,          -- when + how to use
        guidance_ar         NVARCHAR(MAX) NULL,

        regulatory_refs     NVARCHAR(MAX) NULL,          -- e.g. "PDPL Art.25, NCA CSCC-4-1"
        risk_level          NVARCHAR(10)  NOT NULL DEFAULT N'MEDIUM',
                                                          -- LOW | MEDIUM | HIGH | CRITICAL
        negotiability       NVARCHAR(15)  NOT NULL DEFAULT N'NEGOTIABLE',
                                                          -- FIXED | NEGOTIABLE | DEFAULT
        version             NVARCHAR(20)  NOT NULL DEFAULT N'1.0',

        status              NVARCHAR(20)  NOT NULL DEFAULT N'APPROVED',
                                                          -- DRAFT | APPROVED | DEPRECATED
        approved_at         DATETIME2(3)  NULL,
        approved_by         BIGINT        NULL,
        approver_name       NVARCHAR(255) NULL,
        deprecated_at       DATETIME2(3)  NULL,
        deprecation_reason  NVARCHAR(500) NULL,

        owner               NVARCHAR(255) NULL,           -- General Counsel, etc.
        tags                NVARCHAR(MAX) NULL,           -- CSV

        external_id         NVARCHAR(64)  NULL,
        is_deleted          SMALLINT      NOT NULL DEFAULT 0,
        is_active           SMALLINT      NOT NULL DEFAULT 1,
        deleted_at          DATETIME2(3)  NULL,
        deleted_by          BIGINT        NULL,
        created_at          DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT        NULL,
        updated_at          DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by          BIGINT        NULL,
        row_version         BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_clause_category CHECK (category IN
            (N'DATA_PROTECTION', N'SLA', N'AUDIT_RIGHTS', N'EXIT_RIGHTS',
             N'SECURITY', N'COMPLIANCE', N'LIABILITY', N'SUBPROCESSORS',
             N'FORCE_MAJEURE', N'OTHER')),
        CONSTRAINT CK_clause_risk CHECK (risk_level IN
            (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL')),
        CONSTRAINT CK_clause_negotiability CHECK (negotiability IN
            (N'FIXED', N'NEGOTIABLE', N'DEFAULT')),
        CONSTRAINT CK_clause_status CHECK (status IN
            (N'DRAFT', N'APPROVED', N'DEPRECATED')),
        CONSTRAINT UQ_clause_code UNIQUE (organisation_id, clause_code)
    );
END
GO

IF OBJECT_ID(N'scm.contract_clauses', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_clause_cat_status' AND object_id = OBJECT_ID(N'scm.contract_clauses'))
BEGIN
    CREATE INDEX IX_clause_cat_status ON scm.contract_clauses
        (organisation_id, category, status) WHERE is_deleted = 0;
END
GO

-- ─── Seed: 22 representative clauses across 10 categories ─────────────
IF OBJECT_ID(N'scm.contract_clauses', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM scm.contract_clauses WHERE clause_code = N'CLS-DP-01')
BEGIN
    INSERT INTO scm.contract_clauses
        (organisation_id, clause_code, title_en, title_ar,
         category, applicable_to, body_en, body_ar,
         guidance_en, guidance_ar, regulatory_refs,
         risk_level, negotiability, version,
         status, approved_at, approved_by, approver_name,
         owner, tags, external_id)
    VALUES
        -- ═══ DATA_PROTECTION (3) ════════════════════════════════════
        (1, N'CLS-DP-01',
         N'PDPL data-protection compliance',
         N'الامتثال لنظام حماية البيانات الشخصية',
         N'DATA_PROTECTION', N'MSA,DPA,SOW',
         N'The Supplier shall comply with the Saudi Personal Data Protection Law (PDPL) and its Executive Regulations as Data Processor on behalf of the Customer, including: (a) processing personal data only on documented instructions from the Customer; (b) implementing appropriate technical and organisational measures to ensure security of processing per NCA CSCC §4; (c) notifying the Customer within seventy-two (72) hours of any personal-data breach; (d) assisting the Customer with Data Subject Rights requests within twenty (20) days of receipt; (e) deleting or returning all personal data at the end of the agreement.',
         N'يلتزم المورد بنظام حماية البيانات الشخصية ولوائحه التنفيذية بصفته معالجاً للبيانات نيابة عن العميل، بما يشمل: (أ) معالجة البيانات الشخصية فقط بناءً على تعليمات موثقة من العميل؛ (ب) تطبيق التدابير التقنية والتنظيمية المناسبة لضمان أمان المعالجة وفق ضوابط NCA CSCC الفصل 4؛ (ج) إبلاغ العميل خلال 72 ساعة من أي اختراق للبيانات الشخصية؛ (د) مساعدة العميل في طلبات حقوق أصحاب البيانات خلال 20 يوماً؛ (هـ) حذف أو إرجاع جميع البيانات الشخصية في نهاية الاتفاقية.',
         N'Mandatory in every contract where the supplier processes personal data on behalf of TSS. Pair with CLS-DP-02 for cross-border transfers and CLS-DP-03 for breach notification process.',
         N'إلزامي في كل عقد يعالج فيه المورد بيانات شخصية نيابة عن TSS. اقترنه بـ CLS-DP-02 للتحويلات العابرة للحدود و CLS-DP-03 لإجراء إبلاغ الاختراق.',
         N'PDPL Articles 5, 18-22, 27, 38; NCA CSCC-4-1; ISO 27701',
         N'CRITICAL', N'FIXED', N'2.1',
         N'APPROVED', DATEADD(MONTH, -6, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel + DPO', N'PDPL,CRITICAL,FIXED,DPA',
         N'CLS-DP-01'),

        (1, N'CLS-DP-02',
         N'Cross-border transfer of personal data',
         N'التحويل العابر للحدود للبيانات الشخصية',
         N'DATA_PROTECTION', N'DPA,SOW',
         N'Any cross-border transfer of personal data shall comply with PDPL Articles 27-30 and SDAIA guidance. The Supplier shall: (a) execute SDAIA-approved Standard Contractual Clauses prior to transfer; (b) maintain a register of all cross-border transfers; (c) obtain prior written approval from the Customer for any new destination country; (d) ensure adequate level of protection in the destination country per SDAIA''s adequacy decisions.',
         N'يلتزم أي تحويل عابر للحدود للبيانات الشخصية بالمواد 27-30 من نظام حماية البيانات الشخصية وإرشادات سدايا.',
         N'Required when the supplier processes data outside KSA. Pair with CBT workflow (privacy/cross-border-transfers).',
         N'مطلوب عندما يعالج المورد بيانات خارج المملكة. اقترنه بسير عمل CBT.',
         N'PDPL Articles 27-30; SDAIA Cross-Border Guidance 2023',
         N'CRITICAL', N'FIXED', N'1.3',
         N'APPROVED', DATEADD(MONTH, -4, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel + DPO', N'PDPL,CROSS_BORDER,CRITICAL',
         N'CLS-DP-02'),

        (1, N'CLS-DP-03',
         N'Personal-data breach notification',
         N'إبلاغ اختراق البيانات الشخصية',
         N'DATA_PROTECTION', N'MSA,DPA,SOW',
         N'In the event of a personal-data breach, the Supplier shall: (a) notify the Customer Data Protection Officer (DPO) without undue delay and in any event within twenty-four (24) hours of becoming aware; (b) provide full incident details including affected data subjects, categories, volumes, and breach vector; (c) cooperate with the Customer''s breach response including SDAIA notification within 72 hours; (d) implement immediate containment measures; (e) provide post-incident lessons-learned report within thirty (30) days.',
         N'في حال اختراق البيانات الشخصية، يلتزم المورد بإبلاغ مسؤول حماية البيانات لدى العميل خلال 24 ساعة من العلم بالاختراق وتوفير تفاصيل كاملة عن الحادثة.',
         N'24-hour SLA is tighter than PDPL''s 72h to give TSS DPO time to assess + escalate to SDAIA. Non-negotiable for processors handling SENSITIVE category data.',
         N'مهلة 24 ساعة أضيق من 72 ساعة في النظام لإعطاء مسؤول حماية البيانات وقتاً للتقييم والتصعيد إلى سدايا.',
         N'PDPL Article 20; NCA CSCC-2-13',
         N'CRITICAL', N'FIXED', N'1.5',
         N'APPROVED', DATEADD(MONTH, -3, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel + DPO', N'PDPL,BREACH,CRITICAL',
         N'CLS-DP-03'),

        -- ═══ SLA (3) ════════════════════════════════════════════════
        (1, N'CLS-SLA-01',
         N'Service availability — Tier-1',
         N'توافر الخدمة - الطبقة الأولى',
         N'SLA', N'MSA,SOW',
         N'Tier-1 services shall maintain monthly availability of not less than 99.95% measured 24x7x365, excluding planned maintenance windows (not exceeding four (4) hours per quarter, with minimum 14 days prior notice). Availability shall be measured at the customer-facing endpoint. Failure to meet the availability SLA shall result in service credits per the Service Credit Schedule attached as Exhibit A.',
         N'تحافظ خدمات الطبقة الأولى على توافر شهري لا يقل عن 99.95% على مدار الساعة، باستثناء نوافذ الصيانة المخططة (بحد أقصى 4 ساعات لكل ربع سنة).',
         N'Use for any service supporting customer-facing critical processes. Pair with CLS-SLA-03 for response-time SLAs.',
         N'يُستخدم لأي خدمة تدعم العمليات الحرجة المواجهة للعملاء.',
         N'ISO 22301; SAMA Cyber Security Framework',
         N'HIGH', N'NEGOTIABLE', N'2.0',
         N'APPROVED', DATEADD(MONTH, -8, SYSUTCDATETIME()), 1, N'CCO',
         N'Chief Commercial Officer', N'SLA,AVAILABILITY,TIER1',
         N'CLS-SLA-01'),

        (1, N'CLS-SLA-02',
         N'Service availability — Tier-2',
         N'توافر الخدمة - الطبقة الثانية',
         N'SLA', N'MSA,SOW',
         N'Tier-2 services shall maintain monthly availability of not less than 99.5% during business hours (Sunday-Thursday, 07:00-19:00 KSA time). Service credits as per Exhibit A.',
         N'تحافظ خدمات الطبقة الثانية على توافر شهري لا يقل عن 99.5% خلال ساعات العمل.',
         N'Standard tier for back-office and non-customer-facing services.',
         N'الطبقة القياسية للخدمات الداخلية وغير المواجهة للعملاء.',
         N'ISO 22301',
         N'MEDIUM', N'NEGOTIABLE', N'1.2',
         N'APPROVED', DATEADD(MONTH, -8, SYSUTCDATETIME()), 1, N'CCO',
         N'Chief Commercial Officer', N'SLA,AVAILABILITY,TIER2',
         N'CLS-SLA-02'),

        (1, N'CLS-SLA-03',
         N'Incident response and restoration',
         N'الاستجابة للحوادث والاستعادة',
         N'SLA', N'MSA,SOW',
         N'The Supplier shall acknowledge Severity-1 incidents within 15 minutes, Severity-2 within 60 minutes, Severity-3 within 4 hours. Severity-1 restoration target: 4 hours; Severity-2: 8 hours; Severity-3: 24 hours. Response timer starts at incident creation in the Customer''s ticketing system.',
         N'يلتزم المورد بالاستجابة لحوادث الخطورة الأولى خلال 15 دقيقة، الثانية خلال 60 دقيقة، الثالثة خلال 4 ساعات.',
         N'Standard response matrix. Customise severity definitions per service.',
         N'مصفوفة استجابة قياسية.',
         N'ITIL v4; NCA CSCC-2-13',
         N'HIGH', N'NEGOTIABLE', N'1.4',
         N'APPROVED', DATEADD(MONTH, -7, SYSUTCDATETIME()), 1, N'CCO',
         N'Chief Commercial Officer', N'SLA,RESPONSE,INCIDENT',
         N'CLS-SLA-03'),

        -- ═══ AUDIT_RIGHTS (2) ═══════════════════════════════════════
        (1, N'CLS-AUD-01',
         N'Right to audit',
         N'حق المراجعة',
         N'AUDIT_RIGHTS', N'MSA,DPA',
         N'The Customer (or its appointed third-party auditor) shall have the right to audit the Supplier''s compliance with this Agreement annually, with thirty (30) days'' prior written notice. The audit scope may include: (a) cybersecurity controls per NCA ECC/CSCC/CCC/OTCC as applicable; (b) data-protection practices per PDPL; (c) SLA performance evidence; (d) subprocessor management; (e) financial-stability indicators where service-criticality warrants. The Supplier shall provide reasonable cooperation and access to relevant personnel and documentation.',
         N'يحق للعميل أو مراجع خارجي معين تدقيق امتثال المورد سنوياً بإشعار مسبق 30 يوماً.',
         N'Standard right-to-audit. For Tier-1 services pair with CLS-AUD-02 for on-site rights.',
         N'حق المراجعة القياسي. للطبقة الأولى اقترنه بـ CLS-AUD-02 لحقوق المراجعة الميدانية.',
         N'NCA CSCC-1-8; ISO 27001 A.5.34',
         N'HIGH', N'FIXED', N'1.3',
         N'APPROVED', DATEADD(MONTH, -5, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel', N'AUDIT,RIGHT_TO_AUDIT',
         N'CLS-AUD-01'),

        (1, N'CLS-AUD-02',
         N'SOC 2 / ISO 27001 evidence-package delivery',
         N'تسليم حزمة أدلة SOC 2 / ISO 27001',
         N'AUDIT_RIGHTS', N'MSA,DPA',
         N'The Supplier shall provide annually, no later than 60 days after their fiscal year-end: (a) current SOC 2 Type II report covering Security, Availability, and Confidentiality trust principles; (b) ISO 27001 certificate with Statement of Applicability; (c) NCA framework compliance attestation where applicable; (d) penetration-test executive summary covering the services in scope.',
         N'يقدم المورد سنوياً خلال 60 يوماً من نهاية سنته المالية: تقرير SOC 2 Type II، شهادة ISO 27001 مع بيان قابلية التطبيق، وملخص اختبار الاختراق.',
         N'Use for cloud + critical SaaS suppliers. SOC 2 is the de-facto evidence pack.',
         N'يُستخدم للموردين السحابيين و SaaS الحرجين.',
         N'AICPA SOC 2; ISO 27001; NCA CCC-4-1',
         N'HIGH', N'FIXED', N'1.1',
         N'APPROVED', DATEADD(MONTH, -5, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel', N'AUDIT,SOC2,ISO27001',
         N'CLS-AUD-02'),

        -- ═══ EXIT_RIGHTS (2) ════════════════════════════════════════
        (1, N'CLS-EXT-01',
         N'Termination assistance and exit support',
         N'مساعدة الإنهاء ودعم الخروج',
         N'EXIT_RIGHTS', N'MSA,SOW',
         N'Upon termination or expiry of this Agreement (for any reason), the Supplier shall provide a transition period of up to ninety (90) days during which it shall: (a) continue providing services at the contracted rates; (b) cooperate with the Customer''s replacement supplier or in-sourcing project; (c) provide knowledge-transfer sessions, runbooks, and architecture documentation; (d) return or destroy Customer Data per CLS-EXT-02; (e) destroy any residual Customer Confidential Information per a documented procedure with certificate of destruction.',
         N'عند إنهاء أو انتهاء الاتفاقية، يقدم المورد فترة انتقال تصل إلى 90 يوماً يستمر فيها بتقديم الخدمات والتعاون مع المورد البديل.',
         N'Pair with CLS-EXT-02 (data return) and CLS-EXT-03 (data destruction). Required for any Tier-1 or Tier-2 supplier.',
         N'اقترنه بـ CLS-EXT-02 و CLS-EXT-03. مطلوب لأي مورد من الطبقة الأولى أو الثانية.',
         N'NCA CSCC-4-3; SAMA Cyber Security Framework',
         N'HIGH', N'NEGOTIABLE', N'2.0',
         N'APPROVED', DATEADD(MONTH, -4, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel + Procurement', N'EXIT,DISENGAGEMENT,TRANSITION',
         N'CLS-EXT-01'),

        (1, N'CLS-EXT-02',
         N'Data return and destruction',
         N'إرجاع وإتلاف البيانات',
         N'EXIT_RIGHTS', N'MSA,DPA,SOW',
         N'Upon termination, the Supplier shall (within 30 days) return all Customer Data in a structured, commonly-used, machine-readable format and securely delete all residual copies. The Supplier shall provide a Certificate of Destruction signed by an authorised officer. Where deletion is technically infeasible (e.g. due to immutable backups), the Supplier shall maintain such data subject to ongoing PDPL and NCA controls until natural expiration.',
         N'عند الإنهاء، يُعيد المورد جميع بيانات العميل خلال 30 يوماً بصيغة منظمة قابلة للقراءة آلياً ويحذف جميع النسخ المتبقية ويقدم شهادة إتلاف.',
         N'Critical for PDPL Article 5 alignment. Tie to disposal certificate workflow (privacy/disposal-certificates).',
         N'حرج للتوافق مع المادة 5 من نظام حماية البيانات الشخصية.',
         N'PDPL Article 5; NCA CSCC-4-3',
         N'CRITICAL', N'FIXED', N'1.4',
         N'APPROVED', DATEADD(MONTH, -3, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel + DPO', N'EXIT,DATA_RETURN,DESTRUCTION',
         N'CLS-EXT-02'),

        -- ═══ SECURITY (3) ═══════════════════════════════════════════
        (1, N'CLS-SEC-01',
         N'NCA ECC alignment',
         N'التوافق مع NCA ECC',
         N'SECURITY', N'MSA,SOW',
         N'The Supplier shall implement and maintain a cybersecurity programme aligned with NCA Essential Cybersecurity Controls (ECC v2.0) at all times during the Term. Evidence of alignment (gap assessment, control coverage matrix) shall be provided upon onboarding and annually thereafter.',
         N'يُطبق المورد ويُحافظ على برنامج للأمن السيبراني متوافق مع ضوابط NCA ECC الإصدار 2.0 طوال مدة الاتفاقية.',
         N'Mandatory for any supplier processing TSS data. Pair with CLS-AUD-02 for evidence delivery.',
         N'إلزامي لأي مورد يعالج بيانات TSS.',
         N'NCA ECC v2.0',
         N'CRITICAL', N'FIXED', N'2.0',
         N'APPROVED', DATEADD(MONTH, -6, SYSUTCDATETIME()), 1, N'CISO',
         N'CISO + General Counsel', N'NCA,ECC,SECURITY,CRITICAL',
         N'CLS-SEC-01'),

        (1, N'CLS-SEC-02',
         N'Background checks and personnel security',
         N'فحوصات الخلفية وأمن الموظفين',
         N'SECURITY', N'MSA,SOW',
         N'Supplier personnel with access to Customer systems or data shall undergo enhanced background screening (criminal-record check, employment verification, education verification, professional references) prior to access being granted, and re-verification every three (3) years. The Supplier shall maintain evidence of completed screenings.',
         N'يخضع موظفو المورد الذين لديهم وصول لأنظمة العميل أو بياناته لفحوصات خلفية معززة قبل منح الوصول، وإعادة التحقق كل 3 سنوات.',
         N'Required for any supplier with access to sensitive data or critical systems.',
         N'مطلوب لأي مورد لديه وصول لبيانات حساسة أو أنظمة حرجة.',
         N'NCA ECC-1-9; NCA CSCC-1-10',
         N'HIGH', N'NEGOTIABLE', N'1.2',
         N'APPROVED', DATEADD(MONTH, -7, SYSUTCDATETIME()), 1, N'CHRO',
         N'CHRO + CISO', N'PERSONNEL,SCREENING,SECURITY',
         N'CLS-SEC-02'),

        (1, N'CLS-SEC-03',
         N'Encryption requirements',
         N'متطلبات التشفير',
         N'SECURITY', N'MSA,DPA,SOW',
         N'All Customer Data shall be encrypted at-rest using algorithms approved by NCA (minimum AES-256-GCM) and in-transit using TLS 1.2 or higher with strong cipher suites. Cryptographic keys shall be managed in a FIPS-140-2 Level 2 (or higher) certified key store with documented rotation, escrow, and destruction procedures.',
         N'تُشفر جميع بيانات العميل عند التخزين باستخدام خوارزميات معتمدة من NCA (AES-256-GCM كحد أدنى) وعند النقل باستخدام TLS 1.2 أو أعلى.',
         N'Non-negotiable for any service handling personal data, financial data, or critical operational data.',
         N'غير قابل للتفاوض لأي خدمة تتعامل مع بيانات شخصية أو مالية أو تشغيلية حرجة.',
         N'NCA ECC-2-7; NCA CSCC-2-8; NCA CCC-2-5; PDPL Article 19',
         N'CRITICAL', N'FIXED', N'2.1',
         N'APPROVED', DATEADD(MONTH, -2, SYSUTCDATETIME()), 1, N'CISO',
         N'CISO', N'ENCRYPTION,CRYPTOGRAPHY,SECURITY',
         N'CLS-SEC-03'),

        -- ═══ COMPLIANCE (2) ═════════════════════════════════════════
        (1, N'CLS-COM-01',
         N'Saudi data residency',
         N'إقامة البيانات في المملكة العربية السعودية',
         N'COMPLIANCE', N'MSA,DPA,SOW',
         N'All Customer Data and any backups, replicas, or derivative datasets shall be stored within the geographic borders of the Kingdom of Saudi Arabia at all times. Any access from outside KSA shall require explicit prior written consent from the Customer DPO and may be subject to additional safeguards per CLS-DP-02.',
         N'تُخزن جميع بيانات العميل وأي نسخ احتياطية أو مكررة أو مشتقة داخل الحدود الجغرافية للمملكة العربية السعودية في جميع الأوقات.',
         N'Required for SAMA-regulated and government sector contracts; recommended elsewhere.',
         N'مطلوب لعقود القطاع المالي والحكومي.',
         N'PDPL Articles 27-30; SAMA Cyber Security Framework; SDAIA Cross-Border Guidance',
         N'CRITICAL', N'NEGOTIABLE', N'1.6',
         N'APPROVED', DATEADD(MONTH, -5, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel + DPO', N'DATA_RESIDENCY,KSA,COMPLIANCE',
         N'CLS-COM-01'),

        (1, N'CLS-COM-02',
         N'Regulatory-change notification',
         N'إشعار التغييرات التنظيمية',
         N'COMPLIANCE', N'MSA',
         N'The Supplier shall notify the Customer in writing within fifteen (15) business days of any change in applicable laws, regulations, or regulatory guidance that materially affects the services or the Customer''s use of them. The parties shall negotiate in good faith any required amendments to the Agreement.',
         N'يُبلغ المورد العميل كتابياً خلال 15 يوم عمل من أي تغيير في القوانين أو اللوائح أو الإرشادات التنظيمية المعمول بها.',
         N'Standard regulatory-change clause. Recommended for all multi-year contracts.',
         N'بند تغيير تنظيمي قياسي. موصى به لجميع العقود متعددة السنوات.',
         N'SAMA Cyber Security Framework',
         N'MEDIUM', N'NEGOTIABLE', N'1.0',
         N'APPROVED', DATEADD(MONTH, -10, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel', N'REGULATORY,CHANGE_NOTICE',
         N'CLS-COM-02'),

        -- ═══ LIABILITY (2) ══════════════════════════════════════════
        (1, N'CLS-LIA-01',
         N'Limitation of liability — standard cap',
         N'حد المسؤولية - السقف القياسي',
         N'LIABILITY', N'MSA,SOW',
         N'Except for liability arising from (a) breach of confidentiality, (b) breach of data-protection obligations, (c) infringement of third-party IP rights, (d) gross negligence or wilful misconduct, or (e) indemnification obligations, each party''s aggregate liability under this Agreement shall be capped at twelve (12) months of fees paid in the period immediately preceding the claim.',
         N'باستثناء المسؤوليات المحددة، تُحد المسؤولية الإجمالية لكل طرف بسقف يعادل رسوم 12 شهراً مدفوعة في الفترة السابقة مباشرة للمطالبة.',
         N'Standard liability cap. The carve-outs (a-e) are non-negotiable for TSS.',
         N'سقف المسؤولية القياسي. الاستثناءات (أ-هـ) غير قابلة للتفاوض.',
         N'Saudi Civil Transactions Law',
         N'HIGH', N'NEGOTIABLE', N'1.5',
         N'APPROVED', DATEADD(MONTH, -8, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel', N'LIABILITY,CAP',
         N'CLS-LIA-01'),

        (1, N'CLS-LIA-02',
         N'Indemnification — IP and data breach',
         N'التعويض - الملكية الفكرية واختراق البيانات',
         N'LIABILITY', N'MSA,SOW',
         N'The Supplier shall indemnify, defend and hold harmless the Customer from any third-party claims arising from (a) infringement of intellectual-property rights by the services; (b) breach of personal-data obligations causing regulatory fines under PDPL or other applicable laws; (c) breach of confidentiality. The indemnity shall be uncapped where caused by gross negligence or wilful misconduct.',
         N'يُعوض المورد العميل عن أي مطالبات طرف ثالث ناشئة عن انتهاك الملكية الفكرية، أو اختراق التزامات البيانات الشخصية، أو خرق السرية.',
         N'Non-negotiable indemnification for PDPL fines + IP infringement.',
         N'تعويض غير قابل للتفاوض لغرامات نظام حماية البيانات الشخصية والملكية الفكرية.',
         N'PDPL Articles 42-43; Saudi Civil Transactions Law',
         N'HIGH', N'FIXED', N'1.3',
         N'APPROVED', DATEADD(MONTH, -6, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel', N'LIABILITY,INDEMNIFICATION,IP,PDPL',
         N'CLS-LIA-02'),

        -- ═══ SUBPROCESSORS (2) ══════════════════════════════════════
        (1, N'CLS-SUB-01',
         N'Subprocessor approval and chain of custody',
         N'موافقة المعالج الفرعي وسلسلة الحفظ',
         N'SUBPROCESSORS', N'DPA,MSA',
         N'The Supplier shall maintain an up-to-date list of all subprocessors engaged in processing Customer Personal Data. Engagement of any new subprocessor requires the Customer''s prior written consent (not to be unreasonably withheld). The Supplier shall flow-down all data-protection obligations to subprocessors via written contract and remains fully liable for their acts and omissions.',
         N'يحتفظ المورد بقائمة محدثة بجميع المعالجين الفرعيين. يتطلب إشراك أي معالج فرعي جديد موافقة كتابية مسبقة من العميل.',
         N'PDPL-mandated. Pair with CLS-SUB-02 for sub-tier visibility.',
         N'مطلوب وفق نظام حماية البيانات الشخصية. اقترنه بـ CLS-SUB-02 لرؤية الطبقات الفرعية.',
         N'PDPL Article 5; NCA CSCC-4-1',
         N'HIGH', N'FIXED', N'1.4',
         N'APPROVED', DATEADD(MONTH, -4, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel + DPO', N'SUBPROCESSOR,FOURTH_PARTY,PDPL',
         N'CLS-SUB-01'),

        (1, N'CLS-SUB-02',
         N'Subprocessor transparency and audit',
         N'شفافية وتدقيق المعالج الفرعي',
         N'SUBPROCESSORS', N'DPA',
         N'The Supplier shall provide the Customer, upon request, with: (a) the identity and contact details of each subprocessor; (b) the nature and location of processing performed by each subprocessor; (c) the SOC 2 / ISO 27001 attestation status of each subprocessor; (d) the Customer''s right to audit each subprocessor (flowed down) under the same conditions as CLS-AUD-01.',
         N'يقدم المورد للعميل عند الطلب: هوية المعالج الفرعي وموقعه ووضع اعتماده وحق التدقيق.',
         N'Transparency clause for fourth-party risk visibility.',
         N'بند شفافية لرؤية مخاطر الطرف الرابع.',
         N'PDPL Article 5',
         N'MEDIUM', N'NEGOTIABLE', N'1.0',
         N'APPROVED', DATEADD(MONTH, -2, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel + Vendor Risk', N'SUBPROCESSOR,TRANSPARENCY',
         N'CLS-SUB-02'),

        -- ═══ FORCE_MAJEURE (1) ══════════════════════════════════════
        (1, N'CLS-FM-01',
         N'Force majeure — definition and notice',
         N'القوة القاهرة - التعريف والإشعار',
         N'FORCE_MAJEURE', N'MSA,SOW',
         N'Neither party shall be liable for any delay or failure to perform attributable to a Force Majeure Event, defined as an event beyond the reasonable control of the affected party including: acts of God, war, terrorism, pandemics, government-mandated lockdowns, regulatory orders, large-scale internet or power outages. The affected party shall notify the other within five (5) business days, provide periodic status updates, and use reasonable efforts to mitigate.',
         N'لا يتحمل أي من الطرفين المسؤولية عن أي تأخير أو فشل في الأداء يُعزى إلى حدث قوة قاهرة. يُبلغ الطرف المتأثر الطرف الآخر خلال 5 أيام عمل.',
         N'Updated post-COVID. The 5-day notice is non-negotiable.',
         N'محدث بعد كوفيد. مهلة 5 أيام غير قابلة للتفاوض.',
         N'Saudi Civil Transactions Law Articles 175-178',
         N'MEDIUM', N'NEGOTIABLE', N'2.0',
         N'APPROVED', DATEADD(MONTH, -9, SYSUTCDATETIME()), 1, N'General Counsel',
         N'General Counsel', N'FORCE_MAJEURE,CONTINUITY',
         N'CLS-FM-01'),

        -- ═══ DRAFT + DEPRECATED examples ════════════════════════════
        (1, N'CLS-DP-04',
         N'Privacy-by-design certification (draft)',
         N'شهادة الخصوصية بالتصميم (مسودة)',
         N'DATA_PROTECTION', N'DPA',
         N'(Draft) The Supplier shall hold ISO 27701:2019 (PIMS) certification or equivalent privacy-management-system certification recognised by SDAIA, and provide annual surveillance audit reports.',
         N'(مسودة) يحمل المورد شهادة ISO 27701:2019 أو ما يعادلها.',
         N'Draft pending Legal review — adoption blocked on ISO 27701 KSA accreditation availability.',
         N'مسودة بانتظار مراجعة قانونية - الاعتماد متوقف على توفر اعتماد ISO 27701 في المملكة.',
         N'ISO 27701:2019; SDAIA Privacy Standards (forthcoming)',
         N'HIGH', N'NEGOTIABLE', N'0.3',
         N'DRAFT', NULL, NULL, NULL,
         N'General Counsel + DPO', N'PDPL,ISO27701,DRAFT',
         N'CLS-DP-04'),

        (1, N'CLS-SLA-99',
         N'Service availability — legacy 99% tier (deprecated)',
         N'توافر الخدمة - الطبقة القديمة 99% (مهجور)',
         N'SLA', N'MSA',
         N'(Deprecated) Legacy Tier-3 services shall maintain 99% monthly availability. Use CLS-SLA-02 for new contracts.',
         N'(مهجور) خدمات الطبقة الثالثة القديمة بنسبة توافر 99% شهرياً.',
         N'Deprecated 2025-Q4 — use CLS-SLA-02 (99.5% business hours) for new contracts.',
         N'مهجور الربع الرابع 2025 - استخدم CLS-SLA-02.',
         NULL,
         N'LOW', N'NEGOTIABLE', N'1.0',
         N'DEPRECATED', DATEADD(MONTH, -12, SYSUTCDATETIME()), 1, N'CCO',
         N'Chief Commercial Officer', N'SLA,DEPRECATED',
         N'CLS-SLA-99');

    UPDATE scm.contract_clauses
       SET deprecated_at      = DATEADD(MONTH, -6, SYSUTCDATETIME()),
           deprecation_reason = N'Superseded by CLS-SLA-02 — 99.5% business-hours tier replaces 99% all-hours tier.'
     WHERE clause_code = N'CLS-SLA-99';
END
GO

PRINT N'V45 — scm.contract_clauses table + 19 clauses across 8 categories applied.';
GO
