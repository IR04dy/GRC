import { useEffect, useState } from 'react';
import { Send } from 'lucide-react';
import { Button } from '../ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  type IntakeFieldDef, type IntakeFieldOption,
} from '../../lib/intakeForms';

/**
 * IntakeFormRenderer — DD-59.
 *
 * Schema-driven renderer for every employee intake form. Walks an
 * IntakeFieldDef[] array and emits the right control per `kind`. The
 * caller passes `onSubmit(payload)` and receives back the cleaned
 * dictionary of field values.
 *
 * Used by every authenticated employee intake page (security-incident,
 * privacy-breach, risk-issue, control-observation, policy-exception)
 * so the visual + UX is identical across all five.
 */
export function IntakeFormRenderer({ fields, submitLabel, onSubmit, onChange }: {
  fields: IntakeFieldDef[];
  submitLabel: string;
  onSubmit: (payload: Record<string, string>) => void;
  /** DD-73: optional callback fired on every keystroke so parent pages
   *  can render a live preview (e.g. the change-risk panel that
   *  recomputes as the user picks values). */
  onChange?: (values: Record<string, string>) => void;
}) {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [values, setValues] = useState<Record<string, string>>({});

  // DD-73: broadcast values to the parent via effect rather than from
  // inside the setState updater — calling the parent's setState during a
  // child's setState updater triggers a "Cannot update a component while
  // rendering a different component" warning in React 18.
  useEffect(() => {
    if (onChange) onChange(values);
  }, [values, onChange]);

  function set(key: string, v: string) {
    setValues(prev => ({ ...prev, [key]: v }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    // Required-field validation
    const missing = fields.filter(f => f.required && !values[f.key]?.trim());
    if (missing.length > 0) {
      toast.danger(`${t('intakeFormRequired')}: ${missing.map(f => lang === 'ar' ? f.labelAr : f.labelEn).join(' · ')}`);
      return;
    }
    // Minimum-length validation on summary fields
    const sumField = fields.find(f => f.key === 'summary');
    if (sumField && values.summary && values.summary.trim().length < 30) {
      toast.danger(t('intakeFormSummaryTooShort'));
      return;
    }
    onSubmit(values);
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(12, 1fr)', gap: 14,
      }}>
        {fields.map(f => (
          <div key={f.key} style={{ gridColumn: `span ${f.span ?? 12}` }}>
            <FieldControl field={f} value={values[f.key] ?? ''} onChange={v => set(f.key, v)} />
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
        <Button variant="primary" Icon={Send} onClick={() => { /* form onSubmit handles it */ }}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}

function FieldControl({ field, value, onChange }: {
  field: IntakeFieldDef;
  value: string;
  onChange: (v: string) => void;
}) {
  const { lang } = useI18n();
  const label = lang === 'ar' ? field.labelAr : field.labelEn;
  const placeholder = lang === 'ar' ? field.placeholderAr : field.placeholderEn;
  const hint = lang === 'ar' ? field.hintAr : field.hintEn;

  const labelEl = (
    <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }}>
      {label}{field.required ? ' *' : ''}
    </div>
  );

  if (field.kind === 'text') {
    return (
      <div>
        {labelEl}
        <input value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} style={inputStyle} />
        {hint && <Hint text={hint} />}
      </div>
    );
  }
  if (field.kind === 'textarea') {
    return (
      <div>
        {labelEl}
        <textarea value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} rows={4}
          style={{ ...inputStyle, resize: 'vertical' }} />
        {hint && <Hint text={hint} />}
      </div>
    );
  }
  if (field.kind === 'date' || field.kind === 'datetime') {
    return (
      <div>
        {labelEl}
        <input type={field.kind === 'date' ? 'date' : 'datetime-local'}
          value={value} onChange={e => onChange(e.target.value)} style={inputStyle} />
        {hint && <Hint text={hint} />}
      </div>
    );
  }
  if (field.kind === 'select') {
    return (
      <div>
        {labelEl}
        <select value={value} onChange={e => onChange(e.target.value)} style={inputStyle}>
          <option value="">—</option>
          {(field.options ?? []).map(o => (
            <option key={o.value} value={o.value}>{lang === 'ar' ? o.labelAr : o.labelEn}</option>
          ))}
        </select>
        {hint && <Hint text={hint} />}
      </div>
    );
  }
  if (field.kind === 'radio-cards') {
    return (
      <div>
        {labelEl}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 6 }}>
          {(field.options ?? []).map(o => <RadioCard key={o.value} option={o} selected={value === o.value} onClick={() => onChange(o.value)} />)}
        </div>
        {hint && <Hint text={hint} />}
      </div>
    );
  }
  if (field.kind === 'severity-radio') {
    return (
      <div>
        {labelEl}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6 }}>
          {(field.options ?? []).map(o => <SeverityChip key={o.value} option={o} selected={value === o.value} onClick={() => onChange(o.value)} />)}
        </div>
        {hint && <Hint text={hint} />}
      </div>
    );
  }
  if (field.kind === 'checkbox') {
    return (
      <label style={{ display: 'flex', gap: 8, alignItems: 'flex-start', padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', cursor: 'pointer' }}>
        <input type="checkbox" checked={value === 'true'} onChange={e => onChange(e.target.checked ? 'true' : '')} style={{ marginTop: 2 }} />
        <span style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5 }}>{label}</span>
      </label>
    );
  }
  return null;
}

function RadioCard({ option, selected, onClick }: { option: IntakeFieldOption; selected: boolean; onClick: () => void }) {
  const { lang } = useI18n();
  return (
    <button type="button" onClick={onClick}
      style={{
        padding: '10px 12px', textAlign: 'start', cursor: 'pointer',
        border: selected ? '1.5px solid var(--primary)' : '1px solid var(--border)',
        background: selected ? 'var(--primary-50)' : 'var(--surface-2)',
        color: selected ? 'var(--primary)' : 'var(--ink-1)',
        borderRadius: 'var(--r-sm)',
        fontSize: 13, fontWeight: selected ? 600 : 500,
      }}>
      {lang === 'ar' ? option.labelAr : option.labelEn}
    </button>
  );
}

function SeverityChip({ option, selected, onClick }: { option: IntakeFieldOption; selected: boolean; onClick: () => void }) {
  const { lang } = useI18n();
  const palette: Record<string, { bg: string; fg: string; border: string }> = {
    LOW:      { bg: 'var(--success-bg)', fg: 'var(--success-fg)', border: 'var(--success-fg)' },
    MEDIUM:   { bg: 'var(--info-bg)',    fg: 'var(--info-fg)',    border: 'var(--info-fg)' },
    HIGH:     { bg: 'var(--warning-bg)', fg: 'var(--warning-fg)', border: 'var(--warning-fg)' },
    CRITICAL: { bg: 'var(--danger-bg)',  fg: 'var(--danger-fg)',  border: 'var(--danger-fg)' },
  };
  const c = palette[option.value] ?? palette.LOW;
  return (
    <button type="button" onClick={onClick}
      style={{
        padding: '10px 12px', textAlign: 'center', cursor: 'pointer',
        border: selected ? `1.5px solid ${c.border}` : '1px solid var(--border)',
        background: selected ? c.bg : 'var(--surface-2)',
        color: selected ? c.fg : 'var(--ink-1)',
        borderRadius: 'var(--r-sm)',
        fontSize: 13, fontWeight: selected ? 700 : 500,
      }}>
      {lang === 'ar' ? option.labelAr : option.labelEn}
    </button>
  );
}

function Hint({ text }: { text: string }) {
  return <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>{text}</div>;
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '10px 12px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)', color: 'var(--ink-1)',
};
