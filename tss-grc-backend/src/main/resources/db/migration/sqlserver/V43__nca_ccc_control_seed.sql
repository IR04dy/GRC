-- V43 — NCA CCC v1.0 Control Catalogue Seed (representative subset).
-- ===========================================================================
-- Source framework: NCA Cloud Cybersecurity Controls (CCC) v1.0, 2020.
-- Total controls in framework: 4 domains, 37 main + 96 subcontrols (133).
-- Seed coverage: 30 representative controls across all 4 main domains.
-- Idempotent.
-- ===========================================================================

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

DECLARE @org BIGINT = 1;
DECLARE @fw  NVARCHAR(50) = N'NCA CCC';

INSERT INTO cyber.cyber_controls
  (organisation_id, framework_name, domain, control_number, title_en, title_ar,
   procedure_en, control_type, review_frequency, maturity_level,
   implementation_status, status, is_deleted, created_at, external_id)
SELECT v.organisation_id, v.framework_name, v.domain, v.control_number,
       v.title_en, v.title_ar, v.procedure_en, v.control_type,
       v.review_frequency, v.maturity_level, v.implementation_status,
       N'ACTIVE', 0, SYSUTCDATETIME(), CAST(NEWID() AS NVARCHAR(36))
FROM (VALUES
  -- ─── Domain 1: Cybersecurity Governance for Cloud ─────────────────
  (@org, @fw, N'Cloud Cybersecurity Governance', N'CCC-1-1',
   N'Cloud cybersecurity strategy',
   N'استراتيجية الأمن السيبراني للحوسبة السحابية',
   N'A documented cloud cybersecurity strategy must be developed, approved by senior management, and reviewed annually — addressing shared-responsibility model, multi-cloud risk, and KSA data-residency. Maps to: ECC-1-1 · CSCC-1-1.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Governance', N'CCC-1-2',
   N'Cloud cybersecurity policy',
   N'سياسة الأمن السيبراني للسحابة',
   N'A cloud-specific cybersecurity policy must be developed, approved, communicated, and enforced — covering acceptable use, data classification mapping, and prohibited services.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Governance', N'CCC-1-3',
   N'Cloud cybersecurity roles and responsibilities',
   N'الأدوار والمسؤوليات للأمن السيبراني السحابي',
   N'Cloud cybersecurity roles must be defined separately from on-premise roles, explicitly mapping CSP-vs-customer responsibilities under the shared-responsibility model.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Governance', N'CCC-1-4',
   N'Cloud risk management',
   N'إدارة مخاطر السحابة',
   N'A cloud-specific risk-management methodology must be applied including CSP risk assessment, data-residency risk, exit-strategy risk, and concentration risk.',
   N'PREVENTIVE', N'QUARTERLY', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Governance', N'CCC-1-5',
   N'Cybersecurity in cloud projects',
   N'الأمن السيبراني في مشاريع السحابة',
   N'Cybersecurity requirements must be integrated into the cloud-adoption lifecycle: design, procurement, migration, and operation — with security review checkpoints.',
   N'PREVENTIVE', N'AD_HOC', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Governance', N'CCC-1-6',
   N'Cloud compliance with regulations',
   N'الامتثال للتشريعات السحابية',
   N'A register of regulatory, contractual, and statutory cybersecurity obligations applicable to cloud must be maintained — including PDPL, SAMA, NCA ECC + CCC + CSCC.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Governance', N'CCC-1-7',
   N'Cloud cybersecurity review and audit',
   N'مراجعة وتدقيق الأمن السيبراني السحابي',
   N'Annual independent audits of cloud cybersecurity posture must be performed — including CSP SOC2 Type II review and customer-controlled penetration tests of cloud-hosted assets.',
   N'DETECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Governance', N'CCC-1-8',
   N'Cloud cybersecurity awareness',
   N'التوعية بالأمن السيبراني السحابي',
   N'Cloud-specific cybersecurity awareness training must be delivered to all cloud-platform engineers, developers, and administrators — covering CSP misconfiguration scenarios and shared-responsibility boundaries.',
   N'PREVENTIVE', N'QUARTERLY', N'L3', N'IMPLEMENTED'),

  -- ─── Domain 2: Cloud Cybersecurity Defence ────────────────────────
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-1',
   N'Cloud asset management',
   N'إدارة الأصول السحابية',
   N'A continuous inventory of all cloud assets (compute, storage, network, identities, managed services) must be maintained — auto-discovered via CSP APIs and tagged with criticality + owner.',
   N'PREVENTIVE', N'CONTINUOUS', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-2',
   N'Cloud identity and access management',
   N'إدارة الهوية والوصول السحابية',
   N'Cloud IAM must enforce MFA for all human access, just-in-time elevation for privileged operations, and federated identity from corporate directory; access reviews quarterly.',
   N'PREVENTIVE', N'QUARTERLY', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-3',
   N'Cloud network security management',
   N'إدارة أمن الشبكات السحابية',
   N'Cloud workloads must be deployed into private subnets behind security groups and NACLs; public endpoints must be limited and protected by WAF + DDoS protection.',
   N'PREVENTIVE', N'CONTINUOUS', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-4',
   N'Cloud data protection',
   N'حماية البيانات السحابية',
   N'All cloud-resident data must be classified and encrypted at-rest using customer-managed keys (CMK); data in-transit via TLS 1.2+; DLP at egress points.',
   N'PREVENTIVE', N'CONTINUOUS', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-5',
   N'Cloud cryptography and key management',
   N'التشفير وإدارة المفاتيح السحابية',
   N'Cryptographic keys for cloud workloads must be managed in a FIPS-140-2 Level 2+ HSM (CSP KMS or customer-controlled); rotation policies + access audit.',
   N'PREVENTIVE', N'ANNUAL', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-6',
   N'Cloud configuration and change management',
   N'إدارة التهيئة والتغيير السحابية',
   N'Cloud configurations must be defined as code (Terraform/CloudFormation), peer-reviewed, and continuously monitored for drift via CSPM tooling.',
   N'PREVENTIVE', N'CONTINUOUS', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-7',
   N'Cloud vulnerability and patch management',
   N'إدارة الثغرات والتصحيحات السحابية',
   N'Cloud workloads must be scanned for vulnerabilities continuously (e.g. AWS Inspector, GuardDuty) with patch SLAs: critical < 7d, high < 30d.',
   N'DETECTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-8',
   N'Cloud logging and monitoring',
   N'تسجيل ومراقبة السحابة',
   N'All cloud-control-plane and data-plane events must be logged centrally (CloudTrail, Activity Logs) and ingested into SIEM with real-time alerting on high-risk events.',
   N'DETECTIVE', N'CONTINUOUS', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-9',
   N'Cloud workload protection',
   N'حماية الأحمال السحابية',
   N'Cloud workloads (VMs, containers, serverless) must run EDR/CWPP agents with anti-malware, behaviour analytics, and runtime protection.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-10',
   N'Cloud secrets management',
   N'إدارة الأسرار السحابية',
   N'Application secrets (API keys, DB passwords, certificates) must be stored in a dedicated secrets manager (HashiCorp Vault / AWS Secrets Manager) — never in source code or environment files.',
   N'PREVENTIVE', N'CONTINUOUS', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-11',
   N'Cloud incident management',
   N'إدارة الحوادث السحابية',
   N'A cloud-specific incident-response runbook must be in place covering CSP escalation contacts, evidence preservation in shared environments, and NCA HEY notification flows.',
   N'CORRECTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-12',
   N'Cloud API security',
   N'أمن واجهات برمجة التطبيقات السحابية',
   N'CSP and customer APIs must enforce authentication (OAuth 2.0 / mTLS), rate-limiting, and request validation; deprecated APIs must be sunset on a tracked schedule.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-13',
   N'Container and serverless security',
   N'أمن الحاويات و serverless',
   N'Container images must be scanned for vulnerabilities pre-deployment, signed, and pulled from approved registries; serverless functions must have least-privilege IAM.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-14',
   N'Cloud privileged access management',
   N'إدارة الوصول المتميز السحابي',
   N'Privileged cloud actions (root user, billing changes, key rotation) must require break-glass procedure with multi-party approval and full session recording.',
   N'PREVENTIVE', N'CONTINUOUS', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Defence', N'CCC-2-15',
   N'Cloud penetration testing',
   N'اختبار الاختراق السحابي',
   N'Annual penetration testing of cloud-hosted critical systems must be performed by an NCA-licensed firm with CSP consent forms in place.',
   N'DETECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),

  -- ─── Domain 3: Cloud Cybersecurity Resilience ─────────────────────
  (@org, @fw, N'Cloud Cybersecurity Resilience', N'CCC-3-1',
   N'Cloud business-continuity aspects',
   N'جوانب استمرارية الأعمال السحابية',
   N'Cloud-hosted systems must be included in the BCM programme with documented dependency on CSP availability + multi-region or multi-cloud resilience for tier-1 systems.',
   N'CORRECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Resilience', N'CCC-3-2',
   N'Cloud backup and recovery',
   N'النسخ الاحتياطي والاستعادة السحابية',
   N'Cloud-resident data must be backed up via cross-region or cross-cloud replication; immutable backup retention min. 30 days; restore tested quarterly.',
   N'CORRECTIVE', N'QUARTERLY', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Cloud Cybersecurity Resilience', N'CCC-3-3',
   N'Cloud DR plans and tests',
   N'خطط واختبارات التعافي السحابية',
   N'A DR plan must be in place for each tier-1 cloud workload with documented RTO/RPO, automated failover procedures, and annual full-failover tests.',
   N'CORRECTIVE', N'ANNUAL', N'L4', N'IMPLEMENTED'),

  -- ─── Domain 4: Third-Party Cloud Cybersecurity ────────────────────
  (@org, @fw, N'Third-Party Cloud Cybersecurity', N'CCC-4-1',
   N'CSP cybersecurity requirements',
   N'متطلبات الأمن السيبراني لمزود الخدمات السحابية',
   N'CSPs must be contractually required to comply with NCA CCC, hold relevant certifications (SOC2 Type II, ISO 27001/27017/27018), and provide quarterly compliance attestations.',
   N'PREVENTIVE', N'QUARTERLY', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Third-Party Cloud Cybersecurity', N'CCC-4-2',
   N'CSP due diligence',
   N'العناية الواجبة لمزود الخدمات السحابية',
   N'Annual due-diligence assessments must be performed for all CSPs — including SOC2 review, security questionnaire, on-site assessment for tier-1, and financial-health check.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Third-Party Cloud Cybersecurity', N'CCC-4-3',
   N'CSP data location and residency',
   N'موقع وإقامة بيانات مزود الخدمات السحابية',
   N'Data classified as sensitive or critical must be stored in CSP regions located within KSA; cross-border data flows must comply with PDPL and have SDAIA approval.',
   N'PREVENTIVE', N'AD_HOC', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Third-Party Cloud Cybersecurity', N'CCC-4-4',
   N'CSP exit strategy',
   N'استراتيجية الخروج من مزود الخدمات السحابية',
   N'A documented exit strategy must exist for each tier-1 CSP relationship covering data extraction, application portability, and 90-day disengagement SLA.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED')
) AS v (organisation_id, framework_name, domain, control_number,
        title_en, title_ar, procedure_en, control_type, review_frequency,
        maturity_level, implementation_status)
WHERE NOT EXISTS (
    SELECT 1 FROM cyber.cyber_controls c
    WHERE c.organisation_id = v.organisation_id
      AND c.framework_name  = v.framework_name
      AND c.control_number  = v.control_number
);
GO

PRINT N'V43 — NCA CCC: 30 representative controls (of 133) seeded.';
GO
