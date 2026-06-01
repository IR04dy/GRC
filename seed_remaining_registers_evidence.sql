-- Populate the remaining control-mapped registers with domain-appropriate
-- control links + a coverage mix, so their universal "Evidence readiness"
-- section (shared ControlMappingPicker) shows populated coverage. Dev demo only.
-- Idempotent. (AUDIT_ENGAGEMENT = mock data, COMPLAINT/SANCTIONS = no clean
-- table/framework — intentionally skipped.)
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;
DECLARE @today DATE = CAST(SYSUTCDATETIME() AS DATE);

-- Generic seeder via a (entity_type, table) driver is not expressible in pure
-- T-SQL without dynamic SQL, so each register is an explicit guarded insert.

DECLARE @links TABLE (et NVARCHAR(40), eid BIGINT, scid BIGINT, sat NVARCHAR(10));

-- ---- Privacy (PDPL) ----
INSERT INTO @links SELECT N'DSR', id, v.scid, v.sat FROM privacy.data_subject_requests, (VALUES (467,'FULL'),(468,'PARTIAL'),(513,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'CONSENT', id, v.scid, v.sat FROM privacy.consents, (VALUES (467,'FULL'),(468,'PARTIAL')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'ROPA', id, v.scid, v.sat FROM privacy.processing_activities, (VALUES (467,'FULL'),(513,'PARTIAL'),(515,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'SUBPROCESSOR', id, v.scid, v.sat FROM privacy.subprocessor, (VALUES (513,'FULL'),(514,'PARTIAL'),(515,'PARTIAL'),(516,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
-- ---- Ethics / Conduct (ECC governance) ----
INSERT INTO @links SELECT N'FRAUD_CASE', id, v.scid, v.sat FROM fraud.fraud_case, (VALUES (80,'FULL'),(74,'PARTIAL'),(137,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'WHISTLEBLOWER_CASE', id, v.scid, v.sat FROM ethics.whistleblower_case, (VALUES (65,'FULL'),(80,'PARTIAL')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'COI_CAMPAIGN', id, v.scid, v.sat FROM ethics.coi_declaration_campaign, (VALUES (65,'FULL'),(80,'PARTIAL')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
-- ---- Counterparty / Open banking ----
INSERT INTO @links SELECT N'COUNTERPARTY', id, v.scid, v.sat FROM cpty.counterparty, (VALUES (48,'FULL'),(49,'PARTIAL'),(614,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'OB_TPP', id, v.scid, v.sat FROM openbanking.ob_tpp, (VALUES (151,'FULL'),(152,'PARTIAL'),(153,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
-- ---- BCM / Resilience ----
INSERT INTO @links SELECT N'CRISIS_AAR', id, v.scid, v.sat FROM bcm.crisis_aar, (VALUES (38,'FULL'),(33,'PARTIAL'),(39,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'EXERCISE_SCENARIO', id, v.scid, v.sat FROM bcm.exercises, (VALUES (38,'FULL'),(37,'PARTIAL'),(34,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'VENDOR_BCP_CAMPAIGN', id, v.scid, v.sat FROM tprm.vendor_bcp_campaign, (VALUES (153,'FULL'),(37,'PARTIAL'),(43,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'OPRES_ASSESSMENT', id, v.scid, v.sat FROM opres.opres_assessment, (VALUES (53,'FULL'),(54,'PARTIAL'),(37,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'RRP_CYCLE', id, v.scid, v.sat FROM rrp.rrp_cycle, (VALUES (37,'FULL'),(40,'PARTIAL'),(53,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
-- ---- Regulatory / Audit ----
INSERT INTO @links SELECT N'REG_FILING', id, v.scid, v.sat FROM regreporting.regreport_filing, (VALUES (80,'FULL'),(65,'PARTIAL')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'IA_ENGAGEMENT', id, v.scid, v.sat FROM audit_lc.ia_engagement, (VALUES (80,'FULL'),(74,'PARTIAL'),(65,'PLANNED')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;
INSERT INTO @links SELECT N'CCM_TEST', id, v.scid, v.sat FROM ccm.control_test_definition, (VALUES (80,'FULL'),(74,'PARTIAL')) v(scid,sat) WHERE organisation_id=1 AND is_deleted=0;

INSERT INTO evidence.control_links (organisation_id, entity_type, entity_id, sub_control_id, created_by, created_at, satisfaction)
SELECT 1, l.et, l.eid, l.scid, 1, SYSUTCDATETIME(), l.sat
FROM @links l
WHERE NOT EXISTS (SELECT 1 FROM evidence.control_links cl
                  WHERE cl.entity_type=l.et AND cl.entity_id=l.eid AND cl.sub_control_id=l.scid);

-- Evidence for coverage mixes on controls used above that lacked evidence.
DECLARE @seed TABLE (scid BIGINT, ten NVARCHAR(300), tar NVARCHAR(300), st NVARCHAR(30), vto DATE);
INSERT INTO @seed VALUES
  (468, N'Consent capture & withdrawal log',   N'سجل أخذ الموافقة وسحبها',          N'APPROVED',       DATEADD(MONTH, 8, @today)),
  (65 , N'Cybersecurity policy framework',      N'إطار سياسات الأمن السيبراني',       N'APPROVED',       DATEADD(MONTH,10, @today)),
  (37 , N'Business continuity plan (current)',  N'خطة استمرارية الأعمال (الحالية)',   N'APPROVED',       DATEADD(MONTH, 9, @today)),
  (74 , N'Risk treatment & acceptance log',     N'سجل معالجة وقبول المخاطر',          N'PENDING_REVIEW', DATEADD(MONTH, 6, @today)),
  (53 , N'Operational loss-event database',     N'قاعدة بيانات خسائر التشغيل',        N'PENDING_REVIEW', DATEADD(MONTH, 6, @today));

INSERT INTO evidence.evidence_items
    (external_id, organisation_id, sub_control_id, title_en, title_ar,
     evidence_type, status, valid_from, valid_to, current_version_no, is_deleted, created_at, created_by)
SELECT NEWID(), 1, s.scid, s.ten, s.tar, N'DOCUMENT', s.st, DATEADD(MONTH,-3,@today), s.vto, 1, 0, SYSUTCDATETIME(), 1
FROM @seed s
WHERE NOT EXISTS (SELECT 1 FROM evidence.evidence_items e
                  WHERE e.organisation_id=1 AND e.sub_control_id=s.scid AND e.title_en=s.ten AND e.is_deleted=0);

SELECT entity_type, COUNT(DISTINCT entity_id) AS entities, COUNT(*) AS links
FROM evidence.control_links
WHERE entity_type IN (N'DSR',N'CONSENT',N'ROPA',N'SUBPROCESSOR',N'FRAUD_CASE',N'WHISTLEBLOWER_CASE',
  N'COI_CAMPAIGN',N'COUNTERPARTY',N'OB_TPP',N'CRISIS_AAR',N'EXERCISE_SCENARIO',N'VENDOR_BCP_CAMPAIGN',
  N'OPRES_ASSESSMENT',N'RRP_CYCLE',N'REG_FILING',N'IA_ENGAGEMENT',N'CCM_TEST')
GROUP BY entity_type ORDER BY entity_type;
