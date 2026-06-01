-- Map cyber assets + outsourcing arrangements to relevant NCA controls and seed
-- a coverage mix, so the reusable Evidence readiness panel is demonstrable on
-- those registers too. Dev demo only. Idempotent.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

DECLARE @today DATE = CAST(SYSUTCDATETIME() AS DATE);

-- 1) Cyber assets -> ECC asset/data/crypto controls.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT a.organisation_id, N'ASM_ASSET', a.id, x.scid, 1, SYSUTCDATETIME(), x.sat
FROM cyber.attack_surface_asset a
CROSS JOIN (VALUES
    (94 ,'FULL'),    -- ECC 2-1-2  Asset Classification & Labelling
    (116,'PARTIAL'), -- ECC 2-7-1  Data Classification & Handling Procedures
    (122,'PLANNED')  -- ECC 2-8-3  Encryption of Data at Rest
) AS x(scid, sat)
WHERE a.organisation_id = 1 AND a.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=N'ASM_ASSET' AND cl.entity_id=a.id AND cl.sub_control_id=x.scid);

-- 2) Outsourcing arrangements -> third-party control set (reuses the vendor
--    third-party evidence already seeded for 151/152/153).
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT o.organisation_id, N'OUTSOURCING', o.id, x.scid, 1, SYSUTCDATETIME(), x.sat
FROM tprm.outsourcing_arrangement o
CROSS JOIN (VALUES
    (151,'FULL'),    -- ECC 4-1-1  Third-Party Cybersecurity Risk Assessment
    (152,'FULL'),    -- ECC 4-1-2  Cybersecurity Requirements in Third-Party Contracts
    (153,'PARTIAL'), -- ECC 4-1-3  Ongoing Third-Party Cybersecurity Monitoring
    (266,'PARTIAL'), -- DCC 5-1-5  Third-Party Data Recipient Due Diligence
    (275,'PLANNED')  -- DCC 7-1-5  End-of-Contract Data Return & Destruction
) AS x(scid, sat)
WHERE o.organisation_id = 1
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=N'OUTSOURCING' AND cl.entity_id=o.id AND cl.sub_control_id=x.scid);

-- 3) Evidence on the asset controls for a coverage mix (94 COVERED, 116 PARTIAL).
DECLARE @seed TABLE (scid BIGINT, ten NVARCHAR(300), tar NVARCHAR(300), st NVARCHAR(30), vto DATE);
INSERT INTO @seed VALUES
  (94 , N'Asset classification register', N'سجل تصنيف الأصول',          N'APPROVED',       DATEADD(MONTH, 9, @today)),
  (116, N'Data handling procedure (assets)', N'إجراء معالجة البيانات للأصول', N'PENDING_REVIEW', DATEADD(MONTH, 6, @today));

INSERT INTO evidence.evidence_items
    (external_id, organisation_id, sub_control_id, title_en, title_ar,
     evidence_type, status, valid_from, valid_to, current_version_no, is_deleted, created_at, created_by)
SELECT NEWID(), 1, s.scid, s.ten, s.tar, N'DOCUMENT', s.st, DATEADD(MONTH,-3,@today), s.vto, 1, 0, SYSUTCDATETIME(), 1
FROM @seed s
WHERE NOT EXISTS (SELECT 1 FROM evidence.evidence_items e
                  WHERE e.organisation_id=1 AND e.sub_control_id=s.scid AND e.title_en=s.ten AND e.is_deleted=0);

SELECT (SELECT COUNT(*) FROM evidence.control_links WHERE entity_type=N'ASM_ASSET') AS asset_links,
       (SELECT COUNT(*) FROM evidence.control_links WHERE entity_type=N'OUTSOURCING') AS outsourcing_links;
