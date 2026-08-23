/* ============================================================================================
   V396 — F2-309: uniqueness for the nine business references F2-306..F2-308 made immutable.

   WHY THIS MIGRATION EXISTS, AND WHY IT COULD NOT HAVE COME FIRST
   ---------------------------------------------------------------------------------------------
   F2-306, F2-307 and F2-308 closed eleven rename vectors: a business reference is now settable
   while absent and immutable once set, so a code an auditor cites cannot be silently replaced.

   Asking F2-267's question of the result — is a DUPLICATE storable? — gave the opposite answer to
   the one F2-267 got. There, 9 of 10 candidates already carried a unique index, so caller-naming
   was a naming question rather than a defect. Here 8 of 9 resolvable registers have NO unique
   index at all.

   That combination is worse than either half:

       immutable + not unique  =  a duplicate is PERMANENT

   Before F2-306 a collision could be renamed away. After it, a second record carrying an existing
   code can never be corrected through the API — the guard that protects the reference is exactly
   what prevents the repair. This migration is therefore the other half of that work, and
   F2-183's rule is satisfied in the right order for once: the WRITERS were closed first (three
   slices of them), so adding the constraint cannot turn today's silently-stored junk into a
   violation.

   MEASURED BEFORE WRITING (F2-183: measure PROD, not just dev)
   ---------------------------------------------------------------------------------------------
   Both TSS_GRC and TSS_GRC_Deployment, all nine registers: 0 duplicate (organisation_id, code)
   groups and 0 NULL codes. So no WITH NOCHECK, nothing to reconcile, and the pre-flight below
   verifies rather than assumes.

   TENANT-SCOPED, AND THAT IS NOT COSMETIC
   ---------------------------------------------------------------------------------------------
   Every index is keyed (organisation_id, code). A GLOBAL unique index would let one tenant's
   reference block another tenant from using the same string — a multi-tenancy defect dressed up as
   strictness (V385's rule).

   FILTERED, EXCEPT WHERE THE COLUMN IS NOT NULL
   ---------------------------------------------------------------------------------------------
   SQL Server treats NULLs as EQUAL for uniqueness, so a plain UNIQUE index over a nullable column
   would permit at most ONE code-less row per tenant — refusing a state the column's own
   nullability allows. Eight of the nine columns are nullable and get WHERE <col> IS NOT NULL.
   compliance.controls.code is NOT NULL, so the filter would be redundant there and is omitted
   deliberately rather than by oversight.
   ============================================================================================ */

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ── 0. pre-flight: name the offenders rather than letting CREATE INDEX say "duplicate key" ──
   A bare failure sends the reader nowhere, and this file blocks every later migration until it is
   resolved (F2-299). */
DECLARE @offenders NVARCHAR(MAX) = N'';

;WITH dups AS (
    SELECT 'ethics.cases'                 AS reg, organisation_id AS org, case_code       AS code, COUNT(*) AS n FROM ethics.cases                 WHERE case_code       IS NOT NULL GROUP BY organisation_id, case_code       HAVING COUNT(*) > 1
    UNION ALL SELECT 'ethics.disclosures',          organisation_id, disclosure_code,  COUNT(*) FROM ethics.disclosures          WHERE disclosure_code IS NOT NULL GROUP BY organisation_id, disclosure_code  HAVING COUNT(*) > 1
    UNION ALL SELECT 'ndi.gap',                     organisation_id, gap_code,         COUNT(*) FROM ndi.gap                     WHERE gap_code        IS NOT NULL GROUP BY organisation_id, gap_code         HAVING COUNT(*) > 1
    UNION ALL SELECT 'ndi.corrective_action',       organisation_id, action_code,      COUNT(*) FROM ndi.corrective_action       WHERE action_code     IS NOT NULL GROUP BY organisation_id, action_code      HAVING COUNT(*) > 1
    UNION ALL SELECT 'compliance.audit_engagements',organisation_id, engagement_code,  COUNT(*) FROM compliance.audit_engagements WHERE engagement_code IS NOT NULL GROUP BY organisation_id, engagement_code  HAVING COUNT(*) > 1
    UNION ALL SELECT 'change_mgmt.change_requests', organisation_id, change_code,      COUNT(*) FROM change_mgmt.change_requests WHERE change_code     IS NOT NULL GROUP BY organisation_id, change_code      HAVING COUNT(*) > 1
    UNION ALL SELECT 'ndi.improvement_plan',        organisation_id, plan_code,        COUNT(*) FROM ndi.improvement_plan        WHERE plan_code       IS NOT NULL GROUP BY organisation_id, plan_code        HAVING COUNT(*) > 1
    UNION ALL SELECT 'ndi.report_definition',       organisation_id, report_code,      COUNT(*) FROM ndi.report_definition       WHERE report_code     IS NOT NULL GROUP BY organisation_id, report_code      HAVING COUNT(*) > 1
    UNION ALL SELECT 'compliance.controls',         organisation_id, code,             COUNT(*) FROM compliance.controls                                           GROUP BY organisation_id, code             HAVING COUNT(*) > 1
)
SELECT @offenders = @offenders + reg + N' org=' + CAST(org AS NVARCHAR(20))
                  + N' code=' + code + N' x' + CAST(n AS NVARCHAR(10)) + N'; '
  FROM dups;

IF LEN(@offenders) > 0
BEGIN
    -- The message is assembled first: RAISERROR arguments must be constants or variables,
    -- never function calls, so NCHAR(10) cannot be passed as a substitution.
    DECLARE @msg NVARCHAR(2048) = N'V396 pre-flight: duplicate (organisation_id, code) rows exist and a unique index cannot be created over them. Resolve these rows first; this file blocks every later migration until you do. Offenders: ' + @offenders;
    RAISERROR(@msg, 16, 1);
