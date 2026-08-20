/* ============================================================================
 * V383 — F2-260: catalogue the RRP permission module, so the SAMA recovery-and-
 *                resolution filing can be lodged by somebody other than the
 *                platform administrator
 * ============================================================================
 *
 * A ROLE NOBODY HOLDS HAS AN ADMINISTRATOR REMEDY. A PERMISSION NOBODY CAN HOLD DOES NOT.
 * This is both at once, which is why it needs a migration rather than a guard edit.
 *
 *     POST /api/v1/rrp/cycles/{id}/submit   @PreAuthorize hasAnyRole('PLATFORM_ADMIN','CRO','CFO')
 *
 * `CRO` and `CFO` are held by NO USER in dev or prod, and `CRO` is additionally ABSENT from
 * prod's `iam.roles` because V373 is pending there. So the route that lodges the Recovery &
 * Resolution Plan with SAMA admitted the platform administrator alone — F2-253's holder
 * attrition, on the guard V373's own header calls the sharp case. And the ordinary remedy
 * (assign the role) is not available as a conversion target either, because **`RRP` has no
 * catalogued permission of any kind** in either database: F2-257 filed that DATACLASS,
 * OUTSOURCING, REGINSPECT, REGREPORTING and RRP have no WRITE/APPROVE row at all. This file
 * closes the first of those five.
 *
 * ── WHY THE LOCKOUT MATTERS HERE ─────────────────────────────────────────────────────────
 * `RrpService`'s cycle machine is
 *
 *     DRAFT → IN_PREPARATION → BOARD_REVIEW → APPROVED → SUBMITTED → ACKNOWLEDGED → CLOSED
 *
 * `/cycles/{id}/transition` carries the WRITE guard, which RISK_MANAGER holds, so a risk
 * manager can drive a cycle all the way to APPROVED. `transition` then explicitly REFUSES the
 * SUBMITTED edge — "Use the dedicated /submit endpoint to lodge with SAMA" — and
 * `ALLOWED.get("APPROVED")` is `{IN_PREPARATION}`. So for every non-administrator **APPROVED is
 * a dead end: the only forward edge is the administrator-only submit and the only other move is
 * backwards into preparation.** Measured identically in dev and prod: 5 cycles (3
 * IN_PREPARATION, 2 CLOSED), 46 critical functions, 31 indicators, 32 recovery options, 14
 * events — and **no cycle has ever reached SUBMITTED**, for which this guard is a sufficient
 * explanation.
 *
 * ── WHAT THIS FILE DOES ──────────────────────────────────────────────────────────────────
 *   1. Catalogues ('RRP','READ'), ('RRP','WRITE'), ('RRP','APPROVE') — exactly the three tiers
 *      RrpController already expresses as role lists, no more. V362's landmine is the opposite
 *      mistake: it classified actions by METHOD SHAPE and promoted whole WRITE tiers to APPROVE
 *      in four of six modules, which V367 and V368 then had to undo. The tiers here are read
 *      off the controller's own three constants, not inferred.
 *   2. Grants them as literal (role, module, action) triples.
 *
 * ⚠ The grant landmine: the house idiom `INSERT … SELECT … JOIN iam.roles JOIN iam.permissions
 *   WHERE NOT EXISTS` grants NOTHING when either side is catalogued later in the chain, and
 *   NOT EXISTS cannot tell *already granted* from *nothing matched* — so it succeeds silently.
 *   Everything below is declared, and every assumption is a self-check that THROWs.
 *
 * ── WHO IS GRANTED, PER ROLE, AND WHY ────────────────────────────────────────────────────
 * The rule applied throughout: **the conversion must drop nobody, and must widen only where a
 * route needs it.** V362's landmine and V382's precedent — "grant it when a route needs it, not
 * because the tier looks incomplete".
 *
 *   READ  (the guard names 7 non-admin roles; 3 have a live holder)
 *     RISK_MANAGER, COMPLIANCE_OFFICER, INTERNAL_AUDITOR   GRANTED — live readers today.
 *     BCM_MANAGER, BOARD_SECRETARY, CFO, CRO               GRANTED — named by the shipped
 *                                                          guard and held by nobody. Dropping
 *                                                          them would narrow what a tenant
 *                                                          administrator gets on assigning one.
 *     GRC_ADMINISTRATOR                                    GRANTED — required by containment:
 *                                                          it is granted APPROVE below, and
 *                                                          nobody may lodge a regulatory filing
 *                                                          they cannot read.
 *     AUDITOR, VIEWER, REGULATOR_VIEWER                    WITHHELD — all three hold BCM:READ
 *                                                          and RISK:READ, so "convention" would
 *                                                          argue for them. The shipped RRP read
 *                                                          guard names none of them, and a
 *                                                          resolution plan is the most sensitive
 *                                                          document in a bank's contingency set.
 *                                                          Widening it is an owner's decision,
 *                                                          not a side effect of a catalogue fix.
 *     ORG_ADMIN                                            WITHHELD — no route needs it for this
 *                                                          fix and it is held by nobody, so
 *                                                          granting it would be tier-tidying.
 *
 *   WRITE (the guard names 3 non-admin roles; 1 has a live holder)
 *     RISK_MANAGER, BCM_MANAGER, CRO                       GRANTED — exactly the shipped
 *                                                          audience. Population-preserving.
 *     GRC_ADMINISTRATOR                                    WITHHELD — deliberately, and this is
 *                                                          the sharpest decision in the file.
 *                                                          It holds WRITE on BCM and RISK, so
 *                                                          convention argues for it; but no
 *                                                          route needs it here, and withholding
 *                                                          it means the role that LODGES the
 *                                                          filing is not the role that PREPARES
 *                                                          it. See APPROVE.
 *
 *   APPROVE (the guard names 2 non-admin roles; NEITHER has a live holder anywhere)
 *     CRO, CFO                                             GRANTED — the shipped audience, so
 *                                                          assigning either role now confers
 *                                                          the capability without a new jar.
 *     GRC_ADMINISTRATOR                                    GRANTED — this is the unlock, and it
 *                                                          follows the platform's own convention
 *                                                          rather than a preference: it holds
 *                                                          APPROVE on BCM, RISK and ADMIN alike.
 *                                                          It is the only live role admitted.
 *     RISK_MANAGER                                         WITHHELD — it holds RISK:APPROVE, so
 *                                                          convention argues for it, and the
 *                                                          shipped RRP guard deliberately does
 *                                                          not name it: WRITE and APPROVE
 *                                                          overlap only on CRO. Granting it
 *                                                          would let the persona that prepares
 *                                                          and board-approves the cycle also
 *                                                          lodge it, retiring the one separation
 *                                                          this controller does express.
 *     INTERNAL_AUDITOR                                     WITHHELD — V380's reasoning: it reads
 *                                                          the plan in order to audit it, and
 *                                                          lodging it would make internal audit
 *                                                          the author of what it examines.
 *     EXECUTIVE_REVIEWER, ORG_ADMIN                        WITHHELD — both appear in BCM's and
 *                                                          RISK's APPROVE tiers and neither is
 *                                                          held by anyone; no route needs them.
 *     PLATFORM_ADMIN                                       WITHHELD — V272's convention:
 *                                                          "PLATFORM_ADMIN needs no grants
 *                                                          (evaluator bypass)".
 *
 * ⚠ Live audience change, stated rather than left as a side effect (V362's landmine): READ is
 *   unchanged (GRC_ADMINISTRATOR's holder already read the module through COMPLIANCE_OFFICER),
 *   WRITE is unchanged, and APPROVE goes from **the platform administrator alone to the platform
 *   administrator plus GRC_ADMINISTRATOR** — one role, one live holder, on one route.
 *
 * Idempotent: re-running inserts nothing and every check still passes.
 * ==========================================================================================*/

