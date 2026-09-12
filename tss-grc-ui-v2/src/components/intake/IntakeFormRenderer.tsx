import { useEffect, useId, useState } from 'react';
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
 *
 * JRN-09 — this renderer hand-rolls its controls, so it used to bypass every
 * accessible-field mechanism the platform already has.
 *
 * Measured before the fix, over this file: `htmlFor`, `fieldset`, `legend`,
 * `useId`, `role=`, `aria-` of any kind and `aria-required` were ALL ZERO. The
 * label was a styled `<div>` with no association, and the two radio kinds were
 * `<button>`s whose selected state was carried by colour and font-weight alone.
 *
 * ⚠ The population is `IntakeFieldKind`, which has EIGHT members — `date` and
 * `datetime` share one `if` branch, so counting branches gives seven. Of the
 * eight: five had no accessible name (text, textarea, select, date, datetime),
 * two were groups with no grouping (radio-cards, severity-radio), and `checkbox`
 * was already correct — it wraps its input in a real `<label>`, a valid implicit
 * association, which is why `htmlFor === 0` did not imply zero label semantics.
 *
 * 🔑 The remedy is NOT new: `components/ui/Input.tsx` and `Select.tsx` already do
 * `useId()` + `<label htmlFor>` + `required` + `aria-invalid` + `aria-describedby`
 * + a `role="alert"` error, and `FormModal` routes every field through them. This
 * component predates that convention; it now follows it rather than inventing a
 * second one.
 *
 * ⚙ The radio groups use a real `<fieldset>`/`<legend>` and real
 * `<input type="radio" name>` rather than `role="radiogroup"` + `role="radio"`.
 * That is deliberate: the ARIA radio pattern also owes arrow-key navigation and a
 * roving tabindex, so declaring the role without them would tell assistive tech
 * this is a radio group and then not behave like one. Native inputs bring the
 * grouping, the selection state and the keyboard model with them, and the styled
 * chip becomes the `<label>` — so the visual is unchanged.
 */
export function IntakeFormRenderer({ fields, submitLabel, onSubmit, onChange, initial }: {
  fields: IntakeFieldDef[];
  submitLabel: string;
  onSubmit: (payload: Record<string, string>) => void;
  /** DD-73: optional callback fired on every keystroke so parent pages
   *  can render a live preview (e.g. the change-risk panel that
   *  recomputes as the user picks values). */
  onChange?: (values: Record<string, string>) => void;
  /** Optional seed values — e.g. a process launcher prefilling changeType so
   *  "Raise emergency change" lands on the form with EMERGENCY pre-selected. */
  initial?: Record<string, string>;
}) {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [values, setValues] = useState<Record<string, string>>(() => ({ ...(initial ?? {}) }));
  /** Per-field validation messages, keyed by `field.key`. */
  const [errors, setErrors] = useState<Record<string, string>>({});
  // One id namespace per rendered form, so the parent can compute a control's id
  // without a ref — which is what lets a failed submit FOCUS the first invalid
  // field rather than only naming it in a toast. `field.key` is unique by
  // construction (it is the payload's own object key).
  const formId = useId();
  const controlId = (key: string) => `${formId}-${key}`;

  // DD-73: broadcast values to the parent via effect rather than from
  // inside the setState updater — calling the parent's setState during a
  // child's setState updater triggers a "Cannot update a component while
  // rendering a different component" warning in React 18.
  useEffect(() => {
    if (onChange) onChange(values);
  }, [values, onChange]);

  function set(key: string, v: string) {
    setValues(prev => ({ ...prev, [key]: v }));
    // Clear the field's error as soon as the user acts on it — an error that
    // outlives its cause reads as a control that cannot be satisfied.
    setErrors(prev => (prev[key] ? { ...prev, [key]: '' } : prev));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    // JRN-09 — validation is reported ON the field as well as in the toast.
    // It used to be the toast alone, so the first invalid field was neither
    // identified nor focused: a screen-reader user was told which labels were
    // missing and given no way to reach them.
    const next: Record<string, string> = {};

    const missing = fields.filter(f => f.required && !values[f.key]?.trim());
    for (const f of missing) next[f.key] = t('intakeFormRequired');

    // Minimum-length validation on summary fields
    const sumField = fields.find(f => f.key === 'summary');
    if (sumField && values.summary && values.summary.trim().length < 30) {
      next.summary = t('intakeFormSummaryTooShort');
    }

    const firstInvalid = fields.find(f => next[f.key]);
    if (firstInvalid) {
      setErrors(next);
      // The toast stays: it is a useful summary of everything outstanding, and
      // removing it would change behaviour that works. What is new is that the
      // field carries the error too, and that focus lands on the first one.
      if (missing.length > 0) {
        toast.danger(`${t('intakeFormRequired')}: ${missing.map(f => lang === 'ar' ? f.labelAr : f.labelEn).join(' · ')}`);
      } else {
        toast.danger(t('intakeFormSummaryTooShort'));
      }
      document.getElementById(controlId(firstInvalid.key))?.focus();
      return;
    }

    setErrors({});
    onSubmit(values);
  }

  return (
    // ⚠ `noValidate` is load-bearing, not a shortcut. `required` stays on every control because
    // that is the real semantic assistive tech announces — but with the browser's own interactive
    // validation left on, the native bubble fires FIRST and the submit event never reaches
    // `handleSubmit`, so none of the per-field errors, none of the `role="alert"` announcements
    // and none of the focus management below would ever run. The native message is also rendered
    // in the BROWSER's locale, which on an Arabic-primary platform (house rule 7) is not this
    // form's language. So the app owns the messaging and the attribute owns the semantics.
    <form noValidate onSubmit={handleSubmit} style={{ display: 'grid', gridTemplateColumns: 'repeat(12, 1fr)', gap: 14 }}>
      {fields.map(f => (
        <div key={f.key} style={{ gridColumn: `span ${f.span ?? 12}` }}>
          <FieldControl
            field={f}
            id={controlId(f.key)}
            value={values[f.key] ?? ''}
            error={errors[f.key]}
            onChange={v => set(f.key, v)}
          />
        </div>
      ))}
      <div style={{ gridColumn: 'span 12', display: 'flex', justifyContent: 'flex-end', marginTop: 4 }}>
        <Button type="submit" Icon={Send}>{submitLabel}</Button>
      </div>
    </form>
  );
}

