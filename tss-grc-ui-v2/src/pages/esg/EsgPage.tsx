import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Leaf, TrendingDown, TrendingUp, Target, Calculator, FileText, Truck,
  Download, Trash2, CheckCircle2, AlertCircle, Building2,
  CloudLightning, Plus, ArrowRight, Link2, Wrench, Clock,
  Paperclip, Coins, Hourglass, X as XIcon, Pencil, Upload, RefreshCw,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
import { ModuleEngineRequestsPanel } from '../../components/workflows/ModuleEngineRequestsPanel';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { ApiError, reportingApi } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  listClimateItems, createClimateItem, updateClimateItem, assessClimateItem, treatClimateItem,
  closeClimateItem, retireClimateItem, linkClimateItemToErm,
  riskScoreTone, riskScoreBand, shouldOfferErmLink,
  type ClimateRiskOpportunity, type CroKind,
} from '../../lib/climateRisk';
import {
  listGhgRecords, ghgAggregate, ghgYoy,
  createGhgRecord, updateGhgRecord, submitGhgRecord, verifyGhgRecord, approveGhgRecord, archiveGhgRecord,
  ghgStatusTone, canVerify,
  type GhgEmissionRecord, type GhgScope, type GhgAggregate, type GhgYoY,
} from '../../lib/ghgInventory';
import {
  listReportingYears, createReportingYear, advanceReportingYear, listReportsForYear, generateAnnex, generateReport,
  reportTypeLabel,
  submitReport, approveReport, publishReport, exportReport,
  ryStatusTone, reportStatusTone,
  type ReportingYear as Ry, type SustainabilityReport as SR,
  type ReportType, type RyStatus,
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
  listSgi, sgiAggregate, achieveSgi, retireSgi, createSgi, updateSgi, sweepSgi,
  sgiStatusTone, sgiSectorLabel, sgiStatusLabel, sgiTierTone, sgiTierLabel,
  computeProgressPct, canAchieve, computeTier, isMilestoneOverdue,
  type SgiCommitment as SgiC, type SgiAggregate as SgiAgg, type SgiSector,
} from '../../lib/sustainabilitySgi';
import {
  listCma, cmaReadiness, markDisclosed, markPartial, markNotDisclosed,
  markNotApplicable, markUnderReview, attachCmaEvidence, recordCmaGap, createCma, updateCma, approveCma,
  cmaStatusTone, cmaStatusLabel, articleLabel, hasGap, hasEvidence,
  type CmaDisclosure as CmaD, type CmaReadiness as CmaR, type CmaStatus,
} from '../../lib/sustainabilityCma';
import { getUser } from '../../lib/auth';
import {
  listTcfd, tcfdReadiness, createTcfd, updateTcfd,
  tcfdMarkDisclosed, tcfdMarkPartial, tcfdMarkNotDisclosed,
  tcfdMarkNotApplicable, tcfdMarkUnderReview,
  tcfdAttachEvidence, tcfdRecordGap, tcfdLinkClimateRisk,
  tcfdStatusTone, tcfdStatusLabel, tcfdPillarLabel,
  hasTcfdGap, hasTcfdEvidence,
  type TcfdItem as TcfdI, type TcfdReadiness as TcfdR, type TcfdPillar, type TcfdStatus,
} from '../../lib/sustainabilityTcfd';
import {
  listMateriality, generateMatrix, scoreMaterialTopic, approveMaterialTopic,
  archiveMaterialTopic, createMaterialTopic, updateMaterialTopic,
  materialTierTone, materialTierLabel, materialCategoryLabel,
  materialStatusTone, materialStatusLabel,
  type MaterialTopic, type MaterialMatrix, type MaterialCategory,
} from '../../lib/sustainabilityMateriality';
import {
  listMetrics, metricsAggregate, recordMetricReading, archiveMetric, createMetric, updateMetric,
  computeMetricProgress, metricStatusTone, metricStatusLabel,
  metricCategoryLabel as esgMetricCategoryLabel,
  type EsgMetric as EsgM, type MetricAggregate, type MetricCategory,
} from '../../lib/sustainabilityMetrics';
import {
  listEvidenceItems, createEvidenceItem, uploadEvidenceItemFile,
  submitEvidenceItem, approveEvidenceItem, rejectEvidenceItem, deleteEvidenceItem,
  evidenceItemStatusTone, evidenceItemStatusLabel, canSubmitEvidence,
  type EvidenceItem, type EvidenceItemStatus,
} from '../../lib/evidenceItems';
import {
  linkSustEvidence, SUST_EVIDENCE_ENTITY_TYPES, type SustEvidenceEntityType,
} from '../../lib/sustEvidenceLinks';
import {
  getDashboard, esgBandTone, esgBandLabel, esgBandColor, topGapLabel,
  type DashboardPayload,
} from '../../lib/sustainabilityDashboard';
import {
  getSettings, updateSettings, resetSettings,
  type OrganisationSettings as SustSettings,
} from '../../lib/sustainabilitySettings';
import {
  EMISSION_FACTORS,
  loadCalcEntries, appendCalcEntry, deleteCalcEntry,
  subscribeEsg, buildEsgAnnex, buildEsgAnnexSheets,
  type CalculatorEntry, type EsgStats,
} from '../../lib/esg';
// S-O — Vendor ESG surveillance reads the real backend (was VENDOR_SURVEYS mock).
import {
  vendorEsgApi, SURVEY_TONE, SURVEY_LABEL,
  type VendorEsgSurvey,
} from '../../lib/vendorEsg';
// S-P — Overview KPIs + trend chart read the live dashboard/trajectory API
// (was the aggregateEsg / EMISSIONS_TIMESERIES mock).
import {
  esgDashboardApi, mapDashboardToStats, EMPTY_ESG_STATS,
  esgTrajectoryApi, mapTrajectory, isEsgTab,
  type EsgTab, type EsgTrajectoryPoint,
} from '../../lib/esgDashboard';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';
import { printPacket, packetTextToSections } from '../../lib/packetPrint';
import { formatSarCompact, formatSarFull } from '../../lib/format';
// Green-finance plan §5.4–§5.7 — regulatory-horizon tabs (targets / IFRS / env+CBAM).
import { EsgTargetsTab, IfrsTab, EnvComplianceTab, AssuranceTab } from './EsgHorizonTabs';
import '../../styles/pages/esg.css';

