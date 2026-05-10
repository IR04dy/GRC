-- ============================================================================
-- TSS GRC — NCA ECC v2.0 Control Catalogue Seed (representative subset)
-- ============================================================================
-- Source framework: National Cybersecurity Authority (NCA) of Saudi Arabia,
--   Essential Cybersecurity Controls (ECC), version 2.0.
-- Control numbering: ECC-{domain}-{subdomain} per NCA convention.
-- Coverage in this seed:
--   Domain 1  Cybersecurity Governance         (10 controls)
--   Domain 2  Cybersecurity Defence            (15 controls)
--   Domain 3  Cybersecurity Resilience         (1 control — sole subdomain)
--   Domain 4  Third-Party Cybersecurity        (2 controls)
--   Domain 5  Industrial Control Systems       (1 control — sole subdomain)
-- Total: 29 controls across all 5 main domains.
--
-- Mapping notes are appended to procedure_en in the form
--   "Maps to: ISO/IEC 27001:2022 A.X.Y · NIST CSF 2.0 GV.PO-1 · …"
-- so that one stored assessment can satisfy multiple framework views.
--
-- Idempotency: every row is inserted only if (organisation_id, framework_name,
-- control_number) does not already exist.  Re-running this script after edits
-- only inserts the missing rows; it does not overwrite manual changes.
--
-- Apply via SSMS or sqlcmd:
--   sqlcmd -S localhost -d TSS_GRC -U sa -P tss.123 -i V_cyber_nca_ecc_seed.sql
-- ============================================================================

DECLARE @org BIGINT = 1;
DECLARE @fw  NVARCHAR(50) = N'NCA ECC';

SET NOCOUNT ON;

-- ── Domain 1 — Cybersecurity Governance ────────────────────────────────────
INSERT INTO cyber.cyber_controls
  (organisation_id, framework_name, domain, control_number, title_en, title_ar, procedure_en, control_type, review_frequency, maturity_level, implementation_status, status, is_deleted, created_at, external_id)
