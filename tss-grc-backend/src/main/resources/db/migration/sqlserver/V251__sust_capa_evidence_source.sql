-- ============================================================================
-- V251 — S-D: allow EVIDENCE as an auto-CAPA source_type
--
-- The auto-CAPA / gap-closure loop (S-D) mirrors the compliance F8 AR-EVD-GAP
-- rule: a gap surfaced elsewhere in the sustainability domain idempotently
-- opens a sust.corrective_action, deduped by (source_type, source_ref).
--
-- Three trigger families feed it:
--   * SGI commitment behind plan (tier DELAYED)        -> source_type SGI_COMMITMENT
--   * CMA / TCFD disclosure PARTIAL | NOT_DISCLOSED     -> source_type CMA_DISCLOSURE / TCFD_ITEM
--   * sustainability EVIDENCE rejected                  -> source_type EVIDENCE   (NEW)
--
-- The first two source_types are already permitted by the V58 ck_ca_source
-- CHECK; the evidence-rejected trigger needs a new value. Widen the CHECK to
-- add N'EVIDENCE' (additive — every previously-allowed value stays valid).
-- Drop-and-recreate so it is idempotent on a Python-applied dev DB (Flyway is
-- OFF in dev). Mirrors the V236 bcm.gaps 'EVIDENCE' precedent.
-- ============================================================================

IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE name = 'ck_ca_source'
             AND parent_object_id = OBJECT_ID('sust.corrective_action'))
    ALTER TABLE sust.corrective_action DROP CONSTRAINT ck_ca_source;
GO
ALTER TABLE sust.corrective_action ADD CONSTRAINT ck_ca_source CHECK (source_type IN (
    N'CLIMATE_RISK', N'GHG_RECORD', N'ANNEX_GAP', N'CMA_DISCLOSURE',
    N'TCFD_ITEM', N'SGI_COMMITMENT', N'EVIDENCE', N'MANUAL'));
GO
