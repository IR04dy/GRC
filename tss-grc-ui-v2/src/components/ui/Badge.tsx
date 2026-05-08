import { type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export type BadgeTone = 'neutral' | 'primary' | 'accent' | 'success' | 'warning' | 'danger' | 'info';

export function Badge({
  tone = 'neutral', dot, children, className, title,
}: {
  tone?: BadgeTone;
  dot?: boolean;
  children: ReactNode;
  className?: string;
  /** Tooltip text — passed through as the native HTML `title` attribute. */
  title?: string;
}) {
  return (
    <span className={cn('badge', `badge-${tone}`, dot && 'badge-dot', className)} title={title}>
      {children}
    </span>
  );
}
