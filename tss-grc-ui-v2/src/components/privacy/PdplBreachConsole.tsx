import { useEffect, useMemo, useState } from 'react';
import { Fingerprint, ShieldAlert, Clock, AlertOctagon, CheckCircle2, Users, Landmark } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Kpi } from '../ui/Kpi';
import { FormModal, type FieldDef } from '../ui/FormModal';
import { useI18n } from '../../lib/i18n';
import {
  PDPL_BREACH_STAGES, computeBreachClock, classifySeverity,
  requiresDataSubjectNotification, severityIsProvisional,
  formatHoursLabel, cumulativeBudgetTo, dataSubjectsDeadline,
  isSdaiaReported, isSdaiaSubmitted, ARTICLE_25_FIELDS, article25Value, article25NotHeld,
  type BreachClock, type PdplSeverity, type Article25Field, type PdplSeverityInputs,
} from '../../lib/pdplBreach';
import { formatHijri } from '../../lib/sla';
import { useToast } from '../../lib/toast';
import { privacyApi, ApiError, type PrivacyBreachDto, type PrivacySdaiaStatus, type SdaiaSubmissionRequest } from '../../lib/api';

/**
 * PDPL 72-hour breach response console — Sprint 4.
 *
 * Operational hero panel for the highest-priority open breach. Hour-precise
 * countdown from the awareness timestamp; the Article 25 field inventory with
 * per-field provenance; the authoritative SDAIA submission record; and a
 * separate 7-day data-subjects notification track when severity ≥ HIGH.
 *
 * Picks the most-time-sensitive open breach automatically. If none, returns
 * a "compliant" empty state.
 *
 * ⚠⚠ F2-209 — there is no `onDispatch` prop any more, and its removal is the
 * finding. It was documented as the "Real SDAIA dispatch action", with the
 * warning "we never want this regulatory action to 'succeed' without actually
 * transitioning state" — and the one call site passed `notifyDataOfficeAction`,
 * i.e. `POST /breaches/{id}/notify-data-office`, which sets
 * `pdpc_notification_required = true` and stamps `pdpc_notified_at`. It does
 * NOT write `sdaia_notification_status`, does NOT write `sdaia_submitted_at`
 * (the field the server's own javadoc calls the discharge of the Article 31
 * clock), and emits neither the F7 audit row nor the F8
 * `BREACH_REPORTED_TO_SDAIA` event that the real recorder — in the same service
 * — emits. So a button reading "Dispatch to SDAIA" on a 72-hour statutory
 * countdown recorded no notification, wrote no audit trail, and left the
 * platform's authoritative status at NOT_REPORTED. Latent, not live: 0 rows in
 * either database carry `pdpc_notified_at`, so it had never been clicked.
 *
 * 🔑 The prop's own contract stated the rule and the wiring broke it — F2-184's
 * shape, where a comment answers the question a reader was about to ask.
 *
 * And nothing in this platform can dispatch to SDAIA: SDAIA has no
 * machine-readable API, the submission is a human filling in the SDAIA portal,
 * and the entity javadoc says so. A button that claims otherwise cannot be
 * fixed by pointing it at a better endpoint. The honest control is the one that
 * already existed below — record the outcome of the manual submission — so that
 * is now the only one, promoted to primary.
 */
