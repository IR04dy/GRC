-- V41 — IT Disaster Recovery plans (Saudi GRC §3.2 + gap analysis item 7).
--
-- BCM lifecycle handles BCP-level continuity; this table adds the
-- technical disaster-recovery plan layer that DR architects + SREs
-- live in: per critical system / service, with explicit RTO / RPO /
-- MTPD targets, DR-site type (hot/warm/cold/active-active), failover
-- and failback step playbooks, and a tested-outcome record.
--
-- Lifecycle:  DRAFT → APPROVED → ACTIVATED → ARCHIVED
--             DRAFT → REJECTED (terminal)

SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'bcm.dr_plans', N'U') IS NULL
BEGIN
    CREATE TABLE bcm.dr_plans (
        id                          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        organisation_id             BIGINT NOT NULL,

        plan_code                   NVARCHAR(64)  NOT NULL,   -- DRP-2026-001
        title_en                    NVARCHAR(255) NOT NULL,
        title_ar                    NVARCHAR(255) NULL,
        description_en              NVARCHAR(MAX) NULL,
        description_ar              NVARCHAR(MAX) NULL,

        linked_bcp_id               BIGINT        NULL,
        linked_bcp_code             NVARCHAR(64)  NULL,
        critical_system_id          BIGINT        NULL,
        critical_system_name        NVARCHAR(255) NULL,
        owner                       NVARCHAR(255) NULL,

        rto_hours                   INT           NOT NULL DEFAULT 4,
        rpo_hours                   INT           NOT NULL DEFAULT 1,
        mtpd_hours                  INT           NULL,        -- max tolerable period of disruption

        -- HOT | WARM | COLD | ACTIVE_ACTIVE | CLOUD_DR
        dr_site_type                NVARCHAR(20)  NOT NULL DEFAULT N'WARM',
        dr_site_location            NVARCHAR(255) NULL,
        dr_site_provider            NVARCHAR(255) NULL,

        recovery_strategy_en        NVARCHAR(MAX) NULL,
        recovery_strategy_ar        NVARCHAR(MAX) NULL,

        -- [{system, type, criticality, notes}]
        dependencies_json           NVARCHAR(MAX) NULL,

        -- [{seq, actionEn, actionAr, owner, minutesAllocated, automated}]
        failover_steps_json         NVARCHAR(MAX) NULL,
        failback_steps_json         NVARCHAR(MAX) NULL,

        data_backup_strategy_en     NVARCHAR(MAX) NULL,
        data_backup_strategy_ar     NVARCHAR(MAX) NULL,

        last_tested_at              DATETIME2(3)  NULL,
        last_tested_by              BIGINT        NULL,
        last_tested_outcome         NVARCHAR(20)  NULL,
                                            -- NOT_TESTED | PASSED | GAP | FAILED
        last_test_notes_en          NVARCHAR(MAX) NULL,
        next_test_due               DATE          NULL,

        -- DRAFT | APPROVED | ACTIVATED | ARCHIVED | REJECTED
        status                      NVARCHAR(20)  NOT NULL DEFAULT N'DRAFT',
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

        CONSTRAINT CK_drp_site CHECK (dr_site_type IN
            (N'HOT', N'WARM', N'COLD', N'ACTIVE_ACTIVE', N'CLOUD_DR')),
        CONSTRAINT CK_drp_outcome CHECK (last_tested_outcome IS NULL OR last_tested_outcome IN
            (N'NOT_TESTED', N'PASSED', N'GAP', N'FAILED')),
        CONSTRAINT CK_drp_status CHECK (status IN
            (N'DRAFT', N'APPROVED', N'ACTIVATED', N'ARCHIVED', N'REJECTED')),
        CONSTRAINT UQ_drp_code UNIQUE (organisation_id, plan_code)
    );
END
GO

