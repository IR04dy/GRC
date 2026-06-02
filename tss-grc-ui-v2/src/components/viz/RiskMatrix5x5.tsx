import { useId } from 'react';
import { riskColorForScore } from '../../lib/ragColors';

/**
 * RiskMatrix5x5 — the platform's signature risk glyph.
 *
 * A token-driven 5×5 likelihood×impact matrix (SVG): cells shaded on the
 * `--risk-*` ramp with counts, plus an optional **inherent→residual vector
 * field** — one arrow per risk movement (down-left = controls working, teal;
 * up-right = deteriorating, red). This makes control effectiveness visible at
 * a glance, which no static heatmap does.
 *
 * RTL-aware (likelihood axis mirrors), keyboard/`title` accessible, and
 * reduced-motion-safe. Cells are clickable to drill into the register.
 */
export interface RiskMatrixCell { likelihood: number; impact: number; count: number; }
export interface RiskMovement { fromL: number; fromI: number; toL: number; toI: number; }

export interface RiskMatrix5x5Props {
  cells: RiskMatrixCell[];
  movements?: RiskMovement[];
  showVectors?: boolean;
  onCellClick?: (likelihood: number, impact: number) => void;
  ar?: boolean;
  /** Cell edge length in px. */
  cell?: number;
}

const GAP = 4;
const PAD_START = 30; // room for the impact axis (numbers + label)
const PAD_END = 28;   // room for the likelihood axis below

