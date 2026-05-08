import type { ReactNode } from 'react';
import { AlertTriangle, Flame, ClipboardCheck, ShieldCheck, Plus, Check, Activity, FileText, Grid3x3, BarChart3, CheckSquare, Award, Inbox, Clock, GitBranch } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Progress } from '../../components/ui/Progress';
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { BarChart3D } from '../../components/viz/BarChart3D';
import type { DashboardStats } from '../../lib/api';
import { useNavigate } from 'react-router-dom';
import {
  loadNitaqatState, computeSaudization, classifyNitaqatBand, nitaqatMeta, NITAQAT_BANDS,
} from '../../lib/nitaqat';
import { useIntake, hoursUntilSla } from '../../lib/intake';
import { VENDORS } from '../tprm/mockTprm';
import { VENDOR_TPRM_META } from '../tprm/mockTprm';
import { reviewBucket } from '../../lib/vendorOnboarding';
import { CHANGE_REQUESTS } from '../changes/mockChanges';

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

  // ── DD-87: Saudi compliance posture surfaces ────────────────────────
  { id: 'saudi-posture',     titleKey: 'dashSaudiPostureTitle', defaultSize: 'lg', category: 'progress',
    render: ({ t }) =>
      <Card title={t('dashSaudiPostureTitle')} Icon={Award}>
        <SaudiPostureWidget t={t} />
      </Card> },
  { id: 'kpi-open-intakes',           titleKey: 'dashKpiOpenIntakes',           defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ t }) => <OpenIntakesKpi t={t} /> },
  { id: 'kpi-overdue-reassessments',  titleKey: 'dashKpiOverdueReassessments',  defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ t }) => <OverdueReassessmentsKpi t={t} /> },
  { id: 'kpi-cab-queue',              titleKey: 'dashKpiCabQueue',              defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ t }) => <CabQueueKpi t={t} /> },
];

export const WIDGETS_BY_ID: Record<string, DashboardWidget> = Object.fromEntries(WIDGETS.map(w => [w.id, w]));

