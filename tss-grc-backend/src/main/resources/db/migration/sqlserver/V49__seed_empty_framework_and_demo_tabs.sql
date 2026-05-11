-- V49 — Fill the three empty framework-controls tabs (SAMA Cyber, ISO 22301,
--       PCI DSS) and add demo rows for the two empty single-status tabs on
--       /cyber-pdpl-bridge (Linked) and /evidence-chain (Unverified).
--
-- Why this exists
--   The page sweep on 2026-05-11 found:
--     - /framework-controls → SAMA (0), ISO 22301 (0), PCI DSS (0) tabs empty
--       because V37 seeded the framework rows but no control catalogue.
--     - /cyber-pdpl-bridge → Linked (0) tab empty because V46a only seeded
--       BRIDGE_FIRED + BREACH_CREATED + BRIDGE_SKIPPED outcomes, never the
--       BREACH_LINKED outcome.
--     - /evidence-chain → Unverified (0) tab empty because V47/V47a left
--       every chain in VALID or BROKEN state.
--
-- All inserts are idempotent (guarded by NOT EXISTS so re-running is safe).

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

DECLARE @org BIGINT = 1;

-- ─── SAMA Cyber Security Framework ─────────────────────────────────────────
DECLARE @fw_sama NVARCHAR(50) = N'SAMA Cyber Security Framework';
IF NOT EXISTS (SELECT 1 FROM cyber.cyber_controls WHERE framework_name = @fw_sama)
BEGIN
    INSERT INTO cyber.cyber_controls
      (organisation_id, framework_name, domain, control_number, title_en, title_ar,
       procedure_en, control_type, review_frequency, maturity_level,
       implementation_status, status, is_deleted, created_at, external_id)
    SELECT v.organisation_id, v.framework_name, v.domain, v.control_number,
           v.title_en, v.title_ar, v.procedure_en, v.control_type,
           v.review_frequency, v.maturity_level, v.implementation_status,
           N'ACTIVE', 0, SYSUTCDATETIME(), CAST(NEWID() AS NVARCHAR(36))
    FROM (VALUES
      (@org, @fw_sama, N'Cyber Security Leadership and Governance', N'SAMA-1.1.1',
       N'Cyber security governance', N'حوكمة الأمن السيبراني',
       N'A documented cyber security governance framework approved by the board, reviewed annually, aligned with SAMA expectations and the bank''s risk appetite.',
       N'PREVENTIVE', N'ANNUAL', N'L4', N'IMPLEMENTED'),
      (@org, @fw_sama, N'Cyber Security Leadership and Governance', N'SAMA-1.1.2',
       N'Cyber security committee', N'لجنة الأمن السيبراني',
       N'A cyber security committee chaired by the CEO (or designate) meeting at minimum quarterly, with documented terms of reference and minuted decisions.',
       N'PREVENTIVE', N'QUARTERLY', N'L4', N'IMPLEMENTED'),
      (@org, @fw_sama, N'Cyber Security Risk Management', N'SAMA-2.2.1',
       N'Cyber risk assessment', N'تقييم المخاطر السيبرانية',
       N'A documented and repeatable cyber risk assessment methodology covering threats, vulnerabilities, business impact and inherent / residual risk, reviewed yearly.',
       N'PREVENTIVE', N'ANNUAL', N'L4', N'IMPLEMENTED'),
      (@org, @fw_sama, N'Cyber Security Operations', N'SAMA-3.3.1',
       N'Identity and access management', N'إدارة الهوية والوصول',
       N'Centralised IAM with MFA on privileged and remote access, joiner-mover-leaver workflow, quarterly access recertification for critical systems.',
       N'PREVENTIVE', N'QUARTERLY', N'L3', N'IMPLEMENTED'),
      (@org, @fw_sama, N'Cyber Security Operations', N'SAMA-3.3.5',
       N'Cyber security monitoring', N'مراقبة الأمن السيبراني',
       N'A 24x7 SOC with SIEM, threat-intelligence feeds and documented use cases covering at minimum SAMA''s mandatory detection scenarios.',
       N'DETECTIVE', N'CONTINUOUS', N'L4', N'IMPLEMENTED'),
      (@org, @fw_sama, N'Third-Party Cyber Security', N'SAMA-4.4.1',
       N'Third-party cyber risk', N'مخاطر الطرف الثالث',
       N'A documented third-party cyber risk programme covering onboarding due diligence, contractual cyber clauses, ongoing assessment and exit; aligned with the SAMA outsourcing rules.',
       N'PREVENTIVE', N'ANNUAL', N'L3', N'PARTIAL'),
      (@org, @fw_sama, N'Cyber Resilience', N'SAMA-5.5.1',
       N'Cyber incident response', N'الاستجابة للحوادث السيبرانية',
       N'A documented cyber incident response plan with named team, escalation paths, SAMA notification timelines (within 2 hours for critical), and quarterly tabletop exercises.',
       N'CORRECTIVE', N'QUARTERLY', N'L4', N'IMPLEMENTED'),
      (@org, @fw_sama, N'Cyber Resilience', N'SAMA-5.5.3',
       N'Cyber crisis communications', N'التواصل أثناء الأزمات السيبرانية',
       N'Pre-approved internal, regulator and customer communication templates for cyber crisis scenarios, owned by Communications and reviewed yearly.',
       N'CORRECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED')
    ) v(organisation_id, framework_name, domain, control_number, title_en, title_ar,
         procedure_en, control_type, review_frequency, maturity_level, implementation_status);
