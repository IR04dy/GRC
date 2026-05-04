import { useState, useEffect } from 'react';
import { Filter, X as XIcon, RotateCcw } from 'lucide-react';
import { Modal } from './Modal';
import { Button } from './Button';
import { useI18n } from '../../lib/i18n';

// Generic register-page filter modal — DD-47.
//
// Until now, "Filter" on every workspace page just showed a toast.info
// hint (DD-39 stop-gap). Each register has its own bespoke filter
// dimensions, so a fully-typed modal would have to be page-specific.
// This component is the schema-aware middle ground: it takes a list
// of FilterField definitions and renders the right control per kind,
// then hands back a flat Record<string, string | undefined> on apply.
//
// Drives the page-header Filter button on CAPA, Cyber, BCM, Privacy,
// TPRM, Incidents, Evidence, SCM, etc.

export interface FilterFieldOption {
  value: string;
  label: string;
}

export interface FilterField {
  key: string;
  /** Localised label — caller passes the t() result already. */
  label: string;
  kind: 'select' | 'text' | 'date';
  /** For kind='select' — the list of options to choose from. The
      "all" / "any" case is added implicitly as an empty value. */
  options?: FilterFieldOption[];
  /** Optional placeholder for text/date kinds. */
  placeholder?: string;
}

export interface FilterModalProps {
  open: boolean;
  fields: FilterField[];
  value: Record<string, string | undefined>;
  onApply: (next: Record<string, string | undefined>) => void;
  onClose: () => void;
  /** Subtitle / description shown above the fields. */
  hint?: string;
}

export function FilterModal({ open, fields, value, onApply, onClose, hint }: FilterModalProps) {
  const { t } = useI18n();
  // Local draft so Cancel discards changes
  const [draft, setDraft] = useState<Record<string, string | undefined>>(() => ({ ...value }));

  useEffect(() => { if (open) setDraft({ ...value }); }, [open, value]);

  function update(key: string, v: string) {
    setDraft(prev => ({ ...prev, [key]: v === '' ? undefined : v }));
  }

  function clear() {
    setDraft({});
  }

  function apply() {
    onApply(draft);
    onClose();
  }

  const activeCount = Object.values(draft).filter(v => v !== undefined && v !== '').length;

  return (
    <Modal open={open} onClose={onClose} title={t('filterModalTitle')} size="md"
      footer={<>
        <Button variant="ghost" Icon={RotateCcw} onClick={clear}>{t('filterModalClear')}</Button>
        <div style={{ flex: 1 }} />
        <Button variant="ghost" onClick={onClose}>{t('actionCancel')}</Button>
        <Button variant="primary" Icon={Filter} onClick={apply}>
          {t('filterModalApply')}{activeCount > 0 ? ` (${activeCount})` : ''}
        </Button>
      </>}>
      {hint && (
        <div style={{
          padding: 12, marginBottom: 12,
          background: 'var(--surface-2)',
          borderRadius: 'var(--r-sm)',
          fontSize: 12,
          color: 'var(--ink-2)',
        }}>{hint}</div>
      )}
      <div style={{ display: 'grid', gap: 12 }}>
        {fields.map(f => (
          <FilterRow key={f.key} field={f} value={draft[f.key]} onChange={v => update(f.key, v)} />
        ))}
      </div>
    </Modal>
  );
}

function FilterRow({ field, value, onChange }: {
  field: FilterField;
  value: string | undefined;
  onChange: (v: string) => void;
}) {
  const { t } = useI18n();
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
        {field.label}
      </div>
      {field.kind === 'select' && (
        <select value={value ?? ''} onChange={e => onChange(e.target.value)}
          style={{
            width: '100%', padding: '8px 10px', fontSize: 13,
            border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            background: 'var(--surface-2)', color: 'var(--ink-1)',
          }}>
          <option value="">{t('filterModalAny')}</option>
          {(field.options ?? []).map(o => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
      )}
      {field.kind === 'text' && (
        <div style={{ position: 'relative' }}>
          <input type="text" value={value ?? ''} onChange={e => onChange(e.target.value)}
            placeholder={field.placeholder}
            style={{
              width: '100%', padding: '8px 10px', fontSize: 13,
              border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              background: 'var(--surface-2)', color: 'var(--ink-1)',
            }} />
          {value && (
            <button onClick={() => onChange('')}
              aria-label="Clear"
              style={{
                position: 'absolute', insetInlineEnd: 6, top: '50%', transform: 'translateY(-50%)',
                background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--ink-3)',
              }}>
              <XIcon size={14} />
            </button>
          )}
        </div>
      )}
      {field.kind === 'date' && (
        <input type="date" value={value ?? ''} onChange={e => onChange(e.target.value)}
          placeholder={field.placeholder}
          style={{
            width: '100%', padding: '8px 10px', fontSize: 13,
            border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            background: 'var(--surface-2)', color: 'var(--ink-1)',
          }} />
      )}
    </div>
  );
}

/** Compute the count of distinct values in `rows` for the field at `key`,
    as `[value, count]` tuples sorted alphabetically. Useful for building
    select options dynamically from the data. */
export function distinctValues<T>(rows: T[], key: keyof T): FilterFieldOption[] {
  const counts = new Map<string, number>();
  for (const r of rows) {
    const v = r[key];
    if (v === null || v === undefined || v === '') continue;
    const s = String(v);
    counts.set(s, (counts.get(s) ?? 0) + 1);
  }
  return Array.from(counts.entries())
    .map(([value, count]) => ({ value, label: `${value} (${count})` }))
    .sort((a, b) => a.value.localeCompare(b.value));
}
