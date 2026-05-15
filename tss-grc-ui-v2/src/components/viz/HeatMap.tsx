import { useI18n } from '../../lib/i18n';

export interface HeatCell { l: number; i: number; count: number; }

function colorFor(score: number): string {
  if (score <= 4)  return 'var(--risk-vlow)';
  if (score <= 8)  return 'var(--risk-low)';
  if (score <= 12) return 'var(--risk-med)';
  if (score <= 16) return 'var(--risk-high)';
  if (score <= 20) return 'var(--risk-vhigh)';
  return 'var(--risk-crit)';
}

export interface HeatMapProps {
  data: HeatCell[];
  onCellClick?: (l: number, i: number) => void;
  selected?: { l: number; i: number } | null;
}

export function HeatMap({ data, onCellClick, selected }: HeatMapProps) {
  const { t } = useI18n();
  // Split on either Latin (,) or Arabic (،) comma so the source
  // string can use the locale-native separator. The trim() handles
  // the Arabic-prose convention of a space after the comma.
  const levels = t('heatLevels').split(/[,،]/).map(s => s.trim());
  const rows = [5, 4, 3, 2, 1];

  return (
    <div className="heatmap-wrap">
      <div className="heatmap-axis-y">{t('riskAxisImpact')}</div>
      <table className="heatmap-table">
        <tbody>
          {rows.map(r => (
            <tr key={r}>
              <th className="heatmap-row-lbl">{levels[r - 1]}</th>
              {[1, 2, 3, 4, 5].map(c => {
                const cell = data.find(d => d.l === c && d.i === r);
                const score = c * r;
                const isSelected = selected != null && selected.l === c && selected.i === r;
                const clickable = !!onCellClick;
                return (
                  <td
                    key={c}
                    className="heatmap-cell"
                    style={{
                      background: colorFor(score),
                      cursor: clickable ? 'pointer' : 'default',
                      outline: isSelected ? '3px solid var(--accent-700)' : undefined,
                      outlineOffset: isSelected ? '-3px' : undefined,
                      transition: 'transform var(--dur-fast) var(--ease-standard)',
                      transform: isSelected ? 'scale(1.04)' : undefined,
                    }}
                    role={clickable ? 'button' : undefined}
                    tabIndex={clickable ? 0 : undefined}
                    onClick={clickable ? () => onCellClick!(c, r) : undefined}
                    onKeyDown={clickable ? (e) => {
                      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onCellClick!(c, r); }
                    } : undefined}
                    title={`L${c} × I${r} = ${score}${cell ? ` (${cell.count})` : ''}`}
                    aria-pressed={clickable ? isSelected : undefined}
                  >
                    {cell
                      ? <span className="heatmap-count">{cell.count}</span>
                      : <span className="heatmap-score">{score}</span>}
                  </td>
                );
              })}
            </tr>
          ))}
          <tr>
            <td />
            {levels.map(l => <td key={l} className="heatmap-col-lbl">{l}</td>)}
          </tr>
        </tbody>
      </table>
      <div className="heatmap-axis-x">{t('riskAxisLikelihood')}</div>
    </div>
  );
}
