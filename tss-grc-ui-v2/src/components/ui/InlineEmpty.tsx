import { type ReactNode } from 'react';
import { cn } from '../../lib/cn';

/**
 * Small empty-state for use inside cards, drawers, KPI grids, and sub-tabs —
 * sized to sit comfortably *within* another container rather than dominate a
 * full page. For full-page empty states use the larger {@link EmptyState}
 * (which has its own icon + headline + sub).
 *
 * Why a second component: pages were reinventing this all over the codebase as
 * `<div style={{ padding: 12 or 16 or 24, fontSize: 12, color: 'var(--ink-3)',
 * textAlign: 'center' }}>` — same intent, different numbers each time. The
 * size sweep showed three flavours; the most common is captured here, the
 * denser "compact" variant exists for sidebar lists and dashboard tiles.
 */
export function InlineEmpty({
  children, compact, className,
}: {
  children: ReactNode;
  /** Use the denser 12px-padding variant — fits inside sidebars + small tiles. */
  compact?: boolean;
  className?: string;
}) {
  return (
    <div className={cn('empty-inline', compact && 'empty-inline-compact', className)}>
      {children}
    </div>
  );
}
