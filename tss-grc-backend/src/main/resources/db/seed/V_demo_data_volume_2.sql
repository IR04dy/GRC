-- ============================================================================
-- TSS GRC — Demo data volume part 2: fill every empty tab + table
-- ============================================================================
-- Round-2 seed targeting tabs that were still showing empty states.
-- Idempotent — safe to re-run.
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET DATEFORMAT ymd;

DECLARE @org BIGINT = 1;

-- ── risk.risk_assessments (assessments tab) ────────────────────────────────
INSERT INTO risk.risk_assessments (risk_id, assessor_id, assessment_date, likelihood, impact, assessment_notes_en)
SELECT TOP 25 r.id, NULL,
       DATEADD(DAY, -ABS(CHECKSUM(NEWID())) % 60, CAST(GETUTCDATE() AS DATE)),
       3 + (ABS(CHECKSUM(NEWID())) % 3),
       3 + (ABS(CHECKSUM(NEWID())) % 3),
       N'Quarterly assessment — score reaffirmed; mitigation plan tracking on schedule.'
FROM risk.risks r
WHERE r.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM risk.risk_assessments a WHERE a.risk_id = r.id);
PRINT N'Risk assessments seeded.';

-- ── risk.risk_treatments ────────────────────────────────────────────────────
INSERT INTO risk.risk_treatments (risk_id, treatment_type, description_en, due_date, status)
SELECT TOP 25 r.id,
       (SELECT TOP 1 v FROM (VALUES (N'MITIGATE'),(N'TRANSFER'),(N'ACCEPT'),(N'AVOID')) AS x(v) ORDER BY ABS(CHECKSUM(NEWID()))),
       N'Treatment plan: implement compensating controls + monitor KRIs; review quarterly.',
       DATEADD(DAY, 30 + (ABS(CHECKSUM(NEWID())) % 90), CAST(GETUTCDATE() AS DATE)),
       (SELECT TOP 1 v FROM (VALUES (N'PLANNED'),(N'IN_PROGRESS'),(N'COMPLETED')) AS x(v) ORDER BY ABS(CHECKSUM(NEWID())))
FROM risk.risks r
WHERE r.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM risk.risk_treatments t WHERE t.risk_id = r.id);
PRINT N'Risk treatments seeded.';

-- ── risk.risk_indicators (legacy KRI cards on risk page) ───────────────────
INSERT INTO risk.risk_indicators (organisation_id, risk_id, name_en, name_ar, threshold_value, current_value, unit_en, status, last_updated_at, is_active)
SELECT @org, r.id, v.en, v.ar, v.thr, v.cur, v.unit, v.st, SYSUTCDATETIME(), 1
FROM (VALUES
  (N'Critical vulnerabilities open > 30 days',  N'الثغرات الحرجة المفتوحة لأكثر من 30 يومًا',  CAST(2  AS DECIMAL(18,4)), CAST(3  AS DECIMAL(18,4)), N'count',   N'BREACHED'),
  (N'Phishing simulation click-rate %',          N'معدل النقر في محاكاة التصيد',                CAST(8  AS DECIMAL(18,4)), CAST(11 AS DECIMAL(18,4)), N'%',       N'WARNING'),
  (N'Patch SLA compliance %',                    N'الالتزام بـ SLA الترقيع',                    CAST(95 AS DECIMAL(18,4)), CAST(92 AS DECIMAL(18,4)), N'%',       N'WARNING'),
  (N'Mean time to detect (MTTD) — minutes',      N'متوسط وقت الاكتشاف (دقائق)',                  CAST(15 AS DECIMAL(18,4)), CAST(8  AS DECIMAL(18,4)), N'minutes', N'NORMAL'),
  (N'Open privileged access reviews',            N'مراجعات الوصول المُتميّز المفتوحة',           CAST(5  AS DECIMAL(18,4)), CAST(2  AS DECIMAL(18,4)), N'count',   N'NORMAL'),
  (N'PDPL DSR overdue (>30 days)',               N'طلبات PDPL المتأخرة (>30 يومًا)',             CAST(0  AS DECIMAL(18,4)), CAST(2  AS DECIMAL(18,4)), N'count',   N'BREACHED'),
  (N'Vendor SLA breaches (30d)',                 N'انتهاكات SLA للمورّدين (30 يومًا)',           CAST(2  AS DECIMAL(18,4)), CAST(4  AS DECIMAL(18,4)), N'count',   N'BREACHED'),
  (N'Mean time to treat — days',                 N'متوسط وقت المعالجة (أيام)',                   CAST(21 AS DECIMAL(18,4)), CAST(14 AS DECIMAL(18,4)), N'days',    N'NORMAL')
) AS v(en, ar, thr, cur, unit, st)
CROSS APPLY (SELECT TOP 1 id FROM risk.risks WHERE organisation_id=@org AND is_deleted=0 ORDER BY id) r
WHERE NOT EXISTS (SELECT 1 FROM risk.risk_indicators i WHERE i.organisation_id=@org AND i.name_en=v.en AND i.is_deleted=0);
PRINT N'Legacy risk indicators seeded.';

