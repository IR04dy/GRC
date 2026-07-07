import { useEffect, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { Button } from './Button';

interface Props {
  open: boolean;
  title: string;
  /** Optional secondary line under the title (e.g. the record it is anchored to). */
  subtitle?: string;
  onClose: () => void;
  children: ReactNode;
  /** Action bar rendered in a sticky footer (e.g. Cancel / Save draft / Submit). */
  footer?: ReactNode;
  /** Panel width in px (default 480). Clamped to the viewport on small screens. */
  width?: number;
}

/**
 * GRCStructuredDrawer — a right-side (inline-end) slide-in panel for mid-weight,
 * context-preserving GRC actions that are heavier than a quick modal but lighter
 * than a full page/wizard: assign owner, quick review, raise a record-scoped
 * request, status-with-reason, add follow-up.
 *
 * <p>Mirrors {@link Modal}'s affordances — React portal, Escape-to-close,
 * body-scroll lock, scrim-click close, `stopPropagation` on the panel — but docks
 * to the inline-end edge so the parent page stays visible for context. RTL-aware
 * via `justify-content: flex-end` (flips to the left edge under `dir="rtl"`).
 * Structure: header (title + subtitle + close), scrollable body, sticky footer.
 */
export function GRCStructuredDrawer({ open, title, subtitle, onClose, children, footer, width = 480 }: Props) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;

  return createPortal(
    <div onClick={onClose} role="dialog" aria-modal="true" aria-label={title}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', zIndex: 1000,
        display: 'flex', justifyContent: 'flex-end',
      }}>
      <div onClick={e => e.stopPropagation()}
        style={{
          width, maxWidth: '100vw', height: '100vh', background: 'var(--surface-1)',
          boxShadow: '-8px 0 24px rgba(0,0,0,0.15)', display: 'flex', flexDirection: 'column',
        }}>
        <div style={{
          padding: '14px 16px', borderBottom: '1px solid var(--border)',
          display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12, flexShrink: 0,
        }}>
          <div style={{ minWidth: 0 }}>
            <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600, color: 'var(--ink-1)' }}>{title}</h3>
            {subtitle && <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>{subtitle}</div>}
          </div>
          <Button variant="ghost" size="sm" Icon={X} onClick={onClose} aria-label="Close" />
        </div>
        <div style={{ flex: 1, overflowY: 'auto', padding: 16 }}>{children}</div>
        {footer && (
          <div style={{
            padding: '12px 16px', borderTop: '1px solid var(--border)', flexShrink: 0,
            display: 'flex', justifyContent: 'flex-end', gap: 8, background: 'var(--surface-1)',
          }}>{footer}</div>
        )}
      </div>
    </div>,
    document.body,
  );
}
