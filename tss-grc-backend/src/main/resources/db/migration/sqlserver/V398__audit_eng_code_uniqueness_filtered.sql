/* ============================================================================================
   V398 — F2-433: audit_eng.engagements code uniqueness must be FILTERED on IS NOT NULL.

   THE DEFECT
   ----------
   V25 declared `CONSTRAINT uk_eng_code UNIQUE (organisation_id, engagement_code)` — a PLAIN
   unique constraint over a NULLABLE column.  SQL Server treats NULLs as EQUAL for uniqueness,
   so that permits at most ONE engagement per organisation with no code.  Both databases already
   hold exactly that row (id=1, org=1, code=NULL), so `POST /api/audit-eng/engagements` omitting
   the code was refused on the FIRST attempt with

       409  "One of the fields you supplied must be unique"   constraint: uk_eng_code

   — a uniqueness complaint about a field the caller never supplied.  A code-less engagement is
   representable by DESIGN: the column is nullable, `AuditEngagementDto.engagementCode` carries
   no validation annotation while its siblings carry `@NotBlank`, and a row already exists.

   WHY IT SURVIVED
   ---------------
   V396 (F2-309) applied exactly this fix to NINE registers, filtered `WHERE <code> IS NOT NULL`,
   for exactly this reason — and one of the nine is `compliance.audit_engagements`, the TWIN of
   this table.  Same column name, different schema.  So a reader grepping `engagement_code` in
   the migrations finds V396's filtered index and concludes the register is covered, and V396's
   own missing-index self-check verifies its coverage over a population of nine that does not
   include the tenth.

   SCOPE — the NULL axis only, deliberately
   ----------------------------------------
   The filter is `WHERE engagement_code IS NOT NULL`, matching V396's nine siblings EXACTLY.
   It is NOT also filtered on `is_deleted = 0`: whether a code becomes reissuable after an
   engagement is deleted is a product question that applies to all ten registers uniformly
   (V249's `uq_esg_m_org_metric_code` shows the platform does sometimes filter on it), and
   answering it in one register would invent a tenth convention.  Filed as an owner decision.

   SAFETY
   ------
   A filtered unique index is STRICTLY MORE PERMISSIVE than the plain constraint it replaces —
   it can only ever reject FEWER rows — so it cannot refuse a row that is storable today.
   Measured in BOTH databases first (F2-183): 1 row each, 0 duplicate (org, code) groups under
   the proposed key, 0 soft-deleted rows, no foreign key references this table.

   The self-checks below are STRUCTURAL, which is what V385 and V396 do, because `has_filter = 1`
   is exactly the fact that distinguishes the fixed state from the broken one.  The BEHAVIOURAL
   proof — that two code-less engagements are now storable through the real endpoint and that a
   duplicate non-null code is still refused — is the live probe, which is stronger because it
   goes through the controller rather than describing the index.
   ============================================================================================ */

/* ── 0. Nothing to do if the table has not been created yet ──────────────────────────────── */
IF OBJECT_ID('audit_eng.engagements') IS NULL
BEGIN
    PRINT 'V398: audit_eng.engagements does not exist — nothing to do.';
END
GO

/* ── 1. Pre-flight — refuse, NAMING the offending rows ───────────────────────────────────
   A bare "duplicate key" sends the reader nowhere, and a failing migration blocks every
   migration after it (F2-309).  Nothing can offend today, because the plain constraint being
   replaced is stricter than the filtered one; this exists so that if a future hand-edit drops
   the constraint and lets duplicates in, THIS file says which rows. */
IF OBJECT_ID('audit_eng.engagements') IS NOT NULL
BEGIN
    DECLARE @offenders NVARCHAR(MAX) = N'';

    SELECT @offenders = @offenders + N'org=' + CAST(organisation_id AS NVARCHAR(20))
                      + N' code=' + engagement_code
                      + N' x' + CAST(COUNT(*) AS NVARCHAR(10)) + N'; '
      FROM audit_eng.engagements
     WHERE engagement_code IS NOT NULL
     GROUP BY organisation_id, engagement_code
    HAVING COUNT(*) > 1;

    IF LEN(@offenders) > 0
    BEGIN
        DECLARE @msg NVARCHAR(MAX) =
            N'V398: audit_eng.engagements holds duplicate (organisation_id, engagement_code) '
          + N'groups, so the filtered unique index cannot be built. Reconcile these rows by '
          + N'hand before re-running — do not widen the index: ' + @offenders;
        THROW 51398, @msg, 1;
    END