SET NOCOUNT ON;
GO

/* ── check 0 — the pre-flight: every role named below must be seated, or the grant chain
 * matches nothing and reports success. CRO is the one that fails on a prod that has not run
 * V373 yet, which is exactly the case worth failing loudly on. */
IF EXISTS (SELECT t.role_name
             FROM (VALUES (N'RISK_MANAGER'), (N'COMPLIANCE_OFFICER'), (N'INTERNAL_AUDITOR'),
                          (N'BCM_MANAGER'), (N'BOARD_SECRETARY'), (N'CFO'), (N'CRO'),
                          (N'GRC_ADMINISTRATOR')) AS t(role_name)
            WHERE NOT EXISTS (SELECT 1 FROM iam.roles r
                               WHERE r.role_name = t.role_name AND r.is_deleted = 0))
    THROW 51383, 'V383 check 0: one of the roles this migration grants is not present in iam.roles. The grant chain would match nothing and report success. CRO is seated by V373 — run it first.', 1;
GO

/* ── catalogue the three actions ───────────────────────────────────────────────────────────
 * description_en is NVARCHAR(300) — resolved from sys.columns, not guessed; SQL Server error
 * 2628 names the column and the truncated value but not the limit (F2-255). */
INSERT INTO iam.permissions (module, action, permission_name, description_en)
SELECT t.module, t.action, t.permission_name, t.description_en
  FROM (VALUES
         (N'RRP', N'READ',    N'RRP_READ',
          N'View Recovery & Resolution Plan cycles, recovery options, critical functions and early-warning indicators.'),
         (N'RRP', N'WRITE',   N'RRP_WRITE',
          N'Prepare the Recovery & Resolution Plan: open and edit cycles, record options, critical functions, indicators and measurements, and drive the cycle up to APPROVED.'),
         (N'RRP', N'APPROVE', N'RRP_APPROVE',
          N'Lodge the approved Recovery & Resolution Plan with SAMA (APPROVED to SUBMITTED). Distinct from RRP:WRITE, which prepares the plan but may not file it.')
       ) AS t(module, action, permission_name, description_en)
 WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p
                    WHERE p.module = t.module AND p.action = t.action);
