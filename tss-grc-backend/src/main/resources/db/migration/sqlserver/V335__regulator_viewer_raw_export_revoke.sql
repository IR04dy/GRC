/* ============================================================================
   V335 — an external supervisory authority held a permanent, self-service right
          to extract eleven registers. It keeps one: the governed package.

   F2-59 / ADR-017 D-10b(b). Idempotent: safe to run twice (dev applies by hand).

   ── WHY THIS IS A DEFECT, NOT A CONVENIENCE ────────────────────────────────
   `REGULATOR_VIEWER` holds ELEVEN `*:EXPORT` permissions, identically in dev and
   prod: BCM, CHANGE, COMPLIANCE, CYBER, INCIDENT, NDI, REGULATORY, REPORTING,
   RISK, SUST, WORKFLOW. Each one extracts a whole register as a file.

   Saudi supervisory practice is requisition-driven. SAMA, CMA and NCA inspections
   create an obligation to *furnish on request* — through a coordinator, minuted,
   and scoped to the requisition (Banking Control Law art. 18; Capital Market Law
   inspection powers; NCA ECC assessment). None of them creates a standing seat
   inside the supervised entity's GRC system, and no comparator ships one:
   ServiceNow, Archer and MetricStream all furnish through a governed disclosure
   package or a time-boxed scoped grant.

   Under PDPL it is worse than untidy. Several of those registers carry personal
   data — ethics cases, DSRs, roster seats — so a standing bulk-extract right held
   by an external party, with no per-disclosure record of lawful basis, is a
   disclosure the controller cannot account for.

   ── WHAT SURVIVES, AND WHY THAT IS THE POINT ──────────────────────────────
   `REGULATORY:EXPORT` is NOT revoked. V333/F2-60 re-homed `regulator-package.zip`
   onto exactly that permission, so it is the governed channel: one assembled
   package, audited by `ExportAuditInterceptor`, rather than eleven raw registers.
   **Revoking the raw ten while keeping the governed one is the whole change** —
   the authority keeps a lawful way to be furnished and loses the self-service
   bypass around it. This is also why the order mattered: F2-60 had to land first,
   because before it the package was reachable only via COMPLIANCE/REPORTING
   EXPORT, and a blanket revoke would have removed the compliant path too.

   All 13 `*:READ` grants also survive. Reading a register on screen during an
   inspection is the normal supervisory posture; extracting all of them as files
   without a requisition is not. That distinction is F2-27's ratified premise —
   "the right to view one record does not confer the right to extract the whole
   register as a file" — applied to the persona it most obviously governs.

   ── COLLATERAL: MEASURED AT ZERO, BOTH DATABASES ──────────────────────────
   `REGULATOR_VIEWER` has **0 holders** in dev and prod. This is the exact inverse
   of ADR-017 D-10a, where measurement is what STOPPED a revocation: there the
   guard was a disjunction over two of the platform's most-used permissions, so
   revoking would have broken the risk register for 24 roles. Here nothing is
   attached, so the change is preventive and free. **Measure before revoking, every
   time — the same measurement forbade one revocation and licenses this one.**

   ➕ Note for F2-61(c), deliberately NOT actioned here: this role also holds
   `WORKFLOW:WRITE`. An external supervisory authority that can create workflow
   submissions is its own over-grant — but `WORKFLOW:WRITE` is held by 55 of 58
   roles, so it is a breadth problem across the whole grant table rather than a
   property of this persona, and fixing it here would pre-empt F2-61's amendment
   in a slice whose stated subject is exports. Left, named, and scoped to the row
   that owns it.
   ============================================================================ */

SET NOCOUNT ON;

DECLARE @role BIGINT = (SELECT id FROM iam.roles WHERE role_name = 'REGULATOR_VIEWER');

IF @role IS NULL
BEGIN
    THROW 51335, 'V335: REGULATOR_VIEWER does not exist. It is a platform system persona; if it is genuinely absent this migration is running against an unexpected schema — stop and check rather than creating it here.', 1;
END

