import { useEffect, useMemo, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  Siren, Calendar, Users, ShieldAlert, AlertOctagon, Award, Phone,
  Play, StopCircle, Download, CheckCircle2, Clock, Building2,
  PhoneCall, FileText,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { printPacket, packetTextToSections } from '../../lib/packetPrint';
import { HijriDate } from '../../components/ui/HijriDate';
import {
  SCENARIOS, EXERCISES,
  aggregateCrisis,
  CATEGORY_LABEL, TYPE_LABEL,
  ACTIVATION_TONE, ACTIVATION_LABEL, SCC_LABEL,
  loadActive, loadHistory, loadSccTier, saveSccTier,
  startActivation, transitionActivation, ackContact, logRegulatoryNotification,
  subscribeCrisis, buildNcemaPacket,
  type ActivationEvent, type SccTier, type ActivationStatus,
} from '../../lib/crisis';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';
import '../../styles/pages/crisis-management.css';

/**
 * CrisisManagementPage — Sprint 6 (C4) + production hardening.
 *
 * NCEMA-aligned crisis management with live activation flow:
 *   1. SCC tier selector (drives notification windows)
 *   2. Active activation banner (when running)
 *   3. Scenario register + drill-in detail with call tree
 *   4. Activation history
 *   5. Exercise calendar
 *
 * Live state machine: STANDBY → ELEVATED → ACTIVATED → STAND_DOWN.
 * NCEMA report packet generator emits a UTF-8 file ready for Haseen
 * portal upload.
 */
export function CrisisManagementPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const stats = useMemo(() => aggregateCrisis(), []);
  const [selectedScenario, setSelectedScenario] = useState<string>(SCENARIOS[0].code);
  const current = SCENARIOS.find(s => s.code === selectedScenario) ?? SCENARIOS[0];

  // Live activation state — reactive to localStorage + multi-tab.
  const [active, setActive] = useState<ActivationEvent | null>(() => loadActive());
  const [history, setHistory] = useState<ActivationEvent[]>(() => loadHistory());
  const [sccTier, setSccTier] = useState<SccTier>(() => loadSccTier());

  useEffect(() => subscribeCrisis(() => {
    setActive(loadActive());
    setHistory(loadHistory());
    setSccTier(loadSccTier());
  }), []);

  // White-label org name for the NCEMA packet header.
  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);

  const upcomingExercises = EXERCISES
    .filter(e => e.status === 'PLANNED' || e.status === 'IN_PROGRESS')
    .sort((a, b) => new Date(a.scheduledFor).getTime() - new Date(b.scheduledFor).getTime());
  const completedExercises = EXERCISES
    .filter(e => e.status === 'COMPLETED')
    .sort((a, b) => new Date(b.scheduledFor).getTime() - new Date(a.scheduledFor).getTime());

  // ─── Activation actions ────────────────────────────────────────────────────

  function handleStart(isDrill: boolean) {
    if (active) {
      toast.danger(t('crisisErrAlreadyActive'));
      return;
    }
    const ev = startActivation(selectedScenario, isDrill);
    setActive(ev);
    toast.success(isDrill ? t('crisisDrillStartedToast') : t('crisisActivationStartedToast'));
  }

  function handleTransition(status: ActivationStatus) {
    if (!active) return;
    if (status === 'STAND_DOWN' && !confirm(t('crisisStandDownConfirm'))) return;
    const updated = transitionActivation(status);
    setActive(updated);
    toast.info(`${t('crisisStatus')}: ${ACTIVATION_LABEL[status][lang]}`);
  }

  function handleAck(contactId: string) {
    if (!active) return;
    setActive(ackContact(contactId));
    toast.success(t('crisisAckedToast'));
  }

  function handleLogNotification(regulator: string, ref: string) {
    if (!active || !ref.trim()) return;
    setActive(logRegulatoryNotification(regulator as any, ref.trim()));
    toast.success(t('crisisNotificationLoggedToast'));
  }

  function downloadNcemaPacket() {
    if (!active) return;
    const text = buildNcemaPacket(active, brand.organisationNameEn, brand.organisationNameAr, sccTier);
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `NCEMA-${active.id}.txt`;
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.success(t('crisisPacketDownloadedToast'));
  }

  function printNcemaPdf() {
    if (!active) return;
    const text = buildNcemaPacket(active, brand.organisationNameEn, brand.organisationNameAr, sccTier);
    printPacket({
      titleEn: 'NCEMA / NCA — Initial Situational Awareness Packet',
      titleAr: 'حزمة الإحاطة الأولية — NCEMA / NCA',
      eyebrowEn: 'NCEMA / NCA · Situational Awareness',
      eyebrowAr: 'المركز الوطني لإدارة الطوارئ · إحاطة أولية',
      referenceId: `NCEMA-${active.id}`,
      classificationEn: 'CONFIDENTIAL — REGULATOR',
      classificationAr: 'سرّي — جهة تنظيمية',
      sections: packetTextToSections(text),
    }, lang);
  }

  return (
    <Shell title={t('crisisTitle')} sub={t('crisisSub')}>
      <PageHeader
        eyebrow={t('crisisEyebrow')}
        title={t('crisisTitle')}
        sub={t('crisisPageSub')}
      />

      {/* SCC Tier band */}
      <Card>
        <div className="crisis-scc-row">
          <div className="crisis-scc-label">
            <Building2 size={18} color="var(--primary)" />
            <div>
              <div className="crisis-scc-eyebrow">
                {t('crisisSccLabel')}
              </div>
              <div className="crisis-scc-value">
                {SCC_LABEL[sccTier][lang]}
              </div>
            </div>
          </div>
          <div className="crisis-scc-chip-row">
            {(['TIER-1-NATIONAL', 'TIER-2-SECTORAL', 'TIER-3-OPERATIONAL', 'TIER-4-LOCAL'] as SccTier[]).map(tier => (
              <button key={tier} onClick={() => { saveSccTier(tier); setSccTier(tier); }}
                className={`crisis-scc-chip${sccTier === tier ? ' crisis-scc-chip--on' : ''}`}>
                {SCC_LABEL[tier][lang]}
              </button>
            ))}
          </div>
        </div>
        <div className="crisis-scc-desc">
          {lang === 'ar' ? SCC_LABEL[sccTier].descriptionAr : SCC_LABEL[sccTier].descriptionEn}
        </div>
      </Card>

      {/* Active activation banner */}
      {active && (
        <ActiveBanner
          active={active}
          onTransition={handleTransition}
          onDownloadPacket={downloadNcemaPacket}
          onPrintPdf={printNcemaPdf}
        />
      )}

      <div className="kpi-grid crisis-kpi-row">
        <Kpi Icon={Siren}        label={t('crisisKpiScenarios')}      value={String(stats.totalScenarios)} tone="primary"
             onClick={() => document.getElementById('crisis-scenario-register')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={ShieldAlert}    label={t('crisisKpiCyber')}            value={String(stats.cyberScenarios)} tone="warning"
             onClick={() => document.getElementById('crisis-scenario-register')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={AlertOctagon}   label={t('crisisKpiHighImpact')}       value={String(stats.highImpactScenarios)} tone="danger"
             onClick={() => document.getElementById('crisis-scenario-register')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={Calendar}        label={t('crisisKpiPlanned')}          value={String(stats.exercisesPlanned)} tone="info"
             onClick={() => document.getElementById('crisis-exercise-calendar')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={Users}            label={t('crisisKpiCrossSector')}      value={String(stats.ncemaCrossSector)} tone="primary"
             onClick={() => document.getElementById('crisis-scenario-register')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={Award}            label={t('crisisKpiAvgScore')}         value={String(stats.avgExerciseScore)} tone={stats.avgExerciseScore >= 80 ? 'success' : 'warning'}
             onClick={() => document.getElementById('crisis-exercise-calendar')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={Calendar}         label={t('crisisKpiOldest')}            value={`${stats.oldestExercise}d`} tone={stats.oldestExercise > 365 ? 'danger' : stats.oldestExercise > 180 ? 'warning' : 'success'}
             onClick={() => document.getElementById('crisis-exercise-calendar')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
      </div>

      <div id="crisis-scenario-register" className="crisis-main-grid">
        {/* Scenario picker */}
        <Card padded={false} title={t('crisisScenariosTitle')} Icon={Siren}
          action={<Badge tone="primary">{SCENARIOS.length}</Badge>}>
          <div className="crisis-scenario-list">
            {SCENARIOS.map(s => {
              const isSelected = s.code === selectedScenario;
              const score = s.likelihood * s.impact;
              const isActive = active?.scenarioCode === s.code;
              return (
                <button key={s.code} onClick={() => setSelectedScenario(s.code)}
                  className={`crisis-scenario-btn${isSelected ? ' crisis-scenario-btn--selected' : ''}${isActive && !isSelected ? ' crisis-scenario-btn--active' : ''}`}>
                  <div className="crisis-scenario-head">
                    <span className="mono crisis-scenario-code">{s.code}</span>
                    <Badge tone="info">{lang === 'ar' ? CATEGORY_LABEL[s.category].ar : CATEGORY_LABEL[s.category].en}</Badge>
                    <Badge tone={score >= 15 ? 'danger' : score >= 8 ? 'warning' : 'success'}>L{s.likelihood}×I{s.impact}</Badge>
                    {isActive && <Badge tone="danger">● {t('crisisActiveBadge')}</Badge>}
                  </div>
                  <div className="crisis-scenario-title">
                    {lang === 'ar' ? s.titleAr : s.titleEn}
                  </div>
                  <div className="crisis-scenario-meta">
                    {t('crisisLeader')}: {s.leaderRole} · T+{s.activationTargetMinutes}m · {s.callTree.length} {t('crisisContacts')}
                  </div>
                </button>
              );
            })}
          </div>
        </Card>

        {/* Detail */}
        <div className="crisis-detail-col">
          <Card>
            <div className="crisis-detail-head">
              <span className="mono crisis-detail-code">{current.code}</span>
              <Badge tone="info">{lang === 'ar' ? CATEGORY_LABEL[current.category].ar : CATEGORY_LABEL[current.category].en}</Badge>
              <Badge tone="warning">L{current.likelihood} × I{current.impact}</Badge>
              <Badge tone="primary">{t('crisisActivation')}: T+{current.activationTargetMinutes}m</Badge>
            </div>
            <div className="crisis-detail-title">
              {lang === 'ar' ? current.titleAr : current.titleEn}
            </div>
            <div className="crisis-detail-desc">
              {lang === 'ar' ? current.descriptionAr : current.descriptionEn}
            </div>
            <div className="crisis-detail-stats">
              <div>
                <div className="crisis-stat-label">{t('crisisLeader')}</div>
                <div className="crisis-stat-value">{current.leaderRole}</div>
              </div>
              <div>
                <div className="crisis-stat-label">{t('crisisLastExercise')}</div>
                <HijriDate iso={current.lastExerciseAt} layout="stacked" mono />
              </div>
              <div>
                <div className="crisis-stat-label">{t('crisisLastScore')}</div>
                <div className="mono crisis-stat-score"
                  data-tone={(current.lastExerciseScore ?? 0) >= 80 ? 'success' : 'warning'}>
                  {current.lastExerciseScore ?? '—'}
                </div>
              </div>
            </div>

            {/* Trigger buttons */}
            {!active && (
              <div className="crisis-trigger-row">
                <Button variant="danger" Icon={Siren} onClick={() => handleStart(false)}>
                  {t('crisisStartReal')}
                </Button>
                <Button variant="secondary" Icon={Play} onClick={() => handleStart(true)}>
                  {t('crisisStartDrill')}
                </Button>
              </div>
            )}
          </Card>

          {/* Call tree */}
          <CallTreeCard scenario={current} active={active} onAck={handleAck} />

          {/* Regulatory notifications */}
          <NotificationsCard scenario={current} active={active} onLog={handleLogNotification} />

          {/* Stage log (if activation running) */}
          {active && active.scenarioCode === current.code && active.stageLog.length > 0 && (
            <StageLogCard active={active} />
          )}
        </div>
      </div>

      {/* Activation history */}
      {history.length > 0 && (
        <div className="crisis-section-top">
          <Card title={t('crisisActivationHistoryTitle')} Icon={Siren}>
            <div className="crisis-card-sub">
              {t('crisisActivationHistorySub')}
            </div>
            <div className="crisis-history-list">
              {history.map(ev => (
                <div key={ev.id} className="crisis-history-row">
                  <div className="crisis-history-head">
                    <span className="mono crisis-history-id">{ev.id}</span>
                    <Badge tone={ACTIVATION_TONE[ev.status]}>{lang === 'ar' ? ACTIVATION_LABEL[ev.status].ar : ACTIVATION_LABEL[ev.status].en}</Badge>
                    <span className="mono crisis-history-scenario">{ev.scenarioCode}</span>
                    {ev.actualMinutes != null && <Badge tone="primary">T+{ev.actualMinutes}m</Badge>}
                    <Badge tone={ev.isReal ? 'danger' : 'neutral'}>{ev.isReal ? t('crisisRealBadge') : t('crisisDrillBadge')}</Badge>
                  </div>
                  <div className="crisis-history-notes">
                    {lang === 'ar' ? ev.notesAr : ev.notesEn}
                  </div>
                  <div className="mono crisis-history-times">
                    {ev.startedAt.slice(0, 16).replace('T', ' ')}
                    {ev.endedAt && ` → ${ev.endedAt.slice(0, 16).replace('T', ' ')}`}
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      )}

      {/* Exercise calendar */}
      <div id="crisis-exercise-calendar" className="crisis-section-top">
        <Card title={t('crisisExercisesTitle')} Icon={Calendar}
          action={<Badge tone="primary">{EXERCISES.length}</Badge>}>
          <div className="crisis-card-sub">
            {t('crisisExercisesSub')}
          </div>

          <div className="crisis-section-head">
            {t('crisisUpcoming')}
          </div>
          <div className="crisis-upcoming-list">
            {upcomingExercises.length === 0
              ? <InlineEmpty compact>{t('crisisUpcomingEmpty')}</InlineEmpty>
              : upcomingExercises.map(e => (
                  <div key={e.code} className="crisis-upcoming-row">
                    <div>
                      <div className="crisis-upcoming-title">{lang === 'ar' ? e.titleAr : e.titleEn}</div>
                      <div className="mono crisis-upcoming-code">{e.code}</div>
                    </div>
                    {e.ncemaLed && <Badge tone="warning">NCEMA</Badge>}
                    <Badge tone="info">{lang === 'ar' ? TYPE_LABEL[e.type].ar : TYPE_LABEL[e.type].en}</Badge>
                    <HijriDate iso={e.scheduledFor} layout="stacked" mono />
                  </div>
                ))}
          </div>

          <div className="crisis-section-head">
            {t('crisisCompleted')}
          </div>
          <div className="crisis-completed-list">
            {completedExercises.map(e => (
              <div key={e.code} className="crisis-completed-row">
                <div className="crisis-completed-head">
                  <span className="mono crisis-completed-code">{e.code}</span>
                  <Badge tone="info">{lang === 'ar' ? TYPE_LABEL[e.type].ar : TYPE_LABEL[e.type].en}</Badge>
                  {e.ncemaLed && <Badge tone="warning">NCEMA</Badge>}
                  <Badge tone={(e.outcomeScore ?? 0) >= 80 ? 'success' : 'warning'}>{e.outcomeScore}/100</Badge>
                  <span className="crisis-completed-participants">{e.participantsCount} {t('crisisParticipants')}</span>
                </div>
                <div className="crisis-completed-title">
                  {lang === 'ar' ? e.titleAr : e.titleEn}
                </div>
                <HijriDate iso={e.scheduledFor} layout="inline" mono />
                {(e.lessonsLearnedEn || e.lessonsLearnedAr) && (
                  <div className="crisis-lessons">
                    <div className="crisis-lessons-label">
                      {t('crisisLessonsLearned')}
                    </div>
                    {lang === 'ar' ? e.lessonsLearnedAr : e.lessonsLearnedEn}
                  </div>
                )}
              </div>
            ))}
          </div>
        </Card>
      </div>
    </Shell>
  );
}

// ─── Active activation banner ───────────────────────────────────────────────

function ActiveBanner({ active, onTransition, onDownloadPacket, onPrintPdf }: {
  active: ActivationEvent;
  onTransition: (s: ActivationStatus) => void;
  onDownloadPacket: () => void;
  onPrintPdf: () => void;
}) {
  const { t, lang } = useI18n();
  const scenario = SCENARIOS.find(s => s.code === active.scenarioCode);
  const elapsed = Math.round((Date.now() - new Date(active.startedAt).getTime()) / 60_000);
  const targetExceeded = scenario && elapsed > scenario.activationTargetMinutes;

  return (
    <div className="crisis-banner" data-tone={active.status === 'ACTIVATED' ? 'danger' : 'warning'}>
      <div className="crisis-banner-row">
        <Siren size={28} color={active.status === 'ACTIVATED' ? 'var(--danger-fg)' : 'var(--warning-fg)'}
          className="crisis-banner-icon" />
        <div className="crisis-banner-main">
          <div className="crisis-banner-badges">
            <Badge tone={ACTIVATION_TONE[active.status]}>{ACTIVATION_LABEL[active.status][lang]}</Badge>
            <Badge tone={active.isReal ? 'danger' : 'neutral'}>
              {active.isReal ? t('crisisRealBadge') : t('crisisDrillBadge')}
            </Badge>
            <span className="mono crisis-history-id">{active.id}</span>
          </div>
          <div className="crisis-banner-title">
            {scenario ? (lang === 'ar' ? scenario.titleAr : scenario.titleEn) : active.scenarioCode}
          </div>
          <div className="crisis-banner-meta">
            {t('crisisStarted')}: <span className="mono">{new Date(active.startedAt).toLocaleString()}</span>
            {' · '}
            <Clock size={11} className="crisis-contact-link-icon" />
            {' '}
            <span className={`crisis-banner-elapsed${targetExceeded ? ' crisis-banner-elapsed--late' : ''}`}>
              T+{elapsed}m
            </span>
            {scenario && ` / ${t('crisisTarget')} T+${scenario.activationTargetMinutes}m`}
          </div>
        </div>
        <div className="crisis-banner-actions">
          {active.status === 'ELEVATED' && (
            <Button variant="danger" Icon={Siren} onClick={() => onTransition('ACTIVATED')}>
              {t('crisisTransitionActivated')}
            </Button>
          )}
          {active.status === 'ACTIVATED' && (
            <Button variant="secondary" onClick={() => onTransition('ELEVATED')}>
              {t('crisisDowngrade')}
            </Button>
          )}
          <Button variant="primary" Icon={Download} onClick={onPrintPdf}>
            {lang === 'ar' ? 'تنزيل PDF' : 'Download PDF'}
          </Button>
          <Button variant="secondary" Icon={Download} onClick={onDownloadPacket}>
            {t('crisisDownloadPacket')}
          </Button>
          <Button variant="ghost" Icon={StopCircle} onClick={() => onTransition('STAND_DOWN')}>
            {t('crisisStandDown')}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ─── Call tree card ─────────────────────────────────────────────────────────

function CallTreeCard({ scenario, active, onAck }: {
  scenario: typeof SCENARIOS[number];
  active: ActivationEvent | null;
  onAck: (id: string) => void;
}) {
  const { t, lang } = useI18n();
  const isActiveScenario = active?.scenarioCode === scenario.code;

  // Group by step.
  const steps = useMemo(() => {
    const map = new Map<number, typeof scenario.callTree>();
    scenario.callTree.forEach(c => {
      if (!map.has(c.step)) map.set(c.step, []);
      map.get(c.step)!.push(c);
    });
    return Array.from(map.entries()).sort((a, b) => a[0] - b[0]);
  }, [scenario]);

  return (
    <Card title={t('crisisCallTreeTitle')} Icon={PhoneCall}
      action={<Badge tone="primary">{scenario.callTree.length} {t('crisisContacts')}</Badge>}>
      <div className="crisis-card-sub">
        {t('crisisCallTreeSub')}
      </div>
      <div className="crisis-calltree-list">
        {steps.map(([step, contacts]) => (
          <div key={step} className="crisis-calltree-step">
            <div className="crisis-calltree-step-label">
              {t('crisisStep')} {step}
            </div>
            {contacts.map(c => {
              const stage = active?.stageLog.find(s => s.stageCode === c.id);
              const acked = stage?.acked;
              return (
                <div key={c.id} className="crisis-contact-row">
                  <div>
                    <div className="crisis-contact-name">
                      {lang === 'ar' ? c.roleAr : c.roleEn} — {lang === 'ar' ? c.nameAr : c.nameEn}
                    </div>
                    <div className="crisis-contact-links">
                      <a href={`tel:${c.phone}`} className="mono crisis-contact-link">
                        <Phone size={10} className="crisis-contact-link-icon" />
                        {c.phone}
                      </a>
                      {c.backupPhone && (
                        <a href={`tel:${c.backupPhone}`} className="mono crisis-contact-link crisis-contact-link--muted">
                          {c.backupPhone}
                        </a>
                      )}
                      <a href={`mailto:${c.email}`} className="crisis-contact-link crisis-contact-link--muted">
                        {c.email}
                      </a>
                      <span>· ACK ≤ {c.maxAckMinutes}m</span>
                    </div>
                  </div>
                  {isActiveScenario && (
                    acked
                      ? <Badge tone="success"><CheckCircle2 size={11} /> {t('crisisAcked')}</Badge>
                      : <Button size="sm" variant="secondary" onClick={() => onAck(c.id)}>{t('crisisAckBtn')}</Button>
                  )}
                </div>
              );
            })}
          </div>
        ))}
      </div>
    </Card>
  );
}

// ─── Regulatory notifications card ──────────────────────────────────────────

function NotificationsCard({ scenario, active, onLog }: {
  scenario: typeof SCENARIOS[number];
  active: ActivationEvent | null;
  onLog: (regulator: string, ref: string) => void;
}) {
  const { t, lang } = useI18n();
  const isActiveScenario = active?.scenarioCode === scenario.code;
  const [refs, setRefs] = useState<Record<string, string>>({});

  return (
    <Card title={t('crisisRegNotifTitle')} Icon={FileText}>
      <div className="crisis-card-sub">
        {t('crisisRegNotifSub')}
      </div>
      <div className="crisis-notif-list">
        {scenario.regulatoryNotifications.map(rule => {
          const log = active?.notifications.find(n => n.regulator === rule.regulator);
          const sent = !!log?.sentAt;
          return (
            <div key={rule.regulator} className="crisis-notif-row">
              <div>
                <div className="crisis-notif-head">
                  <Badge tone={rule.maxHours <= 4 ? 'danger' : rule.maxHours <= 24 ? 'warning' : 'info'}>
                    {rule.regulator} · ≤ {rule.maxHours}h
                  </Badge>
                  {sent && <Badge tone="success"><CheckCircle2 size={11} /> {t('crisisNotifSent')}</Badge>}
                  {log?.referenceId && (
                    <span className="mono crisis-notif-ref">
                      ref {log.referenceId}
                    </span>
                  )}
                </div>
                <div className="crisis-notif-basis">
                  {lang === 'ar' ? rule.basisAr : rule.basisEn}
                </div>
              </div>
              {isActiveScenario && !sent && (
                <div className="crisis-notif-actions">
                  <input
                    placeholder={t('crisisRefPlaceholder')}
                    value={refs[rule.regulator] ?? ''}
                    onChange={e => setRefs(r => ({ ...r, [rule.regulator]: e.target.value }))}
                    className="mono crisis-notif-input"
                  />
                  <Button size="sm" variant="primary"
                    onClick={() => { onLog(rule.regulator, refs[rule.regulator] ?? ''); setRefs(r => ({ ...r, [rule.regulator]: '' })); }}>
                    {t('crisisLogBtn')}
                  </Button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </Card>
  );
}

// ─── Stage log card ─────────────────────────────────────────────────────────

function StageLogCard({ active }: { active: ActivationEvent }) {
  const { t, lang } = useI18n();
  return (
    <Card title={t('crisisStageLogTitle')} Icon={Clock}>
      <div className="crisis-stage-list">
        {active.stageLog.map((s, i) => (
          <div key={i} className="crisis-stage-row">
            <div className="crisis-stage-dot"
              data-state={s.acked ? 'acked' : s.exitedAt ? 'exited' : 'open'}>
              {s.acked ? <CheckCircle2 size={12} /> : i + 1}
            </div>
            <div>
              <div className="crisis-stage-label">
                {lang === 'ar' ? s.labelAr : s.labelEn}
              </div>
              {(s.noteEn || s.noteAr) && (
                <div className="crisis-stage-note">
                  {lang === 'ar' ? s.noteAr : s.noteEn}
                </div>
              )}
            </div>
            <div className="mono crisis-stage-time">
              {new Date(s.enteredAt).toLocaleTimeString()}
              {s.exitedAt && (
                <div>→ {new Date(s.exitedAt).toLocaleTimeString()}</div>
              )}
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}
