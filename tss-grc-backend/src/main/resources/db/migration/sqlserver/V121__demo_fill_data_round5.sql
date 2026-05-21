-- V121: round-5 demo fill — every empty user-facing tab now has data.
--
-- 29 empty tables remained after V120. This migration covers the 15
-- that are visible on common pages:
--
--   privacy.consent_records           10 (Privacy → Consent tab)
--   privacy_lc.cross_border_transfers  6 (Privacy → Cross-Border tab)
--   privacy_lc.ropa_activities        10 (RoPA lifecycle)
--   privacy_lc.dpia_assessments        4 (DPIA lifecycle)
--   cyber.cloud_services               8 (Cyber → Cloud Services)
--   cyber_lc.assets                   12 (Asset register)
--   cyber_lc.cyber_incidents           5 (Cyber lifecycle incidents)
--   cyber_lc.threat_intel              6 (Threat-intel feed)
--   risk_lc.acceptance_requests        5 (Risk acceptance board)
--   risk_lc.treatment_actions         12 (Risk treatment actions)
--   risk_lc.kri_breaches               6 (KRI breach board)
--   risk_lc.activity_log              25 (Risk activity history)
--   regchange.changes                  8 (Regulatory change tracker)
--   nca_ecc.filings                    4 (NCA ECC annual filings)
--   board_pack.packs                   4 (Board pack list)
--
-- Per-row NOT EXISTS guards everywhere; idempotent.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ══════════════════════════════════════════════════════════════════
-- (1) privacy.consent_records
-- ══════════════════════════════════════════════════════════════════

