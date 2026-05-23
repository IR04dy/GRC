-- V164 — Risk Bow-Tie Analysis (ISO 31010).
--
-- Bow-tie analysis is the headline ISO 31010 risk-analysis technique that
-- Saudi-regulated banks ask for in SAMA / SOX-equivalent risk programs:
--
--     [threats]──┐                         ┌──[consequences]
--     [threats]──┼──preventive──◆──mitigating──┼──[consequences]
--     [threats]──┘   controls   top   controls  └──[consequences]
--                                event
--
-- Centre  = top event (the risk crystallising)
-- Left    = threats / hazards that could cause it  → preventive controls
-- Right   = consequences once it has happened       → mitigating controls
-- Each pathway carries a chain of barriers (controls) with effectiveness.
--
-- One header row (risk.bowtie_analysis) plus four child tables. All four
-- tables are org-scoped and soft-delete-filtered, mirroring the V35
-- risk.scenarios pattern. row_version uses BIGINT (BaseEntity audit
-- shape in this codebase isn't using rowversion natively for Hibernate,
-- but every existing risk.* table writes a BIGINT default-0 column).

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- Ensure the risk schema exists (defensive — already created by earlier migrations).
IF SCHEMA_ID(N'risk') IS NULL EXEC(N'CREATE SCHEMA risk');
GO

