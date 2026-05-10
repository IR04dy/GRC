-- ============================================================================
-- TSS GRC — Saudi Risk Taxonomy Seed
-- ============================================================================
-- Pre-loads the most-used risk-category taxonomies for Saudi enterprise
-- customers, so first-day risk-register entries can be classified consistently
-- against the four authorities that drive Saudi GRC reality:
--   · PDPL (SDAIA / Saudi Data Office) — personal-data processing risk
--   · SAMA Operational Risk Loss Database (ORLD) — banking op-risk
--   · NCA ECC v2.0 — cybersecurity domains used as risk source-areas
--   · ISO 31000 generic risk types — strategic / financial / operational
--
-- Idempotent: re-running this script only inserts categories that don't
-- already exist for organisation_id = 1, identified by name_en + parent_id.
--
-- Apply via SSMS or sqlcmd:
--   sqlcmd -S localhost -d TSS_GRC -U sa -P tss.123 -I -i V_risk_saudi_taxonomy_seed.sql
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

DECLARE @org BIGINT = 1;

-- ── Top-level taxonomies (parent_id IS NULL) ───────────────────────────────
INSERT INTO risk.risk_categories (organisation_id, name_en, name_ar, description_en, is_active, is_deleted, created_at, external_id)
SELECT v.org, v.en, v.ar, v.descr, 1, 0, SYSUTCDATETIME(), NEWID()
FROM (VALUES
  (@org, N'Personal Data Protection (PDPL)', N'حماية البيانات الشخصية',
   N'Top-level taxonomy for risks arising from processing of personal data of Saudi data subjects under the Personal Data Protection Law and its Implementing Regulations.'),
  (@org, N'Operational Risk (SAMA ORLD)', N'المخاطر التشغيلية (SAMA)',
   N'Top-level taxonomy aligned to SAMA Operational Risk Loss Database categories. Used by SAMA-licensed financial institutions for capital-relevant op-risk reporting.'),
  (@org, N'Cybersecurity (NCA ECC)', N'الأمن السيبراني (NCA ECC)',
   N'Top-level taxonomy mirroring NCA Essential Cybersecurity Controls v2.0 main domains as risk source-areas.'),
  (@org, N'Strategic Risk', N'المخاطر الاستراتيجية',
   N'ISO 31000 — risks affecting achievement of strategic objectives.'),
  (@org, N'Financial Risk', N'المخاطر المالية',
   N'ISO 31000 — credit, market, liquidity and capital adequacy risks.'),
  (@org, N'Compliance & Regulatory Risk', N'مخاطر الالتزام والتنظيم',
   N'Risks of non-compliance with Saudi laws and regulations (CMA, ZATCA, MoH, SAMA, NCA, SDAIA, MoCI, HRSD).'),
  (@org, N'Reputational Risk', N'المخاطر السمعية',
   N'Risks affecting public, regulator and stakeholder perception of the organisation.')
) AS v(org, en, ar, descr)
WHERE NOT EXISTS (
  SELECT 1 FROM risk.risk_categories c
  WHERE c.organisation_id = v.org AND c.parent_id IS NULL AND c.name_en = v.en AND c.is_deleted = 0
);

-- ── Child categories — PDPL personal-data taxonomy ─────────────────────────
DECLARE @pdpl_id BIGINT = (SELECT TOP 1 id FROM risk.risk_categories
                            WHERE organisation_id=@org AND parent_id IS NULL
                              AND name_en = N'Personal Data Protection (PDPL)' AND is_deleted = 0);

INSERT INTO risk.risk_categories (organisation_id, parent_id, name_en, name_ar, description_en, is_active, is_deleted, created_at, external_id)
SELECT @org, @pdpl_id, v.en, v.ar, v.descr, 1, 0, SYSUTCDATETIME(), NEWID()
FROM (VALUES
  (N'Lawful Basis Failure',          N'انعدام الأساس القانوني للمعالجة',           N'Processing personal data without a lawful basis under PDPL Article 5.'),
  (N'Cross-Border Transfer Risk',    N'مخاطر النقل عبر الحدود',                     N'Transferring personal data outside KSA without Data Office approval to a non-adequate jurisdiction.'),
  (N'Sensitive Data Mishandling',    N'سوء معالجة البيانات الحساسة',                N'Processing health, biometric, genetic or financial data without elevated controls.'),
  (N'Children''s Data Risk',          N'مخاطر بيانات الأطفال',                       N'Processing personal data of minors without verified parental consent (PDPL Article 7).'),
  (N'Retention Beyond Lawful Period', N'الاحتفاظ بعد الفترة القانونية',             N'Retaining personal data longer than the documented retention period.'),
  (N'DSR Fulfilment Failure',        N'الإخفاق في تلبية طلبات أصحاب البيانات',      N'Failure to respond to a Data Subject Request within the 30-day PDPL window.'),
  (N'Breach Notification Failure',   N'الإخفاق في الإبلاغ عن الخروقات',            N'Failure to notify the Saudi Data Office within 72 hours of a reportable breach.'),
  (N'Sub-Processor Compliance',      N'التزام معالجي البيانات من الأطراف الخارجية', N'Sub-processors handling Saudi personal data without contractual PDPL-aligned obligations.'),
  (N'Consent Withdrawal Cascade',    N'تأثير سحب الموافقة',                         N'Failure to propagate consent withdrawal to downstream systems and erase data accordingly.')
) AS v(en, ar, descr)
WHERE @pdpl_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM risk.risk_categories c
  WHERE c.organisation_id=@org AND c.parent_id=@pdpl_id AND c.name_en=v.en AND c.is_deleted=0
);

