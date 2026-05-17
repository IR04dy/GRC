/**
 * Compact SVG time-series line/area chart. No external chart library.
 * Designed for KRI trend tiles, regulator-coverage trend, and audit
 * activity volume.
 *
 * RTL-aware: x-axis ticks read right-to-left when lang === 'ar'.
 *
 * Modelled on the industry pattern Performance Analytics' breakdown sparkline.
 */
import { useI18n } from '../../lib/i18n';

export interface TimeSeriesPoint { label: string; value: number; }

export function TimeSeries({
  series, height = 60, width = 200, color = 'var(--primary)', area = true,
  showAxis = false, threshold,
}: {
  series: TimeSeriesPoint[];
  height?: number;
  width?: number;
  color?: string;
  /** When true (default), fill the area under the line. */
  area?: boolean;
  /** When true, render x-axis tick labels and y-axis range hints. */
  showAxis?: boolean;
  /** Optional horizontal threshold line (e.g. KRI amber/red). */
  threshold?: { value: number; color: string; label?: string };
}) {
  const { lang } = useI18n();
  if (series.length === 0) {
    return <div style={{ width, height, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, color: 'var(--ink-3)' }}>—</div>;
  }
  const padX = 6;
  const padY = 6;
  const innerW = width - padX * 2;
  const innerH = height - padY * 2 - (showAxis ? 14 : 0);

  const max = Math.max(threshold?.value ?? 0, ...series.map(p => p.value));
  const min = Math.min(0, ...series.map(p => p.value));
  const range = max - min || 1;

  const xs = series.map((_, i) => padX + (i / Math.max(1, series.length - 1)) * innerW);
  const ys = series.map(p => padY + innerH - ((p.value - min) / range) * innerH);

  // RTL flip
  const flip = lang === 'ar';
  const xat = (i: number) => flip ? padX + innerW - (xs[i] - padX) : xs[i];

  const linePath = series.map((_, i) => `${i === 0 ? 'M' : 'L'} ${xat(i)} ${ys[i]}`).join(' ');
  const areaPath = `${linePath} L ${xat(series.length - 1)} ${padY + innerH} L ${xat(0)} ${padY + innerH} Z`;

  const thresholdY = threshold ? padY + innerH - ((threshold.value - min) / range) * innerH : null;

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Time-series chart">
      {/* Threshold line */}
      {threshold && thresholdY != null && (
        <line x1={padX} y1={thresholdY} x2={padX + innerW} y2={thresholdY}
          stroke={threshold.color} strokeWidth={1} strokeDasharray="3,3" opacity={0.7}>
          <title>{threshold.label ?? `Threshold ${threshold.value}`}</title>
        </line>
      )}

      {/* Area fill */}
      {area && (
        <path d={areaPath} fill={color} opacity={0.15} />
      )}

      {/* Line */}
      <path d={linePath} fill="none" stroke={color} strokeWidth={1.8} strokeLinejoin="round" strokeLinecap="round" />

      {/* Last-point marker */}
      <circle cx={xat(series.length - 1)} cy={ys[series.length - 1]} r={3} fill={color}>
        <title>{series[series.length - 1].label}: {series[series.length - 1].value}</title>
      </circle>

      {/* Optional x-axis labels (first / last only to stay compact) */}
      {showAxis && (
        <>
          <text x={xat(0)} y={height - 2} fontSize={9} fill="var(--ink-3)" textAnchor={flip ? 'end' : 'start'}>
            {series[0].label}
          </text>
          <text x={xat(series.length - 1)} y={height - 2} fontSize={9} fill="var(--ink-3)" textAnchor={flip ? 'start' : 'end'}>
            {series[series.length - 1].label}
          </text>
        </>
      )}
    </svg>
  );
}
