/* ============================================================================================
   V361 — the columns a fresh install was missing (F2-146, closing F2-09's residue)
   ============================================================================================

   WHAT THIS FIXES, AND HOW IT WAS FOUND

   F2-09's schema_convergence.py diffs a Flyway-built database against the Python-bootstrapped dev
   schema. With the chain finally running end to end (F2-77, V1 -> V360), the residue is exact and
   carries no caveat: 3 tables and 27 columns exist in dev and are created by NO migration, and
   NOTHING exists in the build that dev lacks. The gap is one-directional -- one schema built twice,
   and the Flyway copy was never finished.

   These 27 columns are the half that BREAKS. 13 sit on compliance.findings and AuditFinding maps
   them with @Column (capa_stage, owner_test_result, audit_signoff_*, independent_test_*, clause_ref
   ...). With ddl-auto=none Hibernate creates nothing, so on a migration-built install every SELECT
   through that entity named columns that did not exist and failed. 11 more are on
   evidence.evidence_items -- the evidence subsystem's collection lineage.

   ⚠ THE 3 TABLES ARE DELIBERATELY NOT CREATED HERE. compliance.compliance_assessments,
   compliance.compliance_findings and compliance.evidence_items are mapped by no entity, read by no
   Java, and created by no migration -- F2-140's three. Adding DDL for them would institutionalise
   dead weight; they are dev-only debris from before the compliance consolidation. Dropping them
   from dev is a destructive change and stays an owner decision.

   ⚠ EVERY DEFINITION IS COPIED FROM THE LIVE DEV COLUMN -- type, length, nullability and default --
   not invented. Two columns are NOT NULL in dev and both carry a default (capa_stage
   N'IDENTIFIED', current_version_no 1), so they are added NOT NULL WITH that default, which is safe
   on a populated table. A NOT NULL column with no default could not be added to a table that
   already has rows, and silently downgrading it to NULL would let a fresh install disagree with dev
   in a way this very diff exists to catch.

   Idempotent: every ALTER is guarded on sys.columns, and the file ends by re-verifying that all 27
   are present and THROWing if any is not. Applied twice to dev by hand (Flyway is OFF in dev).
   ============================================================================================ */
SET NOCOUNT ON;
GO

-- ── bcm.crisis_events ─────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('bcm.crisis_events') AND name = 'cycle_id')
    ALTER TABLE bcm.crisis_events ADD cycle_id BIGINT NULL;
GO

-- ── compliance.findings ─────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'audit_signoff_at')
    ALTER TABLE compliance.findings ADD audit_signoff_at DATETIME2(7) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'audit_signoff_by_id')
    ALTER TABLE compliance.findings ADD audit_signoff_by_id BIGINT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'audit_signoff_notes')
    ALTER TABLE compliance.findings ADD audit_signoff_notes NVARCHAR(MAX) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'capa_stage')
    ALTER TABLE compliance.findings ADD capa_stage NVARCHAR(40) NOT NULL DEFAULT (N'IDENTIFIED');
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'clause_ref')
    ALTER TABLE compliance.findings ADD clause_ref NVARCHAR(100) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'independent_test_at')
    ALTER TABLE compliance.findings ADD independent_test_at DATETIME2(7) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'independent_test_by_id')
    ALTER TABLE compliance.findings ADD independent_test_by_id BIGINT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'independent_test_notes')
    ALTER TABLE compliance.findings ADD independent_test_notes NVARCHAR(MAX) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'independent_test_result')
    ALTER TABLE compliance.findings ADD independent_test_result NVARCHAR(20) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'owner_test_at')
    ALTER TABLE compliance.findings ADD owner_test_at DATETIME2(7) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'owner_test_evidence_id')
    ALTER TABLE compliance.findings ADD owner_test_evidence_id BIGINT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'owner_test_notes')
    ALTER TABLE compliance.findings ADD owner_test_notes NVARCHAR(MAX) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'owner_test_result')
    ALTER TABLE compliance.findings ADD owner_test_result NVARCHAR(20) NULL;
GO

-- ── cyber.cyber_sub_controls ─────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('cyber.cyber_sub_controls') AND name = 'description_ar')
    ALTER TABLE cyber.cyber_sub_controls ADD description_ar NVARCHAR(MAX) NULL;
GO

