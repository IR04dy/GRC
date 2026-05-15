import { useEffect, useState, type ReactNode } from 'react';
import { AlertTriangle, Flame, ClipboardCheck, ShieldCheck, Plus, Check, Activity, FileText, Grid3x3, BarChart3, CheckSquare, Award, Inbox, Clock, GitBranch, Truck, ArrowUpRight, Network, Bug, Share2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Progress } from '../../components/ui/Progress';
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { BarChart3D } from '../../components/viz/BarChart3D';
import type { DashboardStats } from '../../lib/api';
import { useNavigate } from 'react-router-dom';
import { listFourthParty } from '../../lib/fourthParty';
import { listTpc } from '../../lib/thirdPartyCyber';
import { listSrt } from '../../lib/resilienceTests';
import { listProcessorAssessments } from '../../lib/processorAssessments';
import { listDsas } from '../../lib/dataSharing';
import {
  loadNitaqatState, computeSaudization, classifyNitaqatBand, nitaqatMeta,
} from '../../lib/nitaqat';
import { useIntake, hoursUntilSla } from '../../lib/intake';
import { VENDORS } from '../tprm/mockTprm';
import { VENDOR_TPRM_META } from '../tprm/mockTprm';
import { reviewBucket } from '../../lib/vendorOnboarding';
import { CHANGE_REQUESTS } from '../changes/mockChanges';
import { COMPLIANCE_FINDINGS, AUDIT_ENGAGEMENTS } from '../compliance/mockCompliance';

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

  // Enhancement C — TPRM consolidated snapshot widget surfacing the five
  // new modules (fourth-party, third-party cyber, processor DD, SCM
  // resilience tests, data sharing). One row in the layout that rolls them
  // up and deep-links into /tprm overview tab for the full view.
  { id: 'tprm-snapshot', titleKey: 'dashTprmSnapshotTitle', defaultSize: 'lg', category: 'progress',
    render: ({ t }) =>
      <Card title={t('dashTprmSnapshotTitle')} Icon={Truck} action={<Badge tone="primary">{t('dashTprmSnapshotBadge')}</Badge>}>
        <TprmSnapshotWidget t={t} />
      </Card> },
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
  { id: 'tprm-snapshot',              size: 'lg' },
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

// ─── Saudi Regulatory Posture — multi-regulator panel ───────────────────
//
// Previously this widget surfaced ONLY HRSD/Nitaqat (labor) under a title
// that implied it covered every regulator. User feedback: "it contain only
// the labor not other regulators … redesign based on best practice and
// leaders." Redesigned to show the eight major Saudi authorities the
// platform tracks, each as a tile with:
//   - Authority code + short EN/AR label
//   - Headline metric (open findings, % compliance, next deadline)
//   - RAG status: green = no open issues; amber = ≥1 high-severity open;
//     red = critical-severity open OR a regulator-specific red flag
//     (e.g. Nitaqat = LOW/RED band, SAMA = past-due breach notification)
//   - Click → the authority's workspace (/authority/{code})
//
// The picks mirror what a Saudi GRC compliance owner actually reports on
// to the board: NCA + SAMA + SDAIA/PDPL + CMA + CITC + ZATCA + HRSD +
// NCEMA. Frameworks like ISO/NIST aren't regulators and live under
// /compliance, not here.

interface RegulatorTile {
  code: string;       // Matches AUTHORITIES.code → routes to /authority/{code}
  nameEnShort: string;
  nameArShort: string;
  /** What this regulator covers, single-clause description for the tile body. */
  scopeEnShort: string;
  scopeArShort: string;
  /** Headline number to render large. */
  metric: number | string;
  /** EN/AR label under the metric. */
  metricLabelEn: string;
  metricLabelAr: string;
  /** RAG status drives the tile's border + chip tone. */
  status: 'success' | 'warning' | 'danger' | 'info';
  /** Status-chip text. */
  statusEn: string;
  statusAr: string;
}

