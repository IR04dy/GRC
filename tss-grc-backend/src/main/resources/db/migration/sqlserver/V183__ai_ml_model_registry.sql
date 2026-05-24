-- V183 — AI/ML Model Registry (Sprint 11 — AI Governance).
--
-- Tri-track compliance: SDAIA AI Ethics Principles (October 2023,
-- Principle 4 — Transparency & Explainability), SAMA AI Governance
-- Principles (December 2024 update — every model serving a regulated
-- business decision must be inventoried, validated and monitored for
-- drift), and the EU AI Act Article 16 (high-risk AI systems require
-- risk-management documentation + post-market monitoring) for the
-- bank's European subsidiaries.
--
-- Three tables under a new ai_governance schema:
--
--   ai_governance.ml_model                     ← inventory header
--   ai_governance.ml_model_audit               ← fairness / bias /
--                                                  explainability /
--                                                  robustness / privacy /
--                                                  security / performance
--                                                  audit results
--   ai_governance.ml_model_drift_observation   ← post-market monitoring
--                                                  observations (data
--                                                  drift, concept drift,
--                                                  performance degradation,
--                                                  fairness regression)
--
-- Lifecycle: PROPOSED → DEVELOPMENT → VALIDATION → APPROVED_FOR_PROD →
--            IN_PRODUCTION → MONITORING → REVISION → DECOMMISSIONED.
-- Gates enforced server-side in MlModelService (HIGH+ risk tier cannot
-- enter APPROVED_FOR_PROD without validator+fairness+DPIA+explainability).
--
-- All three tables: org-scoped, soft-delete filtered, ROWVERSION-backed.
-- QUOTED_IDENTIFIER ON for the whole script — SQL Server requires it
-- for filtered indexes and computed columns.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── 0. Schema ─────────────────────────────────────────────────────
IF SCHEMA_ID(N'ai_governance') IS NULL EXEC(N'CREATE SCHEMA ai_governance');
GO

