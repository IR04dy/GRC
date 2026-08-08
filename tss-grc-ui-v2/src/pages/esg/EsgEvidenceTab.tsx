/**
 * F2-16 tranche 3 — the ESG evidence repository tab, moved out of `EsgPage.tsx`.
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
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Trash2, CheckCircle2, AlertCircle,
  Plus, ArrowRight, Link2, Clock,
  Paperclip, Pencil, Upload,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listEvidenceItems, createEvidenceItem, uploadEvidenceItemFile,
  submitEvidenceItem, approveEvidenceItem, rejectEvidenceItem, deleteEvidenceItem,
  evidenceItemStatusTone, evidenceItemStatusLabel, canSubmitEvidence,
  type EvidenceItem, type EvidenceItemStatus,
} from '../../lib/evidenceItems';
import {
  linkSustEvidence, SUST_EVIDENCE_ENTITY_TYPES, type SustEvidenceEntityType,
} from '../../lib/sustEvidenceLinks';

// ─── ESG Evidence Repository tab (V64 — slice 10) ────────────────────────────
//
// Backs the evidence_ref text columns scattered across the V55-V63
// entities (climate risks, GHG, CMA, TCFD, SGI, materiality, metrics,
// CAPA). Single repository view filterable by module area + approval
// status + expiry window. Approve / Reject lifecycle for governance.
// Rule 9 sweep button surfaces evidence whose valid_to has passed.

export function EvidenceTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<EvidenceItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<EvidenceItemStatus | 'ALL'>('ALL');
  const [createOpen, setCreateOpen] = useState(false);
  const [linkTarget, setLinkTarget] = useState<EvidenceItem | null>(null);
  const [uploadTargetId, setUploadTargetId] = useState<number | null>(null);
  const fileRef = useRef<HTMLInputElement | null>(null);

  async function reload() {
    setLoading(true);
    const pg = await listEvidenceItems({ size: 200 })
      .catch(() => ({ content: [] as EvidenceItem[], totalElements: 0, totalPages: 0, number: 0, size: 0 }));
    setItems(pg.content);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, []);

  const counts = useMemo(() => {
    const c: Record<string, number> = {
      total: items.length, DRAFT: 0, PENDING_REVIEW: 0, APPROVED: 0, REJECTED: 0, NEEDS_REVISION: 0, EXPIRED: 0,
    };
    for (const e of items) c[e.status] = (c[e.status] ?? 0) + 1;
    return c;
  }, [items]);

  const filtered = statusFilter === 'ALL' ? items : items.filter(e => e.status === statusFilter);

  // ── Create (real EvidenceItem, starts DRAFT) ─────────────────────
  // The optional file staged in the form is uploaded straight after create, so
  // the user attaches evidence in one step (server computes the SHA-256). A
  // metadata-only draft is still allowed — upload later via the row's button.
  async function submitCreate(values: Record<string, unknown>) {
    const titleEn = String(values.titleEn ?? '').trim();
    if (!titleEn) { toast.danger(isAr ? 'العنوان مطلوب' : 'Title is required'); return; }
    const file = values.file instanceof File ? values.file : null;
    try {
      const created = await createEvidenceItem({
        titleEn,
        titleAr: String(values.titleAr ?? '').trim() || undefined,
        descriptionEn: String(values.descriptionEn ?? '').trim() || undefined,
        evidenceType: String(values.evidenceType ?? 'DOCUMENT'),
        version: String(values.version ?? '').trim() || undefined,
        validFrom: String(values.validFrom ?? '').trim() || undefined,
        validTo: String(values.validTo ?? '').trim() || undefined,
        sourceModule: 'SUSTAINABILITY',
      });
      if (file) {
        try {
          await uploadEvidenceItemFile(created.id, file);
          toast.success(isAr ? 'تم إنشاء الدليل ورفع الملف (بصمة SHA-256) — جاهز للإرسال'
                             : 'Evidence created and file uploaded (SHA-256) — ready to submit');
        } catch (upErr) {
          toast.danger(isAr ? 'أُنشئ الدليل لكن فشل رفع الملف — ارفعه من الجدول'
                            : (upErr instanceof ApiError ? upErr.message : 'Evidence created, but the file upload failed — upload it from the table'));
        }
      } else {
        toast.success(isAr ? 'تم إنشاء الدليل (مسودة) — ارفع ملفاً ثم أرسله للمراجعة'
                           : 'Evidence created (Draft) — upload a file, then submit for review');
      }
      setCreateOpen(false);
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Create failed'); }
  }

  // ── Real file upload → server computes SHA-256 ───────────────────
  function triggerUpload(id: number) { setUploadTargetId(id); fileRef.current?.click(); }
  async function uploadPicked(file: File | null) {
    if (!file || uploadTargetId == null) { setUploadTargetId(null); return; }
    try {
      await uploadEvidenceItemFile(uploadTargetId, file);
      toast.success(isAr ? 'تم رفع الملف واحتساب بصمة SHA-256' : 'File uploaded — SHA-256 computed server-side');
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Upload failed'); }
    finally { setUploadTargetId(null); }
  }

  // ── Lifecycle transitions ────────────────────────────────────────
  async function act(fn: () => Promise<unknown>, ok: string) {
    try { await fn(); toast.success(ok); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  const doSubmit  = (id: number) => act(() => submitEvidenceItem(id),  isAr ? 'أُرسل للمراجعة' : 'Submitted for review');
  const doApprove = (id: number) => act(() => approveEvidenceItem(id), isAr ? 'تم الاعتماد' : 'Approved');
  const doReject  = (id: number) => act(() => rejectEvidenceItem(id),  isAr ? 'تم الرفض' : 'Rejected');
  function doDelete(id: number) {
    if (!window.confirm(isAr ? 'حذف هذا الدليل؟' : 'Delete this evidence item?')) return;
    void act(() => deleteEvidenceItem(id), isAr ? 'تم الحذف' : 'Deleted');
  }

  // ── Link a real evidence item to an ESG entity (S-C bridge) ──────
  async function submitLink(values: Record<string, unknown>) {
    if (!linkTarget) return;
    const entityType = String(values.entityType ?? '') as SustEvidenceEntityType;
    const entityId = Number(values.entityId ?? 0);
    if (!entityType || !Number.isFinite(entityId) || entityId <= 0) {
      toast.danger(isAr ? 'النوع والمعرّف مطلوبان' : 'Entity type and id are required'); return;
    }
    try {
      await linkSustEvidence(entityType, entityId, linkTarget.id, String(values.notes ?? '').trim() || undefined);
      toast.success(isAr ? 'تم ربط الدليل بالكيان' : 'Linked to ESG entity');
      setLinkTarget(null);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Link failed'); }
  }

  const createFields: FieldDef[] = [
    { kind: 'text',     key: 'titleEn', required: true, span: 12, label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text',     key: 'titleAr', span: 12, label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'select',   key: 'evidenceType', required: true, span: 6, label: isAr ? 'النوع' : 'Type', options: [
        { value: 'DOCUMENT', label: 'Document' }, { value: 'REPORT', label: 'Report' },
        { value: 'CERTIFICATE', label: 'Certificate' }, { value: 'SPREADSHEET', label: 'Spreadsheet' },
        { value: 'SCREENSHOT', label: 'Screenshot' }, { value: 'POLICY', label: 'Policy' },
        { value: 'ATTESTATION', label: 'Attestation' }, { value: 'OTHER', label: 'Other' } ] },
    { kind: 'text',     key: 'version', span: 6, label: isAr ? 'الإصدار' : 'Version', placeholder: 'v1.0' },
    { kind: 'date',     key: 'validFrom', span: 6, label: isAr ? 'صالح من' : 'Valid from' },
    { kind: 'date',     key: 'validTo', span: 6, label: isAr ? 'صالح حتى' : 'Valid to' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2, label: isAr ? 'الوصف' : 'Description' },
    { kind: 'file',     key: 'file', span: 12, label: isAr ? 'المرفق (اختياري)' : 'Attachment (optional)',
      accept: '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.txt,.csv,.json,.xml,.zip',
      hint: isAr ? 'ارفع الملف الآن (تُحتسب بصمة SHA-256) أو لاحقاً من الجدول'
                 : 'Upload now (SHA-256 computed server-side) or later from the table' },
  ];
  const linkFields: FieldDef[] = [
    { kind: 'select', key: 'entityType', required: true, span: 12, label: isAr ? 'نوع الكيان' : 'ESG entity type',
      options: SUST_EVIDENCE_ENTITY_TYPES.map(t => ({ value: t.value, label: isAr ? t.ar : t.en })) },
    { kind: 'number', key: 'entityId', required: true, span: 6, min: 1, label: isAr ? 'معرّف الكيان' : 'Entity id' },
    { kind: 'textarea', key: 'notes', span: 12, rows: 2, label: isAr ? 'ملاحظات' : 'Notes' },
  ];

  return (
    <div className="esg-stack">
      {/* Hidden picker — one per tab; the row's Upload button drives it. */}
      <input ref={fileRef} type="file" hidden
             onChange={ev => { const f = ev.target.files?.[0] ?? null; ev.target.value = ''; void uploadPicked(f); }} />

      {/* KPI strip */}
      <div className="kpi-grid">
        <Kpi Icon={Paperclip}    label={isAr ? 'إجمالي الأدلة' : 'Total evidence'} value={counts.total}          tone="primary" onClick={() => setStatusFilter('ALL')}            active={statusFilter === 'ALL'} />
        <Kpi Icon={CheckCircle2} label={isAr ? 'معتمدة' : 'Approved'}              value={counts.APPROVED}       tone="success" onClick={() => setStatusFilter('APPROVED')}       active={statusFilter === 'APPROVED'} />
        <Kpi Icon={Clock}        label={isAr ? 'قيد المراجعة' : 'Pending review'}  value={counts.PENDING_REVIEW} tone="info"    onClick={() => setStatusFilter('PENDING_REVIEW')} active={statusFilter === 'PENDING_REVIEW'} />
        <Kpi Icon={Pencil}       label={isAr ? 'مسودة' : 'Draft'}                  value={counts.DRAFT}          tone="neutral" onClick={() => setStatusFilter('DRAFT')}          active={statusFilter === 'DRAFT'} />
        <Kpi Icon={Trash2}       label={isAr ? 'مرفوضة' : 'Rejected'}              value={counts.REJECTED}       tone="danger"  onClick={() => setStatusFilter('REJECTED')}       active={statusFilter === 'REJECTED'} />
        <Kpi Icon={AlertCircle}  label={isAr ? 'منتهية' : 'Expired'}               value={counts.EXPIRED}        tone={counts.EXPIRED > 0 ? 'danger' : 'success'} onClick={() => setStatusFilter('EXPIRED')} active={statusFilter === 'EXPIRED'} />
      </div>

      <Card title={isAr ? 'مستودع أدلة الاستدامة' : 'Sustainability Evidence Repository'}
            Icon={Paperclip}
            action={
              <Button size="sm" variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>
                {isAr ? 'إضافة دليل' : 'Add evidence'}
              </Button>
            }>
        <div className="esg-text-eyebrow-sm">
          {isAr
            ? 'أدلة حقيقية عبر محرّك الأدلة الموحّد (module/evidence): أنشئ عنصراً، ارفع ملفاً حقيقياً (تُحتسب بصمة SHA-256 في الخادم)، ثم أرسله للمراجعة → اعتماد/رفض → إعادة تقديم. اربط أي دليل بكيان استدامة عبر جسر S-C.'
            : 'Real evidence via the unified engine (module/evidence): create an item, upload a real file (SHA-256 computed server-side), then Submit → Approve/Reject → resubmit. Link any item to an ESG entity via the S-C bridge.'}
        </div>

        {/* Status filter chips */}
        <div className="esg-filter-row">
          {(['ALL', 'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'NEEDS_REVISION', 'EXPIRED'] as const).map(s => (
            <button key={s} onClick={() => setStatusFilter(s)}
              className={`esg-filter-btn${statusFilter === s ? ' is-active' : ''}`}>
              {s === 'ALL' ? (isAr ? 'الكل' : 'All') : evidenceItemStatusLabel(s as EvidenceItemStatus, isAr ? 'ar' : 'en')}
            </button>
          ))}
          <span className="text-2xs text-muted esg-filter-row__count">
            {filtered.length} / {items.length}
          </span>
        </div>

        {loading
          ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          : filtered.length === 0
            ? <InlineEmpty>{isAr ? 'لا توجد أدلة — أضف أول دليل' : 'No evidence yet — add your first item'}</InlineEmpty>
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'العنوان' : 'Title'}</th>
                    <th>{isAr ? 'النوع' : 'Type'}</th>
                    <th>{isAr ? 'الإصدار' : 'Version'}</th>
                    <th>{isAr ? 'انتهاء الصلاحية' : 'Valid to'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th className="esg-text-end">{isAr ? 'الإجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(e => (
                    <tr key={e.id}>
                      <td className="mono text-2xs">{e.evidenceCode ?? `#${e.id}`}</td>
                      <td>
                        <div className="esg-cell-title">{isAr && e.titleAr ? e.titleAr : e.titleEn}</div>
                        {e.fileName
                          ? <div className="text-2xs text-muted mono esg-cell-sub">
                              <Paperclip size={9} className="esg-icon-inline" />
                              {e.fileName}{e.fileSize ? ` · ${Math.round(e.fileSize / 1024)} KB` : ''} · {isAr ? 'مجزّأ SHA-256' : 'SHA-256 ✓'}
                            </div>
                          : <div className="text-2xs esg-date-due-soon esg-cell-sub">{isAr ? 'لا ملف بعد' : 'no file yet'}</div>}
                      </td>
                      <td className="text-xs">{e.evidenceType ?? '—'}</td>
                      <td className="mono text-2xs">{e.version ?? '—'}</td>
                      <td className="mono text-2xs">{e.validTo ?? '—'}</td>
                      <td><Badge tone={evidenceItemStatusTone(e.status)}>{evidenceItemStatusLabel(e.status, isAr ? 'ar' : 'en')}</Badge></td>
                      <td className="esg-text-end">
                        <div className="esg-actions-row">
                          {canSubmitEvidence(e.status) && (
                            <>
                              <Button size="sm" variant="ghost" Icon={Upload}
                                title={isAr ? 'رفع ملف' : 'Upload file'} onClick={() => triggerUpload(e.id)} />
                              <Button size="sm" variant="secondary" Icon={ArrowRight} onClick={() => doSubmit(e.id)}>
                                {isAr ? 'إرسال' : 'Submit'}
                              </Button>
                            </>
                          )}
                          {e.status === 'PENDING_REVIEW' && (
                            <>
                              <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => doApprove(e.id)}>
                                {isAr ? 'اعتماد' : 'Approve'}
                              </Button>
                              <Button size="sm" variant="ghost" Icon={AlertCircle}
                                title={isAr ? 'رفض' : 'Reject'} onClick={() => doReject(e.id)} />
                            </>
                          )}
                          <Button size="sm" variant="ghost" Icon={Link2}
                            title={isAr ? 'ربط بكيان ESG' : 'Link to ESG entity'} onClick={() => setLinkTarget(e)} />
                          <Button size="sm" variant="ghost" Icon={Trash2}
                            title={isAr ? 'حذف' : 'Delete'} onClick={() => doDelete(e.id)} />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
      </Card>

      <FormModal
        open={createOpen}
        title={isAr ? 'إضافة دليل جديد' : 'Add new evidence'}
        fields={createFields}
        initial={{ titleEn: '', titleAr: '', evidenceType: 'DOCUMENT', version: '', validFrom: '', validTo: '', descriptionEn: '', file: null }}
        size="lg"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        onClose={() => setCreateOpen(false)}
        onSubmit={submitCreate}
      />
      <FormModal
        open={linkTarget !== null}
        title={isAr ? 'ربط الدليل بكيان استدامة' : 'Link evidence to an ESG entity'}
        fields={linkFields}
        initial={{ entityType: 'CmaDisclosure', entityId: '', notes: '' }}
        size="md"
        submitLabel={isAr ? 'ربط' : 'Link'}
        onClose={() => setLinkTarget(null)}
        onSubmit={submitLink}
      />
    </div>
  );
}