-- ── risk.kri_breach_events ──────────────────────────────────────────────────
INSERT INTO risk.kri_breach_events (kri_id, reading_id, breach_band, reading_value, threshold_crossed, breached_at, status)
SELECT TOP 8 r.kri_id, r.id, r.band, r.reading_value,
       ISNULL((SELECT amber_max FROM risk.kri_definitions WHERE id = r.kri_id), CAST(0 AS DECIMAL(18,4))),
       r.reading_at,
       (SELECT TOP 1 v FROM (VALUES (N'OPEN'),(N'ACKNOWLEDGED'),(N'RESOLVED')) AS x(v) ORDER BY ABS(CHECKSUM(NEWID())))
FROM risk.kri_readings r
WHERE r.band IN (N'AMBER', N'RED')
  AND NOT EXISTS (SELECT 1 FROM risk.kri_breach_events b WHERE b.reading_id = r.id);
PRINT N'KRI breach events seeded.';

-- ── risk.appetite_breach_events ─────────────────────────────────────────────
INSERT INTO risk.appetite_breach_events (appetite_id, observed_value, breach_severity, breached_at, notes, status)
SELECT a.id, a.current_exposure,
       CASE WHEN a.current_exposure > a.tolerance_upper * 1.25 THEN N'CRITICAL'
            WHEN a.current_exposure > a.tolerance_upper THEN N'EXCEEDED'
            ELSE N'WITHIN_TOLERANCE' END,
       SYSUTCDATETIME(),
       N'Auto-evaluated breach event — current exposure crossed tolerance band.',
       N'OPEN'
FROM risk.risk_appetite_statements a
WHERE a.is_deleted = 0
  AND a.tolerance_upper IS NOT NULL
  AND a.current_exposure IS NOT NULL
  AND a.current_exposure > a.tolerance_upper
  AND NOT EXISTS (SELECT 1 FROM risk.appetite_breach_events b WHERE b.appetite_id = a.id);
PRINT N'Appetite breach events seeded.';

-- ── tprm.vendor_assessments ─────────────────────────────────────────────────
INSERT INTO tprm.vendor_assessments (vendor_id, assessment_date, assessor, risk_score, risk_rating, status, findings, next_review_date)
SELECT v.id,
       DATEADD(DAY, -ABS(CHECKSUM(NEWID())) % 90, CAST(GETUTCDATE() AS DATE)),
       N'TPRM Lead',
       50 + (ABS(CHECKSUM(NEWID())) % 50),
       (SELECT TOP 1 r FROM (VALUES (N'LOW'),(N'MEDIUM'),(N'HIGH')) AS x(r) ORDER BY ABS(CHECKSUM(NEWID()))),
       (SELECT TOP 1 r FROM (VALUES (N'COMPLETED'),(N'IN_PROGRESS'),(N'PLANNED')) AS x(r) ORDER BY ABS(CHECKSUM(NEWID()))),
       N'SOC 2 Type II reviewed; ISO 27001 cert valid; financial health stable; no material issues.',
       DATEADD(DAY, 180, CAST(GETUTCDATE() AS DATE))
FROM tprm.vendors v
WHERE v.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM tprm.vendor_assessments a WHERE a.vendor_id = v.id);
PRINT N'Vendor assessments seeded.';

-- ── privacy.dpia_question ───────────────────────────────────────────────────
INSERT INTO privacy.dpia_question (dpia_id, question_code, question_en, question_ar, answer, risk_level, sort_order)
SELECT d.id, q.code, q.en, q.ar, q.ans, q.risk, q.so
FROM privacy.dpia d
CROSS APPLY (VALUES
  (N'PDPL-7-1',  N'What is the purpose of processing personal data?',                     N'ما الغرض من معالجة البيانات الشخصية؟',                  N'Customer onboarding identity verification and KYC compliance.',                      N'LOW',      10),
  (N'PDPL-7-2',  N'Do you process special-category data (health, biometric, religious)?',  N'هل تتم معالجة فئات بيانات خاصة (صحية، حيوية، دينية)؟', N'Yes — biometric data via Sehhaty integration and health attestation forms.',         N'HIGH',     20),
  (N'PDPL-7-3',  N'Are data subjects informed and able to give consent?',                  N'هل أصحاب البيانات مُبلَّغون وقادرون على الموافقة؟',     N'Yes — explicit consent at sign-up plus granular preferences in account settings.',   N'LOW',      30),
  (N'PDPL-7-4',  N'Is there cross-border transfer of personal data?',                       N'هل هناك تحويل عابر للحدود للبيانات الشخصية؟',           N'No — all processing within KSA-resident infrastructure.',                            N'LOW',      40),
  (N'PDPL-7-5',  N'How long is the data retained?',                                          N'ما مدة الاحتفاظ بالبيانات؟',                            N'7 years per SAMA regulation; auto-purge thereafter.',                                 N'MEDIUM',   50),
  (N'PDPL-7-6',  N'Are appropriate technical and organisational measures in place?',        N'هل التدابير التقنية والتنظيمية المناسبة موجودة؟',       N'Encryption at rest + in transit; RBAC; audit logging; vendor due diligence.',         N'LOW',      60)
) AS q(code, en, ar, ans, risk, so)
WHERE d.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM privacy.dpia_question dq WHERE dq.dpia_id = d.id AND dq.question_code = q.code);
PRINT N'DPIA questions seeded.';

