-- V121: round-5 demo fill — every empty user-facing tab now has data.
--
-- 29 empty tables remained after V120. This migration covers the 15
-- listed below, of which 7 NEVER WORKED and were removed by F2-77 on
-- 2026-08-06 (marked ✗). Each named a column that does not exist and/or omitted a
-- required one, behind a TRY/CATCH that cannot catch a compile-time binding error.
-- that are visible on common pages:
--
--   privacy.consent_records           10 (Privacy → Consent tab)
--   privacy_lc.cross_border_transfers  6 (Privacy → Cross-Border tab)
--   privacy_lc.ropa_activities        ✗ (RoPA lifecycle)
--   privacy_lc.dpia_assessments        ✗ (DPIA lifecycle)
--   cyber.cloud_services               8 (Cyber → Cloud Services)
--   cyber_lc.assets                   12 (Asset register)
--   cyber_lc.cyber_incidents           ✗ (Cyber lifecycle incidents)
--   cyber_lc.threat_intel              ✗ (Threat-intel feed)
--   risk_lc.acceptance_requests        5 (Risk acceptance board)
--   risk_lc.treatment_actions         12 (Risk treatment actions)
--   risk_lc.kri_breaches               ✗ (KRI breach board)
--   risk_lc.activity_log              ✗ (Risk activity history)
--   regchange.changes                  8 (Regulatory change tracker)
--   nca_ecc.filings                    ✗ (NCA ECC annual filings)
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
    -- ⚠ F2-77: external_id was MISSING from this insert. It is NVARCHAR(64) NOT NULL with no
    -- DEFAULT and a UNIQUE (organisation_id, external_id) constraint, so this statement could
    -- never have inserted a row — and it never has. privacy_lc.cross_border_transfers holds
    -- ZERO rows in both live databases, and the guard above is "seed if empty", so the block
    -- would have executed if it had ever been reached. It has not: dev runs no Flyway (its
    -- schema is Python-bootstrapped) and prod baselined at V292, above this file. The ordered
    -- chain-proof run is the FIRST execution of this migration in the project's history, which
    -- is precisely why the defect surfaced now and not in three years of deploys.
    --
    -- The value is deterministic rather than NEWID() so a re-run cannot violate the unique
    -- constraint, and it is visibly demo data — the six rows carry distinct ropa_ids 1-6.
    INSERT INTO privacy_lc.cross_border_transfers
        (external_id, organisation_id, ropa_id, destination_country, purpose_en, purpose_ar,
         adequacy, safeguard, safeguard_date, pre_risk_score, post_risk_score,
         review_interval_months, status, created_at, is_deleted)
    VALUES
    (N'CBT-DEMO-001', @org_x, 1, N'Ireland',         N'EU analytics processing',                N'معالجة تحليلات في الاتحاد الأوروبي', N'ADEQUATE', N'SCC',                  DATEADD(MONTH, -6, GETUTCDATE()),  14, 6,  12, N'APPROVED',    GETUTCDATE(), 0),
    (N'CBT-DEMO-002', @org_x, 2, N'United States',   N'Salesforce support tickets',             N'تذاكر دعم في الولايات المتحدة',     N'NONE',     N'DATA_OFFICE_APPROVAL', DATEADD(MONTH, -3, GETUTCDATE()),  20, 10, 6,  N'APPROVED',    GETUTCDATE(), 0),
    (N'CBT-DEMO-003', @org_x, 3, N'Netherlands',     N'ML model training in Azure West-EU',     N'تدريب نموذج التعلم الآلي',           N'PARTIAL',  N'BCR',                  DATEADD(MONTH, -4, GETUTCDATE()),  16, 8,  12, N'APPROVED',    GETUTCDATE(), 0),
    (N'CBT-DEMO-004', @org_x, 4, N'India',           N'24/7 helpdesk processing',               N'مكتب مساعدة على مدار الساعة',        N'NONE',     N'EXPLICIT_CONSENT',     DATEADD(MONTH, -2, GETUTCDATE()),  18, 12, 6,  N'UNDER_REVIEW',GETUTCDATE(), 0),
    (N'CBT-DEMO-005', @org_x, 5, N'UAE',             N'GCC payments clearing house',            N'مقاصة مدفوعات الخليج',               N'ADEQUATE', N'INTRA_GROUP',          DATEADD(MONTH, -8, GETUTCDATE()),  10, 4,  24, N'APPROVED',    GETUTCDATE(), 0),
    (N'CBT-DEMO-006', @org_x, 6, N'United Kingdom',  N'UK insurance underwriting partner',      N'شريك التأمين في المملكة المتحدة',    N'ADEQUATE', N'SCC',                  DATEADD(MONTH, -5, GETUTCDATE()),  12, 5,  12, N'APPROVED',    GETUTCDATE(), 0);
