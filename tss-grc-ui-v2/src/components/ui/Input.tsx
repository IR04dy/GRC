import { forwardRef, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react';

/**
 * Field label with the one required-asterisk convention. The `*` is rendered
 * from the field's `required` prop (not baked into the label string), in
 * --danger, and hidden from screen readers (the native `required` attribute
 * already conveys it). One source of truth for Input / Textarea / Select.
 */
export function FieldLabel({ label, required }: { label?: string; required?: boolean }) {
  if (!label) return null;
  return (
    <span className="input-label">
      {label}
      {required && <span className="input-required" aria-hidden="true">*</span>}
    </span>
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label?: string }>(
  function Input({ label, id, required, ...rest }, ref) {
    return (
      <label htmlFor={id} style={{ display: 'block' }}>
        <FieldLabel label={label} required={required} />
        <input ref={ref} id={id} className="input" required={required} {...rest} />
      </label>
    );
  }
);

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & { label?: string }>(
  function Textarea({ label, id, required, ...rest }, ref) {
    return (
      <label htmlFor={id} style={{ display: 'block' }}>
        <FieldLabel label={label} required={required} />
        <textarea ref={ref} id={id} className="input" required={required} {...rest} />
      </label>
    );
  }
);
