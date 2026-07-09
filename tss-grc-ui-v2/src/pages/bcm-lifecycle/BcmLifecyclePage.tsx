import { useEffect, useMemo, useState } from 'react';
import {
  Activity, Calendar, Clock, Download, Siren, ShieldCheck,
  AlertTriangle, CheckCircle2, ThumbsUp, FileText, MapPin, GitBranch,
  Play, StopCircle, ArrowRight,
} from 'lucide-react';
import { LifecyclePipeline, type PipelineStage, type PipelineCard } from '../../components/lifecycle/LifecyclePipeline';
import { printPacket, packetTextToSections } from '../../lib/packetPrint';
import { reportingApi, ApiError } from '../../lib/api';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  loadBia, transitionBia,
  loadStrategies, transitionStrategy, createStrategy,
  loadDrills, transitionDrill, scheduleDrill,
  loadInvocations, declareInvocation, transitionInvocation, logInvocationNotification,
  activeInvocation, createBia,
  subscribeBcm, aggregateBcm, buildOpresPacket, buildOpresSheets,
  pickProcessById, pickPlanById,
  BCM_PROCESSES, BCM_PLANS,
  TIER_LABEL, TIER_TONE, BIA_STATUS_LABEL, BIA_STATUS_TONE,
  SITE_LABEL, STRATEGY_STATUS_TONE,
  DRILL_TYPE_LABEL, DRILL_STATUS_TONE, DRILL_STATUS_LABEL,
  INVOCATION_STATUS_LABEL, INVOCATION_STATUS_TONE,
  type BiaRecord, type RecoveryStrategy, type Drill, type Invocation,
  type InvocationStatus, type StrategyStatus, type ProcessTier, type DrillType,
  type RecoverySite,
} from '../../lib/bcmLifecycle';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';
import { useRealtimeOrgEvent } from '../../lib/useRealtimeEntity';
import '../../styles/pages/bcm-lifecycle.css';

type BcmTab = 'pipeline' | 'bia' | 'strategy' | 'drills' | 'invocation' | 'history' | 'packet';