-- ─── 1. Inventory header ───────────────────────────────────────────
IF OBJECT_ID(N'ai_governance.ml_model', N'U') IS NULL
BEGIN
    CREATE TABLE ai_governance.ml_model (
        id                                 BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id                    BIGINT NOT NULL,
        external_id                        UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),

        model_code                         NVARCHAR(64)  NOT NULL,
        title_en                           NVARCHAR(255) NOT NULL,
        title_ar                           NVARCHAR(255) NULL,
        description_en                     NVARCHAR(MAX) NULL,
        description_ar                     NVARCHAR(MAX) NULL,

        -- Categorisation.
        model_type                         NVARCHAR(40)  NOT NULL,
        business_domain                    NVARCHAR(40)  NOT NULL,
        risk_tier                          NVARCHAR(20)  NOT NULL,
        lifecycle_stage                    NVARCHAR(40)  NOT NULL DEFAULT N'PROPOSED',

        -- Versioning.
        version_label                      NVARCHAR(64)  NULL,
        version_history_json               NVARCHAR(MAX) NULL,

        -- Ownership.
        model_owner_user_id                BIGINT        NOT NULL,
        business_sponsor_user_id           BIGINT        NOT NULL,
        validator_user_id                  BIGINT        NULL,
        validation_date                    DATE          NULL,

        production_date                    DATE          NULL,
        decommission_date                  DATE          NULL,
        decommission_reason_en             NVARCHAR(1000) NULL,
        decommission_reason_ar             NVARCHAR(1000) NULL,

        -- Provenance (PDPL Art.4/5/6 + EU AI Act Art.10 — training data).
        training_data_provenance_en        NVARCHAR(MAX) NULL,
        training_data_provenance_ar        NVARCHAR(MAX) NULL,
        training_data_size_records         BIGINT        NULL,
        training_data_period_start         DATE          NULL,
        training_data_period_end           DATE          NULL,
        feature_count                      INT           NULL,
        external_dataset_used              BIT           NOT NULL DEFAULT 0,
        external_dataset_provenance        NVARCHAR(MAX) NULL,

        -- Tech.
        framework                          NVARCHAR(40)  NULL,
        hosting                            NVARCHAR(40)  NULL,
        inference_latency_ms               INT           NULL,
        training_compute_hours             DECIMAL(10,2) NULL,

        -- Compliance flags (the tri-track regulator surface).
        has_pdpl_dpia                      BIT           NOT NULL DEFAULT 0,
        pdpl_dpia_id                       BIGINT        NULL,
        has_fairness_audit                 BIT           NOT NULL DEFAULT 0,
        last_fairness_audit_date           DATE          NULL,
        has_explainability_doc             BIT           NOT NULL DEFAULT 0,
        is_high_risk_eu_ai_act             BIT           NOT NULL DEFAULT 0,
        sdaia_ai_ethics_compliance         NVARCHAR(20)  NOT NULL DEFAULT N'NOT_ASSESSED',
        sama_ai_principles_compliance      NVARCHAR(20)  NOT NULL DEFAULT N'NOT_ASSESSED',

        -- Audit + soft-delete.
        is_deleted                         BIT           NOT NULL DEFAULT 0,
        deleted_at                         DATETIME2(3)  NULL,
        deleted_by                         BIGINT        NULL,
        created_at                         DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                         BIGINT        NULL,
        updated_at                         DATETIME2(3)  NULL,
        updated_by                         BIGINT        NULL,
        row_version                        ROWVERSION,

        CONSTRAINT CK_ml_model_type CHECK (model_type IN
            (N'CLASSIFICATION', N'REGRESSION', N'CLUSTERING', N'NLP',
             N'COMPUTER_VISION', N'RECOMMENDATION', N'FORECASTING',
             N'LLM', N'GENERATIVE', N'RULE_BASED', N'HYBRID')),
        CONSTRAINT CK_ml_model_domain CHECK (business_domain IN
            (N'CREDIT_DECISION', N'FRAUD_DETECTION', N'AML_MONITORING',
             N'CUSTOMER_CHURN', N'DOCUMENT_CLASSIFICATION', N'KYC_OCR',
             N'CHATBOT', N'RECOMMENDER', N'RISK_SCORING',
             N'MARKETING_OPTIMIZATION', N'OPERATIONS_AUTOMATION')),
        CONSTRAINT CK_ml_model_tier CHECK (risk_tier IN
            (N'CRITICAL', N'HIGH', N'MEDIUM', N'LOW')),
        CONSTRAINT CK_ml_model_stage CHECK (lifecycle_stage IN
            (N'PROPOSED', N'DEVELOPMENT', N'VALIDATION', N'APPROVED_FOR_PROD',
             N'IN_PRODUCTION', N'MONITORING', N'REVISION', N'DECOMMISSIONED')),
        CONSTRAINT CK_ml_model_framework CHECK (framework IS NULL OR framework IN
            (N'TENSORFLOW', N'PYTORCH', N'SCIKIT_LEARN', N'XGBOOST',
             N'LIGHTGBM', N'HUGGINGFACE', N'AZURE_ML', N'AWS_SAGEMAKER',
             N'OPENAI_API', N'CUSTOM', N'HYBRID')),
        CONSTRAINT CK_ml_model_hosting CHECK (hosting IS NULL OR hosting IN
            (N'ON_PREM', N'AZURE_KSA', N'AWS_MIDDLE_EAST', N'GCP', N'EDGE', N'MIXED')),
        CONSTRAINT CK_ml_model_sdaia CHECK (sdaia_ai_ethics_compliance IN
            (N'COMPLIANT', N'PARTIAL', N'NON_COMPLIANT', N'NOT_ASSESSED')),
        CONSTRAINT CK_ml_model_sama CHECK (sama_ai_principles_compliance IN
            (N'COMPLIANT', N'PARTIAL', N'NON_COMPLIANT', N'NOT_ASSESSED'))
    );
END
GO

-- model_code unique per tenant within live rows.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_ml_model_code' AND object_id = OBJECT_ID(N'ai_governance.ml_model'))
    CREATE UNIQUE INDEX UQ_ml_model_code ON ai_governance.ml_model (organisation_id, model_code) WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ml_model_stage_tier' AND object_id = OBJECT_ID(N'ai_governance.ml_model'))
    CREATE INDEX IX_ml_model_stage_tier ON ai_governance.ml_model (organisation_id, lifecycle_stage, risk_tier) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ml_model_domain_tier' AND object_id = OBJECT_ID(N'ai_governance.ml_model'))
    CREATE INDEX IX_ml_model_domain_tier ON ai_governance.ml_model (organisation_id, business_domain, risk_tier) WHERE is_deleted = 0;
GO

