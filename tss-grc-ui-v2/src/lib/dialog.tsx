// Shared dialog primitive — replaces `window.prompt` / `window.confirm`
// project-wide with a Promise-based API that renders proper styled
// modals instead of the browser's unthemed pop-ups.
//
// There are 70+ `window.prompt` calls and 30+ `window.confirm` calls
// scattered across the codebase — each one breaks RTL, breaks the
// dark-mode/colour theming, and gives the user a "this isn't really
// our app" moment when it appears. This module lets callers replace
// them with one line:
//
//   const reason = await dialog.prompt({
//     title: 'Reject reason',
//     label: 'Reason for rejection',
//     required: true,
//   });
//   if (reason === null) return;  // user cancelled
//
//   const ok = await dialog.confirm({
//     title: 'Cancel this action?',
//     body: 'This cannot be undone.',
//     destructive: true,
//   });
//   if (!ok) return;
//
// The provider lives at the app root so any descendant component can
// pull the hook without each page wiring its own modal state.

import {
  useCallback, useContext, useEffect, useId, useRef, useState,
  type FormEvent, type ReactNode,
} from 'react';
import { stableContext } from './stableContext';
import { Modal } from '../components/ui/Modal';
import { Input, Textarea } from '../components/ui/Input';
import { Button } from '../components/ui/Button';
import { useI18n } from './i18n';

// ─── Public types ────────────────────────────────────────────────────

export interface PromptOptions {
  title: string;
  /** Field label shown above the input. Defaults to the title. */
  label?: string;
  placeholder?: string;
  /** Default value pre-filled in the input. */
  initial?: string;
  type?: 'text' | 'number' | 'textarea';
  /** When true, empty submit shows an inline error instead of resolving. */
  required?: boolean;
  /** Number-only — passed through to the input. */
  min?: number;
  max?: number;
  /** Custom validation — return an error string, or null to allow submit. */
  validate?: (value: string) => string | null;
  submitLabel?: string;
  cancelLabel?: string;
  /** Optional helper text shown below the input. */
  helpText?: string;
  /** Textarea row count (only used when type === 'textarea'). */
  rows?: number;
  size?: 'sm' | 'md' | 'lg';
}

