import { useEffect, useState, type FormEvent } from 'react';
import { Modal } from './Modal';
import { Input, Textarea } from './Input';
import { Select } from './Select';
import { Button } from './Button';
import { useI18n } from '../../lib/i18n';

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
  | (FieldCommon & { kind: 'select';   options: Array<{ value: string; label: string }>; required?: boolean });

interface FormModalProps<T> {
  open: boolean;
  title: string;
  fields: FieldDef[];
  initial: T;
  onClose: () => void;
  onSubmit: (values: T) => Promise<void> | void;
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
  const [values, setValues] = useState<T>(initial);
  const [submitting, setSubmitting] = useState(false);

  // Reset to `initial` whenever the modal opens. Keeps the form fresh between create / edit calls.
  useEffect(() => { if (open) setValues(initial); }, [open, initial]);

  async function handle(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    try { await onSubmit(values); } finally { setSubmitting(false); }
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
          }
        })}
      </form>
    </Modal>
  );
}
