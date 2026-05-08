import { useI18n } from '../../lib/i18n';
import { formatHijri } from '../../lib/sla';

/**
 * HijriDatePicker — Sprint 6.
 *
 * Wraps a native `<input type="date">` with a live Hijri preview directly
 * underneath. As the user picks a Gregorian date, the equivalent Umm
 * al-Qura Hijri date renders below — so a Saudi user can verify against
 * the regulator deadline they were quoted in Hijri.
 */
export function HijriDatePicker({
  value, onChange, label, min, max, required, ariaInvalid,
}: {
  value?: string;
  onChange: (iso: string) => void;
  label?: string;
  min?: string;
  max?: string;
  required?: boolean;
  ariaInvalid?: boolean;
}) {
  const { t, lang } = useI18n();
  const hijri = value ? formatHijri(value, lang) : '';

  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      {label && (
        <span style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
          {label}
        </span>
      )}
      <input type="date"
        value={value ?? ''}
        onChange={e => onChange(e.target.value)}
        min={min} max={max} required={required}
        aria-invalid={ariaInvalid}
        style={{
          padding: '6px 8px', fontSize: 12,
          border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
          background: 'var(--surface-1)',
        }} />
      {hijri && (
        <span style={{ fontSize: 11, color: 'var(--ink-3)', display: 'inline-flex', gap: 4 }}>
          <span>{t('hijriLabel')}</span>
          <span>{hijri}</span>
        </span>
      )}
    </label>
  );
}