-- ── Child categories — SAMA ORLD operational-risk taxonomy ─────────────────
DECLARE @sama_id BIGINT = (SELECT TOP 1 id FROM risk.risk_categories
                            WHERE organisation_id=@org AND parent_id IS NULL
                              AND name_en = N'Operational Risk (SAMA ORLD)' AND is_deleted = 0);

INSERT INTO risk.risk_categories (organisation_id, parent_id, name_en, name_ar, description_en, is_active, is_deleted, created_at, external_id)
SELECT @org, @sama_id, v.en, v.ar, v.descr, 1, 0, SYSUTCDATETIME(), NEWID()
FROM (VALUES
  (N'Internal Fraud',                N'الاحتيال الداخلي',                        N'Acts of fraud, misappropriation of assets or circumvention of controls by internal personnel.'),
  (N'External Fraud',                N'الاحتيال الخارجي',                        N'Fraud, theft, hacking or forgery by third parties.'),
  (N'Employment Practices & Workplace Safety', N'ممارسات التوظيف وسلامة بيئة العمل', N'Acts inconsistent with employment, health, safety laws or agreements.'),
  (N'Clients, Products & Business Practices',  N'العملاء والمنتجات وممارسات الأعمال', N'Failures to meet professional obligations to clients or product-suitability obligations.'),
  (N'Damage to Physical Assets',     N'الأضرار بالأصول المادية',                 N'Loss or damage to physical assets from natural disaster, vandalism, terrorism or other events.'),
  (N'Business Disruption & System Failures',   N'تعطل الأعمال وفشل الأنظمة',     N'Disruption to business or system failures including hardware, software, telecommunications or utility outages.'),
  (N'Execution, Delivery & Process Management', N'التنفيذ والتسليم وإدارة العمليات', N'Failed transaction processing or process management, from relationships with trade counterparties and vendors.')
) AS v(en, ar, descr)
WHERE @sama_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM risk.risk_categories c
  WHERE c.organisation_id=@org AND c.parent_id=@sama_id AND c.name_en=v.en AND c.is_deleted=0
);

-- ── Child categories — NCA ECC cybersecurity domains as risk source-areas ──
DECLARE @nca_id BIGINT = (SELECT TOP 1 id FROM risk.risk_categories
                           WHERE organisation_id=@org AND parent_id IS NULL
                             AND name_en = N'Cybersecurity (NCA ECC)' AND is_deleted = 0);

INSERT INTO risk.risk_categories (organisation_id, parent_id, name_en, name_ar, description_en, is_active, is_deleted, created_at, external_id)
SELECT @org, @nca_id, v.en, v.ar, v.descr, 1, 0, SYSUTCDATETIME(), NEWID()
FROM (VALUES
  (N'Cybersecurity Governance Risk',  N'مخاطر حوكمة الأمن السيبراني',
   N'Risks arising from inadequate cybersecurity strategy, policies, roles or risk-management. NCA ECC Domain 1.'),
  (N'Cybersecurity Defence Risk',     N'مخاطر الدفاع السيبراني',
   N'Risks from inadequate defensive controls — IAM, network, endpoint, email, vulnerability, encryption. NCA ECC Domain 2.'),
  (N'Cybersecurity Resilience Risk',  N'مخاطر المرونة السيبرانية',
   N'Risks affecting cybersecurity aspects of business continuity. NCA ECC Domain 3.'),
  (N'Third-Party Cybersecurity Risk', N'مخاطر الأمن السيبراني للأطراف الخارجية',
   N'Risks from supplier and cloud-provider cybersecurity posture. NCA ECC Domain 4 + NCA TPCS + NCA CCC.'),
  (N'Industrial Control Systems Risk', N'مخاطر أنظمة التحكم الصناعي',
   N'Risks affecting OT/ICS environments. NCA ECC Domain 5 + IEC 62443.')
) AS v(en, ar, descr)
WHERE @nca_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM risk.risk_categories c
  WHERE c.organisation_id=@org AND c.parent_id=@nca_id AND c.name_en=v.en AND c.is_deleted=0
);

-- ── Result summary ─────────────────────────────────────────────────────────
SELECT
  N'Saudi risk taxonomy seed complete' AS status,
  COUNT(*) AS total_categories
FROM risk.risk_categories
WHERE organisation_id = @org AND is_deleted = 0;

GO
