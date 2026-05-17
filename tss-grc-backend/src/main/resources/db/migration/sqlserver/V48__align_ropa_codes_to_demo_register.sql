-- V48 — Align seeded PDPL workflow ROPA references with the RopaPage demo register.
--
-- Problem: V32..V34 seeded privacy workflow records (cross_border_transfers,
-- disposal_certificates, privacy_notices) with ropa_code values like
-- 'ROPA-2026-001' .. 'ROPA-2026-007'. The frontend RopaPage uses a hardcoded
-- demo register (lib/ropaConsent.ts: ROPA_ENTRIES) keyed on 'ROPA-001' ..
-- 'ROPA-008'. The ROPA → PDPL workflows bundle lookup keyed on those page
-- codes therefore returned counts=0 for every ROPA, so the "Privacy notices /
-- Cross-border transfers / Disposal certificates" chips on every ROPA detail
-- appeared empty.
--
-- Fix: rewrite the prefix in place so 'ROPA-2026-NNN' → 'ROPA-NNN'. Numeric
-- segments are unchanged (we have 001..007 on both sides). After this lands,
-- the bundle controller finds matching rows and chips render with real counts.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

UPDATE privacy.cross_border_transfers
   SET ropa_code = N'ROPA-' + SUBSTRING(ropa_code, 11, 3)
 WHERE ropa_code LIKE N'ROPA-2026-%';
GO

UPDATE privacy.disposal_certificates
   SET related_entity_id = N'ROPA-' + SUBSTRING(related_entity_id, 11, 3)
 WHERE related_entity_type = N'ropa'
   AND related_entity_id LIKE N'ROPA-2026-%';
GO

UPDATE privacy.privacy_notices
   SET linked_ropa_codes = REPLACE(linked_ropa_codes, N'ROPA-2026-', N'ROPA-')
 WHERE linked_ropa_codes LIKE N'%ROPA-2026-%';
GO
