-- ============================================================================
-- V298 — GF-2: green finance joins the evidence/assurance fabric (+ GF-5 rider)
--
-- The four green-finance object types gain the V250-style evidence FK so their
-- figures become assurance-grade: linkable via the S-C bridge, bundled by the
-- evidence-package endpoint, and graded in the ISAE 3410 lineage view
-- (GreenProject + CarbonCredit rows — plan §5 decision #2: projects + credits
-- carry the disclosed figures; instruments/allocations stay linkable but out of
-- the lineage view). Carbon-credit retirement keeps accepting the legacy
-- certificate_ref text (compat) but prefers the FK.
--
-- GF-5 rider: green_instruments.prospectus_ref (REG-8 — the offering-document
-- reference next to isin/listing_venue).
--
-- ON DELETE SET NULL matches V250: evidence soft-deletes, so this only guards a
-- hard purge. Idempotent — safe to re-run on the Python-managed dev DB.
-- ============================================================================

IF COL_LENGTH('sust.green_projects', 'evidence_item_id') IS NULL
    ALTER TABLE sust.green_projects ADD evidence_item_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_green_project_evidence_item')
    ALTER TABLE sust.green_projects ADD CONSTRAINT FK_green_project_evidence_item
        FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL;
GO

IF COL_LENGTH('sust.green_instruments', 'evidence_item_id') IS NULL
    ALTER TABLE sust.green_instruments ADD evidence_item_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_green_instrument_evidence_item')
    ALTER TABLE sust.green_instruments ADD CONSTRAINT FK_green_instrument_evidence_item
        FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL;
GO

IF COL_LENGTH('sust.instrument_allocations', 'evidence_item_id') IS NULL
    ALTER TABLE sust.instrument_allocations ADD evidence_item_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_instrument_allocation_evidence_item')
    ALTER TABLE sust.instrument_allocations ADD CONSTRAINT FK_instrument_allocation_evidence_item
        FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL;
GO

IF COL_LENGTH('sust.carbon_credits', 'evidence_item_id') IS NULL
    ALTER TABLE sust.carbon_credits ADD evidence_item_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_carbon_credit_evidence_item')
    ALTER TABLE sust.carbon_credits ADD CONSTRAINT FK_carbon_credit_evidence_item
        FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL;
GO

-- GF-5 (REG-8) — offering-document reference on the instrument register.
IF COL_LENGTH('sust.green_instruments', 'prospectus_ref') IS NULL
    ALTER TABLE sust.green_instruments ADD prospectus_ref NVARCHAR(200) NULL;
GO
