-- V199 — Sprint 13: Customer Complaints Management.
--
-- SAMA Banking Consumer Protection Principles (2021) Principle 6
-- ("Complaint Handling") + CMA Investor Complaints Regulation
-- require every Saudi bank / investment firm to operate a structured
-- complaints channel with:
--   - 5 working-day acknowledgement SLA
--   - 20 working-day resolution SLA
--   - Quarterly aggregate reporting to SAMA
--   - SAMA escalation path with reference tracking
--   - Customer-facing tracking number (no PII exposure to anonymous lookups)
--
-- This migration creates the `consumer` schema and three tables:
--
--   consumer.complaint                  — one row per complaint
--   consumer.complaint_update           — timeline of state changes / notes
--   consumer.complaint_aggregate_report — quarterly SAMA-facing aggregates
--
-- Lifecycle state machine (enforced in ComplaintService):
--   RECEIVED             → ACKNOWLEDGED | REJECTED
--   ACKNOWLEDGED         → UNDER_INVESTIGATION | RESOLVED | REJECTED
--   UNDER_INVESTIGATION  → AWAITING_CUSTOMER | RESOLVED | ESCALATED_TO_SAMA
--   AWAITING_CUSTOMER    → UNDER_INVESTIGATION | RESOLVED
--   RESOLVED             → CLOSED | REOPENED
--   CLOSED               → REOPENED
--   REOPENED             → UNDER_INVESTIGATION
--   ESCALATED_TO_SAMA    → CLOSED | RESOLVED
--   REJECTED             (terminal)
--
-- 13 category codes (Saudi-bank customer-complaint taxonomy):
--   ACCOUNT_ACCESS, CARDS_DISPUTES, ATM_ISSUES, TRANSFER_DELAYS,
--   FRAUD_UNAUTHORIZED_TXN, FEES_CHARGES, LOAN_FINANCING, INSURANCE,
--   INVESTMENT_PRODUCTS, DIGITAL_BANKING, BRANCH_SERVICE,
--   MISLEADING_INFORMATION, OTHER.
--
-- Pre-seed for org=1 (TSS Bank) — 12 realistic Saudi bank complaints
-- spanning fraud, transfers, cards, ATM, fees, loan, digital banking,
-- misleading information; multiple statuses across the lifecycle.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ─── consumer schema ──────────────────────────────────────────────
IF SCHEMA_ID('consumer') IS NULL EXEC('CREATE SCHEMA consumer');
GO

