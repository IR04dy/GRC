import { useEffect, useMemo, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  AlertTriangle, ShieldCheck, TrendingUp, Calculator, GitBranch,
  CheckCircle2, AlertOctagon, Activity, ArrowUpRight,
  Send, ThumbsUp, ThumbsDown, RefreshCw, Sigma,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useRealtimeOrgEvent } from '../../lib/useRealtimeEntity';
import { useToast } from '../../lib/toast';
import { MOCK_RISKS } from '../risk/mockRisks';
import {
  loadTreatmentPlans, plansForRisk, saveTreatmentPlan, transitionPlan, updateActionStatus,
  breachesForRisk, fireBreach, escalateBreach, resolveBreach,
  acceptancesForRisk, saveAcceptance, transitionAcceptance,
  controlsForRisk, runMonteCarlo, aggregateRiskLifecycle,
  subscribeRiskLifecycle,
  TREATMENT_STATUS_LABEL, TREATMENT_STATUS_TONE, STRATEGY_LABEL,
  ACCEPTANCE_STATUS_LABEL, ACCEPTANCE_STATUS_TONE,
  BREACH_STATUS_LABEL, BREACH_STATUS_TONE,
  type TreatmentPlan, type AcceptanceRequest, type KriBreachEvent,
  type MonteCarloResult, type TreatmentStatus,
} from '../../lib/riskLifecycle';
import { formatHijri } from '../../lib/sla';
import { LifecyclePipeline, type PipelineStage, type PipelineCard } from '../../components/lifecycle/LifecyclePipeline';
import { Activity as ActivityIcon, Send as SendIcon, ThumbsUp as ThumbsUpIcon, Play, CheckCircle2 as CheckCircle2Icon, ThumbsDown as ThumbsDownIcon, FileText as FileTextIcon } from 'lucide-react';
import { ActivityFeedDrawer } from '../../components/lifecycle/ActivityFeedDrawer';
import { logTransition } from '../../lib/activityLog';
import { notify } from '../../lib/notificationOutbox';
import { lookupUserByName } from '../../lib/userDirectory';
import '../../styles/pages/risk-lifecycle.css';

/**
 * RiskLifecyclePage — production hardening (Option 1).
 *
 * Per-risk pipeline: pick a risk, work through five workflow cards:
 *   1. Quantitative scoring — Monte Carlo with sliders, VaR p95/p99
 *   2. KRI breach console — open/investigating/escalated breaches with
 *      escalation chain
 *   3. Treatment plan — DRAFT → SUBMITTED → APPROVED → IN_PROGRESS →
 *      COMPLETED with per-action evidence tracking
 *   4. Acceptance approval — CRO + Board dual attestation with annual
 *      Hijri-aware re-attestation
 *   5. Control linkage — what controls mitigate, with CCM + last evidence
 */
