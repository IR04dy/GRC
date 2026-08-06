-- =====================================================================
-- V233 — C-12 (EXPAND phase): real FK links for policy control/framework mappings
-- ---------------------------------------------------------------------
-- Replaces the free-text policy.documents.related_controls / related_framework
-- with a normalised FK model:
--   * policy.document_control_links — many-to-many policy <-> compliance.controls
--   * policy.documents.framework_id — FK to compliance.frameworks
--
-- Expand-contract migration pattern (the professional, low-risk approach):
--   EXPAND (this migration): add the FK structures, backfill from the existing
--   free-text, and make them authoritative for reads. The free-text columns are
--   intentionally KEPT (now deprecated) so that legacy references whose codes do
--   not exist in the current control library are never lost, and the change is
--   fully reversible.
--   CONTRACT (a later migration, once the UI control picker has fully populated
--   the FK links): drop related_controls / related_framework.
--
-- NOTE: many seeded policies use placeholder control-code schemes (e.g. ECC-2-2,
-- AML-CDD-1, Vision2030) that do not correspond to rows in compliance.controls,
-- so the backfill is deliberately partial — only codes that resolve to a real
-- control are linked; the rest remain visible via the retained free-text.
--
-- Idempotent: guarded DDL + NOT EXISTS / IS NULL backfills.
-- =====================================================================

IF COL_LENGTH('policy.documents', 'framework_id') IS NULL
    ALTER TABLE policy.documents ADD framework_id BIGINT NULL;
GO

IF OBJECT_ID('policy.document_control_links') IS NULL
BEGIN
    CREATE TABLE policy.document_control_links (
        id                 BIGINT IDENTITY(1,1) CONSTRAINT pk_pdcl PRIMARY KEY,
        organisation_id    BIGINT   NOT NULL,
        policy_document_id BIGINT   NOT NULL,
        control_id         BIGINT   NOT NULL,
        created_at         DATETIME2 NOT NULL CONSTRAINT df_pdcl_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT fk_pdcl_doc FOREIGN KEY (policy_document_id) REFERENCES policy.documents(id),
        CONSTRAINT fk_pdcl_ctl FOREIGN KEY (control_id)         REFERENCES compliance.controls(id),
        CONSTRAINT ux_pdcl_doc_ctl UNIQUE (policy_document_id, control_id)
    );
    CREATE INDEX ix_pdcl_doc ON policy.document_control_links (policy_document_id);
END
GO-- ⚠ F2-77 (2026-08-06): batch guarded on a spine-created table.
-- compliance.frameworks is created by V341__spine_bootstrap_schema_tables.sql, which
-- runs LATER than this file, so on a first run in version order it does not exist and this
-- batch failed with "Invalid object name". A no-op is correct: on a fresh installation
-- there is no legacy data to read. Guarded rather than reordered, because moving V341
-- earlier would rewrite the schema history of every environment that already ran it.
IF OBJECT_ID(N'compliance.frameworks', N'U') IS NOT NULL
BEGIN


-- Backfill framework_id from the free-text related_framework (exact name match).
UPDATE d
   SET framework_id = f.id
  FROM policy.documents d
  JOIN compliance.frameworks f ON f.name_en = d.related_framework
 WHERE d.framework_id IS NULL
   AND d.related_framework IS NOT NULL;
END
GO

-- Backfill control links from the free-text related_controls. Delimiters in the
-- seed data are inconsistent (',', ';', '|', whitespace) so normalise them all to
-- commas before STRING_SPLIT, then match each token to a real control code.
INSERT INTO policy.document_control_links (organisation_id, policy_document_id, control_id)
SELECT DISTINCT d.organisation_id, d.id, c.id
  FROM policy.documents d
 CROSS APPLY STRING_SPLIT(
        REPLACE(REPLACE(REPLACE(d.related_controls, '|', ','), ';', ','), ' ', ','), ',') s
  JOIN compliance.controls c
    ON c.organisation_id = d.organisation_id
   AND c.code = LTRIM(RTRIM(s.value))
   AND c.is_deleted = 0
 WHERE d.is_deleted = 0
   AND d.related_controls IS NOT NULL
   AND NULLIF(LTRIM(RTRIM(s.value)), '') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1 FROM policy.document_control_links l
         WHERE l.policy_document_id = d.id AND l.control_id = c.id);
GO
