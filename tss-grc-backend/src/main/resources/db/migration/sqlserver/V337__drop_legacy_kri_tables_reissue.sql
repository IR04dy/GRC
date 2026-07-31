/* ============================================================================
   V337 — three legacy tables the platform decided to drop in V247, re-issued
          above the Flyway baseline so the decision can actually take effect.

   F2-65(a). Idempotent: safe to run twice.

   ── WHY V247 NEVER RAN, AND NEVER WILL ─────────────────────────────────────
   `V247__drop_legacy_kri_tables.sql` drops `risk.kri_definitions`,
   `risk.kri_readings` and `risk.kri_breach_events` — the Sprint-2 stubs
   superseded by the canonical singular tables (`kri_definition` 21 rows,
   `kri_measurement` 155, `kri_breach` 32), as `module/risk/kri/package-info.java`
   records. It has never executed anywhere:

     * dev never runs Flyway (Python-bootstrapped schema), and the three tables
       were removed here by hand, so dev is already in the intended state; and
     * prod is baselined at `FLYWAY_BASELINE_VERSION = 292`, so every migration at
       or below 292 is recorded as already applied whether it ran or not. 247 < 292.

   So the tables survive in production only, created **2026-07-11 17:05** — 43
   minutes after the 16:22 dev-dump restore, by an out-of-band script rather than
   by any migration. `Appendix/scripts/schema_drift.py` found them by comparing
   each never-running migration's stated intent against the schema that exists;
   after V336 they were the only contradiction it still reported.

   ── WHY THIS IS COMPLETING A DECISION, NOT MAKING ONE ─────────────────────
   This migration deliberately does the *narrow* half of F2-65. V247 already
   decided these three tables should go; they hold **0 rows**; and dev has been
   running without them since. Re-issuing that drop above the baseline finishes
   work the platform had already committed to.

   The other ~20 legacy table families F2-65 identified are **NOT** touched here,
   and that is a considered engineering position rather than caution: several hold
   real rows (`compliance.evidence_items` 83, `privacy.consents` 36,
   `reporting.report_definitions` 20), they shipped into production inside the
   2026-07-11 restore, and best practice for legacy data is evidence-based
   retirement with a retention decision per family. Dropping evidence records to
   tidy an inventory is precisely the irreversible tidying a GRC platform exists
   to prevent.

   ── THE SAFETY PRECONDITION IS ENFORCED, NOT ASSUMED ──────────────────────
   "They hold 0 rows" was true when measured. A migration that drops tables must
   not rely on a measurement taken hours earlier, so the check below is executed
   at apply time and **THROWs rather than dropping** if any of the three has
   acquired a row. A destructive migration should be incapable of destroying
   anything unexpected.
   ============================================================================ */

SET NOCOUNT ON;

/* ── 0. Refuse to drop anything that is not empty. ─────────────────────────
   Row counts come from sys.partitions rather than COUNT(*) so this works even if
   a table is absent (the branch simply finds nothing). */
DECLARE @nonempty NVARCHAR(500) = NULL;

SELECT @nonempty = COALESCE(@nonempty + N', ', N'') + s.name + N'.' + t.name
                 + N' (' + CAST(ISNULL(p.rows, 0) AS NVARCHAR(20)) + N' rows)'
  FROM sys.tables t
  JOIN sys.schemas s ON s.schema_id = t.schema_id
  LEFT JOIN (SELECT object_id, SUM(rows) AS rows
               FROM sys.partitions
              WHERE index_id IN (0, 1)
              GROUP BY object_id) p ON p.object_id = t.object_id
 WHERE s.name = 'risk'
   AND t.name IN ('kri_definitions', 'kri_readings', 'kri_breach_events')
   AND ISNULL(p.rows, 0) > 0;

IF @nonempty IS NOT NULL
BEGIN
    DECLARE @msg NVARCHAR(1000) =
        N'V337: refusing to drop — a legacy KRI table is NOT empty: ' + @nonempty
        + N'. V247''s decision assumed these were dead stubs. Rows mean something wrote to them '
        + N'since, so the retirement needs a data decision (and probably a migration into '
        + N'risk.kri_definition / kri_measurement / kri_breach) before the drop.';
    THROW 51337, @msg, 1;
END

/* ── 1. Drop, child-before-parent, each guarded so a second run is a no-op. ── */
IF OBJECT_ID('risk.kri_breach_events', 'U') IS NOT NULL DROP TABLE risk.kri_breach_events;
IF OBJECT_ID('risk.kri_readings',      'U') IS NOT NULL DROP TABLE risk.kri_readings;
IF OBJECT_ID('risk.kri_definitions',   'U') IS NOT NULL DROP TABLE risk.kri_definitions;

/* ── 2. The intended end state. ───────────────────────────────────────────── */
IF EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
           WHERE s.name = 'risk'
             AND t.name IN ('kri_definitions', 'kri_readings', 'kri_breach_events'))
BEGIN
    THROW 51337, 'V337: a legacy plural KRI table still exists after the drops. Check for a foreign key or a schema-bound view holding it.', 1;
END

/* ── 3. Anti-vacuity, and the direction that actually matters: the CANONICAL
        tables must still be there. A migration whose subject is "remove the
        superseded copies" must never be the thing that removed the survivors. ── */
IF (SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
    WHERE s.name = 'risk'
      AND t.name IN ('kri_definition', 'kri_measurement', 'kri_breach')) < 3
BEGIN
    THROW 51337, 'V337: one of the canonical KRI tables (risk.kri_definition / kri_measurement / kri_breach) is missing. Those are the tables the platform actually uses; dropping the legacy plurals must not have touched them.', 1;
END