export function RiskLifecyclePage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [riskId, setRiskId] = useState<number>(MOCK_RISKS[0]?.id ?? 1);
  const [historyFor, setHistoryFor] = useState<string | null>(null);
  const risk = useMemo(() => MOCK_RISKS.find(r => r.id === riskId), [riskId]);

  const [_tick, setTick] = useState(0);
  useEffect(() => subscribeRiskLifecycle(() => setTick(t => t + 1)), []);

  // Sprint 4 §2.7 — also tick when the backend pushes a treatment-plan
  // transition. Combines local + remote events through the same `_tick` so the
  // page re-derives memoised data from either source.
  useRealtimeOrgEvent('changed.treatment_plan', () => setTick(t => t + 1));

  const stats = useMemo(() => aggregateRiskLifecycle(), [_tick]);
  const plans = useMemo(() => plansForRisk(riskId), [riskId, _tick]);
  const breaches = useMemo(() => breachesForRisk(riskId), [riskId, _tick]);
  const accepts = useMemo(() => acceptancesForRisk(riskId), [riskId, _tick]);
  const controls = useMemo(() => controlsForRisk(riskId), [riskId]);

  if (!risk) return <Shell title={t('rlcTitle')} sub=""><div /></Shell>;
  const title = lang === 'ar' && risk.titleAr ? risk.titleAr : risk.titleEn;

  return (
    <Shell title={t('rlcTitle')} sub={t('rlcSub')}>
      <PageHeader
        eyebrow={t('rlcEyebrow')}
        title={t('rlcTitle')}
        sub={t('rlcPageSub')}
      />

      <div className="kpi-grid rlc-kpi-strip">
        <Kpi Icon={AlertTriangle}    label={t('rlcKpiTotal')}        value={String(stats.risksTotal)}                tone="primary" to="/risk" />
        <Kpi Icon={AlertOctagon}     label={t('rlcKpiOpenBreach')}   value={String(stats.risksWithOpenBreach)}      tone={stats.risksWithOpenBreach > 0 ? 'danger' : 'success'} to="/risk/kri/dashboard" />
        <Kpi Icon={ShieldCheck}      label={t('rlcKpiTreated')}      value={String(stats.risksWithApprovedTreatment)} tone="success" to="/risk" />
        <Kpi Icon={ThumbsUp}         label={t('rlcKpiAwaitAccept')}  value={String(stats.risksAwaitingAcceptance)}  tone="warning" to="/approvals" />
        <Kpi Icon={RefreshCw}        label={t('rlcKpiExpiring')}     value={String(stats.acceptedExpiringSoon)}     tone={stats.acceptedExpiringSoon > 0 ? 'warning' : 'success'} to="/attestations" />
        <Kpi Icon={TrendingUp}       label={t('rlcKpiSpend')}        value={`${(stats.totalTreatmentSpendK / 1000).toFixed(1)}M SAR`} tone="info" />
      </div>

      {/* Treatment plan pipeline (interactive kanban) */}
      <Card title={t('rlcPipelineTitle')} Icon={ActivityIcon}>
        <RiskTreatmentPipeline
          allPlans={loadTreatmentPlans()}
          onAdvance={(planId, fromStatus) => {
            // DRAFT → SUBMITTED, SUBMITTED → APPROVED, APPROVED → IN_PROGRESS,
            // IN_PROGRESS → COMPLETED, REJECTED → DRAFT.
            const next: Record<string, TreatmentStatus> =
              { DRAFT: 'SUBMITTED', SUBMITTED: 'APPROVED', APPROVED: 'IN_PROGRESS', IN_PROGRESS: 'COMPLETED', REJECTED: 'DRAFT' };
            const target = next[fromStatus];
            if (!target) return;
            const by = target === 'APPROVED' ? 'Mohammed Al-Mahmoud (CRO)' : undefined;
            // Capture prior state for undo.
            const priorPlan = loadTreatmentPlans().find(p => p.id === planId);
            const priorStatus = priorPlan?.status;
            transitionPlan(planId, target, by);
            // Activity log + notify.
            logTransition('treatment-plan', planId, fromStatus, target);
            // Notify the CRO when a plan is submitted or approved.
            if (target === 'SUBMITTED' || target === 'APPROVED') {
              const cro = lookupUserByName('Mohammed Al-Mahmoud');
              if (cro) {
                notify(cro.id, target === 'APPROVED' ? 'P3' : 'P2',
                  `Treatment plan ${planId} ${target.toLowerCase()}`,
                  `خطة المعالجة ${planId} ${target === 'APPROVED' ? 'مُعتمَدة' : 'مُقدَّمة'}`,
                  `Plan moved from ${fromStatus} to ${target}.`,
                  `الخطة انتقلت من ${fromStatus} إلى ${target}.`,
                  'risk-lifecycle',
                  { link: '/risk-lifecycle', refId: planId });
              }
            }
            // Undoable toast.
            toast.undoable(`${t('lpcAdvancedToast')} → ${target}`, () => {
              if (priorStatus) transitionPlan(planId, priorStatus as TreatmentStatus);
              logTransition('treatment-plan', planId, target, priorStatus ?? fromStatus);
              toast.info(t('undoneToast'));
            });
          }}
          onBulkAdvance={(planIds) => {
            // Bulk advance all by one stage.
            const next: Record<string, TreatmentStatus> =
              { DRAFT: 'SUBMITTED', SUBMITTED: 'APPROVED', APPROVED: 'IN_PROGRESS', IN_PROGRESS: 'COMPLETED', REJECTED: 'DRAFT' };
            const priorStates: { id: string; status: string }[] = [];
            planIds.forEach(pid => {
              const p = loadTreatmentPlans().find(x => x.id === pid);
              if (!p) return;
              const tgt = next[p.status];
              if (!tgt) return;
              priorStates.push({ id: pid, status: p.status });
              transitionPlan(pid, tgt);
              logTransition('treatment-plan', pid, p.status, tgt);
            });
            toast.undoable(`${planIds.length} ${t('bulkAdvancedToast')}`, () => {
              priorStates.forEach(s => transitionPlan(s.id, s.status as TreatmentStatus));
              toast.info(t('undoneToast'));
            });
          }}
          onCardHistory={(planId) => setHistoryFor(planId)}
          onCardClick={(planId) => {
            // Jump to the risk that owns this plan.
            const plan = loadTreatmentPlans().find(p => p.id === planId);
            if (plan) setRiskId(plan.riskId);
          }}
        />
      </Card>

      {/* Activity feed drawer */}
      <ActivityFeedDrawer
        open={!!historyFor}
        onClose={() => setHistoryFor(null)}
        entityType="treatment-plan"
        entityId={historyFor ?? ''}
      />

      {/* Risk picker */}
      <div className="rlc-section">
      <Card title={t('rlcPickRisk')} Icon={AlertTriangle}>
        <div className="rlc-picker-row">
          {MOCK_RISKS.slice(0, 12).map(r => {
            const isOn = r.id === riskId;
            const titleR = lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn;
            return (
              <button key={r.id} onClick={() => setRiskId(r.id)}
                className={`rlc-pick-btn${isOn ? ' is-active' : ''}`}>
                <span className="mono rlc-pick-btn__code">{r.riskCode}</span>
                <span className="rlc-pick-btn__title">
                  {titleR}
                </span>
                <Badge tone={r.riskLevel === 'CRITICAL' ? 'danger' : r.riskLevel === 'HIGH' ? 'warning' : r.riskLevel === 'MEDIUM' ? 'info' : 'success'}>
                  {r.riskLevel}
                </Badge>
              </button>
            );
          })}
        </div>
      </Card>
      </div>

      {/* Risk header */}
      <div className="rlc-section">
        <Card>
          <div className="rlc-header-row">
            <div className="rlc-header-main">
              <div className="rlc-header-chips">
                <span className="mono rlc-header-code">{risk.riskCode}</span>
                <Badge tone="info">{lang === 'ar' && risk.categoryNameAr ? risk.categoryNameAr : risk.categoryNameEn}</Badge>
                <Badge tone={risk.riskLevel === 'CRITICAL' ? 'danger' : risk.riskLevel === 'HIGH' ? 'warning' : 'info'}>
                  L{risk.inherentLikelihood} × I{risk.inherentImpact} = {risk.inherentScore}
                </Badge>
                <Badge tone="primary">{t('rlcOwner')}: {risk.ownerFullNameEn}</Badge>
              </div>
              <div className="rlc-header-title">{title}</div>
              <div className="rlc-header-desc">
                {risk.descriptionEn}
              </div>
            </div>
          </div>
        </Card>
      </div>

      {/* Workflow cards */}
      <MonteCarloCard riskId={riskId} risk={risk} />
      <KriBreachCard riskId={riskId} breaches={breaches}
        onFire={() => {
          fireBreach({
            riskId, kriCode: 'KRI-NEW-' + Date.now().toString().slice(-4),
            kriNameEn: 'Sample KRI breach', kriNameAr: 'مؤشّر تجريبي',
            thresholdValue: 5, actualValue: 7.8,
            direction: 'UP_BAD', currentOwner: 'KRI Owner',
          });
          toast.danger(t('rlcBreachFiredToast'));
        }}
        onEscalate={(b) => {
          escalateBreach(b.id, 'CRO',
            `Escalated to CRO — ${b.kriCode} above threshold ${Math.round(((b.actualValue - b.thresholdValue) / b.thresholdValue) * 100)}%`,
            `تصعيد إلى رئيس المخاطر — ${b.kriCode} تجاوز العتبة بنسبة ${Math.round(((b.actualValue - b.thresholdValue) / b.thresholdValue) * 100)}٪`);
          toast.warning(t('rlcEscalatedToast'));
        }}
        onResolve={(b) => {
          resolveBreach(b.id, 'Root cause addressed via control improvement', 'تمّت معالجة السبب الجذري عبر تحسين الضوابط');
          toast.success(t('rlcResolvedToast'));
        }} />
      <TreatmentCard riskId={riskId} plans={plans}
        onCreate={() => {
          const id = `TP-${new Date().getFullYear()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`;
          saveTreatmentPlan({
            id, riskId, strategy: 'MITIGATE',
            rationaleEn: 'New treatment plan — fill in details before submission.',
            rationaleAr: 'خطة معالجة جديدة — أكمل التفاصيل قبل التقديم.',
            status: 'DRAFT', costSarK: 0,
            costBenefitEn: '', costBenefitAr: '',
            actions: [],
            createdAt: new Date().toISOString(),
          });
          toast.info(t('rlcTreatmentCreatedToast'));
        }}
        onSubmit={(p) => { transitionPlan(p.id, 'SUBMITTED'); toast.info(t('rlcSubmittedToast')); }}
        onApprove={(p) => { transitionPlan(p.id, 'APPROVED', 'Mohammed Al-Mahmoud (CRO)'); toast.success(t('rlcApprovedToast')); }}
        onReject={(p) => { transitionPlan(p.id, 'REJECTED', undefined, 'Insufficient cost-benefit justification'); toast.danger(t('rlcRejectedToast')); }}
        onProgress={(p) => { transitionPlan(p.id, 'IN_PROGRESS'); }}
        onActionUpdate={(planId, actionId, status) => {
          updateActionStatus(planId, actionId, status);
          toast.success(t('rlcActionUpdatedToast'));
        }} />
      <AcceptanceCard riskId={riskId} risk={risk} accepts={accepts}
        onCreate={() => {
          const id = `ACC-${new Date().getFullYear()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`;
          const expiry = new Date(Date.now() + 365 * 86_400_000);
          saveAcceptance({
            id, riskId,
            rationaleEn: 'Mitigation cost exceeds expected residual loss; controls already at industry best-practice maturity.',
            rationaleAr: 'تكلفة التخفيف تتجاوز الخسارة المتوقّعة المتبقّية؛ الضوابط بالفعل بمستوى نضج أفضل ممارسات الصناعة.',
            residualScore: risk.inherentScore - 4,
            appetiteUpper: 12,
            status: 'DRAFT',
            expiresAt: expiry.toISOString().slice(0, 10),
            expiresAtHijri: formatHijri(expiry),
            createdAt: new Date().toISOString(),
          });
          toast.info(t('rlcAcceptCreatedToast'));
        }}
        onSubmitToCro={(a) => { transitionAcceptance(a.id, 'CRO_REVIEW', 'Mohammed Al-Mahmoud (CRO)'); toast.info(t('rlcSentToCroToast')); }}
        onPromoteToBoard={(a) => { transitionAcceptance(a.id, 'BOARD_APPROVAL', 'CRO'); toast.info(t('rlcPromotedBoardToast')); }}
        onApprove={(a) => { transitionAcceptance(a.id, 'APPROVED', 'Audit Committee Chair (Khalid Al-Falih)'); toast.success(t('rlcAcceptedToast')); }} />
      <ControlLinkageCard controls={controls} />
    </Shell>
  );
}

