-- ============================================================================
-- TSS GRC Platform — Evidence Management Module: Mock Seed Data
-- Organisation: 1  |  Date context: Q1-Q2 2026
-- Users: 1=admin, 2=rmanager(Khalid), 3=compliance(Noura),
--        4=auditor(Fahad), 5=ranalyst(Sara), 6=viewer(Mohammed)
-- Sub-controls: 1-12 ECC, 13-18 DCC, 19-24 CSCC, 25-30 TW
-- ============================================================================

USE TSS_GRC;
GO
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

-- ============================================================================
-- SECTION 1: EVIDENCE ITEMS
-- Types: POLICY, PROCEDURE, ASSESSMENT, LOG, AUDIT, SCREENSHOT,
--        CONFIGURATION, CERTIFICATE, REPORT, DOCUMENT, TEST
-- Statuses: DRAFT, PENDING_REVIEW, APPROVED, REJECTED
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM evidence.evidence_items WHERE organisation_id = 1)
BEGIN

  -- ECC-1-1: Cybersecurity Strategy & Policy (sc_id=1)
  INSERT INTO evidence.evidence_items
    (organisation_id, sub_control_id, title_en, title_ar, description_en,
     evidence_type, owner_id, version, valid_from, valid_to,
     status, file_name, file_size, mime_type,
     is_deleted, created_by, created_at, updated_at)
  VALUES
  (1, 1,
   N'NCA Cybersecurity Strategy 2026-2028',
   N'استراتيجية الأمن السيبراني للهيئة الوطنية للأمن السيبراني 2026-2028',
   N'Approved three-year cybersecurity strategy aligned with Vision 2030 and NCA ECC-2:2024 requirements. Covers governance, risk, and operational security objectives.',
   'POLICY', 3, '2.0', '2026-01-01', '2028-12-31',
   'APPROVED', N'Cybersecurity_Strategy_2026_2028_v2.pdf', 2457600, 'application/pdf',
   0, 3, DATEADD(day,-90,SYSUTCDATETIME()), DATEADD(day,-85,SYSUTCDATETIME())),

  -- ECC-1-2: Cybersecurity Roles & Responsibilities (sc_id=2)
  (1, 2,
   N'RACI Matrix — Cybersecurity Roles & Responsibilities',
   N'مصفوفة المسؤوليات — أدوار الأمن السيبراني',
   N'Detailed RACI matrix defining cybersecurity roles, responsibilities, accountabilities and communication paths across all departments. Reviewed and signed by CISO.',
   'DOCUMENT', 1, '1.3', '2026-01-15', '2026-12-31',
   'APPROVED', N'RACI_Cybersecurity_Roles_v1.3.xlsx', 184320, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
   0, 1, DATEADD(day,-85,SYSUTCDATETIME()), DATEADD(day,-80,SYSUTCDATETIME())),

  -- ECC-1-3: Cybersecurity Awareness Program (sc_id=3)
  (1, 3,
   N'Q1 2026 Cybersecurity Awareness Training Report',
   N'تقرير برنامج التوعية بالأمن السيبراني — الربع الأول 2026',
   N'Completion report for mandatory cybersecurity awareness training delivered to all 320 staff members in Q1 2026. Covers phishing, social engineering, and PDPL compliance. Pass rate: 94%.',
   'REPORT', 5, '1.0', '2026-03-31', '2026-09-30',
   'APPROVED', N'Q1_2026_Awareness_Training_Report.pdf', 921600, 'application/pdf',
   0, 5, DATEADD(day,-30,SYSUTCDATETIME()), DATEADD(day,-25,SYSUTCDATETIME())),

  -- ECC-2-1: Cybersecurity Risk Assessment (sc_id=4)
  (1, 4,
   N'Enterprise Cybersecurity Risk Assessment — 2026',
   N'تقييم مخاطر الأمن السيبراني المؤسسي — 2026',
   N'Comprehensive enterprise-wide cybersecurity risk assessment identifying 47 risks across 8 domains. Methodology: ISO 31000 adapted for NCA ECC controls. Performed by internal audit team with CISO review.',
   'ASSESSMENT', 2, '1.0', '2026-02-01', '2026-08-01',
   'APPROVED', N'Enterprise_Cyber_Risk_Assessment_2026.pdf', 3670016, 'application/pdf',
   0, 4, DATEADD(day,-75,SYSUTCDATETIME()), DATEADD(day,-70,SYSUTCDATETIME())),

  -- ECC-2-2: Cybersecurity Risk Treatment (sc_id=5)
  (1, 5,
   N'Risk Treatment Plan — Critical Cybersecurity Risks',
   N'خطة معالجة المخاطر — المخاطر السيبرانية الحرجة',
   N'Approved risk treatment plan for 12 critical and high cybersecurity risks identified in Q1 2026 assessment. Includes control owner, budget, and target completion date for each treatment action.',
   'DOCUMENT', 2, '1.1', '2026-03-01', '2026-09-01',
   'PENDING_REVIEW', N'Risk_Treatment_Plan_Critical_2026.xlsx', 256000, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
   0, 2, DATEADD(day,-20,SYSUTCDATETIME()), DATEADD(day,-15,SYSUTCDATETIME())),

  -- ECC-3-1: Asset Management (sc_id=6)
  (1, 6,
   N'IT Asset Inventory — Q1 2026',
   N'جرد أصول تقنية المعلومات — الربع الأول 2026',
   N'Complete IT asset inventory covering 1,240 hardware and software assets across all business units. Includes classification by criticality, owner, and location. Generated from CMDB.',
   'LOG', 1, '2026.Q1', '2026-01-01', '2026-03-31',
   'APPROVED', N'IT_Asset_Inventory_Q1_2026.csv', 512000, 'text/csv',
   0, 1, DATEADD(day,-55,SYSUTCDATETIME()), DATEADD(day,-50,SYSUTCDATETIME())),

  -- ECC-3-2: Vulnerability Management (sc_id=7)
  (1, 7,
   N'Vulnerability Scan Report — March 2026',
   N'تقرير فحص الثغرات الأمنية — مارس 2026',
   N'Monthly vulnerability scan results using Tenable Nessus. Identified 23 critical, 67 high, 134 medium vulnerabilities across 890 scanned hosts. Remediation SLA tracking included.',
   'REPORT', 5, '1.0', '2026-03-01', '2026-04-01',
   'APPROVED', N'Vuln_Scan_March_2026_Nessus.pdf', 1843200, 'application/pdf',
   0, 5, DATEADD(day,-28,SYSUTCDATETIME()), DATEADD(day,-23,SYSUTCDATETIME())),

  -- ECC-3-3: Access Control (sc_id=8)
  (1, 8,
   N'Privileged Access Review — Q1 2026',
   N'مراجعة الوصول المميز — الربع الأول 2026',
   N'Quarterly review of privileged accounts (admin, service, shared) across all production systems. 4 accounts revoked, 8 downgraded. Approved by IT Manager and CISO.',
   'AUDIT', 4, '1.0', '2026-03-15', '2026-06-15',
   'APPROVED', N'Privileged_Access_Review_Q1_2026.pdf', 614400, 'application/pdf',
   0, 4, DATEADD(day,-40,SYSUTCDATETIME()), DATEADD(day,-35,SYSUTCDATETIME())),

  -- ECC-4-1: Vendor Security Assessment (sc_id=9)
  (1, 9,
   N'Vendor Security Assessment — Cloud ERP Provider',
   N'تقييم أمن المورد — مزود نظام تخطيط موارد المؤسسات السحابي',
   N'Third-party security assessment of SAP S/4HANA cloud provider. Evaluated against NCA ECC-4 and ISO 27001 controls. Overall rating: MEDIUM risk with 5 open findings requiring remediation.',
   'ASSESSMENT', 2, '1.0', '2026-02-15', '2026-08-15',
   'APPROVED', N'Vendor_Security_Assessment_SAP_2026.pdf', 2097152, 'application/pdf',
   0, 2, DATEADD(day,-60,SYSUTCDATETIME()), DATEADD(day,-55,SYSUTCDATETIME())),

  -- ECC-4-2: Third-Party Contract Management (sc_id=10)
  (1, 10,
   N'Third-Party Security Clauses — Contract Template',
   N'بنود الأمن السيبراني للأطراف الثالثة — نموذج العقد',
   N'Approved contract template including mandatory NCA-compliant cybersecurity clauses for all technology vendors. Reviewed by Legal and CISO. Effective from January 2026.',
   'POLICY', 1, '3.1', '2026-01-01', '2027-12-31',
   'APPROVED', N'Third_Party_Security_Contract_Template_v3.1.docx', 102400, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
   0, 1, DATEADD(day,-100,SYSUTCDATETIME()), DATEADD(day,-95,SYSUTCDATETIME())),

  -- ECC-5-1: Incident Response Plan (sc_id=11)
  (1, 11,
   N'Cybersecurity Incident Response Playbook 2026',
   N'دليل الاستجابة لحوادث الأمن السيبراني 2026',
   N'Updated incident response playbook covering 12 incident types including ransomware, data breach, DDoS, and insider threat. Aligned with NCA CERT notification requirements. Last tested: March 2026.',
   'PROCEDURE', 3, '2.2', '2026-01-01', '2026-12-31',
   'APPROVED', N'IR_Playbook_2026_v2.2.pdf', 1228800, 'application/pdf',
   0, 3, DATEADD(day,-80,SYSUTCDATETIME()), DATEADD(day,-75,SYSUTCDATETIME())),

  -- ECC-5-2: Business Continuity & DR (sc_id=12)
  (1, 12,
   N'DR Test Results — Core Banking Systems — Feb 2026',
   N'نتائج اختبار التعافي من الكوارث — الأنظمة المصرفية الأساسية — فبراير 2026',
   N'Results of disaster recovery exercise for core banking and ERP systems. RTO achieved: 3.2 hours (target: 4 hours). RPO: 15 minutes. 2 gaps identified, remediation planned for Q2.',
   'TEST', 5, '1.0', '2026-02-28', '2026-08-28',
   'APPROVED', N'DR_Test_Report_Feb_2026.pdf', 768000, 'application/pdf',
   0, 5, DATEADD(day,-55,SYSUTCDATETIME()), DATEADD(day,-50,SYSUTCDATETIME())),

  -- DCC-1-1: Data Governance Policy (sc_id=13)
  (1, 13,
   N'Data Governance Policy — PDPL Aligned',
   N'سياسة حوكمة البيانات — متوافقة مع نظام حماية البيانات الشخصية',
   N'Enterprise data governance policy aligned with Saudi PDPL requirements and DCC-1:2022 framework. Covers data lifecycle, ownership, quality, and cross-border transfer restrictions.',
   'POLICY', 3, '1.2', '2026-01-01', '2026-12-31',
   'APPROVED', N'Data_Governance_Policy_PDPL_v1.2.pdf', 409600, 'application/pdf',
   0, 3, DATEADD(day,-90,SYSUTCDATETIME()), DATEADD(day,-85,SYSUTCDATETIME())),

  -- DCC-1-2: Data Inventory (sc_id=14)
  (1, 14,
   N'Personal Data Inventory & Record of Processing Activities',
   N'جرد البيانات الشخصية وسجل أنشطة المعالجة',
   N'Comprehensive RoPA documenting 34 personal data processing activities across 7 business units. Includes legal basis, data categories, retention periods, and third-party processors.',
   'DOCUMENT', 3, '2.0', '2026-01-01', '2026-12-31',
   'APPROVED', N'ROPA_Personal_Data_Inventory_2026.xlsx', 307200, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
   0, 3, DATEADD(day,-70,SYSUTCDATETIME()), DATEADD(day,-65,SYSUTCDATETIME())),

  -- DCC-2-1: Classification Framework (sc_id=15)
  (1, 15,
   N'Data Classification Framework & Procedures',
   N'إطار تصنيف البيانات والإجراءات',
   N'Data classification framework defining 4 classification levels (Public, Internal, Confidential, Secret) with handling procedures for each. Aligned with NCA data classification guidelines.',
   'PROCEDURE', 1, '1.1', '2026-01-01', '2027-01-01',
   'APPROVED', N'Data_Classification_Framework_v1.1.pdf', 358400, 'application/pdf',
   0, 1, DATEADD(day,-95,SYSUTCDATETIME()), DATEADD(day,-90,SYSUTCDATETIME())),

  -- DCC-2-2: Data Labeling & Handling (sc_id=16)
  (1, 16,
   N'Data Labeling Compliance Audit — Q1 2026',
   N'تدقيق امتثال وسم البيانات — الربع الأول 2026',
   N'Internal audit of data labeling compliance across shared drives and document management systems. 78% compliance rate found; 156 unlabeled documents identified and remediated.',
   'AUDIT', 4, '1.0', '2026-03-01', '2026-06-01',
   'PENDING_REVIEW', N'Data_Labeling_Audit_Q1_2026.pdf', 512000, 'application/pdf',
   0, 4, DATEADD(day,-15,SYSUTCDATETIME()), DATEADD(day,-10,SYSUTCDATETIME())),

  -- DCC-3-1: Encryption Controls (sc_id=17)
  (1, 17,
   N'Encryption Implementation Report — At-Rest & In-Transit',
   N'تقرير تطبيق التشفير — أثناء التخزين والنقل',
   N'Evidence of AES-256 encryption for data at rest across all production databases and TLS 1.3 enforcement for all data in transit. Certificate inventory included.',
   'CONFIGURATION', 1, '1.0', '2026-02-01', '2026-08-01',
   'APPROVED', N'Encryption_Implementation_Report_2026.pdf', 614400, 'application/pdf',
   0, 1, DATEADD(day,-60,SYSUTCDATETIME()), DATEADD(day,-55,SYSUTCDATETIME())),

  -- DCC-3-2: Data Loss Prevention (sc_id=18)
  (1, 18,
   N'DLP Solution Configuration & Policy Screenshots',
   N'لقطات شاشة إعدادات حل منع تسرب البيانات وسياساته',
   N'Screenshots and configuration export of Symantec DLP solution showing active policies for PII detection, blocking, and alerting. 23 DLP incidents blocked in March 2026.',
   'SCREENSHOT', 5, '1.0', '2026-03-15', '2026-09-15',
   'APPROVED', N'DLP_Config_Screenshots_March_2026.pdf', 2621440, 'application/pdf',
   0, 5, DATEADD(day,-12,SYSUTCDATETIME()), DATEADD(day,-7,SYSUTCDATETIME())),

  -- CSC-1-1: Cloud Usage Policy (sc_id=19)
  (1, 19,
   N'Cloud Computing Acceptable Use Policy',
   N'سياسة الاستخدام المقبول للحوسبة السحابية',
   N'Policy governing approved cloud services, prohibited activities, and responsibilities for cloud governance. Aligns with NCA CSCC-1:2020 and Saudi data residency requirements.',
   'POLICY', 3, '1.0', '2026-01-01', '2027-01-01',
   'APPROVED', N'Cloud_Acceptable_Use_Policy_v1.pdf', 204800, 'application/pdf',
   0, 3, DATEADD(day,-100,SYSUTCDATETIME()), DATEADD(day,-95,SYSUTCDATETIME())),

  -- CSC-1-2: Cloud Risk Assessment (sc_id=20)
  (1, 20,
   N'Microsoft Azure Cloud Risk Assessment',
   N'تقييم مخاطر الحوسبة السحابية — مايكروسوفت أزور',
   N'Risk assessment of Microsoft Azure cloud environment covering 42 CSCC controls. Critical finding: unrestricted storage account public access — remediated. Overall risk: LOW after remediation.',
   'ASSESSMENT', 2, '1.0', '2026-02-01', '2026-08-01',
   'APPROVED', N'Azure_Cloud_Risk_Assessment_2026.pdf', 1536000, 'application/pdf',
   0, 2, DATEADD(day,-65,SYSUTCDATETIME()), DATEADD(day,-60,SYSUTCDATETIME())),

  -- CSC-2-1: Network Segmentation (sc_id=21)
  (1, 21,
   N'Network Segmentation Architecture Diagram & Firewall Rules',
   N'مخطط تقسيم الشبكة وقواعد جدار الحماية',
   N'Network architecture showing VLAN segmentation, DMZ configuration, and inter-zone firewall rules. Reviewed by network security team. Aligned with NCA CSC-2 network isolation requirements.',
   'CONFIGURATION', 1, '2.0', '2026-01-01', '2026-12-31',
   'APPROVED', N'Network_Segmentation_Architecture_v2.pdf', 1843200, 'application/pdf',
   0, 1, DATEADD(day,-85,SYSUTCDATETIME()), DATEADD(day,-80,SYSUTCDATETIME())),

  -- CSC-2-2: Identity & Access Management (sc_id=22)
  (1, 22,
   N'IAM Architecture & MFA Enforcement Evidence',
   N'دليل بنية إدارة الهوية والوصول وتطبيق المصادقة متعددة العوامل',
   N'Evidence of Azure AD conditional access policies enforcing MFA for all users accessing cloud resources. Screenshots showing 100% MFA enrollment. Risk-based access policies documented.',
   'SCREENSHOT', 5, '1.0', '2026-03-01', '2026-09-01',
   'DRAFT', N'IAM_MFA_Enforcement_Screenshots.pdf', 3145728, 'application/pdf',
   0, 5, DATEADD(day,-5,SYSUTCDATETIME()), DATEADD(day,-3,SYSUTCDATETIME())),

  -- CSC-3-1: Security Monitoring (sc_id=23)
  (1, 23,
   N'SIEM Dashboard & Alerting Configuration',
   N'لوحة تحكم SIEM وإعدادات التنبيهات',
   N'Microsoft Sentinel SIEM configuration showing 87 active analytics rules, log source coverage, and alert response workflows. Average detection time: 12 minutes for critical alerts.',
   'CONFIGURATION', 1, '1.0', '2026-02-15', '2026-08-15',
   'APPROVED', N'SIEM_Sentinel_Config_Feb_2026.pdf', 921600, 'application/pdf',
   0, 1, DATEADD(day,-50,SYSUTCDATETIME()), DATEADD(day,-45,SYSUTCDATETIME())),

  -- CSC-3-2: Backup & Recovery (sc_id=24)
  (1, 24,
   N'Backup Policy & Restore Test Evidence — Q1 2026',
   N'سياسة النسخ الاحتياطي ودليل اختبار الاستعادة — الربع الأول 2026',
   N'Backup policy covering RPO/RTO targets and evidence of successful quarterly restore test for 15 critical systems. All restores completed within target RTO. Veeam backup solution.',
   'TEST', 5, '1.0', '2026-03-31', '2026-06-30',
   'APPROVED', N'Backup_Restore_Test_Q1_2026.pdf', 716800, 'application/pdf',
   0, 5, DATEADD(day,-25,SYSUTCDATETIME()), DATEADD(day,-20,SYSUTCDATETIME())),

  -- TW-1-1: Teleworking Policy (sc_id=25)
  (1, 25,
   N'Teleworking & Remote Work Security Policy',
   N'سياسة أمن العمل عن بُعد والعمل من المنزل',
   N'Comprehensive remote work security policy covering approved devices, networks, data handling, and reporting procedures. Aligned with NCA Teleworking Controls TW-1:NCA.',
   'POLICY', 3, '1.1', '2026-01-01', '2026-12-31',
   'APPROVED', N'Teleworking_Security_Policy_v1.1.pdf', 307200, 'application/pdf',
   0, 3, DATEADD(day,-90,SYSUTCDATETIME()), DATEADD(day,-85,SYSUTCDATETIME())),

  -- TW-1-2: Acceptable Use Policy (sc_id=26)
  (1, 26,
   N'Acceptable Use Policy — Remote Access',
   N'سياسة الاستخدام المقبول — الوصول عن بُعد',
   N'Policy defining acceptable use of remote access tools, corporate resources, and personal devices during teleworking. Signed acknowledgment from all 87 remote workers on file.',
   'POLICY', 3, '1.0', '2026-01-01', '2026-12-31',
   'APPROVED', N'Acceptable_Use_Policy_Remote_Access.pdf', 204800, 'application/pdf',
   0, 3, DATEADD(day,-88,SYSUTCDATETIME()), DATEADD(day,-83,SYSUTCDATETIME())),

  -- TW-2-1: VPN Controls (sc_id=27)
  (1, 27,
   N'VPN Configuration & Connection Logs — March 2026',
   N'إعدادات VPN وسجلات الاتصال — مارس 2026',
   N'Cisco AnyConnect VPN configuration evidence showing split tunneling disabled, certificate-based authentication, and 30-day connection log extract for compliance review.',
   'LOG', 1, '1.0', '2026-03-01', '2026-04-01',
   'APPROVED', N'VPN_Config_Logs_March_2026.pdf', 1126400, 'application/pdf',
   0, 1, DATEADD(day,-27,SYSUTCDATETIME()), DATEADD(day,-22,SYSUTCDATETIME())),

  -- TW-2-2: Multi-Factor Authentication (sc_id=28)
  (1, 28,
   N'MFA Enrollment Report — Remote Workers',
   N'تقرير تسجيل المصادقة متعددة العوامل — العاملون عن بُعد',
   N'MFA enrollment status report showing 100% of remote workers have enrolled in Microsoft Authenticator. Report generated from Azure AD. Date: March 31, 2026.',
   'REPORT', 5, '1.0', '2026-03-31', '2026-06-30',
   'PENDING_REVIEW', N'MFA_Enrollment_Remote_Workers_Mar2026.pdf', 358400, 'application/pdf',
   0, 5, DATEADD(day,-8,SYSUTCDATETIME()), DATEADD(day,-5,SYSUTCDATETIME())),

  -- TW-3-1: Endpoint Protection (sc_id=29)
  (1, 29,
   N'Endpoint Protection Compliance Report — March 2026',
   N'تقرير امتثال حماية نقاط النهاية — مارس 2026',
   N'Monthly endpoint protection status report from Microsoft Defender for Endpoint. 98.7% device compliance (3 non-compliant devices under remediation). Threat protection summary included.',
   'REPORT', 5, '1.0', '2026-03-31', '2026-04-30',
   'DRAFT', N'Endpoint_Protection_Report_March_2026.pdf', 819200, 'application/pdf',
   0, 5, DATEADD(day,-3,SYSUTCDATETIME()), DATEADD(day,-1,SYSUTCDATETIME())),

  -- TW-3-2: BYOD Controls (sc_id=30)
  (1, 30,
   N'BYOD Security Policy & MDM Enrollment Evidence',
   N'سياسة أمن الأجهزة الشخصية ودليل تسجيل MDM',
   N'BYOD security policy and Microsoft Intune MDM enrollment evidence showing 156 personal devices enrolled with compliant security baselines (encryption, screen lock, remote wipe).',
   'POLICY', 3, '1.0', '2026-01-01', '2026-12-31',
   'REJECTED', N'BYOD_Policy_MDM_Evidence.pdf', 614400, 'application/pdf',
   0, 3, DATEADD(day,-45,SYSUTCDATETIME()), DATEADD(day,-40,SYSUTCDATETIME()));

  PRINT 'Evidence items seeded: 30 items';
