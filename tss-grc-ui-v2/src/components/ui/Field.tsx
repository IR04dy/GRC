import type { ReactNode } from 'react';

export type FieldVariant = 'stacked' | 'inline';

/**
 * Unified detail key/value renderer. Replaces the four near-identical local
 * `Field` copies that drifted across detail pages (10/11/12px labels, inline
 * styles vs CSS classes, stacked vs inline layout).
 *
 *  - `stacked` (default): label above value — the dense rail/overview style.
 *  - `inline`: label beside value (160px gutter) — the data-row style.
 *
 * RTL-safe: block flow + flex honour `direction`, no physical left/right.
 */
export function Field({
  label,
  value,
  variant = 'stacked',
  bold,
  mono,
  multiline,
}: {
  label: ReactNode;
  value: ReactNode;
  variant?: FieldVariant;
  /** Heavier value weight, e.g. for the record's name/title. */
  bold?: boolean;
  /** Monospace value, e.g. for codes / IDs / versions. */
  mono?: boolean;
  /** Preserve newlines for long descriptions. */
  multiline?: boolean;
}) {
  const valueCls = ['field__value', bold ? 'field__value--bold' : 'field__value--medium'];
  if (mono) valueCls.push('field__value--mono');
  if (multiline) valueCls.push('field__value--multiline');
  return (
    <div className={`field field--${variant}`}>
      <div className="field__label">{label}</div>
      <div className={valueCls.join(' ')}>{value}</div>
    </div>
  );
}
