/**
 * S5c — the change KPI strip (§11 `ChangeAnalyticsPage`). Renders `changeApi.analytics()`;
 * CE-12 widens it to the full ITIL/ISO pack.
 */
import { BarChart3, KanbanSquare, ShieldCheck } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Progress } from '../../components/ui/Progress';
import type { ChangeAnalyticsDto } from '../../lib/api';
import { STATUS_TONE } from './changeVocabulary';

// ─── Analytics ─────────────────────────────────────────────────────────────
export function AnalyticsPanel({ analytics, t }: { analytics: ChangeAnalyticsDto | null; t: (k: string) => string }) {
  if (!analytics) return <Card><div className="changes-analytics-empty">{t('changeAnalyticsNoData')}</div></Card>;
  const status = analytics.byStatus, type = analytics.byType, outcome = analytics.byOutcome;
  const max = (m: Record<string, number>) => Math.max(1, ...Object.values(m));
  return (
    <div className="changes-analytics-grid">
      <Card title={t('changeAnalyticsStatus')} Icon={BarChart3}>
        {Object.entries(status).filter(([, v]) => v > 0).map(([k, v]) => (
          <Progress key={k} label={`${k.replace(/_/g, ' ')} · ${v}`} value={Math.round((v / max(status)) * 100)}
                    tone={STATUS_TONE[k] === 'danger' ? 'danger' : STATUS_TONE[k] === 'success' ? 'success' : STATUS_TONE[k] === 'warning' ? 'accent' : 'primary'} />
        ))}
      </Card>
      <Card title={t('changeAnalyticsType')} Icon={KanbanSquare}>
        {Object.entries(type).map(([k, v]) => (
          <Progress key={k} label={`${k} · ${v}`} value={Math.round((v / max(type)) * 100)}
                    tone={k === 'EMERGENCY' ? 'danger' : k === 'STANDARD' ? 'success' : 'primary'} />
        ))}
      </Card>
      <Card title={t('changeAnalyticsOutcome')} Icon={ShieldCheck}
            action={<Badge tone={analytics.successRatePct >= 90 ? 'success' : analytics.successRatePct >= 70 ? 'primary' : 'accent'}>{analytics.successRatePct}% {t('changeKpiSuccess')}</Badge>}>
        {Object.entries(outcome).map(([k, v]) => (
          <Progress key={k} label={`${k.replace(/_/g, ' ')} · ${v}`} value={Math.round((v / max(outcome)) * 100)}
                    tone={k === 'SUCCESS' ? 'success' : k === 'PARTIAL_SUCCESS' ? 'primary' : 'danger'} />
        ))}
      </Card>
    </div>
  );
}
