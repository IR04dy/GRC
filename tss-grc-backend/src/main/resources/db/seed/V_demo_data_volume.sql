-- ============================================================================
-- TSS GRC — Comprehensive demo data volume
-- ============================================================================
-- Fills every module/page with realistic Saudi-flavored sample data so the
-- running app looks populated end-to-end. Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;

DECLARE @org BIGINT = 1;

-- ── Risks (boost) ──────────────────────────────────────────────────────────
DECLARE @catCyber BIGINT     = (SELECT TOP 1 id FROM risk.risk_categories WHERE name_en LIKE N'%Cyber%');
DECLARE @catOps BIGINT       = (SELECT TOP 1 id FROM risk.risk_categories WHERE name_en LIKE N'%Oper%');
DECLARE @catCompliance BIGINT= (SELECT TOP 1 id FROM risk.risk_categories WHERE name_en LIKE N'%Compl%');
DECLARE @catFinance BIGINT   = (SELECT TOP 1 id FROM risk.risk_categories WHERE name_en LIKE N'%Financ%');
DECLARE @catThirdParty BIGINT= (SELECT TOP 1 id FROM risk.risk_categories WHERE name_en LIKE N'%Third%');
DECLARE @catStrategic BIGINT = (SELECT TOP 1 id FROM risk.risk_categories WHERE name_en LIKE N'%Strat%');
DECLARE @catReputation BIGINT= (SELECT TOP 1 id FROM risk.risk_categories WHERE name_en LIKE N'%Reput%');

INSERT INTO risk.risks (organisation_id, category_id, title_en, title_ar, description_en,
                        source_module, status, inherent_likelihood, inherent_impact,
                        residual_likelihood, residual_impact, identified_date)
SELECT @org, v.cat, v.en, v.ar, v.dsc, v.src, v.st, v.il, v.ii, v.rl, v.ri, v.idd
FROM (VALUES
  (@catCyber,      N'Ransomware via phishing-borne credential theft',     N'برمجية فدية بسبب سرقة بيانات الاعتماد عبر التصيد',  N'Phishing campaign harvesting AD credentials of finance users; lateral movement to file shares.',                'ERM', N'OPEN',         5, 5, 3, 4, '2025-01-15'),
  (@catCyber,      N'Web application SQL injection on customer portal',   N'حقن SQL في تطبيق الويب',                              N'Customer portal forms vulnerable to SQLi against customer database.',                                          'CYBER', N'UNDER_ASSESSMENT', 4, 5, 2, 3, '2025-02-01'),
  (@catCyber,      N'Cloud misconfiguration exposes S3 buckets',          N'سوء إعداد التخزين السحابي يكشف الحاويات',             N'Public-readable buckets containing customer financial documents discovered via continuous CSPM scan.',         'CYBER', N'CLOSED',    3, 5, 1, 2, '2025-01-20'),
  (@catCyber,      N'Unpatched VPN gateway CVE — known exploit available',N'بوابة VPN غير مُحدّثة — استغلال معروف',                 N'Critical vulnerability with public exploit. SLA on critical patches breached.',                                'CYBER', N'OPEN',         5, 5, 2, 4, '2025-04-10'),
  (@catCyber,      N'Privileged access creep on AD service accounts',     N'تضخم الصلاحيات في حسابات خدمة الدليل النشط',          N'Service accounts accumulated unnecessary privileges; PIM not enforced.',                                       'CYBER', N'UNDER_ASSESSMENT', 4, 4, 2, 3, '2025-02-22'),
  (@catOps,        N'Single-DC dependency for core banking',              N'الاعتماد على مركز بيانات وحيد للنواة المصرفية',        N'Tier-1 services run only out of Riyadh DC; failover not validated in last DR cycle.',                          'BCM', N'UNDER_ASSESSMENT',  4, 5, 2, 4, '2025-01-08'),
  (@catOps,        N'Key supplier concentration — single payment processor', N'تركّز المورّدين — مزوّد المدفوعات الوحيد',          N'95% of payment volume routed through one processor with no contracted alternate.',                             'ERM', N'OPEN',         3, 5, 2, 4, '2025-03-15'),
  (@catOps,        N'Aging core banking platform — vendor end-of-support',N'انتهاء دعم منصة النواة المصرفية',                       N'Vendor announced end-of-mainstream-support in 2027; replacement program in feasibility.',                      'ERM', N'UNDER_ASSESSMENT', 3, 5, 2, 4, '2025-02-12'),
  (@catCompliance, N'PDPL data subject rights backlog',                   N'تراكم طلبات أصحاب البيانات وفق PDPL',                  N'DSR backlog growing — 30-day SLA at risk for 12 active requests.',                                             'COMPLIANCE', N'OPEN', 4, 4, 2, 3, '2025-04-12'),
  (@catCompliance, N'NCA ECC §2-12 patch SLA breach (recurring)',         N'تجاوز SLA ترقيع NCA ECC §2-12',                         N'Quarterly patch-cycle SLA missed for second consecutive quarter.',                                              'COMPLIANCE', N'OPEN', 4, 3, 3, 3, '2025-03-05'),
  (@catCompliance, N'SAMA quarterly return — late filing risk',           N'تأخر تقديم التقرير الربعي للبنك المركزي',              N'Internal deadline pressure; late-filing penalties documented in SAMA notice.',                                  'COMPLIANCE', N'UNDER_ASSESSMENT', 3, 4, 2, 3, '2025-02-18'),
  (@catCompliance, N'ZATCA e-invoicing phase-2 cryptographic stamp gaps', N'فجوات الختم المشفر في الفوترة الإلكترونية ZATCA',       N'Random sample of 200 invoices showed 4 missing valid cryptographic stamps.',                                    'COMPLIANCE', N'CLOSED', 3, 4, 1, 3, '2025-01-30'),
  (@catFinance,    N'FX hedging gap on USD-denominated payables',         N'فجوة تحوّط على الالتزامات بالدولار',                    N'Open USD payables of SAR 280M not hedged against the 3.75 peg-band stress.',                                    'ERM', N'OPEN',         3, 4, 2, 3, '2025-02-08'),
  (@catFinance,    N'Goodwill impairment on recent acquisition',          N'انخفاض القيمة لشهرة استحواذ حديث',                       N'Sub-target performance suggests potential impairment trigger in Q3 close.',                                     'ERM', N'UNDER_ASSESSMENT', 3, 4, 2, 3, '2025-03-20'),
  (@catFinance,    N'Liquidity coverage ratio approach to floor',         N'اقتراب نسبة تغطية السيولة من الحد الأدنى',              N'LCR trending toward SAMA-mandated floor; mitigations in funding plan.',                                          'ERM', N'UNDER_ASSESSMENT', 4, 4, 2, 3, '2025-01-25'),
  (@catThirdParty, N'4th-party concentration on cloud egress',            N'تركز الطرف الرابع على بوابة الخروج السحابية',           N'Multiple critical SaaS vendors all egress through one CDN provider.',                                            'ERM', N'OPEN',         3, 4, 2, 4, '2025-04-02'),
  (@catThirdParty, N'Vendor SOC 2 attestation — expired',                  N'انتهاء شهادة SOC 2 لمورّد رئيسي',                       N'Critical SaaS vendor SOC 2 Type II expired 60 days ago; renewal in audit.',                                     'ERM', N'UNDER_ASSESSMENT', 3, 3, 2, 3, '2025-03-10'),
  (@catThirdParty, N'Foreign vendor cross-border data transfer (PDPL)',    N'تحويل بيانات لمورّد أجنبي عبر الحدود',                  N'European processor handles Saudi customer data — PDPL Art. 29 review pending.',                                  'ERM', N'OPEN',         3, 4, 2, 3, '2025-04-15'),
  (@catStrategic,  N'Vision 2030 program funding contraction',             N'انكماش تمويل برامج رؤية 2030',                          N'Strategic program funding reduced 18% in revised budget; market-entry plan at risk.',                            'ERM', N'OPEN',         3, 3, 2, 3, '2025-02-28'),
  (@catStrategic,  N'AI/ML adoption gap vs peer benchmark',                N'فجوة تبني الذكاء الاصطناعي مقارنة بالأقران',            N'Maturity assessment shows two levels behind peer median in customer-facing AI.',                                 'ERM', N'UNDER_ASSESSMENT', 3, 3, 2, 3, '2025-03-22'),
  (@catReputation, N'Social-media adverse sentiment spike',                N'ارتفاع المشاعر السلبية في وسائل التواصل',                N'Sentiment dropped 14 points after a service outage; incident postmortem published.',                             'ERM', N'CLOSED',    3, 3, 1, 2, '2025-02-15'),
  (@catReputation, N'Whistleblower protection law gap analysis findings',  N'فجوات نتيجة تحليل لائحة حماية المبلغين',                N'Two procedural gaps in retaliation-detection workflow per Nazaha guidance.',                                     'COMPLIANCE', N'UNDER_ASSESSMENT', 3, 4, 2, 3, '2025-04-22'),
  (@catCompliance, N'Saudization ratio — Nitaqat band downgrade risk',     N'مخاطر تراجع نسبة السعودة — برنامج نطاقات',              N'Quarterly Saudization at 28% vs target 32% for sector. Hiring plan accelerated.',                                'ERM', N'UNDER_ASSESSMENT', 3, 3, 2, 3, '2025-02-20'),
  (@catCyber,      N'Insider-threat — privileged user offboarding delay',  N'تهديد داخلي — تأخر إنهاء صلاحيات الموظفين',             N'Two privileged accounts not deactivated within 24h of separation per audit sample.',                             'CYBER', N'OPEN',         3, 4, 2, 3, '2025-04-05'),
  (@catCompliance, N'CMA related-party disclosure timeliness',             N'توقيت إفصاح الأطراف ذات العلاقة لهيئة السوق المالية',    N'Two RPT disclosures filed past CMA window; remediation plan with internal audit.',                               'COMPLIANCE', N'UNDER_ASSESSMENT', 3, 3, 2, 3, '2025-03-12'),
  (@catCyber,      N'DDoS readiness — ISP scrubbing tier insufficient',    N'الجاهزية لهجمات DDoS — حزمة التنظيف غير كافية',         N'Vendor scrubbing capacity sized for 80 Gbps; recent incidents in region peaked at 120 Gbps.',                    'CYBER', N'UNDER_ASSESSMENT', 3, 4, 2, 3, '2025-04-18')
) AS v(cat, en, ar, dsc, src, st, il, ii, rl, ri, idd)
WHERE NOT EXISTS (SELECT 1 FROM risk.risks r WHERE r.organisation_id=@org AND r.title_en=v.en AND r.is_deleted=0);