END
GO

-- ============================================================================
-- SECTION 2: EVIDENCE REQUESTS
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM evidence.evidence_requests WHERE organisation_id = 1)
BEGIN

  INSERT INTO evidence.evidence_requests
    (organisation_id, sub_control_id, title_en, title_ar, description_en,
     requested_by_id, assigned_to_id, due_date, priority, status, notes,
     is_deleted, created_by, created_at, updated_at)
  VALUES
  -- Open Requests
  (1, 4,
   N'Annual Penetration Testing Report 2026',
   N'تقرير اختبار الاختراق السنوي 2026',
   N'Annual penetration test results from an NCA-approved external vendor covering web applications, APIs, and network infrastructure. Required for ECC-2-1 risk assessment evidence.',
   2, 5, DATEADD(day,14,SYSUTCDATETIME()), 'HIGH', 'OPEN',
   N'External pentest vendor engaged. Report expected by end of April 2026.',
   0, 2, DATEADD(day,-10,SYSUTCDATETIME()), DATEADD(day,-10,SYSUTCDATETIME())),

  (1, 7,
   N'April 2026 Vulnerability Scan Results',
   N'نتائج فحص الثغرات — أبريل 2026',
   N'Monthly vulnerability scan results for April 2026. Required to demonstrate continued vulnerability management under ECC-3-2.',
   2, 5, DATEADD(day,7,SYSUTCDATETIME()), 'MEDIUM', 'OPEN',
   NULL,
   0, 2, DATEADD(day,-3,SYSUTCDATETIME()), DATEADD(day,-3,SYSUTCDATETIME())),

  (1, 22,
   N'IAM Access Review — Q2 2026 Evidence',
   N'مراجعة وصول إدارة الهوية — الربع الثاني 2026',
   N'Quarterly access review results for all privileged and regular user accounts. Must include approval from business unit managers.',
   1, 4, DATEADD(day,21,SYSUTCDATETIME()), 'HIGH', 'OPEN',
   N'Scheduled for first week of Q2 2026.',
   0, 1, DATEADD(day,-5,SYSUTCDATETIME()), DATEADD(day,-5,SYSUTCDATETIME())),

  -- In Progress
  (1, 16,
   N'Data Classification Audit — Full Year Evidence Pack',
   N'حزمة أدلة تدقيق تصنيف البيانات السنوية',
   N'Comprehensive evidence package for annual data classification audit including spot-check results, remediation logs, and sign-off from data owners.',
   3, 4, DATEADD(day,10,SYSUTCDATETIME()), 'MEDIUM', 'IN_PROGRESS',
   N'Auditor Fahad is compiling results from all 7 business units.',
   0, 3, DATEADD(day,-12,SYSUTCDATETIME()), DATEADD(day,-7,SYSUTCDATETIME())),

  (1, 12,
   N'Q2 2026 DR Test Plan & Results',
   N'خطة ونتائج اختبار التعافي من الكوارث — الربع الثاني 2026',
   N'Disaster recovery test plan and results for Q2 2026 covering secondary systems not included in Q1 test. Includes BCM team sign-off.',
   1, 5, DATEADD(day,30,SYSUTCDATETIME()), 'HIGH', 'IN_PROGRESS',
   N'Test scheduled for April 20, 2026.',
   0, 1, DATEADD(day,-8,SYSUTCDATETIME()), DATEADD(day,-4,SYSUTCDATETIME())),

  -- Submitted
  (1, 29,
   N'Endpoint Security Baseline Configuration Evidence',
   N'دليل إعداد خط الأساس لأمن نقاط النهاية',
   N'Configuration baseline screenshots from Microsoft Intune showing enforced security settings on all managed endpoints including patch level, BitLocker status, and AV definitions.',
   3, 5, DATEADD(day,-2,SYSUTCDATETIME()), 'LOW', 'SUBMITTED',
   N'Submitted by Sara. Pending compliance review.',
   0, 3, DATEADD(day,-20,SYSUTCDATETIME()), DATEADD(day,-2,SYSUTCDATETIME())),

  -- Approved
  (1, 3,
   N'Employee Security Awareness Training Completion Certificates',
   N'شهادات إتمام تدريب التوعية الأمنية للموظفين',
   N'Individual completion certificates for 2025 mandatory security awareness training for all 320 employees. Required for NCA ECC-1-3 compliance evidence.',
   3, 5, DATEADD(day,-30,SYSUTCDATETIME()), 'MEDIUM', 'APPROVED',
   N'Completed and approved. All 320 certificates collected and archived.',
   0, 3, DATEADD(day,-45,SYSUTCDATETIME()), DATEADD(day,-30,SYSUTCDATETIME())),

  (1, 11,
   N'Tabletop Exercise Results — Ransomware Scenario',
   N'نتائج التمرين الافتراضي — سيناريو برامج الفدية',
   N'Results and lessons learned from Q1 2026 tabletop exercise simulating a ransomware attack. 8 participants from CISO, IT, Legal, and Communications teams.',
   2, 4, DATEADD(day,-20,SYSUTCDATETIME()), 'HIGH', 'APPROVED',
   N'Exercise completed successfully. Lessons learned incorporated into IR playbook v2.2.',
   0, 2, DATEADD(day,-50,SYSUTCDATETIME()), DATEADD(day,-20,SYSUTCDATETIME())),

  -- Rejected
  (1, 17,
   N'Legacy System Encryption Waiver Documentation',
   N'وثائق استثناء التشفير للأنظمة القديمة',
   N'Formal waiver request for 3 legacy systems unable to support AES-256 encryption due to application constraints. Requires CISO approval.',
   1, 3, DATEADD(day,-10,SYSUTCDATETIME()), 'MEDIUM', 'REJECTED',
   N'Rejected — waiver insufficient. CISO requires compensating controls to be documented first.',
   0, 1, DATEADD(day,-30,SYSUTCDATETIME()), DATEADD(day,-10,SYSUTCDATETIME())),

  -- Closed
  (1, 9,
   N'Vendor Security Questionnaire — Q4 2025 Renewals',
   N'استبيان أمن المورد — تجديدات الربع الرابع 2025',
   N'Completed security questionnaires for 12 technology vendors up for contract renewal in Q4 2025. Includes risk ratings and recommended contract conditions.',
   2, 5, DATEADD(day,-60,SYSUTCDATETIME()), 'MEDIUM', 'CLOSED',
   N'Closed. All 12 vendor questionnaires reviewed and filed.',
   0, 2, DATEADD(day,-90,SYSUTCDATETIME()), DATEADD(day,-60,SYSUTCDATETIME()));

  PRINT 'Evidence requests seeded: 10 requests';