-- ─── consumer.complaint ───────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'complaint' AND schema_id = SCHEMA_ID('consumer'))
BEGIN
    CREATE TABLE consumer.complaint (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        complaint_no                NVARCHAR(32)        NOT NULL,        -- CMP-YYYY-NNNNNN
        submission_token            UNIQUEIDENTIFIER    NOT NULL DEFAULT NEWID(),

        -- WEB | EMAIL | PHONE | BRANCH | SAMA_REFERRAL | SOCIAL_MEDIA
        channel                     NVARCHAR(32)        NOT NULL,

        -- 13 codes — see header
        category                    NVARCHAR(64)        NOT NULL,
        subcategory                 NVARCHAR(64)        NULL,

        -- LOW | MEDIUM | HIGH | CRITICAL
        severity                    NVARCHAR(16)        NOT NULL DEFAULT 'MEDIUM',

        -- See lifecycle state machine in header.
        status                      NVARCHAR(32)        NOT NULL DEFAULT 'RECEIVED',

        customer_name_en            NVARCHAR(256)       NULL,
        customer_name_ar            NVARCHAR(256)       NULL,

        -- NATIONAL_ID | IQAMA | PASSPORT | CR
        customer_id_type            NVARCHAR(16)        NULL,
        customer_id_last4           NVARCHAR(4)         NULL,

        customer_phone              NVARCHAR(32)        NULL,
        customer_email              NVARCHAR(256)       NULL,
        customer_city               NVARCHAR(64)        NULL,

        product_code                NVARCHAR(64)        NULL,
        account_last4               NVARCHAR(4)         NULL,

        description_en              NVARCHAR(MAX)       NULL,
        description_ar              NVARCHAR(MAX)       NULL,

        submitted_on                DATETIME2           NOT NULL DEFAULT SYSUTCDATETIME(),
        acknowledged_on             DATETIME2           NULL,
        acknowledged_by             NVARCHAR(128)       NULL,
        ack_sla_due_on              DATETIME2           NULL,

        resolved_on                 DATETIME2           NULL,
        resolution_summary_en       NVARCHAR(MAX)       NULL,
        resolution_summary_ar       NVARCHAR(MAX)       NULL,
        resolved_by                 NVARCHAR(128)       NULL,
        resolution_sla_due_on       DATETIME2           NULL,

        escalated_to_sama_on        DATETIME2           NULL,
        sama_reference_no           NVARCHAR(64)        NULL,

        assigned_to                 NVARCHAR(128)       NULL,
        root_cause_code             NVARCHAR(64)        NULL,
        compensation_amount_sar     DECIMAL(18,2)       NOT NULL DEFAULT 0,
        lessons_learned             NVARCHAR(MAX)       NULL,

        org_id                      BIGINT              NOT NULL,

        -- BaseEntity audit cols
        created_at                  DATETIME2           NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2           NULL,
        created_by                  BIGINT              NULL,
        updated_by                  BIGINT              NULL,
        is_deleted                  BIT                 NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2           NULL,
        deleted_by                  BIGINT              NULL,
        row_version                 ROWVERSION,

        CONSTRAINT UX_complaint_complaint_no UNIQUE (complaint_no),
        CONSTRAINT UX_complaint_submission_token UNIQUE (submission_token)
    );

    CREATE INDEX IX_complaint_org_status
        ON consumer.complaint(org_id, status)
        WHERE is_deleted = 0;

    CREATE INDEX IX_complaint_org_submitted_on
        ON consumer.complaint(org_id, submitted_on DESC)
        WHERE is_deleted = 0;

    CREATE INDEX IX_complaint_org_category
        ON consumer.complaint(org_id, category)
        WHERE is_deleted = 0;

    CREATE INDEX IX_complaint_org_severity
        ON consumer.complaint(org_id, severity)
        WHERE is_deleted = 0;
END
GO

-- ─── consumer.complaint_update ────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'complaint_update' AND schema_id = SCHEMA_ID('consumer'))
BEGIN
    CREATE TABLE consumer.complaint_update (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        complaint_id                BIGINT              NOT NULL,

        -- STATUS_CHANGE | CUSTOMER_CONTACT | INTERNAL_NOTE |
        -- EVIDENCE_REQUEST | SAMA_NOTIFICATION
        update_type                 NVARCHAR(32)        NOT NULL,

        from_status                 NVARCHAR(32)        NULL,
        to_status                   NVARCHAR(32)        NULL,

        note_en                     NVARCHAR(MAX)       NULL,
        note_ar                     NVARCHAR(MAX)       NULL,

        actor                       NVARCHAR(128)       NULL,
        customer_visible            BIT                 NOT NULL DEFAULT 0,

        occurred_on                 DATETIME2           NOT NULL DEFAULT SYSUTCDATETIME(),

        -- BaseEntity audit cols
        created_at                  DATETIME2           NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2           NULL,
        created_by                  BIGINT              NULL,
        updated_by                  BIGINT              NULL,
        is_deleted                  BIT                 NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2           NULL,
        deleted_by                  BIGINT              NULL,
        row_version                 ROWVERSION,

        CONSTRAINT FK_complaint_update_complaint
            FOREIGN KEY (complaint_id) REFERENCES consumer.complaint(id)
    );

    CREATE INDEX IX_complaint_update_complaint
        ON consumer.complaint_update(complaint_id, occurred_on ASC)
        WHERE is_deleted = 0;
END
GO

