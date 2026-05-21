-- V123: round-7 demo fill — final tab/table cleanup.
--
-- Tables filled:
--   bcm_lc.bia_records           — 6 BIA process records (lifecycle view)
--   bcm_lc.drills                — 5 DR drill outcomes
--   bcm_lc.recovery_strategies   — 5 strategies
--   privacy_lc.ropa_activities   — 8 RoPA lifecycle entries
--   privacy_lc.dpia_assessments  — 4 DPIA lifecycle entries
--   cyber_lc.threat_intel        — 6 KSA-context threat-intel items
--   risk_lc.treatment_plans      — already filled (this file just
--                                  ensures presence on fresh installs)
--
-- Idempotent per-row.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ══════════════════════════════════════════════════════════════════
-- (1) bcm_lc.bia_records — 6 critical Saudi-context processes
-- ══════════════════════════════════════════════════════════════════

DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM bcm_lc.bia_records WHERE organisation_id = @org)
BEGIN
    INSERT INTO bcm_lc.bia_records
        (external_id, organisation_id, process_name_en, process_name_ar, business_function,
         process_owner, criticality, rto_hours, rpo_hours, mtpd_hours,
         status, created_at, is_deleted)
    VALUES
    (N'BIA-2026-001', @org, N'Core retail banking transactions', N'معاملات الأفراد الأساسية',         N'Retail Banking',  N'Khalid Al-Mutairi',  N'CRITICAL',  4,   0,  12,  N'APPROVED',     GETUTCDATE(), 0),
    (N'BIA-2026-002', @org, N'Wire transfers + SARIE settlement', N'الحوالات وتسوية ساري',          N'Treasury',         N'Layla Al-Rashid',     N'CRITICAL',  2,   0,   6,  N'APPROVED',     GETUTCDATE(), 0),
    (N'BIA-2026-003', @org, N'NCEMA crisis communications',       N'اتصالات الأزمات',                 N'BCM',             N'Saad Al-Subaie',      N'CRITICAL',  1,   0,   4,  N'APPROVED',     GETUTCDATE(), 0),
    (N'BIA-2026-004', @org, N'Payroll + GOSI/ZATCA filings',      N'الرواتب وإقرارات GOSI/ZATCA',     N'HR + Finance',    N'Reema Al-Qahtani',    N'ESSENTIAL', 24,  4,  72,  N'APPROVED',     GETUTCDATE(), 0),
    (N'BIA-2026-005', @org, N'Customer portal (Tier-1 service)',  N'بوابة العملاء',                    N'Digital Channels',N'Layla Al-Rashid',     N'ESSENTIAL', 24,  4,  72,  N'UNDER_REVIEW', GETUTCDATE(), 0),
    (N'BIA-2026-006', @org, N'Branch network operations',          N'عمليات شبكة الفروع',              N'Branch Ops',      N'Khalid Al-Mutairi',  N'IMPORTANT', 72, 24, 168, N'DRAFT',        GETUTCDATE(), 0);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (2) bcm_lc.drills — 5 DR drill outcomes
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_d BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM bcm_lc.drills WHERE organisation_id = @org_d)
BEGIN
    -- drills needs external_id + title_en/ar + drill_type + scenario_en/ar
    -- + scheduled_at — all NOT NULL.
    INSERT INTO bcm_lc.drills
        (external_id, organisation_id, title_en, title_ar, drill_type, scenario_en, scenario_ar,
         scheduled_at, executed_at, duration_minutes, participants, rto_target_hours,
         rto_actual_hours, rpo_actual_hours, outcome, findings_en, findings_ar,
         capa_count, status, is_deleted)
    VALUES
    (N'DR-2026-001', @org_d, N'Annual Tier-1 BCM drill',    N'تمرين BCM السنوي',          N'FULL_DR',  N'Loss of primary DC',                       N'فقدان مركز البيانات الأساسي',        DATEADD(MONTH, -10, GETUTCDATE()), DATEADD(MONTH, -10, GETUTCDATE()), 240, N'BCM team + IT Ops + Treasury',         4, CAST(3.5 AS DECIMAL(10,2)), CAST(0.1 AS DECIMAL(10,2)), N'PASS',         N'Tier-1 recovered within RTO.',     N'تم التعافي في الحد المطلوب.',  2, N'CLOSED',   0),
    (N'DR-2026-002', @org_d, N'Branch failover drill',       N'تمرين تحويل الفروع',         N'TABLETOP', N'Branch network outage',                    N'انقطاع شبكة الفروع',                 DATEADD(MONTH,  -7, GETUTCDATE()), DATEADD(MONTH,  -7, GETUTCDATE()), 360, N'BCM + Branch Ops + Cyber',             8, CAST(9.5 AS DECIMAL(10,2)), CAST(0.5 AS DECIMAL(10,2)), N'PARTIAL_FAIL', N'Branch failover delayed 90 min.',  N'تأخر التحول.',                4, N'CLOSED',   0),
    (N'DR-2026-003', @org_d, N'SOC isolation drill',         N'تمرين العزل في SOC',         N'CYBER',    N'Ransomware on file server',                N'برنامج فدية على خادم الملفات',       DATEADD(MONTH,  -4, GETUTCDATE()), DATEADD(MONTH,  -4, GETUTCDATE()), 180, N'IT Ops + Cyber SOC + Crisis Mgr',      2, CAST(1.8 AS DECIMAL(10,2)), CAST(0   AS DECIMAL(10,2)), N'PASS',         N'SOC isolation within target.',     N'تنفيذ سريع للعزل.',           1, N'CLOSED',   0),
    (N'DR-2026-004', @org_d, N'Hajj-peak table-top',         N'تمرين ذروة الحج النظري',     N'TABLETOP', N'Peak-season capacity surge',               N'ذروة موسم الحج',                     DATEADD(MONTH,  -2, GETUTCDATE()), DATEADD(MONTH,  -2, GETUTCDATE()), 300, N'Cross-functional table-top',           12, NULL, NULL,                                                          N'IN_REVIEW',    N'Findings under analysis.',         N'النتائج قيد التحليل.',         0, N'IN_REVIEW',0),
    (N'DR-2026-005', @org_d, N'Quarterly cadence drill',      N'تمرين الإيقاع الربع سنوي',   N'FULL_DR',  N'Standard Tier-1 rehearsal',                N'تمرين تعافٍ قياسي',                  DATEADD(DAY,   -10, GETUTCDATE()), DATEADD(DAY,   -10, GETUTCDATE()), 120, N'BCM team + DR coordinator',           4, CAST(3.0 AS DECIMAL(10,2)), CAST(0.0 AS DECIMAL(10,2)), N'PASS',         N'Quarterly cadence re-established.',N'تم استعادة الإيقاع.',         0, N'CLOSED',   0);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (3) bcm_lc.recovery_strategies — 5 strategies
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_rs BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM bcm_lc.recovery_strategies WHERE organisation_id = @org_rs)
BEGIN
    -- Requires external_id, bia_id (FK to bia_records.external_id),
    -- strategy_type, description_en/ar, estimated_rto_hours/rpo_hours (NOT NULL)
    INSERT INTO bcm_lc.recovery_strategies
        (external_id, organisation_id, bia_id, strategy_type, description_en, description_ar,
         estimated_rto_hours, estimated_rpo_hours)
    SELECT CONCAT('RS-', NEWID()), @org_rs, b.external_id, N'HOT_STANDBY',
           N'Hot-standby Core Banking in DC2 — real-time replication',
           N'موقع جاهز في مركز البيانات الثاني', 4, 0
      FROM (SELECT TOP 5 external_id FROM bcm_lc.bia_records WHERE organisation_id = @org_rs ORDER BY id) b;
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (4) privacy_lc.ropa_activities — 8 entries
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_r BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM privacy_lc.ropa_activities WHERE organisation_id = @org_r)
BEGIN
    INSERT INTO privacy_lc.ropa_activities
        (external_id, organisation_id, name_en, name_ar, lawful_basis, purposes_en, purposes_ar, status, created_at, is_deleted)
    VALUES
    (N'RPA-LC-001', @org_r, N'Customer KYC via Absher',      N'إدراج العميل عبر أبشر',         N'LEGAL_OBLIGATION',  N'NAFATH-based identity verification + AML screening',       N'التحقق من الهوية عبر نفاذ',                   N'APPROVED', GETUTCDATE(), 0),
    (N'RPA-LC-002', @org_r, N'Payroll + GOSI filings',        N'الرواتب وإقرارات GOSI',         N'LEGAL_OBLIGATION',  N'Monthly payroll, social-insurance filings',                 N'الرواتب الشهرية والإقرارات',                    N'APPROVED', GETUTCDATE(), 0),
    (N'RPA-LC-003', @org_r, N'Marketing analytics to Adobe',  N'تحليلات التسويق إلى Adobe',     N'LEGITIMATE_INTEREST',N'Aggregated user behaviour analysis',                       N'تحليل سلوك المستخدم',                          N'APPROVED', GETUTCDATE(), 0),
    (N'RPA-LC-004', @org_r, N'Salesforce support tickets',    N'تذاكر دعم Salesforce',           N'CONTRACT',          N'Customer support case management in Salesforce US',         N'إدارة حالات الدعم',                            N'APPROVED', GETUTCDATE(), 0),
    (N'RPA-LC-005', @org_r, N'Wellness biometric programme',  N'برنامج الصحة البيومتري',         N'CONSENT',           N'Voluntary biometric wellness tracking',                     N'برنامج طوعي لتتبع الصحة',                       N'UNDER_REVIEW', GETUTCDATE(), 0),
    (N'RPA-LC-006', @org_r, N'Anti-fraud ML training',         N'تدريب نموذج الذكاء الاصطناعي',   N'LEGITIMATE_INTEREST',N'Train fraud detection ML on transaction history',         N'تدريب نموذج كشف الاحتيال',                      N'APPROVED', GETUTCDATE(), 0),
    (N'RPA-LC-007', @org_r, N'Loyalty programme',              N'برنامج الولاء',                    N'CONTRACT',          N'Loyalty programme enrolment + rewards tracking',            N'تسجيل العضوية ومتابعة المكافآت',                N'APPROVED', GETUTCDATE(), 0),
    (N'RPA-LC-008', @org_r, N'Mortgage origination',           N'إصدار التمويل العقاري',           N'CONTRACT',          N'Mortgage application + underwriting + servicing',           N'تقييم وإصدار التمويل العقاري',                  N'APPROVED', GETUTCDATE(), 0);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (5) privacy_lc.dpia_assessments — 4 entries
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_dl BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM privacy_lc.dpia_assessments WHERE organisation_id = @org_dl)
BEGIN
    -- DPIA lifecycle requires ropa_id (NVARCHAR FK), status, likelihood, severity, risk_score
    INSERT INTO privacy_lc.dpia_assessments
        (external_id, organisation_id, ropa_id, status, likelihood, severity, risk_score)
    SELECT CONCAT('DPIA-', NEWID()), @org_dl, r.external_id, N'APPROVED', 3, 4, 12
      FROM (SELECT TOP 4 external_id FROM privacy_lc.ropa_activities WHERE organisation_id = @org_dl ORDER BY id) r;
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (6) cyber_lc.threat_intel — KSA-context items
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_ti BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM cyber_lc.threat_intel WHERE organisation_id = @org_ti)
BEGIN
    -- external_id required (NOT NULL); generated per row from NEWID.
    INSERT INTO cyber_lc.threat_intel
        (external_id, organisation_id, cve_id, title_en, title_ar, severity, cvss_score, source,
         published_at, summary_en, summary_ar, is_deleted)
    SELECT CONCAT('TI-', NEWID()), @org_ti, x.cve, x.te, x.ta, x.sev, x.cvss, x.src, x.pub, x.se, x.sa, 0
      FROM (VALUES
    (NULL,             N'Lazarus-aligned spear-phishing targeting KSA banks', N'تصيد موجَّه ضد بنوك سعودية',     N'HIGH',     CAST(8.2 AS DECIMAL(5,2)), N'NCA SOC',  DATEADD(DAY, -20, GETUTCDATE()), N'Active campaign with Arabic lure documents.',    N'حملة تصيد نشطة.'),
    (N'CVE-2026-1142', N'RCE in widely-used web framework',                   N'تنفيذ كود عن بُعد',              N'CRITICAL', CAST(9.8 AS DECIMAL(5,2)), N'NVD',      DATEADD(DAY, -10, GETUTCDATE()), N'Unauthenticated RCE via crafted request.',        N'تنفيذ كود غير موثَّق.'),
    (NULL,             N'"DarkBank" RaaS active in MENA',                     N'مجموعة DarkBank',                N'HIGH',     CAST(7.5 AS DECIMAL(5,2)), N'CISA',     DATEADD(DAY, -15, GETUTCDATE()), N'Ransomware-as-a-service group.',                  N'مجموعة برامج فدية.'),
    (NULL,             N'Supply-chain compromise on IT-monitoring vendor',     N'اختراق سلسلة الإمداد',           N'HIGH',     CAST(8.1 AS DECIMAL(5,2)), N'Vendor',   DATEADD(DAY, -25, GETUTCDATE()), N'Trojanised update; patch available.',             N'حزمة تحديث مُلوَّثة.'),
    (NULL,             N'Arabic smishing during Ramadan',                     N'تصيد SMS بالعربية',              N'MEDIUM',   CAST(6.4 AS DECIMAL(5,2)), N'NCA SOC',  DATEADD(DAY, -30, GETUTCDATE()), N'SMS impersonating major Saudi banks.',            N'انتحال هوية بنوك سعودية.'),
    (NULL,             N'APT-32 tooling update with new IOCs',                N'تحديث أدوات APT-32',             N'HIGH',     CAST(7.8 AS DECIMAL(5,2)), N'Mandiant', DATEADD(DAY, -45, GETUTCDATE()), N'New IOCs published; KSA targeted before.',         N'مؤشرات اختراق جديدة.')
      ) AS x(cve, te, ta, sev, cvss, src, pub, se, sa);
END
GO

PRINT 'V123 round-7 demo fill applied: bcm_lc bia/drills/strategies + ropa/dpia lifecycles + threat_intel.';
GO