END
GO

-- ============================================================================
-- SECTION 3: EVIDENCE REVIEWS
-- ============================================================================

DECLARE @item1  BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=1  ORDER BY id);
DECLARE @item5  BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=5  ORDER BY id);
DECLARE @item16 BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=16 ORDER BY id);
DECLARE @item22 BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=22 ORDER BY id);
DECLARE @item28 BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=28 ORDER BY id);
DECLARE @item29 BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=29 ORDER BY id);
DECLARE @item30 BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=30 ORDER BY id);
DECLARE @item4  BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=4  ORDER BY id);
DECLARE @item11 BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=11 ORDER BY id);
DECLARE @item8  BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=8  ORDER BY id);

IF NOT EXISTS (SELECT 1 FROM evidence.evidence_reviews WHERE organisation_id = 1)
BEGIN

  INSERT INTO evidence.evidence_reviews
    (organisation_id, evidence_id, reviewer_id, review_date, result, notes_en, status,
     is_deleted, created_by, created_at, updated_at)
  VALUES
  -- Completed reviews — APPROVED outcomes
  (1, @item1, 4, DATEADD(day,-83,SYSUTCDATETIME()),
   'APPROVED',
   N'Strategy document is comprehensive and fully aligned with NCA ECC-2:2024 requirements. Board-level sign-off confirmed on page 3. Version control and review schedule are clearly defined. No findings.',
   'COMPLETED', 0, 4, DATEADD(day,-85,SYSUTCDATETIME()), DATEADD(day,-83,SYSUTCDATETIME())),

  (1, @item4, 4, DATEADD(day,-68,SYSUTCDATETIME()),
   'APPROVED',
   N'Risk assessment methodology is sound and follows ISO 31000. All 47 identified risks have been properly scored and linked to ECC controls. Risk register is up to date. Minor recommendation: add inherent vs residual risk comparison charts.',
   'COMPLETED', 0, 4, DATEADD(day,-70,SYSUTCDATETIME()), DATEADD(day,-68,SYSUTCDATETIME())),

  (1, @item11, 4, DATEADD(day,-73,SYSUTCDATETIME()),
   'APPROVED',
   N'Incident response playbook is well-structured and covers all major incident types. NCA CERT notification timelines are correctly referenced. Tabletop exercise evidence attached. Recommend scheduling next test within 6 months.',
   'COMPLETED', 0, 4, DATEADD(day,-75,SYSUTCDATETIME()), DATEADD(day,-73,SYSUTCDATETIME())),

  (1, @item8, 4, DATEADD(day,-33,SYSUTCDATETIME()),
   'APPROVED',
   N'Privileged access review is thorough. All 4 revoked accounts documented with justification. SoD conflicts resolved. Evidence of manager approval for all changes confirmed. Next review due Q2 2026.',
   'COMPLETED', 0, 4, DATEADD(day,-35,SYSUTCDATETIME()), DATEADD(day,-33,SYSUTCDATETIME())),

  -- Completed review — NEEDS_REVISION outcome
  (1, @item5, 3, DATEADD(day,-13,SYSUTCDATETIME()),
   'NEEDS_REVISION',
   N'Risk treatment plan lacks budget allocation for 4 of the 12 treatment actions. Target completion dates for actions 7, 9, and 11 are missing. Also, control owner sign-off is not present for critical risks 3 and 5. Please resubmit with these corrections.',
   'COMPLETED', 0, 3, DATEADD(day,-15,SYSUTCDATETIME()), DATEADD(day,-13,SYSUTCDATETIME())),

  -- Completed review — REJECTED outcome
  (1, @item30, 3, DATEADD(day,-38,SYSUTCDATETIME()),
   'REJECTED',
   N'BYOD policy does not address personal device MDM un-enrollment procedures upon employee departure. Missing: remote wipe authorization process, personal data handling on managed devices, and PDPL consent clause. This evidence cannot be accepted for DCC-3-2 compliance. Please revise the policy and resubmit.',
   'COMPLETED', 0, 3, DATEADD(day,-40,SYSUTCDATETIME()), DATEADD(day,-38,SYSUTCDATETIME())),

  -- Pending reviews (submitted but awaiting reviewer action)
  (1, @item16, 4, NULL,
   NULL,
   NULL,
   'PENDING', 0, 3, DATEADD(day,-10,SYSUTCDATETIME()), DATEADD(day,-10,SYSUTCDATETIME())),

  (1, @item28, 4, NULL,
   NULL,
   NULL,
   'PENDING', 0, 3, DATEADD(day,-5,SYSUTCDATETIME()), DATEADD(day,-5,SYSUTCDATETIME())),

  (1, @item22, 4, NULL,
   NULL,
   NULL,
   'PENDING', 0, 1, DATEADD(day,-3,SYSUTCDATETIME()), DATEADD(day,-3,SYSUTCDATETIME())),

  (1, @item29, 3, NULL,
   NULL,
   NULL,
   'PENDING', 0, 5, DATEADD(day,-1,SYSUTCDATETIME()), DATEADD(day,-1,SYSUTCDATETIME()));

  PRINT 'Evidence reviews seeded: 10 reviews (4 approved, 1 needs revision, 1 rejected, 4 pending)';