-- ─── consumer.complaint_aggregate_report ──────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'complaint_aggregate_report' AND schema_id = SCHEMA_ID('consumer'))
BEGIN
    CREATE TABLE consumer.complaint_aggregate_report (
        id                          BIGINT IDENTITY(1,1) PRIMARY KEY,
        report_period               NVARCHAR(16)        NOT NULL,        -- e.g. Q4-2025
        total_received              INT                 NOT NULL DEFAULT 0,
        total_resolved              INT                 NOT NULL DEFAULT 0,
        total_escalated_sama        INT                 NOT NULL DEFAULT 0,
        avg_resolution_days         DECIMAL(10,2)       NOT NULL DEFAULT 0,
        ack_sla_breach_pct          DECIMAL(5,2)        NOT NULL DEFAULT 0,
        resolution_sla_breach_pct   DECIMAL(5,2)        NOT NULL DEFAULT 0,
        top_category                NVARCHAR(64)        NULL,
        top_category_count          INT                 NOT NULL DEFAULT 0,
        compensation_total_sar      DECIMAL(18,2)       NOT NULL DEFAULT 0,

        generated_on                DATETIME2           NOT NULL DEFAULT SYSUTCDATETIME(),
        generated_by                NVARCHAR(128)       NULL,

        org_id                      BIGINT              NOT NULL,

        -- BaseEntity audit cols
        created_at                  DATETIME2           NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at                  DATETIME2           NULL,
        created_by                  BIGINT              NULL,
        updated_by                  BIGINT              NULL,
        is_deleted                  BIT                 NOT NULL DEFAULT 0,
        deleted_at                  DATETIME2           NULL,
        deleted_by                  BIGINT              NULL,
        row_version                 ROWVERSION
    );

    CREATE UNIQUE INDEX UX_complaint_agg_org_period
        ON consumer.complaint_aggregate_report(org_id, report_period)
        WHERE is_deleted = 0;
END
GO

-- ─── Pre-seed for org=1 — 12 realistic Saudi bank complaints ───────
-- Idempotent: every INSERT guards on complaint_no.

-- (1) CMP-2025-000001 — FRAUD: card used in Turkey, under investigation, HIGH
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000001')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by, assigned_to,
         org_id)
    VALUES
        ('CMP-2025-000001', 'WEB', 'FRAUD_UNAUTHORIZED_TXN', 'HIGH', 'UNDER_INVESTIGATION',
         'Abdullah bin Saad Al-Maliki', N'عبدالله بن سعد المالكي',
         'NATIONAL_ID', '4823', '+966555012834', 'a.malki@example.sa', N'الرياض',
         'My credit card was used in Turkey without my knowledge for 4,200 SAR. I was in Riyadh at the time and have evidence from my employer.',
         N'تم استخدام بطاقتي الائتمانية في تركيا دون علمي بمبلغ 4,200 ر.س. كنت في الرياض في ذلك الوقت ولدي إثبات من جهة عملي.',
         DATEADD(day, -7, SYSUTCDATETIME()), DATEADD(day, 0, SYSUTCDATETIME()), DATEADD(day, 21, SYSUTCDATETIME()),
         DATEADD(day, -6, SYSUTCDATETIME()), 'cpo.officer', 'fraud.investigator.1',
         1);
END
GO

-- (2) CMP-2025-000002 — FRAUD: mada card online purchases, RESOLVED + compensation
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000002')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by, assigned_to,
         resolved_on, resolved_by,
         resolution_summary_en, resolution_summary_ar,
         compensation_amount_sar, root_cause_code,
         org_id)
    VALUES
        ('CMP-2025-000002', 'PHONE', 'FRAUD_UNAUTHORIZED_TXN', 'MEDIUM', 'RESOLVED',
         'Reem bint Abdulaziz Al-Sharif', N'ريم بنت عبدالعزيز الشريف',
         'NATIONAL_ID', '6712', '+966554998211', 'reem.sharif@example.sa', N'جدّة',
         'Unauthorized online purchases on my mada card totalling 890 SAR. I was notified by SMS but did not perform the transactions.',
         N'عمليات شراء عبر الإنترنت غير مصرّح بها على بطاقة مدى بقيمة 890 ر.س. وصلتني رسائل نصية لكنني لم أقم بأي من المعاملات.',
         DATEADD(day, -30, SYSUTCDATETIME()), DATEADD(day, -23, SYSUTCDATETIME()), DATEADD(day, -2, SYSUTCDATETIME()),
         DATEADD(day, -29, SYSUTCDATETIME()), 'cpo.officer', 'fraud.investigator.2',
         DATEADD(day, -5, SYSUTCDATETIME()), 'fraud.investigator.2',
         'Chargeback raised under mada operating rules; merchant refunded full amount; new card issued with chip+PIN binding.',
         N'تمّ تقديم طلب استرداد وفق قواعد تشغيل مدى؛ ردّ التاجر كامل المبلغ؛ تمّ إصدار بطاقة جديدة مرتبطة بالشريحة والرقم السرّي.',
         890.00, 'MERCHANT_FRAUD_3DS_BYPASS',
         1);
