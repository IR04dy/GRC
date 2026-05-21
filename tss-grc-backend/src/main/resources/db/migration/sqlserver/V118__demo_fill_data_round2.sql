-- V118: round-2 demo fill — Saudi-context records on top of V117.
--
-- Adds:
--   scm.vendors                    +8 Saudi-anchored suppliers
--   privacy.processing_activities  +6 RoPA entries (3 cross-border, 1 special-cat)
--   privacy.dpia                   +3 DPIA records
--   privacy_lc.dsr_cases           +6 cases (1 SLA-breached, 1 extended)
--   risk.kri_readings              +30 monthly readings on existing KRIs
--
-- All inserts guarded with NOT EXISTS so the migration is idempotent.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ══════════════════════════════════════════════════════════════════
-- (1) Suppliers — Saudi-anchored
-- ══════════════════════════════════════════════════════════════════

DECLARE @org BIGINT = 1;

-- Idempotent at row level (per-row NOT EXISTS); no top-level count gate
-- so previously soft-deleted demo data doesn't suppress the insert.
;WITH new_vendors AS (
    SELECT * FROM (VALUES
        -- category must be one of: OTHER, CONSULTING, UTILITIES, FINANCIAL,
        -- MANUFACTURING, LOGISTICS, IT_SERVICES (CK_scm_vendor_category)
        (N'STC Business',            N'إس تي سي بزنس',         N'IT_SERVICES', N'HIGH',     N'SA', N'Telecom + cloud connectivity', N'HIGH',   N'ACTIVE',    N'stc.com.sa',          N'Ahmed Al-Qahtani',   N'partners@stc.com.sa'),
        (N'Mobily Business',         N'موبايلي بزنس',           N'IT_SERVICES', N'MEDIUM',   N'SA', N'Network + managed SD-WAN',     N'MEDIUM', N'ACTIVE',    N'business.mobily.com.sa', N'Sara Al-Sulaiman',N'enterprise@mobily.com.sa'),
        (N'Elm Information Security',N'علم لأمن المعلومات',    N'IT_SERVICES', N'CRITICAL', N'SA', N'NCA-aligned MSSP + SOC 24/7',  N'HIGH',   N'ACTIVE',    N'elm.sa',              N'Mohammed Al-Otaibi', N'sales@elm.sa'),
        (N'STC Cloud',               N'إس تي سي كلاود',         N'IT_SERVICES', N'CRITICAL', N'SA', N'Hyperscale IaaS in-country',   N'HIGH',   N'ACTIVE',    N'cloud.stc.com.sa',    N'Hadi Al-Ghamdi',     N'cloud@stc.com.sa'),
        (N'Saudi Aramco IT Services',N'خدمات تقنية أرامكو',    N'CONSULTING',  N'MEDIUM',   N'SA', N'OT cybersecurity consulting',  N'MEDIUM', N'ACTIVE',    N'aramco.com',          N'Khalid Al-Mutairi',  N'partners@aramco.com'),
        (N'Microsoft Arabia',        N'مايكروسوفت العربية',     N'IT_SERVICES', N'HIGH',     N'SA', N'Productivity + Azure tenant',  N'MEDIUM', N'ACTIVE',    N'microsoft.com',       N'Reem Al-Dosari',     N'enterprise@microsoft.com'),
        (N'Oracle Saudi Arabia',     N'أوراكل السعودية',        N'IT_SERVICES', N'MEDIUM',   N'SA', N'Database + ERP',               N'MEDIUM', N'ACTIVE',    N'oracle.com',          N'Bader Al-Anazi',     N'gcc@oracle.com'),
        (N'Lean Business Services',  N'لين لخدمات الأعمال',     N'CONSULTING',  N'LOW',      N'SA', N'Customer support outsourcing', N'LOW',    N'ACTIVE',    N'lean.sa',             N'Lama Al-Rashid',     N'sales@lean.sa')
    ) AS v (name_en, name_ar, category, criticality, country, service_type, risk_level, status, website, contact_name, contact_email)
)
INSERT INTO scm.vendors
    (organisation_id, name_en, name_ar, category, criticality, country, service_type,
     risk_level, status, website, contact_name, contact_email, registration_number,
     onboarding_status, description, created_at, is_deleted)