export function BcmLifecyclePage() {
  const { t, lang } = useI18n();
  const toast = useToast();

  const [tab, setTab] = useState<BcmTab>('pipeline');
  const [_tick, setTick] = useState(0);
  useEffect(() => subscribeBcm(() => setTick(t => t + 1)), []);

  // Sprint 4 §2.7 — backend `bcm_lc.InvocationService` broadcasts on every
  // invocation transition (declare/contain/recover/restore). Tick the same
  // counter so memoised data re-derives for either local or remote events.
  useRealtimeOrgEvent('changed.bcm_invocation', () => setTick(t => t + 1));

  const stats = useMemo(() => aggregateBcm(), [_tick]);
  const bias = useMemo(() => loadBia(), [_tick]);
  const strategies = useMemo(() => loadStrategies(), [_tick]);
  const drills = useMemo(() => loadDrills(), [_tick]);
  const invocations = useMemo(() => loadInvocations(), [_tick]);
  const active = useMemo(() => activeInvocation(), [_tick]);

  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);

  function downloadOpres() {
    const m = new Date().getMonth();
    const q = `Q${Math.floor(m / 3) + 1}-${new Date().getFullYear()}`;
    const text = buildOpresPacket(brand.organisationNameEn, brand.organisationNameAr, q);
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `SAMA-OPRES-${q}.txt`;
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.success(t('bclOpresDownloadedToast'));
  }

  function printOpresPdf() {
    const m = new Date().getMonth();
    const q = `Q${Math.floor(m / 3) + 1}-${new Date().getFullYear()}`;
    const text = buildOpresPacket(brand.organisationNameEn, brand.organisationNameAr, q);
    printPacket({
      titleEn: 'SAMA Operational Resilience Framework — Quarterly Reporting Packet',
      titleAr: 'إطار المرونة التشغيلية ساما — حزمة الإبلاغ الفصلية',
      eyebrowEn: 'SAMA OPRES · Quarterly',
      eyebrowAr: 'ساما OPRES · فصلي',
      referenceId: `SAMA-OPRES-${q}`,
      classificationEn: 'CONFIDENTIAL — REGULATOR',
      classificationAr: 'سرّي — جهة تنظيمية',
      sections: packetTextToSections(text),
    }, lang);
  }

  async function exportOpres(kind: 'xlsx' | 'docx') {
    const payload = { module: 'BCM', exportType: 'SamaOpRes', lang, sheets: buildOpresSheets() };
    try {
      await (kind === 'xlsx' ? reportingApi.packetXlsx(payload) : reportingApi.packetDocx(payload));
      toast.success(lang === 'ar' ? 'تم التصدير' : 'Exported');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (lang === 'ar' ? 'تعذّر التصدير' : 'Export failed'));
    }
  }

  return (
    <Shell title={t('bclTitle')} sub={t('bclSub')}>
      <PageHeader
        eyebrow={t('bclEyebrow')}
        title={t('bclTitle')}
        sub={t('bclPageSub')}
        actions={
          <>
            <Button variant="primary" onClick={printOpresPdf}>{t('bclPrintOpresPdf')}</Button>
            <Button variant="secondary" Icon={Download} onClick={() => exportOpres('xlsx')}>{lang === 'ar' ? 'تصدير Excel' : 'Export Excel'}</Button>
            <Button variant="secondary" Icon={Download} onClick={() => exportOpres('docx')}>{lang === 'ar' ? 'تصدير Word' : 'Export Word'}</Button>
            <Button variant="ghost" Icon={Download} onClick={downloadOpres}>{t('bclDownloadOpres')}</Button>
          </>
        }
      />

      {/* Active invocation banner */}
      {active && <ActiveInvocationBanner active={active}
        onTransition={(status, narrEn, narrAr) => {
          transitionInvocation(active.id, status, 'Layla Al-Ghamdi (Head of BCM)', narrEn, narrAr);
          toast.warning(`${INVOCATION_STATUS_LABEL[status][lang]}`);
        }}
      />}

      <div className="kpi-grid bcl-kpi-strip">
        <Kpi Icon={Activity}        label={t('bclKpiProcesses')}     value={String(stats.bcmProcessTotal)}             tone="primary" to="/bcm/bia" />
        <Kpi Icon={ShieldCheck}     label={t('bclKpiBiaApproved')}   value={`${stats.biaApproved} / ${bias.length}`} tone="success" to="/bcm/bia" />
        <Kpi Icon={Clock}            label={t('bclKpiBiaExpiring')}    value={String(stats.biaExpiringSoon)}             tone={stats.biaExpiringSoon > 0 ? 'warning' : 'success'} to="/bcm/bia" />
        <Kpi Icon={AlertTriangle}    label={t('bclKpiTier1')}          value={String(stats.tier1Count)}                  tone="danger" to="/bcm/bia" />
        <Kpi Icon={Calendar}         label={t('bclKpiDrillsPlanned')}   value={String(stats.drillsPlanned)}              tone="info" to="/resilience-tests" />
        <Kpi Icon={CheckCircle2}     label={t('bclKpiRtoCompl')}        value={`${stats.rtoComplianceRate}%`}             tone={stats.rtoComplianceRate >= 80 ? 'success' : 'warning'} to="/bcm/readiness" />
        <Kpi Icon={Siren}             label={t('bclKpiInvocations')}    value={String(stats.invocationsThisYear)}         tone={stats.invocationsThisYear > 0 ? 'warning' : 'success'} to="/bcm/crisis-room" />
        <Kpi Icon={MapPin}            label={t('bclKpiSpend')}            value={`${(stats.totalAnnualStrategySpendK / 1000).toFixed(1)}M SAR`} tone="info" to="/bcm/strategy-roi" />
      </div>

      {/* Tabs */}
      <div className="bcl-tab-row">
        {([
          { id: 'pipeline',   labelKey: 'lpcTabPipeline', Icon: Activity },
          { id: 'bia',        labelKey: 'bclTabBia',      Icon: ShieldCheck },
          { id: 'strategy',   labelKey: 'bclTabStrategy', Icon: GitBranch },
          { id: 'drills',     labelKey: 'bclTabDrills',   Icon: Calendar },
          { id: 'invocation', labelKey: 'bclTabInvoke',   Icon: Siren },
          { id: 'history',    labelKey: 'bclTabHistory',  Icon: Activity },
          { id: 'packet',     labelKey: 'bclTabPacket',   Icon: FileText },
        ] as { id: BcmTab; labelKey: string; Icon: typeof Activity }[]).map(td => (
          <button key={td.id} onClick={() => setTab(td.id)}
            className={`bcl-tab-btn${tab === td.id ? ' is-active' : ''}`}>
            <td.Icon size={13} />
            {t(td.labelKey)}
          </button>
        ))}
      </div>

      {tab === 'pipeline' && <InvocationPipeline invocations={invocations}
        onAdvance={(id, fromStatus) => {
          const next: Record<string, InvocationStatus> = {
            STANDBY: 'DECLARED',
            DECLARED: 'INVOKED',
            INVOKED: 'STABILISED',
            STABILISED: 'STAND_DOWN',
            STAND_DOWN: 'POST_INCIDENT_REVIEW',
          };
          const target = next[fromStatus];
          if (!target) return;
          transitionInvocation(id, target, 'Layla Al-Ghamdi (Head of BCM)');
          toast.success(t('lpcAdvancedToast'));
        }}
        onCardClick={() => setTab('history')} />}

      {tab === 'bia' && <BiaTab bias={bias}
        onApprove={(b) => { transitionBia(b.id, 'BCM_APPROVED', 'Layla Al-Ghamdi (Head of BCM)'); toast.success(t('bclBiaApprovedToast')); }}
        onCreate={(processId, tier, rto, owner) => { createBia(processId, tier, rto, owner); toast.success(t('bclBiaCreatedToast')); }} />}
      {tab === 'strategy' && <StrategyTab strategies={strategies} bias={bias}
        onTransition={(s, status) => { transitionStrategy(s.id, status); toast.info(`Strategy ${status}`); }}
        onCreate={(processId, primary, alternate, owner) => { createStrategy(processId, primary, alternate, owner); toast.success(t('bclStrategyCreatedToast')); }} />}
      {tab === 'drills' && <DrillsTab drills={drills}
        onTransition={(d, s) => { transitionDrill(d.id, s); toast.info(`${DRILL_STATUS_LABEL[s][lang]}`); }}
        onSchedule={(planId, type, titleEn, titleAr, date, ncema) => { scheduleDrill(planId, type, titleEn, titleAr, date, ncema); toast.success(t('bclDrillScheduledToast')); }} />}
      {tab === 'invocation' && <InvocationTab active={active}
        onDeclare={(planId, processIds, isReal) => {
          const plan = pickPlanById(planId);
          declareInvocation(planId, processIds,
            `Disruption affecting ${plan?.planNameEn ?? 'plan'} — recovery procedures triggered.`,
            `انقطاع يؤثّر على ${plan?.planNameAr ?? 'الخطة'} — تفعيل إجراءات التعافي.`,
            isReal, 'Ahmed Al-Qahtani (CTO)');
          toast.danger(isReal ? t('bclInvocationDeclaredToast') : t('bclDrillDeclaredToast'));
        }}
        onLogNotification={(invId, regulator, ref) => { logInvocationNotification(invId, regulator as any, ref); toast.success(t('bclNotifLoggedToast')); }} />}
      {tab === 'history' && <HistoryTab invocations={invocations} />}
      {tab === 'packet' && <PacketTab stats={stats} bias={bias} drills={drills} />}
    </Shell>
  );
}