END
GO

-- (3) CMP-2025-000003 — FRAUD: Philippines ATM withdrawal, ESCALATED_TO_SAMA, CRITICAL
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000003')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by, assigned_to,
         escalated_to_sama_on, sama_reference_no,
         org_id)
    VALUES
        ('CMP-2025-000003', 'BRANCH', 'FRAUD_UNAUTHORIZED_TXN', 'CRITICAL', 'ESCALATED_TO_SAMA',
         'Majid bin Fahd Al-Otaibi', N'ماجد بن فهد العتيبي',
         'NATIONAL_ID', '1093', '+966551117823', 'majid.otaibi@example.sa', N'الدمّام',
         'Cash withdrawal of 18,500 SAR from an ATM in the Philippines while my card was with me in Dammam. Bank initial response was inadequate; I am escalating.',
         N'سحب نقدي بقيمة 18,500 ر.س من صرّاف آلي في الفلبين رغم أنّ البطاقة كانت بحوزتي في الدمّام. الردّ الأوّلي للبنك غير كافٍ، وأطلب رفع الشكوى.',
         DATEADD(day, -45, SYSUTCDATETIME()), DATEADD(day, -38, SYSUTCDATETIME()), DATEADD(day, -17, SYSUTCDATETIME()),
         DATEADD(day, -44, SYSUTCDATETIME()), 'cpo.officer', 'fraud.investigator.1',
         DATEADD(day, -15, SYSUTCDATETIME()), 'SAMA-CC-2025-0847',
         1);
END
GO

-- (4) CMP-2025-000004 — TRANSFER: SARIE Hajj transfer delayed, RESOLVED
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000004')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by, assigned_to,
         resolved_on, resolved_by,
         resolution_summary_en, resolution_summary_ar,
         root_cause_code,
         org_id)
    VALUES
        ('CMP-2025-000004', 'WEB', 'TRANSFER_DELAYS', 'MEDIUM', 'RESOLVED',
         'Nouf bint Mohammed Al-Harbi', N'نوف بنت محمد الحربي',
         'NATIONAL_ID', '2204', '+966503884412', 'nouf.harbi@example.sa', N'مكّة المكرّمة',
         'SARIE express transfer to Bank Albilad for Hajj expenses was delayed 3 days. Funds needed urgently before Arafah day.',
         N'تأخّر تحويل سريع SARIE إلى بنك البلاد لمصاريف الحجّ ثلاثة أيام. الأموال كانت مطلوبة بشكل عاجل قبل يوم عرفة.',
         DATEADD(day, -22, SYSUTCDATETIME()), DATEADD(day, -15, SYSUTCDATETIME()), DATEADD(day, 6, SYSUTCDATETIME()),
         DATEADD(day, -21, SYSUTCDATETIME()), 'cpo.officer', 'payments.investigator.1',
         DATEADD(day, -10, SYSUTCDATETIME()), 'payments.investigator.1',
         'Root cause traced to AML screening false-positive hold; control rule recalibrated to reduce false-positive rate for Hajj-period transfers. Customer thanked.',
         N'تمّ تتبّع السبب الجذري إلى توقف فحص مكافحة غسل الأموال بإيجابية كاذبة؛ تمّت إعادة معايرة قاعدة الضابط لتقليل معدّل الإيجابيات الكاذبة لتحويلات موسم الحجّ. تمّ شكر العميل.',
         'AML_FALSE_POSITIVE_HOLD',
         1);
