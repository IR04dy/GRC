/* ============================================================================
 * V382 — F2-259: AUDIT:WRITE for BOARD_SECRETARY, the role the converted guard named
 * ============================================================================
 *
 * F2-259 converts AuditCommitteeDashboardController's three snapshot-mutating routes
 * (#generate, #recordResolution, #finalizeSnapshot) from
 *
 *     hasAnyRole('BOARD_SECRETARY','PLATFORM_ADMIN')      ->      hasPermission('AUDIT','WRITE')
 *
 * because BOARD_SECRETARY exists in iam.roles and is held by NO USER in dev or prod, so the
 * guard admitted the platform administrator alone — exactly what F2-52's own heading on that
 * constant says it was fixing. F2-52 corrected COMPANY_SECRETARY to BOARD_SECRETARY and wrote
 * that this "restores one of the two intended personas". It restored nobody: hasAnyRole tests
 * for the granted authority ROLE_X, so naming a role that EXISTS is not admitting anybody.
 * Probed live pre-fix — `compliance` (GRC_ADMINISTRATOR) and `auditor` (INTERNAL_AUDITOR),
 * BOTH holding AUDIT:WRITE, answered 403 on all three routes while `admin` generated at 201.
 *
 * ── WHY THIS MIGRATION EXISTS ────────────────────────────────────────────────────────────
 * BOARD_SECRETARY holds AUDIT:READ and NOT AUDIT:WRITE (measured, both databases). So the
 * conversion on its own would have NARROWED the shipped audience: the one persona the guard
 * was written for would lose the capability the guard existed to give it. That is a policy
 * change disguised as a refactor (V362's landmine, F2-258's precedent), so the grant lands in
 * the same commit as the conversion and the conversion then drops nobody.
 *
 * Live effect today: NONE. Nobody holds BOARD_SECRETARY in either database, so no live
 * audience changes. What changes is what a tenant administrator gets when they assign it —
 * which is the whole remedy section B2b of ROLE_GUARD_CENSUS.md describes.
 *
 * ── WHAT IS DELIBERATELY *NOT* GRANTED ───────────────────────────────────────────────────
 *   · AUDIT:DELETE  — the Board Secretary prepares and finalises the committee's snapshot;
 *                     destroying audit findings is not that role's business.
 *   · AUDIT:EXPORT  — NOT granted here because BOARD_SECRETARY ALREADY HOLDS IT.
 *   · AUDIT:READ    — NOT granted here because BOARD_SECRETARY ALREADY HOLDS IT. Re-asserting
 *                     it would make check 2's containment leg pass for the wrong reason.
 *   · PLATFORM_ADMIN gets nothing: per V272 it needs no grants (evaluator bypass).
 *
 * ⚠ The pre-existing set is {READ, EXPORT}, and check 1's FIRST DRAFT asserted a post-grant
 *   count of 2 on the assumption it was {READ} alone. It THROWed on the first dev run, which
 *   is the self-check earning its place: the migration refused rather than proceeding on a
 *   wrong premise. The fact it surfaced is worth the words — the platform already lets the
 *   Board Secretary READ and EXPORT the audit register, and withheld only the ability to
 *   prepare that committee's own snapshot.
 *
 * ⚠ The grant landmine: the house idiom `INSERT … SELECT … JOIN iam.roles JOIN iam.permissions
 *   WHERE NOT EXISTS` grants NOTHING when either side is catalogued later in the chain, and
 *   NOT EXISTS cannot tell *already granted* from *nothing matched* — so it succeeds silently.
 *   The (role, module, action) triples are therefore DECLARED as literals and every assumption
 *   is a self-check that THROWs.
 *
 * Idempotent: re-running inserts nothing and every check still passes.
 * ==========================================================================================*/

SET NOCOUNT ON;
GO

/* ── check 0 — the pre-flight: the role must be seated, or the chain matches nothing ────── */
IF NOT EXISTS (SELECT 1 FROM iam.roles WHERE role_name = N'BOARD_SECRETARY' AND is_deleted = 0)
    THROW 51382, 'V382 check 0: role BOARD_SECRETARY is not present in iam.roles. The grant chain would match nothing and report success. Seat the role first.', 1;
