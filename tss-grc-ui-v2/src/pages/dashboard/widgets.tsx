import { useEffect, useState, type ReactNode } from 'react';
import { AlertTriangle, Flame, ClipboardCheck, ShieldCheck, Plus, Check, Activity, FileText, Grid3x3, BarChart3, CheckSquare, Award, Inbox, Clock, GitBranch, Truck, ArrowUpRight, Network, Bug, Share2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Progress } from '../../components/ui/Progress';
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { BarChart3D } from '../../components/viz/BarChart3D';
import type { DashboardStats, ComplianceFindingDto } from '../../lib/api';
import { useNavigate } from 'react-router-dom';
import { listFourthParty, isConcentrationAtLeast } from '../../lib/fourthParty';
// W2-1a-ii: these four widgets used to compute from mock datasets (mockTprm, mockChanges,
// mockCompliance) imported at module scope, so the dashboard's regulator RAG, CAB queue and
// overdue-reassessment KPIs were derived from records that do not exist in the tenant.
import { complianceApi, changeApi, tprmApi } from '../../lib/api';
import { listVendorAssessments } from '../../lib/tprmQuestionnaire';
import { loadActivity, subscribeActivity, type ActivityEvent } from '../../lib/activityLog';
import { listTpc } from '../../lib/thirdPartyCyber';
import { listSrt } from '../../lib/resilienceTests';
import { listProcessorAssessments } from '../../lib/processorAssessments';
import { listDsas } from '../../lib/dataSharing';
import {
  loadNitaqatState, computeSaudization, classifyNitaqatBand, nitaqatMeta,
} from '../../lib/nitaqat';
import { useIntake, hoursUntilSla } from '../../lib/intake';


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
  /** Real weekly open-risk trend from /api/v1/dashboard/kpi-trend (for sparklines). */
  riskTrend?: number[];
}

// ─── Built-in widgets ──────────────────────────────────────────────────────
export const WIDGETS: DashboardWidget[] = [
  // KPIs — every tile drills into a filtered list (Tier 1)
  { id: 'kpi-total-risks',    titleKey: 'kpiTotalRisks',    defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t, riskTrend }) => <Kpi Icon={AlertTriangle} label={t('kpiTotalRisks')} value={stats.totalRisks} tone="primary" spark={riskTrend} to="/risk" /> },
  { id: 'kpi-critical-risks', titleKey: 'kpiCriticalRisks', defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t, riskTrend }) => <Kpi Icon={Flame} label={t('kpiCriticalRisks')} value={stats.criticalRisks ?? stats.openCriticalRisks} tone="danger" spark={riskTrend} to="/risk?level=CRITICAL" /> },
  { id: 'kpi-compliance',     titleKey: 'kpiCompliance',    defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t }) => <Kpi Icon={ClipboardCheck} label={t('kpiCompliance')} value={`${stats.complianceScore}%`} tone="success" to="/compliance?tab=scorecard" /> },
  { id: 'kpi-active-controls',titleKey: 'kpiActiveControls',defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ stats, t }) => <Kpi Icon={ShieldCheck} label={t('kpiActiveControls')} value={stats.activeUsers} tone="info" to="/compliance?tab=controls" /> },

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

  // Activity — real recorded events from lib/activityLog (W2-1a-ii)
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
  // W2-1a-ii: renamed with its metric — the tier/cadence needed for "overdue" exists only
  // in a frontend mock, so the live KPI is "never assessed" and says so.
  { id: 'kpi-never-assessed-vendors', titleKey: 'dashKpiNeverAssessedVendors', defaultSize: 'sm', isKpi: true, category: 'kpi',
    render: ({ t }) => <NeverAssessedVendorsKpi t={t} /> },
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
  { id: 'kpi-never-assessed-vendors', size: 'sm' },
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
  /**
   * Headline number to render large. `null` means NOT MEASURED — W2-1a-ii: four of these
   * tiles were fed by a mock array with no live replacement, and a 0 on a regulator tile is
   * a compliance assertion ("nothing open against CMA"), not an honest absence. The tile
   * renders an em dash and a neutral chip instead.
   */
  metric: number | string | null;
  /** EN/AR label under the metric. */
  metricLabelEn: string;
  metricLabelAr: string;
  /** RAG status drives the tile's border + chip tone. */
  status: 'success' | 'warning' | 'danger' | 'info';
  /** Status-chip text. */
  statusEn: string;
  statusAr: string;
}

