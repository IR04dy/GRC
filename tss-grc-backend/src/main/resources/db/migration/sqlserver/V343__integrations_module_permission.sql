/* ============================================================================
   V343 — INTEGRATIONS becomes a permission module, because the guards it
          replaces named six roles that do not exist.

   ADR-017 D-8 (read as A17-D8; §6's D-8 is the unrelated CAB-voting decision).
   Idempotent: safe to run twice (dev applies by hand via pyodbc, Flyway off).

   ── THIS MIGRATION WIDENS ACCESS. THAT IS THE POINT, AND IT IS MEASURED ────
   D-8 was carried into the plan as "narrowing a PDPL/ECC-2 surface". It is the
   opposite. The 20 role-name guards in module/integrations recite nine role
   names, and SIX of them do not exist in iam.roles:

       SOC_AUTOMATION · INTEGRATION_SERVICE · SOC_ANALYST
       SOC_TIER_2     · INCIDENT_COMMANDER  · BOARD_MEMBER

   Verified absent on BOTH databases on 2026-08-01 (dev TSS_GRC and prod
   TSS_GRC_Deployment: 0 rows for all six, deleted or otherwise). hasAnyRole()
   never reaches ModulePermissionEvaluator, so a role guard gets NO
   PLATFORM_ADMIN bypass and a name absent from iam.roles admits nobody. The
   live consequence, probed as six users before this migration was written:

       endpoint tier                     PLATFORM_ADMIN CISO GRC_ADMIN CYBER_MGR RISK_MGR VIEWER
       8 status/config reads                    200      200    403       403      403     403
       4 stub/recorded-calls                    200      200    403       403      403     403
       3 outbound test/push                200/200/200  403*   403       403      403     403
       tenable/status only                      200      200    403       403      200     403

       (* splunk/test-push is hasRole('PLATFORM_ADMIN') — administrator-only today)

   So integration administration is reachable by exactly TWO personas, one of
   which bypasses in code. Accidental administrator-only-ness is not a security
   model: it is what a guard decays into when its role names are never checked
   against the role table. The re-grant below is therefore justified as a
   re-grant, per the rule D-10 adopted, and probed per endpoint rather than in
   aggregate.

   ── WHY NEITHER EXISTING PERMISSION FITS (the slice-4 measurement) ─────────
     CYBER:READ     — 19 roles including VIEWER, REGULATOR_VIEWER and
                      EXTERNAL_AUDITOR. Opens credential configuration and the
                      record of what left the platform to a regulator persona.
     PLATFORM:WRITE — ORG_ADMIN alone. Drops the CISO who runs the integrations.

   Integration administration is its own concern: credential rotation, endpoint
   configuration, and the log of what was transmitted to a third party. That is
   neither security operations nor platform administration, and the measured
   non-fit of both is the evidence rather than the assertion.

   ── SAUDI ANCHOR ──────────────────────────────────────────────────────────
   Outbound integration state is a DATA-TRANSFER surface. PDPL art. 29 restricts
   transfers of personal data outside the Kingdom and NCA ECC-2 requires control
   over data leaving the organisation, so "who may read the record of what we
   sent to a third party" is a named regulatory question. It deserves its own
   permission rather than a borrowed one.

   ⚠ AND THE AMENDMENT'S PDPL FRAMING IS NARROWER THAN IT CLAIMED — recorded
   because it was checked. D-8's amendment demanded a need-to-know rationale for
   "CYBER_MANAGER reading third-party egress logs, which may contain personal
   data". Measured against the code: NO endpoint in this module returns an egress
   payload in a live deployment. /status returns a timestamp; the four
   /stub/recorded-calls endpoints resolve a StubXxxClient bean via
   ObjectProvider#getIfAvailable and return [] when it is absent — and it is
   absent unless mode=STUB. Dev runs STUB (all four adapters, verified), so the
   payload exposure is real in dev and in any customer pilot left in STUB, and
   nil in LIVE mode. That is why the four stub endpoints below are guarded at the
   WRITE tier and not the READ tier: they are the only ones that can return a
   frozen request payload, and they should not widen to the two assurance
   personas that READ widens to.

   ── NEED-TO-KNOW, PER GRANTEE ─────────────────────────────────────────────
   INTEGRATIONS:READ → 4 roles
     ORG_ADMIN         global · the tenant's own administrator; without it a
                       newly-onboarded tenant cannot see whether its own SIEM
                       feed is alive. Already holds PLATFORM:READ/WRITE.
     GRC_ADMINISTRATOR global · operates the platform for the tenant; already
                       holds ADMIN:READ/WRITE/APPROVE/EXPORT.
     CISO              global · owns the security integrations. Named in 18 of
                       the 20 guards being replaced, so this is continuity.
     CYBER_MANAGER     global · THE ONE GRANT THAT IS NOT AN ADMINISTRATOR, and
                       the one D-8 required a rationale for. Rationale, measured
                       rather than argued: CYBER_MANAGER already holds CYBER:READ
                       + WRITE + DELETE + EXPORT, so every detection and
                       vulnerability finding these four adapters ingest is
                       already readable AND exportable by this role. What
                       INTEGRATIONS:READ adds is the record of the TRANSPORT —
                       adapter health, masked configuration, last-poll and
                       last-push timestamps — for payloads the role may already
                       read in full. Under PDPL that is the weaker access, not
                       the stronger one: a role that may read the data may read
                       whether the data moved. Refusing it would produce the
                       inverse absurdity of a cyber manager who can export a
                       vulnerability register but cannot tell whether the scanner
                       feeding it stopped four weeks ago.

   INTEGRATIONS:WRITE → 2 roles (ORG_ADMIN, CISO)
     Credential-bearing configuration and endpoints that transmit to a third
     party. Deliberately NOT GRC_ADMINISTRATOR or CYBER_MANAGER: reading whether
     a feed is healthy and choosing to send data out of the Kingdom are different
     acts, and only the second needs the narrower population.

   ── NOT GRANTED, and each keeps everything it already had ─────────────────
   VIEWER, REGULATOR_VIEWER, EXTERNAL_AUDITOR, AUDITOR, INTERNAL_AUDITOR,
   RISK_ANALYST, RISK_MANAGER, RISK_OWNER, SUPPLIER_MANAGER, DPO,
   PRIVACY_MANAGER, COMPLIANCE_OFFICER, BCM_MANAGER, SECURITY_ANALYST, IR_LEAD,
   and the eight *_INGEST machine roles.

   ⚠ RISK_MANAGER LOSES ONE ENDPOINT and it is the only loss in this slice:
   GET /api/v1/integrations/tenable/status, where RISK_MANAGER is named today
   alongside PLATFORM_ADMIN, CISO and FIVE of the six ghosts. That guard was
   written by enumeration, not by design — BOARD_MEMBER on an adapter kill-switch
   endpoint is the tell. RISK_MANAGER keeps recast, accept-risk and remove-recast
   (its actual function, unchanged below) and keeps RISK:* in full. It does not
   hold CYBER:READ on either database, so it never had a route to the findings
   themselves; losing the adapter's polling timestamp takes nothing it was using.
   Probed both ways rather than asserted.

   ── WHAT DOES NOT MOVE ────────────────────────────────────────────────────
   The four ingest endpoints stay on CYBER:WRITE (F2-05 slice 4) and the five
   outbound ACTION endpoints — crowdstrike contain/lift, tenable
   recast/accept-risk/remove-recast — keep their role lists with the ghost names
   STRIPPED, not converted. Converting them was measured and rejected:
   INTEGRATIONS:WRITE is {ORG_ADMIN, CISO} and would REVOKE recast and
   risk-acceptance from RISK_MANAGER, the role whose function that is; CYBER:WRITE
   is rejected by D-8 itself because it includes eight machine feeds and a SIEM
   must not be able to quarantine a host. Vulnerability risk-acceptance is a
   two-population decision (cyber AND risk) with no catalogued permission, and
   inventing one is not in this slice. Stripping the ghosts is provably
   access-preserving and leaves the population question honestly open.

   ── THE GHOSTS ARE DELETED, NOT SEEDED ────────────────────────────────────
   §7 standing exclusion. Seeding SOC_ANALYST to make an old guard fire would
   make an absent control look present. Block 5 FAILS this migration if any of
   the six ever appears in iam.roles, so the exclusion is enforced and not just
   documented.
   ============================================================================ */