/** Build the regulator tile array from live compliance data. */
function useSaudiRegulatorTiles(): RegulatorTile[] {
  // ── Helpers — derive RAG from real finding counts ────────────────
  const findingsForFramework = (frameworkId: number) =>
    COMPLIANCE_FINDINGS.filter(f => f.frameworkId === frameworkId && f.status !== 'CLOSED');
  const ragFromFindings = (open: number, critical: number): 'success' | 'warning' | 'danger' => {
    if (critical > 0 || open >= 5) return 'danger';
    if (open > 0)                  return 'warning';
    return 'success';
  };

  // ── NCA ECC (frameworkId=1, NCA ECC) ────────────────────────────
  const ncaFindings = findingsForFramework(1);
  const ncaCritical = ncaFindings.filter(f => f.severity === 'CRITICAL' || f.severity === 'HIGH').length;
  const ncaStatus   = ragFromFindings(ncaFindings.length, ncaCritical);

  // ── SDAIA / PDPL (frameworkId=2) ────────────────────────────────
  const pdplFindings = findingsForFramework(2);
  const pdplCritical = pdplFindings.filter(f => f.severity === 'CRITICAL' || f.severity === 'HIGH').length;
  const pdplStatus   = ragFromFindings(pdplFindings.length, pdplCritical);

  // ── SAMA (frameworkId=3, banking CSF) ───────────────────────────
  const samaFindings = findingsForFramework(3);
  const samaCritical = samaFindings.filter(f => f.severity === 'CRITICAL' || f.severity === 'HIGH').length;
  const samaStatus   = ragFromFindings(samaFindings.length, samaCritical);

  // ── HRSD / Nitaqat (real saudization band) ──────────────────────
  const nitaqat = loadNitaqatState();
  const saudization = computeSaudization(nitaqat);
  const band = classifyNitaqatBand(saudization);
  const nitaqatMetaInfo = nitaqatMeta(band);

  // ── CMA, CITC, ZATCA, NCEMA — use audit-engagement coverage as a
  //    proxy when we don't have direct finding-frameworks for them.
  //    Tiles still render the count + status; clicking them goes to
  //    the authority workspace where the deep data lives.
  const engagementsFor = (fwPrefix: string) =>
    AUDIT_ENGAGEMENTS.filter(e => (e.framework ?? '').toUpperCase().includes(fwPrefix)).length;
  const cmaCount   = engagementsFor('CMA');
  const citcCount  = engagementsFor('CITC') + engagementsFor('CLOUD');
  const zatcaCount = engagementsFor('ZATCA');
  const ncemaCount = engagementsFor('NCEMA') + engagementsFor('ISO_22301');

  return [
    {
      code: 'NCA',
      nameEnShort: 'NCA',  nameArShort: 'الأمن السيبراني',
      scopeEnShort: 'Cybersecurity (ECC)',
      scopeArShort: 'الضوابط الأساسية للأمن السيبراني',
      metric: ncaFindings.length,
      metricLabelEn: 'open findings',
      metricLabelAr: 'ملاحظات مفتوحة',
      status: ncaStatus,
      statusEn: ncaStatus === 'success' ? 'On track' : ncaStatus === 'warning' ? 'At risk' : 'Action required',
      statusAr: ncaStatus === 'success' ? 'ضمن المسار' : ncaStatus === 'warning' ? 'متأثّر' : 'إجراء عاجل',
    },
    {
      code: 'SAMA',
      nameEnShort: 'SAMA',  nameArShort: 'البنك المركزي',
      scopeEnShort: 'Banking / Insurance CSF',
      scopeArShort: 'إطار الأمن السيبراني للبنوك والتأمين',
      metric: samaFindings.length,
      metricLabelEn: 'open findings',
      metricLabelAr: 'ملاحظات مفتوحة',
      status: samaStatus,
      statusEn: samaStatus === 'success' ? 'On track' : samaStatus === 'warning' ? 'At risk' : 'Action required',
      statusAr: samaStatus === 'success' ? 'ضمن المسار' : samaStatus === 'warning' ? 'متأثّر' : 'إجراء عاجل',
    },
    {
      code: 'SDAIA',
      nameEnShort: 'SDAIA / PDPL',  nameArShort: 'سدايا / حماية البيانات',
      scopeEnShort: 'Personal Data Protection Law',
      scopeArShort: 'نظام حماية البيانات الشخصية',
      metric: pdplFindings.length,
      metricLabelEn: 'open findings',
      metricLabelAr: 'ملاحظات مفتوحة',
      status: pdplStatus,
      statusEn: pdplStatus === 'success' ? 'On track' : pdplStatus === 'warning' ? 'At risk' : 'Action required',
      statusAr: pdplStatus === 'success' ? 'ضمن المسار' : pdplStatus === 'warning' ? 'متأثّر' : 'إجراء عاجل',
    },
    {
      code: 'CMA',
      nameEnShort: 'CMA',  nameArShort: 'هيئة السوق المالية',
      scopeEnShort: 'Listed-company governance',
      scopeArShort: 'حوكمة الشركات المساهمة',
      metric: cmaCount,
      metricLabelEn: 'active engagements',
      metricLabelAr: 'مهام نشطة',
      status: cmaCount > 0 ? 'info' : 'success',
      statusEn: cmaCount > 0 ? 'In progress' : 'On track',
      statusAr: cmaCount > 0 ? 'قيد التنفيذ' : 'ضمن المسار',
    },
    {
      code: 'CITC',
      nameEnShort: 'CITC',  nameArShort: 'هيئة الاتصالات',
      scopeEnShort: 'Telecom + Cloud Computing Regs',
      scopeArShort: 'الاتصالات وضوابط الحوسبة السحابية',
      metric: citcCount,
      metricLabelEn: 'active engagements',
      metricLabelAr: 'مهام نشطة',
      status: citcCount > 0 ? 'info' : 'success',
      statusEn: citcCount > 0 ? 'In progress' : 'On track',
      statusAr: citcCount > 0 ? 'قيد التنفيذ' : 'ضمن المسار',
    },
    {
      code: 'ZATCA',
      nameEnShort: 'ZATCA',  nameArShort: 'الزكاة والضريبة والجمارك',
      scopeEnShort: 'E-invoicing (Fatoorah), VAT, Customs',
      scopeArShort: 'الفوترة الإلكترونية والضرائب والجمارك',
      metric: zatcaCount,
      metricLabelEn: 'active engagements',
      metricLabelAr: 'مهام نشطة',
      status: zatcaCount > 0 ? 'info' : 'success',
      statusEn: zatcaCount > 0 ? 'In progress' : 'On track',
      statusAr: zatcaCount > 0 ? 'قيد التنفيذ' : 'ضمن المسار',
    },
    {
      code: 'HRSD',
      nameEnShort: 'HRSD',  nameArShort: 'الموارد البشرية',
      scopeEnShort: 'Saudization (Nitaqat)',
      scopeArShort: 'برنامج نطاقات للتوطين',
      metric: `${saudization}%`,
      metricLabelEn: `${nitaqatMetaInfo.labelEn} band`,
      metricLabelAr: `نطاق ${nitaqatMetaInfo.labelAr}`,
      status: nitaqatMetaInfo.tone === 'success' ? 'success'
            : nitaqatMetaInfo.tone === 'warning' ? 'warning'
            : nitaqatMetaInfo.tone === 'danger'  ? 'danger'
            : 'info',
      statusEn: `${nitaqatMetaInfo.labelEn}`,
      statusAr: `نطاق ${nitaqatMetaInfo.labelAr}`,
    },
    {
      code: 'NCEMA',
      nameEnShort: 'NCEMA',  nameArShort: 'الكوارث والأزمات',
      scopeEnShort: 'Business Continuity (ISO 22301)',
      scopeArShort: 'استمرارية الأعمال (آيزو 22301)',
      metric: ncemaCount,
      metricLabelEn: 'active exercises',
      metricLabelAr: 'تمارين قائمة',
      status: ncemaCount > 0 ? 'info' : 'success',
      statusEn: ncemaCount > 0 ? 'In progress' : 'On track',
      statusAr: ncemaCount > 0 ? 'قيد التنفيذ' : 'ضمن المسار',
    },
  ];
}

