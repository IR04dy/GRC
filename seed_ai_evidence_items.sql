-- Seed a realistic evidence-coverage mix on a few AI-mapped sub-controls so the
-- model "Evidence readiness" view shows COVERED / PARTIAL / EXPIRED, not all GAP.
-- Evidence is collected per control (shared across models that map it). Dev only.
-- Idempotent on (organisation_id, sub_control_id, title_en).
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET NOCOUNT ON;

DECLARE @today DATE = CAST(SYSUTCDATETIME() AS DATE);

-- (sub_control_id, title_en, title_ar, status, valid_to)
DECLARE @seed TABLE (scid BIGINT, ten NVARCHAR(300), tar NVARCHAR(300), st NVARCHAR(30), vto DATE);
INSERT INTO @seed VALUES
  (632, N'AI Impact Assessment — Personal Financing model',  N'تقييم أثر الذكاء الاصطناعي — نموذج التمويل الشخصي', N'APPROVED',       DATEADD(MONTH, 9,  @today)),  -- COVERED
  (637, N'Model Card — credit decision XGBoost v2.3',        N'بطاقة النموذج — قرار الائتمان XGBoost 2.3',        N'APPROVED',       DATEADD(MONTH, 9,  @today)),  -- COVERED
  (640, N'Fairness test report — demographic parity',        N'تقرير اختبار العدالة — التكافؤ الديموغرافي',       N'APPROVED',       DATEADD(MONTH, 4,  @today)),  -- COVERED
  (104, N'Secure SDLC checklist — model deployment',         N'قائمة دورة التطوير الآمن — نشر النموذج',           N'PENDING_REVIEW', DATEADD(MONTH, 6,  @today)),  -- PARTIAL
  (633, N'PDPL DPIA — credit model (last cycle)',            N'تقييم أثر حماية البيانات — نموذج الائتمان',        N'APPROVED',       DATEADD(MONTH, -2, @today));  -- EXPIRED (valid_to in past)

INSERT INTO evidence.evidence_items
    (external_id, organisation_id, sub_control_id, title_en, title_ar,
     evidence_type, status, valid_from, valid_to, current_version_no, is_deleted, created_at, created_by)
SELECT NEWID(), 1, s.scid, s.ten, s.tar,
       N'DOCUMENT', s.st, DATEADD(MONTH, -3, @today), s.vto, 1, 0, SYSUTCDATETIME(), 1
FROM @seed s
WHERE NOT EXISTS (
    SELECT 1 FROM evidence.evidence_items e
    WHERE e.organisation_id = 1 AND e.sub_control_id = s.scid AND e.title_en = s.ten AND e.is_deleted = 0);

SELECT sub_control_id, status, valid_to, title_en
FROM evidence.evidence_items
WHERE organisation_id = 1 AND sub_control_id IN (632,637,640,104,633) AND is_deleted = 0
ORDER BY sub_control_id;
