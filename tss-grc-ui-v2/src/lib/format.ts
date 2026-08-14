// ─── Shared number / currency formatters ────────────────────────────────
//
// One place for value-formatting logic so a number renders the same way on
// the dashboard tile, in a table cell, and in the PDF export. Previously
// every page had its own `fmtSar()` and they drifted (some used compact
// notation, some didn't, some used USD currency formatting accidentally).
//
// Rule of thumb:
//   • Use `formatSarCompact(n)` for KPI tiles — auto-compacts large
//     numbers so "SAR 12,480,000" becomes "SAR 12.5M" and the tile no
//     longer overflows. The full value lives in `formatSarFull(n)` for
//     the tile's `title=` hover-disclosure.
//   • Use `formatSarFull(n)` in tables, drawers, and PDFs where the
//     reader expects exact figures.
//   • Use `formatNumberCompact(n)` for non-currency big numbers (row
//     counts, total events, etc.).

/**
 * Compact-notation SAR formatter for KPI tiles.
 *   1,234      → "SAR 1,234"
 *   12,000     → "SAR 12K"
 *   1,500,000  → "SAR 1.5M"
 *   12,480,000 → "SAR 12.5M"
 *   2,400,000,000 → "SAR 2.4B"
 *
 * `Intl.NumberFormat` with `notation: 'compact'` handles the K/M/B/T
 * suffix correctly per locale (English uses K, Arabic uses ألف/مليون).
 */
export function formatSarCompact(amount: number | null | undefined, locale: 'en' | 'ar' = 'en'): string {
  if (amount == null || Number.isNaN(amount)) return '—';
  const bcp = locale === 'ar' ? 'ar-SA' : 'en-US';
  // For small values (<10,000) keep regular notation — "SAR 1K" reads
  // worse than "SAR 1,000" when the reader is comparing to single units.
  if (Math.abs(amount) < 10_000) {
    return new Intl.NumberFormat(bcp, {
      style: 'currency', currency: 'SAR', maximumFractionDigits: 0,
    }).format(amount);
  }
  return new Intl.NumberFormat(bcp, {
    style: 'currency', currency: 'SAR',
    notation: 'compact', compactDisplay: 'short',
    maximumFractionDigits: 1,
  }).format(amount);
}

/**
 * Full-precision SAR formatter — what every prior `fmtSar` was returning.
 * Use in tables, drawers, contracts, exports — anywhere the user
 * legitimately needs to see the exact halala-precision figure.
 */
export function formatSarFull(amount: number | null | undefined, locale: 'en' | 'ar' = 'en'): string {
  if (amount == null || Number.isNaN(amount)) return '—';
  const bcp = locale === 'ar' ? 'ar-SA' : 'en-US';
  return new Intl.NumberFormat(bcp, {
    style: 'currency', currency: 'SAR', maximumFractionDigits: 0,
  }).format(amount);
}

/**
 * Compact non-currency number — for row counts, event volumes, asset
 * counts that can hit 7+ digits and overflow a KPI tile.
 *   154        → "154"
 *   12,400     → "12.4K"
 *   2,400,000  → "2.4M"
 */
export function formatNumberCompact(n: number | null | undefined, locale: 'en' | 'ar' = 'en'): string {
  if (n == null || Number.isNaN(n)) return '—';
  const bcp = locale === 'ar' ? 'ar-SA' : 'en-US';
  if (Math.abs(n) < 10_000) {
    return new Intl.NumberFormat(bcp).format(n);
  }
  return new Intl.NumberFormat(bcp, {
    notation: 'compact', compactDisplay: 'short',
    maximumFractionDigits: 1,
  }).format(n);
}

/**
 * Percentage with one decimal. Returns "—" for null/NaN so the tile
 * doesn't render the literal "NaN%". Caps display at 999.9% (anything
 * higher is almost certainly a divide-by-near-zero artifact).
 */
export function formatPercent(value: number | null | undefined, locale: 'en' | 'ar' = 'en'): string {
  if (value == null || Number.isNaN(value)) return '—';
  const capped = Math.min(999.9, Math.max(-99.9, value));
  const bcp = locale === 'ar' ? 'ar-SA' : 'en-US';
  return new Intl.NumberFormat(bcp, {
    style: 'percent',
    minimumFractionDigits: 0,
    maximumFractionDigits: 1,
  }).format(capped / 100);
}

/**
 * PDPL Article 31 retention period, stored as a whole number of months.
 *
 * ⚠ F2-210: `privacy.processing_activities.retention_period_months` is an INT and
 * the wire carries `retentionPeriodMonths`. The client used to declare a string
 * `retentionPeriod` that the server has never sent, so three surfaces — the ROPA
 * register's Retention column, the detail rail, and the "Completeness check
 * (SDAIA examiner expectations)" panel — read `undefined` on every row. One
 * formatter, read by all of them, so the months-to-label rule cannot drift
 * between the list and the detail view (F2-178's one-predicate shape).
 *
 * Returns an em-dash for absent, which is honest: no row in either database has
 * a null retention today, but a new activity can be created without one and
 * "0 months" would be a different and wrong claim.
 */
export function formatRetentionMonths(
  months: number | null | undefined,
  locale: 'en' | 'ar' = 'en',
): string {
  if (months == null || Number.isNaN(months)) return '—';
  const years = months / 12;
  if (Number.isInteger(years) && years >= 1) {
    return locale === 'ar'
      ? (years === 1 ? 'سنة واحدة' : years === 2 ? 'سنتان' : `${years} سنوات`)
      : `${years} year${years === 1 ? '' : 's'}`;
  }
  return locale === 'ar' ? `${months} شهراً` : `${months} months`;
}
