-- ============================================================================
-- TSS GRC Platform - Mock Seed Data
-- Target DB: TSS_GRC (SQL Server)
-- Context: Saudi Arabia / MENA Enterprise GRC
-- Run: sqlcmd -S localhost -U sa -P "tss.123" -d TSS_GRC -i V999__seed_mock_data.sql
-- ============================================================================

USE TSS_GRC;
GO

SET NOCOUNT ON;

-- ============================================================================
-- SECTION 1: Risk Categories
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM risk.risk_categories WHERE name_en = 'Information Security' AND organisation_id = 1)
    INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_by, created_at)
    VALUES (1, N'Information Security', N'أمن المعلومات',
            N'Risks related to cybersecurity, data protection, and information assets',
            1, 0, 1, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM risk.risk_categories WHERE name_en = 'Physical Security' AND organisation_id = 1)
    INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_by, created_at)
    VALUES (1, N'Physical Security', N'الأمن المادي',
            N'Risks related to physical access, premises, and asset protection',
            1, 0, 1, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM risk.risk_categories WHERE name_en = 'Legal & Compliance' AND organisation_id = 1)
    INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_by, created_at)
    VALUES (1, N'Legal & Compliance', N'القانوني والامتثال',
            N'Risks arising from regulatory requirements, laws, and compliance obligations',
            1, 0, 1, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM risk.risk_categories WHERE name_en = 'Financial' AND organisation_id = 1)
    INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_by, created_at)
    VALUES (1, N'Financial', N'المالي',
            N'Risks related to financial reporting, liquidity, and financial fraud',
            1, 0, 1, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM risk.risk_categories WHERE name_en = 'Operational' AND organisation_id = 1)
    INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_by, created_at)
    VALUES (1, N'Operational', N'التشغيلي',
            N'Risks arising from failed internal processes, people, or systems',
            1, 0, 1, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM risk.risk_categories WHERE name_en = 'Human Resources' AND organisation_id = 1)
    INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_by, created_at)
    VALUES (1, N'Human Resources', N'الموارد البشرية',
            N'Risks related to workforce, talent retention, and HR practices',
            1, 0, 1, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM risk.risk_categories WHERE name_en = 'Technology' AND organisation_id = 1)
    INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_by, created_at)
    VALUES (1, N'Technology', N'التقنية',
            N'Risks related to IT infrastructure, system failures, and digital transformation',
            1, 0, 1, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM risk.risk_categories WHERE name_en = 'Strategic' AND organisation_id = 1)
    INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_by, created_at)
    VALUES (1, N'Strategic', N'الاستراتيجي',
            N'Risks that affect the long-term strategic direction and objectives of the organisation',
            1, 0, 1, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM risk.risk_categories WHERE name_en = 'Third Party & Vendor' AND organisation_id = 1)
    INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_by, created_at)
    VALUES (1, N'Third Party & Vendor', N'الطرف الثالث والموردون',
            N'Risks associated with outsourcing, vendor relationships, and supply chain',
            1, 0, 1, SYSUTCDATETIME());

IF NOT EXISTS (SELECT 1 FROM risk.risk_categories WHERE name_en = 'Reputational' AND organisation_id = 1)
    INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_by, created_at)
    VALUES (1, N'Reputational', N'السمعة',
            N'Risks that may damage the organisation''s public image and brand',
            1, 0, 1, SYSUTCDATETIME());

PRINT 'Risk categories seeded.';
GO

-- ============================================================================
-- SECTION 2: Additional Users
-- BCrypt hash for "Admin@12345": $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- ============================================================================

-- Risk Manager
IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'rmanager')
    INSERT INTO iam.users (organisation_id, username, email, password_hash, full_name_en, full_name_ar,
                           job_title_en, preferred_locale, is_active, is_locked, failed_login_attempts,
                           must_change_password, mfa_enabled, is_deleted, created_by, created_at)
    VALUES (1, N'rmanager', N'rmanager@tss-grc.com',
            N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
            N'Khalid Al-Rashidi', N'خالد الرشيدي',
            N'Risk Manager', N'ar',
            1, 0, 0, 0, 0, 0, 1, SYSUTCDATETIME());

