// Reusable, typed attachments field — drop into any detail/edit surface to let
// users attach & preview documents (plan / report / policy / certificate / …)
// against an entity. Wired to the real generic attachments API.
//
// Upload uses a small staging panel: pick a file, then name it, type it, and
// optionally set an expiry ("valid until") before confirming. Expired
// attachments expose a "Re-collect" action that re-opens the picker pre-seeded
// with the same type/title so the fresh copy supersedes the stale one.
import { useCallback, useEffect, useRef, useState, type ChangeEvent, type CSSProperties, type DragEvent } from 'react';
import { Paperclip, Download, Trash2, Upload, RefreshCw, X, Pencil, History, Layers } from 'lucide-react';
import { Button } from './Button';
import { Badge } from './Badge';
import { ConfirmDialog } from './ConfirmDialog';
import { Modal } from './Modal';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import {
  attachmentsApi, ATTACHMENT_DOC_TYPES, ATTACHMENT_CLASSIFICATIONS, ApiError,
  type AttachmentDto, type AttachmentDocType, type AttachmentClassification,
} from '../../lib/api';

const CLASS_LABELS: Record<AttachmentClassification, { en: string; ar: string }> = {
  PUBLIC:       { en: 'Public',       ar: 'عام' },
  INTERNAL:     { en: 'Internal',     ar: 'داخلي' },
  CONFIDENTIAL: { en: 'Confidential', ar: 'سرّي' },
  RESTRICTED:   { en: 'Restricted',   ar: 'مقيَّد' },
};
const CLASS_TONE: Record<AttachmentClassification, 'success' | 'info' | 'warning' | 'danger'> = {
  PUBLIC: 'success', INTERNAL: 'info', CONFIDENTIAL: 'warning', RESTRICTED: 'danger',
};

const DOC_TYPE_LABELS: Record<AttachmentDocType, { en: string; ar: string }> = {
  PLAN:          { en: 'Plan',          ar: 'خطة' },
  REPORT:        { en: 'Report',        ar: 'تقرير' },
  POLICY:        { en: 'Policy',        ar: 'سياسة' },
  CERTIFICATE:   { en: 'Certificate',   ar: 'شهادة' },
  EVIDENCE:      { en: 'Evidence',      ar: 'دليل' },
  CONTRACT:      { en: 'Contract',      ar: 'عقد' },
  MINUTES:       { en: 'Minutes',       ar: 'محضر' },
  QUESTIONNAIRE: { en: 'Questionnaire', ar: 'استبيان' },
  SCREENSHOT:    { en: 'Screenshot',    ar: 'لقطة شاشة' },
  OTHER:         { en: 'Other',         ar: 'أخرى' },
};

// Mirror the backend allow-list + 50 MB cap so drag-drop (which bypasses the
// file picker's `accept` filter) fails fast with a clear message.
export const MAX_BYTES = 50 * 1024 * 1024;
export const ALLOWED_EXT = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'png', 'jpg', 'jpeg', 'gif', 'txt', 'csv', 'json', 'xml', 'zip'];
/** True if a file passes the extension allow-list + size cap. */
export function isAllowedFile(file: File): boolean {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  return ALLOWED_EXT.includes(ext) && file.size <= MAX_BYTES;
}

