import { useEffect, useMemo, useState } from 'react';
import {
  Calculator, ChevronLeft, FileText, AlertTriangle, ShieldAlert,
  CheckCircle2, XCircle, Send, Banknote, ListChecks,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Input, Textarea } from '../../components/ui/Input';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { EntityActivityTimeline } from '../../components/audit/EntityActivityTimeline';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { ApiError } from '../../lib/api';
import {
  listQuantitativeBias,
  getQuantitativeBia,
  createQuantitativeBia,
  updateQuantitativeBia,
  submitQuantitativeBia,
  bcmReviewQuantitativeBia,
  cfoApproveQuantitativeBia,
  boardApproveQuantitativeBia,
  archiveQuantitativeBia,
  TIME_BANDS, LOSS_TYPES,
  TIME_BAND_LABELS, LOSS_TYPE_LABELS, STATUS_LABELS,
  statusTone, confidenceTone, fmtSar, bandTotal,
  type BiaQuantitative, type BiaQuantitativeLossLine,
  type BiaQuantTimeBand, type BiaQuantLossType, type BiaQuantConfidence,
  type BiaQuantitativeRequest,
} from '../../lib/biaQuantitative';

/**
 * Slice 9-C — Quantitative BIA register + matrix editor.
 *
 * <p>Two modes on one route ({@code /bcm/bia-quantitative}):
 * <ul>
 *   <li><b>List</b> — table of every quantitative BIA in the tenant with
 *       process, totals at 24h / 1m, MTPD breach band, and status.</li>
 *   <li><b>Detail/wizard</b> — header form + 5-band × 8-loss-type matrix
 *       + workflow actions. The "MTPD breach" banner fires the moment the
 *       computed cumulative loss crosses the board-set ceiling — which is
 *       what the CFO and Board actually read.</li>
 * </ul>
 *
 * <p>Saudi-native context throughout: SAMA Operational Resilience
 * Framework §5.3, PDPL Article 35, NCA ECC-2 BCM, SAR currency only.
 *
 * <p>Uses the design-system utility classes (added in PR #173) instead
 * of inline {@code style={{...}}} for layout: {@code kpi-grid-sm},
 * {@code split-pane}, {@code text-bold}, {@code mt-sm}. The few inline
 * styles that remain are content-presentation (table layouts, RTL
 * banner colours) not layout containers.
 */
type Mode = 'list' | 'detail';

/**
 * Editable form state. We keep loss-line amounts as strings while the
 * user is typing (so "" and "1.5" are both legal) and coerce to numbers
 * on save.
 */
interface MatrixCell {
  amount: string;
  basisEn?: string;
  basisAr?: string;
  confidence?: BiaQuantConfidence;
  isEstimate?: boolean;
}

type Matrix = Record<BiaQuantTimeBand, Partial<Record<BiaQuantLossType, MatrixCell>>>;

const EMPTY_MATRIX: Matrix = {
  '24h': {}, '72h': {}, '1w': {}, '2w': {}, '1m': {},
};

interface DraftForm {
  externalId?: string;
  processId: string;
  assessmentCode: string;
  assessmentDate: string;
  cycleYear: string;
  preparedByName: string;
  maxTolerableLossSar: string;
  methodologyNotesEn: string;
  methodologyNotesAr: string;
}

function blankDraft(): DraftForm {
  const today = new Date().toISOString().slice(0, 10);
  const year = String(new Date().getFullYear());
  return {
    externalId: undefined,
    processId: '',
    assessmentCode: '',
    assessmentDate: today,
    cycleYear: year,
    preparedByName: '',
    maxTolerableLossSar: '',
    methodologyNotesEn: '',
    methodologyNotesAr: '',
  };
}

function buildMatrixFromLines(lines: BiaQuantitativeLossLine[]): Matrix {
  const m: Matrix = { '24h': {}, '72h': {}, '1w': {}, '2w': {}, '1m': {} };
  for (const line of lines) {
    if (!TIME_BANDS.includes(line.timeBand)) continue;
    if (!LOSS_TYPES.includes(line.lossType)) continue;
    m[line.timeBand][line.lossType] = {
      amount: String(line.amountSar ?? ''),
      basisEn: line.basisNarrativeEn,
      basisAr: line.basisNarrativeAr,
      confidence: line.confidence,
      isEstimate: line.isEstimate,
    };
  }
  return m;
}

