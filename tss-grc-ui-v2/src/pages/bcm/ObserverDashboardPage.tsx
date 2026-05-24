import { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Eye, Clock, Send, Flag, AlertTriangle, MessageSquare, ListChecks,
  Users, Activity, FileText, Landmark, Megaphone, Info, Sparkles,
  ShieldCheck, CheckCircle2,
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
import { getUser, hasAnyRole } from '../../lib/auth';
import {
  fmtOffset, toArabicDigits, subscribeToRun,
  INJECT_TYPE_LABELS, INJECT_STATUS_LABELS, RUN_STATUS_LABELS,
  type InjectView, type ResponseView,
} from '../../lib/exerciseRuntime';
import {
  getObserverDashboard, recordObservation, flagObservation,
  OBSERVER_CONSOLE_TITLE, OBSERVATION_TYPE_LABELS, OBSERVATION_TYPE_ORDER,
  SEVERITY_LABELS, SEVERITY_ORDER, OBSERVER_ROLE_LABELS,
  observationTypeBadgeTone, severityBadgeTone,
  type ObserverDashboard, type ObservationView, type ObservationType,
  type ObservationSeverity,
} from '../../lib/exerciseObserver';

/**
 * Slice 10-C — Observer Dashboard.
 *
 * <p>Read-only mirror of the FacilitatorConsolePage for auditors, internal
 * audit, CISO, and regulator observers. The Saudi-native title reads
 * "وحدة المراقب" — purposely NOT the literal "لوحة المراقب".
 *
 * <p>Layout:
 *   Left:   Full chronological inject timeline (PENDING / DISPATCHED /
 *           ACKNOWLEDGED states all visible — no dispatch button).
 *   Center: Active inject + live response feed (read-only).
 *   Right:  Observer's own observation entry form + filterable list of all
 *           observations from all observers, colour-coded by type, with
 *           "flag for AAR" toggle on the observer's own rows.
 *
 * <p>Live updates come over the SAME STOMP topic Slice 10-B uses
 * (/topic/org.{orgId}.exercise-run.{runId}). New payload types observed:
 *   OBSERVATION_ADDED, OBSERVATION_FLAGGED, OBSERVER_ASSIGNED, OBSERVER_REMOVED.
 *
 * <p>READ-ONLY enforcement: no dispatch / transition affordances exist on
 * this page. Even if a malicious observer crafted a request, the backend's
 * controller-level @PreAuthorize requires FACILITATOR / BCM_MANAGER /
 * PLATFORM_ADMIN for any write to the run-state surface.
 */
export function ObserverDashboardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();
  const { runId: runIdStr } = useParams<{ runId: string }>();
  const runId = Number(runIdStr);
  const me = getUser();
  const myUserId = me?.userId ?? null;

  // The observer dashboard surface is broad — auditors, internal audit, CISO,
  // BCM_MANAGER, FACILITATOR, RISK_MANAGER, PLATFORM_ADMIN can all view.
  // The "record observation" form is gated separately on the assignment
  // flag returned by the backend.
  const allowedToView = hasAnyRole(
    'BCM_MANAGER', 'FACILITATOR', 'CISO', 'AUDITOR',
    'INTERNAL_AUDITOR', 'PLATFORM_ADMIN', 'RISK_MANAGER',
  );

  const [data, setData]     = useState<ObserverDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy]     = useState(false);
  const [activeId, setActiveId] = useState<number | null>(null);

  // Observation form state
  const [obsType, setObsType]   = useState<ObservationType>('GENERAL');
  const [obsSev, setObsSev]     = useState<ObservationSeverity>('INFO');
  const [obsEn, setObsEn]       = useState('');
  const [obsAr, setObsAr]       = useState('');
  const [recEn, setRecEn]       = useState('');
  const [recAr, setRecAr]       = useState('');
  const [linkInject, setLinkInject] = useState<number | null>(null);
  const [flagOnCreate, setFlagOnCreate] = useState(false);

  // Filter rail (right column)
  const [typeFilter, setTypeFilter] = useState<ObservationType | 'ALL'>('ALL');
  const [showMineOnly, setShowMineOnly] = useState(false);

  // ─── Load ────────────────────────────────────────────────────────────
  async function reload() {
    if (!Number.isFinite(runId)) return;
    setLoading(true);
    try {
      const d = await getObserverDashboard(runId);
      setData(d);
      if (activeId == null && d.injects.length) {
        // Auto-pick the most recently active inject (DISPATCHED / ACKNOWLEDGED)
        const active = d.injects.find(
          i => i.status === 'DISPATCHED' || i.status === 'ACKNOWLEDGED');
        setActiveId(active?.id ?? d.injects[0].id);
        if (active && linkInject == null) setLinkInject(active.id);
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحميل وحدة المراقب' : 'Failed to load observer dashboard'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, [runId]); // eslint-disable-line react-hooks/exhaustive-deps

  // STOMP subscription on the 10-B run topic — re-fetch on anything that
  // changes the read-only view.
  useEffect(() => {
    if (!Number.isFinite(runId)) return;
    const unsub = subscribeToRun(runId, env => {
      const t = env.data?.type;
      if (!t) return;
      if (t === 'INJECT_DISPATCHED' || t === 'CUSTOM_INJECT_ADDED'
          || t === 'STATE_CHANGE'  || t === 'RESPONSE_RECORDED'
          || t === 'OBSERVATION_ADDED' || t === 'OBSERVATION_FLAGGED'
          || t === 'OBSERVER_ASSIGNED'  || t === 'OBSERVER_REMOVED') {
        getObserverDashboard(runId).then(setData).catch(() => {/* swallow */});
      }
    });
    return unsub;
  }, [runId]);

  // ─── Derived ─────────────────────────────────────────────────────────
  const summary  = data?.summary;
  const injects  = useMemo(() => data?.injects ?? [], [data]);
  const observations = useMemo(() => data?.observations ?? [], [data]);
  const observers = useMemo(() => data?.observers ?? [], [data]);
  const activeInject = useMemo(
    () => injects.find(i => i.id === activeId) ?? null,
    [injects, activeId],
  );
  const activeResponses: ReadonlyArray<ResponseView> = useMemo(() => {
    if (!activeId || !data?.responsesByInject) return [];
    return data.responsesByInject[String(activeId)] ?? [];
  }, [activeId, data]);

  const filteredObservations = useMemo(() => {
    let list = observations;
    if (typeFilter !== 'ALL') list = list.filter(o => o.observationType === typeFilter);
    if (showMineOnly && myUserId != null) {
      list = list.filter(o => o.observerUserId === myUserId);
    }
    return list;
  }, [observations, typeFilter, showMineOnly, myUserId]);

  const canRecord = data?.isAssignedObserver === true;

  // ─── Actions ─────────────────────────────────────────────────────────
  async function onSubmit() {
    if (!canRecord) {
      toast.warning(isAr
        ? 'لست مُعيَّناً كمراقب على هذا التمرين'
        : 'You are not an assigned observer on this run');
      return;
    }
    if (!obsEn.trim() && !obsAr.trim()) {
      toast.warning(isAr ? 'يلزم إدخال نص الملاحظة' : 'Observation text is required');
      return;
    }
    setBusy(true);
    try {
      await recordObservation(runId, {
        observationTextEn: obsEn || undefined,
        observationTextAr: obsAr || undefined,
        observationType: obsType,
        severity: obsSev,
        recommendationEn: recEn || undefined,
        recommendationAr: recAr || undefined,
        relatedInjectId: linkInject ?? undefined,
        flagForAar: flagOnCreate,
      });
      toast.success(isAr ? 'تمّ تسجيل الملاحظة' : 'Observation recorded');
      setObsEn(''); setObsAr(''); setRecEn(''); setRecAr('');
      setFlagOnCreate(false);
      // Live broadcast will refresh, but reload for instant UI feedback.
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تسجيل الملاحظة' : 'Failed to record observation'));
    } finally {
      setBusy(false);
    }
  }

  async function onToggleFlag(obs: ObservationView) {
    if (obs.observerUserId !== myUserId) {
      toast.warning(isAr
        ? 'يمكن لمنشئ الملاحظة فقط تغيير علامة التقرير'
        : 'Only the observation author can change the AAR flag');
      return;
    }
    setBusy(true);
    try {
      await flagObservation(runId, obs.id, !obs.isFlaggedForAar);
      toast.success(obs.isFlaggedForAar
        ? (isAr ? 'تمت إزالة العلامة' : 'AAR flag cleared')
        : (isAr ? 'تمّ وضع علامة للتقرير' : 'Flagged for AAR'));
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تغيير العلامة' : 'Flag change failed'));
    } finally {
      setBusy(false);
    }
  }

  // ─── Guards ─────────────────────────────────────────────────────────
  if (!allowedToView) {
    return (
      <Shell>
        <PageHeader
          title={isAr ? OBSERVER_CONSOLE_TITLE.ar : OBSERVER_CONSOLE_TITLE.en}
          eyebrow="BCM" />
        <Card>
          <InlineEmpty>
            <AlertTriangle size={32} className="mx-auto mb-2 text-warning-600" />
            <div className="font-semibold mb-1">{isAr ? 'غير مصرّح' : 'Not authorised'}</div>
            <div className="text-sm text-tx-muted">
              {isAr
                ? 'هذه الواجهة مخصّصة للمراقبين والمراجعين فقط.'
                : 'This dashboard is only available to assigned observers and auditors.'}
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
          title={isAr ? OBSERVER_CONSOLE_TITLE.ar : OBSERVER_CONSOLE_TITLE.en}
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
        title={isAr ? OBSERVER_CONSOLE_TITLE.ar : OBSERVER_CONSOLE_TITLE.en}
        eyebrow={`BCM · ${summary.runCode}`}
        actions={
          <div className="flex items-center gap-2">
            <Badge tone="info" dot>
              <Eye size={12} className="inline" />
              {' '}
              {isAr ? 'وضع القراءة فقط' : 'Read-only'}
            </Badge>
            <Badge tone={statusTone}>{isAr
              ? RUN_STATUS_LABELS[summary.status].ar
              : RUN_STATUS_LABELS[summary.status].en}</Badge>
            <Button variant="ghost" onClick={() => navigate(-1)}>
              {isAr ? 'رجوع' : 'Back'}
            </Button>
          </div>
        } />

      {!canRecord && (
        <div className="mt-4">
          <Card>
            <div className="flex items-start gap-2">
              <AlertTriangle size={18} className="text-warning-600 mt-0.5" />
              <div className="text-sm">
                <div className="font-semibold text-tx-strong mb-0.5">
                  {isAr ? 'أنت في وضع المشاهدة' : 'You are in view-only mode'}
                </div>
                <div className="text-tx-muted">
                  {isAr
                    ? 'لتسجيل ملاحظات على هذا التمرين يجب تعيينك كمراقب من قبل المُنسِّق.'
                    : 'To record observations on this run you must be assigned as an observer by the facilitator.'}
                </div>
              </div>
            </div>
          </Card>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 mt-4">
        {/* ─── Left: Full chronological inject timeline ─── */}
        <div className="lg:col-span-3 space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-tx-strong flex items-center gap-2">
              <Activity size={16} /> {isAr ? 'مسار التمرين' : 'Run timeline'}
            </h3>
            <span className="text-xs text-tx-muted">
              {isAr
                ? `${toArabicDigits(injects.length)} إدخالاً`
                : `${injects.length} injects`}
            </span>
          </div>
          {injects.length === 0 ? (
            <Card><InlineEmpty text={isAr
              ? 'لا توجد إدخالات بعد.'
              : 'No injects yet.'} /></Card>
          ) : (
            <div className="space-y-2 max-h-[calc(100vh-260px)] overflow-y-auto pr-1">
              {injects.map(inj => (
                <ReadOnlyInjectCard
                  key={inj.id}
                  inj={inj}
                  isActive={inj.id === activeId}
                  isAr={isAr}
                  onSelect={() => { setActiveId(inj.id); setLinkInject(inj.id); }} />
              ))}
            </div>
          )}
        </div>

        {/* ─── Center: Active inject + responses (read-only mirror) ─── */}
        <div className="lg:col-span-5 space-y-3">
          <h3 className="text-sm font-semibold text-tx-strong flex items-center gap-2">
            <MessageSquare size={16} />
            {isAr ? 'الإدخال النشط' : 'Active inject'}
          </h3>
          {activeInject ? (
            <ReadOnlyActiveInjectCard inj={activeInject} isAr={isAr} />
          ) : (
            <Card><InlineEmpty text={isAr
              ? 'لم يتم اختيار إدخال — اختر واحداً من المسار.'
              : 'No inject selected — pick one from the timeline.'} /></Card>
          )}

          <h3 className="text-sm font-semibold text-tx-strong flex items-center gap-2 mt-4">
            <Users size={16} />
            {isAr ? 'الردود الواردة' : 'Incoming responses'}
            <Badge tone="info">{isAr ? toArabicDigits(activeResponses.length) : activeResponses.length}</Badge>
          </h3>
          {activeResponses.length === 0 ? (
            <Card><InlineEmpty>
              <Clock size={20} className="mx-auto mb-1 text-tx-muted" />
              <div className="font-semibold">{isAr ? 'بانتظار الردود' : 'Awaiting responses'}</div>
            </InlineEmpty></Card>
          ) : (
            <div className="space-y-2">
              {activeResponses.map(r => (
                <ReadOnlyResponseCard key={r.id} r={r} isAr={isAr} />
              ))}
            </div>
          )}

          {/* Observer roster — small panel */}
          <Card>
            <h3 className="text-sm font-semibold text-tx-strong mb-2 flex items-center gap-2">
              <ShieldCheck size={16} />
              {isAr ? 'فريق المراقبين' : 'Observer roster'}
              <Badge tone="neutral">{isAr ? toArabicDigits(observers.length) : observers.length}</Badge>
            </h3>
            {observers.length === 0 ? (
              <div className="text-xs text-tx-muted">{isAr
                ? 'لم يتم تعيين أيّ مراقب بعد.'
                : 'No observers assigned yet.'}</div>
            ) : (
              <div className="flex flex-wrap gap-2">
                {observers.map(o => (
                  <Badge key={o.id} tone="accent">
                    #{isAr ? toArabicDigits(o.observerUserId) : o.observerUserId}
                    {' · '}
                    {isAr ? OBSERVER_ROLE_LABELS[o.observerRole].ar : OBSERVER_ROLE_LABELS[o.observerRole].en}
                  </Badge>
                ))}
              </div>
            )}
          </Card>
        </div>

        {/* ─── Right: Observation entry + filterable list ─── */}
        <div className="lg:col-span-4 space-y-3">
          {/* Observation entry form (only shown to assigned observers) */}
          {canRecord && (
            <Card>
              <h3 className="text-sm font-semibold text-tx-strong mb-2 flex items-center gap-2">
                <FileText size={16} />
                {isAr ? 'تسجيل ملاحظة' : 'Record observation'}
              </h3>
              <div className="space-y-2 text-sm">
                <div className="grid grid-cols-2 gap-2">
                  <select className="form-select text-sm"
                    value={obsType}
                    onChange={e => setObsType(e.target.value as ObservationType)}>
                    {OBSERVATION_TYPE_ORDER.map(t => (
                      <option key={t} value={t}>
                        {isAr ? OBSERVATION_TYPE_LABELS[t].ar : OBSERVATION_TYPE_LABELS[t].en}
                      </option>
                    ))}
                  </select>
                  <select className="form-select text-sm"
                    value={obsSev}
                    onChange={e => setObsSev(e.target.value as ObservationSeverity)}>
                    {SEVERITY_ORDER.map(s => (
                      <option key={s} value={s}>
                        {isAr ? SEVERITY_LABELS[s].ar : SEVERITY_LABELS[s].en}
                      </option>
                    ))}
                  </select>
                </div>
                <textarea className="form-textarea w-full text-sm" rows={3}
                  placeholder={isAr ? 'نصّ الملاحظة (إنجليزي)' : 'Observation text (English)'}
                  value={obsEn} onChange={e => setObsEn(e.target.value)} />
                <textarea className="form-textarea w-full text-sm" rows={3} dir="rtl"
                  placeholder="نصّ الملاحظة (عربي)"
                  value={obsAr} onChange={e => setObsAr(e.target.value)} />
                <textarea className="form-textarea w-full text-sm" rows={2}
                  placeholder={isAr ? 'التوصية (اختياري — إنجليزي)' : 'Recommendation (optional — English)'}
                  value={recEn} onChange={e => setRecEn(e.target.value)} />
                <textarea className="form-textarea w-full text-sm" rows={2} dir="rtl"
                  placeholder="التوصية (اختياري — عربي)"
                  value={recAr} onChange={e => setRecAr(e.target.value)} />
                <select className="form-select w-full text-sm"
                  value={linkInject ?? ''}
                  onChange={e => setLinkInject(e.target.value ? Number(e.target.value) : null)}>
                  <option value="">{isAr ? '— ملاحظة عامة (غير مرتبطة بإدخال) —' : '— General (not linked to inject) —'}</option>
                  {injects.map(i => (
                    <option key={i.id} value={i.id}>
                      {fmtOffset(i.plannedTimeOffsetMinutes, isAr)}
                      {' · '}
                      {isAr ? INJECT_TYPE_LABELS[i.injectType].ar : INJECT_TYPE_LABELS[i.injectType].en}
                      {' · '}
                      {(isAr && i.injectTextAr ? i.injectTextAr : i.injectTextEn).slice(0, 60)}
                    </option>
                  ))}
                </select>
                <label className="flex items-center gap-2 text-xs text-tx-muted cursor-pointer">
                  <input type="checkbox" checked={flagOnCreate}
                    onChange={e => setFlagOnCreate(e.target.checked)} />
                  {isAr ? 'وضع علامة للتقرير' : 'Flag for AAR'}
                </label>
                <Button variant="primary" Icon={Send}
                  disabled={busy} onClick={onSubmit}>
                  {isAr ? 'تسجيل الملاحظة' : 'Record observation'}
                </Button>
              </div>
            </Card>
          )}

          {/* Filter rail */}
          <Card>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-tx-strong flex items-center gap-2">
                <ListChecks size={16} />
                {isAr ? 'كل الملاحظات' : 'All observations'}
                <Badge tone="neutral">
                  {isAr ? toArabicDigits(filteredObservations.length) : filteredObservations.length}
                </Badge>
              </h3>
              {myUserId != null && (
                <label className="flex items-center gap-1.5 text-xs text-tx-muted cursor-pointer">
                  <input type="checkbox" checked={showMineOnly}
                    onChange={e => setShowMineOnly(e.target.checked)} />
                  {isAr ? 'ملاحظاتي فقط' : 'Mine only'}
                </label>
              )}
            </div>
            <div className="flex flex-wrap gap-1.5 mb-3">
              <button type="button"
                onClick={() => setTypeFilter('ALL')}
                className={`text-xs px-2 py-0.5 rounded-full border ${
                  typeFilter === 'ALL'
                    ? 'bg-primary-50 border-primary-300 text-primary-700'
                    : 'border-bd-soft text-tx-muted hover:bg-bg-soft'}`}>
                {isAr ? 'الكل' : 'All'}
              </button>
              {OBSERVATION_TYPE_ORDER.map(t => (
                <button key={t} type="button"
                  onClick={() => setTypeFilter(t)}
                  className={`text-xs px-2 py-0.5 rounded-full border ${
                    typeFilter === t
                      ? 'bg-primary-50 border-primary-300 text-primary-700'
                      : 'border-bd-soft text-tx-muted hover:bg-bg-soft'}`}>
                  {isAr ? OBSERVATION_TYPE_LABELS[t].ar : OBSERVATION_TYPE_LABELS[t].en}
                </button>
              ))}
            </div>

            {filteredObservations.length === 0 ? (
              <InlineEmpty text={isAr
                ? 'لا توجد ملاحظات بهذا المرشّح.'
                : 'No observations match this filter.'} />
            ) : (
              <div className="space-y-2 max-h-[calc(100vh-460px)] overflow-y-auto pr-1">
                {filteredObservations.map(o => (
                  <ObservationCard
                    key={o.id}
                    o={o}
                    isAr={isAr}
                    isMine={o.observerUserId === myUserId}
                    busy={busy}
                    onToggleFlag={() => onToggleFlag(o)} />
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
    </Shell>
  );
}

// ─── Sub-components ───────────────────────────────────────────────────

function ReadOnlyInjectCard({
  inj, isActive, isAr, onSelect,
}: {
  inj: InjectView;
  isActive: boolean;
  isAr: boolean;
  onSelect: () => void;
}) {
  const typeLabel = isAr ? INJECT_TYPE_LABELS[inj.injectType].ar : INJECT_TYPE_LABELS[inj.injectType].en;
  const statusLabel = isAr ? INJECT_STATUS_LABELS[inj.status].ar : INJECT_STATUS_LABELS[inj.status].en;
  const tone: BadgeTone =
    inj.status === 'PENDING'      ? 'neutral' :
    inj.status === 'DISPATCHED'   ? 'warning' :
    inj.status === 'ACKNOWLEDGED' ? 'info'    :
    inj.status === 'COMPLETED'    ? 'success' : 'neutral';
  const txt = isAr && inj.injectTextAr ? inj.injectTextAr : inj.injectTextEn;

  return (
    <div style={isActive ? { outline: '2px solid var(--primary)', borderRadius: 8 } : undefined}>
      <Card>
        <div onClick={onSelect} className="cursor-pointer space-y-2">
          <div className="flex items-center gap-2 flex-wrap">
            <Badge tone={tone}>{statusLabel}</Badge>
            <Badge tone="accent">{typeLabel}</Badge>
            <span className="text-xs text-tx-muted ml-auto">
              <Clock size={12} className="inline mr-0.5" />
              {fmtOffset(inj.plannedTimeOffsetMinutes, isAr)}
            </span>
          </div>
          <div className="text-sm text-tx-strong line-clamp-2" dir={isAr ? 'rtl' : 'ltr'}>
            {txt}
          </div>
          {inj.responseCount > 0 && (
            <div className="text-xs text-tx-muted">
              {isAr
                ? `${toArabicDigits(inj.responseCount)} رد`
                : `${inj.responseCount} response${inj.responseCount === 1 ? '' : 's'}`}
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}

function ReadOnlyActiveInjectCard({ inj, isAr }: { inj: InjectView; isAr: boolean }) {
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

function ReadOnlyResponseCard({ r, isAr }: { r: ResponseView; isAr: boolean }) {
  const responseTxt = isAr && r.responseTextAr ? r.responseTextAr : r.responseTextEn;
  const decision    = isAr && r.decisionMadeAr ? r.decisionMadeAr : r.decisionMadeEn;
  const secs        = r.responseTimeSeconds ?? 0;
  const fast        = secs < 60;
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

function ObservationCard({
  o, isAr, isMine, busy, onToggleFlag,
}: {
  o: ObservationView;
  isAr: boolean;
  isMine: boolean;
  busy: boolean;
  onToggleFlag: () => void;
}) {
  const txt = isAr && o.observationTextAr ? o.observationTextAr : o.observationTextEn;
  const rec = isAr && o.recommendationAr ? o.recommendationAr : o.recommendationEn;
  const tone = observationTypeBadgeTone(o.observationType);
  const sevTone = severityBadgeTone(o.severity);
  return (
    <div
      style={isMine ? {
        borderInlineStart: '3px solid var(--primary)',
        paddingInlineStart: 2,
      } : undefined}>
      <Card>
        <div className="flex items-center gap-2 flex-wrap mb-1.5">
          <Badge tone={tone}>{isAr ? OBSERVATION_TYPE_LABELS[o.observationType].ar
                                  : OBSERVATION_TYPE_LABELS[o.observationType].en}</Badge>
          <Badge tone={sevTone}>{isAr ? SEVERITY_LABELS[o.severity].ar
                                       : SEVERITY_LABELS[o.severity].en}</Badge>
          {o.isFlaggedForAar && (
            <Badge tone="info" dot>
              <Flag size={10} className="inline" />
              {' '}
              {isAr ? 'في التقرير' : 'in AAR'}
            </Badge>
          )}
          <span className="text-xs text-tx-muted ml-auto">
            T+{isAr ? toArabicDigits(o.timeOffsetMinutes) : o.timeOffsetMinutes}m
          </span>
        </div>
        <div className="text-sm text-tx-strong" dir={isAr ? 'rtl' : 'ltr'}>
          {txt}
        </div>
        {rec && (
          <div className="mt-2 text-xs">
            <span className="font-semibold text-tx-muted">
              {isAr ? 'التوصية: ' : 'Rec: '}
            </span>
            <span className="text-tx-strong" dir={isAr ? 'rtl' : 'ltr'}>{rec}</span>
          </div>
        )}
        <div className="flex items-center justify-between mt-2 pt-2 border-t border-bd-soft">
          <span className="text-xs text-tx-muted">
            {isAr ? 'مراقب' : 'observer'}
            {' '}
            #{isAr ? toArabicDigits(o.observerUserId) : o.observerUserId}
          </span>
          {isMine && (
            <Button size="sm"
              variant={o.isFlaggedForAar ? 'secondary' : 'primary'}
              Icon={Flag}
              disabled={busy}
              onClick={onToggleFlag}>
              {o.isFlaggedForAar
                ? (isAr ? 'إزالة العلامة' : 'Unflag')
                : (isAr ? 'وضع علامة للتقرير' : 'Flag for AAR')}
            </Button>
          )}
        </div>
      </Card>
    </div>
  );
}
