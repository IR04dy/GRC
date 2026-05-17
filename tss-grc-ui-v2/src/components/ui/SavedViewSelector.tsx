import { useEffect, useState } from 'react';
import { Bookmark, Star, X, Plus } from 'lucide-react';
import {
  viewsForPage, saveView, deleteView, subscribeSavedViews,
  type SavedView,
} from '../../lib/savedViews';
import { useI18n } from '../../lib/i18n';
import { useDialog } from '../../lib/dialog';

export interface SavedViewSelectorProps {
  /** Stable key for this page, e.g. "cyber/vulns". */
  pageKey: string;
  /** Currently active view id (caller persists). */
  activeViewId?: string;
  /** Called when user picks a view — caller applies its filter/sort/columns. */
  onApply: (view: SavedView) => void;
  /** Called when user wants to save current state as a new view. Must return
   *  the snapshot to persist. Returning undefined cancels. */
  onSnapshot: () => Omit<SavedView, 'id' | 'createdAt' | 'pageKey'> | undefined;
}

/**
 * Dropdown for picking / saving / managing saved views on a list page.
 *
 * Lives in the DataTable toolbar. Snapshot button captures the current
 * filter/sort/columns; the caller wires `onSnapshot` to read those from its
 * own state.
 */
export function SavedViewSelector({ pageKey, activeViewId, onApply, onSnapshot }: SavedViewSelectorProps) {
  const { lang } = useI18n();
  const dialog = useDialog();
  const [views, setViews] = useState<SavedView[]>([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    setViews(viewsForPage(pageKey));
    return subscribeSavedViews(() => setViews(viewsForPage(pageKey)));
  }, [pageKey]);

  const active = views.find(v => v.id === activeViewId);

  const handleSnapshot = async () => {
    const snap = onSnapshot();
    if (!snap) return;
    const name = await dialog.prompt({
      title: lang === 'ar' ? 'حفظ العرض الحالي' : 'Save current view',
      label: lang === 'ar' ? 'اسم العرض' : 'View name',
      placeholder: lang === 'ar' ? 'مثال: مخاطر حرجة هذا الربع' : 'e.g. Critical risks this quarter',
      required: true,
    });
    if (!name) return;
    saveView({ ...snap, pageKey, name });
    setOpen(false);
  };

  const handleDelete = async (id: string) => {
    const ok = await dialog.confirm({
      title: lang === 'ar' ? 'حذف العرض؟' : 'Delete this view?',
      body: lang === 'ar' ? 'لا يمكن التراجع عن هذا الإجراء.' : 'This cannot be undone.',
      destructive: true,
      submitLabel: lang === 'ar' ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    deleteView(id);
  };

  return (
    <div className="sv-root">
      <button type="button" className="sv-trigger" onClick={() => setOpen(o => !o)}>
        <Bookmark size={14} />
        <span>{active?.name ?? (lang === 'ar' ? 'العروض' : 'Views')}</span>
      </button>

      {open && (
        <div className="sv-popover">
          {views.length === 0 && (
            <div className="sv-empty">{lang === 'ar' ? 'لا توجد عروض محفوظة' : 'No saved views yet'}</div>
          )}
          {views.map(v => (
            <div key={v.id} className={`sv-item ${v.id === activeViewId ? 'sv-item-active' : ''}`}>
              <button type="button" className="sv-apply" onClick={() => { onApply(v); setOpen(false); }}>
                {v.isDefault && <Star size={11} fill="currentColor" />}
                <span>{v.name}</span>
              </button>
              <button type="button" className="sv-delete" onClick={() => handleDelete(v.id)} title={lang === 'ar' ? 'حذف' : 'Delete'}>
                <X size={11} />
              </button>
            </div>
          ))}
          <div className="sv-foot">
            <button type="button" onClick={handleSnapshot} className="sv-snap">
              <Plus size={12} />
              {lang === 'ar' ? 'حفظ الحالي' : 'Save current'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
