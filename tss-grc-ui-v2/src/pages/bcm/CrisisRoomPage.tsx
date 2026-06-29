import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Siren, AlertTriangle, MessageSquare, Gavel, ShieldAlert, Plus, ChevronRight, Download, Pencil } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listCrisisEvents, listActiveCrisisEvents, crisisEventStats,
  getCrisisEvent, createCrisisEvent, patchCrisisEvent, transitionCrisisEvent,
  activateBcp, activateDrp,
  listSitReps, addSitRep,
  listDecisions, addDecision,
  listRegulatoryNotifications, logRegulatoryNotification, ackRegulatoryNotification,
  downloadCrisisEventPdf,
  SEVERITY_LABELS, severityTone, statusTone,
  type CrisisEvent, type CrisisStatus, type CrisisSituationReport, type CrisisDecision,
  type CrisisRegulatoryNotification, type Regulator, type TriggerSource,
} from '../../lib/crisisEvents';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';

/**
 * Crisis Room — independent audit Tier-1 fix.
 *
 * <p>Two-pane workspace:
 * <ul>
 *   <li><b>Left pane</b> — KPIs (active count, high-severity count) and the
 *       list of all events. Click an event to load it on the right.</li>
 *   <li><b>Right pane</b> — selected event detail with three timeline streams
 *       (SitReps · Decisions · Regulatory notifications) plus the state-machine
 *       buttons and quick-actions (Activate BCP · Activate DRP).</li>
 * </ul>
 *
 * <p>The page enforces (visually) the closure rule on the backend: the CLOSED
 * transition is disabled until root cause + lessons-learned are populated.
 *
 * <p>"Activate BCP / DRP" enter the BCP/DRP ID as a number — once the BCP/DRP
 * picker components are unified across the platform this will become a real
 * combobox. For now the audit's recommendation is to expose the linkage; the
 * picker UX is a separate sprint.
 */
const TRIGGERS: Array<{ value: string; en: string; ar: string }> = [
  { value: 'MANUAL',         en: 'Manual entry',     ar: 'إدخال يدوي' },
  { value: 'INCIDENT',       en: 'Incident',         ar: 'حادث' },
  { value: 'EXERCISE',       en: 'Exercise',         ar: 'تمرين' },
  { value: 'THREAT_INTEL',   en: 'Threat intel',     ar: 'مخابرات تهديدات' },
  { value: 'SUPPLIER',       en: 'Supplier event',   ar: 'حدث مورّد' },
  { value: 'CYBER',          en: 'Cyber',            ar: 'سيبراني' },
  { value: 'EXTERNAL_ALERT', en: 'External alert',   ar: 'تنبيه خارجي' },
];

const REGULATORS: Array<{ value: Regulator; en: string; ar: string }> = [
  { value: 'NCEMA',         en: 'NCEMA',          ar: 'المركز الوطني للأزمات' },
  { value: 'SAMA',          en: 'SAMA',           ar: 'البنك المركزي' },
  { value: 'NCA',           en: 'NCA',            ar: 'هيئة الأمن السيبراني' },
  { value: 'CMA',           en: 'CMA',            ar: 'هيئة السوق المالية' },
  { value: 'PDPL',          en: 'PDPL (SDAIA)',   ar: 'سدايا — حماية البيانات' },
  { value: 'CIVIL_DEFENCE', en: 'Civil Defence',  ar: 'الدفاع المدني' },
  { value: 'MOH',           en: 'Ministry of Health',    ar: 'وزارة الصحة' },
  { value: 'MOE',           en: 'Ministry of Education', ar: 'وزارة التعليم' },
  { value: 'OTHER',         en: 'Other',          ar: 'أخرى' },
];

const NEXT_STATUSES: Record<CrisisStatus, CrisisStatus[]> = {
  DETECTED:  ['ASSESSING', 'ACTIVATED', 'CANCELLED'],
  ASSESSING: ['ACTIVATED', 'CLOSED', 'CANCELLED'],
  ACTIVATED: ['CONTAINED', 'RECOVERED'],
  CONTAINED: ['RECOVERED', 'ACTIVATED'],
  RECOVERED: ['CLOSED'],
  CLOSED:    [],
  CANCELLED: [],
};