END
GO

-- ============================================================================================
-- F2-77 (2026-08-06) — THIS SEED WAS REMOVED BECAUSE IT COULD NEVER HAVE INSERTED A ROW.
--
-- Target: privacy_lc.ropa_activities (10 or fewer demo rows).
--   * named a column that does not exist on the table: activity_name
--   * omitted required columns (NOT NULL, no DEFAULT): external_id, lawful_basis, name_ar, name_en, purposes_ar, purposes_en
--
-- The block carried `BEGIN TRY … BEGIN CATCH PRINT '… schema mismatch.'` and the comment
-- "Schema may differ; … let the rest default. Guarded inserts with TRY." Both halves of
-- that belief are false:
--
--   1. `Invalid column name` is a COMPILE-TIME binding error for the entire batch. SQL
--      Server's deferred name resolution covers a missing TABLE, never a missing COLUMN, so
--      the batch fails to compile and nothing inside TRY runs. The CATCH has never printed.
--   2. "let the rest default" is false — the omitted columns above have no DEFAULT.
--
-- So this is F2-112's shape: a control that cannot fire, protecting a statement that cannot
-- run. Measured across V121/V122: 7 of 7 TRY-guarded inserts, all impossible.
--
-- Where dev's rows actually came from: the Python bootstrap (`setup_*_db.py` / 
-- `seed_real_data.py`), not from here. That is F2-77's thesis — the folder was maintained as
-- a description of a schema someone had in front of them rather than as an ordered history,
-- and nothing ever ran it in order to notice.
--
-- Restored rather than repaired ON PURPOSE: repairing it means inventing ~50 rows of
-- bilingual Saudi demo content, and a fabricated row is worse than an absent one because the
-- file header counts it as delivered. If these tabs need demo data, seed them from a source
-- that knows the schema.
-- ============================================================================================
GO

-- ============================================================================================
-- F2-77 (2026-08-06) — THIS SEED WAS REMOVED BECAUSE IT COULD NEVER HAVE INSERTED A ROW.
--
-- Target: privacy_lc.dpia_assessments (10 or fewer demo rows).
--   * named a column that does not exist on the table: title_en
--   * omitted required columns (NOT NULL, no DEFAULT): external_id, ropa_id
--
-- The block carried `BEGIN TRY … BEGIN CATCH PRINT '… schema mismatch.'` and the comment
-- "Schema may differ; … let the rest default. Guarded inserts with TRY." Both halves of
-- that belief are false:
--
--   1. `Invalid column name` is a COMPILE-TIME binding error for the entire batch. SQL
--      Server's deferred name resolution covers a missing TABLE, never a missing COLUMN, so
--      the batch fails to compile and nothing inside TRY runs. The CATCH has never printed.
--   2. "let the rest default" is false — the omitted columns above have no DEFAULT.
--
-- So this is F2-112's shape: a control that cannot fire, protecting a statement that cannot
-- run. Measured across V121/V122: 7 of 7 TRY-guarded inserts, all impossible.
--
-- Where dev's rows actually came from: the Python bootstrap (`setup_*_db.py` / 
-- `seed_real_data.py`), not from here. That is F2-77's thesis — the folder was maintained as
-- a description of a schema someone had in front of them rather than as an ordered history,
-- and nothing ever ran it in order to notice.
--
-- Restored rather than repaired ON PURPOSE: repairing it means inventing ~50 rows of
-- bilingual Saudi demo content, and a fabricated row is worse than an absent one because the
-- file header counts it as delivered. If these tabs need demo data, seed them from a source
-- that knows the schema.
-- ============================================================================================
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