// ─── Active invocation banner ───────────────────────────────────────────────

function ActiveInvocationBanner({ active, onTransition }: {
  active: Invocation;
  onTransition: (status: InvocationStatus, narrEn?: string, narrAr?: string) => void;
}) {
  const { t, lang } = useI18n();
  const plan = pickPlanById(active.planId);
  const elapsed = active.invokedAt
    ? Math.round((Date.now() - new Date(active.invokedAt).getTime()) / 360_000) / 10
    : null;

  return (
    <div className="bcl-active-banner" data-tone={active.status === 'INVOKED' ? 'invoked' : 'declared'}>
      <div className="bcl-active-banner__head">
        <div className="bcl-active-banner__main">
          <div className="bcl-active-banner__chips">
            <Siren size={20} color={active.status === 'INVOKED' ? 'var(--danger-fg)' : 'var(--warning-fg)'} />
            <span className="mono bcl-active-banner__id">{active.id}</span>
            <Badge tone={INVOCATION_STATUS_TONE[active.status]}>{INVOCATION_STATUS_LABEL[active.status][lang]}</Badge>
            <Badge tone={active.isReal ? 'danger' : 'neutral'}>{active.isReal ? t('bclReal') : t('bclDrill')}</Badge>
            {elapsed != null && <Badge tone="warning">T+{elapsed}h</Badge>}
          </div>
          <div className="bcl-active-banner__title">{plan ? (lang === 'ar' ? plan.planNameAr : plan.planNameEn) : active.planId}</div>
          <div className="bcl-active-banner__body">
            {lang === 'ar' ? active.disruptionAr : active.disruptionEn}
          </div>
          <div className="bcl-active-banner__meta">
            {t('bclCommander')}: <b>{active.commander}</b>
          </div>
        </div>
        <div className="bcl-active-banner__actions">
          {active.status === 'DECLARED' && <Button variant="danger" Icon={Play} onClick={() => onTransition('INVOKED')}>{t('bclInvoke')}</Button>}
          {active.status === 'INVOKED' && <Button variant="primary" Icon={CheckCircle2} onClick={() => onTransition('STABILISED')}>{t('bclStabilise')}</Button>}
          {active.status === 'STABILISED' && <Button variant="primary" Icon={StopCircle} onClick={() => onTransition('STAND_DOWN')}>{t('bclStandDown')}</Button>}
          {active.status === 'STAND_DOWN' && (
            <Button variant="secondary" onClick={() => onTransition('POST_INCIDENT_REVIEW',
              'Recovery met RTO target. Lessons learned captured.',
              'تحقّق هدف RTO. الدروس المستفادة موثَّقة.')}>{t('bclOpenPir')}</Button>
          )}
        </div>
      </div>

      {/* Notifications quick log */}
      {active.notifications.length > 0 && (
        <div className="bcl-active-banner__notif">
          <div className="bcl-active-banner__notif-label">
            {t('bclRegNotifications')}
          </div>
          <div className="bcl-active-banner__notif-list">
            {active.notifications.map(n => (
              <Badge key={n.regulator} tone={n.sentAt ? 'success' : n.maxHours <= 4 ? 'danger' : 'warning'}>
                {n.regulator} · ≤{n.maxHours}h {n.sentAt ? '✓' : ''}
              </Badge>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ─── BIA tab ─────────────────────────────────────────────────────────────────

function BiaTab({ bias, onApprove, onCreate }: {
  bias: BiaRecord[];
  onApprove: (b: BiaRecord) => void;
  onCreate: (processId: number, tier: ProcessTier, rto: number, owner: string) => void;
}) {
  const { t, lang } = useI18n();
  // Find processes without a BIA — used for create affordance.
  const uncovered = BCM_PROCESSES.find(p => !bias.some(b => b.processId === p.id));
  return (
    <Card title={t('bclBiaTitle')} Icon={ShieldCheck}
      action={uncovered
        ? <Button size="sm" variant="primary" onClick={() => onCreate(uncovered.id, 'TIER-3-OPERATIONAL', 24, 'Process Owner')}>{t('bclBiaCreate')}</Button>
        : null}>
      <div className="bcl-card-intro">
        {t('bclBiaSub')}
      </div>
      <table className="tbl bcl-table-full">
        <thead>
          <tr>
            <th>{t('bclBiaProcess')}</th>
            <th>{t('bclBiaTier')}</th>
            <th>RTO / RPO / MTPD</th>
            <th>{t('bclBiaImpact')}</th>
            <th>{t('bclBiaStatus')}</th>
            <th>{t('bclBiaExpires')}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {bias.map(b => {
            const proc = pickProcessById(b.processId);
            const exp = new Date(b.expiresAt);
            const daysToExpiry = Math.round((exp.getTime() - Date.now()) / 86_400_000);
            const expiringSoon = daysToExpiry > 0 && daysToExpiry < 60;
            const expired = daysToExpiry <= 0;
            const expiryClass = expired ? 'bcl-bia-expiry--expired' : expiringSoon ? 'bcl-bia-expiry--soon' : '';
            return (
              <tr key={b.id}>
                <td>
                  <div className="bcl-bia-process-name">
                    {proc ? (lang === 'ar' ? proc.processNameAr : proc.processNameEn) : b.processId}
                  </div>
                  <div className="mono bcl-bia-process-id">{b.id}</div>
                  <div className="bcl-bia-process-owner">{b.processOwner}</div>
                </td>
                <td><Badge tone={TIER_TONE[b.tier]}>{TIER_LABEL[b.tier][lang]}</Badge></td>
                <td className="mono bcl-bia-numeric">
                  {b.rtoHours}h / {b.rpoHours}h / {b.mtpdHours}h
                </td>
                <td>
                  <div className="mono bcl-bia-impact-value">
                    {(b.financialImpactSarPerHour / 1000).toFixed(0)}k SAR/h
                  </div>
                  <div className="bcl-bia-impact-text">
                    {lang === 'ar' ? b.regulatoryImpactAr : b.regulatoryImpactEn}
                  </div>
                </td>
                <td><Badge tone={BIA_STATUS_TONE[b.status]}>{BIA_STATUS_LABEL[b.status][lang]}</Badge></td>
                <td className={`mono bcl-bia-expiry ${expiryClass}`}>
                  {b.expiresAt}
                  {expiringSoon && <div className="bcl-bia-expiry__delta">{daysToExpiry}d</div>}
                </td>
                <td>
                  {b.status === 'DEPT_REVIEW' && <Button size="sm" variant="primary" Icon={ThumbsUp} onClick={() => onApprove(b)}>{t('bclApprove')}</Button>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Card>
  );
}

// ─── Strategy tab ────────────────────────────────────────────────────────────

function StrategyTab({ strategies, bias: _bias, onTransition, onCreate }: {
  strategies: RecoveryStrategy[]; bias: BiaRecord[];
  onTransition: (s: RecoveryStrategy, status: StrategyStatus) => void;
  onCreate: (processId: number, primary: RecoverySite, alternate: RecoverySite, owner: string) => void;
}) {
  const { t, lang } = useI18n();
  // Find a process that doesn't have a strategy yet for "create" affordance.
  const uncoveredProc = BCM_PROCESSES.find(p => !strategies.some(s => s.processId === p.id));
  return (
    <Card title={t('bclStrategyTitle')} Icon={GitBranch}
      action={uncoveredProc
        ? <Button size="sm" variant="primary" onClick={() => onCreate(uncoveredProc.id, 'RIYADH-PRIMARY', 'JEDDAH-DR', 'Layla Al-Ghamdi (Head of BCM)')}>{t('bclStrategyCreate')}</Button>
        : null}>
      <div className="bcl-card-intro">
        {t('bclStrategySub')}
      </div>
      {strategies.map(s => {
        const proc = pickProcessById(s.processId);
        return (
          <div key={s.id} className="bcl-card">
            <div className="bcl-card-head">
              <div className="bcl-card-head__main">
                <div className="bcl-card-head__chips">
                  <span className="mono bcl-card-id">{s.id}</span>
                  <Badge tone={STRATEGY_STATUS_TONE[s.status]}>{statusLabel(s.status, lang)}</Badge>
                  <Badge tone={s.maturity >= 4 ? 'success' : s.maturity >= 3 ? 'info' : 'warning'}>
                    {t('bclMaturity')} {s.maturity}/5
                  </Badge>
                  <Badge tone="primary">{(s.annualCostSar / 1_000_000).toFixed(2)}M SAR/y</Badge>
                </div>
                <div className="bcl-card-title">
                  {proc ? (lang === 'ar' ? proc.processNameAr : proc.processNameEn) : s.processId}
                </div>
                <div className="bcl-card-sub">
                  {t('bclActivationOwner')}: <b>{s.activationOwner}</b>
                </div>
              </div>
              {/* Strategy transition buttons */}
              <div className="bcl-card-actions">
                {s.status === 'DRAFT' && <Button size="sm" variant="primary" onClick={() => onTransition(s, 'TESTED')}>{t('bclStrategyMarkTested')}</Button>}
                {s.status === 'TESTED' && <Button size="sm" variant="primary" onClick={() => onTransition(s, 'APPROVED')}>{t('bclStrategyApprove')}</Button>}
                {s.status === 'APPROVED' && <Button size="sm" variant="ghost" onClick={() => onTransition(s, 'NEEDS_UPDATE')}>{t('bclStrategyMarkUpdate')}</Button>}
                {s.status === 'NEEDS_UPDATE' && <Button size="sm" variant="primary" onClick={() => onTransition(s, 'TESTED')}>{t('bclStrategyRetest')}</Button>}
              </div>
            </div>

            {/* Site flow */}
            <div className="bcl-site-flow">
              <Badge tone="success">{SITE_LABEL[s.primarySite][lang]}</Badge>
              <ArrowRight size={14} color="var(--ink-3)" />
              <Badge tone="info">{SITE_LABEL[s.alternateSite][lang]}</Badge>
              <ArrowRight size={14} color="var(--ink-3)" />
              <Badge tone="warning">{SITE_LABEL['MANUAL-PROCESS'][lang]}</Badge>
              {s.lastTestedAt && (
                <span className="bcl-site-flow__test">
                  {t('bclLastTested')}: <span className="mono">{s.lastTestedAt.slice(0, 10)}</span>
                </span>
              )}
            </div>

            <div className="bcl-field-grid-2">
              <FieldBlock label={t('bclManualWorkaround')} value={lang === 'ar' ? s.manualWorkaroundAr : s.manualWorkaroundEn} />
              <FieldBlock label={t('bclTechDeps')} value={lang === 'ar' ? s.techDependenciesAr : s.techDependenciesEn} />
              <FieldBlock label={t('bclPremises')} value={lang === 'ar' ? s.premisesAr : s.premisesEn} />
              <div>
                <div className="bcl-field-label">
                  {t('bclDependencies')}
                </div>
                <div className="bcl-field-badges">
                  <Badge tone="neutral">👥 {s.peopleMin} {t('bclPeople')}</Badge>
                  {s.supplierDependencies.map(sup => <Badge key={sup} tone="info">{sup}</Badge>)}
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </Card>
  );
}

function FieldBlock({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="bcl-field-label">{label}</div>
      <div className="bcl-field-value">{value}</div>
    </div>
  );
}

// ─── Drills tab ──────────────────────────────────────────────────────────────

function DrillsTab({ drills, onTransition, onSchedule }: {
  drills: Drill[];
  onTransition: (d: Drill, s: Drill['status']) => void;
  onSchedule: (planId: number, type: DrillType, titleEn: string, titleAr: string, date: string, ncema: boolean) => void;
}) {
  const { t, lang } = useI18n();
  const sorted = [...drills].sort((a, b) => new Date(b.scheduledDate).getTime() - new Date(a.scheduledDate).getTime());

  const nextQuarterDate = new Date(Date.now() + 60 * 86_400_000).toISOString().slice(0, 10);
  return (
    <Card title={t('bclDrillsTitle')} Icon={Calendar}
      action={<Button size="sm" variant="primary" onClick={() => onSchedule(BCM_PLANS[0].id, 'TABLETOP', `Quick tabletop drill ${nextQuarterDate}`, `تمرين طاولة سريع ${nextQuarterDate}`, nextQuarterDate, false)}>{t('bclDrillSchedule')}</Button>}>
      <div className="bcl-card-intro">
        {t('bclDrillsSub')}
      </div>
      {sorted.map(d => {
        const plan = pickPlanById(d.planId);
        const rtoMet = d.rtoActualHours != null && d.rtoTargetHours != null && d.rtoActualHours <= d.rtoTargetHours;
        return (
          <div key={d.id} className="bcl-card">
            <div className="bcl-card-head">
              <div className="bcl-card-head__main">
                <div className="bcl-card-head__chips">
                  <span className="mono bcl-card-id">{d.id}</span>
                  <Badge tone={DRILL_STATUS_TONE[d.status]}>{DRILL_STATUS_LABEL[d.status][lang]}</Badge>
                  <Badge tone="info">{DRILL_TYPE_LABEL[d.type][lang]}</Badge>
                  {d.ncemaLed && <Badge tone="warning">NCEMA</Badge>}
                  {d.score != null && <Badge tone={d.score >= 80 ? 'success' : 'warning'}>{d.score}/100</Badge>}
                </div>
                <div className="bcl-card-title">
                  {lang === 'ar' ? d.titleAr : d.titleEn}
                </div>
                <div className="bcl-card-scenario">
                  {lang === 'ar' ? d.scenarioAr : d.scenarioEn}
                </div>
                <div className="bcl-card-meta">
                  {plan?.planNameEn} · {t('bclFacilitator')}: {d.facilitator} · {d.participantsCount} {t('bclParticipants')}
                  {' · '} <span className="mono">{d.scheduledDate}</span>
                </div>
              </div>
              <div className="bcl-card-actions">
                {d.status === 'PLANNED' && <Button size="sm" variant="primary" Icon={Play} onClick={() => onTransition(d, 'IN_PROGRESS')}>{t('bclStart')}</Button>}
                {d.status === 'IN_PROGRESS' && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'EXECUTED')}>{t('bclMarkExecuted')}</Button>}
                {d.status === 'EXECUTED' && <Button size="sm" variant="primary" onClick={() => onTransition(d, 'AFTER_ACTION')}>{t('bclOpenAfterAction')}</Button>}
                {d.status === 'AFTER_ACTION' && <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onTransition(d, 'CLOSED')}>{t('bclCloseDrill')}</Button>}
              </div>
            </div>

            {/* RTO actual vs target */}
            {d.rtoActualHours != null && d.rtoTargetHours != null && (
              <div className="bcl-rto-row">
                <span className="bcl-rto-row__label">{t('bclRtoActual')}:</span>
                <span className={`mono bcl-rto-row__actual${rtoMet ? ' bcl-rto-row__actual--met' : ''}`}>
                  {d.rtoActualHours}h
                </span>
                <span className="bcl-rto-row__label">vs target</span>
                <span className="mono">{d.rtoTargetHours}h</span>
                {rtoMet
                  ? <Badge tone="success"><CheckCircle2 size={11} /> {t('bclRtoMet')}</Badge>
                  : <Badge tone="danger">{t('bclRtoMissed')}</Badge>}
              </div>
            )}

            {/* After-action narrative */}
            {(d.whatWorkedEn || d.whatDidntEn) && (
              <div className="bcl-aa-grid">
                {d.whatWorkedEn && (
                  <div className="bcl-aa-block">
                    <div className="bcl-aa-block__label">{t('bclWhatWorked')}</div>
                    <div className="bcl-aa-block__body">
                      {lang === 'ar' ? d.whatWorkedAr : d.whatWorkedEn}
                    </div>
                  </div>
                )}
                {d.whatDidntEn && (
                  <div className="bcl-aa-block bcl-aa-block--warning">
                    <div className="bcl-aa-block__label">
                      {t('bclWhatDidnt')} {d.capaCount > 0 && `· ${d.capaCount} CAPA`}
                    </div>
                    <div className="bcl-aa-block__body">
                      {lang === 'ar' ? d.whatDidntAr : d.whatDidntEn}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        );
      })}
    </Card>
  );
}

// ─── Invocation tab ──────────────────────────────────────────────────────────

function InvocationTab({ active, onDeclare, onLogNotification }: {
  active: Invocation | undefined;
  onDeclare: (planId: number, processIds: number[], isReal: boolean) => void;
  onLogNotification: (invId: string, regulator: string, ref: string) => void;
}) {
  const { t, lang } = useI18n();
  const [planId, setPlanId] = useState<number>(BCM_PLANS[0]?.id ?? 1);
  const [processIds, setProcessIds] = useState<number[]>([BCM_PROCESSES[0]?.id ?? 11]);
  const [refs, setRefs] = useState<Record<string, string>>({});

  function toggleProcess(id: number) {
    setProcessIds(p => p.includes(id) ? p.filter(x => x !== id) : [...p, id]);
  }

  return (
    <>
      {!active && (
        <Card title={t('bclDeclareTitle')} Icon={Siren}>
          <div className="bcl-card-intro">
            {t('bclDeclareSub')}
          </div>

          <div className="bcl-section">
            <div className="bcl-section-label">
              {t('bclSelectPlan')}
            </div>
            <select value={planId} onChange={e => setPlanId(Number(e.target.value))} className="bcl-input">
              {BCM_PLANS.map(p => <option key={p.id} value={p.id}>{p.planNameEn}</option>)}
            </select>
          </div>

          <div className="bcl-section">
            <div className="bcl-section-label">
              {t('bclAffectedProcesses')}
            </div>
            <div className="bcl-process-chip-row">
              {BCM_PROCESSES.slice(0, 8).map(p => {
                const isOn = processIds.includes(p.id);
                return (
                  <button key={p.id} onClick={() => toggleProcess(p.id)}
                    className={`bcl-process-chip${isOn ? ' is-active' : ''}`}>
                    {lang === 'ar' ? p.processNameAr : p.processNameEn}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="bcl-declare-actions">
            <Button variant="danger" Icon={Siren} onClick={() => onDeclare(planId, processIds, true)}>
              {t('bclDeclareReal')}
            </Button>
            <Button variant="secondary" Icon={Play} onClick={() => onDeclare(planId, processIds, false)}>
              {t('bclDeclareDrill')}
            </Button>
          </div>
        </Card>
      )}

      {active && (
        <Card title={t('bclActiveStageLog')} Icon={Clock}>
          <div className="bcl-stage-list">
            {active.stageLog.map((s, i) => (
              <div key={i} className="bcl-stage-row">
                <div className="bcl-stage-bullet">{i + 1}</div>
                <div>
                  <div className="bcl-stage-text">{lang === 'ar' ? s.stepAr : s.stepEn}</div>
                  <div className="bcl-stage-role">{s.byRole}</div>
                </div>
                <div className="mono bcl-stage-time">
                  {new Date(s.atTs).toLocaleTimeString()}
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* Regulatory notifications */}
      {active && active.notifications.length > 0 && (
        <div className="bcl-notif-section">
          <Card title={t('bclRegNotifications')} Icon={FileText}>
            <div className="bcl-card-intro">
              {t('bclRegNotificationsSub')}
            </div>
            {active.notifications.map(n => (
              <div key={n.regulator} className="bcl-notif-row">
                <div>
                  <div className="bcl-notif-row__head">
                    <Badge tone={n.maxHours <= 4 ? 'danger' : n.maxHours <= 24 ? 'warning' : 'info'}>
                      {n.regulator} · ≤ {n.maxHours}h
                    </Badge>
                    {n.sentAt && <Badge tone="success"><CheckCircle2 size={11} /> {t('bclSent')}</Badge>}
                    {n.referenceId && (
                      <span className="mono bcl-notif-ref">
                        ref {n.referenceId}
                      </span>
                    )}
                  </div>
                  <div className="bcl-notif-body">
                    {lang === 'ar' ? n.basisAr : n.basisEn}
                  </div>
                </div>
                {!n.sentAt && (
                  <div className="bcl-notif-actions">
                    <input
                      placeholder={t('bclRefPlaceholder')}
                      value={refs[n.regulator] ?? ''}
                      onChange={e => setRefs(r => ({ ...r, [n.regulator]: e.target.value }))}
                      className="mono bcl-notif-input" />
                    <Button size="sm" variant="primary"
                      onClick={() => { onLogNotification(active.id, n.regulator, refs[n.regulator] ?? ''); setRefs(r => ({ ...r, [n.regulator]: '' })); }}>
                      {t('bclLog')}
                    </Button>
                  </div>
                )}
              </div>
            ))}
          </Card>
        </div>
      )}
    </>
  );
}

// ─── History tab ─────────────────────────────────────────────────────────────

function HistoryTab({ invocations }: { invocations: Invocation[] }) {
  const { t, lang } = useI18n();
  const sorted = [...invocations].sort((a, b) => new Date(b.declaredAt).getTime() - new Date(a.declaredAt).getTime());
  return (
    <Card title={t('bclHistoryTitle')} Icon={Activity}>
      <div className="bcl-card-intro">
        {t('bclHistorySub')}
      </div>
      {sorted.length === 0
        ? <div className="bcl-history-empty">
            {t('bclHistoryEmpty')}
          </div>
        : sorted.map(inv => {
            const plan = pickPlanById(inv.planId);
            return (
              <div key={inv.id} className="bcl-history-card">
                <div className="bcl-card-head">
                  <div className="bcl-card-head__main">
                    <div className="bcl-card-head__chips">
                      <span className="mono bcl-active-banner__id">{inv.id}</span>
                      <Badge tone={INVOCATION_STATUS_TONE[inv.status]}>{INVOCATION_STATUS_LABEL[inv.status][lang]}</Badge>
                      <Badge tone={inv.isReal ? 'danger' : 'neutral'}>{inv.isReal ? t('bclReal') : t('bclDrill')}</Badge>
                      {inv.actualRtoHours != null && <Badge tone="primary">RTO {inv.actualRtoHours}h</Badge>}
                    </div>
                    <div className="bcl-history-title">
                      {plan ? (lang === 'ar' ? plan.planNameAr : plan.planNameEn) : inv.planId}
                    </div>
                    <div className="bcl-card-scenario">
                      {lang === 'ar' ? inv.disruptionAr : inv.disruptionEn}
                    </div>
                  </div>
                  <div className="mono bcl-history-time">
                    <div>{inv.declaredAt.slice(0, 10)} {inv.declaredAt.slice(11, 16)}</div>
                    {inv.standDownAt && <div>→ {inv.standDownAt.slice(0, 10)} {inv.standDownAt.slice(11, 16)}</div>}
                  </div>
                </div>
                {inv.lessonsLearnedEn && (
                  <div className="bcl-history-lessons">
                    <div className="bcl-history-lessons__label">{t('bclLessons')}</div>
                    {lang === 'ar' ? inv.lessonsLearnedAr : inv.lessonsLearnedEn}
                  </div>
                )}
              </div>
            );
          })
      }
    </Card>
  );
}

// ─── Packet preview tab ──────────────────────────────────────────────────────

function PacketTab({ stats, bias, drills }: { stats: ReturnType<typeof aggregateBcm>; bias: BiaRecord[]; drills: Drill[] }) {
  const { t, lang } = useI18n();
  const tier1 = bias.filter(b => b.tier === 'TIER-1-NATIONAL');
  const completedDrills = drills.filter(d => d.status === 'CLOSED');
  return (
    <Card title={t('bclPacketTitle')} Icon={FileText}>
      <div className="bcl-card-intro">
        {t('bclPacketSub')}
      </div>

      <div className="bcl-packet-stat-grid">
        <Stat label={t('bclPacketTierCov')} value={`${stats.tier1Count + stats.tier2Count}`} unit={t('bclPacketCriticalProcs')} tone="primary" />
        <Stat label={t('bclPacketRtoRate')} value={`${stats.rtoComplianceRate}%`} unit={t('bclPacketLastDrills')} tone={stats.rtoComplianceRate >= 80 ? 'success' : 'warning'} />
        <Stat label={t('bclPacketDrillRecency')} value={`${stats.drillsCompleted}`} unit={t('bclPacketCompleted')} tone="info" />
      </div>

      <div className="bcl-packet-section">
        <div className="bcl-packet-heading">{t('bclPacketTier1')}</div>
        <table className="tbl bcl-table-full">
          <thead>
            <tr>
              <th>{t('bclBiaProcess')}</th>
              <th>RTO</th>
              <th>RPO</th>
              <th>{t('bclBiaImpact')}</th>
              <th>{t('bclBiaExpires')}</th>
            </tr>
          </thead>
          <tbody>
            {tier1.map(b => {
              const proc = pickProcessById(b.processId);
              return (
                <tr key={b.id}>
                  <td>{proc ? (lang === 'ar' ? proc.processNameAr : proc.processNameEn) : b.processId}</td>
                  <td className="mono">{b.rtoHours}h</td>
                  <td className="mono">{b.rpoHours}h</td>
                  <td className="mono">{(b.financialImpactSarPerHour / 1000).toFixed(0)}k SAR/h</td>
                  <td className="mono">{b.expiresAt}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div>
        <div className="bcl-packet-heading">{t('bclPacketRecentDrills')}</div>
        {completedDrills.slice(0, 5).map(d => {
          const rtoMet = d.rtoActualHours != null && d.rtoTargetHours != null && d.rtoActualHours <= d.rtoTargetHours;
          return (
            <div key={d.id} className="bcl-packet-drill-row">
              <span className="mono bcl-packet-drill-row__date">{d.scheduledDate}</span>
              <Badge tone="info">{DRILL_TYPE_LABEL[d.type][lang]}</Badge>
              <span className="bcl-packet-drill-row__title">{lang === 'ar' ? d.titleAr : d.titleEn}</span>
              {d.rtoActualHours != null && (
                <Badge tone={rtoMet ? 'success' : 'warning'}>RTO {d.rtoActualHours}h / {d.rtoTargetHours}h</Badge>
              )}
              {d.score != null && <Badge tone={d.score >= 80 ? 'success' : 'warning'}>{d.score}/100</Badge>}
            </div>
          );
        })}
      </div>

      <div className="bcl-packet-hint">
        {t('bclPacketHint')}
      </div>
    </Card>
  );
}

function Stat({ label, value, unit, tone }: { label: string; value: string; unit?: string; tone: 'primary' | 'success' | 'warning' | 'danger' | 'info' }) {
  return (
    <div className="bcl-stat-tile" data-tone={tone}>
      <div className="bcl-stat-tile__label">{label}</div>
      <div className="mono bcl-stat-tile__value">{value}</div>
      {unit && <div className="bcl-stat-tile__unit">{unit}</div>}
    </div>
  );
}

// ─── Invocation pipeline (interactive kanban) ──────────────────────────────

function InvocationPipeline({ invocations, onAdvance, onCardClick }: {
  invocations: Invocation[];
  onAdvance: (id: string, fromStatus: string, toStatus: string | null) => void;
  onCardClick: (id: string) => void;
}) {
  const stages: PipelineStage[] = [
    { code: 'STANDBY',               labelEn: 'Standby',                 labelAr: 'استعداد',                tone: 'neutral', Icon: ShieldCheck },
    { code: 'DECLARED',              labelEn: 'Declared',                labelAr: 'مُعلَن',                  tone: 'warning', Icon: Siren },
    { code: 'INVOKED',               labelEn: 'Invoked',                 labelAr: 'مُفعَّل',                  tone: 'danger',  Icon: Play },
    { code: 'STABILISED',            labelEn: 'Stabilised',              labelAr: 'مستقرّ',                  tone: 'info',    Icon: CheckCircle2 },
    { code: 'STAND_DOWN',            labelEn: 'Stand-down',              labelAr: 'مُنهى',                   tone: 'success', Icon: StopCircle },
    { code: 'POST_INCIDENT_REVIEW',  labelEn: 'Post-incident review',     labelAr: 'مراجعة ما بعد الحادثة',  tone: 'primary', Icon: FileText },
  ];
  const cards: PipelineCard[] = invocations.map(inv => {
    const plan = pickPlanById(inv.planId);
    return {
      id: inv.id, status: inv.status, refCode: inv.id,
      titleEn: plan?.planNameEn ?? `Plan ${inv.planId}`,
      titleAr: plan?.planNameAr ?? `الخطة ${inv.planId}`,
      subtitleEn: `${inv.commander} · ${inv.affectedProcessIds.length} processes affected`,
      subtitleAr: `${inv.commander} · ${inv.affectedProcessIds.length} عمليات متأثّرة`,
      badges: [
        { label: inv.isReal ? 'REAL' : 'DRILL', tone: inv.isReal ? 'danger' : 'neutral' as const },
        ...(inv.actualRtoHours != null ? [{ label: `RTO ${inv.actualRtoHours}h`, tone: 'primary' as const }] : []),
      ],
    };
  });
  return (
    <LifecyclePipeline
      stages={stages}
      cards={cards}
      introEn="Live BCP invocation pipeline — DECLARED → INVOKED → STABILISED → STAND_DOWN → POST_INCIDENT_REVIEW. Click a card to drill into the history tab."
      introAr="مسار تفعيل خطط الاستمرارية الحيّ — مُعلَن ← مُفعَّل ← مستقرّ ← مُنهى ← مراجعة ما بعد الحادثة. انقر على بطاقة لفتح تبويب السجل."
      onAdvance={onAdvance}
      onCardClick={onCardClick}
      emptyEn="No invocations recorded yet. Declare one from the Live invocation tab."
      emptyAr="لا تفعيلات مُسجَّلة بعد. أعلِن واحداً من تبويب التفعيل الحيّ."
    />
  );
}
