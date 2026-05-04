import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { AlertTriangle, Filter, Plus, Flame, ShieldCheck, ClipboardCheck, Target, Activity, ArrowRight, Send, CheckCheck, ShieldOff, Archive, RotateCcw, X as XIcon } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
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
import { ExportMenu } from '../../components/ui/ExportMenu';
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { riskApi, ermAdvancedApi, reportingApi, ApiError, type RiskResponse, type KriDefinitionDto, type KriBreachEventDto, type RiskAppetiteStatementDto, type AppetiteBreachEventDto } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { NewRiskModal } from './NewRiskModal';
import {
  MOCK_RISKS, KRIS, KRI_LATEST_BANDS, KRI_BREACHES, KRI_DASHBOARD_STATS,
  KRI_TRENDS_BY_ID,
  APPETITES, APPETITE_BREACHES, APPETITE_DASHBOARD_STATS,
} from './mockRisks';
import { TimeSeries } from '../../components/viz/TimeSeries';
import { ActiveWorkflowsPanel } from '../../components/workflows/ActiveWorkflowsPanel';
import { RISK_WORKFLOWS } from '../../lib/mockWorkflows';
import { RISK_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { RISK_ANCHORS } from '../../lib/moduleAnchors';

type TabId = 'register' | 'heatmap' | 'kri' | 'appetite' | 'workflows';

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

function scoreToLevel(score: number): 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' {
  if (score >= 16) return 'CRITICAL';
  if (score >= 12) return 'HIGH';
  if (score >= 8)  return 'MEDIUM';
  return 'LOW';
}

const STATUS_TONES: Record<string, BadgeTone> = {
  OPEN: 'danger',
  IN_PROGRESS: 'warning',
  MITIGATE: 'info',
  TREATED: 'success',
  CLOSED: 'neutral',
};

function statusLabel(status: string): string {
  return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}

function scoreBg(s: number): string {
  if (s >= 16) return 'var(--risk-crit)';
  if (s >= 12) return 'var(--risk-vhigh)';
  if (s >= 8)  return 'var(--risk-high)';
  if (s >= 4)  return 'var(--risk-med)';
  return 'var(--risk-low)';
}
function scoreFg(s: number): string { return s >= 12 ? '#fff' : 'var(--ink-1)'; }

function ScorePill({ score }: { score: number }) {
  return <span className="score-pill" style={{ background: scoreBg(score), color: scoreFg(score) }}>{score}</span>;
}