END
GO

-- ============================================================================
-- SECTION 4: EVIDENCE LINKS  (link evidence to risks & compliance module)
-- ============================================================================

DECLARE @ev_strategy BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=1 ORDER BY id);
DECLARE @ev_risk_assess BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=4 ORDER BY id);
DECLARE @ev_ir BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=11 ORDER BY id);
DECLARE @ev_vuln BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=7 ORDER BY id);
DECLARE @ev_dr BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=12 ORDER BY id);
DECLARE @ev_dlp BIGINT = (SELECT TOP 1 id FROM evidence.evidence_items WHERE organisation_id=1 AND sub_control_id=18 ORDER BY id);

-- Get first risk ID from risk module for cross-referencing
DECLARE @risk1 BIGINT = (SELECT TOP 1 id FROM risk.risks WHERE organisation_id=1 AND title_en LIKE '%Ransomware%' ORDER BY id);
DECLARE @risk2 BIGINT = (SELECT TOP 1 id FROM risk.risks WHERE organisation_id=1 AND title_en LIKE '%Data Privacy%' ORDER BY id);
DECLARE @risk3 BIGINT = (SELECT TOP 1 id FROM risk.risks WHERE organisation_id=1 AND title_en LIKE '%NCA ECC%' ORDER BY id);

IF NOT EXISTS (SELECT 1 FROM evidence.evidence_links WHERE organisation_id = 1)
BEGIN

  INSERT INTO evidence.evidence_links
    (organisation_id, evidence_id, linked_module, linked_entity_type, linked_entity_id,
     notes, created_by, created_at, is_deleted)
  VALUES
  -- Strategy linked to compliance NCA_ECC control
  (1, @ev_strategy, 'COMPLIANCE', 'control', 1,
   N'Cybersecurity strategy serves as primary governance evidence for NCA ECC-1 controls.',
   1, DATEADD(day,-85,SYSUTCDATETIME()), 0),

  -- Risk assessment linked to risk module risks
  (1, @ev_risk_assess, 'RISK', 'risk', @risk1,
   N'Risk assessment identifies and quantifies ransomware risk — key evidence for treatment decisions.',
   2, DATEADD(day,-70,SYSUTCDATETIME()), 0),

  (1, @ev_risk_assess, 'RISK', 'risk', @risk3,
   N'Risk assessment provides NCA ECC compliance risk scoring.',
   2, DATEADD(day,-70,SYSUTCDATETIME()), 0),

  -- IR Playbook linked to incident module and ransomware risk
  (1, @ev_ir, 'INCIDENT', 'incident', 1,
   N'IR Playbook is the documented procedure activated during the phishing campaign incident.',
   3, DATEADD(day,-75,SYSUTCDATETIME()), 0),

  (1, @ev_ir, 'RISK', 'risk', @risk1,
   N'IR Playbook is a key control mitigating ransomware attack risk.',
   2, DATEADD(day,-75,SYSUTCDATETIME()), 0),

  -- Vulnerability scan linked to cyber incidents
  (1, @ev_vuln, 'CYBER', 'incident', 2,
   N'Vulnerability scan identified CVE exploited in the SQL injection incident on March 20.',
   5, DATEADD(day,-23,SYSUTCDATETIME()), 0),

  -- DR test linked to BCM plans
  (1, @ev_dr, 'BCM', 'plan', 1,
   N'DR test results validate the effectiveness of the BCP documented in BCM plan.',
   5, DATEADD(day,-50,SYSUTCDATETIME()), 0),

  -- DLP configuration linked to privacy data breach risk
  (1, @ev_dlp, 'RISK', 'risk', @risk2,
   N'DLP solution is a key control mitigating personal data breach risk.',
   5, DATEADD(day,-7,SYSUTCDATETIME()), 0),

  (1, @ev_dlp, 'COMPLIANCE', 'control', 3,
   N'DLP configuration serves as evidence for PDPL data protection technical controls.',
   3, DATEADD(day,-7,SYSUTCDATETIME()), 0);

  PRINT 'Evidence links seeded: 9 cross-module links';
END
GO

-- ============================================================================
-- SECTION 5: VERIFICATION
-- ============================================================================

SELECT 'evidence_items'    AS entity, COUNT(*) AS total FROM evidence.evidence_items    WHERE organisation_id=1 AND is_deleted=0
UNION ALL
SELECT 'evidence_requests',           COUNT(*)           FROM evidence.evidence_requests WHERE organisation_id=1 AND is_deleted=0
UNION ALL
SELECT 'evidence_reviews',            COUNT(*)           FROM evidence.evidence_reviews  WHERE organisation_id=1 AND is_deleted=0
UNION ALL
SELECT 'evidence_links',              COUNT(*)           FROM evidence.evidence_links    WHERE organisation_id=1 AND is_deleted=0;
GO

SELECT status, COUNT(*) AS cnt FROM evidence.evidence_items WHERE organisation_id=1 AND is_deleted=0 GROUP BY status;
GO

PRINT 'Evidence module seed data complete.';
GO
