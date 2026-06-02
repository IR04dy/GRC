import { useEffect, useRef, useState, type ChangeEvent, type DragEvent, type FormEvent } from 'react';
import { Paperclip, Upload, X } from 'lucide-react';
import { Modal } from './Modal';
import { Input, Textarea } from './Input';
import { Select } from './Select';
import { SearchableSelect, type SelectOption } from './SearchableSelect';
import { Button } from './Button';
import { AttachmentsField, isAllowedFile } from './AttachmentsField';
import { attachmentsApi, ATTACHMENT_DOC_TYPES, type AttachmentDocType } from '../../lib/api';

/** A create-mode staged file with its chosen document type. */
interface StagedFile { file: File; docType: AttachmentDocType; }
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';

/**
 * Span out of 12 (Bootstrap-style). 12 = full row, 6 = half, 4 = third, 3 = quarter.
 * `cols` is kept for backwards compatibility (cols=1 → span 6, cols=2 or undefined → span 12).
 */
type FieldCommon = {
  key: string;
  label: string;
  span?: 3 | 4 | 6 | 8 | 12;
  cols?: 1 | 2;
};
export type FieldDef =
  | (FieldCommon & { kind: 'text';     required?: boolean; maxLength?: number; placeholder?: string })
  | (FieldCommon & { kind: 'textarea'; rows?: number })
  | (FieldCommon & { kind: 'number';   min?: number; max?: number; required?: boolean })
  | (FieldCommon & { kind: 'date' })
  | (FieldCommon & { kind: 'select';   options: Array<{ value: string; label: string }>; required?: boolean })
  /**
   * Searchable select with optional "create new" affordance. Use when:
   *   - the option list is long (50+) and scanning is slow
   *   - the user often needs an entity that doesn't yet exist
   *
   * `onCreate` fires when the user picks the inline "+ Create new" row.
   * It should create the entity and return its new value/id so the field
   * can auto-select it. Return null to abort (validation failed etc.).
   */
  | (FieldCommon & {
      kind: 'select-searchable';
      options: SelectOption[];
      required?: boolean;
      placeholder?: string;
      onCreate?: (typedLabel: string) => Promise<string | null> | string | null;
      createLabel?: (typedLabel: string) => string;
      emptyHint?: string;
    })
  /**
   * Typed file attachments inside the form.
   *   - EDIT mode (entityId set): a live AttachmentsField that uploads
   *     immediately against the existing record.
   *   - CREATE mode (entityId omitted): files are staged in form state and
   *     uploaded after onSubmit resolves with the new record's id. Requires
   *     onSubmit to return `{ entityId }`.
   */
  | (FieldCommon & { kind: 'attachments'; entityType: string; entityId?: number });

/** onSubmit may return the created record's id so staged attachments can link to it. */
export type FormSubmitResult = void | { entityId?: number };

interface FormModalProps<T> {
  open: boolean;
  title: string;
  fields: FieldDef[];
  initial: T;
  onClose: () => void;
  onSubmit: (values: T) => Promise<FormSubmitResult> | FormSubmitResult;
  submitLabel?: string;
  /** Modal width preset. Defaults to "lg" (880px) so forms breathe. */
  size?: 'sm' | 'md' | 'lg';
}

function spanFor(f: FieldDef): number {
  if (f.span) return f.span;
  if (f.cols === 1) return 6;
  return 12;
}