SELECT v.organisation_id, v.framework_name, v.domain, v.control_number, v.title_en, v.title_ar, v.procedure_en, v.control_type, v.review_frequency, v.maturity_level, v.implementation_status, N'ACTIVE', 0, SYSUTCDATETIME(), CAST(NEWID() AS NVARCHAR(36))
FROM (VALUES
  (@org, @fw, N'Cybersecurity Governance', N'ECC-1-1',
   N'Cybersecurity Strategy',
   N'استراتيجية الأمن السيبراني',
   N'A documented cybersecurity strategy must be developed, approved by senior management, aligned with the organisation''s strategy and Saudi national cybersecurity priorities, and reviewed at least annually. Maps to: ISO/IEC 27001:2022 A.5.1 · NIST CSF 2.0 GV.OC-1.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Governance', N'ECC-1-2',
   N'Cybersecurity Management',
   N'إدارة الأمن السيبراني',
   N'A dedicated cybersecurity function reporting to senior management must be established, with documented charter, scope and authority. Maps to: ISO/IEC 27001:2022 A.5.2 · NIST CSF 2.0 GV.RR-2.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Governance', N'ECC-1-3',
   N'Cybersecurity Policies and Procedures',
   N'سياسات وإجراءات الأمن السيبراني',
   N'Cybersecurity policies and procedures must be documented, approved, communicated to personnel and reviewed periodically. Maps to: ISO/IEC 27001:2022 A.5.1, A.5.10 · NIST CSF 2.0 GV.PO-1.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Governance', N'ECC-1-4',
   N'Cybersecurity Roles and Responsibilities',
   N'أدوار ومسؤوليات الأمن السيبراني',
   N'Cybersecurity roles and responsibilities must be defined, documented, allocated and communicated. Segregation of duties must be enforced for sensitive privileges. Maps to: ISO/IEC 27001:2022 A.5.2, A.5.3 · NIST CSF 2.0 GV.RR-1.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Governance', N'ECC-1-5',
   N'Cybersecurity Risk Management',
   N'إدارة مخاطر الأمن السيبراني',
   N'A documented cybersecurity risk-management methodology must be established and applied to identify, assess, treat and monitor cybersecurity risks at least annually. Maps to: ISO/IEC 27001:2022 §6.1, A.5.7 · ISO 27005:2022 · NIST CSF 2.0 GV.RM, ID.RA.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Governance', N'ECC-1-6',
   N'Cybersecurity in Information Technology Projects',
   N'الأمن السيبراني في مشاريع تقنية المعلومات',
   N'Cybersecurity requirements must be integrated into IT project lifecycles, including requirements analysis, design review, secure-development controls and pre-go-live security testing. Maps to: ISO/IEC 27001:2022 A.5.8, A.8.25 · NIST SSDF.',
   N'PREVENTIVE', N'PER_PROJECT', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Governance', N'ECC-1-7',
   N'Compliance with Cybersecurity Standards',
   N'الالتزام بمعايير الأمن السيبراني',
   N'Compliance with applicable cybersecurity laws, regulations and standards must be tracked, evidenced and reported. Maps to: ISO/IEC 27001:2022 A.5.31–A.5.36 · NIST CSF 2.0 GV.OV-1.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Governance', N'ECC-1-8',
   N'Periodical Cybersecurity Review and Audit',
   N'المراجعة والتدقيق الدوري للأمن السيبراني',
   N'Cybersecurity controls must be reviewed and audited periodically by an independent function. Findings must be tracked through to closure. Maps to: ISO/IEC 27001:2022 §9.2, A.5.35 · NIST CSF 2.0 GV.OV-3.',
   N'DETECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Governance', N'ECC-1-9',
   N'Cybersecurity in Human Resources',
   N'الأمن السيبراني في الموارد البشرية',
   N'Cybersecurity requirements must be embedded in joiner/mover/leaver processes, including pre-employment screening, NDA, role-based access provisioning, training and termination procedures. Maps to: ISO/IEC 27001:2022 A.6.1–A.6.6 · NIST CSF 2.0 GV.RR-3.',
   N'PREVENTIVE', N'PER_LIFECYCLE_EVENT', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Governance', N'ECC-1-10',
   N'Cybersecurity Awareness and Training Program',
   N'برنامج التوعية والتدريب على الأمن السيبراني',
   N'A cybersecurity awareness and training programme must be implemented for all personnel, with role-specific deepening for privileged users and developers, and effectiveness measured through assessment or simulated phishing. Maps to: ISO/IEC 27001:2022 A.6.3 · NIST CSF 2.0 PR.AT-1, PR.AT-2.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED')
) AS v(organisation_id, framework_name, domain, control_number, title_en, title_ar, procedure_en, control_type, review_frequency, maturity_level, implementation_status)
WHERE NOT EXISTS (
  SELECT 1 FROM cyber.cyber_controls c
  WHERE c.organisation_id = v.organisation_id
    AND c.framework_name  = v.framework_name
    AND c.control_number  = v.control_number
);

-- ── Domain 2 — Cybersecurity Defence ───────────────────────────────────────
INSERT INTO cyber.cyber_controls
  (organisation_id, framework_name, domain, control_number, title_en, title_ar, procedure_en, control_type, review_frequency, maturity_level, implementation_status, status, is_deleted, created_at, external_id)