function FieldControl({ field, id, value, error, onChange }: {
  field: IntakeFieldDef;
  id: string;
  value: string;
  error?: string;
  onChange: (v: string) => void;
}) {
  const { lang } = useI18n();
  const label = lang === 'ar' ? field.labelAr : field.labelEn;
  const placeholder = lang === 'ar' ? field.placeholderAr : field.placeholderEn;
  const hint = lang === 'ar' ? field.hintAr : field.hintEn;

  const hintId = hint ? `${id}-hint` : undefined;
  const errId = error ? `${id}-err` : undefined;
  // Both, in reading order — a field can have a hint AND be invalid, and
  // `aria-describedby` takes a space-separated list precisely for that.
  const describedBy = [hintId, errId].filter(Boolean).join(' ') || undefined;

  // Shared across the five text-ish kinds. `htmlFor` is the explicit
  // association; the label is NOT wrapped, because `<select>` and
  // `<textarea>` inside a label still need the id for `aria-describedby`
  // ordering to read predictably.
  const labelEl = (
    <label htmlFor={id} style={labelStyle}>
      {label}{field.required ? ' *' : ''}
    </label>
  );

  // Common to every native control below.
  const a11y = {
    id,
    required: field.required,
    'aria-describedby': describedBy,
    ...(error ? { 'aria-invalid': true as const } : {}),
  };

  if (field.kind === 'text') {
    return (
      <div>
        {labelEl}
        <input {...a11y} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder}
          style={inputStyle(!!error)} />
        {hint && <Hint id={hintId} text={hint} />}
        <FieldError id={errId} error={error} />
      </div>
    );
  }
  if (field.kind === 'textarea') {
    return (
      <div>
        {labelEl}
        <textarea {...a11y} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} rows={4}
          style={{ ...inputStyle(!!error), resize: 'vertical' }} />
        {hint && <Hint id={hintId} text={hint} />}
        <FieldError id={errId} error={error} />
      </div>
    );
  }
  if (field.kind === 'date' || field.kind === 'datetime') {
    return (
      <div>
        {labelEl}
        <input {...a11y} type={field.kind === 'date' ? 'date' : 'datetime-local'}
          value={value} onChange={e => onChange(e.target.value)} style={inputStyle(!!error)} />
        {hint && <Hint id={hintId} text={hint} />}
        <FieldError id={errId} error={error} />
      </div>
    );
  }
  if (field.kind === 'select') {
    return (
      <div>
        {labelEl}
        <select {...a11y} value={value} onChange={e => onChange(e.target.value)} style={inputStyle(!!error)}>
          <option value="">—</option>
          {(field.options ?? []).map(o => (
            <option key={o.value} value={o.value}>{lang === 'ar' ? o.labelAr : o.labelEn}</option>
          ))}
        </select>
        {hint && <Hint id={hintId} text={hint} />}
        <FieldError id={errId} error={error} />
      </div>
    );
  }
  if (field.kind === 'radio-cards' || field.kind === 'severity-radio') {
    const isSeverity = field.kind === 'severity-radio';
    return (
      // A real fieldset/legend, so the group has a name and every option is
      // announced as "N of M" — neither of which a div of buttons can express.
      // `min-width: 0` because a fieldset's default `min-width: min-content`
      // stops a grid child from shrinking.
      <fieldset style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}
        aria-describedby={describedBy}>
        <legend style={labelStyle}>{label}{field.required ? ' *' : ''}</legend>
        <div style={{
          display: 'grid',
          gridTemplateColumns: isSeverity ? 'repeat(4, 1fr)' : 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: 6,
        }}>
          {(field.options ?? []).map(o => (
            <RadioOption
              key={o.value}
              name={id}
              option={o}
              severity={isSeverity}
              selected={value === o.value}
              required={field.required && isFirst(field, o)}
              onSelect={() => onChange(o.value)}
            />
          ))}
        </div>
        {hint && <Hint id={hintId} text={hint} />}
        <FieldError id={errId} error={error} />
      </fieldset>
    );
  }
  if (field.kind === 'checkbox') {
    // Already correct before JRN-09: wrapping the input in a label is a valid
    // implicit association. It gains only the id and the error wiring.
    return (
      <div>
        <label style={{ display: 'flex', gap: 8, alignItems: 'flex-start', padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', cursor: 'pointer' }}>
          <input {...a11y} type="checkbox" checked={value === 'true'}
            onChange={e => onChange(e.target.checked ? 'true' : '')} style={{ marginTop: 2 }} />
          <span style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5 }}>{label}</span>
        </label>
        <FieldError id={errId} error={error} />
      </div>
    );
  }
  return null;
}

