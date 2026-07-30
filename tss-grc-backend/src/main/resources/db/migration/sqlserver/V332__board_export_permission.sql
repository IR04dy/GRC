/* ============================================================================
   V332 — the board pack gets its own permission, because stripping the two it
          shared would have taken the risk register down with it.

   ADR-017 D-10a. Idempotent: safe to run twice (dev applies by hand, Flyway off).

   ── WHAT WAS MEASURED, ON BOTH DATABASES ───────────────────────────────────
   The two board-pack exports are guarded by

       hasPermission('REPORTING','EXPORT') or hasPermission('RISK','EXPORT')

   Roles satisfying that disjunction:  dev 24 of 59  ·  PROD 24 of 57.
   The finding reproduces in production, so this is not a seed-data artefact.

   Among the 24: VIEWER, SUPPLIER_MANAGER, MA_LEAD, EMERGENCY_RESPONDER,
   REGULATOR_VIEWER, PRIVACY_MANAGER, SUSTAINABILITY_OFFICER, DPO, RISK_ANALYST.
   A board pack is the platform's most sensitive single document — it combines
   ranked enterprise risk, audit-finding aging, KRI breaches, framework coverage,
   change posture and ethics/AML highlights in one file, and it renders as
   "Confidential" by its own page event. So "who may pull it" is not a cosmetic
   question.

   ⚠ CORRECTION, 2026-07-31, to the sentence this comment carried when it was
   first committed. It said the pack "carries up to 200 itemised risks and 1,000
   itemised audit findings per call", citing F2-55. That was wrong, and F2-55 is
   wrong: those are the PAGE SIZES FETCHED, not the payload. Measured in
   BoardPackService: 200 risks are fetched, sorted by inherent score, and
   `subList(0, 20)` renders TWENTY; 1,000 findings are fetched and reduced to four
   aging counters plus a table that breaks at `++rowCount >= 15`. So the pack
   renders 20 risks and 15 findings — bounded board MI, which is what F2-55 said a
   board pack ought to be.

   Both the original claim and F2-55 measured the query and reported it as the
   document. The narrowing below is unaffected: it rests on 24 of 57 PRODUCTION
   roles holding a board-confidential document, which is true irrespective of how
   many rows that document prints. Recording the correction here rather than
   quietly restating it, because the overstatement travelled into two commit
   messages before anyone checked what happened to the rows after the fetch.

   This edit is safe: Flyway has NOT applied V332 to production yet (prod is
   pending the merge), so no checksum exists for it to invalidate. Editing an
   applied migration's file — comment or not — breaks `flyway:validate`.

   ── WHY THIS IS NOT A GRANT REVOCATION, WHICH IS WHAT D-10a ASKED FOR ──────
   D-10a said: strip the four grants with no colourable claim. Measurement says
   that instrument does not fit, for a reason that only shows up when you look at
   the guard rather than the grant table:

     * The guard is a DISJUNCTION. To stop a role reaching the pack you must
       remove BOTH permissions from it, not one.
     * REPORTING:EXPORT and RISK:EXPORT authorise far more than this pack. On
       prod, EXPORT audit events break down as risk-register 12, packet 7,
       evidence-package 7, ai-model-registry 3, regulator-package 2,
       cyber-incidents 1, board-pack 1. So the collateral of a strip is the
       platform's MOST-USED export, and the beneficiary is its least-used one.
     * Seat A's objection to naive stripping was the inverse defect: taking
       EXPORT from EXTERNAL_AUDITOR risks inability to furnish under Banking
       Control Law art. 18, or obstruction of an audit mid-engagement.

   So the defect is in the GUARD, not the grant — the mirror image of ADR-015,
   where the guard was right and the grant was the defect. The pack gets its own
   permission; nothing else loses anything.

   ── THE LOOK-BACK, WHICH D-10a MADE A PRECONDITION ─────────────────────────
   "Was it exercised?" is internal audit's first question, and a hit starts a
   PDPL art. 20 assessment. Run on PROD before writing this file:

       board-pack EXPORT events on prod : 1
       actor                            : admin, organisation 1, 2026-07-08
       total EXPORT events on prod      : 33

   One event, by the platform administrator, during commissioning. No
   unauthorised use. Dev shows 41, all `admin`, all in the F2-27/F2-41 test
   windows. Nothing to assess, and that is a finding worth recording rather than
   assuming: the narrowing below is preventive, not remedial.

   ⚠ That single prod row also reproduces F2-54 in production: its `user_role`
   reads `BCM_APPROVE`, which is a PERMISSION, not a role. The ledger
   mis-attributes the actor on the one board-pack export it holds.

   ── WHO GETS IT, AND THE F2-56 CONSTRAINT THAT SHAPED THE LIST ─────────────
   F2-56: `iam.roles` is tenant-scoped in practice — 47 roles at organisation 1
   and only 10 global on prod (12 in dev). A grant list resolved by role_name
   therefore reaches ONE tenant unless it deliberately includes global roles.
   BOARD_SECRETARY, CFO, CISO, GRC_ADMINISTRATOR, EXECUTIVE_REVIEWER and
   EXTERNAL_AUDITOR are all organisation-1 rows. Granting only those would ship
   a board-pack export that no one in tenants 2 or 3 can ever use — the exact
   shape ADR-017 D-8 examined and rejected.

   ORG_ADMIN and INTERNAL_AUDITOR are global. They are in the list for that
   reason as much as for their claim, and the anti-vacuity block below FAILS the
   migration if the global count ever drops to zero.

     ORG_ADMIN          global   the tenant's own administrator; the only path a
                                 newly-onboarded tenant has to its own board pack
     INTERNAL_AUDITOR   global   assurance; IIA Standard 2060 reporting to the board
     BOARD_SECRETARY    org 1    convenes the board and distributes the pack
     CFO                org 1    presents to the board
     EXECUTIVE_REVIEWER org 1    executive sign-off in this platform's own workflows
     CISO               org 1    presents cyber posture; NCA ECC 1-2 board reporting
     GRC_ADMINISTRATOR  org 1    operates the platform for the tenant
     EXTERNAL_AUDITOR   org 1    statutory audit; the furnish/obstruction argument

   NOT granted, and each keeps every export it already had: VIEWER,
   SUPPLIER_MANAGER, MA_LEAD, EMERGENCY_RESPONDER, PRIVACY_MANAGER,
   SUSTAINABILITY_OFFICER, DPO, RISK_ANALYST, RISK_OWNER, RISK_MANAGER,
   BCM_MANAGER, CYBER_MANAGER, COMPLIANCE_OFFICER, FINANCE_LEAD, AUDITOR,
   REGULATOR_VIEWER.

   ⚠ REGULATOR_VIEWER is deliberately excluded and deliberately noted. A SAMA or
   CMA inspector receiving a board pack is normal; a regulator persona holding a
   self-service pull on 1,000 itemised findings is not the same thing. That is
   D-10b's assurance-mandate mechanism, which does not exist yet.

   `BOARD` becomes the 30th permission module (dev) / 26th (prod). It carries
   exactly ONE action, because exactly one pair of endpoints is being guarded —
   F2-13b's defect is permissions that are catalogued and granted and then guard
   nothing, and padding a module out to six actions is how that happens.
   BoardPackController still reads on ADMIN:READ/WRITE; re-homing those is a
   wider change with its own grant question, and it is not in this slice.
   ============================================================================ */