END
GO

-- (5) CMP-2025-000005 — TRANSFER: International transfer to Egypt stuck, ACKNOWLEDGED
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000005')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by,
         org_id)
    VALUES
        ('CMP-2025-000005', 'EMAIL', 'TRANSFER_DELAYS', 'MEDIUM', 'ACKNOWLEDGED',
         'Ahmed Mahmoud Hassan', N'أحمد محمود حسن',
         'IQAMA', '8456', '+966508811223', 'a.hassan@example.sa', N'الرياض',
         'International transfer to Egypt has been pending for a week. Beneficiary has not received the funds and my account was already debited.',
         N'تحويل دولي إلى مصر معلّق منذ أسبوع. المستفيد لم يستلم الأموال وقد تمّ خصم مبلغ التحويل من حسابي.',
         DATEADD(day, -8, SYSUTCDATETIME()), DATEADD(day, -1, SYSUTCDATETIME()), DATEADD(day, 20, SYSUTCDATETIME()),
         DATEADD(day, -7, SYSUTCDATETIME()), 'cpo.officer',
         1);
END
GO

-- (6) CMP-2025-000006 — CARDS: Amazon SA chargeback dispute, UNDER_INVESTIGATION
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000006')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by, assigned_to,
         org_id)
    VALUES
        ('CMP-2025-000006', 'WEB', 'CARDS_DISPUTES', 'MEDIUM', 'UNDER_INVESTIGATION',
         'Salem bin Khaled Al-Ghamdi', N'سالم بن خالد الغامدي',
         'NATIONAL_ID', '5587', '+966555221199', 'salem.ghamdi@example.sa', N'الرياض',
         'Chargeback dispute on an Amazon Saudi Arabia purchase — item never delivered, merchant refusing refund.',
         N'نزاع ردّ رسوم على عملية شراء من موقع أمازون السعودية — لم تصلني السلعة والتاجر يرفض الاسترداد.',
         DATEADD(day, -15, SYSUTCDATETIME()), DATEADD(day, -8, SYSUTCDATETIME()), DATEADD(day, 13, SYSUTCDATETIME()),
         DATEADD(day, -14, SYSUTCDATETIME()), 'cpo.officer', 'cards.investigator.1',
         1);
END
GO

-- (7) CMP-2025-000007 — CARDS: duplicate charge in Kuwait, RESOLVED with compensation
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000007')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by, assigned_to,
         resolved_on, resolved_by,
         resolution_summary_en, resolution_summary_ar,
         compensation_amount_sar, root_cause_code,
         org_id)
    VALUES
        ('CMP-2025-000007', 'PHONE', 'CARDS_DISPUTES', 'MEDIUM', 'RESOLVED',
         'Hessa bint Sultan Al-Qahtani', N'حصّة بنت سلطان القحطاني',
         'NATIONAL_ID', '3398', '+966507745588', 'hessa.qahtani@example.sa', N'الخبر',
         'Duplicate debit for the same transaction at Kuwait airport lounge — 1,250 SAR charged twice.',
         N'خصم مكرّر لنفس العملية في صالة الكويت بمبلغ 1,250 ر.س.',
         DATEADD(day, -55, SYSUTCDATETIME()), DATEADD(day, -48, SYSUTCDATETIME()), DATEADD(day, -27, SYSUTCDATETIME()),
         DATEADD(day, -54, SYSUTCDATETIME()), 'cpo.officer', 'cards.investigator.2',
         DATEADD(day, -35, SYSUTCDATETIME()), 'cards.investigator.2',
         'Confirmed duplicate authorisation by acquirer; full reversal credited to account.',
         N'تأكّد التفويض المكرّر لدى مكتسب الخدمة؛ تمّ ردّ كامل المبلغ إلى الحساب.',
         1250.00, 'ACQUIRER_DUPLICATE_AUTH',
         1);
