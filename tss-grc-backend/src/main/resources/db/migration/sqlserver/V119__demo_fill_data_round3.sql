-- V119: round-3 demo fill — last empty / thin tables.
--
-- After V117 + V118, three core platform-level tables were still empty
-- (departments, issues, control_assessments) plus risk_controls had only
-- one row. This migration:
--
--   platform.departments         8 Saudi org-chart departments
--   platform.issues              12 issues (mix of severities + statuses)
--   platform.control_assessments 18 assessments across the 8 platform controls
--   risk.risk_controls           +12 risk↔control linkages
--
-- Idempotent per row via NOT EXISTS predicates.

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ══════════════════════════════════════════════════════════════════
-- (1) platform.departments — Saudi org chart
-- ══════════════════════════════════════════════════════════════════

DECLARE @org BIGINT = 1;

;WITH new_depts AS (
    SELECT * FROM (VALUES
        ('CEO',          N'Office of the CEO',          N'مكتب الرئيس التنفيذي',          NULL, N'00-CEO-001',     N'C-suite + board secretariat'),
        ('RISK',         N'Enterprise Risk Management', N'إدارة المخاطر المؤسسية',        NULL, N'10-RISK-002',    N'ERM, KRI engine, treatment plans'),
        ('CYBER',        N'Cybersecurity',              N'الأمن السيبراني',               NULL, N'20-CYB-003',     N'NCA ECC/CSCC/CCC programmes + SOC'),
        ('PRIVACY',      N'Privacy Office',             N'مكتب الخصوصية',                 NULL, N'25-PRIV-004',    N'DPO + PDPL operations + DSR'),
        ('BCM',          N'Business Continuity',        N'استمرارية الأعمال',             NULL, N'30-BCM-005',     N'BCP / DRP / crisis room'),
        ('PROCUREMENT',  N'Procurement & Supply Chain', N'المشتريات وسلسلة الإمداد',      NULL, N'40-PROC-006',    N'Supplier onboarding, contracts'),
        ('INTERNAL_AUD', N'Internal Audit',             N'المراجعة الداخلية',             NULL, N'50-AUD-007',     N'Audit universe + plan + engagements'),
        ('COMPLIANCE',   N'Compliance',                 N'الالتزام',                       NULL, N'60-COM-008',     N'SAMA / NCA / DGA / SDAIA obligations')
    ) AS d (code, name_en, name_ar, parent_id, cost_centre, description)
)
INSERT INTO platform.departments
    (organisation_id, code, name_en, name_ar, parent_id, cost_centre, description,
     status, created_at, is_deleted)
SELECT @org, d.code, d.name_en, d.name_ar, d.parent_id, d.cost_centre, d.description,
       N'ACTIVE', SYSUTCDATETIME(), 0
  FROM new_depts d
 WHERE NOT EXISTS (SELECT 1 FROM platform.departments x
                    WHERE x.organisation_id = @org AND x.code = d.code);
GO

-- ══════════════════════════════════════════════════════════════════
-- (2) platform.issues — mix of severities and statuses
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_i BIGINT = 1;

;WITH new_issues AS (
    SELECT * FROM (VALUES
        ('ISS-2026-001', N'MFA bypass on legacy admin accounts',           N'تجاوز التحقق متعدد العوامل',          N'CYBER',     'Risk',   1,  'HIGH',     'HIGH',     'IN_PROGRESS'),
        ('ISS-2026-002', N'Stale BCP for retail banking critical service', N'خطة استمرارية قديمة',                 N'BCM',       'BcpPlan',1,  'MEDIUM',   'HIGH',     'OPEN'),
        ('ISS-2026-003', N'Cross-border transfer missing adequacy doc',    N'نقل بيانات بدون قرار كفاية',           N'PRIVACY',   'ProcessingActivity', 2, 'HIGH', 'CRITICAL', 'IN_PROGRESS'),
        ('ISS-2026-004', N'Supplier ISO 27001 cert expired',               N'انتهاء شهادة المورد',                  N'SCM',       'Vendor', 3,  'MEDIUM',   'MEDIUM',   'OPEN'),
        ('ISS-2026-005', N'NCA ECC §2-3 evidence stale',                   N'دليل NCA متقادم',                      N'COMPLIANCE','EvidenceItem', 5, 'LOW', 'MEDIUM','OPEN'),
        ('ISS-2026-006', N'Unpatched CVE-2026-1142 on production app',     N'ثغرة غير مُعالَجة',                    N'CYBER',     'Vulnerability', 8, 'CRITICAL','CRITICAL','IN_PROGRESS'),
        ('ISS-2026-007', N'DSR-2026-0304 past 30-day SLA',                 N'تجاوز مهلة طلب البيانات',              N'PRIVACY',   'DsrCase', 4, 'HIGH',     'HIGH',     'OPEN'),
        ('ISS-2026-008', N'OT segment lacks asset inventory',              N'فقدان جرد أصول OT',                    N'CYBER',     'Asset',   NULL,'MEDIUM',   'MEDIUM',   'OPEN'),
        ('ISS-2026-009', N'DR test result: RTO breach on payroll',         N'فشل في وقت التعافي للرواتب',           N'BCM',       'Exercise',1, 'MEDIUM',  'HIGH',     'CLOSED'),
        ('ISS-2026-010', N'Risk acceptance for legacy app expired',        N'انتهاء قبول الخطر',                    N'RISK',      'Risk',   7,  'MEDIUM',   'MEDIUM',   'CLOSED'),
        ('ISS-2026-011', N'PDPL DPO not notified on new ML training',      N'لم يتم إبلاغ DPO',                     N'PRIVACY',   'Dpia',   2,  'LOW',      'MEDIUM',   'OPEN'),
        ('ISS-2026-012', N'Vendor exit plan missing for critical supplier',N'فقدان خطة خروج مورد حرج',             N'SCM',       'Vendor', 4,  'HIGH',     'MEDIUM',   'OPEN')
    ) AS i (code, title_en, title_ar, source_module, source_entity_type, source_entity_id, severity, priority, status)
)
INSERT INTO platform.issues
    (organisation_id, issue_code, title_en, title_ar, description,
     source_module, source_entity_type, source_entity_id,
     severity, priority, status, created_at, is_deleted)
