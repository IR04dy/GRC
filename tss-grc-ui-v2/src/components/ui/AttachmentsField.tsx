// Reusable, typed attachments field — drop into any detail/edit surface to let
// users attach & preview documents (plan / report / policy / certificate / …)
// against an entity. Wired to the real generic attachments API.
import { useCallback, useEffect, useRef, useState, type ChangeEvent } from 'react';
import { Paperclip, Download, Trash2, Upload } from 'lucide-react';
import { Button } from './Button';
import { Badge } from './Badge';
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
  const [items, setItems] = useState<AttachmentDto[]>([]);
  const [docType, setDocType] = useState<AttachmentDocType>('PLAN');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    attachmentsApi.list(entityType, entityId).then(setItems).catch(() => setItems([]));
  }, [entityType, entityId]);
  useEffect(() => { load(); }, [load]);

  async function onFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setBusy(true);
    try {
      await attachmentsApi.upload(entityType, entityId, file, { docType });
      toast.success(ar ? 'تم إرفاق الملف' : 'File attached');
      load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر رفع الملف' : 'Upload failed'));
    } finally { setBusy(false); }
  }

  async function remove(a: AttachmentDto) {
    try { await attachmentsApi.remove(a.id); toast.success(ar ? 'تم الحذف' : 'Removed'); load(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر الحذف' : 'Delete failed')); }
  }

  const today = new Date().toISOString().slice(0, 10);

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: 700 }}>
          <Paperclip size={15} /> {label ?? (ar ? 'المرفقات' : 'Attachments')}
          <Badge tone="neutral">{items.length}</Badge>
        </div>
        <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <select
            value={docType}
            onChange={e => setDocType(e.currentTarget.value as AttachmentDocType)}
            style={{ padding: '6px 8px', borderRadius: 6, border: '1px solid var(--border)', fontSize: 12, background: 'var(--surface)' }}
            aria-label={ar ? 'نوع المستند' : 'Document type'}
          >
            {ATTACHMENT_DOC_TYPES.map(t => (
              <option key={t} value={t}>{ar ? DOC_TYPE_LABELS[t].ar : DOC_TYPE_LABELS[t].en}</option>
            ))}
          </select>
          <Button size="sm" variant="primary" Icon={Upload} disabled={busy}
            onClick={() => fileRef.current?.click()}>
            {busy ? (ar ? 'جارٍ الرفع…' : 'Uploading…') : (ar ? 'إرفاق ملف' : 'Attach file')}
          </Button>
        </div>
      </div>

      {items.length === 0 ? (
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
                  <Button size="sm" variant="ghost" Icon={Download} title={ar ? 'تنزيل' : 'Download'}
                    onClick={() => { void attachmentsApi.download(a); }} />
                  <Button size="sm" variant="ghost" Icon={Trash2} title={ar ? 'حذف' : 'Delete'}
                    onClick={() => { void remove(a); }} />
                </span>
              </div>
            );
          })}
        </div>
      )}

      <input ref={fileRef} type="file" hidden onChange={onFile}
        accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.txt,.csv,.json,.xml,.zip" />
    </div>
  );
}