END
GO

-- (8) CMP-2025-000008 — ATM: King Khalid Airport ATM short-disburse, RESOLVED
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000008')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by, assigned_to,
         resolved_on, resolved_by,
         resolution_summary_en, resolution_summary_ar,
         compensation_amount_sar, root_cause_code,
         org_id)
    VALUES
        ('CMP-2025-000008', 'BRANCH', 'ATM_ISSUES', 'MEDIUM', 'RESOLVED',
         'Yousef bin Saleh Al-Dossari', N'يوسف بن صالح الدّوسري',
         'NATIONAL_ID', '7140', '+966550021456', 'y.dossari@example.sa', N'الرياض',
         'ATM at King Khalid International Airport did not dispense the full amount — 600 SAR short of requested withdrawal.',
         N'الصرّاف الآلي في مطار الملك خالد لم يصرف كامل المبلغ — نقص 600 ر.س من قيمة السحب.',
         DATEADD(day, -38, SYSUTCDATETIME()), DATEADD(day, -31, SYSUTCDATETIME()), DATEADD(day, -10, SYSUTCDATETIME()),
         DATEADD(day, -37, SYSUTCDATETIME()), 'cpo.officer', 'channels.investigator.1',
         DATEADD(day, -28, SYSUTCDATETIME()), 'channels.investigator.1',
         'Cash-balance reconciliation confirmed dispenser short-disburse; amount refunded with branch-manager apology call.',
         N'مطابقة رصيد النقد أكّدت نقص صرف الجهاز؛ تمّ ردّ المبلغ مع مكالمة اعتذار من مدير الفرع.',
         600.00, 'ATM_CASH_DISPENSER_FAULT',
         1);
END
GO

-- (9) CMP-2025-000009 — FEES: undisclosed FX transfer fee, RECEIVED (fresh)
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000009')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         org_id)
    VALUES
        ('CMP-2025-000009', 'WEB', 'FEES_CHARGES', 'LOW', 'RECEIVED',
         'Lama bint Faisal Al-Rashid', N'لمى بنت فيصل الرّاشد',
         'NATIONAL_ID', '8821', '+966509998877', 'lama.rashid@example.sa', N'الرياض',
         'Undisclosed FX transfer fee charged on outgoing international transfer — not shown in the pre-confirmation summary on the app.',
         N'رسوم تحويل خارجي غير مفصح عنها مسبقاً — لم تظهر في ملخّص ما قبل التأكيد على التطبيق.',
         DATEADD(day, -2, SYSUTCDATETIME()), DATEADD(day, 5, SYSUTCDATETIME()), DATEADD(day, 26, SYSUTCDATETIME()),
         1);
END
GO

-- (10) CMP-2025-000010 — LOAN: Tawarruq early-settlement fee dispute, UNDER_INVESTIGATION
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000010')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by, assigned_to,
         org_id)
    VALUES
        ('CMP-2025-000010', 'BRANCH', 'LOAN_FINANCING', 'HIGH', 'UNDER_INVESTIGATION',
         'Khalid bin Abdullah Al-Mutairi', N'خالد بن عبدالله المطيري',
         'NATIONAL_ID', '6649', '+966552113366', 'k.mutairi@example.sa', N'بريدة',
         'Dispute on early-settlement fee for a Tawarruq facility — fee not disclosed in the original contract I signed.',
         N'نزاع على رسوم سداد مبكر لعقد تورّق — لم تُذكر هذه الرسوم في العقد الأصلي الذي وقّعتُه.',
         DATEADD(day, -18, SYSUTCDATETIME()), DATEADD(day, -11, SYSUTCDATETIME()), DATEADD(day, 10, SYSUTCDATETIME()),
         DATEADD(day, -17, SYSUTCDATETIME()), 'cpo.officer', 'sharia.compliance.1',
         1);
END
GO