PRINT N'Risks volume seeded.';

-- ── Audit findings (boost to ~30 with varied severities + ages) ────────────
DECLARE @fwNCA   BIGINT = (SELECT id FROM compliance.frameworks WHERE code='NCA_ECC');
DECLARE @fwISO   BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ISO_27001');
DECLARE @fwSAMA  BIGINT = (SELECT id FROM compliance.frameworks WHERE code='SAMA_CSF');
DECLARE @fwPDPL  BIGINT = (SELECT id FROM compliance.frameworks WHERE code='PDPL');
DECLARE @fwZATCA BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ZATCA_EI');
DECLARE @fw22301 BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ISO_22301');
DECLARE @fw37001 BIGINT = (SELECT id FROM compliance.frameworks WHERE code='ISO_37001');
DECLARE @fwAML   BIGINT = (SELECT id FROM compliance.frameworks WHERE code='SAMA_AML');
DECLARE @fwMOCI  BIGINT = (SELECT id FROM compliance.frameworks WHERE code='MOCI_COC');
DECLARE @fwCMA   BIGINT = (SELECT id FROM compliance.frameworks WHERE code='CMA_CGR');

INSERT INTO compliance.findings (organisation_id, title, severity, owner, capa, due_date, status, capa_stage, framework_id, clause_ref, created_at)
SELECT @org, v.t, v.sev, v.own, v.capa, v.due, v.st, v.stage, v.fw, v.cls, v.created
FROM (VALUES
  (N'Privileged accounts without MFA enforcement',                  N'CRITICAL', N'CISO Office',           N'Roll out conditional access enforcing MFA on all Tier-0 + Tier-1 accounts.',                                  '2025-06-15', N'OPEN',        N'IMPLEMENTING',     @fwNCA, N'§2-4-3-1', DATEADD(DAY, -120, SYSUTCDATETIME())),
  (N'Patch SLA missed for critical CVEs (>30 days)',                N'HIGH',     N'Vulnerability Mgmt',    N'Tighten patch SLA monitoring + escalation to weekly Vulnerability Council.',                                   '2025-05-30', N'OPEN',        N'IDENTIFIED',       @fwNCA, N'§2-12-3', DATEADD(DAY, -95,  SYSUTCDATETIME())),
  (N'Backup restoration test not performed in 12 months',           N'HIGH',     N'IT Operations',         N'Schedule quarterly DR restoration drill; document results and lessons learned.',                              '2025-05-15', N'OPEN',        N'IDENTIFIED',       @fw22301,N'§8.5',     DATEADD(DAY, -68,  SYSUTCDATETIME())),
  (N'Vendor due-diligence file incomplete for 4 critical vendors',  N'HIGH',     N'TPRM Lead',             N'Trigger refresh — pull SOC 2, ISO 27001 cert + financial health for 4 critical vendors.',                      '2025-05-10', N'OPEN',        N'IDENTIFIED',       @fwISO, N'A.5.19',   DATEADD(DAY, -40,  SYSUTCDATETIME())),
  (N'PDPL DSR response SLA breached on 2 of 28 requests',           N'HIGH',     N'DPO',                   N'Deploy automated DSR routing + 25-day reminder; escalation to DPO at day 27.',                                 '2025-05-25', N'OPEN',        N'IMPLEMENTING',     @fwPDPL,N'Art. 7',    DATEADD(DAY, -55,  SYSUTCDATETIME())),
  (N'PDPL cross-border transfer log incomplete',                    N'MEDIUM',   N'DPO',                   N'Implement cross-border-transfer ledger; back-fill historical transfers per Art. 29.',                          '2025-06-01', N'OPEN',        N'IDENTIFIED',       @fwPDPL,N'Art. 29',   DATEADD(DAY, -32,  SYSUTCDATETIME())),
  (N'ZATCA phase-2 cryptographic stamp missing on 4 invoices',      N'MEDIUM',   N'Tax Compliance',        N'Inspect XML signing pipeline; replay reconciliation; add automated assertion.',                                '2025-05-20', N'OPEN',        N'OWNER_TESTED',     @fwZATCA,N'Phase-2 §3', DATEADD(DAY, -28, SYSUTCDATETIME())),
  (N'SAMA quarterly return submitted 2 days late',                  N'HIGH',     N'Reg Affairs',           N'Internal calendar 5 days earlier; add backup approver; quality-check checklist.',                              '2025-06-30', N'IN_PROGRESS', N'INDEPENDENT_TESTED', @fwSAMA,N'Q-Returns',  DATEADD(DAY, -22,  SYSUTCDATETIME())),
  (N'Information security policy not reviewed in 18 months',        N'MEDIUM',   N'Group Compliance',      N'Council reviews + republishes ISP. Cross-attest to NCA + ISO + SAMA.',                                         '2025-05-15', N'IN_PROGRESS', N'AUDIT_SIGNED_OFF', @fwISO, N'A.5.1',     DATEADD(DAY, -75,  SYSUTCDATETIME())),
  (N'Encryption at rest gap on legacy data warehouse',              N'CRITICAL', N'Data Engineering',      N'Migrate warehouse to TDE-enabled tier; deprecate legacy partitions.',                                          '2025-07-01', N'OPEN',        N'IMPLEMENTING',     @fwNCA, N'§2-7-3',    DATEADD(DAY, -110, SYSUTCDATETIME())),
  (N'PIM activation logs not retained for 12 months',               N'MEDIUM',   N'IAM Lead',              N'Forward PIM events to SIEM with 24-month retention bucket.',                                                   '2025-05-30', N'IN_PROGRESS', N'OWNER_TESTED',     @fwSAMA,N'3.4.2',     DATEADD(DAY, -48,  SYSUTCDATETIME())),
  (N'Code of conduct attestation rate below 95%',                   N'LOW',      N'HR Compliance',         N'Email reminder + escalation to managers; lock SSO until attestation completed.',                              '2025-05-12', N'IN_PROGRESS', N'OWNER_TESTED',     @fw37001,N'§7.5',      DATEADD(DAY, -19,  SYSUTCDATETIME())),
  (N'Conflict-of-interest disclosure refresh not run in 24 months', N'MEDIUM',   N'Group Legal',           N'Annual COI disclosure campaign with HR; track non-responders.',                                                '2025-05-10', N'OPEN',        N'IDENTIFIED',       @fw37001,N'§7.5',      DATEADD(DAY, -8,   SYSUTCDATETIME())),
  (N'Anti-bribery training overdue for 47 employees',               N'MEDIUM',   N'Ethics Office',         N'KnowBe4 reassignment + manager escalation; lock travel approvals at day 14.',                                  '2025-05-22', N'OPEN',        N'IDENTIFIED',       @fw37001,N'§7.3',      DATEADD(DAY, -14,  SYSUTCDATETIME())),
  (N'AML transaction-monitoring rule false-positive rate >85%',     N'MEDIUM',   N'Financial Crime Unit',  N'Tune TM-007 + TM-014 thresholds; weekly tuning review.',                                                       '2025-06-15', N'IN_PROGRESS', N'OWNER_TESTED',     @fwAML, N'§3-2-7',    DATEADD(DAY, -45,  SYSUTCDATETIME())),
  (N'Sanctions-screening list refresh latency 48h',                 N'HIGH',     N'Financial Crime Unit',  N'Switch to API-based feed; monitor latency SLO.',                                                                '2025-05-18', N'OPEN',        N'IMPLEMENTING',     @fwAML, N'§3-2-2',    DATEADD(DAY, -52,  SYSUTCDATETIME())),
  (N'Asset inventory drift in CMDB (12% mismatch)',                 N'LOW',      N'IT Operations',         N'Reconcile via discovery scan + manual remediation; quarterly drift report.',                                  '2025-06-10', N'OPEN',        N'IDENTIFIED',       @fwISO, N'A.5.9',     DATEADD(DAY, -10,  SYSUTCDATETIME())),
  (N'Network segmentation gaps to OT environment',                  N'HIGH',     N'Network Security',      N'Deploy industrial firewall; document zone-to-zone traffic policy.',                                            '2025-06-30', N'OPEN',        N'IDENTIFIED',       @fwNCA, N'§3-1-2',    DATEADD(DAY, -100, SYSUTCDATETIME())),
  (N'Workstation hardening baseline drift (12 endpoints)',          N'LOW',      N'Endpoint Security',     N'Re-apply baseline via Intune CSP; add weekly drift report.',                                                   '2025-05-12', N'IN_PROGRESS', N'OWNER_TESTED',     @fwISO, N'A.8.9',     DATEADD(DAY, -25,  SYSUTCDATETIME())),
  (N'Supplier-paid hospitality declaration overdue (3 employees)',  N'LOW',      N'Ethics Office',         N'Targeted reminder; declarations filed; closure noted.',                                                        '2025-05-05', N'CLOSED',      N'CLOSED',           @fw37001,N'§7.5',      DATEADD(DAY, -90,  SYSUTCDATETIME())),
  (N'Data classification labels missing on 8% of files',            N'MEDIUM',   N'DPO',                   N'Re-run scan; auto-classify via Purview; remediation in 60 days.',                                              '2025-05-30', N'IN_PROGRESS', N'OWNER_TESTED',     @fwPDPL,N'Art. 27',   DATEADD(DAY, -60,  SYSUTCDATETIME())),
  (N'Phishing simulation click-rate 14% (above 8% target)',         N'MEDIUM',   N'Security Awareness',    N'Targeted re-training; simulated campaigns weekly for 2 months.',                                               '2025-05-25', N'IN_PROGRESS', N'OWNER_TESTED',     @fwNCA, N'§2-13-3',   DATEADD(DAY, -38,  SYSUTCDATETIME())),
  (N'Incident timeline incomplete on 2 P2 incidents',               N'LOW',      N'SOC',                   N'Add Jira automation that closes-on-required-fields; quarterly review.',                                        '2025-05-10', N'CLOSED',      N'CLOSED',           @fwISO, N'A.5.24',    DATEADD(DAY, -75,  SYSUTCDATETIME())),
  (N'Critical-vendor exit-plan documented for only 60% of T1',      N'HIGH',     N'TPRM Lead',             N'Targeted exit-plan workshops; track to 100% by quarter end.',                                                  '2025-06-30', N'OPEN',        N'IDENTIFIED',       @fwSAMA,N'4.2.1',     DATEADD(DAY, -125, SYSUTCDATETIME()))
) AS v(t, sev, own, capa, due, st, stage, fw, cls, created)
WHERE NOT EXISTS (SELECT 1 FROM compliance.findings f WHERE f.organisation_id=@org AND f.title=v.t AND f.is_deleted=0);