-- ─── 1. Header ─────────────────────────────────────────────────────
IF OBJECT_ID(N'risk.bowtie_analysis', N'U') IS NULL
BEGIN
    CREATE TABLE risk.bowtie_analysis (
        id                                BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id                   BIGINT NOT NULL,
        external_id                       UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        bowtie_code                       NVARCHAR(80)  NOT NULL,
        linked_risk_id                    BIGINT        NULL,

        top_event_title_en                NVARCHAR(500) NOT NULL,
        top_event_title_ar                NVARCHAR(500) NULL,
        top_event_description_en          NVARCHAR(MAX) NULL,
        top_event_description_ar          NVARCHAR(MAX) NULL,

        risk_category                     NVARCHAR(80)  NULL,
        assessment_date                   DATE          NOT NULL,
        assessed_by_name                  NVARCHAR(255) NULL,
        methodology                       NVARCHAR(60)  NOT NULL DEFAULT N'ISO_31010',

        overall_residual_rating           NVARCHAR(20)  NULL,
        overall_control_effectiveness     DECIMAL(5,2)  NULL,

        status                            NVARCHAR(30)  NOT NULL DEFAULT N'DRAFT',
        approved_at                       DATETIME2(3)  NULL,
        approved_by_name                  NVARCHAR(255) NULL,
        next_review_at                    DATE          NULL,

        -- Audit + soft-delete.
        is_deleted                        BIT           NOT NULL DEFAULT 0,
        deleted_at                        DATETIME2(3)  NULL,
        deleted_by                        BIGINT        NULL,
        created_at                        DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                        BIGINT        NULL,
        updated_at                        DATETIME2(3)  NULL,
        updated_by                        BIGINT        NULL,
        row_version                       ROWVERSION,

        CONSTRAINT CK_bowtie_status CHECK (status IN
            (N'DRAFT', N'IN_REVIEW', N'APPROVED', N'SUPERSEDED', N'ARCHIVED')),
        CONSTRAINT CK_bowtie_residual_rating CHECK (overall_residual_rating IS NULL
            OR overall_residual_rating IN (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT CK_bowtie_risk_category CHECK (risk_category IS NULL
            OR risk_category IN
            (N'OPERATIONAL', N'CYBER', N'REGULATORY', N'STRATEGIC',
             N'REPUTATIONAL', N'FINANCIAL', N'THIRD_PARTY', N'CLIMATE')),
        CONSTRAINT UQ_bowtie_code UNIQUE (organisation_id, bowtie_code)
    );
END
GO

IF OBJECT_ID(N'risk.bowtie_analysis', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_bowtie_org_status' AND object_id = OBJECT_ID(N'risk.bowtie_analysis'))
    CREATE INDEX IX_bowtie_org_status ON risk.bowtie_analysis (organisation_id, status) WHERE is_deleted = 0;
GO
IF OBJECT_ID(N'risk.bowtie_analysis', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_bowtie_org_risk' AND object_id = OBJECT_ID(N'risk.bowtie_analysis'))
    CREATE INDEX IX_bowtie_org_risk ON risk.bowtie_analysis (organisation_id, linked_risk_id) WHERE is_deleted = 0 AND linked_risk_id IS NOT NULL;
GO

-- ─── 2. Threats (left side) ───────────────────────────────────────
IF OBJECT_ID(N'risk.bowtie_threat', N'U') IS NULL
BEGIN
    CREATE TABLE risk.bowtie_threat (
        id                  BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id     BIGINT NOT NULL,
        external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        bowtie_id           BIGINT NOT NULL,

        threat_order        INT NOT NULL,
        title_en            NVARCHAR(300) NOT NULL,
        title_ar            NVARCHAR(300) NULL,
        description_en      NVARCHAR(MAX) NULL,
        description_ar      NVARCHAR(MAX) NULL,
        inherent_likelihood NVARCHAR(20)  NULL,
        residual_likelihood NVARCHAR(20)  NULL,
        source              NVARCHAR(120) NULL,

        is_deleted          BIT           NOT NULL DEFAULT 0,
        deleted_at          DATETIME2(3)  NULL,
        deleted_by          BIGINT        NULL,
        created_at          DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by          BIGINT        NULL,
        updated_at          DATETIME2(3)  NULL,
        updated_by          BIGINT        NULL,
        row_version         ROWVERSION,

        CONSTRAINT FK_bowtie_threat_bowtie FOREIGN KEY (bowtie_id)
            REFERENCES risk.bowtie_analysis(id),
        CONSTRAINT CK_bowtie_threat_inh CHECK (inherent_likelihood IS NULL
            OR inherent_likelihood IN (N'RARE', N'UNLIKELY', N'POSSIBLE', N'LIKELY', N'ALMOST_CERTAIN')),
        CONSTRAINT CK_bowtie_threat_res CHECK (residual_likelihood IS NULL
            OR residual_likelihood IN (N'RARE', N'UNLIKELY', N'POSSIBLE', N'LIKELY', N'ALMOST_CERTAIN'))
    );
END
GO

IF OBJECT_ID(N'risk.bowtie_threat', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_bowtie_threat_bowtie' AND object_id = OBJECT_ID(N'risk.bowtie_threat'))
    CREATE INDEX IX_bowtie_threat_bowtie ON risk.bowtie_threat (bowtie_id, threat_order) WHERE is_deleted = 0;
GO

-- ─── 3. Consequences (right side) ─────────────────────────────────
IF OBJECT_ID(N'risk.bowtie_consequence', N'U') IS NULL
BEGIN
    CREATE TABLE risk.bowtie_consequence (
        id                   BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id      BIGINT NOT NULL,
        external_id          UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        bowtie_id            BIGINT NOT NULL,

        consequence_order    INT NOT NULL,
        title_en             NVARCHAR(300) NOT NULL,
        title_ar             NVARCHAR(300) NULL,
        description_en       NVARCHAR(MAX) NULL,
        description_ar       NVARCHAR(MAX) NULL,
        consequence_type     NVARCHAR(40)  NULL,
        inherent_severity    NVARCHAR(20)  NULL,
        residual_severity    NVARCHAR(20)  NULL,
        estimated_loss_sar   DECIMAL(18,2) NULL,

        is_deleted           BIT           NOT NULL DEFAULT 0,
        deleted_at           DATETIME2(3)  NULL,
        deleted_by           BIGINT        NULL,
        created_at           DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by           BIGINT        NULL,
        updated_at           DATETIME2(3)  NULL,
        updated_by           BIGINT        NULL,
        row_version          ROWVERSION,

        CONSTRAINT FK_bowtie_cons_bowtie FOREIGN KEY (bowtie_id)
            REFERENCES risk.bowtie_analysis(id),
        CONSTRAINT CK_bowtie_cons_type CHECK (consequence_type IS NULL
            OR consequence_type IN
            (N'FINANCIAL', N'REGULATORY', N'REPUTATIONAL', N'OPERATIONAL',
             N'LEGAL', N'HUMAN_SAFETY', N'ENVIRONMENTAL')),
        CONSTRAINT CK_bowtie_cons_inh CHECK (inherent_severity IS NULL
            OR inherent_severity IN (N'INSIGNIFICANT', N'MINOR', N'MODERATE', N'MAJOR', N'CATASTROPHIC')),
        CONSTRAINT CK_bowtie_cons_res CHECK (residual_severity IS NULL
            OR residual_severity IN (N'INSIGNIFICANT', N'MINOR', N'MODERATE', N'MAJOR', N'CATASTROPHIC'))
    );
END
GO

IF OBJECT_ID(N'risk.bowtie_consequence', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_bowtie_cons_bowtie' AND object_id = OBJECT_ID(N'risk.bowtie_consequence'))
    CREATE INDEX IX_bowtie_cons_bowtie ON risk.bowtie_consequence (bowtie_id, consequence_order) WHERE is_deleted = 0;
GO

-- ─── 4. Preventive controls (threat side) ─────────────────────────
IF OBJECT_ID(N'risk.bowtie_preventive_control', N'U') IS NULL
BEGIN
    CREATE TABLE risk.bowtie_preventive_control (
        id                     BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id        BIGINT NOT NULL,
        external_id            UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        bowtie_id              BIGINT NOT NULL,
        threat_id              BIGINT NOT NULL,

        control_order          INT NOT NULL,
        title_en               NVARCHAR(300) NOT NULL,
        title_ar               NVARCHAR(300) NULL,
        description_en         NVARCHAR(MAX) NULL,
        description_ar         NVARCHAR(MAX) NULL,
        control_type           NVARCHAR(40)  NULL,
        linked_control_id      BIGINT        NULL,
        effectiveness          NVARCHAR(20)  NOT NULL DEFAULT N'MEDIUM',
        last_tested_at         DATE          NULL,
        last_test_result       NVARCHAR(40)  NULL,
        responsible_role       NVARCHAR(120) NULL,
        responsible_owner      NVARCHAR(255) NULL,

        is_deleted             BIT           NOT NULL DEFAULT 0,
        deleted_at             DATETIME2(3)  NULL,
        deleted_by             BIGINT        NULL,
        created_at             DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by             BIGINT        NULL,
        updated_at             DATETIME2(3)  NULL,
        updated_by             BIGINT        NULL,
        row_version            ROWVERSION,

        CONSTRAINT FK_bowtie_prev_bowtie FOREIGN KEY (bowtie_id)
            REFERENCES risk.bowtie_analysis(id),
        CONSTRAINT FK_bowtie_prev_threat FOREIGN KEY (threat_id)
            REFERENCES risk.bowtie_threat(id),
        CONSTRAINT CK_bowtie_prev_type CHECK (control_type IS NULL
            OR control_type IN (N'PREVENTIVE', N'DETECTIVE', N'CORRECTIVE', N'DIRECTIVE')),
        CONSTRAINT CK_bowtie_prev_eff CHECK (effectiveness IN
            (N'VERY_LOW', N'LOW', N'MEDIUM', N'HIGH', N'VERY_HIGH')),
        CONSTRAINT CK_bowtie_prev_test CHECK (last_test_result IS NULL
            OR last_test_result IN (N'EFFECTIVE', N'PARTIALLY_EFFECTIVE', N'INEFFECTIVE', N'NOT_TESTED'))
    );
END
GO

IF OBJECT_ID(N'risk.bowtie_preventive_control', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_bowtie_prev_threat' AND object_id = OBJECT_ID(N'risk.bowtie_preventive_control'))
    CREATE INDEX IX_bowtie_prev_threat ON risk.bowtie_preventive_control (threat_id, control_order) WHERE is_deleted = 0;
GO

-- ─── 5. Mitigating controls (consequence side) ────────────────────
IF OBJECT_ID(N'risk.bowtie_mitigating_control', N'U') IS NULL
BEGIN
    CREATE TABLE risk.bowtie_mitigating_control (
        id                     BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id        BIGINT NOT NULL,
        external_id            UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        bowtie_id              BIGINT NOT NULL,
        consequence_id         BIGINT NOT NULL,

        control_order          INT NOT NULL,
        title_en               NVARCHAR(300) NOT NULL,
        title_ar               NVARCHAR(300) NULL,
        description_en         NVARCHAR(MAX) NULL,
        description_ar         NVARCHAR(MAX) NULL,
        control_type           NVARCHAR(40)  NULL,
        linked_control_id      BIGINT        NULL,
        effectiveness          NVARCHAR(20)  NOT NULL DEFAULT N'MEDIUM',
        last_tested_at         DATE          NULL,
        last_test_result       NVARCHAR(40)  NULL,
        responsible_role       NVARCHAR(120) NULL,
        responsible_owner      NVARCHAR(255) NULL,

        is_deleted             BIT           NOT NULL DEFAULT 0,
        deleted_at             DATETIME2(3)  NULL,
        deleted_by             BIGINT        NULL,
        created_at             DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by             BIGINT        NULL,
        updated_at             DATETIME2(3)  NULL,
        updated_by             BIGINT        NULL,
        row_version            ROWVERSION,

        CONSTRAINT FK_bowtie_mit_bowtie FOREIGN KEY (bowtie_id)
            REFERENCES risk.bowtie_analysis(id),
        CONSTRAINT FK_bowtie_mit_cons FOREIGN KEY (consequence_id)
            REFERENCES risk.bowtie_consequence(id),
        CONSTRAINT CK_bowtie_mit_type CHECK (control_type IS NULL
            OR control_type IN (N'PREVENTIVE', N'DETECTIVE', N'CORRECTIVE', N'DIRECTIVE')),
        CONSTRAINT CK_bowtie_mit_eff CHECK (effectiveness IN
            (N'VERY_LOW', N'LOW', N'MEDIUM', N'HIGH', N'VERY_HIGH')),
        CONSTRAINT CK_bowtie_mit_test CHECK (last_test_result IS NULL
            OR last_test_result IN (N'EFFECTIVE', N'PARTIALLY_EFFECTIVE', N'INEFFECTIVE', N'NOT_TESTED'))
    );
END
GO

IF OBJECT_ID(N'risk.bowtie_mitigating_control', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_bowtie_mit_cons' AND object_id = OBJECT_ID(N'risk.bowtie_mitigating_control'))
    CREATE INDEX IX_bowtie_mit_cons ON risk.bowtie_mitigating_control (consequence_id, control_order) WHERE is_deleted = 0;
GO

-- ─── 6. Seed worked example — PDPL Art.34 customer-data exfil ──────
-- One fully-worked, bilingual demo bow-tie for org=1 (TSS GRC tenant).
-- Top event: Material customer data exfiltration. Mirrors the scenario
-- a Saudi bank would walk through with the Risk Committee — SAMA
-- enforcement on the regulatory side, SDAIA fine on the privacy side,
-- realistic SAR amounts, full control chains both sides of the knot.

IF OBJECT_ID(N'risk.bowtie_analysis', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM risk.bowtie_analysis
                WHERE organisation_id = 1 AND bowtie_code = N'BOWTIE-2026-001')
BEGIN
    DECLARE @bowtie_id BIGINT;

    INSERT INTO risk.bowtie_analysis
        (organisation_id, bowtie_code, top_event_title_en, top_event_title_ar,
         top_event_description_en, top_event_description_ar,
         risk_category, assessment_date, assessed_by_name, methodology,
         overall_residual_rating, overall_control_effectiveness,
         status, approved_at, approved_by_name, next_review_at)
    VALUES
        (1, N'BOWTIE-2026-001',
         N'Material customer data exfiltration (PDPL Art.34 scenario)',
         N'تسريب مادي لبيانات العملاء (سيناريو المادة 34 من نظام حماية البيانات الشخصية)',
         N'A material exfiltration of customer personal data — defined as ≥10,000 customer records or any record containing sensitive personal data (CR number, IBAN, biometric, health). Triggers PDPL Article 34 (72-hour SDAIA notification), SAMA Cyber Security Framework breach reporting, and the Board crisis-comms playbook.',
         N'تسريب مادي لبيانات العملاء الشخصية — يُعرّف بأنه عدد سجلات يبلغ 10000 أو أكثر، أو أي سجل يحتوي على بيانات شخصية حساسة (رقم السجل التجاري، الآيبان، بيانات حيوية، صحية). يُفعّل المادة 34 من نظام حماية البيانات الشخصية (إخطار الهيئة السعودية للبيانات والذكاء الاصطناعي خلال 72 ساعة)، وتقارير الانتهاكات في إطار الأمن السيبراني للبنك المركزي السعودي، ودليل التواصل في الأزمات للمجلس.',
         N'CYBER', DATEADD(DAY, -14, CAST(SYSUTCDATETIME() AS DATE)),
         N'CISO (Hassan Al-Qahtani)', N'ISO_31010',
         N'MEDIUM', 72.50,
         N'APPROVED', DATEADD(DAY, -7, SYSUTCDATETIME()), N'Group CRO (Khalid Al-Subaie)',
         DATEADD(MONTH, 6, CAST(SYSUTCDATETIME() AS DATE)));

    SET @bowtie_id = SCOPE_IDENTITY();

    -- ─ Threats ────────────────────────────────────────────────────
    DECLARE @t_insider BIGINT, @t_ransom BIGINT, @t_vendor BIGINT, @t_webapp BIGINT;

    INSERT INTO risk.bowtie_threat
        (organisation_id, bowtie_id, threat_order, title_en, title_ar,
         description_en, description_ar,
         inherent_likelihood, residual_likelihood, source)
    VALUES
        (1, @bowtie_id, 1,
         N'Insider abuse — privileged user exfiltrates data',
         N'إساءة استخدام داخلية — مستخدم ذو امتيازات يسرّب البيانات',
         N'A privileged employee or contractor abuses their data access to copy customer records to external storage or personal email.',
         N'موظف أو مقاول يتمتع بامتيازات يسيء استخدام صلاحياته في الوصول إلى البيانات لنسخ سجلات العملاء إلى تخزين خارجي أو بريد إلكتروني شخصي.',
         N'LIKELY', N'UNLIKELY', N'Insider');
    SET @t_insider = SCOPE_IDENTITY();

    INSERT INTO risk.bowtie_threat
        (organisation_id, bowtie_id, threat_order, title_en, title_ar,
         description_en, description_ar,
         inherent_likelihood, residual_likelihood, source)
    VALUES
        (1, @bowtie_id, 2,
         N'Ransomware exfiltration (double extortion)',
         N'تسريب ببرامج الفدية (الابتزاز المزدوج)',
         N'External threat actor deploys ransomware that exfiltrates customer data before encryption to enable double-extortion leverage.',
         N'مهاجم خارجي ينشر برنامج فدية يسرّب بيانات العملاء قبل التشفير لتفعيل الابتزاز المزدوج.',
         N'POSSIBLE', N'UNLIKELY', N'External actor');
    SET @t_ransom = SCOPE_IDENTITY();

    INSERT INTO risk.bowtie_threat
        (organisation_id, bowtie_id, threat_order, title_en, title_ar,
         description_en, description_ar,
         inherent_likelihood, residual_likelihood, source)
    VALUES
        (1, @bowtie_id, 3,
         N'Third-party data processor breach',
         N'انتهاك معالج بيانات طرف ثالث',
         N'A contracted data processor (cloud KYC vendor, marketing platform, archive provider) is breached and exfiltrates customer data shared under DPA.',
         N'يتعرض معالج بيانات متعاقد (مزود KYC السحابي، منصة تسويق، مزود أرشفة) للانتهاك ويتم تسريب بيانات العملاء المشتركة بموجب اتفاقية معالجة البيانات.',
         N'POSSIBLE', N'POSSIBLE', N'Third party');
    SET @t_vendor = SCOPE_IDENTITY();

    INSERT INTO risk.bowtie_threat
        (organisation_id, bowtie_id, threat_order, title_en, title_ar,
         description_en, description_ar,
         inherent_likelihood, residual_likelihood, source)
    VALUES
        (1, @bowtie_id, 4,
         N'Internet-facing web app SQL injection',
         N'حقن SQL على تطبيق ويب يواجه الإنترنت',
         N'A SQL injection on the customer portal or mobile-app API returns a customer-record dump.',
         N'حقن SQL على بوابة العملاء أو واجهة برمجة تطبيق الجوال يُرجع مجموعة من سجلات العملاء.',
         N'POSSIBLE', N'UNLIKELY', N'External actor');
    SET @t_webapp = SCOPE_IDENTITY();

    -- ─ Preventive controls (one or more per threat) ──────────────
    -- DLP
    INSERT INTO risk.bowtie_preventive_control
        (organisation_id, bowtie_id, threat_id, control_order,
         title_en, title_ar, description_en, description_ar,
         control_type, effectiveness, last_tested_at, last_test_result,
         responsible_role, responsible_owner)
    VALUES (1, @bowtie_id, @t_insider, 1,
        N'Data Loss Prevention (DLP) — block egress of PII over email + USB',
        N'منع تسرب البيانات — حظر نقل البيانات الشخصية عبر البريد و USB',
        N'Symantec DLP rules: any message containing ≥50 CR numbers or IBANs is blocked from external send. USB write of PII fingerprinted files is blocked at endpoint.',
        N'قواعد Symantec DLP: تُحظر أي رسالة تحتوي على 50 رقم سجل تجاري أو آيبان فأكثر من الإرسال الخارجي. كتابة USB لملفات بصمات البيانات الشخصية محظورة عند نقطة النهاية.',
        N'PREVENTIVE', N'HIGH', DATEADD(MONTH, -2, CAST(SYSUTCDATETIME() AS DATE)),
        N'EFFECTIVE', N'CISO', N'Hassan Al-Qahtani');

    -- Privileged Access Management
    INSERT INTO risk.bowtie_preventive_control
        (organisation_id, bowtie_id, threat_id, control_order,
         title_en, title_ar, description_en, description_ar,
         control_type, effectiveness, last_tested_at, last_test_result,
         responsible_role, responsible_owner)
    VALUES (1, @bowtie_id, @t_insider, 2,
        N'Privileged Access Management (PAM) — IAM least-privilege + session recording',
        N'إدارة الوصول المميز — أقل امتياز + تسجيل الجلسات',
        N'CyberArk PAM. All privileged sessions to customer-data systems require approval, are time-boxed (4h max), and screen-recorded for forensics.',
        N'CyberArk PAM. جميع الجلسات المميزة لأنظمة بيانات العملاء تتطلب موافقة، ومحدودة بأربع ساعات كحد أقصى، ومسجّلة شاشيًا للتحقيقات.',
        N'PREVENTIVE', N'HIGH', DATEADD(MONTH, -1, CAST(SYSUTCDATETIME() AS DATE)),
        N'EFFECTIVE', N'IAM Lead', N'Faisal Al-Zahrani');

    -- EDR on ransomware
    INSERT INTO risk.bowtie_preventive_control
        (organisation_id, bowtie_id, threat_id, control_order,
         title_en, title_ar, description_en, description_ar,
         control_type, effectiveness, last_tested_at, last_test_result,
         responsible_role, responsible_owner)
    VALUES (1, @bowtie_id, @t_ransom, 1,
        N'Endpoint Detection & Response (EDR) — behaviour-based ransomware kill-switch',
        N'الكشف والاستجابة لنقاط النهاية — مفتاح إيقاف برامج الفدية بناءً على السلوك',
        N'CrowdStrike Falcon. Ransomware behavioural detection (mass-encrypt + C2 callback) triggers automatic host isolation within 60s.',
        N'CrowdStrike Falcon. الكشف السلوكي عن برامج الفدية (التشفير الجماعي + استدعاء C2) يُفعّل العزل التلقائي للمضيف خلال 60 ثانية.',
        N'DETECTIVE', N'VERY_HIGH', DATEADD(MONTH, -1, CAST(SYSUTCDATETIME() AS DATE)),
        N'EFFECTIVE', N'SOC Lead', N'Mohammed Al-Otaibi');

    -- Vendor due-diligence
    INSERT INTO risk.bowtie_preventive_control
        (organisation_id, bowtie_id, threat_id, control_order,
         title_en, title_ar, description_en, description_ar,
         control_type, effectiveness, last_tested_at, last_test_result,
         responsible_role, responsible_owner)
    VALUES (1, @bowtie_id, @t_vendor, 1,
        N'Vendor risk review — SOC 2 + PDPL DPA + annual reassessment',
        N'مراجعة مخاطر الموردين — SOC 2 + اتفاقية معالجة البيانات + إعادة التقييم السنوي',
        N'Every processor handling customer data must show a current SOC 2 Type II, sign the PDPL-aligned DPA, and undergo annual reassessment by Vendor Risk.',
        N'يجب على كل معالج يتعامل مع بيانات العملاء تقديم SOC 2 من النوع الثاني الحالي، والتوقيع على اتفاقية معالجة البيانات المتوافقة مع نظام حماية البيانات الشخصية، والخضوع لإعادة تقييم سنوية من قبل مخاطر الموردين.',
        N'PREVENTIVE', N'MEDIUM', DATEADD(MONTH, -3, CAST(SYSUTCDATETIME() AS DATE)),
        N'PARTIALLY_EFFECTIVE', N'TPRM Lead', N'Sara Al-Ghamdi');

    -- WAF on SQLi
    INSERT INTO risk.bowtie_preventive_control
        (organisation_id, bowtie_id, threat_id, control_order,
         title_en, title_ar, description_en, description_ar,
         control_type, effectiveness, last_tested_at, last_test_result,
         responsible_role, responsible_owner)
    VALUES (1, @bowtie_id, @t_webapp, 1,
        N'Web Application Firewall (WAF) + quarterly DAST',
        N'جدار حماية تطبيقات الويب + الفحص الديناميكي ربع السنوي',
        N'F5 Advanced WAF in blocking mode with the OWASP Top-10 ruleset. Quarterly DAST scans by external pen-testers; findings tracked in CAPA.',
        N'F5 Advanced WAF في وضع الحظر مع مجموعة قواعد OWASP Top-10. فحوصات DAST ربع سنوية بواسطة مختبري الاختراق الخارجيين؛ النتائج متتبعة في CAPA.',
        N'PREVENTIVE', N'HIGH', DATEADD(MONTH, -1, CAST(SYSUTCDATETIME() AS DATE)),
        N'EFFECTIVE', N'AppSec Lead', N'Reem Al-Faisal');

    -- ─ Consequences ──────────────────────────────────────────────
    DECLARE @c_sdaia BIGINT, @c_sama BIGINT, @c_churn BIGINT, @c_reput BIGINT, @c_legal BIGINT;

    INSERT INTO risk.bowtie_consequence
        (organisation_id, bowtie_id, consequence_order, title_en, title_ar,
         description_en, description_ar,
         consequence_type, inherent_severity, residual_severity, estimated_loss_sar)
    VALUES (1, @bowtie_id, 1,
        N'SDAIA fine under PDPL Article 34 / 35',
        N'غرامة الهيئة السعودية للبيانات والذكاء الاصطناعي بموجب المادة 34 / 35 من نظام حماية البيانات الشخصية',
        N'SDAIA enforcement action — administrative fine for breach + unauthorised processing of personal data. PDPL caps at SAR 5M per violation.',
        N'إجراء إنفاذ الهيئة السعودية للبيانات والذكاء الاصطناعي — غرامة إدارية للانتهاك ومعالجة البيانات الشخصية بدون إذن. النظام يحدد سقفًا 5 ملايين ريال لكل مخالفة.',
        N'REGULATORY', N'MAJOR', N'MODERATE', 5000000.00);
    SET @c_sdaia = SCOPE_IDENTITY();

    INSERT INTO risk.bowtie_consequence
        (organisation_id, bowtie_id, consequence_order, title_en, title_ar,
         description_en, description_ar,
         consequence_type, inherent_severity, residual_severity, estimated_loss_sar)
    VALUES (1, @bowtie_id, 2,
        N'SAMA enforcement under Cyber Security Framework',
        N'إجراء إنفاذ البنك المركزي السعودي بموجب إطار الأمن السيبراني',
        N'SAMA enforcement action — supervisory letter, possible monetary penalty, board-level remediation order, and tightened reporting cadence for 12 months.',
        N'إجراء إنفاذ البنك المركزي السعودي — خطاب إشرافي، وإمكانية فرض غرامة مالية، وأمر معالجة على مستوى المجلس، وإحكام وتيرة التقارير لمدة 12 شهرًا.',
        N'REGULATORY', N'MAJOR', N'MODERATE', 8000000.00);
    SET @c_sama = SCOPE_IDENTITY();

    INSERT INTO risk.bowtie_consequence
        (organisation_id, bowtie_id, consequence_order, title_en, title_ar,
         description_en, description_ar,
         consequence_type, inherent_severity, residual_severity, estimated_loss_sar)
    VALUES (1, @bowtie_id, 3,
        N'Customer churn — retail + SME attrition over 6 months',
        N'فقدان العملاء — تراجع الأفراد والمؤسسات الصغيرة على مدى 6 أشهر',
        N'Estimated 4–7% attrition of retail + SME book in the six months following public disclosure. Lifetime-value model used (not just first-year revenue loss).',
        N'تراجع متوقع بنسبة 4-7% من محفظة الأفراد والمؤسسات الصغيرة في الأشهر الستة التي تلي الإفصاح العلني. يُستخدم نموذج القيمة الدائمة (وليس مجرد فقدان إيرادات السنة الأولى).',
        N'FINANCIAL', N'MAJOR', N'MODERATE', 25000000.00);
    SET @c_churn = SCOPE_IDENTITY();

    INSERT INTO risk.bowtie_consequence
        (organisation_id, bowtie_id, consequence_order, title_en, title_ar,
         description_en, description_ar,
         consequence_type, inherent_severity, residual_severity, estimated_loss_sar)
    VALUES (1, @bowtie_id, 4,
        N'Reputational damage — brand equity erosion',
        N'الإضرار بالسمعة — تآكل أصول العلامة التجارية',
        N'Negative coverage in Saudi business press + social-media amplification. Brand-equity tracker drop of 8–15 points over 90 days. Erodes premium pricing power.',
        N'تغطية سلبية في الصحف الاقتصادية السعودية + تضخيم وسائل التواصل الاجتماعي. تراجع متتبع أصول العلامة التجارية بمقدار 8-15 نقطة خلال 90 يومًا. يقلل القدرة على التسعير المتميز.',
        N'REPUTATIONAL', N'CATASTROPHIC', N'MAJOR', 12000000.00);
    SET @c_reput = SCOPE_IDENTITY();

    INSERT INTO risk.bowtie_consequence
        (organisation_id, bowtie_id, consequence_order, title_en, title_ar,
         description_en, description_ar,
         consequence_type, inherent_severity, residual_severity, estimated_loss_sar)
    VALUES (1, @bowtie_id, 5,
        N'Legal costs — civil claims + regulatory defence',
        N'التكاليف القانونية — المطالبات المدنية + الدفاع التنظيمي',
        N'Civil claims from affected customers (PDPL Art.39 private right of action) + external counsel for SDAIA and SAMA proceedings.',
        N'مطالبات مدنية من العملاء المتأثرين (المادة 39 من نظام حماية البيانات الشخصية - الحق الخاص في رفع الدعوى) + استشاريون خارجيون لإجراءات الهيئة السعودية للبيانات والذكاء الاصطناعي والبنك المركزي.',
        N'LEGAL', N'MODERATE', N'MINOR', 3500000.00);
    SET @c_legal = SCOPE_IDENTITY();

    -- ─ Mitigating controls (right side) ──────────────────────────
    INSERT INTO risk.bowtie_mitigating_control
        (organisation_id, bowtie_id, consequence_id, control_order,
         title_en, title_ar, description_en, description_ar,
         control_type, effectiveness, last_tested_at, last_test_result,
         responsible_role, responsible_owner)
    VALUES (1, @bowtie_id, @c_sdaia, 1,
        N'72-hour SDAIA breach-notification playbook',
        N'دليل إخطار الهيئة السعودية للبيانات والذكاء الاصطناعي خلال 72 ساعة',
        N'Pre-approved PDPL Art.34 notification template, DPO escalation path, and tabletop-tested 72-hour clock. Reduces fine exposure by ~40% vs late filing per SDAIA enforcement guidance.',
        N'قالب إخطار المادة 34 معتمد مسبقًا، ومسار تصعيد لمسؤول حماية البيانات، وساعة 72 مختبرة على الطاولة. يقلل التعرض للغرامة بنحو 40٪ مقارنة بالإيداع المتأخر وفقًا لإرشادات إنفاذ الهيئة السعودية للبيانات.',
        N'CORRECTIVE', N'HIGH', DATEADD(MONTH, -1, CAST(SYSUTCDATETIME() AS DATE)),
        N'EFFECTIVE', N'DPO', N'Norah Al-Mutairi');

    INSERT INTO risk.bowtie_mitigating_control
        (organisation_id, bowtie_id, consequence_id, control_order,
         title_en, title_ar, description_en, description_ar,
         control_type, effectiveness, last_tested_at, last_test_result,
         responsible_role, responsible_owner)
    VALUES (1, @bowtie_id, @c_sama, 1,
        N'SAMA CSF incident notification + remediation roadmap',
        N'إخطار البنك المركزي بموجب إطار الأمن السيبراني + خارطة طريق المعالجة',
        N'Standing SAMA supervisor contact, pre-drafted incident classification + remediation roadmap. Demonstrates control maturity which historically reduces enforcement severity.',
        N'جهة اتصال إشرافية ثابتة في البنك المركزي، وتصنيف حادث مُسبق الصياغة + خارطة طريق المعالجة. يُظهر نضج الضوابط الذي يقلل تاريخيًا من شدة الإنفاذ.',
        N'CORRECTIVE', N'MEDIUM', DATEADD(MONTH, -4, CAST(SYSUTCDATETIME() AS DATE)),
        N'PARTIALLY_EFFECTIVE', N'CISO', N'Hassan Al-Qahtani');

    INSERT INTO risk.bowtie_mitigating_control
        (organisation_id, bowtie_id, consequence_id, control_order,
         title_en, title_ar, description_en, description_ar,
         control_type, effectiveness, last_tested_at, last_test_result,
         responsible_role, responsible_owner)
    VALUES (1, @bowtie_id, @c_churn, 1,
        N'12-month complimentary identity-monitoring service for affected customers',
        N'خدمة مراقبة الهوية المجانية لمدة 12 شهرًا للعملاء المتأثرين',
        N'Standing contract with Experian Saudi for credit-monitoring + dark-web scanning. Retention research shows ~30% reduction in churn intent when offered proactively.',
        N'عقد دائم مع Experian السعودية لمراقبة الائتمان + مسح الويب المظلم. تظهر أبحاث الاحتفاظ تخفيضًا بنحو 30٪ في نية الفقد عند تقديمه بشكل استباقي.',
        N'CORRECTIVE', N'MEDIUM', NULL, N'NOT_TESTED',
        N'Customer Experience Lead', N'Lina Al-Harbi');

    INSERT INTO risk.bowtie_mitigating_control
        (organisation_id, bowtie_id, consequence_id, control_order,
         title_en, title_ar, description_en, description_ar,
         control_type, effectiveness, last_tested_at, last_test_result,
         responsible_role, responsible_owner)
    VALUES (1, @bowtie_id, @c_reput, 1,
        N'Pre-approved crisis-communications template + Group Comms playbook',
        N'قالب اتصالات الأزمات المعتمد مسبقًا + دليل اتصالات المجموعة',
        N'Pre-cleared statements in EN/AR, prepared talking points for the CEO + Chair, named spokesperson roster. Activated by the Crisis Management Team within 2 hours of confirmation.',
        N'بيانات معتمدة مسبقًا بالإنجليزية والعربية، ونقاط حوار جاهزة للرئيس التنفيذي ورئيس مجلس الإدارة، وقائمة متحدثين معتمدين. يفعّلها فريق إدارة الأزمات خلال ساعتين من التأكيد.',
        N'CORRECTIVE', N'HIGH', DATEADD(MONTH, -2, CAST(SYSUTCDATETIME() AS DATE)),
        N'EFFECTIVE', N'Group Communications', N'Tarek Al-Dossari');

    INSERT INTO risk.bowtie_mitigating_control
        (organisation_id, bowtie_id, consequence_id, control_order,
         title_en, title_ar, description_en, description_ar,
         control_type, effectiveness, last_tested_at, last_test_result,
         responsible_role, responsible_owner)
    VALUES (1, @bowtie_id, @c_legal, 1,
        N'Cyber insurance — SAR 50M tower with breach-response endorsement',
        N'تأمين سيبراني — تغطية بقيمة 50 مليون ريال مع ملحق استجابة الانتهاكات',
        N'Standalone cyber policy: SAR 50M aggregate, SAR 2.5M retention. Includes regulatory-defence sub-limit and breach-coach hotline within 1 hour SLA.',
        N'وثيقة سيبرانية قائمة بذاتها: 50 مليون ريال إجمالي، 2.5 مليون ريال احتفاظ. تتضمن حدًا فرعيًا للدفاع التنظيمي وخط ساخن لمدرب الانتهاكات ضمن SLA ساعة واحدة.',
        N'CORRECTIVE', N'HIGH', NULL, N'NOT_TESTED',
        N'Group Treasury', N'Yousef Al-Najjar');
END
GO

PRINT N'V164 risk.bowtie_analysis — header + 4 child tables + seed demo applied.';
GO