export function RiskRegisterPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = (searchParams.get('tab') as TabId | null) ?? 'register';
  const [tab, setTabState] = useState<TabId>(initialTab === 'register' || initialTab === 'heatmap' || initialTab === 'kri' || initialTab === 'appetite' ? initialTab : 'register');
  const [filters, setFiltersState] = useState<RiskFilters>(() => readFiltersFromQs(searchParams));
  function setTab(next: TabId) {
    setTabState(next);
    setSearchParams(writeFiltersToQs(searchParams, filters, next), { replace: true });
  }
  function setFilters(updater: (prev: RiskFilters) => RiskFilters) {
    setFiltersState(prev => {
      const next = updater(prev);
      setSearchParams(writeFiltersToQs(searchParams, next, tab), { replace: true });
      return next;
    });
  }
  const [risks, setRisks] = useState<RiskResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRisk, setEditingRisk] = useState<RiskResponse | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<RiskResponse | null>(null);
  const [deleting, setDeleting] = useState(false);
  const demoMode = !isAuthenticated();

  async function load() {
    if (demoMode) { setRisks(MOCK_RISKS); return; }
    setLoading(true); setError(null);
    try {
      const page = await riskApi.list(undefined, 0, 100);
      setRisks(page.content ?? []);
    } catch {
      setError(t('riskLoadError'));
      setRisks(MOCK_RISKS);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  function startCreate() {
    setEditingRisk(null);
    setModalOpen(true);
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
  const acceptRisk      = (r: RiskResponse) => {
    if (!window.confirm(`Formally accept risk "${r.titleEn}"? This indicates senior management acknowledges and accepts this risk without further treatment.`)) return;
    runWorkflow(riskApi.acceptRisk, r, 'Risk formally accepted');
  };
  const closeRisk       = (r: RiskResponse) => runWorkflow(riskApi.closeRisk,  r, 'Risk closed');
  const reopenRisk      = (r: RiskResponse) => runWorkflow(riskApi.reopenRisk, r, 'Risk reopened');

  // ── Derived ──
  const heatData: HeatCell[] = useMemo(() => {
    const buckets = new Map<string, HeatCell>();
    for (const r of risks) {
      const key = `${r.inherentLikelihood}|${r.inherentImpact}`;
      const cur = buckets.get(key);
      if (cur) cur.count += 1;
      else buckets.set(key, { l: r.inherentLikelihood, i: r.inherentImpact, count: 1 });
    }
    return Array.from(buckets.values());
  }, [risks]);

  const filteredRisks = useMemo(() => {
    return risks.filter(r => {
      if (filters.l != null && r.inherentLikelihood !== filters.l) return false;
      if (filters.i != null && r.inherentImpact     !== filters.i) return false;
      if (filters.status   && r.status !== filters.status) return false;
      if (filters.level    && scoreToLevel(r.inherentScore) !== filters.level) return false;
      if (filters.category) {
        const cat = r.categoryNameEn ?? r.categoryName ?? '';
        if (!cat.toLowerCase().includes(filters.category.toLowerCase())) return false;
      }
      return true;
    });
  }, [risks, filters]);

  const criticalCount = risks.filter(r => r.inherentScore >= 16).length;
  const treatedCount  = risks.filter(r => r.status === 'TREATED' || r.status === 'CLOSED').length;
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
  ];

  // Columns for the Register table — DataTable, with Inherent → Residual + actions
  const cols: Column<RiskResponse>[] = [
    { header: t('riskColId'),    cell: r => r.riskCode, mono: true, width: '110px' },
    { header: t('riskColName'),  cell: r => <div style={{ fontWeight: 600, color: 'var(--ink-1)' }}>{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</div> },
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
    { header: t('riskColStatus'),cell: r => <Badge tone={STATUS_TONES[r.status] ?? 'neutral'} dot>{statusLabel(r.status)}</Badge>, width: '130px' },
    { header: '', align: 'end', width: '180px',
      cell: r => {
        const extras: Array<{ Icon: typeof Send; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (r.status === 'OPEN')         extras.push({ Icon: Send,       onClick: () => submitForReview(r), title: t('riskActSubmitReview'), tone: 'primary' });
        if (r.status === 'UNDER_REVIEW') extras.push({ Icon: CheckCheck, onClick: () => approveRisk(r),     title: t('riskActApprove'),      tone: 'success' });
        if (r.status === 'OPEN' || r.status === 'UNDER_REVIEW')
                                         extras.push({ Icon: ShieldOff,  onClick: () => acceptRisk(r),      title: t('riskActAccept'),       tone: 'accent' });
        if (r.status === 'MITIGATING' || r.status === 'TREATED' || r.status === 'ACCEPTED')
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
            <Button variant="secondary" Icon={Filter} onClick={() => toast.info(t('actionFilterHint'))}>{t('riskFilter')}</Button>
            <ExportMenu
              demoMode={demoMode} t={t} toast={toast}
              onPdf={() => reportingApi.riskRegisterPdf(lang)}
              onXlsx={() => reportingApi.riskRegisterXlsx()}
            />
            <Button variant="primary"   Icon={Plus} onClick={startCreate}>{t('riskNew')}</Button>
          </>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={AlertTriangle}   label={t('kpiTotalRisks')}    value={risks.length} tone="primary" onClick={() => setFilters(() => ({}))} />
        <Kpi Icon={Flame}           label={t('kpiCriticalRisks')} value={criticalCount} tone="danger"  onClick={() => { setFilters(prev => ({ ...prev, status: undefined })); setTab('register'); }} />
        <Kpi Icon={ShieldCheck}     label="Treated"               value={treatedCount} tone="success" onClick={() => { setFilters(prev => ({ ...prev, status: 'CLOSED' })); setTab('register'); }} />
        <Kpi Icon={ClipboardCheck}  label="Open"                  value={openCount} tone="warning"   onClick={() => { setFilters(prev => ({ ...prev, status: 'OPEN' })); setTab('register'); }} />
      </div>

      <OverviewStrip
        storageKey="overview.risk"
        processBarTitle={t('processBarRisk') ?? 'Enterprise Risk — Process Center'}
        processBarSubtitle="ISO 31000 + COSO ERM + NIST RMF — pick a process to launch it."
        postureTitle="Regulator Posture · Enterprise Risk"
        anchors={RISK_ANCHORS}
        lang={lang}
        lifecycles={[
          // ── 1. Risk Identification & Assessment ──
          // Workflow: OPEN → UNDER_ASSESSMENT → APPROVED_FOR_TREATMENT
          // Trigger: opens the New Risk modal so the user can register a
          // new risk that immediately enters the assessment workflow.
          { ...RISK_LIFECYCLES[0],
            primary:   { label: 'Start',    onClick: startCreate },
            secondary: { label: 'Register', href: '/risk?status=OPEN' } },

          // ── 2. Risk Treatment Plan ──
          // Workflow: APPROVED_FOR_TREATMENT → TREATMENT_IN_PROGRESS → MONITORING → CLOSED
          // Trigger: filters the register to risks needing treatment + jumps
          // to the register tab so the owner can pick one and submit a plan.
          { ...RISK_LIFECYCLES[1],
            primary:   { label: 'Plan',     onClick: () => { setFilters(prev => ({ ...prev, status: 'UNDER_REVIEW' })); setTab('register'); } },
            secondary: { label: 'Treatments', href: '/risk?status=TREATMENT_IN_PROGRESS' } },

          // ── 3. KRI Threshold Breach Response ──
          // Workflow: BREACH_DETECTED → ACKNOWLEDGED → ACTION_PLAN → RESOLVED
          // Trigger: opens the KRI Dashboard tab where the user can record
          // a new reading; readings that exceed thresholds raise a breach
          // event automatically.
          { ...RISK_LIFECYCLES[2],
            primary:   { label: 'Record',   onClick: () => setTab('kri') },
            secondary: { label: 'Dashboard',href: '/risk?tab=kri' } },

          // ── 4. Risk Appetite Breach Escalation ──
          // Workflow: DETECTED → INVESTIGATED → TREATMENT → WITHIN_TOLERANCE
          // Trigger: opens the Appetite Dashboard where the user can update
          // current exposure for any statement; exposure beyond tolerance
          // raises a breach event for CRO/CFO review.
          { ...RISK_LIFECYCLES[3],
            primary:   { label: 'Update',   onClick: () => setTab('appetite') },
            secondary: { label: 'Statements', href: '/risk?tab=appetite' } },

          // ── 5. Risk Acceptance Approval ──
          // Workflow: SUBMITTED → REVIEWED → APPROVED (time-bound) → EXPIRED
          // Trigger: filters the register to candidate risks. Acceptance
          // is performed via the per-row "Accept" action on each risk —
          // tile drives the user there.
          { ...RISK_LIFECYCLES[4],
            primary:   { label: 'Submit',   onClick: () => { setFilters(prev => ({ ...prev, status: 'OPEN' })); setTab('register'); } },
            secondary: { label: 'Accepted', href: '/risk?status=ACCEPTED' } },
        ]} />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'register' && (
        <>
          {hasFilters && <FilterChips filters={filters} setFilters={setFilters} t={t} totalShown={filteredRisks.length} totalAll={risks.length} />}
          <Card padded={false}>
            {error && <div style={{ padding: 12, color: 'var(--danger-fg)', background: 'var(--danger-bg)', borderBottom: '1px solid #ECCFCC', fontSize: 13 }}>{error}</div>}
            {loading
              ? <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>
              : <DataTable columns={cols} rows={filteredRisks} rowHref={r => `/risk/${r.id}`} empty={<EmptyState Icon={AlertTriangle} title={hasFilters ? t('riskNoMatchTitle') : t('riskEmpty')} sub={hasFilters ? t('riskNoMatchSub') : t('riskEmptySub')} />} />}
          </Card>
        </>
      )}

      {tab === 'heatmap' && (
        <Card title={t('riskTabHeatMap')} action={<Badge tone="primary">5 × 5</Badge>}>
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

      {tab === 'workflows' && <ActiveWorkflowsPanel
        workflows={RISK_WORKFLOWS}
        title={t('riskTabWorkflows') ?? 'Workflows'}
        sub="Active risk-management workflows: lifecycle stages, KRI breach response, appetite breach escalation."
        t={t} />}

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
    </Shell>
  );
}

// ─── KRI Dashboard (W3 — assessment §5.1 ERM #5) ────────────────────────────
const BAND_TONE: Record<string, BadgeTone> = { GREEN: 'success', AMBER: 'warning', RED: 'danger' };

function KriDashboard({ demoMode }: { demoMode: boolean; risks: RiskResponse[] }) {
  const [kris, setKris] = useState<KriDefinitionDto[]>([]);
  const [latest, setLatest] = useState<Record<number, string>>({});
  const [breaches, setBreaches] = useState<Record<number, KriBreachEventDto[]>>({});
  const [stats, setStats] = useState<{ totalKris: number; green: number; amber: number; red: number; noData: number; openBreaches: number } | null>(null);
  const [loading, setLoading] = useState(false);

  function applyMock() {
    setKris(KRIS); setStats(KRI_DASHBOARD_STATS);
    setLatest(KRI_LATEST_BANDS); setBreaches(KRI_BREACHES);
  }

  async function loadAll() {
    if (demoMode) { applyMock(); return; }
    setLoading(true);
    try {
      const [page, dash] = await Promise.all([
        ermAdvancedApi.kris(0, 100),
        ermAdvancedApi.kriDashboard(),
      ]);
      const list = page.content ?? [];
      // Backend has no KRIs yet — fall back to the demo catalogue so the
      // dashboard never reads zeros on a fresh tenant.
      if (list.length === 0) { applyMock(); return; }
      setKris(list);
      setStats(dash);
      // Pull latest reading per KRI in parallel
      const bandMap: Record<number, string> = {};
      const breachMap: Record<number, KriBreachEventDto[]> = {};
      await Promise.all(list.map(async k => {
        try {
          const [r, b] = await Promise.all([
            ermAdvancedApi.readings(k.id),
            ermAdvancedApi.breaches(k.id),
          ]);
          bandMap[k.id] = r[0]?.band ?? 'NO_DATA';
          breachMap[k.id] = b.filter(x => x.status !== 'RESOLVED');
        } catch { /* ignore per-KRI failure */ }
      }));
      setLatest(bandMap);
      setBreaches(breachMap);
    } catch { applyMock(); }
    finally { setLoading(false); }
  }

  useEffect(() => { void loadAll(); /* eslint-disable-next-line */ }, []);

  const toast = useToast();
  async function recordReading(k: KriDefinitionDto) {
    const v = window.prompt(`Enter reading for "${k.nameEn}" (${k.unit ?? ''}):`, '');
    if (v === null) return;
    const num = Number(v);
    if (Number.isNaN(num)) { toast.danger('Invalid number'); return; }
    const notes = window.prompt('Notes (optional)?', '') ?? '';
    try {
      const out = await ermAdvancedApi.recordReading(k.id, num, notes);
      toast.success(`Reading recorded — band ${out.band}` + (out.breachCreated ? ' (breach raised)' : ''));
      void loadAll();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }

  return (
    <>
      {demoMode && (
        <div style={{ marginBottom: 8, fontSize: 11, color: 'var(--ink-3)' }}>
          Demo mode · sample KRIs shown. Sign in for live thresholds and readings.
        </div>
      )}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(5, minmax(140px, 1fr))', marginBottom: 12 }}>
        <Kpi Icon={Activity}      label="Total KRIs"   value={stats?.totalKris ?? 0} tone="primary" />
        <Kpi Icon={ShieldCheck}   label="Green"        value={stats?.green ?? 0}     tone="success" />
        <Kpi Icon={AlertTriangle} label="Amber"        value={stats?.amber ?? 0}     tone="warning" />
        <Kpi Icon={Flame}         label="Red"          value={stats?.red ?? 0}       tone="danger" />
        <Kpi Icon={ClipboardCheck} label="Open breaches" value={stats?.openBreaches ?? 0} tone="accent" />
      </div>
      {loading
        ? <div style={{ padding: 24, color: 'var(--ink-3)', textAlign: 'center' }}>Loading…</div>
        : kris.length === 0
          ? <Card><div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>No KRIs defined yet.</div></Card>
          : (
            <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))' }}>
              {kris.map(k => {
                const band = latest[k.id] ?? 'NO_DATA';
                const tone = BAND_TONE[band] ?? 'neutral';
                const openBreaches = (breaches[k.id] ?? []).length;
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
  const [items, setItems] = useState<RiskAppetiteStatementDto[]>([]);
  const [breaches, setBreaches] = useState<Record<number, AppetiteBreachEventDto[]>>({});
  const [stats, setStats] = useState<{ total: number; withinTolerance: number; exceeded: number; critical: number; noExposure: number } | null>(null);
  const [loading, setLoading] = useState(false);
  const toast = useToast();

  function applyMock() {
    setItems(APPETITES); setBreaches(APPETITE_BREACHES); setStats(APPETITE_DASHBOARD_STATS);
  }

  async function loadAll() {
    if (demoMode) { applyMock(); return; }
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

  return (
    <>
      {demoMode && (
        <div style={{ marginBottom: 8, fontSize: 11, color: 'var(--ink-3)' }}>
          Demo mode · sample appetite statements shown. Sign in for live exposure tracking.
        </div>
      )}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(5, minmax(140px, 1fr))', marginBottom: 12 }}>
        <Kpi Icon={Target}        label="Statements"        value={stats?.total ?? 0} tone="primary" />
        <Kpi Icon={ShieldCheck}   label="Within tolerance"  value={stats?.withinTolerance ?? 0} tone="success" />
        <Kpi Icon={AlertTriangle} label="Exceeded"          value={stats?.exceeded ?? 0} tone="warning" />
        <Kpi Icon={Flame}         label="Critical"          value={stats?.critical ?? 0} tone="danger" />
        <Kpi Icon={ClipboardCheck} label="No exposure data" value={stats?.noExposure ?? 0} tone="info" />
      </div>
      {loading
        ? <div style={{ padding: 24, color: 'var(--ink-3)', textAlign: 'center' }}>Loading…</div>
        : items.length === 0
          ? <Card><div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>No appetite statements yet.</div></Card>
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