PRINT N'Findings volume seeded.';

-- ── KRI readings (12 readings per KRI to populate dashboard charts) ────────
INSERT INTO risk.kri_readings (kri_id, reading_value, reading_at, band, notes)
SELECT k.id,
       CAST(ABS(CHECKSUM(NEWID())) % 25 AS DECIMAL(18,4)),  -- pseudo-random 0..25
       DATEADD(DAY, -7 * (12 - n.n), SYSUTCDATETIME()),
       CASE
         WHEN ABS(CHECKSUM(NEWID())) % 25 < ISNULL(k.green_max, 0) THEN N'GREEN'
         WHEN ABS(CHECKSUM(NEWID())) % 25 < ISNULL(k.amber_max, 100) THEN N'AMBER'
         ELSE N'RED'
       END,
       N'Auto-seeded reading for demo'
FROM risk.kri_definitions k
CROSS JOIN (VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12)) AS n(n)
WHERE k.organisation_id = @org
  AND NOT EXISTS (SELECT 1 FROM risk.kri_readings r WHERE r.kri_id = k.id);

PRINT N'KRI readings seeded.';

-- ── Audit engagements (boost to 7) ──────────────────────────────────────────
INSERT INTO compliance.audit_engagements
  (organisation_id, engagement_code, title_en, title_ar, audit_type, framework, auditor_firm, lead_auditor,
   engagement_letter_date, opening_meeting_date, fieldwork_start_date, fieldwork_end_date, report_issued_date,
   scope_summary, status)
SELECT @org, v.code, v.en, v.ar, v.atype, v.fw, v.firm, v.lead,
       v.eldate, v.omdate, v.fwstart, v.fwend, v.rdate, v.scope, v.st
FROM (VALUES
  (N'AUD-2025-004',
   N'PDPL Implementing-Regulations Compliance Review',
   N'مراجعة الامتثال للائحة التنفيذية لنظام حماية البيانات',
   N'REGULATORY', N'SDAIA PDPL', N'KPMG Saudi Arabia', N'Lead: Mr. Ali Al-Zahrani',
   '2025-03-01', '2025-03-20', '2025-03-20', '2025-04-25', NULL,
   N'Coverage of PDPL Implementing Regulations: lawful basis, DSR fulfilment, breach notification, cross-border transfers, DPO role.',
   N'REPORTING'),
  (N'AUD-2025-005',
   N'ISO 22301 BCMS — Internal Audit',
   N'تدقيق داخلي لنظام إدارة استمرارية الأعمال ISO 22301',
   N'INTERNAL', N'ISO 22301', N'Internal Audit Function', N'Lead: Internal Audit',
   '2025-02-20', '2025-03-05', '2025-03-05', '2025-04-02', '2025-04-15',
   N'BIA, RTO/RPO validation, BCMS exercise programme, recovery operations playbook.',
   N'CLOSED'),
  (N'AUD-2025-006',
   N'Anti-bribery & Corruption Programme Review (ISO 37001)',
   N'مراجعة برنامج مكافحة الرشوة والفساد وفق ISO 37001',
   N'CERTIFICATION', N'ISO 37001:2016', N'PwC Saudi Arabia', N'Lead: Dr. Hassan Al-Faisal',
   '2025-04-10', NULL, NULL, NULL, NULL,
   N'Anti-bribery management system gap-analysis covering due diligence, gifts, hospitality, training.',
   N'PLANNED'),
  (N'AUD-2025-007',
   N'NCA CCC Cloud Cybersecurity Controls — On-site Assessment',
   N'تقييم ضوابط الأمن السيبراني السحابية وفق NCA CCC',
   N'REGULATORY', N'NCA CCC v1.0', N'EY Saudi Arabia', N'Lead: Eng. Khaled Al-Otaibi',
   '2025-04-18', '2025-05-05', NULL, NULL, NULL,
   N'NCA CCC v1.0 cloud cybersecurity controls assessment — all three pillars (Tenant, Provider, Sourcing).',
   N'ENGAGED')
) AS v(code, en, ar, atype, fw, firm, lead, eldate, omdate, fwstart, fwend, rdate, scope, st)
WHERE NOT EXISTS (SELECT 1 FROM compliance.audit_engagements e WHERE e.organisation_id=@org AND e.engagement_code=v.code AND e.is_deleted=0);