export function PdplBreachConsole({ breaches, onRecorded }: {
  breaches: ReadonlyArray<PrivacyBreachDto>;
  /**
   * Called after a SDAIA submission is recorded so the parent can reload
   * its breach list (the console owns the API call + toast for this one,
   * since it also owns the authoritative SDAIA status it fetched).
   */
  onRecorded?: () => void;
}) {
  const { t, lang } = useI18n();
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
    // Awareness timestamp drives the 72h clock. The backend DTO only ships
    // `discoveredAt` (no reportedAt/occurredAt), so fall back to it — otherwise
    // the console renders an empty state against real data.
    const awareOf = (b: PrivacyBreachDto) => b.reportedAt ?? b.occurredAt ?? b.discoveredAt ?? '';
    const open = breaches.filter(b => b.status !== 'CLOSED' && awareOf(b));
    // ⚠⚠ F2-209: this was `b.status !== 'NOTIFIED'` — the breach LIFECYCLE
    // status, not the SDAIA notification status the platform keeps in its own
    // column. Measured in dev and prod: 4 of 17 live breaches are
    // `status = NOTIFIED` with `sdaia_notification_status = NOT_REPORTED`, all
    // four flagged as requiring notification and 113–193 days past the 72-hour
    // window. They were filtered out of this pool, so the one screen built to
    // surface an undischarged Article 25 clock never showed them.
    //
    // ⚠ And SUBMITTED, not merely "reported": `IN_PREPARATION` means somebody
    // started drafting, which discharges nothing. Excluding it here would drop a
    // notification begun six months ago and never sent out of the urgent pool —
    // the same defect one notch milder, which is how the first version of this
    // fix was written.
    const needsSdaia = open.filter(b => !isSdaiaSubmitted(b.sdaiaNotificationStatus));
    const pool = needsSdaia.length > 0 ? needsSdaia : open;
    const enriched = pool.map(b => ({
      breach: b,
      clock: computeBreachClock(awareOf(b), now),
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
  // Backend DTO ships `dataSubjectsAffectedCount`, not `affectedRecords`.
  const records = breach.affectedRecords ?? breach.dataSubjectsAffectedCount ?? 0;
  const aware = breach.reportedAt ?? breach.occurredAt ?? breach.discoveredAt ?? '';
  // ⚠ F2-209: the second argument used to be
  //   `financial: breach.breachType === 'CONFIDENTIALITY' && records > 5_000`
  // — an inference no column supports, asserting that a large confidentiality
  // breach involved financial data, which promotes the band to HIGH and with it
  // the Article 26 "you must notify the data subjects directly" determination.
  // `privacy.data_breaches` has no special-category flag, no data-category list
  // and no `severity` column, so the affected-record count is the only input
  // the platform actually holds. Passing only that makes the band provisional,
  // which is what `severityIsProvisional` then discloses on the badge.
  const severityInputs: PdplSeverityInputs = { affectedRecords: records };
  const severity = classifySeverity(severityInputs);
  const provisional = severityIsProvisional(severityInputs);

  const needsDataSubjectNotice = requiresDataSubjectNotification(severity);
  const subjectsDeadline = dataSubjectsDeadline(aware);
  // The SDAIA question, answered from the column the server owns for it.
  //
  // ⚠ `isSdaiaSubmitted`, not `isSdaiaReported`: only SUBMITTED and ACKNOWLEDGED
  // discharge the 72-hour clock (the server's javadoc puts the discharge at
  // `sdaia_submitted_at`). Rendering the countdown KPI in success green for a
  // breach that is merely IN_PREPARATION would be an invented all-clear on a
  // statutory deadline — F2-197's trap, on the one tile that exists to show it.
  const discharged = isSdaiaSubmitted(breach.sdaiaNotificationStatus);

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
            <Badge tone={SEVERITY_TONE[severity]}
              title={provisional ? t('pdplSevProvisionalHint') : undefined}>
              {t('pdplSev' + severity[0] + severity.slice(1).toLowerCase()) || severity}
              {provisional && <span aria-label={t('pdplSevProvisionalHint')}> ·&nbsp;{t('pdplSevProvisional')}</span>}
            </Badge>
            <Badge tone="info">{breach.breachType ?? '—'}</Badge>
            <Badge tone="neutral">{records.toLocaleString()} {t('pdplRecords')}</Badge>
            <Badge tone="neutral">{t('pdplAwareLabel')} {fmtAware(aware, lang)}</Badge>
            {/* F2-209: the column the hero row used to ignore, stated on the row
                it decides. A breach can be `status = NOTIFIED` and still have
                never been reported to SDAIA — 4 of 17 live rows are. */}
            <Badge tone={SDAIA_STATUS_TONE[breach.sdaiaNotificationStatus ?? 'NOT_REPORTED'] ?? 'neutral'} dot>
              {sdaiaStatusLabel(t, breach.sdaiaNotificationStatus)}
            </Badge>
          </div>
        </div>
      </div>

      {/* ── KPI row ────────────────────────────────────────────────── */}
      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Clock}      label={t('pdplKpiHoursElapsed')}  value={formatHoursLabel(clock.hoursElapsed, lang)} tone="info" to="/data-officer/breaches" />
        {/* F2-209: once the server records the notification as away, the
            72-hour countdown is answered and a red "OVERDUE" beside it would be
            a second, contradicting claim about the same fact. Reported is not
            invented health — it is what `sdaia_notification_status` says. */}
        <Kpi Icon={AlertOctagon} label={t('pdplKpiSdaiaDue')}
          value={discharged ? sdaiaStatusLabel(t, breach.sdaiaNotificationStatus)
                 : clock.hoursRemaining < 0 ? t('pdplBreachedLabel')
                 : formatHoursLabel(clock.hoursRemaining, lang)}
          tone={discharged ? 'success'
                : clock.tier === 'GREEN' ? 'success' : clock.tier === 'AMBER' ? 'warning' : 'danger'}
          to="/data-officer/breaches" />
        <Kpi Icon={Users}      label={t('pdplKpiDataSubjects')}  value={needsDataSubjectNotice ? formatHoursLabel(clock.hoursToDataSubjects, lang) : t('pdplNotRequired')} tone={needsDataSubjectNotice ? 'warning' : 'success'} to="/data-officer/breaches" />
        <Kpi Icon={Fingerprint} label={t('pdplKpiBudget')}       value={`${clock.pctConsumed}%`} tone={clock.pctConsumed >= 100 ? 'danger' : clock.pctConsumed >= 80 ? 'warning' : clock.pctConsumed >= 50 ? 'info' : 'success'} to="/data-officer/breaches" />
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

      {/* ── Article 25 field inventory (per-field provenance) ─────── */}
      <div style={{ marginBottom: 12 }}>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            {t('pdplArt25Title')}
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 3, lineHeight: 1.5 }}>
            {t('pdplArt25Sub').replace('{n}', String(article25NotHeld()))}
          </div>
        </div>
        <Article25Inventory breach={breach} lang={lang} t={t} />
      </div>

      {/* ── SDAIA submission tracking (authoritative, server-backed) ─── */}
      <SdaiaSubmissionPanel breach={breach} onRecorded={onRecorded} />

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
              .replace('{count}', records.toLocaleString())
              .replace('{deadline}', subjectsDeadline
                ? `${new Date(subjectsDeadline).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')} · ${formatHijri(subjectsDeadline, lang)}`
                : '—')}
          </div>
        </div>
      )}
    </Card>
  );
}

