import { useMemo, useState } from 'react';

/**
 * FreshnessCalendar — a GitHub-style calendar heatmap.
 *
 * Each day cell is shaded by a daily count (e.g. evidence items collected that
 * day), so collection cadence — and the "cold streak" where collection lapsed
 * — is visible at a glance. The #1 audit failure is stale evidence; this makes
 * it impossible to miss.
 *
 * Pure SVG, RTL-aware (weeks flow toward the inline-start), title-accessible.
 */
export interface CalendarDatum { date: string; value: number; }

export interface FreshnessCalendarProps {
  data: CalendarDatum[];
  /** Number of weeks back from endDate to render. */
  weeks?: number;
  /** Last day, 'YYYY-MM-DD'. Defaults to today. */
  endDate?: string;
  ar?: boolean;
  cell?: number;
  onDayClick?: (date: string, value: number) => void;
}

const GAP = 3;
const TOP = 16;
const LEFTW = 24;

function addDays(d: Date, n: number): Date {
  const r = new Date(d);
  r.setDate(r.getDate() + n);
  return r;
}
function ymd(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

export function FreshnessCalendar({
  data,
  weeks = 26,
  endDate,
  ar = false,
  cell = 13,
  onDayClick,
}: FreshnessCalendarProps) {
  const [hover, setHover] = useState<string | null>(null);
  const step = cell + GAP;

  const { days, maxVal, monthLabels } = useMemo(() => {
    const counts = new Map<string, number>();
    for (const d of data) counts.set(d.date.slice(0, 10), (counts.get(d.date.slice(0, 10)) ?? 0) + d.value);

    const end = endDate ? new Date(endDate + 'T12:00:00') : new Date();
    end.setHours(12, 0, 0, 0);
    // Walk back to the start of the week (Sunday) of (end - (weeks*7-1)).
    let start = addDays(end, -(weeks * 7 - 1));
    start = addDays(start, -start.getDay());

    const list: Array<{ date: string; v: number; col: number; row: number }> = [];
    const months: Array<{ col: number; label: string }> = [];
    let lastMonth = -1;
    let cur = new Date(start);
    let col = 0;
    while (cur <= end) {
      const row = cur.getDay();
      const ds = ymd(cur);
      list.push({ date: ds, v: counts.get(ds) ?? 0, col, row });
      if (cur.getMonth() !== lastMonth && row <= 1) {
        lastMonth = cur.getMonth();
        months.push({ col, label: cur.toLocaleDateString(ar ? 'ar-SA' : 'en-US', { month: 'short' }) });
      }
      if (row === 6) col += 1;
      cur = addDays(cur, 1);
    }
    const max = list.reduce((m, d) => Math.max(m, d.v), 0);
    return { days: list, maxVal: max, monthLabels: months };
  }, [data, weeks, endDate, ar]);

  const cols = Math.max(...days.map((d) => d.col)) + 1;
  const w = LEFTW + cols * step;
  const h = TOP + 7 * step;
  const xFor = (col: number) => (ar ? (LEFTW + (cols - 1 - col) * step) : LEFTW + col * step);

  const fillFor = (v: number) => {
    if (v <= 0) return 'var(--surface-3)';
    const t = maxVal > 0 ? v / maxVal : 1;
    return `color-mix(in oklab, var(--primary) ${Math.round(25 + t * 65)}%, var(--surface-1))`;
  };

  const weekdayLabels = ar ? ['', 'إث', '', 'أر', '', 'سب', ''] : ['', 'Mon', '', 'Wed', '', 'Fri', ''];

  return (
    <div style={{ overflowX: 'auto' }}>
      <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} role="img"
        aria-label={ar ? `تقويم جِدّة الأدلة — ${weeks} أسبوعاً` : `Evidence freshness calendar — ${weeks} weeks`}
        style={{ maxWidth: '100%' }}>
        {/* Month labels */}
        {monthLabels.map((m, i) => (
          <text key={i} x={xFor(m.col)} y={11} textAnchor={ar ? 'end' : 'start'}
            style={{ font: '600 9px var(--font-sans)', fill: 'var(--ink-3)' }}>{m.label}</text>
        ))}
        {/* Weekday labels */}
        {weekdayLabels.map((lbl, r) => lbl && (
          <text key={r} x={ar ? w - 2 : 2} y={TOP + r * step + cell / 2} dominantBaseline="central"
            textAnchor={ar ? 'end' : 'start'}
            style={{ font: '600 8px var(--font-sans)', fill: 'var(--ink-4)' }}>{lbl}</text>
        ))}
        {/* Day cells */}
        {days.map((d) => (
          <rect key={d.date} x={xFor(d.col)} y={TOP + d.row * step} width={cell} height={cell} rx={4}
            fill={fillFor(d.v)} stroke={hover === d.date ? 'var(--primary)' : 'var(--border)'} strokeWidth={hover === d.date ? 1.5 : 0.5}
            onMouseEnter={() => setHover(d.date)} onMouseLeave={() => setHover(null)}
            onClick={() => onDayClick?.(d.date, d.v)}
            style={{ cursor: onDayClick ? 'pointer' : 'default' }}>
            <title>{`${d.date}: ${d.v} ${ar ? 'دليل' : d.v === 1 ? 'item' : 'items'}`}</title>
          </rect>
        ))}
      </svg>
      {/* Legend */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 5, justifyContent: ar ? 'flex-start' : 'flex-end', marginTop: 6, fontSize: 10, color: 'var(--ink-3)' }}>
        <span>{ar ? 'أقل' : 'Less'}</span>
        {[0, 0.25, 0.5, 0.75, 1].map((t, i) => (
          <span key={i} style={{ width: 11, height: 11, borderRadius: 3, display: 'inline-block',
            background: t === 0 ? 'var(--surface-3)' : `color-mix(in oklab, var(--primary) ${Math.round(25 + t * 65)}%, var(--surface-1))` }} />
        ))}
        <span>{ar ? 'أكثر' : 'More'}</span>
      </div>
    </div>
  );
}