/**
 * Build the regulator tile array from live compliance data.
 *
 * W2-1a-ii — this function's name and comments always claimed "live compliance data" and it
 * read `COMPLIANCE_FINDINGS`, a mock array. The RAG status on the Saudi regulator tiles —
 * the first thing an executive sees on the dashboard — was therefore green/amber/red against
 * findings that do not exist in the tenant. Now fetched from complianceApi.findings().
 *
 * Until the fetch resolves (and if it fails) the tiles render a null metric rather than 0: a
 * zero would read as "no open findings against NCA ECC", which is a compliance assertion and
 * not a loading state.
 */
function useSaudiRegulatorTiles(): RegulatorTile[] {
  const [findings, setFindings] = useState<ComplianceFindingDto[] | null>(null);
  useEffect(() => {
    let live = true;
    complianceApi.findings(0, 500)
      .then(r => { if (live) setFindings(r.content); })
      .catch(() => { if (live) setFindings(null); });
    return () => { live = false; };
  }, []);

  // ── Helpers — derive RAG from real finding counts ────────────────
  const findingsForFramework = (frameworkId: number) =>
    (findings ?? []).filter(f => f.frameworkId === frameworkId && f.status !== 'CLOSED');
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

  // ── CMA, CITC, ZATCA, NCEMA ─────────────────────────
  // These four used audit-engagement coverage as a proxy, read from the mock
  // AUDIT_ENGAGEMENTS array. They stay unmeasured — but NOT for the reason this comment
  // used to give.
  //
  // ⚠ F2-203 correction. It read: "There is NO live list endpoint for audit engagements
  // (lib/auditEngagement.ts only derives milestones from one already fetched …), so there is
  // nothing to substitute." That is false and was false when written:
  // `complianceApi.auditEngagements()` calls `GET /api/v1/compliance/audit-engagements`,
  // which `AuditEngagementController` has served all along, and `CompliancePage` reads it
  // through `useApiList`. 12 real rows in dev and prod. A comment that answers the question
  // a reader was about to ask, wrongly, is worse than no comment (F2-184) — and this one
  // erred conservatively, which is why nobody caught it: an em dash looks like caution.
  //
  // The real reason these four are unmeasured is that engagement coverage is not an
  // obligation count for CMA, CITC, ZATCA or NCEMA. Wiring the number now available would
  // put a measured figure under a label it does not answer, which is a fabrication with a
  // provenance. Clicking through to the authority workspace still reaches the real data.
  const cmaCount   = null;
  const citcCount  = null;
  const zatcaCount = null;
  const ncemaCount = null;

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
      metricLabelEn: 'not measured here',
      metricLabelAr: 'غير مُقاس هنا',
      status: 'info',
      statusEn: 'Open workspace',
      statusAr: 'افتح مساحة العمل',
    },
    {
      code: 'CITC',
      nameEnShort: 'CITC',  nameArShort: 'هيئة الاتصالات',
      scopeEnShort: 'Telecom + Cloud Computing Regs',
      scopeArShort: 'الاتصالات وضوابط الحوسبة السحابية',
      metric: citcCount,
      metricLabelEn: 'not measured here',
      metricLabelAr: 'غير مُقاس هنا',
      status: 'info',
      statusEn: 'Open workspace',
      statusAr: 'افتح مساحة العمل',
    },
    {
      code: 'ZATCA',
      nameEnShort: 'ZATCA',  nameArShort: 'الزكاة والضريبة والجمارك',
      scopeEnShort: 'E-invoicing (Fatoorah), VAT, Customs',
      scopeArShort: 'الفوترة الإلكترونية والضرائب والجمارك',
      metric: zatcaCount,
      metricLabelEn: 'not measured here',
      metricLabelAr: 'غير مُقاس هنا',
      status: 'info',
      statusEn: 'Open workspace',
      statusAr: 'افتح مساحة العمل',
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
      metricLabelEn: 'not measured here',
      metricLabelAr: 'غير مُقاس هنا',
      status: 'info',
      statusEn: 'Open workspace',
      statusAr: 'افتح مساحة العمل',
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
                {tile.metric ?? '—'}
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
    to="/intake-inbox"
  />;
}