// ─── Monte Carlo card ───────────────────────────────────────────────────────

function MonteCarloCard({ riskId, risk }: { riskId: number; risk: any }) {
  const { t } = useI18n();
  // Default distribution — derived from inherent likelihood + impact (mocking SAR scale).
  const baseLikelihood = (risk.inherentLikelihood ?? 3) / 5;
  const baseImpact = (risk.inherentImpact ?? 3) * 1_000_000; // SAR scale
  const [pMin, setPMin] = useState(Math.max(0, baseLikelihood - 0.15));
  const [pMode, setPMode] = useState(baseLikelihood);
  const [pMax, setPMax] = useState(Math.min(1, baseLikelihood + 0.15));
  const [iMin, setIMin] = useState(baseImpact * 0.4);
  const [iMode, setIMode] = useState(baseImpact);
  const [iMax, setIMax] = useState(baseImpact * 2);
  const [trials, setTrials] = useState(5000);
  const [result, setResult] = useState<MonteCarloResult | null>(null);

  // Reset on risk switch.
  useEffect(() => {
    const bl = (risk.inherentLikelihood ?? 3) / 5;
    const bi = (risk.inherentImpact ?? 3) * 1_000_000;
    setPMin(Math.max(0, bl - 0.15)); setPMode(bl); setPMax(Math.min(1, bl + 0.15));
    setIMin(bi * 0.4); setIMode(bi); setIMax(bi * 2);
    setResult(null);
  }, [riskId]);

  function run() {
    setResult(runMonteCarlo({ pMin, pMode, pMax, iMin, iMode, iMax, trials }));
  }

  return (
    <div className="rlc-section">
      <Card title={t('rlcMcTitle')} Icon={Calculator}>
        <div className="rlc-card-intro">
          {t('rlcMcSub')}
        </div>
        <div className="rlc-mc-cols">
          <div>
            <div className="rlc-mc-eyebrow">
              {t('rlcMcLikelihood')}
            </div>
            <SliderRow label={t('rlcMcMin')} value={pMin} min={0} max={1} step={0.01} fmt={v => `${(v * 100).toFixed(0)}%`} onChange={setPMin} />
            <SliderRow label={t('rlcMcMode')} value={pMode} min={0} max={1} step={0.01} fmt={v => `${(v * 100).toFixed(0)}%`} onChange={setPMode} />
            <SliderRow label={t('rlcMcMax')} value={pMax} min={0} max={1} step={0.01} fmt={v => `${(v * 100).toFixed(0)}%`} onChange={setPMax} />
          </div>
          <div>
            <div className="rlc-mc-eyebrow">
              {t('rlcMcImpact')} (SAR)
            </div>
            <SliderRow label={t('rlcMcMin')} value={iMin} min={0} max={iMax} step={50_000} fmt={v => `${(v / 1_000_000).toFixed(2)}M`} onChange={setIMin} />
            <SliderRow label={t('rlcMcMode')} value={iMode} min={0} max={iMax} step={50_000} fmt={v => `${(v / 1_000_000).toFixed(2)}M`} onChange={setIMode} />
            <SliderRow label={t('rlcMcMax')} value={iMax} min={0} max={baseImpact * 5} step={100_000} fmt={v => `${(v / 1_000_000).toFixed(2)}M`} onChange={setIMax} />
          </div>
        </div>

        <div className="rlc-mc-controls">
          <label className="rlc-mc-trials-label">
            {t('rlcMcTrials')}:
            <select value={trials} onChange={e => setTrials(Number(e.target.value))}
              className="rlc-mc-trials-select">
              <option value={1000}>1,000</option>
              <option value={5000}>5,000</option>
              <option value={10000}>10,000</option>
              <option value={50000}>50,000</option>
            </select>
          </label>
          <Button variant="primary" Icon={Sigma} onClick={run}>{t('rlcMcRun')}</Button>
        </div>

        {result && (
          <>
            <div className="rlc-mc-stats">
              <Stat label={t('rlcMcExpected')} value={`${(result.expectedLoss / 1_000_000).toFixed(2)}M`} unit="SAR" tone="primary" />
              <Stat label={t('rlcMcP95')}      value={`${(result.p95Loss / 1_000_000).toFixed(2)}M`}      unit="SAR" tone="warning" />
              <Stat label={t('rlcMcP99')}      value={`${(result.p99Loss / 1_000_000).toFixed(2)}M`}      unit="SAR" tone="danger" />
              <Stat label={t('rlcMcStdev')}    value={`${(result.stdev / 1_000_000).toFixed(2)}M`}        unit="SAR" tone="info" />
            </div>
            {/* Histogram */}
            <div className="rlc-mc-hist-wrap">
              <div className="rlc-mc-hist-label">{t('rlcMcHistogram')}</div>
              <div className="rlc-mc-hist-grid" style={{ gridTemplateColumns: `repeat(${result.histogram.length}, 1fr)` }}>
                {result.histogram.map((b, i) => {
                  const max = Math.max(...result.histogram.map(h => h.count));
                  const heightPct = max > 0 ? (b.count / max) * 100 : 0;
                  return (
                    <div key={i} className="rlc-mc-hist-col">
                      <div className="rlc-mc-hist-bar-wrap">
                        <div className="rlc-mc-hist-bar"
                          style={{ height: `${heightPct}%`, minHeight: b.count > 0 ? 2 : 0 }}
                          title={`${b.rangeStart.toFixed(0)} - ${b.rangeEnd.toFixed(0)}: ${b.count}`} />
                      </div>
                      <div className="mono rlc-mc-hist-tick">
                        {(b.rangeEnd / 1_000_000).toFixed(1)}
                      </div>
                    </div>
                  );
                })}
              </div>
              <div className="rlc-mc-hist-axis">
                {t('rlcMcLossSar')} (M)
              </div>
            </div>
          </>
        )}
      </Card>
    </div>
  );
}