PRINT N'Audit engagements volume seeded.';

-- ── Compliance controls (boost to ~25) ──────────────────────────────────────
INSERT INTO compliance.controls (organisation_id, code, name_en, name_ar, framework, framework_id, domain, maturity_level, status, owner, description)
SELECT @org, v.code, v.en, v.ar, v.fw, v.fwid, v.dom, v.lvl, v.st, v.own, v.dsc
FROM (VALUES
  (N'NCA-2-4-3-1',  N'MFA on privileged accounts',                       N'المصادقة الثنائية على الحسابات الإدارية',                  N'NCA_ECC',   @fwNCA,   N'IAM',                4, N'IMPLEMENTED', N'IAM Lead',           N'Conditional access enforcing MFA on all Tier-0 + Tier-1 accounts.'),
  (N'NCA-2-12-3',   N'Patch management — critical SLA 14 days',          N'إدارة الترقيع — SLA حرج 14 يومًا',                          N'NCA_ECC',   @fwNCA,   N'Vulnerability Mgmt', 3, N'PARTIAL',     N'CISO Office',         N'Critical CVEs patched within 14 days; HIGH within 30 days.'),
  (N'NCA-2-13-3',   N'Phishing-simulation programme (monthly)',          N'برنامج محاكاة التصيد الشهري',                                N'NCA_ECC',   @fwNCA,   N'Awareness',          3, N'IMPLEMENTED', N'Security Awareness',  N'KnowBe4 monthly campaigns with adaptive difficulty.'),
  (N'NCA-2-7-3',    N'Encryption-at-rest for sensitive data',            N'تشفير البيانات الحساسة في حالة السكون',                     N'NCA_ECC',   @fwNCA,   N'Data Protection',    4, N'PARTIAL',     N'Data Engineering',    N'TDE on production DBs; legacy warehouse migration in progress.'),
  (N'NCA-3-1-2',    N'Network segmentation (zones)',                     N'تجزئة الشبكة (مناطق)',                                       N'NCA_ECC',   @fwNCA,   N'Network',            3, N'PARTIAL',     N'Network Security',    N'Three-zone segmentation; OT zone hardening in progress.'),
  (N'ISO-A-5-1',    N'Information security policies',                    N'سياسات أمن المعلومات',                                       N'ISO_27001', @fwISO,   N'Policy',             4, N'IMPLEMENTED', N'Group Compliance',    N'ISMS policy + supporting policies reviewed annually.'),
  (N'ISO-A-5-19',   N'Information security in supplier relationships',   N'أمن المعلومات في علاقات الموردين',                            N'ISO_27001', @fwISO,   N'Third-party',        3, N'PARTIAL',     N'TPRM Lead',           N'Vendor due-diligence + contract clauses; refresh cadence formalised.'),
  (N'ISO-A-5-24',   N'Information security incident management planning',N'إدارة حوادث أمن المعلومات',                                  N'ISO_27001', @fwISO,   N'Incident',           4, N'IMPLEMENTED', N'SOC Manager',         N'IR plan tested quarterly; runbooks updated.'),
  (N'ISO-A-8-9',    N'Configuration management (baselines)',             N'إدارة التهيئة (الخطوط الأساسية)',                            N'ISO_27001', @fwISO,   N'Endpoint',           3, N'PARTIAL',     N'Endpoint Security',   N'Intune baselines for Windows + macOS endpoints.'),
  (N'SAMA-1-2-1',   N'Cybersecurity strategy and policies',              N'استراتيجية وسياسات الأمن السيبراني',                          N'SAMA_CSF',  @fwSAMA,  N'Governance',         4, N'IMPLEMENTED', N'CISO Office',         N'Board-approved cybersecurity strategy refreshed Q1 2025.'),
  (N'SAMA-3-2-7',   N'AML transaction monitoring',                       N'مراقبة عمليات مكافحة غسل الأموال',                            N'SAMA_AML',  @fwAML,   N'AML',                3, N'PARTIAL',     N'Financial Crime Unit',N'Transaction monitoring rules tuned quarterly; FP rate target <70%.'),
  (N'PDPL-Art-7',   N'Data subject rights fulfilment SLA',               N'تنفيذ حقوق أصحاب البيانات وفق SLA',                          N'PDPL',      @fwPDPL,  N'Privacy',            3, N'PARTIAL',     N'DPO',                 N'30-day SLA per Implementing Regulations Art. 7.'),
  (N'PDPL-Art-29',  N'Cross-border transfer governance',                 N'حوكمة التحويلات العابرة للحدود',                              N'PDPL',      @fwPDPL,  N'Privacy',            2, N'PARTIAL',     N'DPO',                 N'Cross-border transfer log + adequacy assessment workflow.')
) AS v(code, en, ar, fw, fwid, dom, lvl, st, own, dsc)
WHERE NOT EXISTS (SELECT 1 FROM compliance.controls c WHERE c.organisation_id=@org AND c.code=v.code AND c.is_deleted=0);

PRINT N'Controls volume seeded.';

-- ── Policies (boost to 12 with varied lifecycle states) ────────────────────
INSERT INTO compliance.policies (organisation_id, code, title_en, title_ar, version, category, content_en, status, effective_from, next_review_date)
SELECT @org, v.code, v.en, v.ar, v.ver, v.cat, v.body, v.st, v.eff, v.nrev
FROM (VALUES
  (N'POL-INFO-001', N'Information Security Policy',         N'سياسة أمن المعلومات',                       N'4.1', N'INFORMATION_SECURITY', N'Master ISMS policy aligned to ISO 27001 + NCA ECC.',                'PUBLISHED',     '2024-12-01', '2025-12-01'),
  (N'POL-PRIV-001', N'Privacy & PDPL Policy',                N'سياسة الخصوصية ونظام حماية البيانات',         N'2.0', N'PRIVACY',              N'Personal data processing principles per SDAIA PDPL.',                'PUBLISHED',     '2024-09-01', '2025-09-01'),
  (N'POL-AUP-001',  N'Acceptable Use Policy',                N'سياسة الاستخدام المقبول',                    N'3.2', N'ACCEPTABLE_USE',       N'Employee acceptable use of IT resources, internet, email + AI tools.', N'REVIEW_DUE',   '2024-06-15', '2025-06-15'),
  (N'POL-BCM-001',  N'Business Continuity Policy',           N'سياسة استمرارية الأعمال',                     N'2.1', N'BUSINESS_CONTINUITY',  N'BCMS scope, RTO/RPO targets, exercise programme.',                  N'PUBLISHED',    '2024-11-15', '2025-11-15'),
  (N'POL-IR-001',   N'Incident Response Policy',             N'سياسة الاستجابة للحوادث',                     N'2.3', N'INCIDENT_RESPONSE',    N'NIST 800-61 lifecycle aligned IR procedures + NCA notification.',     N'PUBLISHED',    '2025-01-10', '2026-01-10'),
  (N'POL-AC-001',   N'Access Control Policy',                N'سياسة التحكم في الوصول',                       N'2.5', N'INFORMATION_SECURITY', N'Identity lifecycle, RBAC, PIM enforcement.',                          N'PUBLISHED',    '2024-10-20', '2025-10-20'),
  (N'POL-COC-001',  N'Code of Conduct',                      N'مدونة السلوك',                                 N'5.0', N'HR',                   N'Ethical conduct, anti-bribery, conflict-of-interest.',                N'PUBLISHED',    '2024-12-30', '2025-12-30'),
  (N'POL-AML-001',  N'AML/CTF Policy',                        N'سياسة مكافحة غسل الأموال وتمويل الإرهاب',     N'3.1', N'FINANCE',              N'CDD, EDD, sanctions, transaction-monitoring per SAMA + FATF.',         N'PUBLISHED',    '2024-08-01', '2025-08-01'),
  (N'POL-AI-001',   N'Generative-AI Use Policy',             N'سياسة استخدام أدوات الذكاء التوليدي',         N'1.0', N'INFORMATION_SECURITY', N'Approved generative-AI tools, prohibited prompts, sensitivity tags.',N'EXEC_APPROVAL','2025-04-01', '2026-04-01'),
  (N'POL-VEN-001',  N'Third-Party Risk Management Policy',   N'سياسة إدارة مخاطر الطرف الثالث',              N'2.0', N'RISK_MANAGEMENT',      N'Vendor risk tiers, due diligence, monitoring, exit planning.',         N'LEGAL_REVIEW', '2025-03-15', '2026-03-15'),
  (N'POL-DR-001',   N'Disaster Recovery & DR Testing Policy',N'سياسة التعافي من الكوارث',                     N'2.2', N'BUSINESS_CONTINUITY',  N'DR architecture, RTO/RPO, quarterly testing requirements.',           N'DRAFT',         NULL, NULL)
) AS v(code, en, ar, ver, cat, body, st, eff, nrev)
WHERE NOT EXISTS (SELECT 1 FROM compliance.policies p WHERE p.organisation_id=@org AND p.code=v.code AND p.is_deleted=0);

