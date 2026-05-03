import type { ReactNode } from 'react';
import { AlertTriangle, Flame, ClipboardCheck, ShieldCheck, Plus, Check, Activity, FileText, Grid3x3, BarChart3, CheckSquare } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Progress } from '../../components/ui/Progress';
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { BarChart3D } from '../../components/viz/BarChart3D';
import type { DashboardStats } from '../../lib/api';

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

// ─── Built-in widgets ──────────────────────────────────────────────────────
export const WIDGETS: DashboardWidget[] = [
  // KPIs
  { id: 'kpi-total-risks',    titleKey: 'kpiTotalRisks',    defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t }) => <Kpi Icon={AlertTriangle} label={t('kpiTotalRisks')} value={stats.totalRisks} tone="primary" /> },
  { id: 'kpi-critical-risks', titleKey: 'kpiCriticalRisks', defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t }) => <Kpi Icon={Flame} label={t('kpiCriticalRisks')} value={stats.criticalRisks ?? stats.openCriticalRisks} tone="danger" /> },
  { id: 'kpi-compliance',     titleKey: 'kpiCompliance',    defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t }) => <Kpi Icon={ClipboardCheck} label={t('kpiCompliance')} value={`${stats.complianceScore}%`} tone="success" /> },
  { id: 'kpi-active-controls',titleKey: 'kpiActiveControls',defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t }) => <Kpi Icon={ShieldCheck} label={t('kpiActiveControls')} value={stats.activeUsers} tone="info" /> },

  // Charts
  { id: 'heatmap',           titleKey: 'cardHeatTitle',    defaultSize: 'md', category: 'chart',
    render: ({ heat, t }) =>
      <Card title={t('cardHeatTitle')} Icon={Grid3x3} action={<Badge tone="primary">5 × 5</Badge>}>
        <HeatMap data={heat} />
      </Card> },
  { id: 'risk-by-severity',  titleKey: 'cardByCategory',   defaultSize: 'md', category: 'chart',
    render: ({ stats, t }) =>
      <Card title={t('cardByCategory')} Icon={BarChart3}>
        <BarChart3D items={[
          { label: 'Critical', value: stats.criticalRisks, color: 'var(--danger)', shade: '#7E2A25' },
          { label: 'High',     value: stats.highRisks,     color: 'var(--cat-6)',  shade: '#5C3220' },
          { label: 'Medium',   value: stats.mediumRisks,   color: 'var(--cat-2)',  shade: '#7A5612' },
          { label: 'Low',      value: stats.lowRisks,      color: 'var(--cat-3)',  shade: '#324754' },
        ]} />
      </Card> },

  // Progress
  { id: 'frameworks',        titleKey: 'cardFrameworks',   defaultSize: 'md', category: 'progress',
    render: ({ stats, t }) =>
      <Card title={t('cardFrameworks')} Icon={CheckSquare} action={<Badge tone="primary">Power BI</Badge>}>
        <Progress label="ISO 27001" value={stats.complianceScore} tone="primary" />
        <Progress label="NCA ECC"   value={Math.max(0, stats.complianceScore - 25)} tone="accent" />
        <Progress label="PDPL"      value={Math.max(0, stats.complianceScore - 49)} tone="danger" />
        <Progress label="ISO 22301" value={Math.max(0, stats.complianceScore - 16)} tone="success" />
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
