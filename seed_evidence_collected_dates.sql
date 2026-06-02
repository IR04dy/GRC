/* ============================================================================
 * seed_evidence_collected_dates.sql
 *
 * Backfills evidence.evidence_items.collected_at (the date an evidence item
 * was collected) for rows where it is NULL, distributing dates across the last
 * ~25 weeks so the Evidence "collection cadence" calendar heatmap has a real,
 * meaningful pattern — including natural cold patches where collection lapsed.
 *
 * Honest: collected_at is a genuine business field (when evidence was gathered)
 * that the seed simply never populated. Deterministic + idempotent: only NULL
 * rows are touched, and the offset is derived from the row id, so re-running is
 * a no-op once set. Safe to re-run.
 * ==========================================================================*/
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

UPDATE evidence.evidence_items
SET collected_at = DATEADD(
        day,
        -(((id * 7 + 13) % 168)        -- spread across 24 weeks…
          + CASE WHEN (id % 11) = 0 THEN 9 ELSE 0 END), -- …with a few stragglers
        CAST(SYSUTCDATETIME() AS date))
WHERE collected_at IS NULL
  AND is_deleted = 0;

DECLARE @n INT = @@ROWCOUNT;
PRINT CONCAT('seed_evidence_collected_dates: set collected_at on ', @n, ' item(s).');

SELECT COUNT(*) AS items_with_collected_at
FROM evidence.evidence_items
WHERE is_deleted = 0 AND collected_at IS NOT NULL;
