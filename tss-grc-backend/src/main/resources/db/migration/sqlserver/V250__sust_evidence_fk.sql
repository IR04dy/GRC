-- ============================================================================
-- V250 — S-C: ESG entities gain a real evidence FK
--
-- Replaces the free-text `evidence_ref` pattern on the sustainability domain
-- entities with a real foreign key into the generic evidence engine
-- (evidence.evidence_items). The sust module now REUSES module/evidence for the
-- full request -> upload(sha256) -> review -> approve -> version -> expire
-- lifecycle; this column holds the primary linked evidence item, and the
-- generic evidence.evidence_links table carries the many-to-many links that the
-- evidence-package endpoint bundles.
--
-- ON DELETE SET NULL: evidence uses soft-delete, so this only matters for a hard
-- purge — the sustainability row survives with a null FK rather than blocking.
-- Idempotent: safe to re-run on a dev DB (Flyway is OFF in dev).
-- ============================================================================

IF COL_LENGTH('sust.ghg_emission_record', 'evidence_item_id') IS NULL
    ALTER TABLE sust.ghg_emission_record ADD evidence_item_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_ghg_evidence_item')
    ALTER TABLE sust.ghg_emission_record ADD CONSTRAINT FK_ghg_evidence_item
        FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL;
GO

IF COL_LENGTH('sust.cma_disclosure', 'evidence_item_id') IS NULL
    ALTER TABLE sust.cma_disclosure ADD evidence_item_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_cma_evidence_item')
    ALTER TABLE sust.cma_disclosure ADD CONSTRAINT FK_cma_evidence_item
        FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL;
GO

IF COL_LENGTH('sust.tcfd_item', 'evidence_item_id') IS NULL
    ALTER TABLE sust.tcfd_item ADD evidence_item_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_tcfd_evidence_item')
    ALTER TABLE sust.tcfd_item ADD CONSTRAINT FK_tcfd_evidence_item
        FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL;
GO

IF COL_LENGTH('sust.sgi_commitment', 'evidence_item_id') IS NULL
    ALTER TABLE sust.sgi_commitment ADD evidence_item_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_sgi_evidence_item')
    ALTER TABLE sust.sgi_commitment ADD CONSTRAINT FK_sgi_evidence_item
        FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL;
GO

IF COL_LENGTH('sust.climate_risk_opportunity', 'evidence_item_id') IS NULL
    ALTER TABLE sust.climate_risk_opportunity ADD evidence_item_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_climate_evidence_item')
    ALTER TABLE sust.climate_risk_opportunity ADD CONSTRAINT FK_climate_evidence_item
        FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL;
GO

IF COL_LENGTH('sust.esg_metric', 'evidence_item_id') IS NULL
    ALTER TABLE sust.esg_metric ADD evidence_item_id BIGINT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_esg_metric_evidence_item')
    ALTER TABLE sust.esg_metric ADD CONSTRAINT FK_esg_metric_evidence_item
        FOREIGN KEY (evidence_item_id) REFERENCES evidence.evidence_items(id) ON DELETE SET NULL;
GO

-- Allow the sustainability module to link evidence: extend the
-- evidence_links module whitelist with 'SUSTAINABILITY' (additive — every
-- previously-allowed module value stays valid). Drop-and-recreate so the
-- change is idempotent on a Python-applied dev DB.
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_ev_links_module')
    ALTER TABLE evidence.evidence_links DROP CONSTRAINT CK_ev_links_module;
GO
ALTER TABLE evidence.evidence_links ADD CONSTRAINT CK_ev_links_module
    CHECK (linked_module IN ('INCIDENT','AUDIT','TPRM','PRIVACY','BCM','CYBER','COMPLIANCE','RISK','SUSTAINABILITY'));
GO
