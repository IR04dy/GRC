import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Filter, Download, Plus, Flame, ShieldCheck, ClipboardCheck, Target, Activity, ArrowRight, Send, CheckCheck, ShieldOff, Archive, RotateCcw } from 'lucide-react';
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
import { HeatMap, type HeatCell } from '../../components/viz/HeatMap';
import { riskApi, ermAdvancedApi, ApiError, type RiskResponse, type KriDefinitionDto, type KriBreachEventDto, type RiskAppetiteStatementDto, type AppetiteBreachEventDto } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { NewRiskModal } from './NewRiskModal';
import { MOCK_RISKS } from './mockRisks';

type TabId = 'register' | 'heatmap' | 'kri' | 'appetite';

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
  const [tab, setTab] = useState<TabId>('register');
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

  const criticalCount = risks.filter(r => r.inherentScore >= 16).length;
  const treatedCount  = risks.filter(r => r.status === 'TREATED' || r.status === 'CLOSED').length;
  const openCount     = risks.filter(r => r.status === 'OPEN').length;

  const tabs = [
    { id: 'register' as const, label: t('riskTabRegister') },
    { id: 'heatmap'  as const, label: t('riskTabHeatMap') },
    { id: 'kri'      as const, label: t('riskTabKri') },
    { id: 'appetite' as const, label: t('riskTabAppetite') },
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
            <Button variant="secondary" Icon={Filter}>{t('riskFilter')}</Button>
            <Button variant="secondary" Icon={Download}>{t('riskExport')}</Button>
            <Button variant="primary"   Icon={Plus} onClick={startCreate}>{t('riskNew')}</Button>
          </>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={AlertTriangle}   label={t('kpiTotalRisks')}    value={risks.length} tone="primary" />
        <Kpi Icon={Flame}           label={t('kpiCriticalRisks')} value={criticalCount} tone="danger" />
        <Kpi Icon={ShieldCheck}     label="Treated"               value={treatedCount} tone="success" />
        <Kpi Icon={ClipboardCheck}  label="Open"                  value={openCount} tone="warning" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'register' && (
        <Card padded={false}>
          {error && <div style={{ padding: 12, color: 'var(--danger-fg)', background: 'var(--danger-bg)', borderBottom: '1px solid #ECCFCC', fontSize: 13 }}>{error}</div>}
          {loading
            ? <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>
            : <DataTable columns={cols} rows={risks} empty={<EmptyState Icon={AlertTriangle} title={t('riskEmpty')} sub={t('riskEmptySub')} />} />}
        </Card>
      )}

      {tab === 'heatmap' && (
        <Card title={t('riskTabHeatMap')} action={<Badge tone="primary">5 × 5</Badge>}>
          <HeatMap data={heatData} />
        </Card>
      )}

      {tab === 'kri' && <KriDashboard demoMode={demoMode} risks={risks} />}

      {tab === 'appetite' && <AppetiteDashboard demoMode={demoMode} risks={risks} />}

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

function KriDashboard({ demoMode, risks }: { demoMode: boolean; risks: RiskResponse[] }) {
  const [kris, setKris] = useState<KriDefinitionDto[]>([]);
  const [latest, setLatest] = useState<Record<number, string>>({});
  const [breaches, setBreaches] = useState<Record<number, KriBreachEventDto[]>>({});
  const [stats, setStats] = useState<{ totalKris: number; green: number; amber: number; red: number; noData: number; openBreaches: number } | null>(null);
  const [loading, setLoading] = useState(false);

  async function loadAll() {
    if (demoMode) {
      setKris([]); setStats({ totalKris: 0, green: 0, amber: 0, red: 0, noData: 0, openBreaches: 0 });
      return;
    }
    setLoading(true);
    try {
      const [page, dash] = await Promise.all([
        ermAdvancedApi.kris(0, 100),
        ermAdvancedApi.kriDashboard(),
      ]);
      const list = page.content ?? [];
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
    } finally { setLoading(false); }
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

  if (demoMode) {
    return (
      <Card title="KRI engine">
        <div style={{ padding: 24, color: 'var(--ink-3)', textAlign: 'center' }}>
          Sign in to view live KRI thresholds, readings and breach events.
          <div style={{ marginTop: 8, fontSize: 12 }}>Open risks (demo): {risks.filter(r => r.status === 'OPEN').length}</div>
        </div>
      </Card>
    );
  }

  return (
    <>
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

function AppetiteDashboard({ demoMode, risks }: { demoMode: boolean; risks: RiskResponse[] }) {
  const [items, setItems] = useState<RiskAppetiteStatementDto[]>([]);
  const [breaches, setBreaches] = useState<Record<number, AppetiteBreachEventDto[]>>({});
  const [stats, setStats] = useState<{ total: number; withinTolerance: number; exceeded: number; critical: number; noExposure: number } | null>(null);
  const [loading, setLoading] = useState(false);
  const toast = useToast();

  async function loadAll() {
    if (demoMode) { setItems([]); setStats({ total: 0, withinTolerance: 0, exceeded: 0, critical: 0, noExposure: 0 }); return; }
    setLoading(true);
    try {
      const [page, dash] = await Promise.all([
        ermAdvancedApi.appetites(0, 100),
        ermAdvancedApi.appetiteDashboard(),
      ]);
      const list = page.content ?? [];
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
    } finally { setLoading(false); }
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

  if (demoMode) {
    return (
      <Card title="Risk appetite">
        <div style={{ padding: 24, color: 'var(--ink-3)', textAlign: 'center' }}>
          Sign in to view live appetite statements with exposure tracking and breach events.
          <div style={{ marginTop: 8, fontSize: 12 }}>Risks in scope (demo): {risks.length}</div>
        </div>
      </Card>
    );
  }

  return (
    <>
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