function matrixToLines(m: Matrix): BiaQuantitativeLossLine[] {
  const out: BiaQuantitativeLossLine[] = [];
  for (const band of TIME_BANDS) {
    for (const type of LOSS_TYPES) {
      const cell = m[band][type];
      if (!cell) continue;
      const amt = Number(cell.amount);
      if (Number.isNaN(amt) || cell.amount === '') continue;
      out.push({
        timeBand: band,
        lossType: type,
        amountSar: amt,
        basisNarrativeEn: cell.basisEn,
        basisNarrativeAr: cell.basisAr,
        confidence: cell.confidence ?? 'MEDIUM',
        isEstimate: cell.isEstimate ?? true,
      });
    }
  }
  return out;
}

function draftFrom(b: BiaQuantitative): DraftForm {
  return {
    externalId: b.externalId,
    processId: String(b.processId),
    assessmentCode: b.assessmentCode,
    assessmentDate: b.assessmentDate,
    cycleYear: String(b.cycleYear),
    preparedByName: b.preparedByName ?? '',
    maxTolerableLossSar: b.maxTolerableLossSar != null ? String(b.maxTolerableLossSar) : '',
    methodologyNotesEn: b.methodologyNotesEn ?? '',
    methodologyNotesAr: b.methodologyNotesAr ?? '',
  };
}

