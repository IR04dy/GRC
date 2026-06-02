/**
 * Compact SVG donut chart. No external chart library — keeps the bundle
 * small and gives full styling control. Used by the Authority Workspace
 * to show control-coverage breakdown.
 *
 * Pass `onSegmentClick(segment)` to make segments interactive (cursor +
 * click handler on each arc + every legend row).
 */
export interface DonutSegment {
  label: string;
  value: number;
  color: string;
  /** Optional payload bag the host can use to identify the segment. */
  data?: unknown;
}

export function Donut({
  segments, size = 160, thickness = 24, centerLabel, centerSub, onSegmentClick,
}: {
  segments: DonutSegment[];
  size?: number;
  thickness?: number;
  centerLabel?: string;
  centerSub?: string;
  /** Optional callback fired when an arc or legend row is clicked. */
  onSegmentClick?: (segment: DonutSegment) => void;
}) {
  const r = (size - thickness) / 2;
  const c = size / 2;
  const circumference = 2 * Math.PI * r;
  const total = segments.reduce((s, x) => s + x.value, 0) || 1;

  let offset = 0;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        {/* Track */}
        <circle cx={c} cy={c} r={r} fill="none" stroke="var(--surface-3)" strokeWidth={thickness} />
        {/* Segments */}
        {segments.map((s, i) => {
          const len = (s.value / total) * circumference;
          const dasharray = `${len} ${circumference - len}`;
          const dashoffset = circumference - offset;
          offset += len;
          return (
            <circle key={i}
              cx={c} cy={c} r={r}
              fill="none"
              stroke={s.color}
              strokeWidth={thickness}
              strokeDasharray={dasharray}
              strokeDashoffset={dashoffset}
              transform={`rotate(-90 ${c} ${c})`}
              style={{
                transition: 'stroke-dasharray var(--dur-normal) var(--ease-standard), stroke-width var(--dur-fast) var(--ease-standard)',
                cursor: onSegmentClick ? 'pointer' : 'default',
              }}
              onClick={onSegmentClick ? () => onSegmentClick(s) : undefined}
              onMouseEnter={onSegmentClick ? (e) => { (e.currentTarget as SVGCircleElement).style.strokeWidth = String(thickness + 4); } : undefined}
              onMouseLeave={onSegmentClick ? (e) => { (e.currentTarget as SVGCircleElement).style.strokeWidth = String(thickness); } : undefined}
            >
              <title>{s.label}: {s.value}{onSegmentClick ? ' — click to drill in' : ''}</title>
            </circle>
          );
        })}
        {/* Center text */}
        {centerLabel && (
          <text x={c} y={c} textAnchor="middle" dominantBaseline="central"
                fontFamily="var(--font-mono)" fontWeight={800} fontSize={size * 0.18}
                fill="var(--ink-1)" dy={centerSub ? -6 : 0}>
            {centerLabel}
          </text>
        )}
        {centerSub && (
          <text x={c} y={c} textAnchor="middle" dominantBaseline="central"
                fontSize={11} fill="var(--ink-3)" dy={size * 0.12}>
            {centerSub}
          </text>
        )}
      </svg>
      {/* Legend */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
        {segments.map((s, i) => {
          const clickable = !!onSegmentClick;
          return (
            <div key={i}
              role={clickable ? 'button' : undefined}
              tabIndex={clickable ? 0 : undefined}
              onClick={clickable ? () => onSegmentClick(s) : undefined}
              onKeyDown={clickable ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSegmentClick(s); } } : undefined}
              style={{
                display: 'flex', alignItems: 'center', gap: 6,
                padding: clickable ? '2px 4px' : 0,
                borderRadius: 'var(--r-sm)',
                cursor: clickable ? 'pointer' : 'default',
                transition: 'background var(--dur-fast) var(--ease-standard)',
              }}
              onMouseEnter={clickable ? (e) => (e.currentTarget as HTMLElement).style.background = 'var(--surface-2)' : undefined}
              onMouseLeave={clickable ? (e) => (e.currentTarget as HTMLElement).style.background = 'transparent' : undefined}
            >
              <span style={{ width: 10, height: 10, borderRadius: 2, background: s.color, flexShrink: 0 }} />
              <span style={{ color: 'var(--ink-2)', flex: 1 }}>{s.label}</span>
              <span className="mono" style={{ fontWeight: 700, color: 'var(--ink-1)' }}>{s.value}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