-- (11) CMP-2025-000011 — DIGITAL: mada Pay activation failure, RESOLVED
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000011')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by, assigned_to,
         resolved_on, resolved_by,
         resolution_summary_en, resolution_summary_ar,
         root_cause_code,
         org_id)
    VALUES
        ('CMP-2025-000011', 'PHONE', 'DIGITAL_BANKING', 'LOW', 'RESOLVED',
         'Bader bin Ali Al-Subaie', N'بدر بن علي السبيعي',
         'NATIONAL_ID', '4471', '+966507712399', 'b.subaie@example.sa', N'الطّائف',
         'mada Pay activation kept failing on my iPhone for 5 days — secure element provisioning loop.',
         N'فشل تفعيل خدمة مدى Pay على جهازي iPhone لمدة 5 أيام — عملية تجهيز العنصر الآمن تدور دون اكتمال.',
         DATEADD(day, -25, SYSUTCDATETIME()), DATEADD(day, -18, SYSUTCDATETIME()), DATEADD(day, 3, SYSUTCDATETIME()),
         DATEADD(day, -24, SYSUTCDATETIME()), 'cpo.officer', 'digital.support.1',
         DATEADD(day, -19, SYSUTCDATETIME()), 'digital.support.1',
         'Token-service-provider provisioning corrected; activation succeeded after token reset. Customer guided on app.',
         N'تمّ تصحيح تجهيز مزوّد خدمة الرموز؛ نجح التفعيل بعد إعادة تعيين الرمز. تمّ توجيه العميل عبر التطبيق.',
         'TSP_PROVISIONING_LOOP',
         1);
END
GO

-- (12) CMP-2025-000012 — MISLEADING_INFORMATION: branch claim of no-annual-fee card, AWAITING_CUSTOMER
IF NOT EXISTS (SELECT 1 FROM consumer.complaint WHERE complaint_no = 'CMP-2025-000012')
BEGIN
    INSERT INTO consumer.complaint
        (complaint_no, channel, category, severity, status,
         customer_name_en, customer_name_ar,
         customer_id_type, customer_id_last4, customer_phone, customer_email, customer_city,
         description_en, description_ar,
         submitted_on, ack_sla_due_on, resolution_sla_due_on,
         acknowledged_on, acknowledged_by, assigned_to,
         org_id)
    VALUES
        ('CMP-2025-000012', 'BRANCH', 'MISLEADING_INFORMATION', 'MEDIUM', 'AWAITING_CUSTOMER',
         'Ghada bint Nasser Al-Anazi', N'غادة بنت ناصر العنزي',
         'NATIONAL_ID', '2956', '+966551884422', 'g.anazi@example.sa', N'حائل',
         'Branch staff stated the credit card had no annual fee, then 250 SAR was debited as the annual fee. Awaiting customer to provide branch visit date and staff name.',
         N'موظف الفرع أفاد أن البطاقة بدون رسوم سنوية، ثمّ تمّ خصم 250 ر.س كرسم سنوي. بانتظار تزويد العميل بتاريخ زيارة الفرع واسم الموظف.',
         DATEADD(day, -12, SYSUTCDATETIME()), DATEADD(day, -5, SYSUTCDATETIME()), DATEADD(day, 16, SYSUTCDATETIME()),
         DATEADD(day, -11, SYSUTCDATETIME()), 'cpo.officer', 'branch.compliance.1',
         1);
END
GO

-- ─── Pre-seed quarterly aggregate report for Q4-2025 ───────────────
IF NOT EXISTS (SELECT 1 FROM consumer.complaint_aggregate_report
               WHERE org_id = 1 AND report_period = 'Q4-2025')
BEGIN
    INSERT INTO consumer.complaint_aggregate_report
        (report_period, total_received, total_resolved, total_escalated_sama,
         avg_resolution_days, ack_sla_breach_pct, resolution_sla_breach_pct,
         top_category, top_category_count, compensation_total_sar,
         generated_on, generated_by, org_id)
    VALUES
        ('Q4-2025', 12, 5, 1,
         18.40, 0.00, 8.33,
         'FRAUD_UNAUTHORIZED_TXN', 3, 2740.00,
         SYSUTCDATETIME(), 'system.scheduler', 1);
END
GO
