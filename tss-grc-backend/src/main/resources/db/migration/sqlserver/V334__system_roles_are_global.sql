/* ============================================================================
   V334 — the authorisation model was single-tenant in practice: 46 of the 58
          live roles existed only for organisation 1.

   F2-56. Idempotent: safe to run twice (dev applies by hand, Flyway off in dev).

   ── WHAT WAS MEASURED, ON BOTH DATABASES ───────────────────────────────────
   `iam.roles` holds, identically in dev and prod:

       46 rows  is_system_role = 1, organisation_id = 1   <-- wrong
       12 rows  is_system_role = 1, organisation_id NULL  (10 in prod)
        1 row   is_system_role = 0, organisation_id = 1   (REVIEW_TEST_ROLE,
                                                           soft-deleted)

   Roles are read through `findByOrganisationIdOrOrganisationIdIsNull(org)`, so a
   tenant sees its own rows plus the global ones and nothing else. A newly
   onboarded tenant therefore reaches TWELVE roles: CISO, GRC_ADMINISTRATOR,
   CYBER_MANAGER, BOARD_SECRETARY and EXTERNAL_AUDITOR are not among them —
   two of three tenants cannot appoint an external auditor or a CISO at all.

   It also silently narrows every grant migration this programme has shipped:
   they resolve their target by role_name, which matches the organisation-1 row,
   so those grants exist for tenant 1 and for no other tenant. V330 got this
   right only by accident of being newest (FRAUD_INVESTIGATOR was authored with
   organisation_id NULL); the 46 inherited from the Python bootstrap are the bug.

   ── WHY A FLIP IS THE WHOLE FIX, AND WHY IT IS SAFE ────────────────────────
   The rule this migration enforces is not a new one — the schema already
   declares it. `is_system_role` marks a platform-shipped persona, `deleteRole`
   refuses to retire one ("System roles are templates and cannot be deleted"),
   and the UI labels null-org roles "Global roles are read-only here". So the
   intended model is exactly "system personas are global templates; customer-
   defined roles are tenant-scoped" — the 46 rows simply contradict it.

   Safety, measured rather than assumed:

     * UQ_role_name is (organisation_id, role_name) and ZERO role names appear
       under more than one organisation, so no flip can collide. Checked again
       below, before the UPDATE, and the migration refuses to run if that stops
       being true.
     * The UPDATE does not touch `id`, so every `iam.user_roles` and
       `iam.role_permissions` row survives untouched. Nothing is re-granted and
       no assignment is orphaned.
     * Every native query that joins `iam.roles` joins on `user_roles.role_id`,
       never on organisation_id — verified across the whole main source tree.
     * Only non-system roles can ever be soft-deleted (deleteRole rejects
       templates), and non-system roles do not move here, so
       `findRetiredRoleId(org, name)` — which matches organisation_id = :org and
       could never match NULL — keeps working for every row that can reach it.

   ── WHAT SHIPS ALONGSIDE (code, same slice) ────────────────────────────────
   Making a role global makes it shared, and a shared role must not be editable
   from inside one tenant: rewriting CISO's matrix from organisation 1 would
   change what CISO means for organisations 2 and 3. `mustOwnRole` already
   refused all writes to global roles — for everyone, including PLATFORM_ADMIN,
   which is why the permission-matrix editor has been dead for the 12 already-
   global roles (live-probed: PUT returns 400 for BCM_MANAGER, CHANGE_MANAGER,
   COMPLIANCE_OFFICER, DPO, EMERGENCY_RESPONDER, FRAUD_INVESTIGATOR). This slice
   gives PLATFORM_ADMIN the platform-scope path and adds the clone endpoint that
   the refusal message has been recommending since it was written — the same
   "visible is not writable" rule SanctionsListService applies to platform-global
   screening lists.

   ── ASSERTIONS ARE RELATIONAL, NOT ABSOLUTE ────────────────────────────────
   Dev ends with 58 global roles and prod with 56 (prod lacks CHANGE_MANAGER and
   FRAUD_INVESTIGATOR, both of which arrive with undeployed migrations). A
   hardcoded count would fail on prod for a reason that is not a defect, so the
   checks below assert INVARIANTS instead: no system role remains tenant-scoped,
   no non-system role became global, and the roles F2-56 exists to make reachable
   are actually reachable.
   ============================================================================ */

