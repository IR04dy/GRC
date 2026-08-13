import { useEffect, useMemo, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { AlertTriangle, ShieldCheck, TrendingUp, Calculator, CheckCircle2, AlertOctagon, Activity, ArrowUpRight, ThumbsUp, RefreshCw, Sigma } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useRealtimeOrgEvent } from '../../lib/useRealtimeEntity';
import { useToast } from '../../lib/toast';
import { MOCK_RISKS } from '../risk/mockRisks';
import { loadTreatmentPlans, plansForRisk, transitionPlan, createTreatmentPlan, hydrateTreatmentPlans, treatmentPlansLoadFailed, nextTreatmentStatuses, treatmentStatusLabel, breachesForRisk, fireBreach, escalateBreach, resolveBreach, acceptancesForRisk, saveAcceptance, transitionAcceptance, controlsForRisk, runMonteCarlo, aggregateRiskLifecycle, subscribeRiskLifecycle, BREACH_STATUS_LABEL, BREACH_STATUS_TONE, type KriBreachEvent, type MonteCarloResult } from '../../lib/riskLifecycle';
import { formatHijri } from '../../lib/sla';
import { Activity as ActivityIcon } from 'lucide-react';
import { ActivityFeedDrawer } from '../../components/lifecycle/ActivityFeedDrawer';
import { logTransition } from '../../lib/activityLog';
import { TreatmentCard, AcceptanceCard, ControlLinkageCard, RiskTreatmentPipeline, Stat }
  from './RiskLifecycleCards';
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

  /**
   * F2-204: every treatment-plan mutation was synchronous over a localStorage store and
   * unconditionally followed by a success toast. A write that fails must say so, and must not
   * leave the screen showing a state the server rejected — the cache is replaced from the
   * server's response, so a refused transition cannot appear to have happened.
   */
  async function runPlan(work: () => Promise<unknown>, okMsg: string) {
    try {
      await work();
      toast.success(okMsg);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (lang === 'ar' ? 'تعذّر الحفظ' : 'Could not save'));
    }
  }

  // Load the real register once; nothing seeds it, so an empty tenant reads as empty.
  useEffect(() => { void hydrateTreatmentPlans(); }, []);
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
      {/* F2-204: an empty register and an unreachable one are different facts. Nothing seeds
          the treatment-plan list any more, so a failed fetch must be reported rather than
          rendered as "this risk has no treatment plans". */}
      {treatmentPlansLoadFailed() && (
        <Card title={lang === 'ar' ? 'تعذّر تحميل خطط المعالجة' : 'Treatment plans could not be loaded'}>
          <div className="rlc-card-sub">
            {lang === 'ar'
              ? 'فشل تحميل سجلّ خطط المعالجة من الخادم. القائمة أدناه غير مكتملة — أعد المحاولة قبل الاعتماد عليها.'
              : 'The treatment-plan register failed to load from the server. The list below is incomplete — retry before relying on it.'}
          </div>
          <Button size="sm" variant="secondary" onClick={() => void hydrateTreatmentPlans(true)}>
            {lang === 'ar' ? 'إعادة المحاولة' : 'Retry'}
          </Button>
        </Card>
      )}
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
        <Kpi Icon={TrendingUp}       label={t('rlcKpiSpend')}        value={`${(stats.totalTreatmentSpendK / 1000).toFixed(1)}M SAR`} tone="info" to="/risk" />
      </div>

      {/* Treatment plan pipeline (interactive kanban) */}
      <Card title={t('rlcPipelineTitle')} Icon={ActivityIcon}>
        <RiskTreatmentPipeline
          allPlans={loadTreatmentPlans()}
          onAdvance={(planId, fromStatus) => {
            // F2-204: this was a FOURTH copy of the machine, written as a lookup map, and it
            // also chose the approver — `'Mohammed Al-Mahmoud (CRO)'`, an invented Chief Risk
            // Officer. Ask the one table; the server stamps the approver (F2-22b(vi)).
            const target = nextTreatmentStatuses(fromStatus)[0];
            if (!target) {
              toast.info(lang === 'ar'
                ? `لا يوجد انتقال مُعلَن من الحالة ${fromStatus}`
                : `No transition is declared out of ${fromStatus}`);
              return;
            }
            void runPlan(() => transitionPlan(planId, target),
                         `${t('lpcAdvancedToast')} → ${treatmentStatusLabel(target, lang)}`);
            logTransition('treatment-plan', planId, fromStatus, target);
            /* F2-142 — the CRO notification on submit/approve is removed, not repointed.
             *
             * It was three fabrications stacked: the recipient was resolved by display name against
             * the invented user directory, so the id existed in no row of `iam.users`; the notify call
             * wrote to a localStorage store with zero network calls; and nothing rendered that store,
             * so the message was unreadable even in principle. There is no client endpoint to repoint
             * it at — `platform.notifications` rows are written server-side by the F8 engine, which is
             * the correct place for a transition-triggered notification and where CE-10 already puts
             * them. Making the risk lifecycle notify for real is server work, not a swapped import. */
            /* ⚠⚠ F2-204: the undoable toast is REMOVED, and wiring the store is why.
             *
             * It called `transitionPlan(planId, priorStatus)` — a BACKWARDS transition. The
             * server's machine allows exactly one reverse edge (`REJECTED → DRAFT`), so every
             * other undo would answer 409: un-submitting, un-approving and un-completing are
             * all forbidden, which is the point of an approval. While the store was
             * browser-local the undo silently worked and the register drifted from what the
             * machine would ever have permitted.
             *
             * 🔑 An undo affordance over a server-enforced state machine is a promise the
             * machine forbids. If reversal is a real requirement it is a reverse EDGE plus an
             * audit reason, decided on the server — not a client-side rewind. Filed. */
          }}
          onBulkAdvance={(planIds) => {
            // F2-204: the FIFTH copy of the machine lived here, and its undo had the same
            // problem as the single-card one above. Sequential on purpose — each transition is
            // a server decision and a partial failure must be reported, not swallowed.
            void (async () => {
              let moved = 0;
              for (const pid of planIds) {
                const plan = loadTreatmentPlans().find(x => x.id === pid);
                const tgt = plan && nextTreatmentStatuses(plan.status)[0];
                if (!plan || !tgt) continue;
                try {
                  await transitionPlan(pid, tgt);
                  logTransition('treatment-plan', pid, plan.status, tgt);
                  moved += 1;
                } catch (e) {
                  toast.danger(e instanceof ApiError ? e.message
                    : (lang === 'ar' ? 'تعذّر الحفظ' : 'Could not save'));
                  break;
                }
              }
              if (moved > 0) toast.success(`${moved} ${t('bulkAdvancedToast')}`);
            })();
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
        /* F2-204: the server mints the reference — this minted `TP-<year>-<4 chars>` itself. */
        onCreate={() => void runPlan(
          () => createTreatmentPlan(riskId, 'MITIGATE',
            'New treatment plan — fill in details before submission.',
            'خطة معالجة جديدة — أكمل التفاصيل قبل التقديم.', 0),
          t('rlcTreatmentCreatedToast'))}
        onSubmit={(p) => void runPlan(() => transitionPlan(p.id, 'SUBMITTED'), t('rlcSubmittedToast'))}
        /* No approver argument: `TreatmentPlanService` stamps the authenticated principal. This
           passed `'Mohammed Al-Mahmoud (CRO)'` and displayed it as the approving officer. */
        onApprove={(p) => void runPlan(() => transitionPlan(p.id, 'APPROVED'), t('rlcApprovedToast'))}
        /* ⚠⚠ And no reason literal. It passed `'Insufficient cost-benefit justification'` — the
           page inventing the SUBSTANCE of a governance decision, not just its author — and the
           server PERSISTS `rejectedReason`, so wiring this without removing it would have
           written invented text into the column as a real officer's stated rationale. There is
           no input for a reason on this card; filed rather than defaulted. */
        onReject={(p) => void runPlan(() => transitionPlan(p.id, 'REJECTED'), t('rlcRejectedToast'))}
        onProgress={(p) => void runPlan(() => transitionPlan(p.id, 'IN_PROGRESS'),
                                        treatmentStatusLabel('IN_PROGRESS', lang))} />
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