SELECT v.organisation_id, v.framework_name, v.domain, v.control_number, v.title_en, v.title_ar, v.procedure_en, v.control_type, v.review_frequency, v.maturity_level, v.implementation_status, N'ACTIVE', 0, SYSUTCDATETIME(), CAST(NEWID() AS NVARCHAR(36))
FROM (VALUES
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-1',
   N'Asset Management',
   N'إدارة الأصول',
   N'All information and technology assets must be inventoried, classified and assigned an owner. The inventory must be reviewed at least annually and on material change. Maps to: ISO/IEC 27001:2022 A.5.9, A.5.12 · NIST CSF 2.0 ID.AM-1, ID.AM-2.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-2',
   N'Identity and Access Management',
   N'إدارة الهويات والوصول',
   N'Logical access to information assets must be authorised based on least-privilege, supported by strong authentication (including MFA for privileged and remote access), and reviewed at least quarterly. Maps to: ISO/IEC 27001:2022 A.5.15–A.5.18 · NIST CSF 2.0 PR.AA-1, PR.AA-3.',
   N'PREVENTIVE', N'QUARTERLY', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-3',
   N'Information System and Information Processing Facilities Protection',
   N'حماية أنظمة وأجهزة معالجة المعلومات',
   N'Information processing facilities, servers, workstations and supporting infrastructure must be protected through hardening baselines, anti-malware, host firewall, application allow-listing and regular patching. Maps to: ISO/IEC 27001:2022 A.8.7, A.8.8, A.8.9 · NIST CSF 2.0 PR.PS-1, PR.PS-2.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-4',
   N'Email Protection',
   N'حماية البريد الإلكتروني',
   N'Email systems must be protected against spoofing, phishing and malware through SPF, DKIM, DMARC enforcement, sandboxing of attachments, URL rewriting, and anti-phishing user training. Maps to: ISO/IEC 27001:2022 A.5.14, A.8.23 · NIST CSF 2.0 PR.DS-2.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-5',
   N'Networks Security Management',
   N'إدارة أمن الشبكات',
   N'Networks must be segmented based on trust zone and data sensitivity, with documented security architecture, traffic filtering at perimeters and inter-zone boundaries, and intrusion-prevention monitoring. Maps to: ISO/IEC 27001:2022 A.8.20–A.8.22 · NIST CSF 2.0 PR.IR-1.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-6',
   N'Mobile Devices Security',
   N'أمن الأجهزة المحمولة',
   N'Mobile devices and BYOD must be enrolled in a Mobile Device Management solution that enforces encryption, screen-lock, remote-wipe, application controls and segregation of corporate from personal data. Maps to: ISO/IEC 27001:2022 A.6.7, A.8.1 · NIST CSF 2.0 PR.PS-3.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-7',
   N'Data and Information Protection',
   N'حماية البيانات والمعلومات',
   N'Data must be classified by sensitivity, with handling rules, retention schedules, secure storage, secure transmission and secure disposal aligned to classification. Personal data must be processed in accordance with PDPL. Maps to: ISO/IEC 27001:2022 A.5.12–A.5.14, A.8.10–A.8.12 · ISO/IEC 27701 · NIST CSF 2.0 PR.DS.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-8',
   N'Cryptography',
   N'التشفير',
   N'Cryptographic controls must be applied to protect data at rest and in transit, with documented key-management procedures including generation, distribution, rotation, escrow and destruction. Algorithm and key-length choices must align with NCA cryptographic guidance. Maps to: ISO/IEC 27001:2022 A.8.24 · NIST CSF 2.0 PR.DS-1, PR.DS-2.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-9',
   N'Backup and Recovery Management',
   N'إدارة النسخ الاحتياطي والاسترداد',
   N'Backup and recovery procedures must be defined, tested at least annually, and stored securely with integrity verification. Backup retention must satisfy regulatory requirements. Maps to: ISO/IEC 27001:2022 A.8.13 · NIST CSF 2.0 PR.DS-11, RC.RP.',
   N'CORRECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-10',
   N'Vulnerabilities Management',
   N'إدارة الثغرات',
   N'Technical vulnerabilities must be discovered through regular automated scanning, prioritised by severity and exploitability, and remediated within defined SLA windows (Critical: 7 days, High: 30 days). Exception requests must be formally approved by the CISO. Maps to: ISO/IEC 27001:2022 A.8.8 · NIST CSF 2.0 ID.RA-1, PR.PS-1.',
   N'DETECTIVE', N'WEEKLY', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-11',
   N'Penetration Testing',
   N'اختبار الاختراق',
   N'Penetration tests must be conducted at least annually for internet-facing services and prior to production go-live for new services, by qualified personnel using a documented methodology. Findings must be remediated and re-tested. Maps to: ISO/IEC 27001:2022 A.8.34 · NIST CSF 2.0 ID.RA-1.',
   N'DETECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-12',
   N'Cybersecurity Event Logs and Monitoring Management',
   N'إدارة سجلات وأحداث الأمن السيبراني',
   N'Security-relevant events must be logged centrally with sufficient retention to support investigation, monitored for threat indicators, and correlated through SIEM or equivalent. Time synchronisation across log sources must be enforced. Maps to: ISO/IEC 27001:2022 A.8.15, A.8.16, A.8.17 · NIST CSF 2.0 DE.CM-1, DE.CM-3.',
   N'DETECTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-13',
   N'Cybersecurity Incident and Threat Management',
   N'إدارة الحوادث والتهديدات السيبرانية',
   N'A cybersecurity incident response capability must be established, exercised at least annually, and integrated with NCA HEY incident notification (within 24 hours of material incident). Threat intelligence must be ingested and operationalised. Maps to: ISO/IEC 27001:2022 A.5.24–A.5.28, A.5.7 · NIST CSF 2.0 RS.MA, RS.AN.',
   N'CORRECTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-14',
   N'Physical Security',
   N'الأمن المادي',
   N'Information processing facilities must be protected by appropriate physical perimeter controls, entry controls, environmental protections (fire, power, climate) and supervised handling of removable media. Maps to: ISO/IEC 27001:2022 A.7.1–A.7.14 · NIST CSF 2.0 PR.AA-6.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cybersecurity Defence', N'ECC-2-15',
   N'Web Application Security',
   N'أمن تطبيقات الويب',
   N'Web applications must be developed using secure-coding practices, protected by a web application firewall for internet-facing services, and tested through SAST/DAST in CI plus annual application-layer penetration testing. Maps to: ISO/IEC 27001:2022 A.8.25–A.8.29 · OWASP ASVS · NIST CSF 2.0 PR.PS-6.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED')
) AS v(organisation_id, framework_name, domain, control_number, title_en, title_ar, procedure_en, control_type, review_frequency, maturity_level, implementation_status)
WHERE NOT EXISTS (
  SELECT 1 FROM cyber.cyber_controls c
  WHERE c.organisation_id = v.organisation_id
    AND c.framework_name  = v.framework_name
    AND c.control_number  = v.control_number
);

