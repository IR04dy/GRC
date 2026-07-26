import { useCallback, useEffect, useState } from 'react';
import { DEFAULT_LAYOUT, WIDGETS_BY_ID, type WidgetSize } from './widgets';

export type LayoutItem = { id: string; size: WidgetSize };

const STORAGE_KEY = 'grc.dashboard.layout';

/**
 * Widget ids that were renamed, old -> new.
 *
 * W2-1a-ii: renaming a widget id is a silent data loss for every user who has arranged
 * their dashboard, because readLayout() drops ids the registry no longer knows and the tile
 * just disappears — no error, no empty state, nothing to notice. `kpi-overdue-reassessments`
 * became `kpi-never-assessed-vendors` when its metric changed (the tier/cadence that
 * "overdue" needs exists only in a frontend mock), so the saved layouts have to come with
 * it. Any future rename belongs here for the same reason.
 */
const RENAMED_WIDGET_IDS: Record<string, string> = {
  'kpi-overdue-reassessments': 'kpi-never-assessed-vendors',
};

function readLayout(): LayoutItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_LAYOUT;
    const parsed = JSON.parse(raw) as LayoutItem[];
    if (!Array.isArray(parsed)) return DEFAULT_LAYOUT;
    return parsed
      .filter(it => it && typeof it.id === 'string')
      .map(it => (RENAMED_WIDGET_IDS[it.id] ? { ...it, id: RENAMED_WIDGET_IDS[it.id] } : it))
      // Drop entries whose widget id no longer exists in the registry.
      .filter(it => WIDGETS_BY_ID[it.id]);
  } catch { return DEFAULT_LAYOUT; }
}

export function useDashboardLayout() {
  const [items, setItems] = useState<LayoutItem[]>(() => readLayout());

  useEffect(() => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(items)); } catch { /* quota */ }
  }, [items]);

  const move = useCallback((from: number, to: number) => {
    setItems(prev => {
      if (from === to || from < 0 || to < 0 || from >= prev.length || to >= prev.length) return prev;
      const next = [...prev];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved);
      return next;
    });
  }, []);

  const remove = useCallback((id: string) => {
    setItems(prev => prev.filter(it => it.id !== id));
  }, []);

  const add = useCallback((id: string) => {
    if (!WIDGETS_BY_ID[id]) return;
    setItems(prev => prev.some(it => it.id === id) ? prev : [...prev, { id, size: WIDGETS_BY_ID[id].defaultSize }]);
  }, []);

  const setSize = useCallback((id: string, size: WidgetSize) => {
    setItems(prev => prev.map(it => it.id === id ? { ...it, size } : it));
  }, []);

  const reset = useCallback(() => setItems(DEFAULT_LAYOUT), []);

  return { items, move, remove, add, setSize, reset };
}
