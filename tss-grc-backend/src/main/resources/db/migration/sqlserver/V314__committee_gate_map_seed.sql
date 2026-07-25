-- =====================================================================
-- V314 (S4 / W1-4) — seed the per-tenant CAB/ECAB committee gate map.
-- ---------------------------------------------------------------------
-- Independent-assessment finding F-4: admin.committee_gate_map was EMPTY in every
-- tenant, while change_mgmt.change_authorisation_matrix demands a committee for
-- CAB (5 rows) and ECAB (6 rows) in all three orgs. The gate map is what resolves
-- a change's authority to an actual committee (CommitteeReferralService.refer →
-- findByOrganisationIdAndGate), so with no rows:
--   * refer() fails with "No committee is configured for gate CAB", and
--   * no change is ever actually committee-gated — every change silently falls
--     back to the legacy approval tally.
-- The flagship Track D control was therefore implemented but not in force.
--
-- Committee choice (deliberate, least-surprising):
--   CAB  → IT_STEERING, else EXEC_COMMITTEE, else any ACTIVE committee
--   ECAB → EXEC_COMMITTEE, else CRISIS_MANAGEMENT, else IT_STEERING, else ACTIVE
-- Emergency authorisation belongs with executives/crisis management; routine change
-- advisory belongs with IT steering. DISSOLVED committees are excluded —
-- CommitteeReferralService.refer() rejects them, so mapping one would just move the
-- failure later.
--
-- DELIBERATE SCOPE LIMIT: this seeds a mapping ONLY where the tenant already has an
-- eligible ACTIVE committee. Orgs 2 and 3 currently have NO committees at all, and
-- inventing one here would create a governance body with no charter, no chair and no
-- members — it could never reach quorum, so minutes could never be approved and the
-- gate would fail at the last step instead of the first, while LOOKING configured.
-- Those tenants are surfaced by the companion config-health check
-- (ChangeGovernanceHealthService) so an administrator constitutes a real committee.
-- Fail loudly and visibly beats fail quietly and late.
--
-- Idempotent: NOT EXISTS on (organisation_id, gate); safe to re-run.
-- Reverse recipe:
--   DELETE FROM admin.committee_gate_map WHERE created_at >= '<deploy date>';
--   -- or, to clear only what this seeded:
--   DELETE FROM admin.committee_gate_map WHERE gate IN ('CAB','ECAB');
-- =====================================================================

-- ── CAB ──────────────────────────────────────────────────────────────────────
INSERT INTO admin.committee_gate_map (organisation_id, gate, committee_id, is_deleted)
SELECT m.organisation_id, 'CAB', pick.committee_id, 0
FROM (
    SELECT DISTINCT organisation_id
    FROM change_mgmt.change_authorisation_matrix
    WHERE is_deleted = 0 AND gate = 'CAB'
) m
CROSS APPLY (
    SELECT TOP 1 c.id AS committee_id
    FROM admin.committees c
    WHERE c.organisation_id = m.organisation_id
      AND c.is_deleted = 0
      AND c.status = 'ACTIVE'
    ORDER BY CASE c.committee_type
                 WHEN 'IT_STEERING'    THEN 1
                 WHEN 'EXEC_COMMITTEE' THEN 2
                 ELSE 3
             END, c.id
) pick
WHERE NOT EXISTS (
    SELECT 1 FROM admin.committee_gate_map g
    WHERE g.organisation_id = m.organisation_id AND g.gate = 'CAB' AND g.is_deleted = 0);
GO

-- ── ECAB ─────────────────────────────────────────────────────────────────────
INSERT INTO admin.committee_gate_map (organisation_id, gate, committee_id, is_deleted)
SELECT m.organisation_id, 'ECAB', pick.committee_id, 0
FROM (
    SELECT DISTINCT organisation_id
    FROM change_mgmt.change_authorisation_matrix
    WHERE is_deleted = 0 AND gate = 'ECAB'
) m
CROSS APPLY (
    SELECT TOP 1 c.id AS committee_id
    FROM admin.committees c
    WHERE c.organisation_id = m.organisation_id
      AND c.is_deleted = 0
      AND c.status = 'ACTIVE'
    ORDER BY CASE c.committee_type
                 WHEN 'EXEC_COMMITTEE'     THEN 1
                 WHEN 'CRISIS_MANAGEMENT'  THEN 2
                 WHEN 'IT_STEERING'        THEN 3
                 ELSE 4
             END, c.id
) pick
WHERE NOT EXISTS (
    SELECT 1 FROM admin.committee_gate_map g
    WHERE g.organisation_id = m.organisation_id AND g.gate = 'ECAB' AND g.is_deleted = 0);
GO
