import { RotateCcw, AlertTriangle } from 'lucide-react';
import { cn } from '../../lib/cn';

interface ColorFieldProps {
  label: string;
  value: string;
  onChange: (next: string) => void;
  isCustom?: boolean;
  onReset?: () => void;
  /**
   * UX-007: when set, renders an inline low-contrast badge under the hex value.
   * `warning.label` is the short badge text ("Low contrast"); `warning.title`
   * is the longer tooltip explaining the AA failure. Both are pre-localised by
   * the caller so this component stays presentation-only.
   */
  warning?: { label: string; title: string };
}

export function ColorField({ label, value, onChange, isCustom, onReset, warning }: ColorFieldProps) {
  return (
    <div className="cf-row">
      <input
        type="color"
        className="cf-swatch"
        value={value}
        onChange={e => onChange(e.currentTarget.value)}
        aria-label={label}
      />
      <div className="cf-info">
        <div className="cf-label">{label}</div>
        {/* Hex code is an LTR token (#0E5E5E) — without `dir="ltr"` it
         * inherits RTL from <html dir="rtl"> and renders the '#' at the
         * end of the string, looking misaligned next to the swatch. */}
        <div className="cf-value mono" dir="ltr">{value}</div>
        {warning && (
          <span
            role="status"
            title={warning.title}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 4,
              marginTop: 4,
              padding: '1px 6px',
              fontSize: 10.5, fontWeight: 700, lineHeight: 1.4,
              color: 'var(--danger-fg)',
              background: 'var(--danger-bg)',
              border: '1px solid var(--danger-border)',
              borderRadius: 'var(--r-xs)',
            }}
          >
            <AlertTriangle size={11} strokeWidth={2.4} aria-hidden="true" />
            {warning.label}
          </span>
        )}
      </div>
      <button
        type="button"
        className={cn('cf-reset', !isCustom && 'cf-reset-hidden')}
        onClick={onReset}
        title="Reset to default"
        aria-label="Reset"
        disabled={!isCustom}
      >
        <RotateCcw size={12} />
      </button>
    </div>
  );
}