-- ── privacy.dpia_mitigation ─────────────────────────────────────────────────
INSERT INTO privacy.dpia_mitigation (dpia_id, mitigation_en, mitigation_ar, status, target_date, sort_order)
SELECT d.id, m.en, m.ar, m.st, m.tdate, m.so
FROM privacy.dpia d
CROSS APPLY (VALUES
  (N'Encrypt all personal data at rest using TDE.',                       N'تشفير البيانات الشخصية في حالة السكون باستخدام TDE.',  N'IMPLEMENTED', DATEADD(DAY, -30, CAST(GETUTCDATE() AS DATE)),  10),
  (N'Restrict access via RBAC + least-privilege review every 90 days.',   N'تقييد الوصول عبر RBAC ومراجعة الصلاحيات كل 90 يومًا.', N'IMPLEMENTED', DATEADD(DAY, -45, CAST(GETUTCDATE() AS DATE)),  20),
  (N'Run quarterly DPIA review with the DPO.',                            N'مراجعة ربع سنوية لتقييم الأثر مع مسؤول حماية البيانات.',N'IN_PROGRESS', DATEADD(DAY, 30, CAST(GETUTCDATE() AS DATE)),    30),
  (N'Document data-subject rights workflow + train CSR team.',            N'توثيق سير عمل حقوق أصحاب البيانات وتدريب فريق الخدمة.',N'PLANNED',     DATEADD(DAY, 60, CAST(GETUTCDATE() AS DATE)),    40)
) AS m(en, ar, st, tdate, so)
WHERE d.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM privacy.dpia_mitigation dm WHERE dm.dpia_id = d.id AND dm.mitigation_en = m.en);
PRINT N'DPIA mitigations seeded.';

-- ── privacy.dpia_review ─────────────────────────────────────────────────────
INSERT INTO privacy.dpia_review (dpia_id, reviewer_id, reviewer_name, decision, comments, decided_at)
SELECT d.id, NULL, r.who, r.dec, r.com, r.dat
FROM privacy.dpia d
CROSS APPLY (VALUES
  (N'Mr. Faisal Al-Otaibi (DPO)', N'APPROVE', N'Mitigations are sufficient; approving the DPIA with quarterly review cadence.',  DATEADD(DAY, -10, SYSUTCDATETIME())),
  (N'Group Compliance Director',  N'APPROVE', N'Reviewed and aligned with PDPL Implementing Regulations; approved.',             DATEADD(DAY, -8, SYSUTCDATETIME())),
  (N'CISO',                       N'REQUEST_CHANGES', N'Need confirmation that biometric template storage uses HSM-backed keys.', DATEADD(DAY, -5, SYSUTCDATETIME()))
) AS r(who, dec, com, dat)
WHERE d.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM privacy.dpia_review dr WHERE dr.dpia_id = d.id AND dr.reviewer_name = r.who);
PRINT N'DPIA reviews seeded.';

-- ── compliance.evidence_items ───────────────────────────────────────────────
INSERT INTO compliance.evidence_items
  (organisation_id, control_id, evidence_type, title_en, collection_date, expiry_date, integrity_hash, is_sufficient)
SELECT @org, c.id, e.tp, e.t, e.cd, e.exp, LOWER(CONVERT(NVARCHAR(64), HASHBYTES('SHA2_256', CAST(c.id AS NVARCHAR(20)) + e.t), 2)), 1
FROM compliance.controls c
CROSS APPLY (VALUES
  (N'POLICY_DOC',      N'Approved policy document v.current',     CAST(GETUTCDATE() AS DATE), DATEADD(YEAR, 1, CAST(GETUTCDATE() AS DATE))),
  (N'CONFIG_SCREENSHOT', N'System configuration screenshot',       CAST(GETUTCDATE() AS DATE), DATEADD(MONTH, 6, CAST(GETUTCDATE() AS DATE))),
  (N'AUDIT_LOG',       N'90-day audit log sample',                 CAST(GETUTCDATE() AS DATE), DATEADD(MONTH, 3, CAST(GETUTCDATE() AS DATE)))
) AS e(tp, t, cd, exp)
WHERE c.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM compliance.evidence_items ev WHERE ev.control_id = c.id AND ev.title_en = e.t);
PRINT N'Compliance evidence items seeded.';