SET NOCOUNT ON;

/* ── 1. catalogue INTEGRATIONS:READ and INTEGRATIONS:WRITE ──────────────── */
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = 'INTEGRATIONS' AND action = 'READ')
BEGIN
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('INTEGRATIONS', 'READ', 'INTEGRATIONS_READ',
            'View integration health, bound configuration (secrets masked) and transmission timestamps for Splunk / ServiceNow / CrowdStrike / Tenable');
END;

IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = 'INTEGRATIONS' AND action = 'WRITE')
BEGIN
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('INTEGRATIONS', 'WRITE', 'INTEGRATIONS_WRITE',
            'Administer integrations: transmit to a third party (test-push, manual event push) and read recorded request payloads');
END;

DECLARE @readId  BIGINT = (SELECT id FROM iam.permissions WHERE module = 'INTEGRATIONS' AND action = 'READ');
DECLARE @writeId BIGINT = (SELECT id FROM iam.permissions WHERE module = 'INTEGRATIONS' AND action = 'WRITE');

IF @readId IS NULL OR @writeId IS NULL
    THROW 51343, 'V343: INTEGRATIONS:READ / INTEGRATIONS:WRITE were not catalogued. Annotating an endpoint against an uncatalogued permission is the F2-23 defect — a guard no tenant can satisfy and no PLATFORM_ADMIN bypass to hide it.', 1;