-- Compliance Officer
IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'compliance')
    INSERT INTO iam.users (organisation_id, username, email, password_hash, full_name_en, full_name_ar,
                           job_title_en, preferred_locale, is_active, is_locked, failed_login_attempts,
                           must_change_password, mfa_enabled, is_deleted, created_by, created_at)
    VALUES (1, N'compliance', N'compliance@tss-grc.com',
            N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
            N'Noura Al-Zahrani', N'نورة الزهراني',
            N'Compliance Officer', N'ar',
            1, 0, 0, 0, 0, 0, 1, SYSUTCDATETIME());

-- Internal Auditor
IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'auditor')
    INSERT INTO iam.users (organisation_id, username, email, password_hash, full_name_en, full_name_ar,
                           job_title_en, preferred_locale, is_active, is_locked, failed_login_attempts,
                           must_change_password, mfa_enabled, is_deleted, created_by, created_at)
    VALUES (1, N'auditor', N'auditor@tss-grc.com',
            N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
            N'Fahad Al-Otaibi', N'فهد العتيبي',
            N'Internal Auditor', N'en',
            1, 0, 0, 0, 0, 0, 1, SYSUTCDATETIME());

-- Risk Analyst
IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'ranalyst')
    INSERT INTO iam.users (organisation_id, username, email, password_hash, full_name_en, full_name_ar,
                           job_title_en, preferred_locale, is_active, is_locked, failed_login_attempts,
                           must_change_password, mfa_enabled, is_deleted, created_by, created_at)
    VALUES (1, N'ranalyst', N'ranalyst@tss-grc.com',
            N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
            N'Sara Al-Ghamdi', N'سارة الغامدي',
            N'Risk Analyst', N'ar',
            1, 0, 0, 0, 0, 0, 1, SYSUTCDATETIME());

-- Viewer
IF NOT EXISTS (SELECT 1 FROM iam.users WHERE username = 'viewer')
    INSERT INTO iam.users (organisation_id, username, email, password_hash, full_name_en, full_name_ar,
                           job_title_en, preferred_locale, is_active, is_locked, failed_login_attempts,
                           must_change_password, mfa_enabled, is_deleted, created_by, created_at)
    VALUES (1, N'viewer', N'viewer@tss-grc.com',
            N'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
            N'Mohammed Al-Harbi', N'محمد الحربي',
            N'IT Governance Analyst', N'ar',
            1, 0, 0, 0, 0, 0, 1, SYSUTCDATETIME());

PRINT 'Additional users seeded.';
GO

-- ============================================================================
-- SECTION 3: Assign roles to new users
-- ============================================================================

-- rmanager → RISK_MANAGER (role_id=3)
IF NOT EXISTS (SELECT 1 FROM iam.user_roles WHERE user_id = (SELECT id FROM iam.users WHERE username='rmanager') AND role_id = 3)
    INSERT INTO iam.user_roles (user_id, role_id, assigned_at, assigned_by)
    VALUES ((SELECT id FROM iam.users WHERE username='rmanager'), 3, SYSUTCDATETIME(), 1);

-- compliance → COMPLIANCE_OFFICER (role_id=5)
IF NOT EXISTS (SELECT 1 FROM iam.user_roles WHERE user_id = (SELECT id FROM iam.users WHERE username='compliance') AND role_id = 5)
    INSERT INTO iam.user_roles (user_id, role_id, assigned_at, assigned_by)
    VALUES ((SELECT id FROM iam.users WHERE username='compliance'), 5, SYSUTCDATETIME(), 1);

-- auditor → INTERNAL_AUDITOR (role_id=6)
IF NOT EXISTS (SELECT 1 FROM iam.user_roles WHERE user_id = (SELECT id FROM iam.users WHERE username='auditor') AND role_id = 6)
    INSERT INTO iam.user_roles (user_id, role_id, assigned_at, assigned_by)
    VALUES ((SELECT id FROM iam.users WHERE username='auditor'), 6, SYSUTCDATETIME(), 1);

