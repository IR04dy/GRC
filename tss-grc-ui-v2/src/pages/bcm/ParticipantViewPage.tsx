import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Send, Clock, CheckCircle2, Info, AlertTriangle, History,
  Landmark, Megaphone, Sparkles,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import { getUser } from '../../lib/auth';
import {
  getRun, recordResponse, subscribeToRun,
  fmtOffset, toArabicDigits,
  INJECT_TYPE_LABELS, INJECT_STATUS_LABELS, RUN_STATUS_LABELS,
  type RunDetail, type InjectView,
} from '../../lib/exerciseRuntime';

/**
 * Slice 10-B — Participant View.
 *
 * <p>Mobile-first single column. The Saudi-native title is
 * "عرض المُشارِك" (participant view).
 *
 * <p>Shows the currently DISPATCHED inject (waiting for the participant's
 * response), a text area for the response + decision, and a 50px-tall submit
 * button (the touch target requirement noted in the slice spec). Past
 * dispatched injects are collapsed below for context.
 */
export function ParticipantViewPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const { runId: runIdStr } = useParams<{ runId: string }>();
  const runId = Number(runIdStr);
  const user = getUser();

  const [detail, setDetail]   = useState<RunDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy]       = useState(false);

  const [respEn, setRespEn]     = useState('');
  const [respAr, setRespAr]     = useState('');
  const [decisionEn, setDecisionEn] = useState('');
  const [decisionAr, setDecisionAr] = useState('');

  // Track which dispatched injects the participant has already responded to,
  // so we hide the form once they submit.
  const [submitted, setSubmitted] = useState<Set<number>>(new Set());

  async function reload() {
    if (!Number.isFinite(runId)) return;
    setLoading(true);
    try {
      const d = await getRun(runId);
      setDetail(d);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل التمرين' : 'Failed to load run'));
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, [runId]); // eslint-disable-line react-hooks/exhaustive-deps

  // STOMP — refresh when a new inject is dispatched or run state changes
  useEffect(() => {
    if (!Number.isFinite(runId)) return;
    const unsub = subscribeToRun(runId, env => {
      const t = env.data?.type;
      if (t === 'INJECT_DISPATCHED' || t === 'STATE_CHANGE' || t === 'CUSTOM_INJECT_ADDED') {
        getRun(runId).then(setDetail).catch(() => {/* noop */});
      }
    });
    return unsub;
  }, [runId]);

  // ─── Derived ─────────────────────────────────────────────────────────
  const summary = detail?.summary;
  const allInjects = useMemo(() => detail?.injects ?? [], [detail]);
  // Most recently dispatched (active) — by actualDispatchedAt descending
  const active = useMemo(() => {
    const dispatched = allInjects
      .filter(i => i.status === 'DISPATCHED' && !submitted.has(i.id));
    if (dispatched.length === 0) return null;
    // Sort by actualDispatchedAt desc to surface the freshest
    return dispatched.sort((a, b) =>
      new Date(b.actualDispatchedAt ?? 0).getTime() -
      new Date(a.actualDispatchedAt ?? 0).getTime()
    )[0];
  }, [allInjects, submitted]);

  const past = useMemo(() => allInjects.filter(i =>
    (i.actualDispatchedAt && i.id !== active?.id) || submitted.has(i.id)
  ).sort((a, b) =>
    new Date(b.actualDispatchedAt ?? 0).getTime() -
    new Date(a.actualDispatchedAt ?? 0).getTime()
  ), [allInjects, active, submitted]);

  // ─── Submit ──────────────────────────────────────────────────────────
  async function onSubmit() {
    if (!summary || !active || !user) return;
    if (!respEn.trim() && !respAr.trim() && !decisionEn.trim() && !decisionAr.trim()) {
      toast.warning(isAr ? 'الرجاء إدخال ردك قبل الإرسال'
                          : 'Please enter your response before submitting');
      return;
    }
    setBusy(true);
    try {
      // F2-226: `participantUserId` is gone. This used to send `user.userId` from the
      // decoded JWT — honest, and beside the point: the server stored whatever arrived,
      // so any other caller could file a response as somebody else and have their
      // reaction time certified. The attribution is now the authenticated principal.
      await recordResponse(summary.id, active.id, {
        responseTextEn: respEn || undefined,
        responseTextAr: respAr || undefined,
        decisionMadeEn: decisionEn || undefined,
        decisionMadeAr: decisionAr || undefined,
      });
      toast.success(isAr ? 'تم تسجيل الرد' : 'Response submitted');
      setSubmitted(prev => new Set(prev).add(active.id));
      setRespEn(''); setRespAr(''); setDecisionEn(''); setDecisionAr('');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر إرسال الرد' : 'Failed to submit response'));
    } finally { setBusy(false); }
  }

  // ─── Guards ──────────────────────────────────────────────────────────
  if (!user) {
    return (
      <Shell>
        <PageHeader title={isAr ? 'عرض المُشارِك' : 'Participant view'} eyebrow="BCM" />
        <Card>
          <InlineEmpty text={isAr ? 'الجلسة منتهية — يرجى تسجيل الدخول.'
                                  : 'Session expired — please log in.'} />
        </Card>
      </Shell>
    );
  }

  if (loading || !summary) {
    return (
      <Shell>
        <PageHeader title={isAr ? 'عرض المُشارِك' : 'Participant view'} eyebrow="BCM" />
        <Card>
          <InlineEmpty text={isAr ? 'جارٍ التحميل…' : 'Loading…'} />
        </Card>
      </Shell>
    );
  }

  const statusTone: BadgeTone =
    summary.status === 'IN_PROGRESS' ? 'success' :
    summary.status === 'PAUSED'      ? 'warning' :
    summary.status === 'COMPLETED'   ? 'info' :
    summary.status === 'ABORTED'     ? 'danger' : 'neutral';

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'عرض المُشارِك' : 'Participant view'}
        eyebrow={`BCM · ${summary.runCode}`}
        actions={<Badge tone={statusTone}>{tlabel(RUN_STATUS_LABELS, summary.status, isAr)}</Badge>} />

      <div className="max-w-2xl mx-auto space-y-4 mt-4">
        {/* Run paused / completed states */}
        {summary.status === 'PAUSED' && (
          <Card>
            <div className="text-center py-6">
              <Clock size={28} className="mx-auto mb-2 text-warning-600" />
              <div className="font-semibold text-tx-strong">
                {isAr ? 'التمرين متوقف مؤقتاً' : 'Run is paused'}
              </div>
              <div className="text-sm text-tx-muted mt-1">
                {isAr ? 'سيُستأنف قريباً من قِبَل المُنسِّق.'
                       : 'The facilitator will resume shortly.'}
              </div>
            </div>
          </Card>
        )}
        {summary.status === 'COMPLETED' && (
          <Card>
            <div className="text-center py-6">
              <CheckCircle2 size={28} className="mx-auto mb-2 text-success-600" />
              <div className="font-semibold text-tx-strong">
                {isAr ? 'انتهى التمرين — شكراً لمشاركتك' : 'Run completed — thank you'}
              </div>
            </div>
          </Card>
        )}
        {summary.status === 'ABORTED' && (
          <Card>
            <div className="text-center py-6">
              <AlertTriangle size={28} className="mx-auto mb-2 text-danger-600" />
              <div className="font-semibold text-tx-strong">
                {isAr ? 'تمّ إلغاء التمرين' : 'Run aborted'}
              </div>
            </div>
          </Card>
        )}

        {/* Active inject + response form */}
        {active && summary.status === 'IN_PROGRESS' && (
          <>
            <ActiveInjectBanner inj={active} isAr={isAr} />
            <Card>
              <h3 className="text-sm font-semibold text-tx-strong mb-3">
                {isAr ? 'ردّك / قرارك' : 'Your response / decision'}
              </h3>
              <div className="space-y-2">
                <textarea className="form-textarea w-full text-sm" rows={3}
                  placeholder={isAr ? 'الردّ (إنجليزي اختياري)' : 'Response (English)'}
                  value={respEn} onChange={e => setRespEn(e.target.value)} />
                <textarea className="form-textarea w-full text-sm" rows={3} dir="rtl"
                  placeholder="الردّ (عربي)"
                  value={respAr} onChange={e => setRespAr(e.target.value)} />
                <input className="form-input w-full text-sm"
                  placeholder={isAr ? 'القرار المتّخذ (إنجليزي)' : 'Decision made (English)'}
                  value={decisionEn} onChange={e => setDecisionEn(e.target.value)} />
                <input className="form-input w-full text-sm" dir="rtl"
                  placeholder="القرار المتّخذ (عربي)"
                  value={decisionAr} onChange={e => setDecisionAr(e.target.value)} />
              </div>
              <div className="mt-3">
                <button
                  type="button"
                  disabled={busy}
                  onClick={onSubmit}
                  className="btn btn-primary w-full flex items-center justify-center gap-2"
                  style={{ minHeight: 50 }}>
                  <Send size={18} />
                  <span className="font-medium">
                    {isAr ? 'إرسال الردّ' : 'Submit response'}
                  </span>
                </button>
              </div>
            </Card>
          </>
        )}

        {/* No active inject yet */}
        {!active && summary.status === 'IN_PROGRESS' && (
          <Card>
            <div className="text-center py-10">
              <Clock size={28} className="mx-auto mb-2 text-tx-muted" />
              <div className="font-semibold text-tx-strong">
                {isAr ? 'بانتظار الإدخال التالي' : 'Awaiting next inject'}
              </div>
              <div className="text-sm text-tx-muted mt-1">
                {isAr ? 'ستظهر هنا فور إطلاقها من قِبَل المُنسِّق.'
                       : 'It will appear here the moment the facilitator dispatches it.'}
              </div>
            </div>
          </Card>
        )}

        {/* Past injects */}
        {past.length > 0 && (
          <details className="bg-bg-soft rounded-md p-3">
            <summary className="cursor-pointer text-sm font-semibold text-tx-strong flex items-center gap-2">
              <History size={14} />
              {isAr ? `الإدخالات السابقة (${toArabicDigits(past.length)})`
                     : `Past injects (${past.length})`}
            </summary>
            <div className="mt-3 space-y-2">
              {past.map(i => (
                <PastInjectCard key={i.id} inj={i} isAr={isAr}
                  responded={submitted.has(i.id)} />
              ))}
            </div>
          </details>
        )}
      </div>
    </Shell>
  );
}

