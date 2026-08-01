/* ============================================================================
   V344 — W2-0: every organisation is granted every ratified SKU, because the
          entitlement aspect is fail-closed and two of three tenants hold one
          licence row each.

   ADR-013 (W2-0) + ADR-018 (the ratified catalogue). Idempotent: safe to run
   twice (dev applies by hand via pyodbc, Flyway off).

   ── WHY THIS MUST LAND BEFORE ANY @RequireModule ANNOTATION ────────────────
   `LicenseGuardAspect` throws `ModuleDisabledException` when the tenant does
   not hold the module, and it fails closed when there is no tenant in scope.
   Measured 2026-08-01, identically in dev (TSS_GRC) and production
   (TSS_GRC_Deployment) — 22 licence rows across 3 active organisations:

       org 1 (TSS)       20 rows — all 20 legacy codes
       org 2 (ATC)        1 row  — SOCOPS
       org 3 (STC Bank)   1 row  — SOCOPS

   So annotating controllers first would black out two paying tenants on
   everything except security operations. F2-04 sweeps ~2,138 endpoints; if it
   ran before this file, the platform would go dark for them in one commit.

   ── THE ROOT CAUSE, WHICH IS NOT WHAT THE PLAN ROW ASSUMED ─────────────────
   The plan describes this as "nothing creates licence rows on organisation
   creation" (F2-32). Measured: **nothing creates an organisation.**
   `OrganisationController` has `accessible` and `switch/{id}` and no POST;
   `OrganisationRepository` is a bare `JpaRepository` and **no code anywhere
   calls `save` on it** — every one of its 8 consumers reads a name for an
   export header. Organisations 2 and 3 exist because the Python bootstrap put
   them there.

   The actual cause is one line of V97: it seeded licence rows with
   `organisation_id = 1` as a **literal**. Every tenant added afterwards got
   nothing, and nothing was ever going to notice, because the only enforced
   code (`SOCOPS`) was hand-granted to each new org by a later migration. *A
   seed written for one tenant is a provisioning bug that only shows up when
   there is a second tenant.* Recorded as F2-81: there is no tenant-onboarding
   path at all, so this migration is the provisioning mechanism until there is
   one, and `Appendix/scripts/licence_coverage.py` is the standing check.

   ── GRANT-ALL-THEN-TIGHTEN, NOT LICENSE-WHAT-HAS-DATA ─────────────────────
   W2-0 is explicit about the instrument, and the reason is worth restating:
   deriving entitlement from "does this tenant have rows in that module's
   tables" false-negatives on read-only use, on seasonal use (an annual BCM
   exercise), and on a module a customer has bought and not yet populated. Each
   false negative is a 403 for a paying customer. So every organisation is
   granted every ratified SKU, and commercial tightening happens afterwards
   against a signed contract — never inferred from row counts.

   Every row this file creates is tagged in `notes` with the marker below, so a
   later commercial tightening can tell **granted administratively** from
   **sold**. Without that marker the two are indistinguishable the day after
   this runs, and the tightening becomes a guess.

   ── WHAT IS GRANTED, AND THE THREE CONSTRAINTS ADR-018 IMPOSES ────────────
   The 15 ratified SKU codes, and only those:

       RISK · COMPLIANCE · AUDIT_LC · CYBER · SOCOPS · BCM · PDPL · TPRM
       ESG · ETHICS · SAUDI_FS · DATA_AI_GOV · BOARD · ACCESS_GOV · CHANGE

   (i)   The 7 legacy codes that retire into the mandatory Base Platform —
         EVIDENCE, REPORTING, AUDIT_LOG, PLATFORM_ADMIN, CONTROL_LIB, POSTURE,
         AUTOMATION — are **not** backfilled (ADR-018 R4: Base is not
         licensable). Org 1's existing rows for them are left alone, because
         deleting rows a tenant holds is a separate decision with its own
         semantics, and block 5 below FAILS if their population changes.
   (ii)  `NDI` → `DATA_AI_GOV` is the one retirement that needs a grant
         (ADR-018 R3): org 1 held the predecessor and no successor. It falls
         out of "grant all 15 to everyone" rather than needing a special case —
         but block 6 asserts it specifically, because "it happens to be covered"
         is how a required grant goes missing when the general rule changes.
   (iii) `SOCOPS` must not change: 22 live `@RequireModule` annotations depend
         on it and it is the only code all three tenants already hold. The
         NOT EXISTS guard leaves all three rows untouched, notes included, and
         block 7 proves it.

   ⚠ Every comparison against the two table variables carries
   `COLLATE DATABASE_DEFAULT` on the column side. `platform.organisation_licenses`
   stores `module_code` as `Latin1_General_100_CI_AS_SC_UTF8` while the database
   default is `SQL_Latin1_General_CP1_CI_AS`, so a table-variable column — which
   takes the default — raises "cannot resolve the collation conflict" on the join.
   A literal does not, because literals are coercible-default; an explicitly
   collated column is not. This file failed on exactly the five join predicates
   and on none of the literal comparisons.

   Expected: **35 rows created** (org 1 needs 7, orgs 2 and 3 need 14 each),
   22 → 57 total. Numbers are printed, not asserted — a fourth organisation
   would change them, and V339's lesson is that a migration may only assert
   what is true of every database it will ever meet.
   ============================================================================ */