/* ── 2. grant READ to the four roles with a claim ───────────────────────── */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, @readId
FROM iam.roles r
WHERE r.role_name IN ('ORG_ADMIN', 'GRC_ADMINISTRATOR', 'CISO', 'CYBER_MANAGER')
  AND ISNULL(r.is_deleted, 0) = 0
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                  WHERE rp.role_id = r.id AND rp.permission_id = @readId);

/* ── 3. grant WRITE to the two that may transmit ────────────────────────── */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, @writeId
FROM iam.roles r
WHERE r.role_name IN ('ORG_ADMIN', 'CISO')
  AND ISNULL(r.is_deleted, 0) = 0
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                  WHERE rp.role_id = r.id AND rp.permission_id = @writeId);

/* ── 4. anti-vacuity: granted to nobody, or to one tenant only ──────────── */
DECLARE @readGrants  INT = (SELECT COUNT(*) FROM iam.role_permissions WHERE permission_id = @readId);
DECLARE @writeGrants INT = (SELECT COUNT(*) FROM iam.role_permissions WHERE permission_id = @writeId);
DECLARE @readGlobal  INT = (SELECT COUNT(*) FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id
                            WHERE rp.permission_id = @readId  AND r.organisation_id IS NULL);
DECLARE @writeGlobal INT = (SELECT COUNT(*) FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id
                            WHERE rp.permission_id = @writeId AND r.organisation_id IS NULL);

IF @readGrants = 0 OR @writeGrants = 0
    THROW 51343, 'V343: INTEGRATIONS was catalogued but granted to no role. Every integration screen becomes PLATFORM_ADMIN-only again — the exact defect this migration exists to remove, re-created with a permission instead of a ghost.', 1;

IF @readGlobal = 0 OR @writeGlobal = 0
    THROW 51343, 'V343: an INTEGRATIONS grant reached no GLOBAL role (organisation_id IS NULL), so it reaches organisation 1 only and tenants 2..n cannot administer their own integrations — F2-56. All four grantees were global on both databases on 2026-08-01; if that changed, fix the role scoping, do not ship the grant.', 1;

/* ── 5. the ghosts must stay absent — §7, and the reason this module rotted */
IF EXISTS (SELECT 1 FROM iam.roles
           WHERE role_name IN ('SOC_AUTOMATION', 'INTEGRATION_SERVICE', 'SOC_ANALYST',
                               'SOC_TIER_2', 'INCIDENT_COMMANDER', 'BOARD_MEMBER'))
    THROW 51343, 'V343: one of the six ghost role names this migration retires now EXISTS in iam.roles. Seeding a ghost to make an old guard fire makes an absent control look present — §7 standing exclusion. Delete the row, or convert the guard that wanted it.', 1;

/* ── 6. WRITE holders must be a SUBSET of READ holders — ADR-017 D-10c ──── */
IF EXISTS (SELECT 1 FROM iam.role_permissions w
           WHERE w.permission_id = @writeId
             AND NOT EXISTS (SELECT 1 FROM iam.role_permissions r
                             WHERE r.role_id = w.role_id AND r.permission_id = @readId))
    THROW 51343, 'V343: a role holds INTEGRATIONS:WRITE without INTEGRATIONS:READ. A principal that may push data to a third party and is refused the health of the feed it pushed to is not a coherent authorisation state — same containment as ADR-017 D-10c states for EXPORT/READ.', 1;

/* ── 7. anti-vacuity, the other direction: the tiering must have teeth ──── */
IF EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id
           WHERE rp.permission_id IN (@readId, @writeId)
             AND r.role_name IN ('VIEWER', 'REGULATOR_VIEWER', 'EXTERNAL_AUDITOR',
                                 'RISK_ANALYST', 'SUPPLIER_MANAGER'))
    THROW 51343, 'V343: a role this migration exists to exclude holds an INTEGRATIONS permission. The whole argument for a new module was that CYBER:READ reaches VIEWER / REGULATOR_VIEWER / EXTERNAL_AUDITOR; if the new one does too, nothing was gained.', 1;

IF EXISTS (SELECT 1 FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id
           WHERE rp.permission_id = @writeId
             AND r.role_name IN ('GRC_ADMINISTRATOR', 'CYBER_MANAGER'))
    THROW 51343, 'V343: GRC_ADMINISTRATOR or CYBER_MANAGER holds INTEGRATIONS:WRITE. The READ/WRITE split exists precisely because reading whether a feed is healthy and choosing to transmit out of the Kingdom are different acts — collapsing them makes the second tier decorative.', 1;

PRINT CONCAT('V343 OK — INTEGRATIONS:READ id=', @readId, ' -> ', @readGrants, ' role(s) (', @readGlobal,
             ' global); INTEGRATIONS:WRITE id=', @writeId, ' -> ', @writeGrants, ' role(s) (', @writeGlobal,
             ' global). WRITE is a subset of READ. All six ghost names absent.');