SELECT @org, v.name_en, v.name_ar, v.category, v.criticality, v.country, v.service_type,
       v.risk_level, v.status, v.website, v.contact_name, v.contact_email,
       CONCAT('CR-', RIGHT('00000' + CAST(ABS(CHECKSUM(v.name_en)) % 99999 AS VARCHAR(6)), 7)),
       N'COMPLETED',
       CONCAT(N'Saudi-anchored supplier — ', v.service_type, N'.'),
       SYSUTCDATETIME(), 0
  FROM new_vendors v
 WHERE NOT EXISTS (SELECT 1 FROM scm.vendors x
                    WHERE x.organisation_id = @org
                      AND x.name_en        = v.name_en
                      AND x.is_deleted     = 0);
GO

-- ══════════════════════════════════════════════════════════════════
-- (2) Processing activities — Saudi-context RoPA
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_ra BIGINT = 1;

IF (SELECT COUNT(*) FROM privacy.processing_activities WHERE organisation_id = @org_ra) < 11
BEGIN
    ;WITH new_ra AS (
        SELECT * FROM (VALUES
            (N'Customer onboarding (Absher integration)', N'إدراج العملاء عبر أبشر',  N'KYC via Absher API + NAFATH verification', N'LEGAL_OBLIGATION',  84, 0, N'ACTIVE',  N'KSA',           NULL,        NULL,           NULL,         0, NULL),
            (N'Payroll processing (HR-cloud)',             N'معالجة الرواتب',          N'Monthly payroll, GOSI + ZATCA filings',     N'LEGAL_OBLIGATION',  120, 0, N'ACTIVE', N'KSA',           NULL,        NULL,           NULL,         0, NULL),
            (N'Marketing analytics export',                N'تصدير تحليلات التسويق',    N'Aggregated user-behaviour to Adobe Analytics in EU', N'LEGITIMATE_INTEREST', 36, 1, N'ACTIVE', N'Ireland',  N'ADEQUATE', N'SCC',         N'EU-IRL-2025-014', 0, NULL),
            (N'Vendor support tickets (Salesforce US)',    N'تذاكر دعم الموردين',     N'Support tickets stored in Salesforce US org', N'CONTRACT',        24, 1, N'ACTIVE', N'United States', N'NONE',     N'DATA_OFFICE_APPROVAL', N'SDO-2026-XB-0014', 0, NULL),
            (N'Health & wellness programme records',       N'سجلات برنامج الصحة',     N'Voluntary employee wellness with biometric stats', N'CONSENT',           36, 0, N'ACTIVE', N'KSA',           NULL,        NULL,           NULL,         1, N'["HEALTH","BIOMETRIC"]'),
            (N'Anti-fraud ML model training',              N'تدريب نموذج كشف الاحتيال', N'Transactional data used to retrain ML model in Azure West-EU', N'LEGITIMATE_INTEREST', 60, 1, N'ACTIVE', N'Netherlands', N'PARTIAL', N'BCR',          N'SDO-2026-XB-0021', 0, NULL)
        ) AS r (activity_name_en, activity_name_ar, purpose_en, legal_basis, retention_period_months,
               cross_border_transfer, status, receiving_country, adequacy_decision, transfer_mechanism,
               data_office_reference, special_category_flag, data_categories_json)
    )
    INSERT INTO privacy.processing_activities
        (organisation_id, activity_name_en, activity_name_ar, purpose_en, legal_basis,
         retention_period_months, cross_border_transfer, status, receiving_country,
         adequacy_decision, transfer_mechanism, data_office_reference,
         special_category_flag, data_categories_json, is_deleted, created_at)
    SELECT @org_ra, r.activity_name_en, r.activity_name_ar, r.purpose_en, r.legal_basis,
           r.retention_period_months, r.cross_border_transfer, r.status, r.receiving_country,
           r.adequacy_decision, r.transfer_mechanism, r.data_office_reference,
           r.special_category_flag, r.data_categories_json, 0, SYSUTCDATETIME()
      FROM new_ra r
     WHERE NOT EXISTS (SELECT 1 FROM privacy.processing_activities x
                        WHERE x.organisation_id    = @org_ra
                          AND x.activity_name_en   = r.activity_name_en);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (3) DPIA — three more records
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_d BIGINT = 1;

