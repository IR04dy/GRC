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
  | (FieldCommon & { kind: 'textarea'; rows?: number; required?: boolean })
  | (FieldCommon & { kind: 'number';   min?: number; max?: number; required?: boolean })
  | (FieldCommon & { kind: 'date'; required?: boolean })
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
  | (FieldCommon & { kind: 'attachments'; entityType: string; entityId?: number })
  /**
   * A single staged file. Unlike `attachments`, this does NOT auto-upload
   * anywhere: it just parks one `File` (or null) in `values[key]`. The consumer
   * reads it in onSubmit and uploads it wherever it belongs — e.g. the evidence
   * engine's own `/file` endpoint that computes a SHA-256. Use when the target
   * store is not the generic attachments API.
   */
  | (FieldCommon & { kind: 'file'; accept?: string; required?: boolean; hint?: string });

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
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [values, setValues] = useState<T>(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  // Reset to `initial` whenever the modal opens. Keeps the form fresh between create / edit calls.
  useEffect(() => { if (open) { setValues(initial); setErrors({}); } }, [open, initial]);

  // Per-field validation — required (all kinds), plus number min/max and text
  // maxLength. Returns a key→message map so the fields can show inline errors
  // (aria-invalid + role="alert") instead of only the native browser bubble.
  function validate(): Record<string, string> {
    const bag = values as unknown as Record<string, unknown>;
    const errs: Record<string, string> = {};
    for (const f of fields) {
      if (f.kind === 'attachments') continue;
      const fv = bag[f.key];
      const empty = fv === undefined || fv === null || fv === '' || (typeof fv === 'number' && Number.isNaN(fv));
      const req = (f as { required?: boolean }).required;
      if (req && empty) {
        errs[f.key] = isAr ? 'هذا الحقل مطلوب' : 'This field is required';
        continue;
      }
      if (f.kind === 'number' && !empty) {
        const num = Number(fv);
        if (f.min != null && num < f.min) errs[f.key] = isAr ? `الحد الأدنى ${f.min}` : `Must be ≥ ${f.min}`;
        else if (f.max != null && num > f.max) errs[f.key] = isAr ? `الحد الأقصى ${f.max}` : `Must be ≤ ${f.max}`;
      }
      if (f.kind === 'text' && f.maxLength != null && typeof fv === 'string' && fv.length > f.maxLength) {
        errs[f.key] = isAr ? `الحد ${f.maxLength} حرفًا` : `Max ${f.maxLength} characters`;
      }
    }
    return errs;
  }

  async function handle(e: FormEvent) {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length > 0) {
      setErrors(errs);
      const firstKey = fields.find(f => errs[f.key])?.key;
      if (firstKey) requestAnimationFrame(() => document.getElementById(firstKey)?.focus());
      return;
    }
    setErrors({});
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
        noValidate
        onSubmit={handle}
        style={{ display: 'grid', gridTemplateColumns: 'repeat(12, 1fr)', gap: '14px 16px' }}
      >
        {fields.map(f => {
          const span = spanFor(f);
          const bag = values as unknown as Record<string, unknown>;
          const v = bag[f.key];
          const setV = (newValue: unknown) => {
            setValues(prev => ({ ...(prev as object), [f.key]: newValue } as T));
            // Clear the field's error as soon as the user edits it.
            setErrors(prev => (prev[f.key] ? { ...prev, [f.key]: '' } : prev));
          };
          const fieldErr = errors[f.key] || undefined;
          const wrap = (child: React.ReactNode) => <div key={f.key} style={{ gridColumn: `span ${span}` }}>{child}</div>;

          switch (f.kind) {
            case 'text':
              return wrap(
                <Input id={f.key} label={f.label} error={fieldErr}
                  required={f.required} maxLength={f.maxLength} placeholder={f.placeholder}
                  value={(v as string) ?? ''}
                  onChange={e => setV(e.currentTarget.value)} />
              );
            case 'textarea':
              return wrap(
                <Textarea id={f.key} label={f.label} error={fieldErr} rows={f.rows ?? 3}
                  required={f.required}
                  value={(v as string) ?? ''}
                  onChange={e => setV(e.currentTarget.value)} />
              );
            case 'number':
              return wrap(
                <Input id={f.key} label={f.label} error={fieldErr}
                  type="number" min={f.min} max={f.max} required={f.required}
                  value={(v as number) ?? ''}
                  onChange={e => setV(Number(e.currentTarget.value))} />
              );
            case 'date':
              return wrap(
                <Input id={f.key} label={f.label} type="date" error={fieldErr}
                  required={f.required}
                  value={(v as string) ?? ''}
                  onChange={e => setV(e.currentTarget.value)} />
              );
            case 'select':
              return wrap(
                <Select id={f.key} label={f.label} error={fieldErr}
                  required={f.required} options={f.options}
                  value={(v as string) ?? ''}
                  onChange={e => setV(e.currentTarget.value)} />
              );
            case 'select-searchable':
              return wrap(
                <SearchableSelect id={f.key} label={f.label} error={fieldErr}
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
            case 'file':
              return wrap(
                <SingleFileField id={f.key} label={f.label} accept={f.accept} hint={f.hint}
                  required={f.required} error={fieldErr}
                  file={(v as File | null) ?? null}
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

/**
 * CREATE-mode single-file staging (the `file` field kind). Parks one `File` in
 * form state and does NOT upload — the consumer uploads it in onSubmit. Drag-drop
 * and the picker both run through `isAllowedFile` (type + 50 MB cap).
 */
function SingleFileField({ id, label, accept, hint, required, error, file, onChange }: {
  id: string; label: string; accept?: string; hint?: string; required?: boolean;
  error?: string; file: File | null; onChange: (f: File | null) => void;
}) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const ref = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  function take(picked: File[]) {
    const f = picked[0];
    if (!f) return;
    if (!isAllowedFile(f)) {
      toast.danger(ar ? 'نوع الملف غير مسموح أو يتجاوز 50 ميجابايت.' : 'File type not allowed, or exceeds the 50 MB limit.');
      return;
    }
    onChange(f);
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6, fontSize: 12, fontWeight: 600, color: 'var(--ink-2)' }}>
        <Paperclip size={13} /> {label}
        {required && <span style={{ color: 'var(--danger, #dc2626)' }}> *</span>}
      </div>
      <div
        onDragOver={e => { e.preventDefault(); setDragging(true); }}
        onDragLeave={e => { if (e.currentTarget === e.target) setDragging(false); }}
        onDrop={e => { e.preventDefault(); setDragging(false); take(Array.from(e.dataTransfer.files ?? [])); }}
        style={{
          display: 'flex', alignItems: 'center', gap: 10, padding: '8px 10px', borderRadius: 8,
          background: 'var(--surface-2)',
          border: `1px ${dragging ? 'dashed' : 'solid'} ${error ? 'var(--danger, #dc2626)' : dragging ? 'var(--primary, #2563eb)' : 'var(--border)'}`,
        }}
      >
        <Button size="sm" variant="secondary" Icon={Upload} onClick={() => ref.current?.click()}>
          {ar ? 'اختيار ملف' : 'Choose file'}
        </Button>
        {file ? (
          <>
            <Paperclip size={12} style={{ color: 'var(--ink-3)' }} />
            <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 12 }}>
              {file.name}
              <span className="mono" style={{ color: 'var(--ink-3)' }}> · {Math.round(file.size / 1024)} KB</span>
            </span>
            <button type="button" aria-label={ar ? 'إزالة' : 'remove'} onClick={() => onChange(null)}
              style={{ border: 'none', background: 'none', cursor: 'pointer', color: 'var(--ink-3)', display: 'inline-flex' }}>
              <X size={14} />
            </button>
          </>
        ) : (
          <span style={{ flex: 1, fontSize: 12, color: 'var(--ink-3)' }}>{ar ? 'لم يتم اختيار ملف' : 'No file chosen'}</span>
        )}
      </div>
      {(error || hint) && (
        <div role={error ? 'alert' : undefined} style={{ marginTop: 4, fontSize: 11, color: error ? 'var(--danger, #dc2626)' : 'var(--ink-3)' }}>
          {error || hint}
        </div>
      )}
      <input ref={ref} id={id} type="file" hidden accept={accept}
        onChange={e => { const picked = Array.from(e.target.files ?? []); e.target.value = ''; take(picked); }} />
    </div>
  );
}
