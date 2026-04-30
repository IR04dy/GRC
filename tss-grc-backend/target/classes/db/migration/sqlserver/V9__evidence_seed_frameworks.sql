-- ============================================================
-- V9 — Evidence Module: NCA Framework Seed Data
-- Frameworks: ECC-2:2024, DCC-1:2022, CSCC-1:2020, Teleworking
-- ============================================================

-- ── ECC-2:2024 ───────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM evidence.frameworks WHERE code = 'ECC-2:2024')
BEGIN
    INSERT INTO evidence.frameworks (code, name_en, name_ar, version, description_en, issuer)
    VALUES ('ECC-2:2024',
            'Essential Cybersecurity Controls',
            'الضوابط الأساسية للأمن السيبراني',
            '2024',
            'Saudi National Cybersecurity Authority - Essential Cybersecurity Controls version 2 (2024)',
            'NCA');
END
GO

DECLARE @eccId BIGINT = (SELECT id FROM evidence.frameworks WHERE code = 'ECC-2:2024');

-- ECC Controls
IF NOT EXISTS (SELECT 1 FROM evidence.controls WHERE framework_id = @eccId AND control_code = 'ECC-1')
BEGIN
    INSERT INTO evidence.controls (framework_id, control_code, name_en, name_ar, description_en) VALUES
    (@eccId, 'ECC-1', 'Cybersecurity Governance', 'حوكمة الأمن السيبراني', 'Establish cybersecurity governance structures, roles, and responsibilities'),
    (@eccId, 'ECC-2', 'Cybersecurity Risk Management', 'إدارة مخاطر الأمن السيبراني', 'Identify, assess, and treat cybersecurity risks'),
    (@eccId, 'ECC-3', 'Cybersecurity Operations & Technology', 'عمليات وتقنيات الأمن السيبراني', 'Implement and operate cybersecurity technical controls'),
    (@eccId, 'ECC-4', 'Third-Party Cybersecurity', 'الأمن السيبراني للجهات الخارجية', 'Manage cybersecurity risks from third-party relationships'),
    (@eccId, 'ECC-5', 'Cybersecurity Resilience', 'متانة الأمن السيبراني', 'Ensure organizational resilience against cybersecurity incidents');
END
GO

DECLARE @eccId2 BIGINT = (SELECT id FROM evidence.frameworks WHERE code = 'ECC-2:2024');

-- ECC-1 Sub-controls
IF NOT EXISTS (SELECT 1 FROM evidence.sub_controls WHERE sub_control_code = 'ECC-1-1'
               AND control_id = (SELECT id FROM evidence.controls WHERE framework_id = @eccId2 AND control_code = 'ECC-1'))