/** `required` on a radio group belongs on ONE member — the browser treats the whole
 *  same-named group as satisfied once any is picked, and repeating it is noise. */
function isFirst(field: IntakeFieldDef, option: IntakeFieldOption): boolean {
  return (field.options ?? [])[0]?.value === option.value;
}

/**
 * One option of a radio group.
 *
 * The styled chip IS the label, and a real `<input type="radio">` sits inside it
 * visually hidden. That buys the grouping, the checked state and arrow-key
 * navigation from the browser — and `.intake-radio` gives the chip a focus ring
 * via `:has(:focus-visible)`, because the input the user focuses is invisible.
 */
function RadioOption({ name, option, severity, selected, required, onSelect }: {
  name: string;
  option: IntakeFieldOption;
  severity: boolean;
  selected: boolean;
  required?: boolean;
  onSelect: () => void;
}) {
  const { lang } = useI18n();
  const c = severity ? (SEVERITY_PALETTE[option.value] ?? SEVERITY_PALETTE.LOW) : null;
  return (
    <label className="intake-radio" style={{
      padding: '10px 12px',
      textAlign: severity ? 'center' : 'start',
      cursor: 'pointer',
      display: 'block',
      border: selected
        ? `1.5px solid ${c ? c.border : 'var(--primary)'}`
        : '1px solid var(--border)',
      background: selected ? (c ? c.bg : 'var(--primary-50)') : 'var(--surface-2)',
      color: selected ? (c ? c.fg : 'var(--primary)') : 'var(--ink-1)',
      borderRadius: 'var(--r-sm)',
      fontSize: 13,
      fontWeight: selected ? (severity ? 700 : 600) : 500,
    }}>
      <input type="radio" name={name} value={option.value} checked={selected}
        required={required} onChange={onSelect} />
      {lang === 'ar' ? option.labelAr : option.labelEn}
    </label>
  );
}

const SEVERITY_PALETTE: Record<string, { bg: string; fg: string; border: string }> = {
  LOW: { bg: 'var(--success-bg)', fg: 'var(--success-fg)', border: 'var(--success-fg)' },
  MEDIUM: { bg: 'var(--info-bg)', fg: 'var(--info-fg)', border: 'var(--info-fg)' },
  HIGH: { bg: 'var(--warning-bg)', fg: 'var(--warning-fg)', border: 'var(--warning-fg)' },
  CRITICAL: { bg: 'var(--danger-bg)', fg: 'var(--danger-fg)', border: 'var(--danger-fg)' },
};

function Hint({ id, text }: { id?: string; text: string }) {
  return <div id={id} style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>{text}</div>;
}

/**
 * Inline validation message. `role="alert"` so assistive tech announces it when it
 * appears, and its `id` is what the control's `aria-describedby` points at — the
 * same contract `components/ui/Input.tsx` uses.
 */
function FieldError({ id, error }: { id?: string; error?: string }) {
  if (!error) return null;
  return (
    <div id={id} role="alert" style={{ fontSize: 11, color: 'var(--danger-fg)', marginTop: 4 }}>
      {error}
    </div>
  );
}

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontSize: 11,
  color: 'var(--ink-3)',
  textTransform: 'uppercase',
  letterSpacing: '0.06em',
  marginBottom: 6,
  padding: 0,
};

function inputStyle(invalid: boolean): React.CSSProperties {
  return {
    width: '100%', padding: '10px 12px', fontSize: 13,
    border: `1px solid ${invalid ? 'var(--danger-fg)' : 'var(--border)'}`,
    borderRadius: 'var(--r-sm)',
    background: 'var(--surface-1)', color: 'var(--ink-1)',
  };
}