-- ranalyst → RISK_ANALYST (role_id=4)
IF NOT EXISTS (SELECT 1 FROM iam.user_roles WHERE user_id = (SELECT id FROM iam.users WHERE username='ranalyst') AND role_id = 4)
    INSERT INTO iam.user_roles (user_id, role_id, assigned_at, assigned_by)
    VALUES ((SELECT id FROM iam.users WHERE username='ranalyst'), 4, SYSUTCDATETIME(), 1);

-- viewer → VIEWER (role_id=9)
IF NOT EXISTS (SELECT 1 FROM iam.user_roles WHERE user_id = (SELECT id FROM iam.users WHERE username='viewer') AND role_id = 9)
    INSERT INTO iam.user_roles (user_id, role_id, assigned_at, assigned_by)
    VALUES ((SELECT id FROM iam.users WHERE username='viewer'), 9, SYSUTCDATETIME(), 1);

PRINT 'User roles assigned.';
GO

-- ============================================================================
-- SECTION 4: Risks (using real column names from live schema)
-- Statuses: OPEN, UNDER_ASSESSMENT, TREATMENT_IN_PROGRESS, ACCEPTED, CLOSED, TRANSFERRED
-- Levels: CRITICAL, HIGH, MEDIUM, LOW
-- ============================================================================

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

-- Helper: get category IDs
DECLARE @catInfoSec    BIGINT = (SELECT id FROM risk.risk_categories WHERE name_en='Information Security'  AND organisation_id=1);
DECLARE @catPhysical   BIGINT = (SELECT id FROM risk.risk_categories WHERE name_en='Physical Security'     AND organisation_id=1);
DECLARE @catLegal      BIGINT = (SELECT id FROM risk.risk_categories WHERE name_en='Legal & Compliance'    AND organisation_id=1);
DECLARE @catFinancial  BIGINT = (SELECT id FROM risk.risk_categories WHERE name_en='Financial'             AND organisation_id=1);
DECLARE @catOps        BIGINT = (SELECT id FROM risk.risk_categories WHERE name_en='Operational'           AND organisation_id=1);
DECLARE @catHR         BIGINT = (SELECT id FROM risk.risk_categories WHERE name_en='Human Resources'       AND organisation_id=1);
DECLARE @catTech       BIGINT = (SELECT id FROM risk.risk_categories WHERE name_en='Technology'            AND organisation_id=1);
DECLARE @catStrategic  BIGINT = (SELECT id FROM risk.risk_categories WHERE name_en='Strategic'             AND organisation_id=1);
DECLARE @catVendor     BIGINT = (SELECT id FROM risk.risk_categories WHERE name_en='Third Party & Vendor'  AND organisation_id=1);
DECLARE @catRepute     BIGINT = (SELECT id FROM risk.risk_categories WHERE name_en='Reputational'          AND organisation_id=1);

DECLARE @adminId    BIGINT = 1;
DECLARE @rmanagerId BIGINT = (SELECT id FROM iam.users WHERE username='rmanager');
DECLARE @analystId  BIGINT = (SELECT id FROM iam.users WHERE username='ranalyst');
DECLARE @auditorId  BIGINT = (SELECT id FROM iam.users WHERE username='auditor');

