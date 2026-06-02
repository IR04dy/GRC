import { forwardRef, useId, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

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

/**
 * Inline validation message, wired for assistive tech: it carries `role="alert"`
 * so screen readers announce it, and its `id` is referenced by the field's
 * `aria-describedby`. Returns null when there's no error.
 */
export function FieldError({ id, error }: { id?: string; error?: string }) {
  if (!error) return null;
  return <span className="input-hint is-error" id={id} role="alert">{error}</span>;
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label?: string; error?: string }>(
  function Input({ label, id, required, error, ...rest }, ref) {
    const autoId = useId();
    const errId = error ? `${id ?? autoId}-err` : undefined;
    return (
      <label htmlFor={id} style={{ display: 'block' }}>
        <FieldLabel label={label} required={required} />
        <input
          ref={ref} id={id}
          className={cn('input', error && 'is-error')}
          required={required}
          {...rest}
          {...(error ? { 'aria-invalid': true, 'aria-describedby': errId } : {})}
        />
        <FieldError id={errId} error={error} />
      </label>
    );
  }
);

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & { label?: string; error?: string }>(
  function Textarea({ label, id, required, error, ...rest }, ref) {
    const autoId = useId();
    const errId = error ? `${id ?? autoId}-err` : undefined;
    return (
      <label htmlFor={id} style={{ display: 'block' }}>
        <FieldLabel label={label} required={required} />
        <textarea
          ref={ref} id={id}
          className={cn('input', error && 'is-error')}
          required={required}
          {...rest}
          {...(error ? { 'aria-invalid': true, 'aria-describedby': errId } : {})}
        />
        <FieldError id={errId} error={error} />
      </label>
    );
  }
);
