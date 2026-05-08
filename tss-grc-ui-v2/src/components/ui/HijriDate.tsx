import { useI18n } from '../../lib/i18n';
import { formatHijri } from '../../lib/sla';

/**
 * HijriDate — Sprint 6.
 *
 * Drop-in display for a date that needs both Gregorian and Hijri readings.
 * Saudi government deadlines, board minutes, CR-renewal notices, and
 * regulatory circulars all quote Hijri; auditors and CEOs expect to see
 * both at a glance.
 *
 * Three layouts:
 *   inline  — "13 May 2026 · 26 Dhu al-Q. 1447 AH"  (single-line, for tables)
 *   stacked — Gregorian on line 1, Hijri on line 2  (rails, detail pages)
 *   compact — Gregorian visible, Hijri in tooltip   (tight column cells)
 *
 * Direction-aware: Gregorian renders LTR, Hijri renders in the user's
 * current locale so it'll flip RTL when the page is in Arabic.
 */
export function HijriDate({
  iso, layout = 'inline', greg, mono, weight,
}: {
  /** ISO date string. Falsy → renders an em-dash. */
  iso?: string | null;
  layout?: 'inline' | 'stacked' | 'compact';
  /** Optional override of the Gregorian formatter — pass a pre-formatted
   *  string when you want to keep an existing date format. */
  greg?: string;
  /** When true, the Gregorian half uses the mono font (table-friendly). */
  mono?: boolean;
  /** Optional bolding of the Gregorian half. */
  weight?: 400 | 500 | 600 | 700;
}) {
  const { lang } = useI18n();
  if (!iso) return <span style={{ color: 'var(--ink-3)' }}>—</span>;

  const date = new Date(iso);
  const gregLabel = greg ?? date.toLocaleDateString(
    lang === 'ar' ? 'ar-SA' : 'en-GB',
    { day: '2-digit', month: 'short', year: 'numeric' },
  );
  const hijriLabel = formatHijri(date, lang);

  if (layout === 'compact') {
    return (
      <span title={`${lang === 'ar' ? 'هجري' : 'Hijri'}: ${hijriLabel}`}
        className={mono ? 'mono' : undefined}
        style={{ fontWeight: weight }}>
        {gregLabel}
      </span>
    );
  }

  if (layout === 'stacked') {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
        <span className={mono ? 'mono' : undefined} style={{ fontWeight: weight }}>{gregLabel}</span>
        <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>{hijriLabel}</span>
      </div>
    );
  }

  return (
    <span style={{ fontWeight: weight }}>
      <span className={mono ? 'mono' : undefined}>{gregLabel}</span>
      <span style={{ color: 'var(--ink-3)', marginInlineStart: 6, fontSize: '0.92em' }}>
        · {hijriLabel}
      </span>
    </span>
  );
}
