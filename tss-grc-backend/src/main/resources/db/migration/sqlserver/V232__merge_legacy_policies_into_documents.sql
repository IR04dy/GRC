-- =====================================================================
-- V232 — C-4: merge legacy compliance.policies into canonical policy.documents
-- ---------------------------------------------------------------------
-- The platform had two parallel policy registers: the legacy
-- compliance.policies (DRAFT -> LEGAL_REVIEW -> EXEC_APPROVAL -> PUBLISHED
-- -> REVIEW_DUE) and the newer, fuller policy.documents (DRAFT -> REVIEW ->
-- APPROVED -> PUBLISHED -> EXPIRED -> ARCHIVED, with exceptions, attestations,
-- versioning, file upload, evidence-packages and control resolution).
--
-- This migration makes policy.documents the single canonical register by
-- copying every live legacy policy into it, then retires the legacy register
-- at the UI layer (the "Policies" tab is removed). The legacy table itself is
-- DELIBERATELY PRESERVED as a read-only archive (not dropped, not emptied), and
-- so are its framework mappings (compliance.policy_framework_mappings).
--
-- Properties:
--   * Idempotent — keyed on external_id; re-running inserts nothing new.
--   * Collision-safe — the sole exact (org, code, version) clash
--     (POL-AI-001 v1.0) is migrated under version '1.0-LEGACY' so the
--     ux_policy_doc_org_code_version unique index is never violated.
--   * Status-mapped to the canonical lifecycle (ck_policy_status compliant).
--   * Lossless — full bilingual body text (content_en/ar) is preserved in
--     summary_en/ar; author_id/approver_id are resolved to names via iam.users.
--   * Reversible — every migrated row carries created_by = 'C4-MIGRATION' and a
--     notes marker, so it can be removed with:
--         DELETE FROM policy.documents WHERE created_by = 'C4-MIGRATION';
-- =====================================================================

INSERT INTO policy.documents (
    organisation_id, external_id, policy_code, version,
    title_en, title_ar, summary_en, summary_ar, category,
    owner_name, approver_name,
    effective_date, next_review_date, status, published_at,
    related_framework, notes,
    is_deleted, created_at, created_by
)
SELECT
    p.organisation_id,
    CONVERT(NVARCHAR(64), p.external_id),
    LEFT(p.code, 40),
    -- Disambiguate the one exact (org, code, version) collision so the unique
    -- index is never violated; all other legacy versions are unique already.
    CASE WHEN EXISTS (
        SELECT 1 FROM policy.documents d
         WHERE d.organisation_id = p.organisation_id
           AND d.policy_code     = p.code
           AND d.version         = p.version
    ) THEN LEFT(p.version + '-LEGACY', 20) ELSE p.version END,
    p.title_en, p.title_ar,
    p.content_en, p.content_ar,
    LEFT(p.category, 60),
    ua.full_name_en,   -- legacy author_id   -> owner_name
    up.full_name_en,   -- legacy approver_id -> approver_name
    p.effective_from,
    p.next_review_date,
    CASE p.status
        WHEN 'DRAFT'         THEN 'DRAFT'
        WHEN 'LEGAL_REVIEW'  THEN 'REVIEW'
        WHEN 'EXEC_APPROVAL' THEN 'APPROVED'
        WHEN 'PUBLISHED'     THEN 'PUBLISHED'
        WHEN 'REVIEW_DUE'    THEN 'PUBLISHED'
        ELSE 'DRAFT'
    END,
    p.published_at,
    -- Primary mapped framework (full detail stays in the legacy archive table).
    LEFT((SELECT TOP 1 f.name_en
            FROM compliance.policy_framework_mappings m
            JOIN compliance.frameworks f ON f.id = m.framework_id
           WHERE m.policy_id = p.id AND m.is_deleted = 0
           ORDER BY m.id), 120),
    CONCAT('[Migrated from legacy compliance.policies #', p.id,
           '. Framework mappings retained in the legacy archive table.]'),
    0,
    SYSUTCDATETIME(),
    'C4-MIGRATION'
FROM compliance.policies p
LEFT JOIN iam.users ua ON ua.id = p.author_id
LEFT JOIN iam.users up ON up.id = p.approver_id
WHERE p.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM policy.documents d
       WHERE d.external_id = CONVERT(NVARCHAR(64), p.external_id)
  );
