-- =====================================================================
-- V318 — F2-26: make a committee roster seat's platform-user link a first-class,
--               three-state fact, and index the lookup that DF-3 made hot.
-- ---------------------------------------------------------------------
-- WHAT THIS MIGRATION DOES **NOT** DO: it does not link anybody.
--
-- `member_user_id` has existed since V154 and is NULL on every one of the 31 seeded
-- roster rows. The temptation is to close the gap here — match "CISO" to the user whose
-- job title is CISO, match "Mohammed Al-Ghamdi" to a user of that name, and ship a
-- migration that populates the column. That is exactly the defect F2-26 describes,
-- performed by the fixer: name matching is the weakness DF-3 removed from the vote path,
-- and re-introducing it as a *resolution* mechanism undoes that. Worse, a migration runs
-- on every tenant, so it would fabricate governance identities on real customer data --
-- inventing who sits on a board committee. V315 refuses to backfill vote provenance and
-- V316 refuses to backfill approval provenance for the same reason; this refuses to
-- backfill seat identity.
--
-- Linking is therefore a deliberate secretariat act through
-- POST /api/v1/committees/registry/terms/{id}/link-user, audited per seat. The dev/demo
-- persona links live in scripts/seed_committee_member_links.py -- a SEED SCRIPT, which
-- touches only the dev database and never ships inside the artefact.
--
-- ---------------------------------------------------------------------
-- 1. THE THIRD STATE.
--
-- A seat with no platform user is ambiguous today, and the ambiguity is the reason the
-- gap stayed invisible for 31 rows: "nobody has got round to linking this" and "this is
-- an external auditor who will never have a login" are the same NULL. A coverage report
-- built on that NULL either nags forever about seats that are legitimately unlinkable,
-- or it is ignored. So the state becomes explicit:
--
--   member_user_id NOT NULL                         -> LINKED    (can self-vote)
--   member_user_id NULL, no_platform_user = 1       -> EXTERNAL  (declared; not a gap)
--   member_user_id NULL, no_platform_user = 0       -> UNLINKED  (a real gap)
--
-- `no_platform_user_basis_en` is mandatory *at the service* whenever the flag is set --
-- an undocumented "external" claim would be the same unevidenced assertion the flag
-- exists to eliminate. The column is nullable here only because rows that predate the
-- flag have it 0, and a NOT NULL basis would then need a fabricated default.
--
-- Naming: `_en`-suffixed to match the sibling `independence_basis_en` on this table
-- rather than introduce a third naming style. It is a secretariat note, and F2-30 is the
-- backlog item that sweeps the platform's `*_ar` invariant; a consistent pattern is
-- easier for that sweep to find than a bespoke one.
--
-- ---------------------------------------------------------------------
-- 2. THE INDEX, and why it belongs in this migration and not a later "perf" one.
--
-- CommitteeMemberTermRepository.findActiveCommitteeIdsForUser filters
-- (organisation_id, member_user_id, renewal_status). Measured before this migration, the
-- table carries exactly two non-clustered indexes -- IX_member_term_committee
-- (organisation_id, committee_id, renewal_status) and IX_member_term_expiry
-- (organisation_id, term_ends_at) -- so that query has no usable index and scans.
--
-- It costs nothing today because every member_user_id is NULL and the table has 31 rows.
-- It stops being free the moment this feature works: the query drives workflow COMMITTEE
-- recipient fan-out and the actionable-recipient check on every inbox load, i.e. it runs
-- per request once seats are linked. Adding the index in the same migration as the
-- feature keeps the two facts together instead of leaving a regression for someone to
-- rediscover under load.
--
-- Filtered on member_user_id IS NOT NULL: unlinked seats can never match this predicate,
-- and today that excludes 100% of rows, so the index stays small while coverage grows.
-- ---------------------------------------------------------------------
-- Idempotent: safe to run twice (Flyway is OFF in dev, so it is applied by hand twice).
-- =====================================================================

-- ── 1. the third state ──────────────────────────────────────────────

IF COL_LENGTH('admin.committee_member_terms', 'no_platform_user') IS NULL
    ALTER TABLE admin.committee_member_terms
        ADD no_platform_user BIT NOT NULL CONSTRAINT df_cmt_no_platform_user DEFAULT 0;
GO

IF COL_LENGTH('admin.committee_member_terms', 'no_platform_user_basis_en') IS NULL
    ALTER TABLE admin.committee_member_terms
        ADD no_platform_user_basis_en NVARCHAR(300) NULL;
GO

-- Documentation that survives in the database, for the DBA who meets this column first.
-- (The description is built into a variable because EXEC argument values must be literals
-- or variables in T-SQL — a concatenation expression is a syntax error.)
DECLARE @desc NVARCHAR(1000) =
      N'1 = this seat is not expected to ever have a platform login (external auditor, '
    + N'external advisor, a director who attends in person only). Distinguishes a declared '
    + N'non-user from an unlinked seat awaiting secretariat action, so the linkage report '
    + N'can tell a gap from a non-gap. Requires no_platform_user_basis_en. F2-26.';

IF NOT EXISTS (
        SELECT 1 FROM sys.extended_properties
        WHERE major_id = OBJECT_ID('admin.committee_member_terms')
          AND name = 'MS_Description'
          AND minor_id = COLUMNPROPERTY(OBJECT_ID('admin.committee_member_terms'),
                                        'no_platform_user', 'ColumnId'))
    EXEC sys.sp_addextendedproperty
        @name = N'MS_Description',
        @value = @desc,
        @level0type = N'SCHEMA', @level0name = N'admin',
        @level1type = N'TABLE',  @level1name = N'committee_member_terms',
        @level2type = N'COLUMN', @level2name = N'no_platform_user';
GO

-- A declared non-user must not also carry a link, and must carry a basis. Enforced at the
-- service with a named 400/409; enforced here so no other writer (script, import, future
-- endpoint) can create the contradictory state behind the service's back.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'ck_cmt_no_platform_user')
    ALTER TABLE admin.committee_member_terms WITH CHECK
        ADD CONSTRAINT ck_cmt_no_platform_user CHECK (
            no_platform_user = 0
            OR (member_user_id IS NULL AND no_platform_user_basis_en IS NOT NULL)
        );
GO

-- ── 2. the lookup index ─────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_member_term_user')
    CREATE NONCLUSTERED INDEX IX_member_term_user
        ON admin.committee_member_terms (organisation_id, member_user_id, renewal_status)
        INCLUDE (committee_id)
        WHERE member_user_id IS NOT NULL;
GO