-- ── platform.business_units ─────────────────────────────────────────────────
INSERT INTO platform.business_units (organisation_id, parent_id, name_en, name_ar, bu_code, is_active)
SELECT @org, NULL, v.en, v.ar, v.code, 1
FROM (VALUES
  (N'Corporate Banking',           N'الخدمات المصرفية للشركات',     N'BU-CB'),
  (N'Retail Banking',              N'الخدمات المصرفية للأفراد',     N'BU-RB'),
  (N'Treasury & Markets',          N'الخزينة وأسواق المال',          N'BU-TM'),
  (N'Information Technology',      N'تقنية المعلومات',                N'BU-IT'),
  (N'Risk & Compliance',           N'إدارة المخاطر والامتثال',       N'BU-RC'),
  (N'Operations',                  N'العمليات',                       N'BU-OP'),
  (N'Human Resources',             N'الموارد البشرية',                N'BU-HR'),
  (N'Finance',                     N'المالية',                        N'BU-FIN'),
  (N'Legal',                       N'الشؤون القانونية',                N'BU-LG'),
  (N'Internal Audit',              N'المراجعة الداخلية',              N'BU-IA')
) AS v(en, ar, code)
WHERE NOT EXISTS (SELECT 1 FROM platform.business_units b WHERE b.organisation_id=@org AND b.bu_code=v.code AND b.is_deleted=0);
PRINT N'Business units seeded.';

-- ── platform.notifications (bell icon feed) ────────────────────────────────
INSERT INTO platform.notifications
  (organisation_id, recipient_id, title_en, title_ar, message_en, message_ar, notification_type, entity_type, entity_id, is_read, created_at)
SELECT @org, u.id, v.te, v.ta, v.me, v.ma, v.nt, v.et, v.eid, v.r, DATEADD(HOUR, -v.h, SYSUTCDATETIME())
FROM iam.users u
CROSS APPLY (VALUES
  (N'New finding raised — Patch SLA breach',     N'ملاحظة جديدة — تجاوز SLA الترقيع',          N'Critical CVEs open >30 days; CISO Office assigned.',         N'ثغرات حرجة مفتوحة لأكثر من 30 يومًا.',                  N'INFO',    N'FINDING',     2, 0, 1),
  (N'Policy review-due — AUP v3.2',              N'سياسة قيد المراجعة — AUP v3.2',             N'Policy AUP v3.2 is review-due in 14 days.',                  N'سياسة الاستخدام v3.2 — موعد المراجعة بعد 14 يومًا.',     N'WARNING', N'POLICY',      3, 0, 4),
  (N'KRI breach — Phishing CTR',                  N'تجاوز عتبة المؤشر — معدل النقر',            N'Phishing simulation CTR 11% > amber threshold 8%.',         N'معدل النقر 11% فوق العتبة الصفراء.',                     N'WARNING', N'KRI',         2, 0, 18),
  (N'Vendor SOC 2 expired — TechCorp',            N'انتهاء شهادة SOC 2 — مورّد رئيسي',           N'Critical vendor SOC 2 Type II expired 60 days ago.',        N'انتهت شهادة المورّد قبل 60 يومًا.',                       N'DANGER',  N'VENDOR',      3, 1, 36),
  (N'New change request — CHG-2025-0010',         N'طلب تغيير جديد — CHG-2025-0010',             N'Apache Tomcat patch — emergency change waiting CAB review.',N'ترقيع Apache Tomcat — في انتظار مراجعة CAB.',           N'INFO',    N'CHANGE',      10, 1, 50),
  (N'AML alert — structuring pattern',            N'تنبيه AML — نمط هيكلة',                       N'TM-007 fired on BlueWave Trading Co. — investigate.',       N'تنبيه TM-007 على شركة بلو ويف للتجارة.',                  N'DANGER',  N'AML_ALERT',   1, 0, 6),
  (N'Audit engagement opened — NCA ECC',          N'فتح مهمة تدقيق — NCA ECC',                  N'Internal NCA ECC v2.0 audit kicked off; fieldwork May 1.',  N'بدأت مهمة تدقيق NCA ECC الداخلية.',                       N'INFO',    N'ENGAGEMENT',  2, 1, 96)
) AS v(te, ta, me, ma, nt, et, eid, r, h)
WHERE u.organisation_id = @org
  AND NOT EXISTS (SELECT 1 FROM platform.notifications n WHERE n.recipient_id=u.id AND n.title_en=v.te);
PRINT N'Notifications seeded.';

-- ── audit.audit_logs ────────────────────────────────────────────────────────
INSERT INTO audit.audit_logs
  (organisation_id, event_type, entity_type, entity_id, user_id, username, ip_address, change_summary_en, event_timestamp, module)
