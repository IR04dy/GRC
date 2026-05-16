import { useEffect, useMemo, useState } from 'react';
import {
  Leaf, TrendingDown, TrendingUp, Target, Calculator, FileText, Truck,
  Download, Trash2, CheckCircle2, AlertCircle, Building2,
  CloudLightning, Plus, ArrowRight, Link2, Wrench, Clock,
  Paperclip, Coins, Hourglass, X as XIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listClimateItems, createClimateItem, assessClimateItem, treatClimateItem,
  closeClimateItem, retireClimateItem, linkClimateItemToErm,
  riskScoreTone, riskScoreBand, shouldOfferErmLink,
  type ClimateRiskOpportunity, type CroKind,
} from '../../lib/climateRisk';
import {
  listGhgRecords, ghgAggregate, ghgYoy,
  createGhgRecord, submitGhgRecord, verifyGhgRecord, approveGhgRecord,
  ghgStatusTone, canVerify,
  type GhgEmissionRecord, type GhgScope, type GhgAggregate, type GhgYoY,
} from '../../lib/ghgInventory';
import {
  listReportingYears, listReportsForYear, generateAnnex, generateReport,
  reportTypeLabel,
  submitReport, approveReport, publishReport, exportReport,
  ryStatusTone, reportStatusTone,
  type ReportingYear as Ry, type SustainabilityReport as SR,
  type ReportType,
} from '../../lib/sustainabilityAnnex';
import {
  listCapas, capaAggregate,
  startCapa, completeCapa, verifyCapa, closeCapa, cancelCapa,
  capaStatusTone, capaPriorityTone, capaAllowedNext,
  canComplete, capaSourceLabel, capaStatusLabel, capaPriorityLabel,
  daysRemaining,
  type CorrectiveAction, type CapaAggregate, type CapaStatus,
} from '../../lib/sustainabilityCapa';
import {
  listSgi, sgiAggregate, achieveSgi, retireSgi,
  sgiStatusTone, sgiSectorLabel, sgiStatusLabel,
  computeProgressPct, canAchieve,
  type SgiCommitment as SgiC, type SgiAggregate as SgiAgg,
} from '../../lib/sustainabilitySgi';
import {
  listCma, cmaReadiness, markDisclosed, markPartial, markNotDisclosed,
  markNotApplicable, markUnderReview, attachCmaEvidence, recordCmaGap,
  cmaStatusTone, cmaStatusLabel, articleLabel, hasGap, hasEvidence,
  type CmaDisclosure as CmaD, type CmaReadiness as CmaR,
} from '../../lib/sustainabilityCma';
import {
  listTcfd, tcfdReadiness,
  tcfdMarkDisclosed, tcfdMarkPartial, tcfdMarkNotDisclosed,
  tcfdMarkNotApplicable, tcfdMarkUnderReview,
  tcfdAttachEvidence, tcfdRecordGap,
  tcfdStatusTone, tcfdStatusLabel, tcfdPillarLabel,
  hasTcfdGap, hasTcfdEvidence,
  type TcfdItem as TcfdI, type TcfdReadiness as TcfdR, type TcfdPillar,
} from '../../lib/sustainabilityTcfd';
import {
  listMateriality, generateMatrix, scoreMaterialTopic, approveMaterialTopic,
  archiveMaterialTopic,
  materialTierTone, materialTierLabel, materialCategoryLabel,
  materialStatusTone, materialStatusLabel,
  type MaterialTopic, type MaterialMatrix, type MaterialCategory,
} from '../../lib/sustainabilityMateriality';
import {
  listMetrics, metricsAggregate, recordMetricReading, archiveMetric,
  computeMetricProgress, metricStatusTone, metricStatusLabel,
  metricCategoryLabel as esgMetricCategoryLabel,
  type EsgMetric as EsgM, type MetricAggregate, type MetricCategory,
} from '../../lib/sustainabilityMetrics';
import {
  listEvidence, evidenceAggregate, approveEvidence, rejectEvidence,
  sweepExpiredEvidence,
  evidenceStatusTone, evidenceStatusLabel,
  evidenceConfidentialityTone, evidenceConfidentialityLabel,
  evidenceModuleLabel, daysUntilExpiry,
  type SustainabilityEvidence as SE, type EvidenceAggregate as EvidenceAgg,
  type EvidenceApprovalStatus,
} from '../../lib/sustainabilityEvidence';
import {
  getDashboard, esgBandTone, esgBandLabel, esgBandColor, topGapLabel,
  type DashboardPayload,
} from '../../lib/sustainabilityDashboard';
import {
  getSettings, updateSettings, resetSettings,
  type OrganisationSettings as SustSettings,
} from '../../lib/sustainabilitySettings';
import {
  EMISSIONS_TIMESERIES, CURRENT_YEAR_RECORD, EMISSION_FACTORS,
  SURVEY_TONE, SURVEY_LABEL,
  aggregateEsg,
  loadCalcEntries, appendCalcEntry, deleteCalcEntry,
  effectiveSurveys, saveSurveyOverride,
  subscribeEsg, buildEsgAnnex,
  type CalculatorEntry, type VendorEsgSurvey,
} from '../../lib/esg';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';
import { formatSarCompact, formatSarFull } from '../../lib/format';

type EsgTab = 'overview' | 'annex' | 'capa' | 'calculator' | 'cma' | 'tcfd' | 'materiality' | 'metrics' | 'evidence' | 'sgi' | 'ghg' | 'climate' | 'survey' | 'settings';

/**
 * EsgPage — Sprint 6 (C2) + production hardening.
 *
 * Six-tab dashboard:
 *   • Overview   — multi-year scope trend, KPI strip, ESG annex download
 *   • Calculator — GHG-Protocol emission-factor calculator (Saudi grid +
 *                  IPCC fuels + DEFRA travel)
 *   • CMA        — Articles 91-94 disclosure checklist
 *   • TCFD       — 4-pillar disclosure tracker
 *   • SGI        — Saudi Green Initiative commitments by sector
 *   • Survey     — Vendor ESG survey for Scope 3 supplier engagement
 */