-- ─── 2. Audit results ──────────────────────────────────────────────
IF OBJECT_ID(N'ai_governance.ml_model_audit', N'U') IS NULL
BEGIN
    CREATE TABLE ai_governance.ml_model_audit (
        id                            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id               BIGINT NOT NULL,
        external_id                   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        model_id                      BIGINT NOT NULL,

        audit_type                    NVARCHAR(40)  NOT NULL,
        audit_date                    DATE          NOT NULL,
        auditor_user_id               BIGINT        NULL,
        auditor_external_party        NVARCHAR(255) NULL,
        methodology_en                NVARCHAR(MAX) NULL,
        methodology_ar                NVARCHAR(MAX) NULL,
        findings_en                   NVARCHAR(MAX) NULL,
        findings_ar                   NVARCHAR(MAX) NULL,
        status                        NVARCHAR(30)  NOT NULL,
        metrics_json                  NVARCHAR(MAX) NULL,
        capa_task_id                  BIGINT        NULL,

        is_deleted                    BIT           NOT NULL DEFAULT 0,
        deleted_at                    DATETIME2(3)  NULL,
        deleted_by                    BIGINT        NULL,
        created_at                    DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                    BIGINT        NULL,
        updated_at                    DATETIME2(3)  NULL,
        updated_by                    BIGINT        NULL,
        row_version                   ROWVERSION,

        CONSTRAINT FK_ml_audit_model FOREIGN KEY (model_id)
            REFERENCES ai_governance.ml_model(id),
        CONSTRAINT CK_ml_audit_type CHECK (audit_type IN
            (N'FAIRNESS', N'BIAS', N'EXPLAINABILITY', N'ROBUSTNESS',
             N'PRIVACY', N'SECURITY', N'PERFORMANCE')),
        CONSTRAINT CK_ml_audit_status CHECK (status IN
            (N'PASSED', N'PASSED_WITH_CONDITIONS', N'FAILED', N'REMEDIATED'))
    );
END
GO

-- Per-(model, audit_type, audit_date) uniqueness — one row per audit run.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UQ_ml_audit_model_type_date' AND object_id = OBJECT_ID(N'ai_governance.ml_model_audit'))
    CREATE UNIQUE INDEX UQ_ml_audit_model_type_date ON ai_governance.ml_model_audit (model_id, audit_type, audit_date) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ml_audit_org_model' AND object_id = OBJECT_ID(N'ai_governance.ml_model_audit'))
    CREATE INDEX IX_ml_audit_org_model ON ai_governance.ml_model_audit (organisation_id, model_id, audit_date DESC) WHERE is_deleted = 0;
GO

