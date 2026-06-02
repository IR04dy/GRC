import { useEffect, useState, type ReactNode } from 'react';
import { Check, ChevronLeft, ChevronRight, Save, Send } from 'lucide-react';
import { type LucideIcon } from 'lucide-react';
import { Button } from '../ui/Button';

/**
 * Generic step-by-step workflow wizard frame.
 *
 * <p>Every Saudi-GRC platform workflow with three or more sequential steps
 * (BIA, DPIA, supplier onboarding, cyber incident response, DSR fulfilment,
 * BCP development, …) lives inside this shell. The shell handles:
 *
 * <ul>
 *   <li>Step indicator (clickable, with completion checkmarks)</li>
 *   <li>Per-step validation gating the Next button</li>
 *   <li>Back / Next / Save Draft / Submit buttons</li>
 *   <li>Auto-save on step change (caller supplies the persistence)</li>
 *   <li>RTL support for Arabic</li>
 * </ul>
 *
 * <p>The wizard intentionally does NOT own the form state — pages keep
 * their own state, then pass it via the {@link WizardStep.content} render
 * prop. This means a wizard step can be any React subtree (a form, a
 * matrix, a workflow-engine task page, a confirmation card).
 */
export interface WizardStep {
  id: string;
  title: string;
  titleAr?: string;
  /** Short hint shown under the step label in the rail. */
  sub?: string;
  subAr?: string;
  Icon?: LucideIcon;
  /** Render the step body. Receives `isActive` so steps can skip heavy
   *  work when off-screen. */
  content: (ctx: { isActive: boolean }) => ReactNode;
  /** Optional gate — when present + returns a string, the Next button
   *  is disabled and the string is shown as a hint. */
  validate?: () => string | null | undefined;
  /** Optional save handler called when the user clicks Next (after
   *  validation passes) or Save Draft. Throwing here keeps the user on
   *  the current step and the shell shows a toast via onError. */
  onLeave?: () => Promise<void> | void;
}

export function WizardShell({
  steps,
  active,
  onChange,
  onSubmit,
  onSaveDraft,
  onCancel,
  rtl = false,
  submitting = false,
  submitLabel,
  cancelLabel,
  saveDraftLabel,
}: {
  steps: WizardStep[];
  active: number;
  onChange: (idx: number) => void;
  /** Called when the user confirms the final step. */
  onSubmit?: () => Promise<void> | void;
  onSaveDraft?: () => Promise<void> | void;
  onCancel?: () => void;
  rtl?: boolean;
  submitting?: boolean;
  submitLabel?: string;
  cancelLabel?: string;
  saveDraftLabel?: string;
}) {
  const isLast = active === steps.length - 1;
  const isFirst = active === 0;
  const step = steps[active];

  // Per-step "tried to advance with invalid state" hint. Surfaced under the
  // Next button so the user knows why it's disabled.
  const [hint, setHint] = useState<string | null>(null);
  useEffect(() => { setHint(null); }, [active]);

  async function go(delta: 1 | -1) {
    if (delta === 1) {
      const reason = step.validate?.();
      if (reason) {
        setHint(reason);
        return;
      }
      try {
        await step.onLeave?.();
      } catch {
        // page is responsible for surfacing the toast — keep the user here.
        return;
      }
    }
    setHint(null);
    onChange(active + delta);
  }

  async function submit() {
    const reason = step.validate?.();
    if (reason) { setHint(reason); return; }
    try { await step.onLeave?.(); } catch { return; }
    try { await onSubmit?.(); } catch { /* page handles toast */ }
  }

  return (
    <div className="wizard-shell">
      {/* Step rail */}
      <ol className="wizard-rail" role="tablist"
          aria-orientation="vertical">
        {steps.map((s, i) => {
          const isActive = i === active;
          const isDone   = i < active;
          return (
            <li key={s.id}
                role="tab"
                aria-selected={isActive}
                className={
                  'wizard-rail-step '
                  + (isActive ? 'is-active ' : '')
                  + (isDone ? 'is-done ' : '')
                }>
              <button type="button"
                      onClick={() => onChange(i)}
                      className="wizard-rail-button"
                      // Allow jumping back, but block forward jumps over
                      // un-validated steps (the user has to walk through).
                      disabled={i > active}>
                <span className="wizard-rail-marker">
                  {isDone
                    ? <Check size={14} strokeWidth={2.5} />
                    : <span>{i + 1}</span>}
                </span>
                <span className="wizard-rail-body">
                  <span className="wizard-rail-title">
                    {s.Icon && <s.Icon size={14} strokeWidth={2} />}
                    {rtl && s.titleAr ? s.titleAr : s.title}
                  </span>
                  {(s.sub || s.subAr) && (
                    <span className="wizard-rail-sub">
                      {rtl && s.subAr ? s.subAr : s.sub}
                    </span>
                  )}
                </span>
              </button>
            </li>
          );
        })}
      </ol>

      {/* Step body */}
      <div className="wizard-body">
        <div className="wizard-content">
          <div className="wizard-step-count">
            {rtl ? `الخطوة ${active + 1} من ${steps.length}` : `Step ${active + 1} of ${steps.length}`}
          </div>
          {steps.map((s, i) => (
            <div key={s.id}
                 className="wizard-step-pane"
                 hidden={i !== active}>
              {s.content({ isActive: i === active })}
            </div>
          ))}
        </div>

        {/* Footer */}
        <div className="wizard-footer">
          <div className="wizard-footer-left">
            {onCancel && (
              <Button variant="ghost" onClick={onCancel}>
                {cancelLabel ?? (rtl ? 'إلغاء' : 'Cancel')}
              </Button>
            )}
            {onSaveDraft && !isLast && (
              <Button variant="secondary" Icon={Save} onClick={onSaveDraft}>
                {saveDraftLabel ?? (rtl ? 'حفظ المسودة' : 'Save draft')}
              </Button>
            )}
          </div>

          <div className="wizard-footer-right">
            {hint && <span className="wizard-hint">{hint}</span>}
            {!isFirst && (
              <Button variant="ghost"
                      Icon={rtl ? ChevronRight : ChevronLeft}
                      onClick={() => void go(-1)}>
                {rtl ? 'السابق' : 'Back'}
              </Button>
            )}
            {!isLast && (
              <Button variant="primary"
                      onClick={() => void go(1)}>
                {rtl ? 'التالي' : 'Next'}
                {rtl
                  ? <ChevronLeft size={16} strokeWidth={2} />
                  : <ChevronRight size={16} strokeWidth={2} />}
              </Button>
            )}
            {isLast && onSubmit && (
              <Button variant="primary"
                      Icon={Send}
                      onClick={() => void submit()}
                      disabled={submitting}>
                {submitLabel
                  ?? (submitting
                    ? (rtl ? 'جاري الإرسال…' : 'Submitting…')
                    : (rtl ? 'إرسال' : 'Submit'))}
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
