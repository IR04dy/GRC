import { type ReactNode } from 'react';
import { useCountUp } from '../../lib/useCountUp';
import { DEFAULT_GAUGE_STROKE } from '../../lib/gaugeSizes';

/**
 * RadialGauge — the platform's "arc-as-number" hero primitive.
 *
 * A 240° (configurable) gauge: a track, optional coloured RAG bands, and a
 * value arc that sweeps to position in sync with a count-up of the centre
 * numeral. Token-driven, RTL-aware (arcs mirror; the numeral stays upright),
 * and reduced-motion-safe (snaps to final via useCountUp).
 *
 * Powers: Executive posture, risk appetite, BCM readiness rings, KRI health,
 * MTTD/MTTR — anywhere one headline metric reads against bands.
 */
export interface GaugeBand {
  /** Inclusive lower bound (in value units). */
  from: number;
  /** Exclusive upper bound (in value units). */
  to: number;
  /** CSS colour (token var recommended), e.g. 'var(--danger)'. */
  color: string;
}

export interface RadialGaugeProps {
  value: number;
  max?: number;
  /** Outer pixel size of the square SVG. */
  size?: number;
  /** Arc stroke width. */
  thickness?: number;
  /** Total sweep in degrees, centred on top (12 o'clock). */
  sweepDeg?: number;
  /** RAG zones drawn as faint track segments; also colours the value arc. */
  bands?: GaugeBand[];
  /** Explicit value-arc colour (overrides band-derived colour). */
  valueColor?: string;
  trackColor?: string;
  /** Centre numeral formatter. */
  format?: (v: number) => string;
  /** Small caption under the numeral. */
  caption?: ReactNode;
  /** Suffix beside the numeral, e.g. '/ 100'. */
  unit?: ReactNode;
  decimals?: number;
  animate?: boolean;
  rtl?: boolean;
  'aria-label'?: string;
}

function polar(cx: number, cy: number, r: number, gaugeDeg: number): [number, number] {
  const theta = ((-90 + gaugeDeg) * Math.PI) / 180; // 0° = top, clockwise positive
  return [cx + r * Math.cos(theta), cy + r * Math.sin(theta)];
}

function arcPath(cx: number, cy: number, r: number, a0: number, a1: number): string {
  const [x0, y0] = polar(cx, cy, r, a0);
  const [x1, y1] = polar(cx, cy, r, a1);
  const large = Math.abs(a1 - a0) > 180 ? 1 : 0;
  const sweep = a1 > a0 ? 1 : 0;
  return `M ${x0.toFixed(2)} ${y0.toFixed(2)} A ${r} ${r} 0 ${large} ${sweep} ${x1.toFixed(2)} ${y1.toFixed(2)}`;
}

export function RadialGauge({
  value,
  max = 100,
  size = 168,
  thickness = DEFAULT_GAUGE_STROKE,
  sweepDeg = 240,
  bands,
  valueColor,
  trackColor = 'var(--surface-3)',
  format,
  caption,
  unit,
  decimals = 0,
  animate = true,
  rtl = false,
  ...rest
}: RadialGaugeProps) {
  const animated = useCountUp(animate ? value : value, { durationMs: animate ? 700 : 0, decimals });
  const shown = animate ? animated : value;

  const clampedMax = max <= 0 ? 1 : max;
  const frac = Math.max(0, Math.min(1, shown / clampedMax));
  const start = -sweepDeg / 2;
  const end = sweepDeg / 2;
  const angleFor = (v: number) => start + (Math.max(0, Math.min(clampedMax, v)) / clampedMax) * sweepDeg;

  const cx = size / 2;
  const cy = size / 2;
  const r = (size - thickness) / 2 - 2;

  // Derive the value-arc colour from the band containing the current value.
  const derived =
    valueColor ??
    bands?.find((b) => value >= b.from && value < b.to)?.color ??
    bands?.[bands.length - 1]?.color ??
    'var(--primary)';

  const valueEnd = start + frac * sweepDeg;
  const display = format ? format(shown) : String(Math.round(shown * 10 ** decimals) / 10 ** decimals);

  return (
    <div style={{ position: 'relative', width: size, height: size }}
      role="img" aria-label={rest['aria-label'] ?? `${display} of ${max}`}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}
        style={{ transform: rtl ? 'scaleX(-1)' : undefined, overflow: 'visible' }} aria-hidden="true">
        {/* Track */}
        <path d={arcPath(cx, cy, r, start, end)} fill="none" stroke={trackColor}
          strokeWidth={thickness} strokeLinecap="round" />
        {/* Faint RAG band segments behind the value arc */}
        {bands?.map((b, i) => (
          <path key={i} d={arcPath(cx, cy, r, angleFor(b.from), angleFor(b.to))} fill="none"
            stroke={b.color} strokeWidth={thickness} strokeLinecap="butt"
            style={{ opacity: 0.22 }} />
        ))}
        {/* Value arc */}
        {frac > 0 && (
          <path d={arcPath(cx, cy, r, start, valueEnd)} fill="none" stroke={derived}
            strokeWidth={thickness} strokeLinecap="round" />
        )}
        {/* Needle tip dot */}
        {frac > 0 && (() => {
          const [tx, ty] = polar(cx, cy, r, valueEnd);
          return <circle cx={tx} cy={ty} r={thickness / 2 + 1.5} fill={derived} stroke="var(--surface)" strokeWidth={2} />;
        })()}
      </svg>
      {/* Centre numeral (HTML overlay — never mirrored) */}
      <div style={{
        position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', gap: 2, pointerEvents: 'none',
      }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
          <span className="tnum" style={{
            font: `800 ${Math.round(size * 0.26)}px/1 var(--font-sans)`,
            color: derived, letterSpacing: 'var(--tracking-num)',
          }}>{display}</span>
          {unit && <span className="tnum" style={{ font: '700 13px var(--font-sans)', color: 'var(--ink-3)' }}>{unit}</span>}
        </div>
        {caption && (
          <span style={{
            font: '700 9px var(--font-sans)', color: 'var(--ink-3)',
            textTransform: 'uppercase', letterSpacing: '0.1em', textAlign: 'center',
          }}>{caption}</span>
        )}
      </div>
    </div>
  );
}
