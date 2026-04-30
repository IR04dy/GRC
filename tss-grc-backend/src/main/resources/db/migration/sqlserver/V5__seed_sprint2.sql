-- ============================================================================
-- TSS GRC Platform — Sprint 2: Seed Data for Compliance & Incidents
-- For dev/demo use only
-- ============================================================================

USE TSS_GRC;
GO

SET NOCOUNT ON;

-- ── Sample Controls ───────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM compliance.controls WHERE organisation_id = 1 AND code = 'ISO27001-A.5.1')
INSERT INTO compliance.controls (organisation_id, code, name_en, name_ar, framework, domain, maturity_level, status, owner, description, is_deleted, created_by, created_at)
VALUES
(1, 'ISO27001-A.5.1',  'Information Security Policies',             N'سياسات أمن المعلومات',          'ISO_27001', 'Governance',         4, 'IMPLEMENTED', 'CISO',              N'Management direction for information security', 0, 1, SYSUTCDATETIME()),
(1, 'ISO27001-A.6.1',  'Organisation of Information Security',      N'تنظيم أمن المعلومات',           'ISO_27001', 'Organisation',       3, 'IMPLEMENTED', 'CISO',              N'Internal organisation responsibilities', 0, 1, SYSUTCDATETIME()),
(1, 'ISO27001-A.8.1',  'Asset Management',                          N'إدارة الأصول',                 'ISO_27001', 'Asset Management',   3, 'PARTIAL',      'IT Manager',        N'Identification and classification of assets', 0, 1, SYSUTCDATETIME()),
(1, 'ISO27001-A.9.1',  'Access Control Policy',                     N'سياسة التحكم في الوصول',        'ISO_27001', 'Access Control',     4, 'IMPLEMENTED', 'IT Manager',        N'Business requirements of access control', 0, 1, SYSUTCDATETIME()),
(1, 'ISO27001-A.12.1', 'Operational Procedures and Responsibilities',N'إجراءات التشغيل والمسؤوليات',  'ISO_27001', 'Operations',         3, 'PARTIAL',      'IT Manager',        N'Documented operating procedures', 0, 1, SYSUTCDATETIME()),
(1, 'ISO27001-A.16.1', 'Management of Information Security Incidents',N'إدارة حوادث أمن المعلومات',  'ISO_27001', 'Incidents',          2, 'PLANNED',      'CISO',              N'Incident management responsibilities', 0, 1, SYSUTCDATETIME()),
(1, 'NCA-ECC-1-1',     'Cybersecurity Governance',                  N'حوكمة الأمن السيبراني',        'NCA_ECC',   'Governance',         3, 'IMPLEMENTED', 'CISO',              N'Governance framework for cybersecurity', 0, 1, SYSUTCDATETIME()),
(1, 'NCA-ECC-1-2',     'Cybersecurity Policy',                      N'سياسة الأمن السيبراني',        'NCA_ECC',   'Policy',             4, 'IMPLEMENTED', 'CISO',              N'Approved cybersecurity policy', 0, 1, SYSUTCDATETIME()),
(1, 'NCA-ECC-2-1',     'Cybersecurity Risk Management',             N'إدارة مخاطر الأمن السيبراني',  'NCA_ECC',   'Risk',               3, 'PARTIAL',      'Risk Manager',      N'Risk identification and assessment', 0, 1, SYSUTCDATETIME()),
(1, 'PDPL-ART-5',      'Lawful Basis for Processing',               N'الأساس القانوني للمعالجة',      'PDPL',      'Legal Basis',        3, 'IMPLEMENTED', 'Privacy Officer',   N'Valid legal basis for personal data processing', 0, 1, SYSUTCDATETIME()),
(1, 'PDPL-ART-15',     'Data Subject Rights',                       N'حقوق صاحب البيانات',           'PDPL',      'Rights',             2, 'PARTIAL',      'Privacy Officer',   N'DSR handling procedures', 0, 1, SYSUTCDATETIME()),
(1, 'ISO22301-8.2',    'Business Impact Analysis',                  N'تحليل أثر الأعمال',            'ISO_22301', 'BIA',                2, 'PLANNED',      'BCM Manager',       N'BIA methodology and results', 0, 1, SYSUTCDATETIME());
GO