-- ── Domain 3 — Cybersecurity Resilience ────────────────────────────────────
INSERT INTO cyber.cyber_controls
  (organisation_id, framework_name, domain, control_number, title_en, title_ar, procedure_en, control_type, review_frequency, maturity_level, implementation_status, status, is_deleted, created_at, external_id)
SELECT v.organisation_id, v.framework_name, v.domain, v.control_number, v.title_en, v.title_ar, v.procedure_en, v.control_type, v.review_frequency, v.maturity_level, v.implementation_status, N'ACTIVE', 0, SYSUTCDATETIME(), CAST(NEWID() AS NVARCHAR(36))
FROM (VALUES
  (@org, @fw, N'Cybersecurity Resilience', N'ECC-3-1',
   N'Cybersecurity Resilience aspects of Business Continuity Management',
   N'الجوانب السيبرانية لإدارة استمرارية الأعمال',
   N'Cybersecurity scenarios must be incorporated into Business Impact Analyses, continuity plans, recovery strategies and exercises, with documented Recovery Time and Point Objectives, and supplier-dependency mapping for critical processes. Maps to: ISO 22301:2019 · ISO/IEC 27001:2022 A.5.29, A.5.30, A.8.13, A.8.14 · NIST CSF 2.0 RC.RP, RC.CO.',
   N'CORRECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED')
) AS v(organisation_id, framework_name, domain, control_number, title_en, title_ar, procedure_en, control_type, review_frequency, maturity_level, implementation_status)
WHERE NOT EXISTS (
  SELECT 1 FROM cyber.cyber_controls c
  WHERE c.organisation_id = v.organisation_id
    AND c.framework_name  = v.framework_name
    AND c.control_number  = v.control_number
);

-- ── Domain 4 — Third-Party Cybersecurity ───────────────────────────────────
INSERT INTO cyber.cyber_controls
  (organisation_id, framework_name, domain, control_number, title_en, title_ar, procedure_en, control_type, review_frequency, maturity_level, implementation_status, status, is_deleted, created_at, external_id)
