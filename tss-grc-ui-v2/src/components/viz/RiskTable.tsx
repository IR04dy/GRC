import { Badge, type BadgeTone } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';

export interface RiskRow {
  id: number | string;
  code: string;
  name: string;
  category: string;
  score: number;
  status: string;
  statusTone: BadgeTone;
  owner: string;
}

function scoreBg(s: number): string {
  if (s >= 16) return 'var(--risk-crit)';
  if (s >= 12) return 'var(--risk-vhigh)';
  if (s >= 8)  return 'var(--risk-high)';
  if (s >= 4)  return 'var(--risk-med)';
  return 'var(--risk-low)';
}
function scoreFg(s: number): string {
  return s >= 12 ? '#fff' : 'var(--ink-1)';
}

export function RiskTable({ rows }: { rows: RiskRow[] }) {
  const { t } = useI18n();
  const headers = [
    t('riskColId'),
    t('riskColName'),
    t('riskColCategory'),
    t('riskColScore'),
    t('riskColStatus'),
    t('riskColOwner'),
  ];

  return (
    <table className="risk-table">
      <thead>
        <tr>{headers.map(h => <th key={h}>{h}</th>)}</tr>
      </thead>
      <tbody>
        {rows.map(r => (
          <tr key={r.id}>
            <td className="mono">{r.code}</td>
            <td><div style={{ fontWeight: 600, color: 'var(--ink-1)' }}>{r.name}</div></td>
            <td><Badge tone="info">{r.category}</Badge></td>
            <td>
              <span className="score-pill" style={{ background: scoreBg(r.score), color: scoreFg(r.score) }}>
                {r.score}
              </span>
            </td>
            <td><Badge tone={r.statusTone} dot>{r.status}</Badge></td>
            <td style={{ color: 'var(--ink-2)' }}>{r.owner}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