export function FormModal<T>({
  open, title, fields, initial, onClose, onSubmit, submitLabel, size = 'lg',
}: FormModalProps<T>) {
  const { t } = useI18n();
  const toast = useToast();
  const [values, setValues] = useState<T>(initial);
  const [submitting, setSubmitting] = useState(false);

  // Reset to `initial` whenever the modal opens. Keeps the form fresh between create / edit calls.
  useEffect(() => { if (open) setValues(initial); }, [open, initial]);

  async function handle(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try {
      const result = await onSubmit(values);
      // CREATE-mode attachments: upload staged files once we know the new id.
      const newId = result && typeof result === 'object' ? result.entityId : undefined;
      const bag = values as unknown as Record<string, unknown>;
      const pending = fields.filter(
        (f): f is Extract<FieldDef, { kind: 'attachments' }> =>
          f.kind === 'attachments' && f.entityId == null,
      );
      if (pending.length && newId != null) {
        let failed = 0;
        for (const f of pending) {
          const staged = (bag[f.key] as StagedFile[] | undefined) ?? [];
          for (const s of staged) {
            try { await attachmentsApi.upload(f.entityType, newId, s.file, { docType: s.docType }); }
            catch { failed++; }
          }
        }
        if (failed > 0) toast.danger(`${failed} attachment(s) failed to upload — add them from the record's Documents tab.`);
      }
    } finally { setSubmitting(false); }
  }

  return (
    <Modal
      open={open}
      title={title}
      size={size}
      onClose={() => { if (!submitting) onClose(); }}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={submitting}>{t('cancel')}</Button>
          <Button type="submit" form="entity-form" disabled={submitting}>
            {submitting ? t('saving') : (submitLabel ?? t('save'))}
          </Button>
        </>
      }
    >
      <form
        id="entity-form"
        className="form-12col"
        onSubmit={handle}
        style={{ display: 'grid', gridTemplateColumns: 'repeat(12, 1fr)', gap: '14px 12px' }}
      >
        {fields.map(f => {
          const span = spanFor(f);
          const bag = values as unknown as Record<string, unknown>;
          const v = bag[f.key];
          const setV = (newValue: unknown) => setValues(prev => ({ ...(prev as object), [f.key]: newValue } as T));
          const wrap = (child: React.ReactNode) => <div key={f.key} style={{ gridColumn: `span ${span}` }}>{child}</div>;

          switch (f.kind) {
            case 'text':
              return wrap(
                <Input id={f.key} label={f.label}
                  required={f.required} maxLength={f.maxLength} placeholder={f.placeholder}
                  value={(v as string) ?? ''}
                  onChange={e => setV(e.currentTarget.value)} />
              );
            case 'textarea':
              return wrap(
                <Textarea id={f.key} label={f.label} rows={f.rows ?? 3}
                  value={(v as string) ?? ''}
                  onChange={e => setV(e.currentTarget.value)} />
              );
            case 'number':
              return wrap(
                <Input id={f.key} label={f.label}
                  type="number" min={f.min} max={f.max} required={f.required}
                  value={(v as number) ?? ''}
                  onChange={e => setV(Number(e.currentTarget.value))} />
              );
            case 'date':
              return wrap(
                <Input id={f.key} label={f.label} type="date"
                  value={(v as string) ?? ''}
                  onChange={e => setV(e.currentTarget.value)} />
              );
            case 'select':
              return wrap(
                <Select id={f.key} label={f.label}
                  required={f.required} options={f.options}
                  value={(v as string) ?? ''}
                  onChange={e => setV(e.currentTarget.value)} />
              );
            case 'select-searchable':
              return wrap(
                <SearchableSelect id={f.key} label={f.label}
                  required={f.required} options={f.options}
                  placeholder={f.placeholder}
                  onCreate={f.onCreate}
                  createLabel={f.createLabel}
                  emptyHint={f.emptyHint}
                  value={(v as string) ?? ''}
                  onChange={(next) => setV(next)} />
              );
            case 'attachments':
              return wrap(
                f.entityId != null
                  ? <AttachmentsField entityType={f.entityType} entityId={f.entityId} label={f.label} />
                  : <PendingFilesField label={f.label}
                      files={(v as StagedFile[] | undefined) ?? []}
                      onChange={(next) => setV(next)} />
              );
          }
        })}
      </form>
    </Modal>
  );
}

/**
 * CREATE-mode attachment staging: collect files (each with a chosen doc-type)
 * before the record exists. FormModal uploads them after onSubmit returns the id.
 */
function PendingFilesField({ label, files, onChange }: {
  label: string;
  files: StagedFile[];
  onChange: (files: StagedFile[]) => void;
}) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const ref = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  function accept(incoming: File[]) {
    const ok = incoming.filter(isAllowedFile);
    if (ok.length < incoming.length) {
      toast.danger(ar ? 'تم تجاهل ملفات غير مسموحة (النوع/الحجم 50 ميجابايت).' : 'Some files were skipped (type or 50 MB limit).');
    }
    if (ok.length) onChange([...files, ...ok.map(file => ({ file, docType: 'OTHER' as AttachmentDocType }))]);
  }

  function add(e: ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(e.target.files ?? []);
    e.target.value = '';
    accept(picked);
  }

  function onDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragging(false);
    accept(Array.from(e.dataTransfer.files ?? []));
  }

  return (
    <div
      onDragOver={e => { e.preventDefault(); setDragging(true); }}
      onDragLeave={e => { if (e.currentTarget === e.target) setDragging(false); }}
      onDrop={onDrop}
      style={{ outline: dragging ? '2px dashed var(--primary, #2563eb)' : 'none', outlineOffset: 4, borderRadius: 8 }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 600, color: 'var(--ink-2)' }}>
          <Paperclip size={13} /> {label}
        </span>
        <Button size="sm" variant="secondary" Icon={Upload} onClick={() => ref.current?.click()}>
          {ar ? 'إضافة ملفات' : 'Add files'}
        </Button>
      </div>
      {files.length === 0 ? (
        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
          {ar ? 'ستُرفع الملفات بعد حفظ السجل.' : 'Files upload after the record is saved.'}
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
          {files.map((s, i) => (
            <div key={`${s.file.name}-${i}`} style={{
              display: 'flex', alignItems: 'center', gap: 8, fontSize: 12,
              padding: '4px 8px', borderRadius: 6, background: 'var(--surface-2)',
            }}>
              <Paperclip size={12} style={{ color: 'var(--ink-3)' }} />
              <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{s.file.name}</span>
              <select
                value={s.docType}
                aria-label={ar ? 'نوع المستند' : 'Document type'}
                onChange={e => onChange(files.map((x, j) => j === i ? { ...x, docType: e.currentTarget.value as AttachmentDocType } : x))}
                style={{ fontSize: 11, padding: '1px 4px', borderRadius: 6, border: '1px solid var(--border)', background: 'var(--surface)' }}>
                {ATTACHMENT_DOC_TYPES.map(dt => <option key={dt} value={dt}>{dt}</option>)}
              </select>
              <button type="button" aria-label="remove"
                onClick={() => onChange(files.filter((_, j) => j !== i))}
                style={{ border: 'none', background: 'none', cursor: 'pointer', color: 'var(--ink-3)', display: 'inline-flex' }}>
                <X size={13} />
              </button>
            </div>
          ))}
        </div>
      )}
      <input ref={ref} type="file" multiple hidden onChange={add}
        accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.txt,.csv,.json,.xml,.zip" />
    </div>
  );
}