function fmtBytes(n?: number): string {
  if (!n && n !== 0) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

const inputStyle: CSSProperties = {
  padding: '6px 8px', borderRadius: 6, border: '1px solid var(--border)',
  fontSize: 12, background: 'var(--surface)', color: 'var(--ink-1)',
};

export interface AttachmentsFieldProps {
  entityType: string;
  entityId: number;
  /** Optional heading; defaults to a localized "Attachments". */
  label?: string;
}

export function AttachmentsField({ entityType, entityId, label }: AttachmentsFieldProps) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const seedRef = useRef<{ docType: AttachmentDocType; title: string; classification?: AttachmentClassification | '' } | null>(null);
  const [items, setItems] = useState<AttachmentDto[]>([]);
  const [loading, setLoading] = useState(true);
  // staging panel (a file has been picked but not yet uploaded)
  const [staged, setStaged] = useState<File | null>(null);
  const [sDocType, setSDocType] = useState<AttachmentDocType>('PLAN');
  const [sTitle, setSTitle] = useState('');
  const [sValidTo, setSValidTo] = useState('');
  const [sClass, setSClass] = useState<AttachmentClassification | ''>('');
  const [queue, setQueue] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [dragging, setDragging] = useState(false);
  // metadata edit
  const [editing, setEditing] = useState<AttachmentDto | null>(null);
  const [eDocType, setEDocType] = useState<AttachmentDocType>('OTHER');
  const [eTitle, setETitle] = useState('');
  const [eValidTo, setEValidTo] = useState('');
  const [eClass, setEClass] = useState<AttachmentClassification | ''>('');
  // delete confirmation
  const [confirmDel, setConfirmDel] = useState<AttachmentDto | null>(null);
  const [deleting, setDeleting] = useState(false);
  // versioning
  const [replaceTarget, setReplaceTarget] = useState<AttachmentDto | null>(null);
  const [historyOf, setHistoryOf] = useState<AttachmentDto | null>(null);
  const [historyRows, setHistoryRows] = useState<AttachmentDto[]>([]);

  const load = useCallback(() => {
    setLoading(true);
    attachmentsApi.list(entityType, entityId)
      .then(setItems).catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [entityType, entityId]);
  useEffect(() => { load(); }, [load]);

  /** Open the OS file picker, optionally seeding type/title for a re-collect. */
  function pick(seed?: { docType: AttachmentDocType; title: string; classification?: AttachmentClassification | '' }) {
    seedRef.current = seed ?? null;
    fileRef.current?.click();
  }

  /** Replace an attachment with a new version (supersedes the current one). */
  function pickReplace(a: AttachmentDto) {
    setReplaceTarget(a);
    seedRef.current = { docType: a.docType, title: a.titleEn ?? '', classification: a.classification ?? '' };
    fileRef.current?.click();
  }

  function openHistory(a: AttachmentDto) {
    setHistoryOf(a);
    attachmentsApi.history(a.id).then(setHistoryRows).catch(() => setHistoryRows([]));
  }

  function validate(file: File): boolean {
    const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
    if (!ALLOWED_EXT.includes(ext)) {
      toast.danger(ar ? `نوع الملف غير مسموح: .${ext}` : `File type not allowed: .${ext}`);
      return false;
    }
    if (file.size > MAX_BYTES) {
      toast.danger(ar ? 'حجم الملف يتجاوز 50 ميجابايت' : 'File exceeds the 50 MB limit');
      return false;
    }
    return true;
  }

  /** Move one file into the staging panel (applies any re-collect seed). */
  function stageOne(file: File) {
    const seed = seedRef.current;
    seedRef.current = null;
    const base = file.name.replace(/\.[^./\\]+$/, '');
    setStaged(file);
    setSDocType(seed?.docType ?? 'PLAN');
    setSTitle(seed?.title || base);
    setSValidTo('');
    setSClass(seed?.classification ?? '');
  }

  /** Validate a batch; stage the first valid file and queue the rest. */
  function stageFiles(files: File[]) {
    const ok = files.filter(validate);
    if (!ok.length) return;
    stageOne(ok[0]);
    setQueue(ok.slice(1));
  }

  function onFile(e: ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(e.target.files ?? []);
    e.target.value = '';
    if (picked.length) stageFiles(picked);
  }

  function onDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragging(false);
    if (busy || staged) return;
    stageFiles(Array.from(e.dataTransfer.files ?? []));
  }

  function cancelStaged() { setStaged(null); setQueue([]); setReplaceTarget(null); }

  async function confirmUpload() {
    if (!staged) return;
    const opts = { docType: sDocType, title: sTitle.trim() || undefined, validTo: sValidTo || undefined,
      classification: sClass || undefined };
    setBusy(true);
    try {
      if (replaceTarget) {
        await attachmentsApi.newVersion(replaceTarget.id, staged, opts);
        toast.success(ar ? 'تم رفع نسخة جديدة' : 'New version uploaded');
        setReplaceTarget(null); setStaged(null); setQueue([]);
        load();
      } else {
        await attachmentsApi.upload(entityType, entityId, staged, opts);
        toast.success(ar ? 'تم إرفاق الملف' : 'File attached');
        load();
        // Advance to the next queued file, if any.
        if (queue.length) { const [next, ...rest] = queue; setQueue(rest); stageOne(next); }
        else setStaged(null);
      }
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر الرفع' : 'Upload failed'));
    } finally { setBusy(false); }
  }

  async function remove(a: AttachmentDto) {
    setDeleting(true);
    try { await attachmentsApi.remove(a.id); toast.success(ar ? 'تم الحذف' : 'Removed'); setConfirmDel(null); load(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر الحذف' : 'Delete failed')); }
    finally { setDeleting(false); }
  }

  function startEdit(a: AttachmentDto) {
    setEditing(a);
    setEDocType(a.docType);
    setETitle(a.titleEn ?? '');
    setEValidTo(a.validTo ?? '');
    setEClass(a.classification ?? '');
  }

  async function saveEdit() {
    if (!editing) return;
    setBusy(true);
    try {
      // '' clears the classification server-side; a value sets it.
      await attachmentsApi.update(editing.id, { docType: eDocType, titleEn: eTitle.trim(), validTo: eValidTo || undefined, classification: eClass });
      toast.success(ar ? 'تم تحديث المرفق' : 'Attachment updated');
      setEditing(null);
      load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر التحديث' : 'Update failed'));
    } finally { setBusy(false); }
  }

  const today = new Date().toISOString().slice(0, 10);

  return (
    <div
      onDragOver={e => { if (!busy && !staged) { e.preventDefault(); setDragging(true); } }}
      onDragLeave={e => { if (e.currentTarget === e.target) setDragging(false); }}
      onDrop={onDrop}
      style={{
        outline: dragging ? '2px dashed var(--primary, #2563eb)' : 'none',
        outlineOffset: 4, borderRadius: 8, transition: 'outline-color .1s',
      }}
    >
      {dragging && (
        <div style={{ fontSize: 12, color: 'var(--primary, #2563eb)', fontWeight: 600, padding: '2px 0 6px' }}>
          {ar ? 'أفلِت الملف للإرفاق…' : 'Drop the file to attach…'}
        </div>
      )}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: 700 }}>
          <Paperclip size={15} /> {label ?? (ar ? 'المرفقات' : 'Attachments')}
          <Badge tone="neutral">{items.length}</Badge>
        </div>
        <Button size="sm" variant="primary" Icon={Upload} disabled={busy || !!staged}
          onClick={() => pick()}>
          {ar ? 'إرفاق ملف' : 'Attach file'}
        </Button>
      </div>

      {/* Staging panel — name/type/expiry before the upload is committed. */}
      {staged && (
        <div style={{
          display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 10, padding: 10,
          border: '1px solid var(--primary-200, var(--border))', borderRadius: 8, background: 'var(--surface-2)',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
            <Paperclip size={13} style={{ color: 'var(--ink-3)' }} />
            <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>{staged.name}</span>
            <span className="mono" style={{ color: 'var(--ink-3)' }}>{fmtBytes(staged.size)}</span>
            {queue.length > 0 && (
              <Badge tone="info">{ar ? `+${queue.length} في الانتظار` : `+${queue.length} queued`}</Badge>
            )}
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
            <select value={sDocType} onChange={e => setSDocType(e.currentTarget.value as AttachmentDocType)}
              aria-label={ar ? 'نوع المستند' : 'Document type'} style={inputStyle}>
              {ATTACHMENT_DOC_TYPES.map(t => (
                <option key={t} value={t}>{tlabel(DOC_TYPE_LABELS, t, ar)}</option>
              ))}
            </select>
            <input value={sTitle} onChange={e => setSTitle(e.currentTarget.value)}
              placeholder={ar ? 'عنوان (اختياري)' : 'Title (optional)'}
              aria-label={ar ? 'العنوان' : 'Title'} style={{ ...inputStyle, flex: 1, minWidth: 140 }} />
            <input type="date" value={sValidTo} min={today} onChange={e => setSValidTo(e.currentTarget.value)}
              title={ar ? 'صالح حتى (اختياري — للشهادات والعقود)' : 'Valid until (optional — for certs/contracts)'}
              aria-label={ar ? 'صالح حتى' : 'Valid until'}
              style={{ ...inputStyle, color: sValidTo ? 'var(--ink-1)' : 'var(--ink-3)' }} />
            <select value={sClass} onChange={e => setSClass(e.currentTarget.value as AttachmentClassification | '')}
              title={ar ? 'التصنيف (اختياري)' : 'Classification (optional)'}
              aria-label={ar ? 'التصنيف' : 'Classification'}
              style={{ ...inputStyle, color: sClass ? 'var(--ink-1)' : 'var(--ink-3)' }}>
              <option value="">{ar ? 'التصنيف…' : 'Classification…'}</option>
              {ATTACHMENT_CLASSIFICATIONS.map(c => (
                <option key={c} value={c}>{tlabel(CLASS_LABELS, c, ar)}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button size="sm" variant="ghost" Icon={X} disabled={busy} onClick={cancelStaged}>
              {queue.length > 0 ? (ar ? 'إلغاء الكل' : 'Cancel all') : (ar ? 'إلغاء' : 'Cancel')}
            </Button>
            <Button size="sm" variant="primary" Icon={Upload} disabled={busy} onClick={() => { void confirmUpload(); }}>
              {busy ? (ar ? 'جارٍ الرفع…' : 'Uploading…')
                : queue.length > 0 ? (ar ? 'رفع والتالي' : 'Upload & next')
                : (ar ? 'رفع' : 'Upload')}
            </Button>
          </div>
        </div>
      )}

      {/* Metadata editor — re-type / rename / re-date without re-uploading. */}
      {editing && (
        <div style={{
          display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 10, padding: 10,
          border: '1px solid var(--warning-300, var(--border))', borderRadius: 8, background: 'var(--surface-2)',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
            <Pencil size={13} style={{ color: 'var(--ink-3)' }} />
            <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {ar ? 'تعديل: ' : 'Editing: '}{editing.fileName}
            </span>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
            <select value={eDocType} onChange={e => setEDocType(e.currentTarget.value as AttachmentDocType)}
              aria-label={ar ? 'نوع المستند' : 'Document type'} style={inputStyle}>
              {ATTACHMENT_DOC_TYPES.map(t => (
                <option key={t} value={t}>{tlabel(DOC_TYPE_LABELS, t, ar)}</option>
              ))}
            </select>
            <input value={eTitle} onChange={e => setETitle(e.currentTarget.value)}
              placeholder={ar ? 'عنوان (اختياري)' : 'Title (optional)'}
              aria-label={ar ? 'العنوان' : 'Title'} style={{ ...inputStyle, flex: 1, minWidth: 140 }} />
            <input type="date" value={eValidTo} onChange={e => setEValidTo(e.currentTarget.value)}
              aria-label={ar ? 'صالح حتى' : 'Valid until'}
              style={{ ...inputStyle, color: eValidTo ? 'var(--ink-1)' : 'var(--ink-3)' }} />
            <select value={eClass} onChange={e => setEClass(e.currentTarget.value as AttachmentClassification | '')}
              title={ar ? 'التصنيف' : 'Classification'}
              aria-label={ar ? 'التصنيف' : 'Classification'}
              style={{ ...inputStyle, color: eClass ? 'var(--ink-1)' : 'var(--ink-3)' }}>
              <option value="">{ar ? 'بدون تصنيف' : 'Unclassified'}</option>
              {ATTACHMENT_CLASSIFICATIONS.map(c => (
                <option key={c} value={c}>{tlabel(CLASS_LABELS, c, ar)}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button size="sm" variant="ghost" Icon={X} disabled={busy} onClick={() => setEditing(null)}>
              {ar ? 'إلغاء' : 'Cancel'}
            </Button>
            <Button size="sm" variant="primary" disabled={busy} onClick={() => { void saveEdit(); }}>
              {busy ? (ar ? 'جارٍ الحفظ…' : 'Saving…') : (ar ? 'حفظ' : 'Save')}
            </Button>
          </div>
        </div>
      )}

      {loading ? (
        <div style={{ fontSize: 12, color: 'var(--ink-3)', padding: '10px 0' }}>
          {ar ? 'جارٍ التحميل…' : 'Loading…'}
        </div>
      ) : items.length === 0 ? (
        <div style={{ fontSize: 12, color: 'var(--ink-3)', padding: '10px 0' }}>
          {ar ? 'لا توجد مرفقات بعد.' : 'No attachments yet.'}
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {items.map(a => {
            const expired = a.validTo && a.validTo < today;
            return (
              <div key={a.id} style={{
                display: 'grid', gridTemplateColumns: 'auto 1fr auto auto auto auto',
                gap: 10, alignItems: 'center', padding: '8px 10px',
                borderBottom: '1px solid var(--border)', background: 'var(--surface)', fontSize: 12,
              }}>
                <Badge tone="info">{tlabel(DOC_TYPE_LABELS, a.docType, ar)}</Badge>
                <div style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  <span style={{ fontWeight: 600 }}>{a.titleEn || a.fileName}</span>
                  {a.titleEn && <span style={{ color: 'var(--ink-3)' }}> · {a.fileName}</span>}
                  {a.version != null && a.version > 1 && (
                    <span className="mono" style={{ marginInlineStart: 6, fontSize: 10, color: 'var(--primary, #2563eb)', fontWeight: 700 }}>v{a.version}</span>
                  )}
                </div>
                <span style={{ color: 'var(--ink-3)' }} className="mono">
                  {fmtBytes(a.fileSize)}{a.createdAt ? ` · ${a.createdAt.slice(0, 10)}` : ''}
                </span>
                {a.classification
                  ? <Badge tone={CLASS_TONE[a.classification]}>{tlabel(CLASS_LABELS, a.classification, ar)}</Badge>
                  : <span />}
                {a.validTo
                  ? <Badge tone={expired ? 'danger' : 'neutral'}>{ar ? 'حتى ' : 'until '}{a.validTo}</Badge>
                  : <span />}
                <span style={{ display: 'inline-flex', gap: 4 }}>
                  {expired && (
                    <Button size="sm" variant="ghost" Icon={RefreshCw}
                      title={ar ? 'إعادة التجميع (رفع نسخة محدّثة)' : 'Re-collect (upload a fresh copy)'}
                      onClick={() => pick({ docType: a.docType, title: a.titleEn || '', classification: a.classification ?? '' })} />
                  )}
                  <Button size="sm" variant="ghost" Icon={Layers} title={ar ? 'رفع نسخة جديدة' : 'Upload new version'}
                    onClick={() => pickReplace(a)} />
                  {a.version != null && a.version > 1 && (
                    <Button size="sm" variant="ghost" Icon={History} title={ar ? 'سجل النسخ' : 'Version history'}
                      onClick={() => openHistory(a)} />
                  )}
                  <Button size="sm" variant="ghost" Icon={Pencil} title={ar ? 'تعديل البيانات' : 'Edit details'}
                    onClick={() => startEdit(a)} />
                  <Button size="sm" variant="ghost" Icon={Download} title={ar ? 'تنزيل' : 'Download'}
                    onClick={() => { void attachmentsApi.download(a); }} />
                  <Button size="sm" variant="ghost" Icon={Trash2} title={ar ? 'حذف' : 'Delete'}
                    onClick={() => setConfirmDel(a)} />
                </span>
              </div>
            );
          })}
        </div>
      )}

      <input ref={fileRef} type="file" multiple hidden onChange={onFile}
        accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.txt,.csv,.json,.xml,.zip" />

      <ConfirmDialog
        open={!!confirmDel}
        destructive
        busy={deleting}
        title={ar ? 'حذف المرفق' : 'Delete attachment'}
        message={confirmDel
          ? (ar ? `سيتم حذف «${confirmDel.titleEn || confirmDel.fileName}». لا يمكن التراجع.`
                : `“${confirmDel.titleEn || confirmDel.fileName}” will be deleted. This cannot be undone.`)
          : ''}
        confirmLabel={ar ? 'حذف' : 'Delete'}
        onCancel={() => setConfirmDel(null)}
        onConfirm={() => { if (confirmDel) void remove(confirmDel); }}
      />

      {historyOf && (
        <Modal open title={(ar ? 'سجل النسخ · ' : 'Version history · ') + (historyOf.titleEn || historyOf.fileName)}
          size="md" onClose={() => setHistoryOf(null)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {historyRows.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--ink-3)', padding: 8 }}>{ar ? 'لا توجد نسخ.' : 'No versions.'}</div>
            ) : historyRows.map((v, idx) => (
              <div key={v.id} style={{
                display: 'grid', gridTemplateColumns: 'auto 1fr auto auto', gap: 10, alignItems: 'center',
                padding: '8px 10px', borderBottom: '1px solid var(--border)', fontSize: 12,
              }}>
                <Badge tone={idx === 0 ? 'success' : 'neutral'}>v{v.version}{idx === 0 ? (ar ? ' · حالي' : ' · current') : ''}</Badge>
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{v.fileName}</span>
                <span className="mono" style={{ color: 'var(--ink-3)' }}>{v.createdAt?.slice(0, 10)}</span>
                <Button size="sm" variant="ghost" Icon={Download} title={ar ? 'تنزيل' : 'Download'}
                  onClick={() => { void attachmentsApi.download(v); }} />
              </div>
            ))}
          </div>
        </Modal>
      )}
    </div>
  );
}
