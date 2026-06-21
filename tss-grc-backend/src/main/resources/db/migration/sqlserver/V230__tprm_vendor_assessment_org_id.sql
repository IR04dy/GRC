-- ============================================================================
-- V230 — denormalise organisation_id onto tprm.vendor_assessments (PERF-PG-002)
--
-- GET /api/v1/tprm/assessments (no vendorId) previously had no way to scope
-- assessments by tenant directly, so TprmService loaded EVERY vendor for the
-- org via Pageable.unpaged(), mapped them to a List<Long>, and ran
-- assessmentRepo.findByVendorIdIn(...) — an unbounded vendor-table scan plus a
-- SQL IN(...) list that grows one bind parameter per vendor (SQL Server caps
-- parameters at ~2100). This migration adds organisation_id to the assessment
-- table so the service can use a paged, org-scoped finder directly.
--
-- The column is added NULLable and backfilled from the parent vendor; the
-- application sets it on every insert (see TprmService.createAssessment). It is
-- left NULLable so the migration can never fail on edge data, and Hibernate's
-- ddl-auto=validate does not check column nullability. Fully idempotent.
-- ============================================================================
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE name = 'organisation_id' AND object_id = OBJECT_ID('tprm.vendor_assessments'))
    ALTER TABLE tprm.vendor_assessments ADD organisation_id BIGINT NULL;
GO

-- Backfill from the parent vendor (vendor_id is the existing FK / tenant anchor).
UPDATE va
   SET va.organisation_id = v.organisation_id
  FROM tprm.vendor_assessments va
 INNER JOIN tprm.vendors v ON v.id = va.vendor_id
 WHERE va.organisation_id IS NULL;
GO

-- Supporting index for the new paged org-scoped finder.
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'ix_vendor_assessments_org' AND object_id = OBJECT_ID('tprm.vendor_assessments'))
    CREATE NONCLUSTERED INDEX ix_vendor_assessments_org
        ON tprm.vendor_assessments (organisation_id);
GO
