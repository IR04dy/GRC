import { useEffect, useRef, useState } from 'react';
import { type LucideIcon, TrendingUp, TrendingDown, Check } from 'lucide-react';
import { DirectionalArrow } from '../rtl/DirectionalArrow';
import { Link } from 'react-router-dom';

type KpiTone = 'primary' | 'danger' | 'success' | 'warning' | 'info' | 'accent' | 'neutral';

interface KpiProps {
  Icon: LucideIcon;
  label: string;
  /**
   * The metric value. `null` / `undefined` is treated as an
   * unavailable/uninitialised metric (NOT a genuine zero) and renders the
   * `.empty-inline-compact` em-dash treatment with `emptyHint` as a tooltip,
   * so "not yet measured" never reads as a real "0" (UX-008).
   */
  value: string | number | null | undefined;
  /**
   * Tooltip shown on the em-dash when `value` is null/undefined — must be a
   * pre-translated string (e.g. "Not yet computed" / "لم يُحتسب بعد").
   */
  emptyHint?: string;
  /**
   * When the value is a genuine, meaningful zero (e.g. 0 non-compliant
   * items = clean), set this to render a subtle confirming ✓ cue beside the
   * number so a good zero is distinguishable from no-data. Pair with
   * `tone="success"` for the green value colour.
   */
  zeroConfirm?: boolean;
  trend?: { dir: 'up' | 'down'; value: string };
  tone?: KpiTone;
  /**
   * Optional series of trailing values for an inline sparkline (most-recent
   * value last). When provided, the % delta vs. the first sample is rendered
   * as the trend chip if `trend` is omitted.
   */
  spark?: number[];
  /**
   * When supplied, the tile becomes a clickable handle that navigates to the
   * given route. Pattern matches the industry pattern / industry-standard tooling where
   * every KPI is a drill-down anchor.
   */
  to?: string;
  /**
   * Alternative click handler when navigation isn't appropriate (e.g. a
   * drill that opens a slide-out panel rather than a route).
   */
  onClick?: () => void;
  /**
   * Selected/active state. When a tile drives an in-page filter and that
   * filter is currently applied, set true to render the standard "selected"
   * ring (`.kpi-active`) so the active stat reads as the chosen facet —
   * the market-leader pattern where the KPI strip doubles as the filter bar.
   */
  active?: boolean;
}

// Icon-chip palette. `bg` is the tinted square behind the icon glyph.
// Using the soft semantic -bg tokens gives the icon a subtle marker
// presence without the chip dominating the tile — the VALUE should be
// the focal point, not the icon.
const TONES: Record<KpiTone, { bg: string; fg: string; line: string }> = {
  primary: { bg: 'var(--primary-50)', fg: 'var(--primary-700)', line: 'var(--primary)' },
  danger:  { bg: 'var(--danger-bg)',  fg: 'var(--danger-fg)',   line: 'var(--danger)'  },
  success: { bg: 'var(--success-bg)', fg: 'var(--success-fg)',  line: 'var(--success)' },
  warning: { bg: 'var(--warning-bg)', fg: 'var(--warning-fg)',  line: 'var(--warning)' },
  info:    { bg: 'var(--info-bg)',    fg: 'var(--info-fg)',     line: 'var(--info)'    },
  accent:  { bg: 'var(--accent-50)',  fg: 'var(--accent-700)',  line: 'var(--accent-500)' },
  neutral: { bg: 'var(--surface-2, var(--surface))', fg: 'var(--ink-2)', line: 'var(--ink-4)' },
};

function autoTrend(spark?: number[]): { dir: 'up' | 'down'; value: string } | undefined {
  if (!spark || spark.length < 2) return undefined;
  const a = spark[0], b = spark[spark.length - 1];
  if (a === 0 && b === 0) return undefined;
  const delta = b - a;
  const pct = a !== 0 ? Math.round((delta / Math.abs(a)) * 100) : (b > 0 ? 100 : -100);
  return { dir: delta >= 0 ? 'up' : 'down', value: (pct >= 0 ? '+' : '') + pct + '%' };
}

