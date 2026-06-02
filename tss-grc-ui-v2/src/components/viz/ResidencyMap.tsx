import { useState } from 'react';

/**
 * ResidencyMap — the Saudi-native cross-border data hero.
 *
 * KSA-centric concentric PDPL trust zones (In-Kingdom core → GCC → Adequate →
 * Non-adequate), with each personal-data flow drawn as a transfer ray landing
 * in its adequacy zone. A cross-border flow missing its PDPL Art. 7 transfer
 * reference renders as a dashed red, pulsing ray — so violations find the user.
 *
 * Hand-rolled SVG. The map reads the same in RTL (it's radial, not directional);
 * destination labels are Latin/neutral. Reduced-motion disables the pulse.
 */
export type ResidencyZone = 'IN_KINGDOM' | 'GCC' | 'ADEQUATE' | 'NON_ADEQUATE';

export interface ResidencyFlow {
  id: number | string;
  zone?: ResidencyZone;
  sourceLabel?: string;
  destLabel?: string;
  destCountry?: string;
  hasArt7Ref?: boolean;
}

export interface ResidencyMapProps {
  flows: ResidencyFlow[];
  ar?: boolean;
  size?: number;
  onFlowClick?: (f: ResidencyFlow) => void;
}

const ZONES: ResidencyZone[] = ['IN_KINGDOM', 'GCC', 'ADEQUATE', 'NON_ADEQUATE'];
const ZONE_COLOR: Record<ResidencyZone, string> = {
  IN_KINGDOM: 'var(--success)',
  GCC: 'var(--info)',
  ADEQUATE: 'var(--warning)',
  NON_ADEQUATE: 'var(--danger)',
};
const ZONE_LABEL: Record<ResidencyZone, { en: string; ar: string }> = {
  IN_KINGDOM: { en: 'In Kingdom', ar: 'داخل المملكة' },
  GCC: { en: 'GCC', ar: 'دول الخليج' },
  ADEQUATE: { en: 'Adequate', ar: 'ملائم' },
  NON_ADEQUATE: { en: 'Non-adequate', ar: 'غير ملائم' },
};

