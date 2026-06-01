-- Map every TPRM vendor to the core third-party control set (NCA ECC 4-1 +
-- DCC third-party data controls) and seed a realistic evidence-coverage mix,
-- so the new vendor "Evidence readiness" panel is demonstrable. Dev demo only.
-- Idempotent. Evidence is collected per control (shared across vendors).
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

DECLARE @today DATE = CAST(SYSUTCDATETIME() AS DATE);

-- 1) Control links: each org-1 vendor -> third-party sub-controls.
INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT v.organisation_id, N'TPRM_VENDOR', v.id, x.scid, 1, SYSUTCDATETIME(), x.sat
FROM tprm.vendors v
CROSS JOIN (VALUES
    (151,'FULL'),    -- ECC 4-1-1  Third-Party Cybersecurity Risk Assessment
    (152,'FULL'),    -- ECC 4-1-2  Cybersecurity Requirements in Third-Party Contracts
    (153,'PARTIAL'), -- ECC 4-1-3  Ongoing Third-Party Cybersecurity Monitoring
    (266,'PARTIAL'), -- DCC 5-1-5  Third-Party Data Recipient Due Diligence
    (275,'PLANNED')  -- DCC 7-1-5  End-of-Contract Data Return & Destruction (Third Parties)
) AS x(scid, sat)
WHERE v.organisation_id = 1 AND v.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=N'TPRM_VENDOR' AND cl.entity_id=v.id AND cl.sub_control_id=x.scid);

-- 2) Evidence items on the third-party controls (org-wide) for a coverage mix.
DECLARE @seed TABLE (scid BIGINT, ten NVARCHAR(300), tar NVARCHAR(300), st NVARCHAR(30), vto DATE);
INSERT INTO @seed VALUES
  (151, N'Third-party cyber risk assessment — annual', N'تقييم مخاطر الطرف الثالث السيبرانية - سنوي', N'APPROVED',       DATEADD(MONTH, 8, @today)), -- COVERED
  (152, N'Vendor contract cybersecurity clauses',      N'بنود الأمن السيبراني في عقد المورّد',       N'APPROVED',       DATEADD(MONTH,10, @today)), -- COVERED
  (153, N'Continuous vendor monitoring report (Q2)',   N'تقرير المراقبة المستمرة للمورّد - الربع 2',  N'PENDING_REVIEW', DATEADD(MONTH, 6, @today)); -- PARTIAL

INSERT INTO evidence.evidence_items
    (external_id, organisation_id, sub_control_id, title_en, title_ar,
     evidence_type, status, valid_from, valid_to, current_version_no, is_deleted, created_at, created_by)
SELECT NEWID(), 1, s.scid, s.ten, s.tar, N'DOCUMENT', s.st, DATEADD(MONTH,-3,@today), s.vto, 1, 0, SYSUTCDATETIME(), 1
FROM @seed s
WHERE NOT EXISTS (SELECT 1 FROM evidence.evidence_items e
                  WHERE e.organisation_id=1 AND e.sub_control_id=s.scid AND e.title_en=s.ten AND e.is_deleted=0);

SELECT (SELECT COUNT(DISTINCT entity_id) FROM evidence.control_links WHERE entity_type=N'TPRM_VENDOR') AS vendors_mapped,
       (SELECT COUNT(*) FROM evidence.control_links WHERE entity_type=N'TPRM_VENDOR') AS total_links;
