-- V366 — F2-153, the MIRROR direction: five guarantees a FRESH INSTALL enforces and the DEPLOYED
-- databases do not.
--
-- WHY THIS IS THE INTERESTING DIRECTION. F2-153 and V364/V365 closed dev-minus-build: objects dev has
-- and a fresh install lacked. Running the diff the other way round — build-minus-dev — turns up five
-- objects the migration set creates and **neither dev nor prod has**:
--
--     bcm.bcp_plans            UX_bcp_plans_org_code        (organisation_id, plan_code)
--     bcm.bia_assessments      UX_bia_assessments_org_code  (organisation_id, bia_code)
--     bcm.compliance_mappings  UX_bcm_compl_org_req         (organisation_id, source_framework,
--                                                            requirement_code)
--     platform.departments     UX_departments_org_code      (organisation_id, code)
--     bcm.bia_assessments      CK_bia_rto_le_mtpd           rto_hours <= mtpd_hours
--
-- So **the running platform allows two departments with the same code in one organisation**, two BCP
-- plans with the same plan code, two BIA assessments with the same BIA code, a duplicated compliance
-- requirement mapping, and a BIA whose recovery time objective exceeds the maximum tolerable period of
-- disruption — while a customer provisioned from the migration folder is protected from all five.
--
-- ⚠ THIS IS THE SAME ROOT CAUSE READ BACKWARDS. F2-153: the Python bootstrap creates objects the
-- migration set omits. Here the migration set creates objects the bootstrap omits (V103 for
-- `CK_bia_rto_le_mtpd`, the four `UX_*` likewise). Two authorities build the schema, and each has gaps
-- the other covers. **The direction that reaches production is this one**, because prod descends from
-- a dev dump — so a gap in the bootstrap is a gap in the live system, while a gap in the migrations
-- only bites a fresh install.
--
-- ⚠ AND IT SETTLES SOMETHING I HAD FILED AS A PRODUCT QUESTION. F2-153 recorded
-- `ck_bia_rto_le_mtpd` as "either dev's data violates RTO <= MTPD or the rule is wrong". Measured:
-- **dev 7 rows, prod 7 rows, 0 violations, 0 NULLs**, and no migration ever drops the constraint. So
-- the rule is right, the data satisfies it, and the constraint is simply absent from the bootstrap.
-- Not a product question — a convergence gap with a direction I had not measured.
--
-- ⚠ MEASURED BEFORE WRITING THIS FILE (2026-08-07). The audit that matters is against the databases
-- that will actually gain the objects, so it was run on both:
--
--     dev   bcp_plans 8 · bia_assessments 7 · compliance_mappings 412 · departments 34
--     prod  bcp_plans 8 · bia_assessments 7 · compliance_mappings 412 · departments 34
--     both  0 duplicate groups on all four keys · 0 RTO > MTPD violations
--
-- Every statement is guarded on the object's absence, so this is a no-op on any fresh install (V103
-- and its siblings already made all five) and adds exactly five objects to dev and prod.
--
-- FILTERED, DELIBERATELY. All four indexes carry `WHERE is_deleted = 0`, copied from the built
-- database rather than invented: this platform soft-deletes, and an unfiltered unique index would
-- refuse to re-use the code of a deleted department. That is why they are `CREATE UNIQUE INDEX` and
-- not `ADD CONSTRAINT` — a UNIQUE constraint cannot carry a filter.
--
-- REVERSE RECIPE: DROP INDEX UX_bcp_plans_org_code ON bcm.bcp_plans;  (and the other three)
--                 ALTER TABLE bcm.bia_assessments DROP CONSTRAINT CK_bia_rto_le_mtpd;

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ── 1. Uniqueness the deployed databases never had ────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('bcm.bcp_plans') AND name = 'UX_bcp_plans_org_code')
    CREATE UNIQUE INDEX UX_bcp_plans_org_code
        ON bcm.bcp_plans ([organisation_id], [plan_code])
     WHERE [is_deleted] = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('bcm.bia_assessments')
                  AND name = 'UX_bia_assessments_org_code')
    CREATE UNIQUE INDEX UX_bia_assessments_org_code
        ON bcm.bia_assessments ([organisation_id], [bia_code])
     WHERE [is_deleted] = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('bcm.compliance_mappings')
                  AND name = 'UX_bcm_compl_org_req')
    CREATE UNIQUE INDEX UX_bcm_compl_org_req
        ON bcm.compliance_mappings ([organisation_id], [source_framework], [requirement_code])
     WHERE [is_deleted] = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
                WHERE object_id = OBJECT_ID('platform.departments')
                  AND name = 'UX_departments_org_code')
    CREATE UNIQUE INDEX UX_departments_org_code
        ON platform.departments ([organisation_id], [code])
     WHERE [is_deleted] = 0;
GO

-- ── 2. The recovery-objective rule ────────────────────────────────────────────────────
-- Definition copied from the built database, NULL-tolerant exactly as V103 wrote it: an assessment
-- that has not yet set either figure is not in violation, it is incomplete.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints cc
                 JOIN sys.tables t ON t.object_id = cc.parent_object_id
                 JOIN sys.schemas s ON s.schema_id = t.schema_id
                WHERE s.name = 'bcm' AND t.name = 'bia_assessments'
                  AND cc.name = 'CK_bia_rto_le_mtpd')
    ALTER TABLE bcm.bia_assessments ADD CONSTRAINT CK_bia_rto_le_mtpd
        CHECK ([rto_hours] IS NULL OR [mtpd_hours] IS NULL OR [rto_hours] <= [mtpd_hours]);
GO

-- ── Self-checks ───────────────────────────────────────────────────────────────────────
-- The property, not the statement. THROW takes a literal or a variable, never a concatenation.
DECLARE @ix INT;
DECLARE @ixMsg NVARCHAR(400) =
    N'V366 failed: fewer than 4 of the named unique indexes exist. The platform can still hold two '
  + N'departments with the same code in one organisation.';
SELECT @ix = COUNT(*) FROM sys.indexes
 WHERE is_unique = 1 AND name IN ('UX_bcp_plans_org_code', 'UX_bia_assessments_org_code',
                                  'UX_bcm_compl_org_req', 'UX_departments_org_code');
IF @ix < 4 THROW 50000, @ixMsg, 1;
GO

-- Each must still be FILTERED. An unfiltered unique index here would refuse to re-use the code of a
-- soft-deleted row, which turns a correctness fix into a data-entry dead end — so losing the filter
-- is a regression, not a tightening.
DECLARE @unfiltered INT;
DECLARE @unfilteredMsg NVARCHAR(400) =
    N'V366 failed: one of its unique indexes is not filtered. Without WHERE is_deleted = 0 the '
  + N'platform cannot re-use the code of a soft-deleted department, plan or assessment.';
SELECT @unfiltered = COUNT(*) FROM sys.indexes
 WHERE name IN ('UX_bcp_plans_org_code', 'UX_bia_assessments_org_code',
                'UX_bcm_compl_org_req', 'UX_departments_org_code')
   AND has_filter = 0;
IF @unfiltered > 0 THROW 50000, @unfilteredMsg, 1;
GO

DECLARE @ckMsg NVARCHAR(400) =
    N'V366 failed: CK_bia_rto_le_mtpd is missing, disabled or not trusted. A BIA can still declare a '
  + N'recovery time objective beyond its maximum tolerable period of disruption.';
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                WHERE name = 'CK_bia_rto_le_mtpd' AND is_disabled = 0 AND is_not_trusted = 0)
    THROW 50000, @ckMsg, 1;
GO