-- ── evidence.evidence_items ─────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'collected_at')
    ALTER TABLE evidence.evidence_items ADD collected_at DATETIME2(7) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'collection_method')
    ALTER TABLE evidence.evidence_items ADD collection_method NVARCHAR(50) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'collector_id')
    ALTER TABLE evidence.evidence_items ADD collector_id BIGINT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'collector_name')
    ALTER TABLE evidence.evidence_items ADD collector_name NVARCHAR(255) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'connector_code')
    ALTER TABLE evidence.evidence_items ADD connector_code NVARCHAR(50) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'current_version_no')
    ALTER TABLE evidence.evidence_items ADD current_version_no INT NOT NULL DEFAULT ((1));
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'evidence_hash')
    ALTER TABLE evidence.evidence_items ADD evidence_hash NVARCHAR(128) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'merkle_anchor')
    ALTER TABLE evidence.evidence_items ADD merkle_anchor NVARCHAR(255) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'previous_hash')
    ALTER TABLE evidence.evidence_items ADD previous_hash NVARCHAR(128) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'tool_version')
    ALTER TABLE evidence.evidence_items ADD tool_version NVARCHAR(100) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'witness_id')
    ALTER TABLE evidence.evidence_items ADD witness_id BIGINT NULL;
GO

-- ── evidence.sub_controls ─────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.sub_controls') AND name = 'evidence_guidance_ar')
    ALTER TABLE evidence.sub_controls ADD evidence_guidance_ar NVARCHAR(MAX) NULL;
GO

-- ⚠ Verify by throwing. A guarded ALTER that silently did nothing looks identical to one that
-- worked; only a re-read proves the column is there.
DECLARE @missing NVARCHAR(MAX) = N'';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('bcm.crisis_events') AND name = 'cycle_id')
    SET @missing = @missing + N'bcm.crisis_events.cycle_id ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'audit_signoff_at')
    SET @missing = @missing + N'compliance.findings.audit_signoff_at ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'audit_signoff_by_id')
    SET @missing = @missing + N'compliance.findings.audit_signoff_by_id ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'audit_signoff_notes')
    SET @missing = @missing + N'compliance.findings.audit_signoff_notes ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'capa_stage')
    SET @missing = @missing + N'compliance.findings.capa_stage ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'clause_ref')
    SET @missing = @missing + N'compliance.findings.clause_ref ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'independent_test_at')
    SET @missing = @missing + N'compliance.findings.independent_test_at ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'independent_test_by_id')
    SET @missing = @missing + N'compliance.findings.independent_test_by_id ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'independent_test_notes')
    SET @missing = @missing + N'compliance.findings.independent_test_notes ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'independent_test_result')
    SET @missing = @missing + N'compliance.findings.independent_test_result ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'owner_test_at')
    SET @missing = @missing + N'compliance.findings.owner_test_at ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'owner_test_evidence_id')
    SET @missing = @missing + N'compliance.findings.owner_test_evidence_id ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'owner_test_notes')
    SET @missing = @missing + N'compliance.findings.owner_test_notes ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('compliance.findings') AND name = 'owner_test_result')
    SET @missing = @missing + N'compliance.findings.owner_test_result ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('cyber.cyber_sub_controls') AND name = 'description_ar')
    SET @missing = @missing + N'cyber.cyber_sub_controls.description_ar ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'collected_at')
    SET @missing = @missing + N'evidence.evidence_items.collected_at ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'collection_method')
    SET @missing = @missing + N'evidence.evidence_items.collection_method ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'collector_id')
    SET @missing = @missing + N'evidence.evidence_items.collector_id ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'collector_name')
    SET @missing = @missing + N'evidence.evidence_items.collector_name ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'connector_code')
    SET @missing = @missing + N'evidence.evidence_items.connector_code ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'current_version_no')
    SET @missing = @missing + N'evidence.evidence_items.current_version_no ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'evidence_hash')
    SET @missing = @missing + N'evidence.evidence_items.evidence_hash ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'merkle_anchor')
    SET @missing = @missing + N'evidence.evidence_items.merkle_anchor ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'previous_hash')
    SET @missing = @missing + N'evidence.evidence_items.previous_hash ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'tool_version')
    SET @missing = @missing + N'evidence.evidence_items.tool_version ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.evidence_items') AND name = 'witness_id')
    SET @missing = @missing + N'evidence.evidence_items.witness_id ';
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('evidence.sub_controls') AND name = 'evidence_guidance_ar')
    SET @missing = @missing + N'evidence.sub_controls.evidence_guidance_ar ';
IF LEN(@missing) > 0
    THROW 61361, N'V361: columns still missing after the ALTERs, so a fresh install still disagrees with the code that maps them.', 1;
GO
