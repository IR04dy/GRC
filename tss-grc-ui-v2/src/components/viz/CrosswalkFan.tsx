import { useState } from 'react';

/**
 * CrosswalkFan — "test once, satisfy many" made visual.
 *
 * One control node fans out to every authority citation it satisfies, with
 * Bézier ribbons coloured per authority and a ×N reuse multiplier. Hovering a
 * citation traces its ribbon (others dim); hovering the control lights the
 * whole fan. Gap citations (not implemented) render as dashed/danger.
 *
 * Hand-rolled SVG (precise ribbon anchoring + hover control). RTL-aware: the
 * control sits on the inline-start, citations on the inline-end.
 */
export interface CrosswalkCitation {
  authorityCode: string;
  frameworkCode?: string;
  controlRef: string;
  citationText?: string;
  controlStatus?: string;
}

export interface CrosswalkFanProps {
  controlCode: string;
  citations: CrosswalkCitation[];
  ar?: boolean;
  onCitationClick?: (c: CrosswalkCitation) => void;
}

const PAD = 10;
const CONTROL_W = 116;
const CONTROL_H = 56;
const CIT_W = 300;
const CIT_H = 30;
const GAP = 9;
const RIBBON_GAP = 84;

export function CrosswalkFan({ controlCode, citations, ar = false, onCitationClick }: CrosswalkFanProps) {
  const [hover, setHover] = useState<number | null>(null);
  const [hoverControl, setHoverControl] = useState(false);

  const n = citations.length;
  if (n === 0) return null;

  // Distinct authorities → --cat-N colour.
  const authorities = [...new Set(citations.map((c) => c.authorityCode))];
  const colorFor = (a: string) => `var(--cat-${(authorities.indexOf(a) % 8) + 1})`;

  const totalCitH = n * CIT_H + (n - 1) * GAP;
  const svgH = Math.max(CONTROL_H, totalCitH) + 2 * PAD;
  const svgW = PAD + CONTROL_W + RIBBON_GAP + CIT_W + PAD;

  // Side-aware geometry (control on inline-start).
  const controlX = ar ? svgW - PAD - CONTROL_W : PAD;
  const citX = ar ? PAD : PAD + CONTROL_W + RIBBON_GAP;
  const controlCy = svgH / 2;
  const controlEdge = ar ? controlX : controlX + CONTROL_W; // side facing citations
  const citEdge = ar ? citX + CIT_W : citX;                  // side facing control
  const dir = ar ? -1 : 1;

  const citCy = (i: number) => PAD + i * (CIT_H + GAP) + CIT_H / 2;

  const ribbonOpacity = (i: number) => {
    if (hoverControl) return 0.85;
    if (hover === null) return 0.34;
    return hover === i ? 0.9 : 0.1;
  };

  return (
    <div style={{ overflowX: 'auto' }}>
      <svg width={svgW} height={svgH} viewBox={`0 0 ${svgW} ${svgH}`} style={{ maxWidth: '100%' }}
        role="img"
        aria-label={ar
          ? `الضابط ${controlCode} يلبّي ${n} استشهاداً عبر ${authorities.length} جهة`
          : `Control ${controlCode} satisfies ${n} citations across ${authorities.length} authorities`}>
        {/* Ribbons */}
        {citations.map((c, i) => {
          const x0 = controlEdge, y0 = controlCy;
          const x1 = citEdge, y1 = citCy(i);
          const cp = RIBBON_GAP * 0.55;
          const d = `M ${x0} ${y0} C ${x0 + dir * cp} ${y0}, ${x1 - dir * cp} ${y1}, ${x1} ${y1}`;
          const gap = (c.controlStatus ?? '').toUpperCase() === 'NOT_IMPLEMENTED';
          return (
            <path key={i} d={d} fill="none"
              stroke={gap ? 'var(--danger)' : colorFor(c.authorityCode)}
              strokeOpacity={ribbonOpacity(i)}
              strokeWidth={hover === i || hoverControl ? 3 : 2}
              strokeDasharray={gap ? '5 4' : undefined}
              style={{ transition: 'stroke-opacity var(--dur-fast) var(--ease-out), stroke-width var(--dur-fast)' }} />
          );
        })}

        {/* Control node */}
        <g onMouseEnter={() => setHoverControl(true)} onMouseLeave={() => setHoverControl(false)}
          style={{ cursor: 'default' }}>
          <rect x={controlX} y={controlCy - CONTROL_H / 2} width={CONTROL_W} height={CONTROL_H} rx={8}
            fill="var(--primary)" />
          <text x={controlX + CONTROL_W / 2} y={controlCy - 6} textAnchor="middle"
            style={{ font: '700 13px var(--font-mono)', fill: 'var(--ink-0)' }}>{controlCode}</text>
          <text x={controlX + CONTROL_W / 2} y={controlCy + 13} textAnchor="middle" className="tnum"
            style={{ font: '800 14px var(--font-sans)', fill: 'var(--ink-0)', opacity: 0.95 }}>×{n}</text>
        </g>

        {/* Citation nodes */}
        {citations.map((c, i) => {
          const y = citCy(i) - CIT_H / 2;
          const col = (c.controlStatus ?? '').toUpperCase() === 'NOT_IMPLEMENTED'
            ? 'var(--danger)' : colorFor(c.authorityCode);
          const active = hover === i || hoverControl;
          return (
            <g key={i}
              onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}
              onClick={() => onCitationClick?.(c)}
              style={{ cursor: onCitationClick ? 'pointer' : 'default' }}>
              <rect x={citX} y={y} width={CIT_W} height={CIT_H} rx={8}
                fill={active ? 'var(--surface-2)' : 'var(--surface)'}
                stroke={col} strokeWidth={active ? 2 : 1}
                style={{ transition: 'fill var(--dur-fast), stroke-width var(--dur-fast)' }} />
              {/* authority chip */}
              <rect x={citX + 6} y={y + 6} width={48} height={CIT_H - 12} rx={4} fill={col} fillOpacity={0.16} />
              <text x={citX + 30} y={citCy(i)} textAnchor="middle" dominantBaseline="central"
                style={{ font: '700 10px var(--font-sans)', fill: col }}>{c.authorityCode}</text>
              {/* citation ref + framework */}
              <text x={citX + 62} y={citCy(i)} dominantBaseline="central"
                style={{ font: '600 12px var(--font-mono)', fill: 'var(--ink-1)' }}>
                {(c.frameworkCode ? c.frameworkCode.replace(/_/g, ' ') + ' · ' : '') + c.controlRef}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