export function CrisisRoomPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [events, setEvents]   = useState<CrisisEvent[]>([]);
  const [active, setActive]   = useState<CrisisEvent[]>([]);
  const [stats, setStats]     = useState<Record<string, number>>({});
  const [selected, setSelected] = useState<CrisisEvent | null>(null);
  const [sitreps, setSitreps] = useState<CrisisSituationReport[]>([]);
  const [decisions, setDecisions] = useState<CrisisDecision[]>([]);
  const [regNotifs, setRegNotifs] = useState<CrisisRegulatoryNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [editOpen, setEditOpen] = useState(false);

  // Form state for the "new event" modal
  const [newEvt, setNewEvt] = useState<Partial<CrisisEvent>>(emptyEvent());

  async function reload() {
    setLoading(true);
    // Per-call .catch() — list, active subset and stats failing independently
    // shouldn't blank the whole crisis room. Stale stats are better than no UI.
    const [page, act, st] = await Promise.all([
      listCrisisEvents(0, 100).catch(() => ({ content: [] as CrisisEvent[], totalElements: 0 })),
      listActiveCrisisEvents().catch(() => [] as CrisisEvent[]),
      crisisEventStats().catch(() => ({} as Record<string, number>)),
    ]);
    setEvents(page.content);
    setActive(act);
    setStats(st);
    if (!selected && page.content.length) {
      await loadDetail(page.content[0].id);
    }
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function loadDetail(id: number) {
    // Per-call .catch() — sub-resources (sitreps, decisions, notifications)
    // may not exist for a brand-new crisis event; treat each as empty if it
    // fails so the rest of the detail still renders.
    const [e, sr, dec, rn] = await Promise.all([
      getCrisisEvent(id).catch(() => null as CrisisEvent | null),
      listSitReps(id).catch(() => [] as CrisisSituationReport[]),
      listDecisions(id).catch(() => [] as CrisisDecision[]),
      listRegulatoryNotifications(id).catch(() => [] as CrisisRegulatoryNotification[]),
    ]);
    if (e) setSelected(e);
    setSitreps(sr);
    setDecisions(dec);
    setRegNotifs(rn);
  }

  // ── Edit the crisis-event record (PATCH — partial null-means-keep) ─────
  // Status is owned by transition(); the PATCH body must echo the current
  // status because the endpoint binds a JPA entity whose status field
  // defaults to "DETECTED" — an omitted status would otherwise trip the
  // server's "Use transition() to change status" guard. The server also
  // refuses edits on CLOSED / CANCELLED events, so the affordance is gated.
  const editFields: FieldDef[] = [
    { key: 'titleEn', kind: 'text', required: true, span: 6, label: isAr ? 'العنوان (إنجليزي)' : 'Title (EN)' },
    { key: 'titleAr', kind: 'text', span: 6, label: isAr ? 'العنوان (عربي)' : 'Title (AR)' },
    { key: 'description', kind: 'textarea', span: 12, rows: 2, label: isAr ? 'الوصف' : 'Description' },
    { key: 'severity', kind: 'select', required: true, span: 6, label: isAr ? 'الشدة' : 'Severity',
      options: [1, 2, 3, 4, 5].map(n => ({ value: String(n), label: `${n} — ${isAr ? SEVERITY_LABELS[n].ar : SEVERITY_LABELS[n].en}` })) },
    { key: 'triggerSource', kind: 'select', span: 6, label: isAr ? 'المصدر' : 'Trigger',
      options: TRIGGERS.map(t => ({ value: t.value, label: isAr ? t.ar : t.en })) },
    { key: 'rootCause', kind: 'textarea', span: 12, rows: 2, label: isAr ? 'السبب الجذري' : 'Root cause' },
  ];
  const editInitial = useMemo(() => selected ? {
    titleEn: selected.titleEn,
    titleAr: selected.titleAr ?? '',
    description: selected.description ?? '',
    severity: String(selected.severity),
    triggerSource: selected.triggerSource ?? '',
    rootCause: selected.rootCause ?? '',
  } : {}, [selected]);
  async function onPatchEvent(values: Record<string, unknown>) {
    if (!selected) return;
    try {
      const updated = await patchCrisisEvent(selected.id, {
        titleEn: String(values.titleEn ?? '').trim(),
        titleAr: String(values.titleAr ?? '').trim() || undefined,
        description: String(values.description ?? '').trim() || undefined,
        severity: Number(values.severity) as 1 | 2 | 3 | 4 | 5,
        triggerSource: (String(values.triggerSource ?? '').trim() || undefined) as TriggerSource | undefined,
        rootCause: String(values.rootCause ?? '').trim() || undefined,
        status: selected.status, // echo — see comment above
      });
      setSelected(updated);
      setEditOpen(false);
      toast.success(isAr ? 'تم تحديث الحدث' : 'Event updated');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل تحديث الحدث' : 'Failed to update event'));
    }
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    if (!newEvt.titleEn || !newEvt.severity) {
      toast.warning(isAr ? 'العنوان والشدة مطلوبة' : 'Title and severity are required');
      return;
    }
    try {
      const created = await createCrisisEvent(newEvt);
      toast.success(isAr ? `تم تسجيل الأزمة ${created.eventCode}` : `Event ${created.eventCode} created`);
      setShowCreate(false);
      setNewEvt(emptyEvent());
      await reload();
      await loadDetail(created.id);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التسجيل' : 'Create failed'));
    }
  }

  async function moveStatus(to: CrisisStatus) {
    if (!selected) return;
    try {
      const out = await transitionCrisisEvent(selected.id, to);
      toast.success(isAr ? `الحالة → ${to}` : `Status → ${to}`);
      setSelected(out);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التحديث' : 'Transition failed'));
    }
  }

  async function handleActivateBcp() {
    if (!selected) return;
    const raw = window.prompt(isAr ? 'رقم خطة BCP لتفعيلها' : 'BCP id to activate');
    if (!raw) return;
    const id = Number(raw);
    if (!Number.isFinite(id) || id <= 0) {
      toast.warning(isAr ? 'رقم غير صالح' : 'Invalid id');
      return;
    }
    try {
      const out = await activateBcp(selected.id, id);
      setSelected(out);
      toast.success(isAr ? `تم تفعيل BCP #${id}` : `BCP #${id} activated`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function handleActivateDrp() {
    if (!selected) return;
    const raw = window.prompt(isAr ? 'رقم خطة DRP لتفعيلها' : 'DRP id to activate');
    if (!raw) return;
    const id = Number(raw);
    if (!Number.isFinite(id) || id <= 0) {
      toast.warning(isAr ? 'رقم غير صالح' : 'Invalid id');
      return;
    }
    try {
      const out = await activateDrp(selected.id, id);
      setSelected(out);
      toast.success(isAr ? `تم تفعيل DRP #${id}` : `DRP #${id} activated`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function handleAddSitRep(summary: string, sev: string, statusUpd: string) {
    if (!selected || !summary.trim()) return;
    try {
      await addSitRep(selected.id, {
        summary: summary.trim(),
        severityChange: sev ? Number(sev) as 1 | 2 | 3 | 4 | 5 : undefined,
        statusUpdate: (statusUpd || undefined) as CrisisSituationReport['statusUpdate'],
      });
      toast.success(isAr ? 'تم تسجيل التقرير' : 'SitRep logged');
      await loadDetail(selected.id);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function handleAddDecision(text: string, rationale: string, action: string) {
    if (!selected || !text.trim()) return;
    try {
      await addDecision(selected.id, {
        decisionText: text.trim(),
        rationale: rationale.trim() || undefined,
        actionRequired: action.trim() || undefined,
      });
      toast.success(isAr ? 'تم تسجيل القرار' : 'Decision logged');
      await loadDetail(selected.id);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function handleLogRegNotif(regulator: Regulator, method: string, reference: string, summary: string) {
    if (!selected) return;
    try {
      await logRegulatoryNotification(selected.id, {
        regulator,
        notificationMethod: (method || undefined) as CrisisRegulatoryNotification['notificationMethod'],
        referenceNumber: reference.trim() || undefined,
        bodySummary: summary.trim() || undefined,
      });
      toast.success(isAr ? `تم تسجيل إخطار ${regulator}` : `${regulator} notification logged`);
      await loadDetail(selected.id);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function handleAck(notifId: number) {
    if (!selected) return;
    const ref = window.prompt(isAr ? 'الرقم المرجعي للإقرار' : 'Acknowledgement reference') ?? '';
    try {
      await ackRegulatoryNotification(selected.id, notifId, ref);
      toast.success(isAr ? 'تم تسجيل الإقرار' : 'Ack recorded');
      await loadDetail(selected.id);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  // `selected && ...` could yield null when selected is null; coerce to
  // a plain boolean so consumers (Button.disabled) don't get a null prop.
  const closureBlocked: boolean = !!(selected
    && (selected.status === 'RECOVERED' || selected.status === 'ASSESSING')
    && (!selected.rootCause || !selected.lessonsLearnedId));

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'غرفة الأزمات' : 'Crisis Room'}
        sub={isAr
          ? 'تسجيل وتفعيل الأزمات وإدارة تقارير الموقف والقرارات والإخطارات التنظيمية في خط زمني واحد.'
          : 'Log and activate crisis events with SitReps, decisions, and regulator notifications on a single timeline.'}
        eyebrow={isAr ? 'إدارة الاستمرارية' : 'Business Continuity'}
        actions={<Button variant="primary" onClick={() => setShowCreate(true)}>
          <Plus size={14} /> {isAr ? 'تسجيل أزمة' : 'New event'}
        </Button>}
      />

      <div className="kpi-grid-sm">
        <Kpi Icon={Siren}        label={isAr ? 'أزمات نشطة'    : 'Active events'}       value={stats.active ?? 0}        tone="danger"   to="/bcm/crisis-room" />
        <Kpi Icon={ShieldAlert}  label={isAr ? 'شدة عالية'     : 'High severity (≥4)'}  value={stats.high_severity ?? 0} tone="danger"   to="/bcm/crisis-room" />
        <Kpi Icon={AlertTriangle} label={isAr ? 'قيد التقييم'  : 'Assessing'}           value={stats.assessing ?? 0}     tone="warning"  to="/bcm/crisis-room" />
        <Kpi Icon={MessageSquare} label={isAr ? 'مغلقة'        : 'Closed'}              value={stats.closed ?? 0}        tone="success"  to="/bcm/crisis-room" />
      </div>

      {active.length > 0 && (
        <div style={{
          background: 'var(--danger-bg, #fbeaea)',
          border: '1px solid var(--danger, #c8362c)',
          borderRadius: 6, padding: '10px 14px', marginBottom: 12,
          display: 'flex', alignItems: 'center', gap: 8, fontSize: 13,
        }}>
          <Siren size={16} color="var(--danger, #c8362c)" />
          <strong>{isAr ? 'أزمات نشطة' : 'Active crises'}: {active.length}</strong>
          <span className="text-muted">
            {active.slice(0, 3).map(e => `${e.eventCode} (sev ${e.severity})`).join(' · ')}
          </span>
        </div>
      )}

      <div className="split-pane">
        {/* ── Left: event list ── */}
        <Card title={isAr ? 'الأزمات' : 'Events'}>
          {loading && (
            <div className="text-muted text-xs mb-sm">
              {isAr ? 'جارٍ التحميل…' : 'Loading…'}
            </div>
          )}
          {events.length === 0 ? (
            <InlineEmpty text={isAr ? 'لا توجد أزمات بعد' : 'No events yet'} />
          ) : (
            <ul className="event-list">
              {events.map(e => (
                <li key={e.id}>
                  <button
                    onClick={() => void loadDetail(e.id)}
                    className={`event-list-item${selected?.id === e.id ? ' is-selected' : ''}`}
                    style={{
                      width: '100%', textAlign: 'start',
                      display: 'grid', gap: 4,
                    }}>
                    <div className="row-between">
                      <span className="mono text-xs">{e.eventCode}</span>
                      <Badge tone={severityTone(e.severity)}>sev {e.severity}</Badge>
                    </div>
                    <div className="text-sm">{e.titleEn}</div>
                    <Badge tone={statusTone(e.status)}>{e.status}</Badge>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* ── Right: selected event detail ── */}
        <div className="stack">
          {!selected ? (
            <Card title={isAr ? 'تفاصيل الأزمة' : 'Event details'}>
              <InlineEmpty text={isAr ? 'اختر أزمة من القائمة' : 'Pick an event from the list'} />
            </Card>
          ) : (
            <>
              {/* Header card */}
              <Card title={`${selected.eventCode} — ${selected.titleEn}`}
                    action={
                      <div style={{ display: 'flex', gap: 8 }}>
                        {selected.status !== 'CLOSED' && selected.status !== 'CANCELLED' && (
                          <Button variant="ghost" onClick={() => setEditOpen(true)}>
                            <Pencil size={14} /> {isAr ? 'تعديل' : 'Edit'}
                          </Button>
                        )}
                        <Button variant="ghost" onClick={() => void downloadCrisisEventPdf(selected.id, selected.eventCode)}>
                          <Download size={14} /> {isAr ? 'تقرير PDF' : 'After-action PDF'}
                        </Button>
                      </div>
                    }>
                <div className="kpi-grid-xs text-sm">
                  <div><span className="text-muted">{isAr ? 'الشدة' : 'Severity'}: </span>
                    <Badge tone={severityTone(selected.severity)}>
                      {selected.severity} — {isAr ? SEVERITY_LABELS[selected.severity]?.ar : SEVERITY_LABELS[selected.severity]?.en}
                    </Badge>
                  </div>
                  <div><span className="text-muted">{isAr ? 'الحالة' : 'Status'}: </span>
                    <Badge tone={statusTone(selected.status)}>{selected.status}</Badge>
                  </div>
                  <div><span className="text-muted">{isAr ? 'المصدر' : 'Trigger'}: </span>{selected.triggerSource ?? '—'}</div>
                  <div><span className="text-muted">{isAr ? 'الاكتشاف' : 'Detected'}: </span>
                    <span className="mono">{selected.detectionTime?.slice(0, 16) ?? '—'}</span>
                  </div>
                  <div><span className="text-muted">{isAr ? 'التفعيل' : 'Activated'}: </span>
                    <span className="mono">{selected.activationTime?.slice(0, 16) ?? '—'}</span>
                  </div>
                  <div><span className="text-muted">{isAr ? 'الإغلاق' : 'Closed'}: </span>
                    <span className="mono">{selected.closureTime?.slice(0, 16) ?? '—'}</span>
                  </div>
                </div>

                <div className="row mt-md">
                  {selected.personalDataAffected && <Badge tone="danger">{isAr ? 'بيانات شخصية متأثرة' : 'Personal data affected'}</Badge>}
                  {selected.cyberRelated && <Badge tone="danger">{isAr ? 'مرتبط بالأمن السيبراني' : 'Cyber-related'}</Badge>}
                  {selected.supplierInvolved && <Badge tone="warning">{isAr ? 'تورّط مورّد' : 'Supplier involved'}</Badge>}
                </div>

                {selected.description && (
                  <div className="mt-md text-md text-relaxed">{selected.description}</div>
                )}

                <div className="row mt-lg" style={{ alignItems: 'center' }}>
                  <span className="text-muted text-xs">{isAr ? 'الانتقال' : 'Transition'}:</span>
                  {NEXT_STATUSES[selected.status].map(s => {
                    const blocked = s === 'CLOSED' && closureBlocked;
                    return (
                      <Button key={s} variant="ghost" disabled={blocked} onClick={() => void moveStatus(s)}>
                        <ChevronRight size={12} /> {s}
                      </Button>
                    );
                  })}
                  {closureBlocked && (
                    <span className="text-muted text-xs">
                      {isAr
                        ? 'لا يمكن الإغلاق قبل تسجيل السبب الجذري وربط درس مستفاد'
                        : 'Cannot close until root cause + lessons-learned are set'}
                    </span>
                  )}
                </div>

                <div className="row mt-sm">
                  <Button variant="secondary" onClick={handleActivateBcp}>
                    {isAr ? 'تفعيل BCP' : 'Activate BCP'}
                  </Button>
                  <Button variant="secondary" onClick={handleActivateDrp}>
                    {isAr ? 'تفعيل DRP' : 'Activate DRP'}
                  </Button>
                </div>

                {(selected.relatedBcpIdsJson || selected.relatedDrpIdsJson) && (
                  <div className="text-muted text-xs mt-md">
                    {selected.relatedBcpIdsJson && <>BCPs: <span className="mono">{selected.relatedBcpIdsJson}</span> · </>}
                    {selected.relatedDrpIdsJson && <>DRPs: <span className="mono">{selected.relatedDrpIdsJson}</span></>}
                  </div>
                )}
              </Card>

              {/* B-C: crisis evidence (decision logs, comms, regulator filings) via generic AttachmentService */}
              <Card title={isAr ? 'الأدلة والوثائق' : 'Evidence & documents'}>
                <AttachmentsField entityType="CRISIS_EVENT" entityId={selected.id} />
              </Card>

              {/* SitReps */}
              <Card title={isAr ? 'تقارير الموقف (SitReps)' : 'Situation reports (SitReps)'}>
                <SitRepForm onSubmit={handleAddSitRep} isAr={isAr} />
                {sitreps.length === 0 ? (
                  <InlineEmpty text={isAr ? 'لم يُسجَّل أي تقرير بعد' : 'No SitReps yet'} />
                ) : (
                  <ul className="timeline-list">
                    {sitreps.map(s => (
                      <li key={s.id} className="timeline-item">
                        <div className="timeline-item-header">
                          <span className="mono">{s.reportedAt.slice(0, 16)}</span>
                          <div className="row-gap-sm">
                            {s.statusUpdate && <Badge tone="info">{s.statusUpdate}</Badge>}
                            {s.severityChange && <Badge tone={severityTone(s.severityChange)}>sev → {s.severityChange}</Badge>}
                          </div>
                        </div>
                        <div className="timeline-item-body">{s.summary}</div>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>

              {/* Decisions */}
              <Card title={isAr ? 'سجل القرارات' : 'Decisions log'}>
                <DecisionForm onSubmit={handleAddDecision} isAr={isAr} />
                {decisions.length === 0 ? (
                  <InlineEmpty text={isAr ? 'لم تُسجَّل قرارات' : 'No decisions yet'} />
                ) : (
                  <ul className="timeline-list">
                    {decisions.map(d => (
                      <li key={d.id} className="timeline-item">
                        <div className="timeline-item-header">
                          <span><Gavel size={11} /> <span className="mono">{d.decidedAt.slice(0, 16)}</span></span>
                          <span className="text-muted">{d.decidedByName ?? `user#${d.decidedBy ?? '?'}`}</span>
                        </div>
                        <div className="timeline-item-body text-bold">{d.decisionText}</div>
                        {d.rationale       && <div className="text-muted timeline-item-meta">{isAr ? 'المبرر' : 'Rationale'}: {d.rationale}</div>}
                        {d.actionRequired  && <div className="timeline-item-meta"><Badge tone="warning">{isAr ? 'إجراء مطلوب' : 'Action'}</Badge> {d.actionRequired}</div>}
                      </li>
                    ))}
                  </ul>
                )}
              </Card>

              {/* Regulatory notifications */}
              <Card title={isAr ? 'الإخطارات التنظيمية' : 'Regulatory notifications'}>
                <RegNotifForm onSubmit={handleLogRegNotif} isAr={isAr} />
                {regNotifs.length === 0 ? (
                  <InlineEmpty text={isAr ? 'لم تُرسل إخطارات' : 'No notifications yet'} />
                ) : (
                  <ul className="timeline-list">
                    {regNotifs.map(n => (
                      <li key={n.id} className="timeline-item">
                        <div className="row-between text-sm">
                          <div>
                            <Badge tone="info">{n.regulator}</Badge>
                            <span className="mono" style={{ marginInlineStart: 6 }}>{n.notifiedAt.slice(0, 16)}</span>
                            {n.referenceNumber && <span className="text-muted" style={{ marginInlineStart: 6 }}>ref: {n.referenceNumber}</span>}
                          </div>
                          {n.acknowledgementReceived
                            ? <Badge tone="success">{isAr ? 'تم الإقرار' : 'Acknowledged'} {n.acknowledgementReference && `(${n.acknowledgementReference})`}</Badge>
                            : <Button variant="ghost" onClick={() => void handleAck(n.id)}>
                                {isAr ? 'تسجيل الإقرار' : 'Record ack'}
                              </Button>}
                        </div>
                        {n.bodySummary && <div className="mt-sm text-sm">{n.bodySummary}</div>}
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </>
          )}
        </div>
      </div>

      {/* Create modal */}
      {showCreate && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'grid', placeItems: 'center', zIndex: 999,
        }} onClick={() => setShowCreate(false)}>
          <form onClick={e => e.stopPropagation()} onSubmit={handleCreate} style={{
            background: 'var(--surface)', borderRadius: 8, padding: 20, width: 'min(560px, 92vw)',
            display: 'grid', gap: 12,
          }}>
            <h3 style={{ margin: 0 }}>{isAr ? 'تسجيل أزمة جديدة' : 'Log new crisis event'}</h3>
            <Field label={isAr ? 'العنوان (إنجليزي)' : 'Title (EN)'} required>
              <input value={newEvt.titleEn ?? ''} onChange={e => setNewEvt({ ...newEvt, titleEn: e.target.value })}
                     style={inputStyle} required maxLength={500} />
            </Field>
            <Field label={isAr ? 'العنوان (عربي)' : 'Title (AR)'}>
              <input value={newEvt.titleAr ?? ''} onChange={e => setNewEvt({ ...newEvt, titleAr: e.target.value })}
                     style={inputStyle} maxLength={500} />
            </Field>
            <Field label={isAr ? 'الوصف' : 'Description'}>
              <textarea value={newEvt.description ?? ''} onChange={e => setNewEvt({ ...newEvt, description: e.target.value })}
                        style={{ ...inputStyle, minHeight: 70 }} />
            </Field>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <Field label={isAr ? 'الشدة' : 'Severity'} required>
                <select value={newEvt.severity ?? 1} onChange={e => setNewEvt({ ...newEvt, severity: Number(e.target.value) as 1 | 2 | 3 | 4 | 5 })}
                        style={inputStyle}>
                  {[1, 2, 3, 4, 5].map(n => (
                    <option key={n} value={n}>{n} — {tlabel(SEVERITY_LABELS, n, isAr)}</option>
                  ))}
                </select>
              </Field>
              <Field label={isAr ? 'مصدر التفعيل' : 'Trigger source'}>
                <select value={newEvt.triggerSource ?? ''} onChange={e => setNewEvt({ ...newEvt, triggerSource: (e.target.value || undefined) as CrisisEvent['triggerSource'] })}
                        style={inputStyle}>
                  <option value="">—</option>
                  {TRIGGERS.map(t => <option key={t.value} value={t.value}>{isAr ? t.ar : t.en}</option>)}
                </select>
              </Field>
            </div>
            <div style={{ display: 'flex', gap: 14, fontSize: 12 }}>
              <label><input type="checkbox" checked={!!newEvt.personalDataAffected}
                            onChange={e => setNewEvt({ ...newEvt, personalDataAffected: e.target.checked })} />
                {' '}{isAr ? 'بيانات شخصية متأثرة' : 'Personal data affected'}</label>
              <label><input type="checkbox" checked={!!newEvt.cyberRelated}
                            onChange={e => setNewEvt({ ...newEvt, cyberRelated: e.target.checked })} />
                {' '}{isAr ? 'مرتبط بالأمن السيبراني' : 'Cyber-related'}</label>
              <label><input type="checkbox" checked={!!newEvt.supplierInvolved}
                            onChange={e => setNewEvt({ ...newEvt, supplierInvolved: e.target.checked })} />
                {' '}{isAr ? 'تورّط مورّد' : 'Supplier involved'}</label>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
              <Button variant="ghost" type="button" onClick={() => setShowCreate(false)}>
                {isAr ? 'إلغاء' : 'Cancel'}
              </Button>
              <Button variant="primary" type="submit">
                {isAr ? 'تسجيل' : 'Create'}
              </Button>
            </div>
          </form>
        </div>
      )}

      {selected && (
        <FormModal
          open={editOpen}
          title={`${isAr ? 'تعديل الحدث' : 'Edit event'} — ${selected.eventCode}`}
          fields={editFields}
          initial={editInitial}
          onClose={() => setEditOpen(false)}
          onSubmit={onPatchEvent}
          submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        />
      )}
    </Shell>
  );
}

// ─── Inline forms ──────────────────────────────────────────────

function SitRepForm({ onSubmit, isAr }: { onSubmit: (s: string, sev: string, st: string) => void; isAr: boolean }) {
  const [summary, setSummary] = useState('');
  const [sev, setSev] = useState('');
  const [stUpd, setStUpd] = useState('');
  return (
    <form onSubmit={e => { e.preventDefault(); onSubmit(summary, sev, stUpd); setSummary(''); setSev(''); setStUpd(''); }}
      style={{ display: 'grid', gap: 6 }}>
      <textarea value={summary} onChange={e => setSummary(e.target.value)}
                placeholder={isAr ? 'ملخص الوضع الحالي' : 'Current situation summary'}
                style={{ ...inputStyle, minHeight: 60 }} required />
      <div style={{ display: 'flex', gap: 6 }}>
        <select value={stUpd} onChange={e => setStUpd(e.target.value)} style={{ ...inputStyle, flex: 1 }}>
          <option value="">{isAr ? 'حالة عامة' : 'Status update'}</option>
          <option value="WORSENING">{isAr ? 'تتفاقم' : 'Worsening'}</option>
          <option value="STABILISING">{isAr ? 'تستقر' : 'Stabilising'}</option>
          <option value="RECOVERING">{isAr ? 'تتعافى' : 'Recovering'}</option>
          <option value="NORMAL">{isAr ? 'طبيعي' : 'Normal'}</option>
        </select>
        <select value={sev} onChange={e => setSev(e.target.value)} style={{ ...inputStyle, flex: 1 }}>
          <option value="">{isAr ? 'تغيير الشدة' : 'Change severity'}</option>
          {[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>sev {n}</option>)}
        </select>
        <Button variant="primary" type="submit">{isAr ? 'إضافة' : 'Add'}</Button>
      </div>
    </form>
  );
}

function DecisionForm({ onSubmit, isAr }: { onSubmit: (t: string, r: string, a: string) => void; isAr: boolean }) {
  const [text, setText] = useState('');
  const [rationale, setRationale] = useState('');
  const [action, setAction] = useState('');
  return (
    <form onSubmit={e => { e.preventDefault(); onSubmit(text, rationale, action); setText(''); setRationale(''); setAction(''); }}
      style={{ display: 'grid', gap: 6 }}>
      <input value={text} onChange={e => setText(e.target.value)}
             placeholder={isAr ? 'القرار' : 'Decision'} style={inputStyle} required />
      <input value={rationale} onChange={e => setRationale(e.target.value)}
             placeholder={isAr ? 'المبرر' : 'Rationale'} style={inputStyle} />
      <div style={{ display: 'flex', gap: 6 }}>
        <input value={action} onChange={e => setAction(e.target.value)}
               placeholder={isAr ? 'إجراء مطلوب (اختياري)' : 'Action required (optional)'}
               style={{ ...inputStyle, flex: 1 }} />
        <Button variant="primary" type="submit">{isAr ? 'تسجيل' : 'Log'}</Button>
      </div>
    </form>
  );
}

function RegNotifForm({ onSubmit, isAr }: { onSubmit: (r: Regulator, m: string, ref: string, s: string) => void; isAr: boolean }) {
  const [regulator, setRegulator] = useState<Regulator>('NCEMA');
  const [method, setMethod] = useState('');
  const [reference, setReference] = useState('');
  const [summary, setSummary] = useState('');
  return (
    <form onSubmit={e => { e.preventDefault(); onSubmit(regulator, method, reference, summary); setReference(''); setSummary(''); }}
      style={{ display: 'grid', gap: 6 }}>
      <div style={{ display: 'flex', gap: 6 }}>
        <select value={regulator} onChange={e => setRegulator(e.target.value as Regulator)} style={{ ...inputStyle, flex: 1 }}>
          {REGULATORS.map(r => <option key={r.value} value={r.value}>{isAr ? r.ar : r.en}</option>)}
        </select>
        <select value={method} onChange={e => setMethod(e.target.value)} style={{ ...inputStyle, flex: 1 }}>
          <option value="">{isAr ? 'الوسيلة' : 'Method'}</option>
          <option value="EMAIL">Email</option>
          <option value="PORTAL">Portal</option>
          <option value="PHONE">Phone</option>
          <option value="LETTER">Letter</option>
          <option value="API">API</option>
        </select>
      </div>
      <input value={reference} onChange={e => setReference(e.target.value)}
             placeholder={isAr ? 'الرقم المرجعي (اختياري)' : 'Reference number (optional)'} style={inputStyle} />
      <div style={{ display: 'flex', gap: 6 }}>
        <input value={summary} onChange={e => setSummary(e.target.value)}
               placeholder={isAr ? 'ملخص المحتوى' : 'Body summary'}
               style={{ ...inputStyle, flex: 1 }} />
        <Button variant="primary" type="submit">{isAr ? 'تسجيل' : 'Log'}</Button>
      </div>
    </form>
  );
}

// ─── tiny visual helpers ──────────────────────────────────────

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
      <span className="text-muted">{label}{required && ' *'}</span>
      {children}
    </label>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '6px 10px', border: '1px solid var(--border)', borderRadius: 4,
  fontSize: 13, background: 'var(--surface)', width: '100%',
};

function emptyEvent(): Partial<CrisisEvent> {
  return {
    titleEn: '', titleAr: '', description: '',
    severity: 2, triggerSource: 'MANUAL',
    personalDataAffected: false, cyberRelated: false, supplierInvolved: false,
  };
}
