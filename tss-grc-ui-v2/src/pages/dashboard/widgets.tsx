import type { ReactNode } from 'react';
import { AlertTriangle, Flame, ClipboardCheck, ShieldCheck, Plus, Check, Activity, FileText, Grid3x3, BarChart3, CheckSquare } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Progress } from '../../components/ui/Progress';
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { BarChart3D } from '../../components/viz/BarChart3D';
import type { DashboardStats } from '../../lib/api';
import { useNavigate } from 'react-router-dom';

export type WidgetSize = 'sm' | 'md' | 'lg';

export interface DashboardWidget {
  id: string;
  /** Translation key shown in the customise panel + as a default Card title. */
  titleKey: string;
  /** Default size when first added. */
  defaultSize: WidgetSize;
  /** Whether this widget is a single KPI (rendered without a Card wrapper). */
  isKpi?: boolean;
  /** Tone hint used by the customise panel chip. */
  category: 'kpi' | 'chart' | 'list' | 'progress';
  render: (ctx: WidgetContext) => ReactNode;
}

export interface WidgetContext {
  stats: DashboardStats;
  heat: HeatCell[];
  t: (k: string) => string;
}

/**
 * Generates a mock 12-point trend that lands on the current value.
 * Replace with real `/api/v1/dashboard/kpi-trend?metric=...` calls when the
 * backend endpoint ships.
 */
function mockTrend(current: number, volatility = 0.18): number[] {
  const n = 12;
  const series: number[] = [];
  let v = current * (1 - volatility);
  for (let i = 0; i < n - 1; i++) {
    // Random walk biased toward `current` so the line drifts to today's value
    const noise = (Math.random() - 0.5) * volatility * Math.max(1, current * 0.3);
    const pull  = (current - v) * 0.18;
    v = Math.max(0, v + pull + noise);
    series.push(Math.round(v * 100) / 100);
  }
  series.push(current);
  return series;
}

// ─── Built-in widgets ──────────────────────────────────────────────────────
export const WIDGETS: DashboardWidget[] = [
  // KPIs — every tile drills into a filtered list (Tier 1)
  { id: 'kpi-total-risks',    titleKey: 'kpiTotalRisks',    defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t }) => <Kpi Icon={AlertTriangle} label={t('kpiTotalRisks')} value={stats.totalRisks} tone="primary" spark={mockTrend(stats.totalRisks)} to="/risk" /> },
  { id: 'kpi-critical-risks', titleKey: 'kpiCriticalRisks', defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t }) => <Kpi Icon={Flame} label={t('kpiCriticalRisks')} value={stats.criticalRisks ?? stats.openCriticalRisks} tone="danger" spark={mockTrend(stats.criticalRisks ?? stats.openCriticalRisks ?? 0, 0.3)} to="/risk?level=CRITICAL" /> },
  { id: 'kpi-compliance',     titleKey: 'kpiCompliance',    defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t }) => <Kpi Icon={ClipboardCheck} label={t('kpiCompliance')} value={`${stats.complianceScore}%`} tone="success" spark={mockTrend(stats.complianceScore, 0.06)} to="/compliance?tab=scorecard" /> },
  { id: 'kpi-active-controls',titleKey: 'kpiActiveControls',defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t }) => <Kpi Icon={ShieldCheck} label={t('kpiActiveControls')} value={stats.activeUsers} tone="info" spark={mockTrend(stats.activeUsers, 0.08)} to="/compliance?tab=controls" /> },

  // Charts
  { id: 'heatmap',           titleKey: 'cardHeatTitle',    defaultSize: 'md', category: 'chart',
    render: ({ heat, t }) =>
      <Card title={t('cardHeatTitle')} Icon={Grid3x3} action={<Badge tone="primary">5 × 5</Badge>}>
        <HeatmapDrill heat={heat} />
      </Card> },
  { id: 'risk-by-severity',  titleKey: 'cardByCategory',   defaultSize: 'md', category: 'chart',
    render: ({ stats, t }) =>
      <Card title={t('cardByCategory')} Icon={BarChart3}>
        <RiskBySeverityDrill stats={stats} />
      </Card> },

  // Progress
  { id: 'frameworks',        titleKey: 'cardFrameworks',   defaultSize: 'md', category: 'progress',
    render: ({ stats, t }) =>
      <Card title={t('cardFrameworks')} Icon={CheckSquare} action={<Badge tone="primary">Power BI</Badge>}>
        <FrameworksDrill stats={stats} />
      </Card> },

  // Activity (mock)
  { id: 'activity',          titleKey: 'cardActivity',     defaultSize: 'md', category: 'list',
    render: ({ t }) =>
      <Card title={t('cardActivity')} Icon={Activity}>
        <ActivityFeed />
      </Card> },
];

