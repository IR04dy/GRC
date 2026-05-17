-- V44 — NCA OTCC v1.0 Control Catalogue Seed (representative subset).
-- ===========================================================================
-- Source framework: NCA Operational Technology Cybersecurity Controls (OTCC)
--   v1.0, 2022. Aligned with IEC 62443.
-- Total controls in framework: 5 domains, 47 main controls.
-- Seed coverage: 25 representative controls across all 5 main domains.
-- Idempotent.
-- ===========================================================================

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

DECLARE @org BIGINT = 1;
DECLARE @fw  NVARCHAR(50) = N'NCA OTCC';

INSERT INTO cyber.cyber_controls
  (organisation_id, framework_name, domain, control_number, title_en, title_ar,
   procedure_en, control_type, review_frequency, maturity_level,
   implementation_status, status, is_deleted, created_at, external_id)
SELECT v.organisation_id, v.framework_name, v.domain, v.control_number,
       v.title_en, v.title_ar, v.procedure_en, v.control_type,
       v.review_frequency, v.maturity_level, v.implementation_status,
       N'ACTIVE', 0, SYSUTCDATETIME(), CAST(NEWID() AS NVARCHAR(36))
FROM (VALUES
  -- ─── Domain 1: OT Cybersecurity Governance ────────────────────────
  (@org, @fw, N'OT Cybersecurity Governance', N'OTCC-1-1',
   N'OT cybersecurity strategy',
   N'استراتيجية الأمن السيبراني للتقنية التشغيلية',
   N'A documented OT cybersecurity strategy must be developed jointly with engineering + operations leadership, aligned with safety and reliability objectives, reviewed annually. Maps to: IEC 62443-2-1 · ECC-1-1.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Governance', N'OTCC-1-2',
   N'OT cybersecurity governance and oversight',
   N'الحوكمة والإشراف للأمن السيبراني التشغيلي',
   N'An OT cybersecurity steering committee with representation from operations, engineering, IT cybersecurity, and HSE must meet at minimum quarterly.',
   N'PREVENTIVE', N'QUARTERLY', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Governance', N'OTCC-1-3',
   N'OT cybersecurity roles and responsibilities',
   N'الأدوار والمسؤوليات للأمن السيبراني التشغيلي',
   N'OT cybersecurity roles must be documented with clear segregation between OT operations + maintenance + cybersecurity functions; appointment of OT-CISO recommended.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Governance', N'OTCC-1-4',
   N'OT cybersecurity risk management',
   N'إدارة مخاطر الأمن السيبراني التشغيلي',
   N'An OT-specific risk-management methodology must be applied considering safety, availability and integrity impacts; HAZOP + cybersecurity hybrid assessments performed.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Governance', N'OTCC-1-5',
   N'OT cybersecurity in capital projects',
   N'الأمن السيبراني التشغيلي في المشاريع الرأسمالية',
   N'Cybersecurity requirements must be integrated into the OT project lifecycle starting at FEED phase; vendors must provide IEC 62443-4-1 secure-development evidence.',
   N'PREVENTIVE', N'AD_HOC', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Governance', N'OTCC-1-6',
   N'OT cybersecurity training and awareness',
   N'التدريب والتوعية للأمن السيبراني التشغيلي',
   N'Specialised training programmes for OT cybersecurity personnel must be in place — including IEC 62443 + ICS-CERT content; annual refreshers for operators.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),

  -- ─── Domain 2: OT Cybersecurity Defence ───────────────────────────
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-1',
   N'OT asset inventory and classification',
   N'جرد وتصنيف الأصول التشغيلية',
   N'A comprehensive inventory of OT assets (controllers, HMIs, historians, network gear) must be maintained — auto-discovered passively where possible — with criticality classification per IEC 62443 zones.',
   N'PREVENTIVE', N'QUARTERLY', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-2',
   N'OT network segmentation',
   N'تقسيم شبكات التقنية التشغيلية',
   N'OT networks must be segmented from IT into Purdue-model levels with DMZ between levels 3-4; firewalls must enforce industrial-protocol-aware policies.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-3',
   N'OT identity and access management',
   N'إدارة الهوية والوصول التشغيلية',
   N'OT identity must be managed in a dedicated directory; shared / generic accounts on PLCs and HMIs must be eliminated or compensated by physical access controls and session recording.',
   N'PREVENTIVE', N'QUARTERLY', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-4',
   N'OT remote-access security',
   N'أمن الوصول عن بُعد للتقنية التشغيلية',
   N'Remote access to OT systems (vendor or internal) must use jump hosts with MFA, session recording, time-limited tickets, and just-in-time activation.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-5',
   N'OT change management',
   N'إدارة التغيير في التقنية التشغيلية',
   N'OT changes must follow a documented change-management procedure including cybersecurity impact analysis, peer review, and rollback plan before production deployment.',
   N'PREVENTIVE', N'AD_HOC', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-6',
   N'OT removable media and portable devices',
   N'الوسائط القابلة للإزالة والأجهزة المحمولة',
   N'Removable media used in OT environments must be scanned at a dedicated sanitisation station; portable devices (laptops, USB) used by vendors must be issued by the organisation.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-7',
   N'OT vulnerability management',
   N'إدارة الثغرات في التقنية التشغيلية',
   N'OT vulnerability scanning must be passive where possible (active only during planned outages); patching must follow ICS-CERT advisories with vendor coordination + planned-outage windows.',
   N'DETECTIVE', N'QUARTERLY', N'L2', N'PARTIAL'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-8',
   N'OT logging and monitoring',
   N'تسجيل ومراقبة التقنية التشغيلية',
   N'OT-aware monitoring (Claroty / Nozomi / Dragos) must observe industrial protocols passively; anomalies fed to SIEM with OT-engineering on-call playbook.',
   N'DETECTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-9',
   N'OT endpoint protection',
   N'حماية نقاط النهاية التشغيلية',
   N'Workstations and engineering laptops in OT environments must run application-whitelisting (not signature AV) where vendor-supported; offline scanning of historians + servers.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-10',
   N'OT data protection',
   N'حماية البيانات التشغيلية',
   N'Sensitive OT data (recipes, setpoints, process trends) must be protected with access controls; historian replication to IT must be one-way (data diodes for tier-1 sites).',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-11',
   N'OT physical security',
   N'الأمن المادي للتقنية التشغيلية',
   N'OT facilities (substations, control rooms, marshalling cabinets) must have physical access controls including badge readers, CCTV, environmental sensors, and intrusion alarms.',
   N'PREVENTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Defence', N'OTCC-2-12',
   N'OT incident response',
   N'الاستجابة للحوادث التشغيلية',
   N'A dedicated OT incident-response playbook must be in place — including safety-state declaration procedure, forensic preservation techniques for live OT systems, and NCA HEY reporting.',
   N'CORRECTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),

  -- ─── Domain 3: OT Cybersecurity Resilience ────────────────────────
  (@org, @fw, N'OT Cybersecurity Resilience', N'OTCC-3-1',
   N'OT backup and configuration management',
   N'النسخ الاحتياطي وإدارة التهيئة',
   N'PLC programs, HMI configurations, and engineering workstations must be backed up after every change; gold-image baselines maintained off-site.',
   N'CORRECTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'OT Cybersecurity Resilience', N'OTCC-3-2',
   N'OT business-continuity and disaster recovery',
   N'استمرارية الأعمال والتعافي للتقنية التشغيلية',
   N'OT systems must be included in BCM with recovery-time objectives compatible with process safety; cold-standby spare-parts strategy documented per critical asset.',
   N'CORRECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),

  -- ─── Domain 4: Third-Party OT Cybersecurity ───────────────────────
  (@org, @fw, N'Third-Party OT Cybersecurity', N'OTCC-4-1',
   N'OT vendor cybersecurity requirements',
   N'متطلبات الأمن السيبراني لموردي التقنية التشغيلية',
   N'OT-equipment vendors must be contractually required to comply with IEC 62443-4-1 (secure development), provide SBOMs, and patch vulnerabilities under defined SLAs.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Third-Party OT Cybersecurity', N'OTCC-4-2',
   N'OT vendor remote-access governance',
   N'حوكمة الوصول عن بُعد لموردي التقنية التشغيلية',
   N'Vendor remote access to OT must be ticketed, time-bound, supervised, session-recorded, and audited monthly. Standing accounts are prohibited.',
   N'PREVENTIVE', N'MONTHLY', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Third-Party OT Cybersecurity', N'OTCC-4-3',
   N'OT vendor due diligence',
   N'العناية الواجبة لموردي التقنية التشغيلية',
   N'Annual due-diligence assessments must be performed for OT-equipment vendors — covering supply-chain risk (origin of firmware, manufacturing security) and incident-response capability.',
   N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),

  -- ─── Domain 5: Industrial Control Systems Cybersecurity ───────────
  (@org, @fw, N'Industrial Control Systems Cybersecurity', N'OTCC-5-1',
   N'Safety system independence',
   N'استقلالية أنظمة السلامة',
   N'Safety Instrumented Systems (SIS) must be physically and logically separated from BPCS; safety logic must not be reachable from corporate IT.',
   N'PREVENTIVE', N'CONTINUOUS', N'L4', N'IMPLEMENTED'),
  (@org, @fw, N'Industrial Control Systems Cybersecurity', N'OTCC-5-2',
   N'PLC and controller hardening',
   N'تصلب المتحكمات المنطقية',
   N'PLCs must be hardened: default passwords changed, web interfaces disabled where possible, firmware signed-and-verified, programming modes locked under physical-key control.',
   N'PREVENTIVE', N'AD_HOC', N'L3', N'IMPLEMENTED'),
  (@org, @fw, N'Industrial Control Systems Cybersecurity', N'OTCC-5-3',
   N'Wireless ICS security',
   N'أمن الشبكات اللاسلكية الصناعية',
   N'Wireless OT communications (WirelessHART, ISA-100, LoRaWAN) must use vendor-recommended encryption + integrity protection; coverage area periodically surveyed for rogue devices.',
   N'PREVENTIVE', N'QUARTERLY', N'L2', N'PARTIAL'),
  (@org, @fw, N'Industrial Control Systems Cybersecurity', N'OTCC-5-4',
   N'ICS device lifecycle management',
   N'إدارة دورة حياة الأجهزة الصناعية',
   N'End-of-life ICS devices must be tracked; compensating controls (network isolation, additional monitoring) applied to legacy devices that cannot be patched.',
   N'PREVENTIVE', N'ANNUAL', N'L2', N'PARTIAL')
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

PRINT N'V44 — NCA OTCC: 25 representative controls (of 47) seeded.';
GO