-- Risk 1: Ransomware Attack (CRITICAL - 5x5=25)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Ransomware Attack on Core Infrastructure' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catInfoSec,
        N'Ransomware Attack on Core Infrastructure',
        N'هجوم برامج الفدية على البنية التحتية الأساسية',
        N'Risk of ransomware encrypting critical systems including ERP, HRMS, and data centres, resulting in operational shutdown and data loss.',
        N'ERM', @rmanagerId, N'OPEN', 5, 5, 3, 4,
        '2026-01-15', '2026-07-15', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 2: Data Privacy Breach (CRITICAL - 4x5=20)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Personal Data Breach - Customer PII' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catInfoSec,
        N'Personal Data Breach - Customer PII',
        N'اختراق البيانات الشخصية - بيانات هوية العملاء',
        N'Unauthorised disclosure of customer personally identifiable information due to insecure APIs or misconfigured cloud storage, triggering PDPL compliance breach.',
        N'ERM', @rmanagerId, N'UNDER_ASSESSMENT', 4, 5, 2, 4,
        '2026-01-20', '2026-06-30', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 3: Regulatory Non-Compliance (HIGH - 3x5=15)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Non-Compliance with NCA ECC Controls' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catLegal,
        N'Non-Compliance with NCA ECC Controls',
        N'عدم الامتثال لضوابط الأمن السيبراني للهيئة الوطنية للأمن السيبراني',
        N'Failure to implement mandatory Essential Cybersecurity Controls (ECC) as required by NCA, resulting in regulatory sanctions and potential licence suspension.',
        N'ERM', @auditorId, N'UNDER_ASSESSMENT', 3, 5, 2, 4,
        '2026-02-01', '2026-09-01', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 4: Third-Party Vendor Data Exposure (HIGH - 4x4=16)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Third-Party Vendor Data Exposure' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catVendor,
        N'Third-Party Vendor Data Exposure',
        N'تسريب البيانات من خلال موردين خارجيين',
        N'Vendors with access to TSS systems may expose sensitive data due to inadequate security controls or insider threats within the vendor organisation.',
        N'ERM', @rmanagerId, N'OPEN', 4, 4, 2, 3,
        '2026-02-10', '2026-08-10', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 5: Business Continuity Failure (HIGH - 3x5=15)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Core System Downtime - Business Continuity Failure' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catOps,
        N'Core System Downtime - Business Continuity Failure',
        N'تعطل الأنظمة الأساسية - فشل استمرارية الأعمال',
        N'Extended outage of primary ERP or banking systems due to infrastructure failure, natural disaster, or cyber incident with no tested recovery plan in place.',
        N'ERM', @analystId, N'UNDER_ASSESSMENT', 3, 5, 2, 3,
        '2026-02-15', '2026-09-15', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 6: Phishing and Social Engineering (HIGH - 5x3=15)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Phishing and Social Engineering Attacks' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catInfoSec,
        N'Phishing and Social Engineering Attacks',
        N'هجمات التصيد الاحتيالي والهندسة الاجتماعية',
        N'Employees targeted by sophisticated spear-phishing campaigns leading to credential compromise, financial fraud, or ransomware installation.',
        N'ERM', @rmanagerId, N'OPEN', 5, 3, 3, 2,
        '2026-02-20', '2026-08-20', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 7: Insider Threat (MEDIUM - 3x4=12)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Privileged Insider Threat - Data Exfiltration' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catHR,
        N'Privileged Insider Threat - Data Exfiltration',
        N'تهديد داخلي من المستخدمين المميزين - تسريب البيانات',
        N'Malicious or negligent insiders with privileged access exfiltrating sensitive data or sabotaging critical systems.',
        N'ERM', @analystId, N'UNDER_ASSESSMENT', 3, 4, 2, 3,
        '2026-03-01', '2026-09-01', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 8: Cloud Misconfiguration (MEDIUM - 4x3=12)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Cloud Infrastructure Misconfiguration' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catTech,
        N'Cloud Infrastructure Misconfiguration',
        N'الإعداد الخاطئ للبنية التحتية السحابية',
        N'Misconfigured cloud storage buckets or IAM policies exposing sensitive data to the internet, leading to regulatory penalties and reputational damage.',
        N'ERM', @analystId, N'OPEN', 4, 3, 2, 2,
        '2026-03-05', '2026-09-05', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 9: Financial Fraud - Payment Diversion (MEDIUM - 3x4=12)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Business Email Compromise - Payment Diversion' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catFinancial,
        N'Business Email Compromise - Payment Diversion',
        N'الاحتيال عبر البريد الإلكتروني التجاري - تحويل المدفوعات',
        N'Attackers impersonating senior executives to redirect payments to fraudulent accounts using compromised or spoofed email accounts.',
        N'ERM', @auditorId, N'UNDER_ASSESSMENT', 3, 4, 2, 2,
        '2026-03-10', '2026-09-10', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 10: Key Personnel Dependency (MEDIUM - 3x3=9)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Key Personnel Dependency and Knowledge Concentration' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catHR,
        N'Key Personnel Dependency and Knowledge Concentration',
        N'الاعتماد على الأفراد الرئيسيين وتركيز المعرفة',
        N'Critical operational knowledge concentrated in a few individuals; departure of key staff would cause significant service disruption.',
        N'ERM', @rmanagerId, N'ACCEPTED', 3, 3, 2, 2,
        '2026-03-15', '2026-10-15', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 11: PDPL Non-Compliance (MEDIUM - 3x3=9)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Non-Compliance with Saudi PDPL' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catLegal,
        N'Non-Compliance with Saudi PDPL',
        N'عدم الامتثال لنظام حماية البيانات الشخصية السعودي',
        N'Failure to meet requirements of the Personal Data Protection Law (PDPL) issued by SDAIA, resulting in fines up to SAR 5 million.',
        N'ERM', @auditorId, N'UNDER_ASSESSMENT', 3, 3, 2, 2,
        '2026-03-20', '2026-10-20', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 12: Patch Management Failure (LOW - 3x2=6)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Unpatched Systems and Vulnerability Exposure' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catTech,
        N'Unpatched Systems and Vulnerability Exposure',
        N'الأنظمة غير المُرقَّعة والتعرض للثغرات الأمنية',
        N'Delayed application of security patches creating exploitable vulnerabilities in production systems.',
        N'ERM', @analystId, N'OPEN', 3, 2, 2, 1,
        '2026-03-25', '2026-09-25', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 13: Reputational Damage - Social Media (LOW - 2x3=6)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Reputational Damage from Social Media Incidents' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catRepute,
        N'Reputational Damage from Social Media Incidents',
        N'الضرر بالسمعة من حوادث وسائل التواصل الاجتماعي',
        N'Negative social media campaigns or data breach disclosures damaging brand perception and customer trust.',
        N'ERM', @rmanagerId, N'ACCEPTED', 2, 3, 1, 2,
        '2026-04-01', '2026-10-01', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 14: Physical Perimeter Breach (LOW - 2x3=6)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Unauthorised Physical Access to Data Centre' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catPhysical,
        N'Unauthorised Physical Access to Data Centre',
        N'الوصول المادي غير المصرح به إلى مركز البيانات',
        N'Physical intrusion into the data centre resulting in hardware tampering, theft of storage media, or destruction of critical infrastructure.',
        N'ERM', @analystId, N'CLOSED', 2, 3, 1, 2,
        '2025-12-01', '2026-06-01', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

