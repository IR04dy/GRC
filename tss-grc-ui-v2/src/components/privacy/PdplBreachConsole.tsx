import { useEffect, useMemo, useState } from 'react';
import { Fingerprint, Send, ShieldAlert, Clock, FileDown, AlertOctagon, CheckCircle2, Users } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Kpi } from '../ui/Kpi';
import { useI18n } from '../../lib/i18n';
import {
  PDPL_BREACH_STAGES, computeBreachClock, classifySeverity,
  generateSdaiaNotification, requiresDataSubjectNotification,
  formatHoursLabel, cumulativeBudgetTo,
  type BreachClock, type PdplSeverity, type SdaiaNotificationPacket,
} from '../../lib/pdplBreach';
import { formatHijri } from '../../lib/sla';
import type { PrivacyBreachDto } from '../../lib/api';

/**
 * PDPL 72-hour breach response console — Sprint 4.
 *
 * Operational hero panel for the highest-priority open breach. Hour-precise
 * countdown driven by the breach `reportedAt` timestamp; auto-generated
 * SDAIA notification packet (PDPL Art. 25 fields) ready for DPO dispatch;
 * separate 7-day data-subjects notification track when severity ≥ HIGH.
 *
 * Picks the most-time-sensitive open breach automatically. If none, returns
 * a "compliant" empty state.
 */
