-- V122: round-6 demo fill — schema-corrected fixes for V121 misses.
--
-- V121 hit schema mismatches on 7 tables. This migration picks them up
-- with the actual column names + required NOT NULL fields:
--
--   privacy_lc.cross_border_transfers  — needs external_id (NVARCHAR NOT NULL)
--                                        and ropa_id (NVARCHAR not BIGINT)
--   privacy_lc.ropa_activities         — uses different column names
--   privacy_lc.dpia_assessments        — uses different column names
--   cyber_lc.cyber_incidents           — needs external_id, severity,
--                                        classification, detected_at, status
--   cyber_lc.threat_intel              — schema TBD; TRY block keeps it safe
--   risk_lc.kri_breaches               — needs external_id (NVARCHAR), risk_id
--   risk_lc.activity_log               — uses entity_type / entity_id / ts
--                                        instead of risk_id / actor / occurred_at
--   nca_ecc.filings                    — uses fiscal_year, framework_version
--   board_pack.packs                   — needs drafted_by (NOT NULL)

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ══════════════════════════════════════════════════════════════════
-- (1) cross_border_transfers — external_id + ropa_id as NVARCHAR
-- ══════════════════════════════════════════════════════════════════

DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM privacy_lc.cross_border_transfers WHERE organisation_id = @org)
BEGIN
    INSERT INTO privacy_lc.cross_border_transfers
        (external_id, organisation_id, ropa_id, destination_country, purpose_en, purpose_ar,
         adequacy, safeguard, safeguard_date, pre_risk_score, post_risk_score,
         review_interval_months, status, created_at, is_deleted)
    VALUES
    (N'CBT-2026-001', @org, N'RPA-001', N'Ireland',        N'EU analytics processing',           N'معالجة تحليلات في الاتحاد الأوروبي', N'ADEQUATE', N'SCC',                  DATEADD(MONTH, -6, GETUTCDATE()),  14, 6,  12, N'APPROVED',    GETUTCDATE(), 0),
    (N'CBT-2026-002', @org, N'RPA-002', N'United States',  N'Salesforce support tickets',         N'تذاكر دعم في الولايات المتحدة',     N'NONE',     N'DATA_OFFICE_APPROVAL', DATEADD(MONTH, -3, GETUTCDATE()),  20, 10, 6,  N'APPROVED',    GETUTCDATE(), 0),
    (N'CBT-2026-003', @org, N'RPA-006', N'Netherlands',    N'ML model training in Azure West-EU', N'تدريب نموذج التعلم الآلي',           N'PARTIAL',  N'BCR',                  DATEADD(MONTH, -4, GETUTCDATE()),  16, 8,  12, N'APPROVED',    GETUTCDATE(), 0),
    (N'CBT-2026-004', @org, N'RPA-014', N'India',          N'24/7 helpdesk processing',           N'مكتب مساعدة على مدار الساعة',        N'NONE',     N'EXPLICIT_CONSENT',     DATEADD(MONTH, -2, GETUTCDATE()),  18, 12, 6,  N'UNDER_REVIEW',GETUTCDATE(), 0),
    (N'CBT-2026-005', @org, N'RPA-027', N'UAE',            N'GCC payments clearing house',        N'مقاصة مدفوعات الخليج',               N'ADEQUATE', N'INTRA_GROUP',          DATEADD(MONTH, -8, GETUTCDATE()),  10, 4,  24, N'APPROVED',    GETUTCDATE(), 0),
    (N'CBT-2026-006', @org, N'RPA-005', N'United Kingdom', N'UK insurance underwriting partner',  N'شريك التأمين في المملكة المتحدة',    N'ADEQUATE', N'SCC',                  DATEADD(MONTH, -5, GETUTCDATE()),  12, 5,  12, N'APPROVED',    GETUTCDATE(), 0);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (2) cyber_lc.cyber_incidents — full required fields
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_ci BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM cyber_lc.cyber_incidents WHERE organisation_id = @org_ci)
BEGIN
    INSERT INTO cyber_lc.cyber_incidents
        (external_id, organisation_id, title_en, title_ar, severity, classification,
         detected_at, status, is_deleted)
    VALUES
    (N'INC-2026-001', @org_ci, N'Phishing campaign — finance team',         N'حملة تصيد ضد فريق المالية',           N'HIGH',     N'PHISHING',     DATEADD(DAY, -45, GETUTCDATE()), N'CLOSED',         0),
    (N'INC-2026-002', @org_ci, N'EDR detection — workstation 47',           N'اكتشاف EDR في محطة العمل ٤٧',         N'MEDIUM',   N'MALWARE',      DATEADD(DAY, -30, GETUTCDATE()), N'CLOSED',         0),
    (N'INC-2026-003', @org_ci, N'Stolen credentials — VPN portal',          N'سرقة بيانات اعتماد VPN',              N'HIGH',     N'UNAUTHORIZED_ACCESS', DATEADD(DAY, -20, GETUTCDATE()), N'CONTAINED', 0),
    (N'INC-2026-004', @org_ci, N'Cloud misconfig — S3 bucket exposure',     N'تكوين سحابي خاطئ',                    N'CRITICAL', N'DATA_EXPOSURE',DATEADD(DAY, -12, GETUTCDATE()), N'CONTAINED',     0),
    (N'INC-2026-005', @org_ci, N'Supplier breach — third-party SaaS',       N'انتهاك مورد سحابي',                   N'MEDIUM',   N'SUPPLY_CHAIN', DATEADD(DAY,  -5, GETUTCDATE()), N'INVESTIGATING', 0);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (3) risk_lc.kri_breaches — required external_id + risk_id
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_kb BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM risk_lc.kri_breaches WHERE organisation_id = @org_kb)
BEGIN
    -- direction is NVARCHAR(10) — use 'GT'/'LT', not 'GREATER_THAN'.
    -- current_owner is NOT NULL.
    INSERT INTO risk_lc.kri_breaches
        (external_id, organisation_id, risk_id, kri_code, kri_name_en, kri_name_ar,
         threshold_value, actual_value, direction, detected_at, status,
         current_owner, is_deleted)
    SELECT
        CONCAT('KRB-', NEWID()),
        @org_kb, r.id,
        CONCAT('KRI-', RIGHT('000' + CAST(r.id AS VARCHAR(4)), 3)),
        CONCAT(N'Auto-KRI for risk ', r.id),
        CONCAT(N'مؤشر مخاطر للخطر ', r.id),
        CAST(80.00 AS DECIMAL(10,2)),
        CAST(92.00 + (r.id % 5) * 2 AS DECIMAL(10,2)),
        N'GT',
        DATEADD(DAY, -(5 + r.id), GETUTCDATE()),
        CASE (r.id % 4)
          WHEN 0 THEN N'OPEN'
          WHEN 1 THEN N'ACKNOWLEDGED'
          WHEN 2 THEN N'IN_TREATMENT'
          ELSE        N'RESOLVED'
        END,
        N'Khalid Al-Mutairi',
        0
      FROM (SELECT TOP 6 id FROM risk.risks WHERE organisation_id = @org_kb AND is_deleted = 0 ORDER BY id) r;
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (4) risk_lc.activity_log — uses entity_type / entity_id / ts
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_al BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM risk_lc.activity_log WHERE organisation_id = @org_al)
BEGIN
    -- user_id NOT NULL — soft FK to iam.users.id (admin user = 1)
    INSERT INTO risk_lc.activity_log
        (organisation_id, entity_type, entity_id, action, from_status, to_status,
         note_en, note_ar, user_id, ts)
    SELECT
        @org_al, N'Risk', r.id,
        CASE ((r.id + n) % 6)
          WHEN 0 THEN N'CREATED'
          WHEN 1 THEN N'ASSESSED'
          WHEN 2 THEN N'TREATMENT_PLANNED'
          WHEN 3 THEN N'KRI_BREACH'
          WHEN 4 THEN N'OWNER_REASSIGNED'
          ELSE        N'STATUS_CHANGED'
        END,
        CASE (n % 3) WHEN 0 THEN N'OPEN'           WHEN 1 THEN N'UNDER_ASSESSMENT' ELSE N'TREATMENT_IN_PROGRESS' END,
        CASE (n % 3) WHEN 0 THEN N'UNDER_ASSESSMENT' WHEN 1 THEN N'TREATMENT_IN_PROGRESS' ELSE N'ACCEPTED' END,
        CONCAT(N'Lifecycle activity recorded for risk ', r.id),
        CONCAT(N'نشاط دورة حياة للخطر ', r.id),
        1,
        DATEADD(DAY, -(r.id * 3 + n), GETUTCDATE())
      FROM (SELECT TOP 5 id FROM risk.risks WHERE organisation_id = @org_al AND is_deleted = 0 ORDER BY id) r
      CROSS JOIN (VALUES (1),(2),(3),(4),(5)) v(n);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (5) nca_ecc.filings — uses fiscal_year + framework_version
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_f BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM nca_ecc.filings WHERE organisation_id = @org_f)
BEGIN
    -- drafted_by NOT NULL — supply a placeholder email
    INSERT INTO nca_ecc.filings
        (external_id, organisation_id, fiscal_year, framework_version, title_en, drafted_by)
    VALUES
    (N'NCA-ECC-2023', @org_f, 2023, N'ECC-1:2018', N'NCA ECC 2023 self-assessment',    N'cyber.manager@tss.com.sa'),
    (N'NCA-ECC-2024', @org_f, 2024, N'ECC-1:2018', N'NCA ECC 2024 self-assessment',    N'cyber.manager@tss.com.sa'),
    (N'NCA-ECC-2025', @org_f, 2025, N'ECC-2:2024', N'NCA ECC 2025 self-assessment',    N'cyber.manager@tss.com.sa'),
    (N'NCA-ECC-2026', @org_f, 2026, N'ECC-2:2024', N'NCA ECC 2026 self-assessment',    N'cyber.manager@tss.com.sa');
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (6) board_pack.packs — drafted_by required
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_bp BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM board_pack.packs WHERE organisation_id = @org_bp)
BEGIN
    INSERT INTO board_pack.packs
        (external_id, organisation_id, title_en, title_ar, period_label,
         period_start, period_end, audience,
         risk_summary_json, appetite_breaches_json, audit_engagements_json,
         incidents_json, pdpl_summary_json, ccm_summary_json, sla_summary_json,
         status, drafted_by, drafted_at)
    VALUES
    (CONCAT('BP-', NEWID()), @org_bp, N'Q1 2026 GRC Board Pack', N'حقيبة المجلس — الربع الأول ٢٠٢٦', N'2026-Q1',
     '2026-01-01', '2026-03-31', N'BOARD',
     '{"openHighRisks":12,"overdueCapa":4,"appetiteBreaches":3}',
     '{"breachesQ1":3}',
     '{"engagements":4}',
     '{"active":2,"closedThisQuarter":7}',
     '{"complianceScore":76}',
     '{"complianceScore":82}',
     '{"breachedSlas":2}',
     N'PUBLISHED', N'board.secretary@tss.com.sa', DATEADD(DAY, -40, GETUTCDATE())),

    (CONCAT('BP-', NEWID()), @org_bp, N'Q4 2025 GRC Board Pack', N'حقيبة المجلس — الربع الرابع ٢٠٢٥', N'2025-Q4',
     '2025-10-01', '2025-12-31', N'BOARD',
     '{"openHighRisks":15,"overdueCapa":6,"appetiteBreaches":4}',
     '{"breachesQ4":4}',
     '{"engagements":5}',
     '{"active":3,"closedThisQuarter":9}',
     '{"complianceScore":72}',
     '{"complianceScore":80}',
     '{"breachedSlas":3}',
     N'PUBLISHED', N'board.secretary@tss.com.sa', DATEADD(DAY, -135, GETUTCDATE())),

    (CONCAT('BP-', NEWID()), @org_bp, N'Q3 2025 GRC Exec Brief', N'إيجاز تنفيذي — الربع الثالث ٢٠٢٥', N'2025-Q3',
     '2025-07-01', '2025-09-30', N'EXEC',
     '{"openHighRisks":18,"overdueCapa":8,"appetiteBreaches":5}',
     '{"breachesQ3":5}',
     '{"engagements":3}',
     '{"active":4,"closedThisQuarter":11}',
     '{"complianceScore":68}',
     '{"complianceScore":77}',
     '{"breachedSlas":4}',
     N'APPROVED', N'board.secretary@tss.com.sa', DATEADD(DAY, -210, GETUTCDATE())),

    (CONCAT('BP-', NEWID()), @org_bp, N'2025 Annual Risk Committee Report', N'تقرير لجنة المخاطر السنوي ٢٠٢٥', N'2025',
     '2025-01-01', '2025-12-31', N'RISK_COMMITTEE',
     '{"openHighRisks":12,"overdueCapa":4,"appetiteBreaches":12}',
     '{"breaches2025":12}',
     '{"engagements":15}',
     '{"active":2,"closedThisYear":31}',
     '{"complianceScore":76}',
     '{"complianceScore":82}',
     '{"breachedSlas":7}',
     N'DRAFT', N'board.secretary@tss.com.sa', DATEADD(DAY, -10, GETUTCDATE()));
END
GO

PRINT 'V122 round-6 demo fill applied: cross_border + cyber incidents + kri breaches + activity log + nca filings + board packs.';
GO
