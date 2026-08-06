-- V302 (CH-6 / PM-C1, Phase A): canonical policy-document -> framework mapping.
--
-- The legacy compliance.policy_framework_mappings (frameworks <-> compliance.policies)
-- is the ONLY policy feature with no canonical home: it is what feeds framework
-- coverage + the board pack, yet it keys off the legacy register. V232 (C-4)
-- already bridged every legacy policy into policy.documents by external_id, so the
-- many-to-many can now live canonically. This table mirrors the legacy shape and
-- adds organisation_id (carried directly, so coverage no longer needs the CH-15
-- parent-policy subquery). Phase B repoints FrameworkAnchorService + BoardPackService
-- to read here; the legacy table then becomes archive-only.
--
-- Idempotent: CREATE guarded by OBJECT_ID; data migrated with a NOT EXISTS key on
-- (document_id, framework_id, clause_ref). Reversible:
--   DELETE FROM policy.document_framework_mappings WHERE created_by = 'C6-MIGRATION';

IF OBJECT_ID('policy.document_framework_mappings', 'U') IS NULL
BEGIN
    CREATE TABLE policy.document_framework_mappings (
        id              BIGINT IDENTITY(1,1) CONSTRAINT pk_policy_doc_fw_map PRIMARY KEY,
        organisation_id BIGINT         NOT NULL,
        document_id     BIGINT         NOT NULL
            CONSTRAINT fk_policy_doc_fw_map_document REFERENCES policy.documents (id),
        framework_id    BIGINT         NOT NULL,
        clause_ref      NVARCHAR(100)  NULL,
        satisfaction    NVARCHAR(20)   NOT NULL CONSTRAINT df_policy_doc_fw_map_sat DEFAULT ('PARTIAL')
            CONSTRAINT ck_policy_doc_fw_map_sat CHECK (satisfaction IN ('FULL','PARTIAL','OUT_OF_SCOPE','NOT_APPLICABLE')),
        notes           NVARCHAR(MAX)  NULL,
        is_deleted      BIT            NOT NULL CONSTRAINT df_policy_doc_fw_map_del DEFAULT (0),
        created_at      DATETIME2      NOT NULL CONSTRAINT df_policy_doc_fw_map_cat DEFAULT (SYSUTCDATETIME()),
        created_by      NVARCHAR(100)  NULL
    );
END
GO

-- Look-ups: coverage/board-pack scan by (org, framework); detail by document.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_policy_doc_fw_map_org_fw')
    CREATE INDEX ix_policy_doc_fw_map_org_fw
        ON policy.document_framework_mappings (organisation_id, framework_id) WHERE is_deleted = 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_policy_doc_fw_map_document')
    CREATE INDEX ix_policy_doc_fw_map_document
        ON policy.document_framework_mappings (document_id) WHERE is_deleted = 0;
GO
-- One live mapping per (document, framework, clause).
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_policy_doc_fw_map_doc_fw_clause')
    CREATE UNIQUE INDEX ux_policy_doc_fw_map_doc_fw_clause
        ON policy.document_framework_mappings (document_id, framework_id, clause_ref) WHERE is_deleted = 0;
GO-- ⚠ F2-77 (2026-08-06): batch guarded on a spine-created table.
-- compliance.policies, compliance.policy_framework_mappings is created by V341__spine_bootstrap_schema_tables.sql, which
-- runs LATER than this file, so on a first run in version order it does not exist and this
-- batch failed with "Invalid object name". A no-op is correct: on a fresh installation
-- there is no legacy data to read. Guarded rather than reordered, because moving V341
-- earlier would rewrite the schema history of every environment that already ran it.
IF OBJECT_ID(N'compliance.policies', N'U') IS NOT NULL AND OBJECT_ID(N'compliance.policy_framework_mappings', N'U') IS NOT NULL
BEGIN


-- Migrate the legacy mappings, resolving legacy policy_id -> canonical document_id
-- through the external_id bridge (V232). organisation_id comes from the parent
-- legacy policy. Only satisfaction values in the canonical enum are carried.
INSERT INTO policy.document_framework_mappings
    (organisation_id, document_id, framework_id, clause_ref, satisfaction, notes, is_deleted, created_by)
SELECT
    p.organisation_id,
    d.id,
    m.framework_id,
    LEFT(m.clause_ref, 100),
    CASE WHEN m.satisfaction IN ('FULL','PARTIAL','OUT_OF_SCOPE','NOT_APPLICABLE') THEN m.satisfaction ELSE 'PARTIAL' END,
    m.notes,
    0,
    'C6-MIGRATION'
-- COLLATE DATABASE_DEFAULT on every cross-table NVARCHAR comparison: legacy
-- columns are SQL_Latin1_General_CP1_CI_AS while the canonical schema is UTF-8,
-- so an un-collated equality throws a collation-conflict (mixed-collation DB).
FROM compliance.policy_framework_mappings m
JOIN compliance.policies p ON p.id = m.policy_id AND p.is_deleted = 0
JOIN policy.documents d
     ON d.external_id COLLATE DATABASE_DEFAULT = CONVERT(NVARCHAR(64), p.external_id) COLLATE DATABASE_DEFAULT
    AND d.is_deleted = 0
WHERE m.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM policy.document_framework_mappings x
       WHERE x.document_id = d.id
         AND x.framework_id = m.framework_id
         AND ISNULL(x.clause_ref, N'~NULL~') COLLATE DATABASE_DEFAULT
           = ISNULL(LEFT(m.clause_ref, 100), N'~NULL~') COLLATE DATABASE_DEFAULT
  );
END
GO