// ─── Subcomponents ──────────────────────────────────────────────────────────

const SDAIA_STATUS_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  NOT_REPORTED: 'danger', IN_PREPARATION: 'warning', SUBMITTED: 'info', ACKNOWLEDGED: 'success',
};

function sdaiaStatusLabel(t: (k: string) => string, status?: string): string {
  switch (status) {
    case 'IN_PREPARATION': return t('sdaiaStatusPreparing');
    case 'SUBMITTED':      return t('sdaiaStatusSubmitted');
    case 'ACKNOWLEDGED':   return t('sdaiaStatusAcknowledged');
    default:               return t('sdaiaStatusNotReported');
  }
}

/**
 * SDAIA submission tracking (PDPL Article 31). Reads the authoritative status
 * from the backend — the server-computed 72h deadline, current notification
 * status, portal reference and submitted-at — and lets the DPO record the
 * outcome of the (manual) SDAIA-portal submission. Surfaces the orphaned
 * GET/POST /breaches/{id}/sdaia-submission endpoints.
 */
function SdaiaSubmissionPanel({ breach, onRecorded }: {
  breach: PrivacyBreachDto;
  onRecorded?: () => void;
}) {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [status, setStatus] = useState<PrivacySdaiaStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [formOpen, setFormOpen] = useState(false);

  useEffect(() => {
    let live = true;
    setLoading(true);
    privacyApi.sdaiaStatus(breach.id)
      .then(s => { if (live) setStatus(s); })
      .catch(() => { if (live) setStatus(null); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [breach.id]);

  const deadline = status?.pdplArticle31DeadlineAt ? new Date(status.pdplArticle31DeadlineAt) : null;
  const hoursToDeadline = deadline ? (deadline.getTime() - Date.now()) / 3_600_000 : null;
  // F2-209: the same predicate the hero row uses, so one component cannot hold
  // two answers to "has SDAIA been notified?" again. It used to be a `!==
  // 'NOT_REPORTED'` comparison here and a `status === 'NOTIFIED'` read on a
  // different column 200 lines above.
  const reported = isSdaiaReported(status?.sdaiaNotificationStatus);
  const st = status?.sdaiaNotificationStatus ?? 'NOT_REPORTED';

  const fields: FieldDef[] = [
    { kind: 'select', key: 'status', label: t('sdaiaFieldStatus'), required: true, span: 12,
      options: [
        { value: 'SUBMITTED',      label: t('sdaiaStatusSubmitted') },
        { value: 'IN_PREPARATION', label: t('sdaiaStatusPreparing') },
        { value: 'ACKNOWLEDGED',   label: t('sdaiaStatusAcknowledged') },
      ] },
    { kind: 'text', key: 'submissionRef', label: t('sdaiaFieldRef'), span: 12,
      placeholder: 'SDAIA-2026-XXXXXX' },
  ];
  const initial = { status: 'SUBMITTED', submissionRef: status?.sdaiaSubmissionRef ?? '' };

  async function submit(v: { status: string; submissionRef: string }): Promise<void> {
    try {
      const body: SdaiaSubmissionRequest = {
        status: v.status,
        submissionRef: v.submissionRef.trim() || undefined,
        submittedAt: null, // server stamps now
      };
      await privacyApi.recordSdaiaSubmission(breach.id, body);
      const fresh = await privacyApi.sdaiaStatus(breach.id);
      setStatus(fresh);
      setFormOpen(false);
      toast.success(lang === 'ar' ? 'تم تسجيل إبلاغ سدايا' : 'SDAIA submission recorded');
      onRecorded?.();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (lang === 'ar' ? 'فشل التسجيل' : 'Record failed'));
    }
  }

  return (
    <div style={{
      marginBottom: 12, padding: 12, borderRadius: 'var(--r-md)',
      border: '1px solid var(--border)', background: 'var(--surface-2)',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, gap: 8, flexWrap: 'wrap' }}>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Landmark size={15} style={{ color: 'var(--primary)' }} />
          <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink-1)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            {t('sdaiaPanelTitle')}
          </span>
          <Badge tone={SDAIA_STATUS_TONE[st] ?? 'neutral'} dot>{sdaiaStatusLabel(t, st)}</Badge>
        </div>
        <Button variant={reported ? 'secondary' : 'primary'} size="sm" Icon={Landmark}
          disabled={loading} onClick={() => setFormOpen(true)}>
          {reported ? t('sdaiaUpdateBtn') : t('sdaiaRecordBtn')}
        </Button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10 }}>
        <SdaiaStat label={t('sdaiaDeadline')}
          value={deadline ? deadline.toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'}
          hint={hoursToDeadline == null ? undefined
            : hoursToDeadline < 0 ? (lang === 'ar' ? `تجاوز ${Math.round(-hoursToDeadline)} ساعة` : `${Math.round(-hoursToDeadline)}h overdue`)
            : (lang === 'ar' ? `خلال ${Math.round(hoursToDeadline)} ساعة` : `in ${Math.round(hoursToDeadline)}h`)}
          tone={hoursToDeadline == null ? 'neutral' : hoursToDeadline < 0 ? 'danger' : hoursToDeadline < 24 ? 'warning' : 'success'} />
        <SdaiaStat label={t('sdaiaFieldRef')} value={status?.sdaiaSubmissionRef || '—'} mono />
        <SdaiaStat label={t('sdaiaSubmittedAt')}
          value={status?.sdaiaSubmittedAt ? new Date(status.sdaiaSubmittedAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'} />
      </div>

      <FormModal<{ status: string; submissionRef: string }>
        open={formOpen}
        title={t('sdaiaRecordTitle')}
        fields={fields}
        initial={initial}
        onClose={() => setFormOpen(false)}
        onSubmit={submit}
        submitLabel={t('sdaiaRecordBtn')}
      />
    </div>
  );
}

function SdaiaStat({ label, value, hint, tone = 'neutral', mono }: {
  label: string; value: string; hint?: string;
  tone?: 'neutral' | 'success' | 'warning' | 'danger'; mono?: boolean;
}) {
  const hintColour = tone === 'danger' ? 'var(--danger-fg)' : tone === 'warning' ? 'var(--warning-fg)' : tone === 'success' ? 'var(--success-fg)' : 'var(--ink-3)';
  return (
    <div>
      <div style={{ fontSize: 10, color: 'var(--ink-4)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 3 }}>
        {label}
      </div>
      <div className={mono ? 'mono' : undefined} style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--ink-1)' }}>{value}</div>
      {hint && <div style={{ fontSize: 10.5, fontWeight: 600, color: hintColour, marginTop: 2 }}>{hint}</div>}
    </div>
  );
}

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

/**
 * The Article 25 field inventory — what SDAIA's notification asks for, and per
 * field whether this platform holds it.
 *
 * ⚠⚠ F2-209: replaces `PacketPreview`, which rendered a generated submission
 * whose controller identity, affected-data categories, likely consequences,
 * containment measures and proposed measures were literals in the bundle. There
 * is no column for any of them, so they could not be wired; reconstructing them
 * is F2-196's defect.
 *
 * Three states, and the distinction between the last two is the point:
 *   • a value      — read from the property `ARTICLE_25_FIELDS` declares
 *   • "not yet recorded" — the platform HAS a column and the row is empty, so
 *                    somebody can fill it in here
 *   • "not held by the platform" — there is no column, so the DPO must supply
 *                    it in the SDAIA portal and no amount of data entry here
 *                    will produce it
 *
 * The third state is never rendered as an empty value or a zero: an absence
 * that looks like a blank field reads as "nothing further is required"
 * (F2-197), and this is a statutory submission. Em-dash + a bilingual hint on
 * both `title` and `aria-label` is UX-008's convention, the same treatment
 * F2-195 and F2-197 used for a metric the platform cannot compute.
 */
function Article25Inventory({ breach, lang, t }: {
  breach: PrivacyBreachDto; lang: 'en' | 'ar'; t: (k: string) => string;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {ARTICLE_25_FIELDS.map(f => (
        <Article25Row key={f.key} field={f} breach={breach} lang={lang} t={t} />
      ))}
    </div>
  );
}

function Article25Row({ field, breach, lang, t }: {
  field: Article25Field; breach: PrivacyBreachDto; lang: 'en' | 'ar'; t: (k: string) => string;
}) {
  const value = article25Value(field, breach, lang);
  const held = field.source !== null;
  const notHeldHint = t('pdplArt25NotHeldHint');

  return (
    <div style={{
      display: 'grid', gridTemplateColumns: '190px 1fr auto', gap: 12,
      padding: '8px 0', borderBottom: '1px dashed var(--border)', alignItems: 'baseline',
    }}>
      <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {/* ⚠ `textTransform: none` — the enclosing label is uppercased, which
            rendered the citation as "25(A)". The PDPL Implementing Regulations
            cite sub-paragraphs in lower case, so the uppercase style was
            silently altering a regulatory reference. Found in the browser. */}
        <span style={{ color: 'var(--ink-4)', marginInlineEnd: 5, textTransform: 'none' }}>
          {field.clause === 'contact' || field.clause === 'ref' ? '' : `25(${field.clause})`}
        </span>
        {t('pdplArt25' + field.key)}
      </span>

      {value !== undefined ? (
        <span style={{ fontSize: 12, color: 'var(--ink-1)', lineHeight: 1.5 }}>{value}</span>
      ) : held ? (
        <span style={{ fontSize: 12, color: 'var(--ink-3)', lineHeight: 1.5 }}>
          {t('pdplArt25Empty')}
        </span>
      ) : (
        <span style={{ fontSize: 12, color: 'var(--ink-3)', lineHeight: 1.5 }}
          title={notHeldHint} aria-label={notHeldHint}>
          —
        </span>
      )}

      {!held && (
        <Badge tone="neutral">{t('pdplArt25DpoSupplies')}</Badge>
      )}
      {held && value === undefined && (
        <Badge tone="warning">{lang === 'ar' ? 'غير مُدخَل' : 'Blank'}</Badge>
      )}
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

// ⚠⚠ F2-209: `downloadPacket` used to live here. It wrote
//   JSON.stringify({ packet, breach })
// — the invented Article 25 submission and the genuine server row, side by side
// in one file, named `PDPL-N-<id>-<year>.json` from a client-minted reference.
//
// 🔑 That is why F2-199's `exportProvenance` gate was green over it: the page
// does call a server, and the emitted object does contain real server data. A
// file-level provenance rule cannot see that HALF a document is invented, and
// the real half is what makes the invented half credible. The gate this slice
// adds asks the question one level down — per FIELD, not per file.
//
// Nothing replaces it. A download of the inventory above would be a worksheet
// of five real values and five em-dashes, and the DPO fills the SDAIA portal in
// by hand anyway (SDAIA has no machine-readable API). Offering a file whose
// name looks like a submission reference is what got us here.