IF (SELECT COUNT(*) FROM privacy.dpia WHERE organisation_id = @org_d) < 5
BEGIN
    INSERT INTO privacy.dpia
        (organisation_id, title_en, title_ar, trigger_reason, status,
         overall_residual_risk, assessment_date, next_review_date, conclusion,
         is_deleted, created_at)
    SELECT @org_d, t.title_en, t.title_ar, t.trigger_reason, t.status,
           t.residual, t.assess_date, t.next_review, t.conclusion, 0, SYSUTCDATETIME()
      FROM (VALUES
        (N'Employee biometric attendance system',         N'نظام الحضور البيومتري للموظفين',         N'SPECIAL_CATEGORY',      N'APPROVED',     N'MEDIUM',   DATEADD(DAY, -90, GETUTCDATE()), DATEADD(DAY, 275, GETUTCDATE()), N'Acceptable with consent + data-minimisation controls applied.'),
        (N'Anti-fraud ML model on transaction data',      N'نموذج الذكاء الاصطناعي لكشف الاحتيال',  N'AUTOMATED_DECISION',    N'UNDER_REVIEW', N'HIGH',     DATEADD(DAY, -25, GETUTCDATE()), DATEADD(DAY, 340, GETUTCDATE()), NULL),
        (N'Customer satisfaction survey with sentiment AI', N'استبيان رضى العملاء',                  N'NEW_PROCESSING',       N'DRAFT',        N'LOW',      DATEADD(DAY, -5,  GETUTCDATE()), DATEADD(DAY, 360, GETUTCDATE()), NULL)
      ) AS t (title_en, title_ar, trigger_reason, status, residual, assess_date, next_review, conclusion)
     WHERE NOT EXISTS (SELECT 1 FROM privacy.dpia x
                        WHERE x.organisation_id = @org_d
                          AND x.title_en        = t.title_en);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (4) DSR cases — including one SLA-breached + one extended
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_dsr BIGINT = 1;

IF (SELECT COUNT(*) FROM privacy_lc.dsr_cases WHERE organisation_id = @org_dsr) < 18
BEGIN
    INSERT INTO privacy_lc.dsr_cases
        (external_id, organisation_id, type, subject_name, national_id_last4,
         contact_email, status, filed_at, identity_verified_at, verification_method,
         sla_deadline, extended, ropa_ids, is_deleted)
    SELECT t.ext, @org_dsr, t.type, t.subject, t.id_last4, t.email, t.status,
           t.filed_at, t.verified_at, t.method, t.sla_deadline, t.extended, t.ropa_ids, 0
      FROM (VALUES
        ('DSR-2026-0301', N'ACCESS',          N'Faisal Al-Qarni',     '8821', 'faisal.q@email.sa',  N'COLLECTING',    DATEADD(DAY, -10, GETUTCDATE()), DATEADD(DAY, -8, GETUTCDATE()), N'NAFATH',       CAST(DATEADD(DAY, 20, GETUTCDATE()) AS DATE),   CAST(0 AS BIT), N'RPA-001,RPA-014'),
        ('DSR-2026-0302', N'ERASURE',         N'Aisha Al-Shahrani',   '4109', 'aisha.s@email.sa',   N'REDACTING',     DATEADD(DAY, -22, GETUTCDATE()), DATEADD(DAY, -20,GETUTCDATE()), N'ID_DOCUMENT',  CAST(DATEADD(DAY,  8, GETUTCDATE()) AS DATE),   CAST(0 AS BIT), N'RPA-005,RPA-014,RPA-027'),
        ('DSR-2026-0303', N'RECTIFICATION',   N'Mohammed Al-Zahrani', '3340', 'mz@email.sa',        N'DELIVERED',     DATEADD(DAY, -45, GETUTCDATE()), DATEADD(DAY, -42,GETUTCDATE()), N'NAFATH',       CAST(DATEADD(DAY,-15, GETUTCDATE()) AS DATE),   CAST(0 AS BIT), N'RPA-002'),
        -- Overdue: filed 40 days ago, still COLLECTING → SLA-breached
        ('DSR-2026-0304', N'ACCESS',          N'Hessa Al-Anazi',      '7755', 'hessa.a@email.sa',   N'COLLECTING',    DATEADD(DAY, -40, GETUTCDATE()), DATEADD(DAY, -38,GETUTCDATE()), N'NAFATH',       CAST(DATEADD(DAY,-10, GETUTCDATE()) AS DATE),   CAST(0 AS BIT), N'RPA-027'),
        -- Extended: under-extension flow, deadline pushed
        ('DSR-2026-0305', N'PORTABILITY',     N'Salem Al-Mutairi',    '5612', 'salem.m@email.sa',   N'EXTENDED',      DATEADD(DAY, -35, GETUTCDATE()), DATEADD(DAY, -32,GETUTCDATE()), N'VIDEO_KYC',    CAST(DATEADD(DAY, 25, GETUTCDATE()) AS DATE),   CAST(1 AS BIT), N'RPA-001,RPA-002,RPA-014'),
        ('DSR-2026-0306', N'WITHDRAW_CONSENT',N'Norah Al-Saedi',      '2271', 'norah.s@email.sa',   N'DELIVERED',     DATEADD(DAY, -18, GETUTCDATE()), DATEADD(DAY, -16,GETUTCDATE()), N'NAFATH',       CAST(DATEADD(DAY, 12, GETUTCDATE()) AS DATE),   CAST(0 AS BIT), N'RPA-005')
      ) AS t (ext, type, subject, id_last4, email, status, filed_at, verified_at, method, sla_deadline, extended, ropa_ids)
     WHERE NOT EXISTS (SELECT 1 FROM privacy_lc.dsr_cases x
                        WHERE x.external_id = t.ext);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (5) Stamp the SLA-breached row so dashboards pick it up