END
GO

/* ── 1. the eight nullable columns: filtered, tenant-scoped, idempotent ───────────────────── */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_ethics_case_code_org' AND object_id = OBJECT_ID('ethics.cases'))
    CREATE UNIQUE INDEX UX_ethics_case_code_org ON ethics.cases (organisation_id, case_code) WHERE case_code IS NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_ethics_disclosure_code_org' AND object_id = OBJECT_ID('ethics.disclosures'))
    CREATE UNIQUE INDEX UX_ethics_disclosure_code_org ON ethics.disclosures (organisation_id, disclosure_code) WHERE disclosure_code IS NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_ndi_gap_code_org' AND object_id = OBJECT_ID('ndi.gap'))
    CREATE UNIQUE INDEX UX_ndi_gap_code_org ON ndi.gap (organisation_id, gap_code) WHERE gap_code IS NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_ndi_capa_code_org' AND object_id = OBJECT_ID('ndi.corrective_action'))
    CREATE UNIQUE INDEX UX_ndi_capa_code_org ON ndi.corrective_action (organisation_id, action_code) WHERE action_code IS NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_audit_engagement_code_org' AND object_id = OBJECT_ID('compliance.audit_engagements'))
    CREATE UNIQUE INDEX UX_audit_engagement_code_org ON compliance.audit_engagements (organisation_id, engagement_code) WHERE engagement_code IS NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_change_request_code_org' AND object_id = OBJECT_ID('change_mgmt.change_requests'))
    CREATE UNIQUE INDEX UX_change_request_code_org ON change_mgmt.change_requests (organisation_id, change_code) WHERE change_code IS NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_ndi_improvement_plan_code_org' AND object_id = OBJECT_ID('ndi.improvement_plan'))
    CREATE UNIQUE INDEX UX_ndi_improvement_plan_code_org ON ndi.improvement_plan (organisation_id, plan_code) WHERE plan_code IS NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_ndi_report_code_org' AND object_id = OBJECT_ID('ndi.report_definition'))
    CREATE UNIQUE INDEX UX_ndi_report_code_org ON ndi.report_definition (organisation_id, report_code) WHERE report_code IS NOT NULL;
GO

/* ── 2. the one NOT NULL column: no filter, deliberately ──────────────────────────────────── */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_compliance_control_code_org' AND object_id = OBJECT_ID('compliance.controls'))
    CREATE UNIQUE INDEX UX_compliance_control_code_org ON compliance.controls (organisation_id, code);
GO

/* ── 3. self-check: every index exists, is unique, and leads with organisation_id ──────────── */
DECLARE @missing NVARCHAR(MAX) = N'';

;WITH expected AS (
    SELECT 'ethics.cases'                  AS tbl, 'UX_ethics_case_code_org'          AS ix UNION ALL
    SELECT 'ethics.disclosures',                   'UX_ethics_disclosure_code_org'          UNION ALL
    SELECT 'ndi.gap',                              'UX_ndi_gap_code_org'                    UNION ALL
    SELECT 'ndi.corrective_action',                'UX_ndi_capa_code_org'                   UNION ALL
    SELECT 'compliance.audit_engagements',         'UX_audit_engagement_code_org'           UNION ALL
    SELECT 'change_mgmt.change_requests',          'UX_change_request_code_org'             UNION ALL
    SELECT 'ndi.improvement_plan',                 'UX_ndi_improvement_plan_code_org'       UNION ALL
    SELECT 'ndi.report_definition',                'UX_ndi_report_code_org'                 UNION ALL
    SELECT 'compliance.controls',                  'UX_compliance_control_code_org'
)
SELECT @missing = @missing + e.tbl + N'.' + e.ix + N'; '
  FROM expected e
 WHERE NOT EXISTS (
        SELECT 1
          FROM sys.indexes i
          JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                                   AND ic.key_ordinal = 1
          JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
         WHERE i.name = e.ix
           AND i.object_id = OBJECT_ID(e.tbl)
           AND i.is_unique = 1
           AND c.name = 'organisation_id');

IF LEN(@missing) > 0
BEGIN
    RAISERROR(N'V396 self-check: index missing, not unique, or not keyed on organisation_id first. A globally-unique code index would let one tenant block another. Offending: %s', 16, 1, @missing);
END
GO

/* ── 4. self-check: a duplicate is genuinely refused, proved rather than asserted ───────────
   Probes an existing row inside a rolled-back transaction. On an EMPTY table this verifies
   nothing, and it is deliberately not made to fail there: a fresh install runs this before any
   control exists, and requiring rows would make a migration assert TENANT DATA rather than its own
   writes — the check F2-260 had to delete from V383 to avoid blocking the chain. The hermetic gate
   covers the empty case. */
IF EXISTS (SELECT 1 FROM compliance.controls)
BEGIN
    DECLARE @refused BIT = 0;
    BEGIN TRAN;
    BEGIN TRY
        INSERT INTO compliance.controls (organisation_id, code, name_en, framework, is_deleted, created_at)
        SELECT TOP 1 organisation_id, code, N'V396 duplicate probe', framework, 0, SYSUTCDATETIME()
          FROM compliance.controls;
    END TRY
    BEGIN CATCH
        SET @refused = 1;
    END CATCH
    ROLLBACK TRAN;

    IF @refused = 0
    BEGIN
        RAISERROR(N'V396 self-check: a duplicate (organisation_id, code) was ACCEPTED on compliance.controls. The index exists by name but is not enforcing — check for a disabled or non-trusted index.', 16, 1);
    END
END
GO
