-- V259 — SOC Operations role model (epic SOC-A)
--
-- FINDING (review C-CRIT-2): every socops @PreAuthorize gates on hasAnyRole(...)
-- but NONE of the referenced SOC roles exist in iam.roles — so the whole module
-- was effectively PLATFORM_ADMIN-only, and machine-ingest webhooks (SIEM/scanner/
-- DLP/…) could only authenticate as full admin. This seeds the 22 missing roles
-- referenced by module/socops so least-privilege SOC operation becomes possible.
--
-- Forward-only + idempotent: each INSERT is guarded by NOT EXISTS on role_name,
-- so re-running matches 0 rows. (Dev is Flyway-off; hand-applied via pyodbc.)
-- Roles already present and reused by socops (PLATFORM_ADMIN, RISK_MANAGER,
-- COMPLIANCE_OFFICER) are intentionally left untouched.

-- 1) Seed the missing SOC roles (human + machine-ingest service roles).
INSERT INTO iam.roles (external_id, organisation_id, role_name, display_name_en, display_name_ar, description_en, is_system_role)
SELECT NEWID(), 1, v.rn, v.en, v.ar, v.de, 1
FROM (VALUES
  ('CISO',            N'Chief Information Security Officer', N'رئيس أمن المعلومات',            N'Owns the cybersecurity programme, SOC and incident response.'),
  ('SECURITY_ANALYST',N'Security Analyst (SOC)',            N'محلل أمن سيبراني (مركز العمليات)', N'SOC L1/L2 analyst: triage, investigate, resolve events.'),
  ('FORENSICS_LEAD',  N'Digital Forensics Lead',            N'رئيس التحليل الجنائي الرقمي',    N'Owns forensic acquisition, chain-of-custody and analysis.'),
  ('IR_LEAD',         N'Incident Response Lead',            N'رئيس الاستجابة للحوادث',         N'Leads incident response and IR playbook execution.'),
  ('APPSEC_LEAD',     N'Application Security Lead',          N'رئيس أمن التطبيقات',            N'Owns AppSec findings (SAST/DAST) remediation.'),
  ('DLP_REVIEWER',    N'DLP Reviewer',                      N'مراجع منع تسرّب البيانات',       N'Reviews and dispositions DLP events.'),
  ('IAM_LEAD',        N'Identity & Access Lead',             N'رئيس إدارة الهوية والوصول',      N'Owns joiner/mover/leaver and privileged-access reviews.'),
  ('MA_LEAD',         N'M&A Cyber Due-Diligence Lead',       N'رئيس العناية الواجبة السيبرانية للاندماج', N'Owns M&A cyber due-diligence transitions.'),
  ('OT_LEAD',         N'OT / ICS Security Lead',             N'رئيس أمن التقنية التشغيلية',     N'Owns operational-technology / ICS asset security.'),
  ('BRAND_MONITORING',N'Brand Protection Analyst',           N'محلل حماية العلامة التجارية',    N'Handles brand-impersonation and takedown cases.'),
  ('AUDITOR',         N'Auditor',                            N'مدقّق',                         N'Read + audit access across cyber and SOC records.'),
  ('CFO',             N'Chief Financial Officer',            N'المدير المالي',                 N'Executive stakeholder for cyber-insurance and M&A.'),
  ('FINANCE_LEAD',    N'Finance Lead',                       N'رئيس الشؤون المالية',           N'Finance stakeholder for insurance / M&A workflows.'),
  ('HR_LEAD',         N'HR Lead',                            N'رئيس الموارد البشرية',          N'HR stakeholder for JML / insider workflows.'),
  ('LEGAL_COUNSEL',   N'Legal Counsel',                      N'المستشار القانوني',            N'Legal stakeholder for breach, takedown and M&A.'),
  ('SIEM_INGEST',     N'SIEM Ingest (service)',              N'حساب إدخال منظومة SIEM',        N'Least-privilege service account: SIEM event ingest only.'),
  ('SCANNER_INGEST',  N'Scanner Ingest (service)',           N'حساب إدخال الماسح',            N'Least-privilege service account: vulnerability-scanner ingest only.'),
  ('DLP_INGEST',      N'DLP Ingest (service)',               N'حساب إدخال DLP',               N'Least-privilege service account: DLP event ingest only.'),
  ('APPSEC_INGEST',   N'AppSec Ingest (service)',            N'حساب إدخال أمن التطبيقات',      N'Least-privilege service account: AppSec finding ingest only.'),
  ('BRAND_INGEST',    N'Brand Ingest (service)',             N'حساب إدخال مراقبة العلامة',     N'Least-privilege service account: brand-monitoring ingest only.'),
  ('JML_INGEST',      N'JML Ingest (service)',               N'حساب إدخال دورة حياة الهوية',    N'Least-privilege service account: IAM joiner/mover/leaver ingest only.'),
  ('TI_INGEST',       N'Threat-Intel Ingest (service)',      N'حساب إدخال معلومات التهديدات',   N'Least-privilege service account: threat-intel ingest only.'),
  ('SBOM_INGEST',     N'SBOM Ingest (service)',              N'حساب إدخال قائمة مكوّنات البرمجيات', N'Least-privilege service account: SBOM component ingest only.')
) v(rn, en, ar, de)
WHERE NOT EXISTS (SELECT 1 FROM iam.roles r WHERE r.role_name = v.rn);
GO

-- 2) Make so_officer a working non-admin SOC operator (demo/dev) so the module is
--    provably usable without PLATFORM_ADMIN. Idempotent on (user_id, role_id).
INSERT INTO iam.user_roles (user_id, role_id, assigned_at, assigned_by)
SELECT u.id, r.id, SYSUTCDATETIME(), 1
FROM iam.users u
CROSS JOIN iam.roles r
WHERE u.username = 'so_officer'
  AND r.role_name IN ('SECURITY_ANALYST', 'CISO')
  AND NOT EXISTS (SELECT 1 FROM iam.user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id);
GO