// ─── Sub-components ──────────────────────────────────────────────────

function ActiveInjectBanner({ inj, isAr }: { inj: InjectView; isAr: boolean }) {
  const txt = isAr && inj.injectTextAr ? inj.injectTextAr : inj.injectTextEn;
  const exp = isAr && inj.expectedActionAr ? inj.expectedActionAr : inj.expectedActionEn;
  const TypeIcon =
    inj.injectType === 'REGULATOR'  ? Landmark   :
    inj.injectType === 'MEDIA'      ? Megaphone  :
    inj.injectType === 'DECISION'   ? AlertTriangle :
    inj.injectType === 'ESCALATION' ? AlertTriangle :
    inj.injectType === 'CUSTOM'     ? Sparkles   : Info;
  const tone: BadgeTone =
    inj.injectType === 'REGULATOR'  ? 'danger'  :
    inj.injectType === 'DECISION'   ? 'warning' :
    inj.injectType === 'ESCALATION' ? 'warning' :
    inj.injectType === 'MEDIA'      ? 'info'    :
    inj.injectType === 'CUSTOM'     ? 'accent'  : 'neutral';
  return (
    <div style={{ outline: '2px solid var(--primary)', borderRadius: 8 }}>
    <Card>
      <div className="flex items-center gap-2 mb-3">
        <TypeIcon size={20} className="text-primary-600" />
        <Badge tone={tone}>{tlabel(INJECT_TYPE_LABELS, inj.injectType, isAr)}</Badge>
        <span className="text-xs text-tx-muted ml-auto">
          {fmtOffset(inj.plannedTimeOffsetMinutes, isAr)}
        </span>
      </div>
      <div className="text-base text-tx-strong leading-relaxed mb-3"
        dir={isAr ? 'rtl' : 'ltr'}>
        {txt}
      </div>
      {exp && (
        <div className="border-t border-bd-soft pt-3 mt-3 bg-warning-50 -mx-4 -mb-4 px-4 py-3 rounded-b-md">
          <div className="text-xs uppercase tracking-wide text-warning-700 mb-1 font-semibold">
            {isAr ? 'الإجراء المتوقع منك' : 'Action expected from you'}
          </div>
          <div className="text-sm text-tx-strong" dir={isAr ? 'rtl' : 'ltr'}>
            {exp}
          </div>
        </div>
      )}
    </Card>
    </div>
  );
}

function PastInjectCard({ inj, isAr, responded }: {
  inj: InjectView; isAr: boolean; responded: boolean;
}) {
  const txt = isAr && inj.injectTextAr ? inj.injectTextAr : inj.injectTextEn;
  const statusLabel = responded
    ? (isAr ? 'تمّ ردّك' : 'You responded')
    : (tlabel(INJECT_STATUS_LABELS, inj.status, isAr));
  const tone: BadgeTone = responded ? 'success'
    : inj.status === 'COMPLETED'    ? 'success'
    : inj.status === 'ACKNOWLEDGED' ? 'info'    : 'neutral';
  return (
    <Card>
      <div className="flex items-center gap-2 mb-1">
        <Badge tone="neutral">{tlabel(INJECT_TYPE_LABELS, inj.injectType, isAr)}</Badge>
        <Badge tone={tone}>{statusLabel}</Badge>
        <span className="text-xs text-tx-muted ml-auto">
          {fmtOffset(inj.plannedTimeOffsetMinutes, isAr)}
        </span>
      </div>
      <div className="text-sm text-tx-strong line-clamp-2" dir={isAr ? 'rtl' : 'ltr'}>
        {txt}
      </div>
    </Card>
  );
}