function SliderRow({ label, value, min, max, step, fmt, onChange }: {
  label: string; value: number; min: number; max: number; step: number;
  fmt: (v: number) => string; onChange: (v: number) => void;
}) {
  return (
    <div className="rlc-slider-row">
      <span className="rlc-slider-label">{label}</span>
      <input type="range" min={min} max={max} step={step} value={value}
        onChange={e => onChange(Number(e.target.value))} className="rlc-slider-input" />
      <span className="mono rlc-slider-value">{fmt(value)}</span>
    </div>
  );
}

// ─── KRI breach card ────────────────────────────────────────────────────────

function KriBreachCard({ riskId: _riskId, breaches, onFire, onEscalate, onResolve }: {
  riskId: number;
  breaches: KriBreachEvent[];
  onFire: () => void;
  onEscalate: (b: KriBreachEvent) => void;
  onResolve: (b: KriBreachEvent) => void;
}) {
  const { t, lang } = useI18n();
  const open = breaches.filter(b => b.status !== 'RESOLVED');
  const resolved = breaches.filter(b => b.status === 'RESOLVED');

  return (
    <div className="rlc-section">
      <Card title={t('rlcKriTitle')} Icon={Activity}
        action={
          <div className="rlc-kri-actions">
            <Badge tone={open.length > 0 ? 'danger' : 'success'}>{open.length} {t('rlcKriOpen')}</Badge>
            <Button size="sm" variant="secondary" Icon={AlertOctagon} onClick={onFire}>{t('rlcFireBreach')}</Button>
          </div>
        }>
        <div className="rlc-card-intro">
          {t('rlcKriSub')}
        </div>
        {breaches.length === 0
          ? <InlineEmpty>{t('rlcKriEmpty')}</InlineEmpty>
          : [...open, ...resolved].map(b => (
              <div key={b.id} className="rlc-breach-card">
                <div className="rlc-breach-card__head">
                  <Badge tone={BREACH_STATUS_TONE[b.status]}>{BREACH_STATUS_LABEL[b.status][lang]}</Badge>
                  <span className="mono rlc-breach-card__code">{b.kriCode}</span>
                  <span className="rlc-breach-card__name">{lang === 'ar' ? b.kriNameAr : b.kriNameEn}</span>
                  <Badge tone={b.direction === 'UP_BAD' ? 'danger' : 'warning'}>
                    {b.actualValue} <ArrowUpRight size={11} className="rlc-arrow-inline" /> {b.thresholdValue} ({b.direction === 'UP_BAD' ? '+' : '−'}{Math.abs(((b.actualValue - b.thresholdValue) / b.thresholdValue) * 100).toFixed(0)}%)
                  </Badge>
                  <span className="rlc-breach-card__owner">
                    {t('rlcOwner')}: <b>{b.currentOwner}</b>
                  </span>
                </div>
                {/* Escalation chain */}
                <div className="rlc-breach-card__esc">
                  {b.escalationLog.map((e, i) => (
                    <span key={i} className="rlc-breach-esc-step">
                      <span className="rlc-breach-esc-pill">
                        {lang === 'ar' ? e.stepAr : e.stepEn}
                      </span>
                      {i < b.escalationLog.length - 1 && <ArrowUpRight size={10} />}
                    </span>
                  ))}
                </div>
                {b.status !== 'RESOLVED' && (
                  <div className="rlc-breach-actions">
                    <Button size="sm" variant="secondary" Icon={ArrowUpRight} onClick={() => onEscalate(b)}>{t('rlcEscalate')}</Button>
                    <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onResolve(b)}>{t('rlcResolve')}</Button>
                  </div>
                )}
                {b.resolutionEn && (
                  <div className="rlc-breach-resolution">
                    {lang === 'ar' ? b.resolutionAr : b.resolutionEn}
                  </div>
                )}
              </div>
            ))
        }
      </Card>
    </div>
  );
}

