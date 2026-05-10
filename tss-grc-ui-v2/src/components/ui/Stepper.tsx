import { Check, type LucideIcon } from 'lucide-react';
import { cn } from '../../lib/cn';

export interface StepperItem { id: string; label: string; }

interface StepperProps {
  steps: StepperItem[];
  /** ID of the active step. Steps before this are marked complete. */
  active: string;
  /** Optional icon to render in the active step's circle (defaults to a dot). */
  ActiveIcon?: LucideIcon;
}

/**
 * Horizontal stepper rendering a workflow's progression.
 * Used for vulnerability lifecycle, vendor onboarding, incident response stages.
 */
export function Stepper({ steps, active, ActiveIcon }: StepperProps) {
  const activeIdx = Math.max(0, steps.findIndex(s => s.id === active));
  return (
    <div className="stepper">
      {steps.map((step, i) => {
        const state = i < activeIdx ? 'done' : i === activeIdx ? 'active' : 'todo';
        return (
          <div key={step.id} className={cn('step', `step-${state}`)}>
            <div className="step-dot">
              {state === 'done'
                ? <Check size={11} strokeWidth={3} />
                : state === 'active' && ActiveIcon
                  ? <ActiveIcon size={11} strokeWidth={2.5} />
                  : <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700 }}>{i + 1}</span>}
            </div>
            <div className="step-label">{step.label}</div>
            {i < steps.length - 1 && <div className="step-bar" />}
          </div>
        );
      })}
    </div>
  );
}