SET NOCOUNT ON;

DECLARE @marker NVARCHAR(200) =
    N'Grandfather grant (W2-0 / ADR-018) — granted administratively, not sold. A commercial tightening starts here.';

/* The 15 ratified SKU codes. Kept as a table variable rather than a literal
   list in the INSERT so the assertion blocks below can join against exactly the
   same set the INSERT used — two hand-maintained lists is how they diverge. */
DECLARE @ratified TABLE (module_code NVARCHAR(40) PRIMARY KEY);
INSERT INTO @ratified (module_code) VALUES
    ('RISK'), ('COMPLIANCE'), ('AUDIT_LC'), ('CYBER'), ('SOCOPS'),
    ('BCM'), ('PDPL'), ('TPRM'), ('ESG'), ('ETHICS'),
    ('SAUDI_FS'), ('DATA_AI_GOV'), ('BOARD'), ('ACCESS_GOV'), ('CHANGE');

DECLARE @baseRetired TABLE (module_code NVARCHAR(40) PRIMARY KEY);
INSERT INTO @baseRetired (module_code) VALUES
    ('EVIDENCE'), ('REPORTING'), ('AUDIT_LOG'), ('PLATFORM_ADMIN'),
    ('CONTROL_LIB'), ('POSTURE'), ('AUTOMATION');

/* ── 0. before-state, captured so the assertions can compare rather than guess */
DECLARE @orgCount      INT = (SELECT COUNT(*) FROM platform.organisations);
DECLARE @ratifiedCount INT = (SELECT COUNT(*) FROM @ratified);
DECLARE @rowsBefore    INT = (SELECT COUNT(*) FROM platform.organisation_licenses);
DECLARE @baseBefore    INT = (SELECT COUNT(*) FROM platform.organisation_licenses l
                              JOIN @baseRetired b ON b.module_code = l.module_code COLLATE DATABASE_DEFAULT);
DECLARE @socopsBefore  INT = (SELECT COUNT(*) FROM platform.organisation_licenses
                              WHERE module_code = 'SOCOPS');
DECLARE @untaggedBefore INT = (SELECT COUNT(*) FROM platform.organisation_licenses
                               WHERE ISNULL(notes, N'') <> @marker);

IF @orgCount = 0
    THROW 51344, 'V344: platform.organisations is empty, so the backfill grants nothing and every "every organisation holds every SKU" check below would pass vacuously. A licence backfill with no tenants is not a no-op, it is an unverified migration.', 1;

IF @ratifiedCount <> 15
    THROW 51344, 'V344: the ratified SKU list does not hold 15 codes. ADR-018 ratified exactly 15 product SKUs plus a non-licensable Base; if that changed, amend SKU_CATALOGUE.md, ADR-018, LicensingService.SELLABLE_MODULES and SkuCatalogueRatchetTest in the same commit — never one without the others.', 1;

/* ── 1. the backfill: every organisation × every ratified SKU ─────────────── */
INSERT INTO platform.organisation_licenses
    (organisation_id, module_code, tier, enabled, valid_from, valid_to, notes, created_at, updated_at)
SELECT o.id, r.module_code, 'ENTERPRISE', 1, CAST(SYSUTCDATETIME() AS DATE), NULL,
       @marker, SYSUTCDATETIME(), SYSUTCDATETIME()
FROM platform.organisations o
CROSS JOIN @ratified r
WHERE NOT EXISTS (
    SELECT 1 FROM platform.organisation_licenses l
    WHERE l.organisation_id = o.id AND l.module_code COLLATE DATABASE_DEFAULT = r.module_code);

DECLARE @created INT = @@ROWCOUNT;

/* ── 2. every organisation now holds every ratified SKU ───────────────────── */
DECLARE @gaps INT = (
    SELECT COUNT(*) FROM platform.organisations o
    CROSS JOIN @ratified r
    WHERE NOT EXISTS (SELECT 1 FROM platform.organisation_licenses l
                      WHERE l.organisation_id = o.id AND l.module_code COLLATE DATABASE_DEFAULT = r.module_code));

IF @gaps > 0
    THROW 51344, 'V344: an (organisation, ratified SKU) pair still has no licence row after the backfill. LicenseGuardAspect is fail-closed, so every gap is a 403 for a tenant that has bought the platform. The INSERT ran but did not cover the cross product.', 1;

/* ── 3. and every one of those rows is usable — enabled, and in date ───────── */
DECLARE @unusable INT = (
    SELECT COUNT(*) FROM platform.organisation_licenses l
    JOIN @ratified r ON r.module_code = l.module_code COLLATE DATABASE_DEFAULT
    WHERE ISNULL(l.enabled, 0) = 0
       OR (l.valid_from IS NOT NULL AND l.valid_from > CAST(SYSUTCDATETIME() AS DATE))
       OR (l.valid_to   IS NOT NULL AND l.valid_to   < CAST(SYSUTCDATETIME() AS DATE)));