BEGIN
    DECLARE @ecc1 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @eccId2 AND control_code = 'ECC-1');
    DECLARE @ecc2 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @eccId2 AND control_code = 'ECC-2');
    DECLARE @ecc3 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @eccId2 AND control_code = 'ECC-3');
    DECLARE @ecc4 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @eccId2 AND control_code = 'ECC-4');
    DECLARE @ecc5 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @eccId2 AND control_code = 'ECC-5');

    INSERT INTO evidence.sub_controls (control_id, sub_control_code, name_en, name_ar, evidence_guidance) VALUES
    -- ECC-1 Governance
    (@ecc1, 'ECC-1-1', 'Cybersecurity Strategy & Policy', 'استراتيجية وسياسة الأمن السيبراني',
     'Approved cybersecurity strategy document, Board approval minutes, Policy publication record'),
    (@ecc1, 'ECC-1-2', 'Cybersecurity Roles & Responsibilities', 'أدوار ومسؤوليات الأمن السيبراني',
     'Organizational chart with CISO role, Job descriptions, RACI matrix'),
    (@ecc1, 'ECC-1-3', 'Cybersecurity Awareness Program', 'برنامج التوعية بالأمن السيبراني',
     'Training completion records, Awareness campaign materials, Assessment scores'),
    -- ECC-2 Risk
    (@ecc2, 'ECC-2-1', 'Cybersecurity Risk Assessment', 'تقييم مخاطر الأمن السيبراني',
     'Risk assessment report, Risk register, Methodology documentation'),
    (@ecc2, 'ECC-2-2', 'Cybersecurity Risk Treatment', 'معالجة مخاطر الأمن السيبراني',
     'Risk treatment plans, Acceptance records, Residual risk sign-off'),
    -- ECC-3 Operations
    (@ecc3, 'ECC-3-1', 'Asset Management', 'إدارة الأصول',
     'Asset inventory, Classification records, Asset owner assignments'),
    (@ecc3, 'ECC-3-2', 'Vulnerability Management', 'إدارة الثغرات',
     'Vulnerability scan reports, Patch records, Remediation tracking'),
    (@ecc3, 'ECC-3-3', 'Access Control', 'التحكم في الوصول',
     'IAM policy, Access review records, Privileged access logs'),
    -- ECC-4 Third Party
    (@ecc4, 'ECC-4-1', 'Vendor Security Assessment', 'تقييم أمن الموردين',
     'Vendor questionnaire results, Contract security clauses, Assessment reports'),
    (@ecc4, 'ECC-4-2', 'Third-Party Contract Management', 'إدارة عقود الجهات الخارجية',
     'Signed contracts with security requirements, NDA records, SLA documents'),
    -- ECC-5 Resilience
    (@ecc5, 'ECC-5-1', 'Incident Response Plan', 'خطة الاستجابة للحوادث',
     'Approved IRP document, Test exercise records, Incident logs'),
    (@ecc5, 'ECC-5-2', 'Business Continuity & DR', 'استمرارية الأعمال والتعافي',
     'BCP/DRP documents, Test results, RTO/RPO measurements');
END
GO

-- ── DCC-1:2022 ───────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM evidence.frameworks WHERE code = 'DCC-1:2022')
BEGIN
    INSERT INTO evidence.frameworks (code, name_en, name_ar, version, description_en, issuer)
    VALUES ('DCC-1:2022',
            'Data Cybersecurity Controls',
            'ضوابط البيانات للأمن السيبراني',
            '2022',
            'Saudi National Cybersecurity Authority - Data Cybersecurity Controls',
            'NCA');
END
GO

DECLARE @dccId BIGINT = (SELECT id FROM evidence.frameworks WHERE code = 'DCC-1:2022');

IF NOT EXISTS (SELECT 1 FROM evidence.controls WHERE framework_id = @dccId AND control_code = 'DCC-1')
BEGIN
    INSERT INTO evidence.controls (framework_id, control_code, name_en, name_ar, description_en) VALUES
    (@dccId, 'DCC-1', 'Data Governance', 'حوكمة البيانات', 'Establish data governance policies and ownership'),
    (@dccId, 'DCC-2', 'Data Classification', 'تصنيف البيانات', 'Classify data assets based on sensitivity and criticality'),
    (@dccId, 'DCC-3', 'Data Protection', 'حماية البيانات', 'Apply data protection controls based on classification');
END
GO

DECLARE @dccId2 BIGINT = (SELECT id FROM evidence.frameworks WHERE code = 'DCC-1:2022');

IF NOT EXISTS (SELECT 1 FROM evidence.sub_controls WHERE sub_control_code = 'DCC-1-1'
               AND control_id = (SELECT id FROM evidence.controls WHERE framework_id = @dccId2 AND control_code = 'DCC-1'))
