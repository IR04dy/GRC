import type { ReactNode } from 'react';
import { X } from 'lucide-react';
import { useI18n } from '../../lib/i18n';

export interface BulkAction<T> {
  id: string;
  label: string;
  icon?: ReactNode;
  onClick: (rows: T[]) => void | Promise<void>;
  variant?: 'default' | 'destructive';
  /** Disabled when this returns true. */
  disabled?: (rows: T[]) => boolean;
}

export interface BulkActionsBarProps<T> {
  selected: T[];
  actions: BulkAction<T>[];
  onClear: () => void;
}

/**
 * Floating action bar — slides up from the bottom of the viewport when at
 * least one row is selected in a DataTable. ServiceNow IRM pattern.
 *
 * Wire by: `<BulkActionsBar selected={selectedRows} actions={[...]} onClear={() => setSel([])} />`
 * Place at the page level (not inside the table); CSS positions it fixed.
 */
export function BulkActionsBar<T>({ selected, actions, onClear }: BulkActionsBarProps<T>) {
  const { lang } = useI18n();
  if (selected.length === 0) return null;

  return (
    <div className="ba-root" role="region" aria-label={lang === 'ar' ? 'إجراءات مجمّعة' : 'Bulk actions'}>
      <span className="ba-count">
        {selected.length} {lang === 'ar' ? 'محدَّد' : 'selected'}
      </span>
      <div className="ba-divider" />
      {actions.map(a => {
        const disabled = a.disabled?.(selected) ?? false;
        return (
          <button
            key={a.id}
            type="button"
            className={`ba-btn ${a.variant === 'destructive' ? 'ba-btn-danger' : ''}`}
            disabled={disabled}
            onClick={() => a.onClick(selected)}
          >
            {a.icon}
            <span>{a.label}</span>
          </button>
        );
      })}
      <div className="ba-divider" />
      <button type="button" onClick={onClear} className="ba-clear" title={lang === 'ar' ? 'إلغاء' : 'Clear'}>
        <X size={14} />
      </button>
    </div>
  );
}