PRINT N'Policies volume seeded.';

-- ── Vendors (boost to 12) ───────────────────────────────────────────────────
INSERT INTO tprm.vendors (organisation_id, vendor_name, vendor_name_ar, vendor_type, country, contact_name, contact_email, risk_rating, status, contract_start_date, contract_end_date, description)
SELECT @org, v.n, v.nar, v.t, v.c, v.cn, v.ce, v.rr, v.st, v.cs, v.ce2, v.dsc
FROM (VALUES
  (N'Microsoft Saudi Arabia',          N'مايكروسوفت السعودية',          N'STRATEGIC',  N'SAU', N'Khalid Saeed',     N'khalid@example.com',     N'MEDIUM',   N'ACTIVE', '2024-01-01', '2026-12-31', N'M365, Azure, Sentinel — strategic platform partner.'),
  (N'Amazon Web Services KSA',         N'خدمات أمازون ويب — السعودية',    N'STRATEGIC',  N'SAU', N'Sara Al-Ghamdi',  N'sara.aws@example.com',   N'MEDIUM',   N'ACTIVE', '2024-03-01', '2027-02-28', N'Cloud — KSA-CENTRAL-1 region migration partner.'),
  (N'STC Solutions',                   N'حلول STC',                       N'CRITICAL',   N'SAU', N'Mohammed Q.',     N'm.qassem@example.com',   N'HIGH',     N'ACTIVE', '2023-06-01', '2026-05-31', N'Telecom + connectivity for KSA branches.'),
  (N'BSI Saudi Arabia',                N'BSI السعودية',                   N'OPERATIONAL',N'SAU', N'Dr. Khalid O.',    N'k.otaibi@example.com',   N'LOW',      N'ACTIVE', '2024-01-15', '2026-01-14', N'ISO 27001 + ISO 22301 certification body.'),
  (N'KPMG Saudi Arabia',               N'KPMG السعودية',                  N'OPERATIONAL',N'SAU', N'Ali Al-Zahrani',   N'a.zahrani@example.com',  N'MEDIUM',   N'ACTIVE', '2024-04-01', '2026-03-31', N'Financial audit + advisory services.'),
  (N'Tenable',                         N'Tenable',                         N'OPERATIONAL',N'USA', N'Vendor Account',   N'vendor@example.com',     N'MEDIUM',   N'ACTIVE', '2024-02-01', '2026-01-31', N'Vulnerability management platform — Tenable.io.'),
  (N'KnowBe4',                         N'KnowBe4',                         N'OPERATIONAL',N'USA', N'Vendor Account',   N'vendor@example.com',     N'LOW',      N'ACTIVE', '2024-03-01', '2026-02-28', N'Security awareness + phishing simulation platform.'),
  (N'Tata Consultancy Services',       N'TCS',                             N'STRATEGIC',  N'IND', N'Account Director', N'tcs.acct@example.com',  N'MEDIUM',   N'ACTIVE', '2023-09-01', '2026-08-31', N'Application development + maintenance services.')
) AS v(n, nar, t, c, cn, ce, rr, st, cs, ce2, dsc)
WHERE NOT EXISTS (SELECT 1 FROM tprm.vendors x WHERE x.organisation_id=@org AND x.vendor_name=v.n AND x.is_deleted=0);

PRINT N'Vendors volume seeded.';

-- ── Cyber incidents (boost to 12) ───────────────────────────────────────────
INSERT INTO cyber.incidents (organisation_id, title, title_ar, severity, category, status, assignee, discovery_date, resolution_date, description, nist_phase)
SELECT @org, v.t, v.tar, v.sev, v.cat, v.st, v.assn, v.dd, v.rd, v.dsc, v.np
FROM (VALUES
  (N'Phishing campaign — finance-team targeted',          N'حملة تصيد تستهدف فريق المالية',                 N'HIGH',     N'CYBER',         N'INVESTIGATING',  N'SOC L2',  '2025-03-12', '2025-03-13', N'~120 phishing emails impersonating CFO; 3 clicks; no credentials harvested due to MFA.', N'CONTAINMENT'),
  (N'Ransomware attempt blocked at endpoint',             N'محاولة برامج فدية محظورة عند نقطة النهاية',     N'CRITICAL', N'CYBER',          N'CLOSED',     N'SOC L3',  '2025-02-20', '2025-02-22', N'EDR blocked DLL injection; root cause: phished credentials. STR filed.',                N'LESSONS_LEARNED'),
  (N'DDoS — public website 12-min outage',                N'هجوم DDoS — انقطاع 12 دقيقة لموقع الويب',        N'HIGH',     N'CYBER',             N'CLOSED',     N'NetOps',  '2025-04-08', '2025-04-08', N'~80 Gbps; mitigated by upstream scrubber; review triggered SLA discussion.',            N'POST_INCIDENT'),
  (N'Insider data exfiltration — DLP triggered',          N'تسريب داخلي — تنبيه DLP',                       N'HIGH',     N'ACCESS_CONTROL',          N'INVESTIGATING',N'SOC L3', '2025-04-22', NULL,         N'Sales user attempted to upload customer list to personal Drive. HR + Legal engaged.',  N'CONTAINMENT'),
  (N'Cloud account credential leak (GitHub)',             N'تسرب بيانات اعتماد سحابية على GitHub',           N'HIGH',     N'ACCESS_CONTROL',  N'CLOSED',     N'CloudOps','2025-01-15', '2025-01-16', N'Engineer pushed creds; rotated, audit log clean. Trufflehog hook added.',                N'LESSONS_LEARNED'),
  (N'Suspicious privileged-account activity (after-hours)',N'نشاط مشبوه لحساب إداري — خارج الدوام',          N'MEDIUM',   N'ACCESS_CONTROL', N'CLOSED',     N'SOC L2',  '2025-03-30', '2025-04-01', N'PIM activation outside normal hours — confirmed legitimate by manager; documented.',     N'CLOSED'),
  (N'Vendor data breach notification (third-party)',      N'إشعار اختراق من طرف ثالث',                       N'HIGH',     N'OTHER',      N'INVESTIGATING',N'CISO',   '2025-04-29', NULL,         N'SaaS vendor reported breach; assessing data-impact + notification obligations.',         N'IDENTIFICATION')
) AS v(t, tar, sev, cat, st, assn, dd, rd, dsc, np)
WHERE NOT EXISTS (SELECT 1 FROM cyber.incidents i WHERE i.organisation_id=@org AND i.title=v.t AND i.is_deleted=0);

PRINT N'Cyber incidents volume seeded.';

-- ── Change requests (boost to 15) ───────────────────────────────────────────
INSERT INTO change_mgmt.change_requests
  (organisation_id, change_code, title_en, title_ar, change_type, category, risk_level,
   impact_summary, requested_by_name, change_owner_name, requested_date, scheduled_start, scheduled_end, status)