BEGIN
    DECLARE @dcc1 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @dccId2 AND control_code = 'DCC-1');
    DECLARE @dcc2 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @dccId2 AND control_code = 'DCC-2');
    DECLARE @dcc3 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @dccId2 AND control_code = 'DCC-3');

    INSERT INTO evidence.sub_controls (control_id, sub_control_code, name_en, name_ar, evidence_guidance) VALUES
    (@dcc1, 'DCC-1-1', 'Data Governance Policy', 'سياسة حوكمة البيانات',
     'Approved data governance policy, Data owner assignment records'),
    (@dcc1, 'DCC-1-2', 'Data Inventory', 'جرد البيانات',
     'Data catalog / inventory list, Data flow diagrams'),
    (@dcc2, 'DCC-2-1', 'Classification Framework', 'إطار التصنيف',
     'Data classification scheme document, Labeling guidelines'),
    (@dcc2, 'DCC-2-2', 'Data Labeling & Handling', 'وضع العلامات والتعامل مع البيانات',
     'Labeled data samples, Handling procedure records, Audit logs'),
    (@dcc3, 'DCC-3-1', 'Encryption Controls', 'ضوابط التشفير',
     'Encryption policy, Key management records, Encryption audit logs'),
    (@dcc3, 'DCC-3-2', 'Data Loss Prevention', 'منع فقدان البيانات',
     'DLP policy, DLP tool configuration screenshots, Alert/incident logs');
END
GO

-- ── CSCC-1:2020 ──────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM evidence.frameworks WHERE code = 'CSCC-1:2020')
BEGIN
    INSERT INTO evidence.frameworks (code, name_en, name_ar, version, description_en, issuer)
    VALUES ('CSCC-1:2020',
            'Cloud Cybersecurity Controls',
            'ضوابط الأمن السيبراني للحوسبة السحابية',
            '2020',
            'Saudi National Cybersecurity Authority - Cloud Cybersecurity Controls',
            'NCA');
END
GO

DECLARE @csccId BIGINT = (SELECT id FROM evidence.frameworks WHERE code = 'CSCC-1:2020');

IF NOT EXISTS (SELECT 1 FROM evidence.controls WHERE framework_id = @csccId AND control_code = 'CSC-1')
BEGIN
    INSERT INTO evidence.controls (framework_id, control_code, name_en, name_ar, description_en) VALUES
    (@csccId, 'CSC-1', 'Cloud Governance', 'حوكمة السحابة', 'Establish cloud governance and risk management policies'),
    (@csccId, 'CSC-2', 'Cloud Security Architecture', 'هندسة أمن السحابة', 'Design and implement secure cloud architecture'),
    (@csccId, 'CSC-3', 'Cloud Operations Security', 'أمن عمليات السحابة', 'Secure day-to-day cloud operations');
END
GO

DECLARE @csccId2 BIGINT = (SELECT id FROM evidence.frameworks WHERE code = 'CSCC-1:2020');

IF NOT EXISTS (SELECT 1 FROM evidence.sub_controls WHERE sub_control_code = 'CSC-1-1'
               AND control_id = (SELECT id FROM evidence.controls WHERE framework_id = @csccId2 AND control_code = 'CSC-1'))
BEGIN
    DECLARE @csc1 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @csccId2 AND control_code = 'CSC-1');
    DECLARE @csc2 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @csccId2 AND control_code = 'CSC-2');
    DECLARE @csc3 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @csccId2 AND control_code = 'CSC-3');

    INSERT INTO evidence.sub_controls (control_id, sub_control_code, name_en, name_ar, evidence_guidance) VALUES
    (@csc1, 'CSC-1-1', 'Cloud Usage Policy', 'سياسة استخدام السحابة',
     'Approved cloud policy, Approved cloud service list, Risk acceptance records'),
    (@csc1, 'CSC-1-2', 'Cloud Risk Assessment', 'تقييم مخاطر السحابة',
     'Cloud risk assessment report, CSP security documentation review'),
    (@csc2, 'CSC-2-1', 'Network Segmentation', 'تقسيم الشبكة',
     'Network architecture diagram, VPC/VLAN configuration, Firewall rules'),
    (@csc2, 'CSC-2-2', 'Identity & Access Management', 'إدارة الهوية والوصول',
     'IAM policy, MFA enforcement records, Privilege access review'),
    (@csc3, 'CSC-3-1', 'Security Monitoring', 'الرصد الأمني',
     'SIEM configuration, Alert rules, Monitoring dashboards, Log retention policy'),
    (@csc3, 'CSC-3-2', 'Backup & Recovery', 'النسخ الاحتياطي والتعافي',
     'Backup policy, Recovery test records, RTO/RPO evidence');