SELECT @org, v.et, v.ent, v.eid, NULL, v.usr, v.ip, v.cs, DATEADD(MINUTE, -v.mins, SYSUTCDATETIME()), v.mod
FROM (VALUES
  (N'CREATE', N'RISK',        50, N'admin',         N'10.0.0.12', N'Risk created — Web application SQLi.',                       15,    N'RISK'),
  (N'UPDATE', N'RISK',        49, N'riskmanager',   N'10.0.0.12', N'Risk reviewed — score adjusted from 16 → 12 (residual).',    180,   N'RISK'),
  (N'CREATE', N'FINDING',     30, N'auditor',       N'10.0.0.13', N'Finding raised — Encryption-at-rest gap on legacy DW.',      45,    N'COMPLIANCE'),
  (N'UPDATE', N'POLICY',      3,  N'admin',         N'10.0.0.12', N'Policy AUP v3.2 marked review-due.',                          20,    N'COMPLIANCE'),
  (N'CREATE', N'KRI_READING', 60, N'admin',         N'10.0.0.12', N'KRI reading recorded — phishing CTR 11%.',                    8,     N'RISK'),
  (N'CREATE', N'CHANGE',      11, N'admin',         N'10.0.0.12', N'Change request CHG-2025-0011 submitted.',                     65,    N'CHANGE'),
  (N'UPDATE', N'VENDOR',      8,  N'admin',         N'10.0.0.12', N'Vendor risk rating elevated MEDIUM → HIGH.',                  12,    N'TPRM'),
  (N'CREATE', N'INCIDENT',    7,  N'admin',         N'10.0.0.13', N'Incident logged — Vendor data breach notification.',          480,   N'CYBER'),
  (N'LOGIN',  N'SESSION',     NULL,N'admin',         N'10.0.0.12', N'User logged in.',                                             1,     N'AUTH'),
  (N'LOGIN',  N'SESSION',     NULL,N'auditor',       N'10.0.0.13', N'User logged in.',                                             32,    N'AUTH'),
  (N'CREATE', N'CASE',        14, N'admin',         N'10.0.0.12', N'Ethics case CASE-2025-0014 logged from web form (anonymous).',5,     N'ETHICS'),
  (N'UPDATE', N'AML_ALERT',   2,  N'admin',         N'10.0.0.12', N'AML alert AML-2025-0002 status → STR_FILED (STR-2025-0042).', 720,   N'ETHICS'),
  (N'DELETE', N'EVIDENCE',    12, N'admin',         N'10.0.0.12', N'Evidence record archived per retention policy.',              1440,  N'COMPLIANCE'),
  (N'CREATE', N'KYC_SUBJECT', 6,  N'admin',         N'10.0.0.12', N'KYC subject onboarded — Sand Cliffs Trading (HIGH risk).',    2880,  N'ETHICS')
) AS v(et, ent, eid, usr, ip, cs, mins, mod)
WHERE NOT EXISTS (SELECT 1 FROM audit.audit_logs a WHERE a.organisation_id=@org AND a.entity_type=v.ent AND a.event_type=v.et AND a.change_summary_en=v.cs);
PRINT N'Audit log entries seeded.';

-- ── cyber.vulnerabilities (boost) ──────────────────────────────────────────
INSERT INTO cyber.vulnerabilities (organisation_id, asset_name, title_en, title_ar, description_en, severity, cvss_score, vulnerability_type, cve_id, owner_name, sla_days, due_date, workflow_status, overdue, reopened_count)
SELECT @org, v.an, v.t, v.tar, v.dsc, v.sev, v.cvss, v.vt, v.cve, v.own, v.sla, v.due, v.ws, v.od, 0
FROM (VALUES
  (N'web-portal-prod-01',     N'Apache Log4j RCE (CVE-2021-44228)',                  N'ثغرة Apache Log4j RCE',                              N'Java Log4j 2.x JNDI lookup allowing remote code execution.',     N'CRITICAL', 10.0, N'OS_LIBRARY',  N'CVE-2021-44228', N'AppSec',     14, DATEADD(DAY, -2, CAST(GETUTCDATE() AS DATE)),  N'IN_PROGRESS', 1),
  (N'core-banking-db-01',     N'Microsoft SQL Server RCE (CVE-2024-37985)',           N'ثغرة Microsoft SQL Server RCE',                       N'SQL Server vulnerability allowing privileged command execution.', N'CRITICAL', 9.8,  N'DATABASE',    N'CVE-2024-37985', N'DBA Lead',   14, DATEADD(DAY, 7, CAST(GETUTCDATE() AS DATE)),    N'IN_PROGRESS', 0),
  (N'cust-portal-web-02',     N'OpenSSL Heartbleed (CVE-2014-0160)',                  N'ثغرة OpenSSL Heartbleed',                            N'TLS heartbeat extension memory leak.',                            N'HIGH',     7.5,  N'CRYPTOGRAPHY',N'CVE-2014-0160',  N'NetSec',     30, DATEADD(DAY, -5, CAST(GETUTCDATE() AS DATE)),  N'OPEN',        1),
  (N'jenkins-build-01',       N'Jenkins arbitrary file read (CVE-2024-23897)',        N'ثغرة قراءة ملفات في Jenkins',                          N'CLI command processing allows reading arbitrary files.',          N'HIGH',     8.0,  N'APPLICATION', N'CVE-2024-23897', N'AppSec',     30, DATEADD(DAY, 12, CAST(GETUTCDATE() AS DATE)),   N'IN_PROGRESS', 0),
  (N'ad-domain-ctrl-01',      N'Windows Server SMB signing not required',             N'ضبط توقيع SMB غير مفعّل',                            N'Domain controllers not enforcing SMB signing — relay attack.',     N'MEDIUM',   5.5,  N'CONFIGURATION',NULL,             N'IT Infra',   60, DATEADD(DAY, 30, CAST(GETUTCDATE() AS DATE)),   N'OPEN',        0),
  (N'kubernetes-cluster-01',  N'kube-apiserver authn bypass (CVE-2025-1974)',         N'تجاوز المصادقة kube-apiserver',                       N'Authentication bypass via crafted bearer token.',                  N'CRITICAL', 9.6,  N'CONTAINER',   N'CVE-2025-1974',  N'CloudSec',   14, DATEADD(DAY, 4, CAST(GETUTCDATE() AS DATE)),    N'IN_PROGRESS', 0),
  (N'fortinet-fw-edge-01',    N'FortiOS authn bypass (CVE-2024-21762)',                N'تجاوز المصادقة في FortiOS',                          N'Out-of-bounds write enabling authentication bypass.',              N'CRITICAL', 9.8,  N'NETWORK',     N'CVE-2024-21762', N'NetOps',     14, DATEADD(DAY, -10, CAST(GETUTCDATE() AS DATE)), N'IN_PROGRESS', 1),
  (N'office365-tenant',       N'Outdated MFA enforcement on legacy auth',             N'فرض MFA على المصادقة القديمة',                       N'Some service accounts still permit basic-auth fallback.',          N'HIGH',     7.0,  N'CONFIGURATION',NULL,             N'IAM Lead',   30, DATEADD(DAY, 18, CAST(GETUTCDATE() AS DATE)),   N'OPEN',        0)
) AS v(an, t, tar, dsc, sev, cvss, vt, cve, own, sla, due, ws, od)
WHERE NOT EXISTS (SELECT 1 FROM cyber.vulnerabilities x WHERE x.organisation_id=@org AND x.title_en=v.t AND x.is_deleted=0);
PRINT N'Vulnerabilities boosted.';