/* The set is expressed as a RULE, not a list: every EXPORT this role holds except
   the governed package. That is better than enumerating the ten in three ways —
   it says the intent directly ("keep only the governed channel"), it cannot drift
   out of sync with itself across the checks below, and a module catalogued and
   granted to this role after today is covered automatically instead of silently
   slipping through.

   ⚠ It also sidesteps a collation trap that broke the first two attempts at this
   migration, and the received wisdom about that trap is WRONG for this schema.
   The note in the house landmines says "mixed Latin1-vs-UTF8 → COLLATE
   DATABASE_DEFAULT". Measured here: the database default is
   SQL_Latin1_General_CP1_CI_AS while iam.permissions.module and
   iam.roles.role_name are explicitly Latin1_General_100_CI_AS_SC_UTF8. So
   DATABASE_DEFAULT resolves to the WRONG collation, and a table variable joined
   on text fails either way with "Cannot resolve the collation conflict". A bare
   literal compared against a column takes that column's collation, so the
   predicate below has no conflict to resolve. Capture ids (BIGINT) when a set has
   to be remembered across statements — never text. */

/* Remember WHICH permissions are being revoked, by id, so check 4 can prove the
   DELETE stayed scoped to this one role. Ids carry no collation. */
DECLARE @revoked TABLE (permission_id BIGINT PRIMARY KEY);

INSERT INTO @revoked (permission_id)
SELECT p.id
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE rp.role_id = @role
   AND p.action = 'EXPORT'
   AND p.module <> 'REGULATORY';

/* ── the revocation. Idempotent: a second run selects and deletes nothing. ── */
DELETE rp
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON p.id = rp.permission_id
 WHERE rp.role_id = @role
   AND p.action = 'EXPORT'
   AND p.module <> 'REGULATORY';

/* ── 1. The governed channel must still be reachable by this role. ────────── */
IF NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE rp.role_id = @role AND p.module = 'REGULATORY' AND p.action = 'EXPORT')
BEGIN
    THROW 51335, 'V335: REGULATOR_VIEWER no longer holds REGULATORY:EXPORT, so it cannot receive even the governed regulator package. That makes the entity unable to furnish through the compliant path — the inverse finding ADR-017 D-10b warned about.', 1;
END

/* ── 2. Exactly one EXPORT should remain. More means the revoke was partial;
        fewer is impossible given check 1, but assert the shape, not the trend. ─ */
DECLARE @remaining INT = (
    SELECT COUNT(*) FROM iam.role_permissions rp
    JOIN iam.permissions p ON p.id = rp.permission_id
    WHERE rp.role_id = @role AND p.action = 'EXPORT');

IF @remaining <> 1
BEGIN
    THROW 51335, 'V335: REGULATOR_VIEWER holds a number of EXPORT permissions other than the single governed one. Re-read the revoke list against iam.permissions — a module catalogued after this migration was written would slip through.', 1;
END

/* ── 3. Anti-vacuity: the role must still be able to DO its job. Revoking the
        exports must not have emptied it — a supervisory authority that cannot
        read the registers on screen is a different defect, manufactured by the
        fix for this one. ─────────────────────────────────────────────────────── */
IF (SELECT COUNT(*) FROM iam.role_permissions WHERE role_id = @role) < 10
BEGIN
    THROW 51335, 'V335: REGULATOR_VIEWER is left with fewer than 10 grants. The intent was to remove ten EXPORTs and keep every READ; a role stripped this far cannot conduct an inspection at all.', 1;
END

/* ── 4. Anti-vacuity, opposite direction and the one that matters more: this
        DELETE is scoped to one role, so no other role may have lost anything.
        If any revoked permission is now held by nobody, the scoping failed. ── */
IF EXISTS (
    SELECT 1 FROM @revoked v
    WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions rp WHERE rp.permission_id = v.permission_id))
BEGIN
    THROW 51335, 'V335: one of the revoked EXPORT permissions is now held by NO role at all. The DELETE was meant to be scoped to REGULATOR_VIEWER; a permission reaching zero holders means it also stripped the personas whose job those exports are, which is a worse outcome than the over-grant.', 1;
END