export function ResidencyMap({ flows, ar = false, size = 400, onFlowClick }: ResidencyMapProps) {
  const [hover, setHover] = useState<string | number | null>(null);
  const cx = size / 2;
  const cy = size / 2;
  const unit = size / 400;
  const zoneRingR: Record<ResidencyZone, number> = {
    IN_KINGDOM: 42 * unit, GCC: 124 * unit, ADEQUATE: 166 * unit, NON_ADEQUATE: 196 * unit,
  };
  const nodeR: Record<ResidencyZone, number> = {
    IN_KINGDOM: 26 * unit, GCC: 108 * unit, ADEQUATE: 150 * unit, NON_ADEQUATE: 186 * unit,
  };

  const polar = (r: number, deg: number): [number, number] => {
    const t = ((-90 + deg) * Math.PI) / 180;
    return [cx + r * Math.cos(t), cy + r * Math.sin(t)];
  };

  // Spread each zone's flows evenly around the circle.
  const byZone = new Map<ResidencyZone, ResidencyFlow[]>();
  for (const f of flows) {
    const z = f.zone ?? 'IN_KINGDOM';
    (byZone.get(z) ?? byZone.set(z, []).get(z)!).push(f);
  }
  const placed: Array<{ f: ResidencyFlow; x: number; y: number; gap: boolean }> = [];
  for (const z of ZONES) {
    const list = byZone.get(z) ?? [];
    list.forEach((f, j) => {
      const deg = ((j + 0.5) / Math.max(1, list.length)) * 360 + (z === 'GCC' ? 18 : z === 'ADEQUATE' ? 9 : 0);
      const [x, y] = polar(nodeR[z], deg);
      const gap = z !== 'IN_KINGDOM' && f.hasArt7Ref === false;
      placed.push({ f, x, y, gap });
    });
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img"
        aria-label={ar
          ? `خريطة إقامة البيانات: ${flows.length} تدفّق عبر مناطق الثقة`
          : `Data-residency map: ${flows.length} flows across PDPL trust zones`}
        style={{ maxWidth: '100%' }}>
        {/* Zone backdrops (outer → inner) */}
        {[...ZONES].reverse().map((z) => (
          <circle key={z} cx={cx} cy={cy} r={zoneRingR[z]}
            fill={ZONE_COLOR[z]} fillOpacity={z === 'IN_KINGDOM' ? 0.16 : 0.06}
            stroke={ZONE_COLOR[z]} strokeOpacity={0.3} strokeWidth={1} />
        ))}
        {/* Zone labels (top of each ring) */}
        {ZONES.map((z) => {
          const [lx, ly] = polar(zoneRingR[z] - 9 * unit, 0);
          return (
            <text key={z} x={lx} y={ly} textAnchor="middle"
              style={{ font: `700 ${9 * unit}px var(--font-sans)`, fill: ZONE_COLOR[z], opacity: 0.85 }}>
              {ZONE_LABEL[z][ar ? 'ar' : 'en']}
            </text>
          );
        })}

        {/* Transfer rays */}
        {placed.map(({ f, x, y, gap }) => {
          const z = f.zone ?? 'IN_KINGDOM';
          const active = hover === f.id;
          const col = gap ? 'var(--danger)' : ZONE_COLOR[z];
          const mx = (cx + x) / 2 + (y - cy) * 0.08;
          const my = (cy + y) / 2 - (x - cx) * 0.08;
          return (
            <path key={`ray-${f.id}`} d={`M ${cx} ${cy} Q ${mx} ${my} ${x} ${y}`}
              fill="none" stroke={col}
              strokeOpacity={hover === null ? 0.45 : active ? 0.95 : 0.12}
              strokeWidth={active ? 2.4 : 1.6}
              strokeDasharray={gap ? `${5 * unit} ${4 * unit}` : undefined}
              className={gap ? 'viz-pulse' : undefined}
              style={{ transition: 'stroke-opacity var(--dur-fast)' }} />
          );
        })}

        {/* KSA core */}
        <circle cx={cx} cy={cy} r={nodeR.IN_KINGDOM} fill="var(--success)" fillOpacity={0.18}
          stroke="var(--success)" strokeWidth={1.5} className="viz-pulse" />
        <text x={cx} y={cy - 3 * unit} textAnchor="middle" dominantBaseline="central"
          style={{ font: `800 ${13 * unit}px var(--font-sans)`, fill: 'var(--success)' }}>KSA</text>
        <text x={cx} y={cy + 11 * unit} textAnchor="middle" dominantBaseline="central"
          style={{ font: `700 ${9 * unit}px var(--font-sans)`, fill: 'var(--success)', opacity: 0.8 }}>
          {ar ? 'المملكة' : 'home'}
        </text>

        {/* Destination nodes */}
        {placed.map(({ f, x, y, gap }) => {
          const z = f.zone ?? 'IN_KINGDOM';
          const col = gap ? 'var(--danger)' : ZONE_COLOR[z];
          return (
            <g key={`node-${f.id}`}
              onMouseEnter={() => setHover(f.id)} onMouseLeave={() => setHover(null)}
              onClick={() => onFlowClick?.(f)}
              style={{ cursor: onFlowClick ? 'pointer' : 'default' }}>
              <title>{`${f.sourceLabel ?? '?'} → ${f.destLabel ?? f.destCountry ?? '?'} · ${ZONE_LABEL[z][ar ? 'ar' : 'en']}${gap ? (ar ? ' · ينقصه مرجع المادة ٧' : ' · missing Art.7 ref') : ''}`}</title>
              <circle cx={x} cy={y} r={(hover === f.id ? 6 : 4.5) * unit} fill={col}
                stroke="var(--surface)" strokeWidth={1.5 * unit}
                className={gap ? 'viz-pulse' : undefined} />
            </g>
          );
        })}
      </svg>

      {/* Legend */}
      <div style={{ display: 'flex', justifyContent: 'center', gap: 12, flexWrap: 'wrap', marginTop: 8, fontSize: 11, color: 'var(--ink-3)' }}>
        {ZONES.map((z) => (
          <span key={z} style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
            <span style={{ width: 10, height: 10, borderRadius: 999, background: ZONE_COLOR[z], display: 'inline-block' }} />
            {ZONE_LABEL[z][ar ? 'ar' : 'en']}
          </span>
        ))}
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
          <svg width="20" height="6"><line x1="0" y1="3" x2="20" y2="3" stroke="var(--danger)" strokeWidth="2" strokeDasharray="4 3" /></svg>
          {ar ? 'ينقصه مرجع المادة ٧' : 'Missing Art. 7 ref'}
        </span>
      </div>
    </div>
  );
}
