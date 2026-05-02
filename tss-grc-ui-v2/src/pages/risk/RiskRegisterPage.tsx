import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Filter, Download, Plus, Flame, ShieldCheck, ClipboardCheck, TrendingUp, TrendingDown, Target, Activity, ArrowRight, Send, CheckCheck, ShieldOff, Archive, RotateCcw } from 'lucide-react';
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
import { riskApi, ApiError, type RiskResponse } from '../../lib/api';
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

      {tab === 'kri' && <KriDashboard risks={risks} />}

      {tab === 'appetite' && <AppetiteDashboard risks={risks} />}

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

// ─── KRI Dashboard ──────────────────────────────────────────────────────────
function KriDashboard({ risks }: { risks: RiskResponse[] }) {
  const criticalCount = risks.filter(r => r.inherentScore >= 16).length;
  const openCount = risks.filter(r => r.status === 'OPEN').length;
  const kris = [
    { name: 'Critical risks open',           value: criticalCount,    threshold: 5,    unit: '',  trend: 'up',   delta: '+2'   },
    { name: 'Risks past review date',         value: 3,                threshold: 0,    unit: '',  trend: 'up',   delta: '+1'   },
    { name: 'Failed login attempts (24h)',    value: 1247,             threshold: 1000, unit: '',  trend: 'up',   delta: '+18%' },
    { name: 'Privileged access reviews due',  value: 2,                threshold: 5,    unit: '',  trend: 'down', delta: '−3'   },
    { name: 'Vendor SLA breaches (30d)',      value: 4,                threshold: 2,    unit: '',  trend: 'up',   delta: '+1'   },
    { name: 'Unpatched critical CVEs',        value: 1,                threshold: 0,    unit: '',  trend: 'down', delta: '−2'   },
    { name: 'Open risks',                     value: openCount,        threshold: 30,   unit: '',  trend: 'down', delta: '−4'   },
    { name: 'Mean time to treat (days)',      value: 14,               threshold: 21,   unit: 'd', trend: 'down', delta: '−3'   },
  ];
  return (
    <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
      {kris.map(k => {
        const breached = k.threshold > 0 && k.value > k.threshold;
        const Trend = k.trend === 'up' ? TrendingUp : TrendingDown;
        const trendBg = k.trend === 'up' ? (breached ? 'var(--danger-bg)' : 'var(--success-bg)') : 'var(--success-bg)';
        const trendFg = k.trend === 'up' ? (breached ? 'var(--danger-fg)' : 'var(--success-fg)') : 'var(--success-fg)';
        return (
          <Card key={k.name} title={k.name} Icon={Activity} action={breached ? <Badge tone="danger" dot>Breach</Badge> : <Badge tone="success" dot>OK</Badge>}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 8 }}>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 32, fontWeight: 800, color: breached ? 'var(--danger-fg)' : 'var(--ink-1)' }}>
                {k.value.toLocaleString()}{k.unit}
              </span>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, padding: '2px 8px', background: trendBg, color: trendFg, fontSize: 11, fontWeight: 700, fontFamily: 'var(--font-mono)', borderRadius: 'var(--r-pill)' }}>
                <Trend size={11} /> {k.delta}
              </span>
            </div>
            <div style={{ height: 6, background: 'var(--surface-3)', borderRadius: 'var(--r-pill)', overflow: 'hidden', marginBottom: 6 }}>
              <div style={{ width: `${Math.min(100, k.threshold > 0 ? (k.value / k.threshold) * 100 : 0)}%`, height: '100%', background: breached ? 'linear-gradient(90deg, var(--accent), var(--danger))' : 'linear-gradient(90deg, var(--primary-300), var(--primary))' }} />
            </div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', justifyContent: 'space-between' }}>
              <span>Threshold: <span className="mono">{k.threshold}{k.unit}</span></span>
              <span>{breached ? 'Above limit' : 'Within limit'}</span>
            </div>
          </Card>
        );
      })}
    </div>
  );
}

// ─── Risk Appetite Dashboard ────────────────────────────────────────────────
function AppetiteDashboard({ risks }: { risks: RiskResponse[] }) {
  const categories = ['Cyber', 'Operational', 'Compliance', 'Third-Party', 'Financial', 'Strategic'];
  const buckets = categories.map(cat => {
    const cs = risks.filter(r => (r.categoryNameEn ?? r.categoryName ?? '').toLowerCase().includes(cat.toLowerCase()));
    const score = cs.reduce((s, r) => s + r.inherentScore, 0);
    const budget = cat === 'Cyber' ? 80 : cat === 'Operational' ? 60 : cat === 'Compliance' ? 50 : cat === 'Third-Party' ? 40 : cat === 'Financial' ? 70 : 30;
    const used = Math.min(100, Math.round((score / budget) * 100));
    const tone: 'primary' | 'accent' | 'danger' | 'success' = used >= 100 ? 'danger' : used >= 80 ? 'accent' : used >= 50 ? 'primary' : 'success';
    return { cat, used, budget, score, count: cs.length, tone };
  });
  return (
    <>
      <Card title="Risk Appetite Statement" Icon={Target} action={<Badge tone="primary">FY2026</Badge>}>
        <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6, padding: '4px 2px' }}>
          The board accepts <b>moderate</b> risk in pursuit of strategic growth, with <b>zero tolerance</b> for unmanaged
          regulatory non-compliance, customer-impacting privacy breaches, or material misstatement.
          Risk consumption is monitored monthly by the Risk Council; categories above 80% trigger a treatment review.
        </div>
      </Card>
      <div style={{ height: 16 }} />
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))' }}>
        {buckets.map(b => (
          <Card key={b.cat} title={b.cat} Icon={Target} action={<Badge tone={b.tone}>{b.used}% used</Badge>}>
            <Progress label={`${b.count} risks · score ${b.score} / ${b.budget}`} value={Math.min(100, b.used)} tone={b.tone} />
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 8, display: 'flex', justifyContent: 'space-between' }}>
              <span>{b.used >= 100 ? 'Over appetite — escalate' : b.used >= 80 ? 'Approaching limit' : 'Within appetite'}</span>
              <span className="mono">{b.score}/{b.budget}</span>
            </div>
          </Card>
        ))}
      </div>
    </>
  );
}