-- ============================================================================================
-- F2-77 (2026-08-06) — THIS SEED WAS REMOVED BECAUSE IT COULD NEVER HAVE INSERTED A ROW.
--
-- Target: cyber_lc.cyber_incidents (10 or fewer demo rows).
--   * omitted required columns (NOT NULL, no DEFAULT): classification, external_id, severity, title_ar
--
-- The block carried `BEGIN TRY … BEGIN CATCH PRINT '… schema mismatch.'` and the comment
-- "Schema may differ; … let the rest default. Guarded inserts with TRY." Both halves of
-- that belief are false:
--
--   1. `Invalid column name` is a COMPILE-TIME binding error for the entire batch. SQL
--      Server's deferred name resolution covers a missing TABLE, never a missing COLUMN, so
--      the batch fails to compile and nothing inside TRY runs. The CATCH has never printed.
--   2. "let the rest default" is false — the omitted columns above have no DEFAULT.
--
-- So this is F2-112's shape: a control that cannot fire, protecting a statement that cannot
-- run. Measured across V121/V122: 7 of 7 TRY-guarded inserts, all impossible.
--
-- Where dev's rows actually came from: the Python bootstrap (`setup_*_db.py` / 
-- `seed_real_data.py`), not from here. That is F2-77's thesis — the folder was maintained as
-- a description of a schema someone had in front of them rather than as an ordered history,
-- and nothing ever ran it in order to notice.
--
-- Restored rather than repaired ON PURPOSE: repairing it means inventing ~50 rows of
-- bilingual Saudi demo content, and a fabricated row is worse than an absent one because the
-- file header counts it as delivered. If these tabs need demo data, seed them from a source
-- that knows the schema.
-- ============================================================================================
GO

-- ============================================================================================
-- F2-77 (2026-08-06) — THIS SEED WAS REMOVED BECAUSE IT COULD NEVER HAVE INSERTED A ROW.
--
-- Target: cyber_lc.threat_intel (10 or fewer demo rows).
--   * omitted required columns (NOT NULL, no DEFAULT): external_id, severity, title_ar
--
-- The block carried `BEGIN TRY … BEGIN CATCH PRINT '… schema mismatch.'` and the comment
-- "Schema may differ; … let the rest default. Guarded inserts with TRY." Both halves of
-- that belief are false:
--
--   1. `Invalid column name` is a COMPILE-TIME binding error for the entire batch. SQL
--      Server's deferred name resolution covers a missing TABLE, never a missing COLUMN, so
--      the batch fails to compile and nothing inside TRY runs. The CATCH has never printed.
--   2. "let the rest default" is false — the omitted columns above have no DEFAULT.
--
-- So this is F2-112's shape: a control that cannot fire, protecting a statement that cannot
-- run. Measured across V121/V122: 7 of 7 TRY-guarded inserts, all impossible.
--
-- Where dev's rows actually came from: the Python bootstrap (`setup_*_db.py` / 
-- `seed_real_data.py`), not from here. That is F2-77's thesis — the folder was maintained as
-- a description of a schema someone had in front of them rather than as an ordered history,
-- and nothing ever ran it in order to notice.
--
-- Restored rather than repaired ON PURPOSE: repairing it means inventing ~50 rows of
-- bilingual Saudi demo content, and a fabricated row is worse than an absent one because the
-- file header counts it as delivered. If these tabs need demo data, seed them from a source
-- that knows the schema.
-- ============================================================================================
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

