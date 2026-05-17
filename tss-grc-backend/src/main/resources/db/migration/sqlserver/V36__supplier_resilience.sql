-- V36 — Supplier resilience: AlternateSupplier + ExitPlan (Saudi GRC §3.3).
--
-- Closes priority gap #4 from docs/saudi-grc-gap-analysis.md. The
-- existing scm module covers supplier registration, criticality, SLAs
-- and incident escalation but has no record of (a) the pre-vetted
-- alternates we would fall back to and (b) the concrete disengagement
-- plan if a critical supplier fails. Both are required for the
-- "supplier resilience report" (§30.3) and are explicitly called out
-- in §3.3 of the implementation prompt.
--
-- alternate_suppliers — one row per (primary vendor, alternate). A
-- critical vendor may have 1-3 alternates ranked by role.
--
-- exit_plans — one row per (vendor, contract). Holds the ordered
-- steps_json playbook, the last-tested outcome, and the next-test
-- due date so the resilience dashboard can flag drift.

SET QUOTED_IDENTIFIER ON;
GO

-- ─── alternate_suppliers ──────────────────────────────────────────────
IF OBJECT_ID(N'scm.alternate_suppliers', N'U') IS NULL
BEGIN
    CREATE TABLE scm.alternate_suppliers (
        id                          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id             BIGINT NOT NULL,

        -- Primary vendor we'd be falling back FROM.
        primary_vendor_id           BIGINT        NULL,
        primary_vendor_name         NVARCHAR(255) NOT NULL,

        -- Alternate to fall back TO.
        alternate_vendor_name       NVARCHAR(255) NOT NULL,
        alternate_vendor_country    NVARCHAR(3)   NULL,    -- ISO-3 alpha
        alternate_vendor_role       NVARCHAR(20)  NOT NULL,-- BACKUP_PRIMARY|BACKUP_SECONDARY|TERTIARY

        service_scope_en            NVARCHAR(MAX) NOT NULL,
        service_scope_ar            NVARCHAR(MAX) NULL,

        estimated_onboarding_days   INT           NULL,
        estimated_cost_sar          DECIMAL(18,2) NULL,

        last_assessed_at            DATETIME2(3)  NULL,
        last_assessed_by            BIGINT        NULL,
        last_assessed_outcome       NVARCHAR(20)  NULL,    -- READY|CONDITIONAL|GAP|NOT_VIABLE

        contractual_status          NVARCHAR(20)  NOT NULL DEFAULT N'NONE',
                                                            -- NONE|MOU_SIGNED|FRAMEWORK_ACTIVE

        notes_en                    NVARCHAR(MAX) NULL,
        notes_ar                    NVARCHAR(MAX) NULL,

        status                      NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
                                                            -- DRAFT|APPROVED|ARCHIVED
        approved_at                 DATETIME2(3)  NULL,
        approved_by                 BIGINT        NULL,
        approver_name               NVARCHAR(255) NULL,
        archived_at                 DATETIME2(3)  NULL,
        archive_reason              NVARCHAR(500) NULL,

        -- BaseEntity audit fields.
        external_id                 NVARCHAR(64)  NULL,
        is_deleted                  SMALLINT      NOT NULL DEFAULT 0,
        is_active                   SMALLINT      NOT NULL DEFAULT 1,
        deleted_at                  DATETIME2(3)  NULL,
        deleted_by                  BIGINT        NULL,
        created_at                  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT        NULL,
        updated_at                  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by                  BIGINT        NULL,
        row_version                 BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_alt_role CHECK (alternate_vendor_role IN
            (N'BACKUP_PRIMARY', N'BACKUP_SECONDARY', N'TERTIARY')),
        CONSTRAINT CK_alt_contractual CHECK (contractual_status IN
            (N'NONE', N'MOU_SIGNED', N'FRAMEWORK_ACTIVE')),
        CONSTRAINT CK_alt_outcome CHECK (last_assessed_outcome IS NULL OR last_assessed_outcome IN
            (N'READY', N'CONDITIONAL', N'GAP', N'NOT_VIABLE')),
        CONSTRAINT CK_alt_status CHECK (status IN (N'DRAFT', N'APPROVED', N'ARCHIVED'))
    );