export function PdplBreachConsole({ breaches, onDispatch }: {
  breaches: ReadonlyArray<PrivacyBreachDto>;
  /**
   * Real SDAIA dispatch action. When provided, the "Dispatch to SDAIA"
   * button calls this with the active breach (parent owns the API call,
   * confirm dialog, error toast, and reload). Without it the button
   * is disabled — we never want this regulatory action to "succeed"
   * without actually transitioning state.
   */
  onDispatch?: (breach: PrivacyBreachDto) => Promise<void> | void;
}) {
  const { t, lang } = useI18n();
  const [dispatching, setDispatching] = useState(false);
  const [now, setNow] = useState<Date>(() => new Date());

  // Re-tick every 30s — operational console, fast feedback.
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 30_000);
    return () => window.clearInterval(id);
  }, []);

  // Pick the breach where the SDAIA-notification clock is still actionable
  // and most urgent. Order of preference:
  //   1. Open + needs SDAIA + still inside the 72h window — pick the one
  //      with the least time remaining (most urgent active case).
  //   2. Open + needs SDAIA + already past 72h — most-recently-reported
  //      (still recoverable / actionable today).
  //   3. Any open breach (e.g. NOTIFIED awaiting data-subjects step).
  const target = useMemo(() => {
    const open = breaches.filter(b => b.status !== 'CLOSED' && b.reportedAt);
    const needsSdaia = open.filter(b => b.status !== 'NOTIFIED');
    const pool = needsSdaia.length > 0 ? needsSdaia : open;
    const enriched = pool.map(b => ({
      breach: b,
      clock: computeBreachClock(b.reportedAt ?? b.occurredAt ?? '', now),
    }));
    const inWindow = enriched.filter(c => c.clock.hoursRemaining >= 0);
    if (inWindow.length > 0) {
      return [...inWindow].sort((a, b) => a.clock.hoursRemaining - b.clock.hoursRemaining)[0];
    }
    return [...enriched].sort((a, b) => a.clock.hoursElapsed - b.clock.hoursElapsed)[0];
  }, [breaches, now]);

  if (!target) {
    return (
      <Card title={t('pdplConsoleTitle')} Icon={ShieldAlert}>
        <div style={{ padding: 24, textAlign: 'center', color: 'var(--success-fg)', fontSize: 13 }}>
          <CheckCircle2 size={20} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 6 }} />
          {t('pdplConsoleEmpty')}
        </div>
      </Card>
    );
  }

  const { breach, clock } = target;
  const severity = classifySeverity({
    affectedRecords: breach.affectedRecords,
    financial: breach.breachType === 'CONFIDENTIALITY' && (breach.affectedRecords ?? 0) > 5_000,
  });

  const packet = generateSdaiaNotification(breach, severity);
  const needsDataSubjectNotice = requiresDataSubjectNotification(severity);
  const reportedReady = breach.status === 'NOTIFIED';

  return (
    <Card title={t('pdplConsoleTitle')} Icon={ShieldAlert}
      action={<Badge tone={CLOCK_TONE[clock.tier]}>{tierLabel(t, clock.tier)}</Badge>}>

      {/* ── Hero countdown row ─────────────────────────────────────── */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'auto 1fr',
        gap: 16, alignItems: 'center', padding: 12,
        background: clock.tier === 'BREACHED' ? 'var(--danger-bg)'
                  : clock.tier === 'RED'      ? 'var(--danger-bg)'
                  : clock.tier === 'AMBER'    ? 'var(--warning-bg)'
                  :                              'var(--success-bg)',
        borderRadius: 'var(--r-md)', marginBottom: 16,
      }}>
        <CountdownRing clock={clock} t={t} />
        <div>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            {t('pdplActiveBreach')} · #{breach.id}
          </div>
          <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--ink-1)', marginTop: 4 }}>
            {breach.titleEn}
          </div>
          <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 4, lineHeight: 1.5 }}>
            {breach.descriptionEn}
          </div>
          <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
            <Badge tone={SEVERITY_TONE[severity]}>{t('pdplSev' + severity[0] + severity.slice(1).toLowerCase()) || severity}</Badge>
            <Badge tone="info">{breach.breachType ?? '—'}</Badge>
            <Badge tone="neutral">{(breach.affectedRecords ?? 0).toLocaleString()} {t('pdplRecords')}</Badge>
            <Badge tone="neutral">{t('pdplAwareLabel')} {fmtAware(breach.reportedAt ?? breach.occurredAt ?? '', lang)}</Badge>
          </div>
        </div>
      </div>

      {/* ── KPI row ────────────────────────────────────────────────── */}
      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Clock}      label={t('pdplKpiHoursElapsed')}  value={formatHoursLabel(clock.hoursElapsed, lang)} tone="info" />
        <Kpi Icon={AlertOctagon} label={t('pdplKpiSdaiaDue')}    value={clock.hoursRemaining < 0 ? t('pdplBreachedLabel') : formatHoursLabel(clock.hoursRemaining, lang)} tone={clock.tier === 'GREEN' ? 'success' : clock.tier === 'AMBER' ? 'warning' : 'danger'} />
        <Kpi Icon={Users}      label={t('pdplKpiDataSubjects')}  value={needsDataSubjectNotice ? formatHoursLabel(clock.hoursToDataSubjects, lang) : t('pdplNotRequired')} tone={needsDataSubjectNotice ? 'warning' : 'success'} />
        <Kpi Icon={Fingerprint} label={t('pdplKpiBudget')}       value={`${clock.pctConsumed}%`} tone={clock.pctConsumed >= 100 ? 'danger' : clock.pctConsumed >= 80 ? 'warning' : clock.pctConsumed >= 50 ? 'info' : 'success'} />
      </div>

      {/* ── Stage timeline ─────────────────────────────────────────── */}
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 8 }}>
          {t('pdplStageTimeline')}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          {PDPL_BREACH_STAGES.filter(s => s.code !== 'CLOSED').map((s, idx) => {
            const cumBudget = cumulativeBudgetTo(s.code);
            const stageStart = cumBudget - s.hoursBudget;
            const reached = clock.hoursElapsed >= stageStart;
            const past = clock.hoursElapsed > cumBudget;
            // Marker stages (hoursBudget=0, e.g. "Detected") are not deadlines —
            // they're points in time. Once reached they're Done, never Overdue.
            const overdue = s.hoursBudget > 0 && past;
            const active = reached && !past && s.hoursBudget > 0;
            const current = clock.expectedStage === s.code;
            return (
              <div key={s.code} style={{
                display: 'grid', gridTemplateColumns: '24px 200px 1fr auto', gap: 10, alignItems: 'center',
                padding: '6px 8px', borderRadius: 'var(--r-sm)',
                background: current ? 'var(--primary-50)' : 'transparent',
                border: current ? '1px solid var(--primary-200, var(--border))' : '1px solid transparent',
              }}>
                <div style={{
                  width: 18, height: 18, borderRadius: '50%',
                  background: overdue ? 'var(--danger)' : reached ? 'var(--success)' : 'var(--surface-2)',
                  color: 'white', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 10, fontWeight: 700,
                }}>{idx + 1}</div>
                <span style={{ fontSize: 12, fontWeight: current ? 700 : 600, color: 'var(--ink-1)' }}>
                  {lang === 'ar' ? s.labelAr : s.labelEn}
                </span>
                <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  {s.hoursBudget > 0
                    ? t('pdplStageBudget').replace('{n}', String(s.hoursBudget)).replace('{cum}', String(cumBudget))
                    : t('pdplStageStart')}
                </span>
                <Badge tone={overdue ? 'danger' : active ? 'warning' : reached ? 'success' : 'neutral'}>
                  {overdue ? t('pdplStageOverdue')
                   : active ? t('pdplStageActive')
                   : reached ? t('pdplStageDone')
                             : t('pdplStagePending')}
                </Badge>
              </div>
            );
          })}
        </div>
      </div>

      {/* ── Auto-generated SDAIA packet preview ──────────────────── */}
      <div style={{ marginBottom: 12 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            {t('pdplSdaiaPacketTitle')}
          </div>
          <div style={{ display: 'flex', gap: 6 }}>
            <Button variant="secondary" Icon={FileDown} onClick={() => downloadPacket(packet, breach)}>
              {t('pdplDownloadPacket')}
            </Button>
            <Button Icon={Send} disabled={reportedReady || dispatching || !onDispatch}
              onClick={async () => {
                if (!onDispatch) return;
                setDispatching(true);
                try {
                  await onDispatch(breach);
                  // Success toast is owned by the parent (it has the canonical
                  // bilingual copy + reload flow). We just clear the busy state.
                } catch {
                  // Parent owns error toast — swallow here so dispatching flips back.
                } finally {
                  setDispatching(false);
                }
              }}>
              {reportedReady ? t('pdplDispatched') : t('pdplDispatchToSdaia')}
            </Button>
          </div>
        </div>
        <PacketPreview packet={packet} lang={lang} t={t} />
      </div>

      {/* ── Data-subjects notification card ─────────────────────── */}
      {needsDataSubjectNotice && (
        <div style={{
          padding: 12, borderRadius: 'var(--r-sm)',
          background: 'var(--warning-bg)', color: 'var(--warning-fg)', fontSize: 12, lineHeight: 1.6,
        }}>
          <div style={{ fontWeight: 700, marginBottom: 4, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <Users size={14} />
            {t('pdplDataSubjectsTitle')}
          </div>
          <div>
            {t('pdplDataSubjectsBody')
              .replace('{count}', (breach.affectedRecords ?? 0).toLocaleString())
              .replace('{deadline}', packet.dataSubjectsNotificationPlannedAt
                ? `${new Date(packet.dataSubjectsNotificationPlannedAt).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')} · ${formatHijri(packet.dataSubjectsNotificationPlannedAt, lang)}`
                : '—')}
          </div>
        </div>
      )}
    </Card>
  );
}

// ─── Subcomponents ──────────────────────────────────────────────────────────

function CountdownRing({ clock, t }: { clock: BreachClock; t: (k: string) => string }) {
  // Draw a 72-hour ring with the consumed wedge in red/amber/green.
  const size = 96;
  const stroke = 9;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const consumed = Math.min(1, clock.hoursElapsed / 72);
  const offset = c * (1 - consumed);
  const colour = clock.tier === 'BREACHED' || clock.tier === 'RED'
    ? 'var(--danger)'
    : clock.tier === 'AMBER' ? 'var(--warning-fg)' : 'var(--success-fg)';
  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg width={size} height={size}>
        <circle cx={size / 2} cy={size / 2} r={r} stroke="var(--surface-2)" strokeWidth={stroke} fill="none" />
        <circle cx={size / 2} cy={size / 2} r={r} stroke={colour} strokeWidth={stroke} fill="none"
          strokeDasharray={c} strokeDashoffset={offset} strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`} />
      </svg>
      <div style={{
        position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', textAlign: 'center',
      }}>
        <div className="mono" style={{ fontSize: 16, fontWeight: 800, color: colour }}>
          {clock.hoursRemaining < 0 ? `+${Math.round(-clock.hoursRemaining)}h` : `${Math.round(clock.hoursRemaining)}h`}
        </div>
        <div style={{ fontSize: 9, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
          {clock.hoursRemaining < 0 ? t('pdplBreachedLabel') : t('pdplToSdaia')}
        </div>
      </div>
    </div>
  );
}

function PacketPreview({ packet, lang, t }: { packet: SdaiaNotificationPacket; lang: 'en' | 'ar'; t: (k: string) => string }) {
  const rows: Array<[string, string]> = [
    [t('pdplFieldRef'), packet.internalRef],
    [t('pdplFieldController'), `${packet.controller.nameEn} / ${packet.controller.nameAr} (CR ${packet.controller.crNumber})`],
    [t('pdplFieldDpo'), `${packet.controller.dpoNameEn} · ${packet.controller.dpoEmail}`],
    [t('pdplFieldNature'), lang === 'ar' ? packet.natureOfBreach.ar : packet.natureOfBreach.en],
    [t('pdplFieldCategories'), packet.categoriesAffected.join(' · ')],
    [t('pdplFieldSubjects'), `${packet.approximateSubjects.toLocaleString()} ${t('pdplFieldSubjectsUnit')}`],
    [t('pdplFieldConsequences'), lang === 'ar' ? packet.likelyConsequences.ar : packet.likelyConsequences.en],
    [t('pdplFieldContainment'), lang === 'ar' ? packet.containmentMeasures.ar : packet.containmentMeasures.en],
    [t('pdplFieldProposed'), lang === 'ar' ? packet.proposedMeasures.ar : packet.proposedMeasures.en],
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {rows.map(([label, value], i) => (
        <div key={i} style={{
          display: 'grid', gridTemplateColumns: '180px 1fr', gap: 12,
          padding: '8px 0', borderBottom: '1px dashed var(--border)',
        }}>
          <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</span>
          <span style={{ fontSize: 12, color: 'var(--ink-1)', lineHeight: 1.5 }}>{value}</span>
        </div>
      ))}
    </div>
  );
}

// ─── Helpers ────────────────────────────────────────────────────────────────

const CLOCK_TONE: Record<BreachClock['tier'], 'success' | 'info' | 'warning' | 'danger'> = {
  GREEN: 'success', AMBER: 'warning', RED: 'danger', BREACHED: 'danger',
};
const SEVERITY_TONE: Record<PdplSeverity, 'risk-low' | 'risk-med' | 'risk-high' | 'risk-crit'> = {
  LOW: 'risk-low', MEDIUM: 'risk-med', HIGH: 'risk-high', CRITICAL: 'risk-crit',
};

function tierLabel(t: (k: string) => string, tier: BreachClock['tier']): string {
  return tier === 'BREACHED' ? t('pdplTierBreached')
       : tier === 'RED'      ? t('pdplTierRed')
       : tier === 'AMBER'    ? t('pdplTierAmber')
                              : t('pdplTierGreen');
}

function fmtAware(iso: string, lang: 'en' | 'ar'): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return `${d.toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}`;
}

function downloadPacket(packet: SdaiaNotificationPacket, breach: PrivacyBreachDto) {
  const blob = new Blob([JSON.stringify({ packet, breach }, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${packet.internalRef}.json`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
