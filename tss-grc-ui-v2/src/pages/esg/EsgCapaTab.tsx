/**
 * F2-16 tranche 3 — the sustainability corrective-action register tab, moved out of `EsgPage.tsx`.
 *
 * One of seven extracted together, each **self-contained by measurement**: zero references to any
 * page-local identifier outside its own range, exactly one call site in the page's tab switch, and
 * nothing declared inside it needed outside.
 *
 * <b>⚠ The boundary rule that got this tab here is not the one tranche 2 used, and the difference is
 * a defect tranche 2's rule would have shipped.</b> That tranche walked up from the declaration over
 * adjacent `//` lines. `ClimateTab` is documented with a `/** … *&#47;` block and `CapaTab`/`AnnexTab`
 * have multi-paragraph banners split by blank lines — so the walk-up would have left their
 * documentation behind on the page, orphaned above an unrelated declaration, compiling cleanly. The
 * rule now is the file's own convention: everything between the previous top-level declaration and
 * this one belongs to this one.
 *
 * <b>Proven, not trusted.</b> The moved range appears here verbatim with one deliberate `export`; the
 * page keeps every line it did not lose, byte for byte; no `function` or banner line of a moved tab
 * survives on the page; and the line count is conserved. `noUnusedLocals` makes `tsc` the checker for
 * the import surgery. Tests: `EsgOperationalTabs.test.tsx`.
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Target,
  Download, Trash2, CheckCircle2, AlertCircle,
  Plus, ArrowRight, Link2, Wrench, Clock,
  Paperclip, Coins, Hourglass, X as XIcon, Pencil,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { Modal } from '../../components/ui/Modal';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listCapas, capaAggregate, createCapa, updateCapa,
  startCapa, completeCapa, verifyCapa, closeCapa, cancelCapa,
  capaStatusTone, capaPriorityTone, capaAllowedNext,
  canComplete, canVerify as canVerifyCapa,
  capaSourceLabel, capaStatusLabel, capaPriorityLabel,
  capaActionTypeLabel, capaActionTypeTone, capaSourceTab, exportCapaXlsx,
  daysRemaining,
  type CorrectiveAction, type CapaAggregate, type CapaStatus,
  type CapaSourceType, type CapaPriority as CapaPrio, type CapaActionType,
} from '../../lib/sustainabilityCapa';
import { getUser } from '../../lib/auth';
import {
  type EsgTab,
} from '../../lib/esgDashboard';
import { formatSarCompact, formatSarFull } from '../../lib/format';

// ─── Corrective Actions tab (V58) ──────────────────────────────────────────
//
// CAPA-like remediation register that closes the loop on gaps surfaced
// by V55 (Climate Risks), V56 (GHG Scope-3 coverage <70%) and V57 (annex
// open_gaps_count). State machine: OPEN → IN_PROGRESS → COMPLETED →
// VERIFIED → CLOSED (any → CANCELLED). Per-row inline buttons mirror the
// allowed transitions; Rule B disables Complete until evidenceRef is set
// and Rule A prompts for a verifier name distinct from the owner.

// CT-6 — onNavigate is the page's setTab: source chips jump to the tab that
// owns the gap (the URL param alone wouldn't re-render the mounted page).
export function CapaTab({ onNavigate }: { onNavigate: (t: EsgTab) => void }) {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<CorrectiveAction[]>([]);
  const [agg, setAgg]     = useState<CapaAggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<CapaStatus | 'ALL'>('ALL');
  // CT-5 — source + priority narrowing, client-side like the status filter
  // (the tab loads the full first page once and filters in memory).
  const [sourceFilter, setSourceFilter]     = useState<CapaSourceType | 'ALL'>('ALL');
  const [priorityFilter, setPriorityFilter] = useState<CapaPrio | 'ALL'>('ALL');
  // CT-3 — CAR/PAR narrowing (older rows without the field count as CORRECTIVE,
  // matching the V297 backfill default).
  const [typeFilter, setTypeFilter] = useState<CapaActionType | 'ALL'>('ALL');

  // CT-4 — verify confirm state. The verifier IS the authenticated principal
  // (the server stamps it and ignores any body), so the modal asks for
  // confirmation instead of a name; Rule A is mirrored against the principal.
  const [verifyingAction, setVerifyingAction] = useState<CorrectiveAction | null>(null);
  // CT-2 — manual CAR/PAR creation modal.
  const [creating, setCreating] = useState(false);
  // CT-1 — edit modal state. Auto-CAPAs are born without evidence/owner/plan;
  // this modal is the only in-product way to fill them (evidenceRef unblocks
  // Rule B, so the lifecycle can actually reach COMPLETED).
  const [editCapa, setEditCapa] = useState<CorrectiveAction | null>(null);

  async function reload() {
    setLoading(true);
    // Per-call .catch() — render whatever loaded; one endpoint failing
    // shouldn't blank the whole CAPA tab.
    const [pg, a] = await Promise.all([
      listCapas({ size: 200 }).catch(() => ({ content: [] as CorrectiveAction[], totalElements: 0 })),
      capaAggregate().catch(() => null as CapaAggregate | null),
    ]);
    setItems(pg.content);
    setAgg(a);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  const filtered = items.filter(it =>
    (statusFilter   === 'ALL' || it.status     === statusFilter) &&
    (sourceFilter   === 'ALL' || it.sourceType === sourceFilter) &&
    (priorityFilter === 'ALL' || it.priority   === priorityFilter) &&
    (typeFilter     === 'ALL' || (it.actionType ?? 'CORRECTIVE') === typeFilter));

  // Only offer source options that actually occur in the register, so the
  // dropdown stays short for tenants using few gap engines.
  const sourceOptions = Array.from(new Set(items.map(it => it.sourceType))).sort();

  async function onStart(id: number)    { try { await startCapa(id);    toast.success(isAr ? 'بدأ التنفيذ' : 'Started');     await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); } }
  async function onComplete(id: number) { try { await completeCapa(id); toast.success(isAr ? 'تم الإكمال — في انتظار التحقّق' : 'Completed — awaiting verification'); await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); } }
  async function onClose_(id: number)   { try { await closeCapa(id);    toast.success(isAr ? 'تم الإغلاق' : 'Closed');         await reload(); } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); } }
  async function onCancel(id: number)   {
    if (!window.confirm(isAr ? 'إلغاء هذا الإجراء التصحيحي؟ هذا الإجراء نهائي.' : 'Cancel this corrective action? This is terminal.')) return;
    try { await cancelCapa(id); toast.info(isAr ? 'تم الإلغاء' : 'Cancelled'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  // CT-4 — the acting verifier is the session principal. Rule A mirrored
  // client-side (name AND email, trim/case-insensitive — same as the server's
  // sameActor) so a blocked self-verify never round-trips for its 400.
  const principal = getUser()?.username ?? '';
  const verifyBlocked = verifyingAction !== null && !canVerifyCapa(verifyingAction, principal);

  async function onConfirmVerify() {
    const a = verifyingAction;
    if (!a || verifyBlocked) return;
    try {
      await verifyCapa(a.id!);
      toast.success(isAr ? 'تم التحقّق' : 'Verified');
      setVerifyingAction(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Action failed');
    }
  }

  // CT-2 — manual creation. Source is fixed MANUAL; both titles are required
  // (bilingual rule) and enforced field-by-field by the FormModal validator.
  async function submitCreate(values: Record<string, unknown>) {
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    try {
      await createCapa({
        sourceType:    'MANUAL',
        actionType:    (str(values.actionType) as CapaActionType) ?? 'CORRECTIVE',
        titleEn:       String(values.titleEn ?? '').trim(),
        titleAr:       String(values.titleAr ?? '').trim(),
        descriptionEn: str(values.descriptionEn),
        descriptionAr: str(values.descriptionAr),
        priority:      (str(values.priority) as CapaPrio) ?? 'MEDIUM',
        ownerName:     str(values.ownerName),
        ownerEmail:    str(values.ownerEmail),
        assignedTo:    str(values.assignedTo),
        targetDate:    str(values.targetDate),
        evidenceRef:   str(values.evidenceRef),
        costEstimateSar: num(values.costEstimateSar),
        effortHours:     num(values.effortHours),
      });
      toast.success(isAr ? 'أُنشئ الإجراء' : 'Action created');
      setCreating(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر إنشاء الإجراء' : 'Failed to create action'));
    }
  }

  const createFields: FieldDef[] = [
    { kind: 'select', key: 'actionType', required: true, span: 4,
      label: isAr ? 'نوع الإجراء' : 'Action type',
      options: (['CORRECTIVE', 'PREVENTIVE'] as const)
        .map(t => ({ value: t, label: capaActionTypeLabel(t, isAr ? 'ar' : 'en') })) },
    { kind: 'select', key: 'priority', required: true, span: 4,
      label: isAr ? 'الأولوية' : 'Priority',
      options: (['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const)
        .map(p => ({ value: p, label: capaPriorityLabel(p, isAr ? 'ar' : 'en') })) },
    { kind: 'date', key: 'targetDate', span: 4,
      label: isAr ? 'تاريخ الاستحقاق' : 'Target date' },
    { kind: 'text', key: 'titleEn', required: true, span: 6, maxLength: 255,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', required: true, span: 6, maxLength: 255,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'textarea', key: 'descriptionEn', span: 6, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 6, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
    { kind: 'text', key: 'ownerName', span: 4,
      label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text', key: 'ownerEmail', span: 4,
      label: isAr ? 'بريد المالك' : 'Owner email' },
    { kind: 'text', key: 'assignedTo', span: 4,
      label: isAr ? 'مُسند إلى' : 'Assigned to' },
    { kind: 'text', key: 'evidenceRef', span: 12,
      label: isAr ? 'مرجع الدليل (اختياري — مطلوب قبل الإكمال، القاعدة B)'
                  : 'Evidence ref (optional — required before Complete, Rule B)' },
    { kind: 'number', key: 'costEstimateSar', span: 6, min: 0,
      label: isAr ? 'الكلفة التقديرية (ر.س)' : 'Cost estimate (SAR)' },
    { kind: 'number', key: 'effortHours', span: 6, min: 0,
      label: isAr ? 'المجهود (ساعات)' : 'Effort (hours)' },
  ];

  // Stable identity — FormModal resets its values when `initial` changes, so a
  // per-render literal would wipe the form on every keystroke.
  const createInitial = useMemo<Record<string, unknown>>(() => ({
    actionType: 'CORRECTIVE', priority: 'MEDIUM', targetDate: '',
    titleEn: '', titleAr: '', descriptionEn: '', descriptionAr: '',
    ownerName: '', ownerEmail: '', assignedTo: '', evidenceRef: '',
    costEstimateSar: '', effortHours: '',
  }), []);

  // CT-1 — full editable definition for a non-terminal action. Lifecycle
  // fields (status, timestamps, verifier) stay owned by the transitions.
  const editFields: FieldDef[] = [
    { kind: 'select', key: 'actionType', required: true, span: 4,
      label: isAr ? 'نوع الإجراء' : 'Action type',
      options: (['CORRECTIVE', 'PREVENTIVE'] as const)
        .map(t => ({ value: t, label: capaActionTypeLabel(t, isAr ? 'ar' : 'en') })) },
    { kind: 'select', key: 'priority', required: true, span: 4,
      label: isAr ? 'الأولوية' : 'Priority',
      options: (['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const)
        .map(p => ({ value: p, label: capaPriorityLabel(p, isAr ? 'ar' : 'en') })) },
    { kind: 'date', key: 'targetDate', span: 4,
      label: isAr ? 'تاريخ الاستحقاق' : 'Target date' },
    { kind: 'text', key: 'assignedTo', span: 4,
      label: isAr ? 'مُسند إلى' : 'Assigned to' },
    { kind: 'text', key: 'ownerName', span: 4,
      label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text', key: 'ownerEmail', span: 4,
      label: isAr ? 'بريد المالك' : 'Owner email' },
    { kind: 'text', key: 'evidenceRef', span: 12,
      label: isAr ? 'مرجع الدليل (مطلوب قبل الإكمال — القاعدة B)'
                  : 'Evidence ref (required before Complete — Rule B)' },
    { kind: 'textarea', key: 'rootCauseEn', span: 6, rows: 2,
      label: isAr ? 'السبب الجذري (إنجليزي)' : 'Root cause (English)' },
    { kind: 'textarea', key: 'rootCauseAr', span: 6, rows: 2,
      label: isAr ? 'السبب الجذري (عربي)' : 'Root cause (Arabic)' },
    { kind: 'textarea', key: 'remediationPlanEn', span: 6, rows: 2,
      label: isAr ? 'خطة المعالجة (إنجليزي)' : 'Remediation plan (English)' },
    { kind: 'textarea', key: 'remediationPlanAr', span: 6, rows: 2,
      label: isAr ? 'خطة المعالجة (عربي)' : 'Remediation plan (Arabic)' },
    { kind: 'number', key: 'costEstimateSar', span: 4,
      label: isAr ? 'الكلفة التقديرية (ر.س)' : 'Cost estimate (SAR)' },
    { kind: 'number', key: 'effortHours', span: 4,
      label: isAr ? 'المجهود (ساعات)' : 'Effort (hours)' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2,
      label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  const editInitial = useMemo<Record<string, unknown>>(() => ({
    actionType:        editCapa?.actionType ?? 'CORRECTIVE',
    priority:          editCapa?.priority ?? 'MEDIUM',
    targetDate:        editCapa?.targetDate ?? '',
    assignedTo:        editCapa?.assignedTo ?? '',
    ownerName:         editCapa?.ownerName ?? '',
    ownerEmail:        editCapa?.ownerEmail ?? '',
    evidenceRef:       editCapa?.evidenceRef ?? '',
    rootCauseEn:       editCapa?.rootCauseEn ?? '',
    rootCauseAr:       editCapa?.rootCauseAr ?? '',
    remediationPlanEn: editCapa?.remediationPlanEn ?? '',
    remediationPlanAr: editCapa?.remediationPlanAr ?? '',
    costEstimateSar:   editCapa?.costEstimateSar != null ? String(editCapa.costEstimateSar) : '',
    effortHours:       editCapa?.effortHours != null ? String(editCapa.effortHours) : '',
    notes:             editCapa?.notes ?? '',
  }), [editCapa]);

  async function submitEdit(values: Record<string, unknown>) {
    if (!editCapa?.id) return;
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    const num = (v: unknown) => {
      const n = Number(v);
      return v === '' || v == null || !Number.isFinite(n) ? undefined : n;
    };
    try {
      await updateCapa(editCapa.id, {
        actionType:        (str(values.actionType) as CapaActionType) ?? 'CORRECTIVE',
        priority:          (str(values.priority) as CapaPrio) ?? 'MEDIUM',
        targetDate:        str(values.targetDate),
        assignedTo:        str(values.assignedTo),
        ownerName:         str(values.ownerName),
        ownerEmail:        str(values.ownerEmail),
        evidenceRef:       str(values.evidenceRef),
        rootCauseEn:       str(values.rootCauseEn),
        rootCauseAr:       str(values.rootCauseAr),
        remediationPlanEn: str(values.remediationPlanEn),
        remediationPlanAr: str(values.remediationPlanAr),
        costEstimateSar:   num(values.costEstimateSar),
        effortHours:       num(values.effortHours),
        notes:             str(values.notes),
      });
      toast.success(isAr ? 'حُدّث الإجراء' : 'Action updated');
      setEditCapa(null);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّر تحديث الإجراء' : 'Failed to update action'));
    }
  }

  return (
    <div className="esg-stack">
      {/* KPI strip — live status counts + overdue alarm */}
      <div className="kpi-grid">
        <Kpi Icon={Wrench}        label={isAr ? 'مفتوحة' : 'Open'}          value={agg?.open ?? 0}        tone="warning" onClick={() => setStatusFilter('OPEN')} active={statusFilter === 'OPEN'} />
        <Kpi Icon={ArrowRight}    label={isAr ? 'قيد التنفيذ' : 'In progress'} value={agg?.inProgress ?? 0} tone="info" onClick={() => setStatusFilter('IN_PROGRESS')} active={statusFilter === 'IN_PROGRESS'} />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'بانتظار التحقّق' : 'Awaiting verification'} value={agg?.completed ?? 0} tone="primary" onClick={() => setStatusFilter('COMPLETED')} active={statusFilter === 'COMPLETED'} />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'مُتحقَّق منها' : 'Verified'} value={agg?.verified ?? 0}   tone="success" onClick={() => setStatusFilter('VERIFIED')} active={statusFilter === 'VERIFIED'} />
        <Kpi Icon={AlertCircle}   label={isAr ? 'متأخّرة' : 'Overdue'}        value={agg?.overdue ?? 0}     tone={(agg?.overdue ?? 0) > 0 ? 'danger' : 'success'} to="/esg" />
        <Kpi Icon={Clock}         label={isAr ? 'مستحقّة قريباً' : 'Due soon'} value={agg?.dueSoon ?? 0}     tone={(agg?.dueSoon ?? 0) > 0 ? 'warning' : 'success'} to="/esg" />
        {/* CT-3 — the corrective:preventive split, once the server reports it. */}
        {agg?.byType && (
          <Kpi Icon={Target} label={isAr ? 'وقائية نشطة' : 'Preventive open'}
            value={agg.byType.PREVENTIVE ?? 0} tone="info"
            onClick={() => setTypeFilter(typeFilter === 'PREVENTIVE' ? 'ALL' : 'PREVENTIVE')}
            active={typeFilter === 'PREVENTIVE'} />
        )}
      </div>

      <Card title={isAr ? 'سجلّ الإجراءات التصحيحية للاستدامة' : 'Sustainability Corrective Actions'}
            Icon={Wrench}
            action={
              <div className="row-actions">
                {/* CT-6 — whole register as XLSX (EXPORT audit row is central). */}
                <Button variant="ghost" size="sm" Icon={Download}
                  onClick={() => exportCapaXlsx(isAr ? 'ar' : 'en').catch(e =>
                    toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر التصدير' : 'Export failed')))}>
                  {isAr ? 'تصدير' : 'Export'}
                </Button>
                {/* CT-2 — manual CAR/PAR creation (source fixed to MANUAL). */}
                <Button variant="primary" size="sm" Icon={Plus} onClick={() => setCreating(true)}>
                  {isAr ? 'إجراء جديد' : 'New action'}
                </Button>
              </div>
            }>
        {/* Status filter chips */}
        <div className="esg-filter-row">
          {(['ALL', 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'VERIFIED', 'CLOSED', 'CANCELLED'] as const).map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              className={`esg-filter-btn${statusFilter === s ? ' is-active' : ''}`}>
              {s === 'ALL' ? (isAr ? 'الكل' : 'All') : capaStatusLabel(s as CapaStatus, isAr ? 'ar' : 'en')}
            </button>
          ))}
          {/* CT-3 — CAR/PAR chip pair (click again to clear) */}
          {(['CORRECTIVE', 'PREVENTIVE'] as const).map(t => (
            <button key={t} onClick={() => setTypeFilter(typeFilter === t ? 'ALL' : t)}
              className={`esg-filter-btn${typeFilter === t ? ' is-active' : ''}`}>
              {capaActionTypeLabel(t, isAr ? 'ar' : 'en')}
            </button>
          ))}
          {/* CT-5 — source + priority narrowing */}
          <select className="input" style={{ width: 'auto' }} value={sourceFilter}
            aria-label={isAr ? 'تصفية حسب المصدر' : 'Filter by source'}
            onChange={e => setSourceFilter(e.target.value as CapaSourceType | 'ALL')}>
            <option value="ALL">{isAr ? 'كل المصادر' : 'All sources'}</option>
            {sourceOptions.map(s => (
              <option key={s} value={s}>{capaSourceLabel(s, isAr ? 'ar' : 'en')}</option>
            ))}
          </select>
          <select className="input" style={{ width: 'auto' }} value={priorityFilter}
            aria-label={isAr ? 'تصفية حسب الأولوية' : 'Filter by priority'}
            onChange={e => setPriorityFilter(e.target.value as CapaPrio | 'ALL')}>
            <option value="ALL">{isAr ? 'كل الأولويات' : 'All priorities'}</option>
            {(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const).map(p => (
              <option key={p} value={p}>{capaPriorityLabel(p, isAr ? 'ar' : 'en')}</option>
            ))}
          </select>
          <span className="text-2xs text-muted esg-filter-row__count">
            {filtered.length} / {items.length}
          </span>
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? (
              <InlineEmpty>
                <div className="esg-card-center">
                  <span>{statusFilter === 'ALL' && sourceFilter === 'ALL' && priorityFilter === 'ALL' && typeFilter === 'ALL'
                    ? (isAr ? 'لا توجد إجراءات تصحيحية بعد' : 'No corrective actions yet')
                    : (isAr ? 'لا توجد إجراءات مطابقة للتصفية' : 'No actions match this filter')}</span>
                  {(statusFilter !== 'ALL' || sourceFilter !== 'ALL' || priorityFilter !== 'ALL' || typeFilter !== 'ALL') && (
                    <Button variant="ghost" size="sm" Icon={XIcon}
                      onClick={() => { setStatusFilter('ALL'); setSourceFilter('ALL'); setPriorityFilter('ALL'); setTypeFilter('ALL'); }}>
                      {isAr ? 'إزالة التصفية' : 'Clear filters'}
                    </Button>
                  )}
                </div>
              </InlineEmpty>
            )
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'العنوان' : 'Title'}</th>
                    <th>{isAr ? 'المصدر' : 'Source'}</th>
                    <th>{isAr ? 'النوع' : 'Type'}</th>
                    <th>{isAr ? 'الأولوية' : 'Priority'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th>{isAr ? 'تاريخ الاستحقاق' : 'Due'}</th>
                    <th>{isAr ? 'الكلفة · المجهود' : 'Cost · Effort'}</th>
                    <th>{isAr ? 'المالك' : 'Owner'}</th>
                    <th className="esg-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(a => {
                    const dr = daysRemaining(a);
                    const overdue = dr !== null && dr < 0
                      && a.status !== 'CLOSED' && a.status !== 'CANCELLED' && a.status !== 'VERIFIED';
                    const dueSoon = dr !== null && dr >= 0 && dr <= 30
                      && a.status !== 'CLOSED' && a.status !== 'CANCELLED' && a.status !== 'VERIFIED';
                    const allowed = capaAllowedNext(a.status);
                    const showCost = a.costEstimateSar != null && a.costEstimateSar > 0;
                    const showEffort = a.effortHours != null && a.effortHours > 0;
                    // CT-6 — the tab owning this gap's source (undefined for MANUAL).
                    const srcTab = capaSourceTab(a.sourceType);
                    return (
                      <tr key={a.id}>
                        <td className="mono text-2xs">{a.actionCode ?? `#${a.id}`}</td>
                        <td>
                          <div className="esg-cell-title">
                            {isAr && a.titleAr ? a.titleAr : a.titleEn}
                          </div>
                          {a.evidenceRef && (
                            <div className="text-2xs text-muted esg-evidence-link">
                              <Paperclip size={11} className="esg-flex-shrink-0" />
                              <span>{isAr ? 'الدليل:' : 'Evidence:'}</span>
                              <span className="mono">{a.evidenceRef}</span>
                            </div>
                          )}
                        </td>
                        <td>
                          {srcTab ? (
                            <button type="button" className="esg-source-jump"
                              title={isAr ? 'فتح تبويب المصدر' : 'Open source tab'}
                              onClick={() => onNavigate(srcTab)}>
                              <Badge tone="neutral">
                                {capaSourceLabel(a.sourceType, isAr ? 'ar' : 'en')}
                                <Link2 size={10} />
                              </Badge>
                            </button>
                          ) : (
                            <Badge tone="neutral">{capaSourceLabel(a.sourceType, isAr ? 'ar' : 'en')}</Badge>
                          )}
                          {a.sourceRef && (
                            <div className="mono text-2xs text-muted esg-cell-sub">{a.sourceRef}</div>
                          )}
                        </td>
                        <td><Badge tone={capaActionTypeTone(a.actionType ?? 'CORRECTIVE')}>{capaActionTypeLabel(a.actionType ?? 'CORRECTIVE', isAr ? 'ar' : 'en')}</Badge></td>
                        <td><Badge tone={capaPriorityTone(a.priority)}>{capaPriorityLabel(a.priority, isAr ? 'ar' : 'en')}</Badge></td>
                        <td><Badge tone={capaStatusTone(a.status)}>{capaStatusLabel(a.status, isAr ? 'ar' : 'en')}</Badge></td>
                        <td className="mono text-2xs">
                          <div className={overdue ? 'esg-date-overdue' : dueSoon ? 'esg-date-due-soon' : ''}>
                            {a.targetDate ?? '—'}
                          </div>
                          {dr !== null && a.status !== 'CLOSED' && a.status !== 'CANCELLED' && (
                            <div className={overdue ? 'esg-date-sub-overdue' : dueSoon ? 'esg-date-sub-due-soon' : 'esg-date-sub-default'}>
                              {overdue
                                ? (isAr ? `متأخّر ${Math.abs(dr)} يوماً` : `${Math.abs(dr)}d overdue`)
                                : (isAr ? `${dr} يوماً متبقّياً` : `${dr}d left`)}
                            </div>
                          )}
                        </td>
                        <td className="text-2xs">
                          {showCost && (
                            <div className="esg-cost-chip"
                                 title={formatSarFull(a.costEstimateSar!, isAr ? 'ar' : 'en')}>
                              <Coins size={11} className="esg-flex-shrink-0" />
                              <span className="mono">{formatSarCompact(a.costEstimateSar!, isAr ? 'ar' : 'en')}</span>
                            </div>
                          )}
                          {showEffort && (
                            <div className={`esg-time-chip${showCost ? ' esg-time-chip--offset' : ''}`}>
                              <Hourglass size={11} className="esg-flex-shrink-0" />
                              <span className="mono">{a.effortHours}{isAr ? ' س' : 'h'}</span>
                            </div>
                          )}
                          {!showCost && !showEffort && <span className="text-muted">—</span>}
                        </td>
                        <td className="text-xs">{a.ownerName ?? '—'}</td>
                        <td className="esg-text-end">
                          <div className="esg-actions-row">
                            {/* CT-1 — edit any non-terminal action (server 409s terminal). */}
                            {a.status !== 'CLOSED' && a.status !== 'CANCELLED' && (
                              <Button variant="ghost" size="sm" Icon={Pencil}
                                title={isAr ? 'تعديل' : 'Edit'}
                                onClick={() => setEditCapa(a)} />
                            )}
                            {allowed.includes('IN_PROGRESS') && (
                              <Button variant="secondary" size="sm" Icon={ArrowRight} onClick={() => onStart(a.id!)}>
                                {isAr ? 'بدء' : 'Start'}
                              </Button>
                            )}
                            {allowed.includes('COMPLETED') && (
                              <Button variant="secondary" size="sm" Icon={CheckCircle2}
                                disabled={!canComplete(a)}
                                title={!canComplete(a) ? (isAr ? 'يجب إضافة دليل (القاعدة B) قبل الإكمال' : 'Evidence required before Complete (Rule B)') : undefined}
                                onClick={() => onComplete(a.id!)}>
                                {isAr ? 'إكمال' : 'Complete'}
                              </Button>
                            )}
                            {allowed.includes('VERIFIED') && (
                              <Button variant="primary" size="sm" Icon={CheckCircle2} onClick={() => setVerifyingAction(a)}>
                                {isAr ? 'تحقّق' : 'Verify'}
                              </Button>
                            )}
                            {allowed.includes('CLOSED') && (
                              <Button variant="primary" size="sm" Icon={CheckCircle2} onClick={() => onClose_(a.id!)}>
                                {isAr ? 'إغلاق' : 'Close'}
                              </Button>
                            )}
                            {allowed.includes('CANCELLED') && (
                              <Button variant="ghost" size="sm" Icon={Trash2}
                                title={isAr ? 'إلغاء' : 'Cancel'}
                                onClick={() => onCancel(a.id!)} />
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
      </Card>

      {/* CT-4 — verify confirm. The server stamps the authenticated principal
          as verifier (it binds no body), so the modal states that instead of
          collecting a name; a blocked self-verify (Rule A) never round-trips. */}
      {verifyingAction !== null && (
        <Modal
          open
          size="md"
          title={`${isAr ? 'تحقّق من الإجراء' : 'Verify action'} · ${verifyingAction.actionCode ?? `#${verifyingAction.id}`}`}
          onClose={() => setVerifyingAction(null)}
          footer={
            <>
              <Button variant="ghost" onClick={() => setVerifyingAction(null)}>
                {isAr ? 'إلغاء' : 'Cancel'}
              </Button>
              <Button variant="primary" Icon={CheckCircle2} disabled={verifyBlocked}
                onClick={() => void onConfirmVerify()}>
                {isAr ? 'تأكيد التحقّق' : 'Confirm verification'}
              </Button>
            </>
          }>
          <p className="text-sm">
            {isAr
              ? <>سيُسجَّل حسابك (<strong className="mono">{principal}</strong>) كجهة التحقّق من هذا الإجراء.</>
              : <>You (<strong className="mono">{principal}</strong>) will be recorded as the verifier of this action.</>}
          </p>
          <p className="text-2xs text-muted">
            {isAr
              ? <>القاعدة A — فصل المهام: يجب أن تختلف جهة التحقّق عن المالك ({verifyingAction.ownerName ?? '—'}).</>
              : <>Rule A — separation of duties: the verifier must differ from the owner ({verifyingAction.ownerName ?? '—'}).</>}
          </p>
          {verifyBlocked && (
            <p className="text-2xs esg-date-overdue">
              {isAr
                ? 'أنت مالك هذا الإجراء — يلزم مستخدم آخر لإتمام التحقّق.'
                : 'You are the owner of this action — a second user must perform the verification.'}
            </p>
          )}
        </Modal>
      )}

      {/* CT-2 — manual CAR/PAR creation; source is fixed to MANUAL. */}
      <FormModal
        open={creating}
        title={isAr ? 'إجراء جديد (مصدر يدوي)' : 'New action (manual source)'}
        fields={createFields}
        initial={createInitial}
        size="lg"
        submitLabel={isAr ? 'إنشاء الإجراء' : 'Create action'}
        onClose={() => setCreating(false)}
        onSubmit={submitCreate}
      />

      {/* CT-1 — edit modal: the in-product way to set owner/plan/evidence.
          evidenceRef here is what unblocks Complete (Rule B). */}
      <FormModal
        open={editCapa !== null}
        title={editCapa
          ? `${isAr ? 'تعديل الإجراء' : 'Edit action'} · ${editCapa.actionCode ?? `#${editCapa.id}`}`
          : ''}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditCapa(null)}
        onSubmit={submitEdit}
      />
    </div>
  );
}