GO

/* ── check 1 — exactly the three tiers the controller expresses, and no fourth ───────────── */
IF (SELECT COUNT(*) FROM iam.permissions WHERE module = N'RRP') <> 3
    THROW 51383, 'V383 check 1: iam.permissions should hold exactly 3 RRP actions (READ, WRITE, APPROVE) — the three constants RrpController declares. A fourth means an action was inferred rather than read off the guard (V362''s landmine).', 1;
GO

IF EXISTS (SELECT 1 FROM iam.permissions
            WHERE module = N'RRP' AND action NOT IN (N'READ', N'WRITE', N'APPROVE'))
    THROW 51383, 'V383 check 1b: an RRP action outside READ/WRITE/APPROVE exists. RrpController declares three guard constants; anything else is not a tier this module has.', 1;
GO

/* ── the grants — declared literals, never derived from iam.role_permissions ─────────────── */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES
         (N'RISK_MANAGER',       N'READ'),
         (N'COMPLIANCE_OFFICER', N'READ'),
         (N'INTERNAL_AUDITOR',   N'READ'),
         (N'BCM_MANAGER',        N'READ'),
         (N'BOARD_SECRETARY',    N'READ'),
         (N'CFO',                N'READ'),
         (N'CRO',                N'READ'),
         (N'GRC_ADMINISTRATOR',  N'READ'),
         (N'RISK_MANAGER',       N'WRITE'),
         (N'BCM_MANAGER',        N'WRITE'),
         (N'CRO',                N'WRITE'),
         (N'CRO',                N'APPROVE'),
         (N'CFO',                N'APPROVE'),
         (N'GRC_ADMINISTRATOR',  N'APPROVE')
       ) AS t(role_name, action)
  JOIN iam.roles       r ON r.role_name = t.role_name AND r.is_deleted = 0
  JOIN iam.permissions p ON p.module = N'RRP' AND p.action = t.action
 WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

/* ── check 2 — the declared grant counts, exactly ────────────────────────────────────────── */
IF (SELECT COUNT(*)
      FROM iam.role_permissions rp
      JOIN iam.permissions p ON p.id = rp.permission_id
     WHERE p.module = N'RRP') <> 14
    THROW 51383, 'V383 check 2: RRP should hold exactly 14 grants (8 READ + 3 WRITE + 3 APPROVE). A different number means the chain matched more or fewer roles than were declared.', 1;
GO

