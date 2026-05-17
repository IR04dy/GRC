import { RotateCcw } from 'lucide-react';
import { cn } from '../../lib/cn';

interface RangeFieldProps {
  label: string;
  value: string;          // e.g. "280px"
  min: number;
  max: number;
  unit: string;
  onChange: (next: string) => void;
  isCustom?: boolean;
  onReset?: () => void;
}

function numericPart(value: string): number {
  const m = value.match(/^(-?\d+(?:\.\d+)?)/);
  return m ? Number(m[1]) : 0;
}

export function RangeField({ label, value, min, max, unit, onChange, isCustom, onReset }: RangeFieldProps) {
  const num = numericPart(value);
  return (
    <div className="cf-row cf-row-range">
      <div className="cf-info">
        <div className="cf-label-row">
          <span className="cf-label">{label}</span>
          {/* "280px" is an LTR token (digits + unit). In RTL the absence
           * of dir="ltr" puts the unit on the wrong side and the digits
           * read awkwardly next to the Arabic label. */}
          <span className="cf-value mono" dir="ltr">{num}{unit}</span>
        </div>
        <input
          type="range"
          min={min}
          max={max}
          value={num}
          onChange={e => onChange(`${e.currentTarget.value}${unit}`)}
          aria-label={label}
        />
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
