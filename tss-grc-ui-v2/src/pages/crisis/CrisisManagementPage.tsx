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

  return (
    <Shell title={t('crisisTitle')} sub={t('crisisSub')}>
      <PageHeader
        eyebrow={t('crisisEyebrow')}
        title={t('crisisTitle')}
        sub={t('crisisPageSub')}
      />

      {/* SCC Tier band */}
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Building2 size={18} color="var(--primary)" />
            <div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                {t('crisisSccLabel')}
              </div>
              <div style={{ fontSize: 14, fontWeight: 700 }}>
                {SCC_LABEL[sccTier][lang]}
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
            {(['TIER-1-NATIONAL', 'TIER-2-SECTORAL', 'TIER-3-OPERATIONAL', 'TIER-4-LOCAL'] as SccTier[]).map(tier => (
              <button key={tier} onClick={() => { saveSccTier(tier); setSccTier(tier); }}
                style={{
                  padding: '6px 10px', borderRadius: 'var(--r-sm)', fontSize: 11, fontWeight: 600,
                  border: `1px solid ${sccTier === tier ? 'var(--primary)' : 'var(--border)'}`,
                  background: sccTier === tier ? 'var(--primary-50)' : 'var(--surface-1)',
                  cursor: 'pointer',
                }}>
                {SCC_LABEL[tier][lang]}
              </button>
            ))}
          </div>
        </div>
        <div style={{ marginTop: 8, fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5 }}>
          {lang === 'ar' ? SCC_LABEL[sccTier].descriptionAr : SCC_LABEL[sccTier].descriptionEn}
        </div>
      </Card>

      {/* Active activation banner */}
      {active && (
        <ActiveBanner
          active={active}
          onTransition={handleTransition}
          onDownloadPacket={downloadNcemaPacket}
        />
      )}

      <div className="kpi-grid" style={{ marginBottom: 16, marginTop: 16 }}>
        <Kpi Icon={Siren}        label={t('crisisKpiScenarios')}      value={String(stats.totalScenarios)} tone="primary" />
        <Kpi Icon={ShieldAlert}    label={t('crisisKpiCyber')}            value={String(stats.cyberScenarios)} tone="warning" />
        <Kpi Icon={AlertOctagon}   label={t('crisisKpiHighImpact')}       value={String(stats.highImpactScenarios)} tone="danger" />
        <Kpi Icon={Calendar}        label={t('crisisKpiPlanned')}          value={String(stats.exercisesPlanned)} tone="info" />
        <Kpi Icon={Users}            label={t('crisisKpiCrossSector')}      value={String(stats.ncemaCrossSector)} tone="primary" />
        <Kpi Icon={Award}            label={t('crisisKpiAvgScore')}         value={String(stats.avgExerciseScore)} tone={stats.avgExerciseScore >= 80 ? 'success' : 'warning'} />
        <Kpi Icon={Calendar}         label={t('crisisKpiOldest')}            value={`${stats.oldestExercise}d`} tone={stats.oldestExercise > 365 ? 'danger' : stats.oldestExercise > 180 ? 'warning' : 'success'} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '360px 1fr', gap: 16 }}>
        {/* Scenario picker */}
        <Card padded={false} title={t('crisisScenariosTitle')} Icon={Siren}
          action={<Badge tone="primary">{SCENARIOS.length}</Badge>}>
          <div style={{ maxHeight: 660, overflowY: 'auto' }}>
            {SCENARIOS.map(s => {
              const isSelected = s.code === selectedScenario;
              const score = s.likelihood * s.impact;
              const isActive = active?.scenarioCode === s.code;
              return (
                <button key={s.code} onClick={() => setSelectedScenario(s.code)}
                  style={{
                    width: '100%', textAlign: 'start',
                    padding: '12px 14px', border: 'none', cursor: 'pointer',
                    borderBottom: '1px solid var(--border)',
                    background: isSelected ? 'var(--primary-50)' : isActive ? 'rgba(220,38,38,0.05)' : 'transparent',
                    display: 'flex', flexDirection: 'column', gap: 4,
                  }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{s.code}</span>
                    <Badge tone="info">{lang === 'ar' ? CATEGORY_LABEL[s.category].ar : CATEGORY_LABEL[s.category].en}</Badge>
                    <Badge tone={score >= 15 ? 'danger' : score >= 8 ? 'warning' : 'success'}>L{s.likelihood}×I{s.impact}</Badge>
                    {isActive && <Badge tone="danger">● {t('crisisActiveBadge')}</Badge>}
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {lang === 'ar' ? s.titleAr : s.titleEn}
                  </div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {t('crisisLeader')}: {s.leaderRole} · T+{s.activationTargetMinutes}m · {s.callTree.length} {t('crisisContacts')}
                  </div>
                </button>
              );
            })}
          </div>
        </Card>

        {/* Detail */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Card>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, flexWrap: 'wrap' }}>
              <span className="mono" style={{ fontSize: 11, color: 'var(--primary)' }}>{current.code}</span>
              <Badge tone="info">{lang === 'ar' ? CATEGORY_LABEL[current.category].ar : CATEGORY_LABEL[current.category].en}</Badge>
              <Badge tone="warning">L{current.likelihood} × I{current.impact}</Badge>
              <Badge tone="primary">{t('crisisActivation')}: T+{current.activationTargetMinutes}m</Badge>
            </div>
            <div style={{ fontSize: 18, fontWeight: 700, marginBottom: 6 }}>
              {lang === 'ar' ? current.titleAr : current.titleEn}
            </div>
            <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6, marginBottom: 12 }}>
              {lang === 'ar' ? current.descriptionAr : current.descriptionEn}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16, marginBottom: 12 }}>
              <div>
                <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('crisisLeader')}</div>
                <div style={{ fontSize: 13, fontWeight: 600, marginTop: 4 }}>{current.leaderRole}</div>
              </div>
              <div>
                <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('crisisLastExercise')}</div>
                <HijriDate iso={current.lastExerciseAt} layout="stacked" mono />
              </div>
              <div>
                <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('crisisLastScore')}</div>
                <div style={{
                  fontSize: 22, fontWeight: 800, marginTop: 4,
                  color: (current.lastExerciseScore ?? 0) >= 80 ? 'var(--success-fg)' : 'var(--warning-fg)',
                }} className="mono">
                  {current.lastExerciseScore ?? '—'}
                </div>
              </div>
            </div>

            {/* Trigger buttons */}
            {!active && (
              <div style={{ display: 'flex', gap: 8, paddingTop: 12, borderTop: '1px solid var(--border)' }}>
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
        <div style={{ marginTop: 16 }}>
          <Card title={t('crisisActivationHistoryTitle')} Icon={Siren}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 10 }}>
              {t('crisisActivationHistorySub')}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {history.map(ev => (
                <div key={ev.id} style={{
                  padding: '10px 0', borderBottom: '1px dashed var(--border)',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, flexWrap: 'wrap' }}>
                    <span className="mono" style={{ fontSize: 11 }}>{ev.id}</span>
                    <Badge tone={ACTIVATION_TONE[ev.status]}>{lang === 'ar' ? ACTIVATION_LABEL[ev.status].ar : ACTIVATION_LABEL[ev.status].en}</Badge>
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{ev.scenarioCode}</span>
                    {ev.actualMinutes != null && <Badge tone="primary">T+{ev.actualMinutes}m</Badge>}
                    <Badge tone={ev.isReal ? 'danger' : 'neutral'}>{ev.isReal ? t('crisisRealBadge') : t('crisisDrillBadge')}</Badge>
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5 }}>
                    {lang === 'ar' ? ev.notesAr : ev.notesEn}
                  </div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 4 }} className="mono">
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
      <div style={{ marginTop: 16 }}>
        <Card title={t('crisisExercisesTitle')} Icon={Calendar}
          action={<Badge tone="primary">{EXERCISES.length}</Badge>}>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('crisisExercisesSub')}
          </div>

          <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>
            {t('crisisUpcoming')}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', marginBottom: 16 }}>
            {upcomingExercises.length === 0
              ? <InlineEmpty compact>{t('crisisUpcomingEmpty')}</InlineEmpty>
              : upcomingExercises.map(e => (
                  <div key={e.code} style={{
                    padding: '10px 0', borderBottom: '1px dashed var(--border)',
                    display: 'grid', gridTemplateColumns: '1fr auto auto auto', gap: 10, alignItems: 'center',
                  }}>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 600 }}>{lang === 'ar' ? e.titleAr : e.titleEn}</div>
                      <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{e.code}</div>
                    </div>
                    {e.ncemaLed && <Badge tone="warning">NCEMA</Badge>}
                    <Badge tone="info">{lang === 'ar' ? TYPE_LABEL[e.type].ar : TYPE_LABEL[e.type].en}</Badge>
                    <HijriDate iso={e.scheduledFor} layout="stacked" mono />
                  </div>
                ))}
          </div>

          <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>
            {t('crisisCompleted')}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {completedExercises.map(e => (
              <div key={e.code} style={{
                padding: '12px 0', borderBottom: '1px dashed var(--border)',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, flexWrap: 'wrap' }}>
                  <span className="mono" style={{ fontSize: 11 }}>{e.code}</span>
                  <Badge tone="info">{lang === 'ar' ? TYPE_LABEL[e.type].ar : TYPE_LABEL[e.type].en}</Badge>
                  {e.ncemaLed && <Badge tone="warning">NCEMA</Badge>}
                  <Badge tone={(e.outcomeScore ?? 0) >= 80 ? 'success' : 'warning'}>{e.outcomeScore}/100</Badge>
                  <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{e.participantsCount} {t('crisisParticipants')}</span>
                </div>
                <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 4 }}>
                  {lang === 'ar' ? e.titleAr : e.titleEn}
                </div>
                <HijriDate iso={e.scheduledFor} layout="inline" mono />
                {(e.lessonsLearnedEn || e.lessonsLearnedAr) && (
                  <div style={{
                    marginTop: 8, padding: 10,
                    background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                    borderInlineStart: '3px solid var(--primary)',
                    fontSize: 12, lineHeight: 1.6, color: 'var(--ink-2)',
                  }}>
                    <div style={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--ink-3)', marginBottom: 4 }}>
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

function ActiveBanner({ active, onTransition, onDownloadPacket }: {
  active: ActivationEvent;
  onTransition: (s: ActivationStatus) => void;
  onDownloadPacket: () => void;
}) {
  const { t, lang } = useI18n();
  const scenario = SCENARIOS.find(s => s.code === active.scenarioCode);
  const elapsed = Math.round((Date.now() - new Date(active.startedAt).getTime()) / 60_000);
  const targetExceeded = scenario && elapsed > scenario.activationTargetMinutes;

  return (
    <div style={{
      marginTop: 12,
      padding: 16,
      borderRadius: 'var(--r-md)',
      border: `2px solid ${active.status === 'ACTIVATED' ? 'var(--danger-fg)' : 'var(--warning-fg)'}`,
      background: active.status === 'ACTIVATED' ? 'rgba(220,38,38,0.06)' : 'rgba(245,158,11,0.06)',
    }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
        <Siren size={28} color={active.status === 'ACTIVATED' ? 'var(--danger-fg)' : 'var(--warning-fg)'}
          style={{ flexShrink: 0 }} />
        <div style={{ flex: 1, minWidth: 240 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 4 }}>
            <Badge tone={ACTIVATION_TONE[active.status]}>{ACTIVATION_LABEL[active.status][lang]}</Badge>
            <Badge tone={active.isReal ? 'danger' : 'neutral'}>
              {active.isReal ? t('crisisRealBadge') : t('crisisDrillBadge')}
            </Badge>
            <span className="mono" style={{ fontSize: 11 }}>{active.id}</span>
          </div>
          <div style={{ fontSize: 16, fontWeight: 700 }}>
            {scenario ? (lang === 'ar' ? scenario.titleAr : scenario.titleEn) : active.scenarioCode}
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
            {t('crisisStarted')}: <span className="mono">{new Date(active.startedAt).toLocaleString()}</span>
            {' · '}
            <Clock size={11} style={{ display: 'inline-block', verticalAlign: 'middle' }} />
            {' '}
            <span style={{ color: targetExceeded ? 'var(--danger-fg)' : 'var(--ink-2)', fontWeight: 600 }}>
              T+{elapsed}m
            </span>
            {scenario && ` / ${t('crisisTarget')} T+${scenario.activationTargetMinutes}m`}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
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
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('crisisCallTreeSub')}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {steps.map(([step, contacts]) => (
          <div key={step} style={{
            border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            padding: 8,
          }}>
            <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 6, fontWeight: 700 }}>
              {t('crisisStep')} {step}
            </div>
            {contacts.map(c => {
              const stage = active?.stageLog.find(s => s.stageCode === c.id);
              const acked = stage?.acked;
              return (
                <div key={c.id} style={{
                  display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'center',
                  padding: '6px 0', borderTop: '1px dashed var(--border)',
                }}>
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 600 }}>
                      {lang === 'ar' ? c.roleAr : c.roleEn} — {lang === 'ar' ? c.nameAr : c.nameEn}
                    </div>
                    <div style={{ display: 'flex', gap: 12, fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                      <a href={`tel:${c.phone}`} className="mono" style={{ textDecoration: 'none', color: 'var(--ink-2)' }}>
                        <Phone size={10} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 2 }} />
                        {c.phone}
                      </a>
                      {c.backupPhone && (
                        <a href={`tel:${c.backupPhone}`} className="mono" style={{ textDecoration: 'none', color: 'var(--ink-3)' }}>
                          {c.backupPhone}
                        </a>
                      )}
                      <a href={`mailto:${c.email}`} style={{ textDecoration: 'none', color: 'var(--ink-3)' }}>
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
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('crisisRegNotifSub')}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {scenario.regulatoryNotifications.map(rule => {
          const log = active?.notifications.find(n => n.regulator === rule.regulator);
          const sent = !!log?.sentAt;
          return (
            <div key={rule.regulator} style={{
              padding: '10px 0', borderBottom: '1px dashed var(--border)',
              display: 'grid', gridTemplateColumns: '1fr auto', gap: 8, alignItems: 'center',
            }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                  <Badge tone={rule.maxHours <= 4 ? 'danger' : rule.maxHours <= 24 ? 'warning' : 'info'}>
                    {rule.regulator} · ≤ {rule.maxHours}h
                  </Badge>
                  {sent && <Badge tone="success"><CheckCircle2 size={11} /> {t('crisisNotifSent')}</Badge>}
                  {log?.referenceId && (
                    <span className="mono" style={{ fontSize: 10, color: 'var(--ink-2)' }}>
                      ref {log.referenceId}
                    </span>
                  )}
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5 }}>
                  {lang === 'ar' ? rule.basisAr : rule.basisEn}
                </div>
              </div>
              {isActiveScenario && !sent && (
                <div style={{ display: 'flex', gap: 4 }}>
                  <input
                    placeholder={t('crisisRefPlaceholder')}
                    value={refs[rule.regulator] ?? ''}
                    onChange={e => setRefs(r => ({ ...r, [rule.regulator]: e.target.value }))}
                    className="mono"
                    style={{
                      padding: '6px 8px', fontSize: 11,
                      border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                      background: 'var(--surface-1)', width: 140,
                    }}
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
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {active.stageLog.map((s, i) => (
          <div key={i} style={{
            padding: '8px 0', borderBottom: '1px dashed var(--border)',
            display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 10, alignItems: 'center',
          }}>
            <div style={{
              width: 22, height: 22, borderRadius: '50%',
              background: s.acked ? 'var(--success)' : s.exitedAt ? 'var(--ink-3)' : 'var(--primary)',
              color: 'white', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 10, fontWeight: 700,
            }}>
              {s.acked ? <CheckCircle2 size={12} /> : i + 1}
            </div>
            <div>
              <div style={{ fontSize: 12, fontWeight: 600 }}>
                {lang === 'ar' ? s.labelAr : s.labelEn}
              </div>
              {(s.noteEn || s.noteAr) && (
                <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                  {lang === 'ar' ? s.noteAr : s.noteEn}
                </div>
              )}
            </div>
            <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)', textAlign: 'end' }}>
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
