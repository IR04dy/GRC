import { useEffect, useMemo, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AlertTriangle, Filter, Plus, Flame, ShieldCheck, ClipboardCheck, Target, Activity, ArrowRight, Send, CheckCheck, ShieldOff, Archive, RotateCcw, X as XIcon, GitBranch, Download } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { WorkflowQuickLaunch } from '../../components/workflows/WorkflowQuickLaunch';
import { ModuleSubmissionsPanel } from '../../components/workflows/ModuleSubmissionsPanel';
import { Tabs } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Progress } from '../../components/ui/Progress';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { ExportOptionsModal, type ExportSelection } from '../../components/ui/ExportOptionsModal';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import { SavedViewSelector } from '../../components/ui/SavedViewSelector';
import { defaultViewForPage, type SavedView } from '../../lib/savedViews';
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { riskApi, ermAdvancedApi, reportingApi, ApiError, type RiskResponse, type KriDefinitionDto, type RiskAppetiteStatementDto, type AppetiteBreachEventDto } from '../../lib/api';
import { kriApi } from '../../lib/kriEngine';
import { usePromoted } from '../../lib/promotedRecords';
import { toRiskResponse, usePromotedProvenance } from '../../lib/promotedAdapters';
import { PromotedBadge } from '../../components/intake/PromotedBadge';
import { isAuthenticated } from '../../lib/auth';
import { useI18n } from '../../lib/i18n';
import { DemoDataNotice } from '../../components/ui/DemoDataNotice';
import { statusLabel as i18nStatusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import { useLiveRefresh } from '../../lib/useLiveRefresh';
import { LivePulse } from '../../components/ui/LivePulse';
import { NewRiskModal } from './NewRiskModal';
import {
  MOCK_RISKS, KRIS, KRI_LATEST_BANDS, KRI_BREACHES, KRI_DASHBOARD_STATS,
  KRI_TRENDS_BY_ID,
  APPETITES, APPETITE_BREACHES,
} from './mockRisks';
import { TimeSeries } from '../../components/viz/TimeSeries';
import { ModuleEngineRequestsPanel } from '../../components/workflows/ModuleEngineRequestsPanel';
import { AppetiteBreachFeed } from '../../components/risk/AppetiteBreachFeed';
import { SAUDI_APPETITES } from '../../lib/riskAppetiteSaudi';
import { RISK_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { RISK_ANCHORS } from '../../lib/moduleAnchors';
import { RegulatoryAlignmentTab } from '../../components/regulatory/RegulatoryAlignmentTab';

type TabId = 'register' | 'heatmap' | 'kri' | 'appetite' | 'workflows' | 'alignment';
/** Every TabId, in one place, so a deep-link check cannot drift from the union it validates. */
const VALID_TABS: TabId[] = ['register', 'heatmap', 'kri', 'appetite', 'workflows', 'alignment'];

interface RiskFilters {
  l?: number; i?: number;       // heatmap drill-down
  status?: string;              // status chip
  category?: string;            // category chip
  level?: string;               // severity-band drill-down (CRITICAL / HIGH / MEDIUM / LOW)
}

function readFiltersFromQs(qs: URLSearchParams): RiskFilters {
  const f: RiskFilters = {};
  const l = qs.get('l'); const i = qs.get('i');
  if (l && /^[1-5]$/.test(l)) f.l = Number(l);
  if (i && /^[1-5]$/.test(i)) f.i = Number(i);
  const s = qs.get('status');   if (s) f.status = s;
  const c = qs.get('category'); if (c) f.category = c;
  const lvl = qs.get('level');  if (lvl && /^(CRITICAL|HIGH|MEDIUM|LOW)$/.test(lvl)) f.level = lvl;
  return f;
}

function writeFiltersToQs(qs: URLSearchParams, f: RiskFilters, tab: TabId): URLSearchParams {
  const next = new URLSearchParams(qs);
  if (f.l != null) next.set('l', String(f.l));         else next.delete('l');
  if (f.i != null) next.set('i', String(f.i));         else next.delete('i');
  if (f.status)    next.set('status', f.status);       else next.delete('status');
  if (f.category)  next.set('category', f.category);   else next.delete('category');
  if (f.level)     next.set('level', f.level);         else next.delete('level');
  if (tab !== 'register') next.set('tab', tab); else next.delete('tab');
  return next;
}

// Bands match the server-side risk.risks.risk_level computed column
// (CRITICAL>=20 / HIGH>=12 / MEDIUM>=6 / LOW>=1) so the UI-computed level
// always agrees with the backend riskLevel.
function scoreToLevel(score: number): 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' {
  if (score >= 20) return 'CRITICAL';
  if (score >= 12) return 'HIGH';
  if (score >= 6)  return 'MEDIUM';
  return 'LOW';
}

// Operating ERM view = residual (post-control) exposure, falling back to
// inherent when a risk has not yet been re-assessed (residual null), matching
// the server's COALESCE(residual, inherent) banding.
function effLikelihood(r: RiskResponse): number { return r.residualLikelihood ?? r.inherentLikelihood; }
function effImpact(r: RiskResponse): number { return r.residualImpact ?? r.inherentImpact; }
function effScore(r: RiskResponse): number { return r.residualScore ?? r.inherentScore; }

/** ⚠ F2-164 — keyed on `CK_risks_status`. Three of the five keys here were invented
 *  vocabulary (`IN_PROGRESS`, `MITIGATE`, `TREATED`) and three real statuses were
 *  absent, so an UNDER_REVIEW risk — 30 of the 71 rows in dev, the largest single
 *  bucket — fell through to `?? 'neutral'`. The detail page's table was wrong the same
 *  way with different wrong words, which is how two screens disagreed about one risk. */
const STATUS_TONES: Record<string, BadgeTone> = {
  OPEN: 'danger',
  UNDER_REVIEW: 'warning',
  MITIGATING: 'info',
  ACCEPTED: 'accent',
  TRANSFERRED: 'primary',
  CLOSED: 'success',
  ARCHIVED: 'neutral',
};

// Replaced by lib/statusLabels.statusLabel — kept signature for callers
// that still pass `string`. Pages access lang via useI18n() at the call
// site so we don't need to thread it through here.

function scoreBg(s: number): string {
  if (s >= 16) return 'var(--risk-crit)';
  if (s >= 12) return 'var(--risk-vhigh)';
  if (s >= 8)  return 'var(--risk-high)';
  if (s >= 4)  return 'var(--risk-med)';
  return 'var(--risk-low)';
}
// A11Y-003: white text only on the dark crit tone (s>=16). vhigh (#D87560) and
// lighter tones use dark ink (#15201D = 5.28:1+) — white on vhigh was 3.16:1.
function scoreFg(s: number): string { return s >= 16 ? '#fff' : 'var(--ink-1)'; }

function ScorePill({ score }: { score: number }) {
  return <span className="score-pill" style={{ background: scoreBg(score), color: scoreFg(score) }}>{score}</span>;
}

export function RiskRegisterPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  // Derived from the URL, not latched from it. The previous form read ?tab= into `useState`, whose
  // initialiser runs once — and React Router does not remount on a query-string-only change, so a
  // deep link arriving while this page was already open was read and thrown away. `/risk?tab=appetite`
  // is linked from the approvals queue and from appetite-breach notifications, so the click looked
  // dead whenever the user was already on the risk register. Same defect and same fix as
  // ChangesPage; PrivacyPage has always derived, which is the convention being restored here.
  //
  // ⚠ The old validation listed only four of the six TabIds, so `/risk?tab=workflows` and
  // `?tab=alignment` were rejected on arrival and fell back to the register — even on a fresh load,
  // and even though both tabs exist and `moduleLifecycles.ts` links to the first. Validating against
  // the declared union instead of a hand-copied subset is why VALID_TABS is derived from one list.
  const urlTab = searchParams.get('tab') as TabId | null;
  const tab: TabId = urlTab && VALID_TABS.includes(urlTab) ? urlTab : 'register';
  const [filters, setFiltersState] = useState<RiskFilters>(() => readFiltersFromQs(searchParams));
  function setTab(next: TabId) {
    setSearchParams(writeFiltersToQs(searchParams, filters, next), { replace: true });
  }
  function setFilters(updater: (prev: RiskFilters) => RiskFilters) {
    setFiltersState(prev => {
      const next = updater(prev);
      setSearchParams(writeFiltersToQs(searchParams, next, tab), { replace: true });
      return next;
    });
  }

  // ── Sprint-4 §2.5 Saved Views ──────────────────────────────────────────
  // The Risk Register is URL-driven (tab + heatmap drill-down + chip
  // filters), so a "view" is just a snapshot of the search-params string.
  // Saving captures the current combination; applying restores it AND
  // re-syncs the local `tab` / `filters` state so the table re-renders.
  // Pinning a view as default auto-applies on next visit (only when the
  // URL is clean — a Hub drill-down or risk?status=OPEN deep link wins).
  const PAGE_KEY = 'risk/register';
  const [activeViewId, setActiveViewId] = useState<string | undefined>(undefined);

  function applyView(v: SavedView): void {
    setActiveViewId(v.id);
    const params = new URLSearchParams(v.urlQuery ?? '');
    setSearchParams(params, { replace: true });
    // No tab re-sync needed: `tab` is derived from the URL, so rewriting the URL *is* the update.
    // The old comment here ("setSearchParams only rewrites the URL") described the bug rather than
    // a constraint — a saved view had to hand-restore the tab because the tab was latched in state.
    setFiltersState(readFiltersFromQs(params));
  }

  // Auto-apply default view on first mount if the URL is clean.
  useEffect(() => {
    if (searchParams.toString() !== '') return;     // URL drill-down wins
    const def = defaultViewForPage(PAGE_KEY);
    if (def) applyView(def);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const [apiRisks, setRisks] = useState<RiskResponse[]>([]);
  // DD-71: include intake-promoted risks in the visible register.
  const promotedRisks = usePromoted('RISK').map(toRiskResponse);
  const risks = useMemo(() => [...promotedRisks, ...apiRisks], [promotedRisks, apiRisks]);
  const provenance = usePromotedProvenance('RISK');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRisk, setEditingRisk] = useState<RiskResponse | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<RiskResponse | null>(null);
  const [deleting, setDeleting] = useState(false);
  // F2-98: reactive. The failure branch below already surfaces `riskLoadError` — but an error
  // toast says the *load* failed, not that the rows still on screen are invented. Both facts
  // are true and the user needs the second one; the hook's own convention (`enabled: !isDemo`)
  // then also stops live refresh from ticking over fabricated rows.
  const [demoMode, setDemoMode] = useState(!isAuthenticated());

  // ── DD-48: filter modal — drives the existing RiskFilters via URL ─────
  const [filterOpen, setFilterOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const riskFilterFields: FilterField[] = [
    { key: 'status',   label: t('filterFieldStatus'),   kind: 'select', options: distinctValues(risks, 'status') },
    { key: 'category', label: t('filterFieldCategory'), kind: 'select', options: distinctValues(risks.filter(r => r.categoryNameEn), 'categoryNameEn') },
    { key: 'level',    label: t('filterFieldRisk'),     kind: 'select',
      options: [{value:'LOW',label:'LOW'},{value:'MEDIUM',label:'MEDIUM'},{value:'HIGH',label:'HIGH'},{value:'CRITICAL',label:'CRITICAL'}] },
  ];
  const riskFilterValues: Record<string, string | undefined> = {
    status:   filters.status,
    category: filters.category,
    level:    filters.level,
  };

  async function load() {
    if (!isAuthenticated()) { setRisks(MOCK_RISKS); setDemoMode(true); return; }
    setLoading(true); setError(null);
    try {
      const page = await riskApi.list(undefined, 0, 100);
      setRisks(page.content ?? []);
      setDemoMode(false);
    } catch {
      setError(t('riskLoadError'));
      setRisks(MOCK_RISKS);
      setDemoMode(true);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  // Sprint 4 §2.7 — live risk register. Subscribes to risk + treatment_plan
  // transitions; bumps stats / heatmap / register through a single debounced
  // reload.
  const { pulse } = useLiveRefresh({
    kinds: ['changed.risk', 'changed.treatment_plan', 'changed.kri_breach'],
    onRefresh: () => { load(); },
    waitMs: 600,
    enabled: !demoMode,
  });

  function startCreate() {
    // DD-76: route new risks through the Services Center intake form so
    // they get a reference code, SLA, and triage routing instead of being
    // created in-place on the register.
    navigate('/intake/risk_issue');
  }
  function startEdit(risk: RiskResponse) {
    setEditingRisk(risk);
    setModalOpen(true);
  }
  function startDelete(risk: RiskResponse) {
    setConfirmDelete(risk);
  }
  async function executeDelete() {
    if (!confirmDelete) return;
    setDeleting(true);
    try {
      if (!demoMode) await riskApi.remove(confirmDelete.id);
      setRisks(prev => prev.filter(r => r.id !== confirmDelete.id));
      toast.success('Risk deleted');
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Could not delete the risk');
    } finally {
      setDeleting(false);
      setConfirmDelete(null);
    }
  }
  function onSaved(saved: RiskResponse) {
    setRisks(prev => {
      const idx = prev.findIndex(r => r.id === saved.id);
      if (idx >= 0) {
        const next = [...prev];
        next[idx] = saved;
        return next;
      }
      return [saved, ...prev];
    });
  }

  // ── Risk lifecycle workflow handlers (ISO 31000 / COSO ERM) ──
  async function runWorkflow(
    fn: (id: number) => Promise<RiskResponse>,
    risk: RiskResponse,
    successMsg: string,
  ) {
    try {
      const updated = await fn(risk.id);
      setRisks(prev => prev.map(r => r.id === updated.id ? updated : r));
      toast.success(successMsg);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Workflow action failed');
    }
  }
  const submitForReview = (r: RiskResponse) => runWorkflow(riskApi.submitForReview, r, 'Risk submitted for review');
  const approveRisk     = (r: RiskResponse) => runWorkflow(riskApi.approveRisk,     r, 'Risk approved for treatment');
  // F2-164 — this row action used to POST /risks/{id}/accept: a bare RISK:WRITE flip
  // to ACCEPTED behind a window.confirm whose own words ("senior management
  // acknowledges and accepts this risk") described a governance decision the request
  // recorded nothing about — no justification, no compensating controls, no expiry,
  // no sign-off, and no acceptance record for the expiry sweep to ever reopen. It
  // also exempted the risk from the mandatory-treatment close gate. It now opens the
  // acceptance wizard, whose approval step is RISK:APPROVE.
  const acceptRisk      = (r: RiskResponse) => navigate(`/workflows/erm/risk-acceptance/${r.id}`);
  const closeRisk       = (r: RiskResponse) => runWorkflow(riskApi.closeRisk,  r, 'Risk closed');
  const reopenRisk      = (r: RiskResponse) => runWorkflow(riskApi.reopenRisk, r, 'Risk reopened');

  // ── Derived ──
  const heatData: HeatCell[] = useMemo(() => {
    const buckets = new Map<string, HeatCell>();
    for (const r of risks) {
      const l = effLikelihood(r), i = effImpact(r);
      const key = `${l}|${i}`;
      const cur = buckets.get(key);
      if (cur) cur.count += 1;
      else buckets.set(key, { l, i, count: 1 });
    }
    return Array.from(buckets.values());
  }, [risks]);

  const filteredRisks = useMemo(() => {
    return risks.filter(r => {
      if (filters.l != null && effLikelihood(r) !== filters.l) return false;
      if (filters.i != null && effImpact(r)     !== filters.i) return false;
      if (filters.status   && r.status !== filters.status) return false;
      if (filters.level    && scoreToLevel(effScore(r)) !== filters.level) return false;
      if (filters.category) {
        const cat = r.categoryNameEn ?? r.categoryName ?? '';
        if (!cat.toLowerCase().includes(filters.category.toLowerCase())) return false;
      }
      return true;
    });
  }, [risks, filters]);

  const criticalCount = risks.filter(r => effScore(r) >= 20).length;
  // ⚠ F2-164 — this counted 'TREATED' too, which `CK_risks_status` has never permitted
  // (OPEN | UNDER_REVIEW | MITIGATING | ACCEPTED | TRANSFERRED | CLOSED | ARCHIVED), so the
  // clause was dead and the KPI has always been the CLOSED count. Kept as the CLOSED count
  // rather than folded into MITIGATING: 'mitigating' is treatment in flight, not treated.
  const treatedCount  = risks.filter(r => r.status === 'CLOSED').length;
  const openCount     = risks.filter(r => r.status === 'OPEN').length;

  function onHeatCellClick(l: number, i: number) {
    // Toggle: clicking the same cell again clears it
    const same = filters.l === l && filters.i === i;
    setFilters(prev => same ? { ...prev, l: undefined, i: undefined } : { ...prev, l, i });
    if (!same) setTab('register');
  }
  const hasFilters = filters.l != null || filters.i != null || !!filters.status || !!filters.category || !!filters.level;

  const tabs = [
    { id: 'register'  as const, label: t('riskTabRegister') },
    { id: 'heatmap'   as const, label: t('riskTabHeatMap') },
    { id: 'kri'       as const, label: t('riskTabKri') },
    { id: 'appetite'  as const, label: t('riskTabAppetite') },
    { id: 'workflows' as const, label: t('riskTabWorkflows') ?? 'Workflows' },
    { id: 'alignment' as const, label: lang === 'ar' ? 'التوافق التنظيمي' : 'Regulatory alignment' },
  ];

  // Columns for the Register table — DataTable, with Inherent → Residual + actions
  const cols: Column<RiskResponse>[] = [
    { header: t('riskColId'),    cell: r => r.riskCode, mono: true, width: '110px' },
    { header: t('riskColName'),  cell: r => <div style={{ fontWeight: 600, color: 'var(--ink-1)' }}>{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}{provenance.has(r.id) && <PromotedBadge sourceReferenceCode={provenance.get(r.id)!} />}</div> },
    { header: t('riskColCategory'), cell: r => <Badge tone="info">{(lang === 'ar' && r.categoryNameAr) ? r.categoryNameAr : (r.categoryNameEn ?? r.categoryName ?? '—')}</Badge> },
    { header: t('riskColOwner'), cell: r => <span style={{ color: 'var(--ink-2)' }}>{r.ownerFullNameEn ?? r.ownerName ?? '—'}</span> },
    { header: t('riskColInherent'), cell: r => <ScorePill score={r.inherentScore} />, align: 'center', width: '90px' },
    { header: t('riskColResidual'), cell: r => r.residualScore != null
        ? (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
            <ScorePill score={r.residualScore} />
            <ArrowRight size={11} color="var(--ink-4)" style={{ opacity: 0.5 }} />
          </span>
        ) : <span style={{ color: 'var(--ink-4)' }} className="mono">—</span>,
      align: 'center', width: '110px' },
    { header: t('riskColStatus'),cell: r => <Badge tone={STATUS_TONES[r.status] ?? 'neutral'} dot>{i18nStatusLabel(r.status, lang)}</Badge>, width: '130px' },
    { header: '', align: 'end', width: '200px',
      cell: r => {
        const extras: Array<{ Icon: typeof Send; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        // Sprint 11 — open the Risk-Control-Finding pivot view in a new tab.
        extras.push({
          Icon: GitBranch,
          onClick: () => navigate(`/governance/pivot/risk/${r.id}`),
          title: lang === 'ar' ? 'محور المخاطر والضوابط والنتائج' : 'Open pivot view',
          tone: 'accent',
        });
        if (r.status === 'OPEN')         extras.push({ Icon: Send,       onClick: () => submitForReview(r), title: t('riskActSubmitReview'), tone: 'primary' });
        if (r.status === 'UNDER_REVIEW') extras.push({ Icon: CheckCheck, onClick: () => approveRisk(r),     title: t('riskActApprove'),      tone: 'success' });
        if (r.status === 'OPEN' || r.status === 'UNDER_REVIEW')
                                         extras.push({ Icon: ShieldOff,  onClick: () => acceptRisk(r),      title: t('riskActAccept'),       tone: 'accent' });
        if (r.status === 'MITIGATING' || r.status === 'ACCEPTED')   // F2-164: 'TREATED' was a dead branch
                                         extras.push({ Icon: Archive,    onClick: () => closeRisk(r),       title: t('riskActClose'),        tone: 'primary' });
        if (r.status === 'CLOSED')       extras.push({ Icon: RotateCcw,  onClick: () => reopenRisk(r),      title: t('riskActReopen'),       tone: 'accent' });
        return <RowActions extras={extras} onEdit={() => startEdit(r)} onDelete={() => startDelete(r)} />;
      } },
  ];

  return (
    <Shell title={t('modRisk')} sub="ISO 31000 + COSO ERM">
      <PageHeader
        eyebrow={t('riskEyebrow')}
        title={t('riskTitle')}
        sub={t('riskSub')}
        actions={
          <>
            {demoMode && <Badge tone="warning">{t('demoMode')}</Badge>}
            {!demoMode && <LivePulse pulse={pulse} />}
            <SavedViewSelector
              pageKey={PAGE_KEY}
              activeViewId={activeViewId}
              onApply={applyView}
              onSnapshot={() => ({
                name: '',     // user picks via the dialog prompt
                urlQuery: searchParams.toString(),
              })} />
            <Button variant="secondary" Icon={Filter} onClick={() => setFilterOpen(true)}>
              {t('riskFilter')}{Object.values(riskFilterValues).filter(Boolean).length > 0 ? ` · ${Object.values(riskFilterValues).filter(Boolean).length}` : ''}
            </Button>
            <Button variant="secondary" Icon={Download} disabled={demoMode}
              onClick={() => setExportOpen(true)}
              title={demoMode ? t('exportSignInRequired') : t('riskExport')}>
              {t('riskExport')}
            </Button>
            <Button variant="primary"   Icon={Plus} onClick={startCreate}>{t('riskNew')}</Button>
          </>
        }
      />
      <WorkflowQuickLaunch modules="ERM" />
      <ModuleSubmissionsPanel module="ERM" />

      <div className="kpi-grid">
        <Kpi Icon={AlertTriangle}   label={t('kpiTotalRisks')}    value={risks.length} tone="primary" onClick={() => setFilters(() => ({}))} />
        <Kpi Icon={Flame}           label={t('kpiCriticalRisks')} value={criticalCount} tone="danger"  onClick={() => { setFilters(prev => ({ ...prev, status: undefined })); setTab('register'); }} />
        <Kpi Icon={ShieldCheck}     label={t('kpiTreated')}        value={treatedCount} tone="success" onClick={() => { setFilters(prev => ({ ...prev, status: 'CLOSED' })); setTab('register'); }} />
        <Kpi Icon={ClipboardCheck}  label={t('kpiOpen')}           value={openCount}    tone="warning" onClick={() => { setFilters(prev => ({ ...prev, status: 'OPEN' })); setTab('register'); }} />
      </div>

      <OverviewStrip
        storageKey="overview.risk"
        processBarTitle={t('processBarRisk')}
        processBarSubtitle={t('processBarRiskSub')}
        postureTitle={t('postureTitleRisk')}
        anchors={RISK_ANCHORS}
        lifecycles={[
          // ── 1. Risk Identification & Assessment ──
          // Workflow: OPEN → UNDER_REVIEW → MITIGATING  (the statuses the server emits;
          // this comment said UNDER_ASSESSMENT → APPROVED_FOR_TREATMENT, neither of which exists)
          // Trigger: opens the New Risk modal so the user can register a
          // new risk that immediately enters the assessment workflow.
          { ...RISK_LIFECYCLES[0],
            primary:   { label: 'Start',    labelAr: 'ابدأ',         onClick: startCreate },
            secondary: { label: 'Register', labelAr: 'السجل',        href: '/risk?status=OPEN' } },

          // ── 2. Risk Treatment Plan ──
          { ...RISK_LIFECYCLES[1],
            primary:   { label: 'Plan',       labelAr: 'خطة المعالجة', onClick: () => { setFilters(prev => ({ ...prev, status: 'UNDER_REVIEW' })); setTab('register'); } },
            // ⚠ F2-164 — this href read `status=TREATMENT_IN_PROGRESS`, a status the DB forbids.
            // The register passes it to `riskApi.list(status)`, so the tile always opened an
            // EMPTY register — a dead link that looks like a working one.
            secondary: { label: 'Treatments', labelAr: 'المعالجات',    href: '/risk?status=MITIGATING' } },

          // ── 3. KRI Threshold Breach Response ──
          { ...RISK_LIFECYCLES[2],
            primary:   { label: 'Record',    labelAr: 'سجّل قراءة',   onClick: () => setTab('kri') },
            secondary: { label: 'Dashboard', labelAr: 'لوحة المؤشرات', href: '/risk?tab=kri' } },

          // ── 4. Risk Appetite Breach Escalation ──
          { ...RISK_LIFECYCLES[3],
            primary:   { label: 'Update',     labelAr: 'حدّث',        onClick: () => setTab('appetite') },
            secondary: { label: 'Statements', labelAr: 'بيانات الشهية', href: '/risk?tab=appetite' } },

          // ── 5. Risk Acceptance Approval ──
          { ...RISK_LIFECYCLES[4],
            primary:   { label: 'Submit',   labelAr: 'تقديم للاعتماد', onClick: () => { setFilters(prev => ({ ...prev, status: 'OPEN' })); setTab('register'); } },
            secondary: { label: 'Accepted', labelAr: 'المُعتمدة',      href: '/risk?status=ACCEPTED' } },
        ]} />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'register' && (
        <>
          {hasFilters && <FilterChips filters={filters} setFilters={setFilters} t={t} totalShown={filteredRisks.length} totalAll={risks.length} />}
          <Card padded={false}>
            {/* Error banner — use --danger-border (added to tokens in the
              * earlier polish pass) instead of the hardcoded #ECCFCC so
              * dark theme + white-label co-vary correctly. */}
            {error && (
              <div style={{
                padding: 12, fontSize: 13,
                color: 'var(--danger-fg)',
                background: 'var(--danger-bg)',
                borderBottom: '1px solid var(--danger-border)',
              }}>{error}</div>
            )}
            {loading
              ? <InlineEmpty>{t('riskLoading')}</InlineEmpty>
              : <DataTable columns={cols} rows={filteredRisks} rowHref={r => `/risk/${r.id}`} empty={<EmptyState Icon={AlertTriangle} title={hasFilters ? t('riskNoMatchTitle') : t('riskEmpty')} sub={hasFilters ? t('riskNoMatchSub') : t('riskEmptySub')} />} />}
          </Card>
        </>
      )}

      {tab === 'heatmap' && (
        <Card title={t('riskTabHeatMap')} action={<><Badge tone="info">{t('riskHeatMapResidual')}</Badge> <Badge tone="primary">5 × 5</Badge></>}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 8 }}>{t('riskHeatMapHint')}</div>
          <HeatMap
            data={heatData}
            onCellClick={onHeatCellClick}
            selected={filters.l != null && filters.i != null ? { l: filters.l, i: filters.i } : null}
          />
        </Card>
      )}

      {tab === 'kri' && <KriDashboard demoMode={demoMode} risks={risks} />}

      {tab === 'appetite' && <AppetiteDashboard demoMode={demoMode} risks={risks} />}

      {tab === 'workflows' && <ModuleEngineRequestsPanel sourceModule="RISK" />}

      {tab === 'alignment' && <RegulatoryAlignmentTab domain="RISK" families={['Risk', 'SAMA', 'International']} />}

      <NewRiskModal
        open={modalOpen}
        existing={editingRisk}
        onClose={() => setModalOpen(false)}
        onSaved={onSaved}
        demoMode={demoMode}
      />

      <ConfirmDialog
        open={!!confirmDelete}
        destructive
        busy={deleting}
        title="Delete this risk?"
        message={confirmDelete
          ? `Risk "${confirmDelete.riskCode} · ${confirmDelete.titleEn}" will be removed. This action cannot be undone.`
          : ''}
        confirmLabel="Delete"
        onCancel={() => setConfirmDelete(null)}
        onConfirm={executeDelete}
      />
      <FilterModal open={filterOpen} fields={riskFilterFields} value={riskFilterValues}
        onApply={v => {
          setFilters(prev => ({
            ...prev,
            status:   v.status,
            category: v.category,
            level:    v.level,
          }));
          toast.success(t('filterModalApplied'));
        }}
        onClose={() => setFilterOpen(false)} />
      <ExportOptionsModal
        open={exportOpen}
        onClose={() => setExportOpen(false)}
        t={t} toast={toast}
        moduleCode="ERM" exportType="RiskRegister"
        formats={['pdf', 'docx', 'xlsx']}
        defaultLang={lang === 'ar' ? 'ar' : 'en'}
        onExport={({ format, lang: L }: ExportSelection) => {
          if (format === 'pdf')  return reportingApi.riskRegisterPdf(L);
          if (format === 'docx') return reportingApi.riskRegisterDocx(L);
          return reportingApi.riskRegisterXlsx();
        }}
      />
    </Shell>
  );
}

// ─── KRI Dashboard (W3 — assessment §5.1 ERM #5) ────────────────────────────
const BAND_TONE: Record<string, BadgeTone> = { GREEN: 'success', AMBER: 'warning', RED: 'danger' };

function KriDashboard({ demoMode }: { demoMode: boolean; risks: RiskResponse[] }) {
  const { t } = useI18n();
  const [kris, setKris] = useState<KriDefinitionDto[]>([]);
  const [latest, setLatest] = useState<Record<number, string>>({});
  // open-breach COUNT per KRI id (render only uses the count)
  const [breaches, setBreaches] = useState<Record<number, number>>({});
  const [extIdById, setExtIdById] = useState<Record<number, string>>({});
  const [stats, setStats] = useState<{ totalKris: number; green: number; amber: number; red: number; noData: number; openBreaches: number } | null>(null);
  const [loading, setLoading] = useState(false);

  // F2-98: reactive — `applyMock()` also runs when a tenant simply has no KRIs defined yet
  // (the comment below says so out loud) and when the engine call fails.
  const [showingDemo, setShowingDemo] = useState(demoMode);
  function applyMock() {
    setShowingDemo(true);
    setKris(KRIS); setStats(KRI_DASHBOARD_STATS);
    setLatest(KRI_LATEST_BANDS);
    setBreaches(Object.fromEntries(Object.entries(KRI_BREACHES)
      .map(([id, arr]) => [Number(id), arr.filter(b => b.status !== 'RESOLVED').length])));
  }

  async function loadAll() {
    if (!isAuthenticated()) { applyMock(); return; }
    setLoading(true);
    try {
      // E-E: read the canonical KRI engine (/api/v1/risk/kri/*). The definition
      // list already carries latestStatus/latestValue, so no per-KRI reading call.
      const [defs, dash] = await Promise.all([
        kriApi.listDefinitions(),
        kriApi.dashboard(),
      ]);
      // Backend has no KRIs yet — fall back to the demo catalogue so the
      // dashboard never reads zeros on a fresh tenant.
      if (defs.length === 0) { applyMock(); return; }
      // Map canonical → this panel's (legacy-shaped) view model.
      setKris(defs.map(d => ({
        id: d.id, code: d.kriCode, nameEn: d.titleEn, nameAr: d.titleAr,
        unit: d.measurementUnit, direction: d.valueDirection,
        greenMax: d.greenThreshold, amberMax: d.amberThreshold, targetValue: d.targetValue,
        frequency: d.frequency, owner: d.ownerUserId != null ? String(d.ownerUserId) : undefined,
        framework: d.clauseRef ?? undefined, isActive: d.isActive,
      })));
      setExtIdById(Object.fromEntries(defs.map(d => [d.id, d.externalId])));
      setLatest(Object.fromEntries(defs.map(d => [d.id, d.latestStatus ?? 'NO_DATA'])));
      setStats({ totalKris: dash.totalKris, green: dash.greenCount, amber: dash.amberCount,
        red: dash.redCount, noData: dash.naCount, openBreaches: dash.openBreachAmber + dash.openBreachRed });
      const bc: Record<number, number> = {};
      dash.openBreaches.forEach(b => { bc[b.kriId] = (bc[b.kriId] ?? 0) + 1; });
      setBreaches(bc);
      setShowingDemo(false);
    } catch { applyMock(); }
    finally { setLoading(false); }
  }

  useEffect(() => { void loadAll(); /* eslint-disable-next-line */ }, []);

  const toast = useToast();
  async function recordReading(k: KriDefinitionDto) {
    const ext = extIdById[k.id];
    if (!ext) { toast.danger('KRI not found'); return; }
    const v = window.prompt(`Enter reading for "${k.nameEn}" (${k.unit ?? ''}):`, '');
    if (v === null) return;
    const num = Number(v);
    if (Number.isNaN(num)) { toast.danger('Invalid number'); return; }
    const notes = window.prompt('Notes (optional)?', '') ?? '';
    try {
      const out = await kriApi.recordMeasurement(ext, { value: num, notesEn: notes || undefined });
      toast.success(`Reading recorded — status ${out.status}`);
      void loadAll();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }

  return (
    <>
      {/* F2-98: was a hard-coded English sentence ending "Sign in for live thresholds" —
          wrong language for an Arabic-primary product, and wrong advice for the signed-in
          user who is the one actually being shown sample KRIs. */}
      {showingDemo && <DemoDataNotice signedIn={isAuthenticated()} detailKey="demoNoticeKris" />}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(5, minmax(140px, 1fr))', marginBottom: 12 }}>
        <Kpi Icon={Activity}      label={t('kriKpiTotal')}        value={stats?.totalKris ?? 0}    tone="primary" to="/risk/kri/dashboard" />
        <Kpi Icon={ShieldCheck}   label={t('kriKpiGreen')}        value={stats?.green ?? 0}        tone="success" to="/risk/kri/dashboard" />
        <Kpi Icon={AlertTriangle} label={t('kriKpiAmber')}        value={stats?.amber ?? 0}        tone="warning" to="/risk/kri/dashboard" />
        <Kpi Icon={Flame}         label={t('kriKpiRed')}          value={stats?.red ?? 0}          tone="danger" to="/risk/kri/dashboard" />
        <Kpi Icon={ClipboardCheck} label={t('kriKpiOpenBreaches')} value={stats?.openBreaches ?? 0} tone="accent" to="/risk/kri/dashboard" />
      </div>
      {loading
        ? <InlineEmpty>Loading…</InlineEmpty>
        : kris.length === 0
          ? <Card><InlineEmpty>No KRIs defined yet.</InlineEmpty></Card>
          : (
            <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))' }}>
              {kris.map(k => {
                const band = latest[k.id] ?? 'NO_DATA';
                const tone = BAND_TONE[band] ?? 'neutral';
                const openBreaches = breaches[k.id] ?? 0;
                return (
                  <Card key={k.id} title={k.code ? `${k.code} · ${k.nameEn}` : k.nameEn} Icon={Activity}
                    action={<Badge tone={tone} dot>{band.replace('NO_DATA', 'No data')}</Badge>}>
                    <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 8 }}>
                      {k.framework && <span>{k.framework} · </span>}
                      {k.frequency} · {k.owner ?? '—'}
                    </div>
                    {/* 12-week trend sparkline (KRI Performance Analytics pattern) */}
                    {(KRI_TRENDS_BY_ID[k.id] ?? []).length > 0 && (
                      <div style={{ margin: '8px 0' }}>
                        <TimeSeries
                          width={300} height={60}
                          color={band === 'RED' ? 'var(--danger)' : band === 'AMBER' ? 'var(--accent-700)' : 'var(--success)'}
                          threshold={k.amberMax != null ? { value: k.amberMax, color: 'var(--accent-700)', label: `Amber ≤ ${k.amberMax}` } : undefined}
                          series={KRI_TRENDS_BY_ID[k.id].map((v, i) => ({ label: `W${i - 11 + 12}`, value: v }))} />
                      </div>
                    )}
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', justifyContent: 'space-between' }}>
                      <span>Green ≤ <span className="mono">{k.greenMax ?? '—'}</span> · Amber ≤ <span className="mono">{k.amberMax ?? '—'}</span></span>
                      <span>{k.unit ?? ''}</span>
                    </div>
                    <div style={{ marginTop: 10, display: 'flex', gap: 8, alignItems: 'center' }}>
                      <Button variant="secondary" Icon={ArrowRight} onClick={() => recordReading(k)}>Record reading</Button>
                      {openBreaches > 0 && <Badge tone="danger">{openBreaches} open breach{openBreaches > 1 ? 'es' : ''}</Badge>}
                    </div>
                  </Card>
                );
              })}
            </div>
          )
      }
    </>
  );
}