function SaudiPostureWidget({ t: _t }: { t: (k: string) => string }) {
  const isAr = typeof document !== 'undefined' && document.documentElement.dir === 'rtl';
  const tiles = useSaudiRegulatorTiles();

  // Rollup chip across all tiles — quick "how is Saudi posture doing?" answer.
  const danger  = tiles.filter(x => x.status === 'danger').length;
  const warning = tiles.filter(x => x.status === 'warning').length;
  const success = tiles.filter(x => x.status === 'success').length;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* Rollup line: tiny RAG counts across all regulators */}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="text-2xs text-muted" style={{ textTransform: 'uppercase', letterSpacing: '0.06em' }}>
          {isAr ? 'الوضع عبر الجهات التنظيمية' : 'Posture across regulators'}
        </span>
        {danger  > 0 && <Badge tone="danger">{danger} {isAr ? 'إجراء عاجل' : 'action required'}</Badge>}
        {warning > 0 && <Badge tone="warning">{warning} {isAr ? 'متأثّر' : 'at risk'}</Badge>}
        {success > 0 && <Badge tone="success">{success} {isAr ? 'ضمن المسار' : 'on track'}</Badge>}
      </div>

      {/* 4-column responsive grid of regulator tiles. Each tile is a Link to
       * the authority workspace; the RAG colour drives the leading-edge
       * stripe via inline borderInlineStart so the warm-cream surface stays
       * the same across tiles (only the stripe + status chip change). */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
        gap: 10,
      }}>
        {tiles.map(tile => (
          <Link key={tile.code} to={`/authority/${tile.code}`} style={{
            display: 'block', textDecoration: 'none', color: 'inherit',
            padding: 14, borderRadius: 'var(--r-md)',
            background: 'var(--surface-1)',
            border: '1px solid var(--border)',
            borderInlineStart: `4px solid var(--${tile.status})`,
            transition: 'box-shadow var(--dur-fast) var(--ease-standard), border-color var(--dur-fast) var(--ease-standard)',
          }}
            onMouseEnter={e => {
              const el = e.currentTarget as HTMLElement;
              el.style.borderColor = `var(--${tile.status})`;
              el.style.boxShadow = '0 2px 8px rgba(0,0,0,0.06)';
            }}
            onMouseLeave={e => {
              const el = e.currentTarget as HTMLElement;
              el.style.borderColor = 'var(--border)';
              el.style.boxShadow = '';
            }}>
            {/* Tile header: code + short name + status chip */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
              <div style={{ minWidth: 0 }}>
                <div className="text-strong" style={{ fontSize: 14 }}>
                  {isAr ? tile.nameArShort : tile.nameEnShort}
                </div>
                <div className="text-2xs text-muted" style={{ marginTop: 2, lineHeight: 1.3 }}>
                  {isAr ? tile.scopeArShort : tile.scopeEnShort}
                </div>
              </div>
              <Badge tone={tile.status}>{isAr ? tile.statusAr : tile.statusEn}</Badge>
            </div>

            {/* Headline metric */}
            <div style={{ marginTop: 12, display: 'flex', alignItems: 'baseline', gap: 6 }}>
              <span style={{ fontSize: 24, fontWeight: 800, color: 'var(--ink-1)', lineHeight: 1 }}>
                {tile.metric}
              </span>
              <span className="text-2xs text-muted">
                {isAr ? tile.metricLabelAr : tile.metricLabelEn}
              </span>
            </div>

            {/* Trailing arrow hint */}
            <div className="text-2xs" style={{ color: `var(--${tile.status}-fg)`, marginTop: 10, fontWeight: 600 }}>
              {isAr ? 'فتح مساحة الجهة ←' : 'Open workspace →'}
            </div>
          </Link>
        ))}
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

/**
 * Dashboard TPRM snapshot — rolls up the five new modules (fourth-party,
 * cyber assessment, processor DD, resilience tests, data sharing) into a
 * single dashboard tile with deep-links into each.
 */
function TprmSnapshotWidget({ t }: { t: (k: string) => string }) {
  const [c, setC] = useState({
    fp: 0, fpHigh: 0, tpc: 0, tpcHigh: 0, proc: 0, procFail: 0,
    srt: 0, srtFail: 0, srtScheduled: 0, dsa: 0, dsaCross: 0,
    loading: true,
  });
  useEffect(() => {
    (async () => {
      try {
        const [a, b, p, s, d] = await Promise.all([
          listFourthParty(undefined, 0, 200).catch(() => ({ content: [] as any[], totalElements: 0 })),
          listTpc(undefined, 0, 200).catch(() => ({ content: [] as any[], totalElements: 0 })),
          listProcessorAssessments(undefined, 0, 200).catch(() => ({ content: [] as any[], totalElements: 0 })),
          listSrt(undefined, 0, 200).catch(() => ({ content: [] as any[], totalElements: 0 })),
          listDsas(undefined, 0, 200).catch(() => ({ content: [] as any[], totalElements: 0 })),
        ]);
        setC({
          fp:           a.content.length,
          fpHigh:       a.content.filter((r: any) => r.concentrationRisk === 'HIGH').length,
          tpc:          b.content.length,
          tpcHigh:      b.content.filter((r: any) => r.residualRiskTier === 'HIGH' || r.residualRiskTier === 'CRITICAL').length,
          proc:         p.content.length,
          procFail:     p.content.filter((r: any) => r.overallOutcome === 'FAIL').length,
          srt:          s.content.length,
          srtFail:      s.content.filter((r: any) => r.outcome === 'FAIL').length,
          srtScheduled: s.content.filter((r: any) => r.status === 'SCHEDULED').length,
          dsa:          d.content.length,
          dsaCross:     d.content.filter((r: any) => r.crossBorderMechanism && r.crossBorderMechanism !== 'NONE').length,
          loading: false,
        });
      } catch { setC(prev => ({ ...prev, loading: false })); }
    })();
  }, []);

  const Tile = ({ to, Icon, label, value, sub, subTone }: { to: string; Icon: any; label: string; value: number | string; sub?: string; subTone?: 'danger' | 'warning' | 'info' | 'success' }) => (
    <Link to={to} style={{
      display: 'flex', flexDirection: 'column', gap: 4,
      padding: '12px 14px', background: 'var(--surface-2)',
      border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
      textDecoration: 'none', color: 'var(--ink-1)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: 'var(--ink-3)' }}>
        <Icon size={13} /> {label} <ArrowUpRight size={11} style={{ marginInlineStart: 'auto' }} />
      </div>
      <div style={{ fontSize: 22, fontWeight: 700, fontFamily: 'var(--font-mono)' }}>{c.loading ? '…' : value}</div>
      {sub && <Badge tone={subTone ?? 'info'}>{sub}</Badge>}
    </Link>
  );

  return (
    <div>
      <p style={{ fontSize: 12, color: 'var(--muted-foreground)', marginBottom: 10 }}>{t('dashTprmSnapshotSub')}</p>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 10 }}>
        <Tile to="/fourth-party"          Icon={Network}        label={t('tpoLinkFp')}    value={c.fp}   sub={c.fpHigh    ? `${c.fpHigh} HIGH conc.`     : undefined} subTone="danger" />
        <Tile to="/third-party-cyber"     Icon={Bug}            label={t('tpoLinkTpc')}   value={c.tpc}  sub={c.tpcHigh   ? `${c.tpcHigh} HIGH residual` : undefined} subTone="danger" />
        <Tile to="/processor-assessments" Icon={ClipboardCheck} label={t('tpoLinkProc')}  value={c.proc} sub={c.procFail  ? `${c.procFail} FAILED`        : undefined} subTone="danger" />
        <Tile to="/resilience-tests"      Icon={Activity}       label={t('tpoLinkSrt')}   value={c.srt}  sub={c.srtScheduled ? `${c.srtScheduled} scheduled` : undefined} subTone="warning" />
        <Tile to="/data-sharing"          Icon={Share2}         label={t('tpoLinkDsa')}   value={c.dsa}  sub={c.dsaCross  ? `${c.dsaCross} cross-border`  : undefined} subTone="info" />
        <Link to="/tprm" style={{
          display: 'flex', flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'center', gap: 4,
          padding: '12px 14px', background: 'var(--primary-50)',
          border: '1px solid var(--primary-200)', borderRadius: 'var(--r-md)',
          color: 'var(--primary-700)', textDecoration: 'none', fontWeight: 600, fontSize: 13,
        }}>
          <Truck size={14} /> {t('dashTprmSnapshotCta')}
          <ArrowUpRight size={12} />
        </Link>
      </div>
    </div>
  );
}