// EsgTab + isEsgTab are imported from lib/esgDashboard (single source of truth for the tab ids).

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
  const { t, lang } = useI18n();
  const toast = useToast();
  // S-P — deep-linkable tabs: initial tab from ?tab=, written back on change.
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get('tab');
  const [tab, setTabState] = useState<EsgTab>(isEsgTab(initialTab) ? initialTab : 'overview');
  const setTab = (next: EsgTab) => {
    setTabState(next);
    const sp = new URLSearchParams(searchParams);
    sp.set('tab', next);
    setSearchParams(sp, { replace: true });
  };

  // Reactive state
  // S-P — Overview KPIs from the live dashboard aggregate (kills the aggregateEsg mock).
  const REPORT_YEAR = 2025;
  const [stats, setStats] = useState<EsgStats>(EMPTY_ESG_STATS);
  useEffect(() => {
    esgDashboardApi.get(REPORT_YEAR)
      .then(d => setStats(mapDashboardToStats(d)))
      .catch(() => { /* keep the zeroed placeholder on failure — no mock fallback */ });
  }, []);
  const [calcEntries, setCalcEntries] = useState<CalculatorEntry[]>(() => loadCalcEntries());
  useEffect(() => subscribeEsg(() => {
    setCalcEntries(loadCalcEntries());
  }), []);

  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);

  function downloadAnnex() {
    const text = buildEsgAnnex(brand.organisationNameEn, brand.organisationNameAr);
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ESG-Annex-${REPORT_YEAR}.txt`;
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.success(t('esgAnnexDownloadedToast'));
  }

  function printEsgAnnexPdf() {
    const text = buildEsgAnnex(brand.organisationNameEn, brand.organisationNameAr);
    printPacket({
      titleEn: 'ESG / Sustainability Annex — Annual Report',
      titleAr: 'ملحق الاستدامة والحوكمة البيئية — التقرير السنوي',
      eyebrowEn: 'CMA Art. 91–94 · SGI · TCFD',
      eyebrowAr: 'المواد 91–94 · مبادرة السعودية الخضراء · TCFD',
      referenceId: `ESG-Annex-${REPORT_YEAR}`,
      classificationEn: 'CONFIDENTIAL — SUSTAINABILITY',
      classificationAr: 'سرّي — الاستدامة',
      sections: packetTextToSections(text),
    }, lang);
  }

  async function exportEsgAnnex(kind: 'xlsx' | 'docx') {
    const payload = { module: 'ESG', exportType: 'SustainabilityAnnex', lang, sheets: buildEsgAnnexSheets() };
    try {
      await (kind === 'xlsx' ? reportingApi.packetXlsx(payload) : reportingApi.packetDocx(payload));
      toast.success(t('esgAnnexDownloadedToast'));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (lang === 'ar' ? 'تعذّر التصدير' : 'Export failed'));
    }
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
        actions={<>
          <RaiseRequestButton sourceModule="ESG" />
          <Button variant="primary" Icon={Download} onClick={printEsgAnnexPdf}>{lang === 'ar' ? 'تنزيل PDF' : 'Download PDF'}</Button>
          <Button variant="secondary" Icon={Download} onClick={() => exportEsgAnnex('xlsx')}>{lang === 'ar' ? 'تصدير Excel' : 'Export Excel'}</Button>
          <Button variant="secondary" Icon={Download} onClick={() => exportEsgAnnex('docx')}>{lang === 'ar' ? 'تصدير Word' : 'Export Word'}</Button>
          <Button variant="ghost" Icon={Download} onClick={downloadAnnex}>{t('esgDownloadAnnex')}</Button>
        </>}
      />

      <div style={{ marginBottom: 16 }}><ModuleEngineRequestsPanel sourceModule="ESG" /></div>

      <div className="kpi-grid esg-kpi-strip">
        <Kpi Icon={Leaf}          label={t('esgKpiTotal')}     value={`${(stats.totalEmissions / 1000).toFixed(1)}k`} tone="primary" onClick={() => setTab('ghg')} active={tab === 'ghg'} />
        <Kpi Icon={TrendingDown}   label={t('esgKpiYoY')}        value={`${stats.yoyTotal.toFixed(1)}%`} tone={stats.yoyTotal < 0 ? 'success' : 'danger'} onClick={() => setTab('ghg')} active={tab === 'ghg'} />
        <Kpi Icon={Leaf}            label={t('esgKpiScope1')}     value={`${(stats.scope1 / 1000).toFixed(1)}k`} tone="success" onClick={() => setTab('ghg')} />
        <Kpi Icon={Leaf}            label={t('esgKpiScope2')}     value={`${(stats.scope2 / 1000).toFixed(1)}k`} tone="success" onClick={() => setTab('ghg')} />
        <Kpi Icon={Leaf}            label={t('esgKpiScope3')}     value={`${(stats.scope3 / 1000).toFixed(1)}k`} tone="warning" onClick={() => setTab('ghg')} />
        <Kpi Icon={FileText}        label={t('esgKpiCma')}        value={`${stats.cmaCoveragePct}%`} tone={stats.cmaCoveragePct >= 80 ? 'success' : 'warning'} onClick={() => setTab('cma')} active={tab === 'cma'} />
        <Kpi Icon={FileText}        label={t('esgKpiTcfd')}       value={`${stats.tcfdCoveragePct}%`} tone={stats.tcfdCoveragePct >= 80 ? 'success' : 'warning'} onClick={() => setTab('tcfd')} active={tab === 'tcfd'} />
        <Kpi Icon={Target}          label={t('esgKpiSgi')}        value={`${stats.sgiOnTrackCount} / ${stats.sgiTotal}`} tone={stats.sgiOnTrackCount === stats.sgiTotal ? 'success' : 'warning'} onClick={() => setTab('sgi')} active={tab === 'sgi'} />
      </div>

      {/* Tab strip */}
      <div className="esg-tab-strip">
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
          { id: 'targets',    labelKey: 'esgTabTargets',   Icon: Target },
          { id: 'ifrs',       labelKey: 'esgTabIfrs',      Icon: FileText },
          { id: 'env',        labelKey: 'esgTabEnv',       Icon: Wrench },
          { id: 'assurance',  labelKey: 'esgTabAssurance', Icon: Paperclip },
          { id: 'settings',   labelKey: 'esgTabSettings',  Icon: Wrench },
        ] as { id: EsgTab; labelKey: string; Icon: typeof Leaf }[]).map(tabDef => (
          <button key={tabDef.id} onClick={() => setTab(tabDef.id)}
            className={`esg-tab-btn${tab === tabDef.id ? ' is-active' : ''}`}>
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
      {tab === 'targets'    && <EsgTargetsTab />}
      {tab === 'ifrs'       && <IfrsTab />}
      {tab === 'env'        && <EnvComplianceTab />}
      {tab === 'assurance'  && <AssuranceTab />}
      {tab === 'survey'     && <SurveyTab />}
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
  const [editItem, setEditItem] = useState<ClimateRiskOpportunity | null>(null);
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
  // Edit (PUT) — partial null-means-keep on the server. kind stays fixed
  // (the category whitelist is kind-specific) and is NOT shown in the form,
  // but it MUST be echoed in the payload: the update endpoint binds a JPA
  // @RequestBody entity whose kind defaults to "RISK", so an omitted kind
  // arrives non-null and would clobber an OPPORTUNITY to RISK — failing the
  // category validation. Echoing editItem.kind makes the overwrite a no-op.
  // reportingYear is immutable via update. The server recomputes
  // riskScore = likelihood × impact; status is owned by the transition
  // endpoints and never touched here.
  async function onUpdate(values: Record<string, unknown>) {
    if (!editItem) return;
    try {
      await updateClimateItem(editItem.id!, {
        kind:          editItem.kind,
        category:      String(values.category ?? editItem.category),
        timeHorizon:   (values.timeHorizon as 'SHORT' | 'MEDIUM' | 'LONG') ?? editItem.timeHorizon,
        titleEn:       String(values.titleEn ?? '').trim(),
        titleAr:       String(values.titleAr ?? '').trim() || undefined,
        descriptionEn: String(values.descriptionEn ?? '').trim() || undefined,
        likelihood:    Number(values.likelihood ?? editItem.likelihood),
        impact:        Number(values.impact ?? editItem.impact),
        ownerName:     String(values.ownerName ?? '').trim() || undefined,
      });
      toast.success(t('cliUpdated'));
      setEditItem(null);
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

  // Category whitelist is kind-specific on the server; scope the edit
  // options to the item's kind so an edit can never produce an invalid
  // kind/category pair. Kind itself is omitted from the edit surface.
  const RISK_CAT_OPTS = [
    { value: 'PHYSICAL',     label: isAr ? 'مادي'          : 'Physical' },
    { value: 'TRANSITION',   label: isAr ? 'انتقالي'        : 'Transition' },
    { value: 'REGULATORY',   label: isAr ? 'تنظيمي'         : 'Regulatory' },
    { value: 'MARKET',       label: isAr ? 'سوق'           : 'Market' },
    { value: 'TECHNOLOGY',   label: isAr ? 'تقني'          : 'Technology' },
    { value: 'REPUTATION',   label: isAr ? 'سُمعة'          : 'Reputation' },
    { value: 'SUPPLY_CHAIN', label: isAr ? 'سلسلة الإمداد' : 'Supply chain' },
  ];
  const OPP_CAT_OPTS = [
    { value: 'RESOURCE_EFFICIENCY', label: isAr ? 'كفاءة الموارد' : 'Resource efficiency' },
    { value: 'ENERGY_SOURCE',       label: isAr ? 'مصدر طاقة'      : 'Energy source' },
    { value: 'PRODUCT_SERVICE',     label: isAr ? 'منتج/خدمة'     : 'Product/service' },
    { value: 'MARKETS',             label: isAr ? 'أسواق'         : 'Markets' },
    { value: 'RESILIENCE',          label: isAr ? 'صمود'          : 'Resilience' },
  ];
  const editCatOpts = editItem?.kind === 'OPPORTUNITY' ? OPP_CAT_OPTS : RISK_CAT_OPTS;
  const EDIT_FIELDS: FieldDef[] = [
    { kind: 'select',   key: 'category',      label: t('cliFldCategory') + ' *', required: true, span: 6, options: editCatOpts },
    { kind: 'select',   key: 'timeHorizon',   label: t('cliFldHorizon') + ' *',  required: true, span: 6,
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
    { kind: 'text',     key: 'ownerName',     label: t('cliFldOwner'),             span: 6 },
  ];
  const editInitial = useMemo(() => editItem ? {
    category:      editItem.category,
    timeHorizon:   editItem.timeHorizon,
    titleEn:       editItem.titleEn,
    titleAr:       editItem.titleAr ?? '',
    descriptionEn: editItem.descriptionEn ?? '',
    likelihood:    editItem.likelihood,
    impact:        editItem.impact,
    ownerName:     editItem.ownerName ?? '',
  } : {}, [editItem]);

  return (
    <div className="esg-stack">
      {/* KPI strip — risks vs opportunities + Rule-8 alert chip */}
      <div className="kpi-grid">
        <Kpi Icon={CloudLightning} label={t('cliKpiRisks')}    value={risks.length}         tone="warning" to="/esg" />
        <Kpi Icon={Target}          label={t('cliKpiOpps')}      value={opportunities.length} tone="success" to="/esg" />
        <Kpi Icon={AlertCircle}     label={t('cliKpiHighScore')} value={risks.filter(r => (r.riskScore ?? 0) >= 15).length} tone="danger" to="/risk" />
        <Kpi Icon={Link2}           label={t('cliKpiNeedsErm')}  value={needsErmLink.length}  tone={needsErmLink.length > 0 ? 'danger' : 'success'} to="/risk" />
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
                    <th className="esg-th-center">{t('cliColScore')}</th>
                    <th>{t('cliColStatus')}</th>
                    <th className="esg-text-end">{t('cliColActions')}</th>
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
                          <div className="esg-cell-title">
                            {isAr && it.titleAr ? it.titleAr : it.titleEn}
                          </div>
                          {it.ownerName && <div className="text-2xs text-muted esg-cell-sub">{it.ownerName}</div>}
                        </td>
                        <td><Badge tone={it.kind === 'RISK' ? 'danger' : 'success'}>{isAr ? (it.kind === 'RISK' ? 'مخاطرة' : 'فرصة') : it.kind}</Badge></td>
                        <td className="text-xs">{it.category}</td>
                        <td><Badge tone="neutral">{it.timeHorizon}</Badge></td>
                        <td className="esg-text-center">
                          <Badge tone={tone}>{it.riskScore} · {band}</Badge>
                        </td>
                        <td><Badge tone={it.status === 'CLOSED' ? 'success' : it.status === 'RETIRED' ? 'neutral' : it.status === 'TREATED' ? 'info' : 'warning'}>{it.status}</Badge></td>
                        <td className="esg-text-end">
                          <div className="esg-actions-row">
                            {/* Rule 8: offer ERM link inline when score >= 15 and not yet linked */}
                            {shouldOfferErmLink(it) && (
                              <Button variant="primary" size="sm" Icon={Link2}
                                onClick={() => setLinkingErmFor(it.id!)}>{t('cliActLinkErm')}</Button>
                            )}
                            {it.status !== 'RETIRED' && (
                              <Button variant="ghost" size="sm" Icon={Pencil}
                                title={t('cliActEdit')} onClick={() => setEditItem(it)} />
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

      <FormModal
        open={!!editItem}
        title={editItem ? `${t('cliEditTitle')} — ${editItem.itemCode ?? `#${editItem.id}`}` : ''}
        fields={EDIT_FIELDS}
        initial={editInitial}
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditItem(null)}
        onSubmit={onUpdate}
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
    // Per-call .catch() — render whatever loaded; one endpoint failing
    // shouldn't blank the whole CAPA tab.
    const [pg, a] = await Promise.all([
      listCapas({ size: 200 }).catch(() => ({ content: [] as CorrectiveAction[], totalElements: 0 })),
      capaAggregate().catch(() => null as CapaAggregate | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
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
    <div className="esg-stack">
      {/* KPI strip — live status counts + overdue alarm */}
      <div className="kpi-grid">
        <Kpi Icon={Wrench}        label={isAr ? 'مفتوحة' : 'Open'}          value={agg?.open ?? 0}        tone="warning" onClick={() => setStatusFilter('OPEN')} active={statusFilter === 'OPEN'} />
        <Kpi Icon={ArrowRight}    label={isAr ? 'قيد التنفيذ' : 'In progress'} value={agg?.inProgress ?? 0} tone="info" onClick={() => setStatusFilter('IN_PROGRESS')} active={statusFilter === 'IN_PROGRESS'} />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'بانتظار التحقّق' : 'Awaiting verification'} value={agg?.completed ?? 0} tone="primary" onClick={() => setStatusFilter('COMPLETED')} active={statusFilter === 'COMPLETED'} />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'مُتحقَّق منها' : 'Verified'} value={agg?.verified ?? 0}   tone="success" onClick={() => setStatusFilter('VERIFIED')} active={statusFilter === 'VERIFIED'} />
        <Kpi Icon={AlertCircle}   label={isAr ? 'متأخّرة' : 'Overdue'}        value={agg?.overdue ?? 0}     tone={(agg?.overdue ?? 0) > 0 ? 'danger' : 'success'} to="/esg" />
        <Kpi Icon={Clock}         label={isAr ? 'مستحقّة قريباً' : 'Due soon'} value={agg?.dueSoon ?? 0}     tone={(agg?.dueSoon ?? 0) > 0 ? 'warning' : 'success'} to="/esg" />
      </div>

      <Card title={isAr ? 'سجلّ الإجراءات التصحيحية للاستدامة' : 'Sustainability Corrective Actions'}
            Icon={Wrench}>
        {/* Status filter chips */}
        <div className="esg-filter-row">
          {(['ALL', 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'VERIFIED', 'CLOSED', 'CANCELLED'] as const).map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              className={`esg-filter-btn${statusFilter === s ? ' is-active' : ''}`}>
              {s === 'ALL' ? (isAr ? 'الكل' : 'All') : capaStatusLabel(s as CapaStatus, isAr ? 'ar' : 'en')}
            </button>
          ))}
          <span className="text-2xs text-muted esg-filter-row__count">
            {filtered.length} / {items.length}
          </span>
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? (
              <InlineEmpty>
                <div className="esg-card-center">
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
                    <th className="esg-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
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
                          <div className="esg-cell-title">
                            {isAr && a.titleAr ? a.titleAr : a.titleEn}
                          </div>
                          {a.evidenceRef && (
                            <div className="text-2xs text-muted esg-evidence-link">
                              <Paperclip size={11} className="esg-flex-shrink-0" />
                              <span>{isAr ? 'الدليل:' : 'Evidence:'}</span>
                              <span className="mono">{a.evidenceRef}</span>
                            </div>
                          )}
                        </td>
                        <td>
                          <Badge tone="neutral">{capaSourceLabel(a.sourceType, isAr ? 'ar' : 'en')}</Badge>
                          {a.sourceRef && (
                            <div className="mono text-2xs text-muted esg-cell-sub">{a.sourceRef}</div>
                          )}
                        </td>
                        <td><Badge tone={capaPriorityTone(a.priority)}>{capaPriorityLabel(a.priority, isAr ? 'ar' : 'en')}</Badge></td>
                        <td><Badge tone={capaStatusTone(a.status)}>{capaStatusLabel(a.status, isAr ? 'ar' : 'en')}</Badge></td>
                        <td className="mono text-2xs">
                          <div className={overdue ? 'esg-date-overdue' : dueSoon ? 'esg-date-due-soon' : ''}>
                            {a.targetDate ?? '—'}
                          </div>
                          {dr !== null && a.status !== 'CLOSED' && a.status !== 'CANCELLED' && (
                            <div className={overdue ? 'esg-date-sub-overdue' : dueSoon ? 'esg-date-sub-due-soon' : 'esg-date-sub-default'}>
                              {overdue
                                ? (isAr ? `متأخّر ${Math.abs(dr)} يوماً` : `${Math.abs(dr)}d overdue`)
                                : (isAr ? `${dr} يوماً متبقّياً` : `${dr}d left`)}
                            </div>
                          )}
                        </td>
                        <td className="text-2xs">
                          {showCost && (
                            <div className="esg-cost-chip"
                                 title={formatSarFull(a.costEstimateSar!, isAr ? 'ar' : 'en')}>
                              <Coins size={11} className="esg-flex-shrink-0" />
                              <span className="mono">{formatSarCompact(a.costEstimateSar!, isAr ? 'ar' : 'en')}</span>
                            </div>
                          )}
                          {showEffort && (
                            <div className={`esg-time-chip${showCost ? ' esg-time-chip--offset' : ''}`}>
                              <Hourglass size={11} className="esg-flex-shrink-0" />
                              <span className="mono">{a.effortHours}{isAr ? ' س' : 'h'}</span>
                            </div>
                          )}
                          {!showCost && !showEffort && <span className="text-muted">—</span>}
                        </td>
                        <td className="text-xs">{a.ownerName ?? '—'}</td>
                        <td className="esg-text-end">
                          <div className="esg-actions-row">
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
  const isAr = lang === 'ar';
  const [data, setData] = useState<DashboardPayload | null>(null);
  // Slice 14-C — settings drive Rule 14 banner thresholds. Loaded in
  // parallel with the dashboard; soft-fail (null) if the call errors,
  // banner just won't render in that case.
  const [settings, setSettings] = useState<SustSettings | null>(null);
  const [loading, setLoading] = useState(true);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — settings already soft-fail; do the same for the
    // dashboard payload so the page shell still renders.
    const [dash, s] = await Promise.all([
      getDashboard(2025).catch(() => null),
      getSettings().catch(() => null),  // banner is non-critical
    ]);
    setData(dash);
    setSettings(s as SustSettings | null);
    setLoading(false);
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
    <div className="esg-stack">
      {/* Rule 14 — Low ESG-readiness banner */}
      {bannerTone && (
        <div className={`esg-hero-banner ${bannerTone === 'danger' ? 'esg-hero-banner--danger' : 'esg-hero-banner--warning'}`}>
          <AlertCircle size={20} />
          <div className="esg-hero-banner__body">
            <div className="esg-hero-banner__title">
              {bannerTone === 'danger'
                ? (isAr ? 'تحذير حرج — جاهزية الاستدامة منخفضة جداً' : 'Critical — ESG readiness severely below threshold')
                : (isAr ? 'تحذير — جاهزية الاستدامة تحت العتبة' : 'Warning — ESG readiness below threshold')}
            </div>
            <div className="esg-hero-banner__sub">
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
      <div className="esg-grid-2col">
        <EsgOverallScoreWidget data={data} isAr={isAr} />
        <TopGapsWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 2: GHG cluster ───────────────────────────────────── */}
      <div className="esg-grid-auto-fit">
        <GhgTotalWidget data={data} isAr={isAr} />
        <ScopeBreakdownWidget data={data} isAr={isAr} />
        <VerificationStatusWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 3: Regulatory readiness ──────────────────────────── */}
      <div className="esg-grid-auto-fit">
        <SgiProgressWidget data={data} isAr={isAr} />
        <CmaReadinessWidget data={data} isAr={isAr} />
        <TcfdAlignmentWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 4: Operational tracking ──────────────────────────── */}
      <div className="esg-grid-auto-fit">
        <ClimateRiskWidget data={data} isAr={isAr} />
        <CapaTrackerWidget data={data} isAr={isAr} />
        <EvidenceCompletenessWidget data={data} isAr={isAr} />
      </div>

      {/* ── Row 5: Annex readiness + Materiality matrix ──────────── */}
      <div className="esg-grid-2col">
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
      <div className="esg-score-hero">
        <svg width={160} height={160} viewBox="0 0 160 160">
          <circle cx={80} cy={80} r={radius} stroke="var(--surface-2)" strokeWidth={stroke} fill="none" />
          <circle cx={80} cy={80} r={radius} stroke={color} strokeWidth={stroke} fill="none"
                  strokeDasharray={`${dash} ${circ}`} strokeDashoffset={0}
                  strokeLinecap="round" transform="rotate(-90 80 80)" />
          <text x={80} y={78} textAnchor="middle" fontSize="32" fontWeight="800" fill="var(--ink-1)">{score}</text>
          <text x={80} y={100} textAnchor="middle" fontSize="11" fill="var(--ink-3)">/ 100</text>
        </svg>
        <div className="esg-score-hero__main">
          <Badge tone={esgBandTone(band)}>{esgBandLabel(band, isAr ? 'ar' : 'en')}</Badge>
          <div className="esg-score-hero__hint">
            {isAr
              ? 'مؤشّر مركّب موزون (التحقّق من الانبعاثات + التزامات SGI + إفصاحات CMA + TCFD + الإجراءات التصحيحية + الأدلة + المخاطر المناخية).'
              : 'Weighted composite (GHG verification + SGI on-track + CMA + TCFD + corrective actions + evidence + climate risks).'}
          </div>
          <details className="esg-score-hero__details">
            <summary>{isAr ? 'تفاصيل الحساب' : 'Score breakdown'}</summary>
            <table className="esg-score-hero__table">
              <tbody>
                {Object.entries(data.overall.breakdown).map(([k, v]) => (
                  <tr key={k}>
                    <td>{k}</td>
                    <td className="mono esg-text-end">{Number(v).toFixed(1)}%</td>
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
        <div className="esg-status-banner-success">
          <CheckCircle2 size={14} className="esg-icon-inline" />
          {isAr ? 'لا توجد ثغرات تنفيذية ظاهرة في الدورة الحالية.' : 'No executive-level gaps detected for the current cycle.'}
        </div>
      ) : (
        <ul className="esg-gaps-list">
          {data.topGaps.map(g => (
            <li key={g}>
              <span className={g.includes('OVERDUE') || g.includes('SCOPE_3') || g.includes('RESILIENCE') ? 'esg-gaps-bullet--danger' : 'esg-gaps-bullet--warning'}>●</span>
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
      <div className="esg-score-hero__big">
        {total.toLocaleString()}
      </div>
      <div className="esg-score-hero__unit">tCO2e — {data.ghg.year}</div>
      <div className="esg-score-hero__yoy">
        <span className={yoy < 0 ? 'esg-score-hero__yoy-positive' : yoy > 0 ? 'esg-score-hero__yoy-negative' : 'esg-score-hero__yoy-flat'}>
          {yoy < 0 ? <TrendingDown size={14} className="esg-icon-trailing" /> : <TrendingUp size={14} className="esg-icon-trailing" />}
          {' '}{yoy > 0 ? '+' : ''}{yoy.toFixed(1)}% {isAr ? 'مقارنة بـ' : 'vs'} {data.ghgYoy.year - 1}
        </span>
      </div>
      {data.ghg.scope3PartialFlag && (
        <div className="esg-score-hero__alert">
          <AlertCircle size={11} className="esg-icon-inline" />
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
      <div className="stack-sm">
        {rows.map(r => {
          const pct = total > 0 ? (r.value / total) * 100 : 0;
          return (
            <div key={r.label}>
              <div className="esg-progress-row">
                <span className="esg-text-strong">{r.label}</span>
                <span className="mono">{r.value.toLocaleString()} ({pct.toFixed(0)}%)</span>
              </div>
              <div className="esg-progress-track">
                <div className="esg-progress-fill" style={{ width: `${pct}%`, background: r.color }} />
              </div>
              <div className={`text-2xs ${r.yoy < 0 ? 'esg-score-hero__yoy-positive' : 'esg-score-hero__yoy-negative'}`} style={{ marginTop: 2 }}>
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
      <div className="esg-score-hero">
        <div className="esg-verify-ring">
          <svg viewBox="0 0 100 100" className="esg-verify-ring__svg">
            <circle cx={50} cy={50} r={40} stroke="var(--surface-2)" strokeWidth={12} fill="none" />
            <circle cx={50} cy={50} r={40} stroke="var(--success)" strokeWidth={12} fill="none"
                    strokeDasharray={`${(verifRate / 100) * 2 * Math.PI * 40} ${2 * Math.PI * 40}`}
                    strokeLinecap="round" />
          </svg>
          <div className="esg-verify-ring__center">
            <div className="esg-verify-ring__value">{verifRate.toFixed(0)}%</div>
            <div className="esg-verify-ring__label">{isAr ? 'مُتحقَّق' : 'verified'}</div>
          </div>
        </div>
        <div className="esg-score-hero__main esg-score-hero__hint">
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
      <div className="esg-widget-headline esg-widget-headline--mb">
        {onTrack}/{total} <span className="esg-widget-sub-inline">{isAr ? 'على المسار' : 'on track'}</span>
      </div>
      <div className="esg-tile-grid-3">
        <div className="esg-tile esg-tile--success">
          <div className="esg-tile__value-lg esg-tile__value-success">{onTrack}</div>
          <div className="esg-tile__label">{isAr ? 'على المسار' : 'On track'}</div>
        </div>
        <div className="esg-tile esg-tile--warning">
          <div className="esg-tile__value-lg esg-tile__value-warning">{atRisk}</div>
          <div className="esg-tile__label">{isAr ? 'مُعرَّض' : 'At risk'}</div>
        </div>
        <div className="esg-tile esg-tile--danger">
          <div className="esg-tile__value-lg esg-tile__value-danger">{delayed}</div>
          <div className="esg-tile__label">{isAr ? 'متأخّر' : 'Delayed'}</div>
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
      <div className="esg-widget-headline">{pct}%</div>
      <div className="esg-widget-sub">
        {disclosed}/{data.cma.applicable} {isAr ? 'مُفصَح' : 'disclosed'}
      </div>
      <div className="esg-bar-h8">
        <div className="esg-bar-fill-h8" style={{
          width: `${pct}%`,
          background: pct >= 80 ? 'var(--success)' : pct >= 50 ? 'var(--warning)' : 'var(--danger)',
        }} />
      </div>
      <div className="row-tight">
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
      <div className="esg-widget-headline">{pct}%</div>
      <div className="esg-widget-sub">
        {data.tcfd.byStatus.DISCLOSED}/{data.tcfd.applicable} {isAr ? 'مُفصَح' : 'disclosed'}
      </div>
      <div className="esg-tile-grid-4" style={{ marginBottom: 8 }}>
        {(['GOVERNANCE', 'STRATEGY', 'RISK_MGMT', 'METRICS_TARGETS'] as const).map(p => {
          const total = data.tcfd.byPillar[p] ?? 0;
          const ok = data.tcfd.disclosedByPillar[p] ?? 0;
          return (
            <div key={p} className="esg-tile esg-tile--surface">
              <div className="esg-tile__value-md">{ok}/{total}</div>
              <div className="esg-tile__label-tiny">
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
        <div className="esg-text-footnote esg-date-due-soon">
          <AlertCircle size={10} className="esg-icon-inline" />
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
      <div className="esg-widget-headline">{data.capa.activeTotal}</div>
      <div className="esg-widget-sub">{isAr ? 'نشطة' : 'active'}</div>
      <div className="esg-tile-grid-4">
        <div className="esg-tile esg-tile--warning">
          <div className="esg-tile__value-md">{data.capa.open}</div>
          <div className="esg-tile__label">{isAr ? 'مفتوحة' : 'Open'}</div>
        </div>
        <div className="esg-tile esg-tile--info">
          <div className="esg-tile__value-md">{data.capa.inProgress}</div>
          <div className="esg-tile__label">{isAr ? 'تنفيذ' : 'WIP'}</div>
        </div>
        <div className="esg-tile esg-tile--success">
          <div className="esg-tile__value-md">{data.capa.verified}</div>
          <div className="esg-tile__label">{isAr ? 'مُتحقَّق' : 'Verified'}</div>
        </div>
        <div className={`esg-tile ${data.capa.overdue > 0 ? 'esg-tile--danger' : 'esg-tile--surface'}`}>
          <div className={`esg-tile__value-md${data.capa.overdue > 0 ? ' esg-tile__value-danger' : ''}`}>{data.capa.overdue}</div>
          <div className="esg-tile__label">{isAr ? 'متأخّرة' : 'Overdue'}</div>
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
      <div className="esg-widget-headline">{pct}%</div>
      <div className="esg-widget-sub">
        {approved}/{total} {isAr ? 'معتمدة' : 'approved'}
      </div>
      <div className="esg-bar-h8">
        <div className="esg-bar-fill-h8" style={{
          width: `${pct}%`,
          background: pct >= 80 ? 'var(--success)' : pct >= 50 ? 'var(--info)' : 'var(--warning)',
        }} />
      </div>
      {data.evidence.expiringSoon30d > 0 && (
        <div className="esg-score-hero__alert">
          <Clock size={11} className="esg-icon-inline" />
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
      <div className="esg-gauge-wrap">
        <svg viewBox="0 0 200 110" className="esg-gauge-svg">
          {/* Semi-circle gauge */}
          <path d="M 20 100 A 80 80 0 0 1 180 100" stroke="var(--surface-2)" strokeWidth={14} fill="none" />
          <path d={`M 20 100 A 80 80 0 0 1 ${20 + (160 * composite / 100)} ${100 - Math.sin(Math.PI * composite / 100) * 80}`}
                stroke={composite >= 80 ? 'var(--success)' : composite >= 50 ? 'var(--warning)' : 'var(--danger)'}
                strokeWidth={14} fill="none" strokeLinecap="round" />
          <text x={100} y={88} textAnchor="middle" fontSize="28" fontWeight="800" fill="var(--ink-1)">{composite}%</text>
        </svg>
        <div className="esg-gauge-sub">
          {isAr ? 'المتوسّط الموزون لـ CMA + TCFD + الأدلة' : 'Weighted mean of CMA + TCFD + Evidence'}
        </div>
      </div>
    </Card>
  );
}

function MaterialityMatrixWidget({ data, isAr }: { data: DashboardPayload; isAr: boolean }) {
  const matrix = data.materiality.matrix;
  return (
    <Card title={isAr ? 'الأهمية النسبية — أهم الموضوعات' : 'Materiality — top topics'} Icon={Target}>
      <div style={{ display: 'flex', gap: 8, marginBottom: 8, fontSize: 11 }}>
        <Badge tone="danger">{data.materiality.criticalCount} {isAr ? 'حرج' : 'Critical'}</Badge>
        <Badge tone="warning">{data.materiality.highCount} {isAr ? 'مرتفع' : 'High'}</Badge>
      </div>
      {matrix.slice(0, 4).map(t => (
        <div key={t.id} style={{
          padding: 8, marginBottom: 6, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
          display: 'grid', gridTemplateColumns: '1fr auto', gap: 8,
        }}>
          <div className="esg-text-strong">
            {isAr && t.topicAr ? t.topicAr : t.topicEn}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11 }}>
            <span className="mono esg-text-bold">{Number(t.materialityScore).toFixed(1)}</span>
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
          {isAr ? 'لا توجد موضوعات ذات أهمية نسبية مُقيَّمة بعد' : 'No materiality topics scored yet'}
        </div>
      )}
    </Card>
  );
}

function EmissionsTrendChart({ isAr }: { isAr: boolean }) {
  // S-P — live multi-year trajectory from /ghg/trajectory (was the
  // EMISSIONS_TIMESERIES mock). Only years with real records are plotted.
  const [series, setSeries] = useState<EsgTrajectoryPoint[]>([]);
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    esgTrajectoryApi.get(2020, 2025)
      .then(r => setSeries(mapTrajectory(r).filter(p => p.scope1 + p.scope2 + p.scope3 > 0)))
      .catch(() => setSeries([]))
      .finally(() => setLoaded(true));
  }, []);
  if (loaded && series.length === 0) {
    return (
      <div className="esg-text-eyebrow-sm">
        {isAr ? 'لا توجد بيانات انبعاثات مسجّلة بعد.' : 'No emissions data recorded yet.'}
      </div>
    );
  }
  const maxTotal = Math.max(1, ...series.map(r => r.scope1 + r.scope2 + r.scope3));
  return (
    <>
      <div className="esg-text-eyebrow-sm">
        {isAr ? 'الانبعاثات الإجمالية مكدّسة بالنطاقات عبر السنوات.' : 'Total emissions stacked by scope across years.'}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${Math.max(1, series.length)}, 1fr)`, gap: 12, alignItems: 'end', height: 220 }}>
        {series.map(r => {
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
        <div className="esg-text-eyebrow-sm">
          {t('esgCalcSub')}
        </div>
        <div className="esg-calc-form">
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
        <div className="esg-calc-preview">
          <div className="esg-calc-preview__meta">
            <b>{factor.kgCo2ePerUnit}</b> kgCO2e / {factor.unit} · {t('esgCalcSource')}: {factor.source}
          </div>
          <div className="esg-text-strong">
            {t('esgCalcPreview')}: <span className="mono">{computed.toFixed(2)}</span> kgCO2e
            ({(computed / 1000).toFixed(3)} tCO2e)
          </div>
        </div>
      </Card>

      <div className="mt-lg">
        <Card title={t('esgCalcLogTitle')} Icon={FileText}
          action={
            <Badge tone="primary">{(totalKg / 1000).toFixed(2)} tCO2e</Badge>
          }>
          {entries.length === 0
            ? <div className="esg-calc-empty">
                {t('esgCalcEmpty')}
              </div>
            : (
              <table className="tbl esg-calc-tbl">
                <thead>
                  <tr>
                    <th>{t('esgCalcLogDate')}</th>
                    <th>{t('esgCalcLogFactor')}</th>
                    <th>{t('esgCalcLogAmount')}</th>
                    <th>{t('esgCalcLogResult')}</th>
                    <th>{t('esgCalcLogNote')}</th>
                    <th style={{ width: 50 }} />
                  </tr>
                </thead>
                <tbody>
                  {entries.map(entry => {
                    const f = EMISSION_FACTORS.find(x => x.code === entry.factorCode);
                    return (
                      <tr key={entry.id}>
                        <td className="mono esg-calc-row-mono">{entry.occurredAt}</td>
                        <td>
                          <Badge tone="info">Scope {f?.scope}</Badge>
                          <span className="ms-sm">{f ? (lang === 'ar' ? f.nameAr : f.nameEn) : entry.factorCode}</span>
                        </td>
                        <td className="mono">{entry.amount} {f?.unit}</td>
                        <td className="mono">
                          <b>{entry.computedKgCo2e.toFixed(2)}</b> kgCO2e
                        </td>
                        <td className="esg-calc-row-note">
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
  const [openNew, setOpenNew]           = useState(false);
  const [editCma, setEditCma]           = useState<CmaD | null>(null);
  const [approvingCma, setApprovingCma] = useState<CmaD | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — disclosures and readiness load independently.
    const [pg, r] = await Promise.all([
      listCma({ year: 2025, size: 100 }).catch(() => ({ content: [] as CmaD[], totalElements: 0 })),
      cmaReadiness(2025).catch(() => null as CmaR | null),
    ]);
    setItems(pg.content);
    setReadi(r);
    setLoading(false);
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

  // Sign-off (CMA Art. 91-94 board attestation). Only DISCLOSED / PARTIAL /
  // NOT_APPLICABLE rows can be approved (the service rejects others); the
  // approver name is captured for the audit trail and defaults to the user.
  const approveInitial: Record<string, string> = useMemo(
    () => ({ approvedBy: getUser()?.username ?? '' }), [approvingCma]);
  const approveFields: FieldDef[] = [
    { kind: 'text', key: 'approvedBy', required: true, span: 12, maxLength: 128,
      label: isAr ? 'المُعتمِد' : 'Approved by',
      placeholder: isAr ? 'اسم المسؤول عن الاعتماد' : 'Name of the approving officer' },
  ];
  async function submitApprove(values: Record<string, unknown>) {
    if (approvingCma?.id == null) return;
    const approvedBy = String(values.approvedBy ?? '').trim();
    if (!approvedBy) { toast.danger(isAr ? 'اسم المُعتمِد مطلوب' : 'Approver name is required'); return; }
    try {
      await approveCma(approvingCma.id, approvedBy);
      toast.success(isAr ? 'تم اعتماد الإفصاح' : 'Disclosure approved');
      setApprovingCma(null);
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

  // Create starts in a gap-free state (UNDER_REVIEW / DISCLOSED / NOT_APPLICABLE)
  // so it never trips Rule 5 (PARTIAL / NOT_DISCLOSED need a gap first).
  const CREATE_STATUSES: CmaStatus[] = ['UNDER_REVIEW', 'DISCLOSED', 'NOT_APPLICABLE'];
  const createFields: FieldDef[] = [
    { kind: 'select', key: 'article', required: true, span: 4,
      label: isAr ? 'المادة' : 'Article',
      options: (['91', '92', '93', '94'] as const).map(a => ({
        value: a, label: isAr ? `المادة ${a}` : `Article ${a}`,
      })) },
    { kind: 'number', key: 'reportingYear', required: true, span: 4, min: 2000, max: 2100,
      label: isAr ? 'سنة التقرير' : 'Reporting year' },
    { kind: 'select', key: 'disclosureStatus', span: 4,
      label: isAr ? 'حالة الإفصاح' : 'Disclosure status',
      options: CREATE_STATUSES.map(s => ({ value: s, label: cmaStatusLabel(s, isAr ? 'ar' : 'en') })) },
    { kind: 'text', key: 'clause', span: 4,
      label: isAr ? 'الفقرة' : 'Clause', placeholder: 'e.g. 91(a)' },
    { kind: 'text', key: 'titleEn', required: true, span: 8,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'text', key: 'responsibleDepartment', span: 6,
      label: isAr ? 'الإدارة المسؤولة' : 'Responsible department' },
    { kind: 'textarea', key: 'requirementEn', span: 12, rows: 2,
      label: isAr ? 'نص المتطلب (إنجليزي)' : 'Requirement (English)' },
  ];

  // Edit-disclosure — descriptive fields only. Disclosure status is owned by
  // the mark-* actions, evidence by Attach, and gaps by Gap, so none of those
  // are in this form. disclosureStatus is echoed back deliberately: the entity
  // defaults the column to "UNDER_REVIEW", so an omitted value would
  // deserialize to that default and update() would overwrite the reviewer's
  // real classification. The service re-applies Rules 5/6 against it on save.
  const editFields: FieldDef[] = [
    { kind: 'text', key: 'titleEn', required: true, span: 6,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'text', key: 'responsibleDepartment', span: 6,
      label: isAr ? 'الإدارة المسؤولة' : 'Responsible department' },
    { kind: 'text', key: 'reportSectionReference', span: 6,
      label: isAr ? 'مرجع قسم التقرير' : 'Report section reference' },
    { kind: 'text', key: 'reviewerName', span: 6,
      label: isAr ? 'اسم المراجع' : 'Reviewer name' },
    { kind: 'text', key: 'reviewerEmail', span: 6,
      label: isAr ? 'بريد المراجع' : 'Reviewer email' },
    { kind: 'textarea', key: 'requirementEn', span: 12, rows: 2,
      label: isAr ? 'نص المتطلب (إنجليزي)' : 'Requirement (English)' },
    { kind: 'textarea', key: 'requirementAr', span: 12, rows: 2,
      label: isAr ? 'نص المتطلب (عربي)' : 'Requirement (Arabic)' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    titleEn:                editCma?.titleEn ?? '',
    titleAr:                editCma?.titleAr ?? '',
    responsibleDepartment:  editCma?.responsibleDepartment ?? '',
    reportSectionReference: editCma?.reportSectionReference ?? '',
    reviewerName:           editCma?.reviewerName ?? '',
    reviewerEmail:          editCma?.reviewerEmail ?? '',
    requirementEn:          editCma?.requirementEn ?? '',
    requirementAr:          editCma?.requirementAr ?? '',
    notes:                  editCma?.notes ?? '',
  }), [editCma]);

  async function submitEdit(values: Record<string, unknown>): Promise<void> {
    if (!editCma?.id) return;
    const titleEn = String(values.titleEn ?? '').trim();
    if (!titleEn) {
      toast.danger(isAr ? 'العنوان (إنجليزي) مطلوب' : 'English title is required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      await updateCma(editCma.id, {
        disclosureStatus: editCma.disclosureStatus,
        titleEn,
        titleAr: str(values.titleAr),
        requirementEn: str(values.requirementEn),
        requirementAr: str(values.requirementAr),
        responsibleDepartment: str(values.responsibleDepartment),
        reportSectionReference: str(values.reportSectionReference),
        reviewerName: str(values.reviewerName),
        reviewerEmail: str(values.reviewerEmail),
        notes: str(values.notes),
      });
      toast.success(isAr ? 'حُدّث الإفصاح' : 'Disclosure updated');
      setEditCma(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث الإفصاح' : 'Failed to update disclosure'));
    }
  }

  async function submitCreate(values: Record<string, unknown>): Promise<void> {
    const titleEn = String(values.titleEn ?? '').trim();
    const article = String(values.article ?? '').trim();
    const year = Number(values.reportingYear);
    if (!titleEn || !article || !Number.isFinite(year)) {
      toast.danger(isAr
        ? 'العنوان (إنجليزي) والمادة وسنة التقرير حقول مطلوبة'
        : 'English title, article and reporting year are required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      const created = await createCma({
        article: article as CmaD['article'],
        reportingYear: year,
        disclosureStatus: (String(values.disclosureStatus ?? 'UNDER_REVIEW') || 'UNDER_REVIEW') as CmaStatus,
        clause: str(values.clause),
        titleEn,
        titleAr: str(values.titleAr),
        requirementEn: str(values.requirementEn),
        responsibleDepartment: str(values.responsibleDepartment),
      });
      toast.success(isAr
        ? `تمت إضافة إفصاح المادة ${created.article}`
        : `Article ${created.article} disclosure added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة الإفصاح' : 'Failed to add disclosure'));
    }
  }

  return (
    <div className="esg-stack">
      {/* Readiness KPI strip */}
      <div className="kpi-grid">
        <Kpi Icon={FileText}     label={isAr ? 'جاهزية الإفصاح' : 'Disclosure readiness'} value={`${readiness?.readinessPct ?? 0}%`}
             tone={(readiness?.readinessPct ?? 0) >= 80 ? 'success' : (readiness?.readinessPct ?? 0) >= 50 ? 'warning' : 'danger'} to="/esg" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'مُفصَح' : 'Disclosed'}      value={readiness?.byStatus.DISCLOSED ?? 0}         tone="success" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'جزئي' : 'Partial'}          value={readiness?.byStatus.PARTIAL ?? 0}           tone="warning" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'غير مُفصَح' : 'Not disclosed'} value={readiness?.byStatus.NOT_DISCLOSED ?? 0}  tone="danger" to="/esg" />
        <Kpi Icon={Paperclip}    label={isAr ? 'يتطلّب دليلاً' : 'Evidence required'} value={readiness?.byStatus.EVIDENCE_REQUIRED ?? 0} tone="danger" to="/esg" />
        <Kpi Icon={ArrowRight}   label={isAr ? 'قيد المراجعة' : 'Under review'}      value={readiness?.byStatus.UNDER_REVIEW ?? 0}      tone="info" to="/esg" />
      </div>

      <Card title={isAr ? 'سجلّ إفصاحات المواد 91–94' : 'CMA Articles 91–94 disclosure matrix'} Icon={FileText}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>
          {isAr ? 'إفصاح جديد' : 'New disclosure'}
        </Button>}>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'مصفوفة الإفصاحات الإلزامية لهيئة السوق المالية. الجزئي وغير المُفصَح يتطلّبان وصف فجوة (القاعدة 5). الإفصاحات المعتمدة بلا دليل تُعلَّم تلقائياً «يتطلّب دليلاً» (القاعدة 6).'
            : 'CMA mandatory disclosure matrix. Partial and Not-Disclosed states require a gap description (Rule 5). Disclosed items without evidence are auto-flagged Evidence Required (Rule 6).'}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد إفصاحات لهذه السنة' : 'No disclosures for this year'}</InlineEmpty>
            : Array.from(byArticle.entries()).map(([article, articleItems]) => (
                <div key={article} className="esg-cma-article">
                  <div className="esg-cma-pillar__title">
                    {isAr ? `المادة ${article}` : `Article ${article}`}
                  </div>
                  {articleItems.map(d => (
                    <div key={d.id} className="esg-disclosure-row">
                      <div className="esg-disclosure-row__main">
                        <div className="esg-disclosure-row__head">
                          <span className="mono esg-disclosure-row__code">
                            {articleLabel(d, isAr ? 'ar' : 'en')}
                          </span>
                          <span className="esg-disclosure-row__title">
                            {isAr && d.titleAr ? d.titleAr : d.titleEn}
                          </span>
                        </div>
                        <div className="esg-disclosure-row__desc">
                          {isAr && d.requirementAr ? d.requirementAr : d.requirementEn}
                        </div>
                        {hasEvidence(d) && (
                          <div className="esg-disclosure-row__evidence">
                            <Paperclip size={10} />
                            <span>{isAr ? 'الدليل:' : 'Evidence:'}</span>
                            <span className="mono">{d.evidenceRef}</span>
                            {d.evidenceOwner && <span>· {d.evidenceOwner}</span>}
                          </div>
                        )}
                        {hasGap(d) && (
                          <div className="esg-disclosure-row__gap esg-banner esg-banner--warning">
                            <AlertCircle size={11} className="esg-icon-inline" />
                            <strong>{isAr ? 'فجوة:' : 'Gap:'}</strong>{' '}
                            {isAr && d.gapDescriptionAr ? d.gapDescriptionAr : (d.gapDescriptionEn ?? d.gapDescriptionAr)}
                          </div>
                        )}
                      </div>
                      <div className="esg-disclosure-row__actions">
                        <Badge tone={cmaStatusTone(d.disclosureStatus)}>{cmaStatusLabel(d.disclosureStatus, isAr ? 'ar' : 'en')}</Badge>
                        <div className="esg-disclosure-row__btn-row">
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
                        <div className="esg-disclosure-row__btn-row">
                          <Button size="sm" variant="ghost" Icon={Pencil}
                            title={isAr ? 'تعديل' : 'Edit'}
                            onClick={() => setEditCma(d)} />
                          <Button size="sm" variant="secondary" Icon={Paperclip} onClick={() => setEvidencingId(d.id!)}>
                            {isAr ? 'إرفاق دليل' : 'Attach'}
                          </Button>
                          <Button size="sm" variant="secondary" Icon={AlertCircle} onClick={() => setGappingId({ id: d.id! })}>
                            {isAr ? 'تسجيل فجوة' : 'Gap'}
                          </Button>
                          {d.approvalStatus === 'APPROVED'
                            ? <Badge tone="success" title={d.approvedBy ? `${isAr ? 'اعتمده' : 'by'} ${d.approvedBy}` : undefined}>
                                <CheckCircle2 size={11} className="esg-icon-inline" />
                                {isAr ? 'مُعتمَد' : 'Approved'}{d.approvedBy ? ` · ${d.approvedBy}` : ''}
                              </Badge>
                            : (d.disclosureStatus === 'DISCLOSED' || d.disclosureStatus === 'PARTIAL' || d.disclosureStatus === 'NOT_APPLICABLE') && (
                                <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => setApprovingCma(d)}>
                                  {isAr ? 'اعتماد' : 'Approve'}
                                </Button>
                              )}
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
      <FormModal
        open={approvingCma !== null}
        title={isAr
          ? `اعتماد الإفصاح${approvingCma ? ` — ${articleLabel(approvingCma, 'ar')}` : ''}`
          : `Approve disclosure${approvingCma ? ` — ${articleLabel(approvingCma, 'en')}` : ''}`}
        fields={approveFields}
        initial={approveInitial}
        size="sm"
        submitLabel={isAr ? 'اعتماد' : 'Approve'}
        onClose={() => setApprovingCma(null)}
        onSubmit={submitApprove}
      />
      <FormModal
        open={openNew}
        title={isAr ? 'إفصاح هيئة السوق المالية جديد' : 'New CMA disclosure'}
        fields={createFields}
        initial={{
          article: '91', reportingYear: 2025, disclosureStatus: 'UNDER_REVIEW',
          clause: '', titleEn: '', titleAr: '', responsibleDepartment: '', requirementEn: '',
        }}
        size="lg"
        submitLabel={isAr ? 'إضافة الإفصاح' : 'Add disclosure'}
        onClose={() => setOpenNew(false)}
        onSubmit={submitCreate}
      />

      <FormModal
        open={editCma !== null}
        title={editCma
          ? (isAr ? `تعديل الإفصاح · ${articleLabel(editCma, 'ar')}` : `Edit disclosure · ${articleLabel(editCma, 'en')}`)
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditCma(null)}
        onSubmit={submitEdit}
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
  const [openNew, setOpenNew]           = useState(false);
  const [editTcfd, setEditTcfd]         = useState<TcfdI | null>(null);
  // Rule-7 traceability — link a TCFD recommendation to a climate risk in the
  // R&O register. The picker is populated from the kind=RISK items.
  const [linkingTcfd, setLinkingTcfd]   = useState<TcfdI | null>(null);
  const [climateRisks, setClimateRisks] = useState<ClimateRiskOpportunity[]>([]);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — matrix, readiness and the climate-risk picker load
    // independently.
    const [pg, r, croPg] = await Promise.all([
      listTcfd({ year: 2025, size: 100 }).catch(() => ({ content: [] as TcfdI[], totalElements: 0 })),
      tcfdReadiness(2025).catch(() => null as TcfdR | null),
      listClimateItems({ kind: 'RISK', size: 100 }).catch(() => ({ content: [] as ClimateRiskOpportunity[], totalElements: 0 })),
    ]);
    setItems(pg.content);
    setReadi(r);
    setClimateRisks(croPg.content);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  const climateRiskLabel = (id?: number) => {
    if (id == null) return null;
    const cro = climateRisks.find(c => c.id === id);
    if (!cro) return `#${id}`;
    return `${cro.itemCode ? cro.itemCode + ' · ' : ''}${isAr && cro.titleAr ? cro.titleAr : cro.titleEn}`;
  };

  const linkFields: FieldDef[] = useMemo(() => [
    { kind: 'select', key: 'climateRiskId', required: true, span: 12,
      label: isAr ? 'مخاطرة مناخية' : 'Climate risk',
      options: climateRisks.map(c => ({
        value: String(c.id),
        label: `${c.itemCode ? c.itemCode + ' · ' : ''}${isAr && c.titleAr ? c.titleAr : c.titleEn}`,
      })) },
  ], [climateRisks, isAr]);

  async function submitLink(values: Record<string, unknown>) {
    if (linkingTcfd?.id == null) return;
    const climateRiskId = Number(values.climateRiskId);
    if (!Number.isInteger(climateRiskId) || climateRiskId <= 0) {
      toast.danger(isAr ? 'اختر مخاطرة مناخية' : 'Select a climate risk'); return;
    }
    try {
      await tcfdLinkClimateRisk(linkingTcfd.id, climateRiskId);
      toast.success(isAr ? 'تم ربط المخاطرة المناخية' : 'Climate risk linked');
      setLinkingTcfd(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

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

  // Create starts in a gap-free state so it never trips Rule 5.
  const TCFD_CREATE_STATUSES: TcfdStatus[] = ['UNDER_REVIEW', 'DISCLOSED', 'NOT_APPLICABLE'];
  const TCFD_PILLARS: TcfdPillar[] = ['GOVERNANCE', 'STRATEGY', 'RISK_MGMT', 'METRICS_TARGETS'];
  const createFields: FieldDef[] = [
    { kind: 'select', key: 'pillar', required: true, span: 6,
      label: isAr ? 'الركيزة' : 'Pillar',
      options: TCFD_PILLARS.map(p => ({ value: p, label: tcfdPillarLabel(p, isAr ? 'ar' : 'en') })) },
    { kind: 'text', key: 'recommendationCode', required: true, span: 3,
      label: isAr ? 'رمز التوصية' : 'Rec. code', placeholder: 'a, b, c…' },
    { kind: 'number', key: 'reportingYear', required: true, span: 3, min: 2000, max: 2100,
      label: isAr ? 'سنة التقرير' : 'Reporting year' },
    { kind: 'text', key: 'titleEn', required: true, span: 6,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'select', key: 'disclosureStatus', span: 6,
      label: isAr ? 'حالة الإفصاح' : 'Disclosure status',
      options: TCFD_CREATE_STATUSES.map(s => ({ value: s, label: tcfdStatusLabel(s, isAr ? 'ar' : 'en') })) },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المالك' : 'Owner' },
    { kind: 'textarea', key: 'recommendationEn', span: 12, rows: 2,
      label: isAr ? 'نص التوصية (إنجليزي)' : 'Recommendation (English)' },
  ];

  // Edit-recommendation — descriptive fields only. Disclosure status is owned
  // by the mark-* actions, evidence by Attach, gaps by Gap, so none are in
  // this form. disclosureStatus is echoed back deliberately: the entity
  // defaults the column to "UNDER_REVIEW", so an omitted value would
  // deserialize to that default and update() would overwrite the reviewer's
  // real classification. The service re-applies Rules 5/6 against it on save.
  const editFields: FieldDef[] = [
    { kind: 'text', key: 'titleEn', required: true, span: 6,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text', key: 'ownerEmail', span: 6,
      label: isAr ? 'بريد المالك' : 'Owner email' },
    { kind: 'text', key: 'reviewerName', span: 12,
      label: isAr ? 'اسم المراجع' : 'Reviewer name' },
    { kind: 'textarea', key: 'recommendationEn', span: 12, rows: 2,
      label: isAr ? 'نص التوصية (إنجليزي)' : 'Recommendation (English)' },
    { kind: 'textarea', key: 'recommendationAr', span: 12, rows: 2,
      label: isAr ? 'نص التوصية (عربي)' : 'Recommendation (Arabic)' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    titleEn:           editTcfd?.titleEn ?? '',
    titleAr:           editTcfd?.titleAr ?? '',
    ownerName:         editTcfd?.ownerName ?? '',
    ownerEmail:        editTcfd?.ownerEmail ?? '',
    reviewerName:      editTcfd?.reviewerName ?? '',
    recommendationEn:  editTcfd?.recommendationEn ?? '',
    recommendationAr:  editTcfd?.recommendationAr ?? '',
    notes:             editTcfd?.notes ?? '',
  }), [editTcfd]);

  async function submitEdit(values: Record<string, unknown>): Promise<void> {
    if (!editTcfd?.id) return;
    const titleEn = String(values.titleEn ?? '').trim();
    if (!titleEn) {
      toast.danger(isAr ? 'العنوان (إنجليزي) مطلوب' : 'English title is required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      await updateTcfd(editTcfd.id, {
        disclosureStatus: editTcfd.disclosureStatus,
        titleEn,
        titleAr: str(values.titleAr),
        recommendationEn: str(values.recommendationEn),
        recommendationAr: str(values.recommendationAr),
        ownerName: str(values.ownerName),
        ownerEmail: str(values.ownerEmail),
        reviewerName: str(values.reviewerName),
        notes: str(values.notes),
      });
      toast.success(isAr ? 'حُدّثت التوصية' : 'Recommendation updated');
      setEditTcfd(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث التوصية' : 'Failed to update recommendation'));
    }
  }

  async function submitCreate(values: Record<string, unknown>): Promise<void> {
    const titleEn = String(values.titleEn ?? '').trim();
    const pillar = String(values.pillar ?? '').trim();
    const code = String(values.recommendationCode ?? '').trim();
    const year = Number(values.reportingYear);
    if (!titleEn || !pillar || !code || !Number.isFinite(year)) {
      toast.danger(isAr
        ? 'العنوان (إنجليزي) والركيزة ورمز التوصية وسنة التقرير حقول مطلوبة'
        : 'English title, pillar, recommendation code and reporting year are required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      const created = await createTcfd({
        pillar: pillar as TcfdPillar,
        recommendationCode: code,
        reportingYear: year,
        disclosureStatus: (String(values.disclosureStatus ?? 'UNDER_REVIEW') || 'UNDER_REVIEW') as TcfdStatus,
        titleEn,
        titleAr: str(values.titleAr),
        recommendationEn: str(values.recommendationEn),
        ownerName: str(values.ownerName),
      });
      toast.success(isAr
        ? `تمت إضافة توصية ${created.recommendationCode}`
        : `Recommendation ${created.recommendationCode} added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة التوصية' : 'Failed to add recommendation'));
    }
  }

  return (
    <div className="esg-stack">
      {/* Rule 7 banner — Strategy resilience recommendation is missing/partial */}
      {readiness?.strategyResilienceGap && (
        <div style={{
          padding: 12, borderRadius: 'var(--r-sm)',
          background: 'var(--warning-bg)', color: 'var(--warning-fg)',
          fontSize: 12, lineHeight: 1.5,
          display: 'flex', gap: 8, alignItems: 'center',
        }}>
          <AlertCircle size={16} className="esg-flex-shrink-0" />
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
             tone={(readiness?.alignmentPct ?? 0) >= 80 ? 'success' : (readiness?.alignmentPct ?? 0) >= 50 ? 'warning' : 'danger'} to="/esg" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'مُفصَح' : 'Disclosed'}    value={readiness?.byStatus.DISCLOSED ?? 0}      tone="success" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'جزئي' : 'Partial'}        value={readiness?.byStatus.PARTIAL ?? 0}        tone="warning" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'غير مُفصَح' : 'Not disclosed'} value={readiness?.byStatus.NOT_DISCLOSED ?? 0} tone="danger" to="/esg" />
        <Kpi Icon={Paperclip}    label={isAr ? 'يتطلّب دليلاً' : 'Evidence required'} value={readiness?.byStatus.EVIDENCE_REQUIRED ?? 0} tone="danger" to="/esg" />
        <Kpi Icon={ArrowRight}   label={isAr ? 'قيد المراجعة' : 'Under review'}      value={readiness?.byStatus.UNDER_REVIEW ?? 0}      tone="info" to="/esg" />
      </div>

      <Card title={isAr ? 'مصفوفة المحاذاة مع TCFD' : 'TCFD alignment matrix'} Icon={FileText}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>
          {isAr ? 'توصية جديدة' : 'New recommendation'}
        </Button>}>
        <div className="esg-text-eyebrow-sm">
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
                  <div key={pillar} className="esg-cma-pillar">
                    <div className="esg-cma-pillar__title">
                      {tcfdPillarLabel(pillar, isAr ? 'ar' : 'en')}
                    </div>
                    {pillarItems.map(t => (
                      <div key={t.id} className="esg-disclosure-row">
                        <div className="esg-disclosure-row__main">
                          <div className="esg-disclosure-row__head">
                            <span className="mono esg-disclosure-row__code">
                              ({t.recommendationCode})
                            </span>
                            <span className="esg-disclosure-row__title">
                              {isAr && t.titleAr ? t.titleAr : t.titleEn}
                            </span>
                          </div>
                          {(isAr ? t.recommendationAr : t.recommendationEn) && (
                            <div className="esg-disclosure-row__desc">
                              {isAr && t.recommendationAr ? t.recommendationAr : t.recommendationEn}
                            </div>
                          )}
                          {hasTcfdEvidence(t) && (
                            <div className="esg-disclosure-row__evidence">
                              <Paperclip size={10} />
                              <span>{isAr ? 'الدليل:' : 'Evidence:'}</span>
                              <span className="mono">{t.evidenceRef}</span>
                            </div>
                          )}
                          {hasTcfdGap(t) && (
                            <div className="esg-disclosure-row__gap esg-banner esg-banner--warning">
                              <AlertCircle size={11} className="esg-icon-inline" />
                              <strong>{isAr ? 'فجوة:' : 'Gap:'}</strong>{' '}
                              {isAr && t.gapDescriptionAr ? t.gapDescriptionAr : (t.gapDescriptionEn ?? t.gapDescriptionAr)}
                            </div>
                          )}
                        </div>
                        <div className="esg-disclosure-row__actions">
                          <Badge tone={tcfdStatusTone(t.disclosureStatus)}>{tcfdStatusLabel(t.disclosureStatus, isAr ? 'ar' : 'en')}</Badge>
                          <div className="esg-disclosure-row__btn-row">
                            {t.disclosureStatus !== 'DISCLOSED'     && <Button size="sm" variant="ghost" onClick={() => onMarkDisclosed(t)}>{isAr ? 'مُفصَح' : 'Disclosed'}</Button>}
                            {t.disclosureStatus !== 'PARTIAL'       && <Button size="sm" variant="ghost" onClick={() => onMarkPartial(t)}>{isAr ? 'جزئي' : 'Partial'}</Button>}
                            {t.disclosureStatus !== 'NOT_DISCLOSED' && <Button size="sm" variant="ghost" onClick={() => onMarkNotDisclosed(t)}>{isAr ? 'غير مُفصَح' : 'Not'}</Button>}
                            {t.disclosureStatus !== 'UNDER_REVIEW'  && <Button size="sm" variant="ghost" onClick={() => onMarkUnderReview(t)}>{isAr ? 'مراجعة' : 'Review'}</Button>}
                            {t.disclosureStatus !== 'NOT_APPLICABLE'&& <Button size="sm" variant="ghost" onClick={() => onMarkNotApplicable(t)}>{isAr ? 'لا ينطبق' : 'N/A'}</Button>}
                          </div>
                          <div className="esg-disclosure-row__btn-row">
                            <Button size="sm" variant="ghost" Icon={Pencil}
                              title={isAr ? 'تعديل' : 'Edit'} onClick={() => setEditTcfd(t)} />
                            <Button size="sm" variant="secondary" Icon={Paperclip}   onClick={() => setEvidencingId(t.id!)}>{isAr ? 'إرفاق' : 'Attach'}</Button>
                            <Button size="sm" variant="secondary" Icon={AlertCircle} onClick={() => setGappingId({ id: t.id! })}>{isAr ? 'فجوة' : 'Gap'}</Button>
                            <Button size="sm" variant="secondary" Icon={Link2} onClick={() => setLinkingTcfd(t)}>
                              {t.relatedClimateRiskId ? (isAr ? 'تغيير المخاطرة' : 'Relink risk') : (isAr ? 'ربط مخاطرة' : 'Link risk')}
                            </Button>
                          </div>
                          {t.relatedClimateRiskId != null && (
                            <div className="text-2xs text-muted" style={{ marginTop: 2 }}>
                              <Link2 size={10} className="esg-icon-inline" />
                              {isAr ? 'مخاطرة مرتبطة: ' : 'Linked risk: '}{climateRiskLabel(t.relatedClimateRiskId)}
                            </div>
                          )}
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
      <FormModal
        open={openNew}
        title={isAr ? 'توصية TCFD جديدة' : 'New TCFD recommendation'}
        fields={createFields}
        initial={{
          pillar: 'GOVERNANCE', recommendationCode: '', reportingYear: 2025,
          titleEn: '', titleAr: '', disclosureStatus: 'UNDER_REVIEW',
          ownerName: '', recommendationEn: '',
        }}
        size="lg"
        submitLabel={isAr ? 'إضافة التوصية' : 'Add recommendation'}
        onClose={() => setOpenNew(false)}
        onSubmit={submitCreate}
      />

      <FormModal
        open={editTcfd !== null}
        title={editTcfd
          ? (isAr
              ? `تعديل التوصية · ${tcfdPillarLabel(editTcfd.pillar, 'ar')} (${editTcfd.recommendationCode})`
              : `Edit recommendation · ${tcfdPillarLabel(editTcfd.pillar, 'en')} (${editTcfd.recommendationCode})`)
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditTcfd(null)}
        onSubmit={submitEdit}
      />

      <FormModal
        open={linkingTcfd !== null}
        title={linkingTcfd
          ? (isAr ? `ربط مخاطرة مناخية · ${linkingTcfd.recommendationCode}` : `Link climate risk · ${linkingTcfd.recommendationCode}`)
          : ''}
        fields={linkFields}
        initial={{ climateRiskId: linkingTcfd?.relatedClimateRiskId != null ? String(linkingTcfd.relatedClimateRiskId) : '' }}
        size="md"
        submitLabel={isAr ? 'ربط' : 'Link'}
        onClose={() => setLinkingTcfd(null)}
        onSubmit={submitLink}
      />
    </div>
  );
}

// ─── ESG Materiality tab (V62 — slice 8) ─────────────────────────────────────
//
// Double-materiality assessment: each topic is scored on two INDEPENDENT
// 0-5 axes — impact materiality (inside-out) and financial materiality
// (outside-in). Service computes materiality_score = max(impact, financial)
// (material if material from either perspective) and buckets into
// CRITICAL/HIGH/MEDIUM/LOW on every save. Approved topics anchor CMA
// Article 92(a) and the climate-risk topic register.

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
  const [openNew, setOpenNew]               = useState(false);
  const [editTopic, setEditTopic]           = useState<MaterialTopic | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — list and generated matrix load independently.
    const [pg, m] = await Promise.all([
      listMateriality({ year: 2025, size: 100 }).catch(() => ({ content: [] as MaterialTopic[], totalElements: 0 })),
      generateMatrix(2025).catch(() => null as MaterialMatrix | null),
    ]);
    setTopics(pg.content);
    setMatrix(m);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  const filtered = categoryFilter === 'ALL'
    ? topics
    : topics.filter(t => t.category === categoryFilter);

  async function submitScore(values: Record<string, unknown>) {
    if (!scoringTopic) return;
    const impact = Number(values.impactMateriality);
    const financial = Number(values.financialMateriality);
    if (!Number.isFinite(impact) || !Number.isFinite(financial) || impact < 0 || impact > 5 || financial < 0 || financial > 5) {
      toast.danger(isAr ? 'القيم يجب أن تكون 0..5' : 'Values must be 0..5'); return;
    }
    try {
      await scoreMaterialTopic(scoringTopic.id!, impact, financial);
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
    { kind: 'number', key: 'impactMateriality', required: true, min: 0, max: 5, span: 6,
      label: isAr ? 'الأهمية النسبية للأثر (من الداخل إلى الخارج) ٠–٥' : 'Impact materiality (inside-out) 0–5' },
    { kind: 'number', key: 'financialMateriality', required: true, min: 0, max: 5, span: 6,
      label: isAr ? 'الأهمية النسبية المالية (من الخارج إلى الداخل) ٠–٥' : 'Financial materiality (outside-in) 0–5' },
  ];
  const approveFields: FieldDef[] = [
    { kind: 'text', key: 'approvedBy', required: true, span: 12,
      label: isAr ? 'اسم المعتَمِد' : 'Approver name',
      placeholder: isAr ? 'مثال: خالد العتيبي' : 'e.g. Khalid Al-Otaibi' },
  ];

  const createFields: FieldDef[] = [
    { kind: 'text', key: 'topicEn', required: true, span: 6,
      label: isAr ? 'الموضوع (إنجليزي)' : 'Topic (English)' },
    { kind: 'text', key: 'topicAr', span: 6,
      label: isAr ? 'الموضوع (عربي)' : 'Topic (Arabic)' },
    { kind: 'select', key: 'category', required: true, span: 6,
      label: isAr ? 'الفئة' : 'Category',
      options: (['ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => ({
        value: c, label: materialCategoryLabel(c, isAr ? 'ar' : 'en'),
      })) },
    { kind: 'number', key: 'reportingYear', required: true, span: 6, min: 2000, max: 2100,
      label: isAr ? 'سنة التقرير' : 'Reporting year' },
    { kind: 'number', key: 'impactMateriality', min: 0, max: 5, span: 6,
      label: isAr ? 'الأهمية النسبية للأثر (٠–٥)' : 'Impact materiality (0–5)' },
    { kind: 'number', key: 'financialMateriality', min: 0, max: 5, span: 6,
      label: isAr ? 'الأهمية النسبية المالية (٠–٥)' : 'Financial materiality (0–5)' },
    { kind: 'text', key: 'stakeholderGroups', span: 6,
      label: isAr ? 'مجموعات أصحاب المصلحة' : 'Stakeholder groups' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المسؤول' : 'Owner' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
  ];

  // Edit-topic — descriptive + ownership fields only. Scoring has its own
  // dedicated "Score" action, and the year is fixed once created.
  const editFields: FieldDef[] = [
    { kind: 'text', key: 'topicEn', required: true, span: 6,
      label: isAr ? 'الموضوع (إنجليزي)' : 'Topic (English)' },
    { kind: 'text', key: 'topicAr', span: 6,
      label: isAr ? 'الموضوع (عربي)' : 'Topic (Arabic)' },
    { kind: 'select', key: 'category', required: true, span: 6,
      label: isAr ? 'الفئة' : 'Category',
      options: (['ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => ({
        value: c, label: materialCategoryLabel(c, isAr ? 'ar' : 'en'),
      })) },
    { kind: 'text', key: 'stakeholderGroups', span: 6,
      label: isAr ? 'مجموعات أصحاب المصلحة' : 'Stakeholder groups' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المسؤول' : 'Owner' },
    { kind: 'text', key: 'ownerEmail', span: 6,
      label: isAr ? 'بريد المسؤول' : 'Owner email' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    topicEn:           editTopic?.topicEn ?? '',
    topicAr:           editTopic?.topicAr ?? '',
    category:          editTopic?.category ?? 'ENVIRONMENTAL',
    stakeholderGroups: editTopic?.stakeholderGroups ?? '',
    ownerName:         editTopic?.ownerName ?? '',
    ownerEmail:        editTopic?.ownerEmail ?? '',
    descriptionEn:     editTopic?.descriptionEn ?? '',
    descriptionAr:     editTopic?.descriptionAr ?? '',
    notes:             editTopic?.notes ?? '',
  }), [editTopic]);

  async function submitEdit(values: Record<string, unknown>): Promise<void> {
    if (!editTopic?.id) return;
    const topicEn = String(values.topicEn ?? '').trim();
    const category = String(values.category ?? '').trim();
    if (!topicEn || !category) {
      toast.danger(isAr ? 'الموضوع والفئة حقلان مطلوبان' : 'Topic and category are required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      await updateMaterialTopic(editTopic.id, {
        topicEn,
        topicAr: str(values.topicAr),
        category: category as MaterialCategory,
        descriptionEn: str(values.descriptionEn),
        descriptionAr: str(values.descriptionAr),
        stakeholderGroups: str(values.stakeholderGroups),
        ownerName: str(values.ownerName),
        ownerEmail: str(values.ownerEmail),
        notes: str(values.notes),
        // Preserve the double-materiality axes: the entity defaults both to 0
        // and the service recomputes materiality_score = max(impact, financial)
        // on every save, so an omitted value would zero out the score + tier.
        // Scoring is owned by the dedicated "Score" action.
        impactMateriality: editTopic.impactMateriality ?? editTopic.stakeholderImportance,
        financialMateriality: editTopic.financialMateriality ?? editTopic.businessImpact,
      });
      toast.success(isAr ? 'حُدّث الموضوع' : 'Topic updated');
      setEditTopic(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث الموضوع' : 'Failed to update topic'));
    }
  }

  async function submitCreate(values: Record<string, unknown>): Promise<void> {
    const topicEn = String(values.topicEn ?? '').trim();
    const category = String(values.category ?? '').trim();
    const year = Number(values.reportingYear);
    if (!topicEn || !category || !Number.isFinite(year)) {
      toast.danger(isAr
        ? 'الموضوع والفئة وسنة التقرير حقول مطلوبة'
        : 'Topic, category and reporting year are required');
      return;
    }
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      const created = await createMaterialTopic({
        topicEn,
        topicAr: str(values.topicAr),
        category: category as MaterialCategory,
        reportingYear: year,
        impactMateriality: num(values.impactMateriality) ?? 0,
        financialMateriality: num(values.financialMateriality) ?? 0,
        stakeholderGroups: str(values.stakeholderGroups),
        ownerName: str(values.ownerName),
        descriptionEn: str(values.descriptionEn),
        status: 'DRAFT',
      });
      toast.success(isAr
        ? `تمت إضافة الموضوع ${created.topicCode ?? created.topicEn}`
        : `Topic ${created.topicCode ?? created.topicEn} added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة الموضوع' : 'Failed to add topic'));
    }
  }

  return (
    <div className="esg-stack">
      {/* KPI strip — tier counts + category counts */}
      <div className="kpi-grid">
        <Kpi Icon={Target}       label={isAr ? 'إجمالي الموضوعات' : 'Total topics'}     value={matrix?.total ?? 0} tone="primary" onClick={() => setCategoryFilter('ALL')} active={categoryFilter === 'ALL'} />
        <Kpi Icon={AlertCircle}  label={isAr ? 'حرج' : 'Critical'}                       value={matrix?.byTier.CRITICAL ?? 0} tone="danger" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'مرتفع' : 'High'}                         value={matrix?.byTier.HIGH ?? 0}     tone="warning" to="/esg" />
        <Kpi Icon={Leaf}         label={isAr ? 'البيئة' : 'Environmental'}                value={matrix?.byCategory.ENVIRONMENTAL ?? 0} tone="success" onClick={() => setCategoryFilter('ENVIRONMENTAL')} active={categoryFilter === 'ENVIRONMENTAL'} />
        <Kpi Icon={Building2}    label={isAr ? 'الاجتماع' : 'Social'}                     value={matrix?.byCategory.SOCIAL ?? 0}        tone="info" onClick={() => setCategoryFilter('SOCIAL')} active={categoryFilter === 'SOCIAL'} />
        <Kpi Icon={Building2}    label={isAr ? 'الحوكمة' : 'Governance'}                  value={matrix?.byCategory.GOVERNANCE ?? 0}    tone="primary" onClick={() => setCategoryFilter('GOVERNANCE')} active={categoryFilter === 'GOVERNANCE'} />
      </div>

      <Card title={isAr ? 'مصفوفة تقييم الأهمية النسبية المزدوجة' : 'Double-materiality assessment matrix'} Icon={Target}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>
          {isAr ? 'موضوع جديد' : 'New topic'}
        </Button>}>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'الأهمية النسبية المزدوجة (وفق CSRD): يُقيَّم كل موضوع على محورين مستقلين من ٠ إلى ٥ — الأهمية النسبية للأثر (من الداخل إلى الخارج) والأهمية النسبية المالية (من الخارج إلى الداخل). درجة الأهمية النسبية = الأعلى بين المحورين (يُعدّ الموضوع ذا أهمية نسبية إذا كان كذلك من أيٍّ من المنظورين).'
            : 'Double materiality (CSRD): each topic is scored on two INDEPENDENT axes (0-5) — impact materiality (inside-out) and financial materiality (outside-in). Materiality score = max of the two — material if material from either perspective.'}
        </div>

        {/* Category filter chips */}
        <div className="esg-filter-row">
          {(['ALL', 'ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => (
            <button key={c} onClick={() => setCategoryFilter(c)}
              className={`esg-filter-btn${categoryFilter === c ? ' is-active' : ''}`}>
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
                    <th>{isAr ? 'الأثر' : 'Impact'}</th>
                    <th>{isAr ? 'المالية' : 'Financial'}</th>
                    <th>{isAr ? 'الدرجة' : 'Score'}</th>
                    <th>{isAr ? 'المستوى' : 'Tier'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th className="esg-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(t => (
                    <tr key={t.id}>
                      <td className="mono text-2xs">{t.topicCode ?? `#${t.id}`}</td>
                      <td>
                        <div className="esg-cell-title">
                          {isAr && t.topicAr ? t.topicAr : t.topicEn}
                        </div>
                        {t.ownerName && <div className="text-2xs text-muted esg-cell-sub">{t.ownerName}</div>}
                      </td>
                      <td><Badge tone="neutral">{materialCategoryLabel(t.category, isAr ? 'ar' : 'en')}</Badge></td>
                      <td className="mono text-xs esg-text-center">{Number(t.impactMateriality ?? t.stakeholderImportance).toFixed(1)}</td>
                      <td className="mono text-xs esg-text-center">{Number(t.financialMateriality ?? t.businessImpact).toFixed(1)}</td>
                      <td className="mono text-xs" style={{ textAlign: 'center', fontWeight: 700 }}>
                        {t.materialityScore != null ? Number(t.materialityScore).toFixed(1) : '—'}
                        {Number(t.impactMateriality ?? t.stakeholderImportance) >= 3 && Number(t.financialMateriality ?? t.businessImpact) >= 3 && (
                          <span title={isAr ? 'ذو أهمية نسبية مزدوجة' : 'Double material'}> <Badge tone="danger">2×</Badge></span>
                        )}
                      </td>
                      <td>
                        {t.priorityTier && <Badge tone={materialTierTone(t.priorityTier)}>{materialTierLabel(t.priorityTier, isAr ? 'ar' : 'en')}</Badge>}
                      </td>
                      <td><Badge tone={materialStatusTone(t.status)}>{materialStatusLabel(t.status, isAr ? 'ar' : 'en')}</Badge></td>
                      <td className="esg-text-end">
                        <div className="esg-actions-row">
                          {t.status !== 'ARCHIVED' && (
                            <Button size="sm" variant="ghost" Icon={Pencil}
                              title={isAr ? 'تعديل' : 'Edit'}
                              onClick={() => setEditTopic(t)} />
                          )}
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
              {isAr ? '← الأهمية النسبية للأثر' : 'Impact materiality →'}
            </div>
            <div style={{ position: 'absolute', bottom: -20, left: 0, right: 0, textAlign: 'center', fontSize: 10, color: 'var(--ink-3)' }}>
              {isAr ? '← الأهمية النسبية المالية' : 'Financial materiality →'}
            </div>
            {/* Quadrant guides at 2.5 / 4.0 */}
            <div style={{ position: 'absolute', insetInlineStart: '50%', top: 0, bottom: 0, width: 1, background: 'var(--border)' }} />
            <div style={{ position: 'absolute', insetBlockStart: '50%', left: 0, right: 0, height: 1, background: 'var(--border)' }} />
            {/* Plot points */}
            {matrix.matrix.map(e => {
              // X = financial materiality; Y = impact materiality (invert Y so high = top).
              const x = (Number(e.financialMateriality ?? e.businessImpact) / 5) * 100;
              const y = (1 - Number(e.impactMateriality ?? e.stakeholderImportance) / 5) * 100;
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
          impactMateriality: scoringTopic?.impactMateriality ?? scoringTopic?.stakeholderImportance ?? 3,
          financialMateriality: scoringTopic?.financialMateriality ?? scoringTopic?.businessImpact ?? 3,
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
      <FormModal
        open={openNew}
        title={isAr ? 'موضوع جوهرية جديد' : 'New materiality topic'}
        fields={createFields}
        initial={{
          topicEn: '', topicAr: '', category: 'ENVIRONMENTAL', reportingYear: 2025,
          impactMateriality: 3, financialMateriality: 3,
          stakeholderGroups: '', ownerName: '', descriptionEn: '',
        }}
        size="lg"
        submitLabel={isAr ? 'إضافة الموضوع' : 'Add topic'}
        onClose={() => setOpenNew(false)}
        onSubmit={submitCreate}
      />
      <FormModal
        open={editTopic !== null}
        title={editTopic
          ? (isAr ? `تعديل الموضوع · ${editTopic.topicCode ?? ''}` : `Edit topic · ${editTopic.topicCode ?? ''}`)
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditTopic(null)}
        onSubmit={submitEdit}
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
  const [openNew, setOpenNew]               = useState(false);
  const [editMetric, setEditMetric]         = useState<EsgM | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — metrics list and aggregate are independent.
    const [pg, a] = await Promise.all([
      listMetrics({ year: 2025, size: 200 }).catch(() => ({ content: [] as EsgM[], totalElements: 0 })),
      metricsAggregate(2025).catch(() => null as MetricAggregate | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
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

  const createFields: FieldDef[] = [
    { kind: 'text', key: 'metricNameEn', required: true, span: 6,
      label: isAr ? 'اسم المؤشّر (إنجليزي)' : 'Metric name (English)' },
    { kind: 'text', key: 'metricNameAr', span: 6,
      label: isAr ? 'اسم المؤشّر (عربي)' : 'Metric name (Arabic)' },
    { kind: 'select', key: 'category', required: true, span: 6,
      label: isAr ? 'الفئة' : 'Category',
      options: (['ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => ({
        value: c, label: esgMetricCategoryLabel(c, isAr ? 'ar' : 'en'),
      })) },
    { kind: 'text', key: 'subcategory', span: 6,
      label: isAr ? 'الفئة الفرعية' : 'Subcategory' },
    { kind: 'text', key: 'unit', required: true, span: 4,
      label: isAr ? 'الوحدة' : 'Unit', placeholder: 'MWh, %, tCO₂e…' },
    { kind: 'number', key: 'reportingYear', required: true, span: 4, min: 2000, max: 2100,
      label: isAr ? 'سنة التقرير' : 'Reporting year' },
    { kind: 'text', key: 'metricCode', span: 4,
      label: isAr ? 'الكود (اختياري)' : 'Code (optional)', placeholder: 'ENV-ENERGY-TOTAL' },
    { kind: 'number', key: 'baselineValue', span: 3,
      label: isAr ? 'قيمة الأساس' : 'Baseline value' },
    { kind: 'number', key: 'baselineYear', span: 3, min: 2000, max: 2100,
      label: isAr ? 'سنة الأساس' : 'Baseline year' },
    { kind: 'number', key: 'targetValue', span: 3,
      label: isAr ? 'القيمة المستهدفة' : 'Target value' },
    { kind: 'number', key: 'targetYear', span: 3, min: 2000, max: 2100,
      label: isAr ? 'سنة الهدف' : 'Target year' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المسؤول' : 'Owner' },
    { kind: 'text', key: 'responsibleDepartment', span: 6,
      label: isAr ? 'الإدارة المسؤولة' : 'Responsible department' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
  ];

  // Edit-metric — descriptive fields + baseline/target correction. The
  // current value keeps its dedicated "Record" action, and the reporting
  // year / code are fixed once created. Numeric columns have no entity
  // default (null), so omitting current_value leaves it untouched.
  const editFields: FieldDef[] = [
    { kind: 'text', key: 'metricNameEn', required: true, span: 6,
      label: isAr ? 'اسم المؤشّر (إنجليزي)' : 'Metric name (English)' },
    { kind: 'text', key: 'metricNameAr', span: 6,
      label: isAr ? 'اسم المؤشّر (عربي)' : 'Metric name (Arabic)' },
    { kind: 'select', key: 'category', required: true, span: 6,
      label: isAr ? 'الفئة' : 'Category',
      options: (['ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => ({
        value: c, label: esgMetricCategoryLabel(c, isAr ? 'ar' : 'en'),
      })) },
    { kind: 'text', key: 'subcategory', span: 3,
      label: isAr ? 'الفئة الفرعية' : 'Subcategory' },
    { kind: 'text', key: 'unit', required: true, span: 3,
      label: isAr ? 'الوحدة' : 'Unit', placeholder: 'MWh, %, tCO₂e…' },
    { kind: 'number', key: 'baselineValue', span: 3,
      label: isAr ? 'قيمة الأساس' : 'Baseline value' },
    { kind: 'number', key: 'baselineYear', span: 3, min: 2000, max: 2100,
      label: isAr ? 'سنة الأساس' : 'Baseline year' },
    { kind: 'number', key: 'targetValue', span: 3,
      label: isAr ? 'القيمة المستهدفة' : 'Target value' },
    { kind: 'number', key: 'targetYear', span: 3, min: 2000, max: 2100,
      label: isAr ? 'سنة الهدف' : 'Target year' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المسؤول' : 'Owner' },
    { kind: 'text', key: 'ownerEmail', span: 6,
      label: isAr ? 'بريد المسؤول' : 'Owner email' },
    { kind: 'text', key: 'responsibleDepartment', span: 12,
      label: isAr ? 'الإدارة المسؤولة' : 'Responsible department' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    metricNameEn:          editMetric?.metricNameEn ?? '',
    metricNameAr:          editMetric?.metricNameAr ?? '',
    category:              editMetric?.category ?? 'ENVIRONMENTAL',
    subcategory:           editMetric?.subcategory ?? '',
    unit:                  editMetric?.unit ?? '',
    baselineValue:         editMetric?.baselineValue != null ? String(editMetric.baselineValue) : '',
    baselineYear:          editMetric?.baselineYear != null ? String(editMetric.baselineYear) : '',
    targetValue:           editMetric?.targetValue != null ? String(editMetric.targetValue) : '',
    targetYear:            editMetric?.targetYear != null ? String(editMetric.targetYear) : '',
    ownerName:             editMetric?.ownerName ?? '',
    ownerEmail:            editMetric?.ownerEmail ?? '',
    responsibleDepartment: editMetric?.responsibleDepartment ?? '',
    descriptionEn:         editMetric?.descriptionEn ?? '',
    descriptionAr:         editMetric?.descriptionAr ?? '',
    notes:                 editMetric?.notes ?? '',
  }), [editMetric]);

  async function submitEdit(values: Record<string, unknown>): Promise<void> {
    if (!editMetric?.id) return;
    const nameEn = String(values.metricNameEn ?? '').trim();
    const unit = String(values.unit ?? '').trim();
    const category = String(values.category ?? '').trim();
    if (!nameEn || !unit || !category) {
      toast.danger(isAr
        ? 'اسم المؤشّر والفئة والوحدة حقول مطلوبة'
        : 'Metric name, category and unit are required');
      return;
    }
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      await updateMetric(editMetric.id, {
        metricNameEn: nameEn,
        metricNameAr: str(values.metricNameAr),
        category: category as MetricCategory,
        subcategory: str(values.subcategory),
        unit,
        baselineValue: num(values.baselineValue),
        baselineYear: num(values.baselineYear),
        targetValue: num(values.targetValue),
        targetYear: num(values.targetYear),
        ownerName: str(values.ownerName),
        ownerEmail: str(values.ownerEmail),
        responsibleDepartment: str(values.responsibleDepartment),
        descriptionEn: str(values.descriptionEn),
        descriptionAr: str(values.descriptionAr),
        notes: str(values.notes),
      });
      toast.success(isAr ? 'حُدّث المؤشّر' : 'Metric updated');
      setEditMetric(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث المؤشّر' : 'Failed to update metric'));
    }
  }

  async function submitCreate(values: Record<string, unknown>): Promise<void> {
    const nameEn = String(values.metricNameEn ?? '').trim();
    const unit = String(values.unit ?? '').trim();
    const category = String(values.category ?? '').trim();
    const year = Number(values.reportingYear);
    if (!nameEn || !unit || !category || !Number.isFinite(year)) {
      toast.danger(isAr
        ? 'اسم المؤشّر والفئة والوحدة وسنة التقرير حقول مطلوبة'
        : 'Metric name, category, unit and reporting year are required');
      return;
    }
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    const str = (v: unknown) => {
      const s = String(v ?? '').trim();
      return s || undefined;
    };
    try {
      const created = await createMetric({
        metricNameEn: nameEn,
        metricNameAr: str(values.metricNameAr),
        category: category as MetricCategory,
        subcategory: str(values.subcategory),
        unit,
        reportingYear: year,
        metricCode: str(values.metricCode),
        baselineValue: num(values.baselineValue),
        baselineYear: num(values.baselineYear),
        targetValue: num(values.targetValue),
        targetYear: num(values.targetYear),
        ownerName: str(values.ownerName),
        responsibleDepartment: str(values.responsibleDepartment),
        descriptionEn: str(values.descriptionEn),
        status: 'ACTIVE',
      });
      toast.success(isAr
        ? `تمت إضافة المؤشّر ${created.metricCode ?? created.metricNameEn}`
        : `Metric ${created.metricCode ?? created.metricNameEn} added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة المؤشّر' : 'Failed to add metric'));
    }
  }

  return (
    <div className="esg-stack">
      {/* Per-pillar mean-progress strip */}
      <div className="kpi-grid">
        <Kpi Icon={TrendingDown} label={isAr ? 'إجمالي المؤشّرات' : 'Total metrics'}                value={agg?.total ?? 0} tone="primary" onClick={() => setCategoryFilter('ALL')} active={categoryFilter === 'ALL'} />
        <Kpi Icon={Leaf}         label={isAr ? 'متوسّط تقدّم البيئة' : 'Env. mean progress'}     value={`${agg ? Number(agg.meanProgressByCategory.ENVIRONMENTAL ?? 0).toFixed(0) : 0}%`} tone="success" onClick={() => setCategoryFilter('ENVIRONMENTAL')} active={categoryFilter === 'ENVIRONMENTAL'} />
        <Kpi Icon={Building2}    label={isAr ? 'متوسّط تقدّم الاجتماع' : 'Social mean progress'} value={`${agg ? Number(agg.meanProgressByCategory.SOCIAL ?? 0).toFixed(0) : 0}%`}        tone="info" onClick={() => setCategoryFilter('SOCIAL')} active={categoryFilter === 'SOCIAL'} />
        <Kpi Icon={Building2}    label={isAr ? 'متوسّط تقدّم الحوكمة' : 'Gov. mean progress'}   value={`${agg ? Number(agg.meanProgressByCategory.GOVERNANCE ?? 0).toFixed(0) : 0}%`}    tone="primary" onClick={() => setCategoryFilter('GOVERNANCE')} active={categoryFilter === 'GOVERNANCE'} />
        <Kpi Icon={CheckCircle2} label={isAr ? 'بلغ الهدف' : 'At target'}                       value={agg
          ? (agg.atTargetByCategory.ENVIRONMENTAL + agg.atTargetByCategory.SOCIAL + agg.atTargetByCategory.GOVERNANCE)
          : 0} tone="success" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'خارج المسار' : 'Off track'}                     value={agg
          ? (agg.offTrackByCategory.ENVIRONMENTAL + agg.offTrackByCategory.SOCIAL + agg.offTrackByCategory.GOVERNANCE)
          : 0} tone="danger" to="/esg" />
      </div>

      <Card title={isAr ? 'سجلّ مؤشّرات الاستدامة (E/S/G)' : 'ESG Metrics Register (E/S/G)'} Icon={TrendingDown}
        action={<Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>
          {isAr ? 'مؤشّر جديد' : 'New metric'}
        </Button>}>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'سجلّ موحَّد لمؤشّرات البيئة والاجتماع والحوكمة. التقدّم يُحسب وفق الاتجاه — يدعم أهداف الارتفاع («حصة المتجدّدة») وأهداف الانخفاض («LTIFR»).'
            : 'Unified register for Environmental, Social, and Governance metrics. Progress is direction-aware — supports both raise-to-target ("renewable share") and lower-to-target ("LTIFR").'}
        </div>

        {/* Category filter chips */}
        <div className="esg-filter-row">
          {(['ALL', 'ENVIRONMENTAL', 'SOCIAL', 'GOVERNANCE'] as const).map(c => (
            <button key={c} onClick={() => setCategoryFilter(c)}
              className={`esg-filter-btn${categoryFilter === c ? ' is-active' : ''}`}>
              {c === 'ALL' ? (isAr ? 'الكل' : 'All') : esgMetricCategoryLabel(c as MetricCategory, isAr ? 'ar' : 'en')}
            </button>
          ))}
          <span className="text-2xs text-muted esg-filter-row__count">
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
                    <th className="esg-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(m => {
                    const pct = computeMetricProgress(m);
                    return (
                      <tr key={m.id}>
                        <td className="mono text-2xs">{m.metricCode ?? `#${m.id}`}</td>
                        <td>
                          <div className="esg-cell-title">
                            {isAr && m.metricNameAr ? m.metricNameAr : m.metricNameEn}
                          </div>
                          {m.ownerName && <div className="text-2xs text-muted esg-cell-sub">{m.ownerName}</div>}
                        </td>
                        <td>
                          <Badge tone="neutral">{esgMetricCategoryLabel(m.category, isAr ? 'ar' : 'en')}</Badge>
                          {m.subcategory && <div className="text-2xs text-muted esg-cell-sub">{m.subcategory}</div>}
                        </td>
                        <td className="mono text-xs esg-text-end">
                          {m.baselineValue != null ? Number(m.baselineValue).toLocaleString() : '—'}
                          <div className="text-2xs text-muted">{m.unit}</div>
                        </td>
                        <td className="mono text-xs esg-text-end esg-text-bold">
                          {m.currentValue != null ? Number(m.currentValue).toLocaleString() : '—'}
                          {m.currentValueAsOf && <div className="text-2xs text-muted">{m.currentValueAsOf}</div>}
                        </td>
                        <td className="mono text-xs esg-text-end">
                          {m.targetValue != null ? Number(m.targetValue).toLocaleString() : '—'}
                          {m.targetYear && <div className="text-2xs text-muted">→ {m.targetYear}</div>}
                        </td>
                        <td style={{ minWidth: 120 }}>
                          <div className="esg-progress-wrap">
                            <div className="esg-progress-track">
                              <div className="esg-progress-fill"
                                style={{
                                  width: `${Math.min(100, pct)}%`,
                                  background: pct >= 100 ? 'var(--success)' : pct >= 50 ? 'var(--primary)' : 'var(--warning)',
                                }} />
                            </div>
                            <span className="mono text-2xs esg-progress-label esg-text-end" style={{ minWidth: 40 }}>{pct.toFixed(0)}%</span>
                          </div>
                        </td>
                        <td><Badge tone={metricStatusTone(m.status)}>{metricStatusLabel(m.status, isAr ? 'ar' : 'en')}</Badge></td>
                        <td className="esg-text-end">
                          <div className="esg-actions-row">
                            {m.status !== 'ARCHIVED' && (
                              <Button size="sm" variant="ghost" Icon={Pencil}
                                title={isAr ? 'تعديل' : 'Edit'}
                                onClick={() => setEditMetric(m)} />
                            )}
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

      <FormModal
        open={openNew}
        title={isAr ? 'مؤشّر استدامة جديد' : 'New ESG metric'}
        fields={createFields}
        initial={{
          metricNameEn: '', metricNameAr: '', category: 'ENVIRONMENTAL',
          subcategory: '', unit: '', reportingYear: 2025, metricCode: '',
          baselineValue: '', baselineYear: '', targetValue: '', targetYear: '',
          ownerName: '', responsibleDepartment: '', descriptionEn: '',
        }}
        size="lg"
        submitLabel={isAr ? 'إضافة المؤشّر' : 'Add metric'}
        onClose={() => setOpenNew(false)}
        onSubmit={submitCreate}
      />

      <FormModal
        open={editMetric !== null}
        title={editMetric
          ? (isAr ? `تعديل المؤشّر · ${editMetric.metricCode ?? ''}` : `Edit metric · ${editMetric.metricCode ?? ''}`)
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditMetric(null)}
        onSubmit={submitEdit}
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

  const [items, setItems] = useState<EvidenceItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<EvidenceItemStatus | 'ALL'>('ALL');
  const [createOpen, setCreateOpen] = useState(false);
  const [linkTarget, setLinkTarget] = useState<EvidenceItem | null>(null);
  const [uploadTargetId, setUploadTargetId] = useState<number | null>(null);
  const fileRef = useRef<HTMLInputElement | null>(null);

  async function reload() {
    setLoading(true);
    const pg = await listEvidenceItems({ size: 200 })
      .catch(() => ({ content: [] as EvidenceItem[], totalElements: 0, totalPages: 0, number: 0, size: 0 }));
    setItems(pg.content);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  const counts = useMemo(() => {
    const c: Record<string, number> = {
      total: items.length, DRAFT: 0, PENDING_REVIEW: 0, APPROVED: 0, REJECTED: 0, NEEDS_REVISION: 0, EXPIRED: 0,
    };
    for (const e of items) c[e.status] = (c[e.status] ?? 0) + 1;
    return c;
  }, [items]);

  const filtered = statusFilter === 'ALL' ? items : items.filter(e => e.status === statusFilter);

  // ── Create (real EvidenceItem, starts DRAFT) ─────────────────────
  // The optional file staged in the form is uploaded straight after create, so
  // the user attaches evidence in one step (server computes the SHA-256). A
  // metadata-only draft is still allowed — upload later via the row's button.
  async function submitCreate(values: Record<string, unknown>) {
    const titleEn = String(values.titleEn ?? '').trim();
    if (!titleEn) { toast.danger(isAr ? 'العنوان مطلوب' : 'Title is required'); return; }
    const file = values.file instanceof File ? values.file : null;
    try {
      const created = await createEvidenceItem({
        titleEn,
        titleAr: String(values.titleAr ?? '').trim() || undefined,
        descriptionEn: String(values.descriptionEn ?? '').trim() || undefined,
        evidenceType: String(values.evidenceType ?? 'DOCUMENT'),
        version: String(values.version ?? '').trim() || undefined,
        validFrom: String(values.validFrom ?? '').trim() || undefined,
        validTo: String(values.validTo ?? '').trim() || undefined,
        sourceModule: 'SUSTAINABILITY',
      });
      if (file) {
        try {
          await uploadEvidenceItemFile(created.id, file);
          toast.success(isAr ? 'تم إنشاء الدليل ورفع الملف (بصمة SHA-256) — جاهز للإرسال'
                             : 'Evidence created and file uploaded (SHA-256) — ready to submit');
        } catch (upErr) {
          toast.danger(isAr ? 'أُنشئ الدليل لكن فشل رفع الملف — ارفعه من الجدول'
                            : (upErr instanceof ApiError ? upErr.message : 'Evidence created, but the file upload failed — upload it from the table'));
        }
      } else {
        toast.success(isAr ? 'تم إنشاء الدليل (مسودة) — ارفع ملفاً ثم أرسله للمراجعة'
                           : 'Evidence created (Draft) — upload a file, then submit for review');
      }
      setCreateOpen(false);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Create failed'); }
  }

  // ── Real file upload → server computes SHA-256 ───────────────────
  function triggerUpload(id: number) { setUploadTargetId(id); fileRef.current?.click(); }
  async function uploadPicked(file: File | null) {
    if (!file || uploadTargetId == null) { setUploadTargetId(null); return; }
    try {
      await uploadEvidenceItemFile(uploadTargetId, file);
      toast.success(isAr ? 'تم رفع الملف واحتساب بصمة SHA-256' : 'File uploaded — SHA-256 computed server-side');
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Upload failed'); }
    finally { setUploadTargetId(null); }
  }

  // ── Lifecycle transitions ────────────────────────────────────────
  async function act(fn: () => Promise<unknown>, ok: string) {
    try { await fn(); toast.success(ok); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  const doSubmit  = (id: number) => act(() => submitEvidenceItem(id),  isAr ? 'أُرسل للمراجعة' : 'Submitted for review');
  const doApprove = (id: number) => act(() => approveEvidenceItem(id), isAr ? 'تم الاعتماد' : 'Approved');
  const doReject  = (id: number) => act(() => rejectEvidenceItem(id),  isAr ? 'تم الرفض' : 'Rejected');
  function doDelete(id: number) {
    if (!window.confirm(isAr ? 'حذف هذا الدليل؟' : 'Delete this evidence item?')) return;
    void act(() => deleteEvidenceItem(id), isAr ? 'تم الحذف' : 'Deleted');
  }

  // ── Link a real evidence item to an ESG entity (S-C bridge) ──────
  async function submitLink(values: Record<string, unknown>) {
    if (!linkTarget) return;
    const entityType = String(values.entityType ?? '') as SustEvidenceEntityType;
    const entityId = Number(values.entityId ?? 0);
    if (!entityType || !Number.isFinite(entityId) || entityId <= 0) {
      toast.danger(isAr ? 'النوع والمعرّف مطلوبان' : 'Entity type and id are required'); return;
    }
    try {
      await linkSustEvidence(entityType, entityId, linkTarget.id, String(values.notes ?? '').trim() || undefined);
      toast.success(isAr ? 'تم ربط الدليل بالكيان' : 'Linked to ESG entity');
      setLinkTarget(null);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Link failed'); }
  }

  const createFields: FieldDef[] = [
    { kind: 'text',     key: 'titleEn', required: true, span: 12, label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text',     key: 'titleAr', span: 12, label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'select',   key: 'evidenceType', required: true, span: 6, label: isAr ? 'النوع' : 'Type', options: [
        { value: 'DOCUMENT', label: 'Document' }, { value: 'REPORT', label: 'Report' },
        { value: 'CERTIFICATE', label: 'Certificate' }, { value: 'SPREADSHEET', label: 'Spreadsheet' },
        { value: 'SCREENSHOT', label: 'Screenshot' }, { value: 'POLICY', label: 'Policy' },
        { value: 'ATTESTATION', label: 'Attestation' }, { value: 'OTHER', label: 'Other' } ] },
    { kind: 'text',     key: 'version', span: 6, label: isAr ? 'الإصدار' : 'Version', placeholder: 'v1.0' },
    { kind: 'date',     key: 'validFrom', span: 6, label: isAr ? 'صالح من' : 'Valid from' },
    { kind: 'date',     key: 'validTo', span: 6, label: isAr ? 'صالح حتى' : 'Valid to' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2, label: isAr ? 'الوصف' : 'Description' },
    { kind: 'file',     key: 'file', span: 12, label: isAr ? 'المرفق (اختياري)' : 'Attachment (optional)',
      accept: '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.txt,.csv,.json,.xml,.zip',
      hint: isAr ? 'ارفع الملف الآن (تُحتسب بصمة SHA-256) أو لاحقاً من الجدول'
                 : 'Upload now (SHA-256 computed server-side) or later from the table' },
  ];
  const linkFields: FieldDef[] = [
    { kind: 'select', key: 'entityType', required: true, span: 12, label: isAr ? 'نوع الكيان' : 'ESG entity type',
      options: SUST_EVIDENCE_ENTITY_TYPES.map(t => ({ value: t.value, label: isAr ? t.ar : t.en })) },
    { kind: 'number', key: 'entityId', required: true, span: 6, min: 1, label: isAr ? 'معرّف الكيان' : 'Entity id' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2, label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  return (
    <div className="esg-stack">
      {/* Hidden picker — one per tab; the row's Upload button drives it. */}
      <input ref={fileRef} type="file" hidden
             onChange={ev => { const f = ev.target.files?.[0] ?? null; ev.target.value = ''; void uploadPicked(f); }} />

      {/* KPI strip */}
      <div className="kpi-grid">
        <Kpi Icon={Paperclip}    label={isAr ? 'إجمالي الأدلة' : 'Total evidence'} value={counts.total}          tone="primary" onClick={() => setStatusFilter('ALL')}            active={statusFilter === 'ALL'} />
        <Kpi Icon={CheckCircle2} label={isAr ? 'معتمدة' : 'Approved'}              value={counts.APPROVED}       tone="success" onClick={() => setStatusFilter('APPROVED')}       active={statusFilter === 'APPROVED'} />
        <Kpi Icon={Clock}        label={isAr ? 'قيد المراجعة' : 'Pending review'}  value={counts.PENDING_REVIEW} tone="info"    onClick={() => setStatusFilter('PENDING_REVIEW')} active={statusFilter === 'PENDING_REVIEW'} />
        <Kpi Icon={Pencil}       label={isAr ? 'مسودة' : 'Draft'}                  value={counts.DRAFT}          tone="neutral" onClick={() => setStatusFilter('DRAFT')}          active={statusFilter === 'DRAFT'} />
        <Kpi Icon={Trash2}       label={isAr ? 'مرفوضة' : 'Rejected'}              value={counts.REJECTED}       tone="danger"  onClick={() => setStatusFilter('REJECTED')}       active={statusFilter === 'REJECTED'} />
        <Kpi Icon={AlertCircle}  label={isAr ? 'منتهية' : 'Expired'}               value={counts.EXPIRED}        tone={counts.EXPIRED > 0 ? 'danger' : 'success'} onClick={() => setStatusFilter('EXPIRED')} active={statusFilter === 'EXPIRED'} />
      </div>

      <Card title={isAr ? 'مستودع أدلة الاستدامة' : 'Sustainability Evidence Repository'}
            Icon={Paperclip}
            action={
              <Button size="sm" variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>
                {isAr ? 'إضافة دليل' : 'Add evidence'}
              </Button>
            }>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'أدلة حقيقية عبر محرّك الأدلة الموحّد (module/evidence): أنشئ عنصراً، ارفع ملفاً حقيقياً (تُحتسب بصمة SHA-256 في الخادم)، ثم أرسله للمراجعة → اعتماد/رفض → إعادة تقديم. اربط أي دليل بكيان استدامة عبر جسر S-C.'
            : 'Real evidence via the unified engine (module/evidence): create an item, upload a real file (SHA-256 computed server-side), then Submit → Approve/Reject → resubmit. Link any item to an ESG entity via the S-C bridge.'}
        </div>

        {/* Status filter chips */}
        <div className="esg-filter-row">
          {(['ALL', 'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'NEEDS_REVISION', 'EXPIRED'] as const).map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              className={`esg-filter-btn${statusFilter === s ? ' is-active' : ''}`}>
              {s === 'ALL' ? (isAr ? 'الكل' : 'All') : evidenceItemStatusLabel(s as EvidenceItemStatus, isAr ? 'ar' : 'en')}
            </button>
          ))}
          <span className="text-2xs text-muted esg-filter-row__count">
            {filtered.length} / {items.length}
          </span>
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد أدلة — أضف أول دليل' : 'No evidence yet — add your first item'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'العنوان' : 'Title'}</th>
                    <th>{isAr ? 'النوع' : 'Type'}</th>
                    <th>{isAr ? 'الإصدار' : 'Version'}</th>
                    <th>{isAr ? 'انتهاء الصلاحية' : 'Valid to'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th className="esg-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(e => (
                    <tr key={e.id}>
                      <td className="mono text-2xs">{e.evidenceCode ?? `#${e.id}`}</td>
                      <td>
                        <div className="esg-cell-title">{isAr && e.titleAr ? e.titleAr : e.titleEn}</div>
                        {e.fileName
                          ? <div className="text-2xs text-muted mono esg-cell-sub">
                              <Paperclip size={9} className="esg-icon-inline" />
                              {e.fileName}{e.fileSize ? ` · ${Math.round(e.fileSize / 1024)} KB` : ''} · {isAr ? 'مجزّأ SHA-256' : 'SHA-256 ✓'}
                            </div>
                          : <div className="text-2xs esg-date-due-soon esg-cell-sub">{isAr ? 'لا ملف بعد' : 'no file yet'}</div>}
                      </td>
                      <td className="text-xs">{e.evidenceType ?? '—'}</td>
                      <td className="mono text-2xs">{e.version ?? '—'}</td>
                      <td className="mono text-2xs">{e.validTo ?? '—'}</td>
                      <td><Badge tone={evidenceItemStatusTone(e.status)}>{evidenceItemStatusLabel(e.status, isAr ? 'ar' : 'en')}</Badge></td>
                      <td className="esg-text-end">
                        <div className="esg-actions-row">
                          {canSubmitEvidence(e.status) && (
                            <>
                              <Button size="sm" variant="ghost" Icon={Upload}
                                title={isAr ? 'رفع ملف' : 'Upload file'} onClick={() => triggerUpload(e.id)} />
                              <Button size="sm" variant="secondary" Icon={ArrowRight} onClick={() => doSubmit(e.id)}>
                                {isAr ? 'إرسال' : 'Submit'}
                              </Button>
                            </>
                          )}
                          {e.status === 'PENDING_REVIEW' && (
                            <>
                              <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => doApprove(e.id)}>
                                {isAr ? 'اعتماد' : 'Approve'}
                              </Button>
                              <Button size="sm" variant="ghost" Icon={AlertCircle}
                                title={isAr ? 'رفض' : 'Reject'} onClick={() => doReject(e.id)} />
                            </>
                          )}
                          <Button size="sm" variant="ghost" Icon={Link2}
                            title={isAr ? 'ربط بكيان ESG' : 'Link to ESG entity'} onClick={() => setLinkTarget(e)} />
                          <Button size="sm" variant="ghost" Icon={Trash2}
                            title={isAr ? 'حذف' : 'Delete'} onClick={() => doDelete(e.id)} />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={createOpen}
        title={isAr ? 'إضافة دليل جديد' : 'Add new evidence'}
        fields={createFields}
        initial={{ titleEn: '', titleAr: '', evidenceType: 'DOCUMENT', version: '', validFrom: '', validTo: '', descriptionEn: '', file: null }}
        size="lg"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        onClose={() => setCreateOpen(false)}
        onSubmit={submitCreate}
      />
      <FormModal
        open={linkTarget !== null}
        title={isAr ? 'ربط الدليل بكيان استدامة' : 'Link evidence to an ESG entity'}
        fields={linkFields}
        initial={{ entityType: 'CmaDisclosure', entityId: '', notes: '' }}
        size="md"
        submitLabel={isAr ? 'ربط' : 'Link'}
        onClose={() => setLinkTarget(null)}
        onSubmit={submitLink}
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

// Reporting-year lifecycle: a single linear forward step plus ARCHIVED from any
// non-archived state (mirrors SustainabilityReportService.transitionYear).
const RY_FORWARD: Record<RyStatus, RyStatus | null> = {
  ACTIVE: 'DATA_COLLECTION', DATA_COLLECTION: 'REVIEW', REVIEW: 'APPROVED',
  APPROVED: 'PUBLISHED', PUBLISHED: null, ARCHIVED: null,
};
const RY_STATUS_LABEL: Record<RyStatus, { en: string; ar: string }> = {
  ACTIVE:          { en: 'Active',          ar: 'نشطة' },
  DATA_COLLECTION: { en: 'Data collection', ar: 'جمع البيانات' },
  REVIEW:          { en: 'Review',          ar: 'مراجعة' },
  APPROVED:        { en: 'Approved',        ar: 'معتمدة' },
  PUBLISHED:       { en: 'Published',       ar: 'منشورة' },
  ARCHIVED:        { en: 'Archived',        ar: 'مؤرشفة' },
};

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

  // New-reporting-year modal. The tab could pick a year, generate annexes and
  // run the report lifecycle, but had no way to open a fresh reporting year,
  // leaving createReportingYear orphaned. Memoised initial keeps a stable
  // identity for FormModal's open-reset effect.
  const [openNewYear, setOpenNewYear] = useState(false);
  const yearInitial = useMemo<RyForm>(() => ({
    year: '', reportingEntityEn: '', reportingEntityAr: '',
    periodStart: '', periodEnd: '', preparedBy: '', notes: '',
  }), []);

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

  // Open a fresh reporting year. Client validation mirrors the entity: the
  // year (a SQL SMALLINT) and the English reporting entity are required;
  // the server defaults the status to ACTIVE. On success we reload the year
  // list and focus the newly-created year.
  async function onCreateYear(v: RyForm) {
    const yearNum = Number(v.year);
    if (!Number.isInteger(yearNum) || yearNum < 2000 || yearNum > 2100) {
      toast.danger(isAr ? 'أدخل سنة صحيحة (2000–2100)' : 'Enter a valid year (2000–2100)');
      return;
    }
    const reportingEntityEn = String(v.reportingEntityEn ?? '').trim();
    if (!reportingEntityEn) {
      toast.danger(isAr ? 'اسم جهة الإبلاغ (إنجليزي) مطلوب' : 'Reporting entity (EN) is required');
      return;
    }
    try {
      const created = await createReportingYear({
        year: yearNum,
        reportingEntityEn,
        reportingEntityAr: String(v.reportingEntityAr ?? '').trim() || undefined,
        periodStart: String(v.periodStart ?? '').trim() || undefined,
        periodEnd: String(v.periodEnd ?? '').trim() || undefined,
        preparedBy: String(v.preparedBy ?? '').trim() || undefined,
        notes: String(v.notes ?? '').trim() || undefined,
      });
      toast.success(isAr ? `تم إنشاء سنة الإبلاغ ${created.year}` : `Reporting year ${created.year} created`);
      setOpenNewYear(false);
      await loadYears();
      if (created.id) { setActive(created); await loadReports(created.id); }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل إنشاء سنة الإبلاغ' : 'Create failed'));
    }
  }

  // Advance the active reporting year through its lifecycle. The server allows
  // a single linear forward step (ACTIVE -> DATA_COLLECTION -> REVIEW ->
  // APPROVED -> PUBLISHED) plus ARCHIVED from any state; the UI offers exactly
  // those so an illegal transition is never attempted.
  async function onAdvance(to: RyStatus) {
    if (!activeRy?.id) return;
    try {
      const updated = await advanceReportingYear(activeRy.id, to);
      setActive(updated);
      setYears(prev => prev.map(y => (y.id === updated.id ? updated : y)));
      toast.success(isAr ? `حالة سنة الإبلاغ → ${RY_STATUS_LABEL[to][isAr ? 'ar' : 'en']}` : `Reporting year → ${to}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تغيير الحالة' : 'Failed to advance'));
    }
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

  return (
    <div className="esg-stack">
      {/* Year picker strip */}
      <div className="row-gap-md" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="text-2xs text-muted esg-text-eyebrow">{t('annexYear')}</span>
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
        <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenNewYear(true)}>
          {isAr ? 'سنة إبلاغ جديدة' : 'New reporting year'}
        </Button>
      </div>

      {years.length === 0 && <InlineEmpty>{t('annexNoYears')}</InlineEmpty>}

      {/* Selected-year summary card */}
      {activeRy && (
        <Card title={t('annexYearTitle').replace('{y}', String(activeRy.year))} Icon={Building2}
          action={
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <Badge tone={ryStatusTone(activeRy.status)}>{activeRy.status}</Badge>
              {activeRy.status && RY_FORWARD[activeRy.status] && (
                <Button size="sm" variant="primary" onClick={() => void onAdvance(RY_FORWARD[activeRy.status!]!)}>
                  {isAr
                    ? `الانتقال إلى ${RY_STATUS_LABEL[RY_FORWARD[activeRy.status]!].ar}`
                    : `Advance to ${RY_STATUS_LABEL[RY_FORWARD[activeRy.status]!].en}`}
                </Button>
              )}
              {activeRy.status && activeRy.status !== 'ARCHIVED' && (
                <Button size="sm" variant="ghost" onClick={() => void onAdvance('ARCHIVED')}>
                  {isAr ? 'أرشفة' : 'Archive'}
                </Button>
              )}
            </div>
          }>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
            <div className="stat-tile">
              <div className="stat-tile-label">{t('annexEntity')}</div>
              <div className="esg-text-strong">
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
      {activeRy && (
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
                  <th className="esg-text-end">{t('annexColActions')}</th>
                </tr>
              </thead>
              <tbody>
                {reports.map(r => (
                  <tr key={r.id} style={r.id === previewId ? { background: 'var(--primary-50)' } : undefined}>
                    <td className="mono">{r.reportCode ?? `#${r.id}`}</td>
                    <td><Badge tone="info">{r.reportType}</Badge></td>
                    <td className="mono">{r.version}</td>
                    <td className="mono text-xs">{r.generatedAt?.slice(0, 16).replace('T', ' ')}</td>
                    <td><Badge tone={reportStatusTone(r.status)}>{statusLabel(r.status, lang)}</Badge></td>
                    <td className="esg-text-end">
                      <div className="esg-actions-row">
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
      )}

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

      <FormModal
        open={openNewYear}
        title={isAr ? 'سنة إبلاغ جديدة' : 'New reporting year'}
        size="md"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        fields={ryFields(isAr)}
        initial={yearInitial}
        onClose={() => setOpenNewYear(false)}
        onSubmit={onCreateYear}
      />
    </div>
  );
}

// All-string create-reporting-year form. `year` is a number-kind field whose
// runtime value the submit handler coerces; the rest are plain strings.
interface RyForm {
  year:              string;
  reportingEntityEn: string;
  reportingEntityAr: string;
  periodStart:       string;
  periodEnd:         string;
  preparedBy:        string;
  notes:             string;
}

function ryFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'number', key: 'year', label: isAr ? 'السنة' : 'Year', span: 4, min: 2000, max: 2100, required: true },
    { kind: 'date', key: 'periodStart', label: isAr ? 'بداية الفترة' : 'Period start', span: 4 },
    { kind: 'date', key: 'periodEnd', label: isAr ? 'نهاية الفترة' : 'Period end', span: 4 },

    { kind: 'text', key: 'reportingEntityEn', label: isAr ? 'جهة الإبلاغ (إنجليزي)' : 'Reporting entity (EN)',
      span: 6, required: true, maxLength: 255, placeholder: isAr ? 'مثلاً: TSS GRC Platform' : 'e.g. TSS GRC Platform' },
    { kind: 'text', key: 'reportingEntityAr', label: isAr ? 'جهة الإبلاغ (عربي)' : 'Reporting entity (AR)',
      span: 6, maxLength: 255, placeholder: isAr ? 'مثلاً: منصة تي إس إس للحوكمة' : 'e.g. منصة تي إس إس للحوكمة' },

    { kind: 'text', key: 'preparedBy', label: isAr ? 'أُعدّ بواسطة' : 'Prepared by', span: 12, maxLength: 255 },
    { kind: 'textarea', key: 'notes', label: isAr ? 'ملاحظات' : 'Notes', span: 12, rows: 2 },
  ];
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
  const [editRec, setEditRec] = useState<GhgEmissionRecord | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — GHG records list, aggregate, and YoY are
    // independent; loss of one shouldn't blank the GHG tab.
    const [page, aggData, yoyData] = await Promise.all([
      listGhgRecords({ year, scope, size: 200 }).catch(() => ({ content: [] as GhgEmissionRecord[], totalElements: 0 })),
      ghgAggregate(year).catch(() => null as GhgAggregate | null),
      ghgYoy(year).catch(() => null as GhgYoY | null),
    ]);
    setRows(page.content);
    setAgg(aggData);
    setYoy(yoyData);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, [year, scope]); // eslint-disable-line react-hooks/exhaustive-deps

  async function onCreate(values: Record<string, unknown>) {
    try {
      await createGhgRecord({
        reportingYear: Number(values.reportingYear ?? year),
        scope: Number(values.scope ?? 1) as GhgScope,
        category: String(values.category ?? 'STATIONARY_COMBUSTION'),
        scope3Category: values.scope3Category ? Number(values.scope3Category) : undefined,
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
  // Edit (PUT) — partial null-means-keep on the server; reportingYear and
  // scope are immutable identity (the update endpoint never reads them) so
  // they're excluded from the edit surface. emissionValueTco2e is server-
  // recomputed from activityData × emissionFactor only when both are present;
  // for the manually-entered rows here it is taken verbatim.
  async function onUpdate(values: Record<string, unknown>) {
    if (!editRec) return;
    try {
      await updateGhgRecord(editRec.id!, {
        category:           String(values.category ?? editRec.category),
        sourceNameEn:       String(values.sourceNameEn ?? '').trim(),
        sourceNameAr:       String(values.sourceNameAr ?? '').trim() || undefined,
        emissionValueTco2e: Number(values.emissionValueTco2e ?? 0),
        coveragePercent:    Number(values.coveragePercent ?? 100),
        methodology:        String(values.methodology ?? '').trim() || undefined,
        evidenceRef:        String(values.evidenceRef ?? '').trim() || undefined,
        ownerName:          String(values.ownerName ?? '').trim() || undefined,
      });
      toast.success(t('ghgUpdated'));
      setEditRec(null);
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
  async function onArchive(id: number) {
    // ARCHIVED is terminal (no transition back out), so confirm first.
    if (!window.confirm(isAr
      ? 'أرشفة هذا السجل؟ لا يمكن التراجع عن الأرشفة.'
      : 'Archive this record? Archiving cannot be undone.')) return;
    try { await archiveGhgRecord(id); toast.success(isAr ? 'تمت أرشفة السجل' : 'Record archived'); await reload(); }
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
    // V295 — GHG Protocol Scope-3 value-chain category (backend rejects it on S1/S2 rows).
    { kind: 'select',   key: 'scope3Category',     label: isAr ? 'فئة النطاق 3 (1–15)' : 'Scope-3 category (1–15)', span: 6,
      options: [
        { value: '', label: isAr ? '— لا ينطبق —' : '— n/a —' },
        { value: '1',  label: '1 · Purchased goods & services' },
        { value: '2',  label: '2 · Capital goods' },
        { value: '3',  label: '3 · Fuel & energy activities' },
        { value: '4',  label: '4 · Upstream transport' },
        { value: '5',  label: '5 · Waste in operations' },
        { value: '6',  label: '6 · Business travel' },
        { value: '7',  label: '7 · Employee commuting' },
        { value: '8',  label: '8 · Upstream leased assets' },
        { value: '9',  label: '9 · Downstream transport' },
        { value: '10', label: '10 · Processing of sold products' },
        { value: '11', label: '11 · Use of sold products' },
        { value: '12', label: '12 · End-of-life of sold products' },
        { value: '13', label: '13 · Downstream leased assets' },
        { value: '14', label: '14 · Franchises' },
        { value: '15', label: '15 · Investments' },
      ] },
    { kind: 'text',     key: 'sourceNameEn',       label: t('ghgFldSourceEn') + ' *', required: true, span: 12 },
    { kind: 'text',     key: 'sourceNameAr',       label: t('ghgFldSourceAr'),         span: 12 },
    { kind: 'number',   key: 'emissionValueTco2e', label: t('ghgFldValue') + ' *',     required: true, min: 0, span: 6 },
    { kind: 'number',   key: 'coveragePercent',    label: t('ghgFldCoverage'),         min: 0, max: 100, span: 6 },
    { kind: 'text',     key: 'methodology',        label: t('ghgFldMethodology'),      placeholder: 'GHG Protocol Corporate Standard', span: 6 },
    { kind: 'text',     key: 'evidenceRef',        label: t('ghgFldEvidence'),         span: 6 },
    { kind: 'text',     key: 'ownerName',          label: t('ghgFldOwner'),            span: 6 },
  ];

  // Edit reuses the create field defs minus the immutable identity fields
  // (reporting year + scope are fixed once a record exists).
  const EDIT_FIELDS: FieldDef[] = FIELDS.filter(f => f.key !== 'reportingYear' && f.key !== 'scope');
  const editInitial = useMemo(() => editRec ? {
    category:           editRec.category,
    sourceNameEn:       editRec.sourceNameEn,
    sourceNameAr:       editRec.sourceNameAr ?? '',
    emissionValueTco2e: Number(editRec.emissionValueTco2e),
    coveragePercent:    editRec.coveragePercent != null ? Number(editRec.coveragePercent) : 100,
    methodology:        editRec.methodology ?? '',
    evidenceRef:        editRec.evidenceRef ?? '',
    ownerName:          editRec.ownerName ?? '',
  } : {}, [editRec]);

  function pctChip(v?: number) {
    if (v == null) return '—';
    const sign = v > 0 ? '+' : '';
    return `${sign}${Number(v).toFixed(1)}%`;
  }

  return (
    <div className="esg-stack">
      {/* Year + scope filter strip */}
      <div className="row-gap-md" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="text-2xs text-muted esg-text-eyebrow">{t('ghgFilterYear')}</span>
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
        <Kpi Icon={Leaf} label={t('ghgKpiScope1')} value={agg ? `${(Number(agg.scope1Total) / 1000).toFixed(1)}k` : '—'} tone="success" onClick={() => setScope(1)} active={scope === 1} />
        <Kpi Icon={Leaf} label={t('ghgKpiScope2')} value={agg ? `${(Number(agg.scope2Total) / 1000).toFixed(1)}k` : '—'} tone="success" onClick={() => setScope(2)} active={scope === 2} />
        <Kpi Icon={Leaf} label={t('ghgKpiScope3')} value={agg ? `${(Number(agg.scope3Total) / 1000).toFixed(1)}k` : '—'} tone="warning" onClick={() => setScope(3)} active={scope === 3} />
        <Kpi Icon={Leaf} label={t('ghgKpiTotal')}  value={agg ? `${(Number(agg.grandTotal) / 1000).toFixed(1)}k` : '—'} tone="primary" onClick={() => setScope(undefined)} active={scope === undefined} />
        <Kpi Icon={CheckCircle2} label={t('ghgKpiS3Coverage')} value={agg ? `${Number(agg.scope3Coverage).toFixed(0)}%` : '—'}
          tone={agg?.scope3PartialFlag ? 'danger' : 'success'} to="/esg" />
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
              <div key={i} className="stack-xs">
                <span className="text-2xs text-muted esg-text-eyebrow">{it.label}</span>
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
                    <th className="esg-text-end">{t('ghgColValue')}</th>
                    <th className="esg-text-center">{t('ghgColCoverage')}</th>
                    <th>{t('ghgColStatus')}</th>
                    <th className="esg-text-end">{t('ghgColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={r.id}>
                      <td className="mono">{r.recordCode ?? `#${r.id}`}</td>
                      <td><Badge tone={r.scope === 1 ? 'success' : r.scope === 2 ? 'info' : 'warning'}>S{r.scope}</Badge></td>
                      <td>
                        <div className="esg-cell-title">
                          {isAr && r.sourceNameAr ? r.sourceNameAr : r.sourceNameEn}
                        </div>
                        {r.facility && <div className="text-2xs text-muted esg-cell-sub">{r.facility}</div>}
                      </td>
                      <td className="text-xs">{r.category}</td>
                      <td className="mono esg-text-end">{Number(r.emissionValueTco2e).toLocaleString()}</td>
                      <td className="esg-text-center">
                        <Badge tone={Number(r.coveragePercent) >= 90 ? 'success' : Number(r.coveragePercent) >= 70 ? 'info' : 'warning'}>
                          {Number(r.coveragePercent).toFixed(0)}%
                        </Badge>
                      </td>
                      <td><Badge tone={ghgStatusTone(r.status)}>{statusLabel(r.status, lang)}</Badge></td>
                      <td className="esg-text-end">
                        <div className="esg-actions-row">
                          {(r.status === 'DRAFT' || r.status === 'SUBMITTED') && (
                            <Button variant="ghost" size="sm" Icon={Pencil}
                              title={isAr ? 'تعديل' : 'Edit'}
                              onClick={() => setEditRec(r)} />
                          )}
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
                          {r.status !== 'ARCHIVED'  && (
                            <Button variant="ghost" size="sm" Icon={Trash2}
                              title={isAr ? 'أرشفة' : 'Archive'}
                              onClick={() => onArchive(r.id!)} />
                          )}
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

      <FormModal
        open={!!editRec}
        title={editRec ? `${isAr ? 'تعديل سجل الانبعاثات' : 'Edit emission record'} — ${editRec.recordCode ?? `#${editRec.id}`}` : ''}
        fields={EDIT_FIELDS}
        initial={editInitial}
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditRec(null)}
        onSubmit={onUpdate}
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
  const [retiringId, setRetiringId]   = useState<number | null>(null);
  const [openNew, setOpenNew]         = useState(false);
  const [editSgi, setEditSgi]         = useState<SgiC | null>(null);
  const [sweeping, setSweeping]       = useState(false);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — SGI list and aggregate load independently.
    const [pg, a] = await Promise.all([
      listSgi({ size: 200 }).catch(() => ({ content: [] as SgiC[], totalElements: 0 })),
      sgiAggregate().catch(() => null as SgiAgg | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
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

  // SGI-A — Achieve is a disclosure-grade governance action: no typed name
  // (the server stamps the authenticated principal), and it is APPROVE-guarded
  // with Rule C (achiever ≠ last editor, 409) and Rule D (evidence attached,
  // 400). A plain confirm suffices; the server's message surfaces on failure.
  async function onAchieve(id: number) {
    if (!window.confirm(isAr
      ? 'تأكيد إنجاز الالتزام؟ يتطلب دليلاً مرفقاً، ويجب أن يختلف المُعتمِد عن آخر مُحرِّر (فصل المهام).'
      : 'Confirm this commitment as achieved? Requires attached evidence, and the approver must differ from the last editor (SoD).')) return;
    try {
      await achieveSgi(id);
      toast.success(isAr ? 'تم الإنجاز' : 'Achieved');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }
  // SGI-B — run the surveillance sweep on demand (also runs daily 03:00 UTC).
  async function onSweep() {
    setSweeping(true);
    try {
      const r = await sweepSgi();
      const total = r.delayedCapas + r.milestoneCapas;
      toast.success(total === 0
        ? (isAr ? 'اكتمل الفحص — لا انحرافات جديدة' : 'Sweep complete — no new gaps')
        : (isAr
          ? `اكتمل الفحص — ${r.delayedCapas} متأخّر و${r.milestoneCapas} مرحلة فائتة`
          : `Sweep complete — ${r.delayedCapas} behind-plan, ${r.milestoneCapas} overdue-milestone CAPA(s)`));
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر الفحص' : 'Sweep failed'));
    } finally {
      setSweeping(false);
    }
  }
  async function submitRetire(values: Record<string, unknown>) {
    if (retiringId == null) return;
    const reason = String(values.reason ?? '').trim();
    if (!reason) {
      toast.danger(isAr ? 'السبب مطلوب' : 'A reason is required'); return;
    }
    try {
      await retireSgi(retiringId, reason);
      toast.info(isAr ? 'تم التقاعد' : 'Retired');
      setRetiringId(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  const retireFields: FieldDef[] = [
    // FieldDef.textarea doesn't carry a `required` flag — submitRetire
    // re-validates that the reason isn't empty before dispatch (Rule B).
    // Retirer identity is stamped from the principal, not typed (SGI-A).
    { kind: 'textarea', key: 'reason', span: 12, rows: 3,
      label: isAr ? 'سبب التقاعد (للسجل المراجعي) *' : 'Reason (for audit trail) *' },
  ];

  const SGI_SECTORS: SgiSector[] = ['ENERGY', 'INDUSTRY', 'TRANSPORT', 'WASTE', 'FORESTRY', 'WATER', 'LAND', 'OTHER'];
  const createFields: FieldDef[] = [
    { kind: 'text', key: 'commitmentCode', required: true, span: 6,
      label: isAr ? 'كود الالتزام' : 'Commitment code', placeholder: 'SGI-RENEWABLE-50' },
    { kind: 'select', key: 'sector', required: true, span: 6,
      label: isAr ? 'القطاع' : 'Sector',
      options: SGI_SECTORS.map(s => ({ value: s, label: sgiSectorLabel(s, isAr ? 'ar' : 'en') })) },
    { kind: 'text', key: 'titleEn', required: true, span: 6,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 6,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'text', key: 'unit', required: true, span: 4,
      label: isAr ? 'الوحدة' : 'Unit', placeholder: 'tCO₂e/yr, %, MW…' },
    { kind: 'number', key: 'targetValue', required: true, span: 4,
      label: isAr ? 'القيمة المستهدفة' : 'Target value' },
    { kind: 'number', key: 'targetYear', required: true, span: 4, min: 2020, max: 2100,
      label: isAr ? 'سنة الهدف' : 'Target year' },
    { kind: 'number', key: 'baselineValue', span: 4,
      label: isAr ? 'قيمة الأساس' : 'Baseline value' },
    { kind: 'number', key: 'baselineYear', span: 4, min: 2000, max: 2100,
      label: isAr ? 'سنة الأساس' : 'Baseline year' },
    { kind: 'number', key: 'currentValue', span: 4,
      label: isAr ? 'القيمة الحالية' : 'Current value' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text', key: 'nextMilestoneEn', span: 6,
      label: isAr ? 'المرحلة التالية' : 'Next milestone' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
  ];

  // Edit-commitment — the full editable definition. commitmentCode is the
  // immutable identifier and status is owned by the achieve/retire actions,
  // so neither is in the form. Numeric columns have no entity default (null),
  // so any omitted value is left untouched on save.
  const editFields: FieldDef[] = [
    { kind: 'select', key: 'sector', required: true, span: 6,
      label: isAr ? 'القطاع' : 'Sector',
      options: SGI_SECTORS.map(s => ({ value: s, label: sgiSectorLabel(s, isAr ? 'ar' : 'en') })) },
    { kind: 'text', key: 'titleEn', required: true, span: 6,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', span: 12,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'text', key: 'unit', required: true, span: 4,
      label: isAr ? 'الوحدة' : 'Unit', placeholder: 'tCO₂e/yr, %, MW…' },
    { kind: 'number', key: 'targetValue', required: true, span: 4,
      label: isAr ? 'القيمة المستهدفة' : 'Target value' },
    { kind: 'number', key: 'targetYear', required: true, span: 4, min: 2020, max: 2100,
      label: isAr ? 'سنة الهدف' : 'Target year' },
    { kind: 'number', key: 'baselineValue', span: 4,
      label: isAr ? 'قيمة الأساس' : 'Baseline value' },
    { kind: 'number', key: 'baselineYear', span: 4, min: 2000, max: 2100,
      label: isAr ? 'سنة الأساس' : 'Baseline year' },
    { kind: 'number', key: 'currentValue', span: 4,
      label: isAr ? 'القيمة الحالية' : 'Current value' },
    { kind: 'text', key: 'ownerName', span: 6,
      label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text', key: 'ownerEmail', span: 6,
      label: isAr ? 'بريد المالك' : 'Owner email' },
    { kind: 'date', key: 'nextMilestoneDate', span: 4,
      label: isAr ? 'تاريخ المرحلة التالية' : 'Next milestone date' },
    { kind: 'text', key: 'nextMilestoneEn', span: 8,
      label: isAr ? 'المرحلة التالية (إنجليزي)' : 'Next milestone (English)' },
    { kind: 'text', key: 'nextMilestoneAr', span: 12,
      label: isAr ? 'المرحلة التالية (عربي)' : 'Next milestone (Arabic)' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
    { kind: 'textarea', key: 'notesEn', span: 12, rows: 2,
      label: isAr ? 'ملاحظات (إنجليزي)' : 'Notes (English)' },
    { kind: 'textarea', key: 'notesAr', span: 12, rows: 2,
      label: isAr ? 'ملاحظات (عربي)' : 'Notes (Arabic)' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    sector:            editSgi?.sector ?? 'ENERGY',
    titleEn:           editSgi?.titleEn ?? '',
    titleAr:           editSgi?.titleAr ?? '',
    unit:              editSgi?.unit ?? '',
    targetValue:       editSgi?.targetValue != null ? String(editSgi.targetValue) : '',
    targetYear:        editSgi?.targetYear != null ? String(editSgi.targetYear) : '',
    baselineValue:     editSgi?.baselineValue != null ? String(editSgi.baselineValue) : '',
    baselineYear:      editSgi?.baselineYear != null ? String(editSgi.baselineYear) : '',
    currentValue:      editSgi?.currentValue != null ? String(editSgi.currentValue) : '',
    ownerName:         editSgi?.ownerName ?? '',
    ownerEmail:        editSgi?.ownerEmail ?? '',
    nextMilestoneDate: editSgi?.nextMilestoneDate ?? '',
    nextMilestoneEn:   editSgi?.nextMilestoneEn ?? '',
    nextMilestoneAr:   editSgi?.nextMilestoneAr ?? '',
    descriptionEn:     editSgi?.descriptionEn ?? '',
    descriptionAr:     editSgi?.descriptionAr ?? '',
    notesEn:           editSgi?.notesEn ?? '',
    notesAr:           editSgi?.notesAr ?? '',
  }), [editSgi]);

  async function submitEdit(values: Record<string, unknown>): Promise<void> {
    if (!editSgi?.id) return;
    const titleEn = String(values.titleEn ?? '').trim();
    const sector = String(values.sector ?? '').trim();
    const unit = String(values.unit ?? '').trim();
    const targetValue = Number(values.targetValue);
    const targetYear = Number(values.targetYear);
    if (!titleEn || !sector || !unit
        || !Number.isFinite(targetValue) || !Number.isFinite(targetYear)) {
      toast.danger(isAr
        ? 'العنوان والقطاع والوحدة والقيمة المستهدفة وسنة الهدف حقول مطلوبة'
        : 'Title, sector, unit, target value and target year are required');
      return;
    }
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      await updateSgi(editSgi.id, {
        sector: sector as SgiSector,
        titleEn,
        titleAr: str(values.titleAr),
        unit,
        targetValue,
        targetYear,
        baselineValue: num(values.baselineValue),
        baselineYear: num(values.baselineYear),
        currentValue: num(values.currentValue),
        ownerName: str(values.ownerName),
        ownerEmail: str(values.ownerEmail),
        nextMilestoneDate: str(values.nextMilestoneDate),
        nextMilestoneEn: str(values.nextMilestoneEn),
        nextMilestoneAr: str(values.nextMilestoneAr),
        descriptionEn: str(values.descriptionEn),
        descriptionAr: str(values.descriptionAr),
        notesEn: str(values.notesEn),
        notesAr: str(values.notesAr),
      });
      toast.success(isAr ? 'حُدّث الالتزام' : 'Commitment updated');
      setEditSgi(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث الالتزام' : 'Failed to update commitment'));
    }
  }

  async function submitCreate(values: Record<string, unknown>): Promise<void> {
    const code = String(values.commitmentCode ?? '').trim();
    const titleEn = String(values.titleEn ?? '').trim();
    const sector = String(values.sector ?? '').trim();
    const unit = String(values.unit ?? '').trim();
    const targetValue = Number(values.targetValue);
    const targetYear = Number(values.targetYear);
    if (!code || !titleEn || !sector || !unit
        || !Number.isFinite(targetValue) || !Number.isFinite(targetYear)) {
      toast.danger(isAr
        ? 'الكود والعنوان والقطاع والوحدة والقيمة المستهدفة وسنة الهدف حقول مطلوبة'
        : 'Code, title, sector, unit, target value and target year are required');
      return;
    }
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    try {
      const created = await createSgi({
        commitmentCode: code,
        sector: sector as SgiSector,
        titleEn,
        titleAr: str(values.titleAr),
        unit,
        targetValue,
        targetYear,
        baselineValue: num(values.baselineValue),
        baselineYear: num(values.baselineYear),
        currentValue: num(values.currentValue),
        ownerName: str(values.ownerName),
        nextMilestoneEn: str(values.nextMilestoneEn),
        descriptionEn: str(values.descriptionEn),
        status: 'ACTIVE',
      });
      toast.success(isAr
        ? `تمت إضافة الالتزام ${created.commitmentCode}`
        : `Commitment ${created.commitmentCode} added`);
      setOpenNew(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة الالتزام' : 'Failed to add commitment'));
    }
  }

  return (
    <div className="esg-stack">
      {/* KPI strip — tier counts + lifecycle totals */}
      <div className="kpi-grid">
        <Kpi Icon={Target}       label={isAr ? 'الالتزامات النشطة' : 'Active commitments'} value={agg?.activeTotal ?? 0}   tone="primary" to="/esg" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'على المسار' : 'On track'}                  value={agg?.byTier.ON_TRACK ?? 0} tone="success" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'مُعرَّض للخطر' : 'At risk'}                value={agg?.byTier.AT_RISK ?? 0}  tone="warning" to="/esg" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'متأخّر' : 'Delayed'}                       value={agg?.byTier.DELAYED ?? 0}  tone="danger" to="/esg" />
        <Kpi Icon={CheckCircle2} label={isAr ? 'مُنجَزة' : 'Achieved'}                     value={agg?.achievedTotal ?? 0}   tone="success" to="/esg" />
        {/* Retired is intentionally tone-less (primary default — black ink)
            so it reads as a quiet historical counter alongside the
            louder status colors. KpiTone doesn't have a "neutral". */}
        <Kpi Icon={Trash2}       label={isAr ? 'مُتقاعَدة' : 'Retired'}                   value={agg?.retiredTotal ?? 0} to="/esg" />
      </div>

      <Card title={isAr ? 'التزامات المبادرة السعودية الخضراء' : 'Saudi Green Initiative commitments'} Icon={Target}
        action={<div className="row-actions">
          <Button variant="secondary" size="sm" Icon={RefreshCw} onClick={onSweep} disabled={sweeping}>
            {sweeping ? (isAr ? 'جارٍ الفحص…' : 'Sweeping…') : (isAr ? 'فحص المراقبة' : 'Run sweep')}
          </Button>
          <Button variant="primary" size="sm" Icon={Plus} onClick={() => setOpenNew(true)}>
            {isAr ? 'التزام جديد' : 'New commitment'}
          </Button>
        </div>}>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'سجلّ الالتزامات المحفوظ مع تتبّع التقدّم، المالك، الموعد التالي، والسجل المراجعي. الإنجاز يتطلّب الوصول للهدف؛ التقاعد يتطلّب سبباً موثّقاً.'
            : 'Registered commitments with progress tracking, owner, next milestone, and audit trail. Achievement requires the target to be met; retirement requires a documented reason.'}
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : items.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد التزامات' : 'No commitments yet'}</InlineEmpty>
            : Array.from(bySector.entries()).map(([sector, sectorItems]) => (
              <div key={sector} className="mb-lg">
                <div className="esg-cma-pillar__title esg-text-eyebrow">
                  <Leaf size={12} className="esg-icon-inline" />
                  {sgiSectorLabel(sector as 'ENERGY', isAr ? 'ar' : 'en')}
                </div>
                {sectorItems.map(c => {
                  const progressPct = computeProgressPct(c);
                  const isActive = c.status === 'ACTIVE';
                  return (
                    <div key={c.id} className={`esg-sgi-card${c.status === 'ACHIEVED' ? ' esg-sgi-card--achieved' : c.status === 'RETIRED' ? ' esg-sgi-card--retired' : ''}`}>
                      <div className="esg-sgi-card__head">
                        <div>
                          <div className="esg-sgi-card__title">
                            {isAr && c.titleAr ? c.titleAr : c.titleEn}
                          </div>
                          <div className="esg-sgi-card__meta">
                            <span className="mono">{c.commitmentCode}</span>
                            {' · '}
                            {isAr ? 'الهدف' : 'Target'} {c.targetYear}
                            {c.ownerName && <> {' · '} {c.ownerName}</>}
                          </div>
                        </div>
                        <div className="esg-sgi-card__actions">
                          <Badge tone={sgiStatusTone(c.status)}>{sgiStatusLabel(c.status, isAr ? 'ar' : 'en')}</Badge>
                          {/* SGI-B — indicative tier badge (default AT_RISK factor;
                              the sweep + KPI strip are authoritative). */}
                          {(() => { const tier = computeTier(c);
                            return tier && tier !== 'ON_TRACK'
                              ? <Badge tone={sgiTierTone(tier)}>{sgiTierLabel(tier, isAr ? 'ar' : 'en')}</Badge>
                              : null; })()}
                          {isMilestoneOverdue(c) && (
                            <Badge tone="warning" title={isAr ? 'المرحلة التالية فائتة' : 'Next milestone overdue'}>
                              <Clock size={11} /> {isAr ? 'مرحلة فائتة' : 'Milestone overdue'}
                            </Badge>
                          )}
                          {isActive && (
                            <>
                              <Button size="sm" variant="ghost" Icon={Pencil}
                                title={isAr ? 'تعديل' : 'Edit'}
                                onClick={() => setEditSgi(c)} />
                              <Button size="sm" variant="secondary" Icon={CheckCircle2}
                                disabled={!canAchieve(c)}
                                title={!canAchieve(c) ? (isAr ? 'لم يصل القياس الحالي إلى الهدف بعد' : 'Current value has not met target yet') : undefined}
                                onClick={() => onAchieve(c.id!)}>
                                {isAr ? 'إنجاز' : 'Achieve'}
                              </Button>
                              <Button size="sm" variant="ghost" Icon={Trash2}
                                title={isAr ? 'تقاعد' : 'Retire'}
                                onClick={() => setRetiringId(c.id!)} />
                            </>
                          )}
                        </div>
                      </div>
                      <div className="esg-sgi-card__progress">
                        <div className="esg-sgi-card__progress-bar">
                          <div className="esg-sgi-card__progress-fill" style={{
                            width: `${Math.min(100, progressPct)}%`,
                            background: progressPct >= 75 ? 'var(--success)'
                                       : progressPct >= 40 ? 'var(--primary)'
                                       : 'var(--warning)',
                          }} />
                        </div>
                        <div className="mono esg-sgi-card__progress-label">
                          {progressPct.toFixed(1)}%
                        </div>
                      </div>
                      <div className="esg-sgi-card__values">
                        {c.baselineValue != null && <span>{isAr ? 'خط الأساس' : 'Baseline'}: <span className="mono">{Number(c.baselineValue).toLocaleString()}</span> {c.unit}</span>}
                        {c.currentValue != null && <span>{isAr ? 'الحالي' : 'Current'}: <span className="mono">{Number(c.currentValue).toLocaleString()}</span> {c.unit}</span>}
                        <span>{isAr ? 'الهدف' : 'Target'}: <span className="mono">{Number(c.targetValue).toLocaleString()}</span> {c.unit}</span>
                      </div>
                      {(isAr ? c.notesAr : c.notesEn) && (
                        <div className="esg-sgi-card__notes">
                          {isAr && c.notesAr ? c.notesAr : c.notesEn}
                        </div>
                      )}
                      {c.nextMilestoneDate && (
                        <div className="esg-sgi-card__milestone">
                          <ArrowRight size={11} />
                          <span>{isAr ? 'المرحلة التالية' : 'Next milestone'} ({c.nextMilestoneDate}):</span>
                          <span>{isAr && c.nextMilestoneAr ? c.nextMilestoneAr : c.nextMilestoneEn}</span>
                        </div>
                      )}
                      {c.status === 'RETIRED' && c.retiredReason && (
                        <div className="esg-sgi-card__retired">
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
        open={retiringId !== null}
        title={isAr ? 'تقاعد الالتزام' : 'Retire commitment'}
        fields={retireFields}
        initial={{ reason: '' }}
        size="md"
        submitLabel={isAr ? 'تقاعد' : 'Retire'}
        onClose={() => setRetiringId(null)}
        onSubmit={submitRetire}
      />
      <FormModal
        open={openNew}
        title={isAr ? 'التزام مبادرة خضراء جديد' : 'New Green-Initiative commitment'}
        fields={createFields}
        initial={{
          commitmentCode: '', sector: 'ENERGY', titleEn: '', titleAr: '',
          unit: '', targetValue: '', targetYear: 2030,
          baselineValue: '', baselineYear: '', currentValue: '',
          ownerName: '', nextMilestoneEn: '', descriptionEn: '',
        }}
        size="lg"
        submitLabel={isAr ? 'إضافة الالتزام' : 'Add commitment'}
        onClose={() => setOpenNew(false)}
        onSubmit={submitCreate}
      />

      <FormModal
        open={editSgi !== null}
        title={editSgi
          ? (isAr ? `تعديل الالتزام · ${editSgi.commitmentCode}` : `Edit commitment · ${editSgi.commitmentCode}`)
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditSgi(null)}
        onSubmit={submitEdit}
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
      <div className="stack-xs">
        <label className="esg-field-label">
          {isAr ? label.ar : label.en}
        </label>
        <div className="esg-field-row">
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
          {opts.unit && <span className="esg-field-hint">{opts.unit}</span>}
        </div>
        {opts.help && (
          <span className="esg-field-hint">
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
      <div className="stack-xs">
        <label className="esg-field-label">
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
          <span className="esg-field-hint">
            {isAr ? opts.help.ar : opts.help.en}
          </span>
        )}
      </div>
    );
  }

  return (
    <div className="esg-stack">
      <Card title={isAr ? 'إعدادات الاستدامة' : 'Sustainability settings'} Icon={Wrench}
        action={
          <div className="esg-actions-inline">
            <Button size="sm" variant="ghost" Icon={Trash2} onClick={onReset} disabled={saving}>
              {isAr ? 'إعادة للافتراضي' : 'Reset defaults'}
            </Button>
            <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={onSave} disabled={saving}>
              {saving ? (isAr ? 'يحفظ…' : 'Saving…') : (isAr ? 'حفظ' : 'Save')}
            </Button>
          </div>
        }>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'تُحدّد هذه العتبات سلوك القواعد 1 و 3 و 9 و 13 و 14 على مستوى المؤسسة. التغييرات تسري فوراً.'
            : 'These thresholds drive the runtime behaviour of Rules 1, 3, 9, 13 and 14 at the organisation level. Changes take effect immediately.'}
        </div>

        {/* Section: Rule 14 — ESG readiness bands */}
        <div className="esg-settings-section">
          <h4 className="esg-settings-section__title">
            {isAr ? 'القاعدة 14 — لافتة جاهزية الاستدامة' : 'Rule 14 — ESG readiness banner'}
          </h4>
          <div className="esg-settings-grid">
            {Num({ en: 'Warning threshold', ar: 'عتبة التحذير' }, 'esgReadinessWarningThreshold',
              { min: 0, max: 100, unit: '/ 100',
                help: { en: 'Banner appears below this score.', ar: 'تظهر اللافتة دون هذه الدرجة.' } })}
            {Num({ en: 'Critical threshold', ar: 'عتبة الحرج' }, 'esgReadinessCriticalThreshold',
              { min: 0, max: 100, unit: '/ 100',
                help: { en: 'Banner shows in danger tone below this.', ar: 'تتحوّل اللافتة إلى نغمة الخطر دون هذه الدرجة.' } })}
          </div>
        </div>

        {/* Section: Rule 1 — Scope 3 */}
        <div className="esg-settings-section">
          <h4 className="esg-settings-section__title">
            {isAr ? 'القاعدة 1 — تغطية النطاق 3' : 'Rule 1 — Scope 3 coverage'}
          </h4>
          <div className="esg-settings-grid">
            {Num({ en: 'Partial-coverage threshold', ar: 'عتبة التغطية الجزئية' }, 'scope3PartialCoverageThreshold',
              { min: 0, max: 100, step: 0.5, unit: '%',
                help: { en: 'Below this weighted coverage, GHG aggregate flags Scope 3 as Partial.', ar: 'دون هذه التغطية الموزونة، يصنّف تجميع الانبعاثات النطاق 3 كجزئي.' } })}
            {Num({ en: 'Carbon-offset cap', ar: 'سقف أرصدة الكربون' }, 'carbonOffsetCapPct',
              { min: 0, max: 100, step: 0.5, unit: '%',
                help: { en: 'Retired credits may not exceed this % of the disclosed gross reduction (REG-11 integrity).', ar: 'لا يجوز أن تتجاوز الأرصدة المسحوبة هذه النسبة من إجمالي الخفض المفصح عنه (نزاهة REG-11).' } })}
          </div>
        </div>

        {/* Section: Rule 3 — SGI at-risk */}
        <div className="esg-settings-section">
          <h4 className="esg-settings-section__title">
            {isAr ? 'القاعدة 3 — تصنيف "معرّض للخطر" لالتزامات SGI' : 'Rule 3 — SGI at-risk classification'}
          </h4>
          <div className="esg-settings-grid">
            {Num({ en: 'At-risk factor', ar: 'عامل الخطر' }, 'sgiAtRiskProgressFactor',
              { min: 0, max: 1, step: 0.05,
                help: { en: 'Commitment is "at risk" when actual progress < expected × factor (e.g. 0.70 = 70%).', ar: 'الالتزام "معرّض للخطر" عندما يكون التقدّم الفعلي < المتوقّع × العامل (مثال 0.70 = 70%).' } })}
          </div>
        </div>

        {/* Section: Rule 9 — Evidence expiry */}
        <div className="esg-settings-section">
          <h4 className="esg-settings-section__title">
            {isAr ? 'القاعدة 9 — انتهاء صلاحية الأدلة' : 'Rule 9 — Evidence expiry'}
          </h4>
          <div className="esg-settings-grid">
            {Num({ en: 'Warning window (days)', ar: 'نافذة التحذير (أيام)' }, 'evidenceExpiryWarningDays',
              { min: 0, max: 365, unit: isAr ? 'يوم' : 'days',
                help: { en: 'Evidence within this window of expiry is flagged as "expiring soon".', ar: 'الأدلة التي تنتهي خلال هذه النافذة تُعلَّم "تنتهي قريباً".' } })}
            {Text({ en: 'Sweep cron', ar: 'جدولة الفحص' }, 'evidenceSweepCron',
              { placeholder: '0 0 6 * * *',
                help: { en: 'Spring cron expression for the nightly expiry sweep (6 fields, second-level).', ar: 'تعبير cron الخاص بـ Spring للفحص الليلي (6 خانات، بمستوى الثواني).' } })}
          </div>
        </div>

        {/* Section: Rule 13 — CAPA escalation */}
        <div className="esg-settings-section esg-settings-section--last">
          <h4 className="esg-settings-section__title">
            {isAr ? 'القاعدة 13 — تصعيد الإجراءات التصحيحية' : 'Rule 13 — Corrective-action escalation'}
          </h4>
          <div className="esg-settings-grid">
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

function SurveyTab() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [surveys, setSurveys] = useState<VendorEsgSurvey[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [busyId, setBusyId] = useState<number | null>(null);

  const [editId, setEditId] = useState<number | null>(null);
  const [draftScope12, setDraftScope12] = useState<string>('');
  const [draftRenewable, setDraftRenewable] = useState<string>('');
  const [draftSaudization, setDraftSaudization] = useState<string>('');
  const [draftIso, setDraftIso] = useState(false);
  const [draftNetZero, setDraftNetZero] = useState(false);

  // S-O — the Survey tab now reads the REAL backend (kills the VENDOR_SURVEYS
  // mock). reloadKey bumps re-run the fetch after any write.
  useEffect(() => {
    let alive = true;
    setLoading(true);
    vendorEsgApi.list()
      .then(page => { if (alive) { setSurveys(page.content); setError(false); } })
      .catch(() => { if (alive) setError(true); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [reloadKey]);
  const reload = () => setReloadKey(k => k + 1);

  const numOrUndef = (s: string): number | undefined => {
    const n = parseFloat(s);
    return Number.isFinite(n) ? n : undefined;
  };

  function startEdit(s: VendorEsgSurvey) {
    setEditId(s.id);
    setDraftScope12(s.reportedScope12?.toString() ?? '');
    setDraftRenewable(s.renewablePct?.toString() ?? '');
    setDraftSaudization(s.saudizationPct?.toString() ?? '');
    setDraftIso(!!s.iso14001);
    setDraftNetZero(!!s.hasNetZeroTarget);
  }

  async function saveResponse(id: number) {
    setBusyId(id);
    try {
      await vendorEsgApi.recordResponse(id, {
        reportedScope12: numOrUndef(draftScope12),
        renewablePct: numOrUndef(draftRenewable),
        saudizationPct: numOrUndef(draftSaudization),
        iso14001: draftIso,
        hasNetZeroTarget: draftNetZero,
      });
      toast.success(t('esgSurveySavedToast'));
      setEditId(null);
      reload();
    } catch { toast.danger(t('esgSurveyErrorToast')); }
    finally { setBusyId(null); }
  }

  async function act(id: number, fn: () => Promise<unknown>, okKey: string) {
    setBusyId(id);
    try { await fn(); toast.success(t(okKey)); reload(); }
    catch { toast.danger(t('esgSurveyErrorToast')); }
    finally { setBusyId(null); }
  }

  const isResponded = (s: VendorEsgSurvey) =>
    s.status === 'RESPONDED' || s.status === 'ACCEPTED' || s.status === 'REJECTED';
  const totalReportedScope12 = surveys.reduce((acc, s) => acc + (s.reportedScope12 ?? 0), 0);
  const respondedSpend = surveys.filter(isResponded).reduce((acc, s) => acc + (s.spendSarK ?? 0), 0);
  const totalSpend = surveys.reduce((acc, s) => acc + (s.spendSarK ?? 0), 0);
  const spendCoveragePct = totalSpend > 0 ? Math.round((respondedSpend / totalSpend) * 100) : 0;
  const respondedCount = surveys.filter(isResponded).length;

  return (
    <Card title={t('esgSurveyTitle')} Icon={Truck}>
      <div className="esg-text-eyebrow-sm">
        {t('esgSurveySub')}
      </div>
      {loading && <div className="esg-text-eyebrow-sm">{t('esgSurveyLoading')}</div>}
      {error && <div className="esg-text-eyebrow-sm">{t('esgSurveyLoadError')}</div>}
      {!loading && !error && surveys.length === 0 && (
        <div className="esg-text-eyebrow-sm">{t('esgSurveyEmpty')}</div>
      )}
      {!loading && !error && surveys.length > 0 && (<>
      <div className="esg-survey-stats">
        <Stat label={t('esgSurveyKpiSpendCov')} value={`${spendCoveragePct}%`} />
        <Stat label={t('esgSurveyKpiReportedScope12')} value={`${(totalReportedScope12 / 1000).toFixed(1)}k tCO2e`} />
        <Stat label={t('esgSurveyKpiVendors')} value={`${respondedCount} / ${surveys.length}`} />
      </div>
      <table className="tbl esg-calc-tbl">
        <thead>
          <tr>
            <th>{t('esgSurveyVendor')}</th>
            <th>{t('esgSurveySpend')}</th>
            <th>{t('esgSurveyStatus')}</th>
            <th>{t('esgSurveyScore')}</th>
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
                <div style={{ fontWeight: 600 }}>{lang === 'ar' ? (s.vendorNameAr || s.vendorNameEn) : s.vendorNameEn}</div>
                <div className="mono esg-survey-row-id">{s.externalId ?? `#${s.id}`}</div>
              </td>
              <td className="mono">{s.spendSarK != null ? `${s.spendSarK.toLocaleString()}k SAR` : '—'}</td>
              <td>
                <Badge tone={SURVEY_TONE[s.status]}>{SURVEY_LABEL[s.status][lang]}</Badge>
              </td>
              {editId === s.id ? (
                <>
                  <td className="mono">—</td>
                  <td>
                    <input value={draftScope12} onChange={e => setDraftScope12(e.target.value)}
                      type="number" placeholder="tCO2e" className="mono esg-survey-input-90" style={inputStyle} />
                  </td>
                  <td>
                    <input value={draftRenewable} onChange={e => setDraftRenewable(e.target.value)}
                      type="number" placeholder="%" className="mono esg-survey-input-60" style={inputStyle} />
                  </td>
                  <td>
                    <input value={draftSaudization} onChange={e => setDraftSaudization(e.target.value)}
                      type="number" placeholder="%" className="mono esg-survey-input-60" style={inputStyle} />
                  </td>
                  <td>
                    <label className="esg-survey-checkbox">
                      <input type="checkbox" checked={draftIso} onChange={e => setDraftIso(e.target.checked)} />
                      ISO 14001
                    </label>
                    <label className="esg-survey-checkbox">
                      <input type="checkbox" checked={draftNetZero} onChange={e => setDraftNetZero(e.target.checked)} />
                      {t('esgSurveyNetZero')}
                    </label>
                  </td>
                  <td>
                    <Button size="sm" variant="primary" disabled={busyId === s.id} onClick={() => saveResponse(s.id)}>{t('esgSurveySave')}</Button>
                  </td>
                </>
              ) : (
                <>
                  <td className="mono">{s.esgScore != null ? Number(s.esgScore).toFixed(0) : '—'}</td>
                  <td className="mono">{s.reportedScope12 != null ? s.reportedScope12.toLocaleString() : '—'}</td>
                  <td className="mono">{s.renewablePct != null ? `${s.renewablePct}%` : '—'}</td>
                  <td className="mono">{s.saudizationPct != null ? `${s.saudizationPct}%` : '—'}</td>
                  <td>
                    <div className="row-tight">
                      {s.iso14001 && <Badge tone="success">ISO</Badge>}
                      {s.hasNetZeroTarget && <Badge tone="info">NZ</Badge>}
                    </div>
                  </td>
                  <td>
                    <div className="row-tight">
                      {s.status === 'DRAFT' && (
                        <Button size="sm" variant="primary" disabled={busyId === s.id}
                          onClick={() => act(s.id, () => vendorEsgApi.send(s.id), 'esgSurveySentToast')}>{t('esgSurveySend')}</Button>
                      )}
                      {(s.status === 'SENT' || s.status === 'ESCALATED') && (
                        <Button size="sm" variant="secondary" onClick={() => startEdit(s)}>{t('esgSurveyRespond')}</Button>
                      )}
                      {s.status === 'RESPONDED' && (<>
                        <Button size="sm" variant="primary" disabled={busyId === s.id}
                          onClick={() => act(s.id, () => vendorEsgApi.accept(s.id), 'esgSurveyAcceptedToast')}>{t('esgSurveyAccept')}</Button>
                        <Button size="sm" variant="secondary" disabled={busyId === s.id}
                          onClick={() => { const r = window.prompt(t('esgSurveyRejectPrompt')); if (r !== null) act(s.id, () => vendorEsgApi.reject(s.id, r), 'esgSurveyRejectedToast'); }}>{t('esgSurveyReject')}</Button>
                      </>)}
                    </div>
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      </>)}
    </Card>
  );
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="stack-xs">
      <span className="esg-stat-row__label">{label}</span>
      {children}
    </label>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="esg-stat-tile">
      <div className="esg-stat-tile__label">{label}</div>
      <div className="esg-stat-tile__value">{value}</div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '8px 10px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
};
