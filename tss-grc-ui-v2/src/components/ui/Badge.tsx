import { type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export type BadgeTone =
  | 'neutral' | 'primary' | 'accent' | 'success' | 'warning' | 'danger' | 'info'
  /** Severity tones on the 6-step --risk-* ramp — keeps HIGH/VERY-HIGH/CRITICAL
   *  distinct (the 4-tone semantic set collapses them). Use via severityBadgeTone(). */
  | 'risk-vlow' | 'risk-low' | 'risk-med' | 'risk-high' | 'risk-vhigh' | 'risk-crit';

export function Badge({
  tone = 'neutral', dot, size, children, className, title,
}: {
  tone?: BadgeTone;
  dot?: boolean;
  /** `sm` = micro variant for in-cell tags / counters (10px). */
  size?: 'sm';
  children: ReactNode;
  className?: string;
  /** Tooltip text — passed through as the native HTML `title` attribute. */
  title?: string;
}) {
  return (
    <span
      className={cn('badge', `badge-${tone}`, dot && 'badge-dot', size === 'sm' && 'badge-sm', className)}
      title={title}
    >
      {children}
    </span>
  );
}