GO

/* ── check 0b — the permission pair must be catalogued (AUDIT:* since the module was built) */
IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'AUDIT' AND action = N'WRITE')
    THROW 51382, 'V382 check 0b: permission AUDIT:WRITE is not catalogued. A guard citing an uncatalogued pair can only ever answer false (F2-255).', 1;
GO

/* ── the grant — declared literals, never derived from iam.role_permissions ─────────────── */
INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES (N'BOARD_SECRETARY', N'AUDIT', N'WRITE')) AS t(role_name, module, action)
  JOIN iam.roles       r ON r.role_name = t.role_name AND r.is_deleted = 0
  JOIN iam.permissions p ON p.module = t.module AND p.action = t.action
 WHERE NOT EXISTS (SELECT 1
                     FROM iam.role_permissions rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id);
GO

/* ── check 1 — the declared grant landed. Asserted as the exact expected SET rather than a
 * bare EXISTS, so granting MORE than intended fails too; the pre-existing two are named so a
 * future change to either is a deliberate edit here rather than a silent drift. */
IF (SELECT COUNT(*)
      FROM iam.role_permissions rp
      JOIN iam.roles       r ON r.id = rp.role_id
      JOIN iam.permissions p ON p.id = rp.permission_id
     WHERE r.role_name = N'BOARD_SECRETARY'
       AND p.module = N'AUDIT'
       AND p.action IN (N'READ', N'EXPORT', N'WRITE')) <> 3
    THROW 51382, 'V382 check 1: BOARD_SECRETARY should hold exactly AUDIT READ+EXPORT+WRITE after this migration (READ and EXPORT pre-existing, WRITE granted here). It does not.', 1;
GO

/* ── check 2 — containment: nobody writes the audit module without being able to read it ──
 * Scoped to READ/WRITE on purpose. It is NOT true across the whole module: AUDITOR holds
 * AUDIT:EXPORT and not AUDIT:READ, deliberately, and a wider assertion would fail on that. */
IF EXISTS (SELECT 1
             FROM iam.role_permissions rpw
             JOIN iam.permissions pw ON pw.id = rpw.permission_id
            WHERE pw.module = N'AUDIT' AND pw.action = N'WRITE'
              AND NOT EXISTS (SELECT 1
                                FROM iam.role_permissions rpr
                                JOIN iam.permissions pr ON pr.id = rpr.permission_id
                               WHERE rpr.role_id = rpw.role_id
                                 AND pr.module = N'AUDIT' AND pr.action = N'READ'))
    THROW 51382, 'V382 check 2: a role holds AUDIT:WRITE without AUDIT:READ. Nobody should be able to mutate the Audit Committee snapshot they cannot read.', 1;
GO

/* ── check 3 — anti-vacuity: AUDIT:WRITE must still be a real, narrow tier ───────────────
 * A floor catches a chain that silently granted nothing; a ceiling catches one that granted
 * the permission to everybody. Measured before this migration: 3 roles. */
IF (SELECT COUNT(*)
      FROM iam.role_permissions rp
      JOIN iam.permissions p ON p.id = rp.permission_id
     WHERE p.module = N'AUDIT' AND p.action = N'WRITE') NOT BETWEEN 4 AND 12
    THROW 51382, 'V382 check 3: AUDIT:WRITE holder count is outside 4..12. Either the grant chain matched nothing, or the write tier has been widened by something other than this migration.', 1;
GO

/* ── check 4 — the role stays out of the destructive tier ───────────────────────────────── */
IF EXISTS (SELECT 1
             FROM iam.role_permissions rp
             JOIN iam.roles       r ON r.id = rp.role_id
             JOIN iam.permissions p ON p.id = rp.permission_id
            WHERE r.role_name = N'BOARD_SECRETARY'
              AND p.module = N'AUDIT' AND p.action = N'DELETE')
    THROW 51382, 'V382 check 4: BOARD_SECRETARY was granted AUDIT:DELETE. This migration grants WRITE only — preparing the committee snapshot is not destroying audit findings.', 1;
GO
