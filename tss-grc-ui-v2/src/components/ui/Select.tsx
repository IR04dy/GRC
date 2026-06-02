import { type SelectHTMLAttributes } from 'react';
import { FieldLabel } from './Input';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  options: Array<{ value: string; label: string }>;
}

export function Select({ label, options, id, required, ...rest }: SelectProps) {
  return (
    <label htmlFor={id} style={{ display: 'block' }}>
      <FieldLabel label={label} required={required} />
      <select id={id} className="input" required={required} {...rest}>
        {options.map(o => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </label>
  );
}