export const WIDGETS_BY_ID: Record<string, DashboardWidget> = Object.fromEntries(WIDGETS.map(w => [w.id, w]));

export const DEFAULT_LAYOUT: Array<{ id: string; size: WidgetSize }> = [
  { id: 'kpi-total-risks',    size: 'sm' },
  { id: 'kpi-critical-risks', size: 'sm' },
  { id: 'kpi-compliance',     size: 'sm' },
  { id: 'kpi-active-controls',size: 'sm' },
  { id: 'heatmap',            size: 'md' },
  { id: 'risk-by-severity',   size: 'md' },
  { id: 'frameworks',         size: 'md' },
  { id: 'activity',           size: 'md' },
];

/** Heatmap that drills the risk register on cell click. */
function HeatmapDrill({ heat }: { heat: HeatCell[] }) {
  const navigate = useNavigate();
  return <HeatMap data={heat} onCellClick={(l, i) => navigate(`/risk?l=${l}&i=${i}`)} />;
}

/** "Risks by severity" 3-D bar chart that drills into the register filtered by level. */
function RiskBySeverityDrill({ stats }: { stats: DashboardStats }) {
  const navigate = useNavigate();
  return <BarChart3D
    onItemClick={(it) => navigate(`/risk?level=${it.label.toUpperCase()}`)}
    items={[
      { label: 'Critical', value: stats.criticalRisks, color: 'var(--danger)', shade: '#7E2A25' },
      { label: 'High',     value: stats.highRisks,     color: 'var(--cat-6)',  shade: '#5C3220' },
      { label: 'Medium',   value: stats.mediumRisks,   color: 'var(--cat-2)',  shade: '#7A5612' },
      { label: 'Low',      value: stats.lowRisks,      color: 'var(--cat-3)',  shade: '#324754' },
    ]} />;
}

/** Framework readiness bars drill into the framework sheet. */
function FrameworksDrill({ stats }: { stats: DashboardStats }) {
  const navigate = useNavigate();
  const go = (code: string) => () => navigate(`/compliance?tab=frameworks&fw=${encodeURIComponent(code)}`);
  return <>
    <Progress label="ISO 27001" value={stats.complianceScore} tone="primary" onClick={go('ISO_27001')} />
    <Progress label="NCA ECC"   value={Math.max(0, stats.complianceScore - 25)} tone="accent" onClick={go('NCA_ECC')} />
    <Progress label="PDPL"      value={Math.max(0, stats.complianceScore - 49)} tone="danger" onClick={go('PDPL')} />
    <Progress label="ISO 22301" value={Math.max(0, stats.complianceScore - 16)} tone="success" onClick={go('ISO_22301')} />
  </>;
}

function ActivityFeed() {
  const items = [
    { Icon: Plus,          color: 'var(--success-fg)', text: 'New risk R-0042 added by Sara',          time: '2m' },
    { Icon: Check,         color: 'var(--primary)',    text: 'Control AC-3 evidence approved',         time: '14m' },
    { Icon: AlertTriangle, color: 'var(--danger-fg)',  text: 'KRI threshold breached: failed logins', time: '1h' },
    { Icon: FileText,      color: 'var(--accent-700)', text: 'Policy v2.4 sent for attestation',       time: '3h' },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 13 }}>
      {items.map((it, k) => (
        <div key={k} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '7px 0', borderBottom: '1px solid var(--border)' }}>
          <div style={{ width: 28, height: 28, borderRadius: 8, background: 'var(--surface-3)', color: it.color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <it.Icon size={14} />
          </div>
          <div style={{ flex: 1, color: 'var(--ink-1)' }}>{it.text}</div>
          <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--ink-4)' }}>{it.time}</div>
        </div>
      ))}
    </div>
  );
}
