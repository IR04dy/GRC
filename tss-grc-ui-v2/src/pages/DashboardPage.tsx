import { useEffect, useState } from 'react';
import { AlertTriangle, Flame, ClipboardCheck, ShieldCheck, RefreshCw, Download, Plus, Grid3x3, BarChart3, CheckSquare, Activity, FileText, Check } from 'lucide-react';
import { useI18n } from '../lib/i18n';
import { Shell } from '../components/layout/Shell';
import { PageHeader } from '../components/layout/PageHeader';
import { Kpi } from '../components/ui/Kpi';
import { Card } from '../components/ui/Card';
import { Progress } from '../components/ui/Progress';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { HeatMap, type HeatCell } from '../components/viz/HeatMap';
import { BarChart3D } from '../components/viz/BarChart3D';
import { dashboardApi, type DashboardStats } from '../lib/api';
import { isAuthenticated } from '../lib/auth';

const FALLBACK_STATS: DashboardStats = {
  totalRisks: 42, criticalRisks: 7, highRisks: 12, mediumRisks: 18, lowRisks: 5,
  openRisks: 28, closedRisks: 14, totalUsers: 47, activeUsers: 42,
  openCriticalRisks: 7, openHighRisks: 12, openIncidents: 3,
  complianceScore: 87, pendingTasks: 4,
};

const FALLBACK_HEAT: HeatCell[] = [
  { l: 5, i: 5, count: 1 }, { l: 4, i: 5, count: 1 }, { l: 5, i: 4, count: 2 },
  { l: 3, i: 4, count: 3 }, { l: 4, i: 3, count: 4 }, { l: 2, i: 3, count: 5 },
  { l: 3, i: 2, count: 6 }, { l: 1, i: 2, count: 4 }, { l: 2, i: 1, count: 7 }, { l: 1, i: 1, count: 9 },
];

export function DashboardPage() {
  const { t } = useI18n();
  const [stats, setStats] = useState<DashboardStats>(FALLBACK_STATS);
  const [heat, setHeat] = useState<HeatCell[]>(FALLBACK_HEAT);
  const [isDemo, setIsDemo] = useState<boolean>(!isAuthenticated());
  const [loading, setLoading] = useState<boolean>(isAuthenticated());

  async function load() {
    if (!isAuthenticated()) { setIsDemo(true); setLoading(false); return; }
    setLoading(true);
    try {
      const [s, h] = await Promise.all([
        dashboardApi.stats().catch(() => null),
        dashboardApi.heatmap().catch(() => null),
      ]);
      if (s) { setStats(s); setIsDemo(false); }
      if (Array.isArray(h)) setHeat(h as HeatCell[]);
    } finally { setLoading(false); }
  }

  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  return (
    <Shell title={t('itemDashboard')} sub={t('brandSub')}>
      <PageHeader
        eyebrow={t('dashEyebrow')}
        title={t('dashTitle')}
        sub={t('dashSub')}
        actions={
          <>
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={RefreshCw} onClick={load} disabled={loading}>{t('dashRefresh')}</Button>
            <Button variant="secondary" Icon={Download}>{t('dashExport')}</Button>
          </>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={AlertTriangle}  label={t('kpiTotalRisks')}     value={stats.totalRisks} tone="primary" />
        <Kpi Icon={Flame}          label={t('kpiCriticalRisks')}  value={stats.criticalRisks ?? stats.openCriticalRisks} tone="danger" />
        <Kpi Icon={ClipboardCheck} label={t('kpiCompliance')}     value={`${stats.complianceScore}%`} tone="success" />
        <Kpi Icon={ShieldCheck}    label={t('kpiActiveControls')} value={stats.activeUsers} tone="info" />
      </div>

      <div className="dash-grid">
        <Card title={t('cardHeatTitle')} Icon={Grid3x3} action={<Badge tone="primary">5 × 5</Badge>}>
          <HeatMap data={heat} />
        </Card>
        <Card title={t('cardByCategory')} Icon={BarChart3}>
          <BarChart3D items={[
            { label: 'Critical', value: stats.criticalRisks, color: 'var(--danger)', shade: '#7E2A25' },
            { label: 'High',     value: stats.highRisks,     color: 'var(--cat-6)',  shade: '#5C3220' },
            { label: 'Medium',   value: stats.mediumRisks,   color: 'var(--cat-2)',  shade: '#7A5612' },
            { label: 'Low',      value: stats.lowRisks,      color: 'var(--cat-3)',  shade: '#324754' },
          ]} />
        </Card>
      </div>

      <div className="dash-grid">
        <Card title={t('cardFrameworks')} Icon={CheckSquare} action={<Badge tone="primary">Power BI</Badge>}>
          <Progress label="ISO 27001"  value={stats.complianceScore} tone="primary" />
          <Progress label="NCA ECC"    value={Math.max(0, stats.complianceScore - 25)} tone="accent" />
          <Progress label="PDPL"       value={Math.max(0, stats.complianceScore - 49)} tone="danger" />
          <Progress label="ISO 22301"  value={Math.max(0, stats.complianceScore - 16)} tone="success" />
        </Card>
        <Card title={t('cardActivity')} Icon={Activity}>
          <ActivityFeed />
        </Card>
      </div>
    </Shell>
  );
}

function ActivityFeed() {
  const items = [
    { Icon: Plus,           color: 'var(--success-fg)', text: 'New risk R-0042 added by Sara',     time: '2m' },
    { Icon: Check,          color: 'var(--primary)',    text: 'Control AC-3 evidence approved',    time: '14m' },
    { Icon: AlertTriangle,  color: 'var(--danger-fg)',  text: 'KRI threshold breached: failed logins', time: '1h' },
    { Icon: FileText,       color: 'var(--accent-700)', text: 'Policy v2.4 sent for attestation',  time: '3h' },
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