END
GO

-- ─── ISO 22301 (Business Continuity Management) ────────────────────────────
DECLARE @fw_iso NVARCHAR(50) = N'ISO 22301';
IF NOT EXISTS (SELECT 1 FROM cyber.cyber_controls WHERE framework_name = @fw_iso)
BEGIN
    INSERT INTO cyber.cyber_controls
      (organisation_id, framework_name, domain, control_number, title_en, title_ar,
       procedure_en, control_type, review_frequency, maturity_level,
       implementation_status, status, is_deleted, created_at, external_id)
    SELECT 1, v.framework_name, v.domain, v.control_number,
           v.title_en, v.title_ar, v.procedure_en, v.control_type,
           v.review_frequency, v.maturity_level, v.implementation_status,
           N'ACTIVE', 0, SYSUTCDATETIME(), CAST(NEWID() AS NVARCHAR(36))
    FROM (VALUES
      (@fw_iso, N'4 — Context of the organisation', N'ISO22301-4.1',
       N'Understanding the organisation and its context',
       N'فهم المنظمة وسياقها',
       N'Documented understanding of internal + external issues affecting continuity outcomes (regulatory, geopolitical, technology, supply chain), reviewed yearly and after major change.',
       N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
      (@fw_iso, N'5 — Leadership', N'ISO22301-5.1',
       N'Leadership and commitment',
       N'القيادة والالتزام',
       N'Top management''s documented commitment to BCMS: policy signed by CEO, budget approved, BCM accountabilities defined in executive scorecards.',
       N'PREVENTIVE', N'ANNUAL', N'L4', N'IMPLEMENTED'),
      (@fw_iso, N'6 — Planning', N'ISO22301-6.1',
       N'Actions to address risks and opportunities',
       N'إجراءات معالجة المخاطر والفرص',
       N'A documented business continuity risk-and-opportunity treatment plan, owned by the BCM Steering Committee, integrated with ERM appetite statements.',
       N'PREVENTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
      (@fw_iso, N'8 — Operation', N'ISO22301-8.2',
       N'Business impact analysis and risk assessment',
       N'تحليل أثر الأعمال وتقييم المخاطر',
       N'BIA covering every critical service with documented MTPD, RTO, RPO and minimum business continuity objective; refreshed annually and on material change.',
       N'PREVENTIVE', N'ANNUAL', N'L4', N'IMPLEMENTED'),
      (@fw_iso, N'8 — Operation', N'ISO22301-8.4',
       N'Business continuity plans and procedures',
       N'خطط وإجراءات استمرارية الأعمال',
       N'Approved BCPs per critical service, with named roles, communication trees, alternate-site arrangements and resource requirements; version controlled and tested.',
       N'CORRECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED'),
      (@fw_iso, N'8 — Operation', N'ISO22301-8.5',
       N'Exercise programme',
       N'برنامج التمارين',
       N'A documented exercise programme covering desk-top, simulation and live tests; at least one full live invocation per critical service per year with after-action review.',
       N'DETECTIVE', N'ANNUAL', N'L3', N'PARTIAL'),
      (@fw_iso, N'9 — Performance evaluation', N'ISO22301-9.1',
       N'Monitoring, measurement, analysis and evaluation',
       N'الرصد والقياس والتحليل والتقييم',
       N'Defined BCMS KPIs (test success rate, RTO/RPO actual vs target, training completion) reported quarterly to BCM Steering Committee.',
       N'DETECTIVE', N'QUARTERLY', N'L3', N'IMPLEMENTED'),
      (@fw_iso, N'10 — Improvement', N'ISO22301-10.2',
       N'Nonconformity and corrective action',
       N'عدم المطابقة والإجراءات التصحيحية',
       N'A documented process for capturing BCMS nonconformities (from exercises, incidents or audits) into the CAPA tracker, with owner, due date and verification of closure.',
       N'CORRECTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED')
    ) v(framework_name, domain, control_number, title_en, title_ar, procedure_en,
         control_type, review_frequency, maturity_level, implementation_status);
END
GO

-- ─── PCI DSS v4.0 ─────────────────────────────────────────────────────────
DECLARE @fw_pci NVARCHAR(50) = N'PCI DSS';
IF NOT EXISTS (SELECT 1 FROM cyber.cyber_controls WHERE framework_name = @fw_pci)
BEGIN
    INSERT INTO cyber.cyber_controls
      (organisation_id, framework_name, domain, control_number, title_en, title_ar,
       procedure_en, control_type, review_frequency, maturity_level,
       implementation_status, status, is_deleted, created_at, external_id)
    SELECT 1, v.framework_name, v.domain, v.control_number,
           v.title_en, v.title_ar, v.procedure_en, v.control_type,
           v.review_frequency, v.maturity_level, v.implementation_status,
           N'ACTIVE', 0, SYSUTCDATETIME(), CAST(NEWID() AS NVARCHAR(36))
    FROM (VALUES
      (@fw_pci, N'1 — Network security controls', N'PCI-1.2',
       N'Network security controls (NSCs) configuration',
       N'تكوين عناصر التحكم في أمن الشبكة',
       N'Firewall + router rule-set reviewed every six months; default-deny inbound; PCI scope segmentation documented in a current network diagram.',
       N'PREVENTIVE', N'SEMI_ANNUAL', N'L4', N'IMPLEMENTED'),
      (@fw_pci, N'3 — Protect stored account data', N'PCI-3.5',
       N'Primary account number (PAN) protection',
       N'حماية رقم الحساب الأساسي',
       N'PAN at rest rendered unreadable via strong cryptography or tokenisation; truncation limited to first-six / last-four; key management documented.',
       N'PREVENTIVE', N'ANNUAL', N'L4', N'IMPLEMENTED'),
      (@fw_pci, N'4 — Protect cardholder data with strong cryptography during transmission', N'PCI-4.2',
       N'Strong cryptography for PAN transmission',
       N'التشفير القوي عند إرسال بيانات البطاقات',
       N'TLS 1.2 minimum, TLS 1.3 preferred, on all PAN transmissions over open / public networks; weak ciphers disabled and re-scanned quarterly.',
       N'PREVENTIVE', N'QUARTERLY', N'L4', N'IMPLEMENTED'),
      (@fw_pci, N'6 — Secure systems and software', N'PCI-6.3',
       N'Vulnerability identification and patching',
       N'تحديد الثغرات والترقيع',
       N'Documented vulnerability-management programme: critical patches within 30 days; quarterly internal + ASV external scans; bug-bounty / pentest yearly.',
       N'CORRECTIVE', N'MONTHLY', N'L3', N'IMPLEMENTED'),
      (@fw_pci, N'8 — Identify users and authenticate access', N'PCI-8.3',
       N'Strong authentication for users',
       N'المصادقة القوية للمستخدمين',
       N'MFA for all non-console administrative access into the CDE and all remote access from outside the trusted network; brute-force lockout after 10 attempts.',
       N'PREVENTIVE', N'ANNUAL', N'L4', N'IMPLEMENTED'),
      (@fw_pci, N'10 — Log and monitor all access', N'PCI-10.2',
       N'Audit log generation for CDE access',
       N'إنشاء سجلات التدقيق',
       N'Audit logs capture all user access to CDE components, administrative actions, access to logs themselves and all authentication failures; retained for 12 months with 3 months readily available.',
       N'DETECTIVE', N'CONTINUOUS', N'L3', N'IMPLEMENTED'),
      (@fw_pci, N'11 — Test security of systems and networks', N'PCI-11.4',
       N'External penetration testing',
       N'اختبار الاختراق الخارجي',
       N'External pentest at minimum yearly and after any significant change to the CDE; findings tracked to closure with remediation evidence retained.',
       N'DETECTIVE', N'ANNUAL', N'L3', N'PARTIAL'),
      (@fw_pci, N'12 — Support information security with policies and programs', N'PCI-12.10',
       N'Incident response plan for cardholder data',
       N'خطة الاستجابة لحوادث بيانات حاملي البطاقات',
       N'A documented PCI incident response plan tested yearly; explicit notification path to acquirer, card brands and SAMA within their required timelines.',
       N'CORRECTIVE', N'ANNUAL', N'L3', N'IMPLEMENTED')
    ) v(framework_name, domain, control_number, title_en, title_ar, procedure_en,
         control_type, review_frequency, maturity_level, implementation_status);
END
GO

-- ─── /cyber-pdpl-bridge → seed one BREACH_LINKED demo row ─────────────────
-- Pick a cyber incident + data breach that aren't already wired up.
IF NOT EXISTS (SELECT 1 FROM privacy.cyber_breach_bridge_log
                WHERE outcome = N'LINKED' AND is_deleted = 0)
BEGIN
    DECLARE @inc_id BIGINT, @inc_no NVARCHAR(64);
    SELECT TOP 1 @inc_id = id, @inc_no = incident_number
      FROM cyber.cyber_incidents
     WHERE is_deleted = 0
       AND id NOT IN (SELECT cyber_incident_id FROM privacy.cyber_breach_bridge_log
                       WHERE cyber_incident_id IS NOT NULL)
     ORDER BY id;

    DECLARE @br_id BIGINT, @br_ext NVARCHAR(64);
    SELECT TOP 1 @br_id = id, @br_ext = external_id
      FROM privacy.data_breaches
     WHERE is_deleted = 0
     ORDER BY id;

    IF @inc_id IS NOT NULL AND @br_id IS NOT NULL
    BEGIN
        INSERT INTO privacy.cyber_breach_bridge_log
          (organisation_id, event_code, cyber_incident_id, cyber_incident_number,
           data_breach_id, data_breach_external_id, rule_name, trigger_reason,
           outcome, outcome_detail, triggered_at, triggered_by_username,
           is_deleted, is_active, created_at)
        VALUES
          (1, N'BREACH_LINKED', @inc_id, @inc_no,
           @br_id, @br_ext,
           N'PIIBreachRule',
           N'Cyber incident PII exfiltration matched an open data breach record for the same data set — bridge linked rather than created a duplicate.',
           N'LINKED',
           N'Linked to existing breach ' + @br_ext + N' (open since 2026-04-09); no new breach record created.',
           DATEADD(DAY, -2, SYSUTCDATETIME()),
           N'admin', 0, 1, SYSUTCDATETIME());
    END
END
GO

-- ─── /evidence-chain → flip the most-recent VALID chain to UNVERIFIED ─────
-- So the "Unverified" filter tab has at least one item to render.
IF NOT EXISTS (SELECT 1 FROM evidence.evidence_items
                WHERE chain_status = N'UNVERIFIED' AND is_deleted = 0)
BEGIN
    DECLARE @ev_id BIGINT;
    SELECT TOP 1 @ev_id = id
      FROM evidence.evidence_items
     WHERE is_deleted = 0 AND chain_status = N'VALID'
     ORDER BY id DESC;

    IF @ev_id IS NOT NULL
    BEGIN
        UPDATE evidence.evidence_items
           SET chain_status = N'UNVERIFIED',
               chain_last_verified_at = NULL
         WHERE id = @ev_id;
    END
END
GO