-- ── cyber.vuln_exceptions (boost) ──────────────────────────────────────────
INSERT INTO cyber.vuln_exceptions
  (organisation_id, vulnerability_id, title_en, exception_reason, workflow_status, owner_name, approver_name, compensating_controls, impact_assessment, expiry_date)
SELECT @org, v.id, N'Risk-accepted: ' + LEFT(v.title_en, 80),
       N'Compensating controls in place; vendor patch deferred to next maintenance window.',
       N'APPROVED', v.owner_name, N'CISO',
       N'WAF rule blocking exploitation pattern; IPS signature deployed.',
       N'Residual risk: LOW. Quarterly review.',
       DATEADD(MONTH, 6, CAST(GETUTCDATE() AS DATE))
FROM cyber.vulnerabilities v
WHERE v.is_deleted = 0
  AND v.severity IN (N'MEDIUM', N'LOW')
  AND NOT EXISTS (SELECT 1 FROM cyber.vuln_exceptions e WHERE e.vulnerability_id = v.id);
PRINT N'Vulnerability exceptions seeded.';

-- ── workflow.instances + action_log ─────────────────────────────────────────
DECLARE @wfDef BIGINT = (SELECT TOP 1 id FROM workflow.definitions);
DECLARE @stOpen BIGINT = (SELECT TOP 1 id FROM workflow.states WHERE code = N'OPEN');
DECLARE @stUR   BIGINT = (SELECT TOP 1 id FROM workflow.states WHERE code = N'UNDER_REVIEW');
DECLARE @stMit  BIGINT = (SELECT TOP 1 id FROM workflow.states WHERE code = N'MITIGATING');
DECLARE @stClosed BIGINT = (SELECT TOP 1 id FROM workflow.states WHERE code = N'CLOSED');
DECLARE @transit BIGINT = (SELECT TOP 1 id FROM workflow.transitions WHERE definition_id = @wfDef);

IF @wfDef IS NOT NULL AND NOT EXISTS (SELECT 1 FROM workflow.instances)
BEGIN
    INSERT INTO workflow.instances (organisation_id, definition_id, target_module, target_entity, target_entity_id, current_state_id, started_at, last_transition_at)
    SELECT TOP 6 @org, @wfDef, N'RISK', N'Risk', r.id,
           CASE WHEN r.status = N'OPEN' THEN @stOpen
                WHEN r.status = N'UNDER_ASSESSMENT' THEN @stUR
                WHEN r.status = N'CLOSED' THEN @stClosed
                ELSE @stMit END,
           DATEADD(DAY, -ABS(CHECKSUM(NEWID())) % 30, SYSUTCDATETIME()),
           SYSUTCDATETIME()
    FROM risk.risks r
    WHERE r.is_deleted = 0
    ORDER BY r.id DESC;

    IF @transit IS NOT NULL AND @stOpen IS NOT NULL AND @stUR IS NOT NULL
    BEGIN
        INSERT INTO workflow.action_log (instance_id, transition_id, action_code, from_state_id, to_state_id, executed_by_id, executed_at, comments)
        SELECT i.id, @transit, N'SUBMIT_FOR_REVIEW', @stOpen, @stUR, NULL, DATEADD(HOUR, -ABS(CHECKSUM(NEWID())) % 720, SYSUTCDATETIME()),
               N'Routine workflow advancement during quarterly review cycle.'
        FROM workflow.instances i
        WHERE NOT EXISTS (SELECT 1 FROM workflow.action_log a WHERE a.instance_id = i.id);
    END