export function EsgPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<EsgTab>('overview');

  // Reactive state
  const [stats, setStats] = useState(() => aggregateEsg());
  const [calcEntries, setCalcEntries] = useState<CalculatorEntry[]>(() => loadCalcEntries());
  const [surveys, setSurveys] = useState<VendorEsgSurvey[]>(() => effectiveSurveys());
  useEffect(() => subscribeEsg(() => {
    setStats(aggregateEsg());
    setCalcEntries(loadCalcEntries());
    setSurveys(effectiveSurveys());
  }), []);

  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);

  function downloadAnnex() {
    const text = buildEsgAnnex(brand.organisationNameEn, brand.organisationNameAr);
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ESG-Annex-${CURRENT_YEAR_RECORD.year}.txt`;
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.success(t('esgAnnexDownloadedToast'));
  }

  // TCFD + CMA grouping were previously static useMemo blocks against
  // TCFD_ITEMS / CMA_ARTICLES; V60 + V61 promoted both tabs to API-
  // driven so they self-load and self-group.

  return (
    <Shell title={t('esgTitle')} sub={t('esgSub')}>
      <PageHeader
        eyebrow={t('esgEyebrow')}
        title={t('esgTitle')}
        sub={t('esgPageSub')}
        actions={<Button variant="primary" Icon={Download} onClick={downloadAnnex}>{t('esgDownloadAnnex')}</Button>}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Leaf}          label={t('esgKpiTotal')}     value={`${(stats.totalEmissions / 1000).toFixed(1)}k`} tone="primary" />
        <Kpi Icon={TrendingDown}   label={t('esgKpiYoY')}        value={`${stats.yoyTotal.toFixed(1)}%`} tone={stats.yoyTotal < 0 ? 'success' : 'danger'} />
        <Kpi Icon={Leaf}            label={t('esgKpiScope1')}     value={`${(stats.scope1 / 1000).toFixed(1)}k`} tone="success" />
        <Kpi Icon={Leaf}            label={t('esgKpiScope2')}     value={`${(stats.scope2 / 1000).toFixed(1)}k`} tone="success" />
        <Kpi Icon={Leaf}            label={t('esgKpiScope3')}     value={`${(stats.scope3 / 1000).toFixed(1)}k`} tone="warning" />
        <Kpi Icon={FileText}        label={t('esgKpiCma')}        value={`${stats.cmaCoveragePct}%`} tone={stats.cmaCoveragePct >= 80 ? 'success' : 'warning'} />
        <Kpi Icon={FileText}        label={t('esgKpiTcfd')}       value={`${stats.tcfdCoveragePct}%`} tone={stats.tcfdCoveragePct >= 80 ? 'success' : 'warning'} />
        <Kpi Icon={Target}          label={t('esgKpiSgi')}        value={`${stats.sgiOnTrackCount} / ${stats.sgiTotal}`} tone={stats.sgiOnTrackCount === stats.sgiTotal ? 'success' : 'warning'} />
      </div>

      {/* Tab strip */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'overview',   labelKey: 'esgTabOverview',  Icon: Leaf },
          { id: 'annex',      labelKey: 'esgTabAnnex',     Icon: FileText },
          { id: 'capa',       labelKey: 'esgTabCapa',      Icon: Wrench },
          { id: 'calculator', labelKey: 'esgTabCalc',      Icon: Calculator },
          { id: 'cma',        labelKey: 'esgTabCma',       Icon: FileText },
          { id: 'tcfd',       labelKey: 'esgTabTcfd',      Icon: FileText },
          { id: 'materiality',labelKey: 'esgTabMateriality', Icon: Target },
          { id: 'metrics',    labelKey: 'esgTabMetrics',   Icon: TrendingDown },
          { id: 'evidence',   labelKey: 'esgTabEvidence',  Icon: Paperclip },
          { id: 'sgi',        labelKey: 'esgTabSgi',       Icon: Target },
          { id: 'ghg',        labelKey: 'esgTabGhg',       Icon: Leaf },
          { id: 'climate',    labelKey: 'esgTabClimate',   Icon: CloudLightning },
          { id: 'survey',     labelKey: 'esgTabSurvey',    Icon: Truck },
          { id: 'settings',   labelKey: 'esgTabSettings',  Icon: Wrench },
        ] as { id: EsgTab; labelKey: string; Icon: typeof Leaf }[]).map(tabDef => (
          <button key={tabDef.id} onClick={() => setTab(tabDef.id)}
            style={{
              padding: '8px 12px', borderRadius: 'var(--r-sm)', fontSize: 12, fontWeight: 600,
              border: `1px solid ${tab === tabDef.id ? 'var(--primary)' : 'var(--border)'}`,
              background: tab === tabDef.id ? 'var(--primary-50)' : 'var(--surface-1)',
              cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6,
            }}>
            <tabDef.Icon size={13} />
            {t(tabDef.labelKey)}
          </button>
        ))}
      </div>

      {tab === 'overview'   && <OverviewTab />}
      {tab === 'annex'      && <AnnexTab />}
      {tab === 'capa'       && <CapaTab />}
      {tab === 'calculator' && <CalculatorTab entries={calcEntries}
        onAdd={(e) => { appendCalcEntry(e); toast.success(t('esgCalcAddedToast')); }}
        onDelete={(id) => { deleteCalcEntry(id); toast.info(t('esgCalcDeletedToast')); }} />}
      {tab === 'cma'        && <CmaTab />}
      {tab === 'tcfd'       && <TcfdTab />}
      {tab === 'materiality' && <MaterialityTab />}
      {tab === 'metrics'    && <MetricsTab />}
      {tab === 'evidence'   && <EvidenceTab />}
      {tab === 'sgi'        && <SgiTab />}
      {tab === 'ghg'        && <GhgInventoryTab />}
      {tab === 'climate'    && <ClimateTab />}
      {tab === 'settings'   && <SettingsTab />}
      {tab === 'survey'     && <SurveyTab surveys={surveys}
        onSubmit={(id, patch) => { saveSurveyOverride(id, patch); toast.success(t('esgSurveySavedToast')); }} />}
    </Shell>
  );
}

/**
 * Climate Risks & Opportunities tab — V55 vertical slice.
 *
 * Reads from `/api/sustainability/climate-risks` (Sustainability module
 * backend). Each row is a risk OR an opportunity (kind discriminator),
 * scored on a 5×5 likelihood/impact matrix. Items with risk-score ≥15
 * (High/Critical) and no existing ERM link surface an inline "Link to
 * ERM" prompt — that's Rule 8 from the spec.
 */
function ClimateTab() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<ClimateRiskOpportunity[]>([]);
  const [loading, setLoading] = useState(true);
  const [openNew, setOpenNew] = useState(false);
  // ERM-link prompt modal (replaces window.prompt). Holds the climate-item
  // id so the modal knows which row to link when the user submits.
  const [linkingErmFor, setLinkingErmFor] = useState<number | null>(null);

  async function reload() {
    setLoading(true);
    try {
      const page = await listClimateItems({ size: 100 });
      setItems(page.content);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('cliLoadFailed'));
      setItems([]);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  const risks         = items.filter(i => i.kind === 'RISK');
  const opportunities = items.filter(i => i.kind === 'OPPORTUNITY');
  const needsErmLink  = risks.filter(shouldOfferErmLink);

  async function onCreate(values: Record<string, unknown>) {
    try {
      await createClimateItem({
        kind:          (values.kind as CroKind) ?? 'RISK',
        category:      String(values.category ?? 'PHYSICAL'),
        titleEn:       String(values.titleEn ?? '').trim(),
        titleAr:       String(values.titleAr ?? '').trim() || undefined,
        descriptionEn: String(values.descriptionEn ?? '').trim() || undefined,
        timeHorizon:   (values.timeHorizon as 'SHORT' | 'MEDIUM' | 'LONG') ?? 'MEDIUM',
        likelihood:    Number(values.likelihood ?? 3),
        impact:        Number(values.impact ?? 3),
        reportingYear: Number(values.reportingYear ?? new Date().getFullYear()),
        ownerName:     String(values.ownerName ?? '').trim() || undefined,
      });
      toast.success(t('cliCreated'));
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed'));
    }
  }
  async function onAssess(id: number)  { try { await assessClimateItem(id);  toast.success(t('cliAssessed'));  await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed')); } }
  async function onTreat(id: number)   { try { await treatClimateItem(id);   toast.success(t('cliTreated'));   await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed')); } }
  async function onClose_(id: number)  { try { await closeClimateItem(id);   toast.success(t('cliClosed'));    await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed')); } }
  async function onRetire(id: number)  { if (!window.confirm(t('cliRetireConfirm'))) return; try { await retireClimateItem(id); toast.success(t('cliRetired')); await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed')); } }
  async function submitErmLink(values: Record<string, unknown>) {
    const id = linkingErmFor;
    if (id == null) return;
    const ermId = Number(values.ermRiskId);
    if (!Number.isFinite(ermId) || ermId <= 0) {
      toast.danger(isAr ? 'مُعرّف غير صالح' : 'Invalid ERM risk ID');
      return;
    }
    try {
      await linkClimateItemToErm(id, ermId);
      toast.success(t('cliLinkedErm'));
      setLinkingErmFor(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('cliActionFailed'));
    }
  }

  const ermLinkFields: FieldDef[] = [
    { kind: 'number', key: 'ermRiskId', required: true, min: 1, span: 12,
      label: isAr ? 'مُعرّف المخاطرة في سجلّ المخاطر المؤسسية (ERM)'
                  : 'ERM risk ID to link' },
  ];

  const FIELDS: FieldDef[] = [
    { kind: 'select', key: 'kind',         label: t('cliFldKind') + ' *',        required: true, span: 4,
      options: [{ value: 'RISK', label: isAr ? 'مخاطرة' : 'Risk' }, { value: 'OPPORTUNITY', label: isAr ? 'فرصة' : 'Opportunity' }] },
    { kind: 'select', key: 'category',     label: t('cliFldCategory') + ' *',    required: true, span: 4,
      options: [
        { value: 'PHYSICAL',     label: isAr ? 'مادي'          : 'Physical' },
        { value: 'TRANSITION',   label: isAr ? 'انتقالي'        : 'Transition' },
        { value: 'REGULATORY',   label: isAr ? 'تنظيمي'         : 'Regulatory' },
        { value: 'MARKET',       label: isAr ? 'سوق'           : 'Market' },
        { value: 'TECHNOLOGY',   label: isAr ? 'تقني'          : 'Technology' },
        { value: 'REPUTATION',   label: isAr ? 'سُمعة'          : 'Reputation' },
        { value: 'SUPPLY_CHAIN', label: isAr ? 'سلسلة الإمداد' : 'Supply chain' },
        { value: 'RESOURCE_EFFICIENCY', label: isAr ? 'كفاءة الموارد' : 'Resource efficiency (opp.)' },
        { value: 'ENERGY_SOURCE',       label: isAr ? 'مصدر طاقة'      : 'Energy source (opp.)' },
        { value: 'PRODUCT_SERVICE',     label: isAr ? 'منتج/خدمة'     : 'Product/service (opp.)' },
        { value: 'MARKETS',             label: isAr ? 'أسواق'         : 'Markets (opp.)' },
        { value: 'RESILIENCE',          label: isAr ? 'صمود'          : 'Resilience (opp.)' },
      ] },
    { kind: 'select', key: 'timeHorizon',  label: t('cliFldHorizon') + ' *',     required: true, span: 4,
      options: [
        { value: 'SHORT',  label: isAr ? 'قصير المدى (≤ سنة)'  : 'Short (≤1y)' },
        { value: 'MEDIUM', label: isAr ? 'متوسط المدى (1-5 س)' : 'Medium (1-5y)' },
        { value: 'LONG',   label: isAr ? 'طويل المدى (> 5 س)'  : 'Long (>5y)' },
      ] },
    { kind: 'text',     key: 'titleEn',       label: t('cliFldTitleEn') + ' *', required: true, span: 12 },
    { kind: 'text',     key: 'titleAr',       label: t('cliFldTitleAr'),         span: 12 },
    { kind: 'textarea', key: 'descriptionEn', label: t('cliFldDescription'),     rows: 3, span: 12 },
    { kind: 'number',   key: 'likelihood',    label: t('cliFldLikelihood') + ' *', required: true, min: 1, max: 5, span: 3 },
    { kind: 'number',   key: 'impact',        label: t('cliFldImpact') + ' *',     required: true, min: 1, max: 5, span: 3 },
    { kind: 'number',   key: 'reportingYear', label: t('cliFldYear'),              min: 2020, max: 2050, span: 3 },
    { kind: 'text',     key: 'ownerName',     label: t('cliFldOwner'),             span: 3 },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* KPI strip — risks vs opportunities + Rule-8 alert chip */}
      <div className="kpi-grid">
        <Kpi Icon={CloudLightning} label={t('cliKpiRisks')}    value={risks.length}         tone="warning" />
        <Kpi Icon={Target}          label={t('cliKpiOpps')}      value={opportunities.length} tone="success" />
        <Kpi Icon={AlertCircle}     label={t('cliKpiHighScore')} value={risks.filter(r => (r.riskScore ?? 0) >= 15).length} tone="danger" />
        <Kpi Icon={Link2}           label={t('cliKpiNeedsErm')}  value={needsErmLink.length}  tone={needsErmLink.length > 0 ? 'danger' : 'success'} />
      </div>

      <Card title={t('cliPageTitle')} Icon={CloudLightning}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>{t('cliNew')}</Button>}>
        {loading
          ? <InlineEmpty>{t('cliLoading')}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{t('cliEmpty')}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{t('cliColCode')}</th>
                    <th>{t('cliColTitle')}</th>
                    <th>{t('cliColKind')}</th>
                    <th>{t('cliColCategory')}</th>
                    <th>{t('cliColHorizon')}</th>
                    <th style={{ textAlign: 'center' }}>{t('cliColScore')}</th>
                    <th>{t('cliColStatus')}</th>
                    <th style={{ textAlign: 'end' }}>{t('cliColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(it => {
                    const tone = riskScoreTone(it.riskScore ?? 0);
                    const band = riskScoreBand(it.riskScore ?? 0, isAr ? 'ar' : 'en');
                    return (
                      <tr key={it.id}>
                        <td className="mono">{it.itemCode ?? `#${it.id}`}</td>
                        <td>
                          <div style={{ fontWeight: 600, color: 'var(--ink-1)', maxWidth: 360, lineHeight: 1.3 }}>
                            {isAr && it.titleAr ? it.titleAr : it.titleEn}
                          </div>
                          {it.ownerName && <div className="text-2xs text-muted" style={{ marginTop: 2 }}>{it.ownerName}</div>}
                        </td>
                        <td><Badge tone={it.kind === 'RISK' ? 'danger' : 'success'}>{isAr ? (it.kind === 'RISK' ? 'مخاطرة' : 'فرصة') : it.kind}</Badge></td>
                        <td className="text-xs">{it.category}</td>
                        <td><Badge tone="neutral">{it.timeHorizon}</Badge></td>
                        <td style={{ textAlign: 'center' }}>
                          <Badge tone={tone}>{it.riskScore} · {band}</Badge>
                        </td>
                        <td><Badge tone={it.status === 'CLOSED' ? 'success' : it.status === 'RETIRED' ? 'neutral' : it.status === 'TREATED' ? 'info' : 'warning'}>{it.status}</Badge></td>
                        <td style={{ textAlign: 'end' }}>
                          <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                            {/* Rule 8: offer ERM link inline when score >= 15 and not yet linked */}
                            {shouldOfferErmLink(it) && (
                              <Button variant="primary" size="sm" Icon={Link2}
                                onClick={() => setLinkingErmFor(it.id!)}>{t('cliActLinkErm')}</Button>
                            )}
                            {it.status === 'IDENTIFIED' && (
                              <Button variant="secondary" size="sm" Icon={ArrowRight} onClick={() => onAssess(it.id!)}>{t('cliActAssess')}</Button>
                            )}
                            {it.status === 'ASSESSED' && (
                              <Button variant="secondary" size="sm" Icon={ArrowRight} onClick={() => onTreat(it.id!)}>{t('cliActTreat')}</Button>
                            )}
                            {it.status === 'TREATED' && (
                              <Button variant="secondary" size="sm" Icon={CheckCircle2} onClick={() => onClose_(it.id!)}>{t('cliActClose')}</Button>
                            )}
                            {it.status !== 'RETIRED' && it.status !== 'CLOSED' && (
                              <Button variant="ghost" size="sm" Icon={Trash2}
                                title={isAr ? 'سحب من السجل' : 'Retire'}
                                onClick={() => onRetire(it.id!)} />
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={openNew}
        title={t('cliNewTitle')}
        fields={FIELDS}
        initial={{ kind: 'RISK', category: 'PHYSICAL', timeHorizon: 'MEDIUM', likelihood: 3, impact: 3, reportingYear: new Date().getFullYear() }}
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />

      {/* ERM-link prompt — replaces window.prompt. Stays in-window so the
          user can see which row they're linking from. */}
      <FormModal
        open={linkingErmFor !== null}
        title={isAr ? 'ربط بسجل المخاطر المؤسسية' : 'Link to ERM register'}
        fields={ermLinkFields}
        initial={{ ermRiskId: '' }}
        size="md"
        submitLabel={isAr ? 'ربط' : 'Link'}
        onClose={() => setLinkingErmFor(null)}
        onSubmit={submitErmLink}
      />
    </div>
  );
}

// ─── Corrective Actions tab (V58) ──────────────────────────────────────────
//
// CAPA-like remediation register that closes the loop on gaps surfaced
// by V55 (Climate Risks), V56 (GHG Scope-3 coverage <70%) and V57 (annex
// open_gaps_count). State machine: OPEN → IN_PROGRESS → COMPLETED →
// VERIFIED → CLOSED (any → CANCELLED). Per-row inline buttons mirror the
// allowed transitions; Rule B disables Complete until evidenceRef is set
// and Rule A prompts for a verifier name distinct from the owner.

function CapaTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<CorrectiveAction[]>([]);
  const [agg, setAgg]     = useState<CapaAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<CapaStatus | 'ALL'>('ALL');

  // Verify-flow modal state. We replace `window.prompt` (which is jarring
  // and unstyled) with a small inline FormModal: it holds the verifier
  // name, mirrors Rule A as you type, and stays in the same window so the
  // user can see the action they're verifying.
  const [verifyingAction, setVerifyingAction] = useState<CorrectiveAction | null>(null);

  async function reload() {
    setLoading(true);
    try {
      const [pg, a] = await Promise.all([
        listCapas({ size: 200 }),
        capaAggregate(),
      ]);
      setItems(pg.content);
      setAgg(a);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل الإجراءات التصحيحية' : 'Failed to load corrective actions'));
      setItems([]);
      setAgg(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  const filtered = statusFilter === 'ALL'
    ? items
    : items.filter(it => it.status === statusFilter);

  async function onStart(id: number)    { try { await startCapa(id);    toast.success(isAr ? 'بدأ التنفيذ' : 'Started');     await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); } }
  async function onComplete(id: number) { try { await completeCapa(id); toast.success(isAr ? 'تم الإكمال — في انتظار التحقّق' : 'Completed — awaiting verification'); await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); } }
  async function onClose_(id: number)   { try { await closeCapa(id);    toast.success(isAr ? 'تم الإغلاق' : 'Closed');         await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); } }
  async function onCancel(id: number)   {
    if (!window.confirm(isAr ? 'إلغاء هذا الإجراء التصحيحي؟ هذا الإجراء نهائي.' : 'Cancel this corrective action? This is terminal.')) return;
    try { await cancelCapa(id); toast.info(isAr ? 'تم الإلغاء' : 'Cancelled'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function submitVerify(values: Record<string, unknown>) {
    const a = verifyingAction;
    if (!a) return;
    const verifier = String(values.verifiedBy ?? '').trim();
    if (!verifier) {
      toast.danger(isAr ? 'اسم الجهة المتحقّقة مطلوب' : 'Verifier name is required');
      return;
    }
    // Rule A — client-side mirror so the user gets immediate feedback
    // instead of waiting for the 400 round-trip.
    if (a.ownerName && verifier.toLowerCase() === a.ownerName.trim().toLowerCase()) {
      toast.danger(isAr
        ? 'لا يمكن أن يكون المتحقّق هو نفس المالك (القاعدة A — فصل المهام).'
        : 'Verifier must differ from owner (Rule A — separation of duties).');
      return;
    }
    try {
      await verifyCapa(a.id!, verifier);
      toast.success(isAr ? 'تم التحقّق' : 'Verified');
      setVerifyingAction(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  const verifyFields: FieldDef[] = [
    { kind: 'text', key: 'verifiedBy', required: true, span: 12,
      label: isAr ? 'اسم الجهة المتحقّقة (يجب أن يختلف عن المالك)'
                  : 'Verifier name (must differ from owner)',
      placeholder: isAr ? 'مثال: لينا الحربي (مدير المخاطر)' : 'e.g. Lina Al-Harbi (Risk Manager)' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* KPI strip — live status counts + overdue alarm */}
      <div className="kpi-grid">
        <Kpi Icon={Wrench}        label={isAr ? 'مفتوحة' : 'Open'}          value={agg?.open ?? 0}        tone="warning" />
        <Kpi Icon={ArrowRight}    label={isAr ? 'قيد التنفيذ' : 'In progress'} value={agg?.inProgress ?? 0} tone="info" />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'بانتظار التحقّق' : 'Awaiting verification'} value={agg?.completed ?? 0} tone="primary" />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'مُتحقَّق منها' : 'Verified'} value={agg?.verified ?? 0}   tone="success" />
        <Kpi Icon={AlertCircle}   label={isAr ? 'متأخّرة' : 'Overdue'}        value={agg?.overdue ?? 0}     tone={(agg?.overdue ?? 0) > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Clock}         label={isAr ? 'مستحقّة قريباً' : 'Due soon'} value={agg?.dueSoon ?? 0}     tone={(agg?.dueSoon ?? 0) > 0 ? 'warning' : 'success'} />
      </div>

      <Card title={isAr ? 'سجلّ الإجراءات التصحيحية للاستدامة' : 'Sustainability Corrective Actions'}
            Icon={Wrench}>
        {/* Status filter chips */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
          {(['ALL', 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'VERIFIED', 'CLOSED', 'CANCELLED'] as const).map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              style={{
                padding: '4px 10px', fontSize: 11, fontWeight: 600,
                border: `1px solid ${statusFilter === s ? 'var(--primary)' : 'var(--border)'}`,
                background: statusFilter === s ? 'var(--primary-50)' : 'var(--surface-1)',
                color: statusFilter === s ? 'var(--primary)' : 'var(--ink-2)',
                borderRadius: 'var(--r-sm)', cursor: 'pointer',
              }}>
              {s === 'ALL' ? (isAr ? 'الكل' : 'All') : capaStatusLabel(s as CapaStatus, isAr ? 'ar' : 'en')}
            </button>
          ))}
          <span className="text-2xs text-muted" style={{ marginInlineStart: 'auto', alignSelf: 'center' }}>
            {filtered.length} / {items.length}
          </span>
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? (
              <InlineEmpty>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
                  <span>{statusFilter === 'ALL'
                    ? (isAr ? 'لا توجد إجراءات تصحيحية بعد' : 'No corrective actions yet')
                    : (isAr ? 'لا توجد إجراءات بهذه الحالة' : 'No actions match this filter')}</span>
                  {statusFilter !== 'ALL' && (
                    <Button variant="ghost" size="sm" Icon={XIcon} onClick={() => setStatusFilter('ALL')}>
                      {isAr ? 'إزالة التصفية' : 'Clear filter'}
                    </Button>
                  )}
                </div>
              </InlineEmpty>
            )
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'العنوان' : 'Title'}</th>
                    <th>{isAr ? 'المصدر' : 'Source'}</th>
                    <th>{isAr ? 'الأولوية' : 'Priority'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th>{isAr ? 'تاريخ الاستحقاق' : 'Due'}</th>
                    <th>{isAr ? 'الكلفة · المجهود' : 'Cost · Effort'}</th>
                    <th>{isAr ? 'المالك' : 'Owner'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(a => {
                    const dr = daysRemaining(a);
                    const overdue = dr !== null && dr < 0
                      && a.status !== 'CLOSED' && a.status !== 'CANCELLED' && a.status !== 'VERIFIED';
                    const dueSoon = dr !== null && dr >= 0 && dr <= 30
                      && a.status !== 'CLOSED' && a.status !== 'CANCELLED' && a.status !== 'VERIFIED';
                    const allowed = capaAllowedNext(a.status);
                    const showCost = a.costEstimateSar != null && a.costEstimateSar > 0;
                    const showEffort = a.effortHours != null && a.effortHours > 0;
                    return (
                      <tr key={a.id}>
                        <td className="mono text-2xs">{a.actionCode ?? `#${a.id}`}</td>
                        <td>
                          <div style={{ fontWeight: 600, color: 'var(--ink-1)', maxWidth: 360, lineHeight: 1.3 }}>
                            {isAr && a.titleAr ? a.titleAr : a.titleEn}
                          </div>
                          {a.evidenceRef && (
                            <div className="text-2xs text-muted"
                              style={{ marginTop: 2, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                              <Paperclip size={11} style={{ flexShrink: 0 }} />
                              <span>{isAr ? 'الدليل:' : 'Evidence:'}</span>
                              <span className="mono">{a.evidenceRef}</span>
                            </div>
                          )}
                        </td>
                        <td>
                          <Badge tone="neutral">{capaSourceLabel(a.sourceType, isAr ? 'ar' : 'en')}</Badge>
                          {a.sourceRef && (
                            <div className="mono text-2xs text-muted" style={{ marginTop: 2 }}>{a.sourceRef}</div>
                          )}
                        </td>
                        <td><Badge tone={capaPriorityTone(a.priority)}>{capaPriorityLabel(a.priority, isAr ? 'ar' : 'en')}</Badge></td>
                        <td><Badge tone={capaStatusTone(a.status)}>{capaStatusLabel(a.status, isAr ? 'ar' : 'en')}</Badge></td>
                        <td className="mono text-2xs">
                          <div style={{ color: overdue ? 'var(--danger-fg)' : dueSoon ? 'var(--warning-fg)' : undefined }}>
                            {a.targetDate ?? '—'}
                          </div>
                          {dr !== null && a.status !== 'CLOSED' && a.status !== 'CANCELLED' && (
                            <div className="text-2xs" style={{ color: overdue ? 'var(--danger-fg)' : dueSoon ? 'var(--warning-fg)' : 'var(--ink-3)' }}>
                              {overdue
                                ? (isAr ? `متأخّر ${Math.abs(dr)} يوماً` : `${Math.abs(dr)}d overdue`)
                                : (isAr ? `${dr} يوماً متبقّياً` : `${dr}d left`)}
                            </div>
                          )}
                        </td>
                        <td className="text-2xs">
                          {showCost && (
                            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: 'var(--ink-2)' }}
                                 title={formatSarFull(a.costEstimateSar!, isAr ? 'ar' : 'en')}>
                              <Coins size={11} style={{ flexShrink: 0 }} />
                              <span className="mono">{formatSarCompact(a.costEstimateSar!, isAr ? 'ar' : 'en')}</span>
                            </div>
                          )}
                          {showEffort && (
                            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: 'var(--ink-3)', marginInlineStart: showCost ? 8 : 0 }}>
                              <Hourglass size={11} style={{ flexShrink: 0 }} />
                              <span className="mono">{a.effortHours}{isAr ? ' س' : 'h'}</span>
                            </div>
                          )}
                          {!showCost && !showEffort && <span className="text-muted">—</span>}
                        </td>
                        <td className="text-xs">{a.ownerName ?? '—'}</td>
                        <td style={{ textAlign: 'end' }}>
                          <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                            {allowed.includes('IN_PROGRESS') && (
                              <Button variant="secondary" size="sm" Icon={ArrowRight} onClick={() => onStart(a.id!)}>
                                {isAr ? 'بدء' : 'Start'}
                              </Button>
                            )}
                            {allowed.includes('COMPLETED') && (
                              <Button variant="secondary" size="sm" Icon={CheckCircle2}
                                disabled={!canComplete(a)}
                                title={!canComplete(a) ? (isAr ? 'يجب إضافة دليل (القاعدة B) قبل الإكمال' : 'Evidence required before Complete (Rule B)') : undefined}
                                onClick={() => onComplete(a.id!)}>
                                {isAr ? 'إكمال' : 'Complete'}
                              </Button>
                            )}
                            {allowed.includes('VERIFIED') && (
                              <Button variant="primary" size="sm" Icon={CheckCircle2} onClick={() => setVerifyingAction(a)}>
                                {isAr ? 'تحقّق' : 'Verify'}
                              </Button>
                            )}
                            {allowed.includes('CLOSED') && (
                              <Button variant="primary" size="sm" Icon={CheckCircle2} onClick={() => onClose_(a.id!)}>
                                {isAr ? 'إغلاق' : 'Close'}
                              </Button>
                            )}
                            {allowed.includes('CANCELLED') && (
                              <Button variant="ghost" size="sm" Icon={Trash2}
                                title={isAr ? 'إلغاء' : 'Cancel'}
                                onClick={() => onCancel(a.id!)} />
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
      </Card>

      {/* Verify-action modal (replaces window.prompt). The header carries
          the action code + title so the user has full context while they
          type the verifier name; Rule A is enforced on submit. */}
      <FormModal
        open={verifyingAction !== null}
        title={verifyingAction
          ? `${isAr ? 'تحقّق من الإجراء' : 'Verify action'} · ${verifyingAction.actionCode ?? `#${verifyingAction.id}`}`
          : ''}
        fields={verifyFields}
        initial={{ verifiedBy: '' }}
        size="md"
        submitLabel={isAr ? 'تأكيد التحقّق' : 'Confirm verification'}
        onClose={() => setVerifyingAction(null)}
        onSubmit={submitVerify}
      />
    </div>
  );
}

// ─── Executive Sustainability Dashboard (V64 follow-up — slice 11) ──────────
//
// The 12-widget executive dashboard composed from /api/sustainability/
// dashboard. One read-only round-trip hydrates every widget; widgets
// pull from sub-objects on the payload (cma, tcfd, ghg, sgi, etc.).
//
// Widget grid (responsive auto-fit):
//   Row 1 (hero):  ESG Overall Score gauge | Top Gaps panel
//   Row 2 (GHG):   Total emissions card | Scope breakdown | Verification donut
//   Row 3 (regs):  SGI tier counts | CMA readiness | TCFD alignment
//   Row 4 (ops):   Climate risks | Corrective actions | Evidence completeness
//   Row 5 (close): Annex readiness gauge | Materiality matrix preview
//   Row 6 (trend): Existing emissions timeseries chart (preserved)

function OverviewTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';
  const [data, setData] = useState<DashboardPayload | null>(null);
  // Slice 14-C — settings drive Rule 14 banner thresholds. Loaded in
  // parallel with the dashboard; soft-fail (null) if the call errors,
  // banner just won't render in that case.
  const [settings, setSettings] = useState<SustSettings | null>(null);
  const [loading, setLoading] = useState(true);

  async function reload() {
    setLoading(true);
    try {
      const [dash, s] = await Promise.all([
        getDashboard(2025),
        getSettings().catch(() => null),  // banner is non-critical
      ]);
      setData(dash);
      setSettings(s as SustSettings | null);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل لوحة المعلومات' : 'Failed to load dashboard'));
      setData(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  if (loading) {
    return <InlineEmpty>{isAr ? 'جارٍ تحميل لوحة المعلومات…' : 'Loading dashboard…'}</InlineEmpty>;
  }
  if (!data) {
    return <InlineEmpty>{isAr ? 'لا توجد بيانات' : 'No data'}</InlineEmpty>;
  }

  // Rule 14 — show readiness banner when score drops below the
  // configured warning / critical thresholds.
  const score = Number(data.overall.score);
  const warnThr = settings?.esgReadinessWarningThreshold ?? 60;
  const critThr = settings?.esgReadinessCriticalThreshold ?? 40;
  const bannerTone: 'danger' | 'warning' | null =
      score < critThr ? 'danger'
    : score < warnThr ? 'warning'
    : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Rule 14 — Low ESG-readiness banner */}
      {bannerTone && (
        <div style={{
          padding: '12px 16px', borderRadius: 'var(--r-md)',
          background: bannerTone === 'danger' ? 'var(--danger-bg)' : 'var(--warning-bg)',
          color:      bannerTone === 'danger' ? 'var(--danger-fg)' : 'var(--warning-fg)',
          border: `1px solid ${bannerTone === 'danger' ? 'var(--danger)' : 'var(--warning)'}`,
          display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 12, alignItems: 'center',
        }}>
          <AlertCircle size={20} />
          <div style={{ lineHeight: 1.5 }}>
            <div style={{ fontWeight: 700, fontSize: 13 }}>
              {bannerTone === 'danger'
                ? (isAr ? 'تحذير حرج — جاهزية الاستدامة منخفضة جداً' : 'Critical — ESG readiness severely below threshold')
                : (isAr ? 'تحذير — جاهزية الاستدامة تحت العتبة' : 'Warning — ESG readiness below threshold')}
            </div>
            <div style={{ fontSize: 12, marginTop: 2 }}>
              {isAr
                ? `الدرجة الحالية ${score}/100 — العتبة المعدّة ${bannerTone === 'danger' ? critThr : warnThr}/100. راجع أبرز الثغرات أدناه.`
                : `Current score ${score}/100 — configured threshold ${bannerTone === 'danger' ? critThr : warnThr}/100. Review top gaps below.`}
            </div>
          </div>
          <Button size="sm" variant="ghost" onClick={() => void reload()} title={isAr ? 'تحديث' : 'Refresh'}>
            <ArrowRight size={14} />
          </Button>
        </div>
      )}

      {/* ── Row 1: Hero — ESG Overall Score + Top Gaps ────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(280px, 1fr) minmax(320px, 1.5fr)', gap: 16 }}>
        <EsgOverallScoreWidget data={data} isAr={isAr} />
        <TopGapsWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 2: GHG cluster ───────────────────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
        <GhgTotalWidget data={data} isAr={isAr} />
        <ScopeBreakdownWidget data={data} isAr={isAr} />
        <VerificationStatusWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 3: Regulatory readiness ──────────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
        <SgiProgressWidget data={data} isAr={isAr} />
        <CmaReadinessWidget data={data} isAr={isAr} />
        <TcfdAlignmentWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 4: Operational tracking ──────────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
        <ClimateRiskWidget data={data} isAr={isAr} />
        <CapaTrackerWidget data={data} isAr={isAr} />
        <EvidenceCompletenessWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 5: Annex readiness + Materiality matrix ──────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(280px, 1fr) minmax(320px, 1.5fr)', gap: 16 }}>
        <AnnexReadinessWidget data={data} isAr={isAr} />
        <MaterialityMatrixWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 6: Emissions timeseries trend (preserved chart) ──── */}
      <Card title={isAr ? 'مسار الانبعاثات السنوية' : 'Annual emissions trend'} Icon={TrendingDown}>
        <EmissionsTrendChart isAr={isAr} />
      </Card>
    </div>
  );
}

// ─── Dashboard widgets ──────────────────────────────────────────────────────

function EsgOverallScoreWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const score = Number(data.overall.score);
  const band = data.overall.band;
  const color = esgBandColor(band);
  // SVG ring: arc length proportional to score
  const radius = 64;
  const stroke = 12;
  const circ = 2 * Math.PI * radius;
  const dash = (Math.min(100, Math.max(0, score)) / 100) * circ;

  return (
    <Card title={isAr ? 'مؤشّر الاستدامة الشامل' : 'ESG Overall Score'} Icon={Target}>
      <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
        <svg width={160} height={160} viewBox="0 0 160 160">
          <circle cx={80} cy={80} r={radius} stroke="var(--surface-2)" strokeWidth={stroke} fill="none" />
          <circle cx={80} cy={80} r={radius} stroke={color} strokeWidth={stroke} fill="none"
                  strokeDasharray={`${dash} ${circ}`} strokeDashoffset={0}
                  strokeLinecap="round" transform="rotate(-90 80 80)" />
          <text x={80} y={78} textAnchor="middle" style={{ fontSize: 32, fontWeight: 800, fill: 'var(--ink-1)' }}>{score}</text>
          <text x={80} y={100} textAnchor="middle" style={{ fontSize: 11, fill: 'var(--ink-3)' }}>/ 100</text>
        </svg>
        <div style={{ flex: 1 }}>
          <Badge tone={esgBandTone(band)}>{esgBandLabel(band, isAr ? 'ar' : 'en')}</Badge>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 8, lineHeight: 1.5 }}>
            {isAr
              ? 'مؤشّر مركّب موزون (التحقّق من الانبعاثات + التزامات SGI + إفصاحات CMA + TCFD + الإجراءات التصحيحية + الأدلة + المخاطر المناخية).'
              : 'Weighted composite (GHG verification + SGI on-track + CMA + TCFD + corrective actions + evidence + climate risks).'}
          </div>
          <details style={{ marginTop: 8, fontSize: 11 }}>
            <summary style={{ cursor: 'pointer', color: 'var(--ink-2)', fontWeight: 600 }}>
              {isAr ? 'تفاصيل الحساب' : 'Score breakdown'}
            </summary>
            <table style={{ width: '100%', marginTop: 6, fontSize: 11 }}>
              <tbody>
                {Object.entries(data.overall.breakdown).map(([k, v]) => (
                  <tr key={k}>
                    <td style={{ color: 'var(--ink-3)', padding: '2px 0' }}>{k}</td>
                    <td className="mono" style={{ textAlign: 'end' }}>{Number(v).toFixed(1)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </details>
        </div>
      </div>
    </Card>
  );
}

function TopGapsWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  return (
    <Card title={isAr ? 'أبرز الثغرات التنفيذية' : 'Top ESG gaps'} Icon={AlertCircle}>
      {data.topGaps.length === 0 ? (
        <div style={{ padding: 12, fontSize: 12, color: 'var(--success-fg)', lineHeight: 1.5 }}>
          <CheckCircle2 size={14} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 6 }} />
          {isAr ? 'لا توجد ثغرات تنفيذية ظاهرة في الدورة الحالية.' : 'No executive-level gaps detected for the current cycle.'}
        </div>
      ) : (
        <ul style={{ margin: 0, paddingInlineStart: 18, fontSize: 12, color: 'var(--ink-1)', lineHeight: 1.8 }}>
          {data.topGaps.map(g => (
            <li key={g} style={{ marginBottom: 6 }}>
              <span style={{ color: g.includes('OVERDUE') || g.includes('SCOPE_3') || g.includes('RESILIENCE') ? 'var(--danger-fg)' : 'var(--warning-fg)' }}>●</span>
              {' '}
              {topGapLabel(g, isAr ? 'ar' : 'en')}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function GhgTotalWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const total = Number(data.ghg.grandTotal);
  const yoy = Number(data.ghgYoy.totalYoY);
  return (
    <Card title={isAr ? 'إجمالي الانبعاثات' : 'GHG total emissions'} Icon={Leaf}>
      <div style={{ fontSize: 32, fontWeight: 800, color: 'var(--ink-1)', lineHeight: 1, letterSpacing: '-0.025em' }}>
        {total.toLocaleString()}
      </div>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4 }}>tCO2e — {data.ghg.year}</div>
      <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
        <span style={{ color: yoy < 0 ? 'var(--success-fg)' : yoy > 0 ? 'var(--danger-fg)' : 'var(--ink-3)' }}>
          {yoy < 0 ? <TrendingDown size={14} style={{ display: 'inline-block', verticalAlign: 'middle' }} /> : <TrendingUp size={14} style={{ display: 'inline-block', verticalAlign: 'middle' }} />}
          {' '}{yoy > 0 ? '+' : ''}{yoy.toFixed(1)}% {isAr ? 'مقارنة بـ' : 'vs'} {data.ghgYoy.year - 1}
        </span>
      </div>
      {data.ghg.scope3PartialFlag && (
        <div style={{ marginTop: 8, fontSize: 11, color: 'var(--warning-fg)' }}>
          <AlertCircle size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
          {isAr ? 'تغطية النطاق 3 جزئية' : 'Scope 3 partial coverage'} ({Number(data.ghg.scope3Coverage).toFixed(0)}%)
        </div>
      )}
    </Card>
  );
}

function ScopeBreakdownWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const s1 = Number(data.ghg.scope1Total);
  const s2 = Number(data.ghg.scope2Total);
  const s3 = Number(data.ghg.scope3Total);
  const total = s1 + s2 + s3;
  const rows = [
    { label: isAr ? 'النطاق 1' : 'Scope 1', value: s1, color: '#10b981', yoy: Number(data.ghgYoy.scope1YoY) },
    { label: isAr ? 'النطاق 2' : 'Scope 2', value: s2, color: '#0ea5e9', yoy: Number(data.ghgYoy.scope2YoY) },
    { label: isAr ? 'النطاق 3' : 'Scope 3', value: s3, color: '#f59e0b', yoy: Number(data.ghgYoy.scope3YoY) },
  ];
  return (
    <Card title={isAr ? 'توزيع النطاقات' : 'Scope breakdown'} Icon={Leaf}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {rows.map(r => {
          const pct = total > 0 ? (r.value / total) * 100 : 0;
          return (
            <div key={r.label}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, marginBottom: 4 }}>
                <span style={{ fontWeight: 600 }}>{r.label}</span>
                <span className="mono">{r.value.toLocaleString()} ({pct.toFixed(0)}%)</span>
              </div>
              <div style={{ height: 6, background: 'var(--surface-2)', borderRadius: 3, overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${pct}%`, background: r.color, borderRadius: 3 }} />
              </div>
              <div className="text-2xs" style={{ marginTop: 2, color: r.yoy < 0 ? 'var(--success-fg)' : 'var(--danger-fg)' }}>
                YoY {r.yoy > 0 ? '+' : ''}{r.yoy.toFixed(1)}%
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

function VerificationStatusWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  // Verification rate from the dashboard breakdown
  const verifRate = Number(data.overall.breakdown.ghgVerification);
  return (
    <Card title={isAr ? 'حالة التحقّق' : 'Verification status'} Icon={CheckCircle2}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <div style={{ position: 'relative', width: 100, height: 100 }}>
          <svg viewBox="0 0 100 100" style={{ transform: 'rotate(-90deg)' }}>
            <circle cx={50} cy={50} r={40} stroke="var(--surface-2)" strokeWidth={12} fill="none" />
            <circle cx={50} cy={50} r={40} stroke="var(--success)" strokeWidth={12} fill="none"
                    strokeDasharray={`${(verifRate / 100) * 2 * Math.PI * 40} ${2 * Math.PI * 40}`}
                    strokeLinecap="round" />
          </svg>
          <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ fontSize: 18, fontWeight: 800 }}>{verifRate.toFixed(0)}%</div>
            <div style={{ fontSize: 9, color: 'var(--ink-3)' }}>{isAr ? 'مُتحقَّق' : 'verified'}</div>
          </div>
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5, flex: 1 }}>
          {isAr
            ? `${data.ghg.recordCount} سجل انبعاثات إجمالاً. التحقّق الخارجي يدعم الإفصاحات الرئيسية.`
            : `${data.ghg.recordCount} GHG records total. External verification underpins the headline disclosures.`}
        </div>
      </div>
    </Card>
  );
}

function SgiProgressWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const onTrack = data.sgi.byTier.ON_TRACK ?? 0;
  const atRisk  = data.sgi.byTier.AT_RISK ?? 0;
  const delayed = data.sgi.byTier.DELAYED ?? 0;
  const total = onTrack + atRisk + delayed;
  return (
    <Card title={isAr ? 'التزامات SGI' : 'SGI commitments'} Icon={Target}>
      <div style={{ fontSize: 24, fontWeight: 800, marginBottom: 4 }}>
        {onTrack}/{total} <span style={{ fontSize: 12, fontWeight: 400, color: 'var(--ink-3)' }}>{isAr ? 'على المسار' : 'on track'}</span>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 6, marginTop: 12, fontSize: 11 }}>
        <div style={{ padding: 8, background: 'var(--success-bg)', borderRadius: 'var(--r-sm)' }}>
          <div style={{ color: 'var(--success-fg)', fontWeight: 700, fontSize: 16 }}>{onTrack}</div>
          <div style={{ color: 'var(--ink-3)' }}>{isAr ? 'على المسار' : 'On track'}</div>
        </div>
        <div style={{ padding: 8, background: 'var(--warning-bg)', borderRadius: 'var(--r-sm)' }}>
          <div style={{ color: 'var(--warning-fg)', fontWeight: 700, fontSize: 16 }}>{atRisk}</div>
          <div style={{ color: 'var(--ink-3)' }}>{isAr ? 'مُعرَّض' : 'At risk'}</div>
        </div>
        <div style={{ padding: 8, background: 'var(--danger-bg)', borderRadius: 'var(--r-sm)' }}>
          <div style={{ color: 'var(--danger-fg)', fontWeight: 700, fontSize: 16 }}>{delayed}</div>
          <div style={{ color: 'var(--ink-3)' }}>{isAr ? 'متأخّر' : 'Delayed'}</div>
        </div>
      </div>
    </Card>
  );
}

function CmaReadinessWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const pct = data.cma.readinessPct;
  const disclosed = data.cma.byStatus.DISCLOSED ?? 0;
  const partial   = data.cma.byStatus.PARTIAL ?? 0;
  const evidenceReq = data.cma.byStatus.EVIDENCE_REQUIRED ?? 0;
  return (
    <Card title={isAr ? 'جاهزية إفصاحات CMA' : 'CMA disclosure readiness'} Icon={FileText}>
      <div style={{ fontSize: 24, fontWeight: 800 }}>{pct}%</div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
        {disclosed}/{data.cma.applicable} {isAr ? 'مُفصَح' : 'disclosed'}
      </div>
      <div style={{ height: 8, background: 'var(--surface-2)', borderRadius: 4, marginBottom: 8 }}>
        <div style={{ height: '100%', width: `${pct}%`,
          background: pct >= 80 ? 'var(--success)' : pct >= 50 ? 'var(--warning)' : 'var(--danger)',
          borderRadius: 4 }} />
      </div>
      <div style={{ display: 'flex', gap: 6, fontSize: 10, flexWrap: 'wrap' }}>
        <Badge tone="warning">{partial} {isAr ? 'جزئي' : 'Partial'}</Badge>
        {evidenceReq > 0 && <Badge tone="danger">{evidenceReq} {isAr ? 'يتطلّب دليلاً' : 'Evidence req.'}</Badge>}
      </div>
    </Card>
  );
}

function TcfdAlignmentWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const pct = data.tcfd.alignmentPct;
  return (
    <Card title={isAr ? 'محاذاة TCFD' : 'TCFD alignment'} Icon={FileText}>
      <div style={{ fontSize: 24, fontWeight: 800 }}>{pct}%</div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
        {data.tcfd.byStatus.DISCLOSED}/{data.tcfd.applicable} {isAr ? 'مُفصَح' : 'disclosed'}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 4, fontSize: 10, marginBottom: 8 }}>
        {(['GOVERNANCE', 'STRATEGY', 'RISK_MGMT', 'METRICS_TARGETS'] as const).map(p => {
          const total = data.tcfd.byPillar[p] ?? 0;
          const ok = data.tcfd.disclosedByPillar[p] ?? 0;
          return (
            <div key={p} style={{ padding: 6, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', textAlign: 'center' }}>
              <div style={{ fontWeight: 700, fontSize: 12 }}>{ok}/{total}</div>
              <div style={{ color: 'var(--ink-3)', fontSize: 9, marginTop: 2 }}>
                {p === 'GOVERNANCE' ? (isAr ? 'حوكمة' : 'Gov')
                 : p === 'STRATEGY' ? (isAr ? 'استراتيجية' : 'Strat')
                 : p === 'RISK_MGMT' ? (isAr ? 'مخاطر' : 'Risk')
                 : (isAr ? 'مقاييس' : 'Metrics')}
              </div>
            </div>
          );
        })}
      </div>
      {data.tcfd.strategyResilienceGap && (
        <div style={{ fontSize: 10, color: 'var(--warning-fg)' }}>
          <AlertCircle size={10} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
          {isAr ? 'القاعدة 7: ثغرة في صمود الاستراتيجية' : 'Rule 7: Strategy resilience gap'}
        </div>
      )}
    </Card>
  );
}

function ClimateRiskWidget({ isAr }: { data: DashboardPayload; isAr: boolean }) {
  // Lean preview — the full heat-map lives on the climate-risks tab.
  // Dashboard shows the 5×5 grid with placeholder counts.
  return (
    <Card title={isAr ? 'مصفوفة المخاطر المناخية' : 'Climate risk heatmap'} Icon={CloudLightning}>
      <div style={{ position: 'relative', width: '100%', maxWidth: 180, aspectRatio: '1 / 1', margin: '0 auto' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gridTemplateRows: 'repeat(5, 1fr)', gap: 2, width: '100%', height: '100%' }}>
          {Array.from({ length: 25 }, (_, i) => {
            const x = i % 5;
            const y = Math.floor(i / 5);
            const severity = (4 - y) * (x + 1);  // higher right + top = worse
            const bg = severity >= 16 ? 'var(--danger)'
                    : severity >= 9 ? 'var(--warning)'
                    : severity >= 4 ? 'var(--info)' : 'var(--success-bg)';
            return <div key={i} style={{ background: bg, borderRadius: 2 }} />;
          })}
        </div>
      </div>
      <div style={{ fontSize: 10, color: 'var(--ink-3)', textAlign: 'center', marginTop: 8 }}>
        {isAr ? 'الأثر × الاحتمالية' : 'Impact × Likelihood'}
      </div>
    </Card>
  );
}

function CapaTrackerWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  return (
    <Card title={isAr ? 'الإجراءات التصحيحية' : 'Corrective actions'} Icon={Wrench}>
      <div style={{ fontSize: 24, fontWeight: 800 }}>{data.capa.activeTotal}</div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>{isAr ? 'نشطة' : 'active'}</div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 4, fontSize: 10 }}>
        <div style={{ padding: 6, background: 'var(--warning-bg)', borderRadius: 'var(--r-sm)', textAlign: 'center' }}>
          <div style={{ fontWeight: 700, fontSize: 13 }}>{data.capa.open}</div>
          <div style={{ color: 'var(--ink-3)' }}>{isAr ? 'مفتوحة' : 'Open'}</div>
        </div>
        <div style={{ padding: 6, background: 'var(--info-bg)', borderRadius: 'var(--r-sm)', textAlign: 'center' }}>
          <div style={{ fontWeight: 700, fontSize: 13 }}>{data.capa.inProgress}</div>
          <div style={{ color: 'var(--ink-3)' }}>{isAr ? 'تنفيذ' : 'WIP'}</div>
        </div>
        <div style={{ padding: 6, background: 'var(--success-bg)', borderRadius: 'var(--r-sm)', textAlign: 'center' }}>
          <div style={{ fontWeight: 700, fontSize: 13 }}>{data.capa.verified}</div>
          <div style={{ color: 'var(--ink-3)' }}>{isAr ? 'مُتحقَّق' : 'Verified'}</div>
        </div>
        <div style={{ padding: 6, background: data.capa.overdue > 0 ? 'var(--danger-bg)' : 'var(--surface-2)', borderRadius: 'var(--r-sm)', textAlign: 'center' }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: data.capa.overdue > 0 ? 'var(--danger-fg)' : undefined }}>{data.capa.overdue}</div>
          <div style={{ color: 'var(--ink-3)' }}>{isAr ? 'متأخّرة' : 'Overdue'}</div>
        </div>
      </div>
    </Card>
  );
}

function EvidenceCompletenessWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const approved = data.evidence.byStatus.APPROVED ?? 0;
  const total = data.evidence.total;
  const pct = total > 0 ? Math.round((approved / total) * 100) : 0;
  return (
    <Card title={isAr ? 'اكتمال الأدلة' : 'Evidence completeness'} Icon={Paperclip}>
      <div style={{ fontSize: 24, fontWeight: 800 }}>{pct}%</div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
        {approved}/{total} {isAr ? 'معتمدة' : 'approved'}
      </div>
      <div style={{ height: 8, background: 'var(--surface-2)', borderRadius: 4, marginBottom: 8 }}>
        <div style={{ height: '100%', width: `${pct}%`, background: pct >= 80 ? 'var(--success)' : pct >= 50 ? 'var(--info)' : 'var(--warning)', borderRadius: 4 }} />
      </div>
      {data.evidence.expiringSoon30d > 0 && (
        <div style={{ fontSize: 11, color: 'var(--warning-fg)' }}>
          <Clock size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
          {data.evidence.expiringSoon30d} {isAr ? 'تنتهي خلال 30 يوماً' : 'expiring in 30d'}
        </div>
      )}
    </Card>
  );
}

function AnnexReadinessWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  // Composite — averages CMA readiness + TCFD alignment + evidence approval rate
  const cma = data.cma.readinessPct;
  const tcfd = data.tcfd.alignmentPct;
  const evidence = Number(data.overall.breakdown.evidenceApproved);
  const composite = Math.round((cma + tcfd + evidence) / 3);
  return (
    <Card title={isAr ? 'جاهزية الملحق السنوي' : 'Annex readiness gauge'} Icon={FileText}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '12px 0' }}>
        <svg viewBox="0 0 200 110" style={{ width: '100%', maxWidth: 200 }}>
          {/* Semi-circle gauge */}
          <path d="M 20 100 A 80 80 0 0 1 180 100" stroke="var(--surface-2)" strokeWidth={14} fill="none" />
          <path d={`M 20 100 A 80 80 0 0 1 ${20 + (160 * composite / 100)} ${100 - Math.sin(Math.PI * composite / 100) * 80}`}
                stroke={composite >= 80 ? 'var(--success)' : composite >= 50 ? 'var(--warning)' : 'var(--danger)'}
                strokeWidth={14} fill="none" strokeLinecap="round" />
          <text x={100} y={88} textAnchor="middle" style={{ fontSize: 28, fontWeight: 800, fill: 'var(--ink-1)' }}>{composite}%</text>
        </svg>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: -8, textAlign: 'center' }}>
          {isAr ? 'المتوسّط الموزون لـ CMA + TCFD + الأدلة' : 'Weighted mean of CMA + TCFD + Evidence'}
        </div>
      </div>
    </Card>
  );
}

function MaterialityMatrixWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const matrix = data.materiality.matrix;
  return (
    <Card title={isAr ? 'الجوهرية — أهم الموضوعات' : 'Materiality — top topics'} Icon={Target}>
      <div style={{ display: 'flex', gap: 8, marginBottom: 8, fontSize: 11 }}>
        <Badge tone="danger">{data.materiality.criticalCount} {isAr ? 'حرج' : 'Critical'}</Badge>
        <Badge tone="warning">{data.materiality.highCount} {isAr ? 'مرتفع' : 'High'}</Badge>
      </div>
      {matrix.slice(0, 4).map(t => (
        <div key={t.id} style={{
          padding: 8, marginBottom: 6, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
          display: 'grid', gridTemplateColumns: '1fr auto', gap: 8,
        }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>
            {isAr && t.topicAr ? t.topicAr : t.topicEn}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11 }}>
            <span className="mono" style={{ fontWeight: 700 }}>{Number(t.materialityScore).toFixed(1)}</span>
            <span style={{
              width: 10, height: 10, borderRadius: '50%',
              background: t.priorityTier === 'CRITICAL' ? 'var(--danger)'
                       : t.priorityTier === 'HIGH'    ? 'var(--warning)'
                       : t.priorityTier === 'MEDIUM'  ? 'var(--info)' : 'var(--ink-3)',
            }} />
          </div>
        </div>
      ))}
      {matrix.length === 0 && (
        <div style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'center', padding: 12 }}>
          {isAr ? 'لا توجد موضوعات جوهرية مُقيَّمة بعد' : 'No materiality topics scored yet'}
        </div>
      )}
    </Card>
  );
}

function EmissionsTrendChart({ isAr }: { isAr: boolean }) {
  // Preserve the existing visual chart from EMISSIONS_TIMESERIES so the
  // multi-year trend story is retained even though the rest of the
  // dashboard pulls from live aggregates.
  const maxTotal = Math.max(...EMISSIONS_TIMESERIES.map(r => r.scope1 + r.scope2 + r.scope3));
  return (
    <>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
        {isAr ? 'الانبعاثات الإجمالية مكدّسة بالنطاقات عبر السنوات.' : 'Total emissions stacked by scope across years.'}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${EMISSIONS_TIMESERIES.length}, 1fr)`, gap: 12, alignItems: 'end', height: 220 }}>
        {EMISSIONS_TIMESERIES.map(r => {
          const total = r.scope1 + r.scope2 + r.scope3;
          const heightPct = (total / maxTotal) * 100;
          const s1Pct = (r.scope1 / total) * 100;
          const s2Pct = (r.scope2 / total) * 100;
          const s3Pct = (r.scope3 / total) * 100;
          return (
            <div key={r.year} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, height: '100%' }}>
              <div style={{ flex: 1, width: '100%', display: 'flex', flexDirection: 'column-reverse', alignItems: 'center' }}>
                <div style={{ width: '60%', height: `${heightPct}%`, display: 'flex', flexDirection: 'column', borderRadius: '4px 4px 0 0', overflow: 'hidden', boxShadow: '0 1px 0 rgba(0,0,0,0.08)' }}>
                  <div style={{ flex: s3Pct, background: '#f59e0b', minHeight: 2 }} title={`Scope 3: ${r.scope3.toLocaleString()}`} />
                  <div style={{ flex: s2Pct, background: '#0ea5e9', minHeight: 2 }} title={`Scope 2: ${r.scope2.toLocaleString()}`} />
                  <div style={{ flex: s1Pct, background: '#10b981', minHeight: 2 }} title={`Scope 1: ${r.scope1.toLocaleString()}`} />
                </div>
              </div>
              <div style={{ fontSize: 11, fontWeight: 700 }}>{r.year}</div>
              <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{(total / 1000).toFixed(0)}k</div>
            </div>
          );
        })}
      </div>
      <div style={{ display: 'flex', gap: 12, marginTop: 16, fontSize: 11, color: 'var(--ink-3)' }}>
        <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#10b981', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{isAr ? 'النطاق 1' : 'Scope 1'}</span>
        <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#0ea5e9', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{isAr ? 'النطاق 2' : 'Scope 2'}</span>
        <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#f59e0b', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{isAr ? 'النطاق 3' : 'Scope 3'}</span>
      </div>
    </>
  );
}

// ─── Calculator tab ─────────────────────────────────────────────────────────

function CalculatorTab({ entries, onAdd, onDelete }: {
  entries: CalculatorEntry[];
  onAdd: (e: { factorCode: string; amount: number; occurredAt: string; noteEn?: string; noteAr?: string }) => void;
  onDelete: (id: string) => void;
}) {
  const { t, lang } = useI18n();
  const [factorCode, setFactorCode] = useState(EMISSION_FACTORS[0].code);
  const [amount, setAmount] = useState<string>('');
  const [occurredAt, setOccurredAt] = useState(() => new Date().toISOString().slice(0, 10));
  const [note, setNote] = useState('');

  const factor = EMISSION_FACTORS.find(f => f.code === factorCode)!;
  const computed = (parseFloat(amount) || 0) * factor.kgCo2ePerUnit;

  const totalKg = entries.reduce((acc, e) => acc + e.computedKgCo2e, 0);

  function submit() {
    const amt = parseFloat(amount);
    if (!amt || amt <= 0) return;
    onAdd({
      factorCode, amount: amt, occurredAt,
      noteEn: lang === 'en' ? note : undefined,
      noteAr: lang === 'ar' ? note : undefined,
    });
    setAmount(''); setNote('');
  }

  return (
    <>
      <Card title={t('esgCalcTitle')} Icon={Calculator}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {t('esgCalcSub')}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 2fr auto', gap: 8, alignItems: 'end' }}>
          <Field label={t('esgCalcFactor')}>
            <select value={factorCode} onChange={e => setFactorCode(e.target.value)} style={inputStyle}>
              {EMISSION_FACTORS.map(f => (
                <option key={f.code} value={f.code}>
                  Scope {f.scope} · {lang === 'ar' ? f.nameAr : f.nameEn}
                </option>
              ))}
            </select>
          </Field>
          <Field label={`${t('esgCalcAmount')} (${factor.unit})`}>
            <input type="number" value={amount} onChange={e => setAmount(e.target.value)}
              placeholder="0" className="mono" style={inputStyle} />
          </Field>
          <Field label={t('esgCalcDate')}>
            <input type="date" value={occurredAt} onChange={e => setOccurredAt(e.target.value)} style={inputStyle} />
          </Field>
          <Field label={t('esgCalcNote')}>
            <input value={note} onChange={e => setNote(e.target.value)} placeholder={t('esgCalcNotePlaceholder')} style={inputStyle} />
          </Field>
          <Button variant="primary" onClick={submit}>{t('esgCalcAdd')}</Button>
        </div>
        <div style={{ marginTop: 12, padding: 10, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', fontSize: 12 }}>
          <div style={{ color: 'var(--ink-3)', marginBottom: 4 }}>
            <b>{factor.kgCo2ePerUnit}</b> kgCO2e / {factor.unit} · {t('esgCalcSource')}: {factor.source}
          </div>
          <div style={{ fontWeight: 600 }}>
            {t('esgCalcPreview')}: <span className="mono">{computed.toFixed(2)}</span> kgCO2e
            ({(computed / 1000).toFixed(3)} tCO2e)
          </div>
        </div>
      </Card>

      <div style={{ marginTop: 16 }}>
        <Card title={t('esgCalcLogTitle')} Icon={FileText}
          action={
            <Badge tone="primary">{(totalKg / 1000).toFixed(2)} tCO2e</Badge>
          }>
          {entries.length === 0
            ? <div style={{ padding: 20, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
                {t('esgCalcEmpty')}
              </div>
            : (
              <table className="tbl" style={{ width: '100%' }}>
                <thead>
                  <tr>
                    <th>{t('esgCalcLogDate')}</th>
                    <th>{t('esgCalcLogFactor')}</th>
                    <th>{t('esgCalcLogAmount')}</th>
                    <th>{t('esgCalcLogResult')}</th>
                    <th>{t('esgCalcLogNote')}</th>
                    <th style={{ width: 50 }}></th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map(entry => {
                    const f = EMISSION_FACTORS.find(x => x.code === entry.factorCode);
                    return (
                      <tr key={entry.id}>
                        <td className="mono" style={{ fontSize: 11 }}>{entry.occurredAt}</td>
                        <td>
                          <Badge tone="info">Scope {f?.scope}</Badge>
                          <span style={{ marginInlineStart: 6 }}>{f ? (lang === 'ar' ? f.nameAr : f.nameEn) : entry.factorCode}</span>
                        </td>
                        <td className="mono">{entry.amount} {f?.unit}</td>
                        <td className="mono">
                          <b>{entry.computedKgCo2e.toFixed(2)}</b> kgCO2e
                        </td>
                        <td style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                          {entry.noteEn || entry.noteAr || '—'}
                        </td>
                        <td>
                          <Button variant="ghost" Icon={Trash2} onClick={() => onDelete(entry.id)}>{''}</Button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
        </Card>
      </div>
    </>
  );
}

// ─── CMA Articles 91–94 disclosure tab (V60 — slice 6) ─────────────────────
//
// Promoted from the static CMA_ARTICLES constant to a live API-driven
// matrix. Per-row workflow:
//   - Mark Disclosed / Partial / Not Disclosed / Not Applicable
//     (Rule 5 requires a gap for PARTIAL/NOT_DISCLOSED — captured via
//      dialog; Rule 6 auto-flips DISCLOSED/PARTIAL to EVIDENCE_REQUIRED
//      server-side when no evidence is attached)
//   - Attach Evidence (text reference + owner)
//   - Record Gap (English/Arabic narrative for the audit trail)

function CmaTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems]       = useState<CmaD[]>([]);
  const [readiness, setReadi]   = useState<CmaR | null>(null);
  const [loading, setLoading]   = useState(true);
  const [evidencingId, setEvidencingId] = useState<number | null>(null);
  const [gappingId, setGappingId]       = useState<{ id: number; toStatus?: 'PARTIAL' | 'NOT_DISCLOSED' } | null>(null);

  async function reload() {
    setLoading(true);
    try {
      const [pg, r] = await Promise.all([listCma({ year: 2025, size: 100 }), cmaReadiness(2025)]);
      setItems(pg.content);
      setReadi(r);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل سجلّ الإفصاحات' : 'Failed to load disclosure matrix'));
      setItems([]); setReadi(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  // Group by article (91 → 92 → 93 → 94).
  const byArticle = useMemo(() => {
    const map = new Map<string, CmaD[]>();
    for (const d of items) {
      if (!map.has(d.article)) map.set(d.article, []);
      map.get(d.article)!.push(d);
    }
    return map;
  }, [items]);

  async function runTransition(action: () => Promise<unknown>) {
    try {
      await action();
      toast.success(isAr ? 'تم التحديث' : 'Updated');
      await reload();
    } catch (e) {
      // Rule 5/6 violations surface as 400 with a clear message — pipe through.
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function onMarkDisclosed(d: CmaD) {
    await runTransition(() => markDisclosed(d.id!));
  }
  async function onMarkPartial(d: CmaD) {
    // Rule 5 — refuse PARTIAL without a gap. If the row already carries
    // one (e.g. from edit), let the server handle it. Otherwise prompt.
    if (!hasGap(d)) {
      setGappingId({ id: d.id!, toStatus: 'PARTIAL' });
      return;
    }
    await runTransition(() => markPartial(d.id!));
  }
  async function onMarkNotDisclosed(d: CmaD) {
    if (!hasGap(d)) {
      setGappingId({ id: d.id!, toStatus: 'NOT_DISCLOSED' });
      return;
    }
    await runTransition(() => markNotDisclosed(d.id!));
  }
  async function onMarkNotApplicable(d: CmaD) {
    await runTransition(() => markNotApplicable(d.id!));
  }
  async function onMarkUnderReview(d: CmaD) {
    await runTransition(() => markUnderReview(d.id!));
  }

  async function submitAttachEvidence(values: Record<string, unknown>) {
    if (evidencingId == null) return;
    const ref = String(values.evidenceRef ?? '').trim();
    const owner = String(values.owner ?? '').trim() || undefined;
    if (!ref) { toast.danger(isAr ? 'مرجع الدليل مطلوب' : 'Evidence reference is required'); return; }
    try {
      await attachCmaEvidence(evidencingId, ref, owner);
      toast.success(isAr ? 'تم إرفاق الدليل' : 'Evidence attached');
      setEvidencingId(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  async function submitGap(values: Record<string, unknown>) {
    if (gappingId == null) return;
    const gapEn = String(values.gapEn ?? '').trim() || undefined;
    const gapAr = String(values.gapAr ?? '').trim() || undefined;
    if (!gapEn && !gapAr) {
      toast.danger(isAr ? 'وصف الفجوة مطلوب (إنجليزي أو عربي)' : 'Gap description is required (en or ar)');
      return;
    }
    try {
      await recordCmaGap(gappingId.id, gapEn, gapAr);
      // If the user opened this dialog from a Mark Partial / Not Disclosed
      // attempt, chase the transition after the gap is recorded.
      if (gappingId.toStatus === 'PARTIAL')     await markPartial(gappingId.id);
      if (gappingId.toStatus === 'NOT_DISCLOSED') await markNotDisclosed(gappingId.id);
      toast.success(isAr ? 'تم تسجيل الفجوة' : 'Gap recorded');
      setGappingId(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  const evidenceFields: FieldDef[] = [
    { kind: 'text', key: 'evidenceRef', required: true, span: 12,
      label: isAr ? 'مرجع الدليل (مسار، قسم، URL، إلخ)' : 'Evidence reference (path, section, URL, etc.)',
      placeholder: isAr ? 'مثال: Annual Report 2025 §3.4' : 'e.g. Annual Report 2025 §3.4' },
    { kind: 'text', key: 'owner', span: 12,
      label: isAr ? 'مالك الدليل (اختياري)' : 'Evidence owner (optional)',
      placeholder: isAr ? 'مثال: سارة الزهراني' : 'e.g. Sara Al-Zahrani' },
  ];

  // FieldDef.textarea doesn't accept `placeholder`. The labels carry
  // enough hint in this case; submitGap re-validates that at least one
  // of the two fields was filled before dispatch.
  const gapFields: FieldDef[] = [
    { kind: 'textarea', key: 'gapEn', span: 12, rows: 3,
      label: isAr ? 'وصف الفجوة (إنجليزي)' : 'Gap description (English)' },
    { kind: 'textarea', key: 'gapAr', span: 12, rows: 3,
      label: isAr ? 'وصف الفجوة (عربي)' : 'Gap description (Arabic)' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Readiness KPI strip */}
      <div className="kpi-grid">
        <Kpi Icon={FileText}     label={isAr ? 'جاهزية الإفصاح' : 'Disclosure readiness'} value={`${readiness?.readinessPct ?? 0}%`}
             tone={(readiness?.readinessPct ?? 0) >= 80 ? 'success' : (readiness?.readinessPct ?? 0) >= 50 ? 'warning' : 'danger'} />
        <Kpi Icon={CheckCircle2} label={isAr ? 'مُفصَح' : 'Disclosed'}      value={readiness?.byStatus.DISCLOSED ?? 0}         tone="success" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'جزئي' : 'Partial'}          value={readiness?.byStatus.PARTIAL ?? 0}           tone="warning" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'غير مُفصَح' : 'Not disclosed'} value={readiness?.byStatus.NOT_DISCLOSED ?? 0}  tone="danger" />
        <Kpi Icon={Paperclip}    label={isAr ? 'يتطلّب دليلاً' : 'Evidence required'} value={readiness?.byStatus.EVIDENCE_REQUIRED ?? 0} tone="danger" />
        <Kpi Icon={ArrowRight}   label={isAr ? 'قيد المراجعة' : 'Under review'}      value={readiness?.byStatus.UNDER_REVIEW ?? 0}      tone="info" />
      </div>

      <Card title={isAr ? 'سجلّ إفصاحات المواد 91–94' : 'CMA Articles 91–94 disclosure matrix'} Icon={FileText}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {isAr
            ? 'مصفوفة الإفصاحات الإلزامية لهيئة السوق المالية. الجزئي وغير المُفصَح يتطلّبان وصف فجوة (القاعدة 5). الإفصاحات المعتمدة بلا دليل تُعلَّم تلقائياً «يتطلّب دليلاً» (القاعدة 6).'
            : 'CMA mandatory disclosure matrix. Partial and Not-Disclosed states require a gap description (Rule 5). Disclosed items without evidence are auto-flagged Evidence Required (Rule 6).'}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد إفصاحات لهذه السنة' : 'No disclosures for this year'}</InlineEmpty>
            : Array.from(byArticle.entries()).map(([article, articleItems]) => (
                <div key={article} style={{ marginBottom: 18 }}>
                  <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 8, color: 'var(--primary)' }}>
                    {isAr ? `المادة ${article}` : `Article ${article}`}
                  </div>
                  {articleItems.map(d => (
                    <div key={d.id} style={{
                      padding: '12px 0', borderBottom: '1px dashed var(--border)',
                      display: 'grid', gridTemplateColumns: '1fr auto', gap: 10,
                    }}>
                      <div>
                        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 4, flexWrap: 'wrap' }}>
                          <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                            {articleLabel(d, isAr ? 'ar' : 'en')}
                          </span>
                          <span style={{ fontSize: 13, fontWeight: 600 }}>
                            {isAr && d.titleAr ? d.titleAr : d.titleEn}
                          </span>
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                          {isAr && d.requirementAr ? d.requirementAr : d.requirementEn}
                        </div>
                        {hasEvidence(d) && (
                          <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 4, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                            <Paperclip size={10} />
                            <span>{isAr ? 'الدليل:' : 'Evidence:'}</span>
                            <span className="mono">{d.evidenceRef}</span>
                            {d.evidenceOwner && <span>· {d.evidenceOwner}</span>}
                          </div>
                        )}
                        {hasGap(d) && (
                          <div style={{ marginTop: 6, fontSize: 11, color: 'var(--warning-fg)', lineHeight: 1.5,
                              padding: 8, background: 'var(--warning-bg)', borderRadius: 'var(--r-sm)' }}>
                            <AlertCircle size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
                            <strong>{isAr ? 'فجوة:' : 'Gap:'}</strong>{' '}
                            {isAr && d.gapDescriptionAr ? d.gapDescriptionAr : (d.gapDescriptionEn ?? d.gapDescriptionAr)}
                          </div>
                        )}
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-end', minWidth: 220 }}>
                        <Badge tone={cmaStatusTone(d.disclosureStatus)}>{cmaStatusLabel(d.disclosureStatus, isAr ? 'ar' : 'en')}</Badge>
                        <div style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                          {d.disclosureStatus !== 'DISCLOSED' && (
                            <Button size="sm" variant="ghost" onClick={() => onMarkDisclosed(d)}>{isAr ? 'مُفصَح' : 'Disclosed'}</Button>
                          )}
                          {d.disclosureStatus !== 'PARTIAL' && (
                            <Button size="sm" variant="ghost" onClick={() => onMarkPartial(d)}>{isAr ? 'جزئي' : 'Partial'}</Button>
                          )}
                          {d.disclosureStatus !== 'NOT_DISCLOSED' && (
                            <Button size="sm" variant="ghost" onClick={() => onMarkNotDisclosed(d)}>{isAr ? 'غير مُفصَح' : 'Not'}</Button>
                          )}
                          {d.disclosureStatus !== 'UNDER_REVIEW' && (
                            <Button size="sm" variant="ghost" onClick={() => onMarkUnderReview(d)}>{isAr ? 'مراجعة' : 'Review'}</Button>
                          )}
                          {d.disclosureStatus !== 'NOT_APPLICABLE' && (
                            <Button size="sm" variant="ghost" onClick={() => onMarkNotApplicable(d)}>{isAr ? 'لا ينطبق' : 'N/A'}</Button>
                          )}
                        </div>
                        <div style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                          <Button size="sm" variant="secondary" Icon={Paperclip} onClick={() => setEvidencingId(d.id!)}>
                            {isAr ? 'إرفاق دليل' : 'Attach'}
                          </Button>
                          <Button size="sm" variant="secondary" Icon={AlertCircle} onClick={() => setGappingId({ id: d.id! })}>
                            {isAr ? 'تسجيل فجوة' : 'Gap'}
                          </Button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ))}
      </Card>

      <FormModal
        open={evidencingId !== null}
        title={isAr ? 'إرفاق دليل للإفصاح' : 'Attach evidence to disclosure'}
        fields={evidenceFields}
        initial={{ evidenceRef: '', owner: '' }}
        size="md"
        submitLabel={isAr ? 'إرفاق' : 'Attach'}
        onClose={() => setEvidencingId(null)}
        onSubmit={submitAttachEvidence}
      />
      <FormModal
        open={gappingId !== null}
        title={
          gappingId?.toStatus
            ? (isAr
                ? `الفجوة مطلوبة لـ ${gappingId.toStatus === 'PARTIAL' ? 'الإفصاح الجزئي' : 'عدم الإفصاح'} (القاعدة 5)`
                : `Gap required for ${gappingId.toStatus === 'PARTIAL' ? 'Partial' : 'Not Disclosed'} (Rule 5)`)
            : (isAr ? 'تسجيل فجوة' : 'Record gap')
        }
        fields={gapFields}
        initial={{ gapEn: '', gapAr: '' }}
        size="md"
        submitLabel={isAr ? 'حفظ' : 'Save'}
        onClose={() => setGappingId(null)}
        onSubmit={submitGap}
      />
    </div>
  );
}

// ─── TCFD alignment tab (V61 — slice 7) ─────────────────────────────────────
//
// Promoted from the static TCFD_ITEMS constant to a live API-driven
// matrix grouped by pillar. Mirrors CmaTab's lifecycle UX — same
// 5 status buttons, Attach Evidence + Record Gap modals, Rule 5 + 6
// enforcement client-side mirror — plus a Rule 7 banner when the
// Strategy resilience recommendation is in a non-DISCLOSED state.

function TcfdTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems]     = useState<TcfdI[]>([]);
  const [readiness, setReadi] = useState<TcfdR | null>(null);
  const [loading, setLoading] = useState(true);
  const [evidencingId, setEvidencingId] = useState<number | null>(null);
  const [gappingId, setGappingId]       = useState<{ id: number; toStatus?: 'PARTIAL' | 'NOT_DISCLOSED' } | null>(null);

  async function reload() {
    setLoading(true);
    try {
      const [pg, r] = await Promise.all([listTcfd({ year: 2025, size: 100 }), tcfdReadiness(2025)]);
      setItems(pg.content);
      setReadi(r);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل مصفوفة TCFD' : 'Failed to load TCFD matrix'));
      setItems([]); setReadi(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  const byPillar = useMemo(() => {
    const map = new Map<TcfdPillar, TcfdI[]>();
    for (const t of items) {
      if (!map.has(t.pillar)) map.set(t.pillar, []);
      map.get(t.pillar)!.push(t);
    }
    return map;
  }, [items]);

  async function runTransition(action: () => Promise<unknown>) {
    try { await action(); toast.success(isAr ? 'تم التحديث' : 'Updated'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onMarkDisclosed(t: TcfdI)    { await runTransition(() => tcfdMarkDisclosed(t.id!)); }
  async function onMarkPartial(t: TcfdI) {
    if (!hasTcfdGap(t)) { setGappingId({ id: t.id!, toStatus: 'PARTIAL' }); return; }
    await runTransition(() => tcfdMarkPartial(t.id!));
  }
  async function onMarkNotDisclosed(t: TcfdI) {
    if (!hasTcfdGap(t)) { setGappingId({ id: t.id!, toStatus: 'NOT_DISCLOSED' }); return; }
    await runTransition(() => tcfdMarkNotDisclosed(t.id!));
  }
  async function onMarkNotApplicable(t: TcfdI) { await runTransition(() => tcfdMarkNotApplicable(t.id!)); }
  async function onMarkUnderReview(t: TcfdI)   { await runTransition(() => tcfdMarkUnderReview(t.id!)); }

  async function submitAttachEvidence(values: Record<string, unknown>) {
    if (evidencingId == null) return;
    const ref = String(values.evidenceRef ?? '').trim();
    const owner = String(values.owner ?? '').trim() || undefined;
    if (!ref) { toast.danger(isAr ? 'مرجع الدليل مطلوب' : 'Evidence reference is required'); return; }
    try {
      await tcfdAttachEvidence(evidencingId, ref, owner);
      toast.success(isAr ? 'تم إرفاق الدليل' : 'Evidence attached');
      setEvidencingId(null);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function submitGap(values: Record<string, unknown>) {
    if (gappingId == null) return;
    const gapEn = String(values.gapEn ?? '').trim() || undefined;
    const gapAr = String(values.gapAr ?? '').trim() || undefined;
    if (!gapEn && !gapAr) {
      toast.danger(isAr ? 'وصف الفجوة مطلوب' : 'Gap description is required'); return;
    }
    try {
      await tcfdRecordGap(gappingId.id, gapEn, gapAr);
      if (gappingId.toStatus === 'PARTIAL')        await tcfdMarkPartial(gappingId.id);
      if (gappingId.toStatus === 'NOT_DISCLOSED')  await tcfdMarkNotDisclosed(gappingId.id);
      toast.success(isAr ? 'تم تسجيل الفجوة' : 'Gap recorded');
      setGappingId(null);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const evidenceFields: FieldDef[] = [
    { kind: 'text', key: 'evidenceRef', required: true, span: 12,
      label: isAr ? 'مرجع الدليل (مسار، قسم، URL، إلخ)' : 'Evidence reference (path, section, URL, etc.)' },
    { kind: 'text', key: 'owner', span: 12,
      label: isAr ? 'مالك الدليل (اختياري)' : 'Evidence owner (optional)' },
  ];
  const gapFields: FieldDef[] = [
    { kind: 'textarea', key: 'gapEn', span: 12, rows: 3,
      label: isAr ? 'وصف الفجوة (إنجليزي)' : 'Gap description (English)' },
    { kind: 'textarea', key: 'gapAr', span: 12, rows: 3,
      label: isAr ? 'وصف الفجوة (عربي)' : 'Gap description (Arabic)' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Rule 7 banner — Strategy resilience recommendation is missing/partial */}
      {readiness?.strategyResilienceGap && (
        <div style={{
          padding: 12, borderRadius: 'var(--r-sm)',
          background: 'var(--warning-bg)', color: 'var(--warning-fg)',
          fontSize: 12, lineHeight: 1.5,
          display: 'flex', gap: 8, alignItems: 'center',
        }}>
          <AlertCircle size={16} style={{ flexShrink: 0 }} />
          <div>
            <strong>{isAr ? 'تنبيه القاعدة 7:' : 'Rule 7 alert:'}</strong>{' '}
            {isAr
              ? 'توصية «الصمود تحت سيناريوهات مناخية مختلفة» (الاستراتيجية ج) غير مُفصَحة بالكامل — يُنصح برفع إجراء تصحيحي مرتبط بمخاطر مناخية.'
              : 'The Strategy resilience-scenario recommendation (Strategy c) is not fully disclosed — consider raising a climate-risk-linked corrective action.'}
          </div>
        </div>
      )}

      {/* Alignment KPI strip */}
      <div className="kpi-grid">
        <Kpi Icon={FileText}     label={isAr ? 'محاذاة TCFD' : 'TCFD alignment'} value={`${readiness?.alignmentPct ?? 0}%`}
             tone={(readiness?.alignmentPct ?? 0) >= 80 ? 'success' : (readiness?.alignmentPct ?? 0) >= 50 ? 'warning' : 'danger'} />
        <Kpi Icon={CheckCircle2} label={isAr ? 'مُفصَح' : 'Disclosed'}    value={readiness?.byStatus.DISCLOSED ?? 0}      tone="success" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'جزئي' : 'Partial'}        value={readiness?.byStatus.PARTIAL ?? 0}        tone="warning" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'غير مُفصَح' : 'Not disclosed'} value={readiness?.byStatus.NOT_DISCLOSED ?? 0} tone="danger" />
        <Kpi Icon={Paperclip}    label={isAr ? 'يتطلّب دليلاً' : 'Evidence required'} value={readiness?.byStatus.EVIDENCE_REQUIRED ?? 0} tone="danger" />
        <Kpi Icon={ArrowRight}   label={isAr ? 'قيد المراجعة' : 'Under review'}      value={readiness?.byStatus.UNDER_REVIEW ?? 0}      tone="info" />
      </div>

      <Card title={isAr ? 'مصفوفة المحاذاة مع TCFD' : 'TCFD alignment matrix'} Icon={FileText}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {isAr
            ? 'إفصاحات TCFD الـ 11 الموزّعة على 4 ركائز (الحوكمة، الاستراتيجية، إدارة المخاطر، المقاييس والأهداف). نفس قواعد CMA (5 و 6) تنطبق هنا، بالإضافة إلى القاعدة 7 لتوصية صمود الاستراتيجية.'
            : 'TCFD’s 11 recommendations across the 4 pillars (Governance, Strategy, Risk Mgmt, Metrics & Targets). CMA Rules 5 + 6 apply here too, plus Rule 7 on the Strategy resilience recommendation.'}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد توصيات' : 'No recommendations'}</InlineEmpty>
            : (['GOVERNANCE', 'STRATEGY', 'RISK_MGMT', 'METRICS_TARGETS'] as TcfdPillar[]).map(pillar => {
                const pillarItems = byPillar.get(pillar) ?? [];
                if (pillarItems.length === 0) return null;
                return (
                  <div key={pillar} style={{ marginBottom: 18 }}>
                    <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 8, color: 'var(--primary)' }}>
                      {tcfdPillarLabel(pillar, isAr ? 'ar' : 'en')}
                    </div>
                    {pillarItems.map(t => (
                      <div key={t.id} style={{
                        padding: '12px 0', borderBottom: '1px dashed var(--border)',
                        display: 'grid', gridTemplateColumns: '1fr auto', gap: 10,
                      }}>
                        <div>
                          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 4, flexWrap: 'wrap' }}>
                            <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                              ({t.recommendationCode})
                            </span>
                            <span style={{ fontSize: 13, fontWeight: 600 }}>
                              {isAr && t.titleAr ? t.titleAr : t.titleEn}
                            </span>
                          </div>
                          {(isAr ? t.recommendationAr : t.recommendationEn) && (
                            <div style={{ fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                              {isAr && t.recommendationAr ? t.recommendationAr : t.recommendationEn}
                            </div>
                          )}
                          {hasTcfdEvidence(t) && (
                            <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 4, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                              <Paperclip size={10} />
                              <span>{isAr ? 'الدليل:' : 'Evidence:'}</span>
                              <span className="mono">{t.evidenceRef}</span>
                            </div>
                          )}
                          {hasTcfdGap(t) && (
                            <div style={{ marginTop: 6, fontSize: 11, color: 'var(--warning-fg)', lineHeight: 1.5,
                                padding: 8, background: 'var(--warning-bg)', borderRadius: 'var(--r-sm)' }}>
                              <AlertCircle size={11} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
                              <strong>{isAr ? 'فجوة:' : 'Gap:'}</strong>{' '}
                              {isAr && t.gapDescriptionAr ? t.gapDescriptionAr : (t.gapDescriptionEn ?? t.gapDescriptionAr)}
                            </div>
                          )}
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-end', minWidth: 220 }}>
                          <Badge tone={tcfdStatusTone(t.disclosureStatus)}>{tcfdStatusLabel(t.disclosureStatus, isAr ? 'ar' : 'en')}</Badge>
                          <div style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                            {t.disclosureStatus !== 'DISCLOSED'     && <Button size="sm" variant="ghost" onClick={() => onMarkDisclosed(t)}>{isAr ? 'مُفصَح' : 'Disclosed'}</Button>}
                            {t.disclosureStatus !== 'PARTIAL'       && <Button size="sm" variant="ghost" onClick={() => onMarkPartial(t)}>{isAr ? 'جزئي' : 'Partial'}</Button>}
                            {t.disclosureStatus !== 'NOT_DISCLOSED' && <Button size="sm" variant="ghost" onClick={() => onMarkNotDisclosed(t)}>{isAr ? 'غير مُفصَح' : 'Not'}</Button>}
                            {t.disclosureStatus !== 'UNDER_REVIEW'  && <Button size="sm" variant="ghost" onClick={() => onMarkUnderReview(t)}>{isAr ? 'مراجعة' : 'Review'}</Button>}
                            {t.disclosureStatus !== 'NOT_APPLICABLE'&& <Button size="sm" variant="ghost" onClick={() => onMarkNotApplicable(t)}>{isAr ? 'لا ينطبق' : 'N/A'}</Button>}
                          </div>
                          <div style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                            <Button size="sm" variant="secondary" Icon={Paperclip}   onClick={() => setEvidencingId(t.id!)}>{isAr ? 'إرفاق' : 'Attach'}</Button>
                            <Button size="sm" variant="secondary" Icon={AlertCircle} onClick={() => setGappingId({ id: t.id! })}>{isAr ? 'فجوة' : 'Gap'}</Button>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                );
              })}
      </Card>

      <FormModal
        open={evidencingId !== null}
        title={isAr ? 'إرفاق دليل للتوصية' : 'Attach evidence to recommendation'}
        fields={evidenceFields}
        initial={{ evidenceRef: '', owner: '' }}
        size="md"
        submitLabel={isAr ? 'إرفاق' : 'Attach'}
        onClose={() => setEvidencingId(null)}
        onSubmit={submitAttachEvidence}
      />
      <FormModal
        open={gappingId !== null}
        title={
          gappingId?.toStatus
            ? (isAr ? `الفجوة مطلوبة لـ ${gappingId.toStatus === 'PARTIAL' ? 'الإفصاح الجزئي' : 'عدم الإفصاح'} (القاعدة 5)`
                    : `Gap required for ${gappingId.toStatus === 'PARTIAL' ? 'Partial' : 'Not Disclosed'} (Rule 5)`)
            : (isAr ? 'تسجيل فجوة' : 'Record gap')
        }
        fields={gapFields}
        initial={{ gapEn: '', gapAr: '' }}
        size="md"
        submitLabel={isAr ? 'حفظ' : 'Save'}
        onClose={() => setGappingId(null)}
        onSubmit={submitGap}
      />
    </div>
  );
}

// ─── ESG Materiality tab (V62 — slice 8) ─────────────────────────────────────
//
// Double-materiality assessment: each topic gets two scores on a 1-5
// scale (stakeholder importance × business impact). Service computes
// materiality_score = (s+b)/2 and bucket into CRITICAL/HIGH/MEDIUM/LOW
// on every save. Approved topics anchor CMA Article 92(a) and the
// climate-risk topic register.

function MaterialityTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [topics, setTopics]   = useState<MaterialTopic[]>([]);
  const [matrix, setMatrix]   = useState<MaterialMatrix | null>(null);
  const [loading, setLoading] = useState(true);
  const [categoryFilter, setCategoryFilter] = useState<MaterialCategory | 'ALL'>('ALL');
  const [scoringTopic, setScoringTopic]     = useState<MaterialTopic | null>(null);
  const [approvingId, setApprovingId]       = useState<number | null>(null);

  async function reload() {
    setLoading(true);
    try {
      const [pg, m] = await Promise.all([listMateriality({ year: 2025, size: 100 }), generateMatrix(2025)]);
      setTopics(pg.content);
      setMatrix(m);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل تقييم الجوهرية' : 'Failed to load materiality assessment'));
      setTopics([]); setMatrix(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  const filtered = categoryFilter === 'ALL'
    ? topics
    : topics.filter(t => t.category === categoryFilter);

  async function submitScore(values: Record<string, unknown>) {
    if (!scoringTopic) return;
    const s = Number(values.stakeholderImportance);
    const b = Number(values.businessImpact);
    if (!Number.isFinite(s) || !Number.isFinite(b) || s < 0 || s > 5 || b < 0 || b > 5) {
      toast.danger(isAr ? 'القيم يجب أن تكون 0..5' : 'Values must be 0..5'); return;
    }
    try {
      await scoreMaterialTopic(scoringTopic.id!, s, b);
      toast.success(isAr ? 'تم التقييم' : 'Scored');
      setScoringTopic(null);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function submitApprove(values: Record<string, unknown>) {
    if (approvingId == null) return;
    const by = String(values.approvedBy ?? '').trim();
    if (!by) { toast.danger(isAr ? 'الاسم مطلوب' : 'Name required'); return; }
    try {
      await approveMaterialTopic(approvingId, by);
      toast.success(isAr ? 'تم الاعتماد' : 'Approved');
      setApprovingId(null);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onArchive(t: MaterialTopic) {
    if (!window.confirm(isAr ? 'أرشفة هذا الموضوع؟' : 'Archive this topic?')) return;
    try {
      await archiveMaterialTopic(t.id!);
      toast.info(isAr ? 'تم الأرشفة' : 'Archived');
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const scoreFields: FieldDef[] = [
    { kind: 'number', key: 'stakeholderImportance', required: true, min: 0, max: 5, span: 6,
      label: isAr ? 'أهمية لأصحاب المصلحة (0–5)' : 'Stakeholder importance (0–5)' },
    { kind: 'number', key: 'businessImpact', required: true, min: 0, max: 5, span: 6,
      label: isAr ? 'الأثر على الأعمال (0–5)' : 'Business impact (0–5)' },
  ];
  const approveFields: FieldDef[] = [
    { kind: 'text', key: 'approvedBy', required: true, span: 12,
      label: isAr ? 'اسم المعتَمِد' : 'Approver name',
      placeholder: isAr ? 'مثال: خالد العتيبي' : 'e.g. Khalid Al-Otaibi' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* KPI strip — tier counts + category counts */}
      <div className="kpi-grid">
        <Kpi Icon={Target}       label={isAr ? 'إجمالي الموضوعات' : 'Total topics'}     value={matrix?.total ?? 0} tone="primary" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'حرج' : 'Critical'}                       value={matrix?.byTier.CRITICAL ?? 0} tone="danger" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'مرتفع' : 'High'}                         value={matrix?.byTier.HIGH ?? 0}     tone="warning" />
        <Kpi Icon={Leaf}         label={isAr ? 'البيئة' : 'Environmental'}                value={matrix?.byCategory.ENVIRONMENTAL ?? 0} tone="success" />
        <Kpi Icon={Building2}    label={isAr ? 'الاجتماع' : 'Social'}                     value={matrix?.byCategory.SOCIAL ?? 0}        tone="info" />
        <Kpi Icon={Building2}    label={isAr ? 'الحوكمة' : 'Governance'}                  value={matrix?.byCategory.GOVERNANCE ?? 0}    tone="primary" />
      </div>

      <Card title={isAr ? 'مصفوفة تقييم الجوهرية المزدوجة' : 'Double-materiality assessment matrix'} Icon={Target}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {isAr
            ? 'كل موضوع يحصل على درجتين على مقياس 1-5: الأهمية لأصحاب المصلحة والأثر على الأعمال. درجة الجوهرية = المتوسط. يُحدَّد المستوى تلقائياً عند الحفظ.'
            : 'Each topic is scored on two axes (1-5): stakeholder importance and business impact. Materiality score = mean. Priority tier is auto-bucketed on save.'}
        </div>

        {/* Category filter chips */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
          {(['ALL', 'ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => (
            <button key={c} onClick={() => setCategoryFilter(c)}
              style={{
                padding: '4px 10px', fontSize: 11, fontWeight: 600,
                border: `1px solid ${categoryFilter === c ? 'var(--primary)' : 'var(--border)'}`,
                background: categoryFilter === c ? 'var(--primary-50)' : 'var(--surface-1)',
                color: categoryFilter === c ? 'var(--primary)' : 'var(--ink-2)',
                borderRadius: 'var(--r-sm)', cursor: 'pointer',
              }}>
              {c === 'ALL' ? (isAr ? 'الكل' : 'All') : materialCategoryLabel(c as MaterialCategory, isAr ? 'ar' : 'en')}
            </button>
          ))}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد موضوعات مطابقة' : 'No matching topics'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'الموضوع' : 'Topic'}</th>
                    <th>{isAr ? 'الفئة' : 'Category'}</th>
                    <th>{isAr ? 'أصحاب المصلحة' : 'Stakeholder'}</th>
                    <th>{isAr ? 'الأعمال' : 'Business'}</th>
                    <th>{isAr ? 'الدرجة' : 'Score'}</th>
                    <th>{isAr ? 'المستوى' : 'Tier'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(t => (
                    <tr key={t.id}>
                      <td className="mono text-2xs">{t.topicCode ?? `#${t.id}`}</td>
                      <td>
                        <div style={{ fontWeight: 600, color: 'var(--ink-1)', maxWidth: 360, lineHeight: 1.3 }}>
                          {isAr && t.topicAr ? t.topicAr : t.topicEn}
                        </div>
                        {t.ownerName && <div className="text-2xs text-muted" style={{ marginTop: 2 }}>{t.ownerName}</div>}
                      </td>
                      <td><Badge tone="neutral">{materialCategoryLabel(t.category, isAr ? 'ar' : 'en')}</Badge></td>
                      <td className="mono text-xs" style={{ textAlign: 'center' }}>{Number(t.stakeholderImportance).toFixed(1)}</td>
                      <td className="mono text-xs" style={{ textAlign: 'center' }}>{Number(t.businessImpact).toFixed(1)}</td>
                      <td className="mono text-xs" style={{ textAlign: 'center', fontWeight: 700 }}>
                        {t.materialityScore != null ? Number(t.materialityScore).toFixed(1) : '—'}
                      </td>
                      <td>
                        {t.priorityTier && <Badge tone={materialTierTone(t.priorityTier)}>{materialTierLabel(t.priorityTier, isAr ? 'ar' : 'en')}</Badge>}
                      </td>
                      <td><Badge tone={materialStatusTone(t.status)}>{materialStatusLabel(t.status, isAr ? 'ar' : 'en')}</Badge></td>
                      <td style={{ textAlign: 'end' }}>
                        <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                          {t.status !== 'ARCHIVED' && (
                            <Button size="sm" variant="secondary" onClick={() => setScoringTopic(t)}>
                              {isAr ? 'تقييم' : 'Score'}
                            </Button>
                          )}
                          {t.status === 'SCORED' && (
                            <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => setApprovingId(t.id!)}>
                              {isAr ? 'اعتماد' : 'Approve'}
                            </Button>
                          )}
                          {t.status !== 'ARCHIVED' && (
                            <Button size="sm" variant="ghost" Icon={Trash2}
                              title={isAr ? 'أرشفة' : 'Archive'}
                              onClick={() => onArchive(t)} />
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
      </Card>

      {/* Materiality matrix — visual grid where Y = stakeholder, X = business */}
      {matrix && matrix.matrix.length > 0 && (
        <Card title={isAr ? 'الرسم المرئي للمصفوفة' : 'Matrix visualization'} Icon={Target}>
          <div style={{ position: 'relative', width: '100%', maxWidth: 520, margin: '0 auto', aspectRatio: '1 / 1', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-2)' }}>
            {/* Axis labels */}
            <div style={{ position: 'absolute', top: '50%', insetInlineStart: -8, transform: 'translate(-100%, -50%) rotate(-90deg)', fontSize: 10, color: 'var(--ink-3)', whiteSpace: 'nowrap' }}>
              {isAr ? '← الأهمية لأصحاب المصلحة' : 'Stakeholder importance →'}
            </div>
            <div style={{ position: 'absolute', bottom: -20, left: 0, right: 0, textAlign: 'center', fontSize: 10, color: 'var(--ink-3)' }}>
              {isAr ? '← الأثر على الأعمال' : 'Business impact →'}
            </div>
            {/* Quadrant guides at 2.5 / 4.0 */}
            <div style={{ position: 'absolute', insetInlineStart: '50%', top: 0, bottom: 0, width: 1, background: 'var(--border)' }} />
            <div style={{ position: 'absolute', insetBlockStart: '50%', left: 0, right: 0, height: 1, background: 'var(--border)' }} />
            {/* Plot points */}
            {matrix.matrix.map(e => {
              // X = business_impact / 5 * width; Y = (5 - stakeholder) / 5 * height (invert Y so high = top)
              const x = (Number(e.businessImpact) / 5) * 100;
              const y = (1 - Number(e.stakeholderImportance) / 5) * 100;
              const bg = e.priorityTier === 'CRITICAL' ? 'var(--danger)'
                       : e.priorityTier === 'HIGH'    ? 'var(--warning)'
                       : e.priorityTier === 'MEDIUM'   ? 'var(--info)' : 'var(--ink-3)';
              return (
                <div key={e.id}
                     title={`${e.topicCode ?? e.id}: ${isAr && e.topicAr ? e.topicAr : e.topicEn} (${Number(e.materialityScore).toFixed(1)})`}
                     style={{
                       position: 'absolute',
                       insetInlineStart: `${x}%`, top: `${y}%`,
                       transform: 'translate(-50%, -50%)',
                       width: 14, height: 14, borderRadius: '50%',
                       background: bg, border: '2px solid var(--surface)',
                       boxShadow: 'var(--shadow-1)',
                     }} />
              );
            })}
          </div>
          <div style={{ display: 'flex', gap: 16, marginTop: 24, fontSize: 11, color: 'var(--ink-3)', justifyContent: 'center', flexWrap: 'wrap' }}>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: 'var(--danger)', verticalAlign: 'middle', marginInlineEnd: 4 }} />{materialTierLabel('CRITICAL', isAr ? 'ar' : 'en')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: 'var(--warning)', verticalAlign: 'middle', marginInlineEnd: 4 }} />{materialTierLabel('HIGH', isAr ? 'ar' : 'en')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: 'var(--info)', verticalAlign: 'middle', marginInlineEnd: 4 }} />{materialTierLabel('MEDIUM', isAr ? 'ar' : 'en')}</span>
            <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', background: 'var(--ink-3)', verticalAlign: 'middle', marginInlineEnd: 4 }} />{materialTierLabel('LOW', isAr ? 'ar' : 'en')}</span>
          </div>
        </Card>
      )}

      <FormModal
        open={scoringTopic !== null}
        title={isAr ? `تقييم الموضوع · ${scoringTopic?.topicCode ?? ''}` : `Score topic · ${scoringTopic?.topicCode ?? ''}`}
        fields={scoreFields}
        initial={{
          stakeholderImportance: scoringTopic?.stakeholderImportance ?? 3,
          businessImpact: scoringTopic?.businessImpact ?? 3,
        }}
        size="md"
        submitLabel={isAr ? 'حفظ التقييم' : 'Save score'}
        onClose={() => setScoringTopic(null)}
        onSubmit={submitScore}
      />
      <FormModal
        open={approvingId !== null}
        title={isAr ? 'اعتماد الموضوع' : 'Approve topic'}
        fields={approveFields}
        initial={{ approvedBy: '' }}
        size="md"
        submitLabel={isAr ? 'اعتماد' : 'Approve'}
        onClose={() => setApprovingId(null)}
        onSubmit={submitApprove}
      />
    </div>
  );
}

// ─── ESG Metrics tab (V63 — slice 9) ─────────────────────────────────────────
//
// Unified register for Environmental / Social / Governance metrics.
// Each row tracks baseline / current / target with a unit; progress is
// direction-aware (renewable % rises to target; LTIFR + water intensity
// fall to target). "Record reading" updates current_value + as_of so
// the dashboard reflects the latest data without rewriting the whole row.

function MetricsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems]   = useState<EsgM[]>([]);
  const [agg, setAgg]       = useState<MetricAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [categoryFilter, setCategoryFilter] = useState<MetricCategory | 'ALL'>('ALL');
  const [readingMetric, setReadingMetric]   = useState<EsgM | null>(null);

  async function reload() {
    setLoading(true);
    try {
      const [pg, a] = await Promise.all([listMetrics({ year: 2025, size: 200 }), metricsAggregate(2025)]);
      setItems(pg.content);
      setAgg(a);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل المؤشّرات' : 'Failed to load metrics'));
      setItems([]); setAgg(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  const filtered = categoryFilter === 'ALL'
    ? items
    : items.filter(m => m.category === categoryFilter);

  async function submitReading(values: Record<string, unknown>) {
    if (!readingMetric) return;
    const cur = Number(values.currentValue);
    const asOf = String(values.asOf ?? '').trim() || undefined;
    if (!Number.isFinite(cur)) { toast.danger(isAr ? 'القيمة غير صالحة' : 'Invalid value'); return; }
    try {
      await recordMetricReading(readingMetric.id!, cur, asOf);
      toast.success(isAr ? 'تم تسجيل القراءة' : 'Reading recorded');
      setReadingMetric(null);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onArchive(m: EsgM) {
    if (!window.confirm(isAr ? 'أرشفة هذا المؤشّر؟' : 'Archive this metric?')) return;
    try { await archiveMetric(m.id!); toast.info(isAr ? 'تم الأرشفة' : 'Archived'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const readingFields: FieldDef[] = [
    { kind: 'number', key: 'currentValue', required: true, span: 6,
      label: isAr ? 'القيمة الحالية' : 'Current value' },
    { kind: 'date', key: 'asOf', span: 6,
      label: isAr ? 'تاريخ القياس' : 'As of' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Per-pillar mean-progress strip */}
      <div className="kpi-grid">
        <Kpi Icon={TrendingDown} label={isAr ? 'إجمالي المؤشّرات' : 'Total metrics'}                value={agg?.total ?? 0} tone="primary" />
        <Kpi Icon={Leaf}         label={isAr ? 'متوسّط تقدّم البيئة' : 'Env. mean progress'}     value={`${agg ? Number(agg.meanProgressByCategory.ENVIRONMENTAL ?? 0).toFixed(0) : 0}%`} tone="success" />
        <Kpi Icon={Building2}    label={isAr ? 'متوسّط تقدّم الاجتماع' : 'Social mean progress'} value={`${agg ? Number(agg.meanProgressByCategory.SOCIAL ?? 0).toFixed(0) : 0}%`}        tone="info" />
        <Kpi Icon={Building2}    label={isAr ? 'متوسّط تقدّم الحوكمة' : 'Gov. mean progress'}   value={`${agg ? Number(agg.meanProgressByCategory.GOVERNANCE ?? 0).toFixed(0) : 0}%`}    tone="primary" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'بلغ الهدف' : 'At target'}                       value={agg
          ? (agg.atTargetByCategory.ENVIRONMENTAL + agg.atTargetByCategory.SOCIAL + agg.atTargetByCategory.GOVERNANCE)
          : 0} tone="success" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'خارج المسار' : 'Off track'}                     value={agg
          ? (agg.offTrackByCategory.ENVIRONMENTAL + agg.offTrackByCategory.SOCIAL + agg.offTrackByCategory.GOVERNANCE)
          : 0} tone="danger" />
      </div>

      <Card title={isAr ? 'سجلّ مؤشّرات الاستدامة (E/S/G)' : 'ESG Metrics Register (E/S/G)'} Icon={TrendingDown}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {isAr
            ? 'سجلّ موحَّد لمؤشّرات البيئة والاجتماع والحوكمة. التقدّم يُحسب وفق الاتجاه — يدعم أهداف الارتفاع («حصة المتجدّدة») وأهداف الانخفاض («LTIFR»).'
            : 'Unified register for Environmental, Social, and Governance metrics. Progress is direction-aware — supports both raise-to-target ("renewable share") and lower-to-target ("LTIFR").'}
        </div>

        {/* Category filter chips */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
          {(['ALL', 'ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => (
            <button key={c} onClick={() => setCategoryFilter(c)}
              style={{
                padding: '4px 10px', fontSize: 11, fontWeight: 600,
                border: `1px solid ${categoryFilter === c ? 'var(--primary)' : 'var(--border)'}`,
                background: categoryFilter === c ? 'var(--primary-50)' : 'var(--surface-1)',
                color: categoryFilter === c ? 'var(--primary)' : 'var(--ink-2)',
                borderRadius: 'var(--r-sm)', cursor: 'pointer',
              }}>
              {c === 'ALL' ? (isAr ? 'الكل' : 'All') : esgMetricCategoryLabel(c as MetricCategory, isAr ? 'ar' : 'en')}
            </button>
          ))}
          <span className="text-2xs text-muted" style={{ marginInlineStart: 'auto', alignSelf: 'center' }}>
            {filtered.length} / {items.length}
          </span>
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد مؤشّرات' : 'No metrics yet'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'المؤشّر' : 'Metric'}</th>
                    <th>{isAr ? 'الفئة' : 'Category'}</th>
                    <th>{isAr ? 'خط الأساس' : 'Baseline'}</th>
                    <th>{isAr ? 'الحالي' : 'Current'}</th>
                    <th>{isAr ? 'الهدف' : 'Target'}</th>
                    <th>{isAr ? 'التقدّم' : 'Progress'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(m => {
                    const pct = computeMetricProgress(m);
                    return (
                      <tr key={m.id}>
                        <td className="mono text-2xs">{m.metricCode ?? `#${m.id}`}</td>
                        <td>
                          <div style={{ fontWeight: 600, color: 'var(--ink-1)', maxWidth: 320, lineHeight: 1.3 }}>
                            {isAr && m.metricNameAr ? m.metricNameAr : m.metricNameEn}
                          </div>
                          {m.ownerName && <div className="text-2xs text-muted" style={{ marginTop: 2 }}>{m.ownerName}</div>}
                        </td>
                        <td>
                          <Badge tone="neutral">{esgMetricCategoryLabel(m.category, isAr ? 'ar' : 'en')}</Badge>
                          {m.subcategory && <div className="text-2xs text-muted" style={{ marginTop: 2 }}>{m.subcategory}</div>}
                        </td>
                        <td className="mono text-xs" style={{ textAlign: 'end' }}>
                          {m.baselineValue != null ? Number(m.baselineValue).toLocaleString() : '—'}
                          <div className="text-2xs text-muted">{m.unit}</div>
                        </td>
                        <td className="mono text-xs" style={{ textAlign: 'end', fontWeight: 700 }}>
                          {m.currentValue != null ? Number(m.currentValue).toLocaleString() : '—'}
                          {m.currentValueAsOf && <div className="text-2xs text-muted">{m.currentValueAsOf}</div>}
                        </td>
                        <td className="mono text-xs" style={{ textAlign: 'end' }}>
                          {m.targetValue != null ? Number(m.targetValue).toLocaleString() : '—'}
                          {m.targetYear && <div className="text-2xs text-muted">→ {m.targetYear}</div>}
                        </td>
                        <td style={{ minWidth: 120 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <div style={{ flex: 1, height: 6, background: 'var(--surface-2)', borderRadius: 3, overflow: 'hidden' }}>
                              <div style={{
                                height: '100%', width: `${Math.min(100, pct)}%`,
                                background: pct >= 100 ? 'var(--success)' : pct >= 50 ? 'var(--primary)' : 'var(--warning)',
                                borderRadius: 3,
                              }} />
                            </div>
                            <span className="mono text-2xs" style={{ minWidth: 40, textAlign: 'end' }}>{pct.toFixed(0)}%</span>
                          </div>
                        </td>
                        <td><Badge tone={metricStatusTone(m.status)}>{metricStatusLabel(m.status, isAr ? 'ar' : 'en')}</Badge></td>
                        <td style={{ textAlign: 'end' }}>
                          <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                            {m.status !== 'ARCHIVED' && (
                              <Button size="sm" variant="secondary" onClick={() => setReadingMetric(m)}>
                                {isAr ? 'تسجيل' : 'Record'}
                              </Button>
                            )}
                            {m.status !== 'ARCHIVED' && (
                              <Button size="sm" variant="ghost" Icon={Trash2}
                                title={isAr ? 'أرشفة' : 'Archive'}
                                onClick={() => onArchive(m)} />
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={readingMetric !== null}
        title={isAr ? `تسجيل قراءة · ${readingMetric?.metricCode ?? ''}` : `Record reading · ${readingMetric?.metricCode ?? ''}`}
        fields={readingFields}
        initial={{
          currentValue: readingMetric?.currentValue ?? 0,
          asOf: new Date().toISOString().slice(0, 10),
        }}
        size="md"
        submitLabel={isAr ? 'حفظ' : 'Save'}
        onClose={() => setReadingMetric(null)}
        onSubmit={submitReading}
      />
    </div>
  );
}

// ─── ESG Evidence Repository tab (V64 — slice 10) ────────────────────────────
//
// Backs the evidence_ref text columns scattered across the V55-V63
// entities (climate risks, GHG, CMA, TCFD, SGI, materiality, metrics,
// CAPA). Single repository view filterable by module area + approval
// status + expiry window. Approve / Reject lifecycle for governance.
// Rule 9 sweep button surfaces evidence whose valid_to has passed.

function EvidenceTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<SE[]>([]);
  const [agg, setAgg]     = useState<EvidenceAgg | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<EvidenceApprovalStatus | 'ALL'>('ALL');
  const [approvingId, setApprovingId]   = useState<number | null>(null);
  const [rejectingId, setRejectingId]   = useState<number | null>(null);

  async function reload() {
    setLoading(true);
    try {
      const [pg, a] = await Promise.all([listEvidence({ size: 200 }), evidenceAggregate()]);
      setItems(pg.content);
      setAgg(a);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل مستودع الأدلة' : 'Failed to load evidence repository'));
      setItems([]); setAgg(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  const filtered = statusFilter === 'ALL'
    ? items
    : items.filter(e => e.approvalStatus === statusFilter);

  async function submitApprove(values: Record<string, unknown>) {
    if (approvingId == null) return;
    const by = String(values.approvedBy ?? '').trim();
    if (!by) { toast.danger(isAr ? 'الاسم مطلوب' : 'Name required'); return; }
    try { await approveEvidence(approvingId, by); toast.success(isAr ? 'تم الاعتماد' : 'Approved'); setApprovingId(null); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function submitReject(values: Record<string, unknown>) {
    if (rejectingId == null) return;
    const reviewerName = String(values.reviewerName ?? '').trim();
    const reason       = String(values.reason ?? '').trim();
    if (!reviewerName || !reason) {
      toast.danger(isAr ? 'الاسم والسبب مطلوبان' : 'Name and reason required'); return;
    }
    try { await rejectEvidence(rejectingId, reviewerName, reason); toast.info(isAr ? 'تم الرفض' : 'Rejected'); setRejectingId(null); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function onSweep() {
    if (!window.confirm(isAr ? 'فحص الأدلة المنتهية وتعليمها كـ EXPIRED؟' : 'Sweep expired evidence and mark them EXPIRED?')) return;
    try {
      const expired = await sweepExpiredEvidence();
      toast.success(isAr ? `تم تحديث ${expired.length} دليل` : `Marked ${expired.length} evidence row(s) expired`);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  const approveFields: FieldDef[] = [
    { kind: 'text', key: 'approvedBy', required: true, span: 12,
      label: isAr ? 'اسم المعتَمِد' : 'Approver name' },
  ];
  const rejectFields: FieldDef[] = [
    { kind: 'text', key: 'reviewerName', required: true, span: 12,
      label: isAr ? 'اسم المراجِع' : 'Reviewer name' },
    { kind: 'textarea', key: 'reason', span: 12, rows: 3,
      label: isAr ? 'سبب الرفض' : 'Rejection reason' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* KPI strip */}
      <div className="kpi-grid">
        <Kpi Icon={Paperclip}    label={isAr ? 'إجمالي الأدلة' : 'Total evidence'}            value={agg?.total ?? 0} tone="primary" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'معتمدة' : 'Approved'}                          value={agg?.byStatus.APPROVED ?? 0}       tone="success" />
        <Kpi Icon={ArrowRight}   label={isAr ? 'قيد المراجعة' : 'Pending review'}              value={agg?.byStatus.PENDING_REVIEW ?? 0} tone="info" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'مرفوضة' : 'Rejected'}                          value={agg?.byStatus.REJECTED ?? 0}       tone="danger" />
        <Kpi Icon={Clock}        label={isAr ? 'تنتهي خلال 30 يوماً' : 'Expiring within 30d'}  value={agg?.expiringSoon30d ?? 0}         tone={(agg?.expiringSoon30d ?? 0) > 0 ? 'warning' : 'success'} />
        <Kpi Icon={AlertCircle}  label={isAr ? 'منتهية' : 'Expired'}                           value={agg?.byStatus.EXPIRED ?? 0}        tone={(agg?.byStatus.EXPIRED ?? 0) > 0 ? 'danger' : 'success'} />
      </div>

      <Card title={isAr ? 'مستودع أدلة الاستدامة' : 'Sustainability Evidence Repository'}
            Icon={Paperclip}
            action={
              <Button size="sm" variant="secondary" Icon={Clock} onClick={onSweep}>
                {isAr ? 'فحص الانتهاء (القاعدة 9)' : 'Sweep expired (Rule 9)'}
              </Button>
            }>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {isAr
            ? 'مستودع موحَّد للأدلة الداعمة لجميع كيانات الاستدامة. كل دليل له فترة صلاحية، ومراجعة، وحالة اعتماد. عند انتهاء valid_to يتم وسم الإفصاحات المرتبطة بـ EVIDENCE_REQUIRED (القاعدة 9).'
            : 'Unified repository for evidence supporting every sustainability entity. Each row has a validity window, reviewer, and approval state. When valid_to passes, linked disclosures are auto-flagged EVIDENCE_REQUIRED (Rule 9).'}
        </div>

        {/* Status filter chips */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
          {(['ALL', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED', 'SUPERSEDED'] as const).map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              style={{
                padding: '4px 10px', fontSize: 11, fontWeight: 600,
                border: `1px solid ${statusFilter === s ? 'var(--primary)' : 'var(--border)'}`,
                background: statusFilter === s ? 'var(--primary-50)' : 'var(--surface-1)',
                color: statusFilter === s ? 'var(--primary)' : 'var(--ink-2)',
                borderRadius: 'var(--r-sm)', cursor: 'pointer',
              }}>
              {s === 'ALL' ? (isAr ? 'الكل' : 'All') : evidenceStatusLabel(s as EvidenceApprovalStatus, isAr ? 'ar' : 'en')}
            </button>
          ))}
          <span className="text-2xs text-muted" style={{ marginInlineStart: 'auto', alignSelf: 'center' }}>
            {filtered.length} / {items.length}
          </span>
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد أدلة مطابقة' : 'No matching evidence'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'العنوان' : 'Title'}</th>
                    <th>{isAr ? 'الوحدة' : 'Module'}</th>
                    <th>{isAr ? 'النوع' : 'Type'}</th>
                    <th>{isAr ? 'الإصدار' : 'Version'}</th>
                    <th>{isAr ? 'انتهاء الصلاحية' : 'Valid to'}</th>
                    <th>{isAr ? 'سرّية' : 'Confidentiality'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(e => {
                    const days = daysUntilExpiry(e);
                    const expiringSoon = days != null && days >= 0 && days <= 30
                      && e.approvalStatus === 'APPROVED';
                    return (
                      <tr key={e.id}>
                        <td className="mono text-2xs">{e.evidenceCode ?? `#${e.id}`}</td>
                        <td>
                          <div style={{ fontWeight: 600, color: 'var(--ink-1)', maxWidth: 360, lineHeight: 1.3 }}>
                            {isAr && e.titleAr ? e.titleAr : e.titleEn}
                          </div>
                          {e.fileName && (
                            <div className="text-2xs text-muted mono" style={{ marginTop: 2 }}>
                              <Paperclip size={9} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 3 }} />
                              {e.fileName}
                            </div>
                          )}
                          {e.verificationStatus === 'EXTERNAL_VERIFIED' && e.verificationProvider && (
                            <div className="text-2xs" style={{ marginTop: 2, color: 'var(--success-fg)' }}>
                              ✓ {isAr ? 'تحقّق خارجي:' : 'External verification:'} {e.verificationProvider}
                            </div>
                          )}
                        </td>
                        <td>{e.moduleArea && <Badge tone="neutral">{evidenceModuleLabel(e.moduleArea, isAr ? 'ar' : 'en')}</Badge>}</td>
                        <td className="text-xs">{e.evidenceType ?? '—'}</td>
                        <td className="mono text-2xs">{e.version ?? '—'}</td>
                        <td className="mono text-2xs">
                          {e.validTo ?? '—'}
                          {days != null && e.approvalStatus === 'APPROVED' && (
                            <div className="text-2xs" style={{ color: days < 0 ? 'var(--danger-fg)' : expiringSoon ? 'var(--warning-fg)' : 'var(--ink-3)' }}>
                              {days < 0
                                ? (isAr ? `منتهي قبل ${Math.abs(days)} يوماً` : `${Math.abs(days)}d expired`)
                                : (isAr ? `${days} يوماً متبقّياً` : `${days}d left`)}
                            </div>
                          )}
                        </td>
                        <td><Badge tone={evidenceConfidentialityTone(e.confidentiality)}>{evidenceConfidentialityLabel(e.confidentiality, isAr ? 'ar' : 'en')}</Badge></td>
                        <td><Badge tone={evidenceStatusTone(e.approvalStatus)}>{evidenceStatusLabel(e.approvalStatus, isAr ? 'ar' : 'en')}</Badge></td>
                        <td style={{ textAlign: 'end' }}>
                          <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                            {e.approvalStatus === 'PENDING_REVIEW' && (
                              <>
                                <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => setApprovingId(e.id!)}>
                                  {isAr ? 'اعتماد' : 'Approve'}
                                </Button>
                                <Button size="sm" variant="ghost" Icon={Trash2}
                                  title={isAr ? 'رفض' : 'Reject'}
                                  onClick={() => setRejectingId(e.id!)} />
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={approvingId !== null}
        title={isAr ? 'اعتماد الدليل' : 'Approve evidence'}
        fields={approveFields}
        initial={{ approvedBy: '' }}
        size="md"
        submitLabel={isAr ? 'اعتماد' : 'Approve'}
        onClose={() => setApprovingId(null)}
        onSubmit={submitApprove}
      />
      <FormModal
        open={rejectingId !== null}
        title={isAr ? 'رفض الدليل' : 'Reject evidence'}
        fields={rejectFields}
        initial={{ reviewerName: '', reason: '' }}
        size="md"
        submitLabel={isAr ? 'رفض' : 'Reject'}
        onClose={() => setRejectingId(null)}
        onSubmit={submitReject}
      />
    </div>
  );
}

// ─── Annual Sustainability Annex tab (V57) ───────────────────────────────────
//
// The headline Saudi compliance deliverable: bilingual annex covering CMA
// Articles 91-94, Saudi Green Initiative, TCFD, GHG inventory, and Climate
// Risks. Click "Generate" → backend composes Markdown + HTML + JSON from
// live data → DRAFT report row appears. Submit → Approve → Publish moves
// it through the lifecycle. Export downloads any of the three formats.

function AnnexTab() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [years, setYears]     = useState<Ry[]>([]);
  const [activeRy, setActive] = useState<Ry | null>(null);
  const [reports, setReports] = useState<SR[]>([]);
  const [loading, setLoading] = useState(true);
  const [generating, setGen]  = useState(false);
  const [previewId, setPreviewId] = useState<number | null>(null);
  // Slice-12 — selected report type for the Generate flow.
  const [selectedReportType, setSelectedReportType] = useState<ReportType>('ANNUAL_ANNEX');

  async function loadYears() {
    setLoading(true);
    try {
      const ys = await listReportingYears();
      setYears(ys);
      if (ys.length > 0 && !activeRy) {
        setActive(ys[0]);
        await loadReports(ys[0].id!);
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('annexLoadFailed'));
    } finally { setLoading(false); }
  }
  async function loadReports(ryId: number) {
    try {
      const list = await listReportsForYear(ryId);
      setReports(list);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('annexLoadFailed'));
    }
  }
  useEffect(() => { void loadYears(); }, []); // eslint-disable-line

  async function pickYear(y: Ry) {
    setActive(y);
    setPreviewId(null);
    await loadReports(y.id!);
  }

  async function onGenerate() {
    if (!activeRy?.id) return;
    setGen(true);
    try {
      // Slice 12 — dispatch via the generic generator when a non-default
      // type is selected; otherwise stay on the legacy endpoint.
      const r = selectedReportType === 'ANNUAL_ANNEX'
        ? await generateAnnex(activeRy.id)
        : await generateReport(activeRy.id, selectedReportType);
      toast.success(t('annexGenerated').replace('{v}', r.version));
      await loadReports(activeRy.id);
      setPreviewId(r.id!);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('annexActionFailed'));
    } finally { setGen(false); }
  }

  async function onSubmit_(id: number) {
    try { await submitReport(id); toast.success(t('annexSubmitted')); await loadReports(activeRy!.id!); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('annexActionFailed')); }
  }
  async function onApprove(id: number) {
    try { await approveReport(id); toast.success(t('annexApproved')); await loadReports(activeRy!.id!); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('annexActionFailed')); }
  }
  async function onPublish(id: number) {
    if (!window.confirm(t('annexPublishConfirm'))) return;
    try { await publishReport(id); toast.success(t('annexPublished')); await loadReports(activeRy!.id!); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('annexActionFailed')); }
  }
  async function onExport(id: number, fmt: 'md' | 'html' | 'json') {
    try { await exportReport(id, fmt); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('annexActionFailed')); }
  }

  const previewReport = useMemo(
    () => reports.find(r => r.id === previewId) ?? null,
    [reports, previewId]
  );

  if (loading) return <InlineEmpty>{t('annexLoading')}</InlineEmpty>;
  if (years.length === 0) return <InlineEmpty>{t('annexNoYears')}</InlineEmpty>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Year picker strip */}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="text-2xs text-muted" style={{ textTransform: 'uppercase' }}>{t('annexYear')}</span>
        {years.map(y => (
          <button key={y.id} type="button" onClick={() => pickYear(y)}
            style={{
              padding: '6px 12px', borderRadius: 'var(--r-sm)', fontSize: 12, fontWeight: 600,
              border: `1px solid ${y.id === activeRy?.id ? 'var(--primary)' : 'var(--border)'}`,
              background: y.id === activeRy?.id ? 'var(--primary-50)' : 'var(--surface-1)',
              cursor: 'pointer',
            }}>
            {y.year}
          </button>
        ))}
      </div>

      {/* Selected-year summary card */}
      {activeRy && (
        <Card title={t('annexYearTitle').replace('{y}', String(activeRy.year))} Icon={Building2}
          action={<Badge tone={ryStatusTone(activeRy.status)}>{activeRy.status}</Badge>}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
            <div className="stat-tile">
              <div className="stat-tile-label">{t('annexEntity')}</div>
              <div style={{ fontWeight: 600, color: 'var(--ink-1)' }}>
                {isAr && activeRy.reportingEntityAr ? activeRy.reportingEntityAr : activeRy.reportingEntityEn}
              </div>
            </div>
            <div className={`stat-tile stat-tile-${(activeRy.readinessScorePct ?? 0) >= 80 ? 'success' : 'warning'}`}>
              <div className="stat-tile-label">{t('annexReadiness')}</div>
              <div className="stat-tile-value">{activeRy.readinessScorePct == null ? '—' : `${activeRy.readinessScorePct}%`}</div>
            </div>
            <div className={`stat-tile stat-tile-${(activeRy.openGapsCount ?? 0) === 0 ? 'success' : 'warning'}`}>
              <div className="stat-tile-label">{t('annexOpenGaps')}</div>
              <div className="stat-tile-value">{activeRy.openGapsCount ?? 0}</div>
            </div>
            <div className="stat-tile">
              <div className="stat-tile-label">{t('annexPrepared')}</div>
              <div style={{ fontWeight: 500, color: 'var(--ink-2)', fontSize: 13 }}>{activeRy.preparedBy ?? '—'}</div>
            </div>
          </div>
          {activeRy.notes && (
            <div style={{ marginTop: 12, padding: 10, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', fontSize: 12, color: 'var(--ink-2)' }}>
              {activeRy.notes}
            </div>
          )}
          <div style={{ marginTop: 12, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            {/* Slice-12 — report-type picker. Default ANNUAL_ANNEX
                preserves the legacy one-button flow. */}
            <select
              value={selectedReportType}
              onChange={e => setSelectedReportType(e.target.value as ReportType)}
              style={{
                padding: '8px 10px', fontSize: 12,
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                background: 'var(--surface-1)', color: 'var(--ink-1)', minWidth: 240,
              }}
              disabled={generating}
            >
              {(['ANNUAL_ANNEX', 'BOARD_ESG', 'CMA_MATRIX', 'TCFD_REPORT', 'GHG_REPORT',
                 'SGI_PROGRESS', 'CLIMATE_RISK', 'CAPA_STATUS', 'EVIDENCE_PKG', 'DASH_EXPORT'] as ReportType[]).map(t => (
                <option key={t} value={t}>{reportTypeLabel(t, lang)}</option>
              ))}
            </select>
            <Button variant="primary" Icon={FileText} onClick={onGenerate} disabled={generating || !activeRy.id}>
              {generating ? t('annexGenerating') : t('annexGenerateBtn')}
            </Button>
          </div>
        </Card>
      )}

      {/* Reports list */}
      <Card title={t('annexReportsTitle')} Icon={FileText}>
        {reports.length === 0
          ? <InlineEmpty>{t('annexNoReports')}</InlineEmpty>
          : (
            <table className="grc-table">
              <thead>
                <tr>
                  <th>{t('annexColCode')}</th>
                  <th>{t('annexColType')}</th>
                  <th>{t('annexColVersion')}</th>
                  <th>{t('annexColGenerated')}</th>
                  <th>{t('annexColStatus')}</th>
                  <th style={{ textAlign: 'end' }}>{t('annexColActions')}</th>
                </tr>
              </thead>
              <tbody>
                {reports.map(r => (
                  <tr key={r.id} style={r.id === previewId ? { background: 'var(--primary-50)' } : undefined}>
                    <td className="mono">{r.reportCode ?? `#${r.id}`}</td>
                    <td><Badge tone="info">{r.reportType}</Badge></td>
                    <td className="mono">{r.version}</td>
                    <td className="mono text-xs">{r.generatedAt?.slice(0, 16).replace('T', ' ')}</td>
                    <td><Badge tone={reportStatusTone(r.status)}>{r.status}</Badge></td>
                    <td style={{ textAlign: 'end' }}>
                      <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                        <Button variant="ghost"     size="sm" onClick={() => setPreviewId(r.id!)}>{t('annexActPreview')}</Button>
                        <Button variant="ghost"     size="sm" onClick={() => onExport(r.id!, 'md')}>MD</Button>
                        <Button variant="ghost"     size="sm" onClick={() => onExport(r.id!, 'html')}>HTML</Button>
                        <Button variant="ghost"     size="sm" onClick={() => onExport(r.id!, 'json')}>JSON</Button>
                        {r.status === 'DRAFT'     && <Button variant="secondary" size="sm" Icon={ArrowRight}   onClick={() => onSubmit_(r.id!)}>{t('annexActSubmit')}</Button>}
                        {r.status === 'SUBMITTED' && <Button variant="secondary" size="sm" Icon={CheckCircle2} onClick={() => onApprove(r.id!)}>{t('annexActApprove')}</Button>}
                        {r.status === 'APPROVED'  && <Button variant="primary"   size="sm" Icon={CheckCircle2} onClick={() => onPublish(r.id!)}>{t('annexActPublish')}</Button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
      </Card>

      {/* Live preview pane */}
      {previewReport && previewReport.contentMarkdown && (
        <Card title={t('annexPreviewTitle').replace('{v}', previewReport.version)} Icon={FileText}
          action={<Badge tone={reportStatusTone(previewReport.status)}>{previewReport.status}</Badge>}>
          <pre style={{
            fontFamily: 'var(--font-mono)', fontSize: 11, lineHeight: 1.55,
            background: 'var(--surface-2)', color: 'var(--ink-1)',
            padding: 16, borderRadius: 'var(--r-sm)',
            border: '1px solid var(--border)',
            whiteSpace: 'pre-wrap', maxHeight: 480, overflow: 'auto',
            direction: 'ltr',
          }}>{previewReport.contentMarkdown}</pre>
        </Card>
      )}
    </div>
  );
}

// ─── GHG Inventory tab (V56) ─────────────────────────────────────────────────
//
// Live, queryable, verifiable GHG inventory — replaces the static mock
// totals on the overview tab with a per-record register. Surfaces:
//   • Aggregate KPIs (Scope 1/2/3 totals, grand total, Scope-3 coverage %)
//   • Rule-1 dashboard alert when Scope-3 weighted coverage < 70%
//   • YoY chips per scope (computed server-side from prior-year aggregate)
//   • Status-gated lifecycle buttons (Submit → Verify → Approve)
//   • Rule-2 client mirror: Verify button disabled until evidence_ref set

function GhgInventoryTab() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [year, setYear]   = useState<number>(new Date().getFullYear() - 1); // align with seed (2025)
  const [scope, setScope] = useState<GhgScope | undefined>(undefined);
  const [rows, setRows]   = useState<GhgEmissionRecord[]>([]);
  const [agg, setAgg]     = useState<GhgAggregate | null>(null);
  const [yoy, setYoy]     = useState<GhgYoY | null>(null);
  const [loading, setLoading] = useState(true);
  const [openNew, setOpenNew] = useState(false);

  async function reload() {
    setLoading(true);
    try {
      const [page, aggData, yoyData] = await Promise.all([
        listGhgRecords({ year, scope, size: 200 }),
        ghgAggregate(year),
        ghgYoy(year),
      ]);
      setRows(page.content);
      setAgg(aggData);
      setYoy(yoyData);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('ghgLoadFailed'));
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, [year, scope]); // eslint-disable-line react-hooks/exhaustive-deps

  async function onCreate(values: Record<string, unknown>) {
    try {
      await createGhgRecord({
        reportingYear: Number(values.reportingYear ?? year),
        scope: Number(values.scope ?? 1) as GhgScope,
        category: String(values.category ?? 'STATIONARY_COMBUSTION'),
        sourceNameEn: String(values.sourceNameEn ?? '').trim(),
        sourceNameAr: String(values.sourceNameAr ?? '').trim() || undefined,
        emissionValueTco2e: Number(values.emissionValueTco2e ?? 0),
        coveragePercent:    Number(values.coveragePercent ?? 100),
        evidenceRef: String(values.evidenceRef ?? '').trim() || undefined,
        ownerName:   String(values.ownerName ?? '').trim() || undefined,
        methodology: String(values.methodology ?? '').trim() || undefined,
        verified: false,
      });
      toast.success(t('ghgCreated'));
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('ghgActionFailed'));
    }
  }
  async function onSubmit_(id: number) {
    try { await submitGhgRecord(id); toast.success(t('ghgSubmitted')); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('ghgActionFailed')); }
  }
  async function onVerify(id: number) {
    try { await verifyGhgRecord(id); toast.success(t('ghgVerified')); await reload(); }
    catch (e) {
      // Rule 2 — server rejects with a clear message if evidence missing.
      toast.danger(e instanceof ApiError ? e.message : t('ghgActionFailed'));
    }
  }
  async function onApprove(id: number) {
    try { await approveGhgRecord(id); toast.success(t('ghgApproved')); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('ghgActionFailed')); }
  }

  const FIELDS: FieldDef[] = [
    { kind: 'number',   key: 'reportingYear',      label: t('ghgFldYear') + ' *',  required: true, min: 2020, max: 2050, span: 3 },
    { kind: 'select',   key: 'scope',              label: t('ghgFldScope') + ' *', required: true, span: 3,
      options: [{ value: '1', label: 'Scope 1' }, { value: '2', label: 'Scope 2' }, { value: '3', label: 'Scope 3' }] },
    { kind: 'select',   key: 'category',           label: t('ghgFldCategory') + ' *', required: true, span: 6,
      options: [
        { value: 'STATIONARY_COMBUSTION',    label: 'Stationary combustion (S1)' },
        { value: 'MOBILE_COMBUSTION',        label: 'Mobile combustion (S1)' },
        { value: 'FUGITIVE',                 label: 'Fugitive (S1)' },
        { value: 'PROCESS',                  label: 'Process (S1)' },
        { value: 'PURCHASED_ELECTRICITY',    label: 'Purchased electricity (S2)' },
        { value: 'PURCHASED_HEAT_STEAM',     label: 'Purchased heat/steam (S2)' },
        { value: 'PURCHASED_GOODS_SERVICES', label: 'Purchased goods & services (S3)' },
        { value: 'CAPITAL_GOODS',            label: 'Capital goods (S3)' },
        { value: 'BUSINESS_TRAVEL',          label: 'Business travel (S3)' },
        { value: 'EMPLOYEE_COMMUTING',       label: 'Employee commuting (S3)' },
        { value: 'UPSTREAM_TRANSPORT',       label: 'Upstream transport (S3)' },
        { value: 'WASTE_OPERATIONS',         label: 'Waste from operations (S3)' },
        { value: 'USE_OF_SOLD_PRODUCTS',     label: 'Use of sold products (S3)' },
      ] },
    { kind: 'text',     key: 'sourceNameEn',       label: t('ghgFldSourceEn') + ' *', required: true, span: 12 },
    { kind: 'text',     key: 'sourceNameAr',       label: t('ghgFldSourceAr'),         span: 12 },
    { kind: 'number',   key: 'emissionValueTco2e', label: t('ghgFldValue') + ' *',     required: true, min: 0, span: 6 },
    { kind: 'number',   key: 'coveragePercent',    label: t('ghgFldCoverage'),         min: 0, max: 100, span: 6 },
    { kind: 'text',     key: 'methodology',        label: t('ghgFldMethodology'),      placeholder: 'GHG Protocol Corporate Standard', span: 6 },
    { kind: 'text',     key: 'evidenceRef',        label: t('ghgFldEvidence'),         span: 6 },
    { kind: 'text',     key: 'ownerName',          label: t('ghgFldOwner'),            span: 6 },
  ];

  function pctChip(v?: number) {
    if (v == null) return '—';
    const sign = v > 0 ? '+' : '';
    return `${sign}${Number(v).toFixed(1)}%`;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Year + scope filter strip */}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="text-2xs text-muted" style={{ textTransform: 'uppercase' }}>{t('ghgFilterYear')}</span>
        <select className="input" style={{ width: 'auto' }} value={year} onChange={e => setYear(Number(e.target.value))}>
          {[2024, 2025, 2026].map(y => <option key={y} value={y}>{y}</option>)}
        </select>
        <span className="text-2xs text-muted" style={{ textTransform: 'uppercase', marginInlineStart: 12 }}>{t('ghgFilterScope')}</span>
        <select className="input" style={{ width: 'auto' }} value={scope ?? ''} onChange={e => setScope(e.target.value ? Number(e.target.value) as GhgScope : undefined)}>
          <option value="">{isAr ? 'الكل' : 'All'}</option>
          <option value="1">Scope 1</option>
          <option value="2">Scope 2</option>
          <option value="3">Scope 3</option>
        </select>
      </div>

      {/* KPI strip — totals + YoY + Rule-1 flag */}
      <div className="kpi-grid">
        <Kpi Icon={Leaf} label={t('ghgKpiScope1')} value={agg ? `${(Number(agg.scope1Total) / 1000).toFixed(1)}k` : '—'} tone="success" />
        <Kpi Icon={Leaf} label={t('ghgKpiScope2')} value={agg ? `${(Number(agg.scope2Total) / 1000).toFixed(1)}k` : '—'} tone="success" />
        <Kpi Icon={Leaf} label={t('ghgKpiScope3')} value={agg ? `${(Number(agg.scope3Total) / 1000).toFixed(1)}k` : '—'} tone="warning" />
        <Kpi Icon={Leaf} label={t('ghgKpiTotal')}  value={agg ? `${(Number(agg.grandTotal) / 1000).toFixed(1)}k` : '—'} tone="primary" />
        <Kpi Icon={CheckCircle2} label={t('ghgKpiS3Coverage')} value={agg ? `${Number(agg.scope3Coverage).toFixed(0)}%` : '—'}
          tone={agg?.scope3PartialFlag ? 'danger' : 'success'} />
      </div>

      {/* Rule 1 — Scope-3 partial-disclosure warning */}
      {agg?.scope3PartialFlag && (
        <div style={{
          display: 'flex', gap: 10, alignItems: 'flex-start',
          padding: 12, borderRadius: 'var(--r-md)',
          background: 'var(--danger-bg)', borderInlineStart: '4px solid var(--danger)',
        }}>
          <AlertCircle size={18} style={{ color: 'var(--danger-fg)', flexShrink: 0, marginTop: 2 }} />
          <div className="text-sm" style={{ color: 'var(--danger-fg)', lineHeight: 1.5 }}>
            <strong>{t('ghgRule1Title')}:</strong> {t('ghgRule1Body').replace('{coverage}', Number(agg.scope3Coverage).toFixed(0))}
          </div>
        </div>
      )}

      {/* YoY chips */}
      {yoy && (
        <Card title={t('ghgYoyTitle')} Icon={TrendingDown}>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            {[
              { label: 'Scope 1', v: Number(yoy.scope1YoY) },
              { label: 'Scope 2', v: Number(yoy.scope2YoY) },
              { label: 'Scope 3', v: Number(yoy.scope3YoY) },
              { label: t('ghgYoyTotal'), v: Number(yoy.totalYoY) },
            ].map((it, i) => (
              <div key={i} style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span className="text-2xs text-muted" style={{ textTransform: 'uppercase' }}>{it.label}</span>
                <Badge tone={it.v < 0 ? 'success' : it.v > 0 ? 'danger' : 'neutral'}>{pctChip(it.v)}</Badge>
              </div>
            ))}
          </div>
        </Card>
      )}

      <Card title={t('ghgPageTitle')} Icon={Leaf}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>{t('ghgNew')}</Button>}>
        {loading
          ? <InlineEmpty>{t('ghgLoading')}</InlineEmpty>
          : rows.length === 0
            ? <InlineEmpty>{t('ghgEmpty')}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{t('ghgColCode')}</th>
                    <th>{t('ghgColScope')}</th>
                    <th>{t('ghgColSource')}</th>
                    <th>{t('ghgColCategory')}</th>
                    <th style={{ textAlign: 'end' }}>{t('ghgColValue')}</th>
                    <th style={{ textAlign: 'center' }}>{t('ghgColCoverage')}</th>
                    <th>{t('ghgColStatus')}</th>
                    <th style={{ textAlign: 'end' }}>{t('ghgColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={r.id}>
                      <td className="mono">{r.recordCode ?? `#${r.id}`}</td>
                      <td><Badge tone={r.scope === 1 ? 'success' : r.scope === 2 ? 'info' : 'warning'}>S{r.scope}</Badge></td>
                      <td>
                        <div style={{ fontWeight: 600, color: 'var(--ink-1)', maxWidth: 320, lineHeight: 1.3 }}>
                          {isAr && r.sourceNameAr ? r.sourceNameAr : r.sourceNameEn}
                        </div>
                        {r.facility && <div className="text-2xs text-muted" style={{ marginTop: 2 }}>{r.facility}</div>}
                      </td>
                      <td className="text-xs">{r.category}</td>
                      <td style={{ textAlign: 'end' }} className="mono">{Number(r.emissionValueTco2e).toLocaleString()}</td>
                      <td style={{ textAlign: 'center' }}>
                        <Badge tone={Number(r.coveragePercent) >= 90 ? 'success' : Number(r.coveragePercent) >= 70 ? 'info' : 'warning'}>
                          {Number(r.coveragePercent).toFixed(0)}%
                        </Badge>
                      </td>
                      <td><Badge tone={ghgStatusTone(r.status)}>{r.status}</Badge></td>
                      <td style={{ textAlign: 'end' }}>
                        <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                          {r.status === 'DRAFT'     && <Button variant="secondary" size="sm" Icon={ArrowRight} onClick={() => onSubmit_(r.id!)}>{t('ghgActSubmit')}</Button>}
                          {r.status === 'SUBMITTED' && (
                            <Button variant="secondary" size="sm" Icon={CheckCircle2}
                              onClick={() => onVerify(r.id!)}
                              disabled={!canVerify(r)}
                              title={canVerify(r) ? '' : (isAr ? 'يجب إرفاق دليل قبل التحقّق (قاعدة 2)' : 'Attach evidence before verifying (Rule 2)')}>
                              {t('ghgActVerify')}
                            </Button>
                          )}
                          {r.status === 'VERIFIED'  && <Button variant="primary"   size="sm" Icon={CheckCircle2} onClick={() => onApprove(r.id!)}>{t('ghgActApprove')}</Button>}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={openNew}
        title={t('ghgNewTitle')}
        fields={FIELDS}
        initial={{ reportingYear: year, scope: 1, coveragePercent: 100 }}
        onClose={() => setOpenNew(false)}
        onSubmit={onCreate}
      />
    </div>
  );
}

// ─── SGI tab ────────────────────────────────────────────────────────────────

// ─── SGI commitments tab (V59 — slice 5) ───────────────────────────────────
//
// Promoted from the static SGI_COMMITMENTS constant (kept for backwards
// compatibility with the overview KPI math in lib/esg.ts) to a live
// API-driven register. Per-commitment lifecycle:
//   - Achieve: requires current value to actually meet target (Rule A
//     mirrored client-side via canAchieve)
//   - Retire: requires a written reason (Rule B — captured via dialog)
//
// Progress bars are computed client-side from baseline/current/target so
// the tab doesn't have to hit /progress for every row.

function SgiTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<SgiC[]>([]);
  const [agg, setAgg]     = useState<SgiAgg | null>(null);
  const [loading, setLoading] = useState(true);
  const [achievingId, setAchievingId] = useState<number | null>(null);
  const [retiringId, setRetiringId]   = useState<number | null>(null);

  async function reload() {
    setLoading(true);
    try {
      const [pg, a] = await Promise.all([listSgi({ size: 200 }), sgiAggregate()]);
      setItems(pg.content);
      setAgg(a);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل الالتزامات' : 'Failed to load commitments'));
      setItems([]); setAgg(null);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  // Group active commitments by sector, in display order.
  const bySector = useMemo(() => {
    const map = new Map<string, SgiC[]>();
    for (const c of items) {
      if (!map.has(c.sector)) map.set(c.sector, []);
      map.get(c.sector)!.push(c);
    }
    return map;
  }, [items]);

  async function submitAchieve(values: Record<string, unknown>) {
    if (achievingId == null) return;
    const achievedBy = String(values.achievedBy ?? '').trim();
    if (!achievedBy) { toast.danger(isAr ? 'اسم مُعتمِد الإنجاز مطلوب' : 'Approver name required'); return; }
    try {
      await achieveSgi(achievingId, achievedBy);
      toast.success(isAr ? 'تم الإنجاز' : 'Achieved');
      setAchievingId(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }
  async function submitRetire(values: Record<string, unknown>) {
    if (retiringId == null) return;
    const retiredBy = String(values.retiredBy ?? '').trim();
    const reason    = String(values.reason    ?? '').trim();
    if (!retiredBy || !reason) {
      toast.danger(isAr ? 'الاسم والسبب مطلوبان' : 'Name and reason are required'); return;
    }
    try {
      await retireSgi(retiringId, retiredBy, reason);
      toast.info(isAr ? 'تم التقاعد' : 'Retired');
      setRetiringId(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  const achieveFields: FieldDef[] = [
    { kind: 'text', key: 'achievedBy', required: true, span: 12,
      label: isAr ? 'اسم المعتَمِد للإنجاز' : 'Approved-by name',
      placeholder: isAr ? 'مثال: سارة الزهراني (مدير الاستدامة)' : 'e.g. Sara Al-Zahrani (CSO)' },
  ];
  const retireFields: FieldDef[] = [
    { kind: 'text', key: 'retiredBy', required: true, span: 12,
      label: isAr ? 'اسم المعتَمِد للتقاعد' : 'Retired-by name',
      placeholder: isAr ? 'مثال: سارة الزهراني (مدير الاستدامة)' : 'e.g. Sara Al-Zahrani (CSO)' },
    // FieldDef.textarea doesn't carry a `required` flag — submitRetire
    // re-validates that the reason isn't empty before dispatch (Rule B).
    { kind: 'textarea', key: 'reason', span: 12, rows: 3,
      label: isAr ? 'سبب التقاعد (للسجل المراجعي) *' : 'Reason (for audit trail) *' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* KPI strip — tier counts + lifecycle totals */}
      <div className="kpi-grid">
        <Kpi Icon={Target}       label={isAr ? 'الالتزامات النشطة' : 'Active commitments'} value={agg?.activeTotal ?? 0}   tone="primary" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'على المسار' : 'On track'}                  value={agg?.byTier.ON_TRACK ?? 0} tone="success" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'مُعرَّض للخطر' : 'At risk'}                value={agg?.byTier.AT_RISK ?? 0}  tone="warning" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'متأخّر' : 'Delayed'}                       value={agg?.byTier.DELAYED ?? 0}  tone="danger" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'مُنجَزة' : 'Achieved'}                     value={agg?.achievedTotal ?? 0}   tone="success" />
        {/* Retired is intentionally tone-less (primary default — black ink)
            so it reads as a quiet historical counter alongside the
            louder status colors. KpiTone doesn't have a "neutral". */}
        <Kpi Icon={Trash2}       label={isAr ? 'مُتقاعَدة' : 'Retired'}                   value={agg?.retiredTotal ?? 0} />
      </div>

      <Card title={isAr ? 'التزامات المبادرة السعودية الخضراء' : 'Saudi Green Initiative commitments'} Icon={Target}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {isAr
            ? 'سجلّ الالتزامات المحفوظ مع تتبّع التقدّم، المالك، الموعد التالي، والسجل المراجعي. الإنجاز يتطلّب الوصول للهدف؛ التقاعد يتطلّب سبباً موثّقاً.'
            : 'Registered commitments with progress tracking, owner, next milestone, and audit trail. Achievement requires the target to be met; retirement requires a documented reason.'}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد التزامات' : 'No commitments yet'}</InlineEmpty>
            : Array.from(bySector.entries()).map(([sector, sectorItems]) => (
              <div key={sector} style={{ marginBottom: 16 }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--primary)', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                  <Leaf size={12} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
                  {sgiSectorLabel(sector as 'ENERGY', isAr ? 'ar' : 'en')}
                </div>
                {sectorItems.map(c => {
                  const progressPct = computeProgressPct(c);
                  const isActive = c.status === 'ACTIVE';
                  return (
                    <div key={c.id} style={{
                      padding: 12, marginBottom: 8, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                      background: c.status === 'ACHIEVED' ? 'var(--success-bg)'
                                : c.status === 'RETIRED'  ? 'var(--surface-2)' : undefined,
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8, marginBottom: 6, flexWrap: 'wrap' }}>
                        <div>
                          <div style={{ fontSize: 13, fontWeight: 600 }}>
                            {isAr && c.titleAr ? c.titleAr : c.titleEn}
                          </div>
                          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                            <span className="mono">{c.commitmentCode}</span>
                            {' · '}
                            {isAr ? 'الهدف' : 'Target'} {c.targetYear}
                            {c.ownerName && <> {' · '} {c.ownerName}</>}
                          </div>
                        </div>
                        <div style={{ display: 'inline-flex', gap: 6, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                          <Badge tone={sgiStatusTone(c.status)}>{sgiStatusLabel(c.status, isAr ? 'ar' : 'en')}</Badge>
                          {isActive && (
                            <>
                              <Button size="sm" variant="secondary" Icon={CheckCircle2}
                                disabled={!canAchieve(c)}
                                title={!canAchieve(c) ? (isAr ? 'لم يصل القياس الحالي إلى الهدف بعد' : 'Current value has not met target yet') : undefined}
                                onClick={() => setAchievingId(c.id!)}>
                                {isAr ? 'إنجاز' : 'Achieve'}
                              </Button>
                              <Button size="sm" variant="ghost" Icon={Trash2}
                                title={isAr ? 'تقاعد' : 'Retire'}
                                onClick={() => setRetiringId(c.id!)} />
                            </>
                          )}
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <div style={{ flex: 1, height: 8, background: 'var(--surface-2)', borderRadius: 4, overflow: 'hidden' }}>
                          <div style={{
                            height: '100%', width: `${Math.min(100, progressPct)}%`,
                            background: progressPct >= 75 ? 'var(--success)'
                                       : progressPct >= 40 ? 'var(--primary)'
                                       : 'var(--warning)',
                            borderRadius: 4,
                          }} />
                        </div>
                        <div className="mono" style={{ fontSize: 12, fontWeight: 700, minWidth: 60, textAlign: 'end' }}>
                          {progressPct.toFixed(1)}%
                        </div>
                      </div>
                      <div style={{ display: 'flex', gap: 14, marginTop: 8, fontSize: 11, color: 'var(--ink-3)', flexWrap: 'wrap' }}>
                        {c.baselineValue != null && <span>{isAr ? 'خط الأساس' : 'Baseline'}: <span className="mono">{Number(c.baselineValue).toLocaleString()}</span> {c.unit}</span>}
                        {c.currentValue != null && <span>{isAr ? 'الحالي' : 'Current'}: <span className="mono">{Number(c.currentValue).toLocaleString()}</span> {c.unit}</span>}
                        <span>{isAr ? 'الهدف' : 'Target'}: <span className="mono">{Number(c.targetValue).toLocaleString()}</span> {c.unit}</span>
                      </div>
                      {(isAr ? c.notesAr : c.notesEn) && (
                        <div style={{ marginTop: 8, fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.6, padding: 8, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
                          {isAr && c.notesAr ? c.notesAr : c.notesEn}
                        </div>
                      )}
                      {c.nextMilestoneDate && (
                        <div style={{ marginTop: 6, fontSize: 11, color: 'var(--info-fg)', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                          <ArrowRight size={11} />
                          <span>{isAr ? 'المرحلة التالية' : 'Next milestone'} ({c.nextMilestoneDate}):</span>
                          <span>{isAr && c.nextMilestoneAr ? c.nextMilestoneAr : c.nextMilestoneEn}</span>
                        </div>
                      )}
                      {c.status === 'RETIRED' && c.retiredReason && (
                        <div style={{ marginTop: 8, fontSize: 11, color: 'var(--ink-2)', fontStyle: 'italic' }}>
                          {isAr ? 'سبب التقاعد:' : 'Retired:'} {c.retiredReason}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ))}
      </Card>

      <FormModal
        open={achievingId !== null}
        title={isAr ? 'تأكيد إنجاز الالتزام' : 'Confirm commitment achievement'}
        fields={achieveFields}
        initial={{ achievedBy: '' }}
        size="md"
        submitLabel={isAr ? 'تأكيد' : 'Confirm'}
        onClose={() => setAchievingId(null)}
        onSubmit={submitAchieve}
      />
      <FormModal
        open={retiringId !== null}
        title={isAr ? 'تقاعد الالتزام' : 'Retire commitment'}
        fields={retireFields}
        initial={{ retiredBy: '', reason: '' }}
        size="md"
        submitLabel={isAr ? 'تقاعد' : 'Retire'}
        onClose={() => setRetiringId(null)}
        onSubmit={submitRetire}
      />
    </div>
  );
}

// ─── Settings tab (slice 14-A · V65) ────────────────────────────────────────
//
// CRUD over the per-org Sustainability thresholds. Lazy-creates the
// row on first load via getSettings(); save uses partial-update
// semantics so callers only need to send what they changed.

function SettingsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';
  const [settings, setSettings] = useState<Partial<SustSettings> | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  async function reload() {
    setLoading(true);
    try { setSettings(await getSettings()); }
    catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل الإعدادات' : 'Failed to load settings'));
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  async function onSave() {
    if (!settings) return;
    setSaving(true);
    try {
      const updated = await updateSettings(settings);
      setSettings(updated);
      toast.success(isAr ? 'تم الحفظ' : 'Saved');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Save failed');
    } finally { setSaving(false); }
  }

  async function onReset() {
    if (!window.confirm(isAr
      ? 'إعادة كل العتبات إلى القيم الافتراضية؟'
      : 'Reset all thresholds to defaults?')) return;
    setSaving(true);
    try {
      const fresh = await resetSettings();
      setSettings(fresh);
      toast.info(isAr ? 'أُعيدت العتبات إلى الافتراضي' : 'Settings reset to defaults');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Reset failed');
    } finally { setSaving(false); }
  }

  if (loading || !settings) {
    return <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>;
  }

  // Generic numeric-field renderer. Bilingual label + helper text +
  // optional min/max + unit suffix. Keeps the form file-short by
  // declaring fields data-style instead of one JSX block per field.
  function Num(label: { en: string; ar: string }, key: keyof SustSettings,
               opts: { min?: number; max?: number; step?: number; unit?: string;
                       help?: { en: string; ar: string } }) {
    const val = settings![key] as number | undefined;
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>
          {isAr ? label.ar : label.en}
        </label>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <input
            type="number"
            min={opts.min} max={opts.max} step={opts.step ?? 1}
            value={val ?? ''}
            onChange={e => setSettings(s => ({ ...s!, [key]: Number(e.target.value) }))}
            style={{
              padding: '6px 10px', fontSize: 13, width: 120,
              border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              background: 'var(--surface-1)', color: 'var(--ink-1)',
            }}
          />
          {opts.unit && <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{opts.unit}</span>}
        </div>
        {opts.help && (
          <span style={{ fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5 }}>
            {isAr ? opts.help.ar : opts.help.en}
          </span>
        )}
      </div>
    );
  }

  function Text(label: { en: string; ar: string }, key: keyof SustSettings,
                opts: { help?: { en: string; ar: string }; placeholder?: string }) {
    const val = settings![key] as string | undefined;
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>
          {isAr ? label.ar : label.en}
        </label>
        <input
          type="text"
          value={val ?? ''}
          placeholder={opts.placeholder}
          onChange={e => setSettings(s => ({ ...s!, [key]: e.target.value }))}
          style={{
            padding: '6px 10px', fontSize: 13, fontFamily: 'var(--font-mono)',
            border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            background: 'var(--surface-1)', color: 'var(--ink-1)', maxWidth: 280,
          }}
        />
        {opts.help && (
          <span style={{ fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5 }}>
            {isAr ? opts.help.ar : opts.help.en}
          </span>
        )}
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card title={isAr ? 'إعدادات الاستدامة' : 'Sustainability settings'} Icon={Wrench}
        action={
          <div style={{ display: 'inline-flex', gap: 6 }}>
            <Button size="sm" variant="ghost" Icon={Trash2} onClick={onReset} disabled={saving}>
              {isAr ? 'إعادة للافتراضي' : 'Reset defaults'}
            </Button>
            <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={onSave} disabled={saving}>
              {saving ? (isAr ? 'يحفظ…' : 'Saving…') : (isAr ? 'حفظ' : 'Save')}
            </Button>
          </div>
        }>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 16, lineHeight: 1.5 }}>
          {isAr
            ? 'تُحدّد هذه العتبات سلوك القواعد 1 و 3 و 9 و 13 و 14 على مستوى المؤسسة. التغييرات تسري فوراً.'
            : 'These thresholds drive the runtime behaviour of Rules 1, 3, 9, 13 and 14 at the organisation level. Changes take effect immediately.'}
        </div>

        {/* Section: Rule 14 — ESG readiness bands */}
        <div style={{ marginBottom: 20 }}>
          <h4 style={{ fontSize: 13, fontWeight: 700, color: 'var(--primary)', marginBottom: 8 }}>
            {isAr ? 'القاعدة 14 — لافتة جاهزية الاستدامة' : 'Rule 14 — ESG readiness banner'}
          </h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
            {Num({ en: 'Warning threshold', ar: 'عتبة التحذير' }, 'esgReadinessWarningThreshold',
              { min: 0, max: 100, unit: '/ 100',
                help: { en: 'Banner appears below this score.', ar: 'تظهر اللافتة دون هذه الدرجة.' } })}
            {Num({ en: 'Critical threshold', ar: 'عتبة الحرج' }, 'esgReadinessCriticalThreshold',
              { min: 0, max: 100, unit: '/ 100',
                help: { en: 'Banner shows in danger tone below this.', ar: 'تتحوّل اللافتة إلى نغمة الخطر دون هذه الدرجة.' } })}
          </div>
        </div>

        {/* Section: Rule 1 — Scope 3 */}
        <div style={{ marginBottom: 20 }}>
          <h4 style={{ fontSize: 13, fontWeight: 700, color: 'var(--primary)', marginBottom: 8 }}>
            {isAr ? 'القاعدة 1 — تغطية النطاق 3' : 'Rule 1 — Scope 3 coverage'}
          </h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
            {Num({ en: 'Partial-coverage threshold', ar: 'عتبة التغطية الجزئية' }, 'scope3PartialCoverageThreshold',
              { min: 0, max: 100, step: 0.5, unit: '%',
                help: { en: 'Below this weighted coverage, GHG aggregate flags Scope 3 as Partial.', ar: 'دون هذه التغطية الموزونة، يصنّف تجميع الانبعاثات النطاق 3 كجزئي.' } })}
          </div>
        </div>

        {/* Section: Rule 3 — SGI at-risk */}
        <div style={{ marginBottom: 20 }}>
          <h4 style={{ fontSize: 13, fontWeight: 700, color: 'var(--primary)', marginBottom: 8 }}>
            {isAr ? 'القاعدة 3 — تصنيف "معرّض للخطر" لالتزامات SGI' : 'Rule 3 — SGI at-risk classification'}
          </h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
            {Num({ en: 'At-risk factor', ar: 'عامل الخطر' }, 'sgiAtRiskProgressFactor',
              { min: 0, max: 1, step: 0.05,
                help: { en: 'Commitment is "at risk" when actual progress < expected × factor (e.g. 0.70 = 70%).', ar: 'الالتزام "معرّض للخطر" عندما يكون التقدّم الفعلي < المتوقّع × العامل (مثال 0.70 = 70%).' } })}
          </div>
        </div>

        {/* Section: Rule 9 — Evidence expiry */}
        <div style={{ marginBottom: 20 }}>
          <h4 style={{ fontSize: 13, fontWeight: 700, color: 'var(--primary)', marginBottom: 8 }}>
            {isAr ? 'القاعدة 9 — انتهاء صلاحية الأدلة' : 'Rule 9 — Evidence expiry'}
          </h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
            {Num({ en: 'Warning window (days)', ar: 'نافذة التحذير (أيام)' }, 'evidenceExpiryWarningDays',
              { min: 0, max: 365, unit: isAr ? 'يوم' : 'days',
                help: { en: 'Evidence within this window of expiry is flagged as "expiring soon".', ar: 'الأدلة التي تنتهي خلال هذه النافذة تُعلَّم "تنتهي قريباً".' } })}
            {Text({ en: 'Sweep cron', ar: 'جدولة الفحص' }, 'evidenceSweepCron',
              { placeholder: '0 0 6 * * *',
                help: { en: 'Spring cron expression for the nightly expiry sweep (6 fields, second-level).', ar: 'تعبير cron الخاص بـ Spring للفحص الليلي (6 خانات، بمستوى الثواني).' } })}
          </div>
        </div>

        {/* Section: Rule 13 — CAPA escalation */}
        <div style={{ marginBottom: 4 }}>
          <h4 style={{ fontSize: 13, fontWeight: 700, color: 'var(--primary)', marginBottom: 8 }}>
            {isAr ? 'القاعدة 13 — تصعيد الإجراءات التصحيحية' : 'Rule 13 — Corrective-action escalation'}
          </h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
            {Num({ en: 'Owner-warning window (days)', ar: 'نافذة تحذير المالك (أيام)' }, 'capaOverdueWarningDays',
              { min: 0, max: 90, unit: isAr ? 'يوم' : 'days',
                help: { en: 'Days past due before owner gets warned.', ar: 'الأيام بعد تاريخ الاستحقاق قبل تحذير المالك.' } })}
            {Num({ en: 'Executive-escalation window (days)', ar: 'نافذة تصعيد الإدارة (أيام)' }, 'capaExecutiveEscalationDays',
              { min: 0, max: 365, unit: isAr ? 'يوم' : 'days',
                help: { en: 'Days past due before action escalates to exec.', ar: 'الأيام بعد تاريخ الاستحقاق قبل التصعيد إلى الإدارة.' } })}
          </div>
        </div>
      </Card>
    </div>
  );
}

// ─── Survey tab ─────────────────────────────────────────────────────────────

function SurveyTab({ surveys, onSubmit }: {
  surveys: VendorEsgSurvey[];
  onSubmit: (id: string, patch: Partial<VendorEsgSurvey>) => void;
}) {
  const { t, lang } = useI18n();
  const [editId, setEditId] = useState<string | null>(null);
  const [draftScope12, setDraftScope12] = useState<string>('');
  const [draftRenewable, setDraftRenewable] = useState<string>('');
  const [draftSaudization, setDraftSaudization] = useState<string>('');
  const [draftIso, setDraftIso] = useState(false);
  const [draftNetZero, setDraftNetZero] = useState(false);

  function startEdit(s: VendorEsgSurvey) {
    setEditId(s.id);
    setDraftScope12(s.reportedScope12?.toString() ?? '');
    setDraftRenewable(s.renewablePct?.toString() ?? '');
    setDraftSaudization(s.saudizationPct?.toString() ?? '');
    setDraftIso(!!s.iso14001);
    setDraftNetZero(!!s.hasNetZeroTarget);
  }

  function submit(id: string) {
    onSubmit(id, {
      reportedScope12: parseFloat(draftScope12) || undefined,
      renewablePct: parseFloat(draftRenewable) || undefined,
      saudizationPct: parseFloat(draftSaudization) || undefined,
      iso14001: draftIso,
      hasNetZeroTarget: draftNetZero,
      status: 'SUBMITTED',
      updatedAt: new Date().toISOString().slice(0, 10),
    });
    setEditId(null);
  }

  const totalReportedScope12 = surveys.reduce((acc, s) => acc + (s.reportedScope12 ?? 0), 0);
  const submittedSpend = surveys
    .filter(s => s.status === 'SUBMITTED')
    .reduce((acc, s) => acc + s.spendSarK, 0);
  const totalSpend = surveys.reduce((acc, s) => acc + s.spendSarK, 0);
  const spendCoveragePct = totalSpend > 0 ? Math.round((submittedSpend / totalSpend) * 100) : 0;

  return (
    <Card title={t('esgSurveyTitle')} Icon={Truck}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('esgSurveySub')}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 12 }}>
        <Stat label={t('esgSurveyKpiSpendCov')} value={`${spendCoveragePct}%`} />
        <Stat label={t('esgSurveyKpiReportedScope12')} value={`${(totalReportedScope12 / 1000).toFixed(1)}k tCO2e`} />
        <Stat label={t('esgSurveyKpiVendors')} value={`${surveys.filter(s => s.status === 'SUBMITTED').length} / ${surveys.length}`} />
      </div>
      <table className="tbl" style={{ width: '100%' }}>
        <thead>
          <tr>
            <th>{t('esgSurveyVendor')}</th>
            <th>{t('esgSurveySpend')}</th>
            <th>{t('esgSurveyStatus')}</th>
            <th>{t('esgSurveyScope12')}</th>
            <th>{t('esgSurveyRenewable')}</th>
            <th>{t('esgSurveySaudization')}</th>
            <th>{t('esgSurveyCerts')}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {surveys.map(s => (
            <tr key={s.id}>
              <td>
                <div style={{ fontWeight: 600 }}>{lang === 'ar' ? s.vendorNameAr : s.vendorNameEn}</div>
                <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{s.id}</div>
              </td>
              <td className="mono">{s.spendSarK.toLocaleString()}k SAR</td>
              <td>
                <Badge tone={SURVEY_TONE[s.status]}>{SURVEY_LABEL[s.status][lang]}</Badge>
              </td>
              {editId === s.id ? (
                <>
                  <td>
                    <input value={draftScope12} onChange={e => setDraftScope12(e.target.value)}
                      type="number" placeholder="tCO2e" className="mono" style={{ ...inputStyle, width: 90 }} />
                  </td>
                  <td>
                    <input value={draftRenewable} onChange={e => setDraftRenewable(e.target.value)}
                      type="number" placeholder="%" className="mono" style={{ ...inputStyle, width: 60 }} />
                  </td>
                  <td>
                    <input value={draftSaudization} onChange={e => setDraftSaudization(e.target.value)}
                      type="number" placeholder="%" className="mono" style={{ ...inputStyle, width: 60 }} />
                  </td>
                  <td>
                    <label style={{ fontSize: 11, display: 'flex', alignItems: 'center', gap: 4 }}>
                      <input type="checkbox" checked={draftIso} onChange={e => setDraftIso(e.target.checked)} />
                      ISO 14001
                    </label>
                    <label style={{ fontSize: 11, display: 'flex', alignItems: 'center', gap: 4 }}>
                      <input type="checkbox" checked={draftNetZero} onChange={e => setDraftNetZero(e.target.checked)} />
                      {t('esgSurveyNetZero')}
                    </label>
                  </td>
                  <td>
                    <Button size="sm" variant="primary" onClick={() => submit(s.id)}>{t('esgSurveySave')}</Button>
                  </td>
                </>
              ) : (
                <>
                  <td className="mono">{s.reportedScope12 != null ? s.reportedScope12.toLocaleString() : '—'}</td>
                  <td className="mono">{s.renewablePct != null ? `${s.renewablePct}%` : '—'}</td>
                  <td className="mono">{s.saudizationPct != null ? `${s.saudizationPct}%` : '—'}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                      {s.iso14001 && <Badge tone="success">ISO</Badge>}
                      {s.hasNetZeroTarget && <Badge tone="info">NZ</Badge>}
                    </div>
                  </td>
                  <td>
                    <Button size="sm" variant="secondary" onClick={() => startEdit(s)}>{t('esgSurveyEdit')}</Button>
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</span>
      {children}
    </label>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={{
      padding: 10, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
      background: 'var(--surface-2)',
    }}>
      <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase' }}>{label}</div>
      <div style={{ fontSize: 16, fontWeight: 700, marginTop: 2 }}>{value}</div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '8px 10px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
};
