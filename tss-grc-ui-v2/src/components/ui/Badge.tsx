import { type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export type BadgeTone =
  | 'neutral' | 'primary' | 'accent' | 'success' | 'warning' | 'danger' | 'info'
  /** Severity tones on the 6-step --risk-* ramp — keeps HIGH/VERY-HIGH/CRITICAL
   *  distinct (the 4-tone semantic set collapses them). Use via severityBadgeTone(). */
  | 'risk-vlow' | 'risk-low' | 'risk-med' | 'risk-high' | 'risk-vhigh' | 'risk-crit';

export function Badge({
  tone = 'neutral', dot, size, children, className, title, ariaLabel,
}: {
  tone?: BadgeTone;
  dot?: boolean;
  /** `sm` = micro variant for in-cell tags / counters (10px). */
  size?: 'sm';
  children: ReactNode;
  className?: string;
  /** Tooltip text — passed through as the native HTML `title` attribute. */
  title?: string;
  /**
   * F2-441 — the same text for a screen reader. The house convention for disclosing a
   * value the platform cannot supply is a hint on `title` AND `aria-label` (UX-008 built
   * it that way for `Kpi`, and F2-209 records it as the rule), and this component
   * offered only `title` — so a badge saying "no answer recorded" was a tooltip a
   * screen-reader user never received. Additive and optional: every other call site is
   * unchanged.
   */
  ariaLabel?: string;
}) {
  return (
    <span
      className={cn('badge', `badge-${tone}`, dot && 'badge-dot', size === 'sm' && 'badge-sm', className)}
      title={title}
      aria-label={ariaLabel}
    >
      {children}
    </span>
  );
}
