import { Pencil, Trash2, type LucideIcon } from 'lucide-react';
import { cn } from '../../lib/cn';

export interface RowExtraAction {
  Icon: LucideIcon;
  onClick: () => void;
  title: string;
  /** "primary" (default), "success" (green), "accent" (gold). */
  tone?: 'primary' | 'success' | 'accent';
}

interface RowActionsProps {
  onEdit?: () => void;
  onDelete?: () => void;
  /** Extra workflow actions placed before edit/delete (e.g. Approve, Escalate, Complete). */
  extras?: RowExtraAction[];
}

/**
 * Inline row buttons. Workflow actions (extras) come first, then edit/delete.
 * The wrapper has 0.55 opacity by default and lifts to full on row hover.
 */
export function RowActions({ onEdit, onDelete, extras }: RowActionsProps) {
  return (
    <div className="row-actions" onClick={e => e.stopPropagation()}>
      {extras?.map((a, i) => (
        <button
          key={i}
          type="button"
          className={cn('row-action-btn', a.tone === 'success' && 'success', a.tone === 'accent' && 'accent')}
          onClick={a.onClick}
          aria-label={a.title}
          title={a.title}
        >
          <a.Icon size={14} />
        </button>
      ))}
      {onEdit && (
        <button type="button" className="row-action-btn" onClick={onEdit} aria-label="Edit" title="Edit">
          <Pencil size={14} />
        </button>
      )}
      {onDelete && (
        <button type="button" className={cn('row-action-btn', 'danger')} onClick={onDelete} aria-label="Delete" title="Delete">
          <Trash2 size={14} />
        </button>
      )}
    </div>
  );
}