export function RiskMatrix5x5({
  cells,
  movements,
  showVectors = true,
  onCellClick,
  ar = false,
  cell = 54,
}: RiskMatrix5x5Props) {
  const uid = useId().replace(/:/g, '');
  const step = cell + GAP;
  const gridW = 5 * step - GAP;
  const w = PAD_START + gridW;
  const h = gridW + PAD_END;

  // Likelihood 1..5 along X (mirrors in RTL); impact 1..5 up the Y (5 at top).
  const xFor = (L: number) => PAD_START + (ar ? 5 - L : L - 1) * step;
  const yFor = (I: number) => (5 - I) * step;
  const cxFor = (L: number) => xFor(L) + cell / 2;
  const cyFor = (I: number) => yFor(I) + cell / 2;

  const countAt = (L: number, I: number) =>
    cells.find((c) => c.likelihood === L && c.impact === I)?.count ?? 0;

  // Aggregate movements by (from→to) so repeated transitions render as one
  // weighted arrow rather than a tangle.
  const vectorMap = new Map<string, RiskMovement & { weight: number }>();
  if (showVectors && movements) {
    for (const m of movements) {
      if (m.fromL === m.toL && m.fromI === m.toI) continue; // no movement
      const k = `${m.fromL},${m.fromI}->${m.toL},${m.toI}`;
      const ex = vectorMap.get(k);
      if (ex) ex.weight += 1;
      else vectorMap.set(k, { ...m, weight: 1 });
    }
  }
  const vectors = [...vectorMap.values()];

  const impacts = [5, 4, 3, 2, 1];
  const likelihoods = [1, 2, 3, 4, 5];
  const axisColor = 'var(--ink-3)';

  return (
    <div style={{ overflowX: 'auto' }}>
      <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} role="img"
        aria-label={ar ? 'مصفوفة المخاطر ٥×٥ مع متجهات الانتقال من الكامن إلى المتبقي'
                       : '5 by 5 risk matrix with inherent-to-residual movement vectors'}
        style={{ maxWidth: '100%' }}>
        <defs>
          <marker id={`arw-good-${uid}`} markerWidth="7" markerHeight="7" refX="5.5" refY="3"
            orient="auto" markerUnits="userSpaceOnUse">
            <path d="M0,0 L6,3 L0,6 Z" fill="var(--primary)" />
          </marker>
          <marker id={`arw-bad-${uid}`} markerWidth="7" markerHeight="7" refX="5.5" refY="3"
            orient="auto" markerUnits="userSpaceOnUse">
            <path d="M0,0 L6,3 L0,6 Z" fill="var(--danger)" />
          </marker>
        </defs>

        {/* Cells */}
        {impacts.map((I) =>
          likelihoods.map((L) => {
            const count = countAt(L, I);
            const score = L * I;
            const fill = count > 0 ? riskColorForScore(score) : 'var(--surface-3)';
            const light = score >= 15; // deep cells need light text
            return (
              <g key={`${L}-${I}`}
                onClick={() => count > 0 && onCellClick?.(L, I)}
                style={{ cursor: count > 0 && onCellClick ? 'pointer' : 'default' }}>
                <title>{`${ar ? 'احتمال' : 'L'}=${L} × ${ar ? 'أثر' : 'I'}=${I} → ${count} ${ar ? 'مخاطرة' : 'risk(s)'}`}</title>
                <rect x={xFor(L)} y={yFor(I)} width={cell} height={cell} rx={7}
                  fill={fill} fillOpacity={count > 0 ? 0.95 : 1}
                  stroke="var(--surface)" strokeWidth={2} />
                <text x={cxFor(L)} y={cyFor(I)} textAnchor="middle" dominantBaseline="central"
                  className="tnum"
                  style={{ font: '700 15px var(--font-sans)', fill: count > 0 ? (light ? '#fff' : 'var(--ink-1)') : 'var(--ink-4)', pointerEvents: 'none' }}>
                  {count || '·'}
                </text>
              </g>
            );
          }),
        )}

        {/* Inherent→residual vectors */}
        {vectors.length > 0 && (
          <g className="viz-stagger">
            {vectors.map((m, i) => {
              const fromScore = m.fromL * m.fromI;
              const toScore = m.toL * m.toI;
              const good = toScore <= fromScore;
              const col = good ? 'var(--primary)' : 'var(--danger)';
              const x1 = cxFor(m.fromL), y1 = cyFor(m.fromI);
              const x2 = cxFor(m.toL), y2 = cyFor(m.toI);
              return (
                <line key={i} x1={x1} y1={y1} x2={x2} y2={y2}
                  stroke={col} strokeOpacity={0.7}
                  strokeWidth={Math.min(4, 1.4 + m.weight * 0.5)} strokeLinecap="round"
                  markerEnd={`url(#${good ? `arw-good-${uid}` : `arw-bad-${uid}`})`} />
              );
            })}
          </g>
        )}

        {/* Axis: impact numbers (left) */}
        {impacts.map((I) => (
          <text key={`yi-${I}`} x={PAD_START - 10} y={cyFor(I)} textAnchor="middle" dominantBaseline="central"
            style={{ font: '700 11px var(--font-sans)', fill: axisColor }}>{I}</text>
        ))}
        {/* Axis: likelihood numbers (below) */}
        {likelihoods.map((L) => (
          <text key={`xl-${L}`} x={cxFor(L)} y={gridW + 12} textAnchor="middle"
            style={{ font: '700 11px var(--font-sans)', fill: axisColor }}>{L}</text>
        ))}
        {/* Axis labels */}
        <text x={cxFor(3)} y={gridW + PAD_END - 2} textAnchor="middle"
          style={{ font: '600 10px var(--font-sans)', fill: axisColor, letterSpacing: '0.04em' }}>
          {ar ? 'الاحتمال ←' : 'Likelihood →'}
        </text>
      </svg>

      {/* Legend */}
      <div style={{ display: 'flex', justifyContent: 'center', gap: 14, marginTop: 8, flexWrap: 'wrap', fontSize: 11, color: 'var(--ink-3)' }}>
        <LegendSwatch color="var(--risk-crit)" label={ar ? 'حرج (≥٢٠)' : 'Critical (≥20)'} />
        <LegendSwatch color="var(--risk-high)" label={ar ? 'مرتفع (١٠-١٤)' : 'High (10-14)'} />
        <LegendSwatch color="var(--risk-low)" label={ar ? 'منخفض (<٥)' : 'Low (<5)'} />
        {vectors.length > 0 && (
          <>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
              <svg width="22" height="8"><line x1="1" y1="4" x2="16" y2="4" stroke="var(--primary)" strokeWidth="2" /><path d="M16,1 L21,4 L16,7 Z" fill="var(--primary)" /></svg>
              {ar ? 'تحسّن (كامن→متبقي)' : 'Improved (inherent→residual)'}
            </span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
              <svg width="22" height="8"><line x1="1" y1="4" x2="16" y2="4" stroke="var(--danger)" strokeWidth="2" /><path d="M16,1 L21,4 L16,7 Z" fill="var(--danger)" /></svg>
              {ar ? 'تدهور' : 'Deteriorated'}
            </span>
          </>
        )}
      </div>
    </div>
  );
}

function LegendSwatch({ color, label }: { color: string; label: string }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
      <span style={{ width: 12, height: 12, borderRadius: 3, background: color, display: 'inline-block' }} />
      {label}
    </span>
  );
}
