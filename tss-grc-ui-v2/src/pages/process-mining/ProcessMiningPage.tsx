import { useMemo, type CSSProperties } from 'react';
import {
  Activity, AlertOctagon, Clock, ShieldCheck, TrendingDown, Workflow, Hourglass,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import {
  computeStageMetrics, findStuckInstances, workflowLeaderboard, aggregateProcessKpis,
  breachTone, breachBackgroundColor,
} from '../../lib/processMining';
import { ESCALATION_TONE } from '../../lib/workflows';

/**
 * ProcessMiningPage — Sprint 6 follow-on (A1).
 *
 * Bottleneck heatmap + stuck-instance leaderboard for everything alive in
 * the cross-module SLA pulse. Three sections:
 *   1. KPI strip — total in-flight / within SLA / stuck / avg age / avg breach
 *   2. Workflow leaderboard — sorted worst breach → best
 *   3. Stage heatmap — workflow rows × stage columns coloured by breach ratio
 *   4. Top stuck instances — actionable list
 */
export function ProcessMiningPage() {
  const { t } = useI18n();

  const kpis = useMemo(() => aggregateProcessKpis(), []);
  const leaderboard = useMemo(() => workflowLeaderboard(), []);
  const stageMetrics = useMemo(() => computeStageMetrics(), []);
  const stuck = useMemo(() => findStuckInstances().slice(0, 12), []);

  // Build a workflow × stage matrix from stage metrics.
  const matrix = useMemo(() => {
    const workflowSet = new Set<string>();
    const stageSet = new Set<string>();
    const cells = new Map<string, typeof stageMetrics[number]>();
    for (const m of stageMetrics) {
      workflowSet.add(m.workflow);
      stageSet.add(m.stage);
      cells.set(`${m.workflow}::${m.stage}`, m);
    }
    return {
      workflows: Array.from(workflowSet),
      stages: Array.from(stageSet),
      cells,
    };
  }, [stageMetrics]);

  return (
    <Shell title={t('pmTitle')} sub={t('pmSub')}>
      <PageHeader
        eyebrow={t('pmEyebrow')}
        title={t('pmTitle')}
        sub={t('pmPageSub')}
      />

      {/* KPIs */}
      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Activity}      label={t('pmKpiTotal')}    value={String(kpis.totalInFlight)} tone="primary" to="/workflows" />
        <Kpi Icon={ShieldCheck}   label={t('pmKpiWithin')}    value={String(kpis.withinSla)} tone="success" to="/workflows" />
        <Kpi Icon={AlertOctagon}  label={t('pmKpiStuck')}     value={String(kpis.stuck)} tone={kpis.stuck > 0 ? 'danger' : 'success'} to="/workflows" />
        <Kpi Icon={Activity}      label={t('pmKpiPct')}       value={`${kpis.withinSlaPct}%`} tone={kpis.withinSlaPct >= 80 ? 'success' : 'warning'} to="/workflows" />
        <Kpi Icon={Clock}         label={t('pmKpiAvgAge')}    value={`${kpis.avgAgeDays}d`} tone="info" to="/workflows" />
        <Kpi Icon={TrendingDown}  label={t('pmKpiAvgBreach')} value={`${Math.round(kpis.avgBreach * 100)}%`} tone={kpis.avgBreach >= 1 ? 'danger' : kpis.avgBreach >= 0.7 ? 'warning' : 'success'} to="/workflows" />
      </div>

      {/* Workflow leaderboard */}
      <Card title={t('pmLeaderTitle')} Icon={Workflow}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 10 }}>
          {t('pmLeaderSub')}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {leaderboard.map(w => {
            const tone = breachTone(w.avgBreach);
            return (
              <div key={w.workflow} style={{
                padding: '10px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '1fr 90px 100px 110px', gap: 12, alignItems: 'center',
              }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>{w.workflow}</div>
                  {w.worstInstance && w.avgBreach > 1 && (
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                      {t('pmWorst')}: <span className="mono">{w.worstInstance.entityRef}</span> · {w.worstInstance.title}
                    </div>
                  )}
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{w.total} {t('pmInstances')}</div>
                <Badge tone={w.withinSlaPct >= 80 ? 'success' : w.withinSlaPct >= 60 ? 'warning' : 'danger'}>
                  {w.withinSlaPct}% {t('pmInSla')}
                </Badge>
                <Badge tone={tone}>
                  <Hourglass size={11} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />
                  {Math.round(w.avgBreach * 100)}% {t('pmBreachLabel')}
                </Badge>
              </div>
            );
          })}
        </div>
      </Card>

      {/* Stage heatmap */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('pmHeatmapTitle')} Icon={Activity}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 10 }}>
            {t('pmHeatmapSub')}
          </div>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11 }}>
              <thead>
                <tr>
                  <th style={th}>{t('pmTblWorkflow')}</th>
                  {matrix.stages.map(s => (
                    <th key={s} style={{ ...th, fontFamily: 'var(--font-mono)', textAlign: 'center', fontSize: 10 }}>{s}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {matrix.workflows.map(w => (
                  <tr key={w} style={{ borderBottom: '1px solid var(--border)' }}>
                    <td style={{ ...td, fontWeight: 600 }}>{w}</td>
                    {matrix.stages.map(s => {
                      const cell = matrix.cells.get(`${w}::${s}`);
                      if (!cell) return <td key={s} style={td}> </td>;
                      const bg = breachBackgroundColor(cell.avgBreach);
                      return (
                        <td key={s} style={{
                          ...td, textAlign: 'center', background: bg,
                          minWidth: 60,
                        }}
                          title={`${cell.count} instance${cell.count === 1 ? '' : 's'} · avg ${cell.avgAgeDays}d / ${cell.avgSlaDays}d (${Math.round(cell.avgBreach * 100)}%)`}>
                          <div style={{ fontWeight: 700, fontSize: 12 }}>{cell.count}</div>
                          <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)' }}>
                            {Math.round(cell.avgBreach * 100)}%
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ display: 'flex', gap: 12, marginTop: 12, alignItems: 'center', fontSize: 11, color: 'var(--ink-3)' }}>
            <span>{t('pmLegend')}:</span>
            <span style={{ width: 14, height: 14, background: breachBackgroundColor(0.3), border: '1px solid var(--border)', display: 'inline-block' }} /> {t('pmLegendOK')}
            <span style={{ width: 14, height: 14, background: breachBackgroundColor(0.8), border: '1px solid var(--border)', display: 'inline-block' }} /> {t('pmLegendWarn')}
            <span style={{ width: 14, height: 14, background: breachBackgroundColor(1.5), border: '1px solid var(--border)', display: 'inline-block' }} /> {t('pmLegendBreached')}
          </div>
        </Card>
      </div>

      {/* Stuck instances */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('pmStuckTitle')} Icon={AlertOctagon}
          action={<Badge tone={stuck.length > 0 ? 'danger' : 'success'}>{stuck.length}</Badge>}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 10 }}>
            {t('pmStuckSub')}
          </div>
          {stuck.length === 0
            ? <div style={{ padding: 16, fontSize: 12, color: 'var(--success-fg)', textAlign: 'center', background: 'var(--success-bg)', borderRadius: 'var(--r-sm)' }}>
                {t('pmStuckEmpty')}
              </div>
            : <div style={{ display: 'flex', flexDirection: 'column' }}>
                {stuck.map(s => (
                  <div key={s.instance.id} style={{
                    padding: '10px 0', borderBottom: '1px dashed var(--border)',
                    display: 'grid', gridTemplateColumns: '1fr auto auto', gap: 10, alignItems: 'center',
                  }}>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                        {s.instance.title}
                      </div>
                      <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                        <span className="mono">{s.instance.module} · {s.instance.entityRef}</span>
                        {' · '}<span>{t('pmStuckCurrent')}: <span className="mono">{s.instance.currentStage}</span></span>
                        {' · '}<span>{s.instance.workflow}</span>
                      </div>
                    </div>
                    <Badge tone="danger">+{s.daysOver}d {t('pmOverSla')}</Badge>
                    <Badge tone={ESCALATION_TONE[s.instance.escalation]}>
                      {Math.round(s.breachRatio * 100)}%
                    </Badge>
                  </div>
                ))}
              </div>}
        </Card>
      </div>
    </Shell>
  );
}

const th: CSSProperties = {
  padding: '8px 10px',
  textAlign: 'start',
  fontSize: 10,
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
  color: 'var(--ink-3)',
  background: 'var(--surface-2)',
  borderBottom: '2px solid var(--ink-3)',
};
const td: CSSProperties = {
  padding: '8px 10px',
  verticalAlign: 'middle',
  lineHeight: 1.5,
};
