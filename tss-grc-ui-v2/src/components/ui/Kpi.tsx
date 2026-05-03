import { type LucideIcon, TrendingUp, TrendingDown, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';

type KpiTone = 'primary' | 'danger' | 'success' | 'warning' | 'info' | 'accent';

interface KpiProps {
  Icon: LucideIcon;
  label: string;
  value: string | number;
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
   * given route. Pattern matches ServiceNow / MetricStream / OneTrust where
   * every KPI is a drill-down anchor.
   */
  to?: string;
  /**
   * Alternative click handler when navigation isn't appropriate (e.g. a
   * drill that opens a slide-out panel rather than a route).
   */
  onClick?: () => void;
}

const TONES: Record<KpiTone, { bg: string; fg: string; line: string }> = {
  primary: { bg: 'var(--primary-50)', fg: 'var(--primary)',     line: 'var(--primary)' },
  danger:  { bg: 'var(--danger-bg)',  fg: 'var(--danger-fg)',   line: 'var(--danger)' },
  success: { bg: 'var(--success-bg)', fg: 'var(--success-fg)',  line: 'var(--success)' },
  warning: { bg: 'var(--warning-bg)', fg: 'var(--warning-fg)',  line: 'var(--warning)' },
  info:    { bg: 'var(--info-bg)',    fg: 'var(--info-fg)',     line: 'var(--info)' },
  accent:  { bg: 'var(--accent-50)',  fg: 'var(--accent-700)',  line: 'var(--accent-500)' },
};

function autoTrend(spark?: number[]): { dir: 'up' | 'down'; value: string } | undefined {
  if (!spark || spark.length < 2) return undefined;
  const a = spark[0], b = spark[spark.length - 1];
  if (a === 0 && b === 0) return undefined;
  const delta = b - a;
  const pct = a !== 0 ? Math.round((delta / Math.abs(a)) * 100) : (b > 0 ? 100 : -100);
  return { dir: delta >= 0 ? 'up' : 'down', value: (pct >= 0 ? '+' : '') + pct + '%' };
}

export function Kpi({ Icon, label, value, trend, tone = 'primary', spark, to, onClick }: KpiProps) {
  const { bg, fg, line } = TONES[tone];
  const effectiveTrend = trend ?? autoTrend(spark);
  const isInteractive = !!(to || onClick);

  const body = (
    <>
      <div className="kpi-top">
        <div className="kpi-icon-wrap" style={{ background: bg, color: fg }}>
          <Icon size={20} strokeWidth={2} />
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
            <ArrowRight size={14} />
          </span>
        )}
      </div>
      <div className="kpi-value">{value}</div>
      <div className="kpi-label">{label}</div>
      {spark && spark.length >= 2 && <Sparkline values={spark} color={line} />}
    </>
  );

  if (to) {
    return (
      <Link to={to} className="kpi kpi-clickable" style={{ textDecoration: 'none', color: 'inherit', display: 'block' }}>
        {body}
      </Link>
    );
  }
  if (onClick) {
    return (
      <button onClick={onClick} className="kpi kpi-clickable"
        style={{ background: 'inherit', border: 'none', textAlign: 'inherit', padding: 'inherit', font: 'inherit', cursor: 'pointer', color: 'inherit', width: '100%' }}>
        {body}
      </button>
    );
  }
  return <div className="kpi">{body}</div>;
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
