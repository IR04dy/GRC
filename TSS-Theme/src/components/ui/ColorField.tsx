import { RotateCcw } from 'lucide-react';
import { cn } from '../../lib/cn';

interface ColorFieldProps {
  label: string;
  value: string;
  onChange: (next: string) => void;
  isCustom?: boolean;
  onReset?: () => void;
}

export function ColorField({ label, value, onChange, isCustom, onReset }: ColorFieldProps) {
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
        <div className="cf-value mono">{value}</div>
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