SET NOCOUNT ON;

/* ── 0. Refuse to run if a flip would violate UQ_role_name ─────────────────
   If a name existed both globally and at organisation 1, setting the tenant row
   to NULL would duplicate (NULL, name). Measured at 0 today in both databases;
   this check is what makes "measured at 0" safe to rely on at apply time. */
IF EXISTS (
    SELECT 1
    FROM iam.roles r
    WHERE r.is_system_role = 1
      AND r.organisation_id IS NOT NULL
      AND EXISTS (SELECT 1 FROM iam.roles g
                  WHERE g.organisation_id IS NULL
                    AND g.role_name = r.role_name)
)
BEGIN
    THROW 51334, 'V334: a system role name exists both globally and tenant-scoped. Setting organisation_id to NULL would violate UQ_role_name(organisation_id, role_name). Reconcile the duplicate pair by hand before re-running — do not widen the constraint.', 1;
END

/* ── 1. The flip. Idempotent by predicate: a second run matches nothing. ─── */
UPDATE iam.roles
   SET organisation_id = NULL,
       updated_at      = SYSUTCDATETIME()
 WHERE is_system_role = 1
   AND organisation_id IS NOT NULL;

/* ── 2. Invariant: no system role is tenant-scoped any more. ──────────────── */
IF EXISTS (SELECT 1 FROM iam.roles WHERE is_system_role = 1 AND organisation_id IS NOT NULL)
BEGIN
    THROW 51334, 'V334: system roles remain tenant-scoped after the UPDATE. The platform personas are still unreachable by tenants 2+ — F2-56 is not fixed.', 1;
END

/* ── 3. Anti-vacuity, in the OPPOSITE direction and the one that matters more:
        a customer-defined role must never become global. That would publish one
        tenant's private role to every other tenant, which is strictly worse than
        the bug being fixed here. REVIEW_TEST_ROLE is the live control case. ── */
IF EXISTS (SELECT 1 FROM iam.roles WHERE is_system_role = 0 AND organisation_id IS NULL)
BEGIN
    THROW 51334, 'V334: a NON-system (customer-defined) role is now global, so one tenant''s private role is visible to every tenant. The UPDATE predicate must key on is_system_role = 1 only.', 1;
END

/* ── 4. Purpose check — the consequence F2-56 was filed for. If these four are
        not reachable by every tenant, the migration ran without achieving the
        thing it exists to achieve, and D-8's grant list still collapses to
        ORG_ADMIN alone outside organisation 1. ─────────────────────────────── */
IF (SELECT COUNT(*) FROM iam.roles
     WHERE organisation_id IS NULL
       AND ISNULL(is_deleted, 0) = 0
       AND role_name IN ('ORG_ADMIN', 'GRC_ADMINISTRATOR', 'CISO', 'CYBER_MANAGER')) < 4
BEGIN
    THROW 51334, 'V334: at least one of ORG_ADMIN / GRC_ADMINISTRATOR / CISO / CYBER_MANAGER is still not a global role. These are D-8''s grant targets and ADR-017 D-8 explicitly rejected an outcome where the CISO who runs the integrations is dropped.', 1;
END

/* ── 5. And the two roles whose absence F2-59 named: a tenant that cannot
        appoint an external auditor cannot be audited. ─────────────────────── */
IF (SELECT COUNT(*) FROM iam.roles
     WHERE organisation_id IS NULL
       AND ISNULL(is_deleted, 0) = 0
       AND role_name IN ('EXTERNAL_AUDITOR', 'BOARD_SECRETARY')) < 2
BEGIN
    THROW 51334, 'V334: EXTERNAL_AUDITOR or BOARD_SECRETARY is still tenant-scoped, so 2 of 3 tenants still cannot appoint one (F2-59''s second defect).', 1;
END
