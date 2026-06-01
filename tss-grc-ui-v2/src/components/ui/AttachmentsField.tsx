// Reusable, typed attachments field — drop into any detail/edit surface to let
// users attach & preview documents (plan / report / policy / certificate / …)
// against an entity. Wired to the real generic attachments API.
//
// Upload uses a small staging panel: pick a file, then name it, type it, and
// optionally set an expiry ("valid until") before confirming. Expired
// attachments expose a "Re-collect" action that re-opens the picker pre-seeded
// with the same type/title so the fresh copy supersedes the stale one.
import { useCallback, useEffect, useRef, useState, type ChangeEvent, type CSSProperties } from 'react';
import { Paperclip, Download, Trash2, Upload, RefreshCw, X } from 'lucide-react';
import { Button } from './Button';
import { Badge } from './Badge';
import { ConfirmDialog } from './ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  attachmentsApi, ATTACHMENT_DOC_TYPES, ApiError,
  type AttachmentDto, type AttachmentDocType,
} from '../../lib/api';

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
  const seedRef = useRef<{ docType: AttachmentDocType; title: string } | null>(null);
  const [items, setItems] = useState<AttachmentDto[]>([]);
  const [loading, setLoading] = useState(true);
  // staging panel (a file has been picked but not yet uploaded)
  const [staged, setStaged] = useState<File | null>(null);
  const [sDocType, setSDocType] = useState<AttachmentDocType>('PLAN');
  const [sTitle, setSTitle] = useState('');
  const [sValidTo, setSValidTo] = useState('');
  const [busy, setBusy] = useState(false);
  // delete confirmation
  const [confirmDel, setConfirmDel] = useState<AttachmentDto | null>(null);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    attachmentsApi.list(entityType, entityId)
      .then(setItems).catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [entityType, entityId]);
  useEffect(() => { load(); }, [load]);

  /** Open the OS file picker, optionally seeding type/title for a re-collect. */
  function pick(seed?: { docType: AttachmentDocType; title: string }) {
    seedRef.current = seed ?? null;
    fileRef.current?.click();
  }

  function onFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const seed = seedRef.current;
    seedRef.current = null;
    const base = file.name.replace(/\.[^./\\]+$/, '');
    setStaged(file);
    setSDocType(seed?.docType ?? 'PLAN');
    setSTitle(seed?.title || base);
    setSValidTo('');
  }

  async function confirmUpload() {
    if (!staged) return;
    setBusy(true);
    try {
      await attachmentsApi.upload(entityType, entityId, staged, {
        docType: sDocType,
        title: sTitle.trim() || undefined,
        validTo: sValidTo || undefined,
      });
      toast.success(ar ? 'تم إرفاق الملف' : 'File attached');
      setStaged(null);
      load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر رفع الملف' : 'Upload failed'));
    } finally { setBusy(false); }
  }

  async function remove(a: AttachmentDto) {
    setDeleting(true);
    try { await attachmentsApi.remove(a.id); toast.success(ar ? 'تم الحذف' : 'Removed'); setConfirmDel(null); load(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر الحذف' : 'Delete failed')); }
    finally { setDeleting(false); }
  }

  const today = new Date().toISOString().slice(0, 10);

  return (
    <div>
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
            <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{staged.name}</span>
            <span className="mono" style={{ color: 'var(--ink-3)' }}>{fmtBytes(staged.size)}</span>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
            <select value={sDocType} onChange={e => setSDocType(e.currentTarget.value as AttachmentDocType)}
              aria-label={ar ? 'نوع المستند' : 'Document type'} style={inputStyle}>
              {ATTACHMENT_DOC_TYPES.map(t => (
                <option key={t} value={t}>{ar ? DOC_TYPE_LABELS[t].ar : DOC_TYPE_LABELS[t].en}</option>
              ))}
            </select>
            <input value={sTitle} onChange={e => setSTitle(e.currentTarget.value)}
              placeholder={ar ? 'عنوان (اختياري)' : 'Title (optional)'}
              aria-label={ar ? 'العنوان' : 'Title'} style={{ ...inputStyle, flex: 1, minWidth: 140 }} />
            <input type="date" value={sValidTo} min={today} onChange={e => setSValidTo(e.currentTarget.value)}
              title={ar ? 'صالح حتى (اختياري — للشهادات والعقود)' : 'Valid until (optional — for certs/contracts)'}
              aria-label={ar ? 'صالح حتى' : 'Valid until'}
              style={{ ...inputStyle, color: sValidTo ? 'var(--ink-1)' : 'var(--ink-3)' }} />
          </div>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button size="sm" variant="ghost" Icon={X} disabled={busy} onClick={() => setStaged(null)}>
              {ar ? 'إلغاء' : 'Cancel'}
            </Button>
            <Button size="sm" variant="primary" Icon={Upload} disabled={busy} onClick={() => { void confirmUpload(); }}>
              {busy ? (ar ? 'جارٍ الرفع…' : 'Uploading…') : (ar ? 'رفع' : 'Upload')}
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
                display: 'grid', gridTemplateColumns: 'auto 1fr auto auto auto',
                gap: 10, alignItems: 'center', padding: '8px 10px',
                borderBottom: '1px solid var(--border)', background: 'var(--surface)', fontSize: 12,
              }}>
                <Badge tone="info">{ar ? DOC_TYPE_LABELS[a.docType].ar : DOC_TYPE_LABELS[a.docType].en}</Badge>
                <div style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  <span style={{ fontWeight: 600 }}>{a.titleEn || a.fileName}</span>
                  {a.titleEn && <span style={{ color: 'var(--ink-3)' }}> · {a.fileName}</span>}
                </div>
                <span style={{ color: 'var(--ink-3)' }} className="mono">{fmtBytes(a.fileSize)}</span>
                {a.validTo
                  ? <Badge tone={expired ? 'danger' : 'neutral'}>{ar ? 'حتى ' : 'until '}{a.validTo}</Badge>
                  : <span />}
                <span style={{ display: 'inline-flex', gap: 4 }}>
                  {expired && (
                    <Button size="sm" variant="ghost" Icon={RefreshCw}
                      title={ar ? 'إعادة التجميع (رفع نسخة محدّثة)' : 'Re-collect (upload a fresh copy)'}
                      onClick={() => pick({ docType: a.docType, title: a.titleEn || '' })} />
                  )}
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

      <input ref={fileRef} type="file" hidden onChange={onFile}
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
    </div>
  );
}