END
PRINT N'Workflow instances + action log seeded.';

-- ── change_mgmt.change_links + comments ─────────────────────────────────────
INSERT INTO change_mgmt.change_links (change_request_id, entity_type, entity_id, link_type, notes)
SELECT TOP 12 c.id, l.et, l.eid, l.lt, l.n
FROM change_mgmt.change_requests c
CROSS APPLY (VALUES
  (N'RISK',           1, N'AFFECTS',    N'Change addresses identified risk in the register.'),
  (N'AUDIT_FINDING',  3, N'REMEDIATES', N'Change is the corrective action for this finding.'),
  (N'POLICY',         1, N'INFORMS',    N'Change requires update to information security policy.')
) AS l(et, eid, lt, n)
WHERE c.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM change_mgmt.change_links cl WHERE cl.change_request_id = c.id AND cl.entity_type=l.et AND cl.entity_id=l.eid AND cl.is_deleted=0);
PRINT N'Change links seeded.';

INSERT INTO change_mgmt.change_comments (change_request_id, author_name, body)
SELECT c.id, cm.who, cm.body
FROM change_mgmt.change_requests c
CROSS APPLY (VALUES
  (N'Change Mgr',  N'Reviewed scope + impact analysis. Ready for CAB review.'),
  (N'Implementer', N'Pre-prod test successful. Ready to schedule cutover.'),
  (N'CISO',        N'Security review complete. No blockers from infosec.')
) AS cm(who, body)
WHERE c.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM change_mgmt.change_comments cc WHERE cc.change_request_id = c.id AND cc.body = cm.body);
PRINT N'Change comments seeded.';

-- ── ethics.case_links ──────────────────────────────────────────────────────
INSERT INTO ethics.case_links (case_id, entity_type, entity_id, link_type, notes)
SELECT c.id, l.et, l.eid, l.lt, l.n
FROM ethics.cases c
CROSS APPLY (VALUES
  (N'RISK',           1,  N'RAISED_FROM',  N'Case raised in connection with this risk.'),
  (N'AUDIT_FINDING',  4,  N'REMEDIATED_BY',N'Audit finding addressed by case investigation outcome.'),
  (N'CHANGE_REQUEST', 4,  N'INFORMS',      N'Investigation outcome triggered policy change.')
) AS l(et, eid, lt, n)
WHERE c.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM ethics.case_links cl WHERE cl.case_id = c.id AND cl.entity_type=l.et AND cl.entity_id=l.eid AND cl.is_deleted=0);
PRINT N'Ethics case links seeded.';

-- ── ethics.screening_hits (boost — add per-subject hits) ───────────────────
INSERT INTO ethics.screening_hits (subject_id, list_source, list_entry_ref, match_score, match_summary, disposition)
SELECT TOP 8 s.id,
       (SELECT TOP 1 v FROM (VALUES (N'OFAC_SDN'),(N'UN_CONSOLIDATED'),(N'PEP_GLOBAL'),(N'ADVERSE_MEDIA')) AS x(v) ORDER BY ABS(CHECKSUM(NEWID()))),
       N'list-' + CAST(s.id AS NVARCHAR(20)),
       60 + ABS(CHECKSUM(NEWID())) % 30,
       N'Routine screening match — name partial; reviewed and dispositioned.',
       (SELECT TOP 1 v FROM (VALUES (N'FALSE_POSITIVE'),(N'NEEDS_REVIEW')) AS x(v) ORDER BY ABS(CHECKSUM(NEWID())))
FROM ethics.kyc_subjects s
WHERE s.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM ethics.screening_hits h WHERE h.subject_id = s.id AND h.list_entry_ref = N'list-' + CAST(s.id AS NVARCHAR(20)));
PRINT N'Screening hits boosted.';

