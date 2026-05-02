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

export function HeatMap({ data }: { data: HeatCell[] }) {
  const { t } = useI18n();
  const levels = t('heatLevels').split(',');
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
                return (
                  <td
                    key={c}
                    className="heatmap-cell"
                    style={{ background: colorFor(score) }}
                    title={`L${c} × I${r} = ${score}${cell ? ` (${cell.count})` : ''}`}
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