export function Kpi({ Icon, label, value, emptyHint, zeroConfirm, trend, tone = 'primary', spark, to, onClick, active }: KpiProps) {
  const { bg, fg, line } = TONES[tone];
  const effectiveTrend = trend ?? autoTrend(spark);
  const isInteractive = !!(to || onClick);

  // Unavailable/uninitialised metric vs genuine zero (UX-008). A null/undefined
  // value is "not measured" and gets the existing .empty-inline-compact em-dash
  // (+ tooltip); a real numeric 0 with zeroConfirm gets a subtle ✓ confirming cue.
  const isNoData = value == null;
  const isGoodZero = zeroConfirm && value === 0;

  // Flash-on-change: when `value` prop changes, briefly highlight the tile so
  // the user notices a live update arrived. First render is suppressed so the
  // dashboard doesn't flicker on initial mount.
  const previousValue = useRef(value);
  const isFirstRender = useRef(true);
  const [valueDelta, setValueDelta] = useState<'up' | 'down' | null>(null);
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      previousValue.current = value;
      return;
    }
    if (previousValue.current !== value) {
      const prev = Number(previousValue.current);
      const curr = Number(value);
      const dir: 'up' | 'down' | null = !Number.isNaN(prev) && !Number.isNaN(curr)
        ? (curr > prev ? 'up' : curr < prev ? 'down' : null)
        : null;
      setValueDelta(dir ?? 'up');
      const t = setTimeout(() => setValueDelta(null), 1400);
      previousValue.current = value;
      return () => clearTimeout(t);
    }
  }, [value]);

  const flashClass = valueDelta === 'up' ? 'kpi-flash-up'
                    : valueDelta === 'down' ? 'kpi-flash-down'
                    : '';

  const body = (
    <>
      <div className="kpi-top">
        <div className="kpi-icon-wrap" style={{ background: bg, color: fg }}>
          {/* Icon sized to sit comfortably in the 36px chip (was 20 in 40 →
              cramped). 18 / 36 = 50% — the textbook glyph-in-chip ratio. */}
          <Icon size={18} strokeWidth={2} />
        </div>
        {effectiveTrend && (
          <div className={`kpi-trend ${effectiveTrend.dir}`}>
            {effectiveTrend.dir === 'up' ? <TrendingUp size={11} /> : <TrendingDown size={11} />}
            {effectiveTrend.value}
          </div>
        )}
        {isInteractive && (
          <span aria-hidden style={{
            marginInlineStart: 'auto',
            color: 'var(--ink-4)',
            display: 'inline-flex',
            transition: 'transform var(--dur-fast) var(--ease-standard), color var(--dur-fast)',
          }} className="kpi-arrow">
            <DirectionalArrow size={14} />
          </span>
        )}
      </div>
      {/* title= exposes the full string on hover so an ellipsised long
       * value (e.g. "SAR 12.5M" rendered from "SAR 12,480,000") stays
       * inspectable. Browsers don't fire title on touch, but the
       * underlying compact formatter (lib/format.ts) already produces a
       * mostly-fits value, so ellipsis is rare and the tooltip is the
       * desktop-only fallback. */}
      {/* UX-002: label first (eyebrow, --ink-2) then the number, so the
          reading order is label → value rather than number → caption. */}
      <div className="kpi-label">{label}</div>
      {isNoData ? (
        <div className="kpi-value empty-inline-compact" title={emptyHint}
          style={{ color: 'var(--ink-4)' }} aria-label={emptyHint}>—</div>
      ) : (
        <div className="kpi-value" title={String(value)}>
          {isGoodZero && (
            <Check size={16} strokeWidth={3} aria-hidden
              style={{ verticalAlign: 'middle', marginInlineEnd: 4, color: 'var(--success-fg)' }} />
          )}
          {value}
        </div>
      )}
      {!isNoData && spark && spark.length >= 2 && <Sparkline values={spark} color={line} />}
    </>
  );

  // Wrap classNames with the flash class so the kpi tile briefly highlights.
  // For non-primary tones, add .kpi-tone-{tone} so the VALUE colour reflects
  // the semantic palette (a "1 مرفوضة" tile renders the number in danger-fg,
  // not in ink-1, so the count carries the meaning on its own).
  const toneClass = tone !== 'primary' ? `kpi-tone-${tone}` : '';
  const activeClass = active ? 'kpi-active' : '';
  const baseClass = ['kpi', flashClass, toneClass, activeClass].filter(Boolean).join(' ');
  const linkClass = baseClass + ' kpi-clickable';

  if (to) {
    return (
      <Link to={to} className={linkClass} style={{ textDecoration: 'none', color: 'inherit', display: 'block' }}>
        {body}
      </Link>
    );
  }
  if (onClick) {
    return (
      <button onClick={onClick} className={linkClass}
        style={{ textAlign: 'inherit', font: 'inherit', cursor: 'pointer', color: 'inherit', width: '100%', display: 'block' }}>
        {body}
      </button>
    );
  }
  return <div className={baseClass}>{body}</div>;
}

/** Tiny inline sparkline — pure SVG, no library. Rendered at 100% width × 28px. */
function Sparkline({ values, color }: { values: number[]; color: string }) {
  const w = 100, h = 28, pad = 2;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const stepX = (w - pad * 2) / (values.length - 1);
  const pts = values.map((v, i) => {
    const x = pad + i * stepX;
    const y = h - pad - ((v - min) / range) * (h - pad * 2);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  const pathLine = `M ${pts.join(' L ')}`;
  const pathArea = `${pathLine} L ${(w - pad).toFixed(1)},${h - pad} L ${pad},${h - pad} Z`;
  const lastIdx = values.length - 1;
  const lastX = pad + lastIdx * stepX;
  const lastY = h - pad - ((values[lastIdx] - min) / range) * (h - pad * 2);
  return (
    <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" style={{ width: '100%', height: 28, marginTop: 6, display: 'block' }} aria-hidden>
      <path d={pathArea} fill={color} fillOpacity={0.12} />
      <path d={pathLine} fill="none" stroke={color} strokeWidth={1.4} strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={lastX} cy={lastY} r={2.2} fill={color} />
    </svg>
  );
}