SELECT v.organisation_id, v.framework_name, v.domain, v.control_number, v.title_en, v.title_ar, v.procedure_en, v.control_type, v.review_frequency, v.maturity_level, v.implementation_status, N'ACTIVE', 0, SYSUTCDATETIME(), CAST(NEWID() AS NVARCHAR(36))
FROM (VALUES
  (@org, @fw, N'Third-Party Cybersecurity', N'ECC-4-1',
   N'Third-Party Cybersecurity',
   N'الأمن السيبراني للأطراف الخارجية',
   N'Third-party engagements must include cybersecurity due diligence proportionate to risk tier, contractual cybersecurity obligations, periodic re-assessment driven by tier, and exit provisions covering data return and destruction. Aligned with NCA Third-Party Cybersecurity Standard (TPCS). Maps to: ISO/IEC 27001:2022 A.5.19–A.5.23 · ISO/IEC 27036 · NIST CSF 2.0 GV.SC.',
   N'PREVENTIVE', N'PER_TIER', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Third-Party Cybersecurity', N'ECC-4-2',
   N'Cloud Computing and Hosting Cybersecurity',
   N'الأمن السيبراني للحوسبة السحابية والاستضافة',
   N'Cloud services must be selected, configured and operated in line with NCA Cloud Cybersecurity Controls (CCC) v1.0. Data classification levels must drive permissible cloud regions and provider categories. Shared-responsibility boundaries must be documented per service. Maps to: NCA CCC v1.0 · ISO/IEC 27017 · ISO/IEC 27018 · CSA CCM · NIST CSF 2.0 PR.IR-3.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED')
) AS v(organisation_id, framework_name, domain, control_number, title_en, title_ar, procedure_en, control_type, review_frequency, maturity_level, implementation_status)
WHERE NOT EXISTS (
  SELECT 1 FROM cyber.cyber_controls c
  WHERE c.organisation_id = v.organisation_id
    AND c.framework_name  = v.framework_name
    AND c.control_number  = v.control_number
);

-- ── Domain 5 — Industrial Control Systems Cybersecurity ────────────────────
INSERT INTO cyber.cyber_controls
  (organisation_id, framework_name, domain, control_number, title_en, title_ar, procedure_en, control_type, review_frequency, maturity_level, implementation_status, status, is_deleted, created_at, external_id)
SELECT v.organisation_id, v.framework_name, v.domain, v.control_number, v.title_en, v.title_ar, v.procedure_en, v.control_type, v.review_frequency, v.maturity_level, v.implementation_status, N'ACTIVE', 0, SYSUTCDATETIME(), CAST(NEWID() AS NVARCHAR(36))
FROM (VALUES
  (@org, @fw, N'Industrial Control Systems', N'ECC-5-1',
   N'Industrial Control Systems Protection',
   N'حماية أنظمة التحكم الصناعي',
   N'OT/ICS environments must be segregated from enterprise IT networks, with specialised cybersecurity controls covering asset inventory, vulnerability and patching, change management, monitoring, incident response and physical security tailored to operational technology constraints. Maps to: IEC 62443 · NIST SP 800-82r3 · ISO/IEC 27001:2022 A.7.4 · NIST CSF 2.0 PR.IR-1.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED')
) AS v(organisation_id, framework_name, domain, control_number, title_en, title_ar, procedure_en, control_type, review_frequency, maturity_level, implementation_status)
WHERE NOT EXISTS (
  SELECT 1 FROM cyber.cyber_controls c
  WHERE c.organisation_id = v.organisation_id
    AND c.framework_name  = v.framework_name
    AND c.control_number  = v.control_number
);

-- ── Result summary ─────────────────────────────────────────────────────────
SELECT
  N'NCA ECC v2.0 seed run complete' AS status,
  COUNT(*) AS total_nca_ecc_controls
FROM cyber.cyber_controls
WHERE organisation_id = @org
  AND framework_name  = @fw
  AND is_deleted      = 0;

GO
