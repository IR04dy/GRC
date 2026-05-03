/**
 * Generic coverage matrix — rows × columns of counts shaded by status.
 * Used by:
 *   - Authority Workspace (rows = frameworks owned, cols = control statuses)
 *   - Authorities Index   (rows = authorities, cols = domains served)
 *
 * Each cell is shaded by `tone` and intensified by count (white = 0,
 * full saturation = max in matrix).
 */

export interface MatrixCell {
  /** Tone family for the cell — picks the colour. */
  tone: 'success' | 'warning' | 'accent' | 'danger' | 'neutral' | 'info' | 'primary';
  /** Numeric value rendered in the cell. */
  value: number;
  /** Optional href if the cell should link somewhere. */
  href?: string;
  /** Optional tooltip override. */
  title?: string;
}

export interface MatrixRow {
  label: string;
  /** Optional sublabel rendered below the row label. */
  sublabel?: string;
  /** Optional href on the row label. */
  href?: string;
  cells: MatrixCell[];
}

const TONE_BG: Record<MatrixCell['tone'], string> = {
  success: 'rgba(45, 156, 93, ALPHA)',
  warning: 'rgba(193, 134, 38, ALPHA)',
  accent:  'rgba(180, 96, 60, ALPHA)',
  danger:  'rgba(180, 60, 50, ALPHA)',
  neutral: 'rgba(120, 120, 120, ALPHA)',
  info:    'rgba(60, 130, 180, ALPHA)',
  primary: 'rgba(20, 90, 90, ALPHA)',
};
const TONE_FG: Record<MatrixCell['tone'], string> = {
  success: 'var(--success-fg)',
  warning: 'var(--accent-700)',
  accent:  'var(--accent-700)',
  danger:  'var(--danger-fg)',
  neutral: 'var(--ink-2)',
  info:    'var(--ink-1)',
  primary: 'var(--primary-700)',
};

export function CoverageMatrix({
  rows, columnLabels, rowHeaderLabel, max,
}: {
  rows: MatrixRow[];
  columnLabels: string[];
  rowHeaderLabel: string;
  /** Override max-value for shading; otherwise computed from the cells. */
  max?: number;
}) {
  if (rows.length === 0) return null;

  // Auto-compute max if not provided
  const computedMax = max ?? Math.max(1, ...rows.flatMap(r => r.cells.map(c => c.value)));

  return (
    <div style={{ overflowX: 'auto', paddingBottom: 4 }}>
      <table style={{ borderCollapse: 'collapse', minWidth: '100%', fontSize: 12 }}>
        <thead>
          <tr>
            <th style={{
              textAlign: 'start', padding: '6px 10px',
              fontSize: 11, fontWeight: 700, color: 'var(--ink-3)',
              textTransform: 'uppercase', letterSpacing: '0.04em',
              borderBottom: '1px solid var(--border)',
              position: 'sticky', insetInlineStart: 0,
              background: 'var(--surface-1)', zIndex: 1,
            }}>{rowHeaderLabel}</th>
            {columnLabels.map(c => (
              <th key={c} style={{
                textAlign: 'center', padding: '6px 8px',
                fontSize: 10, fontWeight: 700, color: 'var(--ink-3)',
                textTransform: 'uppercase', letterSpacing: '0.04em',
                borderBottom: '1px solid var(--border)',
                whiteSpace: 'nowrap',
              }}>{c}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, ri) => (
            <tr key={ri}>
              <th style={{
                textAlign: 'start', padding: '8px 10px',
                fontWeight: 600, color: 'var(--ink-1)',
                borderBottom: '1px solid var(--border)',
                position: 'sticky', insetInlineStart: 0,
                background: 'var(--surface-1)', zIndex: 1,
                whiteSpace: 'nowrap',
              }}>
                {row.href
                  ? <a href={row.href} style={{ color: 'inherit', textDecoration: 'none' }}>{row.label}</a>
                  : row.label}
                {row.sublabel && (
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', fontWeight: 400, marginTop: 1 }}>
                    {row.sublabel}
                  </div>
                )}
              </th>
              {row.cells.map((cell, ci) => {
                const intensity = cell.value === 0 ? 0 : Math.max(0.15, Math.min(1, cell.value / computedMax));
                const bg = TONE_BG[cell.tone].replace('ALPHA', String(intensity * 0.85));
                const fg = TONE_FG[cell.tone];
                const tooltip = cell.title ?? `${row.label} · ${columnLabels[ci]}: ${cell.value}`;
                return (
                  <td key={ci}
                    title={tooltip}
                    style={{
                      textAlign: 'center', padding: '8px 6px',
                      borderBottom: '1px solid var(--border)',
                      background: cell.value === 0 ? 'transparent' : bg,
                      color: cell.value === 0 ? 'var(--ink-4)' : fg,
                      fontFamily: 'var(--font-mono)',
                      fontWeight: cell.value === 0 ? 400 : 700,
                      fontSize: 13,
                      cursor: cell.href ? 'pointer' : 'default',
                      transition: 'transform var(--dur-fast) var(--ease-standard)',
                      minWidth: 56,
                    }}
                    onMouseEnter={(e) => { if (cell.href) (e.currentTarget as HTMLElement).style.transform = 'scale(1.05)'; }}
                    onMouseLeave={(e) => { if (cell.href) (e.currentTarget as HTMLElement).style.transform = ''; }}
                    onClick={cell.href ? () => { window.location.href = cell.href!; } : undefined}
                  >
                    {cell.value === 0 ? '·' : cell.value}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