SELECT @org, v.code, v.en, v.ar, v.tp, v.cat, v.risk, v.imp, v.req, v.own, v.rdate, v.sst, v.sen, v.st
FROM (VALUES
  (N'CHG-2025-0005', N'Upgrade firewall cluster to 7.4.6',                  N'ترقية عنقود الجدار الناري',                       N'NORMAL', N'NETWORK',     N'MEDIUM',  N'4-min cutover per node; rolling — no service impact expected.',                  N'NetOps Lead', N'Network Eng',     '2025-04-22', '2025-05-08 22:00', '2025-05-09 02:00', N'CAB_APPROVED'),
  (N'CHG-2025-0006', N'Rotate TLS certificates — customer portal',          N'تدوير شهادات TLS — بوابة العملاء',                N'STANDARD',N'INFRASTRUCTURE', N'LOW', N'No downtime — blue/green; 5-min DNS warm-up.',                                  N'CloudOps',    N'CloudOps',         '2025-04-28', '2025-05-10 02:00', '2025-05-10 02:30', N'SCHEDULED'),
  (N'CHG-2025-0007', N'Enable MFA enforcement on standard users',           N'تفعيل المصادقة الثنائية للمستخدمين',                N'NORMAL', N'SECURITY',    N'MEDIUM',  N'Affects ~1,200 users; phased over 2 weeks; helpdesk readiness.',                 N'IAM Lead',    N'IAM Team',         '2025-04-15', '2025-06-01 09:00', '2025-06-15 17:00', N'CAB_PENDING'),
  (N'CHG-2025-0008', N'Implement Sanctions-list daily refresh',             N'تحديث قائمة العقوبات يوميًا',                      N'NORMAL', N'COMPLIANCE_REMEDIATION', N'HIGH', N'Switch from 48h to API-based daily refresh; FCC monitoring SLO.',           N'FCC Head',    N'AML Eng',          '2025-04-18', '2025-05-25 03:00', '2025-05-25 06:00', N'IMPACT_ASSESSED'),
  (N'CHG-2025-0009', N'Decommission legacy data warehouse',                 N'إيقاف مستودع البيانات القديم',                       N'NORMAL', N'DATA',        N'HIGH',    N'Migration complete; archive 7-year retention; final cutover next month.',        N'Data Lead',   N'Data Eng',         '2025-04-12', '2025-06-22 22:00', '2025-06-23 06:00', N'SCHEDULED'),
  (N'CHG-2025-0010', N'Patch Apache Tomcat to latest LTS',                  N'ترقيع Apache Tomcat إلى LTS الأخيرة',              N'NORMAL', N'SECURITY',    N'MEDIUM',  N'Rolling restart; 8 nodes; canary first.',                                       N'AppSec',      N'AppSec on-call',   '2025-04-25', '2025-05-12 22:00', '2025-05-13 01:00', N'IMPLEMENTING'),
  (N'CHG-2025-0011', N'Re-baseline endpoint hardening for macOS fleet',     N'إعادة تثبيت تهيئة نقاط النهاية — macOS',           N'STANDARD',N'INFRASTRUCTURE', N'LOW', N'Push via MDM; users prompted to restart; ~200 endpoints.',                      N'EndpointSec', N'EndpointSec',      '2025-04-20', '2025-05-04 09:00', '2025-05-08 17:00', N'IMPLEMENTED'),
  (N'CHG-2025-0012', N'Issue lease — KSA backup datacenter (Jeddah)',       N'استئجار مركز بيانات احتياطي (جدة)',                N'NORMAL', N'INFRASTRUCTURE', N'CRITICAL',N'Net-new DR site; phased build over 6 months.',                            N'CIO Office',  N'Real Estate',     '2025-03-18', '2025-09-01', '2025-12-31', N'CAB_APPROVED'),
  (N'CHG-2025-0013', N'Standard config — onboard new SaaS (PagerDuty)',     N'إعداد قياسي — اعتماد PagerDuty',                   N'STANDARD',N'APPLICATION', N'LOW',     N'Pre-approved SaaS; SOC2 verified; data classification: internal.',              N'SRE',         N'SRE',              '2025-04-29', '2025-05-15 09:00', '2025-05-15 11:00', N'DRAFT'),
  (N'CHG-2025-0014', N'Update Acceptable Use Policy v3.3 — AI clauses',     N'تحديث سياسة الاستخدام v3.3 — أحكام الذكاء',         N'STANDARD',N'POLICY',      N'LOW',     N'Republish only; attestation campaign.',                                          N'Group Legal', N'HR Comms',         '2025-04-30', '2025-05-20 09:00', '2025-05-20 17:00', N'CAB_PENDING'),
  (N'CHG-2025-0015', N'Implement automated Saudization reporting feed',     N'إعداد تغذية تلقائية لتقارير السعودة',               N'NORMAL', N'PROCESS',     N'MEDIUM',  N'New ETL feed to HRSD portal; weekly cadence.',                                  N'HR Tech',     N'HR Tech',          '2025-04-26', '2025-06-10 02:00', '2025-06-10 04:00', N'SUBMITTED')
) AS v(code, en, ar, tp, cat, risk, imp, req, own, rdate, sst, sen, st)
WHERE NOT EXISTS (SELECT 1 FROM change_mgmt.change_requests c WHERE c.organisation_id=@org AND c.change_code=v.code AND c.is_deleted=0);

PRINT N'Change requests volume seeded.';

-- ── Ethics cases (boost to 14) ──────────────────────────────────────────────
INSERT INTO ethics.cases
  (organisation_id, case_code, title_en, title_ar, category, severity, intake_channel,
   reporter_name, is_anonymous, subject_summary, description, location, date_of_incident,
   framework_id, clause_ref, status, investigator_name, committee_name, regulator_notified)
SELECT @org, v.code, v.en, v.ar, v.cat, v.sev, v.ch, v.rep, v.anon, v.subj, v.dsc,
       v.loc, v.doi, v.fw, v.cls, v.st, v.inv, v.cm, v.reg
FROM (VALUES
  (N'CASE-2025-0005', N'Inappropriate gift exchanged with auditor',           N'تبادل هدية غير لائقة مع مدقق',                     N'BRIBERY',           N'HIGH',     N'WEB_FORM', NULL, 1, N'External auditor + finance director.',                                       N'Anonymous web tip suggesting an external auditor was given a luxury watch by a finance director during fieldwork.',                  N'Riyadh — Finance', '2025-04-01', @fw37001, N'§7.5',    N'TRIAGE',         N'Internal Audit',          N'Group Audit Committee',  0),
  (N'CASE-2025-0006', N'Workplace harassment — open-plan environment',         N'تحرش في بيئة العمل — مكتب مفتوح',                  N'HARASSMENT',        N'HIGH',     N'IN_PERSON', N'Senior Engineer (witness)', 0, N'Two team-leads in engineering.', N'Witness reports persistent inappropriate jokes between two managers; targets identified; HR notified.',                              N'Jeddah — Engineering',  '2025-04-08', @fw37001, N'§7.5',    N'INVESTIGATION',N'HR Investigations Lead', N'HR Disciplinary Committee', 0),
  (N'CASE-2025-0007', N'Discrimination — promotion decision review',           N'تمييز — مراجعة قرار ترقية',                         N'DISCRIMINATION',    N'MEDIUM',   N'EMAIL',    N'Junior Analyst', 0, N'Department director + HR partner.', N'Concern that 2024 promotion cycle systematically disadvantaged Saudi national candidates. HR data review requested.',                 N'Riyadh — Operations',   '2025-03-15', @fwMOCI, N'§3.2', N'INVESTIGATION', N'External Investigator',     N'Board HR Committee',       0),
  (N'CASE-2025-0008', N'Fraud — fictitious vendor invoice',                    N'احتيال — فاتورة مورّد وهمي',                         N'FRAUD',             N'CRITICAL', N'HOTLINE',  NULL, 1, N'Procurement specialist + small vendor.', N'Anonymous hotline tip about an invoice paid to a vendor with no matching PO + no goods received.',                                 N'Riyadh — Procurement',  '2025-04-12', @fw37001, N'§7.5',    N'INVESTIGATION', N'Forensic Accountant',     N'Special Investigations',  1),
  (N'CASE-2025-0009', N'Theft — laptop missing from secure cabinet',           N'سرقة — جهاز محمول مفقود',                           N'THEFT',             N'MEDIUM',   N'IN_PERSON', N'IT Asset Custodian', 0, N'Unknown.', N'Encrypted laptop reported missing during quarterly inventory; remote-wipe initiated; replacement issued.',                            N'Jeddah — IT Storage',   '2025-04-05', NULL,    NULL,        N'NO_VIOLATION',  N'Security Manager',         N'IT Steering',            0),
  (N'CASE-2025-0010', N'Data misuse — customer-list export to personal email', N'إساءة استخدام بيانات — تصدير قائمة العملاء',         N'DATA_MISUSE',       N'CRITICAL', N'EMAIL',    N'DLP Auto-Alert', 0, N'Sales account manager.', N'DLP triggered on bulk customer-record export to personal email. Investigated under PDPL + employment policy.',                       N'Riyadh — Sales',        '2025-04-22', @fwPDPL, N'Art. 20', N'INVESTIGATION',  N'Group Legal',              N'HR Disciplinary Committee', 0),
  (N'CASE-2025-0011', N'Policy violation — undeclared external work',          N'مخالفة سياسة — عمل خارجي غير مُعلن',               N'POLICY_VIOLATION',   N'LOW',      N'WEB_FORM', NULL, 1, N'Mid-level engineer.', N'Tip indicating an engineer maintains paid open-source contracts without filing the outside-business disclosure.',                    N'Remote',                '2025-03-25', @fwMOCI, N'§4.1', N'SUBSTANTIATED', N'HR Investigations Lead', N'HR Committee',            0),
  (N'CASE-2025-0012', N'Workplace safety — near-miss in DC operations',         N'سلامة العمل — حادث وشيك في مركز البيانات',          N'SAFETY',             N'MEDIUM',   N'IN_PERSON',N'DC Engineer', 0, N'Contractor crew.', N'Cooling unit inspection — near-miss with energized panel; PPE protocol breach. RCA + retraining.',                                    N'Riyadh — DC',           '2025-04-02', NULL,    NULL,        N'CLOSED',         N'DC Operations Manager',  N'Safety Committee',        0),
  (N'CASE-2025-0013', N'Bribery suspicion — gift-card rewards programme',      N'اشتباه رشوة — برنامج بطاقات هدايا',                   N'BRIBERY',            N'HIGH',     N'HOTLINE',  NULL, 1, N'Marketing manager + agency.', N'Hotline tip alleging that an agency rewards individual employees outside the corporate referral programme.',                          N'Riyadh — Marketing',     '2025-04-16', @fw37001, N'§7.5',    N'TRIAGE',          N'Internal Audit',         N'Audit Committee',         0),
  (N'CASE-2025-0014', N'Conflict of interest — vendor onboarded by relative',  N'تضارب مصالح — مورد دخل عبر قريب',                    N'CONFLICT_OF_INTEREST',N'MEDIUM',  N'WEB_FORM', NULL, 1, N'Procurement manager.', N'Tip that a recently onboarded vendor is owned by a procurement manager''s sibling. COI disclosure absent.',                          N'Riyadh — Procurement',   '2025-04-18', @fwMOCI, N'§3.2', N'INTAKE',         N'Compliance Officer',     N'Compliance Committee',   0)
) AS v(code, en, ar, cat, sev, ch, rep, anon, subj, dsc, loc, doi, fw, cls, st, inv, cm, reg)
WHERE NOT EXISTS (SELECT 1 FROM ethics.cases c WHERE c.organisation_id=@org AND c.case_code=v.code AND c.is_deleted=0);