-- ══════════════════════════════════════════════════════════════════

UPDATE privacy_lc.dsr_cases
   SET sla_breached_at    = DATEADD(DAY, -10, GETUTCDATE()),
       sla_breach_notified = 1
 WHERE external_id = 'DSR-2026-0304'
   AND sla_breached_at IS NULL;
GO

-- ══════════════════════════════════════════════════════════════════
-- (6) KRI readings — top up to a multi-month series for the top 3 KRIs
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_kri BIGINT = 1;

IF (SELECT COUNT(*) FROM risk.kri_readings rk
     JOIN risk.kri_definitions kd ON kd.id = rk.kri_id
     WHERE kd.organisation_id = @org_kri
       AND rk.reading_at >= DATEADD(MONTH, -6, GETUTCDATE())) < 30
BEGIN
    DECLARE @kri1 BIGINT, @kri2 BIGINT, @kri3 BIGINT;
    SELECT TOP 1 @kri1 = id FROM risk.kri_definitions
      WHERE organisation_id = @org_kri AND is_deleted = 0 ORDER BY id ASC;
    SELECT TOP 1 @kri2 = id FROM risk.kri_definitions
      WHERE organisation_id = @org_kri AND is_deleted = 0 AND id > @kri1 ORDER BY id ASC;
    SELECT TOP 1 @kri3 = id FROM risk.kri_definitions
      WHERE organisation_id = @org_kri AND is_deleted = 0 AND id > @kri2 ORDER BY id ASC;

    -- 6 monthly readings × 3 KRIs = 18 rows.
    ;WITH months (m) AS (
        SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
        UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
    ),
    series AS (
        SELECT k.kri_id, m.m
          FROM (SELECT @kri1 AS kri_id UNION ALL SELECT @kri2 UNION ALL SELECT @kri3) k
          CROSS JOIN months m
         WHERE k.kri_id IS NOT NULL
    )
    INSERT INTO risk.kri_readings (kri_id, reading_value, reading_at, band, notes)
    SELECT s.kri_id,
           -- Sinusoidal-ish synthetic curve so the chart isn't a flat line
           CAST(50 + 18 * SIN(s.m * 0.9) + (s.m * 1.1) AS DECIMAL(18,2)),
           DATEADD(MONTH, -s.m, GETUTCDATE()),
           CASE WHEN s.m <= 2 THEN 'GREEN'
                WHEN s.m <= 4 THEN 'AMBER'
                ELSE 'RED'
           END,
           CONCAT('Auto-seeded reading for month -', s.m)
      FROM series s
     WHERE NOT EXISTS (
        SELECT 1 FROM risk.kri_readings r
         WHERE r.kri_id = s.kri_id
           AND CAST(r.reading_at AS DATE) = CAST(DATEADD(MONTH, -s.m, GETUTCDATE()) AS DATE)
     );
END
GO

PRINT 'V118 round-2 demo fill applied: vendors + RoPA + DPIA + DSR + KRI readings.';
GO