-- ─── 3. Drift observations (post-market monitoring) ───────────────
IF OBJECT_ID(N'ai_governance.ml_model_drift_observation', N'U') IS NULL
BEGIN
    CREATE TABLE ai_governance.ml_model_drift_observation (
        id                       BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id          BIGINT NOT NULL,
        external_id              UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
        model_id                 BIGINT NOT NULL,

        observed_at              DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        observation_type         NVARCHAR(40)  NOT NULL,
        severity                 NVARCHAR(20)  NOT NULL,
        metric_name              NVARCHAR(255) NOT NULL,
        metric_value             DECIMAL(18,6) NOT NULL,
        baseline_value           DECIMAL(18,6) NULL,
        drift_threshold          DECIMAL(18,6) NULL,
        notes_en                 NVARCHAR(2000) NULL,
        notes_ar                 NVARCHAR(2000) NULL,

        is_acknowledged          BIT           NOT NULL DEFAULT 0,
        acknowledged_at          DATETIME2(3)  NULL,
        acknowledged_by_user_id  BIGINT        NULL,
        triggered_issue_id       BIGINT        NULL,

        is_deleted               BIT           NOT NULL DEFAULT 0,
        deleted_at               DATETIME2(3)  NULL,
        deleted_by               BIGINT        NULL,
        created_at               DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by               BIGINT        NULL,
        updated_at               DATETIME2(3)  NULL,
        updated_by               BIGINT        NULL,
        row_version              ROWVERSION,

        CONSTRAINT FK_ml_drift_model FOREIGN KEY (model_id)
            REFERENCES ai_governance.ml_model(id),
        CONSTRAINT CK_ml_drift_type CHECK (observation_type IN
            (N'DATA_DRIFT', N'CONCEPT_DRIFT', N'PERFORMANCE_DEGRADATION',
             N'FAIRNESS_REGRESSION')),
        CONSTRAINT CK_ml_drift_sev CHECK (severity IN
            (N'INFO', N'MINOR', N'MODERATE', N'MAJOR', N'CRITICAL'))
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ml_drift_org_model_observed' AND object_id = OBJECT_ID(N'ai_governance.ml_model_drift_observation'))
    CREATE INDEX IX_ml_drift_org_model_observed ON ai_governance.ml_model_drift_observation (organisation_id, model_id, observed_at DESC) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ml_drift_unacknowledged' AND object_id = OBJECT_ID(N'ai_governance.ml_model_drift_observation'))
    CREATE INDEX IX_ml_drift_unacknowledged ON ai_governance.ml_model_drift_observation (organisation_id, is_acknowledged, severity) WHERE is_deleted = 0 AND is_acknowledged = 0;
GO

-- ─── 4. Seed data — 5 example models for org=1 (TSS Bank) ─────────
-- All bilingual EN/AR, Saudi-native AR (sجل نماذج الذكاء الاصطناعي,
-- not literal "نماذج التعلم الآلي").

-- MODEL-2026-001 — Credit Decision Engine v2.3.
IF NOT EXISTS (SELECT 1 FROM ai_governance.ml_model WHERE organisation_id = 1 AND model_code = N'MODEL-2026-001' AND is_deleted = 0)
INSERT INTO ai_governance.ml_model
    (organisation_id, model_code, title_en, title_ar, description_en, description_ar,
     model_type, business_domain, risk_tier, lifecycle_stage,
     version_label, model_owner_user_id, business_sponsor_user_id,
     validator_user_id, validation_date, production_date,
     training_data_provenance_en, training_data_provenance_ar,
     training_data_size_records, training_data_period_start, training_data_period_end, feature_count,
     external_dataset_used, external_dataset_provenance,
     framework, hosting, inference_latency_ms, training_compute_hours,
     has_pdpl_dpia, has_fairness_audit, last_fairness_audit_date,
     has_explainability_doc, is_high_risk_eu_ai_act,
     sdaia_ai_ethics_compliance, sama_ai_principles_compliance)
VALUES
    (1, N'MODEL-2026-001',
     N'Credit Decision Engine v2.3',
     N'محرّك قرارات التمويل الشخصي - الإصدار 2.3',
     N'XGBoost probability-of-default model for personal financing. Always human-in-the-loop for declines >50k SAR. Outputs feed the SIMAH/Bayan-enriched credit decision pipeline.',
     N'نموذج XGBoost لتقدير احتمال التعثّر في التمويل الشخصي. وجود الإنسان في حلقة القرار للرفض فوق 50,000 ريال. مخرجاته تُغذّي خطّ قرار التمويل المُعزَّز ببيانات سمة/بيان.',
     N'CLASSIFICATION', N'CREDIT_DECISION', N'CRITICAL', N'IN_PRODUCTION',
     N'2.3.1', 1, 1,
     1, '2026-01-20', '2026-02-01',
     N'Internal credit history (T24 core), SIMAH credit bureau report, Bayan SME enrichment. PDPL Art.6 lawful basis: legitimate interest with documented balancing test. Retention 7 years per SAMA record-keeping rules.',
     N'سجل الائتمان الداخلي (T24)، تقرير سمة الائتماني، إثراء بيان للشركات الصغيرة. الأساس النظامي لـ PDPL المادة 6: المصلحة المشروعة مع اختبار موازنة موثّق. مدة الاحتفاظ 7 سنوات وفق متطلبات حفظ السجلات لدى البنك المركزي.',
     842000, '2020-01-01', '2025-12-31', 187,
     1, N'SIMAH credit bureau (Saudi Credit Bureau) — under data sharing agreement SCB-2024-117',
     N'XGBOOST', N'AZURE_KSA', 45, 312.50,
     1, 1, '2026-02-15', 1, 1,
     N'COMPLIANT', N'COMPLIANT');
GO

-- MODEL-2026-002 — AML Transaction Anomaly Detection v1.8.
IF NOT EXISTS (SELECT 1 FROM ai_governance.ml_model WHERE organisation_id = 1 AND model_code = N'MODEL-2026-002' AND is_deleted = 0)
INSERT INTO ai_governance.ml_model
    (organisation_id, model_code, title_en, title_ar, description_en, description_ar,
     model_type, business_domain, risk_tier, lifecycle_stage,
     version_label, model_owner_user_id, business_sponsor_user_id,
     validator_user_id, validation_date, production_date,
     training_data_provenance_en, training_data_provenance_ar,
     training_data_size_records, training_data_period_start, training_data_period_end, feature_count,
     external_dataset_used,
     framework, hosting, inference_latency_ms, training_compute_hours,
     has_pdpl_dpia, has_fairness_audit, last_fairness_audit_date,
     has_explainability_doc, is_high_risk_eu_ai_act,
     sdaia_ai_ethics_compliance, sama_ai_principles_compliance)
VALUES
    (1, N'MODEL-2026-002',
     N'AML Transaction Anomaly Detection v1.8',
     N'كشف الحالات الشاذّة في معاملات مكافحة غسل الأموال - الإصدار 1.8',
     N'LightGBM graph-anomaly detection over transaction networks. Hits feed STR drafts in goAML (SAFIU integration). Tuned for Saudi typology patterns (hawala, layered cash deposits, structured FX outflows).',
     N'كشف شذوذ رسومي بنموذج LightGBM فوق شبكات المعاملات. الحالات الإيجابية تُغذّي مسوّدات بلاغ المعاملات المشبوهة في نظام goAML (تكامل مع وحدة التحرّيات المالية). مُعايَر لأنماط الاحتيال السعودية (الحوالة، الإيداعات النقدية المتتابعة، التحويلات الخارجية المهيكلة).',
     N'CLASSIFICATION', N'AML_MONITORING', N'CRITICAL', N'IN_PRODUCTION',
     N'1.8.0', 1, 1,
     1, '2026-01-12', '2026-01-20',
     N'Transaction ledger from T24 core (debit/credit, channel, geolocation, counterparty), FATF typology library, internal STR feedback loop (analyst decisions feeding labelled training).',
     N'دفتر المعاملات من نواة T24 (المدين/الدائن، القناة، الموقع الجغرافي، الطرف المقابل)، مكتبة أنماط FATF، حلقة التغذية الراجعة من بلاغات المعاملات المشبوهة الداخلية (قرارات المحلّلين كتسميات تدريب).',
     12400000, '2022-01-01', '2025-12-31', 94,
     1,
     N'LIGHTGBM', N'MIXED', 28, 184.00,
     1, 1, '2026-01-30', 1, 1,
     N'COMPLIANT', N'COMPLIANT');
GO

-- MODEL-2026-003 — Customer Service Chatbot (Arabic + English) v1.1.
IF NOT EXISTS (SELECT 1 FROM ai_governance.ml_model WHERE organisation_id = 1 AND model_code = N'MODEL-2026-003' AND is_deleted = 0)
INSERT INTO ai_governance.ml_model
    (organisation_id, model_code, title_en, title_ar, description_en, description_ar,
     model_type, business_domain, risk_tier, lifecycle_stage,
     version_label, model_owner_user_id, business_sponsor_user_id,
     validator_user_id, validation_date, production_date,
     training_data_provenance_en, training_data_provenance_ar,
     external_dataset_used, external_dataset_provenance,
     framework, hosting, inference_latency_ms,
     has_pdpl_dpia, has_fairness_audit, last_fairness_audit_date,
     has_explainability_doc, is_high_risk_eu_ai_act,
     sdaia_ai_ethics_compliance, sama_ai_principles_compliance)
VALUES
    (1, N'MODEL-2026-003',
     N'Customer Service Chatbot (Arabic + English) v1.1',
     N'روبوت خدمة العملاء ثنائي اللغة - الإصدار 1.1',
     N'OpenAI API foundation + custom RAG layer indexing FAQ + product T&Cs. Bilingual (MSA + Saudi colloquial recognised). Hand-off to human agent for any account-action intent. Tawakkalna single sign-on integration flagged as out-of-scope due to data residency concerns (token exchange handled in-country).',
     N'نموذج أساسي عبر واجهة OpenAI مع طبقة استرجاع معزّز فوق فهرس الأسئلة الشائعة وشروط المنتجات. ثنائي اللغة (الفصحى والعامية السعودية). تحويل إلى موظّف بشري لأي نيّة تخصّ إجراءً على الحساب. التكامل مع توكلنا للدخول الموحّد مُستبعَد من النطاق بسبب تحفّظات إقامة البيانات (تبادل الرموز يجري داخل المملكة).',
     N'LLM', N'CHATBOT', N'MEDIUM', N'IN_PRODUCTION',
     N'1.1.0', 1, 1,
     1, '2026-02-10', '2026-02-20',
     N'No customer PII used in fine-tuning. RAG index built from public FAQ + internal product T&Cs only. Conversation logs retained 90 days for QA, then aggregated.',
     N'لا تُستخدم بيانات شخصية للعملاء في الضبط الدقيق. يُبنى فهرس الاسترجاع من الأسئلة الشائعة العامة وشروط المنتج الداخلية فقط. تُحفظ سجلات المحادثة 90 يوماً لمراقبة الجودة ثم تُجمَّع.',
     1, N'OpenAI gpt-4o-mini via Azure OpenAI Service (Saudi region not yet GA — data residency flagged as PARTIAL compliance)',
     N'OPENAI_API', N'AZURE_KSA', 1200,
     1, 1, '2026-02-15', 1, 0,
     N'PARTIAL', N'PARTIAL');
GO

-- MODEL-2026-004 — KYC Document OCR v3.0.
IF NOT EXISTS (SELECT 1 FROM ai_governance.ml_model WHERE organisation_id = 1 AND model_code = N'MODEL-2026-004' AND is_deleted = 0)
INSERT INTO ai_governance.ml_model
    (organisation_id, model_code, title_en, title_ar, description_en, description_ar,
     model_type, business_domain, risk_tier, lifecycle_stage,
     version_label, model_owner_user_id, business_sponsor_user_id,
     validator_user_id, validation_date, production_date,
     training_data_provenance_en, training_data_provenance_ar,
     training_data_size_records, feature_count,
     external_dataset_used,
     framework, hosting, inference_latency_ms, training_compute_hours,
     has_pdpl_dpia, has_fairness_audit, last_fairness_audit_date,
     has_explainability_doc, is_high_risk_eu_ai_act,
     sdaia_ai_ethics_compliance, sama_ai_principles_compliance)
VALUES
    (1, N'MODEL-2026-004',
     N'KYC Document OCR v3.0',
     N'الاستخراج البصري لوثائق اعرف عميلك - الإصدار 3.0',
     N'Custom CNN on TensorFlow extracting name, ID number, dates, address from National ID, Iqama, and commercial registration scans. Output verified by KYC analyst before being promoted to the customer master. EU AI Act high-risk classification due to biometric-adjacent identity processing (Annex III §1).',
     N'شبكة عصبية تلافيفية مُصمَّمة على TensorFlow لاستخراج الاسم ورقم الهوية والتواريخ والعنوان من صور الهوية الوطنية والإقامة والسجل التجاري. يُتحقَّق من المخرجات بواسطة محلّل اعرف عميلك قبل الترقية إلى السجلّ الرئيسي للعميل. مصنَّف كنظام عالي الخطورة وفق نظام الذكاء الاصطناعي الأوروبي بسبب معالجة هويّة قريبة من البيومتري (الملحق الثالث §1).',
     N'COMPUTER_VISION', N'KYC_OCR', N'HIGH', N'IN_PRODUCTION',
     N'3.0.2', 1, 1,
     1, '2025-11-15', '2025-12-01',
     N'Synthetic-augmented internal scan corpus (28,400 labelled documents). No customer PII left raw in training set — all labels manually masked.',
     N'مجموعة وثائق داخلية مع توسيع اصطناعي (28,400 وثيقة مُسمّاة). لا تُترك أي بيانات شخصية بصيغتها الأصلية في مجموعة التدريب — جميع التسميات مُعتَّمة يدوياً.',
     28400, 0,
     0,
     N'TENSORFLOW', N'ON_PREM', 380, 96.00,
     1, 1, '2026-03-02', 1, 1,
     N'COMPLIANT', N'COMPLIANT');
GO

-- MODEL-2026-005 — Branch Footfall Forecasting v0.9.
IF NOT EXISTS (SELECT 1 FROM ai_governance.ml_model WHERE organisation_id = 1 AND model_code = N'MODEL-2026-005' AND is_deleted = 0)
INSERT INTO ai_governance.ml_model
    (organisation_id, model_code, title_en, title_ar, description_en, description_ar,
     model_type, business_domain, risk_tier, lifecycle_stage,
     version_label, model_owner_user_id, business_sponsor_user_id,
     framework, hosting, inference_latency_ms,
     has_pdpl_dpia, has_fairness_audit,
     has_explainability_doc, is_high_risk_eu_ai_act,
     sdaia_ai_ethics_compliance, sama_ai_principles_compliance)
VALUES
    (1, N'MODEL-2026-005',
     N'Branch Footfall Forecasting v0.9',
     N'التنبّؤ بأعداد زوّار الفروع - الإصدار 0.9',
     N'Prophet-style time-series forecasting per branch by hour-of-day and day-of-week, tuned for Ramadan, Hajj season, and end-of-month payroll spikes. Drives teller-staffing decisions only — no customer-facing outputs.',
     N'تنبّؤ بسلسلة زمنية على نمط Prophet لكل فرع بحسب ساعة اليوم ويوم الأسبوع، مُعايَر لأنماط رمضان وموسم الحج وذروة نهاية الشهر بعد صرف الرواتب. يقود قرارات تخصيص الصرّافين فقط، دون أي مخرج يواجه العميل.',
     N'FORECASTING', N'OPERATIONS_AUTOMATION', N'LOW', N'VALIDATION',
     N'0.9.3', 1, 1,
     N'CUSTOM', N'ON_PREM', 150,
     0, 0,
     0, 0,
     N'NOT_ASSESSED', N'NOT_ASSESSED');
GO

-- ─── 5. Pre-seed fairness audits ───────────────────────────────────
-- Credit (MODEL-2026-001) fairness audit — passed.
IF NOT EXISTS (
    SELECT 1 FROM ai_governance.ml_model_audit a
    JOIN ai_governance.ml_model m ON m.id = a.model_id
    WHERE m.organisation_id = 1 AND m.model_code = N'MODEL-2026-001'
      AND a.audit_type = N'FAIRNESS' AND a.audit_date = '2026-02-15' AND a.is_deleted = 0)
INSERT INTO ai_governance.ml_model_audit
    (organisation_id, model_id, audit_type, audit_date, auditor_user_id,
     methodology_en, methodology_ar, findings_en, findings_ar, status, metrics_json)
SELECT 1, m.id, N'FAIRNESS', '2026-02-15', 1,
       N'Demographic parity + equal opportunity tests across gender, nationality, age band, and region. Reference: SDAIA AI Ethics Principles §4 (Transparency & Explainability) + SAMA AI Governance Principles 2024 §3 (Fairness).',
       N'اختبارات التكافؤ الديموغرافي وتكافؤ الفرص عبر الجنس والجنسية والفئة العمرية والمنطقة. المرجع: مبادئ سدايا لأخلاقيات الذكاء الاصطناعي §4 (الشفافية وقابلية التفسير) ومبادئ البنك المركزي السعودي لحوكمة الذكاء الاصطناعي 2024 §3 (العدالة).',
       N'Demographic parity difference 0.038 (within 5pp tolerance). Equal opportunity gap 0.041 (within tolerance). Region (Eastern vs Central) showed marginal deltas — no remediation required.',
       N'فرق التكافؤ الديموغرافي 0.038 (ضمن نطاق تحمّل 5 نقاط مئوية). فجوة تكافؤ الفرص 0.041 (ضمن النطاق). أظهرت المنطقة (الشرقية مقابل الوسطى) فروقاً هامشية - لا تتطلّب معالجة.',
       N'PASSED',
       N'{"demographic_parity_diff": 0.038, "equal_opportunity_diff": 0.041, "accuracy": 0.882, "f1_score": 0.861}'
FROM ai_governance.ml_model m WHERE m.organisation_id = 1 AND m.model_code = N'MODEL-2026-001';
GO

-- Credit explainability audit — passed with conditions.
IF NOT EXISTS (
    SELECT 1 FROM ai_governance.ml_model_audit a
    JOIN ai_governance.ml_model m ON m.id = a.model_id
    WHERE m.organisation_id = 1 AND m.model_code = N'MODEL-2026-001'
      AND a.audit_type = N'EXPLAINABILITY' AND a.audit_date = '2026-01-25' AND a.is_deleted = 0)
INSERT INTO ai_governance.ml_model_audit
    (organisation_id, model_id, audit_type, audit_date, auditor_user_id,
     methodology_en, methodology_ar, findings_en, findings_ar, status, metrics_json)
SELECT 1, m.id, N'EXPLAINABILITY', '2026-01-25', 1,
       N'SHAP value extraction per decision, top-5 features surfaced to credit officer in T24 UI. Adverse-action reason codes mapped to PDPL Art.20 right-to-explanation.',
       N'استخراج قيم SHAP لكل قرار، وتمرير أهم 5 سمات إلى مسؤول الائتمان في واجهة T24. ترميز أسباب الإجراءات السلبية بما يتوافق مع المادة 20 من نظام حماية البيانات الشخصية (حقّ التفسير).',
       N'Reason-code coverage 98% of declines. Two edge-case patterns (combined SIMAH-thin + new-employer) still fall back to generic code — pending mapping in v2.4.',
       N'تغطية رموز الأسباب لـ 98% من حالات الرفض. نمطان حدّيان (سجل سمة محدود + صاحب عمل جديد) لا يزالان يرجعان إلى رمز عام - في انتظار التعيين في الإصدار 2.4.',
       N'PASSED_WITH_CONDITIONS',
       N'{"reason_code_coverage": 0.98, "avg_shap_features_surfaced": 5}'
FROM ai_governance.ml_model m WHERE m.organisation_id = 1 AND m.model_code = N'MODEL-2026-001';
GO

-- AML (MODEL-2026-002) fairness audit — passed.
IF NOT EXISTS (
    SELECT 1 FROM ai_governance.ml_model_audit a
    JOIN ai_governance.ml_model m ON m.id = a.model_id
    WHERE m.organisation_id = 1 AND m.model_code = N'MODEL-2026-002'
      AND a.audit_type = N'FAIRNESS' AND a.audit_date = '2026-01-30' AND a.is_deleted = 0)
INSERT INTO ai_governance.ml_model_audit
    (organisation_id, model_id, audit_type, audit_date, auditor_user_id,
     methodology_en, methodology_ar, findings_en, findings_ar, status, metrics_json)
SELECT 1, m.id, N'FAIRNESS', '2026-01-30', 1,
       N'Disparate-impact ratio across nationality segments (Saudi / GCC / expatriate). Volume of false-positive STR drafts compared by segment.',
       N'نسبة التأثير غير المتكافئ عبر شرائح الجنسية (سعودي / خليجي / مقيم). مقارنة حجم مسوّدات بلاغات المعاملات المشبوهة الإيجابية الكاذبة لكل شريحة.',
       N'Disparate impact ratio 0.91 (above 0.80 four-fifths threshold). No remediation needed. Saudi-typology features (hawala, FX outflow patterns) appropriately distributed — no nationality proxy effect.',
       N'نسبة التأثير غير المتكافئ 0.91 (أعلى من حدّ الأربعة أخماس 0.80). لا تتطلّب معالجة. سمات الأنماط السعودية (الحوالة، أنماط التحويل الخارجي) موزّعة بشكل ملائم - لا يوجد أثر بديل للجنسية.',
       N'PASSED',
       N'{"disparate_impact_ratio": 0.91, "false_positive_rate_saudi": 0.082, "false_positive_rate_gcc": 0.089, "false_positive_rate_expat": 0.091}'
FROM ai_governance.ml_model m WHERE m.organisation_id = 1 AND m.model_code = N'MODEL-2026-002';
GO

-- ─── 6. Pre-seed drift observations on the credit model ───────────
IF NOT EXISTS (
    SELECT 1 FROM ai_governance.ml_model_drift_observation d
    JOIN ai_governance.ml_model m ON m.id = d.model_id
    WHERE m.organisation_id = 1 AND m.model_code = N'MODEL-2026-001'
      AND d.metric_name = N'population_stability_index'
      AND d.observed_at = '2026-04-10 09:00:00' AND d.is_deleted = 0)
INSERT INTO ai_governance.ml_model_drift_observation
    (organisation_id, model_id, observed_at, observation_type, severity,
     metric_name, metric_value, baseline_value, drift_threshold, notes_en, notes_ar)
SELECT 1, m.id, '2026-04-10 09:00:00', N'DATA_DRIFT', N'MINOR',
       N'population_stability_index', 0.142000, 0.000000, 0.200000,
       N'Monthly PSI watch — applicant income distribution shifted (new wave of post-Vision-2030 SME entrepreneur applicants). Within tolerance.',
       N'مراقبة شهرية لمؤشّر استقرار العامّة - تحوّل توزيع دخل المتقدّمين (موجة جديدة من روّاد الأعمال في الشركات الصغيرة بعد رؤية 2030). ضمن نطاق التحمّل.'
FROM ai_governance.ml_model m WHERE m.organisation_id = 1 AND m.model_code = N'MODEL-2026-001';
GO

IF NOT EXISTS (
    SELECT 1 FROM ai_governance.ml_model_drift_observation d
    JOIN ai_governance.ml_model m ON m.id = d.model_id
    WHERE m.organisation_id = 1 AND m.model_code = N'MODEL-2026-001'
      AND d.metric_name = N'auc_rolling_30d'
      AND d.observed_at = '2026-05-05 09:00:00' AND d.is_deleted = 0)
INSERT INTO ai_governance.ml_model_drift_observation
    (organisation_id, model_id, observed_at, observation_type, severity,
     metric_name, metric_value, baseline_value, drift_threshold, notes_en, notes_ar)
SELECT 1, m.id, '2026-05-05 09:00:00', N'PERFORMANCE_DEGRADATION', N'MODERATE',
       N'auc_rolling_30d', 0.831000, 0.882000, 0.860000,
       N'30-day rolling AUC fell to 0.831 (baseline 0.882, threshold 0.86). Retraining ticket opened — likely cohort shift from post-Ramadan applicant mix.',
       N'انخفض مقياس AUC المتدحرج لـ 30 يوماً إلى 0.831 (الأساس 0.882، الحدّ 0.86). فُتح طلب إعادة تدريب - تحوّل محتمل في الفئات بعد رمضان.'
FROM ai_governance.ml_model m WHERE m.organisation_id = 1 AND m.model_code = N'MODEL-2026-001';
GO
