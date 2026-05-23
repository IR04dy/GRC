import { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Send, Pause, Play, StopCircle, XCircle, Plus, Clock, AlertTriangle,
  CheckCircle2, Megaphone, Landmark, Info, Users, MessageSquare,
  Activity, Sparkles,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import { hasAnyRole } from '../../lib/auth';
import {
  getRun, dispatchInject, addCustomInject, transitionRun, listResponses,
  subscribeToRun,
  fmtOffset, toArabicDigits, injectScheduleStatus,
  INJECT_TYPE_LABELS, INJECT_STATUS_LABELS, RUN_STATUS_LABELS,
  type RunDetail, type InjectView, type ResponseView, type InjectType,
} from '../../lib/exerciseRuntime';

/**
 * Slice 10-B — Facilitator Console.
 *
 * <p>Three-column desktop, one-column mobile. The Saudi-native title reads
 * "وحدة تحكم المُنسِّق" — purposely NOT the literal "وحدة تحكم الميسر"
 * because BCM exercises in Saudi enterprises are run by a المُنسِّق
 * (coordinator), not a facilitator-in-the-trainer-sense.
 *
 * <p>Left:   inject queue, ordered by planned offset, with relative-time chip
 * <p>Center: active inject + live response feed
 * <p>Right:  run controls (Pause / Resume / End / Add Custom Inject) +
 *           participant roster + run timeline mini-view
 *
 * <p>Live updates come over STOMP — subscribeToRun() wires up the topic
 * /topic/org.{orgId}.exercise-run.{runId} and we re-fetch the active inject's
 * response list when the server tells us a new response landed.
 */
export function FacilitatorConsolePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();
  const { runId: runIdStr } = useParams<{ runId: string }>();
  const runId = Number(runIdStr);

  const allowed = hasAnyRole('BCM_MANAGER', 'FACILITATOR', 'PLATFORM_ADMIN');

  const [detail, setDetail]         = useState<RunDetail | null>(null);
  const [loading, setLoading]       = useState(true);
  const [busy, setBusy]             = useState(false);
  const [activeId, setActiveId]     = useState<number | null>(null);
  const [responses, setResponses]   = useState<ResponseView[]>([]);

  // Custom inject form
  const [showCustomForm, setShowCustomForm] = useState(false);
  const [customEn, setCustomEn] = useState('');
  const [customAr, setCustomAr] = useState('');
  const [customExpEn, setCustomExpEn] = useState('');
  const [customExpAr, setCustomExpAr] = useState('');
  const [customType, setCustomType] = useState<InjectType>('CUSTOM');

  // Tick the clock so the relative-time chips re-evaluate
  const [, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(id);
  }, []);

  // ─── Load ────────────────────────────────────────────────────────────
  async function reload() {
    if (!Number.isFinite(runId)) return;
    setLoading(true);
    try {
      const d = await getRun(runId);
      setDetail(d);
      if (!activeId && d.injects.length) {
        // Auto-pick the most recently dispatched, or the first PENDING
        const dispatched = d.injects.find(i => i.status === 'DISPATCHED' || i.status === 'ACKNOWLEDGED');
        setActiveId(dispatched?.id ?? d.injects[0].id);
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل تفاصيل التمرين' : 'Failed to load run detail'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, [runId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Load responses for the active inject
  useEffect(() => {
    if (!activeId || !Number.isFinite(runId)) { setResponses([]); return; }
    let cancelled = false;
    (async () => {
      try {
        const list = await listResponses(runId, activeId);
        if (!cancelled) setResponses(list);
      } catch {/* stale id — silent */}
    })();
    return () => { cancelled = true; };
  }, [activeId, runId]);

  // STOMP subscription — re-fetch on dispatch / response / state-change
  useEffect(() => {
    if (!Number.isFinite(runId)) return;
    const unsub = subscribeToRun(runId, env => {
      const data = env.data ?? {};
      if (data.type === 'RESPONSE_RECORDED' && Number(data.runInjectId) === activeId) {
        listResponses(runId, activeId).then(setResponses).catch(() => {/* noop */});
      }
      // Any structural change refreshes the queue
      if (data.type === 'INJECT_DISPATCHED' || data.type === 'CUSTOM_INJECT_ADDED'
          || data.type === 'STATE_CHANGE' || data.type === 'RESPONSE_RECORDED') {
        getRun(runId).then(setDetail).catch(() => {/* noop */});
      }
    });
    return unsub;
  }, [runId, activeId]);

  // ─── Derived ─────────────────────────────────────────────────────────
  const summary = detail?.summary;
  const injects = useMemo(() => detail?.injects ?? [], [detail]);
  const activeInject = useMemo(
    () => injects.find(i => i.id === activeId) ?? null,
    [injects, activeId],
  );
  const runStartedAt = summary?.startedAt;
  const runActive = summary?.status === 'IN_PROGRESS';

  // ─── Actions ─────────────────────────────────────────────────────────
  async function onDispatch(inj: InjectView) {
    if (!summary || !runActive) {
      toast.warning(isAr ? 'لا يمكن الإطلاق — التمرين غير نشط' : 'Run is not active');
      return;
    }
    setBusy(true);
    try {
      await dispatchInject(summary.id, inj.id);
      toast.success(isAr ? 'تم إطلاق الإدخال' : 'Inject dispatched');
      setActiveId(inj.id);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر الإطلاق' : 'Dispatch failed'));
    } finally {
      setBusy(false);
    }
  }

  async function onTransition(action: 'PAUSE' | 'RESUME' | 'END' | 'ABORT') {
    if (!summary) return;
    const confirmMessages: Record<typeof action, [string, string]> = {
      PAUSE:  ['Pause the run?',     'إيقاف التمرين مؤقتاً؟'],
      RESUME: ['Resume the run?',    'استئناف التمرين؟'],
      END:    ['End the run? An after-action draft will be created.', 'إنهاء التمرين؟ سيتم إنشاء مسودّة ورشة الدروس.'],
      ABORT:  ['Abort the run? This cannot be undone.', 'إلغاء التمرين؟ لا يمكن التراجع عن هذا الإجراء.'],
    };
    const msg = isAr ? confirmMessages[action][1] : confirmMessages[action][0];
    if (!window.confirm(msg)) return;

    setBusy(true);
    try {
      await transitionRun(summary.id, action);
      toast.success(isAr ? 'تمّ تحديث الحالة' : 'Run state updated');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تغيير الحالة' : 'State change failed'));
    } finally {
      setBusy(false);
    }
  }

  async function onCustomInject() {
    if (!summary) return;
    if (!customEn.trim()) {
      toast.warning(isAr ? 'نص الإدخال بالإنجليزية مطلوب' : 'English inject text is required');
      return;
    }
    setBusy(true);
    try {
      await addCustomInject(summary.id, {
        injectTextEn: customEn,
        injectTextAr: customAr || undefined,
        expectedActionEn: customExpEn || undefined,
        expectedActionAr: customExpAr || undefined,
        injectType: customType,
        dispatchNow: true,
      });
      toast.success(isAr ? 'تمّ إضافة الموقف الطارئ وإطلاقه'
                          : 'Custom inject added and dispatched');
      setCustomEn(''); setCustomAr(''); setCustomExpEn(''); setCustomExpAr('');
      setShowCustomForm(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر إضافة الموقف الطارئ' : 'Failed to add custom inject'));
    } finally {
      setBusy(false);
    }
  }

  // ─── Guards ─────────────────────────────────────────────────────────
  if (!allowed) {
    return (
      <Shell>
        <PageHeader
          title={isAr ? 'وحدة تحكم المُنسِّق' : 'Facilitator console'}
          eyebrow="BCM" />
        <Card>
          <InlineEmpty>
            <AlertTriangle size={32} className="mx-auto mb-2 text-warning-600" />
            <div className="font-semibold mb-1">{isAr ? 'غير مصرّح' : 'Not authorised'}</div>
            <div className="text-sm text-tx-muted">
              {isAr
                ? 'هذه الواجهة مخصّصة للمُنسِّق ومدير استمرارية الأعمال فقط.'
                : 'This console is available only to facilitators and BCM managers.'}
            </div>
          </InlineEmpty>
        </Card>
      </Shell>
    );
  }

  if (loading || !summary) {
    return (
      <Shell>
        <PageHeader
          title={isAr ? 'وحدة تحكم المُنسِّق' : 'Facilitator console'}
          eyebrow="BCM" />
        <Card>
          <div className="py-12 text-center text-sm text-tx-muted">
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
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
        title={isAr ? 'وحدة تحكم المُنسِّق' : 'Facilitator console'}
        eyebrow={`BCM · ${summary.runCode}`}
        actions={
          <div className="flex items-center gap-2">
            <Badge tone={statusTone}>{isAr
              ? RUN_STATUS_LABELS[summary.status].ar
              : RUN_STATUS_LABELS[summary.status].en}</Badge>
            <Button variant="ghost" onClick={() => navigate(-1)}>
              {isAr ? 'رجوع' : 'Back'}
            </Button>
          </div>
        } />

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 mt-4">
        {/* ─── Left: Inject queue ─── */}
        <div className="lg:col-span-4 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-tx-strong flex items-center gap-2">
              <Activity size={16} /> {isAr ? 'قائمة الإدخالات' : 'Inject queue'}
            </h3>
            <span className="text-xs text-tx-muted">
              {isAr
                ? `${toArabicDigits(injects.length)} إدخالاً`
                : `${injects.length} injects`}
            </span>
          </div>

          {injects.length === 0 ? (
            <Card><InlineEmpty text={isAr
              ? 'لا توجد إدخالات بعد — يمكن إضافة موقف طارئ من اللوحة اليمنى.'
              : 'No injects yet — add a custom inject from the right panel.'} /></Card>
          ) : (
            <div className="space-y-2 max-h-[calc(100vh-220px)] overflow-y-auto pr-1">
              {injects.map(inj => (
                <InjectQueueCard
                  key={inj.id}
                  inj={inj}
                  isActive={inj.id === activeId}
                  isAr={isAr}
                  runStartedAt={runStartedAt}
                  busy={busy || !runActive}
                  onSelect={() => setActiveId(inj.id)}
                  onDispatch={() => onDispatch(inj)} />
              ))}
            </div>
          )}
        </div>

        {/* ─── Center: Active inject + responses ─── */}
        <div className="lg:col-span-5 space-y-3">
          <h3 className="text-sm font-semibold text-tx-strong flex items-center gap-2">
            <MessageSquare size={16} />
            {isAr ? 'الإدخال النشط' : 'Active inject'}
          </h3>
          {activeInject ? (
            <ActiveInjectCard inj={activeInject} isAr={isAr} />
          ) : (
            <Card><InlineEmpty text={isAr
              ? 'لم يتم اختيار إدخال — اختر واحداً من القائمة.'
              : 'No inject selected — pick one from the queue.'} /></Card>
          )}

          <h3 className="text-sm font-semibold text-tx-strong flex items-center gap-2 mt-4">
            <Users size={16} />
            {isAr ? 'الردود الواردة' : 'Incoming responses'}
            <Badge tone="info">{isAr ? toArabicDigits(responses.length) : responses.length}</Badge>
          </h3>
          {responses.length === 0 ? (
            <Card><InlineEmpty>
              <Clock size={20} className="mx-auto mb-1 text-tx-muted" />
              <div className="font-semibold">{isAr ? 'بانتظار الردود' : 'Awaiting responses'}</div>
              <div className="text-xs text-tx-muted mt-1">
                {isAr
                  ? 'الردود ستظهر هنا فور إرسالها من المشاركين.'
                  : 'Responses appear here in real time once participants submit.'}
              </div>
            </InlineEmpty></Card>
          ) : (
            <div className="space-y-2">
              {responses.map(r => (
                <ResponseCard key={r.id} r={r} isAr={isAr} />
              ))}
            </div>
          )}
        </div>

        {/* ─── Right: Run controls + roster + summary ─── */}
        <div className="lg:col-span-3 space-y-3">
          <Card>
            <h3 className="text-sm font-semibold text-tx-strong mb-3">
              {isAr ? 'تحكّم بالتمرين' : 'Run controls'}
            </h3>
            <div className="grid grid-cols-1 gap-2">
              {summary.status === 'IN_PROGRESS' && (
                <>
                  <Button variant="secondary" Icon={Pause} disabled={busy}
                    onClick={() => onTransition('PAUSE')}>
                    {isAr ? 'إيقاف مؤقت' : 'Pause'}
                  </Button>
                  <Button variant="primary" Icon={StopCircle} disabled={busy}
                    onClick={() => onTransition('END')}>
                    {isAr ? 'إنهاء التمرين' : 'End run'}
                  </Button>
                  <Button variant="danger" Icon={XCircle} disabled={busy}
                    onClick={() => onTransition('ABORT')}>
                    {isAr ? 'إلغاء التمرين' : 'Abort'}
                  </Button>
                </>
              )}
              {summary.status === 'PAUSED' && (
                <>
                  <Button variant="primary" Icon={Play} disabled={busy}
                    onClick={() => onTransition('RESUME')}>
                    {isAr ? 'استئناف' : 'Resume'}
                  </Button>
                  <Button variant="primary" Icon={StopCircle} disabled={busy}
                    onClick={() => onTransition('END')}>
                    {isAr ? 'إنهاء التمرين' : 'End run'}
                  </Button>
                  <Button variant="danger" Icon={XCircle} disabled={busy}
                    onClick={() => onTransition('ABORT')}>
                    {isAr ? 'إلغاء التمرين' : 'Abort'}
                  </Button>
                </>
              )}
              {(summary.status === 'COMPLETED' || summary.status === 'ABORTED') && (
                <div className="text-xs text-tx-muted text-center py-2">
                  {isAr ? 'تمّ إنهاء التمرين — لا توجد إجراءات إضافية.'
                        : 'Run is closed — no further actions.'}
                </div>
              )}
            </div>
          </Card>

          <Card>
            <h3 className="text-sm font-semibold text-tx-strong mb-2 flex items-center gap-2">
              <Sparkles size={16} />
              {isAr ? 'إضافة موقف طارئ' : 'Custom inject'}
            </h3>
            {!showCustomForm ? (
              <Button variant="secondary" Icon={Plus}
                disabled={!runActive || busy}
                onClick={() => setShowCustomForm(true)}>
                {isAr ? 'إضافة موقف طارئ' : 'Add custom inject'}
              </Button>
            ) : (
              <div className="space-y-2 text-sm">
                <select className="form-select w-full text-sm"
                  value={customType}
                  onChange={e => setCustomType(e.target.value as InjectType)}>
                  {(['CUSTOM','INFORMATION','ESCALATION','DECISION','MEDIA','REGULATOR'] as const)
                    .map(t => (
                      <option key={t} value={t}>
                        {isAr ? INJECT_TYPE_LABELS[t].ar : INJECT_TYPE_LABELS[t].en}
                      </option>
                    ))}
                </select>
                <textarea className="form-textarea w-full text-sm" rows={2}
                  placeholder={isAr ? 'النص (إنجليزي)' : 'Inject text (English)'}
                  value={customEn} onChange={e => setCustomEn(e.target.value)} />
                <textarea className="form-textarea w-full text-sm" rows={2} dir="rtl"
                  placeholder="النص (عربي)"
                  value={customAr} onChange={e => setCustomAr(e.target.value)} />
                <textarea className="form-textarea w-full text-sm" rows={2}
                  placeholder={isAr ? 'الإجراء المتوقع (إنجليزي)' : 'Expected action (English)'}
                  value={customExpEn} onChange={e => setCustomExpEn(e.target.value)} />
                <textarea className="form-textarea w-full text-sm" rows={2} dir="rtl"
                  placeholder="الإجراء المتوقع (عربي)"
                  value={customExpAr} onChange={e => setCustomExpAr(e.target.value)} />
                <div className="flex gap-2">
                  <Button variant="primary" Icon={Send} disabled={busy}
                    onClick={onCustomInject}>
                    {isAr ? 'إطلاق الآن' : 'Dispatch now'}
                  </Button>
                  <Button variant="ghost" onClick={() => setShowCustomForm(false)}>
                    {isAr ? 'إلغاء' : 'Cancel'}
                  </Button>
                </div>
              </div>
            )}
          </Card>

          <Card>
            <h3 className="text-sm font-semibold text-tx-strong mb-2 flex items-center gap-2">
              <Users size={16} />
              {isAr ? 'ملخص التمرين' : 'Run summary'}
            </h3>
            <dl className="space-y-1.5 text-sm">
              <SummaryRow
                label={isAr ? 'المشاركون' : 'Participants'}
                value={isAr ? toArabicDigits(summary.participantCount) : String(summary.participantCount)} />
              <SummaryRow
                label={isAr ? 'مجموع الإدخالات' : 'Total injects'}
                value={isAr ? toArabicDigits(summary.totalInjects) : String(summary.totalInjects)} />
              <SummaryRow
                label={isAr ? 'تمّ إطلاقها' : 'Dispatched'}
                value={isAr ? toArabicDigits(summary.dispatchedInjects) : String(summary.dispatchedInjects)} />
              <SummaryRow
                label={isAr ? 'قيد الانتظار' : 'Pending'}
                value={isAr ? toArabicDigits(summary.pendingInjects) : String(summary.pendingInjects)} />
              <SummaryRow
                label={isAr ? 'بدأ في' : 'Started'}
                value={summary.startedAt ? new Date(summary.startedAt).toLocaleTimeString() : '—'} />
            </dl>
          </Card>
        </div>
      </div>
    </Shell>
  );
}

// ─── Helpers (sub-components) ─────────────────────────────────────────

function InjectQueueCard({ inj, isActive, isAr, runStartedAt, busy, onSelect, onDispatch }: {
  inj: InjectView;
  isActive: boolean;
  isAr: boolean;
  runStartedAt: string | undefined;
  busy: boolean;
  onSelect: () => void;
  onDispatch: () => void;
}) {
  const typeLabel = isAr ? INJECT_TYPE_LABELS[inj.injectType].ar : INJECT_TYPE_LABELS[inj.injectType].en;
  const statusLabel = isAr ? INJECT_STATUS_LABELS[inj.status].ar : INJECT_STATUS_LABELS[inj.status].en;
  const tone: BadgeTone =
    inj.status === 'PENDING'      ? 'neutral' :
    inj.status === 'DISPATCHED'   ? 'warning' :
    inj.status === 'ACKNOWLEDGED' ? 'info'    :
    inj.status === 'COMPLETED'    ? 'success' : 'neutral';

  const typeTone: BadgeTone =
    inj.injectType === 'REGULATOR'  ? 'danger'  :
    inj.injectType === 'DECISION'   ? 'warning' :
    inj.injectType === 'ESCALATION' ? 'warning' :
    inj.injectType === 'MEDIA'      ? 'info'    :
    inj.injectType === 'CUSTOM'     ? 'accent'  : 'neutral';

  const sched = inj.status === 'PENDING' ? injectScheduleStatus(inj, runStartedAt, new Date()) : null;
  const schedChip = sched ? (
    sched.state === 'ON_TIME' ? (isAr ? 'في الموعد' : 'On schedule') :
    sched.state === 'LATE'    ? (isAr ? `متأخر بـ ${toArabicDigits(sched.deltaMinutes)} دقيقة` : `Late by ${sched.deltaMinutes}m`) :
                                (isAr ? `مُبكِر بـ ${toArabicDigits(sched.deltaMinutes)} دقيقة` : `Early by ${sched.deltaMinutes}m`)
  ) : null;
  const schedTone: BadgeTone =
    sched?.state === 'LATE'  ? 'danger'  :
    sched?.state === 'EARLY' ? 'info'    : 'success';

  const txt = isAr && inj.injectTextAr ? inj.injectTextAr : inj.injectTextEn;

  return (
    <div style={isActive ? { outline: '2px solid var(--primary)', borderRadius: 8 } : undefined}>
    <Card>
      <div onClick={onSelect} className="cursor-pointer space-y-2">
        <div className="flex items-center gap-2 flex-wrap">
          <Badge tone={typeTone}>{typeLabel}</Badge>
          <Badge tone={tone}>{statusLabel}</Badge>
          <span className="text-xs text-tx-muted ml-auto">
            <Clock size={12} className="inline mr-0.5" />
            {fmtOffset(inj.plannedTimeOffsetMinutes, isAr)}
          </span>
        </div>
        <div className="text-sm text-tx-strong line-clamp-2" dir={isAr ? 'rtl' : 'ltr'}>
          {txt}
        </div>
        {schedChip && (
          <Badge tone={schedTone} dot>{schedChip}</Badge>
        )}
        {inj.responseCount > 0 && (
          <div className="text-xs text-tx-muted">
            {isAr
              ? `${toArabicDigits(inj.responseCount)} رد`
              : `${inj.responseCount} response${inj.responseCount === 1 ? '' : 's'}`}
          </div>
        )}
      </div>
      {inj.status === 'PENDING' && (
        <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--border)' }}>
          <Button size="sm" variant="primary" Icon={Send}
            disabled={busy}
            onClick={(e) => { e.stopPropagation(); onDispatch(); }}>
            {isAr ? 'إطلاق الآن' : 'Dispatch now'}
          </Button>
        </div>
      )}
    </Card>
    </div>
  );
}

function ActiveInjectCard({ inj, isAr }: { inj: InjectView; isAr: boolean }) {
  const txt = isAr && inj.injectTextAr ? inj.injectTextAr : inj.injectTextEn;
  const exp = isAr && inj.expectedActionAr ? inj.expectedActionAr : inj.expectedActionEn;
  const TypeIcon =
    inj.injectType === 'REGULATOR'  ? Landmark   :
    inj.injectType === 'MEDIA'      ? Megaphone  :
    inj.injectType === 'DECISION'   ? AlertTriangle :
    inj.injectType === 'ESCALATION' ? AlertTriangle :
    inj.injectType === 'CUSTOM'     ? Sparkles   : Info;

  return (
    <Card>
      <div className="flex items-center gap-2 mb-2">
        <TypeIcon size={18} className="text-primary-600" />
        <span className="text-sm font-semibold text-tx-strong">
          {isAr ? INJECT_TYPE_LABELS[inj.injectType].ar : INJECT_TYPE_LABELS[inj.injectType].en}
        </span>
        <span className="ml-auto text-xs text-tx-muted">
          {fmtOffset(inj.plannedTimeOffsetMinutes, isAr)}
        </span>
      </div>
      <div className="text-base text-tx-strong leading-relaxed mb-3" dir={isAr ? 'rtl' : 'ltr'}>
        {txt}
      </div>
      {exp && (
        <div className="border-t border-bd-soft pt-3 mt-3">
          <div className="text-xs uppercase tracking-wide text-tx-muted mb-1">
            {isAr ? 'الإجراء المتوقع' : 'Expected action'}
          </div>
          <div className="text-sm text-tx-strong" dir={isAr ? 'rtl' : 'ltr'}>
            {exp}
          </div>
        </div>
      )}
      {inj.actualDispatchedAt && (
        <div className="mt-3 text-xs text-tx-muted">
          {isAr ? 'تمّ الإطلاق في ' : 'Dispatched at '}
          {new Date(inj.actualDispatchedAt).toLocaleTimeString()}
        </div>
      )}
    </Card>
  );
}

function ResponseCard({ r, isAr }: { r: ResponseView; isAr: boolean }) {
  const responseTxt = isAr && r.responseTextAr ? r.responseTextAr : r.responseTextEn;
  const decision = isAr && r.decisionMadeAr ? r.decisionMadeAr : r.decisionMadeEn;
  const secs = r.responseTimeSeconds ?? 0;
  const fast = secs < 60;
  return (
    <Card>
      <div className="flex items-center gap-2 mb-1">
        <Users size={14} className="text-tx-muted" />
        <span className="text-xs text-tx-muted">
          {isAr
            ? `المُشارِك #${toArabicDigits(r.participantUserId)}`
            : `Participant #${r.participantUserId}`}
        </span>
        <Badge tone={fast ? 'success' : 'warning'} className="ml-auto">
          {isAr ? `${toArabicDigits(secs)} ثانية` : `${secs}s`}
        </Badge>
      </div>
      {responseTxt && (
        <div className="text-sm text-tx-strong" dir={isAr ? 'rtl' : 'ltr'}>
          {responseTxt}
        </div>
      )}
      {decision && (
        <div className="mt-2 text-xs text-tx-muted">
          <CheckCircle2 size={12} className="inline mr-1 text-success-600" />
          <span className="font-medium">{isAr ? 'القرار: ' : 'Decision: '}</span>
          <span dir={isAr ? 'rtl' : 'ltr'}>{decision}</span>
        </div>
      )}
    </Card>
  );
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between items-baseline">
      <dt className="text-tx-muted">{label}</dt>
      <dd className="font-medium text-tx-strong">{value}</dd>
    </div>
  );
}