END
GO

IF OBJECT_ID(N'scm.alternate_suppliers', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_alt_primary_vendor' AND object_id = OBJECT_ID(N'scm.alternate_suppliers'))
BEGIN
    CREATE INDEX IX_alt_primary_vendor ON scm.alternate_suppliers (organisation_id, primary_vendor_id)
        WHERE is_deleted = 0;
END
GO

-- ─── exit_plans ──────────────────────────────────────────────────────
IF OBJECT_ID(N'scm.exit_plans', N'U') IS NULL
BEGIN
    CREATE TABLE scm.exit_plans (
        id                          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id             BIGINT NOT NULL,

        vendor_id                   BIGINT        NULL,
        vendor_name                 NVARCHAR(255) NOT NULL,
        contract_code               NVARCHAR(64)  NULL,

        plan_code                   NVARCHAR(64)  NOT NULL,   -- EXP-2026-0007
        title_en                    NVARCHAR(255) NOT NULL,
        title_ar                    NVARCHAR(255) NULL,

        -- Disengagement triggers — comma-separated tags (BREACH_SLA, BREACH_NCA, etc.).
        trigger_conditions          NVARCHAR(MAX) NULL,
        severity_threshold          NVARCHAR(20)  NULL,        -- LOW|MEDIUM|HIGH|CRITICAL

        target_disengagement_days   INT           NOT NULL DEFAULT 90,

        /*
         * Ordered playbook of disengagement steps:
         * [{ "seq":1, "actionEn":"...", "actionAr":"...",
         *    "owner":"Vendor Risk Manager", "days":7,
         *    "evidenceRequired":true,
         *    "doneAt":null, "doneBy":null }, ...]
         */
        steps_json                  NVARCHAR(MAX) NOT NULL,

        data_migration_plan_en      NVARCHAR(MAX) NULL,
        data_migration_plan_ar      NVARCHAR(MAX) NULL,
        knowledge_transfer_plan_en  NVARCHAR(MAX) NULL,
        knowledge_transfer_plan_ar  NVARCHAR(MAX) NULL,

        last_tested_at              DATETIME2(3)  NULL,
        last_tested_outcome         NVARCHAR(20)  NULL,        -- NOT_TESTED|PASSED|GAP_IDENTIFIED|FAILED
        next_test_due               DATE          NULL,

        status                      NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
                                                                -- DRAFT|APPROVED|ACTIVATED|ARCHIVED
        approved_at                 DATETIME2(3)  NULL,
        approved_by                 BIGINT        NULL,
        approver_name               NVARCHAR(255) NULL,
        activated_at                DATETIME2(3)  NULL,
        activated_by                BIGINT        NULL,
        activation_reason           NVARCHAR(500) NULL,
        archived_at                 DATETIME2(3)  NULL,
        archive_reason              NVARCHAR(500) NULL,
        rejection_reason            NVARCHAR(500) NULL,

        external_id                 NVARCHAR(64)  NULL,
        is_deleted                  SMALLINT      NOT NULL DEFAULT 0,
        is_active                   SMALLINT      NOT NULL DEFAULT 1,
        deleted_at                  DATETIME2(3)  NULL,
        deleted_by                  BIGINT        NULL,
        created_at                  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        created_by                  BIGINT        NULL,
        updated_at                  DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_by                  BIGINT        NULL,
        row_version                 BIGINT        NOT NULL DEFAULT 0,

        CONSTRAINT CK_exp_status CHECK (status IN
            (N'DRAFT', N'APPROVED', N'ACTIVATED', N'ARCHIVED', N'REJECTED')),
        CONSTRAINT CK_exp_severity CHECK (severity_threshold IS NULL OR severity_threshold IN
            (N'LOW', N'MEDIUM', N'HIGH', N'CRITICAL')),
        CONSTRAINT CK_exp_test_outcome CHECK (last_tested_outcome IS NULL OR last_tested_outcome IN
            (N'NOT_TESTED', N'PASSED', N'GAP_IDENTIFIED', N'FAILED')),
        CONSTRAINT UQ_exp_code UNIQUE (organisation_id, plan_code)
    );
END
GO

IF OBJECT_ID(N'scm.exit_plans', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_exp_vendor' AND object_id = OBJECT_ID(N'scm.exit_plans'))
BEGIN
    CREATE INDEX IX_exp_vendor ON scm.exit_plans (organisation_id, vendor_id)
        WHERE is_deleted = 0;
END
GO

-- ─── Demo seed ───────────────────────────────────────────────────────
-- 6 alternate-supplier rows across 3 primary vendors + 4 exit plans
-- spanning DRAFT/APPROVED/ACTIVATED states. Aramco, Solutions by STC,
-- TCS Saudi Arabia, and AWS as primaries (the same vendors referenced
-- by V33 + V35) for cross-module continuity.

IF OBJECT_ID(N'scm.alternate_suppliers', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM scm.alternate_suppliers WHERE external_id = N'ALT-DEMO-01')
BEGIN
    INSERT INTO scm.alternate_suppliers
        (organisation_id, primary_vendor_id, primary_vendor_name,
         alternate_vendor_name, alternate_vendor_country, alternate_vendor_role,
         service_scope_en, service_scope_ar,
         estimated_onboarding_days, estimated_cost_sar,
         last_assessed_at, last_assessed_by, last_assessed_outcome,
         contractual_status, notes_en, notes_ar,
         status, approved_at, approved_by, approver_name,
         external_id)
    VALUES
        -- Solutions by STC → backup primary + secondary
        (1, NULL, N'Solutions by STC',
         N'Mobily Business', N'SAU', N'BACKUP_PRIMARY',
         N'Managed services for HR + finance SaaS hosting, 24x7 NOC, 99.95% SLA',
         N'خدمات مُدارة لاستضافة برامج الموارد البشرية والمالية، مركز عمليات الشبكة على مدار الساعة، اتفاقية مستوى خدمة 99.95%',
         45, 2800000.00,
         DATEADD(DAY, -30, SYSUTCDATETIME()), 2, N'READY',
         N'MOU_SIGNED',
         N'MOU signed 2026-02; framework SLA negotiation in flight (NCA ECC alignment confirmed).',
         N'مذكرة تفاهم موقعة فبراير 2026؛ التفاوض على اتفاقية إطارية جارٍ (تم تأكيد المواءمة مع ضوابط NCA ECC).',
         N'APPROVED', DATEADD(DAY, -25, SYSUTCDATETIME()), 1, N'admin',
         N'ALT-DEMO-01'),
        (1, NULL, N'Solutions by STC',
         N'Wipro Arabia', N'SAU', N'BACKUP_SECONDARY',
         N'Tertiary fallback for HR SaaS managed services only',
         N'بديل ثالث لخدمات الموارد البشرية المُدارة فقط',
         60, 1900000.00,
         DATEADD(DAY, -90, SYSUTCDATETIME()), 2, N'CONDITIONAL',
         N'NONE',
         N'Conditional — NCA ECC alignment letter pending; would need 60-day onboarding window.',
         N'مشروط - في انتظار خطاب مواءمة NCA ECC؛ سيتطلب نافذة تهيئة 60 يوماً.',
         N'APPROVED', DATEADD(DAY, -80, SYSUTCDATETIME()), 1, N'admin',
         N'ALT-DEMO-02'),
        -- AWS (cloud) → 2 alternates
        (1, NULL, N'AWS (managed cloud services)',
         N'Google Cloud — Dammam region', N'SAU', N'BACKUP_PRIMARY',
         N'Compute + storage + managed databases in-Kingdom, parity feature set, KSA data residency',
         N'حوسبة وتخزين وقواعد بيانات مُدارة داخل المملكة، مجموعة ميزات متكافئة، إقامة بيانات في المملكة',
         30, 3500000.00,
         DATEADD(DAY, -15, SYSUTCDATETIME()), 2, N'READY',
         N'FRAMEWORK_ACTIVE',
         N'Framework active since 2025-Q4; standby capacity reservation confirmed for 40% of primary footprint.',
         N'الإطار نشط منذ الربع الرابع 2025؛ تم تأكيد حجز السعة الاحتياطية لـ 40% من البصمة الأساسية.',
         N'APPROVED', DATEADD(DAY, -10, SYSUTCDATETIME()), 1, N'admin',
         N'ALT-DEMO-03'),
        (1, NULL, N'AWS (managed cloud services)',
         N'Oracle Cloud — Jeddah region', N'SAU', N'BACKUP_SECONDARY',
         N'Disaster-recovery target for the regulated workload subset',
         N'هدف التعافي من الكوارث للأحمال الخاضعة للتنظيم',
         60, 2400000.00,
         DATEADD(DAY, -200, SYSUTCDATETIME()), 2, N'GAP',
         N'NONE',
         N'Gap: 2025-Q3 assessment flagged storage cost > 3x primary; needs renegotiation before MOU.',
         N'فجوة: حدد تقييم الربع الثالث 2025 أن تكلفة التخزين أعلى من الأساسي بثلاثة أضعاف؛ تحتاج إعادة تفاوض قبل المذكرة.',
         N'DRAFT', NULL, NULL, NULL,
         N'ALT-DEMO-04'),
        -- TCS Saudi → 1 alternate (Indian payroll BPO)
        (1, NULL, N'TCS Saudi Arabia (payroll BPO)',
         N'ADP MENA', N'ARE', N'BACKUP_PRIMARY',
         N'Payroll processing + tax filing for KSA + GCC employee population (~1450 headcount)',
         N'معالجة الرواتب وتقديم الضرائب لقوة عاملة في المملكة ودول مجلس التعاون (~1450 موظف)',
         90, 4200000.00,
         DATEADD(DAY, -45, SYSUTCDATETIME()), 2, N'CONDITIONAL',
         N'NONE',
         N'PDPL cross-border review required before MOU — UAE data-residency check pending SDAIA opinion.',
         N'مطلوب مراجعة النقل العابر للبيانات قبل المذكرة - تحقق إقامة البيانات في الإمارات في انتظار رأي سدايا.',
         N'DRAFT', NULL, NULL, NULL,
         N'ALT-DEMO-05'),
        -- Aramco logistics → 1 alternate (NOT_VIABLE, archived)
        (1, NULL, N'Aramco Trading Logistics',
         N'Maersk Saudi Arabia', N'SAU', N'TERTIARY',
         N'Bulk logistics for oilfield equipment — investigated as a hedge',
         N'لوجستيات سائبة لمعدات حقول النفط - تم بحثه كتحوط',
         180, 6500000.00,
         DATEADD(DAY, -300, SYSUTCDATETIME()), 2, N'NOT_VIABLE',
         N'NONE',
         N'Not viable — capacity gap > 60% and onboarding cost-prohibitive. Archived 2026-01.',
         N'غير قابل للتطبيق - فجوة سعة أكثر من 60% وتكلفة التهيئة باهظة. تمت الأرشفة يناير 2026.',
         N'ARCHIVED', NULL, NULL, NULL,
         N'ALT-DEMO-06');
END
GO

-- ─── exit_plans seed ─────────────────────────────────────────────────
IF OBJECT_ID(N'scm.exit_plans', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM scm.exit_plans WHERE plan_code = N'EXP-DEMO-01')
BEGIN
    INSERT INTO scm.exit_plans
        (organisation_id, vendor_id, vendor_name, contract_code,
         plan_code, title_en, title_ar,
         trigger_conditions, severity_threshold, target_disengagement_days,
         steps_json,
         data_migration_plan_en, data_migration_plan_ar,
         knowledge_transfer_plan_en, knowledge_transfer_plan_ar,
         last_tested_at, last_tested_outcome, next_test_due,
         status, approved_at, approved_by, approver_name,
         external_id)
    VALUES
        -- Solutions by STC exit plan — APPROVED, last-tested PASSED
        (1, NULL, N'Solutions by STC', N'CON-2024-0042',
         N'EXP-DEMO-01',
         N'Solutions by STC — managed services exit plan',
         N'خطة الخروج من الحلول من STC - الخدمات المُدارة',
         N'BREACH_NCA_ECC,SLA_BREACH_30D,DATA_INCIDENT_HIGH',
         N'HIGH', 90,
         N'[{"seq":1,"actionEn":"Trigger formal notice under MSA §12.2","actionAr":"إصدار إشعار رسمي بموجب البند 12.2 من اتفاقية الخدمات الرئيسية","owner":"General Counsel","days":3,"evidenceRequired":true},{"seq":2,"actionEn":"Activate Mobily Business backup primary contract","actionAr":"تفعيل عقد Mobily Business البديل الأساسي","owner":"Vendor Risk Manager","days":7,"evidenceRequired":true},{"seq":3,"actionEn":"Data extraction from STC environment to TSS-managed S3","actionAr":"استخراج البيانات من بيئة STC إلى S3 المُدار من TSS","owner":"Data Engineering Lead","days":14,"evidenceRequired":true},{"seq":4,"actionEn":"Cutover application traffic to Mobily SaaS endpoints","actionAr":"تحويل حركة التطبيق إلى نقاط نهاية Mobily SaaS","owner":"SRE Lead","days":21,"evidenceRequired":true},{"seq":5,"actionEn":"30-day parallel run + reconciliation report","actionAr":"تشغيل متوازي 30 يوماً + تقرير المطابقة","owner":"Head of HR Systems","days":60,"evidenceRequired":true},{"seq":6,"actionEn":"Final SLA reconciliation + STC contract close-out","actionAr":"المطابقة النهائية لاتفاقية مستوى الخدمة + إقفال عقد STC","owner":"Procurement Lead","days":90,"evidenceRequired":true}]',
         N'S3-mirrored daily; final cut-over uses AWS DataSync; data-quality reconciliation via 30-day parallel run.',
         N'يتم النسخ المتطابق في S3 يومياً؛ التحويل النهائي يستخدم AWS DataSync؛ مطابقة جودة البيانات عبر تشغيل متوازي 30 يوماً.',
         N'Runbook + recorded walkthroughs handed over during 14-day cutover window.',
         N'كتاب التشغيل + الجلسات الموثقة يتم تسليمها خلال نافذة التحويل البالغة 14 يوماً.',
         DATEADD(MONTH, -3, SYSUTCDATETIME()), N'PASSED', DATEADD(MONTH, 9, CAST(SYSUTCDATETIME() AS DATE)),
         N'APPROVED', DATEADD(MONTH, -4, SYSUTCDATETIME()), 1, N'admin',
         N'EXP-DEMO-01'),

        -- AWS exit plan — APPROVED, last-tested GAP_IDENTIFIED
        (1, NULL, N'AWS (managed cloud services)', N'CON-2023-0017',
         N'EXP-DEMO-02',
         N'AWS managed services exit plan',
         N'خطة الخروج من خدمات AWS المُدارة',
         N'COST_EXCEEDED_BUDGET,RESIDENCY_BREACH,VENDOR_ACQUISITION',
         N'CRITICAL', 120,
         N'[{"seq":1,"actionEn":"Freeze new workload onboarding to AWS","actionAr":"تجميد إدراج أحمال جديدة على AWS","owner":"Cloud Platform Lead","days":1,"evidenceRequired":true},{"seq":2,"actionEn":"Activate Google Cloud Dammam standby capacity","actionAr":"تفعيل سعة Google Cloud الاحتياطية بالدمام","owner":"Cloud Platform Lead","days":3,"evidenceRequired":true},{"seq":3,"actionEn":"Lift-and-shift Tier-1 workloads with Terraform replay","actionAr":"نقل أحمال الطبقة الأولى مع إعادة تشغيل Terraform","owner":"SRE Lead","days":30,"evidenceRequired":true},{"seq":4,"actionEn":"Re-platform Tier-2 + Tier-3 workloads (refactor as needed)","actionAr":"إعادة بناء أحمال الطبقة الثانية والثالثة (إعادة هيكلة عند اللزوم)","owner":"Engineering Director","days":90,"evidenceRequired":true},{"seq":5,"actionEn":"Final AWS account close-out + data sanitization attestation","actionAr":"الإقفال النهائي لحساب AWS + إقرار تعقيم البيانات","owner":"Cloud Platform Lead","days":120,"evidenceRequired":true}]',
         N'Terraform-managed; daily S3 → GCS sync established 2025-Q4. Tier-1 RPO 1h; Tier-2/3 RPO 24h.',
         N'مُدار عبر Terraform؛ تم إنشاء المزامنة اليومية S3 → GCS في الربع الرابع 2025. RPO للطبقة الأولى ساعة واحدة؛ RPO للطبقتين الثانية والثالثة 24 ساعة.',
         N'Cloud platform team rotates through GCP training quarterly; runbooks in Confluence space GCPMIG.',
         N'فريق منصة السحابة يتدرب على Google Cloud كل ربع سنة؛ كتب التشغيل في مساحة Confluence المسماة GCPMIG.',
         DATEADD(MONTH, -6, SYSUTCDATETIME()), N'GAP_IDENTIFIED', DATEADD(MONTH, 6, CAST(SYSUTCDATETIME() AS DATE)),
         N'APPROVED', DATEADD(MONTH, -7, SYSUTCDATETIME()), 1, N'admin',
         N'EXP-DEMO-02'),

        -- TCS payroll exit plan — DRAFT (just being built)
        (1, NULL, N'TCS Saudi Arabia (payroll BPO)', N'CON-2025-0033',
         N'EXP-DEMO-03',
         N'TCS payroll BPO exit plan',
         N'خطة الخروج من تعهيد رواتب TCS',
         N'PDPL_RESIDENCY_BREACH,SLA_BREACH_60D,CONTRACT_TERMINATION',
         N'MEDIUM', 60,
         N'[{"seq":1,"actionEn":"Activate ADP MENA framework","actionAr":"تفعيل إطار ADP MENA","owner":"Vendor Risk Manager","days":7,"evidenceRequired":true},{"seq":2,"actionEn":"Payroll data export + reconciliation","actionAr":"تصدير بيانات الرواتب والمطابقة","owner":"HR Operations Lead","days":21,"evidenceRequired":true},{"seq":3,"actionEn":"Parallel payroll run x2 cycles","actionAr":"تشغيل رواتب متوازي لدورتين","owner":"HR Operations Lead","days":45,"evidenceRequired":true},{"seq":4,"actionEn":"Final TCS close-out + ZATCA filings transfer","actionAr":"الإقفال النهائي مع TCS ونقل تقديمات هيئة الزكاة والضريبة والجمارك","owner":"Finance Director","days":60,"evidenceRequired":true}]',
         N'Encrypted export via SFTP daily; ADP MENA target system validated end-of-month.',
         N'تصدير مشفر عبر SFTP يومياً؛ يتم التحقق من النظام المستهدف لـ ADP MENA في نهاية الشهر.',
         N'TBD — knowledge transfer plan pending ADP framework activation.',
         N'لاحقاً - خطة نقل المعرفة في انتظار تفعيل إطار ADP.',
         NULL, N'NOT_TESTED', DATEADD(MONTH, 3, CAST(SYSUTCDATETIME() AS DATE)),
         N'DRAFT', NULL, NULL, NULL,
         N'EXP-DEMO-03'),

        -- Aramco logistics exit plan — ACTIVATED (real-life scenario)
        (1, NULL, N'Aramco Trading Logistics', N'CON-2024-0099',
         N'EXP-DEMO-04',
         N'Aramco logistics — emergency disengagement',
         N'لوجستيات أرامكو - فك الارتباط الطارئ',
         N'CONTRACT_REPUDIATION,REGULATORY_ORDER',
         N'CRITICAL', 30,
         N'[{"seq":1,"actionEn":"Freeze new shipment authorisations","actionAr":"تجميد تصاريح الشحنات الجديدة","owner":"Logistics Director","days":1,"evidenceRequired":true,"doneAt":"2026-05-08T08:00:00Z","doneBy":2},{"seq":2,"actionEn":"Stand-up emergency procurement war-room","actionAr":"تشكيل غرفة عمليات المشتريات الطارئة","owner":"COO","days":2,"evidenceRequired":true,"doneAt":"2026-05-08T14:00:00Z","doneBy":1},{"seq":3,"actionEn":"Engage Maersk for spot-rate critical shipments","actionAr":"التعاقد مع Maersk للشحنات الحرجة بسعر فوري","owner":"Procurement Lead","days":5,"evidenceRequired":true,"doneAt":"2026-05-10T16:30:00Z","doneBy":2},{"seq":4,"actionEn":"Notify regulator + reroute remaining shipments","actionAr":"إبلاغ الجهة التنظيمية وإعادة توجيه الشحنات المتبقية","owner":"Compliance Director","days":14,"evidenceRequired":true},{"seq":5,"actionEn":"Close-out + lessons learned","actionAr":"الإقفال + الدروس المستفادة","owner":"COO","days":30,"evidenceRequired":true}]',
         N'No personal data in scope — logistical metadata only.',
         N'لا بيانات شخصية في النطاق - بيانات لوجستية وصفية فقط.',
         N'War-room runbook activated; daily stand-up at 09:00 KSA.',
         N'تم تفعيل كتاب تشغيل غرفة العمليات؛ اجتماع يومي الساعة 09:00 بتوقيت المملكة.',
         DATEADD(DAY, -180, SYSUTCDATETIME()), N'PASSED', DATEADD(MONTH, 12, CAST(SYSUTCDATETIME() AS DATE)),
         N'ACTIVATED', DATEADD(DAY, -200, SYSUTCDATETIME()), 1, N'admin',
         N'EXP-DEMO-04'),

        -- Old vendor exit plan — REJECTED with reason
        (1, NULL, N'Legacy Vendor Inc.', N'CON-2022-0011',
         N'EXP-DEMO-05',
         N'Legacy Vendor — proposed exit plan (v1)',
         N'المورد القديم - خطة الخروج المقترحة (الإصدار 1)',
         N'CONTRACT_EXPIRY',
         N'LOW', 180,
         N'[{"seq":1,"actionEn":"Notify contract non-renewal","actionAr":"إبلاغ بعدم تجديد العقد","owner":"Procurement Lead","days":30,"evidenceRequired":false}]',
         NULL, NULL, NULL, NULL,
         NULL, NULL, NULL,
         N'REJECTED', NULL, NULL, NULL,
         N'EXP-DEMO-05');

    -- Set rejection reason for EXP-DEMO-05 (CHECK constraint requires NOT NULL on REJECTED but column wasn't in the INSERT — fix below)
    UPDATE scm.exit_plans
       SET rejection_reason = N'Single-step plan is insufficient for a 180-day disengagement window. Resubmit with steps for data migration, knowledge transfer, and final reconciliation.'
     WHERE plan_code = N'EXP-DEMO-05';
END
GO

PRINT N'V36 — scm.alternate_suppliers (6 rows) + scm.exit_plans (5 rows) applied.';
GO