-- ============================================================================================
-- F2-77 (2026-08-06) — THIS SEED WAS REMOVED BECAUSE IT COULD NEVER HAVE INSERTED A ROW.
--
-- Target: risk_lc.kri_breaches (10 or fewer demo rows).
--   * omitted required columns (NOT NULL, no DEFAULT): current_owner, external_id, kri_name_ar
--
-- The block carried `BEGIN TRY … BEGIN CATCH PRINT '… schema mismatch.'` and the comment
-- "Schema may differ; … let the rest default. Guarded inserts with TRY." Both halves of
-- that belief are false:
--
--   1. `Invalid column name` is a COMPILE-TIME binding error for the entire batch. SQL
--      Server's deferred name resolution covers a missing TABLE, never a missing COLUMN, so
--      the batch fails to compile and nothing inside TRY runs. The CATCH has never printed.
--   2. "let the rest default" is false — the omitted columns above have no DEFAULT.
--
-- So this is F2-112's shape: a control that cannot fire, protecting a statement that cannot
-- run. Measured across V121/V122: 7 of 7 TRY-guarded inserts, all impossible.
--
-- Where dev's rows actually came from: the Python bootstrap (`setup_*_db.py` / 
-- `seed_real_data.py`), not from here. That is F2-77's thesis — the folder was maintained as
-- a description of a schema someone had in front of them rather than as an ordered history,
-- and nothing ever ran it in order to notice.
--
-- Restored rather than repaired ON PURPOSE: repairing it means inventing ~50 rows of
-- bilingual Saudi demo content, and a fabricated row is worse than an absent one because the
-- file header counts it as delivered. If these tabs need demo data, seed them from a source
-- that knows the schema.
-- ============================================================================================
GO

-- ============================================================================================
-- F2-77 (2026-08-06) — THIS SEED WAS REMOVED BECAUSE IT COULD NEVER HAVE INSERTED A ROW.
--
-- Target: risk_lc.activity_log (10 or fewer demo rows).
--   * named a column that does not exist on the table: actor, occurred_at, risk_id
--   * omitted required columns (NOT NULL, no DEFAULT): entity_id, entity_type, user_id
--
-- The block carried `BEGIN TRY … BEGIN CATCH PRINT '… schema mismatch.'` and the comment
-- "Schema may differ; … let the rest default. Guarded inserts with TRY." Both halves of
-- that belief are false:
--
--   1. `Invalid column name` is a COMPILE-TIME binding error for the entire batch. SQL
--      Server's deferred name resolution covers a missing TABLE, never a missing COLUMN, so
--      the batch fails to compile and nothing inside TRY runs. The CATCH has never printed.
--   2. "let the rest default" is false — the omitted columns above have no DEFAULT.
--
-- So this is F2-112's shape: a control that cannot fire, protecting a statement that cannot
-- run. Measured across V121/V122: 7 of 7 TRY-guarded inserts, all impossible.
--
-- Where dev's rows actually came from: the Python bootstrap (`setup_*_db.py` / 
-- `seed_real_data.py`), not from here. That is F2-77's thesis — the folder was maintained as
-- a description of a schema someone had in front of them rather than as an ordered history,
-- and nothing ever ran it in order to notice.
--
-- Restored rather than repaired ON PURPOSE: repairing it means inventing ~50 rows of
-- bilingual Saudi demo content, and a fabricated row is worse than an absent one because the
-- file header counts it as delivered. If these tabs need demo data, seed them from a source
-- that knows the schema.
-- ============================================================================================
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

