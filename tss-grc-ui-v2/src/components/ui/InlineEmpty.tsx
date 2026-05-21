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
 *
 * <p>Accepts either {@link text} (string) or {@link children} (ReactNode).
 * Old call sites used `<InlineEmpty text="No data" />` extensively; new code
 * is encouraged to use children for richer payloads.
 */
export function InlineEmpty({
  text, children, compact, className,
}: {
  /** Plain-string convenience prop — sites doing `<InlineEmpty text="..."/>`. */
  text?: string;
  /** Use for icons + multi-line content. Takes precedence over `text`. */
  children?: ReactNode;
  /** Use the denser 12px-padding variant — fits inside sidebars + small tiles. */
  compact?: boolean;
  className?: string;
}) {
  const body = children ?? text ?? '';
  return (
    <div className={cn('empty-inline', compact && 'empty-inline-compact', className)}>
      {body}
    </div>
  );
}
