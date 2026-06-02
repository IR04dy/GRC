import { useState } from 'react';

/**
 * DriftChart — model-monitoring drift over time with a threshold band.
 *
 * Plots a metric's value across observations, with a faint baseline line, a
 * dashed drift threshold, a tinted danger band above it, and markers coloured
 * by severity. Breaches (value past threshold) pulse. The trend-toward-a-line
 * is exactly what a table can't show and what an MRM reviewer expects
 * (watsonx.governance / Fiddler convention).
 *
 * Hand-SVG, index-spaced X (irregular timestamps), title-accessible, RTL-aware.
 */
export interface DriftPoint {
  observedAt: string;
  value: number;
  baseline?: number | null;
  threshold?: number | null;
  severity?: string;
  acknowledged?: boolean;
}

export interface DriftChartProps {
  points: DriftPoint[];
  metricName?: string;
  ar?: boolean;
  width?: number;
  height?: number;
}

const SEV_COLOR: Record<string, string> = {
  CRITICAL: 'var(--danger)',
  MAJOR: 'var(--danger)',
  MODERATE: 'var(--warning)',
  MINOR: 'var(--accent)',
  INFO: 'var(--info)',
};

const PAD_L = 38;
const PAD_R = 12;
const PAD_T = 12;
const PAD_B = 26;

export function DriftChart({ points, metricName, ar = false, width = 520, height = 240 }: DriftChartProps) {
  const [hover, setHover] = useState<number | null>(null);
  const pts = [...points].sort((a, b) => (a.observedAt < b.observedAt ? -1 : 1));
  if (pts.length === 0) return null;

  const plotW = width - PAD_L - PAD_R;
  const plotH = height - PAD_T - PAD_B;

  // Y domain across values + thresholds + baselines.
  const all: number[] = [];
  for (const p of pts) {
    all.push(p.value);
    if (p.baseline != null) all.push(p.baseline);
    if (p.threshold != null) all.push(p.threshold);
  }
  let lo = Math.min(...all);
  let hi = Math.max(...all);
  if (lo === hi) { lo -= 1; hi += 1; }
  const padY = (hi - lo) * 0.12;
  lo -= padY; hi += padY;

  const n = pts.length;
  const x = (i: number) => (n === 1 ? PAD_L + plotW / 2 : (ar ? PAD_L + plotW - (i / (n - 1)) * plotW : PAD_L + (i / (n - 1)) * plotW));
  const y = (v: number) => PAD_T + plotH - ((v - lo) / (hi - lo)) * plotH;

  // Representative threshold/baseline (latest non-null).
  const threshold = [...pts].reverse().find(p => p.threshold != null)?.threshold ?? null;
  const baseline = [...pts].reverse().find(p => p.baseline != null)?.baseline ?? null;

  const linePath = pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i).toFixed(1)} ${y(p.value).toFixed(1)}`).join(' ');
  const areaPath = `${linePath} L ${x(n - 1).toFixed(1)} ${(PAD_T + plotH).toFixed(1)} L ${x(0).toFixed(1)} ${(PAD_T + plotH).toFixed(1)} Z`;

  const fmt = (v: number) => (Math.abs(v) >= 100 ? v.toFixed(0) : v.toFixed(2));

  return (
    <div style={{ overflowX: 'auto' }}>
      <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img"
        aria-label={ar ? `مخطط انحراف${metricName ? ' لـ ' + metricName : ''} عبر ${n} ملاحظة`
                       : `Drift chart${metricName ? ' for ' + metricName : ''} over ${n} observations`}
        style={{ maxWidth: '100%' }}>
        <defs>
          <linearGradient id="drift-area" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.18" />
            <stop offset="100%" stopColor="var(--primary)" stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* Danger band above threshold */}
        {threshold != null && (
          <rect x={PAD_L} y={PAD_T} width={plotW} height={Math.max(0, y(threshold) - PAD_T)}
            fill="var(--danger)" fillOpacity={0.07} />
        )}
        {/* Axes */}
        <line x1={PAD_L} y1={PAD_T + plotH} x2={PAD_L + plotW} y2={PAD_T + plotH} stroke="var(--border)" />
        {/* Y ticks (lo / mid / hi) */}
        {[lo, (lo + hi) / 2, hi].map((v, i) => (
          <text key={i} x={PAD_L - 5} y={y(v)} textAnchor="end" dominantBaseline="central"
            className="tnum" style={{ font: '600 9px var(--font-sans)', fill: 'var(--ink-4)' }}>{fmt(v)}</text>
        ))}

        {/* Baseline */}
        {baseline != null && (
          <>
            <line x1={PAD_L} y1={y(baseline)} x2={PAD_L + plotW} y2={y(baseline)}
              stroke="var(--ink-4)" strokeDasharray="2 3" strokeOpacity={0.6} />
            <text x={PAD_L + plotW} y={y(baseline) - 3} textAnchor="end"
              style={{ font: '600 8px var(--font-sans)', fill: 'var(--ink-4)' }}>{ar ? 'الأساس' : 'baseline'}</text>
          </>
        )}
        {/* Threshold */}
        {threshold != null && (
          <>
            <line x1={PAD_L} y1={y(threshold)} x2={PAD_L + plotW} y2={y(threshold)}
              stroke="var(--danger)" strokeDasharray="5 4" strokeOpacity={0.8} />
            <text x={PAD_L + plotW} y={y(threshold) - 3} textAnchor="end"
              style={{ font: '700 8px var(--font-sans)', fill: 'var(--danger)' }}>{ar ? 'الحدّ' : 'threshold'}</text>
          </>
        )}

        {/* Value area + line */}
        <path d={areaPath} fill="url(#drift-area)" />
        <path d={linePath} fill="none" stroke="var(--primary)" strokeWidth={2} strokeLinejoin="round" />

        {/* Markers */}
        {pts.map((p, i) => {
          const breached = threshold != null && p.value > threshold;
          const col = SEV_COLOR[(p.severity ?? '').toUpperCase()] ?? 'var(--primary)';
          const active = hover === i;
          return (
            <g key={i} onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}>
              <title>{`${p.observedAt?.slice(0, 16)} · ${fmt(p.value)}${threshold != null ? ` (${ar ? 'الحدّ' : 'thr'} ${fmt(threshold)})` : ''}${breached ? (ar ? ' · تجاوز' : ' · breach') : ''}`}</title>
              {breached && !p.acknowledged && (
                <circle cx={x(i)} cy={y(p.value)} r={8} fill={col} fillOpacity={0.25} className="viz-pulse" />
              )}
              <circle cx={x(i)} cy={y(p.value)} r={active ? 5 : 3.5} fill={col}
                stroke="var(--surface)" strokeWidth={1.5} />
            </g>
          );
        })}
      </svg>
    </div>
  );
}
