-- Map enterprise risks to ERM controls + seed a coverage mix, to demonstrate
-- the now-universal Evidence readiness section (auto-rendered by the shared
-- ControlMappingPicker on any register that maps controls). Dev demo only.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

DECLARE @today DATE = CAST(SYSUTCDATETIME() AS DATE);

-- Control links: top org-1 risks -> ERM control set.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT r.organisation_id, N'RISK', r.id, x.scid, 1, SYSUTCDATETIME(), x.sat
FROM risk.risks r
CROSS JOIN (VALUES
    (48 ,'FULL'),    -- ERM-1-2-1  Risk Register
    (49 ,'PARTIAL'), -- ERM-1-2-2  Risk Assessment Methodology
    (51 ,'PARTIAL'), -- ERM-1-3-1  Risk Treatment Plans
    (52 ,'PLANNED')  -- ERM-1-3-2  Key Risk Indicators Dashboard
) AS x(scid, sat)
WHERE r.organisation_id = 1 AND r.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=N'RISK' AND cl.entity_id=r.id AND cl.sub_control_id=x.scid);

-- Evidence on the ERM controls for a coverage mix (48 COVERED, 49 PARTIAL).
DECLARE @seed TABLE (scid BIGINT, ten NVARCHAR(300), tar NVARCHAR(300), st NVARCHAR(30), vto DATE);
INSERT INTO @seed VALUES
  (48, N'Enterprise risk register (current)', N'سجل المخاطر المؤسسية (الحالي)',   N'APPROVED',       DATEADD(MONTH, 9, @today)),
  (49, N'Risk assessment methodology v2',     N'منهجية تقييم المخاطر - الإصدار 2', N'PENDING_REVIEW', DATEADD(MONTH, 6, @today));

INSERT INTO evidence.evidence_items
    (external_id, organisation_id, sub_control_id, title_en, title_ar,
     evidence_type, status, valid_from, valid_to, current_version_no, is_deleted, created_at, created_by)
SELECT NEWID(), 1, s.scid, s.ten, s.tar, N'DOCUMENT', s.st, DATEADD(MONTH,-3,@today), s.vto, 1, 0, SYSUTCDATETIME(), 1
FROM @seed s
WHERE NOT EXISTS (SELECT 1 FROM evidence.evidence_items e
                  WHERE e.organisation_id=1 AND e.sub_control_id=s.scid AND e.title_en=s.ten AND e.is_deleted=0);

SELECT COUNT(DISTINCT entity_id) AS risks_mapped, COUNT(*) AS total_links
FROM evidence.control_links WHERE entity_type=N'RISK';