export const DEFAULT_LAYOUT: Array<{ id: string; size: WidgetSize }> = [
  // DD-87: lead with the Saudi compliance posture strip — first thing
  // a Saudi-tenant auditor or risk lead lands on.
  { id: 'saudi-posture',              size: 'lg' },
  // DD-87: front-door + workflow KPIs surface the modules that previously
  // only showed up under deep tabs (Services Center, TPRM reassessment, CAB).
  { id: 'kpi-open-intakes',           size: 'sm' },
  { id: 'kpi-overdue-reassessments',  size: 'sm' },
  { id: 'kpi-cab-queue',              size: 'sm' },
  { id: 'kpi-critical-risks',         size: 'sm' },
  // Existing tier-1 KPIs.
  { id: 'kpi-total-risks',    size: 'sm' },
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

// ─── DD-87: Saudi compliance posture + workflow KPIs ──────────────────
//
// These widgets make the recently-shipped Nitaqat/HRSD, intake substrate,
// vendor onboarding, and CAB modules visible from the home dashboard.
// Without them the user would have to dig into deep tabs to know any of
// this exists.

/** Hero strip: current Nitaqat band, Saudization %, ladder visualisation,
 *  click to land on the HRSD authority workspace where the full panel +
 *  process initiation tiles live. */
function SaudiPostureWidget({ t }: { t: (k: string) => string }) {
  // Read locale from URL hash isn't viable here; use document direction as
  // a proxy. Better long-term: thread `lang` through WidgetContext, but
  // that ripples into every widget. For now we read from documentElement.
  const isAr = typeof document !== 'undefined' && document.documentElement.dir === 'rtl';
  const state = loadNitaqatState();
  const saudization = computeSaudization(state);
  const band = classifyNitaqatBand(saudization);
  const meta = nitaqatMeta(band);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Link to="/authority/HRSD" style={{
        display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16,
        textDecoration: 'none', color: 'inherit',
      }}>
        <div style={{
          padding: 16, borderRadius: 'var(--r-md)',
          background: `var(--${meta.tone}-bg, var(--surface-2))`,
          color: `var(--${meta.tone}-fg, var(--ink-1))`,
          border: `1px solid var(--${meta.tone}-fg, var(--border))`,
        }}>
          <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em', opacity: 0.8 }}>
            {t('dashSaudiBandLabel')}
          </div>
          <div style={{ fontSize: 22, fontWeight: 800, marginTop: 4 }}>
            {isAr ? meta.labelAr : meta.labelEn}
          </div>
          <Badge tone={meta.tone}>{t('nitaqatTitle')}</Badge>
        </div>
        <div style={{
          padding: 16, borderRadius: 'var(--r-md)',
          background: 'var(--surface-2)', border: '1px solid var(--border)',
        }}>
          <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--ink-3)' }}>
            {t('nitaqatSaudizationKpi')}
          </div>
          <div style={{ fontSize: 28, fontWeight: 800, color: 'var(--ink-1)', marginTop: 4 }}>{saudization}%</div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            {state.saudiEmployees} / {state.totalEmployees}
          </div>
        </div>
        <div style={{
          padding: 16, borderRadius: 'var(--r-md)',
          background: 'var(--surface-2)', border: '1px solid var(--border)',
        }}>
          <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--ink-3)' }}>
            {t('dashSaudiAuthorityLabel')}
          </div>
          <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink-1)', marginTop: 4, lineHeight: 1.4 }}>
            HRSD · {t('dashSaudiAuthoritySub')}
          </div>
          <div style={{ fontSize: 11, color: 'var(--primary-700, var(--info-fg))', marginTop: 8, fontWeight: 600 }}>
            {t('dashSaudiOpenWorkspace')} →
          </div>
        </div>
      </Link>

      {/* Mini band ladder */}
      <div style={{ display: 'flex', gap: 4 }}>
        {[...NITAQAT_BANDS].reverse().map(b => {
          const isCurrent = b.band === band;
          return (
            <div key={b.band} style={{
              flex: 1, padding: '6px 4px',
              background: isCurrent ? `var(--${b.tone}-bg, var(--surface-2))` : 'var(--surface-2)',
              border: isCurrent ? `2px solid var(--${b.tone}-fg, var(--border))` : '1px solid var(--border)',
              borderRadius: 'var(--r-sm)', textAlign: 'center',
            }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: isCurrent ? `var(--${b.tone}-fg)` : 'var(--ink-2)' }}>
                ≥ {b.minPercent}%
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/** Open intakes count — clicks through to the triager inbox. */
function OpenIntakesKpi({ t }: { t: (k: string) => string }) {
  const { items } = useIntake();
  const open = items.filter(i => i.status === 'SUBMITTED' || i.status === 'TRIAGING').length;
  const overdue = items.filter(i =>
    (i.status === 'SUBMITTED' || i.status === 'TRIAGING') && hoursUntilSla(i) < 0
  ).length;
  return <Kpi
    Icon={Inbox}
    label={t('dashKpiOpenIntakes')}
    value={open}
    tone={overdue > 0 ? 'danger' : 'primary'}
    spark={mockTrend(open, 0.25)}
    to="/intake-inbox"
  />;
}

/** Overdue vendor reassessments — clicks through to the TPRM dashboard. */
function OverdueReassessmentsKpi({ t }: { t: (k: string) => string }) {
  const overdueCount = VENDORS.reduce((acc, v) => {
    const meta = VENDOR_TPRM_META[v.id];
    if (!meta || meta.onboardingStage !== 'ACTIVE') return acc;
    return reviewBucket(meta.tier, meta.lastAssessedAt) === 'OVERDUE' ? acc + 1 : acc;
  }, 0);
  return <Kpi
    Icon={Clock}
    label={t('dashKpiOverdueReassessments')}
    value={overdueCount}
    tone={overdueCount > 0 ? 'warning' : 'success'}
    spark={mockTrend(overdueCount, 0.3)}
    to="/tprm"
  />;
}

/** CAB queue — change requests in CAB review or pending approval. */
function CabQueueKpi({ t }: { t: (k: string) => string }) {
  const inFlight = CHANGE_REQUESTS.filter(c =>
    c.status === 'CAB_REVIEW' || c.status === 'IMPACT_ASSESSMENT' || c.status === 'SCHEDULED' || c.status === 'SUBMITTED'
  ).length;
  return <Kpi
    Icon={GitBranch}
    label={t('dashKpiCabQueue')}
    value={inFlight}
    tone="info"
    spark={mockTrend(inFlight, 0.2)}
    to="/changes"
  />;
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