PRINT N'Ethics cases volume seeded.';

-- ── Disclosures (boost to 10) ───────────────────────────────────────────────
INSERT INTO ethics.disclosures
  (organisation_id, disclosure_code, disclosure_type, title_en, title_ar, description,
   discloser_name, discloser_department, discloser_role,
   counterparty_name, counterparty_relationship, monetary_value, currency, occurred_on,
   status, framework_id, clause_ref)
SELECT @org, v.code, v.tp, v.en, v.ar, v.dsc,
       v.who, v.dept, v.role, v.cp, v.cprel, v.val, v.ccy, v.occ,
       v.st, v.fw, v.cls
FROM (VALUES
  (N'DISC-2025-0006', N'GIFT_RECEIVED',        N'Branded leather portfolio from cloud vendor',           N'حافظة جلدية من مورّد سحابي',                  N'Vendor delivered a branded leather portfolio at a partner event. SAR 280.',                                      N'Eng. Khaled',         N'Cloud Engineering', N'Engineer',         N'CloudWave Middle East',     N'Active vendor',        280.00,  N'SAR', '2025-03-12', N'APPROVED', @fw37001, N'ISO 37001 §7.5'),
  (N'DISC-2025-0007', N'GIFT_GIVEN',           N'Hospitality dinner — visiting regulator',                N'عشاء عمل — جهة تنظيمية زائرة',                 N'Hosted SAMA examination team; restaurant per SAMA guidance schedule.',                                            N'Reem Al-Otaibi',     N'Compliance',         N'Head of Reg Affairs', N'SAMA Examination Team',   N'Regulator',           4500.00, N'SAR', '2025-04-02', N'APPROVED', @fw37001, N'ISO 37001 §7.5'),
  (N'DISC-2025-0008', N'CONFLICT_OF_INTEREST', N'Investor relationship with prospective vendor',          N'علاقة استثمار مع مورّد محتمل',                  N'Disclosure that family member holds a 5% stake in a vendor competing for an upcoming RFP. Recusal requested.',    N'Tariq Al-Ghamdi',    N'Procurement',        N'Senior Specialist', N'NextGen IT Solutions',     N'Family stake',        NULL,    N'SAR', NULL,         N'REVIEW',   @fwMOCI,  N'MoCI CoC §3.2'),
  (N'DISC-2025-0009', N'OUTSIDE_BUSINESS',     N'Open-source paid maintenance gig (annual renewal)',     N'عقد صيانة مفتوح المصدر — تجديد سنوي',         N'Annual renewal of declared paid open-source maintenance work. No corporate IP overlap.',                          N'Engineer (anon)',    N'Engineering',        N'Senior Eng',        N'Various',                  N'External clients',    9000.00,  N'SAR', '2025-03-31', N'APPROVED', @fwMOCI,  N'MoCI CoC §4.1'),
  (N'DISC-2025-0010', N'POLITICAL_DONATION',   N'Charitable donation — Ramadan Iftar campaign',           N'تبرع خيري — حملة إفطار رمضان',                  N'Personal donation to recognized charitable Iftar campaign during Ramadan.',                                       N'Hassan Al-Qassem',   N'Operations',         N'VP Operations',     N'Saudi Charity Org',         N'Charitable',          5000.00, N'SAR', '2025-03-15', N'APPROVED', @fwMOCI,  N'MoCI CoC §3.5')
) AS v(code, tp, en, ar, dsc, who, dept, role, cp, cprel, val, ccy, occ, st, fw, cls)
WHERE NOT EXISTS (SELECT 1 FROM ethics.disclosures d WHERE d.organisation_id=@org AND d.disclosure_code=v.code AND d.is_deleted=0);

PRINT N'Disclosures volume seeded.';

-- ── KYC subjects (boost to 10) ──────────────────────────────────────────────
INSERT INTO ethics.kyc_subjects
  (organisation_id, subject_code, subject_type, full_name, full_name_ar,
   nationality, country_of_residence, national_id, commercial_registration,
   risk_rating, is_pep, is_sanctioned, cdd_level, cdd_completed_at,
   last_screened_at, status, framework_id, notes)
SELECT @org, v.code, v.t, v.fn, v.fnAr, v.nat, v.cor, v.nid, v.cr,
       v.risk, v.pep, v.san, v.cdd, v.cddDone, v.scr, v.st, v.fw, v.notes