IF (SELECT COUNT(*)
      FROM iam.role_permissions rp
      JOIN iam.permissions p ON p.id = rp.permission_id
     WHERE p.module = N'RRP' AND p.action = N'APPROVE') <> 3
    THROW 51383, 'V383 check 2b: RRP:APPROVE should be held by exactly 3 roles (CRO, CFO, GRC_ADMINISTRATOR). This is the tier that lodges a filing with SAMA — it is not a tier to widen by accident.', 1;
GO

/* ── check 3 — containment: nobody may act on a filing they cannot read ──────────────────── */
IF EXISTS (SELECT 1
             FROM iam.role_permissions rpa
             JOIN iam.permissions pa ON pa.id = rpa.permission_id
            WHERE pa.module = N'RRP' AND pa.action IN (N'WRITE', N'APPROVE')
              AND NOT EXISTS (SELECT 1
                                FROM iam.role_permissions rpr
                                JOIN iam.permissions pr ON pr.id = rpr.permission_id
                               WHERE rpr.role_id = rpa.role_id
                                 AND pr.module = N'RRP' AND pr.action = N'READ'))
    THROW 51383, 'V383 check 3: a role holds RRP:WRITE or RRP:APPROVE without RRP:READ. Nobody should be able to prepare or lodge a recovery-and-resolution plan they cannot read.', 1;
GO

/* ── check 4 — the separation the shipped guard expresses is preserved ────────────────────
 * WRITE and APPROVE overlap on CRO and on nothing else. If they ever overlap on a second role,
 * the persona preparing the plan can also lodge it, and the controller's own two-tier split
 * has been retired by a grant rather than by a decision. */
IF (SELECT COUNT(*)
      FROM iam.role_permissions rpw
      JOIN iam.permissions pw ON pw.id = rpw.permission_id
     WHERE pw.module = N'RRP' AND pw.action = N'WRITE'
       AND EXISTS (SELECT 1
                     FROM iam.role_permissions rpa
                     JOIN iam.permissions pa ON pa.id = rpa.permission_id
                    WHERE rpa.role_id = rpw.role_id
                      AND pa.module = N'RRP' AND pa.action = N'APPROVE')) <> 1
    THROW 51383, 'V383 check 4: RRP WRITE and APPROVE must overlap on exactly one role (CRO, as the shipped guard had them). A second overlap means the preparer can also lodge with SAMA.', 1;
GO

/* ── check 5 — DELIBERATELY ABSENT, and the reason is a rule ───────────────────────────────
 * The obvious fifth check is the one this file exists for: *does any ACTIVE user now hold a
 * role granted RRP:APPROVE?* — F2-253's distinction, since satisfiable is a property of the
 * guard and satisfied is a property of iam.user_roles. It was written, and then removed.
 *
 * ⚠⚠ A MIGRATION MAY ASSERT ITS OWN WRITES AND MUST NOT ASSERT TENANT DATA. Role assignments
 * are tenant data: a brand-new deployment runs this chain with a bootstrap administrator and
 * no GRC_ADMINISTRATOR seated, so that check would THROW and block V383 and every migration
 * after it — a self-check that fails on correct usage, which is the one failure a check must
 * never have (F2-214, at the schema). The roles this file GRANTS are safe to require, because
 * they are created by migrations that run before it (V362 seats BOARD_SECRETARY and CFO, V373
 * seats CRO — verified, not assumed); who HOLDS them is not.
 *
 * So "did this actually unlock anything?" is proved by the live probe and held by
 * RrpGuardConversionTest, not here. The same reasoning is why check 6's floor is 50 rather
 * than the 100-ish figure both live databases carry: F2-255 measured that a fresh Flyway
 * install from V1 builds only **93** catalogue pairs, because 36 core pairs exist in dev and
 * prod solely because the Python bootstrap made them. A floor above 93 would block a fresh
 * install for a reason that has nothing to do with RRP.
 */
IF (SELECT COUNT(*) FROM iam.permissions) < 50
    THROW 51383, 'V383 check 6: iam.permissions holds fewer than 50 pairs, so this file is running against a catalogue that has not been built. See the note above for why this floor is 50 and not the ~140 the live databases carry.', 1;
GO