IF OBJECT_ID(N'bcm.dr_plans', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_drp_org_status' AND object_id = OBJECT_ID(N'bcm.dr_plans'))
BEGIN
    CREATE INDEX IX_drp_org_status ON bcm.dr_plans (organisation_id, status)
        WHERE is_deleted = 0;
END
GO

-- ═══ Demo seed — 8 DR plans across site types + statuses ════════════
IF OBJECT_ID(N'bcm.dr_plans', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM bcm.dr_plans WHERE plan_code = N'DRP-DEMO-01')
BEGIN
    INSERT INTO bcm.dr_plans
        (organisation_id, plan_code, title_en, title_ar, description_en, description_ar,
         linked_bcp_code, critical_system_name, owner,
         rto_hours, rpo_hours, mtpd_hours,
         dr_site_type, dr_site_location, dr_site_provider,
         recovery_strategy_en, recovery_strategy_ar,
         dependencies_json, failover_steps_json, failback_steps_json,
         data_backup_strategy_en, data_backup_strategy_ar,
         last_tested_at, last_tested_outcome, last_test_notes_en, next_test_due,
         status, approved_at, approved_by, approver_name,
         activated_at, activated_by, activation_reason,
         archived_at, archive_reason, rejection_reason,
         external_id)
    VALUES
        -- 1. APPROVED — Core ERP (SAP HANA) ACTIVE_ACTIVE
        (1, N'DRP-DEMO-01',
         N'SAP HANA — Core ERP DR plan',
         N'خطة التعافي - SAP HANA النظام المركزي',
         N'Active-active SAP HANA cluster between Riyadh primary and Jeddah DR. Synchronous replication; sub-minute RPO. Customer-facing modules prioritised.',
         N'مجموعة SAP HANA نشطة-نشطة بين الرياض الأساسي وجدة التعافي. نسخ متطابق متزامن؛ RPO أقل من دقيقة. أولوية لوحدات العملاء.',
         N'BCP-FINANCE-01', N'SAP HANA ERP (production)', N'Head of ERP Platform',
         2, 0, 8,
         N'ACTIVE_ACTIVE', N'Jeddah, Saudi Arabia', N'On-prem (TSS-managed)',
         N'Active-active synchronous replication; HANA System Replication mode SYNCMEM; automatic failover via SAP Landscape Management when latency >250ms for 60s.',
         N'نسخ متزامن نشط-نشط؛ وضع SAP HANA SYNCMEM؛ تحويل تلقائي عبر SAP Landscape Management عندما يتجاوز التأخير 250ms لمدة 60 ثانية.',
         N'[{"system":"Active Directory","type":"AUTH","criticality":"CRITICAL","notes":"Both sites have AD replicas"},{"system":"DNS / Route 53","type":"NETWORK","criticality":"HIGH","notes":"Health-check based failover"},{"system":"NetApp storage","type":"STORAGE","criticality":"CRITICAL","notes":"SyncMirror across sites"}]',
         N'[{"seq":1,"actionEn":"Verify Jeddah replica health (5min lag tolerance)","actionAr":"التحقق من صحة نسخة جدة (تسامح 5 دقائق)","owner":"SRE on-call","minutesAllocated":5,"automated":true},{"seq":2,"actionEn":"Trigger SAP Landscape Management failover","actionAr":"تشغيل تحويل SAP Landscape Management","owner":"SRE on-call","minutesAllocated":10,"automated":true},{"seq":3,"actionEn":"DNS health-check flips to Jeddah endpoint","actionAr":"فحص صحة DNS يحول إلى نقطة جدة","owner":"Network Lead","minutesAllocated":2,"automated":true},{"seq":4,"actionEn":"Smoke-test customer-facing API endpoints","actionAr":"اختبار سريع لنقاط نهاية API للعملاء","owner":"SRE Lead","minutesAllocated":15,"automated":false},{"seq":5,"actionEn":"Comms to executive committee + customer success","actionAr":"تواصل مع اللجنة التنفيذية ونجاح العملاء","owner":"COO","minutesAllocated":10,"automated":false}]',
         N'[{"seq":1,"actionEn":"Verify primary site health (HANA + storage + AD)","actionAr":"التحقق من صحة الموقع الأساسي","owner":"SRE Lead","minutesAllocated":30,"automated":false},{"seq":2,"actionEn":"Reverse replication direction (Jeddah → Riyadh)","actionAr":"عكس اتجاه النسخ (جدة → الرياض)","owner":"SRE on-call","minutesAllocated":120,"automated":false},{"seq":3,"actionEn":"DNS flip back to Riyadh","actionAr":"إعادة DNS إلى الرياض","owner":"Network Lead","minutesAllocated":15,"automated":true},{"seq":4,"actionEn":"Post-mortem + lessons learned","actionAr":"التحليل اللاحق والدروس المستفادة","owner":"SRE Lead","minutesAllocated":60,"automated":false}]',
         N'Daily SAP HANA backup to S3 Riyadh + cross-region replicate to GCS Dammam. Retention 30 days online, 12 months Glacier.',
         N'نسخة احتياطية يومية SAP HANA إلى S3 الرياض + نسخ عبر المناطق إلى GCS الدمام. الاحتفاظ 30 يوماً على الإنترنت، 12 شهراً Glacier.',
         DATEADD(MONTH, -1, SYSUTCDATETIME()), N'PASSED',
         N'Full failover exercise 2026-04-15. Failover completed in 4m 18s; well under 2-hour RTO. One observation: API timeout values needed tuning, fixed same week.',
         DATEADD(MONTH, 5, CAST(SYSUTCDATETIME() AS DATE)),
         N'APPROVED', DATEADD(DAY, -200, SYSUTCDATETIME()), 1, N'COO',
         NULL, NULL, NULL, NULL, NULL, NULL,
         N'DRP-DEMO-01'),

        -- 2. APPROVED — HR SaaS (Workday) WARM
        (1, N'DRP-DEMO-02',
         N'Workday HR — DR plan',
         N'خطة التعافي - Workday HR',
         N'Workday SaaS with vendor-managed DR in DC2. Our responsibility limited to integration layer + identity mapping.',
         N'Workday SaaS مع تعافٍ مُدار من المورد في DC2. مسؤوليتنا محدودة بطبقة التكامل وربط الهوية.',
         N'BCP-HR-01', N'Workday HR (production)', N'Head of HR Systems',
         4, 1, 24,
         N'WARM', N'Workday Dublin DC2 (vendor-managed)', N'Workday Inc.',
         N'Vendor-managed DR; our integration layer (Boomi) has hot-standby in AWS Riyadh failover region. Identity bridge SSO via Azure AD with custom claim mapping.',
         N'تعافٍ مُدار من المورد؛ طبقة التكامل لدينا (Boomi) لها استعداد حار في منطقة تعافي AWS الرياض. جسر الهوية SSO عبر Azure AD مع ربط مطالبات مخصص.',
         N'[{"system":"Azure AD","type":"AUTH","criticality":"CRITICAL"},{"system":"Boomi integration","type":"INTEGRATION","criticality":"HIGH"},{"system":"SuccessFactors fallback","type":"APPLICATION","criticality":"LOW","notes":"Read-only emergency archive"}]',
         N'[{"seq":1,"actionEn":"Confirm Workday DR activation via vendor portal","actionAr":"تأكيد تفعيل تعافي Workday عبر بوابة المورد","owner":"Workday admin","minutesAllocated":15,"automated":false},{"seq":2,"actionEn":"Switch Boomi integration to DR endpoint","actionAr":"تحويل تكامل Boomi إلى نقطة التعافي","owner":"Integration Lead","minutesAllocated":30,"automated":false},{"seq":3,"actionEn":"Validate Azure AD claim mapping for DR domain","actionAr":"التحقق من ربط مطالبات Azure AD لنطاق التعافي","owner":"Identity Lead","minutesAllocated":20,"automated":false}]',
         N'[{"seq":1,"actionEn":"Confirm primary Workday tenant restored","actionAr":"تأكيد استعادة مستأجر Workday الأساسي","owner":"Workday admin","minutesAllocated":15,"automated":false},{"seq":2,"actionEn":"Re-point integration + identity to primary","actionAr":"إعادة توجيه التكامل والهوية للأساسي","owner":"Integration Lead","minutesAllocated":30,"automated":false}]',
         N'Vendor maintains 4-tier backup (real-time, hourly, daily, weekly). Our integration logs backed up to S3 hourly.',
         N'يحافظ المورد على 4 مستويات نسخ احتياطي (فوري، ساعي، يومي، أسبوعي). سجلات التكامل لدينا تُنسخ إلى S3 ساعياً.',
         DATEADD(MONTH, -3, SYSUTCDATETIME()), N'PASSED',
         N'Tabletop exercise + paper-trace of vendor DR runbook. Identity claim mapping needed minor refresh; updated 2026-02.',
         DATEADD(MONTH, 3, CAST(SYSUTCDATETIME() AS DATE)),
         N'APPROVED', DATEADD(DAY, -150, SYSUTCDATETIME()), 1, N'CIO',
         NULL, NULL, NULL, NULL, NULL, NULL,
         N'DRP-DEMO-02'),

        -- 3. ACTIVATED — Email security (Mimecast incident May)
        (1, N'DRP-DEMO-03',
         N'Mimecast — email-security DR plan',
         N'خطة التعافي - Mimecast أمان البريد',
         N'Cloud-DR plan invoked 2026-05-03 during Mimecast service degradation. Proofpoint Essentials failover validated; restored 2026-05-04 09:00.',
         N'خطة تعافٍ سحابية مُفعلة 2026-05-03 خلال تدهور خدمة Mimecast. تم التحقق من التحويل إلى Proofpoint Essentials؛ استُعيدت 2026-05-04 09:00.',
         N'BCP-EMAIL-01', N'Mimecast email security', N'SecOps Lead',
         8, 4, 48,
         N'CLOUD_DR', N'Proofpoint Essentials KSA POP', N'Proofpoint Inc.',
         N'DNS-based MX failover to Proofpoint Essentials secondary tenant. Quarantine pulled from Mimecast archive for last 30 days.',
         N'تحويل MX قائم على DNS إلى مستأجر Proofpoint Essentials الثانوي. تم سحب الحجر من أرشيف Mimecast لآخر 30 يوماً.',
         N'[{"system":"DNS","type":"NETWORK","criticality":"CRITICAL"},{"system":"M365 Exchange","type":"APPLICATION","criticality":"CRITICAL"},{"system":"Mimecast archive","type":"DATA","criticality":"HIGH"}]',
         N'[{"seq":1,"actionEn":"Confirm Mimecast outage via vendor status page","actionAr":"تأكيد انقطاع Mimecast عبر صفحة حالة المورد","owner":"SecOps on-call","minutesAllocated":5,"automated":false,"doneAt":"2026-05-03T07:15:00Z"},{"seq":2,"actionEn":"Update DNS MX records to Proofpoint","actionAr":"تحديث سجلات DNS MX إلى Proofpoint","owner":"Network Lead","minutesAllocated":15,"automated":false,"doneAt":"2026-05-03T07:45:00Z"},{"seq":3,"actionEn":"Activate Proofpoint quarantine policies","actionAr":"تفعيل سياسات حجر Proofpoint","owner":"SecOps Lead","minutesAllocated":30,"automated":false,"doneAt":"2026-05-03T08:30:00Z"},{"seq":4,"actionEn":"Comms to all users via Teams","actionAr":"إبلاغ جميع المستخدمين عبر Teams","owner":"Comms Lead","minutesAllocated":15,"automated":false,"doneAt":"2026-05-03T09:00:00Z"}]',
         N'[{"seq":1,"actionEn":"Verify Mimecast service restored","actionAr":"التحقق من استعادة خدمة Mimecast","owner":"SecOps Lead","minutesAllocated":15,"automated":false},{"seq":2,"actionEn":"Cutover MX back to Mimecast","actionAr":"إعادة MX إلى Mimecast","owner":"Network Lead","minutesAllocated":15,"automated":false},{"seq":3,"actionEn":"Sync 30-day Proofpoint quarantine into Mimecast archive","actionAr":"مزامنة حجر Proofpoint لـ 30 يوماً مع أرشيف Mimecast","owner":"SecOps Lead","minutesAllocated":120,"automated":true}]',
         N'Email + quarantine retention 7y per ZATCA + NCA ECC. Daily Mimecast archive snapshot to S3.',
         N'الاحتفاظ بالبريد والحجر 7 سنوات وفق هيئة الزكاة والضريبة والجمارك و NCA ECC. لقطة أرشيف يومية لـ Mimecast إلى S3.',
         DATEADD(DAY, -8, SYSUTCDATETIME()), N'PASSED',
         N'Real activation 2026-05-03 — failover at 07:45, restored at 09:00 next day. Total downtime ~25h, well under 48h MTPD. Slack channel comms gap identified.',
         DATEADD(MONTH, 6, CAST(SYSUTCDATETIME() AS DATE)),
         N'ACTIVATED', DATEADD(DAY, -250, SYSUTCDATETIME()), 1, N'CISO',
         DATEADD(DAY, -8, SYSUTCDATETIME()), 1,
         N'Real Mimecast outage 2026-05-03 — vendor incident, traceable to GCS regional disruption.',
         NULL, NULL, NULL,
         N'DRP-DEMO-03'),

        -- 4. DRAFT — Salesforce CRM
        (1, N'DRP-DEMO-04',
         N'Salesforce CRM — DR plan (draft)',
         N'خطة التعافي - Salesforce CRM (مسودة)',
         N'Draft DR plan for Salesforce Sales Cloud + Service Cloud after the platform was promoted to Tier-1. Pending architecture review.',
         N'مسودة خطة التعافي لـ Salesforce Sales Cloud + Service Cloud بعد ترقية المنصة إلى الطبقة الأولى. بانتظار مراجعة المعمارية.',
         N'BCP-CRM-01', N'Salesforce CRM (production)', N'Head of Sales Ops',
         4, 4, 24,
         N'CLOUD_DR', N'Salesforce Hyperforce Riyadh (vendor)', N'Salesforce Inc.',
         N'Vendor-managed DR with Hyperforce in-region. Our integration via MuleSoft has 1-hour RPO via change-data-capture.',
         N'تعافٍ مُدار من المورد عبر Hyperforce داخل المنطقة. تكاملنا عبر MuleSoft له RPO ساعة واحدة عبر التقاط تغيير البيانات.',
         N'[{"system":"MuleSoft","type":"INTEGRATION","criticality":"HIGH"},{"system":"Marketing Cloud","type":"APPLICATION","criticality":"MEDIUM"}]',
         N'[{"seq":1,"actionEn":"Confirm Salesforce DR activation","actionAr":"تأكيد تفعيل التعافي لـ Salesforce","owner":"Salesforce admin","minutesAllocated":15,"automated":false},{"seq":2,"actionEn":"Switch MuleSoft to DR endpoint","actionAr":"تحويل MuleSoft إلى نقطة التعافي","owner":"Integration Lead","minutesAllocated":30,"automated":false}]',
         N'[]',
         N'Vendor-managed; weekly extract to S3 for archival + e-discovery.',
         N'مُدار من المورد؛ استخراج أسبوعي إلى S3 للأرشفة واكتشاف الأدلة.',
         NULL, NULL, NULL, NULL,
         N'DRAFT', NULL, NULL, NULL,
         NULL, NULL, NULL,
         NULL, NULL, NULL,
         N'DRP-DEMO-04'),

        -- 5. APPROVED — File-share + collaboration (M365)
        (1, N'DRP-DEMO-05',
         N'Microsoft 365 — file + collab DR plan',
         N'خطة التعافي - ملفات وتعاون Microsoft 365',
         N'SharePoint Online + OneDrive DR plan leveraging vendor SLA + AvePoint backup for granular restore.',
         N'خطة تعافي SharePoint Online + OneDrive بالاستفادة من اتفاقية المورد + AvePoint للاستعادة الدقيقة.',
         N'BCP-COLLAB-01', N'Microsoft 365 (SharePoint + OneDrive)', N'M365 Platform Lead',
         8, 1, 72,
         N'CLOUD_DR', N'Microsoft KSA region + AvePoint backup', N'Microsoft + AvePoint',
         N'Vendor-managed regional DR + AvePoint Cloud Backup with 1-year retention. Granular restore at item + site collection level.',
         N'تعافٍ إقليمي مُدار من المورد + النسخ الاحتياطي السحابي AvePoint مع احتفاظ سنة. استعادة دقيقة على مستوى العنصر ومجموعة الموقع.',
         N'[{"system":"Azure AD","type":"AUTH","criticality":"CRITICAL"},{"system":"Power Platform","type":"APPLICATION","criticality":"MEDIUM"}]',
         N'[{"seq":1,"actionEn":"Engage Microsoft Premier Support","actionAr":"التواصل مع دعم Microsoft Premier","owner":"M365 admin","minutesAllocated":30,"automated":false},{"seq":2,"actionEn":"Activate AvePoint emergency restore if SLA breached","actionAr":"تفعيل استعادة AvePoint الطارئة إذا تم اختراق اتفاقية مستوى الخدمة","owner":"M365 admin","minutesAllocated":120,"automated":false}]',
         N'[{"seq":1,"actionEn":"Confirm primary tenant restored","actionAr":"تأكيد استعادة المستأجر الأساسي","owner":"M365 admin","minutesAllocated":30,"automated":false}]',
         N'AvePoint daily backup; SharePoint native versioning; Litigation Hold for e-discovery; 7-year retention per ZATCA.',
         N'نسخ احتياطي يومي AvePoint؛ إصدار SharePoint الأصلي؛ احتجاز للتقاضي للاكتشاف؛ احتفاظ 7 سنوات وفق هيئة الزكاة والضريبة والجمارك.',
         DATEADD(MONTH, -2, SYSUTCDATETIME()), N'GAP',
         N'Tabletop revealed AvePoint restore times longer than the documented 4h target. Working with vendor on capacity expansion (Q3 2026 commit).',
         DATEADD(MONTH, 4, CAST(SYSUTCDATETIME() AS DATE)),
         N'APPROVED', DATEADD(DAY, -180, SYSUTCDATETIME()), 1, N'CIO',
         NULL, NULL, NULL, NULL, NULL, NULL,
         N'DRP-DEMO-05'),

        -- 6. ARCHIVED — Legacy mainframe (decommissioned)
        (1, N'DRP-DEMO-06',
         N'Legacy mainframe — DR plan (archived)',
         N'خطة التعافي للحاسوب الرئيسي القديم (مؤرشفة)',
         N'IBM Z mainframe DR plan; decommissioned 2024-Q4 after migration to AWS RDS Aurora. Kept for audit trail.',
         N'خطة التعافي لحاسوب IBM Z الرئيسي؛ تم إيقافه الربع الرابع 2024 بعد الترحيل إلى AWS RDS Aurora. محفوظ لأثر المراجعة.',
         N'BCP-MAINFRAME-01', N'IBM Z mainframe (decommissioned)', N'Former Platform Lead',
         24, 4, 168,
         N'COLD', N'Off-site tape vault (decommissioned)', N'Iron Mountain',
         N'(Archived.) Cold-recovery from off-site tapes; 24-hour RTO; required vendor engineer dispatch.',
         N'(مؤرشف.) تعافٍ بارد من الأشرطة خارج الموقع؛ RTO 24 ساعة؛ يتطلب إرسال مهندس مورد.',
         N'[]', N'[]', N'[]',
         N'Off-site tapes daily, retained 7 years. All tapes destroyed 2025-Q1 per data-destruction certificate.',
         N'أشرطة خارج الموقع يومياً، احتفاظ 7 سنوات. تم إتلاف جميع الأشرطة في الربع الأول 2025 وفق شهادة إتلاف البيانات.',
         DATEADD(YEAR, -2, SYSUTCDATETIME()), N'PASSED',
         N'Last successful test 2024-Q4 before decommissioning. Archived after final tape destruction.',
         NULL,
         N'ARCHIVED', DATEADD(YEAR, -3, SYSUTCDATETIME()), 1, N'former CIO',
         NULL, NULL, NULL,
         DATEADD(YEAR, -1, SYSUTCDATETIME()),
         N'Mainframe decommissioned 2024-Q4; replaced by AWS RDS Aurora (covered by DRP-DEMO-07).',
         NULL,
         N'DRP-DEMO-06'),

        -- 7. APPROVED — AWS RDS Aurora (replaces mainframe)
        (1, N'DRP-DEMO-07',
         N'AWS RDS Aurora — financial DB DR plan',
         N'خطة التعافي - قاعدة بيانات AWS RDS Aurora المالية',
         N'Aurora Global Database with Riyadh writer + Dammam reader, automatic failover under 90 seconds.',
         N'Aurora Global Database مع كاتب الرياض وقارئ الدمام، تحويل تلقائي في أقل من 90 ثانية.',
         N'BCP-FINANCE-02', N'AWS RDS Aurora (financial ledger)', N'Database Lead',
         1, 0, 4,
         N'ACTIVE_ACTIVE', N'AWS Dammam region (me-central-1)', N'AWS',
         N'Aurora Global Database with synchronous replication to in-region replicas + asynchronous cross-region. Promotion in <1 minute via Route 53 health check.',
         N'Aurora Global Database مع نسخ متزامن إلى نسخ داخل المنطقة + غير متزامن عبر المناطق. الترقية في أقل من دقيقة عبر فحص صحة Route 53.',
         N'[{"system":"IAM","type":"AUTH","criticality":"CRITICAL"},{"system":"VPC peering","type":"NETWORK","criticality":"CRITICAL"},{"system":"S3 archive","type":"STORAGE","criticality":"HIGH"}]',
         N'[{"seq":1,"actionEn":"Route 53 health-check detects writer outage","actionAr":"فحص صحة Route 53 يكتشف انقطاع الكاتب","owner":"Automated","minutesAllocated":1,"automated":true},{"seq":2,"actionEn":"Promote Dammam reader to writer","actionAr":"ترقية قارئ الدمام إلى كاتب","owner":"Automated","minutesAllocated":1,"automated":true},{"seq":3,"actionEn":"Application reconnect via new endpoint","actionAr":"إعادة اتصال التطبيق عبر النقطة الجديدة","owner":"Automated","minutesAllocated":2,"automated":true},{"seq":4,"actionEn":"SRE confirms data integrity","actionAr":"SRE يؤكد سلامة البيانات","owner":"SRE Lead","minutesAllocated":15,"automated":false}]',
         N'[{"seq":1,"actionEn":"Restore Riyadh writer + replication","actionAr":"استعادة كاتب الرياض والنسخ","owner":"Database Lead","minutesAllocated":60,"automated":false},{"seq":2,"actionEn":"Reverse failover Riyadh → Dammam","actionAr":"عكس التحويل من الرياض إلى الدمام","owner":"Database Lead","minutesAllocated":60,"automated":false}]',
         N'Continuous backup to S3 (35-day window); point-in-time recovery; quarterly full snapshot to Glacier with 7-year retention.',
         N'نسخ احتياطي مستمر إلى S3 (نافذة 35 يوماً)؛ استعادة لحظة زمنية محددة؛ لقطة كاملة ربع سنوية إلى Glacier مع احتفاظ 7 سنوات.',
         DATEADD(MONTH, -2, SYSUTCDATETIME()), N'PASSED',
         N'Quarterly failover drill 2026-03. Total failover 47s, well under 1h RTO. No data loss.',
         DATEADD(MONTH, 1, CAST(SYSUTCDATETIME() AS DATE)),
         N'APPROVED', DATEADD(MONTH, -10, SYSUTCDATETIME()), 1, N'COO',
         NULL, NULL, NULL, NULL, NULL, NULL,
         N'DRP-DEMO-07'),

        -- 8. REJECTED — Branch office VPN
        (1, N'DRP-DEMO-08',
         N'Branch office VPN — DR plan (rejected)',
         N'خطة التعافي للشبكة الافتراضية لفرع المكتب (مرفوضة)',
         N'Initial draft for Jeddah branch VPN DR; rejected for being too thin on data-protection controls.',
         N'مسودة أولية لتعافي شبكة جدة الافتراضية؛ تم رفضها لكونها ضعيفة في ضوابط حماية البيانات.',
         N'BCP-NETWORK-02', N'Jeddah branch VPN', N'Network Lead',
         8, 8, 48,
         N'COLD', N'(unspecified)', NULL,
         N'(Rejected.) Cold standby with single ISP fallback.',
         N'(مرفوض.) استعداد بارد مع بديل احتياطي مزود إنترنت واحد.',
         N'[]', N'[]', N'[]',
         N'TBD',
         N'لاحقاً',
         NULL, NULL, NULL, NULL,
         N'REJECTED', NULL, NULL, NULL,
         NULL, NULL, NULL,
         NULL, NULL,
         N'Plan does not address PDPL data-protection requirements for branch-local sensitive data caches. Resubmit with: (1) encryption-at-rest specification, (2) explicit zero-trust device requirements, (3) PDPL data-residency check.',
         N'DRP-DEMO-08');
END
GO

PRINT N'V41 — bcm.dr_plans: 8 demo rows across 5 site types + 5 statuses applied.';
GO
