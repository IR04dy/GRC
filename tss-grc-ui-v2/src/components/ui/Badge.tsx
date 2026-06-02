import { type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export type BadgeTone = 'neutral' | 'primary' | 'accent' | 'success' | 'warning' | 'danger' | 'info';

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