/**
 * Vendors with no assessment on record — clicks through to the TPRM dashboard.
 *
 * W2-1a-ii — this KPI used to read "overdue reassessments" from `VENDOR_TPRM_META`, a mock
 * map keyed by mock vendor id. It could not be ported as-is: the tier and reassessment
 * cadence it depends on (`reviewBucket(tier, lastAssessedAt)`) exist ONLY in that frontend
 * mock. `TprmVendorDto` carries no tier and no last-assessed date, and `riskRating`
 * (HIGH/CRITICAL) does not map to `VendorTier` (TIER_1..4) — so picking a mapping would mean
 * inventing this tenant's reassessment policy, the same fabrication in a new place.
 *
 * What IS live and meaningful: active vendors with no assessment row at all. It needs no
 * cadence assumption, and "this supplier has never been assessed" is a stronger TPRM signal
 * than a date slipping. The label changes with it — a KPI must not keep a name it no longer
 * means.
 */
function NeverAssessedVendorsKpi({ t }: { t: (k: string) => string }) {
  const [count, setCount] = useState<number | null>(null);
  useEffect(() => {
    let live = true;
    Promise.all([tprmApi.vendors(0, 500), listVendorAssessments()])
      .then(([vendors, assessments]) => {
        if (!live) return;
        const assessed = new Set(assessments.map(a => a.vendorId));
        const active = vendors.content.filter(v => (v.status ?? '').toUpperCase() === 'ACTIVE');
        setCount(active.filter(v => !assessed.has(v.id)).length);
      })
      .catch(() => { if (live) setCount(null); });
    return () => { live = false; };
  }, []);
  return <Kpi
    Icon={Clock}
    label={t('dashKpiNeverAssessedVendors')}
    value={count ?? '—'}
    tone={count == null ? 'neutral' : count > 0 ? 'warning' : 'success'}
    to="/tprm"
  />;
}

const CAB_IN_FLIGHT = ['CAB_REVIEW', 'IMPACT_ASSESSMENT', 'SCHEDULED', 'SUBMITTED'];

/**
 * CAB queue — change requests in CAB review or pending approval.
 * W2-1a-ii: counted from the mockChanges array; now from changeApi.list(). Null while
 * loading or on failure, so the tile shows an em dash rather than asserting an empty CAB.
 */
function CabQueueKpi({ t }: { t: (k: string) => string }) {
  const [inFlight, setInFlight] = useState<number | null>(null);
  useEffect(() => {
    let live = true;
    changeApi.list({ size: 500 })
      .then(r => { if (live) setInFlight(r.content.filter(c => CAB_IN_FLIGHT.includes(c.status ?? '')).length); })
      .catch(() => { if (live) setInFlight(null); });
    return () => { live = false; };
  }, []);
  return <Kpi
    Icon={GitBranch}
    label={t('dashKpiCabQueue')}
    value={inFlight ?? '—'}
    tone="info"
    to="/changes"
  />;
}