FROM (VALUES
  (N'KYC-IND-0003', N'INDIVIDUAL', N'Sara Al-Mutairi', N'سارة المطيري', N'SAU', N'SAU', N'1077665544', NULL, N'STANDARD', 0, 0, N'STANDARD', SYSUTCDATETIME(), SYSUTCDATETIME(), N'APPROVED', @fwSAMA, N'Retail customer; routine onboarding.'),
  (N'KYC-IND-0004', N'INDIVIDUAL', N'Ahmed Bin Faisal', N'أحمد بن فيصل', N'SAU', N'SAU', N'1066554433', NULL, N'HIGH',     1, 0, N'ENHANCED', SYSUTCDATETIME(), SYSUTCDATETIME(), N'UNDER_REVIEW', @fwSAMA, N'Domestic PEP — provincial council member; EDD in progress.'),
  (N'KYC-LE-0003', N'LEGAL_ENTITY', N'Al-Faisaliah Holding', N'مجموعة الفيصلية القابضة', N'SAU', N'SAU', NULL, N'1010111222', N'STANDARD', 0, 0, N'STANDARD', SYSUTCDATETIME(), SYSUTCDATETIME(), N'APPROVED', @fwCMA, N'Corporate customer; UBOs verified.'),
  (N'KYC-LE-0004', N'LEGAL_ENTITY', N'Riyadh Trading FZE',  N'الرياض للتجارة المنطقة الحرة', N'ARE', N'ARE', NULL, N'1020333444', N'HIGH',     0, 0, N'ENHANCED', SYSUTCDATETIME(), SYSUTCDATETIME(), N'PENDING', @fwSAMA,  N'Free-zone entity; EDD scheduled.'),
  (N'KYC-IND-0005', N'INDIVIDUAL', N'Faisal Al-Subaie',    N'فيصل السبيعي',  N'SAU', N'SAU', N'1099887700', NULL, N'LOW',      0, 0, N'SIMPLIFIED', SYSUTCDATETIME(), SYSUTCDATETIME(), N'APPROVED', @fwSAMA, N'Retail customer; low-risk simplified CDD per SAMA AML §3-2-3.'),
  (N'KYC-LE-0005', N'LEGAL_ENTITY', N'Sand Cliffs Trading', N'صحارى للتجارة', N'SAU', N'SAU', NULL, N'1030555666', N'HIGH',     0, 0, N'ENHANCED', NULL,            SYSUTCDATETIME(), N'UNDER_REVIEW', @fwSAMA, N'Cash-intensive business; EDD escalated.')
) AS v(code, t, fn, fnAr, nat, cor, nid, cr, risk, pep, san, cdd, cddDone, scr, st, fw, notes)
WHERE NOT EXISTS (SELECT 1 FROM ethics.kyc_subjects s WHERE s.organisation_id=@org AND s.subject_code=v.code AND s.is_deleted=0);

PRINT N'KYC subjects volume seeded.';

-- ── AML alerts (boost to 10) ───────────────────────────────────────────────
INSERT INTO ethics.aml_alerts
  (organisation_id, alert_code, alert_type, severity, rule_id, rule_name,
   title_en, title_ar, description,
   subject_name, counterparty_name, counterparty_country,
   transaction_count, aggregate_amount, currency, period_from, period_to,
   first_detected_at, status, analyst_name,
   sla_due_at, framework_id, clause_ref)
SELECT @org, v.code, v.tp, v.sev, v.rid, v.rname, v.en, v.ar, v.dsc,
       v.subjN, v.cpN, v.cpC, v.tc, v.amt, v.ccy, v.pf, v.pt,
       v.fdt, v.st, v.an, v.sla, v.fw, v.cls
FROM (VALUES
  (N'AML-2025-0005', N'CASH_INTENSIVE',     N'MEDIUM',   N'TM-021', N'Cash-intensive business pattern',     N'Recurring large cash deposits — Sand Cliffs Trading', N'إيداعات نقدية كبيرة متكررة', N'8 cash deposits totaling SAR 2.3M in 30 days; consistent with cash-intensive business but flagged for review.', N'Sand Cliffs Trading',  N'Branch deposits',          N'SAU', 8,  2300000.00, N'SAR', '2025-04-01', '2025-04-29', '2025-04-30 03:00', N'OPEN',          NULL,                              '2025-05-30 03:00', @fwSAMA, N'SAMA AML §3-2-7'),
  (N'AML-2025-0006', N'UNUSUAL_VELOCITY',   N'HIGH',     N'TM-009', N'Unusual transaction velocity',         N'Velocity spike — corporate account',                  N'ارتفاع غير معتاد في وتيرة المعاملات', N'Transaction count tripled vs 90-day baseline.',                                                              N'Riyadh Trading FZE',   N'Multiple counter-parties', N'ARE', 47, 850000.00,  N'SAR', '2025-04-15', '2025-04-22', '2025-04-23 03:00', N'INVESTIGATING', N'AML Analyst — Layla Al-Anazi',  '2025-05-23 03:00', @fwSAMA, N'SAMA AML §3-2-7'),
  (N'AML-2025-0007', N'POLITICAL_EXPOSURE', N'HIGH',     N'TM-003', N'PEP transaction monitoring',           N'PEP-linked outbound transfer',                        N'تحويل صادر مرتبط بشخصية سياسية', N'PEP-linked subject made SAR 6M outbound transfer to international consultancy. EDD source-of-funds documented.',N'Ahmed Bin Faisal',     N'Global Consultancy LLC',   N'GBR', 1,  6000000.00, N'SAR', '2025-04-26', '2025-04-26', '2025-04-27 03:00', N'CLEARED',       N'Head of FCC',                    '2025-05-27 03:00', @fwSAMA, N'SAMA AML §3-3-2'),
  (N'AML-2025-0008', N'UNKNOWN_COUNTERPARTY',N'LOW',     N'TM-018', N'Counterparty not in vendor list',     N'New counter-party first transaction',                 N'طرف مقابل جديد — أول معاملة', N'First-time counter-party SAR 80K wire; counter-party screening clean; cleared after KYC review.',           N'Mohammed Al-Otaibi',   N'New supplier — Oman',      N'OMN', 1,  80000.00,   N'SAR', '2025-04-20', '2025-04-20', '2025-04-21 03:00', N'CLEARED',       N'AML Analyst — Layla Al-Anazi',  '2025-05-21 03:00', @fwSAMA, N'SAMA AML §3-2-3'),
  (N'AML-2025-0009', N'RAPID_MOVEMENT',     N'MEDIUM',   N'TM-011', N'Rapid in-out movement',                N'In/out flow — corporate (Riyadh)',                    N'تدفقات سريعة دخول/خروج', N'Inbound SAR 1.2M followed by outbound SAR 1.1M within 36 hours; pass-through pattern flagged.',              N'Al-Faisaliah Holding', N'Multiple parties',         N'SAU', 6,  2400000.00, N'SAR', '2025-04-23', '2025-04-25', '2025-04-26 03:00', N'INVESTIGATING', N'Financial Crime Unit',          '2025-05-26 03:00', @fwSAMA, N'SAMA AML §3-2-7'),
  (N'AML-2025-0010', N'UNUSUAL_VOLUME',     N'CRITICAL', N'TM-005', N'Volume above 99th-percentile',         N'Volume spike — VIP customer',                          N'حجم معاملات استثنائي', N'Single transaction SAR 18M is at 99.7 percentile vs portfolio. Source-of-funds doc requested.',             N'Sara Al-Mutairi',      N'Real estate office',       N'SAU', 1,  18000000.00,N'SAR', '2025-04-29', '2025-04-29', '2025-04-30 03:00', N'OPEN',          NULL,                              '2025-05-30 03:00', @fwSAMA, N'SAMA AML §3-2-7')
) AS v(code, tp, sev, rid, rname, en, ar, dsc, subjN, cpN, cpC, tc, amt, ccy, pf, pt, fdt, st, an, sla, fw, cls)
WHERE NOT EXISTS (SELECT 1 FROM ethics.aml_alerts a WHERE a.organisation_id=@org AND a.alert_code=v.code AND a.is_deleted=0);

PRINT N'AML alerts volume seeded.';

-- ── Result summary ──────────────────────────────────────────────────────────
SELECT N'risks'             AS module, COUNT(*) AS rows FROM risk.risks WHERE is_deleted=0
UNION ALL SELECT N'findings',    COUNT(*) FROM compliance.findings WHERE is_deleted=0
UNION ALL SELECT N'controls',    COUNT(*) FROM compliance.controls WHERE is_deleted=0
UNION ALL SELECT N'policies',    COUNT(*) FROM compliance.policies WHERE is_deleted=0
UNION ALL SELECT N'kri_readings',COUNT(*) FROM risk.kri_readings
UNION ALL SELECT N'engagements', COUNT(*) FROM compliance.audit_engagements WHERE is_deleted=0
UNION ALL SELECT N'changes',     COUNT(*) FROM change_mgmt.change_requests WHERE is_deleted=0
UNION ALL SELECT N'ethics_cases',COUNT(*) FROM ethics.cases WHERE is_deleted=0
UNION ALL SELECT N'disclosures', COUNT(*) FROM ethics.disclosures WHERE is_deleted=0
UNION ALL SELECT N'kyc_subjects',COUNT(*) FROM ethics.kyc_subjects WHERE is_deleted=0
UNION ALL SELECT N'aml_alerts',  COUNT(*) FROM ethics.aml_alerts WHERE is_deleted=0
UNION ALL SELECT N'vendors',     COUNT(*) FROM tprm.vendors WHERE is_deleted=0
UNION ALL SELECT N'incidents',   COUNT(*) FROM cyber.incidents WHERE is_deleted=0;
GO