-- Risk 15: Strategic Objective Misalignment (LOW - 2x2=4)
IF NOT EXISTS (SELECT 1 FROM risk.risks WHERE title_en='Digital Transformation Strategy Misalignment' AND organisation_id=1)
    INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
        source_module, owner_id, status, inherent_likelihood, inherent_impact,
        residual_likelihood, residual_impact,
        identified_date, review_date, is_deleted, created_by, created_at, updated_at)
    VALUES (1, @catStrategic,
        N'Digital Transformation Strategy Misalignment',
        N'عدم التوافق في استراتيجية التحول الرقمي',
        N'IT and digital transformation investments not aligned with organisational strategic goals under Vision 2030, resulting in wasted resources.',
        N'ERM', @rmanagerId, N'UNDER_ASSESSMENT', 2, 2, 1, 2,
        '2026-04-01', '2026-12-01', 0, 1, SYSUTCDATETIME(), SYSUTCDATETIME());

PRINT 'Risks seeded.';
GO

-- ============================================================================
-- Final count check
-- ============================================================================
SELECT 'risk_categories' AS entity, COUNT(*) AS total FROM risk.risk_categories WHERE is_deleted=0
UNION ALL
SELECT 'risks', COUNT(*) FROM risk.risks WHERE is_deleted=0
UNION ALL
SELECT 'users', COUNT(*) FROM iam.users WHERE is_deleted=0;
GO

PRINT 'Seed data script completed successfully.';
GO
