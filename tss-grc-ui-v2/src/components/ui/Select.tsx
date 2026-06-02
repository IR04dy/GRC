import { useId, type SelectHTMLAttributes } from 'react';
import { cn } from '../../lib/cn';
import { FieldLabel, FieldError } from './Input';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: Array<{ value: string; label: string }>;
}

export function Select({ label, options, id, required, error, ...rest }: SelectProps) {
  const autoId = useId();
  const errId = error ? `${id ?? autoId}-err` : undefined;
  return (
    <label htmlFor={id} style={{ display: 'block' }}>
      <FieldLabel label={label} required={required} />
      <select
        id={id}
        className={cn('input', error && 'is-error')}
        required={required}
        {...rest}
        {...(error ? { 'aria-invalid': true, 'aria-describedby': errId } : {})}
      >
        {options.map(o => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
      <FieldError id={errId} error={error} />
    </label>
  );
}