export interface ConfirmOptions {
  title: string;
  /** Body text — short paragraph explaining what's about to happen. */
  body?: string;
  submitLabel?: string;
  cancelLabel?: string;
  /** Renders the submit button in danger tone for irreversible actions. */
  destructive?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

interface DialogContextValue {
  prompt: (opts: PromptOptions) => Promise<string | null>;
  confirm: (opts: ConfirmOptions) => Promise<boolean>;
}

const DialogContext = stableContext<DialogContextValue | null>('grc.dialog', null);

// ─── Provider ────────────────────────────────────────────────────────

type PromptRequest = { opts: PromptOptions; resolve: (v: string | null) => void };
type ConfirmRequest = { opts: ConfirmOptions; resolve: (v: boolean) => void };

export function DialogProvider({ children }: { children: ReactNode }) {
  const [promptReq, setPromptReq] = useState<PromptRequest | null>(null);
  const [confirmReq, setConfirmReq] = useState<ConfirmRequest | null>(null);

  // Stable function identities so consumers that put `prompt` in deps
  // arrays don't infinite-loop. The provider's state only flips when a
  // dialog actually opens / closes, but the API surface stays constant.
  const prompt = useCallback((opts: PromptOptions): Promise<string | null> =>
    new Promise(resolve => setPromptReq({ opts, resolve })), []);

  const confirm = useCallback((opts: ConfirmOptions): Promise<boolean> =>
    new Promise(resolve => setConfirmReq({ opts, resolve })), []);

  // Resolve-on-close helpers — guaranteed to fire exactly once even if
  // the user mashes Escape + close button in quick succession.
  const resolvePrompt = useCallback((value: string | null) => {
    setPromptReq(req => {
      if (req) req.resolve(value);
      return null;
    });
  }, []);
  const resolveConfirm = useCallback((value: boolean) => {
    setConfirmReq(req => {
      if (req) req.resolve(value);
      return null;
    });
  }, []);

  return (
    <DialogContext.Provider value={{ prompt, confirm }}>
      {children}
      {promptReq && <PromptModal req={promptReq} onResolve={resolvePrompt} />}
      {confirmReq && <ConfirmModal req={confirmReq} onResolve={resolveConfirm} />}
    </DialogContext.Provider>
  );
}

// ─── Hook ────────────────────────────────────────────────────────────

export function useDialog(): DialogContextValue {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error('useDialog must be used inside <DialogProvider>');
  return ctx;
}

// ─── Modal implementations ───────────────────────────────────────────

function PromptModal({
  req, onResolve,
}: {
  req: PromptRequest;
  onResolve: (v: string | null) => void;
}) {
  const { t, lang } = useI18n();
  const { opts } = req;
  const [value, setValue] = useState<string>(opts.initial ?? '');
  const [error, setError] = useState<string | null>(null);
  /** Stable per-mount id so the label can point at the field. */
  const fieldId = useId();
  const inputRef = useRef<HTMLInputElement | HTMLTextAreaElement | null>(null);

  // Reset state when a new prompt request comes in (defensive — the
  // component remounts on each new request but useState's initial value
  // only runs once per mount, so this also covers HMR edge cases).
  useEffect(() => {
    setValue(opts.initial ?? '');
    setError(null);
    // Focus + select after paint so the user can start typing or
    // overwrite the default value immediately.
    const id = window.setTimeout(() => {
      inputRef.current?.focus();
      if (typeof (inputRef.current as HTMLInputElement)?.select === 'function') {
        (inputRef.current as HTMLInputElement).select();
      }
    }, 50);
    return () => window.clearTimeout(id);
  }, [opts]);

  function submit(e?: FormEvent) {
    e?.preventDefault();
    const trimmed = value.trim();
    if (opts.required && !trimmed) {
      setError(lang === 'ar' ? 'هذا الحقل مطلوب' : 'This field is required');
      return;
    }
    if (opts.type === 'number' && trimmed) {
      const n = Number(trimmed);
      if (!Number.isFinite(n)) {
        setError(lang === 'ar' ? 'رقم غير صالح' : 'Invalid number');
        return;
      }
      if (opts.min != null && n < opts.min) {
        setError((lang === 'ar' ? 'أقل من الحد الأدنى: ' : 'Below minimum: ') + opts.min);
        return;
      }
      if (opts.max != null && n > opts.max) {
        setError((lang === 'ar' ? 'فوق الحد الأقصى: ' : 'Above maximum: ') + opts.max);
        return;
      }
    }
    if (opts.validate) {
      const msg = opts.validate(trimmed);
      if (msg) { setError(msg); return; }
    }
    onResolve(trimmed);
  }

  return (
    <Modal
      open
      title={opts.title}
      size={opts.size ?? 'md'}
      onClose={() => onResolve(null)}
      footer={
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <Button variant="ghost" onClick={() => onResolve(null)}>
            {opts.cancelLabel ?? t('cancel') ?? (lang === 'ar' ? 'إلغاء' : 'Cancel')}
          </Button>
          <Button variant="primary" onClick={() => submit()}>
            {opts.submitLabel ?? t('save') ?? (lang === 'ar' ? 'تأكيد' : 'OK')}
          </Button>
        </div>
      }
    >
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {/* `htmlFor`/`id` — the label rendered unassociated, so a screen reader
            announced an unlabelled text box on every one of these prompts, and
            `getByLabelText` could not reach the input in a test. */}
        {opts.label && (
          <label htmlFor={fieldId} style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-2)' }}>
            {opts.label}{opts.required && <span style={{ color: 'var(--danger-fg)' }}> *</span>}
          </label>
        )}
        {opts.type === 'textarea' ? (
          <Textarea
            id={fieldId}
            ref={inputRef as React.RefObject<HTMLTextAreaElement>}
            value={value}
            onChange={e => { setValue(e.target.value); if (error) setError(null); }}
            placeholder={opts.placeholder}
            rows={opts.rows ?? 4}
          />
        ) : (
          <Input
            id={fieldId}
            ref={inputRef as React.RefObject<HTMLInputElement>}
            type={opts.type === 'number' ? 'number' : 'text'}
            value={value}
            onChange={e => { setValue(e.target.value); if (error) setError(null); }}
            placeholder={opts.placeholder}
            min={opts.min}
            max={opts.max}
          />
        )}
        {opts.helpText && (
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{opts.helpText}</span>
        )}
        {error && (
          <span style={{ fontSize: 11, color: 'var(--danger-fg)', fontWeight: 600 }}>{error}</span>
        )}
        {/* Hidden submit so Enter submits the form even when the user
            never tabs to the button. */}
        <button type="submit" style={{ display: 'none' }} />
      </form>
    </Modal>
  );
}

function ConfirmModal({
  req, onResolve,
}: {
  req: ConfirmRequest;
  onResolve: (v: boolean) => void;
}) {
  const { t, lang } = useI18n();
  const { opts } = req;

  // Auto-focus the cancel button by default for non-destructive prompts;
  // focus the confirm button for non-destructive low-stakes prompts. For
  // destructive ones we always default focus to Cancel so a stray Enter
  // press doesn't fire the dangerous action.
  const cancelRef = useRef<HTMLButtonElement | null>(null);
  useEffect(() => {
    const id = window.setTimeout(() => cancelRef.current?.focus(), 50);
    return () => window.clearTimeout(id);
  }, []);

  return (
    <Modal
      open
      title={opts.title}
      size={opts.size ?? 'sm'}
      onClose={() => onResolve(false)}
      footer={
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button
            ref={cancelRef}
            type="button"
            onClick={() => onResolve(false)}
            className="btn-ghost"
            style={{
              padding: '8px 14px', fontSize: 13, fontWeight: 600,
              border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              background: 'var(--surface-1)', color: 'var(--ink-2)', cursor: 'pointer',
            }}
          >
            {opts.cancelLabel ?? t('cancel') ?? (lang === 'ar' ? 'إلغاء' : 'Cancel')}
          </button>
          <Button
            variant={opts.destructive ? 'danger' : 'primary'}
            onClick={() => onResolve(true)}
          >
            {opts.submitLabel ?? (lang === 'ar' ? 'متابعة' : 'Continue')}
          </Button>
        </div>
      }
    >
      {opts.body && (
        <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--ink-2)', margin: 0 }}>
          {opts.body}
        </p>
      )}
    </Modal>
  );
}
