/**
 * Compact SVG donut chart. No external chart library — keeps the bundle
 * small and gives full styling control. Used by the Authority Workspace
 * to show control-coverage breakdown.
 */
export interface DonutSegment {
  label: string;
  value: number;
  color: string;
}

export function Donut({
  segments, size = 160, thickness = 24, centerLabel, centerSub,
}: {
  segments: DonutSegment[];
  size?: number;
  thickness?: number;
  /** Big number rendered in the middle (e.g. "78%"). */
  centerLabel?: string;
  /** Sub-label rendered below the big number (e.g. "Coverage"). */
  centerSub?: string;
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
              style={{ transition: 'stroke-dasharray var(--dur-base) var(--ease-standard)' }}
            >
              <title>{s.label}: {s.value}</title>
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
        {segments.map((s, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 10, height: 10, borderRadius: 2, background: s.color, flexShrink: 0 }} />
            <span style={{ color: 'var(--ink-2)', flex: 1 }}>{s.label}</span>
            <span className="mono" style={{ fontWeight: 700, color: 'var(--ink-1)' }}>{s.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