IF @unusable > 0
    THROW 51344, 'V344: a ratified-SKU licence row exists but is disabled or outside its validity window, so enabledModulesFor() will skip it and the aspect will still refuse. A row is not an entitlement — check enabled, valid_from and valid_to, which is exactly the filter LicensingService applies.', 1;

/* ── 4. nothing pre-existing was touched. The count of rows NOT carrying this
        migration's marker must be exactly what it was before it ran ───────── */
DECLARE @untaggedAfter INT = (SELECT COUNT(*) FROM platform.organisation_licenses
                              WHERE ISNULL(notes, N'') <> @marker);

IF @untaggedAfter <> @untaggedBefore
    THROW 51344, 'V344: the number of licence rows NOT tagged as a grandfather grant changed. This migration may only INSERT; if it edited or replaced an existing row it has overwritten a commercial fact (a tier, a validity window, or the note explaining why a tenant holds a module).', 1;

/* ── 5. the 7 Base-retired codes were NOT granted to anyone new ───────────── */
DECLARE @baseAfter INT = (SELECT COUNT(*) FROM platform.organisation_licenses l
                          JOIN @baseRetired b ON b.module_code = l.module_code COLLATE DATABASE_DEFAULT);

IF @baseAfter <> @baseBefore
    THROW 51344, 'V344: a licence row was created for a code ADR-018 R4 retires into the mandatory Base Platform (EVIDENCE / REPORTING / AUDIT_LOG / PLATFORM_ADMIN / CONTROL_LIB / POSTURE / AUTOMATION). Base is not licensable — granting it re-creates the pricing error F2-35 found, one migration after the catalogue that fixed it.', 1;

/* ── 6. ADR-018 R3: NDI retires to DATA_AI_GOV, so every NDI holder must hold
        the successor. Asserted specifically rather than left to fall out of the
        general rule, because a required grant that is only implied disappears
        silently the next time the general rule is edited ───────────────────── */
IF EXISTS (
    SELECT 1 FROM platform.organisation_licenses nd
    WHERE nd.module_code = 'NDI'
      AND NOT EXISTS (SELECT 1 FROM platform.organisation_licenses su
                      WHERE su.organisation_id = nd.organisation_id
                        AND su.module_code = 'DATA_AI_GOV'))
    THROW 51344, 'V344: an organisation holds the retired code NDI without its successor DATA_AI_GOV. ADR-018 R3 requires the successor to be granted to every holder before a code is retired — otherwise the tenant loses 7,717 LoC of National Data Index capability the day the code stops being honoured.', 1;

/* ── 7. SOCOPS is untouched: same row count, and the three original notes
        survive (22 live @RequireModule annotations depend on this code) ────── */
DECLARE @socopsAfter INT = (SELECT COUNT(*) FROM platform.organisation_licenses
                            WHERE module_code = 'SOCOPS');
DECLARE @socopsOverwritten INT = (SELECT COUNT(*) FROM platform.organisation_licenses
                                  WHERE module_code = 'SOCOPS' AND ISNULL(notes, N'') = @marker);

IF @socopsAfter <> @socopsBefore OR @socopsOverwritten > 0
    THROW 51344, 'V344: SOCOPS rows were added or their notes replaced. SOCOPS is the only code the platform actually enforces (22 @RequireModule sites) and the only one all three tenants already hold; it is the control group for this whole change and must come through untouched.', 1;

/* ── 8. anti-vacuity in the other direction: the backfill must have DONE
        something the first time it ran. A second pass legitimately creates 0
        rows, so this checks coverage rather than @created ───────────────────── */
DECLARE @tagged INT = (SELECT COUNT(*) FROM platform.organisation_licenses
                       WHERE ISNULL(notes, N'') = @marker);

IF @tagged = 0
    THROW 51344, 'V344: no row in platform.organisation_licenses carries the grandfather marker, so either the INSERT matched nothing or the marker was changed after the fact. Without the marker a later commercial tightening cannot distinguish granted-administratively from sold, and the "then-tighten" half of grant-all-then-tighten becomes a guess.', 1;

DECLARE @rowsAfter INT = (SELECT COUNT(*) FROM platform.organisation_licenses);

PRINT CONCAT('V344 OK — ', @orgCount, ' organisation(s) x 15 ratified SKUs. Rows ',
             @rowsBefore, ' -> ', @rowsAfter,
             ' (', @created, ' created this pass, ', @tagged, ' grandfather-tagged in total). ',
             'Base-retired rows unchanged at ', @baseAfter, '; SOCOPS unchanged at ', @socopsAfter,
             '; pre-existing rows untouched (', @untaggedAfter, ' untagged, was ', @untaggedBefore, ').');
