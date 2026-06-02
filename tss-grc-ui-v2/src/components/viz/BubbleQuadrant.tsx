import { useState } from 'react';

/**
 * BubbleQuadrant — the classic risk-vs-criticality / materiality board.
 *
 * A 2-D plot with a tinted "manage closely" quadrant (top-end of both axes),
 * bubbles sized + coloured by caller-supplied metrics, hover highlight and
 * click-to-drill. Reused for TPRM (risk × criticality), SAMA outsourcing
 * (materiality × criticality), and the IA universe (risk × recency).
 *
 * Hand-SVG. RTL-aware: the X axis grows toward the inline-end.
 */
export interface BubblePoint {
  id: number | string;
  x: number;
  y: number;
  /** Radius in px (default 9). */
  size?: number;
  color?: string;
  label?: string;
  meta?: string;
}

export interface BubbleQuadrantProps {
  points: BubblePoint[];
  xLabel: string;
  yLabel: string;
  xMax?: number;
  yMax?: number;
  /** Axis tick labels (e.g. ['Low','Med','High','Critical']). */
  xTicks?: string[];
  yTicks?: string[];
  /** Tint the high-x / high-y "manage closely" quadrant. */
  dangerQuadrant?: boolean;
  ar?: boolean;
  width?: number;
  height?: number;
  onPointClick?: (p: BubblePoint) => void;
}

const PAD_L = 40;
const PAD_B = 38;
const PAD_T = 14;
const PAD_R = 14;

export function BubbleQuadrant({
  points,
  xLabel,
  yLabel,
  xMax = 4,
  yMax = 4,
  xTicks,
  yTicks,
  dangerQuadrant = true,
  ar = false,
  width = 460,
  height = 320,
  onPointClick,
}: BubbleQuadrantProps) {
  const [hover, setHover] = useState<number | string | null>(null);
  const plotW = width - PAD_L - PAD_R;
  const plotH = height - PAD_T - PAD_B;
  const x0 = PAD_L;
  const y0 = PAD_T + plotH; // bottom

  const sx = (x: number) => {
    const frac = Math.max(0, Math.min(1, x / xMax));
    return ar ? x0 + plotW - frac * plotW : x0 + frac * plotW;
  };
  const sy = (y: number) => y0 - Math.max(0, Math.min(1, y / yMax)) * plotH;

  return (
    <div style={{ overflowX: 'auto' }}>
      <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img"
        aria-label={ar ? `${points.length} عنصر على مصفوفة ${yLabel} مقابل ${xLabel}`
                       : `${points.length} items on a ${yLabel} vs ${xLabel} matrix`}
        style={{ maxWidth: '100%' }}>
        {/* Danger quadrant tint (high x + high y) */}
        {dangerQuadrant && (
          <rect
            x={ar ? x0 : x0 + plotW / 2}
            y={PAD_T}
            width={plotW / 2}
            height={plotH / 2}
            fill="var(--danger)" fillOpacity={0.06} />
        )}
        {/* Mid gridlines */}
        <line x1={x0} y1={PAD_T + plotH / 2} x2={x0 + plotW} y2={PAD_T + plotH / 2}
          stroke="var(--border)" strokeDasharray="3 3" />
        <line x1={x0 + plotW / 2} y1={PAD_T} x2={x0 + plotW / 2} y2={y0}
          stroke="var(--border)" strokeDasharray="3 3" />
        {/* Axes */}
        <line x1={x0} y1={y0} x2={x0 + plotW} y2={y0} stroke="var(--border-strong)" />
        <line x1={x0} y1={PAD_T} x2={x0} y2={y0} stroke="var(--border-strong)" />

        {/* Axis tick labels — evenly spaced to align with discrete levels */}
        {xTicks?.map((t, i) => {
          const v = ((i + 1) / (xTicks.length + 1)) * xMax;
          return (
            <text key={`xt-${i}`} x={sx(v)} y={y0 + 14} textAnchor="middle"
              style={{ font: '600 9px var(--font-sans)', fill: 'var(--ink-3)' }}>{t}</text>
          );
        })}
        {yTicks?.map((t, i) => {
          const v = ((i + 1) / (yTicks.length + 1)) * yMax;
          return (
            <text key={`yt-${i}`} x={x0 - 6} y={sy(v)} textAnchor="end" dominantBaseline="central"
              style={{ font: '600 9px var(--font-sans)', fill: 'var(--ink-3)' }}>{t}</text>
          );
        })}

        {/* Axis titles */}
        <text x={x0 + plotW / 2} y={height - 4} textAnchor="middle"
          style={{ font: '700 10px var(--font-sans)', fill: 'var(--ink-2)', letterSpacing: '0.04em' }}>{xLabel}</text>
        <text x={12} y={PAD_T + plotH / 2} textAnchor="middle"
          transform={`rotate(-90 12 ${PAD_T + plotH / 2})`}
          style={{ font: '700 10px var(--font-sans)', fill: 'var(--ink-2)', letterSpacing: '0.04em' }}>{yLabel}</text>

        {/* Bubbles */}
        {points.map((p) => {
          const active = hover === p.id;
          const r = (p.size ?? 9) * (active ? 1.25 : 1);
          return (
            <g key={p.id}
              onMouseEnter={() => setHover(p.id)} onMouseLeave={() => setHover(null)}
              onClick={() => onPointClick?.(p)}
              style={{ cursor: onPointClick ? 'pointer' : 'default' }}>
              <title>{`${p.label ?? p.id}${p.meta ? ' · ' + p.meta : ''}`}</title>
              <circle cx={sx(p.x)} cy={sy(p.y)} r={r}
                fill={p.color ?? 'var(--primary)'} fillOpacity={active ? 0.9 : 0.6}
                stroke="var(--surface)" strokeWidth={1.5}
                style={{ transition: 'r var(--dur-fast), fill-opacity var(--dur-fast)' }} />
            </g>
          );
        })}
      </svg>
    </div>
  );
}