// ─── Risk Appetite Dashboard (W3 — assessment §5.1 ERM #6) ──────────────────
const APPETITE_TONE: Record<string, BadgeTone> = {
  AVERSE: 'danger', MINIMAL: 'warning', CAUTIOUS: 'accent', OPEN: 'info', HUNGRY: 'success',
};
const SEVERITY_TONE_APP: Record<string, BadgeTone> = {
  WITHIN_TOLERANCE: 'success', EXCEEDED: 'warning', CRITICAL: 'danger',
};

function AppetiteDashboard({ demoMode }: { demoMode: boolean; risks: RiskResponse[] }) {
  const { t } = useI18n();
  const [items, setItems] = useState<RiskAppetiteStatementDto[]>([]);
  const [breaches, setBreaches] = useState<Record<number, AppetiteBreachEventDto[]>>({});
  const [stats, setStats] = useState<{ total: number; withinTolerance: number; exceeded: number; critical: number; noExposure: number } | null>(null);
  const [loading, setLoading] = useState(false);
  const toast = useToast();

  // F2-98: reactive, as KriDashboard.
  const [showingDemo, setShowingDemo] = useState(demoMode);
  function applyMock() {
    setShowingDemo(true);
    // Sprint 4: extend demo with Saudi-aligned appetite library so the
    // Appetite tab reflects the regulator-cited statements that listed/
    // SAMA-supervised entities are expected to carry at board level.
    const merged = [...APPETITES, ...SAUDI_APPETITES];
    setItems(merged);
    setBreaches(APPETITE_BREACHES);
    // Recompute stats from the merged list so KPIs match what's rendered.
    let withinTolerance = 0, exceeded = 0, critical = 0, noExposure = 0;
    for (const s of merged) {
      const v = s.currentExposure;
      const upper = s.toleranceUpper;
      if (v == null || upper == null) { noExposure++; continue; }
      if (v <= upper) { withinTolerance++; continue; }
      const pct = upper === 0 ? 100 : ((v - upper) / Math.max(1, upper)) * 100;
      if (pct >= 25) critical++; else exceeded++;
    }
    setStats({ total: merged.length, withinTolerance, exceeded, critical, noExposure });
  }

  async function loadAll() {
    if (!isAuthenticated()) { applyMock(); return; }
    setLoading(true);
    try {
      const [page, dash] = await Promise.all([
        ermAdvancedApi.appetites(0, 100),
        ermAdvancedApi.appetiteDashboard(),
      ]);
      const list = page.content ?? [];
      if (list.length === 0) { applyMock(); return; }
      setItems(list);
      setStats(dash);
      const bm: Record<number, AppetiteBreachEventDto[]> = {};
      await Promise.all(list.map(async s => {
        try {
          const b = await ermAdvancedApi.appetiteBreaches(s.id);
          bm[s.id] = b.filter(x => x.status === 'OPEN');
        } catch { /* ignore */ }
      }));
      setBreaches(bm);
      setShowingDemo(false);
    } catch { applyMock(); }
    finally { setLoading(false); }
  }
  useEffect(() => { void loadAll(); /* eslint-disable-next-line */ }, []);

  function severity(s: RiskAppetiteStatementDto): string | null {
    const v = s.currentExposure;
    const upper = s.toleranceUpper;
    if (v == null || upper == null) return null;
    if (v <= upper) return 'WITHIN_TOLERANCE';
    const pct = upper === 0 ? 100 : ((v - upper) / upper) * 100;
    return pct >= 25 ? 'CRITICAL' : 'EXCEEDED';
  }

  async function updateExposure(s: RiskAppetiteStatementDto) {
    const v = window.prompt(`Update exposure for "${s.titleEn}" (${s.unit ?? ''}):`, String(s.currentExposure ?? ''));
    if (v === null) return;
    const num = Number(v);
    if (Number.isNaN(num)) { toast.danger('Invalid number'); return; }
    const notes = window.prompt('Notes (optional)?', '') ?? '';
    try { await ermAdvancedApi.updateExposure(s.id, num, notes); toast.success('Exposure updated'); void loadAll(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function approveAppetite(s: RiskAppetiteStatementDto) {
    const who = window.prompt('Approver name?', s.approvedBy ?? '');
    if (!who) return;
    try { await ermAdvancedApi.approveAppetite(s.id, who); toast.success('Approved'); void loadAll(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function retireAppetite(s: RiskAppetiteStatementDto) {
    if (!window.confirm(`Retire appetite statement "${s.titleEn}"? It will be marked RETIRED.`)) return;
    try { await ermAdvancedApi.retireAppetite(s.id); toast.success('Retired'); void loadAll(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  return (
    <>
      {showingDemo && <DemoDataNotice signedIn={isAuthenticated()} detailKey="demoNoticeAppetite" />}
      {/* Sprint 4 — Saudi-aligned breach feed driven by the SLA engine.
          Surfaces every appetite statement currently outside its tolerance
          band with regulator citation and a live SLA countdown. */}
      <div style={{ marginBottom: 12 }}>
        <AppetiteBreachFeed />
      </div>
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(5, minmax(140px, 1fr))', marginBottom: 12 }}>
        <Kpi Icon={Target}         label={t('appKpiStatements')} value={stats?.total ?? 0}            tone="primary" to="/risk?tab=appetite" />
        <Kpi Icon={ShieldCheck}    label={t('appKpiWithin')}     value={stats?.withinTolerance ?? 0} tone="success" to="/risk?tab=appetite" />
        <Kpi Icon={AlertTriangle}  label={t('appKpiExceeded')}   value={stats?.exceeded ?? 0}        tone="warning" to="/risk?tab=appetite" />
        <Kpi Icon={Flame}          label={t('appKpiCritical')}   value={stats?.critical ?? 0}        tone="danger"  to="/risk?tab=appetite" />
        <Kpi Icon={ClipboardCheck} label={t('appKpiNoData')}     value={stats?.noExposure ?? 0}      tone="info"    to="/risk?tab=appetite" />
      </div>
      {loading
        ? <InlineEmpty>Loading…</InlineEmpty>
        : items.length === 0
          ? <Card><InlineEmpty>No appetite statements yet.</InlineEmpty></Card>
          : (
            <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))' }}>
              {items.map(s => {
                const sev = severity(s);
                const sevTone = sev ? SEVERITY_TONE_APP[sev] : 'neutral';
                const used = (s.currentExposure != null && s.toleranceUpper != null && s.toleranceUpper !== 0)
                  ? Math.min(150, Math.round((s.currentExposure / s.toleranceUpper) * 100)) : 0;
                const ob = (breaches[s.id] ?? []).length;
                return (
                  <Card key={s.id} title={s.titleEn} Icon={Target}
                    action={<Badge tone={APPETITE_TONE[s.appetiteLevel] ?? 'neutral'}>{s.appetiteLevel}</Badge>}>
                    <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 8 }}>
                      {s.riskDimension} · {s.status}
                    </div>
                    <Progress label={`Exposure ${s.currentExposure ?? '—'} / ${s.toleranceUpper ?? '—'} ${s.unit ?? ''}`} value={Math.min(100, used)} tone={sev === 'CRITICAL' ? 'danger' : sev === 'EXCEEDED' ? 'accent' : 'success'} />
                    <div style={{ display: 'flex', gap: 6, marginTop: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                      {sev && <Badge tone={sevTone}>{sev.replace(/_/g, ' ')}</Badge>}
                      {ob > 0 && <Badge tone="danger">{ob} open breach{ob > 1 ? 'es' : ''}</Badge>}
                      <Button variant="secondary" Icon={ArrowRight} onClick={() => updateExposure(s)}>Update exposure</Button>
                      {s.status !== 'APPROVED' && s.status !== 'RETIRED' &&
                        <Button variant="secondary" Icon={CheckCheck} onClick={() => approveAppetite(s)}>Approve</Button>}
                      {s.status !== 'RETIRED' &&
                        <Button variant="ghost" Icon={Archive} onClick={() => retireAppetite(s)}>Retire</Button>}
                    </div>
                  </Card>
                );
              })}
            </div>
          )
      }
    </>
  );
}


function FilterChips({ filters, setFilters, t, totalShown, totalAll }: { filters: RiskFilters; setFilters: (u: (p: RiskFilters) => RiskFilters) => void; t: (k: string) => string; totalShown: number; totalAll: number }) {
  return (
    <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap', marginBottom: 8 }}>
      <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
        {t('riskFilterLabel')} ·
      </span>
      {filters.l != null && filters.i != null && (
        <Chip label={`L${filters.l} × I${filters.i}`} onClear={() => setFilters(p => ({ ...p, l: undefined, i: undefined }))} />
      )}
      {filters.status && <Chip label={`${t('riskColStatus')}: ${filters.status}`} onClear={() => setFilters(p => ({ ...p, status: undefined }))} />}
      {filters.level && <Chip label={`${t('riskColInherent')}: ${filters.level}`} onClear={() => setFilters(p => ({ ...p, level: undefined }))} />}
      {filters.category && <Chip label={`${t('riskColCategory')}: ${filters.category}`} onClear={() => setFilters(p => ({ ...p, category: undefined }))} />}
      <span style={{ fontSize: 11, color: 'var(--ink-3)', marginInlineStart: 'auto', fontFamily: 'var(--font-mono)' }}>
        {totalShown} / {totalAll}
      </span>
      <button className="lang-toggle" onClick={() => setFilters(() => ({}))} style={{ fontSize: 11 }}>
        {t('riskClearFilters')}
      </button>
    </div>
  );
}

function Chip({ label, onClear }: { label: string; onClear: () => void }) {
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '4px 8px', background: 'var(--surface-2)', border: '1px solid var(--border)',
      borderRadius: 'var(--r-pill)', fontSize: 11, fontWeight: 600,
    }}>
      {label}
      <button onClick={onClear} aria-label="Remove" style={{
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        width: 14, height: 14, padding: 0, border: 'none', background: 'transparent',
        cursor: 'pointer', color: 'var(--ink-3)',
      }}>
        <XIcon size={11} />
      </button>
    </span>
  );
}
