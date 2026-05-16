import { useEffect, useMemo, useState } from 'react';
import {
  Leaf, TrendingDown, Target, Calculator, FileText, Truck,
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
  listReportingYears, listReportsForYear, generateAnnex,
  submitReport, approveReport, publishReport, exportReport,
  ryStatusTone, reportStatusTone,
  type ReportingYear as Ry, type SustainabilityReport as SR,
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
  EMISSIONS_TIMESERIES, CURRENT_YEAR_RECORD, EMISSION_FACTORS,
  SURVEY_TONE, SURVEY_LABEL,
  aggregateEsg, yoyChange,
  loadCalcEntries, appendCalcEntry, deleteCalcEntry,
  effectiveSurveys, saveSurveyOverride,
  subscribeEsg, buildEsgAnnex,
  type CalculatorEntry, type VendorEsgSurvey,
} from '../../lib/esg';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';
import { formatSarCompact, formatSarFull } from '../../lib/format';

type EsgTab = 'overview' | 'annex' | 'capa' | 'calculator' | 'cma' | 'tcfd' | 'materiality' | 'sgi' | 'ghg' | 'climate' | 'survey';

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
          { id: 'sgi',        labelKey: 'esgTabSgi',       Icon: Target },
          { id: 'ghg',        labelKey: 'esgTabGhg',       Icon: Leaf },
          { id: 'climate',    labelKey: 'esgTabClimate',   Icon: CloudLightning },
          { id: 'survey',     labelKey: 'esgTabSurvey',    Icon: Truck },
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
      {tab === 'sgi'        && <SgiTab />}
      {tab === 'ghg'        && <GhgInventoryTab />}
      {tab === 'climate'    && <ClimateTab />}
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

// ─── Overview tab ──────────────────────────────────────────────────────────

function OverviewTab() {
  const { t } = useI18n();
  const maxTotal = Math.max(...EMISSIONS_TIMESERIES.map(r => r.scope1 + r.scope2 + r.scope3));

  return (
    <>
      <Card title={t('esgTrendTitle')} Icon={TrendingDown}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('esgTrendSub')}
        </div>
        {/* Stacked bar chart */}
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
          <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#10b981', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{t('esgKpiScope1')}</span>
          <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#0ea5e9', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{t('esgKpiScope2')}</span>
          <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#f59e0b', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{t('esgKpiScope3')}</span>
        </div>
      </Card>

      <div style={{ marginTop: 16, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <Card title={t('esgScopeBreakdownTitle')} Icon={Leaf}>
          <table className="tbl" style={{ width: '100%' }}>
            <thead>
              <tr>
                <th>{t('esgScope')}</th>
                <th>tCO2e</th>
                <th>{t('esgYoY')}</th>
                <th>{t('esgVerified')}</th>
              </tr>
            </thead>
            <tbody>
              {[1, 2, 3].map(scope => {
                const v = scope === 1 ? CURRENT_YEAR_RECORD.scope1 : scope === 2 ? CURRENT_YEAR_RECORD.scope2 : CURRENT_YEAR_RECORD.scope3;
                const yoy = yoyChange(scope as 1 | 2 | 3);
                const verified = scope === 1 ? CURRENT_YEAR_RECORD.verifiedScope1 : scope === 2 ? CURRENT_YEAR_RECORD.verifiedScope2 : CURRENT_YEAR_RECORD.verifiedScope3;
                return (
                  <tr key={scope}>
                    <td><Badge tone="primary">Scope {scope}</Badge></td>
                    <td className="mono">{v.toLocaleString()}</td>
                    <td className="mono">
                      <span style={{ color: yoy < 0 ? 'var(--success-fg)' : 'var(--danger-fg)' }}>
                        {yoy > 0 ? '+' : ''}{yoy}%
                      </span>
                    </td>
                    <td>
                      {verified
                        ? <Badge tone="success">{t('esgVerifiedYes')}</Badge>
                        : <Badge tone="warning">{t('esgVerifiedNo')}</Badge>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 10, lineHeight: 1.5 }}>
            {t('esgScope3Coverage')}: {CURRENT_YEAR_RECORD.scope3CoveragePct}%
          </div>
        </Card>

        <Card title={t('esgRegulatorTitle')} Icon={Building2}>
          <ul style={{ margin: 0, paddingInlineStart: 16, fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.7 }}>
            <li><b>CMA</b> — {t('esgRegCma')}</li>
            <li><b>SGI</b> — {t('esgRegSgi')}</li>
            <li><b>SDAIA</b> — {t('esgRegSdaia')}</li>
            <li><b>Tadawul</b> — {t('esgRegTadawul')}</li>
            <li><b>NCEMA</b> — {t('esgRegNcema')}</li>
          </ul>
        </Card>
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
      const r = await generateAnnex(activeRy.id);
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
          <div style={{ marginTop: 12 }}>
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
