-- Map DPIA / Breach / Audit-finding registers to relevant PDPL + ECC controls
-- and seed a coverage mix, so these registers show populated Evidence readiness
-- (auto-rendered by the shared ControlMappingPicker). Dev demo only. Idempotent.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

DECLARE @today DATE = CAST(SYSUTCDATETIME() AS DATE);

-- 1) DPIA -> PDPL Art.1/12 controls.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT d.organisation_id, N'DPIA', d.id, x.scid, 1, SYSUTCDATETIME(), x.sat
FROM privacy.dpia d
CROSS JOIN (VALUES
    (467,'FULL'),    -- PDPL 1-1  Identify & document a lawful basis
    (513,'PARTIAL'), -- PDPL 12-1 Due-diligence assessment of processors
    (514,'PARTIAL'), -- PDPL 12-2 Execute a PDPL-compliant DPA
    (516,'PLANNED')  -- PDPL 12-4 Monitor processor compliance
) AS x(scid, sat)
WHERE d.organisation_id = 1 AND d.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=N'DPIA' AND cl.entity_id=d.id AND cl.sub_control_id=x.scid);

-- 2) Privacy breach -> PDPL lawful-basis + DPO + monitoring.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT b.organisation_id, N'BREACH', b.id, x.scid, 1, SYSUTCDATETIME(), x.sat
FROM privacy.data_breaches b
CROSS JOIN (VALUES
    (467,'FULL'),    -- PDPL 1-1  Lawful basis
    (509,'PARTIAL'), -- PDPL 11-1 Designate a qualified DPO
    (516,'PLANNED')  -- PDPL 12-4 Monitor processor compliance
) AS x(scid, sat)
WHERE b.organisation_id = 1 AND b.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=N'BREACH' AND cl.entity_id=b.id AND cl.sub_control_id=x.scid);

-- 3) Audit findings -> NCA ECC governance/risk controls.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT f.organisation_id, N'AUDIT_FINDING', f.id, x.scid, 1, SYSUTCDATETIME(), x.sat
FROM compliance.findings f
CROSS JOIN (VALUES
    (80 ,'FULL'),    -- ECC 1-7-2  Compliance Assessment Against Regulatory Requirements
    (74 ,'PARTIAL'), -- ECC 1-5-3  Risk Treatment & Acceptance Process
    (72 ,'PLANNED')  -- ECC 1-5-1  Cybersecurity Risk Management Framework
) AS x(scid, sat)
WHERE f.organisation_id = 1 AND f.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=N'AUDIT_FINDING' AND cl.entity_id=f.id AND cl.sub_control_id=x.scid);

-- 4) Evidence for a coverage mix on the newly-mapped controls.
DECLARE @seed TABLE (scid BIGINT, ten NVARCHAR(300), tar NVARCHAR(300), st NVARCHAR(30), vto DATE);
INSERT INTO @seed VALUES
  (467, N'Lawful basis register (PDPL Art.5)',  N'سجل الأساس النظامي للمعالجة',        N'APPROVED',       DATEADD(MONTH, 9, @today)),
  (513, N'Processor due-diligence assessment',  N'تقييم العناية الواجبة للمعالجين',     N'PENDING_REVIEW', DATEADD(MONTH, 6, @today)),
  (80 , N'Regulatory compliance assessment',    N'تقييم الامتثال للمتطلبات التنظيمية',  N'APPROVED',       DATEADD(MONTH, 8, @today));

INSERT INTO evidence.evidence_items
    (external_id, organisation_id, sub_control_id, title_en, title_ar,
     evidence_type, status, valid_from, valid_to, current_version_no, is_deleted, created_at, created_by)
SELECT NEWID(), 1, s.scid, s.ten, s.tar, N'DOCUMENT', s.st, DATEADD(MONTH,-3,@today), s.vto, 1, 0, SYSUTCDATETIME(), 1
FROM @seed s
WHERE NOT EXISTS (SELECT 1 FROM evidence.evidence_items e
                  WHERE e.organisation_id=1 AND e.sub_control_id=s.scid AND e.title_en=s.ten AND e.is_deleted=0);

SELECT entity_type, COUNT(DISTINCT entity_id) AS entities, COUNT(*) AS links
FROM evidence.control_links
WHERE entity_type IN (N'DPIA',N'BREACH',N'AUDIT_FINDING')
GROUP BY entity_type ORDER BY entity_type;