export function QuantitativeBiaPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();

  const [mode, setMode] = useState<Mode>('list');
  const [items, setItems] = useState<BiaQuantitative[]>([]);
  const [loading, setLoading] = useState(false);

  const [current, setCurrent] = useState<BiaQuantitative | null>(null);
  const [form, setForm] = useState<DraftForm>(blankDraft());
  const [matrix, setMatrix] = useState<Matrix>(EMPTY_MATRIX);
  const [saving, setSaving] = useState(false);

  // ─── Load ──────────────────────────────────────────────────────────
  async function reload() {
    setLoading(true);
    try {
      const list = await listQuantitativeBias();
      setItems(Array.isArray(list) ? list : []);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل تحليلات الأثر الكمية' : 'Failed to load quantitative BIAs'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ─── Navigation ───────────────────────────────────────────────────
  async function openExisting(externalId: string) {
    try {
      const b = await getQuantitativeBia(externalId);
      setCurrent(b);
      setForm(draftFrom(b));
      setMatrix(buildMatrixFromLines(b.lossLines ?? []));
      setMode('detail');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  function openNew() {
    setCurrent(null);
    setForm(blankDraft());
    setMatrix({ '24h': {}, '72h': {}, '1w': {}, '2w': {}, '1m': {} });
    setMode('detail');
  }

  function backToList() {
    setMode('list');
    setCurrent(null);
    void reload();
  }

  // ─── Computed totals + breach (front-end mirror of the service) ──
  const computed = useMemo(() => {
    const ceiling = Number(form.maxTolerableLossSar);
    const totals: Record<BiaQuantTimeBand, number> = { '24h': 0, '72h': 0, '1w': 0, '2w': 0, '1m': 0 };
    for (const band of TIME_BANDS) {
      for (const type of LOSS_TYPES) {
        const cell = matrix[band][type];
        if (!cell) continue;
        const n = Number(cell.amount);
        if (!Number.isNaN(n)) totals[band] += n;
      }
    }
    let cumulative = 0;
    let breach: BiaQuantTimeBand | null = null;
    if (!Number.isNaN(ceiling) && ceiling > 0) {
      for (const band of TIME_BANDS) {
        cumulative += totals[band];
        if (cumulative > ceiling) { breach = band; break; }
      }
    }
    return { totals, breach };
  }, [matrix, form.maxTolerableLossSar]);

  // ─── Cell editor ──────────────────────────────────────────────────
  function setCell(band: BiaQuantTimeBand, type: BiaQuantLossType, patch: Partial<MatrixCell>) {
    setMatrix(prev => {
      const next = { ...prev, [band]: { ...prev[band] } };
      const existing = next[band][type] ?? { amount: '' };
      next[band][type] = { ...existing, ...patch };
      return next;
    });
  }

  // ─── Save / submit / approve actions ──────────────────────────────
  async function save(): Promise<BiaQuantitative | null> {
    if (!form.processId) {
      toast.danger(isAr ? 'حدّد العملية الحرجة' : 'Pick a critical process');
      return null;
    }
    setSaving(true);
    try {
      const body: BiaQuantitativeRequest = {
        processId: Number(form.processId),
        assessmentCode: form.assessmentCode || undefined,
        assessmentDate: form.assessmentDate,
        cycleYear: Number(form.cycleYear) || new Date().getFullYear(),
        currency: 'SAR',
        methodologyNotesEn: form.methodologyNotesEn || undefined,
        methodologyNotesAr: form.methodologyNotesAr || undefined,
        maxTolerableLossSar: form.maxTolerableLossSar ? Number(form.maxTolerableLossSar) : undefined,
        preparedByName: form.preparedByName || undefined,
        lossLines: matrixToLines(matrix),
      };
      const saved = current?.externalId
        ? await updateQuantitativeBia(current.externalId, body)
        : await createQuantitativeBia(body);
      setCurrent(saved);
      setForm(draftFrom(saved));
      setMatrix(buildMatrixFromLines(saved.lossLines ?? []));
      toast.success(isAr ? 'تم الحفظ' : 'Saved');
      return saved;
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الحفظ' : 'Save failed'));
      return null;
    } finally {
      setSaving(false);
    }
  }

  async function submitForReview() {
    const saved = await save();
    if (!saved) return;
    try {
      const out = await submitQuantitativeBia(saved.externalId);
      setCurrent(out);
      setForm(draftFrom(out));
      toast.success(isAr ? 'أُرسل للمراجعة' : 'Sent for review');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function bcmReview() {
    if (!current?.externalId) return;
    try {
      const out = await bcmReviewQuantitativeBia(current.externalId);
      setCurrent(out);
      setForm(draftFrom(out));
      toast.success(isAr ? 'أحالها قائد استمرارية الأعمال إلى المدير المالي'
        : 'BCM Lead handed off to CFO');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function cfoApprove() {
    if (!current?.externalId) return;
    const approver = await dialog.prompt({
      title: isAr ? 'اسم المدير المالي المعتمِد' : 'CFO approver name',
      placeholder: isAr ? 'مثال: فيصل السبيعي — المدير المالي للمجموعة'
        : 'e.g. Faisal Al-Subaie — Group CFO',
      required: true,
    });
    if (approver === null) return;
    const comments = await dialog.prompt({
      title: isAr ? 'تعليقات الاعتماد (اختياري)' : 'Approval comments (optional)',
      type: 'textarea',
      placeholder: isAr ? 'مثال: اعتمد مع تكليف بـ active/active لـ SARIE في النصف الثاني'
        : 'e.g. Approved subject to active/active SARIE remediation in H2',
    });
    if (comments === null) return;
    try {
      const out = await cfoApproveQuantitativeBia(current.externalId, {
        approverName: approver, comments, decision: 'APPROVE',
      });
      setCurrent(out);
      setForm(draftFrom(out));
      toast.success(isAr ? 'اعتمد المدير المالي التحليل' : 'CFO approved');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function cfoReject() {
    if (!current?.externalId) return;
    const approver = await dialog.prompt({
      title: isAr ? 'اسم المدير المالي' : 'CFO name', required: true,
    });
    if (approver === null) return;
    const reason = await dialog.prompt({
      title: isAr ? 'سبب الرفض' : 'Rejection reason',
      type: 'textarea', required: true,
      placeholder: isAr ? 'مثال: أساس الإيرادات أقل من الواقع — أعد التحليل بأرقام التقرير السنوي المُدقَّق'
        : 'e.g. Revenue baseline understated — redo with audited annual figures',
    });
    if (reason === null) return;
    try {
      const out = await cfoApproveQuantitativeBia(current.externalId, {
        approverName: approver, decision: 'REJECT', rejectionReason: reason,
      });
      setCurrent(out);
      setForm(draftFrom(out));
      toast.success(isAr ? 'تم تسجيل الرفض' : 'Rejection recorded');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function boardApprove() {
    if (!current?.externalId) return;
    const ref = await dialog.prompt({
      title: isAr ? 'مرجع محضر المجلس' : 'Board minutes reference',
      placeholder: isAr ? 'مثال: محضر مجلس الإدارة 2026-ر2 §4.3 — لجنة المخاطر'
        : 'e.g. Board minutes 2026-Q2 §4.3 (Risk Committee)',
      required: true,
    });
    if (ref === null) return;
    try {
      const out = await boardApproveQuantitativeBia(current.externalId, ref);
      setCurrent(out);
      setForm(draftFrom(out));
      toast.success(isAr ? 'اعتمد المجلس التحليل' : 'Board approved');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function archive() {
    if (!current?.externalId) return;
    const ok = await dialog.confirm({
      title: isAr ? 'أرشفة هذا التحليل؟' : 'Archive this assessment?',
      body: isAr ? 'لن يَظهر في القوائم النشطة. يمكن استرجاعه من قاعدة البيانات.'
        : 'It will no longer appear in active lists. Recoverable from the database.',
      destructive: true,
    });
    if (!ok) return;
    try {
      await archiveQuantitativeBia(current.externalId);
      toast.success(isAr ? 'تمت الأرشفة' : 'Archived');
      backToList();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  // ─── Render: list ────────────────────────────────────────────────
  if (mode === 'list') {
    const breached = items.filter(b => b.mtpdBreachAt).length;
    const approved = items.filter(b => b.status === 'CFO_APPROVED' || b.status === 'BOARD_APPROVED').length;
    return (
      <Shell>
        <PageHeader
          title={isAr ? 'تحليل الأثر الكمي للأعمال' : 'Quantitative Business Impact Analysis'}
          sub={isAr
            ? 'قياس الخسارة المالية الناتجة عن انقطاع العمليات الحرجة عبر خمسة نوافذ زمنية وثمانية أنواع خسارة، ومقارنتها بسقف الخسارة المعتمد من المجلس. يستند إلى إطار المرونة التشغيلية لساما §5.3 ومجال استمرارية الأعمال في ECC-2.'
            : 'Quantify the financial loss from critical-process outage across five time bands and eight loss categories, then ladder it against the board-set maximum tolerable loss. Aligned with SAMA Operational Resilience Framework §5.3 and NCA ECC-2 BCM.'}
          eyebrow={isAr ? 'استمرارية الأعمال' : 'Business Continuity'}
          actions={<Button variant="primary" onClick={openNew}>
            <Calculator size={14} /> {isAr ? 'تحليل كمي جديد' : 'New quantitative BIA'}
          </Button>}
        />

        <div className="kpi-grid-sm mb-md">
          <Kpi Icon={ListChecks}    label={isAr ? 'إجمالي' : 'Total'}            value={items.length}        tone="info" />
          <Kpi Icon={CheckCircle2}  label={isAr ? 'معتمد'  : 'Approved'}         value={approved}            tone="success" />
          <Kpi Icon={ShieldAlert}   label={isAr ? 'تجاوز سقف MTPD' : 'MTPD breach'} value={breached}        tone="danger" />
          <Kpi Icon={Banknote}      label={isAr ? 'العملة' : 'Currency'}         value="SAR"                 tone="neutral" />
        </div>

        <Card title={isAr ? 'سجل التحليل الكمي' : 'Quantitative BIA register'}>
          {loading && <div className="text-muted" style={{ fontSize: 11 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
          {items.length === 0 ? (
            <InlineEmpty text={isAr ? 'لا توجد تحليلات كمية بعد' : 'No quantitative BIAs yet'} />
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table" style={{ width: '100%', fontSize: 13 }}>
                <thead>
                  <tr>
                    <th>{isAr ? 'الرمز' : 'Code'}</th>
                    <th>{isAr ? 'العملية' : 'Process'}</th>
                    <th>{isAr ? 'التاريخ' : 'Date'}</th>
                    <th>{isAr ? 'خسارة 24 س' : '24h loss'}</th>
                    <th>{isAr ? 'خسارة شهر' : '1m loss'}</th>
                    <th>{isAr ? 'تجاوز MTPD' : 'MTPD breach'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th>{isAr ? 'آخر تحديث' : 'Updated'}</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(b => (
                    <tr key={b.externalId} onClick={() => openExisting(b.externalId)}
                        className="row-clickable" style={{ cursor: 'pointer' }}>
                      <td className="text-bold">{b.assessmentCode}</td>
                      <td>{isAr ? `عملية #${b.processId}` : `Process #${b.processId}`}</td>
                      <td>{b.assessmentDate}</td>
                      <td>{fmtSar(bandTotal(b, '24h'), isAr)}</td>
                      <td>{fmtSar(bandTotal(b, '1m'), isAr)}</td>
                      <td>
                        {b.mtpdBreachAt
                          ? <Badge tone="danger">{TIME_BAND_LABELS[b.mtpdBreachAt][isAr ? 'ar' : 'en']}</Badge>
                          : <span className="text-muted">—</span>}
                      </td>
                      <td><Badge tone={statusTone(b.status)}>{STATUS_LABELS[b.status][isAr ? 'ar' : 'en']}</Badge></td>
                      <td className="text-muted" style={{ fontSize: 11 }}>
                        {b.updatedAt ? b.updatedAt.slice(0, 10) : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </Shell>
    );
  }

  // ─── Render: detail ──────────────────────────────────────────────
  const ceiling = Number(form.maxTolerableLossSar);
  const status = current?.status ?? 'DRAFT';
  const editable = status === 'DRAFT' || status === 'IN_REVIEW';

  return (
    <Shell>
      <PageHeader
        title={current?.assessmentCode ?? (isAr ? 'تحليل كمي جديد' : 'New quantitative BIA')}
        sub={current
          ? (isAr ? `العملية #${current.processId} — دورة ${current.cycleYear}` : `Process #${current.processId} — cycle ${current.cycleYear}`)
          : (isAr ? 'سجّل الخسارة المتوقعة لكل نافذة زمنية ولكل نوع خسارة، ثم احسب نقطة تجاوز السقف المعتمد من المجلس.'
                  : 'Record expected loss per time band and loss type, then locate the point at which cumulative loss crosses the board ceiling.')}
        eyebrow={isAr ? 'تحليل الأثر الكمي' : 'Quantitative BIA'}
        actions={<>
          <Button variant="secondary" onClick={backToList}>
            <ChevronLeft size={14} /> {isAr ? 'عودة للقائمة' : 'Back to list'}
          </Button>
          {current && <Badge tone={statusTone(status)}>{STATUS_LABELS[status][isAr ? 'ar' : 'en']}</Badge>}
        </>}
      />

      {/* MTPD breach banner — only shown when the loss model + ceiling produce
          a breach. Crucial CFO-grade signal — render it bold and red. The
          Card primitive carries no tone variant, so a thin custom wrapper
          gives us the danger-tinted background for this one alarm. */}
      {computed.breach && (
        <div className="card mb-md" style={{
          background: 'var(--danger-bg)',
          borderColor: 'var(--danger-fg)',
        }}>
          <div className="card-body" style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
            <AlertTriangle size={28} color="var(--danger-fg)" />
            <div>
              <div className="text-bold" style={{ fontSize: 15, color: 'var(--danger-fg)' }}>
                {isAr
                  ? `تجاوز سقف الخسارة المعتمد عند ${TIME_BAND_LABELS[computed.breach][isAr ? 'ar' : 'en']}`
                  : `MTPD ceiling breached at ${TIME_BAND_LABELS[computed.breach].en}`}
              </div>
              <div className="text-muted mt-sm" style={{ fontSize: 12 }}>
                {isAr
                  ? `الخسارة التراكمية تتخطى السقف المعتمد من المجلس (${fmtSar(ceiling, true)}) في هذه النافذة. مطلوب اعتماد المدير المالي + مراجعة لجنة المخاطر.`
                  : `Cumulative loss crosses the board-set ceiling of ${fmtSar(ceiling)} at this band. CFO sign-off + Risk Committee review required.`}
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="split-pane">
        {/* ─── Left rail: header form + workflow actions ─── */}
        <div>
          <Card title={isAr ? 'بيانات التحليل' : 'Assessment header'}>
            <div style={{ display: 'grid', gap: 10 }}>
              <Input
                label={isAr ? 'معرّف العملية الحرجة' : 'Critical process ID'}
                value={form.processId}
                onChange={e => setForm({ ...form, processId: e.target.value })}
                disabled={!editable}
                placeholder={isAr ? 'مثال: 1 (المدفوعات الأفراد — سريع / سداد / مدى)' : 'e.g. 1 (Retail Payments — SARIE/SADAD/mada)'}
              />
              <Input
                label={isAr ? 'رمز التحليل' : 'Assessment code'}
                value={form.assessmentCode}
                onChange={e => setForm({ ...form, assessmentCode: e.target.value })}
                disabled={!editable || !!current}
                placeholder="BIA-Q-2026-001"
              />
              <Input
                type="date"
                label={isAr ? 'تاريخ التحليل' : 'Assessment date'}
                value={form.assessmentDate}
                onChange={e => setForm({ ...form, assessmentDate: e.target.value })}
                disabled={!editable}
              />
              <Input
                label={isAr ? 'سنة الدورة' : 'Cycle year'}
                value={form.cycleYear}
                onChange={e => setForm({ ...form, cycleYear: e.target.value })}
                disabled={!editable}
              />
              <Input
                label={isAr ? 'أعدّ التحليل' : 'Prepared by'}
                value={form.preparedByName}
                onChange={e => setForm({ ...form, preparedByName: e.target.value })}
                disabled={!editable}
                placeholder={isAr ? 'مثال: ليلى العتيبي — قائد استمرارية الأعمال'
                                  : 'e.g. Layla Al-Otaibi — BCM Lead'}
              />
              <Input
                label={isAr ? 'سقف الخسارة المعتمد (ريال)' : 'Max tolerable loss (SAR)'}
                value={form.maxTolerableLossSar}
                onChange={e => setForm({ ...form, maxTolerableLossSar: e.target.value })}
                disabled={!editable}
                placeholder="25000000"
              />
              <Textarea
                label={isAr ? 'منهجية التحليل (EN)' : 'Methodology notes (EN)'}
                value={form.methodologyNotesEn}
                onChange={e => setForm({ ...form, methodologyNotesEn: e.target.value })}
                disabled={!editable}
                rows={3}
              />
              <Textarea
                label={isAr ? 'منهجية التحليل (AR)' : 'Methodology notes (AR)'}
                value={form.methodologyNotesAr}
                onChange={e => setForm({ ...form, methodologyNotesAr: e.target.value })}
                disabled={!editable}
                rows={3}
              />
            </div>
          </Card>

          <div className="mt-md" />
          <Card title={isAr ? 'الإجراءات' : 'Workflow actions'}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {editable && (
                <Button variant="primary" onClick={save} disabled={saving}>
                  <FileText size={14} /> {isAr ? 'حفظ' : 'Save'}
                </Button>
              )}
              {status === 'DRAFT' && current && (
                <Button variant="secondary" onClick={submitForReview}>
                  <Send size={14} /> {isAr ? 'إرسال للمراجعة' : 'Submit for review'}
                </Button>
              )}
              {status === 'IN_REVIEW' && (
                <Button variant="secondary" onClick={bcmReview}>
                  <CheckCircle2 size={14} /> {isAr ? 'مراجعة قائد استمرارية الأعمال' : 'BCM Lead reviewed'}
                </Button>
              )}
              {status === 'CFO_REVIEW' && (
                <>
                  <Button variant="primary" onClick={cfoApprove}>
                    <CheckCircle2 size={14} /> {isAr ? 'اعتماد المدير المالي' : 'CFO approve'}
                  </Button>
                  <Button variant="danger" onClick={cfoReject}>
                    <XCircle size={14} /> {isAr ? 'رفض المدير المالي' : 'CFO reject'}
                  </Button>
                </>
              )}
              {status === 'CFO_APPROVED' && (
                <Button variant="primary" onClick={boardApprove}>
                  <CheckCircle2 size={14} /> {isAr ? 'اعتماد المجلس' : 'Board approve'}
                </Button>
              )}
              {current && (
                <Button variant="ghost" onClick={archive}>
                  {isAr ? 'أرشفة' : 'Archive'}
                </Button>
              )}
            </div>

            {current?.cfoApproverName && (
              <div className="mt-md text-muted" style={{ fontSize: 12 }}>
                <div><span className="text-bold">{isAr ? 'اعتمد:' : 'Approved by:'}</span> {current.cfoApproverName}</div>
                {current.cfoApprovedAt && <div>{current.cfoApprovedAt.slice(0, 16).replace('T', ' ')}</div>}
                {current.cfoComments && <div className="mt-sm">{current.cfoComments}</div>}
              </div>
            )}
            {current?.boardMinutesRef && (
              <div className="mt-md text-muted" style={{ fontSize: 12 }}>
                <span className="text-bold">{isAr ? 'محضر المجلس:' : 'Board minutes:'}</span> {current.boardMinutesRef}
              </div>
            )}
            {current?.rejectionReason && (
              <div className="mt-md" style={{ fontSize: 12, color: 'var(--danger-fg)' }}>
                <span className="text-bold">{isAr ? 'سبب الرفض:' : 'Rejection reason:'}</span> {current.rejectionReason}
              </div>
            )}
          </Card>
        </div>

        {/* ─── Right rail: loss matrix + totals ─── */}
        <div>
          <Card title={isAr ? 'مصفوفة الخسارة (ريال سعودي)' : 'Loss matrix (SAR)'}>
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table" style={{ width: '100%', fontSize: 12 }}>
                <thead>
                  <tr>
                    <th>{isAr ? 'نوع الخسارة' : 'Loss type'}</th>
                    {TIME_BANDS.map(b => (
                      <th key={b} style={{ minWidth: 120 }}>{TIME_BAND_LABELS[b][isAr ? 'ar' : 'en']}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {LOSS_TYPES.map(type => (
                    <tr key={type}>
                      <td className="text-bold" style={{ minWidth: 160 }}>
                        {LOSS_TYPE_LABELS[type][isAr ? 'ar' : 'en']}
                      </td>
                      {TIME_BANDS.map(band => {
                        const cell = matrix[band][type];
                        return (
                          <td key={band} style={{ padding: 4 }}>
                            <input
                              type="number"
                              value={cell?.amount ?? ''}
                              onChange={e => setCell(band, type, { amount: e.target.value })}
                              disabled={!editable}
                              className="input input-sm"
                              style={{ width: '100%', fontSize: 12, padding: '4px 6px' }}
                              placeholder="0"
                            />
                            {cell?.amount && Number(cell.amount) > 0 && (
                              <div className="mt-sm" style={{ display: 'flex', gap: 4, alignItems: 'center', fontSize: 10 }}>
                                <select
                                  value={cell.confidence ?? 'MEDIUM'}
                                  onChange={e => setCell(band, type, { confidence: e.target.value as BiaQuantConfidence })}
                                  disabled={!editable}
                                  className="input input-sm"
                                  style={{ fontSize: 10, padding: '2px 4px' }}
                                >
                                  <option value="HIGH">{isAr ? 'مرتفع' : 'High'}</option>
                                  <option value="MEDIUM">{isAr ? 'متوسط' : 'Medium'}</option>
                                  <option value="LOW">{isAr ? 'منخفض' : 'Low'}</option>
                                </select>
                                <Badge tone={confidenceTone(cell.confidence)}>
                                  {cell.isEstimate === false
                                    ? (isAr ? 'فعلي' : 'Actual')
                                    : (isAr ? 'تقدير' : 'Est.')}
                                </Badge>
                              </div>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                  {/* Computed totals row */}
                  <tr style={{ background: 'var(--surface-2, var(--surface))' }}>
                    <td className="text-bolder">{isAr ? 'الإجمالي' : 'Total'}</td>
                    {TIME_BANDS.map(band => {
                      const t = computed.totals[band];
                      const cumulative = TIME_BANDS.slice(0, TIME_BANDS.indexOf(band) + 1)
                        .reduce((acc, b) => acc + computed.totals[b], 0);
                      const isBreachBand = computed.breach === band;
                      return (
                        <td key={band} className="text-bold" style={{
                          color: isBreachBand ? 'var(--danger-fg)' : undefined,
                          background: isBreachBand ? 'var(--danger-bg)' : undefined,
                        }}>
                          <div>{fmtSar(t, isAr)}</div>
                          <div className="text-muted" style={{ fontSize: 10 }}>
                            {isAr ? 'تراكمي: ' : 'Cum: '}{fmtSar(cumulative, isAr)}
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                </tbody>
              </table>
            </div>
            <div className="text-muted mt-md" style={{ fontSize: 11 }}>
              {isAr
                ? 'الخلايا قابلة للتحرير في حالتي «مسودة» و«قيد المراجعة» فقط. تُحفظ التعليلات النصية لكل خلية عبر واجهة برمجة لاحقة — يكفي هنا تعبئة المبلغ ومستوى الثقة.'
                : 'Cells are editable in DRAFT and IN_REVIEW only. Per-cell narrative notes round-trip via the API — the matrix here captures amount + confidence.'}
            </div>
          </Card>

          {current?.id && (
            <>
              <div className="mt-md" />
              <Card title={isAr ? 'سجل النشاط' : 'Activity timeline'}>
                <EntityActivityTimeline entityType="BiaQuantitative" entityId={current.id} />
              </Card>
            </>
          )}
        </div>
      </div>
    </Shell>
  );
}