// ─── Treatment card ─────────────────────────────────────────────────────────

function TreatmentCard({ riskId: _riskId, plans, onCreate, onSubmit, onApprove, onReject, onProgress, onActionUpdate }: {
  riskId: number;
  plans: TreatmentPlan[];
  onCreate: () => void;
  onSubmit: (p: TreatmentPlan) => void;
  onApprove: (p: TreatmentPlan) => void;
  onReject: (p: TreatmentPlan) => void;
  onProgress: (p: TreatmentPlan) => void;
  onActionUpdate: (planId: string, actionId: string, status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'BLOCKED') => void;
}) {
  const { t, lang } = useI18n();
  return (
    <div className="rlc-section">
      <Card title={t('rlcTreatTitle')} Icon={ShieldCheck}
        action={<Button size="sm" variant="secondary" onClick={onCreate}>{t('rlcCreatePlan')}</Button>}>
        <div className="rlc-card-intro">
          {t('rlcTreatSub')}
        </div>
        {plans.length === 0
          ? <InlineEmpty>{t('rlcTreatEmpty')}</InlineEmpty>
          : plans.map(p => {
              const completedActions = p.actions.filter(a => a.status === 'COMPLETED').length;
              const totalReduction = p.actions.filter(a => a.status === 'COMPLETED').reduce((acc, a) => acc + a.expectedRiskReduction, 0);
              return (
                <div key={p.id} className="rlc-plan-card">
                  <div className="rlc-plan-card__head">
                    <div className="rlc-plan-card__main">
                      <div className="rlc-plan-card__chips">
                        <span className="mono rlc-plan-card__id">{p.id}</span>
                        <Badge tone={TREATMENT_STATUS_TONE[p.status]}>{TREATMENT_STATUS_LABEL[p.status][lang]}</Badge>
                        <Badge tone="info">{STRATEGY_LABEL[p.strategy][lang]}</Badge>
                        {p.costSarK > 0 && <Badge tone="primary">{p.costSarK.toLocaleString()}k SAR</Badge>}
                      </div>
                      <div className="rlc-plan-card__rationale">
                        {lang === 'ar' ? p.rationaleAr : p.rationaleEn}
                      </div>
                      {p.costBenefitEn && (
                        <div className="rlc-plan-card__cb">
                          {lang === 'ar' ? p.costBenefitAr : p.costBenefitEn}
                        </div>
                      )}
                      {p.approvedBy && (
                        <div className="rlc-plan-card__approval">
                          {t('rlcApprovedBy')}: {p.approvedBy}
                        </div>
                      )}
                    </div>
                    <div className="rlc-plan-card__actions">
                      {p.status === 'DRAFT' && <Button size="sm" variant="primary" Icon={Send} onClick={() => onSubmit(p)}>{t('rlcSubmit')}</Button>}
                      {p.status === 'SUBMITTED' && (
                        <>
                          <Button size="sm" variant="primary" Icon={ThumbsUp} onClick={() => onApprove(p)}>{t('rlcApprove')}</Button>
                          <Button size="sm" variant="ghost" Icon={ThumbsDown} onClick={() => onReject(p)}>{t('rlcReject')}</Button>
                        </>
                      )}
                      {p.status === 'APPROVED' && <Button size="sm" variant="primary" onClick={() => onProgress(p)}>{t('rlcStartWork')}</Button>}
                    </div>
                  </div>
                  {/* Progress bar */}
                  {p.actions.length > 0 && (
                    <div className="rlc-progress-wrap">
                      <div className="rlc-progress-head">
                        <span>{t('rlcActions')} {completedActions}/{p.actions.length}</span>
                        <span>{t('rlcExpectedReduction')}: {(totalReduction * 100).toFixed(0)}%</span>
                      </div>
                      <div className="rlc-progress-track">
                        <div className="rlc-progress-fill" style={{ width: `${(completedActions / p.actions.length) * 100}%` }} />
                      </div>
                    </div>
                  )}
                  {/* Actions */}
                  {p.actions.length > 0 && (
                    <div className="rlc-action-list">
                      {p.actions.map(a => (
                        <div key={a.id} className="rlc-action-row">
                          <div>
                            <div className="rlc-action-title">{lang === 'ar' ? a.titleAr : a.titleEn}</div>
                            <div className="rlc-action-meta">
                              {a.ownerRole} · {t('rlcDue')} {a.targetDate} · −{(a.expectedRiskReduction * 100).toFixed(0)}%
                            </div>
                          </div>
                          <Badge tone={a.status === 'COMPLETED' ? 'success' : a.status === 'IN_PROGRESS' ? 'info' : a.status === 'BLOCKED' ? 'danger' : 'neutral'}>
                            {a.status}
                          </Badge>
                          {a.status !== 'COMPLETED' && (
                            <Button size="sm" variant="ghost" onClick={() => onActionUpdate(p.id, a.id, 'COMPLETED')}>{t('rlcMarkDone')}</Button>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })
        }
      </Card>
    </div>
  );
}

// ─── Acceptance card ────────────────────────────────────────────────────────

function AcceptanceCard({ riskId: _riskId, risk: _risk, accepts, onCreate, onSubmitToCro, onPromoteToBoard, onApprove }: {
  riskId: number;
  risk: any;
  accepts: AcceptanceRequest[];
  onCreate: () => void;
  onSubmitToCro: (a: AcceptanceRequest) => void;
  onPromoteToBoard: (a: AcceptanceRequest) => void;
  onApprove: (a: AcceptanceRequest) => void;
}) {
  const { t, lang } = useI18n();
  return (
    <div className="rlc-section">
      <Card title={t('rlcAcceptTitle')} Icon={GitBranch}
        action={<Button size="sm" variant="secondary" onClick={onCreate}>{t('rlcCreateAcceptance')}</Button>}>
        <div className="rlc-card-intro">
          {t('rlcAcceptSub')}
        </div>
        {accepts.length === 0
          ? <InlineEmpty>{t('rlcAcceptEmpty')}</InlineEmpty>
          : accepts.map(a => (
              <div key={a.id} className="rlc-accept-card">
                <div className="rlc-accept-card__head">
                  <div className="rlc-accept-card__main">
                    <div className="rlc-accept-card__chips">
                      <span className="mono rlc-plan-card__id">{a.id}</span>
                      <Badge tone={ACCEPTANCE_STATUS_TONE[a.status]}>{ACCEPTANCE_STATUS_LABEL[a.status][lang]}</Badge>
                      <Badge tone="warning">{t('rlcResidual')}: {a.residualScore}</Badge>
                      <Badge tone="info">{t('rlcAppetite')}: ≤ {a.appetiteUpper}</Badge>
                    </div>
                    <div className="rlc-accept-card__rationale">
                      {lang === 'ar' ? a.rationaleAr : a.rationaleEn}
                    </div>
                    <div className="rlc-accept-card__expiry">
                      {t('rlcExpiresAt')}: <span className="mono">{a.expiresAt}</span>
                      {a.expiresAtHijri && <> · <span className="mono">{a.expiresAtHijri}</span></>}
                    </div>
                  </div>
                  <div className="rlc-accept-card__actions">
                    {a.status === 'DRAFT' && <Button size="sm" variant="primary" Icon={Send} onClick={() => onSubmitToCro(a)}>{t('rlcSendToCro')}</Button>}
                    {a.status === 'CRO_REVIEW' && <Button size="sm" variant="primary" onClick={() => onPromoteToBoard(a)}>{t('rlcPromoteBoard')}</Button>}
                    {a.status === 'BOARD_APPROVAL' && <Button size="sm" variant="primary" Icon={ThumbsUp} onClick={() => onApprove(a)}>{t('rlcBoardSign')}</Button>}
                  </div>
                </div>
                {/* Signatures */}
                <div className="rlc-signatures">
                  <div>
                    <div className="rlc-signature-label">{t('rlcCroSignoff')}</div>
                    {a.croSignedBy
                      ? <div><b>{a.croSignedBy}</b><div className="mono rlc-signature-date">{a.croSignedAt?.slice(0, 10)}</div></div>
                      : <span className="rlc-signature-pending">{t('rlcPending')}</span>}
                  </div>
                  <div>
                    <div className="rlc-signature-label">{t('rlcBoardSignoff')}</div>
                    {a.boardSignedBy
                      ? <div><b>{a.boardSignedBy}</b><div className="mono rlc-signature-date">{a.boardSignedAt?.slice(0, 10)}</div></div>
                      : <span className="rlc-signature-pending">{t('rlcPending')}</span>}
                  </div>
                </div>
              </div>
            ))
        }
      </Card>
    </div>
  );
}

// ─── Control linkage card ──────────────────────────────────────────────────

function ControlLinkageCard({ controls }: { controls: ReturnType<typeof controlsForRisk> }) {
  const { t, lang } = useI18n();
  return (
    <div className="rlc-section">
      <Card title={t('rlcControlsTitle')} Icon={ShieldCheck}>
        <div className="rlc-card-intro">
          {t('rlcControlsSub')}
        </div>
        {controls.length === 0
          ? <InlineEmpty>{t('rlcControlsEmpty')}</InlineEmpty>
          : (
            <table className="tbl rlc-table-full">
              <thead>
                <tr>
                  <th>{t('rlcControlCode')}</th>
                  <th>{t('rlcControlTitle')}</th>
                  <th>{t('rlcControlFw')}</th>
                  <th>{t('rlcControlMaturity')}</th>
                  <th>{t('rlcControlCcm')}</th>
                  <th>{t('rlcControlEvidence')}</th>
                </tr>
              </thead>
              <tbody>
                {controls.map(c => {
                  const tone = c.ccmCoveragePct >= 80 ? 'success' : c.ccmCoveragePct >= 50 ? 'warning' : 'danger';
                  return (
                  <tr key={c.controlCode}>
                    <td className="mono rlc-ccm-text">{c.controlCode}</td>
                    <td>{lang === 'ar' ? c.controlTitleAr : c.controlTitleEn}</td>
                    <td><Badge tone="info">{c.framework}</Badge></td>
                    <td>
                      <Badge tone={c.maturity >= 4 ? 'success' : c.maturity >= 3 ? 'info' : 'warning'}>
                        {c.maturity}/5
                      </Badge>
                    </td>
                    <td>
                      <div className="rlc-ccm-row">
                        <div className="rlc-ccm-track">
                          <div className="rlc-ccm-fill" data-tone={tone} style={{ width: `${c.ccmCoveragePct}%` }} />
                        </div>
                        <span className="mono rlc-ccm-text">{c.ccmCoveragePct}%</span>
                      </div>
                    </td>
                    <td className="mono rlc-ccm-text">{c.lastEvidenceAt ?? '—'}</td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          )}
      </Card>
    </div>
  );
}

// ─── Treatment plan pipeline (interactive kanban) ──────────────────────────

function RiskTreatmentPipeline({ allPlans, onAdvance, onBulkAdvance, onCardHistory, onCardClick }: {
  allPlans: TreatmentPlan[];
  onAdvance: (planId: string, fromStatus: string, toStatus: string | null) => void;
  onBulkAdvance?: (planIds: string[]) => void;
  onCardHistory?: (planId: string) => void;
  onCardClick: (planId: string) => void;
}) {
  const { t } = useI18n();
  const stages: PipelineStage[] = [
    { code: 'DRAFT',       labelEn: 'Draft',         labelAr: 'مسوّدة',         tone: 'neutral', Icon: FileTextIcon },
    { code: 'SUBMITTED',   labelEn: 'Submitted',     labelAr: 'مُقدَّمة',        tone: 'warning', Icon: SendIcon },
    { code: 'APPROVED',    labelEn: 'Approved',      labelAr: 'مُعتمَدة',        tone: 'info',    Icon: ThumbsUpIcon },
    { code: 'IN_PROGRESS', labelEn: 'In progress',    labelAr: 'قيد التنفيذ',    tone: 'primary', Icon: Play },
    { code: 'COMPLETED',   labelEn: 'Completed',     labelAr: 'مُنجَزة',         tone: 'success', Icon: CheckCircle2Icon },
    { code: 'REJECTED',    labelEn: 'Rejected',      labelAr: 'مرفوضة',          tone: 'danger',  Icon: ThumbsDownIcon },
  ];
  const cards: PipelineCard[] = allPlans.map(p => {
    const completed = p.actions.filter(a => a.status === 'COMPLETED').length;
    return {
      id: p.id, status: p.status,
      refCode: p.id,
      titleEn: p.rationaleEn.slice(0, 80) + (p.rationaleEn.length > 80 ? '…' : ''),
      titleAr: p.rationaleAr.slice(0, 80) + (p.rationaleAr.length > 80 ? '…' : ''),
      subtitleEn: `Strategy: ${p.strategy} · ${p.costSarK.toLocaleString()}k SAR`,
      subtitleAr: `الاستراتيجية: ${p.strategy} · ${p.costSarK.toLocaleString()} ألف ريال`,
      badges: [
        { label: `${completed}/${p.actions.length} ${t('rlcActions')}`, tone: completed === p.actions.length && p.actions.length > 0 ? 'success' : 'neutral' as const },
      ],
    };
  });

  return (
    <LifecyclePipeline
      stages={stages}
      cards={cards}
      introEn="Live treatment-plan pipeline across all risks. Click a card to jump to that risk; click the arrow to advance. Hold Shift+click for multi-select; click the clock icon for activity history."
      introAr="مسار خطط المعالجة الحيّ عبر جميع المخاطر. انقر على بطاقة للانتقال إلى تلك المخاطرة، وانقر السهم لتقدّم الخطة. اضغط Shift+نقر للتحديد المتعدّد؛ انقر أيقونة الساعة للسجل."
      onAdvance={onAdvance}
      onCardClick={onCardClick}
      onBulkAdvance={onBulkAdvance}
      onCardHistory={onCardHistory}
      emptyEn="No treatment plans yet."
      emptyAr="لا خطط معالجة بعد."
    />
  );
}

// ─── Helpers ───────────────────────────────────────────────────────────────

function Stat({ label, value, unit, tone }: { label: string; value: string; unit?: string; tone: 'primary' | 'warning' | 'danger' | 'info' }) {
  return (
    <div className="rlc-stat-tile" data-tone={tone}>
      <div className="rlc-stat-tile__label">{label}</div>
      <div className="mono rlc-stat-tile__value">
        {value}{unit && <span className="rlc-stat-tile__unit">{unit}</span>}
      </div>
    </div>
  );
}