-- ── Sample Assessments ────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM compliance.assessments WHERE organisation_id = 1 AND framework = 'ISO_27001')
INSERT INTO compliance.assessments (organisation_id, framework, period, auditor, score, status, assessment_date, notes, is_deleted, created_by, created_at)
VALUES
(1, 'ISO_27001', 'Q1 2026', N'External Auditor — BSI', 76.5, 'COMPLETED', '2026-03-15', N'Satisfactory overall with minor gaps in Annex A domains', 0, 1, SYSUTCDATETIME()),
(1, 'NCA_ECC',   'Q1 2026', N'Internal Audit Team',   82.0, 'COMPLETED', '2026-03-20', N'Strong governance, improvement needed in monitoring', 0, 1, SYSUTCDATETIME()),
(1, 'PDPL',      'Q1 2026', N'Privacy Officer',        68.0, 'IN_PROGRESS','2026-04-01', N'DSR process under development', 0, 1, SYSUTCDATETIME()),
(1, 'ISO_22301', 'Q1 2026', N'BCM Manager',            55.0, 'PLANNED',   NULL,         N'BIA workshop scheduled for April', 0, 1, SYSUTCDATETIME());
GO

-- ── Sample Findings ───────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM compliance.findings WHERE organisation_id = 1)
INSERT INTO compliance.findings (organisation_id, title, severity, owner, capa, due_date, status, is_deleted, created_by, created_at)
VALUES
(1, N'Incomplete Asset Inventory',                  'HIGH',     N'IT Manager',      N'Complete asset discovery using automated scanner',     '2026-05-30', 'OPEN',        0, 1, SYSUTCDATETIME()),
(1, N'Missing Incident Response Procedures',        'CRITICAL',  N'CISO',           N'Draft and approve IR playbook by end of April',       '2026-04-30', 'IN_PROGRESS', 0, 1, SYSUTCDATETIME()),
(1, N'BIA Not Completed for Critical Processes',    'HIGH',     N'BCM Manager',     N'Conduct BIA workshop for top 10 critical processes',  '2026-05-15', 'OPEN',        0, 1, SYSUTCDATETIME()),
(1, N'DSR Response Exceeding 30-Day Limit',        'MEDIUM',   N'Privacy Officer',  N'Implement DSR tracking system with SLA alerts',       '2026-04-25', 'OPEN',        0, 1, SYSUTCDATETIME()),
(1, N'Weak Password Policy Enforcement',           'HIGH',     N'IT Manager',       N'Enforce MFA for all admin accounts immediately',      '2026-04-20', 'REMEDIATED',  0, 1, SYSUTCDATETIME());
GO

-- ── Sample Incidents ──────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM cyber.incidents WHERE organisation_id = 1)
INSERT INTO cyber.incidents (organisation_id, title, severity, category, status, assignee, discovery_date, description, is_deleted, created_by, created_at)
VALUES
(1, N'Phishing Email Campaign Detected',            'HIGH',     'CYBER',           'INVESTIGATING', N'riskmanager', '2026-04-05', N'Multiple employees received targeted phishing emails. No successful compromise detected.', 0, 1, SYSUTCDATETIME()),
(1, N'Unauthorized Access Attempt on VPN',          'CRITICAL',  'ACCESS_CONTROL',  'OPEN',          N'admin',       '2026-04-07', N'Brute-force attack detected on VPN gateway from external IP. IP blocked.', 0, 1, SYSUTCDATETIME()),
(1, N'Malware on Workstation WS-042',               'HIGH',     'CYBER',           'RESOLVED',      N'riskmanager', '2026-03-28', N'Malware detected and removed. Endpoint re-imaged. No data exfiltration.', 0, 1, SYSUTCDATETIME()),
(1, N'Unencrypted Customer Data in Shared Drive',   'MEDIUM',   'DATA_BREACH',     'INVESTIGATING', N'auditor',     '2026-04-02', N'Unencrypted personal data found in shared drive. Access restricted pending review.', 0, 1, SYSUTCDATETIME()),
(1, N'Web Application SQL Injection Attempt',       'HIGH',     'CYBER',           'RESOLVED',      N'admin',       '2026-03-20', N'WAF blocked SQL injection attempt. Patch applied to vulnerable endpoint.', 0, 1, SYSUTCDATETIME());
GO

PRINT 'Sprint 2: Seed data inserted successfully.';
GO