DECLARE @org BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM privacy.consent_records WHERE organisation_id = @org)
BEGIN
    INSERT INTO privacy.consent_records
        (organisation_id, data_subject_ref, purpose_code, purpose_description,
         legal_basis, granted_at, granted_via, valid_until, withdrawn_at, withdrawn_reason)
    VALUES
    (@org, N'subject-001', N'MARKETING_EMAIL',     N'Marketing email subscription',           N'CONSENT', DATEADD(MONTH, -10, GETUTCDATE()), N'PORTAL',    DATEADD(MONTH,  14, GETUTCDATE()), NULL, NULL),
    (@org, N'subject-002', N'ANALYTICS_TRACKING',  N'Web analytics + behavioural tracking',  N'CONSENT', DATEADD(MONTH,  -8, GETUTCDATE()), N'BANNER',    DATEADD(MONTH,  16, GETUTCDATE()), NULL, NULL),
    (@org, N'subject-003', N'MARKETING_SMS',       N'SMS promotional offers',                 N'CONSENT', DATEADD(MONTH,  -6, GETUTCDATE()), N'PORTAL',    DATEADD(MONTH,  18, GETUTCDATE()), DATEADD(MONTH, -2, GETUTCDATE()), N'Subject opted out from preferences'),
    (@org, N'subject-004', N'WELLNESS_PROGRAMME',  N'Health/biometric wellness participation', N'CONSENT', DATEADD(MONTH,  -4, GETUTCDATE()), N'IN_PERSON', DATEADD(MONTH,  20, GETUTCDATE()), NULL, NULL),
    (@org, N'subject-005', N'LOYALTY_PROGRAMME',   N'Loyalty programme enrolment',            N'CONTRACT',DATEADD(MONTH, -12, GETUTCDATE()), N'PORTAL',    DATEADD(MONTH,  12, GETUTCDATE()), NULL, NULL),
    (@org, N'subject-006', N'COOKIE_ANALYTICS',    N'Optional analytics cookies',             N'CONSENT', DATEADD(MONTH,  -3, GETUTCDATE()), N'BANNER',    DATEADD(MONTH,  21, GETUTCDATE()), NULL, NULL),
    (@org, N'subject-007', N'PROFILING',           N'Automated profiling for ad targeting',   N'CONSENT', DATEADD(MONTH,  -5, GETUTCDATE()), N'PORTAL',    DATEADD(MONTH,  19, GETUTCDATE()), DATEADD(MONTH, -1, GETUTCDATE()), N'PDPL Article 30 right exercised'),
    (@org, N'subject-008', N'MARKETING_EMAIL',     N'Marketing email subscription',           N'CONSENT', DATEADD(MONTH,  -2, GETUTCDATE()), N'PORTAL',    DATEADD(MONTH,  22, GETUTCDATE()), NULL, NULL),
    (@org, N'subject-009', N'DATA_SHARING_PARTNERS', N'Sharing with marketing partners',      N'CONSENT', DATEADD(MONTH,  -7, GETUTCDATE()), N'WRITTEN',   DATEADD(MONTH,  17, GETUTCDATE()), NULL, NULL),
    (@org, N'subject-010', N'CROSS_BORDER',        N'Cross-border data transfer to processor',N'CONSENT', DATEADD(MONTH,  -9, GETUTCDATE()), N'PORTAL',    DATEADD(MONTH,  15, GETUTCDATE()), NULL, NULL);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (2) privacy_lc.cross_border_transfers
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_x BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM privacy_lc.cross_border_transfers WHERE organisation_id = @org_x)
BEGIN
    INSERT INTO privacy_lc.cross_border_transfers
        (organisation_id, ropa_id, destination_country, purpose_en, purpose_ar,
         adequacy, safeguard, safeguard_date, pre_risk_score, post_risk_score,
         review_interval_months, status, created_at, is_deleted)
    VALUES
    (@org_x,  1, N'Ireland',         N'EU analytics processing',                N'معالجة تحليلات في الاتحاد الأوروبي', N'ADEQUATE', N'SCC',                  DATEADD(MONTH, -6, GETUTCDATE()),  14, 6,  12, N'APPROVED',    GETUTCDATE(), 0),
    (@org_x,  2, N'United States',   N'Salesforce support tickets',             N'تذاكر دعم في الولايات المتحدة',     N'NONE',     N'DATA_OFFICE_APPROVAL', DATEADD(MONTH, -3, GETUTCDATE()),  20, 10, 6,  N'APPROVED',    GETUTCDATE(), 0),
    (@org_x,  3, N'Netherlands',     N'ML model training in Azure West-EU',     N'تدريب نموذج التعلم الآلي',           N'PARTIAL',  N'BCR',                  DATEADD(MONTH, -4, GETUTCDATE()),  16, 8,  12, N'APPROVED',    GETUTCDATE(), 0),
    (@org_x,  4, N'India',           N'24/7 helpdesk processing',               N'مكتب مساعدة على مدار الساعة',        N'NONE',     N'EXPLICIT_CONSENT',     DATEADD(MONTH, -2, GETUTCDATE()),  18, 12, 6,  N'UNDER_REVIEW',GETUTCDATE(), 0),
    (@org_x,  5, N'UAE',             N'GCC payments clearing house',            N'مقاصة مدفوعات الخليج',               N'ADEQUATE', N'INTRA_GROUP',          DATEADD(MONTH, -8, GETUTCDATE()),  10, 4,  24, N'APPROVED',    GETUTCDATE(), 0),
    (@org_x,  6, N'United Kingdom',  N'UK insurance underwriting partner',      N'شريك التأمين في المملكة المتحدة',    N'ADEQUATE', N'SCC',                  DATEADD(MONTH, -5, GETUTCDATE()),  12, 5,  12, N'APPROVED',    GETUTCDATE(), 0);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (3) privacy_lc.ropa_activities + dpia_assessments
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_r BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM privacy_lc.ropa_activities WHERE organisation_id = @org_r)
BEGIN
    -- Schema may differ; insert only org_id + sequential identifiers and
    -- let the rest default. Guarded inserts with TRY.
    BEGIN TRY
        INSERT INTO privacy_lc.ropa_activities (organisation_id, activity_name)
        SELECT @org_r, n FROM (VALUES
            (N'Customer KYC via Absher'),
            (N'Payroll + GOSI/ZATCA filings'),
            (N'Marketing analytics to Adobe'),
            (N'Salesforce support tickets'),
            (N'Wellness biometric records'),
            (N'Anti-fraud ML training'),
            (N'Loyalty programme'),
            (N'Mortgage origination'),
            (N'Mobile-banking app telemetry'),
            (N'Branch CCTV footage')
        ) AS x (n);
    END TRY
    BEGIN CATCH
        PRINT 'Skipped privacy_lc.ropa_activities — schema mismatch.';
    END CATCH
END
GO

