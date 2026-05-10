import { type SelectHTMLAttributes } from 'react';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  options: Array<{ value: string; label: string }>;
}

export function Select({ label, options, id, ...rest }: SelectProps) {
  return (
    <label htmlFor={id} style={{ display: 'block' }}>
      {label && <span className="input-label">{label}</span>}
      <select id={id} className="input" {...rest}>
        {options.map(o => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </label>
  );
}