-- ── bcm.crisis_comms_distributions ────────────────────────────────────────
DECLARE @plan BIGINT = (SELECT TOP 1 id FROM bcm.bcm_plans);
DECLARE @tmpl BIGINT = (SELECT TOP 1 id FROM bcm.crisis_comms_templates);
DECLARE @aud  BIGINT = (SELECT TOP 1 id FROM bcm.crisis_comms_audiences);
IF @plan IS NOT NULL AND @tmpl IS NOT NULL AND @aud IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM bcm.crisis_comms_distributions WHERE organisation_id = @org)
BEGIN
    INSERT INTO bcm.crisis_comms_distributions
        (organisation_id, plan_id, template_id, audience_id, channel, subject_snapshot, body_snapshot, recipient_count, sent_at, delivery_status, delivery_summary)
    VALUES
      (@org, @plan, @tmpl, @aud, N'EMAIL', N'[ALERT] Service disruption — investigation underway',
       N'A service disruption was detected at 14:23 UTC; teams are investigating. Updates every 30 min.',
       2400, DATEADD(HOUR, -3, SYSUTCDATETIME()),  N'DELIVERED', N'2,392 delivered / 8 bounced'),
      (@org, @plan, @tmpl, @aud, N'SMS',   N'[NOTICE] Service restored — see email for details',
       N'Service is restored. Follow-up communication will detail root cause + remediation.',
       2400, DATEADD(HOUR, -1, SYSUTCDATETIME()),  N'DELIVERED', N'2,401 delivered / 0 failed');
END
PRINT N'Crisis comms distributions seeded.';

-- ── reporting.report_runs (synthesised history) ────────────────────────────
INSERT INTO reporting.report_runs (schedule_id, organisation_id, triggered_at, triggered_by, status, duration_ms, bytes, recipients, message)
SELECT s.id, @org,
       DATEADD(DAY, -7 * n.n, SYSUTCDATETIME()),
       (SELECT TOP 1 v FROM (VALUES (N'CRON'),(N'MANUAL')) AS x(v) ORDER BY ABS(CHECKSUM(NEWID()))),
       (SELECT TOP 1 v FROM (VALUES (N'SUCCESS'),(N'SUCCESS'),(N'SUCCESS'),(N'FAILED')) AS x(v) ORDER BY ABS(CHECKSUM(NEWID()))),
       400 + ABS(CHECKSUM(NEWID())) % 1200,
       80000 + ABS(CHECKSUM(NEWID())) % 200000,
       s.recipients,
       N'Auto-historical run for demo display.'
FROM reporting.report_schedules s
CROSS JOIN (VALUES (1),(2),(3),(4),(5),(6)) AS n(n)
WHERE NOT EXISTS (SELECT 1 FROM reporting.report_runs r WHERE r.schedule_id = s.id);
PRINT N'Report runs (history) seeded.';

-- ── Final summary ──────────────────────────────────────────────────────────
SELECT
    N'risk_assessments'         AS module, COUNT(*) AS rows FROM risk.risk_assessments
UNION ALL SELECT N'risk_treatments',           COUNT(*) FROM risk.risk_treatments WHERE is_deleted=0
UNION ALL SELECT N'risk_indicators',           COUNT(*) FROM risk.risk_indicators WHERE is_deleted=0
UNION ALL SELECT N'kri_breach_events',         COUNT(*) FROM risk.kri_breach_events
UNION ALL SELECT N'appetite_breach_events',    COUNT(*) FROM risk.appetite_breach_events
UNION ALL SELECT N'tprm_assessments',          COUNT(*) FROM tprm.vendor_assessments WHERE is_deleted=0
UNION ALL SELECT N'dpia_questions',            COUNT(*) FROM privacy.dpia_question
UNION ALL SELECT N'dpia_mitigations',          COUNT(*) FROM privacy.dpia_mitigation
UNION ALL SELECT N'dpia_reviews',              COUNT(*) FROM privacy.dpia_review
UNION ALL SELECT N'compliance_evidence_items', COUNT(*) FROM compliance.evidence_items WHERE is_deleted=0
UNION ALL SELECT N'business_units',            COUNT(*) FROM platform.business_units WHERE is_deleted=0
UNION ALL SELECT N'notifications',             COUNT(*) FROM platform.notifications
UNION ALL SELECT N'audit_logs',                COUNT(*) FROM audit.audit_logs
UNION ALL SELECT N'cyber_vulns',               COUNT(*) FROM cyber.vulnerabilities WHERE is_deleted=0
UNION ALL SELECT N'cyber_vuln_exc',            COUNT(*) FROM cyber.vuln_exceptions WHERE is_deleted=0
UNION ALL SELECT N'workflow_instances',        COUNT(*) FROM workflow.instances
UNION ALL SELECT N'workflow_action_log',       COUNT(*) FROM workflow.action_log
UNION ALL SELECT N'change_links',              COUNT(*) FROM change_mgmt.change_links WHERE is_deleted=0
UNION ALL SELECT N'change_comments',           COUNT(*) FROM change_mgmt.change_comments
UNION ALL SELECT N'ethics_case_links',         COUNT(*) FROM ethics.case_links WHERE is_deleted=0
UNION ALL SELECT N'screening_hits',            COUNT(*) FROM ethics.screening_hits
UNION ALL SELECT N'crisis_comms_dist',         COUNT(*) FROM bcm.crisis_comms_distributions
UNION ALL SELECT N'report_runs',               COUNT(*) FROM reporting.report_runs;

GO