/** Icon + tone per recorded action, so the feed reads at a glance. */
const ACTIVITY_LOOK: Record<string, { Icon: typeof Plus; color: string }> = {
  CREATED:        { Icon: Plus,          color: 'var(--success-fg)' },
  TRANSITIONED:   { Icon: Check,         color: 'var(--primary)' },
  CLOSED:         { Icon: Check,         color: 'var(--success-fg)' },
  REOPENED:       { Icon: AlertTriangle, color: 'var(--danger-fg)' },
  ASSIGNED:       { Icon: FileText,      color: 'var(--accent-700)' },
  COMMENTED:      { Icon: FileText,      color: 'var(--ink-3)' },
  EVIDENCE_ADDED: { Icon: FileText,      color: 'var(--primary)' },
  NOTIFIED:       { Icon: FileText,      color: 'var(--ink-3)' },
};

/** "2m" / "14m" / "3h" / "5d" from a timestamp. */
function agoLabel(iso: string, now: number): string {
  const mins = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000));
  if (mins < 60) return `${mins}m`;
  if (mins < 1440) return `${Math.round(mins / 60)}h`;
  return `${Math.round(mins / 1440)}d`;
}

function describeActivity(e: ActivityEvent): string {
  const what = `${e.entityType.replace(/-/g, ' ')} ${e.entityId}`;
  switch (e.action) {
    case 'CREATED':    return `Created ${what}`;
    case 'TRANSITIONED': return `${what}: ${e.fromStatus ?? '?'} → ${e.toStatus ?? '?'}`;
    case 'ASSIGNED':   return `Assigned ${what}${e.assigneeId ? ` to ${e.assigneeId}` : ''}`;
    default:           return e.noteEn?.trim() || `${e.action} · ${what}`;
  }
}

/**
 * Recent activity.
 *
 * W2-1a-ii — this was FOUR HARDCODED LINES presented as a live feed with relative
 * timestamps: "New risk R-0042 added by Sara", "Control AC-3 evidence approved",
 * "KRI threshold breached: failed logins", "Policy v2.4 sent for attestation". None of it
 * ever happened, and `R-0042` is the same mock risk code W2-1a-i proved is not findable in
 * the tenant — so the dashboard reported the creation of a record that does not exist.
 *
 * Worth naming precisely: this was NOT a mock *import*, which is why the plan's count of
 * four mock imports in this file missed it and why the planned `/mock/i` CI rule (W2-1b)
 * would never have caught it. It is the same blind spot F2-33 has — fabrication written
 * inline rather than imported. A CI rule that greps for mock imports cannot see either.
 *
 * Now rendered from the real activity log (`lib/activityLog.ts`), which records genuine user
 * actions — transitions, creations, assignments — as they happen, and subscribes so the tile
 * updates live. An empty log renders an empty state, because a new tenant genuinely has no
 * activity yet and saying so is the honest answer.
 */
function ActivityFeed() {
  const [events, setEvents] = useState<ActivityEvent[]>(() => loadActivity());
  useEffect(() => subscribeActivity(() => setEvents(loadActivity())), []);

  const recent = [...events]
    .sort((a, b) => b.ts.localeCompare(a.ts))
    .slice(0, 6);
  const now = Date.now();

  if (recent.length === 0) {
    return (
      <div style={{ padding: '18px 4px', fontSize: 13, color: 'var(--ink-3)', textAlign: 'center' }}>
        No activity recorded yet.
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 13 }}>
      {recent.map(e => {
        const look = ACTIVITY_LOOK[e.action] ?? { Icon: AlertTriangle, color: 'var(--ink-3)' };
        return (
          <div key={e.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '7px 0', borderBottom: '1px solid var(--border)' }}>
            <div style={{ width: 28, height: 28, borderRadius: 8, background: 'var(--surface-3)', color: look.color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <look.Icon size={14} />
            </div>
            <div style={{ flex: 1, color: 'var(--ink-1)' }}>{describeActivity(e)}</div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--ink-4)' }}>{agoLabel(e.ts, now)}</div>
          </div>
        );
      })}
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
          // F2-229: a threshold over the declared ladder — `=== 'HIGH'` skipped the CRITICAL row.
          fpHigh:       a.content.filter((r: any) => isConcentrationAtLeast(r.concentrationRisk, 'HIGH')).length,
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
      <p style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 10 }}>{t('dashTprmSnapshotSub')}</p>
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