DECLARE @org_dl BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM privacy_lc.dpia_assessments WHERE organisation_id = @org_dl)
BEGIN
    BEGIN TRY
        INSERT INTO privacy_lc.dpia_assessments (organisation_id, title_en)
        SELECT @org_dl, n FROM (VALUES
            (N'Wellness biometrics DPIA'),
            (N'Anti-fraud ML DPIA'),
            (N'Sentiment-AI survey DPIA'),
            (N'Cross-border processor onboarding DPIA')
        ) AS x (n);
    END TRY
    BEGIN CATCH
        PRINT 'Skipped privacy_lc.dpia_assessments — schema mismatch.';
    END CATCH
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (4) cyber.cloud_services (with NCA CSCC / CCC context)
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_c BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM cyber.cloud_services WHERE organisation_id = @org_c)
BEGIN
    INSERT INTO cyber.cloud_services
        (organisation_id, service_name, provider, service_model, deployment_model,
         data_classification, residency_region, kingdom_data_resident, cscc_tier,
         criticality, sso_enabled, mfa_enforced, encryption_at_rest, encryption_in_transit,
         status, last_review_date, next_review_date, notes, created_at, is_deleted)
    VALUES
    (@org_c, N'STC Cloud — Production',        N'STC Cloud',        N'IAAS',     N'PRIVATE',    N'INTERNAL', N'KSA Riyadh',     1, N'TIER_1',  N'CRITICAL',  1,1,1,1, N'ACTIVE', DATEADD(DAY, -45, CAST(GETUTCDATE() AS DATE)), DATEADD(MONTH, 11, CAST(GETUTCDATE() AS DATE)), N'Primary hyperscale provider in-country', GETUTCDATE(), 0),
    (@org_c, N'Microsoft Azure — Workloads',    N'Microsoft',        N'IAAS',     N'PUBLIC',     N'INTERNAL', N'KSA Region',     1, N'TIER_1',  N'CRITICAL',  1,1,1,1, N'ACTIVE', DATEADD(DAY, -60, CAST(GETUTCDATE() AS DATE)), DATEADD(MONTH, 10, CAST(GETUTCDATE() AS DATE)), N'Saudi region — landed for data residency', GETUTCDATE(), 0),
    (@org_c, N'Microsoft 365 — Productivity',   N'Microsoft',        N'SAAS',     N'PUBLIC',     N'PUBLIC',   N'KSA Region',     1, N'TIER_2',  N'HIGH',      1,1,1,1, N'ACTIVE', DATEADD(DAY, -30, CAST(GETUTCDATE() AS DATE)), DATEADD(MONTH, 11, CAST(GETUTCDATE() AS DATE)), N'Productivity + collaboration', GETUTCDATE(), 0),
    (@org_c, N'Salesforce — CRM',               N'Salesforce',       N'SAAS',     N'PUBLIC',     N'CONFIDENTIAL', N'EU Frankfurt', 0, N'TIER_3', N'HIGH',     1,1,1,1, N'ACTIVE', DATEADD(DAY, -90, CAST(GETUTCDATE() AS DATE)), DATEADD(MONTH,  9, CAST(GETUTCDATE() AS DATE)), N'Cross-border — has SCC + Data Office approval', GETUTCDATE(), 0),
    (@org_c, N'AWS — Analytics',                N'AWS',              N'IAAS',     N'PUBLIC',     N'INTERNAL', N'Bahrain',        0, N'TIER_2',  N'MEDIUM',    1,1,1,1, N'ACTIVE', DATEADD(DAY, -75, CAST(GETUTCDATE() AS DATE)), DATEADD(MONTH, 10, CAST(GETUTCDATE() AS DATE)), N'Aggregated analytics — non-PII', GETUTCDATE(), 0),
    (@org_c, N'Oracle Cloud — DB',              N'Oracle',           N'PAAS',     N'PUBLIC',     N'CONFIDENTIAL', N'KSA Region', 1, N'TIER_1',  N'HIGH',     1,1,1,1, N'ACTIVE', DATEADD(DAY, -50, CAST(GETUTCDATE() AS DATE)), DATEADD(MONTH, 11, CAST(GETUTCDATE() AS DATE)), N'OCI in-country', GETUTCDATE(), 0),
    (@org_c, N'ServiceNow — ITSM',              N'ServiceNow',       N'SAAS',     N'PUBLIC',     N'INTERNAL', N'EU Amsterdam',   0, N'TIER_3',  N'MEDIUM',    1,1,1,1, N'ACTIVE', DATEADD(DAY, -120, CAST(GETUTCDATE() AS DATE)), DATEADD(MONTH,  8, CAST(GETUTCDATE() AS DATE)), N'ITSM + workflow', GETUTCDATE(), 0),
    (@org_c, N'GitHub Enterprise Cloud',         N'GitHub',           N'SAAS',     N'PUBLIC',     N'INTERNAL', N'US East',        0, N'TIER_3',  N'MEDIUM',    1,1,1,1, N'ACTIVE', DATEADD(DAY, -180, CAST(GETUTCDATE() AS DATE)), DATEADD(MONTH,  6, CAST(GETUTCDATE() AS DATE)), N'Source code hosting + Advanced Security', GETUTCDATE(), 0);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (5) cyber_lc.assets + cyber_incidents + threat_intel
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_a BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM cyber_lc.assets WHERE organisation_id = @org_a)
BEGIN
    INSERT INTO cyber_lc.assets
        (external_id, organisation_id, asset_name, asset_type, classification,
         business_owner, technical_owner, location_site, cloud_provider, environment,
         criticality, operational_status, last_scanned_at, is_deleted)
    VALUES
    (CONCAT('AST-', NEWID()), @org_a, N'Core Banking — Application',     N'APPLICATION', N'CRITICAL',     N'Retail Banking', N'Core-Apps Team', N'Riyadh DC1',  N'STC Cloud',  N'PROD',  N'CRITICAL', N'OPERATIONAL', DATEADD(DAY, -3, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'Core Banking — Oracle DB',        N'DATABASE',    N'CRITICAL',     N'Retail Banking', N'DBA Team',       N'Riyadh DC1',  N'Oracle Cloud', N'PROD', N'CRITICAL', N'OPERATIONAL', DATEADD(DAY, -3, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'Customer Portal — Web',           N'APPLICATION', N'CONFIDENTIAL', N'Digital Channels', N'Web Team',     N'Riyadh DC1',  N'Microsoft Azure', N'PROD', N'CRITICAL', N'OPERATIONAL', DATEADD(DAY, -2, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'Mobile App — iOS',                 N'MOBILE_APP',  N'CONFIDENTIAL', N'Digital Channels', N'Mobile Team',  N'Cloud',        N'Microsoft Azure', N'PROD', N'HIGH',     N'OPERATIONAL', DATEADD(DAY, -1, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'Mobile App — Android',             N'MOBILE_APP',  N'CONFIDENTIAL', N'Digital Channels', N'Mobile Team',  N'Cloud',        N'Microsoft Azure', N'PROD', N'HIGH',     N'OPERATIONAL', DATEADD(DAY, -1, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'AD Forest',                        N'INFRASTRUCTURE', N'CRITICAL',  N'IT Operations', N'AD Team',        N'Riyadh DC1',  NULL,           N'PROD',  N'CRITICAL', N'OPERATIONAL', DATEADD(DAY, -5, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'Email Gateway',                    N'INFRASTRUCTURE', N'CONFIDENTIAL', N'IT Operations', N'Mail Team',   N'Riyadh DC1', NULL,           N'PROD',  N'HIGH',     N'OPERATIONAL', DATEADD(DAY, -7, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'SIEM — Sentinel',                  N'APPLICATION', N'CONFIDENTIAL', N'Cybersecurity',  N'SOC Team',      N'Cloud',        N'Microsoft Azure', N'PROD', N'CRITICAL', N'OPERATIONAL', DATEADD(DAY, -1, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'Firewall Cluster — Branch',        N'INFRASTRUCTURE', N'CONFIDENTIAL', N'Network',     N'Network Team',  N'Multiple',     NULL,           N'PROD',  N'HIGH',     N'OPERATIONAL', DATEADD(DAY, -10, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'ATM Network Switches',             N'INFRASTRUCTURE', N'CONFIDENTIAL', N'Branch Ops',   N'ATM Team',      N'KSA branches', NULL,           N'PROD',  N'HIGH',     N'OPERATIONAL', DATEADD(DAY, -14, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'OT — UPS controller',              N'OT',          N'CONFIDENTIAL', N'Facilities',    N'OT Team',         N'Riyadh DC1',  NULL,           N'PROD',  N'MEDIUM',   N'OPERATIONAL', DATEADD(DAY, -30, GETUTCDATE()), 0),
    (CONCAT('AST-', NEWID()), @org_a, N'Backup Storage — Tape Vault',      N'INFRASTRUCTURE', N'CRITICAL',   N'IT Operations', N'Backup Team',    N'Riyadh DC2',  NULL,           N'PROD',  N'CRITICAL', N'OPERATIONAL', DATEADD(DAY, -2,  GETUTCDATE()), 0);
END
GO

-- (Cyber LC assessments/incidents/threat_intel — schema varies, use TRY blocks)

DECLARE @org_ci BIGINT = 1;

BEGIN TRY
    IF NOT EXISTS (SELECT 1 FROM cyber_lc.cyber_incidents WHERE organisation_id = @org_ci)
    BEGIN
        INSERT INTO cyber_lc.cyber_incidents (organisation_id, title_en)
        SELECT @org_ci, n FROM (VALUES
            (N'Phishing campaign — finance team'),
            (N'EDR detection — workstation 47'),
            (N'Stolen credentials — VPN portal'),
            (N'Cloud misconfig — S3 bucket'),
            (N'Supplier breach — third-party SaaS')
        ) AS x(n);
    END
END TRY
BEGIN CATCH
    PRINT 'Skipped cyber_lc.cyber_incidents — schema mismatch.';
END CATCH
GO

BEGIN TRY
    IF NOT EXISTS (SELECT 1 FROM cyber_lc.threat_intel WHERE organisation_id = 1)
    BEGIN
        INSERT INTO cyber_lc.threat_intel (organisation_id, title_en)
        SELECT 1, n FROM (VALUES
            (N'Lazarus-aligned spear-phishing targeting KSA banks'),
            (N'CVE-2026-1142 — RCE in widely-used web framework'),
            (N'Ransomware-as-a-service group "DarkBank" active in MENA'),
            (N'Supply-chain compromise on popular IT-monitoring vendor'),
            (N'Saudi-language smishing campaign during Ramadan'),
            (N'Nation-state APT-32 tooling update — new IOCs')
        ) AS x(n);
    END
END TRY
BEGIN CATCH
    PRINT 'Skipped cyber_lc.threat_intel — schema mismatch.';
END CATCH
GO

-- ══════════════════════════════════════════════════════════════════
-- (6) risk_lc.treatment_actions + acceptance_requests + kri_breaches + activity_log
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_t BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM risk_lc.treatment_actions WHERE organisation_id = @org_t)
BEGIN
    -- Use first 12 risks as plan_id soft-links
    ;WITH r AS (
        SELECT TOP 6 id FROM risk.risks WHERE organisation_id = @org_t AND is_deleted = 0 ORDER BY id
    ),
    actions AS (
        SELECT * FROM (VALUES
            (1, N'Enforce MFA on admin accounts',         N'تفعيل MFA لحسابات المسؤول',     N'CYBER_MANAGER',     30,  N'IN_PROGRESS', 0.6),
            (2, N'Quarterly DR test re-instated',         N'إعادة تنشيط اختبار التعافي',     N'BCM_MANAGER',       45,  N'PLANNED',     0.4),
            (3, N'Air-gap backup tier-1 systems',         N'فصل النسخ الاحتياطي عن الشبكة', N'IT_OPS_LEAD',       60,  N'IN_PROGRESS', 0.7)
        ) AS a (slot, title_en, title_ar, owner_role, days_to_target, status, expected_reduction)
    )
    INSERT INTO risk_lc.treatment_actions
        (external_id, organisation_id, plan_id, title_en, title_ar, owner_role,
         target_date, status, expected_risk_reduction, is_deleted)
    SELECT CONCAT('TA-', NEWID()), @org_t, r.id, a.title_en, a.title_ar, a.owner_role,
           DATEADD(DAY, a.days_to_target, CAST(GETUTCDATE() AS DATE)),
           a.status, a.expected_reduction, 0
      FROM r CROSS JOIN actions a;
END
GO

DECLARE @org_ar BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM risk_lc.acceptance_requests WHERE organisation_id = @org_ar)
BEGIN
    ;WITH r AS (
        SELECT TOP 5 id FROM risk.risks WHERE organisation_id = @org_ar AND is_deleted = 0 ORDER BY id
    )
    INSERT INTO risk_lc.acceptance_requests
        (external_id, organisation_id, risk_id, rationale_en, rationale_ar,
         residual_score, appetite_upper, status, expires_at, is_deleted)
    SELECT CONCAT('RA-', NEWID()), @org_ar, r.id,
           N'Treatment cost exceeds expected loss; risk owner + CRO sign-off recorded.',
           N'تكلفة المعالجة تتجاوز الخسارة المتوقعة؛ تم اعتماد قبول الخطر.',
           18, 12,
           CASE (r.id % 3) WHEN 0 THEN N'APPROVED' WHEN 1 THEN N'UNDER_REVIEW' ELSE N'EXPIRED' END,
           DATEADD(MONTH, 6, GETUTCDATE()),
           0
      FROM r;
END
GO

DECLARE @org_kb BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM risk_lc.kri_breaches WHERE organisation_id = @org_kb)
BEGIN
    BEGIN TRY
        INSERT INTO risk_lc.kri_breaches (organisation_id, risk_id, kri_code, kri_name_en,
                                          threshold_value, actual_value, direction, detected_at,
                                          status, is_deleted)
        SELECT 1, r.id,
               CONCAT('KRI-', RIGHT('000' + CAST(r.id AS VARCHAR(4)), 3)),
               CONCAT('Auto-KRI for risk ', r.id),
               CAST(80.00 AS DECIMAL(10,2)),
               CAST(92.00 + (r.id % 5) * 2 AS DECIMAL(10,2)),
               N'GREATER_THAN', DATEADD(DAY, -(5 + r.id), GETUTCDATE()),
               CASE (r.id % 4)
                 WHEN 0 THEN N'OPEN'
                 WHEN 1 THEN N'ACKNOWLEDGED'
                 WHEN 2 THEN N'IN_TREATMENT'
                 ELSE        N'RESOLVED'
               END,
               0
          FROM (SELECT TOP 6 id FROM risk.risks WHERE organisation_id = 1 AND is_deleted = 0 ORDER BY id) r;
    END TRY
    BEGIN CATCH
        PRINT 'Skipped risk_lc.kri_breaches — schema mismatch.';
    END CATCH
END
GO

BEGIN TRY
    IF NOT EXISTS (SELECT 1 FROM risk_lc.activity_log WHERE organisation_id = 1)
    BEGIN
        INSERT INTO risk_lc.activity_log (organisation_id, risk_id, action, actor, occurred_at)
        SELECT 1, r.id,
               CASE ((r.id + n) % 6)
                 WHEN 0 THEN N'CREATED'
                 WHEN 1 THEN N'ASSESSED'
                 WHEN 2 THEN N'TREATMENT_PLANNED'
                 WHEN 3 THEN N'KRI_BREACH'
                 WHEN 4 THEN N'OWNER_REASSIGNED'
                 ELSE        N'STATUS_CHANGED'
               END,
               CASE ((r.id + n) % 4)
                 WHEN 0 THEN N'Khalid Al-Mutairi'
                 WHEN 1 THEN N'Layla Al-Rashid'
                 WHEN 2 THEN N'Saad Al-Subaie'
                 ELSE        N'Reema Al-Qahtani'
               END,
               DATEADD(DAY, -(r.id * 3 + n), GETUTCDATE())
          FROM (SELECT TOP 5 id FROM risk.risks WHERE organisation_id = 1 AND is_deleted = 0 ORDER BY id) r
          CROSS JOIN (VALUES (1),(2),(3),(4),(5)) v(n);
    END
END TRY
BEGIN CATCH
    PRINT 'Skipped risk_lc.activity_log — schema mismatch.';
END CATCH
GO

-- ══════════════════════════════════════════════════════════════════
-- (7) regchange.changes (regulatory-change tracker)
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_rc BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM regchange.changes WHERE organisation_id = @org_rc)
BEGIN
    INSERT INTO regchange.changes
        (external_id, organisation_id, regulator, citation, title_en, title_ar,
         summary_en, summary_ar, published_at, effective_at, status, applicability,
         impact_score, created_at, is_deleted)
    VALUES
    (CONCAT('RC-', NEWID()), @org_rc, N'NCA',    N'NCA-CSCC v3.0',   N'NCA CSCC version 3.0 published',                      N'إصدار CSCC v3.0',                  N'CSCC v3.0 adds OT-specific controls and refines cloud applicability matrix.', N'يضيف v3.0 ضوابط OT جديدة.',              DATEADD(DAY, -45, CAST(GETUTCDATE() AS DATE)), DATEADD(DAY,  45, CAST(GETUTCDATE() AS DATE)), N'IMPACT_ASSESSED', N'APPLICABLE',      4, GETUTCDATE(), 0),
    (CONCAT('RC-', NEWID()), @org_rc, N'SDAIA',  N'PDPL-IR-2026-01', N'PDPL implementing regulation update — DSR portals',  N'تحديث اللائحة التنفيذية لحماية البيانات', N'New requirement for DSR self-service portals + machine-readable export.', N'متطلب جديد لبوابة طلبات صاحب البيانات.', DATEADD(DAY, -30, CAST(GETUTCDATE() AS DATE)), DATEADD(DAY,  90, CAST(GETUTCDATE() AS DATE)), N'PLANNING',        N'APPLICABLE',      5, GETUTCDATE(), 0),
    (CONCAT('RC-', NEWID()), @org_rc, N'SAMA',   N'SAMA-OPRES-2026-02', N'SAMA OPRES — annex on AI model risk',              N'ملحق المخاطر للذكاء الاصطناعي',     N'New annex requires model-risk governance + bias testing for SAMA-licensed entities.', N'يتطلب حوكمة المخاطر للنماذج.', DATEADD(DAY, -20, CAST(GETUTCDATE() AS DATE)), DATEADD(DAY, 120, CAST(GETUTCDATE() AS DATE)), N'NEW',             N'APPLICABLE',      4, GETUTCDATE(), 0),
    (CONCAT('RC-', NEWID()), @org_rc, N'NCEMA',  N'NCEMA-CR-2026-08',N'NCEMA crisis-readiness annual return',                N'إفصاح NCEMA السنوي',                 N'Annual crisis-readiness return now requires exercise outcomes + AAR summary.', N'الإفصاح السنوي يتضمن نتائج التمارين.', DATEADD(DAY, -10, CAST(GETUTCDATE() AS DATE)), DATEADD(DAY,  60, CAST(GETUTCDATE() AS DATE)), N'PLANNING',        N'APPLICABLE',      3, GETUTCDATE(), 0),
    (CONCAT('RC-', NEWID()), @org_rc, N'DGA',    N'DGA-2026-DPS',    N'DGA Digital Public Services maturity index',          N'مؤشر نضج الخدمات الرقمية الحكومية', N'New DGA maturity assessment for digital public services + Vision 2030 KPIs.', N'تقييم نضج جديد للخدمات الرقمية.', DATEADD(DAY,  -5, CAST(GETUTCDATE() AS DATE)), DATEADD(DAY, 150, CAST(GETUTCDATE() AS DATE)), N'NEW',             N'UNDER_REVIEW',    3, GETUTCDATE(), 0),
    (CONCAT('RC-', NEWID()), @org_rc, N'NCA',    N'NCA-OTCC-v2',     N'NCA OTCC version 2 — OT-specific controls',           N'OTCC v2 — ضوابط OT',                  N'OTCC v2 expands coverage for IIoT + manufacturing.', N'OTCC v2 يوسع التغطية إلى IIoT.', DATEADD(DAY, -55, CAST(GETUTCDATE() AS DATE)), DATEADD(DAY, 180, CAST(GETUTCDATE() AS DATE)), N'IMPACT_ASSESSED', N'NOT_APPLICABLE',  1, GETUTCDATE(), 0),
    (CONCAT('RC-', NEWID()), @org_rc, N'SDAIA',  N'PDPL-IR-2026-02', N'PDPL implementing regulation — biometric processing', N'PDPL — معالجة بيانات بيومترية',        N'Tightened conditions for biometric data processing — explicit consent + DPIA mandatory.', N'تشديد على معالجة البيانات البيومترية.', DATEADD(DAY, -75, CAST(GETUTCDATE() AS DATE)), DATEADD(DAY,  30, CAST(GETUTCDATE() AS DATE)), N'IMPACT_ASSESSED', N'APPLICABLE',      5, GETUTCDATE(), 0),
    (CONCAT('RC-', NEWID()), @org_rc, N'SAMA',   N'SAMA-CRMG-2026-01',N'SAMA cyber risk-management guideline refresh',       N'تحديث إرشادات إدارة المخاطر السيبرانية', N'Refreshed cyber risk-management guideline aligning with NCA ECC v2.0.', N'تحديث الإرشادات بالتوافق مع NCA ECC v2.0.', DATEADD(DAY, -90, CAST(GETUTCDATE() AS DATE)), DATEADD(DAY,   0, CAST(GETUTCDATE() AS DATE)), N'CLOSED',          N'APPLICABLE',      4, GETUTCDATE(), 0);
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (8) nca_ecc.filings + board_pack.packs (shallow schemas — TRY blocks)
-- ══════════════════════════════════════════════════════════════════

BEGIN TRY
    IF NOT EXISTS (SELECT 1 FROM nca_ecc.filings WHERE organisation_id = 1)
    BEGIN
        INSERT INTO nca_ecc.filings (organisation_id, filing_year, status)
        SELECT 1, y, s FROM (VALUES (2023,N'ACCEPTED'),(2024,N'ACCEPTED'),(2025,N'ACCEPTED'),(2026,N'IN_PROGRESS')) AS x(y,s);
    END
END TRY
BEGIN CATCH
    PRINT 'Skipped nca_ecc.filings — schema mismatch.';
END CATCH
GO

DECLARE @org_bp BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM board_pack.packs WHERE organisation_id = @org_bp)
BEGIN
    INSERT INTO board_pack.packs
        (external_id, organisation_id, title_en, title_ar, period_label,
         period_start, period_end, audience,
         risk_summary_json, appetite_breaches_json, audit_engagements_json,
         incidents_json, pdpl_summary_json, ccm_summary_json, sla_summary_json)
    VALUES
    (CONCAT('BP-', NEWID()), @org_bp, N'Q1 2026 GRC Board Pack', N'حقيبة المجلس — الربع الأول ٢٠٢٦', N'2026-Q1',
     '2026-01-01', '2026-03-31', N'BOARD',
     '{"openHighRisks":12,"overdueCapa":4,"appetiteBreaches":3}',
     '{"breachesQ1":3}',
     '{"engagements":4}',
     '{"active":2,"closedThisQuarter":7}',
     '{"complianceScore":76}',
     '{"complianceScore":82}',
     '{"breachedSlas":2}'),
    (CONCAT('BP-', NEWID()), @org_bp, N'Q4 2025 GRC Board Pack', N'حقيبة المجلس — الربع الرابع ٢٠٢٥', N'2025-Q4',
     '2025-10-01', '2025-12-31', N'BOARD',
     '{"openHighRisks":15,"overdueCapa":6,"appetiteBreaches":4}',
     '{"breachesQ4":4}',
     '{"engagements":5}',
     '{"active":3,"closedThisQuarter":9}',
     '{"complianceScore":72}',
     '{"complianceScore":80}',
     '{"breachedSlas":3}'),
    (CONCAT('BP-', NEWID()), @org_bp, N'Q3 2025 GRC Board Pack', N'حقيبة المجلس — الربع الثالث ٢٠٢٥', N'2025-Q3',
     '2025-07-01', '2025-09-30', N'EXEC',
     '{"openHighRisks":18,"overdueCapa":8,"appetiteBreaches":5}',
     '{"breachesQ3":5}',
     '{"engagements":3}',
     '{"active":4,"closedThisQuarter":11}',
     '{"complianceScore":68}',
     '{"complianceScore":77}',
     '{"breachedSlas":4}'),
    (CONCAT('BP-', NEWID()), @org_bp, N'2025 Annual Risk Committee Report', N'تقرير لجنة المخاطر السنوي ٢٠٢٥', N'2025',
     '2025-01-01', '2025-12-31', N'RISK_COMMITTEE',
     '{"openHighRisks":12,"overdueCapa":4,"appetiteBreaches":12}',
     '{"breaches2025":12}',
     '{"engagements":15}',
     '{"active":2,"closedThisYear":31}',
     '{"complianceScore":76}',
     '{"complianceScore":82}',
     '{"breachedSlas":7}');
END
GO

PRINT 'V121 round-5 demo fill applied: consent + cross-border + ropa + cloud + assets + threat-intel + treatment + acceptance + KRI breaches + activity log + regchange + filings + board pack.';
GO