SET NOCOUNT ON;

/* ── 1. catalogue BOARD:EXPORT ─────────────────────────────────────────── */
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = 'BOARD' AND action = 'EXPORT')
BEGIN
    INSERT INTO iam.permissions (module, action, permission_name, description_en)
    VALUES ('BOARD', 'EXPORT', 'BOARD_EXPORT',
            'Download the board pack (PDF/PPTX) — board and committee reporting material');
END;

DECLARE @permId BIGINT =
    (SELECT id FROM iam.permissions WHERE module = 'BOARD' AND action = 'EXPORT');

IF @permId IS NULL
    THROW 51332, 'V332: BOARD:EXPORT was not catalogued. Annotating an endpoint against an uncatalogued permission is the F2-23 defect — a guard no tenant can satisfy.', 1;

/* ── 2. grant it, by role name, to the eight roles with a claim ─────────── */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, @permId
FROM iam.roles r
WHERE r.role_name IN ('ORG_ADMIN', 'INTERNAL_AUDITOR', 'BOARD_SECRETARY', 'CFO',
                      'EXECUTIVE_REVIEWER', 'CISO', 'GRC_ADMINISTRATOR', 'EXTERNAL_AUDITOR')
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                  WHERE rp.role_id = r.id AND rp.permission_id = @permId);

/* ── 3. anti-vacuity: a grant that reaches nobody, or reaches one tenant
        only, is worse than no grant — it manufactures assurance ─────────── */
DECLARE @granted INT = (SELECT COUNT(*) FROM iam.role_permissions WHERE permission_id = @permId);
DECLARE @globalGranted INT = (
    SELECT COUNT(*) FROM iam.role_permissions rp
    JOIN iam.roles r ON r.id = rp.role_id
    WHERE rp.permission_id = @permId AND r.organisation_id IS NULL);

IF @granted = 0
    THROW 51332, 'V332: BOARD:EXPORT is catalogued but granted to no role. Every board pack becomes undownloadable by anyone except PLATFORM_ADMIN, which bypasses in code.', 1;

IF @globalGranted = 0
    THROW 51332, 'V332: BOARD:EXPORT reached no GLOBAL role (organisation_id IS NULL), so it reaches organisation 1 only and no other tenant can ever download a board pack — F2-56. ORG_ADMIN and INTERNAL_AUDITOR must exist as global roles.', 1;

/* ── 4. anti-vacuity, the other direction: the narrowing must have teeth ── */
IF EXISTS (SELECT 1 FROM iam.role_permissions rp
           JOIN iam.roles r ON r.id = rp.role_id
           WHERE rp.permission_id = @permId
             AND r.role_name IN ('VIEWER', 'SUPPLIER_MANAGER', 'MA_LEAD',
                                 'EMERGENCY_RESPONDER', 'RISK_ANALYST', 'REGULATOR_VIEWER'))
    THROW 51332, 'V332: a role this migration exists to exclude holds BOARD:EXPORT. The narrowing is inert — check the grant list against the exclusion list before shipping.', 1;

PRINT CONCAT('V332 OK — BOARD:EXPORT id=', @permId,
             ' granted to ', @granted, ' role(s), of which ', @globalGranted,
             ' are global (reachable by every tenant).');