-- ============================================================================================
-- F2-77 (2026-08-06) — THIS SEED WAS REMOVED BECAUSE IT COULD NEVER HAVE INSERTED A ROW.
--
-- Target: nca_ecc.filings (10 or fewer demo rows).
--   * named a column that does not exist on the table: filing_year
--   * omitted required columns (NOT NULL, no DEFAULT): drafted_by, external_id, fiscal_year
--
-- The block carried `BEGIN TRY … BEGIN CATCH PRINT '… schema mismatch.'` and the comment
-- "Schema may differ; … let the rest default. Guarded inserts with TRY." Both halves of
-- that belief are false:
--
--   1. `Invalid column name` is a COMPILE-TIME binding error for the entire batch. SQL
--      Server's deferred name resolution covers a missing TABLE, never a missing COLUMN, so
--      the batch fails to compile and nothing inside TRY runs. The CATCH has never printed.
--   2. "let the rest default" is false — the omitted columns above have no DEFAULT.
--
-- So this is F2-112's shape: a control that cannot fire, protecting a statement that cannot
-- run. Measured across V121/V122: 7 of 7 TRY-guarded inserts, all impossible.
--
-- Where dev's rows actually came from: the Python bootstrap (`setup_*_db.py` / 
-- `seed_real_data.py`), not from here. That is F2-77's thesis — the folder was maintained as
-- a description of a schema someone had in front of them rather than as an ordered history,
-- and nothing ever ran it in order to notice.
--
-- Restored rather than repaired ON PURPOSE: repairing it means inventing ~50 rows of
-- bilingual Saudi demo content, and a fabricated row is worse than an absent one because the
-- file header counts it as delivered. If these tabs need demo data, seed them from a source
-- that knows the schema.
-- ============================================================================================
GO

DECLARE @org_bp BIGINT = 1;

IF NOT EXISTS (SELECT 1 FROM board_pack.packs WHERE organisation_id = @org_bp)
BEGIN
    INSERT INTO board_pack.packs
        (external_id, organisation_id, title_en, title_ar, period_label,
         period_start, period_end, audience, drafted_by,
         -- F2-77: drafted_by is NOT NULL with no DEFAULT and was omitted, so this insert
         -- failed with SQL 515 on the first run of the chain. The value is the one every live
         -- row already carries rather than a new invention.
         risk_summary_json, appetite_breaches_json, audit_engagements_json,
         incidents_json, pdpl_summary_json, ccm_summary_json, sla_summary_json)
    VALUES
    (CONCAT('BP-', NEWID()), @org_bp, N'Q1 2026 GRC Board Pack', N'حقيبة المجلس — الربع الأول ٢٠٢٦', N'2026-Q1',
     '2026-01-01', '2026-03-31', N'BOARD', N'board.secretary@tss.com.sa',
     '{"openHighRisks":12,"overdueCapa":4,"appetiteBreaches":3}',
     '{"breachesQ1":3}',
     '{"engagements":4}',
     '{"active":2,"closedThisQuarter":7}',
     '{"complianceScore":76}',
     '{"complianceScore":82}',
     '{"breachedSlas":2}'),
    (CONCAT('BP-', NEWID()), @org_bp, N'Q4 2025 GRC Board Pack', N'حقيبة المجلس — الربع الرابع ٢٠٢٥', N'2025-Q4',
     '2025-10-01', '2025-12-31', N'BOARD', N'board.secretary@tss.com.sa',
     '{"openHighRisks":15,"overdueCapa":6,"appetiteBreaches":4}',
     '{"breachesQ4":4}',
     '{"engagements":5}',
     '{"active":3,"closedThisQuarter":9}',
     '{"complianceScore":72}',
     '{"complianceScore":80}',
     '{"breachedSlas":3}'),
    (CONCAT('BP-', NEWID()), @org_bp, N'Q3 2025 GRC Board Pack', N'حقيبة المجلس — الربع الثالث ٢٠٢٥', N'2025-Q3',
     '2025-07-01', '2025-09-30', N'EXEC', N'board.secretary@tss.com.sa',
     '{"openHighRisks":18,"overdueCapa":8,"appetiteBreaches":5}',
     '{"breachesQ3":5}',
     '{"engagements":3}',
     '{"active":4,"closedThisQuarter":11}',
     '{"complianceScore":68}',
     '{"complianceScore":77}',
     '{"breachedSlas":4}'),
    (CONCAT('BP-', NEWID()), @org_bp, N'2025 Annual Risk Committee Report', N'تقرير لجنة المخاطر السنوي ٢٠٢٥', N'2025',
     '2025-01-01', '2025-12-31', N'RISK_COMMITTEE', N'board.secretary@tss.com.sa',
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
