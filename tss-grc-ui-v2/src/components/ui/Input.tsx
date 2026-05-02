import { forwardRef, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label?: string }>(
  function Input({ label, id, ...rest }, ref) {
    return (
      <label htmlFor={id} style={{ display: 'block' }}>
        {label && <span className="input-label">{label}</span>}
        <input ref={ref} id={id} className="input" {...rest} />
      </label>
    );
  }
);

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & { label?: string }>(
  function Textarea({ label, id, ...rest }, ref) {
    return (
      <label htmlFor={id} style={{ display: 'block' }}>
        {label && <span className="input-label">{label}</span>}
        <textarea ref={ref} id={id} className="input" {...rest} />
      </label>
    );
  }
);