SELECT @org_i, i.code, i.title_en, i.title_ar,
       CONCAT(N'Auto-seeded issue for ', i.source_module, N' module — see source entity for context.'),
       i.source_module, i.source_entity_type, i.source_entity_id,
       i.severity, i.priority, i.status,
       SYSUTCDATETIME(), 0
  FROM new_issues i
 WHERE NOT EXISTS (SELECT 1 FROM platform.issues x
                    WHERE x.organisation_id = @org_i AND x.issue_code = i.code);
GO

-- ══════════════════════════════════════════════════════════════════
-- (3) platform.control_assessments — per-control assessments
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_ca BIGINT = 1;

IF EXISTS (SELECT 1 FROM platform.controls WHERE organisation_id = @org_ca)
   AND NOT EXISTS (SELECT 1 FROM platform.control_assessments WHERE organisation_id = @org_ca)
BEGIN
    -- 2 assessments per control = (8 controls × 2) = 16 rows.
    -- First one is the prior cycle (older), second is current.
    -- effectiveness CHECK: NOT_TESTED | INEFFECTIVE | NEEDS_IMPROVEMENT | EFFECTIVE
    -- workflow_status CHECK: DRAFT | UNDER_REVIEW | APPROVED | REJECTED
    ;WITH ctrls AS (
        SELECT TOP 8 id FROM platform.controls
         WHERE organisation_id = @org_ca AND is_deleted = 0
         ORDER BY id ASC
    ),
    cycles (offset_days, effectiveness, status_pool) AS (
        SELECT  365, N'NEEDS_IMPROVEMENT', N'APPROVED'
        UNION ALL SELECT 90,  N'EFFECTIVE',         N'APPROVED'
    )
    INSERT INTO platform.control_assessments
        (organisation_id, control_id, effectiveness, assessor_name,
         assessment_date, notes_en, notes_ar, workflow_status,
         is_deleted, created_at)
    SELECT @org_ca, c.id, cy.effectiveness,
           CASE (c.id % 4)
             WHEN 0 THEN N'Khalid Al-Mutairi'
             WHEN 1 THEN N'Layla Al-Rashid'
             WHEN 2 THEN N'Saad Al-Subaie'
             ELSE        N'Reema Al-Qahtani'
           END,
           DATEADD(DAY, -cy.offset_days, CAST(SYSUTCDATETIME() AS DATE)),
           CASE WHEN cy.effectiveness = N'EFFECTIVE'
                THEN N'Sample of 25 transactions passed; no exceptions. Control operating as designed.'
                ELSE N'Sample showed 3 exceptions out of 25; documentation gap identified; remediation in progress.'
           END,
           N'تم إجراء اختبار الضابط؛ النتائج مسجلة في سجل المراجعة.',
           cy.status_pool, 0, DATEADD(DAY, -cy.offset_days, SYSUTCDATETIME())
      FROM ctrls c CROSS JOIN cycles cy;
END
GO

-- ══════════════════════════════════════════════════════════════════
-- (4) risk.risk_controls — link risks to controls
-- ══════════════════════════════════════════════════════════════════

DECLARE @org_rc BIGINT = 1;

IF (SELECT COUNT(*) FROM risk.risk_controls WHERE organisation_id = @org_rc AND is_deleted = 0) < 10
BEGIN
    -- Pick top 6 risks × top 4 controls (across compliance + platform) and
    -- create reasonable linkages. The control_id is a soft FK so we don't
    -- need to assert FK validity here.
    ;WITH r AS (
        SELECT TOP 6 id, ROW_NUMBER() OVER (ORDER BY id) AS rn
          FROM risk.risks
         WHERE organisation_id = @org_rc AND is_deleted = 0
         ORDER BY id ASC
    ),
    c AS (
        SELECT TOP 4 id, ROW_NUMBER() OVER (ORDER BY id) AS cn
          FROM compliance.controls
         WHERE organisation_id = @org_rc AND is_deleted = 0
         ORDER BY id ASC
    )
    INSERT INTO risk.risk_controls
        (organisation_id, risk_id, control_id, effectiveness, contribution_pct,
         notes, is_deleted, created_at)
    SELECT @org_rc, r.id, c.id,
           CASE ((r.rn + c.cn) % 4)
             WHEN 0 THEN N'EFFECTIVE'
             WHEN 1 THEN N'NEEDS_IMPROVEMENT'
             WHEN 2 THEN N'EFFECTIVE'
             ELSE        N'NOT_TESTED'
           END,
           CAST(15 + ((r.rn * c.cn) % 5) * 10 AS DECIMAL(5,2)),  -- 15..55%
           CONCAT(N'Control mitigates risk #', r.id, N' via mapping in unified control library.'),
           0, SYSUTCDATETIME()
      FROM r CROSS JOIN c
     WHERE NOT EXISTS (SELECT 1 FROM risk.risk_controls x
                        WHERE x.risk_id = r.id AND x.control_id = c.id);
END
GO

PRINT 'V119 round-3 demo fill applied: departments + issues + control_assessments + risk_controls.';
GO