END
GO

-- ── Teleworking ───────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM evidence.frameworks WHERE code = 'TW-1:NCA')
BEGIN
    INSERT INTO evidence.frameworks (code, name_en, name_ar, version, description_en, issuer)
    VALUES ('TW-1:NCA',
            'Teleworking Cybersecurity Controls',
            'ضوابط الأمن السيبراني للعمل عن بُعد',
            '1.0',
            'Saudi National Cybersecurity Authority - Cybersecurity Guidelines for Teleworking',
            'NCA');
END
GO

DECLARE @twId BIGINT = (SELECT id FROM evidence.frameworks WHERE code = 'TW-1:NCA');

IF NOT EXISTS (SELECT 1 FROM evidence.controls WHERE framework_id = @twId AND control_code = 'TW-1')
BEGIN
    INSERT INTO evidence.controls (framework_id, control_code, name_en, name_ar, description_en) VALUES
    (@twId, 'TW-1', 'Teleworking Policy & Governance', 'سياسة وحوكمة العمل عن بُعد', 'Establish teleworking governance policies'),
    (@twId, 'TW-2', 'Secure Remote Access', 'الوصول الآمن عن بُعد', 'Implement secure remote access controls'),
    (@twId, 'TW-3', 'Device & Endpoint Security', 'أمن الأجهزة ونقاط النهاية', 'Secure devices used for teleworking');
END
GO

DECLARE @twId2 BIGINT = (SELECT id FROM evidence.frameworks WHERE code = 'TW-1:NCA');

IF NOT EXISTS (SELECT 1 FROM evidence.sub_controls WHERE sub_control_code = 'TW-1-1'
               AND control_id = (SELECT id FROM evidence.controls WHERE framework_id = @twId2 AND control_code = 'TW-1'))
BEGIN
    DECLARE @tw1 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @twId2 AND control_code = 'TW-1');
    DECLARE @tw2 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @twId2 AND control_code = 'TW-2');
    DECLARE @tw3 BIGINT = (SELECT id FROM evidence.controls WHERE framework_id = @twId2 AND control_code = 'TW-3');

    INSERT INTO evidence.sub_controls (control_id, sub_control_code, name_en, name_ar, evidence_guidance) VALUES
    (@tw1, 'TW-1-1', 'Teleworking Policy', 'سياسة العمل عن بُعد',
     'Approved teleworking policy document, Sign-off records, Version history'),
    (@tw1, 'TW-1-2', 'Acceptable Use Policy', 'سياسة الاستخدام المقبول',
     'Signed AUP agreements, Employee acknowledgment forms'),
    (@tw2, 'TW-2-1', 'VPN Controls', 'ضوابط الشبكة الافتراضية الخاصة',
     'VPN configuration documentation, Connection logs, Security configuration audit'),
    (@tw2, 'TW-2-2', 'Multi-Factor Authentication', 'المصادقة متعددة العوامل',
     'MFA enforcement policy, Enrollment records, MFA audit logs'),
    (@tw3, 'TW-3-1', 'Endpoint Protection', 'حماية نقاط النهاية',
     'Antivirus deployment records, EDR configuration, Patch compliance reports'),
    (@tw3, 'TW-3-2', 'BYOD Controls', 'ضوابط الأجهزة الشخصية',
     'BYOD policy, MDM enrollment records, Device compliance reports');
END
GO

PRINT 'V9 — NCA framework seed data inserted successfully.';
GO