END
GO

/* ── 2. Drop the plain UNIQUE constraint ─────────────────────────────────────────────────── */
IF OBJECT_ID('audit_eng.engagements') IS NOT NULL
   AND EXISTS (SELECT 1 FROM sys.indexes
                WHERE name = 'uk_eng_code'
                  AND object_id = OBJECT_ID('audit_eng.engagements'))
    ALTER TABLE audit_eng.engagements DROP CONSTRAINT uk_eng_code;
GO

/* ── 3. Create the filtered unique index ─────────────────────────────────────────────────
   Tenant-scoped, because a globally-unique code index would let one tenant's reference block
   another's (V396's reason).  The name carries the SCHEMA, because the twin register's index is
   `UX_audit_engagement_code_org` on `compliance.audit_engagements` — and the whole reason this
   file exists is that the two registers are easy to mistake for one another. */
IF OBJECT_ID('audit_eng.engagements') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes
                    WHERE name = 'UX_audit_eng_engagement_code_org'
                      AND object_id = OBJECT_ID('audit_eng.engagements'))
    CREATE UNIQUE INDEX UX_audit_eng_engagement_code_org
        ON audit_eng.engagements (organisation_id, engagement_code)
     WHERE engagement_code IS NOT NULL;
GO

/* ── 4. Self-check — the plain constraint is GONE ────────────────────────────────────────── */
IF OBJECT_ID('audit_eng.engagements') IS NOT NULL
   AND EXISTS (SELECT 1 FROM sys.indexes
                WHERE name = 'uk_eng_code'
                  AND object_id = OBJECT_ID('audit_eng.engagements'))
    THROW 51398, 'V398: uk_eng_code still exists. While the plain unfiltered UNIQUE constraint stands, at most ONE engagement per organisation may have no code, whatever else this migration created.', 1;
GO

/* ── 5. Self-check — the replacement exists, is unique, IS FILTERED, and is ENABLED ──────
   `is_disabled` is asserted because a disabled unique index enforces nothing and looks
   identical to a verified one in any listing that only checks the name (F2-299). */
IF OBJECT_ID('audit_eng.engagements') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes
                    WHERE name = 'UX_audit_eng_engagement_code_org'
                      AND object_id = OBJECT_ID('audit_eng.engagements')
                      AND is_unique = 1
                      AND has_filter = 1
                      AND is_disabled = 0)
    THROW 51398, 'V398: UX_audit_eng_engagement_code_org is missing, not unique, not filtered, or disabled. An UNFILTERED unique index over the nullable engagement_code is the defect this migration exists to remove — it would permit only one code-less engagement per organisation.', 1;
GO

/* ── 6. Self-check — the index still covers the right columns, in the right order ─────────
   Tenant first: a key that led with the code would make one tenant's reference block
   another's, which is the multi-tenancy defect a stricter-looking index can hide. */
IF OBJECT_ID('audit_eng.engagements') IS NOT NULL
BEGIN
    DECLARE @keycols NVARCHAR(400) = N'';

    SELECT @keycols = @keycols + c.name + N','
      FROM sys.index_columns ic
      JOIN sys.indexes i ON i.object_id = ic.object_id AND i.index_id = ic.index_id
      JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
     WHERE i.name = 'UX_audit_eng_engagement_code_org'
       AND i.object_id = OBJECT_ID('audit_eng.engagements')
       AND ic.is_included_column = 0
     ORDER BY ic.key_ordinal;

    IF @keycols <> N'organisation_id,engagement_code,'
    BEGIN
        DECLARE @kmsg NVARCHAR(400) =
            N'V398: UX_audit_eng_engagement_code_org is keyed on [' + @keycols
          + N'] rather than [organisation_id,engagement_code,]. Tenant first, or one tenant''s '
          + N'engagement code blocks another tenant''s.';
        THROW 51398, @kmsg, 1;
    END
END
GO

PRINT 'V398 OK: audit_eng.engagements code uniqueness is filtered on IS NOT NULL; a code-less engagement is storable again.';
GO
